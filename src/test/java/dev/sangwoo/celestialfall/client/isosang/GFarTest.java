package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's G seen from afar (2.89.0): its lightning leaves the storm's base and strikes the ground round where the
 * sword falls, all before the sword strikes; the storm's light is the far view's only (none for a camera close by); the
 * column of light stands while the sword hangs in the sky; nothing jumps between two frames at 60 fps but the flashes.
 */
class GFarTest {
    static final float FRAME=1/60f;
    static StandClient.Cast cast(){return GGesture.REFERENCE_CAST;}

    @Test void theLightningStrikesTheGroundRoundWhereTheSwordFalls(){
        Vec3d s=FallingSword.strike(cast());
        for(int i=0;i<GFar.GROUND_BOLTS.length;i++){
            Vec3d[] b=GFar.groundBolt(cast(),i);
            assertTrue(b[0].y-s.y>GFar.BASE-2.5&&b[0].y-s.y<=GFar.BASE,"bolt "+i+" leaves the storm's base");
            assertEquals(s.y+.05,b[1].y,1e-6,"and strikes the ground");
            double reach=Math.hypot(b[1].x-s.x,b[1].z-s.z);
            assertTrue(reach>=11.9&&reach<=38.1,"round where the sword falls, not on it: "+reach);
            assertTrue(GFar.GROUND_BOLTS[i]+.2f<IsosangRules.G_STRIKE,"before the sword strikes");
        }
        for(int i=0;i+1<GFar.GROUND_BOLTS.length;i++)assertTrue(GFar.GROUND_BOLTS[i+1]>GFar.GROUND_BOLTS[i]+.1f,"one at a time");
    }

    @Test void theStormsLightIsTheFarViewsOnly(){
        Vec3d s=FallingSword.strike(cast());
        Vec3d near=s.add(6,2,6),far=s.add(60,10,0);
        for(float t=0;t<7;t+=.05f){
            float[] k=GFar.storm(cast(),t,near);
            assertEquals(0,k[0],1e-6,"no golden eye for a camera close by, at "+t);
            assertEquals(0,k[5],1e-6,"nor lightning in its banks");
        }
        assertTrue(GFar.storm(cast(),2.2f,far)[0]>.8f,"from afar the eye burns gold while the sword hangs in it");
        assertEquals(0,GFar.storm(cast(),3.4f,far)[0],1e-6,"and is out once it has struck");
        for(float at:GFar.GROUND_BOLTS){
            float[] k=GFar.storm(cast(),at+.015f,far);
            assertTrue(k[5]>.9f,"each bolt lights the banks it leaves, at "+at);
            assertTrue(Math.abs(k[3]-(GFar.BASE-40*Math.cos(Math.toRadians(16.96))))<3,"at the storm's base");
        }
        assertEquals(0,GFar.storm(cast(),.5f,far)[5],1e-6,"no lightning before the storm has gathered");
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
            assertTrue(Math.abs(b[5]-a[5])<1.1f,"a flash jumps by more than it enters over a frame and a half, at "+t);
        }
    }
}
