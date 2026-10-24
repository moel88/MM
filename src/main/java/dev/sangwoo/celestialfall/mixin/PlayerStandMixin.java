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
    @Unique private static final TrackedData<Boolean> BOTAND_STAND_OUT=DataTracker.registerData(PlayerEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    @Inject(method="initDataTracker",at=@At("TAIL"))
    private void botand$initializeStand(CallbackInfo ci){
        var tracker=((PlayerEntity)(Object)this).getDataTracker();tracker.startTracking(BOTAND_STAND,"none");tracker.startTracking(BOTAND_STAND_OUT,false);
    }
    @Override public String botand$getStand(){return ((PlayerEntity)(Object)this).getDataTracker().get(BOTAND_STAND);}
    @Override public void botand$setStand(String stand){((PlayerEntity)(Object)this).getDataTracker().set(BOTAND_STAND,stand);}
    @Override public boolean botand$isStandOut(){return ((PlayerEntity)(Object)this).getDataTracker().get(BOTAND_STAND_OUT);}
    @Override public void botand$setStandOut(boolean out){((PlayerEntity)(Object)this).getDataTracker().set(BOTAND_STAND_OUT,out);}
}
