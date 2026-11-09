package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;

/**
 * X Chara's G, the sword dance, drawn (2.112.0, through the effect layer, from its cast's clock: XCharaClient, the user's
 * video for every look). For everyone near: the "!!!" over the one marked, blinking red and white until the strange space
 * (gone at once if it is blocked, a flash of white light on them then); the blows on the one marked where they stand, ten a
 * second, each a great violet crescent of a cut about them with a white edge, white streaks through them, dark ink
 * spikes and a flash; sent flying, a burst and a violet trail after them. For its two in the strange space (and only
 * there, XCharaClient.cinematic): the violet spikes rising out of the dark, the glowing pink panels flying through the
 * second shot, the knife raised upright glowing in the first, and the knives round its user in the third, white with a
 * violet glow, aimed at the one marked.
 */
public final class SwordDance implements Fx.Skill {
    static final int RED=0xff2a2a,WHITE=0xffffff,INK=0x0e0820,VIOLET=0x9a3cff,LILAC=0xe2b8ff,PINK=0xff40d6,GLOW=0x7a30ff;
    /** How long each blow's cut shows (ticks). */
    static final float CUT_LIFE=10;

    @Override public boolean layered(){return true;}
    @Override public float lifetime(){return (END+60)/20f;}

    /** The sword dance this cast draws, and its clock (ticks) this frame. */
    private static XCharaClient.Dance dance(Fx c){return XCharaClient.ofCast(c.cast);}
    /** Whether this client's camera is in this sword dance's strange space now. */
    private static boolean space(XCharaClient.Dance d){return d!=null&&XCharaClient.cinematic()==d;}
    /** Whether the world's side of it is seen by this client at {@code t}: everyone's, but its two's while they are in its strange space. */
    private static boolean world(XCharaClient.Dance d,float t){return !space(d);}

    @Override public void alpha(Fx c,EffectMesh m){
        var d=dance(c);if(d==null)return;float t=c.age;
        mark(c,d,m,t,false);
        if(space(d)){panels(d,m,t,false);knives(d,m,t,false);}
        if(world(d,t))cuts(c,d,m,t,false);
    }
    @Override public void additive(Fx c,EffectMesh m){
        var d=dance(c);if(d==null)return;float t=c.age;
        mark(c,d,m,t,true);
        if(space(d)){spikes(d,m,t);raised(d,m,t);panels(d,m,t,true);knives(d,m,t,true);}
        if(world(d,t)){cuts(c,d,m,t,true);flight(c,d,m,t);}
    }
    @Override public void frame(Fx c,FxFrame f){
        var d=dance(c);if(d==null||!world(d,c.age))return;float t=c.age;
        if(!d.inSpace()||d.stopped()||t<VANISH||t>LAUNCH+12)return;
        var at=middle(c,d);
        float pulse=0;for(int k=0;k<HITS;k++){float s=t-blowAt(k);if(s>=0&&s<4)pulse=Math.max(pulse,1-s/4);}
        f.light(at,0xb050ff,7,.55f+.6f*pulse);
        // fast cuts: short afterglow, as the effects' other fast slashes
        if(t<LAUNCH+2)f.afterglow(.12f);
    }
    @Override public float shake(dev.sangwoo.celestialfall.client.StandClient.Cast cast,float sec){
        var d=XCharaClient.ofCast(cast);if(d==null||!d.inSpace()||d.stopped())return 0;
        float t=sec*20;if(space(d)||t<VANISH||t>LAUNCH+8)return 0;
        float v=0;
        for(int k=0;k<HITS;k++){float s=t-blowAt(k);if(s>=0&&s<3)v=Math.max(v,(k==HITS-1?1.6f:.45f)*(1-s/3));}
        float s=t-LAUNCH;if(s>=0&&s<8)v=Math.max(v,1.8f*(1-s/8));
        return v;
    }

    // ───────────────────────────── the "!!!" ─────────────────────────────

