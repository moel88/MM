package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;

/**
 * The formation's lights (2.98.0, user request: "이제 H 스킬도 너가 꾸밀수 있는 모든 걸 꾸며줘 이펙트를 대폭 강화하고
 * 아주 예쁘게 만들어봐"): moonlight and starlight over all of it, as her sword's own (the basic attack's decorations):
 * <ul>
 * <li>as she springs off the ground: a ring of light opening round her feet and streaks of light shooting up round her;</li>
 * <li>as the black spike breaks in the middle: a pillar of light up out of it, a wider crown, a star;</li>
 * <li>as the swords turn silver: a shimmer running up each from its point to its pommel, out from the middle;</li>
 * <li>while it stands: stars drifting up over it, twinkling ({@link #MOTES}), threads of light drawn now and then
 * between swords near one another as a constellation's ({@link #threads}), and every five seconds, as it strikes again,
 * a ring of light running out over its ground and every sword lit from its guard to its point ({@link #pulse});</li>
 * <li>as it ends: a ring of light running out past its rim and stars rising off every sword as it goes.</li>
 * </ul>
 * None of what the user asked to keep quiet is made louder: the smoke and the mist at its rim stay a dark navy giving no
 * light, its water stays still, the whirlpool's light stays a pale see-through blue, the mountains, the stream under them
 * and the clouds as they are; no impact frame over its opening, where the ink and the whirlpool move. The light's own
 * strengthening of what was there (the thread of lightning, the burst, the rain of swords, their flowers, the gleams, the
 * flare as it ends, the bursts on whom it strikes) is SwordFormation's.
 */
final class FormationLights {
    private FormationLights(){}

