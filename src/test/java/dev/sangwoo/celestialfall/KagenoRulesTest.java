package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;
final class KagenoRulesTest {
    @Test void referenceDamageAndHitCounts(){
        int[] skills={E,R,T,RUSH,Y,H},hits={8,5,13,6,2,12};float[] totals={26,21,51,50,16,32};
        for(int i=0;i<skills.length;i++){float total=0;int count=0;for(int a=0;a<DURATION[skills[i]];a++){float d=damage(skills[i],a,0);total+=d;if(d>0)count+=KagenoRules.hits(skills[i],a);}assertEquals(totals[i],total,.001);assertEquals(hits[i],count);}
        float total=0;for(int combo=1;combo<=4;combo++)for(int a=0;a<14;a++)total+=damage(M1,a,combo);assertEquals(28,total);
    }
    @Test void affectedRadiiIncludeBoundaryButRejectOutsideAndNan(){assertTrue(inside(9,STOP_RADIUS));assertFalse(inside(9.00001,STOP_RADIUS));assertTrue(inside(25,HIGHEST_RADIUS));assertFalse(inside(25.00001,HIGHEST_RADIUS));assertFalse(inside(Double.NaN,5));}
    @Test void freezeEndsBeforeDamageAndAlwaysReleases(){assertFalse(frozen(R,R_GRAY-.01f));assertTrue(frozen(R,R_GRAY));assertTrue(frozen(R,R_COLOR-.01f));assertFalse(frozen(R,R_COLOR));for(int a=0;a<R_COLOR;a++)assertEquals(0,damage(R,a,0));assertEquals(4,hits(R,R_STORED));assertTrue(damage(R,R_RELEASE,0)>0);assertTrue(frozen(H,115));assertFalse(frozen(H,116));}
    @Test void groundFireReleasesCasterWhileHazardContinues(){assertEquals(15,inputEnd(T));assertEquals(15,damage(T,T_STRIKE,0));assertTrue(damage(T,T_STRIKE+64,0)>0);assertEquals(0,damage(T,T_STRIKE+65,0));assertEquals(8,HOLD);assertEquals(COOLDOWN[T],COOLDOWN[RUSH]);}
    @Test void throughTheDarkDashesAfterTheBlastAndCutsAtTheEnd(){
        double moved=0;for(int a=0;a<DURATION[RUSH];a++)moved+=rushSpeed(a);
        assertEquals(32,moved,1e-9);assertEquals(0,rushSpeed(RUSH_START-1));assertTrue(rushSpeed(RUSH_START)>rushSpeed(RUSH_START+5));
        assertEquals(13,damage(RUSH,RUSH_START,0));assertEquals(18,damage(RUSH,RUSH_FINISH,0));
        assertTrue(trapped(T,T_STRIKE)&&!trapped(T,T_STRIKE+71)&&trapped(RUSH,RUSH_START)&&!trapped(RUSH,RUSH_FINISH));
    }
    @Test void fallenIntoTheDarkStompsFlashesHoldsThenLaunchesTwoCharacterHeights(){
        assertEquals(2,damage(Y,Y_STOMP,0));assertEquals(14,damage(Y,Y_FLASH,0));assertTrue(inputEnd(Y)<=34);
        assertFalse(trapped(Y,Y_FLASH-1));assertTrue(trapped(Y,Y_FLASH));assertTrue(trapped(Y,Y_LAUNCH-1));assertFalse(trapped(Y,Y_LAUNCH));
        // Scripted rise: apex ≈3.8 blocks (2.1 character heights) after ≈0.32 s, ≈0.65 s in the air.
        double y=0,apex=0;int up=0,air=0;for(int k=0;k<40;k++){y+=yLift(k);if(y>apex){apex=y;up=k;}if(y<=0)break;air=k+1;}
        assertEquals(3.8,apex,.25);assertTrue(up>=5&&up<=7,"rise ticks "+up);assertTrue(air>=12&&air<=14,"air ticks "+air);
    }
    @Test void overdriveNightAndMarkHaveDistinctMultipliers(){assertEquals(1,KagenoRules.multiplier(false,0,false));assertEquals(1.35,KagenoRules.multiplier(true,0,false));assertEquals(1.05*1.15,KagenoRules.multiplier(false,.05f,true),1e-6);}
    @Test void poseLetsGoAtTheReleaseWhileItsEffectsFinish(){
        assertEquals(18.683*20,P_RELEASE,.5);assertEquals(P_RELEASE,inputEnd(P));
        assertTrue(DURATION[P]>=P_RELEASE+.75*20);assertEquals(0,damage(P,P_RELEASE,0));
    }
    @Test void ownRHonorsUserOverrideToTimeStopImmunity(){assertFalse(ignoresForeignTimeStop("kageno"));assertFalse(ignoresForeignTimeStop("sakuya"));assertFalse(ignoresForeignTimeStop("return_to_zero"));assertTrue(ignoresForeignTimeStop("dio"));}
}
