package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.combat.Stagger;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Lee So-sang's basic attack (2.77.0): four hits, their blows in order, no knockback anywhere, the combo's rhythm and rest. */
class IsosangRulesTest {
    @Test void everyHitsBlowsLandInOrderWhileItHoldsItsUser(){
        assertArrayEquals(new int[]{1,1,4,1},new int[]{blows(1),blows(2),blows(3),blows(4)});
        for(int combo=1;combo<=4;combo++){
            for(int i=0;i<blows(combo);i++){
                assertTrue(blowAt(combo,i)>0&&blowAt(combo,i)<LENGTH[combo],"hit "+combo+" blow "+i);
                if(i>0)assertTrue(blowAt(combo,i)>blowAt(combo,i-1),"hit "+combo+" blows in order");
                assertTrue(damage(combo,i)>0);
            }
            assertTrue(lastBlow(combo)<HOLD[combo],"hit "+combo+" holds its user until its last blow has landed");
            assertTrue(HOLD[combo]<=LENGTH[combo]);
            if(combo<4)assertTrue(STEP[combo]>lastBlow(combo),"the next hit waits for hit "+combo+"'s last blow");
        }
        assertEquals(29,total(1)+total(2)+total(3)+total(4),1e-4,"the whole combo, as Kageno's");
        assertTrue(unblockable(4)&&!unblockable(1)&&!unblockable(2)&&!unblockable(3),"only the finisher takes no guard");
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
        // hit 3's blows hold whom they strike from one to the next
        for(int i=0;i<3;i++){long now=start+blowAt(3,i);assertEquals(start+blowAt(3,i+1)+Stagger.PAST,Stagger.isosang(3,i,start,now));}
        // the last blow of a hit holds them until the next hit's first could land, started as soon as it may
        assertEquals(start+STEP[1]+blowAt(2,0)+Stagger.PAST,Stagger.isosang(1,0,start,start+blowAt(1,0)));
        assertEquals(start+STEP[3]+blowAt(4,0)+Stagger.PAST,Stagger.isosang(3,3,start,start+blowAt(3,3)));
        // the finisher throws no one: it staggers them a moment where they stand
        assertEquals(start+blowAt(4,0)+Stagger.LAST,Stagger.isosang(4,0,start,start+blowAt(4,0)));
        // a late blow (the time stop letting time move again) never staggers into the past
        assertEquals(500+Stagger.PAST,Stagger.isosang(1,0,start,500));
    }
    @Test void eachBlowReachesItsOwnWay(){
        // the cut: ahead and to both sides, not behind
        assertTrue(lane(1,0,2,1.5,1,.3));assertFalse(lane(1,0,-1.5,0,1,.3));assertFalse(lane(1,0,2,0,3.5,.3));
        // the turn: all round
        for(int a=0;a<360;a+=30){double r=SPIN_RADIUS-.2,x=Math.cos(Math.toRadians(a))*r,y=Math.sin(Math.toRadians(a))*r;assertTrue(lane(2,0,x,y,.5,.3),"the turn at "+a);}
        assertFalse(lane(2,0,SPIN_RADIUS+1,0,.5,.3));
        // the thrust: long and narrow; the cross and the sweep: wide and nearer
        assertTrue(lane(3,0,4.5,0,1,.3));assertFalse(lane(3,0,2,1.6,1,.3));
        assertTrue(lane(3,1,2,1.6,1,.3));assertFalse(lane(3,1,4.5,0,1,.3));assertTrue(lane(3,3,2,2.1,1,.3));
        // the finisher: far and wide across the front
        assertTrue(lane(4,0,4.6,0,1,.3));assertTrue(lane(4,0,2,2.7,1,.3));assertFalse(lane(4,0,-1,0,1,.3));
    }
}