    /** All of it in the light pass. */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;Vec3d centre=SwordFormation.centre(c.cast);float yaw=SwordFormation.yaw(c.cast);
        takeoff(c,m,t);
        landing(c,m,t);
        crown(c,m,t,centre);
        shimmer(c,m,t,centre,yaw);
        motes(c,m,t,centre);
        threads(c,m,t,centre,yaw);
        pulse(c,m,t,centre,yaw);
        ending(c,m,t,centre,yaw);
    }

    // ───────────────────────────── as she springs ─────────────────────────────

    /** As she springs off the ground (H_LEAP): a ring of light opening round her feet, a soft flash, streaks of light shooting up round her. */
    static void takeoff(Fx c,EffectMesh m,float t){
        float at=IsosangRules.H_LEAP;if(t<at-.01f||t>=at+.45f)return;
        Vec3d g=SwordFormation.spot(c.cast).add(0,.06,0);
        float a=Ease.smooth(t,at-.01f,at+.01f),u=Ease.unit(t,at,at+.4f);
        FxLib.ring(m,g,.3+2.6*Ease.out(t,at,at+.35f),.14,AZURE,.7f*a*(1-u));
        FxLib.ring(m,g,.2+1.6*Ease.out(t,at,at+.25f),.05,WHITE,.6f*a*(1-u));
        m.softDisc(g.add(0,.4,0),1.8,BLUE,.32f*a*(1-u));
        float up=a*(1-Ease.smooth(t,at+.08f,at+.3f));
        for(int k=0;k<14;k++){
            int seed=k*37+11;double az=Math.PI*2*(k+Ease.hash(seed))/14,r=.35+.3*Ease.hash(seed+1),h=(1.2+1.6*Ease.hash(seed+2))*Ease.out(t,at,at+.2f);
            Vec3d p0=g.add(Math.cos(az)*r,.05,Math.sin(az)*r),p1=p0.add(Math.cos(az)*.15*h,h,Math.sin(az)*.15*h);
            m.needle(p0,p1,.06,.35,k%4==0?WHITE:k%2==0?PALE:AZURE,.75f*up);
        }
    }

    /**
     * As she alights on the pommel of the sword under her (H_LANDED) and as she drops off it to the ground at the end
     * (H_DOWN): a small ring of light round her feet and a few stars.
     */
    static void landing(Fx c,EffectMesh m,float t){
        Vec3d spot=SwordFormation.spot(c.cast);
        alight(c,m,t,IsosangRules.H_LANDED,spot.add(0,IsosangRules.FORMATION_STAND+.03,0),1.1);
        float e=SwordFormation.end(c.cast);
        if(e>=IsosangRules.H_END-1e-3)alight(c,m,t,IsosangRules.H_DOWN,spot.add(0,.06,0),1.6);
    }
    static void alight(Fx c,EffectMesh m,float t,float at,Vec3d g,double big){
        if(t<at-.01f||t>=at+.4f)return;
        float a=Ease.smooth(t,at-.01f,at+.01f),u=Ease.unit(t,at,at+.35f);
        FxLib.ring(m,g,.15+big*Ease.out(t,at,at+.3f),.08,AZURE,.65f*a*(1-u));FxLib.ring(m,g,.1+.6*big*Ease.out(t,at,at+.22f),.03,WHITE,.6f*a*(1-u));
        for(int k=0;k<5;k++){
            double az=Math.PI*2*(k+.3)/5,r=.25+.7*big*Ease.out(t,at,at+.3f);
            glint8(m,facing(g.add(Math.cos(az)*r,.1+.3*u,Math.sin(az)*r),c.camera),.12*(1-u),k%2==0?WHITE:PALE,.75f*a*(1-u));
        }
    }

    // ───────────────────────────── the burst in the middle ─────────────────────────────

    /**
     * As the black spike breaks in the middle (H_PLANT, after SwordFormation.burst's crown): a pillar of light up out of
     * it, gone in half a second, a wide crown of light needles, a star, sparks falling back. No ring runs out over the
     * ground from it (2.82.6: the ink's own ripples do).
     */
    static void crown(Fx c,EffectMesh m,float t,Vec3d centre){
        float at=IsosangRules.H_PLANT;if(t<at-.01f||t>=at+.6f)return;
        float k=Ease.smooth(t,at-.01f,at+.02f)*(1-Ease.smooth(t,at+.08f,at+.5f));
        Vec3d o=centre.add(0,.05,0);
        if(k>.01f){
            double rise=Ease.out(t,at,at+.12f);
            m.softLine(o,o.add(0,22*rise,0),3.0,BLUE,.3f*k);m.softLine(o,o.add(0,18*rise,0),1.1,AZURE,.48f*k);m.line(o,o.add(0,15*rise,0),.13,WHITE,.7f*k);
            // (a small star: a big one reads as a cross laid over the picture)
            Vec3d s=facing(o.add(0,1.2,0),c.camera);float st=1-Ease.smooth(t,at+.05f,at+.3f);glint8(m,s,1.4*(1-.4*Ease.unit(t,at,at+.3f)),PALE,.45f*k*st);glint8(m,s,.55,WHITE,.6f*k*st);
        }
        float crown=Ease.smooth(t,at-.01f,at+.01f)*(1-Ease.smooth(t,at+.06f,at+.3f));
        if(crown>.01f)for(int j=0;j<24;j++){
            int seed=j*41+5;double az=Math.PI*2*(j+.5*Ease.hash(seed))/24,el=Math.toRadians(20+50*Ease.hash(seed+1)),len=(2+2.6*Ease.hash(seed+2))*Ease.out(t,at,at+.1f);
            Vec3d dir=new Vec3d(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el));
            m.needle(o.add(dir.multiply(.4)),o.add(dir.multiply(.4+len)),.16,.35,j%3==0?WHITE:j%3==1?PALE:AZURE,.8f*crown);
        }
        // sparks out of it, falling back
        double s0=t-at;if(s0>=0&&s0<.55)for(int j=0;j<30;j++){
            int seed=j*59+17;double az=Math.PI*2*Ease.hash(seed),out=3+6*Ease.hash(seed+1),up=4+6*Ease.hash(seed+2);
            Vec3d q=spark(o,az,out,up,s0),q0=spark(o,az,out,up,Math.max(0,s0-.035));
            m.needle(q0,q,.06,.4,j%4==0?WHITE:j%2==0?PALE:AZURE,.85f*(1-Ease.smooth((float)s0,.3f,.55f)));
        }
    }
    /** A spark {@code s} seconds after it leaves {@code o}: out {@code out} and up {@code up} blocks a second, falling. */
    static Vec3d spark(Vec3d o,double az,double out,double up,double s){return o.add(Math.cos(az)*out*s,.2+up*s-6*s*s,Math.sin(az)*out*s);}

    // ───────────────────────────── as the swords turn silver ─────────────────────────────

    /**
     * As the swords turn silver (SILVER0 to SILVER1): a shimmer of light runs up each from its point to its pommel, the
     * nearer the middle the sooner (a wave of it out over the formation, {@link #SHIMMER_SPREAD}), a soft ring of light at
     * its foot as it passes.
     */
    static void shimmer(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        if(t<SwordFormation.SILVER0-.05f||t>=SwordFormation.SILVER0+SHIMMER_SPREAD+SHIMMER+.1f)return;
        for(var b:SwordFormation.layout(c.cast.id())){
            float s0=shimmerAt(b),u=Ease.unit(t,s0,s0+SHIMMER);if(u<=0||u>=1)continue;
            Vec3d top=SwordFormation.planted(centre,yaw,b);var f=SwordFormation.frame(SwordFormation.down(b),SwordFormation.spinAt(b,t));
            double y=SwordFormation.S_LEN-(SwordFormation.S_LEN-.05)*Ease.smooth(u,0,1);float a=(float)Math.sin(Math.PI*u);
            Vec3d q0=SwordFormation.at(top,f,new Vec3d(0,Math.max(0,y-.24),0)),q1=SwordFormation.at(top,f,new Vec3d(0,Math.min(SwordFormation.S_LEN,y+.24),0)),q=SwordFormation.at(top,f,new Vec3d(0,y,0));
            m.softLine(q0,q1,.24*SwordFormation.THIN,AZURE,.55f*a);m.line(q0,q1,.03*SwordFormation.THIN,WHITE,.85f*a);
            glint8(m,facing(q,c.camera),.22,WHITE,.65f*a);
            float g=1-Ease.unit(t,s0,s0+.4f);Vec3d foot=SwordFormation.foot(centre,yaw,b);
            if(g>.01f)SwordFormation.groundBand(m,foot.add(0,.05,0),.2+1.2*Ease.out(t,s0,s0+.4f),.06,PALE,.4f*g,b.seed(),t);
        }
    }
    /** When the shimmer starts up a sword: the nearer the middle the sooner. */
    static float shimmerAt(SwordFormation.Blade b){return SwordFormation.SILVER0+SHIMMER_SPREAD*(float)Math.min(1,Math.hypot(b.fw(),b.rt())/IsosangRules.FORMATION_RADIUS);}
    static final float SHIMMER=.3f,SHIMMER_SPREAD=.3f;

    // ───────────────────────────── while it stands ─────────────────────────────

    /**
     * Stars drifting up over the formation while it stands: {@link #MOTES} at a time, each somewhere over it (out to its
     * edge, low), rising slowly and swaying, twinkling, coming in and going over {@link #MOTE_LIFE} seconds or so, another
     * somewhere else as each goes; white, pale blue and lilac, small (fireflies of starlight, not a snowfall).
     */
    static void motes(Fx c,EffectMesh m,float t,Vec3d centre){
        float on=SwordFormation.standing(c.cast,t);if(on<=.01f)return;
        int base=c.cast.id().hashCode()*17+333;
        for(int k=0;k<MOTES;k++){
            double[] life=SwordFormation.shed(base+k*29,k,MOTES,MOTE_LIFE,t);double u=life[0];int seed=(int)life[1];
            float a=(float)(on*Ease.smooth((float)u,0,.2f)*(1-Ease.smooth((float)u,.7f,1)));if(a<=.01f)continue;
            double rr=IsosangRules.FORMATION_EMPTY*.5+(IsosangRules.FORMATION_RADIUS-IsosangRules.FORMATION_EMPTY*.5)*Math.sqrt(Ease.hash(seed+1)),az=Math.PI*2*Ease.hash(seed+2);
            double age=u*MOTE_LIFE,sway=.35*Math.sin(age*1.3+seed);
            Vec3d q=centre.add(Math.cos(az)*rr+sway*Math.sin(az),.3+1.4*Ease.hash(seed+3)+.32*age,Math.sin(az)*rr-sway*Math.cos(az));
            float tw=twinkle(t,seed)*near(q,c.camera)*Ease.smooth((float)q.distanceTo(c.camera),2.6f,5f);if(tw<=.01f)continue;int col=k%5==0?LILAC:k%3==0?WHITE:PALE;
            Vec3d o=facing(q,c.camera);
            glint8(m,o,(.07+.06*Ease.hash(seed+4))*(.7+.3*tw),col,.8f*a*tw);m.softDisc(o,.16,AZURE,.2f*a);
        }
    }
    static final int MOTES=44;
    static final float MOTE_LIFE=7f;

    /**
     * Threads of light drawn now and then between swords near one another while it stands, as a constellation's lines
     * (each {@link #THREAD_EVERY} seconds two more, each from one sword's guard to the nearest sword's but one of its
     * own: drawn across over a fifth of a second, a star at each end, holding, fading): faint, thin.
     */
    static void threads(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        float on=SwordFormation.standing(c.cast,t);if(on<=.01f)return;
        var all=SwordFormation.layout(c.cast.id());if(all.length<2)return;int[] near=nearest(c.cast.id(),all);
        int slot0=(int)Math.floor(t/THREAD_EVERY);
        for(int slot=slot0-2;slot<=slot0;slot++){
            float born=slot*THREAD_EVERY,s=t-born;if(s<0||s>=THREAD_LIFE)continue;
            for(int j=0;j<2;j++){
                int seed=c.cast.id().hashCode()+slot*131+j*17;int i=(int)Math.floor(Ease.hash(seed)*all.length)%all.length,n=near[i];
                var a0=all[i];var b0=all[n];
                Vec3d p=guard(centre,yaw,a0,t),q=guard(centre,yaw,b0,t);
                float draw=Ease.out(s,0,.22f),alpha=on*Ease.smooth(s,0,.06f)*(1-Ease.smooth(s,THREAD_LIFE-.6f,THREAD_LIFE));if(alpha<=.01f)continue;
                Vec3d to=p.lerp(q,draw);
                // (not across the camera's face: gone within a few blocks of it)
                alpha*=Ease.smooth((float)Math.min(p.distanceTo(c.camera),q.distanceTo(c.camera)),3f,6f);if(alpha<=.01f)continue;
                m.softLine(p,to,.12,AZURE,.2f*alpha);m.line(p,to,.012,PALE,.6f*alpha);
                glint8(m,facing(p,c.camera),.1,WHITE,.7f*alpha);
                if(draw>.95f)glint8(m,facing(q,c.camera),.1,WHITE,.7f*alpha);
                else glint8(m,facing(to,c.camera),.13,WHITE,.8f*alpha);
            }
        }
    }
    static final float THREAD_EVERY=1.4f,THREAD_LIFE=1.9f;
    /** Where a sword's guard is at {@code t}, standing. */
    static Vec3d guard(Vec3d centre,float yaw,SwordFormation.Blade b,float t){
        return SwordFormation.at(SwordFormation.planted(centre,yaw,b),SwordFormation.frame(SwordFormation.down(b),SwordFormation.spinAt(b,t)),new Vec3d(0,SwordFormation.S_GUARD,0));
    }
    private static final Map<UUID,int[]> NEAREST=new LinkedHashMap<>();
    /** For each sword of the formation, its nearest other sword (by where they strike the ground). */
    static int[] nearest(UUID id,SwordFormation.Blade[] all){
        var n=NEAREST.get(id);if(n!=null)return n;
        n=new int[all.length];
        for(int i=0;i<all.length;i++){
            double best=1e9;int bi=(i+1)%all.length;
            for(int j=0;j<all.length;j++){if(j==i)continue;double d=Math.hypot(all[i].fw()-all[j].fw(),all[i].rt()-all[j].rt());if(d<best){best=d;bi=j;}}
            n[i]=bi;
        }
        NEAREST.put(id,n);while(NEAREST.size()>16)NEAREST.remove(NEAREST.keySet().iterator().next());
        return n;
    }

    /**
     * How long ago (seconds) the formation last struck again on its five-second beat (IsosangRules.formationStrikes:
     * every FORMATION_EVERY ticks after the swords strike, while it stands), or -1 before the first of them.
     */
    static float pulseAge(StandClient.Cast c,float t){return pulseAge(t,SwordFormation.end(c));}
    /** The same for a formation ending at {@code end} (seconds into it). */
    static float pulseAge(float t,float end){
        float first=IsosangRules.FORMATION_IMPACT_AT/20f,every=IsosangRules.FORMATION_EVERY/20f;
        if(t<first+every-1e-4f)return -1;
        float k=(float)Math.floor((t-first)/every+1e-4f),at=first+k*every;
        if(at>=end)return -1;
        return Math.max(0,t-at);
    }
    /**
     * Every five seconds as it strikes again: a ring of light running out over its ground from the empty middle's edge
     * to its rim, and every sword lit from its guard to its point in a blink, a ring at its foot; so its beat is seen
     * (the five a time it deals).
     */
    static void pulse(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        float s=pulseAge(c.cast,t);if(s<0||s>=PULSE)return;
        float a=Ease.smooth(s,0,.03f)*(1-Ease.smooth(s,.25f,PULSE));
        double r=IsosangRules.FORMATION_EMPTY+(IsosangRules.FORMATION_RADIUS-IsosangRules.FORMATION_EMPTY)*Ease.out(s,0,.55f);
        Vec3d g=centre.add(0,.07,0);
        FxLib.ring(m,g,r,.1,AZURE,.55f*a);FxLib.ring(m,g,r,.035,WHITE,.6f*a);
        SwordFormation.groundBand(m,g,r-.5,.5,BLUE,.18f*a,7,t);
        for(var b:SwordFormation.layout(c.cast.id())){
            // its light running down it from its guard to its point as the ring passes under it
            double rb=Math.hypot(b.fw(),b.rt());float sb=s-(float)(.55*(rb-IsosangRules.FORMATION_EMPTY)/(IsosangRules.FORMATION_RADIUS-IsosangRules.FORMATION_EMPTY));
            if(sb<0||sb>=.3f)continue;float u=sb/.3f,ga=(float)Math.sin(Math.PI*u);
            Vec3d top=SwordFormation.planted(centre,yaw,b);var f=SwordFormation.frame(SwordFormation.down(b),SwordFormation.spinAt(b,t));
            Vec3d g0=SwordFormation.at(top,f,new Vec3d(0,SwordFormation.S_GUARD,0)),g1=SwordFormation.at(top,f,new Vec3d(0,SwordFormation.S_GUARD+(SwordFormation.S_LEN-SwordFormation.S_GUARD)*Ease.out(u,0,.6f),0));
            m.softLine(g0,g1,.2*SwordFormation.THIN,AZURE,.45f*ga);m.line(g0,g1,.025*SwordFormation.THIN,WHITE,.7f*ga);
            Vec3d foot=SwordFormation.foot(centre,yaw,b);
            SwordFormation.groundBand(m,foot.add(0,.05,0),.2+.9*u,.05,PALE,.35f*(1-u),b.seed(),t);
        }
    }
    static final float PULSE=.9f;

    // ───────────────────────────── as it ends ─────────────────────────────

    /** As it ends (its flare): a ring of light running out past its rim, and stars rising off every sword as it goes. */
    static void ending(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        float e=SwordFormation.end(c.cast),s=t-e;if(s<-.01f||s>=1.2f)return;
        float a=Ease.smooth(s,-.01f,.02f)*(1-Ease.smooth(s,.25f,.7f));
        if(a>.01f){
            double r=1+(IsosangRules.FORMATION_RADIUS+4)*Ease.out(s,0,.6f);Vec3d g=centre.add(0,.08,0);
            FxLib.ring(m,g,r,.16,CYAN,.55f*a);FxLib.ring(m,g,r,.05,WHITE,.6f*a);
            SwordFormation.groundBand(m,g,r-.7,.7,BLUE,.2f*a,11,t);
        }
        for(var b:SwordFormation.layout(c.cast.id())){
            Vec3d top=SwordFormation.planted(centre,yaw,b),foot=SwordFormation.foot(centre,yaw,b);
            for(int k=0;k<8;k++){
                int seed=b.seed()*19+k*7+3;float born=.02f+.25f*(float)Ease.hash(seed);float u=Ease.unit(s,born,born+.8f);if(u<=0||u>=1)continue;
                Vec3d q=foot.lerp(top,Ease.hash(seed+1)).add((Ease.hash(seed+2)-.5)*.5*u,2.6*Ease.out(u,0,1),(Ease.hash(seed+3)-.5)*.5*u);
                glint8(m,facing(q,c.camera),.16*(1-.6*u),k%3==0?WHITE:k%3==1?CYAN:PALE,.85f*(1-u));
            }
        }
    }

    // ───────────────────────────── lights ─────────────────────────────

    /** The light they cast: as she springs, the pillar in the middle, each beat, the ring as it ends. */
    static void frame(Fx c,FxFrame f){
        float t=c.sec;Vec3d centre=SwordFormation.centre(c.cast);
        float up=FxLib.kick(t,IsosangRules.H_LEAP,.2f);if(up>.01f)f.light(SwordFormation.spot(c.cast).add(0,1,0),0x9fc8ff,8,.9f*up);
        float pillar=FxLib.kick(t,IsosangRules.H_PLANT+.02f,.3f);if(pillar>.01f)f.light(centre.add(0,4,0),0xb8d8ff,20,1.2f*pillar);
        float s=pulseAge(c.cast,t);if(s>=0&&s<PULSE)f.light(centre.add(0,1,0),0x8cc4ff,16,.7f*(1-Ease.smooth(s,.1f,PULSE)));
        float e=t-SwordFormation.end(c.cast);if(e>=0&&e<.8f)f.light(centre.add(0,1.5,0),0x7fe6ff,24,.6f*(1-Ease.smooth(e,.1f,.8f)));
    }
}
