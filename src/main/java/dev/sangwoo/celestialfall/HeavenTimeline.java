package dev.sangwoo.celestialfall;

/** GIF shot order, with an extended surface contact / slow insertion at nominal 20 TPS. */
public final class HeavenTimeline {
    public static final int FACE=20, PORTAL=28, REVEAL=32, DESCENT=62, IMPACT=100,
        EMBED_END=180, POSE_END=62, RETURN_START=220, CAMERA_END=244, FADE_START=270, END=340;
    public static final double BLADE_HEIGHT=720, BLADE_HALF_WIDTH=71, PORTAL_RADIUS=190;
    public static final double EMBED_DEPTH=96, TILT=Math.toRadians(12), CONTACT_SPEED=1.5;
    private HeavenTimeline() {}
    public static float ease(float a,float from,float to) { return SpellTimeline.smooth(SpellTimeline.progress(a,from,to)); }
    public static float visibility(float a) { return ease(a,0,4)*(1-ease(a,FADE_START,END)); }
    public static float portal(float a) { return ease(a,PORTAL,REVEAL+10)*(1-ease(a,IMPACT,IMPACT+45)); }
    public static float reveal(float a) { return ease(a,REVEAL,REVEAL+6); }
    public static double tipHeight(float a) {
        if(a<=DESCENT)return 260;
        if(a>=EMBED_END)return -EMBED_DEPTH;
        if(a<IMPACT) {
            double p=(a-DESCENT)/(IMPACT-DESCENT);
            // Hermite descent arrives with exactly the insertion's initial velocity.
            return 260*(1-3*p*p+2*p*p*p)+CONTACT_SPEED*(IMPACT-DESCENT)*p*p*(1-p);
        }
        double p=(a-IMPACT)/(EMBED_END-IMPACT);
        double slope=CONTACT_SPEED*(EMBED_END-IMPACT)/EMBED_DEPTH;
        return -EMBED_DEPTH*((3*p*p-2*p*p*p)+slope*(p*p*p-2*p*p+p));
    }
    public static float pressure(float a) { return ease(a,IMPACT,IMPACT+8)*(1-ease(a,EMBED_END-12,EMBED_END+10)); }
    public static float impact(float a) { return SpellTimeline.pulse(a,IMPACT,25); }
    public static float aftershock(float a) { return Math.max(impact(a),Math.max(pressure(a)*.23f,SpellTimeline.pulse(a,EMBED_END,24)*.5f)); }
    public static float blend(float a) { return ease(a,0,4)*(1-ease(a,RETURN_START,CAMERA_END)); }
    public static boolean posed(float a) { return a>=0 && a<POSE_END; }
    public static float shake(float a) { return a>=END?0:aftershock(a)*.85f; }
    public static float rush(float a) { return a<DESCENT || a>=IMPACT?0:ease(a,DESCENT,DESCENT+8)*(1-ease(a,IMPACT-18,IMPACT)); }
    public static int shotIndex(float a) { return a<FACE?0:a<PORTAL?1:a<DESCENT?2:a<IMPACT?3:4; }
    public record Pose(float rightPitch,float rightRoll,float elbow,float leftPitch,float leftRoll,float bodyYaw,float headPitch,float cape) {}
    public static Pose pose(float age) {
        float seal=ease(age,0,16),relax=ease(age,POSE_END-12,POSE_END),hold=1-relax;
        return new Pose((-74+seal*46)*hold,(-24-seal*22)*hold,(-30-seal*100)*hold,
            -12*hold,20*hold,-6*hold,-2*hold,hold);
    }
    /** Actor shots use block-scale coordinates; world shots use the marked impact point. */
    public record Shot(boolean actor,double radius,double height,double focusHeight,double azimuth,double fov,double roll) {}
    public static Shot shot(float age) {
        return switch(shotIndex(age)) {
            case 0 -> new Shot(true,.65,1.54,1.42,42-ease(age,0,FACE)*20,42,-50+ease(age,0,FACE)*38);
            case 1 -> new Shot(true,1.4,1.7,1.7,0,36,0);
            case 2 -> new Shot(false,2.64,.18,1.62,-24,78,-34);
            // Crop the upper sword instead of backing away to fit it. Only the gaze follows
            // a small part of the fall; the ground and the camera position stay anchored.
            case 3 -> new Shot(false,280,64,80+60*SpellTimeline.smooth((float)(tipHeight(age)/260)),0,68,0);
            default -> new Shot(false,280,64,80,0,68,0);
        };
    }
    /** Only the lower 138 blocks taper. The remaining 582 blocks form a long parallel blade. */
    public static double halfWidth(double y) {
        double[] ys={0,24,58,100,138,240,400,580,680,720};
        double[] ws={0,17,38,59,69,71,71,70,66,62};
        if(y<=0)return 0;if(y>=720)return 62;
        for(int i=1;i<ys.length;i++)if(y<=ys[i])return ws[i-1]+(ws[i]-ws[i-1])*(y-ys[i-1])/(ys[i]-ys[i-1]);
        return 62;
    }
}