    /** How strongly the "!!!" shows at {@code t}: from MARK until the strange space (or a little past it until the server says it came), gone as it is blocked or called off. */
    public static float markShown(XCharaClient.Dance d,float t){
        if(t<MARK)return 0;
        if(d.ended()&&t>=d.ended)return 0;
        if(d.inSpace())return t<WARN?1:0;
        return t<WARN+6?1:0;
    }
    private static void mark(Fx c,XCharaClient.Dance d,EffectMesh m,float t,boolean light){
        var target=target(c);if(target==null)return;
        // blocked: a burst of white light on them instead
        if(d.blocked()&&t>=d.ended&&t<d.ended+6){
            if(!light)return;
            float s=(t-d.ended)/6;Vec3d at=target.getLerpedPos(c.delta).add(0,target.getHeight()*.6,0);
            m.softDisc(at,.8+1.6*s,0xd8f0ff,.85f*(1-s));
            dev.sangwoo.celestialfall.client.kageno.FxLib.ring(m,target.getLerpedPos(c.delta).add(0,.05,0),.6+2.2*s,.07,0xbfe6ff,.8f*(1-s));
            return;
        }
        float shown=markShown(d,t);if(shown<=0)return;
        float pop=.6f+.4f*XCharaSpace.smooth((t-MARK)/1.5f);
        Vec3d p=target.getLerpedPos(c.delta).add(0,target.getHeight()+1.15,0);
        Vec3d n=c.camera.subtract(p);if(n.lengthSquared()<1e-6)return;n=n.normalize();
        Vec3d right=n.crossProduct(new Vec3d(0,1,0));if(right.lengthSquared()<1e-4)right=new Vec3d(1,0,0);right=right.normalize();
        Vec3d up=right.crossProduct(n).normalize();
        double s=2.2*pop;
        boolean red=((int)Math.floor((t-MARK)/2))%2==0;
        if(light){
            // a soft glow round it, in its colour
            m.softDisc(p.add(up.multiply(.04*s)),.42*s,red?0xff3030:0xffffff,.35f*shown);
            return;
        }
        int color=red?RED:WHITE;
        for(int i=-1;i<=1;i++){
            Vec3d x=p.add(right.multiply(i*.15*s));
            // each mark: its bar and its dot, a dark outline round both
            rect(m,x.add(up.multiply(.07*s)),right,up,.0375*s+.02,.115*s+.02,INK,.95f*shown);
            rect(m,x.add(up.multiply(-.115*s)),right,up,.0375*s+.02,.0375*s+.02,INK,.95f*shown);
            rect(m,x.add(up.multiply(.07*s)),right,up,.0375*s,.115*s,color,shown);
            rect(m,x.add(up.multiply(-.115*s)),right,up,.0375*s,.0375*s,color,shown);
        }
    }
    private static void rect(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double hu,double hv,int rgb,float alpha){
        Vec3d a=u.multiply(hu),b=v.multiply(hv);
        m.quad(c.subtract(a).subtract(b),c.add(a).subtract(b),c.add(a).add(b),c.subtract(a).add(b),rgb,alpha);
    }

    // ───────────────────────────── the strange space ─────────────────────────────

