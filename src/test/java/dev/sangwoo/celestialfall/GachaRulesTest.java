package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.gacha.GachaRules;
import dev.sangwoo.celestialfall.gacha.GachaRules.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The gacha's odds and guarantees as the user set them (2.39.0). */
class GachaRulesTest {
    @Test void eachPullIsSsrOnePercentSrFivePercentRTheRest(){
        int[] seen=new int[3];
        for(int roll=0;roll<GachaRules.ODDS;roll++)seen[GachaRules.pull(Count.NEW,roll).ordinal()]++;
        assertEquals(100,seen[Grade.SSR.ordinal()]);
        assertEquals(500,seen[Grade.SR.ordinal()]);
        assertEquals(9400,seen[Grade.R.ordinal()]);
    }
    @Test void everyTenPullHasAnSrOrBetterWhereverTheCountStands(){
        for(int since=0;since<GachaRules.SR_PITY;since++){
            var r=GachaRules.pulls(new Count(0,since,0),10,()->GachaRules.ODDS-1);
            assertTrue(r.any(Grade.SR)||r.any(Grade.SSR),"count "+since);
            assertEquals(GachaRules.SR_PITY-since,r.grades().indexOf(Grade.SR)+1,"the guarantee lands on the tenth pull since the last SR");
        }
    }
    @Test void theHundredthPullSinceTheLastSsrIsAnSsr(){
        var r=GachaRules.pulls(Count.NEW,100,()->GachaRules.ODDS-1);
        assertEquals(Grade.SSR,r.grades().get(99));
        assertEquals(1,Collections.frequency(r.grades(),Grade.SSR));
        assertEquals(new Count(0,0,100),r.after());
        // SR guarantees still came every ten pulls on the way
        for(int i=9;i<99;i+=10)assertEquals(Grade.SR,r.grades().get(i),"pull "+(i+1));
    }
    @Test void anSsrAlsoCountsAsTheSrGuaranteeAndResetsBoth(){
        var c=new Count(98,8,40).after(Grade.SSR);
        assertEquals(new Count(0,0,41),c);
        assertEquals(100,c.toSsr());assertEquals(10,c.toSr());
        var sr=new Count(5,9,10).after(Grade.SR);
        assertEquals(new Count(6,0,11),sr);
    }
    @Test void countsStayInRange(){
        var c=new Count(500,-3,-1);
        assertEquals(99,c.sinceSsr());assertEquals(0,c.sinceSr());assertEquals(0,c.pulls());
        assertEquals(1,c.toSsr());
        assertEquals(Integer.MAX_VALUE,new Count(0,0,Integer.MAX_VALUE).after(Grade.R).pulls());
    }
    @Test void ratesHoldOverManyRandomPulls(){
        var rng=new Random(7);var r=GachaRules.pulls(Count.NEW,200_000,()->rng.nextInt(GachaRules.ODDS));
        double ssr=Collections.frequency(r.grades(),Grade.SSR)/200_000.0,sr=Collections.frequency(r.grades(),Grade.SR)/200_000.0;
        // the guarantees lift the plain 1% and 5% to 1.58% and 11.72% over the long run (GachaRules.LONG_RUN)
        assertEquals(GachaRules.LONG_RUN[Grade.SSR.ordinal()],ssr,.002,"SSR");
        assertEquals(GachaRules.LONG_RUN[Grade.SR.ordinal()],sr,.004,"SR");
    }
    @Test void theLongRunSharesShownWithTheOddsAreExact(){
        // how often a player stands at each count over the long run, and what each count's pull gives
        int a=GachaRules.SSR_PITY,b=GachaRules.SR_PITY,n=a*b;double[][] odds=new double[n][3];
        for(int i=0;i<n;i++){var c=new Count(i/b,i%b,0);for(int roll=0;roll<GachaRules.ODDS;roll++)odds[i][GachaRules.pull(c,roll).ordinal()]+=1.0/GachaRules.ODDS;}
        double[] at=new double[n],share=new double[3];at[0]=1;
        for(int step=0;step<4000;step++){
            double[] next=new double[n];
            for(int i=0;i<n;i++)for(var g:Grade.values()){
                double w=at[i]*odds[i][g.ordinal()];if(w==0)continue;
                var c=new Count(i/b,i%b,0).after(g);next[c.sinceSsr()*b+c.sinceSr()]+=w;
                if(step>=2000)share[g.ordinal()]+=w/2000;
            }
            at=next;
        }
        for(var g:Grade.values())assertEquals(GachaRules.LONG_RUN[g.ordinal()],share[g.ordinal()],5e-5,g.name());
    }
    @Test void eachPlayersCountIsKeptWithTheWorld(){
        var s=new dev.sangwoo.celestialfall.gacha.GachaState();var a=UUID.randomUUID();var b=UUID.randomUUID();
        s.set(a,new Count(87,3,412));s.set(b,new Count(0,9,9));
        var copy=dev.sangwoo.celestialfall.gacha.GachaState.read(s.writeNbt(new net.minecraft.nbt.NbtCompound()));
        assertEquals(new Count(87,3,412),copy.get(a));assertEquals(new Count(0,9,9),copy.get(b));
        assertEquals(Count.NEW,copy.get(UUID.randomUUID()),"a new player starts from nothing");
    }
    @Test void pullsAreSpacedOnTheServer(){
        long[] last=GachaRules.never();
        assertFalse(GachaRules.tooSoon(0,last,10),"the first pull goes");
        last[0]=last[1]=100;
        assertTrue(GachaRules.tooSoon(101,last,0),"even reading the count waits a moment");
        assertTrue(GachaRules.tooSoon(105,last,1),"a second pull right away is refused");
        assertFalse(GachaRules.tooSoon(105,last,0),"the count can be read");
        assertFalse(GachaRules.tooSoon(110,last,1),"half a second later it goes");
    }
    @Test void onlyAPlayerWithoutAStandPullsAndAnSsrGivesKageno(){
        assertTrue(GachaRules.canPull("none"));
        assertFalse(GachaRules.canPull("kageno"));
        assertEquals("kageno",GachaRules.SSR_STAND);
        assertTrue(dev.sangwoo.celestialfall.progression.StandStats.known(GachaRules.SSR_STAND));
    }
}
