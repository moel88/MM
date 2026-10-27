package dev.sangwoo.celestialfall.client.vfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VfxNoiseTest {
    private static int channel(int abgr,int k){return (abgr>>>(8*k))&255;}

    @Test void everyChannelUsesTheFullRange() {
        int[] px=VfxNoise.abgr();
        assertEquals(VfxNoise.SIZE*VfxNoise.SIZE,px.length);
        for(int k=0;k<4;k++){
            int lo=255,hi=0;for(int p:px){lo=Math.min(lo,channel(p,k));hi=Math.max(hi,channel(p,k));}
            assertEquals(0,lo,"channel "+k);assertEquals(255,hi,"channel "+k);
        }
    }
    @Test void tilesWithoutASeam() {
        int[] px=VfxNoise.abgr();int n=VfxNoise.SIZE;
        // The step across the wrap is no bigger than the steps inside the texture.
        for(int k=0;k<4;k++){
            int inner=0,wrap=0;
            for(int y=0;y<n;y++){
                for(int x=1;x<n;x++)inner=Math.max(inner,Math.abs(channel(px[y*n+x],k)-channel(px[y*n+x-1],k)));
                wrap=Math.max(wrap,Math.abs(channel(px[y*n],k)-channel(px[y*n+n-1],k)));
            }
            for(int x=0;x<n;x++)wrap=Math.max(wrap,Math.abs(channel(px[x],k)-channel(px[(n-1)*n+x],k)));
            assertTrue(wrap<=inner+2,"channel "+k+" wrap="+wrap+" inner="+inner);
        }
    }
    @Test void isDeterministic() {
        assertArrayEquals(VfxNoise.abgr(),VfxNoise.abgr());
    }
}
