package dev.sangwoo.botandverify;

import dev.sangwoo.celestialfall.training.*;
import dev.sangwoo.celestialfall.combat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.Registries;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.*;
import java.util.*;
import static dev.sangwoo.botandverify.CombatSmoke.*;

/** Uses registered entities and actual server damage, movement and command code. */
public final class DummySmoke {
    public static void run(MinecraftServer server,Map<String,Object> checks)throws Exception {
        var world=server.getOverworld();var props=(net.minecraft.world.level.ServerWorldProperties)world.getLevelProperties();
        long original=world.getTime(),start=original+1000;List<TrainingDummyEntity> spawned=new ArrayList<>();
        var tick=TrainingDummyEntity.class.getDeclaredMethod("mobTick");tick.setAccessible(true);
        var strike=TrainingDummyEntity.class.getDeclaredMethod("strikeTarget",net.minecraft.entity.LivingEntity.class);strike.setAccessible(true);
        var p=player(server,"DummyTester",24,0);var defender=player(server,"DummyDefender",28,-2);
        try {
            HitStun.clear();props.setTime(start);
            for(int x=19;x<=36;x++)for(int z=-7;z<=10;z++)world.setBlockState(new BlockPos(x,100,z),Blocks.STONE.getDefaultState());
            Map<DummyKind,TrainingDummyEntity> dummies=new EnumMap<>(DummyKind.class);int index=0;
            for(var kind:DummyKind.values()) {
                var d=TrainingDummies.TYPES.get(kind).create(world);require(d!=null,"Create "+kind);d.setPosition(20+index++*4,101,4);
                world.spawnEntity(d);spawned.add(d);dummies.put(kind,d);
                require(d.getHealth()==kind.health&&d.getMaxHealth()==kind.health,"Wrong health "+kind);
                require(d instanceof Monster,"Atomic/Heaven must hit dummy");require(d.cannotDespawn()&&!d.canImmediatelyDespawn(100000),"Despawn "+kind);
                require(TrainingDummies.EGGS.get(kind) instanceof SpawnEggItem,"Missing egg "+kind);
                require(Registries.ENTITY_TYPE.getId(d.getType()).getPath().equals(kind.id+"_dummy"),"Wrong entity ID");
                tick.invoke(d);require(d.kind()==DummyKind.ATTACK||d.getNavigation().isIdle(),"Stationary dummy navigated");
            }
            checks.put("dummy_health_200_200_200_2000",true);checks.put("four_registered_eggs_and_monster_skill_targets",true);
            var guard=dummies.get(DummyKind.GUARD);guard.setPosition(24,101,-2);guard.setYaw(0);guard.setHeadYaw(0);tick.invoke(guard);
            var punchSource=new net.minecraft.entity.damage.DamageSource(world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).entryOf(CombatManager.UNARMED),p);
            for(int hit=1;hit<=4;hit++) {
                guard.damage(punchSource,CombatState.DAMAGE[hit]);
                require(guard.guardPressure()==25*hit&&guard.combatState().broken(start)==(hit==4),"Guard dummy fourth-hit threshold "+hit);
            }
            checks.put("guard_dummy_breaks_on_fourth_basic_hit",true);
            require(guard.getHealth()==200&&guard.combatState().broken(start),"Dummy frontal guard/break");
            require(Math.abs(guard.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)/.23-.1)<1e-5,"Dummy break lacks 90% slow");
            props.setTime(start+19);tick.invoke(guard);require(!guard.guarding(),"Guard recovered early");
            props.setTime(start+20);tick.invoke(guard);require(guard.guarding()&&guard.guardPressure()==0,"Guard did not resume at 20 ticks");
            guard.resetTraining();tick.invoke(guard);p.setPosition(24,101,-4);guard.timeUntilRegen=0;
            require(guard.damage(world.getDamageSources().playerAttack(p),4)&&guard.getHealth()==196,"Rear incorrectly blocked");
            checks.put("dummy_directional_guard_break_20_ticks_90_percent_slow",true);
            guard.setPosition(20,101,8);p.setPosition(32,101,0);
            var combo=dummies.get(DummyKind.COMBO);combo.setPosition(32,101,-2);
            var hitMethod=CombatManager.class.getDeclaredMethod("hit",net.minecraft.server.network.ServerPlayerEntity.class,CombatState.class);hitMethod.setAccessible(true);
            var state=new CombatState();props.setTime(start+100);state.requestAttack(start+100);
            List<Long> contacts=new ArrayList<>();
            for(int t=0;t<=40;t++) {
                long now=start+100+t;props.setTime(now);state.tick(now);if(t%4==0)state.requestAttack(now);
                if(state.hitDue(now)){combo.setVelocity(Vec3d.ZERO);hitMethod.invoke(null,p,state);contacts.add((long)t);if(state.attack<4)require(combo.getVelocity().lengthSquared()<1e-8,"Dummy early KB");}
                if(t>=3&&t<40)require(HitStun.stunned(combo),"Dummy escaped combo "+t);
                HitStun.tick();
            }
            require(contacts.equals(List.of(3L,14L,26L,40L))&&combo.getHealth()==1984,"Combo dummy damage/timing");
            require(!HitStun.stunned(combo)&&combo.getVelocity().horizontalLengthSquared()>.1,"Dummy fourth KB");
            var saved=new NbtCompound();combo.writeNbt(saved);var loaded=TrainingDummies.TYPES.get(DummyKind.COMBO).create(world);loaded.readNbt(saved);
            require(loaded.kind()==DummyKind.COMBO&&loaded.getMaxHealth()==2000&&loaded.getHealth()==1984,"Combo persistence");
            combo.resetTraining();require(combo.getHealth()==2000&&!HitStun.stunned(combo)&&combo.getVelocity().lengthSquared()==0,"Combo reset");
            checks.put("player_four_hit_combo_on_dummy_and_2000hp_reset",true);checks.put("dummy_type_health_persist_through_nbt",true);

