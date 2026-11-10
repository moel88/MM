package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;

/**
 * One blow of X Chara's sword dance on the one marked, in the world (2.112.1, user request: "컷씬이 끝나고 적에게 박히는
 * 검무 이펙트를 영상처럼 엄청 대폭 상향해줘 지금은 너무 부족해"; 2.112.0 drew each a violet crescent with a few streaks.
 * 2.112.2, user request: "검무 박힐때 이펙트 더 크게 하고 진한 보라색으로 만들어줘": half again as big, deep purple.
 * 2.112.3, user request: "검무 이펙트가 너무 크고 이펙트를 빛나는 보라 빛나는 흰색 검정 이렇게 섞어서 만들어봐 영상처럼":
 * the video's size again, its glowing purple, glowing white and black).
 * A burst as the video's: a flower of light about them some four blocks either way and less up and down, ten
 * a second, so that they overlap in a storm. Each: petals shooting out from them (glowing purple: a vivid violet, hot
 * and light by the heart, with a purple glow on their edges, white lines on some and a black streak down others), white
 * loops swept round them like a blade's trail, a white slash across them with a purple glow and a black edge, black ink
 * spikes with a purple edge, thin white and violet rays, white shards flying off, a white flash at the heart. The last
 * blow's bigger again, with more of everything and a long purple and white streak through them (the video's 11.43 s).
 * <p>
 * Like the video's, it is a picture of light facing whoever looks at it: the flower lies across the camera that draws it
 * ({@link #frame}), a little behind them from it so their body stays in front of its heart, each petal and stroke a blade
 * of light turned to the camera about its own line. Every part of it is a function of the blow's own clock ({@code s},
 * ticks since it landed) and its number, the same on every client.
 */
final class DanceBurst {
    private DanceBurst(){}
    /** How long a blow's petals last, its loops, its ink spikes, its slash and rays, its shards (ticks). */
    static final float PETAL_LIFE=8.5f,LOOP_LIFE=7,INK_LIFE=6,SLASH_LIFE=3.5f,RAY_LIFE=2.5f,SHARD_LIFE=7;
    /** The longest any part of a blow shows. */
    static final float LIFE=PETAL_LIFE+1;
    /** How much flatter the flower is up and down than across (the video's: wider than tall). */
    static final double SQUASH=.75;
    /** How long a petal takes to shoot out to its full length (ticks: most of it in the first frame or two), and how long anything takes to come in (a frame). */
    static final float GROW=1.7f,IN=.35f;
    /** How far behind the one marked (from the camera) its heart is, so that their body stays in front of it. */
    static final double BEHIND=.55;
    /** The black of its ink spikes, its slash's edge and the streaks down some petals. */
    static final int INK=0x0a0418;
    /**
     * A petal's colours from its base to its tip (2.112.3, the video's glowing purple: 2.112.2's deep purple was dark, its
     * light dimmed so as not to pale in the day): down its middle a light violet by the heart deepening outward, at its
     * edges a vivid violet.
     */
    static final int AXIS_BASE=0xc848ff,AXIS_TIP=0xa420f4,RIM_BASE=0xb014f4,RIM_TIP=0x9010e0;
    /** Its light: the purple glow on its edges and round its strokes, the hot light violet in its petals, the white of its loops, slash, rays and flash. */
    static final int GLOW=0xa81cff,HOT=0xecc4ff,WHITE=0xffffff;
    /** How big every blow is (2.112.3: the video's, some 3.4 times the one marked's height across all of them together; 2.112.2's 1.5 was too big), the last blow so much bigger again. */
    static final double SCALE=.9,LAST=1.4;
    /** How big blow {@code k}'s burst is (the last of them, {@code last}). */
    static double big(boolean last){return SCALE*(last?LAST:1);}
    /** How many petals a blow has. */
    static int petalCount(boolean last){return last?16:10;}

