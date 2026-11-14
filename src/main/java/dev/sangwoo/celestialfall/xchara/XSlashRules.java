package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's H with their hack off, the great X slash ("X 대참격", 2.134.0, user request: "X 대참격으로 날리고 맞은 사람은
 * 글리치에 걸리고 가드 못 하게 엑스차라가 가드에 너무 취약하니까 이런거 하나 넣어주면 좋을듯", the first of four I
 * recommended: the knife lit for a moment, two great cuts crossing, the X they make thrown ahead, and an X left
 * glitching on the ground it went over, bursting a second on; they chose "40+15 · 50초", "가드를 뚫고 맞음",
 * "8초 · 가드만 못 함" and "있는 소리 재사용"). A skill of the knife's pipeline (XCharaCombo.XSLASH, XCharaAttack.xslash),
 * as the jab, the slam, the three cuts, the knives and the terrain delete; its one "blow", at {@link #FIRE}, lets the X
 * go (XCharaXSlash flies it):
 * <ul>
 * <li>the knife lights as the hack's ({@link #LIT}) and cuts twice, the forehand from high right to low left and the
 * backhand from high left to low right; the two cuts stand crossed before them as an X, which is thrown the way they
 * faced as the second is done;</li>
 * <li>the X flies {@link #SPEED} blocks a tick, level, from {@link #NEAR} ahead of their feet as far as {@link #RANGE}
 * or the first wall in its way ({@link #BLOCK_UP} up), growing as it goes from {@link #HALF0} to {@link #HALF} blocks
 * to either side ({@link #half}); everyone it goes over ({@link #LOW} to {@link #HIGH} up) takes {@link #DAMAGE} once:
 * no guard takes it (a guard up is torn down), no knockback, no stagger; and is glitched ({@link #SEAL} ticks: no
 * guard can be raised, CombatState.sealed);</li>
 * <li>the ground it went over keeps an X (the two diagonals of its way, {@link #BAND} to either side of each, from
 * {@link #NEAR} to where it stopped: {@link #onMark}) glitching, which bursts {@link #WAIT} ticks after the X is gone
 * as far as it went ({@link #burstAt}): whoever stands on it then takes {@link #BURST_DAMAGE} (no guard takes it);</li>
 * <li>its user stands where they are until {@link #HOLD}; it is over at {@link #LENGTH}, its cooldown
 * ({@link #COOLDOWN}) running from then; cut short before the X goes, from then (no X).</li>
 * </ul>
 * Pure timing and geometry, shared by the server and the clients; ticks of 1/20 s. Way: ahead along the way they faced
 * as it began, to its right, up from their feet. The times, the sizes, the speed and the burst's reach are mine.
 */
public final class XSlashRules {
    private XSlashRules(){}
    /** The X's damage, the burst's (the user's "40+15"), and the cooldown from its end (ticks: 50 s, the user's). */
    public static final float DAMAGE=40,BURST_DAMAGE=15;
    public static final int COOLDOWN=1000;
    /** How long whom the X strikes is glitched (ticks: 8 s, the user's: "8초 · 가드만 못 함"). */
    public static final int SEAL=160;
    /** The knife lights; the first cut lands (the forehand); the second (the backhand); the X is let go. */
    public static final int LIT=1,CUT1=5,CUT2=9,FIRE=10;
    /** Its user is held this long (ticks), and it is over at LENGTH. */
    public static final int HOLD=16,LENGTH=24;
    /** The X flies this fast (blocks a tick: 40 a second), from this far ahead of its user's feet, this far at most. */
    public static final double SPEED=2,NEAR=2,RANGE=18;
    /** How far to either side it reaches as it goes (where it stood crossed before them) and once it has grown, and over how far it grows (blocks). */
    public static final double HALF0=2.5,HALF=5,GROW=3.4;
    /** How tall it stands, half of it (from its middle to a horn), and how high its middle is over their feet: as it goes and once it has grown (blocks; its lower horns at the ground). */
    public static final double TALL0=1.5,TALL=2.8;
    /** How far down and up from its user's feet it strikes (blocks: it stands taller than they are). */
    public static final double LOW=-1.2,HIGH=4.2;
    /** A wall this high over their feet in its way stops it (blocks: a step or a low ledge does not). */
    public static final double BLOCK_UP=1.6;
    /** The X on the ground: how far to either side of each diagonal it reaches (blocks), and how far down and up from their feet whom it bursts on stands. */
    public static final double BAND=1.0,MARK_LOW=-1.5,MARK_HIGH=2.5;
    /** The X on the ground bursts this long after the X in flight is gone as far as it went (ticks: the user's "1초 뒤"). */
    public static final int WAIT=20;

    private static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    /** How far it has grown at {@code fw} blocks ahead of its user's feet (0 as it goes, 1 full grown). */
    public static double grown(double fw){return smooth((fw-NEAR)/GROW);}
    /** How far to either side it reaches at {@code fw} blocks ahead (blocks). */
    public static double half(double fw){return HALF0+(HALF-HALF0)*grown(fw);}
    /** Half how tall it stands at {@code fw} blocks ahead, and so how high its middle is (blocks: its lower horns at the ground). */
    public static double tall(double fw){return TALL0+(TALL-TALL0)*grown(fw);}
    /** How far ahead of its user's feet the X is {@code age} ticks after it was let go (blocks; before it goes, where it stands). */
    public static double head(double age){return NEAR+SPEED*Math.max(0,age);}
    /** How many ticks after it was let go the X is {@code far} blocks ahead. */
    public static double reached(double far){return Math.max(0,(far-NEAR)/SPEED);}
    /** How long it flies when it goes as far as {@code reach} (ticks). */
    public static double flight(double reach){return reached(Math.max(NEAR,Math.min(RANGE,reach)));}
    /**
     * The stretch of its way the server looks over for bodies {@code n} ticks after it was let go: {from, to} (blocks
     * ahead of its user's feet). A tick ahead of where it is drawn ({@link #head}), so whoever it strikes is told of
     * before it is seen to reach them; as it goes, everything from its user's feet on.
     */
    public static double[] swept(int n){return new double[]{n<=0?0:head(n),head(n+1)};}
    /** The tick into it the X on the ground bursts, for an X that went as far as {@code reach}. */
    public static int burstAt(double reach){return FIRE+(int)Math.ceil(flight(reach)-1e-9)+WAIT;}
    /** When it is ready again: its cooldown after one begun at {@code start} is over. */
    public static long ready(long start){return start+LENGTH+COOLDOWN;}
    /**
     * Whether a body {@code halfWidth} across, {@code fw} ahead, {@code side} to the right and {@code up} over its user's
     * feet, is in the X's way between {@code from} and {@code to} blocks ahead.
     */
    public static boolean inWay(double fw,double side,double up,double halfWidth,double from,double to){
        if(up<LOW||up>HIGH)return false;
        if(fw+halfWidth<from||fw-halfWidth>to)return false;
        double at=Math.max(from,Math.min(to,fw));
        return Math.abs(side)<=half(at)+halfWidth;
    }
    /**
     * The X left on the ground by one that went as far as {@code reach}: its two diagonals, each {x0, z0, x1, z1} as
     * {ahead, to the right} of its user's feet: from near on the left to far on the right, and from near on the right to
     * far on the left.
     */
    public static double[][] diagonals(double reach){
        double far=Math.max(NEAR+.5,Math.min(RANGE,reach));
        return new double[][]{{NEAR,-HALF,far,HALF},{NEAR,HALF,far,-HALF}};
    }
    /** How far a point ({@code fw} ahead, {@code side} to the right) is from the nearer of the diagonals of one that went as far as {@code reach} (blocks). */
    public static double fromMark(double fw,double side,double reach){
        double best=Double.MAX_VALUE;
        for(double[] d:diagonals(reach)){
            double dx=d[2]-d[0],dz=d[3]-d[1],len2=dx*dx+dz*dz;
            double u=len2<1e-12?0:Math.max(0,Math.min(1,((fw-d[0])*dx+(side-d[1])*dz)/len2));
            best=Math.min(best,Math.hypot(fw-d[0]-dx*u,side-d[1]-dz*u));
        }
        return best;
    }
    /** Whether a body {@code halfWidth} across standing there ({@code up} over its user's feet) is on the X on the ground as it bursts. */
    public static boolean onMark(double fw,double side,double up,double halfWidth,double reach){
        if(up<MARK_LOW||up>MARK_HIGH)return false;
        return fromMark(fw,side,reach)<=BAND+halfWidth;
    }
    /** How much of diagonal {@code d} (0 to 1, from its near end) the X has drawn on the ground once it is {@code head} blocks ahead. */
    public static double drawn(double[] d,double head){
        double a=Math.min(d[0],d[2]),b=Math.max(d[0],d[2]);
        return b-a<1e-9?1:Math.max(0,Math.min(1,(head-a)/(b-a)));
    }
}
