package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.BlinkRules.*;

/**
 * X Chara's Z, the teleport (2.138.0; BlinkRules for its timing and its numbers). Their client's Z asks for it
 * (xchara_skill_v1 with {@link XCharaRules#Z}: XCharaUltimate takes the press), the hack on or off alike. From the
 * press their body flickers purple on every client; at {@link BlinkRules#GO} they are put up to
 * {@link BlinkRules#REACH} blocks on, level, the way they looked as they pressed ({@link #goes}): short of a block in
 * the way (their whole body's way: no wall is gone through), on the floor there (a low step up, a drop), or as high as they are with none under them. Until then they
 * walk as ever and a blow does not call it off (the user's answer); nothing else of theirs begins meanwhile
 * ({@link #casting}: CombatManager.busy). Held where they are as it would move them (a cutscene, stopped time, another's
 * hold), it is called off. Its cooldown runs from the move, or from when it was called off.
 * <p>
 * Every viewer within 128 blocks of where they stood or stand is told of each step (xchara_blink_event_v1), the move
 * with where from and where to.
 */
public final class XCharaBlink {
    public static final Identifier EVENT=CelestialFall.id("xchara_blink_event_v1");
    /** It starts (the flicker); they are moved; it was called off. */
    public static final int START=0,GONE=1,CUT=2;
    /** One on its way: its user, when Z was pressed, the way they looked then. */
    static final class Cast {
        final ServerPlayerEntity user;final ServerWorld world;final long start;final float yaw;
        Vec3d from,to;
        Cast(ServerPlayerEntity user,long start,float yaw){this.user=user;world=user.getServerWorld();this.start=start;this.yaw=yaw;from=to=user.getPos();}
        long age(){return world.getTime()-start;}
    }
    private static final Map<UUID,Cast> CASTS=new HashMap<>();
    /** When each player's is ready again (the server's world time). */
    private static final Map<UUID,Long> READY=new HashMap<>();
    /** For the multiplayer check: each player's last, as {how it ended, the tick, from x y z, to x y z}. */
    private static final Map<UUID,double[]> LAST=new HashMap<>();
    private XCharaBlink(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaBlink::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var d=CASTS.get(p.getUuid());
            if(d!=null)end(d,CUT);
            LAST.remove(p.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{CASTS.clear();READY.clear();LAST.clear();});
    }

    /** Whether this one's teleport is on its way now (pressed, not yet moved): nothing else of theirs begins. */
    public static boolean casting(LivingEntity e){return e!=null&&!e.getWorld().isClient&&CASTS.containsKey(e.getUuid());}
    /** When this player's is ready again (the server's world time; none is long ago). */
    public static long ready(PlayerEntity p){return READY.getOrDefault(p.getUuid(),Long.MIN_VALUE);}
    /** Its cooldown is gone once X Chara is not theirs (StandManager.forgetOthers). */
    public static void forget(ServerPlayerEntity p){READY.remove(p.getUuid());}
    /** For the multiplayer check: {@code user}'s last as {how it ended, the tick, from x y z, to x y z}, or null. */
    public static double[] last(PlayerEntity user){return user==null?null:LAST.get(user.getUuid());}

    /** Z pressed by {@code p}: the teleport on its way, if it may be. Returns whether it began. */
    public static boolean cast(ServerPlayerEntity p){
        if(!StandManager.xChara(p)||p.isSpectator()||!p.isAlive())return false;
        var world=p.getServerWorld();long now=world.getTime();
        if(CASTS.containsKey(p.getUuid())||XCharaUltimate.involved(p)||now<ready(p)&&!NoCooldownState.enabled(p))return false;
        if(CombatManager.busy(p)||CombatManager.cinematic(p)||CombatManager.actionLocked(p)||StarPlatinumTimeStop.holds(p)||StarPlatinumTimeStop.active(p))return false;
        if(p.hasVehicle()||p.isSleeping())return false;
        // (nor while a skill of their knife's motion runs on past its hold, nor through their restore)
        if(XCharaAttack.cleaving(p)||XCharaHeal.busy(p))return false;
        var d=new Cast(p,now,MathHelper.wrapDegrees(p.getYaw()));
        CASTS.put(p.getUuid(),d);
        p.clearActiveItem();
        if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        // (the knife's combo ends with it, as with their other skills: no rest added)
        XCharaAttack.skillUsed(p);
        send(d,START,now);
        return true;
    }

