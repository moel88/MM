package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Star Platinum comes out and goes back (2.51.0, user request: "스타플래티나가 있는 상태에서 q 누르면 플레이어 옆에 자세
 * 잡으면서 계속 나와있고"). Q on the user's client asks for it (stand_summon_v1, no payload) and the server decides: it keeps
 * the answer on the player as tracked data, so every client that sees the player sees the stand come out, stay beside its user
 * and go back (client/stand/StandPresence). The stand goes back by itself once its user cannot have it out: dead, asleep, a
 * spectator, off the map, or with another stand. A new body (a respawn, the next login) starts with it in.
 */
public final class StandSummon {
    public static final Identifier TOGGLE=CelestialFall.id("stand_summon_v1");
    /** Server ticks from one request to the next one heard: a double tap cannot make the stand flicker. */
    public static final int RATE=8;
    private static final java.util.Map<ServerPlayerEntity,Integer> NEXT_REQUEST=new java.util.WeakHashMap<>();
    private StandSummon(){}
    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                int now=server.getTicks();
                if(now<NEXT_REQUEST.getOrDefault(player,0))return;
                NEXT_REQUEST.put(player,now+RATE);
                toggle(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var p:server.getPlayerManager().getPlayerList())if(out(p)&&!canBeOut(p))((StandHolder)p).botand$setStandOut(false);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->NEXT_REQUEST.clear());
    }
    public static boolean out(ServerPlayerEntity p){return ((StandHolder)p).botand$isStandOut();}
    /** What having the stand out takes: Star Platinum as the stand, alive and awake on the map. */
    public static boolean canBeOut(ServerPlayerEntity p){return StandManager.starPlatinum(p)&&!p.isSleeping();}
    /**
     * Q: out if it is in and the user may have it out, in if it is out. Returns whether it is out now. Not while its
     * user is incapacitated (2.60.0: their guard broken or struck by a Star Finger; their client sends no Q then either),
     * nor while a beatdown's cutscene has them (2.64.0: its user's stand goes in and comes out as the cutscene has it).
     */
    public static boolean toggle(ServerPlayerEntity p){
        if(dev.sangwoo.celestialfall.combat.CombatManager.incapacitated(p)||StarPlatinumBeatdown.involved(p))return out(p);
        boolean out=!out(p)&&canBeOut(p);
        ((StandHolder)p).botand$setStandOut(out);
        return out;
    }
}
