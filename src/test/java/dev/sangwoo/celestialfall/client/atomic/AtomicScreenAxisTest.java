package dev.sangwoo.celestialfall.client.atomic;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicScreenAxisTest {
    private static final Matrix4f PROJ=new Matrix4f().perspective((float)Math.toRadians(70),16/9f,.05f,4000);

    /** View-space point of a world point for a camera at the origin looking down -z (yaw only). */
    private static Vector4f view(double x,double y,double z){return new Vector4f((float)x,(float)y,(float)z,1);}

    @Test void pillarAheadIsOnScreenWithItsLightInTheMiddleOfTheVisiblePart() {
        // Pillar 130 blocks ahead, ground 12 below the eye, top 240 above the ground.
        float[] a=AtomicPillar.screenAxis(view(0,-12,-130),view(0,240,-130),PROJ,.05f);
        assertNotNull(a);
        assertEquals(1,a[6]);
        assertEquals(.5,a[0],1e-4);assertEquals(.5,a[2],1e-4);
        assertTrue(a[1]<.5&&a[3]>1,"ground below the middle, top above the frame");
        // The light centre is on screen, between the ground point and the top of the frame.
        assertTrue(a[5]>a[1]&&a[5]<1.05f,"light "+a[5]);
    }
    @Test void axisBehindTheCameraHasNoLight() {
        assertNull(AtomicPillar.screenAxis(view(0,-12,50),view(0,240,50),PROJ,.05f));
    }
    @Test void axisLeavingBehindTheCameraIsCutAtTheNearPlane() {
        // Standing beside the pillar looking along it: the ground point is ahead, the top swings behind.
        float[] a=AtomicPillar.screenAxis(view(0,-2,-30),view(0,200,40),PROJ,.05f);
        assertNotNull(a);
        assertTrue(Float.isFinite(a[2])&&Float.isFinite(a[3]));
        assertEquals(1,a[6]);
    }
    @Test void pillarOffToTheSideIsNotLit() {
        float[] a=AtomicPillar.screenAxis(view(400,-12,-60),view(400,240,-60),PROJ,.05f);
        assertNotNull(a);
        assertEquals(0,a[6]);
    }
}
