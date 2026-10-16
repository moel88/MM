package dev.sangwoo.celestialfall;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;

/** Stationary world-space attack. Terrain geometry is client-only; server blocks never change. */
public final class AtomicManager {
    private record Cast(UUID id,UUID caster,ServerWorld world,Vec3d origin,float yaw,long start,
                        double visualRadius,double damage,Set<UUID> viewers) {}
    private static final RegistryKey<DamageType> ATOMIC_DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,new Identifier("celestialfall","atomic"));
    private static final List<Cast> ACTIVE=new ArrayList<>();
    private static final Map<UUID,Long> COOLDOWNS=new HashMap<>();
    private AtomicManager() {}
    private static long age(Cast c) { return c.world.getTime()-c.start; }
    /** The caster is rooted (and cannot act) from the first frame until the colour grade has faded. */
    public static boolean isCasting(UUID player) { return ACTIVE.stream().anyMatch(c -> c.caster.equals(player)&&AtomicTimeline.locked(age(c))); }
    public static boolean immobilized(net.minecraft.entity.player.PlayerEntity p){return ACTIVE.stream().anyMatch(c->c.caster.equals(p.getUuid())&&c.world==p.getWorld()&&AtomicTimeline.locked(age(c)));}
    /** The caster takes no damage while casting. */
    public static boolean invulnerable(net.minecraft.entity.player.PlayerEntity p){return immobilized(p);}
    public static boolean hasActive() { return !ACTIVE.isEmpty(); }
    public static boolean cast(ServerPlayerEntity player) {
        if(!dev.sangwoo.celestialfall.map.MapGate.require(player))return false;
        if(!dev.sangwoo.celestialfall.progression.ProgressManager.canUseKageno(player)){
            player.sendMessage(Text.literal("카게노 스탠드가 없습니다. 스탠드를 다시 획득해야 아토믹을 사용할 수 있어요."),true);return false;
        }
        if(dev.sangwoo.celestialfall.combat.CombatManager.busy(player))return false;
        if(!player.isAlive()||player.isSpectator()||player.isSleeping())return false;
        long now=player.getWorld().getTime(),next=COOLDOWNS.getOrDefault(player.getUuid(),0L);
        boolean free=NoCooldownState.enabled(player);
        if(!free && next>now) { player.sendMessage(Text.translatable("message.celestialfall.cooldown",(next-now+19)/20),true);return false; }
        if(hasActive() || SpellManager.hasActive() || HeavenManager.hasActive()) { player.sendMessage(Text.translatable("message.celestialfall.busy"),true);return false; }
        if(!player.isOnGround() || player.hasVehicle() || player.isSubmergedInWater()) {
            player.sendMessage(Text.translatable("message.celestialfall.atomic_ground"),true);return false;
        }
        if(!player.getServerWorld().isSkyVisible(player.getBlockPos().up(2))) {
            player.sendMessage(Text.translatable("message.celestialfall.outdoors"),true);return false;
        }
        var c=CelestialFall.CONFIG;
        Cast cast=new Cast(UUID.randomUUID(),player.getUuid(),player.getServerWorld(),player.getPos(),player.getYaw(),now,
            c.atomicVisualRadius,c.atomicDamagePerSecond,new HashSet<>());
        ACTIVE.add(cast);
        if(!free) { COOLDOWNS.put(player.getUuid(),now+c.atomicCooldownSeconds*20L);
            player.getItemCooldownManager().set(CelestialFall.ATOMIC_SWORD,c.atomicCooldownSeconds*20); }
        slowNearby(player,c.atomicVisualRadius);
        sync(cast);player.sendMessage(Text.translatable("message.celestialfall.atomic_cast"),true);return true;
    }
    /** On cast, enemies around the caster are slowed by 99 % for 4 seconds. */
    private static void slowNearby(ServerPlayerEntity caster,double radius) {
        var world=caster.getServerWorld();var box=caster.getBoundingBox().expand(radius,radius*.5,radius);
        for(var e:world.getEntitiesByClass(LivingEntity.class,box,e->e.isAlive()&&e!=caster&&!e.isSpectator())){
            if(e.squaredDistanceTo(caster)>radius*radius)continue;
            if(!(e instanceof Monster)&&!(e instanceof net.minecraft.entity.player.PlayerEntity target&&world.getServer().isPvpEnabled()&&caster.shouldDamagePlayer(target)))continue;
            e.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS,80,6,false,false,true),caster);
        }
    }
    private static void sync(Cast s) {
        Set<UUID> present=new HashSet<>();
        for(ServerPlayerEntity p:s.world.getPlayers()) {
            if(p.squaredDistanceTo(s.origin)>1600*1600)continue;
            present.add(p.getUuid());
            if(s.viewers.add(p.getUuid()) && ServerPlayNetworking.canSend(p,CelestialFall.ATOMIC_PACKET)) {
                var b=PacketByteBufs.create();b.writeUuid(s.id);b.writeUuid(s.caster);b.writeIdentifier(s.world.getRegistryKey().getValue());
                b.writeDouble(s.origin.x);b.writeDouble(s.origin.y);b.writeDouble(s.origin.z);
                b.writeFloat(s.yaw);b.writeLong(s.start);b.writeDouble(s.visualRadius);
                ServerPlayNetworking.send(p,CelestialFall.ATOMIC_PACKET,b);
            }
        }
        s.viewers.retainAll(present);
    }
    private static void cancel(Cast s) {
        for(ServerPlayerEntity p:s.world.getPlayers())if(ServerPlayNetworking.canSend(p,CelestialFall.ATOMIC_STOP_PACKET)) {
            var b=PacketByteBufs.create();b.writeUuid(s.id);ServerPlayNetworking.send(p,CelestialFall.ATOMIC_STOP_PACKET,b);
        }
    }
    public static void tick(MinecraftServer server) {
        var it=ACTIVE.iterator();
        while(it.hasNext()) {
            Cast s=it.next();long a=s.world.getTime()-s.start;
            if(a>=AtomicTimeline.END || a<0 || !dev.sangwoo.celestialfall.map.MapGate.enabled(s.world)) { cancel(s);it.remove();continue; }
            var caster=server.getPlayerManager().getPlayer(s.caster);
            // Once the caster is released the crater plays out on its own.
            if(AtomicTimeline.locked(a)&&(caster==null||!caster.isAlive()||caster.isSpectator()||caster.getWorld()!=s.world||!dev.sangwoo.celestialfall.progression.ProgressManager.canUseKageno(caster))){
                cancel(s);it.remove();continue;
            }
            if(AtomicTimeline.locked(a)){
                dev.sangwoo.celestialfall.stand.KagenoManager.freeze(caster);
                caster.setPosition(s.origin);caster.prevX=s.origin.x;caster.prevY=s.origin.y;caster.prevZ=s.origin.z;
            }
            if(a%10==0)sync(s);
            // No terrain mutation, explosion block collection, or restoration ledger for G.
            if(AtomicTimeline.damageDue(a))damage(s);
        }
        if(server.getTicks()%1200==0)COOLDOWNS.entrySet().removeIf(e->e.getValue()<=server.getOverworld().getTime());
    }
    private static void damage(Cast s) {
        if(s.damage<=0)return;
        var owner=s.world.getPlayerByUuid(s.caster);
        var source=new DamageSource(s.world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ATOMIC_DAMAGE),owner);
        // The column opens with the pillar: only what its edge has reached is hit, so running ahead of it escapes.
        double r=AtomicTimeline.damageRadius(s.world.getTime()-s.start,s.visualRadius);if(r<=0)return;
        Box bounds=new Box(s.origin.x-r,s.origin.y-2,s.origin.z-r,
            s.origin.x+r,s.origin.y+AtomicTimeline.damageHeight(s.visualRadius),s.origin.z+r);
        // Query loaded entities only. The bright cylinder and damage cylinder share their dimensions.
        for(var e:s.world.getEntitiesByClass(LivingEntity.class,bounds,e->e.isAlive() && (e instanceof Monster||e instanceof net.minecraft.entity.player.PlayerEntity&&e!=owner))) {
            if(e instanceof net.minecraft.entity.player.PlayerEntity target&&(owner==null||!s.world.getServer().isPvpEnabled()||!owner.shouldDamagePlayer(target)))continue;
            Vec3d d=e.getPos().subtract(s.origin);
            if(e.isSpectator() || !AtomicTimeline.insideColumn(d.x,d.y,d.z,r,AtomicTimeline.damageHeight(s.visualRadius)))continue;
            // Once a second from the impact frames to the end of the eruption beam; fixed damage.
            e.damage(source,owner instanceof ServerPlayerEntity caster?dev.sangwoo.celestialfall.stand.KagenoManager.outgoingDamage(caster,(float)s.damage):(float)s.damage);
        }
    }

    /** Remove both cooldown ledgers without touching active casts or their viewers. */
    public static void resetCooldown(ServerPlayerEntity player) {
        COOLDOWNS.remove(player.getUuid());
        if(player.getItemCooldownManager().isCoolingDown(CelestialFall.ATOMIC_SWORD))player.getItemCooldownManager().remove(CelestialFall.ATOMIC_SWORD);
    }
    public static void clear() { ACTIVE.clear();COOLDOWNS.clear(); }
    public static void clear(MinecraftServer server) {
        for(Cast s:ACTIVE)cancel(s);clear();
        for(var p:server.getPlayerManager().getPlayerList())p.getItemCooldownManager().remove(CelestialFall.ATOMIC_SWORD);
    }
}
