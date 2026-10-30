package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.combat.Stagger;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's basic attack (2.77.0, remade after the user's video of each hit in 2.77.1): four hits, their blows in
 * order, no knockback anywhere, the combo's rhythm and rest.
 */
class IsosangRulesTest {
    @Test void everyHitsBlowsLandInOrderWhileItHoldsItsUser(){
        // the diagonal cut; the low turn and its ring flaring; the thrust and four cuts; the finisher
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
            if(combo<4)assertTrue(STEP[combo]<LENGTH[combo],"hit "+combo+" is still moving as the next may take over");
        }
        assertEquals(29,total(1)+total(2)+total(3)+total(4),1e-4,"the whole combo, as Kageno's");
        assertEquals(6,total(2),1e-4);assertEquals(7,total(3),1e-4);
        assertTrue(unblockable(4)&&!unblockable(1)&&!unblockable(2)&&!unblockable(3),"only the finisher takes no guard");
    }
    @Test void thePaceIsTheVideos(){
        // in the user's videos the hits follow 0.6, 0.65 and about 1.5 s apart; the blows as there
        assertArrayEquals(new int[]{12,13,30},new int[]{STEP[1],STEP[2],STEP[3]});
        assertEquals(6,blowAt(1,0));assertEquals(5,blowAt(2,0));assertEquals(9,blowAt(2,1));
        assertArrayEquals(new int[]{3,9,11,13,19},new int[]{blowAt(3,0),blowAt(3,1),blowAt(3,2),blowAt(3,3),blowAt(3,4)});
        assertEquals(11,blowAt(4,0));
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
        // hit 3's blows hold whom they strike from one to the next, and hit 2's turn until its ring flares
        for(int i=0;i<4;i++){long now=start+blowAt(3,i);assertEquals(start+blowAt(3,i+1)+Stagger.PAST,Stagger.isosang(3,i,start,now));}
        assertEquals(start+blowAt(2,1)+Stagger.PAST,Stagger.isosang(2,0,start,start+blowAt(2,0)));
        // the last blow of a hit holds them until the next hit's first could land, started as soon as it may
        assertEquals(start+STEP[1]+blowAt(2,0)+Stagger.PAST,Stagger.isosang(1,0,start,start+blowAt(1,0)));
        assertEquals(start+STEP[2]+blowAt(3,0)+Stagger.PAST,Stagger.isosang(2,1,start,start+blowAt(2,1)));
        assertEquals(start+STEP[3]+blowAt(4,0)+Stagger.PAST,Stagger.isosang(3,4,start,start+blowAt(3,4)));
        // the finisher throws no one: it staggers them a moment where they stand
        assertEquals(start+blowAt(4,0)+Stagger.LAST,Stagger.isosang(4,0,start,start+blowAt(4,0)));
        // a late blow (the time stop letting time move again) never staggers into the past
        assertEquals(500+Stagger.PAST,Stagger.isosang(1,0,start,500));
    }
    @Test void eachBlowReachesItsOwnWay(){
        // the cut: ahead and wide to both sides, not behind nor overhead
        assertTrue(lane(1,0,2,2.2,1,.3));assertFalse(lane(1,0,-1.5,0,1,.3));assertFalse(lane(1,0,2,0,3.5,.3));
        // the low turn and its ring: all round, both blows
        for(int i=0;i<2;i++)for(int a=0;a<360;a+=30){double r=SPIN_RADIUS-.2,x=Math.cos(Math.toRadians(a))*r,y=Math.sin(Math.toRadians(a))*r;assertTrue(lane(2,i,x,y,.5,.3),"the turn at "+a);}
        assertFalse(lane(2,0,SPIN_RADIUS+1,0,.5,.3));
        // the thrust: long and narrow, only ahead; the cuts after it wide and nearer; the low sweep the widest of them
        assertTrue(lane(3,0,5,0,1,.3));assertFalse(lane(3,0,2,1.6,1,.3));assertFalse(lane(3,0,-.5,0,1,.3));
        assertTrue(lane(3,1,2,2.1,1,.3));assertFalse(lane(3,1,4.6,0,1,.3));
        assertTrue(lane(3,2,2,1.5,1,.3));assertFalse(lane(3,2,2,2.4,1,.3));
        assertTrue(lane(3,3,2,1.7,1,.3));assertTrue(lane(3,4,3.7,2.5,1,.3));
        // the finisher: far and very wide across the front, not behind
        assertTrue(lane(4,0,5.5,0,1,.3));assertTrue(lane(4,0,2,3.5,1,.3));assertFalse(lane(4,0,-1,0,1,.3));assertFalse(lane(4,0,6.5,0,1,.3));
    }
}
