package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.combat.MotionClips;
import dev.sangwoo.celestialfall.combat.MotionPose;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Switching what drives the player model fades from the pose on screen instead of popping. */
class PoseBlendTest {
    static MotionPlayerModel posed(MotionPose p){var m=new MotionPlayerModel(false);MotionRig.reset(m);MotionRig.apply(m,p,0,0);return m;}
    static MotionPose punch(){var p=MotionClips.ready(0);p.rot(MotionPose.RIGHT_ARM,-90,-10,5);p.elbow[0]=-5;p.rot(MotionPose.BODY,10,30,5);return p;}

    @Test void aNewSourceFadesInFromThePoseShownLast(){
        UUID id=UUID.randomUUID();PoseBlend.forget(id);
        var idle=MotionClips.ready(0);
        var m=posed(idle);PoseBlend.apply(id,m,"base",3,100);
        float startArm=m.rightArm.pitch;
        // The next frame something else drives the model: it starts from the pose shown, not from the new one.
        var target=posed(punch());float targetArm=target.rightArm.pitch;
        m=posed(punch());PoseBlend.apply(id,m,"attack",3,100.2f);
        assertEquals(startArm,m.rightArm.pitch,.02,"no pop on the first frame of the new source");
        float last=m.rightArm.pitch;
        for(float now=100.2f;now<=103.2f;now+=1/3f){
            m=posed(punch());PoseBlend.apply(id,m,"attack",3,now);
            assertTrue(Math.abs(m.rightArm.pitch-last)<.35,"jumps at "+now);last=m.rightArm.pitch;
        }
        m=posed(punch());PoseBlend.apply(id,m,"attack",3,103.5f);
        assertEquals(targetArm,m.rightArm.pitch,1e-4,"the new source shows on its own after the fade");
    }
    @Test void severalModelUpdatesInOneFrameAgree(){
        UUID id=UUID.randomUUID();PoseBlend.forget(id);
        var m=posed(MotionClips.ready(0));PoseBlend.apply(id,m,"base",3,10);
        var a=posed(punch());PoseBlend.apply(id,a,"attack",3,11);
        var b=posed(punch());PoseBlend.apply(id,b,"attack",3,11);
        assertEquals(a.rightArm.pitch,b.rightArm.pitch,1e-6);assertEquals(a.body.yaw,b.body.yaw,1e-6);
    }
    @Test void aPoseFromLongAgoIsNotFadedFrom(){
        UUID id=UUID.randomUUID();PoseBlend.forget(id);
        var m=posed(MotionClips.ready(0));PoseBlend.apply(id,m,"base",3,10);
        var target=posed(punch());m=posed(punch());PoseBlend.apply(id,m,"attack",3,60);
        assertEquals(target.rightArm.pitch,m.rightArm.pitch,1e-5);
    }
    @Test void aBodyStoppedInTimeHoldsItsPoseAndFadesOnAsTimeMovesAgain(){
        // (2.67.0) while it stands stopped its clocks go on (another pose would be drawn), its body does not
        UUID id=UUID.randomUUID();PoseBlend.forget(id);
        var shown=posed(punch());PoseBlend.apply(id,shown,"attack",3,50.8f);
        float arm=shown.rightArm.pitch,body=shown.body.yaw,leg=shown.leftLeg.pitch;
        for(int i=0;i<100;i++){
            var m=posed(MotionClips.ready(0));
            assertTrue(PoseBlend.hold(id,m,50.8f));
            assertEquals(arm,m.rightArm.pitch,1e-6);assertEquals(body,m.body.yaw,1e-6);assertEquals(leg,m.leftLeg.pitch,1e-6);
        }
        // time moves again: the next frame's share of the tick may be smaller than the one it was held at
        var m=posed(MotionClips.ready(0));PoseBlend.apply(id,m,"base",3,50.3f);
        assertEquals(arm,m.rightArm.pitch,.02,"no pop as time moves again");
        float last=m.rightArm.pitch;
        for(float now=50.3f;now<=53.3f;now+=1/3f){
            m=posed(MotionClips.ready(0));PoseBlend.apply(id,m,"base",3,now);
            assertTrue(Math.abs(m.rightArm.pitch-last)<.35,"jumps at "+now);last=m.rightArm.pitch;
        }
        m=posed(MotionClips.ready(0));PoseBlend.apply(id,m,"base",3,53.6f);
        assertEquals(posed(MotionClips.ready(0)).rightArm.pitch,m.rightArm.pitch,1e-4,"its own pose after the fade");
    }
    @Test void withNoPoseShownAMomentBeforeNothingIsHeld(){
        UUID id=UUID.randomUUID();PoseBlend.forget(id);
        var m=posed(punch());float arm=m.rightArm.pitch;
        assertFalse(PoseBlend.hold(id,m,10));
        assertEquals(arm,m.rightArm.pitch,1e-6,"the model is left as it is");
        PoseBlend.apply(id,posed(MotionClips.ready(0)),"base",3,10);
        assertFalse(PoseBlend.hold(id,m,20),"a pose from long before is not held");
    }
    @Test void landingDipAndFlinchRiseAndFadeWithoutJumps(){
        assertEquals(0,MotionClips.dip(0),1e-6);assertEquals(1,MotionClips.dip(1.6f),1e-6);assertEquals(0,MotionClips.dip(7.6f),1e-6);
        for(float s=0;s<8;s+=.05f)assertTrue(Math.abs(MotionClips.dip(s+.05f)-MotionClips.dip(s))<.06,"dip jumps at "+s);
        assertEquals(0,MotionClips.recoil(0),1e-6);assertEquals(1,MotionClips.recoil(1),1e-6);
        for(float s=0;s<12;s+=.05f)assertTrue(Math.abs(MotionClips.recoil(s+.05f)-MotionClips.recoil(s))<.08,"flinch jumps at "+s);
        assertTrue(MotionClips.recoil(10)<.05f,"gone when the hurt time ends");
    }
    @Test void theAirbornePoseChangesSmoothlyFromRiseToFall(){
        MotionPose last=null;
        for(float rise=1;rise>=-1;rise-=.05f){
            var p=MotionClips.air(rise,0);
            if(last!=null)for(int j=0;j<6;j++)for(int a=0;a<3;a++)assertTrue(Math.abs(p.joint[j][a]-last.joint[j][a])<3,"joint "+j+" jumps at rise "+rise);
            last=p;
        }
    }
}
