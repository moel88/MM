package dev.sangwoo.celestialfall.progression;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** World-wide UUID records survive death, dimension transfers and server restarts. */
public final class ProgressState extends PersistentState {
    private final Map<UUID,ProgressProfile> players=new HashMap<>();
    private final Set<UUID> clearedStands=new HashSet<>();
    public static ProgressState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(ProgressState::read,ProgressState::new,"botand_progress");
    }
    public ProgressProfile profile(UUID id){return players.getOrDefault(id,ProgressProfile.NEW);}
    public boolean standCleared(UUID id){return clearedStands.contains(id);}
    public void set(UUID id,ProgressProfile profile){
        var before=profile(id);
        if(!profile.stand().equals("none")){if(clearedStands.remove(id))markDirty();}
        else if(!before.stand().equals("none")){clearedStands.add(id);markDirty();}
        if(!profile.equals(players.put(id,profile)))markDirty();
    }
    public static ProgressState read(NbtCompound nbt) {
        var state=new ProgressState();var list=nbt.getList("Players",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++) {
            var row=list.getCompound(i);if(!row.containsUuid("Id"))continue;
            state.players.put(row.getUuid("Id"),new ProgressProfile(row.getInt("Level"),row.getInt("Rebirths"),row.getInt("Transcendence"),row.getString("Stand"),row.getLong("Money"),row.getInt("Xp")));
            if(row.getBoolean("StandCleared"))state.clearedStands.add(row.getUuid("Id"));
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        var list=new NbtList();players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            var p=entry.getValue();var row=new NbtCompound();row.putUuid("Id",entry.getKey());row.putInt("Level",p.level());
            row.putInt("Rebirths",p.rebirths());row.putInt("Transcendence",p.transcendence());row.putString("Stand",p.stand());row.putLong("Money",p.money());row.putInt("Xp",p.xp());row.putBoolean("StandCleared",clearedStands.contains(entry.getKey()));list.add(row);
        });nbt.putInt("Version",2);nbt.put("Players",list);return nbt;
    }
}
