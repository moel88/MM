package dev.sangwoo.celestialfall.map;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;

/** Repairs only the exact quartz inserts shipped in 2.14.0, as chunks load.
 * No forced chunk loading, terrain scan, inventory edit or full map renovation. */
public final class FloorRepair {
    private static final Map<MinecraftServer,Work> WORK=new IdentityHashMap<>();
    private static GardenPatch patch;
    private static final class Work {
        final ServerWorld world;final BitSet pending;
        int ticks,changed;
        Work(ServerWorld w){world=w;pending=new BitSet(patch.count);pending.set(0,patch.count);}
    }
    private FloorRepair(){}
    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(FloorRepair::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORK::remove);
    }
    public static boolean offer(MinecraftServer server){
        if(WORK.containsKey(server))return true;
        var world=server.getOverworld();
        if(!MapGate.enabled(world)||!MapGate.recognizeCity(server))return false;
        try{
            if(patch==null){
                var p=GardenPatch.read(FloorRepair.class.getResourceAsStream("/data/celestialfall/map/azure_garden_floor_fix.bgp.gz"));
                for(int i=0;i<p.count;i++)if(p.y(i)!=80||!p.palette[p.before(i)].equals("minecraft:quartz_bricks")||!p.palette[p.after(i)].equals("minecraft:polished_andesite"))throw new java.io.IOException("Unexpected floor repair state");
                patch=p;
            }
            WORK.put(server,new Work(world));return true;
        }catch(Exception e){CelestialFall.LOGGER.error("Could not load pavement repair; map unchanged",e);return false;}
    }
    public static boolean restart(ServerWorld world){
        if(world!=world.getServer().getOverworld()||!MapGate.enabled(world)||!MapGate.recognizeCity(world.getServer()))return false;
        WORK.remove(world.getServer());return offer(world.getServer());
    }
    public static int remaining(MinecraftServer server){var w=WORK.get(server);return w==null?-1:w.pending.cardinality();}
    private static void tick(MinecraftServer server){
        var w=WORK.get(server);if(w==null||w.pending.isEmpty()||!MapGate.enabled(w.world)||++w.ticks%4!=0)return;
        int budget=128;var pos=new BlockPos.Mutable();
        for(int i=w.pending.nextSetBit(0);i>=0&&budget>0;i=w.pending.nextSetBit(i+1)){
            pos.set(patch.x(i),80,patch.z(i));
            if(!w.world.isChunkLoaded(pos))continue;
            budget--;
            var current=w.world.getBlockState(pos);
            if(current.isOf(Blocks.QUARTZ_BRICKS)&&w.world.getBlockEntity(pos)==null){
                if(!w.world.setBlockState(pos,Blocks.POLISHED_ANDESITE.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE|Block.SKIP_DROPS))continue;
                w.changed++;
            }
            w.pending.clear(i);
        }
        if(w.pending.isEmpty())CelestialFall.LOGGER.info("Botand pavement repair complete: {} inserts replaced",w.changed);
    }
}
