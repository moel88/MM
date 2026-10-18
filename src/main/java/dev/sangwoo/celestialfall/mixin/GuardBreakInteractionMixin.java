package dev.sangwoo.celestialfall.mixin;
import dev.sangwoo.celestialfall.combat.CombatManager;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ServerPlayerInteractionManager.class)
public abstract class GuardBreakInteractionMixin {
    @Shadow @Final protected ServerPlayerEntity player;
    @Shadow protected ServerWorld world;
    @Shadow private boolean mining,failedToMine;
    @Shadow private BlockPos miningPos,failedMiningPos;
    @Shadow private int blockBreakingProgress;
    @Inject(method="update",at=@At("HEAD"),cancellable=true)
    private void botand$cancelPendingMining(CallbackInfo ci) {
        if(!CombatManager.actionLocked(player))return;
        if(mining)world.setBlockBreakingInfo(player.getId(),miningPos,-1);
        if(failedToMine)world.setBlockBreakingInfo(player.getId(),failedMiningPos,-1);
        mining=failedToMine=false;blockBreakingProgress=-1;ci.cancel();
    }
    @Inject(method="tryBreakBlock",at=@At("HEAD"),cancellable=true)
    private void botand$noBreak(BlockPos pos,CallbackInfoReturnable<Boolean> cir){if(CombatManager.actionLocked(player))cir.setReturnValue(false);}
    @Inject(method={"interactItem","interactBlock"},at=@At("HEAD"),cancellable=true)
    private void botand$noUse(CallbackInfoReturnable<ActionResult> cir){if(CombatManager.actionLocked(player))cir.setReturnValue(ActionResult.FAIL);}
}
