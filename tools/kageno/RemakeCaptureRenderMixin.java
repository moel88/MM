package dev.sangwoo.botandverify.mixin;
import dev.sangwoo.botandverify.RemakeCapture;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
public abstract class RemakeCaptureRenderMixin {
 @Inject(method="render",at=@At("TAIL"))
 private void after(boolean tick,CallbackInfo ci){RemakeCapture.afterFrame();}
}
