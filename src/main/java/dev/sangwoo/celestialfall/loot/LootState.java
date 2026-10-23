package dev.sangwoo.celestialfall.loot;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import java.util.*;
/** Slot authority outlives its visual entity, chunk unload, player disconnect and server restart. */
public final class LootState extends PersistentState {
    public record Slot(boolean treasure,int drops,long cycle,long respawnAt,long openedAt){
        public boolean available(){return openedAt<0;}
        public boolean due(long now){return !available()&&now>=respawnAt;}
        public Slot claimed(long now,int delay){if(!available())throw new IllegalStateException("Already claimed");return new Slot(treasure,drops,cycle,now+delay,now);}
    }
    private final Map<Integer,Slot> slots=new HashMap<>();
    public static LootState get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(LootState::read,LootState::new,"botand_field_loot");}
    public Slot slot(int index){return slots.get(index);}
    public void put(int index,Slot value){if(!LootPositions.valid(index))throw new IllegalArgumentException("Unknown field slot");slots.put(index,value);markDirty();}
    public boolean claim(int index,long cycle,long now,int delay){
        var s=slots.get(index);if(s==null||s.cycle()!=cycle||!s.available())return false;
        put(index,s.claimed(now,delay));return true;
    }
    public static LootState read(NbtCompound tag){
        var state=new LootState();var list=tag.getList("Slots",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++){
            var n=list.getCompound(i);int id=n.getInt("Id");if(!LootPositions.valid(id))continue;
            boolean chest=n.getBoolean("Treasure");long opened=n.contains("Opened")?n.getLong("Opened"):-1;
            int drops=n.getInt("Drops"),extras=drops&(LootRules.ARROW|LootRules.STICK);
            state.slots.put(id,new Slot(chest,(chest?LootRules.FRUIT:drops&(LootRules.FRUIT|LootRules.KEY))|extras,Math.max(0,n.getLong("Cycle")),n.getLong("Respawn"),opened));
        }return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound tag){
        var list=new NbtList();slots.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            var s=e.getValue();var n=new NbtCompound();n.putInt("Id",e.getKey());n.putBoolean("Treasure",s.treasure());n.putInt("Drops",s.drops());
            n.putLong("Cycle",s.cycle());n.putLong("Respawn",s.respawnAt());n.putLong("Opened",s.openedAt());list.add(n);
        });tag.putInt("Version",1);tag.put("Slots",list);return tag;
    }
}
