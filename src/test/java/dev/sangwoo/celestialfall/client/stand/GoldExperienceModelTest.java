package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.GoldExperienceStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gold Experience's model files and its motion (2.145.0; 2.155.0: the body the user sent, tools/stand/
 * import_gold_experience.py, on the skeleton the stances turn, tools/stand/make_gold_experience.py).
 */
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
    private static GoldExperienceMesh mesh(){return GoldExperienceMesh.get();}

    /** Every corner of every triangle is a vertex of the body, and every vertex is on the texture (the file's upper half). */
    @Test void everyVertexIsOnTheTextureAndEveryTriangleOnItsVertices()throws Exception{
        var m=mesh();var d=geometry().getAsJsonObject("description");var tex=texture();
        assertEquals(tex.getWidth(),d.get("texture_width").getAsInt());assertEquals(tex.getHeight(),d.get("texture_height").getAsInt());
        assertEquals(1024,tex.getWidth());assertEquals(512,tex.getHeight());
        assertTrue(m.vertices()>3000,"vertices "+m.vertices());assertTrue(m.tri.length/3>4000,"triangles "+m.tri.length/3);
        for(int i:m.tri)assertTrue(i>=0&&i<m.vertices());
        for(float uv:m.uv)assertTrue(uv>=-1e-4f&&uv<=1+1e-4f,"a vertex off the texture: "+uv);
        for(int v=0;v<m.vertices();v++){float l=m.normal[v*3]*m.normal[v*3]+m.normal[v*3+1]*m.normal[v*3+1]+m.normal[v*3+2]*m.normal[v*3+2];assertEquals(1,l,1e-3,"normal "+v);}
    }
    /** Each vertex goes with one to four of the skeleton's bones, as much with each as its weight, the weights making one. */
    @Test void everyVertexGoesWithBonesOfTheSkeletonWhoseWeightsMakeOne()throws Exception{
        var m=mesh();var bones=bones();
        assertEquals(bones.size(),m.bones.length,"the body's bones are the skeleton's");
        for(String b:m.bones)assertTrue(bones.containsKey(b),b);
        int[] carried=new int[m.bones.length];
        for(int v=0;v<m.vertices();v++){
            float sum=0;int most=-1;float top=0;
            for(int k=0;k<4;k++){int b=m.bone[v*4+k];float w=m.weight[v*4+k];if(b<0){assertEquals(0,w,0);continue;}assertTrue(w>0&&b<m.bones.length);sum+=w;if(w>top){top=w;most=b;}}
            assertEquals(1,sum,1e-4,"vertex "+v+"'s weights");
            carried[most]++;
        }
        // the trunk, the head, every limb and the fingers' joints carry the body (the root carries nothing of it)
        for(String b:new String[]{"hips","torso","chest","head","right_arm","left_forearm","right_hand","left_fingers","right_index_1","left_middle_2","right_leg","left_shin","right_foot"})
            assertTrue(carried[m.bone(b)]>5,b+" carries "+carried[m.bone(b)]);
        assertEquals(0,carried[m.bone("root")]);
    }
    @Test void everyBoneHangsFromTheRigAndTheStancesNameOnlyItsBones()throws Exception{
        var bones=bones();
        for(String name:new String[]{"root","hips","torso","chest","neck","head","right_arm","left_forearm","right_hand","left_leg","right_shin","left_foot",
            "right_fingers","left_fingers","right_index_1","right_pinky_2","left_ring_1","left_middle_2"})assertTrue(bones.containsKey(name),name);
        // (2.155.0) no wings, rings or hide of their own: the body's shoulder pads, hooks and loincloth go with its shoulders, hips and legs
        for(var name:bones.keySet())assertFalse(name.contains("wing")||name.contains("ring_")&&!name.matches("(left|right)_ring_[12]")||name.startsWith("hide"),name);
        for(var b:bones.values())if(b.has("parent"))assertTrue(bones.containsKey(b.get("parent").getAsString()),b.get("name")+"'s parent");
        for(var b:bones.values()){assertFalse(b.has("cubes"),"no boxes: the body is drawn as it is");assertFalse(b.has("rotation"),"no bone turned at rest");}
        var poses=json("/assets/celestialfall/stands/gold_experience_poses.json");
        assertEquals(new java.util.TreeSet<>(GoldExperienceStance.NAMES),new java.util.TreeSet<>(poses.keySet()));
        for(var stance:poses.entrySet())for(var bone:stance.getValue().getAsJsonObject().keySet())assertTrue(bones.containsKey(bone),stance.getKey()+" turns "+bone);
        assertEquals(java.util.Set.of("right_arm","left_arm"),poses.getAsJsonObject("stand").keySet(),"standing, it is as it is built, its arms held a little off its hips");
    }
    /** A fist folds every finger at its knuckle and further at its middle and last joints, the left hand one way, the right the other. */
    @Test void aFistFoldsEveryFingerAtEachOfItsJoints()throws Exception{
        var guard=json("/assets/celestialfall/stands/gold_experience_poses.json").getAsJsonObject("guard");
        for(String side:new String[]{"left","right"}){
            float s=side.equals("left")?1:-1;
            float knuckle=guard.getAsJsonObject(side+"_fingers").getAsJsonArray("rot").get(2).getAsFloat();
            assertEquals(100,s*knuckle,1e-3);
            for(String f:new String[]{"index","middle","ring","pinky"}){
                float mid=s*guard.getAsJsonObject(side+"_"+f+"_1").getAsJsonArray("rot").get(2).getAsFloat(),last=s*guard.getAsJsonObject(side+"_"+f+"_2").getAsJsonArray("rot").get(2).getAsFloat();
                assertTrue(mid>60&&last>40&&last<mid,side+" "+f+": "+mid+", "+last);
            }
        }
    }
    @Test void itIsBuiltTheSameOnBothSides()throws Exception{
        var bones=bones();int pairs=0;
        for(var e:bones.entrySet())if(e.getKey().contains("right")){
            var left=bones.get(e.getKey().replace("right","left"));assertNotNull(left,e.getKey()+"'s mirror");
            var a=e.getValue().getAsJsonArray("pivot");var b=left.getAsJsonArray("pivot");
            assertEquals(-a.get(0).getAsDouble(),b.get(0).getAsDouble(),1e-3);assertEquals(a.get(1).getAsDouble(),b.get(1).getAsDouble(),1e-3);assertEquals(a.get(2).getAsDouble(),b.get(2).getAsDouble(),1e-3);
            pairs++;
        }
        assertEquals(15,pairs);
        // the body on the middle (the model is not quite alike on both sides: its shoulder pads and the ladybirds on its
        // hips): its trunk and head as much on the left as on the right
        var m=mesh();double sum=0;int count=0;
        for(int v=0;v<m.vertices();v++){
            int most=-1;float top=0;for(int k=0;k<4;k++)if(m.bone[v*4+k]>=0&&m.weight[v*4+k]>top){top=m.weight[v*4+k];most=m.bone[v*4+k];}
            if(!java.util.Set.of("hips","torso","chest","neck","head").contains(m.bones[most]))continue;
            sum+=m.pos[v*3];count++;
        }
        assertEquals(0,sum/count,.01,"the trunk's middle: "+sum/count+" blocks off");
    }
    @Test void itStandsOnItsSolesTallerThanStarPlatinumAsTheUserChose()throws Exception{
        var m=mesh();float low=1e9f,high=-1e9f;
        for(int v=0;v<m.vertices();v++){low=Math.min(low,m.pos[v*3+1]);high=Math.max(high,m.pos[v*3+1]);}
        assertEquals(0,low*16,1e-3);
        assertEquals(GoldExperienceRenderer.HEIGHT,high*16,.01,"the height the renderer takes it to be");
        // (2.146.1) drawn as tall as Star Platinum to the top of its head (44.35 units at its own size); (2.155.1) and
        // then 1.12 times as big, the user's choice: 2.24 blocks
        double blocks=high*GoldExperienceRenderer.SCALE,platinum=44.35/16*StarPlatinumRenderer.SCALE;
        assertEquals(1.12*platinum,blocks,.02,"it stands "+blocks+" blocks, Star Platinum "+platinum);
        assertEquals(2.24,blocks,.01);
    }
    /** Its colours are the model's: cream gold, orange armour, purple ladybirds and stones, silver pads and hooks. */
    @Test void itIsTheModelsColours()throws Exception{
        var tex=texture();int gold=0,orange=0,purple=0,silver=0,all=0;
        for(int y=0;y<tex.getHeight();y++)for(int x=0;x<tex.getWidth();x++){
            int c=tex.getRGB(x,y);all++;
            int r=c>>16&255,g=c>>8&255,b=c&255;
            if(r>170&&g>90&&g<150&&b<90)orange++;
            else if(r>170&&g>140&&b<150&&r-b>60)gold++;
            else if(b>120&&r<200&&b-g>40)purple++;
            else if(Math.abs(r-g)<20&&Math.abs(g-b)<25&&g>120)silver++;
        }
        assertTrue(gold>all*.25,"gold "+gold+" of "+all);assertTrue(orange>all*.08,"orange "+orange+" of "+all);
        assertTrue(purple>all*.015,"purple "+purple+" of "+all);assertTrue(silver>all*.02,"silver "+silver+" of "+all);
    }
    /** Skinned by bones that stand still it is where it is at rest; a bone moved takes what it carries with it, as much as its weight. */
    @Test void itGoesWhereItsBonesTakeIt(){
        var m=mesh();int n=m.bones.length;var poses=new Matrix4f[n];var normals=new Matrix3f[n];var seen=new boolean[n];
        for(int i=0;i<n;i++){poses[i]=new Matrix4f();normals[i]=new Matrix3f();seen[i]=true;}
        m.skin(poses,normals,seen);
        for(int v=0;v<m.vertices();v++){assertEquals(m.pos[v*3],m.x(v),1e-5);assertEquals(m.pos[v*3+1],m.y(v),1e-5);assertEquals(m.pos[v*3+2],m.z(v),1e-5);}
        int head=m.bone("head");poses[head].translate(0,1,0);
        m.skin(poses,normals,seen);
        for(int v=0;v<m.vertices();v++){
            float w=0;for(int k=0;k<4;k++)if(m.bone[v*4+k]==head)w+=m.weight[v*4+k];
            assertEquals(m.pos[v*3+1]+w,m.y(v),1e-4,"vertex "+v);
        }
    }
    /** (2.147.0) An afterimage of the rush's arms draws its arms alone, each as solid as its look shows it. */
    @Test void anAfterimageOfTheArmsDrawsTheArmsAlone(){
        var m=mesh();int n=m.bones.length;var poses=new Matrix4f[n];var normals=new Matrix3f[n];var seen=new boolean[n];
        for(int i=0;i<n;i++){poses[i]=new Matrix4f();normals[i]=new Matrix3f();seen[i]=true;}
        m.skin(poses,normals,seen);
        var all=new Count();m.draw(all,0,0,1,1,1,1,false,1,1);
        var arms=new Count();m.draw(arms,0,0,1,1,1,1,true,1,1);
        var left=new Count();m.draw(left,0,0,1,1,1,1,true,1,0);
        assertEquals(m.tri.length/3*4,all.vertices,"every triangle, as a quad");
        assertTrue(arms.vertices>all.vertices/10&&arms.vertices<all.vertices/2,"the arms: "+arms.vertices+" of "+all.vertices);
        assertTrue(left.vertices>arms.vertices/3&&left.vertices<arms.vertices*2/3,"the left arm: "+left.vertices+" of "+arms.vertices);
        for(float x:left.x)assertTrue(x<.02f,"the left arm is on the left (-x as GeckoLib bakes it): "+x);
    }
    private static final class Count implements VertexConsumer {
        int vertices;final List<Float> x=new ArrayList<>();
        @Override public VertexConsumer vertex(double x,double y,double z){return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer normal(float x,float y,float z){return this;}
        @Override public void next(){}
        @Override public void fixedColor(int r,int g,int b,int a){}
        @Override public void unfixColor(){}
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){vertices++;this.x.add(x);}
    }
    @Test void everyBoneMovesSmoothlyFrameToFrame()throws Exception{
        int moving=0;
        for(var name:bones().keySet()){
            var m=GoldExperienceMotion.of(name);if(m.kind()!=GoldExperienceMotion.STILL)moving++;
            if(name.endsWith("_leg")||name.endsWith("_foot")||name.endsWith("_arm")||name.equals("root")||name.equals("head"))assertNotEquals(GoldExperienceMotion.STILL,m.kind(),name);
            for(float calm:new float[]{0,1}){
                float[] last=null;
                for(int frame=0;frame<=60*20;frame++){
                    float[] p=new float[6];m.add(p,frame/60.0,calm);
                    if(last!=null)for(int i=0;i<6;i++)assertTrue(Math.abs(p[i]-last[i])<(i<3?1f:.1f),name+" jumps "+(p[i]-last[i])+" in a 60 fps frame");
                    last=p;
                }
            }
        }
        assertEquals(18,moving,"the trunk, the head, the arms and hands, the legs and feet");
        // floating it rises and sinks slowly; standing to be looked at it does not
        var root=GoldExperienceMotion.of("root");float lo=1e9f,hi=-1e9f;
        for(int frame=0;frame<=60*12;frame++){float[] p=new float[6];root.add(p,frame/60.0,1);lo=Math.min(lo,p[4]);hi=Math.max(hi,p[4]);float[] s=new float[6];root.add(s,frame/60.0,0);for(float v:s)assertEquals(0,v);}
        assertEquals(2*GoldExperienceMotion.BOB,hi-lo,.15);
    }
    /**
     * (2.152.0) H's stance: down on its knee it lays its open palm on the ground ahead of it (the user's "스탠드가 땅을
     * 만지고"); its knee and its planted foot are on the ground too, and nothing of it sinks into it.
     */
    @Test void downOnItsKneeItsPalmItsKneeAndItsFootAreOnTheGround()throws Exception{
        var g=json("/assets/celestialfall/stands/gold_experience_fists.json").getAsJsonObject("ground");
        for(var part:new String[]{"palm","knee","sole"}){double y=g.getAsJsonArray(part).get(1).getAsDouble();assertTrue(y>-.3&&y<.4,part+" at "+y+" units off the ground");}
        assertTrue(g.getAsJsonArray("lowest").get(1).getAsDouble()>-.3,"nothing of it sinks into the ground");
        assertTrue(g.getAsJsonArray("palm").get(2).getAsDouble()<-8,"its palm ahead of it");
        // the right fist's point (where its light comes from: GoldTree) is on the palm, on the ground
        assertTrue(GoldRush.FISTS[GoldExperienceStance.GROUND][1][2]<.1f,"the palm's point "+GoldRush.FISTS[GoldExperienceStance.GROUND][1][2]+" blocks up");
    }
    @Test void aChangeOfStanceTurnsEveryBoneTheShortWayWithoutAJump()throws Exception{
        var poses=new GoldExperiencePoses();
        // (the overdrive punch's two stances are gone into and out of by GoldPunch alone, from the guard and back: GoldPunchTest)
        for(int from=0;from<GoldExperienceStance.OVER_WIND;from++)for(int to=0;to<GoldExperienceStance.OVER_WIND;to++){
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
