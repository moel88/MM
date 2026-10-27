package dev.sangwoo.celestialfall.stand;

import java.util.HashMap;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.StandArrowRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The stand arrow's rules (2.70.0, user request: "20% 확률로 사망 할 확률이 있어 80%확률로 스타플래티나 추후에 스탠드가
 * 추가되면 스타플래티나의 확률은 조정 할거야").
 */
class StandArrowRulesTest {
    @Test void oneInFiveDiesAndTheRestAwakenStarPlatinum(){
        assertTrue(whole(),"the table makes up the whole");
        assertEquals(100,ODDS);assertEquals(20,DEATH);
        assertEquals(20,share(DIE));assertEquals(80,share("star_platinum"));assertEquals(0,share("kageno"),"Kageno is the gacha's, not the arrow's");
        for(int r=0;r<ODDS;r++)assertEquals(r<20?DIE:"star_platinum",outcome(r),"roll "+r);
        assertThrows(IllegalArgumentException.class,()->outcome(-1));
        assertThrows(IllegalArgumentException.class,()->outcome(ODDS));
    }
    @Test void theRollsComeOutAtTheirShares(){
        var random=new Random(70);var seen=new HashMap<String,Integer>();int n=200_000;
        for(int i=0;i<n;i++)seen.merge(roll(()->random.nextInt(ODDS)),1,Integer::sum);
        assertEquals(.20,seen.get(DIE)/(double)n,.005,"death");
        assertEquals(.80,seen.get("star_platinum")/(double)n,.005,"Star Platinum");
        assertEquals(2,seen.size(),"nothing else");
    }
    @Test void onlyOneWithNoStandMayUseIt(){
        assertTrue(canUse("none"));
        assertFalse(canUse("kageno"));assertFalse(canUse("star_platinum"));
    }
    /** About ten seconds from the use to what the arrow does (2.71.0, user request: "화살 꽂는 모션이 10초는 됐으면 좋겠어"). */
    @Test void theArrowGoesInThenItsChoiceShowsThenItIsMade(){
        assertTrue(HIT>=100,"looked at, doubted and raised high for five seconds first");
        assertTrue(REVEAL-HIT>=60,"three seconds and more on the knees as it is taken in before its choice shows");
        assertTrue(REVEAL>=170&&REVEAL<=200,"its choice shows at about nine seconds");
        assertTrue(FREE>=REVEAL+30&&FREE<=240,"one it chose gets up as the stand comes out, free at about eleven seconds");
        assertTrue(DEATH_AT>REVEAL+18&&DEATH_AT<FREE,"one it rejects falls before dying, at about ten seconds");
    }
}
