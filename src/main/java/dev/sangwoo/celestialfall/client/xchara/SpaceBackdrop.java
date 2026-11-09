package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;

/**
 * The strange space's depths (2.112.1, user request: "이상한 공간 배경도 멋있게 꾸며줘"; 2.112.0 had only its dark ground,
 * the near spikes and the panels), all light on its dark, behind and under the two and never as bright as they, the
 * knives or the panels: a violet glow rising out of an abyss far under them; the violet spikes (the video's), deep under
 * the frame and soft at their edges, a brighter line up each; giant spikes far behind those, bluer for the distance;
 * clouds of violet, magenta and indigo light behind them, slowly turning; a field of stars past those, twinkling; a great
 * faint ring behind the two with its ticks turning slowly, a clock's face (the dance's stage); embers rising through the
 * space about them; now and then a streak falling far behind.
 * <p>
 * Where everything is follows from where its user stood and the way they faced (as XCharaSpace's: the side cameras look
 * from their left, so "behind" is away from those), and every moment of it is a function of its clock, the same on every
 * client; only its two see it (XCharaClient.cinematic).
 */
final class SpaceBackdrop {
    private SpaceBackdrop(){}

    /** Draws it at {@code t} (its light). */
    static void draw(XCharaClient.Dance d,EffectMesh m,float t){
        float shown=XCharaSpace.spikesShown(t);if(shown<=0)return;
        // it comes up with the strange space in a few frames, and goes as the world is back
        shown*=Math.min(1,(t-WARN+1)/3f);
        Vec3d f=d.f,l=d.l,up=new Vec3d(0,1,0);
        abyss(m,d.from,d.yaw,shown);
        for(var c:clouds(d.from,d.yaw))cloud(m,new Vec3d(c[0],c[1],c[2]),f,up,c[3],CLOUD_RGB[(int)c[4]],CLOUD_ALPHA*shown,c[5],t);
        for(var s:stars(d.from,d.yaw)){
            float a=(float)(s[4]*(.6+.4*Math.sin(t*s[5]+s[6])))*shown;
            Vec3d p=new Vec3d(s[0],s[1],s[2]);int rgb=STAR_RGB[(int)(s[6]*7)%STAR_RGB.length];
            // a sharp point of light, a soft halo round the brighter ones and a cross on the brightest
            m.softDisc(p,s[3]*.7,rgb,.95f*a);
            if(s[4]>.6)m.softDisc(p,s[3]*1.8,rgb,.18f*a);
            if(s[4]>.9)m.star(p,s[3]*3.2,rgb,.8f*a);
        }
        ring(m,d.from,d.yaw,f,up,t,shown);
        for(var s:farSpikes(d.from,d.yaw))spike(m,s,f,l,.13f*shown,0x3a28b8,0x6a52f0,false,0);
        int n=0;
        for(var s:XCharaSpace.spikes(d.from,d.yaw)){
            float glint=(float)(.5+.5*Math.sin(t*.13+n*1.7));
            spike(m,s,f,l,.3f*shown,0x6a24e8,0xb27cff,n%2==0,n%3==0?glint:0);n++;
        }
        for(int i=0;i<EMBERS;i++){
            var e=ember(d.from,d.yaw,i,t);if(e[3]<=0)continue;
            Vec3d p=new Vec3d(e[0],e[1],e[2]);int rgb=EMBER_RGB[i%EMBER_RGB.length];
            m.softDisc(p,e[4]*4,rgb,.32f*(float)e[3]*shown);
            m.softDisc(p,e[4]*1.3,0xfff0ff,.85f*(float)e[3]*shown);
        }
        streaks(m,d.from,d.yaw,t,shown);
    }

    // ───────────────────────────── the abyss ─────────────────────────────

