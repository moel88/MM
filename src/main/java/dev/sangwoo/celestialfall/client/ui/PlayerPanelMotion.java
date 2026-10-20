package dev.sangwoo.celestialfall.client.ui;

/**
 * The player list's slide (2.45.0, user request: shown at the top right on joining; Tab slides it off to the side and
 * Tab again brings it back), kept free of Minecraft for tests. Times are ticks of the HUD's own clock. One progress
 * runs from 0 (shown) to 1 (out of sight) at a steady pace, from wherever it is when Tab turns it round, so it never
 * jumps; each box follows it a little after the one above (the header leads out, the last row leads back in).
 */
public final class PlayerPanelMotion {
    /** Ticks for the whole slide, and how far behind each box follows the one before it (shares of a box's own slide). */
    public static final double SLIDE=9,STAGGER=.1;
    private boolean hidden;private double from,at=-1e9;

    /** Shown, at rest (joining a game). */
    public void reset(){hidden=false;from=0;at=-1e9;}
    public boolean hidden(){return hidden;}
    /** Tab at {@code now}: turns the slide round from where it is. */
    public void toggle(double now){from=progress(now);at=now;hidden=!hidden;}
    /** 0 shown, 1 out of sight; steady in time. */
    public double progress(double now){
        double p=from+(hidden?1:-1)*(now-at)/SLIDE;
        return hidden?Math.min(1,Math.max(from,p)):Math.max(0,Math.min(from,p));
    }
    /** Box {@code i} of {@code n} (0 the header): how far it has gone out, eased (0 in place, 1 out of sight). */
    public static double box(double progress,int i,int n){
        double e=progress*(1+STAGGER*Math.max(0,n-1))-STAGGER*i;e=Math.max(0,Math.min(1,e));
        return e*e*(3-2*e);
    }
}
