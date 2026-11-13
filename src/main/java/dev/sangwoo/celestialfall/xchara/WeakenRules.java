package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's T with the hack on, the weakening (2.126.0, user request: "q활성화 t스킬을 만들자 바라보는 적에게 !!!(기존
 * !!!효과음 사용)가 뜨고 머리 위에 보라색 글씨로 공격력 방어력이 25% 감소한다. 라고 뜨게 해주고 몸에도 보라색 아래 방향
 * 화살표 아우라가 방출되게 디버프는 20초 동안 지속 돼"): its timing and its numbers, on the server's ticks from the
 * press. The user's answers to what I asked: its cooldown 50 seconds ("50초"); a guard during the "!!!" blocks it as G's
 * and R's ("G·R처럼 가드로 막힘"); its "defence down 25%" is harm taken times 1.25 ("받는 피해 1.25배"; its "attack down
 * 25%" harm dealt times 0.75, as I said I would have it); the words stay over their head all its 20 seconds ("20초
 * 내내"). The name, the weakening, is mine.
 * <p>
 * The press marks the one looked at (as G's, XCharaRules.AIM_RANGE) and the "!!!" shows over them from {@link #MARK};
 * any tick before {@link #APPLY} that they hold a guard up toward its user, it is blocked. At APPLY they are weakened
 * for {@link #LASTS} ticks: what they deal is times {@link #DEALT}, what they take times {@link #TAKEN}. Its user is
 * held where they stand until APPLY. Its cooldown ({@link #COOLDOWN}) runs from then, or from when it was blocked or
 * called off.
 */
public final class WeakenRules {
    private WeakenRules(){}
    /** The "!!!" over the one marked from this tick; the weakening on them at this one (0.75 s: until then a guard blocks it, and its user is held). */
    public static final int MARK=1,APPLY=15;
    /** How long they are weakened (the user: 20 seconds). */
    public static final int LASTS=400;
    /** Its cooldown (the user's answer: 50 seconds): from when it took, or from when it was blocked or called off. */
    public static final int COOLDOWN=1000;
    /** Weakened, what they deal is times this ("공격력 25% 감소") and what they take times this (the user's answer: "받는 피해 1.25배"). */
    public static final float DEALT=.75f,TAKEN=1.25f;

    /** Whether a guard can still block it at {@code age} ticks (the "!!!" before it takes). */
    public static boolean warning(long age){return age>=0&&age<APPLY;}
    /** Whether its user is held at {@code age} ticks: through the "!!!" (they stand looking at the one marked). */
    public static boolean userHeld(long age){return age>=0&&age<APPLY;}
    /** When it is ready again after one that took (or was blocked or called off) at {@code end}. */
    public static long ready(long end){return end+COOLDOWN;}
    /** What a blow of {@code amount} comes to, dealt by one weakened or not to one weakened or not. */
    public static float harm(float amount,boolean dealerWeak,boolean takerWeak){return amount*scale(dealerWeak,takerWeak);}
    /** What a blow is multiplied by, dealt by one weakened or not to one weakened or not. */
    public static float scale(boolean dealerWeak,boolean takerWeak){return (dealerWeak?DEALT:1)*(takerWeak?TAKEN:1);}
    /** How much of its time is left at {@code now} for one weakened until {@code until} (0 to 1). */
    public static float left(long until,double now){return (float)Math.max(0,Math.min(1,(until-now)/LASTS));}
}
