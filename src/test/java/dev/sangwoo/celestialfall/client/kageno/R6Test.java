package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.combat.MotionPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class R6Test {
    private static R6.Dir limbDir(float[] e){
        float k=(float)Math.PI/180;var v=new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k).transform(new Vector3f(0,1,0));
        return new R6.Dir(-v.x,-v.y,-v.z);
    }
    private static void near(R6.Dir want,R6.Dir got,String what){
        assertTrue(want.norm().dot(got.norm())>.999,what+" want "+want+" got "+got);
    }
    @Test void limbsPointWhereAuthored(){
        double[][] dirs={{-90,0},{0,0},{0,90},{0,-90},{45,30},{-30,-120},{80,170},{-60,45}};
        for(var d:dirs){
            var f=R6.solve(R6.pose().rArm(d[0],d[1]).lArm(d[0],-d[1]).rLeg(d[0],d[1]).lLeg(d[0],d[1]).blade(10,d[1]+90));
            near(R6.dir(d[0],d[1]),limbDir(f.joints().joint[MotionPose.RIGHT_ARM]),"right arm "+d[0]+","+d[1]);
            near(R6.dir(d[0],-d[1]),limbDir(f.joints().joint[MotionPose.LEFT_ARM]),"left arm");
            near(R6.dir(d[0],d[1]),limbDir(f.joints().joint[MotionPose.RIGHT_LEG]),"right leg");
        }
    }
    @Test void bladeFollowsTheAuthoredDirection(){
        double[][] cases={{-90,0,0,0},{0,0,90,0},{0,-40,-30,60},{60,20,80,-10},{-20,90,-60,170},{30,-60,0,-150},{-80,10,-38,58}};
        for(var c:cases){
            var f=R6.solve(R6.pose().rArm(c[0],c[1]).blade(c[2],c[3]));
            near(R6.dir(c[2],c[3]),R6.bladeOf(f),"blade for arm "+c[0]+","+c[1]+" blade "+c[2]+","+c[3]);
        }
    }
    @Test void restPoseIsIdentity(){
        var f=R6.solve(R6.pose().rArm(-90,0).lArm(-90,0).rLeg(-90,0).lLeg(-90,0).blade(0,0));assertEquals(0,f.grip(),.01);
        for(int part=2;part<6;part++)for(int axis=0;axis<3;axis++)assertEquals(0,f.joints().joint[part][axis],.01,"part "+part);
        for(int i=0;i<2;i++){assertEquals(0,f.joints().elbow[i]);assertEquals(0,f.joints().knee[i]);}
    }
}
