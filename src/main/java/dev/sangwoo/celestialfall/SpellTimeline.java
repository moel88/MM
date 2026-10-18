package dev.sangwoo.celestialfall;

/** Server ticks. The contact hold stops this spell's motion, never the world simulation. */
public final class SpellTimeline {
    public static final int TRACE_START=24, CONVERGE_START=100, REVEAL=136, DESCENT_START=156;
    public static final int SILENCE_START=200, CONTACT=202, IMPACT=206;
    public static final int FADE_START=600, END=680, CLASH_COUNT=3, WAVE_COUNT=3;
    private SpellTimeline() {}
    public static int clashTick(int i) {
        if(i<0 || i>=CLASH_COUNT)throw new IllegalArgumentException("clash index");
        return 112+i*12;
    }
    public static int waveTick(int i) {
        if(i<0 || i>=WAVE_COUNT)throw new IllegalArgumentException("wave index");
        return IMPACT+(i==0?0:i==1?12:26);
    }
    public static float pulse(float age,int tick,float length) {
        return age<tick?0:1-progress(age,tick,tick+length);
    }
    public static float clashPulse(float age) {
        float value=0;
        for(int i=0;i<CLASH_COUNT;i++)value=Math.max(value,pulse(age,clashTick(i),12));
        return value;
    }
    public static float wavePulse(float age) {
        float value=0;
        for(int i=0;i<WAVE_COUNT;i++)value=Math.max(value,pulse(age,waveTick(i),14)*(1-i*.12f));
        return value;
    }
    public static boolean contactHold(float age) { return age>=CONTACT && age<IMPACT; }
    public static float motionAge(float age) { return contactHold(age)?CONTACT:age; }
    public static float clamp(float value) { return Math.max(0,Math.min(1,value)); }
    public static float progress(float age,float start,float end) { return clamp((age-start)/(end-start)); }
    public static float smooth(float value) { value=clamp(value);return value*value*(3-2*value); }
    public static float visibility(float age) {
        return smooth(progress(age,0,32))*(1-smooth(progress(age,FADE_START,END)));
    }
    public static double swordTipHeight(float age) {
        return MountainScale.SUMMON_HEIGHT*(1-Math.pow(progress(age,DESCENT_START,CONTACT),3.4));
    }
    public static double shockwaveRadius(float age) { return waveRadius(age,0); }
    public static double waveRadius(float age,int wave) {
        return MountainScale.SHOCKWAVE_RADIUS*(1+wave*.14)*Math.sqrt(progress(age,waveTick(wave),waveTick(wave)+60));
    }
    public static float monoImpact(float age) {
        if(contactHold(age))return 1;
        return Math.max(pulse(age,clashTick(2),2.2f),pulse(age,IMPACT,1.6f));
    }
    public static float descentPower(float age) {
        if(age<DESCENT_START || age>=CONTACT)return 0;
        return .25f+.75f*progress(age,DESCENT_START,CONTACT);
    }
}
