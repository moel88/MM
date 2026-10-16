package dev.sangwoo.celestialfall.client.atomic;

import dev.sangwoo.celestialfall.AtomicTimeline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicPillarShapeTest {
    private static final double[] RADII={8,28,140};

    @Test void neverWiderThanTheDamageRadius() {
        for(double R:RADII)for(float sec=0;sec<30;sec+=.01f)assertTrue(AtomicPillarShape.radius(sec,R)<=R+1e-9,"R="+R+" sec="+sec);
    }
    @Test void opensFromAHairlineAtTheImpact() {
        for(double R:RADII){
            assertEquals(0,AtomicPillarShape.radius(AtomicLook.IMPACT-.01f,R));
            double first=AtomicPillarShape.radius(AtomicLook.IMPACT,R);
            assertTrue(first>0&&first<=R*.05,"R="+R);
        }
    }
    @Test void opensSteadilyAndHoldsTheFullRadius() {
        for(double R:RADII){
            double prev=0;
            for(float sec=AtomicLook.IMPACT;sec<=AtomicPillarShape.OPEN;sec+=.01f){
                double r=AtomicPillarShape.radius(sec,R);assertTrue(r>=prev-1e-9);prev=r;
            }
            for(float sec=AtomicPillarShape.OPEN;sec<AtomicLook.COLLAPSE;sec+=.05f)assertEquals(R,AtomicPillarShape.radius(sec,R),1e-6);
        }
    }
    @Test void closesBackIntoTheCasterByTheEndOfTheBeam() {
        for(double R:RADII){
            double prev=R;
            for(float sec=AtomicLook.COLLAPSE;sec<AtomicLook.BEAM_END;sec+=.01f){
                double r=AtomicPillarShape.radius(sec,R);assertTrue(r<=prev+1e-9);prev=r;
            }
            assertTrue(AtomicPillarShape.radius(AtomicLook.BEAM_END-.005f,R)<R*.01);
            assertEquals(0,AtomicPillarShape.radius(AtomicLook.BEAM_END,R));
        }
    }
    @Test void baseAndTopStayPut() {
        for(double R:RADII){
            assertEquals(62,AtomicPillarShape.base(64),1e-9);
            for(float sec=AtomicLook.IMPACT+.12f;sec<AtomicLook.BEAM_END;sec+=.1f)assertEquals(64+9*R,AtomicPillarShape.top(sec,64,R),1e-6);
        }
    }
    @Test void interiorUsesTheServerDamageColumn() {
        for(double R:RADII){
            assertTrue(AtomicPillarShape.inside(0,1,0,R));
            assertTrue(AtomicPillarShape.inside(R*.99,0,0,R));
            assertFalse(AtomicPillarShape.inside(R*1.01,0,0,R));
            assertFalse(AtomicPillarShape.inside(0,AtomicTimeline.damageHeight(R)+.5,0,R));
            assertFalse(AtomicPillarShape.inside(0,-2.5,0,R));
        }
    }
    @Test void flowClockNeverJumpsOrStalls() {
        float prev=AtomicPillarShape.flowTime(20);
        for(float sec=20.01f;sec<24;sec+=.01f){
            float now=AtomicPillarShape.flowTime(sec),step=now-prev;
            assertTrue(step>.0099f&&step<.0161f,"sec="+sec+" step="+step);prev=now;
        }
    }
    @Test void ignitionWaveStaysInsideTheRadius() {
        for(double R:RADII)for(float sec=AtomicLook.IMPACT;sec<8;sec+=.01f)assertTrue(AtomicPillarShape.ignitionFront(sec,R)<=R+1e-9);
    }
}
