package dev.sangwoo.celestialfall.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

/**
 * Lag compensation (2.157.0, the combat engine): a player's blow is judged against where its targets stood as that
 * player saw them, not where the server has them now.
 * <p>
 * The server keeps where every living thing near a player stood each tick for the last {@link #KEEP} ticks
 * ({@link #record}). A player's client sends with its press the world time it showed (its clock, behind the server's
 * by the time the news takes to reach it); what it showed of others is {@link #INTERP} ticks further behind (the game
 * slides a tracked body to its new place over its next ticks). So the targets are wound back {@link #offset} ticks: the
 * server's tick as the press arrives less the client's, plus that slide, at most {@link #MAX} (a player farther behind
 * than that is judged as if they were only that far). A boss's blows, and a client that sent no time, are judged as
 * things stand.
 */
public final class Rewind {
    private Rewind(){}
    /** How many ticks of the past are kept. */
    public static final int KEEP=12;
    /** At most this many ticks are wound back (0.4 s). */
    public static final int MAX=8;
    /** How far behind its news a client shows other bodies (ticks): the game slides them to a new place over three ticks, about two behind. */
    public static final int INTERP=2;
    /** How far from a player a living thing's past is kept (blocks). */
    public static final double NEAR=48;

    /**
     * Ticks to wind targets back for a press that reached the server at {@code now}, sent by a client showing
     * {@code clientTick} (negative: it sent none, nothing is wound back).
     */
    public static int offset(long now,long clientTick){
        if(clientTick<0)return 0;
        return (int)Math.max(0,Math.min(MAX,now-clientTick+INTERP));
    }

    private static final class Track {
        final long[] ticks=new long[KEEP];final Box[] boxes=new Box[KEEP];int next;
        Track(){java.util.Arrays.fill(ticks,Long.MIN_VALUE);}
        void put(long tick,Box box){
            int last=(next+KEEP-1)%KEEP;
            if(ticks[last]==tick){boxes[last]=box;return;}
            ticks[next]=tick;boxes[next]=box;next=(next+1)%KEEP;
        }
        Box at(long tick){for(int i=0;i<KEEP;i++)if(ticks[i]==tick)return boxes[i];return null;}
    }
    private static final Map<ServerWorld,Map<Integer,Track>> TRACKS=new HashMap<>();

    /** Writes down where every living thing near a player in a world on the map stands now (once a tick, before blows are judged). */
    public static void record(MinecraftServer server,Iterable<ServerPlayerEntity> fighters){
        Map<ServerWorld,Set<LivingEntity>> near=new HashMap<>();
        for(var p:fighters){
            if(!(p.getWorld() instanceof ServerWorld world)||!dev.sangwoo.celestialfall.map.MapGate.enabled(world))continue;
            var set=near.computeIfAbsent(world,w->new HashSet<>());set.add(p);
            set.addAll(world.getEntitiesByClass(LivingEntity.class,p.getBoundingBox().expand(NEAR),e->e.isAlive()&&!e.isSpectator()));
        }
        TRACKS.keySet().retainAll(near.keySet());
        for(var e:near.entrySet()){
            var tracks=TRACKS.computeIfAbsent(e.getKey(),w->new HashMap<>());long now=e.getKey().getTime();
            Set<Integer> seen=new HashSet<>();
            for(var living:e.getValue()){tracks.computeIfAbsent(living.getId(),id->new Track()).put(now,living.getBoundingBox());seen.add(living.getId());}
            tracks.keySet().retainAll(seen);
        }
    }
    /** Where {@code e} stood at {@code tick} (its box as written down then; as it stands now if none was). */
    public static Box boxAt(LivingEntity e,long tick){
        if(e.getWorld() instanceof ServerWorld world){
            var tracks=TRACKS.get(world);var t=tracks==null?null:tracks.get(e.getId());
            var box=t==null?null:t.at(tick);
            if(box!=null)return box;
        }
        return e.getBoundingBox();
    }
    public static void clear(){TRACKS.clear();}
}
