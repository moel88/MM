package dev.sangwoo.celestialfall.combat;

/** Server-clock combat transitions; deliberately independent of rendering and networking. */
public final class CombatState {
    public static final int[] WINDUP={0,3,4,5,6}, DURATION={0,10,11,13,19};
    public static final float[] DAMAGE={0,3,3,4,6};
    public static final int DASH_TICKS=7, DASH_COOLDOWN=24, BREAK_TICKS=20;
    public int combo, attack, serial;
    public long attackStart=-1000, comboUntil, dashStart=-1000, dashReady, brokenUntil, combatUntil;
    public long guardHeartbeat=-1000, lastGuardHit=-1000, lastRequest=-1000;
    public boolean guarding, queued, hitDone;
    public float pressure, dashForward=1, dashSide,dashYaw;
    public boolean broken(long now) { return now<brokenUntil; }
    public boolean dashing(long now) { return now>=dashStart && now<dashStart+DASH_TICKS; }
    public boolean attacking(long now) { return attack>0 && now<attackStart+DURATION[attack]; }
    public boolean requestAttack(long now) {
        if(now-lastRequest<3)return false;
        lastRequest=now;
        if(broken(now)||guarding||dashing(now))return false;
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
            if(queued&&attack<4&&!broken(now)&&!guarding)startAttack(now);
            else {attack=0;queued=false;}
            changed=true;
        }
        if(now>comboUntil && attack==0)combo=0;
        if(!broken(now)&&now-lastGuardHit>=30&&pressure>0)pressure=Math.max(0,pressure-(guarding?.22f:.85f));
        return changed;
    }
    public boolean guard(boolean hold,long now) {
        guardHeartbeat=now;
        boolean next=hold&&!broken(now)&&!attacking(now)&&!dashing(now);
        boolean changed=next!=guarding;guarding=next;
        if(next) {combatUntil=now+100;queued=false;}
        return changed;
    }
    public boolean guardHit(float damage,long now) { return guardHit(damage,now,false); }
    public boolean guardHit(float damage,long now,boolean unarmed) {
        lastGuardHit=now;combatUntil=now+100;
        pressure=Math.min(100,pressure+(unarmed?25:Math.min(45,18+Math.max(0,damage)*5)));
        if(pressure<100)return false;
        guarding=false;brokenUntil=now+BREAK_TICKS;attack=combo=0;queued=false;serial++;
        return true;
    }
    public boolean dash(long now,float forward,float side) {
        if(now<dashReady||broken(now)||attacking(now)||guarding||dashing(now))return false;
        float length=(float)Math.hypot(forward,side);
        dashForward=length<.1f?1:forward/length;dashSide=length<.1f?0:side/length;
        dashStart=now;dashReady=now+DASH_COOLDOWN;combatUntil=now+100;combo=0;serial++;return true;
    }
    public void cancel() { attack=combo=0;queued=guarding=false;dashStart=-1000; }
    public static double dashSpeed(int age) {
        return age<0||age>=DASH_TICKS?0:new double[]{.78,1.04,1.06,.94,.76,.52,.28}[age];
    }
}
