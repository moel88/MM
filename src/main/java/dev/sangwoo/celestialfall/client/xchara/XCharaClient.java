package dev.sangwoo.celestialfall.client.xchara;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.xchara.XCharaUltimate;
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
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;

/**
 * X Chara's G, the sword dance, as this client knows it (2.112.0; the server's XCharaUltimate, XCharaRules for its timing).
 * G on the map is X Chara's player's key for it: it asks with the one the camera's middle looks at (as Lee So-sang's
 * crosshair picks, {@link #aim}). The server tells when it starts, when its strange space comes, and when it is blocked,
 * called off or over; everything between is drawn from its clock (the server's age as it was told, this client's ticks
 * since and the frame's part of a tick, its cast's: {@link #CASTS}, drawn with the effects, SwordDance).
 * <p>
 * Its strange space is its user's and the one marked's ({@link #cinematic}: the both of them see it, the user's answer):
 * their camera's own shots ({@link XCharaSpace}), the world's blocks, sky, particles and everyone else left out
 * ({@link #spaceHidden}, {@link #hides}), the dark ground cleared over everything, the two drawn there (the one marked
 * where it shows them, {@link #offset}), upside down facing each other ({@link #turn}), lit full. Everyone else sees the
 * two held where they stand, the "!!!", and the blows on the one marked in the world. Its sound: the alert as it starts,
 * then from the strange space on the rest of the user's recording (tools/xchara/cut_g_sound.py), loud for the two, fading
 * out to 40 blocks for the rest.
 */
