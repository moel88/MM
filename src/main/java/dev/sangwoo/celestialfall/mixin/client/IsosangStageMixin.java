package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.isosang.GStage;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lee So-sang's G's own space (2.83.0, GStage): while its user's camera is in its cutscene there, the world's blocks (every
 * layer of them, water too), weather, border and the outline of the block looked at are not drawn. Each is left out
 * quietly if another mod has taken it over (require 0): the world then stays, as with a shader pack.
 */
@Mixin(WorldRenderer.class)
public abstract class IsosangStageMixin {
    @Inject(method="renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;DDDLorg/joml/Matrix4f;)V",at=@At("HEAD"),cancellable=true,require=0)
    private void botand$stageBlocks(RenderLayer layer,MatrixStack matrices,double x,double y,double z,Matrix4f projection,CallbackInfo ci){
        if(GStage.hidden())ci.cancel();
    }
    @Inject(method="renderWeather",at=@At("HEAD"),cancellable=true,require=0)
    private void botand$stageWeather(LightmapTextureManager manager,float tickDelta,double x,double y,double z,CallbackInfo ci){
        if(GStage.hidden())ci.cancel();
    }
    @Inject(method="renderWorldBorder",at=@At("HEAD"),cancellable=true,require=0)
    private void botand$stageBorder(Camera camera,CallbackInfo ci){
        if(GStage.hidden())ci.cancel();
    }
    @Inject(method="drawBlockOutline",at=@At("HEAD"),cancellable=true,require=0)
    private void botand$stageOutline(MatrixStack matrices,VertexConsumer vertices,Entity entity,double x,double y,double z,BlockPos pos,BlockState state,CallbackInfo ci){
        if(GStage.hidden())ci.cancel();
    }
}
