package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.xchara.XSlashRules;
import net.minecraft.util.math.Vec3d;

/**
 * The great X slash's slash itself, made anew (2.136.0, user request: "참격을 여기서 갈아엎고 최대로 멋있게 만들어줘", after
 * "참격을 더 멋있게"; 2.134.0's crescents and all that 2.135.0 and 2.135.1 piled on them are gone: the spikes, the echo
 * blades, the aura and bloom, the swept sheet, the split lines, the glints, the lightning, the rings of its wake).
 * Mine to make up; server rules, times and reach as they were.
 * <ul>
 * <li>Each arm is a blade, not a bowed crescent: a long lens pointed at both its horns (from the high horn to the foot
 * on the ground), its cutting edge the upper one, so the two cross as a clean X.</li>
 * <li>The blade is a tear of black with neon about it (the shader's mode 18 style 3): an ink line and a white-hot core
 * on its cutting edge, lilac and a vivid purple under it ending in teeth of flame, then the void, black, a few pixels
 * of colour blinking in it, its back edge torn into long tongues each rimmed in hot magenta, loose pixels beyond. As
 * it is cut it is all light for a moment (white, lilac, purple: {@link #fresh}); the void comes in from its back as it
 * cools. A soft purple halo stands about it (style 6).</li>
 * <li>As it is cut it springs open from a line ({@link #open}), and each cut's colours split for a moment, pink to one
 * side and cyan to the other (style 5).</li>
 * <li>Flying, its horns sweep back ({@link #SWEEP}: it bends under its own speed, and from its side it is a crescent,
 * not a hairline), a sheet of streaks streams back off each cutting edge (its wake, style 4), and three echoes of it
 * follow, pink, cyan and purple.</li>
 * <li>About it ({@link #draw}): the line of each cut drawn through the air a moment ahead of its blade, out past its
 * high horn, left hanging where it was cut as the X goes and splitting apart as it fades; a star where the two cross;
 * the trails of its four horns and of its heart down the way it flew.</li>
 * </ul>
 * Everything a function of the skill's clock and the server's way.
 */
final class XBlade {
    private XBlade(){}
    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE,PINK=GlitchFx.PINK,CYAN=GlitchFx.CYAN;
    static final float FIRE=XSlash.FIRE;
    /** Each arm's lens: how far its cutting edge and its back edge stand off the line between its horns at its belly (of that line's length; its back the other way), and how its depth swells from horn to belly (a power of the sine). */
    static final double OUTER=.105,INNER=-.095,BELLY=1.15;
    /** Samples along an arm. */
    static final int SAMPLES=40;
    /** Flying, its horns trail its middle by this much of an arm's length, grown over its first blocks. */
    static final double SWEEP=.13,SWEEP_OVER=2.5;
    /** Its wake: how far back the sheet off each cutting edge streams at an arm's middle, full grown (blocks). */
    static final double WAKE=3.4;
    /** Its halo: how much wider than the lens (each way), and how strong. */
    static final double HALO_OUT=2.2,HALO_IN=2.6,HALO_MORE=.02;static final float HALO=.55f;
    /** Its echoes as it flies: how long behind (seconds), which colour (the shader's: 1 pink, 2 cyan, 0 purple), how strong. */
    static final float[] ECHO_LAG={.022f,.044f,.07f};static final int[] ECHO_COLOUR={1,2,0};static final float[] ECHO_ALPHA={.4f,.3f,.22f};
    /** Each cut's colours split this far to either side as it is cut (blocks), going with its freshness. */
    static final double SPLIT=.12;
    /** How wide its cutting edge shows turned to the camera (blocks, of a full-grown X), where the camera sees it from its side. */
    static final double EDGE=.3;
    /** As it is cut: how it springs open (a time constant and a rate, seconds and radians a second), and how long before and after its cut is whole its light lasts (seconds). */
    static final float OPEN_TAU=.03f,OPEN_RATE=45,FRESH_BEFORE=.02f,FRESH_AFTER=.1f;
    /** The line of each cut: drawn this long ahead of its blade, out this far past its high horn (of the arm's length), left hanging until this long after the X goes, its colours this far apart by then. */
    static final float LINE_LEAD=.02f,LINE_LINGER=.3f;static final double LINE_OVER=.4,LINE_APART=.14;
    /** The trails down its way: this long (seconds). */
    static final float TRAIL=.2f;

