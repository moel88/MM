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
    public static boolean insideColumn(double dx,double dy,double dz,double radius,double height) {
        return radius>0 && dx*dx+dz*dz<=radius*radius && dy>=-2 && dy<=height;
    }
    public static boolean insideColumn(double dx,double dy,double dz,double radius) {
        return insideColumn(dx,dy,dz,radius,damageHeight(radius));
    }
}
