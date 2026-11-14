package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's T with their hack off, the three small knives thrown (2.131.0, user request with their video of it, Roblox's
 * "T - Corruption Knives": "이제 T스킬 효과음은 영상 참고하고 2번 재생 돼 t스킬은 영상처럼 간단하게 다른손으로 작은 나이프
 * 3개를 던지는 기술이야"; what I read of the video they confirmed, "맞음, 이대로", and they chose "합 12 (4씩) · 5초",
 * "평타처럼 짧은 경직만" and "가드 불가"). A skill of the knife's pipeline (XCharaCombo.KNIVES, XCharaAttack.knives), as the
 * four knives shot (ShotRules): its "blows" are its knives leaving the hand, not landing:
 * <ul>
 * <li>three small purple knives come to be in the hand that holds no knife ({@link #APPEAR}), it is raised to the
 * shoulder ({@link #RAISED}) and thrown forward, the body going down into it; the knives leave it one after another
 * ({@link #FIRE}: the video's, some 0.1 s apart), each straight at where its user looks as it goes (the point the
 * middle of their screen is on, their client's word: XCharaRules.AIM; without it, where their look meets the
 * world);</li>
 * <li>a knife flies {@link #SPEED} blocks a tick (the video's cross some five blocks between two frames) as far as
 * {@link #RANGE} or the first block, and strikes the first body its point comes to (within {@link #GIRTH} of it):
 * {@link #DAMAGE}, no guard takes it, no knockback, a basic attack's stagger; it goes no further;</li>
 * <li>its user stands where they are until the last has gone and a little ({@link #HOLD}); its cooldown
 * ({@link #COOLDOWN}) runs from its end ({@link #LENGTH}); cut short before the first knife goes, from then (the
 * knives not yet thrown are gone; those in flight fly on).</li>
 * </ul>
 * Pure timing and geometry, shared by the server and the clients; ticks of 1/20 s. The times are the video's as near as
 * ticks come (its second use: its sound begins at 2.37 s of its picture, which is the press; the hand lit at 2.50,
 * raised at 2.56, thrown from 2.60, the first knife in flight at 2.71, a third of a second after the press, the strikes
 * some 0.08 s apart); the speed, the range, where the knives leave from and the stagger are mine.
 */
public final class KnivesRules {
    private KnivesRules(){}
    /** How many knives, each one's damage (the user's: 12 in all), and its cooldown from its end (ticks: 5 s, the user's). */
    public static final int KNIVES=3;
    public static final float DAMAGE=4;
    public static final int COOLDOWN=100;
    /** The tick into it the knives come to be in the hand, the tick the hand is up by the shoulder, and the tick each knife leaves it. */
    public static final int APPEAR=1,RAISED=4;
    public static final int[] FIRE={6,8,10};
    /** Its user is held this long (ticks), and it is over at LENGTH. */
    public static final int HOLD=12,LENGTH=15;
    /** How fast a knife flies (blocks a tick: 80 a second), and how far at most (blocks, its point from where it left). */
    public static final double SPEED=4,RANGE=20;
    /** A small knife's length, its point this far ahead of where it is placed (its tail's end; blocks). */
    public static final double TIP=.62;
    /** A body is struck when the knife's point passes within this of it (blocks). */
    public static final double GIRTH=.3;
    /** How long a knife's strike staggers whom it strikes (ticks): until the next could come and a little more; the last's. */
    public static final int STAGGER=4,STAGGER_LAST=6;
    /**
     * Where each knife leaves from, its tail {ahead, up, to the right} of where its user stood facing as it began
     * (blocks): the hand that holds no knife (the left: the knife is drawn in the right) thrown out ahead of them, low,
     * the body gone down into the throw; the three apart, as the hand sweeps on forward and down. (Where the clients' body has
     * that hand then: client/xchara/CorruptionKnives, its test holds the two together.)
     */
    public static final double[][] SPOTS={{.83,1.22,-.53},{1.14,.97,-.22},{1.19,.82,-.11}};

    /** Where knife {@code k} leaves from for a user who stood at {@code feet} facing {@code yaw} (degrees, the game's): {x, y, z}. */
    public static double[] spot(double[] feet,float yaw,int k){
        double r=Math.toRadians(yaw),fx=-Math.sin(r),fz=Math.cos(r);double[] s=SPOTS[k];
        return new double[]{feet[0]+fx*s[0]-fz*s[2],feet[1]+s[1],feet[2]+fz*s[0]+fx*s[2]};
    }
    /** How far along its way from where it left a knife's point is {@code age} ticks after (blocks). */
    public static double head(double age){return TIP+SPEED*Math.max(0,age);}
    /** The stretch of its way the server looks over for a body {@code n} ticks after a knife left: {from, to} (blocks; a tick ahead of where its point is drawn, as ShotRules.swept). */
    public static double[] swept(int n){return new double[]{n<=0?0:TIP+SPEED*n,TIP+SPEED*(n+1)};}
    /** How many ticks after it left a knife's point is drawn {@code far} along its way. */
    public static double reached(double far){return Math.max(0,(far-TIP)/SPEED);}
    /** The tick into the skill knife {@code k}'s flight is over at the latest (as far as it can go). */
    public static double over(int k){return FIRE[k]+reached(RANGE);}
    /** When it is ready again: its cooldown after one begun at {@code start} is over. */
    public static long ready(long start){return start+LENGTH+COOLDOWN;}
    /** Whether the knives are in the hand {@code age} ticks in, and whether knife {@code k} has left it. */
    public static boolean there(double age){return age>=APPEAR;}
    public static boolean gone(int k,double age){return age>=FIRE[k];}
}
