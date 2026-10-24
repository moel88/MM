package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.ui.PlayerPanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** On the Botand map the player list at the top right stands in for Minecraft's Tab list (2.45.0). */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void botand$replaced(DrawContext context,int scaledWindowWidth,Scoreboard scoreboard,ScoreboardObjective objective,CallbackInfo ci) {
        if(PlayerPanel.replaces())ci.cancel();
    }
}
