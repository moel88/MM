package dev.sangwoo.gachashots.mixin;

import dev.sangwoo.gachashots.GachaShots;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class GachaShotsMixins {
    @Inject(method="render",at=@At("TAIL"))
    private void shots$frame(boolean tick,CallbackInfo ci){GachaShots.afterFrame();}
}
