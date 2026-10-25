package dev.sangwoo.celestialfall.stand;

/**
 * Star Platinum's basic attack, four punches (2.54.0, user request: "이제 평타 만들자 4타에 날아가야해 평타 소리는 내가
 * 보낸 파일이랑 싱크 맞게 해줘"): a left jab, a right cross, a left hook and a lunging right that sends whoever it strikes
 * flying. 2.56.0 (user request: "평타 속도를 더 빠르게 하자 그대신 1 2타는 오라 소리를 없애고 3 4타부터 생기게"): the
 * jab and the cross are quick and silent, three tenths of a second apart; the hook and the finisher shout the last two
 * "ORA!"s of the user's recording, each landing on the "RA" of its own (tools/stand/cut_ora.py: a clip starts one tick
 * after its punch and has its RA (CONTACT - 1) / 20 s in). Those two are long shouts, "Ooo-RAAA", their "O" 0.4 s, so
 * the hook and the finisher still take 0.45 and 0.5 s to land; the finisher comes 0.65 s after the hook (in the
 * recording the two are 1.2 s apart), and its shout takes over from the hook's (StandAttack fades the one before out).
 * The four land 0.20, 0.50, 1.05 and 1.75 s into a mashed combo (2.54.0: 0.30, 1.10, 2.00, 3.20). Since 2.57.0 its E
 * too, the rush (below), since 2.59.0 its R, the heavy punch: a fifth punch, HEAVY, out of the combo (below), and since
 * 2.60.0 its T, the Star Finger: a sixth, FINGER (below), since 2.61.0 its Y, the inhale (below, held down since
 * 2.62.0), and since 2.64.0 its G, the beatdown: a seventh, BEATDOWN, whose blow begins a cutscene (below). Pure timing
 * and reach, shared by the server and the client; ticks of 1/20 s.
 */
public final class StarPlatinumRules {
    /**
     * Each punch's contact, ticks after it starts (by the punch, 1 to 4, the heavy punch, 5, the Star Finger, 6, and the
     * beatdown's blow, 7): the jab and the cross snap in, the hook and the finisher take the "O" of their shouts (0.4 s),
     * the heavy punch its long coil (0.6 s), the Star Finger its aim and its fingers' flight out (0.45 s, the crack in
     * the user's recording), the beatdown's its big wind-up (0.8 s since 2.65.0, slower than even the heavy punch's,
     * its user glowing for the first half second of it, so that whoever it comes at can see it coming and guard or get
     * away: "G 꽂을때 파훼 할 수 있게 모션이 더 크게 보여야하고 빛나는 걸 0.5로 늘리자", "G시전 모션이 R강펀치보다도 더
     * 느리게 만들어줘"; 0.2 s in 2.64.0), its "ORA!" shouted to land its "RA" on it (BEAT_OPENER_SOUND).
     */
    public static final int[] CONTACT={0,4,4,9,10,12,9,16};
    /** Ticks from a punch's start before the next may start (2.56.0: three tenths of a second, the hook's 0.65 s; after the heavy punch, the Star Finger and the beatdown's blow, once they are over). */
    public static final int[] STEP={0,6,6,13,0,28,25,30};
    /** How long each punch runs (ticks) when no next one cuts it short: the strike, the follow-through and the guard again. */
    public static final int[] LENGTH={0,10,10,17,23,28,25,30};
    /** (The beatdown's blow deals nothing itself: the cutscene it begins deals the beatdown's damage, BEAT_DAMAGE.) */
    public static final float[] DAMAGE={0,5,5,6,10,15,10,0};
    /** A press this many ticks or fewer before the next punch may start is kept, and starts it then. */
    public static final int BUFFER=8;
    /** The next punch must start within this many ticks of when it may, or the combo breaks. */
    public static final int GRACE=20;
    /** The rest once the fourth punch is over, or once a late punch broke the combo (ticks), as Kageno's attack. */
    public static final int REST=24;
    /** The fourth punch sends whoever it strikes flying along it (blocks a tick, out and up). */
    public static final double FLY=1.75,FLY_UP=.5;
    /** Where a punch reaches from its user's feet (blocks): ahead (the fourth and the heavy punch lunge further), to either side, up and down. */
    public static final double NEAR=.2,REACH=3.6,REACH_BIG=4.2,HALF_WIDTH=1.15,LOW=-1.2,HIGH=2.8;
    /** Ticks a punch's contact holds whoever it strikes for, past the next punch's contact if that comes as soon as it may. */
    public static final int HOLD_PAST=3;
    /** Ticks a punch stopped short takes to turn back to the guard (2.57.0: three; 0.2 s before). */
    public static final int STOP_RETURN=3;

