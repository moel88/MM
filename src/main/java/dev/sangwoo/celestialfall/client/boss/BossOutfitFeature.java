package dev.sangwoo.celestialfall.client.boss;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Draws a boss's outfit over its body (2.69.0, BossOutfit), as the player's model has its limbs this frame, flashing red with it when struck; and (2.137.0) what of X Chara shines by itself. */
public final class BossOutfitFeature extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    public BossOutfitFeature(FeatureRendererContext<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> context){super(context);}
    @Override public void render(MatrixStack matrices,VertexConsumerProvider buffers,int light,AbstractClientPlayerEntity player,float limbAngle,float limbDistance,float tickDelta,float animationProgress,float headYaw,float headPitch){
        var info=BossClient.info(player.getUuid());if(info==null||player.isInvisible())return;
        var model=BossOutfit.model(info.kind());if(model==null)return;
        model.pose(getContextModel(),limbDistance,animationProgress);
        model.root().render(matrices,buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(model.texture)),light,LivingEntityRenderer.getOverlay(player,0));
        if(!dev.sangwoo.celestialfall.client.xchara.BossAura.KIND.equals(info.kind()))return;
        // (2.137.0, X Chara) what of the skin and of the outfit shines by itself, added at full light: violet by night
        float k=Math.min(1.25f,dev.sangwoo.celestialfall.client.xchara.BossAura.shine(player,tickDelta))
            *dev.sangwoo.celestialfall.client.xchara.BossAura.byDark(net.minecraft.client.render.LightmapTextureManager.getBlockLightCoordinates(light),net.minecraft.client.render.LightmapTextureManager.getSkyLightCoordinates(light),player.getWorld() instanceof net.minecraft.client.world.ClientWorld w?w.getSkyBrightness(tickDelta):1);
        if(k>0){
            if(model.skinGlow!=null)getContextModel().render(matrices,buffers.getBuffer(RenderLayer.getEyes(model.skinGlow)),0xF000F0,net.minecraft.client.render.OverlayTexture.DEFAULT_UV,Math.min(1,k),Math.min(1,k),Math.min(1,k),1);
            if(model.glow!=null)model.root().render(matrices,buffers.getBuffer(RenderLayer.getEyes(model.glow)),0xF000F0,net.minecraft.client.render.OverlayTexture.DEFAULT_UV,Math.min(1,k),Math.min(1,k),Math.min(1,k),1);
        }
    }
}
