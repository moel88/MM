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
            if(t<=0)return a;if(t>=1)return b;
            double d=Math.max(-1,Math.min(1,a.dot(b)));
            if(d>.9995)return new Dir(a.rt+(b.rt-a.rt)*t,a.up+(b.up-a.up)*t,a.fw+(b.fw-a.fw)*t).norm();
            if(d<-.9995){
                Dir basis=Math.abs(a.up)<.9?new Dir(0,1,0):new Dir(1,0,0);
                double projection=a.dot(basis);Dir side=new Dir(basis.rt-a.rt*projection,basis.up-a.up*projection,basis.fw-a.fw*projection).norm();
                double c=Math.cos(Math.PI*t),s=Math.sin(Math.PI*t);
                return new Dir(a.rt*c+side.rt*s,a.up*c+side.up*s,a.fw*c+side.fw*s).norm();
            }
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
        /** In-between of two authored poses (for building keys; clips interpolate solved joints instead). */
        public static Pose lerp(Pose a,Pose b,float t){
            Pose p=new Pose();
            p.fw=a.fw+(b.fw-a.fw)*t;p.up=a.up+(b.up-a.up)*t;p.rt=a.rt+(b.rt-a.rt)*t;
            p.yaw=a.yaw+(b.yaw-a.yaw)*t;p.pitch=a.pitch+(b.pitch-a.pitch)*t;p.roll=a.roll+(b.roll-a.roll)*t;
            p.headYaw=a.headYaw+(b.headYaw-a.headYaw)*t;p.headPitch=a.headPitch+(b.headPitch-a.headPitch)*t;p.headRoll=a.headRoll+(b.headRoll-a.headRoll)*t;
            p.rArm=Dir.lerp(a.rArm,b.rArm,t);p.lArm=Dir.lerp(a.lArm,b.lArm,t);p.rLeg=Dir.lerp(a.rLeg,b.rLeg,t);p.lLeg=Dir.lerp(a.lLeg,b.lLeg,t);p.blade=Dir.lerp(a.blade,b.blade,t);
            return p;
        }
    }
    public static Pose pose(){return new Pose();}
    /** Body-space direction of the world direction (elevation, azimuth toward the right of the facing) for a torso turned as in p. */
    public static Dir local(Pose p,double elevation,double azimuth){
        float k=(float)Math.PI/180;
        // The frame of KagenoClips.blade with the facing along +z: the character's left is +x.
        var q=new Quaternionf().rotationAxis(p.yaw*k,0,1,0).rotateAxis(p.pitch*k,1,0,0).rotateAxis(p.roll*k,0,0,1);
        double e=Math.toRadians(elevation),a=Math.toRadians(azimuth);
        var w=new Vector3f((float)(-Math.cos(e)*Math.sin(a)),(float)Math.sin(e),(float)(Math.cos(e)*Math.cos(a)));
        q.conjugate().transform(w);
        return new Dir(-w.x,w.y,w.z).norm();
    }
    /**
     * A pose written in the world: the root at (fw, up), the torso turned yaw degrees to the left, leaning pitch forward
     * and rolled; the limbs and the blade along world directions ({elevation, azimuth} toward the right of the facing);
     * the head facing the target, level.
     */
    public static Pose world(double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double[] rLeg,double[] lLeg){
        Pose p=pose().at(fw,up,0).body(yaw,pitch,roll).head(yaw,-pitch*.8f,0);
        p.rArm=local(p,rArm[0],rArm[1]);p.blade=local(p,blade[0],blade[1]);p.lArm=local(p,lArm[0],lArm[1]);
        p.rLeg=local(p,rLeg[0],rLeg[1]);p.lLeg=local(p,lLeg[0],lLeg[1]);
        return p;
    }

    /** Solved pose ready for the Minecraft rig. */
    public record Frame(MotionPose joints,float grip,double fw,double up,double rt,float yaw,float pitch,float roll){}

    /** Blend full rotations, avoiding twist flips when a limb crosses its forward axis. */
    static Frame blend(Frame a,Frame b,float t){
        MotionPose m=new MotionPose();float k=(float)Math.PI/180;
        for(int i=0;i<6;i++){
            float[] x=a.joints.joint[i],y=b.joints.joint[i];
            var q=new Quaternionf().rotationZYX(x[2]*k,x[1]*k,x[0]*k);
            q.slerp(new Quaternionf().rotationZYX(y[2]*k,y[1]*k,y[0]*k),t).normalize();
            m.joint[i]=euler(q);
        }
        // Preserve deliberate full root turns, including M1's 360 degree spin.
        return new Frame(m,a.grip+(b.grip-a.grip)*t,
            a.fw+(b.fw-a.fw)*t,a.up+(b.up-a.up)*t,a.rt+(b.rt-a.rt)*t,
            a.yaw+(b.yaw-a.yaw)*t,a.pitch+(b.pitch-a.pitch)*t,a.roll+(b.roll-a.roll)*t);
    }

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
    /**
     * Minecraft ModelPart order is Rz·Ry·Rx: returns {pitch,yaw,roll} in degrees. Read off the rotation matrix; at
     * gimbal lock (yaw ±90°, a limb straight out to the side) roll is set to 0 and pitch takes the whole remaining
     * turn, so the angles always rebuild the same rotation.
     */
    static float[] euler(Quaternionf q){
        double w=q.w,x=q.x,y=q.y,z=q.z;
        double r00=1-2*(y*y+z*z),r01=2*(x*y-w*z),r10=2*(x*y+w*z),r11=1-2*(x*x+z*z),r20=2*(x*z-w*y),r21=2*(y*z+w*x),r22=1-2*(x*x+y*y);
        double pitch,yaw,roll;
        if(Math.abs(r20)<.99999){yaw=Math.asin(-r20);pitch=Math.atan2(r21,r22);roll=Math.atan2(r10,r00);}
        else if(r20<0){yaw=Math.PI/2;roll=0;pitch=Math.atan2(r01,r11);}
        else{yaw=-Math.PI/2;roll=0;pitch=Math.atan2(-r01,r11);}
        return new float[]{(float)Math.toDegrees(pitch),(float)Math.toDegrees(yaw),(float)Math.toDegrees(roll)};
    }
    /**
     * Front of a limb swung from hanging straight down to {@code d} without twisting about itself: forward for a
     * hanging limb, up for one raised straight ahead, unchanged for one raised out to the side. Continuous for every
     * direction but straight up (where it faces backward, as when raised over the front).
     */
    static Dir swingFront(Dir d){
        Vector3f to=new Vector3f((float)d.rt,(float)d.up,(float)d.fw).normalize();
        if(to.y>.9999f)return new Dir(0,0,-1);
        var f=new Quaternionf().rotationTo(new Vector3f(0,-1,0),to).transform(new Vector3f(0,0,1));
        return new Dir(f.x,f.y,f.z);
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
        Dir front=sideLen>1e-3?side.norm():swingFront(arm);
        float grip=(float)-Math.toDegrees(Math.atan2(blade.dot(arm),sideLen));
        m.joint[MotionPose.RIGHT_ARM]=euler(limb(arm,front));
        // The free arm and the legs swing without twisting, so they never flip when they point ahead (kicks, reaches).
        m.joint[MotionPose.LEFT_ARM]=euler(limb(p.lArm.norm(),swingFront(p.lArm.norm())));
        m.joint[MotionPose.RIGHT_LEG]=euler(limb(p.rLeg.norm(),swingFront(p.rLeg.norm())));
        m.joint[MotionPose.LEFT_LEG]=euler(limb(p.lLeg.norm(),swingFront(p.lLeg.norm())));
        return new Frame(m,grip,p.fw,p.up,p.rt,p.yaw,p.pitch,p.roll);
    }

    /** {@code f} with its legs along {@code rLeg} and {@code lLeg} (body space), swung without twisting as {@link #solve} swings them. */
    public static Frame legs(Frame f,Dir rLeg,Dir lLeg){
        MotionPose m=f.joints().copy();
        m.joint[MotionPose.RIGHT_LEG]=euler(limb(rLeg.norm(),swingFront(rLeg.norm())));
        m.joint[MotionPose.LEFT_LEG]=euler(limb(lLeg.norm(),swingFront(lLeg.norm())));
        return new Frame(m,f.grip(),f.fw(),f.up(),f.rt(),f.yaw(),f.pitch(),f.roll());
    }
    /** Where the blade points in body space for a solved frame (used by tests and effects). */
    public static Dir bladeOf(Frame f){
        float[] e=f.joints().joint[MotionPose.RIGHT_ARM];float k=(float)Math.PI/180;
        var q=new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k).rotateX(-90*k).rotateY(180*k).rotateX(f.grip()*k).rotateY(-90*k);
        var v=q.transform(new Vector3f(0,1,0));
        return new Dir(-v.x,-v.y,-v.z);
    }
    private static Dir direction(float[] e){
        float k=(float)Math.PI/180;
        var v=new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k).transform(new Vector3f(0,1,0));
        return new Dir(-v.x,-v.y,-v.z);
    }
    /** Effects use the same solved sword and arm directions as the rendered model. */
    public static Pose poseOf(Frame f){
        float[] h=f.joints.joint[MotionPose.HEAD];
        Pose p=pose().at(f.fw,f.up,f.rt).body(f.yaw,f.pitch,f.roll).head(h[1],h[0],h[2]);
        p.rArm=direction(f.joints.joint[MotionPose.RIGHT_ARM]);p.lArm=direction(f.joints.joint[MotionPose.LEFT_ARM]);
        p.rLeg=direction(f.joints.joint[MotionPose.RIGHT_LEG]);p.lLeg=direction(f.joints.joint[MotionPose.LEFT_LEG]);
        p.blade=bladeOf(f);return p;
    }
}
