package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceOverdrive;
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
 * Gold Experience's overdrive punch as it is drawn (2.148.0, R: GoldExperienceRules has the user's words). The server
 * throws it (GoldExperienceOverdrive: when it began, the way it goes, how far off the first one in its way stands, whom
 * it landed on); here is what the stand does through it, on the punch's own clock: it comes round to the front of its
 * user, and from its guard winds up (dropped low and back, the right fist drawn behind the hip, the left hand out at
 * whoever it is for), holds there a moment, loaded, then throws the right fist faster and faster with its whole body
 * behind it, landing as the server's blow does; it stops dead there at full stretch, holds, and goes back to its guard
 * and to its user's shoulder. Stopped short it turns back to the guard at once. Everything is a function of that clock.
 * R is the key (Gold Experience's user's on the map, as Star Platinum's heavy punch is its user's).
 * <p>
 * Its sound (2.153.0) is the user's recording, the stand's shout "mu-DA!" (tools/stand/cut_gold_sounds.py), its second
 * syllable on the frame the fist lands (SOUND_AT), whether it lands on anyone or not; stopped short before, it fades out.
 */
public final class GoldPunch {
    /** Seconds on the punch's clock: wound up, loaded until, the blow lands (the server's OVER_CONTACT), held out until, back in the guard, over. */
    static final float WOUND=.3f,LOAD=.44f,CONTACT=.6f,HELD=.9f,GUARDED=1.24f,LENGTH=1.4f;
    /** Seconds: stopped short it is back in its guard; it stays a moment in front once in its guard and then goes back. */
    static final float CUT=.15f,LINGER=.12f,RETURN=.4f;
    /** Seconds its stances take to take over from the pose it had. */
    static final float TAKE=.1f;
    /** How much higher than at its user's shoulder it floats in front (the rush's: its fist lands on a body's chest), and how big its arms are through it. */
    static final float RAISE=GoldRush.RAISE,ARMS=1.12f;
    /** How far in front of its user it stands with no one in its way, and no nearer or further than (blocks); how deep its fist goes into whom it meets. */
    static final float AHEAD=1.05f,NEAR=.75f,FAR=2.1f,BITE=.15f;
    /** Where the shout's second syllable is in its file (seconds: tools/stand/cut_gold_sounds.py's DA_AT), so when it is played: that syllable as the fist lands. */
    static final float DA_AT=.2f,SOUND_AT=CONTACT-DA_AT;
    static final GoldSounds.Cue[] SOUND={new GoldSounds.Cue(SOUND_AT,"gold_experience_overdrive",1)};
    /** A punch as heard. */
    public static final class Punch {
        public final long start;final long serverAge,received;final float yaw,aim;final int seed;
        /** Its clock (seconds) when it was stopped short (NaN: it was not) and when its blow landed (NaN: not yet). */
        float stopped=Float.NaN,landedAt=Float.NaN;
        /** Whom its blow met (entity id, -1 no one), whether their guard took it, whether it landed, and where they stood (their middle). */
        int struck=-1;boolean guarded,hit;Vec3d struckAt;
        /** The world tick it is ready again. */
        long ready;
        /** Its shout (2.153.0). */
        final GoldSounds.Run sound=new GoldSounds.Run(SOUND);
        Punch(long start,long serverAge,long received,float yaw,float aim,int seed){this.start=start;this.serverAge=serverAge;this.received=received;this.yaw=yaw;this.aim=aim;this.seed=seed;}
        /** Seconds on its clock this frame. */
        public float age(float delta){return (serverAge+StandRush.ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        /** When it is back in its guard (seconds on its clock). */
        float end(){return Float.isNaN(stopped)?GUARDED:Math.min(GUARDED,stopped+CUT);}
        public boolean landed(){return !Float.isNaN(landedAt);}
        public boolean hit(){return hit;}
        public boolean guarded(){return guarded;}
        public int struck(){return struck;}
        public int seed(){return seed;}
        public float yaw(){return yaw;}
    }
    private static final Map<UUID,Punch> PUNCHES=new HashMap<>();
    private static Object world;
    private static boolean heldR;
    private GoldPunch(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(GoldExperienceOverdrive.EVENT,(mc,handler,b,response)->{
            int type=b.readUnsignedByte();
            if(type==GoldExperienceOverdrive.OUT||type==GoldExperienceOverdrive.BACK){
                int id=b.readInt();Identifier dimension=b.readIdentifier();long now=b.readLong(),until=b.readLong();
                mc.execute(()->{if(MapClient.enabled()&&mc.world!=null&&mc.world.getRegistryKey().getValue().equals(dimension))SoulClient.told(mc,type==GoldExperienceOverdrive.OUT,id,now,until);});
                return;
            }
            UUID user=b.readUuid();Identifier dimension=b.readIdentifier();
            long start=b.readLong(),serverNow=b.readLong();float yaw=b.readFloat(),aim=b.readFloat();long ready=b.readLong();
            int struck=b.readInt();boolean guarded=b.readBoolean();long until=b.readLong();
            if(!Float.isFinite(yaw))return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){PUNCHES.clear();world=mc.world;}
                event(mc,user,type,start,serverNow,yaw,aim,ready,struck,guarded,until);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->{for(var p:PUNCHES.values())p.sound.stop(mc);PUNCHES.clear();world=null;heldR=false;}));
        ClientTickEvents.END_CLIENT_TICK.register(GoldPunch::tick);
    }
    /** The shouts, on their punches' clocks: loud for its user and whom it lands on. */
    private static void tick(MinecraftClient mc){
        if(mc.world==null||mc.isPaused())return;
        for(var e:PUNCHES.entrySet()){
            var p=e.getValue();float t=p.age(0);if(t>LENGTH+2)continue;
            var user=mc.world.getPlayerByUuid(e.getKey());
            boolean ours=mc.player!=null&&(mc.player.getUuid().equals(e.getKey())||mc.player.getId()==p.struck);
            p.sound.tick(mc,t,GoldSounds.volume(mc,ours,user==null?null:user.getPos(),p.struckAt));
        }
    }
    private static void event(MinecraftClient mc,UUID id,int type,long start,long serverNow,float yaw,float aim,long ready,int struck,boolean guarded,long until){
        var p=PUNCHES.get(id);
        switch(type){
            case GoldExperienceOverdrive.PUNCH->{
                p=new Punch(start,serverNow-start,StandRush.ticks,yaw,aim,(int)(start*31+id.getLeastSignificantBits()));
                p.ready=ready;PUNCHES.put(id,p);
            }
            case GoldExperienceOverdrive.LANDED->{
                if(p==null||p.start!=start)return;
                p.ready=ready;p.landedAt=(serverNow-start)/20f;p.struck=struck;p.guarded=guarded;p.hit=until>0;
                if(mc.world.getEntityById(struck) instanceof LivingEntity e)p.struckAt=e.getPos().add(0,e.getHeight()*.6,0);
            }
            case GoldExperienceOverdrive.STOPPED->{
                if(p==null)return;
                p.ready=ready;if(Float.isNaN(p.stopped))p.stopped=Math.max(0,p.age(0));
                if(!p.landed())p.sound.cut();
            }
            default->{}
        }
    }

