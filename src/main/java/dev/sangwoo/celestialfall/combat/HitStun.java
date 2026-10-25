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

/** Server-owned capture: no status-effect removal, permanent AI flag, or player teleport leash. */
public final class HitStun {
    public static final Identifier PACKET=CelestialFall.id("botand_hitstun_v1");
    /**
     * A target held by a bare-hand blow until its combo window closes, or by a stand's punch (Star Platinum's attack,
     * 2.54.0: no window or combat state, {@code until} the tick it lets go) until its next punch has had time to land.
     */
    private record Hold(LivingEntity attacker,LivingEntity target,CombatState state,ComboWindow window,long hitAt,long standUntil,int standStrike) {
        Hold(LivingEntity attacker,LivingEntity target,CombatState state,ComboWindow window){this(attacker,target,state,window,window.hitAt(),0,0);}
        boolean stand(){return window==null;}
        long until(){return stand()?standUntil:window.until();}
        int strike(){return stand()?standStrike:window.strike();}
    }
    private static final Map<UUID,Hold> HELD=new HashMap<>();
    private static final Map<UUID,UUID> OWNED=new HashMap<>();
    private static Predicate<LivingEntity> clientCheck=e->false;
    private HitStun() {}
    public static void setClientCheck(Predicate<LivingEntity> check){clientCheck=check;}
    private static boolean valid(Hold h) {
        if(!(dev.sangwoo.celestialfall.map.MapGate.enabled(h.target.getWorld())&&h.target.isAlive()&&!h.target.isRemoved()&&!h.target.isSpectator()&&h.attacker.isAlive()&&!h.attacker.isRemoved()&&!h.attacker.isSpectator()
            &&h.target.getWorld()==h.attacker.getWorld()))return false;
        // a stand's punch holds them for its own time, while its user still has it out
        if(h.stand())return h.target.getWorld().getTime()<h.standUntil&&h.attacker instanceof PlayerEntity p&&dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punches(p);
        return attackerReady(h.attacker)&&h.window.active(h.state,h.attacker.getWorld().getTime())&&inReach(h.attacker,h.target);
    }
    private static boolean attackerReady(LivingEntity entity) {
        if(entity instanceof PlayerEntity p)return CombatManager.bare(p)&&!CombatManager.cinematic(p);
        return entity instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity d&&d.canContinueCombo();
    }
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
    public static LivingEntity followup(LivingEntity p,CombatState state) {
        UUID id=OWNED.get(p.getUuid());Hold h=id==null?null:HELD.get(id);
        if(h==null||h.stand())return null;
        if(!valid(h)||!h.window.nextContact(state,p.getWorld().getTime())){release(id);return null;}
        return h.target;
    }
    public static void landed(LivingEntity p,CombatState state,LivingEntity target) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return;
        releaseOwned(target.getUuid()); // Being hit cancels the victim's own buffered combo.
        CombatManager.interrupt(target);
        if(state.attack>=4){release(target.getUuid());releaseOwned(p.getUuid());return;}
        releaseOwned(p.getUuid());release(target.getUuid());
        if(!target.isAlive()||stoppedInTime(target))return;
        var h=new Hold(p,target,state,ComboWindow.landed(state,target.getWorld().getTime()));
        HELD.put(target.getUuid(),h);OWNED.put(p.getUuid(),target.getUuid());
        hold(target);
        sync(h,h.until());
    }
    /**
     * A stand's punch (Star Platinum's attack, 2.54.0) struck {@code target}: it is held where it stands, its own moves
     * cut off, until {@code until} (the next punch's contact if that comes as soon as it may, StarPlatinumRules.hold).
     * One punch can strike several; each is held on its own. The fourth punch throws them instead.
     */
    public static void standHold(LivingEntity attacker,LivingEntity target,int strike,long until) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld()))return;
        releaseOwned(target.getUuid());release(target.getUuid());
        if(!target.isAlive()||stoppedInTime(target))return;
        var h=new Hold(attacker,target,null,null,target.getWorld().getTime(),until,strike);
        HELD.put(target.getUuid(),h);
        hold(target);
        sync(h,h.until());
    }
    /**
     * Whether {@code target} stands stopped in Star Platinum's time stop (2.67.0): it is held by that, and no blow holds
     * it besides (its own screen showed the hold's stagger, the others' its flinch, while time was stopped for it).
     */
    private static boolean stoppedInTime(LivingEntity target){return dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopped(target);}
    /** Lets go of everyone a stand's punches of {@code attacker} hold. */
    public static void releaseStand(UUID attacker) {
        for(var e:new ArrayList<>(HELD.entrySet()))if(e.getValue().stand()&&e.getValue().attacker.getUuid().equals(attacker))release(e.getKey());
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
    public static void releaseOwned(UUID attacker){UUID id=OWNED.get(attacker);if(id!=null)release(id);}
    public static void attackerDamaged(LivingEntity entity) {
        if(entity.getWorld().isClient)return;
        // a stand user struck lets go of whom the punches hold, and the combo stops (2.54.0), and the rush (2.57.0), and
        // the inhale lets go of whom it holds (2.61.0), and the time stop's call is cut short (2.67.0)
        if(entity instanceof ServerPlayerEntity p){dev.sangwoo.celestialfall.stand.StarPlatinumAttack.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumRush.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumInhale.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.struck(p);}
        if(!OWNED.containsKey(entity.getUuid()))return;
        releaseOwned(entity.getUuid());CombatManager.interrupt(entity);
    }
    public static void release(UUID victim) {
        Hold h=HELD.remove(victim);if(h==null)return;
        if(!h.stand())OWNED.remove(h.attacker.getUuid(),victim);sync(h,0);
    }
    public static void disconnect(UUID id){releaseOwned(id);releaseStand(id);release(id);}
    public static void clear(){HELD.clear();OWNED.clear();}
    private static void sync(Hold h,long until) {
        if(!(h.target.getWorld() instanceof net.minecraft.server.world.ServerWorld world))return;
        Set<ServerPlayerEntity> viewers=new HashSet<>(world.getPlayers());if(h.attacker instanceof ServerPlayerEntity attacker)viewers.add(attacker);
        if(h.target instanceof ServerPlayerEntity p)viewers.add(p);
        for(var viewer:viewers) {
            if(viewer!=h.attacker&&viewer!=h.target&&viewer.squaredDistanceTo(h.target)>128*128||!ServerPlayNetworking.canSend(viewer,PACKET))continue;
            var b=PacketByteBufs.create();b.writeIdentifier(world.getRegistryKey().getValue());b.writeUuid(h.target.getUuid());b.writeInt(h.target.getId());
            b.writeUuid(h.attacker.getUuid());b.writeLong(until);b.writeLong(h.hitAt());b.writeByte(h.strike());ServerPlayNetworking.send(viewer,PACKET,b);
        }
    }
}
