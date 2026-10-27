package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.combat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.block.Blocks;
import java.util.*;
import static dev.sangwoo.botandverify.CombatSmoke.*;

/** Real Minecraft movement, damage and packet handlers under a manually advanced server clock. */
public final class ComboSmoke {
    public static void run(MinecraftServer server,Map<String,Object> checks)throws Exception {
        var world=server.getOverworld();var props=(net.minecraft.world.level.ServerWorldProperties)world.getLevelProperties();long original=world.getTime();
        var getState=CombatManager.class.getDeclaredMethod("state",ServerPlayerEntity.class);getState.setAccessible(true);
        var hit=CombatManager.class.getDeclaredMethod("hit",ServerPlayerEntity.class,CombatState.class);hit.setAccessible(true);
        var input=CombatManager.class.getDeclaredMethod("input",ServerPlayerEntity.class,int.class,int.class,int.class);input.setAccessible(true);
        try {
            HitStun.clear();
            var attacker=player(server,"ComboAttacker",0,0);var defender=player(server,"ComboDefender",0,-2);
            defender.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);defender.setHealth(100);
            var monster=EntityType.ZOMBIE.create(world);monster.setPosition(2,101,-2);monster.setAiDisabled(false);
            monster.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);monster.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(0);monster.setHealth(100);world.spawnEntity(monster);
            // Player fixtures must be registered in the world so first-hit ray selection sees them.
            world.onPlayerConnected(defender);
            CombatState attack=(CombatState)getState.invoke(null,attacker),victim=(CombatState)getState.invoke(null,defender);
            long start=original+100;props.setTime(start);attack.requestAttack(start);List<Long> contacts=new ArrayList<>();
            for(int tick=0;tick<=40;tick++) {
                long now=start+tick;props.setTime(now);attack.tick(now);
                if(tick%4==0)attack.requestAttack(now);
                if(tick>=3&&tick<40&&tick!=3)require(HitStun.stunned(defender),"Gap before hit at tick "+tick);
                if(attack.hitDue(now)) {
                    if(attack.attack>1){attacker.setYaw(90);attacker.setPitch(50);} // Follow-up stays on the established target.
                    float health=defender.getHealth();hit.invoke(null,attacker,attack);
                    require(Math.abs(health-defender.getHealth()-CombatState.DAMAGE[attack.attack])<.001,"Lost contact "+attack.attack);
                    contacts.add((long)tick);
                }
                if(tick>=3&&tick<40) {
                    require(HitStun.stunned(defender),"Defender not stunned at "+tick);
                    input.invoke(null,defender,0,0,0);input.invoke(null,defender,1,0,0);input.invoke(null,defender,3,1,0);
                    require(victim.attack==0&&!victim.guarding&&!victim.dashing(now),"Defender escaped with combat input at "+tick);
                    double x=defender.getX(),z=defender.getZ();defender.setVelocity(.3,0,.3);defender.travel(new Vec3d(1,0,1));
                    require(Math.abs(defender.getX()-x)<1e-6&&Math.abs(defender.getZ()-z)<1e-6,"Movement escaped stun");
                    defender.setVelocity(Vec3d.ZERO);defender.jump();require(defender.getVelocity().y==0,"Jump escaped stun");
                    if(tick==5) {
                        defender.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.ENDER_PEARL,4));
                        defender.networkHandler.onPlayerInteractItem(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND,1));
                        require(defender.getMainHandStack().getCount()==4,"Item escaped stun");defender.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                        defender.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.PositionAndOnGround(x+1,defender.getY(),z,true));
                        require(Math.abs(defender.getX()-x)<1e-6,"Client movement packet escaped stun");
                    }
                }
                HitStun.tick();
            }
            require(contacts.equals(List.of(3L,14L,26L,40L)),"Timing changed: "+contacts);
            require(defender.getHealth()==84,"Total combo damage changed");require(!HitStun.stunned(defender),"Fourth hit failed to release");
            require(defender.getVelocity().horizontalLengthSquared()>.1,"Fourth knockback was swallowed");
            checks.put("true_combo_contact_ticks",contacts);checks.put("no_escape_between_all_four_player_hits",true);checks.put("fourth_releases_before_knockback",true);

            // A single hit releases at the attack's recovery boundary, with no permanent AI changes.
            attacker.setPosition(0,101,0);attacker.setYaw(180);attacker.setHeadYaw(180);attacker.setPitch(0);monster.setPosition(0,101,-2);
            defender.setPosition(6,101,0);attack.cancel();start+=100;props.setTime(start);attack.requestAttack(start);
            props.setTime(start+3);hit.invoke(null,attacker,attack);require(HitStun.stunned(monster),"Mob was not stunned");
            require(!monster.tryAttack(attacker),"Mob retaliated during hitstun");double x=monster.getX(),z=monster.getZ();monster.setVelocity(.3,0,.3);monster.travel(new Vec3d(1,0,1));
            require(Math.abs(monster.getX()-x)<1e-6&&Math.abs(monster.getZ()-z)<1e-6,"Mob moved during hitstun");
            props.setTime(start+10);attack.tick(start+10);HitStun.tick();require(!HitStun.stunned(monster)&&!monster.isAiDisabled(),"Stopped combo left a stuck mob");
            checks.put("stunned_mob_cannot_move_or_retaliate",true);checks.put("stopped_combo_releases_without_disabling_ai",true);

            // Range and solid cover cancel target assistance; no teleporting / wall hits.
            for(boolean wall:new boolean[]{false,true}) {
                attack.cancel();start+=100;props.setTime(start);attacker.setYaw(180);attacker.setHeadYaw(180);attacker.setPitch(0);monster.setPosition(0,101,-2);
                attack.requestAttack(start);props.setTime(start+3);hit.invoke(null,attacker,attack);require(HitStun.stunned(monster),"Capture fixture missing");
                if(wall){world.setBlockState(new BlockPos(0,102,-1),Blocks.STONE.getDefaultState());world.setBlockState(new BlockPos(-1,102,-1),Blocks.STONE.getDefaultState());}
                else attacker.setPosition(10,101,0);
                HitStun.tick();require(!HitStun.stunned(monster),"Out of range/wall kept target trapped");
                world.setBlockState(new BlockPos(0,102,-1),Blocks.AIR.getDefaultState());world.setBlockState(new BlockPos(-1,102,-1),Blocks.AIR.getDefaultState());attacker.setPosition(0,101,0);
            }
            checks.put("wall_or_range_breaks_capture",true);

            attack.cancel();start+=100;props.setTime(start);attack.requestAttack(start);props.setTime(start+3);hit.invoke(null,attacker,attack);
            attacker.timeUntilRegen=0;attacker.damage(world.getDamageSources().generic(),1);HitStun.tick();require(!HitStun.stunned(monster),"Interrupted attacker kept victim locked");
            checks.put("interrupted_attacker_releases_target",true);
            for(boolean weaponSwap:new boolean[]{true,false}) {
                attack.cancel();start+=100;props.setTime(start);attacker.setYaw(180);attacker.setHeadYaw(180);attacker.setPitch(0);
                attack.requestAttack(start);props.setTime(start+3);hit.invoke(null,attacker,attack);require(HitStun.stunned(monster),"Cleanup capture fixture missing");
                if(weaponSwap)attacker.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));else HitStun.disconnect(attacker.getUuid());
                HitStun.tick();require(!HitStun.stunned(monster),"Weapon swap/disconnect left victim trapped");attacker.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
            }
            checks.put("weapon_swap_and_disconnect_release_target",true);
            monster.setPosition(6,101,-2);defender.setPosition(0,101,-2);defender.setYaw(0);defender.setHeadYaw(0);defender.setPitch(0);defender.setVelocity(Vec3d.ZERO);
            attack.cancel();start+=100;props.setTime(start);victim.guard(true,start);attack.requestAttack(start);props.setTime(start+3);
            float health=defender.getHealth();hit.invoke(null,attacker,attack);
            require(defender.getHealth()==health&&!HitStun.stunned(defender),"Blocked first hit started capture");victim.guard(false,start+3);
            attack.cancel();start+=100;props.setTime(start);attacker.setYaw(90);attacker.setHeadYaw(90);attack.requestAttack(start);props.setTime(start+3);hit.invoke(null,attacker,attack);
            require(!HitStun.stunned(defender)&&!HitStun.stunned(monster),"Miss started capture");
            checks.put("blocked_or_missed_first_hit_does_not_capture",true);
            monster.discard();world.removePlayer(defender,Entity.RemovalReason.DISCARDED);
        } finally {HitStun.clear();props.setTime(original);}
    }
}
