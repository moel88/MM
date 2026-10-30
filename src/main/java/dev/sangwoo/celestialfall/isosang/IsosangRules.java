package dev.sangwoo.celestialfall.isosang;

/**
 * Lee So-sang's basic attack, four hits with the sword (2.77.0, user request: "이제 평타부터 만들자 영상 보내줄게 1타 2타
 * 3타 4타 다 들어있어 영상에 평타는 넉백이 없게 만들어줘"), after the user's video:
 * <ol>
 * <li>a big diagonal cut, the sword raised high and brought down from the right shoulder across the front to the left
 * hip with a step in;</li>
 * <li>a turn on the spot with the blade held out level, a full circle round them (it strikes all round);</li>
 * <li>a long thrust straight ahead, the sword pulled back with a twist, two cuts crossing in an X and a rising sweep:
 * four blows;</li>
 * <li>the finisher: the sword twirled overhead, the free hand pointed at the foe, a lunge and a great level cut across
 * the front that bursts into ink.</li>
 * </ol>
 * No blow throws or pushes anyone (user request): whom they strike stay where they stand, staggered as every basic
 * attack's blow staggers (Stagger.isosang). The fourth cannot be guarded, as the other characters' fourth hits. Each hit
 * goes the way its user faced as it began and holds them where they stand for {@link #HOLD} ticks. Pure timing and reach,
 * shared by the server and the clients; ticks of 1/20 s.
 */
public final class IsosangRules {
    private IsosangRules(){}
    /** The swings' number among the skills' visuals on the clients (KagenoSkills): past every Kageno skill's. */
    public static final int SKILL=15;
    /**
     * The ticks into hit {@code n} its blows land on: the cut at 0.3 s; the turn's blade passing the front at 0.3 s; the
     * thrust at 0.25 s, the cross's two cuts at 0.45 and 0.55 s and the sweep at 0.7 s; the finisher at 0.5 s, after the
     * twirl, the aim and the lunge.
     */
    private static final int[][] BLOWS={{},{6},{6},{5,9,11,14},{10}};
    /** Each blow's damage: 6, 6, 7 (2 + 1.5 + 1.5 + 2) and 10, 29 for the whole combo (Kageno's 28). */
    private static final float[][] DAMAGE={{},{6},{6},{2,1.5f,1.5f,2},{10}};
    /** Ticks from a hit's start before the next may start: its last blow has landed (the turn has come all the way round). */
    public static final int[] STEP={0,8,10,16,0};
    /** Ticks a hit holds its user from its start: no walking, guard, dash or other move until then. */
    public static final int[] HOLD={0,9,10,16,14};
    /** How long each hit runs (ticks) until its user is back in their stance: the clients draw its motion this long. */
    public static final int[] LENGTH={0,16,16,22,30};
    /** A press this many ticks or fewer before the next hit may start is kept, and starts it then. */
    public static final int BUFFER=8;
    /** The next hit must start within this many ticks of when it may, or the combo breaks. */
    public static final int GRACE=24;
    /** The rest once the fourth hit is over, or once a late hit broke the combo (ticks), as the other characters' attacks. */
    public static final int REST=24;

