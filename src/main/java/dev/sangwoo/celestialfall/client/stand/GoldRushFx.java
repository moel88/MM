package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.burst;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.on;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.right;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.up;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.way;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.GUARD;

/**
 * The light of Gold Experience's muda rush (2.147.0), drawn with Star Platinum's rush's on the effect layer
 * (StandRushFx calls it) and in its way, in gold: crisp shapes inked dark round their edges, light that adds up and
 * blooms, none of it over the whole screen. Every fist the rush throws, the body's and its flying arms', trails a smear
 * of golden light out from where it was drawn back to where it stops dead and lands in a small spiky burst; the front
 * of the barrage is a flickering haze of gold with streaks of air shooting on ahead out of it and rings of pressure
 * running out along it; whoever a blow strikes bursts with light where it lands (a little elsewhere on them each time)
 * and sprays light out of their back, and a blow on a guard throws its sparks aside. White only at the cores. Every
 * effect is a function of the rush's clock.
 */
public final class GoldRushFx {
    /** A fist's burst lasts this long after it lands, and a blow's light on whom it struck (seconds). */
    static final float FIST=.1f,BLOW=.22f;
    /** How big the body's fists' light is, and its afterimages' (a share of that). */
    static final float SIZE=.5f,PHANTOM=.6f;
    /** Where the barrage's front is, from the stand's feet (blocks ahead and up, at its size 1): where its fists stop. */
    static final float FRONT_AHEAD=1.15f,FRONT_UP=1.5f;
    /** Gold: the dark ink under it, deep amber, gold, pale gold, and white for the cores; a guard's blue. */
    static final int INK=0x3a1c02,AMBER=0xff9a1e,GOLD=0xffc83a,PALE=0xfff0b0,WHITE=0xffffff,GUARD_BLUE=0x6d84ff,GUARD_LIGHT=0xd8e0ff;
    record Shown(StandRush.Rush rush,float t,StandPresence.Body body,float yaw){
        int seed(){return rush.seed();}
        float on(){return GoldRush.on(rush,t);}
        float arms(){return GoldRush.armSize(rush,t);}
    }
    record Fist(Vec3d from,Vec3d at,Vec3d stop,float strike,int k){}
    private static final List<Shown> FRAME=new ArrayList<>();
    private static float delta;
    private GoldRushFx(){}

