package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumAttack;
import dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown;
import dev.sangwoo.celestialfall.stand.StarPlatinumInhale;
import dev.sangwoo.celestialfall.stand.StarPlatinumRush;
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
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static dev.sangwoo.celestialfall.xchara.CloneRules.*;

/**
 * X Chara's R with the hack on, the double (2.125.0; CloneRules for its timing and its numbers). Their client's R asks for
 * it with the one its crosshair looks at (xchara_skill_v1, as G's: XCharaUltimate takes the press and picks whom); the
 * one marked has the "!!!" over them until {@link CloneRules#THROW}, and a double of them beside its user winds up to
 * throw (the clients' to draw, client/xchara/CloneClient). Its user is held where they stand until
 * {@link CloneRules#RELEASE}; struck, staggered, incapacitated or caught by another's hold before the throw, it is
 * called off. Any tick of the "!!!" the one marked holds a guard up toward its user, it is blocked (as G's). Either way
 * its cooldown runs from then.
 * <p>
 * At the throw the one marked is seized: whatever they were doing stops, they are held where they stand (the clients
 * draw them lifted and turning over in the air) and nothing harms them but what bypasses invulnerability; where the
 * wall will stand is chosen then ({@link #wallFor}: the way its user faced them, {@link CloneRules#WALL} blocks behind
 * them or as far as the way is clear, on the ground there). At {@link CloneRules#IMPACT} they are put at the wall's
 * foot facing its user, take {@link CloneRules#DAMAGE} and are incapacitated, unable to move, for
 * {@link CloneRules#STUCK} ticks (in the wall: any blow reaches them there); at {@link CloneRules#FREE} it is over and
 * its cooldown runs. It ends early, its cooldown running, if its user (before the wall) or the one marked dies, leaves
 * or is gone. Every viewer within 128 blocks of its user (and the two) is told of each step (xchara_clone_event_v1),
 * with when it is ready again.
 */
