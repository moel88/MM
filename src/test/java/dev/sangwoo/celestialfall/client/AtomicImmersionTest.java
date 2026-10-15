package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.client.atomic.AtomicLook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicImmersionTest {
    @Test void hazeRampsDuringTheChantAndStopsAtImpact() {
        assertEquals(0,AtomicLook.tint(0));
        assertTrue(AtomicLook.tint(.6f)>.4f&&AtomicLook.tint(.6f)<.6f);
        assertEquals(1,AtomicLook.tint(3),1e-6);
        assertTrue(AtomicLook.tint(5.57f)<.7f);
        assertEquals(0,AtomicLook.tint(5.6f));
    }
    @Test void impactFramesFollowTheReferenceOrder() {
        assertEquals(0,AtomicLook.frame(5.58f));assertEquals(1,AtomicLook.frame(5.60f));
        assertEquals(2,AtomicLook.frame(5.65f));assertEquals(3,AtomicLook.frame(5.72f));
        assertEquals(4,AtomicLook.frame(5.80f));assertEquals(5,AtomicLook.frame(5.88f));assertEquals(0,AtomicLook.frame(5.9f));
    }
    @Test void interiorGradeHasNoGreen() {
        for(float sec=8.6f;sec<21.2f;sec+=.13f){
            var g=AtomicLook.grade(sec,(long)(sec*50));
            assertEquals(1,g.mix(),1e-6);
            assertEquals(0,(g.lo()>>8)&255);assertEquals(0,(g.hi()>>8)&255);
            assertTrue((g.hi()&255)>(g.lo()&255));
        }
    }
    @Test void gradeFadesBackToNormal() {
        assertEquals(1,AtomicLook.grade(22.2f,0).mix(),1e-6);
        float prev=1;
        for(float sec=22.3f;sec<23.3f;sec+=.05f){float m=AtomicLook.grade(sec,0).mix();assertTrue(m<=prev+1e-6);prev=m;}
        assertEquals(0,AtomicLook.grade(23.3f,0).mix());
    }
    @Test void shakeStartsLateAndDecaysToZero() {
        assertEquals(0,AtomicLook.shake(6.0f));assertTrue(AtomicLook.shake(6.5f)>0);
        assertEquals(1.5,AtomicLook.shake(15),1e-6);
        assertTrue(AtomicLook.shake(24)<AtomicLook.shake(23));assertEquals(0,AtomicLook.shake(25.2f));
        double sum=0;int n=4000;for(int i=0;i<n;i++){float j=AtomicLook.jitter(i,0);sum+=j*j;}
        assertEquals(1,Math.sqrt(sum/n),.15);
    }
    @Test void viewersFarAwayAreUntouched() {
        assertEquals(AtomicImmersion.NONE,AtomicImmersion.sample(15,200,28,1));
        var near=AtomicImmersion.sample(15,10,28,1);
        assertEquals(1,near.grade().mix(),1e-6);assertTrue(near.active());
        var none=AtomicImmersion.sample(15,10,28,0);
        assertEquals(0,none.yaw());assertEquals(0,none.pitch());
    }
}