    /** The abyss's glow: how far under the two's middles, how far behind them from the side cameras, how wide. */
    static final double ABYSS_DOWN=30,ABYSS_BACK=15,ABYSS_R=140;
    /** Its middle. */
    static Vec3d abyssMiddle(Vec3d from,float yaw){
        return XCharaSpace.middle(from,yaw).subtract(XCharaSpace.left(XCharaSpace.f(yaw)).multiply(ABYSS_BACK)).subtract(0,ABYSS_DOWN,0);
    }
    /** Its rings out from its middle: how far, how bright, what colour (violet at its heart, deep indigo at its rim). */
    static final double[] ABYSS_RINGS={0,.18,.43,1};
    static final float[] ABYSS_ALPHA={.34f,.22f,.09f,0};
    static final int[] ABYSS_RGB={0x8a30ff,0x6a26e0,0x3a1ab0,0x1a0c60};
    private static void abyss(EffectMesh m,Vec3d from,float yaw,float shown){
        Vec3d c=abyssMiddle(from,yaw);int n=40;
        for(int ring=0;ring<ABYSS_RINGS.length-1;ring++){
            double r0=ABYSS_R*ABYSS_RINGS[ring],r1=ABYSS_R*ABYSS_RINGS[ring+1];
            for(int i=0;i<n;i++){
                double a0=2*Math.PI*i/n,a1=2*Math.PI*(i+1)/n;
                m.vertex(c.add(Math.cos(a0)*r0,0,Math.sin(a0)*r0),ABYSS_RGB[ring],ABYSS_ALPHA[ring]*shown);
                m.vertex(c.add(Math.cos(a1)*r0,0,Math.sin(a1)*r0),ABYSS_RGB[ring],ABYSS_ALPHA[ring]*shown);
                m.vertex(c.add(Math.cos(a1)*r1,0,Math.sin(a1)*r1),ABYSS_RGB[ring+1],ABYSS_ALPHA[ring+1]*shown);
                m.vertex(c.add(Math.cos(a0)*r1,0,Math.sin(a0)*r1),ABYSS_RGB[ring+1],ABYSS_ALPHA[ring+1]*shown);
            }
        }
    }

    // ───────────────────────────── the spikes ─────────────────────────────

    /** The giant spikes far behind the near ones: {x, base y, z, height, half its width}, across the whole frame. */
    static final int FAR_SPIKES=18;
    static double[][] farSpikes(Vec3d from,float yaw){
        Vec3d f=XCharaSpace.f(yaw),l=XCharaSpace.left(f),mid=XCharaSpace.middle(from,yaw);
        double[][] out=new double[FAR_SPIKES][];
        for(int i=0;i<FAR_SPIKES;i++){
            double along=-95+190*(i+.2+.6*XCharaSpace.hash(i,71))/FAR_SPIKES,back=38+32*XCharaSpace.hash(i,72);
            Vec3d p=mid.add(f.multiply(along)).subtract(l.multiply(back));
            double base=from.y-50,tip=from.y-8+38*XCharaSpace.hash(i,73);
            out[i]=new double[]{p.x,base,p.z,tip-base,1.6+2.2*XCharaSpace.hash(i,74)};
        }
        return out;
    }
    /**
     * One spike ({x, base y, z, height, half width}): two crossed planes rising to a point, brightest up its middle and
     * soft at its edges, coming out of the dark from nothing at its base (its base shows in the frame from far off),
     * brightest a third of the way up and fading to its point; a brighter line up its middle on some ({@code lined}); a
     * soft glint at its point ({@code glint} 0 for none).
     */
    static void spike(EffectMesh m,double[] s,Vec3d f,Vec3d l,float alpha,int edge,int core,boolean lined,float glint){
        Vec3d base=new Vec3d(s[0],s[1],s[2]),tip=base.add(0,s[3],0);
        for(Vec3d across:new Vec3d[]{f,l}){
            for(int i=0;i<SPIKE_AT.length-1;i++){
                Vec3d c0=base.add(0,s[3]*SPIKE_AT[i],0),c1=base.add(0,s[3]*SPIKE_AT[i+1],0);
                Vec3d w0=across.multiply(s[4]*SPIKE_WIDE[i]),w1=across.multiply(s[4]*SPIKE_WIDE[i+1]);
                float a0=alpha*SPIKE_ALPHA[i],a1=alpha*SPIKE_ALPHA[i+1];
                for(int side=-1;side<=1;side+=2){
                    m.vertex(c0,core,a0);m.vertex(c1,core,a1);m.vertex(c1.add(w1.multiply(side)),edge,a1*.2f);m.vertex(c0.add(w0.multiply(side)),edge,a0*.2f);
                }
            }
        }
        if(lined)m.softLine(base.add(0,s[3]*.28,0),base.add(0,s[3]*.88,0),Math.min(.5,s[4]*.4),0xc8a0ff,alpha*.35f);
        if(glint>0)m.softDisc(tip.subtract(0,s[3]*.04,0),.55,0xe0c0ff,alpha*.6f*glint);
    }
    /** A spike's stops up it (of its height): how wide it is there (of its half width) and how bright (of its brightest). */
    static final double[] SPIKE_AT={0,.32,.68,1},SPIKE_WIDE={1,.82,.45,0};
    static final float[] SPIKE_ALPHA={0,1,.6f,0};

    // ───────────────────────────── the clouds and the stars ─────────────────────────────

