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
    @Test void outsideQuakeTopsUpTheBlastShakeWithoutStacking() {
        double R=28;
        for(float sec=5;sec<26;sec+=.01f)for(double d:new double[]{R*1.2,R*2,R*3,R*5,R*7,R*9}){
            float extra=AtomicImmersion.quakeBeyondShake(sec,d,R,1);
            float shake=AtomicLook.shake(sec)*AtomicImmersion.reach(d,R),quake=dev.sangwoo.celestialfall.client.atomic.AtomicStage.quake(sec,d,R);
            assertTrue(extra>=0&&extra<=quake+1e-5);
            // Together the two shake about as hard as the stronger one.
            assertEquals(Math.max(shake,quake),Math.sqrt(shake*shake+extra*extra),1e-4);
        }
        // Beyond the blast shake's reach the whole quake is felt; the shake setting scales it.
        assertEquals(dev.sangwoo.celestialfall.client.atomic.AtomicStage.quake(10,R*5,R),AtomicImmersion.quakeBeyondShake(10,R*5,R,1),1e-5);
        assertEquals(0,AtomicImmersion.quakeBeyondShake(10,R*5,R,0));
    }
    @Test void rumbleIsSmoothAndZeroMean() {
        double sum=0,sq=0,jump=0;int n=20000;float prev=AtomicImmersion.rumble(0,1);
        for(int i=1;i<n;i++){float t=i*.01f,v=AtomicImmersion.rumble(t,1);sum+=v;sq+=v*v;jump=Math.max(jump,Math.abs(v-prev));prev=v;}
        assertEquals(0,sum/n,.15);
        assertTrue(Math.sqrt(sq/n)>.4&&Math.sqrt(sq/n)<1.1);
        // Eased between steps: 0.01 of a step never moves it far.
        assertTrue(jump<.15,"jump "+jump);
    }
    @Test void viewersFarAwayAreUntouched() {
        assertEquals(AtomicImmersion.NONE,AtomicImmersion.sample(15,200,28,1));
        var near=AtomicImmersion.sample(15,10,28,1);
        assertEquals(1,near.grade().mix(),1e-6);assertTrue(near.active());
        var none=AtomicImmersion.sample(15,10,28,0);
        assertEquals(0,none.yaw());assertEquals(0,none.pitch());
    }
}
