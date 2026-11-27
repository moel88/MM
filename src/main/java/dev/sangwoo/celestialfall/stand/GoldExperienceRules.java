package dev.sangwoo.celestialfall.stand;

/**
 * The rules of Gold Experience's skills (pure; GoldExperienceRulesTest).
 * <p>
 * The muda rush, its E (2.147.0, user request: "E스킬 무다러시 부터 만들자 효과음은 이거야 그리고 러시 속도는
 * 스타플래티나보다 미세하게 느리게 데미지도 조금 더 약하게", with the recording of its voice): Star Platinum's rush
 * (StarPlatinumRules, StarPlatinumRush, which runs both) a little slower and a little weaker. E held, the stand comes
 * round to the front of its user and drives its fists into whoever stands there: a blow every 2.2 ticks (Star
 * Platinum's every 2: some nine a second to its ten) from RUSH_FIRST, RUSH_HITS in all, each RUSH_DAMAGE, until E is
 * let go or they have run out. It is as long as the recording's voice (its last blow 3.35 seconds in, the voice dying
 * away by 3.5; Star Platinum's rush is 4.2), so held to the end it deals 30 to Star Platinum's 40. Its way (StarPlatinumRules.rushLane), its user's slow walk, the
 * guard that takes its blows, the hold on whom it strikes and its cooldown are Star Platinum's rush's.
 */
public final class GoldExperienceRules {
    private GoldExperienceRules(){}
    /** The rush's first blow (ticks in: the stand has come round to the front) and how many there are. */
    public static final int RUSH_FIRST=3,RUSH_HITS=30;
    /** Tenths of a tick from one blow to the next: 2.2 ticks, so they fall 2, 2, 2, 2 and 3 ticks apart. */
    public static final int RUSH_STEP=22;
    /** Each blow's damage, and all of them. */
    public static final float RUSH_DAMAGE=1,RUSH_TOTAL=RUSH_DAMAGE*RUSH_HITS;
    /** The tick (into the rush) blow {@code k} (from 0) lands on. */
    public static int rushBlowAt(int k){return RUSH_FIRST+(RUSH_STEP*k+5)/10;}
    /** The last blow, and the rush's end if E is held all the way (its fists have gone back by then). */
    public static final int RUSH_LAST=rushBlowAt(RUSH_HITS-1),RUSH_LENGTH=RUSH_LAST+3;
    /** Ticks from its end until it is ready again (Star Platinum's rush's). */
    public static final int RUSH_COOLDOWN=StarPlatinumRules.RUSH_COOLDOWN;
    /** How long a blow holds whom it strikes (ticks): past the next, which is three ticks on at most. */
    public static final int RUSH_HOLD=6;
    /** Whether a blow lands {@code age} ticks into the rush. */
    public static boolean rushBlow(long age){
        if(age<RUSH_FIRST||age>RUSH_LAST)return false;
        int k=(int)((age-RUSH_FIRST)*10/RUSH_STEP);
        for(int i=Math.max(0,k-1);i<=Math.min(RUSH_HITS-1,k+1);i++)if(rushBlowAt(i)==age)return true;
        return false;
    }
    /** How many blows have landed {@code age} ticks into the rush. */
    public static int rushBlows(long age){int n=0;while(n<RUSH_HITS&&rushBlowAt(n)<=age)n++;return n;}
    /** The tick it is ready again after ending at {@code end}. */
    public static long rushReady(long end){return end+RUSH_COOLDOWN;}

    // ---- R, the sensory overdrive (2.148.0, user request: "이번에는 R스킬 감각폭주 ... 적에게 강펀치를 꽂으면 적은
    // 유체이탈이 돼 그 상태에서는 속도가 2배가 평타도 꽂을 수 있는데 평타가 통과가 되고 유체이탈 상태에서는 아무에게도 안
    // 보이고 자신에게만 보여 그리고 유체이탈 상태에서 본체는 받는 데미지가 2배가 되고 ... 그동안 본체는 아무것도 못하고
    // 맞고만 있어야해", then "10초말고 5초로"; asked and answered: damage 10, cooldown 40 s, a guard takes it, it
    // shoves them back a little): with the stand out, R makes it wind up and drive one heavy punch into the first one in
    // its way, the way its user faced as it began. Whoever it lands on is shoved back a little, and their senses run away from their body: for OVER_LASTS ticks the body stands where it
    // came to rest, able to do nothing, and takes OVER_TAKEN times all harm; on their own screen alone their soul is out
    // of it, walking SOUL_SPEED times as fast and throwing blows that pass through everything (client/stand/SoulClient:
    // the server knows nothing of the soul, nobody else sees it and it touches nothing).

