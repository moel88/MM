package dev.sangwoo.celestialfall.mixin;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(LivingEntity.class)
public abstract class LivingCombatMixin {
    @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
    private net.minecraft.util.math.Vec3d botand$stunnedTravel(net.minecraft.util.math.Vec3d movement) {
        var entity=(LivingEntity)(Object)this;
        if(HitStun.stunned(entity)){HitStun.freeze(entity);return net.minecraft.util.math.Vec3d.ZERO;}
        return movement;
    }
    @Inject(method="jump",at=@At("HEAD"),cancellable=true)
    private void botand$noJumpDuringBreak(CallbackInfo ci) {
        if(HitStun.stunned((LivingEntity)(Object)this)||(Object)this instanceof net.minecraft.entity.player.PlayerEntity p&&CombatManager.actionLocked(p))ci.cancel();
    }
    @Inject(method="takeKnockback",at=@At("HEAD"),cancellable=true)
    private void botand$noEarlyKnockback(double strength,double x,double z,CallbackInfo ci) {
        if(CombatManager.suppressKnockback((LivingEntity)(Object)this))ci.cancel();
    }
    @ModifyVariable(method="damage",at=@At("HEAD"),argsOnly=true)
    private float botand$shadowMark(float amount){return dev.sangwoo.celestialfall.stand.KagenoManager.incomingDamage((LivingEntity)(Object)this,amount);}
    @Inject(method="damage",at=@At("HEAD"),cancellable=true)
    private void botand$guard(DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        if((Object)this instanceof ServerPlayerEntity p && dev.sangwoo.celestialfall.AtomicManager.invulnerable(p) && !source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY)){cir.setReturnValue(false);return;}
        if((Object)this instanceof ServerPlayerEntity p && CombatManager.block(p,source,amount))cir.setReturnValue(false);
    }
    @Inject(method="damage",at=@At("RETURN"))
    private void botand$interruptComboOnDamage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValue())HitStun.attackerDamaged((LivingEntity)(Object)this);
    }
}
