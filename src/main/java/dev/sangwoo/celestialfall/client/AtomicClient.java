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

public final class AtomicClient {
    public static final Map<UUID,Cast> CASTS=new LinkedHashMap<>();
    private static UUID cinematicId;
    public static final class Cast {
        public final UUID id,caster;
        public final Identifier dimension;
        public final Vec3d origin;
        public final float yaw;
        public final long start;
        public final double radius;
        public final ClientSpell fx;
        int lastAge;
        final CinematicSoundSequence audio=new CinematicSoundSequence(CinematicAudioPlan.ATOMIC);
        Cast(UUID id,UUID caster,Identifier dimension,Vec3d origin,float yaw,long start,double radius,int age) {
            this.id=id;this.caster=caster;this.dimension=dimension;this.origin=origin;this.yaw=yaw;this.start=start;
            this.radius=Math.max(8,Math.min(768,radius));lastAge=age-1;
            fx=new ClientSpell(id,dimension,origin,start+AtomicTimeline.IMPACT-SpellTimeline.IMPACT,yaw,caster,age+SpellTimeline.IMPACT-AtomicTimeline.IMPACT);
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
                if(CASTS.size()>=4) { var oldest=CASTS.remove(CASTS.keySet().iterator().next());oldest.audio.stop(mc); }
                Cast s=new Cast(id,caster,dim,pos,yaw,start,radius,age);CASTS.put(id,s);
                // G never takes camera ownership.

            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CelestialFall.ATOMIC_STOP_PACKET,(mc,h,b,r)->{
            UUID id=b.readUuid();mc.execute(()->{var removed=CASTS.remove(id);if(removed!=null)removed.audio.stop(mc);if(id.equals(cinematicId))cinematicId=null;});
        });
        ClientTickEvents.END_CLIENT_TICK.register(AtomicClient::tick);
        WorldRenderEvents.LAST.register(AtomicRenderer::render);
        HudRenderCallback.EVENT.register(AtomicHud::render);
    }
    public static Cast nearest() {
        var mc=MinecraftClient.getInstance();if(!MapClient.enabled()||mc.world==null || mc.player==null)return null;
        return CASTS.values().stream().filter(s->s.dimension.equals(mc.world.getRegistryKey().getValue()))
            .min(Comparator.comparingDouble(s->mc.player.squaredDistanceTo(s.origin))).orElse(null);
    }
    public static Cast cinematic(){return null;}
    public static Cast actor(UUID caster){return null;}
    /** Body animation is independent of camera ownership; G keeps the player's real skin and armor. */
    public static Cast poseCast(UUID caster){
        if(!MapClient.enabled())return null;
        for(var cast:CASTS.values())if(cast.caster.equals(caster)&&cast.age(0)<AtomicTimeline.RECOVER)return cast;
        return null;
    }
    public static boolean locked(UUID caster){return MapClient.enabled()&&CASTS.values().stream().anyMatch(c->c.caster.equals(caster)&&c.age(0)<AtomicTimeline.END);}
    public static void skip() { cinematicId=null; }
    public static float blend(float delta) { Cast s=cinematic();return s==null?0:AtomicTimeline.blend(s.age(delta)); }
    public static float roll(float delta) { Cast s=cinematic();return s==null?0:(float)AtomicTimeline.shot(s.age(delta),s.radius).roll()*blend(delta); }
    public static double fov(double vanilla,float delta) {
        Cast s=cinematic();return s==null?vanilla:vanilla+(AtomicTimeline.shot(s.age(delta),s.radius).fov()-vanilla)*blend(delta);
    }
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta) {
        var mc=MinecraftClient.getInstance();Cast s=cinematic();if(s==null)return null;
        float age=s.age(delta),blend=blend(delta);if(blend<=0)return null;
        var shot=AtomicTimeline.shot(age,s.radius);
        double angle=Math.toRadians(s.yaw+90+shot.azimuth());
        Vec3d desired=s.origin.add(Math.cos(angle)*shot.radius(),shot.height(),Math.sin(angle)*shot.radius());
        // Only near shots need wall avoidance; aerial effect geometry does not require remote chunks.
        if(shot.radius()<16) {
            Vec3d from=s.origin.add(0,1.35,0);
            var hit=mc.world.raycast(new RaycastContext(from,desired,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS)desired=hit.getPos().add(from.subtract(desired).normalize().multiply(.3));
        }
        Vec3d pos=vanilla.lerp(desired,blend),focus=s.origin.add(0,shot.focusHeight(),0);
        Vec3d dir=Vec3d.fromPolar(pitch,yaw).lerp(focus.subtract(pos).normalize(),blend).normalize();
        float shake=AtomicTimeline.shake(age)*(float)CelestialFall.CONFIG.screenShakeStrength*blend;
        return new CinematicDirector.Pose(pos,(float)Math.toDegrees(Math.atan2(-dir.x,dir.z))+(float)Math.sin(age*2.2)*shake*2,
            (float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,dir.y))))+(float)Math.cos(age*2.8)*shake*1.5f,roll(delta));
    }
    private static void tick(MinecraftClient mc) {
        if(!MapClient.enabled()||mc.world==null || mc.player==null) { clear(mc);return; }
        if(mc.isPaused())return;
        CASTS.values().removeIf(s->{
            boolean expired=!s.dimension.equals(mc.world.getRegistryKey().getValue()) || s.age(0)>=AtomicTimeline.END || mc.player.squaredDistanceTo(s.origin)>1700*1700;
            if(expired)s.audio.stop(mc);return expired;
        });
        if(cinematic()==null)cinematicId=null;
        for(Cast s:CASTS.values()) {
            int age=(int)s.age(0);

            float volume=(float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-mc.player.getPos().distanceTo(s.origin)/1600));
            boolean started=s.audio.tick(mc,age,volume);
            if(started && age<=2)mc.getMusicTracker().stop();

            s.lastAge=age;
        }
    }
    public static void clear(MinecraftClient mc) {
        for(var s:CASTS.values())s.audio.stop(mc);
        CASTS.clear();cinematicId=null;
    }
}
