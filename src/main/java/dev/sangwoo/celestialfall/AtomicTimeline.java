package dev.sangwoo.celestialfall;

/** One cast clock for the white exposure, full-height column, impact and damage. */
public final class AtomicTimeline {
    public static final int READY=0, CIRCUITS=0, CLOSEUP=0, WORDS=10, AM=42,
        ATOMIC=90, SLASH_START=100, SLASH_END=110, SILENCE=118, RECOVER=144,
        IMPACT=128, COLUMN=128, INK_START=128, WIDE_SHOT=168, EXTERIOR=240,
        SHRINK_START=432, RETURN_START=464, CAMERA_END=468, END=468;
    public static final int DAMAGE_START=IMPACT, DAMAGE_INTERVAL=20, CAST_LOCK_END=468;
    // The current reference holds a violet exposure, not the older seven-second white screen.
    public static final float WHITEOUT_START=128, WHITEOUT_END=128;
    private AtomicTimeline() {}
    public static float progress(float a,float b,float c) { return SpellTimeline.progress(a,b,c); }
    public static float ease(float a,float b,float c) { return SpellTimeline.smooth(progress(a,b,c)); }
    public static float visibility(float a) { return ease(a,0,12)*(1-ease(a,RETURN_START,END)); }
    public static float charge(float a) { return (.55f+.45f*ease(a,0,ATOMIC))*(1-ease(a,SLASH_END,IMPACT)); }
    public static float ink(float a) { return 0; }
    public static boolean whiteout(float a) { return a>=WHITEOUT_START && a<WHITEOUT_END; }
    public static float column(float a) { return a>=IMPACT && a<END?1:0; }
    public static float pulse(float a) {
        return Math.max(SpellTimeline.pulse(a,IMPACT,20),SpellTimeline.pulse(a,280,24)*.65f);
    }
    public static double blastRadius(float a,double radius) {
        return column(a)>0?radius*remainingScale(a):0;
    }
    public static float remainingScale(float a) { return 1-ease(a,SHRINK_START,END); }
    /** Visual extension only; damage bounds and horizontal contraction keep their original scale. */
    public static double columnHeight(double baseRadius) { return damageHeight(baseRadius)+500; }
    public static double damageHeight(double baseRadius) { return baseRadius*2.5; }
    public static boolean damageDue(long age) { return age>=DAMAGE_START && age<END && (age-DAMAGE_START)%DAMAGE_INTERVAL==0; }
    public static boolean insideColumn(double dx,double dy,double dz,double radius,double height) {
        return radius>0 && dx*dx+dz*dz<=radius*radius && dy>=-2 && dy<=height;
    }
    public static boolean insideColumn(double dx,double dy,double dz,double radius) {
        return insideColumn(dx,dy,dz,radius,damageHeight(radius));
    }
    public static float blend(float a) { return ease(a,0,12)*(1-ease(a,RETURN_START,CAMERA_END)); }
    public static float shake(float a) {
        if(a>=END)return 0;
        return a<IMPACT?.025f*charge(a):Math.max(pulse(a)*.65f,column(a)*.025f);
    }
    public static boolean posed(float a) { return a>=0 && a<RECOVER; }
    public static float violet(float a){return ease(a,IMPACT,IMPACT+3)*(1-ease(a,432,460));}
    public static int shotIndex(float a){return a<IMPACT?0:a<WIDE_SHOT?1:a<EXTERIOR?2:3;}
    public record Shot(double radius,double height,double focusHeight,double azimuth,double fov,double roll) {}
    public static Shot shot(float a,double r){
        if(a<WIDE_SHOT)return new Shot(9.5,1.9,1.15,0,68,0);
        float dolly=ease(a,WIDE_SHOT,EXTERIOR),orbit=ease(a,EXTERIOR,SHRINK_START);
        return new Shot(9.5+(r*1.85-9.5)*dolly,1.9+(r*.9-1.9)*dolly,1.15+(r*.18-1.15)*dolly,35*dolly+65*orbit,68,0);
    }
}
