package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.stand.GoldBeatdown.Beat;
import dev.sangwoo.celestialfall.client.stand.StandBeatdown.Body;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import static dev.sangwoo.celestialfall.client.stand.GoldBeatdown.*;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.*;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.burst;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;

/**
 * The light of Gold Experience's beatdown (2.150.0), on the effect layer with the muda rush's (StandRushFx draws it),
 * in its gold, nothing over the whole screen: as the opening punch winds up light gathers into the drawn fist and
 * trails it as it is thrown, bursting where it lands (a guard it breaks bursts blue); through the cutscene every fist
 * of the rush bursts where it lands on the one struck, light sprayed out of their back, a ring of pressure running on
 * every few fists and the air streaming past them; as the "WRYYY" drives them up, rings race up after them and light
 * streaks up from the ground under the stand; the last blow gathers light into the drawn fist through its long
 * wind-up and lands in the biggest burst, rings racing on along it, a lance of light and sparks; a trail of light
 * follows the one struck as they fly, and where they come down a ring runs out over the ground, dust and chunks of the
 * floor thrown up. Each of them lights the world round it. All of it follows the bodies as the cutscene shows them.
 */
public final class GoldBeatdownFx {
    /** A beatdown near the camera this frame: its clock and the stand's body as drawn. */
    record Shown(Beat b,float t,StandPresence.Body stand){}
    private static final List<Shown> FRAME=new ArrayList<>();
    private static Vec3d eye=Vec3d.ZERO;
    private GoldBeatdownFx(){}

