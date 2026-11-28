package dev.sangwoo.celestialfall.progression;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** World-wide UUID records survive death, dimension transfers and server restarts. */
public final class ProgressState extends PersistentState {
    private final Map<UUID,ProgressProfile> players=new HashMap<>();
    private final Set<UUID> clearedStands=new HashSet<>();
    /**
     * The players awakened (2.72.0): Star Platinum's time stop (H) is theirs. For now an operator grants it
     * (/botand progress awaken); the awakening quest to come will. Kept apart from the profile: a new stand keeps it.
     */
    private final Set<UUID> awakened=new HashSet<>();
    /**
     * Each player's stand skin (2.156.0, StandSkins): the id of one of their stand's skins, none kept for its own look. A
     * skin is its stand's: when their stand changes (a rokakaka, the arrow, the gacha, an operator) it goes, and the stand
     * they get next is in its own look (asked and answered: "원본으로 돌아감"). Kept apart from the profile.
     */
    private final Map<UUID,String> skins=new HashMap<>();
    public static ProgressState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(ProgressState::read,ProgressState::new,"botand_progress");
    }
    public ProgressProfile profile(UUID id){return players.getOrDefault(id,ProgressProfile.NEW);}
    public boolean standCleared(UUID id){return clearedStands.contains(id);}
    public boolean awakened(UUID id){return awakened.contains(id);}
    public void awaken(UUID id,boolean value){if(value?awakened.add(id):awakened.remove(id))markDirty();}
    /** The player's stand skin: an id among their stand's skins (StandSkins), "original" for its own look. */
    public String skin(UUID id){return skins.getOrDefault(id,dev.sangwoo.celestialfall.stand.StandSkins.ORIGINAL);}
    public void skin(UUID id,String skin){
        if(dev.sangwoo.celestialfall.stand.StandSkins.ORIGINAL.equals(skin)?skins.remove(id)!=null:!skin.equals(skins.put(id,skin)))markDirty();
    }
    public void set(UUID id,ProgressProfile profile){
        var before=profile(id);
        // (2.156.0) a stand that changes takes its skin with it
        if(!profile.stand().equals(before.stand())&&skins.remove(id)!=null)markDirty();
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
        var awake=nbt.getList("Awakened",NbtElement.INT_ARRAY_TYPE);
        for(int i=0;i<awake.size();i++){var id=awake.get(i);if(id instanceof NbtIntArray a&&a.getIntArray().length==4)state.awakened.add(NbtHelper.toUuid(a));}
        var skins=nbt.getList("Skins",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<skins.size();i++){var row=skins.getCompound(i);if(row.containsUuid("Id")&&!row.getString("Skin").isEmpty())state.skins.put(row.getUuid("Id"),row.getString("Skin"));}
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        var list=new NbtList();players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            var p=entry.getValue();var row=new NbtCompound();row.putUuid("Id",entry.getKey());row.putInt("Level",p.level());
            row.putInt("Rebirths",p.rebirths());row.putInt("Transcendence",p.transcendence());row.putString("Stand",p.stand());row.putLong("Money",p.money());row.putInt("Xp",p.xp());row.putBoolean("StandCleared",clearedStands.contains(entry.getKey()));list.add(row);
        });nbt.putInt("Version",2);nbt.put("Players",list);
        var awake=new NbtList();awakened.stream().sorted().forEach(id->awake.add(NbtHelper.fromUuid(id)));nbt.put("Awakened",awake);
        var skin=new NbtList();skins.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{var row=new NbtCompound();row.putUuid("Id",entry.getKey());row.putString("Skin",entry.getValue());skin.add(row);});
        nbt.put("Skins",skin);
        return nbt;
    }
}
