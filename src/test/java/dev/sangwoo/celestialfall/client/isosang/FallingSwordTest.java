package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's G (2.83.0): her motion runs smooth from the stance and back to it, facing the front, her feet on the
 * ground; its shots are taken in their own spaces; the sword comes out of the storm and down where the server says, its
 * upper blade never in a shot, smooth from frame to frame; her camera's shots move smoothly within each shot (cutting only
 * where the videos cut); its outline is the texture's.
 */
class FallingSwordTest {
    static final Anim CLIP=FallingSword.CLIP;
    static StandClient.Cast cast(){return new StandClient.Cast(UUID.randomUUID(),UUID.randomUUID(),new net.minecraft.util.Identifier("minecraft","overworld"),IsosangRules.SKILL,IsosangRules.ULTIMATE,0,0,0,new Vec3d(10,64,-5),30,List.of());}

    @Test void itRunsFromTheStanceBackToIt(){
        var stance=IsosangSlash.stance();
        var first=CLIP.frame(0);var last=CLIP.frame(CLIP.length());
        for(int j=0;j<6;j++){assertTrue(turn(first,stance,j)<.5,"G starts in the stance");assertTrue(turn(last,stance,j)<.5,"G ends in the stance");}
        assertEquals(IsosangRules.LENGTH[IsosangRules.ULTIMATE]/20f,CLIP.length(),1e-6);
        assertSame(CLIP,IsosangSlash.hit(IsosangRules.ULTIMATE),"its swing is drawn with its own clip");
        assertTrue(IsosangClient.life(IsosangRules.ULTIMATE)>=IsosangRules.G_CLEAR,"drawn until the dust has cleared");
    }
    @Test void noJointJumpsOrFlips(){
        double h=1/960.0;
        for(double t=h;t+2*h<=CLIP.length();t+=h){
            var p0=CLIP.frame((float)(t-h));var p1=CLIP.frame((float)t);var p2=CLIP.frame((float)(t+h));var p3=CLIP.frame((float)(t+2*h));
            for(int j=0;j<6;j++){
                double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"joint "+j+" jumps "+now+" deg at "+t);
            }
        }
        for(double t=0;t+1/60.0<=CLIP.length();t+=1/60.0)
            for(int j=0;j<6;j++)assertTrue(turn(CLIP.frame((float)t),CLIP.frame((float)(t+1/60.0)),j)<60,"joint "+j+" flips at "+t);
    }
    @Test void itFacesTheFrontAndKeepsHerFeetDown(){
        for(float t=0;t<=CLIP.length();t+=1/120f){
            float yaw=MathHelper.wrapDegrees(CLIP.frame(t).yaw());
            assertTrue(Math.abs(yaw)<=20,"G turns "+yaw+" deg away from the front at "+t);
            double[] f=IsosangSlash.feet(CLIP.frame(t));
            assertTrue(f[0]>-.02&&f[1]>-.02,"a foot in the ground at "+t+": "+f[0]+" "+f[1]);
            assertTrue(Math.min(f[0],f[1])<.05,"no foot down at "+t);
        }
    }
    /** Where {@code p} is in a shot's frame (16:9): {x, y} from -1 to 1 across and up, or null behind it. */
    static double[] inFrame(FallingSword.Shot s,Vec3d p){
        Vec3d f=s.look().subtract(s.eye()).normalize(),r=f.crossProduct(new Vec3d(0,1,0)).normalize(),u=r.crossProduct(f);
        Vec3d d=p.subtract(s.eye());double z=d.dotProduct(f);if(z<=0)return null;
        double t=Math.tan(Math.toRadians(s.fov()/2));
        return new double[]{d.dotProduct(r)/z/(t*16/9.0),d.dotProduct(u)/z/t};
    }
    @Test void itsShotsAreTakenInTheirOwnSpaces(){
        // (2.83.0, "컷씬은 그냥 다른 공간 보여줘서"): black round her hand, a pale sky behind her face, the storm, dusk; the world once the game is back
        var c=cast();
        assertEquals(GStage.BLACK,FallingSword.space(c,.5f));assertEquals(GStage.GREY,FallingSword.space(c,1.2f));
        assertEquals(GStage.STORM,FallingSword.space(c,2f));assertEquals(GStage.DUSK,FallingSword.space(c,2.9f));
        assertEquals(GStage.DUSK,FallingSword.space(c,IsosangRules.G_BACK-.01f));assertEquals(GStage.NONE,FallingSword.space(c,IsosangRules.G_BACK));
    }
    @Test void theSwordComesOutOfTheStormDownAndStrikesWhereTheServerSays(){
        var c=cast();
        assertNull(FallingSword.blade(c,1),"not in the shots of her hand and face");
        // in the storm: a little right of the sky shot's middle and above it, its point toward the camera below its blade,
        // its blade lost in the eye past LOST_TO
        var sky=FallingSword.blade(c,2.2f);var shot=FallingSword.shot(c,2.2f);
        double[] at=inFrame(shot,sky.at(0,3,0)),tip=inFrame(shot,sky.point());
        assertNotNull(at);assertTrue(at[0]>0&&at[0]<.5&&at[1]>-.2&&at[1]<.7,"the sword's lower part a little right of the middle: "+at[0]+" "+at[1]);
        assertNotNull(tip);assertTrue(tip[1]<at[1],"its point below its blade: "+tip[1]+" "+at[1]);
        assertEquals(1,sky.shown(1),1e-6);assertEquals(0,sky.shown(FallingSword.LOST_TO),1e-6);
        // her head left of the middle at its height, her feet out of the frame below
        double[] head=inFrame(shot,FallingSword.head(c.origin(),c.yaw(),2.2f)),foot=inFrame(shot,c.origin());
        assertNotNull(head);assertTrue(head[0]<-.1&&Math.abs(head[1])<.25,"her head left of the middle at its height: "+head[0]+" "+head[1]);
        assertTrue(foot==null||foot[1]<-1,"her feet in the frame");
        // struck: its point bites G_SINK into the ground where it strikes (the strike as far ahead as the rules say)
        var struck=FallingSword.blade(c,IsosangRules.G_STRIKE+.5f);
        Vec3d s=FallingSword.strike(c);
        assertEquals(IsosangRules.G_AHEAD,s.subtract(c.origin()).horizontalLength(),1e-6);
        assertEquals(-IsosangRules.G_SINK,struck.point().subtract(s).dotProduct(struck.axis()),.05);
        assertTrue(struck.point().y<s.y,"its point is in the ground");
        // coming down and stuck: its point in the frame, its top (where its guard would be) never
        for(float t=FallingSword.S_FALL;t<IsosangRules.G_BACK;t+=1/20f){
            var b=FallingSword.blade(c,t);var sh=FallingSword.shot(c,t);
            double[] tipAt=inFrame(sh,b.at(0,Math.max(.2,IsosangRules.G_SINK+.3),0)),top=inFrame(sh,b.at(0,14,0));
            assertNotNull(tipAt);assertTrue(Math.abs(tipAt[0])<1&&Math.abs(tipAt[1])<1,"its point out of the frame at "+t);
            assertTrue(top==null||top[1]>1,"its upper blade in the frame at "+t);
        }
        // smooth from frame to frame (a 60 fps frame) within each shot, its jolt as it bites aside
        for(float t=FallingSword.S_SKY;t+1/60f<IsosangRules.G_BACK;t+=1/240f){
            if(t<FallingSword.S_FALL&&t+1/60f>=FallingSword.S_FALL)continue;
            var a=FallingSword.blade(c,t);var b=FallingSword.blade(c,t+1/60f);
            double step=a.point().distanceTo(b.point());
            boolean falling=t>=FallingSword.S_FALL&&t<IsosangRules.G_STRIKE;
            assertTrue(step<(falling?1.2:.5),"the sword jumps "+step+" blocks at "+t);
            assertTrue(a.axis().dotProduct(b.axis())>Math.cos(Math.toRadians(3)),"the sword turns sharply at "+t);
        }
        assertNull(FallingSword.blade(c,IsosangRules.G_BACK),"gone once the game is back");
    }
    @Test void herCameraMovesSmoothlyWithinEachShot(){
        var c=cast();
        float[] cuts={FallingSword.S_FACE,FallingSword.S_SKY,FallingSword.S_FALL};
        for(float t=0;t+1/60f<IsosangRules.G_BACK;t+=1/240f){
            boolean cut=false;for(float k:cuts)if(t<k&&t+1/60f>=k)cut=true;
            if(cut)continue;
            var a=FallingSword.shot(c,t);var b=FallingSword.shot(c,t+1/60f);
            double step=a.eye().distanceTo(b.eye());
            assertTrue(step<.6,"her camera jumps "+step+" blocks at "+t);
            Vec3d da=a.look().subtract(a.eye()).normalize(),db=b.look().subtract(b.eye()).normalize();
            // (the whip round her hand as it sweeps up is quick, as the videos' is: a few frames)
            boolean whip=t>=FallingSword.S_WHIP-1/60f&&t<FallingSword.S_ARM;
            assertTrue(da.dotProduct(db)>Math.cos(Math.toRadians(whip?40:16)),"her camera turns sharply at "+t);
        }
        // the hand's shots have it in view: the eye close to it
        for(float t=.05f;t<FallingSword.S_FACE;t+=.05f)assertTrue(FallingSword.shot(c,t).eye().distanceTo(FallingSword.hand(c.origin(),c.yaw(),t))<1.2,"far from her hand at "+t);
    }
    @Test void itsOutlineIsTheTexturesAndClosesToAPoint(){
        double w=IsosangRules.BLADE_WIDTH/2;
        assertEquals(0,FallingSword.half(0),1e-9);
        assertEquals(w,FallingSword.half(FallingSword.POINT),1e-9);assertEquals(w,FallingSword.half(IsosangRules.BLADE_LENGTH),1e-9);
        // a point like a shield's: its edges meet at under 110 degrees
        double slope=FallingSword.half(.01)/.01;
        assertTrue(2*Math.toDegrees(Math.atan(slope))<110,"its point is blunt");
        // the texture's outline is drawn by the same numbers (tools/isosang/make_g_sword.py: L, W, P)
        try{
            String py=new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("tools/isosang/make_g_sword.py")),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(py.contains("L, W = "+IsosangRules.BLADE_LENGTH+", "+IsosangRules.BLADE_WIDTH),"the texture's blade is the same size");
            assertTrue(py.contains("P = "+FallingSword.POINT)&&py.contains("(1 - u) ** 1.8"),"the texture's outline is the same");
        }catch(java.io.IOException e){fail(e);}
    }
}