    /** Ticks into the punch its blow lands, and how long the punch is (Star Platinum's heavy punch's). */
    public static final int OVER_CONTACT=12,OVER_LENGTH=28;
    /** The blow's own damage. */
    public static final float OVER_DAMAGE=10;
    /** Ticks from its end until it is ready again (40 s). */
    public static final int OVER_COOLDOWN=800;
    /** How long whoever it lands on is out of their body (ticks: 5 s), and what all harm to the body is multiplied by meanwhile. */
    public static final int OVER_LASTS=100;
    public static final float OVER_TAKEN=2;
    /**
     * It shoves whoever it lands on back a little along it (blocks a tick, out and up: some two and a half blocks on flat
     * ground; asked and answered "조금 밀림"). The bare fists' fourth blow's knock (takeKnockback .95) threw a training
     * dummy some seven blocks in the first take, out of reach of whoever would follow up in the five seconds.
     */
    public static final double OVER_SHOVE=.5,OVER_SHOVE_UP=.22;
    /** How many times as fast as a body their soul walks. */
    public static final float SOUL_SPEED=2;
    /** Whether a body {@code forward} ahead of the user's feet, {@code side} to their side and {@code height} up, half as wide as {@code halfWidth}, is in the punch's way (Star Platinum's finisher's reach). */
    public static boolean overLane(double forward,double side,double height,double halfWidth){
        return forward>=StarPlatinumRules.NEAR-halfWidth&&forward<=StarPlatinumRules.REACH_BIG+halfWidth&&Math.abs(side)<=StarPlatinumRules.HALF_WIDTH+halfWidth
            &&height>=StarPlatinumRules.LOW&&height<=StarPlatinumRules.HIGH;
    }
    /** The tick it is ready again after ending (over, or stopped short) at {@code end}. */
    public static long overReady(long end){return end+OVER_COOLDOWN;}
    /** What harm of {@code amount} comes to for a body whose soul is out of it. */
    public static float overHarm(float amount,boolean out){return out?amount*OVER_TAKEN:amount;}
    // ---- Y, the healing (2.149.0, user request: "이번에는 Y스킬 만들자 힐 스킬이고 꾹 누르면 스탠드가 나와 나를 때리면서
    // 자기자신을 힐 히고 그냥 톡 누르면 스탠드가 나와서 상대 힐 해주는 스킬"; asked and answered: held, 10 a second for
    // five seconds at most; tapped, 60 at once; one cooldown of 45 s for both; held, they walk slowly and a blow on them
    // stops it. Then, of the first make, which healed with a barrage: "나나 상대한테 러시를 꽂으며 힐 해주는거 같은데
    // 그렇게 말고 주먹을 한 번 꽂아서 주먹을 박아넣고 힐 되게": one punch, the fist driven in and left in, and the
    // healing comes through it). With the stand out, Y tapped (let go within HEAL_HOLD ticks) makes it come round to the
    // front and drive one fist into the first one in its way, who is healed MEND as it goes in; Y held makes it come
    // round to its user and drive one fist into them and leave it there, healing them SELF_EACH every SELF_EVERY ticks
    // through it, until Y is let go or the healing has run out.

    /** Y held this many ticks is the hold (the user's own healing); let go sooner it is the tap (Lee So-sang's R's and Kageno's T's threshold). */
    public static final int HEAL_HOLD=8;
    /** Tapped: ticks in its fist goes into whoever it is for, how long it is, and what it heals them. */
    public static final int MEND_CONTACT=6,MEND_LENGTH=18;
    public static final float MEND=60;
    /** Held: ticks in its fist goes into its user and the first healing comes, ticks from one healing to the next, how many there are and what each heals: ten a second for five seconds. */
    public static final int SELF_FIRST=6,SELF_EVERY=2,SELF_BLOWS=50;
    public static final float SELF_EACH=1,SELF_TOTAL=SELF_EACH*SELF_BLOWS;
    /** The last healing, and its end if Y is held all the way. */
    public static final int SELF_LAST=SELF_FIRST+SELF_EVERY*(SELF_BLOWS-1),SELF_LENGTH=SELF_LAST+3;
    /** How much of their speed its user loses while it heals them (the rush's). */
    public static final double SELF_SLOW=StarPlatinumRules.RUSH_SLOW;
    /** Ticks from either one's end until Y is ready again (45 s: one cooldown for both). */
    public static final int HEAL_COOLDOWN=900;
    /** Whether a healing comes to its user {@code age} ticks into it. */
    public static boolean selfBlow(long age){return age>=SELF_FIRST&&age<=SELF_LAST&&(age-SELF_FIRST)%SELF_EVERY==0;}
    /** How many healings have come {@code age} ticks into it. */
    public static int selfBlows(long age){return age<SELF_FIRST?0:(int)Math.min(SELF_BLOWS,(age-SELF_FIRST)/SELF_EVERY+1);}
    /** Whether a body is in reach of its fist (the overdrive punch's way). */
    public static boolean mendLane(double forward,double side,double height,double halfWidth){return overLane(forward,side,height,halfWidth);}
    /** The tick Y is ready again after either ended (over, let go or stopped short) at {@code end}. */
    public static long healReady(long end){return end+HEAL_COOLDOWN;}

