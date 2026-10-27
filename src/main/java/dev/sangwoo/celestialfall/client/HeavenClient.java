package dev.sangwoo.celestialfall.client;

import java.util.*;
import dev.sangwoo.celestialfall.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;

public final class HeavenClient {
    public static final Map<UUID,Cast> CASTS=new LinkedHashMap<>();
    private static UUID cinematicId;
    public static final class Cast {
        public final UUID id,caster;
        public final Identifier dimension;
        public final Vec3d origin,target;
        public final float yaw;
        public final long start;
        public final ClientSpell fx;
        int lastAge;
        final CinematicSoundSequence audio=new CinematicSoundSequence(CinematicAudioPlan.HEAVEN);
        Cast(UUID id,UUID caster,Identifier dimension,Vec3d origin,Vec3d target,float yaw,long start,int age) {
            this.id=id;this.caster=caster;this.dimension=dimension;this.origin=origin;this.target=target;this.yaw=yaw;this.start=start;
            lastAge=age-1;
            fx=new ClientSpell(id,dimension,target,start+HeavenTimeline.IMPACT-SpellTimeline.IMPACT,yaw,caster,age+SpellTimeline.IMPACT-HeavenTimeline.IMPACT);
        }
        public float age(float delta) { var w=MinecraftClient.getInstance().world;return w==null?HeavenTimeline.END:Math.max(0,w.getTime()-start+delta); }
    }
    private HeavenClient() {}
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(CelestialFall.HEAVEN_PACKET,(mc,h,b,r)->{
            UUID id=b.readUuid(),caster=b.readUuid();Identifier dim=b.readIdentifier();
            Vec3d pos=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
            Vec3d target=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();long start=b.readLong();
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null || mc.player==null || !mc.world.getRegistryKey().getValue().equals(dim))return;
                int age=(int)Math.max(0,mc.world.getTime()-start);
                if(age>=HeavenTimeline.END || CASTS.containsKey(id))return;
                if(CASTS.size()>=4) { var oldest=CASTS.remove(CASTS.keySet().iterator().next());oldest.audio.stop(mc); }
                Cast s=new Cast(id,caster,dim,pos,target,yaw,start,age);CASTS.put(id,s);
                if(age<12 && caster.equals(mc.player.getUuid()) && CelestialFall.CONFIG.cinematicCamera)cinematicId=id;
                if(age>=HeavenTimeline.IMPACT)ImpactEffects.explode(mc,s.fx);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CelestialFall.HEAVEN_STOP_PACKET,(mc,h,b,r)->{
            UUID id=b.readUuid();mc.execute(()->{var removed=CASTS.remove(id);if(removed!=null)removed.audio.stop(mc);if(id.equals(cinematicId))cinematicId=null;});
        });
        ClientTickEvents.END_CLIENT_TICK.register(HeavenClient::tick);
        WorldRenderEvents.LAST.register(HeavenRenderer::render);
    }
    public static Cast nearest() {
        var mc=MinecraftClient.getInstance();if(!MapClient.enabled()||mc.world==null || mc.player==null)return null;
        return CASTS.values().stream().filter(s->s.dimension.equals(mc.world.getRegistryKey().getValue()))
            .min(Comparator.comparingDouble(s->mc.player.squaredDistanceTo(s.target))).orElse(null);
    }
    public static Cast cinematic() {
        var mc=MinecraftClient.getInstance();Cast s=CASTS.get(cinematicId);
        return MapClient.enabled() && s!=null && mc.world!=null && s.dimension.equals(mc.world.getRegistryKey().getValue()) && mc.player!=null && mc.player.isAlive() && mc.getCameraEntity()==mc.player
            && CelestialFall.CONFIG.cinematicCamera && s.age(0)<HeavenTimeline.CAMERA_END?s:null;
    }
    public static Cast actor(UUID caster) {
        if(!MapClient.enabled())return null;
        for(Cast s:CASTS.values())if(s.caster.equals(caster) && HeavenTimeline.posed(s.age(0)))return s;
        return null;
    }
    public static void skip() { cinematicId=null; }
    public static boolean wideCinematic() {
        Cast s=cinematic();return s!=null && s.age(0)>=HeavenTimeline.DESCENT;
    }
    public static float blend(float delta) { Cast s=cinematic();return s==null?0:HeavenTimeline.blend(s.age(delta)); }
    public static float roll(float delta) { Cast s=cinematic();return s==null?0:(float)HeavenScene.camera(s.origin,s.target,s.yaw,s.age(delta),HeavenActor.fingerTip(s.origin,s.yaw,s.age(delta))).roll()*blend(delta); }
    public static double fov(double vanilla,float delta) {
        Cast s=cinematic();return s==null?vanilla:vanilla+(HeavenTimeline.shot(s.age(delta)).fov()-vanilla)*blend(delta);
    }
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta) {
        var mc=MinecraftClient.getInstance();Cast s=cinematic();if(s==null)return null;
        float age=s.age(delta),blend=blend(delta);if(blend<=0)return null;
        var frame=HeavenScene.camera(s.origin,s.target,s.yaw,age,HeavenActor.fingerTip(s.origin,s.yaw,age));
        Vec3d desired=frame.position();
        // Keep the two intimate shots clear of terrain. The low reveal is staged at the caster's feet.
        if(HeavenTimeline.shotIndex(age)<2) {
            Vec3d from=s.origin.add(0,1.35,0);
            var hit=mc.world.raycast(new RaycastContext(from,desired,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS)desired=hit.getPos().add(from.subtract(desired).normalize().multiply(.15));
        }
        Vec3d pos=vanilla.lerp(desired,blend),focus=frame.focus();
        Vec3d dir=Vec3d.fromPolar(pitch,yaw).lerp(focus.subtract(pos).normalize(),blend).normalize();
        float shake=HeavenTimeline.shake(age)*(float)CelestialFall.CONFIG.screenShakeStrength*blend;
        return new CinematicDirector.Pose(pos,(float)Math.toDegrees(Math.atan2(-dir.x,dir.z))+(float)Math.sin(age*2.2)*shake*2,
            (float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,dir.y))))+(float)Math.cos(age*2.8)*shake*1.5f,roll(delta));
    }
    private static void tick(MinecraftClient mc) {
        if(!MapClient.enabled()||mc.world==null || mc.player==null) { clear(mc);return; }
        if(mc.isPaused())return;
        CASTS.values().removeIf(s->{
            boolean expired=!s.dimension.equals(mc.world.getRegistryKey().getValue()) || s.age(0)>=HeavenTimeline.END || mc.player.squaredDistanceTo(s.target)>1200*1200;
            if(expired)s.audio.stop(mc);return expired;
        });
        if(cinematic()==null)cinematicId=null;
        for(Cast s:CASTS.values()) {
            int age=(int)s.age(0);
            if(age>=1 && age<HeavenTimeline.IMPACT)ImpactEffects.prepare(mc,s.fx);
            float volume=(float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-mc.player.getPos().distanceTo(s.target)/1100));
            s.audio.tick(mc,age,volume);
            if(s.lastAge<HeavenTimeline.IMPACT && age>=HeavenTimeline.IMPACT) {
                ImpactEffects.explode(mc,s.fx);
            }
            for(int stage=1;stage<4;stage++)if(s.lastAge<HeavenImpact.burstAge(stage) && age>=HeavenImpact.burstAge(stage))
                ImpactEffects.heavenPressureBurst(mc,s.fx,stage);
            s.lastAge=age;
        }
    }
    public static void clear(MinecraftClient mc) {
        for(var s:CASTS.values())s.audio.stop(mc);
        CASTS.clear();cinematicId=null;
    }
}
