package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.CounterStance;
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
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;

/**
 * Gold Experience's T, giving life (2.151.0; GoldExperienceRules has the user's words and the numbers). With the stand
 * out, T (gold_life_v1, no payload) does one of two things by what its user holds in their main hand:
 * <ul>
 * <li>An item: one of it goes out of the hand, the stand's fist touches it and it becomes a frog on its user's shoulder
 * (FROG_BORN ticks in), for FROG_LASTS ticks. Every blow on its user meanwhile ({@link #reflects}, LivingCombatMixin: a
 * blow is harm with a striker other than them) does them nothing and the striker takes all of it (at the end of the
 * tick, so no skill's own reckoning is torn under it). Then the frog is the item again, in their hand (or their
 * inventory, or at their feet). Dying, leaving or losing the stand first, it is the item at once.</li>
 * <li>The hand empty: the stand comes round and drives a fist into the first one in its way, the way its user faced as it
 * began (FLY_CONTACT ticks in, the overdrive punch's way, a guard takes it). Landing it deals FLY_PUNCH_DAMAGE and strikes
 * off one of their parts at random (the head, an arm or a leg, one still theirs); a guard taking it, one of the two arms
 * that held it up. The part becomes a fly that hovers in front of them (GoldFlyEntity): their own blow on it kills it,
 * and they take FLY_SWAT and lie knocked down FLY_DOWN ticks. Its user walks freely through the punch; struck,
 * staggered or incapacitated before it lands, or the stand gone in, stops it short.</li>
 * </ul>
 * One cooldown for both (LIFE_COOLDOWN), running from the frog's end, from the fly's death or the end of its time
 * untouched, or from the punch's end if it made none (or from where it was stopped short). It is not used while the
 * rush, the overdrive, the healing or the beatdown's punch is on, nor those while its punch is. A boss does not strike
 * into the frog (CounterStance). Every viewer within 128 blocks is told (gold_life_event_v1).
 */