    // ---- G, the beatdown: the seven-page muda (2.150.0, user request: "이번에는 G스킬 비트다운이고 7페이지 무다러쉬야
    // 효과음은 영상을 보면 되고 비트다운도 영상을 참고해서 만들고 자료 검색도 해보면서 만들어", with the scene's
    // video, 30.25 s; asked and answered: "눈 클로즈업 빼고 영상 그대로" (the video as it is but for its first shot, the
    // eyes: 0.633 s), it begins as Star Platinum's beatdown does (G, the glow, an opening punch that must land on one
    // with no guard up; a guard up breaks and nothing more comes of it), "스태플래티나 보다는 약하게", "3분"). The
    // opening punch lands BEAT_CUT ticks in and the cutscene begins there, on the video's sound from where its second
    // shot begins (tools/stand/cut_gold_beatdown.py): the recording's second tau is BEAT_CUT + 20 tau ticks in, and
    // every moment below is one of the video's. It is the rush all the way: "muda muda" from its first moment (the
    // video's shots: fists and the face struck, from behind the one struck, close on them, close on the stand), the
    // "WRYYY" as the stand drives them up into the air from under them (BEAT_WRY to BEAT_WRY_END), the rush again high
    // up, seen from over them; then it draws its fist far back (BEAT_WIND) and on the last "muda!" one blow
    // (BEAT_FINAL) sends them flying far off, to come down at BEAT_LAND.

    /** Ticks in: the opening punch lands (the cutscene begins there, and its sound), and how long the punch is if no cutscene comes of it. */
    public static final int BEAT_CUT=16,BEAT_OPENER_LENGTH=30,BEAT_SOUND=BEAT_CUT;
    /** The tick of the recording's second {@code tau}. */
    public static int beatTick(double tau){return BEAT_CUT+(int)Math.round(tau*20);}
    /** The rush's blows: one every BEAT_RUSH_EVERY ticks from BEAT_RUSH_FIRST, BEAT_RUSH_HITS in all, until the stand draws back for its last. */
    public static final int BEAT_RUSH_FIRST=BEAT_CUT+2,BEAT_RUSH_EVERY=2,BEAT_RUSH_HITS=276,BEAT_RUSH_LAST=BEAT_RUSH_FIRST+BEAT_RUSH_EVERY*(BEAT_RUSH_HITS-1);
    /** "WRYYY" (the video's 15.28 to 20.2 s): the stand drives them up into the air. */
    public static final int BEAT_WRY=beatTick(14.65),BEAT_WRY_END=beatTick(19.55);
    /** It draws back for the last blow (the video's 28.28 s), the blow lands (29.58 s), they come down, it is over. */
    public static final int BEAT_WIND=beatTick(27.65),BEAT_FINAL=beatTick(28.95),BEAT_LAND=BEAT_FINAL+26,BEAT_END=BEAT_LAND+16;
    /** Its damage at full health, more the lower it is (beatdownDamage: three quarters of Star Platinum's 60, as its rush is of Star Platinum's). */
    public static final float BEAT_DAMAGE=45;
    /** The rush's share of it between its blows, and the last blow's. */
    public static final float BEAT_RUSH_SHARE=.65f,BEAT_FINAL_SHARE=1-BEAT_RUSH_SHARE;
    /** Ticks from its end (its punch's, or its cutscene's) until it is ready again: three minutes. */
    public static final int BEAT_COOLDOWN=3600;
    /** Where the one struck is held (Star Platinum's beatdown's: on the blow's line, knocked back a little, room for the stand between). */
    public static final double BEAT_BACK=.4,BEAT_HOLD_MIN=2.8,BEAT_HOLD_MAX=4.4;
    /**
     * How high they are held through the first of the rush (blocks, as there is room), how far its blows drive them on
     * along the line, how high the "WRYYY" drives them (over where they stood), and how far the last blow sends them.
     */
    public static final double BEAT_LIFT=1.5,BEAT_DRIFT=1.6,BEAT_HIGH=6,BEAT_FLY=20;
    public static float beatdownDamage(float health,float max){
        float lost=max<=0?0:1-Math.max(0,Math.min(1,health/max));
        return BEAT_DAMAGE*(1+lost);
    }
    /** The share of the beatdown's damage the blow {@code age} ticks into it deals (0 if none lands then). */
    public static float beatShare(long age){
        if(age>=BEAT_RUSH_FIRST&&age<=BEAT_RUSH_LAST&&(age-BEAT_RUSH_FIRST)%BEAT_RUSH_EVERY==0)return BEAT_RUSH_SHARE/BEAT_RUSH_HITS;
        return age==BEAT_FINAL?BEAT_FINAL_SHARE:0;
    }
    public static double beatHold(double ahead){return Math.max(BEAT_HOLD_MIN,Math.min(BEAT_HOLD_MAX,ahead+BEAT_BACK));}
    /** The tick G is ready again after it ended (its punch over, stopped short, or its cutscene) at {@code end}. */
    public static long beatReady(long end){return end+BEAT_COOLDOWN;}

