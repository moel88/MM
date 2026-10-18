package dev.sangwoo.celestialfall.mixin.client;
import dev.sangwoo.celestialfall.client.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/**
 * Gameplay slowdown must not be interpreted by vanilla as an optical zoom. The lens is held while the slowdown is
 * meant (the guard flag, R's walking cast) and while the server's modifier is still on the speed: it leaves a tick or two
 * after the guard comes down or R ends, and the lens twitched in for that moment (user report, 2.38.2).
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class StandFovMixin {
    @Inject(method="getFovMultiplier",at=@At("RETURN"),cancellable=true)
    private void botand$stableGuardLens(CallbackInfoReturnable<Float> cir){
        var p=(AbstractClientPlayerEntity)(Object)this;
        if(MapClient.enabled()&&(CombatClient.snapshot(p.getUuid()).guard()||CombatClient.slowed(p)||StandClient.rWalking(p.getUuid())||StandClient.rSlowed(p)||AtomicClient.locked(p.getUuid())||AtomicClient.held(p)))cir.setReturnValue(1f);
    }
}
