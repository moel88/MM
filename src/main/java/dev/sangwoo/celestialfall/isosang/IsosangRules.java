package dev.sangwoo.celestialfall.isosang;

/**
 * Lee So-sang's basic attack, four hits with the sword (2.77.0, user request: "이제 평타부터 만들자 영상 보내줄게 1타 2타
 * 3타 4타 다 들어있어 영상에 평타는 넉백이 없게 만들어줘"), remade from the ground up after the user's videos of each hit on
 * its own (2.77.2, user request: "완전 달라 전부 갈아엎고 이펙트를 대폭 강화해주고 영상 다시 보고 와", "애초에 4타는 참격을
 * 날리는건데"):
 * <ol>
 * <li>the sword flashing into the hand, a turn and a coil, a great diagonal cut from the high right down round the left
 * and a cut rising straight back up to the high right;</li>
 * <li>a turn into a crouch and a whole spin low over the ground: a wide ring round them (it strikes all round, twice: the
 * ring, then the ring bursting into ink);</li>
 * <li>a lunging thrust (a beam far ahead), a great cut rising up the right side, a cut rising from the low left, a sweep
 * low across the front and a wide sweep across at the head: five blows;</li>
 * <li>the finisher: a turn with the sword twirled overhead, the free hand pointed at the foe, a crouch, a lunge and a low
 * cut that throws a giant crescent of a slash flying ahead ({@link #WAVE_RANGE} blocks, widening as it goes): it strikes
 * everyone it passes through, once.</li>
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
     * The ticks into hit {@code n} its blows land on, from the user's videos of each hit: the diagonal cut at 0.3 s; the
     * ring at 0.15 s and its burst into ink at 0.3 s; the thrust at 0.3 s, the cut up the right side at 0.5 s, the cut
     * rising from the low left at 0.7 s, the low sweep at 0.8 s and the wide sweep at 0.9 s; the finisher's slash thrown
     * at 0.7 s, after the twirl, the aim, the crouch and the lunge (it strikes as it flies on, {@link #WAVE_TICKS}).
     */
    private static final int[][] BLOWS={{},{6},{3,6},{6,10,14,16,18},{14}};
    /** Each blow's damage: 6, 6 (3 + 3), 7 (1.5 + 1.5 + 1 + 1 + 2) and 10, 29 for the whole combo (Kageno's 28). */
    private static final float[][] DAMAGE={{},{6},{3,3},{1.5f,1.5f,1,1,2},{10}};
    /**
     * Ticks from a hit's start before the next may start, from the user's video of the whole combo: the first once its
     * rising cut is through, the second while its ring dries, the third once it is back from its wide sweep.
     */
    public static final int[] STEP={0,11,10,30,0};
    /** Ticks a hit holds its user from its start: no walking, guard, dash or other move until then. */
    public static final int[] HOLD={0,10,12,22,22};
    /** How long each hit runs (ticks) until its user is back in their stance: the clients draw its motion this long. */
    public static final int[] LENGTH={0,18,18,28,36};
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
     * slash staggers them {@code last} ticks. {@code past} is Stagger.PAST.
     */
    public static long staggerUntil(int combo,int blow,long start,long now,int past,int last){
        if(combo>=4)return now+last;
        long next=blow+1<blows(combo)?start+blowAt(combo,blow+1):nextAt(combo,start)+blowAt(combo+1,0);
        return Math.max(now,next)+past;
    }

    // ───────────────────────────── reach ─────────────────────────────

    /** Where hit {@code combo}'s blows reach (blocks from its user's feet): a body this close in counts as in the way. */
    public static final double NEAR=-.5,LOW=-1.2,HIGH=2.8;
    /** The diagonal cut (its crescent wraps round the front and the left); the ring's radius all round. */
    public static final double CUT_REACH=4.2,CUT_HALF_WIDTH=3.4,SPIN_RADIUS=3.9;
    /**
     * Hit 3: the thrust's reach and its narrow way (its beam goes on further, for the eye), the cut up the right side's,
     * the cut rising from the low left's, the low sweep's and the wide sweep's.
     */
    public static final double THRUST_REACH=6.4,THRUST_HALF_WIDTH=.9,RISE_REACH=3.9,RISE_HALF_WIDTH=2.6,CLIMB_REACH=3.8,CLIMB_HALF_WIDTH=2.8,
        SWEEP_REACH=3.9,SWEEP_HALF_WIDTH=3.6,WIDE_REACH=4.3,WIDE_HALF_WIDTH=4.0;
    /**
     * The finisher's slash, thrown flying (2.77.2, "4타는 참격을 날리는건데"): a crescent across the way it was thrown
     * (leaning back from its leading edge, for the eye), from {@link #WAVE_START} blocks ahead of its user to
     * {@link #WAVE_RANGE} over {@link #WAVE_TICKS} ticks (fast at first, slowing: 2.78.0, nearer and slower, as the
     * user's video from above shows it), {@link #WAVE_HALF_START} to {@link #WAVE_HALF_END} blocks to either side as it
     * widens, its tips trailing {@link #WAVE_CURVE_START} to {@link #WAVE_CURVE_END} blocks behind its middle,
     * {@link #WAVE_DEPTH} deep. It strikes whoever its band passes over, once; and whoever is right in front as it is thrown.
     */
    public static final double WAVE_START=1.2,WAVE_RANGE=11,WAVE_HALF_START=4.5,WAVE_HALF_END=8.5,WAVE_CURVE_START=1.0,WAVE_CURVE_END=3.6,WAVE_DEPTH=2.4;
    /**
     * Ticks the slash flies (2.78.0, the user's video from above: it is still close and huge a quarter of a second on,
     * drifting out and widening; 2.77.x had it 13.5 blocks out in 0.4 s, a thin line in the distance).
     */
    public static final int WAVE_TICKS=16;
    /** As far as any blow of the attack reaches (blocks): who could be struck at all is looked for within this. */
    public static final double MAX_REACH=WAVE_RANGE+WAVE_HALF_END;

    /** How far ahead of its user's feet hit {@code combo}'s blow {@code blow} reaches (blocks; the ring's radius all round). */
    public static double reach(int combo,int blow){
        if(combo==3)return switch(blow){case 0->THRUST_REACH;case 1->RISE_REACH;case 2->CLIMB_REACH;case 3->SWEEP_REACH;default->WIDE_REACH;};
        return combo==2?SPIN_RADIUS:combo==4?WAVE_RANGE:CUT_REACH;
    }
    /** How far to either side of the way hit {@code combo}'s blow {@code blow} reaches (blocks). */
    public static double halfWidth(int combo,int blow){
        if(combo==3)return switch(blow){case 0->THRUST_HALF_WIDTH;case 1->RISE_HALF_WIDTH;case 2->CLIMB_HALF_WIDTH;case 3->SWEEP_HALF_WIDTH;default->WIDE_HALF_WIDTH;};
        return combo==2?SPIN_RADIUS:combo==4?WAVE_HALF_END:CUT_HALF_WIDTH;
    }
    /** How near (blocks ahead of its user's feet, behind them below 0) hit {@code combo}'s blow {@code blow} begins to reach: the thrust only ahead. */
    public static double near(int combo,int blow){return combo==3&&blow==0?.1:NEAR;}
    /**
     * Whether a point (ahead along the hit, to its right and up from its user's feet, blocks) is in the way of hit
     * {@code combo}'s blow {@code blow}, for a body {@code halfWidth} across: a lane ahead, the ring's a circle all round.
     * The finisher's slash is a flight, not a lane: {@link #waveSweeps}.
     */
    public static boolean lane(int combo,int blow,double forward,double side,double height,double halfWidth){
        if(height<LOW||height>HIGH)return false;
        if(combo==2)return Math.sqrt(forward*forward+side*side)<=SPIN_RADIUS+halfWidth;
        return forward>=near(combo,blow)-halfWidth&&forward<=reach(combo,blow)+halfWidth&&Math.abs(side)<=halfWidth(combo,blow)+halfWidth;
    }
    /** Whether hit {@code combo} cannot be guarded: the finisher, as the other characters' fourth hits. */
    public static boolean unblockable(int combo){return combo==4;}

    // ───────────────────────────── the finisher's flying slash ─────────────────────────────

    /**
     * How far the flight has gone, 0 to 1, {@code ticks} after the slash was thrown: fast at first, slowing to a stop
     * (2.78.0: a gentler curve than the square, so it is still drifting out past a quarter of a second as the video shows).
     */
    public static double waveFlown(double ticks){double u=Math.max(0,Math.min(1,ticks/WAVE_TICKS));return 1-Math.pow(1-u,1.8);}
    /** How far ahead of where it was thrown the middle of the slash's leading edge is (blocks), {@code ticks} after. */
    public static double waveFront(double ticks){return WAVE_START+(WAVE_RANGE-WAVE_START)*waveFlown(ticks);}
    /**
     * How far to either side the slash reaches (blocks), {@code ticks} after it was thrown: it widens faster than it
     * flies (2.78.0, the video from above: most of its width within a tenth of a second).
     */
    public static double waveHalf(double ticks){double u=Math.max(0,Math.min(1,ticks/WAVE_TICKS));return WAVE_HALF_START+(WAVE_HALF_END-WAVE_HALF_START)*(1-Math.pow(1-u,4));}
    /** How far behind the middle its tips trail (blocks), {@code ticks} after it was thrown. */
    public static double waveCurve(double ticks){return WAVE_CURVE_START+(WAVE_CURVE_END-WAVE_CURVE_START)*waveFlown(ticks);}
    /** The leading edge's place ahead (blocks) at {@code side} blocks to the right of the middle, {@code ticks} after it was thrown. */
    public static double waveEdge(double ticks,double side){double s=Math.min(1,Math.abs(side)/waveHalf(ticks));return waveFront(ticks)-waveCurve(ticks)*s*s;}
    /**
     * Whether the slash's band is over a body {@code ticks} after it was thrown (a point ahead, to the right and up from
     * where its user stood, blocks; a body {@code halfWidth} across): within its reach to the side, between its leading
     * edge and {@link #WAVE_DEPTH} behind it. At the throw itself it also takes whoever is right in front, from
     * {@link #NEAR}.
     */
    public static boolean waveOver(double ticks,double forward,double side,double height,double halfWidth){
        if(height<LOW||height>HIGH||Math.abs(side)>waveHalf(ticks)+halfWidth)return false;
        double edge=waveEdge(ticks,side),back=ticks<=0?Math.min(NEAR,edge-WAVE_DEPTH):edge-WAVE_DEPTH;
        return forward+halfWidth>=back&&forward-halfWidth<=edge;
    }
    /** Steps a tick of the flight is looked at in: the band moves less than its depth from one to the next, so no one slips through. */
    static final int WAVE_STEPS=8;
    /**
     * Whether the slash's band passes over a body between {@code from} and {@code to} ticks after it was thrown (it is
     * looked at {@link #WAVE_STEPS} times a tick, the ends too).
     */
    public static boolean waveSweeps(double from,double to,double forward,double side,double height,double halfWidth){
        if(to<from)return false;
        int n=Math.max(1,(int)Math.ceil((to-from)*WAVE_STEPS));
        for(int i=0;i<=n;i++)if(waveOver(from+(to-from)*i/n,forward,side,height,halfWidth))return true;
        return false;
    }
    /** The first moment (ticks after the throw, to a sixteenth of a tick) the slash's band is over a body, or -1 if it never is. */
    public static double waveReaches(double forward,double side,double height,double halfWidth){
        for(int i=0;i<=WAVE_TICKS*16;i++)if(waveOver(i/16.0,forward,side,height,halfWidth))return i/16.0;
        return -1;
    }
    /** Whether the slash thrown {@code ticks} ago still flies (it strikes on its way until then). */
    public static boolean waveFlying(double ticks){return ticks>=0&&ticks<=WAVE_TICKS;}
}
