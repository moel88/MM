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
        articulated=CombatClient.animate(player);
        if(articulated)MotionRig.apply(this,CombatClient.pose(player,animationProgress-player.age),headYaw,headPitch);
        if(player.isUsingItem()||player.handSwingProgress>0) {
            body.setTransform(vanillaBody);head.setTransform(vanillaHead);
            rightArm.setTransform(vanillaRight);leftArm.setTransform(vanillaLeft);
            rightArm.getChild("elbow").pitch=leftArm.getChild("elbow").pitch=0;
        }
        var stand=dev.sangwoo.celestialfall.client.kageno.KagenoClips.animated(player.getUuid());
        var atomic=AtomicClient.poseCast(player.getUuid());
        if(atomic!=null){
            articulated=true;
            MotionRig.apply(this,dev.sangwoo.celestialfall.client.kageno.AtomicPose.frame(atomic.age(animationProgress-player.age)/20f).joints(),0,0);
        }
        else if(stand!=null){
            articulated=true;var frame=dev.sangwoo.celestialfall.client.kageno.KagenoClips.action(stand,animationProgress-player.age);
            var pose=frame.joints();
            if(stand.skill()==dev.sangwoo.celestialfall.stand.KagenoRules.R&&dev.sangwoo.celestialfall.client.kageno.KagenoClips.walkLegs(stand,animationProgress-player.age)){var walk=CombatClient.pose(player,animationProgress-player.age);for(int i=4;i<=5;i++)pose.joint[i]=walk.joint[i].clone();}
            MotionRig.apply(this,pose,0,0);
        }
        else if(StandClient.visible(player)&&dev.sangwoo.celestialfall.client.kageno.KagenoClips.guardFrame(player.getUuid(),animationProgress-player.age)!=null){
            articulated=true;var guard=dev.sangwoo.celestialfall.client.kageno.KagenoClips.guardFrame(player.getUuid(),animationProgress-player.age).joints();
            for(int i=2;i<=3;i++){var arm=i==2?rightArm:leftArm;arm.pitch=MotionRig.rad(guard.joint[i][0]);arm.yaw=MotionRig.rad(guard.joint[i][1]);arm.roll=MotionRig.rad(guard.joint[i][2]);arm.getChild("elbow").pitch=0;}
        }
        else if(StandClient.visible(player)&&!player.isUsingItem()){
            articulated=true;var rest=dev.sangwoo.celestialfall.client.kageno.KagenoClips.idle().joints();
            rightArm.pitch=MotionRig.rad(rest.joint[2][0])+rightArm.pitch*.25f;rightArm.yaw=MotionRig.rad(rest.joint[2][1]);rightArm.roll=MotionRig.rad(rest.joint[2][2]);rightArm.getChild("elbow").pitch=0;
        }
        overlays();
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
