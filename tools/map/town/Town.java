package dev.sangwoo.botandtown;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.registry.RegistryKey;
import java.nio.file.*;
import java.util.*;

/**
 * Builds Botand's seaside town into a fresh world on a dedicated server, then saves and stops it (tools/map/town/
 * run_town.py). Only with -Dbotand.town.build=true: a normal server is left alone. Not part of the mod jar.
 */
public final class Town implements ModInitializer {
    static final boolean BUILD=Boolean.getBoolean("botand.town.build");
    static final long SEED=Long.getLong("botand.town.seed",20261020L);
    private int phase,wait;private long started;
    private final List<long[]> forced=new ArrayList<>();
    static final StringBuilder LOG=new StringBuilder();

    @Override public void onInitialize(){if(BUILD)ServerTickEvents.END_SERVER_TICK.register(this::tick);}

    static void log(String s){System.out.println("[town] "+s);LOG.append(s).append('\n');}

    private void tick(MinecraftServer server){
        var w=server.getOverworld();
        try{
            switch(phase){
                case 0->{
                    started=System.nanoTime();
                    for(int cx=Plan.X0>>4;cx<=(Plan.X0+Plan.W-1)>>4;cx++)for(int cz=Plan.Z0>>4;cz<=(Plan.Z0+Plan.D-1)>>4;cz++){w.setChunkForced(cx,cz,true);forced.add(new long[]{cx,cz});}
                    log("forced "+forced.size()+" chunks");
                    build(server,w);
                    phase=1;wait=0;
                }
                case 1->{
                    // give the light engine time to light everything that changed
                    if(++wait>=60&&!w.getChunkManager().getLightingProvider().hasUpdates()||wait>6000){log("lighting settled after "+wait+" ticks");phase=2;}
                }
                case 2->{
                    for(long[] f:forced)w.setChunkForced((int)f[0],(int)f[1],false);
                    server.saveAll(true,true,true);
                    Files.writeString(server.getSavePath(WorldSavePath.ROOT).resolve("town_build.log"),LOG.toString());
                    log("saved; stopping after "+(System.nanoTime()-started)/1_000_000_000+" s");
                    phase=3;server.stop(false);
                }
                default->{}
            }
        }catch(Throwable t){
            t.printStackTrace();log("FAILED "+t);
            try{Files.writeString(Path.of("town_error.txt"),LOG+"\n"+t);}catch(Exception ignored){}
            phase=3;server.stop(false);
        }
    }

    private void build(MinecraftServer server,ServerWorld w)throws Exception{
        var canvas=new Canvas(w);
        long t0=System.nanoTime();
        var terrain=new Terrain(canvas,SEED);terrain.build();
        log(String.format("terrain: %,d blocks in %.1f s",canvas.placed,(System.nanoTime()-t0)/1e9));
        var world=new Townscape(canvas,terrain,new Random(SEED));
        world.build();
        log(String.format("town: %,d blocks in all, %.1f s",canvas.placed,(System.nanoTime()-t0)/1e9));
        int shaped=canvas.finish();
        log("shaped "+shaped+" connecting blocks");
        biomes(w,terrain);
        rules(server,w,world);
        world.definition(server.getSavePath(WorldSavePath.DATAPACKS).resolve("botand_seaside"));
        log(String.format("done building in %.1f s",(System.nanoTime()-t0)/1e9));
    }

    /** Green forest on the land, beach along the shore, ocean and river in the water. */
    private void biomes(ServerWorld w,Terrain t){
        var reg=w.getRegistryManager().get(RegistryKeys.BIOME);
        RegistryEntry<Biome> land=reg.entryOf(BiomeKeys.FOREST),beach=reg.entryOf(BiomeKeys.BEACH),sea=reg.entryOf(BiomeKeys.OCEAN),river=reg.entryOf(BiomeKeys.RIVER);
        var sampler=w.getChunkManager().getNoiseConfig().getMultiNoiseSampler();
        int chunks=0;
        for(int cx=Plan.X0>>4;cx<=(Plan.X0+Plan.W-1)>>4;cx++)for(int cz=Plan.Z0>>4;cz<=(Plan.Z0+Plan.D-1)>>4;cz++){
            var chunk=w.getChunk(cx,cz);
            chunk.populateBiomes((bx,by,bz,noise)->{
                int x=(bx<<2)+2,z=(bz<<2)+2;
                if(!Canvas.inPlan(x,z))return sea;
                byte k=t.kind[Canvas.idx(x,z)];
                if(k==Terrain.RIVER)return z>=Plan.SEAWARD_Z?sea:river;
                if(k==Terrain.SEA)return sea;
                return z>=Plan.COAST_Z0-2&&z<=Plan.SEAWARD_Z+30?beach:land;
            },sampler);
            chunk.setNeedsSaving(true);chunks++;
        }
        log("biomes set in "+chunks+" chunks");
    }

    private void rules(MinecraftServer s,ServerWorld w,Townscape town){
        var r=s.getGameRules();
        r.get(GameRules.DO_DAYLIGHT_CYCLE).set(true,s);
        r.get(GameRules.DO_WEATHER_CYCLE).set(false,s);
        r.get(GameRules.DO_MOB_SPAWNING).set(false,s);
        r.get(GameRules.DO_MOB_GRIEFING).set(false,s);
        r.get(GameRules.DO_FIRE_TICK).set(false,s);
        r.get(GameRules.KEEP_INVENTORY).set(true,s);
        r.get(GameRules.SPAWN_RADIUS).set(0,s);
        r.get(GameRules.RANDOM_TICK_SPEED).set(0,s);
        r.get(GameRules.DO_INSOMNIA).set(false,s);
        r.get(GameRules.DO_PATROL_SPAWNING).set(false,s);
        r.get(GameRules.DO_TRADER_SPAWNING).set(false,s);
        r.get(GameRules.COMMAND_BLOCK_OUTPUT).set(false,s);
        w.setWeather(999999,0,false,false);
        w.setTimeOfDay(1000);
        var spawn=town.spawn();
        w.setSpawnPos(new BlockPos(spawn[0],spawn[1],spawn[2]),spawn[3]);
        log("spawn at "+Arrays.toString(spawn));
    }
}
