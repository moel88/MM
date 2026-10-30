package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.AtomicClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import dev.sangwoo.celestialfall.client.MotionArmorModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import java.util.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    protected PlayerEntityRendererMixin(EntityRendererFactory.Context context,PlayerEntityModel<AbstractClientPlayerEntity> model,float shadow) {super(context,model,shadow);}
    @Unique private PlayerEntityModel<AbstractClientPlayerEntity> botand$vanilla,botand$animated;
    @Unique private List<FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>>> botand$normalFeatures,botand$mapFeatures;
    @Unique private boolean botand$usingMap;
    @Unique private boolean botand$posed;
    @Inject(method="<init>",at=@At("TAIL"))
    private void botand$articulatedSkin(EntityRendererFactory.Context context,boolean slim,CallbackInfo ci) {
        botand$vanilla=this.model;botand$normalFeatures=new ArrayList<>(this.features);
        botand$animated=new MotionPlayerModel(slim);botand$mapFeatures=new ArrayList<>(this.features);
        botand$mapFeatures.removeIf(feature->feature instanceof ArmorFeatureRenderer);
        botand$mapFeatures.add(new ArmorFeatureRenderer<>(this,new MotionArmorModel(.5f),new MotionArmorModel(1),context.getModelManager()));
        // a boss's outfit (2.69.0): bosses are only ever on the map
        botand$mapFeatures.add(new dev.sangwoo.celestialfall.client.boss.BossOutfitFeature((net.minecraft.client.render.entity.PlayerEntityRenderer)(Object)this));
        // the stand arrow in the hands and the chest (2.70.0)
        botand$mapFeatures.add(new dev.sangwoo.celestialfall.client.stand.StandArrowFeature((net.minecraft.client.render.entity.PlayerEntityRenderer)(Object)this));
    }
    /**
     * Lee So-sang's player holds their sword (2.76.0: drawn in the main hand whatever it holds, StandClient.sword): that
     * arm holds it as it would an item, not hanging empty.
     */
    @Inject(method="getArmPose",at=@At("RETURN"),cancellable=true)
    private static void botand$isosangSword(AbstractClientPlayerEntity player,net.minecraft.util.Hand hand,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose> ci){
        if(hand==net.minecraft.util.Hand.MAIN_HAND&&ci.getReturnValue()==net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose.EMPTY
            &&dev.sangwoo.celestialfall.client.StandClient.isosang(player)&&!dev.sangwoo.celestialfall.client.meteor.MiningPose.holding(player))
            ci.setReturnValue(net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose.ITEM);
    }
    /** No name over a boss (2.69.0): its bar at the bottom of the screen has it. */
    @Inject(method="renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"),cancellable=true)
    private void botand$noBossName(AbstractClientPlayerEntity player,net.minecraft.text.Text text,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        if(dev.sangwoo.celestialfall.client.boss.BossClient.isBoss(player))ci.cancel();
    }
    @Unique private void botand$selectModel() {
        boolean enabled=dev.sangwoo.celestialfall.client.MapClient.enabled();
        if(enabled==botand$usingMap)return;botand$usingMap=enabled;
        this.model=enabled?botand$animated:botand$vanilla;
        this.features.clear();this.features.addAll(enabled?botand$mapFeatures:botand$normalFeatures);
    }
    @Inject(method={"renderRightArm","renderLeftArm"},at=@At("HEAD"))
    private void botand$selectArm(MatrixStack matrices,VertexConsumerProvider vertices,int light,AbstractClientPlayerEntity player,CallbackInfo ci){botand$selectModel();}
    @Inject(method="render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"),cancellable=true)
    private void celestialfall$atomicActor(AbstractClientPlayerEntity player,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci) {
        botand$selectModel();
        botand$posed=false;
        var atomic=dev.sangwoo.celestialfall.client.AtomicClient.cinematic();
        if(atomic!=null&&!atomic.caster.equals(player.getUuid())&&player.squaredDistanceTo(atomic.origin)<32*32){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.StandClient.cutsceneHidden(player)){ci.cancel();return;}
        if(player==net.minecraft.client.MinecraftClient.getInstance().player&&dev.sangwoo.celestialfall.client.BotandCamera.enabled()
            &&!dev.sangwoo.celestialfall.client.CinematicDirector.isActive()&&dev.sangwoo.celestialfall.client.BotandCamera.STATE.collisionDistance<.7){ci.cancel();return;}
        if(player.isAlive() && (AtomicClient.actor(player.getUuid())!=null || dev.sangwoo.celestialfall.client.HeavenClient.actor(player.getUuid())!=null)){ci.cancel();return;}
        matrices.push();botand$posed=true;
        var stand=dev.sangwoo.celestialfall.client.kageno.KagenoClips.animated(player.getUuid());
        if(stand!=null&&dev.sangwoo.celestialfall.client.kageno.KagenoClips.hidden(stand,delta)){matrices.pop();botand$posed=false;ci.cancel();return;}
        float bodyYaw=net.minecraft.util.math.MathHelper.lerpAngleDegrees(delta,player.prevBodyYaw,player.bodyYaw);
        dev.sangwoo.celestialfall.client.kageno.BodyGhost.begin(player,matrices,delta,this.getPositionOffset(player,delta));
        if(stand!=null)dev.sangwoo.celestialfall.client.kageno.KagenoClips.root(matrices,stand,delta,bodyYaw);
        else dev.sangwoo.celestialfall.client.kageno.KagenoClips.unwind(matrices,player.getUuid(),delta,bodyYaw);
    }
    @Inject(method="render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void botand$restoreLift(AbstractClientPlayerEntity player,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){dev.sangwoo.celestialfall.client.kageno.BodyGhost.end();if(botand$posed){matrices.pop();botand$posed=false;}}
}
