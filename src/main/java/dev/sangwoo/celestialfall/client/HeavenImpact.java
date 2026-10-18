package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.HeavenTimeline;
import net.minecraft.util.math.Vec3d;

/** Shared event clocks and ground-space emitters for the embedding sword. */
public final class HeavenImpact {
    private HeavenImpact() {}
    public static int burstAge(int stage) {
        return switch(stage) { case 0 -> HeavenTimeline.IMPACT; case 1 -> HeavenTimeline.IMPACT+24;
            case 2 -> HeavenTimeline.IMPACT+48; default -> HeavenTimeline.EMBED_END; };
    }
    public static float waveProgress(float age,int wave) {
        int start=wave==0?HeavenTimeline.IMPACT:wave==1?HeavenTimeline.IMPACT+24:HeavenTimeline.EMBED_END;
        return HeavenTimeline.ease(age,start,start+44);
    }
    public static float fade(float age) {
        return age<HeavenTimeline.IMPACT?0:HeavenTimeline.visibility(age)*(1-HeavenTimeline.ease(age,HeavenTimeline.EMBED_END+12,HeavenTimeline.EMBED_END+64));
    }
    /** Expands along the blade's widening footprint, on the contact plane rather than its buried tip. */
    public static Vec3d emitter(Vec3d target,float yaw,float age,double angle,double spread) {
        double tilt=HeavenTimeline.TILT;
        double half=HeavenTimeline.halfWidth(Math.max(0,-HeavenTimeline.tipHeight(age)/Math.cos(tilt)))/Math.cos(tilt);
        double x=Math.cos(angle)*(half+6+spread),z=Math.sin(angle)*(16+spread),az=Math.toRadians(yaw);
        return target.add(x*Math.cos(az)-z*Math.sin(az),.25,x*Math.sin(az)+z*Math.cos(az));
    }
}
