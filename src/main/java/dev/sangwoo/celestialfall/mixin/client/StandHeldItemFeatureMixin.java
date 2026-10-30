package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Only substitutes the rendered stack, never the inventory or equipment packet. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class StandHeldItemFeatureMixin {
    /** One driving the stand arrow into themselves (2.70.0) holds nothing else: the arrow is drawn on its own (StandArrowFeature). */
    @Inject(method="renderItem",at=@At("HEAD"),cancellable=true)
    private void botand$stabbing(LivingEntity entity,ItemStack stack,net.minecraft.client.render.model.json.ModelTransformationMode mode,net.minecraft.util.Arm arm,net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider vertices,int light,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(entity instanceof PlayerEntity player&&dev.sangwoo.celestialfall.client.stand.StandArrowClient.frame(player,net.minecraft.client.MinecraftClient.getInstance().getTickDelta())!=null)ci.cancel();
    }
    @Inject(method="renderItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void botand$thrustGrip(LivingEntity entity,ItemStack stack,net.minecraft.client.render.model.json.ModelTransformationMode mode,net.minecraft.util.Arm arm,net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider vertices,int light,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity holder&&arm==holder.getMainArm()&&dev.sangwoo.celestialfall.client.meteor.MiningPose.holding(holder)){
            // the meteor pickaxe (any player): onto the handle both hands hold, or as the game holds it in one
            float delta=net.minecraft.client.MinecraftClient.getInstance().getTickDelta();
            if(dev.sangwoo.celestialfall.client.meteor.MiningPose.active(holder,delta))dev.sangwoo.celestialfall.client.meteor.MiningPose.applyGrip(matrices,holder,delta);
            return;
        }
        if(entity instanceof PlayerEntity holder&&arm==holder.getMainArm()&&!StandClient.visible(holder)&&StandClient.isosang(holder)){
            // Lee So-sang's sword (2.77.0): turned in the hand as a swing drives it (as the game holds it between swings);
            // its display already turns it IsosangSlash.DISPLAY_TILT further round the wrist
            var swing=dev.sangwoo.celestialfall.client.isosang.IsosangClient.animated(holder.getUuid());
            if(swing!=null){
                var frame=dev.sangwoo.celestialfall.client.kageno.KagenoClips.action(swing,net.minecraft.client.MinecraftClient.getInstance().getTickDelta());
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(frame.grip()-dev.sangwoo.celestialfall.client.isosang.IsosangSlash.DISPLAY_TILT));
            }
            return;
        }
        if(!(entity instanceof PlayerEntity player)||!StandClient.visible(player)||arm!=player.getMainArm())return;
        var cast=dev.sangwoo.celestialfall.client.kageno.KagenoClips.animated(player.getUuid());
        var atomic=dev.sangwoo.celestialfall.client.AtomicClient.poseCast(player.getUuid());
        if(atomic!=null){
            dev.sangwoo.celestialfall.client.kageno.AtomicPose.applyGrip(matrices,atomic.age(net.minecraft.client.MinecraftClient.getInstance().getTickDelta())/20f,arm);return;
        }
        float delta=net.minecraft.client.MinecraftClient.getInstance().getTickDelta();
        var frame=cast!=null?dev.sangwoo.celestialfall.client.kageno.KagenoClips.action(cast,delta)
            :dev.sangwoo.celestialfall.client.kageno.KagenoClips.guardFrame(player.getUuid(),delta)!=null?dev.sangwoo.celestialfall.client.kageno.KagenoClips.guardFrame(player.getUuid(),delta)
            :dev.sangwoo.celestialfall.client.kageno.KagenoClips.idle();
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(frame.grip()));
    }
    @Redirect(method="render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack botand$standSword(LivingEntity entity){
        // a stand user holding the meteor pickaxe holds the pickaxe, not the sword (Kageno's, or Lee So-sang's: 2.76.0)
        var sword=entity instanceof PlayerEntity player?StandClient.sword(player):null;
        return sword!=null&&!(entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity holder&&dev.sangwoo.celestialfall.client.meteor.MiningPose.holding(holder))?sword:entity.getMainHandStack();
    }
}
