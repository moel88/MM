package dev.sangwoo.celestialfall.client.kageno;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationContinuityTest {
    private Quaternionf rotation(float[] e){
        float k=(float)Math.PI/180;
        return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k).normalize();
    }
    @Test void crossingAForwardAxisDoesNotFlipTheArm(){
        var clip=Anim.joints(Anim.k(0,R6.pose().lArm(-25,0)),Anim.k(1,R6.pose().lArm(25,0),Anim.Ease.LINEAR));
        Quaternionf previous=rotation(clip.frame(0).joints().joint[3]);
        for(int frame=1;frame<=240;frame++){
            Quaternionf current=rotation(clip.frame(frame/240f).joints().joint[3]);
            assertTrue(Math.abs(previous.dot(current))>.999,"rotation jumped at sample "+frame);
            previous=current;
        }
    }
    @Test void opposingDirectionsStayFiniteAndUnitLength(){
        for(int i=0;i<=240;i++){
            var d=R6.Dir.lerp(R6.FORWARD,R6.dir(0,180),i/240.0);
            assertTrue(Double.isFinite(d.rt())&&Double.isFinite(d.up())&&Double.isFinite(d.fw()));
            assertEquals(1,d.dot(d),1e-8);
        }
    }
    @Test void frameConsumersCannotMutateTheCachedAnimation(){
        var clip=Anim.joints(Anim.k(0,R6.pose().rArm(30,45)),Anim.k(1,R6.pose().rArm(55,65)));
        var first=clip.frame(.5f);float expected=first.joints().joint[2][0];
        first.joints().joint[2][0]=1000;
        assertEquals(expected,clip.frame(.5f).joints().joint[2][0],1e-5);
    }
    @Test void fullRootSpinIsPreserved(){
        var clip=Anim.joints(Anim.k(0,R6.pose()),Anim.k(1,R6.pose().body(-360,0,0),Anim.Ease.LINEAR));
        assertEquals(-180,clip.frame(.5f).yaw(),1e-4);
        assertEquals(-360,clip.frame(1).yaw(),1e-4);
    }
    @Test void swordKeyframesStillMatchTheirAuthoredDirections(){
        var a=R6.pose().rArm(-15,55).blade(80,5);
        var b=R6.pose().rArm(62,-78).blade(88,0);
        var clip=Anim.joints(Anim.k(0,a),Anim.k(1,b));
        assertTrue(R6.bladeOf(clip.frame(0)).dot(a.blade)>.999);
        assertTrue(R6.bladeOf(clip.frame(1)).dot(b.blade)>.999);
    }
}
