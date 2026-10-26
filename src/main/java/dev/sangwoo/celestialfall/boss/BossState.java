package dev.sangwoo.celestialfall.boss;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * What the world keeps of its bosses (2.69.0), across restarts: for each kind, where it comes (set by an operator,
 * "/botand boss setspawn"; until then the map's own spot, Bosses.spot), when it comes next (the overworld's time), and
 * whether it was up (with how much health, and where it stands) when the server stopped, to bring it back so.
 */
public final class BossState extends PersistentState {
    public static final class Entry {
        /** Where it comes, set by an operator (NaN until then), and which way it faces there. */
        public double x=Double.NaN,y,z;public float yaw;
        /** The overworld's tick it comes next (0: not counted yet), whether it was up, and its health then. */
        public long next;public boolean up;public float health;
        /** Where it stands while it is up (where it came this time, NaN when it is not up), and which way it faces there. */
        public double hx=Double.NaN,hy,hz;public float hyaw;
        public boolean placed(){return !Double.isNaN(x);}
        public boolean standing(){return !Double.isNaN(hx);}
    }
    private final Map<BossKind,Entry> entries=new EnumMap<>(BossKind.class);
    public static BossState get(MinecraftServer server){
        return server.getOverworld().getPersistentStateManager().getOrCreate(BossState::read,BossState::new,"botand_bosses");
    }
    public Entry entry(BossKind kind){return entries.computeIfAbsent(kind,k->new Entry());}
    public void changed(){markDirty();}
    public static BossState read(NbtCompound tag){
        var state=new BossState();
        for(var kind:BossKind.values()){
            if(!tag.contains(kind.id))continue;
            var n=tag.getCompound(kind.id);var e=state.entry(kind);
            if(n.contains("X")){e.x=n.getDouble("X");e.y=n.getDouble("Y");e.z=n.getDouble("Z");e.yaw=n.getFloat("Yaw");}
            e.next=n.getLong("Next");e.up=n.getBoolean("Up");e.health=n.getFloat("Health");
            if(n.contains("HomeX")){e.hx=n.getDouble("HomeX");e.hy=n.getDouble("HomeY");e.hz=n.getDouble("HomeZ");e.hyaw=n.getFloat("HomeYaw");}
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound tag){
        tag.putInt("Version",1);
        for(var en:entries.entrySet()){
            var e=en.getValue();var n=new NbtCompound();
            if(e.placed()){n.putDouble("X",e.x);n.putDouble("Y",e.y);n.putDouble("Z",e.z);n.putFloat("Yaw",e.yaw);}
            n.putLong("Next",e.next);n.putBoolean("Up",e.up);n.putFloat("Health",e.health);
            if(e.standing()){n.putDouble("HomeX",e.hx);n.putDouble("HomeY",e.hy);n.putDouble("HomeZ",e.hz);n.putFloat("HomeYaw",e.hyaw);}
            tag.put(en.getKey().id,n);
        }
        return tag;
    }
}