    /** The plane the flower lies in for this camera: its heart {@code o}, across ({@code u}) and up ({@code v}) the frame, toward the camera ({@code n}). */
    record Frame(Vec3d o,Vec3d u,Vec3d v,Vec3d n,float near){
        /** The way out from the heart at {@code angle} round it, {@code depth} toward the camera (or away). */
        Vec3d out(double angle,double depth){
            return u.multiply(Math.cos(angle)).add(v.multiply(Math.sin(angle)*SQUASH)).add(n.multiply(depth)).normalize();
        }
    }
    /** The flower's plane about {@code mid} (the one marked's middle) for a camera at {@code cam}. */
    static Frame frame(Vec3d mid,Vec3d cam){
        Vec3d to=cam.subtract(mid);double far=to.length();
        Vec3d n=far<1e-4?new Vec3d(0,0,1):to.multiply(1/far);
        Vec3d u=n.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();
        Vec3d v=u.crossProduct(n).normalize();
        // its heart a little behind them, unless the camera is in them (their own first person)
        Vec3d o=far>1.6?mid.subtract(n.multiply(BEHIND)):mid;
        // close to the camera it thins (their own view of it, a third left at most)
        float near=(float)(.3+.7*clamp((far-1.2)/1.8));
        return new Frame(o,u,v,n,near);
    }

    /** Draws blow {@code k}'s burst {@code s} ticks after it landed: its dark parts ({@code light} false) or its light. */
    static void draw(EffectMesh m,Frame fr,int k,int seed,float s,boolean light,boolean last){
        if(s<0||s>=LIFE)return;
        double big=big(last);
        petals(m,fr,k,seed,s,light,big,petalCount(last));
        if(light){
            loops(m,fr,k,seed,s,big,last?6:3);
            rays(m,fr,k,seed,s,big,last?8:5);
            shards(m,fr,k,seed,s,big,last?14:8);
            heart(m,fr,s,big);
            if(last)streak(m,fr,k,seed,s);
        }
        slash(m,fr,k,seed,s,light,big,last?3:1);
        // the ink spikes' purple edge (their black is drawn over all the light, in black)
        if(light)ink(m,fr,k,seed,s,true,big,last?11:6);
    }
    /**
     * Blow {@code k}'s black, drawn after all its light (2.112.3: drawn before it, the glow over it turned it a dark purple):
     * its ink spikes and the streaks of ink down some of its petals, the video's black among the glowing purple and white.
     */
    static void black(EffectMesh m,Frame fr,int k,int seed,float s,boolean last){
        if(s<0||s>=LIFE)return;
        double big=big(last);int count=petalCount(last);
        for(int j=0;j<count;j++){
            var p=petal(fr,k,j,seed,s,big,count);if(p==null||!p.inked())continue;
            Vec3d d=p.b().subtract(p.a());
            m.needle(p.a().add(d.multiply(.3)),p.a().add(d.multiply(.96)),p.w()*.5,.2,INK,.9f*p.fade());
        }
        ink(m,fr,k,seed,s,false,big,last?11:6);
    }

    // ───────────────────────────── its parts ─────────────────────────────

