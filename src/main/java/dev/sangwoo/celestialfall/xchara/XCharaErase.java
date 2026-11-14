package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * X Chara's delete slash as it goes (2.133.0; EraseRules for how far, how fast and how wide; XCharaAttack for the leap
 * that sends it, H with the hack off). Sent at the leap's top, it goes along the ground the way its user faced as they
 * pressed H, from where they stood, as far as {@link #reach} lets it; each tick it strikes everyone its front came over
 * (once each): a foe takes EraseRules.DAMAGE (no guard takes it, minecraft:bypasses_shield; no knockback; its user the
 * attacker) and is deleted for EraseRules.VANISH ticks ({@link #deleted}: nothing harms them, XCharaUltimate.shielded; they
 * can do nothing and their legs are stopped, an incapacitation). It is gone with its user (dead, left, elsewhere, X
 * Chara's no more). Every viewer within 128 blocks of where it went (and its user, and whom it deletes) is told as it goes,
 * of everyone it deletes (and until when), and if it is cut short (xchara_erase_event_v1); the clients draw it
 * (client/xchara/EraseClient, EraseFx).
 */
public final class XCharaErase {
    public static final Identifier EVENT=CelestialFall.id("xchara_erase_event_v1");
    /** It goes; it deleted someone; it was cut short. */
    public static final int WAVE=0,DELETED=1,CUT=2;
    /** Its harm: no guard takes it (minecraft:bypasses_shield), its user the attacker. */
    public static final RegistryKey<DamageType> DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("erase"));
    /** How far its news carries (blocks from where it went). */
    static final double VIEW=128;
    /** One slash as it goes: whose (its call's id its own), where from, the way, how far it goes, when it went, and whom it has come over. */
    static final class Wave {
        final UUID id,owner;final ServerWorld world;final Vec3d from;final float yaw;final double range;final long went;
        final Set<UUID> over=new HashSet<>();
        Wave(ServerPlayerEntity p,UUID id,Vec3d from,float yaw,double range,long went){this.id=id;owner=p.getUuid();world=p.getServerWorld();this.from=from;this.yaw=yaw;this.range=range;this.went=went;}
        long age(){return world.getTime()-went;}
    }
    private static final List<Wave> WAVES=new ArrayList<>();
    /** Who is deleted, until when (the server's time). */
    private static final Map<UUID,Long> GONE=new HashMap<>();
    private XCharaErase(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaErase::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{WAVES.clear();GONE.clear();});
    }

    /** {@code p}'s slash goes now (its call's id {@code id}), from {@code from} the way {@code yaw}. */
    static void start(ServerPlayerEntity p,UUID id,Vec3d from,float yaw){
        var w=new Wave(p,id,from,yaw,reach(p.getServerWorld(),p,from,yaw),p.getServerWorld().getTime());
        WAVES.add(w);
        send(w,WAVE,null,0,0);
    }
    /**
     * How far {@code p}'s slash from {@code from} the way {@code yaw} goes: EraseRules.RANGE, or as far as the first wall
     * across its middle (a solid block both EraseRules.WALL_LOW and WALL_HIGH over their feet: a low step does not stop it)
     * that is a wall across it ({@link #wide}: one pillar or a tree's trunk in its middle does not stop a slash sixteen
     * blocks across; it goes on past it).
     */
    static double reach(ServerWorld world,ServerPlayerEntity p,Vec3d from,float yaw){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        double at=0;
        for(int i=0;i<EraseRules.WALL_LOOKS&&at<EraseRules.RANGE;i++){
            Vec3d a=from.add(f.multiply(at));double left=EraseRules.RANGE-at;
            double low=wall(world,p,a.add(0,EraseRules.WALL_LOW,0),f,left),high=wall(world,p,a.add(0,EraseRules.WALL_HIGH,0),f,left);
            if(Double.isNaN(low)||Double.isNaN(high))return EraseRules.RANGE;
            double d=at+Math.max(low,high);
            if(d>=EraseRules.RANGE)return EraseRules.RANGE;
            if(wide(world,from,f,right,d))return Math.max(EraseRules.NEAR,Math.min(EraseRules.RANGE,d-.3));
            at=d+EraseRules.WALL_STEP;
        }
        return EraseRules.RANGE;
    }
    /** How far ahead of {@code a} the way {@code f} the first solid block is, within {@code most} (blocks; NaN: none). */
    private static double wall(ServerWorld world,ServerPlayerEntity p,Vec3d a,Vec3d f,double most){
        var hit=world.raycast(new RaycastContext(a,a.add(f.multiply(most+.5)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        return hit.getType()==HitResult.Type.MISS?Double.NaN:hit.getPos().distanceTo(a);
    }
    /**
     * Whether what stands {@code d} ahead of {@code from} in the slash's middle is a wall across it: solid both
     * EraseRules.WALL_WIDE to its left and to its right too, at both heights (as deep as that or a little deeper: a wall
     * standing slantwise to it).
     */
    private static boolean wide(ServerWorld world,Vec3d from,Vec3d f,Vec3d right,double d){
        for(int side=-1;side<=1;side+=2){
            boolean solid=false;
            for(double deep:EraseRules.WALL_DEEP){
                Vec3d c=from.add(f.multiply(d+deep)).add(right.multiply(side*EraseRules.WALL_WIDE));
                if(solid(world,c.add(0,EraseRules.WALL_LOW,0))&&solid(world,c.add(0,EraseRules.WALL_HIGH,0))){solid=true;break;}
            }
            if(!solid)return false;
        }
        return true;
    }
    private static boolean solid(ServerWorld world,Vec3d at){
        var pos=BlockPos.ofFloored(at);
        return !world.getBlockState(pos).getCollisionShape(world,pos).isEmpty();
    }
    /** Whether this one is deleted now: gone from sight, nothing harms them (XCharaUltimate.shielded), they can do nothing. */
    public static boolean deleted(LivingEntity e){
        if(e==null||e.getWorld().isClient)return false;
        Long until=GONE.get(e.getUuid());return until!=null&&e.getWorld().getTime()<until;
    }
    /** For the checks: this player's latest slash as {when it went, where from, the way, how far it goes, ticks since, how many it came over}, or null. */
    public static Object[] describe(PlayerEntity p){
        if(p==null)return null;
        Object[] out=null;
        for(var w:WAVES)if(w.owner.equals(p.getUuid()))out=new Object[]{w.went,w.from,w.yaw,w.range,w.age(),w.over.size()};
        return out;
    }
    /** For a boss reading what comes at it (BossThreats): this player's slashes going now, as {from, the way, the front now (blocks ahead), how far it goes}. */
    public static List<double[]> going(PlayerEntity p){
        var out=new ArrayList<double[]>();if(p==null)return out;
        for(var w:WAVES)if(w.owner.equals(p.getUuid())){
            double at=EraseRules.front(w.age(),w.range);if(Double.isNaN(at))continue;
            out.add(new double[]{w.from.x,w.from.y,w.from.z,w.yaw,at,w.range});
        }
        return out;
    }

    private static void tick(MinecraftServer server){
        for(var it=WAVES.iterator();it.hasNext();){
            var w=it.next();long t=w.age();
            var p=server.getPlayerManager().getPlayer(w.owner);
            if(p==null||p.getServerWorld()!=w.world||!p.isAlive()||p.isSpectator()||!StandManager.xChara(p)){
                // its user dead, gone, elsewhere or X Chara's no more: it is gone with them
                send(w,CUT,null,0,0);it.remove();continue;
            }
            if(t>EraseRules.reached(w.range)){it.remove();continue;}
            double[] sw=EraseRules.sweep((int)t,w.range);
            if(sw!=null)go(w,p,sw[0],sw[1]);
        }
        if(!GONE.isEmpty()){
            long most=Long.MIN_VALUE;for(var s:server.getWorlds()){most=Math.max(most,s.getTime());}
            final long now=most;GONE.values().removeIf(u->u<now-200);
        }
    }
    /** Whom {@code p}'s slash may strike at all: alive, to be struck, no merchant, not its user, and (a player) one they may harm. */
    private static boolean foe(ServerPlayerEntity p,LivingEntity e){
        return e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&!(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other));
    }
    /** The slash's front comes over {@code from} to {@code to} ahead this tick: everyone there it has not come over yet is struck. */
    private static void go(Wave w,ServerPlayerEntity p,double from,double to){
        Vec3d f=Vec3d.fromPolar(0,w.yaw),right=new Vec3d(-f.z,0,f.x),o=w.from;
        Vec3d a=o.add(f.multiply(from)),b=o.add(f.multiply(to));
        Box box=new Box(Math.min(a.x,b.x),o.y+EraseRules.LOW,Math.min(a.z,b.z),Math.max(a.x,b.x),o.y+EraseRules.HIGH,Math.max(a.z,b.z)).expand(EraseRules.HALF+2,1,EraseRules.HALF+2);
        var bodies=w.world.getEntitiesByClass(LivingEntity.class,box,e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!w.over.contains(e.getUuid()));
        long now=w.world.getTime();
        for(var e:bodies){
            Vec3d d=e.getPos().subtract(o);
            if(!EraseRules.struck(d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5,from,to))continue;
            w.over.add(e.getUuid());
            if(!foe(p,e)||deleted(e))continue;
            var source=new DamageSource(w.world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE),p);
            CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,EraseRules.DAMAGE))continue;
            // deleted: nothing harms them, and they can do nothing (their legs stopped wholly) until they are back
            GONE.put(e.getUuid(),now+EraseRules.VANISH);
            CombatManager.incapacitate(e,EraseRules.VANISH,EraseRules.VANISH,1);
            float yaw=w.yaw;
            // its user's own hit feedback (the damage number and the blood), as their other skills'
            if(!StarPlatinumTimeStop.deferFeedback(p,e,EraseRules.DAMAGE,yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var buf=PacketByteBufs.create();buf.writeVarInt(e.getId());buf.writeFloat(EraseRules.DAMAGE);buf.writeFloat(yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,buf);
            }
            p.onAttacking(e);
            send(w,DELETED,e,EraseRules.DAMAGE,EraseRules.VANISH);
        }
    }
    /**
     * Tells every viewer near it (within VIEW of where it went; its user and {@code e}, wherever they are) of it: the
     * kind, its id; as it goes, whose, the world, when it went and the server's time now, where from, the way and how far;
     * someone deleted: whom, what it dealt and for how many ticks; cut short: when.
     */
    private static void send(Wave w,int type,LivingEntity e,float dealt,int ticks){
        long now=w.world.getTime();
        for(var viewer:w.world.getPlayers()){
            if(!viewer.getUuid().equals(w.owner)&&viewer!=e&&viewer.squaredDistanceTo(w.from)>VIEW*VIEW)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(w.id);
            if(type==WAVE){
                b.writeUuid(w.owner);b.writeIdentifier(w.world.getRegistryKey().getValue());b.writeLong(w.went);b.writeLong(now);
                b.writeDouble(w.from.x);b.writeDouble(w.from.y);b.writeDouble(w.from.z);b.writeFloat(w.yaw);b.writeFloat((float)w.range);
            }else if(type==DELETED){
                b.writeVarInt(e.getId());b.writeFloat(dealt);b.writeVarInt(ticks);
            }else b.writeLong(now);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
