package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
    private void botand$punch(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if(CombatClient.actionLocked()){cir.setReturnValue(false);return;}
        if(dev.sangwoo.celestialfall.client.commerce.CommerceClient.clickFruit()){cir.setReturnValue(false);return;}
        // the meteor pickaxe strikes meteors (2.49.0) and does nothing else, the stand's attack included
        if(dev.sangwoo.celestialfall.client.meteor.MeteorClient.attack()){cir.setReturnValue(false);return;}
        BotandCamera.prepareInteraction();
        // Star Platinum out: its punches (2.54.0)
        if(dev.sangwoo.celestialfall.client.StandClient.punch()||dev.sangwoo.celestialfall.client.stand.StandAttack.punch()||CombatClient.punch())cir.setReturnValue(false);
    }
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void botand$use(CallbackInfo ci){if(CombatClient.actionLocked())ci.cancel();else {BotandCamera.prepareInteraction();if(dev.sangwoo.celestialfall.client.StandClient.useSkill())ci.cancel();}}
    @Inject(method="doItemPick",at=@At("HEAD"),cancellable=true)
    private void botand$pick(CallbackInfo ci){if(CombatClient.actionLocked())ci.cancel();}
    @Inject(method="handleBlockBreaking",at=@At("HEAD"),cancellable=true)
    private void botand$break(boolean breaking,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.meteor.MeteorClient.breaking(breaking)){var mc=(MinecraftClient)(Object)this;if(mc.interactionManager!=null)mc.interactionManager.cancelBlockBreaking();ci.cancel();return;}
        if(CombatClient.actionLocked()||dev.sangwoo.celestialfall.client.StandClient.visible(((MinecraftClient)(Object)this).player)||dev.sangwoo.celestialfall.client.stand.StandAttack.punching()||dev.sangwoo.celestialfall.client.commerce.CommerceClient.fruitInHand()) {var mc=(MinecraftClient)(Object)this;if(mc.interactionManager!=null)mc.interactionManager.cancelBlockBreaking();ci.cancel();}
    }
    @Redirect(method="handleInputEvents",at=@At(value="INVOKE",target="Lnet/minecraft/client/Mouse;isCursorLocked()Z"))
    private boolean botand$freeCursorMining(net.minecraft.client.Mouse mouse){return mouse.isCursorLocked()||BotandCamera.controls();}
    @Inject(method="handleInputEvents",at=@At("HEAD"))
    private void botand$drainLockedActions(CallbackInfo ci) {
        var mc=(MinecraftClient)(Object)this;
        if(!CombatClient.actionLocked())return;
        for(var key:new net.minecraft.client.option.KeyBinding[]{mc.options.attackKey,mc.options.useKey,mc.options.pickItemKey,mc.options.dropKey,mc.options.swapHandsKey,mc.options.inventoryKey})while(key.wasPressed()){}
        for(var key:mc.options.hotbarKeys)while(key.wasPressed()){}
    }
    /**
     * On the Botand map Esc opens the sky-blue pause menu instead of Minecraft's (2.45.0). F3+Esc in a world of your own
     * keeps Minecraft's bare pause; the game pauses and its sounds stop as they would for Minecraft's menu.
     */
    @Inject(method="openPauseMenu",at=@At("HEAD"),cancellable=true)
    private void botand$pauseMenu(boolean pauseOnly,CallbackInfo ci) {
        var mc=(MinecraftClient)(Object)this;
        if(mc.currentScreen!=null||!dev.sangwoo.celestialfall.client.ui.BotandPauseScreen.replaces())return;
        boolean local=mc.isIntegratedServerRunning()&&mc.getServer()!=null&&!mc.getServer().isRemote();
        if(pauseOnly&&local)return;
        mc.setScreen(new dev.sangwoo.celestialfall.client.ui.BotandPauseScreen());
        if(local)mc.getSoundManager().pauseAll();
        ci.cancel();
    }
    @Inject(method="setScreen",at=@At("HEAD"),cancellable=true)
    private void celestialfall$blockInventory(Screen screen,CallbackInfo ci) {
        if(screen instanceof HandledScreen<?> && (CinematicDirector.isActive()||CombatClient.actionLocked())) {
            ci.cancel();
            // Also close the server handler, so a chest opened by a packet cannot stay stuck.
            var mc=(MinecraftClient)(Object)this;
            if(mc.player!=null)mc.player.closeHandledScreen();
        }
    }
}
