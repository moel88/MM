package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import net.minecraft.util.math.Vec3d;

/**
 * One blow of X Chara's sword dance on the one marked, in the world: light bursting out of their body (2.112.4, user
 * request: "그냥 꽃 모양은 포기하고 너가 창작해줄 수 없음? 수 많은 검이 박혀서 몸에서 이펙트가 터지는거", then "검이
 * 날아오면 안돼" and "꽂혀도 안돼 그냥 이펙트만 터지게": the video's flowers of 2.112.1~2.112.3 given up, and no sword is
 * drawn, neither flying in nor stuck in them; only what bursts out of them). Each blow is an unseen blade through their body:
 * from somewhere all round them (each blow its own way, spread evenly round), through a point inside them, out the other
 * side, most of them coming out sideways and up. Where it goes in, a white flash and star and a smaller burst of spikes
 * back out; where it comes out, light erupts from their body along its way: a cone of glowing white and violet spikes some
 * one to two and a half blocks long with black thorns among them, a white flash and star, a purple glow, a ring round its
 * way, white sparks and black drops flying out and falling. The colours are 2.112.3's (the user's: glowing purple,
 * glowing white and black), the black drawn over all the light ({@link #black}) so its glow does not purple it.
 * <p>
 * The last blow's eruption is bigger, and as they are sent flying everything bursts out of their whole body at once
 * ({@link #finale}). Every part of it is a function of the blow's own clock ({@code s}, ticks since it landed), its number
 * and its cast's seed, the same on every client; near the camera (their own first person, in their head) it thins.
 */
final class DanceBurst {
    private DanceBurst(){}
    /** The black of its thorns and drops. */
    static final int INK=0x0a0418;
    /** Its glowing purple (2.112.3's, the user's): the glow, and the violet of its spikes; the hot light violet of its sparks; its white. */
    static final int GLOW=0xa81cff,VIOLET=0xc058ff,HOT=0xecc4ff,WHITE=0xffffff;
    /** How long a blow's light lasts (ticks), its spikes, its ring, its sparks and drops; the longest any part of it shows. */
    static final float FLASH=3,SPIKE_LIFE=5,RING_LIFE=6,SPARK_LIFE=6,DROP_LIFE=9,LIFE=10;
    /** How long the burst out of the whole body lasts at the end. */
    static final float FINALE_LIFE=14;
    /** How many spikes each eruption has (of them so many black thorns), sparks and drops; how many spikes burst back out where it went in. */
    static final int SPIKES=11,THORNS=3,SPARKS=10,DROPS=6,BACK_SPIKES=5;
    /** The burst back out where it went in, of the one where it comes out. */
    static final double BACK=.6;
    /** The last blow's eruption, so much bigger. */
    static final double LAST=1.4;
    /** How far the spikes spread from their way (the cone's spread). */
    static final double SPREAD=.65;
    /**
     * The unseen blades' ways, up and down (degrees above the level they come from): within these, so that most burst out
     * of them sideways and up, where they show round the one who cut them (in front of them from their own camera; the
     * video's flowers showed round them for their size), few into the ground.
     */
    static final double LOW=-35,HIGH=12;

    // ───────────────────────────── where each blow goes through ─────────────────────────────

    /**
     * Where blow {@code k} goes through the one marked: the way its unseen blade goes ({@code way}, unit), where it goes in
     * and where it comes out on their body's skin ({@code in}, {@code out}) and the point inside them it passes, all from
     * their feet; their body a box {@code width} wide, {@code height} tall.
     */
    record Pierce(Vec3d way,Vec3d in,Vec3d out,Vec3d inside){}
    static Pierce pierce(int k,int seed,double width,double height){
        // all round them, evenly (the golden angle's share of a turn on from the last), each a little off
        double az=2*Math.PI*(k*.381966+.1*h(k,0,seed,1)),el=Math.toRadians(LOW+(HIGH-LOW)*h(k,0,seed,2));
        // it comes from that way (above them for more of them) and goes on through
        Vec3d from=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
        Vec3d way=from.negate();
        // through their middle mostly, their head at times
        double half=width/2;
        double y=h(k,0,seed,6)<.12?height*(.82+.1*h(k,0,seed,5)):height*(.3+.55*h(k,0,seed,5));
        Vec3d inside=new Vec3d((h(k,0,seed,3)-.5)*.4*width,y,(h(k,0,seed,4)-.5)*.4*width);
        return new Pierce(way,inside.add(way.multiply(-leave(inside,from,half,height))),inside.add(way.multiply(leave(inside,way,half,height))),inside);
    }
    /** How far from {@code p} (inside the box {@code half} either way, 0 to {@code height} up) along {@code d} (unit) it leaves the box. */
    static double leave(Vec3d p,Vec3d d,double half,double height){
        double t=Double.MAX_VALUE;
        if(d.x>1e-9)t=Math.min(t,(half-p.x)/d.x);else if(d.x<-1e-9)t=Math.min(t,(-half-p.x)/d.x);
        if(d.y>1e-9)t=Math.min(t,(height-p.y)/d.y);else if(d.y<-1e-9)t=Math.min(t,(0-p.y)/d.y);
        if(d.z>1e-9)t=Math.min(t,(half-p.z)/d.z);else if(d.z<-1e-9)t=Math.min(t,(-half-p.z)/d.z);
        return t;
    }

