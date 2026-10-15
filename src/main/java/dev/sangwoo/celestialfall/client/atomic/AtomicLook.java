package dev.sangwoo.celestialfall.client.atomic;

/**
 * Screen-space timing of G, in seconds after the first skill frame (144.733 s of the review
 * footage). Pure functions only: the post shader, the HUD fallback and the camera read them.
 */
public final class AtomicLook {
    private AtomicLook() {}
    // Chant.
    public static final float REVEAL_START=.017f, REVEAL_END=.134f, TINT_FULL=1.267f, DIP_START=5.367f, LAST_CHANT=5.575f;
    // Impact frames: violet void (drawn in the world), white, black/magenta, black/lavender, white.
    public static final float IMPACT=5.590f, WHITE=5.640f, MAGENTA=5.707f, LAVENDER=5.757f, WHITE_LAST=5.874f, PINK=5.891f;
    // Blast interior.
    public static final float STROBE=6.27f, DEEP=7.87f, COLLAPSE=21.207f, COLLAPSE_FLAT=21.24f, BEAM_END=22.07f,
        GRADE_FADE=22.30f, GRADE_END=23.25f;
    // Shake (random jitter, updated every game frame in the footage).
    public static final float SHAKE_START=6.29f, SHAKE_END=25.15f;

    /** One frame of the screen treatment. Colours are 0xRRGGBB. */
    public record Grade(float tint,int frame,float morph,float mix,int lo,int hi) {
        public static final Grade NONE=new Grade(0,0,0,0,0,0);
    }

    static float unit(float a,float b,float c) { return c==b?(a>=c?1:0):Math.max(0,Math.min(1,(a-b)/(c-b))); }
    static float smooth(float a,float b,float c) { float t=unit(a,b,c);return t*t*(3-2*t); }
    static double hash(double n) { double x=Math.sin(n*12.9898+78.233)*43758.5453;return x-Math.floor(x); }

    /** Violet chant haze: linear ramp to 1.267 s, slight dip before the flash, gone at impact. */
    public static float tint(float sec) {
        if(sec<REVEAL_START||sec>=IMPACT)return 0;
        return unit(sec,REVEAL_START,TINT_FULL)*(1-.35f*smooth(sec,DIP_START,LAST_CHANT));
    }
    /** Two-tone impact frame shown at {@code sec}: 0 none, 1 violet void, 2 white, 3 black/magenta, 4 black/lavender, 5 white. */
    public static int frame(float sec) {
        if(sec<IMPACT||sec>=PINK)return 0;
        return sec<WHITE?1:sec<MAGENTA?2:sec<LAVENDER?3:sec<WHITE_LAST?4:5;
    }
    /** Shape keyframe blend of the impact frames (0 first white … 3 last white). */
    public static float morph(float sec) {
        return switch(frame(sec)){
            case 2->sec<5.673f?0:.25f;
            case 3->sec<5.740f?1:1.15f;
            case 4->2;
            case 5->3;
            default->0;
        };
    }

    private static final float[] PINK_T={5.891f,5.942f,5.988f,6.034f,6.080f,6.125f,6.159f,6.225f,6.259f,6.27f};
    private static final int[] PINK_C={0xfbb8f7,0xf9eff5,0xfbbcf7,0xfac4f7,0xfad8f6,0xf692f8,0xdc78f8,0xf6b4f7,0xb05bf8,0xb05bf8};
    private static final float[] STROBE_T={6.27f,6.62f,7.12f,7.62f,8.12f,8.6f};
    private static final int[] STROBE_C={0xb05bf8,0x7d38f3,0x581de1,0x4910df,0x4007de,0x3a00e8};
    /** Deep blue-violet interior: dark shapes, light shapes (green is exactly zero). */
    public static final int DEEP_LO=0x2200bb,DEEP_HI=0x4b00f8,FLAT=0x4a00f7,INDIGO=0x1a00b3;

