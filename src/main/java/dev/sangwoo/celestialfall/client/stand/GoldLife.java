package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.stand.GoldExperienceLife;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import dev.sangwoo.celestialfall.stand.GoldFlyEntity;
import dev.sangwoo.celestialfall.stand.GoldFlyPath;
import dev.sangwoo.celestialfall.stand.StandManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;

/**
 * Gold Experience's T, giving life, as it is drawn, and its key (2.151.0: GoldExperienceRules has the user's words). The
 * server runs it (GoldExperienceLife); here is what the stand and the bodies do through it, on its own clock.
 * <ul>
 * <li>An item in the hand: its user holds it up before them and it turns of itself (2.151.1: the stand plays no part in
 * it, user request "아이템 개구리로 바꿀때는 스탠드가 아이템 안 때려도 돼 손에 들고 있는게 자동으로 개구리로 변하게"):
 * light gathers into it, and as the server's FROG_BORN comes it shrinks away in a flash of gold and a frog grows in their
 * hand, which hops onto their other shoulder and sits there (GoldFrog) until its time is nearly up, when they hold the
 * hand up again, it hops back into it and is the item once more as the server gives it back.</li>
 * <li>The hand empty: the stand comes round to the front and from its guard drives a short punch into the first one in
 * its way (the overdrive's stances, quicker), landing as the server's blow does; the part it strikes off flies out and
 * becomes the fly (GoldFlyEntity, drawn by GoldFlyRenderer), the part missing from their body while it lives.</li>
 * <li>Swatted, whom it belonged to falls on their back and lies a second, then gets up.</li>
 * </ul>
 * T is the key (Gold Experience's user's on the map, as Star Platinum's Star Finger is its user's); chat is on Enter for
 * them. Every moment is a function of the clocks.
 * <p>
 * Its sounds (2.153.0, laid together from recordings: tools/stand/make_gold_sounds.py) come on those moments (GoldSounds):
 * the light gathering into the item, its pop and the frog's first croak, the hop up, a croak and a ting with every blow
 * turned back, the hop back and the item's return (or a burst, ended early); the punch through the air and its landing
 * (the part popping off), the swat and the fall of whom the fly belonged to. The fly's own (its buzz, as it becomes the
 * fly and as it goes back) are GoldFlySounds'.
 */
public final class GoldLife {
    /** The frog's moments (seconds on its clock): the hand is up, the item turns into the frog (the server's FROG_BORN), it has become the frog, its hop to the shoulder, the hand down. */
    static final float RAISED=.18f,BORN=GoldExperienceRules.FROG_BORN/20f,MORPHED=BORN+.14f,HOP_FROM=BORN+.16f,HOP_TO=BORN+.46f,LOWER_FROM=.5f,LOWER_TO=.78f;
    /** The light gathering into the item held up before it turns (seconds): from, full as it turns, and how long it takes to give way to the turning's bloom. */
    static final float GATHER_FROM=.08f,GATHER_GONE=.12f;
    /** Its end (the server's FROG_END), and its moments before it: the hand up again, the hop back into it, the frog the item again. */
    static final float END=GoldExperienceRules.FROG_END/20f,RAISE_AGAIN=END-.8f,RAISED_AGAIN=END-.6f,HOP_BACK=END-.55f,HOPPED_BACK=END-.27f,UNMORPH=END-.24f,LOWER_AGAIN=END+.05f,LOWERED_AGAIN=END+.32f;
    /** Ended early: how long the frog takes to burst into light where it is. */
    static final float BURST=.22f;
    /** The stand through the punch (seconds): it stays a moment in its guard after it, then goes back; and how long it takes to come in. */
    static final float LINGER=.1f,RETURN=.4f,TAKE=.1f;
    /** The punch (seconds): wound up, loaded until, lands (the server's FLY_CONTACT), held out until, back in the guard; stopped short, back in it. */
    static final float WOUND=.2f,LOAD=.27f,CONTACT=GoldExperienceRules.FLY_CONTACT/20f,HELD=.62f,PUNCH_GUARDED=.86f,CUT=.15f;
    /** How deep the punch's wind-up goes (of the overdrive's), how big its arms are, how far in front its user it stands with no one known and no nearer or further, and how deep its fist goes in. */
    static final float WIND_DEPTH=.6f,ARMS=1.08f,AHEAD=1.05f,NEAR=.75f,FAR=2.1f,BITE=.15f;
    /** Where the raised hand holds the item (blocks: ahead, to the side of the hand, up, for a body 1.8 tall). */
    static final double HAND_AHEAD=.7,HAND_SIDE=.33,HAND_UP=1.58;
    /** Swatted: whom the fly belonged to falls on their back, lies, and is up again (seconds). */
    static final float FALLEN=.22f,GETS_UP=.7f,DOWN=GoldExperienceRules.FLY_DOWN/20f;
    /**
     * The sounds' moments (seconds on the T's clock): the light gathering (it swells to the item's turning at BORN), the
     * pop and the frog's croak (BORN: its croak in the file as the frog has grown, MORPHED), the hop up (its landing as the
     * frog sits, HOP_TO), the hop back and the item's return (its pop at UNMORPH); the punch's rush through the air (at
     * its loudest as the fist lands).
     */
    static final float GATHER_SOUND=.05f,PUNCH_SOUND=.25f;
    static final GoldSounds.Cue[] FROG_SOUNDS={
        new GoldSounds.Cue(GATHER_SOUND,"gold_life_gather",1),new GoldSounds.Cue(BORN,"gold_life_born",1),
        new GoldSounds.Cue(HOP_FROM,"gold_life_hop",1),new GoldSounds.Cue(HOP_BACK,"gold_life_frog_back",1)};
    static final GoldSounds.Cue[] PUNCH_SOUNDS={new GoldSounds.Cue(PUNCH_SOUND,"gold_life_punch",1)};
    /** The least time between the croaks of blows turned back (seconds): a barrage's blows croak now and then, not each. */
    static final float CROAKS=.2f;

