package dev.sangwoo.celestialfall.client;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fire cells stand on upright planes fixed in the world (like the fire block), so they never turn to follow the camera. */
class KagenoFireTest {
    @Test void aCellsPlanesAreUprightSixtyDegreesApartAndSetByItsSeedAlone(){
        for(int seed:new int[]{0,7,11,123,255,1000}){
            Vec3d[] a=KagenoFire.cellPlanes(seed),b=KagenoFire.cellPlanes(seed);
            assertEquals(3,a.length);
            for(int k=0;k<3;k++){
                assertEquals(0,a[k].y,1e-12,"upright");
                assertEquals(1,a[k].length(),1e-9);
                assertEquals(0,a[k].distanceTo(b[k]),1e-12,"the same every frame, wherever the camera is");
                assertEquals(.5,Math.abs(a[k].dotProduct(a[(k+1)%3])),1e-9,"60 degrees apart");
            }
        }
        assertNotEquals(KagenoFire.cellPlanes(1)[0],KagenoFire.cellPlanes(2)[0],"neighbouring cells are turned differently");
    }
}
