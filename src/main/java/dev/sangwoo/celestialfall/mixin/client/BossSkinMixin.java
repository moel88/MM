package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.boss.BossClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A boss's body wears its own skin (2.69.0, textures/entity/boss/KIND.png), on the classic, broad-armed model. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class BossSkinMixin {
    @Inject(method="getSkinTexture",at=@At("HEAD"),cancellable=true)
    private void botand$bossSkin(CallbackInfoReturnable<Identifier> cir){
        var skin=BossClient.skin(((Entity)(Object)this).getUuid());if(skin!=null)cir.setReturnValue(skin);
    }
    @Inject(method="getModel",at=@At("HEAD"),cancellable=true)
    private void botand$bossModel(CallbackInfoReturnable<String> cir){
        if(BossClient.isBoss(((Entity)(Object)this).getUuid()))cir.setReturnValue("default");
    }
}
