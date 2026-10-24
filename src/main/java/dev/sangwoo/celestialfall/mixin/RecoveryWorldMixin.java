package dev.sangwoo.celestialfall.mixin;
import dev.sangwoo.celestialfall.terrain.*;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fluid, piston, player and command edits cannot steal a temporarily reserved block. */
@Mixin(World.class)
public abstract class RecoveryWorldMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void botand$protectRecovery(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> cir) {
        World world=(World)(Object)this;
        if(!world.isClient&&!TerrainRestoration.editing()&&world.getBlockState(pos).getBlock() instanceof RecoveryBlock)cir.setReturnValue(false);
    }
}
