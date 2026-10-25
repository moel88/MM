package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumRush;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;

/**
 * Star Platinum's rush as this client sees it (2.57.0, user request: "이번에는 e스킬 만들자 e홀드로 사용 할 수 있고 도중에
 * 끊으면 바로 끊겨 러시고 연타를 빠르게 꽂는거야"). E held by a user whose stand is out asks the server for the rush and
 * letting it go stops it (StarPlatinumRush, stand_rush_v1); the server says when each user's rush starts, each blow and
 * whom it struck, and when it ends. Each rush runs on its own clock from when the client heard of it, as the punches
 * do (StandAttack): the server's age when it was sent, plus this client's ticks since, plus the frame's part of a tick.
 * <p>
 * The stand comes round to the front of its user (as for the punches) and drives its fists out left and right in turn,
 * each at the middle, high or low (the generator's rush stances), a fist every HALF seconds, each snapping out and
 * stopping dead at full stretch while the other is torn back; afterimages of its arms alone, see-through and violet,
 * fly with it a little out of step and to every side, so the front is full of fists. It faces where its user faces
 * (eased a little, so a quick turn swings it round rather than jumping) and its user walks at a tenth of their speed
 * (the server's modifier) facing where the camera looks. Let go, its fists go back to the guard at once and it goes
 * back to its place soon after.
 * <p>
 * Since 2.58.0 (user request: "오라 러시가 더 격했으면 좋겠어 팔도 더 많아지고 오라 러시 한정으로 팔들이 조금 더 커져도
 * 좋을듯", "러시할때 팔을 더 멀리 뻗으면 동작이 커보여서 좋을 거 같다") it is fiercer: ten afterimages instead of four, its
 * arms (and theirs) a fifth bigger through the rush, each arm bigger still as it stretches out to its fist so that the
 * fist goes out further every time and comes back to the size of the other, and the generator's stances wrench the
 * body round further behind every fist and draw the other further back.
 * <p>
 * Its sound is the user's two recordings (tools/stand/cut_rush.py): the voice from the rush's start, and the blows on
 * it as loud as the rush strikes someone now, so that striking someone it is the recording with blows and striking no
 * one the voice alone; let go, both fade out at once.
 */
