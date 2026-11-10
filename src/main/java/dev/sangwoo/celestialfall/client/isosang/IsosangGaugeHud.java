package dev.sangwoo.celestialfall.client.isosang;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.gacha.GachaArt;
import dev.sangwoo.celestialfall.isosang.IsosangAttack;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import dev.sangwoo.celestialfall.stand.StandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee So-sang's H gauge (2.113.0, user request: "인벤창 바로 위에 영상처럼 검 모양 게이지를 추가해 ... 총 8대를 맞추면 게이지가
 * 100%되고 검 게이지에 애니메이션과 불이 들어와 ... H를 키면 게이지 애니메이션이 바뀌고 H 상태에서는 게이지가 서서히 줄어들고",
 * the user's video (1974a4d8) read on a sheet and confirmed, "맞음, 이대로"; IsosangRules.GAUGE_FULL): a sword lying across
 * the screen right above the hotbar, its pommel's point to the left, its flower of a guard, its blade and point to the
 * right. Empty, its inside is dark and see-through in a pale beige outline. Each foe a basic attack strikes fills it a
 * step at once (no slide: the video's steps come in a frame) from the pommel toward the point: the grip light blue with
 * a pale line inside, the guard deep blue, the blade a navy blue lighter where it has filled to. Full, the whole sword
 * flashes white with light spreading out sideways, then its outline glows white and lilac, beating every 0.7 s, its blade
 * a brighter blue with a darker grain flowing along it, and blue flames rise round it ("불이 들어와"). As H comes on it
 * turns pale sky blue and white in a big flash; on H its blade glows white by the guard, blue along it and sky blue where
 * it has drained to, the whole blade lit up every 0.72 s and the light draining out toward the point, the flames on;
 * it drains from the point (IsosangRules.gaugeLeft, from the server's word of when it runs dry), T taking a fifth at once
 * and H again all of it. Only Lee So-sang's player sees it, on the map. Everything is a continuous function of its own
 * clock (client ticks and the time between) and of the server's word.
 */
public final class IsosangGaugeHud {
    private IsosangGaugeHud(){}
    /** Its length (GUI px) and how far over the bottom of the screen its middle line lies (the hotbar is 22 tall). */
    static final float LEN=170,LINE=37.5f;
    /** How far it pushes up what stood just over the hotbar (the boss bar, the armour row) while it shows (GUI px). */
    static final float ROOM=28;
    /**
     * Its outline, the upper half (the lower is its mirror), read off the user's video (its frame at 11.5 s, the sword 870 px
     * long): along it (0 the pommel's point, 1 the blade's) how far up from its middle line it reaches (of its length): the
     * pommel's point, the grip, the collar's little step, the dip before the guard, the guard's big spike (leaning toward
     * the blade), the notch, its small spike, the blade and its point.
     */
    static final float[][] TOP={{0,0},{.0604f,.0316f},{.1813f,.0316f},{.1942f,.0388f},{.2106f,.0224f},{.2589f,.0829f},{.2690f,.0552f},
        {.2863f,.0512f},{.2900f,.0316f},{.8962f,.0316f},{1,0}};
    /** Where its grip ends (at the collar), and where its blade begins and its point (of its length). */
    static final float GRIP=.183f,BLADE=.29f,POINT=.8962f;
    /** How far its fill and inner line stay inside its outline (of its length: under the outline's own width). */
    static final float INSET=.0045f,LINE_IN=.0115f;
    /** The outline's width (GUI px): empty, and lit (full or on H). */
    static final float RIM=1.0f,RIM_LIT=1.35f;
    /** How long a beat of the full gauge's glow and one of H's light (seconds: the video's 0.7 s and 0.72 s). */
    static final float BEAT=.7f,PULSE=.72f;
    /** How long its grain takes to flow the length of its blade when full (seconds). */
    static final float FLOW=1.5f;
    /** The flames: how many at once at most, how long each lives (seconds) and how often each place has one. */
    static final int FLAMES=18;static final float FLAME_LIFE=.75f,FLAME_EVERY=.92f;

    // ───────────────────────────── what the server says ─────────────────────────────

    /** Its clock (client ticks), how full it is (the server's word), and on H its formation's start and when it runs dry and flares (ticks into it). */
    private static int ticks;private static int units;private static long formStart=-1;private static double emptyAge,endAge;
    /** The server's tick as its last word came, and the clock as it came. */
    private static long told;private static int toldAt;
    /** When (its clock, seconds) it last filled up, H came on, H went off. */
    private static float fullAt=-1e9f,onAt=-1e9f,offAt=-1e9f;
    /** How much it shows (0 to 1, easing in and out over SHOW ticks), and the world its word is of. */
    private static float shown,lastShown;private static Object world;
    static final float SHOW=6;

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(IsosangAttack.GAUGE,(mc,handler,b,response)->{
            int u=b.readUnsignedByte();long serverNow=b.readLong(),fs=b.readLong();double ea=b.readDouble(),en=b.readDouble();
            mc.execute(()->accept(mc,u,serverNow,fs,ea,en));
        });
        ClientTickEvents.END_CLIENT_TICK.register(IsosangGaugeHud::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(IsosangGaugeHud::clear));
    }
    static void accept(MinecraftClient mc,int u,long serverNow,long fs,double ea,double en){
        if(mc.world!=world){clear();world=mc.world;}
        float t=ticks/20f;boolean was=formStart>=0,is=fs>=0&&Double.isFinite(ea)&&Double.isFinite(en);
        if(is&&!was)onAt=t;
        if(!is&&was)offAt=t;
        int full=Math.max(0,Math.min(IsosangRules.GAUGE_FULL,u));
        if(!is&&IsosangRules.gaugeFull(full)&&!IsosangRules.gaugeFull(units))fullAt=t;
        units=full;formStart=is?fs:-1;emptyAge=is?ea:0;endAge=is?en:0;told=serverNow;toldAt=ticks;
    }
    static void clear(){units=0;formStart=-1;emptyAge=endAge=0;fullAt=onAt=offAt=-1e9f;shown=lastShown=0;world=null;}
    private static void tick(MinecraftClient mc){
        ticks++;
        if(mc.world!=world&&world!=null)clear();
        lastShown=shown;
        boolean show=mc.player!=null&&mc.world!=null&&MapClient.enabled()&&StandManager.isosang(mc.player);
        shown=Math.max(0,Math.min(1,shown+(show?1:-1)/SHOW));
    }
    /** How much it shows this frame (0 to 1, eased). */
    static float shown(float delta){float s=lastShown+(shown-lastShown)*delta;return s*s*(3-2*s);}
    /** How far it pushes up what stood just over the hotbar this frame (GUI px; the boss bar and the armour row ask). */
    public static float room(){var mc=MinecraftClient.getInstance();return ROOM*shown(mc==null?1:mc.getTickDelta());}
    /** How full it is shown now (0 to 1): on H, what is left of it as it drains (the server's word of when it runs dry); else its steps. */
    static float fill(float delta){
        if(formStart>=0)return (float)IsosangRules.gaugeLeft(told-formStart+(ticks-toldAt)+delta,emptyAge);
        return units/(float)IsosangRules.GAUGE_FULL;
    }
    /** (For the checks) how full it is shown now (0 to 1). */
    public static float fillNow(){var mc=MinecraftClient.getInstance();return fill(mc==null?0:mc.getTickDelta());}
    /** (For the checks) how full it is by the server's last word, and whether it is on H. */
    public static int units(){return units;}
    public static boolean onH(){return formStart>=0;}

    // ───────────────────────────── its shape ─────────────────────────────

    /** How far up from its middle line its outline reaches {@code u} along it (of its length; its lower half the mirror). */
    static float half(float u){
        if(u<=0||u>=1)return 0;
        for(int i=1;i<TOP.length;i++)if(u<=TOP[i][0]){float a=TOP[i-1][0],b=TOP[i][0],k=(u-a)/(b-a);return TOP[i-1][1]+(TOP[i][1]-TOP[i-1][1])*k;}
        return 0;
    }
    /** Its outline (GUI px, clockwise on screen: along its top from the pommel's point, back along its bottom), its pommel's point at {@code x}, its middle line at {@code y}. */
    static float[] outline(float x,float y){
        int n=TOP.length;float[] p=new float[(n*2-2)*2];int k=0;
        for(int i=0;i<n;i++){p[k++]=x+TOP[i][0]*LEN;p[k++]=y-TOP[i][1]*LEN;}
        for(int i=n-2;i>=1;i--){p[k++]=x+TOP[i][0]*LEN;p[k++]=y+TOP[i][1]*LEN;}
        return p;
    }
    /** Where its strips are cut between {@code from} and {@code to} along it: every corner of its outline and every STEP between. */
    static float[] cuts(float from,float to){
        List<Float> at=new ArrayList<>();at.add(from);
        for(int i=1;i<=STEPS;i++){float u=i/(float)STEPS;if(u>from&&u<to)at.add(u);}
        for(var c:TOP)if(c[0]>from&&c[0]<to)at.add(c[0]);
        at.add(to);at.sort(Float::compare);
        List<Float> out=new ArrayList<>();for(float u:at)if(out.isEmpty()||u-out.get(out.size()-1)>1e-5f)out.add(u);
        float[] r=new float[out.size()];for(int i=0;i<r.length;i++)r[i]=out.get(i);return r;
    }
    static final int STEPS=120;
    /** The rows its strips are cut in across (of its height there, -1 its top to 1 its bottom): its edges lighter. */
    static final float[] ROWS={-1,-.62f,-.25f,0,.25f,.62f,1};
    /** A colour for a place on it: {@code u} along it, {@code s} across it (-1 its top to 1 its bottom). */
    interface Paint{int at(float u,float s);}
    /** Its inside from {@code from} to {@code to} along it, in strips coloured by {@code paint}, {@code inset} inside its outline (of its length). */
    static void strips(BufferBuilder b,Matrix4f m,float x,float y,float from,float to,float inset,Paint paint){
        if(to-from<1e-4f)return;
        float[] cut=cuts(from,to);
        for(int i=0;i+1<cut.length;i++){
            float ua=cut[i],ub=cut[i+1],ha=Math.max(0,half(ua)-inset)*LEN,hb=Math.max(0,half(ub)-inset)*LEN;
            if(ha<=1e-3f&&hb<=1e-3f)continue;
            float xa=x+ua*LEN,xb=x+ub*LEN;
            for(int r=0;r+1<ROWS.length;r++){
                float s0=ROWS[r],s1=ROWS[r+1];
                float ya0=y+s0*ha,ya1=y+s1*ha,yb0=y+s0*hb,yb1=y+s1*hb;
                int a0=paint.at(ua,s0),a1=paint.at(ua,s1),b0=paint.at(ub,s0),b1=paint.at(ub,s1);
                GachaArt.vertex(b,m,xa,ya0,a0);GachaArt.vertex(b,m,xb,yb0,b0);GachaArt.vertex(b,m,xb,yb1,b1);
                GachaArt.vertex(b,m,xa,ya0,a0);GachaArt.vertex(b,m,xb,yb1,b1);GachaArt.vertex(b,m,xa,ya1,a1);
            }
        }
    }
    /** The pale line inside its grip and its blade (top and bottom), from its pommel to {@code to} along it. */
    static void innerLines(BufferBuilder b,Matrix4f m,float x,float y,float to,float width,int grip,int blade){
        for(int side=-1;side<=1;side+=2){
            float pu=-1,px=0,py=0;
            for(int i=0;i<=STEPS;i++){
                float u=i/(float)STEPS;
                if(u>to){u=to;}
                boolean in=(u>=.02f&&u<=GRIP-.004f)||(u>=BLADE+.006f&&u<=.985f);
                float h=half(u)-LINE_IN;
                if(!in||h<.003f){pu=-1;if(u>=to)break;continue;}
                float qx=x+u*LEN,qy=y+side*h*LEN;
                if(pu>=0)GachaArt.line(b,m,px,py,qx,qy,width,u<BLADE?grip:blade);
                pu=u;px=qx;py=qy;
                if(u>=to)break;
            }
        }
    }

    // ───────────────────────────── its looks ─────────────────────────────

    static float smooth(float t,float a,float b){if(b<=a)return t<a?0:1;float k=Math.max(0,Math.min(1,(t-a)/(b-a)));return k*k*(3-2*k);}
    /** How much of it is its guard, and of its blade, at {@code u} (the rest its grip), eased a little where they meet. */
    static float guardShare(float u){return smooth(u,GRIP-.004f,GRIP+.006f)*(1-smooth(u,BLADE-.006f,BLADE+.002f));}
    static float bladeShare(float u){return smooth(u,BLADE-.006f,BLADE+.002f);}
    /** Its parts' colours: {@code grip}, {@code guard}, {@code blade} at {@code u}. */
    static int parts(float u,int grip,int guard,int blade){return GachaArt.mix(GachaArt.mix(grip,guard,guardShare(u)),blade,bladeShare(u));}
    /** How near its edge across ({@code s} -1 to 1): 1 at its edges, 0 down its middle. */
    static float edge(float s){return s*s;}
    /** As it fills (the video's 0.3 to 9 s): the grip light blue, the guard deep blue, the blade navy, lighter where it has filled to ({@code f}). */
    static int filling(float u,float s,float f){
        float e=edge(s);
        int grip=GachaArt.mix(0xff1452ca,0xff5a90e6,e),guard=GachaArt.mix(0xff0a28c2,0xff2e46d4,e),blade=GachaArt.mix(0xff0a0a9e,0xff1c22b8,e);
        blade=GachaArt.mix(blade,GachaArt.mix(0xff1442d6,0xff3c66e4,e),smooth(u,f-.05f,f));
        return parts(u,grip,guard,blade);
    }
    /** Full (the video's 9.3 to 13 s): brighter, a darker grain flowing along its blade every FLOW seconds. */
    static int full(float u,float s,float t){
        float e=edge(s);
        int grip=GachaArt.mix(0xff1f5cd8,0xff6c9cea,e),guard=GachaArt.mix(0xff1c30d2,0xff4554de,e),blade=GachaArt.mix(0xff1b1ecb,0xff5058e0,e);
        float c=BLADE+(1.12f-BLADE)*fract(t/FLOW),g=(u-c)/.07f;
        blade=GachaArt.mix(blade,0xff0b0c86,.42f*(float)Math.exp(-g*g)*(1-e));
        return parts(u,grip,guard,blade);
    }
    /**
     * On H (the video's 13.7 s on): white by the guard, blue along its blade, sky blue where it has drained to ({@code f});
     * every PULSE seconds ({@code p} through it) the blade all lit up, then the light draining out of it from the guard
     * toward its end.
     */
    static int onH(float u,float s,float f,float p){
        float e=edge(s);
        int grip=GachaArt.mix(0xffcde8ea,0xfff3fbff,e),guard=GachaArt.mix(0xffa9cfea,0xffd9effb,e);
        int blade=GachaArt.mix(0xff4f6ed0,0xff5ccfd8,smooth(u,f-.36f,f-.02f));
        blade=GachaArt.mix(blade,0xffdaf3ff,.5f*e);
        blade=GachaArt.mix(blade,0xffb4e8f0,.72f*lit(u,f,p)*(1-.4f*e));
        return parts(u,grip,guard,blade);
    }
    /** How lit its blade is {@code u} along it on H, {@code p} through a pulse: all of it at first, the light then draining out from the guard toward {@code f}. */
    static float lit(float u,float f,float p){
        float front=BLADE+(Math.max(BLADE,f)-BLADE+.08f)*smooth(p,.16f,.84f);
        float shade=(1-smooth(u,front-.05f,front+.05f))*smooth(p,.08f,.24f)*(1-smooth(p,.8f,1f));
        return 1-shade;
    }
    static float fract(float x){return x-(float)Math.floor(x);}
    /** A beat (0 to 1) every {@code period} seconds, {@code t} into it: up quickly, down slowly. */
    static float beat(float t,float period){float p=fract(t/period);return p<.25f?smooth(p,0,.25f):1-smooth(p,.25f,1);}
    /** A flash {@code age} seconds after it: in over {@code rise}, gone by {@code gone}. */
    static float flash(float age,float rise,float gone){return age<0||age>=gone?0:age<rise?smooth(age,0,rise):1-smooth(age,rise,gone);}

    /**
     * One of its flames {@code t} seconds on its clock ({@code k} of FLAMES), on what is lit of it ({@code to} along it):
     * where it is (along it, which side: -1 its top, 1 its bottom), how far it has risen out of it (of its life, 0 to 1),
     * and its size; null when that place has none now. Each rises off the sword outward (up off its top, down off its
     * bottom), swaying, and is gone in FLAME_LIFE.
     */
    record Flame(float u,int side,float age,float size,float sway){}
    static Flame flame(int k,float t,float to){
        float off=GachaArt.hash(k*977+13)*FLAME_EVERY,ct=t+off;int cycle=(int)Math.floor(ct/FLAME_EVERY);
        float age=(ct-cycle*(float)FLAME_EVERY)/FLAME_LIFE;if(age>=1)return null;
        int seed=k*7919+cycle*104729;
        float u=.04f+(Math.max(.05f,to)-.04f)*GachaArt.hash(seed+1);
        int side=GachaArt.hash(seed+2)<.72f?-1:1;
        float size=.75f+.5f*GachaArt.hash(seed+3),sway=(GachaArt.hash(seed+4)-.5f)*2;
        return new Flame(u,side,age,size,sway);
    }
    /** A flame's outline, its foot at ({@code x}, {@code y}), {@code w} wide and {@code h} tall, pointing up off its top ({@code side} -1) or down off its bottom (1). */
    static float[] flameShape(float x,float y,float w,float h,int side){
        float[] ux={0,.3f,.5f,.36f,0,-.36f,-.5f,-.3f},uy={-1,-.46f,0,.28f,.38f,.28f,0,-.46f};
        float[] p=new float[16];
        for(int i=0;i<8;i++){p[i*2]=x+ux[i]*w;p[i*2+1]=y-side*uy[i]*h;}
        return p;
    }

    // ───────────────────────────── drawing ─────────────────────────────

    /** Draws it over the hotbar (InGameHudMixin, with the map's HUD). */
    public static void render(DrawContext d,float delta){
        float a=shown(delta);if(a<=.002f)return;
        var mc=MinecraftClient.getInstance();if(mc.player==null)return;
        int w=d.getScaledWindowWidth(),h=d.getScaledWindowHeight();
        float x=w/2f-LEN/2f,y=h-LINE,t=(ticks+delta)/20f,f=fill(delta);
        boolean on=formStart>=0;
        // how much of each look: H's palette, the full one, and its outline and flames lit
        float hOn=on?smooth(t,onAt+.03f,onAt+.09f):0;
        float full=!on&&IsosangRules.gaugeFull(units)?smooth(t,fullAt+.05f,fullAt+.16f):0;
        float rimLit=on?hOn:Math.max(full,1-smooth(t,offAt,offAt+.3f));
        float dry=on?1-smooth((float)(told-formStart+(ticks-toldAt)+delta),(float)emptyAge,(float)emptyAge+6):1;
        float fire=Math.max(full,on?hOn*dry:Math.max(0,1-smooth(t,offAt,offAt+.4f))*(offAt>-1e8f?1:0));
        float p=fract(Math.max(0,t-onAt)/PULSE);
        var m=d.getMatrices().peek().getPositionMatrix();
        // its inside, dark and see-through, then what is full of it, its inner line and its outline
        var b=GachaArt.begin(d,false);
        strips(b,m,x,y,0,1,INSET,(u,s)->GachaArt.fade(0x9a0a0e16,a));
        final float fl=f,tt=t,hh=hOn,ff=full,pp=p;
        strips(b,m,x,y,0,f,INSET,(u,s)->{
            int c=GachaArt.mix(filling(u,s,fl),full(u,s,tt),ff);
            return GachaArt.fade(GachaArt.mix(c,onH(u,s,fl,pp),hh),a);
        });
        int gripLine=GachaArt.mix(GachaArt.mix(0xb0a8c8f4,0xd0c4d4fa,full),0xd8ffffff,hOn),bladeLine=GachaArt.mix(GachaArt.mix(0x703e5cd8,0xc8b4b6f4,full),0x90ffffff,hOn);
        innerLines(b,m,x,y,f,.8f,GachaArt.fade(gripLine,a),GachaArt.fade(bladeLine,a));
        float[] rim=outline(x,y);
        int rimColour=GachaArt.mix(0xe6b8ad88,GachaArt.mix(0xffeee8ff,0xfff0faff,hOn),rimLit);
        GachaArt.stroke(b,m,rim,RIM+(RIM_LIT-RIM)*rimLit,GachaArt.fade(rimColour,a));
        GachaArt.end(b);
        // light: its outline's glow (beating), the edge it has drained to on H, its flames, and its flashes
        var g=GachaArt.begin(d,true);
        if(rimLit>.01f){
            float glow=full*(.42f+.3f*beat(t-fullAt,BEAT))+hOn*(.5f+.22f*beat(t-onAt,PULSE));
            GachaArt.glow(g,m,rim,5.5f,GachaArt.fade(GachaArt.mix(0xffc4b4ff,0xffa6dcff,hOn),a),Math.min(1,glow)*rimLit);
        }
        if(on&&f>.002f&&hOn>.01f){
            float ex=x+f*LEN,eh=Math.max(1.5f,(half(f)-INSET)*LEN);
            float[] spot=GachaArt.circle(ex,y,1,12);for(int i=0;i<12;i++){spot[i*2]=ex+(spot[i*2]-ex)*2.4f;spot[i*2+1]=y+(spot[i*2+1]-y)*(eh+1.2f);}
            GachaArt.glow(g,m,spot,3.5f,GachaArt.fade(0xff86eaff,a*hOn),.8f);
            GachaArt.fill(g,m,spot,GachaArt.fade(0x9086eaff,a*hOn));
        }
        if(fire>.01f)flames(g,m,x,y,t,on?f:1,fire*a);
        float fullFlash=flash(t-fullAt,.03f,.2f),onFlash=flash(t-onAt,.12f,.42f);
        if(fullFlash>.01f)burst(g,m,x,y,fullFlash*a,1,0xffd8f6ff,(t-fullAt)/.2f);
        if(onFlash>.01f)burst(g,m,x,y,onFlash*a,1.35f,0xffe6fbff,(t-onAt)/.42f);
        GachaArt.end(g);
        RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
    }
    /** Its flames on what is lit of it ({@code to} along it), {@code strength} 0 to 1. */
    static void flames(BufferBuilder g,Matrix4f m,float x,float y,float t,float to,float strength){
        for(int k=0;k<FLAMES;k++){
            var fl=flame(k,t,to);if(fl==null)continue;
            float age=fl.age(),grow=smooth(age,0,.18f),fade=1-smooth(age,.55f,1);
            float alpha=strength*grow*fade;if(alpha<.01f)continue;
            float baseY=y+fl.side()*(half(fl.u())*LEN*.75f),rise=(4.5f+6f*fl.size())*age;
            float fx=x+fl.u()*LEN+fl.sway()*1.6f*(float)Math.sin(age*6.5f+fl.u()*40),fy=baseY+fl.side()*rise;
            float fw=(3.6f+2.0f*fl.size())*(1-.35f*age),fh=(6f+3.4f*fl.size())*(1-.25f*age);
            float[] outer=flameShape(fx,fy,fw,fh,fl.side());
            int deep=GachaArt.fade(0xff2a48ff,alpha*.95f),tip=GachaArt.fade(0xff2a48ff,0);
            GachaArt.fill(g,m,outer,fl.side()<0?tip:deep,fl.side()<0?deep:tip);
            float[] core=flameShape(fx,fy+fl.side()*-.4f,fw*.45f,fh*.55f,fl.side());
            int bright=GachaArt.fade(0xff8ad6ff,alpha*.85f),dim=GachaArt.fade(0xff8ad6ff,0);
            GachaArt.fill(g,m,core,fl.side()<0?dim:bright,fl.side()<0?bright:dim);
        }
    }
    /**
     * A flash over all of it ({@code strength} 0 to 1, {@code k} through it): the sword white, and a band of light spreading
     * out sideways from it, {@code wide} times as wide as the full one's.
     */
    static void burst(BufferBuilder g,Matrix4f m,float x,float y,float strength,float wide,int colour,float k){
        strips(g,m,x,y,0,1,0,(u,s)->GachaArt.fade(0xffffffff,strength*.9f));
        float cx=x+.5f*LEN,rx=LEN*(.42f+.22f*Math.min(1,k))*wide,ry=2.2f+1.6f*wide;
        float[] band=GachaArt.circle(cx,y,1,40);for(int i=0;i<40;i++){band[i*2]=cx+(band[i*2]-cx)*rx;band[i*2+1]=y+(band[i*2+1]-y)*ry;}
        GachaArt.fill(g,m,band,GachaArt.fade(colour,strength*.75f));
        GachaArt.glow(g,m,band,9*wide,GachaArt.fade(colour,strength),.9f);
    }
}
