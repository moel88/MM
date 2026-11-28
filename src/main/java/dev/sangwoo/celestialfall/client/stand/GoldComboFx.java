package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.AMBER;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GOLD;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GUARD_BLUE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GUARD_LIGHT;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.INK;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.PALE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.WHITE;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.UP;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.burst;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.on;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.right;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.up;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.way;

/**
 * The light of Gold Experience's punches (2.154.0, its basic attack), drawn with its rush's on the effect layer
 * (StandRushFx calls it pass by pass) and in its way, in gold: Star Platinum's punches' (StandPunchFx) in this stand's
 * colours. While a punch is loaded light gathers at the drawn-back fist (the fourth's all through its long load, far
 * more of it); as it strikes a smear of light follows the fist out along the way it goes (the hook's curls round the
 * front); as it lands a spiky burst inked dark round its edge flashes at the fist, a shock ring opens round the
 * punch's line, a cone of blasted air and a ring of pressure shoot on ahead, and sparks fly; whom it struck burst with
 * light and a spray of it out of their backs, and a guard that took it turns the burst blue. The fourth lands bigger:
 * a dome of rings and four more racing on, a lance of light through its target, the ground rippling out and a little
 * dust where they stood, and its target trailing light as it flies. Nothing over the whole screen. Every effect is a
 * function of its punch's clock; whatever happens after the contact stays where the blow landed.
 */
final class GoldComboFx {
    /** How big each punch's light is (the fourth's most), and how long its burst lasts after its contact (seconds). */
    static final float[] SIZE={0,.95f,1f,1.12f,1.9f},BURST={0,.17f,.17f,.2f,.3f};
    /** A punch's effects are over this long after its contact (seconds): the fourth's target flies a while. */
    static final float AFTER=1.2f;
    /** Where the blow landed, held from the first frame at or past it: the fist, the way the punch went, its right, its user's feet. */
    record Blow(Vec3d fist,Vec3d way,Vec3d right,Vec3d feet){}
    private static final Map<Long,Blow> BLOWS=new LinkedHashMap<>();
    /** Where each one the fourth threw was as it landed and where they are now, by punch and target. */
    record Shown(UUID user,GoldCombo.Punch punch,float t,StandPresence.Body body,Blow blow){
        int combo(){return punch.combo;}
        float hit(){return GoldCombo.HIT[combo()];}
        boolean big(){return combo()==4;}
        long key(){return punch.start*31+user.getLeastSignificantBits();}
        int seed(){return (int)(key()&0x7fff);}
        Vec3d fistAt(float when){return on(GoldCombo.fist(punch,when),body);}
        boolean guarded(){return punch.struck.length==0&&punch.guarded.length>0;}
    }
    private static final List<Shown> FRAME=new ArrayList<>();
    private static float delta;
    private GoldComboFx(){}

