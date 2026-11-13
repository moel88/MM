package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.WeakenRules.*;

/**
 * X Chara's T with the hack on, the weakening (2.126.0; WeakenRules for its timing and its numbers). Their client's T
 * asks for it with the one its crosshair looks at (xchara_skill_v1, as G's and R's: XCharaUltimate takes the press);
 * the one marked has the "!!!" over them until {@link WeakenRules#APPLY}, its user held where they stand looking at
 * them. Any tick of the "!!!" the one marked holds a guard up toward its user, it is blocked; its user struck,
 * staggered, incapacitated or caught by another's hold meanwhile, it is called off. At APPLY the one marked is weakened
 * for {@link WeakenRules#LASTS} ticks: every blow they deal is times {@link WeakenRules#DEALT} and every harm they take
 * times {@link WeakenRules#TAKEN} ({@link #scale}, LivingCombatMixin). Its cooldown runs from then (or from when it was
 * blocked or called off).
 * <p>
 * One weakened again is weakened anew for the full time (it does not stack). It ends with their death, or when they
 * are gone from the world. Every viewer within 128 blocks is told of each step (xchara_weaken_event_v1), and once a
 * second of everyone weakened near them (so one who comes near later sees it too).
 */
public final class XCharaWeaken {
    public static final Identifier EVENT=CelestialFall.id("xchara_weaken_event_v1");
    /** It starts (the "!!!"); the one marked is weakened; a guard blocked it; it was called off; one near is weakened (told again, once a second); one's weakening is over before its time. */
    public static final int START=0,APPLIED=1,BLOCKED=2,CUT=3,STATE=4,ENDED=5;
    /** One being cast: its user and the one it marked, when T was pressed, the way its user faced them. */
    static final class Mark {
        final ServerPlayerEntity user;final LivingEntity target;final ServerWorld world;final long start;final float yaw;
        Mark(ServerPlayerEntity user,LivingEntity target,long start,float yaw){this.user=user;this.target=target;world=user.getServerWorld();this.start=start;this.yaw=yaw;}
        long age(){return world.getTime()-start;}
    }
    /** One weakened: who, in which world, until when (its world's time), and by whom. */
    static final class Weak {
        final LivingEntity entity;final ServerWorld world;long until;UUID by;
        Weak(LivingEntity entity,long until,UUID by){this.entity=entity;world=(ServerWorld)entity.getWorld();this.until=until;this.by=by;}
    }
    private static final Map<UUID,Mark> BY_USER=new HashMap<>(),BY_TARGET=new HashMap<>();
    private static final Map<UUID,Weak> WEAK=new HashMap<>();
    /** When each player's is ready again (the server's world time). */
    private static final Map<UUID,Long> READY=new HashMap<>();
    private XCharaWeaken(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaWeaken::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var d=BY_USER.get(p.getUuid());if(d==null)d=BY_TARGET.get(p.getUuid());
            if(d!=null)end(d,CUT);
            // (one who leaves weakened is not when they are back: a new body)
            WEAK.remove(p.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{BY_USER.clear();BY_TARGET.clear();WEAK.clear();READY.clear();});
    }

    // ───────────────────────────── who is in one ─────────────────────────────

    /** Whether this one is its user and held by it now (through its "!!!"). */
    public static boolean casting(LivingEntity e){return e!=null&&!e.getWorld().isClient&&BY_USER.containsKey(e.getUuid());}
    /** Whether this one is marked by its "!!!" now. */
    public static boolean marked(LivingEntity e){return e!=null&&!e.getWorld().isClient&&BY_TARGET.containsKey(e.getUuid());}
    /** Whether this one is weakened now. */
    public static boolean weakened(LivingEntity e){
        if(e==null||WEAK.isEmpty()||e.getWorld().isClient)return false;
        var w=WEAK.get(e.getUuid());
        return w!=null&&w.entity==e&&w.world==e.getWorld()&&w.until>w.world.getTime();
    }
    /** When this player's is ready again (the server's world time; none is long ago). */
    public static long ready(PlayerEntity p){return READY.getOrDefault(p.getUuid(),Long.MIN_VALUE);}
    /** For a boss reading what comes at it (BossThreats): the one marking {@code target} now, as {user, start tick}, or null. */
    public static Object[] marking(LivingEntity target){var d=target==null?null:BY_TARGET.get(target.getUuid());return d==null?null:new Object[]{d.user,d.start};}
    /** For the multiplayer check: {@code e}'s weakening as the ticks left of it (0 if none). */
    public static long left(LivingEntity e){var w=e==null?null:WEAK.get(e.getUuid());return w==null||w.entity!=e?0:Math.max(0,w.until-w.world.getTime());}
    /** For the multiplayer check: {@code user}'s cast now as {start, the one marked's id}, or null. */
    public static long[] describe(PlayerEntity user){var d=user==null?null:BY_USER.get(user.getUuid());return d==null?null:new long[]{d.start,d.target.getId()};}
    /** Its cooldown is gone once X Chara is not theirs (StandManager.forgetOthers); whom they weakened stays so its time. */
    public static void forget(ServerPlayerEntity p){READY.remove(p.getUuid());}

