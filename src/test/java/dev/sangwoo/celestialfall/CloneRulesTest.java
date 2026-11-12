package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import dev.sangwoo.celestialfall.xchara.XCharaRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.CloneRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's R with the hack on, the double (2.125.0, user request: "누르면 위에 !!!가 뜨고 뒤에 벽을 소환하고 옆에 적이랑
 * 똑같은 분신인데 얼굴에 X자가 쳐져있는 분신이 적을 날리는 모션을 취하고 적이 벽으로 날아가 박히는 스킬이야", and the
 * user's video; answers: a guard blocks it as G's, 20 damage, 20 seconds, 1.5 seconds in the wall): its timing on the
 * video's moments, its numbers, who it holds when, and where the one seized is from the throw to the wall's foot,
 * nowhere stepping from one frame to the next.
 */
class CloneRulesTest {
    @Test void itsMomentsAreTheVideos(){
        // the "!!!" 0.05 s after the press, the double's first sound 0.15 s, its throw 0.87 s; the one thrown in the wall
        // 1.95 s (the second use) to 2.3 s (the first)
        assertEquals(.05,MARK/20.0,.03);assertEquals(.15,CLONE/20.0,.03);assertEquals(.87,THROW/20.0,.03);
        assertTrue(IMPACT/20.0>=1.95&&IMPACT/20.0<=2.3,"in the wall between the video's two uses");
        assertTrue(MARK<CLONE&&CLONE<THROW&&THROW<RELEASE&&RELEASE<CLONE_GONE&&CLONE_GONE<FLING&&FLING<IMPACT&&IMPACT<FREE);
        assertEquals(END,FREE,"it is over as the wall lets them go");
        assertTrue(IMPACT-FLING<=4,"flung in an instant, as the video's");
    }
    @Test void theUsersNumbers(){
        assertEquals(20,DAMAGE,1e-6,"the user's answer: 20");
        assertEquals(400,COOLDOWN,"the user's answer: 20 seconds");
        assertEquals(30,STUCK,"the user's answer: 1.5 seconds in the wall");assertEquals(IMPACT+30,FREE);
        assertEquals(8,WALL,1e-9,"the wall some 8 blocks behind them (what I told the user)");
        assertEquals(1000+COOLDOWN,ready(1000),"its cooldown runs from its end");
        // a skill of its own beside the knife's and G's, and a key of its own byte
        assertNotEquals(SKILL,XCharaCombo.SKILL);assertNotEquals(SKILL,XCharaRules.SKILL);
        assertNotEquals(XCharaRules.H,XCharaRules.R);assertNotEquals(XCharaRules.H,XCharaRules.G);assertNotEquals(XCharaRules.H,XCharaRules.Y);assertNotEquals(XCharaRules.H,XCharaRules.Q);
    }
    @Test void aGuardBlocksItUntilTheThrowAsGs(){
        assertTrue(warning(0));assertTrue(warning(THROW-1));assertFalse(warning(THROW),"seized at the throw: no guard after");
        assertFalse(warning(-1));
    }
    @Test void whoItHoldsWhen(){
        assertTrue(userHeld(0));assertTrue(userHeld(RELEASE-1));assertFalse(userHeld(RELEASE),"its user free once the double has thrown");
        assertFalse(held(THROW-1),"the one marked free until the throw (to guard)");
        assertTrue(held(THROW));assertTrue(held(IMPACT));assertTrue(held(FREE-1));assertFalse(held(FREE));
        assertTrue(aloft(THROW));assertTrue(aloft(IMPACT-1));assertFalse(aloft(IMPACT),"in the wall they can be struck");
        assertTrue(RELEASE>THROW,"its user watches the throw");
    }
    @Test void liftedTurnedOverFlungAndHeldInTheWall(){
        assertEquals(0,lift(THROW),1e-9);
        assertEquals(LIFT,lift(THROW+LIFT_TICKS),.02,"up in a third of a second");
        double most=0;for(float t=THROW;t<=FLING;t+=.05f){double h=lift(t);most=Math.max(most,h);if(t>THROW+LIFT_TICKS)assertEquals(LIFT,h,.12,"hanging there, bobbing a little");}
        assertTrue(most>LIFT&&most<LIFT*1.25,"a little past it and back");
        assertEquals(0,flown(FLING),1e-9);assertEquals(1,flown(IMPACT),1e-9);
        assertTrue(flown(FLING+1)<flown(IMPACT-1)-flown(FLING+1),"quickening into the wall");
        assertEquals(0,tumble(THROW),1e-6);
        assertEquals(360+LEAN,tumble(IMPACT-.001f),1,"once round into the wall, leaning back");
        assertEquals(LEAN,tumble(IMPACT),1e-4);assertEquals(LEAN,tumble(FREE),1e-4);assertEquals(0,tumble(FREE+DROP),1e-4,"upright again as they drop out of it");
        for(float t=THROW;t<IMPACT-.1f;t+=.1f)assertTrue(tumble(t+.1f)>tumble(t),"turning one way");
    }
    @Test void whereTheOneSeizedIsDrawn(){
        double far=7.4,rise=1;
        assertNull(drawn(THROW-.01f,far,rise));assertNull(drawn(FREE+DROP,far,rise));
        double[] hung=drawn(FLING-.01f,far,rise);assertEquals(0,hung[2],1e-9,"hanging over where they stood");assertEquals(LIFT,hung[1],.12);
        double[] in=drawn(IMPACT,far,rise);
        assertEquals(far+SINK,in[2],1e-6,"in the wall: past its foot");assertEquals(rise+STUCK_UP,in[1],1e-6,"off the ground");
        double[] held=drawn(FREE-.01f,far,rise);assertArrayEquals(in,held,1e-3);
        double[] down=drawn(FREE+DROP-.001f,far,rise);assertEquals(far,down[2],.01,"dropped to the wall's foot, where the server has them");assertEquals(rise,down[1],.01);
        // nowhere stepping: a sixtieth of a second moves them less than a block and a half even as they are flung (8 blocks in 0.15 s)
        double[] before=drawn(THROW,far,rise);
        for(float t=THROW+1/3f;t<FREE+DROP;t+=1/3f){
            double[] now=drawn(t,far,rise);
            double step=Math.hypot(now[1]-before[1],now[2]-before[2]);
            // (jerked up as they are seized: a block a tick at first)
            assertTrue(step<(t>FLING&&t<IMPACT+.4f?4.2:t<THROW+3?.4:.25),"a frame's step of "+step+" at "+t);
            before=now;
        }
        // straight from where they hang to the wall
        double[] a=drawn(FLING,far,rise),m=drawn(FLING+1.5f,far,rise);
        double u=m[2]/(far+SINK);
        assertEquals(a[1]+(rise+STUCK_UP-a[1])*u,m[1],1e-6);
    }
}
