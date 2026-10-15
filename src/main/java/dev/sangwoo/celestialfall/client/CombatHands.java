package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.combat.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.util.hit.HitResult;

/** The same contact-frame choreography projected into a readable two-hand first-person stance. */
public final class CombatHands {
    private static MotionPlayerModel normal,slim;
    private CombatHands() {}
    public static boolean enabled(AbstractClientPlayerEntity p) {
        var mc=MinecraftClient.getInstance();
        return !StandClient.visible(p)&&CombatClient.connected()&&CombatManager.bare(p)&&!p.isInvisible()&&!p.isSpectator()&&!p.isSleeping()&&!p.isSwimming()&&!p.isFallFlying()&&!CinematicDirector.isActive()
            &&!(mc.options.attackKey.isPressed()&&mc.crosshairTarget!=null&&mc.crosshairTarget.getType()==HitResult.Type.BLOCK);
    }
    public static void render(AbstractClientPlayerEntity player,float delta,MatrixStack m,VertexConsumerProvider vertices,int light) {
        boolean thin="slim".equals(player.getModel());
        if(thin&&slim==null)slim=new MotionPlayerModel(true);if(!thin&&normal==null)normal=new MotionPlayerModel(false);
        MotionPlayerModel model=thin?slim:normal;
        MotionPose pose=CombatClient.pose(player,delta);var s=CombatClient.snapshot(player.getUuid());double now=player.getWorld().getTime()+delta;
        pose=firstPersonPose(pose,s,now,player.age+delta);
        MotionRig.reset(model);MotionRig.apply(model,pose,0,0);model.overlays();
        m.push();transform(m);
        var skin=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(player.getSkinTexture()));
        model.rightArm.render(m,skin,light,OverlayTexture.DEFAULT_UV);model.leftArm.render(m,skin,light,OverlayTexture.DEFAULT_UV);
        if(player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE))model.rightSleeve.render(m,skin,light,OverlayTexture.DEFAULT_UV);
        if(player.isPartVisible(PlayerModelPart.LEFT_SLEEVE))model.leftSleeve.render(m,skin,light,OverlayTexture.DEFAULT_UV);
        m.pop();
    }
    static void transform(MatrixStack m) {m.translate(0,-.135,.06);m.scale(1,-1,1);}
    static MotionPose firstPersonPose(MotionPose pose,CombatClient.Snapshot s,double now,float animationAge) {
        pose=pose.copy();
        // First-person shoulders sit behind the eye. Keep the head/torso twist out of this projection.
        pose.joint[MotionPose.BODY]=new float[]{0,0,0};pose.bob=pose.shift=pose.lean=0;
        if(s.attack()==0&&!s.guard()&&!s.broken(now)&&s.dashAge(now)>=CombatState.DASH_TICKS) {
            float breathe=(float)Math.sin(animationAge*.09f);
            pose.rot(MotionPose.RIGHT_ARM,-50+breathe,0,7).rot(MotionPose.LEFT_ARM,-54-breathe,0,-7);
            pose.elbow[0]=-98;pose.elbow[1]=-100;
        }
        // Keep the covering hand high through every punch, with a clear silhouette around the crosshair.
        if(s.attack()>0&&s.attack()<=4) {
            int cover=s.attack()==1||s.attack()==3?0:1;
            pose.joint[MotionPose.RIGHT_ARM+1-cover][2]*=.3f;
            pose.rot(MotionPose.RIGHT_ARM+cover,-53,0,cover==0?7:-7);pose.elbow[cover]=-99;
        }
        if(s.guard()) {
            pose.rot(MotionPose.RIGHT_ARM,-58,-12,8).rot(MotionPose.LEFT_ARM,-60,12,-8);
            pose.elbow[0]=pose.elbow[1]=-103;
        }
        return pose;
    }
}
