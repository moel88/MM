package dev.sangwoo.celestialfall.boss;

import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.CounterStance;
import dev.sangwoo.celestialfall.stand.StarPlatinumAttack;
import dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown;
import dev.sangwoo.celestialfall.stand.StarPlatinumInhale;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumRush;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;

/**
 * Kujo Jotaro's way of fighting (2.69.0, user request: "보스 ai는 매우 좋게 ... 콤보를 매우 잘 넣는다거나"): Star
 * Platinum's every skill, the players' own (StarPlatinumAttack, Rush, Inhale, TimeStop), strung into combos that hold.
 * <ul>
 * <li>Whom his blows hold (staggered, incapacitated, a guard broken, caught by his inhale) he finishes while they are
 * held: the beatdown when it is ready and they will be held through its wind-up (sooner the less health they have),
 * else the rush, else the combo.</li>
 * <li>The combo: jab, cross, hook, each staggering them until the next lands (2.68.0); after the hook, the rush if it is
 * ready (its first blow lands inside the hook's stagger), else the heavy punch (it lands in it too), else the finisher.</li>
 * <li>A guard up in front of him he breaks with the heavy punch (or the beatdown), and fights on through it; with
 * neither ready he goes round to their side.</li>
 * <li>Out of a punch's reach and in his fingers', the Star Finger: three seconds held, then the beatdown or the rush.</li>
 * <li>Close, the inhale: held a moment, let go, and the combo as they stand held a half second more.</li>
 * <li>His time stop (BossKind.mayStopTime: his health at 2500 or less) when nobody free is near enough to strike him
 * as he calls it: in the stopped time he walks up to whom he fights and rushes them, then the heavy punch just before
 * time moves again, all of it coming to them at once.</li>
 * <li>Nothing ready: he keeps out of their reach and circles them, and comes in as soon as something is.</li>
 * </ul>
 */
final class JotaroBrain extends BossBrain {
    /** How near whom he fights he throws each (blocks between their feet): a little inside each reach, as they may step back. */
    static final double PUNCH=3.0,FINGER_NEAR=2.6,FINGER_FAR=4.7,GRAB=2.7,HEAVY_REACH=3.7,BEAT_REACH=3.5,RUSH_REACH_AT=3.2;
    private enum Plan{NONE,COMBO,RUSH,HEAVY,FINGER,INHALE,BEATDOWN,TIMESTOP}
    private Plan plan=Plan.NONE;private long planStart;private LivingEntity mark;
    /** In a plan: whether its key went down, and when; an inhale's catch; the last tick whom he rushes stood in the rush's way. */
    private boolean pressed;private long pressedAt,caught=-1,inWay;
    /** The combo's ender is chosen (after the hook). */
    private boolean ender;
    /** Making room to call his time stop: since when (-1: not), and until when he gives that up; how far, how long at most. */
    private long gapSince=-1,gapRest=Long.MIN_VALUE;
    private static final double GAP=8.5;private static final int GAP_TICKS=60;
    JotaroBrain(BossPlayer boss){super(boss);}

    @Override protected boolean committed(long now){return plan!=Plan.NONE&&plan!=Plan.TIMESTOP;}
    @Override protected void onAngry(long now){plan=Plan.NONE;}

    @Override protected void attack(long now){
        if(plan!=Plan.NONE&&(mark==null||!mark.isAlive()||mark.isRemoved()))end();
        if(plan==Plan.NONE){choose(now);if(plan==Plan.NONE)return;}
        switch(plan){
            case COMBO->combo(now);
            case RUSH->rush(now);
            case HEAVY,FINGER,BEATDOWN->skill(now);
            case INHALE->inhale(now);
            case TIMESTOP->timeStop(now);
            default->{}
        }
    }
    private void start(Plan p,LivingEntity on,long now){plan=p;mark=on;planStart=now;pressed=false;caught=-1;inWay=now;ender=false;}
    private void end(){plan=Plan.NONE;pressed=false;mark=null;}

    // ---- what to do next