    /** One petal at a moment: from {@code a} (its base) to {@code b} (its tip), {@code w} half wide at its widest, how strongly it shows, and its touches (a white line along one edge, a black streak down its middle). */
    record Petal(Vec3d a,Vec3d b,double w,float fade,boolean rimmed,int rimSide,boolean inked){}
    /** Blow {@code k}'s petal {@code j} of {@code count}, {@code s} ticks after it landed (null while it does not show): shooting out from the heart in a moment, evenly round it (each a little off), fading as it drifts on and thins. */
    static Petal petal(Frame fr,int k,int j,int seed,float s,double big,int count){
        float sp=s-(float)(.8*h(k,j,seed,6));if(sp<0||sp>=PETAL_LIFE)return null;
        double angle=2*Math.PI*h(k,0,seed,1)+2*Math.PI*(j+.7*h(k,j,seed,2))/count;
        Vec3d dir=fr.out(angle,(h(k,j,seed,3)-.5)*.6);
        double length=(2.4+2.4*h(k,j,seed,4))*big*(1-.3*Math.abs(Math.sin(angle)));
        double wide=length*(.17+.12*h(k,j,seed,5));
        float grow=1-cube(1-Math.min(1,sp/GROW));
        float fade=in(sp)*(sp<2.5f?1:1-smooth((sp-2.5f)/(PETAL_LIFE-2.5f)))*fr.near;
        if(fade<=0)return null;
        double base=(.1+.4*h(k,j,seed,7))*big+.1*sp;
        Vec3d a=fr.o.add(dir.multiply(base)),b=fr.o.add(dir.multiply(base+length*grow));
        double w=wide*(.3+.7*grow)*(1-.3*smooth((sp-2)/(PETAL_LIFE-2)));
        return new Petal(a,b,w,fade,h(k,j,seed,8)<.5&&sp<4,h(k,j,seed,9)<.5?1:-1,h(k,j,seed,10)<.35);
    }
    /**
     * The petals: their bodies ({@code light} false: a vivid violet, lighter down the middle by the heart) or their light (a
     * purple glow on their edges, a hot light violet glowing in them from the heart out, a white line along one edge of
     * some). The streak of ink down some is drawn over all of it ({@link #black}).
     */
    static void petals(EffectMesh m,Frame fr,int k,int seed,float s,boolean light,double big,int count){
        for(int j=0;j<count;j++){
            var p=petal(fr,k,j,seed,s,big,count);if(p==null)continue;
            Vec3d d=p.b().subtract(p.a());
            if(!light){
                lens(m,p.a(),p.b(),p.w(),AXIS_BASE,AXIS_TIP,RIM_BASE,RIM_TIP,.9f*p.fade(),.94f*p.fade());
                continue;
            }
            rim(m,p.a(),p.b(),p.w(),.3,GLOW,.3f*p.fade());
            // its glow: white-hot by the heart, out along its middle
            lens(m,p.a(),p.a().add(d.multiply(.62)),p.w()*.55,HOT,GLOW,GLOW,GLOW,.5f*p.fade(),0);
            lens(m,p.a(),p.a().add(d.multiply(.36)),p.w()*.36,WHITE,HOT,HOT,HOT,.55f*p.fade(),0);
            float since=s-(float)(.8*h(k,j,seed,6));
            if(p.rimmed())edge(m,p.a(),p.b(),p.w(),p.rimSide(),.06*big,WHITE,.85f*p.fade()*Math.max(0,1-since/4));
        }
    }
    /** The white loops: swept round the heart in a breath like a blade's trail, each two strands in a purple glow, eaten from their tail. */
    static void loops(EffectMesh m,Frame fr,int k,int seed,float s,double big,int count){
        for(int j=0;j<count;j++){
            float sl=s-(float)(.5*h(k,j,seed,20));if(sl<0||sl>=LOOP_LIFE)continue;
            Vec3d c=fr.o.add(fr.u.multiply((h(k,j,seed,21)-.5)*.8*big)).add(fr.v.multiply((h(k,j,seed,22)-.5)*.5*big));
            // its plane: across the camera, tipped any way (an ellipse, flatter or rounder)
            double tip=(h(k,j,seed,23)-.5)*1.5,lean=(h(k,j,seed,24)-.5)*1.2;
            Vec3d p=rotate(fr.u,fr.v,lean),q=rotate(fr.v,fr.u,tip);
            p=p.subtract(q.multiply(p.dotProduct(q))).normalize();
            double r=(1.5+1.7*h(k,j,seed,25))*big,span=Math.toRadians(210+130*h(k,j,seed,26)),start=2*Math.PI*h(k,j,seed,27);
            double head=smooth(sl/1.4f),tail=smooth((sl-1.6f)/(LOOP_LIFE-1.6f));
            if(head<=tail)continue;
            float fade=(1-smooth((sl-3)/(LOOP_LIFE-3)))*fr.near;
            int segs=30;
            for(int strand=0;strand<2;strand++){
                double rr=r*(strand==0?1:1.07);
                Vec3d last=null;
                for(int i=0;i<=segs;i++){
                    double x=tail+(head-tail)*i/segs,a=start+span*x;
                    Vec3d at=c.add(p.multiply(Math.cos(a)*rr)).add(q.multiply(Math.sin(a)*rr*.62));
                    if(last!=null){
                        // the strand thins toward its tail end
                        float along=(float)((x-tail)/(head-tail));
                        if(strand==0){
                            // a broad soft sweep of white in a purple haze, a crisp white line down it (the video's)
                            m.softLine(last,at,1.1*big,GLOW,.18f*fade*along);
                            m.softLine(last,at,.7*big,WHITE,.38f*fade*along);
                            m.line(last,at,.14*big,WHITE,.95f*fade*(.3f+.7f*along));
                        }else m.line(last,at,.05*big,WHITE,.7f*fade*along);
                    }
                    last=at;
                }
            }
        }
    }
    /** A slash across them: a white blade with a purple glow and a black edge, there in a moment and gone. */
    static void slash(EffectMesh m,Frame fr,int k,int seed,float s,boolean light,double big,int count){
        if(s>=SLASH_LIFE)return;
        float left=1-s/SLASH_LIFE,fade=in(s)*left*left*fr.near,out=1-cube(1-Math.min(1,s/.6f));
        for(int j=0;j<count;j++){
            double angle=2*Math.PI*h(k,j,seed,30);
            Vec3d dir=fr.out(angle,(h(k,j,seed,31)-.5)*.4),off=fr.out(angle+Math.PI/2,0).multiply((h(k,j,seed,32)-.5)*1.1*big);
            double length=(3.6+2.6*h(k,j,seed,33))*big*out,width=(.5+.35*h(k,j,seed,34))*big*(.55+.45*left);
            Vec3d a=fr.o.add(off).subtract(dir.multiply(length*.5)),b=fr.o.add(off).add(dir.multiply(length*.5));
            if(!light){
                Vec3d grow=dir.multiply(length*.04);
                m.needle(a.subtract(grow),b.add(grow),width*1.35,.42,INK,.8f*fade);
            }else{
                m.needle(a,b,width*1.8,.42,GLOW,.32f*fade);
                m.needle(a,b,width*1.2,.42,WHITE,.35f*fade);
                m.needle(a,b,width*.7,.42,WHITE,.9f*fade);
            }
        }
    }
    /** The black ink spikes: shooting out from the heart, a purple edge to them, thinning as they go. */
    static void ink(EffectMesh m,Frame fr,int k,int seed,float s,boolean light,double big,int count){
        for(int j=0;j<count;j++){
            float si=s-(float)(.6*h(k,j,seed,40));if(si<0||si>=INK_LIFE)continue;
            Vec3d dir=fr.out(2*Math.PI*h(k,j,seed,41),(h(k,j,seed,42)-.5)*.5);
            double start=.5*big,length=(3+2.6*h(k,j,seed,43))*big,width=(.24+.18*h(k,j,seed,44))*big*(1-.4*smooth(si/INK_LIFE));
            float grow=1-(1-Math.min(1,si))*(1-Math.min(1,si)),fade=(1-smooth((si-1.5f)/(INK_LIFE-1.5f)))*fr.near;
            Vec3d a=fr.o.add(dir.multiply(start)),b=fr.o.add(dir.multiply(start+length*grow));
            if(!light)m.needle(a,b,width,.15,INK,.95f*fade);
            else m.needle(a,b,width*1.5,.15,0x7a14e8,.34f*fade);
        }
    }
    /** Thin rays of white and violet through the burst, for a moment. */
    static void rays(EffectMesh m,Frame fr,int k,int seed,float s,double big,int count){
        if(s>=RAY_LIFE)return;
        float fade=in(s)*(1-s/RAY_LIFE)*fr.near;
        for(int j=0;j<count;j++){
            Vec3d dir=fr.out(2*Math.PI*h(k,j,seed,50),(h(k,j,seed,51)-.5)*.5);
            double length=(3.8+4*h(k,j,seed,52))*big,width=(.06+.05*h(k,j,seed,53))*big;
            m.needle(fr.o.add(dir.multiply(.2)),fr.o.add(dir.multiply(.2+length)),width,.2,j%5<3?WHITE:0xc060ff,.9f*fade);
        }
    }
    /** White and violet shards of light flying off the petals' tips. */
    static void shards(EffectMesh m,Frame fr,int k,int seed,float s,double big,int count){
        for(int j=0;j<count;j++){
            float sh=s-(float)(.6*h(k,j,seed,60));if(sh<0||sh>=SHARD_LIFE)continue;
            Vec3d dir=fr.out(2*Math.PI*h(k,j,seed,61),(h(k,j,seed,62)-.5)*.7);
            double at=(1.2+(3.5+3*h(k,j,seed,63))*Math.sqrt(sh/SHARD_LIFE))*big;
            Vec3d p=fr.o.add(dir.multiply(at));
            m.needle(p.subtract(dir.multiply(.28*big)),p.add(dir.multiply(.28*big)),.08*big,.6,j%3!=0?WHITE:0xb45cff,.85f*in(sh)*(1-sh/SHARD_LIFE)*fr.near);
        }
    }
    /** The flash at the heart as it lands: white, a white star, a purple glow. */
    static void heart(EffectMesh m,Frame fr,float s,double big){
        float in=in(s)*fr.near;
        if(s<2.5f)m.softDisc(fr.o,(.9+.8*s/2.5)*big,WHITE,.75f*(1-s/2.5f)*in);
        if(s<2)m.star(fr.o,1.6*big,WHITE,.85f*(1-s/2)*in);
        if(s<5)m.softDisc(fr.o,2.2*big,GLOW,.2f*(1-s/5)*in);
    }
    /** The last blow's long purple streak through them, white down its middle (the video's 11.43 s). */
    static void streak(EffectMesh m,Frame fr,int k,int seed,float s){
        if(s>=6)return;
        float fade=in(s)*(1-s/6)*fr.near;
        Vec3d dir=fr.out(Math.toRadians(32),0);
        double half=(8+3*smooth(s/1.2f))*SCALE*(1-cube(1-Math.min(1,s/.8f)));
        Vec3d a=fr.o.subtract(dir.multiply(half)),b=fr.o.add(dir.multiply(half));
        m.needle(a,b,1.3*SCALE,.5,GLOW,.5f*fade);
        m.needle(a,b,.32*SCALE,.5,WHITE,.9f*fade);
    }

