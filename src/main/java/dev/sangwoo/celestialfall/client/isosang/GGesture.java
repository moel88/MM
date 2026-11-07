package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.*;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Anatomical RIGHT sword fingers; the left arm is folded behind the lower back.
 * Render-only articulated G pose. Does not change the existing camera's authored anchors. */
public final class GGesture {
    private GGesture() {}
    static final Vector3f LEFT_SHOULDER=new Vector3f(5,2,0);
    static final Vector3f SEAL_WRIST=new Vector3f(.6f,2.4f,-6.4f);
    static final Quaternionf SEAL_HAND=new Quaternionf().rotationX((float)Math.PI);
    // Only the authored shot path is sampled: every viewer sees the same 3D hand pose.
    static final StandClient.Cast REFERENCE_CAST=new StandClient.Cast(new java.util.UUID(0,0),new java.util.UUID(0,0),null,0,9,0,0,0,Vec3d.ZERO,0,java.util.List.of(),new float[0]);
    record Chain(Quaternionf upper,Quaternionf lower) {}

    public static void build(ModelPartData root,boolean slim){buildHand(root,slim,false);buildHand(root,slim,true);}
    private static void buildHand(ModelPartData root,boolean slim,boolean right){
        var elbow=root.getChild(right?"right_arm":"left_arm").getChild("elbow");
        float sign=right?-1:1;int du=right?8:0,dv=right?-32:0;float center=(slim?.5f:1f)*sign;
        // A short forearm, a distinct wrist and palm, followed by four fingers on one row.
        // Index and middle are adjacent and parallel; ring and little fold on the outer side.
        var hand=elbow.addChild("g_hand",ModelPartBuilder.create().uv(32+du,54+dv).mirrored(right)
            .cuboid(center-1.6f,0,-1.7f,3.2f,4.15f,3.4f),ModelTransform.NONE);
        var wrist=hand.addChild("wrist",ModelPartBuilder.create().uv(36+du,57+dv).mirrored(right)
            .cuboid(-1.45f,0,-.9f,2.9f,2.05f,1.8f),ModelTransform.pivot(center,4,0));
        finger(wrist,"index",-1.04f*sign,2.25f,36+du,dv,right);
        finger(wrist,"middle",-.31f*sign,2.4f,37+du,dv,right);
        for(int i=0;i<2;i++){
            float x=(.43f+.72f*i)*sign;
            wrist.addChild(i==0?"ring_fold":"little_fold",ModelPartBuilder.create().uv(38+i+du,60+dv).mirrored(right)
                .cuboid(x-.32f,1.82f,-.55f,.64f,.95f,.95f)
                .cuboid(x-.32f,2.12f,.30f,.64f,.65f,1.02f)
                .cuboid(x-.29f,1.42f,.78f,.58f,.90f,.68f),ModelTransform.NONE);
        }
        wrist.addChild("thumb",ModelPartBuilder.create().uv(36+du,60+dv).mirrored(right)
            .cuboid(-.34f,0,-.34f,.68f,1.5f,.68f),ModelTransform.of(-1.30f*sign,.8f,.82f,0,0,-.88f*sign));
    }
    private static void finger(ModelPartData wrist,String name,float x,float length,int u,int dv,boolean right){
        wrist.addChild(name,ModelPartBuilder.create().uv(u,60+dv).mirrored(right)
            .cuboid(-.34f,0,-.56f,.68f,1.55f,.86f)
            .cuboid(-.30f,1.55f,-.52f,.60f,length-1.55f,.78f),ModelTransform.pivot(x,1.85f,0));
    }
    public static boolean signing(net.minecraft.entity.Entity entity){
        var cast=IsosangClient.animated(entity.getUuid());
        if(cast==null||cast.combo()!=IsosangRules.ULTIMATE||!Float.isNaN(FallingSword.off(cast)))return false;
        float t=cast.age(MinecraftClient.getInstance().getTickDelta())/20f;
        return t>=.04f&&t<=2.8f;
    }
    public static void resetRight(net.minecraft.client.render.entity.model.BipedEntityModel<?> model){
        reset(model.rightArm);
        if(model instanceof PlayerEntityModel<?> p)p.rightSleeve.getChild("elbow").visible=true;
    }
    static void reflectPart(ModelPart from,ModelPart to){
        to.copyTransform(from);to.pivotX=-from.pivotX;to.yaw=-from.yaw;to.roll=-from.roll;
    }
    public static void reset(ModelPart arm){
        var elbow=arm.getChild("elbow");elbow.hidden=false;
        if(elbow.hasChild("g_hand")){
            var hand=elbow.getChild("g_hand");hand.visible=false;hand.resetTransform();
            if(hand.hasChild("wrist"))hand.getChild("wrist").resetTransform();
        }
    }
    /** Turn the entire palm edge-on. The two extended fingers line up in depth
     * instead of forming a broad side-by-side strip across the shot. */
    static Quaternionf flourish(float t){
        var shot=t<1.008f?FallingSword.shot(REFERENCE_CAST,t):FallingSword.face(REFERENCE_CAST,Math.min(t,1.6f));
        Vec3d toward=shot.eye().subtract(referenceWrist(t));
        return new Quaternionf().rotationY((float)Math.atan2(toward.z,toward.x));
    }
    /** Right wrist in the same normalized world frame as the authored camera. */
    static Vec3d referenceWrist(float t){
        var frame=FallingSword.CLIP.frame(t);var pose=R6.poseOf(frame);float k=(float)Math.PI/180;
        float[] joint=frame.joints().joint[3];float blend=seal(t);
        var upper=new Quaternionf().rotationZYX(joint[2]*k,joint[1]*k,joint[0]*k);
        var lower=new Quaternionf(upper).rotateX(frame.joints().elbow[1]*k);
        var chain=solve(LEFT_SHOULDER,SEAL_WRIST,4,new Vector3f(8,7,-1));
        upper.slerp(chain.upper,blend);lower.slerp(chain.lower,blend);
        var point=new Vector3f(LEFT_SHOULDER).add(upper.transform(new Vector3f(0,4,0))).add(lower.transform(new Vector3f(.75f,4,0)));
        point.x=-point.x;float scale=.9375f;
        var local=new Vector3f(point.x/16*scale,(1.501f-point.y/16)*scale-KagenoClips.PIVOT,-point.z/16*scale);
        var root=new Quaternionf().rotationY(pose.yaw*k).rotateX(pose.pitch*k).rotateZ(pose.roll*k);
        root.transform(local);
        return new Vec3d(local.x-pose.rt,local.y+pose.up+KagenoClips.PIVOT,local.z+pose.fw);
    }
    /**
     * How far the two fingers are held upright (2.87.0): as the videos', they run on along the forearm through the shots of
     * the hand (pointing ahead, then up and out under the arm), turn up as the hand comes before the face (0.86 to
     * 0.99 s) and stay upright at the lips. The preview held them upright from the first shot.
     */
    static float uprightWeight(float t){return Ease.smooth(t,.86f,.99f)*(1-Ease.smooth(t,2.70f,2.80f));}
    /** Keep the right index/middle axis vertical from the opening hand shot,
     * independently of the arm sweep and the small animated torso lean. */
    static Quaternionf upright(float t){
        var pose=R6.poseOf(FallingSword.CLIP.frame(t));float k=(float)Math.PI/180;
        var root=new Quaternionf().rotationY(-pose.yaw*k).rotateX(pose.pitch*k).rotateZ(-pose.roll*k);
        var target=root.conjugate().mul(SEAL_HAND);
        // apply() subsequently mirrors the authored left-hand solution to the right.
        return new Quaternionf(target.x,-target.y,-target.z,target.w).mul(flourish(t));
    }
    static Quaternionf wristPose(Quaternionf lower,float t){
        return new Quaternionf().slerp(new Quaternionf(lower).conjugate().mul(upright(t)),uprightWeight(t));
    }
    static float seal(float t){return Ease.smooth(t,1.008f,1.15f)*(1-Ease.smooth(t,2.70f,2.80f));}
    static Quaternionf orientation(Vector3f direction){
        Vector3f y=new Vector3f(direction).normalize(),z=new Vector3f(0,0,1);
        z.sub(new Vector3f(y).mul(y.dot(z)));
        if(z.lengthSquared()<1e-5f)z.set(1,0,0).sub(new Vector3f(y).mul(y.x));
        z.normalize();Vector3f x=new Vector3f(y).cross(z).normalize();z.set(x).cross(y).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,z));
    }
    static Chain solve(Vector3f shoulder,Vector3f wrist,float lower,Vector3f pole){
        Vector3f d=new Vector3f(wrist).sub(shoulder);float distance=d.length();d.div(distance);
        float reach=Math.min(4+lower-.001f,Math.max(Math.abs(4-lower)+.001f,distance));
        float along=(16-lower*lower+reach*reach)/(2*reach),height=(float)Math.sqrt(Math.max(0,16-along*along));
        Vector3f bend=new Vector3f(pole).sub(shoulder);bend.sub(new Vector3f(d).mul(bend.dot(d))).normalize();
        Vector3f elbow=new Vector3f(shoulder).add(new Vector3f(d).mul(along)).add(bend.mul(height));
        return new Chain(orientation(new Vector3f(elbow).sub(shoulder)),orientation(new Vector3f(wrist).sub(elbow)));
    }
    static Quaternionf rotation(ModelPart part){return new Quaternionf().rotationZYX(part.roll,part.yaw,part.pitch);}
    static void rotation(ModelPart part,Quaternionf q){
        // Match ModelPart's Rz * Ry * Rx exactly. The bundled JOML 1.10.5
        // getEulerAnglesZYX has a wrong sign in its pitch denominator.
        q=new Quaternionf(q).normalize();
        part.pitch=(float)Math.atan2(2*(q.w*q.x+q.y*q.z),1-2*(q.x*q.x+q.y*q.y));
        part.yaw=(float)Math.asin(Math.max(-1,Math.min(1,2*(q.w*q.y-q.z*q.x))));
        part.roll=(float)Math.atan2(2*(q.w*q.z+q.x*q.y),1-2*(q.y*q.y+q.z*q.z));
    }
    static void applyChain(ModelPart arm,Chain chain,float blend){
        Quaternionf upper=rotation(arm),lower=new Quaternionf(upper).mul(rotation(arm.getChild("elbow")));
        upper.slerp(chain.upper,blend).normalize();lower.slerp(chain.lower,blend).normalize();
        rotation(arm,upper);rotation(arm.getChild("elbow"),new Quaternionf(upper).conjugate().mul(lower));
    }
    public static void apply(PlayerEntityModel<AbstractClientPlayerEntity> model,AbstractClientPlayerEntity player,float delta){
        var cast=IsosangClient.animated(player.getUuid());
        // (2.101.0) H again: the same hand, its two fingers raised before her face
        if(cast!=null&&cast.combo()==IsosangRules.FINALE){applyFinale(model,cast,delta);return;}
        if(cast==null||cast.combo()!=IsosangRules.ULTIMATE||!Float.isNaN(FallingSword.off(cast)))return;
        float t=cast.age(delta)/20f;if(t<.04f||t>2.8f)return;
        var originalLeft=model.leftArm.getTransform();var originalRight=model.rightArm.getTransform();
        var originalLeftElbow=model.leftArm.getChild("elbow").getTransform();var originalRightElbow=model.rightArm.getChild("elbow").getTransform();
        float held=Ease.smooth(t,.04f,.16f)*(1-Ease.smooth(t,2.70f,2.8f));
        // The opposite hand stays behind the waist instead of cutting across the chest.
        var right=solve(new Vector3f(-5,2,0),new Vector3f(-.6f,7.7f,4.2f),6,new Vector3f(-9,7,3));
        applyChain(model.rightArm,right,held);
        var elbow=model.leftArm.getChild("elbow");if(!elbow.hasChild("g_hand"))return;
        var hand=elbow.getChild("g_hand");var wrist=hand.getChild("wrist");
        elbow.hidden=true;hand.visible=true;
        float blend=seal(t);
        var left=solve(LEFT_SHOULDER,SEAL_WRIST,4,new Vector3f(8,7,-1));
        applyChain(model.leftArm,left,blend);
        Quaternionf lower=rotation(model.leftArm).mul(rotation(elbow));
        // The wrist independently keeps the two straight fingers upright at the lips.
        rotation(wrist,wristPose(lower,t));
        // Solve the established gesture in its authored space, then transfer both
        // arms across the torso. Geometry is separately built for the right skin UVs.
        var savedRight=model.rightArm.getTransform();var savedElbow=model.rightArm.getChild("elbow").getTransform();
        reflectPart(model.leftArm,model.rightArm);
        reflectPart(elbow,model.rightArm.getChild("elbow"));
        model.leftArm.setTransform(savedRight);model.leftArm.pivotX=-savedRight.pivotX;model.leftArm.yaw=-savedRight.yaw;model.leftArm.roll=-savedRight.roll;
        elbow.setTransform(savedElbow);elbow.pivotX=-savedElbow.pivotX;elbow.yaw=-savedElbow.yaw;elbow.roll=-savedElbow.roll;
        var rightElbow=model.rightArm.getChild("elbow");var rightHand=rightElbow.getChild("g_hand");
        reflectPart(wrist,rightHand.getChild("wrist"));
        hand.visible=false;elbow.hidden=false;rightHand.visible=true;rightElbow.hidden=true;
        float release=Ease.smooth(t,2.70f,2.80f);
        restore(model.leftArm,originalLeft,release);restore(model.rightArm,originalRight,release);
        restore(elbow,originalLeftElbow,release);restore(rightElbow,originalRightElbow,release);
        if(GStage.handOnly(player)){
            model.rightArm.visible=model.rightSleeve.visible=true;
            model.leftArm.visible=model.leftSleeve.visible=false;
        }
        for(ModelPart[] pair:new ModelPart[][]{{model.leftSleeve,model.leftArm},{model.rightSleeve,model.rightArm}}){
            pair[0].copyTransform(pair[1]);pair[0].getChild("elbow").copyTransform(pair[1].getChild("elbow"));
        }
        model.rightSleeve.getChild("elbow").visible=false;
    }
    /**
     * (2.101.0) Lee So-sang's H again: her right hand's two fingers raised before her face while she holds them up (the
     * videos': her eyes alight; SwordFinale.sealWeight): G's hand of sword-fingers in place of the end of her right
     * forearm, its wrist turned from the way the forearm points so the fingers stand upright, the clip's arm as it is.
     */
    static void applyFinale(PlayerEntityModel<AbstractClientPlayerEntity> model,StandClient.Cast cast,float delta){
        float k=SwordFinale.sealWeight(cast.age(delta)/20f);if(k<=.001f)return;
        var elbow=model.rightArm.getChild("elbow");if(!elbow.hasChild("g_hand"))return;
        var hand=elbow.getChild("g_hand");var wrist=hand.getChild("wrist");
        elbow.hidden=true;hand.visible=true;
        // the arm bent at the elbow, its wrist before her chin a little to her right (the videos': the hand at the side of
        // her face, the two fingers up before her nose and her eyes over them)
        applyChain(model.rightArm,solve(FINALE_SHOULDER,FINALE_WRIST,4,FINALE_POLE),k);
        // upright: the model's up is its -y, and the fingers run along the hand's +y
        Quaternionf lower=rotation(model.rightArm).mul(rotation(elbow));
        rotation(wrist,new Quaternionf().slerp(new Quaternionf(lower).conjugate().mul(SEAL_HAND),k));
        model.rightSleeve.copyTransform(model.rightArm);model.rightSleeve.getChild("elbow").copyTransform(elbow);
        model.rightSleeve.getChild("elbow").visible=false;
    }
    /**
     * (2.101.0) H again's seal (model pixels: her left +x, down +y, back +z): her right shoulder, where its wrist is held
     * (before her chin, a little to her right) and which way its elbow bends (out and down); where the two fingers' tips
     * then are (SwordFinale.seal: its light), upright 4.25 pixels over the wrist and a little in toward her middle.
     */
    static final Vector3f FINALE_SHOULDER=new Vector3f(-5,2,0),FINALE_WRIST=new Vector3f(-1.5f,1.3f,-5.6f),FINALE_POLE=new Vector3f(-9,8,-1);
    static final Vector3f FINALE_TIP=new Vector3f(-.9f,1.3f-4.25f,-5.6f);
    static void restore(ModelPart part,ModelTransform initial,float amount){
        if(amount<=0)return;
        rotation(part,rotation(part).slerp(new Quaternionf().rotationZYX(initial.roll,initial.yaw,initial.pitch),amount));
        part.pivotX+=(initial.pivotX-part.pivotX)*amount;part.pivotY+=(initial.pivotY-part.pivotY)*amount;part.pivotZ+=(initial.pivotZ-part.pivotZ)*amount;
    }
    static Vec3d mirror(StandClient.Cast cast,float t,Vec3d v,boolean direction){
        var p=R6.poseOf(FallingSword.CLIP.frame(t));var f=FallingSword.forward(cast);var r=FallingSword.right(f);var left=r.negate();float k=(float)Math.PI/180;
        var q=new Quaternionf().rotationAxis(p.yaw*k,0,1,0).rotateAxis(p.pitch*k,(float)left.x,0,(float)left.z).rotateAxis(p.roll*k,(float)f.x,0,(float)f.z);
        Vector3f n=q.transform(new Vector3f((float)r.x,0,(float)r.z));var normal=new Vec3d(n.x,n.y,n.z).normalize();
        var pivot=direction?Vec3d.ZERO:cast.origin().add(f.multiply(p.fw)).add(r.multiply(p.rt)).add(0,p.up+KagenoClips.PIVOT,0);
        return v.subtract(normal.multiply(2*v.subtract(pivot).dotProduct(normal)));
    }
    static FallingSword.Shot handShot(FallingSword.Shot shot,StandClient.Cast cast,float t){
        if(t>=1.008f)return shot;
        return new FallingSword.Shot(mirror(cast,t,shot.eye(),false),mirror(cast,t,shot.look(),false),shot.fov(),-shot.roll());
    }
    /** Her right wrist where it is drawn (2.87.0: the light along her arm and the shards round her hand go by it). */
    static Vec3d wrist(StandClient.Cast cast,float t){return point(cast,t,false);}
    /** The light tracks the corrected fingertips, independently of the unchanged camera anchors. */
    static Vec3d tip(StandClient.Cast cast,float t){return point(cast,t,true);}
    private static Vec3d point(StandClient.Cast cast,float t,boolean fingertips){
        var mc=MinecraftClient.getInstance();var player=mc.world==null?null:mc.world.getPlayerByUuid(cast.caster());
        float center=player instanceof AbstractClientPlayerEntity p&&p.getModel().equals("slim")?.5f:1f;
        var frame=FallingSword.CLIP.frame(t);var pose=R6.poseOf(frame);
        float blend=seal(t),k=(float)Math.PI/180;float[] joint=frame.joints().joint[3];
        Quaternionf upper=new Quaternionf().rotationZYX(joint[2]*k,joint[1]*k,joint[0]*k);
        Quaternionf lower=new Quaternionf(upper).rotateX(frame.joints().elbow[1]*k);
        var chain=solve(LEFT_SHOULDER,SEAL_WRIST,4,new Vector3f(8,7,-1));
        upper.slerp(chain.upper,blend).normalize();lower.slerp(chain.lower,blend).normalize();
        Quaternionf wrist=wristPose(lower,t);
        Quaternionf fingers=new Quaternionf(lower).mul(wrist);
        Vector3f model=new Vector3f(LEFT_SHOULDER).add(upper.transform(new Vector3f(0,4,0)))
            .add(lower.transform(new Vector3f(center,4,0))).add(fingertips?fingers.transform(new Vector3f(-.675f,4.175f,-.12f)):new Vector3f());
        model.x=-model.x;
        Vec3d f=FallingSword.forward(cast),right=FallingSword.right(f),left=right.negate();
        // PlayerEntityRenderer's vanilla 15/16 scale and LivingEntityRenderer's 1.501 pivot.
        double scale=.9375;
        Vec3d local=right.multiply(-model.x/16*scale).add(0,(1.501-model.y/16)*scale-KagenoClips.PIVOT,0).add(f.multiply(-model.z/16*scale));
        Quaternionf q=new Quaternionf().rotationAxis(pose.yaw*k,0,1,0).rotateAxis(pose.pitch*k,(float)left.x,0,(float)left.z).rotateAxis(pose.roll*k,(float)f.x,0,(float)f.z);
        Vector3f v=q.transform(new Vector3f((float)local.x,(float)local.y,(float)local.z));
        return cast.origin().add(f.multiply(pose.fw)).add(right.multiply(pose.rt)).add(v.x,pose.up+KagenoClips.PIVOT+v.y,v.z);
    }
}
