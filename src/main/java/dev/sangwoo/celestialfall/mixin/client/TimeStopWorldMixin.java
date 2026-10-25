package dev.sangwoo.celestialfall.mixin.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What Star Platinum's time stop stopped does not tick on this client until time moves again (2.67.0): it neither moves
 * nor turns nor stirs, and is drawn as on the last frame before (WorldRendererMixin, StandTimeStop.stillDelta).
 */
@Mixin(ClientWorld.class)
public abstract class TimeStopWorldMixin {
    @Inject(method="tickEntity",at=@At("HEAD"),cancellable=true)
    private void botand$stopped(Entity entity,CallbackInfo ci){
        if(!Float.isNaN(dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillDelta(entity)))ci.cancel();
    }
}
