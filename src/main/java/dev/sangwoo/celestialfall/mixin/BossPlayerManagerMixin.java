package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.boss.Bosses;
import java.util.UUID;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The bosses (2.69.0) are players nobody joined as: not in the server's list of players (no slot taken, no count, no
 * save), yet found by id as any player is, so every skill that looks its user up by id finds them; and a player who
 * joins learns of their bodies before the world shows them (a client draws no player it has no entry for).
 */
@Mixin(PlayerManager.class)
public abstract class BossPlayerManagerMixin {
    @Inject(method="getPlayer(Ljava/util/UUID;)Lnet/minecraft/server/network/ServerPlayerEntity;",at=@At("RETURN"),cancellable=true)
    private void botand$boss(UUID id,CallbackInfoReturnable<ServerPlayerEntity> cir){
        if(cir.getReturnValue()==null){var boss=Bosses.get(id);if(boss!=null)cir.setReturnValue(boss);}
    }
    @Inject(method="onPlayerConnect",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerWorld;onPlayerConnected(Lnet/minecraft/server/network/ServerPlayerEntity;)V"))
    private void botand$bossesFirst(ClientConnection connection,ServerPlayerEntity player,CallbackInfo ci){Bosses.introduce(player);}
}
