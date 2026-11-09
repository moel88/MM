package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.isosang.IsosangCinema;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lee So-sang's G (2.83.0): while its user's camera is in its cutscene's own space, that space's sky in place of the
 * world's, and no clouds (GStage); where the world cannot be hidden (a shader pack's mod), no sky, sun or clouds while it
 * is black round her hand (IsosangCinema.dark).
 */
@Mixin(WorldRenderer.class)
public abstract class IsosangSkyMixin {
    @Inject(method="renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",at=@At("HEAD"),cancellable=true)
    private void botand$ultimateSky(MatrixStack matrices,Matrix4f projection,float tickDelta,Camera camera,boolean thickFog,Runnable fog,CallbackInfo ci){
        if(dev.sangwoo.celestialfall.client.xchara.XCharaClient.spaceHidden()||dev.sangwoo.celestialfall.client.isosang.GStage.sky(matrices,tickDelta)||IsosangCinema.dark(tickDelta)>.6f)ci.cancel();
    }
    @Inject(method="renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V",at=@At("HEAD"),cancellable=true)
    private void botand$ultimateClouds(MatrixStack matrices,Matrix4f projection,float tickDelta,double x,double y,double z,CallbackInfo ci){
        if(dev.sangwoo.celestialfall.client.isosang.GStage.hidden()||dev.sangwoo.celestialfall.client.xchara.XCharaClient.spaceHidden()||IsosangCinema.dark(tickDelta)>.6f)ci.cancel();
    }
}
