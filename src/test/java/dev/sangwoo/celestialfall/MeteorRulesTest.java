package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.commerce.TradeRules;
import dev.sangwoo.celestialfall.meteor.*;
import dev.sangwoo.celestialfall.progression.ProgressProfile;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MeteorRulesTest {
    @Test void eachEmptyPlaceFallsFivePercentAMinute(){
        int falls=0;for(int roll=0;roll<100;roll++)if(MeteorRules.falls(roll))falls++;
        assertEquals(5,falls);assertEquals(10,MeteorRules.SLOTS);assertEquals(1200,MeteorRules.ROLL_TICKS);
        assertThrows(IllegalArgumentException.class,()->MeteorRules.falls(100));assertThrows(IllegalArgumentException.class,()->MeteorRules.falls(-1));
    }
    @Test void aRockLiesFiveMinutesAfterItLands(){
        assertEquals(1072,MeteorRules.landing(1000));assertEquals(1072+6000,MeteorRules.expiry(1000));
        var m=new MeteorState.Meteor(3,7,1,2,3,4,5,6,1000,42,3,0);
        assertFalse(m.landed(1071));assertTrue(m.landed(1072));assertFalse(m.expired(7071));assertTrue(m.expired(7072));
    }
    @Test void threeFragmentsOfFiveBlowsEach(){
        var rock=MeteorRules.Rock.whole();int blows=0,fragments=0;
        while(!rock.depleted()){var next=rock.hit();blows++;if(next.brokeFrom(rock))fragments++;rock=next;}
        assertEquals(15,blows);assertEquals(3,fragments);assertEquals(1.0,rock.mined(),1e-9);
        assertThrows(IllegalStateException.class,rock::hit);
        var r=MeteorRules.Rock.whole();for(int i=0;i<4;i++){var n=r.hit();assertFalse(n.brokeFrom(r));r=n;}
        assertEquals(4,r.hits());var off=r.hit();assertTrue(off.brokeFrom(r));assertEquals(2,off.fragments());assertEquals(0,off.hits());
        assertThrows(IllegalArgumentException.class,()->new MeteorRules.Rock(4,0));assertThrows(IllegalArgumentException.class,()->new MeteorRules.Rock(2,5));
    }
    @Test void theFallComesDownAtItsAngleFromItsSide(){
        double[] s=MeteorRules.start(10,80,20,0,45);
        assertEquals(10,s[0],1e-9);assertEquals(80+MeteorRules.FALL_HEIGHT,s[1],1e-9);assertEquals(20+MeteorRules.FALL_HEIGHT,s[2],1e-6);
        // from 90 (a player facing west to look back up it): it comes from the west
        double[] w=MeteorRules.start(0,0,0,Math.toRadians(90),60);
        assertTrue(w[0]<0);assertEquals(0,w[2],1e-9);assertEquals(MeteorRules.FALL_HEIGHT/Math.tan(Math.toRadians(60)),-w[0],1e-9);
    }
    @Test void stateKeepsEveryMeteorAndItsCountdownAcrossSaves(){
        var state=new MeteorState();
        assertEquals(0,state.firstEmpty());
        var a=new MeteorState.Meteor(0,state.nextCycle(),1.5,81,2.5,40,231,-90,500,99,3,0);
        var b=new MeteorState.Meteor(4,state.nextCycle(),-30.5,81,60.5,-30,231,180,900,-7,1,3);
        state.put(a);state.put(b);state.nextRoll(1700);
        assertEquals(1,state.firstEmpty());assertNotEquals(a.cycle(),b.cycle());assertNotEquals(a.uuid(),b.uuid());
        var loaded=MeteorState.read(state.writeNbt(new NbtCompound()));
        assertEquals(a,loaded.slot(0));assertEquals(b,loaded.slot(4));assertEquals(1700,loaded.nextRoll());assertEquals(2,loaded.all().size());
        assertTrue(loaded.nextCycle()>b.cycle());
        loaded.clear(0);assertNull(loaded.slot(0));assertEquals(0,loaded.firstEmpty());
    }
    @Test void theCrafterTakesThreeFragmentsAndAStick(){
        assertTrue(MeteorRules.canCraft(3,1));assertFalse(MeteorRules.canCraft(2,5));assertFalse(MeteorRules.canCraft(9,0));
        var p=new ProgressProfile(1,0,0,"kageno",0,0);
        var made=TradeRules.craft(p,3,1,true);assertTrue(made.ok());assertEquals(TradeRules.CRAFT_XP,made.profile().xp());assertEquals(0,made.profile().money());
        assertFalse(TradeRules.craft(p,2,1,true).ok());assertSame(p,TradeRules.craft(p,2,1,true).profile());
        assertFalse(TradeRules.craft(p,3,0,true).ok());assertFalse(TradeRules.craft(p,3,1,false).ok());
    }
    @Test void theSellerSellsThePickaxeForFiveThousand(){
        var poor=new ProgressProfile(1,0,0,"none",4999,0);
        assertFalse(TradeRules.buy(poor,TradeRules.PICKAXE,true).ok());assertSame(poor,TradeRules.buy(poor,TradeRules.PICKAXE,true).profile());
        var rich=poor.withMoney(5075);
        var bought=TradeRules.buy(rich,TradeRules.PICKAXE,true);assertTrue(bought.ok());assertEquals(75,bought.profile().money());assertEquals(TradeRules.PICKAXE_XP,bought.profile().xp());
        assertFalse(TradeRules.buy(rich,TradeRules.PICKAXE,false).ok());
        assertEquals(TradeRules.BUY_PRICE,TradeRules.price(TradeRules.FRUIT));assertEquals("5,000원",TradeRules.won(TradeRules.PICKAXE_PRICE));
    }
}
