package dev.sangwoo.celestialfall.client;

import java.util.*;
import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.client.atomic.AtomicWorld;
import dev.sangwoo.celestialfall.client.kageno.AtomicPose;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Client side of G. The ordinary player camera is kept for the whole skill (no cutscene). */
public final class AtomicClient {
    public static final Map<UUID,Cast> CASTS=new LinkedHashMap<>();
    public static final class Cast {
        public final UUID id,caster;
        public final Identifier dimension;
        public final Vec3d origin;
        public final float yaw;
        public final long start;
        public final double radius;
        final CinematicSoundSequence audio=new CinematicSoundSequence(CinematicAudioPlan.ATOMIC);
        Cast(UUID id,UUID caster,Identifier dimension,Vec3d origin,float yaw,long start,double radius) {
            this.id=id;this.caster=caster;this.dimension=dimension;this.origin=origin;this.yaw=yaw;this.start=start;
            this.radius=Math.max(8,Math.min(768,radius));
        }
        public float age(float delta) { var w=MinecraftClient.getInstance().world;return w==null?AtomicTimeline.END:Math.max(0,w.getTime()-start+delta); }
    }
    private AtomicClient() {}
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(CelestialFall.ATOMIC_PACKET,(mc,h,b,r)->{
            UUID id=b.readUuid(),caster=b.readUuid();Identifier dim=b.readIdentifier();
            Vec3d pos=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();long start=b.readLong();double radius=b.readDouble();
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null || mc.player==null || !mc.world.getRegistryKey().getValue().equals(dim) || !Double.isFinite(radius))return;
                int age=(int)Math.max(0,mc.world.getTime()-start);
                if(age>=AtomicTimeline.END || CASTS.containsKey(id))return;
                // A fresh cast starts on arrival, so its one-frame flash is never lost to network delay.
                long begin=age<=3?mc.world.getTime():start;
                if(CASTS.size()>=4) { var oldest=CASTS.remove(CASTS.keySet().iterator().next());oldest.audio.stop(mc); }
                CASTS.put(id,new Cast(id,caster,dim,pos,yaw,begin,radius));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CelestialFall.ATOMIC_STOP_PACKET,(mc,h,b,r)->{
            UUID id=b.readUuid();mc.execute(()->{var removed=CASTS.remove(id);if(removed!=null)removed.audio.stop(mc);});
        });
        ClientTickEvents.END_CLIENT_TICK.register(AtomicClient::tick);
        WorldRenderEvents.LAST.register(AtomicWorld::render);
        HudRenderCallback.EVENT.register(AtomicHud::render);
    }
    public static Cast nearest() {
        var mc=MinecraftClient.getInstance();if(!MapClient.enabled()||mc.world==null || mc.player==null)return null;
        return CASTS.values().stream().filter(s->s.dimension.equals(mc.world.getRegistryKey().getValue()))
            .min(Comparator.comparingDouble(s->mc.player.squaredDistanceTo(s.origin))).orElse(null);
    }
    /** G never takes the camera. */
    public static Cast cinematic(){return null;}
    public static Cast actor(UUID caster){return null;}
    /** The caster's body plays the G clip (real skin, armour and sword) until it settles back to idle. */
    public static Cast poseCast(UUID caster){
        if(!MapClient.enabled())return null;
        for(var cast:CASTS.values())if(cast.caster.equals(caster)&&cast.age(0)<AtomicPose.END*20)return cast;
        return null;
    }
    public static boolean locked(UUID caster){return MapClient.enabled()&&CASTS.values().stream().anyMatch(c->c.caster.equals(caster)&&AtomicTimeline.locked(c.age(0)));}
    /** A G column holds this player (the server's 75 % movement modifier has arrived). */
    public static boolean held(net.minecraft.entity.player.PlayerEntity p){
        var speed=p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        return speed!=null&&speed.getModifier(AtomicTimeline.HIT_SLOW_ID)!=null;
    }
    public static void skip() {}
    public static float blend(float delta) { return 0; }
    public static float roll(float delta) { return 0; }
    public static double fov(double vanilla,float delta) { return vanilla; }
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta) { return null; }
    private static void tick(MinecraftClient mc) {
        if(!MapClient.enabled()||mc.world==null || mc.player==null) { clear(mc);return; }
        if(mc.isPaused())return;
        CASTS.values().removeIf(s->{
            boolean expired=!s.dimension.equals(mc.world.getRegistryKey().getValue()) || s.age(0)>=AtomicTimeline.END || mc.player.squaredDistanceTo(s.origin)>1700*1700;
            if(expired)s.audio.stop(mc);return expired;
        });
        for(Cast s:CASTS.values()) {
            int age=(int)s.age(0);
            float volume=(float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-mc.player.getPos().distanceTo(s.origin)/1600));
            boolean started=s.audio.tick(mc,age,volume);
            if(started && age<=AtomicTimeline.AUDIO_START+2)mc.getMusicTracker().stop();
        }
    }
    public static void clear(MinecraftClient mc) {
        for(var s:CASTS.values())s.audio.stop(mc);
        CASTS.clear();
    }
}