public final class XCharaClient {
    /** Its effects' casts (SwordDance through KagenoRenderer), one for each sword dance this client knows of. */
    public static final Map<UUID,StandClient.Cast> CASTS=new LinkedHashMap<>();
    /** A sword dance as this client heard of it. */
    public static final class Dance {
        public final UUID user,targetId;public final int target;public final float yaw;public final Vec3d from,targetFrom,f,r,l;
        public final StandClient.Cast cast;
        /** When its strange space came (ticks on its clock, NaN until the server says), and when it ended and how (NaN while it runs). */
        float space=Float.NaN,ended=Float.NaN;int how=-1;
        long ready=Long.MIN_VALUE;
        Voice warn,voice;
        /** The blows its user's damage numbers have shown, and whether its user's (or the one marked's) camera was handed back. */
        int fed;boolean handed;
        Dance(UUID user,int target,UUID targetId,float yaw,Vec3d from,Vec3d targetFrom,StandClient.Cast cast){
            this.user=user;this.target=target;this.targetId=targetId;this.yaw=yaw;this.from=from;this.targetFrom=targetFrom;this.cast=cast;
            f=Vec3d.fromPolar(0,yaw);r=new Vec3d(-f.z,0,f.x);l=r.negate();
        }
        /** Ticks on its clock this frame. */
        public float age(float delta){return cast.age(delta);}
        /** When it is over on its clock: its end, or when it was blocked or called off. */
        public float over(){return Float.isNaN(ended)?END:Math.min(END,ended);}
        public boolean ended(){return !Float.isNaN(ended);}
        /** Whether it was blocked by a guard or called off (not done). */
        public boolean stopped(){return how==XCharaUltimate.BLOCKED||how==XCharaUltimate.CUT;}
        public boolean blocked(){return how==XCharaUltimate.BLOCKED;}
        /** Whether its strange space has come (the server's word) and it was not stopped before then. */
        public boolean inSpace(){return !Float.isNaN(space);}
        public boolean has(Entity e){return e!=null&&(e.getUuid().equals(user)||e.getId()==target&&e.getUuid().equals(targetId));}
        public boolean isTarget(Entity e){return e!=null&&e.getId()==target&&e.getUuid().equals(targetId);}
    }
    private static final Map<UUID,Dance> DANCES=new HashMap<>();
    /** When each player's G is ready again (the server's world time), as told. */
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static Object world;
    /** Whether Q is down from a press this client took for the hack (its repeats and its release are taken too). */
    private static boolean qHeld;
    private XCharaClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaUltimate.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong();int target=b.readVarInt();UUID targetId=b.readUuid();
            Vec3d from=vec(b),targetFrom=vec(b);float yaw=b.readFloat();long ready=b.readLong();
            if(!Float.isFinite(yaw)||now<start||now-start>END+400||type>XCharaUltimate.END)return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                if(world!=mc.world){clear();world=mc.world;}
                if(type!=XCharaUltimate.START&&type!=XCharaUltimate.SPACE)READY.put(user,ready);
                var d=DANCES.get(user);
                if(type==XCharaUltimate.START){start(mc,user,target,targetId,start,now,yaw,from,targetFrom);return;}
                if(d==null||d.cast.start()!=start)return;
                if(type==XCharaUltimate.SPACE){if(Float.isNaN(d.space)&&!d.ended())space(mc,d);return;}
                if(d.ended())return;
                d.ended=d.age(0);d.how=type;
                if(d.stopped()&&d.voice!=null)d.voice.fade();
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(XCharaClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(XCharaClient::clear));
        // the strange space's dark ground over the world (its blocks are not drawn: XCharaSpace)
        WorldRenderEvents.BEFORE_ENTITIES.register(XCharaClient::ground);
    }
    private static Vec3d vec(net.minecraft.network.PacketByteBuf b){return new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var d:DANCES.values()){stop(mc,d.warn);stop(mc,d.voice);}
        DANCES.clear();CASTS.clear();READY.clear();world=null;
    }
    private static void stop(MinecraftClient mc,Voice v){if(v!=null&&!v.isDone())mc.getSoundManager().stop(v);}
    private static void start(MinecraftClient mc,UUID user,int target,UUID targetId,long start,long now,float yaw,Vec3d from,Vec3d targetFrom){
        var old=DANCES.remove(user);
        if(old!=null){CASTS.remove(old.cast.id());if(old.voice!=null)old.voice.fade();}
        var cast=new StandClient.Cast(UUID.randomUUID(),user,mc.world.getRegistryKey().getValue(),SKILL,0,start,now,StandClient.ticks(),from,yaw,
            List.of(new StandClient.Target(targetId,target,targetFrom)));
        var d=new Dance(user,target,targetId,yaw,from,targetFrom,cast);
        DANCES.put(user,d);CASTS.put(cast.id(),cast);
        // its user looks at the one marked (the server turned them too)
        if(mc.player!=null&&mc.player.getUuid().equals(user)){mc.player.setYaw(yaw);mc.player.setHeadYaw(yaw);mc.player.setBodyYaw(yaw);mc.player.prevYaw=yaw;}
        // the alert, as the "!!!" comes (none if heard too late to come with it)
        if(d.age(0)<=MARK+4)d.warn=play(mc,d,CelestialFall.id("xchara_g_warn"),0);
    }
    private static void space(MinecraftClient mc,Dance d){
        d.space=d.age(0);
        // the rest of its recording, from the strange space on (none if heard too late to come with it)
        if(d.space<=WARN+6)d.voice=play(mc,d,CelestialFall.id("xchara_g"),0);
        if(mc.player!=null&&d.has(mc.player))dev.sangwoo.celestialfall.client.integration.CinematicsBridge.nativeCutscene();
    }
    /** Plays one of its sounds for this client: loud for its two, fading out to 40 blocks from the nearer of them for the rest (as it goes on). */
    private static Voice play(MinecraftClient mc,Dance d,Identifier id,int delay){
        var v=new Voice(id,()->loudness(mc,d));
        if(v.full()<=.01f)return null;
        if(delay>0)mc.getSoundManager().play(v,delay);else mc.getSoundManager().play(v);
        return v;
    }
    /** How loud its sounds are for this client now (before the game's volume settings). */
    static float loudness(MinecraftClient mc,Dance d){
        if(mc.player==null||mc.world==null)return 0;
        if(d.has(mc.player))return (float)CelestialFall.CONFIG.effectVolume;
        double far=Math.min(mc.player.getPos().distanceTo(d.from),mc.player.getPos().distanceTo(d.targetFrom));
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/40));
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!DANCES.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        for(var d:DANCES.values()){
            float t=d.age(0);
            // its user's damage numbers, blow by blow on its clock as the rush's (once the world is back, where they show)
            if(mc.player!=null&&mc.player.getUuid().equals(d.user)&&d.inSpace()&&!d.stopped()&&t>=WORLD){
                int by=Math.min(blowsBy(Math.min(t,d.over())),HITS);
                while(d.fed<by){dev.sangwoo.celestialfall.client.kageno.HitFeedback.hit(mc,d.target,blowDamage(d.fed),d.yaw,d.fed%8==0);d.fed++;}
            }
            // the world back: the two's own cameras look at each other (the user's video: from behind its user, at the one cut up)
            if(!d.handed&&mc.player!=null&&d.inSpace()&&t>=WORLD&&d.has(mc.player)){
                d.handed=true;
                if(!d.stopped()&&CelestialFall.CONFIG.cinematicCamera){
                    float yaw=MathHelper.wrapDegrees(d.isTarget(mc.player)?d.yaw+180:d.yaw);
                    mc.player.setYaw(yaw);mc.player.prevYaw=yaw;mc.player.setPitch(8);mc.player.prevPitch=8;
                    // the map's own camera too (it orbits by its own yaw and pitch)
                    BotandCamera.STATE.yaw=yaw;BotandCamera.STATE.pitch=12;
                }
            }
        }
        // one long over is let go
        DANCES.values().removeIf(d->{boolean gone=d.age(0)>d.over()+60;if(gone)CASTS.remove(d.cast.id());return gone;});
        READY.values().removeIf(at->at<mc.world.getTime()-20);
    }

    // ───────────────────────────── the key ─────────────────────────────

    /** X Chara's player's keys on the map: Q the hack, E, R, T, H and G its skills with it on (E the restore, R the double, T the weakening, H the cleave, G the sword dance: R, T and G asked with the one the camera's middle looks at, or none: the server then takes the one its look meets), I their inventory, Enter their chat. */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle())return false;
        // (2.115.0) Q let go after a press this took
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_Q&&action==org.lwjgl.glfw.GLFW.GLFW_RELEASE&&qHeld){qHeld=false;return true;}
        if(mc.currentScreen!=null||!MapClient.enabled()||mc.player==null||!StandManager.xChara(mc.player))return false;
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_Q){
            // (2.115.0) Q on the map is X Chara's player's hack, on and off (XCharaHack; Ctrl+Q still drops the stack, as the
            // game has it, and Q alone drops nothing: Star Platinum's Q the same)
            if(qHeld)return true;
            if(action!=org.lwjgl.glfw.GLFW.GLFW_PRESS||(modifiers&org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL)!=0)return false;
            qHeld=true;
            if(CombatClient.combatInput()&&ClientPlayNetworking.canSend(XCharaUltimate.INPUT)){
                var b=PacketByteBufs.create();b.writeByte(Q);ClientPlayNetworking.send(XCharaUltimate.INPUT,b);
            }
            return true;
        }
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_E){
            // E on the map is X Chara's player's skill key (their inventory on I, as the other characters'). (2.126.0, user
            // request: "q활성화y를 다시 q활성화 e로 옮기고") E with the hack on, the restore again (XCharaHeal; E's in 2.119 and
            // 2.120, Y's from 2.121 to 2.125); with it off, nothing yet
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&HackMode.on(mc.player)&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(XCharaUltimate.INPUT)){
                var b=PacketByteBufs.create();b.writeByte(E);ClientPlayNetworking.send(XCharaUltimate.INPUT,b);
            }
            return true;
        }
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_Y){
            // (2.127.0) Y with the hack on, the pull (XCharaPull): asked, as G, R and T, with the one the camera's middle
            // looks at; with it off, Y is taken and does nothing yet (the restore was Y's from 2.121 to 2.125)
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&HackMode.on(mc.player)&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(XCharaUltimate.INPUT)){
                var target=aim(mc);
                BotandCamera.prepareInteraction();
                var b=PacketByteBufs.create();b.writeByte(Y);b.writeVarInt(target==null?-1:target.getId());ClientPlayNetworking.send(XCharaUltimate.INPUT,b);
            }
            return true;
        }
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_T){
            // (2.126.0) T with the hack on, the weakening (XCharaWeaken): asked, as G and R, with the one the camera's middle
            // looks at; with it off, T is taken and does nothing yet. T is the game's chat key: X Chara's player opens the
            // chat with Enter (below), as Star Platinum's and Lee So-sang's
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&HackMode.on(mc.player)&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(XCharaUltimate.INPUT)){
                var target=aim(mc);
                BotandCamera.prepareInteraction();
                var b=PacketByteBufs.create();b.writeByte(T);b.writeVarInt(target==null?-1:target.getId());ClientPlayNetworking.send(XCharaUltimate.INPUT,b);
            }
            return true;
        }
        if((key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER||key==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)&&action==org.lwjgl.glfw.GLFW.GLFW_PRESS){
            mc.setScreen(new net.minecraft.client.gui.screen.ChatScreen(""));
            return true;
        }
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_R){
            // (2.121.0) R with the hack on, the cleave (XCharaAttack.cleave: the way they look as it begins); (2.124.0) with
            // it off, the jab (XCharaAttack.jab): the server takes the one or the other by their hack. R is the map's use key
            // otherwise: X Chara's player's is their skill's, as Star Platinum's and Kageno's (the fruit is eaten with the
            // attack key)
            // (2.125.0) with the hack on R is the double (XCharaClone): asked, as G, with the one the camera's middle looks
            // at (the cleave, R's from 2.121.0, is H's now)
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(XCharaUltimate.INPUT)){
                var target=HackMode.on(mc.player)?aim(mc):null;
                BotandCamera.prepareInteraction();
                var b=PacketByteBufs.create();b.writeByte(R);b.writeVarInt(target==null?-1:target.getId());ClientPlayNetworking.send(XCharaUltimate.INPUT,b);
            }
            return true;
        }
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_H){
            // (2.125.0, user request: "Q활성화 R스킬을 Q활성화 H스킬로 옮겨") H with the hack on, the cleave (XCharaAttack.cleave:
            // the way they look as it begins); with it off, H is taken and does nothing yet
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&HackMode.on(mc.player)&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(XCharaUltimate.INPUT)){
                BotandCamera.prepareInteraction();
                var b=PacketByteBufs.create();b.writeByte(H);ClientPlayNetworking.send(XCharaUltimate.INPUT,b);
            }
            return true;
        }
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_I&&action==org.lwjgl.glfw.GLFW.GLFW_PRESS){
            if(!CombatClient.actionLocked())mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player));
            return true;
        }
        if(key!=org.lwjgl.glfw.GLFW.GLFW_KEY_G)return false;
        // (2.119.0, user request: "지금 G스킬은 Q 활성화 상태의 G스킬로 바꿔") the sword dance is the hack's G: asked only with
        // the hack on (with it off, G is taken and does nothing yet)
        // (2.128.0) with the hack off G is the slam (XCharaAttack.slam): asked with no one (where it comes down is told
        // from up in the air, OverwriteSlam.tellAim)
        if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(XCharaUltimate.INPUT)){
            var target=HackMode.on(mc.player)?aim(mc):null;
            BotandCamera.prepareInteraction();
            var b=PacketByteBufs.create();b.writeByte(G);b.writeVarInt(target==null?-1:target.getId());ClientPlayNetworking.send(XCharaUltimate.INPUT,b);
        }
        return true;
    }
    /**
     * Whom the camera's middle looks at now (as Lee So-sang's crosshair picks): the body nearest its middle line within
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

    /** Every sword dance this client knows of. */
    static java.util.Collection<Dance> all(){return DANCES.values();}
    /** This player's sword dance (as its user), while it shows. */
    public static Dance of(UUID user){return DANCES.get(user);}
    /** The sword dance its cast draws. */
    static Dance ofCast(StandClient.Cast cast){var d=DANCES.get(cast.caster());return d!=null&&d.cast==cast?d:null;}
    /** When this player's G is ready again (the server's world time; long ago if never). */
    public static long ready(UUID player){return READY.getOrDefault(player,Long.MIN_VALUE);}
    /** Whether this one is held where they stand by a sword dance now (KagenoManager.frozen on this client): its user until it is over, the one marked from its strange space. */
    public static boolean holds(Entity e){
        if(e==null||!MapClient.enabled())return false;
        // (2.125.0) and by their double: its user until it has thrown, the one marked from the throw until the wall lets them go
        if(CloneClient.holds(e))return true;
        // (2.126.0) and by their weakening: its user through its "!!!"
        if(WeakenClient.holds(e)||PullClient.holds(e))return true;
        for(var d:DANCES.values()){
            float t=d.age(0);if(t<0||t>=d.over())continue;
            if(e.getUuid().equals(d.user))return true;
            if(d.isTarget(e)&&d.inSpace())return true;
        }
        return false;
    }
    /** The sword dance whose strange space this client's camera is in now (its user's or the one marked's), or null. */
    public static Dance cinematic(){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||!mc.player.isAlive()||!MapClient.enabled()||!CelestialFall.CONFIG.cinematicCamera)return null;
        float delta=mc.getTickDelta();
        for(var d:DANCES.values())if(showing(mc,d,d.age(delta)))return d;
        return null;
    }
    /** Whether this client's camera is in {@code d}'s strange space at {@code t}: one of its two, its space come, not yet back in the world, not stopped before. */
    private static boolean showing(MinecraftClient mc,Dance d,float t){
        return d.has(mc.player)&&d.inSpace()&&t>=WARN&&t<WORLD&&(!d.ended()||t<d.ended);
    }
    /** Whether the world is left out of this client's frame now: its camera is in a sword dance's strange space. */
    public static boolean spaceHidden(){return cinematic()!=null;}
    /** Whether {@code e} is left out of this client's frame now: in the strange space, everyone but the two (and they at times: XCharaSpace.shown). */
    public static boolean hides(Entity e){
        var d=cinematic();if(d==null)return false;
        return !d.has(e)||!XCharaSpace.shown(d,d.isTarget(e),d.age(MinecraftClient.getInstance().getTickDelta()));
    }
    /** Whether {@code e} is drawn wherever the strange space shows it (out of its frustum where it really is). */
    public static boolean staged(Entity e){
        var d=cinematic();return d!=null&&d.has(e)&&XCharaSpace.shown(d,d.isTarget(e),d.age(MinecraftClient.getInstance().getTickDelta()));
    }
    /** Where the strange space shows {@code e}, as an offset from where it is (null if it does not move it): the one marked, across from its user. */
    public static Vec3d offset(Entity e,float delta){
        var d=cinematic();if(d==null||!d.isTarget(e))return null;
        return XCharaSpace.targetFeet(d).subtract(e.getLerpedPos(delta));
    }
    /**
     * The strange space's turn of the two's bodies (after the renderer's own, about their feet, local +Z behind them):
     * each turned to face the other, upside down about its middle from the strange space's first moment (XCharaSpace.inverted).
     */
    public static void turn(LivingEntity e,float delta,float bodyYaw,MatrixStack m){
        var d=cinematic();if(d==null||!d.has(e))return;
        float t=d.age(delta);boolean target=d.isTarget(e);
        if(!XCharaSpace.shown(d,target,t))return;
        float want=target?d.yaw+180:d.yaw;
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.wrapDegrees(bodyYaw-want)));
        if(XCharaSpace.inverted(t)){
            float mid=e.getHeight()*.5f;
            m.translate(0,mid,0);m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));m.translate(0,-mid,0);
        }
    }
    /** The light the two are drawn in, in the strange space (full: it has no sun nor shade), or -1 for the world's own. */
    public static int light(Entity e){var d=cinematic();return d!=null&&d.has(e)?0xF000F0:-1;}
    /** Whether this client's player is held by a sword dance (no action of theirs goes through). */
    public static boolean locked(){var mc=MinecraftClient.getInstance();return mc.player!=null&&holds(mc.player);}

    // ───────────────────────────── its camera ─────────────────────────────

    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){
        var d=cinematic();if(d==null)return null;
        var s=XCharaSpace.shot(d,d.age(delta));
        Vec3d dir=s.look().subtract(s.eye());if(dir.lengthSquared()<1e-8)dir=d.f;dir=dir.normalize();
        float sy=(float)Math.toDegrees(Math.atan2(-dir.x,dir.z)),sp=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(dir.y,-1,1)));
        return new CinematicDirector.Pose(s.eye(),sy,sp,0);
    }
    public static double fov(double vanilla,float delta){var d=cinematic();return d==null?vanilla:XCharaSpace.shot(d,d.age(delta)).fov();}

    /** The strange space's dark ground: the world's colour cleared before anyone is drawn (its blocks are not). */
    private static void ground(WorldRenderContext ctx){
        if(!spaceHidden())return;
        RenderSystem.clearColor(XCharaSpace.SKY[0],XCharaSpace.SKY[1],XCharaSpace.SKY[2],1);
        RenderSystem.clear(16384,MinecraftClient.IS_SYSTEM_MAC);
    }

    /** For the capture tools: this player's sword dance (its age, whether its space came, ended, how), or null. */
    public static String describe(PlayerEntity p,float delta){
        var d=DANCES.get(p.getUuid());if(d==null)return null;
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%d,%d",d.age(delta),d.inSpace()?1:0,d.ended()?1:0,d.how);
    }

    /** One of its sounds, its loudness kept as the listener moves (from a source), faded out over FADE ticks when stopped. */
    static final class Voice extends AbstractSoundInstance implements TickableSoundInstance {
        static final int FADE=3;
        private final java.util.function.Supplier<Float> loud;private int fading=-1;private boolean done,begun,dropped;private float scale=1;
        Voice(Identifier id,java.util.function.Supplier<Float> loud){this(id,loud,1);}
        /** (2.114.0) At a pitch of its own: a blow of the knife's basic attack, each a little higher than the one before. */
        Voice(Identifier id,java.util.function.Supplier<Float> loud,float pitch){
            super(id,SoundCategory.PLAYERS,net.minecraft.util.math.random.Random.create());
            this.loud=loud;volume=loud.get();this.pitch=pitch;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        float full(){return volume;}
        /** Whether it has begun to play (one played with a delay has not until then). */
        boolean begun(){return begun&&!dropped;}
        /** Fades it out over FADE ticks; one that has not begun yet (played with a delay, 2.124.0: the jab's thrust) never begins. */
        void fade(){if(!begun){dropped=true;done=true;}if(fading<0)fading=0;}
        @Override public boolean canPlay(){return !dropped;}
        @Override public boolean shouldAlwaysPlay(){return true;}
        @Override public boolean isDone(){return done;}
        @Override public void tick(){
            begun=true;
            if(fading>=0){fading++;scale=Math.max(0,1-fading/(float)FADE);if(fading>=FADE)done=true;}
            volume=Math.max(0,loud.get())*scale;
        }
    }
}
