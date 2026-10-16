package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.atomic.AtomicLook;
import dev.sangwoo.celestialfall.client.atomic.AtomicPillar;
import net.minecraft.util.math.Vec3d;

/**
 * G's screen treatment (chant haze, impact frames, blast colour grade) and its camera quake for
 * the local viewer. Additive to the player camera; never owns it or changes input.
 */
public final class AtomicImmersion {
    public record Sample(AtomicLook.Grade grade,float yaw,float pitch,float roll) {
        public boolean active() { return grade.tint()>0||grade.frame()>0||grade.mix()>0||yaw!=0||pitch!=0||roll!=0; }
        /** The same sample without the blast colour grade (haze, impact frames and quake stay). */
        public Sample withoutGrade() {
            return grade.mix()==0?this:new Sample(new AtomicLook.Grade(grade.tint(),grade.frame(),grade.morph(),0,grade.lo(),grade.hi()),yaw,pitch,roll);
        }
    }
    public static final Sample NONE=new Sample(AtomicLook.Grade.NONE,0,0,0);
    private AtomicImmersion(){}
    /** Full strength within 1.6 blast radii of the cast, gone at 4 radii. */
    public static float reach(double distance,double radius) {
        return (float)Math.max(0,Math.min(1,(radius*4-distance)/(radius*2.4)));
    }
    public static Sample sample(float sec,double distance,double radius,double shakeStrength) {
        float reach=reach(distance,radius);if(reach<=0||!(sec>=0))return NONE;
        var g=AtomicLook.grade(sec,(long)Math.floor(sec*50));
        // Impact frames are all-or-nothing; the haze and grade thin out with distance.
        var grade=new AtomicLook.Grade(g.tint()*reach,reach>=.5f?g.frame():0,g.morph(),g.mix()*reach,g.lo(),g.hi());
        float amp=AtomicLook.shake(sec)*reach*(float)Math.max(0,Math.min(2,shakeStrength));
        if(amp<=0)return new Sample(grade,0,0,0);
        long step=(long)Math.floor(sec*45);
        return new Sample(grade,amp*AtomicLook.jitter(step,0),amp*AtomicLook.jitter(step,1),amp*.3f*AtomicLook.jitter(step,2));
    }
    public static Sample at(Vec3d camera,float delta){
        Sample result=NONE;double best=Double.MAX_VALUE;
        var mc=net.minecraft.client.MinecraftClient.getInstance();
        if(mc.world==null)return NONE;
        for(var cast:AtomicClient.CASTS.values()){
            if(!cast.dimension.equals(mc.world.getRegistryKey().getValue()))continue;
            double d=Math.hypot(camera.x-cast.origin.x,camera.z-cast.origin.z);
            if(d>=best)continue;
            var value=sample(cast.age(delta)/20f,d,cast.radius,CelestialFall.CONFIG.screenShakeStrength);
            // The violet grade belongs to the hit view; outside the damage column the pillar shows its own colours.
            if(value!=NONE&&AtomicPillar.available()&&!AtomicPillar.interior(cast,camera))value=value.withoutGrade();
            if(value!=NONE){result=value;best=d;}
        }
        return result;
    }
}
