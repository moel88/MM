package dev.sangwoo.celestialfall.combat;

import dev.sangwoo.celestialfall.AtomicTimeline;
import static dev.sangwoo.celestialfall.combat.MotionPose.*;

/** Authored draw/raise/chant/brace/slash/recoil/recovery on Botand's shared joint format. */
public final class AtomicMotion {
    private static final float[] TIMES={0,5,12,88,100,110,118,128,136,144};
    private static final MotionPose[] KEYS={
        key(2,-12,-25,55,-8,-10,-8,-22,0,0,0),
        key(1,-19,-88,20,-42,-30,-15,-66,.18f,0,0),
        // Both hands gather near the forehead; the right wrist keeps the blade vertical.
        key(-1,-6,-104,-12,-72,-105,-18,-64,.12f,0,0),
        key(-1,-6,-104,-12,-72,-105,-18,-64,.12f,0,0),
        key(-5,-18,-112,-14,-66,-108,-20,-63,.2f,-.15f,.1f),
        key(10,25,-28,62,-12,-30,-42,-42,.3f,.3f,-.28f),
        key(7,30,-14,65,-14,-23,-47,-30,.22f,.3f,-.2f),
        key(3,17,-22,58,-12,-12,-24,-20,.1f,.15f,-.1f),
        key(1,6,-25,55,-8,-8,-9,-16,0,0,0),
        key(0,0,-25,55,-8,-5,-5,-8,0,0,0)
    };
    private AtomicMotion() {}
    private static MotionPose key(float pitch,float yaw,float right,float rightRoll,float elbow,
                                  float left,float leftRoll,float leftElbow,float bob,float shift,float lean) {
        MotionPose p=new MotionPose();p.rot(BODY,pitch,yaw,0).rot(HEAD,-pitch*.5f,-yaw*.65f,0);
        p.rot(RIGHT_ARM,right,0,rightRoll).rot(LEFT_ARM,left,0,leftRoll);
        p.elbow[0]=elbow;p.elbow[1]=leftElbow;p.bob=bob;p.shift=shift;p.lean=lean;
        return p;
    }
    public static MotionPose sample(float age) {
        float a=Float.isFinite(age)?Math.max(0,age):AtomicTimeline.RECOVER;
        int key=0;while(key<TIMES.length-2 && a>=TIMES[key+1])key++;
        float w=AtomicTimeline.ease(a,TIMES[key],TIMES[key+1]);
        MotionPose p=MotionPose.blend(KEYS[key],KEYS[key+1],w);
        float hold=AtomicTimeline.ease(a,12,20)*(1-AtomicTimeline.ease(a,88,100));
        p.joint[BODY][0]+=(float)Math.sin(a*.09)*.28f*hold;
        p.joint[LEFT_ARM][0]+=(float)Math.sin(a*.09+.5)*.6f*hold;
        // Feet are fixed in world space while the waist and shoulders transfer weight.
        for(int i=0;i<2;i++) {
            float x=(i==0?-.25f:.25f)-p.shift,y=12-p.bob,z=(i==0?1.1f:-1.1f)-p.lean;
            float d=Math.min(11.99f,(float)Math.sqrt(x*x+y*y+z*z));
            float bend=(float)Math.acos(Math.max(0,d/12));
            p.joint[RIGHT_LEG+i][0]=(float)Math.toDegrees(Math.atan2(z,Math.hypot(x,y))-bend);
            p.joint[RIGHT_LEG+i][2]=(float)Math.toDegrees(-Math.atan2(x,y));p.knee[i]=(float)Math.toDegrees(2*bend);
        }
        return p;
    }
    public static float capeSpread(float age) {
        float release=AtomicTimeline.ease(age,100,113)*(1-AtomicTimeline.ease(age,118,144));
        return 8+20*release;
    }
    public static float wrist(float age){return -90*AtomicTimeline.ease(age,0,5)*(1-AtomicTimeline.ease(age,128,144));}
}
