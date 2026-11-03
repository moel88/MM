package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.isosang.GStage;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lee So-sang's G's own space (2.83.0, GStage): no particles while its user's camera is in its cutscene there. */
@Mixin(ParticleManager.class)
public abstract class IsosangStageParticlesMixin {
    @Inject(method="renderParticles",at=@At("HEAD"),cancellable=true,require=0)
    private void botand$stageParticles(MatrixStack matrices,VertexConsumerProvider.Immediate vertices,LightmapTextureManager light,Camera camera,float tickDelta,CallbackInfo ci){
        if(GStage.hidden())ci.cancel();
    }
}
