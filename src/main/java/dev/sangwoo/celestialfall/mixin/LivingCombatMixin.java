package dev.sangwoo.celestialfall.mixin;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(LivingEntity.class)
public abstract class LivingCombatMixin {
    @Shadow protected float lastDamageTaken;
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
    @Inject(method="playHurtSound",at=@At("HEAD"),cancellable=true)
    private void botand$quietBlow(DamageSource source,CallbackInfo ci) {
        if(CombatManager.quiet((LivingEntity)(Object)this))ci.cancel();
    }
    @Inject(method="takeKnockback",at=@At("HEAD"),cancellable=true)
    private void botand$noEarlyKnockback(double strength,double x,double z,CallbackInfo ci) {
        if(CombatManager.suppressKnockback((LivingEntity)(Object)this)){ci.cancel();return;}
        // stopped in Star Platinum's stopped time (2.67.0): it comes to them as time moves again
        if(dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.deferKnockback((LivingEntity)(Object)this,strength,x,z))ci.cancel();
    }
    @ModifyVariable(method="damage",at=@At("HEAD"),argsOnly=true)
    private float botand$shadowMark(float amount){return dev.sangwoo.celestialfall.stand.KagenoManager.incomingDamage((LivingEntity)(Object)this,amount);}
    /** (2.126.0) X Chara's weakening: what one weakened deals is less, what they take more (XCharaWeaken.scale). */
    @ModifyVariable(method="damage",at=@At("HEAD"),argsOnly=true)
    private float botand$weakened(float amount,DamageSource source){return dev.sangwoo.celestialfall.xchara.XCharaWeaken.scale((LivingEntity)(Object)this,source,amount);}
    @Inject(method="damage",at=@At("HEAD"),cancellable=true)
    private void botand$guard(DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        if((Object)this instanceof ServerPlayerEntity p && dev.sangwoo.celestialfall.AtomicManager.invulnerable(p) && !source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY)){cir.setReturnValue(false);return;}
        // Star Platinum's beatdown's cutscene (2.64.0): nothing but its own blows harms its user or the one struck
        if(dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown.shielded((LivingEntity)(Object)this,source)){cir.setReturnValue(false);return;}
        // Lee So-sang's G (2.83.0): nothing harms its user while its cutscene holds them
        if(dev.sangwoo.celestialfall.isosang.IsosangAttack.shielded((LivingEntity)(Object)this,source)){cir.setReturnValue(false);return;}
        // X Chara's G (2.112.0): nothing but its own blows harms its two from its strange space on
        if(dev.sangwoo.celestialfall.xchara.XCharaUltimate.shielded((LivingEntity)(Object)this,source)){cir.setReturnValue(false);return;}
        // Lee So-sang's H (2.94.0): the first blow to reach her in its formation does not, and she is moved onto another of
        // its swords (only one that would land: none the moment after a blow is kept from her anyway, nor fire with fire
        // resistance, as the rest of this method has it)
        if((Object)this instanceof ServerPlayerEntity p&&Float.isFinite(amount)&&amount>0&&lands(p,source,amount)
            &&dev.sangwoo.celestialfall.isosang.IsosangAttack.dodges(p,source)){cir.setReturnValue(false);return;}
        // stopped in Star Platinum's stopped time (2.67.0): the harm is kept and comes to them all at once as time moves
        // again (the blow counts as landed now)
        if(dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.defer((LivingEntity)(Object)this,source,amount)){
            // (2.108.0) its hit counts now, as it lands
            dev.sangwoo.celestialfall.combat.HitCounter.landed((LivingEntity)(Object)this,source);
            cir.setReturnValue(true);return;
        }
        if((Object)this instanceof ServerPlayerEntity p && CombatManager.block(p,source,amount))cir.setReturnValue(false);
    }
    /** Whether vanilla would let this blow land ({@code LivingEntity.damage}): not within the moment after one that was greater, not fire on one fire cannot hurt. */
    private boolean lands(LivingEntity e,DamageSource source,float amount){
        if(source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)&&e.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE))return false;
        return !(e.timeUntilRegen>10&&!source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_COOLDOWN)&&amount<=lastDamageTaken);
    }
    @Inject(method="damage",at=@At("RETURN"))
    private void botand$interruptComboOnDamage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        if(!cir.getReturnValue())return;
        var self=(LivingEntity)(Object)this;
        HitStun.attackerDamaged(self);
        // every blow a player lands counts for their hit count (2.108.0): once, a stopped one as it was kept (above), not
        // again as stopped time gives it
        if(!self.getWorld().isClient&&!dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.settling()&&!dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopped(self))
            dev.sangwoo.celestialfall.combat.HitCounter.landed(self,source);
        // a blow landing on one glowing before a skill with a cutscene cuts the skill short (2.109.0, CutsceneGlow)
        if(!self.getWorld().isClient)dev.sangwoo.celestialfall.combat.CutsceneGlow.struck(self,source);
        // and on X Chara's G's user before its strange space, the G (2.112.0)
        if(!self.getWorld().isClient)dev.sangwoo.celestialfall.xchara.XCharaUltimate.struck(self,source);
    }
}
