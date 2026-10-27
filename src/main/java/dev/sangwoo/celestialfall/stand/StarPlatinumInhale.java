package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
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
 * Star Platinum's inhale, its Y (2.61.0, user request: "이번에는 Y스킬 만들자 스타플래티나가 최대 3블록까지 앞에 있는
 * 플레이어를 입으로 빨아들여서 5초동안 끌고오게 하는 스킬 가드 가능하고 데미지 없음 빨아들이는시간동안은 스타핑거 처럼
 * 무력화"; 2.62.0: "Y는 아무도 안 맞아도 5초동안 지속되고 E처럼 중간에 손 떼면 바로 취소 흡입 무력화는 그냥 95% 슬로우에
 * 아무 스킬 + 대쉬 평타 사용 못하게 그리고 흡입 끝나도 0.5초 정도는 더 무력화", "그리고 흡입은 최대 1명만";
 * StarPlatinumRules for the timing and the reach). The user's client says when Y goes down and when it comes up
 * (stand_inhale_v2: one byte, 1 down, 0 up, as the rush's E) and the server decides: it starts as Y goes down if it may
 * (the stand out, its user free, it ready again; a punch in flight stops short and it starts once the stand is back in
 * its guard, just after the rush once the rush's fists are, and once the heavy punch or the Star Finger is over if Y is
 * still down then), and stops the moment Y comes up, once it has sucked INHALE_SUCK ticks, or when its user is struck,
 * incapacitated, stunned or sent the stand back. From INHALE_CATCH ticks in it sucks: the nearest one in its way in
 * front of its user, in sight, the PvP rule, held by no other inhale, is caught as soon as they are there (one at a
 * time) unless they hold a guard up against it (CombatManager.guardTakes: the guard holds it off as long as it is up).
 * Whom it caught walks INHALE_HELD_SLOW slower and is incapacitated (CombatManager.incapacitate: no skill, attack, dash
 * or guard) until INHALE_AFTER ticks after it lets them go, takes no damage, and is dragged in to just in front of the
 * stand and held there (StarPlatinumRules.inhaleAnchor) as its user turns and walks (at half their speed). A player is
 * moved by their own client (a player's movement is theirs: client/stand/StandInhale drives it as the server plans it,
 * as T held's dash is driven); anything else the server moves. One it held gone (dead, away), it sucks on and may catch
 * another. Its cooldown runs from its end. Every viewer within 128 blocks (and whom it caught) is told when it starts,
 * whom it caught, whose guard holds it off, whom it let go and when it ends (stand_inhale_event_v2); the clients draw it
 * and play its sound.
 */
public final class StarPlatinumInhale {
    public static final Identifier INPUT=CelestialFall.id("stand_inhale_v2"),EVENT=CelestialFall.id("stand_inhale_event_v2");
    /** It starts; it caught someone; guards hold it off (shown again every BLOCK_EVERY ticks they do); it let go of one it held (gone); it ends. */
    public static final int START=0,CATCH=1,BLOCKED=2,LET=3,END=4;
    /** Its user's slow walk while it lasts (StarPlatinumRules.INHALE_SLOW; the client keeps its lens still). */
    public static final UUID SLOW=UUID.fromString("4f2a8c1e-93b7-4d55-a0e6-6c1d2b9f7e31");
    /** At most this many presses a second are heard (a release always is). */
    static final int RATE=20;
    /** How often (ticks) a guard that holds it off is shown doing so again (its first time it is heard as well). */
    static final int BLOCK_EVERY=10;
    /** The one it holds: what, where it was caught, the tick it was, and until when its incapacitation was to last at the longest. */
    private record Held(LivingEntity entity,Vec3d from,long caught,long until){}
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** Whether it is on (from Y going down: it starts once a punch it stopped short is back in its guard), when it starts, when the last ended and when it is ready again. */
        boolean on;long start=-1000,ended=-1000,ready;
        /** Y went down just after the rush: it starts once the rush's fists are back in the guard; while the heavy punch or the Star Finger flew: once they are over, if Y is still down. */
        boolean queued,waiting;
        /** Whom it holds (null: no one yet, or the one it held is gone). */
        Held held;
        /** Whose guards hold it off now, and the tick each was last shown doing so. */
        final Map<UUID,Long> guards=new HashMap<>();
        int window,presses;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    /** Whom an inhale holds now, and whose. */
    private static final Map<UUID,UUID> HOLDERS=new HashMap<>();
    private StarPlatinumInhale(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=1)return;
            boolean down=buf.readByte()!=0;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                // a release is always heard (it must never run on for want of one)
                if(down&&++s.presses>RATE)return;
                if(down)start(player);else release(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(StarPlatinumInhale::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var s=STATES.get(p.getUuid());
            if(s!=null&&s.on)end(p,s,p.getWorld().getTime());
            slow(p,false);STATES.remove(p.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{STATES.clear();HOLDERS.clear();});
    }
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){s=new State(p);STATES.put(p.getUuid(),s);}
        return s;
    }
    /** Whether this player's stand is inhaling now. */
    public static boolean inhaling(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.on;}
    /** Whether an inhale holds this one now. */
    public static boolean held(LivingEntity e){return e!=null&&HOLDERS.containsKey(e.getUuid());}
    /** The tick this player's inhale (on now) began, for a boss to read (2.69.0, BossThreats; MIN_VALUE if it is not on). */
    public static long startedAt(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null||!s.on?Long.MIN_VALUE:s.start;}
    /** Whom this player's inhale holds now (null for nobody). */
    public static LivingEntity holding(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null||!s.on||s.held==null?null:s.held.entity();}
    /** The tick this player's inhale is ready again (0 if they have not inhaled). */
    public static long ready(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player's stand is not Star Platinum (2.74.2, StandManager): the inhale's cooldown is gone. */
    public static void forget(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)s.ready=0;}

    /** Y goes down: the inhale starts if it may. Returns whether it starts (now, or once the rush's fists are back). */
    public static boolean start(ServerPlayerEntity p){
        // (not in its user's own stopped time, 2.67.0: whom it would catch stand stopped where they are)
        if(!StarPlatinumAttack.able(p)||held(p)||StarPlatinumTimeStop.stopping(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(s.on||now<s.ready&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        // the heavy punch and the Star Finger are not cut off: it waits for them to be over, and starts then if Y is still down
        if(now<StarPlatinumAttack.skillOverAt(p)){s.waiting=true;return false;}
        s.waiting=false;
        if(now<StarPlatinumRush.freeAt(p)){s.queued=true;return true;}
        begin(p,s,now);
        return true;
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        // a punch in flight gives way to it: it starts once the stand has turned back to its guard (StarPlatinumRules.STOP_RETURN)
        StarPlatinumAttack.yieldTo(p);
        s.on=true;s.queued=s.waiting=false;s.held=null;s.guards.clear();
        s.start=Math.max(now,StarPlatinumAttack.guardAt(p));
        p.setSprinting(false);p.clearActiveItem();slow(p,true);
        send(p,s,START,List.of());
    }
    /** Y comes up: the inhale stops at once (one waiting to start never does). */
    public static void release(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s==null)return;s.queued=s.waiting=false;if(s.on)end(p,s,p.getWorld().getTime());}
    /** Its user was struck (or incapacitated): the inhale stops, and lets go of whom it holds. */
    public static void struck(ServerPlayerEntity p){release(p);}
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){if(s.on)letGo(s,s.world.getTime());STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            if(s.waiting&&!s.on&&now>=StarPlatinumAttack.skillOverAt(p)){s.waiting=false;start(p);}
            if(s.queued&&!s.on&&now>=StarPlatinumRush.freeAt(p)){
                s.queued=false;
                if(StarPlatinumAttack.able(p)&&(now>=s.ready||dev.sangwoo.celestialfall.NoCooldownState.enabled(p))&&now>=StarPlatinumAttack.skillOverAt(p))begin(p,s,now);
            }
            if(!s.on)continue;
            if(!StarPlatinumAttack.punches(p)||HitStun.stunned(p)||p.isSleeping()||p.hasVehicle()||CombatManager.cinematic(p)||CombatManager.incapacitated(p)){end(p,s,now);continue;}
            long age=now-s.start;
            p.setSprinting(false);
            if(age>=INHALE_CATCH){
                if(s.held!=null)keep(p,s,now);
                // sucking, it catches the nearest in its way as soon as anyone is there (one at a time; not on its last tick)
                if(s.held==null&&age<INHALE_LENGTH)suck(p,s,now);
                if(s.held!=null)drag(p,s,now);
            }
            if(age>=INHALE_LENGTH)end(p,s,now);
        }
    }
    /**
     * It sucks: the nearest one in its way in front of its user now, in sight, is caught, unless a guard held up against
     * it holds it off (shown the first tick it does and every BLOCK_EVERY after). Caught, they are incapacitated and slow
     * until a little after it lets them go, and dragged in from here.
     */
    private static void suck(ServerPlayerEntity p,State s,long now){
        var world=p.getServerWorld();float yaw=MathHelper.wrapDegrees(p.getYaw());
        Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        LivingEntity nearest=null;double best=Double.MAX_VALUE;
        List<LivingEntity> shown=new ArrayList<>();Map<UUID,Long> guards=new HashMap<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(INHALE_REACH+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!e.hasVehicle()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            if(!inhaleLane(d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&(!p.shouldDamagePlayer(other)||other instanceof ServerPlayerEntity sp&&CombatManager.cinematic(sp)))continue;
            if(held(e)||StarPlatinumTimeStop.stopped(e))continue;
            Long last=s.guards.get(e.getUuid());
            // a guard holds it off: heard and seen the first tick it does, seen again every so often while it goes on doing so
            if(CombatManager.guardTakes(e,at,last==null)){
                if(last==null||now-last>=BLOCK_EVERY){shown.add(e);last=now;}
                guards.put(e.getUuid(),last);continue;
            }
            double ahead=d.dotProduct(f);
            if(ahead<best){best=ahead;nearest=e;}
        }
        s.guards.clear();s.guards.putAll(guards);
        if(!shown.isEmpty())send(p,s,BLOCKED,shown);
        if(nearest==null)return;
        // incapacitated and slow for as long as it might yet hold them and a little after (cut short if it lets go sooner)
        long until=s.start+INHALE_LENGTH+INHALE_AFTER;
        s.held=new Held(nearest,nearest.getPos(),now,until);HOLDERS.put(nearest.getUuid(),p.getUuid());
        HitStun.release(nearest.getUuid());
        CombatManager.incapacitate(nearest,(int)(until-now),(int)(until-now),INHALE_HELD_SLOW);
        HitStun.freeze(nearest);
        if(nearest instanceof MobEntity mob){mob.getNavigation().stop();mob.setTarget(null);}
        // a boss caught is a boss struck (2.69.0): it is angry with whoever caught it
        dev.sangwoo.celestialfall.boss.Bosses.provoke(nearest,p);
        send(p,s,CATCH,List.of(nearest));
    }
    /** The one it holds gone (dead, removed, away to another world, a spectator, riding) is let go: it sucks on. */
    private static void keep(ServerPlayerEntity p,State s,long now){
        var h=s.held;var e=h.entity;
        if(e.isAlive()&&!e.isRemoved()&&e.getWorld()==p.getWorld()&&!e.isSpectator()&&!e.hasVehicle())return;
        s.held=null;let(h,now);send(p,s,LET,List.of(e));
    }
    /**
     * The one it holds is dragged in to their place just in front of the stand, and kept there as its user turns and
     * walks: anything but a player here (a player's own client drags them the same way, client/stand/StandInhale).
     */
    private static void drag(ServerPlayerEntity p,State s,long now){
        var h=s.held;var e=h.entity;
        if(e instanceof ServerPlayerEntity)return;
        double[] a=inhaleAnchor(p.getX(),p.getZ(),p.getYaw());
        // the next tick's place along the drag (where it is due as this tick ends): it moves there over the ground as a
        // walker would (blocks in the way stop it, a step it climbs, a drop it falls), not through walls
        double u=inhalePull((now-h.caught+1)/(double)INHALE_PULL);
        double x=h.from.x+(a[0]-h.from.x)*u,z=h.from.z+(a[1]-h.from.z)*u;
        HitStun.freeze(e);
        e.move(MovementType.SELF,new Vec3d(x-e.getX(),0,z-e.getZ()));
        // it turns to the stand it is drawn into
        float face=(float)Math.toDegrees(Math.atan2(-(p.getX()-e.getX()),p.getZ()-e.getZ()));
        e.setYaw(face);e.setHeadYaw(face);e.setBodyYaw(face);
        if(e instanceof MobEntity mob){mob.getNavigation().stop();mob.setTarget(null);}
    }
    private static void end(ServerPlayerEntity p,State s,long now){
        s.on=false;s.queued=s.waiting=false;s.ended=now;
        // its cooldown from now (none for a player the cooldowns are off for: /botand nocooldown)
        s.ready=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?now:inhaleReady(now);
        slow(p,false);
        letGo(s,now);s.guards.clear();
        send(p,s,END,List.of());
    }
    /** Lets go of the one it holds: they stay incapacitated and slow INHALE_AFTER ticks more. */
    private static void letGo(State s,long now){if(s.held!=null){let(s.held,now);s.held=null;}}
    private static void let(Held h,long now){
        HOLDERS.remove(h.entity.getUuid());
        if(h.entity.isAlive())CombatManager.cut(h.entity,h.until,now+INHALE_AFTER);
    }
    /** Its user's slow walk while it lasts. */
    private static void slow(ServerPlayerEntity p,boolean on){
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
        if(!on){attr.removeModifier(SLOW);return;}
        if(attr.getModifier(SLOW)==null)attr.addTemporaryModifier(new EntityAttributeModifier(SLOW,"Star Platinum inhale slow walk",-INHALE_SLOW,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    /** Tells every player near the user (and the user, and those it names) of the event: whom it caught, whose guards hold it off, or whom it let go. */
    private static void send(ServerPlayerEntity p,State s,int type,List<LivingEntity> who){
        var world=p.getServerWorld();long now=world.getTime();float yaw=MathHelper.wrapDegrees(p.getYaw());
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128&&!who.contains(viewer))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(yaw);b.writeLong(s.ready);
            b.writeVarInt(who.size());
            for(var e:who){b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY());b.writeDouble(e.getZ());}
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
