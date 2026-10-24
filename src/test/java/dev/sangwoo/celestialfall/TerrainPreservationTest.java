package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.terrain.RestorationTiming;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TerrainPreservationTest {
    @Test void tenSecondsEmptyThenOneSecondFade() {
        long due=RestorationTiming.solidAt(1000,1000);
        assertEquals(1220,due);
        assertEquals(0,RestorationTiming.alpha(1199.99,due));
        assertEquals(0,RestorationTiming.alpha(1200,due));
        assertEquals(.5,RestorationTiming.alpha(1210,due));
        assertEquals(1,RestorationTiming.alpha(1220,due));
    }
    @Test void continuousAttackRetainsCraterUntilTenSecondsAfterEnd() {
        assertEquals(960,RestorationTiming.solidAt(154,740));
        assertEquals(0,RestorationTiming.alpha(940,960));
        assertEquals(.5,RestorationTiming.alpha(950,960));
    }
    @Test void repeatHitRestartsCountdownWithoutRounding() {
        long first=RestorationTiming.solidAt(1,1),again=RestorationTiming.solidAt(200,200);
        assertEquals(221,first);assertEquals(420,again);
        assertEquals(0,RestorationTiming.alpha(221,again));
    }
    @Test void alphaIsSmoothMonotonicAndClamped() {
        double last=0;
        for(int i=0;i<=1000;i++){double a=RestorationTiming.alpha(195+i*.03,220);assertTrue(a>=last&&a>=0&&a<=1);last=a;}
        assertEquals(1,RestorationTiming.alpha(9999,220));
    }
    @Test void bowlKeepsVisibleRimAndDeeperCore() {
        assertEquals(10,RestorationTiming.depth(0,48,10));
        assertEquals(1,RestorationTiming.depth(48*48,48,10));
        int last=10;
        for(int r=0;r<=48;r++){int d=RestorationTiming.depth(r*r,48,10);assertTrue(d<=last&&d>=1);last=d;}
    }
}
