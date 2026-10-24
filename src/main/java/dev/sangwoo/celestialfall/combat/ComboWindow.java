package dev.sangwoo.celestialfall.combat;

/** One landed strike reserves only the immediately following buffered strike. */
public record ComboWindow(int strike,int serial,long hitAt,long attackEnd,long until) {
    public static ComboWindow landed(CombatState state,long now) {
        int strike=state.attack;
        if(strike<1||strike>3)throw new IllegalArgumentException("Only strikes 1-3 hold a target");
        long end=state.attackStart+CombatState.DURATION[strike];
        return new ComboWindow(strike,state.serial,now,end,end+CombatState.WINDUP[strike+1]+2);
    }
    public boolean active(CombatState state,long now) {
        if(now>=until||state.broken(now)||state.guarding||state.dashing(now))return false;
        if(state.serial==serial&&state.attack==strike)
            return now<attackEnd||state.queued&&now<=attackEnd;
        return state.serial==serial+1&&state.attack==strike+1&&state.attacking(now);
    }
    public boolean nextContact(CombatState state,long now) {
        return active(state,now)&&state.serial==serial+1&&state.attack==strike+1;
    }
}
