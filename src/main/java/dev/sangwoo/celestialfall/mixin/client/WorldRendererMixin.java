package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CelestialRenderer;
import dev.sangwoo.celestialfall.client.HeavenClient;
import dev.sangwoo.celestialfall.client.HeavenTerrainCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @ModifyVariable(method="setupTerrain",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Camera celestialfall$loadedTerrain(Camera view) {
        var mc=MinecraftClient.getInstance();
        // Vanilla keeps chunk storage around the player. Seed traversal there as well,
        // while the actual view/projection and supplied frustum remain at the wide camera.
        return (HeavenClient.wideCinematic()||dev.sangwoo.celestialfall.client.title.TitleMapCamera.active()) && mc.player!=null?new HeavenTerrainCamera(view,mc.player.getEyePos()):view;
    }
    @Redirect(method="setupTerrain",at=@At(value="FIELD",target="Lnet/minecraft/client/MinecraftClient;chunkCullingEnabled:Z"))
    private boolean celestialfall$wideOcclusion(MinecraftClient mc) {
        // Occlusion from the player's position is not valid for a detached camera.
        return !HeavenClient.wideCinematic() && !dev.sangwoo.celestialfall.client.title.TitleMapCamera.active() && mc.chunkCullingEnabled;
    }
    /** The sun and moon share this colour; its alpha (1 − rain) drops to 0 while H looks straight up. */
    @ModifyArg(method="renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
        at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",ordinal=2),index=3)
    private float botand$hideSun(float alpha) {
        return dev.sangwoo.celestialfall.client.kageno.KagenoCamera.hideSun(MinecraftClient.getInstance().getTickDelta())?0:alpha;
    }
    @Inject(method="renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at=@At("TAIL"))
    private void celestialfall$sky(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta,
                                    Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        if(!thickFog) CelestialRenderer.renderSky(matrices,tickDelta);
    }
}
