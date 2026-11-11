package dev.sangwoo.celestialfall.client.xchara;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.xchara.HackKnife.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The lit knife's rim and its glitch (2.115.0, X Chara's Q): its outline is the model's knife (make_knife.py's
 * measures: 24 units from the rod's end to the point, the blade 4.1 times as long as it is deep), gone round
 * counterclockwise, every point of it on the knife's edge with the way out of it pointing out; the glitch's cells cover
 * the knife and a little about it, the share asked of them showing; the light along the rim runs from the rod to the
 * point.
 */
class HackKnifeTest {
    @Test void itsMeasuresAreTheModels(){
        assertEquals(24,Y_TIP+Y_HEEL,1e-9,"24 units from the rod's end to the point");
        assertEquals(18.104,BLADE,.001);assertEquals(4.3746,DEPTH,.001);assertEquals(5.896,ROD_LEN,.001);assertEquals(.4442,SPINE_X,.001);
        assertEquals(1345/325.0,BLADE/DEPTH,1e-9,"the photo's blade");
        assertEquals(DEPTH,depthAt(Y_HEEL+.1),1e-9);assertEquals(0,depthAt(Y_TIP),1e-9);
        assertEquals(.88,depthAt(Y_HEEL+BLADE*.52)/DEPTH,.03,"closing in to 0.88 halfway (the photo)");
    }
    @Test void itsOutlineGoesRoundTheKnifeCounterclockwise(){
        assertTrue(area2(OUTLINE)>0,"counterclockwise");
        // its area: the blade (its depth summed along it, less the heel's lean) and the rod
        double blade=0;int n=4000;
        for(int i=0;i<n;i++){double y=Y_HEEL+BLADE*(i+.5)/n;blade+=depthAt(y)*BLADE/n;}
        blade-=DEPTH*DEPTH*LEAN*.5;
        double rod=2*ROD_R*(Y_HEEL+heelY(0));
        assertEquals(blade+rod,area2(OUTLINE)*.5,(blade+rod)*.02,"the knife's own area");
        double top=-1e9,bottom=1e9,right=-1e9,left=1e9;
        for(var p:OUTLINE){top=Math.max(top,p[1]);bottom=Math.min(bottom,p[1]);right=Math.max(right,p[0]);left=Math.min(left,p[0]);}
        assertEquals(Y_TIP,top,1e-9);assertEquals(-Y_HEEL,bottom,1e-9);assertEquals(SPINE_X,right,1e-9);assertEquals(SPINE_X-DEPTH,left,1e-6);
    }
    @Test void everyPointOfItIsOnTheKnifesEdgeAndItsWayOutPointsOut(){
        assertEquals(OUTLINE.length,MITER.length);
        for(int i=0;i<OUTLINE.length;i++){
            double[] p=OUTLINE[i],m=MITER[i];double len=Math.hypot(m[0],m[1]);
            assertTrue(len>=1-1e-9&&len<=1/.38+1e-9,"point "+i+" way out "+len);
            assertFalse(inKnife(p[0]+m[0]*.06,p[1]+m[1]*.06),"just out of point "+i+" is off the knife");
            assertTrue(inKnife(p[0]-m[0]*.06/len,p[1]-m[1]*.06/len)||inKnife(p[0]-m[0]*.02/len,p[1]-m[1]*.02/len),"just in from point "+i+" is on it");
        }
        // no two points apart by more than the blade's depth, none doubled
        for(int i=0;i<OUTLINE.length;i++){
            double[] a=OUTLINE[i],b=OUTLINE[(i+1)%OUTLINE.length];double d=Math.hypot(b[0]-a[0],b[1]-a[1]);
            assertTrue(d>.01,"points "+i+" apart");
        }
    }
    @Test void theRimIsAThinBandBesideTheKnifeAndItsGlowReachesPastIt(){
        assertTrue(RIM_LILAC<RIM&&RIM<DEPTH*.12,"the crisp rim well under the blade's depth: "+RIM);
        assertTrue(RIM+GLOW_REACH<DEPTH*.4,"the glow no wider than the knife");
    }
    @Test void theGlitchsCellsCoverTheKnifeAndALittleAboutIt(){
        int on=0,off=0;var seen=new HashSet<Integer>();
        for(var c:CELLS){
            assertTrue(seen.add(c.id()),"each cell once");
            if(c.on()){on++;assertTrue(inKnife(c.x(),c.y()));}
            else{
                off++;assertFalse(inKnife(c.x(),c.y()));
                boolean near=false;
                for(int k=0;k<64&&!near;k++){double turn=k*Math.PI/32;for(double r=.2;r<=1.3&&!near;r+=.1)near=inKnife(c.x()+Math.cos(turn)*r,c.y()+Math.sin(turn)*r);}
                assertTrue(near,"a cell off the knife is by it");
            }
        }
        double area=area2(OUTLINE)*.5/(CELL*CELL);
        assertEquals(area,on,area*.2,"as many cells on it as fit its area");
        assertTrue(off>=40&&off<on*2,"some about it: "+off);
    }
    @Test void theShareAskedOfTheCellsShows(){
        for(float share:new float[]{.2f,.55f}){
            int on=0,onShown=0,off=0,offShown=0;
            for(int slot=0;slot<60;slot++)for(var c:CELLS){
                boolean s=showing(c,slot,91,share);
                if(c.on()){on++;if(s)onShown++;}else{off++;if(s)offShown++;}
            }
            assertEquals(share,onShown/(float)on,.04f,"on the knife");
            assertEquals(share*.42f,offShown/(float)off,.04f,"fewer about it");
        }
        // reshuffled from slot to slot
        int same=0,all=0;
        for(var c:CELLS){if(showing(c,3,91,.5f)==showing(c,4,91,.5f))same++;all++;}
        assertTrue(same<all*.75,"not the same cells twice running");
        for(var c:CELLS)assertFalse(showing(c,3,91,0));
    }
    @Test void theLightAlongTheRimRunsFromTheRodToThePoint(){
        double was=-1;
        for(int i=0;i<20;i++){
            float clock=10*RUN+i*RUN/20f;double best=0,at=0;
            for(int k=0;k<=480;k++){double y=-Y_HEEL+(Y_TIP+Y_HEEL)*k/480.0;float v=running(y,clock);assertTrue(v>=0&&v<=1);if(v>best){best=v;at=y;}}
            assertTrue(best>.99,"somewhere on the knife at every moment");
            if(was>=0&&at>was)assertTrue(at-was<(Y_TIP+Y_HEEL)*.07,"moving on a twentieth of the knife each twentieth of its run");
            was=at;
        }
        // narrow: most of the rim is its own purple
        int lit=0;for(int k=0;k<480;k++)if(running(-Y_HEEL+(Y_TIP+Y_HEEL)*k/480.0,3.3f)>.2f)lit++;
        assertTrue(lit<480*.2,"a narrow light: "+lit);
    }
    @Test void coloursMix(){
        assertEquals(0x000000,mix(0x000000,0xffffff,0));assertEquals(0xffffff,mix(0x000000,0xffffff,1));assertEquals(0x808080,mix(0x000000,0xffffff,.5f));
        assertEquals(VIVID,mix(VIVID,WHITE,-1));
    }
}
