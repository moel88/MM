package dev.sangwoo.celestialfall.client.samurai;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.samurai.SamuraiUltimate;
import dev.sangwoo.celestialfall.stand.StandManager;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.*;

/**
 * The samurai's G, the one flash, as this client knows it (2.143.0; the server's SamuraiUltimate, SamuraiRules for its
 * timing), built as X Chara's sword dance is (XCharaClient). G on the map is the samurai's player's key for it: it asks
 * with the one the camera's middle looks at ({@link #aim}). The server tells when it starts, when its cutscene begins
 * (where the two stand), and when it is blocked, called off or over; everything between is drawn from its clock (its
 * cast's: {@link #CASTS}, drawn with the effects, OneFlash; its user's body by its clip, SamuraiKata).
 * <p>
 * Its cutscene is its user's and the one marked's ({@link #cinematic}): their camera's own shots (SamuraiScene), the two
 * drawn where it shows them ({@link #offset}), the one marked turned as it has them ({@link #turn}); in its flat parts the
 * world's blocks, sky and everyone are left out ({@link #flat}, {@link #hides}), a flat ground cleared instead, and the
 * two drawn as flat shapes (SamuraiFlat), the ink over them (SamuraiInk); its grey part takes the world's colour
 * ({@link #grey}). Everyone else sees the two in the world: its user gone to the one marked's side, the flourish, the
 * sword put back, the four cuts through the one marked where they stand, and the pillar of light. Its sounds are ones the
 * map has already (Kageno's sword's four and the clash: none is new).
 */
