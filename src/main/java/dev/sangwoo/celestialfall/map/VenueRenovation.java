package dev.sangwoo.celestialfall.map;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import java.util.*;

/** One-time, resumable renovation of the two requested venues, only in loaded city chunks.
 * Player edits, containers and occupied cells are preserved. Completion survives restarts.
 */
public final class VenueRenovation {
    private static final Map<MinecraftServer,Work> WORK=new IdentityHashMap<>();
    private static GardenPatch patch,original;
    private static BlockState[] states;
    private static final Set<Long> FLOOR=new HashSet<>();
    private static final class Progress extends PersistentState {
        final BitSet pending=new BitSet();
        Progress(){pending.set(0,patch.count);markDirty();}
        static Progress read(NbtCompound n){
            var p=new Progress();
            if(n.getInt("cells")==patch.count&&n.contains("pending")){
                p.pending.clear();p.pending.or(BitSet.valueOf(n.getLongArray("pending")));
                if(p.pending.length()>patch.count)p.pending.clear(patch.count,p.pending.length());
            }
            return p;
        }
        @Override public NbtCompound writeNbt(NbtCompound n){n.putInt("cells",patch.count);n.putLongArray("pending",pending.toLongArray());return n;}
    }
    private static final class Work {
        final ServerWorld world;final Progress progress;
        int cursor,changed,preserved;
        Work(ServerWorld w){world=w;progress=w.getPersistentStateManager().getOrCreate(Progress::read,Progress::new,"botand_venues_2150");}
    }
    private VenueRenovation(){}
    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(VenueRenovation::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORK::remove);
    }
    public static boolean offer(MinecraftServer server){
        if(WORK.containsKey(server))return true;
        var w=server.getOverworld();
        if(!MapGate.enabled(w)||!MapGate.recognizeCity(server))return false;
        try{
            if(patch==null){
                var p=GardenPatch.read(VenueRenovation.class.getResourceAsStream("/data/celestialfall/map/azure_venues.bgp.gz"));
                var o=GardenPatch.read(VenueRenovation.class.getResourceAsStream("/data/celestialfall/map/azure_venues_original.bgp.gz"));
                if(p.count!=o.count||!Arrays.equals(p.palette,o.palette))throw new java.io.IOException("Venue baseline mismatch");
                for(int i=0;i<p.count;i++)if(p.x(i)!=o.x(i)||p.y(i)!=o.y(i)||p.z(i)!=o.z(i)||p.after(i)!=o.after(i))throw new java.io.IOException("Venue coordinates mismatch");
                var decoded=new BlockState[p.palette.length];
                for(int i=0;i<decoded.length;i++)decoded[i]=BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(),p.palette[i],false).blockState();
                var floor=GardenPatch.read(VenueRenovation.class.getResourceAsStream("/data/celestialfall/map/azure_garden_floor_fix.bgp.gz"));
                for(int i=0;i<floor.count;i++)FLOOR.add(BlockPos.asLong(floor.x(i),floor.y(i),floor.z(i)));
                patch=p;original=o;states=decoded;
            }
            WORK.put(server,new Work(w));return true;
        }catch(Exception e){CelestialFall.LOGGER.error("Could not load shop/cafe renovation; no changes applied",e);return false;}
    }
    public static int remaining(MinecraftServer s){var w=WORK.get(s);return w==null?-1:w.progress.pending.cardinality();}
    public static boolean restart(ServerWorld w){
        if(w!=w.getServer().getOverworld()||!MapGate.enabled(w)||!MapGate.recognizeCity(w.getServer())||!offer(w.getServer()))return false;
        var work=WORK.get(w.getServer());work.progress.pending.set(0,patch.count);work.progress.markDirty();work.cursor=0;return true;
    }
    private static void tick(MinecraftServer server){
        var w=WORK.get(server);
        if(w==null||w.progress.pending.isEmpty()||!MapGate.enabled(w.world)||MapDecoration.running(w.world))return;
        try{
            var living=w.world.getEntitiesByType(TypeFilter.instanceOf(LivingEntity.class),LivingEntity::isAlive);
            long deadline=System.nanoTime()+4_000_000L;int visited=0;
            while(visited++<256&&System.nanoTime()<deadline){
                int i=w.progress.pending.nextSetBit(w.cursor);
                if(i<0){w.cursor=0;break;}
                w.cursor=i+1;
                var pos=new BlockPos(patch.x(i),patch.y(i),patch.z(i));
                if(!w.world.isChunkLoaded(pos))continue;
                var current=w.world.getBlockState(pos);var after=states[patch.after(i)];
                // FloorRepair runs first. Defer the exact legacy inserts until that pass catches up.
                if(current.isOf(Blocks.QUARTZ_BRICKS)&&FLOOR.contains(pos.asLong())&&states[patch.before(i)].isOf(Blocks.POLISHED_ANDESITE))continue;
                if(current!=after){
                    if(w.world.getBlockEntity(pos)!=null||(current!=states[patch.before(i)]&&current!=states[original.before(i)])){w.preserved++;}
                    else {
                        // Also protect a floor directly underneath a standing character.
                        var cell=new Box(pos);
                        if(living.stream().anyMatch(e->e.getBoundingBox().expand(0.01,0.06,0.01).intersects(cell)))continue;
                        if(!w.world.setBlockState(pos,after,Block.NOTIFY_LISTENERS|Block.FORCE_STATE|Block.SKIP_DROPS))continue;
                        w.changed++;
                    }
                }
                w.progress.pending.clear(i);w.progress.markDirty();
                if(w.progress.pending.isEmpty())CelestialFall.LOGGER.info("Botand venues complete: {} changed, {} player edits preserved",w.changed,w.preserved);
            }
        }catch(Exception e){CelestialFall.LOGGER.error("Venue renovation paused; pending progress retained",e);WORK.remove(server);}
    }
}
