package dev.sangwoo.botandmap.mixin;

import dev.sangwoo.botandmap.MapShots;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The map filming tool's hooks: the camera placed before each frame is drawn, the frame kept after. */
@Mixin(MinecraftClient.class)
public abstract class MapShotsMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/GameRenderer;render(FJZ)V"))
    private void map$before(boolean tick,CallbackInfo ci){MapShots.beforeFrame();}
    @Inject(method="render",at=@At("TAIL"))
    private void map$after(boolean tick,CallbackInfo ci){MapShots.afterFrame();}
}
