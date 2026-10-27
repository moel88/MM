package dev.sangwoo.celestialfall;

import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoCooldownStateTest {
    @Test void switchIsPerPlayerAndSurvivesWorldSaveRoundTrip() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();var state=new NoCooldownState();
        assertFalse(state.contains(first));state.set(first,true);assertTrue(state.isDirty());
        var restored=NoCooldownState.read(state.writeNbt(new NbtCompound()));
        assertTrue(restored.contains(first));assertFalse(restored.contains(second));
        assertFalse(new NoCooldownState().contains(first),"Different worlds must not share a static toggle");
    }
    @Test void offIsPersistentAndIdempotent() {
        UUID id=UUID.randomUUID();var state=new NoCooldownState();state.set(id,true);state.set(id,true);
        assertEquals(1,state.writeNbt(new NbtCompound()).getList("Players",NbtElement.STRING_TYPE).size());
        state.set(id,false);state.set(id,false);
        assertFalse(NoCooldownState.read(state.writeNbt(new NbtCompound())).contains(id));
    }
    @Test void malformedEntryDoesNotDiscardOtherPlayers() {
        UUID id=UUID.randomUUID();var nbt=new NbtCompound();var list=new NbtList();
        list.add(NbtString.of("bad-entry"));list.add(NbtString.of(id.toString()));nbt.put("Players",list);
        assertTrue(NoCooldownState.read(nbt).contains(id));
    }
}
