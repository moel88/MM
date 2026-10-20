package dev.sangwoo.celestialfall.client;

/**
 * Where the danger banner ({@link AtomicHud}, 2.42.0) goes, kept free of Minecraft so tests can try every screen: centred
 * at the top a fifth of the way down, as in the user's screenshot. On narrow screens the HP art reaches the middle there;
 * the banner then goes up above the art's bars (between its portrait and its bow, made smaller if it must) or, where even
 * that has no room, below the art.
 */
public final class DangerBanner {
    /** Each line's size (em) and the middle of its letters, as shares of the screen's height (the screenshot's). */
    static final float[] SIZE={.040f,.029f,.020f},MIDDLE={.182f,.219f,.250f};
    /** The smallest the banner is made to fit above the art, and the margin it keeps from the screen's top there (GUI units). */
    static final float SMALLEST=.7f,MARGIN=3;
    private DangerBanner(){}

    /** The banner moved so its top is at {@code at} and scaled by {@code scale} (about that top); {@code top} is where the screenshot has it. */
    public record Placement(float at,float scale,float top){
        /** The middle of line {@code i}'s letters, and its size (em), on a screen {@code h} high. */
        public float middle(int i,int h){return at+(MIDDLE[i]*h-top)*scale;}
        public float size(int i,int h){return SIZE[i]*h*scale;}
    }
    /** The banner's box at full size: from the first line's top to the last line's shadow, and half its width. */
    static float top(int h){return MIDDLE[0]*h-SIZE[0]*h*.62f;}
    static float bottom(int h){return MIDDLE[2]*h+SIZE[2]*h*.68f;}
    static float half(float widest,int h){return widest/2+SIZE[1]*h*.2f;}

    /** On a screen {@code w}×{@code h} (GUI units) with its widest line {@code widest} wide at full size; {@code art}: the HP art is shown. */
    public static Placement place(int w,int h,float widest,boolean art){
        float cx=w/2f,top=top(h),bottom=bottom(h),half=half(widest,h);
        if(!art||cx-half>=BotandHudLayout.right(w)+4||top>=BotandHudLayout.bottom(w)+4)return new Placement(top,1,top);
        float clear=(float)BotandHudLayout.clearAbove(w)-2,tall=bottom-top;
        if(bottom<=clear)return new Placement(top,1,top);
        float scale=Math.min(1,(clear-MARGIN)/tall);
        if(scale>=SMALLEST&&cx-half*scale>BotandHudLayout.portraitRight(w)+4)return new Placement(Math.max(MARGIN,clear-tall*scale),scale,top);
        return new Placement(Math.max(top,(float)BotandHudLayout.bottom(w)+4),1,top);
    }
}
