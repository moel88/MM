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
 * Hands a defined map's wires (its power and telephone lines) to each player who joins, and again to everyone when
 * the data packs are reloaded, for the client to draw as thin sagging lines over the map's own dimension. A world
 * without a definition sends none, which clears what a client was drawing.
 */
public final class MapWires {
    public static final Identifier CHANNEL=CelestialFall.id("map_wires_v1");
    private MapWires(){}

    static void initialize(){
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->send(handler.player,MapGate.definition(server).map(MapDefinition::wires).orElse(List.of())));
    }
    /** Sends the current wires to everyone on the server. */
    static void sendAll(MinecraftServer server){
        var wires=MapGate.definition(server).map(MapDefinition::wires).orElse(List.of());
        for(var p:server.getPlayerManager().getPlayerList())send(p,wires);
    }
    private static void send(ServerPlayerEntity p,List<MapDefinition.Wire> wires){
        if(!ServerPlayNetworking.canSend(p,CHANNEL))return;
        ServerPlayNetworking.send(p,CHANNEL,write(wires));
    }
    /** The packet: the dimension the wires hang in, their count, then seven floats each. */
    static PacketByteBuf write(List<MapDefinition.Wire> wires){
        var b=PacketByteBufs.create();
        b.writeIdentifier(World.OVERWORLD.getValue());
        int n=Math.min(wires.size(),MapDefinition.MAX_WIRES);
        b.writeVarInt(n);
        for(int i=0;i<n;i++){var w=wires.get(i);b.writeFloat(w.x0());b.writeFloat(w.y0());b.writeFloat(w.z0());b.writeFloat(w.x1());b.writeFloat(w.y1());b.writeFloat(w.z1());b.writeFloat(w.sag());}
        return b;
    }
}
