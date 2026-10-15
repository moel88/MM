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
        var stand=StandClient.action(player.getUuid());
        var atomic=AtomicClient.poseCast(player.getUuid());
        if(atomic!=null){
            articulated=true;
            MotionRig.apply(this,dev.sangwoo.celestialfall.combat.AtomicMotion.sample(atomic.age(animationProgress-player.age)),0,0);
        }
        else if(stand!=null){
            articulated=true;var pose=KagenoMotion.sample(stand.skill(),stand.age(animationProgress-player.age),stand.combo());
            if(stand.skill()==dev.sangwoo.celestialfall.stand.KagenoRules.R){var walk=CombatClient.pose(player,animationProgress-player.age);for(int i=4;i<=5;i++)pose.joint[i]=walk.joint[i].clone();pose.knee[0]=walk.knee[0];pose.knee[1]=walk.knee[1];}
            MotionRig.apply(this,pose,0,0);
        }
        else if(StandClient.visible(player)&&CombatClient.snapshot(player.getUuid()).guard()){
            articulated=true;var pose=KagenoMotion.idle();pose.rot(2,-75,-35,65).rot(3,-75,15,-45);pose.elbow[0]=-45;pose.elbow[1]=-50;MotionRig.apply(this,pose,headYaw,headPitch);
        }
        else if(StandClient.visible(player)&&!player.isUsingItem()){
            articulated=true;var rest=KagenoMotion.idle();rightArm.pitch=MotionRig.rad(rest.joint[2][0]);rightArm.yaw=0;rightArm.roll=MotionRig.rad(rest.joint[2][2]);rightArm.getChild("elbow").pitch=MotionRig.rad(rest.elbow[0]);
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
