package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.*;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.GUARD;

/**
 * The light of Star Platinum's rush (2.57.0), drawn with its punches' on the effect layer (KagenoRenderer), in their
 * colours and their way: crisp shapes inked dark round their edges, light that adds up and blooms, and none of it over
 * the whole screen (2.56.0's rule for the punches: no shake, flash or impact frame). Every fist the rush throws, the
 * body's and its flying arms', trails a smear of light out from where it was drawn back to where it stops dead and
 * lands in a small spiky burst; the front of the barrage is a flickering haze of violet light, with streaks of air
 * shooting on ahead out of it and rings of pressure running out along it; whoever a blow strikes bursts with light where
 * it lands (a little elsewhere on them each time), sprays light out of their back and lights the world round them, and
 * a blow on a guard throws its sparks aside. Every effect is a function of the rush's clock. Since 2.58.0 the fists
 * are where the rush's bigger arms put them (StandRush.ARMS: out further, stretching further still to each blow), with
 * light as much bigger, and ten afterimages throw theirs.
 */
public final class StandRushFx {
    /** A fist's burst lasts this long after it lands, and a blow's light on whom it struck (seconds). */
    static final float FIST=.1f,BLOW=.22f;
    /** How big the body's fists' light is, and its afterimages' (a share of that). */
    static final float SIZE=.55f,PHANTOM=.6f;
    /** Where the barrage's front is, from the stand's feet (blocks ahead and up, at its size 1): where its fists stop (further out since 2.58.0, its arms bigger). */
    static final float FRONT_AHEAD=1.35f,FRONT_UP=1.45f;
    /** This frame's rushes near the camera: the rush, its clock, where the stand stands (null if it is not drawn) and the way it faces. */
    record Shown(UUID user,StandRush.Rush rush,float t,StandPresence.Body body,float yaw){
        int seed(){return rush.seed();}
        /** How much the barrage is on now (0 to 1): in once the stand has come round, out as it ends. */
        float on(){return Ease.smooth(t,StandRush.RISE*.6f,StandRush.RISE)*(1-Ease.smooth(t,rush.ends(),rush.ends()+.08f));}
        /** How big its arms are now before they stretch out (StandRush.armSize): the body's and its afterimages'. */
        float arms(){return StandRush.armSize(rush,t);}
    }
    /** A fist where it is now (from where it was drawn back to where it stops), how far through its strike, and when it lands. */
    record Fist(Vec3d from,Vec3d at,Vec3d stop,float strike,float lands,int k){}
    private static final List<Shown> FRAME=new ArrayList<>();
    private static float delta;
    private StandRushFx(){}