    // ───────────────────────────── shapes ─────────────────────────────

    static final int SEGS=10;
    /** A petal of light from {@code a} to {@code b}, {@code w} half wide at its widest (an ellipse), turned to the camera about its line; along it from base to tip the colours run {@code axis0} to {@code axis1} down its middle and {@code rim0} to {@code rim1} at its edges. */
    static void lens(EffectMesh m,Vec3d a,Vec3d b,double w,int axis0,int axis1,int rim0,int rim1,float axisAlpha,float rimAlpha){
        Vec3d side=side(m,a,b);if(side==null||w<=0)return;
        Vec3d d=b.subtract(a);
        for(int i=0;i<SEGS;i++){
            double x0=(double)i/SEGS,x1=(double)(i+1)/SEGS;
            Vec3d c0=a.add(d.multiply(x0)),c1=a.add(d.multiply(x1));
            Vec3d s0=side.multiply(w*ellipse(x0)),s1=side.multiply(w*ellipse(x1));
            int ax0=mix(axis0,axis1,x0),ax1=mix(axis0,axis1,x1),r0=mix(rim0,rim1,x0),r1=mix(rim0,rim1,x1);
            for(int half=-1;half<=1;half+=2){
                m.vertex(c0,ax0,axisAlpha);m.vertex(c1,ax1,axisAlpha);
                m.vertex(c1.add(s1.multiply(half)),r1,rimAlpha);m.vertex(c0.add(s0.multiply(half)),r0,rimAlpha);
            }
        }
    }
    /** A soft glow along a petal's edges: from {@code inside} of the way in, brightest at the edge, out the same again. */
    static void rim(EffectMesh m,Vec3d a,Vec3d b,double w,double inside,int rgb,float alpha){
        Vec3d side=side(m,a,b);if(side==null||w<=0||alpha<=0)return;
        Vec3d d=b.subtract(a);
        for(int i=0;i<SEGS;i++){
            double x0=(double)i/SEGS,x1=(double)(i+1)/SEGS,e0=w*ellipse(x0),e1=w*ellipse(x1),band=w*inside;
            Vec3d c0=a.add(d.multiply(x0)),c1=a.add(d.multiply(x1));
            for(int half=-1;half<=1;half+=2){
                Vec3d in0=c0.add(side.multiply(half*Math.max(0,e0-band))),in1=c1.add(side.multiply(half*Math.max(0,e1-band)));
                Vec3d on0=c0.add(side.multiply(half*e0)),on1=c1.add(side.multiply(half*e1));
                Vec3d out0=c0.add(side.multiply(half*(e0+band))),out1=c1.add(side.multiply(half*(e1+band)));
                m.vertex(in0,rgb,0);m.vertex(in1,rgb,0);m.vertex(on1,rgb,alpha);m.vertex(on0,rgb,alpha);
                m.vertex(on0,rgb,alpha);m.vertex(on1,rgb,alpha);m.vertex(out1,rgb,0);m.vertex(out0,rgb,0);
            }
        }
    }
    /** A thin line along one edge of a petal ({@code half} +1 or -1). */
    static void edge(EffectMesh m,Vec3d a,Vec3d b,double w,int half,double width,int rgb,float alpha){
        Vec3d side=side(m,a,b);if(side==null||alpha<=0)return;
        Vec3d d=b.subtract(a),last=null;
        for(int i=0;i<=SEGS;i++){
            double x=(double)i/SEGS;Vec3d at=a.add(d.multiply(x)).add(side.multiply(half*w*ellipse(x)));
            if(last!=null)m.line(last,at,width,rgb,alpha);
            last=at;
        }
    }
    /** The way across a line from {@code a} to {@code b} as the camera sees it (null if it looks straight down the line). */
    static Vec3d side(EffectMesh m,Vec3d a,Vec3d b){
        Vec3d d=b.subtract(a);if(d.lengthSquared()<1e-8)return null;
        Vec3d s=d.crossProduct(m.camera().subtract(a.add(d.multiply(.5))));
        return s.lengthSquared()<1e-10?null:s.normalize();
    }
    /** A petal's half width {@code x} along it (0 its base, 1 its tip), of its widest: an ellipse's. */
    static double ellipse(double x){double y=2*x-1;return Math.sqrt(Math.max(0,1-y*y));}
    /** {@code v} turned by {@code angle} about {@code axis} (unit). */
    static Vec3d rotate(Vec3d v,Vec3d axis,double angle){
        double c=Math.cos(angle),s=Math.sin(angle);
        return v.multiply(c).add(axis.crossProduct(v).multiply(s)).add(axis.multiply(axis.dotProduct(v)*(1-c)));
    }
    static int mix(int a,int b,double t){
        int r=(int)Math.round(((a>>16)&255)+(((b>>16)&255)-((a>>16)&255))*t),g=(int)Math.round(((a>>8)&255)+(((b>>8)&255)-((a>>8)&255))*t),bl=(int)Math.round((a&255)+((b&255)-(a&255))*t);
        return r<<16|g<<8|bl;
    }
    /** A steady hash of blow {@code k}'s part {@code j}, its cast's {@code seed} and a {@code salt} (the same on every client). */
    static double h(int k,int j,int seed,int salt){return XCharaSpace.hash(k*64+j,seed*131+salt);}
    static float smooth(float x){return XCharaSpace.smooth(x);}
    static float cube(float x){return x*x*x;}
    /** How far in anything is {@code s} ticks after it started: over IN (a frame at 60 a second), not at once. */
    static float in(float s){return s<=0?0:Math.min(1,s/IN);}
    static double clamp(double x){return x<0?0:x>1?1:x;}
}