    /** How many blows hit {@code combo} lands (1 to 4). */
    public static int blows(int combo){return combo>=1&&combo<=4?BLOWS[combo].length:0;}
    /** The tick into hit {@code combo} its blow {@code i} lands on. */
    public static int blowAt(int combo,int i){return BLOWS[combo][i];}
    /** The tick into hit {@code combo} its last blow lands on. */
    public static int lastBlow(int combo){return BLOWS[combo][BLOWS[combo].length-1];}
    /** The damage of hit {@code combo}'s blow {@code i}. */
    public static float damage(int combo,int i){return DAMAGE[combo][i];}
    /** All of hit {@code combo}'s blows together. */
    public static float total(int combo){float t=0;for(float d:DAMAGE[combo])t+=d;return t;}
    /** The hit after {@code combo}: the next of the combo, or the first again after the fourth (or none). */
    public static int next(int combo){return combo>=1&&combo<4?combo+1:1;}
    /** When the hit after {@code combo} (begun at {@code start}) may start. */
    public static long nextAt(int combo,long start){return start+STEP[combo];}
    /**
     * The tick the attack is ready again after the combo whose hit {@code combo} began at {@code start}, if it broke by
     * {@code now} (the next hit did not start in time), or -1 while it holds (or has nothing to break: none yet, or the
     * fourth hit ended it).
     */
    public static long broken(int combo,long start,long now){
        if(combo<1||combo>=4)return -1;
        long late=nextAt(combo,start)+GRACE;
        return now>late?late+1+REST:-1;
    }
    /** The tick the attack is ready again after the fourth hit begun at {@code start}: once it is over, a rest on. */
    public static long rested(long start){return start+LENGTH[4]+REST;}
    /** Whether hit {@code combo} begun at {@code start} still holds its user at {@code now}. */
    public static boolean holding(int combo,long start,long now){return combo>=1&&combo<=4&&now>=start&&now<start+HOLD[combo];}
    /**
     * The tick a blow staggers whom it strikes until (Stagger.isosang): the attacker's next blow at full speed (the next
     * of the same hit, else the next hit's first, started as soon as it may) and Stagger.PAST ticks more; the fourth hit's
     * blow staggers them {@code last} ticks. {@code past} is Stagger.PAST.
     */
    public static long staggerUntil(int combo,int blow,long start,long now,int past,int last){
        if(combo>=4)return now+last;
        long next=blow+1<blows(combo)?start+blowAt(combo,blow+1):nextAt(combo,start)+blowAt(combo+1,0);
        return Math.max(now,next)+past;
    }
    /** Where hit {@code combo}'s blows reach (blocks from its user's feet): a body this close in counts as in the way. */
    public static final double NEAR=-.3,LOW=-1.2,HIGH=2.8;
    /** The cut's reach ahead and to either side; the turn's radius all round; the thrust's reach and its narrow way. */
    public static final double CUT_REACH=3.4,CUT_HALF_WIDTH=1.9,SPIN_RADIUS=3.3,THRUST_REACH=4.8,THRUST_HALF_WIDTH=.75;
    /** The cross's and the sweep's reach and width; the finisher's (a great level cut across the front). */
    public static final double CROSS_REACH=3.4,CROSS_HALF_WIDTH=1.6,SWEEP_REACH=3.6,SWEEP_HALF_WIDTH=2.0,FINISH_REACH=4.8,FINISH_HALF_WIDTH=2.6;
    /** How far ahead of its user's feet hit {@code combo}'s blow {@code blow} reaches (blocks; the turn's radius all round). */
    public static double reach(int combo,int blow){
        return combo==2?SPIN_RADIUS:combo==3?(blow==0?THRUST_REACH:blow<3?CROSS_REACH:SWEEP_REACH):combo==4?FINISH_REACH:CUT_REACH;
    }
    /** How far to either side of the way hit {@code combo}'s blow {@code blow} reaches (blocks). */
    public static double halfWidth(int combo,int blow){
        return combo==2?SPIN_RADIUS:combo==3?(blow==0?THRUST_HALF_WIDTH:blow<3?CROSS_HALF_WIDTH:SWEEP_HALF_WIDTH):combo==4?FINISH_HALF_WIDTH:CUT_HALF_WIDTH;
    }
    /** How near (blocks ahead of its user's feet, behind them below 0) hit {@code combo}'s blow {@code blow} begins to reach: the thrust only ahead. */
    public static double near(int combo,int blow){return combo==3&&blow==0?.1:NEAR;}
    /**
     * Whether a point (ahead along the hit, to its right and up from its user's feet, blocks) is in the way of hit
     * {@code combo}'s blow {@code blow}, for a body {@code halfWidth} across: a lane ahead, the turn's a circle all round.
     */
    public static boolean lane(int combo,int blow,double forward,double side,double height,double halfWidth){
        if(height<LOW||height>HIGH)return false;
        if(combo==2)return Math.sqrt(forward*forward+side*side)<=SPIN_RADIUS+halfWidth;
        return forward>=near(combo,blow)-halfWidth&&forward<=reach(combo,blow)+halfWidth&&Math.abs(side)<=halfWidth(combo,blow)+halfWidth;
    }
    /** Whether hit {@code combo} cannot be guarded: the finisher, as the other characters' fourth hits. */
    public static boolean unblockable(int combo){return combo==4;}
}
