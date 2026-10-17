package dev.sangwoo.celestialfall.client.kageno;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R keeps every authored pose on its own time (the motion design of 2.28.1); between the keys it now moves on
 * smooth curves. 2.28.1's in-betweens flipped the sword arm by up to 179 degrees in an instant at the cuts.
 */
class RMotionPreservationTest {
    static Quaternionf q(float[] e){float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}
    static double angle(float[] a,float[] b){return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(q(a).dot(q(b))))));}
    @Test void everyAuthoredRPoseIsReachedOnItsKey(){
        var clip=TimeWalkerSkill.CLIP;float[] t=clip.times();
        assertEquals(19,t.length);
        assertArrayEquals(new float[]{1.018f,1.218f,1.384f,1.535f},TimeWalkerSkill.CUTS);
        for(int k=0;k<t.length;k++){
            var f=clip.frame(t[k]);var key=clip.key(k);
            for(int j=0;j<6;j++)assertTrue(angle(f.joints().joint[j],key.joints().joint[j])<.1,"joint "+j+" at key "+k);
            assertEquals(key.grip(),f.grip(),.1);assertEquals(key.fw(),f.fw(),1e-3);assertEquals(key.up(),f.up(),1e-3);
            // (the release key sits 2 ms into the release hit-stop: a few hundredths of a degree)
            assertEquals(key.yaw(),f.yaw(),.1);assertEquals(key.pitch(),f.pitch(),.1);
        }
    }
    @Test void rMovesWithoutJumps(){
        var clip=TimeWalkerSkill.CLIP;float h=1/960f;var prev=clip.frame(0);
        for(float t=h;t<=clip.length();t+=h){
            var next=clip.frame(t);
            for(int j=0;j<6;j++)assertTrue(angle(prev.joints().joint[j],next.joints().joint[j])<12,"joint "+j+" jumps at "+t);
            prev=next;
        }
    }
}
