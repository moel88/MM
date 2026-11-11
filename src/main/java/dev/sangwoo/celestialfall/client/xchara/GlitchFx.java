package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;

/**
 * What X Chara's knife's hits are drawn with besides their cuts (2.114.0, KnifeCombo; mine to make up: a hacker's
 * tricks): square pixels flying off a blow and going out one by one, the struck one's box flashed as lines round them
 * (a hitbox shown), bars torn sideways across them for a moment (a screen tearing), little 0s and 1s of pixels rising off
 * them (their data spilling), popup windows stamped in the air by the thrusts, a knife of light, and the finisher's X
 * (the mark that closes a window) that folds flat and goes out as an old screen does. Everything is a function of its own clock,
 * its number and a seed, the same on every client; near the camera (the struck one's own first person) it thins.
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

    // ───────────────────────────── the struck one's box ─────────────────────────────

    /** How long the struck one's box shows (seconds). */
    static final float BOX_LIFE=.2f;
    /** How strongly the box shows {@code s} seconds after the blow (blinking as it goes), and how far out of true it is (it snaps onto them). */
    static float boxAlpha(float s){
        if(s<0||s>=BOX_LIFE)return 0;
        float u=s/BOX_LIFE;
        return u<.55f?1:((int)Math.floor(s*60)%2==0?.9f:.25f)*(1-smooth((u-.75f)/.25f)*.6f);
    }
    static double boxGrow(float s){return 1+.22*(1-smooth(s/.05f));}
    /**
     * The struck one's box as lines round them (a hitbox shown): its twelve edges, {@code half} to a side and
     * {@code height} tall over {@code feet}, a little big at first and snapping onto them; in the blended pass white lines
     * outlined dark with a red copy and a cyan one a little aside (the colours split, more at first), in the light a glow.
     */
    static void box(EffectMesh m,Vec3d feet,double half,double height,float s,int seed,boolean light,Vec3d camera){
        float a=boxAlpha(s);if(a<=0)return;
        Vec3d mid=feet.add(0,height*.5,0);a*=near(mid,camera);if(a<=0)return;
        double g=boxGrow(s),hx=half*g+.06,hy=height*.5*g+.06;
        Vec3d[] c=new Vec3d[8];
        for(int i=0;i<8;i++)c[i]=mid.add((i&1)==0?-hx:hx,(i&2)==0?-hy:hy,(i&4)==0?-hx:hx);
        int[][] edges={{0,1},{2,3},{4,5},{6,7},{0,2},{1,3},{4,6},{5,7},{0,4},{1,5},{2,6},{3,7}};
        var f=facing(camera,mid);
        // the colours split: wide as it snaps on, a hair after, twitching
        double split=(.03+.13*(1-smooth(s/.06f)))*(h((int)Math.floor(s*30),0,seed,9)>.3?1:-1);
        Vec3d off=f[0].multiply(split);
        for(int[] e:edges){
            Vec3d p=c[e[0]],q=c[e[1]];
            if(light){m.line(p,q,.16,GLOW,.22f*a);m.line(p,q,.07,WHITE,.3f*a);}
            else{
                m.line(p.add(off),q.add(off),.035,RED,.85f*a);m.line(p.subtract(off),q.subtract(off),.035,CYAN,.85f*a);
                m.line(p,q,.075,INK,.85f*a);m.line(p,q,.04,WHITE,a);
            }
        }
        // its corners: a square pixel on each
        if(!light)for(Vec3d p:c){square(m,p,f,.065,INK,.9f*a);square(m,p,f,.045,WHITE,a);}
    }

    // ───────────────────────────── bars torn across them ─────────────────────────────

    /** How long the torn bars show (seconds), and how many. */
    static final float TEAR_LIFE=.11f;static final int TEARS=6;
    /** One torn bar: its middle, half its width and height, its colour. */
    record Tear(Vec3d at,double halfWide,double halfHigh,int rgb){}
    /**
     * Bar {@code j} torn across a body {@code half} to a side and {@code height} tall at {@code mid} (its middle),
     * {@code s} seconds after the blow (null once gone): at its own height, shoved aside along the camera's right, somewhere
     * else every 30th of a second.
     */
    static Tear tear(Vec3d mid,Vec3d right,double half,double height,int j,int seed,float s,double big){
        if(s<0||s>=TEAR_LIFE)return null;
        int step=(int)Math.floor(s*30);
        double y=(h(j,step,seed,11)-.5)*height*.9,x=(h(j,step,seed,12)-.5)*(half*2+1.1)*big;
        double wide=(.22+.6*h(j,step,seed,13))*big,high=.03+.04*h(j,step,seed,14);
        double c=h(j,step,seed,15);int rgb=c<.3?WHITE:c<.55?PURPLE:c<.72?INK:c<.86?RED:CYAN;
        return new Tear(mid.add(0,y,0).add(right.multiply(x)),wide,high,rgb);
    }
    static void tears(EffectMesh m,Vec3d mid,double half,double height,int seed,float s,double big,boolean light,Vec3d camera){
        if(s<0||s>=TEAR_LIFE)return;
        float near=near(mid,camera);if(near<=0)return;
        var f=facing(camera,mid);
        for(int j=0;j<TEARS;j++){
            var t=tear(mid,f[0],half,height,j,seed,s,big);if(t==null)continue;
            if(light){if(t.rgb()!=INK)bar(m,t.at(),f,t.halfWide()+.05,t.halfHigh()+.05,t.rgb()==WHITE?GLOW:t.rgb(),.35f*near);}
            else bar(m,t.at(),f,t.halfWide(),t.halfHigh(),t.rgb(),.95f*near);
        }
    }

    // ───────────────────────────── 0s and 1s ─────────────────────────────

    /** The digits' pixels, top row first (3 wide, 5 tall). */
    static final String[][] DIGITS={{"111","101","101","101","111"},{"010","110","010","010","111"}};
    /** How long a digit lasts (seconds), and a pixel of it (blocks). */
    static final float DIGIT_LIFE=.5f;static final double DIGIT_PIXEL=.05;
    /** Where digit {@code j} of a blow is {@code s} seconds after it, over {@code from} (null while it does not show): rising, drifting aside. */
    static Vec3d digitAt(Vec3d from,Vec3d right,double spreadWide,double spreadHigh,int j,int seed,float s){
        float born=(float)(.03+.1*h(j,0,seed,21)),age=s-born;
        if(age<0||age>=DIGIT_LIFE)return null;
        double x=(h(j,0,seed,22)-.5)*spreadWide*2,y=(h(j,0,seed,23)-.2)*spreadHigh;
        double rise=(.9+.7*h(j,0,seed,24))*(1-Math.exp(-3*age))/3*2.2;
        return from.add(right.multiply(x+(h(j,0,seed,25)-.5)*.5*age)).add(0,y+rise,0);
    }
    /** Draws a blow's digits (0s and 1s of white pixels in a purple glow) in one pass. */
    static void digits(EffectMesh m,Vec3d from,double spreadWide,double spreadHigh,int n,int seed,float s,boolean light,Vec3d camera){
        if(s<0||s>=DIGIT_LIFE+.15f)return;
        float near=near(from,camera);if(near<=0)return;
        var f=facing(camera,from);
        for(int j=0;j<n;j++){
            Vec3d at=digitAt(from,f[0],spreadWide,spreadHigh,j,seed,s);if(at==null)continue;
            float age=s-(float)(.03+.1*h(j,0,seed,21)),u=age/DIGIT_LIFE;
            // whole, flickering out over its last third
            float a=(u<.66f?1:((int)Math.floor(age*40+j)%2==0?.9f:.15f))*near;
            String[] rows=DIGITS[h(j,0,seed,26)<.5?0:1];double px=DIGIT_PIXEL*(.8+.5*h(j,0,seed,27));
            int rgb=h(j,0,seed,28)<.7?WHITE:LILAC;
            if(light){bar(m,at,f,px*2.6,px*3.6,GLOW,.16f*a);continue;}
            for(int r=0;r<5;r++)for(int c=0;c<3;c++){
                if(rows[r].charAt(c)!='1')continue;
                Vec3d p=at.add(f[0].multiply((c-1)*px)).add(f[1].multiply((2-r)*px));
                square(m,p,f,px*.5+.008,INK,.8f*a);square(m,p,f,px*.5,rgb,a);
            }
        }
    }

    // ───────────────────────────── popup windows ─────────────────────────────

    /** A popup snaps open over this long and folds shut over this long (seconds). */
    static final float WINDOW_OPEN=.07f,WINDOW_SHUT=.06f;
    /** How big a popup is {@code s} seconds after it popped (0 before): it overshoots and settles at 1. */
    static double windowGrow(float s){
        if(s<=0)return 0;
        double u=Math.min(1,s/WINDOW_OPEN)-1,back=2.4;
        return 1+(back+1)*u*u*u+back*u*u;
    }
    /** How tall a popup is {@code shut} seconds into its closing (1 open; it folds flat to a line, then is gone: 0). */
    static double windowTall(float shut){
        if(shut<=0)return 1;
        if(shut>=WINDOW_SHUT)return 0;
        return Math.max(.04,1-smooth(shut/(WINDOW_SHUT*.75f)));
    }
    /**
     * A popup window stamped in the air (X Chara's third hit: a thrust that pops one open, three of them piling up as
     * error messages do): in the plane of {@code right} and up round {@code c} (it faces its user), {@code halfWide} by
     * {@code halfHigh}; {@code s} seconds after it popped, {@code shut} seconds into its closing (0 or less while open).
     * In the blended pass a faint dark pane (what is behind shows through), a title bar of {@code bar} with a close box
     * at its right end, three lines of text (the last still being typed) and a white border outlined dark, shoved aside
     * and its colours split for a moment as it pops; in the light a glow on its border and bar. Closing it folds flat
     * to a white line and is gone.
     */
    static void window(EffectMesh m,Vec3d c,Vec3d right,double halfWide,double halfHigh,float s,float shut,int seed,int bar,boolean light,Vec3d camera){
        double grow=windowGrow(s),tall=windowTall(shut);if(grow<=0||tall<=0)return;
        float near=near(c,camera);if(near<=0)return;
        // shoved aside for a moment as it pops (somewhere else every 30th of a second)
        double shove=s<WINDOW_OPEN?(h((int)Math.floor(s*30),0,seed,51)-.5)*.34*(1-s/WINDOW_OPEN):0;
        Vec3d o=c.add(right.multiply(shove));
        double hw=halfWide*grow,hh=halfHigh*grow*tall;
        Vec3d x=right.multiply(hw),y=UP.multiply(hh);
        Vec3d tl=o.subtract(x).add(y),tr=o.add(x).add(y),br=o.add(x).subtract(y),bl=o.subtract(x).subtract(y);
        boolean line=tall<.2;
        if(light){
            if(line){m.needle(o.subtract(x.multiply(1.1)),o.add(x.multiply(1.1)),.2,.5,WHITE,.9f*near);return;}
            m.line(tl,tr,.2,GLOW,.26f*near);m.line(tr,br,.2,GLOW,.26f*near);m.line(br,bl,.2,GLOW,.26f*near);m.line(bl,tl,.2,GLOW,.26f*near);
            double barHigh=Math.min(hh*2,.2*grow*tall);
            m.quad(tl,tr,tr.subtract(0,barHigh,0),tl.subtract(0,barHigh,0),bar,.3f*near);
            return;
        }
        if(line){m.line(o.subtract(x),o.add(x),.07,INK,.9f*near);m.line(o.subtract(x),o.add(x),.04,WHITE,near);return;}
        // its pane: faint, so whoever it is stamped on shows through
        m.quad(tl,tr,br,bl,INK,.3f*near);
        // its title bar and the close box at its right end
        double barHigh=.2*grow*tall,box=barHigh*.62,gap=barHigh*.19;
        m.quad(tl,tr,tr.subtract(0,barHigh,0),tl.subtract(0,barHigh,0),bar,.92f*near);
        Vec3d b0=tr.subtract(right.multiply(gap+box)).subtract(0,gap,0);
        m.quad(b0,b0.add(right.multiply(box)),b0.add(right.multiply(box)).subtract(0,box,0),b0.subtract(0,box,0),WHITE,.95f*near);
        m.line(b0.add(right.multiply(box*.2)).subtract(0,box*.2,0),b0.add(right.multiply(box*.8)).subtract(0,box*.8,0),.022,INK,near);
        m.line(b0.add(right.multiply(box*.8)).subtract(0,box*.2,0),b0.add(right.multiply(box*.2)).subtract(0,box*.8,0),.022,INK,near);
        // three lines of text, the last still being typed
        for(int j=0;j<3;j++){
            double full=hw*2*(.38+.42*h(j,0,seed,52)),wide=j<2?full:full*Math.min(1,s/.16),high=.05*grow*tall;
            Vec3d at=tl.add(right.multiply(.13*grow)).subtract(0,barHigh+(.14+.19*j)*grow*tall,0);
            if(at.y-high<bl.y)break;
            m.quad(at,at.add(right.multiply(wide)),at.add(right.multiply(wide)).subtract(0,high,0),at.subtract(0,high,0),j==0?WHITE:LILAC,(j==0?.85f:.6f)*near);
        }
        // its border: white outlined dark, its colours split for a moment as it pops
        if(s<.1f){
            Vec3d off=right.multiply(.09*(1-s/.1f)*(h((int)Math.floor(s*30),0,seed,53)>.5?1:-1));
            for(Vec3d[] e:new Vec3d[][]{{tl,tr},{tr,br},{br,bl},{bl,tl}}){m.line(e[0].add(off),e[1].add(off),.04,RED,.8f*near);m.line(e[0].subtract(off),e[1].subtract(off),.04,CYAN,.8f*near);}
        }
        for(Vec3d[] e:new Vec3d[][]{{tl,tr},{tr,br},{br,bl},{bl,tl}})m.line(e[0],e[1],.085,INK,.9f*near);
        for(Vec3d[] e:new Vec3d[][]{{tl,tr},{tr,br},{br,bl},{bl,tl}})m.line(e[0],e[1],.045,WHITE,near);
    }

    // ───────────────────────────── a square frame ─────────────────────────────

    /** A square frame of lines, {@code half} to a side, in the plane of {@code u} and {@code v} (units) round {@code c}, turned {@code turn} radians. */
    static void frame(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double half,double turn,double width,int rgb,float alpha){
        if(alpha<=0||half<=0)return;
        Vec3d a=u.multiply(Math.cos(turn)).add(v.multiply(Math.sin(turn))),b=v.multiply(Math.cos(turn)).subtract(u.multiply(Math.sin(turn)));
        Vec3d p0=c.add(a.multiply(half)).add(b.multiply(half)),p1=c.subtract(a.multiply(half)).add(b.multiply(half)),
            p2=c.subtract(a.multiply(half)).subtract(b.multiply(half)),p3=c.add(a.multiply(half)).subtract(b.multiply(half));
        m.line(p0,p1,width,rgb,alpha);m.line(p1,p2,width,rgb,alpha);m.line(p2,p3,width,rgb,alpha);m.line(p3,p0,width,rgb,alpha);
    }

    // ───────────────────────────── a knife of light ─────────────────────────────

    /**
     * A knife's shape of light, its middle at {@code at}, its point along {@code aim}, its flat turned to the camera as far
     * as its way lets it (its spine up): X Chara's knife as the model has it (SwordDance.knife's outline), {@code length}
     * from its rod's end to its point.
     */
    static void knife(EffectMesh m,Vec3d at,Vec3d aim,double length,Vec3d camera,int rgb,float alpha){
        if(alpha<=0)return;
        Vec3d n=camera.subtract(at),flat=n.subtract(aim.multiply(n.dotProduct(aim)));
        Vec3d up=flat.lengthSquared()<1e-6?UP:aim.crossProduct(flat.normalize());
        if(up.dotProduct(UP)<0)up=up.negate();
        if(up.lengthSquared()<1e-8)up=UP;up=up.normalize();
        double blade=length*SwordDance.BLADE,rod=length-blade,depth=blade/SwordDance.ASPECT;
        Vec3d o=at.subtract(aim.multiply((blade-rod)*.5)).add(up.multiply(depth*.5));
        int steps=12;
        for(int i=0;i<steps;i++){
            double x0=blade*i/steps,x1=blade*(i+1)/steps;
            Vec3d s0=o.add(aim.multiply(x0)),s1=o.add(aim.multiply(x1));
            m.quad(s0,s1,s1.subtract(up.multiply(depth*SwordDance.depth(x1/blade))),s0.subtract(up.multiply(depth*SwordDance.depth(x0/blade))),rgb,alpha);
        }
        double y=depth*.1,hh=Math.max(depth*.05,.025);
        Vec3d r0=o.subtract(aim.multiply(rod)).subtract(up.multiply(y)),r1=o.add(aim.multiply(.02)).subtract(up.multiply(y));
        m.quad(r0.add(up.multiply(hh)),r1.add(up.multiply(hh)),r1.subtract(up.multiply(hh)),r0.subtract(up.multiply(hh)),rgb,alpha);
    }

    // ───────────────────────────── the X ─────────────────────────────

    /** Half a stroke of the X (blocks), its width, and how long one takes to be drawn. */
    static final double X_HALF=1.9,X_WIDE=.34;static final float X_DRAW=.035f;
    /** The X folds flat over this long and its line goes out over this long more (seconds). */
    static final float X_FOLD=.06f,X_OUT=.06f;
    /**
     * How tall and how wide the X is {@code s} seconds into its going out (1 whole): it folds flat to a line, as an old
     * screen switched off, then the line draws in to a point and is gone ({wide, tall}; both 0 once gone).
     */
    static double[] closing(float s){
        if(s<=0)return new double[]{1,1};
        if(s<X_FOLD){double u=smooth(s/X_FOLD);return new double[]{1+.12*u,1-.97*u};}
        float o=s-X_FOLD;if(o>=X_OUT)return new double[]{0,0};
        double u=smooth(o/X_OUT);return new double[]{1.12*(1-u),.03};
    }
    /**
     * One stroke of the X in the plane of {@code right} and up round {@code c}: from its high end ({@code side} +1 the high
     * right, -1 the high left) down across to the other low end, {@code drawn} of it drawn (0 to 1, from its high end),
     * squeezed {@code wide} across and {@code tall} up (its going out). In the blended pass a red blade outlined dark with
     * a cyan copy a little aside, in the light a white core and a glow.
     */
    static void stroke(EffectMesh m,Vec3d c,Vec3d right,int side,float drawn,double wide,double tall,double big,float alpha,boolean light,int seed,float s){
        if(alpha<=0||drawn<=0||wide<=0)return;
        double half=X_HALF*big,k=Math.sqrt(.5);
        Vec3d hi=c.add(right.multiply(side*half*k*wide)).add(0,half*k*tall,0),lo=c.add(right.multiply(-side*half*k*wide)).add(0,-half*k*tall,0);
        Vec3d end=hi.add(lo.subtract(hi).multiply(drawn));
        double width=X_WIDE*big*Math.max(.35,Math.min(1,tall*3));
        if(light){
            m.needle(hi,end,width*2.6,.5,RED,.3f*alpha);m.needle(hi,end,width*.3,.5,WHITE,.9f*alpha);
            return;
        }
        // its colours split: a cyan copy a little aside, twitching
        double split=.07*(h((int)Math.floor(s*30),0,seed,31)>.4?1:-1);
        Vec3d off=right.multiply(split);
        m.needle(hi.add(off),end.add(off),width,.5,CYAN,.75f*alpha);
        m.needle(hi,end,width+.13,.5,INK,.95f*alpha);
        m.needle(hi,end,width,.5,RED,alpha);
    }
}
