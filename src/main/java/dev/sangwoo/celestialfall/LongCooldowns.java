package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.boss.BossPlayer;
import dev.sangwoo.celestialfall.boss.BossRules;
import dev.sangwoo.celestialfall.boss.Bosses;
import dev.sangwoo.celestialfall.stand.StarPlatinumAttack;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

/**
 * The long cooldowns (2.73.0, user request: "카게노 아토믹 쿨타임은 10분으로 하고 게임 나갔다 오거나 재설정하면 쿨타임 다시
 * 10분부터 흐르게 시간정지도 똑같이 바꿔줘 ... 비트다운도 쿨타임 2분(죠타로 보스도 적용)으로 올리고 아토믹이랑 똑같은 메커니즘
 * 적용"): Kageno's Atomic (ten minutes), Star Platinum's time stop (two and a half) and its beatdown (two). Each skill's
 * own ledger (AtomicManager, StarPlatinumTimeStop, StarPlatinumAttack) writes here the tick it is ready again as it sets
 * it, and this keeps them per player with the world: leaving, a server's restart or a world reopened keep them too. One
 * still running when its player comes back into the game (joins: back on a server, or the world reopened) begins again
 * in full from then; one that ran out meanwhile stays over. Since 2.74.0 (user request: "그냥 플레이어한테 죽어도 처음부터
 * 쿨타임 돌게 하고 보스한테 죽어도 처음부터 쿨타임 돌게 그냥 화살로 뽑아도 처음으로 쿨타임 돌게 그냥 죽어서 부활하기만 하면
 * 처음부터 돌게") the same happens whenever they come back to life after a death, whatever killed them (a player, a boss,
 * the stand arrow, their own character reset, a fall: 2.73.0 kept them running through every death but the reset, which
 * began them again as it killed), and when the stand arrow gives them a stand (StandArrow). The player's client is told
 * each one's ready tick and length (the cooldown list draws them from it), and the Atomic sword's cooldown, which
 * Minecraft keeps on the body, is set again on every new one. Bosses keep none: they never come back.
 */
public final class LongCooldowns extends PersistentState {
    /** The long cooldowns, in the packet's and the save's order. */
    public enum Skill{ATOMIC,TIME_STOP,BEATDOWN}
    /** To a player's client: for each skill in turn, the tick it is ready again (world time, 0 for none) and its length in ticks. */
    public static final Identifier STATE=new Identifier("celestialfall","long_cooldowns_v1");
    private static final int N=Skill.values().length;
    /** Each player's ready ticks, by skill; only players with one still to run (or not yet known to have run out). */
    private final Map<UUID,long[]> players=new HashMap<>();

