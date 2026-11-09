package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.stand.StandHolder;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerStandMixin implements StandHolder,dev.sangwoo.celestialfall.map.VanillaHolder {
    @Unique private static final TrackedData<String> BOTAND_STAND=DataTracker.registerData(PlayerEntity.class,TrackedDataHandlerRegistry.STRING);
    /**
     * Whether the stand is out (bit 0) and whether its user called it so (bit 1: Q, 2.72.0, for the sound of it), one value
     * so the two come together; and (bit 2, 2.110.0) whether the player plays as plain Minecraft (VanillaMode), kept as the
     * stand's bits change.
     */
    @Unique private static final TrackedData<Byte> BOTAND_STAND_OUT=DataTracker.registerData(PlayerEntity.class,TrackedDataHandlerRegistry.BYTE);
    @Inject(method="initDataTracker",at=@At("TAIL"))
    private void botand$initializeStand(CallbackInfo ci){
        var tracker=((PlayerEntity)(Object)this).getDataTracker();tracker.startTracking(BOTAND_STAND,"none");tracker.startTracking(BOTAND_STAND_OUT,(byte)0);
    }
    @Override public String botand$getStand(){return ((PlayerEntity)(Object)this).getDataTracker().get(BOTAND_STAND);}
    @Override public void botand$setStand(String stand){((PlayerEntity)(Object)this).getDataTracker().set(BOTAND_STAND,stand);}
    @Override public boolean botand$isStandOut(){return (((PlayerEntity)(Object)this).getDataTracker().get(BOTAND_STAND_OUT)&1)!=0;}
    @Override public boolean botand$standCalled(){return (((PlayerEntity)(Object)this).getDataTracker().get(BOTAND_STAND_OUT)&2)!=0;}
    @Override public void botand$setStandOut(boolean out){((PlayerEntity)(Object)this).getDataTracker().set(BOTAND_STAND_OUT,(byte)((out?1:0)|vanillaBit()));}
    @Override public void botand$callStand(boolean out){((PlayerEntity)(Object)this).getDataTracker().set(BOTAND_STAND_OUT,(byte)((out?3:2)|vanillaBit()));}
    @Override public boolean botand$isVanilla(){return vanillaBit()!=0;}
    @Override public void botand$setVanilla(boolean on){
        var tracker=((PlayerEntity)(Object)this).getDataTracker();int b=tracker.get(BOTAND_STAND_OUT);
        tracker.set(BOTAND_STAND_OUT,(byte)(on?b|4:b&~4));
    }
    @Unique private int vanillaBit(){return ((PlayerEntity)(Object)this).getDataTracker().get(BOTAND_STAND_OUT)&4;}
}
