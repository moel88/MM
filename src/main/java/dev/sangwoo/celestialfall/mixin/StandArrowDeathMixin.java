package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.stand.StandArrow;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No chat for the stand arrow (2.71.0, user request: "채팅은 아예 안 나오게"): one the arrow kills dies without their
 * death message going round the chat (StandArrowQuietMixin keeps it from the player list's broadcasts while their death
 * is being had); their own death screen still has it.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class StandArrowDeathMixin {
    @Inject(method="onDeath",at=@At("HEAD"))
    private void botand$quietArrowDeath(DamageSource source,CallbackInfo ci){if(source.isOf(StandArrow.DAMAGE))StandArrow.QUIET.set(Boolean.TRUE);}
    @Inject(method="onDeath",at=@At("RETURN"))
    private void botand$quietArrowDeathOver(DamageSource source,CallbackInfo ci){StandArrow.QUIET.remove();}
}
