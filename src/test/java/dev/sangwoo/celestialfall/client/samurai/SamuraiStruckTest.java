package dev.sangwoo.celestialfall.client.samurai;

import dev.sangwoo.celestialfall.combat.MotionPose;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.SLASH_SEC;
import static org.junit.jupiter.api.Assertions.*;

/** The limbs of the one a samurai's G has marked (2.144.0): they flow, they are thrown at the cuts, and their joints bend. */
class SamuraiStruckTest {
    static float turn(MotionPose a,MotionPose b){
        float most=0;
        for(int j=0;j<6;j++){
            var qa=new org.joml.Quaternionf().rotationZYX(rad(a.joint[j][2]),rad(a.joint[j][1]),rad(a.joint[j][0]));
            var qb=new org.joml.Quaternionf().rotationZYX(rad(b.joint[j][2]),rad(b.joint[j][1]),rad(b.joint[j][0]));
            most=Math.max(most,(float)Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(qa.dot(qb))))));
        }
        for(int i=0;i<2;i++){most=Math.max(most,Math.abs(a.elbow[i]-b.elbow[i]));most=Math.max(most,Math.abs(a.knee[i]-b.knee[i]));}
        return most;
    }
    static float rad(float d){return d*(float)Math.PI/180;}
    @Test void theyFlow(){
        for(float s=1;s<6.8f;s+=1/60f){
            float t=turn(SamuraiStruck.at(s),SamuraiStruck.at(s+1/60f));
            assertTrue(t<62,"a joint in one frame at "+s+": "+t);
        }
    }
    @Test void theyAreThrownAtTheCutsAndHeldBetween(){
        for(float at:new float[]{SamuraiKata.ARRIVE,SamuraiKata.WHIRL,3.23f,SLASH_SEC[0],SLASH_SEC[1],SLASH_SEC[2],SLASH_SEC[3]}){
            assertTrue(turn(SamuraiStruck.at(at-.03f),SamuraiStruck.at(at+.09f))>25,"thrown at "+at);
        }
        for(float s:new float[]{1.9f,3.0f,5.2f})assertTrue(turn(SamuraiStruck.at(s),SamuraiStruck.at(s+.1f))<6,"held at "+s);
    }
    @Test void theirJointsBend(){
        float elbow=0,knee=0;
        for(float s=1;s<6.8f;s+=.05f){MotionPose m=SamuraiStruck.at(s);elbow=Math.max(elbow,Math.abs(m.elbow[0]));knee=Math.max(knee,Math.abs(m.knee[0]));}
        assertTrue(elbow>30&&knee>20,"elbow "+elbow+" knee "+knee);
    }
}
