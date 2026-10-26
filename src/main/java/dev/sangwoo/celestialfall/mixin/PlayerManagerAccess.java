package dev.sangwoo.celestialfall.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.PlayerManager;
import net.minecraft.stat.ServerStatHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A boss's body leaves its stats and advancements behind as it goes (2.69.0, Bosses.remove): no player left. */
@Mixin(PlayerManager.class)
public interface PlayerManagerAccess {
    @Accessor("statisticsMap") Map<UUID,ServerStatHandler> botand$statistics();
    @Accessor("advancementTrackers") Map<UUID,PlayerAdvancementTracker> botand$advancements();
}
