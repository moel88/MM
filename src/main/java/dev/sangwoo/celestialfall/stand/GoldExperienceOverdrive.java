package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.map.MapGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;

/**
 * Gold Experience's R, the sensory overdrive (2.148.0; GoldExperienceRules has the user's words and the numbers): with
 * the stand out, R (gold_overdrive_v1, no payload) makes it wind up and drive one heavy punch into the first one in its
 * way, OVER_CONTACT ticks on, the way its user faced as it began. A guard takes it. Whoever it lands on takes its
 * damage, is knocked back a little, and is out of their body for OVER_LASTS ticks: the body incapacitated with its legs
 * stopped (CombatManager.incapacitate: it can do nothing, and stands where it comes to rest), all harm to it times
 * OVER_TAKEN ({@link #scale}, LivingCombatMixin). The soul is the struck player's own client's (client/stand/SoulClient):
 * the server only tells who is out of their body and until when (gold_overdrive_event_v1), which their own client makes
 * a soul of and every other draws the body's daze and its doubled damage numbers by.
 * <p>
 * Its user walks freely through the punch; it is not thrown while the rush is on, nor the rush while it is. Its user
 * struck, staggered or incapacitated before it lands, or their stand gone in, stops it short. Its cooldown runs once the
 * punch is over (2.44.0's rule), or from where it was stopped short.
 */
