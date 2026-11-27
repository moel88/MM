package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.kageno.HealNumbers;
import dev.sangwoo.celestialfall.stand.GoldExperienceHeal;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import dev.sangwoo.celestialfall.stand.StandManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;

/**
 * Gold Experience's healing as it is drawn, and its key (2.149.0, Y: GoldExperienceRules has the user's words). The
 * server runs it (GoldExperienceHeal); here is what the stand does through it, on the healing's own clock. It is one
 * punch, the fist driven in and left in (user: "러시를 꽂으며 힐 해주는거 같은데 그렇게 말고 주먹을 한 번 꽂아서
 * 주먹을 박아넣고 힐 되게"), never a barrage:
 * <ul>
 * <li>Y tapped, another's healing: it comes round to the front of its user and from its guard drives its right fist
 * into whoever it is for (the generator's {@code mend}), faster and faster, in as the server heals them; it leaves the
 * fist in a moment, draws it out to its guard and goes back to its user's shoulder.</li>
 * <li>Y held, its user's own: it comes round to their front right, turns on them, and drives the same fist into them,
 * in as the server's first healing comes; the fist stays in them, pressing a little with each healing, until Y is let
 * go; then it draws it out and goes back.</li>
 * </ul>
 * The key: Y down starts a count; let go within GoldExperienceRules.HEAL_HOLD ticks it is the tap, still down then it
 * is the hold, which lasts until Y comes up. Everything drawn is a function of the healing's clock.
 */
