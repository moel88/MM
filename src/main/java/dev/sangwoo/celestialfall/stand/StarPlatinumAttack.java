package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
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
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;

/**
 * Star Platinum's punches, the attack of its user while it is out (2.54.0; StarPlatinumRules for the timing). The
 * attack key on the user's client asks for a punch (stand_attack_v1, no payload; a vanilla attack is taken the same
 * way) and the server decides: the next punch of the combo starts now, or when it may if that is near (a press held
 * or made a little early is kept), or not at all while the attack rests. Each punch goes the way its user faces as it
 * starts; at its contact it strikes everyone in its way in front of its user (StarPlatinumRules.lane), holds them for
 * the next punch (HitStun.standHold) and the fourth throws them along it. Every viewer is told when a punch starts
 * (with how far ahead the nearest one in its way stands, for where the stand goes to strike), whom it struck, and when
 * the attack rests or stops short (stand_attack_event_v1); the clients draw the stand's punches and play the shouts.
 * The user can walk and run while the stand punches. A punch stops short when its user is struck, sent back, or can no
 * longer have the stand out; the attack then rests as after a broken combo.
 * <p>
 * R is its heavy punch (2.59.0, user request: "이번에는 R 강펀치 맞으면 날아가고 가드 브레이커야"; stand_heavy_v1, no
 * payload): a fifth punch out of the combo (StarPlatinumRules.HEAVY), told to the clients like the others. It takes over
 * from a punch in flight (that one stops short, and the heavy punch starts once the stand is back in its guard from
 * it, STOP_RETURN ticks on), breaks the guard of whoever it strikes and lands all the same, sends
 * them flying along it, and is ready again HEAVY_COOLDOWN ticks after it is over. Every event tells the clients when it
 * is ready again and whose guard it broke.
 */
