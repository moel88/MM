package dev.sangwoo.celestialfall.combat;

import dev.sangwoo.celestialfall.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Validates input and traces each strike at its contact frame. Clients never select damage or targets. */
public final class CombatManager {
    public static final Identifier INPUT=CelestialFall.id("botand_input_v1"), STATE=CelestialFall.id("botand_state_v1");
    public static final RegistryKey<DamageType> UNARMED=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("unarmed"));
    /**
     * The guard's, the guard break's and (2.60.0) an incapacitating blow's slowdown on the movement speed (the client
     * keeps its lens still while it is on).
     */
    public static final UUID SLOW=UUID.fromString("c00bcb8e-e904-4ebf-9f83-8cbb798783ed");
    /** An incapacitating blow's slowdown on anything else that walks (2.60.0: mobs; players and training dummies keep theirs in their combat state). */
    public static final UUID MOB_SLOW=UUID.fromString("5d0f6c2e-8a1b-4c3e-9f47-2b6e1d9a7c15");
    /** Mobs slowed by an incapacitating blow, and the tick each slow ends. */
    private static final Map<LivingEntity,Long> MOB_SLOWS=new WeakHashMap<>();
    /**
     * Players whose client started running while an action held them: the server drops that packet and the client
     * never sends it again, so they run once the action lets go.
     */
    private static final Set<UUID> RUN_AFTER=new HashSet<>();
    private record Entry(CombatState state,Identifier dimension,int entityId) {}
    private static final Map<UUID,Entry> STATES=new HashMap<>();
    private static final ThreadLocal<LivingEntity> NO_KNOCKBACK=new ThreadLocal<>(),QUIET=new ThreadLocal<>();
    private CombatManager() {}
    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=3)return;
            int action=buf.readUnsignedByte(),a=buf.readByte(),b=buf.readByte();
            if(action>3||Math.abs(a)>1||Math.abs(b)>1)return;
            server.execute(()->input(player,action,a,b));
        });
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(!dev.sangwoo.celestialfall.map.MapGate.enabled(world))return dev.sangwoo.celestialfall.map.MapGate.botandItem(player.getStackInHand(hand))?ActionResult.FAIL:ActionResult.PASS;
            if(dev.sangwoo.celestialfall.stand.StandManager.kageno(player))return ActionResult.FAIL;
            // Star Platinum out: its punches are the attack (2.54.0), whatever the hand holds
            if(dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punches(player)){
                if(player instanceof ServerPlayerEntity p)dev.sangwoo.celestialfall.stand.StarPlatinumAttack.request(p);
                return ActionResult.FAIL;
            }
            if(hand==Hand.MAIN_HAND&&bare(player)&&!player.isSpectator()) {
                if(player instanceof ServerPlayerEntity p)input(p,0,0,0);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(CombatManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{HitStun.disconnect(handler.player.getUuid());STATES.remove(handler.player.getUuid());RUN_AFTER.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{STATES.clear();HitStun.clear();RUN_AFTER.clear();MOB_SLOWS.clear();});
    }
    /** A sprint packet the action lock dropped ({@code start}) or any stop: the latest wish to run is kept for when the lock ends. */
    public static void runRequest(ServerPlayerEntity p,boolean start){if(start)RUN_AFTER.add(p.getUuid());else RUN_AFTER.remove(p.getUuid());}
    public static void leave(ServerPlayerEntity p) {
        HitStun.disconnect(p.getUuid());STATES.remove(p.getUuid());RUN_AFTER.remove(p.getUuid());
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr!=null)attr.removeModifier(SLOW);
    }
    /** The player fights with their own fists: no Kageno, Star Platinum not out (its punches are the attack then, 2.54.0), hands empty. */
    public static boolean bare(PlayerEntity p) { return !dev.sangwoo.celestialfall.stand.StandManager.kageno(p)&&!dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punches(p)&& p.getMainHandStack().isEmpty()&&p.getOffHandStack().isEmpty(); }
    public static boolean cinematic(PlayerEntity p) { return dev.sangwoo.celestialfall.stand.KagenoManager.busy(p)||SpellManager.isCasting(p.getUuid())||AtomicManager.isCasting(p.getUuid())||HeavenManager.isCasting(p.getUuid()); }
    private static boolean valid(ServerPlayerEntity p) {
        return dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())&&p.isAlive()&&!p.isSpectator()&&!p.hasVehicle()&&!p.isSleeping()&&!p.isFallFlying()&&!p.isSwimming()&&!cinematic(p)&&!HitStun.stunned(p);
    }
    private static CombatState state(ServerPlayerEntity p) {
        var id=p.getWorld().getRegistryKey().getValue();var e=STATES.get(p.getUuid());
        if(e==null||!e.dimension.equals(id)||e.entityId!=p.getId()) {
            e=new Entry(new CombatState(),id,p.getId());STATES.put(p.getUuid(),e);
            var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr!=null)attr.removeModifier(SLOW);
            sync(p,e.state);
        }
        return e.state;
    }
    public static boolean busy(PlayerEntity p) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return false;
        if(HitStun.stunned(p)||dev.sangwoo.celestialfall.stand.KagenoManager.busy(p)||dev.sangwoo.celestialfall.stand.StarPlatinumRush.rushing(p)
            ||dev.sangwoo.celestialfall.stand.StarPlatinumInhale.inhaling(p))return true;
        var e=STATES.get(p.getUuid());if(e==null)return false;
        long n=p.getWorld().getTime();return e.state.locked(n)||e.state.attacking(n)||e.state.dashing(n)||e.state.guarding;
    }
    public static boolean actionLocked(PlayerEntity p) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return false;
        if(HitStun.stunned(p)||dev.sangwoo.celestialfall.stand.KagenoManager.busy(p)||dev.sangwoo.celestialfall.stand.StarPlatinumRush.rushing(p)
            ||dev.sangwoo.celestialfall.stand.StarPlatinumInhale.inhaling(p))return true;
        var e=STATES.get(p.getUuid());return e!=null&&e.entityId==p.getId()&&e.dimension.equals(p.getWorld().getRegistryKey().getValue())&&e.state.locked(p.getWorld().getTime());
    }
    /**
     * Whether this player can do nothing but walk now (2.60.0): incapacitated, or their guard broken (a broken guard is
     * an incapacitation, "가드 브레이커 개념이 2초 무력화"). Every skill, attack, dash and guard asks this through
     * {@link #busy} and {@link #actionLocked}; the stand's summoning too.
     */
    public static boolean incapacitated(PlayerEntity p) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return false;
        var e=STATES.get(p.getUuid());return e!=null&&e.entityId==p.getId()&&e.dimension.equals(p.getWorld().getRegistryKey().getValue())&&e.state.locked(p.getWorld().getTime());
    }
    /**
     * Incapacitates whoever a blow struck (2.60.0: Star Platinum's Star Finger, user request: "맞추면 2초 95% 슬로우 +
     * 3초 동안 대쉬 평타 포함 아무 스킬도 사용 못함(무력화)"): for {@code ticks} they can use no skill and neither attack,
     * dash nor guard, and for {@code slowTicks} they walk {@code slow} slower (0.95: a twentieth of their speed), the
     * game's lens kept still. Whatever they were doing is cut off: their attack, guard and dash, the combo they held
     * someone in, their stand's punches and rush, a skill they were casting. A training dummy stops attacking and
     * guarding as long; anything else is slowed.
     */
    public static void incapacitate(LivingEntity target,int ticks,int slowTicks,double slow) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld())||!target.isAlive())return;
        if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dummy){dummy.incapacitate(ticks,slowTicks,slow);return;}
        if(!(target instanceof ServerPlayerEntity p)) {
            var attr=target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null||slowTicks<=0)return;
            attr.removeModifier(MOB_SLOW);attr.addTemporaryModifier(new EntityAttributeModifier(MOB_SLOW,"Botand incapacitation",-slow,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
            MOB_SLOWS.put(target,target.getWorld().getTime()+slowTicks);
            if(target instanceof net.minecraft.entity.mob.MobEntity mob)mob.getNavigation().stop();
            return;
        }
        long now=p.getWorld().getTime();var s=state(p);
        s.incapacitate(now,ticks,slowTicks,slow);
        HitStun.releaseOwned(p.getUuid());
        dev.sangwoo.celestialfall.stand.StarPlatinumAttack.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumRush.struck(p);
        dev.sangwoo.celestialfall.stand.StarPlatinumInhale.struck(p);
        dev.sangwoo.celestialfall.stand.KagenoManager.disable(p);
        p.clearActiveItem();p.setSprinting(false);if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        speed(p,s,now);sync(p,s);
    }
    /**
     * The incapacitation that was to last until {@code until} ends now (2.61.0: Star Platinum's inhale let go of
     * {@code target} early, its user struck): one that lasts longer stays.
     */
    public static void free(LivingEntity target,long until) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld()))return;
        if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dummy){dummy.free(until);return;}
        if(!(target instanceof ServerPlayerEntity p))return;
        var e=STATES.get(p.getUuid());if(e==null)return;
        long now=p.getWorld().getTime();e.state.free(now,until);sync(p,e.state);
    }
    /**
     * Whether {@code target} holds a guard up against something coming at it from {@code from} (2.61.0: Star Platinum's
     * inhale, which deals no blow for a guard to take): its guard up and not broken, facing it as a guard takes a blow
     * (CombatManager.block, TrainingDummyEntity.block). With {@code show} the guard is heard and seen taking it.
     */
    public static boolean guardTakes(LivingEntity target,Vec3d from,boolean show) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld()))return false;
        long now=target.getWorld().getTime();float yaw;
        if(target instanceof ServerPlayerEntity p){
            var e=STATES.get(p.getUuid());if(e==null||!e.state.guarding||e.state.broken(now)||!valid(p))return false;
            yaw=p.getYaw();
        }else if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dummy){
            if(!dummy.guarding()||dummy.brokenUntil()>now)return false;
            yaw=dummy.getYaw();
        }else return false;
        Vec3d toward=from.subtract(target.getPos()).multiply(1,0,1);
        if(toward.lengthSquared()<1e-6||Vec3d.fromPolar(0,yaw).dotProduct(toward.normalize())<.2)return false;
        if(show&&target.getWorld() instanceof net.minecraft.server.world.ServerWorld world){
            world.playSound(null,target.getX(),target.getY(),target.getZ(),SoundEvents.ITEM_SHIELD_BLOCK,target instanceof ServerPlayerEntity?SoundCategory.PLAYERS:SoundCategory.HOSTILE,.85f,1.15f);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,target.getX(),target.getY()+1.15,target.getZ(),9,.25,.22,.15,.07);
        }
        return true;
    }
    public static void interrupt(LivingEntity target) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld()))return;
        if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dummy){dummy.interruptCombat();return;}
        if(!(target instanceof ServerPlayerEntity p))return;
        long now=p.getWorld().getTime();var s=state(p);s.cancel(now);s.combatUntil=now+100;sync(p,s);
    }
    private static void input(ServerPlayerEntity p,int action,int a,int b) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())){leave(p);return;}
        var s=state(p);long now=p.getWorld().getTime();
        if(action==2) { if(s.guard(false,now))sync(p,s);return; }
        if(!valid(p)||p.isUsingItem())return;
        if(action==0 && bare(p) && s.requestAttack(now)) { p.resetLastAttackedTicks();swing(p,s);sync(p,s); }
        else if(action==1 && s.guard(true,now))sync(p,s);
        else if(action==3 && !p.isTouchingWater()&&s.dash(now,a,b)) {
            s.dashYaw=p.getYaw();p.setSprinting(false);sound(p,SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,.35f,1.25f);sync(p,s);
        }
    }
    private static void tick(MinecraftServer server) {
        for(var p:server.getPlayerManager().getPlayerList()) {
            if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())){leave(p);continue;}
            var s=state(p);long now=p.getWorld().getTime();
            boolean dirty=false;
            if(!valid(p)) { if(s.attack>0||s.guarding||s.dashing(now)) {s.cancel(now);dirty=true;} }
            if(s.attack>0&&!bare(p)) { s.attack=0;s.queued=false;dirty=true; }
            boolean wasBroken=s.broken(now-1);
            if(wasBroken&&!s.broken(now)) {s.pressure=0;dirty=true;}
            int before=s.serial;
            float previousPressure=s.pressure;
            if(s.tick(now))dirty=true;
            if(previousPressure>0&&s.pressure==0)dirty=true;
            if(before!=s.serial)swing(p,s);
            if(s.attack>0 && s.hitDue(now))hit(p,s);
            if(s.dashing(now)) {
                // Horizontal impulse also works over a step/gap and during creative flight.
                // Vertical velocity and flight permission are preserved; a wall or water ends the dash, and its cooldown
                // runs from there (2.44.0).
                if(p.horizontalCollision||p.isTouchingWater()) {s.stopDash(now);dirty=true;}
                else {
                    double yaw=Math.toRadians(s.dashYaw),v=CombatState.dashSpeed((int)(now-s.dashStart));
                    Vec3d direction=new Vec3d(-Math.sin(yaw)*s.dashForward+Math.cos(yaw)*s.dashSide,0,Math.cos(yaw)*s.dashForward+Math.sin(yaw)*s.dashSide);
                    p.setVelocity(direction.multiply(v).add(0,p.getVelocity().y,0));p.velocityModified=true;
                    p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                    if((now-s.dashStart)%2==0)p.getServerWorld().spawnParticles(ParticleTypes.CLOUD,p.getX(),p.getY()+.1,p.getZ(),2,.13,.02,.13,.005);
                }
            }
            // an incapacitation over (2.60.0): told at once, so the client lets its keys go on the tick it ends
            if(s.disabled(now-1)&&!s.disabled(now))dirty=true;
            if(s.locked(now)){p.clearActiveItem();p.setSprinting(false);}
            speed(p,s,now);
            if(RUN_AFTER.contains(p.getUuid())&&!actionLocked(p)){RUN_AFTER.remove(p.getUuid());if(valid(p)&&!s.guarding&&!s.locked(now))p.setSprinting(true);}
            if(dirty||now%4==0&&(s.pressure>0||s.combatUntil>now||s.locked(now)))sync(p,s);
        }
        HitStun.tick();
        // mobs' slows from incapacitating blows run out (2.60.0)
        for(var it=MOB_SLOWS.entrySet().iterator();it.hasNext();){
            var e=it.next();var mob=e.getKey();
            if(mob.isRemoved()||!mob.isAlive()||mob.getWorld().getTime()>=e.getValue()){var attr=mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr!=null)attr.removeModifier(MOB_SLOW);it.remove();}
        }
    }
    private static void speed(ServerPlayerEntity p,CombatState s,long now) {
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
        // the strongest of what slows them: a broken guard, an incapacitating blow (2.60.0), the guard
        double slow=Math.min(s.broken(now)?-.9:0,Math.min(s.slowed(now)?-s.slow:0,s.guarding?-.55:0));
        var old=attr.getModifier(SLOW);
        if(old!=null&&old.getValue()==slow)return;
        if(old!=null)attr.removeModifier(SLOW);
        if(slow!=0)attr.addTemporaryModifier(new EntityAttributeModifier(SLOW,"Botand combat movement",slow,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        if(slow!=0)p.setSprinting(false);
    }
    private static void hit(ServerPlayerEntity p,CombatState s) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return;
        int strike=s.attack;
        var world=p.getServerWorld();Vec3d eye=p.getEyePos(),end=eye.add(Vec3d.fromPolar(p.getPitch(),p.getYaw()).multiply(3.15));
        var wall=world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getPos();
        LivingEntity target=strike>1?HitStun.followup(p,s):null;double distance=eye.squaredDistanceTo(end);
        if(target==null)for(var e:world.getEntitiesByClass(LivingEntity.class,p.getBoundingBox().stretch(end.subtract(eye)).expand(1),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit())) {
            if(e instanceof PlayerEntity other && !p.shouldDamagePlayer(other)||!p.canSee(e))continue;
            var intersection=e.getBoundingBox().expand(.14).raycast(eye,end);
            if(intersection.isPresent()&&eye.squaredDistanceTo(intersection.get())<=distance) {target=e;distance=eye.squaredDistanceTo(intersection.get());}
        }
        if(target==null){HitStun.releaseOwned(p.getUuid());return;}
        var source=new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(UNARMED),p);
        boolean damaged=damageWithoutKnockback(target,source,CombatState.DAMAGE[strike]);
        if(!damaged){HitStun.releaseOwned(p.getUuid());return;}
        if(s.attack==strike&&p.isAlive())HitStun.landed(p,s,target);else HitStun.releaseOwned(p.getUuid());
        if(strike==4) {
            double yaw=Math.toRadians(p.getYaw());target.takeKnockback(.95,Math.sin(yaw),-Math.cos(yaw));
            target.velocityModified=true;
            if(target instanceof ServerPlayerEntity other)other.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(target));
        }
        p.onAttacking(target);p.addExhaustion(.1f);
        sound(p,strike==4?SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK:SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,.7f,strike==4?.75f:1.1f);
        world.spawnParticles(ParticleTypes.CRIT,target.getX(),target.getBodyY(.6),target.getZ(),strike==4?12:5,.18,.18,.18,.08);
        var e=STATES.get(target.getUuid());if(e!=null) {e.state.combatUntil=world.getTime()+100;}
    }
    public static boolean damageWithoutKnockback(LivingEntity target,DamageSource source,float amount) {
        LivingEntity previous=NO_KNOCKBACK.get();NO_KNOCKBACK.set(target);
        try{return target.damage(source,amount);}
        finally{if(previous==null)NO_KNOCKBACK.remove();else NO_KNOCKBACK.set(previous);}
    }
    public static boolean suppressKnockback(LivingEntity entity) {return NO_KNOCKBACK.get()==entity;}
    /** As {@link #damageWithoutKnockback}, and the target does not cry out (a barrage's blow, 2.57.0: its own sound carries it). */
    public static boolean damageQuietly(LivingEntity target,DamageSource source,float amount) {
        LivingEntity previous=QUIET.get();QUIET.set(target);
        try{return damageWithoutKnockback(target,source,amount);}
        finally{if(previous==null)QUIET.remove();else QUIET.set(previous);}
    }
    /** Whether this entity's cry of pain is held back now ({@link #damageQuietly}). */
    public static boolean quiet(LivingEntity entity) {return QUIET.get()==entity;}
    /**
     * A blow no guard can take breaks the guard of whoever it strikes, if they hold one up (2.59.0: Star Platinum's heavy
     * punch, "가드 브레이커"): broken for {@code ticks}, slowed and unable to guard or act, as a guard worn down by blows
     * is, with the same sound and sparks. Returns whether there was a guard up to break.
     */
    public static boolean breakGuard(LivingEntity target,int ticks) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld()))return false;
        if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dummy)return dummy.breakGuard(ticks);
        if(!(target instanceof ServerPlayerEntity p))return false;
        var e=STATES.get(p.getUuid());long now=p.getWorld().getTime();
        if(e==null||!e.state.guarding||e.state.broken(now))return false;
        e.state.breakGuard(now,ticks);
        p.clearActiveItem();p.setSprinting(false);if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        speed(p,e.state,now);sync(p,e.state);
        sound(p,SoundEvents.ITEM_SHIELD_BREAK,.85f,.8f);
        Vec3d effect=p.getPos().add(0,1.15,0).add(Vec3d.fromPolar(p.getPitch(),p.getYaw()).multiply(.6));
        p.getServerWorld().spawnParticles(ParticleTypes.CRIT,effect.x,effect.y,effect.z,24,.25,.22,.15,.07);
        return true;
    }
    /** Whether this player holds their guard up now (and it is not broken). */
    public static boolean guarding(ServerPlayerEntity p) {var e=STATES.get(p.getUuid());return e!=null&&e.state.guarding&&!e.state.broken(p.getWorld().getTime());}
    public static boolean block(ServerPlayerEntity p,DamageSource source,float amount) {
        if(!Float.isFinite(amount)||amount<=0||p.isInvulnerableTo(source)||!valid(p))return false;
        var e=STATES.get(p.getUuid());long now=p.getWorld().getTime();
        if(e==null||!e.state.guarding||e.state.broken(now)||source.isIn(DamageTypeTags.BYPASSES_SHIELD)||source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        Vec3d origin=source.getPosition();if(origin==null)return false;
        Vec3d toward=origin.subtract(p.getPos()).multiply(1,0,1);
        if(toward.lengthSquared()<1e-6||Vec3d.fromPolar(p.getPitch(),p.getYaw()).multiply(1,0,1).normalize().dotProduct(toward.normalize())<.2)return false;
        boolean broken=e.state.guardHit(amount,now,source.isOf(UNARMED));
        if(broken){p.clearActiveItem();p.setSprinting(false);if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();}
        speed(p,e.state,now);sync(p,e.state);
        sound(p,broken?SoundEvents.ITEM_SHIELD_BREAK:SoundEvents.ITEM_SHIELD_BLOCK,.85f,broken?.8f:1.15f);
        Vec3d effect=p.getPos().add(0,1.15,0).add(Vec3d.fromPolar(p.getPitch(),p.getYaw()).multiply(.6));
        p.getServerWorld().spawnParticles(broken?ParticleTypes.CRIT:ParticleTypes.ELECTRIC_SPARK,effect.x,effect.y,effect.z,broken?24:9,.25,.22,.15,.07);
        return true;
    }
    private static void swing(ServerPlayerEntity p,CombatState s) {sound(p,SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,.23f,1.0f+(4-s.attack)*.13f);}
    private static void sound(ServerPlayerEntity p,SoundEvent sound,float volume,float pitch) {p.getWorld().playSound(null,p.getX(),p.getY(),p.getZ(),sound,SoundCategory.PLAYERS,volume,pitch);}
    private static void sync(ServerPlayerEntity p,CombatState s) {
        for(var viewer:p.getServerWorld().getPlayers())if(viewer==p||viewer.squaredDistanceTo(p)<128*128) {
            if(!ServerPlayNetworking.canSend(viewer,STATE))continue;
            var b=PacketByteBufs.create();b.writeUuid(p.getUuid());b.writeIdentifier(p.getWorld().getRegistryKey().getValue());
            b.writeInt(s.serial);b.writeByte(s.attack);b.writeLong(s.attackStart);b.writeBoolean(s.guarding);b.writeFloat(s.pressure);
            b.writeLong(s.brokenUntil);b.writeLong(s.dashStart);b.writeLong(s.dashReady);b.writeFloat(s.dashForward);b.writeFloat(s.dashSide);b.writeLong(s.combatUntil);
            // 2.60.0: when the guard broke, and the incapacitation (until when, since when)
            b.writeLong(s.brokenAt);b.writeLong(s.disabledUntil);b.writeLong(s.disabledAt);
            ServerPlayNetworking.send(viewer,STATE,b);
        }
    }
}
