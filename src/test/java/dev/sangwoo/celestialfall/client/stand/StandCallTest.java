package dev.sangwoo.celestialfall.client.stand;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.stand.StandArrowSoundsTest.ogg;
import static dev.sangwoo.celestialfall.client.stand.StandArrowSoundsTest.seconds;
import static dev.sangwoo.celestialfall.client.stand.StandArrowSoundsTest.sounds;
import static dev.sangwoo.celestialfall.client.stand.StandCall.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum called out and back (2.72.0): the call as it comes out and the sound of it going as it goes back, only
 * when its user called it so (Q), never when it comes out or goes back otherwise; the two cut recordings in the mod's
 * sounds, mono, the lengths tools/stand/cut_summon.py cut them to.
 */
class StandCallTest {
    @Test void onlyACallIsHeard(){
        assertEquals(SUMMON,cue(true,true,true),"called out: the call");
        assertEquals(DISMISS,cue(false,false,true),"called back: the sound of it going");
        assertNull(cue(true,true,false),"out otherwise (the arrow's awakening): silent");
        assertNull(cue(false,false,false),"back otherwise (its user dead, asleep, off the map, another stand): silent");
        // this client's own reasons: its user asleep or off the map while the server still has it out (called)
        assertNull(cue(false,true,true));
        assertNull(cue(true,false,true));
    }
    @Test void theRecordingsAreTheMods()throws Exception{
        var json=sounds();
        for(String name:new String[]{SUMMON,DISMISS}){
            assertTrue(json.has(name),name+" in sounds.json");
            assertTrue(Files.exists(ogg(name)),name+".ogg");
            byte[] b=Files.readAllBytes(ogg(name));
            int id=-1;
            for(int i=0;i+16<b.length&&id<0;i++)if(b[i]==1&&b[i+1]=='v'&&b[i+2]=='o'&&b[i+3]=='r'&&b[i+4]=='b'&&b[i+5]=='i'&&b[i+6]=='s')id=i;
            assertEquals(1,b[id+11],name+" mono");
            int rate=(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24;
            assertEquals(48000,rate,name+" at 48 kHz");
        }
        assertEquals(1.435f,seconds(ogg(SUMMON)),.01f,"the call, \"Star Platinum!\", and its echo (cut_summon.py)");
        assertEquals(1.247f,seconds(ogg(DISMISS)),.01f,"the stand going, as the recording ends (cut_summon.py)");
    }
}
