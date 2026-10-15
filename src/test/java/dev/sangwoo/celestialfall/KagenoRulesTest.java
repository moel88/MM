package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;
final class KagenoRulesTest {
    @Test void referenceDamageAndHitCounts(){
        int[] skills={E,R,T,RUSH,Y,H},hits={8,5,13,6,2,12};float[] totals={26,21,51,50,16,32};
        for(int i=0;i<skills.length;i++){float total=0;int count=0;for(int a=0;a<DURATION[skills[i]];a++){float d=damage(skills[i],a,0);total+=d;if(d>0)count++;}assertEquals(totals[i],total,.001);assertEquals(hits[i],count);}
        float total=0;for(int combo=1;combo<=4;combo++)for(int a=0;a<14;a++)total+=damage(M1,a,combo);assertEquals(28,total);
    }
    @Test void affectedRadiiIncludeBoundaryButRejectOutsideAndNan(){assertTrue(inside(9,STOP_RADIUS));assertFalse(inside(9.00001,STOP_RADIUS));assertTrue(inside(25,HIGHEST_RADIUS));assertFalse(inside(25.00001,HIGHEST_RADIUS));assertFalse(inside(Double.NaN,5));}
    @Test void freezeEndsBeforeDamageAndAlwaysReleases(){assertFalse(frozen(R,7.99f));assertTrue(frozen(R,8));assertTrue(frozen(R,35.99f));assertFalse(frozen(R,36));assertEquals(0,damage(R,35,0));assertTrue(damage(R,36,0)>0);assertTrue(frozen(H,115));assertFalse(frozen(H,116));}
    @Test void groundFireReleasesCasterWhileHazardContinues(){assertEquals(28,inputEnd(T));assertTrue(damage(T,78,0)>0);assertEquals(15,damage(T,6,0));assertEquals(8,HOLD);assertEquals(COOLDOWN[T],COOLDOWN[RUSH]);}
    @Test void overdriveNightAndMarkHaveDistinctMultipliers(){assertEquals(1,KagenoRules.multiplier(false,0,false));assertEquals(1.35,KagenoRules.multiplier(true,0,false));assertEquals(1.05*1.15,KagenoRules.multiplier(false,.05f,true),1e-6);}
    @Test void ownRHonorsUserOverrideToTimeStopImmunity(){assertFalse(ignoresForeignTimeStop("kageno"));assertFalse(ignoresForeignTimeStop("sakuya"));assertFalse(ignoresForeignTimeStop("return_to_zero"));assertTrue(ignoresForeignTimeStop("dio"));}
}