public final class GoldExperienceLife {
    public static final Identifier INPUT=CelestialFall.id("gold_life_v1"),EVENT=CelestialFall.id("gold_life_event_v1");
    /** The harm a blow on the frog's bearer comes back as (its striker's), and the harm swatting the fly is. */
    public static final RegistryKey<DamageType> FROG_DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("gold_frog")),
        FLY_DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("gold_fly"));
    public static EntityType<GoldFlyEntity> FLY;
    /**
     * The event's kinds: the frog is made (the item); the frog is the item again (whether early); a blow was turned back
     * (onto whom, how much, whether it landed); the punch begins (how far off the first one in its way stands); it lands
     * (on whom, whether a guard took it, the part struck off and its fly); it is stopped short; a fly is swatted (whose,
     * which part, where); a fly is gone (the cooldown's start).
     */
    public static final int FROG=0,FROG_GONE=1,REFLECTED=2,PUNCH=3,LANDED=4,STOPPED=5,SWATTED=6,FLY_GONE=7;
    private static final int RATE=20;
    /** The ready tick while its fly lives: T waits on it. */
    static final long WAITING=Long.MAX_VALUE/4;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        long ready;
        /** The frog: on, since when, and the item it is. */
        boolean frog;long frogStart=Long.MIN_VALUE/4;ItemStack item=ItemStack.EMPTY;
        /** The punch: since when, the way it goes, whether it has landed. */
        long punchStart=Long.MIN_VALUE/4;float yaw;boolean landed=true;
        /** The fly its punch made, while it lives. */
        UUID fly;
        int window,presses;
        boolean punching(long now){return now-punchStart<FLY_PUNCH_LENGTH;}
    }
    private record Reflect(ServerPlayerEntity bearer,LivingEntity striker,float amount){}
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final List<Reflect> REFLECTS=new ArrayList<>();
    private static final List<GoldFlyEntity> SWATS=new ArrayList<>();
    private GoldExperienceLife(){}

    public static void initialize(){
        FLY=Registry.register(Registries.ENTITY_TYPE,CelestialFall.id("gold_fly"),FabricEntityTypeBuilder.<GoldFlyEntity>create(SpawnGroup.MISC,GoldFlyEntity::new)
            .dimensions(EntityDimensions.fixed(.5f,.5f)).trackRangeBlocks(64).trackedUpdateRate(2).fireImmune().disableSummon().build());
        FabricDefaultAttributeRegistry.register(FLY,GoldFlyEntity.attributes());
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->server.execute(()->{
            var s=state(player);int second=server.getTicks()/20;
            if(s.window!=second){s.window=second;s.presses=0;}
            if(++s.presses>RATE)return;
            request(player);
        }));
        ServerTickEvents.END_SERVER_TICK.register(GoldExperienceLife::tick);
        // dying, the frog is the item again before what they carry falls (or is kept)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,amount)->{if(entity instanceof ServerPlayerEntity p)endFrog(p,true);return true;});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{endFrog(handler.player,true);STATES.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var p:server.getPlayerManager().getPlayerList())endFrog(p,true);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{STATES.clear();REFLECTS.clear();SWATS.clear();});
        // a boss strikes no one the frog is on: it waits it out (2.69.0's counters)
        CounterStance.register(e->e instanceof ServerPlayerEntity p&&frogOn(p));
    }
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){var old=s;s=new State(p);if(old!=null){s.ready=old.ready;s.fly=old.fly;}STATES.put(p.getUuid(),s);}
        return s;
    }
    /** Whether T is this player's: Gold Experience's user with it out, alive, on the map. */
    public static boolean gives(PlayerEntity p){return GoldExperienceOverdrive.punches(p);}
    /** Whether this player's stand's punch is in flight now. */
    public static boolean punching(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.world==p.getWorld()&&s.punching(p.getWorld().getTime());}
    /** Whether this player's frog turns blows back now. */
    public static boolean frogOn(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s!=null&&s.frog&&s.world==p.getWorld()&&GoldExperienceRules.frogOn(s.frogStart,p.getWorld().getTime());}
    /** The punch in flight for the bosses' reading of it: {start, the tick it lands, yaw as bits}, or null (none, or landed). */
    public static long[] coming(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s==null||s.world!=p.getWorld()||s.landed||!s.punching(p.getWorld().getTime())?null:new long[]{s.punchStart,s.punchStart+FLY_CONTACT,Float.floatToIntBits(s.yaw)};
    }
    /** The tick this player's T is ready again (0 if they have not used it; far off while its fly lives). */
    public static long ready(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** This player's stand is not Gold Experience (StandManager): its cooldown is gone, and the frog is the item again. */
    public static void forget(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s==null)return;endFrog(p,true);s.ready=0;}

    /** T: the frog or the punch, by what its user holds, if it may. Returns whether it began. */
    public static boolean request(ServerPlayerEntity p){
        if(!gives(p)||p.isSleeping()||p.hasVehicle()||CombatManager.busy(p)||CombatManager.cinematic(p))return false;
        if(GoldExperienceHeal.active(p)||GoldExperienceOverdrive.punching(p)||StarPlatinumRush.rushing(p)||GoldExperienceBeatdown.opening(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(s.frog||s.punching(now)||s.fly!=null)return false;
        if(now<s.ready&&!NoCooldownState.enabled(p))return false;
        var held=p.getMainHandStack();
        if(!held.isEmpty()){
            // one of it goes out of the hand: it is the frog now
            s.item=held.split(1);p.getInventory().markDirty();
            s.frog=true;s.frogStart=now;
            long end=now+FROG_END;s.ready=NoCooldownState.enabled(p)?end:lifeReady(end);
            send(p,s,FROG,now,null,0,false,-1,-1);
            return true;
        }
        s.punchStart=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.landed=false;s.ready=WAITING;
        send(p,s,PUNCH,now,null,0,false,-1,-1);
        return true;
    }

    private static void tick(MinecraftServer server){
        // the blows turned back and the flies swatted this tick, now that every skill has reckoned its own
        if(!REFLECTS.isEmpty())reflect();
        if(!SWATS.isEmpty())swat();
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null){if(s.fly==null)STATES.remove(e.getKey());continue;}
            if(p.getServerWorld()!=s.world){endFrog(p,s,s.world.getTime(),true);s.punchStart=Long.MIN_VALUE/4;s.landed=true;continue;}
            long now=p.getWorld().getTime();
            if(s.frog){
                boolean keeps=p.isAlive()&&!p.isSpectator()&&MapGate.enabled(p)&&StandManager.goldExperience(p);
                if(!keeps||now>=s.frogStart+FROG_END)endFrog(p,s,now,!keeps);
            }
            if(!s.landed&&s.punching(now)){
                if(!gives(p)||HitStun.stunned(p)||CombatManager.actionLocked(p)||StarPlatinumRush.rushing(p))stop(p,s,now);
                else if(now>=s.punchStart+FLY_CONTACT)strike(p,s,now);
            }
            // its fly gone without a word (its chunk unloaded as it went): its cooldown runs from now
            if(s.fly!=null&&!(s.world.getEntity(s.fly) instanceof GoldFlyEntity f&&!f.ended))flyEnded(p,s,now);
        }
    }
    /** The frog is the item again now (early: before its time, its cooldown running from now). */
    private static void endFrog(ServerPlayerEntity p,boolean early){var s=STATES.get(p.getUuid());if(s!=null)endFrog(p,s,p.getWorld().getTime(),early);}
    private static void endFrog(ServerPlayerEntity p,State s,long now,boolean early){
        if(!s.frog)return;
        s.frog=false;
        var item=s.item;s.item=ItemStack.EMPTY;
        if(!item.isEmpty())give(p,item);
        if(early)s.ready=NoCooldownState.enabled(p)?now:lifeReady(now);
        send(p,s,FROG_GONE,now,null,0,early,-1,-1);
    }
    /** The item back in {@code p}'s hand: the hand empty, into it; the same item, onto it; else into their inventory, or at their feet. */
    static void give(ServerPlayerEntity p,ItemStack item){
        var inv=p.getInventory();var hand=inv.getStack(inv.selectedSlot);
        if(hand.isEmpty()){inv.setStack(inv.selectedSlot,item);return;}
        if(ItemStack.canCombine(hand,item)&&hand.getCount()+item.getCount()<=hand.getMaxCount()){hand.increment(item.getCount());inv.markDirty();return;}
        inv.offerOrDrop(item);
    }
    private static void stop(ServerPlayerEntity p,State s,long now){
        s.punchStart=Long.MIN_VALUE/4;s.landed=true;s.ready=NoCooldownState.enabled(p)?now:lifeReady(now);
        send(p,s,STOPPED,now,null,0,false,-1,-1);
    }
    /** A blow landed on this player: their stand's punch not yet landed is stopped short, its cooldown running from now (HitStun.attackerDamaged, as the overdrive's). */
    public static void struck(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());long now=p.getWorld().getTime();
        if(s==null||s.world!=p.getServerWorld()||s.landed||!s.punching(now))return;
        stop(p,s,now);
    }
    /** The first one in the punch's way (the nearest, to its near side), in sight, or null: as the overdrive punch's. */
    private static LivingEntity first(ServerPlayerEntity p,float yaw){
        var world=p.getServerWorld();Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        LivingEntity first=null;double best=Double.MAX_VALUE;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(StarPlatinumRules.REACH_BIG+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);double ahead=d.dotProduct(f),half=e.getWidth()*.5;
            if(!lifeLane(ahead,d.dotProduct(right),d.y,half)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            if(ahead-half<best){best=ahead-half;first=e;}
        }
        return first;
    }
    /**
     * The punch lands on the first one in its way: landing, it deals its damage and strikes off a part of them; a guard
     * taking it, one of the two arms that held it up. The part becomes the fly.
     */
    private static void strike(ServerPlayerEntity p,State s,long now){
        s.landed=true;
        var world=p.getServerWorld();var e=first(p,s.yaw);
        long end=s.punchStart+FLY_PUNCH_LENGTH;boolean free=NoCooldownState.enabled(p);
        if(e==null){s.ready=free?end:lifeReady(end);send(p,s,LANDED,now,null,0,false,-1,-1);return;}
        boolean guarded=CombatManager.guardTakes(e,p.getPos(),false);
        e.timeUntilRegen=0;
        boolean landed=CombatManager.damageWithoutKnockback(e,world.getDamageSources().playerAttack(p),FLY_PUNCH_DAMAGE);
        if(landed){
            p.onAttacking(e);
            world.playSound(null,e.getX(),e.getY(),e.getZ(),SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,SoundCategory.PLAYERS,.8f,.9f);
            if(!StarPlatinumTimeStop.deferFeedback(p,e,FLY_PUNCH_DAMAGE,s.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(FLY_PUNCH_DAMAGE);b.writeFloat(s.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);}
        }
        int part=guarded?pick(e,true):landed?pick(e,false):-1;
        if(part<0||!e.isAlive()){s.ready=free?end:lifeReady(end);send(p,s,LANDED,now,e,0,guarded,-1,-1);return;}
        var fly=FLY.create(world);
        if(fly==null){s.ready=free?end:lifeReady(end);send(p,s,LANDED,now,e,0,guarded,-1,-1);return;}
        float facing=GoldFlyEntity.facing(e);
        double[] at=GoldFlyPath.world(e.getX(),e.getY(),e.getZ(),e.getBodyYaw(),GoldFlyPath.socket(part,e.getHeight()));
        int seed=(int)(now*31+e.getId()*7919L+p.getId());
        fly.setup(e,p.getUuid(),part,now,seed,new Vec3d(at[0],at[1],at[2]),facing);
        if(!world.spawnEntity(fly)){s.ready=free?end:lifeReady(end);send(p,s,LANDED,now,e,0,guarded,-1,-1);return;}
        s.fly=fly.getUuid();s.ready=WAITING;
        send(p,s,LANDED,now,e,0,guarded,part,fly.getId());
    }
    /** A part of {@code e} to strike off at random (one still theirs, if any is): with {@code arms}, one of the arms. */
    private static int pick(LivingEntity e,boolean arms){
        boolean[] gone=new boolean[PARTS];
        for(var f:e.getWorld().getEntitiesByClass(GoldFlyEntity.class,e.getBoundingBox().expand(8),f->f.ownedBy(e)&&!f.ended))gone[f.part()]=true;
        int[] from=arms?new int[]{RIGHT_ARM,LEFT_ARM}:new int[]{HEAD,RIGHT_ARM,LEFT_ARM,RIGHT_LEG,LEFT_LEG};
        List<Integer> left=new ArrayList<>();for(int k:from)if(!gone[k])left.add(k);
        var random=e.getRandom();
        return left.isEmpty()?from[random.nextInt(from.length)]:left.get(random.nextInt(left.size()));
    }

    // ───────────────────────────── the frog turns blows back ─────────────────────────────

    /**
     * A blow of {@code amount} from {@code source} about to come to {@code victim} (LivingCombatMixin, as the blow comes):
     * if the frog is on them it does them nothing (returns true) and comes back on its striker, all of it, at the end of
     * the tick. Not what bypasses invulnerability (/kill, the void), harm with no striker, their own, another frog's
     * turned back, or the fly's.
     */
    public static boolean reflects(LivingEntity victim,DamageSource source,float amount){
        if(STATES.isEmpty()||victim==null||victim.getWorld().isClient||!(victim instanceof ServerPlayerEntity p)||!frogOn(p))return false;
        if(source==null||!Float.isFinite(amount)||amount<=0||source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)||source.isOf(FROG_DAMAGE)||source.isOf(FLY_DAMAGE))return false;
        if(!(source.getAttacker() instanceof LivingEntity striker)||striker==p||!striker.isAlive())return false;
        REFLECTS.add(new Reflect(p,striker,reflected(amount)));
        return true;
    }
    private static void reflect(){
        // a tick's blows from one striker on one bearer come back as one
        Map<List<Object>,Float> sums=new LinkedHashMap<>();
        for(var r:REFLECTS)sums.merge(List.of(r.bearer(),r.striker()),r.amount(),Float::sum);
        REFLECTS.clear();
        for(var e:sums.entrySet()){
            var p=(ServerPlayerEntity)e.getKey().get(0);var striker=(LivingEntity)e.getKey().get(1);float amount=e.getValue();
            if(p.isRemoved()||striker.isRemoved()||!striker.isAlive()||striker.getWorld()!=p.getWorld())continue;
            var world=p.getServerWorld();
            var source=new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FROG_DAMAGE),p,p);
            striker.timeUntilRegen=0;
            boolean landed=CombatManager.damageWithoutKnockback(striker,source,amount);
            if(landed&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(striker.getId());b.writeFloat(amount);
                b.writeFloat((float)Math.toDegrees(Math.atan2(-(striker.getX()-p.getX()),striker.getZ()-p.getZ())));
                ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
            var s=STATES.get(p.getUuid());
            if(s!=null)send(p,s,REFLECTED,world.getTime(),striker,amount,landed,-1,-1);
        }
    }

    // ───────────────────────────── the fly ─────────────────────────────

    /** Its owner's blow swatted {@code fly} (GoldFlyEntity.damage): at the end of the tick it dies and they take its harm. */
    static void swatted(GoldFlyEntity fly){if(!SWATS.contains(fly))SWATS.add(fly);}
    private static void swat(){
        var list=new ArrayList<>(SWATS);SWATS.clear();
        for(var fly:list){
            if(fly.ended)continue;
            var owner=fly.owner();var world=(ServerWorld)fly.getWorld();
            var maker=fly.maker()==null?null:world.getServer().getPlayerManager().getPlayer(fly.maker());
            Vec3d where=fly.getPos().add(0,fly.getHeight()/2,0);
            if(owner!=null&&owner.isAlive()){
                var source=new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FLY_DAMAGE),maker!=null&&maker.getWorld()==world?maker:null);
                owner.timeUntilRegen=0;
                boolean landed=CombatManager.damageWithoutKnockback(owner,source,FLY_SWAT);
                // knocked down: it can do nothing a second, its legs stopped
                CombatManager.incapacitate(owner,FLY_DOWN,FLY_DOWN,1);
                world.playSound(null,owner.getX(),owner.getY(),owner.getZ(),SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,SoundCategory.PLAYERS,.9f,.7f);
                if(landed&&maker!=null&&ServerPlayNetworking.canSend(maker,KagenoManager.DAMAGE)){
                    var b=PacketByteBufs.create();b.writeVarInt(owner.getId());b.writeFloat(FLY_SWAT);b.writeFloat(owner.getHeadYaw());
                    ServerPlayNetworking.send(maker,KagenoManager.DAMAGE,b);
                }
                tellSwat(world,fly,owner,where);
            }
            flyGone(fly,true);
        }
    }
    /**
     * {@code fly} is gone ({@code swatted}, or its time up, or whom it belongs to gone, or its chunk): it leaves the world,
     * and its maker's cooldown runs from now.
     */
    static void flyGone(GoldFlyEntity fly,boolean swatted){
        if(fly.ended)return;
        fly.end();
        if(!fly.isRemoved())fly.discard();
        if(fly.maker()==null)return;
        var s=STATES.get(fly.maker());
        if(s==null||!fly.getUuid().equals(s.fly))return;
        var maker=fly.getServer()==null?null:fly.getServer().getPlayerManager().getPlayer(fly.maker());
        flyEnded(maker,s,fly.getWorld().getTime());
    }
    private static void flyEnded(ServerPlayerEntity maker,State s,long now){
        s.fly=null;
        s.ready=maker!=null&&NoCooldownState.enabled(maker)?now:lifeReady(now);
        if(maker!=null&&maker.getServerWorld()==s.world)send(maker,s,FLY_GONE,now,null,0,false,-1,-1);
    }
    /**
     * Whether {@code attacker}'s strike at {@code target} meets its own fly first (a training dummy's blow, which goes
     * straight at whom it strikes rather than down a way: TrainingDummyEntity): the fly swattable and in the way, within
     * the strike's reach. If so it is swatted and the strike goes no further.
     */
    public static boolean swatsInstead(LivingEntity attacker,LivingEntity target){
        if(attacker==null||attacker.getWorld().isClient||target==null)return false;
        Vec3d to=target.getPos().subtract(attacker.getPos());
        return swatsAlong(attacker,to,Math.min(Math.hypot(to.x,to.z),3.2));
    }
    /**
     * Whether {@code attacker}'s blow along {@code way} (level), reaching {@code reach} blocks, meets its own fly first
     * (GoldExperienceRules.swatWay): a player's bare-handed punch (CombatManager.hit) and a training dummy's
     * (swatsInstead). If so it is swatted and the blow goes no further.
     */
    public static boolean swatsAlong(LivingEntity attacker,Vec3d way,double reach){
        if(attacker==null||attacker.getWorld().isClient)return false;
        double far=Math.hypot(way.x,way.z);
        if(far<1e-4)return false;
        Vec3d at=attacker.getPos(),f=new Vec3d(way.x/far,0,way.z/far),right=new Vec3d(-f.z,0,f.x);
        for(var fly:attacker.getWorld().getEntitiesByClass(GoldFlyEntity.class,attacker.getBoundingBox().expand(4),x->x.ownedBy(attacker)&&x.swattable())){
            Vec3d d=fly.getPos().subtract(at);
            if(!GoldExperienceRules.swatWay(d.dotProduct(f),d.dotProduct(right),d.y,reach))continue;
            fly.swatted=true;swatted(fly);
            return true;
        }
        return false;
    }
    /** Whether {@code e} is a fly that is not {@code viewer}'s: it is not there for them (their sight and their blows pass through it). */
    public static boolean passes(net.minecraft.entity.Entity viewer,net.minecraft.entity.Entity e){return e instanceof GoldFlyEntity f&&!f.ownedBy(viewer);}

    // ───────────────────────────── telling ─────────────────────────────

    /**
     * Tells every player near the user (and the user, and {@code whom}) of an event: {@code whom} the striker the frog
     * turned a blow back on ({@code amount}, {@code landed}), or the one the punch landed on (whether a guard took it in
     * {@code landed}, the part struck off and the fly's entity id).
     */
    private static void send(ServerPlayerEntity p,State s,int type,long now,LivingEntity whom,float amount,boolean flag,int part,int fly){
        var world=p.getServerWorld();
        float aim=Float.NaN;
        if(type==PUNCH){var e=first(p,s.yaw);if(e!=null)aim=(float)(e.getPos().subtract(p.getPos()).dotProduct(Vec3d.fromPolar(0,s.yaw))-e.getWidth()*.5);}
        long start=type==FROG||type==FROG_GONE||type==REFLECTED?s.frogStart:s.punchStart;
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer!=whom&&viewer.squaredDistanceTo(p)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());
            b.writeLong(start);b.writeLong(now);b.writeFloat(s.yaw);b.writeFloat(aim);b.writeLong(s.ready==WAITING||NoCooldownState.enabled(p)?0:s.ready);
            b.writeInt(whom==null?-1:whom.getId());b.writeFloat(amount);b.writeBoolean(flag);b.writeByte(part);b.writeInt(fly);
            b.writeItemStack(type==FROG?s.item:ItemStack.EMPTY);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    /** Tells every player near whom a fly belongs to that their fly was swatted: which part, where. */
    private static void tellSwat(ServerWorld world,GoldFlyEntity fly,LivingEntity owner,Vec3d where){
        for(var viewer:world.getPlayers()){
            if(viewer!=owner&&viewer.squaredDistanceTo(owner)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(SWATTED);b.writeUuid(fly.maker()==null?new UUID(0,0):fly.maker());b.writeIdentifier(world.getRegistryKey().getValue());
            b.writeLong(fly.born());b.writeLong(world.getTime());b.writeFloat(0);b.writeFloat(Float.NaN);b.writeLong(0);
            b.writeInt(owner.getId());b.writeFloat(FLY_SWAT);b.writeBoolean(true);b.writeByte(fly.part());b.writeInt(fly.getId());
            b.writeItemStack(ItemStack.EMPTY);
            b.writeDouble(where.x);b.writeDouble(where.y);b.writeDouble(where.z);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
