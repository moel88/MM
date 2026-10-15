package dev.sangwoo.celestialfall.client;

/** H reference shot boundaries: wide launch, inverted close-up, sky, landing, hand insert, final. */
public final class KagenoCameraPath {
    private KagenoCameraPath(){}
    // Source hard cuts at 121.766667, 122.633333 and 123.800000 s (30 fps).
    public static final float AIR_CUT=14.733333f, SKY_CUT=32.066667f, LAND_CUT=55.4f;
    public record Shot(double right,double height,double forward,double focusHeight,double fov,double roll,double focusRight){}
    private record Key(float tick,Shot shot){}
    private static Key k(float t,double x,double y,double z,double aim,double fov,double roll,double focusX){return new Key(t,new Shot(x,y,z,aim,fov,roll,focusX));}
    // All times share the source's 121.03 s onset. The lens moves during the
    // landing and hand insert; the last shot is a locked side view, not an orbit.
    private static final Key[] AIR={
        k(AIR_CUT,-.22,28.72,1.82,29.02,58,-9,0),
        k(19,-.70,28.78,2.45,28.90,60,-6,0),
        k(SKY_CUT-.001f,-.70,28.78,2.45,28.90,60,-6,0)};
    private static final Key[] LAND={
        k(LAND_CUT,-1.35,1.35,4.65,.85,58,0,0),
        k(59,-.80,1.18,2.70,.88,58,0,0),
        k(63,-.50,1.30,1.65,1.20,62,0,0),
        k(66,-.50,1.30,1.65,1.20,62,0,0),
        k(69,-.65,1.34,2.55,1.05,58,0,0),
        k(72,-.92,1.43,1.12,1.49,62,0,0),
        k(74,-1.05,1.40,1.20,1.49,62,0,0),
        k(78,-2.55,1.35,1.85,1.18,54,0,-.18),
        k(106,-2.55,1.35,1.85,1.18,54,0,-.18),
        k(116,-2.55,1.35,1.85,1.18,54,0,-.18)
    };
    public static boolean crossesCut(float a,float b){for(float t:new float[]{AIR_CUT,SKY_CUT,LAND_CUT})if(a<t&&b>=t)return true;return false;}
    public static double ease(double t){t=Math.max(0,Math.min(1,t));return t*t*t*(t*(t*6-15)+10);}
    public static Shot sample(float age){
        if(age<AIR_CUT)return new Shot(0,1.25,9.5,1+KagenoMotion.lift(age),68,0,0);
        if(age<SKY_CUT)return track(age,AIR);
        if(age<LAND_CUT)return new Shot(.25,1.6,18,70,66,0,0);
        return track(age,LAND);
    }
    private static Shot track(float age,Key[] keys){
        for(int i=1;i<keys.length;i++)if(age<keys[i].tick){var a=keys[i-1];var b=keys[i];double t=ease((age-a.tick)/(b.tick-a.tick));return mix(a.shot,b.shot,t);}
        return keys[keys.length-1].shot;
    }
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
    private static Shot mix(Shot a,Shot b,double t){return new Shot(lerp(a.right,b.right,t),lerp(a.height,b.height,t),lerp(a.forward,b.forward,t),lerp(a.focusHeight,b.focusHeight,t),lerp(a.fov,b.fov,t),lerp(a.roll,b.roll,t),lerp(a.focusRight,b.focusRight,t));}
    public static float blend(float age){return (float)(ease(age/2.0)*(1-ease((age-106)/10.0)));}
}