    // ---- T, giving life (2.151.0, user request: "T스킬을 만들자 t스킬은 활용도가 많아 아이템을 들고 t스킬을 쓰면 그
    // 아이템이 개구리로 변하고 10초 동안 내 몸에 붙어 그동안 다른 적이 나를 때리면 데미지 반사 아이템 없이 맨손으로 쓰면
    // 적에게 주먹을 꽂아 주먹에 맞은 적은 랜덤 신체 일부분이 날아가고 그 부분이 파리로 변하고 그 파리가 적을 따라다녀서
    // 방해해(앞으로 알짱거리면서 파리 자기자신을 때리게 유도)적이 그 파리를 한 번 때리면 파리는 죽고 적 자신에게
    // 60데미지가 박히고 1초 동안 쓰러져", then "개구리로 변한 아이템은 10초뒤에 다시 내 아이템으로 돌아옴"; asked and
    // answered: one cooldown of 50 s for both, from the frog's end, or from the fly's popping or the end of its 20 s
    // untouched; the frog reflects every blow for its ten seconds, its user taking none and the striker all of it; the
    // punch deals 5, and a guard taking it turns one of the two guarding arms into the fly; the fly lives 20 s at most).
    // With the stand out, T with an item in the main hand: it becomes a frog on its user's shoulder (2.151.1: of itself,
    // the stand playing no part: "아이템 개구리로 바꿀때는 스탠드가 아이템 안 때려도 돼 손에 들고 있는게 자동으로
    // 개구리로 변하게"; till 2.151.0 the stand's fist touched it); T with the hand empty: the stand drives a fist into the first one in its way, and a part of them flies off
    // and becomes a fly that hovers in front of them (stand/GoldFlyEntity, GoldFlyPath).

