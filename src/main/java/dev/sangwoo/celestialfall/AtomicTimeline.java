package dev.sangwoo.celestialfall;

/**
 * One cast clock for G ("I am Atomic"), timed from the 60 fps review footage: tick 0 is the
 * first skill frame (144.733 s of the video). Server ticks drive damage and the caster lock;
 * the client reads the same clock in seconds for every visual.
 */
public final class AtomicTimeline {
    private AtomicTimeline() {}
    /** The voice/rumble clip starts 0.217 s after the first skill frame. */
    public static final int AUDIO_START=4;
    /** Impact frames (5.590 s). */
    public static final int IMPACT=112;
    /** Rumble and camera shake (6.29 s). */
    public static final int SHAKE_START=126;
    /** Crater slabs erupt (7.07 s). */
    public static final int SLABS=141;
    /** Collapse into the central beam (21.21 s) and the beam's end (22.07 s). */
    public static final int COLLAPSE=424, BEAM_END=441;
    /** The colour grade fades back over 22.30 → 23.25 s; the caster is released at its end. */
    public static final int INTERIOR_END=446, GRADE_END=465;
    /** Crater breakup 24.47 → 27.57 s; the shake reaches zero at 25.15 s. */
    public static final int BREAK_START=489, SHAKE_END=503, END=552;
    public static final int DAMAGE_START=IMPACT, DAMAGE_END=BEAM_END, DAMAGE_INTERVAL=20, CAST_LOCK_END=GRADE_END;
    /** Seconds after the first skill frame for a cast age in ticks. */
    public static float sec(float ticks) { return ticks/20f; }
    public static boolean damageDue(long age) { return age>=DAMAGE_START && age<DAMAGE_END && (age-DAMAGE_START)%DAMAGE_INTERVAL==0; }
    public static boolean locked(float age) { return age>=0 && age<CAST_LOCK_END; }
    /** Damage cylinder height above the origin; the radius is the visual blast radius. */
    public static double damageHeight(double radius) { return radius*2.5; }

    /** The pillar opens out of a hairline this wide (fraction of the blast radius R) at the impact. */
    public static final double HAIRLINE=.04;
    /**
     * Fastest the opening pillar's edge spreads, in blocks per second: a sprinting player (about 5.6 blocks/s) outruns
     * it, a walking one (about 4.3) does not. It eases in and out over {@link #OPEN_RAMP} seconds, and spreads faster
     * only when a very large configured radius would otherwise not open within {@link #OPEN_LIMIT} seconds.
     */
    public static final double OPEN_SPEED=4.5,OPEN_RAMP=.5,OPEN_LIMIT=12;
    /** The pillar's own clock runs slowed for the first 0.9 s after the impact (0.35x at first) and catches up smoothly. */
    public static final double IGNITION_SLOW=.9;
    /** Seconds since the impact on the pillar's own clock, for t real seconds since the impact. */
    public static double pillarClock(double t) {
        if(!(t>0)||t>=IGNITION_SLOW)return t;
        double u=t/IGNITION_SLOW;
        return IGNITION_SLOW*u*(.35+u*(1.3-.65*u));
    }
    /** Seconds the opening takes for a blast radius R. */
    public static double openingSeconds(double R) {
        double d=R*(1-HAIRLINE);
        return d/Math.max(OPEN_SPEED,d/(OPEN_LIMIT-OPEN_RAMP))+OPEN_RAMP;
    }
    /** Radius of the opening pillar t seconds after the impact on its own clock: from the hairline to R at the opening speed. */
    public static double openingRadius(double t,double R) {
        if(!(t>=0)||!(R>0))return 0;
        double d=R*(1-HAIRLINE),v=Math.max(OPEN_SPEED,d/(OPEN_LIMIT-OPEN_RAMP)),T=d/v+OPEN_RAMP;
        if(t>=T)return R;
        double spread=t<OPEN_RAMP?v*t*t/(2*OPEN_RAMP):t<=T-OPEN_RAMP?v*OPEN_RAMP/2+v*(t-OPEN_RAMP):d-v*(T-t)*(T-t)/(2*OPEN_RAMP);
        return Math.min(R,R*HAIRLINE+spread);
    }
    /**
     * Radius of the damage column at a cast age in ticks: none before the impact, then the opening pillar's (on the same
     * clock the pillar is drawn with), the full radius once open and through the collapse until the beam ends.
     */
    public static double damageRadius(long age,double R) {
        return age<DAMAGE_START?0:openingRadius(pillarClock((age-DAMAGE_START)/20.0),R);
    }
    /** Inside the column a target moves 75 % slower (a quarter of its speed), and at its own speed again the moment it is out. */
    public static final double HIT_SLOW=.75;
    /** The movement speed modifier that holds a target in the column (transient, never saved). */
    public static final java.util.UUID HIT_SLOW_ID=java.util.UUID.fromString("8e7d4281-016d-45fc-97d3-af497070f9d6");
    /**
     * The movement speed attribute change that leaves a target a quarter of its speed. A player's walking speed goes
     * with the attribute; a mob's ground speed goes with its square (a mob feeds the attribute in both as its forward
     * input and as the speed scaling it), so a mob's attribute is halved instead.
     */
    public static double holdModifier(boolean player) {
        return player?-HIT_SLOW:Math.sqrt(1-HIT_SLOW)-1;
    }
    /** Radius of the column that holds its targets at a cast age: the damage column from the impact until the beam ends, none otherwise. */
    public static double holdRadius(long age,double R) {
        return age>=DAMAGE_START&&age<DAMAGE_END?damageRadius(age,R):0;
    }
    public static boolean insideColumn(double dx,double dy,double dz,double radius,double height) {
        return radius>0 && dx*dx+dz*dz<=radius*radius && dy>=-2 && dy<=height;
    }
    public static boolean insideColumn(double dx,double dy,double dz,double radius) {
        return insideColumn(dx,dy,dz,radius,damageHeight(radius));
    }
}