public final class GoldExperienceOverdrive {
    public static final Identifier INPUT=CelestialFall.id("gold_overdrive_v1"),EVENT=CelestialFall.id("gold_overdrive_event_v1");
    /** The event's kinds: the punch begins, lands (on whom, if anyone, and whether their guard took it), is stopped short; a body's soul is out of it (until when), and back in it early. */
    public static final int PUNCH=0,LANDED=1,STOPPED=2,OUT=3,BACK=4;
    private static final int RATE=20;
    private static final class Punch {
        final ServerWorld world;long start=Long.MIN_VALUE/4,ready;float yaw;boolean landed=true;int window,presses;
        Punch(ServerPlayerEntity p){world=p.getServerWorld();}
        boolean flying(long now){return now-start<OVER_LENGTH;}
    }
    private record Soulless(LivingEntity body,ServerWorld world,long until){}
    private static final Map<UUID,Punch> PUNCHES=new HashMap<>();
    private static final Map<UUID,Soulless> OUTS=new HashMap<>();
    private GoldExperienceOverdrive(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->server.execute(()->{
            var s=state(player);int second=server.getTicks()/20;
            if(s.window!=second){s.window=second;s.presses=0;}
            if(++s.presses>RATE)return;
            request(player);
        }));
        ServerTickEvents.END_SERVER_TICK.register(GoldExperienceOverdrive::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->PUNCHES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{PUNCHES.clear();OUTS.clear();});
    }
    /** Whether R is this player's overdrive punch: Gold Experience's user with it out, alive, on the map. */
    public static boolean punches(PlayerEntity p){
        return p!=null&&MapGate.enabled(p.getWorld())&&StandManager.goldExperience(p)&&((StandHolder)p).botand$isStandOut()&&p.isAlive()&&!p.isSpectator();
    }
    private static Punch state(ServerPlayerEntity p){
        var s=PUNCHES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){s=new Punch(p);PUNCHES.put(p.getUuid(),s);}
        return s;
    }
    /** Whether this player's stand is throwing the punch now. */
    public static boolean punching(PlayerEntity p){var s=p==null?null:PUNCHES.get(p.getUuid());return s!=null&&s.world==p.getWorld()&&s.flying(p.getWorld().getTime());}
    /** The punch in flight for the bosses' reading of it: {start, the tick it lands, yaw as bits}, or null (none, or landed). */
    public static long[] coming(PlayerEntity p){
        var s=p==null?null:PUNCHES.get(p.getUuid());
        return s==null||s.world!=p.getWorld()||s.landed||!s.flying(p.getWorld().getTime())?null:new long[]{s.start,s.start+OVER_CONTACT,Float.floatToIntBits(s.yaw)};
    }
    /** The tick this player's overdrive is ready again (0 if they have not thrown it). */
    public static long ready(PlayerEntity p){var s=PUNCHES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player's stand is not Gold Experience (StandManager): its cooldown is gone. */
    public static void forget(PlayerEntity p){var s=PUNCHES.get(p.getUuid());if(s!=null)s.ready=0;}

    /** R: the punch starts now if it may. Returns whether it did. */
    public static boolean request(ServerPlayerEntity p){
        if(!punches(p)||p.isSleeping()||p.hasVehicle()||CombatManager.busy(p)||CombatManager.cinematic(p))return false;
        var s=state(p);long now=p.getWorld().getTime();boolean free=NoCooldownState.enabled(p);
        if(s.flying(now)||now<s.ready&&!free)return false;
        s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.landed=false;
        s.ready=free?now+OVER_LENGTH:overReady(now+OVER_LENGTH);
        send(p,s,PUNCH,null,false,0);
        return true;
    }
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(PUNCHES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){PUNCHES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            if(!s.flying(now))continue;
            if(!punches(p)||HitStun.stunned(p)||CombatManager.actionLocked(p)||StarPlatinumRush.rushing(p)){
                // stopped short: its cooldown runs from now
                s.start=Long.MIN_VALUE/4;s.landed=true;s.ready=NoCooldownState.enabled(p)?now:overReady(now);
                send(p,s,STOPPED,null,false,0);
                continue;
            }
            if(!s.landed&&now>=s.start+OVER_CONTACT)strike(p,s,now);
        }
        for(var e:new ArrayList<>(OUTS.entrySet())){
            var o=e.getValue();long now=o.world.getTime();
            boolean gone=!o.body.isAlive()||o.body.isRemoved()||o.body.getWorld()!=o.world||!MapGate.enabled(o.body.getWorld())||o.body.isSpectator();
            if(gone||now>=o.until){OUTS.remove(e.getKey());if(gone)tell(o,BACK,now);continue;}
            // once a second: whoever came near since knows whose soul is out (their damage numbers, the body's daze)
            if((o.until-now)%20==0)tell(o,OUT,now);
        }
    }
    /** A blow landed on this player: their stand's punch not yet landed is stopped short, its cooldown running from now (HitStun.attackerDamaged, as Star Platinum's). */
    public static void struck(ServerPlayerEntity p){
        var s=PUNCHES.get(p.getUuid());long now=p.getWorld().getTime();
        if(s==null||s.world!=p.getServerWorld()||s.landed||!s.flying(now))return;
        s.start=Long.MIN_VALUE/4;s.landed=true;s.ready=NoCooldownState.enabled(p)?now:overReady(now);
        send(p,s,STOPPED,null,false,0);
    }
    /** The first one in the punch's way (the nearest, to its near side), in sight, or null. */
    private static LivingEntity first(ServerPlayerEntity p,Punch s){
        var world=p.getServerWorld();Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        LivingEntity first=null;double best=Double.MAX_VALUE;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(StarPlatinumRules.REACH_BIG+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);double ahead=d.dotProduct(f),half=e.getWidth()*.5;
            if(!overLane(ahead,d.dotProduct(right),d.y,half)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            if(ahead-half<best){best=ahead-half;first=e;}
        }
        return first;
    }
    /** The punch lands on the first one in its way: a guard takes it; landing, it knocks them back a little and their soul is out of their body. */
    private static void strike(ServerPlayerEntity p,Punch s,long now){
        s.landed=true;
        var world=p.getServerWorld();var e=first(p,s);
        if(e==null){send(p,s,LANDED,null,false,0);return;}
        boolean guarded=CombatManager.guardTakes(e,p.getPos(),false);
        // (no sound of its own yet: the user sends one later; until then the game's own blow is heard as it lands)
        var source=world.getDamageSources().playerAttack(p);
        e.timeUntilRegen=0;
        if(!CombatManager.damageWithoutKnockback(e,source,OVER_DAMAGE)){send(p,s,LANDED,e,guarded,0);return;}
        p.onAttacking(e);
        world.playSound(null,e.getX(),e.getY(),e.getZ(),net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,net.minecraft.sound.SoundCategory.PLAYERS,.9f,.8f);
        // its user's own hit feedback (the damage number), before the body is told soulless: the punch's own damage is not doubled
        if(!StarPlatinumTimeStop.deferFeedback(p,e,OVER_DAMAGE,s.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(OVER_DAMAGE);b.writeFloat(s.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);}
        // shoved back a little along the punch (one stopped in a time stop, as time moves again)
        Vec3d shove=Vec3d.fromPolar(0,s.yaw).multiply(OVER_SHOVE).add(0,OVER_SHOVE_UP,0);
        if(!StarPlatinumTimeStop.deferLaunch(e,shove)){e.setVelocity(shove);e.velocityModified=true;}
        long until=out(e,now);
        send(p,s,LANDED,e,false,until);
    }
    /**
     * {@code e}'s soul goes out of their body now, for OVER_LASTS ticks: the body can do nothing and its legs are
     * stopped, and all harm to it is doubled. Returns the tick the soul is back.
     */
    public static long out(LivingEntity e,long now){
        long until=now+OVER_LASTS;
        CombatManager.incapacitate(e,OVER_LASTS,OVER_LASTS,1);
        var o=new Soulless(e,(ServerWorld)e.getWorld(),until);OUTS.put(e.getUuid(),o);
        tell(o,OUT,now);
        return until;
    }
    /** Whether {@code e}'s soul is out of their body now. */
    public static boolean out(LivingEntity e){
        if(e==null||OUTS.isEmpty()||e.getWorld().isClient)return false;
        var o=OUTS.get(e.getUuid());return o!=null&&o.body==e&&e.getWorld().getTime()<o.until;
    }
    /** How many ticks {@code e}'s soul is out of their body yet (0 if it is not). */
    public static long left(LivingEntity e){var o=e==null?null:OUTS.get(e.getUuid());return o==null||o.body!=e?0:Math.max(0,o.until-o.world.getTime());}
    /**
     * What harm of {@code amount} from {@code source} comes to for {@code victim}: times OVER_TAKEN if their soul is out
     * of their body (LivingCombatMixin, as the blow comes). Not what bypasses invulnerability (/kill, the void), and not
     * again as stopped time gives a stopped one what was kept (it was scaled as it was kept): as X Chara's weakening.
     */
    public static float scale(LivingEntity victim,DamageSource source,float amount){
        if(OUTS.isEmpty()||victim==null||victim.getWorld().isClient||source==null||!Float.isFinite(amount)||amount<=0)return amount;
        if(source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)||StarPlatinumTimeStop.settling()||!MapGate.enabled(victim.getWorld()))return amount;
        return overHarm(amount,out(victim));
    }
    /** What a blow to {@code victim} is multiplied by now (for what must know before it deals it: the beatdown keeps its one alive). */
    public static float factor(LivingEntity victim){return out(victim)?OVER_TAKEN:1;}

    /** Tells every player near the user (and the user, and whoever it struck) of the punch: {@code struck} the one it landed on or whose guard took it. */
    private static void send(ServerPlayerEntity p,Punch s,int type,LivingEntity struck,boolean guarded,long until){
        var world=p.getServerWorld();long now=world.getTime();
        float aim=Float.NaN;
        if(type==PUNCH){var e=first(p,s);if(e!=null)aim=(float)(e.getPos().subtract(p.getPos()).dotProduct(Vec3d.fromPolar(0,s.yaw))-e.getWidth()*.5);}
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer!=struck&&viewer.squaredDistanceTo(p)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(s.yaw);b.writeFloat(aim);b.writeLong(s.ready);
            b.writeInt(struck==null?-1:struck.getId());b.writeBoolean(guarded);b.writeLong(until);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    /** Tells every player near a body (and the body's own) that its soul is out of it until {@code until}, or back in it. */
    private static void tell(Soulless o,int type,long now){
        for(var viewer:o.world.getPlayers()){
            if(viewer!=o.body&&viewer.squaredDistanceTo(o.body)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeInt(o.body.getId());b.writeIdentifier(o.world.getRegistryKey().getValue());b.writeLong(now);b.writeLong(o.until);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
