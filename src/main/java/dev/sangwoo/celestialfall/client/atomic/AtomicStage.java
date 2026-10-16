package dev.sangwoo.celestialfall.client.atomic;

import static dev.sangwoo.celestialfall.client.atomic.AtomicLook.*;

/**
 * Timing and distance shaping of G's spectacle for viewers outside the damage column, in seconds
 * after the first skill frame: the sky and world light dimming to violet around the pillar, the
 * slowed ignition, the air shock racing out from the blast (with the jolt, FOV punch and boom it
 * brings when it reaches the viewer), the letterbox and the cloud rings. R is the damage radius;
 * the pillar itself never grows past it, only light, sky and air reach farther. Pure functions.
 */
public final class AtomicStage {
    /** The air shock starts 0.3 R out and travels 5.5 R per second. */
    public static final double SHOCK_START=.3,SHOCK_SPEED=5.5;
    /** Full strength within 16 R of the blast, nothing beyond 48 R. */
    public static final double NEAR=16,FAR=48;
    /** The ignition plays slowed for its first 0.9 s, then joins real time. */
    public static final float SLOW=.9f;
    private AtomicStage(){}

    static float clamp(double v){return (float)Math.max(0,Math.min(1,v));}

    /** 1 within 16 R, fading to 0 at 48 R. */
    public static float reach(double distance,double R){return R>0?clamp((FAR*R-distance)/((FAR-NEAR)*R)):0;}
    /** Sky and world light dimming: falls with the ignition, held while the pillar stands, back over 3 s after the beam. */
    public static float sky(float sec){return smooth(sec,IMPACT,PINK+.35f)*(1-smooth(sec,BEAM_END,BEAM_END+3f));}

    /** Visual clock of the pillar: the first 0.9 s after the impact run slowed (0.35x at first) and catch up, joining real time smoothly. */
    public static float visual(float sec){
        if(!(sec>IMPACT)||sec>=IMPACT+SLOW)return sec;
        float u=(sec-IMPACT)/SLOW;
        return IMPACT+SLOW*u*(.35f+u*(1.3f-.65f*u));
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
    /** FOV offset in degrees: a slow push-in through the slowed ignition, a punch outward when the shock arrives. */
    public static float fov(float sec,double distance,double R){
        float reach=reach(distance,R);if(reach<=0)return 0;
        float push=smooth(sec,IMPACT,IMPACT+.35f)*(1-smooth(sec,IMPACT+.5f,IMPACT+1.1f));
        return reach*(-4f*push+12f*kick(sec,arrival(distance,R)));
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