    // ───────────────────────────── the eruption ─────────────────────────────

    /** One spike of an eruption at a moment: from {@code a} (on their skin) out to {@code b}, {@code width} at its widest, how strongly it shows, and its colour (white, violet or a black thorn). */
    record Spike(Vec3d a,Vec3d b,double width,float fade,int rgb){}
    /** Spike {@code j} of blow {@code k}'s eruption from {@code out} along {@code way}, {@code s} ticks after it landed (null while it does not show): shooting out at once, then gone. */
    static Spike spike(Vec3d out,Vec3d way,int k,int j,int seed,float s,double big){
        if(s<0||s>=SPIKE_LIFE)return null;
        Vec3d dir=way.add(unit(k,j,seed,10).multiply(SPREAD*(.35+.65*h(k,j,seed,13)))).normalize();
        double grow=1-sq(1-Math.min(1,s/1.2)),length=(1.1+1.5*h(k,j,seed,14))*big*grow;
        float fade=in(s)*(1-smooth((s-1)/(SPIKE_LIFE-1)));
        if(fade<=0||length<=1e-4)return null;
        boolean thorn=j<THORNS;
        int rgb=thorn?INK:j%3==0?VIOLET:WHITE;
        double width=(thorn?.17:.1+.07*h(k,j,seed,15))*big;
        return new Spike(out.add(dir.multiply(.04)),out.add(dir.multiply(.04+length*(thorn?.85:1))),width,fade,rgb);
    }
    /** Something flying out of an eruption: where it is {@code s} ticks after it landed, the way it is going (per tick), its size and how strongly it shows. */
    record Bit(Vec3d at,Vec3d v,double size,float fade){}
    /** Spark ({@code drop} false) or black drop {@code j} of blow {@code k}'s eruption: out along its way, spreading, falling (null while it does not show). */
    static Bit bit(Vec3d out,Vec3d way,int k,int j,int seed,float s,double big,boolean drop){
        float life=drop?DROP_LIFE:SPARK_LIFE;
        float sb=s-(float)(.4*h(k,j,seed,drop?30:20));if(sb<0||sb>=life)return null;
        Vec3d v0=way.multiply((drop?.15:.26)*(.7+.6*h(k,j,seed,drop?31:21))*big).add(unit(k,j,seed,drop?32:22).multiply(drop?.11:.15));
        double g=drop?.024:.012;
        Vec3d at=out.add(v0.multiply(sb)).add(0,-.5*g*sb*sb,0),v=v0.add(0,-g*sb,0);
        return new Bit(at,v,(drop?.05+.04*h(k,j,seed,33):.04+.03*h(k,j,seed,23))*big,in(sb)*(1-sb/life));
    }

