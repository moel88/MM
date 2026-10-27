package dev.sangwoo.celestialfall.mixin.client;

import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The sound system behind the sound manager (2.67.0: the time stop hushes the sounds of whom it stopped, StandTimeStop). */
@Mixin(SoundManager.class)
public interface SoundManagerAccess {
    @Accessor("soundSystem") SoundSystem botand$system();
}
