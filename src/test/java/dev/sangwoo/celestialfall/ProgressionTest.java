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
    @Test void leeSoSangIsACharacterTheAdminCommandGives() {
        // (2.76.0) given by /botand progress stand <targets> isosang, which offers every id
        assertTrue(StandStats.known("isosang"));assertEquals("이소상",StandStats.get("isosang").korean());
        assertEquals("stand.botand.isosang",StandStats.get("isosang").translation());assertEquals(200,StandStats.get("isosang").health());
        assertTrue(java.util.Arrays.asList(StandStats.ids()).contains("isosang"));
        assertEquals("isosang",new ProgressProfile(1,0,0,"isosang").stand());
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
    @Test void theAwakeningIsKeptApartAndSurvivesASave() {
        // (2.72.0) Star Platinum's time stop is the awakened's: none awakened at first, an old save has none, the
        // awakening survives a save and a new stand, and taking it back is saved too
        var a=UUID.randomUUID();var b=UUID.randomUUID();var state=new ProgressState();
        assertFalse(state.awakened(a));
        assertFalse(ProgressState.read(new ProgressState().writeNbt(new NbtCompound())).awakened(a));
        var old=new NbtCompound();old.putInt("Version",2);old.put("Players",new NbtList());assertFalse(ProgressState.read(old).awakened(a));
        state.set(a,new ProgressProfile(10,0,0,"star_platinum"));state.awaken(a,true);
        assertTrue(state.isDirty());
        var restored=ProgressState.read(state.writeNbt(new NbtCompound()));
        assertTrue(restored.awakened(a));assertFalse(restored.awakened(b));
        restored.set(a,restored.profile(a).withStand("none"));assertTrue(restored.awakened(a),"a new stand keeps it");
        restored.awaken(a,false);assertFalse(ProgressState.read(restored.writeNbt(new NbtCompound())).awakened(a));
        // never in the profile's rows: an awakened player with no profile yet is saved as well
        var lone=new ProgressState();lone.awaken(b,true);assertTrue(ProgressState.read(lone.writeNbt(new NbtCompound())).awakened(b));
    }
    @Test void corruptCountersCannotCreateNegativeOrOverCapState() {
        var id=UUID.randomUUID();var nbt=new NbtCompound();var list=new NbtList();list.add(new NbtCompound());
        var row=new NbtCompound();row.putUuid("Id",id);row.putInt("Level",999);row.putInt("Rebirths",-2);row.putInt("Transcendence",Integer.MAX_VALUE);row.putString("Stand","not_a_stand");list.add(row);nbt.put("Players",list);
        assertEquals(new ProgressProfile(50,0,ProgressProfile.MAX_TRANSCENDENCE,"none"),ProgressState.read(nbt).profile(id));
    }
}
