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
    private static final int[][] BLOWS={{},{6},{3,6},{2,7,11,17},{10},{2,4,7},{3},{IsosangRules.SWEEP_BLOW},{}};
    /**
     * Each blow's damage: 6, 6 (3 + 3), 7 (1.5 + 1.5 + 1.5 + 2.5) and 10, 29 for the whole combo (Kageno's 28); the
     * empowered first's three swords and fourth's thrust half as hard again as the first's cut and the fourth's slash (9
     * and 15; the empowered combo 43.5).
     */
    private static final float[][] DAMAGE={{},{6},{3,3},{1.5f,1.5f,1.5f,2.5f},{10},{3,3,3},{15},{IsosangRules.SWEEP_DAMAGE},{}};
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
    public static final int[] STEP={0,11,10,25,0,16,0,0,0};
    /** Ticks a hit holds its user from its start: no walking, guard, dash or other move until then (2.81.0: and R's; 2.82.0: H's, all of it). */
    public static final int[] HOLD={0,10,12,22,18,14,16,21,826};
    /** How long each hit runs (ticks) until its user is back in their stance: the clients draw its motion this long (2.81.0: and R's; 2.82.0: and H's). */
    public static final int[] LENGTH={0,18,18,25,32,22,30,24,826};
    /** A press this many ticks or fewer before the next hit may start is kept, and starts it then. */
    public static final int BUFFER=8;
    /** The next hit must start within this many ticks of when it may, or the combo breaks. */
    public static final int GRACE=24;
    /** The rest once the fourth hit is over, or once a late hit broke the combo (ticks), as the other characters' attacks. */
    public static final int REST=24;
    /** Whether {@code kind} is a hit (1 to 4, the empowered 5 and 6). */
    public static boolean valid(int kind){return kind>=1&&kind<=THRUST;}
    /** Whether {@code kind} is a swing the clients draw with the body: a hit, R's sweep (2.81.0), or H's sword formation (2.82.0). */
    public static boolean drawn(int kind){return valid(kind)||kind==SWEEP||kind==FORMATION;}
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
    /** Whether hit {@code combo} cannot be guarded: the finisher (either kind), as the other characters' fourth hits; and R's sweep (2.81.0, the user's choice). */
    public static boolean unblockable(int combo){return position(combo)==4||combo==SWEEP;}

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
     * 2.82.2, user request: "그리고 범위 조금만 줄여", then 10 again in 2.82.3: "아니다 범위는 원래대로 늘리고") and how far
     * under and over its ground a body is still in it.
     */
    public static final double FORMATION_RADIUS=14.5,FORMATION_LOW=-1.2,FORMATION_HIGH=5;
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
    /** (FORMATION_EMPTY 6.3, was 5.4: "중앙으로 넘어간 검 있잖아": a sword or two stood in the bare middle, which is some 5.7 blocks across.) */
    public static final double FORMATION_EMPTY=6.3,FORMATION_EDGE=.6,FORMATION_JITTER=.35;
    /** The golden angle: each sword this far round from the one before, so they spread evenly with no rows. */
    static final double GOLDEN=Math.PI*(3-Math.sqrt(5));
    /**
     * How far behind the formation's middle she stands (blocks, 2.82.4: out toward its edge, on the sword struck under
     * her; the formation's middle is this far ahead of her, the way she faced, the crosshair's; since 2.82.16 swords
     * stand round her and behind her too).
     */
    public static final double FORMATION_STAND_BACK=8.7;
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
}
