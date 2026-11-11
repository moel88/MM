package dev.sangwoo.celestialfall.client.xchara;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What X Chara's hack is made of besides its code (2.115.0, their Q): its two sounds are the mod's (in sounds.json,
 * their files there, mono at 48 kHz, the lengths tools/xchara/make_q_sounds.py makes them: each over within a second);
 * the panel is an item of its own with a name in both languages and a model the game draws through the mod's renderer.
 */
class HackSoundsTest {
    static final Path ASSETS=Path.of("src/main/resources/assets/celestialfall");

    @Test void itsTwoSoundsAreTheModsMonoAndAsLongAsTheyWereMade()throws Exception{
        JsonObject json=JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        String[] names={"xchara_q_on","xchara_q_off"};float[] lengths={.62f,.44f};
        for(int i=0;i<names.length;i++){
            String name=names[i];
            assertTrue(json.has(name),name+" in sounds.json");
            assertEquals("celestialfall:"+name,json.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
            assertTrue(Files.exists(KnifeSoundsTest.ogg(name)),name+".ogg");
            byte[] b=Files.readAllBytes(KnifeSoundsTest.ogg(name));int id=KnifeSoundsTest.header(b);
            assertTrue(id>=0,name+" is Ogg Vorbis");
            assertEquals(1,b[id+11],name+" mono");
            assertEquals(48000,(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24,name+" at 48 kHz");
            assertEquals(lengths[i],KnifeSoundsTest.seconds(b),.02f,name+" as long as make_q_sounds.py makes it");
        }
    }
    @Test void thePanelIsAnItemOfItsOwn()throws Exception{
        for(String lang:new String[]{"ko_kr","en_us"}){
            JsonObject names=JsonParser.parseString(Files.readString(ASSETS.resolve("lang/"+lang+".json"))).getAsJsonObject();
            assertTrue(names.has("item.celestialfall.x_chara_panel"),"its name in "+lang);
        }
        JsonObject model=JsonParser.parseString(Files.readString(ASSETS.resolve("models/item/x_chara_panel.json"))).getAsJsonObject();
        assertEquals("builtin/entity",model.get("parent").getAsString(),"drawn by the mod's renderer (HackPanel)");
        // no display of its own: HackPanel puts it in the hand (a display would turn it once more)
        assertFalse(model.has("display"));
    }
}
