package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.HeavenTimeline;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Shared world-space staging used by the real renderer and offline geometry verification. */
public final class HeavenScene {
    public record Frame(Vec3d position,Vec3d focus,double fov,double roll) {}
    private HeavenScene() {}
    /** Move on the blade's own axis. Its ground intersection never slides away from target. */
    public static Vec3d bladePoint(Vec3d target,float yaw,float age,double x,double y,double z) {
        double h=HeavenTimeline.tipHeight(age),tilt=HeavenTimeline.TILT,az=Math.toRadians(yaw);
        double tx=x*Math.cos(tilt)+y*Math.sin(tilt)+h*Math.tan(tilt);
        double ty=-x*Math.sin(tilt)+y*Math.cos(tilt)+h;
        return target.add(tx*Math.cos(az)-z*Math.sin(az),ty,tx*Math.sin(az)+z*Math.cos(az));
    }
    public static Vec3d effectFocus(Vec3d origin,Vec3d target,float yaw,float age) {
        if(age<HeavenTimeline.PORTAL)return HeavenActor.fingerTip(origin,yaw,age);
        if(age<HeavenTimeline.DESCENT)return portalCentre(target,yaw);
        return age<HeavenTimeline.IMPACT?bladePoint(target,yaw,age,0,0,0):target;
    }
    public static Vec3d forward(Vec3d origin,Vec3d target,float yaw) {
        Vec3d f=target.subtract(origin).multiply(1,0,1);
        return f.lengthSquared()>.01?f.normalize():Vec3d.fromPolar(0,yaw);
    }
    public static Vec3d portalCentre(Vec3d target,float yaw) {
        return bladePoint(target,yaw,HeavenTimeline.DESCENT,0,300,0);
    }
    public static Matrix4f cloudTransform(Vec3d origin,Vec3d target,float yaw,float age,int layer) {
        Vec3d c=portalCentre(target,yaw),normal=origin.add(0,1.5,0).subtract(c).normalize();
        Vec3d u=new Vec3d(0,1,0).crossProduct(normal).normalize();
        if(u.lengthSquared()<.1)u=new Vec3d(1,0,0);
        Vec3d v=normal.crossProduct(u).normalize();
        Matrix4f basis=new Matrix4f().m00((float)u.x).m01((float)u.y).m02((float)u.z)
            .m10((float)v.x).m11((float)v.y).m12((float)v.z)
            .m20((float)normal.x).m21((float)normal.y).m22((float)normal.z)
            .m30((float)c.x).m31((float)c.y).m32((float)c.z);
        float scale=.9f+.1f*HeavenTimeline.ease(age,HeavenTimeline.PORTAL,40);
        return basis.rotateZ((float)((age-HeavenTimeline.PORTAL)*HeavenClouds.LAYERS.get(layer).speed())).scale(scale);
    }
    public static Vec3d revealCamera(Vec3d origin,Vec3d target,float yaw) {
        Vec3d f=forward(origin,target,yaw),right=new Vec3d(f.z,0,-f.x);
        return origin.add(f.multiply(2.1)).subtract(right.multiply(1.6)).add(0,.18,0);
    }
    public static Frame camera(Vec3d origin,Vec3d target,float yaw,float age,Vec3d fingerTip) {
        int shot=HeavenTimeline.shotIndex(age);
        var timing=HeavenTimeline.shot(age);
        if(shot==0)return HeavenActor.handFrame(origin,yaw,age);
        if(shot==1) {
            Vec3d f=Vec3d.fromPolar(0,yaw);
            return new Frame(origin.add(f.multiply(1.4)).add(0,1.7,0),origin.add(0,1.7,0),timing.fov(),0);
        }
        Vec3d f=forward(origin,target,yaw),right=new Vec3d(f.z,0,-f.x);
        if(shot==2) {
            Vec3d position=revealCamera(origin,target,yaw);
            Vec3d actorDirection=origin.add(0,1.62,0).subtract(position).normalize();
            Vec3d portalDirection=portalCentre(target,yaw).subtract(position).normalize();
            Vec3d direction=actorDirection.multiply(.44).add(portalDirection.multiply(.56)).normalize();
            return new Frame(position,position.add(direction.multiply(50)),timing.fov(),timing.roll());
        }
        Vec3d position=target.subtract(f.multiply(timing.radius())).add(right.multiply(64)).add(0,timing.height(),0);
        return new Frame(position,target.add(0,timing.focusHeight(),0),timing.fov(),timing.roll());
    }
    public static float actorYaw(Vec3d origin,Vec3d target,float yaw,float age) {
        if(HeavenTimeline.shotIndex(age)!=2)return yaw;
        Vec3d d=revealCamera(origin,target,yaw).subtract(origin);
        return (float)Math.toDegrees(Math.atan2(-d.x,d.z));
    }
}