    // ---- E, the rush (2.57.0, user request: "이번에는 e스킬 만들자 e홀드로 사용 할 수 있고 도중에 끊으면 바로 끊겨 러시고
    // 연타를 빠르게 꽂는거야 ... 총 40 데미지 박히면 되고 연타마다 나눠서 꽂히면 돼"): while E is held the stand drives
    // its fists into whoever stands in front, a blow every RUSH_EVERY ticks from RUSH_FIRST, until E is let go (at once)
    // or the rush has run its RUSH_HITS blows; held to the end it deals RUSH_TOTAL. Its user turns and walks through it
    // at a tenth of their speed (user request: "방향전환 걷기는 다 되지만 이동속도가 90% 느려져"); its cooldown runs once
    // it is over (2.44.0's rule).

    /** The rush's first blow (ticks in: the stand has come round to the front), one every RUSH_EVERY ticks, RUSH_HITS in all. */
    public static final int RUSH_FIRST=3,RUSH_EVERY=2,RUSH_HITS=40;
    /** All of its blows together, split evenly among them. */
    public static final float RUSH_TOTAL=40,RUSH_DAMAGE=RUSH_TOTAL/RUSH_HITS;
    /** The tick of its last blow, and how long it runs held to the end: its last blow, and the voice's long last "ORA" with it (4.2 s). */
    public static final int RUSH_LAST=RUSH_FIRST+RUSH_EVERY*(RUSH_HITS-1),RUSH_LENGTH=RUSH_LAST+3;
    /** Its cooldown once it is over, let go or run out (ticks: ten seconds). */
    public static final int RUSH_COOLDOWN=200;
    /** A blow holds whom it strikes this long (ticks), past the next one: they stay held while the rush lasts. */
    public static final int RUSH_HOLD=RUSH_EVERY+3;
    /** How much slower its user walks while the stand rushes (0.9: at a tenth of their speed). */
    public static final double RUSH_SLOW=.9;
    /** Where its blows reach from its user's feet (blocks): ahead, to either side (the fists fly all over the front), up and down. */
    public static final double RUSH_REACH=3.6,RUSH_HALF_WIDTH=1.3;
    /**
     * A punch stopped short as the rush is asked for turns back to the guard first (STOP_RETURN): the rush starts once
     * it is there, from the guard. Once the rush is over its fists go back to the guard over RUSH_SETTLE ticks, and a
     * punch asked for then starts once they are there.
     */
    public static final int RUSH_SETTLE=3;
    // ---- R, the heavy punch (2.59.0, user request: "이번에는 R 강펀치 맞으면 날아가고 가드 브레이커야 mp4로 소리 보내줄게"):
    // with the stand out, R throws one right straight with everything behind it, a fifth punch out of the combo. It
    // coils for 0.6 s (CONTACT[HEAVY]) and lands on everyone in its way the way its user faced as it started, as the
    // finisher reaches (REACH_BIG). A guard cannot take it: it breaks the guard (for HEAVY_BREAK ticks, as blows that
    // wear a guard down break it) and lands all the same, and it sends whoever it strikes flying along it, further than
    // the finisher. Since 2.60.0 (user request: "R로 가드 부수면 날아가는 대신 2초 무력화 됨 그냥 가드 브레이커 개념이
    // 2초 무력화로 알고 있으면 됨") whoever's guard it breaks does not fly: they stay where they stand, incapacitated
    // while their guard is broken (two seconds: no skill, attack, dash or guard, CombatManager). Its cooldown runs once
    // it is over (2.44.0's rule), or from where it was stopped short.

