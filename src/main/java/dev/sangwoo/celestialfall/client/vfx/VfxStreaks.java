package dev.sangwoo.celestialfall.client.vfx;

/**
 * Seeds of the energy streak field (vfx_energy mode 17): one row per streak, the same every run.
 * Pure Java (no GL), shared by {@link Vfx} and the offscreen preview tool.
 */
public final class VfxStreaks {
    /** Streaks in the field; quality settings draw a share of them. */
    public static final int COUNT=2600;
    private VfxStreaks(){}

    /**
     * Per streak: lane around the pillar, phase and speed/radius seed (the vertex Position), then
     * length, width, kind and draw order (the vertex Color, 0..255). Draw order is spread evenly so a
     * share of the streaks is an even thinning of the field.
     */
    public static int[] colors(int count){
        int[] c=new int[count*4];
        for(int i=0;i<count;i++){
            c[i*4]=(int)(hash(i,3)*255);c[i*4+1]=(int)(hash(i,4)*255);c[i*4+2]=(int)(hash(i,5)*255);
            c[i*4+3]=(int)(((i*0.6180339887)%1.0)*255);
        }
        return c;
    }
    public static float[] positions(int count){
        float[] p=new float[count*3];
        for(int i=0;i<count;i++){p[i*3]=(float)hash(i,0);p[i*3+1]=(float)hash(i,1);p[i*3+2]=(float)hash(i,2);}
        return p;
    }
    static double hash(int i,int k){
        long h=i*0x9E3779B97F4A7C15L+k*0xC2B2AE3D27D4EB4FL+0x165667B19E3779F9L;
        h=(h^(h>>>33))*0xFF51AFD7ED558CCDL;h=(h^(h>>>33))*0xC4CEB9FE1A85EC53L;h^=h>>>33;
        return (h>>>11)/(double)(1L<<53);
    }
}
