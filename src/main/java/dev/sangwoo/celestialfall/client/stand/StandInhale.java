package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumInhale;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;

/**
 * Star Platinum's inhale as this client sees it (2.61.0, user request: "이번에는 Y스킬 만들자 스타플래티나가 최대
 * 3블록까지 앞에 있는 플레이어를 입으로 빨아들여서 5초동안 끌고오게 하는 스킬 가드 가능하고 데미지 없음
 * 빨아들이는시간동안은 스타핑거 처럼 무력화 효과음 자체제작"). Y by a user whose stand is out asks the server for it
 * (StarPlatinumInhale, stand_inhale_v1); the server says when each user's inhale starts, whom it caught and whose guard
 * took it, and when it ends. Each inhale runs on its own clock from when the client heard of it, as the rush does: the
 * server's age when it was sent, plus this client's ticks since, plus the frame's part of a tick.
 * <p>
 * The stand comes round to the front of its user, rears back drawing its breath in (chest out, head back, arms flung
 * wide: the generator's {@code inhale}) and, as it sucks (StarPlatinumRules.INHALE_CATCH), bends forward into the
 * suction (braced, arms reaching out low: {@code suction}), heaving through the hold, facing where its user faces.
 * It has no mouth (the user's word, 2.50.0): the air goes in under its scarf, at the mouth point the generator writes
 * with the poses. Whom it caught are drawn in to just in front of it and held there (StarPlatinumRules.inhaleAnchor):
 * the local player held is moved there by their own client at the start of each tick ({@link #drive}, as T held's dash
 * is), the same way the server moves anything else; their keys move them nowhere. Its user faces where the camera
 * looks and walks at half their speed. Once it ends the stand turns back to its guard and goes back to its place.
 * <p>
 * Its sounds are made for it (tools/stand/make_inhale_sounds.py, from noise and tones): the breath drawn in and the
 * suction's roar from its start, the catch's thump as it catches someone, and the air let go as it ends.
 */