    private void choose(long now){
        var t=target;double d=flat(t.getPos());
        long held=heldFor(t,now);
        boolean guard=guarding(t),able=StarPlatinumAttack.able(boss);
        float health=t.getHealth()/Math.max(1,t.getMaxHealth());
        if(!able){face(t,30);if(d>WAIT)goTo(t.getPos(),now,true);doing="대기";return;}
        // his time stop, his health low enough: when nobody free stands near enough to strike him as he calls it. With
        // someone free that near he first makes room: the Star Finger on them (it holds them long enough), or he backs
        // off out of their reach (giving it up for 5 s if it takes 3 s)
        if(boss.kind.mayStopTime(boss.getHealth())&&ready(StarPlatinumTimeStop.ready(boss),now)&&d<=14){
            if(held>=30||!threatened(6)){gapSince=-1;start(Plan.TIMESTOP,t,now);return;}
            boolean fingerFirst=ready(StarPlatinumAttack.fingerReady(boss),now)&&d>=FINGER_NEAR&&d<=FINGER_FAR&&!guard&&held<10;
            if(fingerFirst){gapSince=-1;start(Plan.FINGER,t,now);return;}
            if(now>=gapRest){
                if(gapSince<0)gapSince=now;
                if(now-gapSince<GAP_TICKS){keepAway(t,GAP,now);doing="거리 벌리기";return;}
                gapSince=-1;gapRest=now+100;
            }
        }else gapSince=-1;
        if(held>=4&&d<=PUNCH+.3){
            if(ready(StarPlatinumAttack.beatdownReady(boss),now)&&held>=CONTACT[BEATDOWN]+2&&d<=BEAT_REACH&&(health<=.6f||random.nextFloat()<.45f)){start(Plan.BEATDOWN,t,now);return;}
            if(ready(StarPlatinumRush.ready(boss),now)){start(Plan.RUSH,t,now);return;}
            if(punchFree(now)){start(Plan.COMBO,t,now);return;}
        }
        if(guard&&d<=HEAVY_REACH){
            if(ready(StarPlatinumAttack.heavyReady(boss),now)){start(Plan.HEAVY,t,now);return;}
            if(ready(StarPlatinumAttack.beatdownReady(boss),now)&&random.nextFloat()<.5f){start(Plan.BEATDOWN,t,now);return;}
            // round to the side their guard does not face
            keepAway(t,2.4,now);doing="측면";return;
        }
        if(ready(StarPlatinumAttack.fingerReady(boss),now)&&d>=FINGER_NEAR&&d<=FINGER_FAR&&!guard&&held<10){start(Plan.FINGER,t,now);return;}
        if(ready(StarPlatinumInhale.ready(boss),now)&&d<=GRAB&&!guard&&random.nextFloat()<.35f){start(Plan.INHALE,t,now);return;}
        if(d<=PUNCH&&punchFree(now)){start(Plan.COMBO,t,now);return;}
        // in: up to them (dashing in on one who runs), or, nothing ready, out of their reach round them
        boolean anything=punchFree(now)||ready(StarPlatinumAttack.fingerReady(boss),now)||ready(StarPlatinumRush.ready(boss),now);
        if(anything||d>WAIT+1.5){
            face(t,35);goTo(t.getPos(),now,d>4.5);
            var away=t.getVelocity().multiply(1,0,1);var toward=t.getPos().subtract(boss.getPos()).multiply(1,0,1);
            if(d>8.5&&dashReady(now)&&away.dotProduct(toward)>0&&random.nextFloat()<.25f&&room(toward.normalize(),5.4))dash(toward.normalize(),now);
            doing="접근";
        }else{keepAway(t,WAIT,now);doing="견제";}
    }
    /** Whether a player who could strike him stands within {@code reach} (nobody held). */
    private boolean threatened(double reach){
        long now=boss.getWorld().getTime();
        for(var p:boss.getServerWorld().getPlayers()){
            if(!fightable(p)||p.squaredDistanceTo(boss)>reach*reach)continue;
            var s=CombatManager.peek(p);
            if(s!=null&&s.locked(now)&&Math.max(s.disabledUntil,Math.max(s.brokenUntil,s.staggerUntil))-now>=TS_WIND)continue;
            if(StarPlatinumInhale.held(p)||StarPlatinumBeatdown.involved(p))continue;
            return true;
        }
        return false;
    }
    private static boolean ready(long readyAt,long now){return now>=readyAt;}
    /** Whether {@code e} holds a guard up now. */
    private static boolean guarding(LivingEntity e){return e instanceof ServerPlayerEntity p&&CombatManager.guarding(p);}
    /** How much longer {@code e} is held (staggered, incapacitated, its guard broken, caught by an inhale), ticks. */
    static long heldFor(LivingEntity e,long now){
        var s=e instanceof ServerPlayerEntity p?CombatManager.peek(p):null;
        long held=s==null?0:Math.max(0,Math.max(s.disabledUntil,Math.max(s.brokenUntil,s.staggerUntil))-now);
        return StarPlatinumInhale.held(e)?Math.max(held,8):held;
    }
    /** Whether a new combo may start now: no punch in flight, the attack not resting. */
    private boolean punchFree(long now){
        var pu=StarPlatinumAttack.punch(boss);
        if(pu==null)return true;
        if(now<pu.ready())return false;
        return pu.combo()==0||now>=pu.start()+LENGTH[pu.combo()];
    }