    static int lerpColor(int a,int b,float t) {
        int r=(int)Math.round(((a>>16)&255)+(((b>>16)&255)-((a>>16)&255))*t);
        int g=(int)Math.round(((a>>8)&255)+(((b>>8)&255)-((a>>8)&255))*t);
        int bl=(int)Math.round((a&255)+((b&255)-(a&255))*t);
        return (Math.max(0,Math.min(255,r))<<16)|(Math.max(0,Math.min(255,g))<<8)|Math.max(0,Math.min(255,bl));
    }
    static int scale(int c,float k) {
        return (Math.min(255,Math.round(((c>>16)&255)*k))<<16)|(Math.min(255,Math.round(((c>>8)&255)*k))<<8)|Math.min(255,Math.round((c&255)*k));
    }
    static int keyed(float[] t,int[] c,float sec) {
        if(sec<=t[0])return c[0];
        for(int i=1;i<t.length;i++)if(sec<t[i])return lerpColor(c[i-1],c[i],(sec-t[i-1])/(t[i]-t[i-1]));
        return c[c.length-1];
    }

    /** Screen grade at {@code sec}; {@code frameSeed} changes every rendered game frame (flicker). */
    public static Grade grade(float sec,long frameSeed) {
        if(!(sec>=0))return Grade.NONE;
        if(sec<IMPACT)return new Grade(tint(sec),0,0,0,0,0);
        int f=frame(sec);
        if(f>0)return new Grade(0,f,morph(sec),0,0,0);
        if(sec<STROBE){
            int mean=keyed(PINK_T,PINK_C,sec);
            return new Grade(0,0,0,1,scale(mean,.9f),scale(mean,1.05f));
        }
        if(sec<DEEP+.73f){
            int mean=keyed(STROBE_T,STROBE_C,sec);
            // 15-30 Hz flicker between dark purple and light violet, settling as it deepens.
            float amp=1-smooth(sec,STROBE,DEEP+.73f);
            float flick=(float)hash(Math.floor(sec*22))*2-1;
            int c=flick>.55f?lerpColor(mean,0xb26bf7,amp*.85f):scale(mean,1+flick*.22f*amp);
            float spread=sec>=6.57f&&sec<6.87f?.02f:.08f+.1f*(1-amp);
            return new Grade(0,0,0,1,scale(c,1-spread),scale(c,1+spread*.6f));
        }
        if(sec<COLLAPSE){
            // Per-frame brightness wobble (#2e00d4 – #4800f6) and the slightly darker last second.
            float wobble=1+((float)hash(frameSeed*.37+3.1)-.5f)*.09f;
            float dark=1-.12f*smooth(sec,20.37f,20.6f);
            return new Grade(0,0,0,1,scale(DEEP_LO,wobble*dark),scale(DEEP_HI,wobble*dark));
        }
        if(sec<COLLAPSE_FLAT)return new Grade(0,0,0,1,FLAT,FLAT);
        if(sec<BEAM_END)return new Grade(0,0,0,1,DEEP_LO,DEEP_HI);
        if(sec<GRADE_FADE)return new Grade(0,0,0,1,INDIGO,INDIGO);
        if(sec<GRADE_END){
            float x=unit(sec,GRADE_FADE,GRADE_END);
            return new Grade(0,0,0,(1-x)*(1-x),INDIGO,INDIGO);
        }
        return Grade.NONE;
    }

    private static final float[] SHAKE_T={6.29f,6.30f,6.97f,7.2f,8.67f,9.0f,22.27f,22.67f,23.92f,24.67f,24.92f,25.15f};
    private static final float[] SHAKE_A={0f,.8f,.6f,1f,1f,1.5f,1.5f,.9f,.65f,.35f,.15f,0f};
    /** RMS view jitter in degrees: still through the flash, 0.7 s later a constant quake, then a decay to zero. */
    public static float shake(float sec) {
        if(sec<SHAKE_T[0]||sec>=SHAKE_END)return 0;
        for(int i=1;i<SHAKE_T.length;i++)if(sec<SHAKE_T[i])return SHAKE_A[i-1]+(SHAKE_A[i]-SHAKE_A[i-1])*(sec-SHAKE_T[i-1])/(SHAKE_T[i]-SHAKE_T[i-1]);
        return 0;
    }
    /** Zero-mean jitter with unit RMS for one update step and axis. */
    public static float jitter(long step,int axis) {
        double s=hash(step*3.17+axis*101.3)+hash(step*7.91+axis*57.1+11)+hash(step*1.37+axis*13.7+29);
        return (float)((s-1.5)*2);
    }
}
