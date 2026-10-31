package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.combat.Stagger;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's basic attack (2.77.0, rebuilt after the user's videos of each hit in 2.77.2): four hits, their blows in
 * order, no knockback anywhere, the combo's rhythm and rest, and the finisher's slash thrown flying: it strikes whoever
 * its band passes over on its way, with no gap between one tick and the next, and no one behind, beside or beyond it.
 */
class IsosangRulesTest {
    @Test void everyHitsBlowsLandInOrderWhileItHoldsItsUser(){
        // the great cut; the ring and its burst; the thrust and four cuts; the thrown slash
        assertArrayEquals(new int[]{1,2,5,1},new int[]{blows(1),blows(2),blows(3),blows(4)});
        for(int combo=1;combo<=4;combo++){
            for(int i=0;i<blows(combo);i++){
                assertTrue(blowAt(combo,i)>0&&blowAt(combo,i)<LENGTH[combo],"hit "+combo+" blow "+i);
                if(i>0)assertTrue(blowAt(combo,i)>blowAt(combo,i-1),"hit "+combo+" blows in order");
                assertTrue(damage(combo,i)>0);
            }
            assertTrue(lastBlow(combo)<HOLD[combo],"hit "+combo+" holds its user until its last blow has landed");
            assertTrue(HOLD[combo]<=LENGTH[combo]);
            if(combo<4)assertTrue(STEP[combo]>lastBlow(combo),"the next hit waits for hit "+combo+"'s last blow");
            if(combo<3)assertTrue(STEP[combo]<LENGTH[combo],"hit "+combo+" is still moving as the next may take over");
        }
        // (after the third the next waits a moment in the stance, as the video of the whole combo shows)
        assertTrue(STEP[3]>=LENGTH[3]);
        assertTrue(LENGTH[4]/20.0>blowAt(4,0)/20.0+WAVE_TICKS/20.0,"the finisher stands until its slash has flown");
        assertEquals(29,total(1)+total(2)+total(3)+total(4),1e-4,"the whole combo, as Kageno's");
        assertEquals(6,total(2),1e-4);assertEquals(7,total(3),1e-4);
        assertTrue(unblockable(4)&&!unblockable(1)&&!unblockable(2)&&!unblockable(3),"only the finisher takes no guard");
    }
    @Test void thePaceIsTheVideos(){
        // each hit's video gives its blows; the video of the whole combo how soon the next may follow
        assertArrayEquals(new int[]{11,10,30},new int[]{STEP[1],STEP[2],STEP[3]});
        assertEquals(6,blowAt(1,0));assertEquals(3,blowAt(2,0));assertEquals(6,blowAt(2,1));
        assertArrayEquals(new int[]{6,10,14,16,18},new int[]{blowAt(3,0),blowAt(3,1),blowAt(3,2),blowAt(3,3),blowAt(3,4)});
        assertEquals(14,blowAt(4,0),"the slash is thrown after the twirl, the aim, the crouch and the lunge");
    }
    @Test void theComboBreaksWhenTheNextHitIsLateAndRestsAfterwards(){
        assertEquals(2,next(1));assertEquals(4,next(3));assertEquals(1,next(4));assertEquals(1,next(0));
        long start=100;
        assertEquals(-1,broken(1,start,start+STEP[1]+GRACE),"still on time");
        assertEquals(start+STEP[1]+GRACE+1+REST,broken(1,start,start+STEP[1]+GRACE+1));
        assertEquals(-1,broken(4,start,start+500),"the fourth hit ends the combo itself");
        assertEquals(start+LENGTH[4]+REST,rested(start),"the rest runs once the fourth hit is over");
        assertTrue(holding(1,start,start)&&!holding(1,start,start+HOLD[1]));
    }
    @Test void everyBlowStaggersUntilTheNextCouldLandAndTheFinisherABitLonger(){
        long start=0;
        // hit 3's blows hold whom they strike from one to the next, and hit 2's ring until it bursts
        for(int i=0;i<4;i++){long now=start+blowAt(3,i);assertEquals(start+blowAt(3,i+1)+Stagger.PAST,Stagger.isosang(3,i,start,now));}
        assertEquals(start+blowAt(2,1)+Stagger.PAST,Stagger.isosang(2,0,start,start+blowAt(2,0)));
        // the last blow of a hit holds them until the next hit's first could land, started as soon as it may
        assertEquals(start+STEP[1]+blowAt(2,0)+Stagger.PAST,Stagger.isosang(1,0,start,start+blowAt(1,0)));
        assertEquals(start+STEP[2]+blowAt(3,0)+Stagger.PAST,Stagger.isosang(2,1,start,start+blowAt(2,1)));
        assertEquals(start+STEP[3]+blowAt(4,0)+Stagger.PAST,Stagger.isosang(3,4,start,start+blowAt(3,4)));
        // the thrown slash throws no one: it staggers them a moment where they stand, whenever it reaches them
        assertEquals(start+blowAt(4,0)+3+Stagger.LAST,Stagger.isosang(4,0,start,start+blowAt(4,0)+3));
        // a late blow (the time stop letting time move again) never staggers into the past
        assertEquals(500+Stagger.PAST,Stagger.isosang(1,0,start,500));
    }
    @Test void eachBlowReachesItsOwnWay(){
        // the great cut: ahead and wide to both sides, not far behind nor overhead
        assertTrue(lane(1,0,2,3,1,.3));assertFalse(lane(1,0,-1.5,0,1,.3));assertFalse(lane(1,0,2,0,3.5,.3));
        // the ring: all round, both blows
        for(int i=0;i<2;i++)for(int a=0;a<360;a+=30){double r=SPIN_RADIUS-.2,x=Math.cos(Math.toRadians(a))*r,y=Math.sin(Math.toRadians(a))*r;assertTrue(lane(2,i,x,y,.5,.3),"the ring at "+a);}
        assertFalse(lane(2,0,SPIN_RADIUS+1,0,.5,.3));
        // the thrust: long and narrow, only ahead; the cuts after it wide and nearer; the wide sweep the widest of them
        assertTrue(lane(3,0,6,0,1,.3));assertFalse(lane(3,0,2,1.6,1,.3));assertFalse(lane(3,0,-.5,0,1,.3));
        assertTrue(lane(3,1,2,2.5,1,.3));assertFalse(lane(3,1,4.8,0,1,.3));
        assertTrue(lane(3,2,2,2.7,1,.3));assertTrue(lane(3,3,2,3.4,1,.3));assertTrue(lane(3,4,4,3.8,1,.3));assertFalse(lane(3,4,5,0,1,.3));
    }
    @Test void theThrownSlashFliesOutWideningAndSlowing(){
        assertEquals(WAVE_START,waveFront(0),1e-9);assertEquals(WAVE_RANGE,waveFront(WAVE_TICKS),1e-9);assertEquals(WAVE_RANGE,waveFront(WAVE_TICKS+5),1e-9);
        assertEquals(WAVE_HALF_START,waveHalf(0),1e-9);assertEquals(WAVE_HALF_END,waveHalf(WAVE_TICKS),1e-9);
        double was=waveFront(0),speed=Double.MAX_VALUE;
        for(int k=1;k<=WAVE_TICKS;k++){
            double now=waveFront(k);assertTrue(now>was,"it flies on");
            assertTrue(now-was<speed+1e-9,"fast at first, slowing");speed=now-was;was=now;
            assertTrue(waveHalf(k)>waveHalf(k-1),"it widens as it flies");
        }
        assertTrue(waveFront(1)-waveFront(0)>.6,"it leaves fast: "+(waveFront(1)-waveFront(0)));
        // 2.78.0 (the user's video from above): still close a quarter of a second on, drifting out past half a second
        assertTrue(waveFront(5)<7,"still near a quarter of a second on: "+waveFront(5));assertTrue(waveFront(12)>waveFront(10)+.3,"still drifting out past half a second");
        // its tips trail behind its middle
        assertTrue(waveEdge(4,waveHalf(4))<waveEdge(4,0)-1);
        assertTrue(waveFlying(0)&&waveFlying(WAVE_TICKS)&&!waveFlying(WAVE_TICKS+1)&&!waveFlying(-1));
    }
    /** The first tick of the flight whose sweep takes a body there (the throw itself is tick 0), or -1. */
    static int struckAt(double fw,double side,double half){
        for(int k=0;k<=WAVE_TICKS;k++)if(waveSweeps(Math.max(0,k-1),k,fw,side,1,half))return k;
        return -1;
    }
    @Test void theThrownSlashStrikesEveryoneItPassesOverAndNoOneElse(){
        // all along its way, in the middle and out to the sides, no one slips between one tick and the next
        for(double fw=-.4;fw<=WAVE_RANGE-.1;fw+=.1)assertTrue(struckAt(fw,0,.3)>=0,"missed straight ahead at "+fw);
        for(double fw=3;fw<=WAVE_RANGE-WAVE_CURVE_END-.2;fw+=.25)for(double side=-4;side<=4;side+=.5)
            assertTrue(struckAt(fw,side,.3)>=0,"missed at "+fw+", "+side);
        // right in front as it is thrown: at once; further on: later
        assertEquals(0,struckAt(.5,0,.3));assertTrue(struckAt(8,0,.3)>struckAt(4,0,.3));
        // not behind them, not beyond its range, not out past its tips, not over its head or under its feet
        assertEquals(-1,struckAt(-2,0,.3));assertEquals(-1,struckAt(WAVE_RANGE+1,0,.3));
        assertEquals(-1,struckAt(6,WAVE_HALF_END+1,.3));assertTrue(struckAt(.5,5.5,.3)>struckAt(.5,0,.3),"a wide point beside them only once the band has widened over it");
        assertFalse(waveSweeps(0,WAVE_TICKS,5,0,HIGH+.5,.3));assertFalse(waveSweeps(0,WAVE_TICKS,5,0,LOW-.5,.3));
        // a big body is reached sooner than a small one
        assertTrue(struckAt(9,0,1.2)<=struckAt(9,0,.3));
        // a boss reads it as a lane as long and wide as its flight
        assertEquals(WAVE_RANGE,reach(4,0),1e-9);assertEquals(WAVE_HALF_END,halfWidth(4,0),1e-9);
    }
}
