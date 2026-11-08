package dev.sangwoo.celestialfall.isosang;

import java.util.UUID;

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
 * <p>E (2.80.0, user request: "E스킬을 사용하면 평타 쿨타임이 즉시 초기화가 되고 다음 평타가 강화 돼 강화 평타는 데미지
 * 50% 더 강하고 1타와 4타만 바뀌고 2타 3타는 모션이 같아 1타는 영상 처럼 검을 3개 던지고 4타는 뒤로 이동하고 거대한 검을
 * 찔러 4타에 맞으면 넉백이 있어 E 쿨 타임은 15초야"): the attack's rest is gone at once and the next combo is empowered,
 * every blow {@link #EMPOWER} times as hard; its first hit is {@link #THROWS} (three swords thrown flying the way its user
 * looks, {@link #SWORD_SPEED}), its fourth {@link #THRUST} (2.80.1: a leap 5 blocks back and a giant sword thrown flying
 * ahead, which throws whom it strikes), its second and third as they are. A hit's number here is its kind: 1 to 4 the combo's hits, 5 and 6 the
 * empowered first and fourth ({@link #position}). E is ready again {@link #E_COOLDOWN} ticks after the empowered combo is
 * over (the user's choice: "강화 콤보가 끝난 뒤").
 */
public final class IsosangRules {
    private IsosangRules(){}
    /** The swings' number among the skills' visuals on the clients (KagenoSkills): past every Kageno skill's. */
    public static final int SKILL=15;
    /**
     * The ticks into hit {@code n} its blows land on, from the user's videos of each hit: the diagonal cut at 0.3 s; the
     * ring at 0.15 s and its burst into ink at 0.3 s; hit 3's four cuts as each passes the front (2.79.2, the user's
     * videos at a tenth of the speed and "4번으로 바꿔": 0.1, 0.35, 0.55 and 0.85 s; five blows until then); the finisher's slash thrown
     * at 0.5 s, after the twirl, the aim, the crouch and the lunge (2.79.2, the user's video of the whole combo at half
     * speed: the twirl starts 0.05 s in and the slash is thrown 0.44 s after it; it was 0.7 s; it strikes as it flies on,
     * {@link #WAVE_TICKS}).
     */
    private static final int[][] BLOWS={{},{6},{3,6},{2,7,11,17},{10},{2,4,7},{3},{IsosangRules.SWEEP_BLOW},{},{},{20,21,22,23,24,25},
        // (2.96.0) T: the dash through the foe, then the six swords, the three slashes among the last three (LUNGE_KIND)
        {6,11,12,13,14,14,15,15,16,16},
        // (2.97.0) Y: the swords coming down two at a time (CAGE_DROPS) and the ink bursting up round the foe, thirteen
        // blows in all from 0.55 s to 0.9 s (the videos' hit counter)
        {11,11,12,12,13,14,15,15,15,16,17,18,18},
        // (2.101.0) H again, the formation's finale: its swords coming down into the band ahead of her, the last as the
        // band flashes blue (3.5 s); (2.101.9, the swords out through her camera's screen as it cuts behind her at 2.3 s
        // and fired at random from 2.375 s: "카메라 뒤로 이동하자마자", "무작위로 빠르게") three as they rain down (from about
        // 2.45 s to 2.85 s) and one as hers comes down last (3.17 s)
        {50,53,56,63,70}};
    /**
     * Each blow's damage: 6, 6 (3 + 3), 7 (1.5 + 1.5 + 1.5 + 2.5) and 10, 29 for the whole combo (Kageno's 28); the
     * empowered first's three swords and fourth's thrust half as hard again as the first's cut and the fourth's slash (9
     * and 15; the empowered combo 43.5).
     */
    private static final float[][] DAMAGE={{},{6},{3,3},{1.5f,1.5f,1.5f,2.5f},{10},{3,3,3},{15},{IsosangRules.SWEEP_DAMAGE},{},{},{2,2,2,2,2,2},
        // (2.96.0) T: the user's choice, 30 in all: the dash 6, each sword 2, each slash 4
        {6,2,2,2,2,4,2,4,2,4},
        // (2.97.0) Y: the user's choice, 40 in all over its thirteen blows: twelve of 3 and the last 4
        {3,3,3,3,3,3,3,3,3,3,3,3,4},
        // (2.101.0) H again: the user's choice, 60 in all ("합 60"), five of 12
        {12,12,12,12,12}};
    /** The empowered combo's blows are this much harder (2.80.0, user request: "데미지 50% 더 강하고"). */
    public static final float EMPOWER=1.5f;
    /** The empowered hits (2.80.0): the first, three swords thrown; the fourth, a leap back and a giant sword thrown (2.80.1). */
    public static final int THROWS=5,THRUST=6;
    /** Ticks E is not ready again once the empowered combo it gave is over (2.80.0: 15 s). */
    public static final int E_COOLDOWN=300;
    /**
     * Ticks from a hit's start before the next may start, from the user's video of the whole combo: the first once its
     * cut is through, the second while its two cuts dry, the third once its fourth cut is through and they are back in
     * their stance (2.79.2, the user's video of the whole combo at half speed: the finisher begins 1.22 s after the third;
     * it was 1.5 s). The empowered first (2.80.0, the user's videos of E): the second follows its three swords 0.8 s in.
     */
    public static final int[] STEP={0,11,10,25,0,16,0,0,0,0,0,0,0,0};
    /** Ticks a hit holds its user from its start: no walking, guard, dash or other move until then (2.81.0: and R's; 2.82.0: H's, all of it; 2.83.0: G's cutscene). */
    public static final int[] HOLD={0,10,12,22,18,14,16,21,826,IsosangRules.G_HOLD,IsosangRules.VOLLEY_HOLD,IsosangRules.LUNGE_FREE,IsosangRules.CAGE_HOLD,IsosangRules.FINALE_HOLD};
    /** How long each hit runs (ticks) until its user is back in their stance: the clients draw its motion this long (2.81.0: and R's; 2.82.0: and H's; 2.83.0: and G's). */
    public static final int[] LENGTH={0,18,18,25,32,22,30,24,826,IsosangRules.G_HOLD,IsosangRules.VOLLEY_LENGTH,IsosangRules.LUNGE_LENGTH,IsosangRules.CAGE_LENGTH,IsosangRules.FINALE_LENGTH};
    /** A press this many ticks or fewer before the next hit may start is kept, and starts it then. */
    public static final int BUFFER=8;
    /** The next hit must start within this many ticks of when it may, or the combo breaks. */
    public static final int GRACE=24;
    /** The rest once the fourth hit is over, or once a late hit broke the combo (ticks), as the other characters' attacks. */
    public static final int REST=24;
    /** Whether {@code kind} is a hit (1 to 4, the empowered 5 and 6). */
    public static boolean valid(int kind){return kind>=1&&kind<=THRUST;}
    /**
     * Whether {@code kind} is a swing the clients draw: a hit, R's sweep (2.81.0), H's sword formation (2.82.0), G (2.83.0)
     * or H's basic attack (2.95.0, VOLLEY: its swords only, the body standing on in the formation's stance).
     */
    public static boolean drawn(int kind){return valid(kind)||kind==SWEEP||kind==FORMATION||kind==ULTIMATE||kind==VOLLEY||kind==LUNGE||kind==CAGE||kind==FINALE;}
    /** The place of hit {@code kind} in the combo (1 to 4): the empowered first is the first, the empowered fourth the fourth. */
    public static int position(int kind){return kind==THROWS?1:kind==THRUST?4:kind;}
    /** The hit at {@code position} in the combo, empowered or not: only the first and the fourth change. */
    public static int kind(int position,boolean empowered){return empowered&&position==1?THROWS:empowered&&position==4?THRUST:position;}
    /** How many blows hit {@code combo} lands (1 to 6; R's sweep, 7, counts as one: it strikes tick by tick as it passes). */
    public static int blows(int combo){return drawn(combo)?BLOWS[combo].length:0;}
    /** The tick into hit {@code combo} its blow {@code i} lands on. */
    public static int blowAt(int combo,int i){return BLOWS[combo][i];}
    /** The tick into hit {@code combo} its last blow lands on. */
    public static int lastBlow(int combo){return BLOWS[combo][BLOWS[combo].length-1];}
    /** The damage of hit {@code combo}'s blow {@code i}. */
    public static float damage(int combo,int i){return DAMAGE[combo][i];}
    /** The damage of hit {@code combo}'s blow {@code i}, in the empowered combo or not: the second and third half as hard again. */
    public static float damage(int combo,int i,boolean empowered){float d=DAMAGE[combo][i];return empowered&&(combo==2||combo==3)?d*EMPOWER:d;}
    /** All of hit {@code combo}'s blows together. */
    public static float total(int combo){float t=0;for(float d:DAMAGE[combo])t+=d;return t;}
    /** All of hit {@code combo}'s blows together, in the empowered combo or not. */
    public static float total(int combo,boolean empowered){float t=0;for(int i=0;i<blows(combo);i++)t+=damage(combo,i,empowered);return t;}
    /** The hit after {@code combo}: the next of the combo, or the first again after the fourth (or none). */
    public static int next(int combo){return combo>=1&&combo<4?combo+1:1;}
    /** The hit after {@code kind} in the empowered combo or not: the empowered first is followed by the second, the third by the empowered fourth. */
    public static int next(int kind,boolean empowered){int at=position(kind);return kind(at>=1&&at<4?at+1:1,empowered);}
    /** When the hit after {@code combo} (begun at {@code start}) may start. */
    public static long nextAt(int combo,long start){return start+STEP[combo];}
    /**
     * The tick the attack is ready again after the combo whose hit {@code combo} began at {@code start}, if it broke by
     * {@code now} (the next hit did not start in time), or -1 while it holds (or has nothing to break: none yet, or the
     * fourth hit ended it).
     */
    public static long broken(int combo,long start,long now){
        int at=position(combo);if(at<1||at>=4)return -1;
        long late=nextAt(combo,start)+GRACE;
        return now>late?late+1+REST:-1;
    }
    /** The tick the attack is ready again after the fourth hit begun at {@code start}: once it is over, a rest on. */
    public static long rested(long start){return rested(4,start);}
    /** The same after the fourth hit of either kind ({@code kind} 4 or the empowered {@link #THRUST}). */
    public static long rested(int kind,long start){return start+LENGTH[kind]+REST;}
    /** Whether hit {@code combo} begun at {@code start} still holds its user at {@code now}. */
    public static boolean holding(int combo,long start,long now){return valid(combo)&&now>=start&&now<start+HOLD[combo];}
    /**
     * The tick a blow staggers whom it strikes until (Stagger.isosang): the attacker's next blow at full speed (the next
     * of the same hit, else the next hit's first, started as soon as it may) and Stagger.PAST ticks more; the fourth hit's
     * slash staggers them {@code last} ticks. {@code past} is Stagger.PAST.
     */
    public static long staggerUntil(int combo,int blow,long start,long now,int past,int last){return staggerUntil(combo,blow,start,now,past,last,false);}
    /**
     * The same in the empowered combo or not (2.80.0): a thrown sword's next is the next sword thrown, reaching them
     * {@link #SWORD_LAG} ticks later, or the second hit's first blow; the third's last, the empowered fourth's giant sword
     * reaching where they stood before its leap, {@link #GIANT_LAG} ticks after it is thrown (2.80.1).
     */
    public static long staggerUntil(int combo,int blow,long start,long now,int past,int last,boolean empowered){
        // (2.95.0) H's basic attack: until its next sword lands, its last {@code last} ticks (the next volley is 3 s off);
        // (2.96.0) T the same, until its next blow (a sword or a slash)
        if(combo==VOLLEY||combo==LUNGE)return blow+1<blows(combo)?Math.max(now,start+blowAt(combo,blow+1))+past:now+last;
        if(position(combo)>=4)return now+last;
        int after=next(combo,empowered);
        long next=blow+1<blows(combo)?start+blowAt(combo,blow+1)+(combo==THROWS?SWORD_LAG:0):nextAt(combo,start)+blowAt(after,0)+(after==THRUST?GIANT_LAG:0);
        return Math.max(now,next)+past;
    }

    // ───────────────────────────── reach ─────────────────────────────

    /** Where hit {@code combo}'s blows reach (blocks from its user's feet): a body this close in counts as in the way. */
    public static final double NEAR=-.5,LOW=-1.2,HIGH=2.8;
    /** The diagonal cut (its crescent wraps round the front and the left); the ring's radius all round. */
    public static final double CUT_REACH=4.2,CUT_HALF_WIDTH=3.4,SPIN_RADIUS=3.9;
    /**
     * Hit 3 (2.79.2): the first three cuts' way (down, back up in the same place, and their mirror, each a wide sweep
     * across the front) and the fourth's, the biggest.
     */
    public static final double CROSS_REACH=3.9,CROSS_HALF_WIDTH=3.0,BIG_REACH=4.3,BIG_HALF_WIDTH=3.8;
    /**
     * The finisher's slash, thrown flying (2.77.2, "4타는 참격을 날리는건데"): a crescent across the way it was thrown
     * (leaning back from its leading edge, for the eye), from {@link #WAVE_START} blocks ahead of its user to
     * {@link #WAVE_RANGE} over {@link #WAVE_TICKS} ticks (fast at first, slowing: 2.78.0, nearer and slower, as the
     * user's video from above shows it), {@link #WAVE_HALF_START} to {@link #WAVE_HALF_END} blocks to either side as it
     * widens, its tips trailing {@link #WAVE_CURVE_START} to {@link #WAVE_CURVE_END} blocks behind its middle,
     * {@link #WAVE_DEPTH} deep. It strikes whoever its band passes over, once; and whoever is right in front as it is thrown.
     * (2.79.2 draft, the user's videos of hit 4 from above, behind and the front at a tenth of the speed: it flies only a
     * moment, out to about 10 blocks ahead ("참격이 더 멀리가도 될듯 5블록 정도": 5 blocks further than the first draft) and
     * 8 wide, its tips curving back toward its user, and hangs there; the
     * judgement follows what is seen. 2.78.0 had it fly 9.5 blocks over 0.8 s.)
     */
    public static final double WAVE_START=1.2,WAVE_RANGE=10.0,WAVE_HALF_START=2.6,WAVE_HALF_END=4.1,WAVE_CURVE_START=.7,WAVE_CURVE_END=2.6,WAVE_DEPTH=1.8;
    /** Ticks the slash flies (2.79.2 draft, the user's videos: a moment, 0.2 s for its 10 blocks, then it hangs where it stopped). */
    public static final int WAVE_TICKS=4;
    /** As far as any blow of the attack reaches (blocks): who could be struck at all is looked for within this. */
    public static final double MAX_REACH=WAVE_RANGE+WAVE_HALF_END;

    /** How far ahead of its user's feet hit {@code combo}'s blow {@code blow} reaches (blocks; the ring's radius all round). */
    public static double reach(int combo,int blow){
        if(combo==3)return blow<3?CROSS_REACH:BIG_REACH;
        if(combo==THROWS)return SWORD_RANGE;
        if(combo==THRUST)return GIANT_RANGE;
        return combo==2?SPIN_RADIUS:combo==4?WAVE_RANGE:CUT_REACH;
    }
    /** How far to either side of the way hit {@code combo}'s blow {@code blow} reaches (blocks). */
    public static double halfWidth(int combo,int blow){
        if(combo==3)return blow<3?CROSS_HALF_WIDTH:BIG_HALF_WIDTH;
        if(combo==THROWS)return SWORD_RADIUS;
        if(combo==THRUST)return GIANT_HALF_WIDTH;
        return combo==2?SPIN_RADIUS:combo==4?WAVE_HALF_END:CUT_HALF_WIDTH;
    }
    /** How near (blocks ahead of its user's feet, behind them below 0) hit {@code combo}'s blow {@code blow} begins to reach. */
    public static double near(int combo,int blow){return NEAR;}
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
    /**
     * Whether hit {@code combo} cannot be guarded: the finisher (either kind), as the other characters' fourth hits; R's
     * sweep (2.81.0, the user's choice); and Y's swords (2.97.0: they come down from above, round whom they cage).
     */
    public static boolean unblockable(int combo){return position(combo)==4||combo==SWEEP||combo==CAGE||combo==FINALE;}

    // ───────────────────────────── the empowered first: three swords thrown ─────────────────────────────

    /**
     * The empowered first's swords (2.80.0, the user's videos of E, read from above and from behind and confirmed by the
     * user: "맞음, 이대로"): at each of its blows a sword flashes out of a burst of starlight beside its user (low at the
     * front-right, at the right hand, at the left: {@link #SWORD_FROM}, forward, up and right of their feet) and flies
     * the way they look then, {@link #SWORD_SPEED} blocks a tick, at the point their sight meets first (a block or a body,
     * {@link #SWORD_AIM} blocks on at most, {@link #SWORD_AIM_MIN} at least), on out to {@link #SWORD_RANGE} blocks or the
     * first block in its way; it strikes the first body it comes within {@link #SWORD_RADIUS} of, and is spent.
     */
    public static final double[][] SWORD_FROM={{.7,.55,.6},{.6,1.25,.5},{.6,1.05,-.6}};
    public static final double SWORD_SPEED=3,SWORD_RANGE=16,SWORD_RADIUS=.4,SWORD_AIM=16,SWORD_AIM_MIN=3;
    /** Ticks a thrown sword takes to reach a body near its user (for the stagger: until the next could land). */
    public static final int SWORD_LAG=2;
    /** How far a sword thrown {@code ticks} ago has flown (blocks), stopped at {@code most} (its range, or the block in its way). */
    public static double swordFlown(double ticks,double most){return Math.max(0,Math.min(most,ticks*SWORD_SPEED));}
    /** Whether a sword thrown {@code ticks} ago is still on its way to {@code most} blocks. */
    public static boolean swordFlying(double ticks,double most){return ticks>=0&&ticks*SWORD_SPEED<most+SWORD_SPEED;}

    // ───────────────────────────── the empowered fourth: the giant sword ─────────────────────────────

    /**
     * The empowered fourth (2.80.1, user request: "4타 뒤로 5블록 이동하고 그리고 검 안 날리는 거 같은데? 4타만 0.1배속으로 찍은
     * 영상이야 파란색 검이 날아가는 거 안 보여?"; 2.80.0 stepped back 1 block and thrust a giant blade 7.5 blocks ahead;
     * 2.80.2, user request: "2칸 뒤로 움직이게 바꾸고"): its
     * user leaps {@link #BACKSTEP} blocks straight back over ticks {@link #BACKSTEP_FROM} to {@link #BACKSTEP_TO}, fast at
     * first and slowing to a stop ({@link #backstep}; less if a block is in the way behind them: the room, IsosangAttack.room),
     * as a giant sword shows beside them; at its blow they throw it flying the way they faced as the hit began, from where
     * they are then: its point from {@link #GIANT_START} blocks ahead of them on out to {@link #GIANT_RANGE}, or the first
     * block in its way, {@link #GIANT_SPEED} blocks a tick ({@link #giantHead}); it stops there and hangs a moment. It strikes
     * everyone it passes ({@link #GIANT_HALF_WIDTH} to either side of its way), each once, takes no guard, and throws them
     * on the way it flies ({@link #THRUST_FLY} a tick out and {@link #THRUST_FLY_UP} up, as Star Platinum's finisher: some
     * 10 blocks on flat ground).
     */
    public static final double BACKSTEP=2.0,THRUST_FLY=1.75,THRUST_FLY_UP=.5;
    public static final int BACKSTEP_FROM=1,BACKSTEP_TO=4;
    /**
     * The giant sword's flight (2.80.1, the user's video at a tenth of the speed: it shows at their left as they leap back,
     * tilted up ahead, and a frame later flies on point first to some 7 blocks ahead of them, where blue blades burst out
     * of it; 2.80.2: 8 blocks, a little past that, as the leap is 2 blocks now (10 reached past where they stood 5 blocks
     * back)): where its point is as it is thrown (blocks ahead of them), how far it may fly, how fast (blocks a tick), how
     * wide a way it strikes and how high its way runs over their feet.
     */
    // (2.80.4, user request: "검 아주 조금만 더 느리게 날아가도 될듯": 2 blocks a tick, 40 a second; it was 3, 60: its 5.3
    // blocks from where it shows to where it stops now take some 0.13 s, not 0.09)
    public static final double GIANT_START=2.7,GIANT_RANGE=8,GIANT_SPEED=2,GIANT_HALF_WIDTH=1.2,GIANT_HEIGHT=1.0;
    /** Ticks the giant sword takes from its throw to reach a body a little in front of where they stood before the leap (for the stagger: until it could land). */
    public static final int GIANT_LAG=2;
    /** How far back of where it began the empowered fourth has carried its user {@code ticks} in (blocks): a leap, fast at first and slowing to a stop. */
    public static double backstep(double ticks){return backstep(ticks,BACKSTEP);}
    /** The same with {@code room} blocks behind them at most (a block in the way: IsosangAttack.room). */
    public static double backstep(double ticks,double room){
        double u=Math.max(0,Math.min(1,(ticks-BACKSTEP_FROM)/(BACKSTEP_TO-BACKSTEP_FROM)));
        return Math.max(0,Math.min(room,BACKSTEP))*(1-(1-u)*(1-u));
    }
    /** How far ahead of where it was thrown from the giant sword's point is {@code ticks} after the throw (blocks), stopped at {@code most}. */
    public static double giantHead(double ticks,double most){return Math.min(Math.max(most,GIANT_START),GIANT_START+Math.max(0,ticks)*GIANT_SPEED);}
    /** Ticks after the throw the giant sword stops, {@code most} blocks on. */
    public static double giantArrives(double most){return Math.max(0,(most-GIANT_START)/GIANT_SPEED);}
    /** Whether the giant sword thrown {@code ticks} ago still flies (it strikes on its way until it stops). */
    public static boolean giantFlying(double ticks,double most){return ticks>=0&&ticks<=Math.ceil(giantArrives(most));}
    /**
     * Whether the giant sword's way has reached a body {@code ticks} after the throw: a point ahead, to the right and up
     * from where it was thrown (blocks; a body {@code halfWidth} across), from {@link #NEAR} to its point, within
     * {@link #GIANT_HALF_WIDTH} to either side.
     */
    public static boolean giantOver(double ticks,double most,double forward,double side,double height,double halfWidth){
        if(height<LOW||height>HIGH||Math.abs(side)>GIANT_HALF_WIDTH+halfWidth)return false;
        return forward+halfWidth>=NEAR&&forward-halfWidth<=giantHead(ticks,most);
    }
    /** When (ticks after the throw) the giant sword's point reaches a body {@code forward} blocks ahead of where it was thrown from, {@code halfWidth} across. */
    public static double giantReaches(double forward,double halfWidth){return Math.max(0,(forward-halfWidth-GIANT_START)/GIANT_SPEED);}

    // ───────────────────────────── R: the giant sword's sweep ─────────────────────────────

    /**
     * R (2.81.0, user request: "이번에는 R스킬 거대한 검을 소환해서 전방을 크게 베는거야 … 맞아도 넉백 없어 데미지 20이고 검은 10블록
     * 이내에 내가 쳐다보고 있는 적(쉬프트 락 기준으로 십자선으로 보고 있는 적)까지 닿을 수 있어 앞에 적이 없다면 내 바로 앞을 베어내",
     * and the user's videos of it from above and from behind at a tenth of the speed, read on a sheet the user answered;
     * their choices: the whole sweep moves out to the foe, everyone it passes is struck, a 20 s cooldown, no guard takes
     * it; 2.81.1, user requests: "사거리를 30블록으로 늘리자 그러니까 바라보는 적이 30블록 이내에 있으면 검이 적한테 이동해서
     * 밴다는 뜻임", "정확히는 검이 적에게 순간이동하는게 맞음", "아니다 그냥 r은 원래처럼 앞만 베고 R홀드를 해야지 보고 있는 적에게
     * 검이 순간이동 되게 만들자"): a giant sword of blue light shows in the air ahead of its user, its pommel {@link #SWEEP_PIVOT_UP} up and
     * {@link #SWEEP_PIVOT_FW} ahead of where the sweep stands, its blade out to their right and back; it hangs there a
     * moment and sweeps level round its pommel from there across the front to their left and back ({@link #sweepAngle}),
     * its point {@link #SWEEP_REACH} out. R sweeps right in front of its user. R held ({@link #R_HOLD_TICKS}) with a foe
     * in the crosshair within {@link #SWEEP_AIM} blocks sends the sword to them: the whole of it stands out toward them,
     * so that the middle of its blade passes them ({@link #sweepShift}); with none, it sweeps right in front of its user as
     * R does. It strikes everyone it passes once, {@link #SWEEP_DAMAGE}, takes no guard and throws no one. A swing of its
     * own on the clients: kind {@link #SWEEP}.
     */
    public static final int SWEEP=7;
    /** Its damage (the user's word). */
    public static final float SWEEP_DAMAGE=20;
    /** Ticks R is not ready again once it is over (the user's choice: 20 s), as every skill's cooldown, from its end. */
    public static final int R_COOLDOWN=400;
    /**
     * How long R is held down for R held (ticks, 2.81.1): as long as Kageno's T held is (KagenoRules.HOLD, 0.4 s). Let go
     * sooner, it is R, asked as the key comes up; held this long, it is R held, asked then, the key still down. The
     * client's call: the server is asked for one or the other (IsosangAttack.R, R_HELD).
     */
    public static final int R_HOLD_TICKS=8;
    /**
     * How far the crosshair's foe may be (blocks along the sight, the user's word: 10 in 2.81.0, 30 since 2.81.1, the sword
     * standing out as much as {@code SWEEP_AIM-SWEEP_PIVOT_FW-SWEEP_MEET} toward them) and how wide the sight takes a body
     * (blocks round its box).
     */
    public static final double SWEEP_AIM=30,SWEEP_AIM_SLACK=.5;
    /**
     * The sword (the videos, measured on the character's height): its pommel, which it turns round, this far ahead of
     * where the sweep stands and this high (as the sheet the user answered read it: 1.3 ahead, 2.7 up); its length from
     * the pommel to its point (measured again on the video from above, where the whole sword lies across the picture:
     * some 8.5 blocks, the empowered fourth's giant sword, SpiritSword, 8.5/3.5 times its size; the sheet had "about 10");
     * and, measured from the pommel, the sweep's hub, which it strikes as soon as it turns (the grip passes over).
     */
    public static final double SWEEP_PIVOT_FW=1.3,SWEEP_PIVOT_UP=2.7,SWEEP_REACH=8.5,SWEEP_HUB=1.0;
    /** How high over where the sweep stands a body may be and still be struck (blocks; the blade is in the air). */
    public static final double SWEEP_HIGH=3.6;
    /** Where along the blade a foe in the crosshair is met (blocks from the pommel): a little past its middle. */
    public static final double SWEEP_MEET=5.0;
    /** The tick into R its sweep first strikes: the first tick it has turned. */
    static final int SWEEP_BLOW=10;
    /**
     * The sweep's way (the video from above, frame by frame: the blade's angle off the front, right +, by the second into
     * R): held out to the right and back until 0.465 s, then round fast, already some 1000° a second in its first frames,
     * across the front at 0.59 s and on to the left, slowing hard from 0.65 s to a stop 15° behind the left at 0.76 s.
     * Seconds and degrees; a monotone cubic through them, still at either end.
     */
    static final double[] SWEEP_T={.465,.49,.59,.645,.675,.76},SWEEP_A={123,103,0,-72,-90,-105};
    /** Where the sweep begins and ends (degrees off the front, right +). */
    public static final double SWEEP_FROM=SWEEP_A[0],SWEEP_TO=SWEEP_A[SWEEP_A.length-1];
    /** When it begins, crosses the front and stops (seconds into R). */
    public static final double SWEEP_START=SWEEP_T[0],SWEEP_FRONT=SWEEP_T[2],SWEEP_END=SWEEP_T[SWEEP_T.length-1];
    private static final double[] SWEEP_M=slopes(SWEEP_T,SWEEP_A);
    /** Fritsch–Carlson slopes for a monotone cubic through (t, a), still at its ends. */
    private static double[] slopes(double[] t,double[] a){
        int n=t.length;double[] d=new double[n-1],m=new double[n];
        for(int i=0;i<n-1;i++)d[i]=(a[i+1]-a[i])/(t[i+1]-t[i]);
        for(int i=1;i<n-1;i++){
            if(d[i-1]*d[i]<=0){m[i]=0;continue;}
            double w1=2*(t[i+1]-t[i])+(t[i]-t[i-1]),w2=(t[i+1]-t[i])+2*(t[i]-t[i-1]);
            m[i]=(w1+w2)/(w1/d[i-1]+w2/d[i]);
        }
        return m;
    }
    /** The blade's angle off the front (degrees, right +) {@code sec} seconds into R. */
    public static double sweepAngle(double sec){
        int n=SWEEP_T.length;
        if(sec<=SWEEP_T[0])return SWEEP_A[0];
        if(sec>=SWEEP_T[n-1])return SWEEP_A[n-1];
        int i=0;while(sec>SWEEP_T[i+1])i++;
        double h=SWEEP_T[i+1]-SWEEP_T[i],u=(sec-SWEEP_T[i])/h,u2=u*u,u3=u2*u;
        return (2*u3-3*u2+1)*SWEEP_A[i]+(u3-2*u2+u)*h*SWEEP_M[i]+(-2*u3+3*u2)*SWEEP_A[i+1]+(u3-u2)*h*SWEEP_M[i+1];
    }
    /** How far round the sweep has gone {@code sec} seconds into R, 0 to 1. */
    public static double sweepDone(double sec){return (SWEEP_FROM-sweepAngle(sec))/(SWEEP_FROM-SWEEP_TO);}
    /**
     * How far ahead of its user the sweep stands for a foe in the crosshair {@code distance} blocks away (level): out so
     * that the blade, {@link #SWEEP_MEET} along, passes them, but never back from right in front of its user, and never
     * so far that it would meet a foe past {@link #SWEEP_AIM}.
     */
    public static double sweepShift(double distance){return Math.max(0,Math.min(SWEEP_AIM-SWEEP_PIVOT_FW-SWEEP_MEET,distance-SWEEP_PIVOT_FW-SWEEP_MEET));}
    /**
     * Whether the blade passes over a body while it turns from {@code fromDeg} to {@code toDeg} (a point ahead, to the
     * right and up from where the sweep stands, blocks; a body {@code halfWidth} across): within its reach of the pommel
     * and within the angles it swept (widened by how wide the body looks from there); one under the hub as the sweep
     * begins.
     */
    public static boolean swept(double fromDeg,double toDeg,double forward,double side,double height,double halfWidth){
        if(height<LOW||height>SWEEP_HIGH||toDeg>=fromDeg)return false;
        double dx=forward-SWEEP_PIVOT_FW,dz=side,r=Math.sqrt(dx*dx+dz*dz);
        if(r-halfWidth>SWEEP_REACH)return false;
        if(r<=SWEEP_HUB+halfWidth)return fromDeg>=SWEEP_FROM-1e-9;
        double ang=Math.toDegrees(Math.atan2(dz,dx)),m=Math.toDegrees(Math.asin(Math.min(1,halfWidth/r)));
        return ang<=fromDeg+m&&ang>=toDeg-m&&ang<=SWEEP_FROM+m&&ang>=SWEEP_TO-m;
    }
    /** When (seconds into R) the blade passes a body at that point, or -1 if it never does. */
    public static double sweepReaches(double forward,double side,double height,double halfWidth){
        for(int i=0;i<=(int)Math.ceil((SWEEP_END-SWEEP_START)*400);i++){
            double t=SWEEP_START+i/400.0;
            if(swept(sweepAngle(Math.max(0,t-1/400.0)),sweepAngle(t),forward,side,height,halfWidth)||i==0&&swept(SWEEP_FROM,SWEEP_FROM-1e-6,forward,side,height,halfWidth))return t;
        }
        return -1;
    }

    // ───────────────────────────── the finisher's flying slash ─────────────────────────────

    /**
     * How far the flight has gone, 0 to 1, {@code ticks} after the slash was thrown: fast at first, slowing to a stop
     * (2.78.0: a gentler curve than the square, so it is still drifting out past a quarter of a second as the video shows).
     */
    public static double waveFlown(double ticks){double u=Math.max(0,Math.min(1,ticks/WAVE_TICKS));return 1-Math.pow(1-u,1.6);}
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

    // ───────────────────────────── H: the sword formation ─────────────────────────────

    /**
     * H (2.82.0, user request: "다음은 H스킬이야 엄청 많은 검을 박고 검 위로 올라가 검 위에 올라가 있을때는 움직 일 수 없어 영상이랑
     * 똑같이 구현해줘 이펙트도", the user's video of it (c213bfd7, 60 fps, 42.5 s) read frame by frame on a sheet; their
     * answers: as long as in the video (some 39 s), only R while on the sword ("일단은 R만 됨"), a 60 s cooldown; and
     * "데미지는 처음 한 번 H 박을때 30데미지 그리고 5초마다 5 데미지"): Lee So-sang leaps up where she stands, a thread of
     * light from her sword to the ground opening a pool of black there, swords showing in the sky; one is driven into the
     * ground right under her and she comes down onto its pommel, and the rest rain down with blue lightning, planting
     * themselves in rings round the formation's middle out to {@link #FORMATION_RADIUS} blocks, blue crystal flowers
     * blooming where they strike; ink mountains rise round its edge and mist drifts by. She stands on that pommel, unable
     * to walk (only R may be used), until the formation ends ({@link #H_END}): every sword flashes blue, turns to ink and
     * is gone, and she drops down. The formation does not lie round her: she stands on its outer ring, its middle
     * {@link #FORMATION_STAND_BACK} blocks ahead of her (2.82.4, user request: "가운데에서 점프해서 올라가는게 아니고 H를 쓰면
     * 내가 점프를 하고 그 자리에 검이 하나 있어야해 그러니까 H는 플레이어 기준 중앙으로 나오는게 아니고 외곽부분으로
     * 나와야함"; until 2.82.2 it lay round her and she stood on a sword in its middle, in 2.82.3 she leapt from its middle
     * onto its inner ring).
     * Whoever is inside it as the swords strike is struck once ({@link #FORMATION_IMPACT}), and every
     * {@link #FORMATION_EVERY} ticks after that again ({@link #FORMATION_TICK}); no guard takes it, no one is thrown.
     */
    public static final int FORMATION=8;
    /**
     * How far out the formation reaches (blocks, level, from its middle; the video: swords out to some 10 blocks; 8 in
     * 2.82.2, user request: "그리고 범위 조금만 줄여", then 10 again in 2.82.3: "아니다 범위는 원래대로 늘리고"; 14.5 in 2.82.16,
     * the whole ground inside the mountains; 10 in 2.82.17, user request: "H 범위 8블록으로 줄여 그대신 지금 산 간격은 똑같해야
     * 하니까 산의 X값은 그대로 두고 Y값만 올려", "전체 범위를 줄여달라는거였는대", then "10블록으로 줄이고 사진만 뽑아봐": all
     * of it on the ground within this, its swords, its water, its whirlpool, its first ink and the smoke at its rim; the
     * mountains where they were, only higher; then "전체 all", the mountains in too, "산도 같은 비율로 줄임", and "12블록으로
     * 늘리는 대신에 중앙 빈 부분이 너무 크니까 중앙 빈 부분을 조금 줄여": 12, all of 2.82.16 at 12/14.5 of its size, the
     * mountains floating as high as before, its empty middle a little smaller) and how far under and over its ground a body
     * is still in it.
     */
    public static final double FORMATION_RADIUS=12,FORMATION_LOW=-1.2,FORMATION_HIGH=5;
    /** The damage as the swords strike (the user's word: 30) and every {@link #FORMATION_EVERY} ticks after (5). */
    public static final float FORMATION_IMPACT=30,FORMATION_TICK=5;
    /** The tick into H the swords strike (as the rain of swords crashes in), and how often it strikes again after (the user's word: every 5 s). */
    public static final int FORMATION_IMPACT_AT=20,FORMATION_EVERY=100;
    /** Ticks H is not ready again once it is over (the user's choice: 60 s), from its end, as every skill's cooldown. */
    public static final int H_COOLDOWN=1200;
    /** How high she stands on the pommel of the sword she stands on (blocks over the ground; the video: about her own height) and how high she leaps. */
    public static final double FORMATION_STAND=1.7,H_LEAP_HEIGHT=3.0;
    /**
     * H's times (seconds; the video from 0.4 s): she leaps (0.6 s in the video), is at the top, dives (1.18), lands (1.25:
     * 2.82.4, on the pommel of the sword struck under her; until 2.82.3 on the ground, hopping up onto a sword after);
     * that sword strikes, a black spike shooting up out of the pool (1.1); the rest show in the sky (0.75) and land (1.22
     * to 1.52); the formation ends (40.72: the swords flash) and she drops (41.0 to 41.4).
     */
    public static final float H_LEAP=.2f,H_PEAK=.42f,H_DIVE=.78f,H_LANDED=.86f,H_PLANT=.7f,H_SKY=.35f,H_RAIN0=.82f,H_RAIN1=1.12f,
        H_END=40.32f,H_DROP=40.6f,H_DOWN=41f;
    /** Whether a body ({@code dx}, {@code dz} level from the middle, {@code dy} over its ground, {@code halfWidth} across) is in the formation. */
    public static boolean formationOver(double dx,double dz,double dy,double halfWidth){
        return dy>=FORMATION_LOW&&dy<=FORMATION_HIGH&&Math.hypot(dx,dz)<=FORMATION_RADIUS+halfWidth;
    }
    /** Whether the formation strikes on its tick {@code age}: as the swords strike, then every FORMATION_EVERY ticks until it ends. */
    public static boolean formationStrikes(long age){
        return age==FORMATION_IMPACT_AT||age>FORMATION_IMPACT_AT&&(age-FORMATION_IMPACT_AT)%FORMATION_EVERY==0&&age<H_END*20;
    }
    /** The damage of the formation's strike on its tick {@code age}. */
    public static float formationDamage(long age){return age==FORMATION_IMPACT_AT?FORMATION_IMPACT:FORMATION_TICK;}
    /** All the formation strikes on one who stays in it to its end. */
    public static float formationTotal(){float t=0;for(long a=0;a<LENGTH[FORMATION];a++)if(formationStrikes(a))t+=formationDamage(a);return t;}
    /**
     * How high her body is drawn over the formation's ground {@code t} seconds into H (blocks): the leap up, the hang, the
     * dive down onto the pommel of the sword struck under her (2.82.4), stood there, and the drop.
     */
    public static double planned(double t){
        if(t<H_LEAP)return 0;
        if(t<H_PEAK){double u=(t-H_LEAP)/(H_PEAK-H_LEAP);return H_LEAP_HEIGHT*(1-(1-u)*(1-u));}
        if(t<H_DIVE){double u=(t-H_PEAK)/(H_DIVE-H_PEAK);return H_LEAP_HEIGHT+.18*Math.sin(Math.PI*u);}
        if(t<H_LANDED){double u=(t-H_DIVE)/(H_LANDED-H_DIVE);return H_LEAP_HEIGHT-(H_LEAP_HEIGHT-FORMATION_STAND)*u*u;}
        return raised(t);
    }
    /**
     * How high her body really is over the formation's ground {@code t} seconds into H (blocks): on the ground until she
     * leaps, up as high as the pommel she will stand on as she leaps (2.82.4: she never comes down to the ground between),
     * there until the drop, and down. Her own client moves her so (IsosangClient.drive; a boss, the server) and the server
     * keeps her where she stood; {@link #planned} draws the leap over it.
     */
    public static double raised(double t){
        if(t<H_LEAP)return 0;
        if(t<H_PEAK){double u=(t-H_LEAP)/(H_PEAK-H_LEAP);return FORMATION_STAND*u*u*(3-2*u);}
        if(t<H_DROP)return FORMATION_STAND;
        if(t<H_DOWN){double u=(t-H_DROP)/(H_DOWN-H_DROP);return FORMATION_STAND*(1-u*u);}
        return 0;
    }
    /** Whether she is up off the ground {@code t} seconds into H, in the leap or on the sword (held there: the server lets her float). */
    public static boolean onSword(double t){return t>=H_LEAP&&t<H_DOWN;}

    // where the formation's swords lie (2.82.3: here, not with the effects, for the server to know the sword she stands on)

    /**
     * The formation's swords (2.82.16, user request: "H 범위를 조금만 더 넓히고 검 개수는 조금만 줄이고 검이 중앙 제외 모든
     * 범위에 박혀 있게 하자 지금 너무 앞부분에 쏠려있어", then "검 28개", then "뒤가 ㅈㄴ 빈다고 걍 뒤까지 포함에서 칼을 골고루
     * 박으라는건데": FORMATION_RADIUS 14.5, was 10, so the swords lie all over the ground inside the mountains, behind her
     * too, the mountains where they were; 28 swords, were 48 in three rings from 5.5 to 8.7 blocks out, none in the band
     * outside them nor round behind her): how many, from how far out (the empty
     * middle, as since 2.82.3), how far inside the formation's edge the outermost stand, and how far a sword may stray
     * from its even place either way (blocks).
     */
    public static final int FORMATION_SWORDS=28;
    /**
     * (FORMATION_EMPTY 8.3 in 2.82.16, was 5.4: "중앙으로 넘어간 검 있잖아": swords stood in the bare middle the water leaves,
     * which read some 7.5 blocks out from the middle in the game; 5.2 in 2.82.17, the formation 12 blocks wide, "중앙 빈
     * 부분이 너무 크니까 중앙 빈 부분을 조금 줄여": a smaller share of it than 2.82.16's, the water's bare middle as small;
     * FORMATION_EDGE and FORMATION_JITTER at 12/14.5 of 2.82.16's: no two swords within 2.4 blocks.)
     */
    public static final double FORMATION_EMPTY=5.2,FORMATION_EDGE=.5,FORMATION_JITTER=.29;
    /** The golden angle: each sword this far round from the one before, so they spread evenly with no rows. */
    static final double GOLDEN=Math.PI*(3-Math.sqrt(5));
    /**
     * How far behind the formation's middle she stands (blocks, 2.82.4: out toward its edge, on the sword struck under
     * her; the formation's middle is this far ahead of her, the way she faced, the crosshair's; since 2.82.16 swords
     * stand round her and behind her too: "뒤가 ㅈㄴ 빈다고 걍 뒤까지 포함에서 칼을 골고루 박으라는건데"; 8.7 until 2.82.16,
     * 5.6 in 2.82.17 with the formation 12 blocks wide: as before just outside its empty middle, on its innermost
     * swords, the swords round and behind her).
     */
    public static final double FORMATION_STAND_BACK=5.6;
    /** A formation's seed: where its swords lie and how they look follow from it (its swing's id), the same on the server and every client. */
    public static int formationSeed(UUID id){
        return (int)(id.getMostSignificantBits()^(id.getMostSignificantBits()>>>32)^id.getLeastSignificantBits()^(id.getLeastSignificantBits()>>>32));
    }
    /** A number in [0, 1) for {@code i}, the same on every machine (StrictMath: the server and its clients agree on where a sword is). */
    public static double hash(int i){double x=StrictMath.sin(i*12.9898+78.233)*43758.5453;return x-Math.floor(x);}
    /** Each sword's seed in a formation of seed {@code s0}, by its place in {@link #formationSwords} (its look follows from it too). */
    public static int swordSeed(int s0,int k){return s0+97*(k+1);}
    /**
     * Where the formation's swords strike the ground: forward and right of its middle (blocks, in the way its user faced).
     * Spread evenly over all of it but its empty middle (2.82.16: "검이 중앙 제외 모든 범위에 박혀 있게"), from
     * FORMATION_EMPTY out to FORMATION_EDGE inside its edge, as a sunflower's seeds lie: the k-th as far out as leaves
     * each the same ground (its radius squared even from the inside out) and the golden angle round from the one before,
     * each a little off its place by its seed. The pattern is turned so that the one as far out as she stands lies right
     * behind the middle, where she stands: that one is the sword she stands on (2.82.4, {@link #standSword}).
     */
    public static double[][] formationSwords(int s0){
        int n=FORMATION_SWORDS,j=standSword();
        double r0=FORMATION_EMPTY,r1=FORMATION_RADIUS-FORMATION_EDGE,turn=Math.PI-j*GOLDEN;
        double[][] out=new double[n][];
        for(int k=0;k<n;k++){
            if(k==j){out[k]=standAt();continue;}
            int seed=swordSeed(s0,k);
            double rad=Math.sqrt(r0*r0+(r1*r1-r0*r0)*(k+.5)/n)+(hash(seed+3)-.5)*2*FORMATION_JITTER;
            rad=Math.max(r0-FORMATION_JITTER,Math.min(r1+FORMATION_JITTER,rad));
            double a=turn+k*GOLDEN+(hash(seed+2)-.5)*2*FORMATION_JITTER/rad;
            out[k]=new double[]{Math.cos(a)*rad,Math.sin(a)*rad};
        }
        return out;
    }
    /**
     * The sword she stands on (2.82.4, user request: "H를 쓰면 내가 점프를 하고 그 자리에 검이 하나 있어야해"): the one struck
     * into the ground right where she leapt, the one of {@link #formationSwords} as far out as she stands. It stands
     * upright, its pommel {@link #FORMATION_STAND} up, and strikes first ({@link #H_PLANT}).
     */
    public static int standSword(){
        double r0=FORMATION_EMPTY,r1=FORMATION_RADIUS-FORMATION_EDGE,f=(FORMATION_STAND_BACK*FORMATION_STAND_BACK-r0*r0)/(r1*r1-r0*r0);
        return (int)Math.max(0,Math.min(FORMATION_SWORDS-1,Math.round(f*FORMATION_SWORDS-.5)));
    }
    /** Where she stands: right behind the formation's middle on its outer ring (forward and right of the middle, blocks). */
    public static double[] standAt(){return new double[]{-FORMATION_STAND_BACK,0};}

    // ───────────────────────────── Z: across the formation's swords ─────────────────────────────

    /**
     * Z (2.93.0, user request: "H 스킬에서 박혀있는 모든 검이 전부 상호작용 되게 바꿔줘 그리고 동시에 z스킬 만들자 쳐다보는
     * 검(쉬프락은 가운데 십자가로 쳐다보는 검)에 자신에게만 보이는 흰색 테두리가 생기고 아래에는 푸른색 꽃이 피어나고 z스킬을
     * 누르면 10초 동안 최대 3번 쳐다보는 검으로 이동 할 수 있어 영상 처럼 10초 안에 3번 다 사용하는 순간 즉시 재사용 쿨타임
     * 20초가 흐르고 또는 3번 다 사용 안 해도 10초가 지나면 재사용 쿨타임 20초가 흐르게", the user's video of it
     * (68d5f592, 60 fps) read on a sheet and confirmed: "맞음, 이대로"; and their answers: the flower too only for her,
     * one press one move, the window counted from the first): while her formation stands, Z moves Lee So-sang onto the
     * pommel of the sword she looks at (the camera's middle, as R held's foe): she is gone into a bubble, flies there as
     * a blue orb trailing white streaks in Z_TRAVEL ticks (the video: some 0.2 s) and stands on it, held there as on the
     * first. Up to Z_USES moves within Z_WINDOW ticks of the first (one press, one move); the moment the last is used,
     * or once the window is over with fewer, Z is not ready again for Z_COOLDOWN ticks.
     */
    public static final int Z_USES=3,Z_WINDOW=200,Z_COOLDOWN=400,Z_TRAVEL=4;
    /**
     * From when into H Z may move her (seconds): every sword planted (the last falls by H_RAIN1, for some 0.22 s); and
     * until the flight would end as the swords flare (H_END).
     */
    public static final float Z_FROM=1.35f;
    /** Whether Z may move her {@code t} seconds into her formation (it would land before the swords flare). */
    public static boolean zTime(double t){return t>=Z_FROM&&t+Z_TRAVEL/20.0<H_END;}
    /**
     * Z's state for one user: the tick its window opened (its first move; -1 for none), how many moves it has had, and
     * the tick Z is ready again (as the server tells every viewer with each of its swings' news).
     */
    public record ZState(long first,int uses,long ready){
        public static final ZState FRESH=new ZState(-1,0,0);
        /** Whether its window is open at {@code now}: moved, fewer than all, its time not up. */
        public boolean open(long now){return uses>0&&uses<Z_USES&&now<first+Z_WINDOW;}
        /** Whether Z may move her at {@code now}: in its open window, or ready again. */
        public boolean usable(long now){return open(now)||now>=ready;}
        /** How many moves are left at {@code now}: what its open window has left, all once ready, none while cooling down. */
        public int left(long now){return open(now)?Z_USES-uses:now>=ready?Z_USES:0;}
    }
    /**
     * Z used at {@code now} (one move): its state after it, or null when it may not be used then (cooling down). The first
     * move opens a window of Z_WINDOW ticks; the last of Z_USES starts its cooldown at once, else it starts as the window
     * is over ({@code free}: the cooldowns are off for its user, it is ready again at once).
     */
    public static ZState zUse(ZState s,long now,boolean free){
        boolean open=s.open(now);
        if(!open&&now<s.ready()&&!free)return null;
        long first=open?s.first():now;int uses=(open?s.uses():0)+1;
        long ready=free?0:uses>=Z_USES?now+Z_COOLDOWN:first+Z_WINDOW+Z_COOLDOWN;
        return new ZState(first,uses,ready);
    }
    /** How far along its flight she is {@code u} through it (0 to 1): quick from the start, easing onto the pommel. */
    public static double zAlong(double u){u=Math.max(0,Math.min(1,u));return 1-(1-u)*(1-u)*(1-u*.4);}
    /** How high over the straight way between the two pommels her flight arcs {@code u} through it (blocks). */
    public static double zArc(double u){u=Math.max(0,Math.min(1,u));return Z_ARC*4*u*(1-u);}
    public static final double Z_ARC=.55;
    /**
     * H's dodge (2.94.0, user request: "그리고 H를 쓰고 단 1회 맞은 데미지 무시하고 랜덤한 검으로 이동되게 바꿔 그 뒤로는 데미지 잘
     * 박히고 단 1회야 평타든 뭐든 다단히트 스킬 박혀도 1대만 씹을 수 있어"): once in each formation, the first blow to reach
     * her does not (none of it: no harm, no stagger, no throw, no cutscene) and she is moved onto a sword of it picked at
     * random, as Z moves her (its flight, its light and its sound; none of Z's moves spent, its window and cooldown as
     * they were, and while Z cools down too). Every blow after it lands, the rest of the same attack's too.
     * It holds from H's start until a move would land too late (as Z's). A blow before the swords stand
     * (before Z_FROM) is kept from her at once, and she moves as they do; one as she flies, as she lands; one while
     * something holds her where she is (a time stop, a Kageno skill), as it lets go.
     */
    public static boolean dodgeTime(double t){return t>=0&&t+Z_TRAVEL/20.0<H_END;}
    /**
     * The sword H's dodge moves her onto: {@code roll} (at random, 0 to {@code swords}-2) over every sword of her formation
     * but the one she stands on ({@code on}).
     */
    public static int dodgeSword(int on,int roll,int swords){
        if(swords<2)return on;
        roll=Math.floorMod(roll,swords-1);return roll<on?roll:roll+1;
    }

    // ───────────────────────────── H's basic attack: 회풍만검 ─────────────────────────────

    /**
     * H's basic attack, 회풍만검 (2.95.0, user request: "이번에는 평타 만들자 영상 처럼 등 뒤에 검 6개가 나와서 바라보고 있는
     * 상대에게 날아감", the user's video of it (5f78fd72, 60 fps) read on a sheet and confirmed: "맞음, 이대로"; and their
     * answers: 2 a sword, every 3 s, with no foe in sight they fly at the crosshair): while she stands on her formation's
     * sword the attack key fans {@link #VOLLEY_SWORDS} swords of light out behind her back one after another (hilts at her
     * back, points out from her left through overhead to her right), throws them one after another up and away, and they
     * wheel round in the air and come down on the foe she looks at from all round, each sticking in and striking once
     * ({@link #VOLLEY_DAMAGE}; no knockback, a basic attack's stagger, a guard takes it). With no foe in her sight they
     * come down where her crosshair meets a block, and strike whoever is there. Its reach is her formation and
     * {@link #VOLLEY_BEYOND} blocks more (2.95.2, {@link #volleyIn}: the foe must be in it; a crosshair beyond it brings
     * them down at its edge, {@link #volleyPoint}). Her body stands on as it does on the sword (the video: hardly a move).
     */
    public static final int VOLLEY=10,VOLLEY_SWORDS=6;
    public static final float VOLLEY_DAMAGE=2;
    /**
     * How long it runs (ticks, its last sword struck and its light gone by then), how long it holds her (its swords all
     * thrown: no Z or R until then), and the ticks before the next once it is over: one every 3 s (the user's choice:
     * "3초마다", from one's start to the next's; its cooldown runs once it is over, as every skill's).
     */
    public static final int VOLLEY_LENGTH=28,VOLLEY_HOLD=10,VOLLEY_COOLDOWN=32;
    /**
     * How far her crosshair's line is followed to find where it meets (blocks from her eyes: past the far edge of the
     * reach wherever on her formation she stands; until 2.95.1 also how far off the foe she looked at might be), and how
     * far off the middle of her view the foe may be (radians): the nearest to it within this is the one she looks at; and
     * how near the point they come down on (no foe) a body must be to be struck there (blocks, its box widened).
     */
    public static final double VOLLEY_RANGE=30,VOLLEY_AIM=Math.toRadians(6),VOLLEY_SPOT=.6;
    /**
     * How far H's basic attack reaches beyond her formation (2.95.2, user request: "평타 사거리는 H 범위 전체에서 3블록 정도
     * 더"; until 2.95.1, VOLLEY_RANGE blocks from her eyes): all round, level out from its middle to FORMATION_RADIUS and
     * this more (15 blocks), from FORMATION_LOW and this under its ground to FORMATION_HIGH and this over it (the user said
     * only "3블록 정도 더": over and under as well is mine). And how far beyond that the swords still follow the foe they
     * were sent at (blocks, all round; past it they come down where it was last).
     */
    public static final double VOLLEY_BEYOND=3,VOLLEY_LEASH=10;
    /**
     * Whether a body ({@code dx}, {@code dz} level from her formation's middle, {@code dy} from its ground up to its feet,
     * {@code halfWidth} across) is within H's basic attack's reach (2.95.2), {@code slack} blocks more all round.
     */
    public static boolean volleyIn(double dx,double dz,double dy,double halfWidth,double slack){
        double out=VOLLEY_BEYOND+slack;
        return dy>=FORMATION_LOW-out&&dy<=FORMATION_HIGH+out&&Math.hypot(dx,dz)<=FORMATION_RADIUS+out+halfWidth;
    }
    /**
     * Where H's basic attack's swords come down with no foe (2.95.2): where her crosshair met ({@code b}: x, y, z from her
     * formation's middle, its ground at y 0) if that is within the reach; else where the line to it from her eyes
     * ({@code a}) goes out of the reach (its edge, or its top or floor); her eyes out of it themselves, the nearest point of
     * the reach to it. Its last entry is 1 if the point was brought in, 0 if not.
     */
    public static double[] volleyPoint(double ax,double ay,double az,double bx,double by,double bz){
        double r=FORMATION_RADIUS+VOLLEY_BEYOND,low=FORMATION_LOW-VOLLEY_BEYOND,high=FORMATION_HIGH+VOLLEY_BEYOND;
        if(by>=low&&by<=high&&bx*bx+bz*bz<=r*r)return new double[]{bx,by,bz,0};
        if(ay<low||ay>high||ax*ax+az*az>r*r){
            double h=Math.hypot(bx,bz),k=h>r?r/h:1;
            return new double[]{bx*k,Math.max(low,Math.min(high,by)),bz*k,1};
        }
        double dx=bx-ax,dy=by-ay,dz=bz-az,s=1,a=dx*dx+dz*dz;
        // the level circle: |a + s d| = r, the root ahead (her eyes inside it)
        if(a>1e-12){double b=ax*dx+az*dz,c=ax*ax+az*az-r*r;s=Math.min(s,(-b+Math.sqrt(Math.max(0,b*b-a*c)))/a);}
        // its top and its floor
        if(dy>1e-12)s=Math.min(s,(high-ay)/dy);else if(dy<-1e-12)s=Math.min(s,(low-ay)/dy);
        s=Math.max(0,s);
        return new double[]{ax+dx*s,ay+dy*s,az+dz*s,1};
    }
    /**
     * When into it (seconds) its swords show behind her back, the first and each after (from the video: 0.17 s for all
     * six, from the middle out), when they are thrown, the first and each after (the video: the same pace, 0.06 s after the
     * last shows), and how long each sticks in the foe before it strikes (the video: the blows are heard and their damage
     * shown some 0.15 s after the swords reach the foe). Each strikes on its blow's tick (BLOWS: 1.0 to 1.25 s, as the
     * video's).
     */
    public static final float VOLLEY_SHOW0=.05f,VOLLEY_SHOW_STEP=.034f,VOLLEY_THROW0=.3f,VOLLEY_THROW_STEP=.034f,VOLLEY_STICK=.15f;
    /** When into the volley sword {@code i} shows behind her back (seconds). */
    public static float volleyShows(int i){return VOLLEY_SHOW0+i*VOLLEY_SHOW_STEP;}
    /** When into the volley sword {@code i} is thrown (seconds). */
    public static float volleyThrown(int i){return VOLLEY_THROW0+i*VOLLEY_THROW_STEP;}
    /** When into the volley sword {@code i} reaches what it comes down on and sticks in (seconds). */
    public static float volleyArrives(int i){return blowAt(VOLLEY,i)/20f-VOLLEY_STICK;}
    /** When into the volley sword {@code i} strikes (seconds): its blow's tick. */
    public static float volleyStrikes(int i){return blowAt(VOLLEY,i)/20f;}
    /**
     * The same for the six swords of swing {@code combo} (2.96.0): H's basic attack's, or T's (LUNGE: shown as the
     * basic attack's, thrown as she sets off on her dash, LUNGE_THROW0, and coming down one a tick from LUNGE_SWORDS).
     */
    public static float volleyShows(int combo,int i){return volleyShows(i);}
    public static float volleyThrown(int combo,int i){return combo==LUNGE?LUNGE_THROW0+i*VOLLEY_THROW_STEP:volleyThrown(i);}
    public static float volleyArrives(int combo,int i){return combo==LUNGE?volleyStrikes(combo,i)-LUNGE_STICK:volleyArrives(i);}
    public static float volleyStrikes(int combo,int i){return combo==LUNGE?(LUNGE_SWORDS+i)/20f:volleyStrikes(i);}
    /** Whether the volley may begin {@code t} seconds into her formation: every sword planted (Z's from), and over before the swords flare. */
    public static boolean volleyTime(double t){return t>=Z_FROM&&t+VOLLEY_LENGTH/20.0<=H_END;}
    /**
     * The tick the volley is ready again after one begun at {@code start} ({@code free}: the cooldowns are off for her, it
     * is ready once this one is over).
     */
    public static long volleyReady(long start,boolean free){return start+VOLLEY_LENGTH+(free?0:VOLLEY_COOLDOWN);}

    // ───────────────────────────── T: the dash through the foe ─────────────────────────────

    /**
     * T (2.96.0, user request: "이번에는 T스킬 만들자 영상처럼 일단 T를 누르면 평타 6검이 날아가는 동시에 빠르게 바라보는 상대에게
     * 돌진 베어내고 적에게 3 참격으로 베이는 동시에 다른 검으로 넘어가는 스킬이야", the user's video of it (e6830cf0, 60 fps, used
     * twice) read on a sheet and confirmed: "맞음, 이대로"; and their answers: 30 in all with a 15 s cooldown, onto the sword
     * farthest from the foe (2.96.1: onto any sword on the other side of the formation from hers), and with no foe toward
     * the crosshair): on her formation's sword the six swords of H's basic
     * attack fan out behind her and are thrown as she dashes at the foe she looks at (picked as the basic attack picks
     * it, within its reach), cutting it once as she comes, and lands low just in front of it at its side (2.96.2, the
     * video read again: lungeLanding; 2.96.0 to 2.96.1 dashed through it and landed 3 blocks beyond); the swords come
     * down on it one after another, and while she flies on (Z's flight) onto a sword of her formation on the other side
     * of its middle from hers (oppositeSwords, at random), three slashes cross on it. With no foe she dashes to where her
     * crosshair meets (brought in to the reach's edge, on the ground) and all of it comes down there on whoever is there. Ten blows (LUNGE_KIND: the dash,
     * the swords, the slashes; 6, 2 each and 4 each), a guard takes each, no knockback, a basic attack's stagger until the
     * next. It holds her until she stands on the far sword (no Z, R or basic attack until then) and she may use it again
     * from then (2.96.4, user request: "T는 아직 쿨타임 없게 만들어줘 속도는 영상처럼", the user's video using it again every
     * 0.67 s or so: no cooldown for now), its blows still to come coming down while the next one goes.
     */
    public static final int LUNGE=11;
    /**
     * T's ticks (the video, from the press: the fan at 0.1 s, the dash at 0.2 s, its light on the foe by 0.25 s, she low
     * in front of it by 0.3 s, the swords coming down 0.55 to 0.8 s, gone to the far sword by 0.7 s, the slashes 0.7 to
     * 0.9 s, settled at 1.25 s): she sets off ({@link #LUNGE_GO}), cuts the foe as its blow lands (its first, 6), is down
     * in front of it ({@link #LUNGE_LAND}), flies on to the far sword ({@link #LUNGE_HOP}, over Z's {@link #Z_TRAVEL}), the first sword
     * strikes ({@link #LUNGE_SWORDS}, one a tick), and it is over ({@link #LUNGE_LENGTH}: its last slash cut, her body
     * settled). It holds her until she stands on the far sword ({@link #LUNGE_FREE}, HOLD[LUNGE]: 0.7 s; 2.96.4, the user's
     * video: each next T 0.67 s or so after the one before, as she stands on the far sword), when she may use it (or
     * anything else) again; its cooldown from then: none for now (2.96.4, user request: "T는 아직 쿨타임 없게 만들어줘";
     * 2.96.0 to 2.96.3 held her all 1.25 s and it was ready 15 s after).
     */
    public static final int LUNGE_GO=3,LUNGE_LAND=7,LUNGE_HOP=10,LUNGE_SWORDS=11,LUNGE_LENGTH=25,LUNGE_FREE=LUNGE_HOP+Z_TRAVEL,LUNGE_COOLDOWN=0;
    /**
     * Where T lands her (2.96.2, the user's video read again and confirmed: "상대 바로 앞 옆 (영상처럼)"): {@link #LUNGE_FRONT}
     * blocks short of the foe on the way she came and {@link #LUNGE_SIDE} to her right as she dashed (the video: low at its
     * front-right, seen from behind her), less short of it when she began nearer it than that (never behind where she set
     * out); level, x and z. {@code gx}, {@code gz} the foe, {@code wx}, {@code wz} the way she dashes (level, unit), {@code
     * reach} how far it was from her sword.
     */
    public static final double LUNGE_FRONT=1.3,LUNGE_SIDE=.9;
    public static double[] lungeLanding(double gx,double gz,double wx,double wz,double reach){
        double front=Math.min(LUNGE_FRONT,Math.max(0,reach-.5));
        return new double[]{gx-wx*front-wz*LUNGE_SIDE,gz-wz*front+wx*LUNGE_SIDE};
    }
    /** When T's swords are thrown (seconds: as she sets off), and how long before it strikes each sticks in (seconds). */
    public static final float LUNGE_THROW0=.2f,LUNGE_STICK=.1f;
    /** What each of T's blows is (0 the dash, 1 a sword, 2 a slash) and which one of them (BLOWS[LUNGE]'s order). */
    private static final int[] LUNGE_KIND={0,1,1,1,1,2,1,2,1,2},LUNGE_NTH={0,0,1,2,3,0,4,1,5,2};
    public static int lungeKind(int blow){return LUNGE_KIND[blow];}
    public static int lungeNth(int blow){return LUNGE_NTH[blow];}
    /** T's three slashes (2.96.0): how many, and when slash {@code j} is cut (seconds: its blow's tick). */
    public static final int LUNGE_SLASHES=3;
    public static float lungeSlash(int j){for(int b=0;b<LUNGE_KIND.length;b++)if(LUNGE_KIND[b]==2&&LUNGE_NTH[b]==j)return blowAt(LUNGE,b)/20f;return Float.NaN;}
    /** Whether T may begin {@code t} seconds into her formation (2.96.0): every sword standing, and over (on the far sword) before they flare. */
    public static boolean lungeTime(double t){return t>=Z_FROM&&t+LUNGE_LENGTH/20.0<=H_END;}
    /** The tick T is ready again after one begun at {@code start} ({@code free}: the cooldowns are off for her): once she stands on the far sword, and its cooldown after (none for now, 2.96.4). */
    public static long lungeReady(long start,boolean free){return start+LUNGE_FREE+(free?0:LUNGE_COOLDOWN);}
    /**
     * (2.96.5) How long her camera takes to come back to her once her last T is over (ticks, 0.5 s; user request: "T쓸때
     * 카메라 무빙을 영상 처럼 바꿔줘", user choice "T가 끝나면"): through T it stays where it stood as the first began.
     */
    public static final int LUNGE_CAMERA_BACK=10;
    /**
     * (2.96.5) How much her camera is held where it stood as her first T began (1 held, 0 with her) {@code age} ticks into
     * her latest T, which is over {@code end} ticks into it (LUNGE_LENGTH, or when it was called off): held all through it,
     * then let go over LUNGE_CAMERA_BACK, eased (the video's camera stood still through four T's in a row).
     */
    public static double lungeCameraHeld(double age,double end){
        if(age<=end)return 1;if(age>=end+LUNGE_CAMERA_BACK)return 0;
        double u=(age-end)/LUNGE_CAMERA_BACK;return 1-u*u*(3-2*u);
    }
    /**
     * (2.96.5) The way her camera looks {@code age} ticks into her latest T as it comes back to her (degrees): from the way
     * it looked as it began to let go ({@code from}) round to {@code to} (onto where that T came down, from where she is),
     * along the same ease as it comes back, the shorter way round; {@code from} while it is held.
     */
    public static float lungeCameraTurn(double age,double end,float from,float to){
        double held=lungeCameraHeld(age,end);
        float d=to-from;d-=360f*(float)Math.floor((d+180f)/360f);
        return from+d*(float)(1-held);
    }
    /** How far along her dash she is {@code a} ticks into T (0 to 1): on the sword until LUNGE_GO, at a steady flash of a pace to LUNGE_LAND. */
    public static double lungeDash(double a){return Math.max(0,Math.min(1,(a-LUNGE_GO)/(LUNGE_LAND-LUNGE_GO)));}
    /** How far through her flight on to the far sword she is {@code a} ticks into T (0 to 1, Z's flight), -1 before it. */
    public static double lungeHop(double a){return a<LUNGE_HOP?-1:Math.min(1,(a-LUNGE_HOP)/Z_TRAVEL);}
    /**
     * Where T has her level {@code a} ticks in (2.96.0, as fractions of the way): the sword she stood on ({@code from}),
     * along the dash to where she lands ({@code past}: in front of the foe, lungeLanding), there, over Z's flight to the
     * far sword ({@code to}); x and z.
     */
    public static double[] lungeSpot(double a,double fx,double fz,double px,double pz,double tx,double tz){
        if(a<LUNGE_HOP){double u=lungeDash(a);return new double[]{fx+(px-fx)*u,fz+(pz-fz)*u};}
        double u=zAlong(lungeHop(a));return new double[]{px+(tx-px)*u,pz+(tz-pz)*u};
    }
    /**
     * How high over her formation's ground T has her {@code a} ticks in (blocks): on the pommel she stood on
     * ({@code from} high), down along the dash to the ground, on the ground, up over Z's arc onto the far pommel ({@code to} high).
     */
    public static double lungeHeight(double a,double from,double to){
        if(a<LUNGE_LAND)return from*(1-lungeDash(a));
        if(a<LUNGE_HOP)return 0;
        double u=lungeHop(a);return u<1?to*zAlong(u)+zArc(u):to;
    }
    /**
     * How long after she sets off the light of her dash comes (ticks: the video's 0.2 s; 2.96.3, user request: "이펙트가
     * 여전히 조금 이상해 돌진도 그렇고", the video and the game set side by side and confirmed), and how long before she lands
     * she is seen again, down in front of the foe (ticks).
     */
    public static final int LUNGE_LIGHT=1;
    public static final double LUNGE_SEEN=.2;
    /**
     * Whether her body is out of sight {@code a} ticks into T: in the light of her dash, from as it comes until she is down in
     * front of the foe (2.96.3, the video: a few violet copies of her as she sets off, then hidden in its light), and as she
     * flies on, the orb of Z's flight. (2.96.2 showed her all through the dash; 2.96.0 to 2.96.1 she was a streak of light.)
     */
    public static boolean lungeHidden(double a){return a>=LUNGE_GO+LUNGE_LIGHT&&a<LUNGE_LAND-LUNGE_SEEN||a>=LUNGE_HOP&&a<LUNGE_HOP+Z_TRAVEL;}
    /**
     * The swords T may fly her on to (2.96.1, user request: "스킬을 사용한 검에 반대편 검 아무대나 이동으로 바꿔"; 2.96.0
     * flew her onto the sword farthest from the foe): those on the other side of the formation's middle from the sword she
     * used it from ({@code from}), their way out from its middle more than a right angle from that one's ({@code swords}:
     * forward and right of its middle, formationSwords' layout). The server rolls which of them.
     */
    public static int[] oppositeSwords(double[][] swords,int from){
        double fx=swords[from][0],fz=swords[from][1];int n=0;int[] out=new int[swords.length];
        for(int k=0;k<swords.length;k++)if(k!=from&&swords[k][0]*fx+swords[k][1]*fz<0)out[n++]=k;
        return java.util.Arrays.copyOf(out,n);
    }
    /** The one of oppositeSwords {@code roll} picks (any int, by its remainder: the server rolls it), or with none the next sword. */
    public static int oppositeSword(double[][] swords,int from,int roll){
        int[] far=oppositeSwords(swords,from);
        return far.length==0?(from+1)%swords.length:far[Math.floorMod(roll,far.length)];
    }

    // ───────────────────────────── Y: the cage of swords ─────────────────────────────

    /**
     * Y (2.97.0, user request: "이번에는 Y스킬 거대한 검 6개를 적 위에 소환하고 2개씩 빠르게 적에게 떨궈서 원 모양으로 적을
     * 가두고 잉크가 많이 튀는 스킬이야", the user's two videos of it (9e90441f and c6bb25f6, 60 fps) read on a sheet and
     * confirmed: "맞음, 이대로"; and their answers: 40 in all with a 20 s cooldown ("합 40 · 20초"), whom it cages 80%
     * slower and incapacitated while its swords stand round them ("80% 슬로우 + 무력화"), only on her formation's sword
     * ("검진 위에서만 (영상처럼)"), the foe picked as H's basic attack and T pick it, the crosshair's point with none): she
     * leaps up off her sword and reaches toward the foe, six giant swords of light show one after another in the air over
     * it, points down, and as she comes down on her sword again they come down on it two at a time ({@link #CAGE_DROPS}),
     * sticking in round it in a ring ({@link #CAGE_RADIUS}); ink bursts up out of the ground round it in columns and flies
     * off in clumps, and the swords are gone at {@link #CAGE_LENGTH}. Thirteen blows (BLOWS: 0.55 s to 0.9 s, the videos'
     * hit counter), twelve of 3 and the last 4, on everyone the ring stands round; no guard takes them (they come down
     * from above), no one is thrown or staggered: from the first that strikes them they are caged until the swords go
     * ({@link #CAGE_FREE}), incapacitated and {@link #CAGE_SLOW} slower. Until the swords come down their ring follows the
     * foe ({@link #CAGE_PLANT}: set where it stands then). It holds her {@link #CAGE_HOLD} ticks (the videos: she stands
     * on her sword, her hand out at the foe, until her camera comes back to her) and is ready again
     * {@link #CAGE_COOLDOWN} after its swords are gone.
     */
    public static final int CAGE=12,CAGE_SWORDS=6;
    /**
     * Y's ticks: when it lets go of her (1.25 s: the videos' camera back behind her by 1.3 s), when its ring is set where
     * the foe stands (a tick before the first two swords strike), when its swords are gone and the cage with them (1.55
     * s), when it is over (1.6 s: the last of its light and ink gone), and the ticks it is not ready again after that
     * (the user's choice: 20 s).
     */
    public static final int CAGE_HOLD=25,CAGE_PLANT=10,CAGE_FREE=31,CAGE_LENGTH=32,CAGE_COOLDOWN=400;
    /** The ticks each two swords strike (the videos: 0.55, 0.65 and 0.76 s): the first and the fourth, the second and the fifth, the third and the sixth. */
    public static final int[] CAGE_DROPS={11,13,15};
    /** Which two swords come down at drop {@code j} (0 to 2): sword {@code j} and the one across the ring from it. */
    public static int[] cagePair(int j){return new int[]{j,j+CAGE_SWORDS/2};}
    /** The drop sword {@code k} comes down at (0 to 2). */
    public static int cageDrop(int k){return k%(CAGE_SWORDS/2);}
    /** The tick sword {@code k} strikes the ground. */
    public static int cageLands(int k){return CAGE_DROPS[cageDrop(k)];}
    /** How much slower whom it cages walk (the user's word: 80%). */
    public static final double CAGE_SLOW=.8;
    /**
     * Its ring (blocks): how far out from its middle each sword stands in the ground (the videos: round the foe close by,
     * a little wider than they are), and how far below and above its ground a body may stand and still be in it.
     */
    public static final double CAGE_RADIUS=1.25,CAGE_LOW=-1.5,CAGE_HIGH=3.0;
    /** Whether a body ({@code dx}, {@code dz} level from the ring's middle, {@code dy} from its ground up to their feet, {@code halfWidth} across) is in Y's ring. */
    public static boolean caged(double dx,double dz,double dy,double halfWidth){
        return dy>=CAGE_LOW&&dy<=CAGE_HIGH&&Math.hypot(dx,dz)<=CAGE_RADIUS+halfWidth;
    }
    /** Whether Y may begin {@code t} seconds into her formation: every sword standing, and over before they flare (as H's basic attack). */
    public static boolean cageTime(double t){return t>=Z_FROM&&t+CAGE_LENGTH/20.0<=H_END;}
    /** The tick Y is ready again after one begun at {@code start} ({@code free}: the cooldowns are off for her): once its swords are gone, and its cooldown after. */
    public static long cageReady(long start,boolean free){return start+CAGE_LENGTH+(free?0:CAGE_COOLDOWN);}
    /**
     * Her leap (the videos: up off the pommel 0.1 s in, reaching toward the foe in the air while the swords show, down on
     * it again by 0.52 s): how high over the pommel it has her {@code a} ticks into Y (blocks), up over
     * [{@link #CAGE_LEAP_FROM}, {@link #CAGE_LEAP_TOP}] slowing to the top, drifting {@link #CAGE_DRIFT} higher until
     * {@link #CAGE_LEAP_DOWN}, then down onto the pommel by {@link #CAGE_LEAP_LAND}, quickest halfway and landing softly
     * (the videos: a dive of a tenth of a second or so).
     */
    public static final double CAGE_LEAP=2.4,CAGE_DRIFT=.25,CAGE_LEAP_FROM=1.2,CAGE_LEAP_TOP=4.6,CAGE_LEAP_DOWN=7.8,CAGE_LEAP_LAND=10.4;
    public static double cageLeap(double a){
        if(a<=CAGE_LEAP_FROM||a>=CAGE_LEAP_LAND)return 0;
        if(a<CAGE_LEAP_TOP){double u=(a-CAGE_LEAP_FROM)/(CAGE_LEAP_TOP-CAGE_LEAP_FROM);return CAGE_LEAP*(1-(1-u)*(1-u));}
        if(a<CAGE_LEAP_DOWN){double u=(a-CAGE_LEAP_TOP)/(CAGE_LEAP_DOWN-CAGE_LEAP_TOP);return CAGE_LEAP+CAGE_DRIFT*Math.sin(u*Math.PI*.5);}
        double u=(a-CAGE_LEAP_DOWN)/(CAGE_LEAP_LAND-CAGE_LEAP_DOWN);return (CAGE_LEAP+CAGE_DRIFT)*(1-u*u*(3-2*u));
    }
    /**
     * (2.97.0) Her camera through Y (the videos): it swings round to her side so she and the foe are both seen
     * ({@link #CAGE_SIDE_IN}), stands there while the swords show, goes round low behind her as she comes down
     * ({@link #CAGE_BEHIND0} to {@link #CAGE_BEHIND1}), and comes back to her as it lets go of her ({@link #CAGE_BACK0} to
     * {@link #CAGE_BACK1}), each eased (seconds into Y).
     */
    public static final float CAGE_SIDE_IN=.12f,CAGE_BEHIND0=.36f,CAGE_BEHIND1=.54f,CAGE_BACK0=1.22f,CAGE_BACK1=1.6f;

    // ───────────────────────────── H again: the formation's finale ─────────────────────────────

    /**
     * H again on her formation (2.101.0, user request: "이번에는 H를 사용한 상태에서 H를 한 번 더 누르면 되는 스킬 음성은
     * 저렇게 3개고 랜덤으로 하나 나오면 돼 스킬은 카메라가 플레이어로 빛추고 H 영역에 있던 모든 검이 전부 플레이어 뒤로 가고
     * 앞으로 꽂는 스킬이야 이걸 쓰면 H가 해제돼", the user's three videos of it read on a sheet and confirmed: "맞음, 이대로";
     * their answers: 60 in all ("합 60"), the band ahead where the swords come down ("앞쪽 검이 꽂히는 띠"), the cutscene
     * hers alone ("시전자만")): on her formation's sword, every sword of it standing, H again ends the formation in one
     * stroke. A blue flash, and her own camera goes into a cutscene: she leaps high off the pommel and every sword of the
     * formation is pulled out of the ground and flies up round her trailing white light; she comes down onto the ground
     * where she stood, the swords gathering behind her back and spreading there like wings; she raises two fingers before
     * her face, her eyes alight; they fly from behind her all at once and come down slanting into the ground in a wide
     * band ahead of her ({@link #finaleIn}: the way she looks as she presses it), blue crystal flaring up where each
     * strikes, and the band flashes blue. Everyone in the band is struck as they come down (BLOWS, {@link #FINALE_DAMAGE}
     * each, 60 in all): no guard takes it, no one is thrown or staggered. It holds her until her camera is back with her
     * ({@link #FINALE_HOLD}: nothing harms her, nothing else of hers may begin, as G); the swords stand a moment more and
     * are gone ({@link #FINALE_LENGTH}). One of the three videos' recordings plays, at random ({@link #FINALE_VARIANTS},
     * the server's roll). The formation is over as it begins; H's cooldown runs once it is over.
     */
    public static final int FINALE=13;
    /** Each of its blows (the user's word: 60 in all). */
    public static final float FINALE_DAMAGE=12;
    /**
     * Ticks it holds her (3.7 s: the videos' camera back to her 3.6 s after their flash, ours a tenth of a second in) and
     * ticks until its swords are gone (4.8 s: the videos' swords gone one by one some 4.5 s after their flash).
     */
    public static final int FINALE_HOLD=74,FINALE_LENGTH=96;
    /** How many recordings it has, one chosen at random each time (the user's three videos: "음성은 저렇게 3개고 랜덤으로 하나"). */
    public static final int FINALE_VARIANTS=3;
    /**
     * Its times (seconds in; the videos' times from their flash, the flash here a tenth of a second in, FINALE_FLASH):
     * the flash and her camera into its cutscene; she leaps off the pommel (FINALE_LEAP), comes down onto her own sword
     * flying up under her feet (FINALE_RIDE: 2.101.1) and rides it round a lap of the sky, at its crest far behind
     * where she stood (FINALE_CREST), coming back at the camera to stop in the air over where she stood (FINALE_LAP1);
     * up there on it: her sword swung across (FINALE_SWING0 to FINALE_SWING1), the white ring round her (FINALE_RING),
     * her two fingers raised before her face (FINALE_SEAL0 to FINALE_SEAL1), her camera cut behind her with the swords
     * waiting by it and fired at random from then (FINALE_LAUNCH: 2.101.9, user request: "그냥 검들 바로 쏘게 바꿔 ...
     * 카메라 뒤로 이동하자마자 그리고 순서대로 나가는거 같은데 무작위로 빠르게 쏘게", then their video: one about every 0.03 s
     * from the cut for some 0.7 s; 2.101.8 fired them nearest first from 2.43 s) and coming down into the band
     * (FINALE_PLANT0 to FINALE_PLANT1, hers last); she steps off it and comes down
     * onto the ground where she stood (FINALE_DESCEND to FINALE_LAND), it flung down into the band after the rest; the
     * band's flash (FINALE_BURST); her camera back to her (FINALE_BACK0 to FINALE_BACK1); the swords gone one by one
     * (FINALE_GONE0 to FINALE_GONE1).
     */
    public static final float FINALE_FLASH=.1f,FINALE_LEAP=.2f,FINALE_RIDE=.45f,FINALE_CREST=.98f,FINALE_LAP1=1.3f,
        FINALE_SWING0=1.38f,FINALE_SWING1=1.56f,FINALE_RING=1.6f,FINALE_SEAL0=1.62f,FINALE_SEAL1=2.25f,FINALE_LAUNCH=2.3f,
        FINALE_PLANT0=2.38f,FINALE_PLANT1=3.2f,FINALE_DESCEND=3.0f,FINALE_LAND=3.4f,FINALE_BURST=3.5f,
        FINALE_BACK0=3.62f,FINALE_BACK1=3.9f,FINALE_GONE0=4.15f,FINALE_GONE1=4.6f;
    /**
     * The band its swords come down in (blocks from where she stands, the way she looks as she presses it: the user's
     * choice "앞쪽 검이 꽂히는 띠"): from just ahead of her out to FINALE_REACH, FINALE_HALF either side, and how far under
     * and over its ground a body may be and still be in it.
     */
    public static final double FINALE_NEAR=.5,FINALE_REACH=16,FINALE_HALF=9,FINALE_LOW=-1.5,FINALE_HIGH=4;
    /** Whether a body ({@code forward} and {@code side} of where she stands, {@code height} from its ground to its feet, {@code halfWidth} across) is in the band. */
    public static boolean finaleIn(double forward,double side,double height,double halfWidth){
        return height>=FINALE_LOW&&height<=FINALE_HIGH&&forward>=FINALE_NEAR-halfWidth&&forward<=FINALE_REACH+halfWidth&&Math.abs(side)<=FINALE_HALF+halfWidth;
    }
    /**
     * Whether it may begin {@code t} seconds into her formation: every sword standing (as Z, H's basic attack, T and Y), and
     * before they flare (H_END: it ends the formation then itself).
     */
    public static boolean finaleTime(double t){return t>=Z_FROM&&t<H_END;}
    /**
     * How high over the formation's ground her feet are on her lap (2.101.1, the user's word on our reading of their video:
     * "그녀 둘레 6블록", three to five blocks up): as she comes down onto her sword after her leap (FINALE_LAP_LOW), at its crest
     * far behind where she stood (FINALE_LAP_HIGH), and where she stops in the air over where she stood (FINALE_HOVER), her
     * sword under her; how far round her the lap goes (FINALE_LAP_R), and how fast she springs up off the pommel (blocks
     * a second).
     */
    public static final double FINALE_LAP_LOW=3.6,FINALE_LAP_HIGH=5.0,FINALE_HOVER=3.2,FINALE_LAP_R=6,FINALE_SPRING=12;
    /** How much she bobs up there on her sword, and how long each bob takes (two between her stop and her step off). */
    public static final double FINALE_BOB=.08,FINALE_BOB_PERIOD=(FINALE_DESCEND-FINALE_LAP1)/2.0;
    /**
     * How high over the formation's ground her body is drawn {@code t} seconds in (2.101.1, user request: "영상은 칼을
     * 타고 한바퀴를 돈 다음에 공중에서 검을 전면에 모두 꽂는거야 그리고 내려옴"), from the pommel she stood on ({@code pommel}
     * blocks up): there until she leaps; up off it onto her sword (FINALE_RIDE, still rising); up round her lap to its
     * crest and down, slowing, to where she stops (FINALE_HOVER); bobbing there on her sword while she swings, raises her
     * fingers and sends the swords; then off it and down onto the ground, quicker and quicker and soft at the last (the
     * videos: she lands in front of the swords).
     */
    public static double finaleDrawn(double t,double pommel){
        if(t<FINALE_LEAP)return pommel;
        if(t<FINALE_RIDE)return hermite(t,FINALE_LEAP,FINALE_RIDE,pommel,FINALE_LAP_LOW,FINALE_SPRING,RIDE_RISE);
        if(t<FINALE_CREST)return hermite(t,FINALE_RIDE,FINALE_CREST,FINALE_LAP_LOW,FINALE_LAP_HIGH,RIDE_RISE,0);
        if(t<FINALE_LAP1)return hermite(t,FINALE_CREST,FINALE_LAP1,FINALE_LAP_HIGH,FINALE_HOVER,0,0);
        if(t<FINALE_DESCEND)return FINALE_HOVER+FINALE_BOB*.5*(1-Math.cos(2*Math.PI*(t-FINALE_LAP1)/FINALE_BOB_PERIOD));
        if(t<FINALE_LAND){
            // quick and quicker down, the last fifth easing onto the ground (its speed the same either side of the turn)
            double u=(t-FINALE_DESCEND)/(FINALE_LAND-FINALE_DESCEND),v=(u-.8)/.2;
            double fall=u<.8?.8*(u/.8)*(u/.8):.8+.2*(1-(1-v)*(1-v));
            return FINALE_HOVER*(1-fall);
        }
        return 0;
    }
    /** How fast she still rises as she comes onto her sword (blocks a second): on up her lap, not stopping on it. */
    static final double RIDE_RISE=2;
    /** The cubic from {@code a} at {@code t0} to {@code b} at {@code t1}, going {@code va} and {@code vb} a second at its ends. */
    static double hermite(double t,double t0,double t1,double a,double b,double va,double vb){
        double h=t1-t0,u=(t-t0)/h,u2=u*u,u3=u2*u;
        return (2*u3-3*u2+1)*a+(u3-2*u2+u)*h*va+(-2*u3+3*u2)*b+(u3-u2)*h*vb;
    }
    /**
     * Where her lap has her {@code t} seconds in, level, from where she stands (2.101.1: blocks forward along the band's
     * way and to its right; the videos read and confirmed: "검을 타고(서핑처럼) 하늘을 크게 한 바퀴 돎", "한 바퀴를 돌아
     * 카메라 쪽으로 돌아옴", "카메라 앞 공중에 검을 탄 채 멈춤", and the user's "그녀 둘레 6블록"): one loop round the sky to
     * her right, turning right all the way: out ahead from over the pommel, round out to her right FINALE_LAP_R blocks off,
     * on round behind where she stood and straight back in from behind toward the band's way, slowing, to stop over where
     * she stood; turning once round as she goes, never sharply. Nothing before she leaps or once she is back.
     */
    public static double[] finaleLap(double t){
        if(t<=FINALE_LEAP||t>=FINALE_LAP1)return new double[]{0,0};
        double u=(t-FINALE_LEAP)/(FINALE_LAP1-FINALE_LEAP);
        // (out and back in as the square of a sine: off and to a stop gently, no jolt at either end)
        double sn=Math.sin(Math.PI*u),out=FINALE_LAP_R*sn*sn,th=Math.PI*smooth(u);
        return new double[]{out*Math.cos(th),out*Math.sin(th)};
    }
    /** The smooth step of {@code x} (0 at or under 0, 1 at or over 1). */
    static double smooth(double x){if(x<=0)return 0;if(x>=1)return 1;return x*x*(3-2*x);}
    /**
     * The way she goes on her lap {@code t} seconds in (degrees round to the right of the band's way, 2.101.1): ahead as
     * she goes out, round to the right through behind and on round to ahead again as she comes back in, 0 to 360 without
     * a jump; ahead (0) before and after it, and while she hardly moves.
     */
    public static double finaleHeading(double t){
        if(t<=FINALE_LEAP||t>=FINALE_LAP1)return 0;
        double e=1e-3;double[] a=finaleLap(t-e),b=finaleLap(t+e);
        double vf=(b[0]-a[0])/(2*e),vr=(b[1]-a[1])/(2*e),speed=Math.hypot(vf,vr);
        double deg=Math.toDegrees(Math.atan2(vr,vf)),u=(t-FINALE_LEAP)/(FINALE_LAP1-FINALE_LEAP);
        // (it turns only to the right, once round: from ahead through her right and behind to ahead again, so a way that
        // reads less than ahead is more than half round, and in its second half, near ahead is all the way round)
        if(u>.5?deg<90:deg<-90)deg+=360;
        // (where she hardly moves, at its ends, its way is no way at all: ahead)
        double rest=u>.5?360:0;
        return rest+(deg-rest)*smooth(speed/HEADING_SURE);
    }
    /** How fast she must go for her way to be her lap's (blocks a second; slower, toward ahead: only at its ends). */
    static final double HEADING_SURE=1.5;
    /**
     * How high over the formation's ground she really is {@code t} seconds in (blocks): on the pommel until her drawn body
     * comes down past it, then with it onto the ground (her own client moves her so; the leap, her lap and the hover over
     * it are drawn, where she really is the server keeps).
     */
    public static double finaleReal(double t,double pommel){return Math.max(0,Math.min(pommel,finaleDrawn(t,pommel)));}
    /** Whether it holds her up off the ground {@code t} seconds in (the server lets her float until she is down). */
    public static boolean finaleAloft(double t){return t>=0&&t<FINALE_LAND+.2f;}
    /** The tick H is ready again after a finale begun at {@code start} ({@code free}: the cooldowns are off for her): once its swords are gone, and H's cooldown after. */
    public static long finaleReady(long start,boolean free){return free?0:start+FINALE_LENGTH+H_COOLDOWN;}

    // ───────────────────────────── G: the ultimate ─────────────────────────────

    /**
     * G (2.83.0, user request: "이번에는 G스킬 궁극기 시전자만 컷씬이 나오고 범위는 30블록 효과음과 대사는 내가 영상에 보냈고
     * 저거 2개중에 랜덤으로 하나 나오게 하면 돼 컷씬은 저거랑 똑같이 나오게", the user's two videos of it (e3953f00 and
     * b266d9b5, 60 fps) read on a sheet, and their answers: "피해 100 쿨 5분"; struck as the sword strikes the ground, free
     * until then; then "그리고 저거 거대한 검 떨어지는거임" and "컷씬은 그냥 다른 공간 보여줘서 검 안보이게 하고 박히는 순간
     * 원래 월드에는 박히는 효과만 남게"): Lee So-sang raises two fingers, light gathering at their tip, and draws a stroke of
     * gold in the air; holds them before her face, a cross of light flaring there; leans back and cries out, and a giant
     * golden sword comes down point first out of a storm and strikes the ground {@link #G_AHEAD} blocks ahead of her,
     * everyone within {@link #G_RANGE} blocks of her struck as it does ({@link #G_DAMAGE}: no guard takes it, no one is
     * thrown); the ground bursts into fire, flying stone and dust, and fire leaps up out of the ground round her. Only she
     * sees the sword: her own camera's cutscene, in a space of its own (the clients' work); the world itself shows only
     * where it struck. She is held where she stands and nothing harms her until it is over ({@link #G_HOLD}), and G is
     * ready again {@link #G_COOLDOWN} after that. One of the two videos' recordings is played, at random (the server's
     * choice, {@link #G_VARIANTS}), with that video's face.
     */
    public static final int ULTIMATE=9;
    /** Its damage (the user's word), and how far from her it reaches (blocks, from her middle to the nearest of them; the user's word). */
    public static final float G_DAMAGE=100;
    public static final double G_RANGE=30;
    /** The tick into G the sword strikes the ground (3.1 s: the videos' sword strikes 3.08 s after the screen darkens). */
    public static final int G_IMPACT=62;
    /**
     * Ticks G holds her (4.85 s: the videos come back to the game 4.83 s in, from the cutscene's dark) and ticks it is not
     * ready again once it is over (the user's word: 5 minutes), from its end, as every skill's cooldown.
     */
    public static final int G_HOLD=97,G_COOLDOWN=6000;
    /** How many recordings (and faces) it has, one chosen at random each time (the user's two videos). */
    public static final int G_VARIANTS=2;
    /**
     * G's times (seconds; the videos, from the frame the screen goes dark): the light at the fingertips (0.22), the face
     * (1.0), the sky (1.55), the sword coming down (2.68), striking (3.1), the game again (4.83 to 4.95), the fire leaping
     * up round her (5.05) and the dust cleared (6.4).
     */
    public static final float G_GLOW=.217f,G_FACE=1.008f,G_SKY=1.51f,G_FALL=2.7833333f,G_STRIKE=G_IMPACT/20f,G_BACK=4.83f,G_GAME=4.95f,G_ERUPT=5.15f,G_CLEAR=7f;
    /**
     * Where the sword is: it strikes the ground {@link #G_AHEAD} blocks ahead of where she stands (the way she faced), its
     * point {@link #G_SINK} blocks into it. Its blade (blocks): {@link #BLADE_WIDTH} across (the videos', measured against
     * her) and {@link #BLADE_LENGTH} from its point up, longer than any shot of it shows (the videos show only its lower
     * part: its point, its rim and its carving; never its guard or its grip).
     */
    public static final double G_AHEAD=9,G_SINK=2.6,BLADE_LENGTH=96,BLADE_WIDTH=7.6;
    /**
     * Whether a body is within its reach: {@code dx}, {@code dz} level from her feet, {@code dy} from her feet up to their
     * feet, {@code halfWidth} across and {@code height} tall; the nearest of them within G_RANGE of her middle (a block
     * over her feet).
     */
    public static boolean ultimateReaches(double dx,double dy,double dz,double halfWidth,double height){
        double h=Math.max(0,Math.hypot(dx,dz)-halfWidth),low=dy-1,high=dy+height-1;
        double v=low>0?low:high<0?-high:0;
        return h*h+v*v<=G_RANGE*G_RANGE;
    }
    /** Whether G strikes on its tick {@code age} (once, as the sword strikes the ground). */
    public static boolean ultimateStrikes(long age){return age==G_IMPACT;}
}
