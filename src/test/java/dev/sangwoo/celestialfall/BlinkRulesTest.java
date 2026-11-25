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
    @Test void whatItLeavesIsGoneInHalfASecond(){
        assertEquals(.5,BlinkRules.LEFT/20.0,1e-9);assertEquals(.5,BlinkRules.AFTER/20.0,1e-9);
    }
    @Test void itsPressIsItsOwnByte(){
        int[] all={XCharaRules.G,XCharaRules.Q,XCharaRules.E,XCharaRules.R,XCharaRules.H,XCharaRules.T,XCharaRules.Y,XCharaRules.AIM,XCharaRules.Z};
        for(int i=0;i<all.length;i++)for(int j=i+1;j<all.length;j++)assertNotEquals(all[i],all[j]);
    }
}
