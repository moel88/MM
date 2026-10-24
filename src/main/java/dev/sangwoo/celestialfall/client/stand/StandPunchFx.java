package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * The light of Star Platinum's punches (2.54.0; 2.55.0 made far stronger, user request: "이펙트도 대폭 강화해주고"),
 * drawn with Kageno's on the effect layer (KagenoRenderer): crisp shapes with dark ink edges, light that adds up and
 * blooms. While a punch is loaded, light gathers at the drawn-back fist; as it strikes, a smear of light follows the fist
 * out (the hook's sweeps round in arcs) and the air streaks past the arm; as it lands, a spiky impact burst inked dark
 * round its edge flashes at the fist, shock rings open round the punch's line, a cone of blasted air and a ring of
 * pressure shoot on ahead of it to whoever stands there, and sparks fly; whom it struck burst with light and a spray
 * of it out of their backs. The finisher lands far bigger: light gathers into the fist all through its load, a dome of
 * shock rings and five more racing on, a lance of light through its target, the ground rippling out and dust, chunks
 * and a scuff on the floor, its target trailing light as it flies, and close by the screen takes an impact frame.
 * Every effect is a function of its punch's clock; whatever happens after the contact stays where the blow landed.
 */
public final class StandPunchFx {
    static final Vec3d UP=new Vec3d(0,1,0);
    /** Ink, deep blue violet, blue, violet, lilac, white and a touch of the stand's gold. */
    static final int INK=0x120a36,DEEP=0x33268f,BLUE=0x6d84ff,VIOLET=0x9b7cff,LILAC=0xd8ccff,WHITE=0xffffff,GOLD=0xffd889;
    /** How big each punch's light is (the finisher's the most). */
    static final float[] SIZE={0,1.1f,1.15f,1.3f,2.2f};
    /** How long each punch's burst lasts after its contact (seconds). */
    static final float[] BURST={0,.17f,.17f,.2f,.32f};
    /** A punch's effects are over this long after its contact (seconds). */
    static final float AFTER=1.3f;
    /**
     * Where the blow landed, held from the first frame at or past it: the fist, the way the fist went in (the hook's
     * across the front) and its right, its user's feet, and straight ahead down the punch (where the air is blasted).
     */
    record Blow(Vec3d fist,Vec3d way,Vec3d right,Vec3d feet,Vec3d ahead){}
    private static final Map<Long,Blow> BLOWS=new LinkedHashMap<>();
    /** Where each struck one was as the blow landed (their chest), and where each thrown one came down {time, x, y, z}. */
    private static final Map<Long,Vec3d> STRUCK=new LinkedHashMap<>();
    private static final Map<Long,float[]> LANDINGS=new LinkedHashMap<>();
    /** This frame's punches near the camera. */
    record Shown(UUID user,StandAttack.Seen seen,float t,StandPresence.Body body,Blow blow){
        int combo(){return seen.punch.combo();}
        float hit(){return StandPunch.HIT[combo()];}
        long key(){return seen.start*31+user.getLeastSignificantBits();}
        int seed(){return (int)(key()&0x7fff);}
    }
    private static final List<Shown> FRAME=new ArrayList<>();
    private static float delta;
    private StandPunchFx(){}

    /** Gathers this frame's punches (within 96 blocks of the camera); returns whether any show. */
    public static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();delta=tickDelta;
        if(mc.world==null)return false;
        for(var e:StandAttack.all().entrySet()){
            var user=mc.world.getPlayerByUuid(e.getKey());if(user==null||user.getPos().squaredDistanceTo(camera)>96*96)continue;
            var body=StandPresence.body(e.getKey());
            for(var seen:e.getValue()){
                float t=seen.age(tickDelta);int combo=seen.punch.combo();float hit=StandPunch.HIT[combo];
                // from the moment it is loaded (the finisher's light gathers all through its load)
                float from=combo==4?.1f:StandPunch.strikeFrom(combo)-.08f;
                if(t<from||t>hit+AFTER)continue;
                // stopped before it landed: nothing lands
                if(!Float.isNaN(seen.punch.stopped())&&seen.punch.stopped()<hit)continue;
                var shown=new Shown(e.getKey(),seen,t,body,null);
                Blow blow=BLOWS.get(shown.key());
                if(blow==null&&t>=hit&&body!=null){blow=blow(seen,body,user.getLerpedPos(tickDelta));BLOWS.put(shown.key(),blow);while(BLOWS.size()>48)BLOWS.remove(BLOWS.keySet().iterator().next());}
                if(blow==null&&body==null)continue;
                FRAME.add(new Shown(e.getKey(),seen,t,body,blow));
            }
        }
        return !FRAME.isEmpty();
    }
    public static boolean showing(){return !FRAME.isEmpty();}

    // ───────────────────────────── where things are ─────────────────────────────

    /** The punch's way and its right, from the stand's facing. */
    static Vec3d way(float yaw){return Vec3d.fromPolar(0,yaw);}
    static Vec3d right(Vec3d way){return new Vec3d(-way.z,0,way.x);}
    /** A point given as blocks ahead, to the right and up from the stand's feet (at its size 1), where the stand stands now. */
    static Vec3d on(float[] f,StandPresence.Body body){
        Vec3d w=way(body.yaw()),r=right(w);
        return body.at().add(w.multiply(f[0]*body.scale())).add(r.multiply(f[1]*body.scale())).add(0,f[2]*body.scale(),0);
    }
    /** The striking fist's knuckles where the stand stands now, in its punching stance, and loaded (drawn back). */
    static Vec3d fist(int combo,StandPresence.Body body){return on(StandPunch.fist(combo),body);}
    static Vec3d windFist(int combo,StandPresence.Body body){return on(StandPunch.windFist(combo),body);}
    static Blow blow(StandAttack.Seen seen,StandPresence.Body body,Vec3d feet){
        Vec3d w=way(body.yaw());
        // the hook comes in across the front from its left, the rest straight on
        Vec3d go=seen.punch.combo()==3?w.multiply(.45).add(right(w).multiply(.9)).normalize():w;
        return new Blow(fist(seen.punch.combo(),body),go,right(go),feet,w);
    }
    /** Up, square to a punch's way and its right. */
    static Vec3d up(Vec3d go,Vec3d side){Vec3d u=side.crossProduct(go);return u.lengthSquared()<1e-6?UP:u.normalize();}
    /**
     * Whom the punch struck: their chests as the blow landed, held (the finisher's fly off from there). Until the
     * server's word comes (a tick or two after the blow is seen to land), whom this client sees in its way, as the
     * server picks them, so the light comes with the blow.
     */
    static List<Vec3d> struck(Shown s){
        var out=new ArrayList<Vec3d>();var world=MinecraftClient.getInstance().world;if(world==null||s.t<s.hit()-.01f)return out;
        int[] ids=s.seen.landed?s.seen.struck:inWay(s);
        for(int id:ids){
            long key=s.key()*131+id;var at=STRUCK.get(key);
            if(at==null){
                var e=world.getEntityById(id);if(!(e instanceof LivingEntity l))continue;
                at=l.getLerpedPos(delta).add(0,l.getHeight()*.6,0);
                STRUCK.put(key,at);while(STRUCK.size()>96)STRUCK.remove(STRUCK.keySet().iterator().next());
            }
            out.add(at);
        }
        return out;
    }
    /** Who stands in the punch's way now, as the server judges it (StarPlatinumRules.lane, in sight), at most four. */
    static int[] inWay(Shown s){
        var mc=MinecraftClient.getInstance();var user=mc.world.getPlayerByUuid(s.user);if(user==null)return new int[0];
        Vec3d at=user.getLerpedPos(delta),f=Vec3d.fromPolar(0,s.seen.punch.yaw()),r=new Vec3d(-f.z,0,f.x);
        return mc.world.getEntitiesByClass(LivingEntity.class,new net.minecraft.util.math.Box(at,at).expand(dev.sangwoo.celestialfall.stand.StarPlatinumRules.REACH_BIG+2),e->{
            if(e==user||!e.isAlive()||e.isSpectator()||!e.canHit()||e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)return false;
            Vec3d d=e.getLerpedPos(delta).subtract(at);
            return dev.sangwoo.celestialfall.stand.StarPlatinumRules.lane(s.combo(),d.dotProduct(f),d.dotProduct(r),d.y,e.getWidth()*.5)&&user.canSee(e);
        }).stream().limit(4).mapToInt(LivingEntity::getId).toArray();
    }
    /** The finisher's struck ones as they fly (once the server says whom it struck): where they were struck and where they are now. */
    static List<Vec3d[]> flying(Shown s){
        var out=new ArrayList<Vec3d[]>();var world=MinecraftClient.getInstance().world;if(world==null||s.combo()!=4||!s.seen.landed)return out;
        for(int id:s.seen.struck){
            var e=world.getEntityById(id);if(!(e instanceof LivingEntity l))continue;
            var from=STRUCK.get(s.key()*131+id);if(from==null)continue;
            out.add(new Vec3d[]{from,l.getLerpedPos(delta).add(0,l.getHeight()*.6,0)});
        }
        return out;
    }
    static float kick(float t,float at,float tau){return FxLib.kick(t,at,tau);}
    /** How far through its burst a punch is (0 as it lands, 1 once it is gone). */
    static float burstU(Shown s){return Ease.unit(s.t,s.hit(),s.hit()+BURST[s.combo()]);}
    /** The burst of a punch at its fist (and smaller on whom it struck): how big it is now and how strongly it shows. */
    static float burstSize(Shown s){return (float)((.62+.38*Ease.out(s.t,s.hit(),s.hit()+.07f))*SIZE[s.combo()]*(s.combo()==4?1.25:1)*(1-.15*burstU(s)));}
    static float burstAlpha(Shown s){return Ease.smooth(s.t,s.hit()-.006f,s.hit()+.012f)*(float)Math.pow(1-burstU(s),.7);}

    /**
     * A spiky impact burst facing the camera (a flash of light, so it may turn to the camera): {@code n} spikes of
     * uneven length round a disc, their tips out to {@code r} and their roots at {@code inner} of it, the centre in
     * {@code core} and the rim and spikes in {@code rim}. Drawn in ink a little bigger first, it gets a dark edge.
     */
    static void burst(EffectMesh m,Vec3d p,double r,double inner,int n,int seed,int core,int rim,float alpha,float coreAlpha){
        if(alpha<=0||r<=0)return;
        Vec3d normal=m.camera().subtract(p);if(normal.lengthSquared()<1e-6)return;normal=normal.normalize();
        Vec3d u=normal.crossProduct(UP);if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(normal).normalize();
        double turn=Ease.hash(seed)*Math.PI*2,step=Math.PI*2/n,root=r*inner;
        for(int i=0;i<n;i++){
            double a0=turn+(i-.5)*step,a1=turn+(i+.5)*step,am=turn+i*step+(Ease.hash(seed+i*7)-.5)*.6*step;
            double tip=r*(.55+.45*Ease.hash(seed+i*3));
            Vec3d q0=p.add(u.multiply(Math.cos(a0)*root)).add(v.multiply(Math.sin(a0)*root));
            Vec3d q1=p.add(u.multiply(Math.cos(a1)*root)).add(v.multiply(Math.sin(a1)*root));
            Vec3d tp=p.add(u.multiply(Math.cos(am)*tip)).add(v.multiply(Math.sin(am)*tip));
            // the disc out to this spike's roots, then the spike
            m.vertex(p,core,coreAlpha);m.vertex(q0,rim,alpha);m.vertex(q1,rim,alpha);m.vertex(p,core,coreAlpha);
            m.vertex(q0,rim,alpha);m.vertex(tp,rim,alpha*.8f);m.vertex(q1,rim,alpha);m.vertex(q1,rim,alpha);
        }
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** Dark ink under the light: every burst gets an ink edge (read against a bright sky), and the finisher's ring and lance too. */
    public static void alpha(EffectMesh m){
        for(var s:FRAME){
            float t=s.t,hit=s.hit();if(t<hit-.01f||s.blow==null)continue;
            float a=burstAlpha(s),r=burstSize(s);
            if(a>0){
                burst(m,s.blow.fist,r*1.14,.36,10+s.combo(),s.seed(),INK,INK,.78f*a,.78f*a);
                for(var c:struck(s))burst(m,c,r*.72*1.14,.36,9,s.seed()+17,INK,INK,.6f*a,.6f*a);
            }
            if(s.combo()!=4)continue;
            float u=Ease.unit(t,hit,hit+.34f);
            if(u<1)FxLib.ringPlane(m,s.blow.fist.add(s.blow.way.multiply(.25)),s.blow.right,UP,.5+3.4*Ease.out(t,hit,hit+.26f),.42,INK,.6f*(1-u)*Ease.smooth(t,hit-.01f,hit+.01f));
            float lance=lance(t,hit);
            if(lance>0){Vec3d a0=s.blow.fist.subtract(s.blow.way.multiply(.5)),a1=s.blow.fist.add(s.blow.way.multiply(6.5*Ease.out(t,hit-.02f,hit+.05f)));m.needle(a0,a1,.78,.25,INK,.75f*lance);}
        }
    }
    public static void additive(EffectMesh m){
        for(var s:FRAME){
            float t=s.t,hit=s.hit(),size=SIZE[s.combo()];
            charge(m,s,t,hit,size);
            smear(m,s,t,hit,size);
            if(t<hit-.01f||s.blow==null)continue;
            Vec3d p=s.blow.fist,go=s.blow.way,side=s.blow.right,up=up(go,side);
            float a=Ease.smooth(t,hit-.01f,hit+.01f),u=Ease.unit(t,hit,hit+.22f);
            // the impact burst: violet spikes round a lilac heart (the ink under it gives the edge), a small white core
            // and glint; violet is what it is, white only at its very heart
            float ba=burstAlpha(s),br=burstSize(s);
            if(ba>0){
                burst(m,p,br,.36,10+s.combo(),s.seed(),LILAC,VIOLET,.9f*ba,.8f*ba);
                m.star(p,br*.8,WHITE,.5f*ba);
            }
            if(u<1){m.softDisc(p,(1.0+.6*u)*size,BLUE,.42f*a*(1-u));m.softDisc(p,(.5+.25*u)*size,VIOLET,.7f*a*(1-u));m.softDisc(p,.2*size*(1-u)+.05,WHITE,.8f*a*(1-u));}
            // shock rings opening round the punch's line, one quick and bright, one wider after it
            float ur=Ease.unit(t,hit,hit+.17f);
            if(ur<1)FxLib.ringPlane(m,p.add(go.multiply(.15)),side,up,.2+1.35*size*Ease.out(t,hit,hit+.16f),.24*size,VIOLET,.9f*a*(1-ur));
            float ur2=Ease.unit(t,hit+.03f,hit+.32f);
            if(t>=hit+.03f&&ur2<1)FxLib.ringPlane(m,p.add(go.multiply(.35)),side,up,.3+2.3*size*Ease.out(t,hit+.03f,hit+.3f),.13*size,BLUE,.6f*(1-ur2)*Ease.smooth(t,hit+.03f,hit+.05f));
            Vec3d ahead=s.blow.ahead,aside=right(ahead);
            blast(m,s,p,ahead,aside,up(ahead,aside),t,hit,size);
            sparks(m,s,p,go,side,up,t,hit,size);
            // whom it struck: a burst of light on them and a spray of it out of their backs
            for(var c:struck(s)){
                float uc=Ease.unit(t,hit,hit+.28f);if(uc>=1)break;
                if(ba>0)burst(m,c,br*.72,.36,9,s.seed()+17,LILAC,VIOLET,.8f*ba,.7f*ba);
                m.softDisc(c,(1.2+.6*uc)*size,VIOLET,.42f*a*(1-uc));m.softDisc(c,.35*size*(1-uc)+.06,WHITE,.6f*a*(1-uc));
                FxLib.ringPlane(m,c,side,up,.35+1.6*size*Ease.out(t,hit,hit+.22f),.24,LILAC,.6f*a*(1-uc));
                float sp=a*(1-Ease.smooth(t,hit+.06f,hit+.3f));
                if(sp>0)for(int k=0;k<12+s.combo()*3;k++){
                    double ang=Ease.hash(k*5+s.seed()+3)*Math.PI*2,spread=.15+.4*Ease.hash(k*7+s.seed());
                    Vec3d d=go.add(side.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
                    double len=(.7+1.4*Ease.hash(k*11+s.seed()))*size*Ease.out(t,hit,hit+.16f);
                    Vec3d q=c.add(d.multiply(.25+len*.45));
                    m.needle(q,q.add(d.multiply(len*.55)),.06*size,.3,k%5==0?WHITE:k%5==1?GOLD:k%2==0?VIOLET:LILAC,sp);
                }
            }
            if(s.combo()==4)finisher(m,s,t,hit);
        }
    }
    /** While the punch is loaded: light gathering into the drawn-back fist (the finisher's all through its load, and far more of it). */
    static void charge(EffectMesh m,Shown s,float t,float hit,float size){
        if(s.body==null||t>=hit+.01f)return;
        int combo=s.combo();float from=StandPunch.strikeFrom(combo);
        Vec3d p=windFist(combo,s.body).lerp(fist(combo,s.body),StandPunch.striking(s.seen.punch,t));
        if(combo==4){
            float a=Ease.smooth(t,.1f,.2f)*(1-Ease.smooth(t,hit-.03f,hit+.01f));if(a<=0)return;
            FxLib.converge(m,p,2.3,26,t,.1f,hit-.03f,LILAC,s.seed()+5);
            float pulse=.5f+.5f*(float)Math.sin(t*38);
            m.softDisc(p,(.5+.18*pulse)*a+.1,VIOLET,.55f*a);m.softDisc(p,(.22+.08*pulse)*a,WHITE,.85f*a);
            // crackling round the fist, turning
            for(int k=0;k<6;k++){
                double az=Ease.hash(k*13+s.seed())*Math.PI*2+t*(6+3*Ease.hash(k*17)),el=(Ease.hash(k*19+s.seed())-.5)*2.2,r=.35+.25*Ease.hash(k*23);
                Vec3d d=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
                m.needle(p.add(d.multiply(r*.4)),p.add(d.multiply(r)),.05,.5,k%2==0?WHITE:LILAC,.8f*a);
            }
            return;
        }
        float a=Ease.smooth(t,from-.08f,from)*(1-Ease.smooth(t,hit-.03f,hit+.01f));if(a<=0)return;
        FxLib.converge(m,p,.9*size,9,t,from-.1f,hit-.03f,LILAC,s.seed()+5);
        m.softDisc(p,.2*size*a+.04,VIOLET,.6f*a);m.softDisc(p,.09*size*a+.02,WHITE,.85f*a);
    }
    /**
     * As the punch strikes, a smear of light follows the fist out from where it was loaded (narrow at the start, wide
     * at the fist, a white heart), with streaks of air past the arm; the hook's sweeps round in arcs.
     */
    static void smear(EffectMesh m,Shown s,float t,float hit,float size){
        if(s.body==null)return;
        int combo=s.combo();float from=StandPunch.strikeFrom(combo);
        float a=Ease.smooth(t,from,from+.03f)*(1-Ease.smooth(t,hit+.02f,hit+.12f));if(a<=0)return;
        Vec3d start=windFist(combo,s.body),end=s.blow!=null?s.blow.fist:start.lerp(fist(combo,s.body),StandPunch.striking(s.seen.punch,t));
        Vec3d w=way(s.body.yaw()),r=right(w);
        if(combo==3){
            // the hook: arcs round the stand's left shoulder from where it was loaded through the fist
            Vec3d o=s.body.at().add(0,1.52*s.body.scale(),0).add(r.multiply(-.18));
            Vec3d to=end.subtract(o),sf=start.subtract(o);double rad=Math.hypot(to.x,to.z);
            double stop=Math.atan2(to.dotProduct(r),to.dotProduct(w)),begin=Math.atan2(sf.dotProduct(r),sf.dotProduct(w));
            double span=Math.max(Math.toRadians(40),Math.abs(stop-begin));
            for(int k=0;k<4;k++){
                double rr=rad*(.78+.1*k);Vec3d prev=null;
                for(int i=0;i<=14;i++){
                    double f=i/14.0,ang=stop-span*(1-f)*(1-.12*k);
                    Vec3d q=o.add(w.multiply(Math.cos(ang)*rr)).add(r.multiply(Math.sin(ang)*rr)).add(0,(end.y-o.y)+(k-1.5)*.07,0);
                    if(prev!=null)m.needle(prev,q,(.06+.08*f)*size,.5,k==1?WHITE:k==2?VIOLET:LILAC,a*(float)(.2+.8*f));
                    prev=q;
                }
            }
            m.softDisc(end,.35*size,VIOLET,.45f*a);
            return;
        }
        Vec3d along=end.subtract(start);if(along.lengthSquared()<1e-4)return;
        Vec3d tail=start.add(along.multiply(.1)),dir=along.normalize(),upv=up(dir,r);
        m.softTaper(tail,end,.05,.5*size,VIOLET,.55f*a);
        m.softTaper(tail,end,.02,.22*size,LILAC,.7f*a);
        m.needle(tail,end,.1*size,.85,WHITE,.75f*a);
        // streaks of air rushing past the arm
        for(int k=0;k<8;k++){
            double off=(Ease.hash(k*7+combo)-.5)*.7*size,lift=(Ease.hash(k*11+combo)-.5)*.55*size,len=(.8+.9*Ease.hash(k*13+combo))*size;
            Vec3d q=end.add(r.multiply(off)).add(upv.multiply(lift)).subtract(dir.multiply(.12));
            m.needle(q.subtract(dir.multiply(len)),q,.045*size,.85,k%3==0?WHITE:k%3==1?LILAC:BLUE,a*.85f);
        }
    }
    /** The air blasted on ahead of the fist: a cone of streaks shot out along the punch, its glow, and a ring of pressure running on along it to whoever stands there. */
    static void blast(EffectMesh m,Shown s,Vec3d p,Vec3d go,Vec3d side,Vec3d up,float t,float hit,float size){
        float a=Ease.smooth(t,hit-.01f,hit+.01f)*(1-Ease.smooth(t,hit+.07f,hit+.3f));if(a<=0)return;
        int combo=s.combo();double length=(1.5+.45*combo)*size*(combo==4?.9:1);
        float grow=Ease.out(t,hit,hit+.12f);
        for(int k=0;k<16+combo*5;k++){
            double ang=Ease.hash(k*5+s.seed()+31)*Math.PI*2,spread=.06+.3*Ease.hash(k*7+s.seed()+37);
            Vec3d d=go.add(side.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
            double reach=length*(.45+.55*Ease.hash(k*11+s.seed()+41))*grow;
            Vec3d q=p.add(d.multiply(.1+reach*.4)),e=p.add(d.multiply(.1+reach));
            m.needle(q,e,.07*size,.4,k%5==0?WHITE:k%2==0?VIOLET:BLUE,.8f*a);
        }
        m.softTaper(p,p.add(go.multiply(length*grow)),.25*size,1.0*size,BLUE,.3f*a);
        float ur=Ease.unit(t,hit,hit+.2f);
        if(ur<1)FxLib.ringPlane(m,p.add(go.multiply(.2+length*.85*Ease.out(t,hit,hit+.2f))),side,up,(.3+.7*ur)*size,.15*size,VIOLET,.8f*Ease.smooth(t,hit-.005f,hit+.015f)*(1-ur));
    }
    /** Sparks and chips thrown out ahead of the blow, falling as they slow. */
    static void sparks(EffectMesh m,Shown s,Vec3d p,Vec3d go,Vec3d side,Vec3d up,float t,float hit,float size){
        int combo=s.combo();float age=t-hit;if(age<0||age>=.45f)return;
        for(int k=0;k<22+combo*7;k++){
            float life=.18f+.22f*(float)Ease.hash(k*3+s.seed()+51),u=age/life;if(u>=1)continue;
            double az=(Ease.hash(k*5+s.seed()+53)-.5)*2.6,el=(Ease.hash(k*7+s.seed()+57)-.35)*1.6,speed=(3+5*Ease.hash(k*11+s.seed()+59))*size;
            Vec3d d=go.multiply(Math.cos(el)*Math.cos(az)).add(side.multiply(Math.cos(el)*Math.sin(az))).add(up.multiply(Math.sin(el))).normalize();
            Vec3d q=p.add(d.multiply(speed*life*(1-Math.pow(1-u,2))*.5)).add(0,-2.2*age*age,0);
            float al=(1-u)*Ease.smooth(age,0,.015f);int col=k%4==0?GOLD:k%6==1?WHITE:k%2==0?VIOLET:LILAC;
            m.needle(q.subtract(d.multiply(.16*(1-u)*size+.03)),q,.05*size,.3,col,al);
            if(k%3==0)m.softDisc(q,.05*size*(1-.5*u),col,.8f*al);
        }
    }
    /** The finisher: a dome of shock rings, five more racing on along it, a lance of light through the target, the ground rippling out, its targets trailing light as they fly. */
    static void finisher(EffectMesh m,Shown s,float t,float hit){
        if(s.blow==null)return;
        Vec3d p=s.blow.fist,go=s.blow.way,side=s.blow.right,up=up(go,side);
        float in=Ease.smooth(t,hit-.01f,hit+.012f);
        // the dome: three rings round the blow in three planes, opening out
        float ud=Ease.unit(t,hit,hit+.4f);
        if(ud<1){
            Vec3d c=p.add(go.multiply(.6));double r=.5+3.4*Ease.out(t,hit,hit+.36f);float al=.8f*in*(1-ud);
            FxLib.ringPlane(m,c,side,up,r,.32,LILAC,al);FxLib.ringPlane(m,c,go,side,r*.92,.26,VIOLET,.85f*al);FxLib.ringPlane(m,c,go,up,r*.88,.24,BLUE,.75f*al);
            m.softDisc(c,r*.9,DEEP,.18f*in*(1-ud));
        }
        for(int k=0;k<5;k++){
            float at=hit+.035f*k,u=Ease.unit(t,at,at+.34f);if(t<at||u>=1)continue;
            Vec3d o=p.add(go.multiply(.4+1.3*k+2.0*Ease.out(t,at,at+.34f)));
            FxLib.ringPlane(m,o,side,up,.45+(1.4+.6*k)*Ease.out(t,at,at+.28f),.24+.05*k,k==0?LILAC:k%2==1?VIOLET:BLUE,.9f*(1-u)*Ease.smooth(t,at,at+.015f));
        }
        float s1=in*(1-Ease.smooth(t,hit+.12f,hit+.45f));
        if(s1>0)for(int k=0;k<60;k++){
            double az=(Ease.hash(k*3+711)-.5)*1.8,el=(Ease.hash(k*5+712)-.4)*1.3,go2=(1.4+4.2*Ease.hash(k*7+713))*Ease.out(t,hit,hit+.32f);
            Vec3d d=go.multiply(Math.cos(el)*Math.cos(az)).add(side.multiply(Math.cos(el)*Math.sin(az))).add(up.multiply(Math.sin(el)));
            Vec3d q=p.add(d.multiply(.3+go2)).add(0,-.9*Math.pow(Math.max(0,t-hit),2),0);
            m.needle(q.subtract(d.multiply(.3+.35*Ease.hash(k*11+714))),q,.08,.3,k%5==0?GOLD:k%4==1?WHITE:k%2==0?VIOLET:LILAC,s1);
        }
        // the light lance through the target and on past it, a moment long
        float lance=lance(t,hit);
        if(lance>0){Vec3d a0=p.subtract(go.multiply(.5)),a1=p.add(go.multiply(6.5*Ease.out(t,hit-.02f,hit+.05f)));
            m.softLine(a0,a1,1.9,DEEP,.4f*lance);m.needle(a0,a1,.56,.25,VIOLET,lance);m.needle(a0,a1.subtract(go.multiply(.5)),.2,.25,WHITE,lance);}
        // the ground ripples out from under the blow
        Vec3d floor=new Vec3d(p.x,s.blow.feet.y+.06,p.z);
        float ug=Ease.unit(t,hit,hit+.55f);
        if(ug<1){FxLib.ring(m,floor,.5+6*Ease.out(t,hit,hit+.5f),.55,LILAC,.6f*in*(1-ug));
            float ug2=Ease.unit(t,hit+.07f,hit+.6f);if(t>=hit+.07f&&ug2<1)FxLib.ring(m,floor,.4+4.2*Ease.out(t,hit+.07f,hit+.55f),.4,BLUE,.5f*(1-ug2));}
        float fly=t<hit?0:Ease.smooth(t,hit,hit+.04f)*(1-Ease.smooth(t,hit+.6f,hit+.95f));
        if(fly>0)for(var f:flying(s)){
            Vec3d q=f[1],back=f[0].subtract(q);double l=back.length();if(l<.3)continue;
            Vec3d end=q.add(back.multiply(Math.min(1,5/l))),dir=back.multiply(1/l),across=right(new Vec3d(dir.x,0,dir.z).lengthSquared()<1e-6?go:new Vec3d(dir.x,0,dir.z).normalize());
            m.softTaper(q,end,1.45,.08,VIOLET,.72f*fly);m.softTaper(q,end,.5,.02,WHITE,.85f*fly);m.softDisc(q,1.1,BLUE,.35f*fly);
            // sparks streaming off it
            for(int k=0;k<8;k++){
                double along=Ease.hash(k*7+91)*Math.min(l,5),off=(Ease.hash(k*11+93)-.5)*.8;
                Vec3d qq=q.add(dir.multiply(along)).add(across.multiply(off)).add(0,(Ease.hash(k*13+95)-.5)*.6,0);
                m.needle(qq,qq.add(dir.multiply(.5+.4*Ease.hash(k*17))),.05,.3,k%2==0?WHITE:LILAC,.8f*fly);
            }
        }
    }
    /** The finisher's lance: in as the fist lands, gone a sixth of a second later. */
    static float lance(float t,float hit){return Ease.smooth(t,hit-.02f,hit+.005f)*(1-Ease.smooth(t,hit+.06f,hit+.2f));}
    /** Dust kicked up under the finisher's lunge, where it struck and where its targets come down (the effect layer's anime dust). */
    public static void dust(Matrix4f matrix,Vec3d camera){
        if(!FxLayer.available()||!KagenoFire.ready())return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        for(var s:FRAME){
            if(s.blow==null||s.combo()!=4)continue;
            float t=s.t,hit=s.hit();
            Vec3d under=ground(s.blow.fist,s.blow.feet.y);
            FxLib.dustRing(under,t,hit,.4,3.1,14,1.0,dust,puffs,61);
            FxLib.dustRing(under.add(s.blow.way.multiply(1.6)),t,hit+.05f,.3,2.2,10,.85,dust,puffs,67);
            int k=0;
            for(var id:s.seen.struck){
                var e=world.getEntityById(id);if(!(e instanceof LivingEntity l))continue;
                long key=s.key()*137+id;float[] land=LANDINGS.get(key);
                if(land==null&&t>hit+.25f&&t<hit+1.2f&&l.isOnGround()){
                    var q=l.getLerpedPos(delta);land=new float[]{t,(float)q.x,(float)q.y,(float)q.z};LANDINGS.put(key,land);
                    while(LANDINGS.size()>64)LANDINGS.remove(LANDINGS.keySet().iterator().next());
                }
                if(land!=null)FxLib.dustRing(new Vec3d(land[1],land[2],land[3]),t,land[0],.35,2.2,11,.9,dust,puffs,71+k*13);
                k++;
            }
        }
        if(!dust.isEmpty())KagenoFire.dust(matrix,camera,dust,puffs,(float)(world.getTime()+delta));
    }
    static Vec3d ground(Vec3d p,double feet){return new Vec3d(p.x,feet,p.z);}
    /**
     * The effect layer: each blow lights the world round it; the finisher's lance lights the way ahead, a scuff is blown
     * along the floor under it, and close by a light shaft, a colour split and an impact frame.
     */
    public static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            float t=s.t,hit=s.hit();
            if(t<hit-.01f||s.blow==null)continue;
            boolean big=s.combo()==4;
            float k=kick(t,hit,big?.18f:.1f);
            f.light(s.blow.fist,0xc8b8ff,big?14:8,(big?4.5f:2.4f)*k);
            for(var c:struck(s))f.light(c,0xe0d4ff,big?11:6,(big?3.4f:1.8f)*k);
            if(!big)continue;
            float near=(float)Math.max(0,Math.min(1,(26-camera.distanceTo(s.blow.fist))/10));
            if(near>0){f.shaft(s.blow.fist,0xe8dcff,.7f*near*kick(t,hit,.22f));f.chroma(near*.013f*kick(t,hit,.12f));f.impact(near*impact(t,hit),impactMode(t,hit));}
            float heat=1-Ease.smooth(t,hit+.05f,hit+.55f),al=Ease.smooth(t,hit,hit+.04f)*(1-Ease.smooth(t,hit+.8f,hit+1.05f));
            f.decal(ground(s.blow.fist,s.blow.feet.y).add(s.blow.way.multiply(1.1)),s.blow.way,2.4*Ease.out(t,hit,hit+.14f)+.2,.55,heat,al,23.7f);
            for(var fl:flying(s))f.light(fl[1],0x9c86ff,7,1.6f*(1-Ease.smooth(t,hit+.6f,hit+.95f)));
        }
    }
    /** The finisher's impact frame: in over two frames as it lands, held about four, out over two (white shapes on dark, then dark on white). */
    static float impact(float t,float at){return t<at-.01f||t>=at+.145f?0:Ease.smooth(t,at-.01f,at+.02f)*(1-Ease.smooth(t,at+.105f,at+.145f));}
    static float impactMode(float t,float at){return Ease.smooth(t,at+.05f,at+.085f);}
    /** The air bends round each blow, and the finisher's in a dome and rings racing on past its target. */
    public static void distort(FxDistort d){
        for(var s:FRAME){
            float t=s.t,hit=s.hit();if(s.blow==null||t<hit-.01f||t>=hit+.5f)continue;
            float in=Ease.smooth(t,hit-.01f,hit+.015f),size=SIZE[s.combo()];Vec3d up=up(s.blow.way,s.blow.right);
            d.ring(s.blow.fist,s.blow.right,up,.3+2.0*size*Ease.out(t,hit,hit+.26f),.8*size,.8f*in*(1-Ease.unit(t,hit,hit+.26f)));
            if(s.combo()!=4)continue;
            d.ring(s.blow.fist.add(s.blow.way.multiply(.6)),s.blow.way,s.blow.right,.5+3.2*Ease.out(t,hit,hit+.36f),.9,.6f*in*(1-Ease.unit(t,hit,hit+.38f)));
            for(int k=1;k<4;k++){float at=hit+.035f*k;if(t>=at&&t<at+.32f)d.ring(s.blow.fist.add(s.blow.way.multiply(.4+1.3*k+2.0*Ease.out(t,at,at+.34f))),s.blow.right,up,.45+(1.4+.6*k)*Ease.out(t,at,at+.28f),.7,.55f*(1-Ease.unit(t,at,at+.32f)));}
        }
    }
    /** Chunks of the floor kicked up under the finisher's blow. */
    public static void solids(FxSolids solids){
        for(var s:FRAME)if(s.combo()==4&&s.blow!=null&&s.t>=s.hit()&&s.t<s.hit()+1.0f&&s.seen.landed&&s.seen.struck.length>0)
            FxLib.chunks(solids,ground(s.blow.fist,s.blow.feet.y).add(s.blow.way.multiply(.7)),s.t-s.hit(),14,977,1.8,6.5,.16,.45f);
    }
    /** A white flash over the whole screen for the finisher's user and whoever stands close as it lands. */
    public static float flash(Vec3d camera){
        float best=0;
        for(var s:FRAME)if(s.combo()==4&&s.blow!=null){float near=(float)Math.max(0,Math.min(1,(16-camera.distanceTo(s.blow.fist))/8));best=Math.max(best,.32f*near*kick(s.t,s.hit(),.06f));}
        return best;
    }
    /** The camera shakes as each blow lands (the finisher hardest), for its user, whom it struck and those close by: {yaw, pitch, roll} degrees. */
    public static float[] shake(Vec3d camera,float tickDelta){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null)return null;
        float amount=0;double time=mc.world.getTime()+tickDelta;
        for(var e:StandAttack.all().entrySet())for(var seen:e.getValue()){
            int combo=seen.punch.combo();float t=seen.age(tickDelta),hit=StandPunch.HIT[combo];
            if(t<hit-.03f||t>hit+.7f||!Float.isNaN(seen.punch.stopped())&&seen.punch.stopped()<hit)continue;
            boolean mine=e.getKey().equals(mc.player.getUuid()),struck=false;
            for(int id:seen.struck)if(id==mc.player.getId())struck=true;
            var user=mc.world.getPlayerByUuid(e.getKey());if(user==null)continue;
            float reach=mine||struck?1:(float)Math.max(0,1-user.getPos().distanceTo(camera)/20);if(reach<=0)continue;
            float strength=(combo==4?2.4f:combo==3?1.0f:.7f)*(struck?1.4f:1);
            amount=Math.max(amount,strength*reach*kick(t,hit,combo==4?.15f:.09f));
        }
        if(amount<=.001f)return null;
        amount*=(float)CelestialFall.CONFIG.screenShakeStrength;
        return new float[]{amount*(float)(Math.sin(time*2.9)*.7+Math.sin(time*5.3)*.3),amount*(float)(Math.cos(time*3.7)*.7+Math.sin(time*6.1)*.3),amount*.35f*(float)Math.sin(time*4.4)};
    }
}
