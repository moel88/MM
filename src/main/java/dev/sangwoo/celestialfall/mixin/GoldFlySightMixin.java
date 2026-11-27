package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.stand.GoldFlyEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gold Experience's fly (2.151.0, GoldFlyEntity) is there only for whom it belongs to: anyone else's sight passes
 * through it. Nearly every skill finds what it strikes by its user's sight (LivingEntity.canSee), so only their own blows
 * meet it; on a client the same keeps another's drawn hits off it.
 */
@Mixin(LivingEntity.class)
public abstract class GoldFlySightMixin {
    @Inject(method="canSee",at=@At("HEAD"),cancellable=true)
    private void botand$notTheirFly(Entity entity,CallbackInfoReturnable<Boolean> cir){
        if(entity instanceof GoldFlyEntity fly&&!fly.ownedBy((Entity)(Object)this))cir.setReturnValue(false);
    }
}
