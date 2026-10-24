package dev.sangwoo.celestialfall.mixin;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class KagenoFreezeMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void botand$freezeTravel(Vec3d movement,CallbackInfo ci){var e=(LivingEntity)(Object)this;if(KagenoManager.frozen(e)){KagenoManager.freeze(e);ci.cancel();}}
    @Inject(method="tickMovement",at=@At("HEAD"),cancellable=true)
    private void botand$freezeAnimation(CallbackInfo ci){var e=(LivingEntity)(Object)this;if(KagenoManager.frozen(e)){KagenoManager.freeze(e);ci.cancel();}}
}
