package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/hud/InGameHud;renderStatusEffectOverlay(Lnet/minecraft/client/gui/DrawContext;)V",shift=At.Shift.AFTER))
    private void botand$hudBeforeDebug(DrawContext context,float delta,CallbackInfo ci) {
        dev.sangwoo.celestialfall.client.BotandHud.render(context,delta);
    }
    @Inject(method="renderStatusBars",at=@At("HEAD"),cancellable=true)
    private void botand$replaceStatus(DrawContext context,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.BotandHud.enabled()){dev.sangwoo.celestialfall.client.BotandHud.survivalIndicators(context);ci.cancel();}
    }
    @Inject(method="renderExperienceBar",at=@At("HEAD"),cancellable=true)
    private void botand$replaceLevel(DrawContext context,int x,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.BotandHud.enabled())ci.cancel();
    }
    @Inject(method="renderCrosshair",at=@At("HEAD"),cancellable=true)
    private void botand$crosshair(DrawContext context,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.BotandCamera.enabled()&&!dev.sangwoo.celestialfall.client.BotandCamera.locked())ci.cancel();
    }
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void celestialfall$hideHud(DrawContext context,float delta,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.title.TitleMapCamera.active()||CinematicDirector.isActive())ci.cancel();
    }
}
