package dev.sangwoo.celestialfall;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpellTimelineTest {
    @Test void swordAcceleratesToContactAndNeverGoesBelowGround() {
        assertEquals(300,SpellTimeline.swordTipHeight(SpellTimeline.DESCENT_START),1e-8);
        double previous=301;
        for(float age=SpellTimeline.DESCENT_START;age<=SpellTimeline.END;age+=.125f) {
            double y=SpellTimeline.swordTipHeight(age);assertTrue(y>=0 && y<=previous+1e-8);previous=y;
        }
        assertEquals(0,SpellTimeline.swordTipHeight(SpellTimeline.CONTACT),1e-8);
        double early=SpellTimeline.swordTipHeight(160)-SpellTimeline.swordTipHeight(161);
        double late=SpellTimeline.swordTipHeight(200)-SpellTimeline.swordTipHeight(201);
        assertTrue(late>early*10);
    }
    @Test void contactHoldsFourTicksThenReleasesImpact() {
        assertEquals(4,SpellTimeline.IMPACT-SpellTimeline.CONTACT);
        assertTrue(SpellTimeline.SILENCE_START<SpellTimeline.CONTACT);
        assertFalse(SpellTimeline.contactHold(SpellTimeline.CONTACT-.01f));
        for(float age=SpellTimeline.CONTACT;age<SpellTimeline.IMPACT;age+=.125f) {
            assertTrue(SpellTimeline.contactHold(age));assertEquals(SpellTimeline.CONTACT,SpellTimeline.motionAge(age));
            assertEquals(1,SpellTimeline.monoImpact(age));assertEquals(0,SpellTimeline.descentPower(age));
        }
        assertFalse(SpellTimeline.contactHold(SpellTimeline.IMPACT));
        assertEquals(0,SpellTimeline.monoImpact(SpellTimeline.IMPACT+2));
    }
    @Test void everyConstellationCollisionHasItsOwnPulseBeforeDescent() {
        int previous=0;
        for(int i=0;i<3;i++) {
            int tick=SpellTimeline.clashTick(i);assertTrue(tick>previous && tick<SpellTimeline.DESCENT_START);
            assertEquals(1,SpellTimeline.clashPulse(tick));assertTrue(SpellTimeline.clashPulse(tick+5)<1);previous=tick;
        }
        assertEquals(0,SpellTimeline.clashPulse(160));assertThrows(IllegalArgumentException.class,()->SpellTimeline.clashTick(3));
    }
    @Test void effectFadesInAndFullyDisappears() {
        assertEquals(0,SpellTimeline.visibility(0));assertEquals(1,SpellTimeline.visibility(SpellTimeline.IMPACT));
        assertEquals(0,SpellTimeline.visibility(SpellTimeline.END));assertEquals(0,SpellTimeline.visibility(SpellTimeline.END+100));
        for(float age=0;age<800;age+=.2f)assertTrue(SpellTimeline.visibility(age)>=0 && SpellTimeline.visibility(age)<=1);
    }
    @Test void threeShockwavesHaveSeparateOnsetsAndBoundedExpansion() {
        int previousTick=0;
        for(int i=0;i<3;i++) {
            int tick=SpellTimeline.waveTick(i);assertTrue(tick>previousTick);previousTick=tick;
            assertEquals(0,SpellTimeline.waveRadius(tick-1,i));assertEquals(0,SpellTimeline.waveRadius(tick,i));
            assertTrue(SpellTimeline.waveRadius(tick+1,i)>0);
            double previous=0,limit=160*(1+i*.14);
            for(float age=tick;age<SpellTimeline.END;age+=.25f) {
                double radius=SpellTimeline.waveRadius(age,i);
                assertTrue(Double.isFinite(radius) && radius>=previous && radius<=limit);previous=radius;
            }
            assertEquals(limit,previous,1e-8);
        }
        assertThrows(IllegalArgumentException.class,()->SpellTimeline.waveTick(3));
    }
}
