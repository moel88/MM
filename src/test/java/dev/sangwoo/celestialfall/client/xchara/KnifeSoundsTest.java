package dev.sangwoo.celestialfall.client.xchara;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The sounds of X Chara's basic attack (2.114.0, made for it: tools/xchara/make_m1_sounds.py): each hit's own and a
 * blow's two are the mod's (in sounds.json, their files there, mono at 48 kHz, the lengths the script makes them); a
 * hit's is over before its swing is; it starts a tick into its hit (the files start there); and a blow's sound rises a
 * little with every blow of the combo, the finisher's first cut low. And the plain swings' (2.117.0, the hack off: a
 * knife through the air and a cut, nothing of the hacker's): each swing's own and a blow's two, the mod's and as long as
 * the script makes them, each chosen by its hit's kind and place.
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

    @Test void everySoundIsTheModsMonoAndAsLongAsItWasMade()throws Exception{
        JsonObject json=JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        String[] names={"xchara_m1_1","xchara_m1_2","xchara_m1_3","xchara_m1_4","xchara_m1_hit","xchara_m1_hit_last"};
        float[] lengths={.62f,.70f,.86f,1.22f,.30f,.72f};
        for(int i=0;i<names.length;i++){
            String name=names[i];
            assertTrue(json.has(name),name+" in sounds.json");
            assertEquals("celestialfall:"+name,json.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
            assertTrue(Files.exists(ogg(name)),name+".ogg");
            byte[] b=Files.readAllBytes(ogg(name));int id=header(b);
            assertTrue(id>=0,name+" is Ogg Vorbis");
            assertEquals(1,b[id+11],name+" mono");
            assertEquals(48000,(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24,name+" at 48 kHz");
            assertEquals(lengths[i],seconds(b),.02f,name+" as long as make_m1_sounds.py makes it");
        }
        // each hit's sound is over before its swing is drawn no more, and has its last blow in it
        for(int hit=CUT;hit<=DELETE;hit++){
            float length=seconds(Files.readAllBytes(ogg("xchara_m1_"+hit))),lead=KnifeClient.SOUND_LEAD/20f;
            assertTrue(lead+length<KnifeCombo.LIFE,"hit "+hit+"'s sound ends before its swing does");
            assertTrue(length>lastBlow(hit)/20f-lead+.1f,"hit "+hit+"'s last blow is in its sound");
        }
    }
    @Test void aHitsSoundStartsATickInAndABlowsRisesThroughTheCombo(){
        assertEquals(1,KnifeClient.SOUND_LEAD,"the files start a tick into their hits (make_m1_sounds.py's LEAD)");
        assertEquals(40,KnifeClient.CARRY,1e-9,"as far as the other characters' sounds carry");
        float before=0;
        for(int hit=CUT;hit<=FLOOD;hit++){
            assertEquals(blows(hit),KnifeClient.HIT_PITCH[hit].length);
            for(float pitch:KnifeClient.HIT_PITCH[hit]){assertTrue(pitch>before&&pitch>=.5f&&pitch<=2,"a little higher with every blow");before=pitch;}
        }
        assertEquals(blows(DELETE),KnifeClient.HIT_PITCH[DELETE].length);
        assertTrue(KnifeClient.HIT_PITCH[DELETE][0]<1,"the finisher's first cut low");
    }

    // ───────────────────────────── the plain four (2.117.0) ─────────────────────────────

    @Test void thePlainSwingsSoundsAreTheModsMonoAndAsLongAsTheyWereMade()throws Exception{
        JsonObject json=JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        String[] names={"xchara_swing_1","xchara_swing_2","xchara_swing_3","xchara_swing_4","xchara_swing_hit","xchara_swing_hit_last"};
        float[] lengths={.50f,.50f,.66f,.70f,.30f,.55f};
        for(int i=0;i<names.length;i++){
            String name=names[i];
            assertTrue(json.has(name),name+" in sounds.json");
            assertEquals("celestialfall:"+name,json.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
            assertTrue(Files.exists(ogg(name)),name+".ogg");
            byte[] b=Files.readAllBytes(ogg(name));int id=header(b);
            assertTrue(id>=0,name+" is Ogg Vorbis");
            assertEquals(1,b[id+11],name+" mono");
            assertEquals(48000,(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24,name+" at 48 kHz");
            assertEquals(lengths[i],seconds(b),.02f,name+" as long as make_m1_sounds.py makes it");
        }
        // each swing's sound is over before its swing is drawn no more, has its blow in it, and is shorter than the hack's hit's in its place
        for(int hit=SLASH;hit<=LUNGE;hit++){
            float length=seconds(Files.readAllBytes(ogg("xchara_swing_"+place(hit)))),lead=KnifeClient.SOUND_LEAD/20f;
            assertTrue(lead+length<KnifeCombo.LIFE,"hit "+hit+"'s sound ends before its swing does");
            assertTrue(length>lastBlow(hit)/20f-lead+.1f,"hit "+hit+"'s blow is in its sound");
            assertTrue(lead+length<=LENGTH[hit]/20f,"hit "+hit+"'s sound is over as its motion is");
            assertTrue(length<seconds(Files.readAllBytes(ogg("xchara_m1_"+place(hit)))),"a plain swing's sound is the shorter");
        }
    }
    @Test void aHitsSoundsAreItsKindsAndItsPlaces(){
        for(int place=1;place<=4;place++){
            assertEquals("xchara_m1_"+place,KnifeClient.swingSound(hit(place,false)));
            assertEquals("xchara_swing_"+place,KnifeClient.swingSound(hit(place,true)));
        }
        // a blow's: the kind's own, the one that throws them the heavy one
        for(int hit=CUT;hit<=LUNGE;hit++)for(int i=0;i<blows(hit);i++){
            String base=plain(hit)?"xchara_swing_hit":"xchara_m1_hit";
            assertEquals(launches(hit,i)?base+"_last":base,KnifeClient.blowSound(hit,i),"hit "+hit+" blow "+i);
        }
        assertEquals("xchara_swing_hit_last",KnifeClient.blowSound(LUNGE,0));assertEquals("xchara_m1_hit_last",KnifeClient.blowSound(DELETE,1));
        // every plain blow has its pitch: the two cuts a little apart, the chop lower (the heavier)
        for(int hit=SLASH;hit<=LUNGE;hit++){
            assertEquals(1,KnifeClient.HIT_PITCH[hit].length);
            assertTrue(KnifeClient.HIT_PITCH[hit][0]>=.5f&&KnifeClient.HIT_PITCH[hit][0]<=2);
        }
        assertTrue(KnifeClient.HIT_PITCH[RISE][0]>KnifeClient.HIT_PITCH[SLASH][0]&&KnifeClient.HIT_PITCH[CHOP][0]<KnifeClient.HIT_PITCH[SLASH][0]);
    }
}
