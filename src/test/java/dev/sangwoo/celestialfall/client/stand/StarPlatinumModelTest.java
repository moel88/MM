package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.StarPlatinumStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Star Platinum's model files (tools/stand/make_star_platinum.py) and its motion (2.50.0). */
class StarPlatinumModelTest {
    private JsonObject json(String path)throws Exception{
        try(var in=getClass().getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    private JsonObject geometry()throws Exception{return json("/assets/celestialfall/geo/entity/star_platinum.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();}
    private Map<String,JsonObject> bones()throws Exception{
        var map=new HashMap<String,JsonObject>();for(var b:geometry().getAsJsonArray("bones"))map.put(b.getAsJsonObject().get("name").getAsString(),b.getAsJsonObject());return map;
    }

    @Test void everyFaceIsPaintedInsideTheTexture()throws Exception{
        var g=geometry();var d=g.getAsJsonObject("description");
        java.awt.image.BufferedImage tex;
        try(var in=getClass().getResourceAsStream("/assets/celestialfall/textures/entity/star_platinum.png")){assertNotNull(in);tex=ImageIO.read(in);}
        assertEquals(tex.getWidth(),d.get("texture_width").getAsInt());assertEquals(tex.getHeight(),d.get("texture_height").getAsInt());
        int faces=0;
        for(var b:g.getAsJsonArray("bones"))if(b.getAsJsonObject().has("cubes"))for(var c:b.getAsJsonObject().getAsJsonArray("cubes"))
            for(var f:c.getAsJsonObject().getAsJsonObject("uv").entrySet()){
                var uv=f.getValue().getAsJsonObject().getAsJsonArray("uv");var size=f.getValue().getAsJsonObject().getAsJsonArray("uv_size");
                int x=uv.get(0).getAsInt(),y=uv.get(1).getAsInt(),w=size.get(0).getAsInt(),h=size.get(1).getAsInt();
                assertTrue(x>=0&&y>=0&&w>0&&h>0&&x+w<=tex.getWidth()&&y+h<=tex.getHeight(),"a face's texture runs off the atlas");
                faces++;
            }
        assertTrue(faces>1000,"faces "+faces);
    }
    @Test void everyBoneHangsFromTheRigAndTheStancesNameOnlyItsBones()throws Exception{
        var bones=bones();
        for(String name:new String[]{"root","hips","chest","head","right_arm","left_arm","right_forearm","left_hand","right_leg","left_foot","scarf_tail_2","cloth_front_2"})
            assertTrue(bones.containsKey(name),name);
        for(var b:bones.values())if(b.has("parent"))assertTrue(bones.containsKey(b.get("parent").getAsString()),b.get("name")+"'s parent");
        var poses=json("/assets/celestialfall/stands/star_platinum_poses.json");
        assertEquals(new java.util.TreeSet<>(StarPlatinumStance.NAMES),new java.util.TreeSet<>(poses.keySet()));
        for(var stance:poses.entrySet())for(var bone:stance.getValue().getAsJsonObject().keySet())assertTrue(bones.containsKey(bone),stance.getKey()+" turns "+bone);
    }
    @Test void itStandsOnItsSoles()throws Exception{
        // the feet hang from bones that do not turn at rest: their cubes' lowest faces are the model's floor
        double low=1e9;
        for(var b:geometry().getAsJsonArray("bones"))if(!b.getAsJsonObject().has("rotation")&&b.getAsJsonObject().has("cubes"))
            for(var c:b.getAsJsonObject().getAsJsonArray("cubes"))low=Math.min(low,c.getAsJsonObject().getAsJsonArray("origin").get(1).getAsDouble());
        assertEquals(0,low,1e-6);
    }
    @Test void everyBoneMovesSmoothlyFrameToFrame()throws Exception{
        int moving=0;
        for(var name:bones().keySet()){
            var m=StarPlatinumMotion.of(name);if(m.kind()!=StarPlatinumMotion.STILL)moving++;
            if(name.startsWith("hair_")||name.startsWith("cloth_")||name.startsWith("scarf_b")||name.startsWith("scarf_t"))assertNotEquals(StarPlatinumMotion.STILL,m.kind(),name);
            for(float wind:new float[]{0,.5f,1}){
                float[] last=null;
                for(int frame=0;frame<=60*20;frame++){
                    float[] p=new float[6];m.add(p,frame/60.0,wind);
                    // a sway may turn a scarf tip fast in the art's wind, but never more than 1.5 degrees between two frames
                    if(last!=null)for(int i=0;i<6;i++)assertTrue(Math.abs(p[i]-last[i])<(i<3?1.5:.05),name+" jumps "+(p[i]-last[i])+" in a 60 fps frame");
                    last=p;
                }
            }
        }
        assertTrue(moving>60,"moving bones "+moving);
        // a change of stance eases in and out over CROSSFADE ticks: never more than half a tick's worth of a linear blend a frame
        float last=StarPlatinumStance.crossfade(0);assertEquals(0,last);
        for(int frame=1;frame<=60;frame++){float k=StarPlatinumStance.crossfade(frame*20f/60);assertTrue(k-last>=0&&k-last<1.5f/StarPlatinumStance.CROSSFADE/3+1e-6);last=k;}
        assertEquals(1,last,1e-6);
    }
}
