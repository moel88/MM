package dev.sangwoo.celestialfall.stand;

import java.util.List;

/** Star Platinum's stances (2.50.0): their names in the poses file and how the figure goes over from one to another. */
public final class StarPlatinumStance {
    /**
     * Floating at rest, the pose of the official art the user sent, and curled up as it comes out of its user (2.51.0);
     * the basic attack's (2.54.0): the guard, and each punch loaded and thrown (a left jab, a right cross, a left hook,
     * a lunging right).
     */
    public static final List<String> NAMES=List.of("idle","hero","summon","guard","wind_l","punch_l","wind_r","punch_r","wind_hook","hook","wind_big","punch_big");
    public static final int IDLE=0,HERO=1,SUMMON=2,GUARD=3,WIND_L=4,PUNCH_L=5,WIND_R=6,PUNCH_R=7,WIND_HOOK=8,HOOK=9,WIND_BIG=10,PUNCH_BIG=11;
    /** How long the figure takes to go over from one stance to another (ticks). */
    public static final float CROSSFADE=12;
    private StarPlatinumStance(){}
    /** How far (0 to 1) a change made {@code ticks} ago has gone: eased in and out, so no joint starts or stops with a jolt. */
    public static float crossfade(float ticks){float k=Math.max(0,Math.min(1,ticks/CROSSFADE));return k*k*(3-2*k);}
}
