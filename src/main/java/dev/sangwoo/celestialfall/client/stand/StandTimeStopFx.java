package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.*;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.WORLD_GATHER;

/**
 * The light of Star Platinum's time stop (2.68.0, user request: "더 월드 할때 이펙트 엄청 멋있게 만들어줘 이펙트 대폭
 * 강화해주셈"), on the effect layer with the punches' (KagenoRenderer draws it), in their colours over the stopped world
 * (TimeStopView grades the world before the effects are drawn, so this light keeps its colour in it). As its user calls
 * it, a clock face of light opens on the ground under them, its hours lighting one by one and its hands spinning faster
 * and faster; light streams into the stand's gathered fists, where an orb of it swells; strands of light coil up round
 * them, motes rise, the dust of the ground is drawn in and chunks of the floor lift into the air; as the stand flings
 * itself open, rings close in on its user from all round and streaks of light rush in after them. As time stops: a
 * burst at its user, rays of light racing out with the wave, a ring of light where its bubble meets the ground with the
 * minutes marked round it, and dust thrown up behind the front; the clock's hands stop dead and it turns gold. While
 * it is stopped everything the wave passed over hangs where it was: motes of light, the dust it threw up, the chunks of
 * the floor. As "toki wa ugoki dasu" runs, the clock's hands tremble and the motes shiver; as time moves again the bubble
 * breaks into shards of light flung out and falling, the motes and the chunks drop, the dust blows away, the clock's
 * hands run on and it fades, and as the colour has come back to where it began a ring of light runs out from there.
 * Each lights the world round it. Nothing over the whole screen here (TimeStopView draws the wave, the bubble, its
 * cracks and the impact frame). All of it is a function of the time stop's clock: smooth at any frame rate.
 */
public final class StandTimeStopFx {
    /** A time stop near the camera this frame: its clock, where it began, where it stands now (its middle), its stand's body. */
    record Shown(StandTimeStop.Stop s,float t,Vec3d feet,StandPresence.Body stand,int seed,float moves,float yaw){
        boolean cut(){return s.cut;}
        float ended(){return s.ended;}
        Vec3d chest(){return feet.add(0,1.1,0);}
    }
    private static final List<Shown> FRAME=new ArrayList<>();
    /** Seconds after time moves that its light is still about (the shards, the motes' fall, the ring running out). */
    static final float LINGER=1.8f;
    /** The clock face's size (blocks from its middle to its rim). */
    static final double CLOCK=2.6;
    /** How many motes of light hang in the stopped world, how many shards it breaks into, how many chunks of the floor lift. */
    static final int MOTES=170,SHARDS=110,CHUNKS=12;
    private StandTimeStopFx(){}

    /** Gathers this frame's time stops near the camera; returns whether any show. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        FRAME.clear();
        if(mc.world==null)return false;
        for(var s:StandTimeStop.STOPS.values()){
            float t=s.age(delta);
            if(t<0)continue;
            if(s.cut){if(Float.isNaN(s.ended)||t>s.ended+.6f)continue;}
            else if(t>s.moves()+LINGER)continue;
            Vec3d feet=StandTimeStop.feet(mc,s,delta);if(feet==null)continue;
            double far=StandTimeStop.REACH+96;if(feet.squaredDistanceTo(camera)>far*far)continue;
            // its clock faces the way its user faced as they called it (it stays so while time is stopped)
            if(Float.isNaN(s.clockYaw)){var user=mc.world.getPlayerByUuid(s.user);s.clockYaw=user==null?0:user.getHeadYaw();}
            FRAME.add(new Shown(s,t,feet,StandPresence.body(s.user),(int)(s.start*31+s.user.getLeastSignificantBits()),s.moves(),s.clockYaw));
        }
        return !FRAME.isEmpty();
    }
    public static boolean showing(){return !FRAME.isEmpty();}

    // ───────────────────────────── when ─────────────────────────────

    /** How far its user has gathered the power to stop time (0 to 1), and how far the stand has flung itself open. */
    static float gather(Shown s){return Ease.smooth(s.t,.1f,1.4f)*cutOff(s);}
    static float opening(Shown s){return Ease.smooth(s.t,StandTimeStop.OPEN_FROM,StandTimeStop.STOP)*cutOff(s);}
    /** 1 until its call is cut short, going out over 0.3 s from there. */
    static float cutOff(Shown s){return s.cut()&&!Float.isNaN(s.ended())?1-Ease.smooth(s.t,s.ended(),s.ended()+.3f):1;}
    /** Whether time has stopped (and not been cut short as it was called). */
    static boolean stopped(Shown s){return !s.cut()&&s.t>=StandTimeStop.STOP;}
    /** How far the wave has spread {@code t} seconds on (blocks: its bubble's radius; StandTimeStop.wave). */
    static double spread(float t){
        float e=t-StandTimeStop.STOP;if(e<0)return 0;if(e>=StandTimeStop.SPREAD)return StandTimeStop.REACH;
        return StandTimeStop.REACH*(1-Math.pow(1-e/StandTimeStop.SPREAD,2.4));
    }
    /** When the wave reaches {@code d} blocks from the middle of its bubble (seconds on its clock). */
    static float reaches(double d){
        double u=1-Math.pow(Math.max(0,1-Math.min(1,d/StandTimeStop.REACH)),1/2.4);
        return StandTimeStop.STOP+StandTimeStop.SPREAD*(float)u;
    }
    /** How far its cracks have spread {@code t} seconds on its clock (0 to 1, as the bubble's in StandTimeStop.wave). */
    static float crack(Shown s,float t){
        if(s.cut()||t<StandTimeStop.STOP)return 0;
        float lead=s.moves-t;if(lead<=0)return 1;
        return lead<StandTimeStop.CRACK?(float)Math.pow(1-lead/StandTimeStop.CRACK,1.5):0;
    }
    /** The clock's minute hand {@code t} seconds on (radians): faster and faster as its user calls it, stopped dead as time stops. */
    static double minute(Shown s,float t){
        float x=Math.min(t,StandTimeStop.STOP);
        double a=2*Math.PI*(.35*x+.45*Math.pow(x,2.6));
        if(t>StandTimeStop.STOP&&!s.cut()){
            float e=t-StandTimeStop.STOP;
            // the jolt of its stopping dead
            a+=.05*Math.exp(-e/.05)*Math.sin(e*90);
            // trembling as time is about to move
            float lead=s.moves-t;
            if(lead>0&&lead<StandTimeStop.CRACK)a+=Math.toRadians(4)*crack(s,t)*Math.sin(t*150);
            // running on once it moves, slowing as the clock fades
            if(t>s.moves){float d=Math.min(t-s.moves,1.2f);a+=2*Math.PI*(1.2*d-.5*d*d);}
        }
        return a;
    }

