package dev.sangwoo.celestialfall.client.samurai;

import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.SLASH_SEC;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the samurai's G draws (2.144.0): its stars and its ink come off the blade's point at the body's cuts; the ring of
 * ink before the dash is brushed round once and scattered by the dash; the light off the blade only while it is laid at
 * the scabbard's mouth and slid home; its camera's jolts at its blows and nowhere else; and the character for death is the
 * seven strokes of it, within its square.
 */
class OneFlashTest {
    @Test void theStarsAndTheInkComeAtTheBodysCuts(){
        assertEquals(SamuraiKata.CUTS.length*3,OneFlash.STARS.length);assertEquals(SamuraiKata.CUTS.length,OneFlash.INKS.length);
        for(int k=0;k<SamuraiKata.CUTS.length;k++){
            float at=SamuraiKata.CUTS[k][0];float[] star=OneFlash.STARS[k*3],ink=OneFlash.INKS[k];
            assertEquals(7,star.length);assertEquals(7,ink.length);
            // the cut's star: on as the blade goes through its middle, where the blade's point is then
            assertTrue(star[0]<=at&&star[0]+star[1]>at,"a star on at "+at);
            float[] tip=OneFlash.tipBy(at);
            for(int i=0;i<3;i++)assertEquals(tip[i],star[2+i],1e-4);
            assertTrue(tip[1]>0&&tip[1]<3.6,"the point over the ground at "+at+": "+tip[1]);
            // the big cuts' split the light
            assertEquals(SamuraiKata.CUTS[k][1]>=1.1f,star[6]>0);
            // its ink a moment after, a body's height or so
            assertTrue(ink[0]>at&&ink[0]<at+.05f&&ink[6]>=2f&&ink[3]>0);
            // behind them (they face the close shot: never before their body)
            assertTrue(ink[2]<0);
        }
        // every cut is within the close shot's time or before it (the draw is the dash's): none after the sheathing begins
        for(float[] c:SamuraiKata.CUTS)assertTrue(c[0]>=SamuraiScene.DASH&&c[0]<SamuraiKata.NOTO);
        // the lines they leave: the white the shortest, the red the longest, all gone before the next part
        assertTrue(OneFlash.LINE_WHITE<OneFlash.LINE_INK&&OneFlash.LINE_INK<OneFlash.LINE_RED);
        assertTrue(SamuraiKata.WHIRL+OneFlash.LINE_RED<SamuraiScene.FLASH);
    }
    @Test void theRingOfInkIsBrushedRoundOnceAndScatteredByTheDash(){
        assertEquals(0,OneFlash.ensoDrawn(0),1e-6);assertEquals(1,OneFlash.ensoDrawn(OneFlash.ENSO_TO),1e-6);
        float last=0;
        for(float s=0;s<1.2f;s+=1/120f){float d=OneFlash.ensoDrawn(s);assertTrue(d>=last-1e-6&&d-last<.06f,"its brush goes round steadily at "+s);last=d;}
        // drawn before the sword is broken out; there until the dash, gone just after it
        assertTrue(OneFlash.ENSO_TO<SamuraiKata.BURST);
        assertEquals(1,OneFlash.ensoThere(SamuraiKata.BURST),1e-6);assertEquals(0,OneFlash.ensoThere(SamuraiScene.DASH+.07f),1e-6);
        // its brush: thin where it is set down, full, lifted to nearly nothing; never wider than a third of a block
        assertTrue(OneFlash.ensoWidth(0)<.07&&OneFlash.ensoWidth(1)<.07&&OneFlash.ensoWidth(.4)>.2);
        for(double u=0;u<=1;u+=.02)assertTrue(OneFlash.ensoWidth(u)>0&&OneFlash.ensoWidth(u)<=.3001);
        // not a whole turn: the brush's gap is left open
        assertTrue(OneFlash.ENSO_ROUND<Math.PI*2-.3);
    }
    @Test void theLightOffTheBladeIsTheSheathingsAlone(){
        assertEquals(0,OneFlash.noto(SamuraiKata.WHIRL));assertEquals(0,OneFlash.noto(SamuraiKata.SPIN));assertEquals(0,OneFlash.noto(SamuraiKata.CLICK+.05f));assertEquals(0,OneFlash.noto(1.5f));
        for(float s=SamuraiKata.NOTO+.04f;s<SamuraiKata.CLICK-.06f;s+=.02f)assertEquals(1,OneFlash.noto(s),1e-4,"at "+s);
        // (a flash as the sword is laid there)
        assertTrue(OneFlash.noto(SamuraiKata.NOTO+.01f)>1);
        // the crescent's ink follows its white, and its red edge is thin
        assertTrue(OneFlash.INK_AFTER<OneFlash.INK_UNTIL&&OneFlash.INK_AFTER>0&&OneFlash.RED_EDGE<.2);
    }
    @Test void theCameraJoltsAtItsBlowsAndNowhereElse(){
        for(float s:new float[]{1.0f,1.19f,3.6f,5f,6f}){float[] j=OneFlash.jolt(s);assertEquals(0,Math.abs(j[0])+Math.abs(j[1])+Math.abs(j[2]),1e-4,"at "+s);}
        float[] blows={SLASH_SEC[0],SLASH_SEC[1],SLASH_SEC[2],SLASH_SEC[3],SamuraiKata.SPIN,SamuraiKata.WHIRL,SamuraiKata.CLICK};
        for(float at:blows){
            float most=0;
            for(float s=at;s<at+.1f;s+=1/120f){float[] j=OneFlash.jolt(s);most=Math.max(most,Math.abs(j[0])+Math.abs(j[1])+Math.abs(j[2]));}
            assertTrue(most>.3f&&most<6,"a jolt at "+at+": "+most);
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
