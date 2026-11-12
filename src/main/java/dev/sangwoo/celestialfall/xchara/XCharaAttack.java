package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.combat.Stagger;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
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
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * X Chara's basic attack, their knife's four swings (2.114.0; XCharaCombo for the timing and the reach). The attack key
 * on their client asks for a hit (xchara_attack_v1, no payload; a vanilla attack is taken the same way) and the server
 * decides, as for Lee So-sang's sword (IsosangAttack): the next hit of the combo starts now, or when it may if that is
 * near (a press made a little early is kept), or not at all while the attack rests. The swings and their rules are the
 * same with their hack on or off (2.118.0); a hit is told as one begun with the hack on (Q, XCharaHack: XCharaCombo's
 * hits 1 to 4, which the clients draw with a purple cut and what it leaves on whom it strikes) or with it off (hits 5
 * to 8, 2.117.0: the body and the knife only), and the combo goes on through either. Each hit goes the way its user
 * faces as it starts and holds them where they stand a moment (XCharaCombo.HOLD: no walking, guard, dash or other
 * move, CombatManager.busy); at its blow it strikes everyone in its way (XCharaCombo.lane, in sight) without knockback
 * and staggers them until the next blow could land (Stagger.xchara). The fourth, the lunge, takes no guard and sends
 * whom it strikes flying the way the hit went. Every viewer is told when a hit starts, whom its blow struck, and when
 * the attack rests or the hit stops short (xchara_attack_event_v1); the clients draw the body's motion and the cuts
 * (client/xchara/KnifeClient, KnifeCombo). A hit stops short when its user is struck, staggered, incapacitated, held in a
 * cutscene (their own G too), or is X Chara's no more; the attack then rests as after a broken combo.
 * <p>
 * Its sound is the clients' (KnifeClient: the one sound of every swing, the user's own, 2.118.0); the server plays none.
 */
