package dev.sangwoo.celestialfall.combat;

import dev.sangwoo.celestialfall.CelestialFall;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

/**
 * Server-owned hit reactions: no status-effect removal, permanent AI flag, or player teleport leash.
 * <p>
 * A basic attack's blow (bare hands, Kageno's sword, Star Platinum's punches) staggers whom it strikes ({@link #stagger},
 * 2.68.0, {@link Stagger}): for a moment they are incapacitated and slowed in their combat state, and every viewer draws
 * their flinch. Until 2.68.0 such a blow held them where they stood until the next one had time to land. Star
 * Platinum's rush (2.57.0) still holds whom it strikes that way, frozen in place until its next blow ({@link #standHold}).
 * Neither shows a notice on the struck one's screen (2.68.0, user request: "피격 경직 연속 타격 이거 뜨는거 없애줘").
 */
public final class HitStun {
    public static final Identifier PACKET=CelestialFall.id("botand_hitstun_v1");
    /** A target held by a stand's barrage (Star Platinum's rush) where it stands until {@code until}. */
    private record Hold(LivingEntity attacker,LivingEntity target,long hitAt,long until,int strike) {}
    private static final Map<UUID,Hold> HELD=new HashMap<>();
    private static Predicate<LivingEntity> clientCheck=e->false;
    private HitStun() {}
    public static void setClientCheck(Predicate<LivingEntity> check){clientCheck=check;}
    private static boolean valid(Hold h) {
        if(!(dev.sangwoo.celestialfall.map.MapGate.enabled(h.target.getWorld())&&h.target.isAlive()&&!h.target.isRemoved()&&!h.target.isSpectator()&&h.attacker.isAlive()&&!h.attacker.isRemoved()&&!h.attacker.isSpectator()
            &&h.target.getWorld()==h.attacker.getWorld()))return false;
        // a barrage holds them for its own time, while its user still has their stand out
        return h.target.getWorld().getTime()<h.until&&h.attacker instanceof PlayerEntity p&&dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punches(p);
    }
    /** Whether a barrage holds this entity where it stands now (a stagger is in its combat state instead, CombatManager). */
    public static boolean stunned(LivingEntity entity) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(entity.getWorld()))return false;
        if(entity.getWorld().isClient)return clientCheck.test(entity);
        Hold h=HELD.get(entity.getUuid());return h!=null&&h.target==entity&&valid(h);
    }
    public static boolean inReach(LivingEntity p,LivingEntity target) {
        if(p.getWorld()!=target.getWorld()||!p.canSee(target))return false;
        if(p instanceof PlayerEntity player&&target instanceof PlayerEntity other&&!player.shouldDamagePlayer(other))return false;
        Vec3d eye=p.getEyePos();Box box=target.getBoundingBox().expand(.14);
        Vec3d closest=new Vec3d(MathHelper.clamp(eye.x,box.minX,box.maxX),MathHelper.clamp(eye.y,box.minY,box.maxY),MathHelper.clamp(eye.z,box.minZ,box.maxZ));
        if(eye.squaredDistanceTo(closest)>3.15*3.15)return false;
        return p.getWorld().raycast(new RaycastContext(eye,closest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()==HitResult.Type.MISS;
    }
    /**
     * A basic attack's blow {@code strike} (1 to 4) of {@code attacker} landed on {@code target}: it staggers them until
     * {@code until} (2.68.0, {@link Stagger}, CombatManager.stagger), and every viewer draws their flinch until then. One
     * stopped in a time stop is staggered as time moves again (and flinches then with all that comes to them); one a
     * barrage holds keeps that hold's flinch.
     */
    public static void stagger(LivingEntity attacker,LivingEntity target,int strike,long until) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld())||!target.isAlive())return;
        CombatManager.stagger(target,until);
        if(!stoppedInTime(target)&&!HELD.containsKey(target.getUuid()))react(attacker,target,strike,target.getWorld().getTime(),until,false);
    }
    /** Bare hands' strike (its holder's combat state: which strike, since when) landed on {@code target}: it staggers them. */
    public static void landed(LivingEntity p,CombatState state,LivingEntity target) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return;
        stagger(p,target,state.attack,Stagger.bare(state.attack,state.attackStart,target.getWorld().getTime()));
    }
    /**
     * A blow of a stand's barrage (Star Platinum's rush, 2.57.0) struck {@code target}: it is held where it stands, its
     * own moves cut off, until {@code until} (past the barrage's next blow). One blow can strike several; each is held
     * on its own.
     */
    public static void standHold(LivingEntity attacker,LivingEntity target,int strike,long until) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld()))return;
        release(target.getUuid());
        if(!target.isAlive()||stoppedInTime(target))return;
        var h=new Hold(attacker,target,target.getWorld().getTime(),until,strike);
        HELD.put(target.getUuid(),h);
        hold(target);
        sync(h,h.until());
    }
    /**
     * Whether {@code target} stands stopped in Star Platinum's time stop (2.67.0): it is held by that, and no blow holds
     * it besides (its own screen showed the hold's notice, the others' its flinch, while time was stopped for it).
     */
    private static boolean stoppedInTime(LivingEntity target){return dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopped(target);}
    /** Lets go of everyone a stand's barrage of {@code attacker} holds. */
    public static void releaseStand(UUID attacker) {
        for(var e:new ArrayList<>(HELD.entrySet()))if(e.getValue().attacker.getUuid().equals(attacker))release(e.getKey());
    }
    private static void hold(LivingEntity target) {
        target.clearActiveItem();target.setSprinting(false);target.setJumping(false);
        freeze(target);
        if(target instanceof MobEntity mob)mob.getNavigation().stop();
        if(target instanceof ServerPlayerEntity player) {
            if(player.currentScreenHandler!=player.playerScreenHandler)player.closeHandledScreen();
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        }
    }
    public static void freeze(LivingEntity entity) {
        Vec3d v=entity.getVelocity();entity.setVelocity(0,Math.min(0,v.y),0);entity.setJumping(false);entity.setSprinting(false);
    }
    public static void tick() {
        for(var id:new ArrayList<>(HELD.keySet())) {
            Hold h=HELD.get(id);if(h==null)continue;
            if(!valid(h)){release(id);continue;}
            freeze(h.target);
            if(h.target.getWorld().getTime()%4==0)sync(h,h.until());
        }
    }
    public static void attackerDamaged(LivingEntity entity) {
        if(entity.getWorld().isClient)return;
        // a stand user struck lets go of whom the punches hold, and the combo stops (2.54.0), and the rush (2.57.0), and
        // the inhale lets go of whom it holds (2.61.0), and the time stop's call is cut short (2.67.0); Lee So-sang's sword's
        // hit in flight stops short (2.77.0)
        if(entity instanceof ServerPlayerEntity p){dev.sangwoo.celestialfall.stand.StarPlatinumAttack.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumRush.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumInhale.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.struck(p);dev.sangwoo.celestialfall.isosang.IsosangAttack.struck(p);}
    }
    public static void release(UUID victim) {
        Hold h=HELD.remove(victim);if(h==null)return;
        sync(h,0);
    }
    public static void disconnect(UUID id){releaseStand(id);release(id);}
    public static void clear(){HELD.clear();}
    private static void sync(Hold h,long until){react(h.attacker,h.target,h.strike,h.hitAt,until,true);}
    /**
     * Tells every viewer near {@code target} (and the attacker and the target themselves) how it reacts: struck by
     * {@code attacker}'s blow {@code strike} at {@code hitAt}, until {@code until} (0: no more), held where it stands by a
     * barrage ({@code held}) or staggered by a basic attack (2.68.0: the flinch only; the stagger's lock and slow come
     * with its combat state).
     */
    private static void react(LivingEntity attacker,LivingEntity target,int strike,long hitAt,long until,boolean held) {
        if(!(target.getWorld() instanceof net.minecraft.server.world.ServerWorld world))return;
        Set<ServerPlayerEntity> viewers=new HashSet<>(world.getPlayers());if(attacker instanceof ServerPlayerEntity a)viewers.add(a);
        if(target instanceof ServerPlayerEntity p)viewers.add(p);
        for(var viewer:viewers) {
            if(viewer!=attacker&&viewer!=target&&viewer.squaredDistanceTo(target)>128*128||!ServerPlayNetworking.canSend(viewer,PACKET))continue;
            var b=PacketByteBufs.create();b.writeIdentifier(world.getRegistryKey().getValue());b.writeUuid(target.getUuid());b.writeInt(target.getId());
            b.writeUuid(attacker.getUuid());b.writeLong(until);b.writeLong(hitAt);b.writeByte(strike);b.writeBoolean(held);ServerPlayNetworking.send(viewer,PACKET,b);
        }
    }
}
