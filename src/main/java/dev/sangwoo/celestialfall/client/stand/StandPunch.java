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
 * front of its user, where it stands there and which way it faces, how far it dashes on, the gust through its hair and
 * how strongly its afterimages show. Every punch starts from the guard, loads (its anticipation), holds the load a
 * moment, then strikes faster and faster to land at its contact (StarPlatinumRules.CONTACT, the "RA" of its shout),
 * freezes there for a few frames (the hit-stop), holds the blow and goes back to the guard. The first punch carries the
 * stand round from its place at its user's shoulder to the front as it loads; it stays in front, in its guard, while
 * punches come, and goes back once they stop. A new punch takes over from the one before over a few frames, so nothing
 * jumps. Since 2.55.0 (user request: "스타플래티나 평타 모션을 더 격하게 만들어주고 스타플래티나가 플레이어 바로 앞에서
 * 때리게 바꿔줘") it punches from right in front of its user, on the line of the punch, dashing on into each blow as
 * far as it takes to meet the target (within bounds) and back; the loads are deeper, the strikes snappier (a tenth of
 * a second), the hit-stops longer, and five afterimages follow each strike. Since 2.56.0 (user request: "평타 속도를 더
 * 빠르게 하자 ... 더 격하게 때리게 모션을") the jab and the cross come three tenths of a second apart and land a fifth of
 * a second in, each punch takes over from the last before it is back in its guard (a combination, not four punches),
 * the stand comes round to the front faster, every blow drives the stand on through its target a little further
 * than the dash (a shove as it lands), the dashes are longer, and the stances fiercer still.
 */
public final class StandPunch {
    /** A punch as a client saw it: its number (1 to 4), the way it goes (degrees), how far ahead its target stands (blocks, NaN for none), and the time on its own clock (seconds) it was stopped short (NaN if it was not). */
    public record Punch(int combo,float yaw,float aim,float stopped){
        public Punch stop(float at){return new Punch(combo,yaw,aim,at);}
    }
    /** What a frame takes from the punches: the stances' shares (adding up to 1), how far out in front of its user the stand is (0 at its shoulder, 1 in front), the way it faces, how far ahead and to the right of its user it stands, how far it lunges on (blocks, the shove of the blow with it), the gust and the afterimages' strength. */
    public static final class Shape {
        /**
         * The newest punch's stances, and while it takes over from the one before (2.56.0) that one's, and how far it
         * has taken over (1 once it has); that pose can itself be turning from one under it (2.57.0: a punch stopped
         * short turns from where it was back to the guard, the rush and a punch take over from each other).
         */
        public final float[] weights=new float[NAMES.size()],under=new float[NAMES.size()],under2=new float[NAMES.size()];
        /** How much higher than at rest it floats in front (blocks): RAISE for the punches, less for the rush (2.57.0). */
        public float engage,yaw,ahead,side,lunge,gust,ghost,over=1,over2=1,raise=RAISE;
        /** How big its left and right arms are (1 their size: the punches; bigger through the rush, 2.58.0). */
        public float armL=1,armR=1;
        void clear(){Arrays.fill(weights,0);Arrays.fill(under,0);Arrays.fill(under2,0);engage=yaw=ahead=side=lunge=gust=ghost=0;over=over2=1;raise=RAISE;armL=armR=1;}
        Shape set(Shape o){
            System.arraycopy(o.weights,0,weights,0,weights.length);System.arraycopy(o.under,0,under,0,under.length);System.arraycopy(o.under2,0,under2,0,under2.length);
            engage=o.engage;yaw=o.yaw;ahead=o.ahead;side=o.side;lunge=o.lunge;gust=o.gust;ghost=o.ghost;over=o.over;over2=o.over2;raise=o.raise;armL=o.armL;armR=o.armR;return this;
        }
        /** How much of stance {@code i} the shape has, its poses under and its own taken together. */
        float share(int i){return over>=1?weights[i]:weights[i]*over+(over2>=1?under[i]:under[i]*over2+under2[i]*(1-over2))*(1-over);}
        /**
         * This shape taking over from {@code from} (2.57.0: the rush from a punch, or a punch from the rush), {@code k}
         * of the way (0 to 1): turning from the other's pose as it is now, the other turning from its own pose under if
         * it is (in place of a pose this one was turning from), on a way, a place and a dash between the two, out in
         * front as far as either is, in the stronger gust.
         */
        void takeOver(Shape from,float k){
            if(k<1){
                System.arraycopy(from.weights,0,under,0,under.length);
                if(from.over<1){
                    // the other's pose under, with the one under that weighed into it (there is no room for a third)
                    for(int i=0;i<under2.length;i++)under2[i]=from.over2>=1?from.under[i]:from.under[i]*from.over2+from.under2[i]*(1-from.over2);
                    over2=from.over;
                }else{Arrays.fill(under2,0);over2=1;}
                over=Math.max(0,k);
            }
            between(from,k);
        }
        /**
         * This shape, itself turning from its pose under to its own (the rush let go: its fists going back to the guard),
         * taking over from {@code from} {@code k} of the way: the other's pose, weighed into one, goes under its pose
         * under. Not quite turning from the one to the other, but as it began, and as it ends, the same; and smooth.
         */
        void underneath(Shape from,float k){
            if(k<1){for(int i=0;i<under2.length;i++)under2[i]=from.share(i);over2=Math.max(0,k);}
            between(from,k);
        }
        /** The way, place, dash and arms {@code k} of the way from the other's to this one's, out in front as far as either is, in the stronger gust. */
        private void between(Shape from,float k){
            if(k<1){
                yaw=from.yaw+wrap(yaw-from.yaw)*k;raise+=(from.raise-raise)*(1-k);
                ahead+=(from.ahead-ahead)*(1-k);side+=(from.side-side)*(1-k);lunge+=(from.lunge-lunge)*(1-k);ghost+=(from.ghost-ghost)*(1-k);
                armL+=(from.armL-armL)*(1-k);armR+=(from.armR-armR)*(1-k);
            }
            engage=Math.max(engage,from.engage);gust=Math.max(gust,from.gust);
        }
        /**
         * The punches on a look, as far as the stand has come out in front ({@code engage}): in front, the punch drawn
         * as it is, turning from the punch before as it takes over; on its way round, the look's stances and the
         * punches' weighed together.
         */
        public void onto(dev.sangwoo.celestialfall.stand.StarPlatinumLook look){
            float e=engage;
            if(e>=1){
                System.arraycopy(weights,0,look.weights,0,weights.length);
                System.arraycopy(under,0,look.under,0,under.length);look.over=over;
                System.arraycopy(under2,0,look.under2,0,under2.length);look.over2=over2;
                look.armL=armL;look.armR=armR;
                return;
            }
            look.flatten();
            for(int i=0;i<look.weights.length;i++)look.weights[i]=look.weights[i]*(1-e)+share(i)*e;
            look.armL+=(armL-look.armL)*e;look.armR+=(armR-look.armR)*e;
        }
    }
    /** When each punch lands (seconds after it starts) and how long it then stands still (the hit-stop: longer for the heavy ones). */
    public static final float[] HIT=new float[5],FREEZE={0,.05f,.05f,.08f,.13f};
    static{for(int k=1;k<=4;k++)HIT[k]=StarPlatinumRules.CONTACT[k]/20f;}
    /** Ease into a key: smoothly, holding the stance, or striking (faster and faster, at full speed as it lands). */
    static final int SMOOTH=0,HOLD=1,STRIKE=2;
    record Key(float at,int stance,int ease){}
    static Key k(float at,int stance,int ease){return new Key(at,stance,ease);}
    /**
     * Each punch's keys on its own clock with the hit-stop taken out: 1 the jab, 2 the cross, 3 the hook, 4 the
     * finisher, which holds the blow out while its target flies. Each loads, holds the load a moment (the coil before
     * the blow) and strikes in a tenth of a second (the hook and the finisher in 0.12). Since 2.56.0 the jab and the
     * cross load in 0.08 s and land at 0.2 s (StarPlatinumRules.CONTACT), and the next punch comes before they are back
     * in their guard; the hook and the finisher load all through the "O" of their shouts.
     */
    static final Key[][] KEYS={null,
        {k(0,GUARD,SMOOTH),k(.08f,WIND_L,SMOOTH),k(.1f,WIND_L,HOLD),k(.2f,PUNCH_L,STRIKE),k(.24f,PUNCH_L,HOLD),k(.44f,GUARD,SMOOTH)},
        {k(0,GUARD,SMOOTH),k(.08f,WIND_R,SMOOTH),k(.1f,WIND_R,HOLD),k(.2f,PUNCH_R,STRIKE),k(.24f,PUNCH_R,HOLD),k(.44f,GUARD,SMOOTH)},
        {k(0,GUARD,SMOOTH),k(.24f,WIND_HOOK,SMOOTH),k(.34f,WIND_HOOK,HOLD),k(.45f,HOOK,STRIKE),k(.5f,HOOK,HOLD),k(.76f,GUARD,SMOOTH)},
        {k(0,GUARD,SMOOTH),k(.26f,WIND_BIG,SMOOTH),k(.38f,WIND_BIG,HOLD),k(.5f,PUNCH_BIG,STRIKE),k(.72f,PUNCH_BIG,HOLD),k(1f,GUARD,SMOOTH)}};
    /** The stance each punch loads in, the one it strikes in, and the fist it strikes with (true: the right). */
    static final int[] WINDS={0,WIND_L,WIND_R,WIND_HOOK,WIND_BIG},STRIKES={0,PUNCH_L,PUNCH_R,HOOK,PUNCH_BIG};
    static final boolean[] RIGHT={false,false,true,false,true};
    /**
     * How far the stand dashes on into each punch as it lands (blocks): as far as it takes for the fist to sink BITE
     * into its target, but never less than the first nor more than the second (it stays right in front of its user).
     */
    static final float[] LUNGE_MIN={0,.3f,.35f,.4f,.6f},LUNGE_MAX={0,.85f,.9f,.95f,1.3f};
    /** How far each blow shoves the stand on through its target as it lands (blocks, in over two frames and soon gone, 2.56.0). */
    static final float[] DRIVE={0,.12f,.12f,.18f,.3f};
    /** Gust through the hair as each punch lands. */
    static final float[] GUST={0,1f,1.05f,1.3f,1.8f};
    /**
     * Where the stand stands to punch (2.55.0): right in front of its user, AHEAD blocks on along the punch and on its
     * line (SIDE 0), facing straight down it, higher than at rest (RAISE) so its shoulders and fists show over its
     * user's head from the game's camera behind them.
     */
    static final float AHEAD=.95f,SIDE=0,BITE=.15f,RAISE=.3f;
    /** Seconds: the first punch brings it round to the front (off fast, settling in, there before the jab lands); it turns to a new way. */
    static final float RISE=.18f,TURN=.12f;
    /**
     * Seconds a new punch takes to take over from the last, by the new one (2.56.0): the jab and the cross snap in over
     * the last blow, the hook and the finisher take longer (their arms come right round from the blow before, all
     * through the first of their loads).
     */
    static final float[] BLEND={0,.1f,.12f,.2f,.2f};
    /** It stays in front this long once a punch is over (seconds; less once one is stopped short), then goes back over RETURN. */
    static final float LINGER=.45f,LINGER_STOPPED=.15f,RETURN=.4f;
    /** The stand's size, for the fists' places (StarPlatinumRenderer.SCALE: model units of 1/16 block). */
    static final float SCALE=StarPlatinumRenderer.SCALE/16f;
    /** The striking fist's knuckles in each stance (blocks from the stand's feet: ahead, to its right, up), by stance and fist (0 left, 1 right). */
    static final float[][][] FISTS=fists(""),SHOULDERS=fists("_shoulder");
    private StandPunch(){}

    static float clamp(float v){return v<0?0:v>1?1:v;}
    static float smooth(float v){v=clamp(v);return v*v*(3-2*v);}
    /** A strike gathers speed all the way in, at its quickest as it lands (1.6 times its mean speed): fast, but no joint leaps a frame. */
    static float ease(int ease,float x){x=clamp(x);return ease==STRIKE?(float)Math.pow(x,1.6):smooth(x);}
    /** Off fast and settling in: still at both ends, at its quickest a third of the way (as the stand's summon burst). */
    static float burst(float u){u=clamp(u);float r=1-u;return 1-r*r*r*(1+3*u);}
    /** A kick that comes in at {@code at} over two frames and dies away with time constant {@code tau} (seconds). */
    static float kick(float t,float at,float tau){return t<at-.03f?0:smooth((t-at+.03f)/.045f)*(float)Math.exp(-Math.max(0,t-at-.015f)/tau);}

    /** How far back to its guard a punch stopped short is {@code since} seconds after it stopped (StarPlatinumRules.STOP_RETURN). */
    static float back(float since){return smooth(since/(StarPlatinumRules.STOP_RETURN/20f));}
    /** The punch's own clock as its body moves on it: time stands still for the hit-stop. */
    public static float warp(int combo,float t){float hit=HIT[combo];return t<hit?t:t<hit+FREEZE[combo]?hit:t-FREEZE[combo];}
    /** How long a punch shows (seconds): its keys and its stand in front, then its way back. */
    public static float length(Punch p){return Float.isNaN(p.stopped)?StarPlatinumRules.LENGTH[p.combo]/20f+LINGER+RETURN:p.stopped+LINGER_STOPPED+RETURN+.2f;}

    /** Adds the stances of punch {@code p} at {@code t} seconds on its clock into {@code w}, {@code share} of them. */
    static void stances(Punch p,float t,float[] w,float share){
        if(!Float.isNaN(p.stopped)&&t>p.stopped){
            // stopped short: from where it was back to the guard
            float back=back(t-p.stopped);
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
    /** How strongly this punch strikes now (its striking stance's share): what the dash and the speed go by. */
    static float striking(Punch p,float t){float[] w=new float[NAMES.size()];stances(p,t,w,1);return w[STRIKES[p.combo]];}
    /**
     * How far the stand dashes on into this punch as it lands (blocks): for its fist (in its striking stance, from
     * AHEAD) to sink BITE into its target's near side, within LUNGE_MIN and LUNGE_MAX; the least with nobody there.
     */
    public static float lunge(Punch p){
        float min=LUNGE_MIN[p.combo],max=LUNGE_MAX[p.combo];
        if(Float.isNaN(p.aim))return min;
        float need=p.aim+BITE-(AHEAD+FISTS[STRIKES[p.combo]][RIGHT[p.combo]?1:0][0]);
        return Math.max(min,Math.min(max,need));
    }
    /** When the punch's strike starts on its clock (seconds): the key before its striking one. */
    static float strikeFrom(int combo){var keys=KEYS[combo];for(int i=1;i<keys.length;i++)if(keys[i].ease==STRIKE)return keys[i-1].at;return HIT[combo];}
    /** The striking fist's knuckles at the contact of punch {@code combo}: blocks from the stand's feet ahead, to its right and up (at its size 1). */
    public static float[] fist(int combo){return FISTS[STRIKES[combo]][RIGHT[combo]?1:0].clone();}
    /** The same fist loaded (drawn back in the punch's loading stance), where its strike sets out from. */
    public static float[] windFist(int combo){return FISTS[WINDS[combo]][RIGHT[combo]?1:0].clone();}

    /**
     * The frame's shape from the newest punch ({@code now}, {@code t} seconds on its clock) and the one before it (null
     * if none; {@code tb} on its clock). The punches' stances add up to 1; how much of them the stand takes, and how far
     * it has come round to the front, is the engagement.
     */
    public static Shape at(Punch now,float t,Punch before,float tb,Shape into){
        into.clear();
        float take=before==null?1:smooth(t/BLEND[now.combo]);
        if(!Float.isNaN(now.stopped)&&t>now.stopped){
            // stopped short (2.57.0: turned, not weighed in): from where it was then, turning from the punch before if it
            // still was, back to the guard. Weighed in, three stances far apart could be weighed two ways and leap.
            float since=t-now.stopped,then=before==null?1:smooth(now.stopped/BLEND[now.combo]);
            into.weights[GUARD]=1;
            stances(now.combo,warp(now.combo,now.stopped),into.under,1);into.over=back(since);
            if(then<1){stances(before,tb-since,into.under2,1);into.over2=then;}
        }else{
            // the newest punch's stances, and the last one's under them while it takes over (turned from, not weighed in)
            stances(now,t,into.weights,1);
            if(take<1){stances(before,tb,into.under,1);into.over=take;}
        }
        into.engage=Math.max(envelope(now,t),before==null?0:envelope(before,tb));
        float turn=before==null?1:smooth(t/TURN);
        into.yaw=before==null?now.yaw:before.yaw+wrap(now.yaw-before.yaw)*turn;
        into.ahead=AHEAD;into.side=SIDE;
        into.lunge=lunge(now)*striking(now,t)*take+(before==null?0:lunge(before)*striking(before,tb)*(1-take))+drive(now,t)+(before==null?0:drive(before,tb));
        into.gust=GUST[now.combo]*kick(t,HIT[now.combo],.25f)+(before==null?0:GUST[before.combo]*kick(tb,HIT[before.combo],.25f));
        into.ghost=ghost(now,t);
        return into;
    }
    /** The shove of a blow as it lands (blocks): in over two frames, gone in a few more (none for a punch stopped before it landed). */
    static float drive(Punch p,float t){
        if(!Float.isNaN(p.stopped)&&p.stopped<HIT[p.combo])return 0;
        return DRIVE[p.combo]*kick(t,HIT[p.combo],.07f);
    }
    /** The afterimages: from the moment the strike starts, through the blow and its hit-stop, gone soon after (not for a punch stopped before it landed). */
    static float ghost(Punch p,float t){
        float hit=HIT[p.combo];
        if(!Float.isNaN(p.stopped)&&p.stopped<hit)return 0;
        return smooth((t-strikeFrom(p.combo))/.03f)*(1-smooth((t-hit-FREEZE[p.combo])/.1f));
    }
    static float wrap(float degrees){degrees%=360;if(degrees>=180)degrees-=360;if(degrees<-180)degrees+=360;return degrees;}

    /** Reads where the fists are in every stance (tools/stand/make_star_platinum.py writes it with the poses). */
    /**
     * Where fist {@code side} (0 left, 1 right) is in stance {@code stance} with its arm drawn {@code size} times its size
     * (2.58.0: the rush's bigger arms), out from its shoulder that much further (blocks from the stand's feet, as FISTS).
     */
    static float[] grown(int stance,int side,float size){
        float[] f=FISTS[stance][side],s=SHOULDERS[stance][side];
        return new float[]{s[0]+(f[0]-s[0])*size,s[1]+(f[1]-s[1])*size,s[2]+(f[2]-s[2])*size};
    }
    /** The fists' knuckles in every stance, or (with {@code "_shoulder"}) their shoulders' joints, from the file the generator writes with the poses. */
    private static float[][][] fists(String what){
        float[][][] out=new float[NAMES.size()][2][3];
        try(var in=StandPunch.class.getResourceAsStream("/assets/celestialfall/stands/star_platinum_fists.json")){
            var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"fists"),StandardCharsets.UTF_8)).getAsJsonObject();
            for(int s=0;s<NAMES.size();s++){
                var stance=root.getAsJsonObject(NAMES.get(s));if(stance==null)continue;
                for(int side=0;side<2;side++){
                    var p=stance.getAsJsonArray((side==0?"left":"right")+what);
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
