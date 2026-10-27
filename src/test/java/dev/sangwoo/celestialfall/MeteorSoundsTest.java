package dev.sangwoo.celestialfall;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.meteor.MeteorRules;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MeteorSoundsTest {
    private JsonObject json(String path)throws Exception{
        try(var in=getClass().getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    private byte[] ogg(String name)throws Exception{
        try(var in=getClass().getResourceAsStream("/assets/celestialfall/sounds/"+name+".ogg")){assertNotNull(in,name);return in.readAllBytes();}
    }
    private static long le(byte[] b,int at,int n){long v=0;for(int i=n-1;i>=0;i--)v=v<<8|(b[at+i]&0xff);return v;}
    /** Channels, sample rate and seconds of an OGG Vorbis file: its identification header, then the last page's sample count. */
    private static double[] vorbis(byte[] b){
        int page=0;long samples=-1;double channels=0,rate=0;
        while(page<b.length){
            assertEquals("OggS",new String(b,page,4,StandardCharsets.US_ASCII),"an Ogg page at "+page);
            int segments=b[page+26]&0xff,body=0;
            for(int i=0;i<segments;i++)body+=b[page+27+i]&0xff;
            int data=page+27+segments;
            if(page==0){
                assertEquals("vorbis",new String(b,data+1,6,StandardCharsets.US_ASCII));
                channels=b[data+11]&0xff;rate=le(b,data+12,4);
            }
            samples=le(b,page+6,8);page=data+body;
        }
        return new double[]{channels,rate,samples/rate};
    }

    @Test void everyMeteorSoundIsMonoSoItFadesWithDistanceAndHasSubtitles()throws Exception{
        var sounds=json("/assets/celestialfall/sounds.json");var en=json("/assets/celestialfall/lang/en_us.json");var ko=json("/assets/celestialfall/lang/ko_kr.json");
        int count=0;
        for(var e:sounds.entrySet()){
            if(!e.getKey().startsWith("meteor_"))continue;
            count++;var def=e.getValue().getAsJsonObject();
            String subtitle=def.get("subtitle").getAsString();
            assertTrue(en.has(subtitle)&&ko.has(subtitle),"subtitle of "+e.getKey());
            for(var s:def.getAsJsonArray("sounds")){
                String name=s.getAsJsonObject().get("name").getAsString().replace("celestialfall:","");
                // a stereo sound is heard the same from everywhere: the game places only a mono one
                assertEquals(1,vorbis(ogg(name))[0],name+" is not mono");
            }
        }
        assertEquals(6,count,"fall, landing, hit, fragment, crumble, craft");
    }
    @Test void theFallSoundStartsWithTheFallAndFadesUnderTheLanding()throws Exception{
        var v=vorbis(ogg("meteor_fall"));
        assertEquals(48000,v[1]);
        // the recording's loud roar ends as the rock lands (3.6 s) and its fade plays on under the landing
        assertTrue(v[2]>MeteorRules.FALL_TICKS/20.0+.5&&v[2]<6,"the fall sound lasts "+v[2]+" s");
        // heard (faintly) from the place it lands even where the lowest fall starts in the sky
        double far=MeteorRules.FALL_HEIGHT/Math.sin(Math.toRadians(MeteorRules.FALL_ANGLE_MIN));
        var def=json("/assets/celestialfall/sounds.json").getAsJsonObject("meteor_fall").getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertTrue(def.get("attenuation_distance").getAsDouble()>far,"a fall starts "+far+" blocks off");
    }
}
