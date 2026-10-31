package dev.sangwoo.celestialfall.combat;

import dev.sangwoo.celestialfall.stand.KagenoRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;

/**
 * A basic attack's blow staggers whom it strikes (2.68.0, user request: "평타 맞으면 아주 조금 무력화에 90% 슬로우 걸리게
 * 이건 모든 스탠드나 기본 평타에도 적용 ... 근데 평타를 느리게 치거나 빗나갈때 반격도 할 수 있어야하니까 진짜 아주 조금
 * 무력화 걸리게"): bare hands', Kageno's sword's and Star Platinum's punches alike. While it lasts they are incapacitated
 * (no skill, attack, dash or guard, and what they were doing is cut off) and walk {@link #SLOW} slower, without the
 * dazed look of a long incapacitation and without any notice on their screen (the hit-stun notice is gone). Since
 * 2.77.0 Lee So-sang's sword's too.
 * <p>
 * It lasts only until the attacker's next blow would land if it came as soon as it may, and {@link #PAST} ticks more: a
 * combo kept at full speed holds them from blow to blow, while one thrown late, or one that misses, lets them act and
 * answer. The fourth blow of a combo throws them; it staggers them {@link #LAST} ticks.
 */
public final class Stagger {
    private Stagger(){}
    /** How much slower they walk while it lasts (a tenth of their speed). */
    public static final double SLOW=.9;
    /** Ticks it outlasts the attacker's next blow at full speed, and the fourth blow's stagger. */
    public static final int PAST=2,LAST=6;

    /**
     * Bare hands' strike {@code strike} that started at {@code start} and lands {@code now} (CombatState): the tick its
     * stagger ends. The next strike, pressed in time, starts as this one ends and lands its windup in.
     */
    public static long bare(int strike,long start,long now){
        return strike>=1&&strike<4?Math.max(now,start+CombatState.DURATION[strike]+CombatState.WINDUP[strike+1])+PAST:now+LAST;
    }
    /**
     * Kageno's M1 hit {@code combo} that started at {@code start} and lands {@code now}: the next hit can start
     * {@link KagenoRules#M1_STEP} ticks after this one did and lands {@link KagenoRules#M1_HIT} in.
     */
    public static long kageno(int combo,long start,long now){
        return combo>=1&&combo<4?Math.max(now,start+KagenoRules.M1_STEP+KagenoRules.M1_HIT)+PAST:now+LAST;
    }
    /**
     * Star Platinum's punch {@code combo} that started at {@code start} and lands {@code now}: the next punch, pressed
     * in time, starts {@link StarPlatinumRules#STEP} after this one and lands at its own contact.
     */
    public static long stand(int combo,long start,long now){
        return combo>=1&&combo<4?Math.max(now,start+StarPlatinumRules.STEP[combo]+StarPlatinumRules.CONTACT[combo+1])+PAST:now+LAST;
    }
    /**
     * Lee So-sang's sword (2.77.0): blow {@code blow} of hit {@code combo} that started at {@code start} and lands
     * {@code now}. The next blow is the hit's own next one (the third hit lands four), else the next hit's first, started
     * as soon as it may (IsosangRules.STEP); the fourth hit's blow throws no one (user request: "평타는 넉백이 없게") and
     * staggers them {@link #LAST} ticks.
     */
    public static long isosang(int combo,int blow,long start,long now){
        return dev.sangwoo.celestialfall.isosang.IsosangRules.staggerUntil(combo,blow,start,now,PAST,LAST);
    }
    /**
     * The same in E's empowered combo or not (2.80.0): a thrown sword holds them until the next sword could reach them,
     * the third hit's last blow until the empowered fourth's thrust could land.
     */
    public static long isosang(int combo,int blow,long start,long now,boolean empowered){
        return dev.sangwoo.celestialfall.isosang.IsosangRules.staggerUntil(combo,blow,start,now,PAST,LAST,empowered);
    }
}
