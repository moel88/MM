package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * Lee So-sang's R as drawn (2.81.0, user request: "이번에는 R스킬 거대한 검을 소환해서 전방을 크게 베는거야", and the user's
 * videos of it from above and from behind at a tenth of the speed, read frame by frame on a sheet the user answered). The
 * sword is raised, a diamond of light flashes in the air over the ground ahead and a lance of white light shoots out of it
 * to the right and back, turning into a giant sword of blue light (the empowered fourth's, SpiritSword, 6/3.5 times its
 * size), its pommel high ahead, its blade out to the right and back; Lee So-sang springs up and hangs in the air, arms
 * spread, as it hangs there, a star twinkling near its point; then it sweeps round its pommel, level, across the front to
 * the left and on behind it (IsosangRules.sweepAngle, the server's clock), her own sword swung with it as she drops into a
 * crouch: a great crescent of light trails its blade (white at its edge, turning to ink from where it began), a pale fan
 * of light fills what it has just swept, streaks of light fly off, ink splashes over the ground it swept and a ring of ink
 * is laid under its point, the ground darkens under it, and whom it passes is struck where it reaches them. The sword
 * fades into ink as it stops, the crescent lingers a moment and dries, the ink soaks in, and she rises.
 * <p>Its sweep stands where the server put it (IsosangClient.sweep: out toward the foe in the crosshair, or right in front
 * of its user), the body where its user is. Every effect is a function of the swing's clock, so it moves at the render
 * frame rate. (2.102.0) Its moonlight and starlight are SweepLights' (its stars before it is called, the lightning as it
 * hangs, the moon and the trace of its point, sparks, petals, the impact frame and the rings as it crosses the front,
 * stars in its ink).
 */
final class GiantSweep {
    private GiantSweep(){}

    // ───────────────────────────── the body ─────────────────────────────
    // (the video from behind: the sword raised out to the right and up as R begins; a little spring off the ground as the
    // giant sword forms, hanging in the air with the arms spread, the sword out to the right and back as the giant one is;
    // drawn back, then swung round with the giant sword from the right across the front to the left, dropping into a low
    // crouch, the sword out low at the left; held, and up. The body faces the front, twisting at most 30°, and the blade
    // trails the arm the way it cuts, never crossing its line: IsosangSlash's rules for the sword.)
    /** The sword raised out to the right, glowing, a little dip to spring. */
    static final R6.Pose R_LIFT=w(.02,-.04,-10,2,0,d(25,85),d(55,68),d(-75,-20),20,-160);
    /** Down for the spring, the sword up. */
    static final R6.Pose R_PUSH=w(.04,-.13,-8,7,0,d(55,62),d(80,40),d(-42,-72),26,-154);
    /** Off the ground, the sword held up at the right, the free arm out to the left. */
    static final R6.Pose R_UP=w(.04,.3,-6,-4,0,d(58,72),d(74,104),d(8,-96),d(-80,8),d(-84,-10));
    /** Hanging in the air, the arms spread: the sword out to the right and back, as the giant sword is. */
    static final R6.Pose R_SPREAD=w(.03,.42,-4,-6,0,d(15,100),d(15,125),d(12,-100),d(-80,10),d(-82,-12));
    /** Drawn back to the right as the giant sword is about to go, the free hand reaching ahead to the left. */
    static final R6.Pose R_COCK=w(.03,.4,-26,0,-2,d(8,128),d(5,150),d(0,-55),d(-76,14),d(-85,-8));
    /** Swung round with it: the right, the front. */
    static final R6.Pose R_SWEEP1=w(.05,.3,-12,4,0,d(2,70),d(0,92),d(-10,-100),d(-80,10),d(-81,-8));
    static final R6.Pose R_SWEEP2=w(.08,.12,8,8,2,d(-5,0),d(-5,18),d(-15,-135),d(-82,12),d(-84,-14));
    /** Down in a low crouch, the sword out low at the left, the free arm back. */
    static final R6.Pose R_LAND=w(.1,-.3,26,16,4,d(-12,-78),d(-14,-60),d(-30,-150),40,-150);
    static final R6.Pose R_HOLD=w(.1,-.32,25,15,4,d(-15,-82),d(-16,-64),d(-32,-152),40,-150);
    /** Up again, the sword coming round in front. */
    static final R6.Pose R_RISE=w(.06,-.1,8,4,0,d(-40,-20),d(10,-5),d(-70,-40),25,-160);
    static final float LENGTH=IsosangRules.LENGTH[IsosangRules.SWEEP]/20f;
    static final Anim CLIP=Anim.joints(
        k(0,STANCE),
        k(.07,R_LIFT),
        k(.13,R_PUSH),
        k(.24,R_UP,Anim.Ease.OUT),
        k(.36,R_SPREAD),
        k(.45,R_COCK),
        k(.52,R_SWEEP1,Anim.Ease.IN),
        k(.58,R_SWEEP2,Anim.Ease.LINEAR),
        k(.64,R_LAND,Anim.Ease.OUT),
        k(.88,R_HOLD),
        k(1.02,R_RISE),
        k(LENGTH,STANCE))
        .stops(Anim.stop(IsosangRules.SWEEP_FRONT,.03,.05)).after(IsosangSlash::grounded);

