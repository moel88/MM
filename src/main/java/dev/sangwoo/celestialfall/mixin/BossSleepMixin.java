package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.boss.BossPlayer;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A boss never sleeps, and is not counted among those who might (2.69.0): the players asleep are counted against the
 * players only, so a boss in the world does not keep the night from being slept through.
 */
@Mixin(ServerWorld.class)
public abstract class BossSleepMixin {
    @ModifyArg(method="updateSleepingPlayers",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/SleepManager;update(Ljava/util/List;)Z"))
    private List<ServerPlayerEntity> botand$noBossAsleep(List<ServerPlayerEntity> players){
        for(var p:players)if(p instanceof BossPlayer)return players.stream().filter(q->!(q instanceof BossPlayer)).toList();
        return players;
    }
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/SleepManager;canResetTime(ILjava/util/List;)Z"),index=1)
    private List<ServerPlayerEntity> botand$noBossToWake(List<ServerPlayerEntity> players){
        for(var p:players)if(p instanceof BossPlayer)return players.stream().filter(q->!(q instanceof BossPlayer)).toList();
        return players;
    }
}
