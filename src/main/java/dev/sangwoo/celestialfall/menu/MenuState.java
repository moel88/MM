package dev.sangwoo.celestialfall.menu;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.PersistentState;
import net.minecraft.server.MinecraftServer;
public final class MenuState extends PersistentState {
    private final Map<UUID,MenuProgress> players=new HashMap<>();
    public static MenuState get(MinecraftServer s){return s.getOverworld().getPersistentStateManager().getOrCreate(MenuState::read,MenuState::new,"botand_menu");}
    public MenuProgress get(UUID id){return players.getOrDefault(id,new MenuProgress(0,-1));}
    public void set(UUID id,MenuProgress value){if(!value.equals(players.put(id,value)))markDirty();}
    public static MenuState read(NbtCompound n){var s=new MenuState();var l=n.getList("Players",NbtElement.COMPOUND_TYPE);for(int i=0;i<l.size();i++){var e=l.getCompound(i);if(e.containsUuid("Id"))s.players.put(e.getUuid("Id"),new MenuProgress(e.getInt("Unlocked"),e.contains("Equipped")?e.getInt("Equipped"):-1));}return s;}
    public NbtCompound writeNbt(NbtCompound n){var l=new NbtList();players.forEach((id,p)->{var e=new NbtCompound();e.putUuid("Id",id);e.putInt("Unlocked",p.unlocked());e.putInt("Equipped",p.equipped());l.add(e);});n.put("Players",l);return n;}
}
