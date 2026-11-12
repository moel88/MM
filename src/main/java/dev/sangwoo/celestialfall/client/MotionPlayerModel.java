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
            // (2.116.0) X Chara's hack's panel is carried through their knife's hits as it is held standing: that arm out
            if(StandClient.xChara(player)){
                var hack=dev.sangwoo.celestialfall.client.xchara.HackMode.look(player.getUuid(),animationProgress-player.age);
                // (2.121.0: not through their cleave, which has that arm press the panel and then hold the knife, GiantCleave.fix)
                if(hack!=null&&!dev.sangwoo.celestialfall.xchara.XCharaCombo.skill(stand.combo()))dev.sangwoo.celestialfall.client.xchara.XCharaIdle.carry(pose,player.getMainArm()==Arm.RIGHT,hack.arm());
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
        else if(StandClient.xChara(player)&&!player.isUsingItem()&&rig&&"base".equals(CombatClient.state(player,animationProgress-player.age))
            &&!CombatClient.snapshot(player.getUuid()).guard()){
            // X Chara at rest (2.116.0, user request: "다리와 팔과 손을 쭉 펴서 정자세로 있고 다리를 살짝 벌리고 검들고 있는 팔도
            // 살짝 벌리고 있고 패널 부분 팔은 몸에 붙이고 있다가 패널 소환하면 팔 벌리게"): at attention, the arms straight, the
            // knife's a little out from the body, the other against it and out as the hack's panel is called (XCharaIdle;
            // the legs straight and a little apart come with the rig's pose, CombatClient.pose). (2.111.0 to 2.115.0 they
            // held the knife a little forward at the side, the elbow bent, and the panel on a raised forearm.)
            arms="xchara";float d=animationProgress-player.age;
            var pose=CombatClient.pose(player,d);var hack=dev.sangwoo.celestialfall.client.xchara.HackMode.look(player.getUuid(),d);
            // (2.119.0) a press of the panel (their E, HealClient): its arm up in front of them, its hand tapping it, and they look down at it
            float[] press=hack==null?null:dev.sangwoo.celestialfall.client.xchara.HealClient.press(player.getUuid(),d);
            dev.sangwoo.celestialfall.client.xchara.XCharaIdle.arms(pose,player.getMainArm()==Arm.RIGHT,
                dev.sangwoo.celestialfall.client.xchara.XCharaIdle.weight(player.getUuid(),d),hack==null?0:hack.arm(),animationProgress,press==null?0:press[0],press==null?0:press[1]);
            for(int i=0;i<2;i++){
                var arm=i==0?rightArm:leftArm;float[] a=pose.joint[dev.sangwoo.celestialfall.combat.MotionPose.RIGHT_ARM+i];
                arm.pitch=MotionRig.rad(a[0]);arm.yaw=MotionRig.rad(a[1]);arm.roll=MotionRig.rad(a[2]);arm.getChild("elbow").pitch=MotionRig.rad(pose.elbow[i]);
            }
            if(press!=null&&press[0]>0){
                float side=player.getMainArm()==Arm.RIGHT?1:-1;
                // (the short way round: the head's yaw comes in any turn of the circle, and eased straight it span once round on the way)
                head.pitch+=press[0]*net.minecraft.util.math.MathHelper.wrapDegrees(dev.sangwoo.celestialfall.client.xchara.XCharaIdle.LOOK_DOWN-(float)Math.toDegrees(head.pitch))*(float)Math.PI/180;
                head.yaw+=press[0]*net.minecraft.util.math.MathHelper.wrapDegrees(side*dev.sangwoo.celestialfall.client.xchara.XCharaIdle.LOOK_ASIDE-(float)Math.toDegrees(head.yaw))*(float)Math.PI/180;
                hat.copyTransform(head);
            }
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
