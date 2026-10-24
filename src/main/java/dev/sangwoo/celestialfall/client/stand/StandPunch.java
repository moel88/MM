package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;

/**
 * How Star Platinum moves through its punches (2.54.0), as smooth functions of each punch's own clock (seconds from its
 * start): the stances it goes through (the poses tools/stand/make_star_platinum.py writes), how far it has come out in
 * front of its user, where it stands there and which way it faces, how far it lunges, the gust through its hair and
 * how strongly its afterimages show. Every punch starts from the guard, loads (its anticipation), holds a moment, then
 * strikes faster and faster to land at its contact (StarPlatinumRules.CONTACT, the "RA" of its shout), freezes there
 * for a few frames (the hit-stop), holds the blow and goes back to the guard. The first punch carries the stand round
 * from its place at its user's shoulder to the front as it loads; it stays in front, in its guard, while punches come,
 * and goes back once they stop. A new punch takes over from the one before over a few frames, so nothing jumps.
 */
public final class StandPunch {
    /** A punch as a client saw it: its number (1 to 4), the way it goes (degrees), how far ahead its target stands (blocks, NaN for none), and the time on its own clock (seconds) it was stopped short (NaN if it was not). */
    public record Punch(int combo,float yaw,float aim,float stopped){
        public Punch stop(float at){return new Punch(combo,yaw,aim,at);}
    }
    /** What a frame takes from the punches: the stances' shares (adding up to 1), how far out in front of its user the stand is (0 at its shoulder, 1 in front), the way it faces, how far ahead and to the right of its user it stands, how far it lunges on (blocks), the gust and the afterimages' strength. */
    public static final class Shape {
        public final float[] weights=new float[NAMES.size()];
        public float engage,yaw,ahead,side,lunge,gust,ghost;
        /** How far ahead of its user the punch is aimed (blocks): its target's middle, or as far as the fist reaches. */
        public float toward;
        void clear(){Arrays.fill(weights,0);engage=yaw=ahead=side=lunge=gust=ghost=toward=0;}
    }
    /** When each punch lands (seconds after it starts) and how long it then stands still (the hit-stop: longer for the heavy ones). */
    public static final float[] HIT=new float[5],FREEZE={0,.05f,.05f,.07f,.1f};
    static{for(int k=1;k<=4;k++)HIT[k]=StarPlatinumRules.CONTACT[k]/20f;}
    /** Ease into a key: smoothly, holding the stance, or striking (faster and faster, at full speed as it lands). */
    static final int SMOOTH=0,HOLD=1,STRIKE=2;
    record Key(float at,int stance,int ease){}
    static Key k(float at,int stance,int ease){return new Key(at,stance,ease);}
    /**
     * Each punch's keys on its own clock with the hit-stop taken out: 1 the jab, 2 the cross, 3 the hook, 4 the
     * finisher, which holds the blow out while its target flies.
     */
    static final Key[][] KEYS={null,
        {k(0,GUARD,SMOOTH),k(.1f,WIND_L,SMOOTH),k(.15f,WIND_L,HOLD),k(.3f,PUNCH_L,STRIKE),k(.34f,PUNCH_L,HOLD),k(.58f,GUARD,SMOOTH)},
        {k(0,GUARD,SMOOTH),k(.11f,WIND_R,SMOOTH),k(.15f,WIND_R,HOLD),k(.3f,PUNCH_R,STRIKE),k(.35f,PUNCH_R,HOLD),k(.62f,GUARD,SMOOTH)},
        {k(0,GUARD,SMOOTH),k(.24f,WIND_HOOK,SMOOTH),k(.3f,WIND_HOOK,HOLD),k(.45f,HOOK,STRIKE),k(.5f,HOOK,HOLD),k(.84f,GUARD,SMOOTH)},
        {k(0,GUARD,SMOOTH),k(.25f,WIND_BIG,SMOOTH),k(.33f,WIND_BIG,HOLD),k(.5f,PUNCH_BIG,STRIKE),k(.74f,PUNCH_BIG,HOLD),k(1.15f,GUARD,SMOOTH)}};
    /** The stance each punch strikes in, and the fist it strikes with (true: the right). */
    static final int[] STRIKES={0,PUNCH_L,PUNCH_R,HOOK,PUNCH_BIG};
    static final boolean[] RIGHT={false,false,true,false,true};
    /** How far the stand lunges on as each punch lands (blocks). */
    static final float[] LUNGE={0,.1f,.14f,.18f,.45f};
    /** Gust through the hair as each punch lands. */
    static final float[] GUST={0,.5f,.55f,.85f,1.3f};
    /**
     * Where the stand stands to punch, ahead of its user (blocks): this far with nothing in its way; with someone, as far
     * as it takes for the fist to sink BITE into them, within the bounds. It stands SIDE to its user's right, clear of
     * them for a camera behind them, and punches in at what they face from there.
     */
    static final float AHEAD=1.1f,AHEAD_MIN=.4f,AHEAD_MAX=2.6f,BITE=.12f,SIDE=.8f;
    /** Half a target's width, as the server's aim measures to its near side (a player's). */
    static final float HALF=.3f;
    /** Seconds: the first punch brings it round to the front (off fast, settling in); a new punch takes over from the last; it turns to a new way. */
    static final float RISE=.2f,BLEND=.1f,TURN=.12f;
    /** It stays in front this long once a punch is over (seconds; less once one is stopped short), then goes back over RETURN. */
    static final float LINGER=.45f,LINGER_STOPPED=.15f,RETURN=.4f;
    /** The stand's size, for the fists' places (StarPlatinumRenderer.SCALE: model units of 1/16 block). */
    static final float SCALE=StarPlatinumRenderer.SCALE/16f;
    /** The striking fist's knuckles in each stance (blocks from the stand's feet: ahead, to its right, up), by stance and fist (0 left, 1 right). */
    static final float[][][] FISTS=fists();
    private StandPunch(){}

