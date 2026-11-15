package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import dev.sangwoo.celestialfall.xchara.XSlashRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The great X slash made grand (2.135.0, user request: "이제 이걸 엄청 멋있게 만들어봐", of the X slash of 2.134.0):
 * everything it does drawn bigger and with more to it, in X Chara's purple and glitch, without a screen effect (the
 * user took the impact frames off Lee So-sang's R and T: "화면 효과는 빼줘"). What it is, where and when, is the
 * server's as before (XSlashRules); its sounds are as before. Mine, all of it:
 * <ul>
 * <li>as the knife lights, pixels of data drawn in from round them into the blade, faster and faster, and a glint up
 * it ({@link #gather}); a ring of the hack's at their feet, ticking round, flaring as each cut lands, thrown out as the X
 * goes ({@link #ring});</li>
 * <li>each cut split into pink and cyan to either side as it is drawn, as a glitch does ({@link #split}), sparks flung
 * off its cutting edge as the cut's head passes ({@link #spark}); a soft purple bloom over each arm all its life
 * ({@link #bloom}), a white-hot heart where they cross;</li>
 * <li>as the X is whole, corner marks closing on where its arms cross, turned and tightening as a lock does
 * ({@link #lock}), bars of it torn sideways for a moment ({@link #tear});</li>
 * <li>as it goes, two rings blown out in its plane where it went from, one on the ground, streaks rushing back past its
 * user, a flash in the palm that sent it, pixels thrown on ({@link #launch});</li>
 * <li>flying, echoes of it a few hundredths of a second behind in pink, cyan and purple ({@link #echoes}), lightning
 * crawling over its arms ({@link #arcs}), a ring left in its wake every few blocks ({@link #wakes}), sparks ground off
 * its lower horns on the floor ({@link #hornSpark});</li>
 * <li>where its way ends, broken into shards of purple tumbling on and down ({@link #shard}), a flash and a ring;</li>
 * <li>on whom it strikes, an X struck across them ({@link #struck}), bars of them torn, pixels shattered off;</li>
 * <li>the X on the ground cracks out to either side as it is burned in ({@link #crack}), data rising off it more and
 * more ({@link #motes}), its light running in to where its lines cross before it bursts ({@link #converge});</li>
 * <li>bursting: pillars of purple light shot up along it from where its lines cross ({@link #pillars}), the ground under
 * it heaved and pieces of it thrown ({@link #solids}), dust ({@link #dust}), embers rising after ({@link #embers}).</li>
 * </ul>
 * Everything is a function of the skill's own clock, its seed and what the server told, the same on every client.
 */
final class XSlashLights {
    private XSlashLights(){}
    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE,PINK=GlitchFx.PINK,CYAN=GlitchFx.CYAN;
    static final Vec3d UP=new Vec3d(0,1,0);
    static final float LIT=XSlash.LIT,CUT1=XSlash.CUT1,CUT2=XSlash.CUT2,FIRE=XSlash.FIRE;
    static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}
    static float in(float s,float tau){return s<=0?0:Math.min(1,s/tau);}
    /** Where smoothstep reaches {@code u} (0 to 1): the cut's head (XSlash.cut) is that far along at FROM + this of its window. */
    static float unsmooth(float u){u=Math.max(0,Math.min(1,u));return (float)(.5-Math.sin(Math.asin(1-2*u)/3));}
    /** A unit vector from three numbers of a seed (any way). */
    static Vec3d anyWay(int j,int seed,int salt){
        Vec3d v=new Vec3d(h(j,seed,salt)-.5,h(j,seed,salt+1)-.5,h(j,seed,salt+2)-.5);
        return v.lengthSquared()<1e-6?UP:v.normalize();
    }

    // ───────────────────────────── where on the X ─────────────────────────────

    /**
     * A point of arm {@code a}'s band (as XSlash.arm draws it), {@code t} along it (0 its first horn, 1 its last) and
     * {@code k} across it (0 its inner edge, 1 its cutting edge), for an X {@code fw} ahead, {@code half} wide and
     * {@code tall} high (half of each).
     */
    static Vec3d armPoint(XSlash.Way w,double fw,double half,double tall,int a,double t,double k){
        double sx=a==0?half:-half,sy=2*tall,ex=-sx,ey=0;
        double nx=a==0?-tall:tall,ny=half,nl=Math.hypot(nx,ny);nx/=nl;ny/=nl;
        double len=Math.hypot(ex-sx,ey-sy),b=(XSlash.INNER+(XSlash.OUTER-XSlash.INNER)*k)*len,s=Math.sin(Math.PI*t);
        return w.at(fw,sx+(ex-sx)*t+nx*b*s,sy+(ey-sy)*t+ny*b*s);
    }
    /** The way arm {@code a}'s cutting edge bows out (unit, in the X's plane). */
    static Vec3d armNormal(XSlash.Way w,double half,double tall,int a){
        double nx=a==0?-tall:tall,ny=half,nl=Math.hypot(nx,ny);
        return w.right().multiply(nx/nl).add(0,ny/nl,0);
    }
    /** The way arm {@code a}'s cut goes (from its first horn to its last: down to the low left, or to the low right). */
    static Vec3d armWay(XSlash.Way w,double half,double tall,int a){
        Vec3d v=w.right().multiply(a==0?-2*half:2*half).add(0,-2*tall,0);return v.normalize();
    }
    /** Where the middles of the two arms' bands cross (both bow up: a little over the X's middle). */
    static Vec3d heart(XSlash.Way w,double fw,double half,double tall){
        double nl=Math.hypot(tall,half),b=(XSlash.OUTER+XSlash.INNER)*.5*2*nl;
        return w.at(fw,0,tall+half/nl*b);
    }

    // ───────────────────────────── as the knife lights ─────────────────────────────

    /** The data drawn into the knife as it lights: this many pixels, the last setting out this long after it lights, each this long on its way (seconds): all of it in before the first cut swings. */
    static final int GATHER=18;static final float GATHER_LAST=.06f,GATHER_TRAVEL=.09f;
    /** When pixel {@code j} of the gathering sets out. */
    static float gatherBorn(int j,int seed){return LIT+GATHER_LAST*(float)h(j,seed,1);}
    /** Where it sets out from: {ahead, up, to the right} of their feet, round where the knife is wound up (behind the right shoulder), 1.4 to 2.5 blocks off. */
    static double[] gatherFrom(int j,int seed){
        double az=Math.PI*2*h(j,seed,2),el=(h(j,seed,3)-.35)*1.5,r=1.4+1.1*h(j,seed,4);
        return new double[]{-.1+r*Math.cos(el)*Math.cos(az)*.85,1.8+r*Math.sin(el)*.8,.6+r*Math.cos(el)*Math.sin(az)};
    }
    /** How far along its way pixel {@code j} is {@code sec} seconds in (0 setting out to 1 in the knife), faster and faster as it is drawn in; NaN not yet or done. */
    static float gatherAlong(int j,int seed,float sec){
        float u=(sec-gatherBorn(j,seed))/GATHER_TRAVEL;return u<0||u>=1?Float.NaN:u*u;
    }
    /** The last of it is in the knife this many seconds in. */
    static final float GATHERED=LIT+GATHER_LAST+GATHER_TRAVEL;
    static int pick(int j,int seed,int salt){double c=h(j,seed,salt);return c<.42?PURPLE:c<.6?LILAC:c<.74?DEEP:c<.84?WHITE:c<.93?PINK:CYAN;}
    static void gather(Fx c,EffectMesh m,float t,float stop,int seed,float own,boolean light){
        if(t<LIT||t>GATHERED+.14f||!Float.isNaN(stop)&&t>stop)return;
        Vec3d[] hb=KnifeCombo.blade(c.pos,c.yaw,XCharaCombo.XSLASH,t);
        Vec3d f=Vec3d.fromPolar(0,c.yaw),r=new Vec3d(-f.z,0,f.x);
        for(int j=0;j<GATHER;j++){
            float e=gatherAlong(j,seed,t);if(Float.isNaN(e))continue;
            double[] o=gatherFrom(j,seed);Vec3d start=c.pos.add(f.multiply(o[0])).add(0,o[1],0).add(r.multiply(o[2]));
            Vec3d to=hb[0].add(hb[1].multiply(KnifeCombo.HEEL+(KnifeCombo.TIP-KnifeCombo.HEEL)*(.2+.8*h(j,seed,5))));
            Vec3d p=start.lerp(to,e),back=start.lerp(to,Math.max(0,e-.22));
            float a=GlitchFx.near(p,c.camera)*own*Math.min(1,e*8+.25f);if(a<=0)continue;
            var fc=GlitchFx.facing(c.camera,p);double size=.045+.04*h(j,seed,6);int rgb=pick(j,seed,7);
            if(light){m.softLine(back,p,.11,GLOW,.32f*a);if(rgb!=INK)GlitchFx.square(m,p,fc,size*2,rgb==WHITE?GLOW:rgb,.3f*a);}
            else{GlitchFx.square(m,p,fc,size+.012,INK,.85f*a);GlitchFx.square(m,p,fc,size,rgb,a);m.line(back,p,.025,j%3==0?LILAC:PURPLE,.55f*a);}
        }
        // the knife takes it in: a glint run up its blade to its point
        float s=t-(GATHERED-.03f);
        if(light&&s>0&&s<.13f){
            float run=Ease.out(s,0,.08f),a=in(s,.015f)*(1-Ease.smooth(s,.06f,.13f))*own;
            Vec3d p=hb[0].add(hb[1].multiply(KnifeCombo.HEEL+(KnifeCombo.TIP-KnifeCombo.HEEL)*run));
            m.softDisc(p,.5,GLOW,.4f*a);m.star(p,.34,WHITE,.55f*a*GlitchFx.near(p,c.camera));
        }
    }

    /** The ring at their feet: its radius as the knife lights, how long it opens, and how far and over how long it is thrown out as the X goes (blocks, seconds). */
    static final double RING_R=1.35,RING_OUT=.55;static final float RING_OPEN=.1f,RING_GONE=.26f;
    /** How strongly it shows {@code sec} seconds in (stopped short {@code stop} seconds in, NaN: not): opening as the knife lights, gone as the X goes (or as it is stopped). */
    static float ringShown(float sec,float stop){
        if(sec<LIT)return 0;
        float open=Ease.smooth(sec,LIT,LIT+RING_OPEN);
        if(!Float.isNaN(stop)&&stop<FIRE)return open*(1-Ease.smooth(sec,stop,stop+.12f));
        return open*(1-Ease.smooth(sec,FIRE+.04f,FIRE+RING_GONE));
    }
    /** How big it is: opening out to its radius, then a little further out as the X goes and it flares away. */
    static double ringRadius(float sec,float stop){
        double r=RING_R*(.7+.3*Ease.out(sec,LIT,LIT+RING_OPEN));
        if(Float.isNaN(stop)||stop>=FIRE)r+=RING_OUT*Ease.out(sec,FIRE,FIRE+RING_GONE);
        return r;
    }
    /** How it flares: as each cut lands, and most as the X goes. */
    static float ringFlare(float sec){return KnifeCombo.bump(sec,CUT1,.07f)+KnifeCombo.bump(sec,CUT2,.07f)+1.5f*KnifeCombo.bump(sec,FIRE,.09f);}
    static void ring(Fx c,EffectMesh m,XSlash.Way w,float t,float stop,int seed,float own,boolean light){
        float a=ringShown(t,stop)*own;if(a<=.01f)return;
        double r=ringRadius(t,stop);float fl=Math.min(1.6f,ringFlare(t));
        Vec3d o=new Vec3d(w.from().x,w.from().y+.045,w.from().z),u=w.f(),v=w.right();
        if(light){
            DanceBurst.band(m,o,u,v,r,.55,GLOW,.2f*a*(1+fl));
            flatCircle(m,o,u,v,r,.035,WHITE,.3f*a*(.4f+fl));
        }else{
            DanceBurst.band(m,o,u,v,r,.16,PURPLE,.8f*a);
            flatCircle(m,o,u,v,r*1.008,.03,INK,.6f*a);
        }
        // ticks round it, turning one way; dashes inside it, turning the other; a few out at a time, as a glitch
        int step=(int)Math.floor(t*20);
        for(int i=0;i<24;i++){
            if(h(i+step*29,seed,11)<.16)continue;
            double ang=.7*t+i*Math.PI*2/24,r0=r*1.07,r1=r*(i%3==0?1.24:1.15);
            Vec3d d=u.multiply(Math.cos(ang)).add(v.multiply(Math.sin(ang)));
            if(light)flat(m,o.add(d.multiply(r0)),o.add(d.multiply(r1)),.05,GLOW,.35f*a*(1+fl));
            else flat(m,o.add(d.multiply(r0)),o.add(d.multiply(r1)),.028,i%3==0?LILAC:PURPLE,.9f*a);
        }
        for(int i=0;i<6;i++){
            if(h(i+step*13,seed,12)<.12)continue;
            double a0=-1.1*t+i*Math.PI/3,a1=a0+.55;Vec3d last=null;
            for(int q=0;q<=5;q++){
                double ang=a0+(a1-a0)*q/5;Vec3d p=o.add(u.multiply(Math.cos(ang)*r*.8)).add(v.multiply(Math.sin(ang)*r*.8));
                if(last!=null){if(light)flat(m,last,p,.07,GLOW,.25f*a*(1+fl));else flat(m,last,p,.035,DEEP,.85f*a);}
                last=p;
            }
        }
    }

    // ───────────────────────────── the cuts ─────────────────────────────

    /** Each cut's split colours: shown as it is cut and this long after (seconds), this far to either side of its cutting edge (blocks). */
    static final float SPLIT_AFTER=.16f;static final double SPLIT=.1;
    /** How strongly arm {@code a}'s split shows {@code sec} seconds in. */
    static float split(int a,float sec){
        float from=a==0?XSlash.ARM1_FROM:XSlash.ARM2_FROM,to=a==0?XSlash.ARM1_TO:XSlash.ARM2_TO;
        return Ease.smooth(sec,from,from+.025f)*(1-Ease.smooth(sec,to,to+SPLIT_AFTER));
    }
    /** (The light) each cut's cutting edge again, in pink to one side and cyan to the other, jumping a little and dropping out now and then. */
    static void split(Fx c,EffectMesh m,XSlash.Way w,double[] x,float t,float stop,int seed,float own){
        Vec3d side=GlitchFx.facing(c.camera,w.at(x[0],0,x[2]))[0];int step=(int)Math.floor(t*30);
        for(int a=0;a<2;a++){
            float s=split(a,t)*own*(float)(x[3]*(1-x[4]));if(s<=.01f)continue;
            float p=XSlash.cut(a,Float.isNaN(stop)?t:Math.min(t,stop));if(p<=.02f)continue;
            var sw=XSlash.arm(w,x[0],x[1],x[2],a,p);
            for(int k=0;k<2;k++){
                if(h(step*2+k,seed+a,22)<.18)continue;
                double off=SPLIT*(1+.7*(h(step*2+k,seed+a,21)-.5));
                Vec3d o=side.multiply(k==0?-off:off).add(0,(h(step+k*5,seed+a,23)-.5)*.05,0);
                int rgb=k==0?PINK:CYAN;
                for(int i=0;i+1<sw.n();i++){
                    float kk=(float)Math.sin(Math.PI*sw.along()[i]);
                    m.line(sw.outer()[i].add(o),sw.outer()[i+1].add(o),.05+.06*kk,rgb,.42f*s*(.35f+.65f*kk)*GlitchFx.near(sw.outer()[i],c.camera));
                }
            }
        }
    }
    /** Sparks flung off each cut's cutting edge as its head passes. */
    static final int CUT_SPARKS=16;
    /** When spark {@code j} of arm {@code a} flies off: as the cut's head (XSlash.cut) reaches its place. */
    static float sparkBorn(int a,int j){
        float from=a==0?XSlash.ARM1_FROM:XSlash.ARM2_FROM,to=a==0?XSlash.ARM1_TO:XSlash.ARM2_TO;
        return from+(to-from)*unsmooth((j+.5f)/CUT_SPARKS);
    }
    /** How long spark {@code j} of arm {@code a} lasts (seconds). */
    static float sparkLife(int a,int j,int seed){return (float)(.28+.2*h(j,seed+a,31));}
    /**
     * Where spark {@code j} of arm {@code a} is {@code s} seconds after it flew off (null before or once gone): off its
     * place on the cutting edge of the X where it stands crossed, out the way the edge bows and on along the cut,
     * slowing, falling.
     */
    static Vec3d spark(XSlash.Way w,int a,int j,int seed,float s){
        if(s<0||s>=sparkLife(a,j,seed))return null;
        double u=(j+.5)/CUT_SPARKS,fw=XSlashRules.NEAR,half=XSlashRules.HALF0,tall=XSlashRules.TALL0;
        Vec3d p0=armPoint(w,fw,half,tall,a,u,1);
        Vec3d v=armNormal(w,half,tall,a).multiply(2.4+3.4*h(j,seed+a,32)).add(armWay(w,half,tall,a).multiply(2.5+4*h(j,seed+a,33)))
            .add(w.f().multiply(.6+1.6*h(j,seed+a,34))).add(anyWay(j,seed+a,35).multiply(1.2));
        double drag=4.2,travel=(1-Math.exp(-drag*s))/drag;
        return p0.add(v.multiply(travel)).add(0,-4.5*s*s,0);
    }
    static void sparks(Fx c,EffectMesh m,XSlash.Way w,float t,float stop,int seed,float own,boolean light){
        for(int a=0;a<2;a++)for(int j=0;j<CUT_SPARKS;j++){
            float born=sparkBorn(a,j);if(!Float.isNaN(stop)&&born>stop)continue;
            float s=t-born;Vec3d p=spark(w,a,j,seed,s);if(p==null)continue;
            Vec3d q=spark(w,a,j,seed,Math.max(0,s-.028f));if(q==null)q=p;
            float life=sparkLife(a,j,seed),fa=in(s,.012f)*(1-Ease.smooth(s,life*.5f,life))*own*GlitchFx.near(p,c.camera);if(fa<=0)continue;
            if(light){m.softLine(q,p,.12,GLOW,.42f*fa);m.line(q,p,.02,j%2==0?WHITE:LILAC,.75f*fa);}
            else m.line(q,p,.042,j%3==0?LILAC:PURPLE,.9f*fa);
        }
    }
    /** (The light) a soft purple bloom over each arm, as wide as its band and more, all its life; and a white-hot heart where they cross. */
    static void bloom(Fx c,EffectMesh m,XSlash.Way w,double[] x,float t,float stop,float own){
        float fade=(float)(x[3]*(1-x[4]))*own;if(fade<=.01f)return;
        for(int a=0;a<2;a++){
            float p=XSlash.cut(a,Float.isNaN(stop)?t:Math.min(t,stop));if(p<=.02f)continue;
            var sw=XSlash.arm(w,x[0],x[1],x[2],a,p);
            for(int i=0;i+2<sw.n();i+=2){
                Vec3d m0=sw.inner()[i].lerp(sw.outer()[i],.6),m1=sw.inner()[i+2].lerp(sw.outer()[i+2],.6);
                double wide=Math.max(.25,sw.inner()[i+1].distanceTo(sw.outer()[i+1])*2.3);
                m.softLine(m0,m1,wide,GLOW,.14f*fade*GlitchFx.near(m0,c.camera));
            }
        }
        if(XSlash.cut(1,Float.isNaN(stop)?t:Math.min(t,stop))>.6f){
            Vec3d hp=heart(w,x[0],x[1],x[2]);float near=GlitchFx.near(hp,c.camera)*fade,beat=.75f+.25f*(float)Math.sin(t*42);
            m.softDisc(hp,1.1+.25*beat,GLOW,.22f*near);m.softDisc(hp,.45,LILAC,.3f*near*beat);m.star(hp,.42,WHITE,.3f*near*beat);
        }
    }

    // ───────────────────────────── the slash itself ─────────────────────────────
    // (2.135.0, the user's "참격을 멋있게 꾸며봐 집중적으로": the X itself, above all)

    /** How big an X is against the full-grown one (its arm's length over the full-grown arm's): what is drawn on it scales with it. */
    static double scale(double half,double tall){return Math.hypot(half,tall)/Math.hypot(XSlashRules.HALF,XSlashRules.TALL);}
    /** How far the cut of arm {@code a} has been drawn {@code sec} seconds in (stopped short {@code stop} seconds in, NaN: not). */
    static float drawnOf(int a,float sec,float stop){return XSlash.cut(a,Float.isNaN(stop)?sec:Math.min(sec,stop));}
    /** A point of arm {@code a} bowed out {@code bow} of its horns' line's length at its crown (its band from XSlash.INNER to XSlash.OUTER), {@code t} along it. */
    static Vec3d bowPoint(XSlash.Way w,double fw,double half,double tall,int a,double t,double bow){
        return armPoint(w,fw,half,tall,a,t,(bow-XSlash.INNER)/(XSlash.OUTER-XSlash.INNER));
    }

    /** Echo blades: thin arcs of light just outside each arm's cutting edge, bowed out further, each a little later and a little behind it (bow, seconds later, blocks behind full grown). */
    static final double[] ECHO_BOW={.19,.225};static final float[] ECHO_LAG={.035f,.07f};static final double[] ECHO_BACK={.45,.9};
    static void echoBlades(Fx c,EffectMesh m,XSlash.Way w,double[] x,float t,float stop,float own,boolean light){
        float fade=(float)(x[3]*(1-x[4]))*own;if(fade<=.01f)return;
        double sc=scale(x[1],x[2]),fly=t<FIRE?.3:1;
        for(int a=0;a<2;a++)for(int b=0;b<ECHO_BOW.length;b++){
            float p=drawnOf(a,t-ECHO_LAG[b],stop);if(p<=.03f)continue;
            double fw=x[0]-ECHO_BACK[b]*sc*fly;int n=22;Vec3d last=null;
            for(int i=0;i<=n;i++){
                double u=p*i/(double)n;Vec3d q=bowPoint(w,fw,x[1],x[2],a,u,ECHO_BOW[b]);
                if(last!=null){
                    double s=Math.sin(Math.PI*u),wide=(.03+.11*s)*sc;
                    float al=fade*(float)(.25+.75*s)*(b==0?1:.65f)*GlitchFx.near(q,c.camera);
                    if(light){m.softLine(last,q,wide*5,GLOW,.2f*al);m.line(last,q,wide*.45,b==0?WHITE:LILAC,.55f*al);}
                    else m.line(last,q,wide,b==0?LILAC:PURPLE,.7f*al);
                }
                last=q;
            }
        }
    }
    /** Spikes of energy out of each arm's cutting edge: how many to an arm. */
    static final int SPIKES=18;
    /** How long spike {@code i} of arm {@code a} is {@code sec} seconds in, for an X {@code sc} of full grown (blocks): longer at the crown, swaying, a few long ones flicking out. */
    static double spikeLength(int a,int i,int seed,float sec,double sc){
        double u=(i+.5)/SPIKES,s=Math.sin(Math.PI*u);
        double sway=.62+.38*Math.sin(sec*34+Math.PI*2*h(i,seed+a,181));
        double flick=h((int)Math.floor(sec*12)*SPIKES+i,seed+a,182)<.18?1.7:1;
        return (.34+.74*h(i,seed+a,183))*sway*flick*(.35+.65*s)*sc;
    }
    static void spikes(Fx c,EffectMesh m,XSlash.Way w,double[] x,float t,float stop,int seed,float own,boolean light){
        float fade=(float)(x[3]*(1-x[4]))*own;if(fade<=.01f)return;
        double sc=scale(x[1],x[2]);
        for(int a=0;a<2;a++){
            float p=drawnOf(a,t,stop);if(p<=.03f)continue;
            Vec3d n=armNormal(w,x[1],x[2],a),along=armWay(w,x[1],x[2],a);
            Vec3d dir=n.multiply(.88).subtract(along.multiply(.32)).normalize();
            for(int i=0;i<SPIKES;i++){
                double u=(i+.5)/SPIKES;if(u>p)break;
                double s=Math.sin(Math.PI*u),len=spikeLength(a,i,seed,t,sc),wide=(.22+.16*s)*sc;
                Vec3d base=armPoint(w,x[0],x[1],x[2],a,u,.96),tip=base.add(dir.multiply(len));
                float al=fade*Ease.smooth((float)(p-u),0,.04f)*GlitchFx.near(base,c.camera);if(al<=0)continue;
                if(light){m.needle(base,tip,along,wide*2.4,.18,GLOW,.3f*al);m.needle(base,tip,along,wide*.3,.18,WHITE,.6f*al);}
                else{m.needle(base,tip,along,wide*1.3,.18,INK,.65f*al);m.needle(base,tip,along,wide,.18,i%3==0?LILAC:PURPLE,.92f*al);}
            }
        }
    }
    /** (The light) where each cut's head is as it is drawn: a hot flare and a streak along the way it goes. */
    static void head(Fx c,EffectMesh m,XSlash.Way w,double[] x,float t,float stop,float own){
        double sc=scale(x[1],x[2]);
        for(int a=0;a<2;a++){
            float from=a==0?XSlash.ARM1_FROM:XSlash.ARM2_FROM,to=a==0?XSlash.ARM1_TO:XSlash.ARM2_TO;
            if(t<from||t>to+.07f||!Float.isNaN(stop)&&t>stop)continue;
            float p=drawnOf(a,t,stop);
            Vec3d hp=armPoint(w,x[0],x[1],x[2],a,p,.95),ahead=armPoint(w,x[0],x[1],x[2],a,Math.min(1,p+.05),.95),dir=ahead.subtract(hp);
            dir=dir.lengthSquared()<1e-8?armWay(w,x[1],x[2],a):dir.normalize();
            float al=Ease.smooth(t,from,from+.02f)*(1-Ease.smooth(t,to,to+.07f))*own*GlitchFx.near(hp,c.camera);if(al<=0)continue;
            m.softDisc(hp,.9*sc+.35,GLOW,.42f*al);m.star(hp,.42*sc+.16,WHITE,.6f*al);
            m.softLine(hp.subtract(dir.multiply(1.1*sc)),hp.add(dir.multiply(.45*sc)),.32*sc,LILAC,.5f*al);
        }
    }
    /** (The light) glints running along each arm's cutting edge once it is whole, two to an arm, each with a short tail. */
    static void glints(Fx c,EffectMesh m,XSlash.Way w,double[] x,float t,float stop,int seed,float own){
        float fade=(float)(x[3]*(1-x[4]))*own;if(fade<=.01f)return;
        double sc=scale(x[1],x[2]);
        for(int a=0;a<2;a++){
            if(drawnOf(a,t,stop)<.98f)continue;
            for(int k=0;k<2;k++){
                double ph=t*1.9+k*.5+.3*h(k,seed+a,191);ph-=Math.floor(ph);
                double u=-.1+1.2*ph;if(u<.03||u>.97)continue;
                Vec3d gp=armPoint(w,x[0],x[1],x[2],a,u,1);
                float al=fade*(float)Math.sin(Math.PI*u)*GlitchFx.near(gp,c.camera);
                m.softDisc(gp,.55*sc+.2,LILAC,.32f*al);m.star(gp,.38*sc+.12,WHITE,.62f*al);
                Vec3d last=gp;
                for(int i=1;i<=6;i++){double ub=u-.028*i;if(ub<0)break;Vec3d q=armPoint(w,x[0],x[1],x[2],a,ub,1);m.softLine(last,q,.2*sc*(1-i/7.0),WHITE,.38f*al*(1-i/7f));last=q;}
            }
        }
    }
    /** (The light) a soft purple aura just outside each arm's cutting edge. */
    static void aura(Fx c,EffectMesh m,XSlash.Way w,double[] x,float t,float stop,float own){
        float fade=(float)(x[3]*(1-x[4]))*own;if(fade<=.01f)return;double sc=scale(x[1],x[2]);
        for(int a=0;a<2;a++){
            float p=drawnOf(a,t,stop);if(p<=.03f)continue;Vec3d n=armNormal(w,x[1],x[2],a);
            int nn=16;Vec3d last=null;
            for(int i=0;i<=nn;i++){
                double u=p*i/(double)nn,s=Math.sin(Math.PI*u);Vec3d q=armPoint(w,x[0],x[1],x[2],a,u,1).add(n.multiply(.3*sc*s));
                if(last!=null)m.softLine(last,q,(.3+1.2*s)*sc,GLOW,.15f*fade*(float)(.3+.7*s)*GlitchFx.near(q,c.camera));
                last=q;
            }
        }
    }
    /** (The light) the trails its two upper horns leave as it flies. */
    static void tipTrails(Fx c,EffectMesh m,XSlash.Way w,float t,float stop,float own){
        if(t<FIRE)return;
        double[] now=XSlash.shape(t,w.reach(),stop);if(now==null)return;
        float fade=(float)(1-now[4])*own;if(fade<=.01f)return;
        for(int k=0;k<2;k++){
            double side=k==0?1:-1;Vec3d last=null;
            for(int i=0;i<=8;i++){
                float ti=Math.max(FIRE,t-.16f*i/8f);double[] x=XSlash.shape(ti,w.reach(),stop);if(x==null)break;
                Vec3d q=w.at(x[0],side*x[1],2*x[2]);
                if(last!=null&&last.squaredDistanceTo(q)>1e-6){float al=fade*(1-i/8f)*GlitchFx.near(q,c.camera);m.softLine(last,q,.42*(1-i/9.0),GLOW,.3f*al);m.line(last,q,.035,LILAC,.55f*al);}
                last=q;
            }
        }
    }

    // ───────────────────────────── as the X is whole ─────────────────────────────

    /** The lock: corner marks closing on where the arms cross from {@link #LOCK0} until the X goes, then gone this soon. */
    static final float LOCK0=.42f,LOCK_GONE=.09f;
    static float lockShown(float sec,float stop){
        if(!Float.isNaN(stop)&&stop<FIRE)return sec<stop?Ease.smooth(sec,LOCK0,LOCK0+.03f)*(1-Ease.smooth(sec,stop,stop+.05f)):0;
        return Ease.smooth(sec,LOCK0,LOCK0+.03f)*(1-Ease.smooth(sec,FIRE,FIRE+LOCK_GONE));
    }
    /** Half its side (blocks): closing from 1.7 to 0.85 as the X is whole, a little open again as it snaps shut and goes. */
    static double lockHalf(float sec){return sec<FIRE?.85+.85*(1-Ease.out(sec,LOCK0,FIRE)):.85+.35*Ease.out(sec,FIRE,FIRE+LOCK_GONE);}
    /** How far it is turned (radians): from an eighth of a turn to square as it closes. */
    static double lockTurn(float sec){return Math.PI/4*(1-Ease.out(sec,LOCK0,FIRE));}
    static void lock(Fx c,EffectMesh m,XSlash.Way w,float t,float stop,float own,boolean light){
        float a=lockShown(t,stop)*own;if(a<=.01f)return;
        Vec3d o=heart(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0);
        double H=lockHalf(t),turn=lockTurn(t),L=.36*H;
        Vec3d e1=w.right().multiply(Math.cos(turn)).add(0,Math.sin(turn),0),e2=w.right().multiply(-Math.sin(turn)).add(0,Math.cos(turn),0);
        float near=GlitchFx.near(o,c.camera)*a;
        for(int k=0;k<4;k++){
            double sx=k%2==0?1:-1,sy=k<2?1:-1;
            Vec3d corner=o.add(e1.multiply(sx*H)).add(e2.multiply(sy*H));
            Vec3d p1=corner.subtract(e1.multiply(sx*L)),p2=corner.subtract(e2.multiply(sy*L));
            if(light){m.softLine(corner,p1,.22,GLOW,.35f*near);m.softLine(corner,p2,.22,GLOW,.35f*near);m.line(corner,p1,.025,WHITE,.8f*near);m.line(corner,p2,.025,WHITE,.8f*near);}
            else{m.line(corner,p1,.1,INK,.8f*near);m.line(corner,p2,.1,INK,.8f*near);m.line(corner,p1,.055,LILAC,.95f*near);m.line(corner,p2,.055,LILAC,.95f*near);}
        }
        // a small cross at its middle as it shuts
        float shut=KnifeCombo.bump(t,FIRE-.015f,.05f);
        if(light&&shut>.02f)m.star(o,.55,WHITE,.5f*shut*near);
    }
    /** Bars of the X torn sideways for a moment as it is whole (seconds). */
    static final float TEAR0=.455f,TEAR1=.565f;
    static void tear(Fx c,EffectMesh m,XSlash.Way w,double[] x,float t,int seed,float own,boolean light){
        float a=Ease.smooth(t,TEAR0,TEAR0+.015f)*(1-Ease.smooth(t,TEAR1-.04f,TEAR1))*own*(float)(1-x[4]);if(a<=.01f)return;
        int step=(int)Math.floor(t*30);
        for(int i=0;i<7;i++){
            int key=step*7+i;if(h(key,seed,41)<.28)continue;
            double up=(.1+1.8*h(key,seed,42))*x[2],side=(h(key,seed,43)-.5)*1.3*x[1];
            double wide=(.3+.8*h(key,seed,44))*x[1]/XSlashRules.HALF0,high=.035+.05*h(key,seed,45);
            Vec3d p=w.at(x[0]-.06,side,up),r=w.right().multiply(wide),u=new Vec3d(0,high,0);
            double cpick=h(key,seed,46);int rgb=cpick<.35?PURPLE:cpick<.55?PINK:cpick<.72?CYAN:cpick<.86?LILAC:INK;
            float near=GlitchFx.near(p,c.camera)*a;
            if(light){if(rgb!=INK)m.quad(p.subtract(r).subtract(u.multiply(2.5)),p.add(r).subtract(u.multiply(2.5)),p.add(r).add(u.multiply(2.5)),p.subtract(r).add(u.multiply(2.5)),rgb==LILAC?GLOW:rgb,.22f*near);}
            else m.quad(p.subtract(r).subtract(u),p.add(r).subtract(u),p.add(r).add(u),p.subtract(r).add(u),rgb,.85f*near);
        }
    }

    // ───────────────────────────── as it goes ─────────────────────────────

    static void launch(Fx c,EffectMesh m,XSlash.Way w,float t,int seed,float own,boolean light){
        float s=t-FIRE;if(s<0||s>.45f)return;
        Vec3d ctr=heart(w,XSlashRules.NEAR+.4,XSlashRules.HALF0,XSlashRules.TALL0);
        // a ring blown out in its plane where it went from
        if(s<.24f){
            double rad=1.1+2.1*Ease.out(s,0,.22f);
            float fa=in(s,.02f)*(1-Ease.smooth(s,.06f,.24f))*own*GlitchFx.near(ctr,c.camera);
            if(light){circle(m,ctr,w.right(),UP,rad,.04,WHITE,.4f*fa);DanceBurst.band(m,ctr,w.right(),UP,rad,.45,GLOW,.14f*fa);}
            else DanceBurst.band(m,ctr,w.right(),UP,rad,.14,PURPLE,.5f*fa);
        }
        // streaks rushing back past them
        if(light)for(int j=0;j<14;j++){
            float born=.035f*(float)h(j,seed,51),sj=s-born,life=(float)(.13+.08*h(j,seed,52));if(sj<0||sj>=life)continue;
            double side=(h(j,seed,53)-.5)*2*XSlashRules.HALF0*.9,up=.3+2.6*h(j,seed,54),speed=26+16*h(j,seed,55),len=1.4+1.8*h(j,seed,56);
            double fw=XSlashRules.NEAR+.6-speed*sj;
            Vec3d head=w.at(fw,side,up),tail=w.at(fw+len,side,up);
            float fa=in(sj,.02f)*(1-Ease.smooth(sj,life*.5f,life))*own*GlitchFx.near(head,c.camera);if(fa<=0)continue;
            m.softLine(head,tail,.09,LILAC,.4f*fa);if(j%3==0)m.line(head,tail,.02,WHITE,.6f*fa);
        }
        // the palm that sent it
        if(light&&s<.16f){
            Vec3d hand=KagenoClips.freeHand(c.pos,c.yaw,R6.poseOf(XSlash.CLIP.frame(t)));
            float fa=in(s,.015f)*(1-Ease.smooth(s,.05f,.16f))*own*GlitchFx.near(hand,c.camera);
            m.softDisc(hand,.85,GLOW,.45f*fa);m.star(hand,.42,WHITE,.55f*fa);
        }
        // pixels thrown on from where it went
        GlitchFx.pixels(m,ctr,w.f(),.55,7,.06,16,seed+71,s,light,c.camera);
    }

    // ───────────────────────────── flying ─────────────────────────────

    /** Echoes of the flying X behind it: how many, how far apart (seconds), their colours. */
    static final int ECHOES=4;static final float ECHO_GAP=.028f;
    static final int[] ECHO_RGB={PINK,CYAN,LILAC,PURPLE};
    /** (The light) its cutting edges a few hundredths of a second ago, each jumping a little to the side. */
    static void echoes(Fx c,EffectMesh m,XSlash.Way w,float t,float stop,int seed,float own){
        int step=(int)Math.floor(t*30);
        for(int k=1;k<=ECHOES;k++){
            float tk=t-k*ECHO_GAP;if(tk<FIRE)break;
            double[] x=XSlash.shape(tk,w.reach(),stop);if(x==null)continue;
            float fa=(float)(1-x[4])*own*(.46f-.085f*k);if(fa<=.01f)continue;
            Vec3d off=w.right().multiply((h(step+k*7,seed,61)-.5)*.32);
            for(int a=0;a<2;a++){
                var sw=XSlash.arm(w,x[0],x[1],x[2],a,1);
                for(int i=0;i+2<sw.n();i+=2){
                    float kk=(float)Math.sin(Math.PI*sw.along()[i+1]);
                    m.softLine(sw.outer()[i].add(off),sw.outer()[i+2].add(off),.17+.12*kk,ECHO_RGB[k-1],fa*(.3f+.7f*kk)*GlitchFx.near(sw.outer()[i],c.camera));
                }
            }
        }
    }
    /** (The light) lightning crawling over its arms, two to each, new every 1/24 second. */
    static void arcs(Fx c,EffectMesh m,XSlash.Way w,double[] x,float t,int seed,float own){
        int step=(int)Math.floor(t*24);float fa=(float)(1-x[4])*own;if(fa<=.01f)return;
        for(int a=0;a<2;a++){
            Vec3d n=armNormal(w,x[1],x[2],a);
            for(int k=0;k<2;k++){
                int key=step*4+a*2+k;if(h(key,seed,71)<.22)continue;
                double u0=.08+.6*h(key,seed,72),u1=Math.min(.95,u0+.15+.25*h(key,seed,73));
                Vec3d p0=armPoint(w,x[0],x[1],x[2],a,u0,.6+.4*h(key,seed,74)),p1=armPoint(w,x[0],x[1],x[2],a,u1,.6+.4*h(key,seed,75));
                Vec3d last=p0;
                for(int i=1;i<=6;i++){
                    double q=i/6.0,bow=Math.sin(Math.PI*q);
                    Vec3d p=p0.lerp(p1,q).add(n.multiply(i==6?0:(h(key*13+i,seed,76)-.5)*.55*bow)).add(w.f().multiply(i==6?0:(h(key*17+i,seed,77)-.5)*.3*bow));
                    float near=GlitchFx.near(p,c.camera)*fa;
                    m.softLine(last,p,.15,LILAC,.3f*near);m.line(last,p,.028,WHITE,.75f*near);
                    last=p;
                }
            }
        }
    }
    /** A ring left in its wake every this many blocks of its way. */
    static final double WAKE_EVERY=4.5;
    static void wakes(Fx c,EffectMesh m,XSlash.Way w,float t,float stop,float own,boolean light){
        if(!Float.isNaN(stop)&&stop<FIRE)return;
        double reach=w.reach();
        for(int k=1;;k++){
            double fw=XSlashRules.NEAR+WAKE_EVERY*k;if(fw>reach-.4)break;
            float at=FIRE+(float)XSlashRules.reached(fw)/20f,s=t-at;if(s<0||s>.26f)continue;
            double half=XSlashRules.half(fw),tall=XSlashRules.tall(fw),g=.55+.6*Ease.out(s,0,.24f);
            Vec3d ctr=w.at(fw,0,tall);float fa=in(s,.015f)*(1-Ease.smooth(s,.06f,.26f))*own*GlitchFx.near(ctr,c.camera);if(fa<=0)continue;
            if(light)ellipse(m,ctr,w.right(),UP,half*g,tall*g,.035,LILAC,.28f*fa,false);
            else ellipse(m,ctr,w.right(),UP,half*g,tall*g,.07,PURPLE,.3f*fa,false);
        }
    }
    /** Sparks ground off each of its lower horns on the floor as it flies (k 0: the left, 1: the right). */
    static final int HORN_SPARKS=22;
    static float hornBorn(XSlash.Way w,int k,int j,int seed){
        double fl=XSlashRules.flight(w.reach())/20.0;return FIRE+(float)(fl*(j+h(j,seed+k,81))/HORN_SPARKS);
    }
    static float hornLife(int k,int j,int seed){return (float)(.22+.18*h(j,seed+k,82));}
    /** Where horn spark {@code j} on side {@code k} is {@code sec} seconds in (null before or once gone): off where the horn was as it set off, back, out and up, falling. */
    static Vec3d hornSpark(XSlash.Way w,int k,int j,int seed,float sec){
        float born=hornBorn(w,k,j,seed),s=sec-born;if(s<0||s>=hornLife(k,j,seed))return null;
        double fw=Math.min(w.reach(),XSlashRules.head((born-FIRE)*20)),half=XSlashRules.half(fw);
        Vec3d p0=w.at(fw,(k==0?-half:half)*.97,.08);
        Vec3d v=w.f().multiply(-(2+4*h(j,seed+k,83))).add(w.right().multiply((k==0?-1:1)*(.8+2.2*h(j,seed+k,84)))).add(0,2+3.5*h(j,seed+k,85),0);
        double drag=3,travel=(1-Math.exp(-drag*s))/drag;
        return p0.add(v.multiply(travel)).add(0,-7*s*s,0);
    }
    static void hornSparks(Fx c,EffectMesh m,XSlash.Way w,float t,int seed,float own,boolean light){
        for(int k=0;k<2;k++)for(int j=0;j<HORN_SPARKS;j++){
            float born=hornBorn(w,k,j,seed),s=t-born;Vec3d p=hornSpark(w,k,j,seed,t);if(p==null)continue;
            Vec3d q=hornSpark(w,k,j,seed,Math.max(born,t-.022f));if(q==null)q=p;
            float life=hornLife(k,j,seed),fa=in(s,.01f)*(1-Ease.smooth(s,life*.5f,life))*own*GlitchFx.near(p,c.camera);if(fa<=0)continue;
            if(light){m.softLine(q,p,.11,GLOW,.4f*fa);m.line(q,p,.02,j%2==0?WHITE:LILAC,.7f*fa);}
            else m.line(q,p,.04,j%3==0?LILAC:PURPLE,.85f*fa);
        }
    }

    // ───────────────────────────── where its way ends ─────────────────────────────

    /** The X broken where its way ends: how many shards. */
    static final int SHARDS=26;
    /** One shard: where it is, the two ways it lies along, how big, how strongly it shows, its colour. */
    record Shard(Vec3d at,Vec3d e1,Vec3d e2,double size,float alpha,int rgb){}
    static float shardLife(int j,int seed){return (float)(.6+.4*h(j,seed,91));}
    /** Shard {@code j} of an X that went as far as {@code reach}, {@code s} seconds after its way ended (null before or once gone): off its arms, flung on and out, tumbling, falling, shrinking. */
    static Shard shard(XSlash.Way w,int j,int seed,float s){
        float life=shardLife(j,seed);if(s<0||s>=life)return null;
        int a=j%2;double u=(j/2+.5)/(SHARDS/2.0),fw=w.reach(),half=XSlashRules.half(fw),tall=XSlashRules.tall(fw);
        Vec3d p0=armPoint(w,fw,half,tall,a,u,h(j,seed,92));
        Vec3d v=w.f().multiply(3.5+5*h(j,seed,93)).add(armNormal(w,half,tall,a).multiply(1.5+3.5*h(j,seed,94))).add(anyWay(j,seed,95).multiply(2)).add(0,1.5*h(j,seed,98),0);
        double drag=2.2,travel=(1-Math.exp(-drag*s))/drag;
        Vec3d at=p0.add(v.multiply(travel)).add(0,-6*s*s,0);
        Vec3d axis=anyWay(j,seed,99),any=Math.abs(axis.y)<.9?UP:new Vec3d(1,0,0),U=axis.crossProduct(any).normalize(),V=axis.crossProduct(U);
        double th=Math.PI*2*h(j,seed,102)+(8+10*h(j,seed,103))*s*(h(j,seed,104)<.5?-1:1);
        Vec3d e1=U.multiply(Math.cos(th)).add(V.multiply(Math.sin(th)));
        double size=(.2+.25*h(j,seed,105))*(1-Ease.smooth(s,life*.6f,life));
        float alpha=s>life*.8f?((int)Math.floor(s*60+j)%2==0?1:.35f):1;
        double cpick=h(j,seed,106);int rgb=cpick<.45?PURPLE:cpick<.7?DEEP:cpick<.88?LILAC:PINK;
        return new Shard(at,e1,axis,size,alpha,rgb);
    }
    static void end(Fx c,EffectMesh m,XSlash.Way w,float t,int seed,float own,boolean light){
        float end=XSlash.flown(w.reach()),s=t-end;if(s<0||s>1.05f)return;
        for(int j=0;j<SHARDS;j++){
            var sh=shard(w,j,seed,s);if(sh==null||sh.size()<=.01)continue;
            float near=GlitchFx.near(sh.at(),c.camera)*sh.alpha()*own;if(near<=0)continue;
            Vec3d p0=sh.at().add(sh.e1().multiply(sh.size())),p1=sh.at().subtract(sh.e1().multiply(sh.size()*.5)).add(sh.e2().multiply(sh.size()*.7)),p2=sh.at().subtract(sh.e1().multiply(sh.size()*.4)).subtract(sh.e2().multiply(sh.size()*.55));
            if(light){m.line(p0,p1,.025,LILAC,.55f*near);m.line(p1,p2,.025,LILAC,.55f*near);m.line(p2,p0,.025,WHITE,.5f*near);m.softDisc(sh.at(),sh.size()*1.6,GLOW,.18f*near);}
            else{
                Vec3d o=sh.at();double g=1.22;
                m.triangle(o.add(p0.subtract(o).multiply(g)),o.add(p1.subtract(o).multiply(g)),o.add(p2.subtract(o).multiply(g)),INK,.85f*near);
                m.triangle(p0,p1,p2,sh.rgb(),.92f*near);
            }
        }
        // a flash where it ends, and a ring blown out in its plane
        Vec3d o=heart(w,w.reach(),XSlashRules.half(w.reach()),XSlashRules.tall(w.reach()));float near=GlitchFx.near(o,c.camera)*own;
        if(light){
            if(s<.14f){m.softDisc(o,2.3,GLOW,.45f*in(s,.015f)*(1-s/.14f)*near);m.star(o,1.0,WHITE,.55f*in(s,.015f)*(1-s/.14f)*near);}
            if(s<.3f){double g=1+.5*Ease.out(s,0,.28f);float fa=in(s,.015f)*(1-Ease.smooth(s,.08f,.3f))*near;
                Vec3d mid=w.at(w.reach(),0,XSlashRules.tall(w.reach()));
                ellipse(m,mid,w.right(),UP,XSlashRules.half(w.reach())*g,XSlashRules.tall(w.reach())*g,.04,LILAC,.25f*fa,false);
                ellipse(m,mid,w.right(),UP,XSlashRules.half(w.reach())*g,XSlashRules.tall(w.reach())*g,.45,GLOW,.1f*fa,true);}
        }
    }

    // ───────────────────────────── on whom it strikes ─────────────────────────────

    /** How long what the X leaves on whom it strikes lasts (seconds). */
    static final float MARK_LIFE=.36f;
    /** How big the X struck across them is, half of each of its strokes (blocks): out quickly, then holding. */
    static double markSize(float s){return .42+.5*Ease.out(s,0,.07f);}
    static void struck(Fx c,EffectMesh m,XSlash.Way w,Vec3d mid,double halfWidth,double height,float s,int seed,boolean light){
        if(s<0||s>=MARK_LIFE)return;
        Vec3d p=mid.subtract(w.f().multiply(halfWidth+.2));
        double sz=markSize(s);float fa=in(s,.015f)*(1-Ease.smooth(s,.1f,MARK_LIFE))*GlitchFx.near(p,c.camera);if(fa<=0)return;
        Vec3d d1=w.right().multiply(sz).add(0,sz,0),d2=w.right().multiply(-sz).add(0,sz,0);
        if(light){
            m.softLine(p.add(d1),p.subtract(d1),.5,GLOW,.36f*fa);m.softLine(p.add(d2),p.subtract(d2),.5,GLOW,.36f*fa);
            m.line(p.add(d1),p.subtract(d1),.035,WHITE,.9f*fa);m.line(p.add(d2),p.subtract(d2),.035,WHITE,.9f*fa);
        }else{
            m.line(p.add(d1),p.subtract(d1),.17,INK,.75f*fa);m.line(p.add(d2),p.subtract(d2),.17,INK,.75f*fa);
            m.line(p.add(d1),p.subtract(d1),.1,PURPLE,.95f*fa);m.line(p.add(d2),p.subtract(d2),.1,PURPLE,.95f*fa);
        }
        // bars of them torn sideways for a moment
        if(s<.2f){
            var fc=GlitchFx.facing(c.camera,p);int step=(int)Math.floor(s*30);
            for(int i=0;i<5;i++){
                int key=step*5+i;if(h(key,seed,151)<.25)continue;
                Vec3d q=p.add(0,(h(key,seed,152)-.55)*height*.9,0).add(fc[0].multiply((h(key,seed,153)-.5)*.5));
                double hw=(.25+.45*h(key,seed,154))*Math.max(.6,halfWidth*2.4),hh=.03+.04*h(key,seed,155);
                double cpick=h(key,seed,156);int rgb=cpick<.4?PURPLE:cpick<.6?PINK:cpick<.8?CYAN:INK;
                float ta=fa*(1-Ease.smooth(s,.12f,.2f));
                if(light){if(rgb!=INK)GlitchFx.bar(m,q,fc,hw,hh*2.4,rgb,.22f*ta);}
                else GlitchFx.bar(m,q,fc,hw,hh,rgb,.85f*ta);
            }
        }
        GlitchFx.pixels(m,p,w.f(),.85,5.5,.055,14,seed+11,s,light,c.camera);
    }

    // ───────────────────────────── the X on the ground ─────────────────────────────

    /** Cracks out to either side of each diagonal as the X goes over it: how many to each. */
    static final int CRACKS=9;
    /** Crack {@code k} off diagonal {@code a}: {how far along it (0 to 1), which side (±1), how far off it (radians), how long (blocks)}. */
    static double[] crack(int a,int k,int seed){
        return new double[]{(k+.5+.5*(h(k,seed+a,111)-.5))/CRACKS,k%2==0?1:-1,Math.toRadians(35+40*h(k,seed+a,112)),1.0+1.2*h(k,seed+a,113)};
    }
    /** How far crack {@code k} off diagonal {@code a} has grown {@code sec} seconds in (0 to 1): out quickly as the X goes over its root. */
    static float crackGrown(XSlash.Way w,int a,int k,int seed,float sec){
        double u=crack(a,k,seed)[0];var raw=XSlashRules.diagonals(w.reach())[a];
        double fw=raw[0]+(raw[2]-raw[0])*u;float over=FIRE+(float)XSlashRules.reached(fw)/20f;
        return Ease.out(sec,over,over+.1f);
    }
    static void cracks(Fx c,EffectMesh m,XSlash.Way w,Vec3d[][] ds,double[][] g,float t,float shown,float glow,int seed,boolean light){
        var raw=XSlashRules.diagonals(w.reach());
        for(int a=0;a<2;a++){
            Vec3d a0=ds[a][0],dir=ds[a][1].subtract(a0),dh=new Vec3d(dir.x,0,dir.z).normalize(),across=new Vec3d(-dh.z,0,dh.x);
            for(int k=0;k<CRACKS;k++){
                float grown=crackGrown(w,a,k,seed,t);if(grown<=.01f)continue;
                double[] cr=crack(a,k,seed);double y=XSlash.lerpGround(g[a],cr[0])+.045;
                Vec3d root=a0.add(dir.multiply(cr[0]));root=new Vec3d(root.x,y,root.z);
                Vec3d way=dh.multiply(Math.cos(cr[2])*(h(k,seed+a,114)<.5?1:-1)).add(across.multiply(cr[1]*Math.sin(cr[2])));
                Vec3d side=new Vec3d(-way.z,0,way.x);
                Vec3d last=root;float hot=XSlash.burnt(t,raw[a][0]+(raw[a][2]-raw[a][0])*cr[0]);
                for(int i=1;i<=4;i++){
                    double q=i/4.0*grown,jag=i==4?0:(h(k*7+i,seed+a,115)-.5)*.3;
                    Vec3d p=root.add(way.multiply(cr[3]*q)).add(side.multiply(jag));
                    double taper=1-.6*i/4.0;float near=GlitchFx.near(p,c.camera)*shown;
                    if(light){m.softLine(last,p,.4*taper,GLOW,.32f*glow*near);m.line(last,p,.026*taper,WHITE,(.25f+.4f*hot)*glow*near);}
                    else{m.line(last,p,.11*taper,INK,.85f*near);m.line(last,p,.055*taper,hot>.3f?LILAC:PURPLE,.95f*near);}
                    last=p;
                }
            }
        }
    }
    /** Data rising off the X on the ground while it waits: so many to each diagonal, each this long rising (seconds). */
    static final int MOTES=18;static final float MOTE_PERIOD=.6f;
    static void motes(Fx c,EffectMesh m,XSlash.Way w,Vec3d[][] ds,double[][] g,float t,float shown,float charge,int seed,boolean light){
        float from=XSlash.flown(w.reach()),b=XSlash.burst(w.reach());if(t<from||t>b+.05f)return;
        float gone=1-Ease.smooth(t,b,b+.05f);
        for(int a=0;a<2;a++){
            Vec3d a0=ds[a][0],dir=ds[a][1].subtract(a0),across=new Vec3d(-dir.z,0,dir.x).normalize();
            for(int j=0;j<MOTES;j++){
                float q=(t-from)/MOTE_PERIOD+(float)h(j,seed+a,121);int n=(int)Math.floor(q);float s=(q-n)*MOTE_PERIOD;
                int key=n*31+j;if(h(key,seed+a,122)>.4+.6*charge)continue;
                double u=h(key,seed+a,123),side=(h(key,seed+a,124)-.5)*1.6*XSlashRules.BAND;
                Vec3d base=a0.add(dir.multiply(u)).add(across.multiply(side));
                Vec3d p=new Vec3d(base.x,XSlash.lerpGround(g[a],u)+.05+s*(1.1+.9*h(key,seed+a,125))*(1+.8*charge),base.z);
                float fa=Ease.smooth(s,0,.08f)*(1-Ease.smooth(s,MOTE_PERIOD*.55f,MOTE_PERIOD))*shown*gone*GlitchFx.near(p,c.camera);if(fa<=0)continue;
                var fc=GlitchFx.facing(c.camera,p);double size=.04+.035*h(key,seed+a,126);
                double cpick=h(key,seed+a,127);int rgb=cpick<.5?PURPLE:cpick<.75?LILAC:cpick<.9?CYAN:WHITE;
                if(light)GlitchFx.square(m,p,fc,size*2.2,rgb==WHITE||rgb==LILAC?GLOW:rgb,.3f*fa);
                else{GlitchFx.square(m,p,fc,size+.012,INK,.8f*fa);GlitchFx.square(m,p,fc,size,rgb,fa);}
            }
        }
    }
    /** (The light) its light running in along its lines to where they cross over the last of the wait. */
    static final float CONVERGE=.42f;
    static void converge(Fx c,EffectMesh m,XSlash.Way w,Vec3d[][] ds,double[][] g,float t,int seed){
        float b=XSlash.burst(w.reach()),s=t-(b-CONVERGE);if(s<0||t>=b)return;
        float a=Ease.smooth(s,0,.12f);
        for(int d=0;d<2;d++){
            Vec3d a0=ds[d][0],dir=ds[d][1].subtract(a0);
            for(int j=0;j<6;j++){
                double end=j%2==0?0:1,q=(s*2.4+h(j,seed+d,131))%1.0;
                double u=end+(.5-end)*q,u1=end+(.5-end)*Math.max(0,q-.12);
                Vec3d p=a0.add(dir.multiply(u)),p1=a0.add(dir.multiply(u1));
                p=new Vec3d(p.x,XSlash.lerpGround(g[d],u)+.12,p.z);p1=new Vec3d(p1.x,XSlash.lerpGround(g[d],u1)+.12,p1.z);
                float fa=a*(float)Math.sin(Math.PI*q)*GlitchFx.near(p,c.camera);if(fa<=0)continue;
                m.softLine(p1,p,.3,GLOW,.4f*fa);m.line(p1,p,.03,WHITE,.55f*fa);
            }
        }
    }

    // ───────────────────────────── bursting ─────────────────────────────

    /** Pillars of light shot up along each diagonal as it bursts, one every this far (blocks), from where its lines cross outward (seconds apart at most), each this long. */
    static final double PILLAR_EVERY=1.7;static final float PILLAR_STAGGER=.12f,PILLAR_LIFE=.5f;
    /** How many pillars along a diagonal this long. */
    static int pillars(double len){return Math.max(4,(int)Math.floor(len/PILLAR_EVERY));}
    /** How long after the burst pillar {@code j} of {@code n} along a diagonal goes up: from where the lines cross (its middle) outward. */
    static float pillarDelay(int j,int n){double u=(j+.5)/n;return (float)(Math.abs(u-.5)*2*PILLAR_STAGGER);}
    /** How tall pillar {@code j} is {@code s} seconds after it goes up (blocks): up quickly. */
    static double pillarTall(int j,int seed,float s){return (4.5+3.5*h(j,seed,141))*Ease.out(s,0,.07f);}
    static void pillars(Fx c,EffectMesh m,Vec3d[][] ds,double[][] g,float s,int seed){
        for(int a=0;a<2;a++){
            Vec3d a0=ds[a][0],dir=ds[a][1].subtract(a0);int n=pillars(dir.horizontalLength());
            for(int j=0;j<n;j++){
                float sj=s-pillarDelay(j,n);if(sj<0||sj>PILLAR_LIFE)continue;
                double u=(j+.5)/n;Vec3d base=a0.add(dir.multiply(u));base=new Vec3d(base.x,XSlash.lerpGround(g[a],u),base.z);
                double tall=pillarTall(j,seed+a*41,sj),wide=.85*(1-.55*Ease.smooth(sj,.05f,PILLAR_LIFE));
                float fa=in(sj,.015f)*(1-Ease.smooth(sj,.1f,PILLAR_LIFE))*GlitchFx.near(base.add(0,tall*.5,0),c.camera);if(fa<=0)continue;
                Vec3d top=base.add(0,tall,0);
                m.softTaper(base,top,wide,wide*.15,GLOW,.34f*fa);
                m.line(base.add(0,.05,0),base.add(0,tall*.9,0),.045,WHITE,.5f*fa);
                m.softDisc(base.add(0,.15,0),.95,LILAC,.22f*fa);
            }
        }
    }
    /** (The light) the whole of each line again, a wide purple band, as it bursts. */
    static void band(Fx c,EffectMesh m,Vec3d[][] ds,double[][] g,float s){
        float fa=in(s,.02f)*(1-Ease.smooth(s,.06f,.5f));if(fa<=.01f)return;
        for(int a=0;a<2;a++){
            Vec3d a0=ds[a][0],dir=ds[a][1].subtract(a0),across=new Vec3d(-dir.z,0,dir.x).normalize();
            int n=XSlash.GROUND_SAMPLES;
            for(int i=0;i<n;i++){
                double u0=i/(double)n,u1=(i+1)/(double)n;
                Vec3d p0=a0.add(dir.multiply(u0)),p1=a0.add(dir.multiply(u1));
                p0=new Vec3d(p0.x,XSlash.lerpGround(g[a],u0)+.05,p0.z);p1=new Vec3d(p1.x,XSlash.lerpGround(g[a],u1)+.05,p1.z);
                double taper=Math.min(1,Math.min(u0,1-u1)*10+.3);
                XSlash.strip(m,p0,p1,across,XSlashRules.BAND*2.2*taper,GLOW,0,.36f*fa*GlitchFx.near(p0,c.camera));
            }
        }
    }
    /** Embers rising off the lines after it bursts: how many, and how long after it they are all gone (seconds). */
    static final int EMBERS=40;static final float AFTER=1.4f;
    static float emberLife(int j,int seed){return (float)(.6+.6*h(j,seed,161));}
    static float emberBorn(int j,int seed){return (float)(.15*h(j,seed,162));}
    static void embers(Fx c,EffectMesh m,Vec3d[][] ds,double[][] g,float s,int seed,boolean light){
        if(s<0||s>AFTER)return;
        for(int j=0;j<EMBERS;j++){
            float sj=s-emberBorn(j,seed),life=emberLife(j,seed);if(sj<0||sj>=life)continue;
            int a=j%2;double u=h(j,seed,163),side=(h(j,seed,164)-.5)*1.6*XSlashRules.BAND;
            Vec3d a0=ds[a][0],dir=ds[a][1].subtract(a0),across=new Vec3d(-dir.z,0,dir.x).normalize();
            Vec3d base=a0.add(dir.multiply(u)).add(across.multiply(side+.18*Math.sin(sj*3.2+6*h(j,seed,165))));
            Vec3d p=new Vec3d(base.x,XSlash.lerpGround(g[a],u)+.12+sj*(1.2+1.4*h(j,seed,166)),base.z);
            float fa=Ease.smooth(sj,0,.08f)*(1-Ease.smooth(sj,life*.6f,life))*GlitchFx.near(p,c.camera);if(fa<=0)continue;
            var fc=GlitchFx.facing(c.camera,p);double size=.03+.025*h(j,seed,167);
            if(light){m.softDisc(p,size*4,GLOW,.4f*fa);if(j%4==0)m.star(p,size*3.2,WHITE,.4f*fa);}
            else GlitchFx.square(m,p,fc,size,j%3==0?LILAC:PURPLE,.9f*fa);
        }
    }
    /** As it bursts: the pillars, the band, the embers (both passes; the band and the pillars are light). */
    static void burst(Fx c,EffectMesh m,XSlash.Way w,Vec3d[][] ds,double[][] g,float t,int seed,boolean light){
        float s=t-XSlash.burst(w.reach());if(s<0||s>AFTER)return;
        if(light){pillars(c,m,ds,g,s,seed);band(c,m,ds,g,s);}
        embers(c,m,ds,g,s,seed,light);
    }

    /** Pieces of the ground thrown up along each diagonal as it bursts: how many to each. */
    static final int DEBRIS=14;
    /** The ground itself as it bursts: the blocks under its lines heaved up and settling, pieces of them thrown (drawn only: no block is changed). */
    static void solids(Fx c,FxSolids so,XSlash.Way w,float t){
        if(w.told()!=null&&w.told().gone)return;
        float stop=KnifeClient.until(c.cast.id());if(stop!=Float.MAX_VALUE&&stop<FIRE)return;
        float s=t-XSlash.burst(w.reach());if(s<0||s>=AFTER)return;
        var ds=XSlash.diagonals(w);int seed=XSlash.seed(c.cast);
        Set<BlockPos> done=new HashSet<>();
        for(int a=0;a<2;a++){
            Vec3d a0=ds[a][0],dir=ds[a][1].subtract(a0),dh=new Vec3d(dir.x,0,dir.z).normalize(),across=new Vec3d(-dh.z,0,dh.x);
            double len=dir.horizontalLength();
            // heaved: up at once, settling, from where its lines cross outward
            int n=(int)Math.ceil(len/.9);
            for(int i=0;i<=n;i++){
                double u=i/(double)n;float sj=s-(float)(Math.abs(u-.5)*2*PILLAR_STAGGER);
                double lift=.22*Ease.out(sj,0,.05f)*(1-Ease.smooth(sj,.08f,.5f));if(lift<=.02)continue;
                Vec3d p=a0.add(dir.multiply(u));double top=so.groundY(p.add(0,1,0));
                BlockPos pos=BlockPos.ofFloored(p.x,top-.5,p.z);if(!done.add(pos))continue;
                var state=so.surface(pos);if(state==null)continue;
                so.lifted(pos,state,lift,(float)((h(i,seed+a,171)-.5)*18),dh);
            }
            // thrown
            for(int j=0;j<DEBRIS;j++){
                double u=(j+.5+.6*(h(j,seed+a,172)-.5))/DEBRIS;float sj=s-(float)(Math.abs(u-.5)*2*PILLAR_STAGGER);if(sj<0)continue;
                Vec3d p=a0.add(dir.multiply(u));double top=so.groundY(p.add(0,1,0));
                var state=so.ground(new Vec3d(p.x,top-.5,p.z));if(state==null)continue;
                double side=(h(j,seed+a,173)-.5)*2,size=(.16+.16*h(j,seed+a,174))*(1-Ease.smooth(sj,1f,1.35f));if(size<=.02)continue;
                Vec3d v=across.multiply(side*(1+2*h(j,seed+a,175))).add(dh.multiply((h(j,seed+a,176)-.5)*1.5)).add(0,5+4.5*h(j,seed+a,177),0);
                so.chunk(state,FxSolids.flight(new Vec3d(p.x,top+.1,p.z),v,top+size*.5,sj),size,(float)(sj*(300+400*h(j,seed+a,178))),(float)(sj*250),(float)(j*37));
            }
        }
    }
    /** Dust thrown up as it bursts (along its lines, and round where they cross): points and puffs for the effect layer's anime dust. */
    static void dust(XSlash.Way w,double[][] g,float t,int seed,List<Vec3d> dust,List<float[]> puffs){
        float b=XSlash.burst(w.reach());if(t<b||t>b+1.2f)return;
        var ds=XSlash.diagonals(w);
        for(int a=0;a<2;a++){
            Vec3d a0=ds[a][0],dir=ds[a][1].subtract(a0);
            for(int j=0;j<5;j++){
                double u=(j+.5)/5;Vec3d p=a0.add(dir.multiply(u));p=new Vec3d(p.x,XSlash.lerpGround(g[a],u),p.z);
                dev.sangwoo.celestialfall.client.kageno.FxLib.dustRing(p,t,b+(float)(Math.abs(u-.5)*2*PILLAR_STAGGER),.15,.95,4,.6,dust,puffs,seed+211+a*31+j*7);
            }
        }
        Vec3d cross=ds[0][0].add(ds[0][1]).multiply(.5);cross=new Vec3d(cross.x,XSlash.lerpGround(g[0],.5),cross.z);
        dev.sangwoo.celestialfall.client.kageno.FxLib.dustRing(cross,t,b,.4,2.8,10,.8,dust,puffs,seed+271);
    }

    // ───────────────────────────── drawing helpers ─────────────────────────────

    /** A line lying flat (its width level, square to it). */
    static void flat(EffectMesh m,Vec3d a,Vec3d b,double half,int rgb,float alpha){
        if(alpha<=0)return;Vec3d d=b.subtract(a);Vec3d s=new Vec3d(-d.z,0,d.x);if(s.lengthSquared()<1e-10)return;s=s.normalize().multiply(half);
        m.quad(a.subtract(s),b.subtract(s),b.add(s),a.add(s),rgb,alpha);
    }
    /** A thin circle in the plane of {@code u} and {@code v}, of flat lines (lying in the plane). */
    static void flatCircle(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double r,double half,int rgb,float alpha){
        if(alpha<=0||r<=0)return;int n=Math.max(24,Math.min(72,(int)(r*14)));
        Vec3d last=c.add(u.multiply(r));
        for(int i=1;i<=n;i++){double a=i*Math.PI*2/n;Vec3d p=c.add(u.multiply(Math.cos(a)*r)).add(v.multiply(Math.sin(a)*r));
            Vec3d d=p.subtract(last),s=u.crossProduct(v).crossProduct(d);if(s.lengthSquared()>1e-12){s=s.normalize().multiply(half);m.quad(last.subtract(s),p.subtract(s),p.add(s),last.add(s),rgb,alpha);}
            last=p;}
    }
    /** A thin circle facing the camera along its line (each piece a camera-facing line). */
    static void circle(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double r,double width,int rgb,float alpha){ellipse(m,c,u,v,r,r,width,rgb,alpha,false);}
    /** An ellipse in the plane of {@code u} and {@code v}: of camera-facing lines, or of soft ones. */
    static void ellipse(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double rx,double ry,double width,int rgb,float alpha,boolean soft){
        if(alpha<=0)return;int n=44;Vec3d last=c.add(u.multiply(rx));
        for(int i=1;i<=n;i++){double a=i*Math.PI*2/n;Vec3d p=c.add(u.multiply(Math.cos(a)*rx)).add(v.multiply(Math.sin(a)*ry));
            if(soft)m.softLine(last,p,width,rgb,alpha);else m.line(last,p,width,rgb,alpha);last=p;}
    }
}
