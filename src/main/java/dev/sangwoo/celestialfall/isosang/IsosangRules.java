package dev.sangwoo.celestialfall.isosang;

/**
 * Lee So-sang's basic attack, four hits with the sword (2.77.0, user request: "이제 평타부터 만들자 영상 보내줄게 1타 2타
 * 3타 4타 다 들어있어 영상에 평타는 넉백이 없게 만들어줘"), remade after the user's video of each hit (2.77.1, user request:
 * "타수 별로 다시 영상 보내줄게 제대로 참고해"):
 * <ol>
 * <li>the sword flipped up at the right side and held out, a step in with it cocked at the shoulder, a great diagonal cut
 * down across the front to the left and a cut rising back up to the right, the sword held out there;</li>
 * <li>the sword flipped upright, a half turn to the right into a crouch and a whole turn back to the left low over the
 * ground: a wide ring round them (it strikes all round, twice: the turn, then the ring flaring as the sword is
 * flicked);</li>
 * <li>a long lunging thrust, the sword drawn back low to the right, a long rising cut across, a quick cut back down, a
 * great cut rising up the right side and a sweep low across the front from the left: five blows;</li>
 * <li>the finisher: a turn with the sword twirled overhead, the free hand pointed at the foe, a deep crouch, a lunge and
 * a giant level cut across the front that spreads into ink over the ground.</li>
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
     * The ticks into hit {@code n} its blows land on, as in the user's videos: the diagonal cut at 0.3 s; the low turn
     * at 0.25 s and the ring flaring at 0.45 s; the thrust at 0.15 s, the rising cut at 0.45 s, the quick cut at 0.55 s,
     * the cut up the right side at 0.65 s and the low sweep at 0.95 s; the finisher at 0.55 s, after the twirl, the aim,
     * the crouch and the lunge.
     */
    private static final int[][] BLOWS={{},{6},{5,9},{3,9,11,13,19},{11}};
    /** Each blow's damage: 6, 6 (3 + 3), 7 (1.5 + 1.5 + 1 + 1 + 2) and 10, 29 for the whole combo (Kageno's 28). */
    private static final float[][] DAMAGE={{},{6},{3,3},{1.5f,1.5f,1,1,2},{10}};
    /**
     * Ticks from a hit's start before the next may start (the videos' pace): the first held out to the right after its
     * rising cut, the second turned back to the front after its ring, the third back from its sweep.
     */
    public static final int[] STEP={0,12,13,30,0};
    /** Ticks a hit holds its user from its start: no walking, guard, dash or other move until then. */
    public static final int[] HOLD={0,10,12,21,15};
    /** How long each hit runs (ticks) until its user is back in their stance: the clients draw its motion this long. */
    public static final int[] LENGTH={0,24,20,34,32};
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
    /** The diagonal cut's reach ahead and to either side (its crescent is wide); the low turn's radius all round. */
    public static final double CUT_REACH=3.6,CUT_HALF_WIDTH=2.4,SPIN_RADIUS=3.6;
    /** Hit 3: the thrust's reach and its narrow way, the rising and the quick cuts', the cut up the right side's and the low sweep's. */
    public static final double THRUST_REACH=5.4,THRUST_HALF_WIDTH=.8,RISE_REACH=3.6,RISE_HALF_WIDTH=2.2,QUICK_REACH=3.2,QUICK_HALF_WIDTH=1.6,
        ARC_REACH=3.4,ARC_HALF_WIDTH=1.8,SWEEP_REACH=3.8,SWEEP_HALF_WIDTH=2.6;
    /** The finisher's giant level cut across the front. */
    public static final double FINISH_REACH=5.6,FINISH_HALF_WIDTH=3.6;
    /** How far ahead of its user's feet hit {@code combo}'s blow {@code blow} reaches (blocks; the turn's radius all round). */
    public static double reach(int combo,int blow){
        if(combo==3)return switch(blow){case 0->THRUST_REACH;case 1->RISE_REACH;case 2->QUICK_REACH;case 3->ARC_REACH;default->SWEEP_REACH;};
        return combo==2?SPIN_RADIUS:combo==4?FINISH_REACH:CUT_REACH;
    }
    /** How far to either side of the way hit {@code combo}'s blow {@code blow} reaches (blocks). */
    public static double halfWidth(int combo,int blow){
        if(combo==3)return switch(blow){case 0->THRUST_HALF_WIDTH;case 1->RISE_HALF_WIDTH;case 2->QUICK_HALF_WIDTH;case 3->ARC_HALF_WIDTH;default->SWEEP_HALF_WIDTH;};
        return combo==2?SPIN_RADIUS:combo==4?FINISH_HALF_WIDTH:CUT_HALF_WIDTH;
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
