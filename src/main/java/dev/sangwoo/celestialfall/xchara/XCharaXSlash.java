package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * The X of X Chara's H with the hack off in flight, and the X it leaves on the ground (2.134.0; XSlashRules for the
 * timing and the reach, XCharaAttack for the skill that lets it go). Let go, it flies level the way its user faced, on
 * its own: whatever becomes of the skill or of its user's stance it goes on, and so does the X on the ground (not if
 * its user is dead, gone or X Chara's no more: then both are gone). Every tick the server looks over the stretch it
 * comes to next (XSlashRules.swept: a tick ahead of where the clients draw it) for everyone in its way not struck by it
 * yet; each takes its damage (no guard takes it; no knockback, no stagger) and is glitched (CombatManager.seal: no guard
 * for XSlashRules.SEAL ticks). A wall ends its flight where it stands (found as it goes). As the X on the ground bursts,
 * whoever stands on it takes the burst's damage (no guard takes that either). Every viewer is told as it goes (from
 * where, which way, how far), whom it and its burst struck (xchara_xslash_event_v1), and when one is gone early; the
 * clients draw it (client/xchara/XSlash).
 */
public final class XCharaXSlash {
    public static final Identifier EVENT=CelestialFall.id("xchara_xslash_event_v1");
    /** It is let go; it or its burst struck someone; it is gone before its time (its user dead, gone or X Chara's no more). */
    public static final int FIRED=0,STRUCK=1,GONE=2;
    /** Whom a strike was of: the X going over them, or the X on the ground bursting under them. */
    public static final int BY_X=0,BY_MARK=1;
    private static final class Slash {
        final UUID owner,id;final ServerWorld world;final Vec3d from;final float yaw;final double reach;final long fired;
        final Vec3d f,right;
        final Set<UUID> struck=new HashSet<>();
        Slash(ServerPlayerEntity p,UUID id,Vec3d from,float yaw,double reach,long fired){
            owner=p.getUuid();this.id=id;world=p.getServerWorld();this.from=from;this.yaw=yaw;this.reach=reach;this.fired=fired;
            f=Vec3d.fromPolar(0,yaw);right=new Vec3d(-f.z,0,f.x);
        }
        /** The tick into its skill this is (its skill began FIRE ticks before it was let go). */
        long age(){return world.getTime()-fired+XSlashRules.FIRE;}
    }
    private static final List<Slash> SLASHES=new ArrayList<>();
    private XCharaXSlash(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaXSlash::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->SLASHES.clear());
    }
    /** Whom {@code p}'s X may strike at all: alive, to be struck, no merchant, and (a player) one they may harm. */
    private static boolean foe(ServerPlayerEntity p,LivingEntity e){
        return e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&!(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other));
    }
    /** How far ahead of {@code from} along {@code f} the X can fly before a wall stops it (blocks: RANGE with none). */
    static double reach(ServerWorld world,ServerPlayerEntity p,Vec3d from,Vec3d f){
        Vec3d a=from.add(0,XSlashRules.BLOCK_UP,0);
        var hit=world.raycast(new RaycastContext(a,a.add(f.multiply(XSlashRules.RANGE)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(hit.getType()==HitResult.Type.MISS)return XSlashRules.RANGE;
        return Math.max(XSlashRules.NEAR+.5,Math.min(XSlashRules.RANGE,hit.getPos().distanceTo(a)));
    }
    /** {@code p}'s skill {@code id} lets its X go: from where they stood ({@code from}, facing {@code yaw}), as far as it can go. */
    static void fire(ServerPlayerEntity p,UUID id,Vec3d from,float yaw){
        var world=p.getServerWorld();long now=world.getTime();
        var s=new Slash(p,id,from,yaw,reach(world,p,from,Vec3d.fromPolar(0,yaw)),now);double reach=s.reach;
        for(var viewer:viewers(world,p,from,null)){
            var b=head(s,FIRED);
            b.writeDouble(from.x);b.writeDouble(from.y);b.writeDouble(from.z);b.writeFloat(yaw);b.writeDouble(reach);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
        sweep(p,s,0);
        SLASHES.add(s);
    }
    private static net.minecraft.network.PacketByteBuf head(Slash s,int type){
        var b=PacketByteBufs.create();
        b.writeUuid(s.owner);b.writeIdentifier(s.world.getRegistryKey().getValue());b.writeByte(type);b.writeUuid(s.id);
        return b;
    }
    /** Everyone within 128 blocks of its user (null: gone) or of where it went, and {@code also}. */
    private static List<ServerPlayerEntity> viewers(ServerWorld world,ServerPlayerEntity p,Vec3d from,LivingEntity also){
        var out=new ArrayList<ServerPlayerEntity>();
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer!=also&&(p==null||viewer.squaredDistanceTo(p)>128*128)&&viewer.squaredDistanceTo(from)>128*128)continue;
            if(ServerPlayNetworking.canSend(viewer,EVENT))out.add(viewer);
        }
        return out;
    }
    private static void tick(MinecraftServer server){
        for(var it=SLASHES.iterator();it.hasNext();){
            var s=it.next();var p=server.getPlayerManager().getPlayer(s.owner);
            if(p==null||p.getServerWorld()!=s.world||!p.isAlive()||!StandManager.xChara(p)){
                it.remove();
                for(var viewer:viewers(s.world,p!=null&&p.getServerWorld()==s.world?p:null,s.from,null))ServerPlayNetworking.send(viewer,EVENT,head(s,GONE));
                continue;
            }
            int n=(int)(s.world.getTime()-s.fired);
            if(n>0&&n<=Math.ceil(XSlashRules.flight(s.reach))+1)sweep(p,s,n);
            if(s.age()>=XSlashRules.burstAt(s.reach)){burst(p,s);it.remove();}
        }
    }
    /** The stretch of {@code s}'s way {@code n} ticks after it went: everyone in it not struck by it yet is struck. */
    private static void sweep(ServerPlayerEntity p,Slash s,int n){
        double[] st=XSlashRules.swept(n);double a=Math.min(st[0],s.reach),b=Math.min(st[1],s.reach);
        if(b<=a+1e-6&&n>0)return;
        Vec3d pa=s.from.add(s.f.multiply(a)),pb=s.from.add(s.f.multiply(b));
        var box=new Box(pa,pb).expand(XSlashRules.HALF+1.5,0,XSlashRules.HALF+1.5).stretch(0,XSlashRules.HIGH+1,0).stretch(0,XSlashRules.LOW-1,0);
        for(var e:s.world.getEntitiesByClass(LivingEntity.class,box,e->foe(p,e)&&!s.struck.contains(e.getUuid()))){
            Vec3d d=e.getPos().subtract(s.from);
            double fw=d.dotProduct(s.f),side=d.dotProduct(s.right);
            if(!XSlashRules.inWay(fw,side,d.y,e.getWidth()*.5,a,b))continue;
            // (not through a wall: from the X's own middle where it is level with them)
            Vec3d mid=s.from.add(s.f.multiply(Math.max(a,Math.min(b,fw)))).add(0,1.2,0),chest=e.getPos().add(0,e.getHeight()*.55,0);
            if(s.world.raycast(new RaycastContext(mid,chest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            s.struck.add(e.getUuid());
            strike(p,s,e,BY_X,XSlashRules.DAMAGE);
        }
    }
    /** The X on the ground bursts: everyone standing on it is struck. */
    private static void burst(ServerPlayerEntity p,Slash s){
        double far=s.reach+XSlashRules.BAND+2;
        Vec3d mid=s.from.add(s.f.multiply(far*.5));
        var box=new Box(mid,mid).expand(far*.5+XSlashRules.HALF+2,0,far*.5+XSlashRules.HALF+2).stretch(0,XSlashRules.MARK_HIGH+1,0).stretch(0,XSlashRules.MARK_LOW-1,0);
        for(var e:s.world.getEntitiesByClass(LivingEntity.class,box,e->foe(p,e))){
            Vec3d d=e.getPos().subtract(s.from);
            if(!XSlashRules.onMark(d.dotProduct(s.f),d.dotProduct(s.right),d.y,e.getWidth()*.5,s.reach))continue;
            strike(p,s,e,BY_MARK,XSlashRules.BURST_DAMAGE);
        }
    }
    /**
     * {@code e} is struck by {@code s} ({@code by}: its X, or its burst): no guard takes it (theirs is down as it
     * comes), {@code amount}, no knockback; the X's glitches them (no guard for SEAL ticks).
     */
    private static void strike(ServerPlayerEntity p,Slash s,LivingEntity e,int by,float amount){
        CombatManager.interrupt(e);
        e.timeUntilRegen=0;
        boolean took=CombatManager.damageWithoutKnockback(e,s.world.getDamageSources().playerAttack(p),amount);
        long until=0;
        if(took){
            if(by==BY_X){CombatManager.seal(e,XSlashRules.SEAL);until=s.world.getTime()+XSlashRules.SEAL;}
            // the user's own hit feedback (the damage number and the blood), as the other characters'
            if(!StarPlatinumTimeStop.deferFeedback(p,e,amount,s.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(amount);b.writeFloat(s.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
            p.onAttacking(e);p.addExhaustion(.1f);
        }
        for(var viewer:viewers(s.world,p,s.from,e)){
            var b=head(s,STRUCK);b.writeByte(by);b.writeInt(e.getId());b.writeUuid(e.getUuid());b.writeBoolean(took);b.writeLong(s.world.getTime());b.writeLong(until);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    /**
     * For a boss reading what comes at it (BossThreats): {@code p}'s X in flight and on the ground, each as {from x,
     * y, z, yaw, reach, the tick it was let go, the tick its X on the ground bursts}.
     */
    public static List<double[]> going(PlayerEntity p){
        var out=new ArrayList<double[]>();
        for(var s:SLASHES)if(s.owner.equals(p.getUuid()))out.add(new double[]{s.from.x,s.from.y,s.from.z,s.yaw,s.reach,s.fired,s.fired-XSlashRules.FIRE+XSlashRules.burstAt(s.reach)});
        return out;
    }
    /** For the checks: how many of {@code p}'s X are out now, and whom each has struck as "reach:n". */
    public static String describe(PlayerEntity p){
        var sb=new StringBuilder();
        for(var s:SLASHES)if(s.owner.equals(p.getUuid())){if(sb.length()>0)sb.append(' ');sb.append(String.format(java.util.Locale.ROOT,"%.1f:%d",s.reach,s.struck.size()));}
        return sb.toString();
    }
}
