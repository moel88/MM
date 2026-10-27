package dev.sangwoo.celestialfall.boss;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * A boss's connection (2.69.0): nobody is at the other end. What the server would send its player goes nowhere, and
 * nothing comes from it: the boss's body moves on the server (BossPlayer.tick), as its brain steers it.
 */
final class BossConnection extends ServerPlayNetworkHandler {
    BossConnection(ServerPlayerEntity boss){super(boss.getServer(),new ClientConnection(NetworkSide.SERVERBOUND),boss);}
    @Override public void sendPacket(Packet<?> packet,@Nullable PacketCallbacks callbacks){}
    /** (The server sends every player off as it stops: a boss is taken off the map before that, Bosses.) */
    @Override public void disconnect(Text reason){}
}