    /** A T as heard. */
    public static final class Life {
        public final boolean frog;public final long start;final long serverAge,received;final float yaw,aim;final int seed;
        /** The frog: the item it is (what the event carried) and its look (the frog's skin: 0 temperate, 1 warm, 2 cold). */
        final ItemStack item;final int variant;
        /** The frog: its clock when it ended early (NaN: it did not), and the blows it turned back. */
        float early=Float.NaN;final List<Turned> reflected=new ArrayList<>();
        /** The punch: its clock when it was stopped short and when it landed (NaN: not), on whom, whether a guard took it, the part and its fly. */
        float stopped=Float.NaN,landedAt=Float.NaN;int struck=-1,part=-1,fly=-1;boolean guarded;
        long ready;
        /** Its sounds (2.153.0): the frog's sheet or the punch's; and the frog's last croak (its clock). */
        final GoldSounds.Run sound;float croaked=-9;
        Life(boolean frog,long start,long serverAge,long received,float yaw,float aim,int seed,ItemStack item){
            this.frog=frog;this.start=start;this.serverAge=serverAge;this.received=received;this.yaw=yaw;this.aim=aim;this.seed=seed;this.item=item;
            variant=Math.floorMod(seed,3);
            sound=new GoldSounds.Run(frog?FROG_SOUNDS:PUNCH_SOUNDS);
        }
        /** Seconds on its clock this frame. */
        public float age(float delta){return (serverAge+StandRush.ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        public boolean landed(){return !Float.isNaN(landedAt);}
        public int seed(){return seed;}
    }
    /** A blow the frog turned back: its clock then (seconds), who struck it, how much went back to them. */
    record Turned(float at,int striker,float amount){}
    /** Whom a fly belonged to, swatted: the client tick it came and the part. */
    record Down(long at,int part,Vec3d where){
        float age(float delta){return (StandRush.ticks-at+Math.max(0,Math.min(1,delta)))/20f;}
    }
    private static final Map<UUID,Life> LIVES=new HashMap<>();
    private static final Map<Integer,Down> DOWNS=new HashMap<>();
    /** Each body's parts that are flies now (entity id -> bits by GoldExperienceRules' parts), from the flies this client has. */
    private static final Map<Integer,Integer> MISSING=new HashMap<>();
    private static Object world;
    private static boolean heldT;
    /** Where the hand holding the item up and the frog were drawn this frame (world), for their light (GoldLifeFx): this frame's count. */
    private static final Map<UUID,Vec3d[]> DRAWN=new HashMap<>();private static final Map<UUID,Long> DRAWN_AT=new HashMap<>();
    static long frame;
    private GoldLife(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(GoldExperienceLife.EVENT,(mc,handler,b,response)->{
            int type=b.readUnsignedByte();UUID user=b.readUuid();Identifier dimension=b.readIdentifier();
            long start=b.readLong(),serverNow=b.readLong();float yaw=b.readFloat(),aim=b.readFloat();long ready=b.readLong();
            int whom=b.readInt();float amount=b.readFloat();boolean flag=b.readBoolean();int part=b.readByte();int fly=b.readInt();
            ItemStack item=b.readItemStack();
            Vec3d where=type==GoldExperienceLife.SWATTED&&b.readableBytes()>=24?new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()):null;
            if(!Float.isFinite(yaw))return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){stopSounds(mc);LIVES.clear();DOWNS.clear();MISSING.clear();world=mc.world;}
                event(mc,user,type,start,serverNow,yaw,aim,ready,whom,amount,flag,part,fly,item,where);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->{stopSounds(mc);LIVES.clear();DOWNS.clear();MISSING.clear();world=null;heldT=false;}));
        ClientTickEvents.END_CLIENT_TICK.register(GoldLife::tick);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(c->frame++);
        // a fly is only there for whom it belongs to: nobody else's crosshair or drawn hits find it
        GoldFlyEntity.clientHits=f->{var p=MinecraftClient.getInstance().player;return p!=null&&f.ownedBy(p);};
    }
    private static void event(MinecraftClient mc,UUID id,int type,long start,long serverNow,float yaw,float aim,long ready,int whom,float amount,boolean flag,int part,int fly,ItemStack item,Vec3d where){
        var l=LIVES.get(id);
        switch(type){
            case GoldExperienceLife.FROG,GoldExperienceLife.PUNCH->{
                if(l!=null)l.sound.cut();
                l=new Life(type==GoldExperienceLife.FROG,start,serverNow-start,StandRush.ticks,yaw,aim,(int)(start*31+id.getLeastSignificantBits()),item);
                l.ready=ready;LIVES.put(id,l);
                if(l.frog)GoldFrog.born(id);
            }
            case GoldExperienceLife.FROG_GONE->{
                if(l==null||!l.frog||l.start!=start)return;
                l.ready=ready;
                if(flag&&Float.isNaN(l.early)){
                    l.early=Math.max(0,(serverNow-start)/20f);
                    // ended early: what was to come is let go, and the frog bursts (if it had been born)
                    l.sound.cut();
                    if(l.early>=BORN)GoldSounds.play(mc,"gold_life_frog_burst",volume(mc,id,-1,null),1);
                }
            }
            case GoldExperienceLife.REFLECTED->{
                if(l==null||!l.frog||l.start!=start)return;
                float at=(serverNow-start)/20f;
                l.reflected.add(new Turned(at,whom,amount));
                GoldFrog.croak(id,at);
                // a croak and a ting going back (a barrage's blows now and then)
                if(at-l.croaked>=CROAKS){
                    l.croaked=at;float v=volume(mc,id,whom,null);
                    GoldSounds.play(mc,"gold_life_croak",v,1);GoldSounds.play(mc,"gold_life_reflect",v*.8f,1);
                }
            }
            case GoldExperienceLife.LANDED->{
                if(l==null||l.frog||l.start!=start)return;
                l.ready=ready;l.landedAt=(serverNow-start)/20f;l.struck=whom;l.guarded=flag;l.part=part;l.fly=fly;
                // it lands: the blow and the part popping off
                if(whom>=0)GoldSounds.play(mc,"gold_life_strike",volume(mc,id,whom,null),1);
            }
            case GoldExperienceLife.STOPPED->{
                if(l==null||l.frog||l.start!=start)return;
                l.ready=ready;if(Float.isNaN(l.stopped))l.stopped=Math.max(0,l.age(0));
                if(!l.landed())l.sound.cut();
            }
            case GoldExperienceLife.SWATTED->{
                DOWNS.put(whom,new Down(StandRush.ticks,part,where));
                // the swat where the fly was, and whom it belonged to coming down on their back (FALLEN on)
                float v=volume(mc,id,whom,where);
                GoldSounds.play(mc,"gold_life_swat",v,1);
                if(v>.01f)mc.getSoundManager().play(new GoldSounds.Sound("gold_life_fall",v,1),Math.round(FALLEN*20));
            }
            case GoldExperienceLife.FLY_GONE->{if(l!=null&&!l.frog)l.ready=ready;}
            default->{}
        }
    }
    /**
     * How loud T's sounds are here: all of it for its user and whom it is for ({@code whom}, an entity id, -1 none),
     * fading out from where its user, they or {@code at} are.
     */
    static float volume(MinecraftClient mc,UUID user,int whom,Vec3d at){
        if(mc.world==null||mc.player==null)return 0;
        var u=mc.world.getPlayerByUuid(user);var w=whom>=0?mc.world.getEntityById(whom):null;
        boolean ours=mc.player.getUuid().equals(user)||whom>=0&&mc.player.getId()==whom;
        return GoldSounds.volume(mc,ours,u==null?null:u.getPos(),w==null?null:w.getPos(),at);
    }
    private static void stopSounds(MinecraftClient mc){for(var l:LIVES.values())l.sound.stop(mc);}
    private static void tick(MinecraftClient mc){
        MISSING.clear();
        if(mc.world==null)return;
        if(!mc.isPaused())for(var e:LIVES.entrySet()){
            var l=e.getValue();float t=l.age(0);if(t>(l.frog?LOWERED_AGAIN+1:3))continue;
            l.sound.tick(mc,t,volume(mc,e.getKey(),l.frog?-1:l.struck,null));
        }
        for(var e:mc.world.getEntities())if(e instanceof GoldFlyEntity f&&f.isAlive())MISSING.merge(f.ownerId(),1<<Math.max(0,Math.min(4,f.part())),(a,b)->a|b);
        DOWNS.values().removeIf(d->d.age(0)>DOWN+.5f);
        // (a punch's kept while its fly lives: the fly's end brings its cooldown)
        LIVES.values().removeIf(l->l.age(0)>(l.frog?LOWERED_AGAIN+1:waitsForFly(l)?GoldExperienceRules.FLY_GONE/20f+10:3)&&l.ready<=mc.world.getTime());
    }
    /** Whether a punch's cooldown waits on its fly (it struck one off and the fly's end has not been heard). */
    static boolean waitsForFly(Life l){return !l.frog&&l.fly>=0&&l.ready==0;}
    private static boolean usable(MinecraftClient mc){return MapClient.enabled()&&StandManager.goldExperience(mc.player)&&GoldPresence.out(mc.player)&&CombatClient.combatInput();}

