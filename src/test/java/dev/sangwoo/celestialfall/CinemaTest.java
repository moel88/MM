package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.client.title.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CinemaTest {
    @Test void introFootstepsPlayOnceAndIdleCuesRepeat(){
        var clock=new CinemaTimeline();var events=new ArrayList<CinemaTimeline.Cue>();
        for(int i=0;i<31*60;i++)events.addAll(clock.advance(1/60.0,true));
        assertEquals(8,events.stream().filter(e->e.sound().startsWith("cinema_step")).count());
        assertEquals(8,events.stream().filter(e->e.sound().startsWith("cinema_step")).map(CinemaTimeline.Cue::sound).distinct().count());
        assertEquals(1,events.stream().filter(e->e.sound().equals("cinema_cloth")).count());
        assertEquals(3,events.stream().filter(e->e.sound().equals("cinema_chime")).count());
        assertEquals(2,events.stream().filter(e->e.sound().equals("cinema_sand")).count());
        assertTrue(clock.poseTime()>=7&&clock.poseTime()<19);
    }
    @Test void aLongStallCannotSkipTheIntroOrBurstFootsteps(){
        var c=new CinemaTimeline();assertTrue(c.advance(900,true).isEmpty());assertEquals(.1,c.elapsed(),1e-9);
        c.advance(Double.NaN,true);assertEquals(.1,c.elapsed(),1e-9);c.advance(-1,true);assertEquals(.1,c.elapsed(),1e-9);
    }
    @Test void motionDisabledShowsTheTurnedPoseWithoutReplayingTheWalk(){
        var c=new CinemaTimeline();assertTrue(c.advance(1,false).isEmpty());assertEquals(7,c.poseTime());
        assertTrue(c.musicReady());
        for(int i=0;i<100;i++)assertTrue(c.advance(.02,true).stream().noneMatch(e->e.sound().startsWith("cinema_step")));
    }
    @Test void musicStartsAtTheTurnAndDoesNotResetWithTheIdleLoop(){
        var c=new CinemaTimeline();assertFalse(c.musicReady());
        for(int i=0;i<379;i++)c.advance(.01,true);
        assertFalse(c.musicReady());c.advance(.02,true);assertTrue(c.musicReady());
        for(int i=0;i<2000;i++){c.advance(.05,true);assertTrue(c.musicReady());}
    }
    @Test void turnedHeadFacesScreenLeftThroughoutIdle()throws Exception{
        var p=CinemaAssets.performance();float[] frame=new float[p.stride];
        for(double t=7;t<19;t+=.19){
            p.sample(t,frame);int camera=p.bones*12;
            double fx=frame[camera+3]-frame[camera],fz=frame[camera+5]-frame[camera+2];
            double length=Math.hypot(fx,fz),rightX=-fz/length,rightZ=fx/length;
            // Project the native head's forward vector onto the camera's horizontal axis.
            assertTrue(frame[2*12+2]*rightX+frame[2*12+10]*rightZ<-.5);
        }
    }
    @Test void allShippedMeshesHaveValidBonesAndCameraRemainsFinite()throws Exception{
        var p=CinemaAssets.performance();assertEquals(7,p.bones);float[] frame=new float[p.stride];
        for(String name:List.of("stage","glow","bomi","prop","glass")){
            var m=CinemaAssets.mesh(name);assertTrue(m.count()>0);var a=m.vertices();
            for(int i=0;i<a.length;i+=14){assertEquals(Math.rint(a[i+12]),a[i+12]);assertTrue(a[i+12]>=-1&&a[i+12]<p.bones);}
        }
        for(double t=0;t<31;t+=.07){p.sample(t,frame);for(float f:frame)assertTrue(Float.isFinite(f));int k=p.bones*12;assertTrue(frame[k+6]>=30&&frame[k+6]<=45);}
    }
    @Test void bomiUsesOnlyNativeCuboidsAndTheExactSuppliedSkin()throws Exception{
        assertEquals(432,CinemaAssets.mesh("bomi").count());
        try(var in=CinemaTest.class.getResourceAsStream("/assets/celestialfall/textures/cinema/bomi.png")){
            assertNotNull(in);byte[] bytes=in.readAllBytes();
            String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals("b1b9d06472e7dd15e448e0c58c174d453940d98ba648b9ce3ebf91e63ac382ad",hash);
        }
        assertEquals(0,CinemaAssets.mesh("blink").count());
    }
    @Test void introSettlesIntoIdleAndLoopSeamIsContinuous()throws Exception{
        var p=CinemaAssets.performance();float[] a=new float[p.stride],b=new float[p.stride];
        p.sample(7-.00001,a);p.sample(7,b);for(int i=0;i<a.length;i++)assertEquals(a[i],b[i],.003,"intro "+i);
        p.sample(19-.00001,a);p.sample(7,b);for(int i=0;i<a.length;i++)assertEquals(a[i],b[i],.003,"loop "+i);
    }
}
