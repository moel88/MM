package dev.sangwoo.celestialfall.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.combat.Stagger;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * Lee So-sang's basic attack, the sword's four hits (2.77.0; IsosangRules for the timing and the reach). The attack key on
 * their client asks for a hit (isosang_attack_v1, no payload; a vanilla attack is taken the same way) and the server
 * decides: the next hit of the combo starts now, or when it may if that is near (a press made a little early is kept),
 * or not at all while the attack rests. Each hit goes the way its user faces as it starts and holds them where they stand
 * a moment (IsosangRules.HOLD: no walking, guard, dash or other move, CombatManager.busy); at each of its blows it strikes
 * everyone in its way (IsosangRules.lane, in sight) without knockback (user request: "평타는 넉백이 없게") and staggers
 * them until the next blow could land (Stagger.isosang); the fourth cannot be guarded. The fourth's blow throws its
 * slash flying (2.77.2, user request: "4타는 참격을 날리는건데"): from then on, tick by tick, it strikes whoever its band
 * passes over (IsosangRules.waveSweeps), once each, even if its user is struck before it lands. Every viewer is told when
 * a hit starts, whom each blow struck (the flying slash: whom it took that tick), and when the attack rests or the hit
 * stops short (isosang_attack_event_v1); the clients draw the body's motion, the slashes and play the sounds. A hit stops
 * short when its user is struck, staggered, incapacitated, held in a cutscene, or is Lee So-sang's no more; the attack
 * then rests as after a broken combo.
 */
