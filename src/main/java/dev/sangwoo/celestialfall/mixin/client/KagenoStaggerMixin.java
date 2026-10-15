package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** H targets of any kind: out of every cutscene shot, then the stagger after the cut (leaning back about the feet). */
@Mixin(LivingEntityRenderer.class)
public abstract class KagenoStaggerMixin {
    @Inject(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"),cancellable=true)
    private void botand$cutsceneHidden(LivingEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        if(StandClient.cutsceneHidden(entity))ci.cancel();
    }
    @Inject(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",shift=At.Shift.AFTER))
    private void botand$stagger(LivingEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        // After setupTransforms local +Z points behind the body, so a positive X turn tips the head backwards.
        float lean=StandClient.stagger(entity,delta);if(lean>0)matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(lean));
    }
}
