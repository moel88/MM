package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's G seen from afar (2.89.0): the storm's light is the far view's only (none for a camera close by); the
 * column of light stands while the sword hangs in the sky; nothing jumps between two frames at 60 fps but the flashes.
 * (2.90.0) No lightning: the sword cleaves the storm instead, the cleft opening as it comes through the storm's base and
 * torn wider as it falls, light pouring down through it until the blast blows the storm apart.
 */
class GFarTest {
    static final float FRAME=1/60f;
    static StandClient.Cast cast(){return GGesture.REFERENCE_CAST;}

    @Test void theSwordCleavesTheStorm(){
        for(float t=0;t<1.48f;t+=.01f)assertEquals(0,GFar.cleftWide(t),1e-6,"no cleft before the sword comes through, at "+t);
        assertTrue(GFar.cleftWide(2.4f)>9,"open as it hangs in the sky: "+GFar.cleftWide(2.4f));
        assertTrue(GFar.cleftWide(3.3f)>GFar.cleftWide(2.7f)+6,"torn wider as it falls");
        assertTrue(GFar.cleftSpan(3.3f)*.5>50,"past the storm's own banks either side");
        assertTrue(GFar.cleftSpan(2.4f)>GFar.cleftSpan(1.6f)+40,"running out as it comes through");
        for(float t=2.0f;t<4.84f;t+=.01f)assertEquals(1,GFar.cleftOn(t),1e-6,"light pours through it the while it stands open, at "+t);
        for(float t=5.25f;t<7;t+=.01f)assertEquals(0,GFar.cleftOn(t),1e-6,"until the blast blows the storm apart, at "+t);
        Vec3d far=FallingSword.strike(cast()).add(60,10,0);
        float[] k=GFar.storm(cast(),2.9f,far);
        assertEquals(GFar.cleftSpan(2.9f),k[2],1e-6,"the shader gets the cleft's length");
        assertEquals(GFar.cleftWide(2.9f),k[3],1e-6,"and its width");
        for(float t=0;t<7;t+=FRAME/10){
            assertTrue(Math.abs(GFar.cleftWide(t+FRAME)-GFar.cleftWide(t))<.5f,"the cleft's width jumps at "+t);
            assertTrue(Math.abs(GFar.cleftSpan(t+FRAME)-GFar.cleftSpan(t))<2.5f,"its length jumps at "+t);
            assertTrue(Math.abs(GFar.cleftOn(t+FRAME)-GFar.cleftOn(t))<.12f,"the light through it jumps at "+t);
        }
    }

    @Test void theStormsLightIsTheFarViewsOnly(){
        Vec3d s=FallingSword.strike(cast());
        Vec3d near=s.add(6,2,6),far=s.add(60,10,0);
        for(float t=0;t<7;t+=.05f)assertEquals(0,GFar.storm(cast(),t,near)[0],1e-6,"no golden eye for a camera close by, at "+t);
        assertTrue(GFar.storm(cast(),2.2f,far)[0]>.8f,"from afar the eye burns gold while the sword hangs in it");
        assertEquals(0,GFar.storm(cast(),3.4f,far)[0],1e-6,"and is out once it has struck");
        assertEquals(4,GFar.storm(cast(),2.2f,far).length,"its eye, its clock and its cleft: no lightning (2.90.0)");
    }

    @Test void theColumnStandsWhileTheSwordHangsInTheSky(){
        for(float t=0;t<1.15f;t+=.01f)assertEquals(0,GFar.columnOn(t),1e-6,"no column before the storm opens, at "+t);
        for(float t=1.7f;t<2.85f;t+=.01f)assertEquals(1,GFar.columnOn(t),1e-6,"whole while the sword hangs, at "+t);
        for(float t=3.15f;t<7;t+=.01f)assertEquals(0,GFar.columnOn(t),1e-6,"gone once it has struck, at "+t);
    }

    @Test void nothingJumpsBetweenTwoFrames(){
        Vec3d far=FallingSword.strike(cast()).add(60,10,0);
        for(float t=0;t<7;t+=FRAME/10){
            assertTrue(Math.abs(GFar.columnOn(t+FRAME)-GFar.columnOn(t))<.12f,"the column jumps at "+t);
            float[] a=GFar.storm(cast(),t,far),b=GFar.storm(cast(),t+FRAME,far);
            assertTrue(Math.abs(b[0]-a[0])<.12f,"the eye's light jumps at "+t);
        }
    }

    @Test void theStormIsGoneOnceTheBlastHasBlownItApart(){
        assertTrue(GClouds.STORM_END>IsosangRules.G_STRIKE+2,"it stands through the blast ("+GClouds.STORM_END+" s)");
        assertTrue(GClouds.STORM_END<7.0f,"and is gone before the cast is");
    }
}
