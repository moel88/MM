package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.map.MapState;
import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapStateTest {
    @Test void savesKeepIndependentPerDimensionOverrides() {
        var state=new MapState();state.designate("minecraft:overworld",true);state.designate("minecraft:the_nether",false);
        var restored=MapState.read(state.writeNbt(new NbtCompound()));
        assertEquals(true,restored.override("minecraft:overworld"));assertEquals(false,restored.override("minecraft:the_nether"));
        assertNull(restored.override("minecraft:the_end"));assertNull(new MapState().override("minecraft:overworld"));
        restored.designate("minecraft:overworld",null);assertNull(MapState.read(restored.writeNbt(new NbtCompound())).override("minecraft:overworld"));
    }
    @Test void repeatedEntriesNeverOverwriteOriginalPlayerHealthAndHunger() {
        var state=new MapState();UUID id=UUID.randomUUID();var hunger=new NbtCompound();hunger.putInt("foodLevel",7);
        state.remember(id,40,hunger);hunger.putInt("foodLevel",20);state.remember(id,200,hunger);
        var restored=MapState.read(state.writeNbt(new NbtCompound()));
        var back=restored.forget(id);assertEquals(40,back.baseHealth());assertEquals(7,back.hunger().getInt("foodLevel"));
        assertTrue(restored.firstVisit(id));assertFalse(restored.firstVisit(id));
        assertFalse(MapState.read(restored.writeNbt(new NbtCompound())).firstVisit(id));
        assertNull(restored.forget(id));assertNull(MapState.read(restored.writeNbt(new NbtCompound())).returnState(id));
    }
    @Test void invalidEntryDoesNotDiscardOtherPlayers() {
        var state=new MapState();UUID valid=UUID.randomUUID();state.remember(valid,20,new NbtCompound());
        var raw=state.writeNbt(new NbtCompound());var entries=raw.getList("ReturnState",NbtElement.COMPOUND_TYPE);
        var invalid=new NbtCompound();invalid.putUuid("Id",UUID.randomUUID());invalid.putDouble("BaseHealth",Double.NaN);entries.add(invalid);
        var noId=new NbtCompound();noId.putDouble("BaseHealth",20);entries.add(noId);
        var restored=MapState.read(raw);assertEquals(20,restored.returnState(valid).baseHealth());
        assertEquals(1,restored.writeNbt(new NbtCompound()).getList("ReturnState",NbtElement.COMPOUND_TYPE).size());
    }
}
