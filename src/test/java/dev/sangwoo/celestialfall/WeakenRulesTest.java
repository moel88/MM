package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.CloneRules;
import dev.sangwoo.celestialfall.xchara.XCharaRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.WeakenRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's T with the hack on, the weakening (2.126.0, user request: "바라보는 적에게 !!!(기존 !!!효과음 사용)가 뜨고
 * 머리 위에 보라색 글씨로 공격력 방어력이 25% 감소한다. 라고 뜨게 해주고 … 디버프는 20초 동안 지속 돼"; the user's
 * answers: cooldown "50초", "G·R처럼 가드로 막힘", "받는 피해 1.25배", the words "20초 내내"): its numbers and its timing.
 */
class WeakenRulesTest {
    @Test void itLastsTwentySecondsAndItsCooldownIsFiftyFromWhenItTook(){
        assertEquals(20*20,LASTS,"the user's 20 seconds");
        assertEquals(50*20,COOLDOWN,"the user's 50 seconds");
        assertEquals(1000+COOLDOWN,ready(1000));
        // (so it cannot be kept on one enemy without a gap)
        assertTrue(COOLDOWN>LASTS);
    }
    @Test void aQuarterOffWhatTheyDealAndAQuarterMoreOfWhatTheyTake(){
        assertEquals(.75f,DEALT,1e-6,"attack down 25%");
        assertEquals(1.25f,TAKEN,1e-6,"the user's answer: harm taken x1.25");
        assertEquals(10,harm(10,false,false),1e-6);
        assertEquals(7.5f,harm(10,true,false),1e-6,"one weakened deals three quarters");
        assertEquals(12.5f,harm(10,false,true),1e-6,"one weakened takes a quarter more");
        // one weakened striking another weakened: both
        assertEquals(10*.75f*1.25f,harm(10,true,true),1e-6);
        assertEquals(1,scale(false,false),1e-6);assertEquals(DEALT*TAKEN,scale(true,true),1e-6);
    }
    @Test void aGuardBlocksItThroughTheWarningAndItsUserIsHeldThatLong(){
        // the "!!!" some three quarters of a second, as G's and R's
        assertTrue(MARK>=0&&MARK<=2);assertTrue(APPLY>=12&&APPLY<=20,"some 0.75 s to guard: "+APPLY);
        assertTrue(Math.abs(APPLY-CloneRules.THROW)<=3&&Math.abs(APPLY-XCharaRules.WARN)<=4,"about as long as R's and G's");
        assertFalse(warning(-1));assertTrue(warning(0));assertTrue(warning(APPLY-1));assertFalse(warning(APPLY),"it has taken: too late to guard");
        for(int age=-2;age<APPLY+5;age++)assertEquals(warning(age),userHeld(age),"held just as long as it may be blocked, at "+age);
    }
    @Test void itsTimeRunsDownEvenly(){
        assertEquals(1,left(1400,1000),1e-6);assertEquals(.5f,left(1400,1200),1e-6);assertEquals(0,left(1400,1400),1e-6);assertEquals(0,left(1400,2000),1e-6);
        assertEquals(1,left(1400,900),1e-6,"never more than all of it");
    }
    @Test void itsKeysByteIsItsOwn(){
        assertEquals(6,XCharaRules.T);
        for(int other:new int[]{XCharaRules.G,XCharaRules.Q,XCharaRules.E,XCharaRules.R,XCharaRules.H})assertNotEquals(other,XCharaRules.T);
        // (2.126.0, user request: "q활성화y를 다시 q활성화 e로 옮기고") the restore's byte is E's again, the same byte
        assertEquals(3,XCharaRules.E);
    }
}