public final class GoldHeal {
    /** Seconds on either's clock: the fist starts out, is in (the server's MEND_CONTACT, and SELF_FIRST: the same). */
    static final float OUT_FROM=.14f,TOUCH=.3f;
    /** The tap, seconds on its clock: the fist stays in until, is back in the guard, over. */
    static final float STAYS=.56f,GUARDED=.82f,LENGTH=.9f;
    /** Seconds: stopped short or let go the fist is back in its guard; it stays a moment in front once in its guard and then goes back (the punch's). */
    static final float CUT=.15f,LINGER=.12f,RETURN=.4f,TAKE=.1f;
    /** How far in front of its user it stands for the tap with no one known, and no nearer or further than (blocks); how deep its fist goes into whom it is for. */
    static final float AHEAD=1.05f,NEAR=.75f,FAR=3.0f,PRESS=.2f;
    /** Held: where round its user it stands (degrees to the right of the way they face), and how far from their middle its knuckles stop: well inside their body. */
    static final float SIDE=52,DEPTH=.1f;
    /** Held: how much lower still than through the rush it floats (blocks): its fist goes into its user's chest, not their face (the first take in the game had it at a player's neck). */
    static final float LOWER=.17f;
    /** How big its arm is through either. */
    static final float ARMS=1.08f;
    /** Held: how far further in the fist presses with each healing (blocks), and how long a press takes to ease off (seconds). */
    static final float PUSH=.035f,PUSH_OFF=.09f;
    /** A healing as heard. */
    public static final class Heal {
        public final boolean self;public final long start;final long serverAge,received;final float yaw,aim;final int seed;
        /** Its clock (seconds) when it ended (the hold let go or run out; NaN: still on), when the tap was stopped short (NaN: not), and when its hand touched (NaN: not yet). */
        float ended=Float.NaN,stopped=Float.NaN,touchedAt=Float.NaN;
        /** Whom the hand touched (entity id, -1 no one), what it healed them, where they stood (their middle) and how tall and wide they are. */
        int whom=-1;float healed;Vec3d whomAt;float whomHeight=1.8f,whomWidth=.6f;
        /** Held: how many blows have healed, and the clock of the last. */
        int blows;float lastBlow=-9;
        long ready;
        Heal(boolean self,long start,long serverAge,long received,float yaw,float aim,int seed){this.self=self;this.start=start;this.serverAge=serverAge;this.received=received;this.yaw=yaw;this.aim=aim;this.seed=seed;}
        /** Seconds on its clock this frame. */
        public float age(float delta){return (serverAge+StandRush.ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        /** Held: when its blows stop (seconds): when it was let go, or their end. */
        public float ends(){return Float.isNaN(ended)?GoldExperienceRules.SELF_LENGTH/20f:ended;}
        public boolean touched(){return !Float.isNaN(touchedAt);}
        public int whom(){return whom;}
        public float healed(){return healed;}
        public int seed(){return seed;}
        public float yaw(){return yaw;}
    }
    private static final Map<UUID,Heal> HEALS=new HashMap<>();
    private static Object world;
    /** Y is down (the key is ours until it comes up), since which client tick, and whether the hold has been asked for. */
    private static boolean heldY,asked;private static long downAt;
    private GoldHeal(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(GoldExperienceHeal.EVENT,(mc,handler,b,response)->{
            int type=b.readUnsignedByte();UUID user=b.readUuid();Identifier dimension=b.readIdentifier();
            long start=b.readLong(),serverNow=b.readLong();float yaw=b.readFloat(),aim=b.readFloat();long ready=b.readLong();
            int whom=b.readInt();float healed=b.readFloat();
            if(!Float.isFinite(yaw)||!Float.isFinite(healed))return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){HEALS.clear();world=mc.world;}
                event(mc,user,type,start,serverNow,yaw,aim,ready,whom,healed);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->{HEALS.clear();world=null;heldY=asked=false;}));
        ClientTickEvents.END_CLIENT_TICK.register(GoldHeal::tick);
    }
    private static void event(MinecraftClient mc,UUID id,int type,long start,long serverNow,float yaw,float aim,long ready,int whom,float healed){
        var h=HEALS.get(id);
        switch(type){
            case GoldExperienceHeal.MENDING,GoldExperienceHeal.SELFING->{
                h=new Heal(type==GoldExperienceHeal.SELFING,start,serverNow-start,StandRush.ticks,yaw,aim,(int)(start*31+id.getLeastSignificantBits()));
                h.ready=ready;HEALS.put(id,h);
            }
            case GoldExperienceHeal.MENDED->{
                if(h==null||h.start!=start||h.self)return;
                h.ready=ready;h.touchedAt=(serverNow-start)/20f;h.whom=whom;h.healed=healed;
                if(mc.world.getEntityById(whom) instanceof LivingEntity e){
                    h.whomAt=e.getPos().add(0,e.getHeight()*.6,0);h.whomHeight=e.getHeight();h.whomWidth=e.getWidth();
                    if(healed>.05f)HealNumbers.show(e,healed,mc.world.getTime());
                }
            }
            case GoldExperienceHeal.BLOW->{
                if(h==null||h.start!=start||!h.self)return;
                h.blows++;h.lastBlow=(serverNow-start)/20f;h.healed+=healed;
                if(healed>.005f)HealNumbers.show(id,healed,mc.world.getTime());
            }
            case GoldExperienceHeal.END->{
                if(h==null||h.start!=start)return;
                h.ready=ready;
                if(h.self){if(Float.isNaN(h.ended))h.ended=Math.max(0,Math.min(h.age(0),GoldExperienceRules.SELF_LENGTH/20f));}
                else if(whom>=0&&Float.isNaN(h.stopped)&&!h.touched())h.stopped=Math.max(0,h.age(0));
            }
            default->{}
        }
    }
    private static void tick(MinecraftClient mc){
        // Y still down HEAL_HOLD ticks on: it is the hold
        if(heldY&&!asked&&StandRush.ticks-downAt>=GoldExperienceRules.HEAL_HOLD){
            asked=true;
            if(mc.player!=null&&mc.currentScreen==null&&usable(mc))send(GoldExperienceHeal.HOLD);
        }
        if(heldY&&(mc.player==null||mc.currentScreen!=null)){
            // a screen came up over the key: it is let go (its release would never be heard)
            if(asked)send(GoldExperienceHeal.RELEASE);
            heldY=asked=false;
        }
    }
    private static boolean usable(MinecraftClient mc){return MapClient.enabled()&&StandManager.goldExperience(mc.player)&&GoldPresence.out(mc.player)&&CombatClient.combatInput();}
    private static void send(int what){
        if(!ClientPlayNetworking.canSend(GoldExperienceHeal.INPUT))return;
        var mc=MinecraftClient.getInstance();
        if(what!=GoldExperienceHeal.RELEASE&&mc.player!=null){
            BotandCamera.prepareInteraction();
            // the way the hand goes is the way the player faces now: the turn goes to the server before the word
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
        }
        var b=PacketByteBufs.create();b.writeByte(what);ClientPlayNetworking.send(GoldExperienceHeal.INPUT,b);
    }