public final class SamuraiClient {
    /** Its effects' casts (OneFlash through KagenoRenderer), one for each G this client knows of. */
    public static final Map<UUID,StandClient.Cast> CASTS=new LinkedHashMap<>();
    /** One G as this client heard of it. */
    public static final class Flash {
        public final UUID user,targetId;public final int target;public final float yaw;public final Vec3d from,targetFrom;
        public final StandClient.Cast cast;
        /** When its cutscene began (ticks on its clock, NaN until the server says), and when it ended and how (NaN while it runs). */
        float scene=Float.NaN,ended=Float.NaN;int how=-1;
        /** Where its user stands in the cutscene and the one marked is held, the way its user came (level), and the side its side shots are from. */
        public Vec3d spot,hold,f;public int side=1;
        /** Its sounds begun so far, the blows its user's damage numbers have shown, and whether its two's cameras were handed back. */
        int cues,fed;boolean handed;
        final java.util.List<Voice> voices=new java.util.ArrayList<>();
        Flash(UUID user,int target,UUID targetId,float yaw,Vec3d from,Vec3d targetFrom,StandClient.Cast cast){
            this.user=user;this.target=target;this.targetId=targetId;this.yaw=yaw;this.from=from;this.targetFrom=targetFrom;this.cast=cast;
            spot=from;hold=targetFrom;f=Vec3d.fromPolar(0,yaw);
        }
        /** Ticks on its clock this frame. */
        public float age(float delta){return cast.age(delta);}
        /** Seconds on its clock this frame. */
        public float sec(float delta){return cast.age(delta)/20f;}
        /** When it is over on its clock: its end, or when it was blocked or called off. */
        public float over(){return Float.isNaN(ended)?END:Math.min(END,ended);}
        public boolean ended(){return !Float.isNaN(ended);}
        /** Whether it was blocked by a guard or called off (not done). */
        public boolean stopped(){return how==SamuraiUltimate.BLOCKED||how==SamuraiUltimate.CUT;}
        public boolean blocked(){return how==SamuraiUltimate.BLOCKED;}
        /** Whether its cutscene has begun (the server's word). */
        public boolean inScene(){return !Float.isNaN(scene);}
        /** Whether it runs at {@code t} ticks: not yet stopped then. */
        public boolean runs(float t){return !stopped()||t<ended;}
        public boolean has(Entity e){return e!=null&&(e.getUuid().equals(user)||isTarget(e));}
        public boolean isTarget(Entity e){return e!=null&&e.getId()==target&&e.getUuid().equals(targetId);}
        /** The way its user's body faces at {@code t} ticks (degrees): the one marked before the dash, back the way they came after. */
        public float facing(float t){return inScene()&&t>=DASH?MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-f.x,f.z))+180):yaw;}
    }
    private static final Map<UUID,Flash> FLASHES=new HashMap<>();
    /** When each player's G is ready again (the server's world time), as told. */
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static Object world;
    private SamuraiClient(){}

    /** Its sounds (ones the map has: Kageno's sword's four, the clash): {seconds on its clock, the sound, its loudness, its pitch}. */
    static final Object[][] CUES={
        {SamuraiScene.DASH,"kageno_m1_4",1f,1f},{SamuraiKata.SPIN-.1f,"kageno_m1_2",.7f,1.15f},{SamuraiKata.WHIRL-.06f,"kageno_m1_3",.95f,.96f},
        {SamuraiScene.FLASH,"kageno_m1_4",1f,.82f},
        {SLASH_SEC[0],"kageno_m1_1",1f,.95f},{SLASH_SEC[1],"kageno_m1_2",1f,1f},{SLASH_SEC[2],"kageno_m1_3",1f,1.05f},{SLASH_SEC[3],"kageno_m1_4",1f,1.1f},
        {SamuraiKata.SNAP-.02f,"clash",1f,1f}};

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(SamuraiUltimate.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong();int target=b.readVarInt();UUID targetId=b.readUuid();
            Vec3d from=vec(b),targetFrom=vec(b);float yaw=b.readFloat();Vec3d spot=vec(b),hold=vec(b);long ready=b.readLong();
            if(!Float.isFinite(yaw)||now<start||now-start>END+400||type>SamuraiUltimate.END)return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                if(world!=mc.world){clear();world=mc.world;}
                if(type!=SamuraiUltimate.START&&type!=SamuraiUltimate.SCENE)READY.put(user,ready);
                var d=FLASHES.get(user);
                if(type==SamuraiUltimate.START){start(mc,user,target,targetId,start,now,yaw,from,targetFrom);return;}
                if(d==null||d.cast.start()!=start)return;
                if(type==SamuraiUltimate.SCENE){if(!d.inScene()&&!d.ended())scene(mc,d,yaw,spot,hold);return;}
                if(d.ended())return;
                d.ended=d.age(0);d.how=type;
                if(d.stopped())for(var v:d.voices)v.fade();
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(SamuraiClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(SamuraiClient::clear));
        // its flat parts' ground over the world (its blocks are not drawn), and the flat shapes of its two on it
        WorldRenderEvents.BEFORE_ENTITIES.register(SamuraiClient::ground);
        WorldRenderEvents.AFTER_ENTITIES.register(SamuraiFlat::render);
    }
    private static Vec3d vec(net.minecraft.network.PacketByteBuf b){return new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var d:FLASHES.values())for(var v:d.voices)if(!v.isDone())mc.getSoundManager().stop(v);
        FLASHES.clear();CASTS.clear();READY.clear();world=null;
    }
    private static void start(MinecraftClient mc,UUID user,int target,UUID targetId,long start,long now,float yaw,Vec3d from,Vec3d targetFrom){
        var old=FLASHES.remove(user);
        if(old!=null){CASTS.remove(old.cast.id());for(var v:old.voices)v.fade();}
        var cast=new StandClient.Cast(UUID.randomUUID(),user,mc.world.getRegistryKey().getValue(),SKILL,0,start,now,StandClient.ticks(),from,yaw,
            List.of(new StandClient.Target(targetId,target,targetFrom)));
        var d=new Flash(user,target,targetId,yaw,from,targetFrom,cast);
        FLASHES.put(user,d);CASTS.put(cast.id(),cast);
        // its user looks at the one marked (the server turned them too)
        if(mc.player!=null&&mc.player.getUuid().equals(user)){mc.player.setYaw(yaw);mc.player.setHeadYaw(yaw);mc.player.setBodyYaw(yaw);mc.player.prevYaw=yaw;}
    }
    private static void scene(MinecraftClient mc,Flash d,float yaw,Vec3d spot,Vec3d hold){
        d.scene=d.age(0);d.spot=spot;d.hold=hold;d.f=Vec3d.fromPolar(0,yaw);
        // the side of that way its user stands (the server's choice, where there was room): the frame's right, as the video's, or its left
        d.side=spot.subtract(hold).dotProduct(SamuraiScene.right(d.f))<-.2?-1:1;
        if(mc.player!=null&&d.has(mc.player))dev.sangwoo.celestialfall.client.integration.CinematicsBridge.nativeCutscene();
    }
    /** How loud its sounds are for this client now (before the game's volume settings): full for its two, fading out to 40 blocks for the rest. */
    static float loudness(MinecraftClient mc,Flash d){
        if(mc.player==null||mc.world==null)return 0;
        if(d.has(mc.player))return (float)CelestialFall.CONFIG.effectVolume;
        double far=Math.min(mc.player.getPos().distanceTo(d.spot),mc.player.getPos().distanceTo(d.hold));
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/40));
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!FLASHES.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        for(var d:FLASHES.values()){
            float t=d.age(0),sec=t/20f;
            // its sounds, each as its moment comes (none heard too late, nor past its stop; none of the cutscene's before the cutscene)
            while(d.cues<CUES.length&&(Float)CUES[d.cues][0]<=sec+.025f){
                Object[] cue=CUES[d.cues++];float at=(Float)cue[0];
                if(sec-at>.2f||!d.runs(at*20)||!d.inScene())continue;
                float part=(Float)cue[2];
                var v=new Voice(CelestialFall.id((String)cue[1]),()->loudness(mc,d)*part,(Float)cue[3]);
                if(v.full()>.01f){mc.getSoundManager().play(v);d.voices.add(v);}
            }
            // its user's damage numbers, blow by blow on its clock (once the world is back, where they show)
            if(mc.player!=null&&mc.player.getUuid().equals(d.user)&&d.inScene()&&!d.stopped()&&sec>=SamuraiScene.WORLD){
                int by=Math.min(blowsBy(Math.min(t,d.over())),BLOWS.length);
                while(d.fed<by){dev.sangwoo.celestialfall.client.kageno.HitFeedback.hit(mc,d.target,blowDamage(d.fed),d.facing(t)+180,d.fed==0||d.fed==BLOWS.length-1);d.fed++;}
            }
            // the world back: its user's own camera looks at the one marked from behind them, the one marked's at its user
            if(!d.handed&&mc.player!=null&&d.inScene()&&sec>=SamuraiScene.WORLD&&d.has(mc.player)){
                d.handed=true;
                if(!d.stopped()&&CelestialFall.CONFIG.cinematicCamera){
                    Vec3d way=d.isTarget(mc.player)?d.spot.subtract(d.hold):d.hold.subtract(d.spot);
                    float yaw=(float)Math.toDegrees(Math.atan2(-way.x,way.z));
                    mc.player.setYaw(yaw);mc.player.prevYaw=yaw;mc.player.setPitch(4);mc.player.prevPitch=4;
                    // the map's own camera too (it orbits by its own yaw and pitch)
                    BotandCamera.STATE.yaw=yaw;BotandCamera.STATE.pitch=10;
                }
            }
        }
        // one long over is let go
        FLASHES.values().removeIf(d->{boolean gone=d.age(0)>Math.max(d.over(),END)+60;if(gone)CASTS.remove(d.cast.id());return gone;});
        READY.values().removeIf(at->at<mc.world.getTime()-20);
    }

    // ───────────────────────────── the key ─────────────────────────────

    /** The samurai's player's key on the map: G their one skill, asked with the one the camera's middle looks at (none: the server takes the one their look meets). */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle())return false;
        if(mc.currentScreen!=null||!MapClient.enabled()||mc.player==null||!StandManager.samurai(mc.player))return false;
        if(key!=org.lwjgl.glfw.GLFW.GLFW_KEY_G)return false;
        if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(SamuraiUltimate.INPUT)){
            var target=aim(mc);
            BotandCamera.prepareInteraction();
            var b=PacketByteBufs.create();b.writeByte(G);b.writeVarInt(target==null?-1:target.getId());ClientPlayNetworking.send(SamuraiUltimate.INPUT,b);
        }
        return true;
    }
    /**
     * Whom the camera's middle looks at now (as X Chara's crosshair picks): the body nearest its middle line within
     * AIM_CONE, within AIM_RANGE of the player's eyes, in their sight and fair game; null if none.
     */
    static LivingEntity aim(MinecraftClient mc){
        var me=mc.player;if(me==null||mc.world==null||mc.gameRenderer==null)return null;
        var cam=mc.gameRenderer.getCamera();Vec3d eye=me.getEyePos(),look=Vec3d.fromPolar(cam.getPitch(),cam.getYaw()),c=cam.getPos();
        LivingEntity best=null;double near=AIM_CONE;
        var room=me.getBoundingBox().expand(AIM_RANGE+2);
        for(var e:mc.world.getEntitiesByClass(LivingEntity.class,room,e->e!=me&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d chest=e.getPos().add(0,e.getHeight()*.55,0);
            if(eye.squaredDistanceTo(chest)>AIM_RANGE*AIM_RANGE)continue;
            double off=Math.PI;
            for(double up:new double[]{.15,.55,.9}){Vec3d p=e.getPos().add(0,e.getHeight()*up,0).subtract(c);double len=p.length();if(len<1e-4)continue;off=Math.min(off,Math.acos(Math.max(-1,Math.min(1,p.dotProduct(look)/len))));}
            if(off>=near)continue;
            if(mc.world.raycast(new RaycastContext(eye,chest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,me)).getType()!=HitResult.Type.MISS)continue;
            if(e instanceof PlayerEntity other&&!me.shouldDamagePlayer(other))continue;
            near=off;best=e;
        }
        return best;
    }

    // ───────────────────────────── who is in one ─────────────────────────────

    /** Every G this client knows of. */
    static java.util.Collection<Flash> all(){return FLASHES.values();}
    /** This player's G (as its user), while it shows. */
    public static Flash of(UUID user){return FLASHES.get(user);}
    /** The G its cast draws. */
    static Flash ofCast(StandClient.Cast cast){var d=cast==null?null:FLASHES.get(cast.caster());return d!=null&&d.cast==cast?d:null;}
    /** The G this one is marked by or held in (as the one marked), or null. */
    static Flash ofTarget(Entity e){
        if(e==null)return null;
        for(var d:FLASHES.values())if(d.isTarget(e))return d;
        return null;
    }
    /** When this player's G is ready again (the server's world time; long ago if never). */
    public static long ready(UUID player){return READY.getOrDefault(player,Long.MIN_VALUE);}
    /** Whether this one is held where they stand by a G now (KagenoManager.frozen on this client): its user until it is over, the one marked through its cutscene. */
    public static boolean holds(Entity e){
        if(e==null||!MapClient.enabled())return false;
        for(var d:FLASHES.values()){
            float t=d.age(0);if(t<0||t>=d.over())continue;
            if(e.getUuid().equals(d.user))return true;
            if(d.isTarget(e)&&d.inScene()&&t<LAUNCH)return true;
        }
        return false;
    }
    /** The cast whose clip moves this player's body now (KagenoClips.animated): their G's, until it is over or stopped. */
    public static StandClient.Cast animated(UUID id){
        var d=FLASHES.get(id);if(d==null)return null;
        float t=d.age(0);
        return t>=0&&t<SamuraiKata.LENGTH*20&&d.runs(t)&&(d.inScene()||t<DASH+4)?d.cast:null;
    }
    /** The way a G's cast's body is drawn facing (KagenoClips.facing). */
    public static float facing(StandClient.Cast cast,float delta){var d=ofCast(cast);return d==null?cast.yaw():d.facing(cast.age(delta));}
    /** Whether this player holds the samurai's sword now (drawn in their hand whatever it holds): through their G. */
    public static boolean armed(PlayerEntity p){return p!=null&&animated(p.getUuid())!=null;}
    /** The G whose cutscene this client's camera is in now (its user's or the one marked's), or null. */
    public static Flash cinematic(){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||!mc.player.isAlive()||!MapClient.enabled()||!CelestialFall.CONFIG.cinematicCamera)return null;
        float delta=mc.getTickDelta();
        for(var d:FLASHES.values())if(showing(mc,d,d.age(delta)))return d;
        return null;
    }
    /** Whether this client's camera is in {@code d}'s cutscene at {@code t}: one of its two, its cutscene begun, the world not yet back, not stopped. */
    private static boolean showing(MinecraftClient mc,Flash d,float t){
        return d.has(mc.player)&&d.inScene()&&SamuraiScene.shown(t/20f)&&(!d.ended()||t<d.ended);
    }
    /** The part of the cutscene this client's camera is in now, or null. */
    static SamuraiScene.Part part(){var d=cinematic();return d==null?null:SamuraiScene.part(d.sec(MinecraftClient.getInstance().getTickDelta()));}
    /** Whether the world is left out of this client's frame now: its camera is in one of the cutscene's flat parts. */
    public static boolean flat(){var p=part();return p!=null&&SamuraiScene.flat(p);}
    /** Whether {@code e} is left out of this client's frame now: everyone, in the cutscene's flat parts (its two are drawn as flat shapes, SamuraiFlat). */
    public static boolean hides(Entity e){return flat();}
    /** Whether {@code e} is drawn wherever the cutscene shows it (out of its frustum where it really is). */
    public static boolean staged(Entity e){var d=cinematic();return d!=null&&d.has(e)&&!flat();}
    /**
     * Where a G shows {@code e}, as an offset from where it is (null if it does not move it). Its user, from the dash:
     * where the server put them at once (and, for its two, beyond the one marked in the side shots). The one marked,
     * held: where they are held, lifted as the cuts throw them.
     */
    public static Vec3d offset(Entity e,float delta){
        if(e==null||FLASHES.isEmpty())return null;
        var d=FLASHES.get(e.getUuid());
        if(d!=null){
            float t=d.age(delta),sec=t/20f;
            if(!d.inScene()||t<DASH||sec>=SamuraiKata.LENGTH||!d.runs(t))return null;
            Vec3d at=cinematic()==d?SamuraiScene.userFeet(d.spot,d.hold,d.f,d.side,sec):d.spot;
            // (after the cutscene they are where the server has them: only until they have settled there)
            if(sec>=SamuraiScene.WORLD&&e.getLerpedPos(delta).squaredDistanceTo(d.spot)>1)return null;
            return at.subtract(e.getLerpedPos(delta));
        }
        d=ofTarget(e);
        if(d==null||!d.inScene())return null;
        float t=d.age(delta);
        if(t<DASH||t>=LAUNCH||!d.runs(t))return null;
        return d.hold.add(0,SamuraiScene.reel(t/20f)[1],0).subtract(e.getLerpedPos(delta));
    }
    /**
     * The cutscene's turn of the one marked's body (after the renderer's own, about their feet, local +Z behind them):
     * facing the way its user came from, bent forward and leaning over about their middle as the flash goes through them,
     * and thrown one way and the other in the frame by the four cuts (SamuraiScene.reel).
     */
    public static void turn(LivingEntity e,float delta,float bodyYaw,MatrixStack m){
        if(FLASHES.isEmpty())return;
        var d=ofTarget(e);if(d==null||!d.inScene())return;
        float t=d.age(delta);if(t<DASH||t>=LAUNCH||!d.runs(t))return;
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.wrapDegrees(bodyYaw-d.facing(t))));
        float[] reel=SamuraiScene.reel(t/20f);
        if(reel[0]!=0||reel[2]!=0){
            float mid=e.getHeight()*.5f;
            m.translate(0,mid,0);m.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(reel[0]*d.side));m.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(reel[2]));m.translate(0,-mid,0);
        }
    }
    /** The limbs of {@code e} this frame if a G holds them as the one marked (SamuraiStruck), or null. */
    public static dev.sangwoo.celestialfall.combat.MotionPose struckPose(LivingEntity e,float delta){
        if(FLASHES.isEmpty())return null;
        var d=ofTarget(e);if(d==null||!d.inScene()||!e.isAlive())return null;
        float t=d.age(delta);if(t<DASH||t>=LAUNCH||!d.runs(t))return null;
        return SamuraiStruck.at(t/20f);
    }
    /** Whether this client's player is held by a G (no action of theirs goes through). */
    public static boolean locked(){var mc=MinecraftClient.getInstance();return mc.player!=null&&holds(mc.player);}
    /** How grey this client's world is drawn now (0 to 1): the cutscene's grey part. */
    public static float grey(float delta){
        var d=cinematic();if(d==null)return 0;
        return SamuraiScene.part(d.sec(delta))==SamuraiScene.Part.GREY_WORLD?1:0;
    }

    // ───────────────────────────── its camera ─────────────────────────────

    /** The cutscene's shot now, pulled in before anything that stands between its eye and what it looks at (the parts in the world). */
    static SamuraiScene.Shot shot(Flash d,float sec){
        var s=SamuraiScene.shot(d.spot,d.hold,d.f,d.side,sec);
        var mc=MinecraftClient.getInstance();
        if(SamuraiScene.flat(SamuraiScene.part(sec))||mc.world==null)return s;
        var hit=mc.world.raycast(new RaycastContext(s.look(),s.eye(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        if(hit.getType()==HitResult.Type.MISS)return s;
        Vec3d way=s.eye().subtract(s.look());double len=way.length(),room=Math.max(.6,hit.getPos().distanceTo(s.look())-.35);
        return room>=len?s:new SamuraiScene.Shot(s.look().add(way.multiply(room/len)),s.look(),s.fov(),s.roll());
    }
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){
        var d=cinematic();if(d==null)return null;
        float sec=d.sec(delta);var s=shot(d,sec);
        Vec3d dir=s.look().subtract(s.eye());if(dir.lengthSquared()<1e-8)dir=d.f;dir=dir.normalize();
        float sy=(float)Math.toDegrees(Math.atan2(-dir.x,dir.z)),sp=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(dir.y,-1,1)));
        float[] jolt=OneFlash.jolt(sec);
        return new CinematicDirector.Pose(s.eye(),sy+jolt[0],sp+jolt[1],s.roll()+jolt[2]);
    }
    public static double fov(double vanilla,float delta){var d=cinematic();return d==null?vanilla:shot(d,d.sec(delta)).fov();}
    public static float roll(float delta){var d=cinematic();if(d==null)return 0;float sec=d.sec(delta);return shot(d,sec).roll()+OneFlash.jolt(sec)[2];}

    /** The flat parts' ground: the world's colour cleared before anyone is drawn (its blocks are not). */
    private static void ground(WorldRenderContext ctx){
        var p=part();if(p==null||!SamuraiScene.flat(p))return;
        float g=SamuraiScene.ground(p);
        RenderSystem.clearColor(g,g,g,1);
        RenderSystem.clear(16384,MinecraftClient.IS_SYSTEM_MAC);
    }

    /** For the capture tools: this player's G (its age, whether its cutscene began, ended, how, the part this client's camera is in), or null. */
    public static String describe(PlayerEntity p,float delta){
        var d=FLASHES.get(p.getUuid());if(d==null)return null;
        var part=cinematic()==d?SamuraiScene.part(d.sec(delta)):null;
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%d,%d,%s",d.age(delta),d.inScene()?1:0,d.ended()?1:0,d.how,part==null?"-":part.name());
    }

    /** One of its sounds, its loudness kept as the listener moves, faded out over FADE ticks when stopped (XCharaClient.Voice's way). */
    static final class Voice extends AbstractSoundInstance implements TickableSoundInstance {
        static final int FADE=3;
        private final java.util.function.Supplier<Float> loud;private int fading=-1;private boolean done;private float scale=1;
        Voice(Identifier id,java.util.function.Supplier<Float> loud,float pitch){
            super(id,SoundCategory.PLAYERS,net.minecraft.util.math.random.Random.create());
            this.loud=loud;volume=loud.get();this.pitch=pitch;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        float full(){return volume;}
        void fade(){if(fading<0)fading=0;}
        @Override public boolean shouldAlwaysPlay(){return true;}
        @Override public boolean isDone(){return done;}
        @Override public void tick(){
            if(fading>=0){fading++;scale=Math.max(0,1-fading/(float)FADE);if(fading>=FADE)done=true;}
            volume=Math.max(0,loud.get())*scale;
        }
    }
}
