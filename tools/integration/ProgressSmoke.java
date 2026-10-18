package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.progression.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import java.util.Map;
import static dev.sangwoo.botandverify.CombatSmoke.*;
public final class ProgressSmoke {
    public static void run(MinecraftServer server,Map<String,Object> checks)throws Exception {
        var p=player(server,"Progress",84,84);p.setHealth(15);ProgressManager.joined(p);
        require(p.getMaxHealth()==200&&p.getHealth()==150,"Existing health ratio did not migrate");
        ProgressManager.joined(p);require(p.getHealth()==150,"Rejoin healed player");
        p.timeUntilRegen=0;p.damage(server.getOverworld().getDamageSources().magic(),50);
        require(p.getHealth()==100,"200 HP damage did not use real vanilla health");
        p.getHungerManager().setFoodLevel(0);p.getHungerManager().addExhaustion(40);
        for(int i=0;i<200;i++)p.getHungerManager().update(p);
        require(p.getHealth()==100&&p.getHungerManager().getFoodLevel()==20,"Hidden hunger still damaged/healed/blocked sprint");
        checks.put("player_200_real_hp_preserves_ratio_damage_and_no_hunger",true);
        var commands=server.getCommandManager();var source=p.getCommandSource().withLevel(0);var admin=source.withLevel(2);
        require(commands.executeWithPrefix(source,"botand status")==1,"Status needs OP");
        require(commands.executeWithPrefix(source,"botand progress setlevel @s 50")==0,"Non-OP setlevel exploit");
        require(commands.executeWithPrefix(source,"botand giveatomic")==0,"Old admin alias lost permission");
        require(commands.executeWithPrefix(source,"botand rebirth")==0,"Early rebirth");
        for(int i=1;i<=15;i++) {
            require(commands.executeWithPrefix(admin,"botand progress setlevel @s 50")==1,"Setlevel command failed");
            require(commands.executeWithPrefix(source,"botand rebirth")==1,"Valid rebirth failed");
            require(ProgressManager.profile(p).rebirths()==i&&ProgressManager.profile(p).level()==1,"Rebirth counters wrong");
            require(commands.executeWithPrefix(source,"botand rebirth")==0,"Repeated rebirth bypassed gate");
        }
        require(commands.executeWithPrefix(admin,"botand progress stand @s kageno")==1,"Kageno not selectable as stand");
        require(commands.executeWithPrefix(admin,"botand progress setlevel @s 23")==1,"Setlevel failed");
        require(commands.executeWithPrefix(source,"botand transcend")==1,"Transcend failed");
        var profile=ProgressManager.profile(p);
        require(profile.equals(new ProgressProfile(23,0,1,"kageno")),"Transcend did not reset only rebirths");
        require(commands.executeWithPrefix(source,"botand transcend")==0,"Repeated transcend exploit");
        require(p.getHealth()==100,"Progress transitions healed damaged player");
        var restored=ProgressState.read(ProgressState.get(server).writeNbt(new NbtCompound()));
        require(restored.profile(p.getUuid()).equals(profile),"Progress NBT round trip failed");
        var respawn=new ServerPlayerEntity(server,server.getOverworld(),p.getGameProfile());respawn.networkHandler=p.networkHandler;
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(p,respawn,false);
        require(respawn.getMaxHealth()==200&&respawn.getHealth()==200&&ProgressManager.profile(respawn).equals(profile),"Respawn lost HP or progress");
        checks.put("level_50_rebirth_15_transcend_commands_permissions_and_no_duplicate_rewards",true);
        checks.put("progress_saved_by_uuid_survives_respawn_and_kageno_stand",true);
        ProgressManager.set(p,profile.withLevel(50));
        var method=dev.sangwoo.celestialfall.combat.CombatManager.class.getDeclaredMethod("state",ServerPlayerEntity.class);method.setAccessible(true);
        var combat=(dev.sangwoo.celestialfall.combat.CombatState)method.invoke(null,p);combat.brokenUntil=p.getWorld().getTime()+20;
        require(commands.executeWithPrefix(source,"botand rebirth")==0,"Guard break bypass via rebirth");
        checks.put("progress_transition_blocked_while_guard_broken",true);
    }
}