    // ───────────────────────────── the frog's moments ─────────────────────────────

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** When the frog is over (seconds): its end, or its early end's burst. */
    static float over(Life l){return Float.isNaN(l.early)?LOWERED_AGAIN:l.early+BURST;}
    /** How far up the hand holding the item is {@code t} seconds into the frog (0 down, 1 held up before them). */
    static float raised(Life l,float t){
        float up=smooth(t/RAISED)*(1-smooth((t-LOWER_FROM)/(LOWER_TO-LOWER_FROM)));
        if(!Float.isNaN(l.early))return t<l.early?up:up*(1-smooth((t-l.early)/.2f));
        return Math.max(up,smooth((t-RAISE_AGAIN)/(RAISED_AGAIN-RAISE_AGAIN))*(1-smooth((t-LOWER_AGAIN)/(LOWERED_AGAIN-LOWER_AGAIN))));
    }
    /** How big the item is in the hand (0 to 1): there until it turns, shrinking away into the frog; at the end growing back out of it. */
    static float itemShown(Life l,float t){
        float going=1-smooth((t-BORN)/(MORPHED-BORN-.03f));
        if(!Float.isNaN(l.early))return going;
        return t<END-1?going:smooth((t-UNMORPH-.04f)/(END-UNMORPH-.04f))*(1-smooth((t-LOWERED_AGAIN+.04f)/.04f));
    }
    /** How big the frog is (0 to 1, a little over as it pops out): out of the item as it turns; back into it at the end, or bursting early. */
    static float frogShown(Life l,float t){
        float u=(t-BORN-.03f)/(MORPHED-BORN-.03f);if(u<=0)return 0;
        float grown=u>=1?1:(float)(1+.18*Math.sin(Math.PI*u))*smooth(u);
        if(!Float.isNaN(l.early)){float k=(t-l.early)/BURST;return k<=0?grown:k>=1?0:grown*(1+.35f*k)*(1-k*k);}
        return grown*(1-smooth((t-UNMORPH)/(END-UNMORPH-.04f)));
    }
    /** Where the frog is between the raised hand (0) and the shoulder (1) {@code t} seconds in, and how far through a hop it is (for its leap), as {where, leap}. */
    static float[] seat(Life l,float t){
        float to=smooth((t-HOP_FROM)/(HOP_TO-HOP_FROM));
        if(!Float.isNaN(l.early))return new float[]{t<HOP_FROM?0:to,hop(t,HOP_FROM,HOP_TO)};
        float back=smooth((t-HOP_BACK)/(HOPPED_BACK-HOP_BACK));
        return new float[]{to*(1-back),Math.max(hop(t,HOP_FROM,HOP_TO),hop(t,HOP_BACK,HOPPED_BACK))};
    }
    /** How far through a hop from {@code a} to {@code b} it is (0 to 1; 0 outside it). */
    static float hop(float t,float a,float b){return t<=a||t>=b?0:(t-a)/(b-a);}
    /** Whether the frog shows {@code t} seconds in. */
    static boolean frogShows(Life l,float t){return t>=0&&t<=over(l);}
    /**
     * How much light has gathered into the item held up {@code t} seconds in (0 to 1): from as the hand comes up, faster
     * and faster, full as it turns into the frog, then gone with its flash; nothing if it ended before it turned.
     */
    static float gathered(Life l,float t){
        if(!l.frog||t<GATHER_FROM||t>=BORN+GATHER_GONE||!Float.isNaN(l.early)&&l.early<BORN)return 0;
        float u=MathHelper.clamp((t-GATHER_FROM)/(BORN-GATHER_FROM),0,1);
        return u*u*(1-smooth((t-BORN)/GATHER_GONE));
    }

