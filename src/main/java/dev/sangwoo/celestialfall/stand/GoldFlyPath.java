package dev.sangwoo.celestialfall.stand;

import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;

/**
 * Where Gold Experience's fly is (2.151.0, T with the hand empty: GoldExperienceRules has the user's words): pure, for
 * the server's body of it (GoldFlyEntity, which whoever it belongs to swats) and every client's drawing of it alike.
 * <p>
 * The part the punch strikes off flies out of its place on the body ({@link #socket}) in an arc and turns into the fly
 * ({@link GoldExperienceRules#FLY_FORMS} ticks); the fly then hovers in front of whom it belongs to, as the user asked
 * ("앞으로 알짱거리면서 파리 자기자신을 때리게 유도"): a little more than an arm's length ahead of them at the height of
 * their face, swinging from side to side and bobbing, never still, so their own blows meet it -- most of the time, not
 * all: at the ends of its swings it is out of the way of a blow straight ahead, and it lags their turns
 * ({@link #FOLLOW}), so one quick enough can turn and strike past it. Its time up untouched it flies back into its
 * place ({@link GoldExperienceRules#FLY_RETURN} ticks). Every moment of it is a smooth function of its age.
 * <p>
 * Ways about a body: {forward, right, up}, the way it faces (Minecraft's yaw: 0 faces south, +z; the right hand is then
 * west, -x).
 */
public final class GoldFlyPath {
    private GoldFlyPath(){}
    /** How long the fly takes to swing round to where whom it belongs to now faces (seconds): it lags their turns. */
    public static final double FOLLOW=.3;
    /** How far ahead of them it hovers (blocks) and how far nearer and further it drifts; how far to either side it swings; how far below their eyes it hovers and how far up and down it bobs. */
    public static final double AHEAD=1.25,AHEAD_SWING=.38,SIDE=1.45,BELOW_EYES=.15,BOB=.3;
    /** How high the part arcs flying off, and flying back (blocks). */
    public static final double ARC_OFF=.55,ARC_BACK=.3;

    /** The heading after one tick following {@code target} (degrees). */
    public static float follow(float heading,float target){
        double d=wrap(target-heading);
        return (float)(heading+d*(1-Math.exp(-.05/FOLLOW)));
    }
    static double wrap(double d){d%=360;if(d>=180)d-=360;if(d<-180)d+=360;return d;}
    /** A phase from the fly's seed (0 to 2 pi). */
    static double phase(int seed,int k){
        long h=seed*0x9E3779B97F4A7C15L+k*0xBF58476D1CE4E5B9L;
        h^=h>>>31;h*=0x94D049BB133111EBL;h^=h>>>29;
        return (h>>>11)*0x1.0p-53*Math.PI*2;
    }
    /**
     * Where it hovers {@code age} seconds after it took wing: {forward, right, up from the eyes} about whom it belongs
     * to. Slow swings side to side (2.3 s) and in and out (1.7 s), a bob (1.31 s), each with a quicker flutter over it,
     * and a buzzing tremble.
     */
    public static double[] hover(int seed,double age){
        double t=age*Math.PI*2;
        double side=SIDE*Math.sin(t/2.3+phase(seed,1))+.22*Math.sin(t/.83+phase(seed,2));
        double ahead=AHEAD+AHEAD_SWING*Math.sin(t/1.7+phase(seed,3))+.1*Math.sin(t/.61+phase(seed,4));
        double up=-BELOW_EYES+BOB*Math.sin(t/1.31+phase(seed,5))+.08*Math.sin(t/.47+phase(seed,6));
        // the buzz
        side+=.03*Math.sin(t*7.7+phase(seed,7));ahead+=.025*Math.sin(t*9.1+phase(seed,8));up+=.03*Math.sin(t*8.3+phase(seed,9));
        return new double[]{ahead,side,up};
    }
    /** Where part {@code part} is on a body {@code height} tall (its middle): {forward, right, up from its feet}. */
    public static double[] socket(int part,double height){
        double k=height/1.8;
        return switch(part){
            case HEAD->new double[]{0,0,1.74*k};
            case RIGHT_ARM->new double[]{0,.37*k,1.12*k};
            case LEFT_ARM->new double[]{0,-.37*k,1.12*k};
            case RIGHT_LEG->new double[]{0,.12*k,.38*k};
            default->new double[]{0,-.12*k,.38*k};
        };
    }
    /** {forward, right, up} about feet at {@code x,y,z} facing {@code yaw}, as a point of the world. */
    public static double[] world(double x,double y,double z,float yaw,double[] way){
        double r=Math.toRadians(yaw),fx=-Math.sin(r),fz=Math.cos(r);
        return new double[]{x+fx*way[0]-fz*way[1],y+way[2],z+fz*way[0]+fx*way[1]};
    }
    static double smooth(double v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** A point {@code u} of the way from {@code a} to {@code b} (eased), raised {@code arc} at the middle. */
    static double[] arc(double[] a,double[] b,double u,double arc){
        double e=smooth(u),lift=arc*Math.sin(Math.PI*Math.max(0,Math.min(1,u)));
        return new double[]{a[0]+(b[0]-a[0])*e,a[1]+(b[1]-a[1])*e+lift,a[2]+(b[2]-a[2])*e};
    }
    /** How far a client's clock for the fly eases toward the server's at each tick, and past what it is put right at once (ticks). */
    public static final double AGE_EASE=.1,AGE_SNAP=40;
    /**
     * A client's age of the fly after a tick: a tick on from {@code last}, eased toward {@code server} (the age by the
     * world's time, which a client's server sets again every second). A client that lost ticks catches up smoothly
     * (2.151.0); one off by more than 2 s is put right at once.
     */
    public static double age(double last,double server){
        double next=last+1,off=server-next;
        return Math.abs(off)>AGE_SNAP?server:next+off*AGE_EASE;
    }
    /**
     * Where the fly is (the middle of it) {@code age} ticks after the blow, about whom it belongs to standing at
     * {@code x,y,z} with their eyes {@code eyes} up and {@code height} tall, followed at {@code heading}: the part flying
     * off from {@code from} (the world point it was struck off at), the fly hovering, or the fly going back into the
     * part's place. A smooth function of the age.
     */
    public static double[] at(int seed,int part,double age,double x,double y,double z,float heading,double eyes,double height,double[] from){
        if(age<FLY_FORMS){
            double[] to=world(x,y,z,heading,lifted(hover(seed,0),eyes));
            return arc(from,to,age/FLY_FORMS,ARC_OFF);
        }
        double flying=age-FLY_FORMS;
        if(flying<FLY_LASTS)return world(x,y,z,heading,lifted(hover(seed,flying/20),eyes));
        double[] a=world(x,y,z,heading,lifted(hover(seed,FLY_LASTS/20.0),eyes)),b=world(x,y,z,heading,socket(part,height));
        return arc(a,b,(flying-FLY_LASTS)/FLY_RETURN,ARC_BACK);
    }
    /** A hover's place with its height made from the feet. */
    static double[] lifted(double[] hover,double eyes){return new double[]{hover[0],hover[1],hover[2]+eyes};}
}
