package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionClips;
import dev.sangwoo.celestialfall.combat.MotionPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara standing at rest (2.116.0, user request: "엑스 차라 기본 자세를 만들자 다리와 팔과 손을 쭉 펴서 정자세로 있고
 * 다리를 살짝 벌리고 검들고 있는 팔도 살짝 벌리고 있고 패널 부분 팔은 몸에 붙이고 있다가 패널 소환하면 팔 벌리게"): at
 * attention, the legs straight and a little apart with the feet on the ground, the knife's arm straight and a little
 * out from the body, the other arm straight against the body and out from it as the panel is called, the left-handed
 * the mirror; walking, the legs and the free arm take the walk's own, the knife's arm (and the panel's) a quarter of its
 * swing; the panel carried through a hit as it is held standing; and the knife's hits begin and end in this very stance.
 */
class XCharaIdleTest {
    static final float RAD=(float)Math.PI/180;
    /** The rig's pose standing still (amount 0) or walking ahead (amount 1) at stride phase {@code phase}, at breath time {@code time}. */
    static MotionPose rig(float amount,float phase,float time){return MotionClips.locomotion(phase,amount,1,0,0,0,time);}
    /** Where a limb's end is from its root (model px: x to the left, y down, z back) with its turn {@code e} and its hinge bent {@code bend}: 12 px in two halves. */
    static Vector3f end(float[] e,float bend){
        var q=new Quaternionf().rotationZYX(e[2]*RAD,e[1]*RAD,e[0]*RAD);
        return q.transform(new Vector3f(0,6,0)).add(new Quaternionf(q).rotateX(bend*RAD).transform(new Vector3f(0,6,0)));
    }
    static double turn(float[] a,float[] b){
        var qa=new Quaternionf().rotationZYX(a[2]*RAD,a[1]*RAD,a[0]*RAD);var qb=new Quaternionf().rotationZYX(b[2]*RAD,b[1]*RAD,b[0]*RAD);
        return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(qa.dot(qb)))));
    }

    @Test void standingTheLegsAreStraightALittleApartAndTheFeetOnTheGround(){
        // the rig stands everyone with bent knees, the feet a quarter of a pixel off the ground
        var bent=rig(0,0,0);
        assertTrue(bent.knee[0]>15,"the rig's own standing knees are bent: "+bent.knee[0]);
        var p=rig(0,0,0);XCharaIdle.stand(p,0);
        for(int i=0;i<2;i++){
            float[] leg=p.joint[MotionPose.RIGHT_LEG+i];
            assertEquals(0,leg[0],1e-4,"no leg forward or back");assertEquals(0,p.knee[i],1e-4,"the knees straight");
            assertEquals((i==0?1:-1)*XCharaIdle.LEGS_OUT,leg[2],1e-4,"each leg a little out");
            Vector3f foot=end(leg,p.knee[i]);
            // (the right leg is at the model's -x: its foot further that way)
            assertTrue(i==0?foot.x<-.8f:foot.x>.8f,"the foot out to its own side: "+foot.x);
            assertEquals(24,12+p.bob+foot.y,.01,"the foot on the ground (24 px under the model's top)");
        }
        assertTrue(XCharaIdle.LEGS_OUT>=3&&XCharaIdle.LEGS_OUT<=8,"a little apart, not a straddle");
    }
    @Test void walkingTheLegsAreTheWalksOwnAndTheStanceEasesOutAsTheyGetGoing(){
        for(float phase=0;phase<6.3f;phase+=.4f){
            var walk=rig(1,phase,3);var p=rig(1,phase,3);XCharaIdle.stand(p,1);
            for(int i=0;i<2;i++){assertArrayEquals(walk.joint[MotionPose.RIGHT_LEG+i],p.joint[MotionPose.RIGHT_LEG+i],1e-6f);assertEquals(walk.knee[i],p.knee[i],1e-6);}
            assertEquals(walk.bob,p.bob,1e-6);
        }
        // by how little they walk, without a step
        float was=Float.NaN;
        for(int i=0;i<=100;i++){
            var p=rig(i/100f,1,0);XCharaIdle.stand(p,i/100f);
            if(!Float.isNaN(was))assertTrue(Math.abs(p.knee[0]-was)<4,"the knee steps at "+i);
            was=p.knee[0];
        }
    }
    @Test void theKnifesArmHangsStraightALittleOutAndTheOtherStraightAgainstTheBody(){
        for(boolean right:new boolean[]{true,false}){
            // (time 0: the breath at its middle)
            var p=rig(0,0,0);XCharaIdle.arms(p,right,1,0,0);
            int knife=right?0:1,off=1-knife;float out=right?1:-1;
            assertArrayEquals(new float[]{0,0,out*XCharaIdle.KNIFE_OUT},p.joint[MotionPose.RIGHT_ARM+knife],1e-4f,"the knife's arm straight down, out from the body");
            assertEquals(0,p.elbow[knife],1e-6,"its elbow straight");
            // the hand end of it is out to its own side (the right arm's at the model's -x), neither ahead nor behind
            Vector3f hand=end(p.joint[MotionPose.RIGHT_ARM+knife],0);
            assertTrue(hand.x*-out>2.5f&&Math.abs(hand.z)<1e-3,"out to its side: "+hand);
            assertEquals(0,turn(new float[]{0,0,0},p.joint[MotionPose.RIGHT_ARM+off]),1e-3,"the other arm straight down against the body");
            assertEquals(0,p.elbow[off],1e-6,"its elbow straight");
        }
        assertTrue(XCharaIdle.KNIFE_OUT>=8&&XCharaIdle.KNIFE_OUT<=20,"a little out");
        assertTrue(XCharaIdle.PANEL_OUT>XCharaIdle.KNIFE_OUT&&XCharaIdle.PANEL_OUT<=35,"the panel's arm out, further than the knife's");
    }
    @Test void asThePanelIsCalledItsArmComesOutStraightWithoutAJump(){
        for(boolean right:new boolean[]{true,false}){
            int off=right?1:0;float out=right?-1:1;
            var p=rig(0,0,0);XCharaIdle.arms(p,right,1,1,0);
            // (the breath's second clock is off its middle at time 0: within the breath of the stance's angle)
            assertEquals(out*XCharaIdle.PANEL_OUT,p.joint[MotionPose.RIGHT_ARM+off][2],XCharaIdle.PANEL_BREATH+1e-4,"out from the body");
            assertEquals(0,p.joint[MotionPose.RIGHT_ARM+off][0],1e-4);assertEquals(0,p.elbow[off],1e-6,"straight still");
            // with the hack's own measure of it (HackLook.arm), coming on and going off: no jump between frames
            for(boolean on:new boolean[]{true,false}){
                float was=Float.NaN;
                for(int i=0;i<=400;i++){
                    var q=rig(0,0,0);XCharaIdle.arms(q,right,1,HackLook.arm(on,i/1000f),0);
                    float roll=q.joint[MotionPose.RIGHT_ARM+off][2];
                    if(!Float.isNaN(was))assertTrue(Math.abs(roll-was)<.3f,"the arm jumps at "+i+" ms");
                    was=roll;
                }
                assertEquals(on?out*XCharaIdle.PANEL_OUT:0,was,XCharaIdle.PANEL_BREATH+1e-4,on?"out at the end":"back against the body at the end");
            }
        }
    }
    @Test void walkingTheKnifesArmKeepsAQuarterOfItsSwingAndTheFreeArmSwingsAsAnyones(){
        for(float phase=0;phase<6.3f;phase+=.3f){
            var walk=rig(1,phase,0);var p=rig(1,phase,0);XCharaIdle.arms(p,true,0,0,0);
            assertEquals(walk.joint[MotionPose.RIGHT_ARM][0]*XCharaIdle.SWING,p.joint[MotionPose.RIGHT_ARM][0],1e-5,"a quarter of the swing");
            assertEquals(XCharaIdle.KNIFE_OUT,p.joint[MotionPose.RIGHT_ARM][2],1e-4,"still out");assertEquals(0,p.elbow[0],1e-6,"still straight");
            assertArrayEquals(walk.joint[MotionPose.LEFT_ARM],p.joint[MotionPose.LEFT_ARM],1e-6f,"the free arm's own swing");
            assertEquals(walk.elbow[1],p.elbow[1],1e-6);
            // holding the panel: out, straight, a quarter of its swing
            var h=rig(1,phase,0);XCharaIdle.arms(h,true,0,1,0);
            assertEquals(walk.joint[MotionPose.LEFT_ARM][0]*XCharaIdle.SWING,h.joint[MotionPose.LEFT_ARM][0],1e-5);
            assertEquals(0,h.elbow[1],1e-6);
        }
        // the swing really is there to keep a quarter of
        float most=0;for(float phase=0;phase<6.3f;phase+=.1f)most=Math.max(most,Math.abs(rig(1,phase,0).joint[MotionPose.RIGHT_ARM][0]));
        assertTrue(most>15,"the walk swings the arm "+most);
    }
    @Test void theyBreatheALittleNoMore(){
        float lo=99,hi=-99;
        for(int t=0;t<400;t++){var p=rig(0,0,t);XCharaIdle.arms(p,true,1,1,t);float r=p.joint[MotionPose.RIGHT_ARM][2];lo=Math.min(lo,r);hi=Math.max(hi,r);
            float l=-p.joint[MotionPose.LEFT_ARM][2];assertEquals(XCharaIdle.PANEL_OUT,l,XCharaIdle.PANEL_BREATH+1e-4);}
        assertTrue(hi-lo>.3f&&hi-lo<1.2f,"the knife's arm breathes "+(hi-lo)+" deg");
    }
    @Test void aHitCarriesThePanelsArmOutAsItIsHeldStanding(){
        // the hit's stance has that arm against the body: carried, it is the stance's arm out
        var p=new MotionPose();XCharaIdle.carry(p,true,1);
        assertEquals(0,turn(new float[]{0,0,-XCharaIdle.PANEL_OUT},p.joint[MotionPose.LEFT_ARM]),.1);
        var l=new MotionPose();XCharaIdle.carry(l,false,1);
        assertEquals(0,turn(new float[]{0,0,XCharaIdle.PANEL_OUT},l.joint[MotionPose.RIGHT_ARM]),.1,"the left-handed's right arm, mirrored");
        // not held: as the hit has it
        var n=new MotionPose();n.rot(MotionPose.LEFT_ARM,-40,10,-5);XCharaIdle.carry(n,true,0);
        assertArrayEquals(new float[]{-40,10,-5},n.joint[MotionPose.LEFT_ARM],1e-6f);
        // an arm thrown ahead (the second hit's paste) still points ahead
        var a=new MotionPose();a.rot(MotionPose.LEFT_ARM,-90,0,0);XCharaIdle.carry(a,true,1);
        Vector3f hand=end(a.joint[MotionPose.LEFT_ARM],0);
        assertTrue(hand.z<-11.9f,"still ahead: "+hand);
        // and by how far it is held, without a jump
        double was=0;
        for(int i=0;i<=100;i++){
            var c=new MotionPose();c.rot(MotionPose.LEFT_ARM,-30,20,-10);XCharaIdle.carry(c,true,i/100f);
            double t=turn(new float[]{-30,20,-10},c.joint[MotionPose.LEFT_ARM]);
            assertTrue(t>=was-1e-3&&t-was<.4,"at "+i);was=t;
        }
    }
    @Test void theKnifesHitsBeginAndEndInThisVeryStance(){
        // the model's standing pose (the rig's, stood at attention) against the hits' rest, joint by joint
        var p=rig(0,0,0);XCharaIdle.stand(p,0);XCharaIdle.arms(p,true,1,0,0);
        var rest=KnifeCombo.AT_REST.joints();
        for(int j=MotionPose.RIGHT_ARM;j<=MotionPose.LEFT_LEG;j++)assertTrue(turn(p.joint[j],rest.joint[j])<.2,"joint "+j+" is "+turn(p.joint[j],rest.joint[j])+" deg off");
        for(int i=0;i<2;i++){assertEquals(p.elbow[i],rest.elbow[i],1e-4);assertEquals(p.knee[i],rest.knee[i],1e-4);}
        // and the knife in the hand as its display turns it: square to the straight arm, so level ahead, its spine up the arm
        var blade=R6.bladeOf(KnifeCombo.hold());
        assertEquals(1,blade.fw(),1e-4,"the knife level ahead: "+blade);
        assertEquals(0,KnifeCombo.DISPLAY_TILT,1e-6);
    }
}
