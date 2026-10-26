package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.stand.StandArrow;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * One the stand arrow kills dies without the game's cry of death (2.71.0): the stab's own sounds end it (the heart's
 * last beats, the body on the ground, StandArrowSounds). The server's here (StandArrow.kill); each client holds back the
 * one it plays itself (client/StandArrowSilentClientMixin).
 */
@Mixin(PlayerEntity.class)
public abstract class StandArrowSilentMixin {
    @Inject(method="getDeathSound",at=@At("HEAD"),cancellable=true)
    private void botand$silentArrowDeath(CallbackInfoReturnable<SoundEvent> cir){
        if(!((PlayerEntity)(Object)this).getWorld().isClient&&StandArrow.dying(this))cir.setReturnValue(null);
    }
}
