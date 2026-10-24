package dev.sangwoo.celestialfall.terrain;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;

/** An empty, non-colliding reservation, not a solid replacement terrain block. */
public class RecoveryBlock extends Block {
    public RecoveryBlock() {
        super(Settings.create().noCollision().nonOpaque().notSolid().strength(-1,3600000)
            .dropsNothing().noBlockBreakParticles().pistonBehavior(PistonBehavior.BLOCK));
    }
    @Override public BlockRenderType getRenderType(BlockState s) { return BlockRenderType.INVISIBLE; }
    @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c) { return VoxelShapes.empty(); }
    public static final class Anchor extends RecoveryBlock implements BlockEntityProvider {
        @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state) { return new RecoveryAnchor(pos,state); }
    }
}
