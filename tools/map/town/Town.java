package dev.sangwoo.botandtown;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import java.nio.file.*;
import java.util.*;

/**
 * Builds Botand's hourglass island (map v3.0; the seaside town before it) into a fresh world on a dedicated server,
 * then saves and stops it (tools/map/town/run_town.py). The town is built in memory as soon as the overworld exists and written into its storage as
 * generated chunks before the world loads any of them; the server then loads every one of them (lighting them and
 * shaping their fences and stairs), sets the map's rules and spawn, saves and stops. Only with
 * -Dbotand.town.build=true: a normal server is left alone. Not part of the mod jar.
 */
public final class Town implements ModInitializer {
    static final boolean BUILD=Boolean.getBoolean("botand.town.build");
    static final long SEED=Long.getLong("botand.town.seed",20261020L);
    /** The map's data pack (the mod finds the map's definition in it). */
    static final String PACK="botand_hourglass";
    private int phase=-1,wait;private long started;
    private boolean failed;
    /** What the build leaves for after the world has lit it (the model itself is let go first). */
    private Map<String,String> definition;private int[] spawn;
    static final StringBuilder LOG=new StringBuilder();

    @Override public void onInitialize(){
        if(!BUILD)return;
        ServerWorldEvents.LOAD.register((server,world)->{if(world.getRegistryKey()==World.OVERWORLD)build(server,world);});
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    static void log(String s){System.out.println("[town] "+s);LOG.append(s).append('\n');}

    private void build(MinecraftServer server,ServerWorld w){
        try{
            started=System.nanoTime();
            var canvas=new Canvas();
            log(String.format("model ready (%d x %d x %d) in %.1f s",Plan.W,Canvas.H,Plan.D,secs()));
            var terrain=new Terrain(canvas,SEED);terrain.build();
            log(String.format("terrain: %,d blocks, %.1f s",canvas.placed,secs()));
            var town=new Townscape(canvas,terrain,new Random(SEED));
            town.build();
            log(String.format("town: %,d blocks in all, %.1f s",canvas.placed,secs()));
            Chunks.write(w,canvas,biomes(w,terrain));
            log(String.format("chunks written, %.1f s",secs()));
            town.preview(server.getSavePath(WorldSavePath.ROOT).resolve("town_preview.png"));
            definition=town.definition();spawn=town.spawn();
            phase=0;
        }catch(Throwable t){fail(server,t);}
    }
    private double secs(){return (System.nanoTime()-started)/1e9;}

    private void fail(MinecraftServer server,Throwable t){
        t.printStackTrace();log("FAILED "+t);failed=true;
        try{Files.writeString(Path.of("town_error.txt"),LOG+"\n"+t);}catch(Exception ignored){}
        phase=3;server.stop(false);
    }

    private void tick(MinecraftServer server){
        if(failed||phase<0)return;
        var w=server.getOverworld();
        try{
            switch(phase){
                case 0->{
                    int n=0;
                    for(int cx=Plan.X0>>4;cx<=(Plan.X0+Plan.W-1)>>4;cx++)for(int cz=Plan.Z0>>4;cz<=(Plan.Z0+Plan.D-1)>>4;cz++){w.setChunkForced(cx,cz,true);n++;}
                    log("forced "+n+" chunks");phase=1;wait=0;
                }
                case 1->{
                    // every chunk of the plan loaded and ticking (so its blocks are shaped), and the light settled
                    if(++wait%20!=0)return;
                    int pending=0;
                    for(int cx=Plan.X0>>4;cx<=(Plan.X0+Plan.W-1)>>4;cx++)for(int cz=Plan.Z0>>4;cz<=(Plan.Z0+Plan.D-1)>>4;cz++)
                        if(!w.getChunkManager().isTickingFutureReady(ChunkPos.toLong(cx,cz)))pending++;
                    boolean light=w.getChunkManager().getLightingProvider().hasUpdates();
                    if(wait%200==0)log("waiting: "+pending+" chunks not ready, light "+(light?"busy":"settled")+", "+wait+" ticks");
                    if(pending==0&&!light&&wait>=100||wait>72000){log("all chunks ready after "+wait+" ticks ("+(int)secs()+" s)");phase=2;}
                }
                case 2->{
                    rules(server,w);
                    var pack=server.getSavePath(WorldSavePath.DATAPACKS).resolve(PACK);
                    for(var e:definition.entrySet()){var f=pack.resolve(e.getKey());Files.createDirectories(f.getParent());Files.writeString(f,e.getValue());}
                    for(int cx=Plan.X0>>4;cx<=(Plan.X0+Plan.W-1)>>4;cx++)for(int cz=Plan.Z0>>4;cz<=(Plan.Z0+Plan.D-1)>>4;cz++)w.setChunkForced(cx,cz,false);
                    server.saveAll(true,true,true);
                    Files.writeString(server.getSavePath(WorldSavePath.ROOT).resolve("town_build.log"),LOG.toString());
                    log("saved; stopping after "+(int)secs()+" s");
                    phase=3;server.stop(false);
                }
                default->{}
            }
        }catch(Throwable t){fail(server,t);}
    }

    /**
     * Biomes by zone (they tint the grass and leaves): meadow in the rift, forest in the old town and the forest, plains
     * in the capital and the old city, beach on the sandy shores, warm shallows round the coast, ocean beyond.
     */
    private Chunks.Biomes biomes(ServerWorld w,Terrain t){
        var reg=w.getRegistryManager().get(RegistryKeys.BIOME);
        RegistryEntry<Biome> forest=reg.entryOf(BiomeKeys.FOREST),plains=reg.entryOf(BiomeKeys.PLAINS),meadow=reg.entryOf(BiomeKeys.MEADOW),
            beach=reg.entryOf(BiomeKeys.BEACH),shallow=reg.entryOf(BiomeKeys.LUKEWARM_OCEAN),sea=reg.entryOf(BiomeKeys.OCEAN),snowy=reg.entryOf(BiomeKeys.SNOWY_SLOPES);
        return (x,y,z)->{
            if(!Canvas.inPlan(x,z))return sea;
            byte k=t.kind[Canvas.idx(x,z)];
            if(k==Terrain.SEA)return shallow;
            if(k==Terrain.BEACH)return beach;
            if(Plan.zone(x,z)==Plan.TOWN&&Math.hypot(x-Plan.MOUNT_X,z-Plan.MOUNT_Z)<Plan.MOUNT_R*0.55&&y>Plan.mountLevel(0.46))return snowy;
            return switch(Plan.zone(x,z)){case Plan.RIFT_ZONE->meadow;case Plan.CAPITAL,Plan.CYBER->plains;default->forest;};
        };
    }

    private void rules(MinecraftServer s,ServerWorld w){
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
        w.setSpawnPos(new BlockPos(spawn[0],spawn[1],spawn[2]),spawn[3]);
        log("spawn at "+Arrays.toString(spawn));
    }
}
