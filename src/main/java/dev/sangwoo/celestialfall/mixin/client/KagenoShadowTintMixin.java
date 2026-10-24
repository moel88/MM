package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Q shadow mark: the marked target renders as an opaque near-black silhouette (#010304). */
@Mixin(LivingEntityRenderer.class)
public abstract class KagenoShadowTintMixin {
    @ModifyArgs(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private void botand$shadowMark(Args args,LivingEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light){
        if(!StandClient.shadowMarked(entity))return;
        args.set(4,.02f);args.set(5,.03f);args.set(6,.04f);
    }
}