    /** The clouds of light behind everything: {x, y, z, radius, colour, phase}. */
    static final int CLOUDS=8;
    static final int[] CLOUD_RGB={0x6a1cc8,0xa020b0,0x2a2cb8};
    static final float CLOUD_ALPHA=.09f;
    static double[][] clouds(Vec3d from,float yaw){
        Vec3d f=XCharaSpace.f(yaw),l=XCharaSpace.left(f),mid=XCharaSpace.middle(from,yaw);
        double[][] out=new double[CLOUDS][];
        for(int i=0;i<CLOUDS;i++){
            double along=-80+160*(i+XCharaSpace.hash(i,81))/CLOUDS,back=62+18*XCharaSpace.hash(i,82),up=-16+40*XCharaSpace.hash(i,83);
            Vec3d p=mid.add(f.multiply(along)).subtract(l.multiply(back)).add(0,up,0);
            out[i]=new double[]{p.x,p.y,p.z,17+14*XCharaSpace.hash(i,84),(int)(3*XCharaSpace.hash(i,85)),2*Math.PI*XCharaSpace.hash(i,86)};
        }
        return out;
    }
    /** A cloud of light: soft from its heart out, its rim slowly changing shape. */
    private static void cloud(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double r,int rgb,float alpha,double phase,float t){
        int n=28;
        for(int i=0;i<n;i++){
            double a0=2*Math.PI*i/n,a1=2*Math.PI*(i+1)/n,r0=r*cloudRim(a0,phase,t),r1=r*cloudRim(a1,phase,t);
            Vec3d o0=c.add(u.multiply(Math.cos(a0)*r0)).add(v.multiply(Math.sin(a0)*r0)),o1=c.add(u.multiply(Math.cos(a1)*r1)).add(v.multiply(Math.sin(a1)*r1));
            Vec3d h0=c.add(o0.subtract(c).multiply(.5)),h1=c.add(o1.subtract(c).multiply(.5));
            m.vertex(c,rgb,alpha);m.vertex(c,rgb,alpha);m.vertex(h1,rgb,alpha*.55f);m.vertex(h0,rgb,alpha*.55f);
            m.vertex(h0,rgb,alpha*.55f);m.vertex(h1,rgb,alpha*.55f);m.vertex(o1,rgb,0);m.vertex(o0,rgb,0);
        }
    }
    static double cloudRim(double a,double phase,float t){return 1+.22*Math.sin(3*a+phase+t*.012)+.12*Math.sin(5*a+2*phase-t*.008);}

    /** The stars past the clouds: {x, y, z, size, brightness, twinkle's speed, phase}. */
    static final int STARS=170;
    static final int[] STAR_RGB={0xffffff,0xe6d6ff,0xd0d8ff,0xffe0f6};
    static double[][] stars(Vec3d from,float yaw){
        Vec3d f=XCharaSpace.f(yaw),l=XCharaSpace.left(f),mid=XCharaSpace.middle(from,yaw);
        double[][] out=new double[STARS][];
        for(int i=0;i<STARS;i++){
            double along=-140+280*XCharaSpace.hash(i,91),back=88+20*XCharaSpace.hash(i,92),up=-50+130*XCharaSpace.hash(i,93);
            Vec3d p=mid.add(f.multiply(along)).subtract(l.multiply(back)).add(0,up,0);
            double b=XCharaSpace.hash(i,95);
            out[i]=new double[]{p.x,p.y,p.z,.35+.65*Math.pow(XCharaSpace.hash(i,94),3),.35+.65*b,.08+.12*XCharaSpace.hash(i,96),2*Math.PI*XCharaSpace.hash(i,97)};
        }
        return out;
    }

    // ───────────────────────────── the ring ─────────────────────────────

