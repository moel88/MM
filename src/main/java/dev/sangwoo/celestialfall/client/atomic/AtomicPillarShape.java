package dev.sangwoo.celestialfall.client.atomic;

import dev.sangwoo.celestialfall.AtomicTimeline;

/**
 * Shape and timing of G's light pillar seen from outside, in seconds after the first skill frame.
 * Pure functions (no rendering) shared by {@link AtomicPillar} and the tests. R is the server's
 * damage radius: the pillar is one straight cylinder whose radius is the same at every height and
 * never more than R; it opens out of the caster and closes back into it at every height at once,
 * while its base and top stay where they are.
 */
public final class AtomicPillarShape {
    /** The pillar reaches the full damage radius at 6.8 s. */
    public static final float OPEN=6.8f;
    /** Height above the ground in blast radii, how far it reaches under the ground (blocks, the damage column's floor) and its first-frame radius. */
    public static final double HEIGHT_RADII=9,FOOT=2,HAIRLINE=.04;
    private AtomicPillarShape(){}

    /** Radius now: a hairline at the impact, R at {@link #OPEN}, held, then drawn in to nothing from the collapse to the end of the beam. */
    public static double radius(float sec,double R){
        if(!(sec>=AtomicLook.IMPACT)||sec>=AtomicLook.BEAM_END||!(R>0))return 0;
        double u=AtomicLook.unit(sec,AtomicLook.IMPACT,OPEN),open=1-Math.pow(1-u,3);
        double r=R*(HAIRLINE+(1-HAIRLINE)*open);
        if(sec>=AtomicLook.COLLAPSE)r*=Math.pow(1-AtomicLook.unit(sec,AtomicLook.COLLAPSE,AtomicLook.BEAM_END),1.6);
        return Math.min(R,r);
    }
    /** Bottom of the cylinder, two blocks under the ground like the damage column. */
    public static double base(double groundY){return groundY-FOOT;}
    /** Top of the cylinder: shoots up to 9 R in the first 0.12 s and stays there. */
    public static double top(float sec,double groundY,double R){
        double u=AtomicLook.unit(sec,AtomicLook.IMPACT,AtomicLook.IMPACT+.12f);
        return groundY+HEIGHT_RADII*R*(1-Math.pow(1-u,3));
    }
    /** A camera this close to the local player's eyes is their own view (first or third person). */
    public static final double OWN_CAMERA=16;
    /** Inside the damage column, with the server's own test. */
    public static boolean inside(double dx,double dy,double dz,double R){return AtomicTimeline.insideColumn(dx,dy,dz,R);}

    /** Ignition heat: the pillar is born white and its violet structure shows by 6.5 s. */
    public static float heat(float sec){return 1-AtomicLook.smooth(sec,AtomicLook.PINK,6.5f);}
    /** The pillar whitens while it closes. */
    public static float whiteOut(float sec){return .55f*AtomicLook.smooth(sec,AtomicLook.COLLAPSE,AtomicLook.BEAM_END-.1f);}
    /** Clock of the rising streams: real seconds, up to 1.6x faster while the pillar closes (integrated, so the pattern never jumps). */
    public static float flowTime(float sec){
        float d=AtomicLook.BEAM_END-AtomicLook.COLLAPSE,u=AtomicLook.unit(sec,AtomicLook.COLLAPSE,AtomicLook.BEAM_END);
        return sec+.6f*d*(u*u*u-u*u*u*u*.5f)+.6f*Math.max(0,sec-AtomicLook.BEAM_END);
    }
    /** Brightness of the energy streaks: up with the ignition, gone with the pillar. */
    public static float streaks(float sec){return AtomicLook.smooth(sec,AtomicLook.IMPACT,AtomicLook.PINK+.25f)*opacity(sec);}
    /** Solid while it stands; gone over the last frames of the beam. */
    public static float opacity(float sec){return 1-AtomicLook.smooth(sec,AtomicLook.BEAM_END-.08f,AtomicLook.BEAM_END);}
    /** Magenta light on the floor: on with the ignition, fading 1.5 s after the beam. */
    public static float groundGlow(float sec){
        return AtomicLook.smooth(sec,AtomicLook.PINK,AtomicLook.PINK+.3f)*(1-AtomicLook.smooth(sec,AtomicLook.BEAM_END,AtomicLook.BEAM_END+1.5f));
    }
    /** Ignition wave on the ground: runs ahead of the opening pillar and stops at R. */
    public static double ignitionFront(float sec,double R){
        float u=AtomicLook.unit(sec,AtomicLook.IMPACT,AtomicLook.IMPACT+.6f);
        return sec<AtomicLook.IMPACT?0:R*(1-Math.pow(1-u,3));
    }
    /** Brightness of the ignition wave: on at once, fading after it reaches R. */
    public static float ignitionLevel(float sec){
        return sec<AtomicLook.IMPACT?0:AtomicLook.smooth(sec,AtomicLook.IMPACT,AtomicLook.IMPACT+.05f)*(1-AtomicLook.smooth(sec,AtomicLook.IMPACT+.45f,AtomicLook.IMPACT+.95f));
    }
    /** Shimmer where the pillar stood: rises as the beam goes out and dies away over the dust. */
    public static float heatColumn(float sec){
        return AtomicLook.smooth(sec,AtomicLook.BEAM_END-.3f,AtomicLook.BEAM_END+.3f)*(1-AtomicLook.smooth(sec,AtomicLook.BEAM_END+.8f,24.5f));
    }
}
