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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * X Chara's delete slash once it is thrown (2.133.0; EraseRules for its rules, XCharaAttack for the leap that throws it,
 * H with the hack off). It flies from the knife up in the air of the leap to the ground the middle of its user's screen
 * pointed at (EraseGround: no further than EraseRules.REACH out, on the ground there) and stands in it; then:
 * <ul>
 * <li>everyone on the ground round it (EraseRules.SIDE each way) is struck for EraseRules.DAMAGE (no guard takes it,
 * minecraft:bypasses_shield; no knockback; its user the attacker) and deleted for EraseRules.VANISH ticks
 * ({@link #deleted}: nothing harms them, XCharaUltimate.shielded; they can do nothing and their legs are stopped);</li>
 * <li>a cube of that ground rises (EraseRules.lift), something to stand on (EraseCubes: kept out of, stood on, carried up
 * by; the map is not changed), and bursts at EraseRules.BURST: whoever is on it then is struck for
 * EraseRules.BURST_DAMAGE. Whoever comes off it, or falls as it bursts, takes no harm from that fall
 * ({@link #spared}).</li>
 * </ul>
 * It is gone with its user (dead, left, elsewhere, X Chara's no more), the cube with it. Every viewer within 128 blocks of
 * where it lands (and its user, and whom it strikes) is told as it is thrown (from where to where, when it strikes, its
 * ground), of everyone it deletes and everyone the burst strikes, and if it is cut short (xchara_erase_event_v1); the
 * clients draw it (client/xchara/EraseClient, EraseFx) and stand their own player on its cube.
 */
public final class XCharaErase {
    public static final Identifier EVENT=CelestialFall.id("xchara_erase_event_v1");
    /** It is thrown; it deleted someone; the burst struck someone; it was cut short. */
    public static final int THROWN=0,DELETED=1,BLASTED=2,CUT=3;
    /** Its harm: no guard takes it (minecraft:bypasses_shield), its user the attacker. */
    public static final RegistryKey<DamageType> DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("erase"));
    /** How far its news carries (blocks from where it lands). */
    static final double VIEW=128;
    /** How long one who came off a cube is spared their fall (ticks, at most). */
    static final int SPARED=200;
    /** One slash: whose (its call's id its own), its world, from where (the knife) to where (the ground it lands on), when it was thrown and when it strikes, its ground's corner and top, and who was on its cube a tick ago. */
    static final class Strike {
        final UUID id,owner;final ServerWorld world;final Vec3d from,to;final long went,struck;final int x0,z0;final double top;
        boolean landed;Set<UUID> riders=new HashSet<>();
        Strike(ServerPlayerEntity p,UUID id,Vec3d from,Vec3d to,long went,long struck){
            this.id=id;owner=p.getUuid();world=p.getServerWorld();this.from=from;this.to=to;this.went=went;this.struck=struck;
            x0=EraseRules.corner(to.x);z0=EraseRules.corner(to.z);top=to.y;
        }
        long since(long now){return now-struck;}
        /** Its cube now, or null (not struck yet, or burst). */
        EraseCubes.Cube cube(long now){long t=since(now);return landed&&EraseRules.cube(t)?new EraseCubes.Cube(x0,z0,top,t):null;}
        /** The middle of its ground, at its top. */
        Vec3d middle(){return new Vec3d(x0+EraseRules.SIDE*.5,top,z0+EraseRules.SIDE*.5);}
    }
    private static final List<Strike> STRIKES=new ArrayList<>();
    /** Who is deleted, until when (the server's time). */
    private static final Map<UUID,Long> GONE=new HashMap<>();
    /** Who came off a cube (or fell as it burst), from when, until when their fall is spared at most. */
    private static final Map<UUID,long[]> FALLING=new HashMap<>();
    /** The server's cubes, for EraseCubes. */
    static final EraseCubes.Side SIDE=new EraseCubes.Side(){
        @Override public boolean any(){return !STRIKES.isEmpty();}
        @Override public void cubes(World world,List<EraseCubes.Cube> out){
            for(var s:STRIKES){if(s.world!=world)continue;var c=s.cube(world.getTime());if(c!=null)out.add(c);}
        }
    };
    private XCharaErase(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaErase::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{STRIKES.clear();GONE.clear();FALLING.clear();});
    }

    /**
     * {@code p}'s slash is thrown now (its call's id {@code id}): they stood at {@code from} and rose {@code rise} over
     * it, facing {@code yaw}; {@code aim} is their client's last word of where the middle of its screen points (null:
     * none of late, their look from up there is taken). It leaves the knife and lands on the ground found there.
     */
    static void start(ServerPlayerEntity p,UUID id,Vec3d from,double rise,float yaw,Vec3d aim){
        var world=p.getServerWorld();
        Vec3d eye=from.add(0,rise+p.getStandingEyeHeight(),0);
        Vec3d want=aim!=null?aim:EraseGround.aimed(world,p,from,eye,p.getRotationVec(1));
        Vec3d to=EraseGround.landing(world,from,rise,want);
        Vec3d knife=knife(from,rise,yaw);
        long now=world.getTime();
        var s=new Strike(p,id,knife,to,now,now+EraseRules.flight(knife.distanceTo(to)));
        STRIKES.add(s);
        send(s,THROWN,null,0,0);
    }
    /** Where the slash leaves the knife: before their chest up in the air of the leap, the way they faced. */
    public static Vec3d knife(Vec3d from,double rise,float yaw){
        return from.add(Vec3d.fromPolar(0,yaw).multiply(EraseRules.KNIFE_AHEAD)).add(0,rise+EraseRules.KNIFE_UP,0);
    }
    /** Whether this one is deleted now: gone from sight, nothing harms them (XCharaUltimate.shielded), they can do nothing. */
    public static boolean deleted(LivingEntity e){
        if(e==null||e.getWorld().isClient)return false;
        Long until=GONE.get(e.getUuid());return until!=null&&e.getWorld().getTime()<until;
    }
    /** Whether this player is on a cube or over one, a jump or so up (no floating to be kicked for: FormationFloatMixin). */
    public static boolean carried(ServerPlayerEntity p){
        if(STRIKES.isEmpty())return false;
        var box=p.getBoundingBox();double feet=p.getY();long now=p.getWorld().getTime();
        for(var s:STRIKES){
            if(s.world!=p.getWorld())continue;var c=s.cube(now);if(c==null||!c.over(box))continue;
            double top=c.top();if(feet>=top-1&&feet<=top+8)return true;
        }
        return false;
    }
    /** Whether {@code e}'s fall now does it no harm: it came off a cube (or fell as it burst) and has not landed since (LivingCombatMixin: the fall's damage). */
    public static boolean spared(LivingEntity e){
        if(e==null||e.getWorld().isClient||FALLING.isEmpty())return false;
        var f=FALLING.remove(e.getUuid());return f!=null&&e.getWorld().getTime()<=f[1];
    }
    /** For the checks: this player's latest slash as {when it was thrown, from, to, when it strikes, ticks since it struck, its ground's corner x, z, its cube's top now (NaN: none), who is on it}, or null. */
    public static Object[] describe(PlayerEntity p){
        if(p==null)return null;
        Object[] out=null;
        for(var s:STRIKES)if(s.owner.equals(p.getUuid())){
            long now=s.world.getTime();var c=s.cube(now);
            out=new Object[]{s.went,s.from,s.to,s.struck,s.since(now),s.x0,s.z0,c==null?Double.NaN:c.top(),s.riders.size()};
        }
        return out;
    }
    /**
     * For a boss reading what comes at it (BossThreats): this player's slashes now, as {its ground's middle x, top, z,
     * the tick it strikes, the tick its cube bursts, the cube's top now (NaN: none yet or no more)}.
     */
    public static List<double[]> going(PlayerEntity p){
        var out=new ArrayList<double[]>();if(p==null)return out;
        for(var s:STRIKES)if(s.owner.equals(p.getUuid())){
            var m=s.middle();var c=s.cube(s.world.getTime());
            out.add(new double[]{m.x,m.y,m.z,s.struck,s.struck+EraseRules.BURST,c==null?Double.NaN:c.top()});
        }
        return out;
    }

    private static void tick(MinecraftServer server){
        for(var it=STRIKES.iterator();it.hasNext();){
            var s=it.next();long now=s.world.getTime();
            var p=server.getPlayerManager().getPlayer(s.owner);
            if(p==null||p.getServerWorld()!=s.world||!p.isAlive()||p.isSpectator()||!StandManager.xChara(p)){
                // its user dead, gone, elsewhere or X Chara's no more: it is gone with them, its cube with it (whoever was on it falls unharmed)
                fell(s,now);send(s,CUT,null,0,0);it.remove();continue;
            }
            if(!s.landed&&now>=s.struck){s.landed=true;land(s,p,now);}
            if(!s.landed)continue;
            long t=s.since(now);
            if(t>=EraseRules.BURST){burst(s,p,now);it.remove();continue;}
            ride(s,now);
        }
        if(!GONE.isEmpty()||!FALLING.isEmpty()){
            long most=Long.MIN_VALUE;for(var w:server.getWorlds())most=Math.max(most,w.getTime());
            final long now=most;GONE.values().removeIf(u->u<now-200);
            // those who came off a cube: spared until they land (and a moment), or SPARED at most
            FALLING.entrySet().removeIf(en->{
                if(en.getValue()[1]<now)return true;
                var who=entity(server,en.getKey());
                return who==null||who.isOnGround()&&now-en.getValue()[0]>2&&!on(who);
            });
        }
    }
    private static LivingEntity entity(MinecraftServer server,UUID id){
        var p=server.getPlayerManager().getPlayer(id);if(p!=null)return p;
        for(var w:server.getWorlds()){if(w.getEntity(id) instanceof LivingEntity e)return e;}
        return null;
    }
    private static boolean on(LivingEntity e){return !Double.isNaN(EraseCubes.on(e));}
    /** Whom {@code p}'s slash may strike at all: alive, to be struck, no merchant, not its user, and (a player) one they may harm. */
    private static boolean foe(ServerPlayerEntity p,LivingEntity e){
        return e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&!(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other));
    }
    /** The bodies over {@code s}'s ground, their feet from {@code low} to {@code high}. */
    private static List<LivingEntity> over(Strike s,double low,double high){
        Box box=new Box(s.x0,low-.5,s.z0,s.x0+EraseRules.SIDE,high+.5,s.z0+EraseRules.SIDE).expand(1,0,1);
        return s.world.getEntitiesByClass(LivingEntity.class,box,e->e.isAlive()&&!e.isSpectator()&&EraseRules.over(s.x0,s.z0,e.getBoundingBox().minX,e.getBoundingBox().maxX,e.getBoundingBox().minZ,e.getBoundingBox().maxZ));
    }
    private static DamageSource source(Strike s,ServerPlayerEntity p){
        return new DamageSource(s.world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE),p);
    }
    /** {@code e} struck for {@code amount} by {@code p}'s slash; its user's own hit feedback (the number and the blood), as their other skills'. Whether it went in. */
    private static boolean strike(Strike s,ServerPlayerEntity p,LivingEntity e,float amount){
        CombatManager.interrupt(e);
        e.timeUntilRegen=0;
        if(!CombatManager.damageWithoutKnockback(e,source(s,p),amount))return false;
        float yaw=(float)Math.toDegrees(Math.atan2(-(e.getX()-s.from.x),e.getZ()-s.from.z));
        if(!StarPlatinumTimeStop.deferFeedback(p,e,amount,yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
            var buf=PacketByteBufs.create();buf.writeVarInt(e.getId());buf.writeFloat(amount);buf.writeFloat(yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,buf);
        }
        p.onAttacking(e);
        return true;
    }
    /** The slash lands: everyone on its ground is struck and deleted. */
    private static void land(Strike s,ServerPlayerEntity p,long now){
        for(var e:over(s,s.top-EraseRules.STRUCK_UNDER,s.top+EraseRules.STRUCK_OVER)){
            if(!EraseRules.struck(e.getY(),s.top)||!foe(p,e)||deleted(e))continue;
            if(!strike(s,p,e,EraseRules.DAMAGE))continue;
            // deleted: nothing harms them, and they can do nothing (their legs stopped wholly) until they are back
            GONE.put(e.getUuid(),now+EraseRules.VANISH);
            CombatManager.incapacitate(e,EraseRules.VANISH,EraseRules.VANISH,1);
            send(s,DELETED,e,EraseRules.DAMAGE,EraseRules.VANISH);
        }
    }
    /** Who is on the cube now: whoever was a tick ago and is no more came off it (their fall is spared). */
    private static void ride(Strike s,long now){
        var c=s.cube(now);if(c==null)return;double top=c.top();
        Set<UUID> on=new HashSet<>();
        for(var e:over(s,top-EraseRules.ON_UNDER,top+EraseRules.ON_OVER))if(EraseRules.on(e.getY(),top))on.add(e.getUuid());
        for(var id:s.riders)if(!on.contains(id))FALLING.put(id,new long[]{now,now+SPARED});
        s.riders=on;
    }
    /** Everyone on its cube now falls from it (it burst, or it is gone): their fall is spared. */
    private static void fell(Strike s,long now){
        var c=s.cube(now);
        if(c!=null){double top=c.top();for(var e:over(s,top-EraseRules.ON_UNDER,top+EraseRules.ON_OVER))if(EraseRules.on(e.getY(),top))s.riders.add(e.getUuid());}
        for(var id:s.riders)FALLING.put(id,new long[]{now,now+SPARED});
        s.riders.clear();
    }
    /** The cube bursts: whoever is on it is struck, and falls from it unharmed. */
    private static void burst(Strike s,ServerPlayerEntity p,long now){
        double top=s.top+EraseRules.LIFT;
        for(var e:over(s,top-EraseRules.ON_UNDER,top+EraseRules.ON_OVER)){
            if(!EraseRules.on(e.getY(),top))continue;
            s.riders.add(e.getUuid());
            if(!foe(p,e)||deleted(e))continue;
            if(strike(s,p,e,EraseRules.BURST_DAMAGE))send(s,BLASTED,e,EraseRules.BURST_DAMAGE,0);
        }
        for(var id:s.riders)FALLING.put(id,new long[]{now,now+SPARED});
        s.riders.clear();
    }
    /**
     * Tells every viewer near it (within VIEW of where it lands; its user and {@code e}, wherever they are) of it: the
     * kind, its id; thrown: whose, the world, when it was thrown and the server's time now, from where to where, when it
     * strikes, its ground's corner and top; someone deleted or struck by the burst: whom, what it dealt and for how many
     * ticks (deleted); cut short: when.
     */
    private static void send(Strike s,int type,LivingEntity e,float dealt,int ticks){
        long now=s.world.getTime();
        for(var viewer:s.world.getPlayers()){
            if(!viewer.getUuid().equals(s.owner)&&viewer!=e&&viewer.squaredDistanceTo(s.to)>VIEW*VIEW)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(s.id);
            if(type==THROWN){
                b.writeUuid(s.owner);b.writeIdentifier(s.world.getRegistryKey().getValue());b.writeLong(s.went);b.writeLong(now);
                b.writeDouble(s.from.x);b.writeDouble(s.from.y);b.writeDouble(s.from.z);b.writeDouble(s.to.x);b.writeDouble(s.to.y);b.writeDouble(s.to.z);
                b.writeLong(s.struck);b.writeInt(s.x0);b.writeInt(s.z0);
            }else if(type==DELETED||type==BLASTED){
                b.writeVarInt(e.getId());b.writeFloat(dealt);b.writeVarInt(ticks);
            }else b.writeLong(now);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
