package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CelestialRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    /** The sun and moon share this colour; its alpha (1 − rain) drops to 0 while H looks straight up. */
    @ModifyArg(method="renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
        at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",ordinal=2),index=3)
    private float botand$hideSun(float alpha) {
        float delta=MinecraftClient.getInstance().getTickDelta();
        // The sun and moon also fade while G's pillar darkens the sky around a viewer outside it.
        return dev.sangwoo.celestialfall.client.kageno.KagenoCamera.hideSun(delta)?0:alpha*(1-dev.sangwoo.celestialfall.client.atomic.AtomicAmbience.dim(delta));
    }
    @Inject(method="renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at=@At("TAIL"))
    private void celestialfall$sky(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta,
                                    Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        if(!thickFog) CelestialRenderer.renderSky(matrices,tickDelta);
    }
}
