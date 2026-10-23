package dev.sangwoo.botandexpanse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import java.nio.file.*;
import java.util.EnumSet;

/**
 * Writes Botand Azure Garden v1.5 'Expanse' (the plan of tools/map/expanse/plan.py) into a fresh flat-ocean world on a
 * dedicated server, then saves and stops it (tools/map/expanse/run_expanse.py). As the town builder does
 * (tools/map/town/Town.java): every block of the plan goes into the world's storage as chunks generated up to their
 * features but not yet lit, before the world has loaded any of them; the server then loads every one of them and lights
 * it, sets the map's rules and spawn, saves and stops. Only with -Dbotand.expanse.plan=DIR: a normal server is left
 * alone. Not part of the mod jar.
 */
public final class Expanse implements ModInitializer {
    static final String PLAN=System.getProperty("botand.expanse.plan");
    private int phase=-1,wait;private long started;private boolean failed;
    private int x0,z0,w,d,y0,h,sea;private int[] spawn;private float angle;

    @Override public void onInitialize(){
        if(PLAN==null)return;
        ServerWorldEvents.LOAD.register((server,world)->{if(world.getRegistryKey()==World.OVERWORLD)build(server,world);});
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    static void log(String s){System.out.println("[expanse] "+s);}
    private double secs(){return (System.nanoTime()-started)/1e9;}

    private void build(MinecraftServer server,ServerWorld world){
        try{
            started=System.nanoTime();
            var dir=Path.of(PLAN);
            JsonObject plan=JsonParser.parseString(Files.readString(dir.resolve("plan.json"))).getAsJsonObject();
            x0=plan.get("x0").getAsInt();z0=plan.get("z0").getAsInt();w=plan.get("w").getAsInt();d=plan.get("d").getAsInt();
            y0=plan.get("y0").getAsInt();h=plan.get("h").getAsInt();sea=plan.get("sea").getAsInt();
            var names=plan.getAsJsonArray("palette");
            var palette=new BlockState[names.size()];
            for(int i=0;i<palette.length;i++){
                var id=new Identifier(names.get(i).getAsString());
                if(!Registries.BLOCK.containsId(id))throw new IllegalStateException("unknown block "+id);
                palette[i]=Registries.BLOCK.get(id).getDefaultState();
            }
            var s=plan.getAsJsonArray("spawn");
            spawn=new int[]{s.get(0).getAsInt(),s.get(1).getAsInt(),s.get(2).getAsInt()};angle=s.get(3).getAsFloat();
            byte[] volume=Files.readAllBytes(dir.resolve("volume.bin"));
            if(volume.length!=(long)w*d*h)throw new IllegalStateException("the volume holds "+volume.length+" bytes, not "+(long)w*d*h);
            log(String.format("plan read (%d x %d x %d, %d kinds) in %.1f s",w,d,h,palette.length,secs()));
            int n=write(world,volume,palette);
            log(String.format("wrote %d chunks in %.1f s",n,secs()));
            phase=0;
        }catch(Throwable t){fail(server,t);}
    }

    /**
     * The plan's chunks, the edge ones reaching past it (1000 blocks is not a whole number of chunks) as open sea. Below
     * the plan the flat world's own layers: bedrock, stone to 42, gravel to 46, sand to 48. Plains over all of it, as on
     * Azure Garden (the world generates deep ocean beyond).
     */
    private int write(ServerWorld world,byte[] volume,BlockState[] palette){
        var reg=world.getRegistryManager().get(RegistryKeys.BIOME);
        RegistryEntry<Biome> plains=reg.entryOf(BiomeKeys.PLAINS);
        var storage=world.getChunkManager().threadedAnvilChunkStorage;
        BlockState bedrock=Blocks.BEDROCK.getDefaultState(),stone=Blocks.STONE.getDefaultState(),gravel=Blocks.GRAVEL.getDefaultState(),
            sand=Blocks.SAND.getDefaultState(),water=Blocks.WATER.getDefaultState();
        int bottom=world.getBottomY(),n=0;
        var heightmaps=EnumSet.of(Heightmap.Type.WORLD_SURFACE_WG,Heightmap.Type.OCEAN_FLOOR_WG,Heightmap.Type.WORLD_SURFACE,Heightmap.Type.OCEAN_FLOOR,
            Heightmap.Type.MOTION_BLOCKING,Heightmap.Type.MOTION_BLOCKING_NO_LEAVES);
        for(int cx=x0>>4;cx<=(x0+w-1)>>4;cx++)for(int cz=z0>>4;cz<=(z0+d-1)>>4;cz++){
            var cp=new ChunkPos(cx,cz);
            var chunk=new ProtoChunk(cp,UpgradeData.NO_UPGRADE_DATA,world,reg,null);
            var sections=chunk.getSectionArray();
            for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++){
                int x=(cx<<4)+lx,z=(cz<<4)+lz;
                for(int y=bottom;y<y0;y++)sections[world.getSectionIndex(y)].setBlockState(lx,y&15,lz,y==bottom?bedrock:y<=42?stone:y<=46?gravel:sand,false);
                if(x<x0||x>=x0+w||z<z0||z>=z0+d){
                    for(int y=y0;y<=sea;y++)sections[world.getSectionIndex(y)].setBlockState(lx,y&15,lz,water,false);
                    continue;
                }
                int base=((z-z0)*w+(x-x0))*h;
                for(int i=0;i<h;i++){
                    int id=volume[base+i]&255;if(id==0)continue;
                    int y=y0+i;
                    sections[world.getSectionIndex(y)].setBlockState(lx,y&15,lz,palette[id],false);
                }
            }
            chunk.populateBiomes((bx,by,bz,noise)->plains,null);
            chunk.setStatus(ChunkStatus.FEATURES);
            Heightmap.populateHeightmaps(chunk,heightmaps);
            storage.setNbt(cp,ChunkSerializer.serialize(world,chunk));
            if(++n%1024==0)storage.completeAll();
        }
        storage.completeAll();
        return n;
    }

