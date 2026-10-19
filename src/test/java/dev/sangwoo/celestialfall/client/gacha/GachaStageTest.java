package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The pull scenes' timing (2.39.0, 2.40.0): reveals in order, rarer grades held longer, and every scene ends. */
class GachaStageTest {
    @Test void aSinglePullRevealsItsColourBeforeItEnds(){
        for(var g:Grade.values()){
            float r=GachaStage.reveal(g),len=GachaStage.length(List.of(g));
            assertTrue(r>2*GachaStage.SECOND,"she looks into it first");
            assertTrue(r>GachaStage.GATHER,"the light gathers within the scene");
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
            assertTrue(GachaStage.length(g)<15*GachaStage.SECOND,"ten pulls end in under 15 s: "+GachaStage.length(g)/20);
        }
        var usual=new ArrayList<>(Collections.nCopies(10,Grade.R));usual.set(3,Grade.SR);usual.set(7,Grade.SSR);
        assertTrue(GachaStage.length(usual)<13.5f*GachaStage.SECOND,"a usual ten-pull: "+GachaStage.length(usual)/20);
    }
    @Test void theShelvesHoldFiveAHourglassEachLeftToRightTopRowFirst(){
        for(int i=0;i<10;i++){
            float[] a=GachaStage.shelf(i);
            if(i%5>0)assertTrue(a[0]<GachaStage.shelf(i-1)[0],"screen left to right runs toward -x");
            if(i>=5)assertTrue(a[1]<GachaStage.shelf(i-5)[1],"the second five sit on the lower shelf");
        }
    }
    @Test void theCupboardAndItsHourglassesAreBigger(){
        // user request (2.40.0): 2.39.0 had hourglasses at 0.78 of Bomi's, 0.34 m apart on a 2.0 m cupboard
        assertTrue(GachaStage.BIG>=1.2f);
        assertTrue(GachaStage.shelf(0)[0]-GachaStage.shelf(1)[0]>=.5f);
        assertTrue(GachaCinema.CUPBOARD_TOP>=3);
        // and an hourglass fits between the shelves
        assertTrue(GachaCinema.SHELF_HIGH-GachaCinema.SHELF_LOW>.546f*GachaStage.BIG);
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
    @Test void theLampFindsHerFirstAndTheHourglassesFillSlowlyAfterSheLooksUp(){
        var g=new ArrayList<>(Collections.nCopies(10,Grade.R));g.set(2,Grade.SR);g.set(8,Grade.SSR);
        var f=new GachaCinema.Frame();
        // the lamp is on her before she looks up, and the hourglasses are dark
        GachaStage.frame(f,g,GachaStage.LIFT-1);
        assertTrue(f.spotColor[0]>1,"the lamp shines on her");
        assertTrue(f.beam>0,"its beam shows");
        for(int i=0;i<10;i++)assertTrue(f.glow[i]<.02f,"hourglass "+i+" still dark");
        // the camera starts in front of her and ends behind her, the cupboard before it
        assertTrue(f.eye[2]>0,"in front of her");
        GachaStage.frame(f,g,GachaStage.length(g));
        assertTrue(f.eye[2]<0,"behind her at the end");
        // each fills over its ramp, frame by frame at 60 fps never jumping, and is full at its time
        for(int i=0;i<10;i++){
            float at=GachaStage.lightAt(g,i),ra=GachaStage.ramp(g.get(i)),prev=-1;
            for(float t=at-ra;t<=at;t+=1/3f){
                GachaStage.frame(f,g,t);
                if(g.get(i)!=Grade.SSR)assertTrue(f.glow[i]>=prev-1e-4f,"hourglass "+i+" fills steadily");
                if(prev>=0)assertTrue(Math.abs(f.glow[i]-prev)<.12f,"no sudden jump at "+i+": "+prev+" to "+f.glow[i]);
                prev=f.glow[i];
            }
            GachaStage.frame(f,g,at);
            assertTrue(f.glow[i]>.9f,"hourglass "+i+" is lit at its time");
        }
        // the first only starts to fill once she has raised her head
        assertTrue(GachaStage.lightAt(g,0)-GachaStage.ramp(g.get(0))>=GachaStage.LIFT+GachaStage.LIFT_TIME*.5f);
    }
    @Test void aSinglePullsCircleAndSparksBlazeAtTheReveal(){
        var f=new GachaCinema.Frame();
        for(var grade:Grade.values()){
            var g=List.of(grade);float r=GachaStage.reveal(grade);
            GachaStage.frame(f,g,r-GachaStage.GATHER);float before=f.circle;
            GachaStage.frame(f,g,r+2);
            assertTrue(f.circle>before+.3f,"the circle blazes at the reveal");
            assertTrue(f.sparks>.3f,"sparks circle the glass");
        }
    }
}
