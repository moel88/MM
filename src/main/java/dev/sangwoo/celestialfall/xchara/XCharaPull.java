package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
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
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static dev.sangwoo.celestialfall.xchara.PullRules.*;

/**
 * X Chara's Y with the hack on, the pull (2.127.0; PullRules for its timing and its numbers). Their client's Y asks
 * for it with the one its crosshair looks at (xchara_skill_v1, as G's, R's and T's: XCharaUltimate takes the press);
 * the one marked has the "!!!" over them until {@link PullRules#PULL}, its user held where they stand looking at them.
 * Any tick of the "!!!" the one marked holds a guard up toward its user, it is blocked; its user struck, staggered,
 * incapacitated or caught by another's hold meanwhile, it is called off. At PULL the one marked is put before its user
 * ({@link #spot}): no harm, nothing they were doing stopped, facing as they faced. Its cooldown runs from then (or
 * from when it was blocked or called off).
 * <p>
 * One held by something else as it would pull them (a cutscene, stopped time, another's hold) is not pulled: it is
 * called off. Every viewer within 128 blocks is told of each step (xchara_pull_event_v1), the pull with where they
 * stood and where they stand now.
 */
public final class XCharaPull {
    public static final Identifier EVENT=CelestialFall.id("xchara_pull_event_v1");
    /** It starts (the "!!!"); the one marked is pulled; a guard blocked it; it was called off. */
    public static final int START=0,PULLED=1,BLOCKED=2,CUT=3;
    /** One being cast: its user and the one it marked, when Y was pressed, the way its user faced them. */
    static final class Mark {
        final ServerPlayerEntity user;final LivingEntity target;final ServerWorld world;final long start;final float yaw;
        Vec3d from=Vec3d.ZERO,to=Vec3d.ZERO;
        Mark(ServerPlayerEntity user,LivingEntity target,long start,float yaw){this.user=user;this.target=target;world=user.getServerWorld();this.start=start;this.yaw=yaw;}
        long age(){return world.getTime()-start;}
    }
    private static final Map<UUID,Mark> BY_USER=new HashMap<>(),BY_TARGET=new HashMap<>();
    /** When each player's is ready again (the server's world time). */
    private static final Map<UUID,Long> READY=new HashMap<>();
    /** For the multiplayer check: each player's last, as {how it ended, the tick, from x y z, to x y z}. */
    private static final Map<UUID,double[]> LAST=new HashMap<>();
    private XCharaPull(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaPull::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var d=BY_USER.get(p.getUuid());if(d==null)d=BY_TARGET.get(p.getUuid());
            if(d!=null)end(d,CUT);
            LAST.remove(p.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{BY_USER.clear();BY_TARGET.clear();READY.clear();LAST.clear();});
    }

    // ───────────────────────────── who is in one ─────────────────────────────

    /** Whether this one is its user and held by it now (through its "!!!"). */
    public static boolean casting(LivingEntity e){return e!=null&&!e.getWorld().isClient&&BY_USER.containsKey(e.getUuid());}
    /** Whether this one is marked by its "!!!" now. */
    public static boolean marked(LivingEntity e){return e!=null&&!e.getWorld().isClient&&BY_TARGET.containsKey(e.getUuid());}
    /** When this player's is ready again (the server's world time; none is long ago). */
    public static long ready(PlayerEntity p){return READY.getOrDefault(p.getUuid(),Long.MIN_VALUE);}
    /** For a boss reading what comes at it (BossThreats): the one marking {@code target} now, as {user, start tick}, or null. */
    public static Object[] marking(LivingEntity target){var d=target==null?null:BY_TARGET.get(target.getUuid());return d==null?null:new Object[]{d.user,d.start};}
    /** For the multiplayer check: {@code user}'s cast now as {start, the one marked's id}, or null. */
    public static long[] describe(PlayerEntity user){var d=user==null?null:BY_USER.get(user.getUuid());return d==null?null:new long[]{d.start,d.target.getId()};}
    /** For the multiplayer check: {@code user}'s last as {how it ended, the tick, from x y z, to x y z}, or null. */
    public static double[] last(PlayerEntity user){return user==null?null:LAST.get(user.getUuid());}
    /** Its cooldown is gone once X Chara is not theirs (StandManager.forgetOthers). */
    public static void forget(ServerPlayerEntity p){READY.remove(p.getUuid());}

    /** A blow landed on {@code target}: its user struck through its "!!!" has it called off (only a blow: as G's, XCharaUltimate.struck). */
    public static void struck(LivingEntity target,DamageSource source){
        var d=target==null?null:BY_USER.get(target.getUuid());
        if(d==null||d.user!=target)return;
        if(source.getAttacker()==null&&source.getSource()==null||source.getAttacker()==target)return;
        end(d,CUT);
    }

    // ───────────────────────────── the press ─────────────────────────────

