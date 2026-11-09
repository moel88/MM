package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.combat.MotionPose;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * (2.105.0, the user: "왼쪽 팔을 굽어서 손을 엉덩이쪽에 가져다대서 뒷짐 지는 자세를 취하소 오른쪽손은 오른쪽으로 내밀어 이게
 * 기본적으로 서 있는 자세고 검은 평타나 스킬을 쓸때만 나오게") Lee So-sang at rest: her left hand behind her at the small
 * of her back, the elbow bent, clear of her body; her right hand held out down and to the right, the elbow soft; both
 * breathing a little and smoothly; the stance easing in and out without jumps; her sword rising into and sinking out
 * of one's own sight as it comes and goes.
 */
class IsosangIdleTest {
    static final float RAD=(float)Math.PI/180;
    /** The arms' middles off their pivots (wide arms). */
    static final float HALF=1;
    static Quaternionf turn(float[] arm){return new Quaternionf().rotationZYX(arm[2]*RAD,arm[1]*RAD,arm[0]*RAD);}
    /** Where an arm turned about its middle is, from its shoulder (torso px), {@code along} px past the elbow (4.5 the fist, 6 the arm's end). */
    static Vector3f at(Vector3f shoulder,float[] arm,float along){
        Vector3f lower=new Vector3f(0,along,0).rotateX(arm[3]*RAD).add(0,4,0);
        return turn(arm).transform(lower).add(shoulder);
    }
    static Vector3f along(Vector3f shoulder,float[] arm,float y){return turn(arm).transform(new Vector3f(0,y,0)).add(shoulder);}
    /** How far a point is outside the torso's box (x ±4, y 0 to 12, z ±2; negative inside). */
    static float outside(Vector3f p){
        float dx=Math.max(Math.max(-4-p.x,p.x-4),0),dy=Math.max(Math.max(-p.y,p.y-12),0),dz=Math.max(Math.max(-2-p.z,p.z-2),0);
        if(dx==0&&dy==0&&dz==0)return -Math.min(Math.min(Math.min(p.x+4,4-p.x),Math.min(p.y,12-p.y)),Math.min(p.z+2,2-p.z));
        return (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
    }

    @Test void theRightHandIsHeldOutToTheRight(){
        float[] r=IsosangIdle.arms(0,HALF)[0];Vector3f s=IsosangIdle.shoulder(0,HALF);
        Vector3f fist=at(s,r,4.5f),down=new Vector3f(fist).sub(s);
        assertEquals(IsosangIdle.fists(0,HALF)[0].distance(fist),0,1e-3f,"the fist where the stance puts it");
        // out to her right, down by her hip, a little before her
        assertTrue(fist.x<-9.5f,"out to the right: "+fist);
        assertTrue(down.y>5.5f&&down.y<8,"about as high as her hip: "+fist);
        assertTrue(fist.z<-.4f&&fist.z>-2.6f,"a little before her: "+fist);
        double out=Math.toDegrees(Math.atan2(-down.x,down.y));
        assertTrue(out>28&&out<42,"held out a third of the way to level, not hanging nor raised: "+out);
        assertTrue(-r[3]>8&&-r[3]<25,"the elbow just soft: "+r[3]);
        // the arm stays clear of her side
        for(float a=0;a<=6;a+=.5f)assertTrue(outside(at(s,r,a))>1.8f,"clear of her side at "+a);
    }

    @Test void theLeftHandIsBehindHerAtTheSmallOfHerBack(){
        float[] l=IsosangIdle.arms(0,HALF)[1];Vector3f s=IsosangIdle.shoulder(1,HALF);
        Vector3f fist=at(s,l,4.5f),e=along(s,l,4),top=along(s,l,-2);
        assertEquals(IsosangIdle.fists(0,HALF)[1].distance(fist),0,1e-3f,"the fist where the stance puts it");
        assertTrue(fist.z>4,"behind her, clear of her back (2 px behind her middle, the arm 4 px thick): "+fist);
        assertTrue(fist.x>1.5f&&fist.x<4,"at the left of the small of her back: "+fist);
        assertTrue(fist.y>6.5f&&fist.y<9,"at her waist behind her (her hip is at 12; the bent arm reaches no lower): "+fist);
        assertTrue(e.x>4.5f&&e.z>1.5f,"the elbow out to the left and back: "+e);
        assertTrue(-l[3]>50&&-l[3]<75,"the elbow bent: "+l[3]);
        double back=Math.toDegrees(Math.atan2(e.z-s.z,e.y-s.y));
        assertTrue(back>20&&back<40,"the upper arm back only a third of the way to level: "+back);
        assertTrue(top.z>-1.5f,"the top of the arm hardly comes forward out of her shoulder: "+top);
        // the forearm crosses her back without going into it (at most a corner of the arm at the elbow)
        for(float a=0;a<=6;a+=.25f){
            float o=outside(at(s,l,a));
            assertTrue(o>1.45f,"the forearm "+a+" px past the elbow is "+o+" px from her body");
        }
        for(float a=0;a<=4;a+=.25f)assertTrue(outside(along(s,l,a))>1.9f,"the upper arm clear of her side at "+a);
        // the hand lies against her back: its middle 2 px (half the arm) out from it, give or take
        float hand=outside(fist);
        assertTrue(hand>1.8f&&hand<3.2f,"the hand on her back: "+hand);
    }

    @Test void theGlowSitsAtTheFingersOutsideTheHand(){
        float[] r=IsosangIdle.arms(0,HALF)[0];Vector3f s=IsosangIdle.shoulder(0,HALF);
        Vector3f fist=at(s,r,4.5f),end=at(s,r,6),tips=IsosangIdle.fingers(0,HALF);
        assertEquals(1.6f,tips.distance(fist),1e-3f,"past the fist along the forearm");
        assertTrue(tips.distance(end)<.2f,"at the end of the arm: "+tips+" "+end);
        assertTrue(outside(tips)>outside(fist),"further from her body than the fist");
    }

    @Test void eachArmTurnsAboutItsOwnMiddle(){
        for(float half:new float[]{1,.5f}){
            var pose=new MotionPose();IsosangIdle.apply(pose,0,1,half);
            var shift=IsosangIdle.pivots(pose,1,half);
            for(int i=0;i<2;i++){
                Vector3f socket=new Vector3f(i==0?-5:5,2,0),mid=new Vector3f(i==0?-half:half,0,0);
                // the pivot moved, the arm turned: its middle at the pivot's height is where it is unturned
                Vector3f middle=turn(pose.joint[MotionPose.RIGHT_ARM+i]).transform(new Vector3f(mid)).add(socket).add(shift[i]);
                assertEquals(0,middle.distance(new Vector3f(socket).add(mid)),1e-4f,"arm "+i+" ("+half+")");
            }
            var none=IsosangIdle.pivots(pose,0,half);
            assertEquals(0,none[0].length()+none[1].length(),1e-6f,"walking (no stance): the pivots stay");
        }
    }

    @Test void slimArmsTakeTheSameStance(){
        float[][] wide=IsosangIdle.arms(0,1),slim=IsosangIdle.arms(0,.5f);
        for(int i=0;i<2;i++)for(int j=0;j<4;j++)assertEquals(wide[i][j],slim[i][j],1e-3f);
    }

    @Test void sheBreathesALittleAndSmoothly(){
        float[][] rest=IsosangIdle.arms(0,HALF);Vector3f rs=IsosangIdle.shoulder(0,HALF),ls=IsosangIdle.shoulder(1,HALF);
        Vector3f r0=at(rs,rest[0],4.5f),l0=at(ls,rest[1],4.5f);
        float[][] last=rest;
        for(float t=0;t<80;t+=1/3f){
            float[][] a=IsosangIdle.arms(t,HALF);
            assertTrue(at(rs,a[0],4.5f).distance(r0)<.3f,"the right hand stays where it is at "+t);
            assertTrue(at(ls,a[1],4.5f).distance(l0)<.3f,"the left hand stays where it is at "+t);
            for(int i=0;i<2;i++){
                float step=(float)Math.toDegrees(IsosangIdle.quat(a[i]).difference(IsosangIdle.quat(last[i]),new Quaternionf()).angle());
                assertTrue(step<1,"no jump in a 60th of a second at "+t+": "+step);
                assertTrue(Math.abs(a[i][3]-last[i][3])<1,"the elbow does not jump at "+t);
            }
            last=a;
        }
    }

    @Test void theStanceEasesInAndOut(){
        MotionPose base=new MotionPose();base.rot(MotionPose.RIGHT_ARM,20,0,4).rot(MotionPose.LEFT_ARM,-25,0,-4);base.elbow[0]=-10;base.elbow[1]=-30;
        var untouched=base.copy();IsosangIdle.apply(untouched,0,0,HALF);
        assertArrayEquals(base.joint[MotionPose.RIGHT_ARM],untouched.joint[MotionPose.RIGHT_ARM],1e-5f);
        assertEquals(base.elbow[1],untouched.elbow[1],1e-5f);
        var full=base.copy();IsosangIdle.apply(full,0,1,HALF);float[][] arms=IsosangIdle.arms(0,HALF);
        for(int i=0;i<2;i++){
            for(int j=0;j<3;j++)assertEquals(arms[i][j],full.joint[MotionPose.RIGHT_ARM+i][j],1e-4f);
            assertEquals(arms[i][3],full.elbow[i],1e-4f);
        }
        // in even steps, each turning an arm about as much as the next, always nearer the stance
        for(int i=0;i<2;i++){
            var target=IsosangIdle.quat(arms[i]);float lastLeft=Float.MAX_VALUE,most=0,least=Float.MAX_VALUE;Quaternionf before=null;
            for(int k=0;k<=20;k++){
                var p=base.copy();IsosangIdle.apply(p,0,k/20f,HALF);var q=IsosangIdle.quat(p.joint[MotionPose.RIGHT_ARM+i]);
                float left=q.difference(target,new Quaternionf()).angle();
                assertTrue(left<=lastLeft+1e-4f,"nearer the stance at every step");lastLeft=left;
                if(before!=null){float st=q.difference(before,new Quaternionf()).angle();most=Math.max(most,st);least=Math.min(least,st);}
                before=q;
            }
            assertTrue(most-least<.02f,"turned evenly: "+least+" to "+most);
        }
    }

    @Test void sheStandsStraightLeggedButStillStepsAndLands(){
        // (the user: "다리는 왜 구부리고 있어 펴") the rig's own standing knees are bent; hers are straight
        var stand=dev.sangwoo.celestialfall.combat.MotionClips.locomotion(0,0,1,0,0,0,0);
        assertTrue(stand.knee[0]>15,"the rig stands everyone a little bent: "+stand.knee[0]);
        IsosangIdle.straighten(stand,0);
        for(int i=0;i<2;i++){
            assertEquals(0,stand.knee[i],1e-4f,"the knee straight");
            assertEquals(0,stand.joint[MotionPose.RIGHT_LEG+i][0],1e-4f,"the leg straight down from the hip");
        }
        // walking, the steps keep their knees
        MotionPose walk=dev.sangwoo.celestialfall.combat.MotionClips.locomotion(1.3f,1,1,0,0,0,0),kept=walk.copy();
        IsosangIdle.straighten(kept,1);
        for(int i=0;i<2;i++)assertEquals(walk.knee[i],kept.knee[i],1e-5f);
        // in between, evenly
        float last=Float.MAX_VALUE;
        for(float a=0;a<=1;a+=.05f){
            var p=dev.sangwoo.celestialfall.combat.MotionClips.locomotion(0,a,1,0,0,0,0);float before=p.knee[0];
            IsosangIdle.straighten(p,a);
            assertTrue(p.knee[0]<=before+1e-4f,"never more bent than the rig's");
            if(a<.06f)assertEquals(0,p.knee[0],1e-3f);
        }
    }

    @Test void standingStillSheTakesAllOfIt(){
        assertEquals(1,IsosangIdle.weight(UUID.randomUUID(),0),1e-6f);
    }

    @Test void theSwordRisesIntoSightAndSinksOutOfIt(){
        assertEquals(0,IsosangSheath.raised(true,0),1e-6f);
        assertEquals(1,IsosangSheath.raised(true,IsosangSheath.RAISE),1e-6f);
        assertEquals(1,IsosangSheath.raised(false,0),1e-6f);
        assertEquals(0,IsosangSheath.raised(false,IsosangSheath.LOWER),1e-6f);
        float up=0,down=1;
        for(float t=0;t<=.3f;t+=1/60f){
            float u=IsosangSheath.raised(true,t),d=IsosangSheath.raised(false,t);
            assertTrue(u>=up-1e-6f&&d<=down+1e-6f,"rises and sinks without turning back");
            assertTrue(u-up<.35f&&down-d<.25f,"over several frames, not in one");
            up=u;down=d;
        }
    }
}
