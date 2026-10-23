package dev.sangwoo.celestialfall.client.meteor;

import dev.sangwoo.celestialfall.combat.MotionPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningPoseTest {
    private static final float RAD=(float)Math.PI/180;
    /** One 60 fps frame of the 6-tick swing. */
    private static final float FRAME=1/18f;

    private static Quaternionf turn(float[] a){return new Quaternionf().rotationZYX(a[2]*RAD,a[1]*RAD,a[0]*RAD);}
    private static Vector3f tip(MiningPose.Frame f){return f.tool().transformPosition(new Vector3f(10.2f/16,9.96f/16,.5f)).mul(16);}

    @Test void bothFistsCloseOnTheHandleThroughTheBlow(){
        float worst=0;
        for(int i=0;i<=360;i++){
            float s=i/360f;var f=MiningPose.frame(s,0);var p=f.pose();
            var right=MiningPose.fist(new Vector3f(-5,2-f.lift(),0),new float[]{p.joint[MotionPose.RIGHT_ARM][0],p.joint[MotionPose.RIGHT_ARM][1],p.joint[MotionPose.RIGHT_ARM][2],p.elbow[0]},-1);
            var left=MiningPose.fist(new Vector3f(5,2-f.lift(),0),new float[]{p.joint[MotionPose.LEFT_ARM][0],p.joint[MotionPose.LEFT_ARM][1],p.joint[MotionPose.LEFT_ARM][2],p.elbow[1]},1);
            worst=Math.max(worst,Math.max(right.distance(f.rightFist()),left.distance(f.leftFist())));
            // both fists stay ahead of the chest (its front is 2 px ahead of the neck)
            assertTrue(f.rightFist().z<-2.5f&&f.leftFist().z<-2.5f,"fist in the chest at "+s+": "+f.rightFist()+" "+f.leftFist());
        }
        assertTrue(worst<.05f,"a fist misses the handle by "+worst+" px");
    }
    @Test void firstPersonFistsCloseOnTheHandle(){
        float worst=0;
        for(int i=0;i<=360;i++){
            var v=MiningPose.view(i/360f);
            var tool=v.tool();
            Vector3f right=MiningPose.fist(v.rightShoulder(),v.rightArm(),-1),left=MiningPose.fist(v.leftShoulder(),v.leftArm(),1);
            // the fists' points on the handle, back through the pickaxe's own frame
            float r=MiningPose.rightFist(MiningPose.VIEW_SCALE)/(float)Math.sqrt(2),l=MiningPose.LEFT_FIST/(float)Math.sqrt(2);
            var handleRight=tool.transformPosition(new Vector3f(2.5f+r,2.25f+r,8).div(16)).mul(16);
            var handleLeft=tool.transformPosition(new Vector3f(2.5f+l,2.25f+l,8).div(16)).mul(16);
            worst=Math.max(worst,Math.max(right.distance(handleRight),left.distance(handleLeft)));
        }
        assertTrue(worst<.05f,"a first-person fist misses the handle by "+worst+" px");
    }
    @Test void firstPersonKeepsTheArmsAwayFromTheEye(){
        // the eye, from the neck: 2.16 px up and 1 px ahead; every point of both forearms (all first person draws of the
        // arms) on screen (the hand's 70 degree view, 1280x730) stays over 7 px from it, so they never fill the picture
        var eye=new Vector3f(0,-2.16f,-.96f);float nearest=1e9f;String where="";
        double halfUp=Math.toRadians(35),halfSide=Math.atan(Math.tan(halfUp)*1280/730);
        for(int i=0;i<=120;i++){
            var v=MiningPose.view(i/120f);
            for(int side=0;side<2;side++){
                var shoulder=side==0?v.rightShoulder():v.leftShoulder();var arm=side==0?v.rightArm():v.leftArm();
                var turn=new Quaternionf().rotationZYX(arm[2]*RAD,arm[1]*RAD,arm[0]*RAD);
                for(float y=4;y<=10;y+=.5f){
                    var local=y<=4?new Vector3f(0,y,0):new Vector3f(0,y-4,0).rotateX(arm[3]*RAD).add(0,4,0);
                    var point=turn.transform(local).add(shoulder);var rel=new Vector3f(point).sub(eye);
                    boolean seen=rel.z<0&&Math.abs(Math.atan2(rel.y,-rel.z))<halfUp&&Math.abs(Math.atan2(rel.x,-rel.z))<halfSide;
                    if(seen&&point.distance(eye)<nearest){nearest=point.distance(eye);where="swing "+i/120f+(side==0?" right":" left")+" arm at "+y+" px: "+rel;}
                }
            }
        }
        assertTrue(nearest>7,"a forearm on screen comes "+nearest+" px from the eye ("+where+")");
    }
    @Test void theHoldIsTheSameAtBothEndsOfABlow(){
        var a=MiningPose.frame(0,0).pose();var b=MiningPose.frame(1,0).pose();
        for(int i=0;i<6;i++)for(int j=0;j<3;j++)assertEquals(a.joint[i][j],b.joint[i][j],1e-3,"joint "+i);
        for(int i=0;i<2;i++){assertEquals(a.elbow[i],b.elbow[i],1e-3);assertEquals(a.knee[i],b.knee[i],1e-3);}
    }
    @Test void noJointJumpsBetweenTwoFramesAndThePickaxeLandsAtTheStrike(){
        float joint=0,tipStep=0,worstAt=0,lastStep=-1,ratio=0;
        for(float s=0;s<1;s+=FRAME/4){
            float t=Math.min(1,s+FRAME/4);
            var a=MiningPose.frame(s,0);var b=MiningPose.frame(t,0);
            for(int i=0;i<6;i++){
                float d=turn(a.pose().joint[i]).difference(turn(b.pose().joint[i]),new Quaternionf()).angle()/RAD;
                if(d>joint){joint=d;worstAt=s;}
            }
            float step=tip(a).distance(tip(b));tipStep=Math.max(tipStep,step);
            // away from the strike's stop, the head's speed changes gradually from one quarter frame to the next
            if(lastStep>.5f&&Math.abs(s-MiningPose.STRIKE)>FRAME/4)ratio=Math.max(ratio,Math.max(step/lastStep,lastStep/Math.max(step,1e-4f)));
            lastStep=step;
        }
        // a quarter frame: no joint turns more than 20 degrees, the pickaxe's head moves less than 6 px (a strike of
        // 1.5 blocks a frame at its fastest) and its speed never changes more than 2.5 times between two of them
        assertTrue(joint<20,"a joint turns "+joint+" degrees in a quarter frame at "+worstAt);
        assertTrue(tipStep<6,"the head jumps "+tipStep+" px in a quarter frame");
        assertTrue(ratio<2.5f,"the head's speed jumps "+ratio+" times");
        // the head is fastest right before the strike and still right after it
        float before=tip(MiningPose.frame(MiningPose.STRIKE-.01f,0)).distance(tip(MiningPose.frame(MiningPose.STRIKE,0)));
        float after=tip(MiningPose.frame(MiningPose.STRIKE,0)).distance(tip(MiningPose.frame(MiningPose.STRIKE+.01f,0)));
        assertTrue(before>4*after,"no stop at the strike: "+before+" then "+after);
    }
    @Test void theTopRaisesThePickaxeAndTheStrikeBringsItLowAhead(){
        var top=MiningPose.frame(.38f,0);var strike=MiningPose.frame(MiningPose.STRIKE,0);
        // torso space: the head above the neck at the top, low and ahead at the strike
        assertTrue(tip(top).y<-4,"head not raised: "+tip(top));
        assertTrue(tip(strike).y>6&&tip(strike).z<-6,"head not low ahead: "+tip(strike));
    }
}
