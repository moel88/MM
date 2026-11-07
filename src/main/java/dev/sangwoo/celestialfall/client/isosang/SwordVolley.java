package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * H's basic attack, 회풍만검 (2.95.0, user request: "이번에는 평타 만들자 영상 처럼 등 뒤에 검 6개가 나와서 바라보고 있는
 * 상대에게 날아감", the user's video of it (5f78fd72, 60 fps) read on a sheet and confirmed: "맞음, 이대로"), drawn on every
 * client from the server's word of it (a swing of kind IsosangRules.VOLLEY: whom it comes down on, or where): six swords
 * of sky-blue light (2.95.1, user request "검이 하늘색이여야 하고 지금 검 모델링이 너무 안 좋아 영상이랑 똑같이 만들어봐":
 * their own model as the video's, VolleyBlade; 2.95.0 drew the sword in her hand see-through) show one after
 * another behind her back out of a light there, the middle ones first, and fan out like a peacock's tail, hilts at her
 * upper back, points out from her left through overhead to her right; then one after another each is thrown on out the
 * way it points, wheels round through the air and comes down on the foe from its own side (those from high in the fan
 * from high above, those from its edges from the sides and beyond), sticks in a moment and strikes in a burst of light,
 * and is gone. Her body stands on in the formation's stance (the video: hardly a move). Every pose is a continuous
 * function of the swing's clock and of where the foe is drawn (the swords follow it).
 */
final class SwordVolley {
    private SwordVolley(){}
    /** The length from a sword's grip to its point (blocks; 2.95.1: its own model, VolleyBlade, 1.6 blocks pommel to point, the video's each nearly as long as she is tall). */
    static final double LEN=VolleyBlade.TIP;
    /** Each sword's angle in the fan from straight up (degrees, to her right positive), in the order they show: the middle first, then out to either side. */
    static final double[] FAN={16,-16,46,-46,76,-76};
    /**
     * Where their hilts meet (blocks over her feet and behind her: the small of her back, where the video's fan spreads
     * from), how far out of it each comes (its grip), and how far the fan leans back.
     */
    static final double HILT_UP=1.02,HILT_BACK=.3,OUT=.3,LEAN=.06;
    /** How long a sword takes to be gone in light once it has struck (seconds). */
    static final float GONE=.12f;
    /** How long a volley is drawn for (seconds, IsosangClient.life): until its last sword's burst of light is out. */
    static final float LIFE=volleyStrikes(VOLLEY_SWORDS-1)+.45f;
    /**
     * How much of volley {@code id} still shows {@code t} seconds into it: all of it, or, called off (its user gone: the
     * server's word, IsosangClient.volleyEnd), fading out over a tenth of a second from then.
     */
    static float kept(UUID id,float t){float end=IsosangClient.volleyEnd(id);return Float.isNaN(end)?1:1-Ease.smooth(t,end,end+.1f);}
    /** Whether sword {@code i} of volley {@code id} struck (or will): not one whose blow was due after it was called off. */
    static boolean strikes(UUID id,int i){float end=IsosangClient.volleyEnd(id);return Float.isNaN(end)||volleyStrikes(i)<=end;}

    // ───────────────────────────── where the swords are ─────────────────────────────

    /** Where the swords' hilts meet: her upper back, for her feet at {@code feet} and facing {@code yaw}. */
    static Vec3d hilts(Vec3d feet,float yaw){return feet.add(0,HILT_UP,0).subtract(Vec3d.fromPolar(0,yaw).multiply(HILT_BACK));}
    /** The way sword {@code i} points out of her back in the fan (unit): from straight up toward her right by FAN[i], leaning back. */
    static Vec3d fan(float yaw,int i){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);double a=Math.toRadians(FAN[i]);
        return UP.multiply(Math.cos(a)).add(right.multiply(Math.sin(a))).subtract(f.multiply(LEAN)).normalize();
    }
    /**
     * The side sword {@code i} comes down on the foe from (unit, from the foe out): those high in the fan from high above,
     * those at its edges from the sides and round beyond (the video: from above and both sides), each on its own side of
     * the line from her ({@code from}) to the foe.
     */
    static Vec3d approach(Vec3d from,float yaw,Vec3d target,int i){
        Vec3d h=target.subtract(from).multiply(1,0,1);h=h.lengthSquared()<1e-6?Vec3d.fromPolar(0,yaw):h.normalize();
        Vec3d right=new Vec3d(-h.z,0,h.x);double k=Math.abs(FAN[i]),side=Math.signum(FAN[i]);
        double el=Math.toRadians(k<30?68:k<60?40:20),az=Math.toRadians(k<30?30:k<60?80:122)*side;
        Vec3d flat=h.multiply(-Math.cos(az)).add(right.multiply(Math.sin(az)));
        return UP.multiply(Math.sin(el)).add(flat.multiply(Math.cos(el))).normalize();
    }
    /** A sword at a moment: its grip, the way its blade points (unit, grip to point), how big (0 to 1), how plain, and what it does (0 in the fan, 1 flying, 2 stuck in). */
    record Pose(Vec3d grip,Vec3d dir,double size,float alpha,int phase){Vec3d tip(){return grip.add(dir.multiply(LEN*size));}}
    /**
     * Sword {@code i} of a volley begun with its user's feet at {@code from} facing {@code yaw}, {@code t} seconds in, the
     * foe (or where they come down) at {@code target}; null when it is not drawn. In the fan it slides out of the light at
     * her back growing to its size (IsosangRules.volleyShows), a breath of a hover; thrown (volleyThrown) its point flies
     * a curve out the way it pointed (at rest at first, quicker and quicker) and round onto the foe from its side
     * ({@link #approach}), sticking in VOLLEY_STICK seconds before it strikes, then gone in light.
     */
    static Pose pose(Vec3d from,float yaw,Vec3d target,float t,int i){
        float show=volleyShows(i),thrown=volleyThrown(i),arrive=volleyArrives(i),strike=volleyStrikes(i);
        if(t<show||t>=strike+GONE)return null;
        Vec3d d=fan(yaw,i),h=hilts(from,yaw);
        if(t<thrown){
            float grow=Ease.out(t,show,show+.09f);
            double hover=.025*Math.sin((t-show)*17+i*1.7)*(1-Ease.smooth(t,thrown-.08f,thrown));
            return new Pose(h.add(d.multiply(.08+OUT*grow+hover)),d,.6+.4*grow,Ease.smooth(t,show,show+.05f),0);
        }
        Vec3d[] c=curve(from,yaw,target,i);
        if(t<arrive){
            double s=(t-thrown)/(arrive-thrown),u=Math.pow(s,1.2);
            Vec3d tip=bezier(c,u),way=slope(c,u);Vec3d dir=way.lengthSquared()<1e-8?d:way.normalize();
            return new Pose(tip.subtract(dir.multiply(LEN)),dir,1,1,1);
        }
        // stuck in, its point a little in, then gone in light
        Vec3d dir=c[3].subtract(c[2]).normalize();
        double quiver=.03*Math.sin((t-arrive)*90)*(1-Ease.unit(t,arrive,strike));
        Vec3d tip=c[3].add(dir.multiply(quiver));
        return new Pose(tip.subtract(dir.multiply(LEN)),dir,1,t<strike?1:1-Ease.smooth(t,strike,strike+GONE),2);
    }
    /**
     * Sword {@code i}'s way as a cubic curve: from its point where it was thrown, out along the way it pointed (so it
     * leaves the fan the way it lay), round to come down along its {@link #approach}, its point reaching a little into the
     * foe; wider for a foe further off.
     */
    static Vec3d[] curve(Vec3d from,float yaw,Vec3d target,int i){
        Vec3d d=fan(yaw,i),p0=hilts(from,yaw).add(d.multiply(.08+OUT+LEN));
        double far=target.distanceTo(p0);Vec3d a=approach(from,yaw,target,i);
        return new Vec3d[]{p0,p0.add(d.multiply(2.2+.22*far)),target.add(a.multiply(1.8+.2*far)),target.subtract(a.multiply(.25))};
    }
    static Vec3d bezier(Vec3d[] c,double u){double v=1-u;return c[0].multiply(v*v*v).add(c[1].multiply(3*v*v*u)).add(c[2].multiply(3*v*u*u)).add(c[3].multiply(u*u*u));}
    static Vec3d slope(Vec3d[] c,double u){double v=1-u;return c[1].subtract(c[0]).multiply(3*v*v).add(c[2].subtract(c[1]).multiply(6*v*u)).add(c[3].subtract(c[2]).multiply(3*u*u));}

    // ───────────────────────────── whom they come down on ─────────────────────────────

    /** Where each volley's foe was last drawn (by its swing's id): where the rest come down once it is gone. */
    private static final Map<UUID,Vec3d> LAST=new LinkedHashMap<>();
    /**
     * Where swing {@code cast}'s swords come down now: its foe's chest as drawn while it is there (and its swords not all
     * struck yet, and it no further than VOLLEY_LEASH beyond the reach: 2.95.2, as the server follows it), else where it
     * was last drawn, else where the server said (no foe: where the crosshair met).
     */
    static Vec3d target(StandClient.Cast cast,float delta){
        var v=IsosangClient.volley(cast.id());if(v==null)return null;
        var mc=MinecraftClient.getInstance();Vec3d last=LAST.get(cast.id());
        if(v.target()>=0&&mc.world!=null&&mc.world.getEntityById(v.target()) instanceof LivingEntity e&&!e.isRemoved()&&cast.age(delta)/20f<volleyStrikes(VOLLEY_SWORDS-1)
            &&(v.centre()==null||leashed(v.centre(),e))){
            Vec3d at=e.getLerpedPos(delta).add(0,e.getHeight()*.55,0);
            LAST.put(cast.id(),at);while(LAST.size()>32)LAST.remove(LAST.keySet().iterator().next());
            return at;
        }
        return last!=null?last:v.point();
    }
    /** Whether the foe {@code e} is still followed (2.95.2): within VOLLEY_LEASH beyond the reach of the formation whose middle is {@code centre}. */
    static boolean leashed(Vec3d centre,LivingEntity e){Vec3d d=e.getPos().subtract(centre);return volleyIn(d.x,d.z,d.y,e.getWidth()*.5,VOLLEY_LEASH);}
    /**
     * Whom the local player's crosshair looks at now on their formation's sword, and where it meets (2.95.0, sent with the
     * attack: IsosangClient.punch): the body nearest the middle of the camera's view within VOLLEY_AIM, within the reach
     * (2.95.2: the formation and 3 blocks more, IsosangRules.volleyIn; until 2.95.1 VOLLEY_RANGE blocks of their eyes) and
     * in their sight; and where the camera's middle line meets a block, or VOLLEY_RANGE blocks on (the server brings that
     * in to the reach's edge). Null when they are not on their formation's sword (the attack is the sword's combo then).
     */
    static Aim aim(MinecraftClient mc){
        var me=mc.player;if(me==null||mc.world==null||mc.gameRenderer==null||!MapClient.enabled())return null;
        var own=IsosangClient.ownFormation();if(own==null||IsosangClient.stopped(own.id()))return null;
        if(!volleyTime(own.age(mc.getTickDelta())/20.0))return null;
        var form=IsosangClient.formation(own.id());if(form==null)return null;Vec3d mid=form.centre();
        var cam=mc.gameRenderer.getCamera();Vec3d eye=me.getEyePos(),look=Vec3d.fromPolar(cam.getPitch(),cam.getYaw()),c=cam.getPos();
        LivingEntity best=null;double near=VOLLEY_AIM,out=FORMATION_RADIUS+VOLLEY_BEYOND+2;
        var room=new net.minecraft.util.math.Box(mid.x-out,mid.y+FORMATION_LOW-VOLLEY_BEYOND-2,mid.z-out,mid.x+out,mid.y+FORMATION_HIGH+VOLLEY_BEYOND+2,mid.z+out);
        for(var e:mc.world.getEntitiesByClass(LivingEntity.class,room,e->e!=me&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(mid);if(!volleyIn(d.x,d.z,d.y,e.getWidth()*.5,0))continue;
            Vec3d chest=e.getPos().add(0,e.getHeight()*.55,0);
            // the nearer of its chest, head and feet to the line of sight (a big body is looked at anywhere on it)
            double off=Math.PI;
            for(double up:new double[]{.15,.55,.9}){Vec3d p=e.getPos().add(0,e.getHeight()*up,0).subtract(c);double l=p.length();if(l<1e-4)continue;off=Math.min(off,Math.acos(Math.max(-1,Math.min(1,p.dotProduct(look)/l))));}
            if(off>=near)continue;
            if(mc.world.raycast(new RaycastContext(eye,chest,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,me)).getType()!=HitResult.Type.MISS)continue;
            if(e instanceof PlayerEntity other&&!me.shouldDamagePlayer(other))continue;
            near=off;best=e;
        }
        double reach=VOLLEY_RANGE+c.distanceTo(eye);Vec3d end=c.add(look.multiply(reach));
        var block=mc.world.raycast(new RaycastContext(c,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,me));
        Vec3d meets=block.getType()!=HitResult.Type.MISS?block.getPos():end;
        return new Aim(best==null?-1:best.getId(),best==null?meets:best.getPos().add(0,best.getHeight()*.55,0));
    }
    /** Whom the crosshair looks at (an entity id, -1 for none) and where it meets. */
    record Aim(int target,Vec3d point){}

    // ───────────────────────────── the swords themselves ─────────────────────────────

    /**
     * The way each sword's flat faces (2.95.1): away from her back, where the camera behind her is, so the fan shows the
     * broad sides of its blades and the points of its guards as the video's does (and the swords stuck in the foe show
     * her theirs).
     */
    static Vec3d flat(float yaw){return Vec3d.fromPolar(0,yaw).multiply(-1);}

    // ───────────────────────────── their light ─────────────────────────────

    /**
     * The light (the effect layer): the light at her back they come out of, each one's flash as it shows, a white-hot line
     * down each blade and its blue glow, each one's streak behind it as it flies, a flash at its point as it reaches the
     * foe, and a burst on the foe as each strikes (IsosangEmpower.burst: blue spikes, a crescent moon, a ring, sparks).
     */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec,k=kept(c.cast.id(),t);Vec3d from=c.cast.origin();float yaw=c.cast.yaw();Vec3d target=target(c.cast,c.delta);if(target==null)return;
        Vec3d h=hilts(from,yaw);
        if(k>.01f){
            // a soft light at the small of her back as they come out of it (no flare: the video's swords simply are)
            float back=k*Ease.smooth(t,.02f,.07f)*(1-Ease.smooth(t,volleyThrown(VOLLEY_SWORDS-1)-.04f,volleyThrown(VOLLEY_SWORDS-1)+.1f));
            if(back>.01f){Vec3d near=facing(h,c.camera);m.softDisc(near,.35,BLUE,.14f*back);m.softDisc(near,.12,PALE,.25f*back);}
            for(int i=0;i<VOLLEY_SWORDS;i++){
                float show=volleyShows(i),arr=volleyArrives(i);
                if(t>=show-.01f&&t<show+.12f){
                    float a=k*Ease.smooth(t,show-.01f,show+.01f)*(1-Ease.unit(t,show,show+.12f));Vec3d o=facing(h.add(fan(yaw,i).multiply(.4)),c.camera);
                    m.softDisc(o,.3,AZURE,.35f*a);m.softDisc(o,.1,WHITE,.6f*a);
                }
                var p=pose(from,yaw,target,t,i);if(p==null)continue;float pa=p.alpha()*k;
                // (2.95.1) its own light: its outline white, a sky-blue glow round it (its body is drawn in the blended pass, alpha)
                VolleyBlade.glow(m,p.grip(),p.dir(),flat(yaw),p.size(),pa);
                if(p.phase()==1){trail(m,from,yaw,target,t,i,k);m.softDisc(p.tip(),.16,PALE,.5f*k);}
                if(t>=arr-.01f&&t<arr+.1f){float a=k*Ease.smooth(t,arr-.01f,arr+.01f)*(1-Ease.unit(t,arr,arr+.1f));Vec3d o=facing(p.tip(),c.camera);m.softDisc(o,.3,AZURE,.4f*a);m.softDisc(o,.1,WHITE,.7f*a);}
            }
        }
        // each sword's burst on the foe as it strikes (one called off before its blow does not)
        for(int i=0;i<VOLLEY_SWORDS;i++){
            float at=volleyStrikes(i);if(t<at-.01f||t>=at+.3f||!strikes(c.cast.id(),i))continue;
            Vec3d[] w=curve(from,yaw,target,i);IsosangEmpower.burst(c,m,target,w[3].subtract(w[2]).normalize(),at,.4,i*11+3);
        }
    }
    /** The streak a flying sword leaves: its point's way over the last 0.14 s, white at its head, blue and fading behind. */
    static void trail(EffectMesh m,Vec3d from,float yaw,Vec3d target,float t,int i,float kept){
        float thrown=volleyThrown(i);Vec3d prev=null;int n=8;
        for(int k=0;k<=n;k++){
            float tk=t-.14f*k/n;if(tk<thrown)break;
            var p=pose(from,yaw,target,tk,i);if(p==null||p.phase()!=1)break;
            Vec3d q=p.tip();
            if(prev!=null){float a=1-(k-1f)/n;m.softLine(prev,q,.22*a+.05,AZURE,.35f*a*kept);m.line(prev,q,.04*a+.01,PALE,.7f*a*a*kept);}
            prev=q;
        }
    }
    /**
     * The swords themselves (2.95.1: their own model of sky-blue light, VolleyBlade, in the blended pass; 2.95.0 drew the
     * sword in her hand see-through), and the ink edge under each burst, as the sword's other blows' (IsosangEmpower.burstInk).
     */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec,k=kept(c.cast.id(),t);Vec3d from=c.cast.origin();float yaw=c.cast.yaw();Vec3d target=target(c.cast,c.delta);if(target==null)return;
        if(k>.01f)for(int i=0;i<VOLLEY_SWORDS;i++){
            var p=pose(from,yaw,target,t,i);if(p==null)continue;
            VolleyBlade.body(m,c.camera,p.grip(),p.dir(),flat(yaw),p.size(),.92f*p.alpha()*k);
        }
        for(int i=0;i<VOLLEY_SWORDS;i++){float at=volleyStrikes(i);if(t>=at-.01f&&t<at+.3f&&strikes(c.cast.id(),i))IsosangEmpower.burstInk(c,m,target,at,.4,i*11+3);}
    }
    /** A few stars along the fan while it is spread (the swords' own starlight). */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec,kept=kept(c.cast.id(),t);if(kept<=.01f)return;
        Vec3d from=c.cast.origin();float yaw=c.cast.yaw();Vec3d target=target(c.cast,c.delta);if(target==null)return;
        for(int i=0;i<VOLLEY_SWORDS;i++){
            var p=pose(from,yaw,target,t,i);if(p==null||p.phase()!=0)continue;
            for(int k=0;k<2;k++){int seed=i*31+k*7;Vec3d q=p.grip().add(p.dir().multiply(LEN*(.35+.5*Ease.hash(seed))));glint8(m,q,.07+.05*Ease.hash(seed+1),k==0?WHITE:LILAC,.7f*p.alpha()*kept*twinkle(t,seed));}
        }
    }
    /** Lights: her back while they fan out, each sword as it flies, the foe as each strikes; the afterglow cut short while they fly. */
    static void frame(Fx c,FxFrame f){
        float t=c.sec,kept=kept(c.cast.id(),t);Vec3d from=c.cast.origin();float yaw=c.cast.yaw();Vec3d target=target(c.cast,c.delta);if(target==null)return;
        float back=kept*Ease.smooth(t,.02f,.07f)*(1-Ease.smooth(t,volleyThrown(VOLLEY_SWORDS-1),volleyThrown(VOLLEY_SWORDS-1)+.12f));
        if(back>.01f)f.light(hilts(from,yaw),0x8cbcff,5,.8f*back);
        for(int i=0;i<VOLLEY_SWORDS;i++){
            var p=pose(from,yaw,target,t,i);if(p!=null&&p.phase()==1&&kept>.01f)f.light(p.tip(),0x9fc8ff,3,.45f*kept);
            float k=strikes(c.cast.id(),i)?FxLib.kick(t,volleyStrikes(i),.1f):0;if(k>.01f)f.light(target,0xcfe4ff,4,.75f*k);
        }
        // quick as they fly: short afterglow, else each sword leaves copies of itself along its way
        if(t>=volleyThrown(0)-.02f&&t<volleyStrikes(VOLLEY_SWORDS-1)+.15f)f.afterglow(.06f);
    }
    /** A little shake as each strikes (each over two frames). */
    static float shake(StandClient.Cast cast,float sec){
        float v=0;for(int i=0;i<VOLLEY_SWORDS;i++)if(strikes(cast.id(),i))v=Math.max(v,.25f*bump(sec,volleyStrikes(i),.06f));
        return v;
    }
    static void clear(){LAST.clear();}
}
