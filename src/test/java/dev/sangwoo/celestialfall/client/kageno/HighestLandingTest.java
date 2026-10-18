package dev.sangwoo.celestialfall.client.kageno;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HighestLandingTest {
    @Test void freeHandStaysOnTheFloorDuringBraking(){
        for(float t=HighestSkill.LAND;t<=HighestSkill.DRIVE;t+=1/120f){
            var f=HighestSkill.CLIP.frame(t);var p=R6.poseOf(f);
            double a=Math.toRadians(f.pitch());
            double handY=f.up()+1.05+.325*Math.cos(a)
                +.625*(p.lArm.up()*Math.cos(a)-p.lArm.fw()*Math.sin(a));
            assertEquals(.07,handY,.025,"free hand at "+t);
        }
    }
    @Test void retreatImmediatelyReversesIntoAContinuousForwardDrive(){
        var clip=HighestSkill.CLIP;
        assertEquals(5.0,clip.frame(HighestSkill.LAND).fw()-clip.frame(HighestSkill.BRAKE).fw(),1e-5);
        assertTrue(clip.frame(3.47f).fw()>clip.frame(HighestSkill.BRAKE).fw()+6.6);
        assertTrue(clip.frame(HighestSkill.DRIVE+.02f).fw()>clip.frame(HighestSkill.BRAKE).fw());
        assertTrue(HighestSkill.BRAKE-HighestSkill.LAND>.45,"retreat must remain readable before forward drive");
        var previous=clip.frame(HighestSkill.LAND);
        for(float t=HighestSkill.LAND+1/120f;t<HighestSkill.END;t+=1/120f){
            var next=clip.frame(t);
            assertTrue(Math.abs(next.fw()-previous.fw())<.35,"ground teleport at "+t);
            if(t<=HighestSkill.BRAKE)assertTrue(next.fw()<=previous.fw()+1e-6,"moving forward during retreat at "+t);
            previous=next;
        }
    }
    @Test void retreatRecedesInCameraSpaceInsteadOfZoomingTowardTheViewer(){
        var skill=new HighestSkill();double previousScale=Double.POSITIVE_INFINITY,initialDepth=0,lastDepth=0;
        for(float t=HighestSkill.LAND;t<=HighestSkill.DRIVE;t+=1/240f){
            var r=HighestSkill.CLIP.frame(t);var s=skill.shot(t);
            double x=s.lookFw()-s.eyeFw(),y=s.lookUp()-s.eyeUp(),z=s.lookRt()-s.eyeRt();
            double len=Math.sqrt(x*x+y*y+z*z);
            double depth=((r.fw()-s.eyeFw())*x+(.8-s.eyeUp())*y-s.eyeRt()*z)/len;
            double projectedScale=1/(depth*Math.tan(Math.toRadians(s.fov()/2)));
            assertTrue(projectedScale<=previousScale+1e-6,"dolly/zoom makes backward motion appear forward at "+t);
            assertEquals(50,s.fov(),1e-5,"retreat lens stays fixed");
            if(initialDepth==0)initialDepth=depth;lastDepth=depth;previousScale=projectedScale;
        }
        assertTrue(lastDepth>initialDepth+2,"backward travel must remain visible as increasing depth");
    }
    @Test void groundSequenceFacesForwardWithAnObliqueCamera(){
        var skill=new HighestSkill();
        for(float t=HighestSkill.LAND;t<HighestSkill.END;t+=1/120f){
            var f=HighestSkill.CLIP.frame(t);var shot=skill.shot(t);
            assertEquals(0,f.yaw(),1e-5,"body heading at "+t);
            assertEquals(0,f.joints().joint[0][1],1e-5,"head heading at "+t);
            assertTrue(shot.eyeUp()<1.35,"low camera at "+t);
            double forward=shot.eyeFw()-f.fw(),side=shot.eyeRt()-f.rt();
            assertTrue(side>0,"camera must stay on the reversed side at "+t);
            double azimuth=Math.toDegrees(Math.atan2(Math.abs(side),forward));
            assertTrue(azimuth>25&&azimuth<58,"oblique view at "+t+": "+azimuth);
        }
    }
    @Test void finisherReversesTheCameraSideWithoutTurningFrontal(){
        var skill=new HighestSkill();
        for(float t=HighestSkill.SHOT5;t<HighestSkill.END;t+=1/120f){
            var s=skill.shot(t);
            double azimuth=Math.toDegrees(Math.atan2(s.eyeRt()-s.lookRt(),s.eyeFw()-s.lookFw()));
            assertEquals(38,azimuth,1e-5,"same oblique angle, opposite side at "+t);
            assertEquals(70,s.fov(),1e-5);
            assertEquals(1.08,s.eyeUp(),1e-5);
        }
    }
    @Test void downswingAndFinishKeepBothLegsVerticalAndFeetAtFloorHeight(){
        for(float t=HighestSkill.DOWN;t<=HighestSkill.END;t+=1/240f){
            var f=HighestSkill.CLIP.frame(t);var p=R6.poseOf(f);
            for(var leg:new R6.Dir[]{p.rLeg,p.lLeg}){
                assertEquals(0,leg.rt(),1e-5,"no sideways spread at "+t);
                assertEquals(0,leg.fw(),1e-5,"no split stance at "+t);
                assertEquals(-1,leg.up(),1e-5,"vertical leg at "+t);
                assertEquals(0,f.up()+1.05-.30+.75*leg.up(),1e-5,"boot on floor at "+t);
            }
        }
    }
}
