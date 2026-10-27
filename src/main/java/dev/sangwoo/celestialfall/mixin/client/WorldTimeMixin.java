package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CooldownHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server has set the world's clock (2.74.3): the cooldown list reads the ready ticks it is told against that clock
 * only from then (CooldownHud.timed). A world just joined counts from 0 until the server's time comes, and Fabric runs
 * the join, where the long cooldowns are told, before the game sends that time: read in between, Atomic's ten minutes
 * were put some 80 s ahead and its box did not show (the multiplayer check, joining again).
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class WorldTimeMixin {
    @Inject(method="onWorldTimeUpdate",at=@At("TAIL"))
    private void botand$timed(WorldTimeUpdateS2CPacket packet,CallbackInfo ci){CooldownHud.timed(MinecraftClient.getInstance().world);}
}
