package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's G made grand (2.88.0): the sword's inscriptions light only once it is out of the storm, run up it, flare
 * as it strikes and burn hotter until the blast; the ground beats faster toward the blast; the lightning flashes twice
 * and is gone. Every part is a continuous function of the cast clock: nothing jumps between two frames at 60 fps but the
 * flashes, which come in over a frame and a half as the codebase's kicks do.
 */
class GGrandTest {
    static final float FRAME=1/60f;

    @Test void theInscriptionsLightOnceTheSwordIsOutOfTheStorm(){
        for(float t=0;t<1.62f;t+=.01f)assertEquals(0,GGrand.rune(t)[0],1e-6,"no burning inscriptions before the sky shot, at "+t);
        assertTrue(GGrand.rune(2.0f)[0]>.8f,"burning as it comes out of the storm");
        assertTrue(GGrand.rune(1.7f)[1]<8,"lit from the point up first");
        assertTrue(GGrand.rune(2.6f)[1]>30,"lit far up it by the end of the sky shot");
        assertTrue(GGrand.rune(3.0f)[1]>90,"lit all along it as it falls");
        assertTrue(GGrand.rune(IsosangRules.G_STRIKE+.02f)[3]>.8f,"flaring white as it strikes");
        assertTrue(GGrand.rune(4.6f)[0]>GGrand.rune(3.3f)[0]+.4f,"burning hotter the longer it stands in the ground");
        assertTrue(GGrand.rune(4.6f)[3]>.6f,"and whiter as the blast nears");
        float before=GGrand.rune(0)[2];
        for(float t=FRAME;t<7;t+=FRAME){
            float now=GGrand.rune(t)[2];
            assertTrue(now>before,"the light running up it never runs back, at "+t);
            assertTrue(now-before<FRAME*4,"nor races (it runs at most four times as fast), at "+t);
            before=now;
        }
    }

    @Test void theGroundBeatsFasterTowardTheBlast(){
        for(float t=0;t<IsosangRules.G_STRIKE-.01f;t+=.01f)assertEquals(0,GGrand.ground(t),1e-6,"no fissures before it strikes, at "+t);
        for(int i=0;i+2<GGrand.BEATS.length;i++)
            assertTrue(GGrand.BEATS[i+2]-GGrand.BEATS[i+1]<GGrand.BEATS[i+1]-GGrand.BEATS[i]+1e-4,"each beat comes sooner than the last");
        for(int i=0;i+1<GGrand.BEATS.length;i++){
            float beat=GGrand.ground(GGrand.BEATS[i]+.02f),between=GGrand.ground((GGrand.BEATS[i]+GGrand.BEATS[i+1])*.5f+.05f);
            assertTrue(beat>between+.1f,"the ground beats at "+GGrand.BEATS[i]);
        }
        assertEquals(0,GGrand.ground(5.9f),1e-6,"the fissures have gone after the blast");
    }

    @Test void lightningFlashesTwiceAndIsGone(){
        assertEquals(0,GGrand.flash(-.02f),1e-6);
        assertEquals(0,GGrand.flash(.46f),1e-6);
        assertTrue(GGrand.flash(.02f)>.9f,"a strike");
        assertTrue(GGrand.flash(.06f)<GGrand.flash(.02f)-.2f,"dimming");
        assertTrue(GGrand.flash(.09f)>GGrand.flash(.06f)+.1f,"and a second stroke");
        for(double[] b:GGrand.SKY_BOLTS)assertTrue(b[0]>=1.65&&b[0]+.45<IsosangRules.G_FALL+.01,"each bolt is over within the sky shot");
    }

    @Test void nothingJumpsBetweenTwoFrames(){
        for(float t=0;t<7;t+=FRAME/10){
            float[] a=GGrand.rune(t),b=GGrand.rune(t+FRAME);
            assertTrue(Math.abs(b[0]-a[0])<.12f,"the inscriptions' strength jumps at "+t);
            assertTrue(Math.abs(b[1]-a[1])<8,"how far up they are lit jumps at "+t);
            assertTrue(Math.abs(b[3]-a[3])<.8f,"their flare jumps at "+t);
            assertTrue(Math.abs(GGrand.ground(t+FRAME)-GGrand.ground(t))<.85f,"the fissures jump at "+t);
            assertTrue(Math.abs(GGrand.flash(t-2+FRAME)-GGrand.flash(t-2))<.85f,"a flash jumps at "+t);
            assertTrue(Math.abs(GGrand.impact(t+FRAME,IsosangRules.G_STRIKE)-GGrand.impact(t,IsosangRules.G_STRIKE))<.85f,"the impact frame jumps at "+t);
        }
        float most=0;
        for(float t=IsosangRules.G_STRIKE-.05f;t<IsosangRules.G_STRIKE+.2f;t+=.001f)most=Math.max(most,GGrand.impact(t,IsosangRules.G_STRIKE));
        assertEquals(1,most,.02,"the strike's impact frame comes in full");
        assertEquals(0,GGrand.impact(IsosangRules.G_STRIKE+.13f,IsosangRules.G_STRIKE),1e-6,"for a few frames only");
    }
}