    static float clamp(float v){return v<0?0:v>1?1:v;}
    static float smooth(float v){v=clamp(v);return v*v*(3-2*v);}
    /** A strike gathers speed all the way in, at its quickest as it lands (1.7 times its mean speed): fast, but no joint leaps a frame. */
    static float ease(int ease,float x){x=clamp(x);return ease==STRIKE?(float)Math.pow(x,1.7):smooth(x);}
    /** Off fast and settling in: still at both ends, at its quickest a third of the way (as the stand's summon burst). */
    static float burst(float u){u=clamp(u);float r=1-u;return 1-r*r*r*(1+3*u);}
    /** A kick that comes in at {@code at} over two frames and dies away with time constant {@code tau} (seconds). */
    static float kick(float t,float at,float tau){return t<at-.03f?0:smooth((t-at+.03f)/.045f)*(float)Math.exp(-Math.max(0,t-at-.015f)/tau);}

    /** The punch's own clock as its body moves on it: time stands still for the hit-stop. */
    public static float warp(int combo,float t){float hit=HIT[combo];return t<hit?t:t<hit+FREEZE[combo]?hit:t-FREEZE[combo];}
    /** How long a punch shows (seconds): its keys and its stand in front, then its way back. */
    public static float length(Punch p){return Float.isNaN(p.stopped)?StarPlatinumRules.LENGTH[p.combo]/20f+LINGER+RETURN:p.stopped+LINGER_STOPPED+RETURN+.2f;}

    /** Adds the stances of punch {@code p} at {@code t} seconds on its clock into {@code w}, {@code share} of them. */
    static void stances(Punch p,float t,float[] w,float share){
        if(!Float.isNaN(p.stopped)&&t>p.stopped){
            // stopped short: from where it was back to the guard
            float back=smooth((t-p.stopped)/.2f);
            stances(p.combo,warp(p.combo,p.stopped),w,share*(1-back));w[GUARD]+=share*back;return;
        }
        stances(p.combo,warp(p.combo,t),w,share);
    }
    static void stances(int combo,float u,float[] w,float share){
        var keys=KEYS[combo];
        if(u<=keys[0].at){w[keys[0].stance]+=share;return;}
        for(int i=1;i<keys.length;i++){
            if(u>keys[i].at)continue;
            float s=ease(keys[i].ease,(u-keys[i-1].at)/(keys[i].at-keys[i-1].at));
            w[keys[i-1].stance]+=share*(1-s);w[keys[i].stance]+=share*s;return;
        }
        w[keys[keys.length-1].stance]+=share;
    }
    /** How far the stand is out in front for this punch: brought round as it starts, sent back once it is over. */
    static float envelope(Punch p,float t){
        float end=Float.isNaN(p.stopped)?StarPlatinumRules.LENGTH[p.combo]/20f+LINGER:p.stopped+LINGER_STOPPED;
        return burst(t/RISE)*(1-smooth((t-end)/RETURN));
    }
    /** How strongly this punch strikes now (its striking stance's share): what the lunge and the speed go by. */
    static float striking(Punch p,float t){float[] w=new float[NAMES.size()];stances(p,t,w,1);return w[STRIKES[p.combo]];}
    /**
     * Ahead of its user (blocks) where the stand stands for this punch: SIDE to the right of the line to its target, as
     * far back from the target's middle as its reach (the fist sinking BITE into it), so the fist comes in at an angle
     * and lands.
     */
    public static float ahead(Punch p){
        if(Float.isNaN(p.aim))return AHEAD;
        float reach=FISTS[STRIKES[p.combo]][RIGHT[p.combo]?1:0][0]+LUNGE[p.combo]-BITE+HALF;
        float back=(float)Math.sqrt(Math.max(0,reach*reach-SIDE*SIDE));
        return Math.max(AHEAD_MIN,Math.min(AHEAD_MAX,p.aim+HALF-back));
    }
    /** The striking fist's knuckles at the contact of punch {@code combo}: blocks from the stand's feet ahead, to its right and up (at its size 1). */
    public static float[] fist(int combo){return FISTS[STRIKES[combo]][RIGHT[combo]?1:0].clone();}

