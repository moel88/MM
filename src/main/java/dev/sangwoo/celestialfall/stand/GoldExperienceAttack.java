package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.combat.Stagger;
import dev.sangwoo.celestialfall.map.MapGate;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;

/**
 * Gold Experience's punches, the attack of its user while it is out (2.154.0; GoldExperienceRules has the user's words
 * and the numbers): Star Platinum's four (StarPlatinumAttack), a little slower and weaker. The attack key on the user's
 * client asks for a punch (gold_attack_v1, no payload; a vanilla attack is taken the same way) and the server decides:
 * the next punch of the combo starts now, or when it may if that is near (a press made a little early is kept), or not
 * at all while the attack rests. Each punch goes the way its user faces as it starts; at its contact it strikes everyone
 * in its way in front of its user (GoldExperienceRules.m1Lane, in sight, the PvP rule): the first three a guard takes,
 * and they stagger whom they strike (Stagger.gold); the fourth no guard takes, and it throws them along it. Every viewer
 * is told when a punch starts (with how far ahead the nearest one in its way stands, for where the stand goes to strike),
 * whom it struck and whose guard took it, and when the attack rests or stops short (gold_attack_event_v1); the clients
 * draw the stand's punches and play their sounds (client/stand/GoldCombo). The user walks and runs while the stand
 * punches. A punch stops short when its user is struck (StarPlatinumAttack.struck tells this one too), sent back or can
 * no longer have the stand out, when the rush begins (it takes over once the stand is back in its guard, as Star
 * Platinum's does) or when another of its skills does (the overdrive, the healing, the beatdown, giving life, the tree:
 * each takes over from it at once); the attack then rests as after a broken combo. No punch starts while one of those
 * is on, nor while the rush's fists are going back to the guard.
 */
