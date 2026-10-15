package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Colours measured from the review: R outline #21b6d2, Q shadow-mark outline dark violet. */
@Mixin(Entity.class)
public abstract class KagenoOutlineColorMixin {
    @Inject(method="getTeamColorValue",at=@At("RETURN"),cancellable=true)
    private void botand$timeStopOutlineColor(CallbackInfoReturnable<Integer> cir){var e=(Entity)(Object)this;if(StandClient.rOutlined(e))cir.setReturnValue(0x21b6d2);else if(StandClient.shadowOutlined(e))cir.setReturnValue(0x5a2d9a);}
}
