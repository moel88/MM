package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;

/**
 * Square pixels of X Chara's purple: flying off and going out one by one (their hack coming on and going off, HackFx).
 * Until 2.118.0 this also drew what their knife's hits left with the hack on (2.114.0, a hacker's tricks: a hitbox
 * flashed, bars torn across the struck, 0s and 1s, popup windows, a knife of light, a red X), all thrown out with those
 * hits (the swings draw a purple cut now, SlashFx). Everything is a function of its own clock, its number and a seed,
 * the same on every client; near the camera it thins.
 */
final class GlitchFx {
    private GlitchFx(){}
    /** The black of its dead pixels and outlines; its deep and vivid purple; its glow; lilac; white; X Chara's red; the cyan its colours split to. */
    static final int INK=0x0a0418,DEEP=0x6a0cb8,PURPLE=0x9416e0,GLOW=0x8a14e8,LILAC=0xe2b8ff,WHITE=0xffffff,RED=0xff2a3c,CYAN=0x33eaff,PINK=0xff40d6;
    static final Vec3d UP=new Vec3d(0,1,0);

    /** A number from 0 to 1 for the n-th thing's j-th part of a seed. */
    static double h(int n,int j,int seed,int salt){
        double x=Math.sin(n*127.1+j*311.7+seed*74.7+salt*269.5)*43758.5453;return x-Math.floor(x);
    }
    static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    /** How much of something at {@code p} shows to a camera at {@code camera}: all of it from 2.2 blocks off, none within 0.9. */
    static float near(Vec3d p,Vec3d camera){return smooth((float)((camera.distanceTo(p)-.9)/1.3));}
    /** The camera's right and up at {@code p} (and the way to it), for what is drawn facing it. */
    static Vec3d[] facing(Vec3d camera,Vec3d p){
        Vec3d n=camera.subtract(p);if(n.lengthSquared()<1e-8)n=new Vec3d(0,0,1);n=n.normalize();
        Vec3d r=UP.crossProduct(n);if(r.lengthSquared()<1e-6)r=new Vec3d(1,0,0);r=r.normalize();
        return new Vec3d[]{r,n.crossProduct(r).normalize(),n};
    }
    /** A square facing the camera, {@code half} blocks from its middle to a side. */
    static void square(EffectMesh m,Vec3d p,Vec3d[] f,double half,int rgb,float alpha){
        if(alpha<=0||half<=0)return;
        Vec3d a=f[0].multiply(half),b=f[1].multiply(half);
        m.quad(p.subtract(a).subtract(b),p.add(a).subtract(b),p.add(a).add(b),p.subtract(a).add(b),rgb,alpha);
    }
    /** A bar facing the camera: {@code halfWide} along the camera's right, {@code halfHigh} along its up. */
    static void bar(EffectMesh m,Vec3d p,Vec3d[] f,double halfWide,double halfHigh,int rgb,float alpha){
        if(alpha<=0)return;
        Vec3d a=f[0].multiply(halfWide),b=f[1].multiply(halfHigh);
        m.quad(p.subtract(a).subtract(b),p.add(a).subtract(b),p.add(a).add(b),p.subtract(a).add(b),rgb,alpha);
    }

    // ───────────────────────────── pixels ─────────────────────────────

    /** One pixel thrown off a blow: where it is, how big, its colour and how strongly it shows (it stays whole, then blinks out). */
    record Pixel(Vec3d at,double half,int rgb,float alpha){}
    /** How long a blow's pixels last at most (seconds). */
    static final float PIXEL_LIFE=.55f;
    /**
     * Pixel {@code j} of a burst from {@code from}, {@code s} seconds after it (null while it does not show): thrown out
     * along {@code way} (unit; spread round it by {@code spread}, 0 a jet to 1 all round), {@code speed} blocks a second
     * at its fastest, slowing, falling a little. It keeps its size and brightness, flickers near its end, and is gone.
     */
    static Pixel pixel(Vec3d from,Vec3d way,double spread,double speed,double size,int j,int seed,float s){
        float born=(float)(.04*h(j,0,seed,1)),age=s-born,life=(float)(.22+.33*h(j,0,seed,2));
        if(age<0||age>=life)return null;
        Vec3d any=new Vec3d(h(j,0,seed,3)-.5,h(j,0,seed,4)-.35,h(j,0,seed,5)-.5).normalize();
        Vec3d dir=way.multiply(1-spread).add(any.multiply(.35+spread)).normalize();
        double v=speed*(.35+.65*h(j,0,seed,6)),drag=5.5,travel=v*(1-Math.exp(-drag*age))/drag;
        Vec3d at=from.add(dir.multiply(travel)).add(0,-1.6*age*age,0);
        double half=size*(.5+.7*h(j,0,seed,7));
        // whole, then a flicker over its last fifth
        float u=age/life,alpha=u<.8f?1:((int)Math.floor(age*60+j)%2==0?1:0);
        return new Pixel(at,half,colour(j,seed),alpha);
    }
    /** A pixel's colour: purple mostly, some white, some black, a few red and cyan. */
    static int colour(int j,int seed){
        double c=h(j,0,seed,8);
        return c<.34?PURPLE:c<.52?DEEP:c<.7?WHITE:c<.84?INK:c<.93?RED:CYAN;
    }
    /** Draws a burst's pixels in one pass: their bodies (the blended pass), or a little glow of each one's colour but the black ones (the light). */
    static void pixels(EffectMesh m,Vec3d from,Vec3d way,double spread,double speed,double size,int n,int seed,float s,boolean light,Vec3d camera){
        if(s<0||s>=PIXEL_LIFE+.05f)return;
        for(int j=0;j<n;j++){
            var p=pixel(from,way,spread,speed,size,j,seed,s);if(p==null||p.alpha()<=0)continue;
            float near=near(p.at(),camera);if(near<=0)continue;
            var f=facing(camera,p.at());
            if(light){if(p.rgb()!=INK)square(m,p.at(),f,p.half()*1.9,p.rgb()==WHITE?GLOW:p.rgb(),.3f*near);}
            else{square(m,p.at(),f,p.half()+.012,INK,.9f*near);square(m,p.at(),f,p.half(),p.rgb(),near);}
        }
    }

}
