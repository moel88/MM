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
 * on the knees, the body on the ground as it lands; nothing of the stab's own sounding into the stillness. 2.71.1: no
 * voice at all, and the glow swelling from the second knee to end as everything goes still.
 */
class StandArrowSoundsTest {
    static final Path ASSETS=Path.of("src/main/resources/assets/celestialfall");
    static JsonObject sounds()throws Exception{return JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();}
    static boolean onTick(float t){return Math.abs(t*20-Math.round(t*20))<1e-3f;}
    static boolean ground(Cue c){return c.sound().equals(GROUND)||c.sound().equals(STEP);}
    static boolean at(int track,String sound,float t){
        for(var c:CUES)if(c.track()==track&&c.sound().equals(sound)&&Math.abs(c.at()-t)<.026f)return true;
        return false;
    }
    static Path ogg(String sound){return ASSETS.resolve("sounds").resolve(sound+".ogg");}
    /** How long an Ogg Vorbis file plays (s): its last page's granule position (samples) over its rate. */
    static float seconds(Path ogg)throws Exception{
        byte[] b=Files.readAllBytes(ogg);
        int id=-1;
        for(int i=0;i+16<b.length&&id<0;i++)if(b[i]==1&&b[i+1]=='v'&&b[i+2]=='o'&&b[i+3]=='r'&&b[i+4]=='b'&&b[i+5]=='i'&&b[i+6]=='s')id=i;
        int rate=(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24;
        int last=-1;
        for(int i=b.length-27;i>=0&&last<0;i--)if(b[i]=='O'&&b[i+1]=='g'&&b[i+2]=='g'&&b[i+3]=='S')last=i;
        long granule=0;
        for(int k=7;k>=0;k--)granule=granule<<8|(b[last+6+k]&255);
        return granule/(float)rate;
    }

    @Test void everySoundIsTheModsAndItsFilesAreThere()throws Exception{
        var json=sounds();Set<String> used=new HashSet<>();
        for(var c:CUES){
            if(ground(c))continue;
            assertTrue(json.has(c.sound()),c.sound()+" in sounds.json");used.add(c.sound());
        }
        for(String name:used)for(var e:json.getAsJsonObject(name).getAsJsonArray("sounds")){
            String file=e.isJsonObject()?e.getAsJsonObject().get("name").getAsString():e.getAsString();
            assertTrue(file.startsWith("celestialfall:"),file);
            Path ogg=ASSETS.resolve("sounds").resolve(file.substring("celestialfall:".length())+".ogg");
            assertTrue(Files.exists(ogg),ogg+" for "+name);
        }
        // every stand arrow sound in sounds.json is one the cue sheet plays (none left over from 2.70.0)
        for(String name:json.keySet())if(name.startsWith("stand_arrow_"))assertTrue(used.contains(name),name+" is played");
    }
    @Test void theCuesAreOnWholeTicksInOrder(){
        for(int track=STAB;track<=REJECTED;track++){
            float prev=-1;
            for(Cue c:track(track)){
                assertTrue(onTick(c.at()),c+" on a whole tick");
                assertTrue(c.at()>=prev,c+" in order");prev=c.at();
                assertTrue(c.volume()>0&&c.volume()<=1&&c.pitch()>=.5f&&c.pitch()<=2,c+" a volume and pitch the game plays");
                if(!Float.isNaN(c.until()))assertTrue(c.until()>c.at()&&onTick(c.until()),c+" hushed after it starts, on a tick");
            }
        }
        assertFalse(track(STAB).isEmpty());assertFalse(track(CHOSEN).isEmpty());assertFalse(track(REJECTED).isEmpty());
    }
    @Test void theyGoWithTheBody(){
        assertTrue(at(STAB,"stand_arrow_stab",HIT),"the blow as it goes in");
        for(float b:BEATS)assertTrue(at(STAB,"stand_arrow_heart",Math.round(b*20)/20f),"a heartbeat at "+b);
        assertTrue(at(STAB,"stand_arrow_knee",KNEE)&&at(STAB,GROUND,KNEE)&&at(STAB,"stand_arrow_knee_2",KNEES)&&at(STAB,GROUND,KNEES),
            "each knee on the ground it comes down on");
        assertTrue(at(REJECTED,"stand_arrow_bodyfall",Math.round(LANDS_AT*20)/20f)&&at(REJECTED,GROUND,Math.round(LANDS_AT*20)/20f),
            "the body on the ground as it lands");
        assertTrue(at(CHOSEN,"stand_arrow_awaken",0)&&at(REJECTED,"stand_arrow_heart",0),"each choice as it shows");
        assertTrue(at(STAB,"stand_arrow_glow",KNEES),"the light drawn in from when both knees are down");
    }
    @Test void noVoice(){
        // (2.71.1, user request: "목소리나 한숨은 다 빼고": none of 2.71.0's breaths, sighs, gasps or the choke)
        for(var c:CUES)for(String voice:new String[]{"sigh","breath","nervous","catch","gasp","relief","choke"})
            assertFalse(c.sound().contains(voice),c+" is no voice");
    }
    @Test void theGlowEndsAsEverythingGoesStill()throws Exception{
        // it swells to its end (its rings' strikes, backwards) and that end comes as the stillness does: not before it by
        // more than a tick, not into it
        for(var c:track(STAB))if(c.sound().equals("stand_arrow_glow")){
            float end=c.at()+seconds(ogg(c.sound()))/c.pitch();
            assertTrue(end>STILL-.06f&&end<=STILL+.001f,"the glow ends at "+end+", the stillness at "+STILL);
        }
    }
    @Test void nothingOfTheStabSoundsIntoTheStillness()throws Exception{
        for(Cue c:track(STAB)){
            assertTrue(c.at()<STILL,c+" before the stillness (its choice's own sounds after it)");
            // over by then (as long as its file plays at its pitch; the ground's own are short), or hushed as it comes
            float end=c.at()+(ground(c)?1:seconds(ogg(c.sound()))/c.pitch());
            assertTrue(end<=STILL+.051f||!Float.isNaN(c.until())&&c.until()<=STILL+.1f,c+" over or hushed by the stillness (it would end at "+end+")");
        }
        // the rumble under it all is still sounding when it is cut (it swells into the stillness, not out of it)
        for(Cue c:track(STAB))if(c.sound().equals("stand_arrow_rumble"))assertTrue(c.at()+seconds(ogg(c.sound()))>c.until(),"the rumble cut, not over");
    }
    @Test void theSoundsAreWhatTheCuesExpect()throws Exception{
        // the cut recordings: mono (the game only fades mono with distance; these are not placed in the world, but the
        // same as every other sound of the mod), and none longer than its place on the sheet needs
        for(String name:sounds().keySet()){
            if(!name.startsWith("stand_arrow_"))continue;
            byte[] b=Files.readAllBytes(ogg(name));
            int id=-1;
            for(int i=0;i+16<b.length&&id<0;i++)if(b[i]==1&&b[i+1]=='v'&&b[i+2]=='o'&&b[i+3]=='r'&&b[i+4]=='b'&&b[i+5]=='i'&&b[i+6]=='s')id=i;
            assertEquals(1,b[id+11],name+" mono");
            assertTrue(seconds(ogg(name))<6,name+" short");
        }
        assertEquals(2.58f,seconds(ogg("stand_arrow_stab")),.02f,"the blow, ringing on (cut_arrow_sounds.py)");
        assertEquals(1.35f,seconds(ogg("stand_arrow_glow")),.02f,"the glow (cut_arrow_sounds.py)");
        assertEquals(4.82f,seconds(ogg("stand_arrow_rumble")),.02f,"the rumble (cut_arrow_sounds.py)");
    }
}
