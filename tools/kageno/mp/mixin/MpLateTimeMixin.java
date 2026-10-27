package dev.sangwoo.botandmp.mixin;

import dev.sangwoo.botandmp.MpClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 2.74.3 (MP_ONLY=fresh): the world's time packets dropped for the first ticks after joining again (MpClient.lateTime),
 * as if the server's time came late: the long cooldowns told as the player joins are then known while the world just
 * made still counts from 0, which is what put Atomic's box some 80 s ahead (CooldownHud.timed). The names are the
 * game's intermediary ones (ClientPlayNetworkHandler.onWorldTimeUpdate): the bot mod has no refmap.
 */
@Mixin(targets="net.minecraft.class_634",remap=false)
public abstract class MpLateTimeMixin {
    @Inject(method="method_11079",at=@At("HEAD"),cancellable=true,remap=false)
    private void botandmp$late(CallbackInfo ci){if(MpClient.lateTime())ci.cancel();}
}