    /**
     * The frame's shape from the newest punch ({@code now}, {@code t} seconds on its clock) and the one before it (null
     * if none; {@code tb} on its clock). The punches' stances add up to 1; how much of them the stand takes, and how far
     * it has come round to the front, is the engagement.
     */
    public static Shape at(Punch now,float t,Punch before,float tb,Shape into){
        into.clear();
        float take=before==null?1:smooth(t/BLEND);
        stances(now,t,into.weights,take);
        if(take<1)stances(before,tb,into.weights,1-take);
        into.engage=Math.max(envelope(now,t),before==null?0:envelope(before,tb));
        float turn=before==null?1:smooth(t/TURN);
        into.yaw=before==null?now.yaw:before.yaw+wrap(now.yaw-before.yaw)*turn;
        float place=before==null?1:smooth(t/(BLEND*1.5f));
        into.ahead=before==null?ahead(now):ahead(before)+(ahead(now)-ahead(before))*place;
        into.side=SIDE;
        float reach=FISTS[STRIKES[now.combo]][RIGHT[now.combo]?1:0][0];
        into.toward=Float.isNaN(now.aim)?into.ahead+reach:Math.max(into.ahead+.4f,now.aim+HALF);
        into.lunge=LUNGE[now.combo]*striking(now,t)*take+(before==null?0:LUNGE[before.combo]*striking(before,tb)*(1-take));
        into.gust=GUST[now.combo]*kick(t,HIT[now.combo],.25f)+(before==null?0:GUST[before.combo]*kick(tb,HIT[before.combo],.25f));
        into.ghost=ghost(now,t);
        return into;
    }
    /** The afterimages: rising as the punch speeds in, gone shortly after it lands (not for a punch stopped before it landed). */
    static float ghost(Punch p,float t){
        float hit=HIT[p.combo];
        if(!Float.isNaN(p.stopped)&&p.stopped<hit)return 0;
        return smooth((t-hit+.08f)/.07f)*(1-smooth((t-hit-FREEZE[p.combo])/.08f));
    }
    static float wrap(float degrees){degrees%=360;if(degrees>=180)degrees-=360;if(degrees<-180)degrees+=360;return degrees;}

    /** Reads where the fists are in every stance (tools/stand/make_star_platinum.py writes it with the poses). */
    private static float[][][] fists(){
        float[][][] out=new float[NAMES.size()][2][3];
        try(var in=StandPunch.class.getResourceAsStream("/assets/celestialfall/stands/star_platinum_fists.json")){
            var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"fists"),StandardCharsets.UTF_8)).getAsJsonObject();
            for(int s=0;s<NAMES.size();s++){
                var stance=root.getAsJsonObject(NAMES.get(s));if(stance==null)continue;
                for(int side=0;side<2;side++){
                    var p=stance.getAsJsonArray(side==0?"left":"right");
                    // model units (front -z, its right -x, up +y) to blocks ahead, to its right and up
                    out[s][side][0]=-p.get(2).getAsFloat()*SCALE;out[s][side][1]=-p.get(0).getAsFloat()*SCALE;out[s][side][2]=p.get(1).getAsFloat()*SCALE;
                }
            }
        }catch(Exception e){
            dev.sangwoo.celestialfall.CelestialFall.LOGGER.error("Star Platinum's fists could not be read",e);
        }
        return out;
    }
}
