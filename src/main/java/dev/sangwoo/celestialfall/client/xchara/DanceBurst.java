package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;

/**
 * One blow of X Chara's sword dance on the one marked, in the world (2.112.1, user request: "컷씬이 끝나고 적에게 박히는
 * 검무 이펙트를 영상처럼 엄청 대폭 상향해줘 지금은 너무 부족해"; 2.112.0 drew each a violet crescent with a few streaks).
 * A burst as the video's: a flower of light about them some four blocks either way and two and a half up and down, ten
 * a second, so that they overlap in a storm. Each: glowing petals shooting out from them (deep violet along the middle,
 * violet to magenta out to their tips, their edges glowing pink, white rims on some, a bright petal inside others), white
 * loops swept round them like a blade's trail, a white slash across them with a dark edge, dark ink spikes with a blue
 * edge, thin rays, shards flying off, a flash at the heart. The last blow's half again as big, with more of everything
 * and a long magenta streak through them (the video's 11.43 s).
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
    static final int WHITE=0xffffff,INK=0x0c0626;
    /** A petal's colours from its base to its tip: down its middle deep indigo to violet, at its edges violet to magenta (the video's). */
    static final int AXIS_BASE=0x2a10b0,AXIS_TIP=0x6a1ae8,RIM_BASE=0x9a20f0,RIM_TIP=0xff20e0;

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
        double big=last?1.5:1;
        petals(m,fr,k,seed,s,light,big,last?15:8);
        if(light){
            loops(m,fr,k,seed,s,big,last?5:2);
            rays(m,fr,k,seed,s,big,last?6:3);
            shards(m,fr,k,seed,s,big,last?12:6);
            heart(m,fr,s,big);
            if(last)streak(m,fr,k,seed,s);
        }
        slash(m,fr,k,seed,s,light,big,last?3:1);
        ink(m,fr,k,seed,s,light,big,last?10:5);
    }

    // ───────────────────────────── its parts ─────────────────────────────

    /** One petal at a moment: from {@code a} (its base) to {@code b} (its tip), {@code w} half wide at its widest, how strongly it shows, and its touches (a white rim, a bright petal inside). */
    record Petal(Vec3d a,Vec3d b,double w,float fade,boolean rimmed,int rimSide,boolean inner){}
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
        return new Petal(a,b,w,fade,h(k,j,seed,8)<.5&&sp<4,h(k,j,seed,9)<.5?1:-1,h(k,j,seed,10)<.4);
    }
    /** The petals: their bodies ({@code light} false: deep violet down the middle, violet to magenta out to the tips) or their light (pink edges, white rims, bright petals inside). */
    static void petals(EffectMesh m,Frame fr,int k,int seed,float s,boolean light,double big,int count){
        for(int j=0;j<count;j++){
            var p=petal(fr,k,j,seed,s,big,count);if(p==null)continue;
            if(!light){
                lens(m,p.a(),p.b(),p.w(),AXIS_BASE,AXIS_TIP,RIM_BASE,RIM_TIP,.8f*p.fade(),.92f*p.fade());
                continue;
            }
            rim(m,p.a(),p.b(),p.w(),.28,0xff3ae8,.3f*p.fade());
            float since=s-(float)(.8*h(k,j,seed,6));
            if(p.rimmed())edge(m,p.a(),p.b(),p.w(),p.rimSide(),.07*big,WHITE,.75f*p.fade()*Math.max(0,1-since/4));
            if(p.inner()){
                Vec3d d=p.b().subtract(p.a());
                lens(m,p.a().add(d.multiply(.45)),p.b().subtract(d.multiply(.08)),p.w()*.48,0xa030ff,0xff40ec,0xff60f0,0xff90f6,.22f*p.fade(),.16f*p.fade());
            }
        }
    }
    /** The white loops: swept round the heart in a breath like a blade's trail, each two strands, eaten from their tail. */
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
                            m.line(last,at,.34*big,0xff60f0,.38f*fade*along);
                            m.line(last,at,.09*big,WHITE,.95f*fade*(.3f+.7f*along));
                        }else m.line(last,at,.045*big,0xffe6ff,.65f*fade*along);
                    }
                    last=at;
                }
            }
        }
    }
    /** A white slash across them: a thick blade of white with a blue glow and a dark edge, there in a moment and gone. */
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
                m.needle(a.subtract(grow),b.add(grow),width*1.35,.42,INK,.75f*fade);
            }else{
                m.needle(a,b,width*1.9,.42,0x48a8ff,.4f*fade);
                m.needle(a,b,width,.42,WHITE,.95f*fade);
            }
        }
    }
    /** The dark ink spikes: shooting out from the heart, a blue edge to them, thinning as they go. */
    static void ink(EffectMesh m,Frame fr,int k,int seed,float s,boolean light,double big,int count){
        for(int j=0;j<count;j++){
            float si=s-(float)(.6*h(k,j,seed,40));if(si<0||si>=INK_LIFE)continue;
            Vec3d dir=fr.out(2*Math.PI*h(k,j,seed,41),(h(k,j,seed,42)-.5)*.5);
            double start=.5*big,length=(3+2.6*h(k,j,seed,43))*big,width=(.32+.22*h(k,j,seed,44))*big*(1-.4*smooth(si/INK_LIFE));
            float grow=1-(1-Math.min(1,si))*(1-Math.min(1,si)),fade=(1-smooth((si-1.5f)/(INK_LIFE-1.5f)))*fr.near;
            Vec3d a=fr.o.add(dir.multiply(start)),b=fr.o.add(dir.multiply(start+length*grow));
            if(!light)m.needle(a,b,width,.15,INK,.95f*fade);
            else m.needle(a,b,width*1.45,.15,0x3050ff,.4f*fade);
        }
    }
    /** Thin rays of white and lilac through the burst, for a moment. */
    static void rays(EffectMesh m,Frame fr,int k,int seed,float s,double big,int count){
        if(s>=RAY_LIFE)return;
        float fade=in(s)*(1-s/RAY_LIFE)*fr.near;
        for(int j=0;j<count;j++){
            Vec3d dir=fr.out(2*Math.PI*h(k,j,seed,50),(h(k,j,seed,51)-.5)*.5);
            double length=(3.8+4*h(k,j,seed,52))*big,width=(.06+.05*h(k,j,seed,53))*big;
            m.needle(fr.o.add(dir.multiply(.2)),fr.o.add(dir.multiply(.2+length)),width,.2,j%2==0?WHITE:0xe0b8ff,.85f*fade);
        }
    }
    /** Shards of light flying off the petals' tips. */
    static void shards(EffectMesh m,Frame fr,int k,int seed,float s,double big,int count){
        for(int j=0;j<count;j++){
            float sh=s-(float)(.6*h(k,j,seed,60));if(sh<0||sh>=SHARD_LIFE)continue;
            Vec3d dir=fr.out(2*Math.PI*h(k,j,seed,61),(h(k,j,seed,62)-.5)*.7);
            double at=(1.2+(3.5+3*h(k,j,seed,63))*Math.sqrt(sh/SHARD_LIFE))*big;
            Vec3d p=fr.o.add(dir.multiply(at));
            m.needle(p.subtract(dir.multiply(.28*big)),p.add(dir.multiply(.28*big)),.08*big,.6,j%3==0?WHITE:0xff7af2,.85f*in(sh)*(1-sh/SHARD_LIFE)*fr.near);
        }
    }
    /** The flash at the heart as it lands. */
    static void heart(EffectMesh m,Frame fr,float s,double big){
        float in=in(s)*fr.near;
        if(s<2.5f)m.softDisc(fr.o,(1.2+1.1*s/2.5)*big,WHITE,.8f*(1-s/2.5f)*in);
        if(s<2)m.star(fr.o,1.5*big,0xffd0ff,.9f*(1-s/2)*in);
        if(s<5)m.softDisc(fr.o,2.6*big,0xff40e8,.3f*(1-s/5)*in);
    }
    /** The last blow's long magenta streak through them (the video's 11.43 s). */
    static void streak(EffectMesh m,Frame fr,int k,int seed,float s){
        if(s>=6)return;
        float fade=in(s)*(1-s/6)*fr.near;
        Vec3d dir=fr.out(Math.toRadians(32),0);
        double half=(8+3*smooth(s/1.2f))*(1-cube(1-Math.min(1,s/.8f)));
        Vec3d a=fr.o.subtract(dir.multiply(half)),b=fr.o.add(dir.multiply(half));
        m.needle(a,b,1.3,.5,0xff40f0,.55f*fade);
        m.needle(a,b,.32,.5,WHITE,.9f*fade);
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
