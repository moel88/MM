package dev.sangwoo.celestialfall.client.xchara;

/**
 * How X Chara's hack looks over time (2.115.0, their Q: XCharaHack; the user's video of it coming on, read frame by
 * frame): every part of it a continuous function of the seconds since it last came on or went off, so that every frame
 * drawn is its own. Coming on (the video: 0.2 s of purple blocks breaking over the knife and the panel, then steady):
 * the knife's white flickers in over a sixth of a second under blocks of purple, its rim flares and settles, the panel
 * opens from a line of light (its width, then its depth, then what is on it), the other arm comes up to hold it. Going
 * off (not in the video: its coming, backwards): the white breaks up and goes, the rim fades, what is on the panel
 * goes dark, it folds to a line and the line closes, the arm comes down.
 */
public final class HackLook {
    private HackLook(){}
    /** How long its coming on and its going off take (seconds): both done before the server hears the next Q (XCharaRules.HACK_RATE). */
    public static final float ON=.34f,OFF=.30f;
    /** The panel folded to a line: this much of its depth. */
    public static final float LINE=.035f;
    /** One player's hack this frame: whether it is on, the seconds since it changed (long ago if it was so when first seen), this client's clock (seconds) and their own seed. */
    public record Frame(boolean on,float t,float clock,int seed){
        /** Whether anything of it is drawn. */
        public boolean shows(){return on||t<OFF;}
        public float fill(){return HackLook.fill(on,t);}
        public float rim(){return HackLook.rim(on,t);}
        public float cells(){return HackLook.cells(on,t);}
        public float arm(){return HackLook.arm(on,t);}
        public float wide(){return HackLook.wide(on,t);}
        public float deep(){return HackLook.deep(on,t);}
        public float content(){return HackLook.content(on,t);}
        public float line(){return HackLook.line(on,t);}
        /** How much of the steady look is there (its glow, its motes, its light). */
        public float steady(){return on?smooth(t,.1f,ON):1-smooth(t,0,.12f);}
    }
    static float smooth(float x,float a,float b){
        if(b<=a)return x>=b?1:0;
        float u=Math.max(0,Math.min(1,(x-a)/(b-a)));return u*u*(3-2*u);
    }
    /** A value through keys (times rising), straight between them, held before the first and after the last. */
    static float track(float t,float[] at,float[] v){
        if(t<=at[0])return v[0];
        for(int i=1;i<at.length;i++)if(t<at[i])return v[i-1]+(v[i]-v[i-1])*(t-at[i-1])/(at[i]-at[i-1]);
        return v[v.length-1];
    }
    private static final float[] FILL_ON_AT={0,.03f,.06f,.10f,.13f,.17f},FILL_ON={0,.9f,.25f,1,.6f,1};
    private static final float[] FILL_OFF_AT={0,.04f,.07f,.11f,.14f,.20f},FILL_OFF={1,.3f,.85f,.15f,.5f,0};
    /** How white the knife is (0 its own black, 1 lit): flickering in, flickering out. */
    public static float fill(boolean on,float t){return on?track(t,FILL_ON_AT,FILL_ON):track(t,FILL_OFF_AT,FILL_OFF);}
    /** How strongly its purple rim shows (1 steady; above it, the flare as it comes on). */
    public static float rim(boolean on,float t){
        if(!on)return 1-smooth(t,.04f,.20f);
        return smooth(t,0,.05f)*(1+.8f*smooth(t,.03f,.09f)*(1-smooth(t,.09f,.26f)));
    }
    /** How much of the knife is under blocks of purple (the share of its cells showing one): the glitch of its coming and going, none steady. */
    public static float cells(boolean on,float t){
        return on?smooth(t,0,.04f)*(1-smooth(t,.12f,.30f))*.55f:smooth(t,0,.03f)*(1-smooth(t,.11f,.26f))*.6f;
    }
    /** How far the other arm is up, holding the panel (0 as it hangs, 1 held). */
    public static float arm(boolean on,float t){return on?smooth(t,0,.28f):1-smooth(t,.05f,OFF);}
    /** Ease out past the mark and back (a little: 4%). */
    static float back(float x){x=Math.max(0,Math.min(1,x));float c=1.1f,u=x-1;return 1+(c+1)*u*u*u+c*u*u;}
    /** The panel's width, of its own (it opens sideways first, a touch past and back; it closes last). */
    public static float wide(boolean on,float t){
        if(on)return back(t/.13f);
        float u=Math.max(0,Math.min(1,(t-.12f)/.12f));return 1-u*u;
    }
    /** The panel's depth, of its own (a line until it is wide, then open; folding to a line as it goes). */
    public static float deep(boolean on,float t){
        if(on)return LINE+(1-LINE)*back((t-.07f)/.17f);
        return 1-(1-LINE)*smooth(t,.02f,.13f);
    }
    /** How much of what is on the panel shows (its writing, its keys). */
    public static float content(boolean on,float t){return on?smooth(t,.17f,.30f):1-smooth(t,0,.05f);}
    /** The line of light the panel opens from and closes to. */
    public static float line(boolean on,float t){return on?1-smooth(t,.06f,.2f):smooth(t,.04f,.13f)*(1-smooth(t,.2f,.25f));}

