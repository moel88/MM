package dev.sangwoo.celestialfall.client.vfx;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Chunks of floor and the floor's heave: closed functions of time, smooth at any frame rate. */
class FxSolidsTest {
    @Test void chunksFlyBounceAndComeToRestOnTheFloor(){
        Vec3d p0=new Vec3d(0,64.3,0),v0=new Vec3d(3,7,1);double ground=64.1,h=1/240.0,top=0;int bounces=0;
        Vec3d prev=FxSolids.flight(p0,v0,ground,0),before=null;
        assertEquals(0,prev.distanceTo(p0),1e-9);
        for(double t=h;t<4;t+=h){
            Vec3d p=FxSolids.flight(p0,v0,ground,t);
            assertTrue(p.y>=ground-1e-9,"below the floor at "+t);
            assertTrue(p.distanceTo(prev)<v0.length()*h*1.6,"jumps at "+t);
            if(before!=null&&prev.y-before.y<0&&p.y-prev.y>0)bounces++;
            top=Math.max(top,p.y);before=prev;prev=p;
        }
        assertTrue(bounces>=1&&bounces<=4,"bounces "+bounces);
        assertTrue(top>p0.y+.8,"thrown up");
        assertEquals(ground,prev.y,1e-9);
        assertEquals(0,FxSolids.flight(p0,v0,ground,6).distanceTo(prev),1e-9,"and stays at rest");
    }
    @Test void aChunkStartedBelowTheFloorIsLiftedOntoIt(){
        assertTrue(FxSolids.flight(new Vec3d(0,63,0),new Vec3d(0,4,0),64,0).y>=64);
    }
    @Test void theHeaveRunsOutwardAndLeavesTheFloorFlatBehindIt(){
        assertEquals(0,FxSolids.wave(3,0,.55,26,1.3),1e-12);
        double early=crest(.1),late=crest(.3);
        assertTrue(late>early+3,"crest at "+early+" then "+late);
        assertEquals(0,FxSolids.wave(2,1,.55,26,1.3),1e-12,"long behind the front");
        for(double t=0;t<1;t+=1/240.0)for(double d=0;d<12;d+=.5)assertTrue(FxSolids.wave(d,t,.55,26,1.3)<=.55+1e-9);
    }
    static double crest(double t){
        double best=-1,at=0;
        for(double d=0;d<20;d+=.05){double h=FxSolids.wave(d,t,.55,26,1.3);if(h>best){best=h;at=d;}}
        return at;
    }
}
