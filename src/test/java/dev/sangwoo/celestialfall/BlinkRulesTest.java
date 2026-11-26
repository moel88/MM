package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.BlinkRules;
import dev.sangwoo.celestialfall.xchara.XCharaRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** X Chara's Z, the teleport (2.138.0): its timing and its numbers. */
class BlinkRulesTest {
    @Test void aFifthOfASecondOfTheFlickerThenTheMove(){
        assertEquals(.2,BlinkRules.GO/20.0,1e-9);
        assertFalse(BlinkRules.casting(-1));assertTrue(BlinkRules.casting(0)&&BlinkRules.casting(BlinkRules.GO-1));assertFalse(BlinkRules.casting(BlinkRules.GO));
    }
    @Test void fiveBlocksAndFifteenSecondsAsTheUserSaid(){
        assertEquals(5,BlinkRules.REACH,1e-9);
        assertEquals(300,BlinkRules.COOLDOWN);
        assertEquals(1000+300,BlinkRules.ready(1000));
    }
    @Test void theWayItGoesIsTheWayTheyLookLevel(){
        double[] w=BlinkRules.way(0);assertEquals(0,w[0],1e-9);assertEquals(1,w[1],1e-9);
        w=BlinkRules.way(90);assertEquals(-1,w[0],1e-9);assertEquals(0,w[1],1e-9);
        w=BlinkRules.way(-90);assertEquals(1,w[0],1e-9);assertEquals(0,w[1],1e-9);
        w=BlinkRules.way(180);assertEquals(0,w[0],1e-9);assertEquals(-1,w[1],1e-9);
        for(float a=-180;a<180;a+=7){w=BlinkRules.way(a);assertEquals(1,Math.hypot(w[0],w[1]),1e-9);}
    }
    @Test void aBlockInTheWayStopsItShortAndItNeverGoesBack(){
        assertEquals(5,BlinkRules.reach(99),1e-9);assertEquals(5,BlinkRules.reach(BlinkRules.REACH+BlinkRules.SHORT),1e-9);
        assertEquals(3-BlinkRules.SHORT,BlinkRules.reach(3),1e-9);
        assertEquals(0,BlinkRules.reach(.2),1e-9);assertEquals(0,BlinkRules.reach(-1),1e-9);
        for(double c=0;c<9;c+=.1)assertTrue(BlinkRules.reach(c+.1)>=BlinkRules.reach(c)-1e-9);
        assertTrue(BlinkRules.UP<1&&BlinkRules.DOWN>=3&&BlinkRules.NEARER<=.5);
    }
    @Test void noWallIsGoneThroughHoweverThinItIs(){
        // (2.138.1, "벽통과는 못하게") the whole body's way is looked along: free up to a wall, never beyond it
        double most=BlinkRules.REACH+BlinkRules.SHORT-.3;
        assertEquals(most,BlinkRules.clearAlong(d->true,most),1e-9,"nothing in the way: all of it");
        assertEquals(0,BlinkRules.clearAlong(d->false,most),1e-9,"no room where they stand: nowhere");
        assertEquals(0,BlinkRules.clearAlong(d->true,0),1e-9);
        for(double wall=.31;wall<6;wall+=.137)for(double thick:new double[]{.02,.0625,.125,.5,1,3}){
            // a wall whose near face is `wall` on and `thick` deep, met by a body 0.6 wide: no room once its front is in it, room again once its back is past it
            double near=wall,deep=thick;
            java.util.function.DoublePredicate free=d->d+.3<=near||d-.3>=near+deep;
            double got=BlinkRules.clearAlong(free,most);
            assertTrue(got<=Math.max(0,near-.3)+1e-9,"wall at "+wall+", "+thick+" deep: stopped at "+got);
            assertTrue(got>=Math.min(most,near-.3)-.002,"and it goes right up to it: "+got);
            // where they are put: short of the wall's face, never past it
            double put=BlinkRules.reach(got+.3);
            assertTrue(put+.3<=near+1e-9||put==0,"put at "+put+" before the wall at "+near);
        }
        assertTrue(BlinkRules.LOOK<.3,"looked along far finer than a body is wide: nothing is stepped over");
    }
    @Test void whatItLeavesIsGoneInHalfASecond(){
        assertEquals(.5,BlinkRules.LEFT/20.0,1e-9);assertEquals(.5,BlinkRules.AFTER/20.0,1e-9);
    }
    @Test void itsPressIsItsOwnByte(){
        int[] all={XCharaRules.G,XCharaRules.Q,XCharaRules.E,XCharaRules.R,XCharaRules.H,XCharaRules.T,XCharaRules.Y,XCharaRules.AIM,XCharaRules.Z};
        for(int i=0;i<all.length;i++)for(int j=i+1;j<all.length;j++)assertNotEquals(all[i],all[j]);
    }
}