    static float from(int a){return a==0?XSlash.ARM1_FROM:XSlash.ARM2_FROM;}
    static float to(int a){return a==0?XSlash.ARM1_TO:XSlash.ARM2_TO;}
    /** How open a blade is {@code s} seconds after its cut began: from a line, a little past whole, settling (0 before). */
    static float open(float s){return s<=0?0:(float)(1-Math.exp(-s/OPEN_TAU)*Math.cos(s*OPEN_RATE));}
    /** How fresh arm {@code a} is {@code sec} seconds in (1 as it is cut, 0 once it has cooled). */
    static float fresh(int a,float sec){return 1-Ease.smooth(sec,to(a)-FRESH_BEFORE,to(a)+FRESH_AFTER);}
    /** How far its flight has grown it, {@code fw} ahead (0 as it goes, 1 from SWEEP_OVER on). */
    static double grown(double fw){return Ease.smooth((float)(fw-XSlashRules.NEAR),0,(float)SWEEP_OVER);}
    /** How big an X of this size is beside a full-grown one. */
    static double scale(double half,double tall){return Math.hypot(half,tall)/Math.hypot(XSlashRules.HALF,XSlashRules.TALL);}

    /**
     * Arm {@code a} of an X {@code fw} ahead, {@code half} wide and {@code tall} high (half of each), drawn as far as
     * {@code upTo} (0 to 1 from its high horn): a lens between its horns, its edges {@code in} and {@code out} of the
     * arm's length off the line between them at its belly (out the cutting edge, up; in the back edge), {@code open}
     * of that deep; its horns swept back {@code sweep} of its length. Drawn part way it is a lens still, to where the
     * knife is, slimmer. The first from the high right to the low left, the second from the high left to the low right.
     */
    static KnifeCombo.Swept arm(XSlash.Way w,double fw,double half,double tall,int a,float upTo,double open,double sweep,double in,double out){
        double sx=a==0?half:-half,sy=2*tall,ex=-sx,ey=0;
        double nx=a==0?-tall:tall,ny=half,nl=Math.hypot(nx,ny);nx/=nl;ny/=nl;
        double len=Math.hypot(ex-sx,ey-sy),deep=open*(.35+.65*upTo);
        int n=Math.max(2,(int)Math.ceil(SAMPLES*upTo)+1);
        Vec3d[] inner=new Vec3d[n],outer=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){
            double t=upTo*i/(n-1.0),cx=sx+(ex-sx)*t,cy=sy+(ey-sy)*t;
            double s=Math.pow(Math.max(0,Math.sin(Math.PI*i/(n-1.0))),BELLY)*deep,back=sweep*len*(2*t-1)*(2*t-1);
            inner[i]=w.at(fw-back,cx+nx*in*len*s,cy+ny*in*len*s);outer[i]=w.at(fw-back,cx+nx*out*len*s,cy+ny*out*len*s);along[i]=(float)t;
        }
        return new KnifeCombo.Swept(inner,outer,along,n);
    }
    /** The blade itself, whole and flat or as told. */
    static KnifeCombo.Swept arm(XSlash.Way w,double fw,double half,double tall,int a,float upTo,double open,double sweep){return arm(w,fw,half,tall,a,upTo,open,sweep,INNER,OUTER);}
    /** How far back the wake streams {@code t} along an arm of an X {@code fw} ahead and {@code half} wide (blocks): longest at its middle, none at its horns, grown with its flight. */
    static double wake(double fw,double half,double t){return WAKE*grown(fw)*(half/XSlashRules.HALF)*Math.pow(Math.max(0,Math.sin(Math.PI*t)),.6);}
    /** The sheet off a blade's cutting edge: from that edge (its outer) back the way it came. */
    static KnifeCombo.Swept wake(XSlash.Way w,KnifeCombo.Swept sw,double fw,double half){
        int n=sw.n();Vec3d[] back=new Vec3d[n];
        for(int i=0;i<n;i++)back[i]=sw.outer()[i].subtract(w.f().multiply(wake(fw,half,sw.along()[i])));
        return new KnifeCombo.Swept(back,sw.outer(),sw.along(),n);
    }
    private static KnifeCombo.Swept moved(KnifeCombo.Swept sw,Vec3d by){
        int n=sw.n();Vec3d[] in=new Vec3d[n],out=new Vec3d[n];
        for(int i=0;i<n;i++){in[i]=sw.inner()[i].add(by);out[i]=sw.outer()[i].add(by);}
        return new KnifeCombo.Swept(in,out,sw.along(),n);
    }
    private static void ribbon(KnifeCombo.Swept sw,int style,int seed,float eat,float fresh,float alpha){
        KagenoFire.glitchRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),style,seed,eat,fresh,alpha);
    }

    /** The X's blades (the effects' pass for X Chara's cuts): their halos, their wakes, their echoes, then the blades and their edges to the camera. */
    static void ribbons(Fx c){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());if(stop==Float.MAX_VALUE)stop=Float.NaN;
        var w=XSlash.way(c.cast);if(w.told()!=null&&w.told().gone)return;
        var x=XSlash.shape(t,w.reach(),stop);if(x==null)return;
        float own=KnifeCombo.own(c),eat=(float)x[4],tc=Float.isNaN(stop)?t:Math.min(t,stop);int seed=XSlash.seed(c.cast);
        double fw=x[0],half=x[1],tall=x[2];boolean flying=t>=FIRE&&(Float.isNaN(stop)||stop>=FIRE);
        double grow=flying?grown(fw):0,sweep=SWEEP*grow;
        var blades=new KnifeCombo.Swept[2];float[] shown=new float[2],fresh=new float[2],opened=new float[2];
        for(int a=0;a<2;a++){
            float p=XSlash.cut(a,tc);if(p<=.01f)continue;
            shown[a]=(float)x[3]*own*Ease.smooth(t,from(a),from(a)+.02f);opened[a]=open(tc-from(a));fresh[a]=fresh(a,t);
            blades[a]=arm(w,fw,half,tall,a,p,opened[a],sweep);
            var halo=arm(w,fw,half,tall,a,p,opened[a],sweep,INNER*HALO_IN-HALO_MORE,OUTER*HALO_OUT+HALO_MORE);
            ribbon(halo,6,a,eat,0,HALO*(1-.5f*fresh[a])*shown[a]);
        }
        for(int a=0;a<2;a++){
            if(blades[a]==null)continue;
            if(flying&&grow>.02)ribbon(wake(w,blades[a],fw,half),4,a*5+3,eat,0,.9f*(float)grow*shown[a]);
            if(flying)for(int g=ECHO_LAG.length-1;g>=0;g--){
                float tg=t-ECHO_LAG[g];if(tg<FIRE)continue;
                double f2=Math.min(w.reach(),XSlashRules.head((tg-FIRE)*20));
                ribbon(arm(w,f2,XSlashRules.half(f2),XSlashRules.tall(f2),a,1,1,SWEEP*grown(f2)),5,ECHO_COLOUR[g],eat,0,ECHO_ALPHA[g]*shown[a]*(1-eat));
            }else if(fresh[a]>.05f){
                // its colours split as it is cut: pink to one side, cyan to the other
                for(int k=0;k<2;k++){
                    double off=(k==0?SPLIT:-SPLIT)*fresh[a];
                    ribbon(moved(blades[a],w.right().multiply(off).add(0,-off*.3,0)),5,k==0?1:2,eat,0,.6f*fresh[a]*shown[a]);
                }
            }
        }
        // from its side the camera sees only its edge: a strip of it turned to the camera
        Vec3d mid=w.at(fw,0,tall),toCamera=c.camera.subtract(mid);
        double facing=toCamera.lengthSquared()<1e-6?1:Math.abs(toCamera.normalize().dotProduct(w.f()));
        float edge=1-Ease.smooth((float)facing,.45f,.8f);double wide=EDGE*(.6+.4*scale(half,tall));
        for(int a=0;a<2;a++){
            if(blades[a]==null)continue;
            ribbon(blades[a],3,seed+a*7+1,eat,fresh[a],shown[a]);
            if(edge>.02f){var e=KnifeCombo.edge(blades[a],c.camera,wide);ribbon(e,3,seed+a*7+5,eat,fresh[a],shown[a]*edge);}
        }
    }

    // ───────────────────────────── about it ─────────────────────────────

    /** How much of the line of arm {@code a}'s cut is drawn {@code sec} seconds in (0 to 1): a moment ahead of its blade. */
    static float lineDrawn(int a,float sec){return Ease.smooth(sec,from(a)-LINE_LEAD,to(a)-LINE_LEAD);}
    /** How strongly it shows: there from its cut, hanging where it was cut as the X goes, gone LINE_LINGER after. */
    static float lineShown(int a,float sec,float stop){
        if(sec<from(a)-LINE_LEAD)return 0;
        if(!Float.isNaN(stop)&&stop<FIRE)return sec<stop?1:1-Ease.smooth(sec,stop,stop+.1f);
        return 1-Ease.smooth(sec,FIRE+.04f,FIRE+LINE_LINGER);
    }
    /** How far apart its colours stand {@code sec} seconds in (blocks): together until the X goes. */
    static double lineApart(float sec){return LINE_APART*Ease.out(sec,FIRE,FIRE+LINE_LINGER);}
    /** The ends of the line of arm {@code a}'s cut, where the X stood crossed: from past its high horn ({@code LINE_OVER}) down to its foot. */
    static Vec3d[] line(XSlash.Way w,int a){
        double half=XSlashRules.HALF0,tall=XSlashRules.TALL0,sx=a==0?half:-half,sy=2*tall;
        return new Vec3d[]{w.at(XSlashRules.NEAR,sx*(1+2*LINE_OVER),sy*(1+LINE_OVER)),w.at(XSlashRules.NEAR,-sx,.03)};
    }
    /** Where the two arms cross (their lines' middles): the X's heart. */
    static Vec3d heart(XSlash.Way w,double fw,double tall){return w.at(fw,0,tall);}
    /**
     * Where anchor {@code k} of the X is when it is {@code fw} ahead (0 and 1 its high horns, 2 and 3 its feet, 4 its
     * heart): its horns swept back as they are drawn.
     */
    static Vec3d anchor(XSlash.Way w,int k,double fw){
        double half=XSlashRules.half(fw),tall=XSlashRules.tall(fw);
        if(k==4)return heart(w,fw,tall);
        double back=SWEEP*grown(fw)*2*Math.hypot(half,tall);
        return w.at(fw-back,k%2==0?half:-half,k<2?2*tall:.04);
    }
    /** How far ahead the X is {@code sec} seconds in, for one that goes as far as {@code reach} (where it stood before it goes; where its way ends after). */
    static double ahead(float sec,double reach){return sec<FIRE?XSlashRules.NEAR:Math.min(reach,XSlashRules.head((sec-FIRE)*20));}
    /** How strongly its trails show {@code sec} seconds in: from its going until TRAIL after its way's end. */
    static float trailShown(float sec,double reach){float end=XSlash.flown(reach);return sec<FIRE?0:1-Ease.smooth(sec,end,end+TRAIL);}

    /** What stands about the blades, in one pass (the blended bodies, or the light): the lines of the cuts, the star at its heart, its trails. */
    static void draw(Fx c,EffectMesh m,XSlash.Way w,float t,float stop,float own,boolean light){
        // ── the line of each cut: a hairline of white through the air, ink under it; left hanging as the X goes, its colours coming apart
        for(int a=0;a<2;a++){
            float sh=lineShown(a,t,stop)*own;if(sh<=.01f)continue;
            float p=lineDrawn(a,Float.isNaN(stop)?t:Math.min(t,stop));if(p<=.01f)continue;
            Vec3d[] l=line(w,a);Vec3d a0=l[0],a1=a0.add(l[1].subtract(a0).multiply(p));
            float near=GlitchFx.near(a0.lerp(a1,.5),c.camera)*sh;if(near<=0)continue;
            double apart=lineApart(t);
            if(light){
                m.softLine(a0,a1,.34,GLOW,.26f*near);
                if(apart>.004){
                    Vec3d o=w.right().multiply(apart).add(0,apart*.4,0);
                    m.line(a0.add(o),a1.add(o),.04,PINK,.7f*near);m.line(a0.subtract(o),a1.subtract(o),.04,CYAN,.7f*near);
                }
                m.line(a0,a1,.035,WHITE,.9f*near);
            }else{
                Vec3d o=new Vec3d(0,-.05,0);m.line(a0.add(o),a1.add(o),.06,INK,.8f*near);
            }
        }
        if(!Float.isNaN(stop)&&stop<FIRE&&t>stop)return;
        var x=XSlash.shape(t,w.reach(),stop);
        // ── the star at its heart, once both blades are cut: long rays along the two arms, a hot point, beating
        if(light&&x!=null){
            float whole=Ease.smooth(t,to(1)-.03f,to(1)+.02f),fade=(float)(x[3]*(1-x[4]))*own*whole;
            if(fade>.01f){
                Vec3d hp=heart(w,x[0],x[2]);double sc=scale(x[1],x[2]);float near=GlitchFx.near(hp,c.camera)*fade;
                float beat=.78f+.22f*(float)Math.sin(t*31),burst=1+1.2f*KnifeCombo.bump(t,FIRE,.08f);
                for(int a=0;a<2;a++){
                    Vec3d d=w.right().multiply(a==0?-x[1]:x[1]).add(0,-x[2],0).normalize();double len=(1.5+.5*beat)*sc*burst+.3;
                    m.softLine(hp.subtract(d.multiply(len)),hp.add(d.multiply(len)),.3*sc+.08,GLOW,.3f*near);
                    m.line(hp.subtract(d.multiply(len*.85)),hp.add(d.multiply(len*.85)),.03,WHITE,.65f*near*beat);
                }
                m.softDisc(hp,(.5*sc+.15)*burst,WHITE,.36f*near*beat);m.star(hp,(.4*sc+.12)*burst,WHITE,.5f*near*beat);
            }
        }
        // ── its trails: its four horns' and its heart's, down the way it flew
        float shown=trailShown(t,w.reach())*own;
        if(light&&shown>.01f&&(w.told()==null||!w.told().gone)){
            int steps=8;
            for(int k=0;k<5;k++){
                Vec3d last=null;
                for(int i=0;i<=steps;i++){
                    float back=TRAIL*i/steps,ts=t-back;if(ts<FIRE)break;
                    Vec3d p=anchor(w,k,ahead(ts,w.reach()));
                    if(last!=null&&p.squaredDistanceTo(last)>1e-6){
                        float u=1-(i-.5f)/steps,al=u*u*shown*GlitchFx.near(last,c.camera);
                        boolean heart=k==4;
                        m.softLine(last,p,(heart?.34:.26)*u+.05,heart?LILAC:GLOW,(heart?.3f:.36f)*al);
                        m.line(last,p,.03,heart?WHITE:LILAC,(heart?.5f:.6f)*al);
                    }
                    last=p;
                }
            }
        }
    }
}
