package dev.sangwoo.celestialfall.samurai;

/**
 * The samurai's G, the one flash (2.143.0, user request: "그냥 테스트용 사무라이 캐릭터 G스킬이고 이 영상이랑 똑같게
 * 만들어봐", and the user's video, 9.5 s): its timing and its numbers, on the server's ticks from the press (the video's
 * moments at 20 ticks a second: its first frame is the press). A test character: G is their only skill.
 * <p>
 * The press marks the one looked at (within {@link #AIM_RANGE}); its user stands held, a hand on the sheathed sword, and
 * at {@link #BURST} the red light bursts out of them. Any tick before {@link #DASH} that the one marked holds a guard up
 * toward its user, it is blocked; its user struck before then, it is called off. At DASH its user is beside the one
 * marked, their back to them (the cut made in passing lands at {@link #ARRIVE}), and both are held from then: the
 * flourish, the sword put back in its scabbard ({@link #SHEATHE}), the ink ({@link #INK}), the grey world closing to a
 * ring round the one marked ({@link #GREY}), the black ground where four blades wait round them ({@link #VOID}) and cut
 * through them one after another ({@link #SLASHES}), and the world back ({@link #WORLD}): a pillar of light where they
 * stand, and they are thrown up into the air. Its user is let go at {@link #END}; its cooldown runs from then (or from
 * when it was blocked or called off). The numbers but the video's moments are mine.
 */
public final class SamuraiRules {
    private SamuraiRules(){}
    /** Its casts' place among the effects' skills (KagenoSkills). */
    public static final int SKILL=17;
    /** The press asks for G (samurai_skill_v1's first byte; the one in the crosshair follows it). */
    public static final int G=1;
    /** The red light out of its user (the video's 0.6 s), and its user beside the one marked: its cutscene begins (1.0 s). */
    public static final int BURST=12,DASH=20;
    /** The cut made in passing lands (1.2 s); the two more the flourish counts (the video's 93% to 94% at 2.27 s, to 95% at 3.53 s). */
    public static final int ARRIVE=24,FLOURISH_HIT=45,INK_HIT=70;
    /** The sword is home in its scabbard (2.93 s); the ink (3.2 s); the grey world (3.4 s); the black ground (4.2 s). */
    public static final int SHEATHE=59,INK=64,GREY=68,VOID=84;
    /** The four blades cut through the one marked (6.07, 6.23, 6.37 and 6.53 s). */
    public static final int[] SLASHES={121,125,127,131};
    /** The same on the clients' clock (seconds), as the video has them. */
    public static final float[] SLASH_SEC={6.07f,6.23f,6.37f,6.53f};
    /** The world back (6.77 s), the last blow and the one marked thrown up a tick after; its user let go (7.5 s). */
    public static final int WORLD=135,LAUNCH=136,END=150;
    /** Its cooldown: from its end, or from when it was blocked or called off. */
    public static final int COOLDOWN=3600;
    /** Whom the press can mark: the one looked at, this far from its user's eyes at most (blocks); how far off the camera's middle line (radians). */
    public static final double AIM_RANGE=12;
    public static final double AIM_CONE=Math.toRadians(7);
    /** How far the one marked may have gone from its user by DASH (further, or out of sight: called off). */
    public static final double REACH=18;
    /** Where its user stands beside the one marked: this far short of them on the way they came, this far to the side. */
    public static final double SHORT=1.3,ASIDE=1.4;
    /** All its blows deal this together. */
    public static final float DAMAGE=70;
    /** Its blows' ticks and each one's share of DAMAGE: the cut in passing, the flourish's two, the four blades, the last. */
    public static final int[] BLOWS={ARRIVE,FLOURISH_HIT,INK_HIT,SLASHES[0],SLASHES[1],SLASHES[2],SLASHES[3],LAUNCH};
    private static final float[] SHARES={8/70f,4/70f,4/70f,8/70f,8/70f,8/70f,8/70f,22/70f};
    /** Thrown up: the speed up (blocks a tick: some six blocks high) and away from its user. */
    public static final double FLY_UP=1.12,FLY=.12;

    /** The blow landing at {@code age} ticks, or -1 if none does. */
    public static int blowIndex(long age){for(int k=0;k<BLOWS.length;k++)if(BLOWS[k]==age)return k;return -1;}
    /** How many of its blows have landed by {@code age} ticks. */
    public static int blowsBy(double age){int n=0;for(int at:BLOWS)if(age>=at-1e-6)n++;return n;}
    /** What blow {@code k} deals: its share of DAMAGE. */
    public static float blowDamage(int k){return k<0||k>=BLOWS.length?0:DAMAGE*SHARES[k];}
    /** Whether a guard can still block it at {@code age} ticks (before its user is beside them). */
    public static boolean warning(long age){return age>=0&&age<DASH;}
    /** Whether the one marked is held at {@code age} ticks: from DASH until they are thrown up. */
    public static boolean held(long age){return age>=DASH&&age<LAUNCH;}
    /** Whether its user is held at {@code age} ticks: from the press to its end. */
    public static boolean userHeld(long age){return age>=0&&age<END;}
    /** When G is ready again after one that ended (or was stopped) at {@code end}. */
    public static long ready(long end){return end+COOLDOWN;}
}
