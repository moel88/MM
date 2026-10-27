package dev.sangwoo.celestialfall.combat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.entity.LivingEntity;

/**
 * Counters (2.69.0, user request: "나중에 나오는 반격기(플레이어를 때리면 오히려 자신이 맞는 스킬)를 반응해서 안
 * 때린다거나"): a skill that strikes back whoever strikes its user while it is up. None exists yet; each one that comes
 * says here when its user stands in it ({@link #register}), and a boss reads it before every blow it starts (BossBrain:
 * it does not strike into a counter, it waits it out out of reach and strikes once it is down).
 */
public final class CounterStance {
    private static final List<Predicate<LivingEntity>> STANCES=new CopyOnWriteArrayList<>();
    private CounterStance(){}
    /** A counter skill tells when its user stands in it (on the server). */
    public static void register(Predicate<LivingEntity> stance){STANCES.add(stance);}
    /** Whether {@code e} stands in a counter now: striking them now strikes the striker. */
    public static boolean active(LivingEntity e){
        if(e==null)return false;
        for(var s:STANCES)if(s.test(e))return true;
        return false;
    }
    /** (Tests: forget every registered counter.) */
    static void clear(){STANCES.clear();}
}
