package dev.sangwoo.celestialfall.combat;

/** Server-clock combat transitions; deliberately independent of rendering and networking. */
public final class CombatState {
    public static final int[] WINDUP={0,3,4,5,6}, DURATION={0,10,11,13,19};
    public static final float[] DAMAGE={0,3,3,4,6};
    public static final int DASH_TICKS=7, DASH_COOLDOWN=24, BREAK_TICKS=20;
    public int combo, attack, serial;
    public long attackStart=-1000, comboUntil, dashStart=-1000, dashReady, brokenUntil, combatUntil;
    public long guardHeartbeat=-1000, lastGuardHit=-1000, lastRequest=-1000;
    /**
     * When the guard last broke (2.60.0: its stagger is timed from it, the heavy punch's break being longer than one
     * worn down), and the incapacitation of 2.60.0 (Star Platinum's Star Finger): until when, and since when; with a
     * slow on the legs, how much slower (0 to 1) and until when.
     */
    public long brokenAt=-1000, disabledUntil, disabledAt=-1000, slowUntil;
    public double slow;
    public boolean guarding, queued, hitDone;
    public float pressure, dashForward=1, dashSide,dashYaw;
    public boolean broken(long now) { return now<brokenUntil; }
    /** Incapacitated (2.60.0, "무력화"): no skill, attack, dash or guard; walking only. */
    public boolean disabled(long now) { return now<disabledUntil; }
    /**
     * Whether its holder can do nothing but walk: incapacitated, or its guard broken (2.60.0: "그냥 가드 브레이커 개념이
     * 2초 무력화로 알고 있으면 됨", a broken guard is an incapacitation for as long as it is broken).
     */
    public boolean locked(long now) { return broken(now)||disabled(now); }
    /** Whether a slow is on its legs now ({@link #slow} of their speed taken away). */
    public boolean slowed(long now) { return now<slowUntil&&slow>0; }
    public boolean dashing(long now) { return now>=dashStart && now<dashStart+DASH_TICKS; }
    public boolean attacking(long now) { return attack>0 && now<attackStart+DURATION[attack]; }
    public boolean requestAttack(long now) {
        if(now-lastRequest<3)return false;
        lastRequest=now;
        if(locked(now)||guarding||dashing(now))return false;
        if(attacking(now)) {
            if(attack<4 && now>=attackStart+DURATION[attack]-6)queued=true;
            return false;
        }
        startAttack(now);return true;
    }
    private void startAttack(long now) {
        combo=now>comboUntil||combo>=4?1:combo+1;
        attack=combo;attackStart=now;hitDone=false;queued=false;serial++;
        comboUntil=now+DURATION[attack]+14;combatUntil=now+100;
    }
    public boolean hitDue(long now) {
        if(attack==0||hitDone||now<attackStart+WINDUP[attack])return false;
        hitDone=true;return true;
    }
    public boolean tick(long now) {
        boolean changed=false;
        if(guarding && now-guardHeartbeat>16) { guarding=false;changed=true; }
        if(attack>0 && now>=attackStart+DURATION[attack]) {
            if(queued&&attack<4&&!locked(now)&&!guarding)startAttack(now);
            else {attack=0;queued=false;}
            changed=true;
        }
        if(now>comboUntil && attack==0)combo=0;
        if(!broken(now)&&now-lastGuardHit>=30&&pressure>0)pressure=Math.max(0,pressure-(guarding?.22f:.85f));
        return changed;
    }
    public boolean guard(boolean hold,long now) {
        guardHeartbeat=now;
        boolean next=hold&&!locked(now)&&!attacking(now)&&!dashing(now);
        boolean changed=next!=guarding;guarding=next;
        if(next) {combatUntil=now+100;queued=false;}
        return changed;
    }
    public boolean guardHit(float damage,long now) { return guardHit(damage,now,false); }
    public boolean guardHit(float damage,long now,boolean unarmed) {
        lastGuardHit=now;combatUntil=now+100;
        pressure=Math.min(100,pressure+(unarmed?25:Math.min(45,18+Math.max(0,damage)*5)));
        if(pressure<100)return false;
        guarding=false;brokenUntil=now+BREAK_TICKS;brokenAt=now;attack=combo=0;queued=false;serial++;
        return true;
    }
    /** The guard broken outright by a blow no guard can take (2.59.0: Star Platinum's heavy punch), broken for {@code ticks}. */
    public void breakGuard(long now,int ticks) {
        lastGuardHit=now;combatUntil=now+100;pressure=100;
        guarding=false;brokenUntil=now+ticks;brokenAt=now;attack=combo=0;queued=false;serial++;
    }
    /**
     * Incapacitated for {@code ticks} (2.60.0: struck by Star Platinum's Star Finger), its legs {@code slow} slower for
     * {@code slowTicks}: whatever it was doing is cut off (a dash ends there). A longer incapacitation or slow already
     * on stays.
     */
    public void incapacitate(long now,int ticks,int slowTicks,double slow) {
        if(!disabled(now))disabledAt=now;
        disabledUntil=Math.max(disabledUntil,now+ticks);
        if(slowTicks>0&&slow>0){if(!slowed(now)||slow>=this.slow)this.slow=slow;slowUntil=Math.max(slowUntil,now+slowTicks);}
        combatUntil=Math.max(combatUntil,now+100);cancel(now);serial++;
    }
    /**
     * The incapacitation that was to last until {@code until} ends at {@code to} instead (not before now), and so does a
     * slow that was to (2.61.0: Star Platinum's inhale let go of them early; 2.62.0: they stay incapacitated and slow a
     * little after it lets go, "흡입 끝나도 0.5초 정도는 더 무력화"). One that lasts longer (another incapacitated or
     * slowed them for longer since) stays, and none is drawn out. Returns whether either changed.
     */
    public boolean cut(long now,long until,long to) {
        to=Math.max(to,now);boolean changed=false;
        if(disabledUntil==until&&disabled(now)&&to<until){disabledUntil=to;changed=true;}
        if(slowUntil==until&&slowed(now)&&to<until){slowUntil=to;changed=true;}
        if(changed)serial++;
        return changed;
    }
    public boolean dash(long now,float forward,float side) {
        if(now<dashReady||locked(now)||attacking(now)||guarding||dashing(now))return false;
        float length=(float)Math.hypot(forward,side);
        dashForward=length<.1f?1:forward/length;dashSide=length<.1f?0:side/length;
        dashStart=now;dashReady=now+DASH_TICKS+DASH_COOLDOWN;combatUntil=now+100;combo=0;serial++;return true;
    }
    /**
     * The dash ends short (a wall, water, a blow): its cooldown runs from now. Every cooldown runs once its move is over,
     * not from when it began (user request, 2.44.0); a whole dash's runs from its end.
     */
    public void stopDash(long now) { if(dashing(now))dashReady=Math.min(dashReady,now+DASH_COOLDOWN);dashStart=-1000; }
    /** A player's moves are cut off (a blow, a skill of their own): a dash ends there, its cooldown running from now. */
    public void cancel(long now) { stopDash(now);cancel(); }
    public void cancel() { attack=combo=0;queued=guarding=false;dashStart=-1000; }
    public static double dashSpeed(int age) {
        return age<0||age>=DASH_TICKS?0:new double[]{.78,1.04,1.06,.94,.76,.52,.28}[age];
    }
}
