package dev.sangwoo.celestialfall.client;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;

public final class MotionPlayerModel extends PlayerEntityModel<AbstractClientPlayerEntity> {
    private boolean articulated;
    public MotionPlayerModel(boolean slim) {super(MotionRig.player(slim),slim);}
    @Override public void setAngles(AbstractClientPlayerEntity player,float limbAngle,float limbDistance,float animationProgress,float headYaw,float headPitch) {
        MotionRig.reset(this);super.setAngles(player,limbAngle,limbDistance,animationProgress,headYaw,headPitch);
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
        // Star Platinum's beatdown's cutscene (2.64.0): its user's body ("yare yare", a hand on the head) and the one struck's
        var beat=dev.sangwoo.celestialfall.client.stand.BeatdownPose.of(player,animationProgress-player.age);
        // The meteor pickaxe in both hands (2.49.1): after a stand's own motion, before its idle arms.
        boolean mining=atomic==null&&stand==null&&dev.sangwoo.celestialfall.client.meteor.MiningPose.active(player,animationProgress-player.age);
        String arms="";
        if(beat!=null){
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
        // Crossfade whenever the pose comes from somewhere else than a moment ago (the first-person hand pass has no clock).
        float delta=animationProgress-player.age;
        if(delta>=0&&delta<=1.001f){
            Object source;float fade;
            if(beat!=null){source="beatdown";fade=3;}
            else if(atomic!=null){source="atomic";fade=3;}
            else if(stand!=null){source=stand.id();fade=1.6f;}
            else if(mining){source=dev.sangwoo.celestialfall.client.meteor.MiningPose.source(player);fade=3;}
            else if(rig){source=CombatClient.state(player,delta);fade=CombatClient.fade(source);}
            else{source="vanilla";fade=3;}
            // A stand user's arms come from the guard clip while it plays and from the rest pose after it: the change
            // between them fades too (the free arm popped to the combat stance as the guard's release ended). Into the
            // guard it fades fast, so the 0.1 s flourish keeps its snap.
            if(arms.equals("guard"))fade=Math.min(fade,1.2f);
            // a blow is part of the two-handed hold, not a change of pose (the swing would fade every chop)
            PoseBlend.apply(player.getUuid(),this,java.util.List.of(source,!mining&&using,arms),fade,animationProgress);
        }
        dev.sangwoo.celestialfall.client.integration.PlayerAnimatorBridge.apply(player,this,animationProgress-player.age);
        overlays();
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
