package dev.sangwoo.celestialfall.mixin.client;

import java.util.Map;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The sounds playing and their sources (2.67.0: whom the time stop stopped hear nothing but its own, StandTimeStop). */
@Mixin(SoundSystem.class)
public interface SoundSystemAccess {
    @Accessor("sources") Map<SoundInstance,Channel.SourceManager> botand$sources();
}
