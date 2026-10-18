package dev.sangwoo.celestialfall.client.atomic;

import dev.sangwoo.celestialfall.AtomicTimeline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicEdgeTest {
    static final double R=28;
    @Test void theWallPushesWhileItOpensThenChurnsAndSlams() {
        float open=AtomicPillarShape.open(R);
        assertEquals(0,AtomicPillarShape.push(AtomicLook.IMPACT-.1f,R));
        // At full pace in the middle of the opening, easing out to nothing at R.
        assertEquals(1,AtomicPillarShape.push(AtomicLook.IMPACT+3,R),1e-3);
        assertTrue(AtomicPillarShape.push(open-.2f,R)<.5f);
        assertEquals(0,AtomicPillarShape.push(open+.5f,R),1e-6);
        for(float sec=0;sec<25;sec+=.01f){float p=AtomicPillarShape.push(sec,R);assertTrue(p>=0&&p<=1,"sec "+sec);}
        // The slam: nothing before the pillar is open, a burst right after, gone within a few seconds.
        assertEquals(0,AtomicPillarShape.slam(open-.01f,R));
        assertTrue(AtomicPillarShape.slam(open+.06f,R)>.85f);
        assertTrue(AtomicPillarShape.slam(open+2.5f,R)<.01f);
        assertEquals(R+30*.5,AtomicPillarShape.slamFront(open+.5f,R),1e-3);
        // The foot churns at 0.3 while it stands, harder while it pushes and slams, and is off for the collapse.
        assertEquals(.3f,AtomicPillarShape.edge(open+5,R),1e-3);
        assertTrue(AtomicPillarShape.edge(AtomicLook.IMPACT+3,R)>.99f);
        assertTrue(AtomicPillarShape.edge(open+.06f,R)>.9f);
        assertEquals(0,AtomicPillarShape.edge(AtomicLook.COLLAPSE+.2f,R),1e-6);
        assertEquals(0,AtomicPillarShape.edge(AtomicLook.IMPACT-.1f,R),1e-6);
    }
    @Test void wallShakesHardWithinEightBlocksOutsideOnly() {
        float sec=12;double r=AtomicPillarShape.radius(AtomicStage.visual(sec),R);
        // Right at the wall: violent (several times the hit view's 1.5 degree shake).
        assertTrue(AtomicStage.wallShake(sec,r,r,R)>=6);
        // Fading out to nothing 8 blocks out, and nothing inside the pillar (the hit view keeps its own shake).
        assertTrue(AtomicStage.wallShake(sec,r+4,r,R)>0&&AtomicStage.wallShake(sec,r+4,r,R)<AtomicStage.wallShake(sec,r+1,r,R));
        assertEquals(0,AtomicStage.wallShake(sec,r+AtomicStage.WALL_RANGE,r,R));
        assertEquals(0,AtomicStage.wallShake(sec,r-.5,r,R));
        // Nothing before the impact, bounded while it stands.
        assertEquals(0,AtomicStage.wallShake(AtomicLook.IMPACT-.2f,5,1,R));
        for(float t=0;t<26;t+=.01f){
            double rr=AtomicPillarShape.radius(AtomicStage.visual(t),R);
            float a=AtomicStage.wallShake(t,rr+.1,rr,R);assertTrue(a>=0&&a<=12,"sec "+t+" "+a);
        }
    }
    @Test void craterRingsEruptAsTheWallReachesThem() {
        float open=AtomicPillarShape.open(R),last=0;
        for(double rho:AtomicScene.RINGS){
            float rise=AtomicEdge.slabRise(rho,R);
            assertTrue(rise>=last,"in order "+rho);last=rise;
            if(rho<R){
                // The moment the opening wall gets there, not before.
                assertEquals(rho,AtomicPillarShape.radius(AtomicStage.visual(rise),R),.01,"ring "+rho);
                assertTrue(AtomicPillarShape.radius(AtomicStage.visual(rise-.05f),R)<rho);
                assertTrue(rise>AtomicLook.IMPACT&&rise<=open);
            }else assertEquals(rho,AtomicPillarShape.slamFront(rise,R),1e-3,"ring "+rho);
        }
        // The first ring (5 blocks out) a second and more after the impact, the outermost plates within half a second of the slam.
        assertTrue(AtomicEdge.slabRise(AtomicScene.RINGS[0],R)>AtomicLook.IMPACT+1);
        assertTrue(AtomicEdge.slabRise(AtomicScene.RINGS[AtomicScene.RINGS.length-1],R)<open+.5f);
    }
    @Test void debrisLeavesTheWallOutwardOnlyWhileItPushes() {
        int lanes=60,flown=0;
        for(int lane=0;lane<lanes;lane++)for(int j=0;j<40;j++)for(float sec=AtomicLook.IMPACT;sec<AtomicLook.COLLAPSE;sec+=.05f){
            double[] c=AtomicEdge.chunk(123,lane,lanes,j,sec,R);
            if(c==null)continue;flown++;
            float launch=sec-(float)c[3];
            assertTrue(launch<=AtomicPillarShape.open(R)+1e-3,"launched after the pillar opened");
            assertTrue(AtomicPillarShape.push(launch,R)>=.08f);
            // Starts just outside the wall's radius at launch and only ever moves outward.
            double rho=Math.hypot(c[0],c[2]);
            assertTrue(rho>=AtomicPillarShape.radius(launch,R)+.2,"inside the wall at launch");
            assertTrue(c[1]>=-.46&&c[1]<6,"height "+c[1]);
            assertArrayEquals(c,AtomicEdge.chunk(123,lane,lanes,j,sec,R));
        }
        assertTrue(flown>1000,"debris "+flown);
        assertEquals(AtomicTimeline.OPEN_SPEED,4.5);
    }
}
