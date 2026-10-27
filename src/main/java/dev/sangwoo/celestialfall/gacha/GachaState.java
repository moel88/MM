package dev.sangwoo.celestialfall.gacha;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Each player's gacha count, kept with the world so the guarantees survive leaving, dying and server restarts. */
public final class GachaState extends PersistentState {
    private final Map<UUID,GachaRules.Count> players=new HashMap<>();
    public static GachaState get(MinecraftServer server){
        return server.getOverworld().getPersistentStateManager().getOrCreate(GachaState::read,GachaState::new,"botand_gacha");
    }
    public GachaRules.Count get(UUID id){return players.getOrDefault(id,GachaRules.Count.NEW);}
    public void set(UUID id,GachaRules.Count value){if(!value.equals(players.put(id,value)))markDirty();}
    public static GachaState read(NbtCompound nbt){
        var state=new GachaState();var list=nbt.getList("Players",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++){
            var row=list.getCompound(i);if(!row.containsUuid("Id"))continue;
            state.players.put(row.getUuid("Id"),new GachaRules.Count(row.getInt("SinceSsr"),row.getInt("SinceSr"),row.getInt("Pulls")));
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt){
        var list=new NbtList();players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            var c=e.getValue();var row=new NbtCompound();row.putUuid("Id",e.getKey());
            row.putInt("SinceSsr",c.sinceSsr());row.putInt("SinceSr",c.sinceSr());row.putInt("Pulls",c.pulls());list.add(row);
        });
        nbt.putInt("Version",1);nbt.put("Players",list);return nbt;
    }
}