    // ───────────────────────────── the passes ─────────────────────────────

    public static void alpha(EffectMesh m){
        for(var s:FRAME){
            clock(m,s,true);
            float b=burstAlpha(s.t,StandTimeStop.STOP,.36f)*(s.cut()?0:1);
            if(b>0)burst(m,s.chest(),2.3*1.14,.36,16,s.seed+5,INK,INK,.75f*b,.75f*b);
            shards(m,s,true);
        }
    }
    public static void additive(EffectMesh m){
        for(var s:FRAME){
            clock(m,s,false);
            gathering(m,s);
            implosion(m,s);
            stop(m,s);
            front(m,s);
            motes(m,s);
            shards(m,s,false);
            resume(m,s);
        }
    }
    /** A burst's strength {@code t}: in over a frame at {@code at}, gone {@code life} seconds later. */
    static float burstAlpha(float t,float at,float life){
        if(t<at-.01f||t>at+life)return 0;
        return Ease.smooth(t,at-.006f,at+.012f)*(float)Math.pow(1-Ease.unit(t,at,at+life),.7);
    }

    // ───────────────────────────── the clock ─────────────────────────────

    /**
     * The clock face on the ground under its user: its rim drawn round as its user starts to call it, its hours lighting
     * one by one, its hands spinning faster and faster, flaring as the stand opens; a white flash as time stops and its
     * hands stop dead, then gold and still; trembling as time is about to move; running on and fading as it moves.
     * {@code ink}: the alpha pass's dark edges (every line a little wider).
     */
    static void clock(EffectMesh m,Shown s,boolean ink){
        float t=s.t;
        float life=Ease.smooth(t,.08f,.22f)*cutOff(s)*(1-Ease.smooth(t,s.moves+.25f,s.moves+1.1f));
        if(life<=0)return;
        Vec3d o=s.feet.add(0,.045,0);
        double turn=twelve(s);
        float flare=opening(s),flash=stopped(s)?FxLib.kick(t,StandTimeStop.STOP,.2f):0,gold=stopped(s)?Ease.smooth(t,StandTimeStop.STOP,StandTimeStop.STOP+.25f):0;
        float draw=Ease.smooth(t,.1f,.5f);
        float glow=life*(.55f+.35f*gather(s)+.6f*flare+.9f*flash)*(1-.35f*gold*(1-flash));
        int rim=mix(VIOLET,GOLD,gold*.75f),line=mix(LILAC,GOLD,gold),hand=mix(WHITE,GOLD,gold*.6f);
        double grow=ink?1.6:1;int col=ink?INK:rim;float a=ink?.55f*life:glow;
        // its rims: the outer drawn round as it opens, an inner one and a small one round its middle
        arc(m,o,CLOCK,.13*grow,turn,turn+Math.PI*2*draw,col,a);
        arc(m,o,CLOCK*.86,.05*grow,turn,turn-Math.PI*2*draw,ink?INK:line,ink?a:.7f*glow);
        arc(m,o,CLOCK*.36,.05*grow,turn,turn+Math.PI*2*draw,ink?INK:line,ink?a:.6f*glow);
        // the minutes, and the hours lighting one by one (the four quarters longer)
        for(int k=0;k<60;k++){
            double ang=turn+k*Math.PI/30;if(k%5==0)continue;
            float on=Ease.smooth(draw,k/60f,k/60f+.05f);if(on<=0)continue;
            tick(m,o,ang,CLOCK*.88,CLOCK*.95,.045*grow,ink?INK:line,(ink?a:.55f*glow)*on);
        }
        for(int k=0;k<12;k++){
            double ang=turn+k*Math.PI/6;
            float lit=Ease.smooth(t,.25f+k*.11f,.33f+k*.11f);if(lit<=0)continue;
            boolean quarter=k%3==0;
            tick(m,o,ang,CLOCK*(quarter?.66:.74),CLOCK*1.0,(quarter?.2:.12)*grow,ink?INK:k==0?hand:line,(ink?a:glow*(.75f+.5f*FxLib.kick(t,.25f+k*.11f,.12f)))*lit);
        }
        // its hands, the minute's long and thin, the hour's short and broad, each with a tail
        double mn=minute(s,t),hr=mn/12+.9;
        handOf(m,o,turn+mn,CLOCK*.8,.17*grow,ink?INK:hand,ink?a:1.15f*glow);
        handOf(m,o,turn+hr,CLOCK*.52,.26*grow,ink?INK:hand,ink?a:1.05f*glow);
        disc(m,o,.16*grow,ink?INK:hand,ink?a:glow);
        if(!ink&&flash>0)disc(m,o,CLOCK*1.15,WHITE,.35f*flash);
    }
    /** The angle of 12 o'clock on the ground (as the ticks and arcs take it, x toward z: clockwise seen from above): the way its user faced. */
    static double twelve(Shown s){return Math.toRadians(s.yaw)+Math.PI/2;}
    /** An arc flat on the ground round {@code o}, {@code r} out and {@code w} wide, from angle {@code a0} to {@code a1}. */
    static void arc(EffectMesh m,Vec3d o,double r,double w,double a0,double a1,int rgb,float alpha){
        if(alpha<=0||Math.abs(a1-a0)<1e-4)return;
        int n=Math.max(2,(int)Math.ceil(Math.abs(a1-a0)/(Math.PI*2)*72));
        for(int i=0;i<n;i++){
            double p=a0+(a1-a0)*i/n,q=a0+(a1-a0)*(i+1)/n;
            Vec3d dp=new Vec3d(Math.cos(p),0,Math.sin(p)),dq=new Vec3d(Math.cos(q),0,Math.sin(q));
            m.quad(o.add(dp.multiply(r-w/2)),o.add(dq.multiply(r-w/2)),o.add(dq.multiply(r+w/2)),o.add(dp.multiply(r+w/2)),rgb,alpha);
        }
    }
    /** A tick flat on the ground: a sharp sliver from {@code r0} to {@code r1} out along angle {@code ang}. */
    static void tick(EffectMesh m,Vec3d o,double ang,double r0,double r1,double w,int rgb,float alpha){
        Vec3d d=new Vec3d(Math.cos(ang),0,Math.sin(ang)),side=new Vec3d(-d.z,0,d.x);
        m.needle(o.add(d.multiply(r0)),o.add(d.multiply(r1)),side,w,.5,rgb,alpha);
    }
    /** A clock hand flat on the ground: a blade out to {@code length} along angle {@code ang}, and its short tail. */
    static void handOf(EffectMesh m,Vec3d o,double ang,double length,double w,int rgb,float alpha){
        Vec3d d=new Vec3d(Math.cos(ang),0,Math.sin(ang)),side=new Vec3d(-d.z,0,d.x);
        m.needle(o.subtract(d.multiply(length*.22)),o.add(d.multiply(length)),side,w,.3,rgb,alpha);
    }
    /** A disc flat on the ground, solid in its middle, soft at its rim. */
    static void disc(EffectMesh m,Vec3d o,double r,int rgb,float alpha){
        if(alpha<=0||r<=0)return;
        for(int i=0;i<24;i++){
            double a0=i*Math.PI/12,a1=(i+1)*Math.PI/12;
            m.vertex(o,rgb,alpha);m.vertex(o.add(Math.cos(a0)*r,0,Math.sin(a0)*r),rgb,0);m.vertex(o.add(Math.cos(a1)*r,0,Math.sin(a1)*r),rgb,0);m.vertex(o,rgb,alpha);
        }
    }
    static int mix(int a,int b,float u){
        u=Math.max(0,Math.min(1,u));
        int r=(int)(((a>>16)&255)*(1-u)+((b>>16)&255)*u),g=(int)(((a>>8)&255)*(1-u)+((b>>8)&255)*u),bl=(int)((a&255)*(1-u)+(b&255)*u);
        return r<<16|g<<8|bl;
    }

