package dev.sangwoo.botandverify.mixin;
import dev.sangwoo.botandverify.KagenoCapture;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
public abstract class KagenoCaptureRenderMixin {
 @Inject(method="render",at=@At("TAIL"))
 private void captureCompleteFrame(boolean tick,CallbackInfo ci){KagenoCapture.afterFrame();}
}
