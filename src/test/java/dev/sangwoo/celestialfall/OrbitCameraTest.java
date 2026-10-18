package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.combat.OrbitCameraState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrbitCameraTest {
    @Test void zoomStopsAtCapAndReachesFirstPersonFromEitherDirection() {
        var s=new OrbitCameraState();for(int i=0;i<100;i++)s.scroll(-1);assertEquals(14,s.targetDistance);
        for(int i=0;i<100;i++)s.scroll(1);for(int i=0;i<120;i++)s.step(1.0/60);assertTrue(s.firstPerson());assertEquals(0,s.distance);
        s.scroll(-1);assertEquals(OrbitCameraState.MIN_THIRD_DISTANCE,s.targetDistance);for(int i=0;i<60;i++)s.step(1.0/60);assertFalse(s.firstPerson());
    }
    @Test void orbitNeverFlipsAtPolesAndCrossesYawSeamContinuously() {
        var s=new OrbitCameraState();s.rotate(179,200);assertEquals(80,s.pitch);s.rotate(4,-400);assertEquals(-177,s.yaw);assertEquals(-75,s.pitch);
        assertEquals(182,OrbitCameraState.turn(179,-178,36),1e-5);
    }
    @Test void zoomAndShoulderTransitionAreFrameRateIndependent() {
        var a=new OrbitCameraState();var b=new OrbitCameraState();a.targetDistance=b.targetDistance=10;a.shiftLock=b.shiftLock=true;
        for(int i=0;i<30;i++)a.step(1.0/30);for(int i=0;i<144;i++)b.step(1.0/144);
        assertEquals(a.distance,b.distance,1e-8);assertEquals(a.shoulder,b.shoulder,1e-8);
    }
    @Test void cameraClipsImmediatelyButNeverPopsBackOutAfterWallClears() {
        var s=new OrbitCameraState();assertEquals(1.2,s.clip(1.2,1.0/60),1e-6);double out=s.clip(5.5,1.0/60);
        assertTrue(out>1.2&&out<2);assertEquals(.25,s.clip(.25,1.0/60),1e-6);
    }
    @Test void cameraRelativeMovementIsInvariantAcrossAllPlayerHeadings() {
        for(int yaw=-180;yaw<180;yaw+=15)for(int body=-180;body<180;body+=15)for(float[] input:new float[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1}}) {
            var m=OrbitCameraState.movement(input[0],input[1],yaw,body);
            double c=Math.toRadians(yaw),p=Math.toRadians(body);
            assertEquals(-Math.sin(c)*input[0]+Math.cos(c)*input[1],-Math.sin(p)*m[0]+Math.cos(p)*m[1],1e-6);
            assertEquals(Math.cos(c)*input[0]+Math.sin(c)*input[1],Math.cos(p)*m[0]+Math.sin(p)*m[1],1e-6);
        }
    }
    @Test void freeLookDoesNotRequireCaptureAndShiftLockAndFirstPersonDo() {
        var s=new OrbitCameraState();assertFalse(s.captured(false));assertTrue(s.captured(true));s.shiftLock=true;assertTrue(s.captured(false));s.shiftLock=false;s.distance=0;assertTrue(s.captured(false));
    }
    @Test void shoulderAndOrbitHaveCorrectWorldDirections() {
        var s=new OrbitCameraState();s.yaw=0;s.pitch=0;var p=s.offset(5,.65);assertEquals(-.65,p.x(),1e-6);assertEquals(-5,p.z(),1e-6);
        s.yaw=90;p=s.offset(5,.65);assertEquals(5,p.x(),1e-6);assertEquals(-.65,p.z(),1e-6);
        assertEquals(-90,OrbitCameraState.movementYaw(0,0,1));assertEquals(-180,OrbitCameraState.movementYaw(0,-1,0));
    }
}
