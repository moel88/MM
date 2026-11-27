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
}