    // ───────────────────────────── its call ─────────────────────────────

    /** The gathered fists' middle, where the stand gathers the power (in front of its belly), or its user's chest without a stand. */
    static Vec3d core(Shown s){
        if(s.stand==null)return s.chest().add(Vec3d.fromPolar(0,s.yaw).multiply(.9));
        Vec3d l=on(StandPunch.FISTS[WORLD_GATHER][0],s.stand),r=on(StandPunch.FISTS[WORLD_GATHER][1],s.stand);
        return l.add(r).multiply(.5).add(Vec3d.fromPolar(0,s.stand.yaw()).multiply(.08));
    }
    /**
     * As its user calls it: light streaming into the stand's gathered fists and an orb of it swelling there, strands of
     * light coiling up round its user and the stand, motes rising round them.
     */
    static void gathering(EffectMesh m,Shown s){
        float t=s.t,g=gather(s),end=1-Ease.smooth(t,StandTimeStop.STOP-.04f,StandTimeStop.STOP+.04f);
        float on=g*end;if(on<=0)return;
        Vec3d core=core(s);float flare=opening(s);
        FxLib.converge(m,core,3.6,48,t,.2f,StandTimeStop.OPEN_FROM,LILAC,s.seed+11);
        FxLib.converge(m,core,2.6,30,t,.5f,StandTimeStop.OPEN_FROM+.12f,VIOLET,s.seed+13);
        // the orb: swelling, beating faster as it nears the stop
        float beat=.5f+.5f*(float)Math.sin(t*(14+30*flare));
        double r=.12+.3*g+.25*flare;
        m.softDisc(core,r*2.6,VIOLET,.5f*on*(.8f+.2f*beat));m.softDisc(core,r*1.3,LILAC,.75f*on);m.softDisc(core,r*.6,WHITE,.95f*on);
        if(flare>0)m.star(core,.6+1.4*flare,WHITE,.6f*on*flare*(.7f+.3f*beat));
        // strands of light coiling up round them, faster as it gathers
        Vec3d axis=s.feet.add(Vec3d.fromPolar(0,s.yaw).multiply(.5));
        for(int k=0;k<3;k++){
            double phase=k*Math.PI*2/3+t*(5+9*flare);
            Vec3d last=null;
            for(int i=0;i<=16;i++){
                double h=i/16.0*3.4,rad=1.85-.75*h/3.4,a=phase+h*2.2;
                Vec3d p=axis.add(Math.cos(a)*rad,h+.05,Math.sin(a)*rad);
                if(last!=null){float along=(float)Math.sin(Math.PI*i/16.0);m.needle(last,p,.07,.5,k==0?WHITE:k==1?LILAC:VIOLET,.55f*on*along*(.5f+.5f*g));}
                last=p;
            }
        }
        // motes rising round them
        for(int k=0;k<26;k++){
            float life=.9f+.6f*(float)Ease.hash(s.seed+k*3),c=(t+(float)Ease.hash(s.seed+k*5)*life)/life;float u=c-(float)Math.floor(c);
            double az=Ease.hash(s.seed+k*7+(int)Math.floor(c)*13)*Math.PI*2,rr=.6+2.2*Ease.hash(s.seed+k*11+(int)Math.floor(c)*17);
            Vec3d p=axis.add(Math.cos(az)*rr,.1+2.8*u,Math.sin(az)*rr);
            m.softDisc(p,.05+.05*(1-u),k%3==0?WHITE:LILAC,.8f*on*(float)Math.sin(Math.PI*u));
        }
    }
    /** As the stand flings itself open: rings closing in on its user from all round, and streaks of light rushing in after them. */
    static void implosion(EffectMesh m,Shown s){
        float t=s.t;if(t<StandTimeStop.OPEN_FROM-.05f||t>StandTimeStop.STOP+.02f)return;
        float on=cutOff(s)*(1-Ease.smooth(t,StandTimeStop.STOP-.02f,StandTimeStop.STOP+.02f));if(on<=0)return;
        Vec3d o=s.feet.add(0,.08,0),c=s.chest();
        for(int k=0;k<3;k++){
            float from=StandTimeStop.OPEN_FROM+.06f*k,u=Ease.unit(t,from,StandTimeStop.STOP);if(t<from)continue;
            double r=(8-1.2*k)*(1-u*u)+.25;
            FxLib.ring(m,o.add(0,.4*k,0),r,.45,k==1?VIOLET:LILAC,.85f*on*Ease.smooth(t,from,from+.05f));
        }
        for(int k=0;k<34;k++){
            float from=StandTimeStop.OPEN_FROM+.25f*(float)Ease.hash(s.seed+k*19),life=.22f+.1f*(float)Ease.hash(s.seed+k*23);
            float u=Ease.unit(t,from,from+life);if(t<from||u>=1)continue;
            double az=Ease.hash(s.seed+k*29)*Math.PI*2,el=(Ease.hash(s.seed+k*31)-.3)*1.3;
            Vec3d d=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
            double far=7.5*Math.pow(1-u,1.6)+.3;
            Vec3d p=c.add(d.multiply(far));
            m.needle(p.add(d.multiply(.9+.8*u)),p,.07,.3,k%3==0?WHITE:LILAC,.8f*on*(float)Math.sin(Math.PI*u));
        }
    }

