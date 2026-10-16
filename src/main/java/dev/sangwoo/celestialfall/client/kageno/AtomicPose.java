package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.combat.MotionPose;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** G body/held sword only. Reference: 1000022861.mp4, cast flash at 0.916 s. */
public final class AtomicPose {
    private AtomicPose() {}
    /** Finish lowering before the visual flash (5.590 s) and server impact (5.600 s). */
    public static final float END=5.50f;
    static final float SPIN_START=4.45f,SPIN_END=5.30f;
    static final float LOWER_START=SPIN_END;
    private static final float RAD=(float)Math.PI/180,HILT=3.5f/16;
    private static final R6.Frame IDLE=R6.solve(KagenoClips.IDLE);
    private static final float[] TIMES={0,.067f,.10f,.134f,.167f,.20f,.317f,SPIN_START,4.82f,5.0f,5.075f,5.115f,SPIN_END,END};
    private static final R6.Frame[] KEYS={IDLE,IDLE,
        key(-68,8,-45,-10),key(-100,18,-85,-18),key(-132,26,-120,-26),
        key(-147,32,-142,-32),key(-160,31,-160,-31),key(-160,31,-160,-31),
        key(-145,38,-145,-38),key(-145,38,-145,-38),key(-160,31,-160,-31),
        key(-171,10,-160,-35),key(-176,-4,-6,-1),IDLE};
    private static final float[] SPIN_TIMES={4.45f,4.53f,4.62f,4.72f,4.82f,5.0f,5.045f,5.075f,5.115f,5.19f,SPIN_END};
    private static final float[] SPIN_ANGLES={0,15,70,105,120,120,155,240,280,325,360};

