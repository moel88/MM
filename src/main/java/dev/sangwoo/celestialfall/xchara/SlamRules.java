package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's G with their hack off, the slam (2.128.0, user request with their video of it, "G - Overwrite Slam":
 * "이번에는 q 비활성화 g스킬 효과음은 영상에 있고 스킬은 영상처럼 검이 q활성화 상태 색깔로 변하고 보라색 네온 빛 원을
 * 소환하고 공중에 올라가서 내가 보는 방향으로 내려찍는 스킬이야". What I read of the video they confirmed, "맞음, 이대로";
 * where it comes down: "화면 가운데가 가리키는 땅"; the great circle: "떨어질 수 있는 범위"; and "피해 50 · 60초 · 가드
 * 불가"). A skill of the knife's pipeline (XCharaCombo.SLAM, XCharaAttack.slam), as the cleave and the jab:
 * <ol>
 * <li>the knife lights up as the hack's does ({@link #LIT}); a great flat circle of purple neon comes on the ground
 * about them ({@link #CIRCLE}, {@link #RADIUS} blocks: as far as the slam can come down);</li>
 * <li>they crouch, the knife low ({@link #CROUCH}), and at {@link #LEAP} rise straight up, slowing, to
 * {@link #RISE} blocks over where they stood by {@link #RISEN} (less under a roof: the server's word), squares of
 * light rising off the circle; up there they look about;</li>
 * <li>at {@link #DIVE} where they come down is settled (the ground the middle of their screen points at, their
 * client's word, kept within the circle: the server finds the floor there), and they dive on it, the knife under
 * them, landing at {@link #LAND}: everyone within {@link #BLAST} blocks of there is struck for {@link #DAMAGE}, no
 * guard taking it, without knockback, and can do nothing for {@link #STUN} ticks (the video's 0.45 s);</li>
 * <li>they are free at {@link #FREE}; at {@link #END} the circle is gone and the knife is its own black again, and
 * its cooldown ({@link #COOLDOWN}, the user's 60 s) runs from then.</li>
 * </ol>
 * The rise and the dive are drawn only ({@link #planned}: every client draws the body there, over where it really
 * stands until the landing takes it to where it came down, as Lee So-sang's ride round the sky): in the air nothing
 * harms them (what bypasses invulnerability aside). Before the leap, staggered or incapacitated, it stops short and
 * its cooldown runs from then. Ticks of 1/20 s from the press; the times are the video's.
 */
public final class SlamRules {
    private SlamRules(){}
    /** When the knife lights, when the circle comes, when they are down in their crouch, when they leave the ground, when they are as high as they go, when the dive begins (where it lands is settled), when it lands, when they are free, and when it is over (the circle gone). */
    public static final int LIT=1,CIRCLE=7,CROUCH=9,LEAP=22,RISEN=60,DIVE=62,LAND=68,FREE=80,END=91;
    /** How high they rise over where they stood (blocks; less under a roof). */
    public static final double RISE=12;
    /** The circle: how far from where they stood the slam can come down (blocks, level). */
    public static final double RADIUS=18;
    /** Whom the landing strikes: within this far of it, level (blocks; and half their width), from this far under it to this far over it. */
    public static final double BLAST=3.5,BLAST_LOW=-1.5,BLAST_HIGH=3;
    /** What it deals (the user's), how long whom it strikes can do nothing (ticks: the video's 0.45 s; their legs stopped as long, wholly), and its cooldown from its end (ticks: the user's 60 s). */
    public static final float DAMAGE=50;
    public static final int STUN=9,COOLDOWN=1200;
    public static final double SLOW=1;
    /** How far under and over where they stood the floor it lands on may be (blocks). */
    public static final double FLOOR_UNDER=16,FLOOR_OVER=RISE;
    /** Their client tells where the middle of its screen points from this many ticks before the dive, every tick, until it hears where the dive goes. */
    public static final int AIM_FROM=DIVE-10;

    private static double smooth(double x,double a,double b){double u=Math.max(0,Math.min(1,(x-a)/(b-a)));return u*u*(3-2*u);}
    /**
     * How much of their rise they have at {@code age} ticks (0 on the ground, 1 as high as they go): off the ground at
     * LEAP at speed, slowing all the way up (the video's climb, still drifting up as the dive begins).
     */
    public static double risen(double age){
        if(age<=LEAP)return 0;
        double u=Math.min(1,(age-LEAP)/(RISEN-LEAP));
        return 1-Math.pow(1-u,2.4);
    }
    /** How far along the dive they are at {@code age} ticks (0 at its top, 1 landed): gathering speed all the way down. */
    public static double dived(double age){
        if(age<=DIVE)return 0;if(age>=LAND)return 1;
        double u=(age-DIVE)/(LAND-DIVE);
        return .25*u+.75*u*u;
    }
    /**
     * Where their feet are drawn at {@code age} ticks, {x,y,z}: where they stood ({@code from}) until the leap; over it
     * by their rise ({@code rise} blocks) until the dive; down the straight line to where it lands ({@code land}; not
     * yet told, null: they hang at the top) by LAND; and there after.
     */
    public static double[] planned(double age,double[] from,double rise,double[] land){
        double top=from[1]+rise*risen(Math.min(age,DIVE));
        if(age<=DIVE||land==null)return new double[]{from[0],top,from[2]};
        double d=dived(age);
        return new double[]{from[0]+(land[0]-from[0])*d,top+(land[1]-top)*d,from[2]+(land[2]-from[2])*d};
    }
    /** A point brought within the circle about {@code from}, level: {x,z} at most RADIUS from it (short of it by {@code margin}). */
    public static double[] within(double fromX,double fromZ,double x,double z,double margin){
        double dx=x-fromX,dz=z-fromZ,far=Math.hypot(dx,dz),most=RADIUS-margin;
        if(!(far>most))return new double[]{Double.isFinite(x)?x:fromX,Double.isFinite(z)?z:fromZ};
        return new double[]{fromX+dx/far*most,fromZ+dz/far*most};
    }
    /** Whether one standing ({@code dx}, {@code dy}, {@code dz}) from where it lands, {@code half} wide from their middle, is struck. */
    public static boolean struck(double dx,double dy,double dz,double half){
        return dy>=BLAST_LOW&&dy<=BLAST_HIGH&&dx*dx+dz*dz<=(BLAST+half)*(BLAST+half);
    }
    /** Whether they are in the air at {@code age} ticks: from the leap until the landing (nothing harms them then). */
    public static boolean airborne(long age){return age>=LEAP&&age<LAND;}
    /** Whether a blow can still stop it short at {@code age} ticks: before the leap. */
    public static boolean charging(long age){return age>=0&&age<LEAP;}
    /** When it is ready again after one begun at {@code start}. */
    public static long ready(long start){return start+END+COOLDOWN;}
}
