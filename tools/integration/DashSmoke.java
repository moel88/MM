package dev.sangwoo.botandverify;

import dev.sangwoo.celestialfall.combat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.Map;
import static dev.sangwoo.botandverify.CombatSmoke.*;

/** Exercise input acceptance and each real server dash impulse, including creative flight. */
public final class DashSmoke {
    public static void run(MinecraftServer server,Map<String,Object> checks)throws Exception {
        var world=server.getOverworld();var props=(net.minecraft.world.level.ServerWorldProperties)world.getLevelProperties();
        long original=world.getTime();var p=player(server,"DashTester",40,0);
        var stateMethod=CombatManager.class.getDeclaredMethod("state",ServerPlayerEntity.class);stateMethod.setAccessible(true);
        var input=CombatManager.class.getDeclaredMethod("input",ServerPlayerEntity.class,int.class,int.class,int.class);input.setAccessible(true);
        var tick=CombatManager.class.getDeclaredMethod("tick",MinecraftServer.class);tick.setAccessible(true);
        server.getPlayerManager().getPlayerList().add(p);
        try {
            CombatState state=(CombatState)stateMethod.invoke(null,p);
            for(int mode=0;mode<3;mode++) {
                long start=original+2000+mode*100;props.setTime(start);state.cancel();p.horizontalCollision=false;
                p.changeGameMode(mode==2?GameMode.CREATIVE:GameMode.SURVIVAL);
                p.getAbilities().flying=mode==2;p.setOnGround(mode==0);p.setYaw(180);p.setVelocity(0,mode==0?0:-.13,0);
                input.invoke(null,p,3,0,0);require(state.dashing(start),"Dash rejected in mode "+mode);
                for(int age=0;age<CombatState.DASH_TICKS;age++) {
                    props.setTime(start+age);tick.invoke(null,server);
                    require(Math.abs(p.getVelocity().horizontalLength()-CombatState.dashSpeed(age))<1e-6,"Wrong dash impulse "+age);
                    require(Math.abs(p.getVelocity().y-(mode==0?0:-.13))<1e-6,"Dash changed vertical velocity");
                }
                require(p.getAbilities().flying==(mode==2),"Dash toggled flight");
                props.setTime(start+8);long before=state.dashStart;input.invoke(null,p,3,0,0);
                require(state.dashStart==before,"Dash bypassed cooldown");
                props.setTime(start+24);input.invoke(null,p,3,0,1);require(state.dashing(start+24),"Dash did not resume");
                p.horizontalCollision=true;tick.invoke(null,server);require(!state.dashing(start+24),"Dash ignored wall collision");
            }
            checks.put("dash_ground_air_creative_flight_seven_live_impulses",true);
            checks.put("dash_preserves_vertical_velocity_flight_and_cooldown",true);checks.put("dash_wall_stops_impulse",true);
            long now=original+3000;props.setTime(now);state.cancel();state.brokenUntil=now+20;
            input.invoke(null,p,3,1,0);require(!state.dashing(now),"Dash bypassed guard break");
            state.brokenUntil=0;state.guard(true,now);input.invoke(null,p,3,1,0);require(!state.dashing(now),"Dash bypassed guard");
            state.cancel();state.requestAttack(now);input.invoke(null,p,3,1,0);require(!state.dashing(now),"Dash bypassed attack");
            checks.put("dash_blocked_during_attack_guard_and_break",true);
        } finally {server.getPlayerManager().getPlayerList().remove(p);HitStun.clear();props.setTime(original);}
    }
}
