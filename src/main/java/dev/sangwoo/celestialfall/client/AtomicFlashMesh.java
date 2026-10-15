package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.AtomicTimeline;

/** Procedural GUI quads: the exact same ordered geometry is used by the HUD and regression render. */
public final class AtomicFlashMesh {
    @FunctionalInterface public interface Quads {
        void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy,int argb);
    }
    private AtomicFlashMesh() {}
    public static boolean draw(float age,int width,int height,Quads out) {
        if(!AtomicTimeline.whiteout(age)||width<=0||height<=0)return false;
        // User-requested white-only exposure. No ink fragments or speed strokes.
        out.quad(0,0,0,height,width,height,width,0,0xFFFFFFFF);
        return true;
    }
}
