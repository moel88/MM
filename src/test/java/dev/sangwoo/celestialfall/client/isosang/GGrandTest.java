package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's G made grand (2.88.0): the sword's inscriptions light only once it is out of the storm, run up it, flare
 * as it strikes and burn hotter until the blast; the ground beats faster toward the blast; the storm's eye burns gold
 * behind the sword in the sky shot (2.90.0: no lightning in it); (2.91.0) the ground's cracks are jagged and run out to
 * their length. Every part is a continuous function of the cast clock:
 * nothing jumps between two frames at 60 fps but the flashes, which come in over a frame and a half as the codebase's
 * kicks do.
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
            assertTrue(now-before<FRAME*3,"nor races (it runs at most three times as fast), at "+t);
            before=now;
        }
    }

    @Test void theGroundBeatsFasterTowardTheBlast(){
        for(float t=0;t<IsosangRules.G_STRIKE-.01f;t+=.01f)assertEquals(0,GGrand.ground(t),1e-6,"no fissures before it strikes, at "+t);
        for(int i=0;i+2<GGrand.BEATS.length;i++)
            assertTrue(GGrand.BEATS[i+2]-GGrand.BEATS[i+1]<GGrand.BEATS[i+1]-GGrand.BEATS[i]+1e-4,"each beat comes sooner than the last");
        for(int i=0;i+1<GGrand.BEATS.length;i++){
            float beat=GGrand.ground(GGrand.BEATS[i]+.04f),between=GGrand.ground((GGrand.BEATS[i]+GGrand.BEATS[i+1])*.5f+.05f);
            assertTrue(beat>between+.1f,"the ground beats at "+GGrand.BEATS[i]);
        }
        assertEquals(0,GGrand.ground(5.9f),1e-6,"the fissures have gone after the blast");
    }

    @Test void theStormsEyeBurnsGoldWithNoLightning(){
        for(float t=0;t<1.62f;t+=.01f)assertEquals(0,GGrand.storm(t)[0],1e-6,"the eye does not burn before the sky shot, at "+t);
        assertTrue(GGrand.storm(2.3f)[0]>.8f,"the eye burns gold behind the sword in the sky shot");
        assertEquals(0,GGrand.storm(2.96f)[0],1e-6,"and is out once the sword falls");
        assertEquals(2,GGrand.storm(2.0f).length,"its eye and its clock only: no lightning (2.90.0)");
        for(float t=0;t<4;t+=FRAME/10){
            float[] a=GGrand.storm(t),b=GGrand.storm(t+FRAME);
            assertTrue(Math.abs(b[0]-a[0])<.45f,"the eye's light jumps at "+t);
        }
    }

    @Test void theSwordsLightBeatsWithItsHeart(){
        for(int i=0;i<GGrand.BEATS.length;i++){
            assertTrue(GGrand.heart(GGrand.BEATS[i]+.04f)>.95f,"a beat at "+GGrand.BEATS[i]);
            float on=GGrand.rune(GGrand.BEATS[i]+.04f)[0],off=GGrand.rune(GGrand.BEATS[i]-.04f)[0];
            assertTrue(on>off+.05f,"the inscriptions swell on the beat at "+GGrand.BEATS[i]);
        }
        for(int i=0;i+1<4;i++)assertTrue(GGrand.heart((GGrand.BEATS[i]+GGrand.BEATS[i+1])*.5f+.06f)<.5f,"the first beats are apart");
        assertEquals(0,GGrand.heart(3.4f),1e-6,"no beat before the first");
    }

    @Test void theCracksAreJaggedAndRunOutToAPoint(){
        net.minecraft.util.math.Vec3d o=new net.minecraft.util.math.Vec3d(10,64,-5);
        for(int seed=0;seed<40;seed++){
            double angle=seed*.37,length=9+seed%10;
            net.minecraft.util.math.Vec3d[] p=GGrand.jagged(o,angle,1.4,length,16,seed*131+7,.55);
            assertEquals(1.4,p[0].distanceTo(o),1e-6,"it starts at the strike's edge");
            assertEquals(length,p[p.length-1].distanceTo(o),1.0,"and runs out its length");
            for(net.minecraft.util.math.Vec3d q:p)assertEquals(64,q.y,1e-9,"along the ground");
            int sharp=0;
            for(int j=1;j+1<p.length;j++){
                net.minecraft.util.math.Vec3d a=p[j].subtract(p[j-1]).normalize(),b=p[j+1].subtract(p[j]).normalize();
                if(Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,a.dotProduct(b)))))>15)sharp++;
            }
            assertTrue(sharp>=5,"jagged, not a smooth tube: "+sharp+" sharp turns (seed "+seed+")");
        }
    }

    @Test void nothingJumpsBetweenTwoFrames(){
        for(float t=0;t<7;t+=FRAME/10){
            float[] a=GGrand.rune(t),b=GGrand.rune(t+FRAME);
            assertTrue(Math.abs(b[0]-a[0])<.12f,"the inscriptions' strength jumps at "+t);
            assertTrue(Math.abs(b[1]-a[1])<8,"how far up they are lit jumps at "+t);
            assertTrue(Math.abs(b[3]-a[3])<.8f,"their flare jumps at "+t);
            assertTrue(Math.abs(GGrand.ground(t+FRAME)-GGrand.ground(t))<.85f,"the fissures jump at "+t);
            assertTrue(Math.abs(GGrand.impact(t+FRAME,IsosangRules.G_STRIKE)-GGrand.impact(t,IsosangRules.G_STRIKE))<.85f,"the impact frame jumps at "+t);
        }
        float most=0;
        for(float t=IsosangRules.G_STRIKE-.05f;t<IsosangRules.G_STRIKE+.2f;t+=.001f)most=Math.max(most,GGrand.impact(t,IsosangRules.G_STRIKE));
        assertEquals(1,most,.02,"the strike's impact frame comes in full");
        assertEquals(0,GGrand.impact(IsosangRules.G_STRIKE+.13f,IsosangRules.G_STRIKE),1e-6,"for a few frames only");
    }
}