    // ───────────────────────────── the stand ─────────────────────────────

    /** The punch: how far wound up (0 to WIND_DEPTH) and how far thrown (0 to 1) it is {@code t} seconds in. */
    static float wind(float t){return WIND_DEPTH*smooth(t/WOUND);}
    static float strike(float t){float u=MathHelper.clamp((t-LOAD)/(CONTACT-LOAD),0,1);return (float)Math.pow(u,1.6);}
    /** The punch's way back to its guard (0 held out, 1 in it), after the blow or from where it was stopped short. */
    static float back(Life l,float t){
        float k=smooth((t-HELD)/(PUNCH_GUARDED-HELD));
        return Float.isNaN(l.stopped)?k:Math.max(k,smooth((t-l.stopped)/CUT));
    }
    /** When the stand's part in the punch is done (back in its guard, seconds). */
    static float done(Life l){return Float.isNaN(l.stopped)?PUNCH_GUARDED:Math.min(PUNCH_GUARDED,l.stopped+CUT);}
    /** Adds the punch's stances {@code t} seconds into it into {@code w} (the frog has none: the stand plays no part in it). */
    public static void stances(Life l,float t,float[] w){
        if(l.frog)return;
        float at=Float.isNaN(l.stopped)?t:Math.min(t,l.stopped);
        float wind=wind(at),strike=strike(at),back=back(l,t);
        w[GUARD]+=(1-wind)*(1-strike)*(1-back)+back;
        w[OVER_WIND]+=wind*(1-strike)*(1-back);
        w[OVER_PUNCH]+=strike*(1-back);
    }
    /** Whether the stand shows in it {@code t} seconds in: through the punch (never the frog's: it turns of itself). */
    static boolean shows(Life l,float t){return !l.frog&&t>=0&&t<=done(l)+LINGER+RETURN;}
    /** How big its arms are. */
    static float arms(Life l,float t){return l.frog?1:1+(ARMS-1)*smooth(t/StandRush.RISE)*(1-back(l,t));}
    /** How far in front of its user it stands for the punch: its fist at full stretch BITE into the first one in its way. */
    static float place(Life l){
        if(Float.isNaN(l.aim))return AHEAD;
        return MathHelper.clamp(l.aim+BITE-GoldRush.grown(OVER_PUNCH,1,ARMS)[0],NEAR,FAR);
    }
    /** Where its right fist's knuckles are (ahead, right, up from its feet, at its size 1). */
    static float[] fist(Life l,float t){
        float[] w=new float[NAMES.size()];stances(l,t,w);
        float size=arms(l,t);float[] out=new float[3];
        for(int s:new int[]{GUARD,OVER_WIND,OVER_PUNCH}){
            if(w[s]<=0)continue;
            float[] f=GoldRush.grown(s,1,size);
            for(int i=0;i<3;i++)out[i]+=f[i]*w[s];
        }
        return out;
    }
    /** How much the stand's fist's light is on through the punch (0 to 1; none for the frog). */
    static float glow(Life l,float t){
        if(l.frog)return 0;
        float c=smooth((t-.05f)/.2f)*(1-strike(t));
        return Float.isNaN(l.stopped)?c:c*(1-smooth((t-l.stopped)/CUT));
    }
    /**
     * T this frame on a look that already holds the stand's pose: the punch's stances take over (and give way again as it
     * goes back), and {@code shape} says where it stands: in front of its user facing the way it goes. Returns whether it
     * shows: never for the frog (2.151.1), which the item in the hand turns into of itself, the stand where it was.
     */
    static boolean pose(Life l,float delta,GoldExperienceLook look,GoldRush.Shape shape,float[] scratch){
        if(l.frog)return false;
        float t=l.age(delta);if(!shows(l,t))return false;
        java.util.Arrays.fill(scratch,0);stances(l,t,scratch);
        float end=done(l),in=smooth(t/TAKE)*(1-smooth((t-end-LINGER)/RETURN));
        for(int i=0;i<scratch.length;i++)look.weights[i]=look.weights[i]*(1-in)+scratch[i]*in;
        float size=arms(l,t);look.armL=look.armL*(1-in)+size*in;look.armR=look.armR*(1-in)+size*in;
        shape.engage=StandPunch.burst(t/StandRush.RISE)*(1-smooth((t-end-LINGER)/RETURN));
        shape.yaw=l.yaw;shape.ahead=place(l);shape.raise=GoldRush.RAISE;shape.side=0;shape.inward=0;
        return true;
    }

