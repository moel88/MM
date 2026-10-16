package dev.sangwoo.celestialfall.client.atomic;

import static dev.sangwoo.celestialfall.client.atomic.AtomicLook.*;

/**
 * Timing and distance shaping of G's spectacle for viewers outside the damage column, in seconds
 * after the first skill frame: the sky and world light dimming to violet around the pillar, the
 * slowed ignition, the air shock racing out from the blast (with the jolt, FOV punch and boom it
 * brings when it reaches the viewer), the letterbox and the cloud rings, and near the pillar the
 * surges racing up it, the earthquake and the overwhelming screen treatment. R is the damage radius;
 * the pillar itself never grows past it, only light, sky and air reach farther. Pure functions.
 */
public final class AtomicStage {
    /** The air shock starts 0.3 R out and travels 5.5 R per second. */
    public static final double SHOCK_START=.3,SHOCK_SPEED=5.5;
    /** Full strength within 16 R of the blast, nothing beyond 48 R. */
    public static final double NEAR=16,FAR=48;
    /** The ignition plays slowed for its first 0.9 s, then joins real time. */
    public static final float SLOW=(float)dev.sangwoo.celestialfall.AtomicTimeline.IGNITION_SLOW;
    private AtomicStage(){}

    static float clamp(double v){return (float)Math.max(0,Math.min(1,v));}

    /** 1 within 16 R, fading to 0 at 48 R. */
    public static float reach(double distance,double R){return R>0?clamp((FAR*R-distance)/((FAR-NEAR)*R)):0;}
    /** Sky and world light dimming: falls with the ignition, held while the pillar stands, back over 3 s after the beam. */
    public static float sky(float sec){return smooth(sec,IMPACT,PINK+.35f)*(1-smooth(sec,BEAM_END,BEAM_END+3f));}

    /** Visual clock of the pillar: the first 0.9 s after the impact run slowed (0.35x at first) and catch up, joining real time smoothly. */
    public static float visual(float sec){
        if(!(sec>IMPACT)||sec>=IMPACT+SLOW)return sec;
        return IMPACT+(float)dev.sangwoo.celestialfall.AtomicTimeline.pillarClock(sec-IMPACT);
    }

    /** Radius of the air shock front, in blocks from the blast centre. */
    public static double shockRadius(float sec,double R){return sec<IMPACT?0:R*(SHOCK_START+SHOCK_SPEED*(sec-IMPACT));}
    /** When the shock reaches a viewer this far (straight-line distance) from the blast centre. */
    public static float arrival(double distance,double R){return IMPACT+(float)(Math.max(0,distance/R-SHOCK_START)/SHOCK_SPEED);}
    /** The hit when the shock arrives: a 0.05 s rise, then a 0.35 s decay. */
    public static float kick(float sec,float arrival){
        if(!(sec>=arrival))return 0;
        float t=sec-arrival;
        return smooth(t,0,.05f)*(float)Math.exp(-Math.max(0,t-.05f)/.35f);
    }
    /**
     * FOV offset in degrees: a slow push-in through the slowed ignition, a punch outward when the shock arrives,
     * and near the pillar a throb outward with every surge. Never more than 12 degrees.
     */
    public static float fov(float sec,double distance,double R){
        float reach=reach(distance,R);if(reach<=0)return 0;
        float push=smooth(sec,IMPACT,IMPACT+.35f)*(1-smooth(sec,IMPACT+.5f,IMPACT+1.1f));
        float k=near(distance,R);
        return Math.min(12f,reach*(-4f*push+12f*kick(sec,arrival(distance,R)))+2.2f*k*surge(sec));
    }
    /** Camera jolt amplitude in degrees when the shock arrives; weaker farther out. */
    public static float jolt(float sec,double distance,double R){
        return 1.8f*kick(sec,arrival(distance,R))*reach(distance,R)*(float)Math.max(.35,1-distance/(FAR*R));
    }
    /** Brightness of the air shock: fades as it spreads past 18 R and is gone at 30 R. */
    public static float shockLevel(float sec,double R){
        double r=shockRadius(sec,R)/R;
        return sec<IMPACT?0:(1-smooth((float)r,18,30))*smooth(sec,IMPACT,IMPACT+.08f);
    }

