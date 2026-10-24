package dev.sangwoo.celestialfall.stand;

import java.util.List;

/** Star Platinum's stances (2.50.0): their names in the poses file and how the figure goes over from one to another. */
public final class StarPlatinumStance {
    /** Floating at rest, and the pose of the official art the user sent. */
    public static final List<String> NAMES=List.of("idle","hero");
    public static final int IDLE=0,HERO=1;
    /** How long the figure takes to go over from one stance to another (ticks). */
    public static final float CROSSFADE=12;
    private StarPlatinumStance(){}
    /** How far (0 to 1) a change made {@code ticks} ago has gone: eased in and out, so no joint starts or stops with a jolt. */
    public static float crossfade(float ticks){float k=Math.max(0,Math.min(1,ticks/CROSSFADE));return k*k*(3-2*k);}
}
