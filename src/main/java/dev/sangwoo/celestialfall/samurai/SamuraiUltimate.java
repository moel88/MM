package dev.sangwoo.celestialfall.samurai;

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
import dev.sangwoo.celestialfall.xchara.XCharaUltimate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.*;

/**
 * The samurai's G, the one flash (2.143.0; SamuraiRules for its timing and its numbers), built as X Chara's sword dance
 * is (XCharaUltimate). Their client's G asks for it with the one its crosshair looks at (samurai_skill_v1: the skill, then
 * that one's entity id, -1 for none: the server then takes the one its user's look meets). Its user is held where they
 * stand from the press; struck, staggered, incapacitated or caught by another's hold before {@link SamuraiRules#DASH}, it
 * is called off, and any tick before then that the one marked holds a guard up toward its user, it is blocked. Either way
 * its cooldown runs from then.
 * <p>
 * At DASH its user is put beside the one marked, their back to them (a little short of them on the way they came and to
 * the side: where there is room), and both are held from then (KagenoManager.frozen): nothing harms them but its own
 * blows (shielded), and whatever the one marked was doing stops. Its blows land on the one marked where they stand
 * ({@link SamuraiRules#BLOWS}), none but the last taking them below one health; with the last they are thrown up into the
 * air and let go, its user a little after ({@link SamuraiRules#END}), when its cooldown begins. It ends early, its
 * cooldown running, if its user or the one marked dies, leaves or is gone. Every viewer within 128 blocks of its user (and
 * the two) is told when it starts, when its cutscene begins (where the two stand then), and when it is blocked, called off
 * or over (samurai_skill_event_v1), with when it is ready again. Everything seen is the clients' to draw from its clock
 * (client/samurai).
 */
