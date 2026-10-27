package dev.sangwoo.celestialfall.terrain;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;

/** Server-authoritative, reversible craters. No explosions, drops or chunk forcing. */
public final class TerrainRestoration {
    public static final RecoveryBlock EMPTY=new RecoveryBlock();
    public static final RecoveryBlock.Anchor ANCHOR=new RecoveryBlock.Anchor();
    public static BlockEntityType<RecoveryAnchor> ANCHOR_ENTITY;
    private static final Map<World,Set<RecoveryAnchor>> ANCHORS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<Job> JOBS=new ArrayList<>();
    private static int editing;
    private static final int FLAGS=Block.NOTIFY_LISTENERS|Block.FORCE_STATE|Block.SKIP_DROPS;
    public static final int BLOCK_BUDGET=1024, MAX_BLOCKS=65536;
    private record Column(int x,int z,int bottom,int top,double distance) {}
    private static final class Job {
        final ServerWorld world;final ArrayDeque<Column> columns;final long heldUntil;
        int removed;
        Job(ServerWorld w,List<Column> c,long hold){world=w;columns=new ArrayDeque<>(c);heldUntil=hold;}
    }
    private TerrainRestoration() {}
    public static void initialize() {
        Registry.register(Registries.BLOCK,CelestialFall.id("recovering_terrain"),EMPTY);
        Registry.register(Registries.BLOCK,CelestialFall.id("terrain_archive"),ANCHOR);
        ANCHOR_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,CelestialFall.id("terrain_archive"),
            FabricBlockEntityTypeBuilder.create(RecoveryAnchor::new,ANCHOR).build());
        ServerTickEvents.END_SERVER_TICK.register(TerrainRestoration::tick);
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)-> {
            for(var be:chunk.getBlockEntities().values())if(be instanceof RecoveryAnchor a) {
                track(a);boolean change=false;
                // A previously unloaded crater gets its visible one-second return when loaded again.
                for(var c:a.cells.values())if(c.solidAt<=world.getTime()) {c.solidAt=world.getTime()+RestorationTiming.FADE;change=true;}
                // The chunk-load future is not complete here. Never call World.markDirty/updateListeners
                // from this callback: that would synchronously wait on this same chunk.
                if(change){a.revision++;chunk.setNeedsSaving(true);}
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)-> {
            for(var be:chunk.getBlockEntities().values())if(be instanceof RecoveryAnchor a)untrack(a);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{JOBS.clear();ANCHORS.keySet().removeIf(w->!w.isClient);});
    }
    public static boolean editing() { return editing>0; }
    public static void track(RecoveryAnchor a) {
        if(a.getWorld()!=null)ANCHORS.computeIfAbsent(a.getWorld(),w->java.util.concurrent.ConcurrentHashMap.newKeySet()).add(a);
    }
    public static void untrack(RecoveryAnchor a) {
        var set=ANCHORS.get(a.getWorld());if(set!=null)set.remove(a);
    }
    public static void clearClient(World world) { if(world!=null&&world.isClient)ANCHORS.remove(world); }
    public static RecoveryAnchor.Cell original(World world,BlockPos pos) {
        var set=ANCHORS.get(world);if(set==null)return null;
        for(var a:set)if((a.getPos().getX()>>4)==(pos.getX()>>4)&&(a.getPos().getZ()>>4)==(pos.getZ()>>4)) {
            var c=a.cells.get(pos);if(c!=null)return c;
        }
        return null;
    }
    /** The damage / visual radius is unmodified; terrain has its own mobile-friendly cap. */
    public static void crater(ServerWorld world,Vec3d center,double radius,long heldUntil){crater(world,center,radius,heldUntil,0);}
    public static void atomicCrater(ServerWorld world,Vec3d center,double radius,long heldUntil){crater(world,center,radius,heldUntil,2.5);}
    private static void crater(ServerWorld world,Vec3d center,double radius,long heldUntil,double preserveCenter) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(world)||!CelestialFall.CONFIG.terrainDamage||JOBS.size()>=8||!Double.isFinite(radius))return;
        double r=Math.max(1,Math.min(radius,CelestialFall.CONFIG.terrainRadiusLimit));
        int base=MathHelper.floor(center.y-.1),reach=(int)Math.ceil(r);
        var list=new ArrayList<Column>();
        for(int x=MathHelper.floor(center.x)-reach;x<=MathHelper.floor(center.x)+reach;x++)
            for(int z=MathHelper.floor(center.z)-reach;z<=MathHelper.floor(center.z)+reach;z++) {
                double d=MathHelper.square(x+.5-center.x)+MathHelper.square(z+.5-center.z);
                if(d>r*r||d<preserveCenter*preserveCenter)continue;
                int depth=RestorationTiming.depth(d,r,CelestialFall.CONFIG.terrainDepth);
                list.add(new Column(x,z,Math.max(world.getBottomY()+1,base-depth+1),
                    Math.min(world.getTopY()-1,base+CelestialFall.CONFIG.terrainAboveGround),d));
            }
        list.sort(Comparator.comparingDouble(Column::distance));JOBS.add(new Job(world,list,heldUntil));
    }
    public static void tick(MinecraftServer server) {
        // Restore before scheduling more destruction, so overlapping techniques refresh existing snapshots.
        for(ServerWorld world:server.getWorlds())tickRestoration(world);
        int remaining=BLOCK_BUDGET,scans=16384;
        var it=JOBS.iterator();
        while(it.hasNext()&&remaining>0&&scans>0) {
            Job j=it.next();if(!dev.sangwoo.celestialfall.map.MapGate.enabled(j.world)){it.remove();continue;}Set<RecoveryAnchor> changed=new HashSet<>();
            while(!j.columns.isEmpty()&&remaining>0&&scans>0&&j.removed<MAX_BLOCKS) {
                Column c=j.columns.remove();
                WorldChunk chunk=j.world.getChunkManager().getWorldChunk(c.x>>4,c.z>>4);
                if(chunk==null)continue;
                RecoveryAnchor archive=null;
                for(var be:chunk.getBlockEntities().values())if(be instanceof RecoveryAnchor a){archive=a;break;}
                // Complete whole columns in one tick: no staggered floor pieces below a player's feet.
                for(int y=c.bottom;y<=c.top;y++) {
                    scans--;BlockPos p=new BlockPos(c.x,y,c.z);
                    if(!j.world.getWorldBorder().contains(p))continue;
                    BlockState state=j.world.getBlockState(p);
                    long due=RestorationTiming.solidAt(j.world.getTime(),j.heldUntil);
                    if(state.getBlock() instanceof RecoveryBlock) {
                        if(archive!=null) {var old=archive.cells.get(p);if(old!=null){old.solidAt=Math.max(old.solidAt,due);changed.add(archive);}}
                        continue;
                    }
                    if(!eligible(j.world,p,state)||j.removed>=MAX_BLOCKS)continue;
                    var be=j.world.getBlockEntity(p);
                    NbtCompound data=be==null?null:be.createNbtWithIdentifyingData();
                    var saved=new RecoveryAnchor.Cell(p,state,data,due);
                    editing++;
                    try {
                        // Detach the old inventory BEFORE onStateReplaced, which otherwise drops its contents.
                        if(be!=null)j.world.removeBlockEntity(p);
                        if(archive==null) {
                            j.world.setBlockState(p,ANCHOR.getDefaultState(),FLAGS);
                            archive=(RecoveryAnchor)j.world.getBlockEntity(p);
                            if(archive==null)throw new IllegalStateException("Missing terrain archive at "+p);
                        } else j.world.setBlockState(p,EMPTY.getDefaultState(),FLAGS);
                        archive.cells.put(p,saved);changed.add(archive);remaining--;j.removed++;
                    } finally {editing--;}
                }
            }
            for(var a:changed)a.changed();
            if(j.columns.isEmpty()||j.removed>=MAX_BLOCKS)it.remove();
        }
    }
    private static boolean eligible(ServerWorld w,BlockPos p,BlockState s) {
        return !s.isAir()&&s.getHardness(w,p)>=0&&s.getFluidState().isEmpty()
            &&!s.isOf(Blocks.NETHER_PORTAL)&&!s.isOf(Blocks.END_PORTAL)&&!s.isOf(Blocks.MOVING_PISTON)
            &&!s.isOf(Blocks.PISTON_HEAD);
    }
    public static void tickRestoration(ServerWorld world) {
        var set=ANCHORS.get(world);if(set==null)return;
        int budget=BLOCK_BUDGET*2;
        for(var a:List.copyOf(set)) {
            if(a.isRemoved()||a.cells.isEmpty())continue;
            var chunk=world.getChunkManager().getWorldChunk(a.getPos().getX()>>4,a.getPos().getZ()>>4);
            if(chunk==null)continue;
            long now=world.getTime();List<RecoveryAnchor.Cell> due=new ArrayList<>();
            for(var c:a.cells.values())if(c.solidAt<=now&&!c.pos.equals(a.getPos()))due.add(c);
            // The archive itself is always restored last, preserving every remaining cell across saves.
            var self=a.cells.get(a.getPos());
            if(self!=null&&self.solidAt<=now&&due.size()==a.cells.size()-1)due.add(self);
            if(due.isEmpty())continue;
            // Whole chunk batch keeps floors and attached blocks coherent; at most one batch overshoot.
            var passengers=affectedPlayers(world,due);
            editing++;
            try {
                for(var c:due) {
                    world.setBlockState(c.pos,c.state,FLAGS);
                    if(c.data!=null) {
                        var be=world.getBlockEntity(c.pos);
                        if(be!=null){be.readNbt(c.data.copy());be.markDirty();world.updateListeners(c.pos,c.state,c.state,Block.NOTIFY_LISTENERS);}
                    }
                    a.cells.remove(c.pos);
                }
            } finally {editing--;}
            liftPlayers(world,passengers);
            if(!a.isRemoved())a.changed();
            budget-=due.size();if(budget<=0)break;
        }
    }
    private static Map<ServerPlayerEntity,Integer> affectedPlayers(ServerWorld world,List<RecoveryAnchor.Cell> cells) {
        Map<ServerPlayerEntity,Integer> affected=new LinkedHashMap<>();
        for(var p:world.getPlayers()) {
            if(!p.isAlive()||p.isSpectator())continue;
            Box box=p.getBoundingBox();int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
            for(var c:cells) {
                if(c.pos.getX()+1<=box.minX||c.pos.getX()>=box.maxX||c.pos.getZ()+1<=box.minZ||c.pos.getZ()>=box.maxZ)continue;
                if(c.state.getCollisionShape(world,c.pos).isEmpty())continue;
                min=Math.min(min,c.pos.getY());max=Math.max(max,c.pos.getY()+1);
            }
            if(min!=Integer.MAX_VALUE&&p.getY()<=max+.01&&box.maxY>=min-2)affected.put(p,max);
        }
        return affected;
    }
    private static void liftPlayers(ServerWorld world,Map<ServerPlayerEntity,Integer> players) {
        for(var entry:players.entrySet()) {
            var p=entry.getKey();boolean moved=false;
            for(int ring=0;ring<=8&&!moved;ring++)for(int dx=-ring;dx<=ring&&!moved;dx++)for(int dz=-ring;dz<=ring&&!moved;dz++) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
                int x=MathHelper.floor(p.getX())+dx,z=MathHelper.floor(p.getZ())+dz;
                if(world.getChunkManager().getWorldChunk(x>>4,z>>4)==null)continue;
                int top=Math.max(entry.getValue(),world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z));
                for(int y=top;y<world.getTopY()+4;y++) {
                    BlockPos feet=new BlockPos(x,y,z),floor=feet.down();
                    var ground=world.getBlockState(floor);
                    if(ground.getCollisionShape(world,floor).isEmpty()||ground.isOf(Blocks.MAGMA_BLOCK)||ground.isOf(Blocks.CACTUS)||ground.isOf(Blocks.CAMPFIRE)||ground.isOf(Blocks.SOUL_CAMPFIRE))continue;
                    Box target=p.getBoundingBox().offset(x+.5-p.getX(),y+.05-p.getY(),z+.5-p.getZ());
                    if(!world.getWorldBorder().contains(target)||!world.isSpaceEmpty(p,target)||!world.getFluidState(feet).isEmpty())continue;
                    if(p.hasVehicle())p.stopRiding();
                    p.teleport(world,x+.5,y+.05,z+.5,p.getYaw(),p.getPitch());
                    p.setVelocity(p.getVelocity().x,Math.max(0,p.getVelocity().y),p.getVelocity().z);
                    p.fallDistance=0;p.velocityModified=true;moved=true;break;
                }
            }
            if(!moved) {
                // Even an all-magma surface must not leave the player encased in restored rock.
                // There is always air above the highest block; this fallback needs no chunk load.
                int x=MathHelper.floor(p.getX()),z=MathHelper.floor(p.getZ());
                int y=Math.max(entry.getValue(),world.getTopY(Heightmap.Type.MOTION_BLOCKING,x,z));
                while(y<world.getTopY()+4&&!world.isSpaceEmpty(p,p.getBoundingBox().offset(0,y+.05-p.getY(),0)))y++;
                if(p.hasVehicle())p.stopRiding();
                p.teleport(world,p.getX(),y+.05,p.getZ(),p.getYaw(),p.getPitch());
                p.setVelocity(p.getVelocity().x,Math.max(0,p.getVelocity().y),p.getVelocity().z);
                p.fallDistance=0;p.velocityModified=true;
            }
        }
    }
}
