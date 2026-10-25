package dev.sangwoo.celestialfall.training;

import dev.sangwoo.celestialfall.combat.*;
import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** A real damageable mob using the same strike/guard state machine as players. */
public final class TrainingDummyEntity extends PathAwareEntity implements Monster, software.bernie.geckolib.animatable.GeoEntity {
    private final software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache geoCache=software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    @Override public software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache(){return geoCache;}
    @Override public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers){}
    private static final TrackedData<Integer> ATTACK=DataTracker.registerData(TrainingDummyEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> ATTACK_START=DataTracker.registerData(TrainingDummyEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Boolean> GUARD=DataTracker.registerData(TrainingDummyEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> PRESSURE=DataTracker.registerData(TrainingDummyEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Long> BROKEN=DataTracker.registerData(TrainingDummyEntity.class,TrackedDataHandlerRegistry.LONG);
    /** Until when it is incapacitated (2.60.0), for every client's view of it. */
    private static final TrackedData<Long> DISABLED=DataTracker.registerData(TrainingDummyEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final UUID SLOW=UUID.fromString("7718e5f8-f59a-4985-81d6-cdfd0fc00001");
    private final DummyKind kind;
    private final CombatState combat=new CombatState();
    private long nextCombo;

    public TrainingDummyEntity(EntityType<? extends TrainingDummyEntity> type,World world,DummyKind kind) {
        super(type,world);this.kind=kind;setPersistent();setCanPickUpLoot(false);
        getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(kind.health);
        setHealth(kind.health);experiencePoints=0;
    }
    public static DefaultAttributeContainer.Builder attributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH,200)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED,.23).add(EntityAttributes.GENERIC_ATTACK_DAMAGE,3)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE,8).add(EntityAttributes.GENERIC_ARMOR,0)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,0);
    }
    @Override protected void initGoals() {} // No wandering, retaliation goals or natural spawning.
    @Override protected void initDataTracker() {
        super.initDataTracker();dataTracker.startTracking(ATTACK,0);dataTracker.startTracking(ATTACK_START,-1000L);
        dataTracker.startTracking(GUARD,false);dataTracker.startTracking(PRESSURE,0f);dataTracker.startTracking(BROKEN,0L);dataTracker.startTracking(DISABLED,0L);
    }
    public DummyKind kind(){return kind;}
    public CombatState combatState(){return combat;}
    public int strike(){return dataTracker.get(ATTACK);}
    public long strikeStart(){return dataTracker.get(ATTACK_START);}
    public boolean guarding(){return dataTracker.get(GUARD);}
    public float guardPressure(){return dataTracker.get(PRESSURE);}
    public long brokenUntil(){return dataTracker.get(BROKEN);}
    /** Until when it is incapacitated (2.60.0: struck by the Star Finger; its guard broken counts as well, through {@link #brokenUntil}). */
    public long disabledUntil(){return dataTracker.get(DISABLED);}
    public boolean canContinueCombo(){return dev.sangwoo.celestialfall.map.MapGate.enabled(getWorld())&&kind==DummyKind.ATTACK&&isAlive()&&!combat.locked(getWorld().getTime());}
    @Override protected float getActiveEyeHeight(EntityPose pose,EntityDimensions dimensions){return 1.62f;}
    @Override public boolean cannotDespawn(){return true;}
    @Override public boolean canImmediatelyDespawn(double distance){return false;}

    @Override public boolean isPushable() { return kind!=DummyKind.ATTACK && super.isPushable(); }
    @Override public void takeKnockback(double strength,double x,double z) {
        if(kind!=DummyKind.ATTACK)super.takeKnockback(strength,x,z);
    }
    @Override public void travel(Vec3d input) {
        if(kind!=DummyKind.ATTACK){super.travel(input);return;}
        // Allow gravity, but reject navigation, water drift and horizontal external impulses.
        setVelocity(0,getVelocity().y,0);
        super.travel(Vec3d.ZERO);
        setVelocity(0,getVelocity().y,0);
    }
    @Override public void move(MovementType type,Vec3d movement) {
        super.move(type,kind==DummyKind.ATTACK?new Vec3d(0,movement.y,0):movement);
    }
    @Override protected void mobTick() {
        // The Atomic dummy lives only as long as its cast (one saved mid-cast has none when the server comes back).
        if(getCommandTags().contains(TrainingDummies.ATOMIC_TAG)&&!dev.sangwoo.celestialfall.AtomicManager.active(getUuid())){discard();return;}
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(getWorld())){combat.cancel();combat.pressure=0;combat.brokenUntil=0;getNavigation().stop();setTarget(null);HitStun.disconnect(getUuid());syncCombat();return;}
        long now=getWorld().getTime();
        if(combat.brokenUntil>0&&now>=combat.brokenUntil){combat.brokenUntil=0;combat.pressure=0;}
        // stunned, its guard broken or incapacitated (2.60.0): it does nothing
        if(HitStun.stunned(this)||combat.locked(now)) {
            combat.cancel();getNavigation().stop();setForwardSpeed(0);syncCombat();return;
        }
        if(kind!=DummyKind.ATTACK) {
            getNavigation().stop();setForwardSpeed(0);
            combat.guard(kind==DummyKind.GUARD,now);combat.tick(now);syncCombat();return;
        }
        PlayerEntity target=getWorld().getClosestPlayer(getX(),getY(),getZ(),8,
            e->e instanceof PlayerEntity p&&p.isAlive()&&!p.isSpectator());
        if(target==null||!canSee(target)) {
            setTarget(null);combat.cancel();HitStun.releaseOwned(getUuid());getNavigation().stop();setForwardSpeed(0);syncCombat();return;
        }
        setTarget(target);
        Vec3d toward=target.getEyePos().subtract(getEyePos());
        float desiredYaw=(float)Math.toDegrees(Math.atan2(-toward.x,toward.z));
        setYaw(MathHelper.stepUnwrappedAngleTowards(getYaw(),desiredYaw,14));setHeadYaw(getYaw());setBodyYaw(getYaw());
        setPitch((float)-Math.toDegrees(Math.atan2(toward.y,toward.horizontalLength())));
        boolean close=HitStun.inReach(this,target)&&squaredDistanceTo(target)<=2.7*2.7;
        // This is a stationary sparring partner, not a pursuing mob.
        getNavigation().stop();setForwardSpeed(0);setSidewaysSpeed(0);
        int previous=combat.attack;combat.tick(now);
        if(previous==4&&combat.attack==0){nextCombo=now+20;HitStun.releaseOwned(getUuid());}
        if(close&&now>=nextCombo)combat.requestAttack(now);
        if(combat.hitDue(now))strikeTarget(target);
        syncCombat();
    }
    private void strikeTarget(LivingEntity selected) {
        int strike=combat.attack;
        LivingEntity target=strike>1?HitStun.followup(this,combat):null;
        if(target==null)target=selected;
        Vec3d toward=target.getPos().subtract(getPos()).multiply(1,0,1);
        if(!HitStun.inReach(this,target)||toward.lengthSquared()<1e-6
            ||Vec3d.fromPolar(0,getYaw()).dotProduct(toward.normalize())<.65) {
            HitStun.releaseOwned(getUuid());return;
        }
        var source=new DamageSource(getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(CombatManager.UNARMED),this);
        boolean damaged=CombatManager.damageWithoutKnockback(target,source,CombatState.DAMAGE[strike]);
        if(!damaged){HitStun.releaseOwned(getUuid());return;}
        if(combat.attack==strike&&isAlive())HitStun.landed(this,combat,target);else HitStun.releaseOwned(getUuid());
        if(strike==4) {
            double yaw=Math.toRadians(getYaw());target.takeKnockback(.95,Math.sin(yaw),-Math.cos(yaw));target.velocityModified=true;
            if(target instanceof ServerPlayerEntity p)p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));
        }
        getWorld().playSound(null,getX(),getY(),getZ(),strike==4?SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK:SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,SoundCategory.HOSTILE,.65f,strike==4?.75f:1.1f);
        ((ServerWorld)getWorld()).spawnParticles(ParticleTypes.CRIT,target.getX(),target.getBodyY(.6),target.getZ(),strike==4?12:5,.18,.18,.18,.08);
    }
    private boolean block(DamageSource source,float amount) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(getWorld()))return false;
        long now=getWorld().getTime();
        if(!isAlive()||!Float.isFinite(amount)||amount<=0||isInvulnerableTo(source)||!combat.guarding||combat.broken(now)||HitStun.stunned(this)
            ||source.isIn(DamageTypeTags.BYPASSES_SHIELD)||source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        Vec3d origin=source.getPosition();if(origin==null)return false;
        Vec3d toward=origin.subtract(getPos()).multiply(1,0,1);
        if(toward.lengthSquared()<1e-6||Vec3d.fromPolar(0,getYaw()).dotProduct(toward.normalize())<.2)return false;
        boolean broken=combat.guardHit(amount,now,source.isOf(CombatManager.UNARMED));syncCombat();
        getWorld().playSound(null,getX(),getY(),getZ(),broken?SoundEvents.ITEM_SHIELD_BREAK:SoundEvents.ITEM_SHIELD_BLOCK,SoundCategory.HOSTILE,.85f,broken?.8f:1.15f);
        ((ServerWorld)getWorld()).spawnParticles(broken?ParticleTypes.CRIT:ParticleTypes.ELECTRIC_SPARK,getX(),getY()+1.15,getZ(),broken?24:9,.25,.22,.15,.07);
        return true;
    }
    @Override public boolean damage(DamageSource source,float amount) {
        if(!getWorld().isClient&&block(source,amount))return false;
        boolean hit=super.damage(source,amount);
        if(hit&&!getWorld().isClient)interruptCombat();
        return hit;
    }
    /** Its guard broken outright by a blow no guard can take (2.59.0, CombatManager.breakGuard): whether it had one up. */
    public boolean breakGuard(int ticks){
        long now=getWorld().getTime();
        if(!combat.guarding||combat.broken(now))return false;
        combat.breakGuard(now,ticks);syncCombat();
        getWorld().playSound(null,getX(),getY(),getZ(),SoundEvents.ITEM_SHIELD_BREAK,SoundCategory.HOSTILE,.85f,.8f);
        ((ServerWorld)getWorld()).spawnParticles(ParticleTypes.CRIT,getX(),getY()+1.15,getZ(),24,.25,.22,.15,.07);
        return true;
    }
    /** Incapacitated (2.60.0, CombatManager.incapacitate): no attack or guard for {@code ticks}, {@code slow} slower for {@code slowTicks}. */
    public void incapacitate(int ticks,int slowTicks,double slow){
        long now=getWorld().getTime();combat.incapacitate(now,ticks,slowTicks,slow);HitStun.releaseOwned(getUuid());getNavigation().stop();
        nextCombo=Math.max(nextCombo,combat.disabledUntil+12);syncCombat();
    }
    public void interruptCombat(){combat.cancel();getNavigation().stop();nextCombo=getWorld().getTime()+12;syncCombat();}
    public void resetTraining() {
        HitStun.disconnect(getUuid());combat.cancel();combat.pressure=0;combat.brokenUntil=0;combat.comboUntil=0;combat.disabledUntil=combat.slowUntil=0;
        nextCombo=getWorld().getTime()+20;getNavigation().stop();setTarget(null);setVelocity(Vec3d.ZERO);
        setHealth(getMaxHealth());clearStatusEffects();extinguish();timeUntilRegen=0;syncCombat();
    }
    private void syncCombat() {
        dataTracker.set(ATTACK,combat.attack);dataTracker.set(ATTACK_START,combat.attackStart);
        dataTracker.set(GUARD,combat.guarding);dataTracker.set(PRESSURE,combat.pressure);dataTracker.set(BROKEN,combat.brokenUntil);dataTracker.set(DISABLED,combat.disabledUntil);
        var speed=getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);long now=getWorld().getTime();
        double slow=Math.min(combat.broken(now)?-.9:0,Math.min(combat.slowed(now)?-combat.slow:0,combat.guarding?-.55:0));
        var old=speed.getModifier(SLOW);if(old!=null&&old.getValue()==slow)return;
        if(old!=null)speed.removeModifier(SLOW);
        if(slow!=0)speed.addTemporaryModifier(new EntityAttributeModifier(SLOW,"Training guard",slow,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    @Override public void remove(RemovalReason reason){if(!getWorld().isClient)HitStun.disconnect(getUuid());super.remove(reason);}
    @Override public void onDeath(DamageSource source){HitStun.disconnect(getUuid());combat.cancel();super.onDeath(source);}
    @Override public void readCustomDataFromNbt(NbtCompound nbt){super.readCustomDataFromNbt(nbt);dev.sangwoo.celestialfall.progression.ProgressManager.setBaseHealth(this,kind.health);combat.cancel();combat.pressure=0;combat.brokenUntil=0;syncCombat();}
    @Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.ENTITY_IRON_GOLEM_HURT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.BLOCK_WOOL_BREAK;}
}
