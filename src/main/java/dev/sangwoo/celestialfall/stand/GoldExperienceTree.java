package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.map.MapGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;

/**
 * Gold Experience's H, the tree of life (2.152.0; GoldExperienceRules has the user's words and the numbers). With the
 * stand out and its user on the ground, H (gold_tree_v1, no payload) has the stand kneel at their side and lay its palm on
 * the ground: its user stands held where they are (they may do nothing) until TREE_BURST, when a thick tree bursts up out
 * of the ground under them ({@link GoldTreeEntity}):
 * <ul>
 * <li>Everyone round its trunk (TREE_RADIUS, TREE_BELOW to TREE_ABOVE of its foot; in sight of it, by the PvP rules)
 * takes TREE_DAMAGE and is pushed out from it; a guard up toward it takes it (no harm, no push).</li>
 * <li>Its user is flung up off it: TREE_RISE blocks high, on the way they faced by TREE_AHEAD as they come down, steering
 * as anyone in the air; the fall does them no harm ({@link #spared}: until they have stood on the ground again).</li>
 * <li>It stands until TREE_WITHER: every blow on it comes back, all of it, on whoever struck it, at the end of the tick
 * (harm of its own, celestialfall:gold_tree: no armour, shield or guard keeps it off). Its maker's own blows pass by.</li>
 * <li>It withers and is back in the ground by TREE_END; then the cooldown runs. Its maker dead, gone, off the map or no
 * longer Gold Experience's, it withers at once.</li>
 * </ul>
 * Before the tree bursts up, its user struck, staggered, incapacitated, caught by anything that holds them, or the stand
 * gone in, stops it: no tree, the cooldown running from then. Not while the rush, the overdrive punch, the healing, the
 * beatdown's opening punch or T's punch is on, nor those while it is held. Every viewer within 128 blocks is told
 * (gold_tree_event_v1): the touch, the burst (whom it struck, whose guard took it), a blow turned back, a tree gone
 * early, and the cooldown.
 */
