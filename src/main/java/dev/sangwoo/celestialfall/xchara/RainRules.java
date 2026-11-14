package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's H with their hack off, the rain of knives (2.132.0; 2.132.5, user request: "20블록으로 줄이고 떨어지는 칼들도
 * 대폭 줄이고 떨어지는 속도도 줄여 진짜 비 처럼 만들어 그리고 적을 찌르는건 없애고 그냥 비만 떨어지게 비는 30초동안 계속
 * 떨어져"; asked, they chose where it rains "쓴 자리에 고정", its harm "몸에 떨어진 칼마다 1", its user "자유롭게 움직이고
 * 싸움" and the screen inside it "은은하게 30초 내내"). No run and no stab any more (2.132.0 to 2.132.4 had them): H calls
 * the rain, and that is all of it.
 * <ul>
 * <li>The call ({@link XCharaCombo#RAIN}, a skill of the knife's pipeline): the knife raised to the sky, its user held
 * where they stand {@link #HOLD} ticks, over at {@link #LENGTH}; at {@link #CALL} the rain is called (its user struck
 * or held before that, it is not, and its cooldown runs from then).</li>
 * <li>The rain (XCharaRain): fixed where its user stood as H was pressed, {@link #ZONE} blocks round. From
 * {@link #OPEN} ticks after the call, for {@link #POUR} ticks (30 s), knives lit as the hack lights them come out of a
 * circle in the sky ({@link #SKY} over its middle) one after another anywhere over it ({@link #spot}), {@link #PER} a
 * tick at full (coming on over the first {@link #RAMP} ticks and dying away over the last), and fall straight down at
 * {@link #SPEED} blocks a tick, as fast all the way as rain falls, until they meet a body or the ground. Each that
 * meets a body strikes it once: {@link #DAMAGE} (no guard takes it, it comes down from over them; no knockback, no
 * stagger); its user's own break on them and do nothing. Its user is free once the call is over: it rains on whatever
 * they do.</li>
 * </ul>
 * Its cooldown ({@link #COOLDOWN}) runs from its end ({@link #END}, what came last having landed); the rain cut short
 * (its user dead or gone, or X Chara's no more), from then. All here but the user's (20 blocks, 30 s, 1 for each knife
 * that lands on a body, where it rains, its user free; 60 s and no guard from 2.132.0) is mine.
 * Pure timing and geometry, shared by the server and the clients and the same on every machine (integer hashes,
 * StrictMath); ticks of 1/20 s, counted from the call.
 */
public final class RainRules {
    private RainRules(){}
    // ───────────────────────────── the call ─────────────────────────────
    /** The tick into the call the rain is called (the knife's light goes up into the sky), how long it holds its user, and how long it runs. */
    public static final int CALL=8,HOLD=14,LENGTH=20;

    // ───────────────────────────── the rain ─────────────────────────────
    /** The skill the clients draw a rain as (KagenoSkills: a cast of its own, as long as it rains). */
    public static final int SKILL=16;
    /** How far out from its middle it rains (blocks: the user's 20). */
    public static final double ZONE=20;
    /**
     * The ticks after the call the first knife comes out of the sky (its circle opening first), how long knives keep
     * coming (the user's 30 s), and over how many ticks of each end of that it comes on and dies away.
     */
    public static final int OPEN=6,POUR=600,RAMP=20;
    /** How many knives come a tick at full (2.132.5, "떨어지는 칼들도 대폭 줄이고": some 350 in the air at once; 2.132.4 had some 2,270), and how many in all. */
    public static final int PER=16,BLADES=PER*(POUR-RAMP);
    /** How high over its middle a knife's point comes out of the sky (blocks), and how much higher at most. */
    public static final double SKY=10,SKY_MORE=1;
    /** How fast a knife falls (blocks a tick: 11 a second, as fast all the way; 2.132.4's came down at some 1.7 at the last, "떨어지는 속도도 줄여"). */
    public static final double SPEED=.55;
    /** How far under its middle the ground is looked for (blocks): over a deeper drop than this no knife comes. */
    public static final double DEEP=12;
    /** How near a body (level, blocks beyond its box) a knife's point comes down on it. */
    public static final double GIRTH=.3;
    /** What a knife does to a body it lands on (the user's: "몸에 떨어진 칼마다 1"). */
    public static final float DAMAGE=1;
    /** Its cooldown from its end (ticks: 60 s, the user's from 2.132.0). */
    public static final int COOLDOWN=1200;
    /** How long a knife falls from the sky to its middle's height at most (ticks); the ticks after the call it is over, what came last landed there (its cooldown runs from then). */
    public static final int FALL=(int)Math.ceil((SKY+SKY_MORE)/SPEED),END=OPEN+POUR+FALL;
    /** The ticks after the call the last knife that could fall furthest (to DEEP under its middle) has landed: the server looks no longer. */
    public static final int LAST=OPEN+POUR+(int)Math.ceil((SKY+SKY_MORE+DEEP)/SPEED)+1;

    /**
     * How many knives have come by {@code t} ticks after the call (0 to BLADES): PER a tick at full, coming on (the rate
     * rising evenly) over the first RAMP ticks of the pour and dying away over the last.
     */
    public static double count(double t){
        double u=t-OPEN;
        if(u<=0)return 0;
        if(u>=POUR)return BLADES;
        if(u<RAMP)return PER*u*u/(2.0*RAMP);
        if(u<=POUR-RAMP)return PER*(u-RAMP/2.0);
        double v=POUR-u;return BLADES-PER*v*v/(2.0*RAMP);
    }
    /** The ticks after the call knife {@code k} comes out of the sky (the middle of its share of {@link #count}: one after another, never two at once). */
    public static double comes(int k){
        double n=k+.5,u;
        if(n<=PER*RAMP/2.0)u=Math.sqrt(2.0*RAMP*n/PER);
        else if(n<=BLADES-PER*RAMP/2.0)u=n/PER+RAMP/2.0;
        else u=POUR-Math.sqrt(2.0*RAMP*Math.max(0,BLADES-n)/PER);
        return OPEN+u;
    }
    /** The knives that may be falling (or have just landed) at {@code t}: from those that came {@code back} ticks before it to the last that has come ([from, to) of their indices). */
    public static int[] window(double t,double back){
        int from=Math.max(0,(int)Math.floor(count(t-back))-1),to=Math.min(BLADES,(int)Math.ceil(count(t))+1);
        return new int[]{from,Math.max(from,to)};
    }
    /** A hash of knife {@code k} of the rain seeded {@code seed} (0 to 1; integers only, so the same on every machine). */
    public static double h(int k,int seed,int salt){
        int x=k*0x9E3779B1+seed*0x85EBCA77+salt*0xC2B2AE3D;
        x^=x>>>16;x*=0x7FEB352D;x^=x>>>15;x*=0x846CA68B;x^=x>>>16;
        return (x>>>8)/(double)(1<<24);
    }
    /** The golden angle (radians): each knife comes down a turn of it on from the last. */
    static final double GOLDEN=2.399963229728653;
    /**
     * Where knife {@code k} of the rain seeded {@code seed} comes down, level from its middle {x, z}: anywhere over it,
     * evenly (as far out as a share of its ground drawn at random has it, a turn of the golden angle on from the last).
     */
    public static double[] spot(int k,int seed){
        double a=k*GOLDEN+h(k,seed,3)*.5,r=ZONE*Math.sqrt(h(k,seed,4));
        return new double[]{StrictMath.cos(a)*r,StrictMath.sin(a)*r};
    }
    /** How high over its middle knife {@code k}'s point comes out of the sky (blocks). */
    public static double sky(int k,int seed){return SKY+SKY_MORE*h(k,seed,5);}
    /** How high over its middle knife {@code k}'s point is {@code t} ticks after the call (it has fallen straight down at SPEED since it came; as high as it came, before). */
    public static double point(int k,int seed,double t){return sky(k,seed)-Math.max(0,t-comes(k))*SPEED;}
    /** The ticks after the call knife {@code k}'s point comes down to {@code y} over its middle. */
    public static double reaches(int k,int seed,double y){return comes(k)+(sky(k,seed)-y)/SPEED;}
    /** Whether a knife coming down at (x, z) comes down on a body standing over {@code minX..maxX}, {@code minZ..maxZ} (level): its point within GIRTH of its box. */
    public static boolean over(double x,double z,double minX,double maxX,double minZ,double maxZ){
        return x>=minX-GIRTH&&x<=maxX+GIRTH&&z>=minZ-GIRTH&&z<=maxZ+GIRTH;
    }
    /**
     * Where a knife falling from {@code from} down to {@code to} over a tick (its point's heights) first meets a body
     * {@code minY..maxY} high, the ground under it at {@code ground}: the height it strikes at (the top of the body, or
     * where its point is if it is in it already), or NaN if it meets it not (it misses its height, or lands on what is
     * over the body first, or no knife comes there: a NaN ground).
     */
    public static double meets(double from,double to,double minY,double maxY,double ground){
        if(Double.isNaN(ground)||maxY<to||minY>from)return Double.NaN;
        double at=Math.min(from,maxY);
        return at>ground?at:Double.NaN;
    }
    /** A seed for a rain called at {@code tick} by one whose id's halves are {@code most} and {@code least}. */
    public static int seed(long tick,long most,long least){return (int)(tick*31+17)^(int)(most>>>32)^(int)least;}
    /** When it is ready again after a call begun at {@code start}: its cooldown after its rain's end is over. */
    public static long ready(long start){return start+CALL+END+COOLDOWN;}
    /** How many knives a body standing still {@code w} by {@code d} blocks (level) in the rain all through it is struck by, as many as come over it on the mean (for the user: what it does). */
    public static double expected(double w,double d){return BLADES*(w+2*GIRTH)*(d+2*GIRTH)/(Math.PI*ZONE*ZONE);}
}
