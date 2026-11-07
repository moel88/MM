package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.isosang.IsosangAttack;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lee So-sang's formation carries her (2.96.0: T's dash and its flight on to the far sword, Z's flight, the dodge's): her
 * own client flies her there along the formation's way, a long flight's first tick over 10 blocks. The server took that
 * for moving too quickly ("moved too quickly!") and put her back where she landed, so to everyone else she stood there
 * until the formation put her on the far sword a second later (the multiplayer check; a host is never checked, so in
 * single player it was never seen). While the formation carries her the server lets her moves through as it does a
 * teleport's (IsosangAttack.carried, a few ticks either side); the formation holds her to its way after.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class FormationMoveMixin {
    @Redirect(method="onPlayerMove",at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerPlayerEntity;isInTeleportationState()Z"))
    private boolean botand$carried(ServerPlayerEntity player){
        return player.isInTeleportationState()||IsosangAttack.carried(player);
    }
}
