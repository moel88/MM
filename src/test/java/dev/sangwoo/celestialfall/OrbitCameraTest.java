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
    /** (2.142.1) the player's own sight meets what the shift lock's crosshair is on, and ground at their feet turns them little. */
    @Test void thePlayersSightMeetsWhatTheCrosshairIsOn() {
        // the lock's camera: 5.5 behind the eye and .65 to its right, looking along +z
        double[] eye={0,1.62,0},cam={-.65,1.62,-5.5},look={0,0,1};double near=OrbitCameraState.SIGHT_NEAR;
        for(double far:new double[]{8,10,16,30,64}){
            double[] hit={cam[0],cam[1],far},at=OrbitCameraState.sight(cam,look,eye,hit,near);
            // far enough: the very point under the crosshair, so a ray from the eye toward it lands on it
            assertArrayEquals(hit,at,1e-9);
        }
        for(double far:new double[]{.5,2,4,7.9}){
            double[] at=OrbitCameraState.sight(cam,look,eye,new double[]{cam[0],cam[1],far},near);
            // nearer: still on the crosshair's line, SIGHT_NEAR out from abreast of the eye
            assertEquals(cam[0],at[0],1e-9);assertEquals(near,at[2],1e-9);
            // where the eye's ray toward it passes the thing's depth is within the shoulder's offset of the crosshair, the less the further
            double off=.65*(1-far/near);assertTrue(off<.65&&off>=0);
        }
        // the body is never turned further from the camera's way than atan(shoulder/near): 4.6 degrees
        for(double far=0;far<80;far+=.25){
            double[] at=OrbitCameraState.sight(cam,look,eye,new double[]{cam[0],cam[1],far},near);
            double turn=Math.toDegrees(Math.atan2(Math.abs(at[0]-eye[0]),at[2]-eye[2]));
            assertTrue(turn<=Math.toDegrees(Math.atan(.65/near))+1e-9&&turn<4.7,far+" "+turn);
        }
        // and continuous as the thing under the crosshair comes nearer
        double[] last=null;for(double far=20;far>=0;far-=.05){double[] at=OrbitCameraState.sight(cam,look,eye,new double[]{cam[0],cam[1],far},near);if(last!=null)assertTrue(Math.abs(at[2]-last[2])<.051);last=at;}
        // looking down at the ground by the feet, however steeply: the body's heading stays within 4.6 degrees of the camera's
        // (2.141.0 turned it 45 degrees and more; near is measured along the ground)
        for(double pitch:new double[]{20,40,60,74,80}){
            double c=Math.cos(Math.toRadians(pitch)),s=Math.sin(Math.toRadians(pitch));double[] down={0,-s,c},high={-.65,1.62+5.5*s,-5.5*c};
            double reach=(1.62+5.5*s)/s;double[] ground={high[0]+down[0]*reach,0,high[2]+down[2]*reach},at=OrbitCameraState.sight(high,down,eye,ground,near);
            assertEquals(-.65,at[0],1e-9);
            double heading=Math.toDegrees(Math.atan2(Math.abs(at[0]-eye[0]),at[2]-eye[2]));
            assertTrue(heading<=Math.toDegrees(Math.atan(.65/near))+1e-6,pitch+" "+heading);
            // and they look as far down as the camera, nearly
            double looks=Math.toDegrees(Math.atan2(eye[1]-at[1],Math.hypot(at[0]-eye[0],at[2]-eye[2])));assertEquals(pitch,looks,1.5);
        }
        // with no shoulder (the first person, the lock off) the line goes through the eye: the camera's own way
        double[] own=OrbitCameraState.sight(new double[]{0,1.62,-5.5},look,eye,new double[]{0,1.62,2},near);
        assertEquals(0,own[0],1e-9);assertEquals(1.62,own[1],1e-9);assertTrue(own[2]>0);
    }
}
