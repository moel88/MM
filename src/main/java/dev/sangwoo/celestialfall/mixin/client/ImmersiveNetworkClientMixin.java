package dev.sangwoo.celestialfall.mixin.client;

import com.immersivecinematics.immersive_cinematics.fabric.FabricNetwork;
import com.immersivecinematics.immersive_cinematics.trigger.network.CinematicC2SPacket;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** IC's preload/ack tick may run after disconnect in the same Fabric end-tick dispatch. */
@Mixin(value = FabricNetwork.class, remap = false)
public abstract class ImmersiveNetworkClientMixin {
    @Inject(method = "sendToServer", at = @At("HEAD"), cancellable = true, remap = false)
    private void botand$dropDisconnectedPacket(CinematicC2SPacket packet, CallbackInfo ci) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null || !handler.getConnection().isOpen()) ci.cancel();
    }
}
