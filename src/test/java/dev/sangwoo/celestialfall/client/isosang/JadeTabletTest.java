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
 * ground; the tablet comes out of the sky, falls and strikes where the server says, smooth from frame to frame; her
 * camera's shots move smoothly within each shot (cutting only where the videos cut); its outline is the texture's.
 */
class JadeTabletTest {
    static final Anim CLIP=JadeTablet.CLIP;
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
    @Test void theTabletComesOutOfTheSkyFallsAndStrikesWhereTheServerSays(){
        var c=cast();
        assertNull(JadeTablet.tablet(c,1),"not there before it shows");
        var hang=JadeTablet.tablet(c,2.2f);
        assertTrue(hang.point().y-c.origin().y>IsosangRules.G_SKY_UP-IsosangRules.TABLET_HEIGHT,"high in the sky as it hangs");
        // struck: its point bites G_SINK into the ground where it strikes (the strike as far ahead as the rules say)
        var struck=JadeTablet.tablet(c,IsosangRules.G_STRIKE+.5f);
        Vec3d s=JadeTablet.strike(c);
        assertEquals(IsosangRules.G_AHEAD,s.subtract(c.origin()).horizontalLength(),1e-6);
        assertEquals(IsosangRules.G_SINK,struck.point().distanceTo(s),.05);
        assertTrue(struck.point().y<s.y,"its point is in the ground");
        // smooth from frame to frame (a 60 fps frame), its jolt as it bites aside
        for(float t=JadeTablet.SHOW0+.01f;t+1/60f<JadeTablet.CRUMBLE1;t+=1/240f){
            var a=JadeTablet.tablet(c,t);var b=JadeTablet.tablet(c,t+1/60f);if(a==null||b==null)continue;
            double step=a.point().distanceTo(b.point());
            boolean falling=t>=IsosangRules.G_FALL-1/60f&&t<IsosangRules.G_STRIKE;
            assertTrue(step<(falling?3.2:.7),"the tablet jumps "+step+" blocks at "+t);
            assertTrue(a.axis().dotProduct(b.axis())>Math.cos(Math.toRadians(6)),"the tablet turns sharply at "+t);
        }
        // it has gone once it crumbled
        assertNull(JadeTablet.tablet(c,JadeTablet.CRUMBLE1+.01f));
    }
    @Test void herCameraMovesSmoothlyWithinEachShot(){
        var c=cast();
        float[] cuts={JadeTablet.S_FACE,JadeTablet.S_SKY,JadeTablet.S_FALL,JadeTablet.S_STRIKE};
        for(float t=0;t+1/60f<IsosangRules.G_BACK;t+=1/240f){
            boolean cut=false;for(float k:cuts)if(t<k&&t+1/60f>=k)cut=true;
            if(cut)continue;
            var a=JadeTablet.shot(c,t);var b=JadeTablet.shot(c,t+1/60f);
            double step=a.eye().distanceTo(b.eye());
            assertTrue(step<.6,"her camera jumps "+step+" blocks at "+t);
            Vec3d da=a.look().subtract(a.eye()).normalize(),db=b.look().subtract(b.eye()).normalize();
            assertTrue(da.dotProduct(db)>Math.cos(Math.toRadians(16)),"her camera turns sharply at "+t);
        }
        // the hand's shots have it in view: the eye close to it
        for(float t=.05f;t<JadeTablet.S_FACE;t+=.05f)assertTrue(JadeTablet.shot(c,t).eye().distanceTo(JadeTablet.hand(c.origin(),c.yaw(),t))<1.2,"far from her hand at "+t);
    }
    @Test void itsOutlineIsTheTexturesAndClosesToAPoint(){
        assertEquals(0,JadeTablet.half(0),1e-9);
        assertEquals(IsosangRules.TABLET_WIDTH/2,JadeTablet.half(IsosangRules.TABLET_HEIGHT*.55),1e-9);
        assertEquals(0,JadeTablet.half(IsosangRules.TABLET_HEIGHT),1e-9);
        // a sharp point (its edges meet at under 110 degrees), a round top
        double slope=JadeTablet.half(.01)/.01;
        assertTrue(2*Math.toDegrees(Math.atan(slope))<110,"its point is blunt");
        // the texture's outline is drawn by the same numbers (tools/isosang/make_tablet.py: H, W)
        try{
            String py=new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("tools/isosang/make_tablet.py")),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(py.contains("H, W = "+IsosangRules.TABLET_HEIGHT+", "+IsosangRules.TABLET_WIDTH),"the texture's tablet is the same size");
            assertTrue(py.contains("YB, YM = .40 * H, .70 * H")&&py.contains("(1 - u) ** 1.8"),"the texture's outline is the same");
        }catch(java.io.IOException e){fail(e);}
    }
}
