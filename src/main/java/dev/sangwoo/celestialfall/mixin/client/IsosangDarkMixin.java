package dev.sangwoo.celestialfall.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.isosang.IsosangCinema;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lee So-sang's G (2.83.0): through the first shots of its cutscene the world round its user's hand goes black, as the
 * videos' hand is on black: the fog closes in to a couple of blocks and its colour (and the sky's clear colour) goes to
 * black, only for the user's own camera (IsosangCinema.dark).
 */
@Mixin(BackgroundRenderer.class)
public abstract class IsosangDarkMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;
    @Inject(method="render",at=@At("TAIL"))
    private static void botand$ultimateDark(Camera camera,float tickDelta,ClientWorld world,int viewDistance,float skyDarkness,CallbackInfo ci){
        float d=IsosangCinema.dark(tickDelta);if(d<=0)return;
        red*=1-d;green*=1-d;blue*=1-d;
        RenderSystem.clearColor(red,green,blue,0);
    }
    @Inject(method="applyFog",at=@At("TAIL"))
    private static void botand$ultimateFog(Camera camera,BackgroundRenderer.FogType fogType,float viewDistance,boolean thickFog,float tickDelta,CallbackInfo ci){
        if(camera.getSubmersionType()!=CameraSubmersionType.NONE)return;
        float d=IsosangCinema.dark(tickDelta);if(d<=0)return;
        float start=RenderSystem.getShaderFogStart(),end=RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(start+(1.0f-start)*d);
        RenderSystem.setShaderFogEnd(end+(2.6f-end)*d);
    }
}
