package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import java.util.ArrayList;
import java.util.Comparator;
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
 * 무력화"; StarPlatinumRules for the timing and the reach). The user's client asks for it (stand_inhale_v1, nothing in it)
 * and the server decides: it starts if it may (the stand out, its user free, it ready again, no heavy punch or Star
 * Finger in flight; a punch in flight stops short and it starts once the stand is back in its guard, and just after
 * the rush once the rush's fists are). INHALE_CATCH ticks in it sucks: everyone in its way in front of its user, in
 * sight, the PvP rule, held by no other inhale, is caught unless they hold a guard up against it (CombatManager
 * .guardTakes: the guard takes it whole). Whom it caught is incapacitated (CombatManager.incapacitate: no skill, attack,
 * dash or guard) until the hold ends, takes no damage and is dragged in to just in front of the stand and held there
 * (StarPlatinumRules.inhaleAnchor) as its user turns and walks (at half their speed). A player is moved by their own
 * client (a player's movement is theirs: client/stand/StandInhale drives it as the server plans it, as T held's dash
 * is driven); anything else the server moves. It ends INHALE_HOLD ticks after the catch, INHALE_WHIFF after it if it
 * caught no one, at once if whom it held are all gone, and when its user is struck, incapacitated, stunned or sent the
 * stand back: whom it held go at once, their incapacitation with them. Its cooldown runs from its end. Every viewer
 * within 128 blocks (and whom it caught) is told when it starts, whom it caught and whose guard took it, and when it
 * ends (stand_inhale_event_v1); the clients draw it and play its sound.
 */
public final class StarPlatinumInhale {
    public static final Identifier INPUT=CelestialFall.id("stand_inhale_v1"),EVENT=CelestialFall.id("stand_inhale_event_v1");
    /** It starts; it sucks (whom it caught, whose guard took it); it ends. */
    public static final int START=0,CATCH=1,END=2;
    /** Its user's slow walk while it lasts (StarPlatinumRules.INHALE_SLOW; the client keeps its lens still). */
    public static final UUID SLOW=UUID.fromString("4f2a8c1e-93b7-4d55-a0e6-6c1d2b9f7e31");
    /** At most this many asks a second are heard. */
    static final int RATE=20;
    /** One it holds: what, in which slot, where it was caught, and until when its incapacitation was to last. */
    private record Held(LivingEntity entity,int slot,Vec3d from,long until){}
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** Whether it is on, when it starts (a little on, if a punch it stopped turns back to the guard first), when the last ended and when it is ready again. */
        boolean on;long start=-1000,ended=-1000,ready;
        /** Asked for just after the rush: it starts once the rush's fists are back in the guard. */
        boolean queued;
        /** Whether it has sucked yet, whether it caught anyone, and whom it holds still. */
        boolean sucked,caught;final List<Held> held=new ArrayList<>();
        int window,presses;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    /** Whom an inhale holds now, and whose. */
    private static final Map<UUID,UUID> HOLDERS=new HashMap<>();
    private StarPlatinumInhale(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                if(++s.presses>RATE)return;
                start(player);
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
    /** The tick this player's inhale is ready again (0 if they have not inhaled). */
    public static long ready(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.ready;}

    /** Y: the inhale starts if it may. Returns whether it starts (now or once the rush's fists are back). */
    public static boolean start(ServerPlayerEntity p){
        if(!StarPlatinumAttack.able(p)||held(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(s.on||now<s.ready&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        // the heavy punch and the Star Finger are not cut off, nor do they wait for it
        if(now<StarPlatinumAttack.skillOverAt(p))return false;
        if(now<StarPlatinumRush.freeAt(p)){s.queued=true;return true;}
        begin(p,s,now);
        return true;
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        // a punch in flight gives way to it: it starts once the stand has turned back to its guard (StarPlatinumRules.STOP_RETURN)
        StarPlatinumAttack.yieldTo(p);
        s.on=true;s.queued=false;s.sucked=s.caught=false;s.held.clear();
        s.start=Math.max(now,StarPlatinumAttack.guardAt(p));
        p.setSprinting(false);p.clearActiveItem();slow(p,true);
        send(p,s,START,null,null);
    }
    /** Its user was struck (or incapacitated): the inhale stops, and lets go of whom it holds. */
    public static void struck(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s==null)return;s.queued=false;if(s.on)end(p,s,p.getWorld().getTime());}
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){if(s.on)release(s,s.world.getTime());STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            if(s.queued&&!s.on&&now>=StarPlatinumRush.freeAt(p)){
                s.queued=false;
                if(StarPlatinumAttack.able(p)&&(now>=s.ready||dev.sangwoo.celestialfall.NoCooldownState.enabled(p))&&now>=StarPlatinumAttack.skillOverAt(p))begin(p,s,now);
            }
            if(!s.on)continue;
            if(!StarPlatinumAttack.punches(p)||HitStun.stunned(p)||p.isSleeping()||p.hasVehicle()||CombatManager.cinematic(p)||CombatManager.incapacitated(p)){end(p,s,now);continue;}
            long age=now-s.start;
            p.setSprinting(false);
            if(!s.sucked&&age>=INHALE_CATCH)suck(p,s,now);
            if(s.sucked)drag(p,s,now);
            // over: the hold run out, or all it held gone; caught no one, a little after the catch (the stand closes in vain)
            if(s.sucked&&(s.caught?s.held.isEmpty()||age>=INHALE_LENGTH:age>=INHALE_CATCH+INHALE_WHIFF))end(p,s,now);
        }
    }
    /**
     * It sucks: everyone in its way in front of its user now, in sight, nearest first (INHALE_MAX), is caught, unless a
     * guard held up against it takes it. Caught, they are incapacitated until the hold ends and dragged in from here.
     */
    private static void suck(ServerPlayerEntity p,State s,long now){
        var world=p.getServerWorld();float yaw=MathHelper.wrapDegrees(p.getYaw());
        Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        List<LivingEntity> way=new ArrayList<>(),blocked=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(INHALE_REACH+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!e.hasVehicle()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            if(!inhaleLane(d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&(!world.getServer().isPvpEnabled()||!p.shouldDamagePlayer(other)||other instanceof ServerPlayerEntity sp&&CombatManager.cinematic(sp)))continue;
            if(held(e))continue;
            if(CombatManager.guardTakes(e,at,true)){blocked.add(e);continue;}
            way.add(e);
        }
        way.sort(Comparator.comparingDouble(e->e.getPos().subtract(at).dotProduct(f)));
        if(way.size()>INHALE_MAX)way=new ArrayList<>(way.subList(0,INHALE_MAX));
        s.sucked=true;s.caught=!way.isEmpty();
        // (from the start's INHALE_CATCH on, so the hold ends with the inhale: INHALE_LENGTH)
        long until=s.start+INHALE_LENGTH;
        for(int k=0;k<way.size();k++){
            var e=way.get(k);
            s.held.add(new Held(e,k,e.getPos(),until));HOLDERS.put(e.getUuid(),p.getUuid());
            // incapacitated while it holds them, no slower (it moves them), no damage
            HitStun.release(e.getUuid());
            CombatManager.incapacitate(e,(int)(until-now),0,0);
            HitStun.freeze(e);
            if(e instanceof MobEntity mob){mob.getNavigation().stop();mob.setTarget(null);}
        }
        send(p,s,CATCH,way,blocked);
    }
    /**
     * Whom it holds are dragged in to their place just in front of the stand, and kept there as its user turns and
     * walks: anything but a player here (a player's own client drags them the same way, client/stand/StandInhale). One
     * gone (dead, removed, away to another world, riding) is let go.
     */
    private static void drag(ServerPlayerEntity p,State s,long now){
        long since=now-(s.start+INHALE_CATCH);float yaw=p.getYaw();
        for(var it=s.held.iterator();it.hasNext();){
            var h=it.next();var e=h.entity;
            if(!e.isAlive()||e.isRemoved()||e.getWorld()!=p.getWorld()||e.isSpectator()||e.hasVehicle()){let(h,now);it.remove();continue;}
            if(e instanceof ServerPlayerEntity)continue;
            double[] a=inhaleAnchor(p.getX(),p.getZ(),yaw,h.slot);
            // the next tick's place along the drag (where it is due as this tick ends): it moves there over the ground
            // as a walker would (blocks in the way stop it, a step it climbs, a drop it falls), not through walls
            double u=inhalePull((since+1)/(double)INHALE_PULL);
            double x=h.from.x+(a[0]-h.from.x)*u,z=h.from.z+(a[1]-h.from.z)*u;
            HitStun.freeze(e);
            e.move(MovementType.SELF,new Vec3d(x-e.getX(),0,z-e.getZ()));
            // it turns to the stand it is drawn into
            float face=(float)Math.toDegrees(Math.atan2(-(p.getX()-e.getX()),p.getZ()-e.getZ()));
            e.setYaw(face);e.setHeadYaw(face);e.setBodyYaw(face);
            if(e instanceof MobEntity mob){mob.getNavigation().stop();mob.setTarget(null);}
        }
    }
    private static void end(ServerPlayerEntity p,State s,long now){
        s.on=false;s.queued=false;s.ended=now;
        // its cooldown from now (none for a player the cooldowns are off for: /botand nocooldown)
        s.ready=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?now:inhaleReady(now);
        slow(p,false);
        release(s,now);
        send(p,s,END,null,null);
    }
    /** Lets go of everyone it holds: their incapacitation ends with it. */
    private static void release(State s,long now){for(var h:s.held)let(h,now);s.held.clear();}
    private static void let(Held h,long now){
        HOLDERS.remove(h.entity.getUuid());
        if(now<h.until&&h.entity.isAlive())CombatManager.free(h.entity,h.until);
    }
    /** Its user's slow walk while it lasts. */
    private static void slow(ServerPlayerEntity p,boolean on){
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
        if(!on){attr.removeModifier(SLOW);return;}
        if(attr.getModifier(SLOW)==null)attr.addTemporaryModifier(new EntityAttributeModifier(SLOW,"Star Platinum inhale slow walk",-INHALE_SLOW,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    /** Tells every player near the user (and the user, and whom it caught) of the event. */
    private static void send(ServerPlayerEntity p,State s,int type,List<LivingEntity> caught,List<LivingEntity> blocked){
        var world=p.getServerWorld();long now=world.getTime();float yaw=MathHelper.wrapDegrees(p.getYaw());
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128&&(caught==null||!caught.contains(viewer)))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(yaw);b.writeLong(s.ready);
            int n=caught==null?0:caught.size();b.writeVarInt(n);
            for(int i=0;i<n;i++){var e=caught.get(i);b.writeInt(e.getId());b.writeByte(i);b.writeDouble(e.getX());b.writeDouble(e.getY());b.writeDouble(e.getZ());}
            int m=blocked==null?0:blocked.size();b.writeVarInt(m);
            for(int i=0;i<m;i++)b.writeInt(blocked.get(i).getId());
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
