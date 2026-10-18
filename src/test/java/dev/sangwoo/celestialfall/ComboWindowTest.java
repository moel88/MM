package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.combat.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ComboWindowTest {
    @Test void bufferedFourHitChainHasNoDefenderActionGap() {
        var state=new CombatState();state.requestAttack(0);ComboWindow window=null;int hits=0;
        for(int t=0;t<=40;t++) {
            state.tick(t);if(t%4==0)state.requestAttack(t);
            if(t>=3&&window!=null)assertTrue(window.active(state,t),"Escape at tick "+t);
            if(state.hitDue(t)) {
                hits++;if(hits>1)assertTrue(window.nextContact(state,t));
                if(state.attack<4)window=ComboWindow.landed(state,t);
            }
        }
        assertEquals(4,hits);
    }
    @Test void tenthOfSecondCannotBridgeTheExistingContactIntervals() {
        assertArrayEquals(new int[]{11,12,14},new int[]{CombatState.DURATION[1]-CombatState.WINDUP[1]+CombatState.WINDUP[2],CombatState.DURATION[2]-CombatState.WINDUP[2]+CombatState.WINDUP[3],CombatState.DURATION[3]-CombatState.WINDUP[3]+CombatState.WINDUP[4]});
    }
    @Test void releasingTheComboFreesTargetAtRecoveryBoundary() {
        var s=new CombatState();s.requestAttack(0);var w=ComboWindow.landed(s,3);
        assertTrue(w.active(s,9));assertFalse(w.active(s,10));s.tick(10);assertFalse(w.active(s,10));
    }
    @Test void InterruptedOrRestartedSequenceCannotKeepTheTargetLocked() {
        var s=new CombatState();s.requestAttack(0);var w=ComboWindow.landed(s,3);s.cancel();assertFalse(w.active(s,4));
        s.requestAttack(7);assertFalse(w.active(s,8));
    }
    @Test void FourthHitHasNoCaptureAndEveryCaptureHasABoundedDeadline() {
        var s=new CombatState();s.attack=3;s.attackStart=21;s.serial=3;var w=ComboWindow.landed(s,26);
        s.attack=4;s.attackStart=34;s.serial=4;assertTrue(w.active(s,40));assertFalse(w.active(s,42));
        assertThrows(IllegalArgumentException.class,()->ComboWindow.landed(s,40));
    }
    @Test void HitReactionsKeepFiniteStandingJointTransforms() {
        for(int strike=1;strike<=3;strike++)for(float t=0;t<18;t+=.1f){var p=MotionClips.hitReaction(t,strike);for(float[] joint:p.joint)for(float v:joint)assertTrue(Float.isFinite(v));assertTrue(Math.abs(p.bob)<1);}
    }
}
