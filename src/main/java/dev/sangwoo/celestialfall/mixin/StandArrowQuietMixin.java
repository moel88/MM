package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.stand.StandArrow;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The chat lines a death sends round (2.71.0): none while one the stand arrow killed is dying (StandArrowDeathMixin). */
@Mixin(PlayerManager.class)
public abstract class StandArrowQuietMixin {
    @Inject(method="broadcast(Lnet/minecraft/text/Text;Z)V",at=@At("HEAD"),cancellable=true)
    private void botand$quietBroadcast(Text message,boolean overlay,CallbackInfo ci){if(StandArrow.quiet())ci.cancel();}
    @Inject(method="sendToTeam",at=@At("HEAD"),cancellable=true)
    private void botand$quietTeam(PlayerEntity source,Text message,CallbackInfo ci){if(StandArrow.quiet())ci.cancel();}
    @Inject(method="sendToOtherTeams",at=@At("HEAD"),cancellable=true)
    private void botand$quietOtherTeams(PlayerEntity source,Text message,CallbackInfo ci){if(StandArrow.quiet())ci.cancel();}
}