    // ───────────────────────────── the pose ─────────────────────────────

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** How far in the fist is {@code t} seconds in (0 in the guard, 1 driven in): faster and faster, fastest as it goes in, as the server heals. */
    static float reach(float t){float u=MathHelper.clamp((t-OUT_FROM)/(TOUCH-OUT_FROM),0,1);return (float)Math.pow(u,1.6);}
    /** When healing {@code h} starts drawing its fist out (seconds on its clock): the tap after its moment in, or stopped short; the hold as Y is let go or it runs out. */
    static float leaves(Heal h){return h.self?h.ends():Float.isNaN(h.stopped)?STAYS:Math.min(STAYS,h.stopped);}
    /** How long it takes drawing it out to its guard. */
    static float drawing(Heal h){return h.self||!Float.isNaN(h.stopped)?CUT:GUARDED-STAYS;}
    /** When it is back in its guard. */
    static float guarded(Heal h){return leaves(h)+drawing(h);}
    /** How far back in its guard it is {@code t} seconds in (0 still in, 1 in its guard). */
    static float back(Heal h,float t){return smooth((t-leaves(h))/drawing(h));}
    /** Adds the healing's stances {@code t} seconds into it into {@code w}: the guard, the fist driven in, the guard again. */
    public static void stances(Heal h,float t,float[] w){
        float in=reach(Math.min(t,leaves(h)))*(1-back(h,t));
        w[GUARD]+=1-in;w[MEND]+=in;
    }
    /** How far in front of its user it stands for this tap (blocks): so that its fist at full stretch is PRESS into whom it is for. */
    static float place(Heal h){
        if(Float.isNaN(h.aim))return AHEAD;
        return MathHelper.clamp(h.aim+PRESS-fistReach(),NEAR,FAR);
    }
    /** How far ahead of the stand's feet its right fist reaches driven in (blocks, at its size 1). */
    static float fistReach(){return GoldRush.grown(MEND,1,ARMS)[0];}
    /** How far from its user it stands through the hold (blocks): its knuckles DEPTH from their middle, the fist well in them. */
    static float selfPlace(){return fistReach()+DEPTH;}
    /** Held: how far further in the fist presses {@code t} seconds in (blocks): a little with each healing, eased off before the next. */
    static float press(Heal h,float t){
        float since=t-TOUCH;if(since<0||t>h.ends())return 0;
        float every=GoldExperienceRules.SELF_EVERY/20f,u=since%every;
        return PUSH*Math.min(1,u/.02f)*(1-smooth((u-.02f)/PUSH_OFF));
    }
    /** How big its arm is {@code t} seconds into healing {@code h}. */
    static float arms(Heal h,float t){return 1+(ARMS-1)*smooth(t/StandRush.RISE)*(1-back(h,t));}
    /** Where its right fist's knuckles are {@code t} seconds in (blocks ahead, to its right and up from its feet, at its size 1). */
    static float[] fist(Heal h,float t){
        float[] w=new float[NAMES.size()];stances(h,t,w);float size=arms(h,t);
        float[] a=GoldRush.grown(GUARD,1,size),b=GoldRush.grown(MEND,1,size);
        return new float[]{a[0]*w[GUARD]+b[0]*w[MEND],a[1]*w[GUARD]+b[1]*w[MEND],a[2]*w[GUARD]+b[2]*w[MEND]};
    }
    /** Where its right shoulder is then (the same way). */
    static float[] shoulder(Heal h,float t){
        float[] w=new float[NAMES.size()];stances(h,t,w);
        float[] a=GoldRush.SHOULDERS[GUARD][1],b=GoldRush.SHOULDERS[MEND][1];
        return new float[]{a[0]*w[GUARD]+b[0]*w[MEND],a[1]*w[GUARD]+b[1]*w[MEND],a[2]*w[GUARD]+b[2]*w[MEND]};
    }
    /** How much the fist's light is on {@code t} seconds in (0 to 1): up as it winds, on while it is in, out as it is drawn out. */
    static float glow(Heal h,float t){return smooth((t-.04f)/.14f)*(1-back(h,t));}
    /** How much the healing through the fist is on (0 to 1): from the moment it is in until it is drawn out. */
    static float on(Heal h,float t){return smooth((t-TOUCH+.02f)/.05f)*(1-smooth((t-leaves(h))/.1f));}

