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
    /** Where {@code p} is in a shot's frame (the videos', FallingSword.ASPECT wide): {x, y} from -1 to 1 across and up, or null behind it. */
    static double[] inFrame(FallingSword.Shot s,Vec3d p){
        Vec3d f=s.look().subtract(s.eye()).normalize(),r=f.crossProduct(new Vec3d(0,1,0)).normalize(),u=r.crossProduct(f);
        Vec3d d=p.subtract(s.eye());double z=d.dotProduct(f);if(z<=0)return null;
        double t=Math.tan(Math.toRadians(s.fov()/2));
        return new double[]{d.dotProduct(r)/z/(t*FallingSword.ASPECT),d.dotProduct(u)/z/t};
    }
    /** Where {@code p} is in the picture of a camera's frame (its own right and up: turned as the picture is), as the videos' frames are measured: 0 to 1 across and down. */
    static double[] onScreen(FallingSword.Frame k,double fov,Vec3d p){
        Vec3d d=p.subtract(k.eye());double z=d.dotProduct(k.f());
        double t=Math.tan(Math.toRadians(fov/2));
        return new double[]{(d.dotProduct(k.r())/z/(t*FallingSword.ASPECT)+1)/2,(1-d.dotProduct(k.u())/z/t)/2};
    }
    /**
     * The sword's edges the user drew on the videos' sky shot (2.83.1, "검 그거 아니야": its right edge straight up, its
     * bottom edge to her head, meeting at a corner), at 1.95 s: points along them (across, down the frame).
     */
    static final double[][] RIGHT_EDGE={{.6532,.21},{.6646,.27},{.6679,.33},{.6676,.39},{.6635,.48},{.6581,.54},{.6524,.60}};
    static final double[][] BOTTOM_EDGE={{.42,.5891},{.46,.6114},{.50,.6306},{.54,.6460},{.58,.6570},{.62,.6559}};
    /** How far (of the frame's width) the nearest of the sword's two edges near its point passes from a point on the screen. */
    static double offEdge(FallingSword.Frame k,FallingSword.Blade b,double[] p){
        double best=9;
        for(int s=-1;s<=1;s+=2){
            double[] prev=null;
            for(double y=0;y<=20;y+=.02){
                double[] q=onScreen(k,FallingSword.SKY_FOV,b.at(s*FallingSword.half(y),y,FallingSword.THICK*.5));
                if(prev!=null){
                    double ax=prev[0]*FallingSword.ASPECT,ay=prev[1],bx=q[0]*FallingSword.ASPECT,by=q[1],px=p[0]*FallingSword.ASPECT,py=p[1];
                    double dx=bx-ax,dy=by-ay,l=dx*dx+dy*dy,u=l<1e-12?0:Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/l));
                    best=Math.min(best,Math.hypot(px-ax-u*dx,py-ay-u*dy)/FallingSword.ASPECT);
                }
                prev=q;
            }
        }
        return best;
    }
    /** {@code d} as the generator writes it (.38 for 0.38, -.023 for -0.023, 60 for 60.0). */
    static String fmt(double d){String t=d==Math.rint(d)?Long.toString((long)d):Double.toString(d);return t.startsWith("0.")?t.substring(1):t.startsWith("-0.")?"-"+t.substring(2):t;}
    @Test void itsShotsAreTakenInTheirOwnSpaces(){
        // (2.83.0, "컷씬은 그냥 다른 공간 보여줘서"): black round her hand, a pale sky behind her face, the storm, dusk; the world once the game is back
        var c=cast();
        assertEquals(GStage.BLACK,FallingSword.space(c,.5f));assertEquals(GStage.GREY,FallingSword.space(c,1.2f));
        assertEquals(GStage.STORM,FallingSword.space(c,2f));assertEquals(GStage.DUSK,FallingSword.space(c,2.9f));
        assertEquals(GStage.DUSK,FallingSword.space(c,IsosangRules.G_BACK-.01f));assertEquals(GStage.NONE,FallingSword.space(c,IsosangRules.G_BACK));
        // (2.83.1) as the videos' frames: the pale sky at 1.500 s, the storm behind her face from 1.517 s; the sword alone
        // in the storm at 2.717 s (she is not in it), coming down at 2.733 s
        assertEquals(GStage.GREY,FallingSword.space(c,1.5f));assertEquals(GStage.STORM,FallingSword.space(c,1.517f));
        assertFalse(FallingSword.alone(2.7f));assertTrue(FallingSword.alone(2.717f));
        assertEquals(GStage.STORM,FallingSword.space(c,2.717f));assertEquals(GStage.DUSK,FallingSword.space(c,2.733f));
    }
    @Test void theSwordComesOutOfTheStormDownAndStrikesWhereTheServerSays(){
        var c=cast();
        assertNull(FallingSword.blade(c,1),"not in the shots of her hand and face");
        // in the storm (2.83.1): the giant sword itself, its point at the corner the user marked on the videos' sky shot
        // and its two edges along the lines they drew (its right edge straight up, its bottom edge to her head), at 1.95 s
        var k=FallingSword.skyView(c);
        var sky=FallingSword.blade(c,1.95f);
        assertNotNull(sky,"the sword in the storm");
        double[] tip=onScreen(k,FallingSword.SKY_FOV,sky.point());
        assertEquals(.648,tip[0],.004,"its point across");assertEquals(.653,tip[1],.004,"its point down");
        for(double[] p:RIGHT_EDGE)assertTrue(offEdge(k,sky,p)<.009,"its right edge "+offEdge(k,sky,p)+" off the line drawn at "+p[1]);
        for(double[] p:BOTTOM_EDGE)assertTrue(offEdge(k,sky,p)<.009,"its bottom edge "+offEdge(k,sky,p)+" off the line drawn at "+p[0]);
        // a sword coming down point first: its blade goes up from its point, away from the camera; its face to the camera
        assertTrue(sky.axis().y>.6,"its blade goes up from its point");
        assertTrue(sky.axis().dotProduct(k.f())>0,"its blade goes away from the camera");
        assertTrue(sky.normal().dotProduct(k.eye().subtract(sky.point()))>0,"its face to the camera");
        // nearing over the shot: its point down to .675 and .707 by its end (the videos' frame 174)
        double[] late=onScreen(k,FallingSword.SKY_FOV,FallingSword.blade(c,2.683f).point());
        assertEquals(.675,late[0],.006);assertEquals(.707,late[1],.006);
        assertTrue(FallingSword.blade(c,2.683f).point().distanceTo(k.eye())<sky.point().distanceTo(k.eye()),"it nears");
        // behind her; her head a little right of the videos' (.41 across, .52 down), her feet out of the frame below
        Vec3d headAt=FallingSword.head(c.origin(),c.yaw(),1.95f);
        assertTrue(sky.point().distanceTo(k.eye())>headAt.distanceTo(k.eye()),"the sword behind her");
        double[] head=onScreen(k,FallingSword.SKY_FOV,headAt),foot=onScreen(k,FallingSword.SKY_FOV,c.origin());
        assertEquals(.408,head[0],.03,"her head across");assertEquals(.515,head[1],.04,"her head down");
        assertTrue(foot[1]>1,"her feet in the frame");
        // the clouds take it only on its far side: its point is inside the hole round the storm's eye
        Vec3d eye=FallingSword.stormEye(c,1.95f);
        assertTrue(Math.toDegrees(Math.acos(sky.point().subtract(k.eye()).normalize().dotProduct(eye)))<FallingSword.HOLE,"its point in the hole");
        assertTrue(FallingSword.farSide(c).dotProduct(sky.axis())>0,"the clouds' side is the way it goes away");
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
        // (the pull-back from her face to the sky is three of the videos' frames, as quick as a cut)
        float[] cuts={FallingSword.S_FACE,FallingSword.S_FALL};
        for(float t=0;t+1/60f<IsosangRules.G_BACK;t+=1/240f){
            boolean cut=false;for(float k:cuts)if(t<k&&t+1/60f>=k)cut=true;
            if(cut)continue;
            var a=FallingSword.shot(c,t);var b=FallingSword.shot(c,t+1/60f);
            double step=a.eye().distanceTo(b.eye());
            boolean pull=t>=FallingSword.S_PULL-1/60f&&t<FallingSword.S_PULLED;
            assertTrue(step<(pull?1.6:.6),"her camera jumps "+step+" blocks at "+t);
            Vec3d da=a.look().subtract(a.eye()).normalize(),db=b.look().subtract(b.eye()).normalize();
            // (the whip round her hand as it sweeps up is quick, as the videos' is: a few frames)
            boolean whip=t>=FallingSword.S_WHIP-1/60f&&t<FallingSword.S_ARM;
            assertTrue(da.dotProduct(db)>Math.cos(Math.toRadians(whip||pull?40:16)),"her camera turns sharply at "+t);
        }
        // the hand's shots have it in view: the eye close to it
        for(float t=.05f;t<FallingSword.S_FACE;t+=.05f)assertTrue(FallingSword.shot(c,t).eye().distanceTo(FallingSword.hand(c.origin(),c.yaw(),t))<1.2,"far from her hand at "+t);
    }
    @Test void itsOutlineIsTheTexturesAndClosesToAPoint(){
        double w=IsosangRules.BLADE_WIDTH/2;
        assertEquals(0,FallingSword.half(0),1e-9);
        assertEquals(w,FallingSword.half(FallingSword.POINT),1e-9);assertEquals(w,FallingSword.half(IsosangRules.BLADE_LENGTH),1e-9);
        // a point: its edges meet at under 120 degrees
        double slope=FallingSword.half(.01)/.01;
        assertTrue(2*Math.toDegrees(Math.atan(slope))<120,"its point is blunt");
        // the texture's outline is drawn by the same numbers (tools/isosang/make_g_sword.py: L, W, P)
        try{
            String py=new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("tools/isosang/make_g_sword.py")),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(py.contains("L, W = "+IsosangRules.BLADE_LENGTH+", "+IsosangRules.BLADE_WIDTH),"the texture's blade is the same size");
            assertTrue(py.contains("P = "+FallingSword.POINT)&&py.contains("N = "+FallingSword.ROUND)&&py.contains("(1 - u) ** N"),"the texture's outline is the same");
            // and it carries the user's lines onto the face from the sky shot as FallingSword places it
            assertTrue(py.contains("CAM = ("+fmt(FallingSword.SKY_EYE_FW)+", "+fmt(FallingSword.SKY_EYE_SIDE)+", "+fmt(FallingSword.SKY_EYE_UP)+", "+fmt(FallingSword.SKY_PITCH)+", "
                +fmt(FallingSword.SKY_PAN)+", "+fmt(FallingSword.SKY_ROLL)+", "+fmt(FallingSword.SKY_FOV)+")"),"the same sky camera");
            assertTrue(py.contains("POSE = ("+fmt(FallingSword.SKY_TIP_DEPTH)+", "+fmt(FallingSword.SKY_AXIS_UP)+", "+fmt(FallingSword.SKY_AXIS_RIGHT)+")"),"the same pose");
        }catch(java.io.IOException e){fail(e);}
    }
}