    /** The heavy punch's number among the punches (its contact, length and damage are at this index). */
    public static final int HEAVY=5;
    /** Its cooldown once it is over (ticks: eight seconds). */
    public static final int HEAVY_COOLDOWN=160;
    /**
     * How long a guard it breaks stays broken (ticks; one worn down by blows, CombatState.BREAK_TICKS, a second): its
     * holder incapacitated all that while, where they stand (2.60.0: "가드 브레이커 개념이 2초 무력화").
     */
    public static final int HEAVY_BREAK=40;
    /**
     * It sends whoever it strikes with no guard up flying along it (blocks a tick, out and up): further and higher than the finisher (FLY,
     * FLY_UP), some 17 blocks against its 10 on flat ground. (The first tick of a throw is on the ground, so the ground's
     * friction takes nearly half of the speed out at once and the air the rest: 2.0 out went 11.5 blocks in the capture.)
     */
    public static final double FLY_HEAVY=2.8,FLY_HEAVY_UP=.75;
    /** When the heavy punch is ready again once it ended (over, or stopped short) at {@code end}. */
    public static long heavyReady(long end){return end+HEAVY_COOLDOWN;}
    // ---- T, the Star Finger (2.60.0, user request: "이번에는 스타 핑거 손가락 2개를 엄청 늘려서 5블록까지 적을 찌를 수
    // 있음 맞추면 2초 95% 슬로우 + 3초 동안 대쉬 평타 포함 아무 스킬도 사용 못함(무력화)"): with the stand out, T makes it
    // take aim and drive its first two fingers out straight, stretching them out to whoever stands first in their way
    // up to FINGER_REACH blocks ahead of its user, a sixth punch out of the combo. It strikes that one alone (the fingers
    // end in the first body they meet), for DAMAGE[FINGER]; a guard held up against it takes it, as any blow but the
    // heavy punch's. Whoever it strikes walks FINGER_SLOW slower for FINGER_SLOW_TICKS and is incapacitated for
    // FINGER_DISABLE ticks: no skill, attack, dash or guard (CombatManager.incapacitate). Its cooldown runs once it is
    // over (2.44.0's rule), or from where it was stopped short.

