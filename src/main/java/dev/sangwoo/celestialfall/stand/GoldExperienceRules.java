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
}
