package dev.sangwoo.celestialfall.client.vfx;

/**
 * Tileable noise for the VFX materials, generated in code once (no texture asset). Four channels:
 * R smooth FBM, G finer FBM, B ridged lines, A coarse FBM. Every channel wraps seamlessly at the
 * texture edges (the lattice periods divide the size) and is stretched to the full 0..255 range so
 * the shaders can cut crisp streaks out of it. Pure Java: no GL calls, usable from tests.
 */
public final class VfxNoise {
    public static final int SIZE=256;
    private VfxNoise(){}

    /** Pixels row by row in NativeImage order (ABGR packed: alpha in the high byte, red in the low byte). */
    public static int[] abgr(){
        float[] r=stretch(fbm(8,4,11)),g=stretch(fbm(16,3,23)),b=stretch(ridged(8,4,37)),a=stretch(fbm(4,3,53));
        int[] out=new int[SIZE*SIZE];
        for(int i=0;i<out.length;i++)out[i]=(q(a[i])<<24)|(q(b[i])<<16)|(q(g[i])<<8)|q(r[i]);
        return out;
    }
    static int q(float v){return Math.max(0,Math.min(255,Math.round(v*255)));}
    static float hash(int x,int y,int seed){
        int h=x*374761393+y*668265263+seed*1442695041;
        h=(h^(h>>>13))*1274126177;h^=h>>>16;
        return (h&0xffffff)/(float)0xffffff;
    }
    /** Smooth value noise on a lattice of {@code p} cells across the texture, wrapping at p. */
    static float value(float x,float y,int p,int seed){
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);float fx=x-x0,fy=y-y0;
        fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        int xa=Math.floorMod(x0,p),xb=Math.floorMod(x0+1,p),ya=Math.floorMod(y0,p),yb=Math.floorMod(y0+1,p);
        float a=hash(xa,ya,seed),b=hash(xb,ya,seed),c=hash(xa,yb,seed),d=hash(xb,yb,seed);
        return a+(b-a)*fx+(c-a)*fy+(a-b-c+d)*fx*fy;
    }
    static float[] fbm(int period,int octaves,int seed){
        float[] o=new float[SIZE*SIZE];
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            float sum=0,amp=1,norm=0;int p=period;
            for(int k=0;k<octaves;k++){sum+=amp*value(x*p/(float)SIZE,y*p/(float)SIZE,p,seed+k*101);norm+=amp;amp*=.5f;p*=2;}
            o[y*SIZE+x]=sum/norm;
        }
        return o;
    }
    static float[] ridged(int period,int octaves,int seed){
        float[] o=new float[SIZE*SIZE];
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            float sum=0,amp=1,norm=0;int p=period;
            for(int k=0;k<octaves;k++){
                float v=1-Math.abs(2*value(x*p/(float)SIZE,y*p/(float)SIZE,p,seed+k*101)-1);
                sum+=amp*v*v;norm+=amp;amp*=.5f;p*=2;
            }
            o[y*SIZE+x]=sum/norm;
        }
        return o;
    }
    static float[] stretch(float[] v){
        float lo=Float.MAX_VALUE,hi=-Float.MAX_VALUE;for(float f:v){lo=Math.min(lo,f);hi=Math.max(hi,f);}
        float k=hi>lo?1/(hi-lo):1;float[] o=new float[v.length];
        for(int i=0;i<v.length;i++)o[i]=(v[i]-lo)*k;
        return o;
    }
}