    // ───────────────────────────── its user's hand ─────────────────────────────

    /**
     * Its user's arm holding the item up before them (the frog's start and end), over whatever pose they had: the main
     * arm raised a little above level, straight, toward their middle. MotionPlayerModel calls it last.
     */
    public static void arm(net.minecraft.client.render.entity.model.PlayerEntityModel<?> model,PlayerEntity player,float delta){
        var l=LIVES.get(player.getUuid());if(l==null||!l.frog)return;
        float w=raised(l,l.age(delta));if(w<=0)return;
        boolean right=player.getMainArm()!=Arm.LEFT;
        ModelPart arm=right?model.rightArm:model.leftArm;
        arm.pitch=MathHelper.lerp(w,arm.pitch,(float)Math.toRadians(-100));
        arm.yaw=MathHelper.lerp(w,arm.yaw,(float)Math.toRadians(right?-8:8));
        arm.roll=MathHelper.lerp(w,arm.roll,0);
        if(arm.hasChild("elbow")){var elbow=arm.getChild("elbow");elbow.pitch=MathHelper.lerp(w,elbow.pitch,0);}
        var sleeve=right?model.rightSleeve:model.leftSleeve;sleeve.copyTransform(arm);
        if(sleeve.hasChild("elbow")&&arm.hasChild("elbow"))sleeve.getChild("elbow").copyTransform(arm.getChild("elbow"));
    }

