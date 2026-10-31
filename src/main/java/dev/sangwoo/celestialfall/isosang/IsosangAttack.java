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
import net.minecraft.world.RaycastContext;
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
    /**
     * The attack key (no payload); E (2.80.0: one byte, the skill: {@link #E}); and every viewer's news (2.80.0: version 2,
     * with the empowered combo's news and the thrown swords).
     */
    public static final Identifier INPUT=CelestialFall.id("isosang_attack_v1"),SKILL=CelestialFall.id("isosang_skill_v1"),EVENT=CelestialFall.id("isosang_attack_event_v2");
    /**
     * A hit starts; one of its blows lands (whom it struck); the attack rests until it is ready again; the hit in flight
     * stops short; (2.80.0) a sword of the empowered first is thrown (from where, which way and how far); E is used (the
     * next combo is empowered, the attack's rest gone); the empowerment is over without its combo having run (E's
     * cooldown runs from then).
     */
    public static final int HIT=0,LANDED=1,RESTS=2,STOPPED=3,THROWN=4,EMPOWERED=5,SPENT=6;
    /** The skills E's packet asks for (2.80.0: E only). */
    public static final int E=1;
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
        /**
         * (2.80.0) E: the next combo is empowered ({@code charged}: from E until the empowered fourth begins or the empowered
         * combo is over), the next hit begins it again from the first ({@code restart}: E was used, none of its hits has
         * begun yet), the hit in flight is one of the empowered combo's ({@code empowered}), and the tick E is ready again.
         */
        boolean charged,restart,empowered;long eReady;
        /** Where the hit in flight began (the empowered fourth leaps back from it and throws from there). */
        Vec3d origin=Vec3d.ZERO;
        /** (2.80.1) How far back the empowered fourth in flight may leap (blocks: BACKSTEP, or less for a block behind them, {@link #room}). */
        double room=BACKSTEP;
        /** (2.80.1) The empowered fourth's giant sword in flight (null for none). */
        Giant giant;
        /** The empowered first's swords in flight. */
        final List<Sword> swords=new ArrayList<>();
    }
    /** A sword of the empowered first thrown flying: its hit's id and start, which of the three, when, from where, which way, how far it may fly, and whether it is spent. */
    private static final class Sword {
        final UUID id;final long start;final int index;final long thrown;final Vec3d from,dir;final double most;boolean spent;
        /** How far it flew (blocks): its range or the block in its way, else where it struck a body. */
        double end;
        Sword(UUID id,long start,int index,long thrown,Vec3d from,Vec3d dir,double most){this.id=id;this.start=start;this.index=index;this.thrown=thrown;this.from=from;this.dir=dir;this.most=end=most;}
    }
    /** The finisher's slash flying: its hit's id and start, the tick it was thrown, from where and which way, and whom it has struck. */
    private static final class Wave {
        final UUID id;final long start,thrown;final Vec3d from;final float yaw;final Set<UUID> struck=new HashSet<>();
        Wave(UUID id,long start,long thrown,Vec3d from,float yaw){this.id=id;this.start=start;this.thrown=thrown;this.from=from;this.yaw=yaw;}
    }
    /**
     * The empowered fourth's giant sword thrown flying (2.80.1): its hit's id and start, the tick it was thrown, from where
     * (its user's feet then), the way it flies, how far its point may go (its range or the block in its way) and whom it
     * has struck.
     */
    private static final class Giant {
        final UUID id;final long start,thrown;final Vec3d from;final float yaw;final double most;final Set<UUID> struck=new HashSet<>();
        Giant(UUID id,long start,long thrown,Vec3d from,float yaw,double most){this.id=id;this.start=start;this.thrown=thrown;this.from=from;this.yaw=yaw;this.most=most;}
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
        ServerPlayNetworking.registerGlobalReceiver(SKILL,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=1)return;int skill=buf.readUnsignedByte();
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                if(++s.presses>RATE)return;
                if(skill==E)empower(player);
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
    /** This player's giant sword in flight, as a boss reads it (2.80.1): thrown at {@code thrown} from {@code from} (their feet), the way {@code yaw}, its point stopping {@code most} blocks on. */
    public record GiantFlight(long thrown,Vec3d from,float yaw,double most){}
    /** This player's giant sword in flight now (null for none). */
    public static GiantFlight giant(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());var g=s==null?null:s.giant;return g==null?null:new GiantFlight(g.thrown,g.from,g.yaw,g.most);}
    /** How far back this player's empowered fourth in flight leaps (blocks, 2.80.1: its room). */
    public static double room(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?BACKSTEP:s.room;}
    /**
     * How far back the empowered fourth's leap may carry {@code body} from {@code origin}, the hit facing {@code yaw}
     * (2.80.1): BACKSTEP, or less to stay clear of the first block behind them (looked for at the knees and the chest,
     * at their middle and either side, a little in from their sides so a wall they stand against is not in the way). The
     * server and every client find the same room in the same blocks.
     */
    public static double room(net.minecraft.world.World world,net.minecraft.entity.Entity body,Vec3d origin,float yaw){
        if(world==null||body==null)return BACKSTEP;
        Vec3d back=Vec3d.fromPolar(0,yaw).multiply(-1),side=new Vec3d(-back.z,0,back.x);double half=body.getWidth()*.5,room=BACKSTEP;
        for(double up:new double[]{.65,1.4})for(double across:new double[]{.05-half,0,half-.05}){
            Vec3d a=origin.add(0,up,0).add(side.multiply(across)),b=a.add(back.multiply(BACKSTEP+half+.1));
            var hit=world.raycast(new RaycastContext(a,b,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,body));
            if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)room=Math.min(room,a.distanceTo(hit.getPos())-half-.1);
        }
        return Math.max(0,room);
    }
    /**
     * How far the giant sword thrown from {@code from} (feet) the way {@code yaw} may fly before a block stops it (2.80.1):
     * its range, or the first block along its way at its height, looked for from where they stand.
     */
    public static double giantMost(net.minecraft.world.World world,net.minecraft.entity.Entity body,Vec3d from,float yaw){
        if(world==null||body==null)return GIANT_RANGE;
        Vec3d f=Vec3d.fromPolar(0,yaw),a=from.add(0,GIANT_HEIGHT,0);
        var hit=world.raycast(new RaycastContext(a,a.add(f.multiply(GIANT_RANGE)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,body));
        return hit.getType()==net.minecraft.util.hit.HitResult.Type.MISS?GIANT_RANGE:Math.max(GIANT_START,a.distanceTo(hit.getPos())-.25);
    }
    /** Where the empowered fourth throws its giant sword from (2.80.1): where its leap has carried them at its blow, from where the hit began. */
    public static Vec3d throwFrom(Vec3d origin,float yaw,double room){return origin.subtract(Vec3d.fromPolar(0,yaw).multiply(backstep(blowAt(THRUST,0),room)));}
    /** The tick this player's attack is ready again after its rest (0 if it is not resting). */
    public static long ready(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player is Lee So-sang's no more: the attack's rest is gone, so it is ready if they are again; so are E's cooldown and its empowerment (2.80.0). */
    public static void forget(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null){s.ready=0;s.eReady=0;s.charged=s.restart=false;}}
    /**
     * This player's cooldowns are off (/botand nocooldown, every tick: NoCooldownState): the attack's rest and E's
     * cooldown are gone; an empowerment they hold stays theirs (2.80.0).
     */
    public static void cool(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null){s.ready=0;s.eReady=0;}}
    /** The tick this player's E is ready again (0 if it is ready, 2.80.0). */
    public static long eReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.eReady;}
    /** Whether this player's next combo is empowered (E used, its combo not begun or not over, 2.80.0). */
    public static boolean charged(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.charged;}
    /** This player's thrown swords in flight, as a boss reads them (2.80.0): from where each is now, which way, how much further it may fly. */
    public record Flying(Vec3d at,Vec3d dir,double left){}
    public static List<Flying> swords(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());if(s==null)return List.of();long now=p.getWorld().getTime();
        List<Flying> out=new ArrayList<>();
        for(var w:s.swords){if(w.spent)continue;double d=swordFlown(now-w.thrown,w.most);out.add(new Flying(w.from.add(w.dir.multiply(d)),w.dir,w.most-d));}
        return out;
    }
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
        long may=may(s);
        if(now<may){
            // (the fourth hit's motion runs on after its blow; the next combo waits for its rest, set as it began; after
            // E the empowered first may begin once it lets go of them, and a press a little early is kept for it)
            if(position(s.combo)<4||s.restart){if(may-now<=BUFFER)s.queued=true;return s.queued;}
            return false;
        }
        begin(p,s,now);
        return true;
    }
    /**
     * When the next hit may begin: at once with none in flight; the next of the combo its STEP after the hit before; the
     * next combo once the fourth's motion is over (and its rest, s.ready); after E (2.80.0) the empowered first as soon
     * as the hit in flight could be followed, the fourth's as soon as it lets go of them.
     */
    private static long may(State s){
        if(!valid(s.combo))return Long.MIN_VALUE;
        if(position(s.combo)<4)return nextAt(s.combo,s.start);
        return s.start+(s.restart?HOLD[s.combo]:LENGTH[s.combo]);
    }
    /**
     * E (2.80.0, user request): the attack's rest is gone at once and the next combo is empowered, begun again from its
     * first hit (the empowered one: three swords thrown) as soon as the hit in flight may be followed. Not while one is
     * held (by a blow, a guard, a dash, a skill, a cutscene; a hit of their own holding them aside), while they hold an
     * empowerment already, or before E is ready again ({@link IsosangRules#E_COOLDOWN} after the last empowered combo was
     * over; none for a player the cooldowns are off for, /botand nocooldown).
     */
    public static boolean empower(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(s.charged||s.empowered&&valid(s.combo)&&now<s.start+LENGTH[s.combo]||now<s.eReady&&!free)return false;
        s.charged=s.restart=true;s.queued=false;s.ready=0;
        send(p,new UUID(0,0),0,now,MathHelper.wrapDegrees(p.getYaw()),EMPOWERED,-1,null,null);
        return true;
    }
    /**
     * The empowerment is over (2.80.0): its combo broke, a hit of it stopped short, or they died or left the map before
     * it ran; E is ready again {@link IsosangRules#E_COOLDOWN} after {@code at}.
     */
    private static void spend(ServerPlayerEntity p,State s,long at){
        if(!s.charged)return;
        s.charged=s.restart=false;
        s.eReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?0:at+E_COOLDOWN;
        send(p,s.id,s.combo,s.start,s.yaw,SPENT,-1,null,null);
    }
    /** The user was struck (staggered or incapacitated, CombatManager): the hit in flight stops short. */
    public static void struck(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());}
    /**
     * The combo broke (the next hit is late): the attack rests and starts again from the first hit; a rest over ends the
     * combo. (2.80.0) After E the combo it cut off breaks with no rest, and the empowered combo breaking spends E's
     * empowerment: E's cooldown runs from the break.
     */
    private static void settle(ServerPlayerEntity p,State s,long now){
        long rest=broken(s.combo,s.start,now);
        // (none for a player the cooldowns are off for, /botand nocooldown: the list shows no rest for them)
        if(rest>=0){
            boolean spent=s.empowered;
            s.combo=0;s.queued=false;
            if(!s.restart&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.ready=Math.max(s.ready,rest);
            if(spent){s.empowered=false;spend(p,s,rest-REST);}
            send(p,s.id,s.combo,s.start,s.yaw,RESTS,-1,null,null);
        }
        if(position(s.combo)==4&&now>=s.ready&&now>=s.start+LENGTH[s.combo]){s.combo=0;s.empowered=false;}
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        // (2.80.0) the empowered combo after E: begun again from its first hit, its first and fourth the empowered kinds
        s.combo=s.restart?kind(1,s.charged):next(s.combo,s.charged&&s.empowered);s.empowered=s.charged;s.restart=false;
        s.id=UUID.randomUUID();s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=false;s.blows=0;s.origin=p.getPos();
        // (2.80.1) the empowered fourth leaps back as far as the blocks behind them let it
        s.room=s.combo==THRUST?room(p.getWorld(),p,s.origin,s.yaw):BACKSTEP;
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        // (the rest after the fourth hit: none for a player the cooldowns are off for, /botand nocooldown)
        if(position(s.combo)==4)s.ready=free?now:rested(s.combo,now);
        // the empowered fourth ends the empowered combo: E's cooldown runs once its motion is over (the user's choice)
        if(s.combo==THRUST){s.charged=false;s.eReady=free?0:now+LENGTH[THRUST]+E_COOLDOWN;}
        p.clearActiveItem();p.setSprinting(false);p.swingHand(Hand.MAIN_HAND,true);CombatManager.interrupt(p);
        send(p,s.id,s.combo,s.start,s.yaw,HIT,-1,null,null);
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
            // a slash thrown flies on (and strikes) whatever becomes of its user, so long as they are here and Lee So-sang's;
            // so do the empowered first's swords (2.80.0)
            if(s.wave!=null)fly(p,s,now);
            if(!s.swords.isEmpty()){for(var w:new ArrayList<>(s.swords))if(!w.spent)fly(p,s,w,now);s.swords.removeIf(w->w.spent);}
            // and the empowered fourth's giant sword (2.80.1)
            if(s.giant!=null)hurl(p,s,now);
            // (2.80.0) dead, or off the map: an empowerment held is spent (a character changed forgets it, StandManager)
            if(s.charged&&!attacks(p)&&"isosang".equals(dev.sangwoo.celestialfall.progression.ProgressManager.profile(p).stand())){s.empowered=false;spend(p,s,now);}
            if(held(p)){stop(p,s,now);continue;}
            settle(p,s,now);
            if(s.queued&&now>=may(s)){if(able(p))begin(p,s,now);else s.queued=false;}
            while(valid(s.combo)&&s.blows<blows(s.combo)&&now>=s.start+blowAt(s.combo,s.blows))blow(p,s,now);
            // the empowered fourth's leap back: a player's own client moves them (IsosangClient.drive); a boss, here
            if(s.combo==THRUST&&p instanceof dev.sangwoo.celestialfall.boss.BossPlayer&&now-s.start<=BACKSTEP_TO){
                Vec3d at=s.origin.subtract(Vec3d.fromPolar(0,s.yaw).multiply(backstep(now-s.start,s.room)));
                p.setPosition(at.x,p.getY(),at.z);
            }
        }
    }
    /**
     * The hit in flight stops short (or the combo is cut off between hits): the attack rests from now. (2.80.0) A hit of
     * the empowered combo stopped short spends the empowerment: E's cooldown runs from now (as every skill cut short).
     */
    private static void stop(ServerPlayerEntity p,State s,long now){
        boolean moving=valid(s.combo)&&now-s.start<LENGTH[s.combo],open=position(s.combo)>=1&&position(s.combo)<4&&broken(s.combo,s.start,now)<0;
        if(!moving&&!open&&!s.queued)return;
        boolean empowered=s.empowered,thrust=s.combo==THRUST&&moving;
        s.queued=false;s.combo=0;s.blows=0;s.empowered=false;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p)){
            s.ready=Math.max(s.ready,now+REST);
            if(thrust)s.eReady=Math.min(s.eReady,now+E_COOLDOWN);
        }
        send(p,s.id,s.combo,s.start,s.yaw,STOPPED,-1,null,null);
        if(empowered)spend(p,s,now);
    }
    /**
     * One of the hit's blows lands: everyone in its way in front of its user (all round for the ring), in sight, is struck
     * without knockback and staggered until the next blow could land. The fourth hit's blow throws its slash flying
     * instead ({@link #fly}: it strikes whoever is right in front now, and the rest as it reaches them); it takes no guard.
     */
    private static void blow(ServerPlayerEntity p,State s,long now){
        int combo=s.combo,i=s.blows++;
        if(combo==4){s.wave=new Wave(s.id,s.start,now,p.getPos(),s.yaw);fly(p,s,now);return;}
        // (2.80.0) the empowered first: a sword thrown flying the way they look now
        if(combo==THROWS){toss(p,s,i,now);return;}
        // (2.80.1) the empowered fourth: the giant sword thrown flying from where the leap carried them, the way they faced as it began
        if(combo==THRUST){
            Vec3d from=throwFrom(s.origin,s.yaw,s.room);
            s.giant=new Giant(s.id,s.start,now,from,s.yaw,giantMost(p.getWorld(),p,from,s.yaw));
            var told=new Sword(s.id,s.start,0,now,from,Vec3d.fromPolar(0,s.yaw),s.giant.most);
            send(p,s.id,THRUST,s.start,s.yaw,THROWN,0,null,told);
            hurl(p,s,now);
            return;
        }
        Vec3d f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        Vec3d at=p.getPos();
        var struck=strike(p,combo,i,s.start,s.yaw,now,at,e->{Vec3d d=e.getPos().subtract(at);return lane(combo,i,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5);},null,s.empowered);
        send(p,s.id,combo,s.start,s.yaw,LANDED,i,struck,null);
    }
    /**
     * A sword of the empowered first thrown (2.80.0): out of its place beside them (IsosangRules.SWORD_FROM, the way they
     * face now), at the point their sight meets first (a block or a body, SWORD_AIM blocks on at most), on out to its
     * range or the first block in its way. It strikes as it flies ({@link #fly(ServerPlayerEntity,State,Sword,long)});
     * one right on top of them at once.
     */
    private static void toss(ServerPlayerEntity p,State s,int i,long now){
        var world=p.getServerWorld();float yaw=p.getYaw();
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);double[] o=SWORD_FROM[Math.min(i,SWORD_FROM.length-1)];
        Vec3d from=p.getPos().add(f.multiply(o[0])).add(0,o[1],0).add(right.multiply(o[2]));
        Vec3d eye=p.getEyePos(),look=p.getRotationVec(1f);double far=SWORD_AIM;
        var block=world.raycast(new RaycastContext(eye,eye.add(look.multiply(far)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(block.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)far=eye.distanceTo(block.getPos());
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(eye,eye.add(look.multiply(far))).expand(1),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit())){
            var hit=e.getBoundingBox().expand(.3).raycast(eye,eye.add(look.multiply(far)));
            if(hit.isPresent())far=Math.min(far,eye.distanceTo(hit.get()));
        }
        Vec3d aim=eye.add(look.multiply(Math.max(SWORD_AIM_MIN,far))),dir=aim.subtract(from);
        dir=dir.lengthSquared()<1e-6?look:dir.normalize();
        double most=SWORD_RANGE;
        var wall=world.raycast(new RaycastContext(from,from.add(dir.multiply(SWORD_RANGE)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(wall.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)most=from.distanceTo(wall.getPos());
        var sword=new Sword(s.id,s.start,i,now,from,dir,most);s.swords.add(sword);
        send(p,s.id,THROWS,s.start,s.yaw,THROWN,i,null,sword);
        fly(p,s,sword,now);
    }
    /**
     * A thrown sword flies on a tick (2.80.0): the first body along its way since the last tick (its box widened by
     * SWORD_RADIUS), not its user, is struck and the sword is spent; so is one that has flown its way.
     */
    private static void fly(ServerPlayerEntity p,State s,Sword w,long now){
        double t=now-w.thrown,d0=swordFlown(t-1,w.most),d1=swordFlown(t,w.most);
        Vec3d a=w.from.add(w.dir.multiply(d0)),b=w.from.add(w.dir.multiply(d1));
        var world=p.getServerWorld();LivingEntity first=null;double nearest=Double.MAX_VALUE;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(a,b).expand(SWORD_RADIUS+1.5),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            Box box=e.getBoundingBox().expand(SWORD_RADIUS);double at;
            if(box.contains(a))at=0;
            else{var hit=d1>d0?box.raycast(a,b):java.util.Optional.<Vec3d>empty();if(hit.isEmpty())continue;at=a.distanceTo(hit.get());}
            if(at<nearest){nearest=at;first=e;}
        }
        if(first!=null){
            // (it stops where it reached them, struck or guarded against: every viewer is told how far it flew)
            w.spent=true;w.end=d0+nearest;var target=first;
            var struck=strike(p,THROWS,w.index,w.start,MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-w.dir.x,w.dir.z))),now,a,e->e==target,null,true);
            send(p,w.id,THROWS,w.start,s.yaw,LANDED,w.index,struck,w);
            return;
        }
        if(d1>=w.most)w.spent=true;
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
        },w.struck,false);
        if(!struck.isEmpty()||to==0)send(p,w.id,4,w.start,w.yaw,LANDED,0,struck,null);
        if(to>=WAVE_TICKS)s.wave=null;
    }
    /**
     * The empowered fourth's giant sword flies on a tick (2.80.1): whoever its way has reached by now (IsosangRules.giantOver,
     * not behind a block from its way), not struck by it yet, is struck and thrown on; it is over once it has stopped.
     */
    private static void hurl(ServerPlayerEntity p,State s,long now){
        var g=s.giant;double t=now-g.thrown;
        if(!giantFlying(t,g.most)){s.giant=null;return;}
        Vec3d f=Vec3d.fromPolar(0,g.yaw),right=new Vec3d(-f.z,0,f.x);var world=p.getWorld();
        var struck=strike(p,THRUST,0,g.start,g.yaw,now,g.from,e->{
            Vec3d d=e.getPos().subtract(g.from);double fw=d.dotProduct(f);
            if(!giantOver(t,g.most,fw,d.dotProduct(right),d.y,e.getWidth()*.5))return false;
            // (not through a wall beside its way)
            Vec3d on=g.from.add(f.multiply(Math.max(0,Math.min(g.most,fw)))).add(0,GIANT_HEIGHT,0),chest=e.getPos().add(0,e.getHeight()*.5,0);
            return world.raycast(new RaycastContext(on,chest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()==net.minecraft.util.hit.HitResult.Type.MISS;
        },g.struck,true);
        if(!struck.isEmpty())send(p,g.id,THRUST,g.start,g.yaw,LANDED,0,struck,null);
        if(t>=giantArrives(g.most))s.giant=null;
    }
    /**
     * Strikes everyone near {@code at} the test takes (and the slash has not struck yet, {@code done}), in the user's
     * sight: without knockback, staggered until the next blow could land; the finisher takes no guard. Returns whom.
     */
    private static List<LivingEntity> strike(ServerPlayerEntity p,int combo,int i,long start,float yaw,long now,Vec3d at,java.util.function.Predicate<LivingEntity> test,Set<UUID> done,boolean empowered){
        var world=p.getServerWorld();var source=world.getDamageSources().playerAttack(p);float amount=damage(combo,i,empowered);
        List<LivingEntity> struck=new ArrayList<>();
        // (2.80.0: a thrown sword strikes the body it reached, wherever that is: the sword flew a way clear of blocks to it;
        // 2.80.1: so does the giant sword, all along its way)
        boolean thrown=combo==THROWS||combo==THRUST;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand((combo==THROWS?2:MAX_REACH)+3),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            if(done!=null&&done.contains(e.getUuid()))continue;
            if(!test.test(e)||!thrown&&!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            if(done!=null)done.add(e.getUuid());
            if(unblockable(combo))CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,amount))continue;
            struck.add(e);
            // every blow staggers whom it strikes a moment (2.68.0's rule for basic attacks): until the next blow could
            // land at full speed, a little past; one stopped in Star Platinum's stopped time is staggered as time moves again
            if(!KagenoManager.frozen(e)||StarPlatinumTimeStop.stopped(e))HitStun.stagger(p,e,combo,Stagger.isosang(combo,i,start,now,empowered));
            // (2.80.0, user request: "4타에 맞으면 넉백이 있어") the empowered fourth's giant sword throws whom it strikes the way it flies,
            // as Star Platinum's heavy punch: told to a struck player's client once, through the tracker; one stopped in a
            // time stop is thrown as time moves again
            if(combo==THRUST){
                Vec3d fly=Vec3d.fromPolar(0,yaw).multiply(THRUST_FLY).add(0,THRUST_FLY_UP,0);
                if(!StarPlatinumTimeStop.deferLaunch(e,fly)){e.setVelocity(fly);e.velocityModified=true;}
            }
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
     * start and way), the blow that landed (-1 for none) and whom it struck, and when the attack is ready again; since
     * 2.80.0 whether the hit is of the empowered combo, whether the user holds an empowerment, when E is ready again, and
     * for a sword thrown, from where, which way and how far it may fly.
     */
    private static void send(ServerPlayerEntity p,UUID id,int combo,long start,float yaw,int type,int blow,List<LivingEntity> struck,Sword sword){
        var s=state(p);var world=p.getServerWorld();long now=world.getTime();
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128&&(struck==null||!struck.contains(viewer)))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);b.writeUuid(id);b.writeByte(combo);b.writeByte(blow);
            b.writeLong(start);b.writeLong(now);b.writeDouble(p.getX());b.writeDouble(p.getY());b.writeDouble(p.getZ());b.writeFloat(yaw);b.writeLong(s.ready);
            int n=struck==null?0:struck.size();b.writeVarInt(n);
            for(int i=0;i<n;i++){var e=struck.get(i);b.writeUuid(e.getUuid());b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY());b.writeDouble(e.getZ());}
            b.writeBoolean(s.empowered&&id.equals(s.id)||combo==THROWS||combo==THRUST);b.writeBoolean(s.charged);b.writeLong(s.eReady);
            b.writeBoolean(sword!=null);
            if(sword!=null){b.writeDouble(sword.from.x);b.writeDouble(sword.from.y);b.writeDouble(sword.from.z);b.writeDouble(sword.dir.x);b.writeDouble(sword.dir.y);b.writeDouble(sword.dir.z);b.writeDouble(sword.end);}
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