    /** Draws blow {@code k}'s eruption {@code s} ticks after it landed, from their {@code feet}: its light (not the black: {@link #black}). */
    static void light(EffectMesh m,Vec3d feet,Pierce p,int k,int seed,float s,boolean last,Vec3d camera){
        if(s<0||s>=LIFE)return;
        double big=last?LAST:1;
        Vec3d out=feet.add(p.out()),in=feet.add(p.in()),way=p.way();
        float nearOut=near(out,camera),nearIn=near(in,camera);
        // where it goes in: a white flash and star, a smaller burst of spikes back out
        if(nearIn>0){
            if(s<2)m.softDisc(in,.32*big,WHITE,.85f*(1-s/2)*nearIn);
            if(s<2)m.star(in,.7*big,WHITE,.75f*(1-s/2)*nearIn);
            if(s<4)m.softDisc(in,.7*big,GLOW,.24f*(1-s/4)*nearIn);
            for(int j=1;j<BACK_SPIKES;j++){
                var sp=spike(in,way.negate(),k,j+16,seed,s,big*BACK);if(sp==null)continue;
                m.needle(sp.a(),sp.b(),sp.width()*2.6,.25,GLOW,.3f*sp.fade()*nearIn);
                m.needle(sp.a(),sp.b(),sp.width(),.25,j%2==0?VIOLET:WHITE,.9f*sp.fade()*nearIn);
            }
        }
        if(nearOut<=0)return;
        // where it comes out: a white flash and star, a purple glow
        if(s<FLASH)m.softDisc(out,(.45+.35*s/FLASH)*big,WHITE,.95f*(1-s/FLASH)*nearOut);
        if(s<2.5f)m.star(out,1.2*big,WHITE,.85f*(1-s/2.5f)*nearOut);
        if(s<6)m.softDisc(out.add(way.multiply(.35*big)),1.15*big,GLOW,.34f*(1-s/6)*nearOut);
        // a ring round its way, running out along it
        if(s<RING_LIFE){
            Vec3d u=way.crossProduct(Math.abs(way.y)>.9?new Vec3d(1,0,0):new Vec3d(0,1,0)).normalize(),v=way.crossProduct(u).normalize();
            double g=1-sq(1-Math.min(1,s/4));float f=(1-s/RING_LIFE)*nearOut;
            Vec3d at=out.add(way.multiply((.15+.7*g)*big));
            FxLib.ringPlane(m,at,u,v,(.18+.85*g)*big,.24*big,GLOW,.2f*f);
            FxLib.ringPlane(m,at,u,v,(.18+.85*g)*big,.08*big,WHITE,.6f*f);
        }
        // the spikes: white and violet, each in a purple glow (the black thorns among them are drawn over all of it)
        for(int j=THORNS;j<SPIKES;j++){
            var sp=spike(out,way,k,j,seed,s,big);if(sp==null)continue;
            m.needle(sp.a(),sp.b(),sp.width()*2.6,.25,GLOW,.3f*sp.fade()*nearOut);
            m.needle(sp.a(),sp.b(),sp.width(),.25,sp.rgb(),.9f*sp.fade()*nearOut);
        }
        // white sparks flying out, a short streak each
        for(int j=0;j<SPARKS;j++){
            var b=bit(out,way,k,j,seed,s,big,false);if(b==null)continue;
            m.needle(b.at(),b.at().subtract(b.v().multiply(1.4)),b.size(),.3,j%2==0?WHITE:HOT,.9f*b.fade()*near(b.at(),camera));
        }
    }
    /** Draws blow {@code k}'s black, over all the light: the thorns among its spikes and the drops flying out and falling. */
    static void black(EffectMesh m,Vec3d feet,Pierce p,int k,int seed,float s,boolean last,Vec3d camera){
        if(s<0||s>=LIFE)return;
        double big=last?LAST:1;
        Vec3d out=feet.add(p.out()),way=p.way();
        float nearOut=near(out,camera);if(nearOut<=0)return;
        for(int j=0;j<THORNS;j++){
            var sp=spike(out,way,k,j,seed,s,big);if(sp==null)continue;
            m.needle(sp.a(),sp.b(),sp.width(),.15,INK,.95f*sp.fade()*nearOut);
        }
        // and one among the spikes back out where it went in
        var back=spike(feet.add(p.in()),way.negate(),k,16,seed,s,big*BACK);
        if(back!=null)m.needle(back.a(),back.b(),back.width(),.15,INK,.95f*back.fade()*near(back.a(),camera));
        for(int j=0;j<DROPS;j++){
            var b=bit(out,way,k,j,seed,s,big,true);if(b==null)continue;
            m.needle(b.at(),b.at().subtract(b.v().multiply(1.3)),b.size(),.5,INK,.9f*b.fade()*near(b.at(),camera));
        }
    }

    // ───────────────────────────── the end: out of the whole body ─────────────────────────────

