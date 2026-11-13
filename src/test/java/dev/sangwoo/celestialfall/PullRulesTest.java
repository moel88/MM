package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.PullRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's Y with the hack on, the pull (2.127.0, user request: "상대에게 !!!가 뜨고 상대가 내 앞으로 순간이동 돼"; the
 * user's answers: "맞음, 이대로", "G·R·T처럼 가드로 막힘", "20초", "아무것도 없음"): the "!!!" comes at once and the pull a
 * third of a second after the press, as the video's sound has it; a guard blocks it until then; the one pulled stands
 * a body and a half before its user, on the way to where they stood; its cooldown is 20 seconds from when it is over.
 */
class PullRulesTest {
    @Test void theWarningComesAtOnceAndThePullAThirdOfASecondAfterThePress(){
        // the video: the "!!!" 0.07 s after the press, the pull 0.33 s after it (its burst of sound 0.33 s after its warning's start)
        assertEquals(1,PullRules.MARK);assertEquals(.33,PullRules.PULL/20.0,.03);
        assertTrue(PullRules.warning(0)&&PullRules.warning(PullRules.PULL-1));assertFalse(PullRules.warning(PullRules.PULL));assertFalse(PullRules.warning(-1));
        // its user stands held until the pull and is free with it (the user: nothing more comes of it)
        assertTrue(PullRules.userHeld(0)&&PullRules.userHeld(PullRules.PULL-1));assertFalse(PullRules.userHeld(PullRules.PULL));
    }
    @Test void itsCooldownIsTwentySecondsFromWhenItIsOver(){
        assertEquals(400,PullRules.COOLDOWN);
        assertEquals(1000+400,PullRules.ready(1000));
    }
    @Test void theOnePulledStandsBeforeItsUserOnTheWayToWhereTheyStood(){
        // a body and a half before its user (the video)
        assertEquals(1.5,PullRules.AHEAD,1e-9);
        double[] w=PullRules.way(0,0,6,0,0);assertEquals(1,w[0],1e-9);assertEquals(0,w[1],1e-9);
        w=PullRules.way(2,3,2,-5,0);assertEquals(0,w[0],1e-9);assertEquals(-1,w[1],1e-9);
        w=PullRules.way(0,0,3,4,0);assertEquals(.6,w[0],1e-9);assertEquals(.8,w[1],1e-9);
        // one standing on its user: the way its user faces (yaw 0 is +z, 90 is -x)
        w=PullRules.way(1,1,1,1,0);assertEquals(0,w[0],1e-9);assertEquals(1,w[1],1e-9);
        w=PullRules.way(1,1,1,1,90);assertEquals(-1,w[0],1e-9);assertEquals(0,w[1],1e-9);
        for(double a=0;a<360;a+=7){w=PullRules.way(0,0,Math.cos(a)*9,Math.sin(a)*9,33);assertEquals(1,Math.hypot(w[0],w[1]),1e-9);}
        // a block in the way: as far as the way is clear, never behind its user, never past the place
        assertEquals(1.5,PullRules.reach(1.5),1e-9);assertEquals(1.5,PullRules.reach(9),1e-9);
        assertEquals(.7,PullRules.reach(.7),1e-9);assertEquals(0,PullRules.reach(-.3),1e-9);
    }
    @Test void theFlickerAfterItIsHalfASecond(){assertEquals(.5,PullRules.GLITCH/20.0,1e-9);}
}