public final class StandInhale {
    /** Ticks into it its sound starts (a sound held back starts on a client tick, the grid the clock counts on). */
    public static final int LEAD=1;
    /**
     * Seconds: it comes round to the front; once it is round (its guard all the way: a pose flung wide weighed in with
     * the one it had at rest beside its user turned an arm over in a frame) it draws its breath in, from DRAW_FROM to
     * DRAW; it bends into the suction from BEND_FROM to BEND_TO (the catch, INHALE_CATCH, between); once it ends it
     * turns back to the guard over SETTLE, stays a moment and goes back to its place.
     */
    static final float RISE=.18f,DRAW_FROM=.15f,DRAW=.31f,BEND_FROM=.32f,BEND_TO=.52f,SETTLE=.22f,LINGER=.2f,RETURN=.4f;
    /** Through the hold it heaves: this often a second, this far back toward the breath drawn in. */
    static final float PULSE=1.25f,HEAVE=.14f;
    /** Where it stands in front of its user (blocks along the way they face; whom it holds are a little further on) and how much higher than at rest it floats. */
    static final float AHEAD=.85f,RAISE=.05f;
    /** How quickly (seconds to close two thirds of the way) it turns to where its user faces. */
    static final double AIM=.08;
    /** The sounds' loudness at their source and how far they carry, and ticks the suction takes to fade out once it ends. */
    static final float VOLUME=1f,CARRY=40;
    static final int FADE=4;
    /** The farthest the local player held is moved in a tick (blocks): a wall in the way does not store up a fling. */
    static final double STEP=1;
    /**
     * This client draws another player CATCH_UP ticks behind where they are (it eases each move in over three ticks):
     * the place it holds the local player at is led on by its user's way over those ticks, no more than LEAD_MAX blocks
     * (the multiplayer check of 2.61.0: without it the server saw them up to 0.7 blocks nearer their user as it walked).
     */
    static final double CATCH_UP=3,LEAD_MAX=.6;
    /** One it caught: their entity id, their place in front of the stand, and where they stood as it caught them. */
    public record Caught(int id,int slot,Vec3d from){}
    /** An inhale as heard. */
    public static final class Inhale {
        public final long start;final long serverAge,received;
        /** Its clock (seconds) when it ended, NaN while it is on. */
        public float ended=Float.NaN;
        /** The world tick it is ready again once it ended. */
        long ready;
        /** Its clock (seconds) when its catch was heard (NaN until then), whom it caught and whose guard took it (entity ids). */
        public float caughtAt=Float.NaN;public Caught[] caught=new Caught[0];public int[] blocked=new int[0];
        /** Where it faces (degrees), now and a tick ago. */
        float aim,prevAim;boolean aimed;
        /** The suction's sound once asked for (null if not: too far, or heard too late). */
        Sound sound;
        /** The local player held by it: where they stood as the catch was heard, and how many ticks they have been drawn in since. */
        Vec3d localFrom;int steps;boolean let;
        Inhale(long start,long serverAge,long received){this.start=start;this.serverAge=serverAge;this.received=received;}
        /** Whole ticks on its clock (below 0 before it starts: it waits for a punch it stopped short to be back in its guard). */
        public long ticksIn(){return serverAge+ticks-received;}
        /** Seconds on its clock this frame. */
        public float age(float delta){return (ticksIn()+Math.max(0,Math.min(1,delta)))/20f;}
        public boolean on(){return Float.isNaN(ended);}
        /** Whether it has caught anyone (once its catch is heard). */
        public boolean caughtAny(){return caught.length>0;}
        /** When it ends (seconds on its clock): when it ended, or at the latest when it will (its hold run out, or soon after a catch of no one). */
        public float ends(){return on()?(Float.isNaN(caughtAt)||caughtAny()?INHALE_LENGTH/20f:(INHALE_CATCH+INHALE_WHIFF)/20f):ended;}
        /** The way it faces this frame (degrees). */
        public float yaw(float delta){return MathHelper.lerpAngleDegrees(MathHelper.clamp(delta,0,1),prevAim,aim);}
        /** The suction's sound once asked for (null if not). */
        public SoundInstance sound(){return sound;}
        /** The one it caught with this entity id, or null. */
        public Caught caught(int id){for(var c:caught)if(c.id==id)return c;return null;}
    }
    /** The suction's sound: played like any other sound until it is told to fade, then quieter tick by tick to nothing. */
    static final class Sound extends AbstractSoundInstance implements TickableSoundInstance {
        private final float full;private int fading=-1;private boolean done,begun;
        Sound(Identifier id,float volume){
            super(id,SoundCategory.PLAYERS,Random.create());
            this.volume=full=volume;pitch=1;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        void fade(){if(fading<0)fading=0;}
        float loudness(){return volume;}
        /** One told to fade before it began (the inhale over before its sound) never plays; one playing fades out (the game stops a playing sound at once on a tick it cannot play). */
        @Override public boolean canPlay(){return !done&&(fading<0||begun);}
        @Override public boolean isDone(){return done;}
        @Override public void tick(){
            begun=true;
            if(fading<0)return;
            fading++;volume=full*Math.max(0,1-fading/(float)FADE);
            if(fading>=FADE)done=true;
        }
    }
    private static final Map<UUID,Inhale> INHALES=new HashMap<>();
    /** This client's ticks while the game runs. */
    static long ticks;
    private static Object world;
    private static boolean held;
    private StandInhale(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(StarPlatinumInhale.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dimension=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),serverNow=b.readLong();float yaw=b.readFloat();long ready=b.readLong();
            int n=b.readVarInt();if(n<0||n>64)return;
            Caught[] caught=new Caught[n];
            for(int i=0;i<n;i++){int id=b.readInt(),slot=b.readUnsignedByte();caught[i]=new Caught(id,slot,new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()));}
            int m=b.readVarInt();if(m<0||m>256)return;
            int[] blocked=new int[m];for(int i=0;i<m;i++)blocked[i]=b.readInt();
            if(!Float.isFinite(yaw))return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){clear();world=mc.world;}
                event(mc,user,type,start,serverNow,yaw,ready,caught,blocked);
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(StandInhale::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(StandInhale::clear));
    }
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var i:INHALES.values())if(i.sound!=null&&!i.sound.isDone())mc.getSoundManager().stop(i.sound);
        INHALES.clear();world=null;held=false;
    }
    private static void event(MinecraftClient mc,UUID id,int type,long start,long serverNow,float yaw,long ready,Caught[] caught,int[] blocked){
        var i=INHALES.get(id);
        switch(type){
            case StarPlatinumInhale.START->{
                if(i!=null&&i.sound!=null&&!i.sound.isDone())mc.getSoundManager().stop(i.sound);
                // its start may be a few ticks on (a punch it stopped short turns back to the guard first)
                i=new Inhale(start,serverNow-start,ticks);
                i.aim=i.prevAim=yaw;
                INHALES.put(id,i);
                sound(mc,id,i);
            }
            case StarPlatinumInhale.CATCH->{
                if(i==null||i.start!=start||!Float.isNaN(i.caughtAt))return;
                // on this client's clock as the word comes (so nothing jumps), never before the catch itself
                i.caughtAt=Math.max(INHALE_CATCH/20f,i.age(0));i.caught=caught;i.blocked=blocked;
                if(mc.player!=null&&i.caught(mc.player.getId())!=null){i.localFrom=mc.player.getPos();i.steps=0;}
                if(caught.length>0)play(mc,id,"star_platinum_inhale_catch");
            }
            case StarPlatinumInhale.END->{
                if(i==null||i.start!=start||!i.on())return;
                // (never past when it would have ended: a word heard a tick late must not draw it back into the suction)
                i.ended=Math.min(Math.max(0,i.age(0)),i.ends());i.ready=ready;
                // the suction stops (fading out over a few ticks) and the air it held is let go
                if(i.sound!=null){i.sound.fade();play(mc,id,"star_platinum_inhale_release");}
            }
            default->{}
        }
        if(i!=null)i.ready=Math.max(i.ready,ready);
    }
    /** How loud this user's inhale sounds here (0 if too far), as their other sounds. */
    private static float volume(MinecraftClient mc,UUID id){
        if(mc.player==null||mc.world==null)return 0;
        var user=mc.world.getPlayerByUuid(id);
        double far=user==null||user==mc.player?0:user.getPos().distanceTo(mc.player.getPos());
        return (float)(CelestialFall.CONFIG.effectVolume*VOLUME*Math.max(0,1-far/CARRY));
    }
    /** The suction's sound, LEAD ticks into it: loud for its user and those near, fading out to CARRY blocks; none if it was heard too late. */
    private static void sound(MinecraftClient mc,UUID id,Inhale i){
        if(i.serverAge>LEAD)return;
        float volume=volume(mc,id);if(volume<=.01f)return;
        i.sound=new Sound(CelestialFall.id("star_platinum_inhale"),volume);
        int delay=(int)(LEAD-i.serverAge);
        if(delay>0)mc.getSoundManager().play(i.sound,delay);else mc.getSoundManager().play(i.sound);
    }
    /** One of its short sounds now (the catch, the air let go), as loud as its suction. */
    private static void play(MinecraftClient mc,UUID id,String name){
        float volume=volume(mc,id);if(volume<=.01f)return;
        mc.getSoundManager().play(new Sound(CelestialFall.id(name),volume));
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!INHALES.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        INHALES.entrySet().removeIf(e->{
            var i=e.getValue();
            // where it faces: its user's facing, eased
            var user=mc.world.getPlayerByUuid(e.getKey());
            i.prevAim=i.aim;
            if(user!=null){float want=user.getHeadYaw();if(!i.aimed){i.aim=i.prevAim=want;i.aimed=true;}else i.aim+=MathHelper.wrapDegrees(want-i.aim)*(float)(1-Math.exp(-.05/AIM));}
            // (a word of its end lost: it is over once its hold has run out)
            if(i.on()&&i.age(0)>INHALE_LENGTH/20f+1){i.ended=i.ends();if(i.sound!=null)i.sound.fade();}
            return i.age(0)>i.ends()+LINGER+RETURN+1.5f&&i.ready<=mc.world.getTime();
        });
    }

    // ───────────────────────────── the one it holds ─────────────────────────────

    /**
     * Call at the start of every client tick, before the player moves: the local player, held by an inhale, is given
     * the velocity that takes them to where the server would put them at the end of this tick (as it moves anything
     * else it holds, StarPlatinumInhale.drag): drawn in from where they stood to their place just in front of the stand
     * over INHALE_PULL ticks, then kept there as its user turns and walks (where its user is by now: led on over the
     * ticks this client draws them behind, CATCH_UP). Let go, they stop.
     */
    public static void drive(MinecraftClient mc){
        var p=mc.player;if(p==null||mc.world==null||mc.isPaused())return;
        for(var e:INHALES.entrySet()){
            var i=e.getValue();if(i.localFrom==null||i.let)continue;
            var c=i.caught(p.getId());
            if(c==null||!i.on()||i.steps>=INHALE_HOLD||!p.isAlive()){i.let=true;p.setVelocity(0,Math.min(0,p.getVelocity().y),0);continue;}
            var user=mc.world.getPlayerByUuid(e.getKey());if(user==null)continue;
            double lx=(user.getX()-user.prevX)*CATCH_UP,lz=(user.getZ()-user.prevZ)*CATCH_UP,lead=Math.hypot(lx,lz);
            if(lead>LEAD_MAX){lx*=LEAD_MAX/lead;lz*=LEAD_MAX/lead;}
            double[] a=inhaleAnchor(user.getX()+lx,user.getZ()+lz,user.getHeadYaw(),c.slot());
            double u=inhalePull((i.steps+1)/(double)INHALE_PULL);
            double x=i.localFrom.x+(a[0]-i.localFrom.x)*u,z=i.localFrom.z+(a[1]-i.localFrom.z)*u;
            double vx=x-p.getX(),vz=z-p.getZ(),len=Math.hypot(vx,vz);
            if(len>STEP){vx*=STEP/len;vz*=STEP/len;}
            p.setVelocity(vx,Math.min(0,p.getVelocity().y),vz);p.setSprinting(false);
            i.steps++;
        }
    }
    /** Whether the local player is held by an inhale now: their keys move them nowhere and they do nothing else. */
    public static boolean localHeld(){
        var p=MinecraftClient.getInstance().player;if(p==null)return false;
        for(var i:INHALES.values())if(i.localFrom!=null&&!i.let&&i.on()&&i.caught(p.getId())!=null)return true;
        return false;
    }

    // ───────────────────────────── input ─────────────────────────────

    /** Y for Star Platinum's user on the map: asks for the inhale if the stand is out. Returns whether it took the key. */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(key!=GLFW.GLFW_KEY_Y||window!=mc.getWindow().getHandle())return false;
        if(action==GLFW.GLFW_RELEASE){if(!held)return false;held=false;return true;}
        if(held)return true;
        if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.starPlatinum(mc.player))return false;
        held=true;
        if(StandPresence.out(mc.player)&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(StarPlatinumInhale.INPUT)){
            BotandCamera.prepareInteraction();
            // the way it sucks is the way the player faces now: the turn goes to the server before the press
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
            ClientPlayNetworking.send(StarPlatinumInhale.INPUT,PacketByteBufs.empty());
        }
        return true;
    }

    // ───────────────────────────── what others ask ─────────────────────────────

    /** This player's inhale (null if none shows or sounds). */
    public static Inhale of(UUID id){return INHALES.get(id);}
    /** Everyone whose inhale still shows, with theirs. */
    public static Map<UUID,Inhale> all(){return Map.copyOf(INHALES);}
    /** Whether this player's stand is inhaling now, as far as this client knows. */
    public static boolean inhaling(UUID id){var i=INHALES.get(id);return i!=null&&i.on()&&i.age(0)<=i.ends()+.5f;}
    /** Whether the local player's stand is inhaling: they face where the camera looks, walk slowly and do nothing else. */
    public static boolean localInhaling(){var p=MinecraftClient.getInstance().player;return p!=null&&inhaling(p.getUuid());}
    /** The world tick this player's inhale is ready again (0 while it is on or if it has not run). */
    public static long ready(UUID id){var i=INHALES.get(id);return i==null||i.on()?0:i.ready;}

    // ───────────────────────────── how it moves ─────────────────────────────

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** How far it has bent into the suction {@code t} seconds in (0 to 1). */
    static float bend(float t){return smooth((t-BEND_FROM)/(BEND_TO-BEND_FROM));}
    /** How far back toward the breath drawn in it heaves {@code t} seconds in (0 until it has bent into the suction; starting and turning still). */
    static float heave(float t){return t<=BEND_TO?0:HEAVE*(.5f-.5f*(float)Math.cos(2*Math.PI*PULSE*(t-BEND_TO)));}
    /**
     * Adds its stances {@code t} seconds into it into {@code w} ({@code share} of them): from the guard it draws its
     * breath in, then bends into the suction, heaving through the hold.
     */
    public static void stances(float t,float[] w,float share){
        float draw=smooth((t-DRAW_FROM)/(DRAW-DRAW_FROM)),hold=bend(t)*(1-heave(t));
        w[GUARD]+=share*(1-draw);
        w[INHALE]+=share*(draw-hold);
        w[SUCTION]+=share*hold;
    }
    private static final StandPunch.Shape INHALE_SHAPE=new StandPunch.Shape();
    /**
     * The stand's shape this frame through its user's punches, rush and inhale together, {@code back} seconds before
     * this frame (null if none shows). The newest leads and takes over from the rest (the inhale starts as the punch
     * in flight stops, or once the rush is over; a punch can only come once it is over), as the rush and the punches do.
     */
    public static StandPunch.Shape shape(PlayerEntity user,float delta,float back,StandPunch.Shape into){
        var base=StandRush.shape(user,delta,back,into);
        var i=INHALES.get(user.getUuid());if(i==null)return base;
        return over(base,StandRush.newest(user.getUuid(),delta,back),i,delta,back,into);
    }
    /**
     * Inhale {@code i} together with the punches' and the rush's shape ({@code base}, null if neither shows, the newer
     * of them {@code baseAge} seconds old), into {@code into} (which may be {@code base}): the newer leads.
     */
    static StandPunch.Shape over(StandPunch.Shape base,float baseAge,Inhale i,float delta,float back,StandPunch.Shape into){
        var mine=inhale(i,delta,back,INHALE_SHAPE);if(mine==null)return base;
        return StandRush.combine(base,baseAge,mine,i.age(delta)-back,into);
    }
    /**
     * The stand's shape through inhale {@code i}, {@code back} seconds before this frame (null if it does not show): its
     * stances, how far it has come round to the front, the way it faces, where in front it stands, the wind through it.
     */
    static StandPunch.Shape inhale(Inhale i,float delta,float back,StandPunch.Shape into){
        float t=i.age(delta)-back;if(t<0)return null;
        float end=i.ends();
        // over before it began (its user struck while a punch was still going back to its guard): it never shows
        if(end<=0||t>end+LINGER+RETURN)return null;
        into.clear();
        if(t<=end)stances(t,into.weights,1);
        else{
            // over: it turns from where it was back to the guard (turned, not weighed in)
            into.weights[GUARD]=1;stances(end,into.under,1);into.over=smooth((t-end)/SETTLE);
        }
        into.engage=StandPunch.burst(t/RISE)*(1-smooth((t-end-LINGER)/RETURN));
        into.yaw=i.yaw(delta);
        into.ahead=AHEAD;into.side=0;into.raise=RAISE;into.lunge=0;
        // the air it draws through its hair: a breeze as it breathes in, a gale through the suction
        float on=1-smooth((t-end)/SETTLE);
        into.gust=(.45f+.55f*suction(t,end))*on;
        into.ghost=0;
        return into;
    }
    /** How hard it sucks {@code t} seconds in (0 to 1): the air begins to move as it draws its breath, and rushes in once it bends to the catch, until it ends. */
    public static float suction(float t,float end){
        if(t<0)return 0;
        return (.3f*smooth((t-.06f)/.28f)+.7f*smooth((t-BEND_FROM)/(INHALE_CATCH/20f+.02f-BEND_FROM)))*(1-smooth((t-end)/.14f));
    }

    // ───────────────────────────── where its mouth is ─────────────────────────────

    /** Where its mouth would be in every stance (blocks ahead, to its right and up from its feet, at its size 1): under its scarf, from the file the generator writes with the poses. */
    static final float[][] MOUTH=mouths();
    /** Where its mouth is {@code t} seconds into an inhale that ends at {@code end} (as its stances put it). */
    public static float[] mouth(float t,float end){
        float[] w=new float[NAMES.size()];
        if(t<=end)stances(Math.max(0,t),w,1);
        else{float k=smooth((t-end)/SETTLE);stances(end,w,1-k);w[GUARD]+=k;}
        float[] out=new float[3];
        for(int s=0;s<w.length;s++)if(w[s]!=0)for(int j=0;j<3;j++)out[j]+=w[s]*MOUTH[s][j];
        return out;
    }
    private static float[][] mouths(){
        float[][] out=new float[NAMES.size()][3];
        try(var in=StandInhale.class.getResourceAsStream("/assets/celestialfall/stands/star_platinum_fists.json")){
            var root=com.google.gson.JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"fists"),StandardCharsets.UTF_8)).getAsJsonObject();
            for(int s=0;s<NAMES.size();s++){
                var stance=root.getAsJsonObject(NAMES.get(s));if(stance==null||!stance.has("mouth"))continue;
                var p=stance.getAsJsonArray("mouth");
                // model units (front -z, its right -x, up +y) to blocks ahead, to its right and up
                out[s][0]=-p.get(2).getAsFloat()*StandPunch.SCALE;out[s][1]=-p.get(0).getAsFloat()*StandPunch.SCALE;out[s][2]=p.get(1).getAsFloat()*StandPunch.SCALE;
            }
        }catch(Exception e){
            CelestialFall.LOGGER.error("Star Platinum's mouth could not be read",e);
        }
        return out;
    }
    /** For the capture tools: this player's inhale (its age in seconds, whether it is on, how many it caught and whose guard took it, how hard it sucks), or null. */
    public static String describe(PlayerEntity p,float delta){
        var i=INHALES.get(p.getUuid());if(i==null)return null;float t=i.age(delta);
        return String.format(java.util.Locale.ROOT,"%.3f,%d,%d,%d,%.2f",t,i.on()?1:0,i.caught.length,i.blocked.length,suction(t,i.ends()));
    }
}
