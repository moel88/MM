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
 * The light of Star Platinum's punches (2.54.0), drawn with Kageno's on the effect layer (KagenoRenderer): crisp
 * shapes, light that adds up and blooms. As a punch speeds in, streaks run along its arm; as it lands, the fist flashes
 * white in a violet-blue halo, a shock ring opens round its line and the air bursts on ahead; whom it struck light up
 * where they were struck. The finisher lands bigger: rings race on along it, the air and the ground take the blow
 * (refraction, dust, a scuff), its target trails light as it flies, and close by the screen takes an impact frame.
 * Every effect is a function of its punch's clock; whatever happens after the contact stays where the blow landed.
 */
public final class StandPunchFx {
    static final Vec3d UP=new Vec3d(0,1,0);
    /** Ink, deep blue violet, blue, violet, lilac, white and a touch of the stand's gold. */
    static final int INK=0x120a36,DEEP=0x33268f,BLUE=0x6d84ff,VIOLET=0x9b7cff,LILAC=0xd8ccff,WHITE=0xffffff,GOLD=0xffd889;
    /** How big each punch's light is (the finisher's the most). */
    static final float[] SIZE={0,.85f,.9f,1.15f,1.9f};
    /** A punch's effects are over this long after its contact (seconds). */
    static final float AFTER=1.1f;
    /** Where the blow landed, held from the first frame at or past it: the fist, the punch's way, its right, and its user's feet. */
    record Blow(Vec3d fist,Vec3d way,Vec3d right,Vec3d feet){}
    private static final Map<Long,Blow> BLOWS=new LinkedHashMap<>();
    /** Where each struck one was as the blow landed (their chest), and where each thrown one came down {time, x, y, z}. */
    private static final Map<Long,Vec3d> STRUCK=new LinkedHashMap<>();
    private static final Map<Long,float[]> LANDINGS=new LinkedHashMap<>();
    /** This frame's punches near the camera. */
    record Shown(UUID user,StandAttack.Seen seen,float t,StandPresence.Body body,Blow blow){
        int combo(){return seen.punch.combo();}
        float hit(){return StandPunch.HIT[combo()];}
        long key(){return seen.start*31+user.getLeastSignificantBits();}
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
                if(t<hit-.12f||t>hit+AFTER)continue;
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
    /** The striking fist's knuckles where the stand stands now, in its punching stance. */
    static Vec3d fist(int combo,StandPresence.Body body){
        float[] f=StandPunch.fist(combo);Vec3d w=way(body.yaw()),r=right(w);
        return body.at().add(w.multiply(f[0]*body.scale())).add(r.multiply(f[1]*body.scale())).add(0,f[2]*body.scale(),0);
    }
    static Blow blow(StandAttack.Seen seen,StandPresence.Body body,Vec3d feet){
        Vec3d w=way(body.yaw());
        // the hook comes in across the front from its left, the rest straight on
        Vec3d go=seen.punch.combo()==3?w.multiply(.45).add(right(w).multiply(.9)).normalize():w;
        return new Blow(fist(seen.punch.combo(),body),go,right(go),feet);
    }
    /** Where the fist is now (before the blow, from the stand as it stands; after, where it landed). */
    static Vec3d fistNow(Shown s){return s.blow!=null?s.blow.fist:fist(s.combo(),s.body);}
    static Vec3d wayNow(Shown s){return s.blow!=null?s.blow.way:s.combo()==3?way(s.body.yaw()).multiply(.45).add(right(way(s.body.yaw())).multiply(.9)).normalize():way(s.body.yaw());}
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

    // ───────────────────────────── the passes ─────────────────────────────

