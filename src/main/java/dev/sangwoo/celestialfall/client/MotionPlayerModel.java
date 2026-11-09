package dev.sangwoo.celestialfall.client;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;

public final class MotionPlayerModel extends PlayerEntityModel<AbstractClientPlayerEntity> {
    private boolean articulated;
    /** How far each arm's middle is off its pivot (px): 1, or 0.5 for slim arms (2.105.0, Lee So-sang's stance turns her arms about their middles). */
    private final float half;
    public MotionPlayerModel(boolean slim) {super(MotionRig.player(slim),slim);half=slim?.5f:1;}
    @Override public void setAngles(AbstractClientPlayerEntity player,float limbAngle,float limbDistance,float animationProgress,float headYaw,float headPitch) {
        MotionRig.reset(this);super.setAngles(player,limbAngle,limbDistance,animationProgress,headYaw,headPitch);
        // Stopped in Star Platinum's time stop (2.67.0): the body holds the pose it was drawn in as time stopped. Its
        // own clocks go on here (the world's time a flinch or a daze is timed by, a guard or a blow cut off as it was
        // stopped), its body does not; as time moves again the pose it takes fades in from this one.
        if(dev.sangwoo.celestialfall.client.stand.StandTimeStop.stopped(player)&&PoseBlend.hold(player.getUuid(),this,animationProgress)){
            articulated=true;overlays();return;
        }
        var vanillaBody=body.getTransform();var vanillaHead=head.getTransform();
        var vanillaRight=rightArm.getTransform();var vanillaLeft=leftArm.getTransform();
        articulated=CombatClient.animate(player);boolean rig=articulated,using=player.isUsingItem()||player.handSwingProgress>0;
        if(articulated)MotionRig.apply(this,CombatClient.pose(player,animationProgress-player.age),headYaw,headPitch);
        if(using) {
            body.setTransform(vanillaBody);head.setTransform(vanillaHead);
            rightArm.setTransform(vanillaRight);leftArm.setTransform(vanillaLeft);
            rightArm.getChild("elbow").pitch=leftArm.getChild("elbow").pitch=0;
        }
        var stand=dev.sangwoo.celestialfall.client.kageno.KagenoClips.animated(player.getUuid());
        var atomic=AtomicClient.poseCast(player.getUuid());
        // The stand arrow driven into the chest (2.70.0): before everything else (the server cuts it short if anything else takes the body)
        var stab=dev.sangwoo.celestialfall.client.stand.StandArrowClient.frame(player,animationProgress-player.age);
        // Star Platinum's beatdown's cutscene (2.64.0): its user's body ("yare yare", a hand on the head) and the one struck's
        var beat=stab!=null?null:dev.sangwoo.celestialfall.client.stand.BeatdownPose.of(player,animationProgress-player.age);
        // The meteor pickaxe in both hands (2.49.1): after a stand's own motion, before its idle arms.
        boolean mining=stab==null&&atomic==null&&stand==null&&dev.sangwoo.celestialfall.client.meteor.MiningPose.active(player,animationProgress-player.age);
        String arms="";
        if(stab!=null){
            articulated=true;
            dev.sangwoo.celestialfall.client.stand.StandArrowClient.apply(this,stab);
        }
        else if(beat!=null){
            articulated=true;
            MotionRig.apply(this,beat,0,0);
        }
        else if(atomic!=null){
            articulated=true;
            dev.sangwoo.celestialfall.client.kageno.AtomicPose.apply(this,atomic.age(animationProgress-player.age)/20f);
        }
        else if(stand!=null){
            articulated=true;var frame=dev.sangwoo.celestialfall.client.kageno.KagenoClips.action(stand,animationProgress-player.age);
            var pose=frame.joints();
            // R's walking cast: the legs take the walk cycle while the upper body plays the cuts.
            float walking=dev.sangwoo.celestialfall.client.kageno.KagenoClips.walkWeight(stand,animationProgress-player.age);
            if(walking>0){
                float d=animationProgress-player.age,body=net.minecraft.util.math.MathHelper.lerpAngleDegrees(d,player.prevBodyYaw,player.bodyYaw);
                dev.sangwoo.celestialfall.client.kageno.KagenoClips.walkLegs(pose,CombatClient.pose(player,d),walking,frame.yaw()+dev.sangwoo.celestialfall.client.kageno.KagenoClips.facingTurn(stand,d,body));
            }
            MotionRig.apply(this,pose,0,0);
        }
        else if(mining){
            articulated=true;arms="mining";
            dev.sangwoo.celestialfall.client.meteor.MiningPose.apply(this,player,animationProgress-player.age,headYaw,headPitch);
        }
        else if(StandClient.visible(player)&&dev.sangwoo.celestialfall.client.kageno.KagenoClips.guardFrame(player.getUuid(),animationProgress-player.age)!=null){
            articulated=true;arms="guard";var guard=dev.sangwoo.celestialfall.client.kageno.KagenoClips.guardFrame(player.getUuid(),animationProgress-player.age).joints();
            for(int i=2;i<=3;i++){var arm=i==2?rightArm:leftArm;arm.pitch=MotionRig.rad(guard.joint[i][0]);arm.yaw=MotionRig.rad(guard.joint[i][1]);arm.roll=MotionRig.rad(guard.joint[i][2]);arm.getChild("elbow").pitch=0;}
        }
        else if(StandClient.visible(player)&&!player.isUsingItem()){
            articulated=true;arms="idle";var rest=dev.sangwoo.celestialfall.client.kageno.KagenoClips.idle().joints();
            rightArm.pitch=MotionRig.rad(rest.joint[2][0])+rightArm.pitch*.25f;rightArm.yaw=MotionRig.rad(rest.joint[2][1]);rightArm.roll=MotionRig.rad(rest.joint[2][2]);rightArm.getChild("elbow").pitch=0;
        }
        else if(StandClient.isosang(player)&&!player.isUsingItem()&&rig&&"base".equals(CombatClient.state(player,animationProgress-player.age))
            &&!CombatClient.snapshot(player.getUuid()).guard()){
            // Lee So-sang at rest (2.105.0, user request: "왼쪽 팔을 굽어서 손을 엉덩이쪽에 가져다대서 뒷짐 지는 자세를 취하소
            // 오른쪽손은 오른쪽으로 내밀어 이게 기본적으로 서 있는 자세고 검은 평타나 스킬을 쓸때만 나오게"): her left hand
            // behind her, her right held out to the right, no sword (IsosangIdle, IsosangSheath); walking, her arms swing
            // as anyone's, the stance easing out as she goes and back in as she stops. (2.76.0 to 2.104.0 she stood
            // holding her sword a little forward at her side.)
            arms="isosang";float d=animationProgress-player.age;
            var pose=CombatClient.pose(player,d);float w=dev.sangwoo.celestialfall.client.isosang.IsosangIdle.weight(player.getUuid(),d);
            dev.sangwoo.celestialfall.client.isosang.IsosangIdle.apply(pose,animationProgress,w,half);
            MotionRig.apply(this,pose,headYaw,headPitch);
            // each arm turning about its own middle (its pivot is a pixel off it): moved with the torso's turn
            var torso=new org.joml.Quaternionf().rotationZYX(body.roll,body.yaw,body.pitch);var shift=dev.sangwoo.celestialfall.client.isosang.IsosangIdle.pivots(pose,w,half);
            for(int i=0;i<2;i++){var arm=i==0?rightArm:leftArm;var v=torso.transform(shift[i]);arm.pivotX+=v.x;arm.pivotY+=v.y;arm.pivotZ+=v.z;}
        }
        else if(StandClient.xChara(player)&&dev.sangwoo.celestialfall.client.xchara.XCharaClient.raised(player,animationProgress-player.age)>0){
            // X Chara's G (2.112.0): the knife raised upright as its strange space comes (the arm held out ahead: the knife,
            // held as the game holds it, points up), down again for its second shot (XCharaSpace.raise)
            arms="xchara_g";float w=dev.sangwoo.celestialfall.client.xchara.XCharaClient.raised(player,animationProgress-player.age);
            rightArm.pitch=-.33f+(-1.62f+.33f)*w;rightArm.yaw=-.06f+.12f*w;rightArm.roll=.04f;rightArm.getChild("elbow").pitch=-.12f*(1-w);
        }
        else if(StandClient.xChara(player)&&!player.isUsingItem()&&rig&&"base".equals(CombatClient.state(player,animationProgress-player.age))
            &&!CombatClient.snapshot(player.getUuid()).guard()){
            // X Chara's knife (2.111.0): held a little forward at the side, the arm keeping a quarter of its walk's swing,
            // as Lee So-sang held her sword from 2.76.0 to 2.104.0 (their skills to come will bring their own motion;
            // punching and guarding keep the rig's arms)
            arms="xchara";
            rightArm.pitch=-.33f+rightArm.pitch*.25f;rightArm.yaw=-.06f;rightArm.roll=.04f;rightArm.getChild("elbow").pitch=-.12f;
        }
        // Crossfade whenever the pose comes from somewhere else than a moment ago (the first-person hand pass has no clock).
        float delta=animationProgress-player.age;
        if(delta>=0&&delta<=1.001f){
            Object source;float fade;
            if(stab!=null){source="stand-arrow";fade=3;}
            else if(beat!=null){source="beatdown";fade=3;}
            else if(atomic!=null){source="atomic";fade=3;}
            else if(stand!=null){source=stand.id();fade=1.6f;}
            else if(mining){source=dev.sangwoo.celestialfall.client.meteor.MiningPose.source(player);fade=3;}
            else if(rig){source=CombatClient.state(player,delta);fade=CombatClient.fade(source);}
            else{source="vanilla";fade=3;}
            // A stand user's arms come from the guard clip while it plays and from the rest pose after it: the change
            // between them fades too (the free arm popped to the combat stance as the guard's release ended). Into the
            // guard it fades fast, so the 0.1 s flourish keeps its snap.
            if(arms.equals("guard"))fade=Math.min(fade,1.2f);
            // (2.105.0) Lee So-sang settles into her stance at rest more slowly than into a blow: her hand goes behind her
            if(arms.equals("isosang"))fade=Math.max(fade,dev.sangwoo.celestialfall.client.isosang.IsosangIdle.SETTLE);
            // a blow is part of the two-handed hold, not a change of pose (the swing would fade every chop)
            PoseBlend.apply(player.getUuid(),this,java.util.List.of(source,!mining&&using,arms),fade,animationProgress);
        }
        dev.sangwoo.celestialfall.client.integration.PlayerAnimatorBridge.apply(player,this,animationProgress-player.age);
        overlays();
        // Lee So-sang's G (the user's 2.87.0 preview): the sword-fingers hand over the clip's arms
        dev.sangwoo.celestialfall.client.isosang.GGesture.apply(this,player,animationProgress-player.age);
    }
    @Override public void render(MatrixStack matrices,net.minecraft.client.render.VertexConsumer vertices,int light,int overlay,float red,float green,float blue,float alpha) {
        super.render(matrices,vertices,light,overlay,red,green,blue,alpha);
        dev.sangwoo.celestialfall.client.kageno.BodyGhost.body(this,matrices);
    }
    public void overlays() {
        hat.copyTransform(head);jacket.copyTransform(body);
        for(ModelPart[] pair:new ModelPart[][]{{rightSleeve,rightArm},{leftSleeve,leftArm},{rightPants,rightLeg},{leftPants,leftLeg}}) {
            pair[0].copyTransform(pair[1]);String child=pair[1].hasChild("elbow")?"elbow":"knee";
            pair[0].getChild(child).copyTransform(pair[1].getChild(child));
        }
    }
    @Override public void copyBipedStateTo(BipedEntityModel<AbstractClientPlayerEntity> other) {
        super.copyBipedStateTo(other);
        if(other instanceof MotionArmorModel) {
            other.rightArm.getChild("elbow").copyTransform(rightArm.getChild("elbow"));
            other.leftArm.getChild("elbow").copyTransform(leftArm.getChild("elbow"));
            other.rightLeg.getChild("knee").copyTransform(rightLeg.getChild("knee"));
            other.leftLeg.getChild("knee").copyTransform(leftLeg.getChild("knee"));
        }
    }
    @Override public void setArmAngle(Arm side,MatrixStack matrices) {
        super.setArmAngle(side,matrices);
        if(articulated) {
            ModelPart arm=side==Arm.RIGHT?rightArm:leftArm;
            matrices.translate(0,4/16.0,0);matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotation(arm.getChild("elbow").pitch));matrices.translate(0,-4/16.0,0);
        }
    }
}
