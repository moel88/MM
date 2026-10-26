package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.client.stand.StandArrowSounds.Cue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.stand.ArrowStabPose.*;
import static dev.sangwoo.celestialfall.client.stand.StandArrowSounds.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The stand arrow's sounds (2.71.0): recordings laid on the stab's clock where the body does what they are the sound
 * of; every one in the mod's sounds with its file there; on whole ticks; the blow on HIT, the heart on BEATS, the knees
 * on the knees, the body on the ground as it lands; nothing of the stab's own sounding into the stillness.
 */
class StandArrowSoundsTest {
    static final Path ASSETS=Path.of("src/main/resources/assets/celestialfall");
    static JsonObject sounds()throws Exception{return JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();}
    static boolean onTick(float t){return Math.abs(t*20-Math.round(t*20))<1e-3f;}
    static boolean at(int track,String sound,float t){
        for(var c:CUES)if(c.track()==track&&c.sound().equals(sound)&&Math.abs(c.at()-t)<.026f)return true;
        return false;
    }

    @Test void everySoundIsTheModsAndItsFilesAreThere()throws Exception{
        var json=sounds();Set<String> used=new HashSet<>();
        for(var c:CUES){
            if(c.sound().equals(GROUND))continue;
            assertTrue(json.has(c.sound()),c.sound()+" in sounds.json");used.add(c.sound());
        }
        for(String name:used)for(var e:json.getAsJsonObject(name).getAsJsonArray("sounds")){
            String file=e.isJsonObject()?e.getAsJsonObject().get("name").getAsString():e.getAsString();
            if(!file.startsWith("celestialfall:"))continue;
            Path ogg=ASSETS.resolve("sounds").resolve(file.substring("celestialfall:".length())+".ogg");
            assertTrue(Files.exists(ogg),ogg+" for "+name);
        }
        assertFalse(json.has("stand_arrow_stab_old"),"(the made-up sounds of 2.70.0 are gone)");
    }
    @Test void theCuesAreOnWholeTicksInOrder(){
        for(int track=STAB;track<=REJECTED;track++){
            float prev=-1;
            for(Cue c:track(track)){
                assertTrue(onTick(c.at()),c+" on a whole tick");
                assertTrue(c.at()>=prev,c+" in order");prev=c.at();
                assertTrue(c.volume()>0&&c.volume()<=1.5f&&c.pitch()>.4f&&c.pitch()<2.1f,c+" a sane volume and pitch");
                if(!Float.isNaN(c.until()))assertTrue(c.until()>c.at()&&onTick(c.until()),c+" hushed after it starts, on a tick");
            }
        }
        assertFalse(track(STAB).isEmpty());assertFalse(track(CHOSEN).isEmpty());assertFalse(track(REJECTED).isEmpty());
    }
    @Test void theyGoWithTheBody(){
        assertTrue(at(STAB,"stand_arrow_stab",HIT),"the blow as it goes in");
        for(float b:BEATS)assertTrue(at(STAB,"stand_arrow_heart",Math.round(b*20)/20f),"a heartbeat at "+b);
        assertTrue(at(STAB,GROUND,KNEE)&&at(STAB,GROUND,KNEES),"each knee on the ground it comes down on");
        assertTrue(at(REJECTED,GROUND,Math.round(LANDS_AT*20)/20f),"the body on the ground as it lands");
        // the stab's own sounds hushed (or over) by the stillness: nothing of them carries into it
        for(Cue c:track(STAB))if(c.at()<STILL)assertTrue(!Float.isNaN(c.until())&&c.until()<=STILL+.051f||c.at()+3<STILL||c.sound().equals(GROUND)||c.sound().equals("stand_arrow_heart"),c+" hushed by the stillness");
        for(Cue c:track(STAB))assertTrue(c.at()<STILL,c+" before the stillness (its choice's own sounds after it)");
    }
}
