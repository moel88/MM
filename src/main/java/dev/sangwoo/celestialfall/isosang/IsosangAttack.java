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
 * <p>R (2.81.0): the giant sword's sweep ({@link #sweep(ServerPlayerEntity)}, IsosangRules' R section), a swing of its own
 * outside the combo: it ends a combo in flight (with no rest), holds its user while it runs, and from its first turn on
 * strikes tick by tick whoever its blade passes over, once each, even if its user is struck after it began to turn.
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
    /**
     * The skills the skill packet asks for: E (2.80.0), R (2.81.0) and R held (2.81.1: the key held down
     * {@link IsosangRules#R_HOLD_TICKS} ticks, the client's call, as Kageno's T held is), H (2.82.0) and G (2.83.0).
     */
    public static final int E=1,R=2,R_HELD=3,H=4,G=5;
    /** The formation's damage (2.82.0): no guard takes it (minecraft:bypasses_shield), its user is the attacker. */
    public static final net.minecraft.registry.RegistryKey<net.minecraft.entity.damage.DamageType> FORMATION_DAMAGE=
        net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,dev.sangwoo.celestialfall.CelestialFall.id("sword_formation"));
    /** G's damage (2.83.0): no guard takes it either (minecraft:bypasses_shield), its user is the attacker. */
    public static final net.minecraft.registry.RegistryKey<net.minecraft.entity.damage.DamageType> ULTIMATE_DAMAGE=
        net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,dev.sangwoo.celestialfall.CelestialFall.id("jade_tablet"));
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
        /** (2.81.0) R's sweep in flight or the last one (null for none), the tick R is ready again, and a press of R kept for when a hit lets go of them. */
        Sweep sweep;long rReady;boolean rQueued,rHeld;
        /** (2.82.0) H's sword formation under way (null for none) and the tick H is ready again. */
        Formation form;long hReady;
        /** (2.83.0) G under way or the last one (null for none), and the tick G is ready again. */
        Ultimate ult;long gReady;
    }
    /**
     * G (2.83.0): its id and start, where its user stands (their feet) and the way they faced, which of the recordings
     * (and faces) it plays, where its tablet strikes the ground ({@link IsosangRules#G_AHEAD} blocks ahead, on whatever is
     * there), and whether it has struck (or will not: its user gone before it did).
     */
    private static final class Ultimate {
        final UUID id;final long start;final Vec3d at,strike;final float yaw;final int variant;boolean struck,over;
        Ultimate(UUID id,long start,Vec3d at,float yaw,int variant,Vec3d strike){this.id=id;this.start=start;this.at=at;this.yaw=yaw;this.variant=variant;this.strike=strike;}
    }
    /**
     * H's sword formation (2.82.0): its id and start, its middle (on the ground; 2.82.4: IsosangRules.FORMATION_STAND_BACK
     * blocks ahead of where its user stood as it began, the way they faced; until 2.82.3 right under them), the way its
     * user faced, and where its user stands (the ground they stood on as it began, under the sword they stand on:
     * IsosangRules.standAt, the same sword every client draws there).
     */
    private static final class Formation {
        final UUID id;final long start;final Vec3d centre,spot;final float yaw;
        Formation(UUID id,long start,Vec3d spot,float yaw){
            this.id=id;this.start=start;this.spot=spot;this.yaw=yaw;
            this.centre=spot.add(Vec3d.fromPolar(0,yaw).multiply(IsosangRules.FORMATION_STAND_BACK));
        }
    }
    /**
     * R's sweep (2.81.0): its id and start, where it stands (its feet: its user's, or out toward the foe in the crosshair)
     * and the way it faces, the foe it was aimed at (an entity id, -1 for none), whom it has struck, and whether its user
     * has let go of it (struck or held after it began to turn: the sword sweeps on, the body is free).
     */
    private static final class Sweep {
        final UUID id;final long start;final Vec3d at;final float yaw;final int target;final Set<UUID> struck=new HashSet<>();boolean released;
        Sweep(UUID id,long start,Vec3d at,float yaw,int target){this.id=id;this.start=start;this.at=at;this.yaw=yaw;this.target=target;}
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
                else if(skill==R||skill==R_HELD)sweep(player,skill==R_HELD);
                else if(skill==H)formation(player);
                else if(skill==G)ultimate(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(IsosangAttack::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            // (2.82.0) a formation under way ends with them: every viewer is told, its swords go
            var st=STATES.get(handler.player.getUuid());if(st!=null&&st.form!=null)end(handler.player,st,handler.player.getWorld().getTime());
            // (2.83.0) and G before its tablet strikes: it does not
            if(st!=null&&st.ult!=null&&!st.ult.struck&&!st.ult.over)call(handler.player,st,handler.player.getWorld().getTime());
            STATES.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    /** Whether this player's attack is Lee So-sang's sword: theirs, alive, on the map. */
    public static boolean attacks(PlayerEntity p){return p!=null&&StandManager.isosang(p);}
    /** Free to start a hit: nothing else holds them (a blow, a guard, a dash, a skill, a cutscene); a hit's own hold aside. */
    public static boolean able(ServerPlayerEntity p){return attacks(p)&&!p.isSleeping()&&!p.hasVehicle()&&!CombatManager.busyOtherwise(p)&&!CombatManager.cinematic(p);}
    /** Whether one of this player's hits (or, 2.81.0, their R) holds them now (IsosangRules.HOLD): CombatManager.busy and actionLocked count it. */
    public static boolean busy(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s!=null&&s.world==p.getWorld()&&(holding(s.combo,s.start,p.getWorld().getTime())||sweepHolds(s,p.getWorld().getTime())||forming(s,p.getWorld().getTime())||casting(s,p.getWorld().getTime()));
    }
    /** Whether G holds its user now (2.83.0): from its start for its cutscene ({@link IsosangRules#G_HOLD}), unless it was called off. */
    private static boolean casting(State s,long now){var u=s.ult;return u!=null&&!u.over&&now>=u.start&&now-u.start<HOLD[ULTIMATE];}
    /**
     * Whether this one is held by their G now (2.83.0): its cutscene holds them where they stand (KagenoManager.frozen),
     * nothing harms them (shielded), and nothing else of theirs may begin (CombatManager.cinematic).
     */
    public static boolean casting(LivingEntity e){
        if(!(e instanceof PlayerEntity p)||e.getWorld().isClient)return false;
        var s=STATES.get(p.getUuid());return s!=null&&s.world==p.getWorld()&&casting(s,p.getWorld().getTime());
    }
    /** Whether harm from {@code source} is kept from {@code e}: G holds them (what bypasses invulnerability, /kill or the void, still reaches them). */
    public static boolean shielded(LivingEntity e,net.minecraft.entity.damage.DamageSource source){
        return casting(e)&&!source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
    /** The tick this player's G is ready again (0 if it is ready, 2.83.0). */
    public static long gReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.gReady;}
    /** This player's G as a boss reads it (2.83.0): begun at {@code start}, where its user stands; null for none under way before its strike. */
    public record UltimateNow(long start,Vec3d at,Vec3d strike){}
    public static UltimateNow ultimateNow(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());long now=p==null?0:p.getWorld().getTime();
        if(s==null||s.ult==null||s.ult.struck||s.ult.over||now-s.ult.start>G_IMPACT)return null;
        return new UltimateNow(s.ult.start,s.ult.at,s.ult.strike);
    }
    /** Whether H's sword formation is under way for its user now (2.82.0): it holds them from its start to its end. */
    private static boolean forming(State s,long now){var f=s.form;return f!=null&&now>=f.start&&now-f.start<LENGTH[FORMATION];}
    /**
     * Whether this player is up on H's sword now (2.82.0; 2.82.4: from their leap, over where they stood): up over the ground with nothing under them, the server
     * must not take them for flying (ServerPlayNetworkHandler's floating check, FormationFloatMixin).
     */
    public static boolean raised(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());if(s==null||s.world!=p.getWorld()||!forming(s,p.getWorld().getTime()))return false;
        return onSword((p.getWorld().getTime()-s.form.start)/20.0);
    }
    /**
     * This player's sword formation as a boss reads it (2.82.0): begun at {@code start}, its middle at {@code centre}, and
     * where it holds them, level on its ground (2.82.4: where they stood, on its outer ring).
     */
    public record FormationNow(long start,Vec3d centre,Vec3d holds){}
    /** This player's sword formation now (null for none). */
    public static FormationNow formationNow(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());long now=p==null?0:p.getWorld().getTime();if(s==null||!forming(s,now))return null;
        return new FormationNow(s.form.start,s.form.centre,s.form.spot);
    }
    /** The tick this player's H is ready again (0 if it is ready, 2.82.0). */
    public static long hReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.hReady;}
    /** Whether R's sweep holds its user now (2.81.0): from its start for its HOLD, until they let go of it. */
    private static boolean sweepHolds(State s,long now){var w=s.sweep;return w!=null&&!w.released&&now>=w.start&&now<w.start+HOLD[SWEEP];}
    /** Whether R is under way now (2.81.0): its motion still runs (it cannot be used again until then). */
    private static boolean sweeping(State s,long now){var w=s.sweep;return w!=null&&now-w.start<LENGTH[SWEEP];}
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
    /**
     * This player's R in flight, as a boss reads it (2.81.0): begun at {@code start}, standing at {@code at} (its feet) the
     * way {@code yaw}, and whether it has begun to turn (its sword sweeps on to its end whatever becomes of its user).
     */
    public record SweepNow(long start,Vec3d at,float yaw){}
    /** This player's R now (null for none, or once its sword has swept its way). */
    public static SweepNow sweeping(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());var w=s==null?null:s.sweep;if(w==null)return null;
        long now=p.getWorld().getTime();if((now-w.start)/20.0>SWEEP_END)return null;
        return new SweepNow(w.start,w.at,w.yaw);
    }
    /** The tick this player's R is ready again (0 if it is ready, 2.81.0). */
    public static long rReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.rReady;}
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
    /** This player is Lee So-sang's no more: the attack's rest is gone, so it is ready if they are again; so are E's cooldown and its empowerment (2.80.0), and R's cooldown (2.81.0). */
    public static void forget(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null){s.ready=0;s.eReady=0;s.rReady=0;s.hReady=0;s.gReady=0;s.charged=s.restart=s.rQueued=s.rHeld=false;}}
    /**
     * This player's cooldowns are off (/botand nocooldown, every tick: NoCooldownState): the attack's rest and E's
     * cooldown are gone; an empowerment they hold stays theirs (2.80.0).
     */
    public static void cool(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null){s.ready=0;s.eReady=0;s.rReady=0;s.hReady=0;s.gReady=0;}}
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
        // (2.82.0) on H's sword only R may be used (the user's word: "일단은 R만 됨")
        if(forming(s,now))return false;
        if(now<s.ready&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        // (2.81.0) while R holds them: a press a little before it lets go is kept for then
        if(sweepHolds(s,now)){long free=s.sweep.start+HOLD[SWEEP];if(free-now<=BUFFER)s.queued=true;return s.queued;}
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
        // (2.81.0) never while R holds them
        long r=s.sweep!=null&&!s.sweep.released?s.sweep.start+HOLD[SWEEP]:Long.MIN_VALUE;
        if(!valid(s.combo))return r;
        if(position(s.combo)<4)return Math.max(r,nextAt(s.combo,s.start));
        return Math.max(r,s.start+(s.restart?HOLD[s.combo]:LENGTH[s.combo]));
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
        if(forming(s,now)||s.charged||s.empowered&&valid(s.combo)&&now<s.start+LENGTH[s.combo]||now<s.eReady&&!free)return false;
        s.charged=s.restart=true;s.queued=false;s.ready=0;
        send(p,new UUID(0,0),0,now,MathHelper.wrapDegrees(p.getYaw()),EMPOWERED,-1,null,null,null);
        return true;
    }
    /**
     * R (2.81.0, user request: "R스킬 거대한 검을 소환해서 전방을 크게 베는거야"): the giant sword's sweep, at once or, while a
     * hit of theirs holds them, as soon as it lets go if that is near (a press a little early is kept). Not while one is
     * held otherwise (a blow, a guard, a dash, a skill, a cutscene), while R is under way, or before it is ready again
     * ({@link IsosangRules#R_COOLDOWN} after the last one was over; none for a player the cooldowns are off for). R
     * sweeps right in front of its user; R held (2.81.1, user request: "그냥 r은 원래처럼 앞만 베고 R홀드를 해야지 보고 있는
     * 적에게 검이 순간이동 되게 만들자") sends the sword to the foe in the crosshair (aimSweep); the two share the cooldown.
     */
    public static boolean sweep(ServerPlayerEntity p,boolean held){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(sweeping(s,now)||now<s.rReady&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(holding(s.combo,s.start,now)){
            long free=s.start+HOLD[s.combo];
            if(free-now<=BUFFER){s.rQueued=true;s.rHeld=held;return true;}
            return false;
        }
        unleash(p,s,now,held);
        return true;
    }
    /**
     * The foe in the crosshair (2.81.0, the user's word: "쉬프트 락 기준으로 십자선으로 보고 있는 적"): the first body their
     * sight meets within {@link IsosangRules#SWEEP_AIM} blocks, its box taken {@link IsosangRules#SWEEP_AIM_SLACK} wider, not
     * behind a block; null for none. Their client turns them to where the camera's crosshair is before it asks for R
     * (BotandCamera.prepareInteraction), so their sight is the crosshair's.
     */
    public static LivingEntity aimed(ServerPlayerEntity p){
        var world=p.getServerWorld();Vec3d eye=p.getEyePos(),look=p.getRotationVec(1f);double far=SWEEP_AIM;
        var block=world.raycast(new RaycastContext(eye,eye.add(look.multiply(far)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(block.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)far=eye.distanceTo(block.getPos());
        Vec3d end=eye.add(look.multiply(far));LivingEntity best=null;double nearest=Double.MAX_VALUE;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(eye,end).expand(SWEEP_AIM_SLACK+1),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            Box box=e.getBoundingBox().expand(SWEEP_AIM_SLACK);double at;
            if(box.contains(eye))at=0;
            else{var hit=box.raycast(eye,end);if(hit.isEmpty())continue;at=eye.distanceTo(hit.get());}
            if(at<nearest){nearest=at;best=e;}
        }
        return best;
    }
    /**
     * Where R's sweep stands for this player now and the way it faces: right where they stand, the way they look; held
     * (2.81.1, the user's word: "R홀드를 해야지 보고 있는 적에게 검이 순간이동"; 2.81.0's R did this whenever a foe was in
     * the crosshair, the user's choice then "적에게로 옮겨 감"), out toward the foe in the crosshair if there is one, so its
     * blade passes them (IsosangRules.sweepShift), at their height: the sword shows there, not at its user.
     */
    static Sweep aimSweep(ServerPlayerEntity p,long now,boolean held){
        var foe=held?aimed(p):null;Vec3d feet=p.getPos();float yaw=MathHelper.wrapDegrees(p.getYaw());
        // (2.82.0) from H's sword: the sweep stands on the formation's ground under them, not up where they stand
        var st=STATES.get(p.getUuid());if(st!=null&&forming(st,now))feet=new Vec3d(feet.x,st.form.centre.y,feet.z);
        if(foe==null)return new Sweep(UUID.randomUUID(),now,feet,yaw,-1);
        Vec3d d=foe.getPos().subtract(feet);double far=Math.sqrt(d.x*d.x+d.z*d.z);
        if(far>1e-3)yaw=MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        Vec3d at=feet.add(Vec3d.fromPolar(0,yaw).multiply(sweepShift(far)));
        return new Sweep(UUID.randomUUID(),now,new Vec3d(at.x,foe.getY(),at.z),yaw,foe.getId());
    }
    /**
     * R begins (2.81.0): the combo in flight ends here with no rest (the next hit is the first again; an empowered combo
     * under way is spent, E's cooldown running from now), the sweep stands where its aim puts it, and R is ready again
     * {@link IsosangRules#R_COOLDOWN} after its motion is over.
     */
    private static void unleash(ServerPlayerEntity p,State s,long now,boolean held){
        s.rQueued=false;s.rHeld=false;s.queued=false;
        boolean spent=s.empowered;
        s.combo=0;s.blows=0;s.empowered=false;
        if(spent)spend(p,s,now);
        s.sweep=aimSweep(p,now,held);
        s.rReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?0:now+LENGTH[SWEEP]+R_COOLDOWN;
        p.clearActiveItem();p.setSprinting(false);p.swingHand(Hand.MAIN_HAND,true);CombatManager.interrupt(p);
        send(p,s.sweep.id,SWEEP,now,s.sweep.yaw,HIT,-1,null,null,s.sweep);
    }
    /**
     * R's sweep on a tick (2.81.0): from its first turn, whoever the blade passed over since the last tick (IsosangRules.swept,
     * not behind a block from its pommel), not struck by it yet, is struck; it is over once its motion is.
     */
    private static void cut(ServerPlayerEntity p,State s,long now){
        var w=s.sweep;long age=now-w.start;
        if(age>=LENGTH[SWEEP]){s.sweep=null;return;}
        if(age<1)return;
        double a0=sweepAngle((age-1)/20.0),a1=sweepAngle(age/20.0);if(a1>=a0)return;
        Vec3d f=Vec3d.fromPolar(0,w.yaw),right=new Vec3d(-f.z,0,f.x);var world=p.getWorld();
        Vec3d pivot=w.at.add(f.multiply(SWEEP_PIVOT_FW)).add(0,SWEEP_PIVOT_UP,0);
        var struck=strike(p,SWEEP,0,w.start,w.yaw,now,w.at,e->{
            Vec3d d=e.getPos().subtract(w.at);
            if(!swept(a0,a1,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5))return false;
            Vec3d chest=e.getPos().add(0,e.getHeight()*.5,0);
            return world.raycast(new RaycastContext(pivot,chest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()==net.minecraft.util.hit.HitResult.Type.MISS;
        },w.struck,false);
        if(!struck.isEmpty())send(p,w.id,SWEEP,w.start,w.yaw,LANDED,0,struck,null,null);
    }
    /**
     * R's user is struck or held (2.81.0): before its sword has begun to turn, R stops short and its sword is gone (its
     * cooldown runs from now); after, its user lets go of it and the sword sweeps on to its end.
     */
    private static void release(ServerPlayerEntity p,State s,long now){
        var w=s.sweep;if(w==null||w.released||now-w.start>=LENGTH[SWEEP])return;
        boolean turning=(now-w.start)/20.0>=SWEEP_START;
        w.released=true;
        if(!turning){s.sweep=null;if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.rReady=now+R_COOLDOWN;}
        send(p,w.id,SWEEP,w.start,w.yaw,STOPPED,-1,null,null,null);
    }
    /**
     * H (2.82.0, user request: "다음은 H스킬이야 엄청 많은 검을 박고 검 위로 올라가 검 위에 올라가 있을때는 움직 일 수 없어"): the
     * sword formation, at once. Not while one is held otherwise (a blow, a guard, a dash, a skill, a cutscene), while a
     * hit or R of theirs holds them, while it is under way, or before it is ready again ({@link IsosangRules#H_COOLDOWN}
     * after the last one was over; none for a player the cooldowns are off for).
     */
    public static boolean formation(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(s.form!=null&&now-s.form.start<LENGTH[FORMATION]||now<s.hReady&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(holding(s.combo,s.start,now)||sweepHolds(s,now))return false;
        unfold(p,s,now);
        return true;
    }
    /**
     * H begins (2.82.0): the combo in flight ends here with no rest (an empowered combo under way is spent), the formation
     * is laid round them on the ground under them, and H is ready again {@link IsosangRules#H_COOLDOWN} after it is over.
     */
    private static void unfold(ServerPlayerEntity p,State s,long now){
        s.queued=false;s.rQueued=false;
        boolean spent=s.empowered;
        s.combo=0;s.blows=0;s.empowered=false;
        if(spent)spend(p,s,now);
        var world=p.getServerWorld();Vec3d at=p.getPos();double y=at.y;
        var hit=world.raycast(new RaycastContext(at.add(0,.2,0),at.add(0,-6,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)y=hit.getPos().y;
        // (2.82.4: the formation's middle ahead of them, they on its outer ring where they stood)
        s.form=new Formation(UUID.randomUUID(),now,new Vec3d(at.x,y,at.z),MathHelper.wrapDegrees(p.getYaw()));
        s.hReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?0:now+LENGTH[FORMATION]+H_COOLDOWN;
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s.form.id,FORMATION,now,s.form.yaw,HIT,-1,null,null,null,s.form);
    }
    /**
     * The formation on a tick (2.82.0): it strikes whoever is in it as the swords strike and every FORMATION_EVERY ticks
     * after; its user is held where they stood (2.82.4: on the formation's outer ring, its middle ahead; their own client
     * lifts them up onto the pommel of the sword struck there and down again, IsosangClient.drive, as its own movement is;
     * the server puts them back if they stray, a moment allowed at first for the steps they were taking as it began, and
     * never falls them: FormationFloatMixin, fallDistance); it is over once its motion is. Whatever takes them off the map,
     * into a cutscene, to sleep or onto a mount ends it now.
     */
    private static void hold(ServerPlayerEntity p,State s,long now){
        var f=s.form;long age=now-f.start;
        if(age>=LENGTH[FORMATION]){s.form=null;return;}
        if(!attacks(p)||p.isSleeping()||p.hasVehicle()||CombatManager.cinematic(p)){end(p,s,now);return;}
        if(formationStrikes(age))zone(p,f,age);
        double t=age/20.0;Vec3d at=p.getPos(),to=f.spot;double dx=at.x-to.x,dz=at.z-to.z,leave=age<STRAY_GRACE?1:STRAY;
        // (a boss has no client to lift it: the server does, as the empowered fourth's leap back)
        if(p instanceof dev.sangwoo.celestialfall.boss.BossPlayer){
            if(onSword(t)){p.setPosition(to.x,to.y+IsosangRules.raised(t),to.z);p.setVelocity(Vec3d.ZERO);}
            else if(dx*dx+dz*dz>STRAY*STRAY)p.setPosition(to.x,at.y,to.z);
        }else if(dx*dx+dz*dz>leave*leave)p.networkHandler.requestTeleport(to.x,at.y,to.z,p.getYaw(),p.getPitch());
        p.fallDistance=0;
    }
    /**
     * How far (blocks, level) the formation's user may stand off where they stood before the server puts them back, and
     * for how many ticks at first a block is allowed (2.82.0: the steps they were taking as it began, before their client
     * heard of it).
     */
    static final double STRAY=.35;static final int STRAY_GRACE=10;
    /** The formation strikes (2.82.0): everyone in it, alive, the user's own aside, takes its damage; the first time, what they were doing stops. */
    private static void zone(ServerPlayerEntity p,Formation f,long age){
        var world=p.getServerWorld();float amount=formationDamage(age);boolean impact=age==FORMATION_IMPACT_AT;
        var source=new net.minecraft.entity.damage.DamageSource(world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).entryOf(FORMATION_DAMAGE),p);
        List<LivingEntity> struck=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(f.centre,f.centre).expand(FORMATION_RADIUS+2,FORMATION_HIGH+2,FORMATION_RADIUS+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            Vec3d d=e.getPos().subtract(f.centre);
            if(!formationOver(d.x,d.z,d.y,e.getWidth()*.5))continue;
            if(impact)CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,amount))continue;
            struck.add(e);
            if(!StarPlatinumTimeStop.deferFeedback(p,e,amount,f.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(amount);b.writeFloat(f.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
        }
        if(!struck.isEmpty()){p.onAttacking(struck.get(0));send(p,f.id,FORMATION,f.start,f.yaw,LANDED,impact?0:1,struck,null,null,f);}
    }
    /**
     * G (2.83.0, user request: "이번에는 G스킬 궁극기 시전자만 컷씬이 나오고 범위는 30블록 ..."): at once. Not while one is held
     * otherwise (a blow, a guard, a dash, a skill, a cutscene), while a hit, R or H of theirs holds them, while it is under
     * way, or before it is ready again ({@link IsosangRules#G_COOLDOWN} after the last one was over; none for a player the
     * cooldowns are off for).
     */
    public static boolean ultimate(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(casting(s,now)||now<s.gReady&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(holding(s.combo,s.start,now)||sweepHolds(s,now)||forming(s,now))return false;
        s.queued=false;s.rQueued=false;
        boolean spent=s.empowered;
        s.combo=0;s.blows=0;s.empowered=false;
        if(spent)spend(p,s,now);
        var world=p.getServerWorld();Vec3d at=p.getPos();float yaw=MathHelper.wrapDegrees(p.getYaw());
        // where its tablet strikes: G_AHEAD blocks ahead the way they face, on the first thing under the sky there
        Vec3d over=at.add(Vec3d.fromPolar(0,yaw).multiply(G_AHEAD));double y=at.y;
        var down=world.raycast(new RaycastContext(new Vec3d(over.x,at.y+24,over.z),new Vec3d(over.x,at.y-24,over.z),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(down.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)y=down.getPos().y;
        int variant=p.getRandom().nextInt(G_VARIANTS);
        s.ult=new Ultimate(UUID.randomUUID(),now,at,yaw,variant,new Vec3d(over.x,y,over.z));
        s.gReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?0:now+HOLD[ULTIMATE]+G_COOLDOWN;
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s.ult.id,ULTIMATE,now,yaw,HIT,-1,null,null,null,null,s.ult);
        return true;
    }
    /**
     * G on a tick (2.83.0): its tablet strikes as G_IMPACT comes (everyone within G_RANGE of where its user stands, alive,
     * they aside, takes its damage, what they were doing stopping); it holds its user until its cutscene is over. Its user
     * gone from the map, dead or Lee So-sang no more before it strikes calls it off: it does not strike.
     */
    private static void cast(ServerPlayerEntity p,State s,long now){
        var u=s.ult;long age=now-u.start;
        if(u.over){s.ult=null;return;}
        if(!u.struck&&(!attacks(p)||!p.isAlive())){call(p,s,now);s.ult=null;return;}
        if(!u.struck&&age>=G_IMPACT){u.struck=true;strike(p,u);}
        if(age>=HOLD[ULTIMATE]){s.ult=null;return;}
        p.fallDistance=0;
    }
    /** G is called off before its tablet strikes (2.83.0): every viewer is told (its tablet goes), and G is ready again its cooldown after now. */
    private static void call(ServerPlayerEntity p,State s,long now){
        var u=s.ult;if(u==null||u.over)return;u.over=true;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.gReady=Math.min(s.gReady,now+G_COOLDOWN);
        send(p,u.id,ULTIMATE,u.start,u.yaw,STOPPED,-1,null,null,null,null,u);
    }
    /** G's tablet strikes (2.83.0): everyone within G_RANGE of its user, its user aside, takes its damage. */
    private static void strike(ServerPlayerEntity p,Ultimate u){
        var world=p.getServerWorld();
        var source=new net.minecraft.entity.damage.DamageSource(world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).entryOf(ULTIMATE_DAMAGE),p);
        List<LivingEntity> struck=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(u.at,u.at).expand(G_RANGE+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            Vec3d d=e.getPos().subtract(u.at);
            if(!ultimateReaches(d.x,d.y,d.z,e.getWidth()*.5,e.getHeight()))continue;
            CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,G_DAMAGE))continue;
            struck.add(e);
            if(!StarPlatinumTimeStop.deferFeedback(p,e,G_DAMAGE,u.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(G_DAMAGE);b.writeFloat(u.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
        }
        if(!struck.isEmpty())p.onAttacking(struck.get(0));
        send(p,u.id,ULTIMATE,u.start,u.yaw,LANDED,0,struck,null,null,null,u);
    }
    /** The formation ends early (2.82.0): every viewer is told (its swords go now), and H is ready again its cooldown after now. */
    private static void end(ServerPlayerEntity p,State s,long now){
        var f=s.form;if(f==null)return;s.form=null;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.hReady=Math.min(s.hReady,now+H_COOLDOWN);
        send(p,f.id,FORMATION,f.start,f.yaw,STOPPED,-1,null,null,null,f);
    }
    /**
     * The empowerment is over (2.80.0): its combo broke, a hit of it stopped short, or they died or left the map before
     * it ran; E is ready again {@link IsosangRules#E_COOLDOWN} after {@code at}.
     */
    private static void spend(ServerPlayerEntity p,State s,long at){
        if(!s.charged)return;
        s.charged=s.restart=false;
        s.eReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?0:at+E_COOLDOWN;
        send(p,s.id,s.combo,s.start,s.yaw,SPENT,-1,null,null,null);
    }
    /** The user was struck (staggered or incapacitated, CombatManager): the hit in flight stops short; so does R before its sword turns (2.81.0). */
    public static void struck(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null){long now=p.getWorld().getTime();stop(p,s,now);release(p,s,now);s.rQueued=false;}}
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
            send(p,s.id,s.combo,s.start,s.yaw,RESTS,-1,null,null,null);
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
        send(p,s.id,s.combo,s.start,s.yaw,HIT,-1,null,null,null);
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
            // and the empowered fourth's giant sword (2.80.1); and R's sword sweeping (2.81.0)
            if(s.giant!=null)hurl(p,s,now);
            if(s.sweep!=null)cut(p,s,now);
            // (2.82.0) and H's sword formation (it goes on whoever strikes its user; only what takes them away ends it)
            if(s.form!=null)hold(p,s,now);
            // (2.83.0) and G (nothing harms its user while it holds them; only what takes them away calls it off)
            if(s.ult!=null)cast(p,s,now);
            // (2.80.0) dead, or off the map: an empowerment held is spent (a character changed forgets it, StandManager)
            if(s.charged&&!attacks(p)&&"isosang".equals(dev.sangwoo.celestialfall.progression.ProgressManager.profile(p).stand())){s.empowered=false;spend(p,s,now);}
            if(held(p)){stop(p,s,now);release(p,s,now);s.rQueued=false;continue;}
            settle(p,s,now);
            // (2.81.0) R kept for when the hit holding them lets go
            if(s.rQueued&&!holding(s.combo,s.start,now)){if(able(p)&&!sweeping(s,now))unleash(p,s,now,s.rHeld);else s.rQueued=false;}
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
        send(p,s.id,s.combo,s.start,s.yaw,STOPPED,-1,null,null,null);
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
            send(p,s.id,THRUST,s.start,s.yaw,THROWN,0,null,told,null);
            hurl(p,s,now);
            return;
        }
        Vec3d f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        Vec3d at=p.getPos();
        var struck=strike(p,combo,i,s.start,s.yaw,now,at,e->{Vec3d d=e.getPos().subtract(at);return lane(combo,i,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5);},null,s.empowered);
        send(p,s.id,combo,s.start,s.yaw,LANDED,i,struck,null,null);
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
        send(p,s.id,THROWS,s.start,s.yaw,THROWN,i,null,sword,null);
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
            send(p,w.id,THROWS,w.start,s.yaw,LANDED,w.index,struck,w,null);
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
        if(!struck.isEmpty()||to==0)send(p,w.id,4,w.start,w.yaw,LANDED,0,struck,null,null);
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
        if(!struck.isEmpty())send(p,g.id,THRUST,g.start,g.yaw,LANDED,0,struck,null,null);
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
        boolean thrown=combo==THROWS||combo==THRUST||combo==SWEEP;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand((combo==THROWS?2:combo==SWEEP?SWEEP_PIVOT_FW+SWEEP_REACH:MAX_REACH)+3),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
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
            // (2.81.0: R's sweep staggers no one: a skill's blow, as Kageno's skills' are)
            if(combo!=SWEEP&&(!KagenoManager.frozen(e)||StarPlatinumTimeStop.stopped(e)))HitStun.stagger(p,e,combo,Stagger.isosang(combo,i,start,now,empowered));
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
    private static void send(ServerPlayerEntity p,UUID id,int combo,long start,float yaw,int type,int blow,List<LivingEntity> struck,Sword sword,Sweep sweep){
        send(p,id,combo,start,yaw,type,blow,struck,sword,sweep,null);
    }
    private static void send(ServerPlayerEntity p,UUID id,int combo,long start,float yaw,int type,int blow,List<LivingEntity> struck,Sword sword,Sweep sweep,Formation form){
        send(p,id,combo,start,yaw,type,blow,struck,sword,sweep,form,null);
    }
    private static void send(ServerPlayerEntity p,UUID id,int combo,long start,float yaw,int type,int blow,List<LivingEntity> struck,Sword sword,Sweep sweep,Formation form,Ultimate ult){
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
            // (2.81.0, after what 2.80 sent: an older client stops reading before it) when R is ready again, and for R's
            // start where its sweep stands, the way it faces and the foe it was aimed at
            b.writeLong(s.rReady);b.writeBoolean(sweep!=null);
            if(sweep!=null){b.writeDouble(sweep.at.x);b.writeDouble(sweep.at.y);b.writeDouble(sweep.at.z);b.writeFloat(sweep.yaw);b.writeVarInt(sweep.target);}
            // (2.82.0, after what 2.81 sent) when H is ready again, and for H's formation its middle and the way its user faced
            b.writeLong(s.hReady);b.writeBoolean(form!=null);
            if(form!=null){b.writeDouble(form.centre.x);b.writeDouble(form.centre.y);b.writeDouble(form.centre.z);b.writeFloat(form.yaw);}
            // (2.83.0, after what 2.82 sent) when G is ready again, and for G which recording (and face) it plays and where
            // its tablet strikes the ground
            b.writeLong(s.gReady);b.writeBoolean(ult!=null);
            if(ult!=null){b.writeByte(ult.variant);b.writeDouble(ult.strike.x);b.writeDouble(ult.strike.y);b.writeDouble(ult.strike.z);}
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