    /**
     * What harm of {@code amount} from {@code source} comes to for {@code victim}: times DEALT if the one who dealt it
     * is weakened, times TAKEN if the one taking it is (LivingCombatMixin, as the blow comes). Not what bypasses
     * invulnerability (/kill, the void), and not again as stopped time gives a stopped one what was kept (it was
     * scaled as it was kept).
     */
    public static float scale(LivingEntity victim,DamageSource source,float amount){
        if(WEAK.isEmpty()||victim==null||victim.getWorld().isClient||source==null||!Float.isFinite(amount)||amount<=0)return amount;
        if(source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)||StarPlatinumTimeStop.settling()||!MapGate.enabled(victim.getWorld()))return amount;
        boolean dealer=source.getAttacker() instanceof LivingEntity a&&a!=victim&&weakened(a);
        return harm(amount,dealer,weakened(victim));
    }
    /** What a blow dealt by {@code dealer} to {@code victim} is multiplied by now (for what must know before it deals it: the beatdown keeps its one alive). */
    public static float factor(LivingEntity dealer,LivingEntity victim){return WeakenRules.scale(dealer!=victim&&weakened(dealer),weakened(victim));}

    /** A blow landed on {@code target}: its user struck through its "!!!" has it called off (only a blow: as G's, XCharaUltimate.struck). */
    public static void struck(LivingEntity target,DamageSource source){
        var d=target==null?null:BY_USER.get(target.getUuid());
        if(d==null||d.user!=target)return;
        if(source.getAttacker()==null&&source.getSource()==null||source.getAttacker()==target)return;
        end(d,CUT);
    }

    // ───────────────────────────── the press ─────────────────────────────

    /** T pressed by {@code p}, the hack on, with {@code targetId} in their crosshair (-1 none): the "!!!" over the one marked, if it may be. Returns whether it began. */
    public static boolean cast(ServerPlayerEntity p,int targetId){
        if(!StandManager.xChara(p)||p.isSpectator()||!p.isAlive()||!XCharaHack.on(p))return false;
        var world=p.getServerWorld();long now=world.getTime();
        if(BY_USER.containsKey(p.getUuid())||XCharaUltimate.involved(p)||XCharaUltimate.marked(p)||now<ready(p)&&!NoCooldownState.enabled(p))return false;
        if(CombatManager.busy(p)||CombatManager.cinematic(p)||CombatManager.actionLocked(p)||StarPlatinumTimeStop.holds(p)||StarPlatinumTimeStop.active(p))return false;
        if(p.hasVehicle()||p.isSleeping())return false;
        // (nor while their cleave's or their jab's motion runs on past its hold, nor through their restore)
        if(XCharaAttack.cleaving(p)||XCharaHeal.busy(p))return false;
        LivingEntity target=null;
        if(targetId>=0&&world.getEntityById(targetId) instanceof LivingEntity e&&XCharaUltimate.fair(p,e))target=e;
        if(target==null)target=XCharaUltimate.looked(p);
        if(target==null)return false;
        // a boss marked is a boss struck (it guards, if it can)
        dev.sangwoo.celestialfall.boss.Bosses.provoke(target,p);
        Vec3d to=target.getPos().subtract(p.getPos());
        float yaw=to.horizontalLengthSquared()<1e-6?MathHelper.wrapDegrees(p.getYaw()):(float)Math.toDegrees(Math.atan2(-to.x,to.z));
        var d=new Mark(p,target,now,yaw);
        BY_USER.put(p.getUuid(),d);BY_TARGET.put(target.getUuid(),d);
        // its user stops whatever they did and stands looking at the one marked
        p.setYaw(yaw);p.setHeadYaw(yaw);p.setBodyYaw(yaw);p.clearActiveItem();p.setSprinting(false);
        if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        // (the knife's combo ends with it, as with their other skills: no rest added)
        XCharaAttack.skillUsed(p);
        KagenoManager.freeze(p);
        send(d.world,p,target,START,now,d.start,0,d.yaw);
        return true;
    }

    // ───────────────────────────── its course ─────────────────────────────

    private static void tick(MinecraftServer server){
        for(var d:new ArrayList<>(BY_USER.values())){
            long age=d.age();var u=d.user;var t=d.target;
            boolean userGone=u.isRemoved()||!u.isAlive()||u.getServerWorld()!=d.world||u.isSpectator()||!StandManager.xChara(u);
            boolean targetGone=t.isRemoved()||!t.isAlive()||t.getWorld()!=d.world||t.isSpectator();
            if(targetGone||userGone){end(d,CUT);continue;}
            // the "!!!": its user held looking at the one marked; caught by anything that holds them, it is called off
            if(HitStun.stunned(u)||CombatManager.incapacitated(u)||StarPlatinumTimeStop.holds(u)||StarPlatinumBeatdown.involved(u)
                ||XCharaUltimate.held(u)||dev.sangwoo.celestialfall.AtomicManager.immobilized(u)||!XCharaHack.on(u)){end(d,CUT);continue;}
            KagenoManager.freeze(u);
            // a guard up toward its user, any tick of it, blocks it
            if(CombatManager.guardTakes(t,u.getPos(),true)){end(d,BLOCKED);continue;}
            if(age<APPLY)continue;
            // the weakening takes: for its full time, anew if they were weakened already
            long now=d.world.getTime();
            WEAK.put(t.getUuid(),new Weak(t,now+LASTS,u.getUuid()));
            end(d,APPLIED);
        }
        if(WEAK.isEmpty())return;
        for(var en:new ArrayList<>(WEAK.entrySet())){
            var w=en.getValue();var e=w.entity;long now=w.world.getTime();
            if(now>=w.until){WEAK.remove(en.getKey());continue;}
            if(e.isRemoved()||!e.isAlive()||e.getWorld()!=w.world){
                WEAK.remove(en.getKey());
                send(w.world,null,e,ENDED,now,now,now,0);
                continue;
            }
            // told again once a second to whoever is near (one who comes near later sees it too)
            if((now+e.getId())%20==0)send(w.world,null,e,STATE,now,w.until-LASTS,w.until,0);
        }
    }
    /** It took, was blocked or was called off: its user is free, and its cooldown runs from now. */
    private static void end(Mark d,int type){
        BY_USER.remove(d.user.getUuid());if(BY_TARGET.get(d.target.getUuid())==d)BY_TARGET.remove(d.target.getUuid());
        long now=d.world.getTime();
        if(!NoCooldownState.enabled(d.user))READY.put(d.user.getUuid(),WeakenRules.ready(now));else READY.remove(d.user.getUuid());
        send(d.world,d.user,d.target,type,now,d.start,type==APPLIED?now+LASTS:0,d.yaw);
    }
    /**
     * Tells every player near (within 128 blocks of its user or of the one it is about, and the two): whose (none for
     * a weakening only told of), which event, when its cast began and the server's time now, the one it is about,
     * until when they are weakened, the way its user faced them, and when its user's is ready again.
     */
    private static void send(ServerWorld world,ServerPlayerEntity user,LivingEntity target,int type,long now,long start,long until,float yaw){
        for(var viewer:world.getPlayers()){
            if(viewer!=user&&viewer!=target&&(user==null||viewer.squaredDistanceTo(user)>128*128)&&viewer.squaredDistanceTo(target)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var buf=PacketByteBufs.create();
            buf.writeBoolean(user!=null);if(user!=null)buf.writeUuid(user.getUuid());
            buf.writeIdentifier(world.getRegistryKey().getValue());buf.writeByte(type);
            buf.writeLong(start);buf.writeLong(now);buf.writeVarInt(target.getId());buf.writeUuid(target.getUuid());
            buf.writeLong(until);buf.writeFloat(yaw);buf.writeLong(user==null?Long.MIN_VALUE:ready(user));
            ServerPlayNetworking.send(viewer,EVENT,buf);
        }
    }
}
