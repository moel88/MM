package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CutsceneFlash;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A skill with a cutscene flashes its user's body sky blue (2.64.0, CutsceneFlash): right after the body is drawn it is
 * drawn again in a flat sky blue over it (a plain white texture, the colour, no light on it), its skin's outer layer
 * (the hat, the jacket, the sleeves and the trousers' layer) left out so the body keeps its shape.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CutsceneFlashMixin<T extends LivingEntity,M extends EntityModel<T>> {
    @Shadow protected M model;
    @Inject(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V",shift=At.Shift.AFTER))
    private void botand$cutsceneFlash(T entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        float a=CutsceneFlash.strength(entity,delta);if(a<=.001f||!(model instanceof PlayerEntityModel<?> body))return;
        boolean hat=body.hat.visible,jacket=body.jacket.visible,ls=body.leftSleeve.visible,rs=body.rightSleeve.visible,lp=body.leftPants.visible,rp=body.rightPants.visible;
        body.hat.visible=body.jacket.visible=body.leftSleeve.visible=body.rightSleeve.visible=body.leftPants.visible=body.rightPants.visible=false;
        model.render(matrices,vertices.getBuffer(RenderLayer.getEntityTranslucentEmissive(CutsceneFlash.WHITE)),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,
            CutsceneFlash.RED,CutsceneFlash.GREEN,CutsceneFlash.BLUE,CutsceneFlash.COVER*a);
        body.hat.visible=hat;body.jacket.visible=jacket;body.leftSleeve.visible=ls;body.rightSleeve.visible=rs;body.leftPants.visible=lp;body.rightPants.visible=rp;
    }
}
