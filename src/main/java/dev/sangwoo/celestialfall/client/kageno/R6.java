package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.combat.MotionPose;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Roblox R6 style pose kit. The reference avatar has one-piece limbs, so every limb is a
 * single rigid block: no elbow/knee bends. The torso (RootJoint) orientation is applied to
 * the whole model by the renderer; limbs and head are relative to the torso.
 *
 * Authoring space ("body space"): forward = where the character faces, up, right = the
 * character's own right hand side. Directions are written as elevation/azimuth:
 * elevation is degrees above the horizontal plane (-90 = straight down), azimuth is degrees
 * from forward toward the character's right (90 = out to the right, -90 = out to the left,
 * 180 = behind).
 */
public final class R6 {
    private R6(){}

    /** Unit direction in body space (right, up, forward). */
    public record Dir(double rt,double up,double fw){
        public Dir norm(){double l=Math.sqrt(rt*rt+up*up+fw*fw);return l<1e-9?new Dir(0,-1,0):new Dir(rt/l,up/l,fw/l);}
        public double dot(Dir o){return rt*o.rt+up*o.up+fw*o.fw;}
        static Dir lerp(Dir a,Dir b,double t){
            double d=Math.max(-1,Math.min(1,a.dot(b)));
            if(d>.9995)return new Dir(a.rt+(b.rt-a.rt)*t,a.up+(b.up-a.up)*t,a.fw+(b.fw-a.fw)*t).norm();
            double th=Math.acos(d),s=Math.sin(th),wa=Math.sin((1-t)*th)/s,wb=Math.sin(t*th)/s;
            return new Dir(a.rt*wa+b.rt*wb,a.up*wa+b.up*wb,a.fw*wa+b.fw*wb).norm();
        }
    }
    public static Dir dir(double elevation,double azimuth){
        double e=Math.toRadians(elevation),a=Math.toRadians(azimuth);
        return new Dir(Math.cos(e)*Math.sin(a),Math.sin(e),Math.cos(e)*Math.cos(a));
    }
    public static final Dir DOWN=dir(-90,0),FORWARD=dir(0,0),UP=dir(90,0);

    /** One authored keyframe. Root offsets are in blocks, angles in degrees. */
    public static final class Pose {
        public double fw,up,rt;
        public float yaw,pitch,roll;
        public float headYaw,headPitch,headRoll;
        public Dir rArm=dir(-86,8),lArm=dir(-86,-8),rLeg=dir(-90,3),lLeg=dir(-90,-3);
        public Dir blade=dir(-40,-35);
        public Pose copy(){Pose p=new Pose();p.fw=fw;p.up=up;p.rt=rt;p.yaw=yaw;p.pitch=pitch;p.roll=roll;p.headYaw=headYaw;p.headPitch=headPitch;p.headRoll=headRoll;p.rArm=rArm;p.lArm=lArm;p.rLeg=rLeg;p.lLeg=lLeg;p.blade=blade;return p;}
        /** Root translation relative to the cast facing: forward, up, right (blocks). */
        public Pose at(double fw,double up,double rt){Pose p=copy();p.fw=fw;p.up=up;p.rt=rt;return p;}
        /** Torso orientation: yaw turns to the character's left, pitch leans forward, roll tilts to the right. */
        public Pose body(float yaw,float pitch,float roll){Pose p=copy();p.yaw=yaw;p.pitch=pitch;p.roll=roll;return p;}
        /** Head relative to torso: yaw turns right, pitch looks down. */
        public Pose head(float yaw,float pitch,float roll){Pose p=copy();p.headYaw=yaw;p.headPitch=pitch;p.headRoll=roll;return p;}
        public Pose rArm(double elev,double az){Pose p=copy();p.rArm=dir(elev,az);return p;}
        public Pose lArm(double elev,double az){Pose p=copy();p.lArm=dir(elev,az);return p;}
        public Pose rLeg(double elev,double az){Pose p=copy();p.rLeg=dir(elev,az);return p;}
        public Pose lLeg(double elev,double az){Pose p=copy();p.lLeg=dir(elev,az);return p;}
        /** Direction the katana blade points, in body space. */
        public Pose blade(double elev,double az){Pose p=copy();p.blade=dir(elev,az);return p;}
        public static Pose lerp(Pose a,Pose b,float t){
            Pose p=new Pose();
            p.fw=a.fw+(b.fw-a.fw)*t;p.up=a.up+(b.up-a.up)*t;p.rt=a.rt+(b.rt-a.rt)*t;
            p.yaw=a.yaw+(b.yaw-a.yaw)*t;p.pitch=a.pitch+(b.pitch-a.pitch)*t;p.roll=a.roll+(b.roll-a.roll)*t;
            p.headYaw=a.headYaw+(b.headYaw-a.headYaw)*t;p.headPitch=a.headPitch+(b.headPitch-a.headPitch)*t;p.headRoll=a.headRoll+(b.headRoll-a.headRoll)*t;
            p.rArm=Dir.lerp(a.rArm,b.rArm,t);p.lArm=Dir.lerp(a.lArm,b.lArm,t);p.rLeg=Dir.lerp(a.rLeg,b.rLeg,t);p.lLeg=Dir.lerp(a.lLeg,b.lLeg,t);
            p.blade=Dir.lerp(a.blade,b.blade,t);
            return p;
        }
    }
    public static Pose pose(){return new Pose();}

