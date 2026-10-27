package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The pull scenes' timing (2.39.0, 2.40.0): reveals in order, rarer grades held longer, and every scene ends; the
 * ten-pull's opening (2.41.0): the last hourglass set home in her hands, her feet on the stool, the floor and the chair,
 * and nothing in it jumping from one 60 fps frame to the next.
 */
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
            assertTrue(GachaStage.length(g)<20*GachaStage.SECOND,"ten pulls end in under 20 s: "+GachaStage.length(g)/20);
        }
        var usual=new ArrayList<>(Collections.nCopies(10,Grade.R));usual.set(3,Grade.SR);usual.set(7,Grade.SSR);
        assertTrue(GachaStage.length(usual)<17.5f*GachaStage.SECOND,"a usual ten-pull: "+GachaStage.length(usual)/20);
    }
    @Test void theShelvesHoldFiveAHourglassEachLeftToRightTopRowFirst(){
        for(int i=0;i<10;i++){
            float[] a=GachaStage.shelf(i);
            if(i%5>0)assertTrue(a[0]<GachaStage.shelf(i-1)[0],"screen left to right runs toward -x");
            if(i>=5)assertTrue(a[1]<GachaStage.shelf(i-5)[1],"the second five sit on the lower shelf");
        }
    }
    @Test void theCupboardIsOneAndAHalfTimesThe240OneAndItsHourglassesWithIt(){
        // user requests: 2.39.0 had hourglasses at 0.78 of Bomi's, 0.34 m apart on a 2.0 m cupboard; 2.40.0 made it 1.55
        // times that with hourglasses at 1.25; 2.40.1 three times bigger again; 2.41.0 1.5 times the 2.40.0 one
        assertEquals(1.5f*1.55f,GachaCinema.CUPBOARD_SCALE,1e-4);
        assertEquals(1.5f*1.25f,GachaStage.BIG,1e-4);
        assertEquals(1.5f*.527f,GachaStage.shelf(0)[0]-GachaStage.shelf(1)[0],1e-3);
        assertTrue(GachaCinema.CUPBOARD_TOP>4.5f&&GachaCinema.CUPBOARD_TOP<5,"about 4.7 m to its top");
        // an hourglass fits between the shelves, and side by side on a shelf
        assertTrue(GachaCinema.SHELF_HIGH-GachaCinema.SHELF_LOW>.546f*GachaStage.BIG);
        assertTrue(GachaStage.shelf(0)[0]-GachaStage.shelf(1)[0]>.4f*GachaStage.BIG);
        // the lamp hangs above it
        assertTrue(GachaStage.LAMP_HIGH>GachaCinema.CUPBOARD_TOP+.33f*GachaCinema.CUPBOARD_SCALE);
    }
    @Test void theCameraNeverEntersTheCupboardTheStoolHerOrTheFloor(){
        float k=GachaCinema.CUPBOARD_SCALE,x=(.98f+.1f)*k,z0=GachaCinema.CUPBOARD_Z-.1f*k,z1=GachaCinema.CUPBOARD_Z+GachaCinema.CUPBOARD_DEPTH+.1f,top=GachaCinema.CUPBOARD_TOP+.4f*k;
        var f=new GachaCinema.Frame();
        for(var g:TENS)
            for(float t=0;t<=GachaStage.length(g);t+=1/3f){
                GachaStage.frame(f,g,t);float[] e=f.eye;
                assertTrue(e[1]>.3f,"above the floor at "+t);
                assertFalse(Math.abs(e[0])<x&&e[1]<top&&e[2]>z0&&e[2]<z1,"inside the cupboard at "+t+": "+Arrays.toString(e));
                assertFalse(Math.abs(e[0]-GachaCinema.STOOL_X)<GachaCinema.STOOL_HALF+.15f&&e[1]<GachaCinema.STOOL_TOP+.2f&&e[2]>GachaCinema.STOOL_BACK-2*GachaCinema.STOOL_STEP-.15f&&e[2]<GachaCinema.STOOL_BACK+.1f,"in the stool at "+t);
                // never within half a metre of her head or chest
                for(float y:new float[]{1.1f,1.65f}){float[] p=BomiRig.apply(f.bones,BomiRig.BODY,0,y,0);assertTrue(GachaStage.dist(e,p)>.5f,"in her face at "+t/20+": "+GachaStage.dist(e,p));}
            }
    }
    static final List<List<Grade>> TENS=List.of(Collections.nCopies(10,Grade.R),List.of(Grade.R,Grade.R,Grade.R,Grade.SR,Grade.R,Grade.SSR,Grade.R,Grade.R,Grade.R,Grade.R),List.of(Grade.SSR,Grade.R,Grade.R,Grade.R,Grade.R,Grade.R,Grade.R,Grade.R,Grade.R,Grade.SSR));
    @Test void theOpeningsBeatsComeInOrderBeforeSheLooksUp(){
        float[] t={GachaTen.SET,GachaTen.PUSHED,GachaTen.LET_GO,GachaTen.STEP_DOWN,GachaTen.TURNED,GachaTen.STEP_ONE,GachaTen.LANDED,GachaTen.WIPE,GachaTen.WALK,GachaTen.ARRIVED,GachaTen.SIT,GachaTen.SEATED,GachaStage.LIFT,GachaStage.ORBIT};
        for(int i=1;i<t.length;i++)assertTrue(t[i]>t[i-1],"beat "+i+" after beat "+(i-1));
        // she sets off for the chair as her hand comes down from her brow
        assertTrue(GachaTen.WALK>GachaTen.WIPED-.2f*GachaStage.SECOND&&GachaTen.WALK<=GachaTen.WIPED);
        assertTrue(GachaTen.TURN<GachaTen.TURNED&&GachaTen.TURN>=GachaTen.LET_GO,"she turns once she has let go");
        assertTrue(GachaStage.FIRST>=GachaStage.ORBIT+GachaStage.ORBIT_TIME,"the first lights once the camera has swung round");
    }
    @Test void sheSetsTheLastHourglassHomeWithBothHandsOnIt(){
        float[] home=GachaStage.shelf(9);float half=.546f*GachaStage.BIG/2,radius=.146f*GachaStage.BIG;
        float[] prev=null;
        for(float t=0;t<=GachaTen.SEATED;t+=1/3f){
            float[] c=t<GachaTen.PUSHED?GachaTen.carried(t):home;
            // never sunk into the shelf; never over the cupboard's doors below it (the counter's front edge)
            assertTrue(c[1]-half>=GachaCinema.SHELF_LOW-1e-3f,"on or above the shelf at "+t);
            if(prev!=null)assertTrue(GachaStage.dist(c,prev)<.03f,"it glides, never jumps, at "+t);
            prev=c;
            if(t<GachaTen.LET_GO-.05f*GachaStage.SECOND){
                float[] w=GachaStage.tenPose(TENS.get(0),t);
                for(int arm:new int[]{BomiRig.RIGHT_ARM,BomiRig.LEFT_ARM}){
                    float[] h=BomiRig.hand(w,arm);
                    float side=(float)Math.hypot(h[0]-c[0],h[2]-c[2]);
                    assertTrue(side<radius+.13f&&side>radius-.12f,"hand "+arm+" at its side at "+t/20+": "+side);
                    assertTrue(h[1]>c[1]-half-.05f&&h[1]<c[1],"hand "+arm+" low on it at "+t/20);
                }
            }
        }
        assertArrayEquals(new float[]{home[0],home[1],home[2]},GachaTen.carried(GachaTen.PUSHED),1e-4f,"home on the shelf");
        // and her face stays clear of it while she holds it
        for(float t=0;t<GachaTen.LET_GO;t+=1/3f){
            float[] w=GachaStage.tenPose(TENS.get(0),t),c=GachaTen.carried(Math.min(t,GachaTen.PUSHED));
            float[] chin=BomiRig.apply(w,BomiRig.HEAD,0,22.5f/16+.05f,.25f),brow=BomiRig.apply(w,BomiRig.HEAD,0,22.5f/16+.4f,.25f);
            for(float[] p:new float[][]{chin,brow})assertTrue(p[2]<c[2]-radius+.01f,"her face clear of it at "+t/20);
        }
    }
    @Test void herFeetAreOnTheStoolTheFloorAndThenTheChair(){
        var g=TENS.get(1);
        float[] w=GachaStage.tenPose(g,0);
        assertEquals(GachaCinema.STOOL_TOP,sole(w),.02f,"on the top step");
        w=GachaStage.tenPose(g,GachaTen.STEP_ONE);assertEquals(GachaCinema.STOOL_LOW,sole(w),.02f,"on the lower step");
        for(float t=GachaTen.LANDED+.3f*GachaStage.SECOND;t<GachaTen.SIT;t+=1/3f){
            w=GachaStage.tenPose(g,t);float y=sole(w);
            assertTrue(y>-.03f&&y<.06f,"on the floor at "+t/20+": "+y);
            float[] hip=BomiRig.apply(w,BomiRig.ROOT,0,BomiRig.HIP,0);
            assertFalse(Math.abs(hip[0]-GachaCinema.STOOL_X)<GachaCinema.STOOL_HALF+.1f&&hip[2]>GachaCinema.STOOL_BACK-2*GachaCinema.STOOL_STEP-.1f,"clear of the stool at "+t/20);
        }
        w=GachaStage.tenPose(g,GachaTen.SEATED);
        float[] hip=BomiRig.apply(w,BomiRig.ROOT,0,BomiRig.HIP,0);
        assertEquals(GachaCinema.SEAT+.03f,hip[1],.01f,"sitting on the cushion");
        assertEquals(0,hip[0],.01f);assertEquals(-.04f,hip[2],.02f);
        // after she looks up she is looking up at the cupboard
        w=GachaStage.tenPose(g,GachaStage.LIFT+GachaStage.LIFT_TIME);
        float[] eye=BomiRig.apply(w,BomiRig.HEAD,0,1.6f,0),front=BomiRig.apply(w,BomiRig.HEAD,0,1.6f,1);
        assertTrue(front[1]-eye[1]>.25f,"her face turned up");
    }
    /** The lowest sole: the bottom of either leg. */
    private static float sole(float[] w){
        float y=Float.MAX_VALUE;
        for(int leg:new int[]{BomiRig.RIGHT_LEG,BomiRig.LEFT_LEG})for(float z:new float[]{-.125f,.125f})y=Math.min(y,BomiRig.apply(w,leg,BomiRig.PIVOT[leg][0]/16,0,z)[1]);
        return y;
    }
    @Test void nothingInTheTenPullJumpsFromOneFrameToTheNext(){
        // at 60 fps (a third of a tick a frame): every part of her, her hands, the hourglass she carries and the camera
        // move steadily, and never change speed in one frame more than a gentle push would
        for(var g:TENS){
            var f=new GachaCinema.Frame();float[][] last=null,lastStep=null;
            float end=GachaStage.FIRST;
            for(float t=0;t<=end;t+=1/3f){
                GachaStage.frame(f,g,t);
                // points on her: her face, chest, hips, hands and soles
                float[][] pts=new float[12][];int n=0;
                pts[n++]=BomiRig.apply(f.bones,BomiRig.HEAD,0,1.65f,.25f);pts[n++]=BomiRig.apply(f.bones,BomiRig.BODY,0,1.1f,.13f);pts[n++]=BomiRig.apply(f.bones,BomiRig.ROOT,0,BomiRig.HIP,0);
                pts[n++]=BomiRig.hand(f.bones,BomiRig.RIGHT_ARM);pts[n++]=BomiRig.hand(f.bones,BomiRig.LEFT_ARM);
                for(int leg:new int[]{BomiRig.RIGHT_LEG,BomiRig.LEFT_LEG})pts[n++]=BomiRig.apply(f.bones,leg,BomiRig.PIVOT[leg][0]/16,0,0);
                pts[n++]=new float[]{f.place[9][3],f.place[9][7],f.place[9][11]};
                pts[n++]=f.eye.clone();pts[n++]=f.target.clone();
                if(last!=null){
                    float[][] step=new float[n][];
                    for(int i=0;i<n;i++){
                        step[i]=new float[]{pts[i][0]-last[i][0],pts[i][1]-last[i][1],pts[i][2]-last[i][2]};
                        float v=(float)Math.sqrt(step[i][0]*step[i][0]+step[i][1]*step[i][1]+step[i][2]*step[i][2]);
                        // (the camera's swing round her is a quick pan: fast, but as smooth as the rest)
                        assertTrue(v<(i>=n-2?.25f:.09f),"point "+i+" leaps "+v+" m in one frame at "+t/20+" s");
                        if(lastStep!=null){float ax=step[i][0]-lastStep[i][0],ay=step[i][1]-lastStep[i][1],az=step[i][2]-lastStep[i][2];
                            assertTrue(Math.sqrt(ax*ax+ay*ay+az*az)<.02f,"point "+i+" jerks at "+t/20+" s");}
                    }
                    lastStep=step;
                }
                last=pts;
            }
        }
    }
    @Test void theLampFollowsHerFromTheStoolToTheChair(){
        var g=TENS.get(0);var f=new GachaCinema.Frame();
        for(float t=GachaStage.SECOND*.5f;t<GachaStage.LIFT;t+=1/3f){
            GachaStage.frame(f,g,t);
            float[] hip=BomiRig.apply(f.bones,BomiRig.ROOT,0,BomiRig.HIP,0);
            assertTrue(Math.hypot(hip[0]-f.spotPos[0],hip[2]-f.spotPos[2])<.75f,"in the lamp's pool at "+t/20);
            // the beam stays clear of the cupboard's crown
            assertTrue(f.spotPos[2]<GachaCinema.CUPBOARD_Z-.3f);
        }
    }
    @Test void afterTheSwingTheWholeCupboardStandsOverHerInTheFrame(){
        var g=new ArrayList<>(Collections.nCopies(10,Grade.R));g.set(3,Grade.SR);g.set(5,Grade.SSR);
        var f=new GachaCinema.Frame();GachaStage.frame(f,g,GachaStage.ORBIT+GachaStage.ORBIT_TIME);
        var vp=new org.joml.Matrix4f().perspective((float)Math.toRadians(f.fov),1280/730f,.05f,90).lookAt(f.eye[0],f.eye[1],f.eye[2],f.target[0],f.target[1],f.target[2],0,1,0);
        float k=GachaCinema.CUPBOARD_SCALE,z=GachaCinema.CUPBOARD_Z;
        float[] head=screen(vp,0,1.45f,0),crown=screen(vp,0,GachaCinema.CUPBOARD_TOP+.33f*k,z),left=screen(vp,.98f*k,0,z),right=screen(vp,-.98f*k,0,z);
        assertTrue(head[1]>.7f&&head[1]<.97f&&Math.abs(head[0]-.5f)<.15f,"her head low in the middle: "+Arrays.toString(head));
        assertTrue(crown[1]>0&&crown[1]<.2f,"the crown in at the top: "+Arrays.toString(crown));
        for(float[] c:new float[][]{left,right})assertTrue(c[0]>0&&c[0]<1&&c[1]<1,"its foot in the frame: "+Arrays.toString(c));
        assertTrue(crown[1]<head[1]-.5f,"it towers over her");
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
