package dev.sangwoo.celestialfall.client.boss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.boss.BossKind;
import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import dev.sangwoo.celestialfall.client.xchara.BossAura;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara the boss as it looks (2.137.0, user request: "죠타로 처럼 스킨에 디테일이나 장식을 더 해서 꾸며주고 ... 밤에도
 * 보랏빛으로 빛이나게 ... 눈에는 붉은색 빛"): its skin a slim-armed player's with one red eye, its outfit of boxes on the
 * player's bones (tools/boss/make_xchara.py makes them), no two sharing a texel, its torn hems cut out, and what of
 * the skin and of the outfit shines by itself: violet all over (faintly), its marks brightly, its eye red.
 */
class XCharaOutfitTest {
    static JsonObject json(){
        try(var in=XCharaOutfitTest.class.getResourceAsStream("/assets/celestialfall/bosses/xchara_outfit.json")){
            assertNotNull(in,"the outfit");return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(Exception e){throw new AssertionError(e);}
    }
    static final String TEX="/assets/celestialfall/textures/entity/boss/";

    @Test void theBossIsXCharaWithThreeThousandHealthAndComesOnlyWhenCalled(){
        var k=BossKind.byId("xchara");
        assertSame(BossKind.XCHARA,k);assertEquals(3000,k.health,1e-6,"the user's 3000");assertEquals("x_chara",k.stand);
        assertTrue(k.slim(),"its skin is a slim-armed one");assertFalse(BossKind.JOTARO.slim());
        assertFalse(k.comes(),"no timer of its own until the user says when");assertTrue(BossKind.JOTARO.comes());
        assertEquals(0,k.timeStopBelow,1e-6,"no time stop");
    }
    @Test void itsSkinIsASlimPlayersWithOneRedEyeWhereTheLightIsPut(){
        var skin=BossOutfitTest.image(TEX+"xchara.png");
        assertEquals(64,skin.getWidth());assertEquals(64,skin.getHeight(),"a player's skin");
        // slim arms: the right arm's front is three wide (44..46), nothing at 47; the last arm column of the classic layout is empty
        assertTrue((skin.getRGB(46,24)>>>24)>0,"the arm");assertEquals(0,skin.getRGB(54,24)>>>24,"a slim skin leaves the classic arm's last columns empty");
        // the eye the light is on: the head's front face is at 8..15, 8..15; EYE_X, EYE_Y are from the head's middle and its chin
        int x=8+(int)Math.floor(BossAura.EYE_X+4),y=8+(int)Math.floor(BossAura.EYE_Y+8);
        int rgb=skin.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255;
        assertTrue(r>200&&g<120,"the red eye at "+x+","+y+": "+Integer.toHexString(rgb));
        assertTrue(BossAura.EYE_X<0,"the character's right eye (the skin's red one)");assertTrue(BossAura.EYE_Z<-4,"just before the face");
        // and the other eye is not red
        int other=skin.getRGB(8+7-(x-8),y);assertFalse((other>>16&255)>200&&(other>>8&255)<120&&(other&255)<200,"only the one");
    }
    @Test void noTwoPartsShareTheTextureAndEveryPartShowsSomething(){
        var j=json();var tex=j.getAsJsonArray("texture");int tw=tex.get(0).getAsInt(),th=tex.get(1).getAsInt();
        var img=BossOutfitTest.image(TEX+"xchara_outfit.png");
        int scale=img.getWidth()/tw;assertEquals(img.getHeight(),th*scale,"square texels");assertTrue(scale>=2,"finer than the skin");
        var all=new ArrayList<BossOutfitTest.Face>();int torn=0;
        for(var e:j.getAsJsonArray("parts")){
            var p=e.getAsJsonObject();var name=p.get("name").getAsString();int painted=0,holes=0;
            for(var f:BossOutfitTest.faces(name,BossOutfitTest.floats(p,"uv"),BossOutfitTest.floats(p,"size"))){
                assertTrue(f.x()>=0&&f.y()>=0&&f.x()+f.w()<=tw&&f.y()+f.h()<=th,name+" "+f.side()+" within the texture");
                int x0=Math.round(f.x()*scale),y0=Math.round(f.y()*scale),x1=Math.round((f.x()+f.w())*scale),y1=Math.round((f.y()+f.h())*scale);
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){if((img.getRGB(x,y)>>>24)>=200)painted++;else holes++;}
                all.add(f);
            }
            assertTrue(painted>0,name+" shows");
            if(holes>0){torn++;assertTrue(name.startsWith("tail")||name.startsWith("scarf")||name.equals("locket"),name+" has holes: only the torn hems and the heart are cut out");}
        }
        assertTrue(torn>=6,"the coat's tails and the scarf's are torn at their hems");
        for(int a=0;a<all.size();a++)for(int b=a+1;b<all.size();b++){
            var f=all.get(a);var g=all.get(b);if(f.part().equals(g.part()))continue;
            boolean overlap=f.x()<g.x()+g.w()-1e-4&&g.x()<f.x()+f.w()-1e-4&&f.y()<g.y()+g.h()-1e-4&&g.y()<f.y()+f.h()-1e-4;
            assertFalse(overlap,f.part()+" "+f.side()+" and "+g.part()+" "+g.side()+" share texels");
        }
    }
    @Test void itBuildsOnTheSlimBodysBonesAndItsTailsSwing(){
        var id=new net.minecraft.util.Identifier("celestialfall","textures/entity/boss/xchara_outfit.png");
        var outfit=BossOutfit.build(json(),id);
        assertTrue(outfit.head.hasChild("fringe_mid")&&outfit.head.hasChild("streak")&&outfit.head.hasChild("clip_a"),"its hair and its clip on the head");
        assertTrue(outfit.body.hasChild("locket")&&outfit.body.hasChild("belt")&&outfit.body.hasChild("back_x_a"),"the locket, the belt, the X on its back");
        assertTrue(outfit.rightElbow.hasChild("cuff_right")&&outfit.leftKnee.hasChild("shoe_left"),"cuffs and shoes on the forearms and shins");
        assertEquals(6,outfit.flaps.size(),"four tails of the coat and two of the scarf swing");
        var m=new MotionPlayerModel(true);
        m.leftLeg.pitch=.9f;m.rightLeg.pitch=-.8f;m.rightArm.pitch=-1;
        outfit.pose(m,1,10);
        assertEquals(m.rightArm.pivotX,outfit.rightArm.pivotX,1e-6,"on the slim arm");assertEquals(m.rightArm.pitch,outfit.rightArm.pitch,1e-6);
        assertTrue(BossOutfitTest.flap(outfit,"back_left").pitch>.6f,"a tail rides on the leg behind");
        // the slim arm's box is x -2..1 on the right, -1..2 on the left: the cuffs and shoulders sit about those
        for(var e:json().getAsJsonArray("parts")){
            var p=e.getAsJsonObject();String name=p.get("name").getAsString();if(!name.startsWith("cuff")&&!name.startsWith("shoulder"))continue;
            float[] from=BossOutfitTest.floats(p,"from"),size=BossOutfitTest.floats(p,"size");float mid=from[0]+size[0]/2;
            assertEquals(name.endsWith("right")?-.5f:.5f,mid,1e-4,name+" about the slim arm's middle");
        }
    }
    @Test void whatShinesIsVioletAllOverFaintlyItsMarksBrightlyItsEyeRed(){
        var skin=BossOutfitTest.image(TEX+"xchara.png");var glow=BossOutfitTest.image(TEX+"xchara_glow.png");
        assertEquals(skin.getWidth(),glow.getWidth());assertEquals(skin.getHeight(),glow.getHeight());
        int dim=0,bright=0,body=0;
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){
            int s=skin.getRGB(x,y),g=glow.getRGB(x,y),r=g>>16&255,gr=g>>8&255,b=g&255;
            if((s>>>24)==0){assertEquals(0,g&0xffffff,"nothing shines where there is no skin, at "+x+","+y);continue;}
            body++;assertTrue(r+gr+b>0,"all of it shows a little in the dark, at "+x+","+y);
            assertTrue(b>=gr,"violet or red, never green, at "+x+","+y);
            if(Math.max(r,b)>170)bright++;else if(Math.max(r,b)<140)dim++;
        }
        assertTrue(dim>body*.7,"most of it only half as bright as its marks: "+dim+" of "+body);assertTrue(bright>=20,"its marks brightly: "+bright);
        int ex=8+(int)Math.floor(BossAura.EYE_X+4),ey=8+(int)Math.floor(BossAura.EYE_Y+8),eye=glow.getRGB(ex,ey);
        assertTrue((eye>>16&255)>220&&(eye>>8&255)<90&&(eye&255)<140,"its eye shines red: "+Integer.toHexString(eye));
        // the outfit's: black wherever the outfit is cut away, and its neon marks bright
        var outfit=BossOutfitTest.image(TEX+"xchara_outfit.png");var og=BossOutfitTest.image(TEX+"xchara_outfit_glow.png");
        assertEquals(outfit.getWidth(),og.getWidth());assertEquals(outfit.getHeight(),og.getHeight());
        int neon=0;
        for(int y=0;y<og.getHeight();y++)for(int x=0;x<og.getWidth();x++){
            int g=og.getRGB(x,y);
            if((outfit.getRGB(x,y)>>>24)==0)assertEquals(0,g&0xffffff,"nothing shines where the outfit is cut away, at "+x+","+y);
            else if((g&255)>150&&(g>>8&255)<(g&255))neon++;
        }
        assertTrue(neon>150,"the outfit's lines of light: "+neon);
    }
}
