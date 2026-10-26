package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** How far the status effect icons were moved this frame (2.45.0), undone right after them. */
    @Unique private float botand$effectShift;
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/hud/InGameHud;renderStatusEffectOverlay(Lnet/minecraft/client/gui/DrawContext;)V",shift=At.Shift.AFTER))
    private void botand$hudBeforeDebug(DrawContext context,float delta,CallbackInfo ci) {
        // undone here rather than at the icons' own return, so a mod that stops them early cannot leave the HUD moved
        if(botand$effectShift!=0){context.getMatrices().translate(botand$effectShift,0,0);botand$effectShift=0;}
        dev.sangwoo.celestialfall.client.BotandHud.render(context,delta);
        dev.sangwoo.celestialfall.client.boss.BossBar.render(context,delta);
    }
    /** The status effect icons step left of the player list while it shows (2.45.0). */
    @Inject(method="renderStatusEffectOverlay",at=@At("HEAD"))
    private void botand$effectsBesideList(DrawContext context,CallbackInfo ci) {
        botand$effectShift=(float)dev.sangwoo.celestialfall.client.ui.PlayerPanel.effectShift();
        if(botand$effectShift!=0)context.getMatrices().translate(-botand$effectShift,0,0);
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
