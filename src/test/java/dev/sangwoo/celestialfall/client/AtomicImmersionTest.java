package dev.sangwoo.celestialfall.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicImmersionTest {
    @Test void violetAndShakeOnlyOccupyTheLiveThreeDimensionalColumn(){
        assertEquals(AtomicImmersion.NONE,AtomicImmersion.sample(127,0,1,0,28,1));
        assertEquals(AtomicImmersion.NONE,AtomicImmersion.sample(220,29,1,0,28,1));
        assertEquals(AtomicImmersion.NONE,AtomicImmersion.sample(220,0,-3,0,28,1));
        assertEquals(AtomicImmersion.NONE,AtomicImmersion.sample(220,0,571,0,28,1));
        assertEquals(AtomicImmersion.NONE,AtomicImmersion.sample(468,0,1,0,28,1));
        assertTrue(AtomicImmersion.sample(220,0,1,0,28,1).violet()>.99);
        // A stationary observer leaves the shrinking volume before the caster does.
        assertEquals(AtomicImmersion.NONE,AtomicImmersion.sample(450,15,1,0,28,1));
        assertTrue(AtomicImmersion.sample(450,0,1,0,28,1).violet()>.99);
    }
    @Test void shakeIsBoundedSmoothAndCanBeDisabledWithoutLosingExposure(){
        double movement=0;
        for(float age=131;age<460;age+=.05f){
            var a=AtomicImmersion.sample(age,0,1,0,28,1);
            var b=AtomicImmersion.sample(age+.001f,0,1,0,28,1);
            assertTrue(Math.abs(a.yaw())<4.5&&Math.abs(a.pitch())<4&&Math.abs(a.roll())<1.8);
            assertTrue(Math.abs(a.yaw()-b.yaw())<.02);
            movement+=Math.abs(a.yaw());
            var off=AtomicImmersion.sample(age,0,1,0,28,0);
            assertEquals(a.violet(),off.violet());assertEquals(0,off.yaw(),1e-6);assertEquals(0,off.pitch(),1e-6);assertEquals(0,off.roll(),1e-6);
        }
        assertTrue(movement>6000,"sustained quake must remain visible after the initial impact");
    }
}
