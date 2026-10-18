package dev.sangwoo.celestialfall.mixin.client;
import dev.sangwoo.celestialfall.client.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Gameplay slowdown must not be interpreted by vanilla as an optical zoom. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class StandFovMixin {
    @Inject(method="getFovMultiplier",at=@At("RETURN"),cancellable=true)
    private void botand$stableGuardLens(CallbackInfoReturnable<Float> cir){
        var p=(AbstractClientPlayerEntity)(Object)this;
        if(MapClient.enabled()&&(CombatClient.snapshot(p.getUuid()).guard()||StandClient.rWalking(p.getUuid())||AtomicClient.locked(p.getUuid())||AtomicClient.held(p)))cir.setReturnValue(1f);
    }
}