    /** Gathers this frame's rushes (within 96 blocks of the camera); returns whether any show. */
    public static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();delta=tickDelta;
        if(mc.world==null)return false;
        for(var e:StandRush.all().entrySet()){
            var r=e.getValue();var user=mc.world.getPlayerByUuid(e.getKey());
            if(user==null||user.getPos().squaredDistanceTo(camera)>96*96)continue;
            float t=r.age(tickDelta);
            // while it is on, and a moment after (its last fists' bursts, its last blows' light and their dust)
            if(t>r.ends()+1f)continue;
            FRAME.add(new Shown(e.getKey(),r,t,StandPresence.body(e.getKey()),r.yaw(tickDelta)));
        }
        return !FRAME.isEmpty();
    }
    public static boolean showing(){return !FRAME.isEmpty();}

    // ───────────────────────────── where things are ─────────────────────────────

    /** The barrage's front, where the stand stands now. */
    static Vec3d front(StandPresence.Body b){return b.at().add(way(b.yaw()).multiply(FRONT_AHEAD*b.scale())).add(0,FRONT_UP*b.scale(),0);}
    /**
     * Fist {@code k}'s knuckles (from 0: left and right in turn) at full stretch, its arm {@code arms} times its size and
     * stretched out further still (StandRush.STRETCH), and drawn back as the one before it lands.
     */
    static float[] stretched(int k,int seed,float arms){return StandPunch.grown(StandRush.fist(k,seed),k&1,arms*(1+StandRush.STRETCH));}
    static float[] drawn(int k,int seed,float arms){return StandPunch.grown(k==0?GUARD:StandRush.fist(k-1,seed),k&1,arms);}
    /** The fist striking {@code u} seconds into a barrage (the body's, or an afterimage's on its own clock) on body {@code b}, its arms {@code arms} times their size, null if none is. */
    static Fist fist(StandPresence.Body b,float u,float end,int seed,float arms){
        if(u<=0||u>end)return null;
        int k=(int)Math.floor(u/StandRush.HALF);float s=StandRush.smooth(u/StandRush.HALF-k);
        Vec3d from=on(drawn(k,seed,arms),b),stop=on(stretched(k,seed,arms),b);
        return new Fist(from,from.lerp(stop,s),stop,s,(k+1)*StandRush.HALF,k);
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** Dark ink under the light: the body's fists' bursts and the bursts on whom it strikes get an ink edge. */
    public static void alpha(EffectMesh m){
        for(var s:FRAME){
            if(s.body!=null)for(int j=0;j<3;j++){
                // the fists that have landed in the last FIST seconds: their bursts
                float lands=landed(s.t,j);if(lands<0||lands>s.rush.ends())continue;
                float u=(s.t-lands)/FIST;if(u<0||u>=1)continue;
                int k=Math.round(lands/StandRush.HALF)-1;
                var p=on(stretched(k,s.seed(),s.arms()),s.body);float a=(float)Math.pow(1-u,.7)*s.on();
                burst(m,p,SIZE*s.arms()*.62*s.body.scale()*(.75+.25*Ease.out(u,0,.4f))*1.14,.36,8,s.seed()+k*7,INK,INK,.62f*a,.62f*a);
            }
            for(var hit:hits(s)){
                float a=hit.alpha();if(a<=0)continue;
                burst(m,hit.at(),hit.size()*1.14,.36,9,hit.seed(),INK,INK,.6f*a,.6f*a);
            }
        }
    }
    public static void additive(EffectMesh m){
        for(var s:FRAME){
            float on=s.on();
            if(s.body!=null&&on>0){
                fists(m,s,s.body,s.t,s.seed(),1,on);
                for(int i=0;i<StandRush.phantoms();i++){
                    float a=StandRush.phantomAlpha(s.rush,i,s.t);if(a<.02f)continue;
                    fists(m,s,StandRush.phantomBody(s.body,i),s.t-StandRush.phantomLag(i),StandRush.phantomSeed(s.rush,i),PHANTOM,on*a*1.6f);
                }
                front(m,s,on);
            }
            for(var hit:hits(s))struck(m,s,hit);
        }
    }
    /**
     * A body's fists ({@code u} seconds into its barrage, {@code size} and {@code alpha} of the body's): the one striking
     * now trails a smear of light from where it was drawn back to the fist, and the ones that landed in the last FIST
     * seconds burst where they stopped, a ring of air round each.
     */
    static void fists(EffectMesh m,Shown s,StandPresence.Body b,float u,int seed,float size,float alpha){
        float end=s.rush.ends(),arms=s.arms(),sz=SIZE*size*arms*b.scale();
        var f=fist(b,u,end,seed,arms);
        if(f!=null){
            float a=alpha*Ease.smooth(f.strike,.05f,.3f);
            Vec3d along=f.at.subtract(f.from);
            if(a>0&&along.lengthSquared()>1e-4){
                Vec3d tail=f.from.add(along.multiply(.15));
                m.softTaper(tail,f.at,.02,.34*sz,VIOLET,.5f*a);
                m.softTaper(tail,f.at,.01,.16*sz,LILAC,.62f*a);
                m.needle(tail,f.at,.07*sz,.85,WHITE,.5f*a);
                m.softDisc(f.at,.22*sz,VIOLET,.45f*a);
            }
        }
        Vec3d w=way(b.yaw()),r=right(w),up=up(w,r);
        for(int j=0;j<3;j++){
            float lands=landed(u,j);if(lands<0||lands>end)continue;
            float v=(u-lands)/FIST;if(v<0||v>=1)continue;
            int k=Math.round(lands/StandRush.HALF)-1;
            var p=on(stretched(k,seed,arms),b);float a=alpha*(float)Math.pow(1-v,.7)*Ease.smooth(u-lands,0,.012f);
            double rad=sz*.62*(.75+.25*Ease.out(v,0,.4f));
            burst(m,p,rad,.36,8,seed+k*7,LILAC,VIOLET,.82f*a,.72f*a);
            m.softDisc(p,rad*.9,BLUE,.32f*a);m.softDisc(p,rad*.32+.02,WHITE,.6f*a*(1-v));
            FxLib.ringPlane(m,p.add(w.multiply(.08)),r,up,.08+.55*sz*Ease.out(v,0,1),.07*sz,VIOLET,.7f*a*(1-v));
            // a streak of air shot on ahead of the fist
            Vec3d q=p.add(w.multiply(.1+.9*sz*Ease.out(v,0,1)));
            m.needle(q,q.add(w.multiply(.45*sz)),.05*sz,.6,k%3==0?WHITE:k%2==0?LILAC:BLUE,.7f*a*(1-v));
        }
    }
    /** When the fist {@code j} before the one striking {@code u} seconds in landed (seconds, -1 for none): j = 0 is the last one. */
    static float landed(float u,int j){
        int k=(int)Math.floor(u/StandRush.HALF)-j;
        return k<1?-1:k*StandRush.HALF;
    }
    /**
     * The front of the barrage: a flickering haze of violet light, streaks of air shooting on ahead out of it (the lines
     * of a rush), and a ring of pressure running out along it every few fists.
     */
    static void front(EffectMesh m,Shown s,float on){
        var b=s.body;Vec3d w=way(b.yaw()),r=right(w),up=up(w,r),c=front(b);float sc=b.scale(),t=s.t;
        float flicker=.78f+.22f*(float)Math.sin(t*Math.PI*2/StandRush.HALF*.5)*(float)Math.sin(t*23.7+1.3);
        // a small haze where the fists land (kept off the stand's own body: the effect layer draws over bodies)
        m.softDisc(c.add(w.multiply(.2*sc)),.42*sc,VIOLET,.22f*on*flicker);
        // the lines of the rush: streaks of air that come out of the barrage and shoot on ahead, each on its own beat
        for(int k=0;k<18;k++){
            float period=.15f+.08f*(float)Ease.hash(k*7+s.seed()),phase=(float)Ease.hash(k*11+s.seed())*period;
            float u=((t+phase)%period)/period;
            double side=(Ease.hash(k*13+s.seed())-.5)*1.5*sc,lift=(Ease.hash(k*17+s.seed())-.45)*1.1*sc,len=(.35+.5*Ease.hash(k*19+s.seed()))*sc;
            Vec3d start=c.add(r.multiply(side)).add(up.multiply(lift)).add(w.multiply((-.2+2.4*u)*sc));
            float a=on*(float)Math.sin(Math.PI*u)*.7f;
            m.needle(start,start.add(w.multiply(len)),.035*sc,.7,k%6==0?WHITE:k%3==1?LILAC:k%2==0?BLUE:VIOLET,a);
        }
        // a ring of pressure, running on ahead every 0.16 s
        for(int k=0;k<2;k++){
            float period=.16f,u=((t+k*period*.5f)%period)/period;
            Vec3d o=c.add(w.multiply((.2+1.9*Ease.out(u,0,1))*sc));
            FxLib.ringPlane(m,o,r,up,(.3+.55*u)*sc,.06*sc,k==0?LILAC:VIOLET,.45f*on*(1-u)*Ease.smooth(u,0,.08f));
        }
    }
    /** A blow's light on whom it struck: where on them it landed, how big, how strongly it shows now, how far through it is, the way it went and whether a guard took it. */
    record Hit(Vec3d at,double size,float alpha,float u,Vec3d way,boolean guarded,int seed){}
    /** The blows of this rush whose light still shows on whom they struck. */
    static List<Hit> hits(Shown s){
        var out=new ArrayList<Hit>();var world=MinecraftClient.getInstance().world;if(world==null)return out;
        int n=0;
        for(var b:s.rush.blowsHeard()){
            n++;float age=s.t-b.at();if(age<0||age>=BLOW)continue;
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
    static void struck(EffectMesh m,Shown s,Hit h){
        float a=h.alpha();if(a<=0)return;
        Vec3d go=h.way(),side=right(go),up=up(go,side);
        if(h.guarded()){
            // a guard took it: a ring of blue light on the guard and its sparks thrown aside
            burst(m,h.at(),h.size()*.8,.4,9,h.seed(),LILAC,BLUE,.7f*a,.6f*a);
            FxLib.ringPlane(m,h.at(),side,up,.2+.6*Ease.out(h.u(),0,1),.06,BLUE,.7f*a*(1-h.u()));
            for(int k=0;k<6;k++){
                double ang=(Ease.hash(h.seed()+k*5)-.5)*2.6;
                Vec3d d=side.multiply(Math.sin(ang)*(k%2==0?1:-1)).add(up.multiply(Math.cos(ang)*.6)).subtract(go.multiply(.3)).normalize();
                Vec3d q=h.at().add(d.multiply(.15+.6*Ease.out(h.u(),0,1)));
                m.needle(q,q.add(d.multiply(.22)),.04,.4,k%2==0?WHITE:LILAC,.8f*a*(1-h.u()));
            }
            return;
        }
        burst(m,h.at(),h.size(),.36,9,h.seed(),LILAC,VIOLET,.85f*a,.75f*a);
        m.star(h.at(),h.size()*.6,WHITE,.35f*a*(1-h.u()));
        m.softDisc(h.at(),h.size()*1.6,VIOLET,.3f*a);m.softDisc(h.at(),h.size()*.4,WHITE,.5f*a*(1-h.u()));
        FxLib.ringPlane(m,h.at().add(go.multiply(.1)),side,up,.18+.75*Ease.out(h.u(),0,1),.07,LILAC,.6f*a*(1-h.u()));
        // light sprayed out of their back, along the blow
        float sp=a*(1-Ease.smooth(h.u(),.3f,1));
        if(sp>0)for(int k=0;k<7;k++){
            double ang=Ease.hash(h.seed()+k*5+3)*Math.PI*2,spread=.15+.4*Ease.hash(h.seed()+k*7);
            Vec3d d=go.add(side.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
            double len=(.4+.8*Ease.hash(h.seed()+k*11))*Ease.out(h.u(),0,.6f);
            Vec3d q=h.at().add(go.multiply(.5)).add(d.multiply(.15+len*.5));
            m.needle(q,q.add(d.multiply(len*.6+.05)),.045,.3,k%4==0?WHITE:k%4==1?GOLD:k%2==0?VIOLET:LILAC,sp);
        }
    }
    /** A little dust kicked up round the feet of whom the rush strikes, now and then (the effect layer's anime dust). */
    public static void dust(Matrix4f matrix,Vec3d camera){
        if(!FxLayer.available()||!KagenoFire.ready())return;
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        for(var s:FRAME){
            int n=0;
            for(var b:s.rush.blowsHeard()){
                // every tenth blow (by the blow's number on the rush's clock): a few small puffs low on the ground
                int number=Math.round(b.at()*20);if(number%20!=dev.sangwoo.celestialfall.stand.StarPlatinumRules.RUSH_FIRST%20)continue;
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
    public static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            float on=s.on();
            if(s.body!=null&&on>0){
                float flicker=.75f+.25f*(float)Math.sin(s.t*Math.PI*2/StandRush.HALF*.5);
                f.light(front(s.body),0xc8b8ff,6,1.5f*on*flicker);
            }
            for(var h:hits(s))f.light(h.at(),h.guarded()?0xb8c8ff:0xe0d4ff,5,1.5f*h.alpha());
        }
    }
}