    // ───────────────────────────── time stops ─────────────────────────────

    /** As time stops: the burst at its user, rays of light racing out with the wave. */
    static void stop(EffectMesh m,Shown s){
        if(!stopped(s))return;
        float t=s.t;Vec3d c=s.chest();
        // (kept small: the first capture's burst three times this with a white flash blew the middle of the screen out
        // white for six frames, and the impact frame did not read)
        float b=burstAlpha(t,StandTimeStop.STOP,.36f);
        if(b>0){burst(m,c,2.3,.36,16,s.seed+5,VIOLET,VIOLET,.85f*b,.7f*b);FxLib.flash(m,c,1.6,VIOLET,.55f*b);m.star(c,1.6,WHITE,.35f*b);}
        // a ring of light round its chest, square to the ground, thrown wide
        float u=Ease.unit(t,StandTimeStop.STOP,StandTimeStop.STOP+.4f);
        if(u<1){
            float in=Ease.smooth(t,StandTimeStop.STOP-.01f,StandTimeStop.STOP+.02f);
            Vec3d f=Vec3d.fromPolar(0,s.yaw),r=new Vec3d(-f.z,0,f.x);
            FxLib.ringPlane(m,c,r,UP,.5+4.5*Ease.out(t,StandTimeStop.STOP,StandTimeStop.STOP+.35f),.3,LILAC,.9f*in*(1-u));
            FxLib.ringPlane(m,c,f,UP,.4+4*Ease.out(t,StandTimeStop.STOP,StandTimeStop.STOP+.35f),.26,VIOLET,.8f*in*(1-u));
        }
        // rays racing out over the ground with the wave
        float e=t-StandTimeStop.STOP;if(e>StandTimeStop.SPREAD+.25f)return;
        float ra=Ease.smooth(e,0,.03f)*(1-Ease.smooth(e,StandTimeStop.SPREAD*.55f,StandTimeStop.SPREAD+.25f));
        double reach=spread(t);
        for(int k=0;k<36;k++){
            double az=(k+.5*Ease.hash(s.seed+k*37))*Math.PI*2/36,up=.15+1.6*Ease.hash(s.seed+k*41);
            Vec3d d=new Vec3d(Math.cos(az),0,Math.sin(az));
            double tip=reach*(.97-.08*Ease.hash(s.seed+k*43)),tail=Math.max(.6,tip-2.5-3.5*Ease.hash(s.seed+k*47));
            Vec3d a=s.feet.add(d.multiply(tail)).add(0,up,0),z=s.feet.add(d.multiply(tip)).add(0,up*.9,0);
            m.needle(a,z,.12+.1*Ease.hash(s.seed+k*53),.75,k%4==0?WHITE:k%2==0?LILAC:VIOLET,.85f*ra);
        }
    }
    /**
     * Where the wave's bubble meets the ground: a ring of light with the minutes marked round it (a clock the size of
     * the stopped world), bright as it spreads, faint while time is stopped, two echoes following it in.
     */
    static void front(EffectMesh m,Shown s){
        if(!stopped(s)||s.t>s.moves+.12f)return;
        float t=s.t,e=t-StandTimeStop.STOP;
        double R=spread(t),rg=Math.sqrt(Math.max(0,R*R-1.1*1.1));if(rg<.3)return;
        float u=Math.min(1,e/StandTimeStop.SPREAD);
        float bright=Ease.smooth(e,0,.06f)*(.95f*(1-Ease.smooth(u,.6f,1))+.32f)*(1-Ease.smooth(t,s.moves,s.moves+.12f))*(1+.6f*crack(s,t));
        Vec3d o=s.feet.add(0,.06,0);
        arc(m,o,rg,.35,0,Math.PI*2,LILAC,bright);arc(m,o,rg-.3,.12,0,Math.PI*2,WHITE,.7f*bright);
        for(int k=0;k<60;k++){
            double ang=twelve(s)+k*Math.PI/30;boolean hour=k%5==0;
            tick(m,o,ang,rg-(hour?1.9:.8),rg-.15,hour?.32:.14,hour?GOLD:LILAC,(hour?.95f:.7f)*bright);
        }
        if(u<1)for(int k=1;k<=2;k++){
            double re=rg*(1-.16*k);float ea=bright*(1-u)*(k==1?.55f:.3f);
            if(re>.3)arc(m,o,re,.22,0,Math.PI*2,k==1?VIOLET:BLUE,ea);
        }
    }
    /** The place of mote {@code k} (around its user's feet), or null if it falls outside the stopped world. */
    static Vec3d mote(Shown s,int k){
        double az=Ease.hash(s.seed+k*61)*Math.PI*2,rr=Math.sqrt(Ease.hash(s.seed+k*67))*StandTimeStop.REACH*.95;
        double up=.25+6.5*Math.pow(Ease.hash(s.seed+k*71),1.6);
        Vec3d p=s.feet.add(Math.cos(az)*rr,up,Math.sin(az)*rr);
        return p.distanceTo(s.chest())<StandTimeStop.REACH*.97?p:null;
    }
    /**
     * Motes of light hanging still wherever the wave has passed (each lit as the front reaches it), shivering as time is
     * about to move, dropping and going out as it moves.
     */
    static void motes(EffectMesh m,Shown s){
        if(!stopped(s))return;
        float t=s.t,cr=crack(s,t);
        for(int k=0;k<MOTES;k++){
            Vec3d p=mote(s,k);if(p==null)continue;
            float at=reaches(p.distanceTo(s.chest()));if(t<at)continue;
            float a=Ease.smooth(t,at,at+.03f);
            if(t>s.moves){float d=t-s.moves;a*=1-Ease.smooth(d,.25f,.8f);if(a<=0)continue;p=p.add(0,-Math.min(4.9*d*d,p.y-s.feet.y-.05),0);}
            else if(cr>0)p=p.add(.035*cr*Math.sin(t*97+k),.035*cr*Math.sin(t*83+k*2),.035*cr*Math.sin(t*71+k*3));
            double size=.035+.05*Ease.hash(s.seed+k*73);
            int col=k%5==0?GOLD:k%3==0?WHITE:LILAC;
            float pop=FxLib.kick(t,at,.12f);
            m.softDisc(p,size*(4+5*pop),col,(.32f+.5f*pop)*a);m.softDisc(p,size*1.4,WHITE,.85f*a);
            if(k%4==0)m.star(p,size*(5+6*pop),col,(.45f+.4f*pop)*a);
        }
    }

