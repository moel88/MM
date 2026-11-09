package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.AutoRespawn;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (2.107.0) One dead on the Botand map lies where they fell until the server brings them back (AutoRespawn), not taken
 * out of the world a second after dying as Minecraft takes the dead. Taken out, the server stops telling them of
 * everything about them (every other body is gone from their screen, the ground no longer sent): the five seconds of
 * their view would show an emptied world. Everyone sees their body lying there (their own client keeps it too,
 * ClientPlayerMixin); it goes in Minecraft's puff of smoke as they come back.
 */
@Mixin(LivingEntity.class)
public abstract class DeadBodyMixin {
    @Inject(method="updatePostDeath",at=@At("HEAD"),cancellable=true)
    private void botand$lieUntilBack(CallbackInfo ci){
        if((Object)this instanceof ServerPlayerEntity p&&AutoRespawn.waits(p)){p.deathTime++;ci.cancel();}
    }
}
