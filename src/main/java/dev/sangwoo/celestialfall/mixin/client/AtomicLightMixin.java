package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.atomic.AtomicAmbience;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World light dims and turns violet around G's pillar (lighting, not a screen filter: the pillar itself is unlit). */
@Mixin(LightmapTextureManager.class)
public abstract class AtomicLightMixin {
    @Shadow @Final private NativeImage image;
    @Inject(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/texture/NativeImageBackedTexture;upload()V"))
    private void botand$atomicLight(float delta,CallbackInfo ci){
        float k=AtomicAmbience.dim(delta);
        if(k<=0)return;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setColor(x,y,AtomicAmbience.light(image.getColor(x,y),k));
    }
}