    // ---- the plans

    private void combo(long now){
        var t=mark;double d=flat(t.getPos());var pu=StarPlatinumAttack.punch(boss);
        // (only this combo's punches: the last of an earlier one, or a skill's, is no punch of it)
        int c=pu==null||pu.start()<planStart?0:pu.combo();
        boolean flying=pu!=null&&c>=1&&now<pu.start()+LENGTH[c];
        if(!flying&&(d>PUNCH+.9||pu!=null&&now<pu.ready())){end();return;}
        if(CounterStance.active(t)){end();return;}
        face(t,60);
        // close in for the next punch (the stand lunges the rest), not into them
        if(d>2.4)move=t.getPos().subtract(boss.getPos()).multiply(1,0,1).normalize();
        else if(d<1.3)move=boss.getPos().subtract(t.getPos()).multiply(1,0,1).normalize().multiply(.6);
        doing="콤보 "+c;
        if(c==4||StarPlatinumRules.skill(c)){if(!flying||pu.landed())end();return;}
        if(c==3){
            if(!pu.landed()||ender)return;
            ender=true;
            // the hook landed: the rush (its first blow inside the hook's stagger), else the heavy punch, else the finisher
            boolean guard=guarding(t);
            if(guard&&ready(StarPlatinumAttack.heavyReady(boss),now)){start(Plan.HEAVY,t,now);skill(now);return;}
            if(!guard&&ready(StarPlatinumRush.ready(boss),now)){start(Plan.RUSH,t,now);rush(now);return;}
            if(ready(StarPlatinumAttack.heavyReady(boss),now)&&random.nextFloat()<.7f){start(Plan.HEAVY,t,now);skill(now);return;}
            aim(t);StarPlatinumAttack.request(boss);return;
        }
        // the next punch, kept for when it may start (as a press made a little early is)
        if(c==0||!flying||now>=StarPlatinumRules.nextAt(c,pu.start())-BUFFER){if(!flying)aim(t);StarPlatinumAttack.request(boss);}
    }
    private void rush(long now){
        var t=mark;
        if(!StarPlatinumRush.rushing(boss)){
            if(!pressed){
                if(CounterStance.active(t)){end();return;}
                aim(t);
                if(StarPlatinumRush.start(boss)){pressed=true;pressedAt=now;inWay=now;}
                else if(now-planStart>10)end();
                return;
            }
            end();return;
        }
        face(t,28);doing="러시";
        var f=Vec3d.fromPolar(0,boss.getYaw());var right=new Vec3d(-f.z,0,f.x);var o=t.getPos().subtract(boss.getPos());
        if(StarPlatinumRules.rushLane(o.dotProduct(f),o.dotProduct(right),o.y,t.getWidth()*.5))inWay=now;
        if(now-inWay>6||CounterStance.active(t)){StarPlatinumRush.release(boss);end();return;}
        if(flat(t.getPos())>2.2)move=o.multiply(1,0,1).normalize();
    }
    private void skill(long now){
        var t=mark;int which=plan==Plan.HEAVY?HEAVY:plan==Plan.FINGER?FINGER:BEATDOWN;
        if(!pressed){
            if(CounterStance.active(t)){end();return;}
            double d=flat(t.getPos()),reach=which==FINGER?FINGER_FAR+.3:which==HEAVY?HEAVY_REACH:BEAT_REACH;
            face(t,40);
            if(d>reach){goTo(t.getPos(),now,true);if(now-planStart>30)end();return;}
            aim(t);
            boolean ok=which==HEAVY?StarPlatinumAttack.heavy(boss):which==FINGER?StarPlatinumAttack.finger(boss):StarPlatinumAttack.beatdown(boss);
            if(!ok){if(now-planStart>6)end();return;}
            pressed=true;pressedAt=now;return;
        }
        if(StarPlatinumBeatdown.casting(boss)){doing="비트다운";return;}
        var pu=StarPlatinumAttack.punch(boss);
        boolean flying=pu!=null&&pu.combo()==which&&now<pu.start()+LENGTH[which];
        doing=which==HEAVY?"강펀치":which==FINGER?"스타 핑거":"비트다운";
        // walk up to them while the stand does it (the finger's held ones are slow)
        if(flat(t.getPos())>2.2)move=t.getPos().subtract(boss.getPos()).multiply(1,0,1).normalize();
        face(t,30);
        if(flying)return;
        // over: whom it held, while they are held, take the rest
        end();
        if(heldFor(t,now)>=4&&!CounterStance.active(t))choose(now);
    }
    private void inhale(long now){
        var t=mark;
        if(!StarPlatinumInhale.inhaling(boss)){
            if(!pressed){
                aim(t);
                if(StarPlatinumInhale.start(boss)){pressed=true;pressedAt=now;}
                else if(now-planStart>10)end();
                return;
            }
            end();return;
        }
        face(t,25);doing="흡입";
        var held=StarPlatinumInhale.holding(boss);
        if(held!=null){
            if(caught<0)caught=now;
            // held a moment, let go, and the combo while they stand held a half second more
            if(now-caught>=14){
                StarPlatinumInhale.release(boss);
                start(Plan.COMBO,held,now);aim(held);StarPlatinumAttack.request(boss);
            }
        }else if(now-pressedAt>28){StarPlatinumInhale.release(boss);end();}
    }
    private void timeStop(long now){
        if(!pressed){
            if(StarPlatinumTimeStop.request(boss)){pressed=true;pressedAt=now;doing="시간 정지";}
            else end();
            return;
        }
        if(!StarPlatinumTimeStop.active(boss)){end();return;}
        if(!StarPlatinumTimeStop.stopping(boss)){doing="더 월드!";return;}
        // stopped time: up to the one he fights (another stopped one if they were not caught), the rush, then the heavy punch
        long moves=StarPlatinumTimeStop.calledAt(boss)+TS_WIND+TS_LENGTH,left=moves-now;
        LivingEntity v=mark!=null&&StarPlatinumTimeStop.stopped(mark)?mark:null;
        if(v==null)for(var p:boss.getServerWorld().getPlayers())if(fightable(p)&&StarPlatinumTimeStop.stopped(p)&&(v==null||p.getHealth()<v.getHealth()))v=p;
        if(v==null){if(StarPlatinumRush.rushing(boss))StarPlatinumRush.release(boss);doing="정지된 시간";return;}
        double d=flat(v.getPos());face(v,45);doing="정지된 시간 공격";
        if(StarPlatinumRush.rushing(boss)){
            if(left<=CONTACT[HEAVY]+STOP_RETURN+4)StarPlatinumRush.release(boss);
            else if(d>2.2)move=v.getPos().subtract(boss.getPos()).multiply(1,0,1).normalize();
            return;
        }
        if(d>RUSH_REACH_AT){goTo(v.getPos(),now,true);if(d>6&&dashReady(now))dash(v.getPos().subtract(boss.getPos()).multiply(1,0,1).normalize(),now);return;}
        if(left>CONTACT[HEAVY]+STOP_RETURN+12&&ready(StarPlatinumRush.ready(boss),now)&&StarPlatinumAttack.able(boss)){aim(v);StarPlatinumRush.start(boss);return;}
        if(left>=CONTACT[HEAVY]+2&&ready(StarPlatinumAttack.heavyReady(boss),now)&&StarPlatinumAttack.able(boss)){aim(v);StarPlatinumAttack.heavy(boss);return;}
        if(left>CONTACT[1]+1){aim(v);StarPlatinumAttack.request(boss);}
    }