    /** The great faint ring behind the two: how far behind their middle, how wide, how fast its ticks turn (radians a tick). */
    static final double RING_BACK=34,RING_R=23,RING_TURN=.003;
    static Vec3d ringMiddle(Vec3d from,float yaw){
        return XCharaSpace.middle(from,yaw).subtract(XCharaSpace.left(XCharaSpace.f(yaw)).multiply(RING_BACK)).add(0,1,0);
    }
    private static void ring(EffectMesh m,Vec3d from,float yaw,Vec3d u,Vec3d v,float t,float shown){
        Vec3d c=ringMiddle(from,yaw);int rgb=0xa070ff;
        // drawn round as it comes (in its first second), then still
        float drawn=XCharaSpace.smooth((t-WARN)/20f);
        double spin=RING_TURN*(t-WARN);
        circle(m,c,u,v,RING_R,.32,rgb,.16f*shown,drawn,spin);
        circle(m,c,u,v,RING_R-1.6,.18,rgb,.1f*shown,drawn,-spin*.7);
        for(int i=0;i<48;i++){
            double a=spin+2*Math.PI*i/48;if(i/48.0>drawn)break;
            double in=RING_R-(i%4==0?1.9:1.3),out=RING_R-(i%4==0?.25:.55);
            Vec3d dir=u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a)));
            m.line(c.add(dir.multiply(in)),c.add(dir.multiply(out)),i%4==0?.22:.14,rgb,(i%4==0?.17f:.11f)*shown);
        }
        // four small diamonds on it, turning the other way
        for(int i=0;i<4;i++){
            double a=-spin*1.5+Math.PI/2*i;Vec3d dir=u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a))),side=u.multiply(-Math.sin(a)).add(v.multiply(Math.cos(a)));
            Vec3d p=c.add(dir.multiply(RING_R));
            m.quad(p.add(dir.multiply(1.1)),p.add(side.multiply(.55)),p.subtract(dir.multiply(1.1)),p.subtract(side.multiply(.55)),0xc8a8ff,.22f*shown*drawn);
        }
    }
    private static void circle(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double r,double width,int rgb,float alpha,float drawn,double from){
        int n=96;
        for(int i=0;i<n*drawn;i++){
            double a0=from+2*Math.PI*i/n,a1=from+2*Math.PI*Math.min(i+1,n*drawn)/n;
            m.line(c.add(u.multiply(Math.cos(a0)*r)).add(v.multiply(Math.sin(a0)*r)),c.add(u.multiply(Math.cos(a1)*r)).add(v.multiply(Math.sin(a1)*r)),width,rgb,alpha);
        }
    }

    // ───────────────────────────── the embers and the streaks ─────────────────────────────

    /** The embers rising through the space about the two. */
    static final int EMBERS=64;
    static final int[] EMBER_RGB={0xff6af0,0xc890ff,0x9a6cff,0xffb0f0};
    /** Ember {@code i} at {@code t}: {x, y, z, how strongly it shows (0 to 1), its size}: rising, swaying, faded near the top and bottom of its run. */
    static double[] ember(Vec3d from,float yaw,int i,float t){
        Vec3d f=XCharaSpace.f(yaw),l=XCharaSpace.left(f),mid=XCharaSpace.middle(from,yaw);
        double along=-(XCharaSpace.GAP/2+10)+(XCharaSpace.GAP+20)*XCharaSpace.hash(i,101),across=-20+25*XCharaSpace.hash(i,102);
        double rise=.025+.05*XCharaSpace.hash(i,103),y0=26*XCharaSpace.hash(i,104),run=26;
        double y=((y0+rise*(t-WARN))%run+run)%run-13;
        along+=.6*Math.sin(.06*t+2*Math.PI*XCharaSpace.hash(i,105));
        Vec3d p=mid.add(f.multiply(along)).add(l.multiply(across)).add(0,y,0);
        double shown=Math.min(1,Math.min((y+13)/3,(13-y)/3))*(.65+.35*Math.sin(.15*t+7*XCharaSpace.hash(i,106)));
        return new double[]{p.x,p.y,p.z,Math.max(0,shown),.06+.1*XCharaSpace.hash(i,107)};
    }
    /** A streak falls far behind every so often (ticks), each showing so long. */
    static final int STREAK_EVERY=13,STREAK_LIFE=8;
    /** Streak {@code i}'s start {x, y, z} and its way {x, y, z} (blocks a tick). */
    static double[] streak(Vec3d from,float yaw,int i){
        Vec3d f=XCharaSpace.f(yaw),l=XCharaSpace.left(f),mid=XCharaSpace.middle(from,yaw);
        Vec3d start=mid.add(f.multiply(-50+100*XCharaSpace.hash(i,111))).subtract(l.multiply(48+12*XCharaSpace.hash(i,112))).add(0,18+16*XCharaSpace.hash(i,113),0);
        double fall=Math.toRadians(25+15*XCharaSpace.hash(i,114)),way=XCharaSpace.hash(i,115)<.5?1:-1;
        Vec3d dir=f.multiply(way*Math.cos(fall)).add(0,-Math.sin(fall),0).multiply(4.5);
        return new double[]{start.x,start.y,start.z,dir.x,dir.y,dir.z};
    }
    private static void streaks(EffectMesh m,Vec3d from,float yaw,float t,float shown){
        float since=t-WARN;if(since<0)return;
        int last=(int)Math.floor(since/STREAK_EVERY);
        for(int i=Math.max(0,last-1);i<=last;i++){
            float s=since-i*STREAK_EVERY;if(s<0||s>=STREAK_LIFE)continue;
            double[] k=streak(from,yaw,i);
            Vec3d start=new Vec3d(k[0],k[1],k[2]),dir=new Vec3d(k[3],k[4],k[5]);
            Vec3d head=start.add(dir.multiply(s)),tail=head.subtract(dir.multiply(Math.min(s,2.6)));
            float a=(1-s/STREAK_LIFE)*shown;
            m.softLine(tail,head,.7,0xc070ff,.5f*a);
            m.softDisc(head,.9,0xf0d8ff,.6f*a);
        }
    }
}
