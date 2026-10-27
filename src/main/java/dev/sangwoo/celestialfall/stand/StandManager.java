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
    public static boolean kageno(PlayerEntity p){return has(p,"kageno");}
    /** Star Platinum's user on the map (2.51.0): Q brings it out beside its user and sends it back (StandSummon). */
    public static boolean starPlatinum(PlayerEntity p){return has(p,"star_platinum");}
    private static boolean has(PlayerEntity p,String stand){
        return p!=null&&MapGate.enabled(p.getWorld())&&p.isAlive()&&!p.isSpectator()
            &&p instanceof StandHolder holder&&stand.equals(holder.botand$getStand());
    }
    public static void refresh(ServerPlayerEntity p){
        ((StandHolder)p).botand$setStand(MapGate.enabled(p.getWorld())?ProgressManager.profile(p).stand():"none");
    }
    public static void initialize(){
        KagenoManager.initialize();StandSummon.initialize();StarPlatinumAttack.initialize();StarPlatinumRush.initialize();StarPlatinumInhale.initialize();StarPlatinumBeatdown.initialize();StarPlatinumTimeStop.initialize();StandArrow.initialize();
        // (after the stands' own ticks, which may set a cooldown as a move they cut off ends)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(StandManager::forgetOthers);
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
    /**
     * A stand that is not theirs takes its cooldowns with it (2.74.2, user request: "걍 다른 스탠드를 끼면 다른 스탠드
     * 쿨타임은 사라진다 이게 힘든가? 서로 스탠드가 다른데 왜 흐르는건데"): each tick, whoever's stand (their profile's,
     * which stays as they die) is not Star Platinum has none of its short cooldowns (the punches' rest, the rush, the heavy
     * punch, the Star Finger, the inhale), so they are ready if it comes back; Kageno's are gone the tick it is not theirs
     * (KagenoManager.leave). The long ones (Atomic, the time stop, the beatdown) stay in their ledger (LongCooldowns) and
     * are not shown to another stand's user (CooldownHud.shows): if their stand comes back while one would still run, it
     * begins again in full then (2.74.1). A boss is not a listed player and keeps its own.
     */
    private static void forgetOthers(net.minecraft.server.MinecraftServer server){
        for(var p:server.getPlayerManager().getPlayerList())
            if(!"star_platinum".equals(ProgressManager.profile(p).stand())){StarPlatinumAttack.forget(p);StarPlatinumRush.forget(p);StarPlatinumInhale.forget(p);}
    }
    /** Inventory never determines skill ownership; the server's saved profile does. */
    public static boolean useSkill(ServerPlayerEntity p){
        if(!MapGate.enabled(p.getWorld())||!p.isAlive()||p.isSpectator()||p.isSleeping()
            ||!ProgressManager.canUseKageno(p))return false;
        return KagenoManager.input(p,KagenoRules.R);
    }
}