    public static LongCooldowns get(MinecraftServer server){
        return server.getOverworld().getPersistentStateManager().getOrCreate(LongCooldowns::read,LongCooldowns::new,"celestialfall_long_cooldowns");
    }
    public static void initialize(){
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->begin(handler.player));
        // back to life after a death, whatever killed them: what is running begins again in full (2.74.0); a new body back
        // from the End (alive) only has the sword's cooldown put on it again
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->{if(alive)respawned(p);else begin(p);});
        // a client that joins says which packets it takes just after joining: told then too. Queued, not run at once: on
        // a world opened in the game the client says so while its connection is still being set up, and asking then
        // whether it takes a packet fails (the game could not place the player)
        S2CPlayChannelEvents.REGISTER.register((handler,sender,server,channels)->{
            if(channels.contains(STATE))server.send(new ServerTask(server.getTicks(),()->{if(!handler.player.isDisconnected())send(handler.player);}));
        });
    }

    /** The tick {@code id}'s {@code skill} is ready again (0 for none kept). */
    public long ready(UUID id,Skill skill){var r=players.get(id);return r==null?0:r[skill.ordinal()];}
    /** Keeps the tick {@code id}'s {@code skill} is ready again (0 for none); returns whether that changed anything. */
    public boolean put(UUID id,Skill skill,long ready){
        var r=players.get(id);
        if(r==null){if(ready==0)return false;r=new long[N];players.put(id,r);}
        if(r[skill.ordinal()]==ready)return false;
        r[skill.ordinal()]=ready;
        if(Arrays.stream(r).allMatch(x->x==0))players.remove(id);
        markDirty();return true;
    }
    /** The players any are kept for. */
    Set<UUID> players(){return players.keySet();}

    /**
     * When a cooldown ready at {@code ready} is ready once it begins again at {@code now} (its player came back, came back
     * to life, or was given a stand): one still running begins again in full ({@code length} ticks from now); one over
     * (or none, 0) stays over; one whose skill is still going on (an Atomic playing out after its caster died or left:
     * ready more than a whole length away) keeps its tick, to run in full once the skill is over.
     */
    public static long restart(long ready,long now,int length){return ready>now?Math.max(ready,now+length):0;}
    /** The length of {@code p}'s {@code skill}'s cooldown (ticks): Atomic's from the config, a boss's own where it has one. */
    public static int length(ServerPlayerEntity p,Skill skill){
        return switch(skill){
            case ATOMIC->CelestialFall.CONFIG.atomicCooldownSeconds*20;
            case TIME_STOP->Bosses.cooldown(p,BossRules.TIME_STOP,StarPlatinumRules.TS_COOLDOWN);
            case BEATDOWN->Bosses.cooldown(p,BossRules.BEATDOWN,StarPlatinumRules.BEATDOWN_COOLDOWN);
        };
    }

    /**
     * A skill's own ledger has set the tick player {@code id}'s {@code skill} is ready again: kept here (0 once it is
     * over), and their client told if they are here.
     */
    public static void note(MinecraftServer server,UUID id,Skill skill,long ready){
        if(server==null||id==null)return;
        var p=server.getPlayerManager().getPlayer(id);
        if(p instanceof BossPlayer)return;
        long now=server.getOverworld().getTime();
        if(get(server).put(id,skill,ready>now?ready:0)&&p!=null)send(p);
    }
    /** Every player's {@code skill} is over (an operator's clear): forgotten, and the clients here told. */
    public static void forget(MinecraftServer server,Skill skill){
        var state=get(server);
        for(var id:new ArrayList<>(state.players()))if(state.put(id,skill,0)){var p=server.getPlayerManager().getPlayer(id);if(p!=null)send(p);}
    }
    /**
     * {@code p} comes back into the game (joins), comes back to life after a death (respawns), or is given a stand by the
     * stand arrow: each long cooldown still running begins again in full now, the skills' own ledgers and the sword's
     * cooldown set to it; one over is forgotten. Their client is told.
     */
    public static void begin(ServerPlayerEntity p){
        if(p instanceof BossPlayer||p.getServer()==null)return;
        var state=get(p.getServer());long now=p.getWorld().getTime();
        boolean free=NoCooldownState.enabled(p);
        for(var skill:Skill.values()){
            long ready=free?0:restart(state.ready(p.getUuid(),skill),now,length(p,skill));
            state.put(p.getUuid(),skill,ready);
            apply(p,skill,ready,now);
        }
        send(p);
    }
    /** A new body that did not die (back from the End): the sword's cooldown on it again, for what is left of Atomic's; their client told. */
    static void respawned(ServerPlayerEntity p){
        if(p instanceof BossPlayer||p.getServer()==null)return;
        long now=p.getWorld().getTime();
        AtomicManager.restore(p,get(p.getServer()).ready(p.getUuid(),Skill.ATOMIC),now);
        send(p);
    }
    /** Sets {@code p}'s {@code skill} ready at {@code ready} in its own ledger. */
    private static void apply(ServerPlayerEntity p,Skill skill,long ready,long now){
        switch(skill){
            case ATOMIC->AtomicManager.restore(p,ready,now);
            case TIME_STOP->StarPlatinumTimeStop.restore(p,ready);
            case BEATDOWN->StarPlatinumAttack.restoreBeatdown(p,ready);
        }
    }
    /** Tells {@code p}'s client each long cooldown's ready tick and length. */
    static void send(ServerPlayerEntity p){
        if(p==null||p.getServer()==null||!ServerPlayNetworking.canSend(p,STATE))return;
        var state=get(p.getServer());var b=PacketByteBufs.create();
        for(var skill:Skill.values()){b.writeLong(state.ready(p.getUuid(),skill));b.writeVarInt(length(p,skill));}
        ServerPlayNetworking.send(p,STATE,b);
    }

    private static String key(Skill s){return switch(s){case ATOMIC->"Atomic";case TIME_STOP->"TimeStop";case BEATDOWN->"Beatdown";};}
    public static LongCooldowns read(NbtCompound nbt){
        var state=new LongCooldowns();var list=nbt.getList("Players",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++){
            var row=list.getCompound(i);if(!row.containsUuid("Id"))continue;
            long[] r=new long[N];for(var s:Skill.values())r[s.ordinal()]=Math.max(0,row.getLong(key(s)));
            if(Arrays.stream(r).anyMatch(x->x!=0))state.players.put(row.getUuid("Id"),r);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt){
        var list=new NbtList();
        players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            var row=new NbtCompound();row.putUuid("Id",e.getKey());
            for(var s:Skill.values())row.putLong(key(s),e.getValue()[s.ordinal()]);
            list.add(row);
        });
        nbt.put("Players",list);return nbt;
    }
}