    // ───────────────────────────── time moves again ─────────────────────────────

    /**
     * Shard {@code k} of the bubble of the stopped world as it breaks: where it was on the bubble, its corners there,
     * and where it is {@code age} seconds after it broke (flung out and falling, spinning, shrinking). Null for one under
     * the ground.
     */
    static Vec3d[] shard(Shown s,int k,float age,boolean ink){
        double az=Ease.hash(s.seed+k*79)*Math.PI*2,ny=-.04+1.04*Ease.hash(s.seed+k*83);
        double rad=Math.sqrt(Math.max(0,1-ny*ny));
        Vec3d n=new Vec3d(Math.cos(az)*rad,ny,Math.sin(az)*rad),c=s.chest(),home=c.add(n.multiply(StandTimeStop.REACH));
        Vec3d e1=n.crossProduct(UP);e1=e1.lengthSquared()<1e-4?new Vec3d(1,0,0):e1.normalize();Vec3d e2=e1.crossProduct(n).normalize();
        double size=1.1+1.4*Ease.hash(s.seed+k*89),a0=Ease.hash(s.seed+k*97)*Math.PI*2;
        Vec3d[] q=new Vec3d[3];
        for(int i=0;i<3;i++){
            double a=a0+i*Math.PI*2/3+(Ease.hash(s.seed+k*101+i)-.5)*.8,r=size*(.65+.55*Ease.hash(s.seed+k*103+i));
            q[i]=e1.multiply(Math.cos(a)*r).add(e2.multiply(Math.sin(a)*r));
        }
        Vec3d vel=n.multiply(2.5+6.5*Ease.hash(s.seed+k*107)).add(UP.multiply(1+2.2*Ease.hash(s.seed+k*109)));
        double tau=.5,drag=tau*(1-Math.exp(-age/tau));
        Vec3d at=home.add(vel.multiply(drag)).add(0,-4.5*age*age,0);
        Vec3d axis=new Vec3d(Ease.hash(s.seed+k*113)-.5,Ease.hash(s.seed+k*127)-.5,Ease.hash(s.seed+k*131)-.5);axis=axis.lengthSquared()<1e-4?n:axis.normalize();
        double spin=(5+9*Ease.hash(s.seed+k*137))*(drag+.3*age)*(Ease.hash(s.seed+k*139)<.5?-1:1);
        double shrink=(1-.5*Ease.smooth(age,.3f,1.25f))*(ink?1.18:1);
        return new Vec3d[]{at.add(turn(q[0],axis,spin).multiply(shrink)),at.add(turn(q[1],axis,spin).multiply(shrink)),at.add(turn(q[2],axis,spin).multiply(shrink))};
    }
    /** As time moves again the bubble breaks into shards of light, flung out and falling. {@code ink}: their dark edges and their glass. */
    static void shards(EffectMesh m,Shown s,boolean ink){
        if(!stopped(s)||s.t<s.moves)return;
        float t=s.t;
        for(int k=0;k<SHARDS;k++){
            float age=t-s.moves-.05f*(float)Ease.hash(s.seed+k*151);if(age<0||age>=1.3f)continue;
            float a=Ease.smooth(age,0,.03f)*(1-Ease.smooth(age,.6f,1.3f));if(a<=0)continue;
            Vec3d[] q=shard(s,k,age,ink);
            int col=k%3==0?LILAC:k%3==1?VIOLET:BLUE;
            if(ink){
                m.triangle(q[0],q[1],q[2],INK,.6f*a);
                Vec3d[] g=shard(s,k,age,false);m.triangle(g[0],g[1],g[2],col,.42f*a);
                continue;
            }
            m.triangle(q[0],q[1],q[2],col,.22f*a);
            m.needle(q[0],q[1],.06,.5,WHITE,.9f*a);m.needle(q[1],q[2],.05,.5,LILAC,.8f*a);m.needle(q[2],q[0],.04,.5,LILAC,.6f*a);
        }
    }
    /** The colour back to where it began: a ring of light running out over the ground from there, and a flash. */
    static void resume(EffectMesh m,Shown s){
        if(!stopped(s))return;
        float at=s.moves+StandTimeStop.FALL,t=s.t;if(t<at-.01f||t>at+.8f)return;
        Vec3d o=s.feet.add(0,.07,0),c=s.chest();float in=Ease.smooth(t,at-.01f,at+.015f);
        float u=Ease.unit(t,at,at+.7f);
        FxLib.ring(m,o,.5+13*Ease.out(t,at,at+.65f),.6,LILAC,.75f*in*(1-u));
        float u2=Ease.unit(t,at+.08f,at+.75f);if(t>=at+.08f)FxLib.ring(m,o,.4+9*Ease.out(t,at+.08f,at+.7f),.4,BLUE,.6f*(1-u2));
        float b=burstAlpha(t,at,.3f);
        if(b>0){FxLib.flash(m,c,1.8,LILAC,.7f*b);
            for(int k=0;k<18;k++){double az=k*Math.PI*2/18+Ease.hash(s.seed+k*157)*.3;Vec3d d=new Vec3d(Math.cos(az),.15*(Ease.hash(s.seed+k*163)-.3),Math.sin(az));
                m.needle(c.add(d.multiply(.4+2.6*u)),c.add(d.multiply(1.6+3.8*u)),.08,.4,k%2==0?WHITE:LILAC,.8f*b);}}
    }

