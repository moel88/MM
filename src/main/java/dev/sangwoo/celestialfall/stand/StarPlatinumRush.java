package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.map.MapGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;

/**
 * Star Platinum's rush, its E (2.57.0, user request: "이번에는 e스킬 만들자 e홀드로 사용 할 수 있고 도중에 끊으면 바로
 * 끊겨 러시고 연타를 빠르게 꽂는거야 ... 총 40 데미지 박히면 되고 연타마다 나눠서 꽂히면 돼"; StarPlatinumRules for the
 * timing). The user's client says when E goes down and when it comes up (stand_rush_v1: one byte, 1 down, 0 up) and the
 * server decides: the rush starts as E goes down if it may (the stand out, its user free, the rush ready again), lands a
 * blow every RUSH_EVERY ticks on everyone in its way the way its user faces at that blow (StarPlatinumRules.rushLane, in
 * sight, the PvP rule), each blow holding whom it strikes past the next (HitStun.standHold), and stops the moment E
 * comes up, once its blows have run out, or when its user is struck, sent back or can no longer have the stand out.
 * A punch in flight as E goes down stops short and the rush starts once the stand has turned back to its guard (a few
 * ticks; it is on from E going down, its start then in the future); once it is over a punch waits while its fists go
 * back to the guard (StarPlatinumRules.RUSH_SETTLE).
 * Its user turns and walks through it at a tenth of their speed (user request: "방향전환 걷기는 다 되지만 이동속도가 90%
 * 느려져": RUSH_SLOW, a modifier on their speed) and cannot run, jump or punch. A blow on a guard is taken by the guard;
 * none knocks back, and only the first a target takes cries out (the rush's own sound carries the rest). Its cooldown
 * runs from its end. Every viewer within 128 blocks is told when it starts, each blow (whom it struck, which of them on
 * their guard) and when it ends (stand_rush_event_v1); the clients draw the barrage and play its sound.
 */
