package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's basic attack, four hits with their knife: two of them, by whether their hack is on (Q, XCharaHack) as each
 * hit begins.
 * <p>
 * With the hack on, a hacker's tricks (2.114.0, user request: "엑스차라 평타 만들자 너가 창작해줘 엑스차라 컨셉은
 * 언더테일에 나오는 차라가 해커의 능력을 사용하는거야 4타로 끝나고 멋있게": mine to make up, the user gave no video of
 * it; until 2.117.0 these were the attack whether the hack was on or not). A knife fighter's quick cuts, each with a
 * hacker's trick to it:
 * <ol>
 * <li>{@link #CUT} (잘라내기): a fast diagonal cut down from the high right across the front to the low left.</li>
 * <li>{@link #PASTE} (복사·붙여넣기): a backhand cut level across the front from the left to the right, and a moment on
 * the same cut pasted again {@link #PASTE_AHEAD} blocks further ahead (its second blow), as their free hand throws it out.</li>
 * <li>{@link #FLOOD} (연타): three quick thrusts, each sending a knife of light flying on ahead ({@link #KNIFE_RANGE}
 * blocks from their feet).</li>
 * <li>{@link #DELETE} (삭제): the finisher. A wind-up, then two great diagonal cuts crossing in an X in front of them;
 * the second sends whom it strikes flying ({@link #FLY}), and no guard takes either.</li>
 * </ol>
 * With the hack off, the knife and nothing else (2.117.0, user request: "이번에는 Q 껐을때 평타를 만들어줘 이펙트 없이 그냥
 * 휘두르기만해 그대신 모션을 좋게 만들어봐": mine to make up again): four swings, one blow each, nothing drawn but the
 * body and the knife:
 * <ol>
 * <li>{@link #SLASH} (가로베기): a level forehand cut across the front from the right to the left.</li>
 * <li>{@link #RISE} (올려베기): the backhand back, rising from the low left to the high right.</li>
 * <li>{@link #CHOP} (내려찍기): from up there over the head and straight down the middle.</li>
 * <li>{@link #LUNGE} (찌르기): the finisher. The knife drawn back, then a lunge the whole body goes into; it sends whom it
 * strikes flying ({@link #FLY}), and no guard takes it.</li>
 * </ol>
 * 28 in all either way (6, 3 + 3, 2 + 2 + 2, 4 + 6; and 6, 6, 6, 10: about Lee So-sang's four, 29). A hit's kind is its
 * place in the combo (1 to 4) and whether it is plain; the combo goes on from one kind to the other if the hack is turned
 * on or off between hits. Every blow staggers whom it strikes as any basic attack's does (Stagger.xchara), until the next
 * could land at full speed. Each hit goes the way its user faced as it began and holds them where they stand for
 * {@link #HOLD} ticks. Pure timing and reach, shared by the server and the clients; ticks of 1/20 s.
 */
public final class XCharaCombo {
    private XCharaCombo(){}
    /** Its swings' place among the effects' skills on the clients (KagenoSkills: Kageno's 0 to 9, X Chara's G 14, Lee So-sang's 15). */
    public static final int SKILL=13;
    /** The four hits with the hack on. */
    public static final int CUT=1,PASTE=2,FLOOD=3,DELETE=4;
    /** The four hits with the hack off (2.117.0): plain swings of the knife. */
    public static final int SLASH=5,RISE=6,CHOP=7,LUNGE=8;
    /** The ticks into hit {@code n} its blows land on. */
    private static final int[][] BLOWS={{},{4},{3,6},{3,5,7},{7,11},{4},{4},{6},{8}};
    /** Each blow's damage: 6, 3 + 3, 2 + 2 + 2, 4 + 6 (the hack's); 6, 6, 6, 10 (the plain ones'); 28 for either combo. */
    private static final float[][] DAMAGE={{},{6},{3,3},{2,2,2},{4,6},{6},{6},{6},{10}};
    /** Ticks from a hit's start before the next may start. */
    public static final int[] STEP={0,8,9,12,0,8,9,12,0};
    /** Ticks a hit holds its user from its start: no walking, guard, dash or other move until then. */
    public static final int[] HOLD={0,8,9,11,16,8,9,12,15};
    /** How long each hit runs (ticks) until its user is back in their stance: the clients draw its motion this long. */
    public static final int[] LENGTH={0,14,15,18,26,15,16,19,25};
    /** A press this many ticks or fewer before the next hit may start is kept, and starts it then. */
    public static final int BUFFER=8;
    /** The next hit must start within this many ticks of when it may, or the combo breaks. */
    public static final int GRACE=24;
    /** The rest once the fourth hit is over, or once a late hit broke the combo (ticks), as the other characters' attacks. */
    public static final int REST=24;
    /** How far down and up from its user's feet a blow reaches (blocks). */
    public static final double LOW=-1.2,HIGH=2.8;
    /** The second hit's cut pasted again this far ahead of the first (blocks). */
    public static final double PASTE_AHEAD=1.6;
    /** How far ahead of their feet the third hit's knives of light fly (blocks). */
    public static final double KNIFE_RANGE=7.0;
    /** The fourth's second cut sends whom it strikes flying along it (blocks a tick, out and up: as Kageno's fourth, some 7 blocks). */
    public static final double FLY=1.4,FLY_UP=.45;
    /**
     * Where each blow reaches: from how near ahead of its user's feet, how far, and how far to either side (blocks). The
     * plain swings': the two cuts as wide as the hack's first, the chop a narrow lane down the middle as far as its
     * blade comes down (the body has stepped in by then), the lunge a narrower one further on (the body goes out with it:
     * as far as the knife's point is driven).
     */
    private static final double[][][] REACH={{},
        {{-.5,3.5,2.3}},
        {{-.5,3.5,2.3},{PASTE_AHEAD-.8,3.5+PASTE_AHEAD,2.4}},
        {{-.3,KNIFE_RANGE,1.0},{-.3,KNIFE_RANGE,1.0},{-.3,KNIFE_RANGE,1.0}},
        {{-.5,4.2,2.5},{-.5,4.2,2.5}},
        {{-.5,3.5,2.3}},
        {{-.5,3.5,2.3}},
        {{-.3,4.0,1.3}},
        {{-.3,4.8,1.0}}};
    /** As far as any blow of the attack reaches (blocks): who could be struck at all is looked for within this. */
    public static final double MAX_REACH=KNIFE_RANGE+2.5;

    /** Whether {@code combo} is a hit (1 to 8: the hack's four, the plain four). */
    public static boolean valid(int combo){return combo>=CUT&&combo<=LUNGE;}
    /** Whether hit {@code combo} is a plain swing (the hack off as it began). */
    public static boolean plain(int combo){return combo>=SLASH&&combo<=LUNGE;}
    /** Hit {@code combo}'s place in its combo (1 to 4; 0 for none). */
    public static int place(int combo){return valid(combo)?(combo-1)%4+1:0;}
    /** The hit at {@code place} (1 to 4) of the plain combo or of the hack's. */
    public static int hit(int place,boolean plain){return plain?SLASH+place-1:place;}
    /** Whether hit {@code combo} is its combo's last, the finisher. */
    public static boolean last(int combo){return place(combo)==4;}
    /** How many blows hit {@code combo} lands. */
    public static int blows(int combo){return valid(combo)?BLOWS[combo].length:0;}
    /** The tick into hit {@code combo} its blow {@code i} lands on. */
    public static int blowAt(int combo,int i){return BLOWS[combo][i];}
    /** The tick into hit {@code combo} its last blow lands on. */
    public static int lastBlow(int combo){return BLOWS[combo][BLOWS[combo].length-1];}
    /** The damage of hit {@code combo}'s blow {@code i}. */
    public static float damage(int combo,int i){return DAMAGE[combo][i];}
    /** All of hit {@code combo}'s blows together. */
    public static float total(int combo){float t=0;for(float d:DAMAGE[combo])t+=d;return t;}
    /**
     * The hit after {@code combo}, plain or the hack's by {@code plain} (whether the hack is off as it begins): the next
     * place of the combo, or the first again after the fourth (or after none).
     */
    public static int next(int combo,boolean plain){int at=place(combo);return hit(at>=1&&at<4?at+1:1,plain);}
    /** The hit after {@code combo} of its own kind. */
    public static int next(int combo){return next(combo,plain(combo));}
    /** When the hit after {@code combo} (begun at {@code start}) may start. */
    public static long nextAt(int combo,long start){return start+STEP[combo];}
    /**
     * The tick the attack is ready again after the combo whose hit {@code combo} began at {@code start}, if it broke by
     * {@code now} (the next hit did not start in time), or -1 while it holds (or has nothing to break: none yet, or the
     * fourth hit ended it).
     */
    public static long broken(int combo,long start,long now){
        if(!valid(combo)||last(combo))return -1;
        long late=nextAt(combo,start)+GRACE;
        return now>late?late+1+REST:-1;
    }
    /** The tick the attack is ready again after the fourth hit {@code combo} begun at {@code start}: once it is over, a rest on. */
    public static long rested(int combo,long start){return start+LENGTH[combo]+REST;}
    /** Whether hit {@code combo} begun at {@code start} still holds its user at {@code now}. */
    public static boolean holding(int combo,long start,long now){return valid(combo)&&now>=start&&now<start+HOLD[combo];}
    /**
     * The tick a blow staggers whom it strikes until (Stagger.xchara): the attacker's next blow at full speed (the next of
     * the same hit, else the next hit's first, started as soon as it may: a plain one if {@code plainNext}, the hack off
     * as the blow lands, for a plain swing lands later after it begins than the hack's hit in its place) and {@code past}
     * ticks more; the fourth hit's last blow, which throws them, staggers them {@code last} ticks.
     */
    public static long staggerUntil(int combo,int blow,long start,long now,int past,int last,boolean plainNext){
        if(blow+1<blows(combo))return Math.max(now,start+blowAt(combo,blow+1))+past;
        if(last(combo))return now+last;
        return Math.max(now,nextAt(combo,start)+blowAt(next(combo,plainNext),0))+past;
    }
    /** {@link #staggerUntil(int,int,long,long,int,int,boolean)} with the next hit of the blow's own kind. */
    public static long staggerUntil(int combo,int blow,long start,long now,int past,int last){return staggerUntil(combo,blow,start,now,past,last,plain(combo));}
    /** How near (blocks ahead of its user's feet, behind them below 0) hit {@code combo}'s blow {@code blow} begins to reach. */
    public static double near(int combo,int blow){return REACH[combo][blow][0];}
    /** How far ahead of its user's feet hit {@code combo}'s blow {@code blow} reaches (blocks). */
    public static double reach(int combo,int blow){return REACH[combo][blow][1];}
    /** How far to either side of the way hit {@code combo}'s blow {@code blow} reaches (blocks). */
    public static double halfWidth(int combo,int blow){return REACH[combo][blow][2];}
    /**
     * Whether a point (ahead along the hit, to its right and up from its user's feet, blocks) is in the way of hit
     * {@code combo}'s blow {@code blow}, for a body {@code halfWidth} across: a lane ahead.
     */
    public static boolean lane(int combo,int blow,double forward,double side,double height,double halfWidth){
        if(height<LOW||height>HIGH)return false;
        return forward>=near(combo,blow)-halfWidth&&forward<=reach(combo,blow)+halfWidth&&Math.abs(side)<=halfWidth(combo,blow)+halfWidth;
    }
    /** Whether hit {@code combo} cannot be guarded: the finisher (either combo's), as the other characters' fourth hits. */
    public static boolean unblockable(int combo){return last(combo);}
    /** Whether hit {@code combo}'s blow {@code blow} sends whom it strikes flying: the finisher's last. */
    public static boolean launches(int combo,int blow){return last(combo)&&blow==blows(combo)-1;}
}
