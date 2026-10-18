package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * T held "어둠을 가로지르다", remade in 2.38.0 (user request): the free hand runs out along the blade and sets it alight;
 * three fire slashes are cut and thrown up the line (a level cut, a falling backhand and a big falling forehand, at
 * the server's {@code RUSH_SLASHES}), each a flaming crescent flying 30 blocks, burning the way it went and striking
 * whoever it passes, who is held in a dark orb. The caster bursts after them, to the first one struck or to the end
 * of the range, arrives in an explosion of fire that bursts again on the one he came for, coils, and spins once round
 * in one huge ring of fire; the ground he spun on is left burning. Fire is drawn on fixed planes, never turned to the
 * camera, and only the three slashes and the ring are slashes.
 */
final class ThroughSkill implements Fx.Skill {
    // ───────────────────────────── timing (the server's ticks) ─────────────────────────────

    /** The fire slashes leave the blade (seconds). */
    static final float[] SLASH=new float[KagenoRules.RUSH_SLASHES.length];
    static{for(int i=0;i<SLASH.length;i++)SLASH[i]=KagenoRules.RUSH_SLASHES[i]/20f;}
    /** The dash, its arrival strike, the fire bursting on whom it came for, and the spin cut. */
    static final float D=KagenoRules.RUSH_START/20f,ARRIVE=KagenoRules.RUSH_ARRIVE/20f,BURST=KagenoRules.RUSH_BURST/20f,CUT=KagenoRules.RUSH_FINISH/20f;
    /** Where the blade starts its round (the ring of fire grows from there), and the end of the cast. */
    static final float SWEEP=1.47f,END=KagenoRules.DURATION[KagenoRules.RUSH]/20f;
    /** The slashes' speed (blocks a second) and range, how long one takes to reach the end, and how long it is cut before it leaves. */
    static final double SPEED=KagenoRules.RUSH_SLASH_SPEED*20,RANGE=KagenoRules.RUSH_RANGE;
    static final float FLIGHT=(float)((RANGE-1)/SPEED),FORM=.04f;
    static final int DEEP=0x3a0c9c,VIOLET=0x8a3cff,MAGENTA=0xd23cff,PINK=0xff6ae0,HOT=0xffd2f6,WHITE=0xffffff;

    // ───────────────────────────── motion ─────────────────────────────

    /** The blade drawn back to the right side, the free hand laid on it at the guard, then run out along it. */
    static final R6.Pose KINDLE=pose().body(-18,6,0).at(0,-.06,0).head(30,10,0).rArm(-45,42).lArm(-25,37).rLeg(-84,20).lLeg(-84,-18).blade(-5,77);
    static final R6.Pose IGNITE=pose().body(-30,10,0).at(0,-.14,0).head(26,6,0).rArm(-36,50).lArm(-12,70).rLeg(-78,40).lLeg(-80,-34).blade(-4,82);
    /** Cut 1, level from the right to the left: wound back, through the front, carried on round. */
    static final R6.Pose WIND1=pose().body(-46,12,0).at(-.02,-.2,0).head(-28,2,0).rArm(-18,66).lArm(-40,-91).rLeg(-78,44).lLeg(-80,-34).blade(0,92);
    static final R6.Pose PASS1=pose().body(-6,15,0).at(.06,-.22,0).head(-4,2,0).rArm(-4,0).lArm(-48,-86).rLeg(-77,44).lLeg(-78,-32).blade(2,-6);
    static final R6.Pose END1=pose().body(36,14,0).at(.08,-.22,0).head(22,2,0).rArm(-12,-44).lArm(-40,-114).rLeg(-77,44).lLeg(-78,-32).blade(-6,-76);
    /** Cut 2, a falling backhand from the upper left to the lower right. */
    static final R6.Pose RISE2=pose().body(44,4,0).at(.04,-.12,0).head(26,-6,0).rArm(40,4).lArm(-50,-116).rLeg(-80,38).lLeg(-80,-36).blade(48,-28);
    static final R6.Pose PASS2=pose().body(6,16,0).at(.1,-.24,0).head(4,4,0).rArm(-4,18).lArm(-45,-114).rLeg(-77,44).lLeg(-78,-32).blade(-8,14);
    static final R6.Pose END2=pose().body(-34,18,0).at(.12,-.26,0).head(-20,6,0).rArm(-28,50).lArm(-30,-74).rLeg(-76,46).lLeg(-78,-30).blade(-38,78);
    /** Cut 3, the big falling forehand: raised high over the right shoulder with the weight back, then the whole body into it. */
    static final R6.Pose RAISE3=pose().body(-56,-6,0).at(-.06,-.06,0).head(-34,-4,0).rArm(58,48).lArm(-18,-86).rLeg(-82,50).lLeg(-74,-24).blade(64,94);
    static final R6.Pose PASS3=pose().body(6,26,0).at(.2,-.32,0).head(4,-18,0).rArm(-6,8).lArm(-50,-134).rLeg(-70,160).lLeg(-62,-12).blade(-14,0);
    static final R6.Pose END3=pose().body(46,34,0).at(.22,-.4,0).head(30,-26,0).rArm(-40,-34).lArm(-40,-124).rLeg(-66,168).lLeg(-58,-10).blade(-42,-74);
    /** Down low to burst after them, the blade carried on round to trail behind on the left; flat out; braking on arrival. */
    static final R6.Pose COIL=pose().body(10,40,0).at(.1,-.5,0).head(0,-34,0).rArm(-56,-118).lArm(-40,20).rLeg(-58,176).lLeg(-50,-4).blade(-30,-150);
    static final R6.Pose LUNGE=pose().body(4,50,0).at(.1,-.56,0).head(0,-44,0).rArm(-50,-128).lArm(-30,10).rLeg(-40,178).lLeg(-34,-4).blade(-40,-160);
    static final R6.Pose DASH=pose().body(0,56,0).at(0,-.6,0).head(0,-48,0).rArm(-44,-134).lArm(-24,-30).rLeg(-30,176).lLeg(-36,0).blade(-46,-164);
    static final R6.Pose BRAKE=pose().body(-8,18,0).at(.05,-.46,0).head(10,-10,0).rArm(-36,-96).lArm(-10,40).rLeg(-80,150).lLeg(-48,-14).blade(-16,-118);
    /** The coil for the spin: rising, the sword arm across to the left and the whole body wound round to the left. */
    static final R6.Pose SETTLE=pose().body(30,14,0).at(0,-.34,0).head(18,-6,0).rArm(-20,-80).lArm(-30,110).rLeg(-72,50).lLeg(-70,-50).blade(-8,-120);
    static final R6.Pose COILED=pose().body(84,26,0).at(0,-.42,0).head(48,-12,0).rArm(-14,-72).lArm(-36,138).rLeg(-70,58).lLeg(-70,-58).blade(-4,-96);
    static final R6.Pose UNWIND=pose().body(50,24,0).at(0,-.4,0).head(30,-10,0).rArm(-8,-40).lArm(-40,120).rLeg(-70,58).lLeg(-70,-58).blade(-4,-52);
    /** The spin, the blade held out to the right as the body turns once round (clockwise from above). */
    static final R6.Pose SPIN=pose().at(0,-.37,0).head(0,-8,0).rArm(-4,70).lArm(-56,-82).rLeg(-70,58).lLeg(-70,-58).blade(0,90);
    static final R6.Pose FOLLOW=pose().body(-388,18,0).at(0,-.34,0).head(-20,-6,0).rArm(-10,70).lArm(-62,-96).rLeg(-66,62).lLeg(-66,-62).blade(-6,92);
    static final R6.Pose SETTLED=pose().body(-360,10,0).at(0,-.22,0).head(0,-2,0).rArm(-16,60).lArm(-70,-60).rLeg(-72,52).lLeg(-72,-52).blade(-10,84);
    static final R6.Pose LOWER=pose().body(-360,4,0).at(0,-.08,0).rArm(-60,30).lArm(-80,-20).rLeg(-84,22).lLeg(-84,-20).blade(-36,64);
    static final Anim CLIP=Anim.joints(
        k(0,KagenoClips.IDLE),
        k(.12,KINDLE),
        k(.26,IGNITE),
        k(SLASH[0]-.045,WIND1),
        k(SLASH[0],PASS1,Anim.Ease.LINEAR),
        k(SLASH[0]+.05,END1),
        k(SLASH[1]-.05,RISE2),
        k(SLASH[1],PASS2,Anim.Ease.LINEAR),
        k(SLASH[1]+.05,END2),
        k(SLASH[2]-.065,RAISE3),
        k(SLASH[2]-.035,RAISE3.body(-60,-8,0).at(-.08,-.05,0).blade(66,96)),
        k(SLASH[2],PASS3,Anim.Ease.LINEAR),
        k(SLASH[2]+.05,END3),
        k(D-.015,COIL),
        k(D,LUNGE,Anim.Ease.OUT),
        k(D+.05,DASH),
        k(ARRIVE-.07,DASH.body(0,52,0).at(0,-.58,0).lArm(-20,-34)),
        k(ARRIVE,BRAKE),
        k(BURST,SETTLE),
        k(SWEEP-.07,COILED),
        k(SWEEP,UNWIND),
        k(SWEEP+.03,SPIN.body(-40,22,0).rArm(-4,36).blade(0,50),Anim.Ease.LINEAR),
        k(SWEEP+.06,SPIN.body(-150,20,0).rArm(-4,66).blade(0,86),Anim.Ease.LINEAR),
        k(SWEEP+.09,SPIN.body(-262,20,0),Anim.Ease.LINEAR),
        k(SWEEP+.13,SPIN.body(-350,20,0).rArm(-6,72).blade(-2,92)),
        k(1.72,FOLLOW),
        k(2.05,SETTLED),
        k(2.45,LOWER),
        k(2.85,KagenoClips.IDLE.body(-360,0,0))).stops(Anim.stop(ARRIVE,.04,.055),Anim.stop(CUT,.06,.12));
    @Override public Anim clip(int combo){return CLIP;}
    /** The client drops the cast at the server's duration (2.9 s), so everything is over by then. */
    @Override public float lifetime(){return END;}
    @Override public boolean layered(){return true;}
    @Override public int afterimageColor(){return 0x9a4cff;}

    // ───────────────────────────── where things are ─────────────────────────────

    /** Each slash: the tilt of its plane (degrees, its right side up), its radius as it leaves and at the end of its flight, its arc (degrees, signed from its tail to its head toward its right) and the height of its middle. */
    static final double[] ROLL={0,-35,35},R0={2.3,2.3,2.9},R1={3.3,3.3,4.0},ARC={-140,140,-150},LIFT={1.15,1.3,1.35};
    /** The side of slash {@code i}'s plane: the caster's right, tilted by its roll. */
    static Vec3d side(Fx c,int i){double a=Math.toRadians(ROLL[i]);return c.right.multiply(Math.cos(a)).add(0,Math.sin(a),0);}
    /** How far up the line slash {@code i} has flown at {@code t} (blocks; the server's), or -1 before it leaves. */
    static double flown(int i,float t){return KagenoRules.rushSlash(i,t*20);}
    static double radius(int i,float t){return R0[i]+(R1[i]-R0[i])*Ease.out(t-SLASH[i],0,FLIGHT);}
    /** The middle (front) of slash {@code i}'s arc with it {@code d} blocks up the line. */
    static Vec3d front(Fx c,int i,double d){return c.origin.add(c.f.multiply(d)).add(0,LIFT[i],0);}
    /** The point {@code deg} degrees from the middle of slash {@code i}'s arc (toward its side), with it {@code d} up the line and {@code r} across. */
    static Vec3d onArc(Fx c,int i,double d,double r,double deg){
        double a=Math.toRadians(deg);
        return front(c,i,d).add(c.f.multiply((Math.cos(a)-1)*r)).add(side(c,i).multiply(Math.sin(a)*r));
    }
    /** When slash {@code i} passes {@code x} blocks up the line. */
    static float passes(int i,double x){return SLASH[i]+(float)(Math.max(0,x-1)/SPEED);}
    /** How far a point lies up the line from the cast spot. */
    static double along(Fx c,Vec3d p){return p.subtract(c.origin).dotProduct(c.f);}
    /** Who stands where the slashes fly (as the server picks them), and anyone the server holds there. */
    static List<LivingEntity> inPath(Fx c){
        var world=c.mc.world;if(world==null)return c.targets;UUID caster=c.cast.caster();Vec3d mid=c.origin.add(c.f.multiply(RANGE/2));
        List<LivingEntity> out=new ArrayList<>(world.getEntitiesByClass(LivingEntity.class,new Box(mid,mid).expand(RANGE/2+4),e->e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!e.getUuid().equals(caster)&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)&&onLine(c,e.getPos())));
        for(var e:c.targets)if(!out.contains(e)&&onLine(c,e.getPos()))out.add(e);
        return out;
    }
    static boolean onLine(Fx c,Vec3d p){Vec3d d=p.subtract(c.origin);return KagenoRules.rushPath(d.dotProduct(c.f),d.dotProduct(c.right),d.y,-.5,RANGE+.5);}
    static Vec3d chest(Fx c,LivingEntity e){return e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);}
    /** When {@code e} was first struck: by the first slash if it stands on the line, else by the arrival. */
    static float struck(Fx c,LivingEntity e){Vec3d p=e.getLerpedPos(c.delta);return onLine(c,p)?passes(0,along(c,p)):ARRIVE;}
    /** The sword hand and blade on the caster as drawn now. */
    static Vec3d[] blade(Fx c){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(KagenoClips.action(c.cast,c.delta)));}

    /** The caster's feet through the dash as this client drew them ({sec, x, y, z}, a sample a frame), per cast. */
    private static final Map<UUID,List<double[]>> PATHS=new LinkedHashMap<>();
    static List<double[]> path(Fx c){
        var p=PATHS.get(c.cast.id());
        if(p==null){p=new ArrayList<>();PATHS.put(c.cast.id(),p);while(PATHS.size()>16)PATHS.remove(PATHS.keySet().iterator().next());}
        if(c.sec>=D-.02f&&c.sec<CUT){
            if(p.isEmpty()&&c.sec>D)p.add(new double[]{D,c.origin.x,c.origin.y,c.origin.z});
            if(p.isEmpty()||c.sec>p.get(p.size()-1)[0])p.add(new double[]{c.sec,c.pos.x,c.pos.y,c.pos.z});
        }
        return p;
    }
    /** Points every {@code step} blocks along the dash as drawn, each with the time the caster passed it: {sec, x, y, z}. */
    static List<double[]> stations(Fx c,double step){
        var path=path(c);List<double[]> out=new ArrayList<>();double run=0,next=step*.5;
        for(int i=1;i<path.size();i++){
            double[] a=path.get(i-1),b=path.get(i);double len=Math.hypot(b[1]-a[1],b[3]-a[3]);
            while(len>1e-6&&next<=run+len){double u=(next-run)/len;out.add(new double[]{a[0]+(b[0]-a[0])*u,a[1]+(b[1]-a[1])*u,a[2]+(b[2]-a[2])*u,a[3]+(b[3]-a[3])*u});next+=step;}
            run+=len;
        }
        return out;
    }
    /** The way the dash went (horizontal unit vector): from the cast spot to the caster, or the facing before he moves. */
    static Vec3d heading(Fx c){Vec3d d=c.pos.subtract(c.origin).multiply(1,0,1);return d.lengthSquared()>.25?d.normalize():c.f;}
    /** The caster's feet (the floor the arrival and the spin act on). */
    static Vec3d floor(Fx c){return c.pos;}

    /**
     * The spin's blade: where its head is (degrees, clockwise from above like the turn, unwound through the turn) as
     * the clip draws it, held back with the clip through the hit-stop.
     */
    static double bladeAngle(Fx c,float t){return Math.toDegrees(Math.atan2(c.f.z,c.f.x))+turned(t);}
    /** How far the blade has turned round from the cast facing at {@code t} (degrees, clockwise from above, unwound). */
    static double turned(float t){var fr=CLIP.frame(t);var b=R6.bladeOf(fr);return -fr.yaw()+Math.toDegrees(Math.atan2(b.rt(),b.fw()));}
    /** The ring of fire the spin leaves: its tail and head (degrees, the head at most once round ahead). */
    static double[] ring(Fx c,float t){
        double start=bladeAngle(c,SWEEP),head=bladeAngle(c,Math.min(t,RING_END));
        return new double[]{Math.max(start,head-360),head};
    }
    /** The ring stops following the blade once the spin has slowed. */
    static final float RING_END=1.72f;
    /** How far round the spin's ring has gone at {@code t} (0..1). */
    static float round(float t){if(t<SWEEP)return 0;return (float)Math.min(1,(turned(Math.min(t,RING_END))-turned(SWEEP))/360);}
    static float round(Fx c,float t){return round(t);}
    /** When the ring closed: the first time on the clip it has gone once round. */
    static final float CLOSED=closedAt();
    static float closedAt(){for(float s=SWEEP;s<RING_END;s+=.002f)if(round(s)>=1)return s;return RING_END;}
    static float closed(Fx c){return CLOSED;}
    /** Basis {u, v} of a plane through the body tilted up behind by {@code tilt} degrees; angles in it run clockwise seen from above. */
    static Vec3d[] plane(Fx c,double tilt){
        double a=Math.toRadians(tilt);Vec3d n=new Vec3d(0,Math.cos(a),0).add(c.f.multiply(Math.sin(a))).normalize();
        Vec3d u=new Vec3d(1,0,0).subtract(n.multiply(n.x)).normalize();
        return new Vec3d[]{u,u.crossProduct(n)};
    }
    /** The ring's outer edge: growing as it goes round, then thrown far out as it is eaten. */
    static double ringRadius(Fx c,float t,float closed){return 4.4+1.8*round(c,t)+(t>closed?4.2*Ease.out(t,closed,closed+.34f):0);}

    // ───────────────────────────── the slashes ─────────────────────────────

    /**
     * The three fire slashes (fire crescents, widest in the middle with pointed tips): each is cut as the blade
     * passes, leaves at the server's time, grows as it flies 30 blocks up the line and burns out at the end, eaten
     * from its tail. The spin's one ring of fire goes round with the blade, closes and is thrown out, eaten as it goes.
     */
    @Override public void crescents(Fx c){
        float t=c.sec;
        for(int i=0;i<SLASH.length;i++){
            float age=t-SLASH[i];if(age<-FORM||age>=FLIGHT+.07f)continue;
            double d=Math.max(1,flown(i,t)),r=radius(i,Math.max(t,SLASH[i]));
            float grow=Ease.out(age,-FORM,0),gone=Ease.smooth(age,FLIGHT-.02f,FLIGHT+.07f);
            Vec3d o=front(c,i,d).subtract(c.f.multiply(r));double arc=ARC[i]*Math.max(.05,grow);
            KagenoFire.crescent(o,c.f,side(c,i),r*(.8+.2*grow),r*.56,-ARC[i]/2,arc,15,i*5+3,gone,.5f,Ease.smooth(age,-FORM,-FORM+.017f)*(1-.4f*gone),.3);
        }
        if(t>=SWEEP-.01f&&t<2.15f){
            float closed=closed(c);double[] r=ring(c,t);if(r[1]-r[0]<4)return;
            var p=plane(c,5);double outer=ringRadius(c,t,closed);
            float eaten=t<closed?0:Ease.smooth(t,closed+.02f,closed+.36f),a=Ease.smooth(t,SWEEP-.01f,SWEEP+.01f)*(1-Ease.smooth(t,closed+.24f,closed+.4f));
            KagenoFire.crescent(floor(c).add(0,.95,0),p[0],p[1],outer,Math.min(2.7,outer*.55),r[0],r[1]-r[0],15,9,eaten,1,a,.25);
        }
    }

    // ───────────────────────────── light ─────────────────────────────

    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;
        bladeLight(c,m,t);
        for(int i=0;i<SLASH.length;i++)slashLight(c,m,t,i);
        strikes(c,m,t);
        launch(c,m,t);
        dashLight(c,m,t);
        arrival(c,m,t);
        windup(c,m,t);
        cut(c,m,t);
        // Embers rising off the burning ring after the cut.
        if(t>=CUT+.1f&&t<END){float a=Ease.smooth(t,CUT+.1f,CUT+.25f)*(1-Ease.smooth(t,END-.6f,END));FxLib.embers(m,floor(c),6,3.5,40,t-CUT,.9f,.07,0xffa8f0,a,13);}
    }
    /** The blade glowing as the hand runs along it and burning hot through the cuts, the dash and the spin. */
    static void bladeLight(Fx c,EffectMesh m,float t){
        float lit=Ease.smooth(t,.1f,.28f)*(1-Ease.smooth(t,1.8f,2.3f));if(lit<=0)return;
        var hb=blade(c);Vec3d hand=hb[0],tip=hb[0].add(hb[1].multiply(1.35*Math.max(.15,Ease.smooth(t,.1f,.28f))));
        float flare=0;for(float s:SLASH)flare=Math.max(flare,FxLib.kick(t,s,.08f));flare=Math.max(flare,FxLib.kick(t,CUT,.12f));
        m.glowLine(hand.add(hb[1].multiply(.2)),tip,.07,HOT,.9f*lit);
        m.softLine(hand.add(hb[1].multiply(.2)),tip,.5+.4*flare,MAGENTA,(.35f+.4f*flare)*lit);
        m.softDisc(tip,.35+.5*flare,PINK,(.45f+.5f*flare)*lit);
        if(t<.36f)FxLib.embers(m,hand.add(hb[1].multiply(.7)).add(0,-.3,0),.4,1.4,12,t,1.6f,.05,0xffb8f0,lit,7);
    }
    /** A slash leaving: a burst of light where it leaves the blade and a ring of air round its line; its glow flying with it, and its burst at the end of the range. */
    static void slashLight(Fx c,EffectMesh m,float t,int i){
        float age=t-SLASH[i];if(age<-.01f||age>=FLIGHT+.5f)return;
        Vec3d muzzle=front(c,i,1.2);
        if(age<.08f)FxLib.flash(m,muzzle,1.6,MAGENTA,1-Ease.unit(age,0,.08f));
        if(age>=0&&age<.22f)FxLib.ringPlane(m,muzzle,c.right,FxLib.UP,.4+2.6*Ease.out(age,0,.2f),.3,0xffb0f0,.85f*(1-Ease.unit(age,0,.22f)));
        if(age>=0&&age<FLIGHT)m.softDisc(front(c,i,flown(i,t)).subtract(c.f.multiply(radius(i,t)*.35)),radius(i,t)*.75,MAGENTA,.35f);
        float e=age-FLIGHT;
        if(e>=0&&e<.5f){
            Vec3d p=front(c,i,RANGE);float a=1-Ease.smooth(e,.08f,.5f);
            FxLib.flash(m,p,2.6+2*Ease.out(e,0,.15f),MAGENTA,a);
            FxLib.ringPlane(m,p,c.right,FxLib.UP,.6+5*Ease.out(e,0,.35f),.5,PINK,.8f*(1-Ease.unit(e,0,.4f)));
            FxLib.ring(m,new Vec3d(p.x,c.origin.y+.06,p.z),.5+6*Ease.out(e,0,.4f),.7,VIOLET,.7f*(1-Ease.unit(e,0,.45f)));
        }
    }
    /** Whoever a slash passes: a flash and a ring of air on them as it goes through. */
    static void strikes(Fx c,EffectMesh m,float t){
        if(t<SLASH[0]||t>=SLASH[2]+FLIGHT+.4f)return;
        for(var e:inPath(c)){
            Vec3d p=chest(c,e);double x=along(c,e.getLerpedPos(c.delta));
            for(int i=0;i<SLASH.length;i++){
                float k=FxLib.kick(t,passes(i,x),.1f);if(k<.01f)continue;
                FxLib.flash(m,p,1.6*k+.4,MAGENTA,k);FxLib.ringPlane(m,p,c.right,FxLib.UP,.4+2*(1-k),.25,HOT,.8f*k);
            }
        }
    }
    /** The burst the dash leaves from: a pink-violet flash and shock rings racing out over the floor. */
    static void launch(Fx c,EffectMesh m,float t){
        if(t<D-.01f||t>=D+.5f)return;
        Vec3d o=c.origin.add(0,.9,0),floor=c.origin.add(0,.06,0);float a=Ease.smooth(t,D-.01f,D+.01f);
        if(t<D+.18f)FxLib.flash(m,o,2.4+2.4*Ease.out(t,D,D+.1f),MAGENTA,(FxLayer.available()?.75f:1)*(1-Ease.smooth(t,D+.03f,D+.18f)));
        FxLib.ring(m,floor,.5+9*Ease.out(t,D,D+.45f),.9,PINK,.75f*a*(1-Ease.unit(t,D,D+.45f)));
        FxLib.ring(m,floor,.3+5*Ease.out(t,D,D+.25f),.4,WHITE,.6f*a*(1-Ease.unit(t,D,D+.25f)));
        FxLib.ringPlane(m,o,c.right,FxLib.UP,.5+3.6*Ease.out(t,D,D+.22f),.4,0xffb0ec,.8f*a*(1-Ease.unit(t,D,D+.22f)));
    }
    /** The dash: the body a comet of fire, rings of light popping round its path as it passes. */
    static void dashLight(Fx c,EffectMesh m,float t){
        if(t<D||t>=ARRIVE+.4f)return;
        float body=Ease.smooth(t,D,D+.02f)*(1-Ease.smooth(t,ARRIVE-.02f,ARRIVE+.06f));Vec3d at=c.pos.add(0,.9,0),h=heading(c);
        if(body>0){m.softDisc(at,1.4,MAGENTA,.55f*body);m.softDisc(at.add(h.multiply(.4)),.6,HOT,.6f*body);}
        Vec3d side=new Vec3d(-h.z,0,h.x);int k=0;
        for(var s:stations(c,5)){
            float from=(float)s[0],u=Ease.unit(t,from,from+.3f);k++;if(t<from||u>=1)continue;
            FxLib.ringPlane(m,new Vec3d(s[1],s[2]+1,s[3]),side,FxLib.UP,.4+(1.6+.1*k)*Ease.out(t,from,from+.22f),.3,k%2==0?HOT:PINK,.85f*(1-u)*Ease.smooth(t,from,from+.02f));
        }
    }
    /** The arrival: a flash and a shock ring where he lands, a flash on whom he came for, and the fire bursting on them a moment later. */
    static void arrival(Fx c,EffectMesh m,float t){
        if(t<ARRIVE-.01f||t>=BURST+.5f)return;
        Vec3d o=c.pos.add(0,.9,0);float a=Ease.smooth(t,ARRIVE-.01f,ARRIVE+.01f);
        if(t<ARRIVE+.2f)FxLib.flash(m,o,2.6+2*Ease.out(t,ARRIVE,ARRIVE+.08f),MAGENTA,(FxLayer.available()?.75f:1)*(1-Ease.smooth(t,ARRIVE+.04f,ARRIVE+.2f)));
        FxLib.ring(m,floor(c).add(0,.06,0),.5+7*Ease.out(t,ARRIVE,ARRIVE+.35f),.8,PINK,.75f*a*(1-Ease.unit(t,ARRIVE,ARRIVE+.35f)));
        for(var e:c.targets){
            Vec3d p=chest(c,e);float k=FxLib.kick(t,ARRIVE,.08f),b=FxLib.kick(t,BURST,.12f);
            if(k>.01f)FxLib.flash(m,p,1.8*k+.4,HOT,k);
            if(b>.01f){FxLib.flash(m,p,2.8*b+.6,MAGENTA,b);FxLib.ringPlane(m,p,c.right,FxLib.UP,.5+3*(1-b),.35,PINK,.8f*b);}
        }
    }
    /** The coil before the spin: light drawn into the body and a circle of fire closing in over the floor. */
    static void windup(Fx c,EffectMesh m,float t){
        if(t<BURST-.02f||t>=CUT+.05f)return;
        float a=Ease.smooth(t,BURST-.02f,BURST+.08f)*(1-Ease.smooth(t,CUT-.03f,CUT+.05f));Vec3d o=c.pos.add(0,1,0);
        FxLib.converge(m,o,3.4,26,t,BURST,CUT-.06f,0xffb8f0,77);
        FxLib.ring(m,floor(c).add(0,.05,0),2+4.2*(1-Ease.in(t,BURST,CUT)),.5,PINK,.7f*a);
        FxLib.glow(m,floor(c).add(0,.04,0),5,DEEP,.35f*a);
        m.softDisc(o,.8+.6*Ease.smooth(t,BURST,CUT),MAGENTA,.4f*a);
    }
    /** The spin cut: a blinding flash and a pillar of light at the body and shock rings racing out over the floor. */
    static void cut(Fx c,EffectMesh m,float t){
        if(t<CUT-.01f||t>=CUT+.6f)return;
        Vec3d pos=c.pos,o=floor(c).add(0,.06,0);float a=Ease.smooth(t,CUT-.01f,CUT+.01f);
        if(t<CUT+.14f)FxLib.flash(m,pos.add(0,1,0),3.6,MAGENTA,(FxLayer.available()?.8f:1)*(1-Ease.unit(t,CUT,CUT+.14f)));
        FxLib.column(m,pos,10*Ease.out(t,CUT,CUT+.06f),.6*(1-Ease.unit(t,CUT,CUT+.4f))+.1,0xe0a8ff,a*(1-Ease.smooth(t,CUT+.05f,CUT+.4f)));
        FxLib.ring(m,o,.5+15*Ease.out(t,CUT,CUT+.55f),1,WHITE,.6f*a*(1-Ease.unit(t,CUT,CUT+.55f)));
        FxLib.ring(m,o,.4+11*Ease.out(t,CUT,CUT+.45f),1.3,VIOLET,.8f*a*(1-Ease.unit(t,CUT,CUT+.45f)));
        FxLib.ring(m,o,.3+7*Ease.out(t,CUT,CUT+.32f),.7,PINK,.7f*a*(1-Ease.unit(t,CUT,CUT+.32f)));
    }

    // ───────────────────────────── fire, smoke and dust ─────────────────────────────

    /** One fire cell: {size, alpha, heat, wisp, seed}. */
    static void cell(List<Vec3d> pts,List<float[]> cells,Vec3d p,double size,float alpha,double heat,double wisp,int seed){
        if(alpha<=.005f||size<=.01)return;pts.add(p);cells.add(new float[]{(float)size,alpha,(float)heat,(float)wisp,seed});
    }
    /** Cells of fire thrown out of {@code o} at {@code at}: flung out (flatter for a small {@code flat}) and up, slowing, swelling and cooling into smoke. */
    static void burst(List<Vec3d> pts,List<float[]> cells,Vec3d o,float t,float at,int n,double out,double size,double flat,int seed){
        for(int k=0;k<n;k++){
            float life=.4f+.3f*(float)Ease.hash(seed+k*3),s=t-at-.015f*(float)Ease.hash(seed+k*5);if(s<0||s>=life)continue;float u=s/life;
            double az=(k+Ease.hash(seed+k*7)*.7)*Math.PI*2/n,el=(Ease.hash(seed+k*11)-.3)*1.2*flat,go=out*(.55+.45*Ease.hash(seed+k*13))*Ease.out(u,0,.7f)*life;
            Vec3d dir=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
            cell(pts,cells,o.add(dir.multiply(go)).add(0,.6*u*u,0),size*(.6+.6*Ease.out(u,0,.5f))*(.8+.4*Ease.hash(seed+k*17)),Ease.smooth(u,0,.08f)*(1-Ease.smooth(u,.5f,1)),1-.75*u,.5,seed+k*31);
        }
    }
    @Override public void shaders(Fx c){
        float t=c.sec;List<Vec3d> pts=new ArrayList<>();List<float[]> cells=new ArrayList<>();
        bladeFire(c,t,pts,cells);
        igniteRing(c,t,pts,cells);
        for(int i=0;i<SLASH.length;i++)slashFire(c,t,i,pts,cells);
        struckFire(c,t,pts,cells);
        dashFire(c,t,pts,cells);
        vortex(c,t,pts,cells);
        cutFire(c,t,pts,cells);
        // On the effect layer the fire's hottest cores burn above white and bloom.
        KagenoFire.hot(FxLayer.available()?1.1f:0);
        KagenoFire.cells(c.matrix,c.camera,pts,cells,c.age);
        KagenoFire.hot(0);
        orbs(c,t);
        if(FxLayer.available())dust(c,t);
    }
    /** Flames along the blade from the hand's touch, licking off it through the cuts, the dash and the spin. */
    static void bladeFire(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        float lit=1-Ease.smooth(t,1.75f,2.2f);if(t<.1f||lit<=0)return;
        var hb=blade(c);
        for(int k=0;k<8;k++){
            double along=.3+1.05*k/7;float on=Ease.smooth(t,.1f+.17f*k/7,.14f+.17f*k/7);
            float flick=(float)(.8+.2*Math.sin(t*37+k*1.7));
            cell(pts,cells,hb[0].add(hb[1].multiply(along)).add(0,.06,0),(.16+.1*Ease.hash(k*3+1))*flick*(1+.4*FxLib.kick(t,CUT,.15f)),on*lit*.95f,.92+.1*Ease.hash(k*5+2),.8,k*13+5);
        }
    }
    /** A ring of fire flaring up round the feet as the blade catches, dying as the cuts begin. */
    static void igniteRing(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        if(t<.2f||t>=1.0f)return;
        for(int k=0;k<14;k++){
            float born=.2f+.1f*(float)Ease.hash(k*3+41),life=.38f+.2f*(float)Ease.hash(k*5+42);
            float cyc=(t-born)/life;if(cyc<0)continue;int n=(int)Math.floor(cyc);float u=cyc-n;if(born+n*life>.72f)continue;
            double az=(k+.4*Ease.hash(k*7+n*3))*Math.PI*2/14,r=1.5+.5*Ease.hash(k*11+n*5);
            cell(pts,cells,c.origin.add(Math.cos(az)*r,.3+.6*u,Math.sin(az)*r),(.45+.25*Ease.hash(k*13+n))*(1-.3*u),Ease.smooth(u,0,.15f)*(1-Ease.smooth(u,.5f,1)),.95-.4*u,.9,k*17+n*7+3);
        }
    }
    /**
     * A slash's fire: flames shed from its arc as it flies (left behind where it passed, rising and cooling), flames
     * running along the floor under its line, and the burst of fire where it ends.
     */
    static void slashFire(Fx c,float t,int i,List<Vec3d> pts,List<float[]> cells){
        float age=t-SLASH[i];if(age<0||age>=FLIGHT+.8f)return;
        int n=i==2?30:24,seed=i*997+11;
        for(int k=0;k<n;k++){
            double d=1.5+(k+.6*Ease.hash(seed+k))*(RANGE-1.5)/n;float born=passes(i,d),life=.32f+.25f*(float)Ease.hash(seed+k*3),s=t-born;if(s<0||s>=life)continue;float u=s/life;
            double r=radius(i,born)*(.8+.12*Ease.hash(seed+k*5)),deg=(Ease.hash(seed+k*7)-.5)*Math.abs(ARC[i])*.85;
            Vec3d p=onArc(c,i,d,r,deg).add(0,.7*u,0).subtract(c.f.multiply(.6*u));
            cell(pts,cells,p,(.42+.3*Ease.hash(seed+k*11))*(.8+.7*Ease.out(u,0,.6f))*(i==2?1.15:1),Ease.smooth(u,0,.06f)*(1-Ease.smooth(u,.45f,1)),1-.7*u,.75,seed+k*29);
        }
        for(int k=0;k<14;k++){
            double d=2+(k+.5*Ease.hash(seed+k*13+1))*(RANGE-2)/14;float born=passes(i,d)+.02f,life=.5f+.3f*(float)Ease.hash(seed+k*17),s=t-born;if(s<0||s>=life)continue;float u=s/life;
            Vec3d p=c.origin.add(c.f.multiply(d)).add(c.right.multiply((Ease.hash(seed+k*19)-.5)*1.4)).add(0,.3+.5*u,0);
            cell(pts,cells,p,(.45+.3*Ease.hash(seed+k*23))*(1-.2*u),Ease.smooth(u,0,.1f)*(1-Ease.smooth(u,.5f,1)),.9-.5*u,.95,seed+k*37+5);
        }
        burst(pts,cells,front(c,i,RANGE),t,SLASH[i]+FLIGHT,14,7,.85,1,seed+400);
    }
    /** Fire bursting on whoever a slash passes, small fires burning on whoever is held, and the burst after the arrival. */
    static void struckFire(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        if(t<SLASH[0]||t>=BURST+.8f)return;int n=0;
        for(var e:inPath(c)){
            Vec3d p=chest(c,e);double x=along(c,e.getLerpedPos(c.delta));
            for(int i=0;i<SLASH.length;i++)burst(pts,cells,p,t,passes(i,x),8,4.5,.5,1,n*71+i*13+501);
            n++;
        }
        n=0;
        for(var e:c.targets){
            Vec3d p=chest(c,e);float from=struck(c,e)+.05f;
            if(t>=from&&t<CUT)for(int k=0;k<3;k++){
                float life=.4f,cyc=(t-from)/life+(float)Ease.hash(n*7+k);int q=(int)Math.floor(cyc);float u=cyc-q;
                cell(pts,cells,p.add((Ease.hash(n*11+k*3+q)-.5)*.7,-.4+.9*u,(Ease.hash(n*13+k*5+q)-.5)*.7),.3+.15*Ease.hash(k+q),Ease.smooth(u,0,.2f)*(1-Ease.smooth(u,.5f,1))*(1-Ease.smooth(t,CUT-.1f,CUT)),.9-.4*u,.9,n*41+k*7+q);
            }
            burst(pts,cells,p,t,BURST,12,6,.7,1,n*53+601);
            n++;
        }
    }
    /**
     * The dash: a burst of fire where it leaves, flames shed where the body passed (as this client drew it), fire
     * running along the floor of its path, a fiery head round the body, and the explosion where he arrives.
     */
    static void dashFire(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        if(t<D-.01f||t>=ARRIVE+1.0f)return;
        burst(pts,cells,c.origin.add(0,.5,0),t,D,18,8,.85,.6,701);
        int k=0;
        for(var s:stations(c,.8)){
            float life=.4f+.3f*(float)Ease.hash(k*3+711),age=t-(float)s[0];k++;if(age<0||age>=life)continue;float u=age/life;
            Vec3d p=new Vec3d(s[1],s[2],s[3]).add((Ease.hash(k*5+712)-.5)*.9,.45+1.1*Ease.hash(k*7+713)+.6*u,(Ease.hash(k*11+714)-.5)*.9);
            cell(pts,cells,p,(.5+.4*Ease.hash(k*13+715))*(.8+.6*Ease.out(u,0,.5f)),Ease.smooth(u,0,.06f)*(1-Ease.smooth(u,.45f,1)),1-.7*u,.7,k*19+716);
        }
        k=0;
        for(var s:stations(c,1.6)){
            float life=.6f+.3f*(float)Ease.hash(k*3+731),age=t-(float)s[0]-.03f;k++;if(age<0||age>=life)continue;float u=age/life;
            cell(pts,cells,new Vec3d(s[1],s[2],s[3]).add((Ease.hash(k*5+732)-.5)*1.2,.3+.5*u,(Ease.hash(k*7+733)-.5)*1.2),(.5+.3*Ease.hash(k*11+734))*(1-.2*u),Ease.smooth(u,0,.1f)*(1-Ease.smooth(u,.5f,1)),.9-.5*u,.95,k*23+735);
        }
        float body=Ease.smooth(t,D,D+.02f)*(1-Ease.smooth(t,ARRIVE-.03f,ARRIVE+.05f));
        if(body>0){Vec3d h=heading(c),across=new Vec3d(-h.z,0,h.x);
            for(int q=0;q<6;q++)cell(pts,cells,c.pos.subtract(h.multiply(.2+.5*q)).add(0,.5+.8*Ease.hash(q*3+741),0).add(across.multiply((Ease.hash(q*5+742)-.5)*.8)),.55-.05*q,body*(1-q/7f),1-.08*q,.6,q*29+743);}
        burst(pts,cells,floor(c).add(0,.5,0),t,ARRIVE,18,8,.9,.5,751);
    }
    /** The coil before the spin: fire drawn round and in to the body, heating as it gathers. */
    static void vortex(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        if(t<BURST||t>=CUT+.02f)return;Vec3d o=c.pos;
        for(int k=0;k<22;k++){
            float born=BURST+(CUT-BURST-.24f)*(float)Ease.hash(k*3+801),life=.24f,u=(t-born)/life;if(u<0||u>=1)continue;
            double az=Ease.hash(k*5+802)*Math.PI*2-u*2.6,r=4.4*(1-u)*(1-.3*u)+.5,h=.4+1.2*Ease.hash(k*7+803)+.3*u;
            cell(pts,cells,o.add(Math.cos(az)*r,h,Math.sin(az)*r),.55*(1-.4*u),Ease.smooth(u,0,.2f)*(1-Ease.smooth(u,.8f,1)),.6+.4*u,.6,k*31+804);
        }
    }
    /**
     * The spin cut: flames shed round the ring as it goes round, a pillar of fire at the body, a nova of fire flung out
     * flat round the waist, and then the ground left burning in a ring round him with smoke rising off it.
     */
    static void cutFire(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        if(t<SWEEP||t>=END)return;Vec3d o=floor(c);
        if(t<2.1f){
            float closed=closed(c);double[] r=ring(c,t);
            for(int k=0;k<30;k++){
                double at=r[0]+(r[1]-r[0])*(k+.5)/30;float life=.35f+.2f*(float)Ease.hash(k*3+901),s=(float)((r[1]-at)/4000)+(t<closed?0:t-closed);
                if(s>=life)continue;float u=s/life;double rad=ringRadius(c,t,closed)*(.8+.1*Ease.hash(k*5+902)),a=Math.toRadians(at);
                cell(pts,cells,o.add(Math.cos(a)*rad,.9+.4*Ease.hash(k*7+903)+.5*u,Math.sin(a)*rad),(.5+.3*Ease.hash(k*11+904))*(1+.5*u),Ease.smooth(u,0,.05f)*(1-Ease.smooth(u,.5f,1))*(1-Ease.smooth(t,closed+.3f,closed+.5f)),1-.6*u,.7,k*37+905);
            }
        }
        if(t>=CUT&&t<CUT+.9f){
            for(int k=0;k<10;k++){
                float s=t-CUT-.02f*k,life=.5f;if(s<0||s>=life)continue;float u=s/life;
                cell(pts,cells,o.add((Ease.hash(k*3+921)-.5)*.6,.4+1.0*k+2.5*u,(Ease.hash(k*5+922)-.5)*.6),(.9-.04*k)*(1+.4*u),Ease.smooth(u,0,.06f)*(1-Ease.smooth(u,.4f,1)),1.05-.6*u,.4,k*41+923);
            }
            burst(pts,cells,o.add(0,.9,0),t,CUT,40,22,1.0,.15,931);
        }
        if(t>=CUT+.05f){
            float dying=1-Ease.smooth(t,END-.7f,END-.05f);
            for(int k=0;k<40;k++){
                float born=CUT+.05f+.2f*(float)Ease.hash(k*3+951),life=.42f+.25f*(float)Ease.hash(k*5+952),cyc=(t-born)/life;if(cyc<0)continue;
                int q=(int)Math.floor(cyc);float u=cyc-q;
                double az=(k+.6*Ease.hash(k*7+q*3))*Math.PI*2/40,rad=5.2+1.2*Ease.hash(k*11+q*5);
                cell(pts,cells,o.add(Math.cos(az)*rad,.3+.7*u,Math.sin(az)*rad),(.55+.35*Ease.hash(k*13+q))*(1-.25*u)*(.6+.4*dying),Ease.smooth(u,0,.12f)*(1-Ease.smooth(u,.5f,1))*dying,(.95-.45*u)*(.55+.45*dying),.9,k*43+q*11+953);
            }
            for(int k=0;k<14;k++){
                float born=CUT+.4f+.9f*(float)Ease.hash(k*3+971),life=.9f,u=(t-born)/life;if(u<0||u>=1)continue;
                double az=Ease.hash(k*5+972)*Math.PI*2,rad=5+1.5*Ease.hash(k*7+973);
                cell(pts,cells,o.add(Math.cos(az)*rad,.8+2.4*u,Math.sin(az)*rad),(.9+.7*u)*(.8+.4*Ease.hash(k*11+974)),.55f*Ease.smooth(u,0,.15f)*(1-Ease.smooth(u,.5f,1)),.06+.08*Ease.hash(k*13+975),.2,k*47+976);
            }
        }
    }
    /** Whoever is held, inside a dark orb from the strike until the spin cut lets them go. */
    static void orbs(Fx c,float t){
        if(t<SLASH[0]||t>=CUT+.12f||c.targets.isEmpty())return;
        List<Vec3d> orbs=new ArrayList<>();List<Double> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
        for(LivingEntity e:c.targets){
            float from=struck(c,e)+.02f,form=Ease.out(t,from,from+.15f);if(form<=0)continue;
            orbs.add(chest(c,e));sizes.add(.6*SparkSkill.H*(.4+.6*form)*(1+.3*Ease.smooth(t,CUT-.03f,CUT+.12f)));alphas.add(form*(1-Ease.smooth(t,CUT-.02f,CUT+.12f)));
        }
        KagenoFire.billboards(c.matrix,c.camera,10,orbs,sizes,alphas,c.age);
    }
    /** Dust thrown out over the floor where the dash leaves and where it brakes, and by the spin cut. */
    static void dust(Fx c,float t){
        if(t<D-.02f||t>=CUT+1.3f)return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        FxLib.dustRing(c.origin,t,D,.5,4,16,1.15,dust,puffs,121);
        Vec3d h=heading(c),across=new Vec3d(-h.z,0,h.x);
        for(int i=0;i<9;i++){
            float s=t-ARRIVE-.02f*i,life=.6f+.25f*(float)Ease.hash(i*3+131);if(s<0||s>=life)continue;float u=s/life;
            Vec3d q=floor(c).add(h.multiply(.6+.25*i)).add(across.multiply((Ease.hash(i*5+132)-.5)*1.4)).add(0,.2+.45*u,0).subtract(h.multiply(.6*Ease.out(u,0,.6f)));
            dust.add(q);puffs.add(new float[]{(float)((.35+.3*Ease.out(u,0,.5f))*(.8+.4*Ease.hash(i*7+133))),.85f*Ease.smooth(s,0,.04f)*(1-Ease.smooth(u,.5f,1)),.5f*(1-u),u,i*29+134});
        }
        FxLib.dustRing(floor(c),t,ARRIVE,.6,3.5,12,1.1,dust,puffs,141);
        FxLib.dustRing(floor(c),t,CUT,1.2,10,30,1.7,dust,puffs,151);
        FxLib.dustRing(floor(c),t,CUT+.06f,.8,5,14,1.3,dust,puffs,161);
        if(!dust.isEmpty())KagenoFire.dust(c.matrix,c.camera,dust,puffs,c.age);
    }

    // ───────────────────────────── effect layer ─────────────────────────────

    /**
     * Lights: the burning blade, each slash lighting the line as it flies and the burst where it ends, a flash on
     * whoever it passes, the dash's burst, the body's fire along the way, the arrival and the burst on whom he came
     * for, the coil's glow, the spin cut's blinding flash with lights round the floor where the ring passed, and the
     * burning ring left after it. For viewers close by the spin cut has the impact frame, a colour split and a light
     * shaft (the finisher only). Decals: the floor under the slashes' line burnt in two lengths, the dash's path burnt
     * into the floor, and seven gashes torn out round the spin.
     */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;
        float lit=Ease.smooth(t,.1f,.28f)*(1-Ease.smooth(t,1.8f,2.3f));
        if(lit>0){var hb=blade(c);float flare=0;for(float s:SLASH)flare=Math.max(flare,FxLib.kick(t,s,.1f));f.light(hb[0].add(hb[1].multiply(.8)),0xff6ae0,5,(.9f+1.4f*flare)*lit);}
        for(int i=0;i<SLASH.length;i++){
            float age=t-SLASH[i];
            if(age>=0&&age<FLIGHT)f.light(front(c,i,flown(i,t)),0xd04cff,9,2.6f);
            if(age>=-.01f&&age<.2f)f.light(front(c,i,1.2),0xff6ae0,6,2.2f*FxLib.kick(t,SLASH[i],.07f));
            float e=age-FLIGHT;if(e>=-.01f&&e<.6f)f.light(front(c,i,RANGE),0xe060ff,11,3.4f*FxLib.kick(t,SLASH[i]+FLIGHT,.15f));
        }
        if(t>=SLASH[0]&&t<SLASH[2]+FLIGHT+.3f)for(var e:inPath(c)){
            double x=along(c,e.getLerpedPos(c.delta));float k=0;for(int i=0;i<SLASH.length;i++)k=Math.max(k,FxLib.kick(t,passes(i,x),.12f));
            if(k>.01f)f.light(chest(c,e),0xff7ae8,6,2.6f*k);
        }
        if(t>=D-.01f&&t<D+.6f)f.light(c.origin.add(0,1,0),0xff3cc8,13,3.8f*FxLib.kick(t,D,.15f));
        float body=Ease.smooth(t,D,D+.02f)*(1-Ease.smooth(t,ARRIVE,ARRIVE+.1f));
        if(body>0)f.light(c.pos.add(0,1,0),0xd04cff,8,2.2f*body);
        if(t>=ARRIVE-.01f&&t<ARRIVE+.6f)f.light(c.pos.add(0,1,0),0xff4cd8,12,4.0f*FxLib.kick(t,ARRIVE,.15f));
        if(t>=BURST-.01f&&t<BURST+.6f)for(var e:c.targets)f.light(chest(c,e),0xff6ae0,9,3.2f*FxLib.kick(t,BURST,.13f));
        float coil=Ease.smooth(t,BURST,CUT-.05f)*(1-Ease.smooth(t,CUT,CUT+.1f));
        if(coil>0)f.light(c.pos.add(0,1,0),0xff4cd8,7,1.8f*coil);
        if(t>=CUT-.01f){
            Vec3d pos=c.pos,end=floor(c);
            f.light(pos.add(0,1,0),0xe0a0ff,16,5.2f*FxLib.kick(t,CUT,.2f));
            for(int k=0;k<6;k++){double az=k*Math.PI/3+.3;f.light(end.add(Math.cos(az)*5.5,.8,Math.sin(az)*5.5),0xd04cff,7,2.2f*FxLib.kick(t,CUT+.03f+.01f*k,.18f));}
            float burning=Ease.smooth(t,CUT+.1f,CUT+.3f)*(1-Ease.smooth(t,END-.8f,END));
            if(burning>0)f.light(end.add(0,.8,0),0xff70e0,11,1.4f*burning*(float)(.85+.15*Math.sin(t*23)));
            float near=FxLib.near(c,pos);
            if(near>0){f.impact(near*FxLib.impact(t,CUT),FxLib.impactMode(t,CUT));f.chroma(near*.012f*FxLib.kick(t,CUT,.12f));f.shaft(pos.add(0,1,0),0xf0b8ff,.7f*near*FxLib.kick(t,CUT,.22f));}
            float heat=1-Ease.smooth(t,CUT+.15f,CUT+1.1f),a=Ease.smooth(t,CUT,CUT+.05f)*(1-Ease.smooth(t,END-.35f,END));double open=Ease.out(t,CUT,CUT+.12f);
            for(int k=0;k<7;k++){double ang=Math.toRadians(20+360.0/7*k+12*Ease.hash(k*7+151)),reach=k%2==0?1:.7;Vec3d dir=c.f.multiply(Math.cos(ang)).add(c.right.multiply(Math.sin(ang)));
                f.decal(end.add(dir.multiply((3.2*open+.6)*reach)),dir,(2.6*open+.2)*reach,k%2==0?.48:.34,heat,a,21.1f+k*3.7f);}
        }
        if(t>=SLASH[0]){
            float heat=1-Ease.smooth(t,SLASH[2]+.3f,2.2f),fade=1-Ease.smooth(t,END-.35f,END);
            f.decal(c.origin.add(c.f.multiply(8.5)),c.f,7,.55,heat*.9f,.85f*Ease.smooth(t,passes(0,4),passes(0,8))*fade,23.9f);
            f.decal(c.origin.add(c.f.multiply(23)),c.f,7.5,.55,heat*.9f,.85f*Ease.smooth(t,passes(0,19),passes(0,24))*fade,29.3f);
        }
        if(t>=D){
            Vec3d end=floor(c);double len=Math.hypot(end.x-c.origin.x,end.z-c.origin.z);
            if(len>1.5){float heat=1-Ease.smooth(t,D+.2f,D+1.4f),a=Ease.smooth(t,D,D+.08f)*(1-Ease.smooth(t,END-.35f,END));
                f.decal(new Vec3d((c.origin.x+end.x)/2,c.origin.y,(c.origin.z+end.z)/2),heading(c),len*.5,.65,heat,a,17.3f);}
        }
    }
    /**
     * The air bends along each slash as it flies and in a ring where it leaves the blade, over the floor and round the
     * line as the dash leaves, along the dash and where it lands, round the spin's ring and in its shock over the floor.
     */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;
        for(int i=0;i<SLASH.length;i++){
            float age=t-SLASH[i];if(age<0||age>=FLIGHT)continue;
            double r=radius(i,t);Vec3d o=front(c,i,flown(i,t)).subtract(c.f.multiply(r));
            d.arc(o,c.f,side(c,i),r+.15,r*.7,-ARC[i]/2,ARC[i],.8f*(1-Ease.smooth(age,FLIGHT-.06f,FLIGHT)));
            if(age<.18f)d.ring(front(c,i,1.2),c.right,FxLib.UP,.3+2.4*Ease.out(age,0,.18f),.6,.5f*(1-Ease.unit(age,0,.18f)));
        }
        if(t>=D-.01f&&t<D+.4f){
            float in=Ease.smooth(t,D-.01f,D+.015f);
            d.ring(c.origin.add(0,.05,0),c.right,c.f,.5+9*Ease.out(t,D,D+.4f),1.6,.8f*in*(1-Ease.unit(t,D,D+.4f)));
            d.ring(c.origin.add(0,1,0),c.right,FxLib.UP,.5+3.6*Ease.out(t,D,D+.22f),.9,.6f*in*(1-Ease.unit(t,D,D+.22f)));
        }
        if(t>=D&&t<ARRIVE+.15f){Vec3d from=c.origin.add(0,1,0),to=c.pos.add(0,1,0);if(from.distanceTo(to)>1)d.band(from,to,FxLib.UP,1.8,.9f*(1-Ease.smooth(t,ARRIVE-.05f,ARRIVE+.15f)));}
        if(t>=ARRIVE-.01f&&t<ARRIVE+.35f)d.ring(floor(c).add(0,.05,0),c.right,c.f,.5+7*Ease.out(t,ARRIVE,ARRIVE+.35f),1.4,.8f*Ease.smooth(t,ARRIVE-.01f,ARRIVE+.015f)*(1-Ease.unit(t,ARRIVE,ARRIVE+.35f)));
        if(t>=SWEEP&&t<2.1f){
            float closed=closed(c);double[] r=ring(c,t);
            if(r[1]-r[0]>4){var p=plane(c,5);double outer=ringRadius(c,t,closed);d.arc(floor(c).add(0,.95,0),p[0],p[1],outer+.1,Math.min(2.7,outer*.55),r[0],r[1]-r[0],.7f*(1-Ease.smooth(t,closed+.1f,closed+.35f)));}
        }
        if(t>=CUT-.01f&&t<CUT+.55f){
            float in=Ease.smooth(t,CUT-.01f,CUT+.015f);Vec3d floor=floor(c).add(0,.05,0);
            d.ring(floor,c.right,c.f,.5+14*Ease.out(t,CUT,CUT+.55f),2.0,.95f*in*(1-Ease.unit(t,CUT,CUT+.55f)));
            d.ring(floor,c.right,c.f,.4+7.5*Ease.out(t,CUT,CUT+.35f),1.4,.7f*in*(1-Ease.unit(t,CUT,CUT+.35f)));
        }
    }
    /** Chunks torn from the floor where the dash leaves and where it lands, and the spin cut tearing it up round him: chunks thrown out and the floor heaving in a wide ring. */
    @Override public void solids(Fx c,FxSolids s){
        float t=c.sec;
        if(t>=D&&t<D+1.3f)FxLib.chunks(s,c.origin,t-D,12,1301,3.5,6,.18,.8f);
        if(t>=ARRIVE&&t<ARRIVE+1.2f)FxLib.chunks(s,floor(c),t-ARRIVE,12,1351,3,5.5,.17,.7f);
        if(t<CUT||t>=CUT+1.4f)return;Vec3d end=floor(c);
        FxLib.chunks(s,end,t-CUT,32,1401,7,8,.22,.9f);
        FxLib.heave(s,end,t-CUT,12,.45,18,1.3,1.4);
    }
    @Override public float flash(Fx c){
        // The ring passing a nearby camera tints the view violet for a moment (on the effect layer the impact frame owns the cut).
        float t=c.sec;if(t<SWEEP||t>=CUT+.5f)return 0;
        if(FxLayer.available()&&t>=CUT-.02f&&t<CUT+.13f)return 0;
        double d=Math.hypot(c.camera.x-c.pos.x,c.camera.z-c.pos.z),r=ringRadius(c,t,closed(c));
        return d>18?0:.3f*(float)Math.max(0,1-Math.abs(r-d)/3)*round(c,t)*(1-Ease.smooth(t,CUT+.15f,CUT+.5f));
    }
    @Override public int flashColor(Fx c){return 0x6a10d0;}

    // ───────────────────────────── camera ─────────────────────────────

    /** Shake with each slash, the dash's burst, the arrival and the burst after it, and hardest with the spin cut; each comes in over two frames. */
    @Override public float shake(StandClient.Cast cast,float sec){
        float s=0;for(float at:SLASH)s=Math.max(s,.45f*ComboSkill.bump(sec,at,.07f));
        s=Math.max(s,Math.max(1.1f*ComboSkill.bump(sec,D,.1f),1.5f*ComboSkill.bump(sec,ARRIVE,.12f)));
        return Math.max(s,Math.max(.9f*ComboSkill.bump(sec,BURST,.1f),2.6f*ComboSkill.bump(sec,CUT,.16f)));
    }
    /** The camera rides the cuts (rolling with each), is pushed by the dash, nods with the arrival and twists with the spin. */
    static final float[][] CUT_KICKS={{.5f,.2f,-.8f},{-.6f,.3f,.9f},{.7f,.5f,-1.1f}};
    @Override public float[] kick(StandClient.Cast cast,float sec){
        if(sec<SLASH[0]-.02f||sec>=CUT+.7f)return null;
        float[] k=new float[3];
        for(int i=0;i<SLASH.length;i++){float b=ComboSkill.bump(sec,SLASH[i],.08f);for(int j=0;j<3;j++)k[j]+=CUT_KICKS[i][j]*b;}
        k[1]+=1.2f*ComboSkill.bump(sec,ARRIVE,.12f)-.7f*ComboSkill.bump(sec,D,.12f);
        float b=ComboSkill.bump(sec,CUT,.16f);k[0]-=1.4f*b;k[1]+=.8f*b;
        return k;
    }
}
