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
    public static final int LIT=1,CIRCLE=7,CROUCH=9,LEAP=22,RISEN=48,DIVE=62,LAND=68,FREE=80,END=91;
    /**
     * The rise is in two (2.128.3, the user: "원본 영상 보면 상승할때 한번 상승하고 멈추고 다시 한 번 상승하거든"; the
     * video, measured again: off the ground at 1.07 s to a low height within a sixth of a second, held there, a
     * second spring at about 1.85 s to the full height by 2.3 s, held until the dive): by {@link #HELD} they are
     * {@link #FIRST} of the way up and stop; at {@link #LEAP2} they spring again, and are as high as they go by RISEN.
     */
    public static final int HELD=28,LEAP2=37;
    public static final double FIRST=.4;
    /** How high they rise over where they stood (blocks; less under a roof). The user's, 2.128.2: "너무 높게 뛰어오른다 4칸 정도만 오르게" (it was 12, what I took the video's height for). */
    public static final double RISE=4;
    /** The circle: how far from where they stood the slam can come down (blocks, level). */
    public static final double RADIUS=10;
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
     * They go forward as they rise (2.128.4, the user: "애초에 원본 영상은 올라가면서 앞으로 이동까지 함", and how it is
     * to be: "그냥 G를 쓰면 앞으로 올라갔다가 내가 바라보는곳으로 떨어지면 된다고"): this far the way they faced as it
     * began by the top of the rise (blocks; less where something is in the way: the server's word), {@link #FIRST_AHEAD}
     * of it with the first spring, drifting {@link #DRIFT} of it on while they hang between the two.
     */
    public static final double FORWARD=3,FIRST_AHEAD=.36,DRIFT=.06;
    /** Between the two springs they sink this much of the rise, gathering for the second (not held dead still in the air). */
    public static final double DIP=.035;
    /**
     * How much of their rise they have at {@code age} ticks (0 on the ground, 1 as high as they go): off the ground at
     * LEAP at speed, slowing to a stop FIRST of the way up by HELD; sinking a little from there until LEAP2; then up
     * again at speed, slowing to a stop at the top by RISEN, and there until the dive.
     */
    public static double risen(double age){
        if(age<=LEAP)return 0;
        if(age<=LEAP2)return FIRST*sprung((age-LEAP)/(HELD-LEAP),2.4)-DIP*smooth(age,HELD-1,LEAP2);
        return FIRST-DIP+(1-FIRST+DIP)*sprung((age-LEAP2)/(RISEN-LEAP2),2.4);
    }
    /** How much of their way forward they have gone at {@code age} ticks (0 where they stood, 1 as far as they go): with each spring, a slow drift between. */
    public static double gone(double age){
        if(age<=LEAP)return 0;
        if(age<=LEAP2)return FIRST_AHEAD*sprung((age-LEAP)/(HELD-LEAP),1.7)+DRIFT*smooth(age,HELD-3,LEAP2);
        double at=gone(LEAP2);
        return at+(1-at)*sprung((age-LEAP2)/(RISEN-LEAP2),1.7);
    }
    /** A spring's way, 0 to 1 over its time: off at speed, slowing all the way to a stop (the more so the greater {@code power}). */
    private static double sprung(double u,double power){return 1-Math.pow(1-Math.max(0,Math.min(1,u)),power);}
    /** How far along the dive they are at {@code age} ticks (0 at its top, 1 landed): gathering speed all the way down. */
    public static double dived(double age){
        if(age<=DIVE)return 0;if(age>=LAND)return 1;
        double u=(age-DIVE)/(LAND-DIVE);
        return .25*u+.75*u*u;
    }
    /**
     * Where their feet are drawn at {@code age} ticks, {x,y,z}: where they stood ({@code from}) until the leap; up by
     * their rise ({@code rise} blocks) and forward by {@code ahead} ({x,z}: the way they faced, as far as they go)
     * over the two springs until the dive; down the straight line from there to where it lands ({@code land}; not yet
     * told, null: they hang at the top) by LAND; and there after.
     */
    public static double[] planned(double age,double[] from,double rise,double[] ahead,double[] land){
        double up=Math.min(age,DIVE),g=gone(up);
        double x=from[0]+ahead[0]*g,top=from[1]+rise*risen(up),z=from[2]+ahead[1]*g;
        if(age<=DIVE||land==null)return new double[]{x,top,z};
        double d=dived(age);
        return new double[]{x+(land[0]-x)*d,top+(land[1]-top)*d,z+(land[2]-z)*d};
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
