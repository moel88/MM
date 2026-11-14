package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * X Chara's rain of knives as it rains (2.132.5; RainRules for when each knife comes, where it comes down and how fast
 * it falls; XCharaAttack for the call that brings it, H with the hack off). Called, it rains where its user stood as
 * they pressed H, for its whole time, whatever its user does then; it is cut short if they die, leave, go to another
 * world or are X Chara's no more (its cooldown runs from then, XCharaAttack.rainCut).
 * <p>
 * Every knife is known to the server and to every client alike (its seed is told): when it comes out of the sky, where
 * it comes down, how high from, how fast it falls. Every tick the server looks at the stretch each knife falling
 * falls over that tick for the first body it meets (its point within RainRules.GIRTH of one's box, before the ground:
 * RainGround finds it as the clients do); the knife stops there. A foe it meets takes RainRules.DAMAGE (no guard takes
 * it, minecraft:bypasses_shield; no knockback, no stagger; its user is the attacker); its user's own, or one it may not
 * harm, it breaks on harmlessly. Every viewer within 128 blocks of it (and its user, and whom a knife meets) is told as
 * it is called, again once a second (one who comes near later sees it too), of every knife that met a body (where, and
 * what it did), and as it is over or cut short (xchara_rain_event_v1); the clients draw it (client/xchara/RainClient,
 * RainFx).
 */
