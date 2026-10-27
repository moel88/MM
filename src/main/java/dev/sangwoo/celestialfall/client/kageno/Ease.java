package dev.sangwoo.celestialfall.client.kageno;

/** Small timing helpers shared by the Kageno clips and effects. */
public final class Ease {
    private Ease(){}
    public static float unit(float a,float b,float c){return c==b?(a>=c?1:0):Math.max(0,Math.min(1,(a-b)/(c-b)));}
    public static float smooth(float a,float b,float c){float t=unit(a,b,c);return t*t*(3-2*t);}
    public static float out(float a,float b,float c){float t=1-unit(a,b,c);return 1-t*t*t;}
    public static float in(float a,float b,float c){float t=unit(a,b,c);return t*t*t;}
    /** 1 inside [b,c], fading in over {@code in} and out over {@code out} (same units). */
    public static float window(float a,float b,float c,float in,float out){return smooth(a,b,b+in)*(1-smooth(a,c-out,c));}
    public static double hash(int i){double x=Math.sin(i*12.9898+78.233)*43758.5453;return x-Math.floor(x);}
    /** Seconds of reference footage → ticks. */
    public static float t(double sec){return (float)(sec*20);}
}
