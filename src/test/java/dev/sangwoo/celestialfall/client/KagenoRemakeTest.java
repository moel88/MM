package dev.sangwoo.celestialfall.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;
import static dev.sangwoo.celestialfall.combat.MotionPose.*;

class KagenoRemakeTest {
    @Test void highestLandingActuallyReversesTravelAndSettlesWithoutTeleporting(){
        assertEquals(0,KagenoMotion.hTravel(55));
        assertTrue(KagenoMotion.hTravel(60)<-1.3);
        assertEquals(-2.35,KagenoMotion.hTravel(64),.001);
        assertTrue(KagenoMotion.hTravel(69)>KagenoMotion.hTravel(67));
        assertEquals(.35,KagenoMotion.hTravel(72),.001);
        assertEquals(0,KagenoMotion.hTravel(78),.001);
        for(float t=55;t<80;t+=.01f)assertTrue(Math.abs(KagenoMotion.hTravel(t+.001f)-KagenoMotion.hTravel(t))<.001,"no slide teleport");
    }
    @Test void heldFireUsesLowGlideInsteadOfAnAlternatingRunCycle(){
        var a=KagenoMotion.sample(RUSH,33,0);var b=KagenoMotion.sample(RUSH,42,0);
        assertTrue(a.bob>3&&a.knee[0]>70&&a.knee[1]>60);
        assertEquals(a.joint[RIGHT_LEG][0],b.joint[RIGHT_LEG][0]);
        assertEquals(a.joint[LEFT_LEG][0],b.joint[LEFT_LEG][0]);
        assertTrue(rushSpeed(RUSH_START)>rushSpeed(RUSH_START+8)*4,"fast launch settles into a cutting glide");
        assertEquals(0,rushSpeed(RUSH_FINISH+1));
    }
    @Test void atomicActualHeldItemTransformPointsAboveTheHeadDuringChant(){
        for(float age:new float[]{14,45,82}){
            var p=dev.sangwoo.celestialfall.combat.AtomicMotion.sample(age);var b=p.joint[BODY];var arm=p.joint[RIGHT_ARM];float k=(float)Math.PI/180;
            var rot=new org.joml.Quaternionf().rotationZYX(b[2]*k,b[1]*k,b[0]*k)
                .mul(new org.joml.Quaternionf().rotationZYX(arm[2]*k,arm[1]*k,arm[0]*k)).rotateX(p.elbow[0]*k)
                .rotateX(-k*90).rotateY(k*180).rotateX(dev.sangwoo.celestialfall.combat.AtomicMotion.wrist(age)*k).rotateY(-k*90);
            assertTrue(rot.transform(new org.joml.Vector3f(0,1,0)).y<-.92,"real held sword points into sky");
        }
    }
    @Test void cameraIsContinuousWithinShotsAndCutsOnlyAtReferenceBoundaries(){
        for(float t=0;t<116;t+=.05f){var a=KagenoCameraPath.sample(t);var b=KagenoCameraPath.sample(t+.001f);
            if(KagenoCameraPath.crossesCut(t,t+.001f))continue;
            assertTrue(Math.abs(a.height()-b.height())<.01,"height at "+t);
            assertTrue(Math.abs(a.forward()-b.forward())<.01,"distance at "+t);
            assertTrue(Math.abs(a.focusHeight()-b.focusHeight())<.01,"focus at "+t);
            assertTrue(Math.abs(a.fov()-b.fov())<.02,"lens at "+t);
        }
    }
    @Test void referenceCameraHoldsCloseupsAndSkyComposition(){
        assertTrue(KagenoCameraPath.crossesCut(14.73f,14.74f));assertTrue(KagenoCameraPath.crossesCut(32.06f,32.07f));assertTrue(KagenoCameraPath.crossesCut(55.39f,55.41f));
        assertTrue(KagenoCameraPath.sample(20).height()>28&&KagenoCameraPath.sample(20).height()<30);
        assertEquals(70,KagenoCameraPath.sample(40).focusHeight(),.001);
        assertEquals(KagenoCameraPath.sample(82),KagenoCameraPath.sample(100));
        assertTrue(KagenoCameraPath.sample(82).right()<-2,"final shot views the right side of the caster");
        assertTrue(KagenoCameraPath.sample(74).forward()<1.3,"hand insert is a close shot");
    }
    @Test void cameraBlendsInAndHardCutsBackAtTheReferenceTime(){
        assertEquals(0,KagenoCameraPath.blend(0));assertEquals(1,KagenoCameraPath.blend(6));
        assertEquals(1,KagenoCameraPath.blend(100));assertEquals(0,KagenoCameraPath.blend(102.5f),1e-6);
        assertEquals(0,KagenoCameraPath.blend(116));
    }
    @Test void rebuiltTimelinesMatchTheReviewFootage(){
        // R: release at 36 (33.6 s), caster lock ends before the petals finish.
        assertTrue(inputEnd(R)<DURATION[R]);assertTrue(DURATION[R]>=72);
        // Y: slam crouch at 16, white blast and heavy hit at 24, plates linger ~3 s.
        assertEquals(16,KagenoFx.Y_SLAM);assertEquals(24,KagenoFx.Y_BLAST);assertEquals(14,damage(Y,24,0));
        assertTrue(KagenoFx.plateRise(KagenoFx.Y_BLAST)<.01f&&KagenoFx.plateRise(40)>.99f&&KagenoFx.plateRise(DURATION[Y])==0);
        assertTrue(KagenoFx.flash(Y,KagenoFx.Y_BLAST+1)>.9f&&KagenoFx.flash(Y,KagenoFx.Y_BLAST+8)<.01f);
        // T: white blast at the sword plant, fire ends before the black scorch fades.
        assertTrue(KagenoFx.flash(T,T_STRIKE+1)>.9f);assertEquals(112,DURATION[T]);
        // M1: four distinct stroke planes (two diagonals in opposite directions, ring, vertical).
        var a=KagenoFx.m1Plane(1,new net.minecraft.util.math.Vec3d(0,0,1),new net.minecraft.util.math.Vec3d(1,0,0));
        var b=KagenoFx.m1Plane(2,new net.minecraft.util.math.Vec3d(0,0,1),new net.minecraft.util.math.Vec3d(1,0,0));
        assertTrue(a.sweep()*b.sweep()<0,"hit 2 sweeps the other way");
        assertTrue(Math.abs(KagenoFx.m1Plane(3,new net.minecraft.util.math.Vec3d(0,0,1),new net.minecraft.util.math.Vec3d(1,0,0)).sweep())>Math.PI*2,"hit 3 wraps fully around");
        assertEquals(1,KagenoFx.m1Plane(4,new net.minecraft.util.math.Vec3d(0,0,1),new net.minecraft.util.math.Vec3d(1,0,0)).v().y,1e-9);
    }
    @Test void castClockHandlesLateArrivalWithoutDependingOnWorldTime(){
        var clock=new KagenoCastClock(27,100);
        assertEquals(27,clock.age(100,0));assertEquals(37.5,clock.age(110,.5f));
        assertEquals(38,clock.age(111,0));assertEquals(39,clock.age(111,4));
    }
    @Test void barrageExtendsElbowTowardsForwardLaneOnEveryHit(){
        for(int i=0;i<7;i++){var back=KagenoMotion.sample(E,8+i*6,0);var hit=KagenoMotion.sample(E,10+i*6,0);
            assertTrue(hit.elbow[0]>back.elbow[0]+60);
            assertTrue(hit.joint[RIGHT_ARM][0]<back.joint[RIGHT_ARM][0]-60);
            assertTrue(Math.abs(hit.joint[BODY][1]-back.joint[BODY][1])>=20,"torso recoils on every thrust");
            float rotation=(float)Math.toRadians(hit.joint[BODY][0]+hit.joint[RIGHT_ARM][0]+hit.elbow[0]);
            // Actual held-item order: shoulder, elbow, vanilla -90 X / 180 Y, grip correction, display transform.
            var blade=new org.joml.Vector3f(0,1,0);
            new org.joml.Quaternionf().rotationX(rotation).rotateX(-(float)Math.PI/2).rotateY((float)Math.PI).rotateX((float)Math.toRadians(KagenoMotion.wrist(10+i*6))).rotateY(-(float)Math.PI/2).transform(blade);
            assertTrue(blade.z<-.99f,"sword tip must point forward, not behind the caster");assertEquals(0,blade.y,.01);
        }
    }
    @Test void highestRaisesBladeAboveHeadThenLowersItBeforeResidualWave(){
        for(float age:new float[]{79,82,85}){
            var p=KagenoMotion.sample(H,age,0);
            float pitch=(float)Math.toRadians(p.joint[BODY][0]+p.joint[RIGHT_ARM][0]+p.elbow[0]);
            var blade=new org.joml.Vector3f(0,1,0);
            new org.joml.Quaternionf().rotationX(pitch).rotateX(-(float)Math.PI/2).rotateY((float)Math.PI).rotateX((float)Math.toRadians(KagenoMotion.hWrist(age))).rotateY(-(float)Math.PI/2).transform(blade);
            assertTrue(blade.y<-.94f,"model Y is inverted by entity rendering: blade must point into sky");
        }
        assertTrue(KagenoMotion.sample(H,90,0).joint[RIGHT_ARM][0]>30);
        assertEquals(-180,KagenoMotion.flip(22));
        var inverted=KagenoMotion.sample(H,22,0);
        assertTrue(inverted.knee[0]<10&&inverted.knee[1]<20,"inverted legs remain open rather than folding into a ball");
        assertTrue(inverted.joint[LEFT_LEG][0]<-45&&inverted.joint[RIGHT_LEG][0]>-20,"asymmetric inverted silhouette");
    }
    @Test void landingBladeExtendsSidewaysAndDoesNotPointAtTheCamera(){
        for(float age:new float[]{58,64,66}){
            var p=KagenoMotion.sample(H,age,0);var body=p.joint[BODY];var arm=p.joint[RIGHT_ARM];
            float k=(float)Math.PI/180;
            var rotation=new org.joml.Quaternionf().rotationZYX(body[2]*k,body[1]*k,body[0]*k)
                .mul(new org.joml.Quaternionf().rotationZYX(arm[2]*k,arm[1]*k,arm[0]*k)).rotateX(p.elbow[0]*k)
                .rotateX(-(float)Math.PI/2).rotateY((float)Math.PI).rotateX(KagenoMotion.hWrist(age)*k).rotateY(-(float)Math.PI/2);
            var blade=rotation.transform(new org.joml.Vector3f(0,1,0));
            assertTrue(blade.x<-.85f,"blade must reach out past the raised knee in the reference landing silhouette");
            assertTrue(Math.abs(blade.y)<.4f&&Math.abs(blade.z)<.4f,"blade must be almost horizontal, not foreshortened down the view axis");
        }
    }
    @Test void highestPoseAndWristAreContinuousAtEveryBoundary(){
        for(float a=0;a<116;a+=.05f){var p=KagenoMotion.sample(H,a,0);var q=KagenoMotion.sample(H,a+.001f,0);
            for(int joint=0;joint<6;joint++)for(int axis=0;axis<3;axis++)assertTrue(Math.abs(p.joint[joint][axis]-q.joint[joint][axis])<.12,"pose jump at "+a);
            assertTrue(Math.abs(KagenoMotion.hWrist(a)-KagenoMotion.hWrist(a+.001f))<.05);
        }
    }
    @Test void thrustLaneExcludesSidewaysAndRearTargets(){
        assertTrue(thrustLane(2,0,0,.3));assertTrue(thrustLane(3.4,.9,0,.3));
        assertFalse(thrustLane(-1,0,0,.3));assertFalse(thrustLane(1,2,0,.3));assertFalse(thrustLane(1,0,3,.3));
    }
}