    // ───────────────────────────── where it stands ─────────────────────────────

    /** Where R's sweep stands (its feet) and the way it faces: the server's word (IsosangClient.sweep), else where its caster stood. */
    static Vec3d feet(StandClient.Cast c){var s=IsosangClient.sweep(c.id());return s==null?c.origin():s.at();}
    static float yaw(StandClient.Cast c){var s=IsosangClient.sweep(c.id());return s==null?c.yaw():s.yaw();}
    /** The sword's pommel, which it turns round (world). */
    static Vec3d pivot(StandClient.Cast c){return world(feet(c),yaw(c),v(IsosangRules.SWEEP_PIVOT_FW,IsosangRules.SWEEP_PIVOT_UP,0));}
    /** The giant sword's size: SpiritSword's model, from its pommel's point to its blade's, as long as the sweep's reach. */
    static final double SCALE=IsosangRules.SWEEP_REACH/SpiritSword.LENGTH;
    /** How far its blade leans down from its pommel (degrees): its point at a head's height. */
    static final double TILT=3;
    /** The way its blade points at angle {@code deg} off the sweep's front (right +), leaning down TILT. */
    static Vec3d dir(float yaw,double deg){
        double a=Math.toRadians(deg),t=Math.toRadians(TILT);
        Vec3d h=IsosangSlash.dir(yaw,v(Math.cos(a),0,Math.sin(a)));
        return h.multiply(Math.cos(t)).add(0,-Math.sin(t),0).normalize();
    }
    /** The way its blade moves at angle {@code deg} (level): round to the left of it, as it sweeps. */
    static Vec3d moving(float yaw,double deg){double a=Math.toRadians(deg-90);return IsosangSlash.dir(yaw,v(Math.cos(a),0,Math.sin(a)));}
    /** The ground under where the sweep stands (its feet may be in the air with a foe who jumped), held per swing. */
    private static final Map<UUID,Double> FLOORS=new LinkedHashMap<>();
    static double floor(StandClient.Cast c){
        var f=FLOORS.get(c.id());if(f!=null)return f;
        var mc=MinecraftClient.getInstance();Vec3d at=feet(c);double y=at.y;
        if(mc.world!=null&&mc.player!=null){
            var hit=mc.world.raycast(new RaycastContext(at.add(0,.4,0),at.add(0,-8,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS)y=hit.getPos().y;
        }
        FLOORS.put(c.id(),y);while(FLOORS.size()>32)FLOORS.remove(FLOORS.keySet().iterator().next());
        return y;
    }
    /** The sweep's feet on the ground under it. */
    static Vec3d ground(StandClient.Cast c){Vec3d f=feet(c);return new Vec3d(f.x,floor(c),f.z);}

    // ───────────────────────────── the giant sword ─────────────────────────────

    /** When the diamond flashes, the lance of light shoots out, the sword forms, and it fades into ink once it has swept (seconds into R). */
    static final float FLASH=.14f,LANCE0=.15f,LANCE1=.22f,FORM0=.17f,FORM1=.3f,FADE0=.74f,FADE1=.84f;
    /** The giant sword as drawn: the middle of its grip, the way it points, how strongly it shows, how white-hot and how inked. */
    record Giant(Vec3d grip,Vec3d dir,float alpha,float heat,float ink){
        Vec3d at(double along){return grip.add(dir.multiply(along*SCALE));}
        Vec3d point(){return at(SpiritSword.TIP);}
    }
    /**
     * The blade's angle at {@code t} (degrees off the sweep's front, right +): the server's sweep, drawn a few degrees
     * further back as it hangs just before it goes (the video), back on the server's way as it turns.
     */
    static double angle(float t){
        return IsosangRules.sweepAngle(t)+6*Ease.smooth(t,.3f,.44f)*(1-Ease.smooth(t,(float)IsosangRules.SWEEP_START-.005f,(float)IsosangRules.SWEEP_START+.03f));
    }
    /** The giant sword at R's second {@code t} (null before it forms and once it is gone). */
    static Giant giant(StandClient.Cast c,float t){
        if(t<FORM0||t>=FADE1)return null;
        float alpha=Ease.smooth(t,FORM0,FORM1)*(1-Ease.smooth(t,FADE0,FADE1));if(alpha<=.005f)return null;
        float heat=.2f+.8f*(1-Ease.smooth(t,FORM0+.02f,FORM1+.05f)),ink=Ease.smooth(t,FADE0-.04f,FADE1);
        Vec3d d=dir(yaw(c),angle(t));
        // (it hangs, bobbing a little, until it goes)
        float bob=Ease.smooth(t,FORM1,FORM1+.06f)*(1-Ease.smooth(t,(float)IsosangRules.SWEEP_START-.04f,(float)IsosangRules.SWEEP_START));
        Vec3d p=pivot(c).add(0,.035*Math.sin(t*9)*bob,0);
        return new Giant(p.add(d.multiply(-SpiritSword.POMMEL*SCALE)),d,alpha,heat,ink);
    }

    // ───────────────────────────── its crescent, its ring of ink ─────────────────────────────

    /** The crescent its blade leaves (in the sweep's frame, round the pommel, level a little under it, from the guard out past the point). */
    static final double RIBBON_IN=.45*IsosangRules.SWEEP_REACH,RIBBON_OUT=IsosangRules.SWEEP_REACH+.3,RIBBON_UP=IsosangRules.SWEEP_PIVOT_UP-.45;
    /**
     * The crescent: swept on with the blade (pace 3, the server's sweep), thin where it began and thick at its head (style
     * 0), white along its cutting edge, turning to ink a third of a second after the blade passed (the video from above:
     * still bright blue 0.2 s after it stopped, dark by 1.02 s) and eaten away from its tail by 1.3 s.
     */
    static final Arc ARC=new Arc(v(IsosangRules.SWEEP_PIVOT_FW,RIBBON_UP,0),v(1,0,0),v(0,0,1),RIBBON_IN,RIBBON_OUT,IsosangRules.SWEEP_FROM,IsosangRules.SWEEP_TO,
        (float)IsosangRules.SWEEP_START,(float)IsosangRules.SWEEP_END,1.05f,1.3f,.8f,1.05f,0,23,3);
    /** The ring of ink laid on the ground under its point as it passes (the video from above: a dark ring line), eaten by 1.4 s. */
    static final Loop RING=new Loop(v(IsosangRules.SWEEP_PIVOT_FW,.03,0),0,0,IsosangRules.SWEEP_REACH+.15,1,.12,IsosangRules.SWEEP_FROM,IsosangRules.SWEEP_TO,(float)IsosangRules.SWEEP_START,(float)IsosangRules.SWEEP_END,1.05f,1.4f,17);
    /** When (seconds into R) the blade passes the angle {@code deg} (right +). */
    static float passes(double deg){
        double lo=IsosangRules.SWEEP_START,hi=IsosangRules.SWEEP_END;
        if(deg>=IsosangRules.SWEEP_FROM)return (float)lo;
        if(deg<=IsosangRules.SWEEP_TO)return (float)hi;
        for(int i=0;i<30;i++){double mid=(lo+hi)*.5;if(IsosangRules.sweepAngle(mid)>deg)lo=mid;else hi=mid;}
        return (float)((lo+hi)*.5);
    }

    // ───────────────────────────── whom it struck ─────────────────────────────

    /**
     * Whom the sweep struck (the server's word, told tick by tick as it strikes), and until it is over those in its way the
     * server has not told of yet, as the server finds them.
     */
    static List<LivingEntity> struck(Fx c){
        var out=new ArrayList<LivingEntity>();var world=c.mc.world;if(world==null)return out;
        var told=IsosangClient.struck(c.cast.id(),0);
        if(told!=null)for(int id:told)if(world.getEntityById(id) instanceof LivingEntity e)out.add(e);
        if(c.sec>IsosangRules.SWEEP_END+.15)return out;
        Vec3d feet=feet(c.cast);float yaw=yaw(c.cast);Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(feet,feet).expand(IsosangRules.SWEEP_PIVOT_FW+IsosangRules.SWEEP_REACH+2),e->e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!e.getUuid().equals(c.cast.caster())&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            if(out.contains(e))continue;
            Vec3d o=e.getPos().subtract(feet);
            if(IsosangRules.sweepReaches(o.dotProduct(f),o.dotProduct(right),o.y,e.getWidth()*.5)>=0)out.add(e);
            if(out.size()>=8)break;
        }
        return out;
    }
    /** When (seconds into R) the blade reached {@code e}: where they stood as it struck them (the server's word), else where they are. */
    static float struckAt(Fx c,LivingEntity e){
        Vec3d pos=e.getLerpedPos(c.delta);for(var tg:c.cast.targets())if(tg.entity()==e.getId()){pos=tg.position();break;}
        Vec3d feet=feet(c.cast);float yaw=yaw(c.cast);Vec3d o=pos.subtract(feet),f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        double at=IsosangRules.sweepReaches(o.dotProduct(f),o.dotProduct(right),Math.max(0,Math.min(1,o.y)),e.getWidth()*.5);
        return at<0?(float)IsosangRules.SWEEP_FRONT:(float)at;
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** The ink: the ground's ink first (the darkened ground, the splashes, the ring under its point, round whom it struck), then its crescent. */
    static void ink(Fx c){
        float t=c.sec;Vec3d feet=feet(c.cast),ground=ground(c.cast);float yaw=yaw(c.cast);double floor=ground.y+.02;
        // the ground it swept darkens while the ink lies on it (the video from above: nearly black over the whole front,
        // light showing through in patches, for some two seconds)
        float dk=t-(float)IsosangRules.SWEEP_START-.05f;
        if(dk>=0&&dk<2.1f){
            // (over what it swept, ahead: its middle out in front of the pommel, not round them)
            Vec3d o=world(ground,yaw,v(IsosangRules.SWEEP_PIVOT_FW+.3*IsosangRules.SWEEP_REACH,0,0));float dry=Ease.smooth(dk,1.2f,2.1f);
            stain(new Vec3d(o.x,floor-.004,o.z),1.9*IsosangRules.SWEEP_REACH*(.6+.4*Ease.out(dk,0,.2f)),Math.toRadians(-yaw),1,9,dry,.55f*(1-Ease.smooth(dry,.6f,1)));
        }
        // splashes of ink over the ground it swept, each landing as the blade passes over it
        for(int k=0;k<24;k++){
            int seed=k*59+2003;
            double deg=IsosangRules.SWEEP_TO+(IsosangRules.SWEEP_FROM-IsosangRules.SWEEP_TO)*(k+.5+.6*(Ease.hash(seed)-.5))/24,r=1.6+(IsosangRules.SWEEP_REACH-1)*Math.sqrt(Ease.hash(seed+1));
            float born=passes(deg)+.05f+.12f*(float)Ease.hash(seed+2);if(t<born)continue;
            Vec3d q=world(ground,yaw,point(ARC,deg,r));
            splatFor(new Vec3d(q.x,floor+.002*(k%5),q.z),2.4+2.0*Ease.hash(seed+3),Math.PI*2*Ease.hash(seed+4),1+.6*Ease.hash(seed+5),seed,t-born,.88f);
        }
        // the great splash ahead as the blade crosses the front (the video from above: ink burst out there, spikes flung out)
        Vec3d q=world(ground,yaw,point(ARC,8,.73*IsosangRules.SWEEP_REACH));
        splatFor(new Vec3d(q.x,floor+.012,q.z),4.2,Math.toRadians(-yaw),1.25,31,t-(float)IsosangRules.SWEEP_FRONT-.03f,.92f);
        // the ring of ink under its point
        IsosangSlash.loop(ground,yaw,RING,t);
        // round whom it struck, where it reached them
        int b=0;
        for(var e:struck(c)){
            float hit=struckAt(c,e);b++;if(t<hit)continue;Vec3d o=e.getLerpedPos(c.delta);
            for(int k=0;k<3;k++){
                int seed=b*97+k*13+3001;double az=Math.PI*2*Ease.hash(seed),r=.4+.5*k;
                splatFor(new Vec3d(o.x+Math.cos(az)*r,floor+.006+.002*k,o.z+Math.sin(az)*r),1.6+1.1*Ease.hash(seed+3),az+1,1.1+.5*Ease.hash(seed+4),seed+5,t-hit-.06f-.05f*k,.88f);
            }
        }
        // the ink flung off the blade, where it lands
        for(var d:drops(c,t)){
            if(d.size()<STAIN_MIN)continue;
            double land=lands(d,floor+.01);if(land<0)continue;
            float age=t-d.born()-(float)land;if(age<0||age>=STAIN_LIFE)continue;
            Vec3d p=flight(d,land),mv=IsosangSlash.moving(d,land);
            stainAt(new Vec3d(p.x,floor+.001*(d.seed()%7),p.z),d.size()*4.6,Math.atan2(mv.z,mv.x),1+Math.min(1.4,Math.hypot(mv.x,mv.z)*.3),d.seed(),age,.78f);
            if(d.size()>.08)splatAt(new Vec3d(p.x,floor+.003+.001*(d.seed()%5),p.z),d.size()*9,Math.atan2(mv.z,mv.x),1+Math.min(1.2,Math.hypot(mv.x,mv.z)*.25),d.seed()+3,age,.8f);
        }
        // its crescent
        if(t<ARC.eat1()){
            var sw=band(feet,yaw,ARC,t);
            if(sw!=null)KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),ARC.style(),ARC.seed(),Ease.smooth(t,ARC.eat0(),ARC.eat1()),inks(ARC,sw,t),Ease.smooth(t,ARC.t0(),ARC.t0()+.015f)*shown(sw,c.camera));
        }
    }
    /**
     * How long the sweep's splashes of ink last (seconds; the video from above: the ground it swept stays splashed black well
     * after it, fading from about a second and a quarter, gone by some 2.2 s).
     */
    static final float SWEEP_SPLAT_LIFE=2.2f;
    /** A splash of the sweep's {@code age} seconds after the ink landed. */
    static void splatFor(Vec3d p,double size,double angle,double stretch,int seed,float age,float alpha){
        if(age<0||age>=SWEEP_SPLAT_LIFE)return;
        float dry=Ease.smooth(age,.55f*SWEEP_SPLAT_LIFE,SWEEP_SPLAT_LIFE);
        splat(p,size*(.55+.45*Ease.out(age,0,.07f)),angle,stretch,seed&15,dry,alpha*(1-Ease.smooth(dry,.35f,1)));
    }
    /**
     * The ink flung off the blade as it sweeps (drops and flakes from its outer part, thrown out and on round the way it
     * goes, falling), and splashed off whom it strikes.
     */
    static List<Drop> drops(Fx c,float t){
        List<Drop> out=new ArrayList<>();if(t<IsosangRules.SWEEP_START)return out;
        Vec3d feet=feet(c.cast);float yaw=yaw(c.cast);Vec3d centre=pivot(c.cast);
        for(int k=0;k<28;k++){
            int seed=k*67+4001;double f=(k+Ease.hash(seed))/28;
            double deg=IsosangRules.SWEEP_FROM+(IsosangRules.SWEEP_TO-IsosangRules.SWEEP_FROM)*f;float born=passes(deg)+.03f;if(t<born)continue;
            double r=RIBBON_IN+(RIBBON_OUT-RIBBON_IN)*(.2+.75*Ease.hash(seed+1));
            Vec3d at=world(feet,yaw,point(ARC,deg,r)),radial=at.subtract(centre).multiply(1,0,1);radial=radial.lengthSquared()<1e-8?UP:radial.normalize();
            double speed=1.4+2.4*Ease.hash(seed+2);
            Vec3d vel=radial.multiply(speed).add(moving(yaw,deg).multiply(speed*(.3+.8*Ease.hash(seed+3)))).add(0,.3+.7*Ease.hash(seed+4),0);
            int kind=k%3==2?1:k%5==3?2:0;
            out.add(new Drop(at,vel,3,3.2,born,.7f+.4f*(float)Ease.hash(seed+5),kind==0?.05+.06*Ease.hash(seed+6):.07+.08*Ease.hash(seed+6),seed,kind));
        }
        int b=0;
        for(var e:struck(c)){
            float at=struckAt(c,e);b++;if(t<at)continue;
            Vec3d p=IsosangEmpower.chestOf(c,e,at),way=moving(yaw,angle(at));
            Vec3d side=way.crossProduct(UP);side=side.lengthSquared()<1e-4?c.right:side.normalize();
            for(int k=0;k<14;k++){
                int seed=k*41+b*131+4507;double ang=(Ease.hash(seed+1)-.5)*2.6,rise=Ease.hash(seed+2)*1.3-.2;
                Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(0,rise,0).normalize();
                int kind=k%3==1?1:k%7==3?2:0;
                out.add(new Drop(p,dir.multiply(1.8+3*Ease.hash(seed+3)),4,2.8,at,.6f+.4f*(float)Ease.hash(seed),kind==0?.045+.065*Ease.hash(seed+4):.065+.08*Ease.hash(seed+4),seed,kind));
            }
        }
        return out;
    }
    /** The blended pass: the giant sword itself (its own model of blue light), the ink flung in the air, the ink round every burst. */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec;
        var g=giant(c.cast,t);
        if(g!=null)SpiritSword.body(m,c.camera,g.grip(),g.dir(),SCALE,g.alpha(),g.heat(),g.ink());
        double floor=floor(c.cast)+.03;
        for(var d:drops(c,t)){
            float age=t-d.born();if(age<0||age>=d.life())continue;
            double land=lands(d,floor);if(land>=0&&age>=land)continue;
            Vec3d q=flight(d,age),mv=IsosangSlash.moving(d,age);float u=age/d.life();
            float by=near(q,c.camera);if(by<=0)continue;
            double size=d.size()*(1-.45*u)*Ease.out(age,0,.06f)*(.35+.65*by);float a=.94f*(1-Ease.smooth(u,.6f,1))*by;
            switch(d.kind()){
                case 1->flake(m,q,size,age,1.2+2.0*Ease.hash(d.seed()+9),d.seed(),INK,a);
                case 2->blot(m,q,mv,size,d.seed(),INK,a);
                default->drop(m,q,mv,size,d.seed(),INK,a);
            }
        }
        int k=0;for(var e:struck(c)){float at=struckAt(c,e);IsosangEmpower.burstInk(c,m,IsosangEmpower.chestOf(c,e,at),at,.95,k++*13+7);}
        SweepLights.alpha(c,m);
    }
    /**
     * The light: her sword's aura; the diamond flash and the lance of light the giant sword forms out of; its own edges and
     * glow; the star near its point as it hangs; the crescent's cutting edge and the pale fan of what it has just swept;
     * the streaks of light; a burst on whom it strikes.
     */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;Vec3d feet=feet(c.cast);float yaw=yaw(c.cast);
        aura(c,m,t);
        flash(c,m,t);
        lance(c,m,t);
        var g=giant(c.cast,t);
        if(g!=null){SpiritSword.glow(m,g.grip(),g.dir(),SCALE,g.alpha(),g.heat(),g.ink());tipStar(m,t,g);}
        var sw=band(feet,yaw,ARC,t);
        if(sw!=null&&t<ARC.eat1())edgeGlow(m,sw,ARC.style(),edgeLit(ARC,t),inks(ARC,sw,t));
        fan(c,m,t,feet,yaw);
        streaks(m,t,feet,yaw);
        int k=0;for(var e:struck(c)){float at=struckAt(c,e);IsosangEmpower.burst(c,m,IsosangEmpower.chestOf(c,e,at),moving(yaw,angle(at)),at,.95,k++*13+7);}
        SweepLights.additive(c,m);
    }
    /** Over the ink: the starlight on the crescent and a glint racing at its head (2.102.0: smaller, a moon riding by it, SweepLights.tipMoon). */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec;Vec3d feet=feet(c.cast);float yaw=yaw(c.cast);
        for(var st:stars(ARC,t)){
            Vec3d p=world(feet,yaw,point(ARC,st.deg(),st.radius()));
            int col=st.night()?(st.seed()%3==0?LILAC:PALE):(st.seed()%3==0?WHITE:st.seed()%3==1?PALE:LILAC);
            glint8(m,p,st.size()*1.5,col,st.alpha());
        }
        if(t>=ARC.t0()&&t<ARC.t1()+.08f){
            float a=Ease.smooth(t,ARC.t0(),ARC.t0()+.015f)*(1-Ease.smooth(t,ARC.t1()+.01f,ARC.t1()+.08f));
            Vec3d p=world(feet,yaw,point(ARC,IsosangRules.sweepAngle(t),RIBBON_OUT));
            glint8(m,p,.34,WHITE,.85f*a);m.softDisc(p,.36,AZURE,.28f*a);
        }
        SweepLights.overlay(c,m);
    }
    /** Her own sword's blue aura: as it is raised to call the giant sword, and flaring as she swings it round with it. */
    static void aura(Fx c,EffectMesh m,float t){
        float g=Ease.window(t,.02f,.68f,.05f,.12f)+.8f*FxLib.kick(t,(float)IsosangRules.SWEEP_FRONT,.08f);if(g<=.01f)return;
        var hb=blade(c,IsosangRules.SWEEP,t);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));float on=Math.min(1,g);
        double w=.06*(1+.4*Math.max(0,Math.min(1,g-1)));float a=Math.min(1.1f,g);
        m.softLine(guard,tip,w*5,AURA[1],a*.12f);m.softLine(guard,tip,w*2.4,AURA[1],a*.45f);
        m.line(guard,tip,w,AURA[2],a*.85f);m.line(guard,tip,w*.45,AURA[3],a*.8f);
        m.softDisc(tip,.12+.08*on,AURA[3],.5f*on);
    }
    /** The diamond of light the giant sword shows out of (the videos: a flash high ahead of them, a moment before the lance). */
    static void flash(Fx c,EffectMesh m,float t){
        float k=FxLib.kick(t,FLASH,.07f);if(k<=.01f)return;
        Vec3d p=pivot(c.cast),d=dir(yaw(c.cast),angle(t));
        m.softDisc(p,1.3,BLUE,.32f*k);m.softDisc(p,.55,PALE,.5f*k);
        // a diamond: long along the blade's way, short across it
        Vec3d across=d.crossProduct(UP);across=across.lengthSquared()<1e-4?c.right:across.normalize();
        Vec3d a=p.add(d.multiply(1.05)),b=p.add(across.multiply(.42)),e=p.subtract(d.multiply(1.05)),f=p.subtract(across.multiply(.42));
        m.triangle(a,b,p,WHITE,.8f*k);m.triangle(b,e,p,WHITE,.8f*k);m.triangle(e,f,p,WHITE,.8f*k);m.triangle(f,a,p,WHITE,.8f*k);
        glint8(m,p,.95,WHITE,k);
    }
    /**
     * The lance of white light the giant sword forms out of (the videos: shot out from the flash to the right and back, white
     * hot, crosses of light along it, then the sword takes shape inside it).
     */
    static void lance(Fx c,EffectMesh m,float t){
        if(t<LANCE0-.01f||t>=FORM1+.06f)return;
        Vec3d p=pivot(c.cast),d=dir(yaw(c.cast),angle(t));double len=SpiritSword.LENGTH*SCALE;
        float grow=Ease.out(t,LANCE0,LANCE1),a=Ease.smooth(t,LANCE0-.01f,LANCE0+.01f)*(1-Ease.smooth(t,FORM1-.05f,FORM1+.06f));
        Vec3d head=p.add(d.multiply(len*grow));
        m.softLine(p,head,1.25,BLUE,.28f*a);m.softLine(p,head,.55,AZURE,.5f*a);m.line(p,head,.15,WHITE,.92f*a);
        m.star(head,.85,WHITE,a);m.softDisc(head,.6,PALE,.45f*a);
        for(int k=0;k<3;k++){double u=(.3+.24*k)*grow;m.star(p.add(d.multiply(len*u)),.55*twinkle(t,k*13+7),WHITE,.8f*a);}
    }
    /** The star near the giant sword's point as it hangs (the videos: a four-pointed star twinkling out there). */
    static void tipStar(EffectMesh m,float t,Giant g){
        float a=Ease.smooth(t,.24f,.3f)*(1-Ease.smooth(t,(float)IsosangRules.SWEEP_START,(float)IsosangRules.SWEEP_START+.05f))*g.alpha();if(a<=.01f)return;
        Vec3d q=g.at(SpiritSword.TIP*.82);float tw=.6f+.4f*twinkle(t,77);
        m.star(q,1.1*tw,WHITE,.8f*a);m.star(q,.55,PALE,.6f*a);m.softDisc(q,.6,AZURE,.25f*a);
    }
    /**
     * The pale fan of light over what the blade has just swept (the video from behind: a great see-through fan of light
     * behind it as it comes round): the last 0.06 s of its sweep, from by its pommel out to past its point, brightest out
     * there and at the blade.
     */
    static void fan(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        double s0=IsosangRules.SWEEP_START,s1=IsosangRules.SWEEP_END;if(t<s0||t>=s1+.1)return;
        float fade=1-Ease.smooth(t,(float)s1-.04f,(float)s1+.1f);
        double d1=IsosangRules.sweepAngle(t),d0=IsosangRules.sweepAngle(Math.max(s0,t-.06));if(d0-d1<1)return;
        int n=Math.max(4,(int)Math.ceil((d0-d1)/5));
        for(int i=0;i<n;i++){
            double a=d0+(d1-d0)*i/n,b=d0+(d1-d0)*(i+1)/n;float ua=i/(float)n,ub=(i+1)/(float)n;
            Vec3d ia=world(feet,yaw,point(ARC,a,.6)),oa=world(feet,yaw,point(ARC,a,RIBBON_OUT-.2)),ib=world(feet,yaw,point(ARC,b,.6)),ob=world(feet,yaw,point(ARC,b,RIBBON_OUT-.2));
            m.vertex(ia,PALE,0);m.vertex(ib,PALE,0);m.vertex(ob,CYAN,.3f*fade*ub*ub);m.vertex(oa,CYAN,.3f*fade*ua*ua);
        }
    }
    /** Streaks of light flying off as it sweeps (the videos: long thin lines of light scattered round the way it went, flying on). */
    static void streaks(EffectMesh m,float t,Vec3d feet,float yaw){
        double s0=IsosangRules.SWEEP_START;if(t<s0||t>=1.3f)return;
        for(int k=0;k<26;k++){
            int seed=k*53+1901;float born=(float)s0+.02f+.26f*(float)Ease.hash(seed),life=.3f+.18f*(float)Ease.hash(seed+1),age=t-born;
            if(age<0||age>=life)continue;
            double deg=IsosangRules.sweepAngle(born)+(Ease.hash(seed+2)-.5)*30,r=1.8+(IsosangRules.SWEEP_REACH-.8)*Ease.hash(seed+3),h=(Ease.hash(seed+4)-.4)*2.4;
            Vec3d o=world(feet,yaw,point(ARC,deg,r)).add(0,h,0);
            Vec3d out=world(feet,yaw,point(ARC,deg,r+1)).subtract(world(feet,yaw,point(ARC,deg,r))).normalize();
            Vec3d way=moving(yaw,deg).multiply(.8).add(out.multiply(.5)).add(0,(Ease.hash(seed+5)-.5)*.5,0).normalize();
            double len=1.1+1.6*Ease.hash(seed+6),go=(3+5*Ease.hash(seed+7))*age;
            Vec3d a=o.add(way.multiply(go)),b=a.add(way.multiply(len*(1-.5*age/life)));
            float al=(1-age/life)*Ease.smooth(age,0,.02f);
            m.line(a,b,.045,WHITE,.85f*al);m.softLine(a,b,.22,AZURE,.35f*al);
        }
    }

    // ───────────────────────────── the effect layer, the camera ─────────────────────────────

    static void frame(Fx c,FxFrame f){
        float t=c.sec;
        float k=FxLib.kick(t,FLASH,.12f);if(k>.01f)f.light(pivot(c.cast),0x9fc8ff,8,1.1f*k);
        var g=giant(c.cast,t);if(g!=null)f.light(g.at(SpiritSword.TIP*.55),0x6aa0ff,9,.75f*g.alpha());
        float s=FxLib.kick(t,(float)IsosangRules.SWEEP_FRONT,.2f);if(s>.01f)f.light(world(feet(c.cast),yaw(c.cast),point(ARC,0,.6*IsosangRules.SWEEP_REACH)),0xb8d8ff,12,1.2f*s);
        // fast as it sweeps: short afterglow, else the blade and its crescent leave copies of themselves round its way
        if(t<LENGTH)f.afterglow(t>=IsosangRules.SWEEP_START-.02f&&t<FADE1?.05f:.3f);
        SweepLights.frame(c,f);
    }
    /** Shake: a little as the sword forms, more as it crosses the front (each over two frames). */
    static float shake(StandClient.Cast cast,float sec){return Math.max(.4f*bump(sec,FLASH+.02f,.08f),1.4f*bump(sec,(float)IsosangRules.SWEEP_FRONT,.12f));}
    /** How far its caster's own camera draws back for it (blocks): out as the sword forms, back once it is over. */
    static final double PULL=1.6;
    static double pull(float sec){return PULL*Ease.smooth(sec,.06f,.5f)*(1-Ease.smooth(sec,.95f,1.45f));}
}
