package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.boss.BossPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A boss is fought whatever the server says of PvP (2.69.0): it and the players always harm each other (bosses never each other). */
@Mixin(ServerPlayerEntity.class)
public abstract class BossPvpMixin {
    @Inject(method="shouldDamagePlayer",at=@At("HEAD"),cancellable=true)
    private void botand$boss(PlayerEntity other,CallbackInfoReturnable<Boolean> cir){
        boolean self=(Object)this instanceof BossPlayer,them=other instanceof BossPlayer;
        if(self||them)cir.setReturnValue(self!=them);
    }
}
