package dev.sangwoo.botandverify.mixin;
import dev.sangwoo.botandverify.RemakeCapture;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Measure the rendered facing after both production root motion and vanilla body yaw. */
@Mixin(LivingEntityRenderer.class)
public abstract class HighestRenderedDirectionMixin {
 @Inject(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",shift=At.Shift.AFTER))
 private void after(LivingEntity entity,float yaw,float delta,MatrixStack m,VertexConsumerProvider v,int light,CallbackInfo ci){RemakeCapture.afterActor(entity,delta,m);}
}
