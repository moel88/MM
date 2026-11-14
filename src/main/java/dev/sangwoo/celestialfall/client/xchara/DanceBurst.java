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
 * back out; where it comes out, it erupts from their body along its way: a cone of deep purple spikes (2.112.5, user
 * request: "이펙트 크게 만들어주고 보라색을 더 진하게 만들어봐": half again as big, some one and a half to four blocks long, the
 * purple laid on deep, {@link #body}) with thin white cores down some and black thorns among them, a deep purple haze, a white
 * flash and star, a purple ring round its way, white and purple sparks and black drops flying out and falling. And each blow
 * covers them ({@link #cover}, 2.112.5: "이펙트가 커서 캐릭터가 덮일정도로"): a deep purple haze over their whole body (in
 * front of it, toward the camera) with deep purple and black spikes across the frame, a white flash and star; the blows ten a
 * second, they stay covered while they are cut up. The black is drawn over all the light ({@link #black}) so its glow does not
 * purple it.
 * <p>
 * The last blow's eruption is bigger, and as they are sent flying everything bursts out of their whole body at once
 * ({@link #finale}). Every part of it is a function of the blow's own clock ({@code s}, ticks since it landed), its number
 * and its cast's seed, the same on every client; near the camera (their own first person, in their head) it thins.
 */
final class DanceBurst {
    private DanceBurst(){}
    /** The black of its thorns and drops. */
    static final int INK=0x0a0418;
    /**
     * Its deep purple (2.112.5, user request: "보라색을 더 진하게 만들어봐": 2.112.4's spikes were white and a light violet in a
     * purple glow added over the sky, a lavender): the deep and the vivid purple of its spikes' bodies, of its rings and of the
     * haze where it bursts out, laid over what is behind them, not added to it ({@link #body}); the little purple glow added
     * round them; the purple of its sparks.
     */
    static final int DEEP=0x6a0cb8,PURPLE=0x9416e0,GLOW=0x8a14e8,SPARK=0xb048ff;
    /** Its white: the thin hot cores of some spikes, its flash and star, the thin line of its rings, some sparks. */
    static final int WHITE=0xffffff;
    /** How big it all is (2.112.5, user request: "이펙트 크게 만들어주고": half again as big as 2.112.4's). */
    static final double SIZE=1.5;
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

    /** One spike of an eruption at a moment: from {@code a} (on their skin) out to {@code b}, {@code width} at its widest, how strongly it shows, its colour (the deep or the vivid purple, or a black thorn) and whether a thin white core runs down it. */
    record Spike(Vec3d a,Vec3d b,double width,float fade,int rgb,boolean core){}
    /** Spike {@code j} of blow {@code k}'s eruption from {@code out} along {@code way}, {@code s} ticks after it landed (null while it does not show): shooting out at once, then gone. */
    static Spike spike(Vec3d out,Vec3d way,int k,int j,int seed,float s,double big){
        if(s<0||s>=SPIKE_LIFE)return null;
        Vec3d dir=way.add(unit(k,j,seed,10).multiply(SPREAD*(.35+.65*h(k,j,seed,13)))).normalize();
        double grow=1-sq(1-Math.min(1,s/1.2)),length=(1.1+1.5*h(k,j,seed,14))*big*grow;
        float fade=in(s)*(1-smooth((s-1)/(SPIKE_LIFE-1)));
        if(fade<=0||length<=1e-4)return null;
        boolean thorn=j<THORNS;
        int rgb=thorn?INK:j%2==0?DEEP:PURPLE;
        double width=(thorn?.17:.11+.07*h(k,j,seed,15))*big;
        return new Spike(out.add(dir.multiply(.04)),out.add(dir.multiply(.04+length*(thorn?.85:1))),width,fade,rgb,!thorn&&j%2==1);
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

    /** The size of blow {@code k}'s eruption (the last of them, {@code last}). */
    static double big(boolean last){return SIZE*(last?LAST:1);}
    /** The ring round a blow's way, running out along it: its middle {@code s} ticks after it landed and how far round it is (null once it is gone). */
    static double[] ring(float s){
        if(s<0||s>=RING_LIFE)return null;
        double g=1-sq(1-Math.min(1,s/4));
        return new double[]{.15+.7*g,.18+.85*g,1-s/RING_LIFE};
    }
    /** Two units across {@code way} (unit). */
    static Vec3d[] across(Vec3d way){
        Vec3d u=way.crossProduct(Math.abs(way.y)>.9?new Vec3d(1,0,0):new Vec3d(0,1,0)).normalize();
        return new Vec3d[]{u,way.crossProduct(u).normalize()};
    }

    /** Draws blow {@code k}'s deep purple, laid over what is behind it (the effects' blended pass): the haze where it bursts out, its ring's band and its spikes' bodies, and theirs where it went in. */
    static void body(EffectMesh m,Vec3d feet,Pierce p,int k,int seed,float s,boolean last,Vec3d camera){
        if(s<0||s>=LIFE)return;
        double big=big(last);
        Vec3d out=feet.add(p.out()),in=feet.add(p.in()),way=p.way();
        float nearOut=near(out,camera),nearIn=near(in,camera);
        if(nearIn>0){
            if(s<4)m.softDisc(in,.55*big,DEEP,.5f*(1-s/4)*nearIn);
            for(int j=1;j<BACK_SPIKES;j++){
                var sp=spike(in,way.negate(),k,j+16,seed,s,big*BACK);
                if(sp!=null)m.needle(sp.a(),sp.b(),sp.width(),.25,sp.rgb(),.92f*sp.fade()*nearIn);
            }
        }
        if(nearOut<=0)return;
        if(s<6)m.softDisc(out.add(way.multiply(.3*big)),.95*big,DEEP,.55f*(1-s/6)*nearOut);
        var r=ring(s);
        if(r!=null){var uv=across(way);band(m,out.add(way.multiply(r[0]*big)),uv[0],uv[1],r[1]*big,.18*big,PURPLE,.8f*(float)r[2]*nearOut);}
        for(int j=THORNS;j<SPIKES;j++){
            var sp=spike(out,way,k,j,seed,s,big);
            if(sp!=null)m.needle(sp.a(),sp.b(),sp.width(),.25,sp.rgb(),.92f*sp.fade()*nearOut);
        }
    }
    /** Draws blow {@code k}'s light, added: white flashes and stars, a little purple glow round its spikes, the white cores down some, its ring's thin white line, its sparks. */
    static void light(EffectMesh m,Vec3d feet,Pierce p,int k,int seed,float s,boolean last,Vec3d camera){
        if(s<0||s>=LIFE)return;
        double big=big(last);
        Vec3d out=feet.add(p.out()),in=feet.add(p.in()),way=p.way();
        float nearOut=near(out,camera),nearIn=near(in,camera);
        if(nearIn>0){
            if(s<2)m.softDisc(in,.3*big,WHITE,.8f*(1-s/2)*nearIn);
            if(s<2)m.star(in,.6*big,WHITE,.7f*(1-s/2)*nearIn);
            for(int j=1;j<BACK_SPIKES;j++){
                var sp=spike(in,way.negate(),k,j+16,seed,s,big*BACK);if(sp==null)continue;
                spikeLight(m,sp,nearIn);
            }
        }
        if(nearOut<=0)return;
        if(s<FLASH)m.softDisc(out,(.32+.28*s/FLASH)*big,WHITE,.9f*(1-s/FLASH)*nearOut);
        if(s<2.5f)m.star(out,.85*big,WHITE,.8f*(1-s/2.5f)*nearOut);
        var r=ring(s);
        if(r!=null){var uv=across(way);FxLib.ringPlane(m,out.add(way.multiply(r[0]*big)),uv[0],uv[1],r[1]*big,.06*big,WHITE,.45f*(float)r[2]*nearOut);}
        for(int j=THORNS;j<SPIKES;j++){
            var sp=spike(out,way,k,j,seed,s,big);if(sp==null)continue;
            spikeLight(m,sp,nearOut);
        }
        for(int j=0;j<SPARKS;j++){
            var b=bit(out,way,k,j,seed,s,big,false);if(b==null)continue;
            m.needle(b.at(),b.at().subtract(b.v().multiply(1.4)),b.size(),.3,j%2==0?WHITE:SPARK,.9f*b.fade()*near(b.at(),camera));
        }
    }
    /** A spike's light: a little purple glow round it, a thin white core down it if it has one. */
    static void spikeLight(EffectMesh m,Spike sp,float near){
        m.needle(sp.a(),sp.b(),sp.width()*2.2,.25,GLOW,.14f*sp.fade()*near);
        if(sp.core())m.needle(sp.a(),sp.b(),sp.width()*.32,.25,WHITE,.85f*sp.fade()*near);
    }
    /** Draws blow {@code k}'s black, over all the light: the thorns among its spikes and the drops flying out and falling. */
    static void black(EffectMesh m,Vec3d feet,Pierce p,int k,int seed,float s,boolean last,Vec3d camera){
        if(s<0||s>=LIFE)return;
        double big=big(last);
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

    // ───────────────────────────── the cover: over their whole body ─────────────────────────────

    /**
     * How big the burst over their body is (2.112.5, user request: "이펙트가 커서 캐릭터가 덮일정도로": each blow covering the one
     * marked, the blows overlapping so that they stay covered while they are cut up): its haze's reach (blocks, from their
     * body's middle: their whole body under it), its spikes' farthest reach.
     */
    static final double COVER=2.0,COVER_SPIKE=4.2;
    /** How long it lasts (ticks), its spikes. */
    static final float COVER_LIFE=6,COVER_SPIKE_LIFE=4.5f;
    /** How many spikes it has across the frame (of them so many black thorns). */
    static final int COVER_SPIKES=12,COVER_THORNS=3;
    /** How far toward the camera from their body's middle it lies, so that it lies over them (in front of their body, not in it). */
    static final double COVER_FRONT=.75;
    /** Where the burst over their body lies: their body's {@code mid}dle, toward the camera. */
    static Vec3d coverAt(Vec3d mid,Vec3d camera){
        Vec3d to=camera.subtract(mid);double d=to.length();
        return d<1e-4?mid:mid.add(to.multiply(Math.min(COVER_FRONT,d*.4)/d));
    }
    /** Spike {@code j} of blow {@code k}'s burst over their body, from where it lies {@code at} out across the frame, {@code s} ticks after it landed (null while it does not show). */
    static Spike coverSpike(Vec3d at,Vec3d camera,int k,int j,int seed,float s,double big){
        if(s<0||s>=COVER_SPIKE_LIFE)return null;
        Vec3d n=camera.subtract(at);if(n.lengthSquared()<1e-6)return null;n=n.normalize();
        Vec3d u=n.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();
        Vec3d v=u.crossProduct(n).normalize();
        double a=2*Math.PI*((j+.6*h(k,j,seed,50))/COVER_SPIKES+h(k,0,seed,51));
        Vec3d dir=u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a)*.8)).normalize();
        double grow=1-sq(1-Math.min(1,s/1.0)),length=COVER_SPIKE*(.5+.5*h(k,j,seed,52))*big*grow;
        float fade=in(s)*(1-smooth((s-.8f)/(COVER_SPIKE_LIFE-.8f)));
        if(fade<=0||length<=1e-4)return null;
        boolean thorn=j<COVER_THORNS;
        double width=(thorn?.2:.16+.1*h(k,j,seed,53))*big;
        return new Spike(at.add(dir.multiply(.15*big)),at.add(dir.multiply(.15*big+length)),width,fade,thorn?INK:j%2==0?DEEP:PURPLE,!thorn&&j%3==1);
    }
    /** How strongly blow {@code k}'s haze over their body shows {@code s} ticks after it landed (0 once it is gone). */
    static float coverHaze(float s){return s<0||s>=COVER_LIFE?0:in(s)*(1-s/COVER_LIFE);}
    /** Blow {@code k}'s burst over their body, from its {@code mid}dle: its deep purple (pass 0), its light (1) or its black (2). */
    static void cover(EffectMesh m,Vec3d mid,int k,int seed,float s,boolean last,int pass,Vec3d camera){
        if(s<0||s>=COVER_LIFE)return;
        double big=last?LAST:1;
        Vec3d at=coverAt(mid,camera);float near=near(at,camera);if(near<=0)return;
        float haze=coverHaze(s);
        if(pass==0){
            m.softDisc(at,COVER*big*(.85+.15*Math.min(1,s/2)),DEEP,.62f*haze*near);
            m.softDisc(at,COVER*.6*big,PURPLE,.45f*haze*haze*near);
            for(int j=COVER_THORNS;j<COVER_SPIKES;j++){
                var sp=coverSpike(at,camera,k,j,seed,s,big);
                if(sp!=null)m.needle(sp.a(),sp.b(),sp.width(),.25,sp.rgb(),.92f*sp.fade()*near);
            }
        }else if(pass==1){
            if(s<2.5f)m.softDisc(at,.65*big,WHITE,.8f*(1-s/2.5f)*near);
            if(s<2)m.star(at,1.6*big,WHITE,.75f*(1-s/2)*near);
            for(int j=COVER_THORNS;j<COVER_SPIKES;j++){
                var sp=coverSpike(at,camera,k,j,seed,s,big);if(sp!=null)spikeLight(m,sp,near);
            }
        }else{
            for(int j=0;j<COVER_THORNS;j++){
                var sp=coverSpike(at,camera,k,j,seed,s,big);
                if(sp!=null)m.needle(sp.a(),sp.b(),sp.width(),.15,INK,.95f*sp.fade()*near);
            }
        }
    }

    // ───────────────────────────── the end: out of the whole body ─────────────────────────────

    /** How many spikes burst out of the whole body at the end (every so many of them a black thorn), sparks and drops. */
    static final int FINALE_SPIKES=20,FINALE_THORN_EVERY=3,FINALE_SPARKS=26,FINALE_DROPS=16;
    /** Its size (the spikes' reach, of a blow's; 2.112.5: bigger with the rest of it). */
    static final double FINALE_BIG=1.9;
    /** How much of the rest of it (its flash, star, haze and ring) is bigger than 2.112.4's. */
    static final double FINALE_GROW=1.3;
    /** Whether the burst at the end's spike {@code j} is a black thorn. */
    static boolean finaleThorn(int j){return j%FINALE_THORN_EVERY==0;}
    /** Spike {@code j} of the burst out of the whole body, from its {@code centre}, {@code s} ticks after it: evenly all round (the golden angle on from the last), less up and down. */
    static Spike finaleSpike(Vec3d centre,int j,int seed,float s){
        double a=2*Math.PI*(j*.381966+.08*h(999,j,seed,40)),y=(2*h(999,j,seed,44)-1)*.7,r=Math.sqrt(1-y*y);
        Vec3d dir=new Vec3d(r*Math.cos(a),y,r*Math.sin(a));
        // a thorn of its own (the first spikes of a blow are its thorns)
        return spike(centre,dir,999,finaleThorn(j)?0:THORNS+j%(SPIKES-THORNS),seed+j*977,s,FINALE_BIG*(.8+.4*h(999,j,seed,41)));
    }
    /** The burst out of the whole body at the end, {@code s} ticks after it: its deep purple (pass 0), its light (1) or its black (2). */
    static void finale(EffectMesh m,Vec3d centre,int seed,float s,int pass,Vec3d camera){
        if(s<0||s>=FINALE_LIFE)return;
        float near=near(centre,camera);if(near<=0)return;
        double gr=FINALE_GROW;
        Vec3d[] uv=null;
        if(s<7){
            Vec3d n=camera.subtract(centre).normalize(),u=n.crossProduct(new Vec3d(0,1,0));
            if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();
            uv=new Vec3d[]{u,u.crossProduct(n).normalize()};
        }
        double ringR=(.5+3.7*(1-sq(1-Math.min(1,s/6))))*gr;float ringF=(1-s/7)*near;
        if(pass==0){
            // over their whole body, as each blow's (and bigger)
            if(s<9)m.softDisc(coverAt(centre,camera),2.6*gr,DEEP,.62f*in(s)*(1-s/9)*near);
            if(uv!=null)band(m,centre,uv[0],uv[1],ringR,.32*gr,PURPLE,.75f*ringF);
            for(int j=0;j<FINALE_SPIKES;j++){
                if(finaleThorn(j))continue;
                var sp=finaleSpike(centre,j,seed,s);
                if(sp!=null)m.needle(sp.a(),sp.b(),sp.width(),.25,sp.rgb(),.92f*sp.fade()*near);
            }
            return;
        }
        if(pass==2){
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
        if(s<4)m.softDisc(coverAt(centre,camera),(1.3+.7*s/4)*gr,WHITE,.9f*(1-s/4)*near);
        if(s<3)m.star(coverAt(centre,camera),2.3*gr,WHITE,.85f*(1-s/3)*near);
        if(uv!=null)FxLib.ringPlane(m,centre,uv[0],uv[1],ringR,.1*gr,WHITE,.5f*ringF);
        for(int j=0;j<FINALE_SPIKES;j++){
            if(finaleThorn(j))continue;
            var sp=finaleSpike(centre,j,seed,s);if(sp==null)continue;
            spikeLight(m,sp,near);
        }
        for(int j=0;j<FINALE_SPARKS;j++){
            var b=bit(centre,unit(999,j,seed,43),999,j,seed+7,s,FINALE_BIG*.8,false);if(b==null)continue;
            m.needle(b.at(),b.at().subtract(b.v().multiply(1.4)),b.size(),.3,j%2==0?WHITE:SPARK,.9f*b.fade()*near(b.at(),camera));
        }
    }
    /** A flat ring of one colour about {@code c} in the plane of {@code u} and {@code v} (units): {@code r} its middle, {@code width} across, soft at both edges. */
    static void band(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double r,double width,int rgb,float alpha){
        if(alpha<=0||r<=0)return;
        int n=Math.max(24,Math.min(96,(int)(r*12)));double ri=Math.max(0,r-width/2),ro=r+width/2;
        for(int i=0;i<n;i++){
            double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
            Vec3d d0=u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))),d1=u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
            m.vertex(c.add(d0.multiply(ri)),rgb,0);m.vertex(c.add(d1.multiply(ri)),rgb,0);m.vertex(c.add(d1.multiply(r)),rgb,alpha);m.vertex(c.add(d0.multiply(r)),rgb,alpha);
            m.vertex(c.add(d0.multiply(r)),rgb,alpha);m.vertex(c.add(d1.multiply(r)),rgb,alpha);m.vertex(c.add(d1.multiply(ro)),rgb,0);m.vertex(c.add(d0.multiply(ro)),rgb,0);
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
