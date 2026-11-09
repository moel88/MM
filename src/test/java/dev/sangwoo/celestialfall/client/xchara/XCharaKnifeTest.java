package dev.sangwoo.celestialfall.client.xchara;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's knife (2.111.0, user request: "이제 신규 스탠드 만들자 스탠드 이름은 엑스 차라 검 모델링부터 보내줄게 대충 이걸
 * 참고해", with a photo of it in a hand; answers: the red-and-white blocks are the hand, not the knife; its player holds
 * it in person; then "검 크기 ㅈㄴ 키워야할듯 그리고 손잡이 가운데 부분 잡아야하고", choosing twice the photo's size, 3 blocks in
 * all, from in-game candidates) as tools/xchara/make_knife.py writes it for GeckoLib: a black blade 4.1 times as long as
 * it is deep, its spine straight to the point, its edge closing in a little to halfway and its belly curving up to the
 * point, its heel leaning its lower corner ahead (all as measured off the photo); a thin rod for its handle a tenth of
 * the blade's depth below the spine; the model 1.5 blocks from the rod's end to the point, centred on the rod's middle;
 * its display holding it twice that size with the fist round the rod's middle, and X Chara's player holding it.
 */
class XCharaKnifeTest {
    static JsonObject json(String path){
        try(var in=XCharaKnifeTest.class.getResourceAsStream(path)){
            assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(Exception e){throw new AssertionError(e);}
    }
    static BufferedImage image(String path){
        try(var in=XCharaKnifeTest.class.getResourceAsStream(path)){assertNotNull(in,path);return ImageIO.read(in);}catch(Exception e){throw new AssertionError(e);}
    }
    static JsonObject geometry(){return json("/assets/celestialfall/geo/item/x_chara_knife.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();}
    /** A box of the model: its bone, where it reaches, and whether it is turned. */
    record Box(String bone,float x0,float y0,float z0,float x1,float y1,float z1,boolean turned){}
    static List<Box> boxes(){
        var out=new ArrayList<Box>();
        for(var b:geometry().getAsJsonArray("bones")){
            var bone=b.getAsJsonObject();if(!bone.has("cubes"))continue;
            for(var c:bone.getAsJsonArray("cubes")){
                var o=c.getAsJsonObject().getAsJsonArray("origin");var s=c.getAsJsonObject().getAsJsonArray("size");
                float x=o.get(0).getAsFloat(),y=o.get(1).getAsFloat(),z=o.get(2).getAsFloat();
                out.add(new Box(bone.get("name").getAsString(),x,y,z,x+s.get(0).getAsFloat(),y+s.get(1).getAsFloat(),z+s.get(2).getAsFloat(),c.getAsJsonObject().has("rotation")));
            }
        }
        return out;
    }
    static float spine(List<Box> all){return (float)all.stream().filter(b->b.bone.equals("blade")).mapToDouble(Box::x1).max().orElseThrow();}
    /** The heel's upper corner: where the blade begins at its spine. */
    static float heel(List<Box> all,float spine){return (float)all.stream().filter(b->b.bone.equals("blade")&&b.x1>spine-.01f).mapToDouble(Box::y0).min().orElseThrow();}
    static float tip(List<Box> all){return (float)all.stream().filter(b->b.bone.equals("blade")).mapToDouble(Box::y1).max().orElseThrow();}
    /** The blade's depth (spine to edge) across y. */
    static float depthAt(List<Box> all,float spine,float y){
        float low=spine;
        for(var b:all)if(b.bone.equals("blade")&&b.y0<=y&&b.y1>=y)low=Math.min(low,b.x0);
        return spine-low;
    }

    @Test void itsModelIsOneAndAHalfBlocksFromTheRodsEndToThePointCentredOnTheRodsMiddle(){
        var all=boxes();float spine=spine(all),heel=heel(all,spine),tip=tip(all);
        float rodEnd=(float)all.stream().filter(b->b.bone.equals("rod")).mapToDouble(Box::y0).min().orElseThrow();
        assertEquals(24,tip-rodEnd,.1,"1.5 blocks in all in the model (the photo's proportions; a hand holds it twice that)");
        assertEquals(0,(rodEnd+heel)/2,.05,"the rod's middle is the model's origin, where the fist holds it");
        assertEquals(18.1,tip-heel,.15,"the blade");
        assertEquals(5.9,heel-rodEnd,.1,"the rod");
    }
    @Test void itsBladeIsFourTimesAsLongAsItIsDeepItsSpineStraightToThePoint(){
        var all=boxes();float spine=spine(all),heel=heel(all,spine),tip=tip(all);
        float deep=depthAt(all,spine,heel+1);
        assertEquals(4.37,deep,.05,"its depth at the heel");
        float ratio=(tip-heel)/deep;
        assertTrue(ratio>4.0&&ratio<4.25,"4.1 times as long as deep (the photo: 1345 by 325): "+ratio);
        // the spine: every slice of the body reaches it, all the way from the heel to the point
        for(float y=heel+.05f;y<tip-.05f;y+=.05f){
            final float at=y;
            assertTrue(all.stream().anyMatch(b->b.bone.equals("blade")&&b.y0<=at&&b.y1>=at&&b.x1>spine-.002f),"the spine at "+y);
        }
        // the point on the spine's line: the last slice shallow and up at the spine
        assertTrue(depthAt(all,spine,tip-.01f)<.03f*deep,"a sharp point: "+depthAt(all,spine,tip-.01f));
    }
    @Test void itsEdgeClosesInALittleToHalfwayThenItsBellyCurvesUpToThePointAsThePhotoHasIt(){
        var all=boxes();float spine=spine(all),heel=heel(all,spine),tip=tip(all),blade=tip-heel,deep=depthAt(all,spine,heel+1);
        // the photo's measures (its depth along it, of its depth at the heel)
        float[][] photo={{.22f,1f},{.37f,.94f},{.52f,.88f},{.67f,.74f},{.74f,.63f},{.82f,.51f},{.89f,.36f},{.93f,.26f},{.967f,.12f}};
        for(var m:photo){
            float d=depthAt(all,spine,heel+m[0]*blade)/deep;
            assertEquals(m[1],d,.035,"its depth "+m[0]+" of the way to the point");
        }
        // the edge never comes back down: shallower and shallower to the point
        float last=Float.MAX_VALUE;
        for(float t=.05f;t<.999f;t+=.01f){
            float d=depthAt(all,spine,heel+t*blade);
            assertTrue(d<=last+.02f,"no bump at "+t);last=d;
        }
    }
    @Test void itsHeelLeansItsLowerCornerAhead(){
        var all=boxes();float spine=spine(all),heel=heel(all,spine);
        float deep=depthAt(all,spine,heel+1),edge=spine-deep;
        float lower=(float)all.stream().filter(b->b.bone.equals("blade")&&b.x0<edge+.01f).mapToDouble(Box::y0).min().orElseThrow();
        assertEquals(deep*Math.tan(Math.toRadians(7)),lower-heel,.08,"its lower corner ahead of its upper (the photo: 7 degrees)");
    }
    @Test void theRodIsThinAndLevelWithTheSpineATenthOfTheDepthBelowIt(){
        var all=boxes();float spine=spine(all),heel=heel(all,spine),deep=depthAt(all,spine,heel+1);
        var rod=all.stream().filter(b->b.bone.equals("rod")).toList();
        assertFalse(rod.isEmpty());
        for(var b:rod){
            float across=b.x1-b.x0;
            assertTrue(across>=.3f&&across<=.1f*deep,"thin (the photo: 7% of the blade's depth), yet seen: "+across);
            assertEquals(0,(b.x0+b.x1)/2,1e-3,"its middle on the model's axis");
        }
        assertEquals(.1*deep,spine,.03,"the spine a tenth of the blade's depth above it (the photo: 33 of 325)");
    }
    @Test void itIsBlack(){
        var tex=image("/assets/celestialfall/textures/item/x_chara_knife_model.png");
        double sum=0;int n=0;double brightest=0;
        for(int y=0;y<tex.getHeight();y++)for(int x=0;x<tex.getWidth();x++){
            int c=tex.getRGB(x,y);if(c>>>24==0)continue;
            double l=(.2126*(c>>16&255)+.7152*(c>>8&255)+.0722*(c&255))/255;
            sum+=l;n++;brightest=Math.max(brightest,l);
        }
        assertTrue(n>1000);
        assertTrue(sum/n<.12,"near black, as in the photo: "+sum/n);
        assertTrue(brightest<.45,"its brightest the faint line at its edge: "+brightest);
    }
    @Test void everyFaceHasAPlaceOnTheTexturePaintedFacesApartFromEachOther(){
        var geo=geometry();var d=geo.getAsJsonObject("description");
        int tw=d.get("texture_width").getAsInt(),th=d.get("texture_height").getAsInt();
        var tex=image("/assets/celestialfall/textures/item/x_chara_knife_model.png");
        assertEquals(tw,tex.getWidth());assertEquals(th,tex.getHeight());
        int[][] used=new int[th][tw];int faces=0;
        for(var b:geo.getAsJsonArray("bones")){
            var bone=b.getAsJsonObject();if(!bone.has("cubes"))continue;
            for(var c:bone.getAsJsonArray("cubes"))for(var f:c.getAsJsonObject().getAsJsonObject("uv").entrySet()){
                var uv=f.getValue().getAsJsonObject();faces++;
                int x0=uv.getAsJsonArray("uv").get(0).getAsInt(),y0=uv.getAsJsonArray("uv").get(1).getAsInt();
                int w=uv.getAsJsonArray("uv_size").get(0).getAsInt(),h=uv.getAsJsonArray("uv_size").get(1).getAsInt();
                assertTrue(x0>=0&&y0>=0&&x0+w<=tw&&y0+h<=th,"inside the texture");
                // a face all of one colour shares a 2x2 patch with the others of its colour; a painted one is its own
                boolean shared=w==2&&h==2;
                for(int y=y0;y<y0+h;y++)for(int x=x0;x<x0+w;x++){
                    assertEquals(255,tex.getRGB(x,y)>>>24,"painted, solid");
                    if(!shared){assertEquals(0,used[y][x],"apart from the others");used[y][x]=1;}
                }
            }
        }
        assertTrue(faces>500,"faces: "+faces);
    }
    @Test void itsDisplayHoldsItTwiceItsSizeTheFistRoundTheRodsMiddle(){
        var model=json("/assets/celestialfall/models/item/x_chara_knife.json");
        assertEquals("builtin/entity",model.get("parent").getAsString());
        var display=model.getAsJsonObject("display");
        for(String mode:new String[]{"thirdperson_righthand","thirdperson_lefthand","firstperson_righthand","firstperson_lefthand","gui","ground","fixed"})
            assertTrue(display.has(mode),mode);
        for(String mode:new String[]{"thirdperson_righthand","thirdperson_lefthand"}){
            var held=display.getAsJsonObject(mode);
            // twice the model in a hand: 3 blocks from the rod's end to the point (the user's choice among in-game sizes)
            for(var s:held.getAsJsonArray("scale"))assertEquals(2,s.getAsFloat(),1e-6,mode);
            // the game puts a held item's middle at the fist's lower front edge (1/8 block ahead of the arm's middle,
            // at its end): two pixels back and two up put the rod's middle, the model's origin, in the fist
            var at=held.getAsJsonArray("translation");
            assertEquals(0,at.get(0).getAsFloat(),1e-6,mode);assertEquals(-2,at.get(1).getAsFloat(),1e-6,mode);assertEquals(2,at.get(2).getAsFloat(),1e-6,mode);
        }
        // the game mirrors the left hand's turn once more: given mirrored, it holds the spine up there too
        assertEquals(-display.getAsJsonObject("thirdperson_righthand").getAsJsonArray("rotation").get(1).getAsFloat(),
            display.getAsJsonObject("thirdperson_lefthand").getAsJsonArray("rotation").get(1).getAsFloat(),1e-6);
    }
    @Test void xCharasPlayerHoldsItInTheirHand()throws Exception{
        // the knife in their hand whatever it holds, the arm holding it, from their own eyes too
        assertTrue(calls(node("dev/sangwoo/celestialfall/client/StandClient"),"sword","dev/sangwoo/celestialfall/client/StandClient","xChara"),"drawn in their hand");
        assertTrue(calls(node("dev/sangwoo/celestialfall/mixin/client/PlayerEntityRendererMixin"),null,"dev/sangwoo/celestialfall/client/StandClient","xChara"),"their arm holds it");
        assertTrue(calls(node("dev/sangwoo/celestialfall/mixin/client/HeldItemRendererMixin"),null,"dev/sangwoo/celestialfall/client/StandClient","xCharaKnife"),"from their own eyes");
        boolean arms=false;
        for(var m:node("dev/sangwoo/celestialfall/client/MotionPlayerModel").methods)for(var x:m.instructions)if(x instanceof LdcInsnNode l&&"xchara".equals(l.cst))arms=true;
        assertTrue(arms,"held a little forward at their side");
        boolean registered=false;
        for(var m:node("dev/sangwoo/celestialfall/xchara/XChara").methods)for(var x:m.instructions)if(x instanceof LdcInsnNode l&&"x_chara_knife".equals(l.cst))registered=true;
        assertTrue(registered,"the item celestialfall:x_chara_knife");
        for(String lang:new String[]{"ko_kr","en_us"})try(var in=XCharaKnifeTest.class.getResourceAsStream("/assets/celestialfall/lang/"+lang+".json")){
            var text=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(text.contains("\"item.celestialfall.x_chara_knife\"")&&text.contains("\"stand.botand.x_chara\""),lang);
        }
    }

    private static boolean calls(ClassNode node,String method,String owner,String name){
        for(MethodNode m:node.methods)if(method==null||m.name.equals(method))
            for(var x:m.instructions)if(x instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))return true;
        return false;
    }
    private static ClassNode node(String name)throws Exception{
        try(var stream=XCharaKnifeTest.class.getResourceAsStream("/"+name+".class")){
            assertNotNull(stream,name);var node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
}
