package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.AutoRespawn;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (2.107.0) One dead on the Botand map comes back after five seconds, brought back by the server (AutoRespawn): an ask
 * to come back before then (an older client's respawn button, its death screen) is let go unanswered, on the server's
 * thread. The ask of one leaving the End is never held.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class AutoRespawnMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="onClientStatus",at=@At(value="INVOKE",target="Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",shift=At.Shift.AFTER),cancellable=true)
    private void botand$notYet(ClientStatusC2SPacket packet,CallbackInfo ci){
        if(packet.getMode()==ClientStatusC2SPacket.Mode.PERFORM_RESPAWN&&AutoRespawn.early(player))ci.cancel();
    }
}
