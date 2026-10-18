package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.commerce.TradeRules;
import dev.sangwoo.celestialfall.progression.*;
import net.minecraft.nbt.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommerceTest {
    @Test void exactPricesAndSuccessRewards(){
        var p=new ProgressProfile(1,2,3,"kageno",75,95);
        var buy=TradeRules.trade(p,true,false,true);assertTrue(buy.ok());assertEquals(0,buy.profile().money());assertEquals(2,buy.profile().level());assertEquals(5,buy.profile().xp());
        var sell=TradeRules.trade(buy.profile(),false,true,false);assertTrue(sell.ok());assertEquals(50,sell.profile().money());assertEquals(10,sell.profile().xp());assertEquals("kageno",sell.profile().stand());
    }
    @Test void rejectedTransactionsNeverChangeMoneyXpOrLevel(){
        var poor=new ProgressProfile(1,0,0,"none",74,99);
        assertSame(poor,TradeRules.trade(poor,true,false,true).profile());
        var rich=poor.withMoney(75);assertFalse(TradeRules.trade(rich,true,true,false).ok());assertSame(rich,TradeRules.trade(rich,true,true,false).profile());
        assertSame(rich,TradeRules.trade(rich,false,false,true).profile());
        var cap=rich.withMoney(ProgressProfile.MAX_MONEY);assertSame(cap,TradeRules.trade(cap,false,true,true).profile());
    }
    @Test void growthOverflowCapsAtFiftyAndRebirthKeepsWallet(){
        var p=new ProgressProfile(49,5,1,"kageno",700,0).grantXp(Integer.MAX_VALUE);
        assertEquals(50,p.level());assertEquals(0,p.xp());assertEquals(700,p.rebirth().money());assertEquals(0,p.rebirth().xp());assertEquals(1,p.rebirth().level());
        assertSame(p,p.grantXp(10));assertEquals(50,TradeRules.trade(p,true,false,true).profile().level());
    }
    @Test void resettingOnlyStandPreservesProgressAndMoney(){
        var p=new ProgressProfile(23,9,2,"kageno",850,75);var reset=p.withStand("none");
        assertEquals(new ProgressProfile(23,9,2,"none",850,75),reset);assertEquals(75,p.withRebirths(10).xp());assertEquals(0,p.withLevel(15).xp());
    }
    @Test void walletAndXpSurviveNbtAndOldSavesUpgradeWithoutInventingMoney(){
        var state=new ProgressState();var id=UUID.randomUUID();var p=new ProgressProfile(17,3,4,"kageno",98765,412);state.set(id,p);
        assertEquals(p,ProgressState.read(state.writeNbt(new NbtCompound())).profile(id));
        var saved=state.writeNbt(new NbtCompound());var row=saved.getList("Players",NbtElement.COMPOUND_TYPE).getCompound(0);row.remove("Money");row.remove("Xp");
        var old=ProgressState.read(saved).profile(id);assertEquals(0,old.money());assertEquals(0,old.xp());assertEquals(17,old.level());
    }
    @Test void corruptAndExtremeStateStaysBounded(){
        var p=new ProgressProfile(1,0,0,"none",Long.MAX_VALUE,Integer.MAX_VALUE);assertEquals(ProgressProfile.MAX_MONEY,p.money());assertEquals(99,p.xp());
        assertEquals(0,p.withMoney(Long.MIN_VALUE).money());assertEquals(1,p.grantXp(-10).level());
    }
    @Test void intentionalResetCannotReequipFromAnOldSwordAfterRejoin(){
        var state=new ProgressState();var id=UUID.randomUUID();
        assertFalse(state.standCleared(id));
        state.set(id,new ProgressProfile(8,2,1,"kageno",600,30));
        state.set(id,state.profile(id).withStand("none"));assertTrue(state.standCleared(id));
        var loaded=ProgressState.read(state.writeNbt(new NbtCompound()));assertTrue(loaded.standCleared(id));
        loaded.set(id,loaded.profile(id).withStand("kageno"));assertFalse(loaded.standCleared(id));
        assertEquals(600,loaded.profile(id).money());assertEquals(30,loaded.profile(id).xp());
    }

}
