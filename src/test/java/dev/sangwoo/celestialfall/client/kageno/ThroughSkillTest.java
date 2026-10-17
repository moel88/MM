package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** T held: the beams and the spin cut line up with the server's hits, and the spin cut is slashes, not a dome or disc. */
class ThroughSkillTest {
    @Test void eachBeamLeavesJustBeforeOneOfTheServersPreDashHits(){
        int[] hits={KagenoRules.T_STRIKE+7,KagenoRules.T_STRIKE+13,KagenoRules.T_STRIKE+18};
        for(int i=0;i<hits.length;i++){
            assertTrue(KagenoRules.damage(KagenoRules.RUSH,hits[i],0)>0);
            assertEquals(hits[i]/20f-.05f,ThroughSkill.BEAMS[i],1e-6);
        }
    }
    @Test void theSpinCutIsBladesRoundTheBodyNeverAFilledDisc(){
        for(double[] s:ThroughSkill.SLASHES){
            assertTrue(s[5]<=240,"a blade, not a full ring: "+s[5]+" degrees");
            for(float u=0;u<=1;u+=.05f){
                double outer=s[2]+(s[3]-s[2])*u,width=Math.min(s[4]*(.85+.3*u),outer*.6);
                assertTrue(outer-width>=outer*.4-1e-9,"the body stays clear inside the blade");
            }
        }
    }
    @Test void theWidestSlashIsRoundTheBodyWhenTheServersFinishHitLands(){
        float hit=KagenoRules.RUSH_FINISH/20f;
        assertTrue(KagenoRules.damage(KagenoRules.RUSH,KagenoRules.RUSH_FINISH,0)>0);
        assertTrue(ThroughSkill.reach(hit)>0,"a slash is out when the finish hit lands");
        assertEquals(0,ThroughSkill.reach(ThroughSkill.SWELL-.01f),1e-9,"none before the cut");
    }
}
