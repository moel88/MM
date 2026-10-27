package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.client.title.TitleCameraTour;
import dev.sangwoo.celestialfall.client.title.TitleCameraTour.Point;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class TitleCameraTourTest {
    static class Terrain implements TitleCameraTour.Terrain {
        int height=64,limit=1000,queries=0;boolean blocked=false;
        public boolean loaded(int x,int z){return Math.abs(x)<=limit&&Math.abs(z)<=limit;}
        public int surface(int x,int z){assertTrue(loaded(x,z));queries++;return height;}
        public int ceiling(){return 320;}
        public boolean clear(Point p,Point f){return !blocked;}
    }
    TitleCameraTour tour(Terrain t){return new TitleCameraTour(new Point(0,65,0),8,t);}
    @Test void holdsTheExactPoseForTenRealSecondsThenCuts(){
        var t=new Terrain();var tour=tour(t);var first=tour.sample(700);int queries=t.queries;
        t.height=80;
        for(long now:new long[]{701,999,1700,4000,10699})assertSame(first,tour.sample(now));
        assertEquals(queries,t.queries,"No per-frame terrain work or camera drift");
        var second=tour.sample(10700);assertNotEquals(first.position(),second.position());
        assertSame(second,tour.sample(20699));assertNotSame(second,tour.sample(20700));
    }
    @Test void fourDistinctViewsLoopAndSkippingFramesUsesTheRealClock(){
        var tour=tour(new Terrain());var first=tour.sample(100);var positions=new HashSet<Point>();
        for(int i=0;i<4;i++)positions.add(tour.sample(100+i*10000).position());
        assertEquals(4,positions.size());assertEquals(first,tour.sample(40100));
        assertEquals(first,tour.sample(120100));assertEquals(12,tour.interval());
    }
    @Test void waitsForLoadedTerrainAndStartsFullTenSecondsAfterItArrives(){
        var t=new Terrain();t.limit=0;var tour=tour(t);assertNull(tour.sample(0));assertEquals(0,t.queries);
        t.limit=1000;assertNull(tour.sample(249));var first=tour.sample(250);assertNotNull(first);
        assertSame(first,tour.sample(10249));assertNotSame(first,tour.sample(10250));
    }
    @Test void onlyLoadedTerrainIsSampledAndCameraPullsInWhenChunksAreMissing(){
        var t=new Terrain();t.limit=30;
        for(int i=0;i<4;i++){
            var shot=TitleCameraTour.choose(i,new Point(0,65,0),32,t);assertNotNull(shot);
            assertTrue(Math.abs(shot.position().x())<=18&&Math.abs(shot.position().z())<=18);
        }
        t.limit=0;assertNull(TitleCameraTour.choose(0,new Point(0,65,0),32,t));
    }
    @Test void occludedShotsAreRejectedAndPreviousViewIsKeptDuringLaterLoading(){
        var t=new Terrain();t.blocked=true;assertNull(tour(t).sample(0));
        t.blocked=false;var tour=tour(t);var first=tour.sample(0);t.blocked=true;
        assertSame(first,tour.sample(10000));t.blocked=false;assertNotSame(first,tour.sample(20000));
    }
    @Test void cameraRaisesForAnObstacleAndAllCoordinatesStayFinite(){
        Terrain t=new Terrain(){public boolean clear(Point p,Point f){return p.y()>=100;}};
        assertTrue(tour(t).sample(0).position().y()>=100);
        t=new Terrain();t.height=319;
        for(int range:new int[]{2,8,32})for(int slot=0;slot<4;slot++){
            var s=TitleCameraTour.choose(slot,new Point(0,65,0),range,t);
            assertTrue(s.position().y()<=318&&s.focus().y()<=318);
            assertTrue(Float.isFinite(s.yaw())&&Float.isFinite(s.pitch()));
        }
    }
    @Test void aNewMenuSessionRestartsTheTour(){
        var t=new Terrain();var a=tour(t);a.sample(0);a.sample(20000);
        var b=tour(t);var first=b.sample(22000);assertSame(first,b.sample(31999));assertNotSame(first,b.sample(32000));
    }
}
