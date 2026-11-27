package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gold Experience's muda rush (2.147.0, user request: "러시 속도는 스타플래티나보다 미세하게 느리게 데미지도 조금 더
 * 약하게"): its blows a little slower than Star Platinum's rush's, what it deals a little less, and as long as the
 * recording of its voice.
 */
class GoldExperienceRulesTest {
    @Test void itsBlowsFallALittleSlowerThanStarPlatinums(){
        assertEquals(StarPlatinumRules.RUSH_FIRST,RUSH_FIRST,"its first blow as soon as it has come round");
        int blows=0;long last=-1;
        for(long age=0;age<=RUSH_LENGTH+10;age++){
            if(!rushBlow(age)){assertEquals(blows,rushBlows(age));continue;}
            assertEquals(rushBlowAt(blows),age,"blow "+blows);
            if(last>=0){long gap=age-last;assertTrue(gap==2||gap==3,"blows "+gap+" ticks apart");}
            last=age;blows++;
            assertEquals(blows,rushBlows(age));
        }
        assertEquals(RUSH_HITS,blows);assertEquals(RUSH_LAST,last);assertEquals(RUSH_LAST+3,RUSH_LENGTH);
        // 2.2 ticks from one to the next on the whole (Star Platinum's: 2): about nine a second to its ten
        double step=(RUSH_LAST-RUSH_FIRST)/(double)(RUSH_HITS-1);
        assertEquals(2.2,step,.03);assertTrue(step>StarPlatinumRules.RUSH_EVERY&&step<StarPlatinumRules.RUSH_EVERY*1.2,"a little slower, not much");
        // never ahead of Star Platinum's rush, and behind it before long
        for(long age=0;age<=RUSH_LENGTH;age++)assertTrue(rushBlows(age)<=StarPlatinumRules.rushBlows(age),"at "+age);
        assertTrue(rushBlows(40)<StarPlatinumRules.rushBlows(40));
    }
    @Test void itDealsALittleLess(){
        assertTrue(RUSH_DAMAGE<=StarPlatinumRules.RUSH_DAMAGE,"no blow harder than Star Platinum's");
        assertEquals(RUSH_HITS*RUSH_DAMAGE,RUSH_TOTAL,1e-6);
        assertTrue(RUSH_TOTAL<StarPlatinumRules.RUSH_TOTAL&&RUSH_TOTAL>=StarPlatinumRules.RUSH_TOTAL*.7,"a little weaker: "+RUSH_TOTAL);
        // a second of it deals a little less too
        double each=RUSH_DAMAGE*20/2.2,theirs=StarPlatinumRules.RUSH_DAMAGE*20.0/StarPlatinumRules.RUSH_EVERY;
        assertTrue(each<theirs&&each>theirs*.85,each+" a second to "+theirs);
        assertEquals(500+RUSH_COOLDOWN,rushReady(500),"its cooldown from its end");
        assertEquals(StarPlatinumRules.RUSH_COOLDOWN,RUSH_COOLDOWN);
        // a blow holds whom it strikes past the next
        assertTrue(RUSH_HOLD>3);
    }
    @Test void itIsAsLongAsTheRecordingOfItsVoice()throws Exception{
        // the sound starts a tick in (StandRush.LEAD) and has died away as the rush ends
        double sound=StarPlatinumRulesTest.seconds("gold_experience_muda");
        assertEquals(RUSH_LENGTH/20.0,sound+dev.sangwoo.celestialfall.client.stand.StandRush.LEAD/20.0,.12,"the recording: "+sound+" s");
        assertTrue(RUSH_LENGTH<StarPlatinumRules.RUSH_LENGTH,"shorter than Star Platinum's rush: the recording is");
    }
    @Test void theOverdrivePunchIsOneGuardableBlowThatLeavesABodySoullessForFiveSeconds(){
        // the user's numbers: damage 10, cooldown 40 s, out of the body 5 s ("10초말고 5초로"), harm to it doubled, the soul twice as fast
        assertEquals(10,OVER_DAMAGE,1e-6);
        assertEquals(40*20,OVER_COOLDOWN);
        assertEquals(5*20,OVER_LASTS);
        assertEquals(2,OVER_TAKEN,1e-6);
        assertEquals(2,SOUL_SPEED,1e-6);
        assertEquals(20,overHarm(10,true),1e-6);assertEquals(10,overHarm(10,false),1e-6);
        // a wind-up one can see and guard: Star Platinum's heavy punch's
        assertEquals(StarPlatinumRules.CONTACT[StarPlatinumRules.HEAVY],OVER_CONTACT);
        assertEquals(StarPlatinumRules.LENGTH[StarPlatinumRules.HEAVY],OVER_LENGTH);
        // its cooldown runs once it is over
        assertEquals(1000+800,overReady(1000));
        // the finisher's lane
        assertTrue(overLane(2,0,0,.3));assertTrue(overLane(4.4,0,0,.3));
        assertFalse(overLane(4.6,0,0,.3));assertFalse(overLane(2,1.6,0,.3));assertFalse(overLane(-.4,0,0,.3));
        // it shoves them back a little: well under Star Platinum's finisher's throw
        assertTrue(OVER_SHOVE>0&&OVER_SHOVE<StarPlatinumRules.FLY/2&&OVER_SHOVE_UP<StarPlatinumRules.FLY_UP);
    }
    @Test void yHealsAnotherAtATouchOrItsUserBlowByBlowOnOneCooldown(){
        // tapped: one touch of 60, as quick as a blow
        assertEquals(60,MEND,1e-6);assertEquals(6,MEND_CONTACT);assertTrue(MEND_LENGTH>MEND_CONTACT);
        // held: ten a second for five seconds
        assertEquals(50,SELF_TOTAL,1e-6);assertEquals(50,SELF_BLOWS);
        assertEquals(10,20f/SELF_EVERY*SELF_EACH,1e-6,"ten a second");
        assertEquals(100,SELF_EVERY*SELF_BLOWS,"over five seconds");
        int blows=0;for(long age=0;age<=SELF_LENGTH+20;age++){if(selfBlow(age))blows++;assertEquals(blows,selfBlows(age),"blows by "+age);}
        assertEquals(SELF_BLOWS,blows);
        assertFalse(selfBlow(SELF_FIRST-1));assertTrue(selfBlow(SELF_FIRST));assertFalse(selfBlow(SELF_FIRST+1));assertTrue(selfBlow(SELF_LAST));assertFalse(selfBlow(SELF_LAST+SELF_EVERY));
        // let go at any moment it has healed what has landed by then
        assertEquals(0,selfBlows(SELF_FIRST-1));assertEquals(1,selfBlows(SELF_FIRST));assertEquals(10,selfBlows(SELF_FIRST+SELF_EVERY*9+1));
        // one cooldown of 45 s for both, from the end
        assertEquals(900,HEAL_COOLDOWN);assertEquals(1000+900,healReady(1000));
        // held: its user walks at a tenth of their speed (the rush's)
        assertEquals(StarPlatinumRules.RUSH_SLOW,SELF_SLOW,1e-9);
        // the threshold between the two is Lee So-sang's R's
        assertEquals(dev.sangwoo.celestialfall.isosang.IsosangRules.R_HOLD_TICKS,HEAL_HOLD);
        // the hand reaches whom the overdrive punch would
        assertTrue(mendLane(2,0,0,.3));assertFalse(mendLane(6,0,0,.3));assertFalse(mendLane(2,3,0,.3));
    }
}