    /** Gathers this frame's muda rushes (within 96 blocks of the camera); returns whether any show. */
    static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();delta=tickDelta;
        if(mc.world==null)return false;
        for(var e:StandRush.all().entrySet()){
            var r=e.getValue();if(!r.gold())continue;
            var user=mc.world.getPlayerByUuid(e.getKey());
            if(user==null||user.getPos().squaredDistanceTo(camera)>96*96)continue;
            float t=r.age(tickDelta);
            if(t>r.ends()+1f)continue;
            FRAME.add(new Shown(r,t,GoldPresence.body(e.getKey()),r.yaw(tickDelta)));
        }
        return !FRAME.isEmpty();
    }

    static Vec3d front(StandPresence.Body b){return b.at().add(way(b.yaw()).multiply(FRONT_AHEAD*b.scale())).add(0,FRONT_UP*b.scale(),0);}
    static float[] stretched(GoldSkin skin,int k,int seed,float arms){return GoldRush.grown(skin,GoldRush.fist(k,seed),k&1,arms*(1+GoldRush.STRETCH));}
    static float[] drawn(GoldSkin skin,int k,int seed,float arms){return GoldRush.grown(skin,k==0?GUARD:GoldRush.fist(k-1,seed),k&1,arms);}
    /** The fist striking {@code u} seconds into a barrage (the body's, or an afterimage's on its own clock), null if none is. */
    static Fist fist(GoldSkin skin,StandPresence.Body b,float u,float end,int seed,float arms){
        if(u<=0||u>end)return null;
        int k=(int)Math.floor(u/GoldRush.HALF);float s=GoldRush.smooth(u/GoldRush.HALF-k);
        Vec3d from=on(drawn(skin,k,seed,arms),b),stop=on(stretched(skin,k,seed,arms),b);
        return new Fist(from,from.lerp(stop,s),stop,s,k);
    }
    /** When the fist {@code j} before the one striking {@code u} seconds in landed (seconds, -1 for none). */
    static float landed(float u,int j){
        int k=(int)Math.floor(u/GoldRush.HALF)-j;
        return k<1?-1:k*GoldRush.HALF;
    }

    /** Dark ink under the light: the body's fists' bursts and the bursts on whom it strikes get an ink edge. */
    static void alpha(EffectMesh m){
        for(var s:FRAME){
            if(s.body!=null)for(int j=0;j<3;j++){
                float lands=landed(s.t,j);if(lands<0||lands>s.rush.ends())continue;
                float u=(s.t-lands)/FIST;if(u<0||u>=1)continue;
                int k=Math.round(lands/GoldRush.HALF)-1;
                var p=on(stretched(s.rush.skin,k,s.seed(),s.arms()),s.body);float a=(float)Math.pow(1-u,.7)*s.on();
                burst(m,p,SIZE*s.arms()*.62*s.body.scale()*(.75+.25*Ease.out(u,0,.4f))*1.14,.36,8,s.seed()+k*7,INK,INK,.6f*a,.6f*a);
            }
            for(var hit:hits(s)){
                float a=hit.alpha();if(a<=0)continue;
                burst(m,hit.at(),hit.size()*1.14,.36,9,hit.seed(),INK,INK,.6f*a,.6f*a);
            }
        }
    }
    static void additive(EffectMesh m){
        for(var s:FRAME){
            float on=s.on();
            if(s.body!=null&&on>0){
                fists(m,s,s.body,s.t,s.seed(),1,on);
                for(int i=0;i<GoldRush.PHANTOMS.length;i++){
                    float a=GoldRush.phantomAlpha(s.rush,i,s.t);if(a<.02f)continue;
                    fists(m,s,GoldRush.phantomBody(s.body,i),s.t-GoldRush.PHANTOMS[i][0],GoldRush.phantomSeed(s.rush,i),PHANTOM,on*a*1.6f);
                }
                front(m,s,on);
            }
            for(var hit:hits(s))struck(m,hit);
        }
    }
    /**
     * A body's fists ({@code u} seconds into its barrage, {@code size} and {@code alpha} of the body's): the one striking
     * now trails a smear of light from where it was drawn back to the fist, and the ones that landed in the last FIST
     * seconds burst where they stopped, a ring of air round each.
     */
    static void fists(EffectMesh m,Shown s,StandPresence.Body b,float u,int seed,float size,float alpha){
        float end=s.rush.ends(),arms=s.arms(),sz=SIZE*size*arms*b.scale();
        var f=fist(s.rush.skin,b,u,end,seed,arms);
        if(f!=null){
            float a=alpha*Ease.smooth(f.strike,.05f,.3f);
            Vec3d along=f.at.subtract(f.from);
            if(a>0&&along.lengthSquared()>1e-4){
                Vec3d tail=f.from.add(along.multiply(.15));
                m.softTaper(tail,f.at,.02,.34*sz,AMBER,.5f*a);
                m.softTaper(tail,f.at,.01,.16*sz,GOLD,.62f*a);
                m.needle(tail,f.at,.06*sz,.85,WHITE,.42f*a);
                m.softDisc(f.at,.22*sz,GOLD,.42f*a);
            }
        }
        Vec3d w=way(b.yaw()),r=right(w),up=up(w,r);
        for(int j=0;j<3;j++){
            float lands=landed(u,j);if(lands<0||lands>end)continue;
            float v=(u-lands)/FIST;if(v<0||v>=1)continue;
            int k=Math.round(lands/GoldRush.HALF)-1;
            var p=on(stretched(s.rush.skin,k,seed,arms),b);float a=alpha*(float)Math.pow(1-v,.7)*Ease.smooth(u-lands,0,.012f);
            double rad=sz*.62*(.75+.25*Ease.out(v,0,.4f));
            burst(m,p,rad,.36,8,seed+k*7,PALE,GOLD,.82f*a,.72f*a);
            m.softDisc(p,rad*.9,AMBER,.3f*a);m.softDisc(p,rad*.3+.02,WHITE,.5f*a*(1-v));
            FxLib.ringPlane(m,p.add(w.multiply(.08)),r,up,.08+.55*sz*Ease.out(v,0,1),.07*sz,GOLD,.7f*a*(1-v));
            // a streak of air shot on ahead of the fist
            Vec3d q=p.add(w.multiply(.1+.9*sz*Ease.out(v,0,1)));
            m.needle(q,q.add(w.multiply(.45*sz)),.05*sz,.6,k%3==0?WHITE:k%2==0?PALE:GOLD,.7f*a*(1-v));
        }
    }
    /**
     * The front of the barrage: a flickering haze of gold, streaks of air shooting on ahead out of it (the lines of a
     * rush), and a ring of pressure running out along it every few fists.
     */
    static void front(EffectMesh m,Shown s,float on){
        var b=s.body;Vec3d w=way(b.yaw()),r=right(w),up=up(w,r),c=front(b);float sc=b.scale(),t=s.t;
        float flicker=.78f+.22f*(float)Math.sin(t*Math.PI*2/GoldRush.HALF*.5)*(float)Math.sin(t*23.7+1.3);
        // a small haze where the fists land (kept off the stand's own body: the effect layer draws over bodies)
        m.softDisc(c.add(w.multiply(.2*sc)),.42*sc,AMBER,.2f*on*flicker);
        for(int k=0;k<18;k++){
            float period=.16f+.09f*(float)Ease.hash(k*7+s.seed()),phase=(float)Ease.hash(k*11+s.seed())*period;
            float u=((t+phase)%period)/period;
            double side=(Ease.hash(k*13+s.seed())-.5)*1.5*sc,lift=(Ease.hash(k*17+s.seed())-.45)*1.1*sc,len=(.35+.5*Ease.hash(k*19+s.seed()))*sc;
            Vec3d start=c.add(r.multiply(side)).add(up.multiply(lift)).add(w.multiply((-.2+2.4*u)*sc));
            float a=on*(float)Math.sin(Math.PI*u)*.7f;
            m.needle(start,start.add(w.multiply(len)),.035*sc,.7,k%6==0?WHITE:k%3==1?PALE:k%2==0?AMBER:GOLD,a);
        }
        for(int k=0;k<2;k++){
            float period=.176f,u=((t+k*period*.5f)%period)/period;
            Vec3d o=c.add(w.multiply((.2+1.9*Ease.out(u,0,1))*sc));
            FxLib.ringPlane(m,o,r,up,(.3+.55*u)*sc,.06*sc,k==0?PALE:GOLD,.45f*on*(1-u)*Ease.smooth(u,0,.08f));
        }
    }
    /** A blow's light on whom it struck: where on them it landed, how big, how strongly it shows now, how far through it is, the way it went and whether a guard took it. */
    record Hit(Vec3d at,double size,float alpha,float u,Vec3d way,boolean guarded,int seed){}
    /** The blows of this rush whose light still shows on whom they struck. */
    static List<Hit> hits(Shown s){
        var out=new ArrayList<Hit>();var world=MinecraftClient.getInstance().world;if(world==null)return out;
        for(var b:s.rush.blowsHeard()){
            float age=s.t-b.at();if(age<0||age>=BLOW)continue;
            float u=age/BLOW,a=Ease.smooth(age,0,.015f)*(float)Math.pow(1-u,.8);
            Vec3d go=Vec3d.fromPolar(0,b.yaw()),side=right(go);
            for(int i=0;i<b.ids().length;i++){
                int seed=(int)(Math.round(b.at()*20)*131+b.ids()[i]*7);
                var e=world.getEntityById(b.ids()[i]);
                Vec3d chest=e instanceof LivingEntity l?l.getLerpedPos(delta).add(0,l.getHeight()*.62,0):b.where()[i].add(0,1.1,0);
                double w=e instanceof LivingEntity l?l.getWidth()*.5:.3;
                // on the near side of them, a little elsewhere each blow
                Vec3d at=chest.subtract(go.multiply(w*.8)).add(side.multiply((Ease.hash(seed)-.5)*.55)).add(0,(Ease.hash(seed+3)-.5)*.6,0);
                out.add(new Hit(at,.42*(.8+.4*Ease.hash(seed+5))*(.7+.3*Ease.out(u,0,.3f)),a,u,go,b.guarded()[i],seed));
            }
        }
        return out;
    }
    /** A blow's light on whom it struck: a burst, a ring, and light sprayed out of their back (thrown aside off a guard). */
    static void struck(EffectMesh m,Hit h){
        float a=h.alpha();if(a<=0)return;
        Vec3d go=h.way(),side=right(go),up=up(go,side);
        if(h.guarded()){
            burst(m,h.at(),h.size()*.8,.4,9,h.seed(),GUARD_LIGHT,GUARD_BLUE,.7f*a,.6f*a);
            FxLib.ringPlane(m,h.at(),side,up,.2+.6*Ease.out(h.u(),0,1),.06,GUARD_BLUE,.7f*a*(1-h.u()));
            for(int k=0;k<6;k++){
                double ang=(Ease.hash(h.seed()+k*5)-.5)*2.6;
                Vec3d d=side.multiply(Math.sin(ang)*(k%2==0?1:-1)).add(up.multiply(Math.cos(ang)*.6)).subtract(go.multiply(.3)).normalize();
                Vec3d q=h.at().add(d.multiply(.15+.6*Ease.out(h.u(),0,1)));
                m.needle(q,q.add(d.multiply(.22)),.04,.4,k%2==0?WHITE:GUARD_LIGHT,.8f*a*(1-h.u()));
            }
            return;
        }
        burst(m,h.at(),h.size(),.36,9,h.seed(),PALE,GOLD,.85f*a,.75f*a);
        m.star(h.at(),h.size()*.6,WHITE,.3f*a*(1-h.u()));
        m.softDisc(h.at(),h.size()*1.6,AMBER,.28f*a);m.softDisc(h.at(),h.size()*.4,WHITE,.45f*a*(1-h.u()));
        FxLib.ringPlane(m,h.at().add(go.multiply(.1)),side,up,.18+.75*Ease.out(h.u(),0,1),.07,GOLD,.6f*a*(1-h.u()));
        // light sprayed out of their back, along the blow
        float sp=a*(1-Ease.smooth(h.u(),.3f,1));
        if(sp>0)for(int k=0;k<7;k++){
            double ang=Ease.hash(h.seed()+k*5+3)*Math.PI*2,spread=.15+.4*Ease.hash(h.seed()+k*7);
            Vec3d d=go.add(side.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
            double len=(.4+.8*Ease.hash(h.seed()+k*11))*Ease.out(h.u(),0,.6f);
            Vec3d q=h.at().add(go.multiply(.5)).add(d.multiply(.15+len*.5));
            m.needle(q,q.add(d.multiply(len*.6+.05)),.045,.3,k%4==0?WHITE:k%4==1?PALE:k%2==0?AMBER:GOLD,sp);
        }
    }
    /** A little dust kicked up round the feet of whom the rush strikes, now and then (the effect layer's anime dust). */
    static void dust(Matrix4f matrix,Vec3d camera){
        if(FRAME.isEmpty()||!FxLayer.available()||!KagenoFire.ready())return;
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        for(var s:FRAME){
            int n=0;
            for(var b:s.rush.blowsHeard()){
                // every ninth blow or so (one tick in twenty of the rush's clock): a few small puffs low on the ground
                int number=Math.round(b.at()*20);if(number%20!=dev.sangwoo.celestialfall.stand.GoldExperienceRules.RUSH_FIRST%20)continue;
                for(int i=0;i<b.ids().length;i++){
                    var e=world.getEntityById(b.ids()[i]);if(!(e instanceof LivingEntity l)||!l.isOnGround())continue;
                    FxLib.dustRing(l.getLerpedPos(delta),s.t,b.at(),.2,.8,5,.3,dust,puffs,number*13+i*7+n);
                }
                n++;
            }
        }
        if(!dust.isEmpty())KagenoFire.dust(matrix,camera,dust,puffs,(float)(world.getTime()+delta));
    }
    /** The effect layer: the barrage lights the world in front of it, flickering with its fists, and each blow lights whom it strikes. */
    static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            float on=s.on();
            if(s.body!=null&&on>0){
                float flicker=.75f+.25f*(float)Math.sin(s.t*Math.PI*2/GoldRush.HALF*.5);
                f.light(front(s.body),0xffdc8a,6,1.4f*on*flicker);
            }
            for(var h:hits(s))f.light(h.at(),h.guarded()?0xb8c8ff:0xffe6a8,5,1.4f*h.alpha());
        }
    }
}