    // ───────────────────────────── the pose ─────────────────────────────

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** How far wound up it is {@code t} seconds in (0 in its guard, 1 wound up). */
    static float wind(float t){return smooth(t/WOUND);}
    /** How far thrown its fist is (0 wound up, 1 at full stretch as the blow lands): faster and faster, fastest as it lands. */
    static float strike(float t){float u=MathHelper.clamp((t-LOAD)/(CONTACT-LOAD),0,1);return (float)Math.pow(u,1.6);}
    /** How far back in its guard it is after the blow (0 held out, 1 in its guard), or after it was stopped short at {@code stopped} (NaN: it was not). */
    static float back(float t,float stopped){
        float k=smooth((t-HELD)/(GUARDED-HELD));
        return Float.isNaN(stopped)?k:Math.max(k,smooth((t-stopped)/CUT));
    }
    /**
     * Adds the punch's stances {@code t} seconds into it into {@code w}: the guard, wound up, thrown, the guard again.
     * Stopped short at {@code stopped} it turns back to the guard from the pose it had got to then.
     */
    public static void stances(float t,float stopped,float[] w){
        float at=Float.isNaN(stopped)?t:Math.min(t,stopped);
        float wind=wind(at),strike=strike(at),back=back(t,stopped);
        w[GUARD]+=(1-wind)*(1-strike)*(1-back)+back;
        w[OVER_WIND]+=wind*(1-strike)*(1-back);
        w[OVER_PUNCH]+=strike*(1-back);
    }
    /** Whether punch {@code p} shows {@code t} seconds into it. */
    static boolean shows(Punch p,float t){return t>=0&&t<=p.end()+LINGER+RETURN;}
    /** How far round to the front of its user it is (0 at their shoulder, 1 in front). */
    static float engage(Punch p,float t){return StandPunch.burst(t/StandRush.RISE)*(1-smooth((t-p.end()-LINGER)/RETURN));}
    /** How much its fist's light has gathered {@code t} seconds in (0 to 1): up as it winds up, gone as it is thrown. */
    static float charge(Punch p,float t){
        float c=smooth((t-.05f)/.3f)*(1-strike(t));
        return Float.isNaN(p.stopped)?c:c*(1-smooth((t-p.stopped)/CUT));
    }
    /** How far in front of its user it stands for this punch (blocks): so that its fist at full stretch goes BITE into the first one in its way. */
    static float place(Punch p){
        if(Float.isNaN(p.aim))return AHEAD;
        return MathHelper.clamp(p.aim+BITE-fistReach(),NEAR,FAR);
    }
    /** How far ahead of the stand's feet its right fist reaches as the blow lands (blocks, at its size 1). */
    static float fistReach(){return GoldRush.grown(OVER_PUNCH,1,ARMS)[0];}
    /** Where its right fist's knuckles are {@code t} seconds in (blocks ahead, to its right and up from its feet, at its size 1). */
    static float[] fist(Punch p,float t){
        float[] w=new float[NAMES.size()];stances(t,p.stopped,w);
        float size=arms(p,t);float[] out=new float[3];
        for(int s:new int[]{GUARD,OVER_WIND,OVER_PUNCH}){
            if(w[s]<=0)continue;
            float[] f=GoldRush.grown(s,1,size);
            for(int i=0;i<3;i++)out[i]+=f[i]*w[s];
        }
        return out;
    }
    /** How big its arms are {@code t} seconds in. */
    static float arms(Punch p,float t){return 1+(ARMS-1)*smooth(t/StandRush.RISE)*(1-back(t,p.stopped));}
    /**
     * Punch {@code p} this frame on a look that already holds the stand's pose (beside its user, or its rush's): the
     * punch's stances take over from it (and give way again as it goes back), its arms grow, and {@code shape} says
     * where it stands. Returns whether the punch shows.
     */
    static boolean pose(Punch p,float delta,GoldExperienceLook look,GoldRush.Shape shape,float[] scratch){
        float t=p.age(delta);if(!shows(p,t))return false;
        java.util.Arrays.fill(scratch,0);
        stances(t,p.stopped,scratch);
        float in=smooth(t/TAKE)*(1-smooth((t-p.end()-LINGER)/RETURN));
        for(int i=0;i<scratch.length;i++)look.weights[i]=look.weights[i]*(1-in)+scratch[i]*in;
        float size=arms(p,t);
        look.armL=look.armL*(1-in)+size*in;look.armR=look.armR*(1-in)+size*in;
        shape.engage=engage(p,t);shape.yaw=p.yaw;shape.ahead=place(p);shape.raise=RAISE;shape.side=0;shape.inward=0;
        return true;
    }