    // ---- answers

    /**
     * A blow comes that his jab lands before (and the jab's stagger stops it, 2.68.0): a Star Platinum user winding up a
     * heavy punch, a Star Finger, a beatdown or an inhale, a bare hand slow to swing, Kageno beginning a cast, right in
     * front of him. He jabs first, and the combo goes on from it.
     */
    @Override protected boolean strikeFirst(BossThreats.Threat t,int left,long now){
        var from=t.from();if(from==null||t.timeStop()||CounterStance.active(from))return false;
        // answered already: while its own blow is on its way (it lands first) it keeps to it, and does not dash away
        // from its own jab; once that blow has landed and the threat is still coming, it is answered otherwise
        if(from==answeredFrom&&t.seen()==answeredSeen){
            var mine=StarPlatinumAttack.punch(boss);
            if(mine!=null&&mine.combo()>=1&&mine.combo()<CONTACT.length&&now<mine.start()+CONTACT[mine.combo()])return true;
            return false;
        }
        String w=t.what();
        boolean slow=w.equals("punch"+HEAVY)||w.equals("punch"+FINGER)||w.equals("punch"+BEATDOWN)||w.equals("inhale")||w.startsWith("kageno")||w.startsWith("bare");
        if(!slow||left<CONTACT[1]+1||plan!=Plan.NONE&&plan!=Plan.COMBO)return false;
        if(flat(from.getPos())>PUNCH+.4||!punchFree(now)||!StarPlatinumAttack.able(boss))return false;
        releaseKeys();if(guarding())guard(false,now);
        aim(from);
        if(!StarPlatinumAttack.request(boss))return false;
        start(Plan.COMBO,from,now);answeredFrom=from;answeredSeen=t.seen();
        return true;
    }
    /** The threat it last answered with a blow of its own first (whose, and when it was begun). */
    private net.minecraft.server.network.ServerPlayerEntity answeredFrom;private long answeredSeen=Long.MIN_VALUE;
    /**
     * A player calls a time stop: struck before it stops, the call is cut short. Within the Star Finger's reach, it; within
     * a punch's, a jab; a dash and a jab away, the dash first. Otherwise he gets out of its reach.
     */
    @Override protected boolean answerTimeStop(BossThreats.Threat t,long now){
        var from=t.from();if(from==null)return false;
        long left=t.hitAt()-now;double d=flat(from.getPos());
        if(!StarPlatinumAttack.able(boss))return false;
        if(d<=FINGER_FAR+.3&&d>=PUNCH&&ready(StarPlatinumAttack.fingerReady(boss),now)&&left>CONTACT[FINGER]){aim(from);if(StarPlatinumAttack.finger(boss)){end();start(Plan.FINGER,from,now);pressed=true;pressedAt=now;return true;}}
        if(d<=PUNCH+.4&&punchFree(now)&&left>CONTACT[1]){releaseKeys();aim(from);if(StarPlatinumAttack.request(boss)){start(Plan.COMBO,from,now);return true;}}
        // a dash toward them and a jab still land before it stops
        if(d<=PUNCH+5.2&&left>=7+CONTACT[1]&&dashReady(now)){
            var toward=from.getPos().subtract(boss.getPos()).multiply(1,0,1).normalize();
            if(room(toward,Math.min(5.4,d-1.5))){face(from,90);dash(toward,now);return true;}
        }
        if(d<=PUNCH+3){face(from,60);move=from.getPos().subtract(boss.getPos()).multiply(1,0,1).normalize();sprint=true;return true;}
        return false;
    }
}
