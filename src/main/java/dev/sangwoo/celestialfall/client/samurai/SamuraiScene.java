package dev.sangwoo.celestialfall.client.samurai;

import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.SLASH_SEC;

/**
 * The samurai's G's cutscene laid out (2.143.0, from the user's video, its seconds): its parts, its camera's shots, where
 * its two are shown, how the one marked is turned, the four blades of its black ground and their cuts. Everything is a
 * function of its clock and of where the two stand ({@code spot} its user's feet beside the one marked, {@code hold} the
 * one marked's, {@code f} the way its user came, level), the same on every client. {@code side} is the side of that way its
 * user stands beside the one marked (1 its right, as the server puts them where there is room: its user at the right of
 * every shot then, as the video's; -1 the mirror).
 * <p>
 * Its parts ({@link #part}): the dash's flat frames (white, two of ink, the burst); the close shot in the world, its
 * user's flourish in front, the one marked behind; the flash's flat frames (a line on black, black bodies on white, white
 * bodies on black twice, the character for death, black); the world gone grey seen level from further back, closing to a ring
 * round the one marked; the ring's two flat frames; the black ground with its grid and the four blades; grey, white, and
 * the world back.
 */
public final class SamuraiScene {
    private SamuraiScene(){}
    /** Its parts' ends (seconds). */
    public static final float DASH=1f,CLOSE=1.2f,FLASH=3.2f,GREY=3.4f,RING=4.2f,VOID=4.2667f,OUT=6.6667f,WORLD=6.7667f;
    /** The flash's flat frames (a thirtieth of a second each, from FLASH). */
    static final float FRAME=1/30f;
    public enum Part{BEFORE,DASH_WHITE,DASH_INK_A,DASH_INK_B,DASH_BURST,CLOSE_UP,CUT_LINE,CUT_WASH,CUT_BODIES,CUT_SIGN,CUT_BLACK,GREY_WORLD,RING_WHITE,RING_BLACK,VOID_GROUND,OUT_GREY,OUT_WHITE,AFTER}

    public static Part part(float sec){
        if(sec<DASH)return Part.BEFORE;
        if(sec<DASH+FRAME)return Part.DASH_WHITE;
        if(sec<DASH+3*FRAME)return Part.DASH_INK_A;
        if(sec<DASH+4*FRAME)return Part.DASH_INK_B;
        if(sec<CLOSE)return Part.DASH_BURST;
        if(sec<FLASH)return Part.CLOSE_UP;
        if(sec<FLASH+FRAME)return Part.CUT_LINE;
        if(sec<FLASH+2*FRAME)return Part.CUT_WASH;
        if(sec<FLASH+4*FRAME)return Part.CUT_BODIES;
        if(sec<FLASH+5*FRAME)return Part.CUT_SIGN;
        if(sec<GREY)return Part.CUT_BLACK;
        if(sec<RING)return Part.GREY_WORLD;
        if(sec<RING+FRAME)return Part.RING_WHITE;
        if(sec<VOID)return Part.RING_BLACK;
        if(sec<OUT)return Part.VOID_GROUND;
        if(sec<OUT+2*FRAME)return Part.OUT_GREY;
        if(sec<WORLD)return Part.OUT_WHITE;
        return Part.AFTER;
    }
    /** Whether the world is left out of the frame in this part (a flat ground cleared instead, {@link #ground}). */
    public static boolean flat(Part p){
        return switch(p){case CUT_LINE,CUT_WASH,CUT_BODIES,CUT_SIGN,CUT_BLACK,RING_WHITE,RING_BLACK,VOID_GROUND,OUT_GREY,OUT_WHITE,DASH_WHITE,DASH_INK_A,DASH_INK_B,DASH_BURST->true;default->false;};
    }
    /** The flat ground's grey in a flat part (0 black to 1 white). */
    public static float ground(Part p){
        return switch(p){case CUT_WASH,RING_WHITE,DASH_WHITE,OUT_WHITE->1;case OUT_GREY->.72f;default->0;};
    }
    /** The bodies' grey in a flat part (they are flat shapes there), or -1 where none is drawn. */
    public static float body(Part p,boolean target){
        return switch(p){
            case CUT_WASH->0;case CUT_BODIES->1;
            case RING_WHITE->target?1:-1;case RING_BLACK->target?0:-1;
            case VOID_GROUND->target?1:-1;
            default->-1;
        };
    }
    /** Whether the camera is the cutscene's at {@code sec} (from the dash until the world is back). */
    public static boolean shown(float sec){return sec>=DASH&&sec<WORLD;}

