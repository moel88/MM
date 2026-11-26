package dev.sangwoo.celestialfall.client.samurai;

import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.SLASH_SEC;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the samurai's G draws (2.143.0): its stars and its ink come within the flourish, at the video's moments; the light
 * off the blade only while it lies at the scabbard's mouth; its camera's jolts at its blows and nowhere else; and the
 * character for death is the seven strokes of it, within its square.
 */
class OneFlashTest {
    @Test void theStarsAndTheInkComeWithinTheFlourish(){
        float last=0;
        for(float[] g:OneFlash.STARS){
            assertEquals(7,g.length);
            assertTrue(g[0]>=last,"in order");last=g[0];
            assertTrue(g[0]>=SamuraiScene.CLOSE&&g[0]+g[1]<=SamuraiScene.FLASH+.03f,"a star at "+g[0]);
            assertTrue(g[1]>=.05f&&g[1]<=.12f&&g[5]>0&&g[3]>0&&g[3]<3);
        }
        // the video's big split stars: 1.37, 1.57, 1.77, 1.87, 2.10, 2.20, 2.30 and 2.87 s
        float[] big={1.37f,1.57f,1.77f,1.87f,2.1f,2.2f,2.3f,2.87f};int found=0;
        for(float at:big)for(float[] g:OneFlash.STARS)if(g[6]>0&&at>=g[0]-.001f&&at<g[0]+g[1]){found++;break;}
        assertEquals(big.length,found);
        last=0;
        for(float[] g:OneFlash.INKS){
            assertEquals(7,g.length);
            assertTrue(g[0]>=last);last=g[0];
            assertTrue(g[0]>=SamuraiScene.CLOSE&&g[0]+g[1]<=SamuraiScene.FLASH,"ink at "+g[0]);
            // behind its user (they face the close shot), a body's height or more
            assertTrue(g[2]<=.1f&&g[6]>=1.8f);
        }
    }
    @Test void theLightOffTheBladeIsTheSheathingsAlone(){
        assertEquals(0,OneFlash.noto(2.25f));assertEquals(0,OneFlash.noto(SamuraiKata.CLICK+.05f));assertEquals(0,OneFlash.noto(1.5f));
        for(float s=2.42f;s<2.86f;s+=.04f)assertEquals(1,OneFlash.noto(s),1e-4,"at "+s);
        // (the video's flash as the sword is laid there)
        assertTrue(OneFlash.noto(2.37f)>1);
    }
    @Test void theCameraJoltsAtItsBlowsAndNowhereElse(){
        for(float s:new float[]{1.0f,1.19f,2.6f,3.6f,5f,6f}){float[] j=OneFlash.jolt(s);assertEquals(0,Math.abs(j[0])+Math.abs(j[1])+Math.abs(j[2]),1e-4,"at "+s);}
        for(float at:SLASH_SEC){
            float most=0;
            for(float s=at;s<at+.1f;s+=1/120f){float[] j=OneFlash.jolt(s);most=Math.max(most,Math.abs(j[0])+Math.abs(j[1])+Math.abs(j[2]));}
            assertTrue(most>.5f&&most<6,"a jolt at "+at+": "+most);
        }
        // none jumps in a frame by more than a few degrees
        for(float s=1f;s<7f;s+=1/120f){
            float[] a=OneFlash.jolt(s),b=OneFlash.jolt(s+1/120f);
            for(int i=0;i<3;i++)assertTrue(Math.abs(b[i]-a[i])<2.5f,"jolt "+i+" at "+s+": "+(b[i]-a[i]));
        }
    }
    @Test void theSignIsTheCharactersSevenStrokesWithinItsSquare(){
        assertEquals(7,OneFlash.Shi.STROKES.length);assertEquals(7,OneFlash.Shi.WIDTHS.length);
        for(int k=0;k<7;k++){
            for(double t=0;t<=1;t+=.05){
                double[] p=OneFlash.Shi.at(OneFlash.Shi.STROKES[k],t);
                assertTrue(p[0]>.02&&p[0]<.98&&p[1]>.1&&p[1]<.98,"stroke "+k+" at "+t+": "+p[0]+", "+p[1]);
            }
            double[] a=OneFlash.Shi.at(OneFlash.Shi.STROKES[k],0),b=OneFlash.Shi.at(OneFlash.Shi.STROKES[k],1);
            double[][] pts=OneFlash.Shi.STROKES[k];
            assertEquals(pts[0][0],a[0],1e-9);assertEquals(pts[pts.length-1][1],b[1],1e-6);
        }
        // the bar over it is its widest stroke, across the top
        double[][] bar=OneFlash.Shi.STROKES[0];
        assertTrue(bar[bar.length-1][0]-bar[0][0]>.8);
        for(double[] p:bar)assertTrue(p[1]<.25);
    }
}
