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
        assertFalse(s.dash(7,1,0));assertFalse(s.requestAttack(3));assertTrue(s.dash(24,0,0));assertEquals(1,s.dashForward);
        s.requestAttack(32);assertFalse(s.dash(40,1,0));
        double distance=0;for(int t=0;t<CombatState.DASH_TICKS;t++)distance+=CombatState.dashSpeed(t);
        assertTrue(distance>4&&distance<6);assertEquals(0,CombatState.dashSpeed(7));
    }
}