    private static R6.Frame key(float rightPitch,float rightRoll,float leftPitch,float leftRoll) {
        MotionPose p=IDLE.joints().copy();
        p.rot(MotionPose.RIGHT_ARM,rightPitch,0,rightRoll).rot(MotionPose.LEFT_ARM,leftPitch,0,leftRoll);
        return new R6.Frame(p,IDLE.grip(),0,0,0,0,0,0);
    }
    private static Quaternionf rotation(float[] joint) {
        return new Quaternionf().rotationZYX(joint[2]*RAD,joint[1]*RAD,joint[0]*RAD);
    }
    /** Unwrapped, one-way revolution. Shortest-path quaternion blending erases a full turn. */
    public static float spinDegrees(float sec) {
        if(sec<=SPIN_START)return 0;
        for(int i=1;i<SPIN_TIMES.length;i++)if(sec<SPIN_TIMES[i]) {
            float t=(sec-SPIN_TIMES[i-1])/(SPIN_TIMES[i]-SPIN_TIMES[i-1]);
            return SPIN_ANGLES[i-1]+(SPIN_ANGLES[i]-SPIN_ANGLES[i-1])*t;
        }
        return 360;
    }
    private static Quaternionf idleBlade() {
        return rotation(IDLE.joints().joint[MotionPose.RIGHT_ARM])
            .rotateX(-90*RAD).rotateY(180*RAD).rotateX(IDLE.grip()*RAD).rotateY(-90*RAD);
    }
    static Quaternionf bladeRotation(float sec) {
        Quaternionf up=new Quaternionf().rotationX(180*RAD);
        if(sec<.317f)return idleBlade().slerp(up,Ease.smooth(sec,.10f,.317f));
        if(sec>=LOWER_START)return up.slerp(idleBlade(),Ease.smooth(sec,LOWER_START,END));
        // Model +X is the character's left: the tip rolls to screen-right, down, left, then up.
        return new Quaternionf().rotationZ(spinDegrees(sec)*RAD).mul(up);
    }
    static float shoulderLift(float sec,Arm side) {
        float lift=3*Ease.smooth(sec,.067f,.317f)*(1-Ease.smooth(sec,LOWER_START,END));
        return side==Arm.LEFT?lift*(1-Ease.smooth(sec,5.075f,SPIN_END)):lift;
    }
    /** A raised shoulder places the joined hands above the brow without stretching the skin mesh. */
    public static void apply(net.minecraft.client.render.entity.model.BipedEntityModel<?> model,float sec) {
        dev.sangwoo.celestialfall.client.MotionRig.apply(model,frame(sec).joints(),0,0);
        model.rightArm.pivotY-=shoulderLift(sec,Arm.RIGHT);
        model.leftArm.pivotY-=shoulderLift(sec,Arm.LEFT);
    }
    private static Matrix4f armMatrix(MotionPose p,float sec,Arm side) {
        int i=side==Arm.RIGHT?0:1;
        return new Matrix4f().translation((i==0?-5:5)/16f,(2-shoulderLift(sec,side))/16f,0)
            .rotate(rotation(p.joint[MotionPose.RIGHT_ARM+i]))
            .translate(0,4/16f,0).rotateX(p.elbow[i]*RAD).translate(0,-4/16f,0);
    }
    /** Vanilla transforms before the existing stand grip injection. */
    static Matrix4f heldMatrix(MotionPose p,float sec,Arm side) {
        return armMatrix(p,sec,side).rotateX(-90*RAD).rotateY(180*RAD)
            .translate((side==Arm.RIGHT?1:-1)/16f,2/16f,-10/16f);
    }
    /** Item display + item centring + KagenoSword mesh centring, in that order. */
    static Matrix4f itemMatrix() {
        // The left-hand JSON uses +90 Y, then Transformation.apply mirrors Y: also -90.
        return new Matrix4f().translation(0,1/16f,-1/16f).rotateY(-90*RAD).scale(.8f).translate(0,-.5f,0);
    }
    private static Vector3f gripPoint(MotionPose p,float sec) {
        var local=new Matrix4f().rotateX(-90*RAD).rotateY(180*RAD).translate(1/16f,2/16f,-10/16f)
            .rotateX(IDLE.grip()*RAD).mul(itemMatrix()).transformPosition(new Vector3f(0,HILT,0));
        float custom=Ease.smooth(sec,.067f,.317f)*(1-Ease.smooth(sec,LOWER_START,END));
        local.lerp(new Vector3f(0,10/16f,0),custom);
        return armMatrix(p,sec,Arm.RIGHT).transformPosition(local);
    }
    static Vector3f supportGrip(MotionPose p,float sec) {
        // Both hands share the same hilt point, at the inner edge of the left fingers.
        return gripPoint(p,sec);
    }
    static float supportWeight(float sec) {
        return Ease.smooth(sec,.134f,.317f)*(1-Ease.smooth(sec,5.075f,5.115f));
    }
    private static void supportHand(MotionPose p,float sec) {
        float weight=supportWeight(sec);if(weight<=0)return;
        Vector3f d=supportGrip(p,sec).mul(16).sub(5,2-shoulderLift(sec,Arm.LEFT),0);
        // 4 px upper arm + 6 px to the fingers. The shared hilt sits at local x=2,
        // inside both classic and slim hand geometry.
        float cosine=Math.max(-1,Math.min(1,(d.lengthSquared()-56)/48));
        float bend=-(float)Math.acos(cosine);
        Vector3f local=new Vector3f(2,4+6*(float)Math.cos(bend),6*(float)Math.sin(bend));
        Vector3f y=new Vector3f(d).normalize();
        Vector3f front=new Vector3f(0,0,-1);front.sub(new Vector3f(y).mul(front.dot(y))).normalize();
        Vector3f z=new Vector3f(front).negate(),x=new Vector3f(y).cross(z).normalize();
        var basis=new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,z);
        var desired=new Quaternionf().setFromNormalized(basis)
            .mul(new Quaternionf().rotationTo(local.normalize(),new Vector3f(0,1,0)));
        p.joint[MotionPose.LEFT_ARM]=R6.euler(rotation(p.joint[MotionPose.LEFT_ARM]).slerp(desired,weight));
        p.elbow[1]=(float)Math.toDegrees(bend)*weight;
    }
    public static R6.Frame frame(float sec) {
        float t=Float.isFinite(sec)?Math.max(0,sec):END;
        int key=0;while(key<TIMES.length-2&&t>=TIMES[key+1])key++;
        var f=R6.blend(KEYS[key],KEYS[key+1],Ease.smooth(t,TIMES[key],TIMES[key+1]));
        supportHand(f.joints(),t);return f;
    }
    /** Blade pivot at the palm, not the item model's remote origin: the hilt cannot orbit out of the hand. */
    static Matrix4f swordMatrix(float sec) {
        var p=frame(sec).joints();var grip=gripPoint(p,sec);
        return new Matrix4f().translation(grip).rotate(bladeRotation(sec)).scale(.8f).translate(0,-HILT,0);
    }
    static Matrix4f gripCorrection(float sec,Arm side) {
        return heldMatrix(frame(sec).joints(),sec,side).invert().mul(swordMatrix(sec)).mul(itemMatrix().invert());
    }
    public static void applyGrip(MatrixStack matrices,float sec,Arm side) {
        var correction=gripCorrection(sec,side);
        var pos=correction.getTranslation(new Vector3f());
        matrices.translate(pos.x,pos.y,pos.z);
        matrices.multiply(correction.getUnnormalizedRotation(new Quaternionf()).normalize());
    }
}
