package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.title.BotandTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent the ordinary world playlist from starting over the welcome performance. */
@Mixin(MusicTracker.class)
public abstract class TitleMusicMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void botand$titleMusic(CallbackInfo ci){
        if(MinecraftClient.getInstance().currentScreen instanceof BotandTitleScreen screen&&screen.liveCinema())ci.cancel();
    }
}
