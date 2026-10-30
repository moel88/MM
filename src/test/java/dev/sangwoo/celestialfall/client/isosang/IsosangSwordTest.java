package dev.sangwoo.celestialfall.client.isosang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's sword (2.76.0) as tools/isosang/make_sword.py writes it for GeckoLib, after the art and the 3D model the
 * user sent: a long straight blade with its point, the grip held at the model's middle; the guard two wings, one off
 * each edge, the one the other's mirror image, reaching furthest out near their front (as the 3D model's parts sheet
 * has it); every face its own place on the texture, inside it and apart from the others; the glow texture as large as
 * the texture, its lit texels the texture's own colours and only on the blade's flats (the lightning and its glints);
 * its display holding it in a hand.
 */
class IsosangSwordTest {
    static JsonObject json(String path){
        try(var in=IsosangSwordTest.class.getResourceAsStream(path)){
            assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(Exception e){throw new AssertionError(e);}
    }
    static BufferedImage image(String path){
        try(var in=IsosangSwordTest.class.getResourceAsStream(path)){assertNotNull(in,path);return ImageIO.read(in);}catch(Exception e){throw new AssertionError(e);}
    }
    /** A face's place on the texture: which bone it belongs to, and its texels (x, y, width, height). */
    record Place(String bone,String face,int x,int y,int w,int h){}

    static JsonObject geometry(){return json("/assets/celestialfall/geo/item/isosang_sword.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();}
    static List<Place> places(JsonObject geo){
        var out=new ArrayList<Place>();
        for(var b:geo.getAsJsonArray("bones")){
            var bone=b.getAsJsonObject();if(!bone.has("cubes"))continue;
            for(var c:bone.getAsJsonArray("cubes"))for(var f:c.getAsJsonObject().getAsJsonObject("uv").entrySet()){
                var uv=f.getValue().getAsJsonObject();
                out.add(new Place(bone.get("name").getAsString(),f.getKey(),uv.getAsJsonArray("uv").get(0).getAsInt(),uv.getAsJsonArray("uv").get(1).getAsInt(),
                    uv.getAsJsonArray("uv_size").get(0).getAsInt(),uv.getAsJsonArray("uv_size").get(1).getAsInt()));
            }
        }
        return out;
    }

    @Test void aLongStraightBladeWithItsPointAGuardAndTheGripHeldAtTheMiddle(){
        var geo=geometry();float low=Float.MAX_VALUE,high=-Float.MAX_VALUE,gripLow=Float.MAX_VALUE,gripHigh=-Float.MAX_VALUE,bladeLeft=0,bladeRight=0;
        var bones=new ArrayList<String>();
        for(var b:geo.getAsJsonArray("bones")){
            var bone=b.getAsJsonObject();String name=bone.get("name").getAsString();bones.add(name);
            if(!bone.has("cubes"))continue;
            for(var c:bone.getAsJsonArray("cubes")){
                var o=c.getAsJsonObject().getAsJsonArray("origin");var s=c.getAsJsonObject().getAsJsonArray("size");
                float y0=o.get(1).getAsFloat(),y1=y0+s.get(1).getAsFloat();
                low=Math.min(low,y0);high=Math.max(high,y1);
                if(name.equals("grip")){gripLow=Math.min(gripLow,y0);gripHigh=Math.max(gripHigh,y1);}
                if(name.equals("blade")){bladeLeft=Math.min(bladeLeft,o.get(0).getAsFloat());bladeRight=Math.max(bladeRight,o.get(0).getAsFloat()+s.get(0).getAsFloat());}
            }
        }
        float bladeWide=bladeRight-bladeLeft;
        assertEquals(List.of("root","grip","guard","blade"),bones);
        assertTrue(gripLow<0&&gripHigh>0,"the hand holds the grip at the model's middle: "+gripLow+" to "+gripHigh);
        assertTrue(high>26&&high<32,"the point about two blocks up the sword (in sixteenths): "+high);
        assertTrue(high-low>30&&high-low<36,"its whole length: "+(high-low));
        assertTrue(bladeWide>1.8f&&bladeWide<2.3f,"the blade's width, edge to edge: "+bladeWide);
        assertTrue((high-gripHigh)/bladeWide>12,"long and straight");
    }
    @Test void theGuardsTwoWingsAreMirrorImagesReachingFurthestNearTheirFront(){
        var geo=geometry();float blade=0;
        for(var b:geo.getAsJsonArray("bones")){
            var bone=b.getAsJsonObject();
            if(bone.get("name").getAsString().equals("blade"))for(var c:bone.getAsJsonArray("cubes")){
                var o=c.getAsJsonObject().getAsJsonArray("origin");blade=Math.max(blade,o.get(0).getAsFloat()+c.getAsJsonObject().getAsJsonArray("size").get(0).getAsFloat());
            }
        }
        // the wings: the guard's boxes reaching well past the blade's edge (not turned: the round parts and the knobs are)
        float right=0,left=0,rightY=0,leftY=0,wingLow=Float.MAX_VALUE,wingHigh=-Float.MAX_VALUE;
        for(var b:geo.getAsJsonArray("bones")){
            var bone=b.getAsJsonObject();
            if(!bone.get("name").getAsString().equals("guard"))continue;
            for(var c:bone.getAsJsonArray("cubes")){
                var cube=c.getAsJsonObject();var o=cube.getAsJsonArray("origin");var s=cube.getAsJsonArray("size");
                if(cube.has("rotation"))continue;
                float x0=o.get(0).getAsFloat(),x1=x0+s.get(0).getAsFloat(),y0=o.get(1).getAsFloat(),y1=y0+s.get(1).getAsFloat();
                if(x1>right){right=x1;rightY=(y0+y1)/2;}
                if(x0<left){left=x0;leftY=(y0+y1)/2;}
                if(x1>1.2f*blade){wingLow=Math.min(wingLow,y0);wingHigh=Math.max(wingHigh,y1);}
            }
        }
        assertEquals(right,-left,1e-3,"the one wing the other's mirror image");
        assertEquals(rightY,leftY,1e-3);
        float widths=right/(2*blade);
        assertTrue(widths>1.4f&&widths<1.7f,"each reaching about one and a half blade widths out from the middle: "+widths);
        assertTrue(rightY>(wingLow+wingHigh)/2,"furthest out near its front, towards the point: "+rightY+" in "+wingLow+" to "+wingHigh);
    }
    /** A box of the model as the json has it: its bone, its corners, and whether it is turned. */
    record Box(String bone,float x0,float y0,float z0,float x1,float y1,float z1,boolean turned){}
    static List<Box> boxes(JsonObject geo){
        var out=new ArrayList<Box>();
        for(var b:geo.getAsJsonArray("bones")){
            var bone=b.getAsJsonObject();if(!bone.has("cubes"))continue;
            for(var c:bone.getAsJsonArray("cubes")){
                var cube=c.getAsJsonObject();var o=cube.getAsJsonArray("origin");var s=cube.getAsJsonArray("size");
                float x0=o.get(0).getAsFloat(),y0=o.get(1).getAsFloat(),z0=o.get(2).getAsFloat();
                out.add(new Box(bone.get("name").getAsString(),x0,y0,z0,x0+s.get(0).getAsFloat(),y0+s.get(1).getAsFloat(),z0+s.get(2).getAsFloat(),cube.has("rotation")));
            }
        }
        return out;
    }
    @Test void theUpperGemSitsInANotchOfTheGuardsSocketRoundTheTopOfTheGrip(){
        // 2.76.4 (user request: "아니 흰 보석이랑 코등이랑 자연스럽게 이어지지가 않았잖아", as on the made copy in the
        // user's photo): below the guard's core its gold goes on down round the grip, and the upper gem sits in a notch
        // up the middle of it, the gold beside it standing as far out as its border
        var all=boxes(geometry());
        float blade=0;for(var b:all)if(b.bone.equals("blade"))blade=Math.max(blade,b.x1);
        float coreBottom=Float.MAX_VALUE;
        for(var b:all)if(b.bone.equals("guard")&&!b.turned&&Math.abs(b.x0+blade)<1e-3f&&Math.abs(b.x1-blade)<1e-3f)coreBottom=Math.min(coreBottom,b.y0);
        var socket=new ArrayList<Box>();
        for(var b:all)if(b.bone.equals("guard")&&!b.turned&&b.y1<=coreBottom+1e-3f&&b.x0>=-blade-1e-3f&&b.x1<=blade+1e-3f)socket.add(b);
        assertFalse(socket.isEmpty(),"the guard goes on down below its core round the grip");
        for(var b:socket)assertTrue(socket.stream().anyMatch(m->Math.abs(m.x0+b.x1)<1e-3f&&Math.abs(m.x1+b.x0)<1e-3f&&Math.abs(m.y0-b.y0)<1e-3f&&Math.abs(m.z1-b.z1)<1e-3f),
            "its one side the other's mirror image: "+b);
        float topOfNotch=-Float.MAX_VALUE;Box widest=null;
        for(var b:socket)if(b.x0>1e-3f){
            topOfNotch=Math.max(topOfNotch,b.y1);
            // where the notch is widest with the gold still either side of the gem: the gem's widest, at the socket's foot
            if(all.stream().anyMatch(g->g.bone.equals("grip")&&!g.turned&&g.z0>0&&g.y0<b.y1&&g.y1>b.y0&&Math.abs(g.x1-b.x0)<1e-3f)&&(widest==null||b.x0>widest.x0))widest=b;
        }
        float notchTop=topOfNotch;
        assertTrue(socket.stream().anyMatch(b->b.x0<0&&b.x1>0&&b.y0>=notchTop-1e-3f),"the notch comes to a point in the gold below the core");
        assertNotNull(widest,"the gem fills the notch, its sides against the gold's");
        var at=widest;
        float gemFace=0,gold=0;
        for(var g:all)if(g.bone.equals("grip")&&!g.turned&&g.z0>0&&g.y0<at.y1&&g.y1>at.y0&&Math.abs(g.x1-at.x0)<1e-3f)gemFace=Math.max(gemFace,g.z1);
        for(var b:socket)if(b.y0<at.y1&&b.y1>at.y0)gold=Math.max(gold,b.z1);
        assertTrue(gold>=gemFace,"the gem set in the gold, its border no further out than the gold beside it: "+gemFace+" and "+gold);
        assertTrue(at.x0>0.25f*blade,"the gem about a third of the blade's width across at its widest: "+(2*at.x0/(2*blade)));
    }
    @Test void everyFaceHasItsOwnPlaceOnTheTexture(){
        var geo=geometry();var d=geo.getAsJsonObject("description");
        int tw=d.get("texture_width").getAsInt(),th=d.get("texture_height").getAsInt();
        var tex=image("/assets/celestialfall/textures/item/isosang_sword_model.png");
        assertEquals(tw,tex.getWidth());assertEquals(th,tex.getHeight());
        var places=places(geo);assertTrue(places.size()>200,"faces: "+places.size());
        boolean[][] used=new boolean[th][tw];
        for(var p:places){
            assertTrue(p.x>=0&&p.y>=0&&p.x+p.w<=tw&&p.y+p.h<=th,"inside the texture: "+p);
            for(int y=p.y;y<p.y+p.h;y++)for(int x=p.x;x<p.x+p.w;x++){
                assertFalse(used[y][x],"apart from the others: "+p);used[y][x]=true;
                assertEquals(255,tex.getRGB(x,y)>>>24,"painted, solid: "+p);
            }
        }
    }
    @Test void theLightningGlowsInItsOwnColoursOnTheBladesFlatsOnly(){
        var geo=geometry();var tex=image("/assets/celestialfall/textures/item/isosang_sword_model.png");
        var glow=image("/assets/celestialfall/textures/item/isosang_sword_model_glow.png");
        assertEquals(tex.getWidth(),glow.getWidth());assertEquals(tex.getHeight(),glow.getHeight());
        boolean[][] flats=new boolean[tex.getHeight()][tex.getWidth()];
        for(var p:places(geo))if(p.bone.equals("blade")&&(p.face.equals("north")||p.face.equals("south")))
            for(int y=p.y;y<p.y+p.h;y++)for(int x=p.x;x<p.x+p.w;x++)flats[y][x]=true;
        int lit=0,bright=0;
        for(int y=0;y<glow.getHeight();y++)for(int x=0;x<glow.getWidth();x++){
            int g=glow.getRGB(x,y);if(g>>>24==0)continue;
            lit++;
            assertTrue(flats[y][x],"only on the blade's flats: "+x+","+y);
            assertEquals(tex.getRGB(x,y),g,"in the texture's own colour");
            int r=g>>16&255,gr=g>>8&255,b=g&255;
            if(r+gr+b>540)bright++;
        }
        assertTrue(lit>1000,"the lightning and its glints: "+lit);
        assertTrue(bright>lit/4,"mostly bright, lavender-white: "+bright+" of "+lit);
    }
    @Test void itsDisplayHoldsItInAHand(){
        var display=json("/assets/celestialfall/models/item/isosang_sword.json").getAsJsonObject("display");
        for(String mode:new String[]{"thirdperson_righthand","thirdperson_lefthand","firstperson_righthand","firstperson_lefthand","gui","ground","fixed"})
            assertTrue(display.has(mode),mode);
        // flats to the sides as a sword is held (a quarter turn about y), the point raised a little ahead of the hand
        var third=display.getAsJsonObject("thirdperson_righthand").getAsJsonArray("rotation");
        assertEquals(90,third.get(1).getAsFloat(),1e-6);
        assertTrue(third.get(0).getAsFloat()>0,"the point a little up");
    }
}
