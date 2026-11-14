package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import dev.sangwoo.celestialfall.training.TrainingDummyEntity;
import java.util.ArrayList;
import java.util.Comparator;
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
 * X Chara's terrain delete once its grounds are marked (2.133.0; EraseRules for its rules, XCharaAttack for the press
 * that marks them, H with the hack on). Four grounds EraseRules.SIDE each way ({@link #start}): where the middle of its
 * user's screen points (EraseGround), under the nearest ones within EraseRules.NEAR (players its user may harm, and
 * training dummies), and at random places for the rest; then:
 * <ul>
 * <li>EraseRules.WARN after the mark each rises as a cube (EraseRules.lift), something to stand on (EraseCubes: kept
 * out of, stood on, carried up by; the map is not changed);</li>
 * <li>at EraseRules.BURST each bursts: whoever is on one then is struck for EraseRules.BURST_DAMAGE (no guard takes it,
 * minecraft:bypasses_shield; no knockback; its user the attacker). Whoever comes off one, or falls as it bursts, takes
 * no harm from that fall ({@link #spared}).</li>
 * </ul>
 * The cubes are gone with their user (dead, left, elsewhere, X Chara's no more). Every viewer within 128 blocks of its
 * user or of a ground (and its user) is told as the grounds are marked (where, what each is), of everyone a burst strikes,
 * and if it is cut short (xchara_erase_event_v1); the clients draw it (client/xchara/EraseClient, EraseFx) and stand their
 * own player on its cubes.
 */
public final class XCharaErase {
    public static final Identifier EVENT=CelestialFall.id("xchara_erase_event_v1");
    /** Its grounds are marked; a burst struck someone; it was cut short. */
    public static final int MARKED=0,BLASTED=1,CUT=2;
    /** Its harm: no guard takes it (minecraft:bypasses_shield), its user the attacker. */
    public static final RegistryKey<DamageType> DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("erase"));
    /** How far its news carries (blocks from its user or from a ground). */
    static final double VIEW=128;
    /** How long one who came off a cube is spared their fall (ticks, at most). */
    static final int SPARED=200;
    /** How long one who came off a cube must have been back on the ground (ticks, after being off it) before their fall is no more spared. */
    static final int LANDED=10;
    /** How far up and down from its user's feet one near may be (blocks). */
    static final double NEAR_HEIGHT=12;
    /**
     * One marked ground: its corner, its ground's top, what it is (EraseRules.LOOK, PLAYER, RANDOM), whom it is under (a
     * PLAYER's: their entity id; -1) and the top of their head where they stood as it was marked (its "!!!" shows over it;
     * null for the others), and who was on its cube a tick ago.
     */
    static final class Ground {
        final int x0,z0,kind,under;final double top;final Vec3d head;Set<UUID> riders=new HashSet<>();
        Ground(int x0,int z0,double top,int kind,int under,Vec3d head){this.x0=x0;this.z0=z0;this.top=top;this.kind=kind;this.under=under;this.head=head;}
        /** Its cube {@code t} ticks after the mark, or null (burst). */
        EraseCubes.Cube cube(long t){return EraseRules.cube(t)?new EraseCubes.Cube(x0,z0,top,t):null;}
        /** The middle of its ground, at its top. */
        Vec3d middle(){return new Vec3d(x0+EraseRules.SIDE*.5,top,z0+EraseRules.SIDE*.5);}
    }
    /** One call: whose (its id its own), its world, where its user stood, when its grounds were marked, and its grounds. */
    static final class Call {
        final UUID id,owner;final ServerWorld world;final Vec3d from;final long marked;final List<Ground> grounds;
        Call(ServerPlayerEntity p,UUID id,Vec3d from,long marked,List<Ground> grounds){
            this.id=id;owner=p.getUuid();world=p.getServerWorld();this.from=from;this.marked=marked;this.grounds=grounds;
        }
        long since(long now){return now-marked;}
    }
    private static final List<Call> CALLS=new ArrayList<>();
    /** One who came off a cube (or fell as it burst): from when, until when at most their fall is spared, and for how many ticks in a row they have been on the ground (no cube under them) since. */
    private static final class Fall {
        final long from,until;int grounded;
        Fall(long from,long until){this.from=from;this.until=until;}
    }
    private static final Map<UUID,Fall> FALLING=new HashMap<>();
    /** The server's cubes, for EraseCubes. */
    static final EraseCubes.Side SIDE=new EraseCubes.Side(){
        @Override public boolean any(){return !CALLS.isEmpty();}
        @Override public void cubes(World world,List<EraseCubes.Cube> out){
            for(var c:CALLS){
                if(c.world!=world)continue;long t=c.since(world.getTime());
                for(var g:c.grounds){var cube=g.cube(t);if(cube!=null)out.add(cube);}
            }
        }
    };
    private XCharaErase(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(XCharaErase::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{CALLS.clear();FALLING.clear();});
    }

    /**
     * {@code p}'s grounds are marked now (its call's id {@code id}): they stand at {@code from}; {@code aim} is their
     * client's word of where the middle of its screen points as they pressed H (null: none, their look is taken). One
     * ground there; one under each of the nearest within EraseRules.NEAR, up to three; the rest at random places, none
     * with them on it; no two overlapping.
     */
    static void start(ServerPlayerEntity p,UUID id,Vec3d from,Vec3d aim){
        var world=p.getServerWorld();
        Vec3d want=aim!=null?aim:EraseGround.aimed(world,p,from,from.add(0,p.getStandingEyeHeight(),0),p.getRotationVec(1));
        List<int[]> taken=new ArrayList<>();List<Ground> grounds=new ArrayList<>();
        // where they look: the giant "!!!"
        Vec3d at=EraseGround.landing(world,from,want);
        int lx=EraseRules.corner(at.x),lz=EraseRules.corner(at.z);
        grounds.add(new Ground(lx,lz,at.y,EraseRules.LOOK,-1,null));taken.add(new int[]{lx,lz});
        // under the nearest ones (one on a ground marked already is lifted by it: the next one near is taken)
        for(var e:near(p,from)){
            if(grounds.size()>EraseRules.NEAR_ONES)break;
            double top=EraseGround.under(world,e.getX(),e.getZ(),e.getY());if(Double.isNaN(top))continue;
            int[] c=EraseRules.around(e.getX(),e.getZ(),taken);if(c==null)continue;
            grounds.add(new Ground(c[0],c[1],top,EraseRules.PLAYER,e.getId(),new Vec3d(e.getX(),e.getY()+e.getHeight(),e.getZ())));taken.add(c);
        }
        // the rest at random places near them, none with them on it
        var box=p.getBoundingBox();var random=world.getRandom();
        while(grounds.size()<EraseRules.CUBES){
            boolean found=false;
            for(int k=0;k<EraseRules.RANDOM_TRIES&&!found;k++){
                double[] xz=EraseRules.random(from.x,from.z,random.nextDouble(),random.nextDouble());
                int x0=EraseRules.corner(xz[0]),z0=EraseRules.corner(xz[1]);
                if(!EraseRules.free(x0,z0,taken)||EraseRules.over(x0,z0,box.minX,box.maxX,box.minZ,box.maxZ))continue;
                double top=EraseGround.near(world,x0+EraseRules.SIDE*.5,z0+EraseRules.SIDE*.5,from.y);if(Double.isNaN(top))continue;
                grounds.add(new Ground(x0,z0,top,EraseRules.RANDOM,-1,null));taken.add(new int[]{x0,z0});found=true;
            }
            if(!found)break;
        }
        var c=new Call(p,id,from,world.getTime(),grounds);
        CALLS.add(c);
        send(c,MARKED,null,-1,0);
    }
    /** The ones near {@code p} standing at {@code from} whose ground may be marked, nearest first: players they may harm (not in creative) and training dummies, within EraseRules.NEAR level and NEAR_HEIGHT up or down. */
    private static List<LivingEntity> near(ServerPlayerEntity p,Vec3d from){
        var box=new Box(from,from).expand(EraseRules.NEAR,NEAR_HEIGHT,EraseRules.NEAR);
        var out=p.getServerWorld().getEntitiesByClass(LivingEntity.class,box,e->{
            if(!foe(p,e))return false;
            if(!(e instanceof PlayerEntity||e instanceof TrainingDummyEntity))return false;
            if(e instanceof PlayerEntity other&&other.getAbilities().creativeMode)return false;
            double dx=e.getX()-from.x,dz=e.getZ()-from.z;return dx*dx+dz*dz<=EraseRules.NEAR*EraseRules.NEAR;
        });
        out.sort(Comparator.comparingDouble(e->e.squaredDistanceTo(from)));
        return out;
    }
    /** Whether this player is on a cube or over one, a jump or so up (no floating to be kicked for: FormationFloatMixin). */
    public static boolean carried(ServerPlayerEntity p){
        if(CALLS.isEmpty())return false;
        var box=p.getBoundingBox();double feet=p.getY();long now=p.getWorld().getTime();
        for(var c:CALLS){
            if(c.world!=p.getWorld())continue;long t=c.since(now);
            for(var g:c.grounds){
                var cube=g.cube(t);if(cube==null||!cube.over(box))continue;
                double top=cube.top();if(feet>=top-1&&feet<=top+8)return true;
            }
        }
        return false;
    }
    /**
     * Whether {@code e}'s fall now does it no harm (LivingCombatMixin: the fall's damage): it came off a cube (or fell as
     * one burst) and has not been back on the ground for a while since. Kept on (not taken by the first landing) so that
     * a hop or a landing told twice does not leave the real one unspared.
     */
    public static boolean spared(LivingEntity e){
        if(e==null||e.getWorld().isClient||FALLING.isEmpty())return false;
        var f=FALLING.get(e.getUuid());return f!=null&&e.getWorld().getTime()<=f.until;
    }
    /** For the checks: this player's latest call as {when it was marked, where they stood, ticks since the mark, its grounds each as {x0, z0, top, kind, under, cube's top now (NaN: none), riders}}, or null. */
    public static Object[] describe(PlayerEntity p){
        if(p==null)return null;
        Object[] out=null;
        for(var c:CALLS)if(c.owner.equals(p.getUuid())){
            long now=c.world.getTime(),t=c.since(now);
            var gs=new ArrayList<Object[]>();
            for(var g:c.grounds){var cube=g.cube(t);gs.add(new Object[]{g.x0,g.z0,g.top,g.kind,g.under,cube==null?Double.NaN:cube.top(),g.riders.size()});}
            out=new Object[]{c.marked,c.from,t,gs};
        }
        return out;
    }
    /**
     * For a boss reading what comes at it (BossThreats): this player's grounds now, as {the ground's middle x, top, z,
     * the tick it rises, the tick it bursts, its cube's top now (NaN: not up yet or no more)}.
     */
    public static List<double[]> going(PlayerEntity p){
        var out=new ArrayList<double[]>();if(p==null)return out;
        for(var c:CALLS)if(c.owner.equals(p.getUuid())){
            long t=c.since(c.world.getTime());
            for(var g:c.grounds){
                var m=g.middle();var cube=g.cube(t);
                out.add(new double[]{m.x,m.y,m.z,c.marked+EraseRules.RISE_FROM,c.marked+EraseRules.BURST,cube==null||t<EraseRules.RISE_FROM?Double.NaN:cube.top()});
            }
        }
        return out;
    }

    private static void tick(MinecraftServer server){
        for(var it=CALLS.iterator();it.hasNext();){
            var c=it.next();long now=c.world.getTime();
            var p=server.getPlayerManager().getPlayer(c.owner);
            if(p==null||p.getServerWorld()!=c.world||!p.isAlive()||p.isSpectator()||!StandManager.xChara(p)){
                // its user dead, gone, elsewhere or X Chara's no more: the cubes are gone with them (whoever was on them falls unharmed)
                for(var g:c.grounds)fell(c,g,now);
                send(c,CUT,null,-1,0);it.remove();continue;
            }
            long t=c.since(now);
            if(t>=EraseRules.BURST){for(var g:c.grounds)burst(c,g,p,now);it.remove();continue;}
            for(var g:c.grounds)ride(c,g,now);
        }
        if(!FALLING.isEmpty()){
            long most=Long.MIN_VALUE;for(var w:server.getWorlds())most=Math.max(most,w.getTime());
            final long now=most;
            // those who came off a cube: spared until they have stood on the ground (no cube under them) LANDED ticks in a
            // row, or SPARED at most. (2.133.0's first multiplayer check: taken by the first tick the server had them on the
            // ground after a burst, a moment before their client's landing came, the fall hurt them all the same.)
            FALLING.entrySet().removeIf(en->{
                var f=en.getValue();if(f.until<now)return true;
                var who=entity(server,en.getKey());if(who==null)return true;
                if(!who.isOnGround()||on(who)){f.grounded=0;return false;}
                return ++f.grounded>=LANDED;
            });
        }
    }
    private static LivingEntity entity(MinecraftServer server,UUID id){
        var p=server.getPlayerManager().getPlayer(id);if(p!=null)return p;
        for(var w:server.getWorlds()){if(w.getEntity(id) instanceof LivingEntity e)return e;}
        return null;
    }
    private static boolean on(LivingEntity e){return !Double.isNaN(EraseCubes.on(e));}
    /** Whom {@code p}'s cubes may strike, or whose ground they may mark: alive, to be struck, no merchant, not its user, and (a player) one they may harm. */
    private static boolean foe(ServerPlayerEntity p,LivingEntity e){
        return e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&!(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other));
    }
    /** The bodies over ground {@code g}, their feet from {@code low} to {@code high}. */
    private static List<LivingEntity> over(Call c,Ground g,double low,double high){
        Box box=new Box(g.x0,low-.5,g.z0,g.x0+EraseRules.SIDE,high+.5,g.z0+EraseRules.SIDE).expand(1,0,1);
        return c.world.getEntitiesByClass(LivingEntity.class,box,e->e.isAlive()&&!e.isSpectator()&&EraseRules.over(g.x0,g.z0,e.getBoundingBox().minX,e.getBoundingBox().maxX,e.getBoundingBox().minZ,e.getBoundingBox().maxZ));
    }
    private static DamageSource source(Call c,ServerPlayerEntity p){
        return new DamageSource(c.world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE),p);
    }
    /** {@code e} struck for {@code amount} by {@code p}'s burst; its user's own hit feedback (the number and the blood), as their other skills'. Whether it went in. */
    private static boolean strike(Call c,Ground g,ServerPlayerEntity p,LivingEntity e,float amount){
        CombatManager.interrupt(e);
        e.timeUntilRegen=0;
        if(!CombatManager.damageWithoutKnockback(e,source(c,p),amount))return false;
        var m=g.middle();float yaw=(float)Math.toDegrees(Math.atan2(-(e.getX()-m.x),e.getZ()-m.z));
        if(!StarPlatinumTimeStop.deferFeedback(p,e,amount,yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
            var buf=PacketByteBufs.create();buf.writeVarInt(e.getId());buf.writeFloat(amount);buf.writeFloat(yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,buf);
        }
        p.onAttacking(e);
        return true;
    }
    /** Who is on ground {@code g}'s cube now: whoever was a tick ago and is no more came off it (their fall is spared). */
    private static void ride(Call c,Ground g,long now){
        var cube=g.cube(c.since(now));if(cube==null)return;double top=cube.top();
        Set<UUID> on=new HashSet<>();
        for(var e:over(c,g,top-EraseRules.ON_UNDER,top+EraseRules.ON_OVER))if(EraseRules.on(e.getY(),top))on.add(e.getUuid());
        for(var id:g.riders)if(!on.contains(id))FALLING.put(id,new Fall(now,now+SPARED));
        g.riders=on;
    }
    /** Everyone on ground {@code g}'s cube now falls from it (it is gone): their fall is spared. */
    private static void fell(Call c,Ground g,long now){
        var cube=g.cube(c.since(now));
        if(cube!=null){double top=cube.top();for(var e:over(c,g,top-EraseRules.ON_UNDER,top+EraseRules.ON_OVER))if(EraseRules.on(e.getY(),top))g.riders.add(e.getUuid());}
        for(var id:g.riders)FALLING.put(id,new Fall(now,now+SPARED));
        g.riders.clear();
    }
    /** Ground {@code g}'s cube bursts: whoever is on it is struck, and falls from it unharmed. */
    private static void burst(Call c,Ground g,ServerPlayerEntity p,long now){
        double top=g.top+EraseRules.LIFT;
        for(var e:over(c,g,top-EraseRules.ON_UNDER,top+EraseRules.ON_OVER)){
            if(!EraseRules.on(e.getY(),top))continue;
            g.riders.add(e.getUuid());
            if(!foe(p,e))continue;
            if(strike(c,g,p,e,EraseRules.BURST_DAMAGE))send(c,BLASTED,e,c.grounds.indexOf(g),EraseRules.BURST_DAMAGE);
        }
        for(var id:g.riders)FALLING.put(id,new Fall(now,now+SPARED));
        g.riders.clear();
    }
    /** Whether {@code viewer} is near enough to be told of call {@code c}: its user, or within VIEW of them or of a ground. */
    private static boolean near(Call c,ServerPlayerEntity viewer){
        if(viewer.getUuid().equals(c.owner)||viewer.squaredDistanceTo(c.from)<=VIEW*VIEW)return true;
        for(var g:c.grounds)if(viewer.squaredDistanceTo(g.middle())<=VIEW*VIEW)return true;
        return false;
    }
    /**
     * Tells every viewer near it (and its user, and {@code e}, wherever they are) of it: the kind, its id; marked: whose,
     * the world, when it was marked and the server's time now, where its user stood, and its grounds (each its corner,
     * its top, what it is, whom it is under and, if anyone, the top of their head where they stood); someone struck by a
     * burst: which ground's, whom, what it dealt; cut short: when.
     */
    private static void send(Call c,int type,LivingEntity e,int ground,float dealt){
        long now=c.world.getTime();
        for(var viewer:c.world.getPlayers()){
            if(viewer!=e&&!near(c,viewer))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(c.id);
            if(type==MARKED){
                b.writeUuid(c.owner);b.writeIdentifier(c.world.getRegistryKey().getValue());b.writeLong(c.marked);b.writeLong(now);
                b.writeDouble(c.from.x);b.writeDouble(c.from.y);b.writeDouble(c.from.z);
                b.writeVarInt(c.grounds.size());
                for(var g:c.grounds){
                    b.writeInt(g.x0);b.writeInt(g.z0);b.writeDouble(g.top);b.writeByte(g.kind);b.writeVarInt(g.under);
                    b.writeBoolean(g.head!=null);if(g.head!=null){b.writeDouble(g.head.x);b.writeDouble(g.head.y);b.writeDouble(g.head.z);}
                }
            }else if(type==BLASTED){
                b.writeVarInt(ground);b.writeVarInt(e.getId());b.writeFloat(dealt);
            }else b.writeLong(now);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
