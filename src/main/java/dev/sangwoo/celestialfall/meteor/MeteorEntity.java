package dev.sangwoo.celestialfall.meteor;

import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.function.BiConsumer;

/**
 * A meteor's rock on the ground: what players see, stand against and mine. All of it lives in {@link MeteorState}
 * (where it lies, what is left, when it crumbles); the rock is never saved with its chunk and is put back by
 * {@link MeteorManager} whenever its chunk is loaded. Solid like a boat, never pushed or hurt.
 */
public final class MeteorEntity extends Entity {
    /**
     * Statuses the server sends for the client's effects: a hit, a crystal (a fragment) breaking off, a crystal breaking
     * off as the rock goes (mined out, or shattered with crystals still in it), crumbled with age.
     */
    public static final byte HIT=70,BREAK=71,MINED=72,EXPIRED=73;
    /** The client's effects for those statuses (set by the client; nothing on a dedicated server). */
    public static BiConsumer<MeteorEntity,Byte> EVENTS=(e,s)->{};
    private static final TrackedData<Integer> SLOT=DataTracker.registerData(MeteorEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> CYCLE=DataTracker.registerData(MeteorEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> SEED=DataTracker.registerData(MeteorEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> LANDING=DataTracker.registerData(MeteorEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> FRAGMENTS=DataTracker.registerData(MeteorEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> HITS=DataTracker.registerData(MeteorEntity.class,TrackedDataHandlerRegistry.INTEGER);

    public MeteorEntity(EntityType<? extends MeteorEntity> type,World world){super(type,world);setNoGravity(true);setInvulnerable(true);}
    @Override protected void initDataTracker(){
        dataTracker.startTracking(SLOT,-1);dataTracker.startTracking(CYCLE,0L);dataTracker.startTracking(SEED,0);
        dataTracker.startTracking(LANDING,0L);dataTracker.startTracking(FRAGMENTS,MeteorRules.FRAGMENTS);dataTracker.startTracking(HITS,0);
    }
    public int slot(){return dataTracker.get(SLOT);}
    public long cycle(){return dataTracker.get(CYCLE);}
    public int seed(){return dataTracker.get(SEED);}
    /** The world tick it landed. */
    public long landing(){return dataTracker.get(LANDING);}
    public int fragments(){return dataTracker.get(FRAGMENTS);}
    public int hits(){return dataTracker.get(HITS);}
    public MeteorRules.Rock rock(){return new MeteorRules.Rock(Math.max(0,Math.min(MeteorRules.FRAGMENTS,fragments())),Math.max(0,Math.min(MeteorRules.HITS_PER_FRAGMENT-1,hits())));}
    public void configure(MeteorState.Meteor m){
        dataTracker.set(SLOT,m.slot());dataTracker.set(CYCLE,m.cycle());dataTracker.set(SEED,m.seed());dataTracker.set(LANDING,m.landing());
        dataTracker.set(FRAGMENTS,m.fragments());dataTracker.set(HITS,m.hits());
    }
    @Override public void tick(){
        super.tick();setVelocity(Vec3d.ZERO);
        if(getWorld() instanceof ServerWorld w){
            var m=MeteorManager.enabled(w)?MeteorState.get(w).slot(slot()):null;
            if(m==null||m.cycle()!=cycle()||!getUuid().equals(m.uuid())){discard();return;}
            configure(m);setPosition(m.pos());
        }
    }
    @Override public void handleStatus(byte status){
        if(status>=HIT&&status<=EXPIRED){if(getWorld().isClient)EVENTS.accept(this,status);}
        else super.handleStatus(status);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound n){}
    @Override protected void writeCustomDataToNbt(NbtCompound n){}
    @Override public boolean shouldSave(){return false;}
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
    @Override public boolean damage(DamageSource source,float amount){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean isCollidable(){return true;}
    @Override public boolean canHit(){return isAlive();}
    @Override public boolean canUsePortals(){return false;}
    /** Seen from as far as the map's view reaches (a rock on a wide field). */
    @Override public boolean shouldRender(double distance){return distance<512*512;}
}
