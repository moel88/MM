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
    @Test void nearPillarQuakeAndOverwhelmStayBounded() {
        double R=28;
        assertEquals(1,AtomicStage.near(R*1.2,R),1e-6);
        assertEquals(0,AtomicStage.near(R*8.1,R),1e-6);
        for(float sec=0;sec<30;sec+=.01f)for(double d:new double[]{R*1.1,R*3,R*6,R*9}){
            float q=AtomicStage.quake(sec,d,R),o=AtomicStage.overwhelm(sec,d,R);
            assertTrue(q>=0&&q<=2.0001f,"quake "+q);
            assertTrue(o>=0&&o<=1.0001f,"overwhelm "+o);
        }
        assertEquals(0,AtomicStage.quake(5,R*1.2,R),1e-6);
        assertTrue(AtomicStage.quake(10,R*1.2,R)>1);
        assertEquals(0,AtomicStage.quake(AtomicLook.BEAM_END+2.1f,R*1.2,R),1e-6);
        assertEquals(0,AtomicStage.overwhelm(10,R*9,R),1e-6);
    }
    @Test void surgesPulseWhileThePillarStands() {
        assertEquals(0,AtomicStage.surge(AtomicLook.PINK-.1f));
        float max=0;for(float sec=8;sec<9.6f;sec+=.005f)max=Math.max(max,AtomicStage.surge(sec));
        assertTrue(max>.95f);
        assertEquals(0,AtomicStage.surge(AtomicLook.BEAM_END+.1f));
    }
    @Test void surgeFrontLeavesTheBaseOnEachPulseAndClearsTheTop() {
        double R=28;
        assertEquals(-1,AtomicStage.surgeFront(AtomicLook.PINK-.01f,R));
        assertEquals(-1,AtomicStage.surgeFront(AtomicLook.BEAM_END,R));
        assertEquals(0,AtomicStage.surgeGlow(AtomicLook.PINK-.01f));
        for(int k=0;k<15;k++){
            float launch=AtomicLook.PINK+k*AtomicStage.SURGE_PERIOD;
            assertEquals(0,AtomicStage.surgeFront(launch+1e-4f,R),.2);
            // The front climbs steadily and passes the pillar's top (9 R above the ground) before the next one.
            double prev=-1;for(float a=.01f;a<AtomicStage.SURGE_PERIOD-.01f;a+=.01f){double f=AtomicStage.surgeFront(launch+a,R);assertTrue(f>prev);prev=f;}
            assertTrue(prev>9*R+2);
            // The quake kick and the visible surge start together.
            assertTrue(AtomicStage.surge(launch+.06f)>.7f&&AtomicStage.surgeGlow(launch+.06f)>.9f);
        }
    }
    @Test void nearViewersFovThrobsWithSurgesWithinTheCap() {
        double R=28;float max=0;
        for(float sec=AtomicLook.PINK;sec<AtomicLook.BEAM_END;sec+=.005f)for(double d:new double[]{R*1.1,R*2,R*3}){
            float fov=AtomicStage.fov(sec,d,R);assertTrue(fov<=12f+1e-5);max=Math.max(max,fov);
        }
        assertTrue(AtomicStage.fov(12.0f,R*1.2,R)>=0);
        float launch=AtomicLook.PINK+10*AtomicStage.SURGE_PERIOD;
        assertTrue(AtomicStage.fov(launch+.06f,R*1.2,R)-AtomicStage.fov(launch+.6f,R*1.2,R)>1.2f);
    }
}
