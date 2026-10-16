package dev.sangwoo.celestialfall;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicTimelineTest {
    @Test void referenceClockMatchesTheReviewFootage() {
        // t0 = 144.733 s: impact frames at 150.323 s, beam gone at 166.8 s, grade back at 167.95 s, crater gone at 172.3 s.
        assertEquals(5.59,AtomicTimeline.sec(AtomicTimeline.IMPACT),.02);
        assertEquals(22.07,AtomicTimeline.sec(AtomicTimeline.BEAM_END),.03);
        assertEquals(23.25,AtomicTimeline.sec(AtomicTimeline.GRADE_END),.03);
        assertEquals(27.57,AtomicTimeline.sec(AtomicTimeline.END),.05);
        assertTrue(AtomicTimeline.IMPACT<AtomicTimeline.SHAKE_START&&AtomicTimeline.SHAKE_START<AtomicTimeline.SLABS);
        assertTrue(AtomicTimeline.COLLAPSE<AtomicTimeline.BEAM_END&&AtomicTimeline.BEAM_END<AtomicTimeline.INTERIOR_END);
        assertTrue(AtomicTimeline.GRADE_END<AtomicTimeline.BREAK_START&&AtomicTimeline.BREAK_START<AtomicTimeline.SHAKE_END&&AtomicTimeline.SHAKE_END<AtomicTimeline.END);
    }
    @Test void damageRunsOnceASecondFromImpactToTheBeam() {
        int hits=0;
        for(int a=-5;a<=700;a++){
            boolean due=AtomicTimeline.damageDue(a);
            boolean expected=a>=AtomicTimeline.IMPACT&&a<AtomicTimeline.BEAM_END&&(a-AtomicTimeline.IMPACT)%20==0;
            assertEquals(expected,due,"age "+a);if(due)hits++;
        }
        assertEquals(17,hits);
    }
    @Test void casterIsLockedUntilTheGradeFades() {
        assertFalse(AtomicTimeline.locked(-1));assertTrue(AtomicTimeline.locked(0));
        assertTrue(AtomicTimeline.locked(AtomicTimeline.GRADE_END-1));assertFalse(AtomicTimeline.locked(AtomicTimeline.GRADE_END));
    }
    @Test void theColumnHoldsWhatItHasReachedFromTheImpactUntilTheBeamEnds() {
        double R=28;
        assertEquals(.75,AtomicTimeline.HIT_SLOW);
        // Nothing before the impact, nothing once the beam has ended.
        for(long a=0;a<AtomicTimeline.IMPACT;a++)assertEquals(0,AtomicTimeline.holdRadius(a,R),"age "+a);
        for(long a=AtomicTimeline.BEAM_END;a<AtomicTimeline.END;a++)assertEquals(0,AtomicTimeline.holdRadius(a,R),"age "+a);
        // In between it is the damage column: opening with the pillar, the full radius through the collapse.
        for(long a=AtomicTimeline.IMPACT;a<AtomicTimeline.BEAM_END;a++)assertEquals(AtomicTimeline.damageRadius(a,R),AtomicTimeline.holdRadius(a,R),"age "+a);
        assertEquals(R,AtomicTimeline.holdRadius(AtomicTimeline.COLLAPSE+10,R));
        // Someone standing 4, 12, 20 or 26 blocks out is held from the tick the edge reaches them, before the hit that follows.
        double height=AtomicTimeline.damageHeight(R);
        long[][] expected={{4,130,132},{12,166,172},{20,201,212},{26,228,232}};
        for(long[] e:expected){
            long held=-1,hit=-1;
            for(long a=AtomicTimeline.IMPACT;a<AtomicTimeline.BEAM_END;a++){
                if(held<0&&AtomicTimeline.insideColumn(e[0],0,0,AtomicTimeline.holdRadius(a,R),height))held=a;
                if(hit<0&&AtomicTimeline.damageDue(a)&&AtomicTimeline.insideColumn(e[0],0,0,AtomicTimeline.damageRadius(a,R),height))hit=a;
            }
            assertEquals(e[1],held,"held at "+e[0]);assertEquals(e[2],hit,"hit at "+e[0]);
        }
        // Out of the column means not held, whatever the age.
        assertFalse(AtomicTimeline.insideColumn(R+.5,0,0,AtomicTimeline.holdRadius(AtomicTimeline.COLLAPSE,R),height));
    }
    @Test void damageCylinderBounds() {
        assertEquals(70,AtomicTimeline.damageHeight(28));
        assertTrue(AtomicTimeline.insideColumn(0,70,0,28));assertFalse(AtomicTimeline.insideColumn(0,70.01,0,28));
        assertTrue(AtomicTimeline.insideColumn(27.9,0,0,28));assertFalse(AtomicTimeline.insideColumn(28.1,0,0,28));
        assertFalse(AtomicTimeline.insideColumn(0,-3,0,28));assertFalse(AtomicTimeline.insideColumn(0,0,0,0));
    }
}
