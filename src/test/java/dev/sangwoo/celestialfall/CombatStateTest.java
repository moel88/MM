package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.combat.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatStateTest {
    @Test void fourHitsRequireWindupAndCannotSkipOrRepeatContactFrames() {
        CombatState s=new CombatState();assertTrue(s.requestAttack(100));int count=0,highest=0;
        for(long now=100;now<190;now++) {
            s.tick(now);
            if(now%4==0&&now<145)s.requestAttack(now);
            if(s.hitDue(now)) {count++;highest=s.attack;assertFalse(s.hitDue(now));}
        }
        assertEquals(4,count);assertEquals(4,highest);assertEquals(0,s.attack);
    }
    @Test void earlyMashingDoesNotQueueAnUnwantedSecondStrike() {
        CombatState s=new CombatState();s.requestAttack(20);assertFalse(s.requestAttack(21));assertFalse(s.requestAttack(23));
        s.hitDue(23);s.tick(30);assertEquals(0,s.attack);assertEquals(1,s.combo);
    }
    @Test void bufferedPunchStartsAtRecoveryBoundaryAndPauseResetsCombo() {
        CombatState s=new CombatState();s.requestAttack(10);s.requestAttack(15);assertTrue(s.queued);
        s.tick(19);assertEquals(1,s.attack);s.tick(20);assertEquals(2,s.attack);assertEquals(20,s.attackStart);
        s.tick(100);s.requestAttack(100);assertEquals(1,s.attack);
    }
    @Test void guardBreakLocksEveryActionForExactlyTwentyTicks() {
        CombatState s=new CombatState();assertTrue(s.guard(true,50));
        assertFalse(s.guardHit(4,50));assertFalse(s.guardHit(4,51));assertTrue(s.guardHit(4,52));
        assertEquals(100,s.pressure);assertFalse(s.guarding);
        for(int t=52;t<72;t++){assertTrue(s.broken(t));assertFalse(s.dash(t,1,0));assertFalse(s.requestAttack(t));s.guard(true,t);assertFalse(s.guarding);}
        assertFalse(s.broken(72));assertTrue(s.guard(true,72));
    }
    @Test void basicComboFillsExactlyOneQuarterPerHitAndBreaksOnlyOnFourth() {
        CombatState s=new CombatState();s.guard(true,0);
        int[] times={3,14,26,40};
        for(int i=0;i<4;i++) {
            for(int tick=i==0?0:times[i-1]+1;tick<=times[i];tick++){s.guard(true,tick);s.tick(tick);}
            assertEquals(i==3,s.guardHit(CombatState.DAMAGE[i+1],times[i],true));
            assertEquals((i+1)*25,s.pressure);assertEquals(i<3,s.guarding);
        }
        assertTrue(s.broken(59));assertFalse(s.broken(60));
    }
    @Test void releaseAndMissingHeartbeatStopGuardAndPressureRecoversAfterDelay() {
        CombatState s=new CombatState();s.guard(true,0);s.guardHit(3,0);s.tick(16);assertTrue(s.guarding);
        s.tick(17);assertFalse(s.guarding);float pressure=s.pressure;s.tick(29);assertEquals(pressure,s.pressure);
        s.tick(30);assertTrue(s.pressure<pressure);s.guard(true,31);s.guard(false,32);assertFalse(s.guarding);
    }
    @Test void diagonalDashIsNormalizedHasCooldownAndDoesNotCancelAnAttack() {
        CombatState s=new CombatState();assertTrue(s.dash(0,1,1));assertEquals(1,Math.hypot(s.dashForward,s.dashSide),1e-6);
        // its cooldown runs once the dash is over (user request, 2.44.0)
        assertFalse(s.dash(7,1,0));assertFalse(s.requestAttack(3));assertFalse(s.dash(30,0,0));assertTrue(s.dash(31,0,0));assertEquals(1,s.dashForward);
        assertTrue(s.requestAttack(60));assertFalse(s.dash(65,1,0));
        double distance=0;for(int t=0;t<CombatState.DASH_TICKS;t++)distance+=CombatState.dashSpeed(t);
        assertTrue(distance>4&&distance<6);assertEquals(0,CombatState.dashSpeed(7));
    }
    /** User request (2.44.0): a dash's cooldown runs once the dash is over, from where it stopped when something cut it short. */
    @Test void theDashCooldownRunsOnceTheDashIsOver() {
        CombatState s=new CombatState();assertTrue(s.dash(0,1,0));
        assertEquals(CombatState.DASH_TICKS+CombatState.DASH_COOLDOWN,s.dashReady,"a whole dash, then its cooldown");
        s=new CombatState();assertTrue(s.dash(0,1,0));s.stopDash(3);
        assertFalse(s.dashing(4));assertEquals(3+CombatState.DASH_COOLDOWN,s.dashReady,"stopped at 3 by a wall: the cooldown runs from there");
        assertFalse(s.dash(2+CombatState.DASH_COOLDOWN,1,0));assertTrue(s.dash(3+CombatState.DASH_COOLDOWN,1,0));
        long ready=s.dashReady;s.cancel(100);assertEquals(ready,s.dashReady,"a blow once the dash is over changes nothing");
        s=new CombatState();assertTrue(s.dash(0,1,0));s.cancel(5);assertEquals(5+CombatState.DASH_COOLDOWN,s.dashReady,"a blow mid-dash ends it there");
    }
    /**
     * User request (2.60.0): struck by Star Platinum's Star Finger, "2초 95% 슬로우 + 3초 동안 대쉬 평타 포함 아무 스킬도 사용
     * 못함(무력화)": three seconds of no attack, guard or dash (and no skill: CombatManager.busy), the legs 95% slower
     * for the first two; whatever was going on is cut off.
     */
    @Test void anIncapacitatedOneCanOnlyWalk() {
        CombatState s=new CombatState();assertTrue(s.requestAttack(100));assertTrue(s.attacking(101));
        s.incapacitate(102,60,40,.95);
        assertEquals(0,s.attack,"the attack it was throwing is cut off");assertFalse(s.attacking(103));
        for(long t=102;t<162;t++){
            assertTrue(s.disabled(t)&&s.locked(t),"incapacitated at "+t);
            assertFalse(s.requestAttack(t),"no attack");assertFalse(s.dash(t,1,0),"no dash");
            s.guard(true,t);assertFalse(s.guarding,"no guard");
            assertEquals(t<142,s.slowed(t),"slowed for the first two seconds only");
        }
        assertEquals(.95,s.slow,1e-9);
        assertFalse(s.locked(162));assertTrue(s.dash(162,1,0),"free again after three seconds");
        assertEquals(102,s.disabledAt);
    }
    @Test void aLongerIncapacitationOrSlowStays() {
        CombatState s=new CombatState();s.incapacitate(0,60,40,.95);s.incapacitate(10,20,10,.5);
        assertEquals(60,s.disabledUntil,"the longer one stays");assertEquals(40,s.slowUntil);assertEquals(.95,s.slow,1e-9,"and the stronger slow");
        assertEquals(0,s.disabledAt,"still the first");
    }
    /** User request (2.60.0): "그냥 가드 브레이커 개념이 2초 무력화로 알고 있으면 됨": a broken guard is an incapacitation, timed from when it broke. */
    @Test void aBrokenGuardIsAnIncapacitation() {
        CombatState s=new CombatState();assertTrue(s.guard(true,10));s.breakGuard(20,40);
        assertEquals(20,s.brokenAt);
        for(long t=20;t<60;t++){assertTrue(s.locked(t));assertFalse(s.disabled(t),"not the Star Finger's");assertFalse(s.requestAttack(t));assertFalse(s.dash(t,1,0));}
        assertFalse(s.locked(60));
        CombatState w=new CombatState();w.guard(true,0);for(int i=0;i<10&&!w.broken(i);i++)w.guardHit(10,i);
        assertTrue(w.broken(w.brokenAt));assertEquals(w.brokenAt+CombatState.BREAK_TICKS,w.brokenUntil,"one worn down too, from when it broke");
    }
}