    private static void spikes(XCharaClient.Dance d,EffectMesh m,float t){
        float shown=XCharaSpace.spikesShown(t);if(shown<=0)return;
        Vec3d f=d.f,l=d.l;
        for(double[] s:XCharaSpace.spikes(d.from,d.yaw)){
            Vec3d base=new Vec3d(s[0],s[1],s[2]),tip=base.add(0,s[3],0);
            for(Vec3d across:new Vec3d[]{f,l}){
                Vec3d a=base.add(across.multiply(s[4])),b=base.subtract(across.multiply(s[4]));
                m.vertex(a,GLOW,.5f*shown);m.vertex(b,GLOW,.5f*shown);m.vertex(tip,GLOW,0);m.vertex(tip,GLOW,0);
            }
            m.vertex(base.add(f.multiply(s[4]*.25)),0xb070ff,.35f*shown);m.vertex(base.subtract(f.multiply(s[4]*.25)),0xb070ff,.35f*shown);
            m.vertex(base.add(0,s[3]*.8,0),0xb070ff,0);m.vertex(base.add(0,s[3]*.8,0),0xb070ff,0);
        }
    }
    /** The knife raised upright, glowing white in the first shot (its user's right hand, the arm out ahead). */
    private static void raised(XCharaClient.Dance d,EffectMesh m,float t){
        float w=XCharaSpace.raise(t);if(w<=.01f||t>=SHOT_B)return;
        Vec3d hand=d.from.add(d.f.multiply(.62)).add(d.r.multiply(.36)).add(0,1.38,0);
        Vec3d a=hand.add(0,.3,0),b=hand.add(0,2.6,0);
        m.softLine(a,b,.55,0xcdb4ff,.45f*w);
        m.softLine(a,b,.16,WHITE,.8f*w);
    }
    private static void panels(XCharaClient.Dance d,EffectMesh m,float t,boolean light){
        if(t<SHOT_B||t>=SHOT_B+XCharaSpace.PANEL_LIFE)return;
        Vec3d f=d.f;Vec3d up=new Vec3d(0,1,0);
        for(int k=0;k<XCharaSpace.PANELS;k++){
            var p=XCharaSpace.panel(d.from,d.yaw,k,t);if(p.shown()<=0)continue;
            Vec3d u=f.multiply(Math.cos(p.turn())).add(up.multiply(Math.sin(p.turn()))),v=f.multiply(-Math.sin(p.turn())).add(up.multiply(Math.cos(p.turn())));
            if(light){
                rect(m,p.at(),u,v,p.halfWidth()+.12,p.halfHeight()+.12,PINK,.55f*p.shown());
                rect(m,p.at(),u,v,p.halfWidth()+.32,p.halfHeight()+.32,PINK,.14f*p.shown());
            }else rect(m,p.at(),u,v,p.halfWidth(),p.halfHeight(),0xfff4ff,.97f*p.shown());
        }
    }
    private static void knives(XCharaClient.Dance d,EffectMesh m,float t,boolean light){
        float shown=XCharaSpace.knives(t);if(shown<=0)return;
        for(int k=0;k<XCharaSpace.KNIVES;k++){
            var at=XCharaSpace.knife(d.from,d.yaw,k);double size=XCharaSpace.knifeSize(k)*XCharaSpace.KNIFE_LENGTH;
            if(light){
                knife(m,at[0],at[1],size*1.18,VIOLET,.5f*shown);
                knife(m,at[0],at[1],size*1.45,GLOW,.16f*shown);
                knife(m,at[0],at[1],size*.9,WHITE,.35f*shown);
            }else knife(m,at[0],at[1],size,0xf6f0ff,.97f*shown);
        }
    }
    /**
     * One knife's shape, flat, its middle at {@code at}, its point along {@code aim}, its spine up (X Chara's knife as the
     * model has it: the spine straight to the point, the edge level a fifth of the way and its belly curving up to the
     * point; the thin rod off the heel just under the spine). {@code length} from the rod's end to the point.
     */
    static void knife(EffectMesh m,Vec3d at,Vec3d aim,double length,int rgb,float alpha){
        if(alpha<=0)return;
        Vec3d up=new Vec3d(0,1,0).subtract(aim.multiply(aim.y));if(up.lengthSquared()<1e-6)up=new Vec3d(1,0,0);up=up.normalize();
        double blade=length*BLADE,rod=length-blade,depth=blade/ASPECT;
        Vec3d o=at.subtract(aim.multiply((blade-rod)*.5)).add(up.multiply(depth*.5));
        int n=12;
        for(int i=0;i<n;i++){
            double x0=blade*i/n,x1=blade*(i+1)/n;
            Vec3d s0=o.add(aim.multiply(x0)),s1=o.add(aim.multiply(x1));
            m.quad(s0,s1,s1.subtract(up.multiply(depth*depth(x1/blade))),s0.subtract(up.multiply(depth*depth(x0/blade))),rgb,alpha);
        }
        double y=depth*.1,h=Math.max(depth*.05,.025);
        Vec3d r0=o.subtract(aim.multiply(rod)).subtract(up.multiply(y)),r1=o.add(aim.multiply(.02)).subtract(up.multiply(y));
        m.quad(r0.add(up.multiply(h)),r1.add(up.multiply(h)),r1.subtract(up.multiply(h)),r0.subtract(up.multiply(h)),rgb,alpha);
    }
    /** The knife's blade's share of its length, and its length to its depth at the heel (tools/xchara/make_knife.py: the photo's 1345, 438 and 325 pixels). */
    static final double BLADE=1345/1783.0,ASPECT=1345/325.0;
    /** Its blade's depth {@code u} along it (0 the heel, 1 the point), of its depth at the heel (make_knife.py's depth_frac). */
    static double depth(double u){
        double w=Math.max(0,Math.min(1,(u-.20)/(1-.20))),blend=.16;
        double run=(w<blend?w*w/(2*blend):w-blend/2)/(1-blend/2);
        return Math.max(1-.36*run-(1-.36)*Math.pow(w,4.3),0);
    }

