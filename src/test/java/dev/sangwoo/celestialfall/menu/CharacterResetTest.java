package dev.sangwoo.celestialfall.menu;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** "캐릭터 재설정하기" (2.45.0): one reset a second, a damage type nothing stops, and a death message in both languages. */
class CharacterResetTest {
    @Test void aSecondResetWithinASecondIsDropped(){
        assertFalse(CharacterReset.Gate.tooSoon(null,100),"the first reset goes through");
        assertTrue(CharacterReset.Gate.tooSoon(100,100));
        assertTrue(CharacterReset.Gate.tooSoon(100,119));
        assertFalse(CharacterReset.Gate.tooSoon(100,120),"a second later it goes through");
        assertFalse(CharacterReset.Gate.tooSoon(100,40),"a server restart's smaller tick count never blocks it");
    }
    @Test void theDamageNothingStopsAndItsDeathMessage()throws Exception{
        var type=json("/data/celestialfall/damage_type/reset.json");
        assertEquals("celestialfall.reset",type.get("message_id").getAsString());
        assertEquals("never",type.get("scaling").getAsString());
        assertEquals(0,type.get("exhaustion").getAsDouble());
        for(String tag:new String[]{"bypasses_armor","bypasses_cooldown","bypasses_effects","bypasses_enchantments","bypasses_invulnerability","bypasses_resistance","bypasses_shield"}){
            var t=json("/data/minecraft/tags/damage_type/"+tag+".json");
            assertFalse(t.get("replace").getAsBoolean(),tag);
            assertTrue(t.get("values").toString().contains("celestialfall:reset"),tag);
        }
        for(String lang:new String[]{"ko_kr","en_us"}){
            var l=json("/assets/celestialfall/lang/"+lang+".json");
            assertTrue(l.get("death.attack.celestialfall.reset").getAsString().contains("%1$s"),lang);
            var escape=l.get("death.attack.celestialfall.reset.player").getAsString();
            assertTrue(escape.contains("%1$s")&&escape.contains("%2$s"),lang);
        }
    }
    private static com.google.gson.JsonObject json(String path)throws Exception{
        try(var in=CharacterResetTest.class.getResourceAsStream(path)){
            assertNotNull(in,path);
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
