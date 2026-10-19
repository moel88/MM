package dev.sangwoo.celestialfall.client.gacha;

/**
 * Bomi's rig from the title intro (cinema/scene.json): the native slim player's seven rigid parts, each turned about its
 * pivot (degrees about the fixed x, then y, then z axes) under its parent, exactly as tools/title_cinema/build_animation.py
 * poses her for performance.anim. Metres, +Y up, she faces +Z.
 */
final class BomiRig {
    private BomiRig(){}
    static final int ROOT=0,BODY=1,HEAD=2,RIGHT_ARM=3,LEFT_ARM=4,RIGHT_LEG=5,LEFT_LEG=6,BONES=7;
    static final int[] PARENT={-1,ROOT,BODY,BODY,BODY,ROOT,ROOT};
    /** Pivots in sixteenths of a metre (scene.json bones). */
    static final float[][] PIVOT={{0,0,0},{0,22.5f,0},{0,22.5f,0},{-4.6875f,20.15625f,0},{4.6875f,20.15625f,0},{-1.78125f,11.25f,0},{1.78125f,11.25f,0}};
    /** The intro's carry: straight arms holding the hourglass in front of her chest (performance.json carry_pose). */
    static final float[] CARRY_RIGHT={-62.9490861f,0,37.4499940f},CARRY_LEFT={-46.9854661f,0,-24.4761667f};
    /** The hips' height over the soles when she stands (the legs' pivots). */
    static final float HIP=11.25f/16;

    /** Turns ({@code euler[bone]}, degrees) and the root's offset to the bones' world matrices, row-major 3x4 each. */
    static float[] pose(float[][] euler,float tx,float ty,float tz){
        float[] out=new float[BONES*12];double[][] world=new double[BONES][];
        for(int i=0;i<BONES;i++){
            double[] r=rotation(euler[i]);double px=PIVOT[i][0]/16,py=PIVOT[i][1]/16,pz=PIVOT[i][2]/16;
            // [R | p - R p]
            double[] m={r[0],r[1],r[2],px-(r[0]*px+r[1]*py+r[2]*pz),r[3],r[4],r[5],py-(r[3]*px+r[4]*py+r[5]*pz),r[6],r[7],r[8],pz-(r[6]*px+r[7]*py+r[8]*pz)};
            if(PARENT[i]>=0)m=mul(world[PARENT[i]],m);else{m[3]+=tx;m[7]+=ty;m[11]+=tz;}
            world[i]=m;for(int k=0;k<12;k++)out[i*12+k]=(float)m[k];
        }
        return out;
    }
    /** Rz(c) Ry(b) Rx(a), row-major 3x3. */
    static double[] rotation(float[] e){
        double a=Math.toRadians(e[0]),b=Math.toRadians(e[1]),c=Math.toRadians(e[2]);
        double ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b),cc=Math.cos(c),sc=Math.sin(c);
        return new double[]{
            cc*cb, cc*sb*sa-sc*ca, cc*sb*ca+sc*sa,
            sc*cb, sc*sb*sa+cc*ca, sc*sb*ca-cc*sa,
            -sb,   cb*sa,          cb*ca};
    }
    /** Two 3x4 affine matrices multiplied (a then applied after b). */
    static double[] mul(double[] a,double[] b){
        double[] o=new double[12];
        for(int r=0;r<3;r++){
            for(int c=0;c<4;c++){double s=a[r*4]*b[c]+a[r*4+1]*b[4+c]+a[r*4+2]*b[8+c];if(c==3)s+=a[r*4+3];o[r*4+c]=s;}
        }
        return o;
    }
    /** A point carried by bone {@code i} of {@code pose}. */
    static float[] apply(float[] pose,int i,float x,float y,float z){
        int a=i*12;return new float[]{pose[a]*x+pose[a+1]*y+pose[a+2]*z+pose[a+3],pose[a+4]*x+pose[a+5]*y+pose[a+6]*z+pose[a+7],pose[a+8]*x+pose[a+9]*y+pose[a+10]*z+pose[a+11]};
    }
    /** An hourglass copy's placement: the mesh (built around Bomi's {@code center}) scaled, turned by {@code yaw} and set at {@code at}. */
    static float[] place(float[] center,float scale,float yaw,float x,float y,float z){
        double c=Math.cos(Math.toRadians(yaw)),s=Math.sin(Math.toRadians(yaw));
        // T(at) Ry(yaw) S(scale) T(-center)
        double[] r={c*scale,0,s*scale,0, 0,scale,0,0, -s*scale,0,c*scale,0};
        double ox=-(r[0]*center[0]+r[1]*center[1]+r[2]*center[2]),oy=-(r[4]*center[0]+r[5]*center[1]+r[6]*center[2]),oz=-(r[8]*center[0]+r[9]*center[1]+r[10]*center[2]);
        return new float[]{(float)r[0],(float)r[1],(float)r[2],(float)(ox+x),(float)r[4],(float)r[5],(float)r[6],(float)(oy+y),(float)r[8],(float)r[9],(float)r[10],(float)(oz+z)};
    }
}
