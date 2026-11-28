package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.engine.EngineLag;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (2.157.0) The combat engine's simulated round trip ({@code /botand engine lag}, EngineLag): on the server's side of a
 * player's connection, what it sends them and what it reads from them wait half of it each, in their order. Nothing is
 * held back unless that tool put a round trip on the player.
 */
@Mixin(ClientConnection.class)
public abstract class EngineLagMixin {
    @Shadow private Channel channel;
    @Shadow public abstract PacketListener getPacketListener();
    @Shadow public abstract void send(Packet<?> packet,@Nullable PacketCallbacks callbacks);
    @Shadow protected abstract void channelRead0(ChannelHandlerContext context,Packet<?> packet);

    @Inject(method="send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",at=@At("HEAD"),cancellable=true)
    private void botand$heldOut(Packet<?> packet,@Nullable PacketCallbacks callbacks,CallbackInfo ci){
        if(!EngineLag.any()||EngineLag.passing()||channel==null)return;
        if(!(getPacketListener() instanceof ServerPlayNetworkHandler h)||h.player==null)return;
        int ms=EngineLag.each(h.player.getUuid());if(ms<=0)return;
        ci.cancel();
        channel.eventLoop().schedule(()->EngineLag.pass(()->send(packet,callbacks)),ms,TimeUnit.MILLISECONDS);
    }
    @Inject(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",at=@At("HEAD"),cancellable=true)
    private void botand$heldIn(ChannelHandlerContext context,Packet<?> packet,CallbackInfo ci){
        if(!EngineLag.any()||EngineLag.passing())return;
        if(!(getPacketListener() instanceof ServerPlayNetworkHandler h)||h.player==null)return;
        int ms=EngineLag.each(h.player.getUuid());if(ms<=0)return;
        ci.cancel();
        context.channel().eventLoop().schedule(()->EngineLag.pass(()->channelRead0(context,packet)),ms,TimeUnit.MILLISECONDS);
    }
}
