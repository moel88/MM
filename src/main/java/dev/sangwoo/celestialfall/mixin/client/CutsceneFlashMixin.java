package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CutsceneFlash;
import dev.sangwoo.celestialfall.client.stand.StandAuraLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A skill with a cutscene makes its user's body glow sky blue (CutsceneFlash). The stands' aura layer draws the glow
 * round the body (StandAuraLayer); where it cannot (a shader pack, or post-processing off), right after the body is
 * drawn its glowing shell is drawn round it (CutsceneFlash.hull: only round its outline, the body itself left as it is).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CutsceneFlashMixin<T extends LivingEntity,M extends EntityModel<T>> {
    @Shadow protected M model;
    @Inject(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V",shift=At.Shift.AFTER))
    private void botand$cutsceneFlash(T entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        float a=CutsceneFlash.strength(entity,delta);if(a<=.001f||!(model instanceof BipedEntityModel<?> body)||StandAuraLayer.available())return;
        CutsceneFlash.hull(matrices,vertices,body,a);
    }
}