    /** The Star Finger's number among the punches (its contact, length and damage are at this index). */
    public static final int FINGER=6;
    /** How far its fingers reach from its user's feet (blocks ahead) and how wide their way is (to either side; two fingers, not a fist). */
    public static final double FINGER_REACH=5,FINGER_HALF_WIDTH=.6;
    /** Its cooldown once it is over (ticks: twelve seconds). */
    public static final int FINGER_COOLDOWN=240;
    /** Whoever it strikes walks this much slower (0.95: at a twentieth of their speed) for FINGER_SLOW_TICKS, and is incapacitated for FINGER_DISABLE. */
    public static final double FINGER_SLOW=.95;
    public static final int FINGER_SLOW_TICKS=40,FINGER_DISABLE=60;
    /** When the Star Finger is ready again once it ended (over, or stopped short) at {@code end}. */
    public static long fingerReady(long end){return end+FINGER_COOLDOWN;}
    /** Whether the punch {@code combo} is one of the stand's own skills out of the combo (the heavy punch, the Star Finger, the beatdown's blow). */
    public static boolean skill(int combo){return combo==HEAVY||combo==FINGER||combo==BEATDOWN;}
    // ---- G, the beatdown (2.64.0, user request: "이제 G 비트다운을 만들어야해 이건 컷씬이 나와야하고 소리대로 설명을 해줄게
    // 처음 G스킬을 사용할때 오라! 를 외치고 주먹을 꽂아 맞추면 둘 다컷씬이 들어가고 못 맞추면 그대로 쿨타임 기다려야하고
    // 맞추면 야레야레 라고 할거거든? 야레야레라고 할때 플레이어가 손으로 머리를 잡고 머리를 흔들어 그리고 스타플래티나 라고
    // 외칠거거든 그때 스타플래티나 보여주고 오라오라 러쉬 엄청 꽂을꺼야 그걸 컷씬으로 만들어줘야해 이건 상대 피가 낮을수록
    // 위력이 더 강해져 기본 데미지는 60이고 낮을수록 더 강해져 가드 브레이커고 가드를 부수면 컷씬에는 안 들어가", "비트다운
    // 꽂을때는 플레이어의 멱살을 잡고 올려치고 그대로 오라러쉬를 꽂아줘 찰지게 때리고 날려야해"): with the stand out, G makes
    // it shout "ORA!" and drive its fist in, a seventh punch out of the combo, at whoever stands first in its way (the
    // finisher's reach). A guard held up against it breaks (HEAVY_BREAK: incapacitated while it is broken) and nothing
    // more comes of it; nobody there, nothing comes of it either; either way its cooldown runs once the blow is over.
    // Striking someone with no guard up, it begins a cutscene for its user and for them (StarPlatinumBeatdown): both held
    // where they are and harmed by nothing else all through it, on the user's recording's own time from its "ORA!"
    // (tools/stand/cut_beatdown.py, the ticks below: a tick on for every 20th of a second of it): "yare yare daze"
    // (its user grabs their head and shakes it), "Star Platinum!" (the stand bursts out of them), the collar grabbed, the
    // uppercut, and the ORA rush, which goes on through "裁くのは俺のスタンドだ" ("the one who judges is my stand") until
    // its "DA!", where one heavy punch sends them flying to where they come down (2.65.0, user request: "계속 오라오라
    // 하다가 나의 스탠드다 끝날때 강펀치 한번만 꽂고 날려버려야해": 2.64.0 took the line's syllables for five big ORAs
    // and threw one at each). Its blows deal BEAT_DAMAGE between them, more the lower their health was as it began
    // (beatdownDamage), none of them taking them below one health: what is more than they had, the fall deals as they come
    // down, and it is the fall that kills them. Its cooldown runs once it is over (2.44.0's rule).

