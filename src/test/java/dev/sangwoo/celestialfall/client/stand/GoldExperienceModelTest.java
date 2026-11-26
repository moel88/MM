package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.GoldExperienceStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Gold Experience's model files (tools/stand/make_gold_experience.py) and its motion (2.145.0). */
class GoldExperienceModelTest {
    private JsonObject json(String path)throws Exception{
        try(var in=getClass().getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    private JsonObject geometry()throws Exception{return json("/assets/celestialfall/geo/entity/gold_experience.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();}
    private Map<String,JsonObject> bones()throws Exception{
        var map=new HashMap<String,JsonObject>();for(var b:geometry().getAsJsonArray("bones"))map.put(b.getAsJsonObject().get("name").getAsString(),b.getAsJsonObject());return map;
    }
    private java.awt.image.BufferedImage texture()throws Exception{
        try(var in=getClass().getResourceAsStream("/assets/celestialfall/textures/entity/gold_experience.png")){assertNotNull(in);return ImageIO.read(in);}
    }

    @Test void everyFaceIsPaintedInsideTheTexture()throws Exception{
        var g=geometry();var d=g.getAsJsonObject("description");var tex=texture();
        assertEquals(tex.getWidth(),d.get("texture_width").getAsInt());assertEquals(tex.getHeight(),d.get("texture_height").getAsInt());
        int faces=0;
        for(var b:g.getAsJsonArray("bones"))if(b.getAsJsonObject().has("cubes"))for(var c:b.getAsJsonObject().getAsJsonArray("cubes"))
            for(var f:c.getAsJsonObject().getAsJsonObject("uv").entrySet()){
                var uv=f.getValue().getAsJsonObject().getAsJsonArray("uv");var size=f.getValue().getAsJsonObject().getAsJsonArray("uv_size");
                int x=uv.get(0).getAsInt(),y=uv.get(1).getAsInt(),w=size.get(0).getAsInt(),h=size.get(1).getAsInt();
                assertTrue(x>=0&&y>=0&&w>0&&h>0&&x+w<=tex.getWidth()&&y+h<=tex.getHeight(),"a face's texture runs off the atlas");
                faces++;
            }
        assertTrue(faces>900,"faces "+faces);
    }
    @Test void everyBoneHangsFromTheRigAndTheStancesNameOnlyItsBones()throws Exception{
        var bones=bones();
        // the body, the wings on its shoulders, the rings at its belt and the hide behind its hips
        for(String name:new String[]{"root","hips","torso","chest","neck","head","right_arm","left_forearm","right_hand","left_leg","right_shin","left_foot",
            "right_wing","left_wing_blade_2","right_ring","left_ring","hide_0","hide_2_1","hide_right","hide_left_1"})assertTrue(bones.containsKey(name),name);
        for(var b:bones.values())if(b.has("parent"))assertTrue(bones.containsKey(b.get("parent").getAsString()),b.get("name")+"'s parent");
        var poses=json("/assets/celestialfall/stands/gold_experience_poses.json");
        assertEquals(new java.util.TreeSet<>(GoldExperienceStance.NAMES),new java.util.TreeSet<>(poses.keySet()));
        for(var stance:poses.entrySet())for(var bone:stance.getValue().getAsJsonObject().keySet())assertTrue(bones.containsKey(bone),stance.getKey()+" turns "+bone);
        assertEquals(java.util.Set.of("right_arm","left_arm"),poses.getAsJsonObject("stand").keySet(),"standing, it is as it is built, its arms held a little off its hips");
    }
    @Test void itIsBuiltTheSameOnBothSides()throws Exception{
        var bones=bones();int pairs=0;
        for(var e:bones.entrySet())if(e.getKey().contains("right")){
            var left=bones.get(e.getKey().replace("right","left"));assertNotNull(left,e.getKey()+"'s mirror");
            var a=e.getValue().getAsJsonArray("pivot");var b=left.getAsJsonArray("pivot");
            assertEquals(-a.get(0).getAsDouble(),b.get(0).getAsDouble(),1e-3);assertEquals(a.get(1).getAsDouble(),b.get(1).getAsDouble(),1e-3);assertEquals(a.get(2).getAsDouble(),b.get(2).getAsDouble(),1e-3);
            assertEquals(e.getValue().has("cubes")?e.getValue().getAsJsonArray("cubes").size():0,left.has("cubes")?left.getAsJsonArray("cubes").size():0,e.getKey());
            pairs++;
        }
        assertTrue(pairs>=13,"pairs "+pairs);
    }
    @Test void itStandsOnItsSolesAPlayersHeight()throws Exception{
        // the feet and the head hang from bones that do not turn at rest: their cubes' lowest and highest faces are the model's
        double low=1e9,high=-1e9;
        for(var b:geometry().getAsJsonArray("bones"))if(!b.getAsJsonObject().has("rotation")&&b.getAsJsonObject().has("cubes"))
            for(var c:b.getAsJsonObject().getAsJsonArray("cubes")){
                double y=c.getAsJsonObject().getAsJsonArray("origin").get(1).getAsDouble();
                low=Math.min(low,y);high=Math.max(high,y+c.getAsJsonObject().getAsJsonArray("size").get(1).getAsDouble());
            }
        assertEquals(0,low,1e-6);
        assertEquals(GoldExperienceRenderer.HEIGHT,high,.01,"the height the renderer takes it to be");
        // (2.146.1) drawn as tall as Star Platinum to the top of its head: 44.35 units at its own size
        double blocks=high/16*GoldExperienceRenderer.SCALE,platinum=44.35/16*StarPlatinumRenderer.SCALE;
        assertEquals(platinum,blocks,.02,"it stands "+blocks+" blocks, Star Platinum "+platinum);
    }
    /** The rings at its belt are rings (their middles cut out of their plates), and the hide ends in ragged teeth. */
    @Test void theRingsAreCutThroughAndTheHideIsRagged()throws Exception{
        var tex=texture();var bones=bones();
        for(String side:new String[]{"right","left"}){
            var uv=bones.get(side+"_ring").getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonObject("uv").getAsJsonObject("north");
            int x=uv.getAsJsonArray("uv").get(0).getAsInt(),y=uv.getAsJsonArray("uv").get(1).getAsInt(),w=uv.getAsJsonArray("uv_size").get(0).getAsInt(),h=uv.getAsJsonArray("uv_size").get(1).getAsInt();
            assertEquals(0,tex.getRGB(x+w/2,y+h/2)>>>24,"the middle of the "+side+" ring is clear");
            assertEquals(255,tex.getRGB(x+w/2,y+h-3)>>>24,"its lower rim is there");
            assertEquals(0,tex.getRGB(x+1,y+h-2)>>>24,"its plate's corner is clear");
        }
        var uv=bones.get("hide_1_1").getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonObject("uv").getAsJsonObject("south");
        int x=uv.getAsJsonArray("uv").get(0).getAsInt(),y=uv.getAsJsonArray("uv").get(1).getAsInt(),w=uv.getAsJsonArray("uv_size").get(0).getAsInt(),h=uv.getAsJsonArray("uv_size").get(1).getAsInt();
        int clear=0,solid=0;for(int i=0;i<w;i++){if(tex.getRGB(x+i,y+h-4)>>>24==0)clear++;else solid++;}
        assertTrue(clear>0&&solid>0,"the hide's end is torn into teeth: "+clear+" clear, "+solid+" there");
        for(int i=0;i<w;i++)assertEquals(255,tex.getRGB(x+i,y+1)>>>24,"its top is whole");
    }
    /** Its colours are the pictures': pale cream gold, orange armour, purple ladybirds, silver wings. */
    @Test void itIsPaintedInThePicturesColours()throws Exception{
        var tex=texture();int cream=0,orange=0,purple=0,silver=0,all=0;
        for(int y=0;y<tex.getHeight();y++)for(int x=0;x<tex.getWidth();x++){
            int c=tex.getRGB(x,y);if(c>>>24==0)continue;all++;
            int r=c>>16&255,g=c>>8&255,b=c&255;
            if(r>215&&g>195&&b>110&&b<225&&r-b>25)cream++;
            else if(r>190&&g>110&&g<200&&b<110)orange++;
            else if(b>140&&r<180&&b-g>50)purple++;
            else if(Math.abs(r-g)<22&&b>=g&&g>150)silver++;
        }
        assertTrue(cream>all*.2,"cream "+cream+" of "+all);assertTrue(orange>all*.1,"orange "+orange+" of "+all);
        assertTrue(purple>all*.015,"purple "+purple+" of "+all);assertTrue(silver>all*.012,"silver "+silver+" of "+all);
    }
    @Test void everyBoneMovesSmoothlyFrameToFrame()throws Exception{
        int moving=0;
        for(var name:bones().keySet()){
            var m=GoldExperienceMotion.of(name);if(m.kind()!=GoldExperienceMotion.STILL)moving++;
            if(name.startsWith("hide_")||name.endsWith("_ring")||name.contains("wing")||name.endsWith("_leg")||name.endsWith("_foot")||name.equals("root"))assertNotEquals(GoldExperienceMotion.STILL,m.kind(),name);
            for(float calm:new float[]{0,1}){
                float[] last=null;
                for(int frame=0;frame<=60*20;frame++){
                    float[] p=new float[6];m.add(p,frame/60.0,calm);
                    if(last!=null)for(int i=0;i<6;i++)assertTrue(Math.abs(p[i]-last[i])<(i<3?1f:.1f),name+" jumps "+(p[i]-last[i])+" in a 60 fps frame");
                    last=p;
                }
            }
        }
        assertTrue(moving>=36,"moving bones "+moving);
        // floating it rises and sinks slowly; standing to be looked at it does not
        var root=GoldExperienceMotion.of("root");float lo=1e9f,hi=-1e9f;
        for(int frame=0;frame<=60*12;frame++){float[] p=new float[6];root.add(p,frame/60.0,1);lo=Math.min(lo,p[4]);hi=Math.max(hi,p[4]);float[] s=new float[6];root.add(s,frame/60.0,0);for(float v:s)assertEquals(0,v);}
        assertEquals(2*GoldExperienceMotion.BOB,hi-lo,.15);
    }
    @Test void aChangeOfStanceTurnsEveryBoneTheShortWayWithoutAJump()throws Exception{
        var poses=new GoldExperiencePoses();
        for(int from=0;from<GoldExperienceStance.NAMES.size();from++)for(int to=0;to<GoldExperienceStance.NAMES.size();to++){
            if(from==to)continue;
            for(var name:bones().keySet()){
                float[] start=new float[6],end=new float[6],last=null;
                poses.stance(name,from,from,1,start);poses.stance(name,to,to,1,end);
                for(int frame=0;frame<=36;frame++){
                    float[] p=new float[6];poses.stance(name,from,to,GoldExperienceStance.crossfade(frame*20f/60),p);
                    // twelve ticks, eased: no joint turns more than 12 degrees in a 60 fps frame
                    if(last!=null)for(int i=0;i<3;i++)assertTrue(Math.abs(StarPlatinumPoses.wrap(p[i]-last[i]))<12,name+" turns "+(p[i]-last[i])+" in a frame from "+from+" to "+to);
                    last=p;
                }
                double[] a=StarPlatinumPoses.quat(last[0],last[1],last[2],new double[4]),b=StarPlatinumPoses.quat(end[0],end[1],end[2],new double[4]);
                assertEquals(1,Math.abs(a[0]*b[0]+a[1]*b[1]+a[2]*b[2]+a[3]*b[3]),1e-4,name+" ends in the stance");
            }
        }
        float last=GoldExperienceStance.crossfade(0);assertEquals(0,last);
        for(int frame=1;frame<=60;frame++){float k=GoldExperienceStance.crossfade(frame*20f/60);assertTrue(k-last>=0&&k-last<1.5f/GoldExperienceStance.CROSSFADE/3+1e-6);last=k;}
        assertEquals(1,last,1e-6);
    }
}
