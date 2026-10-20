package dev.sangwoo.celestialfall.map;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.List;

/**
 * Hands a defined map's backdrop (the mountains of the far coast across the water) to each player who joins, and
 * again to everyone when the data packs are reloaded, for the client to draw behind everything over the map's own
 * dimension. A world without a definition sends none, which clears what a client was drawing.
 */
public final class MapBackdrop {
    public static final Identifier CHANNEL=CelestialFall.id("map_backdrop_v1");
    private MapBackdrop(){}

    static void initialize(){
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->send(handler.player,MapGate.definition(server).map(MapDefinition::backdrop).orElse(List.of())));
    }
    /** Sends the current backdrop to everyone on the server. */
    static void sendAll(MinecraftServer server){
        var peaks=MapGate.definition(server).map(MapDefinition::backdrop).orElse(List.of());
        for(var p:server.getPlayerManager().getPlayerList())send(p,peaks);
    }
    private static void send(ServerPlayerEntity p,List<MapDefinition.Peak> peaks){
        if(!ServerPlayNetworking.canSend(p,CHANNEL))return;
        ServerPlayNetworking.send(p,CHANNEL,write(peaks));
    }
    /** The packet: the dimension the mountains stand around, their count, then five floats and a seed each. */
    static PacketByteBuf write(List<MapDefinition.Peak> peaks){
        var b=PacketByteBufs.create();
        b.writeIdentifier(World.OVERWORLD.getValue());
        int n=Math.min(peaks.size(),MapDefinition.MAX_PEAKS);
        b.writeVarInt(n);
        for(int i=0;i<n;i++){var m=peaks.get(i);b.writeFloat(m.x());b.writeFloat(m.z());b.writeFloat(m.rx());b.writeFloat(m.rz());b.writeFloat(m.top());b.writeInt(m.seed());}
        return b;
    }
}
