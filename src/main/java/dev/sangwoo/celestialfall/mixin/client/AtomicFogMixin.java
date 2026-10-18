package dev.sangwoo.celestialfall.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.atomic.AtomicAmbience;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The fog and clear colour follow the darkened sky, so the horizon matches it (not under water or lava). */
@Mixin(BackgroundRenderer.class)
public abstract class AtomicFogMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;
    @Inject(method="render",at=@At("TAIL"))
    private static void botand$atomicFog(Camera camera,float tickDelta,ClientWorld world,int viewDistance,float skyDarkness,CallbackInfo ci){
        if(camera.getSubmersionType()!=CameraSubmersionType.NONE)return;
        float[] c=AtomicAmbience.fog(red,green,blue,camera.getPos(),tickDelta);
        if(c==null)return;
        red=c[0];green=c[1];blue=c[2];
        RenderSystem.clearColor(red,green,blue,0);
    }
}
