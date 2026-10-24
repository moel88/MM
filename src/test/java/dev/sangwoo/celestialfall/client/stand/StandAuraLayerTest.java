package dev.sangwoo.celestialfall.client.stand;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The aura round a stand and its user (2.53.0): flames that flow smoothly at 60 fps for ever, and keep their size in blocks. */
class StandAuraLayerTest {
    @Test void theFlamesFlowUpSmoothlyForever(){
        float[] last=StandAuraLayer.flow(0);
        for(int f=1;f<=60*120;f++){
            float[] now=StandAuraLayer.flow(f/60.0);
            for(int k=0;k<now.length;k++){
                assertTrue(now[k]>=0&&now[k]<1,"layer "+k+" at "+now[k]);
                // wrapping from 1 back to 0 is no jump on the tiling noise
                float d=now[k]-last[k];if(d<-.5f)d+=1;
                assertTrue(d>0&&d<.05f,"layer "+k+" moves "+d+" in a frame");
            }
            last=now;
        }
        // ten hours into a session: still the same step every frame
        double t=36000;float[] a=StandAuraLayer.flow(t),b=StandAuraLayer.flow(t+1/60.0);
        for(int k=0;k<a.length;k++){float d=b[k]-a[k];if(d<-.5f)d+=1;assertEquals(StandAuraLayer.FLOW[k]/60,d,1e-4);}
    }
    @Test void itKeepsItsSizeInBlocks(){
        float near=StandAuraLayer.scale(730,1.43f,2.5),far=StandAuraLayer.scale(730,1.43f,5);
        assertEquals(730*.5*1.43/2.5,near,1e-3);
        assertEquals(near/2,far,1e-3,"twice as far, half as big on screen");
        assertTrue(StandAuraLayer.scale(730,1.43f,0)<1e4,"no blow-up at the camera");
    }
}
