package dev.sangwoo.celestialfall.engine;

import dev.sangwoo.celestialfall.combat.CombatState;

/**
 * What a fighter may do now, in one table (2.157.0, the combat engine): the actions everyone has (attack, guard, dash,
 * evade) against the phase their combat state is in. Before it each action carried its own list of what stops it; the
 * combat state now asks here (CombatState.requestAttack, guard, dash, evade), and the client asks the same of its
 * prediction. A character's own skills still ask their own managers until they are moved onto the engine.
 * <pre>
 *               attack  guard  dash  evade
 * free            o       o     o     -
 * attacking     (keep)    -     -     -
 * guarding        -      (up)   -     -
 * dashing         -       -     -     -
 * evading         -       -     -     -
 * staggered       -       -     -     o
 * incapacitated   -       -     -     o
 * guard broken    -       -     -     o
 * </pre>
 * (keep: a press near the attack's end is kept for the next; the dash also waits for its cooldown, the guard is shut
 * while glitched, the evasive waits for its own cooldown and is not for a cutscene's or a grab's hold, which the
 * server knows of and checks besides.)
 */
public final class Actions {
    private Actions(){}
    public enum Act{ATTACK,GUARD,DASH,EVADE}
    public enum Phase{FREE,ATTACKING,GUARDING,DASHING,EVADING,STAGGERED,INCAPACITATED,GUARD_BROKEN}

    /** The phase {@code s} is in at {@code now} (the strongest first: an evasive, then what holds it, then what it does). */
    public static Phase phase(CombatState s,long now){
        if(s.evading(now))return Phase.EVADING;
        if(s.broken(now))return Phase.GUARD_BROKEN;
        if(s.disabled(now))return Phase.INCAPACITATED;
        if(s.staggered(now))return Phase.STAGGERED;
        if(s.dashing(now))return Phase.DASHING;
        if(s.attacking(now))return Phase.ATTACKING;
        if(s.guarding)return Phase.GUARDING;
        return Phase.FREE;
    }
    /** Whether a held phase: what the evasive gets them out of (the server adds a barrage's hold). */
    public static boolean held(Phase p){return p==Phase.STAGGERED||p==Phase.INCAPACITATED||p==Phase.GUARD_BROKEN;}

    /** Whether {@code s} may {@code act} at {@code now} (an attack under way keeps a press instead: CombatState.requestAttack). */
    public static boolean may(CombatState s,long now,Act act){
        var p=phase(s,now);
        switch(act){
            case ATTACK:return p==Phase.FREE;
            case GUARD:return (p==Phase.FREE||p==Phase.GUARDING)&&!s.sealed(now);
            case DASH:return p==Phase.FREE&&now>=s.dashReady;
            default:return held(p)&&now>=s.evadeReady;
        }
    }
}
