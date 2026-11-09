package dev.sangwoo.celestialfall.map;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Per-save, per-dimension designation. Never a global config switch. */
public final class MapState extends PersistentState {
    public record ReturnState(double baseHealth,NbtCompound hunger) {}
    private final Map<String,Boolean> overrides=new HashMap<>();
    private final Map<UUID,ReturnState> returns=new HashMap<>();
    private final Set<UUID> visited=new HashSet<>();
    /** (2.110.0) The players who play as plain Minecraft on the map (VanillaMode), each with the game mode they had before. */
    private final Map<UUID,String> vanilla=new HashMap<>();
    public static MapState get(MinecraftServer s) {return s.getOverworld().getPersistentStateManager().getOrCreate(MapState::read,MapState::new,"botand_map");}
    public Boolean override(String dimension){return overrides.get(dimension);}
    public void designate(String dimension,Boolean enabled){if(enabled==null)overrides.remove(dimension);else overrides.put(dimension,enabled);markDirty();}
    public ReturnState returnState(UUID id){return returns.get(id);}
    public boolean firstVisit(UUID id){if(!visited.add(id))return false;markDirty();return true;}
    public void remember(UUID id,double health,NbtCompound hunger){if(!returns.containsKey(id)){returns.put(id,new ReturnState(health,hunger.copy()));markDirty();}}
    public ReturnState forget(UUID id){var r=returns.remove(id);if(r!=null)markDirty();return r;}
    /** The game mode this player had before their vanilla mode (VanillaMode), null if they are not in it. */
    public String vanilla(UUID id){return vanilla.get(id);}
    /** This player's vanilla mode on, remembering the game mode they had ({@code before}), or off ({@code before} null). */
    public void vanilla(UUID id,String before){if(before==null?vanilla.remove(id)!=null:!before.equals(vanilla.put(id,before)))markDirty();}
    public static MapState read(NbtCompound n) {
        var s=new MapState();var modes=n.getCompound("Dimensions");for(String k:modes.getKeys())s.overrides.put(k,modes.getBoolean(k));
        var seen=n.getList("Visited",NbtElement.STRING_TYPE);for(int i=0;i<seen.size();i++)try{s.visited.add(UUID.fromString(seen.getString(i)));}catch(IllegalArgumentException ignored){}
        var plain=n.getList("Vanilla",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<plain.size();i++){var e=plain.getCompound(i);if(e.containsUuid("Id"))s.vanilla.put(e.getUuid("Id"),e.getString("Before"));}
        var list=n.getList("ReturnState",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++){var e=list.getCompound(i);try {
            if(!e.containsUuid("Id"))continue;
            double base=e.getDouble("BaseHealth");if(!Double.isFinite(base)||base<=0||base>100000)continue;
            s.returns.put(e.getUuid("Id"),new ReturnState(base,e.getCompound("Hunger").copy()));
        }catch(IllegalArgumentException ignored){}}
        return s;
    }
    @Override public NbtCompound writeNbt(NbtCompound n) {
        var modes=new NbtCompound();overrides.forEach(modes::putBoolean);n.put("Dimensions",modes);
        var seen=new NbtList();visited.forEach(id->seen.add(NbtString.of(id.toString())));n.put("Visited",seen);
        var plain=new NbtList();vanilla.forEach((id,before)->{var e=new NbtCompound();e.putUuid("Id",id);e.putString("Before",before);plain.add(e);});n.put("Vanilla",plain);
        var list=new NbtList();returns.forEach((id,r)->{var e=new NbtCompound();e.putUuid("Id",id);e.putDouble("BaseHealth",r.baseHealth);e.put("Hunger",r.hunger.copy());list.add(e);});n.put("ReturnState",list);return n;
    }
}
