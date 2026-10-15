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
    @Inject(method="renderItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void botand$thrustGrip(LivingEntity entity,ItemStack stack,net.minecraft.client.render.model.json.ModelTransformationMode mode,net.minecraft.util.Arm arm,net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider vertices,int light,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(!(entity instanceof PlayerEntity player)||!StandClient.visible(player)||arm!=player.getMainArm())return;
        var cast=StandClient.action(player.getUuid());
        var atomic=dev.sangwoo.celestialfall.client.AtomicClient.poseCast(player.getUuid());
        if(atomic!=null){
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(dev.sangwoo.celestialfall.combat.AtomicMotion.wrist(atomic.age(net.minecraft.client.MinecraftClient.getInstance().getTickDelta()))));return;
        }
        if(cast!=null){
            float a=cast.age(net.minecraft.client.MinecraftClient.getInstance().getTickDelta());
            float grip=switch(cast.skill()){
                case dev.sangwoo.celestialfall.stand.KagenoRules.E->dev.sangwoo.celestialfall.client.KagenoMotion.wrist(a);
                case dev.sangwoo.celestialfall.stand.KagenoRules.H->dev.sangwoo.celestialfall.client.KagenoMotion.hWrist(a);
                case dev.sangwoo.celestialfall.stand.KagenoRules.T->-90*dev.sangwoo.celestialfall.client.KagenoMotion.smooth(a,0,3)*(1-dev.sangwoo.celestialfall.client.KagenoMotion.smooth(a,20,28));
                case dev.sangwoo.celestialfall.stand.KagenoRules.RUSH->-90*dev.sangwoo.celestialfall.client.KagenoMotion.smooth(a,0,3)*(1-dev.sangwoo.celestialfall.client.KagenoMotion.smooth(a,58,70));
                default->0;
            };
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(grip));
        }
    }
    @Redirect(method="render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack botand$standSword(LivingEntity entity){
        return entity instanceof PlayerEntity player&&StandClient.visible(player)?StandClient.sword():entity.getMainHandStack();
    }
}
