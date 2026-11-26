package dev.sangwoo.celestialfall.stand;

import java.util.List;

/** Gold Experience's stances (2.145.0): their names in the poses file and how the figure goes over from one to another. */
public final class GoldExperienceStance {
    /**
     * Floating at rest; the pose of the picture the user sent (floating with its legs crossed, its left hand on its hip
     * and its right hand hanging at its thigh); and standing straight as the model is built, for looking at it.
     */
    public static final List<String> NAMES=List.of("idle","hero","stand");
    public static final int IDLE=0,HERO=1,STAND=2;
    /** How long the figure takes to go over from one stance to another (ticks). */
    public static final float CROSSFADE=12;
    private GoldExperienceStance(){}
    /** How far (0 to 1) a change made {@code ticks} ago has gone: eased in and out, so no joint starts or stops with a jolt. */
    public static float crossfade(float ticks){float k=Math.max(0,Math.min(1,ticks/CROSSFADE));return k*k*(3-2*k);}
}
