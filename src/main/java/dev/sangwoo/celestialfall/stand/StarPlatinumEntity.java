package dev.sangwoo.celestialfall.stand;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Star Platinum's body (2.50.0, user request: "일단 스타플래티나 모델링만 뽑자 신경써서", after the official art they sent).
 * As an entity it is a figure that stands where it is summoned ("/botand platinum [idle|hero|summon|clear]", OP) and
 * holds a stance; its model, texture and stances come from tools/stand/make_star_platinum.py and its motion from
 * StarPlatinumModel. The stand that comes out of its user (2.51.0) is the same body drawn by each client beside the user
 * (client/stand/StandPuppet), never spawned in the world.
 */
public class StarPlatinumEntity extends MobEntity implements GeoEntity {
    private static final TrackedData<Integer> STANCE=DataTracker.registerData(StarPlatinumEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    /** The client's crossfade: the stance last drawn, the one before it and when (its age in ticks) the change came. */
    private int shownStance=-1,previousStance;private float changedAt=-1000;

    public StarPlatinumEntity(EntityType<? extends StarPlatinumEntity> type,World world){
        super(type,world);setNoGravity(true);setInvulnerable(true);setSilent(true);setAiDisabled(true);setPersistent();
    }
    public static DefaultAttributeContainer.Builder attributes(){return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH,20);}
    @Override protected void initDataTracker(){super.initDataTracker();dataTracker.startTracking(STANCE,0);}
    public int stance(){return dataTracker.get(STANCE);}
    public void setStance(int stance){dataTracker.set(STANCE,Math.floorMod(stance,StarPlatinumStance.NAMES.size()));}
    /**
     * For the client's model, once a frame: how far (0 to 1, eased) the figure has gone over from the stance before
     * ({@link #previousStance()}) to the current one. A change is seen on the frame it arrives and eased in from there.
     */
    public float blend(float delta){
        int now=stance();float clock=age+delta;
        if(shownStance<0){shownStance=now;previousStance=now;}
        else if(now!=shownStance){previousStance=shownStance;shownStance=now;changedAt=clock;}
        return StarPlatinumStance.crossfade(clock-changedAt);
    }
    public int previousStance(){return previousStance;}
    private final StarPlatinumLook look=new StarPlatinumLook();
    /** What the model draws this frame: the figure's stance, crossfaded from the one before (the art's stance blows in its wind). */
    public StarPlatinumLook look(float delta){
        float k=blend(delta);look.only(stance());
        if(previousStance!=stance()){look.weights[previousStance]=1-k;look.weights[stance()]=k;}
        look.wind=look.weights[StarPlatinumStance.HERO];
        return look;
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt){super.writeCustomDataToNbt(nbt);nbt.putString("Stance",StarPlatinumStance.NAMES.get(stance()));}
    @Override public void readCustomDataFromNbt(NbtCompound nbt){super.readCustomDataFromNbt(nbt);setStance(Math.max(0,StarPlatinumStance.NAMES.indexOf(nbt.getString("Stance"))));}
    /** Only what gets past invulnerability (/kill, the void) removes it; "/botand platinum clear" is the usual way. */
    @Override public boolean isInvulnerableTo(DamageSource source){return !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);}
    @Override public boolean isPushable(){return false;}
    @Override protected void pushAway(Entity entity){}
    @Override public boolean isCollidable(){return false;}
    @Override public boolean cannotDespawn(){return true;}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
}
