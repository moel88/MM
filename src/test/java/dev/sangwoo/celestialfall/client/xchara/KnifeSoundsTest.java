package dev.sangwoo.celestialfall.client.xchara;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The sound of X Chara's basic attack (2.118.0: the user's own, taken out of their video from under its music,
 * tools/xchara/extract_slash.py; "이걸 보탠드 엑스 차라 평타 소리로 옮겨 Q활성화 비활성화 전부 똑같게"): one sound for every
 * swing with the hack on or off, the mod's (in sounds.json, its file there, mono at 48 kHz, as long as the tool makes
 * it); it starts as the knife is let go, a little before the blow, so the blow lands in its hiss and its tail falls
 * through the follow-through, over before the swing is drawn no more; nothing is left of the sounds made up before it.
 */
class KnifeSoundsTest {
    static final Path ASSETS=Path.of("src/main/resources/assets/celestialfall");
    static Path ogg(String sound){return ASSETS.resolve("sounds").resolve(sound+".ogg");}
    static int header(byte[] b){
        for(int i=0;i+16<b.length;i++)if(b[i]==1&&b[i+1]=='v'&&b[i+2]=='o'&&b[i+3]=='r'&&b[i+4]=='b'&&b[i+5]=='i'&&b[i+6]=='s')return i;
        return -1;
    }
    /** How long an Ogg Vorbis file plays (s): its last page's granule position (samples) over its rate. */
    static float seconds(byte[] b){
        int id=header(b),rate=(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24,last=-1;
        for(int i=b.length-27;i>=0&&last<0;i--)if(b[i]=='O'&&b[i+1]=='g'&&b[i+2]=='g'&&b[i+3]=='S')last=i;
        long granule=0;for(int k=7;k>=0;k--)granule=granule<<8|(b[last+6+k]&255);
        return granule/(float)rate;
    }

    @Test void theOneSoundIsTheModsMonoAndAsLongAsItWasMade()throws Exception{
        JsonObject json=JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        String name=KnifeClient.SOUND;
        assertEquals("xchara_slash",name);
        assertTrue(json.has(name),name+" in sounds.json");
        assertEquals("celestialfall:"+name,json.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(Files.exists(ogg(name)),name+".ogg");
        byte[] b=Files.readAllBytes(ogg(name));int id=header(b);
        assertTrue(id>=0,name+" is Ogg Vorbis");
        assertEquals(1,b[id+11],name+" mono");
        assertEquals(48000,(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24,name+" at 48 kHz");
        assertEquals(SlashFx.SOUND,seconds(b),.02f,name+" as long as extract_slash.py makes it");
    }
    @Test void nothingIsLeftOfTheSoundsMadeUpBeforeIt()throws Exception{
        JsonObject json=JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        for(String key:json.keySet())assertFalse(key.startsWith("xchara_m1_")||key.startsWith("xchara_swing_"),key+" in sounds.json");
        try(var files=Files.list(ASSETS.resolve("sounds"))){
            files.forEach(f->{String n=f.getFileName().toString();assertFalse(n.startsWith("xchara_m1_")||n.startsWith("xchara_swing_"),n);});
        }
    }
    @Test void itStartsAsTheKnifeIsLetGoAndTheBlowLandsInItsHiss(){
        assertEquals(40,KnifeClient.CARRY,1e-9,"as far as the other characters' sounds carry");
        assertEquals(2,KnifeClient.SOUND_BEFORE,"two ticks before the blow");
        for(int hit=HACK_SLASH;hit<=LUNGE;hit++){
            int at=KnifeClient.soundAt(hit);
            assertEquals(blowAt(hit,0)-KnifeClient.SOUND_BEFORE,at,"hit "+hit);
            assertTrue(at>=0&&at<blowAt(hit,0));
            // the same for the swing in its place of the other kind
            assertEquals(at,KnifeClient.soundAt(hit(place(hit),!plain(hit))));
            float start=at/20f,blow=blowAt(hit,0)/20f;
            // its hiss (full from 0.03 s to about 0.25 s of it) has begun as the knife is let go and holds through the blow
            assertTrue(start+.03f<=SlashFx.FROM[place(hit)]+.02f,"hit "+hit+": heard as the knife is let go");
            assertTrue(blow>=start+.03f&&blow<=start+.2f,"hit "+hit+": the blow lands in its hiss");
            // and it is over before the swing is drawn no more, and as the next hit's blow comes at the soonest
            assertTrue(start+SlashFx.SOUND<KnifeCombo.LIFE);
            assertTrue(start+SlashFx.SOUND<=LENGTH[hit]/20f+.2f,"hit "+hit+": over about as its motion is");
        }
    }
}
