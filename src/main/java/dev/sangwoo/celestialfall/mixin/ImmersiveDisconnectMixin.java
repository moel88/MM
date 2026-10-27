package dev.sangwoo.celestialfall.mixin;

import com.immersivecinematics.immersive_cinematics.handler.ServerEventHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric disconnect callbacks can run on Netty. Camera chunk tickets belong to the server thread. */
@Mixin(value = ServerEventHandler.class, remap = false)
public abstract class ImmersiveDisconnectMixin {
    @Inject(method = "onPlayerQuit", at = @At("HEAD"), cancellable = true, remap = false)
    private static void botand$quitOnServerThread(ServerPlayerEntity player, CallbackInfo ci) {
        var server = player.getServer();
        if (server != null && !server.isOnThread()) {
            server.execute(() -> ServerEventHandler.onPlayerQuit(player));
            ci.cancel();
        }
    }
}