    /** The beatdown's blow's number among the punches (its contact, length and damage are at this index). */
    public static final int BEATDOWN=7;
    /**
     * Ticks into the beatdown (from G) its two sounds start: the opener's "ORA!" (its "RA", 0.15 s into it, on the
     * contact) and, if it struck, the cutscene's: 1.1 s further into the user's recording, so 22 ticks after the first.
     * The recording's second tau is BEAT_OPENER_SOUND + 20 tau ticks in, and every moment of the cutscene below is one of
     * its (since 2.65.0 twelve ticks later than in 2.64.0: the wind-up before the contact is that much longer).
     */
    public static final int BEAT_OPENER_SOUND=13,BEAT_SOUND=35;
    /** The cutscene begins as the blow lands. */
    public static final int BEAT_CUT=16;
    /** "yare yare daze" (1.15 to 2.5 s into the recording): its user's hand on their head as it is said. */
    public static final int BEAT_YARE=36,BEAT_YARE_END=63;
    /** "Star Platinum!" (2.6 s): the stand bursts out of its user. */
    public static final int BEAT_REVEAL=65;
    /** The collar grabbed (the thud 3.8 s in), and the uppercut (the deep thump 4.36 s in). */
    public static final int BEAT_GRAB=89,BEAT_UPPERCUT=100;
    /**
     * The rush: a blow every BEAT_RUSH_EVERY ticks from BEAT_RUSH_FIRST (4.5 s in, as its voice begins), BEAT_RUSH_HITS in
     * all, its voice (to 8.28 s) and on through "裁くのは俺のスタンドだ" (8.35 to 10.7 s) until the stand draws back for the
     * last blow as "スタンド" is said (its last blow 10.1 s in).
     */
    public static final int BEAT_RUSH_FIRST=103,BEAT_RUSH_EVERY=2,BEAT_RUSH_HITS=57,BEAT_RUSH_LAST=BEAT_RUSH_FIRST+BEAT_RUSH_EVERY*(BEAT_RUSH_HITS-1);
    /**
     * The last blow, one heavy punch on the line's "DA!" (the blast on it, 10.8 s in: its "D" 10.71 s, its loudest
     * 10.795 s), where they come down, and the cutscene's end.
     */
    public static final int BEAT_FINAL=229,BEAT_LAND=246,BEAT_END=262;
    /** The beatdown's damage at full health, more the lower it is (beatdownDamage), dealt by its blows between them. */
    public static final float BEAT_DAMAGE=60;
    /** Each blow's share of it: the uppercut's, the rush's (all its blows together), and the last blow's (the rest). */
    public static final float BEAT_UPPERCUT_SHARE=.08f,BEAT_RUSH_SHARE=.6f,BEAT_FINAL_SHARE=1-BEAT_UPPERCUT_SHARE-BEAT_RUSH_SHARE;
    /** Its cooldown once it is over (ticks: thirty seconds, as Kageno's H, the other cutscene). */
    public static final int BEATDOWN_COOLDOWN=600;
    /**
     * Where the cutscene holds the one struck: this far ahead of its user along the blow (blocks; where they stood and
     * BEAT_BACK more, as the blow knocks them back, within BEAT_HOLD_MIN and BEAT_HOLD_MAX: room for the stand between).
     */
    public static final double BEAT_BACK=.4,BEAT_HOLD_MIN=2.8,BEAT_HOLD_MAX=4.4;
    /**
     * Where the last blow sends them from (blocks from where they are held: BEAT_DRIFT on along the blow, as the rush
     * drives them back, and BEAT_LIFT up, as the uppercut threw them) and how far (BEAT_FLY on along it,
     * unless something stops them sooner).
     */
    public static final double BEAT_DRIFT=1.6,BEAT_LIFT=1.7,BEAT_FLY=13;
    /**
     * The beatdown's damage on whoever has {@code health} of {@code max}: BEAT_DAMAGE at full health, up to twice that
     * the nearer they are to none (by the share of their health they have lost).
     */
    public static float beatdownDamage(float health,float max){
        float lost=max<=0?0:1-health/max;lost=lost<0?0:lost>1?1:lost;
        return BEAT_DAMAGE*(1+lost);
    }
    /** Whether a blow of the beatdown's cutscene lands {@code age} ticks into the beatdown: the uppercut, the rush's, the last. */
    public static boolean beatBlow(long age){return beatShare(age)>0;}
    /** The share of the beatdown's damage the blow {@code age} ticks into it deals (0 if none lands then). */
    public static float beatShare(long age){
        if(age==BEAT_UPPERCUT)return BEAT_UPPERCUT_SHARE;
        if(age>=BEAT_RUSH_FIRST&&age<=BEAT_RUSH_LAST&&(age-BEAT_RUSH_FIRST)%BEAT_RUSH_EVERY==0)return BEAT_RUSH_SHARE/BEAT_RUSH_HITS;
        return age==BEAT_FINAL?BEAT_FINAL_SHARE:0;
    }
    /** How far ahead of its user the cutscene holds the one struck, who stood {@code ahead} blocks ahead along the blow. */
    public static double beatHold(double ahead){return Math.max(BEAT_HOLD_MIN,Math.min(BEAT_HOLD_MAX,ahead+BEAT_BACK));}
    /** When the beatdown is ready again once it ended (its blow over, or its cutscene) at {@code end}. */
    public static long beatdownReady(long end){return end+BEATDOWN_COOLDOWN;}

