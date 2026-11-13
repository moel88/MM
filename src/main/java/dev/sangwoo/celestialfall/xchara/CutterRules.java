package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's E with their hack off, the three cuts (2.129.0, user request with their video of it, Roblox's "E - Edge
 * Cutter": "이번에는 q비활성화 e스킬 효과음은 영상에 2번 재생되고 스킬은 영상처럼 앞으로 짧게 이동하면서 3번 베는 스킬이야
 * 적이 바로 앞에 있으면 이동하지 않고 3번 베기만 해"; what I read of the video they confirmed with one change, "다 맞는데 그냥
 * 4타처럼 넉백이 있는거임 위로 떠오르는게 아니고", and they chose "합 24 (8씩)", "12초" and "가드로 막힘"). A skill of the
 * knife's pipeline (XCharaCombo.CUTTER and CUTTER_DASH, XCharaAttack.cutter), as the cleave, the jab and the slam:
 * <ul>
 * <li>with no one right ahead ({@link #CLOSE}) they go a short way forward first ({@link #DASH} blocks at most over
 * {@link #DASH_TO} ticks, stopping {@link #KEEP} short of the first one in their way, and before a block or a drop
 * they cannot stand under): XCharaCombo.CUTTER_DASH, its cuts {@link #LEAD} ticks later; with someone right ahead
 * they cut where they stand: XCharaCombo.CUTTER;</li>
 * <li>three cuts, {@link #EVERY} ticks apart (the video's quarter of a second), {@link #DAMAGE} each on everyone in
 * their way ahead: a guard takes them, no knockback, and each staggers whom it strikes until the next could land (as
 * a basic attack's: the video's "stunned" runs from the first cut to a little past the third);</li>
 * <li>the third throws whom it strikes the way it went, as the basic attack's fourth does (XCharaCombo.FLY);</li>
 * <li>its cooldown ({@link #COOLDOWN}) runs from its end; cut short before its first cut, from then.</li>
 * </ul>
 * The going forward is drawn, not walked (as the slam's rise and dive): the server keeps its user where they began
 * and takes them to where it ends at {@link #DASH_TO}; every client draws the body along {@link #planned} meanwhile.
 * Pure timing and reach, shared by the server and the clients; ticks of 1/20 s. The distances, the reach and the times
 * the video does not show are mine.
 */
public final class CutterRules {
    private CutterRules(){}
    /** Each cut's damage (the user's: 24 in all), and its cooldown from its end (ticks: 12 s, the user's). */
    public static final float DAMAGE=8;
    public static final int COOLDOWN=240;
    /** The tick into the skill each cut lands on where they stand, and how many ticks apart they are. */
    public static final int FIRST=4,EVERY=5;
    /** How many ticks later every cut is when they go forward first; the going is over (and they are there) by DASH_TO. */
    public static final int LEAD=4,DASH_TO=6;
    /** How far forward they go at most (blocks). */
    public static final double DASH=3;
    /** Someone whose near side is this near ahead of them (blocks) is right ahead: they cut where they stand. */
    public static final double CLOSE=2.6;
    /** They stop this far short of the near side of the first one in their way (blocks: the cuts' own reach is past it). */
    public static final double KEEP=1.7;
    /** How far to either side of their way someone is in it (blocks, beyond half their own width). */
    public static final double PATH=1.2;
    /** A going shorter than this is none (blocks): they cut where they stand. */
    public static final double LEAST=.4;
    /** Where the cuts reach: from how near ahead of their feet, how far, and how far to either side (blocks). */
    public static final double[] REACH={-.4,3.6,2.0};
    /** How far down from where they stand the floor they come to may be, and how far up (blocks: a low step). */
    public static final double DOWN=3,UP=.6;

    /**
     * How far forward they go (blocks; 0: they cut where they stand): with the near side of the first one in their way
     * {@code ahead} blocks ahead of their feet (NaN: no one) and nothing in the way for {@code clear} blocks.
     */
    public static double dash(double ahead,double clear){
        if(!Double.isNaN(ahead)&&ahead<=CLOSE)return 0;
        double far=Math.min(DASH,Math.max(0,clear));
        if(!Double.isNaN(ahead))far=Math.min(far,ahead-KEEP);
        return far<LEAST?0:far;
    }
    /** How much of the way they have gone {@code age} ticks in (0 to 1): quick off the mark, slowing in. */
    public static double gone(double age){
        double u=Math.max(0,Math.min(1,age/DASH_TO));
        return 1-(1-u)*(1-u);
    }
    /** Where their feet are drawn {@code age} ticks in, going from {@code from} to {@code to}: {x, y, z}. */
    public static double[] planned(double age,double[] from,double[] to){
        double g=gone(age);
        // (a floor higher or lower where they end: taken over the last of the way)
        double gy=Math.max(0,Math.min(1,(g-.5)*2));gy=gy*gy*(3-2*gy);
        return new double[]{from[0]+(to[0]-from[0])*g,from[1]+(to[1]-from[1])*gy,from[2]+(to[2]-from[2])*g};
    }
    /** When it is ready again: its cooldown after one of {@code combo} begun at {@code start} is over. */
    public static long ready(int combo,long start){return start+XCharaCombo.LENGTH[combo]+COOLDOWN;}
}
