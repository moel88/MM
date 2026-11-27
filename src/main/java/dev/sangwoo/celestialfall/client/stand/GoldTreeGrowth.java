package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * How Gold Experience's tree grows, stands and withers (2.152.0), on its clock (ticks since it burst up out of the ground,
 * smooth between ticks): the size of each of its branches (each grows out of the one before it, from the trunk out to the
 * twigs, as a young tree shoots up in the pictures the user sent) and how it stirs. A branch's size scales all that grows
 * out of it, so nothing shows before what it grows on. Everything is a continuous function of the clock: no bone jumps
 * from one 60 fps frame to the next.
 */
public final class GoldTreeGrowth {
    /**
     * When each depth of branch begins to grow (ticks after the burst) and how long it takes: the trunk (bursting up fast,
     * with its maker flung off it), its limbs, their branches, the twigs; each branch a little after or before its fellows.
     */
    static final float[] FROM={0f,1.2f,3.6f,6.1f},TAKES={2.4f,4.2f,4.6f,4.4f};
    static final float LATE=.9f;
    /** By when it has all grown (the server's TREE_GROWN, from the burst). */
    public static final float GROWN=GoldExperienceRules.TREE_GROWN-GoldExperienceRules.TREE_BURST;
    /** How long it takes to wither back into the ground (the server's, from TREE_WITHER to TREE_END). */
    public static final float WITHER=GoldExperienceRules.TREE_END-GoldExperienceRules.TREE_WITHER;
    /** Withering, when each depth goes (shares of the withering): the twigs first, the trunk last, into the ground. */
    static final float[] GO_FROM={.5f,.3f,.14f,0f},GO_TO={1f,.82f,.62f,.46f};
    /** How far each depth stirs in the wind (degrees), and how long it takes to settle after growing (ticks). */
    static final float[] SWAY={.35f,1f,1.8f,2.6f};
    static final float SETTLE=14;
    /** A blow turned back: how far it jolts (degrees, the trunk; its branches more) and how fast it dies away (seconds). */
    static final float JOLT=4.5f,JOLT_TAU=.16f;
    /** A branch that grows: its first bone, how deep it is in the tree (0 the trunk .. 3 a twig), its order among them. */
    public record Grower(String bone,int depth,int order){}
    /** The branches that grow, from the file the generator writes with the model (tools/stand/make_gold_tree.py). */
    public static final List<Grower> GROWERS=growers();
    private GoldTreeGrowth(){}
    private static List<Grower> growers(){
        var out=new ArrayList<Grower>();
        try(var in=GoldTreeGrowth.class.getResourceAsStream("/assets/celestialfall/stands/gold_tree_shape.json")){
            if(in==null)throw new IllegalStateException("gold_tree_shape.json is missing");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var g:root.getAsJsonArray("growers")){var a=g.getAsJsonArray();out.add(new Grower(a.get(0).getAsString(),a.get(1).getAsInt(),a.get(2).getAsInt()));}
        }catch(Exception ex){
            org.slf4j.LoggerFactory.getLogger("celestialfall").error("Gold Experience's tree could not be read",ex);
        }
        return List.copyOf(out);
    }

    static float smooth(double v){v=v<0?0:v>1?1:v;return (float)(v*v*(3-2*v));}
    /** A branch's own lateness among its fellows (0 to 1), by its order. */
    static float late(int order){double v=Math.sin(order*12.9898+4.1)*43758.5453;return (float)(v-Math.floor(v));}
    /**
     * How grown a branch of {@code depth} (0 the trunk .. 3 a twig) is {@code age} ticks after the burst: 0 not yet, 1
     * grown; a branch shoots out a little past its length and settles (the trunk bursts up and stops hard).
     */
    public static float grown(double age,int depth,int order){
        int d=Math.max(0,Math.min(3,depth));
        // (the trunk bursts up at once; each branch a little after or before its fellows)
        double u=(age-FROM[d]-(d==0?0:LATE*late(order)))/TAKES[d];
        if(u<=0)return 0;if(u>=1)return 1;
        if(d==0)return (float)(1-Math.pow(1-u,3));
        double s=1.25,v=u-1;
        return (float)(1+(s+1)*v*v*v+s*v*v);
    }
    /** How much of a branch of {@code depth} is left {@code age} ticks after the burst, withering from {@code wither} (1 until then, 0 gone). */
    public static float left(double age,double wither,int depth){
        int d=Math.max(0,Math.min(3,depth));
        double w=(age-wither)/WITHER;
        return 1-smooth((w-GO_FROM[d])/(GO_TO[d]-GO_FROM[d]));
    }
    /** A branch's size this frame: grown, and as much of it as is left (never quite 0: a bone scaled to nothing has no turn). */
    public static float size(double age,double wither,int depth,int order){
        return Math.max(.001f,grown(age,depth,order)*left(age,wither,depth));
    }
    /** How far the roots have run out over the ground (0 to 1): out with the trunk, back in as it withers. */
    public static float roots(double age,double wither){
        return smooth(age/3.5)*(1-smooth(((age-wither)/WITHER-.55)/.45));
    }
    /** How far a branch stirs this frame (degrees about x, about z), its tree grown and settled. */
    public static float[] sway(double age,int depth,int order,float[] out){
        int d=Math.max(0,Math.min(3,depth));
        double s=age/20,phase=late(order)*6.283,period=2.6+late(order*7+3);
        float a=SWAY[d]*smooth((age-GROWN)/SETTLE);
        out[0]=(float)(a*Math.sin(s*6.283/period+phase));
        out[1]=(float)(a*.7*Math.sin(s*6.283/(period*1.37)+phase*1.7));
        return out;
    }
    /** How far it jolts (degrees, at {@code depth}) {@code since} seconds after a blow on it came back on its striker (NaN: none heard). */
    public static float jolt(double since,int depth){
        if(Double.isNaN(since)||since<0||since>1)return 0;
        // in over a frame or two, shaking, dying away
        double in=Math.min(1,since/.035);
        return (float)(JOLT*(1+.5*depth)*in*Math.exp(-since/JOLT_TAU)*Math.sin(since*30));
    }
    /** How dark it is as it withers (0 green and brown, 1 as dark as it goes). */
    public static float dimmed(double age,double wither){return smooth((age-wither)/(WITHER*.6));}

    /**
     * Seen through (2.152.0, the first filming: its user, flung on past it, stood for its six seconds hidden behind its
     * trunk from the camera behind them). It is drawn see-through where it stands between a camera and the body that
     * camera follows. It is all branches: only its trunk and the forks of its limbs take the sight, so: how far round its
     * trunk the line from the camera to the body must pass for all of it (blocks), and over what further distance it
     * comes back; the heights at its trunk (over its foot) between which the line is taken (blocks: from just under its
     * foot, up to about where its limbs spread, fading out over the crown); how far before the body its trunk must stand on
     * that line (blocks); and how much of it then shows. Not as it bursts up, its user just flung up over it (from
     * SEE_FROM ticks after the burst, over SEE_IN): the burst is the thing to see.
     */
    static final double SEE_R=.8,SEE_EDGE=1.1,SEE_LOW=-.8,SEE_FOOT=-.3,SEE_FORKS=3.4,SEE_OVER=6.4,SEE_AHEAD=.8,SEE_FROM=14,SEE_IN=8;
    public static final float SEE_MIN=.28f;
    /** How fast what shows follows what should (ticks): a quick fade, never a jump from frame to frame however fast the camera or the body moves. */
    public static final double SEE_TAU=2.4;
    static double between(double v,double from,double to){double u=(v-from)/(to-from);u=u<0?0:u>1?1:u;return u*u*(3-2*u);}
    /**
     * How much of the tree standing at {@code t*}, {@code age} ticks after it burst up, should show (1 all of it, down to
     * SEE_MIN), seen from a camera at {@code c*} that follows a body at {@code b*} (its middle): less as its trunk comes
     * between them, more again as it goes; all of it where it stands beyond the body or off the line, or the line passes
     * over its forks. A continuous function of all of them.
     */
    public static float opacity(double tx,double ty,double tz,double cx,double cy,double cz,double bx,double by,double bz,double age){
        double ux=bx-cx,uz=bz-cz,len2=ux*ux+uz*uz;
        double sRaw=len2<1e-8?0:((tx-cx)*ux+(tz-cz)*uz)/len2,s=Math.max(0,Math.min(1,sRaw));
        double d=Math.hypot(cx+s*ux-tx,cz+s*uz-tz);
        // how far before the body its trunk stands, along the line (blocks): none, or beyond the body, and it hides nothing of it
        double before=(1-sRaw)*Math.sqrt(len2);
        double y=cy+s*(by-cy)-ty;
        double w=(1-between(d,SEE_R,SEE_R+SEE_EDGE))*between(before,0,SEE_AHEAD)
            *between(y,SEE_LOW,SEE_FOOT)*(1-between(y,SEE_FORKS,SEE_OVER))*between(age,SEE_FROM,SEE_FROM+SEE_IN);
        return (float)(1-(1-SEE_MIN)*w);
    }
    /** What shows {@code dt} ticks on, from {@code shown}, following {@code should} (SEE_TAU); a long gap (or none heard yet) takes it at once. */
    public static float eased(float shown,float should,double dt){
        if(!(dt>=0)||dt>10||Float.isNaN(shown))return should;
        return (float)(shown+(should-shown)*(1-Math.exp(-dt/SEE_TAU)));
    }
}
