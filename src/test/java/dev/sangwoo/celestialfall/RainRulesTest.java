package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.RainRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the rain of knives (2.132.0; the user's: a run, a stab at whoever is ahead, the knife
 * kept in them while a great many knives lit as the hack lights them pour in from every side; "합 70 · 60초", "가드 불가",
 * the one stabbed "그 자리에 묶임"): its two skills of the knife's pipeline, its times, its run and its numbers.
 */
class RainRulesTest {
    @Test void itIsTwoSkillsOfTheKnifesPipelineTheRunWithNoBlow(){
        assertEquals(16,RUSH);assertEquals(17,RAIN);assertEquals(RAIN,LAST);
        assertTrue(valid(RUSH)&&valid(RAIN)&&!valid(RAIN+1));
        assertTrue(skill(RUSH)&&skill(RAIN)&&rain(RUSH)&&rain(RAIN)&&!rain(KNIVES)&&!rain(CUTTER_DASH));
        assertEquals(0,blows(RUSH),"the run strikes no one itself");
        assertEquals(1+RainRules.VOLLEYS,blows(RAIN));
        assertEquals(RainRules.STAB,blowAt(RAIN,0));
        for(int v=0;v<RainRules.VOLLEYS;v++)assertEquals(RainRules.FIRST+v,blowAt(RAIN,1+v),"a volley a tick");
        assertEquals(RainRules.LAST,lastBlow(RAIN));
        assertEquals(RainRules.RUSH_HOLD,HOLD[RUSH]);assertEquals(RainRules.RUSH_LENGTH,LENGTH[RUSH]);
        assertEquals(RainRules.HOLD,HOLD[RAIN]);assertEquals(RainRules.LENGTH,LENGTH[RAIN]);
        assertFalse(place(RUSH)>0||place(RAIN)>0||last(RAIN),"no hit of the combo");
    }
    @Test void seventyInAllSixtySecondsNoGuardTheOneStabbedBound(){
        assertEquals(70,total(RAIN),1e-6,"the user's 70");assertEquals(70,RainRules.total(),1e-6);
        assertEquals(RainRules.STAB_DAMAGE,damage(RAIN,0));assertEquals(RainRules.VOLLEY_DAMAGE,damage(RAIN,7));
        assertEquals(1200,RainRules.COOLDOWN,"60 s");assertEquals(1200,cooldown(RAIN));assertEquals(1200,cooldown(RUSH));
        assertTrue(unblockable(RAIN),"no guard takes it");
        assertTrue(single(RAIN),"the stab is at one");
        assertFalse(launches(RAIN,0)||launches(RAIN,RainRules.VOLLEYS),"no one is thrown");
        // bound from the stab until the last volley has landed
        assertTrue(RainRules.STAB+RainRules.BIND>RainRules.LAST);assertEquals(1,RainRules.BIND_SLOW,"their legs stopped wholly");
        assertEquals(1000+RainRules.LENGTH+1200,RainRules.ready(1000));assertEquals(1000+RainRules.RUSH_LENGTH+1200,RainRules.readyAfterRun(1000));
    }
    @Test void theKnifeStaysInUntilTheLastVolleyHasLanded(){
        assertTrue(RainRules.ARRIVE<RainRules.STAB,"there before the knife goes in");
        assertTrue(RainRules.STAB<RainRules.FIRST&&RainRules.FIRST<RainRules.LAST);
        assertTrue(RainRules.WITHDRAW>RainRules.LAST,"the knife is in until the last volley is");
        assertTrue(RainRules.HOLD>RainRules.WITHDRAW&&RainRules.LENGTH>RainRules.HOLD);
        assertTrue(RainRules.STAB+RainRules.MISS<RainRules.FIRST,"a stab that met no one is over before a volley would land");
        assertEquals(6480,RainRules.BLADES,"a great many: 216 a tick for a second and a half (2.132.4: one on them, the rest over the ground 30 blocks round)");
        for(int k=0;k<RainRules.BLADES;k++){
            int v=RainRules.volleyOf(k);assertTrue(v>=0&&v<RainRules.VOLLEYS);
            assertTrue(Math.abs(RainRules.landsAt(k)-RainRules.volleyAt(v))<.5,"each lands with its volley");
            if(k>0)assertTrue(RainRules.landsAt(k)>RainRules.landsAt(k-1),"one after another");
        }
    }
    @Test void theRunSpringsOffRunsAndSlowsInWhereItEnds(){
        assertEquals(0,RainRules.ran(0,8),1e-9);assertEquals(0,RainRules.ran(RainRules.RUN_FROM,8),1e-9);
        for(double far:new double[]{8,5.3,1.5,.6,0}){
            double last=0;
            for(double t=0;t<=40;t+=.05){
                double d=RainRules.ran(t,far);
                assertTrue(d>=last-1e-9,"never back");assertTrue(d<=far+1e-9,"never past where it ends");
                assertTrue(d-last<=RainRules.SPEED*.05+1e-6,"no faster than its speed: no jump");
                last=d;
            }
            if(far>0)assertTrue(far-RainRules.ran(RainRules.RUN_TO,far)<.4,"nearly there as the server takes them there: "+(far-RainRules.ran(RainRules.RUN_TO,far)));
            assertEquals(far,RainRules.ran(40,far),.01);
        }
        // at speed in between: 16 blocks a second
        assertEquals(RainRules.SPEED,RainRules.ran(7,8)-RainRules.ran(6,8),1e-6);
        assertEquals(8,RainRules.RUN);
    }
    @Test void caughtTheyGoOnToTheKnifesLengthShortOfThem(){
        assertEquals(RainRules.CATCH-RainRules.KEEP,RainRules.LUNGE,1e-9);
        assertEquals(0,RainRules.advance(RainRules.KEEP-.5,5),"nearer than the knife is long: where they are");
        assertEquals(1,RainRules.advance(RainRules.KEEP+1,5),1e-9);
        assertEquals(RainRules.LUNGE,RainRules.advance(RainRules.CATCH,5),1e-9);
        assertEquals(.3,RainRules.advance(RainRules.CATCH,.3),1e-9,"no further than the way is open");
        assertEquals(0,RainRules.lunged(0));assertEquals(1,RainRules.lunged(RainRules.ARRIVE));assertEquals(1,RainRules.lunged(30));
        double last=0;for(double t=0;t<=RainRules.ARRIVE;t+=.1){double g=RainRules.lunged(t);assertTrue(g>=last);last=g;}
        // the stab reaches the one they stopped short of
        assertTrue(lane(RAIN,0,RainRules.KEEP+.3,0,0,.3));assertFalse(lane(RAIN,0,RainRules.REACH[1]+1,0,0,.3));assertFalse(lane(RAIN,0,2,RainRules.REACH[2]+.7,0,.3));
    }
}
