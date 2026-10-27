package dev.sangwoo.celestialfall.combat;

/** Rotations in degrees, translations in model pixels. All sampling is frame-rate independent. */
public final class MotionPose {
    public static final int HEAD=0,BODY=1,RIGHT_ARM=2,LEFT_ARM=3,RIGHT_LEG=4,LEFT_LEG=5;
    public final float[][] joint=new float[6][3];
    public final float[] elbow=new float[2],knee=new float[2];
    public float bob,shift,lean;
    public MotionPose rot(int part,float pitch,float yaw,float roll) {joint[part]=new float[]{pitch,yaw,roll};return this;}
    public MotionPose copy() {return blend(this,this,0);}
    public static MotionPose blend(MotionPose a,MotionPose b,float w) {
        w=Math.max(0,Math.min(1,w));MotionPose p=new MotionPose();
        for(int i=0;i<6;i++)for(int j=0;j<3;j++)p.joint[i][j]=lerp(a.joint[i][j],b.joint[i][j],w);
        for(int i=0;i<2;i++){p.elbow[i]=lerp(a.elbow[i],b.elbow[i],w);p.knee[i]=lerp(a.knee[i],b.knee[i],w);}
        p.bob=lerp(a.bob,b.bob,w);p.shift=lerp(a.shift,b.shift,w);p.lean=lerp(a.lean,b.lean,w);return p;
    }
    public static float lerp(float a,float b,float t) {return a+(b-a)*t;}
}
