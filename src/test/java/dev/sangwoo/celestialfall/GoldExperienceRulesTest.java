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
    @Test void theBeatdownIsTheVideosSevenPagesWeakerThanStarPlatinumsOnThreeMinutes()throws Exception{
        // its punch is to be seen coming, as Star Platinum's beatdown's blow (0.8 s)
        assertEquals(StarPlatinumRules.BEAT_CUT,BEAT_CUT);assertTrue(BEAT_OPENER_LENGTH>BEAT_CUT);
        // the video's moments, its first shot (0.633 s) left out: the WRYYY at 15.28 s, the last blow at 29.58 s
        assertEquals(BEAT_CUT+293,BEAT_WRY);assertEquals(BEAT_CUT+391,BEAT_WRY_END);assertEquals(BEAT_CUT+553,BEAT_WIND);assertEquals(BEAT_CUT+579,BEAT_FINAL);
        assertEquals(15.28-.633,(BEAT_WRY-BEAT_CUT)/20.0,.03);assertEquals(29.58-.633,(BEAT_FINAL-BEAT_CUT)/20.0,.03);
        // the rush all the way from the punch until it draws back for the last
        assertEquals(BEAT_CUT+2,BEAT_RUSH_FIRST);assertTrue(BEAT_RUSH_LAST<BEAT_WIND&&BEAT_RUSH_LAST>=BEAT_WIND-2);
        int blows=0;float dealt=0;
        for(long age=0;age<=BEAT_END;age++){float s=beatShare(age);if(s>0){blows++;dealt+=s;}}
        assertEquals(BEAT_RUSH_HITS+1,blows,"the rush's blows and the last");
        assertEquals(1,dealt,1e-3,"between them all of its damage");
        assertEquals(BEAT_FINAL_SHARE,beatShare(BEAT_FINAL),1e-6);assertEquals(0,beatShare(BEAT_FINAL+1),0);assertEquals(0,beatShare(BEAT_CUT),0);
        // weaker than Star Platinum's (the user's answer): three quarters of it, more the lower their health
        assertEquals(45,beatdownDamage(100,100),1e-4);assertEquals(90,beatdownDamage(0,100),1e-4);assertEquals(67.5f,beatdownDamage(50,100),1e-4);
        for(float h:new float[]{0,20,50,100})assertTrue(beatdownDamage(h,100)<StarPlatinumRules.beatdownDamage(h,100),"weaker at "+h);
        assertEquals(.75f,beatdownDamage(100,100)/StarPlatinumRules.beatdownDamage(100,100),1e-4);
        // three minutes from its end
        assertEquals(3600,BEAT_COOLDOWN);assertEquals(1000+3600,beatReady(1000));
        // they come down after the last blow, and it is over a moment after; the sound outlasts the last blow
        assertTrue(BEAT_LAND>BEAT_FINAL&&BEAT_END>BEAT_LAND);
        // held where Star Platinum's holds them, driven higher by the WRYYY than through the rush
        assertEquals(StarPlatinumRules.beatHold(1.2),beatHold(1.2),1e-9);assertEquals(StarPlatinumRules.beatHold(5),beatHold(5),1e-9);
        assertTrue(BEAT_HIGH>BEAT_LIFT+2);
        // the sound is the video's from its second shot to its end: it starts as the punch lands and its last blow is on the server's
        var ogg=java.nio.file.Path.of("src/main/resources/assets/celestialfall/sounds/gold_experience_beatdown.ogg");
        assertTrue(java.nio.file.Files.size(ogg)>100_000,"the cutscene's sound");
        assertEquals(BEAT_CUT,BEAT_SOUND);
    }
    @Test void tGivesLifeAFrogThatTurnsBlowsBackOrAFlyTheyMustNotSwat(){
        // the frog: born at the stand's touch, on for ten seconds, the item given back as it is over
        assertEquals(200,FROG_LASTS,"ten seconds");assertEquals(FROG_BORN+FROG_LASTS,FROG_END);
        assertTrue(FROG_BORN>0&&FROG_BORN<=8,"the stand's touch, quick");
        assertFalse(frogOn(1000,1000+FROG_BORN-1));assertTrue(frogOn(1000,1000+FROG_BORN));
        assertTrue(frogOn(1000,1000+FROG_END-1));assertFalse(frogOn(1000,1000+FROG_END));
        // every blow it takes goes back whole to whoever struck it ("나는 0, 적이 그대로 받음")
        for(float a:new float[]{1,5,12.5f,60,200})assertEquals(a,reflected(a),1e-6);
        // the punch: 5, quick, the overdrive punch's lane
        assertEquals(5,FLY_PUNCH_DAMAGE,1e-6);assertTrue(FLY_CONTACT<OVER_CONTACT,"quicker than the overdrive punch");
        assertTrue(FLY_PUNCH_LENGTH>FLY_CONTACT);
        for(double[] p:new double[][]{{2,0,0},{4.4,0,0},{4.6,0,0},{2,1.6,0},{-.4,0,0},{1,0,3}})
            assertEquals(overLane(p[0],p[1],p[2],.3),lifeLane(p[0],p[1],p[2],.3));
        // the fly: twenty seconds at most, swatted it is 60 to whom it belongs to and a second down
        assertEquals(400,FLY_LASTS,"twenty seconds");assertEquals(FLY_FORMS+FLY_LASTS+FLY_RETURN,FLY_GONE);
        assertEquals(60,FLY_SWAT,1e-6);assertEquals(20,FLY_DOWN,"a second down");
        assertFalse(swattable(0));assertFalse(swattable(FLY_FORMS-1),"not while it is still the part flying off");
        assertTrue(swattable(FLY_FORMS));assertTrue(swattable(FLY_FORMS+FLY_LASTS-1));
        assertFalse(swattable(FLY_FORMS+FLY_LASTS),"not as it flies back");
        // the parts: the head, the arms, the legs
        assertEquals(5,PARTS);assertEquals(0,HEAD);
        // one cooldown of 50 seconds for both, from the end (of the frog, of the fly)
        assertEquals(1000,LIFE_COOLDOWN);assertEquals(5000+1000,lifeReady(5000));
    }
    /** Its owner's blow meets the fly on its way (2.151.0, the multiplayer check: a bare-handed punch's own line all but never did). */
    @Test void theOwnersBlowMeetsTheFlyInItsWay(){
        assertTrue(swatWay(1.2,0,1.2,3.15),"right in front of them, at their face");
        assertTrue(swatWay(1.2,1.05,1.2,3.15)&&swatWay(1.2,-1.05,1.2,3.15),"a body's width at either hand");
        assertFalse(swatWay(1.2,1.2,1.2,3.15),"but not further to the side");
        assertFalse(swatWay(1.6,0,1.2,1.0),"nor beyond whom the blow strikes first");
        assertTrue(swatWay(1.25,0,1.2,1.0),"(a little beyond them: the fly is not a point)");
        assertFalse(swatWay(-.3,0,1.2,3.15),"nor behind them");
        assertFalse(swatWay(1.2,0,2.8,3.15),"nor high over their head");
    }
    /**
     * (2.152.0) H, the tree of life: the stand's palm on the ground, then the tree bursting up under its user strikes 30
     * round it out to 6 blocks and flings them about 8 blocks high; it stands, withers and is back in the ground; 45
     * seconds from then (the user's numbers).
     */
    @Test void hBringsUpATreeThatStrikesRoundItAndFlingsItsUserHigh(){
        assertTrue(TREE_TOUCH<TREE_BURST&&TREE_BURST<TREE_GROWN&&TREE_GROWN<TREE_WITHER&&TREE_WITHER<TREE_END);
        assertEquals(30,TREE_DAMAGE,1e-6);assertEquals(6,TREE_RADIUS,1e-9);
        assertEquals(900,TREE_COOLDOWN,"45 seconds");assertEquals(1000+900,treeReady(1000));
        assertTrue(treeArea(5.9,0,0,0));assertTrue(treeArea(6.2,0,0,.3),"a body's side within it");assertFalse(treeArea(6.4,0,0,.3));
        assertFalse(treeArea(1,-2,0,.3),"not far under its foot");assertFalse(treeArea(1,4,0,.3),"nor high over it");
        // pushed straight out from the trunk; one right on it the way its user faced
        double[] o=treeOut(3,4,0);assertEquals(.6,o[0],1e-9);assertEquals(.8,o[1],1e-9);
        o=treeOut(0,0,90);assertEquals(-1,o[0],1e-6);assertEquals(0,o[1],1e-6);
        // flung about 8 blocks high by the game's own gravity and drag ("약 8블록"), 3 on the way they faced as they come down
        double[] leap=treeLeap();
        assertEquals(TREE_RISE,apex(leap[1]),.02);
        double x=0,y=0,vx=leap[0],vy=leap[1],top=0;int k=0;
        // (the first tick still on the ground: its grip, then the air's)
        do{x+=vx;y+=vy;top=Math.max(top,y);vx*=k==0?GRIP:AIR;vy=(vy-GRAVITY)*DRAG;k++;}while(y>0&&k<400);
        assertEquals(TREE_RISE,top,.02);assertEquals(TREE_AHEAD,x,.05);
        assertTrue(TREE_SPARED>=k,"spared the fall's harm at least as long as they are in the air ("+k+" ticks)");
    }
}