    // ───────────────────────────── what others ask ─────────────────────────────

    /** This player's punch (null if none was heard). */
    public static Punch of(UUID id){return PUNCHES.get(id);}
    /** Everyone's punches that still show, with theirs. */
    static Map<UUID,Punch> all(){return PUNCHES;}
    /** The world tick this player's overdrive is ready again (0 if none was thrown). */
    public static long ready(UUID id){var p=PUNCHES.get(id);return p==null?0:p.ready;}
    /** This player's stand is not Gold Experience: its cooldown is gone, as the server forgets it. */
    public static void forget(UUID id){var p=PUNCHES.get(id);if(p!=null)p.ready=0;}
    /** For the checks: this player's punch as "age,landed,struck,guarded,hit,stopped,sounds", or null. */
    public static String describe(UUID id,float delta){
        var p=PUNCHES.get(id);if(p==null)return null;
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%d,%d,%d,%d,%d",p.age(delta),p.landed()?1:0,p.struck,p.guarded?1:0,p.hit?1:0,Float.isNaN(p.stopped)?0:1,p.sound.heard());
    }

    /** R for Gold Experience's user on the map: the overdrive punch, if the stand is out. Returns whether it took the key. */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(key!=GLFW.GLFW_KEY_R||window!=mc.getWindow().getHandle())return false;
        if(action==GLFW.GLFW_RELEASE){if(!heldR)return false;heldR=false;return true;}
        if(heldR)return true;
        if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.goldExperience(mc.player))return false;
        heldR=true;
        if(GoldPresence.out(mc.player)&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(GoldExperienceOverdrive.INPUT)){
            BotandCamera.prepareInteraction();
            // the way it goes is the way the player faces now: the turn goes to the server before the press
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
            ClientPlayNetworking.send(GoldExperienceOverdrive.INPUT,PacketByteBufs.empty());
        }
        return true;
    }
}
