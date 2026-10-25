package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
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
 * and a scuff on the floor, and its target trailing light as it flies. Since 2.56.0 (user request: "평타에 화면 효과는
 * 없애줘") none of it is over the whole screen: the punches shake no camera and the finisher takes no impact frame,
 * flash, colour split, light shaft or warp of the air; all of their light is in the world, where the blows land.
 * Every effect is a function of its punch's clock; whatever happens after the contact stays where the blow landed.
 * <p>
 * The heavy punch (R, 2.59.0, user request: "이번에는 R 강펀치 맞으면 날아가고 가드 브레이커야") lands as the finisher
 * does, and bigger still. All through its coil the air is drawn into its cocked fist in rings closing on it, and
 * light, sparks and crackling gather there; as it lands the dome, the rings racing on, the lance and the ripple of the
 * ground are larger, more chunks and dust fly, and a guard held up against it shatters: a pane of blue light before
 * whoever held it, crazed through for an instant, then flung apart in spinning shards along the blow, with a ring of
 * blue light. All of it is in the world, nothing over the whole screen.
 * <p>
 * The Star Finger (T, 2.60.0, user request: "이번에는 스타 핑거 손가락 2개를 엄청 늘려서 5블록까지 적을 찌를 수 있음"):
 * as it takes aim, light gathers at the fingertips; as they shoot out, a spear of light runs along them with streaks of
 * air rushing back past them and a ring of air bursting from the fist; where they strike, the burst, the rings and a
 * spray of light out of whoever they pierce, a short lance on through them; the fingers glow while they are in their
 * target and a flash runs back along them as they snap back. Where they strike no one, the air bursts at their tips;
 * where a guard takes them, it sparks blue. Whoever is incapacitated (2.60.0: struck by it, or their guard broken)
 * has small stars of light turning over their head, as long as it lasts ({@link #dazed}).
 */
public final class StandPunchFx {
    static final Vec3d UP=new Vec3d(0,1,0);
    /** Ink, deep blue violet, blue, violet, lilac, white and a touch of the stand's gold. */
    static final int INK=0x120a36,DEEP=0x33268f,BLUE=0x6d84ff,VIOLET=0x9b7cff,LILAC=0xd8ccff,WHITE=0xffffff,GOLD=0xffd889;
    /** The guard's light, as it shatters (2.59.0): its blue and a paler blue. */
    static final int GUARD=0x5f8dff,GUARD_LIGHT=0xb4ccff;
    /** How big each punch's light is (the finisher's, and the heavy punch's most of all). */
    static final float[] SIZE={0,1.1f,1.15f,1.3f,2.2f,2.6f,.9f};
    /** How long each punch's burst lasts after its contact (seconds). */
    static final float[] BURST={0,.17f,.17f,.2f,.32f,.38f,.2f};
    /** How much larger the heavy punch's finishing light is than the finisher's (its dome, rings, lance, ripple and dust). */
    static final double HEAVY_SCALE=1.3;
    /** A punch's effects are over this long after its contact (seconds): the heavy punch's target flies longer, and its dust where it comes down. */
    static final float AFTER=1.3f,AFTER_HEAVY=2.2f;
    /**
     * Where the blow landed, held from the first frame at or past it: the fist, the way the fist went in (the hook's
     * across the front) and its right, its user's feet, and straight ahead down the punch (where the air is blasted).
     */
    record Blow(Vec3d fist,Vec3d way,Vec3d right,Vec3d feet,Vec3d ahead){}
    private static final Map<Long,Blow> BLOWS=new LinkedHashMap<>();
    /** Where each struck one was as the blow landed (their chest), and where each thrown one came down {time, x, y, z}. */
    private static final Map<Long,Vec3d> STRUCK=new LinkedHashMap<>();
    private static final Map<Long,float[]> LANDINGS=new LinkedHashMap<>();
    /** Whether each struck one held a guard up as the heavy punch landed, as this client saw it then (2.59.0). */
    private static final Map<Long,Boolean> GUARDS=new LinkedHashMap<>();
    /** This frame's punches near the camera. */
    record Shown(UUID user,StandAttack.Seen seen,float t,StandPresence.Body body,Blow blow){
        int combo(){return seen.punch.combo();}
        float hit(){return StandPunch.HIT[combo()];}
        /**
         * The finisher and the heavy punch: they throw whom they strike, and land with everything (not the Star Finger,
         * which comes after them: it lands with its own, StandPunchFx.finger).
         */
        boolean big(){return combo()==4||heavy();}
        boolean heavy(){return combo()==dev.sangwoo.celestialfall.stand.StarPlatinumRules.HEAVY;}
        /** The Star Finger (2.60.0). */
        boolean finger(){return combo()==dev.sangwoo.celestialfall.stand.StarPlatinumRules.FINGER;}
        /** How much larger its finishing light is (the heavy punch's than the finisher's). */
        double grand(){return heavy()?HEAVY_SCALE:1;}
        long key(){return seen.start*31+user.getLeastSignificantBits();}
        int seed(){return (int)(key()&0x7fff);}
    }
    private static final List<Shown> FRAME=new ArrayList<>();
    private static float delta;
    private StandPunchFx(){}

    /** Gathers this frame's punches (within 96 blocks of the camera); returns whether any show. */
    public static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();DAZED.clear();delta=tickDelta;
        if(mc.world==null)return false;
        dazedPrepare(mc,camera);
        for(var e:StandAttack.all().entrySet()){
            var user=mc.world.getPlayerByUuid(e.getKey());if(user==null||user.getPos().squaredDistanceTo(camera)>96*96)continue;
            var body=StandPresence.body(e.getKey());
            for(var seen:e.getValue()){
                float t=seen.age(tickDelta);int combo=seen.punch.combo();float hit=StandPunch.HIT[combo];
                // from the moment it is loaded (the finisher's and the heavy punch's light gathers all through the load, the
                // Star Finger's as it takes aim)
                float from=combo>=4?.1f:StandPunch.strikeFrom(combo)-.08f;
                if(t<from||t>hit+(combo==dev.sangwoo.celestialfall.stand.StarPlatinumRules.HEAVY?AFTER_HEAVY:AFTER))continue;
                // stopped before it landed: nothing lands
                if(!Float.isNaN(seen.punch.stopped())&&seen.punch.stopped()<hit)continue;
                var shown=new Shown(e.getKey(),seen,t,body,null);
                Blow blow=BLOWS.get(shown.key());
                if(blow==null&&t>=hit&&body!=null){blow=combo==dev.sangwoo.celestialfall.stand.StarPlatinumRules.FINGER?fingerBlow(shown,user.getLerpedPos(tickDelta)):blow(seen,body,user.getLerpedPos(tickDelta));if(blow!=null){BLOWS.put(shown.key(),blow);while(BLOWS.size()>48)BLOWS.remove(BLOWS.keySet().iterator().next());}}
                if(blow==null&&body==null)continue;
                FRAME.add(new Shown(e.getKey(),seen,t,body,blow));
            }
        }
        return !FRAME.isEmpty()||!DAZED.isEmpty();
    }
    public static boolean showing(){return !FRAME.isEmpty()||!DAZED.isEmpty();}

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
    /** The finisher's (and the heavy punch's) struck ones as they fly (once the server says whom it struck): where they were struck and where they are now. */
    static List<Vec3d[]> flying(Shown s){
        var out=new ArrayList<Vec3d[]>();var world=MinecraftClient.getInstance().world;if(world==null||!s.big()||!s.seen.landed)return out;
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
    static float burstSize(Shown s){return (float)((.62+.38*Ease.out(s.t,s.hit(),s.hit()+.07f))*SIZE[s.combo()]*(s.big()?1.25:1)*(1-.15*burstU(s)));}
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
        dazedInk(m);
        for(var s:FRAME){
            float t=s.t,hit=s.hit();
            // the guard the heavy punch breaks: each shard inked a little larger, for its dark edge (seen whole a moment before it lands)
            for(var c:broken(s))shatter(m,s,c,t,hit,true);
            if(t<hit-.01f||s.blow==null)continue;
            float a=burstAlpha(s),r=burstSize(s);
            // (the Star Finger's on whom it pierced is its own burst, at the fingertips: none again at their chest)
            if(a>0){
                burst(m,s.blow.fist,r*1.14,.36,10+s.combo(),s.seed(),INK,INK,.78f*a,.78f*a);
                if(!s.finger())for(var c:struck(s))burst(m,c,r*.72*1.14,.36,9,s.seed()+17,INK,INK,.6f*a,.6f*a);
            }
            if(s.finger()&&fingerPierced(s)){float lance=lance(t,hit);if(lance>0)m.needle(s.blow.fist.subtract(s.blow.way.multiply(.3)),s.blow.fist.add(s.blow.way.multiply(FINGER_LANCE*Ease.out(t,hit-.02f,hit+.05f))),.36,.25,INK,.6f*lance);}
            if(!s.big())continue;
            double g=s.grand();
            float u=Ease.unit(t,hit,hit+.34f);
            if(u<1)FxLib.ringPlane(m,s.blow.fist.add(s.blow.way.multiply(.25)),s.blow.right,UP,.5+3.4*g*Ease.out(t,hit,hit+.26f),.42*g,INK,.6f*(1-u)*Ease.smooth(t,hit-.01f,hit+.01f));
            float lance=lance(t,hit);
            if(lance>0){Vec3d a0=s.blow.fist.subtract(s.blow.way.multiply(.5)),a1=s.blow.fist.add(s.blow.way.multiply(lanceLength(s)*Ease.out(t,hit-.02f,hit+.05f)));m.needle(a0,a1,.78*g,.25,INK,.75f*lance);}
        }
    }
    public static void additive(EffectMesh m){
        for(var s:FRAME){
            float t=s.t,hit=s.hit(),size=SIZE[s.combo()];
            if(s.finger())finger(m,s,t,hit);
            else{charge(m,s,t,hit,size);smear(m,s,t,hit,size);}
            for(var c:broken(s))shatter(m,s,c,t,hit,false);
            if(t<hit-.01f||s.blow==null)continue;
            Vec3d p=s.blow.fist,go=s.blow.way,side=s.blow.right,up=up(go,side);
            float a=Ease.smooth(t,hit-.01f,hit+.01f),u=Ease.unit(t,hit,hit+.22f);
            // the impact burst: violet spikes round a lilac heart (the ink under it gives the edge), a small white core
            // and glint; violet is what it is, white only at its very heart
            float ba=burstAlpha(s),br=burstSize(s);
            // (the heavy punch's is the biggest: its heart is violet and its white kept small, or it all burns white; a
            // guard that took the Star Finger turns it blue, and nothing blasts on through it)
            boolean heavy=s.heavy(),guarded=s.finger()&&fingerBlocked(s);
            int heart=guarded?GUARD_LIGHT:heavy?VIOLET:LILAC,edge=guarded?GUARD:VIOLET;
            if(ba>0){
                burst(m,p,br,.36,10+s.combo(),s.seed(),heart,edge,.9f*ba,(heavy?.6f:.8f)*ba);
                m.star(p,br*(heavy?.45:.8),WHITE,(heavy?.3f:.5f)*ba);
            }
            if(u<1){m.softDisc(p,(1.0+.6*u)*size,guarded?GUARD:BLUE,(heavy?.24f:.42f)*a*(1-u));m.softDisc(p,(.5+.25*u)*size,edge,(heavy?.55f:.7f)*a*(1-u));m.softDisc(p,(heavy?.12:.2)*size*(1-u)+.05,WHITE,.8f*a*(1-u));}
            // shock rings opening round the punch's line, one quick and bright, one wider after it
            float ur=Ease.unit(t,hit,hit+.17f);
            if(ur<1)FxLib.ringPlane(m,p.add(go.multiply(.15)),side,up,.2+1.35*size*Ease.out(t,hit,hit+.16f),.24*size,edge,.9f*a*(1-ur));
            float ur2=Ease.unit(t,hit+.03f,hit+.32f);
            if(t>=hit+.03f&&ur2<1)FxLib.ringPlane(m,p.add(go.multiply(.35)),side,up,.3+2.3*size*Ease.out(t,hit+.03f,hit+.3f),.13*size,guarded?GUARD:BLUE,.6f*(1-ur2)*Ease.smooth(t,hit+.03f,hit+.05f));
            Vec3d ahead=s.blow.ahead,aside=right(ahead);
            if(!guarded)blast(m,s,p,ahead,aside,up(ahead,aside),t,hit,size);
            sparks(m,s,p,go,side,up,t,hit,size);
            // whom it struck: a burst of light on them and a spray of it out of their backs (the Star Finger's: its own, below)
            if(!s.finger())for(var c:struck(s)){
                float uc=Ease.unit(t,hit,hit+.28f);if(uc>=1)break;
                if(ba>0)burst(m,c,br*.72,.36,9,s.seed()+17,heavy?VIOLET:LILAC,VIOLET,.8f*ba,(heavy?.55f:.7f)*ba);
                m.softDisc(c,(heavy?.75+.35*uc:1.2+.6*uc)*size,VIOLET,(heavy?.3f:.42f)*a*(1-uc));m.softDisc(c,(heavy?.18:.35)*size*(1-uc)+.06,WHITE,.6f*a*(1-uc));
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
            if(s.big())finisher(m,s,t,hit);
            if(s.finger())fingerStrike(m,s,t,hit,size);
        }
        dazed(m);
    }
    /** While the punch is loaded: light gathering into the drawn-back fist (the finisher's all through its load, and far more of it). */
    static void charge(EffectMesh m,Shown s,float t,float hit,float size){
        if(s.body==null||t>=hit+.01f)return;
        int combo=s.combo();float from=StandPunch.strikeFrom(combo);
        Vec3d p=windFist(combo,s.body).lerp(fist(combo,s.body),StandPunch.striking(s.seen.punch,t));
        if(s.big()){
            boolean heavy=s.heavy();double g=heavy?1.35:1;
            float a=Ease.smooth(t,.1f,.2f)*(1-Ease.smooth(t,hit-.03f,hit+.01f));if(a<=0)return;
            FxLib.converge(m,p,2.3*g,heavy?36:26,t,.1f,hit-.03f,LILAC,s.seed()+5);
            float pulse=.5f+.5f*(float)Math.sin(t*(heavy?46:38));
            m.softDisc(p,((.5+.18*pulse)*a+.1)*g,VIOLET,.55f*a);m.softDisc(p,(.22+.08*pulse)*a*g,WHITE,.85f*a);
            // crackling round the fist, turning
            for(int k=0;k<(heavy?10:6);k++){
                double az=Ease.hash(k*13+s.seed())*Math.PI*2+t*(6+3*Ease.hash(k*17)),el=(Ease.hash(k*19+s.seed())-.5)*2.2,r=(.35+.25*Ease.hash(k*23))*g;
                Vec3d d=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
                m.needle(p.add(d.multiply(r*.4)),p.add(d.multiply(r)),.05,.5,k%2==0?WHITE:LILAC,.8f*a);
            }
            if(heavy)coil(m,s,t,hit,p,a);
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
        int combo=s.combo();double length=(1.5+.45*combo)*size*(s.heavy()?.82:combo==4?.9:s.finger()?.45:1);
        float grow=Ease.out(t,hit,hit+.12f);
        for(int k=0;k<16+combo*5;k++){
            double ang=Ease.hash(k*5+s.seed()+31)*Math.PI*2,spread=.06+.3*Ease.hash(k*7+s.seed()+37);
            Vec3d d=go.add(side.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
            double reach=length*(.45+.55*Ease.hash(k*11+s.seed()+41))*grow;
            Vec3d q=p.add(d.multiply(.1+reach*.4)),e=p.add(d.multiply(.1+reach));
            m.needle(q,e,.07*size,.4,k%5==0?WHITE:k%2==0?VIOLET:BLUE,.8f*a);
        }
        m.softTaper(p,p.add(go.multiply(length*grow)),.25*size,1.0*size,BLUE,(s.heavy()?.18f:.3f)*a);
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
    /**
     * The finisher: a dome of shock rings, five more racing on along it, a lance of light through the target, the ground
     * rippling out, its targets trailing light as they fly. The heavy punch's (2.59.0) is HEAVY_SCALE larger, with a sixth
     * ring, more sparks and a longer trail.
     */
    static void finisher(EffectMesh m,Shown s,float t,float hit){
        if(s.blow==null)return;
        Vec3d p=s.blow.fist,go=s.blow.way,side=s.blow.right,up=up(go,side);
        double g=s.grand();boolean heavy=s.heavy();
        float in=Ease.smooth(t,hit-.01f,hit+.012f);
        // the dome: three rings round the blow in three planes, opening out
        float ud=Ease.unit(t,hit,hit+.4f);
        if(ud<1){
            Vec3d c=p.add(go.multiply(.6));double r=.5+3.4*g*Ease.out(t,hit,hit+.36f);float al=.8f*in*(1-ud);
            FxLib.ringPlane(m,c,side,up,r,.32*g,LILAC,al);FxLib.ringPlane(m,c,go,side,r*.92,.26*g,VIOLET,.85f*al);FxLib.ringPlane(m,c,go,up,r*.88,.24*g,BLUE,.75f*al);
            m.softDisc(c,r*.9,DEEP,.18f*in*(1-ud));
        }
        for(int k=0;k<(heavy?6:5);k++){
            float at=hit+.035f*k,u=Ease.unit(t,at,at+.34f);if(t<at||u>=1)continue;
            Vec3d o=p.add(go.multiply((.4+1.3*k+2.0*Ease.out(t,at,at+.34f))*g));
            FxLib.ringPlane(m,o,side,up,.45+(1.4+.6*k)*g*Ease.out(t,at,at+.28f),(.24+.05*k)*g,k==0?LILAC:k%2==1?VIOLET:BLUE,.9f*(1-u)*Ease.smooth(t,at,at+.015f));
        }
        float s1=in*(1-Ease.smooth(t,hit+.12f,hit+.45f));
        if(s1>0)for(int k=0;k<(heavy?84:60);k++){
            double az=(Ease.hash(k*3+711)-.5)*1.8,el=(Ease.hash(k*5+712)-.4)*1.3,go2=(1.4+4.2*Ease.hash(k*7+713))*g*Ease.out(t,hit,hit+.32f);
            Vec3d d=go.multiply(Math.cos(el)*Math.cos(az)).add(side.multiply(Math.cos(el)*Math.sin(az))).add(up.multiply(Math.sin(el)));
            Vec3d q=p.add(d.multiply(.3+go2)).add(0,-.9*Math.pow(Math.max(0,t-hit),2),0);
            m.needle(q.subtract(d.multiply(.3+.35*Ease.hash(k*11+714))),q,.08,.3,k%5==0?GOLD:k%4==1?WHITE:k%2==0?VIOLET:LILAC,s1);
        }
        // the light lance through the target and on past it, a moment long
        float lance=lance(t,hit);
        if(lance>0){Vec3d a0=p.subtract(go.multiply(.5)),a1=p.add(go.multiply(lanceLength(s)*Ease.out(t,hit-.02f,hit+.05f)));
            m.softLine(a0,a1,1.9*g,DEEP,.4f*lance);m.needle(a0,a1,.56*g,.25,VIOLET,lance);m.needle(a0,a1.subtract(go.multiply(.5)),.2,.25,WHITE,(heavy?.65f:1f)*lance);}
        // the ground ripples out from under the blow
        Vec3d floor=new Vec3d(p.x,s.blow.feet.y+.06,p.z);
        float ug=Ease.unit(t,hit,hit+.55f);
        if(ug<1){FxLib.ring(m,floor,.5+6*g*Ease.out(t,hit,hit+.5f),.55*g,LILAC,.6f*in*(1-ug));
            float ug2=Ease.unit(t,hit+.07f,hit+.6f);if(t>=hit+.07f&&ug2<1)FxLib.ring(m,floor,.4+4.2*g*Ease.out(t,hit+.07f,hit+.55f),.4*g,BLUE,.5f*(1-ug2));}
        // (the heavy punch's target flies longer: its trail lasts longer too)
        float fly=t<hit?0:Ease.smooth(t,hit,hit+.04f)*(1-(heavy?Ease.smooth(t,hit+.9f,hit+1.3f):Ease.smooth(t,hit+.6f,hit+.95f)));
        double trail=heavy?7:5;
        if(fly>0)for(var f:flying(s)){
            Vec3d q=f[1],back=f[0].subtract(q);double l=back.length();if(l<.3)continue;
            Vec3d end=q.add(back.multiply(Math.min(1,trail/l))),dir=back.multiply(1/l),across=right(new Vec3d(dir.x,0,dir.z).lengthSquared()<1e-6?go:new Vec3d(dir.x,0,dir.z).normalize());
            m.softTaper(q,end,1.45,.08,VIOLET,.72f*fly);m.softTaper(q,end,.5,.02,WHITE,.85f*fly);m.softDisc(q,1.1,BLUE,.35f*fly);
            // sparks streaming off it
            for(int k=0;k<8;k++){
                double along=Ease.hash(k*7+91)*Math.min(l,trail),off=(Ease.hash(k*11+93)-.5)*.8;
                Vec3d qq=q.add(dir.multiply(along)).add(across.multiply(off)).add(0,(Ease.hash(k*13+95)-.5)*.6,0);
                m.needle(qq,qq.add(dir.multiply(.5+.4*Ease.hash(k*17))),.05,.3,k%2==0?WHITE:LILAC,.8f*fly);
            }
        }
    }
    /** The finisher's lance: in as the fist lands, gone a sixth of a second later. */
    static float lance(float t,float hit){return Ease.smooth(t,hit-.02f,hit+.005f)*(1-Ease.smooth(t,hit+.06f,hit+.2f));}
    /** How far on past the fist the lance reaches (blocks; the heavy punch's further). */
    static double lanceLength(Shown s){return s.heavy()?8.5:6.5;}
    /**
     * The heavy punch's coil (2.59.0): the air drawn into its cocked fist, rings of it closing on the fist one after
     * another, square to the punch, faster and brighter as they close; and wisps of it pulled in along the arm.
     */
    static void coil(EffectMesh m,Shown s,float t,float hit,Vec3d p,float a){
        Vec3d w=way(s.body.yaw()),side=right(w);
        for(int k=0;k<5;k++){
            float at=.12f+.075f*k,end=at+.2f;if(t<at||t>=end||end>hit)continue;
            float u=Ease.in(t,at,end);
            double r=.18+1.6*(1-u);
            FxLib.ringPlane(m,p,side,UP,r,.05+.07*u,k%2==0?LILAC:VIOLET,a*.75f*Ease.smooth(t,at,at+.05f)*(.35f+.65f*u));
        }
        for(int k=0;k<8;k++){
            float born=.14f+.38f*(float)Ease.hash(s.seed()+k*29),u=(t-born)/.16f;if(u<0||u>=1||born+.16f>hit)continue;
            double az=Ease.hash(s.seed()+k*31)*Math.PI*2,r=(1.8+.6*Ease.hash(s.seed()+k*37))*(1-u*u);
            Vec3d d=side.multiply(Math.cos(az)).add(UP.multiply(Math.sin(az))).add(w.multiply(-.35)).normalize();
            Vec3d q=p.add(d.multiply(r));
            m.needle(q,q.add(d.multiply(-Math.min(r,.45))),.04,.5,k%2==0?WHITE:LILAC,a*(float)Math.sin(Math.PI*u));
        }
    }
    /**
     * Whose guard the heavy punch breaks, at their chests (2.59.0): from a moment before it lands, whom this client sees
     * in its way with a guard up (their guard shows as it is struck); once it lands, as they were then (held), and the
     * server's word on top when it comes (a tick or two after the blow is seen to land).
     */
    static List<Vec3d> broken(Shown s){
        var out=new ArrayList<Vec3d>();if(!s.heavy()||s.t<s.hit()-SHIELD)return out;
        var world=MinecraftClient.getInstance().world;if(world==null)return out;
        boolean landed=s.t>=s.hit()-.01f;
        if(landed)struck(s);
        int[] ids=s.seen.landed?s.seen.struck:inWay(s);
        for(int id:ids){
            long key=s.key()*139+id;Boolean held=GUARDS.get(key);
            boolean broke;
            if(held!=null)broke=held;
            else{
                broke=guarding(world.getEntityById(id),world.getTime());
                if(landed){GUARDS.put(key,broke);while(GUARDS.size()>64)GUARDS.remove(GUARDS.keySet().iterator().next());}
            }
            if(s.seen.landed)for(int b:s.seen.broke)if(b==id)broke=true;
            if(!broke)continue;
            var at=STRUCK.get(s.key()*131+id);
            if(at==null&&!landed&&world.getEntityById(id) instanceof LivingEntity l)at=l.getLerpedPos(delta).add(0,l.getHeight()*.6,0);
            if(at!=null)out.add(at);
        }
        return out;
    }
    /** How long before the heavy punch lands a guard it breaks shows (seconds): the pane it shatters is seen whole first. */
    static final float SHIELD=.08f;
    /** Whether this one holds a guard up now (as the server keeps it: a player's combat state, a training dummy's). */
    static boolean guarding(net.minecraft.entity.Entity e,long now){
        if(e instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity d)return d.guarding();
        if(e instanceof net.minecraft.entity.player.PlayerEntity p){var c=dev.sangwoo.celestialfall.client.CombatClient.snapshot(p.getUuid());return c.guard()&&!c.broken(now);}
        return false;
    }
    /**
     * A guard shattered by the heavy punch (2.59.0), before the chest {@code c} of whoever held it: a pane of blue light
     * square to the blow (the guard), seen whole a moment before the fist lands, crazed white for an instant as it
     * lands, then flung apart in eighteen spinning shards out across it and on along the blow, falling and shrinking
     * as they go and out past the flash of the blow, with a flash and two rings of blue light. The shards are in the
     * world, not turned to the camera. {@code ink}: the alpha pass's dark edges (each shard inked larger).
     */
    static void shatter(EffectMesh m,Shown s,Vec3d c,float t,float hit,boolean ink){
        float age=t-hit;if(age<-SHIELD||age>=1.1f)return;
        // the blow's way: the stand's (it faces down the punch) until the blow has landed
        Vec3d go=s.blow!=null?s.blow.way:s.body!=null?way(s.body.yaw()):way(s.seen.punch.yaw()),side=right(go),up=up(go,side);
        Vec3d o=c.subtract(go.multiply(.55));int seed=s.seed()*7+(int)(c.x*13+c.z*7);
        float in=Ease.smooth(t,hit-SHIELD,hit-SHIELD*.4f),gone=1-Ease.smooth(t,hit+.55f,hit+1.1f);
        float fly=Math.max(0,age-.04f);
        for(int i=0;i<6;i++){
            Vec3d v0=pane(o,side,up,i,seed),v1=pane(o,side,up,i+1,seed);
            double am=(i+.5)*Math.PI/3+(Ease.hash(seed+i*41)-.5)*.5,rm=(.4+.25*Ease.hash(seed+i*43))*PANE;
            Vec3d mid=o.add(side.multiply(Math.cos(am)*rm)).add(up.multiply(Math.sin(am)*rm));
            Vec3d heart=o.add(side.multiply((Ease.hash(seed+i*47)-.5)*.14)).add(up.multiply((Ease.hash(seed+i*53)-.5)*.14));
            Vec3d[][] tris={{heart,v0,mid},{heart,mid,v1},{mid,v0,v1}};
            for(int j=0;j<3;j++){
                int n=seed+i*61+j*17;Vec3d[] q=tris[j];
                Vec3d gc=q[0].add(q[1]).add(q[2]).multiply(1/3.0),outw=gc.subtract(o);
                outw=outw.lengthSquared()<1e-6?up:outw.normalize();
                // flung out across the pane and on along the blow, slowing in the air and falling
                Vec3d vel=outw.multiply(4+4*Ease.hash(n)).add(go.multiply(3+4*Ease.hash(n+1))).add(up.multiply(1.5+2*Ease.hash(n+2)));
                double tau=.35,drag=tau*(1-Math.exp(-fly/tau));
                Vec3d at=gc.add(vel.multiply(drag)).add(0,-6*fly*fly,0);
                Vec3d axis=new Vec3d(Ease.hash(n+3)-.5,Ease.hash(n+4)-.5,Ease.hash(n+5)-.5);axis=axis.lengthSquared()<1e-4?go:axis.normalize();
                double spin=(8+10*Ease.hash(n+6))*(drag+.4*fly)*(Ease.hash(n+7)<.5?-1:1);
                double shrink=(1-.4*Ease.smooth(fly,.15f,.9f))*(ink?1.22:1);
                Vec3d a=at.add(turn(q[0].subtract(gc),axis,spin).multiply(shrink)),b=at.add(turn(q[1].subtract(gc),axis,spin).multiply(shrink)),cc=at.add(turn(q[2].subtract(gc),axis,spin).multiply(shrink));
                float al=in*gone;
                if(ink){m.triangle(a,b,cc,INK,.75f*al);continue;}
                m.triangle(a,b,cc,(i+j)%3==0?GUARD_LIGHT:GUARD,.85f*al);
                // the shard's bright edge
                m.needle(a,b,.045,.5,GUARD_LIGHT,.9f*al);m.needle(b,cc,.035,.5,WHITE,.55f*al);
            }
        }
        if(ink)return;
        // crazed through as the fist lands, the instant before it flies apart
        float crack=Ease.smooth(t,hit-.01f,hit+.004f)*(1-Ease.smooth(t,hit+.04f,hit+.09f));
        if(crack>0)for(int i=0;i<6;i++){
            Vec3d v=pane(o,side,up,i,seed);m.needle(o,v,.06,.5,WHITE,crack);
            double am=(i+.5)*Math.PI/3;m.needle(o,o.add(side.multiply(Math.cos(am)*.6*PANE)).add(up.multiply(Math.sin(am)*.6*PANE)),.04,.5,GUARD_LIGHT,crack);
        }
        // its flash and two rings of blue light opening round it (as it breaks)
        float u=Ease.unit(t,hit,hit+.35f);
        if(t>=hit-.01f&&u<1){
            float on=Ease.smooth(t,hit-.01f,hit+.01f);
            m.softDisc(o,1.6+.8*u,GUARD,.45f*on*(1-u));m.softDisc(o,.45*(1-u)+.1,GUARD_LIGHT,.6f*on*(1-u));
            FxLib.ringPlane(m,o,side,up,.8+2.8*Ease.out(t,hit,hit+.3f),.22,GUARD,.95f*on*(1-u));
        }
        float u2=Ease.unit(t,hit+.05f,hit+.5f);
        if(t>=hit+.05f&&u2<1)FxLib.ringPlane(m,o.add(go.multiply(.3)),side,up,.6+3.6*Ease.out(t,hit+.05f,hit+.45f),.14,GUARD_LIGHT,.7f*(1-u2)*Ease.smooth(t,hit+.05f,hit+.07f));
    }
    /** The guard pane's size (blocks from its heart to its rim). */
    static final double PANE=1.25;
    /** The guard pane's rim, corner {@code i} of six (a little uneven), round {@code o} square to the blow. */
    static Vec3d pane(Vec3d o,Vec3d side,Vec3d up,int i,int seed){
        int k=((i%6)+6)%6;double a=(i+(Ease.hash(seed+k*3)-.5)*.25)*Math.PI/3,r=(.85+.2*Ease.hash(seed+k*5))*PANE;
        return o.add(side.multiply(Math.cos(a)*r)).add(up.multiply(Math.sin(a)*r));
    }
    /** {@code v} turned {@code angle} radians about the unit {@code axis} (Rodrigues). */
    static Vec3d turn(Vec3d v,Vec3d axis,double angle){
        double c=Math.cos(angle),sn=Math.sin(angle);
        return v.multiply(c).add(axis.crossProduct(v).multiply(sn)).add(axis.multiply(axis.dotProduct(v)*(1-c)));
    }
    // ───────────────────────────── the Star Finger (2.60.0) ─────────────────────────────

    /** How far on through whom it pierced its lance runs (blocks). */
    static final double FINGER_LANCE=2.4;
    /**
     * The Star Finger's fingers this frame: where they leave the fist and where their tips are, as drawn on the stand
     * (StarPlatinumRenderer), or (the stand not drawn) about where they would be.
     */
    static Vec3d[] fingerLine(Shown s){
        var drawn=StarPlatinumRenderer.finger(s.user);
        if(drawn!=null)return new Vec3d[]{drawn.root(),drawn.tip()};
        if(s.body==null)return null;
        Vec3d root=fist(s.combo(),s.body);
        var mc=MinecraftClient.getInstance();var user=mc.world==null?null:mc.world.getPlayerByUuid(s.user);
        float ext=StandPunch.fingers(s.seen.punch,s.t,false);
        return new Vec3d[]{root,user==null?root:root.lerp(StandAttack.fingerTip(mc,user,s.seen,delta),ext)};
    }
    /** Where the Star Finger struck, held from the first frame at or past its contact: at the fingertips, along the fingers. */
    static Blow fingerBlow(Shown s,Vec3d feet){
        var line=fingerLine(s);if(line==null)return null;
        Vec3d along=line[1].subtract(line[0]);
        Vec3d go=along.lengthSquared()<1e-6?way(s.seen.punch.yaw()):along.normalize();
        Vec3d flat=new Vec3d(go.x,0,go.z);flat=flat.lengthSquared()<1e-6?way(s.seen.punch.yaw()):flat.normalize();
        return new Blow(line[1],go,right(flat),feet,go);
    }
    /** Whether each Star Finger struck a guard, as this client saw it as the fingers struck (held, so it does not change after). */
    private static final Map<Long,Boolean> FINGER_GUARDED=new LinkedHashMap<>();
    /**
     * Whether the Star Finger struck a guard (2.60.0): whoever stood first in its way held a guard up as it struck, as this
     * client saw it then; and once the server's word comes, it struck no one (a guard takes it whole).
     */
    static boolean fingerBlocked(Shown s){
        if(s.seen.landed&&s.seen.struck.length>0)return false;
        Boolean held=FINGER_GUARDED.get(s.key());
        if(held==null){
            var mc=MinecraftClient.getInstance();var user=mc.world==null?null:mc.world.getPlayerByUuid(s.user);if(user==null)return false;
            var target=StandAttack.fingerAt(mc,user,s.seen,delta);
            held=target!=null&&guarding(target,mc.world.getTime());
            if(s.t>=s.hit()){FINGER_GUARDED.put(s.key(),held);while(FINGER_GUARDED.size()>48)FINGER_GUARDED.remove(FINGER_GUARDED.keySet().iterator().next());}
        }
        return held;
    }
    /** Whether the Star Finger pierced someone: the server says it struck someone, or (until its word comes) someone stands in its way unguarded. */
    static boolean fingerPierced(Shown s){
        if(fingerBlocked(s))return false;
        if(s.seen.landed)return s.seen.struck.length>0;
        var mc=MinecraftClient.getInstance();var user=mc.world==null?null:mc.world.getPlayerByUuid(s.user);
        return user!=null&&StandAttack.fingerAt(mc,user,s.seen,delta)!=null;
    }
    /**
     * The Star Finger's light before and while it strikes: light gathering at the fingertips as it takes aim; as they
     * shoot out, a spear of light along them (a white heart), streaks of air rushing back past them and a ring of air
     * bursting from the fist; while they are out, a glow along them; as they snap back, a flash running back along them.
     */
    static void finger(EffectMesh m,Shown s,float t,float hit){
        var line=fingerLine(s);if(line==null)return;
        Vec3d root=line[0],tip=line[1],along=tip.subtract(root);double len=along.length();
        Vec3d dir=len<1e-4?way(s.seen.punch.yaw()):along.multiply(1/len);
        Vec3d flat=new Vec3d(dir.x,0,dir.z);flat=flat.lengthSquared()<1e-6?way(s.seen.punch.yaw()):flat.normalize();
        Vec3d side=right(flat),up=up(dir,side);
        float ext=StandPunch.fingers(s.seen.punch,t,false);
        // taking aim: light gathering at the fingertips, crackling
        float aim=Ease.smooth(t,.12f,.24f)*(1-Ease.smooth(t,hit-.07f,hit-.03f));
        if(aim>0){
            FxLib.converge(m,tip,1.2,16,t,.12f,hit-.05f,LILAC,s.seed()+5);
            float pulse=.5f+.5f*(float)Math.sin(t*44);
            m.softDisc(tip,.18+.07*pulse,VIOLET,.6f*aim);m.softDisc(tip,.07+.02*pulse,WHITE,.85f*aim);
            for(int k=0;k<5;k++){
                double az=Ease.hash(k*13+s.seed())*Math.PI*2+t*(7+3*Ease.hash(k*17)),el=(Ease.hash(k*19+s.seed())-.5)*2,r=.16+.12*Ease.hash(k*23);
                Vec3d d=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
                m.needle(tip.add(d.multiply(r*.4)),tip.add(d.multiply(r)),.03,.5,k%2==0?WHITE:LILAC,.8f*aim);
            }
        }
        // the shot: a spear of light all along them, air rushing back past them, a ring of air bursting from the fist
        float shot=Ease.smooth(t,hit-StandPunch.SHOOT,hit-StandPunch.SHOOT+.02f)*(1-Ease.smooth(t,hit+.04f,hit+.28f));
        if(shot>0&&len>.05){
            m.softTaper(root,tip,.08,.45,VIOLET,.5f*shot);
            m.softTaper(root,tip,.04,.2,LILAC,.7f*shot);
            m.needle(root,tip,.07,.85,WHITE,.8f*shot);
            for(int k=0;k<10;k++){
                double f0=.15+.85*Ease.hash(s.seed()+k*7),off=(Ease.hash(s.seed()+k*11)-.5)*.45,lift=(Ease.hash(s.seed()+k*13)-.5)*.45,l=(.35+.65*Ease.hash(s.seed()+k*17))*len*.45;
                Vec3d q=root.add(dir.multiply(len*f0)).add(side.multiply(off)).add(up.multiply(lift));
                m.needle(q.subtract(dir.multiply(l)),q,.03,.85,k%3==0?WHITE:k%3==1?LILAC:BLUE,.75f*shot);
            }
            float from=hit-StandPunch.SHOOT,ur=Ease.unit(t,from,from+.2f);
            if(ur<1){
                FxLib.ringPlane(m,root.add(dir.multiply(.12)),side,up,.12+.85*Ease.out(t,from,from+.18f),.07,LILAC,.85f*(1-ur));
                FxLib.ringPlane(m,root.add(dir.multiply(.12+len*.5*Ease.out(t,from,hit))),side,up,.1+.35*Ease.out(t,from,from+.2f),.05,VIOLET,.6f*(1-ur));
            }
        }
        // out in their target: a glow along them, the tips pulsing
        float held=Ease.smooth(ext,.7f,1f)*Ease.smooth(t,hit,hit+.06f);
        if(held>0&&len>.05){
            float pulse=.5f+.5f*(float)Math.sin(t*30);
            m.softTaper(root,tip,.05,.22,VIOLET,.28f*held);m.softDisc(tip,.22+.08*pulse,VIOLET,.4f*held);m.softDisc(tip,.06,WHITE,.6f*held);
        }
        // snapping back: a flash running back along them to the fist
        float back=s.t>hit+.2f?Ease.smooth(ext,.05f,.4f)*(1-Ease.smooth(ext,.6f,.95f))*4:0;
        if(back>0&&len>.05){
            m.needle(tip,root,.06,.85,WHITE,Math.min(1,back)*.7f);m.softTaper(tip,root,.05,.25,LILAC,Math.min(1,back)*.4f);
        }
    }
    /**
     * Where the Star Finger strikes (with the punches' burst and rings there, at its tips): a spray of light out of whom
     * it pierced and a short lance on through them; where it struck no one, the air bursting at its tips; where a guard
     * took it, blue sparks and a ring of blue light.
     */
    static void fingerStrike(EffectMesh m,Shown s,float t,float hit,float size){
        if(s.blow==null)return;
        Vec3d p=s.blow.fist,go=s.blow.way,side=s.blow.right,up=up(go,side);
        float a=Ease.smooth(t,hit-.01f,hit+.01f);
        boolean blocked=fingerBlocked(s),struck=fingerPierced(s);
        if(blocked){
            float u=Ease.unit(t,hit,hit+.3f);if(u>=1)return;
            m.softDisc(p,.9+.5*u,GUARD,.45f*a*(1-u));m.softDisc(p,.25*(1-u)+.05,GUARD_LIGHT,.7f*a*(1-u));
            FxLib.ringPlane(m,p,side,up,.3+1.4*Ease.out(t,hit,hit+.25f),.12,GUARD,.9f*a*(1-u));
            for(int k=0;k<16;k++){
                double ang=Ease.hash(k*5+s.seed()+3)*Math.PI*2;Vec3d d=side.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang))).add(go.multiply(-.4)).normalize();
                double l=(.4+.8*Ease.hash(k*7+s.seed()))*Ease.out(t,hit,hit+.15f);Vec3d q=p.add(d.multiply(.15+l));
                m.needle(q.subtract(d.multiply(.25)),q,.04,.3,k%2==0?GUARD_LIGHT:WHITE,a*(1-u));
            }
            return;
        }
        if(!struck){
            float u=Ease.unit(t,hit,hit+.25f);if(u>=1)return;
            FxLib.ringPlane(m,p,side,up,.15+.9*Ease.out(t,hit,hit+.22f),.08,LILAC,.8f*a*(1-u));
            m.softDisc(p,.35+.3*u,VIOLET,.35f*a*(1-u));
            return;
        }
        // the spray out of their back, along the fingers
        float sp=a*(1-Ease.smooth(t,hit+.05f,hit+.32f));
        if(sp>0)for(int k=0;k<22;k++){
            double ang=Ease.hash(k*5+s.seed()+3)*Math.PI*2,spread=.08+.32*Ease.hash(k*7+s.seed());
            Vec3d d=go.add(side.multiply(Math.cos(ang)*spread)).add(up.multiply(Math.sin(ang)*spread)).normalize();
            double len=(.6+1.3*Ease.hash(k*11+s.seed()))*Ease.out(t,hit,hit+.14f);
            Vec3d q=p.add(d.multiply(.3+len*.5));
            m.needle(q,q.add(d.multiply(len*.5)),.05,.3,k%5==0?WHITE:k%5==1?GOLD:k%2==0?VIOLET:LILAC,sp);
        }
        // a short lance on through them
        float lance=lance(t,hit);
        if(lance>0){
            Vec3d a0=p.subtract(go.multiply(.3)),a1=p.add(go.multiply(FINGER_LANCE*Ease.out(t,hit-.02f,hit+.05f)));
            m.softLine(a0,a1,.9,DEEP,.35f*lance);m.needle(a0,a1,.3,.25,VIOLET,lance);m.needle(a0,a1.subtract(go.multiply(.3)),.1,.25,WHITE,.8f*lance);
        }
    }

    // ───────────────────────────── incapacitated (2.60.0) ─────────────────────────────

    /** Whoever is incapacitated this frame (near the camera): where their head is, how strongly the stars show, whether it is a broken guard, and a seed. */
    record Dazed(Vec3d head,float alpha,boolean guard,int seed,double clock){}
    private static final List<Dazed> DAZED=new ArrayList<>();
    /** When this client first saw each one incapacitated (world ticks, for the stars to come in), and until when. */
    private static final Map<Integer,long[]> DAZED_SINCE=new LinkedHashMap<>();
    /**
     * Gathers whoever is incapacitated (a player's combat state, a training dummy's): Star Platinum's Star Finger struck
     * them, or their guard is broken (2.60.0: "가드 브레이커 개념이 2초 무력화").
     */
    static void dazedPrepare(MinecraftClient mc,Vec3d camera){
        long now=mc.world.getTime();double clock=now+delta;
        for(var e:mc.world.getEntitiesByClass(LivingEntity.class,new net.minecraft.util.math.Box(camera,camera).expand(48),e->e.isAlive()&&!e.isSpectator())){
            long until;boolean guard;
            if(e instanceof net.minecraft.entity.player.PlayerEntity p){
                if(p==mc.getCameraEntity()&&!mc.gameRenderer.getCamera().isThirdPerson())continue;
                var c=dev.sangwoo.celestialfall.client.CombatClient.snapshot(p.getUuid());
                guard=c.broken(clock);until=guard?c.brokenUntil():c.disabledUntil();
            }else if(e instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity d){
                guard=d.brokenUntil()>clock;until=guard?d.brokenUntil():d.disabledUntil();
            }else continue;
            if(until<=clock){DAZED_SINCE.remove(e.getId());continue;}
            long[] since=DAZED_SINCE.get(e.getId());
            if(since==null||since[1]!=until&&clock>since[1]){since=new long[]{now,until};DAZED_SINCE.put(e.getId(),since);while(DAZED_SINCE.size()>64)DAZED_SINCE.remove(DAZED_SINCE.keySet().iterator().next());}
            since[1]=until;
            float in=Ease.smooth((float)(clock-since[0]),0,4),out=Ease.smooth((float)(until-clock),0,6);
            DAZED.add(new Dazed(e.getLerpedPos(delta).add(0,e.getHeight()+DAZED_UP,0),in*out,guard,e.getId()*7919,clock));
        }
    }
    /**
     * The stars over whoever is incapacitated: how high over their head they turn (blocks over its top: under their name,
     * round the crown), how far out, and how big each star is (its points' reach).
     */
    static final double DAZED_UP=.14,DAZED_R=.5,DAZED_STAR=.17;
    /** Where the dazed star {@code k} of three is now: turning round the head, bobbing a little. */
    static Vec3d dazedStar(Dazed d,int k){
        double a=d.clock/20*Math.PI*2*1.1+Ease.hash(d.seed)*Math.PI*2+k*Math.PI*2/3;
        return d.head.add(Math.cos(a)*DAZED_R,.06*Math.sin(a*2+k),Math.sin(a)*DAZED_R);
    }
    static float dazedTwinkle(Dazed d,int k){return .8f+.2f*(float)Math.sin(d.clock*.9+k*2.1);}
    /** The dazed stars' dark edges (each star in ink a little bigger, under its light: they read against a bright sky). */
    static void dazedInk(EffectMesh m){
        for(var d:DAZED){
            if(d.alpha<.01f)continue;
            for(int k=0;k<3;k++){float a=.75f*d.alpha*dazedTwinkle(d,k);star(m,dazedStar(d,k),DAZED_STAR*1.3,.5,d.clock*.08+k,INK,INK,a,a);}
        }
    }
    /** Three five-pointed stars turning round the head of whoever is incapacitated, a faint ring under them (blue over a broken guard). */
    static void dazed(EffectMesh m){
        for(var d:DAZED){
            if(d.alpha<.01f)continue;
            int rim=d.guard?GUARD:VIOLET,light=d.guard?GUARD_LIGHT:LILAC;
            FxLib.ring(m,d.head,DAZED_R,.04,rim,.5f*d.alpha);
            for(int k=0;k<3;k++){
                Vec3d q=dazedStar(d,k);float a=d.alpha*dazedTwinkle(d,k);
                m.softDisc(q,DAZED_STAR*1.4,rim,.35f*a);
                star(m,q,DAZED_STAR,.5,d.clock*.08+k,light,rim,a,a);
                m.star(q,DAZED_STAR*.45,WHITE,.75f*a);
            }
        }
    }
    /**
     * An even five-pointed star facing the camera: its points out to {@code r}, the notches between them at {@code inner}
     * of it, turned {@code turn} radians in its plane; {@code core} at its middle, {@code rim} out to its points.
     */
    static void star(EffectMesh m,Vec3d p,double r,double inner,double turn,int core,int rim,float alpha,float coreAlpha){
        if(alpha<=0||r<=0)return;
        Vec3d normal=m.camera().subtract(p);if(normal.lengthSquared()<1e-6)return;normal=normal.normalize();
        Vec3d u=normal.crossProduct(UP);if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(normal).normalize();
        double step=Math.PI*2/5;
        for(int i=0;i<5;i++){
            double am=turn+Math.PI/2+i*step,a0=am-step/2,a1=am+step/2;
            Vec3d q0=p.add(u.multiply(Math.cos(a0)*r*inner)).add(v.multiply(Math.sin(a0)*r*inner));
            Vec3d q1=p.add(u.multiply(Math.cos(a1)*r*inner)).add(v.multiply(Math.sin(a1)*r*inner));
            Vec3d tp=p.add(u.multiply(Math.cos(am)*r)).add(v.multiply(Math.sin(am)*r));
            // the middle out to the notches on either side of this point, then the point
            m.vertex(p,core,coreAlpha);m.vertex(q0,rim,alpha);m.vertex(tp,rim,alpha);m.vertex(p,core,coreAlpha);
            m.vertex(p,core,coreAlpha);m.vertex(tp,rim,alpha);m.vertex(q1,rim,alpha);m.vertex(p,core,coreAlpha);
        }
    }
    /** Dust kicked up under the finisher's (and the heavy punch's) lunge, where it struck and where its targets come down (the effect layer's anime dust). */
    public static void dust(Matrix4f matrix,Vec3d camera){
        if(!FxLayer.available()||!KagenoFire.ready())return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        for(var s:FRAME){
            if(s.blow==null||!s.big())continue;
            float t=s.t,hit=s.hit();double g=s.grand();
            Vec3d under=ground(s.blow.fist,s.blow.feet.y);
            FxLib.dustRing(under,t,hit,.4,3.1*g,s.heavy()?18:14,1.0*g,dust,puffs,61);
            FxLib.dustRing(under.add(s.blow.way.multiply(1.6*g)),t,hit+.05f,.3,2.2*g,s.heavy()?13:10,.85*g,dust,puffs,67);
            int k=0;
            for(var id:s.seen.struck){
                var e=world.getEntityById(id);if(!(e instanceof LivingEntity l))continue;
                long key=s.key()*137+id;float[] land=LANDINGS.get(key);
                if(land==null&&t>hit+.25f&&t<hit+(s.heavy()?1.8f:1.2f)&&l.isOnGround()){
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
     * The effect layer: each blow lights the world round it (a guard the heavy punch broke flashes blue), and the
     * finisher's targets as they fly; a scuff is blown along the floor under the finisher. Nothing over the whole
     * screen (2.56.0, user request: "평타에 화면 효과는 없애줘"): no light shaft, colour split, impact frame, white
     * flash, warp of the air or shake of the camera.
     */
    public static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            float t=s.t,hit=s.hit();
            if(t<hit-.01f||s.blow==null)continue;
            boolean big=s.big(),heavy=s.heavy();
            float k=kick(t,hit,big?.18f:.1f);
            f.light(s.blow.fist,0xc8b8ff,heavy?15:big?14:8,(heavy?4.6f:big?4.5f:2.4f)*k);
            for(var c:struck(s))f.light(c,0xe0d4ff,big?11:6,(big?3.4f:1.8f)*k);
            // the guard it broke, flashing blue
            for(var c:broken(s))f.light(c,0x9cb8ff,9,2.6f*kick(t,hit,.14f));
            if(!big)continue;
            float heat=1-Ease.smooth(t,hit+.05f,hit+.55f),al=Ease.smooth(t,hit,hit+.04f)*(1-Ease.smooth(t,hit+.8f,hit+1.05f));
            f.decal(ground(s.blow.fist,s.blow.feet.y).add(s.blow.way.multiply(1.1*s.grand())),s.blow.way,(2.4*Ease.out(t,hit,hit+.14f)+.2)*s.grand(),.55*s.grand(),heat,al,23.7f);
            for(var fl:flying(s))f.light(fl[1],0x9c86ff,7,1.6f*(1-(heavy?Ease.smooth(t,hit+.9f,hit+1.3f):Ease.smooth(t,hit+.6f,hit+.95f))));
        }
    }
    /** Chunks of the floor kicked up under the finisher's blow (the heavy punch's, more of them). */
    public static void solids(FxSolids solids){
        for(var s:FRAME)if(s.big()&&s.blow!=null&&s.t>=s.hit()&&s.t<s.hit()+1.0f&&s.seen.landed&&s.seen.struck.length>0)
            FxLib.chunks(solids,ground(s.blow.fist,s.blow.feet.y).add(s.blow.way.multiply(.7)),s.t-s.hit(),s.heavy()?20:14,977,1.8*s.grand(),6.5,.16,.45f);
    }
}
