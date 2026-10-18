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
 * T held "어둠을 가로지르다", remade in 2.38.0 (user request): the left hand summons a little fire on the floor just in
 * front, burning for about 2 s, and the blade catches; three fire slashes are cut and thrown up the line (a level cut, a falling backhand and a big falling forehand, at
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

    /**
     * The left hand summons the fire (user request): thrust out and down at the floor just in front, then lifted as the
     * fire rises from it. The sword hand stays as it is, low at the right side, until it throws the slashes.
     */
    static final R6.Pose SUMMON=pose().body(8,10,0).at(0,-.1,0).head(-18,13,0).rArm(-78,16).lArm(-10,-14).rLeg(-84,18).lLeg(-82,-20).blade(-36,58);
    static final R6.Pose RAISE=pose().body(4,6,0).at(0,-.12,0).head(-14,-10,0).rArm(-76,18).lArm(8,-20).rLeg(-82,22).lLeg(-82,-22).blade(-34,60);
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
        k(.13,SUMMON),
        k(.27,RAISE),
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

    /**
     * Each slash: the roll of its plane (degrees, its right side up), its radius as it leaves and at the end of its
     * flight, its arc (degrees, signed from its tail to its head toward its right) and the height of its middle.
     */
    static final double[] ROLL={0,-28,28},R0={2.8,2.8,3.3},R1={3.6,3.6,4.0},ARC={-140,140,-150},LIFT={1.5,1.95,2.0};
    /** How far each slash's plane leans back up from level (degrees), so a camera behind the caster sees its face and not its edge. */
    static final double LEAN=22;
    /** Slash {@code i}'s plane: {its forward, leaned back up; its side, the caster's right turned by its roll and square to the forward}. */
    static Vec3d[] slashPlane(Vec3d f,Vec3d right,int i){
        double p=Math.toRadians(LEAN),a=Math.toRadians(ROLL[i]);Vec3d fw=f.multiply(Math.cos(p)).add(0,Math.sin(p),0);
        Vec3d s=right.multiply(Math.cos(a)).add(0,Math.sin(a),0);
        return new Vec3d[]{fw,s.subtract(fw.multiply(s.dotProduct(fw))).normalize()};
    }
    static Vec3d[] slashPlane(Fx c,int i){return slashPlane(c.f,c.right,i);}
    /** How far up the line slash {@code i} has flown at {@code t} (blocks; the server's), or -1 before it leaves. */
    static double flown(int i,float t){return KagenoRules.rushSlash(i,t*20);}
    static double radius(int i,float t){return R0[i]+(R1[i]-R0[i])*Ease.out(t-SLASH[i],0,FLIGHT);}
    /** The middle (front) of slash {@code i}'s arc with it {@code d} blocks up the line. */
    static Vec3d front(Fx c,int i,double d){return c.origin.add(c.f.multiply(d)).add(0,LIFT[i],0);}
    /** Where the point {@code deg} degrees round slash {@code i}'s arc from its middle lies from its middle, the arc {@code r} across. */
    static Vec3d arcOffset(Vec3d[] plane,double r,double deg){double a=Math.toRadians(deg);return plane[0].multiply((Math.cos(a)-1)*r).add(plane[1].multiply(Math.sin(a)*r));}
    static Vec3d onArc(Fx c,int i,double d,double r,double deg){return front(c,i,d).add(arcOffset(slashPlane(c,i),r,deg));}
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
    /** Where slash {@code i} ends: blocks up the line, and on whom (null: it flew to the end of the range). */
    record End(double at,LivingEntity on){}
    private static final Map<Long,End> ENDS=new LinkedHashMap<>();
    /**
     * Where slash {@code i} ends, as the server ends it (user request: a slash does not cut through): on the first one
     * standing in its lane ahead of it, or at the end of the range. Held from the moment it gets there, so whoever it
     * struck moving off later does not send it on. Only those still ahead of it (or just passed this frame) count.
     */
    static End slashEnd(Fx c,int i){
        long key=c.cast.id().getLeastSignificantBits()*31+i;var held=ENDS.get(key);if(held!=null)return held;
        double from=Math.max(-.5,flown(i,c.sec)-5),reach=RANGE;LivingEntity first=null;
        for(var e:inPath(c)){double x=along(c,e.getLerpedPos(c.delta));if(x>=from&&x<reach){first=e;reach=x;}}
        var end=new End(first==null?RANGE:Math.max(1,reach),first);
        if(flown(i,c.sec)>=end.at()-1e-6){ENDS.put(key,end);while(ENDS.size()>48)ENDS.remove(ENDS.keySet().iterator().next());}
        return end;
    }
    /** When slash {@code i} gets to its end, and the point it bursts at there (on whom it struck, or in the air at the end of the range). */
    static float endsAt(Fx c,int i){return passes(i,slashEnd(c,i).at());}
    static Vec3d endPoint(Fx c,int i){var end=slashEnd(c,i);return end.on()!=null?chest(c,end.on()):front(c,i,end.at());}
    /** When {@code e} was first struck: by the first slash if it stands on the line, else by the arrival. */
    static float struck(Fx c,LivingEntity e){Vec3d p=e.getLerpedPos(c.delta);return onLine(c,p)?passes(0,along(c,p)):ARRIVE;}
    /** The left hand on the caster as drawn now (the torso's turn taken, its lean left out). */
    static Vec3d leftHand(Fx c){
        var p=R6.poseOf(KagenoClips.action(c.cast,c.delta));double a=Math.toRadians(p.yaw);
        Vec3d fw=c.f.multiply(Math.cos(a)).add(c.left.multiply(Math.sin(a))),rt=c.right.multiply(Math.cos(a)).add(c.f.multiply(Math.sin(a)));
        Vec3d arm=rt.multiply(p.lArm.rt()).add(0,p.lArm.up(),0).add(fw.multiply(p.lArm.fw()));
        return c.pos.add(fw.multiply(p.fw)).add(0,1.375+p.up,0).subtract(rt.multiply(.3125)).add(arm.multiply(.7));
    }
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
    /**
     * The spin's slash of fire: its tail and head (degrees). Its head rides the blade round; it is never more than
     * {@link #RING_ARC} long, so it reads as one slash going round, not a closed disc.
     */
    static double[] ring(Fx c,float t){
        double start=bladeAngle(c,SWEEP),head=bladeAngle(c,Math.min(t,RING_END));
        return new double[]{Math.max(start,head-RING_ARC),head};
    }
    static final double RING_ARC=200;
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
     * passes, leaves at the server's time, grows as it flies up the line and is gone where it ends, on the first one
     * it reaches or 30 blocks out, eaten from its tail. The spin's one slash of fire goes round with the blade, widest at its head and thin at its tail,
     * and once it has gone round it is thrown out, eaten as it goes.
     */
    @Override public void crescents(Fx c){
        float t=c.sec;
        for(int i=0;i<SLASH.length;i++){
            float age=t-SLASH[i],end=endsAt(c,i);if(age<-FORM||t>=end+.07f)continue;
            double d=Math.max(1,Math.min(slashEnd(c,i).at(),flown(i,t))),r=radius(i,Math.max(t,SLASH[i]));
            float grow=Ease.out(age,-FORM,0),gone=Ease.smooth(t,end-.01f,end+.06f);
            var p=slashPlane(c,i);Vec3d o=front(c,i,d).subtract(p[0].multiply(r));double arc=ARC[i]*Math.max(.05,grow);
            KagenoFire.crescent(o,p[0],p[1],r*(.8+.2*grow),r*.6,-ARC[i]/2,arc,15,i*5+3,gone,.5f,Ease.smooth(age,-FORM,-FORM+.017f)*(1-.4f*gone),.15);
        }
        if(t>=SWEEP-.01f&&t<2.15f){
            float closed=closed(c);double[] r=ring(c,t);if(r[1]-r[0]<4)return;
            var p=plane(c,5);double outer=ringRadius(c,t,closed);
            float eaten=t<closed?0:Ease.smooth(t,closed+.02f,closed+.36f),a=Ease.smooth(t,SWEEP-.01f,SWEEP+.01f)*(1-Ease.smooth(t,closed+.24f,closed+.4f));
            KagenoFire.crescent(floor(c).add(0,.95,0),p[0],p[1],outer,Math.min(1.9,outer*.34),r[0],r[1]-r[0],15,9,eaten,1,a,.25);
        }
    }

    // ───────────────────────────── light ─────────────────────────────

    /** A soft flash: a coloured halo round a hot core, with no rays (a long cross of light would read as more slashes); thinned out close to the camera. */
    static void bloom(Fx c,EffectMesh m,Vec3d p,double r,int rgb,float a){
        a*=near(c,p,r);if(a<=.005f||r<=0)return;
        m.softDisc(p,r,rgb,.4f*a);m.softDisc(p,r*.5,rgb,.65f*a);m.softDisc(p,r*.22,HOT,a);
    }
    /** How much of something {@code size} across at {@code p} to draw: all of it from a few blocks off the camera, none of it on the lens. */
    static float near(Fx c,Vec3d p,double size){return Ease.smooth((float)(c.camera.distanceTo(p)-size),.4f,2.6f);}
    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;
        bladeLight(c,m,t);
        for(int i=0;i<SLASH.length;i++)slashLight(c,m,t,i);
        frontLight(c,m,t);
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
        float end=endsAt(c,i);
        if(age<.07f)bloom(c,m,front(c,i,1.3),1.3,MAGENTA,1-Ease.smooth(age,.01f,.07f));
        if(age>=0&&t<end)bloom(c,m,front(c,i,flown(i,t)).add(slashPlane(c,i)[0].multiply(-radius(i,t)*.3)),radius(i,t)*.55,MAGENTA,.45f);
        float e=t-end;
        if(e>=0&&e<.5f){
            // Where it ends: a burst of light on whom it struck, or in the air at the end of the range.
            Vec3d p=endPoint(c,i);boolean on=slashEnd(c,i).on()!=null;float a=1-Ease.smooth(e,.08f,.5f);
            bloom(c,m,p,(on?1.8:2.6)+(on?1.2:2)*Ease.out(e,0,.15f),MAGENTA,a);
            FxLib.ring(m,new Vec3d(p.x,c.origin.y+.06,p.z),.5+(on?3.5:6)*Ease.out(e,0,.4f),.7,VIOLET,.7f*(1-Ease.unit(e,0,.45f)));
        }
    }
    /** Under the fire that rises in front of the caster as the skill begins: a warm glow on the floor and sparks. */
    static void frontLight(Fx c,EffectMesh m,float t){
        float palm=Ease.smooth(t,.03f,.12f)*(1-Ease.smooth(t,.26f,.38f));if(palm>0)bloom(c,m,leftHand(c),.32+.18*palm,PINK,.8f*palm);
        float a=front(t);if(a<=0)return;Vec3d at=frontSpot(c);
        FxLib.glow(m,at.add(0,.03,0),FRONT_WIDTH*.75,PINK,.45f*a);
        FxLib.embers(m,at,FRONT_WIDTH*.35,frontHeight(t)*1.2,28,t,1.5f,.07,0xffb8f0,a,17);
    }
    /** The burst the dash leaves from: a pink-violet flash and shock rings racing out over the floor. */
    static void launch(Fx c,EffectMesh m,float t){
        if(t<D-.01f||t>=D+.5f)return;
        Vec3d o=c.origin.add(0,.9,0),floor=c.origin.add(0,.06,0);float a=Ease.smooth(t,D-.01f,D+.01f);
        if(t<D+.16f)bloom(c,m,o,1.8+1.4*Ease.out(t,D,D+.08f),MAGENTA,(FxLayer.available()?.75f:1)*(1-Ease.smooth(t,D+.03f,D+.16f)));
        FxLib.ring(m,floor,.5+3.8*Ease.out(t,D,D+.35f),.6,PINK,.55f*a*(1-Ease.unit(t,D,D+.35f)));
        FxLib.ring(m,floor,.3+2.4*Ease.out(t,D,D+.2f),.3,WHITE,.45f*a*(1-Ease.unit(t,D,D+.2f)));
    }
    /** The dash: the body a comet of fire, rings of light popping round its path as it passes. */
    static void dashLight(Fx c,EffectMesh m,float t){
        if(t<D||t>=ARRIVE+.4f)return;
        float body=Ease.smooth(t,D,D+.02f)*(1-Ease.smooth(t,ARRIVE-.02f,ARRIVE+.06f));Vec3d at=c.pos.add(0,.9,0),h=heading(c);
        if(body>0){bloom(c,m,at,1.3,MAGENTA,.6f*body);bloom(c,m,at.add(h.multiply(.4)),.6,PINK,.6f*body);}
    }
    /** The arrival: a flash and a shock ring where he lands, a flash on whom he came for, and the fire bursting on them a moment later. */
    static void arrival(Fx c,EffectMesh m,float t){
        if(t<ARRIVE-.01f||t>=BURST+.5f)return;
        Vec3d o=c.pos.add(0,.9,0);float a=Ease.smooth(t,ARRIVE-.01f,ARRIVE+.01f);
        if(t<ARRIVE+.2f)bloom(c,m,o,2.2+1.6*Ease.out(t,ARRIVE,ARRIVE+.08f),MAGENTA,(FxLayer.available()?.75f:1)*(1-Ease.smooth(t,ARRIVE+.04f,ARRIVE+.2f)));
        FxLib.ring(m,floor(c).add(0,.06,0),.5+5*Ease.out(t,ARRIVE,ARRIVE+.35f),.7,PINK,.6f*a*(1-Ease.unit(t,ARRIVE,ARRIVE+.35f)));
        for(var e:c.targets){
            Vec3d p=chest(c,e);float k=FxLib.kick(t,ARRIVE,.08f),b=FxLib.kick(t,BURST,.12f);
            if(k>.01f)bloom(c,m,p,1.6*k+.4,PINK,k);
            if(b>.01f)bloom(c,m,p,2.6*b+.6,MAGENTA,b);
        }
    }
    /** The coil before the spin: light drawn into the body and a circle of fire closing in over the floor. */
    static void windup(Fx c,EffectMesh m,float t){
        if(t<BURST-.02f||t>=CUT+.05f)return;
        float a=Ease.smooth(t,BURST-.02f,BURST+.08f)*(1-Ease.smooth(t,SWEEP-.06f,SWEEP));Vec3d o=c.pos.add(0,1,0);
        FxLib.converge(m,o,3.4,26,t,BURST,CUT-.06f,0xffb8f0,77);
        FxLib.ring(m,floor(c).add(0,.05,0),2+4.2*(1-Ease.in(t,BURST,CUT)),.4,PINK,.5f*a);
        FxLib.glow(m,floor(c).add(0,.04,0),5,DEEP,.35f*a);
        m.softDisc(o,.8+.6*Ease.smooth(t,BURST,CUT),MAGENTA,.4f*a);
    }
    /** The spin cut: a blinding flash and a pillar of light at the body and shock rings racing out over the floor. */
    static void cut(Fx c,EffectMesh m,float t){
        if(t<CUT-.01f||t>=CUT+.6f)return;
        Vec3d pos=c.pos,o=floor(c).add(0,.06,0);float a=Ease.smooth(t,CUT-.01f,CUT+.01f);
        if(t<CUT+.14f)bloom(c,m,pos.add(0,1,0),3.4,MAGENTA,(FxLayer.available()?.8f:1)*(1-Ease.unit(t,CUT,CUT+.14f)));
        FxLib.column(m,pos,10*Ease.out(t,CUT,CUT+.06f),.6*(1-Ease.unit(t,CUT,CUT+.4f))+.1,0xe0a8ff,a*(1-Ease.smooth(t,CUT+.05f,CUT+.4f)));
        FxLib.ring(m,o,.5+13*Ease.out(t,CUT,CUT+.5f),.8,WHITE,.5f*a*(1-Ease.unit(t,CUT,CUT+.5f)));
        FxLib.ring(m,o,.4+9*Ease.out(t,CUT,CUT+.4f),1.1,VIOLET,.7f*a*(1-Ease.unit(t,CUT,CUT+.4f)));
    }

    // ───────────────────────────── fire, smoke and dust ─────────────────────────────

    /** One fire cell: {size, alpha, heat, wisp, seed}. */
    static void cell(List<Vec3d> pts,List<float[]> cells,Vec3d p,double size,float alpha,double heat,double wisp,int seed){cell(pts,cells,p,size,alpha,heat,wisp,seed,1);}
    /** A fire cell stretched {@code tall} times upward: a tongue of flame. */
    static void cell(List<Vec3d> pts,List<float[]> cells,Vec3d p,double size,float alpha,double heat,double wisp,int seed,double tall){
        if(alpha<=.005f||size<=.01)return;pts.add(p);cells.add(new float[]{(float)size,alpha,(float)heat,(float)wisp,seed,(float)tall});
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
        frontFire(c,t,pts,cells);
        for(int i=0;i<SLASH.length;i++)slashFire(c,t,i,pts,cells);
        struckFire(c,t,pts,cells);
        dashFire(c,t,pts,cells);
        vortex(c,t,pts,cells);
        cutFire(c,t,pts,cells);
        // Fire that would fill the lens (the camera trailing the dash runs through what it left) thins out close to it.
        for(int i=0;i<pts.size();i++){float[] q=cells.get(i);q[1]*=near(c,pts.get(i),q[0]);}
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
    /**
     * Where the summoned fire rises: on the floor in front of the caster, toward the left hand that summons it (a fire
     * straight ahead hid behind his body from a camera behind him); his dash runs through its right side.
     */
    static Vec3d frontSpot(Fx c){return c.origin.add(c.f.multiply(FRONT_AHEAD)).add(c.left.multiply(FRONT_LEFT));}
    static final double FRONT_AHEAD=3.2,FRONT_LEFT=1.6;
    /**
     * Its full height and width (user request, with a picture of a fire rising far above the caster and filling the
     * view): three times a player's 1.8 blocks, and two and a half times the 2.8 blocks of the one before.
     */
    static final double FRONT_HEIGHT=5.4,FRONT_WIDTH=7.0;
    /** How much of it burns at {@code t} (user request): it flares up as the left hand reaches out and is gone about 2 s later. */
    static float front(float t){return t<.07f||t>=FRONT_END?0:Ease.smooth(t,.07f,.18f)*(1-Ease.smooth(t,FRONT_END-.35f,FRONT_END));}
    static final float FRONT_END=2.1f;
    /** How tall it stands at {@code t}: it bursts up out of the floor as the hand points and sinks as it dies. */
    static double frontHeight(float t){return FRONT_HEIGHT*(.2+.8*Ease.out(t,.07f,.3f))*(1-.5*Ease.smooth(t,FRONT_END-.45f,FRONT_END));}
    /** How far across it has spread at {@code t}: it bursts up narrower and spreads as it rises. */
    static double frontSpread(float t){return .55+.45*Ease.out(t,.07f,.34f);}
    /** The summoned fire's great tongues, left to right as the caster sees them: across (toward his left) and ahead of its middle, height and lean outward at the top (blocks). */
    static final double[][] FRONT_TONGUES={{3.1,.5,.65,.8},{1.6,-.3,.85,.5},{.1,.4,1,.1},{-1.4,-.5,.85,-.45},{-2.9,.2,.6,-.8}};
    /**
     * The fire the left hand summons as T held begins, a great blaze like the user's picture: five great tongues of
     * flame apart from each other (user request: not stuck together), joined at their white-hot feet and parting as
     * they rise, each leaning out and swaying, cooling from pink through magenta and purple to indigo at its tip, the
     * middle one the tallest; and flames licking out over the floor from its ends. Each tongue is three tall flames
     * climbing it one after another, so it burns as one long flame rather than a string of puffs. It bursts up as the
     * hand points and dies down about 2 s later.
     */
    static void frontFire(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        float a=front(t);if(a<=0)return;Vec3d at=frontSpot(c);double h=frontHeight(t),spread=frontSpread(t),grown=.45+.55*h/FRONT_HEIGHT;
        for(int k=0;k<FRONT_TONGUES.length;k++){
            double[] g=FRONT_TONGUES[k];double top=h*g[2];
            Vec3d foot=at.add(c.left.multiply(g[0]*spread)).add(c.f.multiply(g[1]));
            // Its foot, white-hot.
            float fl=(float)(.85+.15*Math.sin(t*9+k*2.3));
            cell(pts,cells,foot.add(0,.65,0),1.25*grown*fl,a*.95f,.97,.3,k*23+71);
            for(int j=0;j<3;j++){
                float life=(float)(.55+.15*g[2]),cyc=t/life+j/3f+(float)Ease.hash(k*5+72);int q=(int)Math.floor(cyc);float u=cyc-q;
                double size=(1.2-.5*u)*(.9+.2*Ease.hash(k*13+q*7+j))*grown,tall=1+.9*u,sway=.3*u*Math.sin(t*4.1+k*1.9+q);
                // Its middle climbs from just above the foot to where its tip reaches the tongue's top.
                double y=.9+Math.max(0,top-.9-.7*size*tall)*u;
                Vec3d p=foot.add(c.left.multiply(g[3]*u*u*grown+sway)).add(c.f.multiply(.2*Math.sin(t*3.3+k)*u)).add(0,y,0);
                cell(pts,cells,p,size,Ease.smooth(u,0,.1f)*(1-Ease.smooth(u,.65f,1))*a,.93-.6*u,.4+.5*u,k*29+j*7+q*13+95,tall);
            }
        }
        // Flames licking out over the floor from its ends.
        for(int k=0;k<2;k++){
            double[] g=FRONT_TONGUES[k==0?0:FRONT_TONGUES.length-1];
            float life=.55f,cyc=t/life+.37f*k;int q=(int)Math.floor(cyc);float u=cyc-q;
            Vec3d p=at.add(c.left.multiply((g[0]+Math.signum(g[0])*(.3+.9*Ease.out(u,0,1)))*spread)).add(c.f.multiply(g[1])).add(0,.6+.9*u,0);
            cell(pts,cells,p,(.85-.4*u)*grown,Ease.smooth(u,0,.15f)*(1-Ease.smooth(u,.5f,1))*a,.85-.45*u,.75,k*31+q*17+105,1.4);
        }
    }
    /**
     * A slash's fire: flames shed from its arc as it flies (left behind where it passed, rising and cooling), flames
     * running along the floor under its line, and the burst of fire where it ends.
     */
    static void slashFire(Fx c,float t,int i,List<Vec3d> pts,List<float[]> cells){
        float age=t-SLASH[i];if(age<0||age>=FLIGHT+.8f)return;
        int n=i==2?14:12,seed=i*997+11;double last=slashEnd(c,i).at();
        for(int k=0;k<n;k++){
            double d=3+(k+.6*Ease.hash(seed+k))*(RANGE-3)/n;if(d>last)break;float born=passes(i,d),life=.18f+.14f*(float)Ease.hash(seed+k*3),s=t-born;if(s<0||s>=life)continue;float u=s/life;
            double r=radius(i,born)*(.72+.12*Ease.hash(seed+k*5)),deg=(Ease.hash(seed+k*7)-.5)*Math.abs(ARC[i])*.7;
            Vec3d p=onArc(c,i,d,r,deg).add(0,.5*u,0);
            cell(pts,cells,p,(.3+.2*Ease.hash(seed+k*11))*(.8+.6*Ease.out(u,0,.6f))*(i==2?1.15:1),Ease.smooth(u,0,.08f)*(1-Ease.smooth(u,.45f,1)),1-.6*u,.75,seed+k*29);
        }
        for(int k=0;k<7;k++){
            double d=4.5+(k+.5*Ease.hash(seed+k*13+1))*(RANGE-4.5)/7;if(d>last)break;float born=passes(i,d)+.02f,life=.35f+.15f*(float)Ease.hash(seed+k*17),s=t-born;if(s<0||s>=life)continue;float u=s/life;
            Vec3d p=c.origin.add(c.f.multiply(d)).add(c.right.multiply((Ease.hash(seed+k*19)-.5)*1.2)).add(0,.3+.4*u,0);
            cell(pts,cells,p,(.35+.15*Ease.hash(seed+k*23))*(1-.2*u),Ease.smooth(u,0,.1f)*(1-Ease.smooth(u,.5f,1)),.9-.5*u,.95,seed+k*37+5);
        }
        // It bursts into fire where it ends, on whom it struck or at the end of the range.
        boolean on=slashEnd(c,i).on()!=null;
        burst(pts,cells,endPoint(c,i),t,endsAt(c,i),on?8:10,on?4:6,on?.55:.8,1,seed+400);
    }
    /** Small fires burning on whoever is held, and the burst on them after the arrival. */
    static void struckFire(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        if(t<SLASH[0]||t>=BURST+.8f)return;int n=0;
        for(var e:c.targets){
            Vec3d p=chest(c,e);float from=struck(c,e)+.05f;
            if(t>=from&&t<CUT)for(int k=0;k<3;k++){
                float life=.4f,cyc=(t-from)/life+(float)Ease.hash(n*7+k);int q=(int)Math.floor(cyc);float u=cyc-q;
                cell(pts,cells,p.add((Ease.hash(n*11+k*3+q)-.5)*.7,-.4+.9*u,(Ease.hash(n*13+k*5+q)-.5)*.7),.3+.15*Ease.hash(k+q),Ease.smooth(u,0,.2f)*(1-Ease.smooth(u,.5f,1))*(1-Ease.smooth(t,CUT-.1f,CUT)),.9-.4*u,.9,n*41+k*7+q);
            }
            burst(pts,cells,p,t,BURST,10,5,.6,1,n*53+601);
            n++;
        }
    }
    /**
     * The dash: a burst of fire where it leaves, flames shed where the body passed (as this client drew it), fire
     * running along the floor of its path, a fiery head round the body, and the explosion where he arrives.
     */
    static void dashFire(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        if(t<D-.01f||t>=ARRIVE+1.0f)return;
        burst(pts,cells,c.origin.add(0,.5,0),t,D,10,4.5,.55,.6,701);
        int k=0;
        for(var s:stations(c,1.1)){
            float life=.25f+.15f*(float)Ease.hash(k*3+711),age=t-(float)s[0];k++;if(age<0||age>=life)continue;float u=age/life;
            Vec3d p=new Vec3d(s[1],s[2],s[3]).add((Ease.hash(k*5+712)-.5)*.6,.5+.9*Ease.hash(k*7+713)+.4*u,(Ease.hash(k*11+714)-.5)*.6);
            cell(pts,cells,p,(.35+.25*Ease.hash(k*13+715))*(.8+.5*Ease.out(u,0,.5f)),Ease.smooth(u,0,.08f)*(1-Ease.smooth(u,.45f,1)),1-.6*u,.7,k*19+716);
        }
        k=0;
        for(var s:stations(c,2.4)){
            float life=.45f+.15f*(float)Ease.hash(k*3+731),age=t-(float)s[0]-.03f;k++;if(age<0||age>=life)continue;float u=age/life;
            cell(pts,cells,new Vec3d(s[1],s[2],s[3]).add((Ease.hash(k*5+732)-.5)*.9,.25+.4*u,(Ease.hash(k*7+733)-.5)*.9),(.4+.15*Ease.hash(k*11+734))*(1-.2*u),Ease.smooth(u,0,.1f)*(1-Ease.smooth(u,.5f,1)),.9-.5*u,.95,k*23+735);
        }
        float body=Ease.smooth(t,D,D+.02f)*(1-Ease.smooth(t,ARRIVE-.03f,ARRIVE+.05f));
        if(body>0){Vec3d h=heading(c),across=new Vec3d(-h.z,0,h.x);
            for(int q=0;q<4;q++)cell(pts,cells,c.pos.subtract(h.multiply(.3+.5*q)).add(0,.6+.7*Ease.hash(q*3+741),0).add(across.multiply((Ease.hash(q*5+742)-.5)*.6)),.45-.06*q,body*(1-q/5f),1-.08*q,.6,q*29+743);}
        burst(pts,cells,floor(c).add(0,.5,0),t,ARRIVE,12,6,.7,.5,751);
    }
    /** The coil before the spin: fire drawn round and in to the body, heating as it gathers. */
    static void vortex(Fx c,float t,List<Vec3d> pts,List<float[]> cells){
        if(t<BURST||t>=CUT+.02f)return;Vec3d o=c.pos;
        for(int k=0;k<14;k++){
            float born=BURST+(CUT-BURST-.24f)*(float)Ease.hash(k*3+801),life=.24f,u=(t-born)/life;if(u<0||u>=1)continue;
            double az=Ease.hash(k*5+802)*Math.PI*2-u*2.6,r=4.2*(1-u)*(1-.3*u)+.5,h=.4+1.2*Ease.hash(k*7+803)+.3*u;
            cell(pts,cells,o.add(Math.cos(az)*r,h,Math.sin(az)*r),.42*(1-.4*u),Ease.smooth(u,0,.2f)*(1-Ease.smooth(u,.8f,1)),.6+.4*u,.6,k*31+804);
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
            for(int k=0;k<16;k++){
                double at=r[0]+(r[1]-r[0])*(k+.5)/16;float life=.25f+.15f*(float)Ease.hash(k*3+901),s=(float)((r[1]-at)/4000)+(t<closed?0:t-closed);
                if(s>=life)continue;float u=s/life;double rad=ringRadius(c,t,closed)*(.72+.1*Ease.hash(k*5+902)),a=Math.toRadians(at);
                cell(pts,cells,o.add(Math.cos(a)*rad,.9+.3*Ease.hash(k*7+903)+.4*u,Math.sin(a)*rad),(.4+.2*Ease.hash(k*11+904))*(1+.4*u),Ease.smooth(u,0,.06f)*(1-Ease.smooth(u,.5f,1))*(1-Ease.smooth(t,closed+.3f,closed+.5f)),1-.6*u,.7,k*37+905);
            }
        }
        if(t>=CUT&&t<CUT+.9f){
            for(int k=0;k<7;k++){
                float s=t-CUT-.02f*k,life=.45f;if(s<0||s>=life)continue;float u=s/life;
                cell(pts,cells,o.add((Ease.hash(k*3+921)-.5)*.5,.4+1.1*k+2.2*u,(Ease.hash(k*5+922)-.5)*.5),(.7-.04*k)*(1+.4*u),Ease.smooth(u,0,.06f)*(1-Ease.smooth(u,.4f,1)),1.05-.6*u,.4,k*41+923);
            }
            burst(pts,cells,o.add(0,.9,0),t,CUT,18,10,.6,.15,931);
        }
        if(t>=CUT+.05f){
            float dying=1-Ease.smooth(t,END-.7f,END-.05f);
            for(int k=0;k<28;k++){
                float born=CUT+.05f+.2f*(float)Ease.hash(k*3+951),life=.42f+.25f*(float)Ease.hash(k*5+952),cyc=(t-born)/life;if(cyc<0)continue;
                int q=(int)Math.floor(cyc);float u=cyc-q;
                double az=(k+.6*Ease.hash(k*7+q*3))*Math.PI*2/28,rad=5.2+1.0*Ease.hash(k*11+q*5);
                cell(pts,cells,o.add(Math.cos(az)*rad,.25+.6*u,Math.sin(az)*rad),(.42+.25*Ease.hash(k*13+q))*(1-.25*u)*(.6+.4*dying),Ease.smooth(u,0,.12f)*(1-Ease.smooth(u,.5f,1))*dying,(.95-.45*u)*(.55+.45*dying),.9,k*43+q*11+953);
            }
            for(int k=0;k<8;k++){
                float born=CUT+.6f+.8f*(float)Ease.hash(k*3+971),life=.9f,u=(t-born)/life;if(u<0||u>=1)continue;
                double az=Ease.hash(k*5+972)*Math.PI*2,rad=5+1.2*Ease.hash(k*7+973);
                cell(pts,cells,o.add(Math.cos(az)*rad,.8+2.2*u,Math.sin(az)*rad),(.6+.5*u)*(.8+.4*Ease.hash(k*11+974)),.45f*Ease.smooth(u,0,.15f)*(1-Ease.smooth(u,.5f,1)),.06+.08*Ease.hash(k*13+975),.2,k*47+976);
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
        // A slash flying 100 blocks a second (and the dash after it) would leave fading copies of itself behind as the
        // afterglow, side by side like more slashes: the afterglow is kept short while they fly.
        boolean flying=false;for(float s:SLASH)flying|=t>=s-.02f&&t<s+FLIGHT+.08f;
        if(flying)f.afterglow(.2f);else if(t>=D-.02f&&t<ARRIVE+.08f)f.afterglow(.35f);
        // The spin's slash sweeps round as fast, and its afterglow would fill the circle in like a disc.
        if(t>=SWEEP-.02f&&t<CLOSED+.45f)f.afterglow(.25f);
        float lit=Ease.smooth(t,.1f,.28f)*(1-Ease.smooth(t,1.8f,2.3f));
        if(lit>0){var hb=blade(c);float flare=0;for(float s:SLASH)flare=Math.max(flare,FxLib.kick(t,s,.1f));f.light(hb[0].add(hb[1].multiply(.8)),0xff6ae0,5,(.9f+1.4f*flare)*lit);}
        float fire=front(t);if(fire>0)f.light(frontSpot(c).add(0,frontHeight(t)*.35,0),0xff6ae0,10,2.2f*fire*(float)(.85+.15*Math.sin(t*29)));
        for(int i=0;i<SLASH.length;i++){
            float age=t-SLASH[i];if(age<-.01f||age>=FLIGHT+.6f)continue;float end=endsAt(c,i);
            if(age>=0&&t<end)f.light(front(c,i,flown(i,t)),0xd04cff,7,1.8f);
            if(age<.2f)f.light(front(c,i,1.2),0xff6ae0,6,2.2f*FxLib.kick(t,SLASH[i],.07f));
            if(t>=end-.01f&&t<end+.6f)f.light(endPoint(c,i),0xe060ff,slashEnd(c,i).on()!=null?7:11,(slashEnd(c,i).on()!=null?2.8f:3.4f)*FxLib.kick(t,end,.15f));
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
            float heat=.8f*(1-Ease.smooth(t,CUT+.08f,CUT+.6f)),a=Ease.smooth(t,CUT,CUT+.05f)*(1-Ease.smooth(t,END-.35f,END));double open=Ease.out(t,CUT,CUT+.12f);
            for(int k=0;k<7;k++){double ang=Math.toRadians(20+360.0/7*k+12*Ease.hash(k*7+151)),reach=k%2==0?1:.7;Vec3d dir=c.f.multiply(Math.cos(ang)).add(c.right.multiply(Math.sin(ang)));
                f.decal(end.add(dir.multiply((3*open+.6)*reach)),dir,(2*open+.2)*reach,k%2==0?.42:.3,heat,a,21.1f+k*3.7f);}
        }
        if(t>=SLASH[0]){
            // The floor under the slashes burnt as far as they got: two lengths, each as the first slash crosses it.
            double far=1;for(int i=0;i<SLASH.length;i++)if(t>=SLASH[i])far=Math.max(far,Math.min(slashEnd(c,i).at(),flown(i,t)));
            float heat=1-Ease.smooth(t,SLASH[2]+.3f,2.2f),fade=1-Ease.smooth(t,END-.35f,END);double mid=Math.min(far,15.5);
            if(mid>3)f.decal(c.origin.add(c.f.multiply((1+mid)/2)),c.f,(mid-1)/2,.55,heat*.9f,.85f*fade,23.9f);
            if(far>16.5)f.decal(c.origin.add(c.f.multiply((15.5+far)/2)),c.f,(far-15.5)/2,.55,heat*.9f,.85f*fade,29.3f);
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
            float age=t-SLASH[i],end=endsAt(c,i);if(age<0||t>=end)continue;
            double r=radius(i,t);var p=slashPlane(c,i);Vec3d o=front(c,i,flown(i,t)).subtract(p[0].multiply(r));
            d.arc(o,p[0],p[1],r+.15,r*.7,-ARC[i]/2,ARC[i],.8f*(1-Ease.smooth(t,end-.06f,end)));
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
            if(r[1]-r[0]>4){var p=plane(c,5);double outer=ringRadius(c,t,closed);d.arc(floor(c).add(0,.95,0),p[0],p[1],outer+.1,Math.min(1.9,outer*.34),r[0],r[1]-r[0],.7f*(1-Ease.smooth(t,closed+.1f,closed+.35f)));}
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
