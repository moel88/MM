package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R's moonlight and starlight (2.102.0): the sword's stars lit in its shape where it is called and given up to it as it
 * forms; lightning over its edges only while it hangs; the guide on the ground drawn before it sweeps and each part gone as
 * its point passes; the moon's trace left where its point passes; sparks flung out and falling; petals drifting down; the
 * stars in the ink only over the darkened ground ahead and gone as it dries; no impact frame but its shake kept (2.102.1);
 * all of it gone within its swing's life.
 */
class SweepLightsTest {
    static final Vec3d PIVOT=new Vec3d(0,IsosangRules.SWEEP_PIVOT_UP,IsosangRules.SWEEP_PIVOT_FW);
    static final float START=(float)IsosangRules.SWEEP_START,FRONT=(float)IsosangRules.SWEEP_FRONT,END=(float)IsosangRules.SWEEP_END;

    @Test void theSwordsStarsAreLitInItsShapePommelToPointAndGivenUpAsItForms(){
        // facing +z (yaw 0); its pommel's star on the pivot, its point's where the sword's point will be as it forms
        assertTrue(SweepLights.star(PIVOT,0,0).distanceTo(PIVOT)<1e-6,"its pommel's star where its pommel hangs");
        var cast=new dev.sangwoo.celestialfall.client.StandClient.Cast(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),null,IsosangRules.SKILL,IsosangRules.SWEEP,0,0,0,Vec3d.ZERO,0,java.util.List.of());
        var formed=GiantSweep.giant(cast,GiantSweep.FORM0+.02f);assertNotNull(formed);
        assertTrue(SweepLights.star(PIVOT,0,6).distanceTo(formed.point())<.05,"its point's star where its point forms: "+SweepLights.star(PIVOT,0,6)+" "+formed.point());
        // the guard's two points level with each other, either side of the gem
        Vec3d l=SweepLights.star(PIVOT,0,2),g=SweepLights.star(PIVOT,0,3),r=SweepLights.star(PIVOT,0,4);
        assertEquals(l.y,r.y,1e-6,"the guard's points level");
        assertEquals(0,l.add(r).multiply(.5).subtract(SweepLights.onSword(PIVOT,0,SweepLights.CON0,0,SpiritSword.GUARD_POINT)).length(),1e-6,"either side of the blade's line");
        assertTrue(l.distanceTo(r)>2,"as wide as the sword's guard: "+l.distanceTo(r));
        assertTrue(g.distanceTo(PIVOT)>1&&g.distanceTo(PIVOT)<l.distanceTo(PIVOT)+1,"the gem past the grip");
        // lit pommel to point, all before the flash; given up to the sword as it forms
        for(int k=0;k<SweepLights.STARS.length;k++){
            double along=SweepLights.STARS[k][1];
            for(int j=0;j<SweepLights.STARS.length;j++)if(SweepLights.STARS[j][1]>along+.3)assertTrue(SweepLights.lit(j)>SweepLights.lit(k),"lit from its pommel to its point: "+k+" before "+j);
            assertTrue(SweepLights.lit(k)>=.02f&&SweepLights.lit(k)+.025f<=GiantSweep.FLASH,"lit before the flash: "+k);
            assertEquals(0,SweepLights.starShown(k,.01f),1e-6);assertTrue(SweepLights.starShown(k,GiantSweep.FLASH)>.99f);
            assertEquals(0,SweepLights.starShown(k,GiantSweep.FORM1+.05f),1e-6,"given up to the sword once it has formed");
        }
        // each flares as the lance's head runs through it, the pommel's first and the point's last
        float pommel=peak(0),point=peak(6);
        assertTrue(pommel>=GiantSweep.LANCE0-.01f&&pommel<point&&point<=GiantSweep.LANCE1+.01f,"the lance runs through them: "+pommel+" "+point);
    }
    static float peak(int k){float best=-1,at=0;for(float t=.1f;t<.35f;t+=.001f){float f=SweepLights.flare(k,t);if(f>best){best=f;at=t;}}assertTrue(best>.9f,"flares: "+k+" "+best);return at;}

    @Test void lightningCreepsOverItsEdgesOnlyWhileItHangs(){
        assertEquals(0,SweepLights.charge(.25f),1e-6,"none before it hangs");
        assertEquals(0,SweepLights.charge(FRONT+.08f),1e-6,"gone once it has crossed the front");
        assertTrue(SweepLights.charge(START-.01f)>SweepLights.charge(.36f),"gathering until it goes");
        for(float t=0;t<1.2f;t+=1/60f)assertTrue(Math.abs(SweepLights.charge(t+1/60f)-SweepLights.charge(t))<.25f,"no jump at "+t);
        for(int q=0;q<200;q++)for(int s=0;s<SweepLights.BOLTS;s++){
            double[] sp=SweepLights.boltSpan(s,q);
            assertTrue(sp[0]>=SpiritSword.BLADE_BASE&&sp[1]<=SpiritSword.TIP-.2&&sp[1]>sp[0]+.4,"on the blade: "+sp[0]+".."+sp[1]);
            for(int e=0;e<9;e++){double off=Math.abs(SweepLights.boltOff(s*131+q*17+2021+e));assertTrue(off>1&&off<1.5,"its ends just outside the edges: "+off);}
        }
    }
    @Test void theGuideIsDrawnBeforeItSweepsAndEachPartGoesAsItsPointPasses(){
        assertEquals(1,SweepLights.guideDrawn(START-.02f),1e-6,"all drawn before the sweep begins");
        assertEquals(0,SweepLights.guideDrawn(SweepLights.GUIDE0),1e-6);
        // as far out as the ring of ink laid under its point (GiantSweep.RING), on the ground
        for(double deg=IsosangRules.SWEEP_TO;deg<=IsosangRules.SWEEP_FROM;deg+=15){
            Vec3d p=SweepLights.guidePoint(deg);
            assertEquals(GiantSweep.RING.radius(),Math.hypot(p.x-IsosangRules.SWEEP_PIVOT_FW,p.z),1e-9);assertTrue(p.y<.1);
            float pass=GiantSweep.passes(deg);
            assertTrue(SweepLights.guideShown(deg,START-.03f)>.9f,"shown at "+deg+" before the sweep");
            assertTrue(SweepLights.guideShown(deg,pass-.03f)>.9f,"still shown just before its point passes "+deg);
            assertEquals(0,SweepLights.guideShown(deg,pass+.07f),1e-6,"gone once its point has passed "+deg);
        }
        // drawn round from its back: a part further round shows later
        assertTrue(SweepLights.guideShown(100,.36f)>0&&SweepLights.guideShown(-90,.36f)==0,"drawn round from its back");
    }
    @Test void theMoonsTraceIsLeftWhereItsPointPasses(){
        for(int k=0;k<SweepLights.TRACE;k++){
            float born=SweepLights.traceBorn(k);double deg=SweepLights.traceDeg(k);
            assertTrue(deg<=IsosangRules.SWEEP_FROM&&deg>=IsosangRules.SWEEP_TO);
            assertEquals(deg,IsosangRules.sweepAngle(born),.1,"left as its point passes: "+k);
            Vec3d p=SweepLights.traceAt(PIVOT,0,k,0),q=SweepLights.traceAt(PIVOT,0,k,.6f);
            double r=Math.hypot(p.x-PIVOT.x,p.z-PIVOT.z);
            assertTrue(r>IsosangRules.SWEEP_REACH*.8&&r<IsosangRules.SWEEP_REACH+.3,"out where its point is: "+r);
            assertTrue(q.y<p.y,"drifting down: "+k);
        }
        // spread over the way round: no great gaps
        for(int k=1;k<SweepLights.TRACE;k++)assertTrue(SweepLights.traceDeg(k-1)-SweepLights.traceDeg(k)<7.5,"spread round: "+k);
    }
    @Test void sparksFlyOutAndFallAndPetalsDriftDown(){
        for(int i=0;i<SweepLights.SPARKS;i++){
            float born=SweepLights.sparkBorn(i);assertTrue(born>=START&&born<=END,"off the blade as it sweeps: "+born);
            Vec3d a=SweepLights.sparkAt(PIVOT,0,i,0),b=SweepLights.sparkAt(PIVOT,0,i,.12f),c=SweepLights.sparkAt(PIVOT,0,i,SweepLights.sparkLife(i));
            assertTrue(Math.hypot(b.x-PIVOT.x,b.z-PIVOT.z)>Math.hypot(a.x-PIVOT.x,a.z-PIVOT.z),"flung out: "+i);
            assertTrue(c.y<b.y,"then falling: "+i);
        }
        for(int i=0;i<SweepLights.PETALS;i++){
            float born=SweepLights.petalBorn(i);assertEquals(SweepLights.petalDeg(i),IsosangRules.sweepAngle(born-.02f),.1,"shed as the blade passes: "+i);
            double[] a=SweepLights.petal(PIVOT,0,i,0),b=SweepLights.petal(PIVOT,0,i,1);
            assertTrue(b[1]<a[1]-.3,"drifting down: "+i);
        }
    }
    @Test void theStarsInTheInkLieOverTheDarkenedGroundAheadUntilItDries(){
        for(int k=0;k<SweepLights.GROUND_STARS;k++){
            double[] g=SweepLights.groundStar(k);
            assertTrue(Math.abs(g[0])<=SweepLights.GROUND_ARC&&g[1]>=SweepLights.GROUND_NEAR&&g[1]<=SweepLights.GROUND_FAR,"ahead, within the darkened ground: "+g[0]+" "+g[1]);
            float pass=GiantSweep.passes(g[0]);
            assertEquals(0,SweepLights.groundShown(k,pass),1e-6,"not before the blade passes over it");
            assertTrue(SweepLights.groundShown(k,pass+.3f)>.9f,"out once it has");
            assertEquals(0,SweepLights.groundShown(k,START+.05f+SweepLights.GROUND_DRY1),1e-6,"gone as the ink dries");
        }
    }
    @Test void itStillShakesAsItCrossesTheFrontAndItsShockwaveRunsOut(){
        // (2.102.1, user request: "화면 효과는 빼줘 R도 빼고 T도 빼", then "흔들림은 안 빼도 돼") its impact frame went
        // (SweepLights.frame only lights the world) but its shake stays: a little as the sword forms, most as it crosses the front
        var slash=new IsosangSlash();
        var cast=new dev.sangwoo.celestialfall.client.StandClient.Cast(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),null,IsosangRules.SKILL,IsosangRules.SWEEP,0,0,0,Vec3d.ZERO,0,java.util.List.of());
        float formed=0,crossed=0;
        for(float t=0;t<FRONT-.1f;t+=1/240f)formed=Math.max(formed,slash.shake(cast,t));
        for(float t=FRONT-.02f;t<FRONT+.25f;t+=1/240f)crossed=Math.max(crossed,slash.shake(cast,t));
        assertTrue(formed>.3f&&crossed>1.2f&&crossed>formed,"shaken as before: "+formed+" "+crossed);
        assertTrue(slash.shake(cast,1.5f)<.01f,"settled by the time it is ink");
        // the shockwave's rings run out on the ground from under its pommel as it crosses the front
        assertEquals(-1,SweepLights.shockRadius(FRONT-.02f),1e-9);
        assertTrue(SweepLights.shockRadius(FRONT+.3f)>SweepLights.shockRadius(FRONT+.1f));
    }
    @Test void allOfItIsGoneWithinItsSwingsLife(){
        float life=new IsosangSlash().lifetime();
        assertTrue(SweepLights.last()<life-.3f,"gone well within its swing's life: "+SweepLights.last()+" of "+life);
        // the moon rising from its gem goes up and fades
        assertTrue(SweepLights.moonAt(PIVOT,0,.8f).y>SweepLights.moonAt(PIVOT,0,0).y+1);
    }
}
