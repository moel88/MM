package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.progression.*;
import net.minecraft.nbt.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProgressionTest {
    @Test void defaultsAreFreshAndKagenoIsAStand() {
        assertEquals(new ProgressProfile(1,0,0,"none"),ProgressProfile.NEW);
        assertEquals(350,StandStats.get("none").health());assertEquals(325,StandStats.get("kageno").health());
        assertTrue(StandStats.known("kageno"));assertEquals("none",new ProgressProfile(1,0,0,null).stand());
    }
    @Test void eachCharacterHasItsOwnHealth() {
        // (2.140.0, "스탠드 없음은 hp 350 이소상은 300 엑스차라는 375 스타플래티나는 400 카게노 325")
        assertEquals(350,StandStats.get("none").health());assertEquals(300,StandStats.get("isosang").health());
        assertEquals(375,StandStats.get("x_chara").health());assertEquals(400,StandStats.get("star_platinum").health());
        assertEquals(325,StandStats.get("kageno").health());
        // (2.146.0, the user's answer when asked) Gold Experience 375
        assertEquals(375,StandStats.get("gold_experience").health());assertTrue(StandStats.known("gold_experience"));
        // one the mod does not know is as one with none
        assertEquals(350,StandStats.get("nobody").health());
        for(String id:StandStats.ids())assertTrue(StandStats.known(id)&&StandStats.get(id).health()>=300,id);
    }
    @Test void leeSoSangIsACharacterTheAdminCommandGives() {
        // (2.76.0) given by /botand progress stand <targets> isosang, which offers every id
        assertTrue(StandStats.known("isosang"));assertEquals("이소상",StandStats.get("isosang").korean());
        assertEquals("stand.botand.isosang",StandStats.get("isosang").translation());assertEquals(300,StandStats.get("isosang").health());
        assertTrue(java.util.Arrays.asList(StandStats.ids()).contains("isosang"));
        assertEquals("isosang",new ProgressProfile(1,0,0,"isosang").stand());
    }
    @Test void xCharaIsACharacterTheAdminCommandGives() {
        // (2.111.0) given by /botand progress stand <targets> x_chara, which offers every id
        assertTrue(StandStats.known("x_chara"));assertEquals("엑스 차라",StandStats.get("x_chara").korean());
        assertEquals("stand.botand.x_chara",StandStats.get("x_chara").translation());assertEquals(375,StandStats.get("x_chara").health());
        assertTrue(java.util.Arrays.asList(StandStats.ids()).contains("x_chara"));
        assertEquals("x_chara",new ProgressProfile(1,0,0,"x_chara").stand());
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
    @Test void aStandSkinIsItsStandsKeptThroughASaveAndGoneWithTheStand() {
        // (2.156.0) Gold Experience's own look is the box-built figure, its skin "고퀄리티" (hq) the model the user sent;
        // the numbers every client learns: 0 its own look, 1 on its skins (three bits of the stand's tracked byte)
        assertEquals(java.util.List.of("original","hq"),dev.sangwoo.celestialfall.stand.StandSkins.of("gold_experience"));
        assertEquals(java.util.List.of("original"),dev.sangwoo.celestialfall.stand.StandSkins.of("star_platinum"),"no skins of its own yet");
        assertEquals(0,dev.sangwoo.celestialfall.stand.StandSkins.number("gold_experience","original"));
        assertEquals(1,dev.sangwoo.celestialfall.stand.StandSkins.number("gold_experience","hq"));
        assertEquals(-1,dev.sangwoo.celestialfall.stand.StandSkins.number("star_platinum","hq"),"a skin is its stand's");
        assertEquals("hq",dev.sangwoo.celestialfall.stand.StandSkins.skin("gold_experience",1));
        assertEquals("original",dev.sangwoo.celestialfall.stand.StandSkins.skin("gold_experience",5),"a number it has no skin for: its own look");
        assertEquals("original",dev.sangwoo.celestialfall.stand.StandSkins.skin("star_platinum",1));
        for(String id:StandStats.ids())assertTrue(dev.sangwoo.celestialfall.stand.StandSkins.of(id).size()-1<=dev.sangwoo.celestialfall.stand.StandSkins.MOST,id);
        assertTrue(dev.sangwoo.celestialfall.stand.StandSkins.MOST<=7,"three bits");
        assertEquals("skin.botand.gold_experience.hq",dev.sangwoo.celestialfall.stand.StandSkins.translation("gold_experience","hq"));
        assertEquals("skin.botand.original",dev.sangwoo.celestialfall.stand.StandSkins.translation("kageno","original"));
        // each player's own, its own look at first; kept through a save and a change that keeps their stand
        var a=UUID.randomUUID();var b=UUID.randomUUID();var state=new ProgressState();
        assertEquals("original",state.skin(a));
        state.set(a,new ProgressProfile(10,0,0,"gold_experience"));state.set(b,new ProgressProfile(10,0,0,"gold_experience"));
        var clean=ProgressState.read(state.writeNbt(new NbtCompound()));
        clean.skin(a,"hq");assertTrue(clean.isDirty());assertEquals("hq",clean.skin(a));assertEquals("original",clean.skin(b));
        var restored=ProgressState.read(clean.writeNbt(new NbtCompound()));
        assertEquals("hq",restored.skin(a));assertEquals("original",restored.skin(b));
        restored.set(a,restored.profile(a).addLevels(3));assertEquals("hq",restored.skin(a),"a level does not take it");
        // the stand they lose takes its skin with it: the next is in its own look ("원본으로 돌아감"), even the same stand again
        restored.set(a,restored.profile(a).withStand("none"));assertEquals("original",restored.skin(a));
        restored.set(a,restored.profile(a).withStand("gold_experience"));assertEquals("original",restored.skin(a),"got again: its own look");
        assertEquals("original",ProgressState.read(restored.writeNbt(new NbtCompound())).skin(a));
        // back to its own look is no skin kept
        var back=new ProgressState();back.set(b,new ProgressProfile(1,0,0,"gold_experience"));back.skin(b,"hq");back.skin(b,"original");
        assertEquals("original",back.skin(b));
        assertFalse(back.writeNbt(new NbtCompound()).getList("Skins",NbtElement.COMPOUND_TYPE).size()>0,"none saved");
        // an old save has none
        var old=new NbtCompound();old.putInt("Version",2);old.put("Players",new NbtList());assertEquals("original",ProgressState.read(old).skin(a));
    }
    @Test void corruptCountersCannotCreateNegativeOrOverCapState() {
        var id=UUID.randomUUID();var nbt=new NbtCompound();var list=new NbtList();list.add(new NbtCompound());
        var row=new NbtCompound();row.putUuid("Id",id);row.putInt("Level",999);row.putInt("Rebirths",-2);row.putInt("Transcendence",Integer.MAX_VALUE);row.putString("Stand","not_a_stand");list.add(row);nbt.put("Players",list);
        assertEquals(new ProgressProfile(50,0,ProgressProfile.MAX_TRANSCENDENCE,"none"),ProgressState.read(nbt).profile(id));
    }
}