    // ───────────────────────────── the shots ─────────────────────────────

    /** A shot: where the eye is, what it looks at, its lens (degrees, up and down) and its roll. */
    public record Shot(Vec3d eye,Vec3d look,double fov,float roll){}
    static Vec3d right(Vec3d f){return new Vec3d(-f.z,0,f.x);}

    /** The close shot's lens and the side shots'. */
    public static final double FOV_CLOSE=48,FOV_SIDE=40;
    /** The side shots' distance from the one marked: the grey world's, and the black ground's as it cuts in and as it ends. */
    public static final double SIDE_GREY=9.2,SIDE_VOID=12.4,SIDE_VOID_END=11.9;

    /**
     * The camera at {@code sec}. Close: low in front of its user (who faces it, the way they came), the one marked behind
     * them at the other side of the frame, pushing in a little. From the flash: level from the side, the one marked in
     * the middle (who faces it: the eye stays the way its user came from, further back and level); further off again over
     * the black ground, its horizon at their feet.
     */
    public static Shot shot(Vec3d spot,Vec3d hold,Vec3d f,int side,float sec){
        Vec3d r=right(f);
        if(sec<FLASH){
            // which side of the one marked its user stands (the frame's other half)
            double aside=spot.subtract(hold).dotProduct(r);
            Vec3d focus=spot.lerp(hold,.4).add(0,1.0,0);
            double u=unit((sec-CLOSE)/(FLASH-CLOSE)),back=3.75-.45*u;
            Vec3d eye=focus.subtract(f.multiply(back)).add(r.multiply(-.18*Math.signum(aside))).add(0,-.52,0);
            return new Shot(eye,focus.add(0,.08,0),FOV_CLOSE,(float)(-3.5*Math.signum(aside==0?1:aside)));
        }
        if(sec<VOID){
            double u=unit((sec-FLASH)/(VOID-FLASH));
            return new Shot(hold.subtract(f.multiply(SIDE_GREY-.25*u)).add(0,.5,0),hold.add(0,1.32,0),FOV_SIDE,0);
        }
        double u=unit((sec-VOID)/(OUT-VOID)),far=SIDE_VOID+(SIDE_VOID_END-SIDE_VOID)*u;
        return new Shot(hold.subtract(f.multiply(far)).add(0,.15,0),hold.add(0,.85,0),FOV_SIDE,0);
    }
    /** The way the wide shots look (level): the way its user came, at the one marked's front. */
    public static Vec3d sideLook(Vec3d f,int side){return f;}
    /** The way across the wide shots' frame toward the side its user is on (level): the frame's right when they stand to the right of the way they came. */
    public static Vec3d sideRight(Vec3d f,int side){return right(f).multiply(side);}

    // ───────────────────────────── where the two are shown ─────────────────────────────

    /** How far beyond the one marked its user is shown in the side shots, walking away (the video's: at the frame's edge). */
    public static final double AWAY=5.8;
    /** Whether its user is shown beyond the one marked at {@code sec} (the side shots; they stand where they are otherwise). */
    public static boolean away(float sec){return sec>=FLASH&&sec<WORLD;}
    /** Where its user's feet are shown at {@code sec}: beside the one marked, or beyond them in the side shots. */
    public static Vec3d userFeet(Vec3d spot,Vec3d hold,Vec3d f,int side,float sec){
        return away(sec)?hold.add(sideRight(f,side).multiply(AWAY)).add(sideLook(f,side).multiply(.4)):spot;
    }

