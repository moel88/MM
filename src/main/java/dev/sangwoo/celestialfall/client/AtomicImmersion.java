package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.AtomicTimeline;
import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.util.math.Vec3d;

/** Exposure and additive shake inside G. Never owns the camera or changes input. */
public final class AtomicImmersion {
    public record Sample(float violet,float flash,float yaw,float pitch,float roll) {}
    public static final Sample NONE=new Sample(0,0,0,0,0);
    private AtomicImmersion(){}
    public static Sample sample(float age,double dx,double dy,double dz,double baseRadius,double strength){
        double radius=AtomicTimeline.blastRadius(age,baseRadius);
        if(!AtomicTimeline.insideColumn(dx,dy,dz,radius,AtomicTimeline.columnHeight(baseRadius)))return NONE;
        double distance=Math.sqrt(dx*dx+dz*dz);
        float edge=(float)Math.min(1,Math.max(0,(radius-distance)/Math.max(.15,radius*.08)));
        float exposure=AtomicTimeline.ease(age,AtomicTimeline.IMPACT,AtomicTimeline.IMPACT+3)*(1-AtomicTimeline.ease(age,460,468))*edge;
        float flash=(1-AtomicTimeline.ease(age,AtomicTimeline.IMPACT,AtomicTimeline.IMPACT+7))*edge;
        // A low-frequency quake carries the scene; faster tremors roughen each shock.
        float shake=(float)(exposure*(2.35+AtomicTimeline.pulse(age)*1.45)*Math.max(0,Math.min(2,strength)));
        return new Sample(exposure,flash,
            shake*(float)(.76*Math.sin(age*.67)+.24*Math.sin(age*2.43)+.12*Math.sin(age*4.17)),
            shake*(float)(.66*Math.cos(age*.81)+.23*Math.cos(age*2.13)+.10*Math.sin(age*3.61)),
            shake*.40f*(float)(Math.sin(age*.59)+.18*Math.sin(age*1.77)));
    }
    public static Sample at(Vec3d camera,float delta){
        Sample result=NONE;
        for(var cast:AtomicClient.CASTS.values()){
            Vec3d offset=camera.subtract(cast.origin);
            var value=sample(cast.age(delta),offset.x,offset.y,offset.z,cast.radius,CelestialFall.CONFIG.screenShakeStrength);
            if(value.violet()+value.flash()>result.violet()+result.flash())result=value;
        }
        return result;
    }
}