public final class GoldExperienceAttack {
    public static final Identifier INPUT=CelestialFall.id("gold_attack_v1"),EVENT=CelestialFall.id("gold_attack_event_v1");
    /** A punch starts; it lands (whom it struck, whose guard took it); the attack rests until it is ready again; the punch in flight stops short. */
    public static final int PUNCH=0,LANDED=1,RESTS=2,STOPPED=3;
    /** At most this many presses a second are heard. */
    static final int RATE=40;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** The punch in flight or the last one (1 to 4, 0 for none), when it began and the way it goes (degrees). */
        int combo;long start=-1000;float yaw;
        /** A press kept for the next punch; the punch in flight has landed. */
        boolean queued,landed;
        /** The tick the attack is ready again after a rest (0 for none), and the tick a punch in flight last stopped short. */
        long ready,stoppedAt=-1000;
        int window,presses;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private GoldExperienceAttack(){}

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
        ServerTickEvents.END_SERVER_TICK.register(GoldExperienceAttack::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->STATES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    /** Whether this player's attack is Gold Experience's punches: its user with it out, alive, on the map. */
    public static boolean punches(PlayerEntity p){
        return p!=null&&MapGate.enabled(p.getWorld())&&StandManager.goldExperience(p)&&((StandHolder)p).botand$isStandOut()&&p.isAlive()&&!p.isSpectator();
    }
    /** Whether another of the stand's skills is on (its punch, its hand or its knee): no punch starts over it. */
    private static boolean other(ServerPlayerEntity p){
        return GoldExperienceOverdrive.punching(p)||GoldExperienceHeal.active(p)||GoldExperienceBeatdown.opening(p)||GoldExperienceLife.punching(p)||GoldExperienceTree.holding(p);
    }
    /** Free to start a punch: nothing else holds the user (a blow, a guard, a dash, a skill, a cutscene). */
    public static boolean able(ServerPlayerEntity p){
        return punches(p)&&!p.isSleeping()&&!p.hasVehicle()&&!CombatManager.busy(p)&&!CombatManager.cinematic(p)&&!other(p);
    }
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){s=new State(p);STATES.put(p.getUuid(),s);}
        return s;
    }

    /** The user asks for a punch. Returns whether one starts now or is kept for when it may. */
    public static boolean request(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(now<s.ready)return false;
        if(s.combo>=1&&s.combo<4&&now<m1NextAt(s.combo,s.start)){
            if(m1NextAt(s.combo,s.start)-now<=M1_BUFFER)s.queued=true;
            return s.queued;
        }
        // the rush just over: its fists are going back to the guard, and the punch starts once they are there
        long free=StarPlatinumRush.freeAt(p);
        if(now<free){if(free-now<=M1_BUFFER)s.queued=true;return s.queued;}
        begin(p,s,now);
        return true;
    }
    /**
     * What this player's stand is punching now, as a boss reads it (BossThreats): the punch (1 to 4, 0 for none), when it
     * began, the way it goes, whether it has landed, and the tick the attack is ready again after a rest.
     */
    public record Punch(int combo,long start,float yaw,boolean landed,long ready){}
    /** This player's punch now (null if they have thrown none). */
    public static Punch punch(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?null:new Punch(s.combo,s.start,s.yaw,s.landed,s.ready);}
    /** Whether this player's stand is throwing a punch now (one that has not landed yet, or is still out at full stretch). */
    public static boolean punching(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s!=null&&s.world==p.getWorld()&&s.combo>=1&&p.getWorld().getTime()-s.start<M1_LENGTH[s.combo];
    }
    /** The tick this player's attack is ready again after its rest (0 if it has not rested). */
    public static long ready(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player's stand is not Gold Experience (StandManager): the punches' rest is gone. */
    public static void forget(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)s.ready=0;}
    /** The user was struck: the punch in flight stops short (StarPlatinumAttack.struck tells this too, for every blow that stops a stand's punch). */
    public static void struck(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());}
    /** The rush or another of the stand's skills begins: the punch in flight, if one is, stops short (and the combo starts again after its rest). */
    public static void yieldTo(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());}
    /** The tick the stand is back in its guard after the punch it last stopped short (StarPlatinumRules.STOP_RETURN; 0 if none has stopped). */
    public static long guardAt(ServerPlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.stoppedAt+StarPlatinumRules.STOP_RETURN;}

    /** The combo broke (the next punch is late): the attack rests and starts again from the first punch. */
    private static void settle(ServerPlayerEntity p,State s,long now){
        long rest=m1Broken(s.combo,s.start,now);
        if(rest>=0){s.combo=0;s.queued=false;s.ready=Math.max(s.ready,rest);send(p,s,RESTS,null,null);}
        if(s.combo==4&&now>=s.ready)s.combo=0;
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        s.combo=m1Next(s.combo);s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=s.landed=false;
        // after the fourth it rests once it is over (none for a player the cooldowns are off for, /botand nocooldown)
        if(s.combo==4)s.ready=NoCooldownState.enabled(p)?now+M1_LENGTH[4]:m1Rested(now);
        send(p,s,PUNCH,null,null);
    }
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            if(!punches(p)||HitStun.stunned(p)||CombatManager.actionLocked(p)){stop(p,s,now);continue;}
            settle(p,s,now);
            if(s.queued&&now>=m1NextAt(s.combo,s.start)&&now>=StarPlatinumRush.freeAt(p)){if(able(p))begin(p,s,now);else s.queued=false;}
            if(s.combo>=1&&!s.landed&&now>=s.start+M1_CONTACT[s.combo])strike(p,s,now);
        }
    }
    /** The punch in flight stops short (or the combo is cut off between punches): the attack rests from now. */
    private static void stop(ServerPlayerEntity p,State s,long now){
        boolean flying=s.combo>=1&&now-s.start<M1_LENGTH[s.combo],open=s.combo>=1&&s.combo<4&&m1Broken(s.combo,s.start,now)<0;
        if(!flying&&!open&&!s.queued)return;
        if(flying)s.stoppedAt=now;
        s.queued=false;s.ready=Math.max(s.ready,NoCooldownState.enabled(p)?now:now+M1_REST);s.combo=0;
        send(p,s,STOPPED,null,null);
    }
    /**
     * The punch lands: everyone in its way in front of its user, in sight, is struck. The first three a guard takes, and
     * they stagger whom they strike; the fourth cannot be guarded and throws them along it.
     */
    private static void strike(ServerPlayerEntity p,State s,long now){
        s.landed=true;
        var world=p.getServerWorld();int combo=s.combo;boolean big=combo==4;
        Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw);
        var source=world.getDamageSources().playerAttack(p);
        List<LivingEntity> struck=new ArrayList<>(),guarded=new ArrayList<>();
        for(var e:way(p,s)){
            // a guard the first three meet takes them (the clients draw that blow blue); the fourth's no guard takes:
            // it drops the guard, and what they were doing, as it lands
            boolean onGuard=!big&&CombatManager.guardTakes(e,at,false);
            if(big)CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,M1_DAMAGE[combo])){if(onGuard)guarded.add(e);continue;}
            struck.add(e);
            if(big){
                HitStun.release(e.getUuid());
                // the fourth staggers them a moment as it throws them (the basic attack's Stagger)
                HitStun.stagger(p,e,combo,Stagger.gold(combo,s.start,now));
                Vec3d fly=f.multiply(M1_FLY).add(0,M1_FLY_UP,0);
                // one stopped in a time stop is thrown as time moves again, with whatever else came to them
                if(!StarPlatinumTimeStop.deferLaunch(e,fly)){
                    e.setVelocity(fly);e.velocityModified=true;
                    // (as Star Platinum's finisher: the throw told to a struck player's client at once, a tick before the tracker would)
                    if(e instanceof ServerPlayerEntity other)other.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(other));
                }
            }else HitStun.stagger(p,e,combo,Stagger.gold(combo,s.start,now));
            // the user's own hit feedback (the damage number and the blood), as Star Platinum's
            if(!StarPlatinumTimeStop.deferFeedback(p,e,M1_DAMAGE[combo],s.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(M1_DAMAGE[combo]);b.writeFloat(s.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);}
        }
        // (the punches' own sounds carry their blows: GoldCombo plays them on every client, the game's attack sounds not)
        if(!struck.isEmpty())p.onAttacking(struck.get(0));
        send(p,s,LANDED,struck,guarded);
    }
    /** Everyone in the punch's way in front of its user now, in sight, whom its user may strike (the PvP rule). */
    private static List<LivingEntity> way(ServerPlayerEntity p,State s){
        var world=p.getServerWorld();Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        List<LivingEntity> way=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(StarPlatinumRules.REACH_BIG+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            if(!m1Lane(s.combo,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            way.add(e);
        }
        return way;
    }
    /**
     * Tells every player near the user (and the user, and whoever was struck) of the event: the punch, when it began and
     * its way, how far ahead the nearest one in its way stands (as it starts), when the attack is ready again, whom it
     * struck (and where they stood) and whose guard took it (as it lands).
     */
    private static void send(ServerPlayerEntity p,State s,int type,List<LivingEntity> struck,List<LivingEntity> guarded){
        var world=p.getServerWorld();long now=world.getTime();
        float aim=type==PUNCH?aim(p,s):Float.NaN;
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128&&(struck==null||!struck.contains(viewer)))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);b.writeByte(s.combo);
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(s.yaw);b.writeFloat(aim);b.writeLong(s.ready);
            int n=struck==null?0:struck.size();b.writeVarInt(n);
            for(int i=0;i<n;i++){var e=struck.get(i);b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY()+e.getHeight()*.6);b.writeDouble(e.getZ());}
            int m=guarded==null?0:guarded.size();b.writeVarInt(m);
            for(int i=0;i<m;i++){var e=guarded.get(i);b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY()+e.getHeight()*.6);b.writeDouble(e.getZ());}
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    /**
     * How far ahead (blocks along the punch) the nearest one in its way stands, to the near side of their body, or NaN:
     * the stand goes as far out in front of its user as it takes for its fist to meet them.
     */
    private static float aim(ServerPlayerEntity p,State s){
        Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw);
        double best=Double.NaN;
        for(var e:way(p,s)){double near=e.getPos().subtract(at).dotProduct(f)-e.getWidth()*.5;if(!(near>=best))best=near;}
        return (float)best;
    }
}
