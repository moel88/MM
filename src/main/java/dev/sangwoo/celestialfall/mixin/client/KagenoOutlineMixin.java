package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R: the caster keeps a cyan screen-space outline while the world is gray; Q shadow marks show to their owner. */
@Mixin(MinecraftClient.class)
public abstract class KagenoOutlineMixin {
    @Inject(method="hasOutline",at=@At("RETURN"),cancellable=true)
    private void botand$timeStopOutline(Entity entity,CallbackInfoReturnable<Boolean> cir){if(StandClient.rOutlined(entity)||StandClient.shadowOutlined(entity))cir.setReturnValue(true);}
}