    // ───────────────────────────── the fly ─────────────────────────────

    /** Where {@code fly} is this frame (its middle), about whom it belongs to as this client draws them; null before it has been ticked here. */
    public static Vec3d flyAt(GoldFlyEntity fly,float delta){
        if(!fly.clientReady())return null;
        var mc=MinecraftClient.getInstance();
        if(!(mc.world.getEntityById(fly.ownerId()) instanceof LivingEntity o))return fly.getLerpedPos(delta).add(0,fly.getHeight()/2,0);
        var at=o.getLerpedPos(delta);float heading=MathHelper.lerpAngleDegrees(delta,fly.prevClientHeading,fly.clientHeading);
        var p=GoldFlyPath.at(fly.seed(),fly.part(),fly.clientAge(delta),at.x,at.y,at.z,heading,o.getStandingEyeHeight(),o.getHeight(),fly.from());
        return new Vec3d(p[0],p[1],p[2]);
    }
    /** How big the part flying off is {@code u} of the way through it (whole, then shrinking into the fly: GoldFlyRenderer). */
    static float partShown(double u){return u>=1?0:1-smooth((float)((u-.58)/.3));}
    /** How big the fly is {@code age} ticks after the blow: growing out of the part, whole, shrinking away as it reaches the part's place. */
    static float flyShown(double age){
        double u=age/GoldExperienceRules.FLY_FORMS;
        if(u<1)return smooth((float)((u-.62)/.38));
        double back=(age-GoldExperienceRules.FLY_FORMS-GoldExperienceRules.FLY_LASTS)/GoldExperienceRules.FLY_RETURN;
        return back<=.55?1:1-smooth((float)((back-.55)/.45));
    }
    /** The parts of {@code e} that are flies now (bits by GoldExperienceRules' parts). */
    public static int missing(Entity e){var m=MISSING.get(e.getId());return m==null?0:m;}
    /** Whether part {@code part} of {@code e} is a fly now. */
    public static boolean missing(Entity e,int part){return (missing(e)&(1<<part))!=0;}
    /** Hides the parts of a body that are flies now (and their outer layers): MotionPlayerModel and the dummy call it after posing. */
    public static void hide(net.minecraft.client.render.entity.model.BipedEntityModel<?> model,Entity e){
        int m=missing(e);if(m==0)return;
        boolean player=model instanceof net.minecraft.client.render.entity.model.PlayerEntityModel<?>;
        var pm=player?(net.minecraft.client.render.entity.model.PlayerEntityModel<?>)model:null;
        if((m&1<<GoldExperienceRules.HEAD)!=0){model.head.visible=false;model.hat.visible=false;}
        if((m&1<<GoldExperienceRules.RIGHT_ARM)!=0){model.rightArm.visible=false;if(pm!=null)pm.rightSleeve.visible=false;}
        if((m&1<<GoldExperienceRules.LEFT_ARM)!=0){model.leftArm.visible=false;if(pm!=null)pm.leftSleeve.visible=false;}
        if((m&1<<GoldExperienceRules.RIGHT_LEG)!=0){model.rightLeg.visible=false;if(pm!=null)pm.rightPants.visible=false;}
        if((m&1<<GoldExperienceRules.LEFT_LEG)!=0){model.leftLeg.visible=false;if(pm!=null)pm.leftPants.visible=false;}
    }
    /** Whether the hand {@code arm} of {@code e} is a fly now (it holds nothing then). */
    public static boolean handGone(LivingEntity e,Arm arm){return missing(e,arm==Arm.RIGHT?GoldExperienceRules.RIGHT_ARM:GoldExperienceRules.LEFT_ARM);}
    /**
     * Whether what the hand {@code arm} of {@code e} holds is not drawn this frame: it is a fly, or it is holding up the
     * item that becomes the frog (or takes it back), which GoldFrog draws in it alone.
     */
    public static boolean handHides(LivingEntity e,Arm arm){
        if(!MapClient.enabled())return false;
        if(handGone(e,arm))return true;
        if(!(e instanceof PlayerEntity p)||arm!=p.getMainArm())return false;
        var l=LIVES.get(p.getUuid());if(l==null||!l.frog)return false;
        float t=l.age(MinecraftClient.getInstance().getTickDelta());
        return t>=0&&t<=over(l)&&raised(l,t)>.02f;
    }

