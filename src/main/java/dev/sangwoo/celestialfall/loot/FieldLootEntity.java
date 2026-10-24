package dev.sangwoo.celestialfall.loot;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
/** Non-solid visual: all ownership, rewards and cooldowns live in LootState. */
public final class FieldLootEntity extends Entity {
    private static final TrackedData<Integer> SLOT=DataTracker.registerData(FieldLootEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> TREASURE=DataTracker.registerData(FieldLootEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Long> CYCLE=DataTracker.registerData(FieldLootEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Long> OPENED=DataTracker.registerData(FieldLootEntity.class,TrackedDataHandlerRegistry.LONG);
    public FieldLootEntity(EntityType<? extends FieldLootEntity> type,World world){super(type,world);setNoGravity(true);setInvulnerable(true);}
    @Override protected void initDataTracker(){dataTracker.startTracking(SLOT,-1);dataTracker.startTracking(TREASURE,false);dataTracker.startTracking(CYCLE,0L);dataTracker.startTracking(OPENED,-1L);}
    public int slot(){return dataTracker.get(SLOT);}
    public boolean treasure(){return dataTracker.get(TREASURE);}
    public long cycle(){return dataTracker.get(CYCLE);}
    public long openedAt(){return dataTracker.get(OPENED);}
    public boolean opened(){return openedAt()>=0;}
    public void configure(int index,LootState.Slot state){dataTracker.set(SLOT,index);dataTracker.set(TREASURE,state.treasure());dataTracker.set(CYCLE,state.cycle());dataTracker.set(OPENED,state.openedAt());}
    @Override public void tick(){
        super.tick();setVelocity(Vec3d.ZERO);
        if(getWorld() instanceof ServerWorld w){
            if(!LootWorld.enabled(w)||!LootPositions.valid(slot())){discard();return;}
            var state=LootState.get(w).slot(slot());
            if(state==null||!getUuid().equals(LootPositions.id(slot()))||state.cycle()!=cycle()){discard();return;}
            configure(slot(),state);setPosition(Vec3d.ofBottomCenter(LootPositions.current().get(slot())));
            if(opened()&&w.getTime()-openedAt()>=LootRules.OPEN_TICKS)discard();
        }
    }
    @Override protected void readCustomDataFromNbt(NbtCompound n){dataTracker.set(SLOT,n.getInt("Slot"));dataTracker.set(TREASURE,n.getBoolean("Treasure"));dataTracker.set(CYCLE,n.getLong("Cycle"));dataTracker.set(OPENED,n.contains("Opened")?n.getLong("Opened"):-1L);}
    @Override protected void writeCustomDataToNbt(NbtCompound n){n.putInt("Slot",slot());n.putBoolean("Treasure",treasure());n.putLong("Cycle",cycle());n.putLong("Opened",openedAt());}
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
    @Override public boolean damage(DamageSource source,float amount){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canHit(){return false;}
    @Override public boolean canUsePortals(){return false;}
}
