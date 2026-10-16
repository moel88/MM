package dev.sangwoo.celestialfall.client.atomic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicStageTest {
    @Test void skyDimsWithTheIgnitionHoldsAndComesBack() {
        assertEquals(0,AtomicStage.sky(AtomicLook.IMPACT-.01f));
        assertEquals(1,AtomicStage.sky(8),1e-6);
        assertEquals(1,AtomicStage.sky(AtomicLook.BEAM_END),1e-6);
        assertEquals(0,AtomicStage.sky(AtomicLook.BEAM_END+3.01f),1e-6);
    }
    @Test void reachIsFullNearAndGoneFar() {
        assertEquals(1,AtomicStage.reach(28*10,28),1e-6);
        assertEquals(0,AtomicStage.reach(28*49,28),1e-6);
        assertTrue(AtomicStage.reach(28*30,28)>0&&AtomicStage.reach(28*30,28)<1);
    }
    @Test void slowedIgnitionJoinsRealTimeSmoothly() {
        float prev=AtomicStage.visual(AtomicLook.IMPACT);
        assertEquals(AtomicLook.IMPACT,prev,1e-6);
        for(float sec=AtomicLook.IMPACT+.005f;sec<AtomicLook.IMPACT+1.5f;sec+=.005f){
            float v=AtomicStage.visual(sec);
            assertTrue(v>=prev,"monotonic at "+sec);
            assertTrue(v<=sec+1e-5,"never ahead of real time at "+sec);
            prev=v;
        }
        assertEquals(AtomicLook.IMPACT+AtomicStage.SLOW,AtomicStage.visual(AtomicLook.IMPACT+AtomicStage.SLOW),1e-6);
        // Slow at first.
        assertTrue(AtomicStage.visual(AtomicLook.IMPACT+.1f)-AtomicLook.IMPACT<.05f);
    }
    @Test void shockArrivesWhenItsFrontReachesTheViewer() {
        double R=28;
        for(double d:new double[]{45,130,224,600}){
            float a=AtomicStage.arrival(d,R);
            assertEquals(d,AtomicStage.shockRadius(a,R),1e-3);
            assertEquals(0,AtomicStage.kick(a-.01f,a));
            assertTrue(AtomicStage.kick(a+.06f,a)>.9f);
            assertTrue(AtomicStage.kick(a+2,a)<.01f);
        }
        assertTrue(AtomicStage.arrival(224,28)>AtomicStage.arrival(45,28));
    }
    @Test void cameraBeatsStayModest() {
        double R=28;
        for(float sec=5;sec<30;sec+=.01f)for(double d:new double[]{30,45,130,224,900}){
            float fov=AtomicStage.fov(sec,d,R);assertTrue(fov>=-4.01f&&fov<=12.01f);
            float jolt=AtomicStage.jolt(sec,d,R);assertTrue(jolt>=0&&jolt<=1.81f);
            float bar=AtomicStage.letterbox(sec,d,R);assertTrue(bar>=0&&bar<=.0851f);
        }
        assertEquals(0,AtomicStage.fov(10,28*60,R));
    }
    @Test void cloudRingsGrowOutwardAndFade() {
        double prev=0;
        for(float sec=5.6f;sec<14;sec+=.05f){double r=AtomicStage.cloudRing(sec);assertTrue(r>=prev);prev=r;}
        assertTrue(prev<=14);
        assertEquals(0,AtomicStage.cloudRingLevel(14),1e-6);
        assertTrue(AtomicStage.condensationRing(6.5f)>1&&AtomicStage.condensationRing(9)<=3.2);
        assertEquals(0,AtomicStage.condensationLevel(8),1e-6);
    }
}