    private void fail(MinecraftServer server,Throwable t){
        t.printStackTrace();log("FAILED "+t);failed=true;
        try{Files.writeString(Path.of("expanse_error.txt"),String.valueOf(t));}catch(Exception ignored){}
        phase=3;server.stop(false);
    }

    private void tick(MinecraftServer server){
        if(failed||phase<0)return;
        var world=server.getOverworld();
        try{
            switch(phase){
                case 0->{
                    int n=0;
                    for(int cx=x0>>4;cx<=(x0+w-1)>>4;cx++)for(int cz=z0>>4;cz<=(z0+d-1)>>4;cz++){world.setChunkForced(cx,cz,true);n++;}
                    log("forced "+n+" chunks");phase=1;wait=0;
                }
                case 1->{
                    // every chunk of the plan loaded and lit
                    if(++wait%20!=0)return;
                    int pending=0;
                    for(int cx=x0>>4;cx<=(x0+w-1)>>4;cx++)for(int cz=z0>>4;cz<=(z0+d-1)>>4;cz++)
                        if(!world.getChunkManager().isTickingFutureReady(ChunkPos.toLong(cx,cz)))pending++;
                    boolean light=world.getChunkManager().getLightingProvider().hasUpdates();
                    if(wait%200==0)log("waiting: "+pending+" chunks not ready, light "+(light?"busy":"settled")+", "+wait+" ticks");
                    if(pending==0&&!light&&wait>=100||wait>72000){log("all chunks ready after "+wait+" ticks ("+(int)secs()+" s)");phase=2;}
                }
                case 2->{
                    rules(server,world);
                    for(int cx=x0>>4;cx<=(x0+w-1)>>4;cx++)for(int cz=z0>>4;cz<=(z0+d-1)>>4;cz++)world.setChunkForced(cx,cz,false);
                    server.saveAll(true,true,true);
                    log("saved; stopping after "+(int)secs()+" s");
                    phase=3;server.stop(false);
                }
                default->{}
            }
        }catch(Throwable t){fail(server,t);}
    }

    /** Azure Garden's rules: always morning and clear, no mobs, nothing burns or grows, inventories kept. */
    private void rules(MinecraftServer s,ServerWorld world){
        var r=s.getGameRules();
        r.get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);
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
        world.setWeather(999999,0,false,false);
        world.setTimeOfDay(1000);
        world.setSpawnPos(new BlockPos(spawn[0],spawn[1],spawn[2]),angle);
        log("spawn at "+spawn[0]+" "+spawn[1]+" "+spawn[2]+" facing "+angle);
    }
}
