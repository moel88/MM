package dev.sangwoo.gachashots.mixin;

import dev.sangwoo.gachashots.GachaShots;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sound QA (2.40.0): every gacha sound the game starts is logged with the screen's clock and System.nanoTime, so
 * mux_audio.py can find each one in the recording and line the recording up with the films by them.
 */
@Mixin(targets="dev.sangwoo.celestialfall.client.gacha.GachaSound",remap=false)
public abstract class GachaShotsSoundMixin {
    @Inject(method="start",at=@At("HEAD"))
    private static void shots$started(@Coerce Object cue,float pitch,float volume,CallbackInfoReturnable<SoundInstance> cir){GachaShots.played(String.valueOf(cue),pitch);}
}
