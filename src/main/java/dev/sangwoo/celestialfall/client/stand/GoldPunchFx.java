package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.AMBER;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GOLD;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GUARD_BLUE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GUARD_LIGHT;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.INK;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.PALE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.WHITE;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.burst;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.on;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.right;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.up;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.way;

/**
 * The light of Gold Experience's overdrive punch (2.148.0), drawn with its rush's on the effect layer (StandRushFx calls
 * it pass by pass) and in its way, in gold: as it winds up, light gathers into its drawn-back fist (motes drawn in,
 * rings closing on it); thrown, the fist trails a smear of light and lands in a spiky burst with rings running on
 * along it; a guard takes it in blue. Landing, the one struck bursts with light and their soul is knocked out of their
 * back: pale wisps torn out along the blow (nobody but they sees the soul itself, SoulClient). While a soul is out,
 * faint pale motes rise off its body's head (everyone sees those), and on the struck player's own screen a thread of
 * pale light runs from the soul back to the body. None of it over the whole screen. Every effect is a function of the
 * punch's clock.
 */
final class GoldPunchFx {
    /** A soul's light: pale blue, deeper blue. */
    static final int SOUL=0xd6ecff,SOUL_DEEP=0x86b8ff;
    /** Seconds the fist's burst lasts once it lands, and the light on whom it struck and the wisps torn out of them. */
    static final float BURST=.24f,STRUCK=.55f;
    record Shown(GoldPunch.Punch punch,float t,StandPresence.Body body){
        Vec3d fist(){return on(GoldPunch.fist(punch,t),body);}
        Vec3d fistAt(float when){return on(GoldPunch.fist(punch,when),body);}
    }
    private static final List<Shown> FRAME=new ArrayList<>();
    private static final List<Vec3d[]> HEADS=new ArrayList<>();
    private static float delta;private static double clock;
    private GoldPunchFx(){}