public final class StandRush {
    /** Ticks into the rush its sound starts (a sound held back starts on a client tick, the grid the clock counts on). */
    public static final int LEAD=1;
    /** A fist every HALF seconds (left and right in turn, twelve and a half a second), each easing out to a dead stop at full stretch. */
    public static final float HALF=.08f;
    /** Seconds: it comes round to the front; once the rush ends its fists go back to the guard, it stays a moment and goes back. */
    static final float RISE=.18f,SETTLE=.12f,LINGER=.15f,RETURN=.4f;
    /**
     * Where it stands in front of its user (blocks along the way they face) with nobody in its way. With someone in its
     * way it stands where its fists, at full stretch, sink BITE into their near side, but never nearer its user than NEAR
     * nor further than FAR; it closes in on that place over PLACE seconds. It holds that place through the barrage
     * (2.58.0, user request: in 2.57.0 every fist drove it on 0.05 blocks and back).
     */
    static final float AHEAD=1.05f,LUNGE=.18f,BITE=.15f,NEAR=.95f,FAR=2.8f;
    /** How much higher than at rest it floats in front (blocks): less than for the punches, so its fists land on a body, not over its head. */
    static final float RAISE=.1f;
    /**
     * How big its arms are through the rush (2.58.0, only the rush: the punches' are their size), and how much bigger
     * still an arm grows as it stretches out to its fist, from its shoulder: at full stretch its fist is out ARMS times
     * (1+STRETCH) as far from the shoulder as at its size.
     */
    static final float ARMS=1.2f,STRETCH=.12f;
    static final double PLACE=.08;
    /** How quickly (seconds to close two thirds of the way) it turns to where its user faces. */
    static final double AIM=.06;
    /** The stances it throws a fist in, by side and by height (the middle twice as often as high or low). */
    static final int[] LEFT={RUSH_L,RUSH_L_HI,RUSH_L_LO},RIGHT={RUSH_R,RUSH_R_HI,RUSH_R_LO};
    /**
     * The afterimages of its flying arms: each a little out of step with the body (seconds behind), its fists thrown
     * in their own order, a little to its right and up (blocks) and turned (degrees), and how strongly it shows. Ten
     * since 2.58.0 (four in 2.57.0), round it on every side, none above its shoulders (they hid its head), spread over
     * a fist's time; each shows the arm striking out and fades the one drawn back (phantomShow).
     */
    static final float[][] PHANTOMS={
        {.031f,.36f,.06f,9,.5f},{.057f,-.38f,.02f,-10,.46f},{.019f,-.22f,.12f,-5,.42f},{.068f,.24f,-.16f,6,.38f},
        {.044f,.6f,.1f,15,.36f},{.082f,-.6f,.08f,-15,.34f},{.012f,.12f,-.3f,2,.32f},{.075f,-.14f,-.34f,-3,.3f},
        {.05f,.52f,-.22f,13,.3f},{.025f,-.5f,-.18f,-13,.3f}};
    /** The sounds' loudness at their source (the recordings' own level, the blows' peak at -1 dB) and how far they carry. */
    static final float VOLUME=1f,CARRY=40;
    /** Ticks the sound takes to fade out once the rush stops short, and the blows to come or go as the rush strikes or stops striking. */
    static final int FADE=3,SWAP=3;
    /** A blow that struck someone keeps the blows sounding this many ticks (past the next blow: a miss between hits does not drop them). */
    static final int STRUCK=4;
    /** A blow heard: when it landed on the rush's clock (seconds), whom it struck (entity ids, where they stood, on a guard or not) and the way its user faced. */
    public record Blow(float at,int[] ids,Vec3d[] where,boolean[] guarded,float yaw){}
    /** A rush as heard. */
    public static final class Rush {
        public final long start;final long serverAge,received;final int seed;
        /** Its clock (seconds) when it ended, NaN while it is on. */
        public float ended=Float.NaN;
        /** The world tick it is ready again once it ended. */
        long ready;
        /** This client's tick a blow last struck someone, how many blows have been heard, the last few, and whom they have struck (entity ids). */
        long struckAt=-100;public int blows;final ArrayDeque<Blow> recent=new ArrayDeque<>();final java.util.Set<Integer> struck=new java.util.HashSet<>();
        /** Where it faces (degrees), now and a tick ago. */
        float aim,prevAim;boolean aimed;
        /** How far in front of its user it stands (blocks), now and a tick ago. */
        float place=AHEAD+LUNGE,prevPlace=AHEAD+LUNGE;
        /** How loud the blows sound now (0 to 1), and the sounds. */
        float strike;Sound voice,hits;
        Rush(long start,long serverAge,long received,int seed){this.start=start;this.serverAge=serverAge;this.received=received;this.seed=seed;}
        /** Whole ticks on its clock (below 0 before it starts: it waits for a punch it stopped short to be back in its guard). */
        public long ticksIn(){return serverAge+ticks-received;}
        /** Seconds on its clock this frame. */
        public float age(float delta){return (ticksIn()+Math.max(0,Math.min(1,delta)))/20f;}
        public boolean on(){return Float.isNaN(ended);}
        /** When it ends (seconds on its clock): when it ended, or when it will if it runs out. */
        float end(){return on()?StarPlatinumRules.RUSH_LENGTH/20f:ended;}
        /** The blows heard, oldest first. */
        public List<Blow> blowsHeard(){return List.copyOf(recent);}
        /** What picks its fists' heights (the same on every client). */
        public int seed(){return seed;}
        /** The way it faces this frame (degrees). */
        public float yaw(float delta){return MathHelper.lerpAngleDegrees(MathHelper.clamp(delta,0,1),prevAim,aim);}
        /** Its clock (seconds) when it ends or will (when it runs out). */
        public float ends(){return end();}
        /** Its two sounds once asked for (null if not: too far, or heard too late), and how loud its blows sound now (0 to 1). */
        public SoundInstance voice(){return voice;}
        public SoundInstance blowsSound(){return hits;}
        public float striking(){return strike;}
    }
    /**
     * The rush's sound: the voice, or the blows on it. Played like any other sound; once a tick it sets its loudness from
     * the rush (the blows as loud as the rush strikes someone now) and, once the rush has stopped short, fades out.
     */
    static final class Sound extends AbstractSoundInstance implements TickableSoundInstance {
        private final Rush rush;private final boolean blows;private final float full;private int fading=-1;private boolean done,begun;
        Sound(Identifier id,Rush rush,boolean blows,float volume){
            super(id,SoundCategory.PLAYERS,Random.create());
            this.rush=rush;this.blows=blows;full=volume;this.volume=blows?0:volume;pitch=1;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        void fade(){if(fading<0)fading=0;}
        float loudness(){return volume;}
        /**
         * One told to fade before it began (the rush let go before it started) never plays. One playing fades out: the
         * game stops a playing sound at once on the tick it says it cannot play (2.59.0: before, a rush let go was cut
         * off there rather than faded).
         */
        @Override public boolean canPlay(){return !done&&(fading<0||begun);}
        /**
         * The blows start silent when nobody is in the rush's way and come up once it strikes someone: the game skips
         * a sound that starts at no loudness unless it says it must play.
         */
        @Override public boolean shouldAlwaysPlay(){return blows;}
        @Override public boolean isDone(){return done;}
        @Override public void tick(){
            begun=true;
            float level=blows?rush.strike:1;
            if(fading>=0){fading++;level*=Math.max(0,1-fading/(float)FADE);if(fading>=FADE)done=true;}
            volume=full*level;
        }
    }
    private static final Map<UUID,Rush> RUSHES=new HashMap<>();
    /** This client's ticks while the game runs. */
    static long ticks;
    private static Object world;
    /** Whether E is held (this client asked for the rush and has not let it go yet). */
    private static boolean held;
    private StandRush(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(StarPlatinumRush.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dimension=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),serverNow=b.readLong();float yaw=b.readFloat();long ready=b.readLong();
            int n=b.readVarInt();if(n<0||n>256)return;
            int[] ids=new int[n];Vec3d[] at=new Vec3d[n];boolean[] guarded=new boolean[n];
            for(int i=0;i<n;i++){ids[i]=b.readInt();at[i]=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());guarded[i]=b.readBoolean();}
            if(!Float.isFinite(yaw))return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){clear();world=mc.world;}
                event(mc,user,type,start,serverNow,yaw,ready,ids,at,guarded);
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(StandRush::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(StandRush::clear));
    }
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var r:RUSHES.values())stopSounds(mc,r);
        RUSHES.clear();world=null;held=false;
    }
    private static void stopSounds(MinecraftClient mc,Rush r){
        if(r.voice!=null&&!r.voice.isDone())mc.getSoundManager().stop(r.voice);
        if(r.hits!=null&&!r.hits.isDone())mc.getSoundManager().stop(r.hits);
    }
    private static void event(MinecraftClient mc,UUID id,int type,long start,long serverNow,float yaw,long ready,int[] ids,Vec3d[] at,boolean[] guarded){
        var r=RUSHES.get(id);
        switch(type){
            case StarPlatinumRush.START->{
                if(r!=null)stopSounds(mc,r);
                // its start may be a few ticks on (a punch it stopped short turns back to the guard first)
                r=new Rush(start,serverNow-start,ticks,(int)(start*31+id.getLeastSignificantBits()));
                r.aim=r.prevAim=yaw;
                RUSHES.put(id,r);
                sound(mc,id,r);
            }
            case StarPlatinumRush.BLOW->{
                if(r==null||r.start!=start)return;
                r.blows++;
                var blow=new Blow((serverNow-start)/20f,ids,at,guarded,yaw);
                r.recent.addLast(blow);while(r.recent.size()>12)r.recent.removeFirst();
                if(ids.length>0)r.struckAt=ticks;
                for(int i=0;i<ids.length;i++)if(!guarded[i])r.struck.add(ids[i]);
                // its user's own feedback: the running total on whom it struck, and a streak of blood every eighth blow
                if(mc.player!=null&&id.equals(mc.player.getUuid()))
                    for(int i=0;i<ids.length;i++)if(!guarded[i])dev.sangwoo.celestialfall.client.kageno.HitFeedback.hit(mc,ids[i],StarPlatinumRules.RUSH_DAMAGE,yaw,r.blows%8==1);
            }
            case StarPlatinumRush.END->{
                if(r==null||r.start!=start||!r.on())return;
                // on this client's clock as the word comes (a stop seen as it is heard, so nothing jumps), never past its own end
                r.ended=Math.min(r.age(0),StarPlatinumRules.RUSH_LENGTH/20f);r.ready=ready;
                // stopped short: its fists and its sound stop at once (run out, the voice's last "ORA" rings out by itself)
                if(serverNow-start<StarPlatinumRules.RUSH_LENGTH){if(r.voice!=null)r.voice.fade();if(r.hits!=null)r.hits.fade();}
            }
            default->{}
        }
        if(r!=null)r.ready=Math.max(r.ready,ready);
    }
    /** The rush's sound, LEAD ticks into it: loud for its user and those near, fading out to CARRY blocks; none if it was heard too late. */
    private static void sound(MinecraftClient mc,UUID id,Rush r){
        if(mc.player==null||r.serverAge>LEAD)return;
        var user=mc.world.getPlayerByUuid(id);
        double far=user==null||user==mc.player?0:user.getPos().distanceTo(mc.player.getPos());
        float volume=(float)(CelestialFall.CONFIG.effectVolume*VOLUME*Math.max(0,1-far/CARRY));if(volume<=.01f)return;
        r.voice=new Sound(CelestialFall.id("star_platinum_rush"),r,false,volume);
        r.hits=new Sound(CelestialFall.id("star_platinum_rush_hits"),r,true,volume);
        int delay=(int)(LEAD-r.serverAge);
        if(delay>0){mc.getSoundManager().play(r.voice,delay);mc.getSoundManager().play(r.hits,delay);}
        else{mc.getSoundManager().play(r.voice);mc.getSoundManager().play(r.hits);}
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!RUSHES.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        // (E let go while the window is not in focus comes as a release too: GLFW lets go of every key as focus goes)
        if(mc.isPaused())return;
        ticks++;
        RUSHES.entrySet().removeIf(e->{
            var r=e.getValue();
            // where it faces: its user's facing, eased
            var user=mc.world.getPlayerByUuid(e.getKey());
            r.prevAim=r.aim;r.prevPlace=r.place;
            if(user!=null){float want=user.getHeadYaw();if(!r.aimed){r.aim=r.prevAim=want;r.aimed=true;}else r.aim+=MathHelper.wrapDegrees(want-r.aim)*(float)(1-Math.exp(-.05/AIM));}
            // where it stands: its fists on whoever is nearest in its way
            float face=user==null?Float.NaN:nearSide(mc,user);
            float want=Float.isNaN(face)?AHEAD+LUNGE:MathHelper.clamp(face+BITE-fistReach(),NEAR,FAR);
            r.place+=(want-r.place)*(float)(1-Math.exp(-.05/PLACE));
            // the blows sound while it strikes someone (or, before the first blow is heard, while someone stands in its way)
            boolean striking=r.on()&&(ticks-r.struckAt<=STRUCK||r.blows==0&&!Float.isNaN(face));
            r.strike=MathHelper.clamp(r.strike+(striking?1f:-1f)/SWAP,0,1);
            return r.age(0)>r.end()+LINGER+RETURN+1.5f&&r.ready<=mc.world.getTime();
        });
    }

    // ───────────────────────────── input ─────────────────────────────

    /**
     * E for Star Platinum's user on the map (whatever the hand holds; its inventory is on I then, as Kageno's): down
     * asks for the rush if the stand is out, up stops it. Returns whether it took the key.
     */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle())return false;
        if(key==GLFW.GLFW_KEY_E){
            if(action==GLFW.GLFW_RELEASE){if(!held)return false;held=false;send(false);return true;}
            if(held)return true;
            if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.starPlatinum(mc.player))return false;
            held=true;
            if(CombatClient.combatInput()&&StandPresence.out(mc.player)){
                BotandCamera.prepareInteraction();
                // the way it goes is the way the player faces now: the turn goes to the server before the press
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
                send(true);
            }
            return true;
        }
        if(key==GLFW.GLFW_KEY_I&&action==GLFW.GLFW_PRESS&&mc.currentScreen==null&&MapClient.enabled()&&StandManager.starPlatinum(mc.player)){
            if(!CombatClient.actionLocked())mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player));
            return true;
        }
        return false;
    }
    private static void send(boolean down){
        if(!ClientPlayNetworking.canSend(StarPlatinumRush.INPUT))return;
        var b=PacketByteBufs.create();b.writeByte(down?1:0);ClientPlayNetworking.send(StarPlatinumRush.INPUT,b);
    }

    // ───────────────────────────── what others ask ─────────────────────────────

    /** This player's rush (null if none shows or sounds). */
    public static Rush of(UUID id){return RUSHES.get(id);}
    /** Everyone whose rush still shows, with theirs. */
    public static Map<UUID,Rush> all(){return Map.copyOf(RUSHES);}
    /** Whether this player's stand is rushing now, as far as this client knows. */
    public static boolean rushing(UUID id){var r=RUSHES.get(id);return r!=null&&r.on()&&r.age(0)<=r.end()+.5f;}
    /** Whether the local player's stand is rushing: they face where the camera looks, walk slowly and do nothing else. */
    public static boolean localRushing(){var p=MinecraftClient.getInstance().player;return p!=null&&rushing(p.getUuid());}
    /** The world tick this player's rush is ready again (0 if it has not run). */
    public static long ready(UUID id){var r=RUSHES.get(id);return r==null||r.on()?0:r.ready;}
    /**
     * Whether this body's cry of pain is held back now: the local player in a rush's blows (the first blow cries out, the
     * rest come with the rush's own sound; the server holds back the cry others would hear). It is held back for as long
     * as the rush that struck them is on, however long ago its last blow was heard: a client that draws a few frames a
     * second takes in several ticks' packets at once, so a blow's damage can come more than STRUCK ticks after the word
     * of the blow before it (the multiplayer check of 2.57.0 heard two more cries in a rush that way).
     */
    public static boolean quietHurt(LivingEntity e){
        var mc=MinecraftClient.getInstance();if(e!=mc.player||mc.player==null)return false;
        // the server's word of a blow comes after the damage it did: the first blow on them is not yet in the set
        for(var r:RUSHES.values())if(r.on()&&r.struck.contains(e.getId()))return true;
        return false;
    }
    /** Who stands in this user's rush's way now (as the server picks them, StarPlatinumRules.rushLane, in sight). */
    static List<LivingEntity> inWay(MinecraftClient mc,PlayerEntity user){
        Vec3d at=user.getPos(),f=Vec3d.fromPolar(0,user.getHeadYaw()),r=new Vec3d(-f.z,0,f.x);
        return mc.world.getEntitiesByClass(LivingEntity.class,new net.minecraft.util.math.Box(at,at).expand(StarPlatinumRules.RUSH_REACH+2),e->{
            if(e==user||!e.isAlive()||e.isSpectator()||!e.canHit()||e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)return false;
            Vec3d d=e.getPos().subtract(at);
            return StarPlatinumRules.rushLane(d.dotProduct(f),d.dotProduct(r),d.y,e.getWidth()*.5)&&user.canSee(e);
        });
    }
    /** How far ahead of this user (blocks, along the way they face) the near side of whoever is nearest in their rush's way is, NaN if nobody is. */
    static float nearSide(MinecraftClient mc,PlayerEntity user){
        Vec3d at=user.getPos(),f=Vec3d.fromPolar(0,user.getHeadYaw());float best=Float.NaN;
        for(var e:inWay(mc,user)){float d=(float)(e.getPos().subtract(at).dotProduct(f)-e.getWidth()*.5);if(Float.isNaN(best)||d<best)best=d;}
        return best;
    }
    /** How far ahead of the stand's feet its fists reach at full stretch through the rush (blocks, at its size 1): its arms at their biggest. */
    static float fistReach(){return StandPunch.grown(RUSH_L,0,ARMS*(1+STRETCH))[0];}

    // ───────────────────────────── how it moves ─────────────────────────────

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** The stance of the rush's {@code k}-th fist (from 0): left and right in turn, each at the middle, high or low as its seed picks. */
    static int fist(int k,int seed){
        int[] side=(k&1)==0?LEFT:RIGHT;double h=Ease.hash(seed*7919+k*104729);
        return side[h<.5?0:h<.75?1:2];
    }
    /**
     * Adds the barrage's stances {@code t} seconds into it into {@code w} ({@code share} of them): from the guard the
     * first fist goes out, then each next fist goes out as the one before comes back, eased to a dead stop at each end.
     */
    public static void barrage(float t,int seed,float[] w,float share){
        if(t<=0){w[GUARD]+=share;return;}
        float u=t/HALF;int k=(int)Math.floor(u);float s=smooth(u-k);
        int from=k==0?GUARD:fist(k-1,seed),to=fist(k,seed);
        w[from]+=share*(1-s);w[to]+=share*s;
    }
    /**
     * How big its arms are {@code t} seconds into rush {@code r} before either stretches out (1 their size): growing as
     * it comes round to the front, back to their size as its fists go back to the guard.
     */
    static float armSize(Rush r,float t){return 1+(ARMS-1)*smooth(t/RISE)*(1-smooth((t-r.end())/(SETTLE+LINGER)));}
    /** How far out arm {@code side} (the stances that throw its fist) is in these stances (0 to 1). */
    static float out(float[] w,int[] side){float s=0;for(int i:side)s+=w[i];return s;}
    /** How hard the fist that is going out now drives through the air (0 to 1, most as it lands): the gust through its hair. */
    static float drive(float t){if(t<=0)return 0;float u=t/HALF,f=u-(float)Math.floor(u);return (float)Math.sin(Math.PI*f*.5)*(1-smooth((f-.85f)/.15f));}
    /**
     * Seconds the rush takes to take over from a punch that shows as it starts, and a punch after it to take over from
     * the rush: two of its fists (its first fist strikes out as it turns from the punch's pose).
     */
    static final float TAKE=.16f;
    private static final StandPunch.Shape RUSH=new StandPunch.Shape();
    /**
     * The stand's shape this frame through its user's punches and rush together, {@code back} seconds before this frame
     * (null if neither shows). The newer of the two leads (the rush starts as the punch in flight stops, and a punch
     * can only come once the rush is over) and takes over from the other over TAKE seconds, turning from the pose the
     * other has then; the stand stays out in front as long as either wants it there.
     */
    public static StandPunch.Shape shape(PlayerEntity user,float delta,float back,StandPunch.Shape into){
        var punch=StandAttack.shape(user.getUuid(),delta,back,into);
        var r=RUSHES.get(user.getUuid());if(r==null)return punch;
        var newest=StandAttack.newest(user.getUuid());
        return combine(punch,newest==null?Float.MAX_VALUE:newest.age(delta)-back,rush(r,delta,back,RUSH),r.age(delta)-back,into);
    }
    /**
     * How old (seconds, {@code back} seconds before this frame) the newer of this user's newest punch and rush is
     * (Float.MAX_VALUE if they have neither): what the inhale (2.61.0, StandInhale) takes over from, or what takes over
     * from it once it is over.
     */
    static float newest(UUID id,float delta,float back){
        var punch=StandAttack.newest(id);var r=RUSHES.get(id);
        return Math.min(punch==null?Float.MAX_VALUE:punch.age(delta)-back,r==null?Float.MAX_VALUE:r.age(delta)-back);
    }
    /**
     * The punches' shape ({@code punch}, null if none shows, its newest punch {@code pt} seconds old) and the rush's
     * ({@code rush}, null if it does not show, {@code rt} seconds old) together, into {@code into} (which may be the
     * punches' shape; the rush's is changed): the newer leads, taking over from the other.
     */
    static StandPunch.Shape combine(StandPunch.Shape punch,float pt,StandPunch.Shape rush,float rt,StandPunch.Shape into){
        if(rush==null)return punch;
        if(punch==null)return into.set(rush);
        if(rt<=pt){
            // the rush is the newer; let go while it still takes over, its fists going back to the guard turn from it too
            float k=smooth(rt/TAKE);
            if(rush.over<1)rush.underneath(punch,k);else rush.takeOver(punch,k);
            return into.set(rush);
        }
        if(punch!=into)into.set(punch);
        into.takeOver(rush,smooth(pt/TAKE));
        return into;
    }
    /**
     * The stand's shape through rush {@code r}, {@code back} seconds before this frame (null if it does not show): its
     * stances, how far it has come round to the front, the way it faces, how far in front it stands and how big its arms are.
     */
    static StandPunch.Shape rush(Rush r,float delta,float back,StandPunch.Shape into){
        float t=r.age(delta)-back;if(t<0)return null;
        float end=r.end();
        // let go before it began (E let go while a punch was still going back to its guard): it never shows
        if(end<=0||t>end+LINGER+RETURN)return null;
        into.clear();
        if(t<=end)barrage(t,r.seed,into.weights,1);
        else{
            // let go: its fists turn from where they were back to the guard (turned, not weighed in)
            into.weights[GUARD]=1;barrage(end,r.seed,into.under,1);into.over=smooth((t-end)/SETTLE);
        }
        into.engage=StandPunch.burst(t/RISE)*(1-smooth((t-end-LINGER)/RETURN));
        into.yaw=MathHelper.lerpAngleDegrees(MathHelper.clamp(delta,0,1),r.prevAim,r.aim);
        into.ahead=AHEAD;into.side=0;into.raise=RAISE;
        float on=1-smooth((t-end)/SETTLE);
        into.lunge=r.prevPlace+(r.place-r.prevPlace)*MathHelper.clamp(delta,0,1)-AHEAD;
        into.gust=(.95f+.25f*drive(t))*on;
        into.ghost=0;
        // its arms bigger, each bigger still as far as it is out (let go: as far as it still is, turning back)
        float size=armSize(r,t),left=0,right=0;
        for(int i:LEFT)left+=into.share(i);for(int i:RIGHT)right+=into.share(i);
        into.armL=size*(1+STRETCH*left);into.armR=size*(1+STRETCH*right);
        return into;
    }
    /**
     * Afterimage {@code i} of the stand's flying arms this frame: its stances and its arms' sizes into {@code into}, and
     * how strongly it shows (0 when it does not). It throws its own fists a little behind the body's, while the rush is
     * on and the stand in front, its arms as big as the body's and as much bigger as each is out.
     */
    public static float phantom(UUID id,int i,float delta,dev.sangwoo.celestialfall.stand.StarPlatinumLook into){
        var r=RUSHES.get(id);if(i>=PHANTOMS.length)return 0;
        // (the beatdown's rush throws them as well, 2.64.0)
        if(r==null)return StandBeatdown.phantom(id,i,delta,into);
        float t=r.age(delta),show=phantomAlpha(r,i,t);if(show<=0)return StandBeatdown.phantom(id,i,delta,into);
        var w=into.weights;java.util.Arrays.fill(w,0);
        barrage(t-PHANTOMS[i][0],phantomSeed(r,i),w,1);
        float size=armSize(r,t),left=out(w,LEFT),right=out(w,RIGHT);
        into.armL=size*(1+STRETCH*left);into.armR=size*(1+STRETCH*right);
        into.showL=phantomShow(left);into.showR=phantomShow(right);
        return show;
    }
    /** How strongly an afterimage's arm shows as far out as it is (0 drawn back, 1 at full stretch): it strikes out and fades as it is drawn back. */
    static float phantomShow(float out){return smooth(out);}
    /** How strongly afterimage {@code i} shows {@code t} seconds into rush {@code r}: once the stand has come round, until the rush ends. */
    static float phantomAlpha(Rush r,int i,float t){return smooth((t-RISE)/.08f)*(1-smooth((t-r.end())/.08f))*PHANTOMS[i][4];}
    /** Afterimage {@code i}'s place this frame: beside the body where it stands (to its right and up, blocks) and turned a little. */
    public static StandPresence.Body phantomBody(StandPresence.Body body,int i){
        double r=Math.toRadians(body.yaw()),fx=-Math.sin(r),fz=Math.cos(r),right=PHANTOMS[i][1];
        // Minecraft's yaw: 0 faces south (+z); the right hand is then west (-x)
        return new StandPresence.Body(body.at().add(-fz*right,PHANTOMS[i][2],fx*right),body.yaw()+PHANTOMS[i][3],body.scale());
    }
    /** Afterimage {@code i}'s fists: a little behind the body's, thrown in an order of their own (its lag, seconds, and its seed). */
    public static float phantomLag(int i){return PHANTOMS[i][0];}
    public static int phantomSeed(Rush r,int i){return r.seed+101*(i+1);}
    public static int phantoms(){return PHANTOMS.length;}
    /** How strongly afterimage {@code i} shows at its fullest (2.64.0: the beatdown's rush throws the same afterimages). */
    static float phantomStrength(int i){return PHANTOMS[i][4];}
    /** For the capture tools: this player's rush (its age in seconds, blows heard, how many the last struck, whether it is on, how loud its blows sound), or null. */
    public static String describe(PlayerEntity p,float delta){
        var r=RUSHES.get(p.getUuid());if(r==null)return null;var last=r.recent.peekLast();
        return String.format(java.util.Locale.ROOT,"%.3f,%d,%d,%d,%.2f",r.age(delta),r.blows,last==null?0:last.ids.length,r.on()?1:0,r.strike);
    }
}