public final class StarPlatinumAttack {
    public static final Identifier INPUT=CelestialFall.id("stand_attack_v1"),EVENT=CelestialFall.id("stand_attack_event_v1"),HEAVY_INPUT=CelestialFall.id("stand_heavy_v1");
    /** A punch starts; it lands (whom it struck); the attack rests until it is ready again; the punch in flight stops short. */
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
        /** The tick the heavy punch is ready again (2.59.0), and an R kept for when the rush's fists are back in the guard. */
        long heavyReady;boolean heavyQueued;
        int window,presses,heavyWindow,heavyPresses;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private StarPlatinumAttack(){}

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
        ServerPlayNetworking.registerGlobalReceiver(HEAVY_INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.heavyWindow!=second){s.heavyWindow=second;s.heavyPresses=0;}
                if(++s.heavyPresses>RATE)return;
                heavy(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(StarPlatinumAttack::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->STATES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    /** Whether this player's attack is Star Platinum's punches: its user with it out, alive, on the map. */
    public static boolean punches(PlayerEntity p){
        return p!=null&&MapGate.enabled(p.getWorld())&&StandManager.starPlatinum(p)&&((StandHolder)p).botand$isStandOut()&&p.isAlive()&&!p.isSpectator();
    }
    /** Free to start a punch: nothing else holds the user (a blow, a guard, a dash, a skill, a cutscene). */
    private static boolean able(ServerPlayerEntity p){return punches(p)&&!p.isSleeping()&&!p.hasVehicle()&&!CombatManager.busy(p)&&!CombatManager.cinematic(p);}
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
        // (the heavy punch too: the next punch may start once it is over)
        if(s.combo>=1&&s.combo!=4&&now<nextAt(s.combo,s.start)){
            if(nextAt(s.combo,s.start)-now<=BUFFER)s.queued=true;
            return s.queued;
        }
        // the rush just over: its fists are going back to the guard, and the punch starts once they are there (2.57.0)
        long free=StarPlatinumRush.freeAt(p);
        if(now<free){if(free-now<=BUFFER)s.queued=true;return s.queued;}
        begin(p,s,now);
        return true;
    }
    /**
     * R: the heavy punch starts now if it may (2.59.0): the stand out and its user free, it ready again and none in
     * flight. A punch in flight stops short and the heavy punch takes over from it; just after the rush it starts once
     * the rush's fists are back in the guard. Returns whether it starts (now or then).
     */
    public static boolean heavy(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(s.combo==HEAVY&&now<s.start+LENGTH[HEAVY])return false;
        if(now<s.heavyReady&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(now<StarPlatinumRush.freeAt(p)){s.heavyQueued=true;return true;}
        beginHeavy(p,s,now);
        return true;
    }
    /** The tick this player's heavy punch is ready again (0 if they have not thrown one). */
    public static long heavyReady(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.heavyReady;}
    /** The tick this player's heavy punch in flight is over (0 if none is): the rush waits for it (StarPlatinumRush). */
    public static long heavyOverAt(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null||s.combo!=HEAVY?0:nextAt(HEAVY,s.start);}
    private static void beginHeavy(ServerPlayerEntity p,State s,long now){
        // a punch in flight (or a combo still open) stops short: the heavy punch starts once the stand has turned back
        // to its guard from it (StarPlatinumRules.STOP_RETURN, as the rush does), so the clients' stand goes from its
        // guard into the coil
        s.heavyQueued=false;stop(p,s,now);
        s.combo=HEAVY;s.start=Math.max(now,s.stoppedAt+STOP_RETURN);s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=s.landed=s.heavyQueued=false;
        // its cooldown runs once it is over (none for a player the cooldowns are off for, /botand nocooldown: ready from
        // now, so the cooldown list shows nothing for it, as for the rush)
        long end=s.start+LENGTH[HEAVY];
        s.heavyReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?now:StarPlatinumRules.heavyReady(end);
        send(p,s,PUNCH,null,null);
    }
    /** The user was struck: whom the punches hold go, and the punch in flight stops short. */
    public static void struck(ServerPlayerEntity p){
        HitStun.releaseStand(p.getUuid());
        var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());
    }
    /** The rush starts (2.57.0): the punch in flight, if one is, stops short (and the combo starts again after its rest). */
    public static void yieldTo(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());}
    /** The tick the stand is back in its guard after the punch it last stopped short (StarPlatinumRules.STOP_RETURN; 0 if none has stopped). */
    public static long guardAt(ServerPlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.stoppedAt+STOP_RETURN;}
    /** The combo broke (the next punch is late): the attack rests and starts again from the first punch. */
    private static void settle(ServerPlayerEntity p,State s,long now){
        long rest=broken(s.combo,s.start,now);
        if(rest>=0){s.combo=0;s.queued=false;s.ready=Math.max(s.ready,rest);send(p,s,RESTS,null,null);}
        if(s.combo==4&&now>=s.ready)s.combo=0;
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        s.combo=next(s.combo);s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=s.landed=false;
        if(s.combo==4)s.ready=rested(now);
        send(p,s,PUNCH,null,null);
    }
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            if(!punches(p)||HitStun.stunned(p)||CombatManager.actionLocked(p)){stop(p,s,now);continue;}
            settle(p,s,now);
            if(s.queued&&now>=nextAt(s.combo,s.start)&&now>=StarPlatinumRush.freeAt(p)){if(able(p))begin(p,s,now);else s.queued=false;}
            if(s.heavyQueued&&now>=StarPlatinumRush.freeAt(p)){s.heavyQueued=false;if(able(p)&&(now>=s.heavyReady||dev.sangwoo.celestialfall.NoCooldownState.enabled(p)))beginHeavy(p,s,now);}
            if(s.combo>=1&&!s.landed&&now>=s.start+CONTACT[s.combo])strike(p,s,now);
        }
    }
    /** The punch in flight stops short (or the combo is cut off between punches): the attack rests from now. */
    private static void stop(ServerPlayerEntity p,State s,long now){
        boolean flying=s.combo>=1&&now-s.start<LENGTH[s.combo],open=s.combo>=1&&s.combo<4&&broken(s.combo,s.start,now)<0;
        if(!flying&&!open&&!s.queued&&!s.heavyQueued)return;
        if(flying)s.stoppedAt=now;
        // a heavy punch stopped short: its cooldown runs from now (2.44.0's rule for a move cut off)
        if(flying&&s.combo==HEAVY)s.heavyReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?now:StarPlatinumRules.heavyReady(now);
        s.queued=s.heavyQueued=false;s.ready=Math.max(s.ready,now+REST);s.combo=0;
        send(p,s,STOPPED,null,null);
    }
    /**
     * The punch lands: everyone in its way in front of its user, in sight, is struck. The first three hold whom they
     * strike for the next punch; the fourth cannot be guarded and throws them along it; the heavy punch (2.59.0) breaks
     * the guard of whoever holds one up and throws them further.
     */
    private static void strike(ServerPlayerEntity p,State s,long now){
        s.landed=true;
        var world=p.getServerWorld();int combo=s.combo;boolean big=combo>=4,heavy=combo==HEAVY;
        Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        var source=world.getDamageSources().playerAttack(p);
        List<LivingEntity> struck=new ArrayList<>(),broke=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(REACH_BIG+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            if(!lane(combo,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&(!world.getServer().isPvpEnabled()||!p.shouldDamagePlayer(other)))continue;
            // the heavy punch breaks a guard held up against it, and lands all the same
            if(heavy&&CombatManager.breakGuard(e,HEAVY_BREAK))broke.add(e);
            if(big)CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,DAMAGE[combo]))continue;
            struck.add(e);
            if(big){
                HitStun.release(e.getUuid());
                e.setVelocity(f.multiply(heavy?FLY_HEAVY:FLY).add(0,heavy?FLY_HEAVY_UP:FLY_UP,0));e.velocityModified=true;
                // the heavy punch tells a struck player's client of it once, through the tracker (velocityModified):
                // the finisher sends it here as well, a tick before the tracker does, and a client that moves between
                // the two is thrown twice (2.56.0's check: 11 or 20 blocks)
                if(!heavy&&e instanceof ServerPlayerEntity other)other.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(other));
            }else HitStun.standHold(p,e,combo,now+hold(combo));
            // the user's own hit feedback (the damage number and the blood), as Kageno's
            if(ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(DAMAGE[combo]);b.writeFloat(s.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);}
        }
        // (the heavy punch's own sound carries its blow: StandAttack plays it on every client)
        if(!struck.isEmpty()){
            p.onAttacking(struck.get(0));
            if(!heavy)world.playSound(null,at.x,at.y,at.z,big?SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK:SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,SoundCategory.PLAYERS,big?.8f:.55f,big?.7f:.9f);
        }
        send(p,s,LANDED,struck,broke);
    }
    /**
     * Tells every player near the user (and the user, and whoever was struck) of the event; since 2.59.0 every event
     * also carries when the heavy punch is ready again and, as it lands, whose guard it broke.
     */
    private static void send(ServerPlayerEntity p,State s,int type,List<LivingEntity> struck,List<LivingEntity> broke){
        var world=p.getServerWorld();long now=world.getTime();
        float aim=type==PUNCH?aim(p,s):Float.NaN;
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128&&(struck==null||!struck.contains(viewer)))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);b.writeByte(s.combo);
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(s.yaw);b.writeFloat(aim);b.writeLong(s.ready);
            int n=struck==null?0:struck.size();b.writeVarInt(n);
            for(int i=0;i<n;i++){var e=struck.get(i);b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY());b.writeDouble(e.getZ());}
            b.writeLong(s.heavyReady);
            int m=broke==null?0:broke.size();b.writeVarInt(m);
            for(int i=0;i<m;i++)b.writeInt(broke.get(i).getId());
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    /**
     * How far ahead (blocks along the punch) the nearest one in its way stands, to the near side of their body, or NaN:
     * the stand goes as far out in front of its user as it takes for its fist to meet them.
     */
    private static float aim(ServerPlayerEntity p,State s){
        var world=p.getServerWorld();Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        double best=Double.NaN;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(REACH_BIG+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);double ahead=d.dotProduct(f),half=e.getWidth()*.5;
            if(!lane(s.combo,ahead,d.dotProduct(right),d.y,half)||!p.canSee(e))continue;
            if(!(ahead-half>=best))best=ahead-half;
        }
        return (float)best;
    }
}