    // ───────────────────────────── dust, chunks, light ─────────────────────────────

    /**
     * Dust: drawn in over the ground as its user calls it; thrown up behind the wave's front as time stops and hanging
     * there, stopped, until time moves and it blows away.
     */
    public static void dust(Matrix4f matrix,Vec3d camera){
        if(FRAME.isEmpty()||!FxLayer.available()||!KagenoFire.ready())return;
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        float now=(float)(mc.world.getTime()+mc.getTickDelta());
        List<Vec3d> moving=new ArrayList<>(),still=new ArrayList<>();List<float[]> movingCells=new ArrayList<>(),stillCells=new ArrayList<>();
        float stillAge=now;
        for(var s:FRAME){
            // (the world's time as time stopped: what it stopped is drawn as it was then)
            if(stopped(s))stillAge=now-(s.t-StandTimeStop.STOP)*20;
            float t=s.t;
            // drawn in as it is called
            float g=gather(s)*(1-Ease.smooth(t,StandTimeStop.STOP-.05f,StandTimeStop.STOP+.05f));
            if(g>0)for(int k=0;k<18;k++){
                float life=.7f+.4f*(float)Ease.hash(s.seed+k*167),c=(t+(float)Ease.hash(s.seed+k*173)*life)/life;float u=c-(float)Math.floor(c);
                double az=Ease.hash(s.seed+k*179+(int)Math.floor(c)*7)*Math.PI*2+u*1.4,r=5.2*(1-u)+.7;
                moving.add(s.feet.add(Math.cos(az)*r,.2+.15*u,Math.sin(az)*r));
                movingCells.add(new float[]{(float)(.55+.4*Ease.hash(s.seed+k*181))*(1-.5f*u),.75f*g*(float)Math.sin(Math.PI*u),0,u,s.seed+k*37});
            }
            if(!stopped(s))continue;
            // thrown up behind the front near the edge of the stopped world, then hanging there; blown away as time moves
            // (one ring: the first capture's second, halfway out, read as a ring of clouds)
            for(int ring=0;ring<1;ring++){
                double share=.88;int n=24;
                for(int k=0;k<n;k++){
                    double az=(k+Ease.hash(s.seed+ring*997+k*191)*.6)*Math.PI*2/n,d=StandTimeStop.REACH*share*(.94+.08*Ease.hash(s.seed+k*193));
                    float at=reaches(Math.hypot(d,1.1));if(t<at)continue;
                    // (small and faint: the first capture's, twice this, hung round the stopped world as big white clouds)
                    float rise=Math.min(t,s.moves)-at,size=(float)(.42+.3*Ease.hash(s.seed+k*197))*Ease.out(rise,0,.12f);
                    float a=.6f*Ease.smooth(rise,0,.04f);
                    Vec3d p=s.feet.add(Math.cos(az)*d,.25+.3*Ease.out(rise,0,.12f),Math.sin(az)*d);
                    float[] cell={size,a,0,.3f,s.seed+ring*577+k*41};
                    if(t<=s.moves){still.add(p);stillCells.add(cell);}
                    else{float gone=t-s.moves;if(gone>.8f)continue;cell[1]*=1-Ease.smooth(gone,.1f,.8f);cell[0]*=1+.8f*Ease.out(gone,0,.8f);cell[3]=.3f+.7f*Ease.unit(gone,0,.8f);
                        moving.add(p.add(Math.cos(az)*1.8*gone,.6*gone,Math.sin(az)*1.8*gone));movingCells.add(cell);}
                }
            }
        }
        if(!moving.isEmpty())KagenoFire.dust(matrix,camera,moving,movingCells,now);
        // (what time stopped is drawn at the moment it stopped: it does not stir)
        if(!still.isEmpty())KagenoFire.dust(matrix,camera,still,stillCells,stillAge);
    }
    /** Chunks of the floor lifting into the air round its user as they call it, hanging there while time is stopped, dropping as it moves. */
    public static void solids(FxSolids solids){
        for(var s:FRAME){
            float t=s.t;if(t<.4f)continue;
            var block=solids.ground(s.feet.add(0,-.5,0));if(block==null)continue;
            double floor=solids.groundY(s.feet.add(0,.3,0));
            float stopAt=s.cut()&&!Float.isNaN(s.ended())?s.ended():StandTimeStop.STOP,held=s.cut()?stopAt:s.moves;
            for(int k=0;k<CHUNKS;k++){
                double az=(k+.6*Ease.hash(s.seed+k*211))*Math.PI*2/CHUNKS,r=1.7+3.2*Ease.hash(s.seed+k*223);
                double size=.22+.25*Ease.hash(s.seed+k*227);
                float lifted=Math.min(t,stopAt);
                double lift=(.35+1.6*Ease.hash(s.seed+k*229))*Ease.smooth(lifted,.45f+.35f*(float)Ease.hash(s.seed+k*233),1.75f);
                // a slow turn and sway as it rises; still once time has stopped
                double bob=.06*Math.sin(lifted*3+k)*Ease.smooth(lifted,.6f,1f);
                Vec3d p=new Vec3d(s.feet.x+Math.cos(az)*r,floor+size*.5+lift+bob,s.feet.z+Math.sin(az)*r);
                float spin=lifted*(40+50*(float)Ease.hash(s.seed+k*239));
                if(t>held){
                    float d=t-held;if(d>1.1f)continue;
                    p=FxSolids.flight(p,Vec3d.ZERO,floor+size*.5,d);size*=1-Ease.smooth(d,.6f,1.1f);
                }
                if(size>.01)solids.chunk(block,p,size,spin+k*41,spin*.7f+k*13,spin*.4f);
            }
        }
    }
    /** Each part lights the world round it. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            float t=s.t,g=gather(s)*(1-Ease.smooth(t,StandTimeStop.STOP-.04f,StandTimeStop.STOP+.04f));
            if(g>0)f.light(core(s),0xb8a4ff,5+3*opening(s),2.2f*g+2f*opening(s));
            if(!stopped(s))continue;
            Vec3d c=s.chest();
            f.light(c,0xc8b8ff,22,4f*FxLib.kick(t,StandTimeStop.STOP,.3f));
            float shaft=FxLib.kick(t,StandTimeStop.STOP,.35f);if(shaft>.02f)f.shaft(c,0xc8b8ff,.7f*shaft);
            float clock=Ease.smooth(t,StandTimeStop.STOP,StandTimeStop.STOP+.3f)*(1-Ease.smooth(t,s.moves+.2f,s.moves+1f));
            if(clock>0)f.light(s.feet.add(0,.6,0),0xffe7b0,4.5,.5f*clock);
            if(t>=s.moves+StandTimeStop.FALL-.01f)f.light(c,0xd8ccff,16,3.5f*FxLib.kick(t,s.moves+StandTimeStop.FALL,.3f));
        }
    }
}
