package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.loot.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LootRulesTest {
    @Test void cumulativeRespawnIsExactlyThirtySixtyOneHundred(){
        int one=0,two=0,three=0;
        for(int roll=0;roll<100;roll++)for(int position=0;position<60;position++){
            int t=LootRules.respawnTicks(roll,position);assertTrue(t>=100&&t<=3600);
            if(t<=1200)one++;if(t<=2400)two++;if(t<=3600)three++;
        }
        assertEquals(1800,one);assertEquals(3600,two);assertEquals(6000,three);
        assertEquals(1200,LootRules.respawnTicks(29,59));assertEquals(1220,LootRules.respawnTicks(30,0));
        assertEquals(2400,LootRules.respawnTicks(59,59));assertEquals(2420,LootRules.respawnTicks(60,0));assertEquals(3600,LootRules.respawnTicks(99,59));
    }
    @Test void IndependentItemRollsHaveExactMarginalRates(){
        int fruit=0,key=0,both=0,empty=0;
        for(int a=0;a<100;a++)for(int b=0;b<100;b++){
            int drops=LootRules.drops(false,a,b);if((drops&1)!=0)fruit++;if((drops&2)!=0)key++;if(drops==3)both++;if(drops==0)empty++;
        }
        assertEquals(5000,fruit);assertEquals(1000,key);assertEquals(500,both);assertEquals(4500,empty);
    }
    @Test void TreasureHasOneGuaranteedFruitAndFiftyPercentMoreBaseRewards(){
        int chests=0;for(int i=0;i<100;i++)if(LootRules.treasure(i))chests++;assertEquals(20,chests);
        for(int i=0;i<100;i++)for(int j=0;j<100;j++)assertEquals(LootRules.FRUIT,LootRules.drops(true,i,j));
        assertEquals(10,LootRules.money(false));assertEquals(15,LootRules.money(true));assertEquals(10,LootRules.xp(false));assertEquals(15,LootRules.xp(true));
    }
    @Test void ClaimIsExclusiveAndCycleRejectsOldRequests(){
        var state=new LootState();state.put(0,new LootState.Slot(false,3,7,-1,-1));
        assertFalse(state.claim(0,6,100,1200));assertTrue(state.claim(0,7,100,1200));assertFalse(state.claim(0,7,101,1200));
        assertEquals(1300,state.slot(0).respawnAt());assertFalse(state.slot(0).due(1299));assertTrue(state.slot(0).due(1300));
        state.put(0,new LootState.Slot(true,1,8,-1,-1));assertFalse(state.claim(0,7,200,100));assertTrue(state.claim(0,8,200,100));
    }
    @Test void SaveRetainsCountdownOpeningDropsAndCycle(){
        var s=new LootState();s.put(2,new LootState.Slot(false,3,32,98100,95400));s.put(7,new LootState.Slot(true,1,9,-1,-1));
        var reloaded=LootState.read(s.writeNbt(new NbtCompound()));assertEquals(s.slot(2),reloaded.slot(2));assertEquals(s.slot(7),reloaded.slot(7));
        assertFalse(reloaded.claim(2,32,96000,100));assertTrue(reloaded.slot(2).due(98100));assertTrue(reloaded.slot(7).available());
    }
    @Test void OnlyElevenOriginalFieldMarkersAreRegistered(){
        assertEquals(11,LootPositions.ALL.size());assertEquals(11,LootPositions.ALL.stream().distinct().count());
        assertEquals(11,java.util.stream.IntStream.range(0,11).mapToObj(LootPositions::id).distinct().count());
        assertFalse(LootPositions.ALL.contains(new net.minecraft.util.math.BlockPos(38,81,-19)));
        assertThrows(IllegalArgumentException.class,()->LootRules.respawnTicks(100,0));
        assertThrows(IllegalArgumentException.class,()->LootRules.drops(false,-1,0));
    }
}
