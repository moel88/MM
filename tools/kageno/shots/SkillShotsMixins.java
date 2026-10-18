package dev.sangwoo.botandshots.mixin;

import dev.sangwoo.botandshots.SkillShots;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class SkillShotsMixins {
    @Inject(method="render",at=@At("TAIL"))
    private void shots$frame(boolean tick,CallbackInfo ci){SkillShots.afterFrame();}
}