    // ---- Y, the inhale (2.61.0, user request: "이번에는 Y스킬 만들자 스타플래티나가 최대 3블록까지 앞에 있는 플레이어를
    // 입으로 빨아들여서 5초동안 끌고오게 하는 스킬 가드 가능하고 데미지 없음 빨아들이는시간동안은 스타핑거 처럼 무력화
    // 효과음 자체제작"; 2.62.0: "Y는 아무도 안 맞아도 5초동안 지속되고 E처럼 중간에 손 떼면 바로 취소 흡입 무력화는 그냥
    // 95% 슬로우에 아무 스킬 + 대쉬 평타 사용 못하게 그리고 흡입 끝나도 0.5초 정도는 더 무력화", "그리고 흡입은 최대
    // 1명만"): with the stand out, Y held down makes it draw a huge breath and then suck in the air in front of its user
    // (StarPlatinumInhale) for as long as Y stays down, INHALE_SUCK ticks at the most, whether it catches anyone or not;
    // Y let go, it stops at once, as the rush does. From INHALE_CATCH ticks in, as it sucks, the nearest one within
    // INHALE_REACH blocks in front of its user (INHALE_HALF_WIDTH to either side, in sight, the PvP rule) with no guard
    // held up against it is caught, one at most: dragged in over INHALE_PULL ticks to just in front of the stand
    // (inhaleAnchor) and held there until it ends, its user free to turn and to walk (INHALE_SLOW slower), dragging them
    // along. All that while, and INHALE_AFTER ticks once it lets them go, they walk INHALE_HELD_SLOW slower and are
    // incapacitated as the Star Finger's are (no skill, attack, dash or guard: CombatManager.incapacitate), and take no
    // damage. A guard held up against it holds it off for as long as it is up. Its cooldown runs once it is over
    // (2.44.0's rule): let go, run out, or cut short (its user struck, the stand sent back).

    /** Ticks: it draws its breath and begins to suck; it sucks this long at the most after (Y held down all the while). */
    public static final int INHALE_CATCH=8,INHALE_SUCK=100;
    /** Ticks whoever it catches takes to be dragged in to just in front of the stand. */
    public static final int INHALE_PULL=12;
    /** The longest it runs (ticks): its breath and its suction. */
    public static final int INHALE_LENGTH=INHALE_CATCH+INHALE_SUCK;
    /** Ticks whom it held stay incapacitated (and as slow) once it lets them go ("흡입 끝나도 0.5초 정도는 더 무력화"). */
    public static final int INHALE_AFTER=10;
    /** How much slower whom it holds walk (0.95: at a twentieth of their speed, "95% 슬로우"), until INHALE_AFTER after it lets them go. */
    public static final double INHALE_HELD_SLOW=.95;
    /** How far it sucks from its user's feet (blocks ahead) and how wide (to either side). */
    public static final double INHALE_REACH=3,INHALE_HALF_WIDTH=1.1;
    /** Its cooldown once it is over (ticks: fifteen seconds). */
    public static final int INHALE_COOLDOWN=300;
    /** How much slower its user walks while it inhales (0.5: at half their speed), dragging whom it holds. */
    public static final double INHALE_SLOW=.5;
    /**
     * Where whoever it holds is held: this many blocks ahead of its user's feet along the way they face, just in front
     * of the stand (since 2.63.0 it sucks reared back with its head flung to the sky, 1.75 blocks ahead of its user so
     * that its head and mane stay clear of theirs, and whom it holds are just in front of its knees: client/stand/
     * StandInhale.AHEAD, StandInhaleTest). 2.61.0 held them at 1.75 and 2.62.0 at 2.1, at the face of the stand lunging
     * into the suction.
     */
    public static final double INHALE_AHEAD=2.4;
    /** Whether a point (ahead along the way its user faces, to their right and up from their feet, blocks) is in the inhale's way, for a body {@code halfWidth} across. */
    public static boolean inhaleLane(double forward,double side,double height,double halfWidth){
        return forward>=NEAR-halfWidth&&forward<=INHALE_REACH+halfWidth&&Math.abs(side)<=INHALE_HALF_WIDTH+halfWidth&&height>=LOW&&height<=HIGH;
    }
    /** Where the inhale holds whom it caught: x and z of the point INHALE_AHEAD ahead of a user standing at ({@code x}, {@code z}) facing {@code yaw} (degrees, Minecraft's: 0 south). */
    public static double[] inhaleAnchor(double x,double z,float yaw){
        double r=Math.toRadians(yaw);
        return new double[]{x-Math.sin(r)*INHALE_AHEAD,z+Math.cos(r)*INHALE_AHEAD};
    }
    /**
     * How far along its drag the one it caught is (0 where they were caught, 1 at the hold) a share {@code u} of the way
     * through INHALE_PULL: still at both ends, slow to start and quickest late, as air gathers speed into a mouth.
     */
    public static double inhalePull(double u){u=u<0?0:u>1?1:u;double v=Math.pow(u,1.35);return v*v*(3-2*v);}
    /** When the inhale is ready again once it ended at {@code end}. */
    public static long inhaleReady(long end){return end+INHALE_COOLDOWN;}

