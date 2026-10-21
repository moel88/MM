package dev.sangwoo.botandmapmp;

import dev.sangwoo.celestialfall.commerce.MerchantEntity;
import dev.sangwoo.celestialfall.loot.FieldLootEntity;
import dev.sangwoo.celestialfall.loot.LootPositions;
import dev.sangwoo.celestialfall.map.MapGate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;

/**
 * The map's multiplayer check, server half (only with -Dbotand.mapmp.server=true): on a dedicated server running the
 * built seaside world with the release jar, logs whether the mod takes the world for a Botand map from its definition,
 * where the two merchants and the field supplies stand, and where each player is and whether the map is on for them.
 */
public final class MapMpServer implements ModInitializer {
    static final Path OUT=Path.of(System.getProperty("botand.mapmp.out","mapmp"));
    private final StringBuilder log=new StringBuilder();
    private int ticks;

    @Override public void onInitialize(){
        if(!Boolean.getBoolean("botand.mapmp.server"))return;
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }
    private void note(String s){
        log.append(s).append('\n');System.out.println("[mapmp] "+s);
        try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("server.txt"),log);}catch(Exception ignored){}
    }
    /** Noted on the first tick, after the mod's own start (MapGate reads the definition when the server has started,
     *  and its listener can come after this one). */
    private void started(MinecraftServer server){
        var w=server.getOverworld();
        note("started: botand map="+MapGate.isBotandMap(server)+" city="+MapGate.isCity(server)+" enabled="+MapGate.enabled(w)
            +" definition="+MapGate.definition(server).map(d->d.name()+" ("+d.id()+"), "+d.loot().size()+" supply spots, places "+d.places().keySet()).orElse("none"));
        note("supply spots in use: "+LootPositions.current().size()+(LootPositions.current()==LootPositions.ALL?" (the city's)":" (the definition's)"));
        note("world spawn: "+w.getSpawnPos()+" time "+w.getTimeOfDay()+" daylight cycle "+server.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE));
    }
    private void tick(MinecraftServer server){
        ticks++;
        if(ticks==1)started(server);
        if(ticks%200!=0||ticks>2400)return;
        var w=server.getOverworld();
        StringBuilder s=new StringBuilder("tick "+ticks+":");
        for(var e:w.iterateEntities()){
            if(e instanceof MerchantEntity m)s.append(" merchant[").append(m.seller?"seller":"buyer").append(" ").append(fmt(m.getPos())).append(" yaw ").append(Math.round(m.getYaw())).append("]");
            if(e instanceof FieldLootEntity l)s.append(" supply[").append(l.slot()).append(" ").append(fmt(l.getPos())).append("]");
        }
        for(var p:server.getPlayerManager().getPlayerList())s.append(" player[").append(p.getName().getString()).append(" ").append(fmt(p.getPos()))
            .append(" map=").append(MapGate.enabled(p.getWorld())).append(" hp=").append(Math.round(p.getMaxHealth())).append(" mode=").append(p.interactionManager.getGameMode().getName()).append("]");
        note(s.toString());
        if(ticks==2400)note("done");
    }
    private static String fmt(net.minecraft.util.math.Vec3d v){return String.format("%.1f,%.1f,%.1f",v.x,v.y,v.z);}
}
