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
 */
public final class StarPlatinumAttack {
    public static final Identifier INPUT=CelestialFall.id("stand_attack_v1"),EVENT=CelestialFall.id("stand_attack_event_v1");
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
        /** The tick the attack is ready again after a rest (0 for none). */
        long ready;
        int window,presses;
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
        if(s.combo>=1&&s.combo<4&&now<nextAt(s.combo,s.start)){
            if(nextAt(s.combo,s.start)-now<=BUFFER)s.queued=true;
            return s.queued;
        }
        begin(p,s,now);
        return true;
    }
    /** The user was struck: whom the punches hold go, and the punch in flight stops short. */
    public static void struck(ServerPlayerEntity p){
        HitStun.releaseStand(p.getUuid());
        var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());
    }
    /** The combo broke (the next punch is late): the attack rests and starts again from the first punch. */
    private static void settle(ServerPlayerEntity p,State s,long now){
        long rest=broken(s.combo,s.start,now);
        if(rest>=0){s.combo=0;s.queued=false;s.ready=Math.max(s.ready,rest);send(p,s,RESTS,null);}
        if(s.combo==4&&now>=s.ready)s.combo=0;
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        s.combo=next(s.combo);s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=s.landed=false;
        if(s.combo==4)s.ready=rested(now);
        send(p,s,PUNCH,null);
    }
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            if(!punches(p)||HitStun.stunned(p)||CombatManager.actionLocked(p)){stop(p,s,now);continue;}
            settle(p,s,now);
            if(s.queued&&now>=nextAt(s.combo,s.start)){if(able(p))begin(p,s,now);else s.queued=false;}
            if(s.combo>=1&&!s.landed&&now>=s.start+CONTACT[s.combo])strike(p,s,now);
        }
    }
    /** The punch in flight stops short (or the combo is cut off between punches): the attack rests from now. */
    private static void stop(ServerPlayerEntity p,State s,long now){
        boolean flying=s.combo>=1&&now-s.start<LENGTH[s.combo],open=s.combo>=1&&s.combo<4&&broken(s.combo,s.start,now)<0;
        if(!flying&&!open&&!s.queued)return;
        s.queued=false;s.ready=Math.max(s.ready,now+REST);s.combo=0;
        send(p,s,STOPPED,null);
    }
    /**
     * The punch lands: everyone in its way in front of its user, in sight, is struck. The first three hold whom they
     * strike for the next punch; the fourth cannot be guarded and throws them along it.
     */
    private static void strike(ServerPlayerEntity p,State s,long now){
        s.landed=true;
        var world=p.getServerWorld();int combo=s.combo;boolean big=combo==4;
        Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        var source=world.getDamageSources().playerAttack(p);
        List<LivingEntity> struck=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(REACH_BIG+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            if(!lane(combo,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&(!world.getServer().isPvpEnabled()||!p.shouldDamagePlayer(other)))continue;
            if(big)CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,DAMAGE[combo]))continue;
            struck.add(e);
            if(big){
                HitStun.release(e.getUuid());
                e.setVelocity(f.multiply(FLY).add(0,FLY_UP,0));e.velocityModified=true;
                if(e instanceof ServerPlayerEntity other)other.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(other));
            }else HitStun.standHold(p,e,combo,now+hold(combo));
            // the user's own hit feedback (the damage number and the blood), as Kageno's
            if(ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(DAMAGE[combo]);b.writeFloat(s.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);}
        }
        if(!struck.isEmpty()){
            p.onAttacking(struck.get(0));
            world.playSound(null,at.x,at.y,at.z,big?SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK:SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,SoundCategory.PLAYERS,big?.8f:.55f,big?.7f:.9f);
        }
        send(p,s,LANDED,struck);
    }
    /** Tells every player near the user (and the user, and whoever was struck) of the event. */
    private static void send(ServerPlayerEntity p,State s,int type,List<LivingEntity> struck){
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
