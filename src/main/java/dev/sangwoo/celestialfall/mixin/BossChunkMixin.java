package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.boss.BossPlayer;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nobody watches the world through a boss (2.69.0): the chunks round it stay loaded as round any player (its body
 * needs the ground), but none is packed up to be sent to it.
 */
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class BossChunkMixin {
    @Inject(method="sendWatchPackets",at=@At("HEAD"),cancellable=true)
    private void botand$nobodyWatches(ServerPlayerEntity player,ChunkPos pos,MutableObject<ChunkDataS2CPacket> packet,boolean oldWithinViewDistance,boolean newWithinViewDistance,CallbackInfo ci){
        if(player instanceof BossPlayer)ci.cancel();
    }
}
