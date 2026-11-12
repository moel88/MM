package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;

/**
 * X Chara's E with their hack on, the restore (2.119.0, user request: "Q활성화 E스킬부터 만들자 간단하게 패널 누르는 모션
 * 나오고 몸에 연두 이펙트 둘러지고 65힐"). Their client's E asks (xchara_skill_v1 with XCharaRules.E) and the server
 * decides: with the hack on (Q, XCharaHack), nothing else holding them and its cooldown over, they stand where they are
 * and press their panel; as the press lands (HEAL_AT ticks in) they are healed HEAL, and it is over at HEAL_LENGTH. Its
 * cooldown runs from its end (HEAL_COOLDOWN). Struck, staggered, held or the hack gone before the press lands, it stops
 * short: no heal, and its cooldown runs from then (as every skill cut short). After the heal nothing stops it.
 * <p>
 * Every viewer near its user is told when it starts (with when it will be ready again if it runs its course), when the
 * heal lands (how much it gave) and when it stops short (xchara_heal_event_v1); the clients draw the press, the lime
 * light round the body and play its sound (client/xchara/HealClient).
 */
public final class XCharaHeal {
    /** Every viewer's news of it. */
    public static final Identifier EVENT=CelestialFall.id("xchara_heal_event_v1");
    /** It starts; its heal lands; it stops short before the heal. */
    public static final int START=0,HEALED=1,STOPPED=2;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** When the one in flight began (none: long ago), whether its heal has landed, and when E is ready again (0 for at once). */
        long start=Long.MIN_VALUE/2;boolean healed;long ready;
        boolean running(long now){return now>=start&&now<start+HEAL_LENGTH;}
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private XCharaHeal(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaHeal::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->STATES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){s=new State(p);STATES.put(p.getUuid(),s);}
        return s;
    }
    /** Whether this player's E holds them where they stand now (HEAL_HOLD): CombatManager.busy and actionLocked count it. */
    public static boolean busy(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s!=null&&s.world==p.getWorld()&&healHolds(s.start,p.getWorld().getTime());
    }
    /** When this player's E is ready again (the server's world time; 0 if it is). */
    public static long ready(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player is X Chara's no more: its cooldown is gone (StandManager.forgetOthers). */
    public static void forget(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)s.ready=0;}
    /** For the multiplayer check: this player's E as {when it began, whether its heal landed, when it is ready again}, or null. */
    public static long[] describe(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?null:new long[]{s.start,s.healed?1:0,s.ready};}

    /** E from their client. Returns whether it began. */
    public static boolean request(ServerPlayerEntity p){
        if(!StandManager.xChara(p)||p.isSpectator()||!p.isAlive()||p.isSleeping()||p.hasVehicle())return false;
        // the hack's E: with the hack off, E is not this skill (nothing yet)
        if(!XCharaHack.on(p)||XCharaHack.held(p))return false;
        if(CombatManager.busy(p)||CombatManager.cinematic(p)||CombatManager.actionLocked(p)||StarPlatinumTimeStop.holds(p)||StarPlatinumTimeStop.active(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(s.running(now)||now<s.ready&&!NoCooldownState.enabled(p))return false;
        s.start=now;s.healed=false;
        s.ready=NoCooldownState.enabled(p)?0:healReady(now);
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s,START,0);
        return true;
    }
    /** What stops it short before the press lands: its user struck, held, or without the hack. */
    private static boolean held(ServerPlayerEntity p){
        return !StandManager.xChara(p)||!XCharaHack.on(p)||!p.isAlive()||p.isSleeping()||p.hasVehicle()||HitStun.stunned(p)||CombatManager.incapacitated(p)
            ||CombatManager.cinematic(p)||KagenoManager.frozen(p)||XCharaUltimate.involved(p);
    }
    /** The user was struck (harmed: HitStun.attackerDamaged; staggered or incapacitated: CombatManager): before the press lands, it stops short. */
    public static void struck(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());}
    private static void stop(ServerPlayerEntity p,State s,long now){
        if(!s.running(now)||s.healed)return;
        s.start=Long.MIN_VALUE/2;
        s.ready=NoCooldownState.enabled(p)?0:now+HEAL_COOLDOWN;
        send(p,s,STOPPED,0);
    }
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            if(!s.running(now)||s.healed)continue;
            if(held(p)){stop(p,s,now);continue;}
            if(now>=s.start+HEAL_AT){
                float before=p.getHealth();
                p.heal(HEAL);
                s.healed=true;
                send(p,s,HEALED,p.getHealth()-before);
            }
        }
    }
    /** Tells every viewer near the user (and the user): whose, which event, when it began and the server's time now, when E is ready again, and what the heal gave. */
    private static void send(ServerPlayerEntity p,State s,int type,float healed){
        var world=p.getServerWorld();long now=world.getTime();
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);
            b.writeLong(type==STOPPED?now:s.start);b.writeLong(now);b.writeLong(s.ready);b.writeFloat(healed);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