public final class XCharaRain {
    public static final Identifier EVENT=CelestialFall.id("xchara_rain_event_v1");
    /** It is called; who is near is told of it again (once a second); a knife met a body; it is over; it was cut short. */
    public static final int START=0,STATE=1,STRUCK=2,ENDED=3,CUT=4;
    /** Its harm: no guard takes it (minecraft:bypasses_shield: it comes down from over them), its user the attacker. */
    public static final RegistryKey<DamageType> DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("blade_rain"));
    /** How far its news carries (blocks from its middle). */
    static final double VIEW=128;
    /** One rain as it rains: whose, where (its middle: its user's feet as they pressed H), from when (its call), its seed. */
    static final class Rain {
        final UUID id,owner;final ServerWorld world;final Vec3d middle;final long start;final int seed;
        /** The ground under each knife (NaN: none, no knife comes there), whether it has been looked for, and whether the knife has met a body. */
        final double[] ground=new double[RainRules.BLADES];final BitSet known=new BitSet(RainRules.BLADES),met=new BitSet(RainRules.BLADES);
        /** How many knives have struck a foe, and how many met a body at all (for the checks). */
        int struck,stopped;
        Rain(ServerPlayerEntity p,UUID id,Vec3d middle,long start){
            this.id=id;owner=p.getUuid();world=p.getServerWorld();this.middle=middle;this.start=start;
            seed=RainRules.seed(start,id.getMostSignificantBits(),id.getLeastSignificantBits());
        }
        long age(){return world.getTime()-start;}
    }
    private static final List<Rain> RAINS=new ArrayList<>();
    private XCharaRain(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaRain::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->RAINS.clear());
    }

    /** A rain is called by {@code p} (its call's id {@code id}), its middle {@code middle}: it rains from now (a rain of theirs still raining is over first). */
    static void start(ServerPlayerEntity p,UUID id,Vec3d middle){
        for(var it=RAINS.iterator();it.hasNext();){var r=it.next();if(r.owner.equals(p.getUuid())){send(r,ENDED,null,-1,0,0);it.remove();}}
        var r=new Rain(p,id,middle,p.getServerWorld().getTime());
        RAINS.add(r);
        send(r,START,null,-1,0,0);
    }
    /** Whether this player's rain is raining now (one called, not yet over). */
    public static boolean raining(PlayerEntity p){
        if(p==null)return false;
        for(var r:RAINS)if(r.owner.equals(p.getUuid())&&r.age()<RainRules.END)return true;
        return false;
    }
    /** For the checks: this player's rain as {its call's tick, its middle, ticks into it, knives that met a body, of them those that struck a foe}, or null. */
    public static Object[] describe(PlayerEntity p){
        if(p==null)return null;
        for(var r:RAINS)if(r.owner.equals(p.getUuid()))return new Object[]{r.start,r.middle,r.age(),r.stopped,r.struck};
        return null;
    }

    private static void tick(MinecraftServer server){
        for(var it=RAINS.iterator();it.hasNext();){
            var r=it.next();long t=r.age();
            var p=server.getPlayerManager().getPlayer(r.owner);
            boolean gone=p==null||p.getServerWorld()!=r.world||!p.isAlive()||p.isSpectator()||!StandManager.xChara(p);
            if(gone){
                // its user dead, gone, elsewhere or X Chara's no more: over now (its cooldown from now, if it was still raining)
                if(t<RainRules.END){send(r,CUT,null,-1,0,0);if(p!=null)XCharaAttack.rainCut(p);}
                else send(r,ENDED,null,-1,0,0);
                it.remove();continue;
            }
            if(t>=RainRules.OPEN)fall(r,p,t);
            if(t>=RainRules.LAST){send(r,ENDED,null,-1,0,0);it.remove();continue;}
            // who is near is told of it again once a second (one who comes near later sees it too)
            if(t>0&&t%20==(r.seed&15))send(r,STATE,null,-1,0,0);
        }
    }
    /** Whom its knives may harm: alive, to be struck, no merchant, not its user, and (a player) one they may harm. */
    private static boolean foe(ServerPlayerEntity p,LivingEntity e){
        return e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&!(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other));
    }
    /** The ground under knife {@code k} of {@code r} coming down at (x, z) from {@code top} (looked for once; none where the world is not there). */
    private static double ground(Rain r,int k,double x,double z,double top){
        if(r.known.get(k))return r.ground[k];
        double g=r.world.isChunkLoaded(net.minecraft.util.math.MathHelper.floor(x)>>4,net.minecraft.util.math.MathHelper.floor(z)>>4)
            ?RainGround.at(r.world,x,z,top,r.middle.y-RainRules.DEEP):Double.NaN;
        r.ground[k]=g;r.known.set(k);
        return g;
    }
    /** A tick of the rain {@code t} ticks after its call: each knife falling over this tick meets the first body in its way, if any. */
    private static void fall(Rain r,ServerPlayerEntity p,long t){
        var m=r.middle;
        var bodies=r.world.getEntitiesByClass(LivingEntity.class,
            new Box(m.x-RainRules.ZONE-1,m.y-RainRules.DEEP-3,m.z-RainRules.ZONE-1,m.x+RainRules.ZONE+1,m.y+RainRules.SKY+RainRules.SKY_MORE+1,m.z+RainRules.ZONE+1),
            e->e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity));
        if(bodies.isEmpty())return;
        int[] w=RainRules.window(t,RainRules.LAST-RainRules.POUR);
        for(int k=w[0];k<w[1];k++){
            if(r.met.get(k))continue;
            double c=RainRules.comes(k);if(c>t)break;
            double from=RainRules.point(k,r.seed,Math.max(c,t-1)),to=RainRules.point(k,r.seed,t);
            double[] s=RainRules.spot(k,r.seed);double x=m.x+s[0],z=m.z+s[1];
            LivingEntity best=null;double at=-Double.MAX_VALUE,g=Double.NaN;boolean looked=false;
            for(var e:bodies){
                var b=e.getBoundingBox();
                if(!RainRules.over(x,z,b.minX,b.maxX,b.minZ,b.maxZ))continue;
                if(!looked){g=ground(r,k,x,z,m.y+RainRules.sky(k,r.seed));looked=true;if(Double.isNaN(g))break;}
                double hit=RainRules.meets(m.y+from,m.y+to,b.minY,b.maxY,g);
                if(!Double.isNaN(hit)&&hit>at){at=hit;best=e;}
            }
            if(best==null)continue;
            r.met.set(k);r.stopped++;
            strike(r,p,best,k,at);
        }
    }
    /** Knife {@code k} of {@code r} meets {@code e} at the height {@code at}: a foe takes its harm (its user's own breaks on them harmlessly). */
    private static void strike(Rain r,ServerPlayerEntity p,LivingEntity e,int k,double at){
        float dealt=0;
        if(foe(p,e)){
            var source=new DamageSource(r.world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE),p);
            e.timeUntilRegen=0;
            if(CombatManager.damageWithoutKnockback(e,source,RainRules.DAMAGE)){
                dealt=RainRules.DAMAGE;r.struck++;
                Vec3d to=e.getPos().subtract(r.middle);float yaw=to.horizontalLengthSquared()<1e-6?p.getYaw():(float)Math.toDegrees(Math.atan2(-to.x,to.z));
                // its user's own hit feedback (the damage number and the blood), as their other skills'
                if(!StarPlatinumTimeStop.deferFeedback(p,e,dealt,yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                    var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(dealt);b.writeFloat(yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
                }
                p.onAttacking(e);
            }
        }
        send(r,STRUCK,e,k,(float)(at-e.getY()),dealt);
    }
    /**
     * Tells every viewer near it (within VIEW of its middle; its user and {@code e}, wherever they are) of it: the kind,
     * its id; as it is called and again, whose, the world, when it was called and the server's time now, its middle and
     * seed; a knife that met a body: which, whom, how high over their feet, what it did; over or cut short: when.
     */
    private static void send(Rain r,int type,LivingEntity e,int k,float height,float dealt){
        long now=r.world.getTime();
        for(var viewer:r.world.getPlayers()){
            if(!viewer.getUuid().equals(r.owner)&&viewer!=e&&viewer.squaredDistanceTo(r.middle)>VIEW*VIEW)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(r.id);
            if(type==START||type==STATE){
                b.writeUuid(r.owner);b.writeIdentifier(r.world.getRegistryKey().getValue());b.writeLong(r.start);b.writeLong(now);
                b.writeDouble(r.middle.x);b.writeDouble(r.middle.y);b.writeDouble(r.middle.z);b.writeInt(r.seed);
            }else if(type==STRUCK){
                b.writeVarInt(k);b.writeVarInt(e.getId());b.writeFloat(height);b.writeFloat(dealt);
            }else b.writeLong(now);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
