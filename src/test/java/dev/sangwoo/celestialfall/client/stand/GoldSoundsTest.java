package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gold Experience's sounds (2.153.0): the user's recordings for its R and Y (tools/stand/cut_gold_sounds.py), and its T's
 * and H's (and since 2.154.0 its basic attack's) laid together from downloaded recordings (tools/stand/make_gold_sounds.py:
 * nothing synthesized). Every one is
 * the mod's (in sounds.json, its files there, Ogg Vorbis, mono at 48 kHz, as long as its tool makes it); the croak and the
 * knock are three each, the game picking one; the buzz loops in the world; and each comes on its skill's moment.
 */
class GoldSoundsTest {
    static final Path ASSETS=Path.of("src/main/resources/assets/celestialfall");
    static int header(byte[] b){
        for(int i=0;i+16<b.length;i++)if(b[i]==1&&b[i+1]=='v'&&b[i+2]=='o'&&b[i+3]=='r'&&b[i+4]=='b'&&b[i+5]=='i'&&b[i+6]=='s')return i;
        return -1;
    }
    /** How long an Ogg Vorbis file plays (s): its last page's granule position over its rate. */
    static float seconds(byte[] b){
        int id=header(b),rate=(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24,last=-1;
        for(int i=b.length-27;i>=0&&last<0;i--)if(b[i]=='O'&&b[i+1]=='g'&&b[i+2]=='g'&&b[i+3]=='S')last=i;
        long granule=0;for(int k=7;k>=0;k--)granule=granule<<8|(b[last+6+k]&255);
        return granule/(float)rate;
    }
    /** Every file and how long its tool makes it (s). */
    static final Map<String,Float> FILES=Map.ofEntries(
        Map.entry("gold_experience_overdrive",.689f),Map.entry("gold_experience_mend",1.373f),Map.entry("gold_experience_self_heal",1.345f),
        Map.entry("gold_life_gather",.27f),Map.entry("gold_life_born",.75f),Map.entry("gold_life_hop",.5f),
        Map.entry("gold_life_croak_1",.42f),Map.entry("gold_life_croak_2",.34f),Map.entry("gold_life_croak_3",.27f),
        Map.entry("gold_life_reflect",.6f),Map.entry("gold_life_frog_back",.85f),Map.entry("gold_life_frog_burst",.85f),
        Map.entry("gold_life_punch",.32f),Map.entry("gold_life_strike",.75f),Map.entry("gold_life_fly_born",.5f),
        Map.entry("gold_life_fly_buzz",2.5f),Map.entry("gold_life_fly_back",.42f),Map.entry("gold_life_swat",.6f),Map.entry("gold_life_fall",.75f),
        Map.entry("gold_tree_touch",.62f),Map.entry("gold_tree_burst",1.45f),
        Map.entry("gold_tree_knock_1",.55f),Map.entry("gold_tree_knock_2",.5f),Map.entry("gold_tree_knock_3",.5f),Map.entry("gold_tree_wither",1.3f),
        // (2.154.0) the basic attack's
        Map.entry("gold_punch_swing_1",.26f),Map.entry("gold_punch_swing_2",.3f),Map.entry("gold_punch_swing_3",.38f),Map.entry("gold_punch_swing_4",.5f),
        Map.entry("gold_punch_hit_1",.35f),Map.entry("gold_punch_hit_2",.4f),Map.entry("gold_punch_hit_3",.42f),Map.entry("gold_punch_hit_4",.8f));
    /** Every sound event and its files. */
    static final Map<String,String[]> EVENTS=Map.ofEntries(
        Map.entry("gold_experience_overdrive",new String[]{"gold_experience_overdrive"}),Map.entry("gold_experience_mend",new String[]{"gold_experience_mend"}),
        Map.entry("gold_experience_self_heal",new String[]{"gold_experience_self_heal"}),
        Map.entry("gold_life_gather",new String[]{"gold_life_gather"}),Map.entry("gold_life_born",new String[]{"gold_life_born"}),Map.entry("gold_life_hop",new String[]{"gold_life_hop"}),
        Map.entry("gold_life_croak",new String[]{"gold_life_croak_1","gold_life_croak_2","gold_life_croak_3"}),
        Map.entry("gold_life_reflect",new String[]{"gold_life_reflect"}),Map.entry("gold_life_frog_back",new String[]{"gold_life_frog_back"}),
        Map.entry("gold_life_frog_burst",new String[]{"gold_life_frog_burst"}),Map.entry("gold_life_punch",new String[]{"gold_life_punch"}),
        Map.entry("gold_life_strike",new String[]{"gold_life_strike"}),Map.entry("gold_life_fly_born",new String[]{"gold_life_fly_born"}),
        Map.entry("gold_life_fly_buzz",new String[]{"gold_life_fly_buzz"}),Map.entry("gold_life_fly_back",new String[]{"gold_life_fly_back"}),
        Map.entry("gold_life_swat",new String[]{"gold_life_swat"}),Map.entry("gold_life_fall",new String[]{"gold_life_fall"}),
        Map.entry("gold_tree_touch",new String[]{"gold_tree_touch"}),Map.entry("gold_tree_burst",new String[]{"gold_tree_burst"}),
        Map.entry("gold_tree_knock",new String[]{"gold_tree_knock_1","gold_tree_knock_2","gold_tree_knock_3"}),Map.entry("gold_tree_wither",new String[]{"gold_tree_wither"}),
        Map.entry("gold_punch_swing_1",new String[]{"gold_punch_swing_1"}),Map.entry("gold_punch_swing_2",new String[]{"gold_punch_swing_2"}),
        Map.entry("gold_punch_swing_3",new String[]{"gold_punch_swing_3"}),Map.entry("gold_punch_swing_4",new String[]{"gold_punch_swing_4"}),
        Map.entry("gold_punch_hit_1",new String[]{"gold_punch_hit_1"}),Map.entry("gold_punch_hit_2",new String[]{"gold_punch_hit_2"}),
        Map.entry("gold_punch_hit_3",new String[]{"gold_punch_hit_3"}),Map.entry("gold_punch_hit_4",new String[]{"gold_punch_hit_4"}));

    @Test void everyOneIsTheModsMonoAt48kHzAndAsLongAsItsToolMakesIt()throws Exception{
        JsonObject json=JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        for(var e:EVENTS.entrySet()){
            assertTrue(json.has(e.getKey()),e.getKey()+" in sounds.json");
            var list=json.getAsJsonObject(e.getKey()).getAsJsonArray("sounds");
            assertEquals(e.getValue().length,list.size(),e.getKey()+": its files");
            for(int i=0;i<list.size();i++)assertEquals("celestialfall:"+e.getValue()[i],list.get(i).getAsJsonObject().get("name").getAsString());
        }
        for(var f:FILES.entrySet()){
            var path=ASSETS.resolve("sounds").resolve(f.getKey()+".ogg");
            assertTrue(Files.exists(path),f.getKey()+".ogg");
            byte[] b=Files.readAllBytes(path);int id=header(b);
            assertTrue(id>=0,f.getKey()+" is Ogg Vorbis");
            assertEquals(1,b[id+11],f.getKey()+" mono");
            assertEquals(48000,(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24,f.getKey()+" at 48 kHz");
            assertEquals(f.getValue(),seconds(b),.02f,f.getKey()+" as long as its tool makes it");
        }
        // the buzz is heard in the world, near the fly (12 blocks), and loops
        var buzz=json.getAsJsonObject("gold_life_fly_buzz").getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertEquals(12,buzz.get("attenuation_distance").getAsInt());
    }
    @Test void theFrogsSoundsComeOnItsMoments(){
        // the light gathering swells into the item's turning (its peak .25 s into the file)
        assertEquals(GoldLife.BORN,GoldLife.GATHER_SOUND+.25f,1e-4);
        var c=GoldLife.FROG_SOUNDS;
        assertEquals("gold_life_gather",c[0].sound());assertEquals(GoldLife.GATHER_SOUND,c[0].at(),1e-6);
        // the pop as it turns, its croak (.13 s into the file) as the frog has grown out of it
        assertEquals("gold_life_born",c[1].sound());assertEquals(GoldLife.BORN,c[1].at(),1e-6);
        assertEquals(GoldLife.MORPHED,c[1].at()+.13f,.02f);
        // the hop up, its pat (.30 s into the file) as it sits on the shoulder
        assertEquals("gold_life_hop",c[2].sound());assertEquals(GoldLife.HOP_FROM,c[2].at(),1e-6);assertEquals(GoldLife.HOP_TO,c[2].at()+.30f,1e-4);
        // the hop back, the item's pop (.31 s into the file) as it turns back
        assertEquals("gold_life_frog_back",c[3].sound());assertEquals(GoldLife.HOP_BACK,c[3].at(),1e-6);assertEquals(GoldLife.UNMORPH,c[3].at()+.31f,1e-4);
        assertTrue(c[3].at()<GoldExperienceRules.FROG_END/20f,"before the item is given back");
        // a barrage's blows croak now and then
        assertTrue(GoldLife.CROAKS>=.15f&&GoldLife.CROAKS<=.3f);
    }
    @Test void thePunchsRushIsLoudestAsTheFistLands(){
        // the rush through the air: its loudest moment .13 s into the file
        assertEquals(GoldLife.CONTACT,GoldLife.PUNCH_SOUNDS[0].at()+.13f,.03f);
        assertEquals("gold_life_punch",GoldLife.PUNCH_SOUNDS[0].sound());
        assertTrue(GoldLife.PUNCH_SOUNDS[0].at()>=GoldLife.WOUND,"as it is thrown");
    }
    @Test void theFlyBuzzesFromBecomingTheFlyUntilItGoesBack(){
        assertEquals(GoldExperienceRules.FLY_FORMS,GoldFlySounds.FORMS);
        assertEquals(GoldExperienceRules.FLY_FORMS+GoldExperienceRules.FLY_LASTS,GoldFlySounds.BACK);
        assertTrue(GoldFlySounds.BUZZ<=.8f,"under the blows' sounds");
    }
    @Test void theTreesSoundsComeOnItsMoments(){
        var c=GoldTree.SOUNDS;
        // the touch's pat (.25 s into the file) as the palm is laid on the ground, its light swelling to the burst (.40 s)
        assertEquals("gold_tree_touch",c[0].sound());
        assertEquals(GoldTree.TOUCH,c[0].at()+.25f,1e-4);assertEquals(GoldTree.BURST,c[0].at()+.40f,1e-4);
        assertEquals("gold_tree_burst",c[1].sound());assertEquals(GoldTree.BURST,c[1].at(),1e-6);
        // the withering as it begins to wither, over by the tree's end
        assertEquals("gold_tree_wither",c[2].sound());assertEquals(GoldExperienceRules.TREE_WITHER/20f,c[2].at(),1e-6);
        assertTrue(c[2].at()+FILES.get("gold_tree_wither")<=GoldExperienceRules.TREE_END/20f+.6f,"about over as the tree is gone");
        for(var cue:c)assertEquals(0,Math.round(cue.at()*20)-cue.at()*20,1e-4,"on a tick: "+cue.at());
    }
}
