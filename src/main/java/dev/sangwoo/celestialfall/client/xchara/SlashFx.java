package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * What X Chara's knife draws on a swing begun with their hack on (2.118.0, user request: "Q활성화 평타 이펙트를 전체적으로
 * 싹 다 갈아엎고 평타 소리에 맞춰서 새로 만들어줘 보라색 느낌으로 이펙트는 원래꺼보다는 약하고 적당하게"; the swings
 * themselves are the plain four, KnifeCombo, the user's choice). Made to the attack's one sound (the user's: a hiss as
 * the knife cuts and a tail falling in pitch after it, half a second; it starts KnifeClient.SOUND_BEFORE ticks before
 * the blow), mine to make up but for what the user's video of the attack shows on whom a hit strikes (a white star of
 * thin rays, a purple swirl round them, sharp purple spikes):
 * <ul>
 * <li>The cut: a crisp slash of X Chara's purple along the knife's own path (its outer part), drawn as the knife is let
 * go (as the hiss begins) and whole as the blow lands; then, as the sound's tail falls, it cools (narrows to its
 * cutting edge, its white core goes, its purple deepens) and is eaten from its tail, gone as the sound ends.</li>
 * <li>The lunge's: a lance of the same purple along the knife's line, out past its point as it lands, and one thin ring
 * round the line where it lands.</li>
 * <li>On whom a blow strikes: a white star and a few thin white rays for a moment, a purple swirl (two crescents turning
 * round them, slowing and closing in as the tail falls), a few sharp purple spikes shot out and gone, a few sparks.
 * The lunge's a third bigger.</li>
 * </ul>
 * Smaller and fewer than 2.114.0's (the user: weaker than those, moderate): nothing covers the body or whom it strikes,
 * no afterimages, no screen effects. The purple is laid over what is behind it (the effects' blended pass), so it stays
 * purple before a bright sky; white only in thin cores, the star and its rays. Every part is a function of the swing's
 * clock, its place in the combo and a seed, the same on every client.
 */
final class SlashFx {
    private SlashFx(){}
    /** X Chara's purple (as their sword dance's, DanceBurst): the ink of outlines, the deep and the vivid purple, the glow, sparks; lilac and white. */
    static final int INK=DanceBurst.INK,DEEP=DanceBurst.DEEP,PURPLE=DanceBurst.PURPLE,GLOW=DanceBurst.GLOW,SPARK=DanceBurst.SPARK,LILAC=0xe2b8ff,WHITE=0xffffff;

    // ───────────────────────────── the cut ─────────────────────────────

    /**
     * When each swing's cut begins and when the knife has gone through (seconds into the hit, by its place in the combo):
     * from a moment after the knife is let go, once it has come round from behind them (a cut begun as it is let go lay
     * far out behind their back and, the chop's, high over their head: too much of it).
     */
    static final float[] FROM={0,.14f,.135f,.25f,.31f},TO={0,.27f,.27f,.375f,.44f};
    /** The cut lies this far along the knife from the fist (blocks): its outer part (the knife's point at 2.63). */
    static final double INNER=1.75,OUTER=2.7;
    /** How wide the cut's edge shows to the camera (blocks: a band seen edge-on is a hairline, KnifeCombo.edge). */
    static final double EDGE=.36;
    /**
     * After the blow (seconds): the cut cools from COOL0 to COOL1 and is eaten from its tail from EAT0 to EAT1, as the
     * sound's tail falls (it starts falling under the hiss as the blow lands and is gone 0.44 s after it).
     */
    static final float COOL0=.07f,COOL1=.38f,EAT0=.12f,EAT1=.42f;
    /** The sound: how long it is, and how long before the blow it starts (seconds: KnifeClient.SOUND_BEFORE ticks). */
    static final float SOUND=.54f,SOUND_BEFORE=KnifeClient.SOUND_BEFORE/20f;
    /** The second into hit {@code hit} its blow lands on. */
    static float blow(int hit){return blowAt(hit,0)/20f;}
    /** The moment the cut's life is counted from: its blow, or where the swing stopped short of it. */
    static float mark(int hit,float until){return Math.min(blow(hit),until);}
    /** How far the cut has cooled {@code t} seconds into hit {@code hit} (0 as it is cut, 1 cooled). */
    static float cool(int hit,float t,float until){float b=mark(hit,until);return Ease.smooth(t,b+COOL0,b+COOL1);}
    /** The share of the cut eaten from its tail by then. */
    static float eaten(int hit,float t,float until){float b=mark(hit,until);return Ease.smooth(t,b+EAT0,b+EAT1);}
    /** Whether anything of the cut shows at {@code t}. */
    static boolean cutting(int hit,float t,float until){int p=place(hit);return p>0&&t>=FROM[p]&&t<mark(hit,until)+EAT1&&FROM[p]<until;}

    /** The lance (the lunge's cut): from this far along the knife as it is let go, to this far past its point as it lands (blocks), this wide. */
    static final double LANCE_FROM=1.2,LANCE_PAST=.7,LANCE_WIDE=.5;
    /** How far past the knife's point the lance reaches {@code t} seconds into the lunge: out as it lands. */
    static double lancePast(float t){float b=blow(HACK_LUNGE);return LANCE_PAST*Ease.smooth(t,b-.04f,b+.03f);}
    /** The lance's two ends {@code t} seconds into the lunge: where the knife was as it was let go, and its point now and on past it. */
    static Vec3d[] lance(Vec3d feet,float yaw,int hit,float t){
        var was=KnifeCombo.blade(feet,yaw,hit,FROM[4]);var now=KnifeCombo.blade(feet,yaw,hit,Math.min(t,TO[4]));
        return new Vec3d[]{was[0].add(was[1].multiply(LANCE_FROM)),now[0].add(now[1].multiply(KnifeCombo.TIP+lancePast(t)))};
    }
    /** A straight band from {@code a} to {@code b}, {@code wide} across, turned to the camera. */
    static KnifeCombo.Swept strip(Vec3d a,Vec3d b,double wide,Vec3d camera){
        int n=13;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        Vec3d d=b.subtract(a),side=d.crossProduct(camera.subtract(a.add(d.multiply(.5))));
        side=side.lengthSquared()<1e-10?new Vec3d(0,1,0):side.normalize();side=side.multiply(wide*.5);
        for(int i=0;i<n;i++){
            Vec3d p=a.add(d.multiply(i/(double)(n-1)));
            in[i]=p.subtract(side);out[i]=p.add(side);along[i]=i/(float)(n-1);
        }
        return new KnifeCombo.Swept(in,out,along,n);
    }

    /** The swing's cut (the effects' pass for X Chara's cuts: the shader's mode 18). */
    static void cut(Fx c){
        int hit=c.cast.combo(),place=place(hit);float t=c.sec,until=KnifeClient.until(c.cast.id());
        if(!cutting(hit,t,until))return;
        // (stopped short, the cut ends where the knife was)
        float from=FROM[place],head=Math.min(Math.min(t,TO[place]),until);if(head<=from+.004f)return;
        float eaten=eaten(hit,t,until),cool=cool(hit,t,until),shown=KnifeCombo.own(c)*Ease.smooth(t,from,from+.015f);
        Vec3d feet=KnifeCombo.spot(c,0);
        if(place==4){
            var ends=lance(feet,c.cast.yaw(),hit,head);
            var sw=strip(ends[0],ends[1],LANCE_WIDE,c.camera);
            KagenoFire.glitchRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),1,place,eaten,cool,shown);
            return;
        }
        var sw=KnifeCombo.swept(feet,c.cast.yaw(),hit,from,head,INNER,OUTER);
        KagenoFire.glitchRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),0,place,eaten,cool,shown);
        // its edge turned to the camera: a slash as thick from anywhere (from right behind, the band itself is edge-on)
        var e=KnifeCombo.edge(sw,c.camera,EDGE);
        KagenoFire.glitchRibbon(e.inner(),e.outer(),e.along(),e.n(),0,place+4,eaten,cool,shown);
    }

    // ───────────────────────────── the lunge's ring ─────────────────────────────

    /** The ring round the lunge's line where it lands: how long it shows (seconds), its radius {@code s} seconds after the blow and how strongly it shows. */
    static final float RING_LIFE=.2f;
    static double ringRadius(float s){return .14+.62*Ease.smooth(s,0,RING_LIFE*.9f);}
    static float ringShown(float s){return s<0||s>=RING_LIFE?0:in(s)*(1-Ease.smooth(s,.06f,RING_LIFE));}

    // ───────────────────────────── on whom a blow strikes ─────────────────────────────

    /** How long each part shows after the blow (seconds): the white star and its rays, the swirl, the spikes, the sparks. */
    static final float STAR_LIFE=.14f,RAY_LIFE=.16f,SWIRL_LIFE=.4f,SPIKE_LIFE=.24f,SPARK_LIFE=.32f;
    /** How many times round the swirl turns in its life. */
    static final double TURNS=1.0;
    /** How many rays, spikes (the lunge's two more) and sparks. */
    static final int RAYS=7,SPIKES=4,SPIKES_LAST=6,SPARKS=6;
    /** The lunge's burst, so much bigger. */
    static final double LAST=1.3;
    /** How far toward the camera from their body's middle the burst lies (blocks: in front of their body, not in it). */
    static final double FRONT=.45;
    static double big(int hit){return last(hit)?LAST:1;}
    /** How far in anything is {@code s} seconds after it started: over a frame or two at 60 a second, not at once. */
    static float in(float s){return s<=0?0:Math.min(1,s/.02f);}
    static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}
    /** Where the burst lies for a camera at {@code camera}: their body's {@code mid}dle, toward the camera. */
    static Vec3d burstAt(Vec3d mid,Vec3d camera){
        Vec3d to=camera.subtract(mid);double far=to.length();
        return far<1e-6?mid:mid.add(to.multiply(Math.min(FRONT,far*.5)/far));
    }
    /** Ray {@code j}: the angle it points at round the camera's view (radians) and how long it is (blocks). */
    static double[] ray(int j,int seed,double big){
        return new double[]{(j+.15+.7*h(j,seed,1))*Math.PI*2/RAYS,(.5+.55*h(j,seed,2))*big};
    }
    /**
     * The swirl {@code s} seconds after the blow: its radius (closing in), how far round it has turned (radians: fast
     * at first, slowing as the sound's tail falls), how far round each of its two crescents reaches, how thick they are
     * and how strongly it shows.
     */
    static double[] swirl(float s,int seed,double big){
        float u=Math.max(0,Math.min(1,s/SWIRL_LIFE));
        double radius=big*(.62-.16*Ease.smooth(u,0,1));
        double turned=h(0,seed,3)*Math.PI*2+(h(0,seed,4)<.5?-1:1)*Math.PI*2*TURNS*2*(u-.5*u*u);
        double span=Math.toRadians(150-70*u),thick=big*.15*(1-.45*u);
        double shown=in(s)*(1-Ease.smooth(s,.14f,SWIRL_LIFE));
        return new double[]{radius,turned,span,thick,shown};
    }
    /** A spike of the burst at a moment: from {@code a} out to {@code b}, how wide, how strongly it shows. */
    record Spike(Vec3d a,Vec3d b,double width,float fade){}
    /** Spike {@code j} of the burst at {@code at} for a camera whose right and up are {@code f}, {@code s} seconds after the blow (null while it does not show): shot out at once, then gone. */
    static Spike spike(Vec3d at,Vec3d[] f,int j,int n,int seed,float s,double big){
        if(s<0||s>=SPIKE_LIFE)return null;
        double a=(j+.2+.6*h(j,seed,5))*Math.PI*2/n+h(0,seed,6)*Math.PI*2,tilt=(h(j,seed,7)-.5)*.5;
        Vec3d dir=f[0].multiply(Math.cos(a)).add(f[1].multiply(Math.sin(a))).add(f[2].multiply(tilt)).normalize();
        double len=(.85+.6*h(j,seed,8))*big*Ease.smooth(s,0,.05f);
        float fade=in(s)*(1-Ease.smooth(s,.1f,SPIKE_LIFE));
        return new Spike(at.add(dir.multiply(.22*big)),at.add(dir.multiply(.22*big+len)),(.1+.05*h(j,seed,9))*big,fade);
    }
    /** Spark {@code j}: where it is {@code s} seconds after the blow, the way it is going (blocks a second) and how strongly it shows (null while it does not show). */
    static Vec3d[] spark(Vec3d at,Vec3d way,int j,int seed,float s,double big){
        float life=(float)(SPARK_LIFE*(.6+.4*h(j,seed,10)));if(s<0||s>=life)return null;
        Vec3d any=new Vec3d(h(j,seed,11)-.5,h(j,seed,12)-.2,h(j,seed,13)-.5).normalize();
        Vec3d dir=way.multiply(.45).add(any).normalize();
        double v=(3.5+3.5*h(j,seed,14))*big,drag=6,k=Math.exp(-drag*s);
        Vec3d p=at.add(dir.multiply(v*(1-k)/drag)).add(0,-3.2*s*s,0),vel=dir.multiply(v*k).add(0,-6.4*s,0);
        return new Vec3d[]{p,vel,new Vec3d(1-s/life,0,0)};
    }

    /** The burst on one struck at {@code mid}: its purple laid over what is behind it (the blended pass), or its light (added). */
    static void burst(EffectMesh m,Vec3d mid,Vec3d way,int hit,int seed,float s,boolean light,Vec3d camera){
        if(s<0||s>=SWIRL_LIFE)return;
        double big=big(hit);Vec3d at=burstAt(mid,camera);
        float near=DanceBurst.near(at,camera);if(near<=0)return;
        var f=GlitchFx.facing(camera,at);
        // the white star and its thin rays: a moment
        if(light){
            if(s<.1f)m.softDisc(at,.3*big,WHITE,.8f*in(s)*(1-s/.1f)*near);
            if(s<STAR_LIFE)m.star(at,.7*big,WHITE,.85f*in(s)*(1-s/STAR_LIFE)*near);
            if(s<RAY_LIFE)for(int j=0;j<RAYS;j++){
                var r=ray(j,seed,big);Vec3d dir=f[0].multiply(Math.cos(r[0])).add(f[1].multiply(Math.sin(r[0])));
                m.needle(at.add(dir.multiply(.1*big)),at.add(dir.multiply(.1*big+r[1]*Ease.smooth(s,0,.04f))),.045*big,.3,WHITE,.9f*in(s)*(1-s/RAY_LIFE)*near);
            }
            m.softDisc(at,.75*big,GLOW,.16f*in(s)*(1-Ease.smooth(s,.08f,.3f))*near);
        }
        // the swirl: two crescents turning round them, closing in
        var w=swirl(s,seed,big);
        if(w[4]>0)for(int k=0;k<2;k++)crescent(m,at,f,w[0],w[1]+k*Math.PI,w[2],w[3],(float)w[4]*near,light);
        // the spikes: shot out and gone
        int n=last(hit)?SPIKES_LAST:SPIKES;
        for(int j=0;j<n;j++){
            var sp=spike(at,f,j,n,seed,s,big);if(sp==null)continue;
            if(light){
                m.needle(sp.a(),sp.b(),sp.width()*2.2,.25,GLOW,.14f*sp.fade()*near);
                if(j%2==0)m.needle(sp.a(),sp.b(),sp.width()*.3,.25,WHITE,.8f*sp.fade()*near);
            }else{
                m.needle(sp.a(),sp.b(),sp.width()*1.45,.25,INK,.85f*sp.fade()*near);
                m.needle(sp.a(),sp.b(),sp.width(),.25,j%2==0?PURPLE:DEEP,.95f*sp.fade()*near);
            }
        }
        // a few sparks
        if(light)for(int j=0;j<SPARKS;j++){
            var b=spark(at,way,j,seed,s,big);if(b==null)continue;
            m.needle(b[0],b[0].subtract(b[1].multiply(.03)),.04*big,.3,j%2==0?WHITE:SPARK,.9f*(float)b[2].x*DanceBurst.near(b[0],camera));
        }
    }
    /**
     * One crescent of the swirl, facing the camera: an arc of radius {@code r} about {@code c} from angle {@code from}
     * on round {@code span}, thin at its tail and {@code thick} at its head (inward from the arc). Its body (ink, the
     * deep purple, the vivid purple along its outer edge), or its light (a thin lilac line along its outer edge).
     */
    static void crescent(EffectMesh m,Vec3d c,Vec3d[] f,double r,double from,double span,double thick,float alpha,boolean light){
        int n=16;
        for(int i=0;i<n;i++){
            double u0=i/(double)n,u1=(i+1)/(double)n,a0=from+span*u0,a1=from+span*u1;
            Vec3d d0=f[0].multiply(Math.cos(a0)).add(f[1].multiply(Math.sin(a0))),d1=f[0].multiply(Math.cos(a1)).add(f[1].multiply(Math.sin(a1)));
            double w0=thick*shape(u0),w1=thick*shape(u1);
            if(light){
                m.quad(c.add(d0.multiply(r-.03*shape(u0))),c.add(d1.multiply(r-.03*shape(u1))),c.add(d1.multiply(r)),c.add(d0.multiply(r)),LILAC,.5f*alpha);
                continue;
            }
            m.quad(c.add(d0.multiply(r-w0-.025)),c.add(d1.multiply(r-w1-.025)),c.add(d1.multiply(r+.025)),c.add(d0.multiply(r+.025)),INK,.85f*alpha);
            m.quad(c.add(d0.multiply(r-w0)),c.add(d1.multiply(r-w1)),c.add(d1.multiply(r)),c.add(d0.multiply(r)),DEEP,.95f*alpha);
            m.quad(c.add(d0.multiply(r-w0*.45)),c.add(d1.multiply(r-w1*.45)),c.add(d1.multiply(r)),c.add(d0.multiply(r)),PURPLE,.95f*alpha);
        }
    }
    /** How thick a crescent is {@code u} of the way from its tail to its head: from nothing, widest near the head, closing at the head. */
    static double shape(double u){return Math.pow(u,.8)*(1-Ease.smooth((float)u,.82f,1));}

    /** Everything but the cut, in one pass: the lunge's ring and what the blow leaves on whom it struck. */
    static void draw(Fx c,EffectMesh m,boolean light){
        int hit=c.cast.combo();float t=c.sec,s=t-blow(hit);
        if(s<0||!KnifeClient.cameTo(c.cast,0))return;
        if(last(hit)&&s<RING_LIFE){
            var tip=KnifeCombo.blade(KnifeCombo.spot(c,0),c.cast.yaw(),hit,blow(hit));
            Vec3d way=tip[1],at=tip[0].add(way.multiply(KnifeCombo.TIP));
            float near=DanceBurst.near(at,c.camera)*KnifeCombo.own(c),shown=ringShown(s)*near;
            if(shown>0){
                var uv=DanceBurst.across(way);double r=ringRadius(s);
                if(light)FxLib.ringPlane(m,at,uv[0],uv[1],r,.05,WHITE,.4f*shown);
                else DanceBurst.band(m,at,uv[0],uv[1],r,.14,PURPLE,.75f*shown);
            }
        }
        if(s>=SWIRL_LIFE)return;
        for(var e:KnifeCombo.struck(c,0))burst(m,e.mid(),c.f,hit,KnifeCombo.seed(c,0)+e.id(),s,light,c.camera);
    }

    /** The light the cut and the bursts throw on the world; and, while the knife cuts, little afterglow (the band would leave copies of itself). */
    static void frame(Fx c,FxFrame f){
        int hit=c.cast.combo(),place=place(hit);float t=c.sec,until=KnifeClient.until(c.cast.id());
        if(t>=FROM[place]-.02f&&t<TO[place]+.06f)f.afterglow(.15f);
        float g=Ease.window(t,FROM[place],TO[place]+.1f,.03f,.1f);
        if(g>.01f&&t<until){var hb=KnifeCombo.blade(c.pos,c.cast.yaw(),hit,t);f.light(hb[0].add(hb[1].multiply(KnifeCombo.TIP*.7)),0x8a2be2,4,.35f*g);}
        float s=t-blow(hit);
        if(s>=0&&s<.3f&&KnifeClient.cameTo(c.cast,0))for(var e:KnifeCombo.struck(c,0))f.light(e.mid(),0x9a3cff,last(hit)?8:6,(last(hit)?1f:.65f)*(1-s/.3f));
    }
}
