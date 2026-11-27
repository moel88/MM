package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.map.MapGate;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;

/**
 * The fly Gold Experience's punch makes of a part of whom it strikes (2.151.0, T with the hand empty:
 * GoldExperienceRules has the user's words). On the server it is the body that one's own blows meet: it flies where
 * {@link GoldFlyPath} has it, in front of them, and only their blows touch it ("적이 그 파리를 한 번 때리면"): anyone
 * else's sight passes through it (LivingEntity.canSee, GoldFlySightMixin; nearly every skill finds its targets by sight)
 * and their blows do nothing to it. One of theirs once it has become the fly swats it: it dies, and they take
 * {@link GoldExperienceRules#FLY_SWAT} and lie knocked down {@link GoldExperienceRules#FLY_DOWN} ticks (GoldExperienceLife,
 * at the end of the tick). Its time up untouched it flies back into the part's place and is gone; so it is the moment
 * that one is gone, dead, off the map or out of its world. It is never saved: a fly is gone with its chunk.
 * <p>
 * Every client draws it (client/stand/GoldFlyRenderer) by the same path about whom it belongs to as that client sees
 * them, not by where the server last put it; the part it was is missing from their body while it lives
 * (client/stand/GoldLife).
 */
public class GoldFlyEntity extends LivingEntity {
    private static final TrackedData<Integer> OWNER=DataTracker.registerData(GoldFlyEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Byte> PART=DataTracker.registerData(GoldFlyEntity.class,TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Long> BORN=DataTracker.registerData(GoldFlyEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> SEED=DataTracker.registerData(GoldFlyEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Vector3f> FROM=DataTracker.registerData(GoldFlyEntity.class,TrackedDataHandlerRegistry.VECTOR3F);
    /** On a client: whether its player may touch a fly (set by the client: only whom it belongs to). The server's blows find it by sight. */
    public static Predicate<GoldFlyEntity> clientHits=f->true;

    // the server's
    UUID owner,maker;LivingEntity ownerBody;float heading;boolean swatted,ended;
    // a client's: the way it follows whom it belongs to, a tick ago and now, and its age as it was first seen here
    public float clientHeading,prevClientHeading;private long seenAge=Long.MIN_VALUE;private int clientTicks;

    public GoldFlyEntity(EntityType<? extends GoldFlyEntity> type,World world){
        super(type,world);setNoGravity(true);noClip=true;setSilent(true);
    }
    public static DefaultAttributeContainer.Builder attributes(){return LivingEntity.createLivingAttributes();}
    @Override protected void initDataTracker(){
        super.initDataTracker();
        dataTracker.startTracking(OWNER,-1);dataTracker.startTracking(PART,(byte)0);dataTracker.startTracking(BORN,0L);
        dataTracker.startTracking(SEED,0);dataTracker.startTracking(FROM,new Vector3f());
    }
    /** Made by {@code maker}'s punch of {@code part} of {@code body} at {@code born}, struck off at {@code from}. */
    void setup(LivingEntity body,UUID maker,int part,long born,int seed,Vec3d from,float heading){
        owner=body.getUuid();ownerBody=body;this.maker=maker;this.heading=heading;
        dataTracker.set(OWNER,body.getId());dataTracker.set(PART,(byte)part);dataTracker.set(BORN,born);dataTracker.set(SEED,seed);
        dataTracker.set(FROM,new Vector3f((float)from.x,(float)from.y,(float)from.z));
        refreshPositionAndAngles(from.x,from.y-getHeight()/2,from.z,heading,0);
    }
    /** Whom it belongs to (their entity id). */
    public int ownerId(){return dataTracker.get(OWNER);}
    /** Which part of them it is (GoldExperienceRules.HEAD ... LEFT_LEG). */
    public int part(){return dataTracker.get(PART);}
    /** The world tick of the blow that made it. */
    public long born(){return dataTracker.get(BORN);}
    public int seed(){return dataTracker.get(SEED);}
    /** Where the part was struck off. */
    public double[] from(){var v=dataTracker.get(FROM);return new double[]{v.x,v.y,v.z};}
    /** Whether {@code e} is whom it belongs to. */
    public boolean ownedBy(Entity e){return e!=null&&e.getId()==ownerId();}
    /** Who made it (the server's; null on a client). */
    public UUID maker(){return maker;}
    /** Whom it belongs to, on the server (null if they are gone). */
    public LivingEntity owner(){
        if(getWorld().isClient||owner==null)return null;
        if(ownerBody==null||ownerBody.isRemoved()||!ownerBody.getUuid().equals(owner))ownerBody=((ServerWorld)getWorld()).getEntity(owner) instanceof LivingEntity l?l:null;
        return ownerBody;
    }
    /** Ticks since the blow that made it (the server's clock). */
    public long age(){return getWorld().getTime()-born();}
    /** Whether it can be swatted now: it has become the fly, its time is not up and it has not been swatted. */
    public boolean swattable(){return !ended&&!swatted&&GoldExperienceRules.swattable(age());}

    /** Whether whom it belongs to can be followed by it now: there, alive, on the map. */
    static boolean holds(LivingEntity o,World world){
        if(o==null||o.isRemoved()||!o.isAlive()||o.getWorld()!=world||o.isSpectator()||!MapGate.enabled(world))return false;
        return !(o instanceof PlayerEntity p)||MapGate.enabled(p);
    }

    @Override public void tick(){
        setVelocity(Vec3d.ZERO);
        super.tick();
        if(getWorld().isClient){clientTick();return;}
        if(ended)return;
        var o=owner();
        if(!holds(o,getWorld())){GoldExperienceLife.flyGone(this,false);return;}
        long age=age();
        if(age>=FLY_GONE){GoldExperienceLife.flyGone(this,false);return;}
        // whom it belongs to stopped in a time stop: it hangs where it is
        if(StarPlatinumTimeStop.stopped(o))return;
        heading=GoldFlyPath.follow(heading,o.getHeadYaw());
        var p=GoldFlyPath.at(seed(),part(),age,o.getX(),o.getY(),o.getZ(),heading,o.getStandingEyeHeight(),o.getHeight(),from());
        setPosition(p[0],p[1]-getHeight()/2,p[2]);
        setYaw(heading+180);setHeadYaw(heading+180);setBodyYaw(heading+180);
    }
    private void clientTick(){
        var o=getWorld().getEntityById(ownerId());
        if(seenAge==Long.MIN_VALUE){
            seenAge=getWorld().getTime()-born();clientTicks=0;
            clientHeading=prevClientHeading=o instanceof LivingEntity l?l.getHeadYaw():getYaw();
        }
        clientTicks++;
        prevClientHeading=clientHeading;
        if(o instanceof LivingEntity l)clientHeading=GoldFlyPath.follow(clientHeading,l.getHeadYaw());
    }
    /** On a client: ticks since the blow that made it this frame (as first seen here, counted on by this client's ticks). */
    public double clientAge(float delta){return seenAge==Long.MIN_VALUE?0:seenAge+clientTicks-1+delta;}
    /** On a client: whether it has been ticked here yet (its age and heading are known). */
    public boolean clientReady(){return seenAge!=Long.MIN_VALUE;}

    /**
     * A blow on it: only one of its owner's, once it has become the fly, swats it (the rest come to nothing). Never a
     * hit as far as the blow goes: the owner's harm comes from the fly's death, at the end of the tick.
     */
    @Override public boolean damage(DamageSource source,float amount){
        if(getWorld().isClient||ended)return false;
        if(source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)){GoldExperienceLife.flyGone(this,false);return false;}
        if(!swattable())return false;
        var a=source.getAttacker();
        if(a==null||!ownedBy(a))return false;
        swatted=true;GoldExperienceLife.swatted(this);
        return false;
    }
    @Override public void remove(RemovalReason reason){
        // gone with its chunk, or otherwise taken out of the world: its maker's cooldown runs from now
        if(!getWorld().isClient&&!ended)GoldExperienceLife.flyGone(this,false);
        super.remove(reason);
    }
    /** For GoldExperienceLife: marks it over (before it is discarded). */
    void end(){ended=true;}

    /** On a client: whether this client's player can touch it (only whom it belongs to); the server's are found by sight. */
    @Override public boolean canHit(){return !getWorld().isClient||clientHits.test(this);}
    /** Arrows and the like pass through it (whosever they are: a projectile cannot tell whose it is to meet). */
    @Override public boolean canBeHitByProjectile(){return false;}
    @Override public boolean shouldSave(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override protected void pushAway(Entity entity){}
    @Override public boolean isCollidable(){return false;}
    @Override public boolean canUsePortals(){return false;}
    @Override public boolean isAffectedBySplashPotions(){return false;}
    @Override public boolean canHaveStatusEffect(StatusEffectInstance effect){return false;}
    @Override public boolean shouldRenderName(){return false;}
    @Override public boolean isMobOrPlayer(){return false;}
    @Override public Iterable<ItemStack> getArmorItems(){return List.of();}
    @Override public ItemStack getEquippedStack(EquipmentSlot slot){return ItemStack.EMPTY;}
    @Override public void equipStack(EquipmentSlot slot,ItemStack stack){}
    @Override public Arm getMainArm(){return Arm.RIGHT;}
    @Override public void writeCustomDataToNbt(NbtCompound nbt){}
    @Override public void readCustomDataFromNbt(NbtCompound nbt){}
}