    /** With an item: ticks in the item held up turns into the frog (it reflects from then), how long it lives, and its end (the item back in its user's hand). */
    public static final int FROG_BORN=6,FROG_LASTS=200,FROG_END=FROG_BORN+FROG_LASTS;
    /** With the hand empty: ticks in the punch lands, how long it is, and its damage. */
    public static final int FLY_CONTACT=8,FLY_PUNCH_LENGTH=22;
    public static final float FLY_PUNCH_DAMAGE=5;
    /** The part struck off: ticks it takes flying off to become the fly (it can be swatted from then), how long the fly lives, and how long it takes to fly back into the body once its time is up. */
    public static final int FLY_FORMS=8,FLY_LASTS=400,FLY_RETURN=8;
    /** Ticks from the blow until the fly is back in the body, its time up untouched. */
    public static final int FLY_GONE=FLY_FORMS+FLY_LASTS+FLY_RETURN;
    /** What the one it belongs to takes swatting it, and how long they lie knocked down (ticks: 1 s). */
    public static final float FLY_SWAT=60;
    public static final int FLY_DOWN=20;
    /** Ticks from its end (the frog's, the fly's, or the punch's that made none) until T is ready again (50 s: one cooldown for both). */
    public static final int LIFE_COOLDOWN=1000;
    /** The parts a blow can strike off: the head, the arms, the legs (a guard taking it: an arm). */
    public static final int HEAD=0,RIGHT_ARM=1,LEFT_ARM=2,RIGHT_LEG=3,LEFT_LEG=4,PARTS=5;
    /** Whether a body is in reach of its fist (the overdrive punch's way). */
    public static boolean lifeLane(double forward,double side,double height,double halfWidth){return overLane(forward,side,height,halfWidth);}
    /** The tick T is ready again after its frog, its fly or its punch that made none ended at {@code end}. */
    public static long lifeReady(long end){return end+LIFE_COOLDOWN;}
    /** Whether the frog of a T begun at {@code start} reflects at {@code now}: from its birth to its end. */
    public static boolean frogOn(long start,long now){return now-start>=FROG_BORN&&now-start<FROG_END;}
    /** What the striker takes of a blow of {@code amount} on the frog's bearer (all of it: "나는 0, 적이 그대로 받음"). */
    public static float reflected(float amount){return amount;}
    /** Whether a fly struck off {@code age} ticks ago can be swatted now: once it has become the fly, until its time is up. */
    public static boolean swattable(long age){return age>=FLY_FORMS&&age<FLY_FORMS+FLY_LASTS;}
    /** How far to the side of a blow's way its owner's fly still meets it (blocks), and how far beyond whom it strikes. */
    public static final double SWAT_SIDE=1.1,SWAT_PAST=.3;
    /**
     * Whether its owner's blow meets the fly ("앞으로 알짱거리면서 파리 자기자신을 때리게 유도"): the fly {@code ahead}
     * blocks along the blow's way (level), {@code side} to its side and {@code up} over the striker's feet (the fly's
     * underside), the blow reaching {@code reach} blocks or as far as whom it strikes. The way is as wide as a body
     * at either hand: a training dummy's blow and, since the multiplayer check of 2.151.0, a player's bare-handed punch
     * (whose own line, narrower than the fly, all but never met it), so that striking straight ahead mostly meets it.
     */
    public static boolean swatWay(double ahead,double side,double up,double reach){
        return ahead>=.1&&ahead<=reach+SWAT_PAST&&Math.abs(side)<=SWAT_SIDE&&up>=-.6&&up<=2.6;
    }

    // ---- H, the tree of life (2.152.0, user request: "이번에는 H스킬 나무를 소환해서 광역으로 주변에 데미지를 주고 높게
    // 점프하는 스킬 ... 이것도 자료검색해서 만들어봐", then "그리고 적들이 나무를 때리면 그 데미지가 적에게 가게". Searched:
    // in the source (Golden Wind's first episode, the manga's third chapter) Gold Experience turns the ground under Giorno
    // into a tree that lifts him away, and Koichi's blow on the tree comes back on him; Roblox's A Universal Time has the
    // stand bring a tree up under its user that strikes those round it and carries them up. Asked and answered: the stand
    // touches the ground and the tree grows out of it ("내리치지 말고 스탠드가 땅을 만지고 나무가 자라나게 원작 같은 굵은
    // 나무로"), 30 within 6 blocks, a cooldown of 45 s; whom it strikes is pushed out and a guard takes it; its user leaps
    // about 8 blocks. Then, before it went out: "나무 쿨타임을 1분30초로 늘리고 나무가 있는 시간을 20초로 늘리자 쿨타임은
    // 20초가 지난 시점부터 흐르게": the tree there for 20 s from its burst (its last 0.8 s withering back into the ground),
    // the cooldown 90 s from then.)
    // With the stand out, H on the ground: the stand kneels at its user's side and lays its palm on the ground (TREE_TOUCH);
    // at TREE_BURST a thick tree bursts up out of the ground under its user, strikes everyone round it (TREE_DAMAGE within
    // TREE_RADIUS, pushed out from the trunk; a guard takes it) and flings its user up (TREE_RISE blocks, TREE_AHEAD on the
    // way they faced; no harm from that fall). It stands until TREE_WITHER, every blow on it coming back, all of it, on
    // whoever struck it, and is back in the ground by TREE_END, 20 s after it burst up; the cooldown runs from then (the
    // user's rule, as 2.44.0's).