    /**
     * How the one marked is turned about their middle at {@code sec}: {leaning in the frame away from the side its user is
     * on (degrees: over as the flash goes through them, thrown one way and the other and over by the four blades), lifted
     * (blocks), bent forward (degrees)}.
     */
    public static float[] reel(float sec){
        if(sec<FLASH+FRAME)return new float[]{0,0,0};
        float in=smooth((sec-(FLASH+FRAME))/.06f);
        float lean=14*in+4*smooth((sec-GREY)/.7f),lift=0;
        for(int k=0;k<4;k++){
            float s=sec-SLASH_SEC[k];if(s<0)break;
            float e=1-(float)Math.exp(-s/.035f);
            lean+=REEL_TURN[k]*e;lift+=REEL_LIFT[k]*e;
        }
        return new float[]{lean,lift,22*in};
    }
    /** What each of the four cuts adds to the one marked's lean (degrees) and lift (blocks). */
    static final float[] REEL_TURN={-36,50,-76,-44},REEL_LIFT={.05f,.1f,.15f,.15f};

    // ───────────────────────────── the ring ─────────────────────────────

    /** The ring round the one marked in the grey world at {@code sec}: its radius (blocks; 0 before it shows). */
    public static double ring(float sec){
        if(sec<3.58f||sec>=RING)return 0;
        return 2.75-.85*unit((sec-3.58f)/(RING-3.58f));
    }
    /** The disc behind the one marked in the ring's two flat frames (blocks). */
    public static final double DISC_WHITE=1.62,DISC_BLACK=2.3;

    // ───────────────────────────── the four blades ─────────────────────────────

    /**
     * The four blades of the black ground, in the plane through the one marked that faces the side shots: each {across to
     * the right of frame, up} of its point (from the one marked's feet, blocks) and the way it points {across, up}. In the
     * order they cut: from the left level, from the upper right, from the upper left, from under them.
     */
    public static final double[][] BLADES={
        {-2.0,1.55,1,0},
        {2.7,3.1,-Math.cos(Math.toRadians(31.5)),-Math.sin(Math.toRadians(31.5))},
        {-2.8,2.55,Math.cos(Math.toRadians(26.7)),-Math.sin(Math.toRadians(26.7))},
        {.1,-.9,0,1}};
    /** How long each blade is and how wide at its root (blocks). */
    public static final double BLADE_LENGTH=11,BLADE_WIDTH=.24;
    /** A point of the blades' plane: {@code x} to the right of frame and {@code y} up from the one marked's feet. */
    public static Vec3d plane(Vec3d hold,Vec3d f,int side,double x,double y){return hold.add(sideRight(f,side).multiply(x)).add(0,y,0);}
    /** Whether blade {@code k} still waits at {@code sec} (until its cut). */
    public static boolean waits(int k,float sec){return sec>=RING&&sec<SLASH_SEC[k];}
    /** How wide blade {@code k}'s cut is at {@code sec} (blocks: a broad band as it goes through, then the thin line it leaves); 0 before it. */
    public static double cut(int k,float sec){
        float s=sec-SLASH_SEC[k];if(s<0)return 0;
        return .045+.85*Math.exp(-s/.04);
    }

    static float unit(double x){return (float)(x<0?0:x>1?1:x);}
    static float smooth(float x){x=x<0?0:x>1?1:x;return x*x*(3-2*x);}
    /** A steady hash in [0, 1) of {@code i} and {@code salt} (the same on every client). */
    public static double hash(int i,int salt){
        long h=i*0x9E3779B97F4A7C15L+salt*0xC2B2AE3D27D4EB4FL;
        h^=h>>>31;h*=0xBF58476D1CE4E5B9L;h^=h>>>27;h*=0x94D049BB133111EBL;h^=h>>>33;
        return (h>>>11)*0x1.0p-53;
    }
}
