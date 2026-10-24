package dev.sangwoo.celestialfall.terrain;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** One archive per affected chunk: archive and empty cells save atomically in the same chunk NBT.
 * No per-block ticker/entity and no separate journal with a different save boundary. */
public final class RecoveryAnchor extends BlockEntity {
    public static final class Cell {
        public final BlockPos pos;
        public final BlockState state;
        public final NbtCompound data;
        public long solidAt;
        public Cell(BlockPos pos,BlockState state,NbtCompound data,long solidAt) {
            this.pos=pos.toImmutable();this.state=state;this.data=data;this.solidAt=solidAt;
        }
    }
    public final Map<BlockPos,Cell> cells=new LinkedHashMap<>();
    public int revision;
    public RecoveryAnchor(BlockPos pos,BlockState state) { super(TerrainRestoration.ANCHOR_ENTITY,pos,state); }
    @Override public void setWorld(World world) { super.setWorld(world);TerrainRestoration.track(this); }
    @Override public void markRemoved() { TerrainRestoration.untrack(this);super.markRemoved(); }
    @Override public void cancelRemoval() { super.cancelRemoval();TerrainRestoration.track(this); }
    public void changed() {
        revision++;markDirty();
        if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);
    }
    @Override protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);NbtList list=new NbtList();
        for(Cell c:cells.values()) {
            NbtCompound e=new NbtCompound();e.putLong("Pos",c.pos.asLong());e.put("State",NbtHelper.fromBlockState(c.state));
            e.putLong("SolidAt",c.solidAt);if(c.data!=null)e.put("Data",c.data.copy());list.add(e);
        }
        nbt.put("Cells",list);
    }
    @Override public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);cells.clear();
        // Network data deliberately excludes inventory / private block entity NBT.
        if(nbt.contains("Visual",NbtElement.LONG_ARRAY_TYPE)) {
            long[] v=nbt.getLongArray("Visual");
            for(int i=0;i+2<v.length;i+=3) {
                BlockPos p=BlockPos.fromLong(v[i]);
                cells.put(p,new Cell(p,Block.getStateFromRawId((int)v[i+1]),null,v[i+2]));
            }
        } else {
            NbtList list=nbt.getList("Cells",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<list.size();i++) {
                NbtCompound e=list.getCompound(i);BlockPos p=BlockPos.fromLong(e.getLong("Pos"));
                if((p.getX()>>4)!=(pos.getX()>>4)||(p.getZ()>>4)!=(pos.getZ()>>4))continue;
                BlockState s=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),e.getCompound("State"));
                if(s.getBlock() instanceof RecoveryBlock)continue;
                cells.put(p,new Cell(p,s,e.contains("Data")?e.getCompound("Data").copy():null,e.getLong("SolidAt")));
            }
        }
        revision++;
    }
    @Override public NbtCompound toInitialChunkDataNbt() {
        NbtCompound n=new NbtCompound();long[] v=new long[cells.size()*3];int i=0;
        for(Cell c:cells.values()){v[i++]=c.pos.asLong();v[i++]=Block.getRawIdFromState(c.state);v[i++]=c.solidAt;}
        n.putLongArray("Visual",v);return n;
    }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
}