    // ───────────────────────────── the blows ─────────────────────────────

    private static LivingEntity target(Fx c){return c.targets.isEmpty()?null:c.targets.get(0);}
    /** The one marked's middle (where they stand: held there), or where they stood. */
    private static Vec3d middle(Fx c,XCharaClient.Dance d){
        var e=target(c);
        return e!=null?e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0):d.targetFrom.add(0,1,0);
    }
    private static void cuts(Fx c,XCharaClient.Dance d,EffectMesh m,float t,boolean light){
        if(!d.inSpace()||t<VANISH)return;
        // called off, the cuts stop with it (those already made fade)
        float stop=d.stopped()?d.ended:Float.MAX_VALUE;
        Vec3d mid=middle(c,d);int seed=(int)(d.cast.start()*31);
        if(light&&t<Math.min(LAUNCH+2,stop+4))m.softDisc(mid,3.4,0x8a3cff,.2f);
        for(int k=0;k<HITS;k++){
            float at=blowAt(k),s=t-at;
            if(s<0||s>=CUT_LIFE||at>stop)continue;
            cut(m,mid,k,seed,s,light,k==HITS-1);
        }
    }
    /** Blow {@code k}'s cut, {@code s} ticks after it: a violet crescent about them sweeping round, its white edge, streaks, ink spikes and a flash. */
    static void cut(EffectMesh m,Vec3d mid,int k,int seed,float s,boolean light,boolean last){
        double h1=XCharaSpace.hash(k,seed+1),h2=XCharaSpace.hash(k,seed+2);
        // its plane: any way about them
        double z=2*h1-1,a=h2*Math.PI*2,q=Math.sqrt(1-z*z);
        Vec3d n=new Vec3d(q*Math.cos(a),z,q*Math.sin(a));
        Vec3d u=n.crossProduct(Math.abs(n.y)<.9?new Vec3d(0,1,0):new Vec3d(1,0,0)).normalize(),v=n.crossProduct(u).normalize();
        Vec3d o=mid.add(u.multiply(.25*(XCharaSpace.hash(k,seed+3)-.5))).add(v.multiply(.25*(XCharaSpace.hash(k,seed+4)-.5)));
        double scale=last?1.45:1;
        double r=(3.0+1.8*XCharaSpace.hash(k,seed+5))*scale,w=(1.0+.8*XCharaSpace.hash(k,seed+6))*scale;
        double start=XCharaSpace.hash(k,seed+7)*Math.PI*2,span=Math.toRadians(150+90*XCharaSpace.hash(k,seed+8));
        float life=(float)Math.pow(Math.max(0,1-s/CUT_LIFE),1.3);
        // swept round in a moment, thinning as it goes
        double head=XCharaSpace.smooth(s/1.6f),thin=1-.55*XCharaSpace.smooth(s/CUT_LIFE);
        int segs=18;
        for(int i=0;i<segs;i++){
            double p0=(double)i/segs,p1=(double)(i+1)/segs;
            if(p0>head)break;p1=Math.min(p1,head);
            double t0=start+span*p0,t1=start+span*p1;
            double w0=w*thin*Math.pow(Math.sin(Math.PI*p0),.8),w1=w*thin*Math.pow(Math.sin(Math.PI*p1),.8);
            Vec3d d0=u.multiply(Math.cos(t0)).add(v.multiply(Math.sin(t0))),d1=u.multiply(Math.cos(t1)).add(v.multiply(Math.sin(t1)));
            Vec3d out0=o.add(d0.multiply(r)),out1=o.add(d1.multiply(r)),in0=o.add(d0.multiply(r-w0)),in1=o.add(d1.multiply(r-w1));
            if(light){
                m.line(out0,out1,.12*scale,0xffc8ff,.95f*life);
                m.line(out0,out1,.4*scale,PINK,.3f*life);
                m.quad(in0,in1,out1,out0,0xa040ff,.3f*life);
            }else m.quad(in0,in1,out1,out0,0x7a1ed8,.6f*life);
        }
        if(light){
            // white streaks through them (quick), and the flash
            if(s<4)for(int j=0;j<3;j++){
                double b=XCharaSpace.hash(k*2+j,seed+9)*Math.PI*2,e=(XCharaSpace.hash(k*2+j,seed+10)-.5)*1.6;
                Vec3d dir=new Vec3d(Math.cos(b)*Math.cos(e),Math.sin(e),Math.sin(b)*Math.cos(e));
                double len=(3.5+2.5*XCharaSpace.hash(k*2+j,seed+11))*scale;
                m.needle(o.subtract(dir.multiply(len)),o.add(dir.multiply(len)),.14*scale,.5,WHITE,.95f*(1-s/4));
            }
            if(s<2.5f)m.softDisc(o,(1.1+.6*s)*scale,WHITE,.75f*(1-s/2.5f));
        }else if(s<6){
            for(int j=0;j<3;j++){
                double b=XCharaSpace.hash(k*3+j,seed+12)*Math.PI*2,e=(XCharaSpace.hash(k*3+j,seed+13)-.5)*1.4;
                Vec3d dir=new Vec3d(Math.cos(b)*Math.cos(e),Math.sin(e),Math.sin(b)*Math.cos(e));
                double len=(1.8+2.0*XCharaSpace.hash(k*3+j,seed+14))*scale;
                m.needle(o.add(dir.multiply(.4)),o.add(dir.multiply(.4+len)),.3*scale,.25,INK,.85f*(1-s/6));
            }
        }
    }
    /** Sent flying: a burst where they stood and a violet trail after them as they go. */
    private static void flight(Fx c,XCharaClient.Dance d,EffectMesh m,float t){
        if(!d.inSpace()||d.stopped()||d.how!=dev.sangwoo.celestialfall.xchara.XCharaUltimate.END&&t>=END)return;
        float s=t-LAUNCH;if(s<0||s>18)return;
        var e=target(c);if(e==null||!e.isAlive())return;
        Vec3d at=e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);
        if(s<5){m.softDisc(d.targetFrom.add(0,1,0),2.2+1.5*s/5,WHITE,.85f*(1-s/5));}
        if(s<9)dev.sangwoo.celestialfall.client.kageno.FxLib.ring(m,d.targetFrom.add(0,.06,0),1+4.5*s/9,.12,0xc070ff,.8f*(1-s/9));
        Vec3d way=d.f.multiply(FLY).add(0,FLY_UP,0).normalize();
        float fade=1-s/18;
        m.glowLine(at.subtract(way.multiply(Math.min(6,.6+s*.8))),at,.22,0xb050ff,.8f*fade);
    }
}