    // ───────────────────────────── what is on the panel ─────────────────────────────

    /** A line of its writing is typed and moved up every this many seconds: typed over TYPE of them, held, then moved up over the last SCROLL. */
    public static final float LINE_EVERY=1.4f,TYPE=1.08f,SCROLL=.12f;
    /** Its rows of writing (the last being typed), the most characters a line has, its keys across and down. */
    public static final int ROWS=4,CHARS=26,KEYS_ACROSS=10,KEYS_DOWN=2;
    /** A key stays lit this long after it is struck (seconds). */
    public static final float KEY_LIT=.16f;
    static float hash(int a,int b,int c){
        int h=a*374761393+b*668265263+c*1274126177;h=(h^(h>>>13))*1274126177;h^=h>>>16;
        return (h&0xffffff)/(float)0x1000000;
    }
    /** The line being typed at {@code clock} seconds. */
    public static int lineAt(float clock){return (int)Math.floor(clock/LINE_EVERY);}
    /** How long line {@code n} is (characters, spaces counted). */
    public static int length(int n,int seed){return 12+(int)(hash(n,seed,11)*(CHARS-12+.999f));}
    /** Whether line {@code n} has a glyph at {@code c} (not a space): words of a few characters, a space between. */
    public static boolean glyph(int n,int c,int seed){
        if(c<0||c>=length(n,seed))return false;
        // words of two to seven glyphs, a space after each
        int start=0;
        while(true){
            int len=2+(int)(hash(n*31+start,seed,5)*5.999f);
            if(c<start+len)return true;
            if(c==start+len)return false;
            start+=len+1;
        }
    }
    /** How many characters of the line being typed are there at {@code clock} (the fraction: the one being struck). */
    public static float typed(float clock,int seed){
        float into=clock-lineAt(clock)*LINE_EVERY;
        return length(lineAt(clock),seed)*Math.min(1,into/TYPE);
    }
    /** How far the rows have moved up at {@code clock}, of a row (0 while a line is typed, to 1 as the next begins). */
    public static float scrolled(float clock){
        float into=clock-lineAt(clock)*LINE_EVERY;
        return smooth(into,LINE_EVERY-SCROLL,LINE_EVERY);
    }
    /** The key character {@code c} of line {@code n} strikes (0 to KEYS_ACROSS*KEYS_DOWN-1). */
    public static int keyOf(int n,int c,int seed){return (int)(hash(n*131+c,seed,23)*(KEYS_ACROSS*KEYS_DOWN-.001f));}
    /** How lit key {@code key} is at {@code clock} (0 to 1): struck as each glyph of the line being typed comes, dying over KEY_LIT. */
    public static float keyLit(int key,float clock,int seed){
        int n=lineAt(clock);float into=clock-n*LINE_EVERY;int len=length(n,seed);
        float per=TYPE/len,lit=0;
        int last=(int)Math.min(len-1,Math.floor(into/per));
        for(int c=last;c>=0&&c>last-6;c--){
            if(!glyph(n,c,seed)||keyOf(n,c,seed)!=key)continue;
            float since=into-c*per;if(since<0||since>=KEY_LIT)continue;
            lit=Math.max(lit,smooth(since,0,.02f)*(1-since/KEY_LIT));
        }
        return lit;
    }
}
