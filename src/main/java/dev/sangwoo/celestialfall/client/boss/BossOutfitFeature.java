package dev.sangwoo.celestialfall.client.boss;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Draws a boss's outfit over its body (2.69.0, BossOutfit), as the player's model has its limbs this frame, flashing red with it when struck. */
public final class BossOutfitFeature extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    public BossOutfitFeature(FeatureRendererContext<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> context){super(context);}
    @Override public void render(MatrixStack matrices,VertexConsumerProvider buffers,int light,AbstractClientPlayerEntity player,float limbAngle,float limbDistance,float tickDelta,float animationProgress,float headYaw,float headPitch){
        var info=BossClient.info(player.getUuid());if(info==null||player.isInvisible())return;
        var model=BossOutfit.model(info.kind());if(model==null)return;
        model.pose(getContextModel(),limbDistance,animationProgress);
        model.root().render(matrices,buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(model.texture)),light,LivingEntityRenderer.getOverlay(player,0));
    }
}
