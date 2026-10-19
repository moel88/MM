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
    @Test void theCupboardIsThreeTimesTheSizeItWasAndItsHourglassesWithIt(){
        // user requests: 2.39.0 had hourglasses at 0.78 of Bomi's, 0.34 m apart on a 2.0 m cupboard; 2.40.0 made it 1.55
        // times that with hourglasses at 1.25; 2.40.1 three times bigger again
        assertTrue(GachaCinema.CUPBOARD_SCALE>=3*1.55f-1e-4f);
        assertTrue(GachaStage.BIG>=3*1.25f-1e-4f);
        assertTrue(GachaStage.shelf(0)[0]-GachaStage.shelf(1)[0]>=3*.527f-1e-3f);
        assertTrue(GachaCinema.CUPBOARD_TOP>=9);
        // an hourglass fits between the shelves, and side by side on a shelf
        assertTrue(GachaCinema.SHELF_HIGH-GachaCinema.SHELF_LOW>.546f*GachaStage.BIG);
        assertTrue(GachaStage.shelf(0)[0]-GachaStage.shelf(1)[0]>.4f*GachaStage.BIG);
        // the lamp hangs above it
        assertTrue(GachaStage.LAMP_HIGH>GachaCinema.CUPBOARD_TOP+.33f*GachaCinema.CUPBOARD_SCALE);
    }
    @Test void theCameraNeverEntersTheCupboardOrTheFloor(){
        float k=GachaCinema.CUPBOARD_SCALE,x=(.98f+.1f)*k,z0=GachaCinema.CUPBOARD_Z-.1f*k,z1=GachaCinema.CUPBOARD_Z+GachaCinema.CUPBOARD_DEPTH+.1f,top=GachaCinema.CUPBOARD_TOP+.4f*k;
        var f=new GachaCinema.Frame();
        for(var g:List.of(Collections.nCopies(10,Grade.R),List.of(Grade.R,Grade.R,Grade.R,Grade.SR,Grade.R,Grade.SSR,Grade.R,Grade.R,Grade.R,Grade.R),List.of(Grade.SSR,Grade.R,Grade.R,Grade.R,Grade.R,Grade.R,Grade.R,Grade.R,Grade.R,Grade.SSR)))
            for(float t=0;t<=GachaStage.length(g);t+=1/3f){
                GachaStage.frame(f,g,t);float[] e=f.eye;
                assertTrue(e[1]>.3f,"above the floor at "+t);
                assertFalse(Math.abs(e[0])<x&&e[1]<top&&e[2]>z0&&e[2]<z1,"inside the cupboard at "+t+": "+Arrays.toString(e));
            }
    }
    @Test void afterTheSwingTheWholeCupboardStandsOverHerInTheFrame(){
        var g=new ArrayList<>(Collections.nCopies(10,Grade.R));g.set(3,Grade.SR);g.set(5,Grade.SSR);
        var f=new GachaCinema.Frame();GachaStage.frame(f,g,GachaStage.ORBIT+GachaStage.ORBIT_TIME);
        var vp=new org.joml.Matrix4f().perspective((float)Math.toRadians(f.fov),1280/730f,.05f,90).lookAt(f.eye[0],f.eye[1],f.eye[2],f.target[0],f.target[1],f.target[2],0,1,0);
        float k=GachaCinema.CUPBOARD_SCALE,z=GachaCinema.CUPBOARD_Z;
        float[] head=screen(vp,0,1.45f,0),crown=screen(vp,0,GachaCinema.CUPBOARD_TOP+.33f*k,z),left=screen(vp,.98f*k,0,z),right=screen(vp,-.98f*k,0,z);
        assertTrue(head[1]>.75f&&head[1]<.97f&&Math.abs(head[0]-.5f)<.15f,"her head low in the middle: "+Arrays.toString(head));
        assertTrue(crown[1]>0&&crown[1]<.15f,"the crown in at the top: "+Arrays.toString(crown));
        for(float[] c:new float[][]{left,right})assertTrue(c[0]>0&&c[0]<1&&c[1]<1,"its foot in the frame: "+Arrays.toString(c));
        assertTrue(crown[1]<head[1]-.6f,"it towers over her");
    }
    private static float[] screen(org.joml.Matrix4f vp,float x,float y,float z){
        var v=new org.joml.Vector4f(x,y,z,1).mul(vp);return new float[]{v.x/v.w*.5f+.5f,.5f-v.y/v.w*.5f};
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