    private static void tick(MinecraftServer server){
        for(var d:new ArrayList<>(CASTS.values())){
            var u=d.user;
            if(u.isRemoved()||!u.isAlive()||u.getServerWorld()!=d.world||u.isSpectator()||!StandManager.xChara(u)){end(d,CUT);continue;}
            if(d.age()<GO)continue;
            // one whom something holds where they are is not moved out of it
            if(XCharaPull.caught(u)){end(d,CUT);continue;}
            d.from=u.getPos();d.to=goes(u,d.yaw);
            if(u.hasVehicle())u.stopRiding();
            if(d.to.squaredDistanceTo(d.from)>1e-6){
                // (looking as they look: the turn is their client's. The look asked for is their own with the turn left
                // relative, so the packet's turn is none; asked for 0 it would turn them, and their client, to the south)
                u.networkHandler.requestTeleport(d.to.x,d.to.y,d.to.z,u.getYaw(),u.getPitch(),EnumSet.of(PositionFlag.X_ROT,PositionFlag.Y_ROT));
                u.setVelocity(Vec3d.ZERO);u.fallDistance=0;u.velocityModified=true;
            }
            end(d,GONE);
        }
    }
    /**
     * Where {@code p} is put for a teleport the way {@code yaw}: level from their feet, as far as their whole body goes
     * with no block in it ({@link #room}: as wide and as tall as they are, looked at all along the way, so no wall and
     * no corner of one is gone through; 2.138.1), REACH at most and SHORT of what stops it; on the floor there (a step
     * of UP up, a drop of DOWN), else as high as they are if they fit there; with no place for them, nearer by NEARER
     * at a time; none at all, where they stand.
     */
    static Vec3d goes(ServerPlayerEntity p,float yaw){
        var world=p.getServerWorld();double[] w=way(yaw);Vec3d f=new Vec3d(w[0],0,w[1]),at=p.getPos();
        var dims=p.getDimensions(EntityPose.STANDING);double half=dims.width/2;
        // (on the ground a low step in the way is gone over, as walking goes over it: the body is looked at from that high)
        double low=p.isOnGround()?UP:0;
        double free=clearAlong(far->room(world,p,dims.getBoxAt(at.x+f.x*far,at.y,at.z+f.z*far).contract(1e-3,0,1e-3).withMinY(at.y+low+1e-3)),REACH+SHORT-half);
        for(double far=reach(free+half);far>=NEARER*.5;far-=NEARER){
            double x=at.x+f.x*far,z=at.z+f.z*far;
            // (in the air as it moves them, the floor is theirs only if it is at their feet: they are not put down a drop)
            double y=SlamGround.floor(world,p,x,z,at.y+UP,at.y-(p.isOnGround()?DOWN:.1));
            if(!Double.isNaN(y)&&room(world,p,dims.getBoxAt(x,y+.001,z).contract(1e-3,0,1e-3)))return new Vec3d(x,y,z);
            if(room(world,p,dims.getBoxAt(x,at.y,z).contract(1e-3,0,1e-3)))return new Vec3d(x,at.y,z);
        }
        return at;
    }
    /** Whether a body has room in {@code box}: no block's shape in it, nor a cube of ground a terrain delete lifted (bodies it goes through). */
    private static boolean room(ServerWorld world,ServerPlayerEntity p,Box box){
        if(world.getBlockCollisions(p,box).iterator().hasNext())return false;
        if(EraseCubes.any(world))for(var c:EraseCubes.cubes(world)){var b=c.box();if(b!=null&&b.intersects(box))return false;}
        return true;
    }
    /** It moved them or was called off: its cooldown runs from now. */
    private static void end(Cast d,int type){
        CASTS.remove(d.user.getUuid());
        long now=d.world.getTime();
        if(!NoCooldownState.enabled(d.user))READY.put(d.user.getUuid(),BlinkRules.ready(now));else READY.remove(d.user.getUuid());
        LAST.put(d.user.getUuid(),new double[]{type,now,d.from.x,d.from.y,d.from.z,d.to.x,d.to.y,d.to.z});
        send(d,type,now);
    }
    /**
     * Tells every player near (within 128 blocks of where its user stood or stands, and its user): whose, which event,
     * when it began and the server's time now, the way they looked, where from and where to (the move's), and when
     * theirs is ready again.
     */
    private static void send(Cast d,int type,long now){
        var user=d.user;
        for(var viewer:d.world.getPlayers()){
            if(viewer!=user&&viewer.squaredDistanceTo(d.from)>128*128&&viewer.squaredDistanceTo(d.to)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var buf=PacketByteBufs.create();
            buf.writeUuid(user.getUuid());buf.writeVarInt(user.getId());buf.writeIdentifier(d.world.getRegistryKey().getValue());buf.writeByte(type);
            buf.writeLong(d.start);buf.writeLong(now);buf.writeFloat(d.yaw);
            buf.writeDouble(d.from.x);buf.writeDouble(d.from.y);buf.writeDouble(d.from.z);
            buf.writeDouble(d.to.x);buf.writeDouble(d.to.y);buf.writeDouble(d.to.z);
            buf.writeLong(ready(user));
            ServerPlayNetworking.send(viewer,EVENT,buf);
        }
    }
}
