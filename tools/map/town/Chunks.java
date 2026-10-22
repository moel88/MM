package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import java.util.EnumSet;

/**
 * Writes the finished town into the world's region storage as chunks generated up to their features but not yet lit,
 * before the world has loaded any of them. When the world then loads them it lights them from scratch and, once their
 * neighbours are loaded, shapes the marked fences, panes, walls and stairs from their neighbours, the same steps it
 * takes for chunks it generates itself. Below the model the flat world's own layers are written unchanged.
 */
final class Chunks {
    private Chunks(){}
    interface Biomes{RegistryEntry<Biome> at(int x,int y,int z);}

    static int write(ServerWorld w,Canvas c,Biomes biomes){
        var reg=w.getRegistryManager().get(RegistryKeys.BIOME);
        var storage=w.getChunkManager().threadedAnvilChunkStorage;
        BlockState bedrock=Blocks.BEDROCK.getDefaultState(),stone=Blocks.STONE.getDefaultState(),gravel=Blocks.GRAVEL.getDefaultState(),sand=Blocks.SAND.getDefaultState(),
            water=Blocks.WATER.getDefaultState();
        int bottom=w.getBottomY(),n=0,entities=0,marked=0;
        var pos=new BlockPos.Mutable();
        var heightmaps=EnumSet.of(Heightmap.Type.WORLD_SURFACE_WG,Heightmap.Type.OCEAN_FLOOR_WG,Heightmap.Type.WORLD_SURFACE,Heightmap.Type.OCEAN_FLOOR,
            Heightmap.Type.MOTION_BLOCKING,Heightmap.Type.MOTION_BLOCKING_NO_LEAVES);
        for(int cx=Plan.X0>>4;cx<=(Plan.X0+Plan.W-1)>>4;cx++)for(int cz=Plan.Z0>>4;cz<=(Plan.Z0+Plan.D-1)>>4;cz++){
            var cp=new ChunkPos(cx,cz);
            var chunk=new ProtoChunk(cp,UpgradeData.NO_UPGRADE_DATA,w,reg,null);
            var sections=chunk.getSectionArray();
            for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++){
                int x=(cx<<4)+lx,z=(cz<<4)+lz;
                // the flat world's layers under the sea: bedrock, stone, gravel, sand
                for(int y=bottom;y<=Plan.FLOOR;y++)sections[w.getSectionIndex(y)].setBlockState(lx,y&15,lz,y==bottom?bedrock:y<=46?stone:y<=48?gravel:sand,false);
                // the edge chunks reach past the plan (400 blocks is not a whole number of chunks): open sea there
                if(!Canvas.inPlan(x,z)){for(int y=Canvas.Y0;y<=Plan.SEA;y++)sections[w.getSectionIndex(y)].setBlockState(lx,y&15,lz,water,false);continue;}
                for(int y=Canvas.Y0;y<=Canvas.YMAX;y++){
                    char id=c.raw(x,y,z);if(id==Canvas.AIR)continue;
                    BlockState s=Canvas.state(id);Block b=s.getBlock();
                    sections[w.getSectionIndex(y)].setBlockState(lx,y&15,lz,s,false);
                    if(b instanceof FenceBlock||b instanceof PaneBlock||b instanceof WallBlock||b instanceof StairsBlock||b instanceof FenceGateBlock||b instanceof dev.sangwoo.celestialfall.town.TownBlocks.Window){
                        chunk.markBlockForPostProcessing(pos.set(x,y,z));marked++;
                    }
                    if(s.hasBlockEntity()&&b instanceof BlockEntityProvider provider){
                        var bp=new BlockPos(x,y,z);
                        BlockEntity be=provider.createBlockEntity(bp,s);
                        if(be!=null){var settings=c.entities.get(bp.asLong());if(settings!=null)settings.accept(be);chunk.setBlockEntity(be);entities++;}
                    }
                }
            }
            chunk.populateBiomes((bx,by,bz,noise)->biomes.at((bx<<2)+2,(by<<2)+2,(bz<<2)+2),null);
            chunk.setStatus(ChunkStatus.FEATURES);
            Heightmap.populateHeightmaps(chunk,heightmaps);
            storage.setNbt(cp,ChunkSerializer.serialize(w,chunk));
            // let the writer catch up now and then (2000 x 2000 is sixteen thousand chunks)
            if(++n%1024==0)storage.completeAll();
        }
        storage.completeAll();
        Town.log(String.format("wrote %d chunks (%d block entities, %d blocks to shape)",n,entities,marked));
        return n;
    }
}