public final class XCharaClone {
    public static final Identifier EVENT=CelestialFall.id("xchara_clone_event_v1");
    /** It starts (the "!!!"); the one marked is seized (the wall's place chosen); they are in the wall; a guard blocked it; it was called off; it is over. */
    public static final int START=0,SEIZED=1,IMPACT_HIT=2,BLOCKED=3,CUT=4,END_OF=5;
    /** One of them: its user and the one it marked, when R was pressed, the way its user faced them, where each stood. */
    static final class Throw {
        final ServerPlayerEntity user;final LivingEntity target;final ServerWorld world;final long start;final float yaw;final Vec3d from;
        /** Where the one marked stood (as it began, then as they were seized), and the wall's foot (where they stand after), once chosen. */
        Vec3d targetFrom,wall;
        boolean seized,struck;float dealt;
        Throw(ServerPlayerEntity user,LivingEntity target,long start,float yaw){
            this.user=user;this.target=target;world=user.getServerWorld();this.start=start;this.yaw=yaw;from=user.getPos();targetFrom=target.getPos();wall=targetFrom;
        }
        long age(){return world.getTime()-start;}
    }
    private static final Map<UUID,Throw> BY_USER=new HashMap<>(),BY_TARGET=new HashMap<>();
    /** When each player's is ready again (the server's world time). */
    private static final Map<UUID,Long> READY=new HashMap<>();
    /** Set while its own blow is dealt (to the one in the wall). */
    private static final ThreadLocal<LivingEntity> BLOW=new ThreadLocal<>();
    private XCharaClone(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaClone::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var d=BY_USER.get(p.getUuid());if(d==null)d=BY_TARGET.get(p.getUuid());
            if(d!=null)end(d,CUT);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{BY_USER.clear();BY_TARGET.clear();READY.clear();});
    }

    // ───────────────────────────── who is in one ─────────────────────────────

    /** Whether this one is its user and still held by it (from the press until RELEASE). */
    public static boolean casting(LivingEntity e){
        if(e==null||e.getWorld().isClient)return false;
        var d=BY_USER.get(e.getUuid());return d!=null&&userHeld(d.age());
    }
    /** Whether this one is seized by one now: from the throw until the wall lets them go. */
    public static boolean held(LivingEntity e){
        if(e==null||e.getWorld().isClient)return false;
        var d=BY_TARGET.get(e.getUuid());return d!=null&&d.seized;
    }
    /** Whether one has this one now: its user while held, the one marked from the throw on (until then they are free, to guard). */
    public static boolean involved(LivingEntity e){return casting(e)||held(e);}
    /** Whether this one is marked by its "!!!" now (not yet seized). */
    public static boolean marked(LivingEntity e){var d=e==null||e.getWorld().isClient?null:BY_TARGET.get(e.getUuid());return d!=null&&!d.seized;}
    /** Whether harm from {@code source} is kept from {@code e}: seized and in the air (not yet in the wall), and this is not its own blow (what bypasses invulnerability still reaches them). */
    public static boolean shielded(LivingEntity e,DamageSource source){
        if(e==null||e.getWorld().isClient)return false;
        var d=BY_TARGET.get(e.getUuid());
        return d!=null&&d.seized&&!d.struck&&BLOW.get()!=e&&!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
    /** When this player's is ready again (the server's world time; none is long ago). */
    public static long ready(PlayerEntity p){return READY.getOrDefault(p.getUuid(),Long.MIN_VALUE);}
    /** For a boss reading what comes at it (BossThreats): the one marking {@code target} now, as {user, start tick}, or null. */
    public static Object[] marking(LivingEntity target){var d=target==null?null:BY_TARGET.get(target.getUuid());return d==null||d.seized?null:new Object[]{d.user,d.start};}
    /** For the multiplayer check: {@code user}'s now, as {start, seized, in the wall, dealt, the one marked's id, the wall's foot}, or null. */
    public static Object[] describe(PlayerEntity user){var d=user==null?null:BY_USER.get(user.getUuid());return d==null?null:new Object[]{d.start,d.seized,d.struck,d.dealt,d.target.getId(),d.wall};}
    /** Its cooldown is gone once X Chara is not theirs (StandManager.forgetOthers). */
    public static void forget(ServerPlayerEntity p){READY.remove(p.getUuid());}

    /** A blow landed on {@code target}: its user struck before the throw has it called off (only a blow: as G's, XCharaUltimate.struck). */
    public static void struck(LivingEntity target,DamageSource source){
        var d=target==null?null:BY_USER.get(target.getUuid());
        if(d==null||d.seized||d.user!=target)return;
        if(source.getAttacker()==null&&source.getSource()==null||source.getAttacker()==target)return;
        end(d,CUT);
    }

    // ───────────────────────────── the press ─────────────────────────────

    /** R pressed by {@code p}, the hack on, with {@code targetId} in their crosshair (-1 none): the "!!!" over the one marked, if it may be. Returns whether it began. */
    public static boolean cast(ServerPlayerEntity p,int targetId){
        if(!StandManager.xChara(p)||p.isSpectator()||!p.isAlive()||!XCharaHack.on(p))return false;
        var world=p.getServerWorld();long now=world.getTime();
        if(BY_USER.containsKey(p.getUuid())||XCharaUltimate.involved(p)||XCharaUltimate.marked(p)||now<ready(p)&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(CombatManager.busy(p)||CombatManager.cinematic(p)||CombatManager.actionLocked(p)||StarPlatinumTimeStop.holds(p)||StarPlatinumTimeStop.active(p))return false;
        if(p.hasVehicle()||p.isSleeping())return false;
        // (nor while their cleave's or their jab's motion runs on past its hold, as their other skills)
        if(XCharaAttack.cleaving(p)||XCharaHeal.busy(p))return false;
        LivingEntity target=null;
        if(targetId>=0&&world.getEntityById(targetId) instanceof LivingEntity e&&XCharaUltimate.fair(p,e))target=e;
        if(target==null)target=XCharaUltimate.looked(p);
        if(target==null)return false;
        // a boss marked is a boss struck (it guards, if it can)
        dev.sangwoo.celestialfall.boss.Bosses.provoke(target,p);
        Vec3d to=target.getPos().subtract(p.getPos());
        float yaw=to.horizontalLengthSquared()<1e-6?MathHelper.wrapDegrees(p.getYaw()):(float)Math.toDegrees(Math.atan2(-to.x,to.z));
        var d=new Throw(p,target,now,yaw);
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
        if(BY_USER.isEmpty())return;
        for(var d:new ArrayList<>(BY_USER.values())){
            long age=d.age();var u=d.user;var t=d.target;
            boolean userGone=u.isRemoved()||!u.isAlive()||u.getServerWorld()!=d.world||u.isSpectator()||!StandManager.xChara(u);
            boolean targetGone=t.isRemoved()||!t.isAlive()||t.getWorld()!=d.world||t.isSpectator();
            // (its user gone once the one seized is in the wall: the wall holds them its time all the same)
            if(targetGone||userGone&&!d.struck){end(d,CUT);continue;}
            if(!d.seized){
                // the "!!!": its user held looking at the one marked; caught by anything that holds them, it is called off
                if(HitStun.stunned(u)||CombatManager.incapacitated(u)||StarPlatinumTimeStop.holds(u)||StarPlatinumBeatdown.involved(u)
                    ||XCharaUltimate.held(u)||dev.sangwoo.celestialfall.AtomicManager.immobilized(u)||!XCharaHack.on(u)){end(d,CUT);continue;}
                KagenoManager.freeze(u);
                // a guard up toward its user, any tick of it, blocks it
                if(CombatManager.guardTakes(t,u.getPos(),true)){end(d,BLOCKED);continue;}
                if(age<THROW)continue;
                // the one marked caught by another's cutscene or stopped time meanwhile: it is called off
                if(StarPlatinumBeatdown.involved(t)||StarPlatinumTimeStop.stopped(t)||StarPlatinumTimeStop.holds(t)||XCharaUltimate.held(t)
                    ||dev.sangwoo.celestialfall.isosang.IsosangAttack.casting(t)||dev.sangwoo.celestialfall.isosang.IsosangAttack.finaling(t)
                    ||t instanceof PlayerEntity q&&dev.sangwoo.celestialfall.AtomicManager.immobilized(q)){end(d,CUT);continue;}
                seize(d);
            }
            if(userHeld(age)&&!userGone)KagenoManager.freeze(u);
            KagenoManager.freeze(t);t.fallDistance=0;
            if(!d.struck){
                if(age>=IMPACT)impact(d);
                if(!t.isAlive()){end(d,END_OF);continue;}
            }
            if(age>=FREE)end(d,END_OF);
        }
    }
    /** The throw: whatever the one marked was doing stops, they are held from now, and the wall's place is chosen. */
    private static void seize(Throw d){
        d.seized=true;var t=d.target;
        CombatManager.interrupt(t);HitStun.release(t.getUuid());
        if(t instanceof ServerPlayerEntity sp){
            StarPlatinumAttack.struck(sp);StarPlatinumRush.struck(sp);StarPlatinumInhale.struck(sp);StarPlatinumTimeStop.struck(sp);KagenoManager.disable(sp);
            dev.sangwoo.celestialfall.isosang.IsosangAttack.struck(sp);XCharaAttack.struck(sp);XCharaHeal.struck(sp);
            sp.clearActiveItem();sp.setSprinting(false);if(sp.currentScreenHandler!=sp.playerScreenHandler)sp.closeHandledScreen();
        }
        if(t instanceof MobEntity mob){mob.getNavigation().stop();mob.setTarget(null);}
        KagenoManager.freeze(t);
        d.targetFrom=t.getPos();d.wall=wallFor(d.world,t,d.yaw);
        send(d,SEIZED,d.world.getTime());
    }
    /**
     * The wall's foot for {@code t} thrown the way {@code yaw}: where they stand once it lets them go. As far that way
     * as their body goes clear (their chest's height and their knees'), WALL blocks at most; on the ground under there
     * (up to 4 blocks down, or a step of one up), or at their own height if there is none.
     */
    static Vec3d wallFor(ServerWorld world,LivingEntity t,float yaw){
        Vec3d f=Vec3d.fromPolar(0,yaw),from=t.getPos();double half=t.getWidth()*.5+.35,far=WALL;
        for(double up:new double[]{t.getHeight()*.3,t.getHeight()*.8}){
            Vec3d a=from.add(0,up,0),b=a.add(f.multiply(WALL+half));
            var hit=world.raycast(new RaycastContext(a,b,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,t));
            if(hit.getType()!=HitResult.Type.MISS)far=Math.min(far,hit.getPos().distanceTo(a)-half);
        }
        far=Math.max(0,far);
        Vec3d at=from.add(f.multiply(far));
        BlockPos pos=BlockPos.ofFloored(at.x,at.y+1.2,at.z);
        for(int i=0;i<7;i++){
            var shape=world.getBlockState(pos).getCollisionShape(world,pos);
            if(!shape.isEmpty()){
                double top=pos.getY()+shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                // (a step up of a block at most; higher is a wall in the way: they stay at their own height before it)
                return top<=from.y+1.05?new Vec3d(at.x,top,at.z):new Vec3d(at.x,from.y,at.z);
            }
            pos=pos.down();
        }
        return new Vec3d(at.x,from.y,at.z);
    }
    /** Into the wall: put at its foot facing its user, its damage past every guard (they hold none, held), and incapacitated, unable to move, while it holds them. */
    private static void impact(Throw d){
        d.struck=true;var t=d.target;float back=d.yaw+180;
        if(t instanceof ServerPlayerEntity sp)sp.networkHandler.requestTeleport(d.wall.x,d.wall.y,d.wall.z,back,0);
        else{t.refreshPositionAndAngles(d.wall.x,d.wall.y,d.wall.z,back,0);t.setHeadYaw(back);t.setBodyYaw(back);}
        t.setVelocity(Vec3d.ZERO);t.fallDistance=0;t.velocityModified=true;
        var source=d.world.getDamageSources().playerAttack(d.user);
        LivingEntity previous=BLOW.get();BLOW.set(t);
        try{
            t.timeUntilRegen=0;
            if(CombatManager.damageWithoutKnockback(t,source,DAMAGE))d.dealt+=DAMAGE;
        }finally{if(previous==null)BLOW.remove();else BLOW.set(previous);}
        d.user.onAttacking(t);
        if(t.isAlive())CombatManager.incapacitate(t,STUCK,STUCK,1);
        send(d,IMPACT_HIT,d.world.getTime());
    }
    /** It is over (done), blocked or called off: neither is held any more, and its cooldown runs from now. */
    private static void end(Throw d,int type){
        BY_USER.remove(d.user.getUuid());if(BY_TARGET.get(d.target.getUuid())==d)BY_TARGET.remove(d.target.getUuid());
        long now=d.world.getTime();
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(d.user))READY.put(d.user.getUuid(),CloneRules.ready(now));else READY.remove(d.user.getUuid());
        send(d,type,now);
    }
    /** Tells every player near its user (and the two) of the event. */
    private static void send(Throw d,int type,long now){
        var u=d.user;
        for(var viewer:d.world.getPlayers()){
            if(viewer!=u&&viewer!=d.target&&viewer.squaredDistanceTo(u)>128*128&&viewer.squaredDistanceTo(d.target)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var buf=PacketByteBufs.create();
            buf.writeUuid(u.getUuid());buf.writeIdentifier(d.world.getRegistryKey().getValue());buf.writeByte(type);
            buf.writeLong(d.start);buf.writeLong(now);buf.writeVarInt(d.target.getId());buf.writeUuid(d.target.getUuid());
            vec(buf,d.from);vec(buf,d.targetFrom);vec(buf,d.wall);buf.writeFloat(d.yaw);buf.writeFloat(d.dealt);buf.writeLong(ready(u));
            ServerPlayNetworking.send(viewer,EVENT,buf);
        }
    }
    private static void vec(net.minecraft.network.PacketByteBuf buf,Vec3d v){buf.writeDouble(v.x);buf.writeDouble(v.y);buf.writeDouble(v.z);}
}
