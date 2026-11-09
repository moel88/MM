package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.map.MapGate;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

/**
 * Back to life by itself (2.107.0, user request: "시스템을 가꾸어야 할 거 같아 일단 죽으면 리스폰 화면이 뜨지 않고 자동으로
 * 부활되게 하는데 죽으면 그 자리에서 5초 동안 화면만 움직일수 있게 하다가 자동 부활"): one who dies on the Botand map is
 * shown no death screen; for {@link #DELAY} (five seconds) they stay where they fell, only their view moving (their
 * client's DeathView: the camera turns and zooms, nothing else), and then the server brings them back, as Roblox does
 * after its five seconds. It brings them back the way Minecraft's respawn button does (ServerPlayNetworkHandler
 * .onClientStatus: their spawn point, a hardcore world's spectator, every AFTER_RESPAWN rule after it: the long
 * cooldowns begin in full, LongCooldowns), so nothing else about coming back changes. Its five seconds are the server's
 * ticks from the tick it first finds them dead; an ask to come back before then (an older client's respawn button) is
 * let go unanswered (AutoRespawnMixin), so the five seconds hold for every client; a world with doImmediateRespawn
 * brings them back at once. While they wait they can do nothing (CombatManager.actionLocked). A client knows the server
 * brings its dead back by the {@link #CHANNEL} it takes (an older server's players are still shown Minecraft's death
 * screen, as that server waits for its button). One who leaves dead and joins again waits five seconds from the join.
 * Off the map (another dimension, a world that is not the Botand map) dying is Minecraft's own, death screen and all.
 */
public final class AutoRespawn {
    /**
     * Taken by the server, though nothing is sent on it: a client that sees the server takes it (ClientPlayNetworking
     * .canSend) knows this server brings its dead back by itself, and opens no death screen.
     */
    public static final Identifier CHANNEL=new Identifier("celestialfall","auto_respawn_v1");
    /** Server ticks from the death to coming back to life: five seconds. */
    public static final int DELAY=100;
    /** The server tick each player waiting to come back was first found dead. */
    private static final Map<UUID,Integer> DEAD=new HashMap<>();
    private AutoRespawn(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,(server,p,handler,buffer,sender)->{});
        ServerTickEvents.END_SERVER_TICK.register(AutoRespawn::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->DEAD.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->DEAD.clear());
    }
    /**
     * Whether {@code p} lies dead on the Botand map, waiting to be brought back: a player in the server's list (not a boss,
     * whose body goes a second after it falls as Minecraft's dead do, Bosses), connected, not one leaving the End (alive).
     */
    public static boolean waits(ServerPlayerEntity p){
        return p.isDead()&&!p.notInAnyWorld&&!p.isDisconnected()&&p.getServer()!=null&&p.getServer().getPlayerManager().getPlayerList().contains(p)&&MapGate.enabled(p.getWorld());
    }
    /** Server ticks {@code p} waits dead: {@link #DELAY}, none in a world with doImmediateRespawn. */
    public static int delay(ServerPlayerEntity p){return p.getWorld().getGameRules().getBoolean(GameRules.DO_IMMEDIATE_RESPAWN)?0:DELAY;}
    /**
     * Whether one first found dead at server tick {@code since} comes back at {@code now} after waiting {@code delay}
     * ticks. A clock gone back (never, but for a tick count from before a restart) holds nobody.
     */
    public static boolean due(int since,int now,int delay){return now-since>=delay||now<since;}
    /** Whether an ask from {@code p} to come back to life comes too soon: dead on the map and not yet five seconds. */
    public static boolean early(ServerPlayerEntity p){
        if(!waits(p))return false;
        Integer since=DEAD.get(p.getUuid());
        return since==null||!due(since,p.getServer().getTicks(),delay(p));
    }

    private static void tick(MinecraftServer server){
        int now=server.getTicks();List<ServerPlayerEntity> back=null;
        for(var p:server.getPlayerManager().getPlayerList()){
            if(!waits(p)){DEAD.remove(p.getUuid());continue;}
            int since=DEAD.computeIfAbsent(p.getUuid(),id->now);
            if(due(since,now,delay(p))){if(back==null)back=new ArrayList<>();back.add(p);}
        }
        // after the loop: coming back replaces the player in the server's list
        if(back!=null)for(var p:back)respawn(p,now);
    }
    /**
     * Brings {@code p} back as Minecraft's respawn button does (their client is told as it would be then). Their body,
     * lying where they fell until now (DeadBodyMixin), goes in Minecraft's puff of smoke.
     */
    private static void respawn(ServerPlayerEntity p,int now){
        Integer since=DEAD.get(p.getUuid());
        if(!p.isRemoved())p.getWorld().sendEntityStatus(p,net.minecraft.entity.EntityStatuses.ADD_DEATH_PARTICLES);
        p.networkHandler.onClientStatus(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
        DEAD.remove(p.getUuid());
        var back=p.networkHandler.player;
        CelestialFall.LOGGER.info("Auto respawn: {} back to life after {} ticks ({})",p.getEntityName(),since==null?-1:now-since,back!=p&&back.isAlive()?"respawned":"still dead");
    }
}