    /** How many spikes burst out of the whole body at the end (every so many of them a black thorn), sparks and drops. */
    static final int FINALE_SPIKES=20,FINALE_THORN_EVERY=3,FINALE_SPARKS=26,FINALE_DROPS=16;
    /** Its size (the spikes' reach, of a blow's). */
    static final double FINALE_BIG=1.45;
    /** Whether the burst at the end's spike {@code j} is a black thorn. */
    static boolean finaleThorn(int j){return j%FINALE_THORN_EVERY==0;}
    /** Spike {@code j} of the burst out of the whole body, from its {@code centre}, {@code s} ticks after it: evenly all round (the golden angle on from the last), less up and down. */
    static Spike finaleSpike(Vec3d centre,int j,int seed,float s){
        double a=2*Math.PI*(j*.381966+.08*h(999,j,seed,40)),y=(2*h(999,j,seed,44)-1)*.7,r=Math.sqrt(1-y*y);
        Vec3d dir=new Vec3d(r*Math.cos(a),y,r*Math.sin(a));
        // a thorn of its own (the first spikes of a blow are its thorns)
        return spike(centre,dir,999,finaleThorn(j)?0:THORNS+j%(SPIKES-THORNS),seed+j*977,s,FINALE_BIG*(.8+.4*h(999,j,seed,41)));
    }
    /** The burst out of the whole body at the end, {@code s} ticks after it: its light ({@code light}) or its black. */
    static void finale(EffectMesh m,Vec3d centre,int seed,float s,boolean light,Vec3d camera){
        if(s<0||s>=FINALE_LIFE)return;
        float near=near(centre,camera);if(near<=0)return;
        if(!light){
            for(int j=0;j<FINALE_SPIKES;j++){
                if(!finaleThorn(j))continue;
                var sp=finaleSpike(centre,j,seed,s);if(sp==null)continue;
                m.needle(sp.a(),sp.b(),sp.width()*1.1,.15,INK,.95f*sp.fade()*near);
            }
            for(int j=0;j<FINALE_DROPS;j++){
                var b=bit(centre,unit(999,j,seed,42),999,j,seed+5,s,FINALE_BIG*.8,true);if(b==null)continue;
                m.needle(b.at(),b.at().subtract(b.v().multiply(1.3)),b.size(),.5,INK,.9f*b.fade()*near(b.at(),camera));
            }
            return;
        }
        if(s<4)m.softDisc(centre,1.5+.8*s/4,WHITE,.95f*(1-s/4)*near);
        if(s<3)m.star(centre,2.6,WHITE,.9f*(1-s/3)*near);
        if(s<9)m.softDisc(centre,3.2,GLOW,.45f*(1-s/9)*near);
        if(s<7){
            Vec3d n=camera.subtract(centre).normalize(),u=n.crossProduct(new Vec3d(0,1,0));
            if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(n).normalize();
            double g=1-sq(1-Math.min(1,s/6));float f=(1-s/7)*near;
            FxLib.ringPlane(m,centre,u,v,.5+3.7*g,.4,GLOW,.3f*f);
            FxLib.ringPlane(m,centre,u,v,.5+3.7*g,.14,WHITE,.8f*f);
        }
        for(int j=0;j<FINALE_SPIKES;j++){
            if(finaleThorn(j))continue;
            var sp=finaleSpike(centre,j,seed,s);if(sp==null)continue;
            m.needle(sp.a(),sp.b(),sp.width()*2.6,.25,GLOW,.3f*sp.fade()*near);
            m.needle(sp.a(),sp.b(),sp.width(),.25,sp.rgb(),.9f*sp.fade()*near);
        }
        for(int j=0;j<FINALE_SPARKS;j++){
            var b=bit(centre,unit(999,j,seed,43),999,j,seed+7,s,FINALE_BIG*.8,false);if(b==null)continue;
            m.needle(b.at(),b.at().subtract(b.v().multiply(1.4)),b.size(),.3,j%2==0?WHITE:HOT,.9f*b.fade()*near(b.at(),camera));
        }
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /** How much of something at {@code p} shows from a camera at {@code camera}: none in the camera (their own first person, in their head), all of it from 1.6 blocks off. */
    static float near(Vec3d p,Vec3d camera){return smooth((float)((p.distanceTo(camera)-.6)/1.0));}
    /** A steady unit vector of blow {@code k}'s part {@code j} (evenly over the sphere). */
    static Vec3d unit(int k,int j,int seed,int salt){
        double z=2*h(k,j,seed,salt)-1,a=2*Math.PI*h(k,j,seed,salt+1),r=Math.sqrt(Math.max(0,1-z*z));
        return new Vec3d(r*Math.cos(a),z,r*Math.sin(a));
    }
    /** A steady hash of blow {@code k}'s part {@code j}, its cast's {@code seed} and a {@code salt} (the same on every client). */
    static double h(int k,int j,int seed,int salt){return XCharaSpace.hash(k*64+j,seed*131+salt);}
    static float smooth(float x){return XCharaSpace.smooth(x);}
    static double sq(double x){return x*x;}
    /** How far in anything is {@code s} ticks after it started: over a frame at 60 a second, not at once. */
    static float in(float s){return s<=0?0:Math.min(1,s/.35f);}
}
