package dev.sangwoo.celestialfall.mixin;
import dev.sangwoo.celestialfall.combat.HitStun;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(MobEntity.class)
public abstract class MobHitStunMixin {
    @Inject(method="tickNewAi",at=@At("HEAD"),cancellable=true)
    private void botand$pauseAi(CallbackInfo ci){if(HitStun.stunned((MobEntity)(Object)this)||dev.sangwoo.celestialfall.combat.CombatManager.staggered((MobEntity)(Object)this)||dev.sangwoo.celestialfall.stand.KagenoManager.frozen((MobEntity)(Object)this))ci.cancel();}
    @Inject(method="tryAttack",at=@At("HEAD"),cancellable=true)
    private void botand$noRetaliation(Entity target,CallbackInfoReturnable<Boolean> cir){if(HitStun.stunned((MobEntity)(Object)this)||dev.sangwoo.celestialfall.combat.CombatManager.staggered((MobEntity)(Object)this)||dev.sangwoo.celestialfall.stand.KagenoManager.frozen((MobEntity)(Object)this))cir.setReturnValue(false);}
}
