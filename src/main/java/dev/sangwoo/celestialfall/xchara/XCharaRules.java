package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's G, the sword dance (2.112.0, user request: "바로 G스킬 부터 만들자 상대를 쳐다보고 G스킬 누르면 !!!가 나와 이때
 * 가드로 방어하면 G스킬을 씹을 수 있고 못 막으면 그대로 G스킬 컷씬이 나오게 컷씬은 이상한 공간으로 서로 이동 되고 서로
 * 뒤집어져 있다가 카메라가 넓어지더니 수많은 검들이 보이고 컷씬이 끝나고 적이 엄청난 칼을 맞아서 썰리는 스킬이야 다 맞으면
 * 날아가", and the user's video): its timing and its numbers, on the server's ticks from the press (the video's moments
 * less its press, 0.45 s into it, at 20 ticks a second). The user's answers: the both of them upside down facing each other
 * in the strange space, the cutscene its user's and the one it marked's, 60 in all and 3 minutes ("합 60 3분"), a guard
 * during the "!!!" calls it off and its cooldown runs, its sound cut from the video's.
 * <p>
 * The press marks the one looked at (within {@link #AIM_RANGE}) and the "!!!" shows over them; any tick before
 * {@link #WARN} that they hold a guard up toward its user, it is blocked. Past WARN both are in the strange space, the two
 * hanging upside down facing each other from its first moment (2.112.1, user request: "시전자가 칼을 세워드는 저거 없애고
 * 곧바로 서로 거꾸로 마주보는 컷씬부터 시작": 2.112.0 opened on its user raising the knife until the video's 1.75 s), the
 * camera pulling back on the knives round its user ({@link #SHOT_C}); at {@link #VANISH} the two are gone from it and its blows begin, one every
 * {@link #EVERY} ticks, {@link #HITS} of them; at {@link #WORLD} the world is back, the one marked cut up where they stand;
 * the blows taken, they are sent flying at {@link #LAUNCH}, when it is over.
 */
public final class XCharaRules {
    private XCharaRules(){}
    /** Its casts' place among the effects' skills (KagenoSkills: Kageno's 0 to 9, Lee So-sang's 15). */
    public static final int SKILL=14;
    /** The press asks for G (xchara_skill_v1's first byte). */
    public static final int G=1;
    /** (2.115.0) The press asks for Q, the hack on or off (xchara_skill_v1's only byte then; XCharaHack). */
    public static final int Q=2;
    /** (2.119.0) The press asks for the restore (xchara_skill_v1's only byte then; XCharaHeal): E's until 2.120, Y's from 2.121.0 (user request: "q활성화 e를 q활성화 y로 옮기고"). */
    public static final int Y=3;
    /** (2.121.0) The press asks for R with the hack on, the cleave (XCharaAttack.cleave, XCharaCombo.CLEAVE). */
    public static final int R=4;
    /**
     * E with the hack on, the restore (2.119.0, user request: "Q활성화 E스킬부터 만들자 간단하게 패널 누르는 모션 나오고 몸에
     * 연두 이펙트 둘러지고 65힐"): its user presses their panel and is healed {@link #HEAL}. The press lands HEAL_AT
     * ticks in (the heal is then); it holds its user where they stand HEAL_HOLD ticks and is over at HEAL_LENGTH; its
     * cooldown runs from its end, or from when it was stopped short (struck before the press landed: no heal). The
     * numbers but the 65 are mine.
     */
    public static final float HEAL=65;
    public static final int HEAL_AT=8,HEAL_HOLD=14,HEAL_LENGTH=18,HEAL_COOLDOWN=400;
    /** When E is ready again after one begun at {@code start} that ran its course. */
    public static long healReady(long start){return start+HEAL_LENGTH+HEAL_COOLDOWN;}
    /** Whether E begun at {@code start} still holds its user at {@code now}. */
    public static boolean healHolds(long start,long now){return now>=start&&now<start+HEAL_HOLD;}
    /** Server ticks from one Q heard to the next (a double tap cannot make it flicker; its coming and going are done in less: client/xchara/HackLook). */
    public static final int HACK_RATE=8;
    /** The hack after a Q: on if it was off and its user may have it on, off if it was on. */
    public static boolean hackAfter(boolean on,boolean may){return !on&&may;}

    /** The "!!!" over the one marked from this tick (the video's 0.6 s), until the strange space at WARN (its 1.35 s). */
    public static final int MARK=3,WARN=18;
    /** The strange space's shots: the two upside down from WARN, pulled back on the knives from SHOT_C (the video's 4.35 s). */
    public static final int SHOT_C=78;
    /** The two gone from the strange space and its first blow (7.4 s); the world back (7.75 s). */
    public static final int VANISH=139,WORLD=146;
    /** Its blows: one every EVERY ticks from VANISH (ten a second, the video's hit count: 1 at 7.4 s, 40 at 11.3 s). */
    public static final int HITS=40,EVERY=2;
    /** All its blows deal this together (the user's answer: "합 60"), the same each. */
    public static final float DAMAGE=60;
    /** The one marked, every blow taken, is sent flying (11.4 s): it is over. */
    public static final int LAUNCH=219,END=LAUNCH;
    /** Its cooldown (the user's answer: "3분"): from its end, or from when it was blocked or called off. */
    public static final int COOLDOWN=3600;
    /** Whom the press can mark: the one looked at, this far from its user's eyes at most (blocks). */
    public static final double AIM_RANGE=12;
    /** How far off the camera's middle line the one marked may be (radians). */
    public static final double AIM_CONE=Math.toRadians(7);
    /** Sent flying: each tick along the way from its user and up (as Star Platinum's heavy punch throws, some 17 blocks). */
    public static final double FLY=2.8,FLY_UP=.75;

    /** The tick of blow {@code k} (from 0). */
    public static int blowAt(int k){return VANISH+k*EVERY;}
    /** The blow landing at {@code age} ticks, or -1 if none does. */
    public static int blowIndex(long age){
        if(age<VANISH||(age-VANISH)%EVERY!=0)return -1;
        long k=(age-VANISH)/EVERY;return k<HITS?(int)k:-1;
    }
    /** How many of its blows have landed by {@code age} ticks. */
    public static int blowsBy(double age){return age<VANISH?0:(int)Math.min(HITS,Math.floor((age-VANISH)/EVERY+1e-6)+1);}
    /** What blow {@code k} deals: its share of DAMAGE, all alike. */
    public static float blowDamage(int k){return k<0||k>=HITS?0:DAMAGE/HITS;}
    /** Whether a guard can still block it at {@code age} ticks (the "!!!" before the strange space). */
    public static boolean warning(long age){return age>=0&&age<WARN;}
    /** Whether its two are held in it at {@code age} ticks: from the strange space to the launch. */
    public static boolean held(long age){return age>=WARN&&age<END;}
    /** Whether its user is held at {@code age} ticks: through the "!!!" too (they stand looking at the one marked). */
    public static boolean userHeld(long age){return age>=0&&age<END;}
    /** Whether they are in the strange space at {@code age} ticks (not in the world): WARN until the world is back. */
    public static boolean inSpace(double age){return age>=WARN&&age<WORLD;}
}