public final class IsosangAttack {
    public static final Identifier INPUT=CelestialFall.id("isosang_attack_v1"),EVENT=CelestialFall.id("isosang_attack_event_v1");
    /** A hit starts; one of its blows lands (whom it struck); the attack rests until it is ready again; the hit in flight stops short. */
    public static final int HIT=0,LANDED=1,RESTS=2,STOPPED=3;
    /** At most this many presses a second are heard. */
    static final int RATE=40;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** The hit in flight or the last one (1 to 4, 0 for none), its id, when it began, the way it goes (degrees) and how many of its blows have landed. */
        int combo;UUID id=new UUID(0,0);long start=-1000;float yaw;int blows;
        /** A press kept for the next hit, and the tick the attack is ready again after a rest (0 for none). */
        boolean queued;long ready;
        int window,presses;
        /** The finisher's slash in flight (null for none): thrown by that hit (its id and start) from where they stood, the way it went. */
        Wave wave;
    }
    /** The finisher's slash flying: its hit's id and start, the tick it was thrown, from where and which way, and whom it has struck. */
    private static final class Wave {
        final UUID id;final long start,thrown;final Vec3d from;final float yaw;final Set<UUID> struck=new HashSet<>();
        Wave(UUID id,long start,long thrown,Vec3d from,float yaw){this.id=id;this.start=start;this.thrown=thrown;this.from=from;this.yaw=yaw;}
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private IsosangAttack(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                if(++s.presses>RATE)return;
                request(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(IsosangAttack::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->STATES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    /** Whether this player's attack is Lee So-sang's sword: theirs, alive, on the map. */
    public static boolean attacks(PlayerEntity p){return p!=null&&StandManager.isosang(p);}
    /** Free to start a hit: nothing else holds them (a blow, a guard, a dash, a skill, a cutscene); a hit's own hold aside. */
    public static boolean able(ServerPlayerEntity p){return attacks(p)&&!p.isSleeping()&&!p.hasVehicle()&&!CombatManager.busyOtherwise(p)&&!CombatManager.cinematic(p);}
    /** Whether one of this player's hits holds them now (IsosangRules.HOLD): CombatManager.busy and actionLocked count it. */
    public static boolean busy(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s!=null&&s.world==p.getWorld()&&holding(s.combo,s.start,p.getWorld().getTime());
    }
    /**
     * What this player's sword is doing now, as a boss reads it (BossThreats): the hit (1 to 4, 0 for none), when it
     * began, the way it goes and how many of its blows have landed.
     */
    public record Hit(int combo,long start,float yaw,int blows){}
    /** This player's hit now (null if they have swung none). */
    public static Hit hit(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?null:new Hit(s.combo,s.start,s.yaw,s.blows);}
    /** This player's finisher's slash in flight, as a boss reads it: thrown at {@code thrown} from {@code from}, the way {@code yaw}. */
    public record Flight(long thrown,Vec3d from,float yaw){}
    /** This player's slash in flight now (null for none). */
    public static Flight flight(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());var w=s==null?null:s.wave;return w==null?null:new Flight(w.thrown,w.from,w.yaw);}
    /** The tick this player's attack is ready again after its rest (0 if it is not resting). */
    public static long ready(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player is Lee So-sang's no more: the attack's rest is gone, so it is ready if they are again. */
    public static void forget(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)s.ready=0;}
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){s=new State(p);STATES.put(p.getUuid(),s);}
        return s;
    }

    /** The player asks for a hit. Returns whether one starts now or is kept for when it may. */
    public static boolean request(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(now<s.ready&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(s.combo>=1&&s.combo<4&&now<nextAt(s.combo,s.start)){
            if(nextAt(s.combo,s.start)-now<=BUFFER)s.queued=true;
            return s.queued;
        }
        // (the fourth hit's motion runs on after its blow; the next combo waits for its rest, set as it began)
        if(s.combo==4&&now<s.start+LENGTH[4])return false;
        begin(p,s,now);
        return true;
    }
    /** The user was struck (staggered or incapacitated, CombatManager): the hit in flight stops short. */
    public static void struck(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());}
    /** The combo broke (the next hit is late): the attack rests and starts again from the first hit; a rest over ends the combo. */
    private static void settle(ServerPlayerEntity p,State s,long now){
        long rest=broken(s.combo,s.start,now);
        // (none for a player the cooldowns are off for, /botand nocooldown: the list shows no rest for them)
        if(rest>=0){s.combo=0;s.queued=false;if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.ready=Math.max(s.ready,rest);send(p,s.id,s.combo,s.start,s.yaw,RESTS,-1,null);}
        if(s.combo==4&&now>=s.ready&&now>=s.start+LENGTH[4])s.combo=0;
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        s.combo=next(s.combo);s.id=UUID.randomUUID();s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=false;s.blows=0;
        // (the rest after the fourth hit: none for a player the cooldowns are off for, /botand nocooldown)
        if(s.combo==4)s.ready=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?now:rested(now);
        p.clearActiveItem();p.setSprinting(false);p.swingHand(Hand.MAIN_HAND,true);CombatManager.interrupt(p);
        send(p,s.id,s.combo,s.start,s.yaw,HIT,-1,null);
    }
    /** Whatever ends the hit in flight: its user struck, held, or no longer able to fight with the sword. */
    private static boolean held(ServerPlayerEntity p){
        return !attacks(p)||p.isSleeping()||p.hasVehicle()||HitStun.stunned(p)||CombatManager.incapacitated(p)||CombatManager.cinematic(p)||KagenoManager.frozen(p);
    }
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            // a slash thrown flies on (and strikes) whatever becomes of its user, so long as they are here and Lee So-sang's
            if(s.wave!=null)fly(p,s,now);
            if(held(p)){stop(p,s,now);continue;}
            settle(p,s,now);
            if(s.queued&&now>=nextAt(s.combo,s.start)){if(able(p))begin(p,s,now);else s.queued=false;}
            while(s.combo>=1&&s.blows<blows(s.combo)&&now>=s.start+blowAt(s.combo,s.blows))blow(p,s,now);
        }
    }
    /** The hit in flight stops short (or the combo is cut off between hits): the attack rests from now. */
    private static void stop(ServerPlayerEntity p,State s,long now){
        boolean moving=s.combo>=1&&now-s.start<LENGTH[s.combo],open=s.combo>=1&&s.combo<4&&broken(s.combo,s.start,now)<0;
        if(!moving&&!open&&!s.queued)return;
        s.queued=false;s.combo=0;s.blows=0;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.ready=Math.max(s.ready,now+REST);
        send(p,s.id,s.combo,s.start,s.yaw,STOPPED,-1,null);
    }
    /**
     * One of the hit's blows lands: everyone in its way in front of its user (all round for the ring), in sight, is struck
     * without knockback and staggered until the next blow could land. The fourth hit's blow throws its slash flying
     * instead ({@link #fly}: it strikes whoever is right in front now, and the rest as it reaches them); it takes no guard.
     */
    private static void blow(ServerPlayerEntity p,State s,long now){
        int combo=s.combo,i=s.blows++;
        if(combo==4){s.wave=new Wave(s.id,s.start,now,p.getPos(),s.yaw);fly(p,s,now);return;}
        Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        var struck=strike(p,combo,i,s.start,s.yaw,now,at,e->{Vec3d d=e.getPos().subtract(at);return lane(combo,i,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5);},null);
        send(p,s.id,combo,s.start,s.yaw,LANDED,i,struck);
    }
    /**
     * The finisher's slash flies on a tick: whoever its band passed over since the last tick (from the throw: whoever was
     * right in front too), in its user's sight and not struck by it yet, is struck; it is over once it has flown its way.
     */
    private static void fly(ServerPlayerEntity p,State s,long now){
        var w=s.wave;double to=now-w.thrown,from=Math.max(0,to-1);
        if(!waveFlying(to)){s.wave=null;return;}
        Vec3d f=Vec3d.fromPolar(0,w.yaw),right=new Vec3d(-f.z,0,f.x);
        var struck=strike(p,4,0,w.start,w.yaw,now,w.from,e->{
            Vec3d d=e.getPos().subtract(w.from);
            return waveSweeps(from,to,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5);
        },w.struck);
        if(!struck.isEmpty()||to==0)send(p,w.id,4,w.start,w.yaw,LANDED,0,struck);
        if(to>=WAVE_TICKS)s.wave=null;
    }
    /**
     * Strikes everyone near {@code at} the test takes (and the slash has not struck yet, {@code done}), in the user's
     * sight: without knockback, staggered until the next blow could land; the finisher takes no guard. Returns whom.
     */
    private static List<LivingEntity> strike(ServerPlayerEntity p,int combo,int i,long start,float yaw,long now,Vec3d at,java.util.function.Predicate<LivingEntity> test,Set<UUID> done){
        var world=p.getServerWorld();var source=world.getDamageSources().playerAttack(p);float amount=damage(combo,i);
        List<LivingEntity> struck=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(MAX_REACH+3),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            if(done!=null&&done.contains(e.getUuid()))continue;
            if(!test.test(e)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            if(done!=null)done.add(e.getUuid());
            if(unblockable(combo))CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,amount))continue;
            struck.add(e);
            // every blow staggers whom it strikes a moment (2.68.0's rule for basic attacks): until the next blow could
            // land at full speed, a little past; one stopped in Star Platinum's stopped time is staggered as time moves again
            if(!KagenoManager.frozen(e)||StarPlatinumTimeStop.stopped(e))HitStun.stagger(p,e,combo,Stagger.isosang(combo,i,start,now));
            // the user's own hit feedback (the damage number and the blood), as the other characters' (as time moves again
            // for one stopped in a time stop)
            if(!StarPlatinumTimeStop.deferFeedback(p,e,amount,yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(amount);b.writeFloat(yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
        }
        if(!struck.isEmpty()){p.onAttacking(struck.get(0));p.addExhaustion(.1f);}
        return struck;
    }
    /**
     * Tells every player near the user (and the user, and whoever was struck) of the event: the hit (its id, number,
     * start and way), the blow that landed (-1 for none) and whom it struck, and when the attack is ready again.
     */
    private static void send(ServerPlayerEntity p,UUID id,int combo,long start,float yaw,int type,int blow,List<LivingEntity> struck){
        var s=state(p);var world=p.getServerWorld();long now=world.getTime();
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128&&(struck==null||!struck.contains(viewer)))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);b.writeUuid(id);b.writeByte(combo);b.writeByte(blow);
            b.writeLong(start);b.writeLong(now);b.writeDouble(p.getX());b.writeDouble(p.getY());b.writeDouble(p.getZ());b.writeFloat(yaw);b.writeLong(s.ready);
            int n=struck==null?0:struck.size();b.writeVarInt(n);
            for(int i=0;i<n;i++){var e=struck.get(i);b.writeUuid(e.getUuid());b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY());b.writeDouble(e.getZ());}
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
