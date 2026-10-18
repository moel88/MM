package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.progression.ProgressManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class StandManager {
    public static final Identifier SKILL=CelestialFall.id("stand_skill_v1");
    private static final java.util.Map<ServerPlayerEntity,Integer> NEXT_REQUEST=new java.util.WeakHashMap<>();
    private StandManager(){}
    public static boolean kageno(PlayerEntity p){
        return p!=null&&MapGate.enabled(p.getWorld())&&p.isAlive()&&!p.isSpectator()
            &&p instanceof StandHolder holder&&"kageno".equals(holder.botand$getStand());
    }
    public static void refresh(ServerPlayerEntity p){
        ((StandHolder)p).botand$setStand(MapGate.enabled(p.getWorld())?ProgressManager.profile(p).stand():"none");
    }
    public static void initialize(){
        KagenoManager.initialize();
        ServerPlayNetworking.registerGlobalReceiver(SKILL,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                // Entity age resets on respawn; server ticks keep the rate limit continuous.
                int now=server.getTicks();
                if(now<NEXT_REQUEST.getOrDefault(player,0))return;
                NEXT_REQUEST.put(player,now+4);
                useSkill(player);
            });
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->NEXT_REQUEST.clear());
    }
    /** Inventory never determines skill ownership; the server's saved profile does. */
    public static boolean useSkill(ServerPlayerEntity p){
        if(!MapGate.enabled(p.getWorld())||!p.isAlive()||p.isSpectator()||p.isSleeping()
            ||!ProgressManager.canUseKageno(p))return false;
        return KagenoManager.input(p,KagenoRules.R);
    }
}