public final class StarPlatinumRush {
    public static final Identifier INPUT=CelestialFall.id("stand_rush_v1"),EVENT=CelestialFall.id("stand_rush_event_v1");
    /** It starts; a blow lands (whom it struck); it ends (let go, run out or stopped short). */
    public static final int START=0,BLOW=1,END=2;
    /** Its user's slow walk (StarPlatinumRules.RUSH_SLOW; the client keeps its lens still while it is on). */
    public static final UUID SLOW=UUID.fromString("9d3c2f61-6c47-4e0a-8a55-1f6b7d2e4c90");
    /** At most this many presses and releases a second are heard. */
    static final int RATE=20;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /**
         * Whether it is on (from when E went down: it starts once a punch it stopped short is back in its guard), when
         * it starts, when the last one ended and the tick it is ready again after it.
         */
        boolean on;long start=-1000,ended=-1000,ready;
        /** E is down while the heavy punch is in flight (2.59.0): the rush starts once it is over, if E is still down. */
        boolean waiting;
        /** Whom it has struck so far (their first blow cries out, the rest are quiet). */
        final Set<UUID> struck=new HashSet<>();
        int window,presses;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private StarPlatinumRush(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=1)return;
            boolean down=buf.readByte()!=0;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                // a release is always heard (a rush must never run on for want of one)
                if(down&&++s.presses>RATE)return;
                if(down)start(player);else release(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(StarPlatinumRush::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{slow(handler.player,false);STATES.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){s=new State(p);STATES.put(p.getUuid(),s);}
        return s;
    }
    /** Whether this player's stand is rushing now. */
    public static boolean rushing(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.on;}
    /** The tick this player's rush (on now) began its blows' clock, for a boss to read (2.69.0, BossThreats; MIN_VALUE if it is not on). */
    public static long startedAt(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null||!s.on?Long.MIN_VALUE:s.start;}
    /** The tick this player's rush is ready again (0 if they have not rushed). */
    public static long ready(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** The tick this player's stand is back in its guard after their last rush, and may punch (StarPlatinumRules.RUSH_SETTLE). */
    public static long freeAt(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.ended+RUSH_SETTLE;}

    /** E goes down: the rush starts if it may. Returns whether it did. */
    public static boolean start(ServerPlayerEntity p){
        if(!StarPlatinumAttack.punches(p)||p.isSleeping()||p.hasVehicle()||CombatManager.busy(p)||CombatManager.cinematic(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(s.on||now<s.ready&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        // the heavy punch (R, 2.59.0) and the Star Finger (T, 2.60.0) are not cut off: the rush waits for them to be over
        if(now<StarPlatinumAttack.skillOverAt(p)){s.waiting=true;return false;}
        s.waiting=false;
        // a punch in flight gives way to it: it starts once the stand has turned back to its guard (StarPlatinumRules.STOP_RETURN)
        StarPlatinumAttack.yieldTo(p);
        s.on=true;s.start=Math.max(now,StarPlatinumAttack.guardAt(p));s.struck.clear();
        p.setSprinting(false);p.clearActiveItem();slow(p,true);
        send(p,s,START,null,null);
        return true;
    }
    /** E comes up: the rush stops at once. */
    public static void release(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s==null)return;s.waiting=false;if(s.on)end(p,s,p.getWorld().getTime());}
    /** Its user was struck: the rush stops. */
    public static void struck(ServerPlayerEntity p){release(p);}
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            if(s.waiting&&!s.on&&p.getWorld().getTime()>=StarPlatinumAttack.skillOverAt(p)){s.waiting=false;start(p);}
            if(!s.on)continue;
            long now=p.getWorld().getTime(),age=now-s.start;
            if(!StarPlatinumAttack.punches(p)||HitStun.stunned(p)||p.isSleeping()||p.hasVehicle()||CombatManager.cinematic(p)){end(p,s,now);continue;}
            p.setSprinting(false);
            if(rushBlow(age))blow(p,s,now);
            if(age>=RUSH_LENGTH)end(p,s,now);
        }
    }
    private static void end(ServerPlayerEntity p,State s,long now){
        // its cooldown from now (none for a player the cooldowns are off for: /botand nocooldown)
        s.on=false;s.ended=now;s.ready=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?now:rushReady(now);
        slow(p,false);
        // whom it held go at once
        HitStun.releaseStand(p.getUuid());
        send(p,s,END,null,null);
    }
    /**
     * A blow lands: everyone in the rush's way in front of its user now, in sight, is struck and held past the next
     * blow; a guard takes it. Their first blow cries out, the rest are quiet.
     */
    private static void blow(ServerPlayerEntity p,State s,long now){
        var world=p.getServerWorld();float yaw=MathHelper.wrapDegrees(p.getYaw());
        Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        var source=world.getDamageSources().playerAttack(p);
        List<LivingEntity> struck=new ArrayList<>();List<Boolean> guarded=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(RUSH_REACH+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            if(!rushLane(d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            e.timeUntilRegen=0;
            boolean first=!s.struck.contains(e.getUuid());
            boolean hurt=first?CombatManager.damageWithoutKnockback(e,source,RUSH_DAMAGE):CombatManager.damageQuietly(e,source,RUSH_DAMAGE);
            if(hurt)s.struck.add(e.getUuid());
            // a blow the guard took still lands on the guard (it is not held then: the guard holds)
            boolean onGuard=!hurt&&e instanceof ServerPlayerEntity sp&&CombatManager.guarding(sp);
            if(!hurt&&!onGuard)continue;
            struck.add(e);guarded.add(onGuard);
            if(hurt)HitStun.standHold(p,e,1,now+RUSH_HOLD);
        }
        if(!struck.isEmpty())p.onAttacking(struck.get(0));
        send(p,s,BLOW,struck,guarded);
    }
    /** Its user's slow walk while it lasts. */
    private static void slow(ServerPlayerEntity p,boolean on){
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
        if(!on){attr.removeModifier(SLOW);return;}
        if(attr.getModifier(SLOW)==null)attr.addTemporaryModifier(new EntityAttributeModifier(SLOW,"Star Platinum rush slow walk",-RUSH_SLOW,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    /** Tells every player near the user (and the user, and whoever was struck) of the event. */
    private static void send(ServerPlayerEntity p,State s,int type,List<LivingEntity> struck,List<Boolean> guarded){
        var world=p.getServerWorld();long now=world.getTime();float yaw=MathHelper.wrapDegrees(p.getYaw());
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128&&(struck==null||!struck.contains(viewer)))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(yaw);b.writeLong(s.ready);
            int n=struck==null?0:struck.size();b.writeVarInt(n);
            for(int i=0;i<n;i++){var e=struck.get(i);b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY());b.writeDouble(e.getZ());b.writeBoolean(guarded.get(i));}
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