    /** H: ticks in the stand's palm is on the ground, the tree bursts up (its blow, its user's leap), it is grown, it begins to wither, and it is gone. */
    public static final int TREE_TOUCH=7,TREE_BURST=10,TREE_GROWN=22,TREE_WITHER=394,TREE_END=410;
    /** Its blow on everyone round it. */
    public static final float TREE_DAMAGE=30;
    /** How far round the trunk it strikes (blocks, to the near side of a body) and how far below and above its foot. */
    public static final double TREE_RADIUS=6,TREE_BELOW=1.5,TREE_ABOVE=3.5;
    /** How hard whom it strikes is pushed out (blocks a tick: out from the trunk, and up). */
    public static final double TREE_PUSH=.62,TREE_PUSH_UP=.42;
    /** Its user's leap: how high over where they stood (blocks, the top of the arc), and how far on the way they faced by the time they are down again. */
    public static final double TREE_RISE=8,TREE_AHEAD=3;
    /** Ticks from the tree's end until H is ready again (90 s). */
    public static final int TREE_COOLDOWN=1800;
    /** The longest its user is spared the harm of the leap's fall (ticks), should they never come down. */
    public static final int TREE_SPARED=200;
    /** The game's own fall of a body in the air: what it loses of its speed upward and keeps of it, and keeps of its speed along the ground, every tick. */
    public static final double GRAVITY=.08,DRAG=.98,AIR=.91;
    /** The tick H is ready again after its tree ended at {@code end}. */
    public static long treeReady(long end){return end+TREE_COOLDOWN;}
    /** Whether a body {@code half} wide, {@code dx}, {@code dz} from the trunk and {@code dy} over its foot, is in the tree's blow. */
    public static boolean treeArea(double dx,double dy,double dz,double half){return Math.hypot(dx,dz)-half<=TREE_RADIUS&&dy>=-TREE_BELOW&&dy<=TREE_ABOVE;}
    /** The way out from the trunk (level, of length 1) for a body {@code dx}, {@code dz} from it; one right on it goes the way {@code yaw} faces. */
    public static double[] treeOut(double dx,double dz,float yaw){
        double d=Math.hypot(dx,dz);
        if(d<1e-3){double r=Math.toRadians(yaw);return new double[]{-Math.sin(r),Math.cos(r)};}
        return new double[]{dx/d,dz/d};
    }
    /** How high a body rising at {@code v} blocks a tick goes, the game's way: it moves by its speed, then loses GRAVITY and keeps DRAG of it, every tick. */
    public static double apex(double v){
        double y=0,top=0;
        for(int k=0;k<400&&v>0;k++){y+=v;top=Math.max(top,y);v=(v-GRAVITY)*DRAG;}
        return top;
    }
    /** The speed upward (blocks a tick) a body leaving the ground needs to rise {@code rise} blocks. */
    public static double leapSpeed(double rise){
        double lo=0,hi=6;
        for(int k=0;k<60;k++){double m=(lo+hi)/2;if(apex(m)<rise)lo=m;else hi=m;}
        return hi;
    }
    /** How many ticks a body that left the ground rising at {@code v} is in the air before it is back at that height. */
    public static int airborne(double v){
        double y=0;int k=0;
        do{y+=v;v=(v-GRAVITY)*DRAG;k++;}while(y>0&&k<400);
        return k;
    }
    /**
     * How much of its speed along the ground a body keeps the tick it leaves the ground: it is still on it as it moves that
     * tick, so the ground's grip takes it (a common block's slipperiness .6, times AIR) and the air's after.
     */
    public static final double GRIP=.6*AIR;
    /**
     * The speed along the ground (blocks a tick) that carries a body leaving the ground {@code ahead} blocks in {@code ticks}
     * ticks: the first tick on the ground's grip (GRIP), then in the air (AIR of it kept a tick). (The first filming of
     * 2.152.0 took the air's from the first tick and its user came down two blocks on, not three.)
     */
    public static double aheadSpeed(double ahead,int ticks){
        double sum=0,f=1;for(int k=0;k<ticks;k++){sum+=f;f*=k==0?GRIP:AIR;}
        return sum<=0?0:ahead/sum;
    }
    /** Its user's leap as {along the way they faced, up} (blocks a tick), the moment the tree flings them. */
    public static double[] treeLeap(){double up=leapSpeed(TREE_RISE);return new double[]{aheadSpeed(TREE_AHEAD,airborne(up)),up};}
}
