package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.map.*;
import dev.sangwoo.celestialfall.combat.*;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.terrain.*;
import dev.sangwoo.celestialfall.training.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.nio.file.*;
import java.util.*;
import static dev.sangwoo.botandverify.CombatSmoke.*;

public final class MapGateSmoke {
    public static void run(MinecraftServer server,Map<String,Object> checks)throws Exception {
        var w=server.getOverworld();long now=w.getTime();var commands=server.getCommandManager();
        boolean bundled=Files.exists(Path.of("botand_smoke_world/datapacks/cliffcity/pack.mcmeta"));
        Path checkpoint=Path.of("../build/botand-motion/map-restart.json");
        if(Files.exists(checkpoint))require(Boolean.TRUE.equals(MapState.get(server).override("minecraft:overworld")),"Map designation lost across real restart");
        MapGate.designate(w,null);
        require(MapGate.enabled(w)==bundled,"Default/automatic city recognition wrong: "+bundled);
        require(!MapGate.enabled(server.getWorld(World.NETHER))&&!MapGate.enabled(server.getWorld(World.END)),"Designation leaked to Nether/End");
        checks.put(bundled?"map_bundled_third_city_datapack_automatically_recognized":"map_ordinary_save_defaults_off",true);
        if(Files.exists(checkpoint))checks.put("map_designation_survives_real_server_restart",true);
        var p=player(server,"MapTester",224.5,224.5);var admin=p.getCommandSource().withLevel(2);var user=p.getCommandSource().withLevel(0);
        var input=CombatManager.class.getDeclaredMethod("input",ServerPlayerEntity.class,int.class,int.class,int.class);input.setAccessible(true);
        var state=CombatManager.class.getDeclaredMethod("state",ServerPlayerEntity.class);state.setAccessible(true);
        var dummy=TrainingDummies.TYPES.get(DummyKind.ATTACK).create(w);
        require(dummy!=null,"Dummy fixture");
        boolean terrain=CelestialFall.CONFIG.terrainDamage;
        try {
            MapGate.designate(w,false);server.getPlayerManager().getPlayerList().add(p);w.onPlayerConnected(p);
            require(commands.executeWithPrefix(user,"botand map enable")==0&&!MapGate.enabled(w),"Non-OP can designate map");
            require(!SpellManager.cast(p)&&!AtomicManager.cast(p)&&!HeavenManager.cast(p),"Skill cast accepted in ordinary map");
            for(var item:List.of(CelestialFall.ASTRAL_SIGIL,CelestialFall.ATOMIC_SWORD,CelestialFall.HEAVEN_SWORD,TrainingDummies.EGGS.get(DummyKind.ATTACK))) {
                p.setStackInHand(Hand.MAIN_HAND,new ItemStack(item));require(item.use(w,p,Hand.MAIN_HAND).getResult()==ActionResult.FAIL,"Item use accepted off-map");
            }
            p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
            require(commands.executeWithPrefix(admin,"botand dummy attack")==0,"Dummy command accepted off-map");
            require(commands.executeWithPrefix(admin,"botand progress setlevel @s 50")==0,"Progress changed off-map");
            for(int action=0;action<4;action++)input.invoke(null,p,action,0,1);
            require(!CombatManager.busy(p)&&!CombatManager.actionLocked(p),"Combat packet bypassed map gate");
            p.networkHandler.onClientCommand(new ClientCommandC2SPacket(p,ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            require(p.isSneaking(),"Vanilla crouch blocked outside map");p.setSneaking(false);
            p.getHungerManager().setFoodLevel(12);p.getHungerManager().setSaturationLevel(0);p.getHungerManager().addExhaustion(5);p.getHungerManager().update(p);
            require(p.getHungerManager().getFoodLevel()==11,"Vanilla hunger disabled outside map");
            dummy.setPosition(224.5,101,223);w.spawnEntity(dummy);dummy.setTarget(p);
            var dt=TrainingDummyEntity.class.getDeclaredMethod("mobTick");dt.setAccessible(true);float before=p.getHealth();
            for(int i=0;i<80;i++){TerrainSmoke.time(w,now+i);dt.invoke(dummy);}
            require(p.getHealth()==before&&dummy.combatState().attack==0&&!dummy.guarding(),"Dummy attacks outside map");dummy.discard();TerrainSmoke.time(w,now);
            checks.put("map_rejects_all_skills_eggs_combat_packets_progress_and_dummy_attacks",true);
            checks.put("map_vanilla_crouch_and_hunger_work_outside",true);
            // Health and hunger ownership survives enable/disable without healing or losing custom base HP.
            p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40);p.setHealth(30);p.getHungerManager().setFoodLevel(11);
            require(commands.executeWithPrefix(admin,"botand map enable")==1&&MapGate.enabled(w),"Enable command failed");
            require(p.getMaxHealth()==200&&p.getHealth()==150,"Entering map lost health fraction");
            p.getHungerManager().update(p);require(p.getHungerManager().getFoodLevel()==20,"Map hunger suppression absent");
            var combat=(CombatState)state.invoke(null,p);combat.brokenUntil=now+20;
            var speed=CombatManager.class.getDeclaredMethod("speed",ServerPlayerEntity.class,CombatState.class,long.class);speed.setAccessible(true);speed.invoke(null,p,combat,now);
            require(CombatManager.actionLocked(p),"Fixture not locked");
            BlockPos block=new BlockPos(224,100,224);w.setBlockState(block,Blocks.STONE.getDefaultState());CelestialFall.CONFIG.terrainDamage=true;
            TerrainRestoration.crater(w,new Vec3d(224.5,101,224.5),1,now);TerrainRestoration.tick(server);
            require(w.getBlockState(block).getBlock() instanceof RecoveryBlock,"Enabled map crater missing");
            require(commands.executeWithPrefix(admin,"botand map disable")==1&&!MapGate.enabled(w),"Disable command failed");
            require(p.getMaxHealth()==40&&p.getHealth()==30&&p.getHungerManager().getFoodLevel()==11,"Leaving map did not restore HP/hunger");
            require(!CombatManager.actionLocked(p)&&p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)==p.getAttributeBaseValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),"Guard slow/lock survived map exit");
            TerrainSmoke.time(w,now+220);TerrainRestoration.tick(server);require(w.getBlockState(block).isOf(Blocks.STONE),"Disabling map abandoned pending restoration");
            TerrainRestoration.crater(w,new Vec3d(224.5,101,224.5),1,now+220);TerrainRestoration.tick(server);require(w.getBlockState(block).isOf(Blocks.STONE),"Off-map terrain damage accepted");
            checks.put("map_exit_restores_custom_hp_fraction_hunger_and_clears_guard_slow",true);
            checks.put("map_disable_stops_new_craters_but_finishes_existing_restoration",true);
            commands.executeWithPrefix(admin,"botand map auto");require(MapGate.enabled(w)==bundled,"Auto did not clear override");
        }finally {
            dummy.discard();server.getPlayerManager().getPlayerList().remove(p);p.discard();CombatManager.leave(p);
            CelestialFall.CONFIG.terrainDamage=terrain;TerrainSmoke.time(w,now);
            MapGate.designate(w,true);Files.createDirectories(checkpoint.getParent());Files.writeString(checkpoint,"{\"expectedOverworld\":true}");
        }
    }
}