public final class XCharaAttack {
    /** The attack key (no payload); and every viewer's news. */
    public static final Identifier INPUT=CelestialFall.id("xchara_attack_v1"),EVENT=CelestialFall.id("xchara_attack_event_v1");
    /** A hit starts; one of its blows lands (whom it struck); the attack rests until it is ready again; the hit in flight stops short. */
    public static final int HIT=0,LANDED=1,RESTS=2,STOPPED=3;
    /** At most this many presses a second are heard. */
    static final int RATE=40;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** The hit in flight or the last one (1 to 8: the four begun with the hack on, the plain four; 0 for none), its id, when it began, the way it goes (degrees), and how many of its blows have landed. */
        int combo;UUID id=new UUID(0,0);long start=-1000;float yaw;int blows;
        /** A press kept for the next hit, and the tick the attack is ready again after a rest (0 for none). */
        boolean queued;long ready;
        int window,presses;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private XCharaAttack(){}

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
        ServerTickEvents.END_SERVER_TICK.register(XCharaAttack::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->STATES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    /** Whether this player's attack is X Chara's knife: theirs, alive, on the map. */
    public static boolean attacks(PlayerEntity p){return p!=null&&StandManager.xChara(p);}
    /** Free to start a hit: nothing else holds them (a blow, a guard, a dash, a skill, a cutscene); a hit's own hold aside. */
    public static boolean able(ServerPlayerEntity p){return attacks(p)&&!p.isSleeping()&&!p.hasVehicle()&&!CombatManager.busyOtherwise(p)&&!CombatManager.cinematic(p)&&!XCharaHeal.busy(p);}
    /** Whether one of this player's hits holds them now (XCharaCombo.HOLD): CombatManager.busy and actionLocked count it. */
    public static boolean busy(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s!=null&&s.world==p.getWorld()&&holding(s.combo,s.start,p.getWorld().getTime());
    }
    /** This player's hit as a boss reads it: which, begun at {@code start}, the way {@code yaw}, {@code blows} of its blows landed. */
    public record Hit(int combo,long start,float yaw,int blows){}
    /** This player's hit now (null if they have swung none). */
    public static Hit hit(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?null:new Hit(s.combo,s.start,s.yaw,s.blows);}
    /** The tick this player's attack is ready again after its rest (0 if it is not resting). */
    public static long ready(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player is X Chara's no more: the attack's rest is gone, so it is ready if they are again (StandManager.forgetOthers). */
    public static void forget(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)s.ready=0;}
    /** The attack's rest is gone (a player the cooldowns are off for, /botand nocooldown). */
    public static void cool(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)s.ready=0;}
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
            // (the fourth hit's motion runs on after its blows; the next combo waits for its rest, set as it began)
            if(!last(s.combo)){if(may-now<=BUFFER)s.queued=true;return s.queued;}
            return false;
        }
        begin(p,s,now);
        return true;
    }
    /** When the next hit may begin: at once with none in flight; the next of the combo its STEP after the hit before; the next combo once the fourth's motion is over (and its rest, s.ready). */
    private static long may(State s){
        if(!valid(s.combo))return Long.MIN_VALUE;
        return last(s.combo)?s.start+LENGTH[s.combo]:nextAt(s.combo,s.start);
    }
    /** The combo broke (the next hit is late): the attack rests and starts again from the first hit; a rest over ends the combo. */
    private static void settle(ServerPlayerEntity p,State s,long now){
        long rest=broken(s.combo,s.start,now);
        if(rest>=0){
            s.combo=0;s.queued=false;
            // (none for a player the cooldowns are off for, /botand nocooldown: the list shows no rest for them)
            if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.ready=Math.max(s.ready,rest);
            send(p,s,RESTS,-1,null);
        }
        if(last(s.combo)&&now>=s.ready&&now>=s.start+LENGTH[s.combo])s.combo=0;
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        // the hack off as it begins, a plain swing (2.117.0); on, the same swing drawn with its purple cut (2.118.0): the
        // combo goes on from the one to the other
        s.combo=next(s.combo,!XCharaHack.on(p));s.id=UUID.randomUUID();s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=false;s.blows=0;
        // (the rest after the fourth hit: none for a player the cooldowns are off for, /botand nocooldown)
        if(last(s.combo))s.ready=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?now:rested(s.combo,now);
        p.clearActiveItem();p.setSprinting(false);p.swingHand(Hand.MAIN_HAND,true);CombatManager.interrupt(p);
        send(p,s,HIT,-1,null);
    }
    /** Whatever ends the hit in flight: its user struck, held, or no longer able to fight with the knife. */
    private static boolean held(ServerPlayerEntity p){
        return !attacks(p)||p.isSleeping()||p.hasVehicle()||HitStun.stunned(p)||CombatManager.incapacitated(p)||CombatManager.cinematic(p)||KagenoManager.frozen(p);
    }
    /** The user was struck (staggered or incapacitated, CombatManager): the hit in flight stops short. */
    public static void struck(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());}
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            if(held(p)){stop(p,s,now);continue;}
            settle(p,s,now);
            if(s.queued&&now>=may(s)){if(able(p))begin(p,s,now);else s.queued=false;}
            if(!valid(s.combo))continue;
            while(s.blows<blows(s.combo)&&now>=s.start+blowAt(s.combo,s.blows))blow(p,s,now);
        }
    }
    /** The hit in flight stops short (or the combo is cut off between hits): the attack rests from now. */
    private static void stop(ServerPlayerEntity p,State s,long now){
        boolean moving=valid(s.combo)&&now-s.start<LENGTH[s.combo],open=valid(s.combo)&&!last(s.combo)&&broken(s.combo,s.start,now)<0;
        if(!moving&&!open&&!s.queued)return;
        s.queued=false;s.combo=0;s.blows=0;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.ready=Math.max(s.ready,now+REST);
        send(p,s,STOPPED,-1,null);
    }
    /**
     * The hit's blow lands: everyone in its way in front of its user, in sight, is struck without knockback and staggered
     * until the next blow could land; the fourth takes no guard and sends them flying.
     */
    private static void blow(ServerPlayerEntity p,State s,long now){
        int combo=s.combo,i=s.blows++;
        var world=p.getServerWorld();var source=world.getDamageSources().playerAttack(p);float amount=damage(combo,i);
        Vec3d f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x),at=p.getPos();
        List<LivingEntity> struck=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(MAX_REACH+3),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            if(!lane(combo,i,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            if(unblockable(combo))CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,amount))continue;
            struck.add(e);
            // every blow staggers whom it strikes a moment (2.68.0's rule for basic attacks); one stopped in Star Platinum's
            // stopped time is staggered as time moves again
            if(!KagenoManager.frozen(e)||StarPlatinumTimeStop.stopped(e))HitStun.stagger(p,e,combo,Stagger.xchara(combo,i,s.start,now));
            // the finisher's last cut throws them the way it went, told to a struck player's client once, through the
            // tracker; one stopped in a time stop is thrown as time moves again
            if(launches(combo,i)){
                Vec3d fly=f.multiply(FLY).add(0,FLY_UP,0);
                if(!StarPlatinumTimeStop.deferLaunch(e,fly)){e.setVelocity(fly);e.velocityModified=true;}
            }
            // the user's own hit feedback (the damage number and the blood), as the other characters'
            if(!StarPlatinumTimeStop.deferFeedback(p,e,amount,s.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(amount);b.writeFloat(s.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
        }
        if(!struck.isEmpty()){
            p.onAttacking(struck.get(0));p.addExhaustion(.1f);
        }
        send(p,s,LANDED,i,struck);
    }
    /**
     * Tells every viewer near the user (and the user) of the event: whose, the kind, the hit's id, which hit and which
     * blow, when the hit began and the server's time now, where its user stands and the way it goes, when the attack is
     * ready again, and whom the blow struck (each with where they stand).
     */
    private static void send(ServerPlayerEntity p,State s,int type,int blow,List<LivingEntity> struck){
        var world=p.getServerWorld();long now=world.getTime();
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);b.writeUuid(s.id);b.writeByte(s.combo);b.writeByte(blow);
            b.writeLong(s.start);b.writeLong(now);b.writeDouble(p.getX());b.writeDouble(p.getY());b.writeDouble(p.getZ());b.writeFloat(s.yaw);b.writeLong(s.ready);
            b.writeVarInt(struck==null?0:struck.size());
            if(struck!=null)for(var e:struck){b.writeUuid(e.getUuid());b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY());b.writeDouble(e.getZ());}
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
