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
 * Gold Experience's body (2.145.0, user request: "새로운 스탠드 만들자 죠죠의 골드익스피리언스 외형부터 만들자 외형은
 * Geckolib으로 만들고 사진 보내줄게 스타플래티나 만들때 처럼 만들면 돼", after the two pictures they sent). So far only its
 * looks: as an entity it is a figure that stands where it is summoned ("/botand gold [idle|hero|stand|clear]", OP) and
 * holds a stance, as Star Platinum's figure does ({@link StarPlatinumEntity}); its model, texture and stances come from
 * tools/stand/make_gold_experience.py and its motion from the client's GoldExperienceModel. It is nobody's stand yet:
 * its skills and its place among the stands are made as the user says.
 */
public class GoldExperienceEntity extends MobEntity implements GeoEntity {
    private static final TrackedData<Integer> STANCE=DataTracker.registerData(GoldExperienceEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    /** The client's crossfade: the stance last drawn, the one before it and when (its age in ticks) the change came. */
    private int shownStance=-1,previousStance;private float changedAt=-1000;

    public GoldExperienceEntity(EntityType<? extends GoldExperienceEntity> type,World world){
        super(type,world);setNoGravity(true);setInvulnerable(true);setSilent(true);setAiDisabled(true);setPersistent();
    }
    public static DefaultAttributeContainer.Builder attributes(){return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH,20);}
    @Override protected void initDataTracker(){super.initDataTracker();dataTracker.startTracking(STANCE,0);}
    public int stance(){return dataTracker.get(STANCE);}
    public void setStance(int stance){dataTracker.set(STANCE,Math.floorMod(stance,GoldExperienceStance.NAMES.size()));}
    /**
     * For the client's model, once a frame: how far (0 to 1, eased) the figure has gone over from the stance before
     * ({@link #previousStance()}) to the current one. A change is seen on the frame it arrives and eased in from there.
     */
    public float blend(float delta){
        int now=stance();float clock=age+delta;
        if(shownStance<0){shownStance=now;previousStance=now;}
        else if(now!=shownStance){previousStance=shownStance;shownStance=now;changedAt=clock;}
        return GoldExperienceStance.crossfade(clock-changedAt);
    }
    public int previousStance(){return previousStance;}
    /** Its look as a stand beside its user (2.146.0, the client's body of one: GoldPuppet), or null for the figure, which goes by its stance. */
    public GoldExperienceLook look(float delta){return null;}
    @Override public void writeCustomDataToNbt(NbtCompound nbt){super.writeCustomDataToNbt(nbt);nbt.putString("Stance",GoldExperienceStance.NAMES.get(stance()));}
    @Override public void readCustomDataFromNbt(NbtCompound nbt){super.readCustomDataFromNbt(nbt);setStance(Math.max(0,GoldExperienceStance.NAMES.indexOf(nbt.getString("Stance"))));}
    /** Only what gets past invulnerability (/kill, the void) removes it; "/botand gold clear" is the usual way. */
    @Override public boolean isInvulnerableTo(DamageSource source){return !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);}
    @Override public boolean isPushable(){return false;}
    @Override protected void pushAway(Entity entity){}
    @Override public boolean isCollidable(){return false;}
    @Override public boolean cannotDespawn(){return true;}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
}
