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
    @Test void theArrowGoesInThenItsChoiceShowsThenItIsMade(){
        assertTrue(HIT>=20,"raised and held a moment first (a second or more)");
        assertTrue(REVEAL-HIT>=20,"a second or more taken into the body before its choice shows");
        assertTrue(FREE>REVEAL+15,"one it chose stands a moment as the stand comes out");
        assertTrue(DEATH_AT>REVEAL+18,"one it rejects falls before dying");
    }
}
