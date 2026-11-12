package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.CutsceneGlow;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandHolder;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * X Chara's Q, the hack (2.115.0, user request: "Q를 누르면 검이 빛나고 빛나는 보라색 테두리가 생기고 다른 손에는 해킹 패널이
 * 생겨 Q를 다시 누르면 해제하고", and the user's video of it coming on): a state their Q turns on and off. On, their knife
 * is lit white within a glowing purple rim and a hacking panel is in their other hand; the user named nothing else of
 * it, so it does nothing else (their other skills, as the user gives them, may ask {@link #on}).
 * <p>
 * Their client's Q asks (xchara_skill_v1 with {@link XCharaRules#Q}, at most one heard every
 * {@link XCharaRules#HACK_RATE} ticks) and the server decides: it keeps the answer on the player as tracked data (a bit
 * of the stand's byte, StandHolder.botand$isHacking), so every client that sees the player sees it come on, stay and go
 * (client/xchara/HackMode). It goes off by itself once its user cannot have it: dead, a spectator, asleep, off the map
 * (or in their vanilla mode), or no longer X Chara. A new body (a respawn, the next login) starts with it off. Q is not
 * heard while its user can do nothing ({@link #held}: staggered, incapacitated, held by another's skill or cutscene, in
 * their own sword dance or glowing before it); a hit of their knife does not keep it from them.
 */
public final class XCharaHack {
    private static final Map<UUID,Integer> NEXT=new HashMap<>();
    private XCharaHack(){}
    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var p:server.getPlayerManager().getPlayerList())if(on(p)&&!may(p))((StandHolder)p).botand$setHacking(false);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->NEXT.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->NEXT.clear());
    }
    /** Whether this player's hack is on (on the server, or as a client was told). */
    public static boolean on(PlayerEntity p){return p instanceof StandHolder h&&h.botand$isHacking();}
    /** What having it on takes: X Chara's player, alive and awake on the map. */
    public static boolean may(ServerPlayerEntity p){return StandManager.xChara(p)&&!p.isSleeping();}
    /** Whether nothing its user does goes through now (their Q is not heard). */
    public static boolean held(ServerPlayerEntity p){
        // (2.119.0) nor while they press the panel for their E (the hack going off would stop it short)
        return CombatManager.incapacitated(p)||CutsceneGlow.glowing(p)||XCharaUltimate.involved(p)||StarPlatinumBeatdown.involved(p)||KagenoManager.frozen(p)||XCharaHeal.busy(p)
            // (2.121.0) nor through their cleave (the giant knife is the hack's)
            ||XCharaAttack.cleaving(p);
    }
    /** A Q from their client: heard at most once every HACK_RATE ticks. */
    static void request(ServerPlayerEntity p){
        int now=p.getServer().getTicks();
        if(now<NEXT.getOrDefault(p.getUuid(),0))return;
        NEXT.put(p.getUuid(),now+XCharaRules.HACK_RATE);
        toggle(p);
    }
    /** Q: on if it is off and its user may have it on, off if it is on. Returns whether it is on now. */
    public static boolean toggle(ServerPlayerEntity p){
        if(held(p))return on(p);
        boolean next=XCharaRules.hackAfter(on(p),may(p));
        if(next!=on(p))((StandHolder)p).botand$setHacking(next);
        return next;
    }
}
