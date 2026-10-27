package dev.sangwoo.celestialfall.client.kageno;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;
import static org.junit.jupiter.api.Assertions.*;

/** The keyframe engine: classic eases on their own, flow through breakdown keys, settled extremes, no jumps, hit-stops. */
class AnimEngineTest {
    static final R6.Pose A=pose().rArm(-80,10).blade(-40,30),B=pose().rArm(-20,40).blade(10,60),C=pose().rArm(30,70).blade(50,90);
    static Quaternionf arm(R6.Frame f){float[] e=f.joints().joint[2];float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}
    static double angle(R6.Frame a,R6.Frame b){return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(arm(a).dot(arm(b))))));}
    /** Angular speed of the sword arm at t (degrees per second). */
    static double speed(Anim a,float t){float h=.0005f;return angle(a.frame(t-h),a.frame(t+h))/(2*h);}

    @Test void aSingleSegmentKeepsTheClassicEaseCurve(){
        for(Anim.Ease e:new Anim.Ease[]{Anim.Ease.LINEAR,Anim.Ease.SMOOTH,Anim.Ease.OUT,Anim.Ease.IN}){
            Anim a=Anim.joints(k(0,A),k(1,B,e));
            for(float t=0;t<=1;t+=.05f){
                var want=new Quaternionf(arm(R6.solve(A))).slerp(arm(R6.solve(B)),Anim.ease(e,t));
                assertTrue(Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(want.dot(arm(a.frame(t)))))))<.2,e+" at "+t);
            }
        }
    }
    @Test void aKeyInsideAContinuingMotionIsPassedThroughNotStoppedAt(){
        Anim a=Anim.joints(k(0,A),k(.2,B),k(.4,C));
        double mean=angle(a.frame(0),a.frame(.4f))/.4;
        assertTrue(speed(a,.2f)>.5*mean,"stops at the breakdown key: "+speed(a,.2f)+" vs mean "+mean);
        // and passes exactly through it
        assertTrue(angle(a.frame(.2f),R6.solve(B))<.1);
    }
    @Test void aKeyWhereTheMotionTurnsBackSettlesWithoutOvershooting(){
        Anim a=Anim.joints(k(0,A),k(.2,B),k(.4,A));
        assertTrue(speed(a,.2f)<2,"the extreme should settle, speed "+speed(a,.2f));
        double reach=angle(R6.solve(A),R6.solve(B));
        for(float t=0;t<=.4f;t+=.01f)assertTrue(angle(R6.solve(A),a.frame(t))<=reach+.5,"overshoots at "+t);
    }
    @Test void anArrivalAtSpeedCarriesOnIntoASmoothFollowThrough(){
        Anim a=Anim.joints(k(0,A),k(.1,B,Anim.Ease.IN),k(.3,C));
        double in=speed(a,.1f-.002f),out=speed(a,.1f+.002f),follow=angle(R6.solve(B),R6.solve(C))/.2;
        // It keeps going at the most a follow-through of that length can carry (3x its mean speed) instead of stopping.
        assertTrue(out>2.5*follow&&out<=in*1.01,"the follow-through starts at "+out+" after arriving at "+in+" (its mean "+follow+")");
    }
    @Test void holdAndSnapMoveIntoTheirKeyWithinTwoFramesWithoutJumping(){
        for(Anim.Ease e:new Anim.Ease[]{Anim.Ease.HOLD,Anim.Ease.SNAP}){
            Anim a=Anim.joints(k(0,A),k(.5,B,e));
            assertTrue(angle(a.frame(.5f-Anim.QUICK-.001f),R6.solve(A))<.1,e+" holds until two frames before");
            assertTrue(angle(a.frame(.5f),R6.solve(B))<.1,e+" lands on its key");
            for(float t=0;t<.5f;t+=1/960f)assertTrue(angle(a.frame(t),a.frame(t+1/960f))<12,e+" jumps at "+t);
        }
    }
    @Test void cutJumpsAtItsKey(){
        Anim a=Anim.joints(k(0,A),k(.5,B,Anim.Ease.CUT));
        assertTrue(angle(a.frame(.499f),R6.solve(A))<.1);
        assertTrue(angle(a.frame(.5f),R6.solve(B))<.1);
    }
    @Test void aHitStopLeavesTheImpactOnTimeThenCatchesUp(){
        Anim a=Anim.joints(k(0,A),k(.3,B,Anim.Ease.LINEAR),k(.8,C)).stops(Anim.stop(.3,.06,.12));
        assertEquals(.3f,a.warp(.3f),1e-6);
        assertEquals(.3f+.06f*Anim.CREEP,a.warp(.36f),1e-5,"nearly frozen right after the impact");
        assertEquals(.48f,a.warp(.48f),1e-6,"back in step after the catch-up");
        for(float t=0;t<.8f;t+=.001f)assertTrue(a.warp(t+.001f)>=a.warp(t),"time runs backwards at "+t);
        assertThrows(IllegalArgumentException.class,()->a.stops(Anim.stop(.3,.06,.12),Anim.stop(.4,.05,.1)));
    }
    @Test void anglesRebuildTheSameRotationAtGimbalLock(){
        // A limb straight out to the side solves to a yaw of ±90°, where pitch and roll are not separable.
        for(double el:new double[]{-60,-8,0,8,60})for(double az:new double[]{90,-90}){
            var f=R6.solve(pose().rArm(el,az).blade(0,az).lArm(el,-az));
            assertTrue(Math.abs(R6.poseOf(f).rArm.dot(R6.dir(el,az))-1)<1e-4,"right arm at "+el+","+az);
            assertTrue(Math.abs(R6.poseOf(f).lArm.dot(R6.dir(el,-az))-1)<1e-4,"left arm at "+el+","+(-az));
        }
    }
    @Test void legsAndTheFreeArmDoNotTwistAroundWhenTheySwingAhead(){
        // A kick swinging from straight down through straight ahead: neighbouring solves stay close.
        R6.Frame previous=null;
        for(double el=-90;el<=20;el+=2){
            var f=R6.solve(pose().lLeg(el,0).lArm(el,-2));
            if(previous!=null){
                for(int j:new int[]{3,5}){
                    float[] a=previous.joints().joint[j],b=f.joints().joint[j];float k=(float)Math.PI/180;
                    double d=Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(new Quaternionf().rotationZYX(a[2]*k,a[1]*k,a[0]*k).dot(new Quaternionf().rotationZYX(b[2]*k,b[1]*k,b[0]*k))))));
                    assertTrue(d<5,"joint "+j+" turns "+d+" deg for a 2 deg swing at "+el);
                }
            }
            previous=f;
        }
    }
}
