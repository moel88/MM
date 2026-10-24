package dev.sangwoo.celestialfall;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeavenTimelineTest {
    @Test void gifOrderHasFourSecondInsertionTwoSecondHoldAndGentleReturn() {
        assertEquals(244,HeavenTimeline.CAMERA_END);assertEquals(100,HeavenTimeline.IMPACT);
        assertTrue(HeavenTimeline.FACE<HeavenTimeline.PORTAL && HeavenTimeline.PORTAL<HeavenTimeline.REVEAL);
        assertTrue(HeavenTimeline.REVEAL<HeavenTimeline.DESCENT && HeavenTimeline.DESCENT<HeavenTimeline.IMPACT);
        assertEquals(80,HeavenTimeline.EMBED_END-HeavenTimeline.IMPACT);
        assertEquals(40,HeavenTimeline.RETURN_START-HeavenTimeline.EMBED_END);
        assertEquals(24,HeavenTimeline.CAMERA_END-HeavenTimeline.RETURN_START);
        assertEquals(1,HeavenTimeline.blend(HeavenTimeline.RETURN_START));
        assertEquals(0,HeavenTimeline.blend(HeavenTimeline.CAMERA_END));assertEquals(0,HeavenTimeline.blend(0));
    }
    @Test void swordContinuouslySlowsIntoTheGroundAndThenSettles() {
        double last=260;
        for(float age=0;age<HeavenTimeline.END;age+=.1f) {
            double y=HeavenTimeline.tipHeight(age);assertTrue(Double.isFinite(y) && y>=-96 && y<=last+1e-5);
            if(age<HeavenTimeline.DESCENT)assertEquals(260,y);
            if(age>=HeavenTimeline.EMBED_END)assertEquals(-96,y);
            last=y;
        }
        assertEquals(0,HeavenTimeline.tipHeight(HeavenTimeline.IMPACT),1e-9);
        for(int cut:new int[]{HeavenTimeline.DESCENT,HeavenTimeline.IMPACT,HeavenTimeline.EMBED_END}) {
            double before=(HeavenTimeline.tipHeight(cut)-HeavenTimeline.tipHeight(cut-.01f))/.01;
            double after=(HeavenTimeline.tipHeight(cut+.01f)-HeavenTimeline.tipHeight(cut))/.01;
            assertEquals(before,after,.025,"Continuous velocity at "+cut);
        }
        for(int t=HeavenTimeline.IMPACT;t<HeavenTimeline.EMBED_END;t++)
            assertTrue(HeavenTimeline.tipHeight(t)-HeavenTimeline.tipHeight(t+1)<1.7,"Slow insertion, not a second fall");
    }
    @Test void noEarlyImpactAndEffectsFinishCleanly() {
        assertEquals(0,HeavenTimeline.aftershock(99.9f));assertEquals(1,HeavenTimeline.impact(100));
        assertTrue(HeavenTimeline.pressure(130)>.9 && HeavenTimeline.aftershock(180)>0);
        for(float a=HeavenTimeline.END;a<HeavenTimeline.END+100;a+=.25f) {
            assertEquals(0,HeavenTimeline.visibility(a));assertEquals(0,HeavenTimeline.portal(a));
            assertEquals(0,HeavenTimeline.shake(a));assertEquals(0,HeavenTimeline.rush(a));assertEquals(0,HeavenTimeline.blend(a));
        }
    }
    @Test void fiveShotsHaveFiniteCoordinatesAndPreserveImpactFraming() {
        int[] cuts={0,20,28,62,100};for(int i=0;i<cuts.length;i++)assertEquals(i,HeavenTimeline.shotIndex(cuts[i]));
        for(float a=0;a<HeavenTimeline.CAMERA_END;a+=.125f) {
            var shot=HeavenTimeline.shot(a);assertTrue(shot.radius()>=.6 && shot.radius()<=280);
            assertTrue(shot.height()>0 && shot.height()<=64);assertTrue(shot.focusHeight()>0 && shot.focusHeight()<=140);
            assertTrue(shot.fov()>=24 && shot.fov()<=78);assertTrue(Math.abs(shot.roll())<=50);
        }
        assertEquals(HeavenTimeline.shot(99.999f).radius(),HeavenTimeline.shot(100).radius());
    }
    @Test void sealPoseHasBentElbowAndReturnsWithoutLeaking() {
        var pose=HeavenTimeline.pose(20);assertTrue(pose.elbow()<-120);assertTrue(pose.rightPitch()<0);
        assertTrue(HeavenTimeline.posed(61));assertFalse(HeavenTimeline.posed(62));
        var end=HeavenTimeline.pose(62);assertEquals(0,end.elbow(),1e-5);assertEquals(0,end.cape(),1e-5);
    }
    @Test void broadTaperedBladeAndPortalHaveBoundedScale() {
        assertEquals(0,HeavenTimeline.halfWidth(0));assertEquals(71,HeavenTimeline.halfWidth(240));
        for(double y=0;y<=720;y+=.25)assertTrue(HeavenTimeline.halfWidth(y)>=0 && HeavenTimeline.halfWidth(y)<=71);
        for(float a=0;a<HeavenTimeline.END;a+=.25) { assertTrue(HeavenTimeline.portal(a)>=0 && HeavenTimeline.portal(a)<=1);assertTrue(HeavenTimeline.reveal(a)>=0 && HeavenTimeline.reveal(a)<=1); }
        assertEquals(0,HeavenTimeline.reveal(31));assertEquals(1,HeavenTimeline.reveal(52));
    }
}
