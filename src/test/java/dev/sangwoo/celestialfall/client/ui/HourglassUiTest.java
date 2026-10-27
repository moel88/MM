package dev.sangwoo.celestialfall.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The player list's hourglass (2.73.0): its sand runs down smoothly at 60 fps, it turns over once the sand has run out,
 * and turned over it looks as the next run begins, so nothing jumps; its bulbs are convex (they are filled as fans).
 */
class HourglassUiTest {
    private static final double FRAME=20/60.;

    @Test void theSandRunsDownAndTheTurnLeavesItAsTheNextRunBegins(){
        var last=HourglassUi.sand(0);
        assertEquals(1,last.top(),1e-6,"it begins full above");assertEquals(0,last.angle(),1e-6);
        int turns=0;
        double step=FRAME/HourglassUi.FLOW+1e-5,turn=Math.PI*1.5*FRAME/HourglassUi.FLIP+1e-5;
        for(double t=FRAME;t<3*(HourglassUi.FLOW+HourglassUi.FLIP);t+=FRAME){
            var s=HourglassUi.sand(t);
            assertTrue(s.top()>=0&&s.top()<=1&&s.bottom()>=0&&s.bottom()<=1&&s.stream()>=0&&s.stream()<=1);
            if(s.angle()==0)assertEquals(1,s.top()+s.bottom(),1e-5,"the sand is all there, above or below");
            if(s.angle()>0)assertEquals(0,s.stream(),1e-6,"no stream while it turns");
            boolean smooth=Math.abs(s.top()-last.top())<=step&&Math.abs(s.bottom()-last.bottom())<=step&&Math.abs(s.angle()-last.angle())<=turn;
            if(!smooth){
                turns++;
                // turned half round, the full lower bulb is the full upper one: the next run begins as this one ended
                assertEquals(0,last.top(),1e-4);assertEquals(1,last.bottom(),1e-4);assertEquals(Math.PI,last.angle(),.01);
                assertEquals(1,s.top(),.01);assertEquals(0,s.bottom(),.01);assertEquals(0,s.angle(),1e-9);
            }
            last=s;
        }
        assertEquals(2,turns,"once a run, and only there");
    }
    @Test void itsBulbsAreConvexAndClockwise(){
        for(float[] p:new float[][]{HourglassUi.bulb(10),HourglassUi.lower(HourglassUi.bulb(10))}){
            int n=p.length/2;
            for(int i=0;i<n;i++){
                int j=(i+1)%n,k=(i+2)%n;
                double ex=p[j*2]-p[i*2],ey=p[j*2+1]-p[i*2+1],fx=p[k*2]-p[j*2],fy=p[k*2+1]-p[j*2+1];
                assertTrue(ex*fy-ey*fx>0,"turns the same way at corner "+j);
            }
        }
        float[] up=HourglassUi.bulb(10);
        double top=1e9,neck=-1e9;for(int i=1;i<up.length;i+=2){top=Math.min(top,up[i]);neck=Math.max(neck,up[i]);}
        assertEquals(-4,top,1e-6,"the upper bulb from its top");assertTrue(neck<0&&neck>-.1,"down to the neck at the middle");
    }
}
