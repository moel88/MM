package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.progression.*;
import net.minecraft.nbt.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProgressionTest {
    @Test void defaultsAreFreshAndKagenoIsAStand() {
        assertEquals(new ProgressProfile(1,0,0,"none"),ProgressProfile.NEW);
        assertEquals(200,StandStats.get("none").health());assertEquals(200,StandStats.get("kageno").health());
        assertTrue(StandStats.known("kageno"));assertEquals("none",new ProgressProfile(1,0,0,null).stand());
    }
    @Test void rebirthIsAtomicAndRequiresLevelFifty() {
        var p=new ProgressProfile(49,5,2,"kageno");assertSame(p,p.rebirth());
        var next=p.withLevel(50).rebirth();assertEquals(new ProgressProfile(1,6,2,"kageno"),next);
        assertSame(next,next.rebirth());assertEquals(50,next.addLevels(Integer.MAX_VALUE).level());
    }
    @Test void fifteenRebirthsThenTranscendResetOnlyRebirthCounter() {
        var p=ProgressProfile.NEW;
        for(int i=0;i<15;i++){assertFalse(p.canTranscend());p=p.withLevel(50).rebirth();assertEquals(i+1,p.rebirths());}
        p=p.withLevel(23);assertFalse(p.canRebirth());assertTrue(p.canTranscend());
        var next=p.transcend();assertEquals(new ProgressProfile(23,0,1,"none"),next);assertSame(next,next.transcend());
        var cap=new ProgressProfile(50,15,ProgressProfile.MAX_TRANSCENDENCE,"kageno");assertSame(cap,cap.transcend());
    }
    @Test void saveRoundTripKeepsPlayersAndWorldsIndependent() {
        var a=UUID.randomUUID();var b=UUID.randomUUID();var state=new ProgressState();
        var p=new ProgressProfile(50,15,2,"kageno");state.set(a,p);state.set(b,new ProgressProfile(17,3,1,"none"));
        assertTrue(state.isDirty());var restored=ProgressState.read(state.writeNbt(new NbtCompound()));
        assertEquals(p,restored.profile(a));assertEquals(17,restored.profile(b).level());
        assertEquals(ProgressProfile.NEW,new ProgressState().profile(a));
    }
    @Test void corruptCountersCannotCreateNegativeOrOverCapState() {
        var id=UUID.randomUUID();var nbt=new NbtCompound();var list=new NbtList();list.add(new NbtCompound());
        var row=new NbtCompound();row.putUuid("Id",id);row.putInt("Level",999);row.putInt("Rebirths",-2);row.putInt("Transcendence",Integer.MAX_VALUE);row.putString("Stand","not_a_stand");list.add(row);nbt.put("Players",list);
        assertEquals(new ProgressProfile(50,0,ProgressProfile.MAX_TRANSCENDENCE,"none"),ProgressState.read(nbt).profile(id));
    }
}