    /** Gathers this frame's punches (within 96 blocks of the camera); returns whether any show. */
    static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();delta=tickDelta;
        if(mc.world==null)return false;
        for(var e:GoldCombo.all().entrySet()){
            var user=mc.world.getPlayerByUuid(e.getKey());if(user==null||user.getPos().squaredDistanceTo(camera)>96*96)continue;
            var body=GoldPresence.body(e.getKey());
            for(var p:e.getValue()){
                float t=p.age(tickDelta),hit=GoldCombo.HIT[p.combo];
                float from=p.combo==4?.08f:GoldCombo.strikeFrom(p.combo)-.08f;
                if(t<from||t>hit+AFTER)continue;
                // stopped before it landed: nothing lands
                if(!Float.isNaN(p.stopped)&&p.stopped<hit)continue;
                var shown=new Shown(e.getKey(),p,t,body,null);
                Blow blow=BLOWS.get(shown.key());
                if(blow==null&&t>=hit&&body!=null){
                    Vec3d w=way(body.yaw());
                    blow=new Blow(shown.fistAt(hit),w,right(w),user.getLerpedPos(tickDelta));
                    BLOWS.put(shown.key(),blow);while(BLOWS.size()>48)BLOWS.remove(BLOWS.keySet().iterator().next());
                }
                if(blow==null&&body==null)continue;
                FRAME.add(new Shown(e.getKey(),p,t,body,blow));
            }
        }
        return !FRAME.isEmpty();
    }
    static float burstU(Shown s){return Ease.unit(s.t,s.hit(),s.hit()+BURST[s.combo()]);}
    static float burstSize(Shown s){return (float)((.62+.38*Ease.out(s.t,s.hit(),s.hit()+.07f))*SIZE[s.combo()]*(s.big()?1.2:1)*(1-.15*burstU(s)));}
    static float burstAlpha(Shown s){return Ease.smooth(s.t,s.hit()-.006f,s.hit()+.012f)*(float)Math.pow(1-burstU(s),.7);}
    /** Where whom it struck were as it landed (their chests), held. */
    static Vec3d[] struck(Shown s){return s.punch.struck.length>0?s.punch.struckAt:new Vec3d[0];}

    // ───────────────────────────── the passes ─────────────────────────────

    /** Dark ink under the light: every burst gets an ink edge (read against a bright sky), and the fourth's ring and lance too. */
    static void alpha(EffectMesh m){
        for(var s:FRAME){
            float t=s.t,hit=s.hit();
            if(t<hit-.01f||s.blow==null)continue;
            float a=burstAlpha(s),r=burstSize(s);
            if(a>0){
                burst(m,s.blow.fist,r*1.14,.36,9+s.combo(),s.seed(),INK,INK,.7f*a,.7f*a);
                for(var c:struck(s))burst(m,c,r*.72*1.14,.36,9,s.seed()+17,INK,INK,.55f*a,.55f*a);
            }
            if(!s.big())continue;
            float u=Ease.unit(t,hit,hit+.34f);
            if(u<1)FxLib.ringPlane(m,s.blow.fist.add(s.blow.way.multiply(.25)),s.blow.right,UP,.45+3.0*Ease.out(t,hit,hit+.26f),.36,INK,.5f*(1-u)*Ease.smooth(t,hit-.01f,hit+.01f));
            float lance=lance(t,hit);
            if(lance>0)m.needle(s.blow.fist.subtract(s.blow.way.multiply(.5)),s.blow.fist.add(s.blow.way.multiply(LANCE*Ease.out(t,hit-.02f,hit+.05f))),.66,.25,INK,.6f*lance);
        }
    }
    static void additive(EffectMesh m){
        for(var s:FRAME){
            float t=s.t,hit=s.hit(),size=SIZE[s.combo()];
            charge(m,s,t,hit,size);smear(m,s,t,hit,size);
            if(t<hit-.01f||s.blow==null)continue;
            Vec3d p=s.blow.fist,go=s.blow.way,side=s.blow.right,up=up(go,side);
            float a=Ease.smooth(t,hit-.01f,hit+.01f),u=Ease.unit(t,hit,hit+.22f);
            boolean guard=s.guarded();
            int heart=guard?GUARD_LIGHT:PALE,edge=guard?GUARD_BLUE:GOLD;
            // the impact burst: gold spikes round a pale heart (the ink under it gives the edge), a small white core
            float ba=burstAlpha(s),br=burstSize(s);
            if(ba>0){burst(m,p,br,.36,9+s.combo(),s.seed(),heart,edge,.88f*ba,.75f*ba);m.star(p,br*.6,WHITE,.4f*ba);}
            if(u<1){
                m.softDisc(p,(1.0+.6*u)*size,guard?GUARD_BLUE:AMBER,.34f*a*(1-u));m.softDisc(p,(.5+.25*u)*size,edge,.55f*a*(1-u));
                m.softDisc(p,.16*size*(1-u)+.04,WHITE,.7f*a*(1-u));
            }
            // a shock ring opening round the punch's line, then a wider one after it
            float ur=Ease.unit(t,hit,hit+.17f);
            if(ur<1)FxLib.ringPlane(m,p.add(go.multiply(.15)),side,up,.2+1.25*size*Ease.out(t,hit,hit+.16f),.2*size,edge,.85f*a*(1-ur));
            float ur2=Ease.unit(t,hit+.03f,hit+.32f);
            if(t>=hit+.03f&&ur2<1)FxLib.ringPlane(m,p.add(go.multiply(.35)),side,up,.3+2.0*size*Ease.out(t,hit+.03f,hit+.3f),.11*size,guard?GUARD_LIGHT:PALE,.55f*(1-ur2)*Ease.smooth(t,hit+.03f,hit+.05f));
            if(!guard)blast(m,s,p,go,side,up,t,hit,size);
            sparks(m,s,p,go,side,up,t,hit,size,guard);
            // whom it struck: a burst of light on them and a spray of it out of their backs
            for(var c:struck(s)){
                float uc=Ease.unit(t,hit,hit+.28f);if(uc>=1)break;
                if(ba>0)burst(m,c,br*.72,.36,9,s.seed()+17,PALE,GOLD,.78f*ba,.65f*ba);
                m.softDisc(c,(1.1+.6*uc)*size,AMBER,.3f*a*(1-uc));m.softDisc(c,.3*size*(1-uc)+.06,WHITE,.5f*a*(1-uc));
                FxLib.ringPlane(m,c,side,up,.35+1.5*size*Ease.out(t,hit,hit+.22f),.2,PALE,.55f*a*(1-uc));
                float sp=a*(1-Ease.smooth(t,hit+.06f,hit+.3f));
                if(sp>0)for(int k=0;k<10+s.combo()*3;k++){
                    double ang=Ease.hash(k*5+s.seed()+3)*Math.PI*2,spread=.15+.4*Ease.hash(k*7+s.seed());
                    Vec3d d=go.add(side.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
                    double len=(.7+1.3*Ease.hash(k*11+s.seed()))*size*Ease.out(t,hit,hit+.16f);
                    Vec3d q=c.add(d.multiply(.25+len*.45));
                    m.needle(q,q.add(d.multiply(len*.55)),.055*size,.3,k%5==0?WHITE:k%3==1?PALE:k%2==0?GOLD:AMBER,sp);
                }
            }
            if(s.big())finisher(m,s,t,hit,guard);
        }
    }
    /** While the punch is loaded: light gathering into the drawn-back fist (the fourth's all through its load, and far more of it). */
    static void charge(EffectMesh m,Shown s,float t,float hit,float size){
        if(s.body==null||t>=hit+.01f)return;
        Vec3d p=s.fistAt(t);
        if(s.big()){
            float a=GoldCombo.charge(s.punch,t);if(a<=0)return;
            FxLib.converge(m,p,2.0,24,t,.08f,hit-.03f,PALE,s.seed()+5);
            float pulse=.5f+.5f*(float)Math.sin(t*40);
            m.softDisc(p,(.42+.15*pulse)*a+.08,AMBER,.45f*a);m.softDisc(p,(.2+.07*pulse)*a,GOLD,.6f*a);m.star(p,.12+.1*a,WHITE,.55f*a);
            // crackling round the fist, turning
            for(int k=0;k<7;k++){
                double az=Ease.hash(k*13+s.seed())*Math.PI*2+t*(6+3*Ease.hash(k*17)),el=(Ease.hash(k*19+s.seed())-.5)*2.2,r=.3+.22*Ease.hash(k*23);
                Vec3d d=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
                m.needle(p.add(d.multiply(r*.4)),p.add(d.multiply(r)),.045,.5,k%2==0?WHITE:PALE,.75f*a);
            }
            return;
        }
        float from=GoldCombo.strikeFrom(s.combo());
        float a=Ease.smooth(t,from-.08f,from)*(1-Ease.smooth(t,hit-.03f,hit+.01f));if(a<=0)return;
        FxLib.converge(m,p,.8*size,8,t,from-.1f,hit-.03f,PALE,s.seed()+5);
        m.softDisc(p,.18*size*a+.04,AMBER,.5f*a);m.softDisc(p,.08*size*a+.02,WHITE,.7f*a);
    }
    /**
     * As the punch strikes, a smear of light follows the fist out from where it was loaded, along the very way it went
     * (narrow at the start, wide at the fist, a white heart; the hook's curls round the front), with streaks of air past the arm.
     */
    static void smear(EffectMesh m,Shown s,float t,float hit,float size){
        if(s.body==null)return;
        float from=GoldCombo.strikeFrom(s.combo());
        float a=Ease.smooth(t,from,from+.03f)*(1-Ease.smooth(t,hit+.02f,hit+.12f));if(a<=0)return;
        // the fist's own path from the start of the strike to where it is now (to the blow once it has landed)
        float to=Math.min(t,hit);int n=10;Vec3d prev=null,end=null,dir=null;
        for(int i=0;i<=n;i++){
            float when=from+(to-from)*i/n;Vec3d q=s.fistAt(when);
            if(prev!=null&&q.squaredDistanceTo(prev)>1e-6){
                float f=i/(float)n;
                m.softTaper(prev,q,(.03+.2*f*f)*size,(.03+.22*f)*size,AMBER,.42f*a*(.3f+.7f*f));
                m.needle(prev,q,(.03+.06*f)*size,.7,f>.6?WHITE:PALE,.6f*a*f);
                dir=q.subtract(prev);
            }
            prev=q;end=q;
        }
        if(end==null||dir==null||dir.lengthSquared()<1e-8)return;
        dir=dir.normalize();Vec3d r=right(way(s.body.yaw())),upv=up(dir,r);
        m.softDisc(end,.24*size,GOLD,.4f*a);
        // streaks of air rushing past the arm
        for(int k=0;k<7;k++){
            double off=(Ease.hash(k*7+s.combo())-.5)*.6*size,lift=(Ease.hash(k*11+s.combo())-.5)*.5*size,len=(.7+.8*Ease.hash(k*13+s.combo()))*size;
            Vec3d q=end.add(r.multiply(off)).add(upv.multiply(lift)).subtract(dir.multiply(.12));
            m.needle(q.subtract(dir.multiply(len)),q,.04*size,.85,k%3==0?WHITE:k%3==1?PALE:GOLD,a*.7f);
        }
    }
    /** The air blasted on ahead of the fist: a cone of streaks shot out along the punch, its glow, and a ring of pressure running on along it. */
    static void blast(EffectMesh m,Shown s,Vec3d p,Vec3d go,Vec3d side,Vec3d up,float t,float hit,float size){
        float a=Ease.smooth(t,hit-.01f,hit+.01f)*(1-Ease.smooth(t,hit+.07f,hit+.3f));if(a<=0)return;
        int combo=s.combo();double length=(1.4+.4*combo)*size*(combo==4?.9:1);
        float grow=Ease.out(t,hit,hit+.12f);
        for(int k=0;k<14+combo*4;k++){
            double ang=Ease.hash(k*5+s.seed()+31)*Math.PI*2,spread=.06+.28*Ease.hash(k*7+s.seed()+37);
            Vec3d d=go.add(side.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
            double reach=length*(.45+.55*Ease.hash(k*11+s.seed()+41))*grow;
            m.needle(p.add(d.multiply(.1+reach*.4)),p.add(d.multiply(.1+reach)),.06*size,.4,k%5==0?WHITE:k%2==0?GOLD:PALE,.7f*a);
        }
        m.softTaper(p,p.add(go.multiply(length*grow)),.22*size,.9*size,AMBER,.24f*a);
        float ur=Ease.unit(t,hit,hit+.2f);
        if(ur<1)FxLib.ringPlane(m,p.add(go.multiply(.2+length*.85*Ease.out(t,hit,hit+.2f))),side,up,(.3+.7*ur)*size,.13*size,GOLD,.7f*Ease.smooth(t,hit-.005f,hit+.015f)*(1-ur));
    }
    /** Sparks thrown out ahead of the blow, falling as they slow (blue off a guard, thrown aside). */
    static void sparks(EffectMesh m,Shown s,Vec3d p,Vec3d go,Vec3d side,Vec3d up,float t,float hit,float size,boolean guard){
        int combo=s.combo();float age=t-hit;if(age<0||age>=.45f)return;
        for(int k=0;k<18+combo*6;k++){
            float life=.18f+.22f*(float)Ease.hash(k*3+s.seed()+51),u=age/life;if(u>=1)continue;
            double az=(Ease.hash(k*5+s.seed()+53)-.5)*(guard?4.2:2.6),el=(Ease.hash(k*7+s.seed()+57)-.35)*1.6,speed=(3+5*Ease.hash(k*11+s.seed()+59))*size;
            Vec3d d=go.multiply(Math.cos(el)*Math.cos(az)*(guard?-.3:1)).add(side.multiply(Math.cos(el)*Math.sin(az))).add(up.multiply(Math.sin(el))).normalize();
            Vec3d q=p.add(d.multiply(speed*life*(1-Math.pow(1-u,2))*.5)).add(0,-2.2*age*age,0);
            float al=(1-u)*Ease.smooth(age,0,.015f);int col=guard?(k%2==0?GUARD_LIGHT:GUARD_BLUE):k%4==0?WHITE:k%3==1?PALE:k%2==0?GOLD:AMBER;
            m.needle(q.subtract(d.multiply(.15*(1-u)*size+.03)),q,.045*size,.3,col,al);
            if(k%3==0)m.softDisc(q,.045*size*(1-.5*u),col,.75f*al);
        }
    }
    /** How far on past the fist the fourth's lance reaches (blocks). */
    static final double LANCE=5.5;
    /** The fourth's lance: in as the fist lands, gone a sixth of a second later. */
    static float lance(float t,float hit){return Ease.smooth(t,hit-.02f,hit+.005f)*(1-Ease.smooth(t,hit+.06f,hit+.2f));}
    /**
     * The fourth: a dome of rings round the blow, four more racing on along it, a lance of light through the target, the
     * ground rippling out under it, and whom it threw trailing light as they fly.
     */
    static void finisher(EffectMesh m,Shown s,float t,float hit,boolean guard){
        Vec3d p=s.blow.fist,go=s.blow.way,side=s.blow.right,up=up(go,side);
        float in=Ease.smooth(t,hit-.01f,hit+.012f);
        float ud=Ease.unit(t,hit,hit+.4f);
        if(ud<1){
            Vec3d c=p.add(go.multiply(.6));double r=.45+3.0*Ease.out(t,hit,hit+.36f);float al=.72f*in*(1-ud);
            FxLib.ringPlane(m,c,side,up,r,.28,PALE,al);FxLib.ringPlane(m,c,go,side,r*.92,.22,GOLD,.85f*al);FxLib.ringPlane(m,c,go,up,r*.88,.2,AMBER,.75f*al);
        }
        for(int k=0;k<4;k++){
            float at=hit+.035f*k,u=Ease.unit(t,at,at+.34f);if(t<at||u>=1)continue;
            Vec3d o=p.add(go.multiply(.4+1.2*k+1.8*Ease.out(t,at,at+.34f)));
            FxLib.ringPlane(m,o,side,up,.4+(1.25+.55*k)*Ease.out(t,at,at+.28f),.22+.04*k,k==0?PALE:k%2==1?GOLD:AMBER,.85f*(1-u)*Ease.smooth(t,at,at+.015f));
        }
        float s1=in*(1-Ease.smooth(t,hit+.12f,hit+.45f));
        if(s1>0)for(int k=0;k<48;k++){
            double az=(Ease.hash(k*3+811)-.5)*1.8,el=(Ease.hash(k*5+812)-.4)*1.3,go2=(1.3+3.8*Ease.hash(k*7+813))*Ease.out(t,hit,hit+.32f);
            Vec3d d=go.multiply(Math.cos(el)*Math.cos(az)).add(side.multiply(Math.cos(el)*Math.sin(az))).add(up.multiply(Math.sin(el)));
            Vec3d q=p.add(d.multiply(.3+go2)).add(0,-.9*Math.pow(Math.max(0,t-hit),2),0);
            m.needle(q.subtract(d.multiply(.28+.3*Ease.hash(k*11+814))),q,.07,.3,k%5==0?WHITE:k%3==1?PALE:k%2==0?GOLD:AMBER,s1);
        }
        if(guard)return;
        float lance=lance(t,hit);
        if(lance>0){Vec3d a0=p.subtract(go.multiply(.5)),a1=p.add(go.multiply(LANCE*Ease.out(t,hit-.02f,hit+.05f)));
            m.softLine(a0,a1,1.6,AMBER,.32f*lance);m.needle(a0,a1,.48,.25,GOLD,lance);m.needle(a0,a1.subtract(go.multiply(.5)),.17,.25,WHITE,.8f*lance);}
        // the ground ripples out from under the blow
        Vec3d floor=new Vec3d(p.x,s.blow.feet.y+.06,p.z);
        float ug=Ease.unit(t,hit,hit+.55f);
        if(ug<1){FxLib.ring(m,floor,.5+5*Ease.out(t,hit,hit+.5f),.5,PALE,.5f*in*(1-ug));
            float ug2=Ease.unit(t,hit+.07f,hit+.6f);if(t>=hit+.07f&&ug2<1)FxLib.ring(m,floor,.4+3.6*Ease.out(t,hit+.07f,hit+.55f),.36,GOLD,.42f*(1-ug2));}
        // whom it threw trail light as they fly
        float fly=t<hit?0:Ease.smooth(t,hit,hit+.04f)*(1-Ease.smooth(t,hit+.55f,hit+.9f));
        if(fly>0){
            var world=MinecraftClient.getInstance().world;
            for(int i=0;i<s.punch.struck.length;i++){
                if(world==null||!(world.getEntityById(s.punch.struck[i]) instanceof LivingEntity e))continue;
                Vec3d q=e.getLerpedPos(delta).add(0,e.getHeight()*.55,0),back=s.punch.struckAt[i].subtract(q);double l=back.length();if(l<.3)continue;
                Vec3d end=q.add(back.multiply(Math.min(1,4.5/l)));
                m.softTaper(q,end,1.2,.07,AMBER,.6f*fly);m.softTaper(q,end,.42,.02,WHITE,.7f*fly);m.softDisc(q,.9,GOLD,.3f*fly);
            }
        }
    }
    /** A little dust where the fourth's target stood as it was thrown (the effect layer's anime dust). */
    static void dust(Matrix4f matrix,Vec3d camera){
        if(!FxLayer.available()||!KagenoFire.ready())return;
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        for(var s:FRAME){
            if(!s.big()||s.blow==null||s.guarded())continue;
            for(int i=0;i<s.punch.struck.length;i++){
                Vec3d at=s.punch.struckAt[i];
                FxLib.dustRing(new Vec3d(at.x,s.blow.feet.y,at.z),s.t,s.hit(),.25,1.1,7,.35,dust,puffs,s.seed()+i*7);
            }
        }
        if(!dust.isEmpty())KagenoFire.dust(matrix,camera,dust,puffs,(float)(world.getTime()+delta));
    }
    /** The effect layer: the gathering fist lights the world about it, the blow lights where it lands. */
    static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            float c=GoldCombo.charge(s.punch,s.t);
            if(c>0&&s.body!=null)f.light(s.fistAt(s.t),0xffdc8a,5,1.2f*c);
            if(s.blow==null)continue;
            float a=burstAlpha(s);
            if(a>0)f.light(s.blow.fist,s.guarded()?0xb8c8ff:0xffe6a8,s.big()?9:5,(s.big()?2.2f:1.4f)*a);
        }
    }
}
