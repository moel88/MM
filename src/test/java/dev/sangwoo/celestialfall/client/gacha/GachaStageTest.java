package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The pull scenes' timing (2.39.0): reveals in order, rarer grades held longer, and every scene ends. */
class GachaStageTest {
    @Test void aSinglePullRevealsItsColourBeforeItEnds(){
        for(var g:Grade.values()){
            float r=GachaStage.reveal(g),len=GachaStage.length(List.of(g));
            assertTrue(r>2*GachaStage.SECOND,"she looks into it first");
            assertTrue(len-r>=GachaStage.AFTER,"the colour shows before the fade");
            assertTrue(len<7*GachaStage.SECOND,"short enough to sit through: "+len/20);
        }
        assertTrue(GachaStage.reveal(Grade.R)<GachaStage.reveal(Grade.SR)&&GachaStage.reveal(Grade.SR)<GachaStage.reveal(Grade.SSR));
    }
    @Test void tenHourglassesLightInOrderAndTheSceneWaitsForTheLast(){
        var rng=new Random(3);
        for(int run=0;run<200;run++){
            var g=new ArrayList<Grade>();for(int i=0;i<10;i++)g.add(Grade.values()[rng.nextInt(3)]);
            float prev=0;
            for(int i=0;i<10;i++){float at=GachaStage.lightAt(g,i);assertTrue(at>prev,"in order");prev=at;}
            assertTrue(GachaStage.length(g)>=prev+GachaStage.HOLD_TEN);
            assertTrue(GachaStage.length(g)<14*GachaStage.SECOND,"ten pulls end in under 14 s");
        }
    }
    @Test void theShelvesHoldFiveAHourglassEachLeftToRightTopRowFirst(){
        for(int i=0;i<10;i++){
            float[] a=GachaStage.shelf(i);
            if(i%5>0)assertTrue(a[0]<GachaStage.shelf(i-1)[0],"screen left to right runs toward -x");
            if(i>=5)assertTrue(a[1]<GachaStage.shelf(i-5)[1],"the second five sit on the lower shelf");
        }
    }
    @Test void sheWatchesEachHourglassBeforeItLights(){
        var g=new ArrayList<>(Collections.nCopies(10,Grade.R));g.set(6,Grade.SSR);
        for(int i=0;i<10;i++){
            float[] w=GachaStage.watch(g,GachaStage.lightAt(g,i)),s=GachaStage.shelf(i);
            assertEquals(s[0],w[0],1e-4,"looking at "+i+" as it lights");
        }
        float[] end=GachaStage.watch(g,GachaStage.length(g)),ssr=GachaStage.shelf(6);
        assertEquals(ssr[0],end[0],1e-4,"and at the SSR at the end");
    }
}
