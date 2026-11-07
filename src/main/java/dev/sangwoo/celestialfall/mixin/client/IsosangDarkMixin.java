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
 * black, only for the user's own camera (IsosangCinema.dark); and as its fire leaps up, a beige haze of dust over
 * everything near, for anyone within reach of it (IsosangCinema.dust).
 */
@Mixin(BackgroundRenderer.class)
public abstract class IsosangDarkMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;
    @Inject(method="render",at=@At("TAIL"))
    private static void botand$ultimateDark(Camera camera,float tickDelta,ClientWorld world,int viewDistance,float skyDarkness,CallbackInfo ci){
        // (its dust: a beige haze as the fire leaps up, for anyone near)
        float dust=IsosangCinema.dust(tickDelta)*.85f;
        if(dust>0){red+=(.62f-red)*dust;green+=(.53f-green)*dust;blue+=(.41f-blue)*dust;RenderSystem.clearColor(red,green,blue,0);}
        // (2.101.0) H again's cutscene: a red dusk, the world darker into the distance
        float k=IsosangCinema.dusk(tickDelta);
        if(k>0){red+=(DUSK[0]-red)*k;green+=(DUSK[1]-green)*k;blue+=(DUSK[2]-blue)*k;RenderSystem.clearColor(red,green,blue,0);}
        float d=IsosangCinema.dark(tickDelta);if(d<=0)return;
        red*=1-d;green*=1-d;blue*=1-d;
        RenderSystem.clearColor(red,green,blue,0);
    }
    /** H again's dusk's fog colour (2.101.0: the videos' world gone a dark navy under its amber sky), and how near its fog closes in. */
    private static final float[] DUSK={.035f,.05f,.085f};
    @Inject(method="applyFog",at=@At("TAIL"))
    private static void botand$ultimateFog(Camera camera,BackgroundRenderer.FogType fogType,float viewDistance,boolean thickFog,float tickDelta,CallbackInfo ci){
        if(camera.getSubmersionType()!=CameraSubmersionType.NONE)return;
        float dust=IsosangCinema.dust(tickDelta)*.8f;
        if(dust>0){float start=RenderSystem.getShaderFogStart(),end=RenderSystem.getShaderFogEnd();
            RenderSystem.setShaderFogStart(start+(2f-start)*dust);RenderSystem.setShaderFogEnd(end+(28f-end)*dust);}
        float k=IsosangCinema.dusk(tickDelta);
        if(k>0){float start=RenderSystem.getShaderFogStart(),end=RenderSystem.getShaderFogEnd();
            RenderSystem.setShaderFogStart(start+(4f-start)*k);RenderSystem.setShaderFogEnd(end+(44f-end)*k);}
        float d=IsosangCinema.dark(tickDelta);if(d<=0)return;
        float start=RenderSystem.getShaderFogStart(),end=RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(start+(1.0f-start)*d);
        RenderSystem.setShaderFogEnd(end+(2.6f-end)*d);
    }
}