    static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        FRAME.clear();eye=camera;
        if(mc.world==null)return false;
        for(var b:GoldBeatdown.all()){
            float t=b.age(delta);
            if(t<0||t>b.over()+1.6f)continue;
            Vec3d at=b.scene?b.from:null;
            if(at==null){var user=mc.world.getPlayerByUuid(b.user);if(user==null)continue;at=user.getPos();}
            if(at.squaredDistanceTo(camera)>96*96)continue;
            FRAME.add(new Shown(b,t,GoldPresence.body(b.user)));
        }
        return !FRAME.isEmpty();
    }

    // ───────────────────────────── where ─────────────────────────────

    static Vec3d way(Beat b,float t){
        double l=Math.toRadians(lean(b,t));
        return b.f.multiply(Math.cos(l)).add(0,Math.sin(l),0);
    }
    /** The stand's right fist where it is drawn at {@code t} in the punch's stances (the opening punch and the last blow). */
    static Vec3d fist(Shown s,float t){
        var body=s.stand;if(body==null)return null;
        float[] w=new float[NAMES.size()];stances(s.b,t,w);
        float size=armSize(s.b,t);double a=0,rt=0,up=0,sum=0;
        for(int st:new int[]{GUARD,OVER_WIND,OVER_PUNCH}){
            if(w[st]<=0)continue;
            float[] f=GoldRush.grown(st,1,size);a+=f[0]*w[st];rt+=f[1]*w[st];up+=f[2]*w[st];sum+=w[st];
        }
        if(sum<=.2)return null;
        a/=sum;rt/=sum;up/=sum;
        // tipped as the stand leans about its waist
        double l=Math.toRadians(-lean(s.b,t)),q=up-WAIST;
        Vec3d f=StandPunchFx.way(body.yaw()),r=StandPunchFx.right(f);
        return body.at().add(f.multiply(a*Math.cos(l)+q*Math.sin(l))).add(r.multiply(rt)).add(0,WAIST+q*Math.cos(l)-a*Math.sin(l),0);
    }
    /** Where the rush's fist {@code k} lands on the one struck: on their front at their face, chest or belly, a little to the side it comes in on, as they are when it lands. */
    static Vec3d spot(Beat b,int k){
        int seed=b.seed+k*7919,h=fistHeight(fistStance(b,k));
        double up=(h==1?1.6:h==0?1.3:.95)+(Ease.hash(seed+6)-.5)*.14,rt=fistSide(k)*.08+(Ease.hash(seed+5)-.5)*.16;
        Body v=victim(b,fistAt(k));
        return point(b,v,-.3,up).add(b.r.multiply(rt));
    }
    /** How much a burst at {@code p} shows from where the camera is: none right in front of it (a shot from the one struck's eyes), all from a block on. */
    static float near(Vec3d p){return Ease.smooth((float)p.distanceTo(eye),.45f,1.1f);}
    static float burstAlpha(float t,float at,float life){
        if(t<at-.01f||t>at+life)return 0;
        return Ease.smooth(t,at-.006f,at+.012f)*(float)Math.pow(1-Ease.unit(t,at,at+life),.7);
    }
    /** Where the last blow lands on the one struck: their face. */
    static Vec3d finalAt(Beat b){return point(b,launch(b),-.3,1.5);}

    // ───────────────────────────── the passes ─────────────────────────────

    static void alpha(EffectMesh m){
        for(var s:FRAME){
            var b=s.b;float t=s.t;
            // ink under the big bursts, a little bigger, for their dark edge
            float o=burstAlpha(t,CUT,.26f);
            if(o>0&&b.landed()&&b.met>=0){Vec3d c=punchAt(s);if(c!=null)burst(m,c,1.5*1.14,.36,11,b.seed,INK,INK,.75f*o,.75f*o);}
            if(!b.scene)continue;
            float a=burstAlpha(t,FINAL,.4f);
            if(a>0)burst(m,finalAt(b),2.7*1.14,.36,13,b.seed+31,INK,INK,.78f*a,.78f*a);
        }
    }
    /** Where the opening punch landed: on whom it met (the cutscene's one struck as they stood; anyone else where the fist stopped). */
    static Vec3d punchAt(Shown s){
        var b=s.b;
        if(b.scene)return b.victimFrom.add(0,1.25,0).subtract(b.f.multiply(.3));
        return fist(s,CUT);
    }
    static void additive(EffectMesh m){
        for(var s:FRAME){
            var b=s.b;float t=s.t;
            opener(m,s,t);
            if(!b.scene)continue;
            rush(m,s,t);
            wry(m,s,t);
            last(m,s,t);
        }
    }
    /** Light gathered into a drawn fist at {@code q}, {@code c} of the way charged. */
    static void gather(EffectMesh m,Vec3d q,Vec3d w,float t,float c,int seed,double size){
        Vec3d r=StandPunchFx.right(w),up=StandPunchFx.up(w,r);
        float pulse=.5f+.5f*(float)Math.sin(t*Math.PI*2*(3+5*c));
        m.softDisc(q,(.34+.1*pulse)*size,AMBER,.34f*c);m.softDisc(q,(.16+.04*pulse)*size,GOLD,.55f*c);m.star(q,(.12+.1*c)*size,WHITE,.5f*c);
        for(int k=0;k<12;k++){
            double u=(t*(2.2+1.4*Ease.hash(k*7+seed))+Ease.hash(k*13))%1;
            double a=Ease.hash(k*5+1)*Math.PI*2,e=(Ease.hash(k*11+2)-.5)*2.2;
            Vec3d d=r.multiply(Math.cos(a)*Math.cos(e)).add(up.multiply(Math.sin(e))).add(w.multiply(Math.sin(a)*Math.cos(e)));
            Vec3d at=q.add(d.multiply((.15+.95*(1-u))*size));
            m.needle(at,at.subtract(d.multiply(.2+.1*u)),.035,.45,k%3==0?WHITE:k%3==1?PALE:GOLD,(float)(c*u*.85));
        }
        for(int k=0;k<2;k++){
            double u=(t*(1.6+2.2*c)+k*.5)%1;
            FxLib.ringPlane(m,q,r,up,(.12+.75*(1-u))*size,.035,k==0?GOLD:PALE,(float)(c*u*.6));
        }
    }
    /** The opening punch: light gathers into the drawn fist, trails it as it is thrown, bursts where it lands (blue on a guard it breaks). */
    static void opener(EffectMesh m,Shown s,float t){
        var b=s.b;if(t>CUT+.5f)return;
        boolean stopped=!Float.isNaN(b.stopped);
        float c=Ease.smooth(t,.06f,.4f)*(1-strike(t))*(stopped?1-Ease.smooth(t,b.stopped,b.stopped+STOPPED):1);
        Vec3d q=fist(s,stopped?Math.min(t,b.stopped):t);
        if(c>0&&q!=null)gather(m,q,b.f,t,c,b.seed,1);
        if(!stopped&&t>LOAD&&t<CUT+.1f&&q!=null){
            float a=Ease.smooth(t,LOAD+.03f,LOAD+.09f)*(1-Ease.smooth(t,CUT,CUT+.1f));
            Vec3d from=fist(s,LOAD);
            if(a>0&&from!=null){
                Vec3d along=q.subtract(from);
                if(along.lengthSquared()>1e-4){
                    Vec3d tail=from.add(along.multiply(.1));
                    m.softTaper(tail,q,.03,.52,AMBER,.5f*a);m.softTaper(tail,q,.015,.26,GOLD,.66f*a);
                    m.needle(tail,q,.09,.85,WHITE,.45f*a);m.softDisc(q,.3,GOLD,.45f*a);
                }
            }
        }
        float a=burstAlpha(t,CUT,.26f);
        if(a<=0||stopped||!b.landed())return;
        Vec3d p=punchAt(s);if(p==null)return;
        Vec3d r=b.r,up=FxLib_UP;
        if(b.met<0){
            // on no one: a small burst of air at the fist
            burst(m,p,.5,.36,8,b.seed,PALE,GOLD,.6f*a,.5f*a);
            FxLib.ringPlane(m,p,r,up,.15+.7*Ease.out(t,CUT,CUT+.22f),.06,GOLD,.6f*a);
            return;
        }
        int core=b.broke?GUARD_LIGHT:PALE,rim=b.broke?GUARD_BLUE:GOLD;
        float u=Ease.unit(t,CUT,CUT+.26f);
        burst(m,p,1.5*(.75+.25*Ease.out(u,0,.4f)),.36,11,b.seed,core,rim,.9f*a,.78f*a);
        m.star(p,.8,WHITE,.5f*a*(1-u));m.softDisc(p,1.3,b.broke?GUARD_BLUE:AMBER,.32f*a);
        for(int k=0;k<3;k++){
            float at=CUT+.025f*k,v=Ease.unit(t,at,at+.26f);if(t<at||v>=1)continue;
            FxLib.ringPlane(m,p.add(b.f.multiply(.1+.9*k*Ease.out(t,at,at+.26f))),r,up,.25+(.9+.3*k)*Ease.out(t,at,at+.22f),.1,k==1?core:rim,.8f*(1-v));
        }
        for(int k=0;k<14;k++){
            double ang=Ease.hash(b.seed+k*5)*Math.PI*2,spread=.25+.6*Ease.hash(b.seed+k*7);
            Vec3d d=b.f.multiply(b.broke?-.4:1).add(r.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
            double len=(.5+1.1*Ease.hash(b.seed+k*11))*Ease.out(u,0,.6f);
            Vec3d at=p.add(d.multiply(.2+len*.6));
            m.needle(at,at.add(d.multiply(len*.5+.06)),.05,.3,k%3==0?WHITE:k%2==0?core:rim,a);
        }
    }
    private static final Vec3d FxLib_UP=new Vec3d(0,1,0);
    /** The rush: every fist bursts where it lands on the one struck; a ring of pressure runs on every few fists; the air streams past them. */
    static void rush(EffectMesh m,Shown s,float t){
        var b=s.b;int n=fists(t);if(n<=0||t>WIND+.4f)return;
        for(int k=Math.max(0,n-3);k<n;k++){
            float age=t-fistAt(k);if(age<0||age>=GoldRushFx.BLOW)continue;
            float u=age/GoldRushFx.BLOW,a=Ease.smooth(age,0,.015f)*(float)Math.pow(1-u,.8);
            Vec3d at=spot(b,k);a*=near(at);if(a<=0)continue;
            int seed=b.seed+k*131;
            GoldRushFx.struck(m,new GoldRushFx.Hit(at,.5*(.8+.4*Ease.hash(seed+5))*(.7+.3*Ease.out(u,0,.3f)),a,u,way(b,fistAt(k)),false,seed));
        }
        float on=barrage(t);if(on<=0)return;
        Vec3d w=way(b,t),r=b.r,up=StandPunchFx.up(w,r);
        Vec3d c=chest(b,victim(b,t,false));
        // a ring of pressure every four fists, running on past them
        for(int k=Math.max(0,n-8);k<n;k++){
            if(k%4!=0)continue;
            float at=fistAt(k),v=Ease.unit(t,at,at+.3f);if(t<at||v>=1)continue;
            Vec3d o=c.add(w.multiply(.3+2.2*Ease.out(t,at,at+.3f)));
            FxLib.ringPlane(m,o,r,up,.4+.9*v,.07,k%8==0?PALE:GOLD,.5f*on*(1-v)*near(o));
        }
        // the air streaming past them along the fists' way
        for(int k=0;k<16;k++){
            float period=.18f+.1f*(float)Ease.hash(k*7+b.seed),phase=(float)Ease.hash(k*11+b.seed)*period;
            float u=((t+phase)%period)/period;
            double side=(Ease.hash(k*13+b.seed)-.5)*1.9,lift=(Ease.hash(k*17+b.seed)-.5)*1.9,len=.4+.6*Ease.hash(k*19+b.seed);
            Vec3d start=c.add(r.multiply(side)).add(up.multiply(lift)).add(w.multiply(-.9+3.2*u));
            float a=on*(float)Math.sin(Math.PI*u)*.6f*near(start);
            m.needle(start,start.add(w.multiply(len)),.035,.7,k%6==0?WHITE:k%3==1?PALE:k%2==0?AMBER:GOLD,a);
        }
    }
    /** "WRYYY": rings race up after the one struck as they are driven up, and light streaks up from the ground under the stand. */
    static void wry(EffectMesh m,Shown s,float t){
        var b=s.b;if(t<WRY-.05f||t>WRY_END+.3f)return;
        float on=Ease.smooth(t,WRY-.05f,WRY+.15f)*(1-Ease.smooth(t,WRY_END-.2f,WRY_END+.3f));
        Vec3d c=chest(b,victim(b,t,false));
        Vec3d ground=new Vec3d(c.x-b.f.x*.8,b.from.y+.06,c.z-b.f.z*.8);
        // as it begins: a ring runs out over the ground under them
        float u0=Ease.unit(t,WRY,WRY+.5f);
        if(t>=WRY&&u0<1){
            FxLib.ring(m,ground,.4+4.2*Ease.out(t,WRY,WRY+.45f),.4,GOLD,.65f*(1-u0));
            FxLib.ring(m,ground,.3+2.8*Ease.out(t,WRY+.05f,WRY+.5f),.26,PALE,.5f*(1-u0));
        }
        // rings racing up after them, one every 0.35 s
        for(int k=0;k<4;k++){
            float period=.35f,u=((t-WRY+k*period*.25f)%period)/period;
            Vec3d o=c.add(0,-2.2+2.6*u,0).subtract(b.f.multiply(.4));
            FxLib.ring(m,o,.5+.9*u,.12,k%2==0?GOLD:PALE,.5f*on*(1-u)*Ease.smooth(u,0,.1f)*near(o));
        }
        // light streaking up from the ground past the stand
        for(int k=0;k<20;k++){
            float period=.3f+.2f*(float)Ease.hash(k*7+b.seed+3),u=((t+(float)Ease.hash(k*11+b.seed)*period)%period)/period;
            double ang=Ease.hash(k*5+b.seed)*Math.PI*2,rad=.5+1.3*Ease.hash(k*13+b.seed);
            double top=Math.max(2,c.y-ground.y+1.5);
            Vec3d p=ground.add(Math.cos(ang)*rad,top*u,Math.sin(ang)*rad);
            m.needle(p,p.add(0,.5+.6*Ease.hash(k*19+b.seed),0),.04,.6,k%5==0?WHITE:k%3==0?PALE:k%2==0?AMBER:GOLD,on*(float)Math.sin(Math.PI*u)*.55f*near(p));
        }
    }
    /** The last blow: light gathers into the drawn fist through its wind-up; it lands in the biggest burst, rings racing on along it, a lance of light and sparks; a trail of light after the one struck as they fly; a ring over the ground where they come down. */
    static void last(EffectMesh m,Shown s,float t){
        var b=s.b;if(t<WIND)return;
        Vec3d w=way(b,Math.min(t,FINAL)),r=b.r,up=StandPunchFx.up(w,r);
        float c=Ease.smooth(t,WIND+.2f,FINAL-.2f)*(1-Ease.smooth(t,FINAL-.12f,FINAL));
        Vec3d q=fist(s,t);
        if(c>0&&q!=null)gather(m,q,w,t,c,b.seed+5,1.5);
        if(t>FINAL-.17f&&t<FINAL+.1f&&q!=null){
            float a=Ease.smooth(t,FINAL-.14f,FINAL-.08f)*(1-Ease.smooth(t,FINAL,FINAL+.1f));
            Vec3d from=fist(s,FINAL-.17f);
            if(a>0&&from!=null){
                Vec3d along=q.subtract(from);
                if(along.lengthSquared()>1e-4){
                    Vec3d tail=from.add(along.multiply(.1));
                    m.softTaper(tail,q,.04,.7,AMBER,.55f*a);m.softTaper(tail,q,.02,.36,GOLD,.7f*a);
                    m.needle(tail,q,.12,.85,WHITE,.5f*a);m.softDisc(q,.42,GOLD,.5f*a);
                }
            }
        }
        Vec3d p=finalAt(b);
        float a=burstAlpha(t,FINAL,.4f)*near(p);
        if(a>0){
            float u=Ease.unit(t,FINAL,FINAL+.4f);
            burst(m,p,2.7*(.75+.25*Ease.out(u,0,.4f)),.36,13,b.seed+31,PALE,GOLD,.92f*a,.8f*a);
            m.star(p,1.3,WHITE,.55f*a*(1-u));m.softDisc(p,2.2,AMBER,.36f*a);m.softDisc(p,.7,WHITE,.5f*a*(1-u));
        }
        // rings racing on along the blow
        for(int k=0;k<6;k++){
            float at=FINAL+.02f*k,v=Ease.unit(t,at,at+.36f);if(t<at||v>=1)continue;
            Vec3d o=p.add(w.multiply(.3+(1.4+1.3*k)*Ease.out(t,at,at+.36f)));
            FxLib.ringPlane(m,o,r,up,.4+(1.1+.35*k)*Ease.out(t,at,at+.3f),.14,k%2==0?GOLD:PALE,.85f*(1-v)*Ease.smooth(t,at,at+.015f));
        }
        // a lance of light along it
        float lance=Ease.smooth(t,FINAL-.02f,FINAL+.005f)*(1-Ease.smooth(t,FINAL+.08f,FINAL+.26f));
        if(lance>0){
            Vec3d end=p.add(w.multiply(9));
            m.softTaper(p,end,.5,.05,AMBER,.5f*lance);m.softTaper(p,end,.26,.03,GOLD,.7f*lance);m.needle(p,end,.1,.2,WHITE,.6f*lance);
        }
        // sparks thrown on, falling
        float life=.7f,age=t-FINAL;
        if(age>=0&&age<life){
            float u=age/life;
            for(int k=0;k<60;k++){
                int seed=b.seed+k*17;
                double az=(Ease.hash(seed+3)-.5)*2.4,el=(Ease.hash(seed+7)-.4)*1.5,speed=4+7*Ease.hash(seed+9);
                Vec3d d=w.multiply(Math.cos(el)*Math.cos(az)).add(r.multiply(Math.cos(el)*Math.sin(az))).add(up.multiply(Math.sin(el))).normalize();
                Vec3d at=p.add(d.multiply(speed*life*(1-Math.pow(1-u,2))*.5)).add(0,-2.2*age*age,0);
                m.needle(at.subtract(d.multiply(.2*(1-u)+.03)),at,.055,.3,k%4==0?WHITE:k%3==0?PALE:k%2==0?AMBER:GOLD,(1-u)*Ease.smooth(age,0,.015f));
            }
        }
        // the one struck trails light as they fly
        float fly=Ease.smooth(t,FINAL+FINAL_STOP-.02f,FINAL+FINAL_STOP+.04f)*(1-Ease.smooth(t,LAND-.1f,LAND+.15f));
        if(fly>0){
            Vec3d now=flight(b,Math.min(t,LAND)).feet().add(0,.9,0),then=flight(b,Math.max(FINAL+FINAL_STOP,Math.min(t,LAND)-.22f)).feet().add(0,.9,0);
            if(now.squaredDistanceTo(then)>1e-4){
                m.softTaper(then,now,.04,.6,AMBER,.4f*fly);m.softTaper(then,now,.02,.3,GOLD,.55f*fly);m.softDisc(now,.6,GOLD,.3f*fly);
            }
        }
        // where they come down: rings run out over the ground
        float u=Ease.unit(t,LAND,LAND+.5f);
        if(t>=LAND&&u<1){
            Vec3d o=new Vec3d(b.land.x,b.land.y+.06,b.land.z).subtract(b.f.multiply(SLIDE));
            FxLib.ring(m,o,.4+3.4*Ease.out(t,LAND,LAND+.45f),.36,GOLD,.6f*(1-u));
            FxLib.ring(m,o,.3+2.2*Ease.out(t,LAND+.04f,LAND+.5f),.24,PALE,.45f*(1-u));
        }
    }
    /** Dust: where the one struck comes down, and under them as the "WRYYY" begins (the effect layer's anime dust). */
    static void dust(Matrix4f matrix,Vec3d camera){
        if(FRAME.isEmpty()||!FxLayer.available()||!KagenoFire.ready())return;
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        for(var s:FRAME){
            var b=s.b;float t=s.t;if(!b.scene)continue;
            if(t>LAND-.1f)FxLib.dustRing(new Vec3d(b.land.x,b.land.y,b.land.z).subtract(b.f.multiply(SLIDE)),t,LAND,.35,2.8,14,1.0,dust,puffs,b.seed+71);
            if(t>WRY-.1f&&t<WRY+1.6f){Vec3d h=b.hold.add(b.f.multiply(b.drift-.8));FxLib.dustRing(h,t,WRY,.4,3.0,14,.9,dust,puffs,b.seed+81);}
        }
        if(!dust.isEmpty())KagenoFire.dust(matrix,camera,dust,puffs,(float)(world.getTime()+MinecraftClient.getInstance().getTickDelta()));
    }
    /** Each blow lights the world round it; the one struck glows as they fly. */
    static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            var b=s.b;float t=s.t;
            if(t<CUT+.6f&&b.landed()&&b.met>=0){Vec3d p=punchAt(s);if(p!=null)f.light(p,b.broke?0xb8c8ff:0xffe6a8,11,3.2f*FxLib.kick(t,CUT,.16f));}
            if(!b.scene)continue;
            int n=fists(t);
            if(n>0&&t<WIND+.3f)f.light(chest(b,victim(b,t,false)),0xffe0a0,8,1.7f*FxLib.kick(t,fistAt(n-1),.08f)+.4f*barrage(t));
            if(t>=FINAL-.01f&&t<FINAL+.7f)f.light(finalAt(b),0xffe6a8,16,4.8f*FxLib.kick(t,FINAL,.22f));
            float fly=Ease.smooth(t,FINAL+FINAL_STOP-.02f,FINAL+FINAL_STOP+.04f)*(1-Ease.smooth(t,LAND,LAND+.3f));
            if(fly>0)f.light(flight(b,Math.min(t,LAND)).feet().add(0,.9,0),0xffc860,7,1.6f*fly);
            if(t>=LAND-.01f&&t<LAND+1.1f)f.light(b.land.add(0,1,0),0xffe0a0,6,1.3f*FxLib.kick(t,LAND,.16f));
        }
    }
    /** Chunks of the floor thrown up where the one struck comes down. */
    public static void solids(FxSolids solids){
        for(var s:FRAME){
            var b=s.b;float t=s.t;if(!b.scene)continue;
            if(t>=LAND&&t<LAND+1.0f)FxLib.chunks(solids,new Vec3d(b.land.x,b.land.y,b.land.z).subtract(b.f.multiply(SLIDE)),t-LAND,16,b.seed+977,1.6,5.5,.15,.45f);
        }
    }
}
