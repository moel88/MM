package dev.sangwoo.celestialfall.client.vfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VfxStreaksTest {
    @Test void seedsAreStableAndInRange() {
        float[] p=VfxStreaks.positions(VfxStreaks.COUNT),q=VfxStreaks.positions(VfxStreaks.COUNT);
        int[] c=VfxStreaks.colors(VfxStreaks.COUNT);
        assertArrayEquals(p,q);
        for(float v:p)assertTrue(v>=0&&v<1);
        for(int v:c)assertTrue(v>=0&&v<=255);
    }
    @Test void lanesCoverThePillarEvenly() {
        float[] p=VfxStreaks.positions(VfxStreaks.COUNT);int[] bins=new int[16];
        for(int i=0;i<VfxStreaks.COUNT;i++)bins[(int)(p[i*3]*16)]++;
        for(int b:bins)assertTrue(Math.abs(b-VfxStreaks.COUNT/16.0)<VfxStreaks.COUNT/16.0*.3,"lane bin "+b);
    }
    @Test void aShareOfTheFieldThinsItEvenly() {
        int[] c=VfxStreaks.colors(VfxStreaks.COUNT);
        // The vertex shader draws a streak when its order (alpha) is under the share: about that share is drawn.
        for(float share:new float[]{.35f,.6f}){
            int n=0;for(int i=0;i<VfxStreaks.COUNT;i++)if(c[i*4+3]/255f<=share)n++;
            assertEquals(share,n/(double)VfxStreaks.COUNT,.03);
        }
        // About one in ten is a bolt (kind above 0.9).
        int bolts=0;for(int i=0;i<VfxStreaks.COUNT;i++)if(c[i*4+2]/255f>.9f)bolts++;
        assertEquals(.1,bolts/(double)VfxStreaks.COUNT,.03);
    }
}
