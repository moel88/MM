package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * The knives of X Chara's Y with the hack off in flight (2.130.0; ShotRules for the timing and the reach, XCharaAttack
 * for the skill they are let go by). A knife let go flies straight from where it hung, the way it was sent, on its own:
 * it goes on whatever becomes of the skill or of its user's stance (not of its user: dead, gone or X Chara's no more,
 * their knives are gone). Every tick the server looks over the stretch its point comes to next (ShotRules.swept: a
 * tick ahead of where the clients draw it) for the first body it may strike; that one takes its damage (a guard takes
 * it instead; no knockback; a basic attack's stagger) and the knife is done. A block ends it where it stands (found as
 * it is let go). Every viewer is told as each goes (where from, which way, how far it can) and whom each struck and
 * how far along (xchara_shot_event_v1); the clients draw the knives (client/xchara/BladeShot).
 * <p>
 * (2.131.0) The three small knives of their T with the hack off fly the same way, by measures of their own
 * ({@link #KNIVES}, KnivesRules: faster, from the hand that threw them, and no guard takes them); the clients tell the
 * two apart by the skill a knife is of (client/xchara/CorruptionKnives draws these).
 */
public final class XCharaShot {
    public static final Identifier EVENT=CelestialFall.id("xchara_shot_event_v1");
    /** A knife is let go; a knife struck someone. */
    public static final int FIRED=0,STRUCK=1;
    /** A kind of knife by its measures: how many, where each's point is as it goes and how fast and far it flies, how near a body it strikes, its damage, whether a guard takes it, and how long it staggers (the last's). */
    record Kind(int count,double tip,double speed,double range,double girth,float damage,boolean guarded,int stagger,int staggerLast){
        /** The stretch of its way looked over {@code n} ticks after it went (ShotRules.swept). */
        double[] swept(int n){return new double[]{n<=0?0:tip+speed*n,tip+speed*(n+1)};}
    }
    /** Y's four (ShotRules) and (2.131.0) T's three small ones (KnivesRules). */
    static final Kind SHOT=new Kind(ShotRules.BLADES,ShotRules.TIP,ShotRules.SPEED,ShotRules.RANGE,ShotRules.GIRTH,ShotRules.DAMAGE,true,ShotRules.STAGGER,ShotRules.STAGGER_LAST),
        KNIVES=new Kind(KnivesRules.KNIVES,KnivesRules.TIP,KnivesRules.SPEED,KnivesRules.RANGE,KnivesRules.GIRTH,KnivesRules.DAMAGE,false,KnivesRules.STAGGER,KnivesRules.STAGGER_LAST);
    private static final class Flight {
        final UUID owner,id;final ServerWorld world;final Kind kind;final int k;final Vec3d from,dir;final double reach;final long fired;final float yaw;
        Flight(ServerPlayerEntity p,UUID id,Kind kind,int k,Vec3d from,Vec3d dir,double reach,long fired){
            owner=p.getUuid();this.id=id;world=p.getServerWorld();this.kind=kind;this.k=k;this.from=from;this.dir=dir;this.reach=reach;this.fired=fired;
            yaw=(float)Math.toDegrees(Math.atan2(-dir.x,dir.z));
        }
    }
    private static final List<Flight> FLIGHTS=new ArrayList<>();
    private XCharaShot(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaShot::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->FLIGHTS.clear());
    }
    private static double[] arr(Vec3d v){return new double[]{v.x,v.y,v.z};}
    /** Whom {@code p}'s knife may strike at all: alive, to be struck, no merchant, and (a player) one they may harm. */
    private static boolean foe(ServerPlayerEntity p,LivingEntity e){
        return e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&!(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other));
    }
    /** Where {@code p}'s look meets the world now: the first block or body within the knives' range of their eyes, or as far out as that. */
    public static Vec3d looked(ServerPlayerEntity p){return looked(p,ShotRules.RANGE);}
    /** As {@link #looked(ServerPlayerEntity)}, within {@code range} of their eyes. */
    public static Vec3d looked(ServerPlayerEntity p,double range){
        Vec3d eye=p.getEyePos(),look=p.getRotationVec(1),end=eye.add(look.multiply(range));
        var block=p.getWorld().raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(block.getType()!=HitResult.Type.MISS)end=block.getPos();
        Vec3d best=end;double near=eye.squaredDistanceTo(end);
        for(var e:p.getServerWorld().getEntitiesByClass(LivingEntity.class,new Box(eye,end).expand(1.5),e->foe(p,e))){
            var hit=e.getBoundingBox().expand(ShotRules.GIRTH).raycast(eye,end);
            if(hit.isPresent()&&eye.squaredDistanceTo(hit.get())<near){near=eye.squaredDistanceTo(hit.get());best=hit.get();}
        }
        return best;
    }
    /**
     * Knife {@code k} of {@code p}'s skill {@code id} is let go at {@code want}: from where it hung (for one who stood
     * at {@code stood} facing {@code yaw}), straight at it, as far as its range or the first block.
     */
    static void fire(ServerPlayerEntity p,UUID id,int k,Vec3d stood,float yaw,Vec3d want){
        fire(p,id,SHOT,k,ShotRules.spot(arr(stood),yaw,k),stood,want);
    }
    /** (2.131.0) Small knife {@code k} of {@code p}'s throw {@code id} leaves the hand at {@code want}: from where the hand threw it (KnivesRules.spot), straight at it. */
    static void throwKnife(ServerPlayerEntity p,UUID id,int k,Vec3d stood,float yaw,Vec3d want){
        fire(p,id,KNIVES,k,KnivesRules.spot(arr(stood),yaw,k),stood,want);
    }
    private static void fire(ServerPlayerEntity p,UUID id,Kind kind,int k,double[] s,Vec3d stood,Vec3d want){
        var world=p.getServerWorld();long now=world.getTime();
        double[] w=ShotRules.way(s,arr(stood.add(0,p.getStandingEyeHeight(),0)),arr(want));
        Vec3d from=new Vec3d(s[0],s[1],s[2]),dir=new Vec3d(w[0],w[1],w[2]);
        var block=world.raycast(new RaycastContext(from,from.add(dir.multiply(kind.range)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        double reach=block.getType()==HitResult.Type.MISS?kind.range:block.getPos().distanceTo(from);
        var f=new Flight(p,id,kind,k,from,dir,reach,now);
        for(var viewer:viewers(p,null)){
            var b=head(f,FIRED);
            b.writeDouble(from.x);b.writeDouble(from.y);b.writeDouble(from.z);b.writeDouble(dir.x);b.writeDouble(dir.y);b.writeDouble(dir.z);b.writeDouble(reach);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
        // (as it goes: whoever stands in it already, and the stretch its point comes to next)
        if(!sweep(p,f,0))FLIGHTS.add(f);
    }
    private static net.minecraft.network.PacketByteBuf head(Flight f,int type){
        var b=PacketByteBufs.create();
        b.writeUuid(f.owner);b.writeIdentifier(f.world.getRegistryKey().getValue());b.writeByte(type);b.writeUuid(f.id);b.writeByte(f.k);
        return b;
    }
    private static List<ServerPlayerEntity> viewers(ServerPlayerEntity p,LivingEntity also){
        var out=new ArrayList<ServerPlayerEntity>();
        for(var viewer:p.getServerWorld().getPlayers()){
            if(viewer!=p&&viewer!=also&&viewer.squaredDistanceTo(p)>128*128)continue;
            if(ServerPlayNetworking.canSend(viewer,EVENT))out.add(viewer);
        }
        return out;
    }
    private static void tick(MinecraftServer server){
        for(var it=FLIGHTS.iterator();it.hasNext();){
            var f=it.next();var p=server.getPlayerManager().getPlayer(f.owner);
            if(p==null||p.getServerWorld()!=f.world||!p.isAlive()||!StandManager.xChara(p)){it.remove();continue;}
            int n=(int)(f.world.getTime()-f.fired);
            if(n<=0)continue;
            if(sweep(p,f,n))it.remove();
        }
    }
    /** The stretch of knife {@code f}'s way {@code n} ticks after it went: the first body in it is struck. Returns whether the knife is done (it struck, or it has gone as far as it can). */
    private static boolean sweep(ServerPlayerEntity p,Flight f,int n){
        double[] st=f.kind.swept(n);double a=Math.min(st[0],f.reach),b=Math.min(st[1],f.reach);
        if(b>a+1e-6||n==0){
            Vec3d pa=f.from.add(f.dir.multiply(a)),pb=f.from.add(f.dir.multiply(b));
            LivingEntity first=null;double at=Double.MAX_VALUE;
            for(var e:f.world.getEntitiesByClass(LivingEntity.class,new Box(pa,pb).expand(f.kind.girth+1),e->foe(p,e))){
                var box=e.getBoundingBox().expand(f.kind.girth);double d;
                if(box.contains(pa))d=a;
                else{var hit=box.raycast(pa,pb);if(hit.isEmpty())continue;d=a+hit.get().distanceTo(pa);}
                if(d<at){at=d;first=e;}
            }
            if(first!=null){strike(p,f,first,at);return true;}
        }
        return st[1]>=f.reach;
    }
    /** Knife {@code f} strikes {@code e}, {@code far} along its way: its damage (a guard takes it), no knockback, a basic attack's stagger. */
    private static void strike(ServerPlayerEntity p,Flight f,LivingEntity e,double far){
        long now=f.world.getTime();var kind=f.kind;
        // (2.131.0: no guard takes a small knife; theirs is down as it comes)
        if(!kind.guarded)CombatManager.interrupt(e);
        e.timeUntilRegen=0;
        boolean took=CombatManager.damageWithoutKnockback(e,f.world.getDamageSources().playerAttack(p),kind.damage);
        if(took){
            if(!KagenoManager.frozen(e)||StarPlatinumTimeStop.stopped(e))HitStun.stagger(p,e,1,now+(f.k+1<kind.count?kind.stagger:kind.staggerLast));
            // the user's own hit feedback (the damage number and the blood), as the other characters'
            if(!StarPlatinumTimeStop.deferFeedback(p,e,kind.damage,f.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(kind.damage);b.writeFloat(f.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
            p.onAttacking(e);p.addExhaustion(.1f);
        }
        for(var viewer:viewers(p,e)){
            var b=head(f,STRUCK);b.writeInt(e.getId());b.writeDouble(far);b.writeBoolean(took);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    /** For the multiplayer check: how many of {@code p}'s knives are in flight now. */
    public static int flying(PlayerEntity p){int n=0;for(var f:FLIGHTS)if(f.owner.equals(p.getUuid()))n++;return n;}
}
