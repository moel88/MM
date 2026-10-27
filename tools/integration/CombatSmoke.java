package dev.sangwoo.botandverify;
import com.mojang.authlib.GameProfile;
import dev.sangwoo.celestialfall.combat.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.network.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.network.packet.c2s.play.*;

/** Separate verification-only mod. Never included in the player-facing mod JAR. */
public final class CombatSmoke implements ModInitializer {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static ServerPlayerEntity player(MinecraftServer server,String name,double x,double z)throws Exception {
        var p=new ServerPlayerEntity(server,server.getOverworld(),new GameProfile(UUID.randomUUID(),name));
        p.networkHandler=new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),p);
        p.setPosition(x,101,z);p.setYaw(180);p.setHeadYaw(180);p.setPitch(0);p.setOnGround(true);
        var field=ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");field.setAccessible(true);field.setInt(p,0);
        return p;
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            var checks=new LinkedHashMap<String,Object>();
            try {
                TerrainSmoke.verifyRestart(server,checks);
                MapGateSmoke.run(server,checks);
                var world=server.getOverworld();world.setTimeOfDay(18000);
                for(int x=-6;x<=8;x++)for(int z=-6;z<=4;z++)world.setBlockState(new BlockPos(x,100,z),Blocks.STONE.getDefaultState());
                var p=player(server,"Attacker",0,0);
                var target=EntityType.ZOMBIE.create(world);require(target!=null,"Zombie fixture");target.setAiDisabled(true);target.setPosition(0,101,-2);
                target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);target.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(0);target.setHealth(100);boolean spawned=world.spawnEntity(target);
                System.out.println("FIXTURE "+spawned+" eye="+p.getEyePos()+" look="+p.getRotationVec(1)+" target="+target.getBoundingBox()+" canHit="+target.canHit()+" visible="+p.canSee(target)+" found="+world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,p.getBoundingBox().expand(4),e->true).size());
                var hit=CombatManager.class.getDeclaredMethod("hit",ServerPlayerEntity.class,CombatState.class);hit.setAccessible(true);
                var s=new CombatState();
                for(int strike=1;strike<=4;strike++) {
                    target.setVelocity(Vec3d.ZERO);float before=target.getHealth();s.attack=strike;hit.invoke(null,p,s);
                    require(Math.abs(before-target.getHealth()-CombatState.DAMAGE[strike])<.001,"Contact damage "+strike+": "+(before-target.getHealth()));
                    if(strike<4)require(target.getVelocity().lengthSquared()<1e-8,"Unexpected early knockback "+strike);
                    else require(target.getVelocity().horizontalLengthSquared()>.1,"Fourth punch must knock back");
                }
                checks.put("four_live_damage_hits",true);checks.put("first_three_no_knockback_fourth_knockback",true);
                world.setBlockState(new BlockPos(0,102,-1),Blocks.STONE.getDefaultState());world.setBlockState(new BlockPos(-1,102,-1),Blocks.STONE.getDefaultState());
                float before=target.getHealth();hit.invoke(null,p,s);require(target.getHealth()==before,"Punch passed through a wall");checks.put("wall_blocks_punch",true);
                world.setBlockState(new BlockPos(0,102,-1),Blocks.AIR.getDefaultState());world.setBlockState(new BlockPos(-1,102,-1),Blocks.AIR.getDefaultState());
                var defender=player(server,"Defender",4,0);p.setPosition(4,101,-2);
                var getState=CombatManager.class.getDeclaredMethod("state",ServerPlayerEntity.class);getState.setAccessible(true);
                CombatState guard=(CombatState)getState.invoke(null,defender);long now=world.getTime();guard.guard(true,now);
                float health=defender.getHealth();
                var punchSource=new net.minecraft.entity.damage.DamageSource(world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).entryOf(CombatManager.UNARMED),p);
                for(int i=1;i<=4;i++) {
                    defender.damage(punchSource,CombatState.DAMAGE[i]);
                    require(guard.pressure==25*i && guard.broken(now)==(i==4),"Player guard broke before fourth basic hit "+i);
                }
                checks.put("player_guard_breaks_on_fourth_basic_hit",true);
                require(defender.getHealth()==health,"Frontal guard leaked health");require(guard.brokenUntil==now+20&&!guard.guarding,"Guard did not break for 20 ticks");
                double slowed=defender.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                require(Math.abs(slowed/defender.getAttributeBaseValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)-.1)<1e-6,"Break is not 90% slow: "+slowed);
                checks.put("frontal_guard_and_break",true);checks.put("live_attribute_90_percent_slow",true);
                require(CombatManager.actionLocked(defender),"Action lock missing");
                defender.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.ENDER_PEARL,4));
                defender.setStackInHand(Hand.OFF_HAND,new ItemStack(Items.DIAMOND,2));
                defender.networkHandler.onPlayerInteractItem(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND,1));
                require(defender.getMainHandStack().getCount()==4&&!defender.isUsingItem(),"Item use bypassed break");
                defender.networkHandler.onPlayerAction(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,BlockPos.ORIGIN,Direction.UP));
                require(defender.getMainHandStack().isOf(Items.ENDER_PEARL),"Swap bypassed break");
                defender.networkHandler.onPlayerAction(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ITEM,BlockPos.ORIGIN,Direction.UP));
                require(defender.getMainHandStack().getCount()==4,"Drop bypassed break");
                int selected=defender.getInventory().selectedSlot;defender.networkHandler.onUpdateSelectedSlot(new UpdateSelectedSlotC2SPacket(3));
                require(defender.getInventory().selectedSlot==selected,"Hotbar bypassed break");
                target.setPosition(4,101,-2);float zombieHealth=target.getHealth();
                defender.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.attack(target,false));
                require(target.getHealth()==zombieHealth,"Vanilla attack bypassed break");
                defender.networkHandler.onClientCommand(new ClientCommandC2SPacket(defender,ClientCommandC2SPacket.Mode.START_SPRINTING));
                require(!defender.isSprinting(),"Sprint bypassed break");
                defender.networkHandler.onClientCommand(new ClientCommandC2SPacket(defender,ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
                require(!defender.isSneaking(),"On-foot crouch was not removed");
                BlockPos blockPos=new BlockPos(5,101,0);world.setBlockState(blockPos,Blocks.STONE.getDefaultState());
                require(!defender.interactionManager.tryBreakBlock(blockPos)&&world.getBlockState(blockPos).isOf(Blocks.STONE),"Block breaking bypassed break");
                require(defender.interactionManager.interactItem(defender,world,defender.getMainHandStack(),Hand.MAIN_HAND)==ActionResult.FAIL,"Direct item use bypassed break");
                defender.setVelocity(Vec3d.ZERO);defender.jump();require(defender.getVelocity().lengthSquared()==0,"Jump bypassed break");
                checks.put("break_blocks_item_attack_drop_swap_slot_sprint_jump_mining",true);checks.put("no_on_foot_crouch",true);
                guard.brokenUntil=now;require(!CombatManager.actionLocked(defender),"Action lock did not expire");
                defender.networkHandler.onUpdateSelectedSlot(new UpdateSelectedSlotC2SPacket(3));require(defender.getInventory().selectedSlot==3,"Hotbar stayed locked after expiry");
                checks.put("actions_resume_at_break_expiry",true);
                guard.brokenUntil=0;guard.pressure=0;guard.guard(true,now);p.setPosition(4,101,2);defender.timeUntilRegen=0;
                defender.damage(world.getDamageSources().playerAttack(p),4);require(defender.getHealth()<health,"Rear attack incorrectly blocked");checks.put("rear_attack_bypasses_guard",true);
                var speed=CombatManager.class.getDeclaredMethod("speed",ServerPlayerEntity.class,CombatState.class,long.class);speed.setAccessible(true);
                guard.guard(false,now);speed.invoke(null,defender,guard,now+20);
                require(Math.abs(defender.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)-defender.getAttributeBaseValue(EntityAttributes.GENERIC_MOVEMENT_SPEED))<1e-6,"Movement modifier not removed");checks.put("movement_restores",true);
                ComboSmoke.run(server,checks);
                DummySmoke.run(server,checks);
                AtomicSmoke.run(server,checks);
                DashSmoke.run(server,checks);
                ProgressSmoke.run(server,checks);
                MenuSmoke.run(server,checks);
                TerrainSmoke.run(server,checks);
                checks.put("server_mixins_applied",true);checks.put("result","passed");
                System.out.println("BOTAND_COMBAT_SMOKE_PASSED "+checks);
            } catch(Throwable error) {
                error.printStackTrace();checks.put("result","failed");checks.put("error",error.toString());
            } finally {
                try {Path out=Path.of("../build/botand-motion/server-combat.json");Files.createDirectories(out.getParent());Files.writeString(out,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(checks));}catch(Exception ignored){}
                server.stop(false);
            }
        });
    }
}
