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
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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
 * Gold Experience's Y, its healing (2.149.0; GoldExperienceRules has the user's words and the numbers). The user's
 * client says which it is (gold_heal_v1, one byte: TAP, Y tapped; HOLD, Y held past the threshold; RELEASE, Y let go)
 * and the server decides.
 * It is one punch with the fist left in (the user: "주먹을 한 번 꽂아서 주먹을 박아넣고 힐 되게"), never a barrage.
 * <ul>
 * <li>Tapped: the stand comes round to the front and drives one fist into the first one in its way, the way its user
 * faced as it began, MEND_CONTACT ticks on: whoever that is (anyone alive: friend or foe, a boss, a dummy, a mob) is
 * healed MEND (no harm, no cry). With no one in its way as Y is tapped nothing happens, and nothing is spent. Its user
 * walks freely through it; struck, staggered or incapacitated before the fist is in, or the stand gone in, stops it
 * short.</li>
 * <li>Held: the stand comes round to its user and drives one fist into them, SELF_FIRST ticks on, and leaves it in:
 * from then they are healed SELF_EACH every SELF_EVERY ticks, SELF_BLOWS times in all, until Y is let go. Its user
 * walks at a tenth of their speed (the rush's slow walk) and can do nothing else; a blow on them, a stagger, anything
 * that holds them or the stand gone in stops it.</li>
 * </ul>
 * One cooldown for both (HEAL_COOLDOWN), running once either is over or from where it was stopped. Neither starts while
 * the rush or the overdrive punch is on, nor those while either of these is. Every viewer within 128 blocks is told
 * (gold_heal_event_v1): what began, what the fist healed, each healing of its user, and the end.
 */
public final class GoldExperienceHeal {
    public static final Identifier INPUT=CelestialFall.id("gold_heal_v1"),EVENT=CelestialFall.id("gold_heal_event_v1");
    /** What the client says: Y let go; Y tapped (the other's healing); Y held (their own). */
    public static final int RELEASE=0,TAP=1,HOLD=2;
    /** The event's kinds: the hand goes out; it touched (whom, what it healed); the user's own healing begins; a blow healed them (how much); either ended (whether stopped short). */
    public static final int MENDING=0,MENDED=1,SELFING=2,BLOW=3,END=4;
    /** Its user's slow walk while it heals them (the client keeps its lens still while it is on, StandClient.skillSlowed). */
    public static final UUID SLOW=UUID.fromString("5b0e7a34-91c2-4d6f-b3a8-2e4f6c8d1a57");
    private static final int RATE=20;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** What is on (0 nothing, TAP, HOLD), since when, the way the hand goes, whether it has touched, and the tick Y is ready again. */
        int on;long start=Long.MIN_VALUE/4,ready;float yaw;boolean touched;
        int window,presses;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private GoldExperienceHeal(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=1)return;
            int what=buf.readByte();
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                // a release is always heard (their healing must never run on for want of one)
                if(what!=RELEASE&&++s.presses>RATE)return;
                if(what==TAP)mend(player);else if(what==HOLD)self(player);else if(what==RELEASE)release(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(GoldExperienceHeal::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{slow(handler.player,false);STATES.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    /** Whether Y is this player's healing: Gold Experience's user with it out, alive, on the map. */
    public static boolean heals(PlayerEntity p){return GoldExperienceOverdrive.punches(p);}
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){s=new State(p);STATES.put(p.getUuid(),s);}
        return s;
    }
    /** Whether this player's stand is healing them now (Y held): they may only walk. */
    public static boolean selfing(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.on==HOLD&&s.world==p.getWorld();}
    /** Whether this player's stand's hand is out to heal another now (Y tapped). */
    public static boolean mending(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.on==TAP&&s.world==p.getWorld();}
    /** Whether either is on. */
    public static boolean active(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.on!=0&&s.world==p.getWorld();}
    /** The tick this player's Y is ready again (0 if they have not used it). */
    public static long ready(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player's stand is not Gold Experience (StandManager): its cooldown is gone. */
    public static void forget(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)s.ready=0;}

    private static boolean may(ServerPlayerEntity p,State s,long now){
        if(!heals(p)||p.isSleeping()||p.hasVehicle()||CombatManager.busy(p)||CombatManager.cinematic(p))return false;
        if(s.on!=0||GoldExperienceOverdrive.punching(p)||StarPlatinumRush.rushing(p)||GoldExperienceBeatdown.opening(p)||GoldExperienceLife.punching(p))return false;
        return now>=s.ready||NoCooldownState.enabled(p);
    }
    /** Y tapped: the hand goes out to the first one in its way, if there is one. Returns whether it did. */
    public static boolean mend(ServerPlayerEntity p){
        var s=state(p);long now=p.getWorld().getTime();
        if(!may(p,s,now))return false;
        float yaw=MathHelper.wrapDegrees(p.getYaw());
        var e=first(p,yaw);if(e==null)return false;
        s.on=TAP;s.start=now;s.yaw=yaw;s.touched=false;
        s.ready=NoCooldownState.enabled(p)?now+MEND_LENGTH:healReady(now+MEND_LENGTH);
        send(p,s,MENDING,e,0);
        return true;
    }
    /** Y held: the stand turns on its user and heals them blow by blow. Returns whether it began. */
    public static boolean self(ServerPlayerEntity p){
        var s=state(p);long now=p.getWorld().getTime();
        if(!may(p,s,now))return false;
        s.on=HOLD;s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.ready=0;
        p.setSprinting(false);p.clearActiveItem();slow(p,true);
        send(p,s,SELFING,null,0);
        return true;
    }
    /** Y let go: their own healing stops at once (the hand's, once out, goes on). */
    public static void release(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null&&s.on==HOLD)end(p,s,p.getWorld().getTime(),false);}
    /** A blow landed on this player (HitStun.attackerDamaged), or something took hold of them: their own healing stops, and the hand not yet laid on is stopped short. */
    public static void struck(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());if(s==null||s.world!=p.getServerWorld())return;
        if(s.on==HOLD||s.on==TAP&&!s.touched)end(p,s,p.getWorld().getTime(),true);
    }
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            if(s.on==0)continue;
            long now=p.getWorld().getTime(),age=now-s.start;
            if(s.on==TAP){
                if(!s.touched&&(!heals(p)||HitStun.stunned(p)||CombatManager.actionLocked(p)||StarPlatinumRush.rushing(p))){end(p,s,now,true);continue;}
                if(!s.touched&&age>=MEND_CONTACT)touch(p,s);
                if(age>=MEND_LENGTH){s.on=0;send(p,s,END,null,0);}
                continue;
            }
            if(!heals(p)||HitStun.stunned(p)||CombatManager.incapacitated(p)||p.isSleeping()||p.hasVehicle()||CombatManager.cinematic(p)||KagenoManager.frozen(p)){end(p,s,now,true);continue;}
            p.setSprinting(false);
            if(selfBlow(age)){
                float before=p.getHealth();p.heal(SELF_EACH);
                send(p,s,BLOW,p,p.getHealth()-before);
            }
            if(age>=SELF_LENGTH)end(p,s,now,false);
        }
    }
    /** Either ends now: Y's cooldown runs from here (none for a player the cooldowns are off for). */
    private static void end(ServerPlayerEntity p,State s,long now,boolean stopped){
        boolean self=s.on==HOLD;
        s.on=0;s.ready=NoCooldownState.enabled(p)?now:healReady(now);
        if(self)slow(p,false);
        send(p,s,END,stopped?p:null,0);
    }
    /** The first one in the hand's reach (the nearest, to its near side), in sight: anyone alive on the map but a merchant. */
    private static LivingEntity first(ServerPlayerEntity p,float yaw){
        var world=p.getServerWorld();Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        LivingEntity first=null;double best=Double.MAX_VALUE;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(StarPlatinumRules.REACH_BIG+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);double ahead=d.dotProduct(f),half=e.getWidth()*.5;
            if(!mendLane(ahead,d.dotProduct(right),d.y,half)||!p.canSee(e))continue;
            // (one who has taken themselves off the map's rules, /botand vanilla, is not its to touch)
            if(e instanceof PlayerEntity other&&!MapGate.enabled(other))continue;
            // (2.152.0) nor a tree (GoldTreeEntity): there is nothing in it to heal
            if(e instanceof GoldTreeEntity)continue;
            if(ahead-half<best){best=ahead-half;first=e;}
        }
        return first;
    }
    /** The hand touches the first one in its way: they are healed. */
    private static void touch(ServerPlayerEntity p,State s){
        s.touched=true;
        var e=first(p,s.yaw);
        if(e==null){send(p,s,MENDED,null,0);return;}
        float before=e.getHealth();e.heal(GoldExperienceRules.MEND);
        send(p,s,MENDED,e,e.getHealth()-before);
    }
    /** Its user's slow walk while it heals them. */
    private static void slow(ServerPlayerEntity p,boolean on){
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
        if(!on){attr.removeModifier(SLOW);return;}
        if(attr.getModifier(SLOW)==null)attr.addTemporaryModifier(new EntityAttributeModifier(SLOW,"Gold Experience healing slow walk",-SELF_SLOW,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    /**
     * Tells every player near the user (and the user, and whoever the hand is for) of the event: {@code whom} the one
     * the hand goes to or touched (with how far off their near side stands as it goes out), the user for a blow, or
     * non-null for an end stopped short; {@code healed} what was healed.
     */
    private static void send(ServerPlayerEntity p,State s,int type,LivingEntity whom,float healed){
        var world=p.getServerWorld();long now=world.getTime();
        float aim=Float.NaN;
        if(type==MENDING&&whom!=null)aim=(float)(whom.getPos().subtract(p.getPos()).dotProduct(Vec3d.fromPolar(0,s.yaw))-whom.getWidth()*.5);
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer!=whom&&viewer.squaredDistanceTo(p)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(s.yaw);b.writeFloat(aim);b.writeLong(s.ready);
            b.writeInt(whom==null?-1:whom.getId());b.writeFloat(healed);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