    /** Solved pose ready for the Minecraft rig. */
    public record Frame(MotionPose joints,float grip,double fw,double up,double rt,float yaw,float pitch,float roll){}

    /** Body space → Minecraft model space (model x = character's left, y = down, z = backwards). */
    private static Vector3f model(Dir d){return new Vector3f((float)-d.rt,(float)-d.up,(float)-d.fw);}

    /** Rotation taking the limb rest axis (model +Y) to {@code d} with the limb front (model -Z) toward {@code front}. */
    private static Quaternionf limb(Dir d,Dir front){
        Vector3f y=model(d).normalize();
        Vector3f f=model(front);f.sub(new Vector3f(y).mul(f.dot(y)));
        if(f.lengthSquared()<1e-6){f=model(FORWARD);f.sub(new Vector3f(y).mul(f.dot(y)));if(f.lengthSquared()<1e-6){f=model(UP);f.sub(new Vector3f(y).mul(f.dot(y)));}}
        f.normalize();
        Vector3f z=new Vector3f(f).negate();
        Vector3f x=new Vector3f(y).cross(z).normalize();
        Matrix3f m=new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,z);
        return new Quaternionf().setFromNormalized(m);
    }
    /** Minecraft ModelPart order is Rz·Ry·Rx: returns {pitch,yaw,roll} in degrees. */
    static float[] euler(Quaternionf q){
        double pitch=Math.atan2(2*(q.w*q.x+q.y*q.z),1-2*(q.x*q.x+q.y*q.y));
        double yaw=Math.asin(Math.max(-1,Math.min(1,2*(q.w*q.y-q.z*q.x))));
        double roll=Math.atan2(2*(q.w*q.z+q.x*q.y),1-2*(q.y*q.y+q.z*q.z));
        return new float[]{(float)Math.toDegrees(pitch),(float)Math.toDegrees(yaw),(float)Math.toDegrees(roll)};
    }
    private static Dir perpendicular(Dir v,Dir axis){double d=v.dot(axis);return new Dir(v.rt-axis.rt*d,v.up-axis.up*d,v.fw-axis.fw*d);}

    public static Frame solve(Pose p){
        MotionPose m=new MotionPose();
        m.rot(MotionPose.HEAD,p.headPitch,p.headYaw,p.headRoll);
        m.rot(MotionPose.BODY,0,0,0);
        // The sword arm is twisted so the blade's sideways component leaves the palm;
        // the remaining along-arm component becomes the wrist (grip) angle.
        Dir arm=p.rArm.norm(),blade=p.blade.norm();
        Dir side=perpendicular(blade,arm);
        double sideLen=Math.sqrt(side.dot(side));
        Dir front=sideLen>1e-3?side.norm():perpendicular(FORWARD,arm);
        float grip=(float)-Math.toDegrees(Math.atan2(blade.dot(arm),sideLen));
        m.joint[MotionPose.RIGHT_ARM]=euler(limb(arm,front));
        m.joint[MotionPose.LEFT_ARM]=euler(limb(p.lArm.norm(),FORWARD));
        m.joint[MotionPose.RIGHT_LEG]=euler(limb(p.rLeg.norm(),FORWARD));
        m.joint[MotionPose.LEFT_LEG]=euler(limb(p.lLeg.norm(),FORWARD));
        return new Frame(m,grip,p.fw,p.up,p.rt,p.yaw,p.pitch,p.roll);
    }

    /** Where the blade points in body space for a solved frame (used by tests and effects). */
    public static Dir bladeOf(Frame f){
        float[] e=f.joints().joint[MotionPose.RIGHT_ARM];float k=(float)Math.PI/180;
        var q=new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k).rotateX(-90*k).rotateY(180*k).rotateX(f.grip()*k).rotateY(-90*k);
        var v=q.transform(new Vector3f(0,1,0));
        return new Dir(-v.x,-v.y,-v.z);
    }
}
