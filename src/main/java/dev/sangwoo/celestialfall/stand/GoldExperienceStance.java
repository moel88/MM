package dev.sangwoo.celestialfall.stand;

import java.util.List;

/** Gold Experience's stances (2.145.0): their names in the poses file and how the figure goes over from one to another. */
public final class GoldExperienceStance {
    /**
     * Floating at rest; the pose of the picture the user sent (floating with its legs crossed, its left hand on its hip
     * and its right hand hanging at its thigh); and standing straight as the model is built, for looking at it.
     */
    public static final List<String> NAMES=List.of("idle","hero","stand","summon","guard","rush_l","rush_l_hi","rush_l_lo","rush_r","rush_r_hi","rush_r_lo","over_wind","over_punch","mend","ground");
    /** SUMMON (2.146.0): curled up behind its crossed forearms, as it comes out of its user and goes back into them. */
    public static final int IDLE=0,HERO=1,STAND=2,SUMMON=3;
    /**
     * (2.147.0) The muda rush: the guard it holds before its first fist and after its last, and a fist thrown out
     * straight, left or right, at the middle, high or low (client/stand/GoldRush goes from one to the next).
     */
    public static final int GUARD=4,RUSH_L=5,RUSH_L_HI=6,RUSH_L_LO=7,RUSH_R=8,RUSH_R_HI=9,RUSH_R_LO=10;
    /** (2.148.0) The overdrive punch: wound up, and thrown (client/stand/GoldPunch goes guard, wound up, thrown, guard). */
    public static final int OVER_WIND=11,OVER_PUNCH=12;
    /** (2.149.0) Y tapped, the healing of another: its open hand laid on them (client/stand/GoldHeal goes guard, this, guard). */
    public static final int MEND=13;
    /**
     * (2.152.0) H, the tree of life: down on its left knee, its open right palm laid flat on the ground ahead of it
     * (client/stand/GoldTree goes idle, this, idle: the user's "스탠드가 땅을 만지고 나무가 자라나게").
     */
    public static final int GROUND=14;
    /** How long the figure takes to go over from one stance to another (ticks). */
    public static final float CROSSFADE=12;
    private GoldExperienceStance(){}
    /** How far (0 to 1) a change made {@code ticks} ago has gone: eased in and out, so no joint starts or stops with a jolt. */
    public static float crossfade(float ticks){float k=Math.max(0,Math.min(1,ticks/CROSSFADE));return k*k*(3-2*k);}
}
