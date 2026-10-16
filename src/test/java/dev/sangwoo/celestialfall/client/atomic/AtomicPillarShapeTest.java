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
            double prev=0;float open=AtomicPillarShape.open(R);
            assertTrue(open<AtomicLook.COLLAPSE-3,"R="+R);
            for(float sec=AtomicLook.IMPACT;sec<=open;sec+=.01f){
                double r=AtomicPillarShape.radius(sec,R);assertTrue(r>=prev-1e-9);prev=r;
            }
            assertTrue(AtomicPillarShape.radius(open-.1f,R)<R);
            for(float sec=open;sec<AtomicLook.COLLAPSE;sec+=.05f)assertEquals(R,AtomicPillarShape.radius(sec,R),1e-6);
        }
    }
    @Test void edgeSpreadsNoFasterThanASprinter() {
        // The default 28-block blast and smaller ones open at 4.5 blocks/s or slower.
        for(double R:new double[]{8,28,40}){
            double prev=AtomicPillarShape.radius(AtomicLook.IMPACT,R);
            for(float sec=AtomicLook.IMPACT+.01f;sec<AtomicPillarShape.open(R)+.5f;sec+=.01f){
                double r=AtomicPillarShape.radius(sec,R);
                assertTrue((r-prev)/.01<=AtomicTimeline.OPEN_SPEED*1.001,"R="+R+" sec="+sec+" speed="+(r-prev)/.01);prev=r;
            }
        }
        assertEquals(6.47,AtomicTimeline.openingSeconds(28),.01);
        // Very large configured radii still open well before the collapse.
        assertEquals(AtomicTimeline.OPEN_LIMIT,AtomicTimeline.openingSeconds(768),1e-6);
    }
    @Test void aSprinterOutsideTheEdgeEscapesSomeoneStandingIsCaught() {
        double R=28,sprint=5.6;
        for(double t0=0;t0<6;t0+=.25){
            double x=AtomicTimeline.openingRadius(t0,R)+.5;
            for(double t=t0;t<AtomicTimeline.openingSeconds(R)+1;t+=.01){
                double r=AtomicTimeline.openingRadius(t,R),runner=x+sprint*(t-t0);
                assertTrue(runner>r||runner>R,"caught t0="+t0+" t="+t);
            }
            // Standing still just outside the edge, the column reaches you.
            if(x<R)assertTrue(AtomicTimeline.openingRadius(AtomicTimeline.openingSeconds(R),R)>=x);
        }
    }
    @Test void damageColumnOpensWithThePillarAndHoldsThroughTheCollapse() {
        double R=28;
        for(long age=0;age<AtomicTimeline.BEAM_END;age++){
            double server=AtomicTimeline.damageRadius(age,R);
            float sec=AtomicStage.visual(age/20f);
            if(age<AtomicTimeline.IMPACT){assertEquals(0,server);continue;}
            // The client's column (its impact is 0.01 s earlier) and the server's agree within a few hundredths of a block.
            assertEquals(AtomicPillarShape.column(sec,R),server,.06,"age="+age);
        }
        assertEquals(R,AtomicTimeline.damageRadius(AtomicTimeline.COLLAPSE+10,R),1e-9);
        // Someone 20 blocks out is outside the column one second after the impact and inside once the edge passes.
        assertFalse(AtomicPillarShape.inside(AtomicLook.IMPACT+1,20,1,0,R));
        assertTrue(AtomicPillarShape.inside(AtomicPillarShape.open(R),20,1,0,R));
        assertTrue(AtomicPillarShape.inside(AtomicLook.COLLAPSE+.5f,20,1,0,R));
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
