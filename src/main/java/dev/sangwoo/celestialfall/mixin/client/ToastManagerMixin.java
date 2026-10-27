package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method="draw",at=@At("HEAD"),cancellable=true)
    private void celestialfall$hideToasts(DrawContext context,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.title.TitleMapCamera.active()||CinematicDirector.isActive())ci.cancel();
    }
}
