package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.stand.StandHolder;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerStandMixin implements StandHolder {
    @Unique private static final TrackedData<String> BOTAND_STAND=DataTracker.registerData(PlayerEntity.class,TrackedDataHandlerRegistry.STRING);
    @Inject(method="initDataTracker",at=@At("TAIL"))
    private void botand$initializeStand(CallbackInfo ci){((PlayerEntity)(Object)this).getDataTracker().startTracking(BOTAND_STAND,"none");}
    @Override public String botand$getStand(){return ((PlayerEntity)(Object)this).getDataTracker().get(BOTAND_STAND);}
    @Override public void botand$setStand(String stand){((PlayerEntity)(Object)this).getDataTracker().set(BOTAND_STAND,stand);}
}