            var attacker=dummies.get(DummyKind.ATTACK);attacker.setPosition(28,101,0);attacker.setYaw(180);attacker.setHeadYaw(180);attacker.resetTraining();
            defender.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);defender.setHealth(100);world.onPlayerConnected(defender);
            props.setTime(start+200);attacker.resetTraining(); // Cooldown ends at +220.
            List<Long> enemyContacts=new ArrayList<>();
            for(int t=0;t<=73;t++) {
                props.setTime(start+200+t);float before=defender.getHealth();tick.invoke(attacker);
                if(defender.getHealth()!=before)enemyContacts.add((long)t);
                if(t>=23&&t<60)require(HitStun.stunned(defender),"Player escaped dummy combo "+t);
                if(t>23&&t<60)require(defender.getVelocity().horizontalLengthSquared()<1e-8,"Dummy early knockback");
                HitStun.tick();
            }
            require(enemyContacts.equals(List.of(23L,34L,46L,60L)),"Dummy attack contacts "+enemyContacts);
            require(defender.getHealth()==84&&!HitStun.stunned(defender),"Dummy 4-hit player damage or final release");
            require(defender.getVelocity().horizontalLengthSquared()>.1,"Dummy fourth no knockback");
            checks.put("attacking_dummy_contacts",enemyContacts);checks.put("attacking_dummy_shared_true_combo_and_pause",true);
            // No attacks through walls, or while interrupted.
            attacker.resetTraining();attacker.combatState().attack=1;defender.setHealth(100);
            world.setBlockState(new BlockPos(28,102,-1),Blocks.STONE.getDefaultState());world.setBlockState(new BlockPos(27,102,-1),Blocks.STONE.getDefaultState());
            strike.invoke(attacker,defender);require(defender.getHealth()==100,"Dummy hit through wall");
            world.setBlockState(new BlockPos(28,102,-1),Blocks.AIR.getDefaultState());world.setBlockState(new BlockPos(27,102,-1),Blocks.AIR.getDefaultState());
            attacker.damage(world.getDamageSources().playerAttack(p),1);require(attacker.combatState().attack==0,"Hit failed to cancel dummy attack");
            checks.put("dummy_wall_and_damage_interrupt",true);

            // A normal entity tick must run AI for the creative player used to test skills.
            HitStun.clear();props.setTime(start+400);attacker.resetTraining();
            defender.changeGameMode(net.minecraft.world.GameMode.CREATIVE);defender.setPosition(28,101,-2);defender.setHealth(100);
            attacker.setPosition(28,101,0);attacker.setYaw(180);attacker.setHeadYaw(180);attacker.setVelocity(Vec3d.ZERO);attacker.setOnGround(true);
            boolean swung=false;
            for(int t=0;t<27;t++) {
                props.setTime(start+420+t);attacker.tick();
                if(attacker.strike()>0)swung=true;
            }
            require(attacker.getTarget()==defender&&swung,"Full entity tick failed to attack creative player");
            require(defender.getHealth()==100&&!HitStun.stunned(defender),"Creative invulnerability changed");
            defender.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);props.setTime(start+450);attacker.tick();
            require(attacker.getTarget()==null&&attacker.strike()==0,"Dummy targets spectator or leaves old target");
            checks.put("dummy_full_entity_tick_attacks_creative_preserves_invulnerability",true);
            checks.put("dummy_ignores_spectators_and_clears_target",true);

            // Targets outside reach must never cause navigation, sliding or pursuit.
            defender.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);defender.setHealth(100);defender.setPosition(28,101,-6);
            attacker.resetTraining();attacker.setPosition(28,101,0);Vec3d fixed=attacker.getPos();
            for(int t=0;t<80;t++) {
                props.setTime(start+500+t);
                attacker.setVelocity(.6,attacker.getVelocity().y,.4);attacker.takeKnockback(1,1,1);
                attacker.tick();
                require(attacker.getX()==fixed.x&&attacker.getZ()==fixed.z,"Attack dummy moved horizontally "+t);
                require(attacker.getNavigation().isIdle()&&attacker.strike()==0,"Out-of-range dummy navigates or swings");
            }
            require(defender.getHealth()==100&&!attacker.isPushable(),"Stationary dummy damage/push policy");
            attacker.move(net.minecraft.entity.MovementType.PISTON,new Vec3d(1,0,1));
            require(attacker.getX()==fixed.x&&attacker.getZ()==fixed.z,"External movement displaced attack dummy");
            checks.put("attack_dummy_stationary_80_full_ticks_no_pursuit_push_or_knockback",true);

            var source=server.getCommandSource().withPosition(new Vec3d(28,101,6)).withRotation(new Vec2f(0,0));
            var dispatcher=server.getCommandManager().getDispatcher();
            require(dispatcher.execute("botand dummy idle",source)==1,"Alias summon command failed");
            require(dispatcher.execute("botand dummy reset",source)>=4,"Reset command failed");
            require(dispatcher.execute("botand dummy clear",source)>=4,"Clear command failed");
            checks.put("dummy_spawn_reset_clear_commands",true);
        } finally {
            for(var d:spawned)d.discard();world.removePlayer(defender,Entity.RemovalReason.DISCARDED);HitStun.clear();props.setTime(original);
        }
    }
}