    /** Whether healing {@code h} shows {@code t} seconds into it. */
    static boolean shows(Heal h,float t){return t>=0&&t<=guarded(h)+LINGER+RETURN;}
    /**
     * Healing {@code h} this frame on a look that already holds the stand's pose: its stances take over from it (and
     * give way again as it goes back), and {@code shape} says where it stands; {@code facing} the way its user faces as
     * the stand follows it (the hold stands by that). Returns whether it shows.
     */
    static boolean pose(Heal h,float delta,GoldExperienceLook look,GoldRush.Shape shape,float[] scratch,float facing){
        float t=h.age(delta);if(!shows(h,t))return false;
        java.util.Arrays.fill(scratch,0);
        stances(h,t,scratch);
        float end=guarded(h),in=smooth(t/TAKE)*(1-smooth((t-end-LINGER)/RETURN));
        for(int i=0;i<scratch.length;i++)look.weights[i]=look.weights[i]*(1-in)+scratch[i]*in;
        float size=arms(h,t);
        look.armL=look.armL*(1-in)+size*in;look.armR=look.armR*(1-in)+size*in;
        shape.engage=StandPunch.burst(t/StandRush.RISE)*(1-smooth((t-end-LINGER)/RETURN));shape.raise=GoldRush.RAISE;
        if(h.self){shape.yaw=facing;shape.ahead=selfPlace()-press(h,t);shape.side=SIDE;shape.inward=1;shape.raise=GoldRush.RAISE-LOWER;}
        else{shape.yaw=h.yaw;shape.ahead=place(h);shape.side=0;shape.inward=0;}
        return true;
    }

    // ───────────────────────────── what others ask ─────────────────────────────

    /** This player's healing (null if none was heard). */
    public static Heal of(UUID id){return HEALS.get(id);}
    static Map<UUID,Heal> all(){return HEALS;}
    /** Whether this player's stand is healing them now (Y held): they walk slowly and do nothing else. */
    public static boolean selfing(UUID id){var h=HEALS.get(id);return h!=null&&h.self&&Float.isNaN(h.ended)&&h.age(0)<GoldExperienceRules.SELF_LENGTH/20f+.1f;}
    public static boolean localSelfing(){var p=MinecraftClient.getInstance().player;return p!=null&&selfing(p.getUuid());}
    /** The world tick this player's Y is ready again (0 if none was used, or it is still on: its cooldown runs from its end). */
    public static long ready(UUID id){var h=HEALS.get(id);return h==null?0:h.ready;}
    /** This player's stand is not Gold Experience: its cooldown is gone, as the server forgets it. */
    public static void forget(UUID id){var h=HEALS.get(id);if(h!=null)h.ready=0;}
    /** For the checks: this player's healing as "self,age,touched,whom,healed,blows,ended,stopped", or null. */
    public static String describe(UUID id,float delta){
        var h=HEALS.get(id);if(h==null)return null;
        return String.format(java.util.Locale.ROOT,"%d,%.2f,%d,%d,%.1f,%d,%d,%d",h.self?1:0,h.age(delta),h.touched()?1:0,h.whom,h.healed,h.blows,Float.isNaN(h.ended)?0:1,Float.isNaN(h.stopped)?0:1);
    }

    /** Y for Gold Experience's user on the map: tapped, another's healing; held, their own. Returns whether it took the key. */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(key!=GLFW.GLFW_KEY_Y||window!=mc.getWindow().getHandle())return false;
        if(action==GLFW.GLFW_RELEASE){
            if(!heldY)return false;
            // let go before the hold was asked for: the tap; after: the hold stops
            if(asked)send(GoldExperienceHeal.RELEASE);
            else if(usable(mc))send(GoldExperienceHeal.TAP);
            heldY=asked=false;return true;
        }
        if(heldY)return true;
        if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.goldExperience(mc.player))return false;
        heldY=true;asked=false;downAt=StandRush.ticks;
        return true;
    }
}
