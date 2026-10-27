package dev.sangwoo.celestialfall.mixin.client;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Whom Star Platinum's time stop stopped hear nothing but its own sound until time moves again (2.67.0, StandTimeStop). */
@Mixin(SoundSystem.class)
public abstract class TimeStopSoundMixin {
    @Inject(method="play(Lnet/minecraft/client/sound/SoundInstance;)V",at=@At("HEAD"),cancellable=true)
    private void botand$hushed(SoundInstance sound,CallbackInfo ci){
        if(dev.sangwoo.celestialfall.client.stand.StandTimeStop.hushed(sound))ci.cancel();
    }
}
