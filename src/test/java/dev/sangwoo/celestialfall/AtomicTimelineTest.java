package dev.sangwoo.celestialfall;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicTimelineTest {
    @Test void reviewUsesVioletExposureAndNeverAnOpaqueWhiteCard() {
        for(float age=-10;age<600;age+=.25f){assertFalse(AtomicTimeline.whiteout(age));assertEquals(0,AtomicTimeline.ink(age));assertTrue(AtomicTimeline.violet(age)>=0&&AtomicTimeline.violet(age)<=1);}
        assertEquals(0,AtomicTimeline.violet(127));assertEquals(1,AtomicTimeline.violet(140));assertEquals(0,AtomicTimeline.violet(468));
    }
    @Test void fullColumnAndFirstDamageShareReferenceImpact() {
        assertEquals(468,AtomicTimeline.END);assertEquals(128,AtomicTimeline.IMPACT);
        assertEquals(0,AtomicTimeline.blastRadius(127.999f,28));assertEquals(28,AtomicTimeline.blastRadius(128,28));
        assertEquals(1,AtomicTimeline.column(128));assertTrue(AtomicTimeline.damageDue(128));
    }
    @Test void collapseChangesOnlyHorizontalRadiusAndNeverHeight() {
        for(double base:new double[]{8,28,128,768}) {
            double previous=base;
            for(float a=AtomicTimeline.IMPACT;a<=AtomicTimeline.END;a+=.125f) {
                double r=AtomicTimeline.blastRadius(a,base);
                assertTrue(Double.isFinite(r)&&r>=0&&r<=base&&r<=previous);
                if(a<=AtomicTimeline.SHRINK_START)assertEquals(base,r);
                assertEquals(base*2.5+500,AtomicTimeline.columnHeight(base));
                if(a<AtomicTimeline.END)assertTrue(AtomicTimeline.insideColumn(0,base*2.5,0,r,AtomicTimeline.damageHeight(base)));
                previous=r;
            }
            assertEquals(base*.5,AtomicTimeline.blastRadius(450,base),1e-6);
            assertEquals(0,AtomicTimeline.blastRadius(468,base));
        }
    }
    @Test void exteriorCameraAndFocusHoldTheirHeightDuringContraction() {
        var held=AtomicTimeline.shot(432,28);
        for(float a=432;a<468;a+=.125f) {
            var s=AtomicTimeline.shot(a,28);
            assertEquals(held.radius(),s.radius());assertEquals(held.height(),s.height());assertEquals(held.focusHeight(),s.focusHeight());
        }
    }
    @Test void cameraStaysFiniteCloseAndReturnsAtReferenceEnd() {
        for(double radius:new double[]{8,28,768})for(float a=0;a<468;a+=.125f) {
            var s=AtomicTimeline.shot(a,radius);
            assertTrue(Double.isFinite(s.radius())&&s.radius()>1&&s.radius()<=Math.max(10,radius*2.15));
            assertTrue(s.height()>0&&s.height()<=Math.max(2,radius*.92));
            assertTrue(s.fov()>=40&&s.fov()<=80);assertEquals(0,s.roll());
        }
        assertEquals(0,AtomicTimeline.blend(0));assertEquals(0,AtomicTimeline.blend(468));
    }
    @Test void effectsEndAndNeverLeakToNextCast() {
        for(float a=468;a<668;a+=.25f) {
            assertEquals(0,AtomicTimeline.visibility(a));assertEquals(0,AtomicTimeline.charge(a));assertEquals(0,AtomicTimeline.shake(a));
            assertEquals(0,AtomicTimeline.blend(a));assertEquals(0,AtomicTimeline.ink(a));assertEquals(0,AtomicTimeline.column(a));
            assertEquals(0,AtomicTimeline.blastRadius(a,28));
        }
    }
    @Test void damagePulsesEverySecondStartingWithVisibleColumn() {
        int count=0;long previous=-1;
        for(long age=-10;age<=1000;age++) {
            boolean expected=age>=128&&age<468&&(age-128)%20==0;
            assertEquals(expected,AtomicTimeline.damageDue(age));
            if(expected){if(previous>=0)assertEquals(20,age-previous);previous=age;count++;}
        }
        assertEquals(17,count);assertEquals(448,previous);
    }
    @Test void highMonstersStayInsideWhileOutsideHorizontalBoundaryIsSafe() {
        assertEquals(570,AtomicTimeline.columnHeight(28));assertEquals(70,AtomicTimeline.damageHeight(28));
        assertFalse(AtomicTimeline.insideColumn(0,71,0,28));
        assertTrue(AtomicTimeline.insideColumn(0,70,0,1,70));
        assertFalse(AtomicTimeline.insideColumn(0,70.01,0,28,70));
        assertFalse(AtomicTimeline.insideColumn(1.1,20,0,1,70));
        assertFalse(AtomicTimeline.insideColumn(0,0,0,0,70));
        assertFalse(AtomicTimeline.insideColumn(0,-3,0,28,70));
    }
}
