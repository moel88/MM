package dev.sangwoo.botandverify.mixin;
import dev.sangwoo.botandverify.RemakeCapture;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Capture the actual pre-animation view matrix, before the production root transform. */
@Mixin(value=PlayerEntityRenderer.class,priority=500)
public abstract class HighestDirectionProofMixin {
 @Inject(method="render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"))
 private void before(AbstractClientPlayerEntity player,float yaw,float delta,MatrixStack m,VertexConsumerProvider v,int light,CallbackInfo ci){RemakeCapture.beforeActor(player,m);}
}
