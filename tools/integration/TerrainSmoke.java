package dev.sangwoo.botandverify;

import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.terrain.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
import static dev.sangwoo.botandverify.CombatSmoke.*;

/** Live world mutations, containers, collision safety, disk reload and skill impact hooks. */
public final class TerrainSmoke {
    static void time(ServerWorld w,long t){((net.minecraft.world.level.ServerWorldProperties)w.getLevelProperties()).setTime(t);}
    static RecoveryAnchor archive(ServerWorld w,BlockPos p) {
        for(var be:w.getChunk(p.getX()>>4,p.getZ()>>4).getBlockEntities().values())if(be instanceof RecoveryAnchor a&&a.cells.containsKey(p))return a;
        throw new AssertionError("Archive missing at "+p);
    }
    static void floor(ServerWorld w,int x,int z,int r) {
        for(int xx=x-r;xx<=x+r;xx++)for(int zz=z-r;zz<=z+r;zz++)for(int y=95;y<=100;y++)
            w.setBlockState(new BlockPos(xx,y,zz),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
    }
    static int count(ServerWorld w,int x0,int x1,int z0,int z1) {
        int total=0;
        for(int x=x0>>4;x<=x1>>4;x++)for(int z=z0>>4;z<=z1>>4;z++) {
            var chunk=w.getChunkManager().getWorldChunk(x,z);if(chunk==null)continue;
            for(var be:chunk.getBlockEntities().values())if(be instanceof RecoveryAnchor a)total+=a.cells.size();
        }
        return total;
    }
    public static void verifyRestart(MinecraftServer server,Map<String,Object> checks)throws Exception {
        var w=server.getOverworld();long start=w.getTime();
        Path checkpoint=Path.of("../build/botand-motion/terrain-restart.json");
        BlockPos persisted=new BlockPos(192,100,192);w.getChunk(persisted);
            if(Files.exists(checkpoint)) {
                require(w.getBlockState(persisted).getBlock() instanceof RecoveryBlock,"Real server restart lost pending placeholder");
                var a=archive(w,persisted);var saved=a.cells.get(persisted);
                require(saved.state.isOf(Blocks.CHEST)&&saved.data!=null,"Restart lost original chest snapshot");
                long due=a.cells.values().stream().mapToLong(c->c.solidAt).max().orElseThrow();
                time(w,due);TerrainRestoration.tickRestoration(w);
                require(((ChestBlockEntity)w.getBlockEntity(persisted)).getStack(7).getCount()==37,"Restart lost chest contents");
                checks.put("terrain_real_server_restart_restores_original_inventory",true);
                time(w,start);
            }
    }
    public static void run(MinecraftServer server,Map<String,Object> checks)throws Exception {
        var w=server.getOverworld();long start=w.getTime();var cfg=CelestialFall.CONFIG;
        boolean enabled=cfg.terrainDamage;int depth=cfg.terrainDepth,above=cfg.terrainAboveGround;double cap=cfg.terrainRadiusLimit;
        Path checkpoint=Path.of("../build/botand-motion/terrain-restart.json");
        var p=player(server,"RecoveryTester",128.5,128.5);
        try {
            cfg.terrainDamage=true;cfg.terrainRadiusLimit=4;cfg.terrainDepth=4;cfg.terrainAboveGround=2;
            BlockPos persisted=new BlockPos(192,100,192);w.getChunk(persisted);
            floor(w,128,128,6);BlockPos chest=new BlockPos(128,100,128);
            w.setBlockState(chest,Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,Direction.EAST));
            var original=(ChestBlockEntity)w.getBlockEntity(chest);original.setStack(3,new ItemStack(Items.DIAMOND,23));original.setCustomName(net.minecraft.text.Text.literal("Preserved chest"));original.markDirty();
            BlockPos bedrock=new BlockPos(129,100,128);w.setBlockState(bedrock,Blocks.BEDROCK.getDefaultState());
            int drops=w.getEntitiesByClass(ItemEntity.class,new Box(chest).expand(10),e->true).size();
            TerrainRestoration.crater(w,new Vec3d(128.5,101,128.5),4,start);TerrainRestoration.tick(server);
            var a=archive(w,chest);require(a.cells.get(chest).solidAt==start+220,"Wrong ten-plus-one-second deadline");
            require(w.getBlockState(chest).getBlock() instanceof RecoveryBlock,"Original block not actually removed");
            require(w.getBlockState(chest).getCollisionShape(w,chest).isEmpty(),"Crater has an invisible solid floor");
            require(w.getBlockState(bedrock).isOf(Blocks.BEDROCK),"Bedrock broken");
            require(w.getEntitiesByClass(ItemEntity.class,new Box(chest).expand(10),e->true).size()==drops,"Inventory duplicated into dropped items");
            require(!w.setBlockState(chest,Blocks.DIAMOND_BLOCK.getDefaultState()),"Reserved cell accepted foreign placement");
            require(!a.toInitialChunkDataNbt().toString().contains("diamond")&&!a.toUpdatePacket().getNbt().contains("Cells"),"Private inventory leaked to ghost packet");
            var raw=a.createNbt();var restored=new RecoveryAnchor(a.getPos(),a.getCachedState());restored.readNbt(raw);
            require(restored.cells.size()==a.cells.size()&&restored.cells.get(chest).data.equals(a.cells.get(chest).data),"Archive NBT round trip corrupted block entity data");
            var clientCopy=new RecoveryAnchor(a.getPos(),a.getCachedState());clientCopy.readNbt(a.toInitialChunkDataNbt());
            require(clientCopy.cells.get(chest).state.equals(a.cells.get(chest).state)&&clientCopy.cells.get(chest).data==null,"Network ghost lost state or exposed inventory");
            checks.put("terrain_real_empty_collision_protected_bedrock_no_drops_and_private_ghost_packet",true);
            time(w,start+199);TerrainRestoration.tickRestoration(w);require(w.getBlockState(chest).getBlock() instanceof RecoveryBlock,"Restored before 10 seconds");
            time(w,start+210);TerrainRestoration.tickRestoration(w);require(w.getBlockState(chest).getBlock() instanceof RecoveryBlock,"Solid before 1-second fade finished");
            w.onPlayerConnected(p);p.setPosition(128.5,97,128.5);p.fallDistance=50;p.setVelocity(0,-1,0);
            time(w,start+220);TerrainRestoration.tickRestoration(w);
            require(w.getBlockState(chest).isOf(Blocks.CHEST)&&w.getBlockState(chest).get(ChestBlock.FACING)==Direction.EAST,"Original state/orientation not restored");
            var back=(ChestBlockEntity)w.getBlockEntity(chest);
            require(back.getStack(3).getCount()==23&&back.getStack(3).isOf(Items.DIAMOND)&&back.getCustomName().getString().equals("Preserved chest"),"Restored contents or custom name mismatch");
            require(p.getY()>=101&&p.fallDistance==0&&w.isSpaceEmpty(p,p.getBoundingBox())&&p.getVelocity().y>=0,"Player not safely lifted above restored terrain: "+p.getPos());
            checks.put("terrain_200_tick_wait_20_tick_fade_chest_contents_orientation_and_safe_player_lift",true);
            // Overlapping casts retain the FIRST snapshot, and move only the recovery deadline.
            time(w,start+300);TerrainRestoration.crater(w,new Vec3d(128.5,101,128.5),4,start+300);TerrainRestoration.tick(server);
            var first=archive(w,chest).cells.get(chest).data.copy();
            time(w,start+400);TerrainRestoration.crater(w,new Vec3d(128.5,101,128.5),4,start+400);TerrainRestoration.tick(server);
            require(archive(w,chest).cells.get(chest).solidAt==start+620&&archive(w,chest).cells.get(chest).data.equals(first),"Overlap overwrote original snapshot");
            time(w,start+520);TerrainRestoration.tickRestoration(w);require(w.getBlockState(chest).getBlock() instanceof RecoveryBlock,"Overlap restored too early");
            time(w,start+620);TerrainRestoration.tickRestoration(w);require(((ChestBlockEntity)w.getBlockEntity(chest)).getStack(3).getCount()==23,"Overlap damaged inventory");
            checks.put("terrain_overlap_preserves_first_snapshot_and_extends_countdown",true);
            // No safe material nearby: still escape restored blocks instead of remaining suffocated.
            time(w,start+700);floor(w,176,176,10);
            for(int x=166;x<=186;x++)for(int z=166;z<=186;z++)w.setBlockState(new BlockPos(x,100,z),Blocks.MAGMA_BLOCK.getDefaultState());
            TerrainRestoration.crater(w,new Vec3d(176.5,101,176.5),4,start+700);TerrainRestoration.tick(server);
            p.setPosition(176.5,97,176.5);time(w,start+920);TerrainRestoration.tickRestoration(w);
            require(p.getY()>=101&&w.isSpaceEmpty(p,p.getBoundingBox()),"All-hazard surface left player suffocating");
            checks.put("terrain_hazardous_surface_fallback_still_prevents_suffocation",true);
            // A multi-tick crater must not exceed the configured per-tick budget by more than one column.
            time(w,start+1000);floor(w,224,128,16);cfg.terrainRadiusLimit=12;cfg.terrainDepth=6;
            TerrainRestoration.crater(w,new Vec3d(224.5,101,128.5),12,start+1000);TerrainRestoration.tick(server);
            int firstCount=count(w,208,240,112,144);require(firstCount>=1024&&firstCount<=1030,"Destruction ignored its column-bounded tick budget: "+firstCount);
            time(w,start+1001);TerrainRestoration.tick(server);int secondCount=count(w,208,240,112,144);
            require(secondCount>firstCount,"Queued crater stopped after first budget slice");
            time(w,start+1230);for(int i=0;i<10;i++)TerrainRestoration.tickRestoration(w);
            require(count(w,208,240,112,144)==0,"Budgeted crater did not fully recover");
            checks.put("terrain_large_crater_column_budget_and_complete_recovery",true);checks.put("terrain_first_tick_blocks",firstCount);checks.put("terrain_second_tick_blocks",secondCount);
            cfg.terrainRadiusLimit=4;cfg.terrainDepth=4;
            // Verify all THREE existing skill impact entrypoints schedule real terrain, at unchanged timings.
            time(w,start+1000);floor(w,144,144,6);Vec3d target=new Vec3d(144.5,101,144.5);BlockPos ground=new BlockPos(144,100,144);
            for(String kind:List.of("SpellManager","HeavenManager","AtomicManager")) {
                long base=w.getTime();Class<?> manager=Class.forName("dev.sangwoo.celestialfall."+kind),cast=Class.forName(manager.getName()+"$Cast");
                var ctor=cast.getDeclaredConstructors()[0];ctor.setAccessible(true);Object value;
                UUID id=UUID.randomUUID(),caster=UUID.randomUUID();
                if(kind.equals("SpellManager"))value=ctor.newInstance(id,w,target,base,caster,0f,0.,4.,false,false,new HashSet<UUID>());
                else if(kind.equals("HeavenManager"))value=ctor.newInstance(id,caster,w,target,target,0f,base,0.,4.,new HashSet<UUID>());
                else value=ctor.newInstance(id,caster,w,target,0f,base,4.,0.,new HashSet<UUID>());
                var activeField=manager.getDeclaredField("ACTIVE");activeField.setAccessible(true);
                @SuppressWarnings("unchecked") var list=(List<Object>)activeField.get(null);list.add(value);
                int impact=kind.equals("SpellManager")?SpellTimeline.IMPACT:kind.equals("HeavenManager")?HeavenTimeline.IMPACT:AtomicTimeline.IMPACT;
                time(w,base+impact);manager.getMethod("tick",MinecraftServer.class).invoke(null,server);TerrainRestoration.tick(server);
                require(w.getBlockState(ground).getBlock() instanceof RecoveryBlock,kind+" did not excavate on impact with entity damage disabled");
                long due=archive(w,ground).cells.get(ground).solidAt;
                require(due==base+(kind.equals("AtomicManager")?AtomicTimeline.END:impact)+220,"Wrong recovery timing for "+kind);
                time(w,due);TerrainRestoration.tickRestoration(w);require(w.getBlockState(ground).isOf(Blocks.STONE),kind+" did not restore");list.clear();
            }
            checks.put("terrain_all_three_live_skill_impact_hooks_and_atomic_end_hold",true);
            // A genuine chunk-save checkpoint: next server process must recover this inventory.
            floor(w,192,192,5);w.setBlockState(persisted,Blocks.CHEST.getDefaultState());
            ((ChestBlockEntity)w.getBlockEntity(persisted)).setStack(7,new ItemStack(Items.EMERALD,37));
            time(w,start);TerrainRestoration.crater(w,new Vec3d(192.5,101,192.5),3,start);TerrainRestoration.tick(server);
            require(archive(w,persisted).cells.get(persisted).data!=null,"No persisted snapshot");
            Files.writeString(checkpoint,"{\"checkpoint\":true}");
            checks.put("terrain_restart_checkpoint_created",true);
        } finally {
            p.discard();time(w,start);cfg.terrainDamage=enabled;cfg.terrainDepth=depth;cfg.terrainAboveGround=above;cfg.terrainRadiusLimit=cap;
        }
    }
}
