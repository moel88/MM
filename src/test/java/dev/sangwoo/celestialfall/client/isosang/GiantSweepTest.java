package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's R as drawn (2.81.0): the body's motion (from the stance and back to it, facing the front, no joint jumping
 * or flipping between frames, a little spring off the ground as the giant sword forms and down into a crouch as it
 * sweeps, never a foot in the ground, her blade trailing her arm); the giant sword where the server's sweep is (its
 * pommel where it turns, its point as far out as it reaches, round the front as the server strikes there), and its
 * crescent swept on with it.
 */
class GiantSweepTest {
    static final Anim CLIP=GiantSweep.CLIP;

    @Test void itRunsAsLongAsTheServerSaysFromTheStanceBackToIt(){
        var stance=IsosangSlash.stance();
        var first=CLIP.frame(0);var last=CLIP.frame(CLIP.length());
        for(int j=0;j<6;j++){assertTrue(turn(first,stance,j)<.5,"R starts in the stance");assertTrue(turn(last,stance,j)<.5,"R ends in the stance");}
        assertEquals(0,last.fw(),1e-6);assertEquals(0,last.up(),1e-6);assertEquals(0,MathHelper.wrapDegrees(last.yaw()),1e-3);
        assertEquals(IsosangRules.LENGTH[IsosangRules.SWEEP]/20f,CLIP.length(),1e-6);
        assertTrue(IsosangRules.HOLD[IsosangRules.SWEEP]<IsosangRules.LENGTH[IsosangRules.SWEEP],"it lets them walk a moment before its motion is over");
        assertSame(CLIP,IsosangSlash.hit(IsosangRules.SWEEP),"its swing is drawn with its own clip");
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
            for(int j=0;j<6;j++)assertTrue(turn(CLIP.frame((float)t),CLIP.frame((float)(t+1/60.0)),j)<125,"joint "+j+" flips at "+t);
    }
    @Test void itFacesTheFront(){
        for(double t=0;t<=CLIP.length();t+=1/240.0){
            float yaw=MathHelper.wrapDegrees(CLIP.frame((float)t).yaw());
            assertTrue(Math.abs(yaw)<=32,"R turns "+yaw+" deg away from the front at "+t);
        }
    }
    @Test void itSpringsUpAsTheSwordFormsAndNeverPutsAFootInTheGround(){
        for(float t=0;t<=CLIP.length();t+=1/120f){
            double[] f=IsosangSlash.feet(CLIP.frame(t));
            assertTrue(f[0]>-.02&&f[1]>-.02,"a foot in the ground at "+t+": "+f[0]+" "+f[1]);
            // on the ground until it springs and once it has come down
            if(t<.14f||t>.66f)assertTrue(Math.min(f[0],f[1])<.05,"no foot on the ground at "+t+": "+f[0]+" "+f[1]);
        }
        // in the air as the giant sword hangs (the video from behind: her feet well off the ground)
        for(float t=.3f;t<=.46f;t+=1/120f){double[] f=IsosangSlash.feet(CLIP.frame(t));assertTrue(Math.min(f[0],f[1])>.2,"off the ground at "+t+": "+f[0]+" "+f[1]);}
        // down in a crouch as it sweeps past
        assertTrue(CLIP.frame(.75f).up()<-.25,"crouched after the sweep: "+CLIP.frame(.75f).up());
    }
    @Test void herBladeSwingsRoundWithTheGiantSwordFromTheRightToTheLeft(){
        // her sword points out to the right as the giant sword hangs, and ends out at the left as it has swept by
        var hang=IsosangSlash.blade(Vec3d.ZERO,0,IsosangRules.SWEEP,.4f)[1];
        var end=IsosangSlash.blade(Vec3d.ZERO,0,IsosangRules.SWEEP,.8f)[1];
        // (KagenoClips' frame, facing +z: the character's right is -x)
        assertTrue(hang.x<-.5,"out to the right as it hangs: "+hang);
        assertTrue(end.x>.5,"out to the left once it has swept by: "+end);
    }
    @Test void theGiantSwordTurnsRoundItsPommelWhereTheServerSweeps(){
        var cast=new dev.sangwoo.celestialfall.client.StandClient.Cast(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),null,IsosangRules.SKILL,IsosangRules.SWEEP,0,0,0,Vec3d.ZERO,0,java.util.List.of());
        assertNull(GiantSweep.giant(cast,.1f),"not yet: it forms out of the lance of light");
        Vec3d pivot=GiantSweep.pivot(cast);
        assertEquals(IsosangRules.SWEEP_PIVOT_UP,pivot.y,1e-9);assertEquals(IsosangRules.SWEEP_PIVOT_FW,pivot.z,1e-9);
        for(float t=.3f;t<.83f;t+=.01f){
            var g=GiantSweep.giant(cast,t);assertNotNull(g,"shown at "+t);
            Vec3d pommel=g.at(SpiritSword.POMMEL),point=g.point();
            assertTrue(pommel.distanceTo(pivot)<.05,"its pommel where it turns at "+t+": "+pommel);
            double reach=Math.hypot(point.x-pivot.x,point.z-pivot.z);
            assertEquals(IsosangRules.SWEEP_REACH*Math.cos(Math.toRadians(GiantSweep.TILT)),reach,.02,"its point as far out as the server's reach at "+t);
            assertTrue(point.y>1.4&&point.y<IsosangRules.SWEEP_PIVOT_UP,"its point at a head's height at "+t+": "+point.y);
            if(t>=IsosangRules.SWEEP_START+.03){
                // facing +z (yaw 0): its angle off the front, right + (the world's -x)
                double deg=Math.toDegrees(Math.atan2(-(point.x-pivot.x),point.z-pivot.z));
                assertEquals(IsosangRules.sweepAngle(t),deg,.5,"the server's way round at "+t);
            }
        }
        assertNull(GiantSweep.giant(cast,.9f),"gone into ink once it has swept");
        // it crosses the front as the server strikes there
        var front=GiantSweep.giant(cast,(float)IsosangRules.SWEEP_FRONT);
        assertTrue(front.dir().z>.95,"ahead as it crosses the front: "+front.dir());
    }
    @Test void itsCrescentIsSweptOnWithIt(){
        for(float t=(float)IsosangRules.SWEEP_START;t<=IsosangRules.SWEEP_END;t+=.01f){
            double head=IsosangRules.SWEEP_FROM+(IsosangRules.SWEEP_TO-IsosangRules.SWEEP_FROM)*IsosangSlash.head(GiantSweep.ARC,t);
            assertEquals(IsosangRules.sweepAngle(t),head,1e-3,"the crescent's head with the blade at "+t);
        }
        assertTrue(GiantSweep.ARC.ink0()>IsosangRules.SWEEP_END&&GiantSweep.ARC.eat1()<4.2f,"it turns to ink after the sweep and is gone well within the swing's life");
        // when the blade passes each angle (the ink on the ground lands as it does)
        for(double deg=IsosangRules.SWEEP_TO;deg<=IsosangRules.SWEEP_FROM;deg+=10)assertEquals(deg,IsosangRules.sweepAngle(GiantSweep.passes(deg)),.05);
    }
    @Test void theCasterCameraDrawsBackAndComesInSmoothly(){
        assertEquals(0,GiantSweep.pull(0),1e-9);assertEquals(0,GiantSweep.pull(1.46f),1e-9);
        assertTrue(GiantSweep.pull(.6f)>1.4,"drawn back as it sweeps");
        for(float t=0;t<1.5f;t+=1/60f)assertTrue(Math.abs(GiantSweep.pull(t+1/60f)-GiantSweep.pull(t))<.1,"no jump at "+t);
    }
}