public final class SamuraiUltimate {
    public static final Identifier INPUT=CelestialFall.id("samurai_skill_v1"),EVENT=CelestialFall.id("samurai_skill_event_v1");
    /** It starts; its user is beside the one marked and its cutscene begins; a guard blocked it; it was called off; it is over. */
    public static final int START=0,SCENE=1,BLOCKED=2,CUT=3,END=4;
    /** At most this many presses a second are heard. */
    static final int RATE=20;
    /** One of them: its user and the one it marked, when G was pressed, the way its user faced them, where each stood. */
    static final class Flash {
        final ServerPlayerEntity user;final LivingEntity target;final ServerWorld world;final long start;
        /** The way its user faced the one marked: as it was pressed, then as its cutscene began. */
        float yaw;
        Vec3d from,targetFrom;
        /** Whether its cutscene has begun (past DASH), where its user stands in it and the one marked is held, and what its blows have dealt. */
        boolean scene;Vec3d spot,hold;float dealt;
        /** Whether the one marked was thrown up (let go since). */
        boolean thrown;
        Flash(ServerPlayerEntity user,LivingEntity target,long start,float yaw){
            this.user=user;this.target=target;world=user.getServerWorld();this.start=start;this.yaw=yaw;from=user.getPos();targetFrom=target.getPos();spot=from;hold=targetFrom;
        }
        long age(){return world.getTime()-start;}
    }
    private static final Map<UUID,Flash> BY_USER=new HashMap<>(),BY_TARGET=new HashMap<>();
    /** When each player's G is ready again (the server's world time). */
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static final Map<UUID,long[]> PRESSES=new HashMap<>();
    /** Set while one of its own blows is dealt (to the one marked): the only harm that reaches them. */
    private static final ThreadLocal<LivingEntity> BLOW=new ThreadLocal<>();
    private SamuraiUltimate(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()<1||buf.readableBytes()>6)return;
            int skill=buf.readUnsignedByte();
            int target=buf.readableBytes()>0?buf.readVarInt():-1;
            if(skill!=G)return;
            server.execute(()->{if(heard(player))cast(player,target);});
        });
        ServerTickEvents.END_SERVER_TICK.register(SamuraiUltimate::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var d=BY_USER.get(p.getUuid());if(d==null)d=BY_TARGET.get(p.getUuid());
            if(d!=null)end(d,CUT);
            PRESSES.remove(p.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{BY_USER.clear();BY_TARGET.clear();READY.clear();PRESSES.clear();});
    }
    /** Whether this press is heard (at most RATE a second). */
    private static boolean heard(ServerPlayerEntity p){
        long now=p.getServer().getTicks();var w=PRESSES.computeIfAbsent(p.getUuid(),k->new long[]{now,0});
        if(now-w[0]>=20){w[0]=now;w[1]=0;}
        return ++w[1]<=RATE;
    }

    // ───────────────────────────── who is in one ─────────────────────────────

    /** Whether this one is its user now (any part of it: they stand held from the press). */
    public static boolean casting(LivingEntity e){return e!=null&&!e.getWorld().isClient&&BY_USER.containsKey(e.getUuid());}
    /** Whether this one is held in its cutscene now: its user, or the one marked until they are thrown up. */
    public static boolean held(LivingEntity e){
        if(e==null||e.getWorld().isClient)return false;
        var d=BY_USER.get(e.getUuid());if(d!=null)return d.scene;
        d=BY_TARGET.get(e.getUuid());
        return d!=null&&d.scene&&!d.thrown;
    }
    /** Whether one of them has this one now: its user from the press, the one marked from its cutscene on (until then they are free, to guard). */
    public static boolean involved(LivingEntity e){return casting(e)||held(e);}
    /** Whether this one is marked by one now (its cutscene not yet begun). */
    public static boolean marked(LivingEntity e){var d=e==null||e.getWorld().isClient?null:BY_TARGET.get(e.getUuid());return d!=null&&!d.scene;}
    /** Whether harm from {@code source} is kept from {@code e}: it is held in the cutscene and this is not its own blow (what bypasses invulnerability, /kill or the void, still reaches them). */
    public static boolean shielded(LivingEntity e,DamageSource source){
        return held(e)&&BLOW.get()!=e&&!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
    /** When this player's G is ready again (the server's world time; none is long ago). */
    public static long ready(PlayerEntity p){return READY.getOrDefault(p.getUuid(),Long.MIN_VALUE);}
    /** For the checks: {@code user}'s G now, as {start, whether its cutscene has begun, what its blows dealt, the one marked's id}, or null. */
    public static Object[] describe(PlayerEntity user){var d=user==null?null:BY_USER.get(user.getUuid());return d==null?null:new Object[]{d.start,d.scene,d.dealt,d.target.getId()};}
    /** Its cooldown is gone once the samurai is not theirs (as every other character's short ones are, StandManager.forgetOthers). */
    public static void forget(ServerPlayerEntity p){READY.remove(p.getUuid());}

    /**
     * A blow landed on {@code target} (LivingEntity.damage let it through): its user struck before its cutscene has it
     * called off. Only a blow: harm from someone, or from something thrown; none from a fall, a fire or a poison, nor
     * their own (as X Chara's G's, XCharaUltimate.struck).
     */
    public static void struck(LivingEntity target,DamageSource source){
        var d=target==null?null:BY_USER.get(target.getUuid());
        if(d==null||d.scene||d.user!=target)return;
        if(source.getAttacker()==null&&source.getSource()==null||source.getAttacker()==target)return;
        end(d,CUT);
    }

    // ───────────────────────────── the press ─────────────────────────────

    /** G pressed by {@code p} with {@code targetId} in their crosshair (-1 none): it begins, if it may. Returns whether it did. */
    public static boolean cast(ServerPlayerEntity p,int targetId){
        if(!StandManager.samurai(p)||p.isSpectator()||!p.isAlive())return false;
        var world=p.getServerWorld();long now=world.getTime();
        if(involved(p)||marked(p)||now<ready(p)&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(CombatManager.busy(p)||CombatManager.cinematic(p)||CombatManager.actionLocked(p)||StarPlatinumTimeStop.holds(p)||StarPlatinumTimeStop.active(p))return false;
        if(p.hasVehicle()||p.isSleeping())return false;
        LivingEntity target=null;
        if(targetId>=0&&world.getEntityById(targetId) instanceof LivingEntity e&&fair(p,e,AIM_RANGE+1.5))target=e;
        if(target==null)target=looked(p);
        if(target==null)return false;
        // a boss marked is a boss struck (it guards, if it can)
        dev.sangwoo.celestialfall.boss.Bosses.provoke(target,p);
        var d=new Flash(p,target,now,facing(p,target));
        BY_USER.put(p.getUuid(),d);BY_TARGET.put(target.getUuid(),d);
        // its user stops whatever they did and stands looking at the one marked
        turn(p,d.yaw);p.clearActiveItem();p.setSprinting(false);
        if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        KagenoManager.freeze(p);
        send(d,START,now);
        return true;
    }
    private static float facing(LivingEntity from,LivingEntity to){
        Vec3d way=to.getPos().subtract(from.getPos());
        return way.horizontalLengthSquared()<1e-6?MathHelper.wrapDegrees(from.getYaw()):(float)Math.toDegrees(Math.atan2(-way.x,way.z));
    }
    private static void turn(LivingEntity e,float yaw){e.setYaw(yaw);e.setHeadYaw(yaw);e.setBodyYaw(yaw);}
    /**
     * Whether {@code e} may be marked by {@code p}: alive and to be struck, not a merchant, within {@code range} of their
     * eyes, in their sight, fair game (a player only where they may harm each other), and in no one's cutscene or stopped
     * time.
     */
    static boolean fair(ServerPlayerEntity p,LivingEntity e,double range){
        if(e==p||!e.isAlive()||e.isRemoved()||e.isSpectator()||!e.canHit()||e.getWorld()!=p.getWorld())return false;
        if(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity||e instanceof dev.sangwoo.celestialfall.stand.GoldFlyEntity)return false;
        if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))return false;
        Vec3d eye=p.getEyePos(),chest=e.getPos().add(0,e.getHeight()*.55,0);
        if(eye.squaredDistanceTo(chest)>range*range)return false;
        if(p.getWorld().raycast(new RaycastContext(eye,chest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()!=HitResult.Type.MISS)return false;
        return !caught(e);
    }
    /** Whether {@code e} is in another's cutscene, hold or stopped time (not to be marked, nor taken into this one). */
    private static boolean caught(LivingEntity e){
        if(XCharaUltimate.involved(e)||XCharaUltimate.marked(e)||dev.sangwoo.celestialfall.xchara.XCharaAttack.airborne(e)||StarPlatinumBeatdown.involved(e)||StarPlatinumTimeStop.stopped(e)||StarPlatinumTimeStop.holds(e))return true;
        if(dev.sangwoo.celestialfall.isosang.IsosangAttack.casting(e)||dev.sangwoo.celestialfall.isosang.IsosangAttack.finaling(e))return true;
        var mine=BY_TARGET.get(e.getUuid());if(mine!=null&&mine.scene||BY_USER.containsKey(e.getUuid()))return true;
        return e instanceof PlayerEntity q&&(dev.sangwoo.celestialfall.AtomicManager.immobilized(q)||dev.sangwoo.celestialfall.stand.StandArrow.stabbing(q));
    }
    /** The one {@code p}'s look meets within AIM_RANGE (their box half a block bigger), if they may be marked. */
    static LivingEntity looked(ServerPlayerEntity p){
        Vec3d eye=p.getEyePos(),look=p.getRotationVec(1),end=eye.add(look.multiply(AIM_RANGE));
        var block=p.getWorld().raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        double reach=block.getType()==HitResult.Type.MISS?AIM_RANGE:block.getPos().distanceTo(eye);
        LivingEntity best=null;double near=reach;
        for(Entity e:p.getWorld().getOtherEntities(p,p.getBoundingBox().stretch(look.multiply(reach)).expand(1.5),x->x instanceof LivingEntity)){
            var hit=e.getBoundingBox().expand(.5).raycast(eye,eye.add(look.multiply(reach)));
            if(hit.isEmpty())continue;double dist=hit.get().distanceTo(eye);
            if(dist<near&&!BY_TARGET.containsKey(e.getUuid())&&fair(p,(LivingEntity)e,AIM_RANGE+1.5)){near=dist;best=(LivingEntity)e;}
        }
        return best;
    }

    // ───────────────────────────── its course ─────────────────────────────

    private static void tick(MinecraftServer server){
        if(BY_USER.isEmpty())return;
        for(var d:new ArrayList<>(BY_USER.values())){
            long age=d.age();var u=d.user;var t=d.target;
            boolean userGone=u.isRemoved()||!u.isAlive()||u.getServerWorld()!=d.world||u.isSpectator()||!StandManager.samurai(u);
            boolean targetGone=!d.thrown&&(t.isRemoved()||!t.isAlive()||t.getWorld()!=d.world||t.isSpectator());
            if(userGone||targetGone){end(d,CUT);continue;}
            if(!d.scene){
                // before the dash: its user held looking at the one marked; caught by anything that holds them, it is called off
                if(HitStun.stunned(u)||CombatManager.incapacitated(u)||StarPlatinumTimeStop.holds(u)||StarPlatinumBeatdown.involved(u)||XCharaUltimate.held(u)
                    ||dev.sangwoo.celestialfall.AtomicManager.immobilized(u)){end(d,CUT);continue;}
                KagenoManager.freeze(u);
                // a guard up toward its user, any tick of it, blocks it
                if(CombatManager.guardTakes(t,u.getPos(),true)){end(d,BLOCKED);continue;}
                if(age<DASH)continue;
                // the one marked gone too far, out of sight, or caught by another's cutscene or stopped time meanwhile: called off
                Vec3d eye=u.getEyePos(),chest=t.getPos().add(0,t.getHeight()*.55,0);
                if(eye.squaredDistanceTo(chest)>REACH*REACH||caught(t)
                    ||d.world.raycast(new RaycastContext(eye,chest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,u)).getType()!=HitResult.Type.MISS){end(d,CUT);continue;}
                if(!scene(d)){end(d,CUT);continue;}
            }
            KagenoManager.freeze(u);
            if(!d.thrown){KagenoManager.freeze(t);t.fallDistance=0;}
            int k=blowIndex(age);
            if(k>=0&&!d.thrown)blow(d,k);
            if(age>=LAUNCH&&!d.thrown)launch(d);
            if(age>=SamuraiRules.END)end(d,END);
        }
    }
    /**
     * Its cutscene begins: its user is put beside the one marked, their back to them, where there is room (a little short
     * of them and to the right of the way they came, or to the left, or straight short of them), and both are held from
     * now; whatever the one marked was doing stops. Returns whether there was room.
     */
    private static boolean scene(Flash d){
        var u=d.user;var t=d.target;
        d.yaw=facing(u,t);d.hold=t.getPos();
        Vec3d f=Vec3d.fromPolar(0,d.yaw),r=new Vec3d(-f.z,0,f.x);
        Vec3d spot=null;
        for(double[] c:new double[][]{{SHORT,ASIDE},{SHORT,-ASIDE},{SHORT+.4,0},{SHORT-.3,0}}){
            Vec3d p=d.hold.subtract(f.multiply(c[0])).add(r.multiply(c[1]));
            // onto the ground there (a step up or down)
            var ground=d.world.raycast(new RaycastContext(p.add(0,1.2,0),p.add(0,-2.5,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,u));
            if(ground.getType()==HitResult.Type.MISS)continue;
            Vec3d at=ground.getPos();
            if(Math.abs(at.y-d.hold.y)>1.3||!fits(d.world,u,at))continue;
            spot=at;break;
        }
        if(spot==null)return false;
        d.spot=spot;d.scene=true;
        CombatManager.interrupt(t);HitStun.release(t.getUuid());
        StarPlatinumRush.struck(u);StarPlatinumInhale.struck(u);
        if(t instanceof ServerPlayerEntity sp){
            StarPlatinumAttack.struck(sp);StarPlatinumRush.struck(sp);StarPlatinumInhale.struck(sp);StarPlatinumTimeStop.struck(sp);KagenoManager.disable(sp);
            sp.clearActiveItem();sp.setSprinting(false);if(sp.currentScreenHandler!=sp.playerScreenHandler)sp.closeHandledScreen();
        }
        if(t instanceof MobEntity mob){mob.getNavigation().stop();mob.setTarget(null);}
        // its user beside them, their back to them (the way they came); the one marked turned to where its user came from
        float back=MathHelper.wrapDegrees(d.yaw+180);
        u.networkHandler.requestTeleport(spot.x,spot.y,spot.z,back,0);
        u.setVelocity(Vec3d.ZERO);u.fallDistance=0;turn(u,back);
        turn(t,back);t.setVelocity(Vec3d.ZERO);t.velocityModified=true;
        KagenoManager.freeze(u);KagenoManager.freeze(t);
        send(d,SCENE,d.world.getTime());
        return true;
    }
    /** Whether {@code e}'s body fits at {@code p} (its feet). */
    private static boolean fits(ServerWorld world,LivingEntity e,Vec3d p){
        double half=e.getWidth()*.5;
        return world.isSpaceEmpty(e,new Box(p.x-half,p.y+.01,p.z-half,p.x+half,p.y+e.getHeight(),p.z+half));
    }
    /**
     * One of its blows on the one marked, where they stand: its share of the damage, past every guard but its own (they
     * cannot hold one up, held), none but the last taking them below one health (a blow that finds them at one health
     * still shows as one: they flash red, and it counts a hit, as the beatdown's); quietly but the first and the last.
     */
    private static void blow(Flash d,int k){
        var t=d.target;var source=d.world.getDamageSources().playerAttack(d.user);
        boolean last=k==BLOWS.length-1,quietly=k>0&&!last;
        float weak=Math.max(.01f,dev.sangwoo.celestialfall.xchara.XCharaWeaken.factor(d.user,t));
        float amount=last?DAMAGE-d.dealt:Math.min(blowDamage(k),(t.getHealth()-1)/weak);
        if(amount>0){
            LivingEntity previous=BLOW.get();BLOW.set(t);
            try{
                t.timeUntilRegen=0;
                boolean landed=quietly?CombatManager.damageQuietly(t,source,amount):CombatManager.damageWithoutKnockback(t,source,amount);
                if(landed)d.dealt+=amount;
            }finally{if(previous==null)BLOW.remove();else BLOW.set(previous);}
        }else if(t.isAlive()){
            d.world.sendEntityDamage(t,source);
            dev.sangwoo.celestialfall.combat.HitCounter.struck(d.user,t);
        }
        d.user.onAttacking(t);
    }
    /** Every blow taken: the one marked is thrown up into the air, a little away from its user, and let go. */
    private static void launch(Flash d){
        d.thrown=true;
        if(BY_TARGET.get(d.target.getUuid())==d)BY_TARGET.remove(d.target.getUuid());
        var t=d.target;if(!t.isAlive()||t.isRemoved())return;
        Vec3d away=d.hold.subtract(d.spot);away=away.horizontalLengthSquared()<1e-6?Vec3d.ZERO:new Vec3d(away.x,0,away.z).normalize();
        t.setVelocity(away.x*FLY,FLY_UP,away.z*FLY);t.velocityModified=true;t.fallDistance=0;
    }
    /** It is over (done), blocked or called off: neither is held any more, and its cooldown runs from now. */
    private static void end(Flash d,int type){
        BY_USER.remove(d.user.getUuid());if(BY_TARGET.get(d.target.getUuid())==d)BY_TARGET.remove(d.target.getUuid());
        long now=d.world.getTime();
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(d.user))READY.put(d.user.getUuid(),SamuraiRules.ready(now));else READY.remove(d.user.getUuid());
        // cut short, its user's hit count is held open no longer
        if(type!=END)dev.sangwoo.celestialfall.combat.HitCounter.release(d.user);
        send(d,type,now);
    }
    /** Tells every player near its user (and the two) of the event. */
    private static void send(Flash d,int type,long now){
        var u=d.user;
        for(var viewer:d.world.getPlayers()){
            if(viewer!=u&&viewer!=d.target&&viewer.squaredDistanceTo(u)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var buf=PacketByteBufs.create();
            buf.writeUuid(u.getUuid());buf.writeIdentifier(d.world.getRegistryKey().getValue());buf.writeByte(type);
            buf.writeLong(d.start);buf.writeLong(now);buf.writeVarInt(d.target.getId());buf.writeUuid(d.target.getUuid());
            vec(buf,d.from);vec(buf,d.targetFrom);buf.writeFloat(d.yaw);vec(buf,d.spot);vec(buf,d.hold);buf.writeLong(ready(u));
            ServerPlayNetworking.send(viewer,EVENT,buf);
        }
    }
    private static void vec(net.minecraft.network.PacketByteBuf buf,Vec3d v){buf.writeDouble(v.x);buf.writeDouble(v.y);buf.writeDouble(v.z);}
}
