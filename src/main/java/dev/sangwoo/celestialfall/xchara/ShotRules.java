package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's Y with their hack off, the four knives shot (2.130.0, user request with their video of it, Roblox's "Y -
 * Blade Shot": "이번에는 q비활성화 y스킬 효과음은 영상 참고하고 y스킬은 영상 보면 q활성화 상태의 검을 4개를 적에게 날리는
 * 스킬이야 영상에서는 살짝 끊기면서 나가는데 렉일뿐이고 그냥 나가면 돼"; what I read of the video they confirmed, "맞음,
 * 이대로", and they chose "합 32 (8씩) · 15초", "가드로 막힘" and, of where the knives go, "보는 방향으로 날아감"). A skill
 * of the knife's pipeline (XCharaCombo.SHOT, XCharaAttack.shot), as the cleave, the jab, the slam and the three cuts;
 * its "blows" are its knives let go, not landing:
 * <ul>
 * <li>four knives lit as the hack's come to be about its user, one after another ({@link #APPEAR}: by the
 * shoulder, over the head, low, and high on the other side: {@link #SPOTS}), hang there, and are let go one
 * after another ({@link #FIRE}), each straight at where its user looks as it goes (the point the middle of their
 * screen is on, their client's word: XCharaRules.AIM; without it, where their look meets the world);</li>
 * <li>a knife flies {@link #SPEED} blocks a tick as far as {@link #RANGE} or the first block, and strikes the first
 * body its point comes to (within {@link #GIRTH} of it): {@link #DAMAGE}, a guard takes it, no knockback, a basic
 * attack's stagger; it goes no further;</li>
 * <li>its user stands where they are until the last has gone ({@link #HOLD}); its cooldown ({@link #COOLDOWN}) runs
 * from its end ({@link #LENGTH}); cut short before the first knife goes, from then (the knives not yet gone are
 * gone; those in flight fly on).</li>
 * </ul>
 * Pure timing and geometry, shared by the server and the clients; ticks of 1/20 s. The video lagged (its knives hung
 * half a second before any went, then two went at once), so the times are mine: a knife every 0.15 s, each let go
 * 0.45 s after it came; the places, the speed, the range and the stagger are mine too.
 */
public final class ShotRules {
    private ShotRules(){}
    /** How many knives, each one's damage (the user's: 32 in all), and its cooldown from its end (ticks: 15 s, the user's). */
    public static final int BLADES=4;
    public static final float DAMAGE=8;
    public static final int COOLDOWN=300;
    /** The tick into it each knife comes to be on, and the tick each is let go on. */
    public static final int[] APPEAR={1,4,7,10},FIRE={10,13,16,19};
    /** Its user is held this long (ticks: until the last has gone), and it is over at LENGTH. */
    public static final int HOLD=21,LENGTH=26;
    /** How fast a knife flies (blocks a tick: 40 a second), and how far at most (blocks, its point from where it hung). */
    public static final double SPEED=2,RANGE=24;
    /** The knife's size of its model's (the size it is in the hand), and how far ahead of its grip (the middle of its rod, where it is placed) its point is then (blocks). */
    public static final double SCALE=2,TIP=(5.896/2+18.105)/16*SCALE;
    /** A body is struck when the knife's point passes within this of it (blocks). */
    public static final double GIRTH=.3;
    /** The point the knives are sent at is no nearer their user's eyes than this (blocks: one right before them is in the knives' way all the same). */
    public static final double NEAR=3;
    /** How long a knife's strike staggers whom it strikes (ticks): until the next could come and a little more; the last's. */
    public static final int STAGGER=5,STAGGER_LAST=6;
    /**
     * Where each knife hangs, its grip {ahead, up, to the right} of where its user stood facing as it began (blocks):
     * by the right shoulder, over the head to the left, low to the left, high to the right: about them, each blade's
     * middle beside or over them. (The video's first and last hang behind its user; its knives are a third the length
     * of ours, and ours behind them filled the game's camera, which looks on from there: the first capture.)
     */
    public static final double[][] SPOTS={{-1.3,1.5,1.15},{-1.2,2.7,-.55},{-1.0,.55,-1.15},{-1.4,3.05,.6}};

    /** Where knife {@code k}'s grip hangs for a user who stood at {@code feet} facing {@code yaw} (degrees, the game's): {x, y, z}. */
    public static double[] spot(double[] feet,float yaw,int k){
        double r=Math.toRadians(yaw),fx=-Math.sin(r),fz=Math.cos(r);double[] s=SPOTS[k];
        return new double[]{feet[0]+fx*s[0]-fz*s[2],feet[1]+s[1],feet[2]+fz*s[0]+fx*s[2]};
    }
    /**
     * The way a knife hanging at {@code spot} goes for a user whose eyes are at {@code eye}, sent at {@code want} (unit
     * {x, y, z}): straight at it; a point nearer their eyes than {@link #NEAR} is taken as far out along their look.
     */
    public static double[] way(double[] spot,double[] eye,double[] want){
        double ex=want[0]-eye[0],ey=want[1]-eye[1],ez=want[2]-eye[2],far=Math.sqrt(ex*ex+ey*ey+ez*ez);
        double tx=want[0],ty=want[1],tz=want[2];
        if(far<1e-6){tx=spot[0];ty=spot[1];tz=spot[2]+1;}
        else if(far<NEAR){double k=NEAR/far;tx=eye[0]+ex*k;ty=eye[1]+ey*k;tz=eye[2]+ez*k;}
        double dx=tx-spot[0],dy=ty-spot[1],dz=tz-spot[2],len=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(len<1e-6)return new double[]{0,0,1};
        return new double[]{dx/len,dy/len,dz/len};
    }
    /** The way a knife points while it hangs, before its user's look is told: level, the way they faced (unit {x, y, z}). */
    public static double[] hangs(float yaw){double r=Math.toRadians(yaw);return new double[]{-Math.sin(r),0,Math.cos(r)};}
    /** How far along its way from where it hung a knife's point is {@code age} ticks after it was let go (blocks; before it goes, where it hangs). */
    public static double head(double age){return TIP+SPEED*Math.max(0,age);}
    /**
     * The stretch of its way the server looks over for a body {@code n} ticks after a knife was let go: {from, to}
     * (blocks from where it hung). A tick ahead of where its point is drawn ({@link #head}), so whoever it strikes is
     * told of before the knife is seen to reach them; as it goes, everything from its grip on.
     */
    public static double[] swept(int n){return new double[]{n<=0?0:TIP+SPEED*n,TIP+SPEED*(n+1)};}
    /** How many ticks after it was let go a knife's point is drawn {@code far} along its way. */
    public static double reached(double far){return Math.max(0,(far-TIP)/SPEED);}
    /** The tick into the skill knife {@code k}'s flight is over at the latest (as far as it can go). */
    public static double over(int k){return FIRE[k]+reached(RANGE);}
    /** When it is ready again: its cooldown after one begun at {@code start} is over. */
    public static long ready(long start){return start+LENGTH+COOLDOWN;}
    /** Whether knife {@code k} has come to be {@code age} ticks in, and whether it has been let go. */
    public static boolean there(int k,double age){return age>=APPEAR[k];}
    public static boolean gone(int k,double age){return age>=FIRE[k];}
}
