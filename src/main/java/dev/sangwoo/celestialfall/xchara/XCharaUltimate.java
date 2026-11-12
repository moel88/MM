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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;

/**
 * X Chara's G, the sword dance (2.112.0; XCharaRules for its timing and its numbers). Their client's G asks for it with the
 * one its crosshair looks at (xchara_skill_v1: the skill, then that one's entity id, -1 for none: the server then takes the
 * one its user's look meets); the server marks them if they may be (alive, near enough, in sight, fair game), and the "!!!"
 * is over them until {@link XCharaRules#WARN}. Its user is held where they stand all through it; struck, staggered,
 * incapacitated or caught by another's hold before WARN, it is called off. Any tick of the "!!!" the one marked holds a
 * guard up toward its user (CombatManager.guardTakes: a shield's sound and sparks), it is blocked. Either way its
 * cooldown runs from then.
 * <p>
 * Past WARN the two are in its strange space (the clients' to draw, client/xchara): both are held where they are
 * (KagenoManager.frozen) and nothing harms them but its own blows (shielded); whatever the one marked was doing stops.
 * Its blows land on the one marked where they stand, quietly but the first and the last ({@link XCharaRules#blowDamage});
 * every one taken, they are sent flying the way its user faced, and it is over: its cooldown runs from then
 * ({@link XCharaRules#COOLDOWN}). It ends early, its cooldown running, if its user or the one marked dies, leaves or is
 * gone (the one marked dying of its blows, it is simply over). Every viewer within 128 blocks of its user (and the two)
 * is told when it starts, when the strange space comes, and when it is blocked, called off or over
 * (xchara_skill_event_v1), with when it is ready again.
 */
