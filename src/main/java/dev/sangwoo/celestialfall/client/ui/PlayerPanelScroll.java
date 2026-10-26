package dev.sangwoo.celestialfall.client.ui;

/**
 * The player list's scroll (2.73.0, user request: "최대 5명만 보이고 나머지는 스크롤 내려야 보이게"): at most SHOWN rows show;
 * with more players the mouse wheel over the list moves it a row a notch, easing to that row in TICKS (so nothing jumps
 * from one frame to the next), never past the first row or the last. Times are ticks of the HUD's own clock; kept free
 * of Minecraft for tests.
 */
public final class PlayerPanelScroll {
    /** Rows shown at most, and the ticks a scroll eases over. */
    public static final int SHOWN=5;public static final double TICKS=3;
    private double from,to,at=-1e9,pending;

    /** How many rows can be scrolled past with {@code players} in the list and {@code shown} rows showing. */
    public static int most(int players,int shown){return Math.max(0,players-shown);}
    /** The first row shown at {@code now}: a fraction while it moves. */
    public double first(double now){double k=Math.max(0,Math.min(1,(now-at)/TICKS));k=k*k*(3-2*k);return from+(to-from)*k;}
    /** The row it is going to (or at). */
    public int target(){return (int)Math.round(to);}
    /**
     * The wheel turned by {@code notches} (up positive, as GLFW gives it) over the list at {@code now}: whether the list
     * takes it. It does whenever more players are listed than show, even at either end (so the camera does not zoom
     * under the list); a notch is a row, and a trackpad's fractions add up to rows.
     */
    public boolean wheel(double notches,int players,int shown,double now){
        int most=most(players,shown);if(most==0){pending=0;return false;}
        pending+=notches;int steps=(int)pending;pending-=steps;
        if(steps!=0)move(Math.max(0,Math.min(most,target()-steps)),now);
        return true;
    }
    /** Keeps it within the list as players come and go. */
    public void fit(int players,int shown,double now){int most=most(players,shown);if(target()>most)move(most,now);}
    /** Back to the top at once (joining a game). */
    public void reset(){from=to=0;at=-1e9;pending=0;}
    private void move(int row,double now){from=first(now);to=row;at=now;}
}
