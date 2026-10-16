package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import dev.sangwoo.celestialfall.client.MotionRig;
import dev.sangwoo.celestialfall.combat.MotionPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression checks for the user's corrected video: both hands, one revolution, lower. */
class AtomicPoseReferenceTest {
    static Vector3f palm(ModelPart arm,float lateral) {
        var m=new MatrixStack();arm.rotate(m);arm.getChild("elbow").rotate(m);
        return m.peek().getPositionMatrix().transformPosition(new Vector3f(lateral/16,5/16f,0));
    }
    static Vector3f blade(float t) {
        return AtomicPose.swordMatrix(t).transformDirection(new Vector3f(0,1,0)).normalize();
    }
    @Test void bothHandsStayOnTheHandleDuringTheOverheadHoldAndTurn() {
        for(boolean slim:new boolean[]{false,true}) {
            var m=new MotionPlayerModel(slim);
            for(float t=.317f;t<=5.075f;t+=1/120f) {
                var p=AtomicPose.frame(t).joints();MotionRig.reset(m);AtomicPose.apply(m,t);
                var hilt=AtomicPose.swordMatrix(t).transformPosition(new Vector3f(0,3.5f/16,0));
                var right=palm(m.rightArm,slim?-.5f:-1);var left=palm(m.leftArm,slim?.5f:1);
                assertTrue(right.distance(hilt)*16<1.6,"right hand off hilt at "+t+": "+right.distance(hilt)*16);
                assertTrue(left.distance(hilt)*16<2.0,"left hand off hilt at "+t+": "+left.distance(hilt)*16);
                assertTrue(right.y<0&&left.y<0,"both hands must be above the shoulders at "+t);
            }
        }
    }
    @Test void bladeMakesExactlyOneClockwiseRevolutionRatherThanTakingTheShortestPath() {
        double total=0,previous=Math.atan2(blade(4.45f).x,-blade(4.45f).y);
        for(int i=1;i<=600;i++) {
            float t=4.45f+(5.3f-4.45f)*i/600;
            var d=blade(t);double angle=Math.atan2(d.x,-d.y),delta=angle-previous;
            while(delta>Math.PI)delta-=2*Math.PI;while(delta<-Math.PI)delta+=2*Math.PI;
            assertTrue(delta>=-.00001,"turn reversed at "+t);total+=delta;previous=angle;
        }
        assertEquals(360,Math.toDegrees(total),.005);
        assertTrue(blade(4.62f).x>.9,"tip crosses the right side first");
        assertTrue(blade(5.045f).y>.85,"tip passes below the hilt");
        assertTrue(blade(5.115f).x<-.9,"tip completes the left half of the circle");
        assertTrue(blade(5.3f).y<-.99,"tip ends upright");
    }
    @Test void actualHeldItemMatricesKeepTheGripAtThePalmAndApplyTheWholeRotation()throws Exception {
        var field=MotionPlayerModel.class.getDeclaredField("articulated");field.setAccessible(true);
        var model=new MotionPlayerModel(false);field.setBoolean(model,true);
        for(float t:new float[]{0,.12f,.317f,2,4.62f,4.82f,5.045f,5.075f,5.115f,5.3f,5.4f,5.5f,5.59f,22.6f}) {
            var p=AtomicPose.frame(t).joints();MotionRig.reset(model);AtomicPose.apply(model,t);
            for(var side:Arm.values()) {
                var m=new MatrixStack();model.setArmAngle(side,m);
                m.multiply(new Quaternionf().rotationX((float)-Math.PI/2));m.multiply(new Quaternionf().rotationY((float)Math.PI));
                m.translate((side==Arm.RIGHT?1:-1)/16f,2/16f,-10/16f);
                AtomicPose.applyGrip(m,t,side);
                var rendered=new Matrix4f(m.peek().getPositionMatrix()).mul(AtomicPose.itemMatrix());
                var expected=AtomicPose.swordMatrix(t);
                for(var point:new Vector3f[]{new Vector3f(0,3.5f/16,0),new Vector3f(0,31.5f/16,0),new Vector3f(.1f,.5f,.03f)})
                    assertEquals(0,new Vector3f(point).mulPosition(rendered).distance(new Vector3f(point).mulPosition(expected)),.00001,"rendered sword at "+t+" "+side);
            }
        }
    }
    @Test void releaseAndLoweringAreContinuousAndReturnToTheUnchangedIdlePose() {
        for(float boundary:new float[]{.067f,.10f,.134f,.167f,.20f,.317f,4.45f,4.53f,4.62f,4.72f,4.82f,5,5.045f,5.075f,5.115f,5.19f,5.30f,5.50f}) {
            var a=AtomicPose.swordMatrix(boundary-.00001f);var b=AtomicPose.swordMatrix(boundary+.00001f);
            assertTrue(a.transformPosition(new Vector3f(0,31.5f/16,0)).distance(b.transformPosition(new Vector3f(0,31.5f/16,0)))<.01,"sword jump at "+boundary);
            var pa=AtomicPose.frame(boundary-.00001f).joints();var pb=AtomicPose.frame(boundary+.00001f).joints();
            for(int i=0;i<6;i++)for(int j=0;j<3;j++)assertEquals(pa.joint[i][j],pb.joint[i][j],.5,"joint jump at "+boundary);
        }
        var idle=R6.solve(KagenoClips.IDLE);var end=AtomicPose.frame(AtomicPose.END);
        for(int i=0;i<6;i++)assertArrayEquals(idle.joints().joint[i],end.joints().joint[i],.0001f);
        var normal=AtomicPose.heldMatrix(idle.joints(),AtomicPose.END,Arm.RIGHT).rotateX(idle.grip()*(float)Math.PI/180).mul(AtomicPose.itemMatrix());
        var actual=AtomicPose.swordMatrix(AtomicPose.END);
        assertEquals(0,normal.transformPosition(new Vector3f(0,1,0)).distance(actual.transformPosition(new Vector3f(0,1,0))),.00001);
        assertTrue(blade(AtomicPose.LOWER_START).y<-.99);
        float flash=dev.sangwoo.celestialfall.client.atomic.AtomicLook.IMPACT;
        assertTrue(AtomicPose.END<flash,"sword must finish lowering before the visual blast");
        assertTrue(AtomicPose.END<dev.sangwoo.celestialfall.AtomicTimeline.IMPACT/20f);
        for(float t:new float[]{5.50f,5.55f,flash,5.60f,6,12,22.6f}) {
            assertTrue(blade(t).y>.5,"sword must stay down before and throughout the blast at "+t);
            for(int i=0;i<6;i++)assertArrayEquals(idle.joints().joint[i],AtomicPose.frame(t).joints().joint[i],.0001f);
        }
        assertEquals(112,dev.sangwoo.celestialfall.AtomicTimeline.IMPACT);
        assertEquals(465,dev.sangwoo.celestialfall.AtomicTimeline.CAST_LOCK_END);
        assertEquals(552,dev.sangwoo.celestialfall.AtomicTimeline.END);
    }
}
