package dev.sangwoo.celestialfall;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import static org.junit.jupiter.api.Assertions.*;

class CinematicPathTest {
    @Test void cameraReturnsToPlayerAndShakeEnds() {
        assertEquals(0,CinematicPath.blend(-1));assertEquals(0,CinematicPath.blend(0));
        assertEquals(1,CinematicPath.blend(190));assertEquals(0,CinematicPath.blend(CinematicPath.END));
        assertEquals(0,CinematicPath.blend(10000));assertEquals(0,CinematicPath.shake(190));
        for(int i=0;i<3;i++)assertTrue(CinematicPath.shake(SpellTimeline.waveTick(i))>.7);
        for(float age=0;age<500;age+=.25f)assertTrue(CinematicPath.shake(age)>=0 && CinematicPath.shake(age)<=1);
        assertEquals(0,CinematicPath.shake(SpellTimeline.IMPACT+115));
    }
    @Test void sixDeliberateCutsStayWithinFiniteCameraBounds() {
        int[] cuts={0,60,136,156,174,206};
        for(int i=0;i<cuts.length;i++) {
            assertEquals(i,CinematicPath.shotIndex(cuts[i]));
            if(i>0)assertEquals(i-1,CinematicPath.shotIndex(cuts[i]-.01f));
        }
        for(float age=-2;age<450;age+=.125f) {
            var s=CinematicPath.sample(age);
            assertTrue(s.radius()>=6 && s.radius()<=290);assertTrue(s.height()>=2 && s.height()<=352);
            assertTrue(s.focusHeight()>=95 && s.focusHeight()<=408);assertTrue(s.fov()>=62 && s.fov()<=108);
            assertTrue(Double.isFinite(s.orbit()) && Math.abs(s.roll())<=9);
        }
    }
    @Test void contactHoldKeepsCameraPoseAndShakeStill() {
        var pose=CinematicPath.sample(SpellTimeline.CONTACT);
        for(float age=SpellTimeline.CONTACT;age<SpellTimeline.IMPACT;age+=.125f) {
            assertEquals(pose,CinematicPath.sample(age));assertEquals(0,CinematicPath.shake(age));
        }
        assertEquals(pose.radius(),CinematicPath.sample(SpellTimeline.IMPACT).radius());
        assertEquals(pose.height(),CinematicPath.sample(SpellTimeline.IMPACT).height());
    }
    @Test void worldRollAndBillboardRotationRemainConsistent() {
        // Minecraft's view includes a 180-degree Y flip. Equal local camera roll
        // and leading view roll cancel on billboards at arbitrary yaw/pitch.
        for(float yaw:new float[]{-172,0,68})for(float pitch:new float[]{-80,0,47})for(float roll:new float[]{-7,0,9}) {
            float y=(float)Math.toRadians(yaw),p=(float)Math.toRadians(pitch),r=(float)Math.toRadians(roll);
            Matrix4f view=new Matrix4f().rotateZ(r).rotateX(p).rotateY(y+(float)Math.PI);
            Quaternionf camera=new Quaternionf().rotationYXZ(-y,p,0).rotateZ(r);
            assertTrue(view.rotate(camera).equals(new Matrix4f().rotateY((float)Math.PI),.000002f));
        }
    }
}
