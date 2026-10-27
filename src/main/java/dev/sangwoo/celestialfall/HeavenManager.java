package dev.sangwoo.celestialfall;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Only synchronizes rendering and damages loaded monsters; temporarily excavates the impact site. */
public final class HeavenManager {
    private record Cast(UUID id,UUID caster,ServerWorld world,Vec3d origin,Vec3d target,float yaw,long start,
                        double damage,double radius,Set<UUID> viewers) {}
    private static final List<Cast> ACTIVE=new ArrayList<>();
    private static final Map<UUID,Long> COOLDOWNS=new HashMap<>();
    private HeavenManager() {}
    public static boolean isCasting(UUID player) { return ACTIVE.stream().anyMatch(c -> c.caster.equals(player)); }
    public static boolean hasActive() { return !ACTIVE.isEmpty(); }
    public static boolean cast(ServerPlayerEntity p) {
        if(!dev.sangwoo.celestialfall.map.MapGate.require(p))return false;
        if(dev.sangwoo.celestialfall.combat.CombatManager.busy(p))return false;
        if(!p.isAlive() || p.isSpectator())return false;
        ServerWorld w=p.getServerWorld();long now=w.getTime(),next=COOLDOWNS.getOrDefault(p.getUuid(),0L);
        boolean free=NoCooldownState.enabled(p);
        if(!free && next>now) { p.sendMessage(Text.translatable("message.celestialfall.cooldown",(next-now+19)/20),true);return false; }
        if(hasActive() || AtomicManager.hasActive() || SpellManager.hasActive()) {
            p.sendMessage(Text.translatable("message.celestialfall.busy"),true);return false;
        }
        if(!p.isOnGround() || p.hasVehicle() || p.isSubmergedInWater()) {
            p.sendMessage(Text.translatable("message.celestialfall.atomic_ground"),true);return false;
        }
        Vec3d eye=p.getEyePos(),end=eye.add(p.getRotationVec(1).multiply(CelestialFall.CONFIG.castRange));
        var hit=w.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(hit.getType()==HitResult.Type.MISS || hit.getPos().subtract(p.getPos()).horizontalLengthSquared()<144) {
            p.sendMessage(Text.translatable("message.celestialfall.heaven_aim"),true);return false;
        }
        Vec3d target=hit.getPos().add(0,.08,0);BlockPos pos=BlockPos.ofFloored(target);
        if(!w.isChunkLoaded(pos) || !w.getWorldBorder().contains(pos))return false;
        if(!w.isSkyVisible(p.getBlockPos().up(2)) || !w.isSkyVisible(pos.up())) {
            p.sendMessage(Text.translatable("message.celestialfall.outdoors"),true);return false;
        }
        var c=CelestialFall.CONFIG;
        Cast s=new Cast(UUID.randomUUID(),p.getUuid(),w,p.getPos(),target,p.getYaw(),now,c.heavenDamage,c.heavenImpactRadius,new HashSet<>());
        ACTIVE.add(s);
        if(!free) { COOLDOWNS.put(p.getUuid(),now+c.heavenCooldownSeconds*20L);
            p.getItemCooldownManager().set(CelestialFall.HEAVEN_SWORD,c.heavenCooldownSeconds*20); }
        sync(s);p.sendMessage(Text.translatable("message.celestialfall.heaven_cast"),true);return true;
    }
    private static void sync(Cast s) {
        Set<UUID> present=new HashSet<>();
        for(var p:s.world.getPlayers()) {
            if(p.squaredDistanceTo(s.target)>1100*1100)continue;
            present.add(p.getUuid());
            if(s.viewers.add(p.getUuid()) && ServerPlayNetworking.canSend(p,CelestialFall.HEAVEN_PACKET)) {
                var b=PacketByteBufs.create();b.writeUuid(s.id);b.writeUuid(s.caster);b.writeIdentifier(s.world.getRegistryKey().getValue());
                for(Vec3d v:new Vec3d[]{s.origin,s.target}) { b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z); }
                b.writeFloat(s.yaw);b.writeLong(s.start);ServerPlayNetworking.send(p,CelestialFall.HEAVEN_PACKET,b);
            }
        }
        s.viewers.retainAll(present);
    }
    private static void cancel(Cast s) {
        for(var p:s.world.getPlayers())if(ServerPlayNetworking.canSend(p,CelestialFall.HEAVEN_STOP_PACKET)) {
            var b=PacketByteBufs.create();b.writeUuid(s.id);ServerPlayNetworking.send(p,CelestialFall.HEAVEN_STOP_PACKET,b);
        }
    }
    public static void tick(MinecraftServer server) {
        var it=ACTIVE.iterator();while(it.hasNext()) {
            Cast s=it.next();long age=s.world.getTime()-s.start;
            if(age<0 || age>=HeavenTimeline.END || !dev.sangwoo.celestialfall.map.MapGate.enabled(s.world)) { cancel(s);it.remove();continue; }
            if(age<HeavenTimeline.PORTAL) {
                var p=s.world.getPlayerByUuid(s.caster);
                if(p==null || !p.isAlive() || p.isSpectator() || p.squaredDistanceTo(s.origin)>2.25) { cancel(s);it.remove();continue; }
            }
            if(age%10==0)sync(s);
            if(age==HeavenTimeline.IMPACT)dev.sangwoo.celestialfall.terrain.TerrainRestoration.crater(s.world,s.target,s.radius,s.world.getTime());
            if(age==HeavenTimeline.IMPACT && s.damage>0) {
                var source=s.world.getDamageSources().magic();
                for(var e:s.world.getEntitiesByClass(LivingEntity.class,new Box(s.target,s.target).expand(s.radius),e->e.isAlive() && e instanceof Monster)) {
                    if(e.isSpectator() || e.squaredDistanceTo(s.target)>s.radius*s.radius)continue;
                    e.damage(source,(float)s.damage);
                }
            }
        }
        if(server.getTicks()%1200==0)COOLDOWNS.entrySet().removeIf(e->e.getValue()<=server.getOverworld().getTime());
    }
    /** Remove both cooldown ledgers without touching active casts or their viewers. */
    public static void resetCooldown(ServerPlayerEntity player) {
        COOLDOWNS.remove(player.getUuid());
        if(player.getItemCooldownManager().isCoolingDown(CelestialFall.HEAVEN_SWORD))player.getItemCooldownManager().remove(CelestialFall.HEAVEN_SWORD);
    }
    public static void clear() { ACTIVE.clear();COOLDOWNS.clear(); }
    public static void clear(MinecraftServer server) {
        for(Cast s:ACTIVE)cancel(s);clear();
        for(var p:server.getPlayerManager().getPlayerList())p.getItemCooldownManager().remove(CelestialFall.HEAVEN_SWORD);
    }
}
