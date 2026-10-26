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
/**
 * Non-solid visual: all ownership, rewards and cooldowns live in LootState. A box a boss left (2.69.0, slot {@link #LOOSE})
 * is none of the map's supply spots: it rolled its own rewards when it came, opens once, and goes once it has been opened
 * or BossRules.BOX_LIFE after it came.
 */
public final class FieldLootEntity extends Entity {
    /** The slot of a box a boss left. */
    public static final int LOOSE=-2;
    private static final TrackedData<Integer> SLOT=DataTracker.registerData(FieldLootEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> TREASURE=DataTracker.registerData(FieldLootEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Long> CYCLE=DataTracker.registerData(FieldLootEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Long> OPENED=DataTracker.registerData(FieldLootEntity.class,TrackedDataHandlerRegistry.LONG);
    /** A boss's box: what it holds (LootRules flags) and the tick it came. */
    private int looseDrops;private long born=-1;
    public FieldLootEntity(EntityType<? extends FieldLootEntity> type,World world){super(type,world);setNoGravity(true);setInvulnerable(true);}
    public boolean loose(){return slot()==LOOSE;}
    public int looseDrops(){return looseDrops;}
    /** Makes it a box a boss left, holding {@code drops}, come at {@code now}. */
    public void makeLoose(int drops,long now){dataTracker.set(SLOT,LOOSE);dataTracker.set(TREASURE,false);dataTracker.set(CYCLE,0L);dataTracker.set(OPENED,-1L);looseDrops=drops;born=now;}
    /** A boss's box is opened (it goes OPEN_TICKS on, as a supply box does). */
    public void openLoose(long now){if(loose())dataTracker.set(OPENED,now);}
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
            if(loose()){
                long now=w.getTime();
                if(!dev.sangwoo.celestialfall.map.MapGate.enabled(w)||(opened()?now-openedAt()>=LootRules.OPEN_TICKS:born>=0&&now-born>=dev.sangwoo.celestialfall.boss.BossRules.BOX_LIFE))discard();
                return;
            }
            if(!LootWorld.enabled(w)||!LootPositions.valid(slot())){discard();return;}
            var state=LootState.get(w).slot(slot());
            if(state==null||!getUuid().equals(LootPositions.id(slot()))||state.cycle()!=cycle()){discard();return;}
            configure(slot(),state);setPosition(Vec3d.ofBottomCenter(LootPositions.current().get(slot())));
            if(opened()&&w.getTime()-openedAt()>=LootRules.OPEN_TICKS)discard();
        }
    }
    @Override protected void readCustomDataFromNbt(NbtCompound n){dataTracker.set(SLOT,n.getInt("Slot"));dataTracker.set(TREASURE,n.getBoolean("Treasure"));dataTracker.set(CYCLE,n.getLong("Cycle"));dataTracker.set(OPENED,n.contains("Opened")?n.getLong("Opened"):-1L);looseDrops=n.getInt("Drops");born=n.contains("Born")?n.getLong("Born"):-1;}
    @Override protected void writeCustomDataToNbt(NbtCompound n){n.putInt("Slot",slot());n.putBoolean("Treasure",treasure());n.putLong("Cycle",cycle());n.putLong("Opened",openedAt());if(loose()){n.putInt("Drops",looseDrops);n.putLong("Born",born);}}
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
    @Override public boolean damage(DamageSource source,float amount){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canHit(){return false;}
    @Override public boolean canUsePortals(){return false;}
}