    // ───────────────────────────── knocked down ─────────────────────────────

    static final R6.Pose STAND=R6.pose().rArm(-84,10).lArm(-84,-10);
    /** Struck down: the arms flung up and out, the head thrown back. */
    static final R6.Pose FALLING=R6.pose().rArm(8,50).lArm(8,-50).rLeg(-72,8).lLeg(-82,-6).head(0,-22,0);
    /** On their back: the arms out to the sides, the legs straight and a little apart, the head back. */
    static final R6.Pose LYING=R6.pose().rArm(-18,78).lArm(-18,-78).rLeg(-86,8).lLeg(-86,-8).head(0,-14,0);
    static final Anim DOWN_BODY=Anim.of(Anim.k(0,STAND),Anim.k(.12,FALLING),Anim.k(FALLEN,LYING),Anim.k(GETS_UP,LYING),Anim.k(DOWN,STAND));
    /** How far over on their back they are (0 standing, 1 flat) {@code t} seconds after the swat. */
    static float fallen(float t){return smooth(t/FALLEN)*(1-smooth((t-GETS_UP)/(DOWN-GETS_UP)));}
    /** Whether {@code e} lies knocked down by their own fly's swat now. */
    public static boolean down(Entity e){var d=DOWNS.get(e.getId());return d!=null&&d.age(0)<DOWN;}
    /** The body of one knocked down this frame (null if they are not): MotionPlayerModel and the dummy's model. */
    public static MotionPose downPose(Entity e,float delta){
        var d=DOWNS.get(e.getId());if(d==null)return null;
        float t=d.age(delta);if(t<0||t>=DOWN)return null;
        return DOWN_BODY.frame(t).joints();
    }
    /** Turns the body of one knocked down onto their back about their feet (KagenoStaggerMixin, TrainingDummyRenderer). */
    public static void turn(LivingEntity e,float delta,MatrixStack m){
        var d=DOWNS.get(e.getId());if(d==null)return;
        float k=fallen(d.age(delta));if(k<=0)return;
        // (lifted a little as it goes over: its back lies on the ground, not in it; after setupTransforms +x tips the head back)
        m.translate(0,.13*k,0);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(88*k));
    }

    // ───────────────────────────── what others ask ─────────────────────────────

    /**
     * Where {@code hand} and {@code frog} (the body's model space, the stack's base turning the world to the view) are
     * drawn in the world this frame, kept for {@code p}'s frog's light (GoldFrog as it draws them; a shadow pass of a
     * shader pack draws from somewhere else: only near the player).
     */
    static void drawn(PlayerEntity p,MatrixStack m,org.joml.Vector3f hand,org.joml.Vector3f frog){
        var mc=MinecraftClient.getInstance();
        var back=com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix();var cam=mc.gameRenderer.getCamera().getPos();
        Vec3d[] at=new Vec3d[2];int k=0;
        for(var v:new org.joml.Vector3f[]{hand,frog}){
            var o=m.peek().getPositionMatrix().transformPosition(new org.joml.Vector3f(v));back.transform(o);
            at[k++]=cam.add(o.x,o.y,o.z);
        }
        if(at[0].squaredDistanceTo(p.getLerpedPos(mc.getTickDelta()).add(0,1,0))>9)return;
        DRAWN.put(p.getUuid(),at);DRAWN_AT.put(p.getUuid(),frame);
    }
    /** Where {@code id}'s raised hand (0) or frog (1) was drawn this frame (null if it was not). */
    static Vec3d drawn(UUID id,int which){var at=DRAWN_AT.get(id);return at==null||at!=frame?null:DRAWN.get(id)[which];}

    /** This player's T (null if none was heard). */
    public static Life of(UUID id){return LIVES.get(id);}
    static Map<UUID,Life> all(){return LIVES;}
    static Map<Integer,Down> downs(){return DOWNS;}
    /** The world tick this player's T is ready again (0 if none was used, or its frog or fly is still on: its cooldown runs from their end). */
    public static long ready(UUID id){var l=LIVES.get(id);return l==null?0:l.ready;}
    /** This player's stand is not Gold Experience: its cooldown is gone, as the server forgets it. */
    public static void forget(UUID id){var l=LIVES.get(id);if(l!=null)l.ready=0;}
    /** For the checks: this player's T as "frog,age,early,reflected,landed,struck,guarded,part,fly,stopped", or null. */
    public static String describe(UUID id,float delta){
        var l=LIVES.get(id);if(l==null)return null;
        return String.format(java.util.Locale.ROOT,"%d,%.2f,%d,%d,%d,%d,%d,%d,%d,%d",l.frog?1:0,l.age(delta),Float.isNaN(l.early)?0:1,l.reflected.size(),l.landed()?1:0,l.struck,l.guarded?1:0,l.part,l.fly,Float.isNaN(l.stopped)?0:1);
    }

    /** T for Gold Experience's user on the map: giving life, if the stand is out. Returns whether it took the key. Chat is on Enter for them. */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle())return false;
        if(key==GLFW.GLFW_KEY_ENTER&&action==GLFW.GLFW_PRESS&&mc.currentScreen==null&&MapClient.enabled()&&StandManager.goldExperience(mc.player)){
            mc.setScreen(new net.minecraft.client.gui.screen.ChatScreen(""));return true;
        }
        if(key!=GLFW.GLFW_KEY_T)return false;
        if(action==GLFW.GLFW_RELEASE){if(!heldT)return false;heldT=false;return true;}
        if(heldT)return true;
        if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.goldExperience(mc.player))return false;
        heldT=true;
        if(usable(mc)&&ClientPlayNetworking.canSend(GoldExperienceLife.INPUT)){
            BotandCamera.prepareInteraction();
            // the way it goes is the way the player faces now: the turn goes to the server before the press
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
            ClientPlayNetworking.send(GoldExperienceLife.INPUT,PacketByteBufs.empty());
        }
        return true;
    }
}