    /** Y pressed by {@code p}, the hack on, with {@code targetId} in their crosshair (-1 none): the "!!!" over the one marked, if it may be. Returns whether it began. */
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
        send(d,START,now);
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
            if(age<PULL)continue;
            // one whom something else holds where they are is not pulled out of it
            if(caught(t)){end(d,CUT);continue;}
            pull(d);
            end(d,PULLED);
        }
    }
    /** Whether something else holds {@code t} where they are now (a cutscene, stopped time, another's hold, a formation's sword under them): they are not pulled out of it. */
    static boolean caught(LivingEntity t){
        if(StarPlatinumBeatdown.involved(t)||StarPlatinumTimeStop.stopped(t)||StarPlatinumTimeStop.holds(t)||XCharaUltimate.held(t)||XCharaClone.involved(t))return true;
        if(dev.sangwoo.celestialfall.isosang.IsosangAttack.casting(t)||dev.sangwoo.celestialfall.isosang.IsosangAttack.finaling(t))return true;
        if(t instanceof PlayerEntity q&&(dev.sangwoo.celestialfall.AtomicManager.immobilized(q)||dev.sangwoo.celestialfall.stand.StandArrow.stabbing(q)))return true;
        return KagenoManager.frozen(t);
    }
    /** The pull: the one marked put before its user, facing as they faced, still. */
    private static void pull(Mark d){
        var t=d.target;var u=d.user;
        d.from=t.getPos();d.to=spot(d.world,u,t,d.yaw);
        if(t.hasVehicle())t.stopRiding();
        if(t instanceof ServerPlayerEntity sp)sp.networkHandler.requestTeleport(d.to.x,d.to.y,d.to.z,sp.getYaw(),sp.getPitch());
        else t.refreshPositionAndAngles(d.to.x,d.to.y,d.to.z,t.getYaw(),t.getPitch());
        if(t instanceof MobEntity mob)mob.getNavigation().stop();
        t.setVelocity(Vec3d.ZERO);t.fallDistance=0;t.velocityModified=true;
    }
    /**
     * Where {@code t} is put before {@code u}: on the way from its user to where they stand (PullRules.way), AHEAD
     * blocks from its user's feet or as far as their body goes clear (their chest's height and their knees'); on the
     * ground under there (up to 4 blocks down, or a step of one up), or at its user's height if there is none.
     */
    static Vec3d spot(ServerWorld world,LivingEntity u,LivingEntity t,float yaw){
        double[] w=way(u.getX(),u.getZ(),t.getX(),t.getZ(),yaw);
        Vec3d f=new Vec3d(w[0],0,w[1]),from=u.getPos();double half=t.getWidth()*.5+.1,clear=AHEAD;
        for(double up:new double[]{t.getHeight()*.3,t.getHeight()*.8}){
            Vec3d a=from.add(0,up,0),b=a.add(f.multiply(AHEAD+half));
            var hit=world.raycast(new RaycastContext(a,b,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,u));
            if(hit.getType()!=HitResult.Type.MISS)clear=Math.min(clear,hit.getPos().distanceTo(a)-half);
        }
        Vec3d at=from.add(f.multiply(reach(clear)));
        BlockPos pos=BlockPos.ofFloored(at.x,at.y+1.2,at.z);
        for(int i=0;i<7;i++){
            var shape=world.getBlockState(pos).getCollisionShape(world,pos);
            if(!shape.isEmpty()){
                double top=pos.getY()+shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                // (a step up of a block at most; higher is a wall in the way: they stand at its user's height before it)
                return top<=from.y+1.05?new Vec3d(at.x,top,at.z):new Vec3d(at.x,from.y,at.z);
            }
            pos=pos.down();
        }
        return new Vec3d(at.x,from.y,at.z);
    }
    /** It pulled, was blocked or was called off: its user is free, and its cooldown runs from now. */
    private static void end(Mark d,int type){
        BY_USER.remove(d.user.getUuid());if(BY_TARGET.get(d.target.getUuid())==d)BY_TARGET.remove(d.target.getUuid());
        long now=d.world.getTime();
        if(!NoCooldownState.enabled(d.user))READY.put(d.user.getUuid(),PullRules.ready(now));else READY.remove(d.user.getUuid());
        LAST.put(d.user.getUuid(),new double[]{type,now,d.from.x,d.from.y,d.from.z,d.to.x,d.to.y,d.to.z});
        send(d,type,now);
    }
    /**
     * Tells every player near (within 128 blocks of its user or of the one marked, where they stand or stood, and the
     * two): whose, which event, when its cast began and the server's time now, the one marked, the way its user faced
     * them, where they stood and where they stand now (the pull's), and when its user's is ready again.
     */
    private static void send(Mark d,int type,long now){
        var user=d.user;var target=d.target;
        for(var viewer:d.world.getPlayers()){
            if(viewer!=user&&viewer!=target&&viewer.squaredDistanceTo(user)>128*128&&viewer.squaredDistanceTo(target)>128*128&&viewer.squaredDistanceTo(d.from)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var buf=PacketByteBufs.create();
            buf.writeUuid(user.getUuid());buf.writeIdentifier(d.world.getRegistryKey().getValue());buf.writeByte(type);
            buf.writeLong(d.start);buf.writeLong(now);buf.writeVarInt(target.getId());buf.writeUuid(target.getUuid());buf.writeFloat(d.yaw);
            buf.writeDouble(d.from.x);buf.writeDouble(d.from.y);buf.writeDouble(d.from.z);
            buf.writeDouble(d.to.x);buf.writeDouble(d.to.y);buf.writeDouble(d.to.z);
            buf.writeLong(ready(user));
            ServerPlayNetworking.send(viewer,EVENT,buf);
        }
    }
}
