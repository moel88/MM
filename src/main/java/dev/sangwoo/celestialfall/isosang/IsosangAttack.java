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
     * (2.113.0) H's gauge, to its own player: how full it is (0 to IsosangRules.GAUGE_FULL), and on H the tick its formation
     * began (-1 for none) and the ticks into it its gauge runs dry and its swords flare (IsosangRules.gaugeLeft, gaugeEnd).
     */
    public static final Identifier GAUGE=CelestialFall.id("isosang_gauge_v1");
    /**
     * A hit starts; one of its blows lands (whom it struck); the attack rests until it is ready again; the hit in flight
     * stops short; (2.80.0) a sword of the empowered first is thrown (from where, which way and how far); E is used (the
     * next combo is empowered, the attack's rest gone); the empowerment is over without its combo having run (E's
     * cooldown runs from then).
     */
    public static final int HIT=0,LANDED=1,RESTS=2,STOPPED=3,THROWN=4,EMPOWERED=5,SPENT=6,CROSSED=7;
    /**
     * The skills the skill packet asks for: E (2.80.0), R (2.81.0) and R held (2.81.1: the key held down
     * {@link IsosangRules#R_HOLD_TICKS} ticks, the client's call, as Kageno's T held is), H (2.82.0) and G (2.83.0), Z
     * (2.93.0), T (2.96.0) and Y (2.97.0).
     */
    public static final int E=1,R=2,R_HELD=3,H=4,G=5,Z=6,T=7,Y=8;
    /** The formation's damage (2.82.0): no guard takes it (minecraft:bypasses_shield), its user is the attacker. */
    public static final net.minecraft.registry.RegistryKey<net.minecraft.entity.damage.DamageType> FORMATION_DAMAGE=
        net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,dev.sangwoo.celestialfall.CelestialFall.id("sword_formation"));
    /** G's damage (2.83.0): no guard takes it either (minecraft:bypasses_shield), its user is the attacker. */
    public static final net.minecraft.registry.RegistryKey<net.minecraft.entity.damage.DamageType> ULTIMATE_DAMAGE=
        net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,dev.sangwoo.celestialfall.CelestialFall.id("giant_sword"));
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
        /** (2.93.0) Z: its window, its moves in it and when it is ready again (IsosangRules.ZState). */
        IsosangRules.ZState z=IsosangRules.ZState.FRESH;
        /** (2.95.0) H's basic attack, 회풍만검, under way or the last one (null for none), and the tick it is ready again. */
        Volley volley;long vReady;
        /**
         * (2.96.0) T: the latest one (null for none under way), every one whose blows are still to come (2.96.4: she may use
         * it again as she stands on the far sword, the one before still bringing its swords and slashes down), and the tick
         * it is ready again.
         */
        Lunge lunge;final List<Lunge> lunges=new ArrayList<>();long tReady;
        /** (2.96.4) A press of T kept for when the one under way lets go of her (IsosangRules.BUFFER before: pressed again and again, one goes as she stands on the far sword), or null. */
        TPress tQueued;
        /** (2.97.0) Y under way or the last one (null for none), and the tick it is ready again. */
        Cage cage;long yReady;
        /** (2.101.0) H again, the formation's finale, under way (null for none; H's ready tick is hReady's). */
        Finale finale;
        /**
         * (2.113.0) H's gauge (IsosangRules.GAUGE_FULL full lets H be used): how much of it is full, the hit whose foes it
         * has counted ({@code gaugeHit}: its start tick) and whom (each foe once a hit).
         */
        int gauge;long gaugeHit=Long.MIN_VALUE;final Set<UUID> gaugeFoes=new HashSet<>();
    }
    /**
     * H again (2.101.0, IsosangRules.FINALE): its id and start, where she stands (level, on the formation's ground, under
     * the sword she stood on: where she comes down), the way she looked as she pressed it (the band ahead), which recording
     * it plays, the formation it ends (its id, its start, its middle, the way its user faced, the sword she stood on) and
     * how many of its blows have landed.
     */
    private static final class Finale {
        final UUID id,form;final long start,formStart;final Vec3d spot,centre;final float yaw,formYaw;final int variant,on;int blows;
        Finale(UUID id,long start,Vec3d spot,float yaw,int variant,Formation f){
            this.id=id;this.start=start;this.spot=spot;this.yaw=yaw;this.variant=variant;
            this.form=f.id;this.formStart=f.start;this.centre=f.centre;this.formYaw=f.yaw;this.on=f.on;
        }
    }
    /**
     * Y (2.97.0, IsosangRules.CAGE): whom and where its swords come down on (as H's basic attack's, {@code aim}: its id,
     * start, the way from her to the foe, the foe, the point it began on, the formation's middle, the foe's last chest,
     * its blows so far), the middle of its ring on the ground ({@code at}: under the foe until it is set, CAGE_PLANT, then
     * where it stays), and whom it has caged.
     */
    private static final class Cage {
        final Volley aim;Vec3d at;boolean planted;final Set<UUID> caged=new HashSet<>();
        Cage(Volley aim,Vec3d at){this.aim=aim;this.at=at;}
    }
    /**
     * H's basic attack, 회풍만검 (2.95.0, IsosangRules.VOLLEY): its id and start, the way its user faced, the foe it comes
     * down on (an entity id, -1 for none: then the point), where it comes down (the foe's chest as it began, else where
     * the crosshair met), where the foe was last seen (its chest: where the rest come down if it is gone) and how many of
     * its swords have struck.
     */
    private static final class Volley {
        final UUID id;final long start;final float yaw;final int target;final Vec3d point,centre;Vec3d last;int blows;
        Volley(UUID id,long start,float yaw,int target,Vec3d point,Vec3d centre){this.id=id;this.start=start;this.yaw=yaw;this.target=target;this.point=point;this.last=point;this.centre=centre;}
    }
    /**
     * T (2.96.0, IsosangRules.LUNGE): whom and where its blows come down on (as H's basic attack's, {@code aim}: its id,
     * start, way, foe, point, the formation's middle, the foe's last chest, its blows so far), the sword she stood on,
     * the one she flies on to (2.96.1: any on the other side of the formation from hers; 2.96.0 the farthest from the foe)
     * and where she lands (in front of the foe since 2.96.2).
     */
    private static final class Lunge {
        final Volley aim;final int from,to;final Vec3d past;
        Lunge(Volley aim,int from,int to,Vec3d past){this.aim=aim;this.from=from;this.to=to;this.past=past;}
    }
    /** (2.96.4) A press of T kept (what its client said: whether it aimed, at whom and where). */
    private record TPress(boolean aimed,int foe,Vec3d meets){}
    /** (2.83.0) For the film harness only (as StandArrow.rigged): the recording the next G plays (0 or 1), -1 to roll. */
    public static volatile int riggedVariant=-1;
    /** (2.101.0) For the film harness only: the recording the next finale plays (0 to 2), -1 to roll. */
    public static volatile int riggedFinale=-1;
    /**
     * G (2.83.0): its id and start, where its user stands (their feet) and the way they faced, which of the recordings
     * (and faces) it plays, where its sword strikes the ground ({@link IsosangRules#G_AHEAD} blocks ahead, on whatever is
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
        /**
         * (2.93.0) Z: the sword its user stands on now (by its place in IsosangRules.formationSwords: the one struck under
         * them at first), the one they flew from (-1 for none yet), and the tick they left it.
         */
        int on=IsosangRules.standSword(),from=-1;long moved=Long.MIN_VALUE/2;
        /**
         * (2.94.0) H's dodge: whether its one blow has been kept from its user (IsosangRules.dodgeTime), and whether its
         * move waits for them to be free to fly (the swords not standing yet, a flight under way, held where they are).
         */
        boolean dodged,waiting;
        /**
         * (2.96.0) T: the tick it began (its dash, landing and flight on, IsosangRules.lungeSpot), the sword she stood on
         * as it began and where she lands (in front of the foe since 2.96.2); she flies on to {@code on} at {@code moved} (Z's flight).
         */
        long lungeAt=Long.MIN_VALUE/2;int lungeFrom=-1;Vec3d past=Vec3d.ZERO;
        final double[][] swords;
        /**
         * (2.113.0) H's gauge on it: the tick into it (fractional) its gauge runs dry (from full over GAUGE_TICKS; T takes its
         * share at once, IsosangRules.gaugeAfterT) and the one its swords flare at, its end (as its gauge runs dry; a T under
         * way holds it on until T is over, gaugeEnd; it never comes later). H_END while T is not used.
         */
        double emptyAge=IsosangRules.GAUGE_TICKS,endAge=IsosangRules.H_END*20.0;
        /** Its end (seconds into it): its swords flare. */
        double end(){return endAge/20;}
        /** Whether it is over {@code age} ticks in: its swords flared and she has dropped (IsosangRules.formationLength). */
        boolean over(long age){return age>=IsosangRules.formationLength(endAge)-1e-6;}
        Formation(UUID id,long start,Vec3d spot,float yaw){
            this.id=id;this.start=start;this.spot=spot;this.yaw=yaw;
            this.centre=spot.add(Vec3d.fromPolar(0,yaw).multiply(IsosangRules.FORMATION_STAND_BACK));
            this.swords=IsosangRules.formationSwords(IsosangRules.formationSeed(id));
        }
        /** Where sword {@code k} strikes the ground (level, on the formation's ground): forward and right of its middle the way its user faced. */
        Vec3d place(int k){
            Vec3d f=Vec3d.fromPolar(0,yaw),r=new Vec3d(-f.z,0,f.x);double[] w=swords[k];
            return centre.add(f.multiply(w[0])).add(r.multiply(w[1]));
        }
        /** Whether its user is flying from one sword to another at {@code now} (2.93.0). */
        boolean flying(long now){return now>=moved&&now<moved+IsosangRules.Z_TRAVEL;}
        /** Whether T has them on its way at {@code now} (2.96.0): from its start until they stand on the far sword. */
        boolean lunging(long now){return lungeFrom>=0&&now>=lungeAt&&now<lungeAt+IsosangRules.LUNGE_HOP+IsosangRules.Z_TRAVEL;}
        /**
         * Where it holds its user at {@code now}, level on its ground (2.93.0): under the sword they stand on (the first,
         * where they stood as it began, until Z moves them), on the way between two in Z's flight.
         */
        Vec3d spotAt(long now){
            // (2.96.0) T: along its dash from the sword she stood on to where she lands, there, and on over Z's flight
            if(lunging(now)){
                Vec3d a=place(lungeFrom),b=place(on);
                double[] xz=IsosangRules.lungeSpot(now-lungeAt,a.x,a.z,past.x,past.z,b.x,b.z);
                return new Vec3d(xz[0],centre.y,xz[1]);
            }
            if(from<0||now>=moved+IsosangRules.Z_TRAVEL)return on==IsosangRules.standSword()&&from<0?spot:place(on);
            double u=IsosangRules.zAlong(Math.max(0,now-moved)/(double)IsosangRules.Z_TRAVEL);
            return place(from).lerp(place(on),u);
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
        // (the user's 2.87.0 preview) G's arena: who shares its cutscene
        GArenaServer.initialize();
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            // (2.95.0) on H's sword the press carries whom the client's crosshair looks at (an entity id, -1 for none) and
            // where it meets (H's basic attack, 회풍만검); every other press is empty (and an older client's on the sword)
            int bytes=buf.readableBytes();if(bytes!=0&&(bytes<25||bytes>29))return;
            int asked=-1;Vec3d meets=null;
            if(bytes!=0){
                asked=buf.readVarInt();meets=new Vec3d(buf.readDouble(),buf.readDouble(),buf.readDouble());
                if(buf.isReadable()||!Double.isFinite(meets.x)||!Double.isFinite(meets.y)||!Double.isFinite(meets.z))return;
            }
            int foe=asked;Vec3d at=meets;boolean aimed=bytes!=0;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                if(++s.presses>RATE)return;
                request(player,aimed,foe,at);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SKILL,(server,player,handler,buf,response)->{
            // (2.93.0) Z with the sword it moves to (a second byte); (2.96.0) T with whom the crosshair picks and where it
            // meets (as the basic attack's press: a VarInt and three doubles, or nothing); every other skill is its one byte
            // (2.97.0) Y as T: with whom the crosshair picks and where it meets, or nothing
            int bytes=buf.readableBytes();if(bytes<1||bytes>30)return;int skill=buf.readUnsignedByte();
            boolean aiming=skill==T||skill==Y;
            if(skill==Z?bytes!=2:aiming?bytes!=1&&bytes<26:bytes!=1)return;int sword=skill==Z?buf.readUnsignedByte():-1;
            int asked=-1;Vec3d meets=null;
            if(aiming&&bytes>1){
                asked=buf.readVarInt();meets=new Vec3d(buf.readDouble(),buf.readDouble(),buf.readDouble());
                if(buf.isReadable()||!Double.isFinite(meets.x)||!Double.isFinite(meets.y)||!Double.isFinite(meets.z))return;
            }
            int foe=asked;Vec3d at=meets;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                if(++s.presses>RATE)return;
                if(skill==E)empower(player);
                else if(skill==R||skill==R_HELD)sweep(player,skill==R_HELD);
                else if(skill==H)formation(player);
                else if(skill==G)ultimate(player);
                else if(skill==Z)cross(player,sword);
                else if(skill==T)lunge(player,at!=null,foe,at);
                else if(skill==Y)cage(player,at!=null,foe,at);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(IsosangAttack::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            // (2.82.0) a formation under way ends with them: every viewer is told, its swords go
            var st=STATES.get(handler.player.getUuid());if(st!=null&&st.form!=null)end(handler.player,st,handler.player.getWorld().getTime());
            // (2.83.0) and G before its sword strikes: it does not
            if(st!=null&&st.ult!=null&&!st.ult.struck&&!st.ult.over)call(handler.player,st,handler.player.getWorld().getTime());
            // (2.101.0) and H again before its swords are down: they are not
            if(st!=null&&st.finale!=null)callOff(handler.player,st,handler.player.getWorld().getTime());
            STATES.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    /** Whether this player's attack is Lee So-sang's sword: theirs, alive, on the map. */
    public static boolean attacks(PlayerEntity p){return p!=null&&StandManager.isosang(p);}
    /** Free to start a hit: nothing else holds them (a blow, a guard, a dash, a skill, a cutscene); a hit's own hold aside. */
    public static boolean able(ServerPlayerEntity p){return attacks(p)&&!p.isSleeping()&&!p.hasVehicle()&&!CombatManager.busyOtherwise(p)&&!CombatManager.cinematic(p)&&!finaling(p);}
    /** Whether one of this player's hits (or, 2.81.0, their R) holds them now (IsosangRules.HOLD): CombatManager.busy and actionLocked count it. */
    public static boolean busy(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s!=null&&s.world==p.getWorld()&&(holding(s.combo,s.start,p.getWorld().getTime())||sweepHolds(s,p.getWorld().getTime())||forming(s,p.getWorld().getTime())||casting(s,p.getWorld().getTime())
            ||finaling(s,p.getWorld().getTime()));
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
        return (casting(e)||finaling(e))&&!source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
    /** Whether her H again holds her now (2.101.0): from its start until her camera is back with her (IsosangRules.FINALE_HOLD). */
    private static boolean finaling(State s,long now){var u=s.finale;return u!=null&&now>=u.start&&now-u.start<HOLD[FINALE];}
    /**
     * Whether this one is held by their H again now (2.101.0): its cutscene holds them where they stand (their own client
     * brings them down from the pommel, IsosangClient.drive), nothing harms them (shielded), nothing else of theirs may
     * begin (busy), and a time stop passes them by (as G's).
     */
    public static boolean finaling(LivingEntity e){
        if(!(e instanceof PlayerEntity p)||e.getWorld().isClient)return false;
        var s=STATES.get(p.getUuid());return s!=null&&s.world==p.getWorld()&&finaling(s,p.getWorld().getTime());
    }
    /** This player's H again as a boss reads it (2.101.0): begun at {@code start}, she at {@code spot}, its band the way {@code yaw}, {@code blows} landed; null for none. */
    public record FinaleNow(long start,Vec3d spot,float yaw,int blows){}
    public static FinaleNow finaleNow(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());var u=s==null?null:s.finale;
        return u==null?null:new FinaleNow(u.start,u.spot,u.yaw,u.blows);
    }
    /** The tick this player's G is ready again (0 if it is ready, 2.83.0). */
    public static long gReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.gReady;}
    /** This player's G as a boss reads it (2.83.0): begun at {@code start}, where its user stands; null for none under way before its strike. */
    public record UltimateNow(long start,Vec3d at,Vec3d strike){}
    public static UltimateNow ultimateNow(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());long now=p==null?0:p.getWorld().getTime();
        if(s==null||s.ult==null||s.ult.struck||s.ult.over||now-s.ult.start>GExplosionRules.HIT_TICK)return null;
        return new UltimateNow(s.ult.start,s.ult.at,s.ult.strike);
    }
    /** Whether H's sword formation is under way for its user now (2.82.0): it holds them from its start to its end. */
    private static boolean forming(State s,long now){var f=s.form;return f!=null&&now>=f.start&&!f.over(now-f.start);}
    /**
     * Whether this player is up on H's sword now (2.82.0; 2.82.4: from their leap, over where they stood): up over the ground with nothing under them, the server
     * must not take them for flying (ServerPlayNetworkHandler's floating check, FormationFloatMixin).
     */
    public static boolean raised(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());if(s==null||s.world!=p.getWorld())return false;
        // (2.101.0) H again: up on the pommel until she comes down onto the ground
        if(s.finale!=null&&IsosangRules.finaleAloft((p.getWorld().getTime()-s.finale.start)/20.0))return true;
        if(!forming(s,p.getWorld().getTime()))return false;
        return onSword((p.getWorld().getTime()-s.form.start)/20.0,s.form.end());
    }
    /**
     * Whether this player's formation carries them now (2.96.0): Z's flight or the dodge's, T's dash and its flight on, a
     * few ticks either side for their client's lag. FormationMoveMixin lets their moves through the server's "moved too
     * quickly" check then (a long flight's first tick goes over 10 blocks; a host's are let through anyway, so in single
     * player it was never seen); the formation holds them to its way after (hold).
     */
    public static boolean carried(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());if(s==null||s.world!=p.getWorld())return false;
        long now=p.getWorld().getTime();var f=s.form;if(f==null||!forming(s,now))return false;
        if(now>=f.moved-CARRY_BEFORE&&now<f.moved+IsosangRules.Z_TRAVEL+CARRY_AFTER)return true;
        return f.lungeFrom>=0&&now>=f.lungeAt-CARRY_BEFORE&&now<f.lungeAt+IsosangRules.LUNGE_HOP+IsosangRules.Z_TRAVEL+CARRY_AFTER;
    }
    /** How many ticks before and after the formation carries its user their moves are let through (their client's lag). */
    static final int CARRY_BEFORE=2,CARRY_AFTER=8;
    /**
     * This player's sword formation as a boss reads it (2.82.0): begun at {@code start}, its middle at {@code centre}, and
     * where it holds them, level on its ground (2.82.4: where they stood, on its outer ring).
     */
    public record FormationNow(long start,Vec3d centre,Vec3d holds){}
    /** This player's sword formation now (null for none). */
    public static FormationNow formationNow(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());long now=p==null?0:p.getWorld().getTime();if(s==null||!forming(s,now))return null;
        return new FormationNow(s.form.start,s.form.centre,s.form.spotAt(now));
    }
    /** This player's Z now (2.93.0, for the film harness and a boss): its window, its moves and when it is ready again. */
    public static IsosangRules.ZState zNow(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?IsosangRules.ZState.FRESH:s.z;}
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
    public static void forget(PlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s!=null){
            s.ready=0;s.eReady=0;s.rReady=0;s.hReady=0;s.gReady=0;s.yReady=0;s.charged=s.restart=s.rQueued=s.rHeld=false;s.tQueued=null;
            // (2.113.0) and H's gauge is empty (it is theirs as Lee So-sang)
            if(s.gauge>0&&p instanceof ServerPlayerEntity sp){s.gauge=0;gauge(sp,s);}
        }
    }
    /**
     * This player's cooldowns are off (/botand nocooldown, every tick: NoCooldownState): the attack's rest and E's
     * cooldown are gone; an empowerment they hold stays theirs (2.80.0).
     */
    public static void cool(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null){s.ready=0;s.eReady=0;s.rReady=0;s.hReady=0;s.gReady=0;s.yReady=0;}}
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
    public static boolean request(ServerPlayerEntity p){return request(p,false,-1,null);}
    /**
     * The same, with whom and where their client's crosshair looks ({@code aimed}: it said; the foe's entity id, -1 for
     * none, and where the crosshair meets): on H's sword it is H's basic attack (2.95.0, {@link #volley}).
     */
    public static boolean request(ServerPlayerEntity p,boolean aimed,int foe,Vec3d meets){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        // (2.82.0) on H's sword only R and Z were used (the user's word: "일단은 R만 됨"); (2.95.0) and its own basic attack
        if(forming(s,now))return volley(p,s,now,aimed,foe,meets);
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
        // (2.93.0) not while Z flies them from one of their formation's swords to another; (2.95.0) nor while H's basic
        // attack's swords fan out behind them
        if(s.form!=null&&forming(s,now)&&(s.form.flying(now)||volleyHolds(s,now)||lungeHolds(s,now)||cageHolds(s,now)))return false;
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
        // (2.101.0) H again on her formation: its finale
        if(forming(s,now))return finale(p,s,now);
        if(finaling(s,now)||now<s.hReady&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        // (2.113.0) only with H's gauge full (its cooldown run too: the user's choice, "그대로 둠")
        if(!IsosangRules.gaugeFull(s.gauge))return false;
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
        s.hReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?0:hReadyAfter(s.form);
        // (2.113.0) H spends its gauge: on it, the gauge drains from full as its formation stands (Formation.emptyAge)
        s.gauge=0;s.gaugeFoes.clear();s.gaugeHit=Long.MIN_VALUE;
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s.form.id,FORMATION,now,s.form.yaw,HIT,-1,null,null,null,s.form);
        gauge(p,s);
    }
    /** (2.113.0) The tick H is ready again after formation {@code f}: its cooldown from its end (its swords flared and she down). */
    private static long hReadyAfter(Formation f){return f.start+Math.round(IsosangRules.formationLength(f.endAge))+H_COOLDOWN;}
    /**
     * H again on her formation (2.101.0, user request: "이번에는 H를 사용한 상태에서 H를 한 번 더 누르면 되는 스킬 ... H 영역에
     * 있던 모든 검이 전부 플레이어 뒤로 가고 앞으로 꽂는 스킬이야 이걸 쓰면 H가 해제돼", IsosangRules.FINALE): its finale, at
     * once. Only while every sword of it stands and before they flare (IsosangRules.finaleTime), not while Z flies her, R
     * holds her, H's basic attack's swords fan out, T holds her or Y does. The formation is over as it begins (its swords
     * are the finale's: every viewer told of both, the finale first); the band it comes down in lies ahead the way she
     * looks now (her crosshair's: her client turned her to it), from where she stands; H is ready again its cooldown after
     * the finale's swords are gone (IsosangRules.finaleReady). One of its recordings at random (the server's roll).
     */
    private static boolean finale(ServerPlayerEntity p,State s,long now){
        var f=s.form;
        if(!IsosangRules.finaleTime((now-f.start)/20.0,f.end())||f.flying(now)||f.lunging(now)||sweepHolds(s,now)||volleyHolds(s,now)||lungeHolds(s,now)||cageHolds(s,now))return false;
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        float yaw=MathHelper.wrapDegrees(p.getYaw());
        int variant=riggedFinale>=0?Math.min(riggedFinale,FINALE_VARIANTS-1):p.getRandom().nextInt(FINALE_VARIANTS);riggedFinale=-1;
        s.finale=new Finale(UUID.randomUUID(),now,f.spotAt(now),yaw,variant,f);
        s.queued=false;s.rQueued=false;s.tQueued=null;
        // the formation is over as it begins: its swords are the finale's now (T's and Y's blows still to come go on)
        s.form=null;
        s.hReady=IsosangRules.finaleReady(now,free);
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s.finale.id,FINALE,now,yaw,HIT,-1,null,null,null,f,null,null,null,null,s.finale);
        send(p,f.id,FORMATION,f.start,f.yaw,STOPPED,-1,null,null,null,f);
        // (2.113.0) H again empties its gauge at once ("게이지가 즉시 전부 줄어들고")
        gauge(p,s);
        return true;
    }
    /**
     * H again on a tick (2.101.0): it holds her where she stands (level; her own client brings her down from the pommel,
     * a boss is put there) until her camera is back with her; each blow on its tick strikes everyone in the band ahead
     * (IsosangRules.finaleIn: no guard takes it, no one is thrown or staggered). Her gone (dead, off the map, Lee So-sang
     * no more) calls the rest off.
     */
    private static void conclude(ServerPlayerEntity p,State s,long now){
        var u=s.finale;long age=now-u.start;
        if(age>=FINALE_LENGTH){s.finale=null;return;}
        if(!attacks(p)||!p.isAlive()){callOff(p,s,now);return;}
        if(age<HOLD[FINALE]){
            double t=age/20.0;Vec3d at=p.getPos();double dx=at.x-u.spot.x,dz=at.z-u.spot.z;
            if(p instanceof dev.sangwoo.celestialfall.boss.BossPlayer){p.setPosition(u.spot.x,u.spot.y+IsosangRules.finaleReal(t,FORMATION_STAND),u.spot.z);p.setVelocity(Vec3d.ZERO);}
            else if(age>=2&&dx*dx+dz*dz>STRAY*STRAY)p.networkHandler.requestTeleport(u.spot.x,at.y,u.spot.z,p.getYaw(),p.getPitch());
            p.fallDistance=0;
        }
        Vec3d fw=Vec3d.fromPolar(0,u.yaw),rt=new Vec3d(-fw.z,0,fw.x),mid=u.spot.add(fw.multiply((FINALE_NEAR+FINALE_REACH)/2));
        while(u.blows<blows(FINALE)&&age>=blowAt(FINALE,u.blows)){
            int i=u.blows++;
            var struck=strike(p,FINALE,i,u.start,u.yaw,now,mid,e->{Vec3d d=e.getPos().subtract(u.spot);return IsosangRules.finaleIn(d.dotProduct(fw),d.dotProduct(rt),d.y,e.getWidth()*.5);},null,false);
            if(!struck.isEmpty())send(p,u.id,FINALE,u.start,u.yaw,LANDED,i,struck,null,null,null,null,null,null,null,u);
        }
    }
    /** H again called off before it is over (2.101.0: her gone): its viewers told (its swords not down yet go), H's cooldown from now. */
    private static void callOff(ServerPlayerEntity p,State s,long now){
        var u=s.finale;if(u==null)return;s.finale=null;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.hReady=Math.min(s.hReady,now+H_COOLDOWN);
        send(p,u.id,FINALE,u.start,u.yaw,STOPPED,-1,null,null,null,null,null,null,null,null,u);
    }
    /**
     * Z (2.93.0, user request: "H 스킬에서 박혀있는 모든 검이 전부 상호작용 되게 바꿔줘 그리고 동시에 z스킬 만들자 ... z스킬을
     * 누르면 10초 동안 최대 3번 쳐다보는 검으로 이동 할 수 있어", IsosangRules.zUse): its user flies onto the pommel of
     * sword {@code k} of their formation (the one they look at, as their client found it: the camera's middle), and stands
     * on it from then as on the first. Only while their formation stands with every sword planted
     * (IsosangRules.zTime), not while they fly or R holds them, not onto the sword they stand on, and only as Z's rules
     * let it (IsosangRules.zUse: up to three moves within ten seconds of the first, then twenty seconds' cooldown).
     */
    public static boolean cross(ServerPlayerEntity p,int k){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();var f=s.form;
        if(f==null||!forming(s,now)||!IsosangRules.zTime((now-f.start)/20.0,f.end()))return false;
        if(f.flying(now)||sweepHolds(s,now)||volleyHolds(s,now)||lungeHolds(s,now)||cageHolds(s,now)||k<0||k>=f.swords.length||k==f.on)return false;
        var next=IsosangRules.zUse(s.z,now,dev.sangwoo.celestialfall.NoCooldownState.enabled(p));if(next==null)return false;
        s.z=next;f.from=f.on;f.on=k;f.moved=now;
        send(p,f.id,FORMATION,f.start,f.yaw,CROSSED,k,null,null,null,f);
        return true;
    }
    /**
     * H's dodge (2.94.0, user request: "그리고 H를 쓰고 단 1회 맞은 데미지 무시하고 랜덤한 검으로 이동되게 바꿔 그 뒤로는 데미지
     * 잘 박히고 단 1회야 평타든 뭐든 다단히트 스킬 박혀도 1대만 씹을 수 있어", IsosangRules.dodgeTime): whether the blow
     * {@code source} brings {@code e} now is kept from them (none of it lands: the caller lets it go as a miss). Only the
     * first to reach them in their formation, and only a blow (it comes from someone or something: not fire, a fall,
     * poison or the like, nor their own); what bypasses invulnerability (/kill, the void) reaches them all the same. They
     * are moved onto a sword of it picked at random, now or as soon as they are free to fly (dodge).
     */
    public static boolean dodges(LivingEntity e,net.minecraft.entity.damage.DamageSource source){
        if(!(e instanceof ServerPlayerEntity p)||e.getWorld().isClient||!p.isAlive())return false;
        var s=STATES.get(p.getUuid());if(s==null||s.world!=p.getWorld()||s.form==null)return false;
        long now=p.getWorld().getTime();var f=s.form;
        if(f.dodged||!forming(s,now)||!IsosangRules.dodgeTime((now-f.start)/20.0,f.end())||!attacks(p))return false;
        if(source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        var by=source.getAttacker();var through=source.getSource();
        if(by==null&&through==null||by==p||through==p)return false;
        f.dodged=f.waiting=true;
        dodge(p,s,now);
        return true;
    }
    /**
     * H's dodge's move (2.94.0): its user flies onto a sword of their formation picked at random (not the one they stand
     * on) as Z flies them, its viewers told as of Z's move (its light and its sound; Z's own moves, window and cooldown as
     * they were). Once they are free to: the swords standing (IsosangRules.zTime), no flight under way, nothing holding
     * them where they are (a time stop, a Kageno skill, KagenoManager.frozen). Too late for it (the swords about to flare),
     * there is none.
     */
    private static void dodge(ServerPlayerEntity p,State s,long now){
        var f=s.form;if(f==null||!f.waiting)return;
        double t=(now-f.start)/20.0;
        if(!IsosangRules.zTime(t,f.end())){if(t>=IsosangRules.Z_FROM)f.waiting=false;return;}
        if(f.flying(now)||KagenoManager.frozen(p)||volleyHolds(s,now)||lungeHolds(s,now)||cageHolds(s,now))return;
        f.waiting=false;
        int k=IsosangRules.dodgeSword(f.on,p.getRandom().nextInt(f.swords.length-1),f.swords.length);
        f.from=f.on;f.on=k;f.moved=now;
        send(p,f.id,FORMATION,f.start,f.yaw,CROSSED,k,null,null,null,f);
    }
    /**
     * H's basic attack, 회풍만검 (2.95.0, user request: "이번에는 평타 만들자 영상 처럼 등 뒤에 검 6개가 나와서 바라보고 있는
     * 상대에게 날아감", IsosangRules.VOLLEY): the attack key on their formation's sword. Six swords of light fan out behind
     * them and come down on the foe they look at (the one their client's crosshair is nearest, as it said: {@code foe},
     * checked here; an older client's press says nothing, and the server looks along their sight itself), each striking
     * once as it reaches it (shower); with none they come down where the crosshair met ({@code meets}). Its reach is the
     * formation and 3 blocks more (2.95.2, user request: "평타 사거리는 H 범위 전체에서 3블록 정도 더", IsosangRules.volleyIn:
     * the foe must be in it, a block allowed for what their client saw; a crosshair beyond it brings the swords down at its
     * edge, on the ground there if there is any, downAt). Only while every sword of the formation stands
     * (IsosangRules.volleyTime), not while Z flies them or R holds them, one at a time, and once every 3 s
     * (IsosangRules.volleyReady; as soon as the last is over for a player the cooldowns are off for).
     */
    private static boolean volley(ServerPlayerEntity p,State s,long now,boolean aimed,int foe,Vec3d meets){
        var f=s.form;if(f==null||!IsosangRules.volleyTime((now-f.start)/20.0,f.end())||f.flying(now)||sweepHolds(s,now)||lungeHolds(s,now)||cageHolds(s,now))return false;
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(s.volley!=null&&now<s.volley.start+VOLLEY_LENGTH||now<s.vReady&&!free)return false;
        Vec3d eye=p.getEyePos();LivingEntity target=null;Vec3d at;
        if(aimed){
            if(foe>=0&&p.getWorld().getEntityById(foe) instanceof LivingEntity e&&fair(p,e)&&within(f.centre,e,1)&&p.canSee(e))target=e;
            at=meets==null?eye:meets;
        }else{
            // (an older client's press: whom their sight meets first, R held's way; else where it meets a block)
            target=aimed(p);if(target!=null&&!within(f.centre,target,0))target=null;
            Vec3d look=p.getRotationVec(1f);at=eye.add(look.multiply(VOLLEY_RANGE));
            var block=p.getServerWorld().raycast(new RaycastContext(eye,at,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
            if(block.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)at=block.getPos();
        }
        at=target!=null?chest(target):downAt(p,f.centre,eye,at);
        s.volley=new Volley(UUID.randomUUID(),now,MathHelper.wrapDegrees(p.getYaw()),target==null?-1:target.getId(),at,f.centre);
        s.vReady=IsosangRules.volleyReady(now,free);
        send(p,s.volley.id,VOLLEY,now,s.volley.yaw,HIT,-1,null,null,null,f,null,s.volley);
        return true;
    }
    /** Whether {@code e} is within H's basic attack's reach of the formation whose middle is {@code centre} (2.95.2, IsosangRules.volleyIn), {@code slack} blocks more all round. */
    private static boolean within(Vec3d centre,LivingEntity e,double slack){
        Vec3d d=e.getPos().subtract(centre);return IsosangRules.volleyIn(d.x,d.z,d.y,e.getWidth()*.5,slack);
    }
    /**
     * Where H's basic attack's swords come down with no foe (2.95.2): where the crosshair met, within the reach
     * (IsosangRules.volleyPoint: else where the line to it from their eyes goes out of the reach); brought in, onto the
     * ground under that if there is some down to the reach's floor (so they stick in at its edge, not in the air).
     */
    private static Vec3d downAt(ServerPlayerEntity p,Vec3d centre,Vec3d eye,Vec3d meets){
        Vec3d a=eye.subtract(centre),b=meets.subtract(centre);
        double[] k=IsosangRules.volleyPoint(a.x,a.y,a.z,b.x,b.y,b.z);
        Vec3d at=centre.add(k[0],k[1],k[2]);if(k[3]==0)return at;
        Vec3d floor=new Vec3d(at.x,centre.y+FORMATION_LOW-IsosangRules.VOLLEY_BEYOND,at.z);
        if(floor.y>=at.y)return at;
        var block=p.getServerWorld().raycast(new RaycastContext(at,floor,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        return block.getType()!=net.minecraft.util.hit.HitResult.Type.MISS?block.getPos():at;
    }
    /**
     * T (2.96.0, user request: "이번에는 T스킬 만들자 영상처럼 일단 T를 누르면 평타 6검이 날아가는 동시에 빠르게 바라보는 상대에게
     * 돌진 베어내고 적에게 3 참격으로 베이는 동시에 다른 검으로 넘어가는 스킬이야", IsosangRules.LUNGE): on their formation's
     * sword, the foe the crosshair picks (as the basic attack picks it: {@code foe} as their client said, checked here;
     * an older client's press says nothing and their sight is looked along) or, with none, where it met (brought in to the
     * reach's edge, on the ground there, downAt). They dash from the sword they stand on at it and land just in front of
     * it at its side (2.96.2, IsosangRules.lungeLanding; short of any block in the way; 2.96.0 to 2.96.1 through it and
     * 3 blocks beyond), then fly on onto a sword of the formation on the other side
     * of its middle from the one they used it from, the server's roll (2.96.1, IsosangRules.oppositeSword; Z's flight;
     * the formation holds them along all of it, Formation.spotAt). Only while every sword stands and it
     * can be over before they flare (IsosangRules.lungeTime), not while Z flies them, R holds them, H's basic attack's
     * swords fan out or T holds them, and once it is ready again (IsosangRules.lungeReady, 2.96.4: as they stand on the far
     * sword, no cooldown for now; the T before still brings its last blows down).
     */
    public static boolean lunge(ServerPlayerEntity p,boolean aimed,int foe,Vec3d meets){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();var f=s.form;
        // (2.96.4) pressed while the one under way still holds her, a moment before it lets go: kept for then
        if(f!=null&&forming(s,now)&&lungeHolds(s,now)&&s.lunge.aim.start+HOLD[LUNGE]-now<=BUFFER){s.tQueued=new TPress(aimed,foe,meets);return true;}
        if(f==null||!forming(s,now)||!IsosangRules.lungeTime((now-f.start)/20.0,f.end())||f.flying(now)||sweepHolds(s,now)||volleyHolds(s,now)||lungeHolds(s,now)||cageHolds(s,now))return false;
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(now<s.tReady&&!free)return false;
        Vec3d eye=p.getEyePos();LivingEntity target=null;Vec3d at;
        if(aimed){
            if(foe>=0&&p.getWorld().getEntityById(foe) instanceof LivingEntity e&&fair(p,e)&&within(f.centre,e,1)&&p.canSee(e))target=e;
            at=meets==null?eye:meets;
        }else{
            target=aimed(p);if(target!=null&&!within(f.centre,target,0))target=null;
            Vec3d look=p.getRotationVec(1f);at=eye.add(look.multiply(VOLLEY_RANGE));
            var block=p.getServerWorld().raycast(new RaycastContext(eye,at,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
            if(block.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)at=block.getPos();
        }
        // (2.96.4) used again while the one before is under way with no one in the crosshair (she has just flown on to the
        // far sword and the camera is still turning): the one before's foe again, as the video brings T after T down on one
        var last=s.lunge;
        if(target==null&&last!=null&&now-last.aim.start<IsosangRules.LUNGE_LENGTH&&last.aim.target>=0
            &&p.getWorld().getEntityById(last.aim.target) instanceof LivingEntity prev&&fair(p,prev)&&within(f.centre,prev,1)&&p.canSee(prev))target=prev;
        at=target!=null?chest(target):downAt(p,f.centre,eye,at);
        // where they land: just in front of the foe at its side (2.96.2, IsosangRules.lungeLanding), on the ground there
        // (with none, where they come down), short of a block in the way
        Vec3d start=f.place(f.on),goal=target!=null?target.getPos():at;
        Vec3d way=new Vec3d(goal.x-start.x,0,goal.z-start.z);double reach=way.length();
        way=way.lengthSquared()<1e-4?Vec3d.fromPolar(0,p.getYaw()):way.normalize();
        Vec3d past=at;
        if(target!=null){double[] xz=IsosangRules.lungeLanding(goal.x,goal.z,way.x,way.z,reach);past=new Vec3d(xz[0],goal.y,xz[1]);}
        var world=p.getServerWorld();
        Vec3d from=start.add(0,1,0),to=new Vec3d(past.x,Math.max(past.y,f.centre.y)+1,past.z);
        var wall=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(wall.getType()!=net.minecraft.util.hit.HitResult.Type.MISS){
            Vec3d run=to.subtract(from).multiply(1,0,1);run=run.lengthSquared()<1e-4?way:run.normalize();
            Vec3d hit=wall.getPos().subtract(run.multiply(.6));past=new Vec3d(hit.x,past.y,hit.z);
        }
        Vec3d top=new Vec3d(past.x,Math.max(past.y,f.centre.y)+2,past.z),bottom=new Vec3d(past.x,f.centre.y+FORMATION_LOW-IsosangRules.VOLLEY_BEYOND,past.z);
        var ground=world.raycast(new RaycastContext(top,bottom,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        past=ground.getType()!=net.minecraft.util.hit.HitResult.Type.MISS?ground.getPos():new Vec3d(past.x,f.centre.y,past.z);
        // the sword they fly on to: any on the other side of the formation's middle from the one they used it from (2.96.1,
        // the server's roll; 2.96.0 the farthest from the foe)
        int onto=IsosangRules.oppositeSword(f.swords,f.on,p.getRandom().nextInt(1<<20));
        float yaw=(float)Math.toDegrees(Math.atan2(-way.x,way.z));
        s.lunge=new Lunge(new Volley(UUID.randomUUID(),now,MathHelper.wrapDegrees(yaw),target==null?-1:target.getId(),at,f.centre),f.on,onto,past);
        s.lunges.add(s.lunge);
        f.lungeAt=now;f.lungeFrom=f.on;f.past=past;f.from=f.on;f.on=onto;f.moved=now+IsosangRules.LUNGE_HOP;
        s.tReady=IsosangRules.lungeReady(now,free);
        // (2.113.0) T takes its share of H's gauge at once ("T스킬을 쓰면 게이지가 20% 확 줄어들게"): the formation ends as it
        // runs dry, but not before this T is over (its swords and slashes down, she on the far sword); H's cooldown from then
        f.emptyAge=IsosangRules.gaugeAfterT(f.emptyAge);f.endAge=Math.min(f.endAge,IsosangRules.gaugeEnd(now-f.start,f.emptyAge));
        if(!free)s.hReady=hReadyAfter(f);
        p.swingHand(Hand.MAIN_HAND,true);
        send(p,s.lunge.aim.id,LUNGE,now,s.lunge.aim.yaw,HIT,-1,null,null,null,f,null,s.lunge.aim,s.lunge);
        gauge(p,s);
        return true;
    }
    /** Whether T holds this player now (2.96.0; 2.96.4: until they stand on the far sword, HOLD[LUNGE]; it held them all 1.25 s before). */
    private static boolean lungeHolds(State s,long now){var l=s.lunge;return l!=null&&now>=l.aim.start&&now<l.aim.start+HOLD[LUNGE];}
    /**
     * T on a tick (2.96.0): each blow on its tick (the dash as they pass the foe, the six swords, the three slashes) the foe
     * it was sent at, wherever it has gone so long as it is still there and within VOLLEY_LEASH of the reach, or, with none
     * (or it gone), whoever is where it all comes down (its box widened VOLLEY_SPOT); no knockback, a basic attack's stagger
     * until the next. Its user gone (dead, off the map, Lee So-sang no more, their formation over) calls the rest off.
     * (2.96.4) Every T whose blows are still to come, oldest first: the one before goes on bringing its last swords and
     * slashes down after the next has begun.
     */
    private static void rush(ServerPlayerEntity p,State s,long now){
        // (2.101.0: H again ends the formation; what T still brings down goes on)
        if(!attacks(p)||s.form==null&&s.finale==null){halt(p,s,now);return;}
        for(var l:List.copyOf(s.lunges)){
            var v=l.aim;long age=now-v.start;
            LivingEntity foe=null;
            if(v.target>=0&&p.getWorld().getEntityById(v.target) instanceof LivingEntity e&&fair(p,e)&&within(v.centre,e,IsosangRules.VOLLEY_LEASH)){foe=e;v.last=chest(e);}
            while(v.blows<blows(LUNGE)&&age>=blowAt(LUNGE,v.blows)){
                int i=v.blows++;List<LivingEntity> struck;
                if(foe!=null){var target=foe;struck=strike(p,LUNGE,i,v.start,v.yaw,now,target.getPos(),e->e==target,null,false);}
                else{Vec3d at=v.last;struck=strike(p,LUNGE,i,v.start,v.yaw,now,at,e->e.getBoundingBox().expand(VOLLEY_SPOT).contains(at),null,false);}
                if(!struck.isEmpty())send(p,v.id,LUNGE,v.start,v.yaw,LANDED,i,struck,null,null,s.form,null,v,l);
            }
            if(age>=IsosangRules.LUNGE_LENGTH){s.lunges.remove(l);if(s.lunge==l)s.lunge=null;}
        }
    }
    /**
     * T called off before it is over (2.96.0: its user gone, their formation over): its viewers told, its cooldown from
     * now; their formation (if it stands on) holds them on the sword they were flying to. (2.96.4) Every one whose blows
     * are still to come.
     */
    private static void halt(ServerPlayerEntity p,State s,long now){
        if(s.lunges.isEmpty())return;
        var all=List.copyOf(s.lunges);s.lunges.clear();s.lunge=null;s.tQueued=null;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.tReady=Math.min(s.tReady,now+IsosangRules.LUNGE_COOLDOWN);
        var f=s.form;if(f!=null&&f.lunging(now)){f.lungeFrom=-1;f.from=-1;f.moved=now;}
        for(var l:all)send(p,l.aim.id,LUNGE,l.aim.start,l.aim.yaw,STOPPED,-1,null,null,null,f,null,l.aim,l);
    }
    /** T as a boss and the checks read it (2.96.0): begun at {@code start}, its blows coming down at {@code at} (its foe's chest as last seen, -1 for none), {@code blows} landed, where she lands and the swords she flies from and to. */
    public record LungeNow(long start,Vec3d at,int target,int blows,Vec3d past,int from,int to){}
    public static LungeNow lungeNow(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());var l=s==null?null:s.lunge;
        return l==null?null:new LungeNow(l.aim.start,l.aim.last,l.aim.target,l.aim.blows,l.past,l.from,l.to);
    }
    /** (2.96.0) Where each sword of this player's formation stands (its foot on the ground; null for none; the multiplayer check reads it). */
    public static Vec3d[] formationPlaces(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());if(s==null||s.form==null)return null;
        Vec3d[] out=new Vec3d[s.form.swords.length];for(int k=0;k<out.length;k++)out[k]=s.form.place(k);return out;
    }
    /** (2.96.0) The tick this player's T is ready again (0 if it is). */
    public static long tReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.tReady;}
    /**
     * Y (2.97.0, user request: "이번에는 Y스킬 거대한 검 6개를 적 위에 소환하고 2개씩 빠르게 적에게 떨궈서 원 모양으로 적을
     * 가두고 잉크가 많이 튀는 스킬이야", IsosangRules.CAGE): on their formation's sword, the foe the crosshair picks (as H's
     * basic attack and T pick it: {@code foe} as their client said, checked here; an older client's press says nothing and
     * their sight is looked along) or, with none, where it met (brought in to the reach's edge, on the ground there,
     * downAt). Six giant swords show over it and come down round it two at a time, caging whom they stand round (trap).
     * The way from them to it is its way (their body turns to it). Only while every sword of the formation stands and it
     * can be over before they flare (IsosangRules.cageTime), not while Z flies them, R holds them, H's basic attack's
     * swords fan out, T holds them or Y does, one at a time, and once it is ready again (IsosangRules.cageReady: its
     * cooldown from when its swords are gone; at once for a player the cooldowns are off for).
     */
    public static boolean cage(ServerPlayerEntity p,boolean aimed,int foe,Vec3d meets){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();var f=s.form;
        if(f==null||!forming(s,now)||!IsosangRules.cageTime((now-f.start)/20.0,f.end())||f.flying(now)||sweepHolds(s,now)||volleyHolds(s,now)||lungeHolds(s,now))return false;
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(s.cage!=null&&now<s.cage.aim.start+CAGE_LENGTH||now<s.yReady&&!free)return false;
        Vec3d eye=p.getEyePos();LivingEntity target=null;Vec3d at;
        if(aimed){
            if(foe>=0&&p.getWorld().getEntityById(foe) instanceof LivingEntity e&&fair(p,e)&&within(f.centre,e,1)&&p.canSee(e))target=e;
            at=meets==null?eye:meets;
        }else{
            target=aimed(p);if(target!=null&&!within(f.centre,target,0))target=null;
            Vec3d look=p.getRotationVec(1f);at=eye.add(look.multiply(VOLLEY_RANGE));
            var block=p.getServerWorld().raycast(new RaycastContext(eye,at,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
            if(block.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)at=block.getPos();
        }
        Vec3d point=target!=null?chest(target):downAt(p,f.centre,eye,at);
        Vec3d ring=cageGround(p.getWorld(),p,target!=null?target.getPos():point,f.centre.y);
        // its way: from where she stands to it (her body turns to it), else the way she looks
        Vec3d d=ring.subtract(p.getPos());float yaw=d.x*d.x+d.z*d.z<1e-4?p.getYaw():(float)Math.toDegrees(Math.atan2(-d.x,d.z));
        s.cage=new Cage(new Volley(UUID.randomUUID(),now,MathHelper.wrapDegrees(yaw),target==null?-1:target.getId(),point,f.centre),ring);
        s.yReady=IsosangRules.cageReady(now,free);
        send(p,s.cage.aim.id,CAGE,now,s.cage.aim.yaw,HIT,-1,null,null,null,f,null,s.cage.aim,null,s.cage);
        return true;
    }
    /**
     * Where Y's ring stands for a foe (or a point) at {@code at} (2.97.0): on the ground under it, looked for from a little
     * over it down to the formation's floor and the reach's ({@code floor}: the formation's ground), else where it is.
     * The server and every client find it in the same blocks.
     */
    public static Vec3d cageGround(net.minecraft.world.World world,net.minecraft.entity.Entity body,Vec3d at,double floor){
        if(world==null)return at;
        Vec3d top=at.add(0,.5,0),bottom=new Vec3d(at.x,Math.min(at.y-.5,floor+FORMATION_LOW-IsosangRules.VOLLEY_BEYOND),at.z);
        var hit=world.raycast(new RaycastContext(top,bottom,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,body));
        return hit.getType()!=net.minecraft.util.hit.HitResult.Type.MISS?hit.getPos():at;
    }
    /** Whether Y holds this player now (2.97.0): from its start for its CAGE_HOLD (she stands on her sword until her camera is back with her). */
    private static boolean cageHolds(State s,long now){var c=s.cage;return c!=null&&now>=c.aim.start&&now<c.aim.start+HOLD[CAGE];}
    /**
     * Y on a tick (2.97.0): until its ring is set (CAGE_PLANT) it follows the foe it was sent at (so long as it is still
     * there and within VOLLEY_LEASH of the reach), on the ground under it; then it stays. Each blow on its tick strikes
     * everyone the ring stands round (IsosangRules.caged): no guard takes it, no one is thrown or staggered; whom it
     * strikes are caged until its swords are gone (CAGE_FREE: incapacitated, CAGE_SLOW slower; one stopped in a time stop
     * as time moves again). Its user gone (dead, off the map, Lee So-sang no more, their formation over) calls the rest off.
     */
    private static void trap(ServerPlayerEntity p,State s,long now){
        var c=s.cage;var v=c.aim;long age=now-v.start;
        if(age>=CAGE_LENGTH){s.cage=null;return;}
        // (2.101.0: H again ends the formation; Y's swords stand on through it)
        if(!attacks(p)||s.form==null&&s.finale==null){stopCage(p,s,now);return;}
        if(!c.planted){
            if(v.target>=0&&p.getWorld().getEntityById(v.target) instanceof LivingEntity e&&fair(p,e)&&within(v.centre,e,IsosangRules.VOLLEY_LEASH)){
                v.last=chest(e);c.at=cageGround(p.getWorld(),p,e.getPos(),v.centre.y);
            }
            // set: every viewer is told where it stays (their swords come down there)
            if(age>=CAGE_PLANT){c.planted=true;send(p,v.id,CAGE,v.start,v.yaw,THROWN,-1,null,null,null,s.form,null,v,null,c);}
        }
        while(v.blows<blows(CAGE)&&age>=blowAt(CAGE,v.blows)){
            int i=v.blows++;Vec3d ring=c.at;
            var struck=strike(p,CAGE,i,v.start,v.yaw,now,ring,e->{Vec3d d=e.getPos().subtract(ring);return IsosangRules.caged(d.x,d.z,d.y,e.getWidth()*.5);},null,false);
            for(var e:struck)if(c.caged.add(e.getUuid())){int left=(int)Math.max(1,v.start+CAGE_FREE-now);CombatManager.incapacitate(e,left,left,CAGE_SLOW);}
            if(!struck.isEmpty())send(p,v.id,CAGE,v.start,v.yaw,LANDED,i,struck,null,null,s.form,null,v,null,c);
        }
    }
    /** Y called off before it is over (2.97.0: its user gone, their formation over): its viewers told, its cooldown from now. */
    private static void stopCage(ServerPlayerEntity p,State s,long now){
        var c=s.cage;if(c==null)return;s.cage=null;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.yReady=Math.min(s.yReady,now+CAGE_COOLDOWN);
        send(p,c.aim.id,CAGE,c.aim.start,c.aim.yaw,STOPPED,-1,null,null,null,s.form,null,c.aim,null,c);
    }
    /** Y as a boss and the checks read it (2.97.0): begun at {@code start}, its ring's middle on the ground at {@code at}, its foe (-1 for none), {@code blows} landed. */
    public record CageNow(long start,Vec3d at,int target,int blows){}
    public static CageNow cageNow(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());var c=s==null?null:s.cage;
        return c==null?null:new CageNow(c.aim.start,c.at,c.aim.target,c.aim.blows);
    }
    /** (2.97.0) The tick this player's Y is ready again (0 if it is). */
    public static long yReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.yReady;}
    /** Whether H's basic attack's swords still fan out behind this player now (IsosangRules.VOLLEY_HOLD): no Z, R or move to another sword until they are thrown. */
    private static boolean volleyHolds(State s,long now){var v=s.volley;return v!=null&&now>=v.start&&now<v.start+VOLLEY_HOLD;}
    /** Whether {@code e} may be struck by {@code p}'s swords: alive, in the world, not them, not a merchant, and by the PvP rules. */
    private static boolean fair(ServerPlayerEntity p,LivingEntity e){
        return e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&e.getWorld()==p.getWorld()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&!(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other));
    }
    /** The middle of {@code e}'s body: where H's basic attack's swords come down. */
    private static Vec3d chest(LivingEntity e){return e.getPos().add(0,e.getHeight()*.55,0);}
    /**
     * H's basic attack on a tick (2.95.0): each sword strikes on its blow's tick (IsosangRules.volleyStrikes) the foe it was
     * sent at, wherever it has gone (so long as it is still there and within VOLLEY_LEASH of the reach, 2.95.2; until
     * 2.95.1 within 40 blocks of their eyes), or, with none (or it gone), whoever is where they come down (its box widened
     * VOLLEY_SPOT); each a blow of 2, no knockback, a basic attack's stagger. Its user gone (dead, off the map, Lee So-sang
     * no more) calls the rest off.
     */
    private static void shower(ServerPlayerEntity p,State s,long now){
        var v=s.volley;long age=now-v.start;
        if(!attacks(p)){s.volley=null;send(p,v.id,VOLLEY,v.start,v.yaw,STOPPED,-1,null,null,null,s.form,null,v);return;}
        LivingEntity foe=null;
        if(v.target>=0&&p.getWorld().getEntityById(v.target) instanceof LivingEntity e&&fair(p,e)&&within(v.centre,e,IsosangRules.VOLLEY_LEASH)){foe=e;v.last=chest(e);}
        while(v.blows<VOLLEY_SWORDS&&age>=blowAt(VOLLEY,v.blows)){
            int i=v.blows++;List<LivingEntity> struck;
            if(foe!=null){var target=foe;struck=strike(p,VOLLEY,i,v.start,v.yaw,now,target.getPos(),e->e==target,null,false);}
            else{Vec3d at=v.last;struck=strike(p,VOLLEY,i,v.start,v.yaw,now,at,e->e.getBoundingBox().expand(VOLLEY_SPOT).contains(at),null,false);}
            if(!struck.isEmpty())send(p,v.id,VOLLEY,v.start,v.yaw,LANDED,i,struck,null,null,s.form,null,v);
        }
        if(age>=VOLLEY_LENGTH)s.volley=null;
    }
    /** H's basic attack as a boss reads it (2.95.0): begun at {@code start}, its swords coming down at {@code at} (its foe's chest as last seen), {@code blows} struck so far. */
    public record VolleyNow(long start,Vec3d at,int target,int blows){}
    public static VolleyNow volleyNow(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());var v=s==null?null:s.volley;
        return v==null?null:new VolleyNow(v.start,v.last,v.target,v.blows);
    }
    /** (2.95.0) The tick this player's H's basic attack is ready again (0 if it is). */
    public static long vReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.vReady;}
    /** (2.94.0) Whether this player's formation's dodge has been used (the multiplayer check reads it). */
    public static boolean dodged(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.form!=null&&s.form.dodged;}
    /** (2.94.0) The sword this player stands on in their formation now (-1 for none; the multiplayer check reads it). */
    public static int standsOn(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null||s.form==null?-1:s.form.on;}
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
        // (2.113.0) over once its swords have flared (as its gauge ran dry) and she has dropped
        if(f.over(age)){s.form=null;gauge(p,s);return;}
        if(!attacks(p)||p.isSleeping()||p.hasVehicle()||CombatManager.cinematic(p)){end(p,s,now);return;}
        if(formationStrikes(age,f.endAge))zone(p,f,age);
        // (2.113.0) whoever is in it walks slower while it stands ("H스킬 상태일때는 영역내에 있는 모든 적은 50% 둔화")
        if(IsosangRules.zoneSlows(age,f.endAge))slowZone(p,f);
        // (2.94.0) H's dodge's move, kept until they were free to fly
        if(f.waiting)dodge(p,s,now);
        // (2.93.0) where it holds them: under the sword they stand on, Z's flight between two (their own client flies them;
        // the server lets it be as they fly and a moment after, as at first)
        double t=age/20.0;Vec3d at=p.getPos(),to=f.spotAt(now);double dx=at.x-to.x,dz=at.z-to.z,leave=age<STRAY_GRACE?1:STRAY;
        if(now<f.moved+IsosangRules.Z_TRAVEL+STRAY_GRACE)leave=Double.MAX_VALUE;
        // (a boss has no client to lift it: the server does, as the empowered fourth's leap back)
        if(p instanceof dev.sangwoo.celestialfall.boss.BossPlayer){
            if(onSword(t,f.end())){double up=f.lunging(now)?IsosangRules.lungeHeight(now-f.lungeAt,FORMATION_STAND,FORMATION_STAND):IsosangRules.raised(t,f.end());p.setPosition(to.x,to.y+up,to.z);p.setVelocity(Vec3d.ZERO);}
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
            // (2.113.0) its first blow (as it is laid) leaves whom it strikes slower for a while ("처음 H(영역을 전개)꽂을때 적을
            // 맞추면 3초동안 70% 둔화")
            if(impact)CombatManager.slow(e,H_IMPACT_SLOW_TICKS,H_IMPACT_SLOW);
            if(!StarPlatinumTimeStop.deferFeedback(p,e,amount,f.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(amount);b.writeFloat(f.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
        }
        if(!struck.isEmpty()){p.onAttacking(struck.get(0));send(p,f.id,FORMATION,f.start,f.yaw,LANDED,impact?0:1,struck,null,null,f);}
    }
    /**
     * G (2.83.0, user request: "이번에는 G스킬 궁극기 시전자만 컷씬이 나오고 범위는 30블록 ..."): since 2.109.0 its user
     * glows first and it is cast as the glow ends (CutsceneGlow; at once before). Not while one is held
     * otherwise (a blow, a guard, a dash, a skill, a cutscene), while a hit, R or H of theirs holds them, while it is under
     * way, or before it is ready again ({@link IsosangRules#G_COOLDOWN} after the last one was over; none for a player the
     * cooldowns are off for).
     */
    public static boolean ultimate(ServerPlayerEntity p){
        // (2.109.0, user request: "빛나는 메커니즘을 바꾸자 몸이 하늘색으로 빛나고 빛이 끝나고 스킬이 시전되게") it glows
        // first: its user glows sky blue half a second (CutsceneGlow) and G is cast as the glow ends (ultimateGlowed)
        return ultimateMay(p)&&dev.sangwoo.celestialfall.combat.CutsceneGlow.begin(p,dev.sangwoo.celestialfall.combat.CutsceneGlow.ISOSANG_G);
    }
    /** Whether G may be begun now (its glow at its key, its cast as the glow ends). */
    private static boolean ultimateMay(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(casting(s,now)||now<s.gReady&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        return !(holding(s.combo,s.start,now)||sweepHolds(s,now)||forming(s,now));
    }
    /**
     * G's glow was cut short (2.109.0, CutsceneGlow: a blow landed on its user as they glowed, "끊기고 쿨타임도 다시
     * 흐름"): its cooldown runs in full from now (none for a player the cooldowns are off for), its user's client told.
     */
    public static void ultimateGlowCut(ServerPlayerEntity p){
        var s=state(p);long now=p.getWorld().getTime();
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.gReady=Math.max(s.gReady,now+G_COOLDOWN);
        send(p,s.id,s.combo,s.start,s.yaw,RESTS,-1,null,null,null);
    }
    /** G once its glow is over (2.109.0, CutsceneGlow): cast as at its key before, if it still may be. */
    public static boolean ultimateGlowed(ServerPlayerEntity p){
        if(!ultimateMay(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        s.queued=false;s.rQueued=false;
        boolean spent=s.empowered;
        s.combo=0;s.blows=0;s.empowered=false;
        if(spent)spend(p,s,now);
        var world=p.getServerWorld();Vec3d at=p.getPos();float yaw=MathHelper.wrapDegrees(p.getYaw());
        // where its sword strikes: G_AHEAD blocks ahead the way they face, on the first thing under the sky there
        Vec3d over=at.add(Vec3d.fromPolar(0,yaw).multiply(G_AHEAD));double y=at.y;
        var down=world.raycast(new RaycastContext(new Vec3d(over.x,at.y+24,over.z),new Vec3d(over.x,at.y-24,over.z),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(down.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)y=down.getPos().y;
        int variant=riggedVariant>=0?Math.min(riggedVariant,G_VARIANTS-1):p.getRandom().nextInt(G_VARIANTS);riggedVariant=-1;
        s.ult=new Ultimate(UUID.randomUUID(),now,at,yaw,variant,new Vec3d(over.x,y,over.z));
        s.gReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?0:now+HOLD[ULTIMATE]+G_COOLDOWN;
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s.ult.id,ULTIMATE,now,yaw,HIT,-1,null,null,null,null,s.ult);
        return true;
    }
    /**
     * G on a tick (2.83.0): its sword strikes as G_IMPACT comes (everyone within G_RANGE of where its user stands, alive,
     * they aside, takes its damage, what they were doing stopping); it holds its user until its cutscene is over. Its user
     * gone from the map, dead or Lee So-sang no more before it strikes calls it off: it does not strike.
     */
    private static void cast(ServerPlayerEntity p,State s,long now){
        var u=s.ult;long age=now-u.start;
        if(u.over){s.ult=null;return;}
        if(!u.struck&&(!attacks(p)||!p.isAlive())){call(p,s,now);s.ult=null;return;}
        // (the user's 2.87.0 preview) it strikes as the cutscene ends (4.85 s)
        if(!u.struck&&age>=GExplosionRules.HIT_TICK){u.struck=true;strike(p,u);}
        if(age>=HOLD[ULTIMATE]){s.ult=null;return;}
        p.fallDistance=0;
    }
    /** G is called off before its sword strikes (2.83.0): every viewer is told (its sword goes), and G is ready again its cooldown after now. */
    private static void call(ServerPlayerEntity p,State s,long now){
        var u=s.ult;if(u==null||u.over)return;u.over=true;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.gReady=Math.min(s.gReady,now+G_COOLDOWN);
        send(p,u.id,ULTIMATE,u.start,u.yaw,STOPPED,-1,null,null,null,null,u);
    }
    /**
     * G's sword strikes (the user's 2.87.0 preview, GExplosionDamage): everyone in 62 × 47 blocks round where it strikes,
     * or in the garden's arena everyone in the arena, by how far from it (2.87.1: 200 within 5 blocks, 100 within 15, 50
     * within 30, 20 further on, GExplosionRules); its user aside.
     */
    private static void strike(ServerPlayerEntity p,Ultimate u){
        List<LivingEntity> struck=GExplosionDamage.apply(p,u.strike,u.yaw,u.id);
        if(!struck.isEmpty())p.onAttacking(struck.get(0));
        send(p,u.id,ULTIMATE,u.start,u.yaw,LANDED,0,struck,null,null,null,u);
    }
    /** The formation ends early (2.82.0): every viewer is told (its swords go now), and H is ready again its cooldown after now. */
    private static void end(ServerPlayerEntity p,State s,long now){
        var f=s.form;if(f==null)return;
        // (2.96.0) T on the way ends with it, its cooldown from now
        if(!s.lunges.isEmpty())halt(p,s,now);
        s.tQueued=null;s.form=null;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.hReady=Math.min(s.hReady,now+H_COOLDOWN);
        send(p,f.id,FORMATION,f.start,f.yaw,STOPPED,-1,null,null,null,f);
        gauge(p,s);
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
            // (2.95.0) and H's basic attack's swords, so long as their user is here and Lee So-sang's
            if(s.volley!=null)shower(p,s,now);
            // (2.96.0) and T's blows, so long as their formation stands
            if(!s.lunges.isEmpty())rush(p,s,now);
            // (2.97.0) and Y's swords and its cage
            if(s.cage!=null)trap(p,s,now);
            // (2.101.0) and H again (nothing harms her while it holds her; only what takes her away calls it off)
            if(s.finale!=null)conclude(p,s,now);
            // (2.96.4) a press of T kept goes as the one before lets go of her (if it may then)
            if(s.tQueued!=null&&!lungeHolds(s,now)){var q=s.tQueued;s.tQueued=null;lunge(p,q.aimed(),q.foe(),q.meets());}
            // (2.80.0) dead, or off the map: an empowerment held is spent (a character changed forgets it, StandManager)
            if(s.charged&&!attacks(p)&&"isosang".equals(dev.sangwoo.celestialfall.progression.ProgressManager.profile(p).stand())){s.empowered=false;spend(p,s,now);}
            // (2.113.0) and H's gauge is emptied (dead, off the map, in their vanilla mode)
            if(s.gauge>0&&!attacks(p)){s.gauge=0;gauge(p,s);}
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
        // (2.95.0: so does H's basic attack's every sword, coming down on whom it was sent at)
        // (2.96.0: and T's every blow: the dash through, the swords and the slashes on whom it was sent at)
        // (2.97.0: and Y's swords, coming down round whom they cage)
        // (2.101.0: and H again's, coming down into the band ahead of her)
        boolean thrown=combo==THROWS||combo==THRUST||combo==SWEEP||combo==VOLLEY||combo==LUNGE||combo==CAGE||combo==FINALE;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand((combo==THROWS||combo==VOLLEY||combo==LUNGE||combo==CAGE?2:combo==SWEEP?SWEEP_PIVOT_FW+SWEEP_REACH
            :combo==FINALE?Math.hypot((FINALE_REACH-FINALE_NEAR)/2,FINALE_HALF):MAX_REACH)+3),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
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
            // (2.97.0: nor Y's: whom it strikes it cages, IsosangRules.CAGE_FREE, trap)
            // (2.101.0: nor H again's)
            if(combo!=SWEEP&&combo!=CAGE&&combo!=FINALE&&(!KagenoManager.frozen(e)||StarPlatinumTimeStop.stopped(e)))HitStun.stagger(p,e,combo,Stagger.isosang(combo,i,start,now,empowered));
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
        // (2.113.0) a basic attack's hit fills H's gauge, once for each foe it strikes (the empowered ones too)
        if(valid(combo)&&!struck.isEmpty())fill(p,start,struck);
        return struck;
    }
    /**
     * H's gauge fills (2.113.0, IsosangRules.GAUGE_FULL): the basic attack's hit begun at {@code start} struck these; each
     * foe it had not struck yet fills one (the user's choice: "맞은 적 수만큼"; however many of its blows reach one, once).
     * Not on H (it only drains then; its basic attack there is the volley anyway).
     */
    private static void fill(ServerPlayerEntity p,long start,List<LivingEntity> struck){
        var s=STATES.get(p.getUuid());if(s==null||s.form!=null||!attacks(p))return;
        if(s.gaugeHit!=start){s.gaugeHit=start;s.gaugeFoes.clear();}
        int foes=0;for(var e:struck)if(s.gaugeFoes.add(e.getUuid()))foes++;
        int was=s.gauge;s.gauge=IsosangRules.gaugeFilled(was,foes);
        if(s.gauge!=was)gauge(p,s);
    }
    /**
     * Whoever is in the formation walks slower while it stands (2.113.0, IsosangRules.H_ZONE_SLOW): every foe in it (as its
     * blows find them: alive, the user's own aside, not a merchant, PvP's rule), held a little past this tick so it lifts
     * as they step out or it ends. It does not stop what they do (it is no incapacitation).
     */
    private static void slowZone(ServerPlayerEntity p,Formation f){
        var world=p.getServerWorld();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(f.centre,f.centre).expand(FORMATION_RADIUS+2,FORMATION_HIGH+2,FORMATION_RADIUS+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            Vec3d d=e.getPos().subtract(f.centre);
            if(formationOver(d.x,d.z,d.y,e.getWidth()*.5))CombatManager.slow(e,H_ZONE_HOLD,H_ZONE_SLOW);
        }
    }
    /**
     * Tells this player of their H's gauge (2.113.0, GAUGE): how full it is, and on H the tick its formation began and the
     * ticks into it its gauge runs dry and its swords flare (its HUD drains it from them).
     */
    private static void gauge(ServerPlayerEntity p,State s){
        if(!ServerPlayNetworking.canSend(p,GAUGE))return;
        var f=s.form;var b=PacketByteBufs.create();
        b.writeByte(s.gauge);b.writeLong(p.getWorld().getTime());
        b.writeLong(f==null?-1:f.start);b.writeDouble(f==null?0:f.emptyAge);b.writeDouble(f==null?0:f.endAge);
        ServerPlayNetworking.send(p,GAUGE,b);
    }
    /**
     * (2.113.0) Sets this player's H gauge to {@code units} (0 to IsosangRules.GAUGE_FULL), not while they are on H (it
     * drains then): /botand gauge (OP), for trying H out and for the checks and films. Returns whether it was set.
     */
    public static boolean setGauge(ServerPlayerEntity p,int units){
        if(!attacks(p))return false;
        var s=state(p);if(s.form!=null&&forming(s,p.getWorld().getTime()))return false;
        s.gauge=Math.max(0,Math.min(IsosangRules.GAUGE_FULL,units));gauge(p,s);return true;
    }
    /** (2.113.0) /botand gauge &lt;targets&gt; [units] (OP): sets Lee So-sang's H gauge (full with no count). */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.server.command.ServerCommandSource> commands(){
        return net.minecraft.server.command.CommandManager.literal("gauge").requires(s->s.hasPermissionLevel(2))
            .then(net.minecraft.server.command.CommandManager.argument("targets",net.minecraft.command.argument.EntityArgumentType.players())
                .executes(c->setGauges(c.getSource(),net.minecraft.command.argument.EntityArgumentType.getPlayers(c,"targets"),IsosangRules.GAUGE_FULL))
                .then(net.minecraft.server.command.CommandManager.argument("units",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,IsosangRules.GAUGE_FULL))
                    .executes(c->setGauges(c.getSource(),net.minecraft.command.argument.EntityArgumentType.getPlayers(c,"targets"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"units")))));
    }
    private static int setGauges(net.minecraft.server.command.ServerCommandSource source,java.util.Collection<ServerPlayerEntity> players,int units){
        int n=0;for(var p:players)if(setGauge(p,units))n++;
        int count=n;source.sendFeedback(()->net.minecraft.text.Text.translatable("message.celestialfall.gauge",count,units,IsosangRules.GAUGE_FULL),true);
        return n;
    }
    /** (2.113.0) How full this player's H gauge is (0 to IsosangRules.GAUGE_FULL; the checks read it). */
    public static int gauge(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.gauge;}
    /** (2.113.0) Their formation's gauge on H as the checks read it: the ticks into it it runs dry and its swords flare (null for none). */
    public static double[] formationGauge(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());var f=s==null?null:s.form;return f==null?null:new double[]{f.emptyAge,f.endAge};}
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
        send(p,id,combo,start,yaw,type,blow,struck,sword,sweep,form,ult,null);
    }
    private static void send(ServerPlayerEntity p,UUID id,int combo,long start,float yaw,int type,int blow,List<LivingEntity> struck,Sword sword,Sweep sweep,Formation form,Ultimate ult,Volley volley){
        send(p,id,combo,start,yaw,type,blow,struck,sword,sweep,form,ult,volley,null);
    }
    private static void send(ServerPlayerEntity p,UUID id,int combo,long start,float yaw,int type,int blow,List<LivingEntity> struck,Sword sword,Sweep sweep,Formation form,Ultimate ult,Volley volley,Lunge lunge){
        send(p,id,combo,start,yaw,type,blow,struck,sword,sweep,form,ult,volley,lunge,null);
    }
    private static void send(ServerPlayerEntity p,UUID id,int combo,long start,float yaw,int type,int blow,List<LivingEntity> struck,Sword sword,Sweep sweep,Formation form,Ultimate ult,Volley volley,Lunge lunge,Cage cage){
        send(p,id,combo,start,yaw,type,blow,struck,sword,sweep,form,ult,volley,lunge,cage,null);
    }
    private static void send(ServerPlayerEntity p,UUID id,int combo,long start,float yaw,int type,int blow,List<LivingEntity> struck,Sword sword,Sweep sweep,Formation form,Ultimate ult,Volley volley,Lunge lunge,Cage cage,Finale finale){
        GArenaServer.event(p,id,combo,start,type);
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
            // its sword strikes the ground
            b.writeLong(s.gReady);b.writeBoolean(ult!=null);
            if(ult!=null){b.writeByte(ult.variant);b.writeDouble(ult.strike.x);b.writeDouble(ult.strike.y);b.writeDouble(ult.strike.z);}
            // (2.93.0, after what 2.92 sent) Z: when it is ready again, its window's first move and how many it has had; and
            // for H's formation the sword its user stands on, the one they flew from (255 for none) and the tick they left it
            b.writeLong(s.z.ready());b.writeLong(s.z.first());b.writeByte(s.z.uses());b.writeBoolean(form!=null);
            if(form!=null){b.writeByte(form.on);b.writeByte(form.from<0?255:form.from);b.writeLong(form.moved);}
            // (2.95.0, after what 2.94 sent) when H's basic attack is ready again, and for it the foe it comes down on (-1 for
            // none) and where (the foe's chest as it began, else where the crosshair met)
            b.writeLong(s.vReady);b.writeBoolean(volley!=null);
            if(volley!=null){b.writeVarInt(volley.target);b.writeDouble(volley.point.x);b.writeDouble(volley.point.y);b.writeDouble(volley.point.z);}
            // (2.96.0, after what 2.95 sent) when T is ready again, and for T the sword she stood on, the one she flies on
            // to and where she lands in front of the foe (its foe and where it comes down are the basic attack's, above)
            b.writeLong(s.tReady);b.writeBoolean(lunge!=null);
            if(lunge!=null){b.writeByte(lunge.from);b.writeByte(lunge.to);b.writeDouble(lunge.past.x);b.writeDouble(lunge.past.y);b.writeDouble(lunge.past.z);}
            // (2.97.0, after what 2.96 sent) when Y is ready again, and for Y whether its ring is set and where its middle
            // stands on the ground now (its foe and the point it began on are the basic attack's, above)
            b.writeLong(s.yReady);b.writeBoolean(cage!=null);
            if(cage!=null){b.writeBoolean(cage.planted);b.writeDouble(cage.at.x);b.writeDouble(cage.at.y);b.writeDouble(cage.at.z);}
            // (2.101.0, after what 2.97 to 2.100 sent) H again: the formation it ends (its id and start, its middle and its
            // user's way, the sword she stood on), which recording it plays, and where she stands (where she comes down)
            b.writeBoolean(finale!=null);
            if(finale!=null){
                b.writeUuid(finale.form);b.writeLong(finale.formStart);b.writeDouble(finale.centre.x);b.writeDouble(finale.centre.y);b.writeDouble(finale.centre.z);
                b.writeFloat(finale.formYaw);b.writeByte(finale.on);b.writeByte(finale.variant);b.writeDouble(finale.spot.x);b.writeDouble(finale.spot.y);b.writeDouble(finale.spot.z);
            }
            // (2.113.0, after what 2.101 to 2.112 sent) for H's formation the ticks into it its swords flare (its end: as its
            // gauge runs dry; every viewer brings its ending then)
            b.writeBoolean(form!=null);
            if(form!=null)b.writeDouble(form.endAge);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
