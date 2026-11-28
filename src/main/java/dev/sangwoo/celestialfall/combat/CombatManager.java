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

/**
 * Validates input and traces each strike at its contact frame. Clients never select damage or targets.
 * <p>
 * (2.157.0) The combat engine's runtime for the actions everyone has: its input ({@code engine_input_v1}: the action,
 * its number, the world time the client showed), bare hands' strikes judged as the striker saw their targets (Rewind),
 * the perfect guard and the evasive (Defense), throws told once (Launch). The client predicts its own player by the
 * same combat state (EngineClient) and checks itself against the state this sends back, with the number of the last
 * input gone through.
 */
public final class CombatManager {
    public static final Identifier INPUT=dev.sangwoo.celestialfall.engine.EngineNet.INPUT, STATE=CelestialFall.id("botand_state_v1");
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
    /** Mobs staggered by a basic attack's blow (2.68.0), and the tick each stagger ends: they do nothing meanwhile. */
    private static final Map<LivingEntity,Long> MOB_STAGGERS=new WeakHashMap<>();
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
            if(buf.readableBytes()!=dev.sangwoo.celestialfall.engine.EngineNet.INPUT_BYTES)return;
            int action=buf.readUnsignedByte(),seq=buf.readUnsignedShort();long clientTick=buf.readLong();int a=buf.readByte(),b=buf.readByte();
            if(action>dev.sangwoo.celestialfall.engine.EngineNet.EVADE||Math.abs(a)>1||Math.abs(b)>1)return;
            server.execute(()->input(player,action,seq,clientTick,a,b));
        });
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            // (2.110.0) off the map, or in one's vanilla mode (VanillaMode): Minecraft's own attack
            if(!dev.sangwoo.celestialfall.map.MapGate.enabled(player))return dev.sangwoo.celestialfall.map.MapGate.botandItem(player.getStackInHand(hand))?ActionResult.FAIL:ActionResult.PASS;
            if(dev.sangwoo.celestialfall.stand.StandManager.kageno(player))return ActionResult.FAIL;
            // Lee So-sang: their sword's hits are the attack (2.77.0), whatever the hand holds
            if(dev.sangwoo.celestialfall.isosang.IsosangAttack.attacks(player)){
                if(player instanceof ServerPlayerEntity p)dev.sangwoo.celestialfall.isosang.IsosangAttack.request(p);
                return ActionResult.FAIL;
            }
            // X Chara: their knife's hits are the attack (2.114.0), whatever the hand holds
            if(dev.sangwoo.celestialfall.xchara.XCharaAttack.attacks(player)){
                if(player instanceof ServerPlayerEntity p)dev.sangwoo.celestialfall.xchara.XCharaAttack.request(p);
                return ActionResult.FAIL;
            }
            // Star Platinum out: its punches are the attack (2.54.0), whatever the hand holds
            if(dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punches(player)){
                if(player instanceof ServerPlayerEntity p)dev.sangwoo.celestialfall.stand.StarPlatinumAttack.request(p);
                return ActionResult.FAIL;
            }
            // (2.154.0) and Gold Experience's
            if(dev.sangwoo.celestialfall.stand.GoldExperienceAttack.punches(player)){
                if(player instanceof ServerPlayerEntity p)dev.sangwoo.celestialfall.stand.GoldExperienceAttack.request(p);
                return ActionResult.FAIL;
            }
            if(hand==Hand.MAIN_HAND&&bare(player)&&!player.isSpectator()) {
                // (Minecraft's own attack: the engine's input carries the press; this one is judged as things stand)
                if(player instanceof ServerPlayerEntity p)input(p,dev.sangwoo.celestialfall.engine.EngineNet.ATTACK,-1,-1,0,0);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(CombatManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{HitStun.disconnect(handler.player.getUuid());STATES.remove(handler.player.getUuid());RUN_AFTER.remove(handler.player.getUuid());dev.sangwoo.celestialfall.engine.EngineDebug.leave(handler.player.getUuid());dev.sangwoo.celestialfall.engine.EngineLag.set(handler.player.getUuid(),0);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{STATES.clear();HitStun.clear();RUN_AFTER.clear();MOB_SLOWS.clear();MOB_STAGGERS.clear();dev.sangwoo.celestialfall.engine.Rewind.clear();dev.sangwoo.celestialfall.engine.EngineDebug.clear();});
    }
    /** A sprint packet the action lock dropped ({@code start}) or any stop: the latest wish to run is kept for when the lock ends. */
    public static void runRequest(ServerPlayerEntity p,boolean start){if(start)RUN_AFTER.add(p.getUuid());else RUN_AFTER.remove(p.getUuid());}
    public static void leave(ServerPlayerEntity p) {
        HitStun.disconnect(p.getUuid());STATES.remove(p.getUuid());RUN_AFTER.remove(p.getUuid());
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr!=null)attr.removeModifier(SLOW);
    }
    /**
     * The player fights with their own fists: no Kageno, Star Platinum not out (its punches are the attack then, 2.54.0),
     * nor Gold Experience (2.154.0), not Lee So-sang (their sword is, 2.77.0), not X Chara (their knife is, 2.114.0),
     * hands empty.
     */
    public static boolean bare(PlayerEntity p) { return !dev.sangwoo.celestialfall.stand.StandManager.kageno(p)&&!dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punches(p)&&!dev.sangwoo.celestialfall.stand.GoldExperienceAttack.punches(p)&&!dev.sangwoo.celestialfall.stand.StandManager.isosang(p)&&!dev.sangwoo.celestialfall.stand.StandManager.xChara(p)&& p.getMainHandStack().isEmpty()&&p.getOffHandStack().isEmpty(); }
    /** In a cutscene or a cast that holds the player: Kageno's skills, the old items', and Star Platinum's beatdown's (2.64.0, its user and the one struck). */
    public static boolean cinematic(PlayerEntity p) { return dev.sangwoo.celestialfall.stand.KagenoManager.busy(p)||SpellManager.isCasting(p.getUuid())||AtomicManager.isCasting(p.getUuid())||HeavenManager.isCasting(p.getUuid())||dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown.involved(p)||dev.sangwoo.celestialfall.isosang.IsosangAttack.casting(p)||dev.sangwoo.celestialfall.xchara.XCharaUltimate.involved(p)||dev.sangwoo.celestialfall.samurai.SamuraiUltimate.involved(p); }
    private static boolean valid(ServerPlayerEntity p) {
        // (2.77.0: nor while a hit of Lee So-sang's sword holds them: no guard or dash then, IsosangRules.HOLD)
        return dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())&&p.isAlive()&&!p.isSpectator()&&!p.hasVehicle()&&!p.isSleeping()&&!p.isFallFlying()&&!p.isSwimming()&&!cinematic(p)&&!HitStun.stunned(p)
            &&!dev.sangwoo.celestialfall.isosang.IsosangAttack.busy(p)
            // (2.114.0: nor while a hit of X Chara's knife holds them; 2.119.0: nor their E)
            &&!dev.sangwoo.celestialfall.xchara.XCharaAttack.busy(p)&&!dev.sangwoo.celestialfall.xchara.XCharaHeal.busy(p)
            // (2.138.0: nor while their teleport is on its way)
            &&!dev.sangwoo.celestialfall.xchara.XCharaBlink.casting(p);
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
    /**
     * This player's combat state as it stands, for a boss to read (2.69.0: whether they swing, guard, dash, how long
     * they are held; null if they have none yet). Read it only: it is the server's own.
     */
    public static CombatState peek(PlayerEntity p) {
        var e=p==null?null:STATES.get(p.getUuid());
        return e==null||e.entityId!=p.getId()||!e.dimension.equals(p.getWorld().getRegistryKey().getValue())?null:e.state;
    }
    /** A boss's guard key (2.69.0, BossBrain): up while {@code hold} is sent at least every 16 ticks, down when it is not. */
    public static boolean guardInput(ServerPlayerEntity p,boolean hold) {return guardInput(p,hold,false);}
    /**
     * As {@link #guardInput(ServerPlayerEntity,boolean)}; {@code perfect}: a guard going up now opens a perfect window
     * (2.157.0, Defense: a boss raises its guard as a blow comes, and chooses when it means to take it perfectly).
     */
    public static boolean guardInput(ServerPlayerEntity p,boolean hold,boolean perfect) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(!hold){if(s.guard(false,now))sync(p,s);return false;}
        if(!valid(p)||p.isUsingItem())return false;
        if(s.guard(true,now,perfect))sync(p,s);
        return s.guarding;
    }
    /** A boss's evasive (2.157.0, BossBrain): as a client's, {@code forward} along the way it faces and {@code side} to its left. */
    public static boolean evadeInput(ServerPlayerEntity p,float forward,float side) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return false;
        var s=state(p);long now=p.getWorld().getTime();
        return evade(p,s,now,forward,side,false);
    }
    /** (2.157.0) Whether a boss's evasive is ready (its own cooldown; what holds it is checked as it is called). */
    public static boolean evadeReady(ServerPlayerEntity p){var e=STATES.get(p.getUuid());return e==null||p.getWorld().getTime()>=e.state.evadeReady;}
    /**
     * A boss's dash (2.69.0, BossBrain): {@code forward} along the way it faces and {@code side} to its left (a client's
     * dash keys give -1, 0 or 1 of each; any mix of the two goes, as the dash makes it one way).
     */
    public static boolean dashInput(ServerPlayerEntity p,float forward,float side) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(!valid(p)||p.isUsingItem()||p.isTouchingWater()||!s.dash(now,forward,side))return false;
        s.dashYaw=p.getYaw();p.setSprinting(false);sound(p,SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,.35f,1.25f);sync(p,s);
        return true;
    }
    public static boolean busy(PlayerEntity p) {return busyOtherwise(p)||dev.sangwoo.celestialfall.isosang.IsosangAttack.busy(p)||dev.sangwoo.celestialfall.xchara.XCharaAttack.busy(p)||dev.sangwoo.celestialfall.xchara.XCharaHeal.busy(p);}
    /**
     * {@link #busy} but for a hit of Lee So-sang's sword holding them (2.77.0, IsosangAttack.busy): the sword asks this
     * before its next hit, kept from a press while the last one holds them. (2.114.0) Nor for a hit of X Chara's knife
     * (XCharaAttack.busy), which asks it the same way.
     */
    public static boolean busyOtherwise(PlayerEntity p) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return false;
        if(HitStun.stunned(p)||dev.sangwoo.celestialfall.stand.KagenoManager.busy(p)||dev.sangwoo.celestialfall.stand.StarPlatinumRush.rushing(p)
            ||dev.sangwoo.celestialfall.stand.StarPlatinumInhale.inhaling(p)||dev.sangwoo.celestialfall.stand.GoldExperienceHeal.selfing(p)||dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown.involved(p)||dev.sangwoo.celestialfall.stand.StandArrow.stabbing(p)
            ||dev.sangwoo.celestialfall.xchara.XCharaUltimate.involved(p)||dev.sangwoo.celestialfall.samurai.SamuraiUltimate.involved(p))return true;
        // (2.138.0) X Chara's teleport on its way (the fifth of a second before it moves them): they may only walk
        if(dev.sangwoo.celestialfall.xchara.XCharaBlink.casting(p))return true;
        // (2.152.0) Gold Experience's H, its palm on the ground until the tree bursts up: they stand held
        if(dev.sangwoo.celestialfall.stand.GoldExperienceTree.holding(p))return true;
        // (2.109.0) glowing before a skill with a cutscene is cast (CutsceneGlow): they may only walk
        if(CutsceneGlow.glowing(p))return true;
        var e=STATES.get(p.getUuid());if(e==null)return false;
        // (2.157.0: and the evasive's dash)
        long n=p.getWorld().getTime();return e.state.locked(n)||e.state.attacking(n)||e.state.dashing(n)||e.state.evading(n)||e.state.guarding;
    }
    public static boolean actionLocked(PlayerEntity p) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return false;
        // (2.107.0) dead, waiting to be brought back (AutoRespawn): only their view moves, nothing they do goes through
        if(p.isDead())return true;
        if(HitStun.stunned(p)||dev.sangwoo.celestialfall.stand.KagenoManager.busy(p)||dev.sangwoo.celestialfall.isosang.IsosangAttack.busy(p)||dev.sangwoo.celestialfall.xchara.XCharaAttack.busy(p)||dev.sangwoo.celestialfall.xchara.XCharaHeal.busy(p)||dev.sangwoo.celestialfall.stand.StarPlatinumRush.rushing(p)
            ||dev.sangwoo.celestialfall.stand.StarPlatinumInhale.inhaling(p)||dev.sangwoo.celestialfall.stand.GoldExperienceHeal.selfing(p)||dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown.involved(p)||dev.sangwoo.celestialfall.stand.StandArrow.stabbing(p)
            ||CutsceneGlow.glowing(p)||dev.sangwoo.celestialfall.xchara.XCharaUltimate.involved(p)||dev.sangwoo.celestialfall.samurai.SamuraiUltimate.involved(p)
            ||dev.sangwoo.celestialfall.stand.GoldExperienceTree.holding(p))return true;
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
     * (2.157.0) Whether {@code e} is untouchable from its evasive now (Defense.EVADE_SAFE): no blow lands on it, and no
     * stagger, incapacitation, slow or hold takes it. Only players and bosses have one.
     */
    public static boolean untouchable(LivingEntity e) {
        if(!(e instanceof ServerPlayerEntity p)||!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return false;
        var x=STATES.get(p.getUuid());return x!=null&&x.entityId==p.getId()&&x.state.untouchable(p.getWorld().getTime());
    }
    /**
     * (2.157.0) Whether {@code p} may get out by the evasive, as far as what the combat state does not know of goes:
     * on the map, alive and free to move, and not held by a cutscene, a grab or stopped time (XCharaPull.caught: a
     * beatdown, a time stop, an ultimate, a clone's throw, a Kageno hold, a stand arrow), Star Platinum's inhale, nor
     * with their soul out of their body (Gold Experience's overdrive: "본체는 아무것도 못하고"). What the combat state
     * holds them by (a stagger, an incapacitation, a broken guard) and a barrage's hold it may get them out of.
     */
    public static boolean evadeValid(ServerPlayerEntity p) {
        return dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())&&p.isAlive()&&!p.isSpectator()&&!p.hasVehicle()&&!p.isSleeping()&&!p.isFallFlying()
            &&!cinematic(p)&&!dev.sangwoo.celestialfall.xchara.XCharaPull.caught(p)&&!dev.sangwoo.celestialfall.stand.StarPlatinumInhale.held(p)
            &&!dev.sangwoo.celestialfall.stand.GoldExperienceOverdrive.out(p);
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
        // (2.157.0) not one untouchable from their evasive
        if(untouchable(target))return;
        // stopped in Star Platinum's stopped time (2.67.0): it comes to them as time moves again
        if(dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.deferIncapacitate(target,ticks,slowTicks,slow))return;
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
        dev.sangwoo.celestialfall.stand.StarPlatinumAttack.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumRush.struck(p);dev.sangwoo.celestialfall.stand.GoldExperienceHeal.struck(p);
        dev.sangwoo.celestialfall.stand.StarPlatinumInhale.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.struck(p);
        dev.sangwoo.celestialfall.stand.KagenoManager.disable(p);dev.sangwoo.celestialfall.isosang.IsosangAttack.struck(p);dev.sangwoo.celestialfall.xchara.XCharaAttack.struck(p);dev.sangwoo.celestialfall.xchara.XCharaHeal.struck(p);
        p.clearActiveItem();p.setSprinting(false);if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        speed(p,s,now);sync(p,s);
    }
    /**
     * Slows {@code target}'s legs for {@code ticks}, {@code slow} slower (2.113.0: Lee So-sang's sword formation, its zone and
     * its first blow, IsosangRules.H_ZONE_SLOW and H_IMPACT_SLOW): only its legs, it is no incapacitation (whatever it does
     * goes on), the game's lens kept still as for every slow of the combat's (CombatClient.slowed). A stronger slow on it
     * stays as long as it lasts; one as strong is drawn out. A training dummy stands still anyway; a mob is slowed as an
     * incapacitation slows it.
     */
    public static void slow(LivingEntity target,int ticks,double slow) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld())||!target.isAlive()||ticks<=0||slow<=0)return;
        if(untouchable(target))return;
        if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity)return;
        long now=target.getWorld().getTime();
        if(!(target instanceof ServerPlayerEntity p)) {
            var attr=target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
            var held=MOB_SLOWS.get(target);var old=attr.getModifier(MOB_SLOW);boolean on=old!=null&&held!=null&&held>now;
            if(on&&-old.getValue()>slow+1e-9)return;
            boolean same=on&&Math.abs(old.getValue()+slow)<=1e-9;
            if(!same){if(old!=null)attr.removeModifier(MOB_SLOW);attr.addTemporaryModifier(new EntityAttributeModifier(MOB_SLOW,"Botand incapacitation",-slow,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));}
            MOB_SLOWS.put(target,Math.max(same?held:now,now+ticks));
            return;
        }
        // kept apart from the slows of blows (CombatState.slow): its zone's weaker slow, held each tick, must not draw out
        // its first blow's stronger one past its 3 s
        var s=state(p);
        s.extraSlow(now,ticks,slow);
        speed(p,s,now);
    }
    /**
     * Staggers whoever a basic attack's blow struck until {@code until} (2.68.0, {@link Stagger}): as an incapacitation,
     * only for that moment and with their legs {@link Stagger#SLOW} slower, without the dazed look. Whatever they were
     * doing is cut off as an incapacitation cuts it off. A training dummy stands still as long; a mob does nothing and
     * walks as slowly.
     */
    public static void stagger(LivingEntity target,long until) {stagger(target,until,true);}
    /**
     * As {@link #stagger(LivingEntity,long)}; {@code poise}: a boss held long enough lately may shrug it off (not when a
     * perfect guard turns it back, 2.157.0).
     */
    private static void stagger(LivingEntity target,long until,boolean poise) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld())||!target.isAlive())return;
        long now=target.getWorld().getTime();if(until<=now)return;
        if(untouchable(target))return;
        // a boss held down long enough lately shrugs it off for a moment (2.69.0, BossRules.Poise)
        if(poise&&dev.sangwoo.celestialfall.boss.Bosses.shrugsStagger(target))return;
        // stopped in Star Platinum's stopped time (2.67.0): it comes to them as time moves again
        if(dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.deferStagger(target,(int)(until-now)))return;
        if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dummy){dummy.stagger(until,Stagger.SLOW);return;}
        if(!(target instanceof ServerPlayerEntity p)) {
            MOB_STAGGERS.merge(target,until,Math::max);
            var attr=target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if(attr!=null){
                var held=MOB_SLOWS.get(target);
                if(held==null||held<until){attr.removeModifier(MOB_SLOW);attr.addTemporaryModifier(new EntityAttributeModifier(MOB_SLOW,"Botand incapacitation",-Stagger.SLOW,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));MOB_SLOWS.put(target,until);}
            }
            if(target instanceof net.minecraft.entity.mob.MobEntity mob)mob.getNavigation().stop();
            return;
        }
        var s=state(p);
        s.stagger(now,until,Stagger.SLOW);
        dev.sangwoo.celestialfall.stand.StarPlatinumAttack.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumRush.struck(p);dev.sangwoo.celestialfall.stand.GoldExperienceHeal.struck(p);
        dev.sangwoo.celestialfall.stand.StarPlatinumInhale.struck(p);dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.struck(p);
        dev.sangwoo.celestialfall.stand.KagenoManager.disable(p);dev.sangwoo.celestialfall.isosang.IsosangAttack.struck(p);dev.sangwoo.celestialfall.xchara.XCharaAttack.struck(p);dev.sangwoo.celestialfall.xchara.XCharaHeal.struck(p);
        p.clearActiveItem();p.setSprinting(false);if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        speed(p,s,now);sync(p,s);
    }
    /** Whether a basic attack's blow staggers this mob or player now (2.68.0). */
    public static boolean staggered(LivingEntity e) {
        if(e.getWorld().isClient||!dev.sangwoo.celestialfall.map.MapGate.enabled(e.getWorld()))return false;
        long now=e.getWorld().getTime();
        if(e instanceof ServerPlayerEntity p){var x=STATES.get(p.getUuid());return x!=null&&x.entityId==p.getId()&&x.state.staggered(now);}
        var until=MOB_STAGGERS.get(e);return until!=null&&until>now;
    }
    /**
     * The incapacitation (and the slow) that was to last until {@code until} ends at {@code to} instead (2.61.0: Star
     * Platinum's inhale let go of {@code target} early, its user struck; 2.62.0: whom it held stay incapacitated a
     * little after it lets go): one that lasts longer stays.
     */
    public static void cut(LivingEntity target,long until,long to) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld()))return;
        if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dummy){dummy.cut(until,to);return;}
        if(!(target instanceof ServerPlayerEntity p)) {
            // a mob's slow (it is only slowed): the one put on until then ends at to instead
            var left=MOB_SLOWS.get(target);if(left!=null&&left==until&&to<until)MOB_SLOWS.put(target,Math.max(to,target.getWorld().getTime()));
            return;
        }
        var e=STATES.get(p.getUuid());if(e==null)return;
        long now=p.getWorld().getTime();if(e.state.cut(now,until,to)){speed(p,e.state,now);sync(p,e.state);}
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
        if(untouchable(target))return;
        if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dummy){dummy.interruptCombat();return;}
        if(!(target instanceof ServerPlayerEntity p))return;
        long now=p.getWorld().getTime();var s=state(p);s.cancel(now);s.combatUntil=now+100;sync(p,s);
    }
    /**
     * (2.157.0) Turns back whoever a perfect guard of {@code by}'s took a blow from (Defense): staggered
     * Defense.PARRY_TICKS, what they were doing cut off, a boss's poise no help; every viewer draws them recoiling.
     */
    public static void parry(LivingEntity striker,LivingEntity by) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(striker.getWorld())||!striker.isAlive())return;
        long until=striker.getWorld().getTime()+dev.sangwoo.celestialfall.engine.Defense.PARRY_TICKS;
        stagger(striker,until,false);
        if(!untouchable(striker)&&!dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopped(striker))HitStun.parried(by,striker,until);
    }
    /**
     * The client's input (2.157.0: the engine's): {@code action} (EngineNet), its number {@code seq} (-1: none, Minecraft's
     * own attack), the world time its client showed as it was pressed ({@code clientTick}, -1 none: for the lag
     * compensation), and the two ways pressed for a dash or evasive. The client hears back how it went: the state with
     * the number of this input.
     */
    private static void input(ServerPlayerEntity p,int action,int seq,long clientTick,int a,int b) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())){leave(p);return;}
        var s=state(p);long now=p.getWorld().getTime();
        boolean changed=act(p,s,now,action,clientTick,a,b);
        if(seq>=0){
            s.ackSeq=seq;
            // whether it went through: a press kept counts; a guard asked for is as asked
            s.ackOk=action==dev.sangwoo.celestialfall.engine.EngineNet.GUARD_UP?s.guarding:action==dev.sangwoo.celestialfall.engine.EngineNet.GUARD_DOWN?!s.guarding
                :action==dev.sangwoo.celestialfall.engine.EngineNet.ATTACK?changed||s.queued:changed;
        }
        // (told whether anything changed or not: the client's prediction checks itself against it)
        if(changed||seq>=0)sync(p,s);
    }
    private static boolean act(ServerPlayerEntity p,CombatState s,long now,int action,long clientTick,int a,int b) {
        if(action==dev.sangwoo.celestialfall.engine.EngineNet.GUARD_DOWN) return s.guard(false,now);
        // the evasive is for what holds them: its own checks (evadeValid)
        if(action==dev.sangwoo.celestialfall.engine.EngineNet.EVADE) return evade(p,s,now,a,b,ownClient(p));
        if(!valid(p)||p.isUsingItem())return false;
        if(action==dev.sangwoo.celestialfall.engine.EngineNet.ATTACK && bare(p)) {
            boolean queued=s.queued;
            if(s.requestAttack(now)) {
                // its targets are judged where this player saw them (Rewind)
                s.seen=dev.sangwoo.celestialfall.engine.Rewind.offset(now,clientTick);
                p.resetLastAttackedTicks();swing(p,s);return true;
            }
            if(s.queued&&!queued){s.seen=dev.sangwoo.celestialfall.engine.Rewind.offset(now,clientTick);return true;}
            return false;
        }
        if(action==dev.sangwoo.celestialfall.engine.EngineNet.GUARD_UP) return s.guard(true,now);
        if(action==dev.sangwoo.celestialfall.engine.EngineNet.DASH && !p.isTouchingWater()&&s.dash(now,a,b)) {
            // (2.157.0) a client of the engine's runs its dash itself as it predicts it: the server pushes none
            s.dashYaw=p.getYaw();s.driven=ownClient(p);p.setSprinting(false);soundFor(p,SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,.35f,1.25f);return true;
        }
        return false;
    }
    /** (2.157.0) Whether this player's client runs the engine (predicts its own moves): a boss has none. */
    private static boolean ownClient(ServerPlayerEntity p){return ServerPlayNetworking.canSend(p,dev.sangwoo.celestialfall.engine.EngineNet.EVENT);}
    /**
     * (2.157.0) The evasive (Defense): out of what holds them and away, untouchable a moment; {@code driven}: their
     * client runs the dash (else the server pushes it). Heard and seen by everyone near.
     */
    private static boolean evade(ServerPlayerEntity p,CombatState s,long now,float forward,float side,boolean driven) {
        if(!evadeValid(p))return false;
        boolean held=HitStun.stunned(p);
        if(!s.evade(now,forward,side,held))return false;
        if(held)HitStun.release(p.getUuid());
        s.evadeYaw=p.getYaw();s.driven=driven;
        p.clearActiveItem();p.setSprinting(false);if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        speed(p,s,now);sync(p,s);
        soundFor(p,SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE,.7f,1.35f);soundFor(p,SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,.45f,1.55f);
        dev.sangwoo.celestialfall.engine.EngineNet.evaded(p,now,p.getPos(),s.evadeYaw,s.evadeForward,s.evadeSide);
        return true;
    }
    private static void tick(MinecraftServer server) {
        // (2.157.0) where everything near a player stands this tick, for blows judged as their striker saw it (Rewind)
        dev.sangwoo.celestialfall.engine.Rewind.record(server,dev.sangwoo.celestialfall.boss.Bosses.withBosses(server.getPlayerManager().getPlayerList()));
        // (the bosses too, 2.69.0: their guard, dash and what holds them run as a player's)
        for(var p:dev.sangwoo.celestialfall.boss.Bosses.withBosses(server.getPlayerManager().getPlayerList())) {
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
                // (2.157.0) a client of the engine's runs its own dash: nothing pushed (its particles still go up here)
                else if(s.driven){if((now-s.dashStart)%2==0)p.getServerWorld().spawnParticles(ParticleTypes.CLOUD,p.getX(),p.getY()+.1,p.getZ(),2,.13,.02,.13,.005);}
                else {
                    double yaw=Math.toRadians(s.dashYaw),v=CombatState.dashSpeed((int)(now-s.dashStart));
                    Vec3d direction=new Vec3d(-Math.sin(yaw)*s.dashForward+Math.cos(yaw)*s.dashSide,0,Math.cos(yaw)*s.dashForward+Math.sin(yaw)*s.dashSide);
                    p.setVelocity(direction.multiply(v).add(0,p.getVelocity().y,0));p.velocityModified=true;
                    p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                    if((now-s.dashStart)%2==0)p.getServerWorld().spawnParticles(ParticleTypes.CLOUD,p.getX(),p.getY()+.1,p.getZ(),2,.13,.02,.13,.005);
                }
            }
            // (2.157.0) the evasive's dash: a boss's pushed here (a client of the engine's runs its own); stopped by a wall
            if(s.evading(now)&&!s.driven&&!p.horizontalCollision) {
                double yaw=Math.toRadians(s.evadeYaw),v=dev.sangwoo.celestialfall.engine.Defense.evadeSpeed((int)(now-s.evadeStart));
                Vec3d direction=new Vec3d(-Math.sin(yaw)*s.evadeForward+Math.cos(yaw)*s.evadeSide,0,Math.cos(yaw)*s.evadeForward+Math.sin(yaw)*s.evadeSide);
                p.setVelocity(direction.multiply(v).add(0,p.getVelocity().y,0));p.velocityModified=true;
                p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            }
            if(s.evading(now-1)&&!s.evading(now))dirty=true;
            // an incapacitation over (2.60.0), or a stagger (2.68.0): told at once, so the client lets its keys go on the
            // tick it ends
            if(s.disabled(now-1)&&!s.disabled(now)||s.staggered(now-1)&&!s.staggered(now))dirty=true;
            if(s.locked(now)){p.clearActiveItem();p.setSprinting(false);}
            speed(p,s,now);
            if(RUN_AFTER.contains(p.getUuid())&&!actionLocked(p)){RUN_AFTER.remove(p.getUuid());if(valid(p)&&!s.guarding&&!s.locked(now))p.setSprinting(true);}
            if(dirty||now%4==0&&(s.pressure>0||s.combatUntil>now||s.locked(now)))sync(p,s);
        }
        HitStun.tick();
        // mobs' slows from incapacitating blows run out (2.60.0), and their staggers (2.68.0)
        MOB_STAGGERS.entrySet().removeIf(e->e.getKey().isRemoved()||!e.getKey().isAlive()||e.getKey().getWorld().getTime()>=e.getValue());
        for(var it=MOB_SLOWS.entrySet().iterator();it.hasNext();){
            var e=it.next();var mob=e.getKey();
            if(mob.isRemoved()||!mob.isAlive()||mob.getWorld().getTime()>=e.getValue()){var attr=mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr!=null)attr.removeModifier(MOB_SLOW);it.remove();}
        }
    }
    private static void speed(ServerPlayerEntity p,CombatState s,long now) {
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
        // the strongest of what slows them: a broken guard, an incapacitating blow (2.60.0), the guard
        // (2.113.0) and what only slows (Lee So-sang's sword formation, CombatState.extraSlow)
        double slow=Math.min(Math.min(s.broken(now)?-.9:0,Math.min(s.slowed(now)?-s.slow:0,s.guarding?-.55:0)),-s.extraSlow(now));
        var old=attr.getModifier(SLOW);
        if(old!=null&&old.getValue()==slow)return;
        if(old!=null)attr.removeModifier(SLOW);
        if(slow!=0)attr.addTemporaryModifier(new EntityAttributeModifier(SLOW,"Botand combat movement",slow,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        if(slow!=0)p.setSprinting(false);
    }
    private static void hit(ServerPlayerEntity p,CombatState s) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return;
        int strike=s.attack;
        // (2.157.0) the strike as the engine's chain has it (Moves.BARE): its ray, judged against where its targets stood
        // as this player saw them, wound back s.seen ticks (Rewind)
        var move=dev.sangwoo.celestialfall.engine.Moves.BARE.move(strike);var shape=(dev.sangwoo.celestialfall.engine.Hitbox.Ray)move.first().shape();
        var world=p.getServerWorld();long now=world.getTime(),then=now-s.seen;
        Vec3d eye=p.getEyePos(),end=eye.add(Vec3d.fromPolar(p.getPitch(),p.getYaw()).multiply(shape.reach()));
        var wall=world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getPos();
        LivingEntity target=null;double distance=eye.distanceTo(end);
        // (those it may meet: near the ray now, or near it where they stood then; a body goes at most a few blocks in the ticks wound back)
        List<LivingEntity> judged=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,p.getBoundingBox().stretch(end.subtract(eye)).expand(1+.6*s.seen),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit())) {
            if(e instanceof PlayerEntity other && !p.shouldDamagePlayer(other)||!p.canSee(e))continue;
            judged.add(e);
            var along=dev.sangwoo.celestialfall.engine.Hitbox.along(shape,eye,end,dev.sangwoo.celestialfall.engine.Rewind.boxAt(e,then));
            if(along.isPresent()&&along.getAsDouble()<=distance) {target=e;distance=along.getAsDouble();}
        }
        dev.sangwoo.celestialfall.engine.EngineDebug.ray(p,dev.sangwoo.celestialfall.engine.Moves.number(move),0,now,s.seen,eye,end,shape.pad(),target,judged);
        distance*=distance;
        // (2.151.0) Gold Experience's fly hovering in front of whom it belongs to meets their punch on the way, as far as
        // whom it would strike (GoldExperienceRules.swatWay: a body's width at either hand, not this punch's own line)
        Vec3d reached=target==null?end:eye.add(end.subtract(eye).normalize().multiply(Math.sqrt(distance)));
        if(dev.sangwoo.celestialfall.stand.GoldExperienceLife.swatsAlong(p,end.subtract(eye),Math.hypot(reached.x-eye.x,reached.z-eye.z)))return;
        if(target==null)return;
        var source=new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(UNARMED),p);
        boolean damaged=damageWithoutKnockback(target,source,CombatState.DAMAGE[strike]);
        if(!damaged)return;
        // the blow staggers them a moment (2.68.0, Stagger: until the next strike would land at full speed)
        if(s.attack==strike&&p.isAlive())HitStun.landed(p,s,target);
        if(move.first().knock()>0) {
            // (2.157.0) told once (Launch): it was sent a second time here besides, and a client that moved between was thrown twice
            double yaw=Math.toRadians(p.getYaw());dev.sangwoo.celestialfall.engine.Launch.knock(target,move.first().knock(),Math.sin(yaw),-Math.cos(yaw));
        }
        p.onAttacking(target);p.addExhaustion(.1f);
        // (heard by the striker too: whether it landed is the server's to say)
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
        if(untouchable(target))return false;
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
    /**
     * (2.134.0) Glitches whoever X Chara's great X slash struck: for {@code ticks} they can raise no guard (theirs, if up,
     * goes down now); they walk, attack, dash and cast as ever. A training dummy the same; anything else has no guard.
     */
    public static void seal(LivingEntity target,int ticks) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(target.getWorld())||!target.isAlive())return;
        if(untouchable(target))return;
        if(target instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dummy){dummy.seal(ticks);return;}
        if(!(target instanceof ServerPlayerEntity p))return;
        long now=p.getWorld().getTime();var s=state(p);
        if(s.seal(now,ticks)){speed(p,s,now);sync(p,s);}
    }
    /** (2.134.0) Until when this player is glitched and can raise no guard (0 if never). */
    public static long sealedUntil(PlayerEntity p) {var e=p==null?null:STATES.get(p.getUuid());return e==null||e.entityId!=p.getId()?0:e.state.sealedUntil;}
    /** Whether this player holds their guard up now (and it is not broken). */
    public static boolean guarding(ServerPlayerEntity p) {var e=STATES.get(p.getUuid());return e!=null&&e.state.guarding&&!e.state.broken(p.getWorld().getTime());}
    public static boolean block(ServerPlayerEntity p,DamageSource source,float amount) {
        if(!Float.isFinite(amount)||amount<=0||p.isInvulnerableTo(source)||!valid(p))return false;
        var e=STATES.get(p.getUuid());long now=p.getWorld().getTime();
        if(e==null||!e.state.guarding||e.state.broken(now)||source.isIn(DamageTypeTags.BYPASSES_SHIELD)||source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        Vec3d origin=source.getPosition();if(origin==null)return false;
        Vec3d toward=origin.subtract(p.getPos()).multiply(1,0,1);
        if(toward.lengthSquared()<1e-6||Vec3d.fromPolar(p.getPitch(),p.getYaw()).multiply(1,0,1).normalize().dotProduct(toward.normalize())<.2)return false;
        // (2.157.0) a guard just raised takes it perfectly (Defense): nothing on the guard, and its striker turned back
        if(e.state.perfect(now)){perfect(p,e.state,source,now);return true;}
        boolean broken=e.state.guardHit(amount,now,source.isOf(UNARMED));
        if(broken){p.clearActiveItem();p.setSprinting(false);if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();}
        speed(p,e.state,now);sync(p,e.state);
        sound(p,broken?SoundEvents.ITEM_SHIELD_BREAK:SoundEvents.ITEM_SHIELD_BLOCK,.85f,broken?.8f:1.15f);
        Vec3d effect=p.getPos().add(0,1.15,0).add(Vec3d.fromPolar(p.getPitch(),p.getYaw()).multiply(.6));
        p.getServerWorld().spawnParticles(broken?ParticleTypes.CRIT:ParticleTypes.ELECTRIC_SPARK,effect.x,effect.y,effect.z,broken?24:9,.25,.22,.15,.07);
        return true;
    }
    /**
     * (2.157.0) A perfect guard of {@code p}'s took a blow (Defense): its pressure eased, its striker turned back if near
     * (Defense.parries), a bright clang heard by everyone near and the guard's flash seen (EngineNet.PERFECT, DefenseFx).
     */
    private static void perfect(ServerPlayerEntity p,CombatState s,DamageSource source,long now) {
        s.perfectHit(now);speed(p,s,now);sync(p,s);
        LivingEntity striker=source.getAttacker() instanceof LivingEntity l&&l!=p&&l.isAlive()?l:null;
        boolean parried=striker!=null&&striker.getWorld()==p.getWorld()&&dev.sangwoo.celestialfall.engine.Defense.parries(striker.getPos().distanceTo(p.getPos()));
        if(parried)parry(striker,p);
        Vec3d at=p.getPos().add(0,1.2,0).add(Vec3d.fromPolar(0,p.getYaw()).multiply(.55));
        var world=p.getServerWorld();
        world.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.ITEM_SHIELD_BLOCK,SoundCategory.PLAYERS,1f,1.6f);
        world.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.BLOCK_ANVIL_LAND,SoundCategory.PLAYERS,.3f,2f);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,at.x,at.y,at.z,16,.2,.2,.2,.18);
        dev.sangwoo.celestialfall.engine.EngineNet.perfect(p,striker,parried,at,now);
    }
    private static void swing(ServerPlayerEntity p,CombatState s) {soundFor(p,SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,.23f,1.0f+(4-s.attack)*.13f);}
    private static void sound(ServerPlayerEntity p,SoundEvent sound,float volume,float pitch) {p.getWorld().playSound(null,p.getX(),p.getY(),p.getZ(),sound,SoundCategory.PLAYERS,volume,pitch);}
    /**
     * (2.157.0) A sound of {@code p}'s own move: for everyone near but them when their client runs the engine (it played it
     * as it predicted the move, EngineClient), for everyone when it does not (a boss).
     */
    private static void soundFor(ServerPlayerEntity p,SoundEvent sound,float volume,float pitch) {p.getWorld().playSound(ownClient(p)?p:null,p.getX(),p.getY(),p.getZ(),sound,SoundCategory.PLAYERS,volume,pitch);}
    private static void sync(ServerPlayerEntity p,CombatState s) {
        for(var viewer:p.getServerWorld().getPlayers())if(viewer==p||viewer.squaredDistanceTo(p)<128*128) {
            if(!ServerPlayNetworking.canSend(viewer,STATE))continue;
            var b=PacketByteBufs.create();b.writeUuid(p.getUuid());b.writeIdentifier(p.getWorld().getRegistryKey().getValue());
            b.writeInt(s.serial);b.writeByte(s.attack);b.writeLong(s.attackStart);b.writeBoolean(s.guarding);b.writeFloat(s.pressure);
            b.writeLong(s.brokenUntil);b.writeLong(s.dashStart);b.writeLong(s.dashReady);b.writeFloat(s.dashForward);b.writeFloat(s.dashSide);b.writeLong(s.combatUntil);
            // 2.60.0: when the guard broke, and the incapacitation (until when, since when); 2.68.0: the stagger's end
            b.writeLong(s.brokenAt);b.writeLong(s.disabledUntil);b.writeLong(s.disabledAt);b.writeLong(s.staggerUntil);
            // 2.134.0: until when they are glitched and can raise no guard (X Chara's great X slash)
            b.writeLong(s.sealedUntil);
            // 2.157.0: the engine's: the guard's going up and its perfect window, the evasive, the last input gone through
            b.writeLong(s.guardUpAt);b.writeLong(s.perfectAt);b.writeLong(s.evadeStart);b.writeLong(s.evadeReady);
            b.writeFloat(s.evadeForward);b.writeFloat(s.evadeSide);b.writeInt(viewer==p?s.ackSeq:-1);b.writeBoolean(viewer==p&&s.ackOk);
            ServerPlayNetworking.send(viewer,STATE,b);
        }
    }
}