    /** Letterbox bar height as a fraction of the screen height. */
    public static float letterbox(float sec,double distance,double R){
        return .085f*smooth(sec,IMPACT,IMPACT+.6f)*(1-smooth(sec,BEAM_END+.4f,BEAM_END+1.6f))*reach(distance,R);
    }

    /** Nearness to the pillar for the quake and the post effect: 1 within 1.5 R, gone at 8 R. */
    public static float near(double distance,double R){return R>0?clamp((8*R-distance)/(6.5*R)):0;}
    /** A surge of light leaves the pillar's base every 0.8 s from the pink frame until the beam ends, racing up at 14 R/s. */
    public static final float SURGE_PERIOD=.8f;
    public static final double SURGE_SPEED=14;
    /** Seconds since the latest surge left the base, or -1 when none. */
    public static float surgeAge(float sec){
        if(!(sec>=PINK)||sec>=BEAM_END)return -1;
        float a=(sec-PINK)%SURGE_PERIOD;
        return a<0?a+SURGE_PERIOD:a;
    }
    /** Height of the latest surge's front above the pillar's base, in blocks (-1 when none). */
    public static double surgeFront(float sec,double R){float a=surgeAge(sec);return a<0?-1:a*SURGE_SPEED*R;}
    /** Brightness of the climbing surge: flashes on at the base and fades as it climbs. */
    public static float surgeGlow(float sec){float a=surgeAge(sec);return a<0?0:smooth(a,0,.04f)*(1-.6f*a/SURGE_PERIOD);}
    /** The pulse each surge sends through the ground and the air (shared by the quake, the FOV throb and the post effect). */
    public static float surge(float sec){
        float a=surgeAge(sec);if(a<0)return 0;
        float s=a/SURGE_PERIOD;
        return smooth(s,0,.08f)*(1-smooth(s,.08f,.45f));
    }
    /** While the pillar stands: on with the ignition, off over 2 s after the beam. */
    public static float standing(float sec){return smooth(sec,IMPACT,PINK+.3f)*(1-smooth(sec,BEAM_END,BEAM_END+2f));}
    /** Earthquake felt near the pillar from outside (degrees): a steady quake with a kick on every surge. */
    public static float quake(float sec,double distance,double R){
        float k=near(distance,R);
        return k<=0?0:k*k*standing(sec)*(1.1f+.9f*surge(sec));
    }
    /**
     * How strongly the pillar's light pours into the view of an outside viewer (glow and light shafts), 0..1.3:
     * a burst with the ignition flash, held while the pillar stands, thinning with distance like the sky.
     */
    public static float radiance(float sec,double distance,double R){
        float k=reach(distance,R);if(k<=0)return 0;
        float flash=smooth(sec,IMPACT,IMPACT+.06f)*(1-smooth(sec,PINK,PINK+.6f));
        return k*Math.min(1.3f,standing(sec)+.5f*flash);
    }
    /** Strength of the overwhelming screen treatment near the pillar (0..1). */
    public static float overwhelm(float sec,double distance,double R){
        float k=near(distance,R);
        return k<=0?0:k*standing(sec)*(.75f+.25f*surge(sec));
    }

    /** Cloud ring at cloud height, outer radius in R: punched out by the blast, racing to about 14 R. */
    public static double cloudRing(float sec){
        float t=sec-(IMPACT+.15f);
        return t<=0?0:1+13*(1-Math.exp(-t/1.6));
    }
    public static float cloudRingLevel(float sec){
        float t=sec-(IMPACT+.15f);
        return t<=0?0:smooth(t,0,.3f)*(1-smooth(t,3.5f,7.5f));
    }
    /** Condensation ring around the pillar at mid height: from just outside R to about 3.2 R in 1.6 s. */
    public static double condensationRing(float sec){
        float t=sec-(IMPACT+.05f);
        return t<=0?0:1.05+2.15*(1-Math.exp(-t/.55));
    }
    public static float condensationLevel(float sec){
        float t=sec-(IMPACT+.05f);
        return t<=0?0:smooth(t,0,.12f)*(1-smooth(t,.6f,1.8f));
    }
}
