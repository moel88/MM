package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.stand.StandArrowClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The cry of death a client plays itself for one the stand arrow rejected, held back (2.71.0, StandArrowSilentMixin). */
@Mixin(PlayerEntity.class)
public abstract class StandArrowSilentClientMixin {
    @Inject(method="getDeathSound",at=@At("HEAD"),cancellable=true)
    private void botand$silentArrowDeath(CallbackInfoReturnable<SoundEvent> cir){
        var self=(PlayerEntity)(Object)this;
        if(self.getWorld().isClient&&StandArrowClient.silent(self))cir.setReturnValue(null);
    }
}