    /** Whether a blow of the rush lands {@code age} ticks into it. */
    public static boolean rushBlow(long age){return age>=RUSH_FIRST&&age<=RUSH_LAST&&(age-RUSH_FIRST)%RUSH_EVERY==0;}
    /** How many of the rush's blows have landed by {@code age} ticks into it (that one too). */
    public static int rushBlows(long age){return age<RUSH_FIRST?0:(int)Math.min(RUSH_HITS,(age-RUSH_FIRST)/RUSH_EVERY+1);}
    /** When the rush is ready again once it ended at {@code end}. */
    public static long rushReady(long end){return end+RUSH_COOLDOWN;}
    /** Whether a point (ahead along the way its user faces, to their right and up from their feet, blocks) is in the rush's way, for a body {@code halfWidth} across. */
    public static boolean rushLane(double forward,double side,double height,double halfWidth){
        return forward>=NEAR-halfWidth&&forward<=RUSH_REACH+halfWidth&&Math.abs(side)<=RUSH_HALF_WIDTH+halfWidth&&height>=LOW&&height<=HIGH;
    }
    private StarPlatinumRules(){}

    /** Whether the punch {@code combo} shouts (2.56.0: the hook and the finisher; the jab and the cross have none; the heavy punch has its own two, StandAttack). */
    public static boolean shouts(int combo){return combo==3||combo==4;}

    /** The punch after {@code combo}: 1 again after the fourth, the heavy punch or the Star Finger (or none yet). */
    public static int next(int combo){return combo>=1&&combo<4?combo+1:1;}
    /** When the punch after {@code combo} (begun at {@code start}) may start. */
    public static long nextAt(int combo,long start){return start+STEP[combo];}
    /**
     * The tick the attack is ready again after the combo whose punch {@code combo} began at {@code start}, if it broke by
     * {@code now} (the next punch did not start in time), or -1 while it holds (or has nothing to break: none yet, or the
     * fourth punch ended it).
     */
    public static long broken(int combo,long start,long now){
        if(combo<1||combo>=4)return -1;
        long late=nextAt(combo,start)+GRACE;
        return now>late?late+1+REST:-1;
    }
    /** When the attack is ready again after the fourth punch, begun at {@code start}: its rest runs once it is over (2.43.1, 2.44.0). */
    public static long rested(long start){return start+LENGTH[4]+REST;}
    /**
     * How long the punch {@code combo} holds whoever it strikes (ticks from its contact): until the next punch's contact
     * if it starts as soon as it may, and a little past. The fourth punch holds no one (it throws them).
     */
    public static int hold(int combo){return combo>=1&&combo<4?STEP[combo]-CONTACT[combo]+CONTACT[combo+1]+HOLD_PAST:0;}
    /**
     * Whether a point (ahead along the punch, to its right and up from its user's feet, blocks) is in the punch's way,
     * for a body {@code halfWidth} across.
     */
    public static boolean lane(int combo,double forward,double side,double height,double halfWidth){
        // the Star Finger's way (2.60.0): longer and narrower, two fingers stretched out
        if(combo==FINGER)return forward>=NEAR-halfWidth&&forward<=FINGER_REACH+halfWidth&&Math.abs(side)<=FINGER_HALF_WIDTH+halfWidth&&height>=LOW&&height<=HIGH;
        return forward>=NEAR-halfWidth&&forward<=(combo>=4?REACH_BIG:REACH)+halfWidth&&Math.abs(side)<=HALF_WIDTH+halfWidth&&height>=LOW&&height<=HIGH;
    }
}
