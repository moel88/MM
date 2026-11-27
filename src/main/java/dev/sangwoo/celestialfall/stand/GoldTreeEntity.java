package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.map.MapGate;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;

/**
 * The tree Gold Experience's H brings up out of the ground under its user (2.152.0: GoldExperienceRules has the user's
 * words). On the server it is the body blows on the tree meet: its trunk, where it burst up, never moved by anything and
 * never harmed. A blow on it while it stands comes back, all of it, on whoever struck it (GoldExperienceTree.struck: the
 * user's "적들이 나무를 때리면 그 데미지가 적에게 가게", as Koichi's blow on Giorno's tree came back on him): the blow
 * itself comes to nothing (as a blow it does not land), so what a skill does to whom it lands on (a cutscene, a hold, a
 * soul struck out, a fly) never comes of it. Its maker's own blows pass by. It withers at TREE_WITHER (no longer struck)
 * and is gone at TREE_END (GoldExperienceTree takes it away), or early, its maker gone. It is never saved.
 * <p>
 * Every client draws it (client/stand/GoldTreeRenderer, a GeckoLib model of tools/stand/make_gold_tree.py's) growing out
 * of the ground by its own clock: the server's age of it, eased as a fly's is.
 */
public class GoldTreeEntity extends LivingEntity implements GeoEntity {
    private static final TrackedData<Integer> OWNER=DataTracker.registerData(GoldTreeEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> BORN=DataTracker.registerData(GoldTreeEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Long> WITHER=DataTracker.registerData(GoldTreeEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> SEED=DataTracker.registerData(GoldTreeEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> FACING=DataTracker.registerData(GoldTreeEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);

    // the server's: its maker, where it stands, whether it is over
    UUID maker;Vec3d home;boolean ended;
    // a client's: its age (ticks), a tick ago and now
    private boolean seen;private double clientAgeNow,clientAgePrev;

    public GoldTreeEntity(EntityType<? extends GoldTreeEntity> type,World world){
        super(type,world);setNoGravity(true);noClip=true;setSilent(true);
    }
    public static DefaultAttributeContainer.Builder attributes(){return LivingEntity.createLivingAttributes().add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,1);}
    @Override protected void initDataTracker(){
        super.initDataTracker();
        dataTracker.startTracking(OWNER,-1);dataTracker.startTracking(BORN,0L);dataTracker.startTracking(WITHER,Long.MAX_VALUE/4);
        dataTracker.startTracking(SEED,0);dataTracker.startTracking(FACING,0f);
    }
    /** Brought up by {@code maker} at {@code born} where they stood ({@code at}), its trunk leaning the way they faced. */
    void setup(LivingEntity maker,long born,int seed,float facing,Vec3d at){
        this.maker=maker.getUuid();home=at;
        dataTracker.set(OWNER,maker.getId());dataTracker.set(BORN,born);dataTracker.set(WITHER,born+TREE_WITHER-TREE_BURST);
        dataTracker.set(SEED,seed);dataTracker.set(FACING,facing);
        refreshPositionAndAngles(at.x,at.y,at.z,facing,0);setBodyYaw(facing);setHeadYaw(facing);
    }
    /** Whose it is (their entity id). */
    public int ownerId(){return dataTracker.get(OWNER);}
    /** Whether {@code e} is whose it is. */
    public boolean ownedBy(Entity e){return e!=null&&(e.getId()==ownerId()||maker!=null&&maker.equals(e.getUuid()));}
    /** Its maker (the server's; null on a client). */
    public UUID maker(){return maker;}
    /** The world tick it burst up. */
    public long born(){return dataTracker.get(BORN);}
    /** The world tick it begins to wither (from then it is struck no more). */
    public long witherAt(){return dataTracker.get(WITHER);}
    public int seed(){return dataTracker.get(SEED);}
    /** The way its maker faced as it burst up (its trunk leans that way, after them). */
    public float facing(){return dataTracker.get(FACING);}
    /** Ticks since it burst up (the server's clock). */
    public long age(){return getWorld().getTime()-born();}
    /** Whether it stands now (blows on it come back): from its burst until it begins to wither. */
    public boolean standing(){return !ended&&getWorld().getTime()<witherAt();}
    /** It withers from {@code now} (its maker gone), if it does not already. */
    void witherNow(long now){if(now<witherAt())dataTracker.set(WITHER,now);}
    /** For GoldExperienceTree: marks it over (before it is discarded). */
    void end(){ended=true;}

    @Override public void tick(){
        setVelocity(Vec3d.ZERO);
        // nothing moves it: where it burst up it stays (a skill's throw or pull, a push, comes to nothing)
        if(!getWorld().isClient&&home!=null&&(getX()!=home.x||getY()!=home.y||getZ()!=home.z))setPosition(home);
        super.tick();
        if(getWorld().isClient)clientTick();
    }
    private void clientTick(){
        // its age goes on a tick at each of this client's ticks, eased toward the server's (as a fly's: GoldFlyPath.age)
        double server=getWorld().getTime()-born();
        if(!seen){seen=true;clientAgeNow=clientAgePrev=server-1;}
        clientAgePrev=clientAgeNow;
        clientAgeNow=GoldFlyPath.age(clientAgeNow,server);
    }
    /** On a client: ticks since it burst up, this frame (smooth between its ticks). */
    public double clientAge(float delta){return seen?clientAgePrev+(clientAgeNow-clientAgePrev)*delta:Math.max(0,getWorld().getTime()-born());}

    /**
     * A blow on it: while it stands, all of it comes back on whoever struck it (GoldExperienceTree.struck), its maker's own
     * passing by. As far as the blow goes nothing lands (false): it is never harmed, and nothing a skill does to whom it
     * lands on comes of it. What gets past everything (/kill, the void) takes it away.
     */
    @Override public boolean damage(DamageSource source,float amount){
        if(getWorld().isClient||ended)return false;
        if(source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)){GoldExperienceTree.gone(this);return false;}
        if(standing())GoldExperienceTree.struck(this,source,amount);
        return false;
    }
    @Override public void remove(RemovalReason reason){
        // gone with its chunk, or otherwise taken out of the world: its maker's cooldown runs from now
        if(!getWorld().isClient&&!ended)GoldExperienceTree.gone(this);
        super.remove(reason);
    }
    /**
     * On a client: whether its crosshair may find this tree (its maker's passes through their own: they cannot strike it
     * to any end). Set by the client (client/stand/GoldTree).
     */
    public static java.util.function.Predicate<GoldTreeEntity> clientHits=t->true;
    /** Something to strike while it stands (the crosshair finds it, the skills' sight does); withering, nothing is. */
    @Override public boolean canHit(){return standing()&&(!getWorld().isClient||clientHits.test(this));}
    @Override public boolean canBeHitByProjectile(){return standing();}
    @Override public void takeKnockback(double strength,double x,double z){}
    @Override public void addVelocity(double x,double y,double z){}
    @Override public boolean isPushable(){return false;}
    @Override protected void pushAway(Entity entity){}
    @Override public boolean isCollidable(){return false;}
    @Override public boolean canUsePortals(){return false;}
    @Override public boolean isAffectedBySplashPotions(){return false;}
    @Override public boolean canHaveStatusEffect(StatusEffectInstance effect){return false;}
    @Override public boolean shouldRenderName(){return false;}
    @Override public boolean isMobOrPlayer(){return false;}
    @Override public boolean shouldSave(){return false;}
    @Override public boolean isFireImmune(){return true;}
    /** Its crown reaches far past its trunk: it is drawn while any of it can be seen. */
    @Override public Box getVisibilityBoundingBox(){return getBoundingBox().expand(3.6,1.5,3.6);}
    @Override public boolean shouldRender(double distance){return distance<160*160;}
    @Override public Iterable<ItemStack> getArmorItems(){return List.of();}
    @Override public ItemStack getEquippedStack(EquipmentSlot slot){return ItemStack.EMPTY;}
    @Override public void equipStack(EquipmentSlot slot,ItemStack stack){}
    @Override public Arm getMainArm(){return Arm.RIGHT;}
    @Override public void writeCustomDataToNbt(NbtCompound nbt){}
    @Override public void readCustomDataFromNbt(NbtCompound nbt){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}

    /** Whether {@code e} is a tree (what a skill that holds, marks or carries someone off passes by). */
    public static boolean is(Entity e){return e instanceof GoldTreeEntity;}
    /** Whether {@code world} is one where trees may stand (the map). */
    static boolean grows(World world){return MapGate.enabled(world);}
}
