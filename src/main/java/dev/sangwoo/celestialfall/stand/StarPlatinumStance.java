package dev.sangwoo.celestialfall.stand;

import java.util.List;

/** Star Platinum's stances (2.50.0): their names in the poses file and how the figure goes over from one to another. */
public final class StarPlatinumStance {
    /**
     * Floating at rest, the pose of the official art the user sent, and curled up as it comes out of its user (2.51.0);
     * the basic attack's (2.54.0): the guard, and each punch loaded and thrown (a left jab, a right cross, a left hook,
     * a lunging right); the rush's (2.57.0): the barrage with the left or the right fist thrown out straight, at the
     * middle, high or low, the other torn back to the chest; the heavy punch's (2.59.0): coiled deep with the right fist
     * cocked back past the hip, and the right straight thrown with the whole body behind it; the Star Finger's (2.60.0):
     * taking aim with the right hand drawn back level at the ribs, its first two fingers held out straight, and that hand
     * driven out straight at the target; the inhale's (2.61.0; 2.63.0 from the user's picture, as in the original): it
     * sucks with its breath drawn in (the suction: reared back, the back arched, chest out, head flung back to the sky,
     * arms flung wide and high), rears back into it with a gasp (the inhale: the arch deeper, the arms up in a V), and
     * trembles toward the strain as it sucks (the shoulders hunched up, the arms drawn in tight); the beatdown's (2.64.0):
     * gripping the collar of the one it struck with its left hand, the right fist cocked low (the grab), and the uppercut
     * thrown straight up through their chin (the rest of its cutscene is the punches', the rush's and the summon's); and
     * since 2.65.0 its own blow: reared up and back with the right fist cocked high behind the head (the wind-up, big
     * enough to be seen coming), and that fist smashed down on through the target with the whole body behind it.
     */
    public static final List<String> NAMES=List.of("idle","hero","summon","guard","wind_l","punch_l","wind_r","punch_r","wind_hook","hook","wind_big","punch_big",
        "rush_l","rush_l_hi","rush_l_lo","rush_r","rush_r_hi","rush_r_lo","wind_heavy","punch_heavy","wind_finger","star_finger",
        "inhale","suction","strain","beat_grab","beat_upper","beat_wind","beat_strike");
    public static final int IDLE=0,HERO=1,SUMMON=2,GUARD=3,WIND_L=4,PUNCH_L=5,WIND_R=6,PUNCH_R=7,WIND_HOOK=8,HOOK=9,WIND_BIG=10,PUNCH_BIG=11,
        RUSH_L=12,RUSH_L_HI=13,RUSH_L_LO=14,RUSH_R=15,RUSH_R_HI=16,RUSH_R_LO=17,WIND_HEAVY=18,PUNCH_HEAVY=19,WIND_FINGER=20,STAR_FINGER=21,
        INHALE=22,SUCTION=23,STRAIN=24,COLLAR=25,UPPERCUT=26,BEAT_WIND=27,BEAT_STRIKE=28;
    /** How long the figure takes to go over from one stance to another (ticks). */
    public static final float CROSSFADE=12;
    private StarPlatinumStance(){}
    /** How far (0 to 1) a change made {@code ticks} ago has gone: eased in and out, so no joint starts or stops with a jolt. */
    public static float crossfade(float ticks){float k=Math.max(0,Math.min(1,ticks/CROSSFADE));return k*k*(3-2*k);}
}
