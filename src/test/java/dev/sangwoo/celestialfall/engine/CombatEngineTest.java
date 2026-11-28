package dev.sangwoo.celestialfall.engine;

import dev.sangwoo.celestialfall.combat.CombatState;
import dev.sangwoo.celestialfall.combat.MotionClips;
import dev.sangwoo.celestialfall.combat.MotionPose;
import java.util.Random;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The combat engine (2.157.0): bare hands' strikes as its chain with the very numbers they had and running just as they
 * did, what may be done when by one table, the perfect guard and the evasive, the lag compensation's wind-back, its
 * shapes, and the client's prediction agreeing with the server however far apart their clocks are.
 */
class CombatEngineTest {
    /**
     * 2.156.0's combat state (before the engine), its rules as they were, word for word: the engine's is held to it.
     * (Its slows from what only slows and its cut are left out: the engine did not touch them.)
     */
    private static final class Before {
        static final int[] WINDUP={0,3,4,5,6}, DURATION={0,10,11,13,19};
        static final int DASH_TICKS=7, DASH_COOLDOWN=24, BREAK_TICKS=20;
        int combo, attack, serial;
        long attackStart=-1000, comboUntil, dashStart=-1000, dashReady, brokenUntil, combatUntil;
        long guardHeartbeat=-1000, lastGuardHit=-1000, lastRequest=-1000;
        long brokenAt=-1000, disabledUntil, disabledAt=-1000, slowUntil;
        double slow;
        long staggerUntil, sealedUntil;
        boolean guarding, queued, hitDone;
        float pressure, dashForward=1, dashSide;
        boolean broken(long now) { return now<brokenUntil; }
        boolean sealed(long now) { return now<sealedUntil; }
        boolean disabled(long now) { return now<disabledUntil; }
        boolean staggered(long now) { return now<staggerUntil; }
        boolean locked(long now) { return broken(now)||disabled(now)||staggered(now); }
        boolean slowed(long now) { return now<slowUntil&&slow>0; }
        boolean dashing(long now) { return now>=dashStart && now<dashStart+DASH_TICKS; }
        boolean attacking(long now) { return attack>0 && now<attackStart+DURATION[attack]; }
        boolean requestAttack(long now) {
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
        boolean hitDue(long now) {
            if(attack==0||hitDone||now<attackStart+WINDUP[attack])return false;
            hitDone=true;return true;
        }
        boolean tick(long now) {
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
        boolean guard(boolean hold,long now) {
            guardHeartbeat=now;
            boolean next=hold&&!locked(now)&&!attacking(now)&&!dashing(now)&&!sealed(now);
            boolean changed=next!=guarding;guarding=next;
            if(next) {combatUntil=now+100;queued=false;}
            return changed;
        }
        boolean guardHit(float damage,long now) {
            lastGuardHit=now;combatUntil=now+100;
            pressure=Math.min(100,pressure+Math.min(45,18+Math.max(0,damage)*5));
            if(pressure<100)return false;
            guarding=false;brokenUntil=now+BREAK_TICKS;brokenAt=now;attack=combo=0;queued=false;serial++;
            return true;
        }
        boolean seal(long now,int ticks) {
            long until=now+Math.max(0,ticks);boolean changed=until>sealedUntil||guarding;
            sealedUntil=Math.max(sealedUntil,until);
            if(guarding){guarding=false;queued=false;}
            combatUntil=Math.max(combatUntil,now+100);
            if(changed)serial++;
            return changed;
        }
        void breakGuard(long now,int ticks) {
            lastGuardHit=now;combatUntil=now+100;pressure=100;
            guarding=false;brokenUntil=now+ticks;brokenAt=now;attack=combo=0;queued=false;serial++;
        }
        void incapacitate(long now,int ticks,int slowTicks,double slow) {
            if(!disabled(now))disabledAt=now;
            disabledUntil=Math.max(disabledUntil,now+ticks);
            if(slowTicks>0&&slow>0){if(!slowed(now)||slow>=this.slow)this.slow=slow;slowUntil=Math.max(slowUntil,now+slowTicks);}
            combatUntil=Math.max(combatUntil,now+100);cancel(now);serial++;
        }
        void stagger(long now,long until,double slow) {
            staggerUntil=Math.max(staggerUntil,until);
            if(slow>0&&until>now){if(!slowed(now)||slow>=this.slow)this.slow=slow;slowUntil=Math.max(slowUntil,until);}
            combatUntil=Math.max(combatUntil,now+100);cancel(now);serial++;
        }
        boolean dash(long now,float forward,float side) {
            if(now<dashReady||locked(now)||attacking(now)||guarding||dashing(now))return false;
            float length=(float)Math.hypot(forward,side);
            dashForward=length<.1f?1:forward/length;dashSide=length<.1f?0:side/length;
            dashStart=now;dashReady=now+DASH_TICKS+DASH_COOLDOWN;combatUntil=now+100;combo=0;serial++;return true;
        }
        void stopDash(long now) { if(dashing(now))dashReady=Math.min(dashReady,now+DASH_COOLDOWN);dashStart=-1000; }
        void cancel(long now) { stopDash(now);attack=combo=0;queued=guarding=false;dashStart=-1000; }
    }
    private static String state(Before b){
        return b.combo+" "+b.attack+" "+b.serial+" "+b.attackStart+" "+b.comboUntil+" "+b.dashStart+" "+b.dashReady+" "+b.brokenUntil+" "+b.combatUntil
            +" "+b.guardHeartbeat+" "+b.lastGuardHit+" "+b.lastRequest+" "+b.brokenAt+" "+b.disabledUntil+" "+b.disabledAt+" "+b.slowUntil+" "+b.slow
            +" "+b.staggerUntil+" "+b.sealedUntil+" "+b.guarding+" "+b.queued+" "+b.hitDone+" "+b.pressure+" "+b.dashForward+" "+b.dashSide;
    }
    private static String state(CombatState b){
        return b.combo+" "+b.attack+" "+b.serial+" "+b.attackStart+" "+b.comboUntil+" "+b.dashStart+" "+b.dashReady+" "+b.brokenUntil+" "+b.combatUntil
            +" "+b.guardHeartbeat+" "+b.lastGuardHit+" "+b.lastRequest+" "+b.brokenAt+" "+b.disabledUntil+" "+b.disabledAt+" "+b.slowUntil+" "+b.slow
            +" "+b.staggerUntil+" "+b.sealedUntil+" "+b.guarding+" "+b.queued+" "+b.hitDone+" "+b.pressure+" "+b.dashForward+" "+b.dashSide;
    }

    @Test void bareHandsChainHasTheNumbersTheyAlwaysHad(){
        assertArrayEquals(Before.WINDUP,CombatState.WINDUP);
        assertArrayEquals(Before.DURATION,CombatState.DURATION);
        assertArrayEquals(new float[]{0,3,3,4,6},CombatState.DAMAGE);
        var c=Moves.BARE;assertEquals(4,c.size());assertEquals(6,c.queue);assertEquals(14,c.reset);assertEquals(3,c.every);
        for(int i=1;i<=4;i++){
            var move=c.move(i);var h=move.first();
            assertEquals(1,move.hits.size(),"one blow a strike");
            assertEquals(Before.WINDUP[i],h.tick());assertEquals(Before.DURATION[i],move.length);
            assertEquals(Move.Guard.BLOCKABLE,h.guard(),"every strike is blockable");
            var ray=(Hitbox.Ray)h.shape();assertEquals(3.15,ray.reach(),1e-9);assertEquals(.14,ray.pad(),1e-9);
            assertEquals(i==4?.95:0,h.knock(),1e-9,"only the fourth throws");
            assertEquals(Before.WINDUP[i],move.startup());assertEquals(1,move.active());
            assertEquals(move.length-move.startup()-1,move.recovery());
        }
        for(int i=0;i<Moves.count();i++)assertEquals(i,Moves.number(Moves.byNumber(i)),"a move's number names it");
    }
    /**
     * Pressed, guarded, dashed, struck, staggered, incapacitated, glitched and broken at random, the engine's combat state
     * and the one before it agree on everything tick after tick (the evasive and the perfect guard are never asked for).
     */
    @Test void theEngineRunsBareHandsJustAsBefore(){
        var random=new Random(157);
        for(int run=0;run<400;run++){
            var now=new CombatState();var old=new Before();
            for(long t=0;t<500;t++){
                assertEquals(old.tick(t),now.tick(t),"tick "+t);
                assertEquals(old.hitDue(t),now.hitDue(t),"blow due at "+t);
                int r=random.nextInt(1000);
                if(r<220)assertEquals(old.requestAttack(t),now.requestAttack(t),"press at "+t);
                else if(r<290){boolean h=random.nextInt(4)!=0;assertEquals(old.guard(h,t),now.guard(h,t),"guard at "+t);}
                else if(r<310){float f=random.nextInt(3)-1,s=random.nextInt(3)-1;assertEquals(old.dash(t,f,s),now.dash(t,f,s),"dash at "+t);}
                else if(r<325){long until=t+2+random.nextInt(14);double sl=random.nextBoolean()?.9:0;old.stagger(t,until,sl);now.stagger(t,until,sl);}
                else if(r<330){int ticks=5+random.nextInt(40),slowTicks=random.nextInt(50);double sl=random.nextDouble();old.incapacitate(t,ticks,slowTicks,sl);now.incapacitate(t,ticks,slowTicks,sl);}
                else if(r<360){float d=1+random.nextInt(8);assertEquals(old.guardHit(d,t),now.guardHit(d,t),"blow on the guard at "+t);}
                else if(r<363){int ticks=10+random.nextInt(40);old.breakGuard(t,ticks);now.breakGuard(t,ticks);}
                else if(r<366){int ticks=random.nextInt(60);assertEquals(old.seal(t,ticks),now.seal(t,ticks),"glitched at "+t);}
                assertEquals(state(old),state(now),"run "+run+" tick "+t);
                assertEquals(old.attacking(t),now.attacking(t));assertEquals(old.dashing(t),now.dashing(t));assertEquals(old.locked(t),now.locked(t));
            }
        }
    }
    @Test void oneTableSaysWhatMayBeDoneWhen(){
        var s=new CombatState();
        assertEquals(Actions.Phase.FREE,Actions.phase(s,0));
        for(var a:new Actions.Act[]{Actions.Act.ATTACK,Actions.Act.GUARD,Actions.Act.DASH})assertTrue(Actions.may(s,0,a),a+" when free");
        assertFalse(Actions.may(s,0,Actions.Act.EVADE),"nothing to get out of");
        s.requestAttack(10);assertEquals(Actions.Phase.ATTACKING,Actions.phase(s,11));
        assertFalse(Actions.may(s,11,Actions.Act.GUARD));assertFalse(Actions.may(s,11,Actions.Act.DASH));assertFalse(Actions.may(s,11,Actions.Act.ATTACK),"one at a time (a press near its end is kept)");
        s=new CombatState();s.guard(true,0);assertEquals(Actions.Phase.GUARDING,Actions.phase(s,1));
        assertFalse(Actions.may(s,1,Actions.Act.ATTACK));assertFalse(Actions.may(s,1,Actions.Act.DASH));assertTrue(Actions.may(s,1,Actions.Act.GUARD));
        s=new CombatState();s.seal(0,40);assertFalse(Actions.may(s,5,Actions.Act.GUARD),"glitched: no guard");assertTrue(Actions.may(s,5,Actions.Act.ATTACK));
        s=new CombatState();s.dash(0,1,0);assertEquals(Actions.Phase.DASHING,Actions.phase(s,2));
        assertFalse(Actions.may(s,CombatState.DASH_TICKS+1,Actions.Act.DASH),"its cooldown");assertTrue(Actions.may(s,CombatState.DASH_TICKS+CombatState.DASH_COOLDOWN,Actions.Act.DASH));
        s=new CombatState();s.stagger(0,10,.9);assertEquals(Actions.Phase.STAGGERED,Actions.phase(s,1));
        s=new CombatState();s.incapacitate(0,10,10,.5);assertEquals(Actions.Phase.INCAPACITATED,Actions.phase(s,1));
        s=new CombatState();s.breakGuard(0,10);assertEquals(Actions.Phase.GUARD_BROKEN,Actions.phase(s,1));
        for(var phase:Actions.Phase.values()){
            boolean held=phase==Actions.Phase.STAGGERED||phase==Actions.Phase.INCAPACITATED||phase==Actions.Phase.GUARD_BROKEN;
            assertEquals(held,Actions.held(phase),phase+": the evasive gets them out of it");
        }
        // held: nothing but the evasive (which their cooldown allows)
        s=new CombatState();s.stagger(0,20,.9);
        for(var a:new Actions.Act[]{Actions.Act.ATTACK,Actions.Act.GUARD,Actions.Act.DASH})assertFalse(Actions.may(s,3,a),a+" when held");
        assertTrue(Actions.may(s,3,Actions.Act.EVADE));
        s.evadeReady=50;assertFalse(Actions.may(s,3,Actions.Act.EVADE),"not before it is ready");
    }
    @Test void aGuardJustRaisedTakesABlowPerfectlyAtMostOnceASecond(){
        var s=new CombatState();
        s.guard(true,100);
        for(long t=100;t<100+Defense.PERFECT_WINDOW;t++)assertTrue(s.perfect(t),"within its window: "+t);
        assertFalse(s.perfect(100+Defense.PERFECT_WINDOW),"past it");
        s.guard(true,103);assertTrue(s.perfect(103),"holding it on keeps the window it opened");
        // dropped and raised again within the second: no new window; a second after the last, one again
        s.guard(false,108);s.guard(true,110);assertFalse(s.perfect(110),"tapping opens none");assertFalse(s.perfect(111));
        s.guard(false,115);s.guard(true,100+Defense.PERFECT_EVERY);
        assertTrue(s.perfect(100+Defense.PERFECT_EVERY),"a second later it opens again");
        // a perfect blow puts nothing on the guard and takes some off, and does not close the window for the next
        s.pressure=50;s.perfectHit(121);assertEquals(50-Defense.PERFECT_EASE,s.pressure,1e-6);assertTrue(s.perfect(122));
        s.pressure=5;s.perfectHit(122);assertEquals(0,s.pressure,1e-6,"not below nothing");
        // a boss raising its guard without meaning a perfect one opens none, and a broken guard takes none perfectly
        var b=new CombatState();b.guard(true,0,false);assertFalse(b.perfect(1));
        var c=new CombatState();c.guard(true,0);c.breakGuard(1,40);assertFalse(c.perfect(2));
        // a guard that cannot go up opens none: mid-strike
        var d=new CombatState();d.requestAttack(0);d.guard(true,1);assertFalse(d.guarding);assertFalse(d.perfect(1));assertEquals(-1000,d.perfectAt);
        assertTrue(Defense.parries(3.15));assertTrue(Defense.parries(Defense.PARRY_REACH));assertFalse(Defense.parries(8.5),"a sword from afar is not turned back");
        assertTrue(Defense.opens(-1000,0));assertFalse(Defense.opens(0,Defense.PERFECT_EVERY-1));assertTrue(Defense.opens(0,Defense.PERFECT_EVERY));
    }
    @Test void theEvasiveGetsThemOutOfWhatHoldsThemOnceInFifteenSeconds(){
        // a stagger, an incapacitation, a broken guard: each let go of
        for(int kind=0;kind<3;kind++){
            var s=new CombatState();
            if(kind==0)s.stagger(10,30,.9);else if(kind==1)s.incapacitate(10,60,60,.95);else{s.guard(true,0);s.breakGuard(10,40);}
            assertTrue(s.locked(11));
            assertTrue(s.evade(11,0,0),"out of it");
            assertFalse(s.locked(11),"let go");assertFalse(s.slowed(11),"its slow too");assertFalse(s.broken(11));
            assertEquals(-1,s.evadeForward,1e-6,"back if no way was pressed");
            assertTrue(s.evading(11));assertTrue(s.evading(11+Defense.EVADE_TICKS-1));assertFalse(s.evading(11+Defense.EVADE_TICKS));
            for(long t=11;t<11+Defense.EVADE_SAFE;t++)assertTrue(s.untouchable(t),"untouchable "+t);
            assertFalse(s.untouchable(11+Defense.EVADE_SAFE));assertFalse(s.untouchable(10),"not before it");
            assertEquals(Actions.Phase.EVADING,Actions.phase(s,12));
            assertFalse(s.requestAttack(13),"no attack mid-evasive");assertFalse(s.dash(13,1,0));s.guard(true,13);assertFalse(s.guarding);
            assertFalse(s.evade(13,1,0),"no second one");
            assertTrue(s.requestAttack(11+Defense.EVADE_TICKS),"free once it is over");
            long ready=11+Defense.EVADE_TICKS+Defense.EVADE_COOLDOWN;assertEquals(ready,s.evadeReady);
            s.stagger(ready-10,ready+20,.9);assertFalse(s.evade(ready-1,0,0),"not before its cooldown");assertTrue(s.evade(ready,1,0));
        }
        // nothing holds them: no evasive; a barrage's hold (the server's) is something
        var free=new CombatState();assertFalse(free.evade(5,1,0));assertTrue(free.evade(5,1,0,true),"a barrage holds them");
        assertEquals(1,free.evadeForward,1e-6);
        var side=new CombatState();side.stagger(0,20,.9);side.evade(1,1,1);
        assertEquals(1,Math.hypot(side.evadeForward,side.evadeSide),1e-6,"its way is a direction");
        // it carries them about as far as a dash, quicker
        double dash=0;for(int i=0;i<CombatState.DASH_TICKS;i++)dash+=CombatState.dashSpeed(i);
        assertTrue(Defense.evadeDistance()>4&&Defense.evadeDistance()<dash,"evasive "+Defense.evadeDistance()+" dash "+dash);
        assertEquals(0,Defense.evadeSpeed(-1));assertEquals(0,Defense.evadeSpeed(Defense.EVADE_TICKS));
        for(int i=1;i<Defense.EVADE_TICKS;i++)assertTrue(Defense.evadeSpeed(i)<=Defense.evadeSpeed(1),"fastest at the push off");
    }
    @Test void targetsAreWoundBackAsFarAsTheStrikerSawThem(){
        assertEquals(0,Rewind.offset(100,-1),"no time sent: as things stand");
        assertEquals(Rewind.INTERP,Rewind.offset(100,100),"no lag: the slide only");
        assertEquals(3+Rewind.INTERP,Rewind.offset(103,100),"a round trip of three ticks");
        assertEquals(Rewind.MAX,Rewind.offset(200,100),"at most MAX");
        assertEquals(0,Rewind.offset(100,110),"a client ahead of the server winds nothing back");
        assertTrue(Rewind.MAX<Rewind.KEEP,"what is kept covers the most wound back");
    }
    @Test void theShapesMeetWhatTheyShould(){
        var ray=Moves.BARE_RAY;Vec3d eye=new Vec3d(0,1.62,0),end=new Vec3d(0,1.62,3.15);
        assertEquals(2.5,Hitbox.along(ray,eye,end,new Box(-.3,0,2.64,.3,1.8,3.1)).orElse(-1),1e-6,"a body in reach, met at its face grown by the pad");
        assertTrue(Hitbox.along(ray,eye,end,new Box(.1,0,1.5,.7,1.8,2.1)).isPresent(),"grazed within the pad");
        assertFalse(Hitbox.along(ray,eye,end,new Box(.2,0,1.5,.8,1.8,2.1)).isPresent(),"just past it");
        assertFalse(Hitbox.along(ray,eye,end,new Box(-.3,0,3.6,.3,1.8,4.2)).isPresent(),"out of reach");
        var lane=new Hitbox.Lane(.2,4.2,1.15,-1.2,2.8);
        assertTrue(Hitbox.meets(lane,Vec3d.ZERO,0,new Box(-.3,0,3,.3,1.8,3.6)),"ahead (yaw 0 faces +z)");
        assertFalse(Hitbox.meets(lane,Vec3d.ZERO,0,new Box(-.3,0,-3.6,.3,1.8,-3)),"behind");
        assertTrue(Hitbox.meets(lane,Vec3d.ZERO,180,new Box(-.3,0,-3.6,.3,1.8,-3)),"turned round");
        assertFalse(Hitbox.meets(lane,Vec3d.ZERO,0,new Box(2,0,2,2.6,1.8,2.6)),"off to the side");
        assertFalse(Hitbox.meets(lane,Vec3d.ZERO,0,new Box(-.3,4,2,.3,5.8,2.6)),"overhead");
        var ball=new Hitbox.Sphere(2,1,1);
        assertTrue(Hitbox.meets(ball,Vec3d.ZERO,0,new Box(-.3,0,2.6,.3,1.8,3.2)));assertFalse(Hitbox.meets(ball,Vec3d.ZERO,0,new Box(-.3,0,4,.3,1.8,4.6)));
        assertEquals(1,Hitbox.steps(.1,.5).length);assertEquals(4,Hitbox.steps(2,.5).length);assertEquals(8,Hitbox.steps(99,.5).length);
        var t=Hitbox.steps(2,.5);assertEquals(1,t[t.length-1],1e-9,"the end of the tick last");
        for(int i=1;i<t.length;i++)assertEquals(.25,t[i]-t[i-1],1e-9,"evenly");
    }
    /**
     * The client predicts its player by the same combat state the server runs: its presses go out at moments of its
     * own clock and reach the server the same few ticks later each time, and the server starts the same strikes the
     * same ticks after them (so what the client showed at once is what the server does, as late as the lag).
     */
    @Test void thePredictionAgreesWithTheServerHoweverFarApartTheirClocks(){
        var random=new Random(2157);
        for(int lag=0;lag<=8;lag++)for(int run=0;run<40;run++){
            var client=new CombatState();var server=new CombatState();
            long[] presses=new long[60];long t=5;for(int i=0;i<presses.length;i++){t+=1+random.nextInt(9);presses[i]=t;}
            StringBuilder c=new StringBuilder(),s=new StringBuilder();int k=0,j=0;
            for(long now=0;now<t+60;now++){
                client.tick(now);if(k<presses.length&&presses[k]==now){client.requestAttack(now);k++;}
                server.tick(now);if(j<presses.length&&presses[j]+lag==now){server.requestAttack(now);j++;}
                if(client.attack>0&&client.attackStart==now)c.append(client.attack).append('@').append(now).append(' ');
                if(server.attack>0&&server.attackStart==now)s.append(server.attack).append('@').append(now-lag).append(' ');
            }
            assertFalse(c.isEmpty());
            assertEquals(c.toString(),s.toString(),"lag "+lag);
        }
    }
    @Test void oneTurnedBackRecoilsAndComesBackToItself(){
        MotionPose full=MotionClips.parried(4,10),stand=MotionClips.ready(4);
        assertTrue(full.joint[MotionPose.RIGHT_ARM][0]<stand.joint[MotionPose.RIGHT_ARM][0]-100&&full.joint[MotionPose.LEFT_ARM][0]<stand.joint[MotionPose.LEFT_ARM][0]-100,"the arms flung up");
        assertTrue(full.joint[MotionPose.BODY][0]<stand.joint[MotionPose.BODY][0]-15&&full.lean>stand.lean+.4f,"thrown back");
        float worst=0;MotionPose last=null;
        for(float t=0;t<=Defense.PARRY_TICKS;t+=1/3f){
            var p=MotionClips.parried(t,Defense.PARRY_TICKS-t);
            if(last!=null)for(int i=0;i<p.joint.length;i++)for(int a=0;a<3;a++)worst=Math.max(worst,Math.abs(p.joint[i][a]-last.joint[i][a]));
            last=p;
        }
        assertTrue(worst<70,"no joint jumps more than 70 degrees in a frame at 60 fps: "+worst);
        // out of the stand and back to it exactly
        for(float at:new float[]{0,14}){
            MotionPose ends=MotionClips.parried(at,at==0?Defense.PARRY_TICKS:0),rest=MotionClips.ready(at);
            for(int i=0;i<rest.joint.length;i++)for(int a=0;a<3;a++)assertEquals(rest.joint[i][a],ends.joint[i][a],1e-4,"at "+at);
            for(int i=0;i<2;i++)assertEquals(rest.elbow[i],ends.elbow[i],1e-4);
            assertEquals(rest.lean,ends.lean,1e-4);
        }
    }
}