public final class XCharaUltimate {
    public static final Identifier INPUT=CelestialFall.id("xchara_skill_v1"),EVENT=CelestialFall.id("xchara_skill_event_v1");
    /** It starts (the "!!!"); the two go into its strange space; a guard blocked it; it was called off; it is over. */
    public static final int START=0,SPACE=1,BLOCKED=2,CUT=3,END=4;
    /** At most this many presses a second are heard. */
    static final int RATE=20;
    /** One sword dance: its user and the one it marked, when G was pressed, the way its user faced them, where each stood. */
    static final class Dance {
        final ServerPlayerEntity user;final LivingEntity target;final ServerWorld world;final long start;final float yaw;final Vec3d from,targetFrom;
        /** Whether the two are in its strange space yet (past WARN), and what its blows have dealt. */
        boolean space;float dealt;
        Dance(ServerPlayerEntity user,LivingEntity target,long start,float yaw){
            this.user=user;this.target=target;world=user.getServerWorld();this.start=start;this.yaw=yaw;from=user.getPos();targetFrom=target.getPos();
        }
        long age(){return world.getTime()-start;}
    }
    private static final Map<UUID,Dance> BY_USER=new HashMap<>(),BY_TARGET=new HashMap<>();
    /** When each player's G is ready again (the server's world time). */
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static final Map<UUID,long[]> PRESSES=new HashMap<>();
    /** Set while one of its own blows is dealt (to the one marked): the only harm that reaches them. */
    private static final ThreadLocal<LivingEntity> BLOW=new ThreadLocal<>();
    private XCharaUltimate(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()<1||buf.readableBytes()>8)return;
            int skill=buf.readUnsignedByte();int target=buf.readableBytes()>0?buf.readVarInt():-1;
            // (2.115.0) Q: the hack on or off (XCharaHack)
            if(skill==Q){server.execute(()->{if(heard(player))XCharaHack.request(player);});return;}
            // (2.119.0) E: the restore, with the hack on (XCharaHeal)
            if(skill==Y){server.execute(()->{if(heard(player))XCharaHeal.request(player);});return;}
            // R: with the hack on the double (2.125.0: asked with the one in the crosshair, as G; the cleave was R's from
            // 2.121.0 and went to H), with it off the jab (2.124.0)
            if(skill==R){server.execute(()->{if(!heard(player))return;if(XCharaHack.on(player))XCharaClone.cast(player,target);else XCharaAttack.jab(player);});return;}
            // (2.125.0, user request: "Q활성화 R스킬을 Q활성화 H스킬로 옮겨") H with the hack on: the cleave; with it off, nothing yet
            if(skill==H){server.execute(()->{if(heard(player)&&XCharaHack.on(player))XCharaAttack.cleave(player);});return;}
            if(skill!=G)return;
            server.execute(()->{if(heard(player))cast(player,target);});
        });
        ServerTickEvents.END_SERVER_TICK.register(XCharaUltimate::tick);
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

    /** Whether this one is a sword dance's user now (any part of it: they stand held from the press). */
    public static boolean casting(LivingEntity e){return e!=null&&!e.getWorld().isClient&&BY_USER.containsKey(e.getUuid());}
    /** Whether this one is in a sword dance's strange space now, or after it until it is over (its user or the one marked). */
    public static boolean held(LivingEntity e){
        if(e==null||e.getWorld().isClient)return false;
        var d=BY_USER.get(e.getUuid());if(d==null)d=BY_TARGET.get(e.getUuid());
        return d!=null&&d.space;
    }
    /** Whether a sword dance has this one now: its user from the press, the one marked from the strange space on (until then they are free, to guard). */
    public static boolean involved(LivingEntity e){return casting(e)||held(e)||XCharaClone.involved(e);}
    /** Whether this one is marked by a sword dance's "!!!" now (not yet in its strange space). */
    public static boolean marked(LivingEntity e){var d=e==null||e.getWorld().isClient?null:BY_TARGET.get(e.getUuid());return d!=null&&!d.space||XCharaClone.marked(e);}
    /** Whether harm from {@code source} is kept from {@code e}: it is in a sword dance's strange space or after it, and this is not its own blow (what bypasses invulnerability, /kill or the void, still reaches them). */
    public static boolean shielded(LivingEntity e,DamageSource source){
        // (2.125.0) and one seized by their double, in the air until the wall (XCharaClone)
        if(XCharaClone.shielded(e,source))return true;
        return held(e)&&BLOW.get()!=e&&!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
    /** When this player's G is ready again (the server's world time; none is long ago). */
    public static long ready(PlayerEntity p){return READY.getOrDefault(p.getUuid(),Long.MIN_VALUE);}
    /** For a boss reading what comes at it (BossThreats): the sword dance marking {@code target} now, as {user, start tick}, or null. */
    public static Object[] marking(LivingEntity target){var d=target==null?null:BY_TARGET.get(target.getUuid());return d==null||d.space?null:new Object[]{d.user,d.start};}
    /** For the multiplayer check: {@code user}'s sword dance now, as {start, whether its strange space has come, what its blows dealt, the one marked's id}, or null. */
    public static Object[] describe(PlayerEntity user){var d=user==null?null:BY_USER.get(user.getUuid());return d==null?null:new Object[]{d.start,d.space,d.dealt,d.target.getId()};}
    /** Its cooldown is gone once X Chara is not theirs (as every other stand's short ones are, StandManager.forgetOthers). */
    public static void forget(ServerPlayerEntity p){READY.remove(p.getUuid());XCharaClone.forget(p);}

    /**
     * A blow landed on {@code target} (LivingEntity.damage let it through): a sword dance's user struck before the strange
     * space has it called off. Only a blow: harm from someone, or from something thrown; none from a fall, a fire or a
     * poison, nor their own (as a glowing cutscene skill's, CutsceneGlow.struck).
     */
    public static void struck(LivingEntity target,DamageSource source){
        XCharaClone.struck(target,source);
        var d=target==null?null:BY_USER.get(target.getUuid());
        if(d==null||d.space||d.user!=target)return;
        if(source.getAttacker()==null&&source.getSource()==null||source.getAttacker()==target)return;
        end(d,CUT);
    }

    // ───────────────────────────── the press ─────────────────────────────

    /** G pressed by {@code p} with {@code targetId} in their crosshair (-1 none): the "!!!" over the one marked, if it may be. Returns whether it began. */
    public static boolean cast(ServerPlayerEntity p,int targetId){
        if(!StandManager.xChara(p)||p.isSpectator()||!p.isAlive())return false;
        // (2.119.0, user request: "지금 G스킬은 Q 활성화 상태의 G스킬로 바꿔") the sword dance is the hack's G: with the hack
        // off, G is not this skill (nothing yet)
        if(!XCharaHack.on(p))return false;
        var world=p.getServerWorld();long now=world.getTime();
        if(involved(p)||marked(p)||now<ready(p)&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(CombatManager.busy(p)||CombatManager.cinematic(p)||CombatManager.actionLocked(p)||StarPlatinumTimeStop.holds(p)||StarPlatinumTimeStop.active(p))return false;
        if(p.hasVehicle()||p.isSleeping())return false;
        LivingEntity target=null;
        if(targetId>=0&&world.getEntityById(targetId) instanceof LivingEntity e&&fair(p,e))target=e;
        if(target==null)target=looked(p);
        if(target==null)return false;
        // a boss marked is a boss struck (it guards, if it can)
        dev.sangwoo.celestialfall.boss.Bosses.provoke(target,p);
        Vec3d to=target.getPos().subtract(p.getPos());
        float yaw=to.horizontalLengthSquared()<1e-6?MathHelper.wrapDegrees(p.getYaw()):(float)Math.toDegrees(Math.atan2(-to.x,to.z));
        var d=new Dance(p,target,now,yaw);
        BY_USER.put(p.getUuid(),d);BY_TARGET.put(target.getUuid(),d);
        // its user stops whatever they did and stands looking at the one marked
        p.setYaw(yaw);p.setHeadYaw(yaw);p.setBodyYaw(yaw);p.clearActiveItem();p.setSprinting(false);
        if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        KagenoManager.freeze(p);
        send(d,START,now);
        return true;
    }
    /**
     * Whether {@code e} may be marked by {@code p}: alive and to be struck, not a merchant, within AIM_RANGE (and a block
     * to spare) of their eyes, in their sight, fair game (a player only where they may harm each other), and in no one's
     * cutscene or stopped time.
     */
    static boolean fair(ServerPlayerEntity p,LivingEntity e){
        if(e==p||!e.isAlive()||e.isRemoved()||e.isSpectator()||!e.canHit()||e.getWorld()!=p.getWorld())return false;
        if(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)return false;
        if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))return false;
        Vec3d eye=p.getEyePos(),chest=e.getPos().add(0,e.getHeight()*.55,0);
        if(eye.squaredDistanceTo(chest)>(AIM_RANGE+1.5)*(AIM_RANGE+1.5))return false;
        if(p.getWorld().raycast(new RaycastContext(eye,chest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()!=HitResult.Type.MISS)return false;
        if(involved(e)||marked(e)||StarPlatinumBeatdown.involved(e)||StarPlatinumTimeStop.stopped(e)||StarPlatinumTimeStop.holds(e))return false;
        if(dev.sangwoo.celestialfall.isosang.IsosangAttack.casting(e)||dev.sangwoo.celestialfall.isosang.IsosangAttack.finaling(e))return false;
        if(e instanceof PlayerEntity q&&(dev.sangwoo.celestialfall.AtomicManager.immobilized(q)||dev.sangwoo.celestialfall.stand.StandArrow.stabbing(q)))return false;
        return true;
    }
    /** The one {@code p}'s look meets within AIM_RANGE (their box half a block bigger), if they may be marked. */
    static LivingEntity looked(ServerPlayerEntity p){
        Vec3d eye=p.getEyePos(),look=p.getRotationVec(1),end=eye.add(look.multiply(AIM_RANGE));
        var block=p.getWorld().raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        double reach=block.getType()==HitResult.Type.MISS?AIM_RANGE:block.getPos().distanceTo(eye);
        LivingEntity best=null;double near=reach;
        for(Entity e:p.getWorld().getOtherEntities(p,p.getBoundingBox().stretch(look.multiply(reach)).expand(1.5),x->x instanceof LivingEntity)){
            var hit=e.getBoundingBox().expand(.5).raycast(eye,eye.add(look.multiply(reach)));
            if(hit.isEmpty())continue;double d=hit.get().distanceTo(eye);
            if(d<near&&fair(p,(LivingEntity)e)){near=d;best=(LivingEntity)e;}
        }
        return best;
    }

    // ───────────────────────────── its course ─────────────────────────────

    private static void tick(MinecraftServer server){
        if(BY_USER.isEmpty())return;
        for(var d:new ArrayList<>(BY_USER.values())){
            long age=d.age();var u=d.user;var t=d.target;
            boolean userGone=u.isRemoved()||!u.isAlive()||u.getServerWorld()!=d.world||u.isSpectator()||!StandManager.xChara(u);
            boolean targetGone=t.isRemoved()||!t.isAlive()||t.getWorld()!=d.world||t.isSpectator();
            if(userGone||targetGone){end(d,CUT);continue;}
            if(!d.space){
                // the "!!!": its user held looking at the one marked; caught by anything that holds them, it is called off
                if(HitStun.stunned(u)||CombatManager.incapacitated(u)||StarPlatinumTimeStop.holds(u)||StarPlatinumBeatdown.involved(u)
                    ||dev.sangwoo.celestialfall.AtomicManager.immobilized(u)){end(d,CUT);continue;}
                KagenoManager.freeze(u);
                // a guard up toward its user, any tick of it, blocks it
                if(CombatManager.guardTakes(t,u.getPos(),true)){end(d,BLOCKED);continue;}
                if(age<WARN)continue;
                // the one marked caught by another's cutscene or stopped time meanwhile: it is called off
                if(StarPlatinumBeatdown.involved(t)||StarPlatinumTimeStop.stopped(t)||StarPlatinumTimeStop.holds(t)
                    ||dev.sangwoo.celestialfall.isosang.IsosangAttack.casting(t)||dev.sangwoo.celestialfall.isosang.IsosangAttack.finaling(t)
                    ||t instanceof PlayerEntity q&&dev.sangwoo.celestialfall.AtomicManager.immobilized(q)){end(d,CUT);continue;}
                space(d);
            }
            KagenoManager.freeze(u);KagenoManager.freeze(t);t.fallDistance=0;
            int k=blowIndex(age);
            if(k>=0)blow(d,k);
            if(!t.isAlive()){end(d,END);continue;}
            if(age>=LAUNCH){launch(d);continue;}
        }
    }
    /** Into the strange space: whatever the one marked was doing stops, and both are held from now. */
    private static void space(Dance d){
        d.space=true;var u=d.user;var t=d.target;
        CombatManager.interrupt(t);HitStun.release(t.getUuid());
        StarPlatinumRush.struck(u);StarPlatinumInhale.struck(u);
        if(t instanceof ServerPlayerEntity sp){
            StarPlatinumAttack.struck(sp);StarPlatinumRush.struck(sp);StarPlatinumInhale.struck(sp);StarPlatinumTimeStop.struck(sp);KagenoManager.disable(sp);
            sp.clearActiveItem();sp.setSprinting(false);if(sp.currentScreenHandler!=sp.playerScreenHandler)sp.closeHandledScreen();
        }
        if(t instanceof MobEntity mob){mob.getNavigation().stop();mob.setTarget(null);}
        KagenoManager.freeze(u);KagenoManager.freeze(t);
        send(d,SPACE,d.world.getTime());
    }
    /**
     * One of its blows on the one marked, where they stand: its share of the damage, past every guard but its own (they
     * cannot hold one up, held); quietly but the first and the last (the recording carries the rest), each counted a hit.
     */
    private static void blow(Dance d,int k){
        var t=d.target;var source=d.world.getDamageSources().playerAttack(d.user);
        float amount=blowDamage(k);boolean quietly=k>0&&k<HITS-1;
        LivingEntity previous=BLOW.get();BLOW.set(t);
        try{
            t.timeUntilRegen=0;
            boolean landed=quietly?CombatManager.damageQuietly(t,source,amount):CombatManager.damageWithoutKnockback(t,source,amount);
            if(landed)d.dealt+=amount;
        }finally{if(previous==null)BLOW.remove();else BLOW.set(previous);}
        // (its user's own damage numbers are their client's, blow by blow on its clock, as the rush's: XCharaClient)
        d.user.onAttacking(t);
    }
    /** Every blow taken: the one marked is sent flying the way its user faced them, and it is over. */
    private static void launch(Dance d){
        end(d,END);
        var t=d.target;if(!t.isAlive()||t.isRemoved())return;
        Vec3d f=Vec3d.fromPolar(0,d.yaw);
        t.setVelocity(f.x*FLY,FLY_UP,f.z*FLY);t.velocityModified=true;t.fallDistance=0;
    }
    /** It is over (done), blocked or called off: neither is held any more, and its cooldown runs from now. */
    private static void end(Dance d,int type){
        BY_USER.remove(d.user.getUuid());if(BY_TARGET.get(d.target.getUuid())==d)BY_TARGET.remove(d.target.getUuid());
        long now=d.world.getTime();
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(d.user))READY.put(d.user.getUuid(),now+COOLDOWN);else READY.remove(d.user.getUuid());
        // cut short, its user's hit count is held open no longer
        if(type!=END)dev.sangwoo.celestialfall.combat.HitCounter.release(d.user);
        send(d,type,now);
    }
    /** Tells every player near its user (and the two) of the event. */
    private static void send(Dance d,int type,long now){
        var u=d.user;
        for(var viewer:d.world.getPlayers()){
            if(viewer!=u&&viewer!=d.target&&viewer.squaredDistanceTo(u)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var buf=PacketByteBufs.create();
            buf.writeUuid(u.getUuid());buf.writeIdentifier(d.world.getRegistryKey().getValue());buf.writeByte(type);
            buf.writeLong(d.start);buf.writeLong(now);buf.writeVarInt(d.target.getId());buf.writeUuid(d.target.getUuid());
            vec(buf,d.from);vec(buf,d.targetFrom);buf.writeFloat(d.yaw);buf.writeLong(ready(u));
            ServerPlayNetworking.send(viewer,EVENT,buf);
        }
    }
    private static void vec(net.minecraft.network.PacketByteBuf buf,Vec3d v){buf.writeDouble(v.x);buf.writeDouble(v.y);buf.writeDouble(v.z);}
}