    /** Dark ink under the light: the finisher's shock ring and its streaks get an ink edge, read against a bright sky. */
    public static void alpha(EffectMesh m){
        for(var s:FRAME){
            if(s.combo()!=4||s.blow==null)continue;
            float t=s.t,hit=s.hit(),u=Ease.unit(t,hit,hit+.3f);if(u>=1)continue;
            Vec3d p=s.blow.fist;
            FxLib.ringPlane(m,p.add(s.blow.way.multiply(.25)),s.blow.right,UP,.5+2.6*Ease.out(t,hit,hit+.24f),.34,INK,.55f*(1-u)*Ease.smooth(t,hit-.01f,hit+.01f));
        }
    }
    public static void additive(EffectMesh m){
        for(var s:FRAME){
            float t=s.t,hit=s.hit(),size=SIZE[s.combo()];
            streaks(m,s,t,hit,size);
            if(t<hit-.01f)continue;
            Vec3d p=fistNow(s),go=wayNow(s),side=right(go);
            float a=Ease.smooth(t,hit-.01f,hit+.01f),u=Ease.unit(t,hit,hit+.22f);
            // the fist flashes white in a violet-blue halo, with a four-point glint
            if(u<1){
                m.softDisc(p,(.75+.55*u)*size,BLUE,.55f*a*(1-u));m.softDisc(p,(.42+.2*u)*size,VIOLET,.8f*a*(1-u));m.softDisc(p,.24*size*(1-u)+.05,WHITE,a*(1-u));
                m.star(p,(.8+.5*u)*size,WHITE,.85f*a*(1-u));
            }
            // a shock ring opening round its line, and the air bursting on ahead of it in thin streaks
            float ur=Ease.unit(t,hit,hit+.2f);
            if(ur<1)FxLib.ringPlane(m,p.add(go.multiply(.15)),side,UP,.18+1.05*size*Ease.out(t,hit,hit+.18f),.2*size,LILAC,.9f*a*(1-ur));
            float sp=a*(1-Ease.smooth(t,hit+.06f,hit+.24f));
            if(sp>0)for(int k=0;k<10+s.combo()*3;k++){
                double az=(Ease.hash(k*3+s.combo()*17)-.5)*1.5,el=(Ease.hash(k*5+s.combo()*11)-.5)*1.2,len=(.5+.9*Ease.hash(k*7))*size*Ease.out(t,hit,hit+.12f);
                Vec3d d=go.multiply(Math.cos(el)*Math.cos(az)).add(side.multiply(Math.cos(el)*Math.sin(az))).add(0,Math.sin(el),0).normalize();
                Vec3d q=p.add(d.multiply(.15+len*.4));
                m.needle(q,q.add(d.multiply(len*.6)),.05*size,.3,k%4==0?GOLD:k%2==0?WHITE:LILAC,sp);
            }
            // whom it struck light up where it landed on them
            for(var c:struck(s)){
                float uc=Ease.unit(t,hit,hit+.26f);if(uc>=1)break;
                m.softDisc(c,(1.1+.5*uc)*size,VIOLET,.5f*a*(1-uc));m.softDisc(c,.45*size*(1-uc)+.08,WHITE,.9f*a*(1-uc));
                FxLib.ringPlane(m,c,side,UP,.3+1.3*size*Ease.out(t,hit,hit+.2f),.22,WHITE,.7f*a*(1-uc));
            }
            if(s.combo()==4)finisher(m,s,t,hit);
        }
    }
    /** Streaks along the arm as the punch speeds in, gone as it lands (the hook's sweep round in an arc). */
    static void streaks(EffectMesh m,Shown s,float t,float hit,float size){
        float a=Ease.smooth(t,hit-.07f,hit-.02f)*(1-Ease.smooth(t,hit,hit+.06f));if(a<=0||s.body==null)return;
        Vec3d fist=fistNow(s),w=way(s.body.yaw()),r=right(w);
        if(s.combo()==3){
            // the hook: arcs round the stand's left shoulder through the fist
            Vec3d o=s.body.at().add(0,1.52*s.body.scale(),0).add(r.multiply(-.18));
            Vec3d to=fist.subtract(o);double rad=Math.hypot(to.x,to.z),end=Math.atan2(to.dotProduct(r),to.dotProduct(w));
            for(int k=0;k<3;k++){
                double rr=rad*(.82+.12*k),span=Math.toRadians(85-k*14);
                Vec3d prev=null;
                for(int i=0;i<=10;i++){
                    double ang=end-span*(1-i/10.0);
                    Vec3d q=o.add(w.multiply(Math.cos(ang)*rr)).add(r.multiply(Math.sin(ang)*rr)).add(0,(fist.y-o.y)+(k-1)*.08,0);
                    if(prev!=null)m.needle(prev,q,.05*size*(i/10.0+.3),.5,k==1?WHITE:LILAC,a*(float)(i/10.0));
                    prev=q;
                }
            }
            return;
        }
        for(int k=0;k<6;k++){
            double off=(Ease.hash(k*7+s.combo())-.5)*.5*size,lift=(Ease.hash(k*11+s.combo())-.5)*.4*size,len=(.9+.7*Ease.hash(k*13))*size;
            Vec3d q=fist.add(r.multiply(off)).add(0,lift,0).subtract(w.multiply(.15));
            m.needle(q.subtract(w.multiply(len)),q,.045*size,.85,k%3==0?WHITE:LILAC,a*.85f);
        }
    }
    /** The finisher: rings racing on along it one after another, a blast of air streaks, its targets trailing light as they fly. */
    static void finisher(EffectMesh m,Shown s,float t,float hit){
        if(s.blow==null)return;
        Vec3d p=s.blow.fist,go=s.blow.way,side=s.blow.right;
        for(int k=0;k<3;k++){
            float at=hit+.04f*k,u=Ease.unit(t,at,at+.32f);if(t<at||u>=1)continue;
            Vec3d o=p.add(go.multiply(.4+1.5*k+1.8*Ease.out(t,at,at+.32f)));
            FxLib.ringPlane(m,o,side,UP,.45+(1.2+.6*k)*Ease.out(t,at,at+.26f),.22+.06*k,k==0?WHITE:k==1?LILAC:BLUE,.9f*(1-u)*Ease.smooth(t,at,at+.015f));
        }
        float s1=Ease.smooth(t,hit-.01f,hit+.01f)*(1-Ease.smooth(t,hit+.12f,hit+.42f));
        if(s1>0)for(int k=0;k<30;k++){
            double az=(Ease.hash(k*3+711)-.5)*1.3,el=(Ease.hash(k*5+712)-.4)*1.0,go2=(1.2+3.4*Ease.hash(k*7+713))*Ease.out(t,hit,hit+.3f);
            Vec3d d=go.multiply(Math.cos(el)*Math.cos(az)).add(side.multiply(Math.cos(el)*Math.sin(az))).add(0,Math.sin(el),0);
            Vec3d q=p.add(d.multiply(.3+go2));
            m.needle(q.subtract(d.multiply(.25+.3*Ease.hash(k*11+714))),q,.07,.3,k%5==0?GOLD:k%2==0?WHITE:LILAC,s1);
        }
        // the light lance through the target and on past it, a moment long
        float lance=Ease.smooth(t,hit-.02f,hit+.005f)*(1-Ease.smooth(t,hit+.05f,hit+.18f));
        if(lance>0){Vec3d a0=p.subtract(go.multiply(.4)),a1=p.add(go.multiply(4.5*Ease.out(t,hit-.02f,hit+.04f)));
            m.softLine(a0,a1,1.4,DEEP,.35f*lance);m.needle(a0,a1,.42,.25,VIOLET,lance);m.needle(a0,a1.subtract(go.multiply(.4)),.15,.25,WHITE,lance);}
        float fly=t<hit?0:Ease.smooth(t,hit,hit+.04f)*(1-Ease.smooth(t,hit+.5f,hit+.85f));
        if(fly>0)for(var f:flying(s)){
            Vec3d q=f[1],back=f[0].subtract(q);double l=back.length();if(l<.3)continue;
            Vec3d end=q.add(back.multiply(Math.min(1,4/l)));
            m.softTaper(q,end,1.15,.08,VIOLET,.7f*fly);m.softTaper(q,end,.38,.02,WHITE,.85f*fly);m.softDisc(q,.9,BLUE,.32f*fly);
        }
    }
    /** Dust kicked up under the finisher's lunge and where its targets come down (the effect layer's anime dust). */
    public static void dust(Matrix4f matrix,Vec3d camera){
        if(!FxLayer.available()||!KagenoFire.ready())return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        for(var s:FRAME){
            if(s.blow==null)continue;
            float t=s.t,hit=s.hit();
            Vec3d under=ground(s.blow.fist,s.blow.feet.y);
            if(s.combo()==4)FxLib.dustRing(under,t,hit,.35,2.3,11,.85,dust,puffs,61);
            if(s.combo()==4){
                int k=0;
                for(var id:s.seen.struck){
                    var e=world.getEntityById(id);if(!(e instanceof LivingEntity l))continue;
                    long key=s.key()*137+id;float[] land=LANDINGS.get(key);
                    if(land==null&&t>hit+.25f&&t<hit+1.1f&&l.isOnGround()){
                        var q=l.getLerpedPos(delta);land=new float[]{t,(float)q.x,(float)q.y,(float)q.z};LANDINGS.put(key,land);
                        while(LANDINGS.size()>64)LANDINGS.remove(LANDINGS.keySet().iterator().next());
                    }
                    if(land!=null)FxLib.dustRing(new Vec3d(land[1],land[2],land[3]),t,land[0],.35,1.9,9,.85,dust,puffs,71+k*13);
                    k++;
                }
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
            float k=kick(t,hit,s.combo()==4?.16f:.09f);
            f.light(s.blow.fist,0xc8b8ff,s.combo()==4?11:6,(s.combo()==4?3.6f:1.8f)*k);
            for(var c:struck(s))f.light(c,0xe0d4ff,s.combo()==4?9:5,(s.combo()==4?2.8f:1.4f)*k);
            if(s.combo()!=4)continue;
            float near=(float)Math.max(0,Math.min(1,(24-camera.distanceTo(s.blow.fist))/10));
            if(near>0){f.shaft(s.blow.fist,0xe8dcff,.5f*near*kick(t,hit,.2f));f.chroma(near*.009f*kick(t,hit,.1f));f.impact(near*impact(t,hit),impactMode(t,hit));}
            float heat=1-Ease.smooth(t,hit+.05f,hit+.5f),al=Ease.smooth(t,hit,hit+.04f)*(1-Ease.smooth(t,hit+.75f,hit+.95f));
            f.decal(ground(s.blow.fist,s.blow.feet.y).add(s.blow.way.multiply(.8)),s.blow.way,1.5*Ease.out(t,hit,hit+.12f)+.2,.4,heat,al,23.7f);
            for(var fl:flying(s))f.light(fl[1],0x9c86ff,6,1.4f*(1-Ease.smooth(t,hit+.5f,hit+.85f)));
        }
    }
    /** The finisher's impact frame: in over two frames as it lands, held about three, out over two (white shapes on dark, then dark on white). */
    static float impact(float t,float at){return t<at-.01f||t>=at+.125f?0:Ease.smooth(t,at-.01f,at+.02f)*(1-Ease.smooth(t,at+.085f,at+.125f));}
    static float impactMode(float t,float at){return Ease.smooth(t,at+.04f,at+.075f);}
    /** The air bends round each blow, and in rings racing on past the finisher's target. */
    public static void distort(FxDistort d){
        for(var s:FRAME){
            float t=s.t,hit=s.hit();if(s.blow==null||t<hit-.01f||t>=hit+.45f)continue;
            float in=Ease.smooth(t,hit-.01f,hit+.015f),size=SIZE[s.combo()];
            d.ring(s.blow.fist,s.blow.right,UP,.3+1.6*size*Ease.out(t,hit,hit+.25f),.7*size,.7f*in*(1-Ease.unit(t,hit,hit+.25f)));
            if(s.combo()==4)for(int k=1;k<3;k++){float at=hit+.04f*k;if(t>=at&&t<at+.3f)d.ring(s.blow.fist.add(s.blow.way.multiply(.4+1.5*k+1.8*Ease.out(t,at,at+.32f))),s.blow.right,UP,.45+(1.2+.6*k)*Ease.out(t,at,at+.26f),.7,.55f*(1-Ease.unit(t,at,at+.3f)));}
        }
    }
    /** A few chunks of the floor kicked up under the finisher's target. */
    public static void solids(FxSolids solids){
        for(var s:FRAME)if(s.combo()==4&&s.blow!=null&&s.t>=s.hit()&&s.t<s.hit()+.9f&&s.seen.landed&&s.seen.struck.length>0)
            FxLib.chunks(solids,ground(s.blow.fist,s.blow.feet.y).add(s.blow.way.multiply(.6)),s.t-s.hit(),7,977,1.4,5.5,.15,.45f);
    }
    /** A white flash over the whole screen for the finisher's user and whoever stands close as it lands. */
    public static float flash(Vec3d camera){
        float best=0;
        for(var s:FRAME)if(s.combo()==4&&s.blow!=null){float near=(float)Math.max(0,Math.min(1,(14-camera.distanceTo(s.blow.fist))/8));best=Math.max(best,.22f*near*kick(s.t,s.hit(),.05f));}
        return best;
    }
    /** The camera shakes as each blow lands (the finisher hardest), for its user, whom it struck and those close by: {yaw, pitch, roll} degrees. */
    public static float[] shake(Vec3d camera,float tickDelta){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null)return null;
        float amount=0;double time=mc.world.getTime()+tickDelta;
        for(var e:StandAttack.all().entrySet())for(var seen:e.getValue()){
            int combo=seen.punch.combo();float t=seen.age(tickDelta),hit=StandPunch.HIT[combo];
            if(t<hit-.03f||t>hit+.6f||!Float.isNaN(seen.punch.stopped())&&seen.punch.stopped()<hit)continue;
            boolean mine=e.getKey().equals(mc.player.getUuid()),struck=false;
            for(int id:seen.struck)if(id==mc.player.getId())struck=true;
            var user=mc.world.getPlayerByUuid(e.getKey());if(user==null)continue;
            float reach=mine||struck?1:(float)Math.max(0,1-user.getPos().distanceTo(camera)/18);if(reach<=0)continue;
            float strength=(combo==4?1.5f:combo==3?.7f:.38f)*(struck?1.4f:1);
            amount=Math.max(amount,strength*reach*kick(t,hit,combo==4?.13f:.08f));
        }
        if(amount<=.001f)return null;
        amount*=(float)CelestialFall.CONFIG.screenShakeStrength;
        return new float[]{amount*(float)(Math.sin(time*2.9)*.7+Math.sin(time*5.3)*.3),amount*(float)(Math.cos(time*3.7)*.7+Math.sin(time*6.1)*.3),amount*.35f*(float)Math.sin(time*4.4)};
    }
}
