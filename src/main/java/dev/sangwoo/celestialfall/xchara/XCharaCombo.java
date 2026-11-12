package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's basic attack: four swings of their knife, one blow each.
 * <ol>
 * <li>{@link #SLASH} (가로베기): a level forehand cut across the front from the right to the left.</li>
 * <li>{@link #RISE} (올려베기): the backhand back, rising from the low left to the high right.</li>
 * <li>{@link #CHOP} (내려찍기): from up there over the head and straight down the middle.</li>
 * <li>{@link #LUNGE} (찌르기): the finisher. The knife drawn back, then a lunge the whole body goes into; it sends whom it
 * strikes flying ({@link #FLY}), and no guard takes it.</li>
 * </ol>
 * 6, 6, 6 and 10: 28 in all (about Lee So-sang's four, 29). The same four whether their hack (Q, XCharaHack) is on or off
 * (2.118.0, user choice: "Q 끈 평타와 같은 동작"); a hit begun with the hack on is one of {@link #HACK_SLASH} to
 * {@link #HACK_LUNGE}, the same swing by every rule here, which the clients draw with its purple cut and what it leaves
 * on whom it strikes (client/xchara/SlashFx), and one begun with it off is one of {@link #SLASH} to {@link #LUNGE},
 * drawn with nothing but the body and the knife (2.117.0, user request: "이번에는 Q 껐을때 평타를 만들어줘 이펙트 없이 그냥
 * 휘두르기만해 그대신 모션을 좋게 만들어봐": the swings are mine to make up). A hit's kind is its place in the combo (1 to 4)
 * and whether it is plain; the combo goes on from one kind to the other if the hack is turned on or off between hits.
 * <p>
 * Until 2.118.0 the hack's four were a hacker's tricks with rules of their own (2.114.0: a cut, a cut pasted again
 * further on, three thrusts throwing knives of light 7 blocks, two cuts crossing in an X: two and three blows a hit),
 * mine to make up; the user had their effects thrown out and made anew to the attack's sound ("Q활성화 평타 이펙트를
 * 전체적으로 싹 다 갈아엎고 평타 소리에 맞춰서 새로 만들어줘"), one sound a hit, and chose the plain swings for both.
 * <p>
 * Every blow staggers whom it strikes as any basic attack's does (Stagger.xchara), until the next could land at full
 * speed. Each hit goes the way its user faced as it began and holds them where they stand for {@link #HOLD} ticks. Pure
 * timing and reach, shared by the server and the clients; ticks of 1/20 s.
 */
public final class XCharaCombo {
    private XCharaCombo(){}
    /** Its swings' place among the effects' skills on the clients (KagenoSkills: Kageno's 0 to 9, X Chara's G 14, Lee So-sang's 15). */
    public static final int SKILL=13;
    /** The four swings begun with the hack on: drawn with their purple cuts. */
    public static final int HACK_SLASH=1,HACK_RISE=2,HACK_CHOP=3,HACK_LUNGE=4;
    /** The four swings begun with the hack off (2.117.0): nothing drawn but the body and the knife. */
    public static final int SLASH=5,RISE=6,CHOP=7,LUNGE=8;
    /**
     * R with the hack on, the cleave (2.121.0, user request: "R키는 패널을 누르면 들고 있던 검이 엄청 거대해지고 그걸 양손으로
     * 잡고 덤블링 한 번 돌고 크게 내려찍는 스킬 맞추면 못 움직이는 1초 무력화"): no hit of the combo but a skill of the
     * knife's pipeline (as Star Platinum's heavy punch is of its punches'): they press the panel, the knife's blade
     * grows huge, they take it in both hands, raise it over their head and bring it down where they stand. (2.122.0,
     * user request: "덤블링을 돌지말고 그냥 제자리에서 거대한 검을 내려치게 하고 손잡이는 상관없고 칼날 부분을 엄청 키워줘
     * 그리고 내려찍을때 바닥에 박히는 효과를 엄청 강하게 만들어주고 무력화 시간을 3초로 늘려줘": 2.121.0 leapt and turned
     * a somersault with it, and its incapacitation was a second.) Its one blow lands CLEAVE's BLOWS ticks in on everyone
     * in its long lane ahead: unguardable, no knockback, no stagger; whoever it strikes cannot move or act for
     * {@link #CLEAVE_STUN} ticks (an incapacitation that also stops their legs). Its cooldown ({@link #CLEAVE_COOLDOWN})
     * runs from its end; cut short before the blow, from then. The damage, the reach, the times and the cooldown are
     * mine (the user gave the motion and the incapacitation's length).
     */
    public static final int CLEAVE=9;
    /** The cleave: its incapacitation (ticks; the legs stopped as long, wholly), its cooldown from its end (ticks: 12 s), and its reach {near, far, half width} and how high it reaches (blocks). */
    public static final int CLEAVE_STUN=60,CLEAVE_COOLDOWN=240;
    public static final double CLEAVE_SLOW=1,CLEAVE_HIGH=3.6;
    private static final double[] CLEAVE_REACH={.2,9.6,1.5};
    /** When the cleave's panel is pressed, when the knife is full grown and in both hands, and when it is raised over their head, wound for the blow (ticks into it): what the clients draw to (GiantCleave). */
    public static final int CLEAVE_PRESS=4,CLEAVE_GROWN=9,CLEAVE_RAISED=14;
    /** The tick into hit {@code n} its blow lands on (the same for either kind). */
    private static final int[][] BLOWS={{},{4},{4},{6},{8},{4},{4},{6},{8},{20}};
    /** Each blow's damage: 6, 6, 6, 10; 28 for the combo. */
    private static final float[][] DAMAGE={{},{6},{6},{6},{10},{6},{6},{6},{10},{18}};
    /** Ticks from a hit's start before the next may start. */
    public static final int[] STEP={0,8,9,12,0,8,9,12,0,0};
    /** Ticks a hit holds its user from its start: no walking, guard, dash or other move until then. */
    public static final int[] HOLD={0,8,9,12,15,8,9,12,15,34};
    /** How long each hit runs (ticks) until its user is back in their stance: the clients draw its motion this long. */
    public static final int[] LENGTH={0,15,16,19,25,15,16,19,25,40};
    /** A press this many ticks or fewer before the next hit may start is kept, and starts it then. */
    public static final int BUFFER=8;
    /** The next hit must start within this many ticks of when it may, or the combo breaks. */
    public static final int GRACE=24;
    /** The rest once the fourth hit is over, or once a late hit broke the combo (ticks), as the other characters' attacks. */
    public static final int REST=24;
    /** How far down and up from its user's feet a blow reaches (blocks). */
    public static final double LOW=-1.2,HIGH=2.8;
    /** The lunge sends whom it strikes flying along it (blocks a tick, out and up: as Kageno's fourth, some 7 blocks). */
    public static final double FLY=1.4,FLY_UP=.45;
    /**
     * Where each blow reaches: from how near ahead of its user's feet, how far, and how far to either side (blocks). The
     * two cuts wide across the front, the chop a narrow lane down the middle as far as its blade comes down (the body
     * has stepped in by then), the lunge a narrower one further on (the body goes out with it: as far as the knife's
     * point is driven).
     */
    private static final double[][] REACH={{},{-.5,3.5,2.3},{-.5,3.5,2.3},{-.3,4.0,1.3},{-.3,4.8,1.0}};
    /** As far as any blow of the attack reaches (blocks): who could be struck at all is looked for within this. */
    public static final double MAX_REACH=9.6+2.5;

    /** Whether {@code combo} is a hit (1 to 8: the four with the hack on, the plain four) or the cleave (9). */
    public static boolean valid(int combo){return combo>=HACK_SLASH&&combo<=CLEAVE;}
    /** Whether {@code combo} is a skill of the pipeline, no hit of the combo: the cleave. */
    public static boolean skill(int combo){return combo==CLEAVE;}
    /** When the cleave is ready again: its cooldown after one begun at {@code start} is over. */
    public static long cleaveReady(long start){return start+LENGTH[CLEAVE]+CLEAVE_COOLDOWN;}
    /** Whether hit {@code combo} is a plain swing (the hack off as it began). */
    public static boolean plain(int combo){return combo>=SLASH&&combo<=LUNGE;}
    /** Hit {@code combo}'s place in its combo (1 to 4; 0 for none). */
    public static int place(int combo){return valid(combo)&&!skill(combo)?(combo-1)%4+1:0;}
    /** The hit at {@code place} (1 to 4), plain or begun with the hack on. */
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
     * The hit after {@code combo}, plain or not by {@code plain} (whether the hack is off as it begins): the next
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
        if(!valid(combo)||last(combo)||skill(combo))return -1;
        long late=nextAt(combo,start)+GRACE;
        return now>late?late+1+REST:-1;
    }
    /** The tick the attack is ready again after the fourth hit {@code combo} begun at {@code start}: once it is over, a rest on. */
    public static long rested(int combo,long start){return start+LENGTH[combo]+REST;}
    /** Whether hit {@code combo} begun at {@code start} still holds its user at {@code now}. */
    public static boolean holding(int combo,long start,long now){return valid(combo)&&now>=start&&now<start+HOLD[combo];}
    /**
     * The tick a blow staggers whom it strikes until (Stagger.xchara): the attacker's next blow at full speed (the next
     * hit's, started as soon as it may; either kind lands as long after it begins) and {@code past} ticks more; the
     * lunge, which throws them, staggers them {@code last} ticks.
     */
    public static long staggerUntil(int combo,int blow,long start,long now,int past,int last){
        if(skill(combo))return now;
        if(blow+1<blows(combo))return Math.max(now,start+blowAt(combo,blow+1))+past;
        if(last(combo))return now+last;
        return Math.max(now,nextAt(combo,start)+blowAt(next(combo),0))+past;
    }
    /** How near (blocks ahead of its user's feet, behind them below 0) hit {@code combo}'s blow {@code blow} begins to reach. */
    public static double near(int combo,int blow){return reaches(combo)[0];}
    /** How far ahead of its user's feet hit {@code combo}'s blow {@code blow} reaches (blocks). */
    public static double reach(int combo,int blow){return reaches(combo)[1];}
    /** How far to either side of the way hit {@code combo}'s blow {@code blow} reaches (blocks). */
    public static double halfWidth(int combo,int blow){return reaches(combo)[2];}
    private static double[] reaches(int combo){return skill(combo)?CLEAVE_REACH:REACH[place(combo)];}
    /**
     * Whether a point (ahead along the hit, to its right and up from its user's feet, blocks) is in the way of hit
     * {@code combo}'s blow {@code blow}, for a body {@code halfWidth} across: a lane ahead.
     */
    public static boolean lane(int combo,int blow,double forward,double side,double height,double halfWidth){
        if(height<LOW||height>(skill(combo)?CLEAVE_HIGH:HIGH))return false;
        return forward>=near(combo,blow)-halfWidth&&forward<=reach(combo,blow)+halfWidth&&Math.abs(side)<=halfWidth(combo,blow)+halfWidth;
    }
    /** Whether hit {@code combo} cannot be guarded: the lunge, as the other characters' fourth hits. */
    public static boolean unblockable(int combo){return last(combo)||skill(combo);}
    /** Whether hit {@code combo}'s blow {@code blow} sends whom it strikes flying: the lunge. */
    public static boolean launches(int combo,int blow){return last(combo)&&blow==blows(combo)-1;}
}