    /** Gathers this frame's punches and soulless bodies (within 96 blocks of the camera); returns whether anything shows. */
    static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();HEADS.clear();delta=tickDelta;
        if(mc.world==null)return false;
        clock=mc.world.getTime()+tickDelta;
        for(var e:GoldPunch.all().entrySet()){
            var p=e.getValue();var user=mc.world.getPlayerByUuid(e.getKey());
            if(user==null||user.getPos().squaredDistanceTo(camera)>96*96)continue;
            float t=p.age(tickDelta);
            if(t<0||t>GoldPunch.LENGTH+1f)continue;
            FRAME.add(new Shown(p,t,GoldPresence.body(e.getKey())));
        }
        for(var e:mc.world.getEntitiesByClass(LivingEntity.class,new Box(camera,camera).expand(64),SoulClient::soulless)){
            if(e==mc.getCameraEntity()&&!mc.gameRenderer.getCamera().isThirdPerson()&&!SoulClient.out())continue;
            HEADS.add(new Vec3d[]{e.getLerpedPos(tickDelta).add(0,e.getHeight(),0),new Vec3d(e.getId()*31,0,0)});
        }
        return !FRAME.isEmpty()||!HEADS.isEmpty()||SoulClient.out();
    }
    /** How strongly the fist's burst shows {@code t} seconds in (0 before it lands). */
    static float burstAlpha(Shown s){
        if(!s.punch.landed()&&s.t<GoldPunch.CONTACT||!Float.isNaN(s.punch.stopped))return 0;
        float u=(s.t-GoldPunch.CONTACT)/BURST;
        return u<0||u>=1?0:Ease.smooth(s.t,GoldPunch.CONTACT-.006f,GoldPunch.CONTACT+.012f)*(float)Math.pow(1-u,.7);
    }
    /** Dark ink under the light: the fist's burst and the burst on whom it struck get an ink edge. */
    static void alpha(EffectMesh m){
        for(var s:FRAME){
            if(s.body==null)continue;
            float a=burstAlpha(s);
            if(a>0){float u=(s.t-GoldPunch.CONTACT)/BURST;burst(m,s.fistAt(GoldPunch.CONTACT),.62*(.75+.25*Ease.out(u,0,.4f))*1.14,.36,10,s.punch.seed(),INK,INK,.62f*a,.62f*a);}
            if(s.punch.hit()&&s.punch.struckAt!=null){
                float u=(s.t-s.punch.landedAt)/STRUCK;
                if(u>=0&&u<1){float b=(float)Math.pow(1-u,.8);burst(m,s.punch.struckAt,.8*(.8+.2*Ease.out(u,0,.3f))*1.14,.36,11,s.punch.seed()+5,INK,INK,.6f*b,.6f*b);}
            }
        }
    }
    static void additive(EffectMesh m){
        for(var s:FRAME){
            if(s.body==null)continue;
            Vec3d w=way(s.punch.yaw()),r=right(w),up=up(w,r);
            gather(m,s,w,r,up);
            thrown(m,s,w,r,up);
            if(s.punch.landed()&&s.punch.struckAt!=null)struck(m,s,w,r,up);
        }
        for(var h:HEADS)soulless(m,h[0],(int)h[1].x);
        thread(m);
    }
    /** As it winds up: light gathers into the drawn-back fist, motes drawn into it, rings closing on it faster and faster. */
    static void gather(EffectMesh m,Shown s,Vec3d w,Vec3d r,Vec3d up){
        float c=GoldPunch.charge(s.punch,s.t);if(c<=0)return;
        Vec3d q=s.fist();float pulse=.5f+.5f*(float)Math.sin(s.t*Math.PI*2*(3+5*c));
        m.softDisc(q,.34+.1*pulse,AMBER,.34f*c);m.softDisc(q,.16+.04*pulse,GOLD,.55f*c);m.star(q,.12+.1*c,WHITE,.5f*c);
        for(int k=0;k<12;k++){
            double u=(s.t*(2.2+1.4*Ease.hash(k*7+s.punch.seed()))+Ease.hash(k*13))%1;
            double a=Ease.hash(k*5+1)*Math.PI*2,b=(Ease.hash(k*11+2)-.5)*2.2;
            Vec3d d=r.multiply(Math.cos(a)*Math.cos(b)).add(up.multiply(Math.sin(b))).add(w.multiply(Math.sin(a)*Math.cos(b)));
            Vec3d at=q.add(d.multiply(.15+.95*(1-u)));
            m.needle(at,at.subtract(d.multiply(.2+.1*u)),.035,.45,k%3==0?WHITE:k%3==1?PALE:GOLD,(float)(c*u*.85));
        }
        for(int k=0;k<2;k++){
            double u=(s.t*(1.6+2.2*c)+k*.5)%1;
            FxLib.ringPlane(m,q,r,up,.12+.75*(1-u),.035,k==0?GOLD:PALE,(float)(c*u*.6));
        }
    }
    /** Thrown: the fist trails a smear of light from where it was wound up, and lands in a burst with rings and streaks of air running on ahead. */
    static void thrown(EffectMesh m,Shown s,Vec3d w,Vec3d r,Vec3d up){
        float t=s.t;
        if(Float.isNaN(s.punch.stopped)&&t>GoldPunch.LOAD&&t<GoldPunch.CONTACT+.1f){
            float a=Ease.smooth(t,GoldPunch.LOAD+.03f,GoldPunch.LOAD+.09f)*(1-Ease.smooth(t,GoldPunch.CONTACT,GoldPunch.CONTACT+.1f));
            Vec3d from=s.fistAt(GoldPunch.LOAD),at=s.fistAt(Math.min(t,GoldPunch.CONTACT)),along=at.subtract(from);
            if(a>0&&along.lengthSquared()>1e-4){
                Vec3d tail=from.add(along.multiply(.1));
                m.softTaper(tail,at,.03,.52,AMBER,.5f*a);m.softTaper(tail,at,.015,.26,GOLD,.66f*a);
                m.needle(tail,at,.09,.85,WHITE,.45f*a);m.softDisc(at,.3,GOLD,.45f*a);
            }
        }
        float a=burstAlpha(s);if(a<=0)return;
        float u=(t-GoldPunch.CONTACT)/BURST;boolean guard=s.punch.guarded();
        int core=guard?GUARD_LIGHT:PALE,rim=guard?GUARD_BLUE:GOLD;
        Vec3d p=s.fistAt(GoldPunch.CONTACT);double rad=.62*(.75+.25*Ease.out(u,0,.4f));
        burst(m,p,rad,.36,10,s.punch.seed(),core,rim,.85f*a,.75f*a);
        m.softDisc(p,rad*1.1,guard?GUARD_BLUE:AMBER,.3f*a);m.softDisc(p,rad*.32+.03,WHITE,.55f*a*(1-u));
        FxLib.ringPlane(m,p.add(w.multiply(.1)),r,up,.15+1.0*Ease.out(u,0,1),.08,rim,.75f*a*(1-u));
        FxLib.ringPlane(m,p.add(w.multiply(.5*Ease.out(u,0,1))),r,up,.1+.6*Ease.out(u,0,1),.05,core,.6f*a*(1-u));
        if(guard){
            // thrown aside off the guard
            for(int k=0;k<8;k++){
                double ang=(Ease.hash(s.punch.seed()+k*5)-.5)*2.6;
                Vec3d d=r.multiply(Math.sin(ang)*(k%2==0?1:-1)).add(up.multiply(Math.cos(ang)*.6)).subtract(w.multiply(.3)).normalize();
                Vec3d q=p.add(d.multiply(.15+.8*Ease.out(u,0,1)));
                m.needle(q,q.add(d.multiply(.26)),.045,.4,k%2==0?WHITE:GUARD_LIGHT,.8f*a*(1-u));
            }
            return;
        }
        for(int k=0;k<9;k++){
            double ang=Ease.hash(s.punch.seed()+k*5+3)*Math.PI*2,spread=.08+.3*Ease.hash(s.punch.seed()+k*7);
            Vec3d d=w.add(r.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
            Vec3d q=p.add(d.multiply(.15+1.1*Ease.out(u,0,1)));
            m.needle(q,q.add(d.multiply(.5)),.05,.5,k%3==0?WHITE:k%3==1?PALE:GOLD,.7f*a*(1-u));
        }
    }
    /** The one it landed on: a burst of gold where it struck, and their soul knocked out of their back, pale wisps torn out along the blow and a pale ring opening behind them. */
    static void struck(EffectMesh m,Shown s,Vec3d w,Vec3d r,Vec3d up){
        var p=s.punch;if(!p.hit())return;
        float u=(s.t-p.landedAt)/STRUCK;if(u<0||u>=1)return;
        float a=(float)Math.pow(1-u,.8)*Ease.smooth(s.t,p.landedAt-.006f,p.landedAt+.012f);
        Vec3d at=p.struckAt;double size=.8*(.8+.2*Ease.out(u,0,.3f));
        burst(m,at,size,.36,11,p.seed()+5,PALE,GOLD,.85f*a,.75f*a);
        m.star(at,size*.7,WHITE,.4f*a*(1-u));m.softDisc(at,size*1.5,AMBER,.26f*a);m.softDisc(at,size*.4,WHITE,.5f*a*(1-u));
        FxLib.ringPlane(m,at.add(w.multiply(.1)),r,up,.2+1.0*Ease.out(u,0,1),.07,GOLD,.6f*a*(1-u));
        // the soul torn out of their back
        Vec3d back=at.add(w.multiply(.35));float out=Ease.out(u,0,.7f),fade=1-Ease.smooth(u,.25f,1);
        m.softDisc(back.add(w.multiply(.5*out)),.5+.5*out,SOUL_DEEP,.22f*fade);
        FxLib.ringPlane(m,back.add(w.multiply(.2+.9*out)),r,up,.25+.7*out,.06,SOUL,.55f*fade);
        for(int k=0;k<10;k++){
            double ang=Ease.hash(p.seed()+k*5+9)*Math.PI*2,spread=.05+.28*Ease.hash(p.seed()+k*7+1);
            Vec3d d=w.add(r.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread+.12)).normalize();
            double len=(1.0+1.4*Ease.hash(p.seed()+k*11))*out;
            Vec3d q=back.add(d.multiply(len*.35));
            m.softTaper(q,q.add(d.multiply(len*.65+.05)),.09,.01,k%3==0?SOUL_DEEP:SOUL,.5f*fade);
            if(k%2==0)m.needle(q,q.add(d.multiply(len*.6+.05)),.04,.3,WHITE,.4f*fade);
        }
    }
    /** A soulless body: faint pale motes rising off its head, twinkling out. */
    static void soulless(EffectMesh m,Vec3d head,int seed){
        for(int k=0;k<6;k++){
            double u=(clock/20*(.55+.3*Ease.hash(seed+k*7))+Ease.hash(seed+k*13))%1;
            double a=Ease.hash(seed+k*5+(int)Math.floor(clock/20*(.55+.3*Ease.hash(seed+k*7))+Ease.hash(seed+k*13)))*Math.PI*2;
            Vec3d q=head.add(Math.cos(a)*(.12+.1*u),-.1+.75*u,Math.sin(a)*(.12+.1*u));
            float al=(float)(Math.sin(u*Math.PI)*.6);
            m.softDisc(q,.07,SOUL_DEEP,.3f*al);m.star(q,.05,SOUL,al);
        }
    }
    /** The struck player's own screen: a thread of pale light from their soul back to their body, beads running along it, and a faint light about the soul. */
    static void thread(EffectMesh m){
        var ends=SoulClient.ends(delta);if(ends==null)return;
        float show=SoulClient.shown(delta);if(show<=0)return;
        Vec3d a=ends[0].add(0,1.1,0),b=ends[1].add(0,1.1,0);double length=a.distanceTo(b);
        m.softDisc(a,.8,SOUL_DEEP,.1f*show);
        for(int k=0;k<7;k++){
            double u=(clock/20*.35+Ease.hash(k*7))%1,ang=Ease.hash(k*5)*Math.PI*2;
            Vec3d q=ends[0].add(Math.cos(ang)*.38,.2+1.7*u,Math.sin(ang)*.38);
            m.star(q,.05,SOUL,(float)(Math.sin(u*Math.PI)*.55*show));
        }
        if(length<.6)return;
        float thin=(float)Math.min(1,(length-.6)/1.2)*show;
        m.softTaper(a,b,.035,.035,SOUL_DEEP,.3f*thin);m.softTaper(a,b,.012,.012,SOUL,.45f*thin);
        int beads=(int)Math.min(12,2+length*.5);
        for(int k=0;k<beads;k++){
            double u=(clock/20*.6+k/(double)beads)%1;
            m.star(a.lerp(b,u),.06,SOUL,(float)(Math.sin(u*Math.PI)*.6*thin));
        }
    }
    /** The effect layer: the gathering fist lights the world about it, the blow lights where it lands. */
    static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            if(s.body==null)continue;
            float c=GoldPunch.charge(s.punch,s.t);
            if(c>0)f.light(s.fist(),0xffdc8a,5,1.3f*c);
            float a=burstAlpha(s);
            if(a>0)f.light(s.fistAt(GoldPunch.CONTACT),s.punch.guarded()?0xb8c8ff:0xffe6a8,8,2.2f*a);
        }
    }
}
