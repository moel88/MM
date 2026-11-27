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
}
