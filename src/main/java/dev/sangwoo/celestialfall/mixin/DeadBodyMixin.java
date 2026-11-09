package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.AutoRespawn;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (2.107.0) One dead on the Botand map, waiting to be brought back (AutoRespawn), is not taken out of the world a second
 * after dying as Minecraft takes the dead: taken out, the server stops telling them of everything about them (every
 * other body gone from their screen, the ground no longer sent) and their five seconds would show an emptied world.
 * (2.107.1, user request: "죽고 시체가 원래 처럼 소멸하는 건 똑같게 해줘") Their body still goes as Minecraft's does: a
 * second on (death time 20) in its puff of smoke, and out of everyone else's world (AutoRespawn.hide, told again each
 * tick for anyone who comes near), while their own client takes it out of theirs as it always does
 * (ClientPlayerEntity.updatePostDeath).
 */
@Mixin(LivingEntity.class)
public abstract class DeadBodyMixin {
    @Inject(method="updatePostDeath",at=@At("HEAD"),cancellable=true)
    private void botand$stayInTheWorld(CallbackInfo ci){
        if(!((Object)this instanceof ServerPlayerEntity p)||!AutoRespawn.waits(p))return;
        p.deathTime++;
        if(p.deathTime==AutoRespawn.VANISH)p.getWorld().sendEntityStatus(p,net.minecraft.entity.EntityStatuses.ADD_DEATH_PARTICLES);
        if(p.deathTime>=AutoRespawn.VANISH)AutoRespawn.hide(p);
        ci.cancel();
    }
}