public final class GoldExperienceTree {
    public static final Identifier INPUT=CelestialFall.id("gold_tree_v1"),EVENT=CelestialFall.id("gold_tree_event_v1");
    /** The harm a blow on the tree comes back as (on whoever struck it). */
    public static final RegistryKey<DamageType> TREE_HARM=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("gold_tree"));
    public static EntityType<GoldTreeEntity> TREE;
    /**
     * The event's kinds: the stand's palm goes to the ground (where they stand, the way they face); the tree burst up (its
     * entity, whom it struck and whose guard took it); stopped short before it did; a blow on it turned back (onto whom,
     * how much, whether it landed); the tree began to wither early (its maker gone).
     */
    public static final int TOUCH=0,BURST=1,STOPPED=2,REFLECTED=3,WITHERED=4;
    private static final int RATE=20;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** Since when it is on (the press), the way they faced, where they stood, whether the tree has burst up; the tick H is ready again. */
        long start=Long.MIN_VALUE/4,ready;float yaw;Vec3d spot;boolean burst=true;
        /** The tree it brought up, while it stands. */
        GoldTreeEntity tree;
        int window,presses;
        boolean holding(long now){return !burst&&now-start<TREE_BURST;}
    }
    /** A tree standing in a world: when it is to be gone. */
    private static final class Grown {
        final GoldTreeEntity tree;final ServerWorld world;final UUID maker;long end;
        Grown(GoldTreeEntity tree,ServerWorld world,UUID maker,long end){this.tree=tree;this.world=world;this.maker=maker;this.end=end;}
    }
    private record Reflect(GoldTreeEntity tree,ServerPlayerEntity maker,LivingEntity striker,float amount){}
    /** One flung up: spared the fall's harm until they have stood on the ground again a moment (or until). */
    private static final class Leap {final long until;boolean left;int grounded;Leap(long until){this.until=until;}}
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final List<Grown> TREES=new ArrayList<>();
    private static final List<Reflect> REFLECTS=new ArrayList<>();
    private static final Map<UUID,Leap> LEAPS=new HashMap<>();
    private GoldExperienceTree(){}

    public static void initialize(){
        TREE=Registry.register(Registries.ENTITY_TYPE,CelestialFall.id("gold_tree"),FabricEntityTypeBuilder.<GoldTreeEntity>create(SpawnGroup.MISC,GoldTreeEntity::new)
            .dimensions(EntityDimensions.fixed(1.6f,6.5f)).trackRangeBlocks(160).trackedUpdateRate(20).fireImmune().disableSummon().build());
        FabricDefaultAttributeRegistry.register(TREE,GoldTreeEntity.attributes());
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->server.execute(()->{
            var s=state(player);int second=server.getTicks()/20;
            if(s.window!=second){s.window=second;s.presses=0;}
            if(++s.presses>RATE)return;
            request(player);
        }));
        ServerTickEvents.END_SERVER_TICK.register(GoldExperienceTree::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(entity instanceof ServerPlayerEntity p)makerGone(p);});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{makerGone(handler.player);STATES.remove(handler.player.getUuid());LEAPS.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var g:TREES){g.tree.end();g.tree.discard();}TREES.clear();});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{STATES.clear();TREES.clear();REFLECTS.clear();LEAPS.clear();});
    }
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){var old=s;s=new State(p);if(old!=null)s.ready=old.ready;STATES.put(p.getUuid(),s);}
        return s;
    }
    /** Whether H is this player's tree: Gold Experience's user with it out, alive, on the map. */
    public static boolean grows(PlayerEntity p){return GoldExperienceOverdrive.punches(p);}
    /** Whether this player's stand is laying its palm on the ground now (they stand held, and may do nothing). */
    public static boolean holding(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.world==p.getWorld()&&s.holding(p.getWorld().getTime());}
    /** Whether this player's tree stands now. */
    public static boolean standing(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.tree!=null&&!s.tree.isRemoved();}
    /** The tree coming for the bosses' reading of it: {start, the tick it bursts up, where (x, z as bits)}, or null. */
    public static long[] coming(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        if(s==null||s.world!=p.getWorld()||!s.holding(p.getWorld().getTime())||s.spot==null)return null;
        return new long[]{s.start,s.start+TREE_BURST,Double.doubleToLongBits(s.spot.x),Double.doubleToLongBits(s.spot.z),Double.doubleToLongBits(s.spot.y)};
    }
    /** The tick this player's H is ready again (0 if they have not used it). */
    public static long ready(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player's stand is not Gold Experience (StandManager): its cooldown is gone, and a tree of theirs withers. */
    public static void forget(PlayerEntity p){
        var s=STATES.get(p.getUuid());if(s==null)return;
        if(p instanceof ServerPlayerEntity sp)makerGone(sp);
        s.ready=0;
    }
    /** Whether {@code e}'s fall now does it no harm: the tree flung them up and they have not stood on the ground again since. */
    public static boolean spared(LivingEntity e){
        if(e==null||LEAPS.isEmpty()||e.getWorld().isClient)return false;
        var l=LEAPS.get(e.getUuid());return l!=null&&e.getWorld().getTime()<=l.until;
    }

    private static boolean may(ServerPlayerEntity p,State s,long now){
        if(!grows(p)||!p.isOnGround()||p.isSleeping()||p.hasVehicle()||CombatManager.busy(p)||CombatManager.cinematic(p)||CombatManager.actionLocked(p))return false;
        if(GoldExperienceHeal.active(p)||GoldExperienceOverdrive.punching(p)||StarPlatinumRush.rushing(p)||GoldExperienceBeatdown.opening(p)||GoldExperienceLife.punching(p))return false;
        if(StarPlatinumTimeStop.holds(p)||StarPlatinumBeatdown.involved(p))return false;
        if(s.holding(now)||s.tree!=null&&!s.tree.isRemoved())return false;
        return now>=s.ready||NoCooldownState.enabled(p);
    }
    /** H: the stand lays its palm on the ground, if it may. Returns whether it did. */
    public static boolean request(ServerPlayerEntity p){
        var s=state(p);long now=p.getWorld().getTime();
        if(!may(p,s,now))return false;
        s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.spot=p.getPos();s.burst=false;s.tree=null;
        s.ready=NoCooldownState.enabled(p)?now+TREE_END:treeReady(now+TREE_END);
        p.setSprinting(false);p.clearActiveItem();KagenoManager.freeze(p);
        send(p,s,TOUCH,now,null,null,0,false);
        return true;
    }
    /** A blow landed on this player (HitStun.attackerDamaged): their tree not yet up is stopped short. */
    public static void struck(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());long now=p.getWorld().getTime();
        if(s==null||s.world!=p.getServerWorld()||!s.holding(now))return;
        stop(p,s,now);
    }
    private static void stop(ServerPlayerEntity p,State s,long now){
        s.burst=true;s.start=Long.MIN_VALUE/4;s.ready=NoCooldownState.enabled(p)?now:treeReady(now);
        send(p,s,STOPPED,now,null,null,0,false);
    }

    private static void tick(MinecraftServer server){
        // the blows turned back this tick, now that every skill has reckoned its own
        if(!REFLECTS.isEmpty())reflect();
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world)continue;
            long now=p.getWorld().getTime();
            if(s.tree!=null&&s.tree.isRemoved())s.tree=null;
            if(!s.holding(now)){if(!s.burst&&now-s.start>=TREE_BURST)burst(p,s,now);continue;}
            // held with the stand's palm on the ground: caught by anything that holds them, or the stand gone in, it is stopped
            if(!grows(p)||HitStun.stunned(p)||CombatManager.incapacitated(p)||StarPlatinumTimeStop.holds(p)||StarPlatinumBeatdown.involved(p)
                ||CombatManager.cinematic(p)||p.hasVehicle()||dev.sangwoo.celestialfall.AtomicManager.immobilized(p)){stop(p,s,now);continue;}
            KagenoManager.freeze(p);
        }
        for(var g:new ArrayList<>(TREES)){
            long now=g.world.getTime();
            if(g.tree.isRemoved()){TREES.remove(g);continue;}
            if(now>=g.end){TREES.remove(g);g.tree.end();g.tree.discard();continue;}
            // its maker gone, it withers at once
            if(g.tree.standing()&&!holds(g)){
                g.tree.witherNow(now);g.end=Math.min(g.end,now+TREE_END-TREE_WITHER);
                var maker=g.world.getServer().getPlayerManager().getPlayer(g.maker);
                tellWithered(g,now);
                if(maker!=null){var s=STATES.get(g.maker);if(s!=null&&s.tree==g.tree)s.ready=NoCooldownState.enabled(maker)?g.end:treeReady(g.end);}
            }
        }
        if(!LEAPS.isEmpty())for(var e:new ArrayList<>(LEAPS.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var l=e.getValue();
            if(p==null||p.getWorld().getTime()>l.until){LEAPS.remove(e.getKey());continue;}
            if(!p.isOnGround())l.left=true;
            else if(l.left&&++l.grounded>=3)LEAPS.remove(e.getKey());
        }
    }
    /** Whether a tree's maker still holds it up: there, alive, on the map, with Gold Experience. */
    private static boolean holds(Grown g){
        var p=g.world.getServer().getPlayerManager().getPlayer(g.maker);
        return p!=null&&p.isAlive()&&!p.isSpectator()&&p.getServerWorld()==g.world&&MapGate.enabled(p)&&StandManager.goldExperience(p);
    }
    /** Its maker died, left or is no longer Gold Experience's: their tree withers now. */
    private static void makerGone(ServerPlayerEntity p){
        for(var g:TREES)if(g.maker.equals(p.getUuid())&&g.tree.standing()){
            long now=g.world.getTime();g.tree.witherNow(now);g.end=Math.min(g.end,now+TREE_END-TREE_WITHER);tellWithered(g,now);
            var s=STATES.get(p.getUuid());if(s!=null&&s.tree==g.tree)s.ready=NoCooldownState.enabled(p)?g.end:treeReady(g.end);
        }
        var s=STATES.get(p.getUuid());
        if(s!=null&&p.getServerWorld()==s.world&&s.holding(p.getWorld().getTime()))stop(p,s,p.getWorld().getTime());
    }
    /** A tree taken out of the world (its chunk unloaded, /kill): its maker's cooldown runs from now. */
    static void gone(GoldTreeEntity tree){
        if(tree.ended)return;
        tree.end();
        var world=(ServerWorld)tree.getWorld();long now=world.getTime();
        TREES.removeIf(g->g.tree==tree);
        if(!tree.isRemoved())tree.discard();
        var maker=tree.maker==null?null:world.getServer().getPlayerManager().getPlayer(tree.maker);
        if(maker!=null){var s=STATES.get(tree.maker);if(s!=null&&s.tree==tree){s.tree=null;s.ready=NoCooldownState.enabled(maker)?now:treeReady(now);}}
    }

    /**
     * The tree bursts up under its user: it strikes everyone round it (a guard up toward it takes it), pushing them out,
     * and flings its user up.
     */
    private static void burst(ServerPlayerEntity p,State s,long now){
        s.burst=true;
        var world=p.getServerWorld();Vec3d at=p.getPos();s.spot=at;
        var tree=TREE.create(world);
        if(tree!=null){
            tree.setup(p,now,(int)(now*31+p.getId()*7919L),s.yaw,at);
            if(world.spawnEntity(tree)){s.tree=tree;TREES.add(new Grown(tree,world,p.getUuid(),now+TREE_END-TREE_BURST));}
        }
        var source=world.getDamageSources().playerAttack(p);
        List<LivingEntity> struck=new ArrayList<>();List<Boolean> guarded=new ArrayList<>();
        Vec3d from=at.add(0,1,0);
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(TREE_RADIUS+2,TREE_ABOVE+2,TREE_RADIUS+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)&&!(e instanceof GoldTreeEntity)&&!(e instanceof GoldFlyEntity)
            &&!(e instanceof PlayerEntity q&&dev.sangwoo.celestialfall.map.VanillaMode.on(q)))){
            Vec3d d=e.getPos().subtract(at);
            if(!treeArea(d.x,d.y,d.z,e.getWidth()*.5))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            // (not through a wall: the ground bursts up round the trunk, in sight of it)
            Vec3d chest=e.getPos().add(0,e.getHeight()*.5,0);
            if(world.raycast(new RaycastContext(from,chest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            if(CombatManager.guardTakes(e,at,true)){struck.add(e);guarded.add(true);continue;}
            e.timeUntilRegen=0;
            boolean landed=CombatManager.damageWithoutKnockback(e,source,TREE_DAMAGE);
            if(!landed)continue;
            p.onAttacking(e);
            // pushed out from the trunk (one stopped in a time stop, as time moves again)
            double[] out=treeOut(d.x,d.z,s.yaw);
            Vec3d push=new Vec3d(out[0]*TREE_PUSH,TREE_PUSH_UP,out[1]*TREE_PUSH);
            if(!StarPlatinumTimeStop.deferLaunch(e,push)){e.setVelocity(push);e.velocityModified=true;}
            float toward=(float)Math.toDegrees(Math.atan2(-d.x,d.z));
            if(!StarPlatinumTimeStop.deferFeedback(p,e,TREE_DAMAGE,toward)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(TREE_DAMAGE);b.writeFloat(toward);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
            struck.add(e);guarded.add(false);
        }
        // its user flung up off it: high, on the way they faced, and spared the fall
        double[] leap=treeLeap();Vec3d f=Vec3d.fromPolar(0,s.yaw);
        p.setVelocity(f.x*leap[0],leap[1],f.z*leap[0]);p.velocityModified=true;p.fallDistance=0;
        LEAPS.put(p.getUuid(),new Leap(now+TREE_SPARED));
        send(p,s,BURST,now,struck,guarded,0,false);
    }

    // ───────────────────────────── the tree turns blows back ─────────────────────────────

    /**
     * A blow of {@code amount} from {@code source} on {@code tree} while it stands (GoldTreeEntity.damage): it comes back,
     * all of it, on whoever struck it, at the end of the tick. Not its maker's own blow (theirs passes by), harm with no
     * striker, nor what another tree, frog or fly turned back or dealt; a player its maker may not harm (the PvP rules)
     * strikes it to no end.
     */
    static void struck(GoldTreeEntity tree,DamageSource source,float amount){
        if(source==null||!Float.isFinite(amount)||amount<=0||source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        if(source.isOf(TREE_HARM)||source.isOf(GoldExperienceLife.FROG_DAMAGE)||source.isOf(GoldExperienceLife.FLY_DAMAGE))return;
        if(!(source.getAttacker() instanceof LivingEntity striker)||!striker.isAlive()||tree.ownedBy(striker)||striker instanceof GoldTreeEntity)return;
        var world=(ServerWorld)tree.getWorld();
        var maker=tree.maker()==null?null:world.getServer().getPlayerManager().getPlayer(tree.maker());
        if(maker==null||maker.getServerWorld()!=world)return;
        if(striker instanceof PlayerEntity other&&!maker.shouldDamagePlayer(other))return;
        REFLECTS.add(new Reflect(tree,maker,striker,amount));
    }
    private static void reflect(){
        // a tick's blows from one striker on one tree come back as one
        Map<List<Object>,Float> sums=new LinkedHashMap<>();
        Map<List<Object>,Reflect> first=new HashMap<>();
        for(var r:REFLECTS){var k=List.<Object>of(r.tree(),r.striker());sums.merge(k,r.amount(),Float::sum);first.putIfAbsent(k,r);}
        REFLECTS.clear();
        for(var e:sums.entrySet()){
            var r=first.get(e.getKey());float amount=e.getValue();
            var tree=r.tree();var maker=r.maker();var striker=r.striker();
            if(maker.isRemoved()||striker.isRemoved()||!striker.isAlive()||striker.getWorld()!=tree.getWorld())continue;
            var world=(ServerWorld)tree.getWorld();
            var source=new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(TREE_HARM),maker,maker);
            striker.timeUntilRegen=0;
            boolean landed=CombatManager.damageWithoutKnockback(striker,source,amount);
            if(landed&&ServerPlayNetworking.canSend(maker,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(striker.getId());b.writeFloat(amount);
                b.writeFloat((float)Math.toDegrees(Math.atan2(-(striker.getX()-tree.getX()),striker.getZ()-tree.getZ())));
                ServerPlayNetworking.send(maker,KagenoManager.DAMAGE,b);
            }
            tellReflected(tree,maker,striker,amount,landed,world.getTime());
        }
    }

    // ───────────────────────────── telling ─────────────────────────────

    /**
     * Tells every player near the user (and the user, and whom it struck) of an event: for the burst, the tree's entity and
     * whom it struck ({@code guarded}: whose guard took it).
     */
    private static void send(ServerPlayerEntity p,State s,int type,long now,List<LivingEntity> struck,List<Boolean> guarded,float amount,boolean flag){
        var world=p.getServerWorld();
        Vec3d at=s.spot==null?p.getPos():s.spot;
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&(struck==null||!struck.contains(viewer))&&viewer.squaredDistanceTo(at)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(s.yaw);
            b.writeDouble(at.x);b.writeDouble(at.y);b.writeDouble(at.z);
            b.writeLong(NoCooldownState.enabled(p)?0:s.ready);
            b.writeInt(s.tree==null?-1:s.tree.getId());
            int n=struck==null?0:struck.size();b.writeVarInt(n);
            for(int i=0;i<n;i++){b.writeInt(struck.get(i).getId());b.writeBoolean(guarded.get(i));}
            b.writeInt(-1);b.writeFloat(amount);b.writeBoolean(flag);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    /** Tells every player near a tree that it turned a blow back: onto whom, how much, whether it landed. */
    private static void tellReflected(GoldTreeEntity tree,ServerPlayerEntity maker,LivingEntity striker,float amount,boolean landed,long now){
        var s=STATES.get(maker.getUuid());
        tell(tree,maker.getUuid(),REFLECTED,now,s==null?tree.born()-TREE_BURST:s.start,s==null?0:NoCooldownState.enabled(maker)?0:s.ready,striker,amount,landed);
    }
    private static void tellWithered(Grown g,long now){
        var s=STATES.get(g.maker);var maker=g.world.getServer().getPlayerManager().getPlayer(g.maker);
        tell(g.tree,g.maker,WITHERED,now,g.tree.born()-TREE_BURST,s==null||maker!=null&&NoCooldownState.enabled(maker)?0:s.ready,null,0,true);
    }
    private static void tell(GoldTreeEntity tree,UUID maker,int type,long now,long start,long ready,LivingEntity whom,float amount,boolean flag){
        var world=(ServerWorld)tree.getWorld();
        for(var viewer:world.getPlayers()){
            if(viewer!=whom&&!viewer.getUuid().equals(maker)&&viewer.squaredDistanceTo(tree)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(maker);b.writeIdentifier(world.getRegistryKey().getValue());
            b.writeLong(start);b.writeLong(now);b.writeFloat(tree.facing());
            b.writeDouble(tree.getX());b.writeDouble(tree.getY());b.writeDouble(tree.getZ());
            b.writeLong(ready);
            b.writeInt(tree.getId());
            b.writeVarInt(0);
            b.writeInt(whom==null?-1:whom.getId());b.writeFloat(amount);b.writeBoolean(flag);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
