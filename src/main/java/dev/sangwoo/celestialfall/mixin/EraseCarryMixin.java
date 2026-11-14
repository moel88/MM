package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.xchara.EraseCubes;
import dev.sangwoo.celestialfall.xchara.XCharaErase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (2.133.0) X Chara's terrain delete's cubes of ground: one standing on it is carried up as it rises, before they move this
 * tick (EraseCubes.carry: the server carries everything but players, each client its own player; their move then stands
 * them on its top); and one who came off it (or fell as it burst) takes no harm from that fall (XCharaErase.spared: the
 * user's "덩어리에서 떨어지면 데미지 없음").
 */
@Mixin(LivingEntity.class)
public abstract class EraseCarryMixin {
    @Inject(method="tickMovement",at=@At("HEAD"))
    private void botand$eraseCarry(CallbackInfo ci){
        LivingEntity self=(LivingEntity)(Object)this;
        if(!EraseCubes.any(self.getWorld()))return;
        double up=EraseCubes.carry(self);
        if(up>0){self.setPosition(self.getX(),self.getY()+up,self.getZ());if(self.getVelocity().y<0)self.setVelocity(self.getVelocity().x,0,self.getVelocity().z);self.fallDistance=0;}
    }
    @Inject(method="handleFallDamage",at=@At("HEAD"),cancellable=true)
    private void botand$eraseFall(float fallDistance,float damageMultiplier,DamageSource source,CallbackInfoReturnable<Boolean> ci){
        if(XCharaErase.spared((LivingEntity)(Object)this))ci.setReturnValue(false);
    }
}
