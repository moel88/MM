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

/** Regression checks for the user's corrected video (both hands, one revolution) and, from 2.38.0, the arms spread at the blast. */
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
        for(float t:new float[]{0,.12f,.317f,2,4.62f,4.82f,5.045f,5.075f,5.115f,5.3f,5.4f,5.5f,5.59f,12,21.9f,22.25f,22.6f}) {
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
    static Vector3f arm(float t,int joint) {
        var j=AtomicPose.frame(t).joints().joint[joint];
        return new Quaternionf().rotationZYX(j[2]*(float)Math.PI/180,j[1]*(float)Math.PI/180,j[0]*(float)Math.PI/180).transform(new Vector3f(0,1,0));
    }
    @Test void theArmsSpreadOutAndDownBeforeTheBlastAndStaySpreadThroughIt() {
        // User request (Atomic_Original.mp4): at the blast the arms are spread out and down to the sides, the sword
        // carried along the right arm, its tip down and out; the left hand is open, off the hilt.
        float flash=dev.sangwoo.celestialfall.client.atomic.AtomicLook.IMPACT;
        assertTrue(AtomicPose.SPREAD<flash&&AtomicPose.SPREAD<dev.sangwoo.celestialfall.AtomicTimeline.IMPACT/20f,"the arms are spread before the blast");
        var model=new MotionPlayerModel(false);
        for(float t:new float[]{AtomicPose.SPREAD,flash,5.6f,6,9.3f,12,17.7f,AtomicPose.LOWER_START}) {
            var right=arm(t,MotionPose.RIGHT_ARM);var left=arm(t,MotionPose.LEFT_ARM);
            // Model space: x the character's left, y down, z back.
            assertTrue(right.x<-.7&&left.x>.7,"arms out to the sides at "+t+": "+right+" "+left);
            for(var a:new Vector3f[]{right,left}){double below=Math.toDegrees(Math.asin(a.y));assertTrue(below>25&&below<45,"arm "+below+" degrees below level at "+t);}
            assertTrue(Math.abs(right.z)<.35&&Math.abs(left.z)<.35,"arms beside the body, not ahead of it, at "+t);
            var b=blade(t);
            assertTrue(Math.toDegrees(Math.acos(Math.min(1,b.dot(right))))<15,"the sword runs along the right arm at "+t);
            assertTrue(b.y>.4,"the sword's tip is down at "+t);
            MotionRig.reset(model);AtomicPose.apply(model,t);
            var hilt=AtomicPose.swordMatrix(t).transformPosition(new Vector3f(0,3.5f/16,0));
            assertTrue(palm(model.rightArm,-1).distance(hilt)*16<1.6,"the right hand holds the sword at "+t);
            assertTrue(palm(model.leftArm,1).distance(hilt)*16>8,"the left hand is open, away from the hilt, at "+t);
        }
    }
    @Test void theArmsComeDownTheSidesNotThroughTheFront() {
        // As in the clip the sword arm sweeps down the right side and the left arm opens out to the left.
        for(float t=AtomicPose.SPIN_END;t<=AtomicPose.SPREAD;t+=.005f) {
            var right=arm(t,MotionPose.RIGHT_ARM);
            assertTrue(Math.abs(right.z)<.35,"the sword arm swings through the front at "+t+": "+right);
            assertTrue(right.x<.1,"the sword arm crosses the body at "+t);
        }
        for(float t=5.115f;t<=AtomicPose.SPREAD;t+=.005f)assertTrue(arm(t,MotionPose.LEFT_ARM).z<.5,"the left arm swings behind at "+t);
        assertTrue(arm(AtomicPose.SPIN_END,MotionPose.LEFT_ARM).x>.8,"the left arm is out to the side while the sword is still up");
    }
    @Test void releaseAndLoweringAreContinuousAndReturnToTheUnchangedIdlePose() {
        for(float boundary:new float[]{.067f,.10f,.134f,.167f,.20f,.317f,4.45f,4.53f,4.62f,4.72f,4.82f,5,5.045f,5.075f,5.115f,5.19f,5.30f,5.36f,5.42f,5.50f,5.55f,6.15f,21.3f,21.9f,22.6f}) {
            var a=AtomicPose.swordMatrix(boundary-.00001f);var b=AtomicPose.swordMatrix(boundary+.00001f);
            assertTrue(a.transformPosition(new Vector3f(0,31.5f/16,0)).distance(b.transformPosition(new Vector3f(0,31.5f/16,0)))<.01,"sword jump at "+boundary);
            var pa=AtomicPose.frame(boundary-.00001f).joints();var pb=AtomicPose.frame(boundary+.00001f).joints();
            for(int i=0;i<6;i++)for(int j=0;j<3;j++)assertEquals(pa.joint[i][j],pb.joint[i][j],.5,"joint jump at "+boundary);
        }
        // At 60 fps no arm turns too far between two frames from the release to the spread and through the lowering,
        // nor the sword once the (unchanged) overhead turn has left it.
        for(float[] span:new float[][]{{5.0f,5.8f},{21.8f,22.7f}})for(float t=span[0];t<span[1];t+=1/60f) {
            for(int joint:new int[]{MotionPose.RIGHT_ARM,MotionPose.LEFT_ARM}) {
                double turn=Math.toDegrees(Math.acos(Math.min(1,arm(t,joint).dot(arm(t+1/60f,joint)))));
                assertTrue(turn<20,"arm "+joint+" turns "+turn+" degrees in a frame at "+t);
            }
            if(t<AtomicPose.SPIN_END)continue;
            double sword=Math.toDegrees(Math.acos(Math.min(1,blade(t).dot(blade(t+1/60f)))));
            assertTrue(sword<20,"the sword turns "+sword+" degrees in a frame at "+t);
        }
        var idle=R6.solve(KagenoClips.IDLE);var end=AtomicPose.frame(AtomicPose.END);
        for(int i=0;i<6;i++)assertArrayEquals(idle.joints().joint[i],end.joints().joint[i],.0001f);
        var normal=AtomicPose.heldMatrix(idle.joints(),AtomicPose.END,Arm.RIGHT).rotateX(idle.grip()*(float)Math.PI/180).mul(AtomicPose.itemMatrix());
        var actual=AtomicPose.swordMatrix(AtomicPose.END);
        assertEquals(0,normal.transformPosition(new Vector3f(0,1,0)).distance(actual.transformPosition(new Vector3f(0,1,0))),.00001);
        assertTrue(blade(AtomicPose.SPIN_END).y<-.99,"the turn leaves the sword upright");
        // Lowered while the column dies, before the caster is let go.
        assertTrue(AtomicPose.LOWER_START>dev.sangwoo.celestialfall.AtomicTimeline.IMPACT/20f+10);
        assertTrue(AtomicPose.END*20<dev.sangwoo.celestialfall.AtomicTimeline.CAST_LOCK_END);
        assertEquals(112,dev.sangwoo.celestialfall.AtomicTimeline.IMPACT);
        assertEquals(465,dev.sangwoo.celestialfall.AtomicTimeline.CAST_LOCK_END);
        assertEquals(552,dev.sangwoo.celestialfall.AtomicTimeline.END);
    }
}
