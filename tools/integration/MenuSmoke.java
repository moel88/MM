package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.menu.*;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.combat.*;
import dev.sangwoo.celestialfall.map.MapGate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.NbtCompound;
import java.util.*;
import static dev.sangwoo.botandverify.CombatSmoke.*;
public final class MenuSmoke {
    public static void run(MinecraftServer server,Map<String,Object> checks)throws Exception {
        var p=player(server,"MenuTester",252,252);var other=player(server,"OtherMenuTester",255,255);
        var state=MenuState.get(server);var initial=ProgressManager.profile(p);
        MenuServer.perform(p,1,-1);require(ProgressManager.profile(p).equals(initial),"UI rebirth bypassed level gate");
        MenuServer.perform(p,2,-1);require(ProgressManager.profile(p).equals(initial),"UI transcend bypassed 15 rebirth gate");
        MenuServer.perform(p,3,3);require(state.get(p.getUuid()).equipped()==-1,"Locked title exploit");
        ProgressManager.set(p,new ProgressProfile(50,0,0,"kageno"));p.setHealth(100);
        require(state.get(p.getUuid()).earned(0),"Level achievement not recorded outside menu");
        MenuServer.perform(p,1,-1);require(ProgressManager.profile(p).equals(new ProgressProfile(1,1,0,"kageno")),"UI rebirth wrong state");require(p.getHealth()==100,"Rebirth healed player");
        var after=ProgressManager.profile(p);MenuServer.perform(p,1,-1);require(ProgressManager.profile(p).equals(after),"Repeated click duplicates rebirth");
        MenuServer.perform(p,3,0);require(state.get(p.getUuid()).equipped()==0,"Earned title not equipped");
        MenuServer.perform(other,3,0);require(state.get(other.getUuid()).equipped()==-1,"Another player's title leaked");
        ProgressManager.set(p,new ProgressProfile(23,15,0,"kageno"));MenuServer.perform(p,2,-1);
        require(ProgressManager.profile(p).equals(new ProgressProfile(23,0,1,"kageno")),"UI transcend resets wrong fields");require(state.get(p.getUuid()).unlocked()==15,"Achievements lost after transcendence");
        after=ProgressManager.profile(p);MenuServer.perform(p,2,-1);require(ProgressManager.profile(p).equals(after),"Repeated transcend click");
        var restored=MenuState.read(state.writeNbt(new NbtCompound()));require(restored.get(p.getUuid()).equals(state.get(p.getUuid())),"Title NBT state lost");
        var get=CombatManager.class.getDeclaredMethod("state",net.minecraft.server.network.ServerPlayerEntity.class);get.setAccessible(true);
        ((CombatState)get.invoke(null,p)).brokenUntil=p.getWorld().getTime()+20;
        MenuServer.perform(p,3,2);require(state.get(p.getUuid()).equipped()==0,"Title use bypassed action lock");CombatManager.leave(p);
        MapGate.designate(server.getOverworld(),false);MenuServer.perform(p,3,2);require(state.get(p.getUuid()).equipped()==0,"Title use allowed outside map");MapGate.designate(server.getOverworld(),true);
        checks.put("menu_server_validates_growth_titles_action_lock_and_map",true);
        checks.put("menu_duplicate_clicks_do_not_duplicate_progress_and_never_heal",true);
        checks.put("menu_earned_titles_survive_rebirth_transcend_and_nbt",true);
    }
}
