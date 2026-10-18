package dev.sangwoo.celestialfall;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;

public final class SpellManager {
    private static final List<Cast> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final double VIEW_RANGE_SQUARED = 640 * 640;
    private record Cast(UUID id, ServerWorld world, Vec3d target, long start, UUID caster, float yaw,
                        double damage, double radius, boolean damagePlayers,
                        boolean hurtCaster, Set<UUID> viewers) {}
    private SpellManager() {}

    public static boolean isCasting(UUID player) { return ACTIVE.stream().anyMatch(c -> c.caster.equals(player)); }
    public static boolean hasActive() { return !ACTIVE.isEmpty(); }
    public static boolean cast(ServerPlayerEntity player) {
        if(!dev.sangwoo.celestialfall.map.MapGate.require(player))return false;
        if(dev.sangwoo.celestialfall.combat.CombatManager.busy(player))return false;
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        long next = COOLDOWNS.getOrDefault(player.getUuid(), 0L);
        if (player.isSpectator()) return false;
        boolean free=NoCooldownState.enabled(player);
        if (!free && next > now) {
            player.sendMessage(Text.translatable("message.celestialfall.cooldown", (next - now + 19) / 20), true);
            return false;
        }
        if (HeavenManager.hasActive() || AtomicManager.hasActive() || ACTIVE.size() >= CelestialFall.CONFIG.maxActiveCasts) {
            player.sendMessage(Text.translatable("message.celestialfall.busy"), true);
            return false;
        }
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVec(1).multiply(CelestialFall.CONFIG.castRange));
        HitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.translatable("message.celestialfall.aim"), true);
            return false;
        }
        Vec3d target = hit.getPos().add(0, 0.08, 0);
        BlockPos pos = BlockPos.ofFloored(target);
        if (!world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)) return false;
        // Require open sky. The caller is told to move outside instead of spawning a hidden blade underground.
        HitResult above = world.raycast(new RaycastContext(target.add(0, 0.2, 0),
            target.add(0, MountainScale.SUMMON_HEIGHT, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (above.getType() != HitResult.Type.MISS) {
            player.sendMessage(Text.translatable("message.celestialfall.outdoors"), true);
            return false;
        }
        ModConfig c = CelestialFall.CONFIG;
        Cast spell = new Cast(UUID.randomUUID(), world, target, now, player.getUuid(), player.getYaw(),
            c.damage, c.impactRadius, c.damagePlayers, c.hurtCaster, new HashSet<>());
        ACTIVE.add(spell);
        if(!free) { COOLDOWNS.put(player.getUuid(), now + c.cooldownSeconds * 20L);
            player.getItemCooldownManager().set(CelestialFall.ASTRAL_SIGIL, c.cooldownSeconds * 20); }
        syncViewers(spell);
        player.sendMessage(Text.translatable("message.celestialfall.cast"), true);
        return true;
    }

    public static void tick(MinecraftServer server) {
        Iterator<Cast> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Cast cast = iterator.next();
            long age = cast.world.getTime() - cast.start;
            if (age >= SpellTimeline.END || age < 0 || !dev.sangwoo.celestialfall.map.MapGate.enabled(cast.world)) { iterator.remove(); continue; }
            if (age % 10 == 0) syncViewers(cast);
            if (age == SpellTimeline.IMPACT && cast.world.isChunkLoaded(BlockPos.ofFloored(cast.target))) impact(cast);
        }
        if (server.getTicks() % 1200 == 0)
            COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= server.getOverworld().getTime());
    }

    private static void syncViewers(Cast cast) {
        Set<UUID> present = new HashSet<>();
        for (ServerPlayerEntity player : cast.world.getPlayers()) {
            if (player.getPos().squaredDistanceTo(cast.target) > VIEW_RANGE_SQUARED) continue;
            present.add(player.getUuid());
            if (cast.viewers.add(player.getUuid()) && ServerPlayNetworking.canSend(player, CelestialFall.CAST_PACKET)) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeUuid(cast.id);
                buf.writeIdentifier(cast.world.getRegistryKey().getValue());
                buf.writeDouble(cast.target.x); buf.writeDouble(cast.target.y); buf.writeDouble(cast.target.z);
                buf.writeLong(cast.start); buf.writeFloat(cast.yaw); buf.writeUuid(cast.caster);
                ServerPlayNetworking.send(player, CelestialFall.CAST_PACKET, buf);
            }
        }
        cast.viewers.retainAll(present);
    }

    private static void impact(Cast cast) {
        dev.sangwoo.celestialfall.terrain.TerrainRestoration.crater(cast.world,cast.target,cast.radius,cast.world.getTime());
        var owner = cast.world.getPlayerByUuid(cast.caster);
        var damageSource = owner != null ? cast.world.getDamageSources().playerAttack(owner) : cast.world.getDamageSources().magic();
        Box area = new Box(cast.target, cast.target).expand(cast.radius);
        for (LivingEntity entity : cast.world.getEntitiesByClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (entity.isSpectator() || entity.squaredDistanceTo(cast.target) > cast.radius * cast.radius) continue;
            if (!cast.hurtCaster && entity.getUuid().equals(cast.caster)) continue;
            if (entity instanceof PlayerEntity && !cast.damagePlayers) continue;
            if (owner != null && entity instanceof PlayerEntity other && !owner.shouldDamagePlayer(other)) continue;
            double distance = entity.getPos().distanceTo(cast.target);
            float damage = (float) (cast.damage * (1 - 0.65 * Math.min(1, distance / cast.radius)));
            if (damage > 0 && entity.damage(damageSource, damage)) {
                Vec3d knockback = entity.getPos().subtract(cast.target).normalize().multiply(4.5);
                entity.addVelocity(knockback.x, 1.8, knockback.z);
                entity.velocityModified = true;
            }
        }
        cast.world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, cast.target.x, cast.target.y + 1, cast.target.z,
            1, 0, 0, 0, 0);
    }

    /** Remove both cooldown ledgers without touching active casts or their viewers. */
    public static void resetCooldown(ServerPlayerEntity player) {
        COOLDOWNS.remove(player.getUuid());
        if(player.getItemCooldownManager().isCoolingDown(CelestialFall.ASTRAL_SIGIL))player.getItemCooldownManager().remove(CelestialFall.ASTRAL_SIGIL);
    }
    public static void clear() { ACTIVE.clear(); COOLDOWNS.clear(); }
    public static void clear(MinecraftServer server) {
        clear();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if(player.getItemCooldownManager().isCoolingDown(CelestialFall.ASTRAL_SIGIL))player.getItemCooldownManager().remove(CelestialFall.ASTRAL_SIGIL);
            if (ServerPlayNetworking.canSend(player, CelestialFall.CLEAR_PACKET))
                ServerPlayNetworking.send(player, CelestialFall.CLEAR_PACKET, PacketByteBufs.empty());
        }
    }
}
