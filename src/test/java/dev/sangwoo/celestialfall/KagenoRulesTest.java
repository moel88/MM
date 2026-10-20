package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;
final class KagenoRulesTest {
    @Test void referenceDamageAndHitCounts(){
        int[] skills={E,R,T,RUSH,Y,H},hits={8,5,13,6,2,12};float[] totals={26,38,51,50,16,32};
        for(int i=0;i<skills.length;i++){float total=0;int count=0;for(int a=0;a<DURATION[skills[i]];a++){float d=damage(skills[i],a,0);total+=d;if(d>0)count+=KagenoRules.hits(skills[i],a);}
            // T+'s fire slashes strike as they fly past, outside the timed hits.
            if(skills[i]==RUSH)for(int s=0;s<RUSH_SLASHES.length;s++){total+=rushSlashDamage(s);count++;}
            assertEquals(totals[i],total,.001);assertEquals(hits[i],count);}
        float total=0;for(int combo=1;combo<=4;combo++)for(int a=0;a<14;a++)total+=damage(M1,a,combo);assertEquals(28,total);
    }
    @Test void affectedRadiiIncludeBoundaryButRejectOutsideAndNan(){assertTrue(inside(49,STOP_RADIUS));assertFalse(inside(49.00001,STOP_RADIUS));assertTrue(inside(25,HIGHEST_RADIUS));assertFalse(inside(25.00001,HIGHEST_RADIUS));assertFalse(inside(Double.NaN,5));}
    /** User report (2.38.2): R's caster stayed slow for 1.6 s after R looked finished; the slowdown ends with R's input window. */
    @Test void rSlowdownEndsWithItsInputWindow(){
        assertTrue(rWalking(0));assertTrue(rWalking(inputEnd(R)-1));assertFalse(rWalking(inputEnd(R)));assertFalse(rWalking(DURATION[R]-1));
        assertTrue(inputEnd(R)<DURATION[R],"the effects still play on after the caster is free");
    }
    @Test void freezeEndsBeforeDamageAndAlwaysReleases(){assertFalse(frozen(R,R_GRAY-.01f));assertTrue(frozen(R,R_GRAY));assertTrue(frozen(R,R_COLOR-.01f));assertFalse(frozen(R,R_COLOR));for(int a=0;a<R_COLOR;a++)assertEquals(0,damage(R,a,0));assertEquals(4,hits(R,R_STORED));assertEquals(R_CUTS,hits(R,R_STORED));assertTrue(damage(R,R_RELEASE,0)>0);assertTrue(frozen(H,115));assertFalse(frozen(H,116));}
    /** R, buffed: a 7-block time stop, four stored cuts (24) and the big final cut (14), which also strikes the band ahead; 9 s cooldown. */
    @Test void timeWalkerStopsWiderCutsHarderAndReleasesAlongTheBand(){
        assertEquals(7,STOP_RADIUS);assertEquals(24,damage(R,R_STORED,0));assertEquals(14,damage(R,R_RELEASE,0));assertEquals(180,COOLDOWN[R]);
        assertTrue(rBand(0,0,0)&&rBand(7,10,5)&&rBand(3.5,-10,-2));
        assertFalse(rBand(-.01,0,0));assertFalse(rBand(7.01,0,0));assertFalse(rBand(3,10.01,0));assertFalse(rBand(3,0,5.01));assertFalse(rBand(3,0,-2.01));
        assertTrue(R_RELEASE>R_STORED&&R_STORED>=R_COLOR);
    }
    @Test void groundFireReleasesCasterWhileHazardContinues(){assertEquals(15,inputEnd(T));assertEquals(15,damage(T,T_STRIKE,0));assertTrue(damage(T,T_STRIKE+64,0)>0);assertEquals(0,damage(T,T_STRIKE+65,0));assertEquals(8,HOLD);assertEquals(COOLDOWN[T],COOLDOWN[RUSH]);}
    /**
     * T+ (remade in 2.38.0): three fire slashes fly 30 blocks up the line, the dash covers its whole way in its ticks
     * (an explosive start, eased out) and arrives before the spin cut, and whoever a slash strikes is held until the cut.
     */
    @Test void throughTheDarkThrowsFireSlashesDashesToWhomTheyStruckAndSpins(){
        assertEquals(0,rushDashed(-1));assertEquals(1,rushDashed(RUSH_DASH-1),1e-9);assertEquals(1,rushDashed(RUSH_DASH+3),1e-9);
        for(int k=1;k<RUSH_DASH;k++)assertTrue(rushDashed(k)-rushDashed(k-1)<rushDashed(k-1)-rushDashed(k-2)+1e-9,"eases out at "+k);
        assertTrue(RUSH_SLASHES[0]>0&&RUSH_SLASHES[RUSH_SLASHES.length-1]<RUSH_START&&RUSH_ARRIVE<RUSH_BURST&&RUSH_BURST<RUSH_FINISH);
        for(int i=0;i<RUSH_SLASHES.length;i++){
            assertEquals(-1,rushSlash(i,RUSH_SLASHES[i]-.5f));assertEquals(1,rushSlash(i,RUSH_SLASHES[i]),1e-9);
            assertEquals(RUSH_RANGE,rushSlash(i,RUSH_SLASHES[i]+20),1e-9);
            // Each is out at the end of its range before the dash is over, so it can still turn the dash.
            assertTrue(rushSlash(i,RUSH_ARRIVE-1)>=RUSH_RANGE||i==RUSH_SLASHES.length-1);
        }
        assertTrue(rushPath(10,2.4,1,9.5,10.5)&&!rushPath(10,2.41,1,9.5,10.5)&&!rushPath(11,0,1,9.5,10.5)&&!rushPath(10,0,3.6,9.5,10.5));
        assertEquals(13,damage(RUSH,RUSH_ARRIVE,0));assertEquals(5,damage(RUSH,RUSH_BURST,0));assertEquals(18,damage(RUSH,RUSH_FINISH,0));
        assertEquals(0,damage(RUSH,RUSH_SLASHES[0],0));assertEquals(0,damage(RUSH,RUSH_START,0));
        assertTrue(trapped(T,T_STRIKE)&&!trapped(T,T_STRIKE+71)&&!trapped(RUSH,RUSH_SLASHES[0]-1)&&trapped(RUSH,RUSH_SLASHES[0])&&trapped(RUSH,RUSH_FINISH-1)&&!trapped(RUSH,RUSH_FINISH));
    }
    /** Stepping the dash tick by tick lands exactly on its end: just short of the one struck, or the end of the range. */
    @Test void theDashStepsLandOnItsEnd(){
        var origin=new net.minecraft.util.math.Vec3d(.5,64,.5);var f=new net.minecraft.util.math.Vec3d(0,0,1);
        for(var mark:new net.minecraft.util.math.Vec3d[]{null,new net.minecraft.util.math.Vec3d(1.2,64,12.5),new net.minecraft.util.math.Vec3d(.5,64,1.2)}){
            var at=origin;
            for(int k=0;k<RUSH_DASH;k++){var dest=rushDestination(origin,f,at,mark);at=at.add(dest.subtract(at).multiply(1,0,1).multiply(rushStep(k)));}
            if(mark==null)assertEquals(RUSH_RANGE,at.subtract(origin).dotProduct(f),1e-9);
            else if(origin.distanceTo(mark)>RUSH_STOP)assertEquals(RUSH_STOP,at.distanceTo(mark),1e-9);
            else assertEquals(0,at.distanceTo(origin),1e-9,"already close enough: he stays");
        }
        assertEquals(1,rushStep(RUSH_DASH-1),1e-9);assertEquals(1,rushStep(RUSH_DASH+2),1e-9);assertEquals(.24,rushStep(0),1e-9);
    }
    /** The last slash leaves ahead of the dash and stays ahead of the caster all the way (the dash never overtakes it). */
    @Test void theDashNeverOvertakesTheLastSlash(){
        int last=RUSH_SLASHES.length-1;
        for(int age=RUSH_START;age<RUSH_ARRIVE;age++){
            double caster=RUSH_RANGE*rushDashed(age-RUSH_START),slash=rushSlash(last,age);
            assertTrue(slash>=caster||slash>=RUSH_RANGE,"caster "+caster+" passes slash "+slash+" at "+age);
        }
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
    /** M1 hit 4 throws whoever it strikes ≈7 blocks along the thrust (vanilla drag: 0.546 on the ground, 0.91 in the air; gravity 0.08). */
    @Test void theFourthHitSendsItsTargetFlying(){
        double x=0,y=0,vx=M1_FLY,vy=M1_FLY_UP;int ticks=0;boolean ground=true;
        do{x+=vx;y+=vy;vx*=ground?.546:.91;vy=(vy-.08)*.98;ground=false;ticks++;}while(y>0&&ticks<100);
        assertTrue(x>6&&x<8,"flies "+x+" blocks");assertTrue(ticks>=10&&ticks<=16,"lands after "+ticks+" ticks");
    }
    /**
     * User request (2.43.0): a combo whose next hit does not start in time breaks, rests as long as after a fourth hit
     * (shown in the cooldown list) and starts again from the first hit.
     */
    @Test void aLateHitBreaksTheComboWhichRestsAsAfterTheFourth(){
        assertEquals(-1,m1Broken(1,100,100+M1_WINDOW),"the next hit may still start at the end of the window");
        assertEquals(100+M1_WINDOW+1+M1_REST,m1Broken(1,100,100+M1_WINDOW+1),"broken once the window is past: it rests from there");
        assertEquals(m1Broken(3,100,137),m1Broken(3,100,400),"the rest is the same whenever the break is noticed");
        assertEquals(-1,m1Broken(4,100,1000),"the fourth hit ends the combo with its own rest");
        assertEquals(-1,m1Broken(0,100,1000),"nothing to break before a first hit");
        assertTrue(M1_REST>M1_STEP&&M1_STEP<M1_WINDOW,"hits follow closer than the rest, and in time");
    }
}
