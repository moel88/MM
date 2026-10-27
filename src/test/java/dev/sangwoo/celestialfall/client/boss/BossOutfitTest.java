package dev.sangwoo.celestialfall.client.boss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Kujo Jotaro's outfit (2.69.0, user request: "스킨은 이거 기반으로 하되 옷이나 모자나 장식을 더 꾸며서 입체감있게"): boxes
 * worn over his skin (tools/boss/make_jotaro.py makes them), every face of each painted on its texture and no two
 * sharing a texel, each on a bone of the player's body, following it, the coat's skirt swinging with the legs smoothly.
 */
class BossOutfitTest {
    static JsonObject json(){
        try(var in=BossOutfitTest.class.getResourceAsStream("/assets/celestialfall/bosses/jotaro_outfit.json")){
            assertNotNull(in,"the outfit");return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(Exception e){throw new AssertionError(e);}
    }
    static BufferedImage image(String path){
        try(var in=BossOutfitTest.class.getResourceAsStream(path)){assertNotNull(in,path);return ImageIO.read(in);}catch(Exception e){throw new AssertionError(e);}
    }
    /** A face's place on the texture, in the outfit's texture units: x, y, width, height. */
    record Face(String part,String side,float x,float y,float w,float h){}
    /** The six faces of a box {@code size} with its texture at {@code uv}, as the game lays them out. */
    static List<Face> faces(String part,float[] uv,float[] size){
        float u=uv[0],v=uv[1],w=size[0],h=size[1],d=size[2];
        return List.of(new Face(part,"top",u+d,v,w,d),new Face(part,"bottom",u+d+w,v,w,d),new Face(part,"right",u,v+d,d,h),
            new Face(part,"front",u+d,v+d,w,h),new Face(part,"left",u+d+w,v+d,d,h),new Face(part,"back",u+2*d+w,v+d,w,h));
    }
    static float[] floats(JsonObject o,String key){var a=o.getAsJsonArray(key);var out=new float[a.size()];for(int i=0;i<out.length;i++)out[i]=a.get(i).getAsFloat();return out;}

    @Test void everyFaceIsPaintedAndNoTwoShareTheTexture(){
        var j=json();var tex=j.getAsJsonArray("texture");int tw=tex.get(0).getAsInt(),th=tex.get(1).getAsInt();
        var img=image("/assets/celestialfall/textures/entity/boss/jotaro_outfit.png");
        assertEquals(0,img.getWidth()%tw,"whole texels");
        int scale=img.getWidth()/tw;assertEquals(img.getHeight(),th*scale,"square texels");
        assertTrue(scale>=2,"finer than the skin");
        var all=new ArrayList<Face>();
        for(var e:j.getAsJsonArray("parts")){
            var p=e.getAsJsonObject();var name=p.get("name").getAsString();
            for(var f:faces(name,floats(p,"uv"),floats(p,"size"))){
                assertTrue(f.x>=0&&f.y>=0&&f.x+f.w<=tw&&f.y+f.h<=th,name+" "+f.side+" within the texture");
                // every pixel of the face painted (no hole where the game samples it)
                int x0=Math.round(f.x*scale),y0=Math.round(f.y*scale),x1=Math.round((f.x+f.w)*scale),y1=Math.round((f.y+f.h)*scale);
                assertTrue(x1>x0&&y1>y0,name+" "+f.side+" at least a pixel");
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)assertTrue((img.getRGB(x,y)>>>24)>=200,name+" "+f.side+" painted at "+x+","+y);
                all.add(f);
            }
        }
        for(int a=0;a<all.size();a++)for(int b=a+1;b<all.size();b++){
            var f=all.get(a);var g=all.get(b);if(f.part.equals(g.part))continue;
            boolean overlap=f.x<g.x+g.w-1e-4&&g.x<f.x+f.w-1e-4&&f.y<g.y+g.h-1e-4&&g.y<f.y+f.h-1e-4;
            assertFalse(overlap,f.part+" "+f.side+" and "+g.part+" "+g.side+" share texels");
        }
    }
    @Test void itBuildsOnThePlayersBonesAndTheSkinIsAPlayers(){
        var model=BossOutfit.build(json(),new net.minecraft.util.Identifier("celestialfall","textures/entity/boss/jotaro_outfit.png"));
        assertEquals(6,model.flaps.size(),"six skirt flaps");
        for(String bone:new String[]{"head","body","right_arm","left_arm","right_leg","left_leg"})assertTrue(model.root.hasChild(bone),bone);
        assertTrue(model.head.hasChild("cap_crown")&&model.head.hasChild("cap_visor"),"the cap on the head");
        assertTrue(model.rightElbow.hasChild("cuff_right")&&model.leftElbow.hasChild("cuff_left"),"the cuffs on the forearms");
        assertTrue(model.rightKnee.hasChild("shoe_right")&&model.leftKnee.hasChild("shoe_left"),"the shoes on the shins");
        var skin=image("/assets/celestialfall/textures/entity/boss/jotaro.png");
        assertEquals(64,skin.getWidth());assertEquals(64,skin.getHeight(),"a player's skin");
        assertTrue((skin.getRGB(12,12)>>>24)>0,"its face");
    }
    @Test void itFollowsTheBodyAndTheSkirtSwingsWithTheLegs(){
        var outfit=BossOutfit.build(json(),new net.minecraft.util.Identifier("celestialfall","textures/entity/boss/jotaro_outfit.png"));
        var m=new MotionPlayerModel(false);
        m.head.yaw=.4f;m.head.pitch=-.2f;m.body.pitch=.15f;m.rightArm.pitch=-1.1f;m.rightArm.getChild("elbow").pitch=-.7f;
        m.leftLeg.pitch=.9f;m.rightLeg.pitch=-.8f;m.leftLeg.getChild("knee").pitch=.6f;
        outfit.pose(m,1,10);
        for(var pair:new ModelPart[][]{{outfit.head,m.head},{outfit.body,m.body},{outfit.rightArm,m.rightArm},{outfit.leftLeg,m.leftLeg},{outfit.rightElbow,m.rightArm.getChild("elbow")},{outfit.leftKnee,m.leftLeg.getChild("knee")}}){
            var a=pair[0].getTransform();var b=pair[1].getTransform();
            assertEquals(b.pitch,a.pitch,1e-6);assertEquals(b.yaw,a.yaw,1e-6);assertEquals(b.roll,a.roll,1e-6);
            assertEquals(b.pivotX,a.pivotX,1e-6);assertEquals(b.pivotY,a.pivotY,1e-6);assertEquals(b.pivotZ,a.pivotZ,1e-6);
        }
        // the left leg back: its back flap is pushed back; the right leg forward: its front flap goes forward
        assertTrue(flap(outfit,"back_left").pitch>.6f,"the back flap rides on the leg behind");
        assertTrue(flap(outfit,"front_right").pitch<-.5f,"the front flap rides on the leg in front");
        assertTrue(flap(outfit,"back_right").pitch>0&&flap(outfit,"back_right").pitch<.5f,"a leg in front does not lift the back flap past the run's flare");
    }
    static ModelPart flap(BossOutfit.Model o,String swing){return o.flaps.get(o.swings.indexOf(swing));}
    @Test void theSkirtNeverJumpsAFrameWalkingOrRunning(){
        var outfit=BossOutfit.build(json(),new net.minecraft.util.Identifier("celestialfall","textures/entity/boss/jotaro_outfit.png"));
        var m=new MotionPlayerModel(false);
        float[] last=null;float limb=0,limbDistance=0;double worst=0,legStep=0;
        // 60 frames a second: walks up to a run (the limb animator's distance to 1, its angle a tick's distance a tick) and stops
        for(int f=0;f<60*8;f++){
            float age=f/3f,target=f<60*5?Math.min(1,f/90f):0;
            limbDistance+=(target-limbDistance)*.4f/3;limb+=limbDistance/3;
            float swing=MathHelper.cos(limb*.6662f)*1.4f*limbDistance;
            m.leftLeg.pitch=swing;m.rightLeg.pitch=-swing;m.leftLeg.getChild("knee").pitch=Math.max(0,swing)*.5f;m.rightLeg.getChild("knee").pitch=Math.max(0,-swing)*.5f;
            outfit.pose(m,limbDistance,age);
            int n=outfit.flaps.size()*2;var now=new float[n+2];
            for(int i=0;i<outfit.flaps.size();i++){now[2*i]=outfit.flaps.get(i).pitch;now[2*i+1]=outfit.flaps.get(i).roll;}
            now[n]=swing;now[n+1]=Math.max(0,swing)*.5f;
            if(last!=null){
                legStep=Math.max(legStep,Math.abs(now[n]-last[n]));
                for(int i=0;i<n;i++)worst=Math.max(worst,Math.abs(now[i]-last[i]));
            }
            last=now;
        }
        // a back flap rides on the leg and its knee (1.15 times the leg's swing here) and flares as the run takes up or lets go
        assertTrue(worst<=legStep*1.15+Math.toRadians(3),"a flap turns no faster than the leg under it ("+Math.toDegrees(legStep)+" degrees a frame at most) and its flare: "+Math.toDegrees(worst));
        assertTrue(worst>Math.toRadians(3),"the skirt does swing");
    }
}
