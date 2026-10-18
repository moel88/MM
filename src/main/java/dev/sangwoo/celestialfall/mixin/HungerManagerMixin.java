package dev.sangwoo.celestialfall.mixin;
import net.minecraft.entity.player.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Hunger is disabled mechanically, so the hidden food meter never causes starvation or sprint lock. */
@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {
    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Shadow private float exhaustion;
    @Shadow private int foodTickTimer;
    @Inject(method="update",at=@At("HEAD"),cancellable=true)
    private void botand$noHunger(PlayerEntity player,CallbackInfo ci) {
        if(player.getWorld().isClient||!dev.sangwoo.celestialfall.map.MapGate.enabled(player.getWorld()))return;
        foodLevel=20;saturationLevel=0;exhaustion=0;foodTickTimer=0;ci.cancel();
    }
}
