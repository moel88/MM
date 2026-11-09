package dev.sangwoo.celestialfall.combat;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The hit count (2.108.0, user request: "히트 수 표시가 카게노 H에만 있는데 모든 스탠드에 모든 스킬 평타에 히트 수가 뜨게
 * 바꿔줘"): every blow a player lands on another living thing in the map counts, whatever dealt it: any stand's or
 * character's basic attack or skill (Kageno's, Star Platinum's, Lee So-sang's) or the bare hand's. Before, only Kageno's
 * skills counted (KagenoManager), and its counter's place was the cooldown list's, so it was seen only in his H's
 * cutscene, where that list is not drawn.
 * <p>
 * A blow counts once for each one it lands on ({@link #landed}: {@code LivingEntity.damage} let it through, so a blow a
 * guard takes, one a dodge or a shield turns aside, and one swallowed by the moment after another do not), or once when
 * Star Platinum's stopped time keeps it for later (it counts as landed then; what it kept is not counted again as time
 * moves). A blow that lands without harm counts too ({@link #struck}: the beatdown's opening punch and its blows held
 * back at 1 health). The count runs on while each blow comes within {@link #COMBO} ticks of the one before and starts
 * again after ({@link #next}); a skill whose own blows come further apart keeps it open meanwhile ({@link #hold}: the
 * beatdown, whose opening punch lands some 4 s before the uppercut, the cutscene's words between). It is told to the
 * player who dealt it at the end of the tick ("kageno_hits_v1", its name kept: a 2.107 client still shows Kageno's): the
 * count, how many of it came this tick, and how many ticks on it is held open.
 */
public final class HitCounter {
    public static final Identifier CHANNEL=new Identifier(CelestialFall.ID,"kageno_hits_v1");
    /** Ticks a count waits for its next hit (2.5 s, as Kageno's did): its display is gone by then (HitCounterHud). */
    public static final int COMBO=50;
    /** The most a count tells (its display's digits). */
    public static final int MOST=9999;
    private static final class Count {int hits,added;long last=Long.MIN_VALUE/2,heldUntil=Long.MIN_VALUE/2;boolean dirty;}
    private static final Map<UUID,Count> COUNTS=new HashMap<>();
    /** How many hits a blow counts while {@link #weighing} runs it (1 but for Kageno's R's stored cut). */
    private static final ThreadLocal<Integer> WEIGHT=ThreadLocal.withInitial(()->1);
    private static MinecraftServer server;
    private HitCounter(){}

    public static void initialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->server=s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{server=null;COUNTS.clear();});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->s.execute(()->COUNTS.remove(h.player.getUuid())));
        ServerTickEvents.END_SERVER_TICK.register(HitCounter::tell);
    }

    /** The count after {@code n} more hits {@code now}, the one before having come at {@code last}. */
    public static int next(int hits,long last,long now,int n){return Math.min(MOST,now-last>COMBO?n:hits+n);}

    /** A blow landed on {@code target} (LivingEntity.damage let it through, or stopped time kept it): counted for the player who dealt it. */
    public static void landed(LivingEntity target,DamageSource source){
        if(source.getAttacker() instanceof ServerPlayerEntity p)count(p,target,WEIGHT.get());
    }
    /** A blow {@code attacker} landed on {@code target} that dealt no harm: counted all the same. */
    public static void struck(ServerPlayerEntity attacker,LivingEntity target){count(attacker,target,1);}
    /**
     * {@code p}'s count stays open {@code ticks} on whatever comes between (its skill's own blows come further apart than
     * {@link #COMBO}: the beatdown, from its opening punch to the landing). Counted on as its blows come; it may run out
     * {@link #COMBO} ticks after the later of its last blow and the hold.
     */
    public static void hold(ServerPlayerEntity p,int ticks){
        if(server==null||p instanceof dev.sangwoo.celestialfall.boss.BossPlayer)return;
        long now=server.getTicks();var c=COUNTS.computeIfAbsent(p.getUuid(),u->new Count());
        if(now+ticks>c.heldUntil){c.heldUntil=now+ticks;c.dirty=true;}
    }
    /** The hold on {@code p}'s count ends now (the skill holding it was cut short). */
    public static void release(ServerPlayerEntity p){
        if(server==null)return;
        long now=server.getTicks();var c=COUNTS.get(p.getUuid());
        if(c!=null&&c.heldUntil>now){c.heldUntil=now;c.dirty=true;}
    }
    /** Runs {@code blow} with each hit it lands counting {@code n} (Kageno's R: its stored cut is R_CUTS slashes in one blow). */
    public static boolean weighing(int n,BooleanSupplier blow){
        int before=WEIGHT.get();WEIGHT.set(Math.max(1,n));
        try{return blow.getAsBoolean();}finally{WEIGHT.set(before);}
    }
    private static void count(ServerPlayerEntity p,LivingEntity target,int n){
        if(p==target||target.getWorld().isClient||p instanceof dev.sangwoo.celestialfall.boss.BossPlayer||p.isRemoved())return;
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p)||server==null)return;
        long now=server.getTicks();var c=COUNTS.computeIfAbsent(p.getUuid(),u->new Count());
        // (held open, it runs out only that long after the hold)
        c.hits=next(c.hits,Math.max(c.last,c.heldUntil),now,n);c.added=Math.min(c.hits,c.added+n);c.last=now;c.dirty=true;
    }
    /** At the end of the tick, each player whose count grew hears it once. */
    private static void tell(MinecraftServer s){
        for(var e:COUNTS.entrySet()){
            var c=e.getValue();if(!c.dirty)continue;
            c.dirty=false;
            var p=s.getPlayerManager().getPlayer(e.getKey());
            if(p==null||!ServerPlayNetworking.canSend(p,CHANNEL))continue;
            var b=PacketByteBufs.create();b.writeVarInt(c.hits);b.writeVarInt(c.added);b.writeVarInt((int)Math.max(0,Math.min(Integer.MAX_VALUE,c.heldUntil-s.getTicks())));
            ServerPlayNetworking.send(p,CHANNEL,b);
        }
        for(var c:COUNTS.values())c.added=0;
    }
}
