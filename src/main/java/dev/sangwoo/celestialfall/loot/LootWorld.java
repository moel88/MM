package dev.sangwoo.celestialfall.loot;
import dev.sangwoo.celestialfall.map.MapGate;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;
public final class LootWorld {
    private static final Map<ServerWorld,int[]> LOADED=new IdentityHashMap<>();
    public static boolean enabled(ServerWorld w){return w.getRegistryKey().equals(World.OVERWORLD)&&MapGate.isBotandMap(w.getServer())&&MapGate.enabled(w);}
    public static void initialize(){
        ServerTickEvents.END_WORLD_TICK.register(LootWorld::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s->LOADED.keySet().removeIf(w->w.getServer()==s));
    }
    public static LootState.Slot next(ServerWorld w,long cycle){
        boolean chest=LootRules.treasure(w.random.nextInt(100));
        int drops=LootRules.drops(chest,w.random.nextInt(100),w.random.nextInt(100))|LootRules.extras(chest,w.random.nextInt(1000),w.random.nextInt(100));
        return new LootState.Slot(chest,drops,cycle,-1,-1);
    }
    public static void tick(ServerWorld w){
        if(!enabled(w))return;
        var spots=LootPositions.current();
        var state=LootState.get(w);int[] loaded=LOADED.computeIfAbsent(w,k->new int[spots.size()]);
        if(loaded.length!=spots.size()){loaded=new int[spots.size()];LOADED.put(w,loaded);}
        for(int i=0;i<loaded.length;i++){
            var pos=spots.get(i);
            if(!w.isChunkLoaded(pos)){loaded[i]=0;continue;}
            if(++loaded[i]<40||loaded[i]%10!=0)continue;
            var slot=state.slot(i);
            if(slot==null){
                if(!migrate(w,pos))continue;
                slot=next(w,0);state.put(i,slot);
            }
            if(slot.due(w.getTime())){slot=next(w,slot.cycle()+1);state.put(i,slot);}
            var existing=w.getEntity(LootPositions.id(i));
            if(existing!=null){
                if(existing instanceof FieldLootEntity loot&&loot.cycle()!=slot.cycle())existing.discard();
                else continue;
            }
            if(!slot.available()||!w.getBlockState(pos).isAir())continue;
            var entity=LootRegistry.FIELD_LOOT.create(w);if(entity==null)continue;
            entity.setUuid(LootPositions.id(i));entity.configure(i,slot);
            entity.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);w.spawnEntity(entity);
        }
    }
    /** Replace only known field supplies or an already emptied marker, never player storage elsewhere. */
    public static boolean migrate(ServerWorld w,BlockPos pos){
        var block=w.getBlockState(pos);if(block.isAir())return true;
        if(!block.isOf(Blocks.CHEST)||block.get(ChestBlock.CHEST_TYPE)!=ChestType.SINGLE||!(w.getBlockEntity(pos) instanceof ChestBlockEntity chest)||chest.hasCustomName())return false;
        int bread=0,iron=0,torch=0;
        for(int i=0;i<chest.size();i++){
            var stack=chest.getStack(i);if(stack.isEmpty())continue;
            if(stack.hasNbt())return false;
            if(stack.isOf(Items.BREAD))bread+=stack.getCount();else if(stack.isOf(Items.IRON_INGOT))iron+=stack.getCount();else if(stack.isOf(Items.TORCH))torch+=stack.getCount();else return false;
        }
        if(bread>12||iron>8||torch>16)return false;
        // Remove the block entity first so vanilla does not scatter the replaced map supplies.
        w.removeBlockEntity(pos);return w.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.FORCE_STATE|Block.SKIP_DROPS);
    }
    private LootWorld(){}
}
