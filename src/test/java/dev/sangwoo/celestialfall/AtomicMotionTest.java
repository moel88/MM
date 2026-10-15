package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.combat.*;
import static dev.sangwoo.celestialfall.combat.MotionPose.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AtomicMotionTest {
    @Test void deliberateDrawHoldSlashAndRecoveryReplaceInstantRaisedPose() {
        assertTrue(AtomicMotion.sample(0).joint[RIGHT_ARM][0]>-40);
        assertTrue(AtomicMotion.sample(14).joint[RIGHT_ARM][0]<-100);
        assertTrue(AtomicMotion.sample(34).elbow[0]<-60,"chant bends the forearm to the forehead");
        assertTrue(AtomicMotion.sample(34).joint[LEFT_ARM][0]<-90,"supporting hand is raised too");
        assertTrue(AtomicMotion.sample(100).joint[RIGHT_ARM][0]+AtomicMotion.sample(100).elbow[0]<-175);
        assertTrue(AtomicMotion.sample(110).joint[RIGHT_ARM][0]>-40);
        assertTrue(AtomicMotion.sample(110).joint[BODY][1]>20);
        assertTrue(AtomicMotion.sample(100).joint[BODY][1]<-15);
        assertEquals(0,AtomicMotion.sample(144).joint[BODY][1]);
        assertTrue(AtomicMotion.capeSpread(118)>AtomicMotion.capeSpread(40)+15);
    }
    @Test void jointsAreContinuousFiniteAndFeetNeverKneelOrLieDown() {
        MotionPose previous=AtomicMotion.sample(0);
        for(float age=.125f;age<=190;age+=.125f) {
            MotionPose p=AtomicMotion.sample(age);
            for(int i=0;i<6;i++)for(int j=0;j<3;j++) {
                assertTrue(Float.isFinite(p.joint[i][j]));assertTrue(Math.abs(p.joint[i][j]-previous.joint[i][j])<4);
            }
            assertTrue(Math.abs(p.bob)<=.4);assertTrue(Math.abs(p.joint[BODY][0])<=12);
            for(float knee:p.knee)assertTrue(knee>=0&&knee<40);
            previous=p;
        }
        assertEquals(0,AtomicMotion.sample(Float.NaN).joint[BODY][1]);
    }
}
