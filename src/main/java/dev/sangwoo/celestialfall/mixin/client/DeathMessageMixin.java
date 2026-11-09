package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.DeathView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (2.107.0) The player dies on the Botand map: no death screen (DeathView) and no ask to come back, the server bringing
 * them back after five seconds (AutoRespawn). What they had open closes, as the death screen would have taken its place.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class DeathMessageMixin {
    @Inject(method="onDeathMessage",at=@At(value="INVOKE",target="Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V",shift=At.Shift.AFTER),cancellable=true)
    private void botand$noDeathScreen(DeathMessageS2CPacket packet,CallbackInfo ci){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||packet.getEntityId()!=mc.player.getId()||!DeathView.handled())return;
        // (2.107.1) dead to the view from now, before the health comes: no roll, no keys in between
        DeathView.died(mc.player);
        if(mc.currentScreen!=null)mc.setScreen(null);
        ci.cancel();
    }
}
