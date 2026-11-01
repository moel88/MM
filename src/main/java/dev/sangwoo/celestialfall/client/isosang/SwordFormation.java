package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * Lee So-sang's H as drawn (2.82.0, user request: "다음은 H스킬이야 엄청 많은 검을 박고 검 위로 올라가 검 위에 올라가 있을때는
 * 움직 일 수 없어 영상이랑 똑같이 구현해줘 이펙트도", and the user's video of it, read frame by frame on a sheet the user
 * answered: some 39 s, the formation strikes all along, only R up there, 60 s cooldown; then "데미지는 처음 한 번 H 박을때
 * 30데미지 그리고 5초마다 5 데미지"). She crouches with her sword low at her right and leaps some three blocks up, her sword
 * pointing straight down: a thread of lightning runs from its point to the ground and a pool of ink spreads under her,
 * its rim lit blue, while swords show high in the sky, points down. She spreads her arms to call them; the middle one
 * strikes first, a black spike shooting up out of the pool and bursting into blue light, and as she dives back down to
 * the middle the pool swirls out over the whole formation in bands of blue and the swords rain down all round, each
 * trailing blue lightning, each struck into the ground with a flower of blue crystal and a splash of ink at its foot. Ink
 * mountains rise round the formation and a white mist drifts in; the swords, dark as they strike, turn silver with a
 * white glow; she hops up onto the middle sword's pommel and stands there (IsosangClient.drive lifts her, IsosangClient.lift
 * draws the leap and the hop's arc) until the formation ends: every sword flares sky blue, turns to ink and breaks away,
 * the mountains, the mist and the blue of the ground going with them, and she drops back down.
 * <p>The formation lies round its middle as the server laid it (IsosangClient.formation: the ground under her as she
 * began, the way she faced), each sword where the swing's id seeds it (every client the same). Every effect is a function
 * of the swing's clock, so it moves at the render frame rate.
 */
final class SwordFormation {
    private SwordFormation(){}

    // ───────────────────────────── the clock ─────────────────────────────

    static final float LENGTH=IsosangRules.LENGTH[IsosangRules.FORMATION]/20f;
    /** How long a formation that ended early is still drawn after its end (its swords going, the ink they leave drying, IsosangClient.lifeOf). */
    static final float AFTER=2.5f;
    /** The thread of lightning from her sword's point to the ground as she leaps (seconds into H). */
    static final float THREAD0=.22f,THREAD1=.6f;
    /** The pool: spreading under her to POOL_R blocks, then swirling out over the formation to SWIRL_R. */
    static final float POOL0=.22f,POOL1=.6f,SWIRL0=.7f,SWIRL1=1.15f;
    static final double POOL_R=3.2,SWIRL_R=IsosangRules.FORMATION_RADIUS-.4;
    /** The pool and its swirl dry away (the video: the formation's ground clears from 1.6 s, colours back by 2.1 s). */
    static final float DRY0=1.3f,DRY1=1.9f;
    /** The mountains rise; the mist comes in. */
    static final float RISE0=1.0f,RISE1=1.45f,MIST0=1.2f,MIST1=2.6f;
    /** The swords, dark as they strike, turn silver; the crystal flowers at their feet wilt. */
    static final float SILVER0=1.42f,SILVER1=1.8f,WILT0=1.45f,WILT1=1.78f;
    /** As it ends (seconds after its end): the swords flare sky blue (to GLOW), turn to ink (to INKED), and are gone (GONE). */
    static final float GLOW=.08f,INKED=.18f,GONE=.42f;
    /** How long a sword takes to fall from the sky to the ground. */
    static final float FALL=.22f;

    /** When this formation ends (seconds into it): its whole length run, or the server's word that it ended early. */
    static float end(StandClient.Cast c){float e=IsosangClient.formationEnd(c.id());return Float.isNaN(e)?IsosangRules.H_END:Math.min(IsosangRules.H_END,e);}
    /** How far it has gone as it ends: 0 until then; through the flare, the ink and the breaking away (0 to 1). */
    static float going(StandClient.Cast c,float t){return Ease.smooth(t,end(c)+INKED,end(c)+GONE);}

    // ───────────────────────────── the body ─────────────────────────────
    // (the video: she crouches, her sword low at her right; springs up, her sword turned straight down below her right hand,
    // her free arm out to the left; hangs there; spreads both arms to call the swords down; dives back down to the middle
    // and lands in a crouch; stands with her arms spread as they rain; dips and hops up onto the middle sword, lands on its
    // pommel and stands there, her sword held as she holds it, shifting a little now and then; steps off as it ends,
    // drops and lands in a crouch, and rises. The body faces the way she faced, as every hit's.)
    static final R6.Pose H_DIP=w(.04,-.12,-4,6,0,d(-55,40),d(0,60),d(-70,-25),22,-158);
    static final R6.Pose H_CROUCH=w(.06,-.32,-10,15,0,d(-32,72),d(-18,122),d(-36,-48),30,-150);
    static final R6.Pose H_SPRING=w(.04,-.04,-4,2,0,d(-58,40),d(-82,18),d(4,-96),d(-60,12),d(-97,-6));
    static final R6.Pose H_AIR=w(.02,.04,-6,-6,0,d(-42,28),d(-89,8),d(16,-100),d(-52,14),d(-102,-8));
    static final R6.Pose H_AIR2=w(.02,.05,-4,-4,0,d(-36,32),d(-86,14),d(22,-102),d(-58,12),d(-98,-10));
    static final R6.Pose H_CALL=w(.03,.05,0,-8,0,d(8,96),d(-24,112),d(12,-98),d(-62,10),d(-100,-10));
    static final R6.Pose H_CALL2=w(.03,.04,0,-6,0,d(12,94),d(-18,108),d(16,-96),d(-66,10),d(-98,-10));
    static final R6.Pose H_DIVE=w(.04,0,-2,4,0,d(-18,64),d(-56,82),d(0,-82),d(-80,6),d(-88,-6));
    static final R6.Pose H_LAND=w(.06,-.34,-6,15,0,d(-24,70),d(-8,112),d(-22,-70),28,-152);
    static final R6.Pose H_LAND2=w(.06,-.3,-6,13,0,d(-26,72),d(-10,114),d(-20,-72),28,-152);
    static final R6.Pose H_CONDUCT=w(.02,-.05,0,0,0,d(2,86),d(14,100),d(6,-88),22,-158);
    static final R6.Pose H_CONDUCT2=w(.02,-.04,0,-2,0,d(6,84),d(18,98),d(10,-86),22,-158);
    static final R6.Pose H_HOP_DIP=w(.04,-.2,0,9,0,d(-28,60),d(-14,96),d(-30,-62),24,-156);
    static final R6.Pose H_HOP_AIR=w(.02,.02,0,-4,0,d(-36,42),d(-60,40),d(-6,-80),d(-56,10),d(-72,-12));
    static final R6.Pose H_HOP_FALL=w(.01,.02,0,0,0,d(-52,30),d(-30,24),d(-40,-50),d(-82,6),d(-86,-6));
    /** On the pommel: her feet together on it, her sword as she holds it. */
    static final R6.Pose H_ON=w(0,0,0,3,0,d(-62,14),d(18,6),d(-74,-16),d(-90,3),d(-90,-3));
    /** Standing there, shifting a little now and then. */
    static final R6.Pose H_IDLE=w(0,0,3,1,0,d(-68,6),d(26,4),d(-84,-10),d(-90,3),d(-90,-3));
    /** Stepping off as it ends, dropping, landing in a crouch. */
    static final R6.Pose H_STEP=w(.02,0,0,-2,0,d(-50,30),d(10,20),d(-40,-50),d(-74,8),d(-96,-6));
    static final R6.Pose H_FALL=w(.02,0,0,2,0,d(-30,44),d(-6,40),d(-20,-70),d(-84,6),d(-88,-6));
    static final R6.Pose H_TOUCH=w(.05,-.26,0,12,0,d(-38,54),d(-12,82),d(-36,-56),24,-156);
    static final R6.Pose H_TOUCH2=w(.05,-.22,0,10,0,d(-44,40),d(-4,60),d(-48,-46),24,-156);
    /** How often she shifts as she stands there (seconds). */
    static final double SHIFT=3.2;
    static final Anim CLIP=clip();
    private static Anim clip(){
        List<Anim.Key> keys=new ArrayList<>(List.of(
            k(0,STANCE),
            k(.06,H_DIP),
            k(.14,H_CROUCH,Anim.Ease.OUT),
            k(IsosangRules.H_LEAP,H_SPRING,Anim.Ease.OUT),
            k(.32,H_AIR),
            k(.56,H_AIR2),
            k(.68,H_CALL,Anim.Ease.OUT),
            k(IsosangRules.H_DIVE,H_CALL2),
            k(.84,H_DIVE,Anim.Ease.IN),
            k(.89,H_LAND,Anim.Ease.OUT),
            k(.98,H_LAND2),
            k(1.08,H_CONDUCT),
            k(1.15,H_CONDUCT2),
            k(IsosangRules.H_HOP,H_HOP_DIP,Anim.Ease.OUT),
            k(1.33,H_HOP_AIR,Anim.Ease.OUT),
            k(1.49,H_HOP_FALL),
            k(IsosangRules.H_ON+.01,H_ON,Anim.Ease.OUT),
            k(1.8,STANCE)));
        boolean shifted=true;
        for(double t=1.8+SHIFT;t<=IsosangRules.H_END-1;t+=SHIFT){keys.add(k(t,shifted?H_IDLE:STANCE));shifted=!shifted;}
        keys.add(k(IsosangRules.H_END,STANCE));
        keys.add(k(IsosangRules.H_DROP,H_STEP));
        keys.add(k(IsosangRules.H_DROP+.25,H_FALL));
        keys.add(k(IsosangRules.H_DOWN,H_TOUCH,Anim.Ease.OUT));
        keys.add(k(IsosangRules.H_DOWN+.1,H_TOUCH2));
        keys.add(k(LENGTH,STANCE));
        return Anim.joints(keys.toArray(new Anim.Key[0])).after(IsosangSlash::grounded);
    }

    // ───────────────────────────── where it lies ─────────────────────────────

    /** The formation's middle on its ground (the server's word), else where she stood. */
    static Vec3d centre(StandClient.Cast c){var f=IsosangClient.formation(c.id());return f==null?c.origin():f.centre();}
    /** The way she faced as it began. */
    static float yaw(StandClient.Cast c){var f=IsosangClient.formation(c.id());return f==null?c.yaw():f.yaw();}
    /** Her body as drawn: where she is, lifted as the formation's plan has her (IsosangClient.lift). */
    static Vec3d body(Fx c){return c.pos.add(0,IsosangClient.lift(c.cast.caster(),c.delta),0);}
    /** Her sword's hand and blade at {@code t} as drawn (on her lifted body). */
    static Vec3d[] blade(Fx c,float t){return KagenoClips.blade(body(c),c.cast.yaw(),R6.poseOf(CLIP.frame(t)));}

    /**
     * A sword of the formation: where it strikes the ground (forward and right of the middle, blocks), how high its pommel
     * stands over it, how far it leans from upright and which way (degrees), its guard's turn, when it strikes the ground,
     * how high over its place it hangs in the sky, when it shows there, how big the flower at its foot is, and its seed.
     */
    record Blade(double fw,double rt,double height,double tilt,double toward,double spin,float plant,double sky,float shows,double flower,int seed){
        boolean middle(){return fw==0&&rt==0;}
    }
    /** The rings it lies in (the video: some 45 swords out to 10 blocks, in loose rings round the middle one): radius, how many. */
    static final double[] RING_R={3.4,6.2,9.0};
    static final int[] RING_N={8,14,20};
    private static final Map<UUID,Blade[]> LAYOUTS=new LinkedHashMap<>();
    /** The swords of the formation of swing {@code id} (the same on every client: seeded by the id). */
    static Blade[] layout(UUID id){
        var l=LAYOUTS.get(id);if(l!=null)return l;
        int s0=(int)(id.getMostSignificantBits()^(id.getMostSignificantBits()>>>32)^id.getLeastSignificantBits()^(id.getLeastSignificantBits()>>>32));
        List<Blade> out=new ArrayList<>();
        // the middle one: upright, its pommel as high as she stands on it, first to strike
        out.add(new Blade(0,0,IsosangRules.FORMATION_STAND,0,0,180*Ease.hash(s0+1),IsosangRules.H_PLANT,10,IsosangRules.H_SKY,0,s0));
        int n=0;
        for(int r=0;r<RING_R.length;r++){
            double off=Math.PI*2*Ease.hash(s0+11+r);
            for(int i=0;i<RING_N[r];i++){
                int seed=s0+97*(++n);
                double a=off+Math.PI*2*(i+.45*(Ease.hash(seed+2)-.5))/RING_N[r];
                double rad=Math.min(IsosangRules.FORMATION_RADIUS-.5,RING_R[r]+(Ease.hash(seed+3)-.5)*1.0);
                double height=1.25+.5*Ease.hash(seed+4),tilt=2+11*Ease.hash(seed+5),toward=360*Ease.hash(seed+6),spin=180*Ease.hash(seed+7);
                float plant=IsosangRules.H_RAIN0+(IsosangRules.H_RAIN1-IsosangRules.H_RAIN0)*(float)Math.min(1,.12*r+.82*Ease.hash(seed+8));
                double sky=7+5*Ease.hash(seed+9);
                float shows=IsosangRules.H_SKY+(float)Math.min(plant-FALL-IsosangRules.H_SKY-.02f,.24*Ease.hash(seed+10));
                out.add(new Blade(Math.cos(a)*rad,Math.sin(a)*rad,height,tilt,toward,spin,plant,sky,shows,.8+.45*Ease.hash(seed+11),seed));
            }
        }
        l=out.toArray(new Blade[0]);
        LAYOUTS.put(id,l);while(LAYOUTS.size()>16)LAYOUTS.remove(LAYOUTS.keySet().iterator().next());
        return l;
    }
    /** Where a sword strikes the ground (world). */
    static Vec3d foot(Vec3d centre,float yaw,Blade b){return world(centre,yaw,v(b.fw(),0,b.rt()));}
    /** The way a sword points, from its pommel to its point: down, leaning its tilt toward its way. */
    static Vec3d down(Blade b){
        double t=Math.toRadians(b.tilt()),a=Math.toRadians(b.toward());
        return new Vec3d(Math.sin(t)*Math.cos(a),-Math.cos(t),Math.sin(t)*Math.sin(a)).normalize();
    }
    /** A sword's pommel once it has struck: its height up along it from where it strikes the ground. */
    static Vec3d planted(Vec3d centre,float yaw,Blade b){
        Vec3d d=down(b);return foot(centre,yaw,b).subtract(d.multiply(b.height()/Math.max(.5,-d.y)));
    }
    /** How far over where it strikes a sword is at {@code t} (blocks, 0 once struck): hanging in the sky, then falling, faster and faster. */
    static double above(Blade b,float t){
        float from=b.plant()-FALL;if(t>=b.plant())return 0;
        if(t<from)return b.sky()+.06*Math.sin(t*5+b.seed());
        float u=Ease.unit(t,from,b.plant());return b.sky()*(1-u*u)+.06*Math.sin(from*5+b.seed())*(1-u);
    }
    /** How strongly a sword shows at {@code t} (0 to 1): coming in where it hangs, and going as the formation ends. */
    static float shown(StandClient.Cast c,Blade b,float t){return Ease.smooth(t,b.shows(),b.shows()+.08f)*(1-Ease.smooth(t,end(c)+INKED+.02f,end(c)+GONE));}

    // ───────────────────────────── a sword ─────────────────────────────

    /** The formation's sword (the video: a plain silver longsword), along it from its pommel's top (y 0) to its point, blocks. */
    static final double S_LEN=2.2,S_POMMEL=.09,S_GRIP=.4,S_GUARD=.47,S_W=.072,S_W2=.058,S_T=.022,S_POINT=.34,S_GUARD_W=.27,S_GUARD_T=.042,S_GRIP_R=.03;
    enum Part{BLADE,GUARD,GRIP,POMMEL}
    record Face(Vec3d[] v,Vec3d n,Part part){}
    record Edge(Vec3d a,Vec3d b,boolean outline){}
    static final List<Face> FACES=new ArrayList<>();
    static final List<Edge> EDGES=new ArrayList<>();
    static{build();}
    private static Vec3d p(double x,double y,double z){return new Vec3d(x,y,z);}
    /** A face, its normal turned to agree with {@code out} (a rough outward way). */
    private static void face(Part part,Vec3d out,Vec3d... v){
        Vec3d n=v[1].subtract(v[0]).crossProduct(v[2].subtract(v[0]));
        if(n.lengthSquared()<1e-14&&v.length>3)n=v[2].subtract(v[0]).crossProduct(v[3].subtract(v[0]));
        n=n.normalize();if(n.dotProduct(out)<0)n=n.negate();
        FACES.add(new Face(v,n,part));
    }
    private static void build(){
        // the blade: a diamond across (its edges at x = ±w, its ridge at z = ±t), narrowing a little to where its point begins
        double y0=S_GUARD,y1=S_LEN-S_POINT,t1=S_T*.85;
        Vec3d e0=p(S_W,y0,0),r0=p(0,y0,S_T),f0=p(-S_W,y0,0),q0=p(0,y0,-S_T);
        Vec3d e1=p(S_W2,y1,0),r1=p(0,y1,t1),f1=p(-S_W2,y1,0),q1=p(0,y1,-t1),tip=p(0,S_LEN,0);
        face(Part.BLADE,p(1,0,1),e0,e1,r1,r0);face(Part.BLADE,p(-1,0,1),r0,r1,f1,f0);
        face(Part.BLADE,p(-1,0,-1),f0,f1,q1,q0);face(Part.BLADE,p(1,0,-1),q0,q1,e1,e0);
        face(Part.BLADE,p(1,.4,1),e1,tip,r1);face(Part.BLADE,p(-1,.4,1),r1,tip,f1);
        face(Part.BLADE,p(-1,.4,-1),f1,tip,q1);face(Part.BLADE,p(1,.4,-1),q1,tip,e1);
        EDGES.add(new Edge(e0,e1,true));EDGES.add(new Edge(e1,tip,true));EDGES.add(new Edge(f0,f1,true));EDGES.add(new Edge(f1,tip,true));
        EDGES.add(new Edge(r0,r1,false));EDGES.add(new Edge(r1,tip,false));EDGES.add(new Edge(q0,q1,false));EDGES.add(new Edge(q1,tip,false));
        // the guard: a straight bar across, its ends a little swept toward the point
        double g0=S_GRIP,g1=S_GUARD,gw=S_GUARD_W,gt=S_GUARD_T,sweep=.035;
        Vec3d a=p(-gw,g0+sweep,-gt),b=p(gw,g0+sweep,-gt),c=p(gw,g1+sweep,-gt),dd=p(-gw,g1+sweep,-gt);
        Vec3d e=p(-gw,g0+sweep,gt),f=p(gw,g0+sweep,gt),g=p(gw,g1+sweep,gt),h=p(-gw,g1+sweep,gt);
        Vec3d ma=p(0,g0,-gt),mb=p(0,g1,-gt),me=p(0,g0,gt),mf=p(0,g1,gt);
        // (each half its own faces: bent at the middle)
        face(Part.GUARD,p(0,0,-1),a,ma,mb,dd);face(Part.GUARD,p(0,0,-1),ma,b,c,mb);
        face(Part.GUARD,p(0,0,1),e,h,mf,me);face(Part.GUARD,p(0,0,1),me,mf,g,f);
        face(Part.GUARD,p(0,-1,0),a,e,me,ma);face(Part.GUARD,p(0,-1,0),ma,me,f,b);
        face(Part.GUARD,p(0,1,0),dd,mb,mf,h);face(Part.GUARD,p(0,1,0),mb,c,g,mf);
        face(Part.GUARD,p(-1,0,0),a,dd,h,e);face(Part.GUARD,p(1,0,0),b,f,g,c);
        EDGES.add(new Edge(p(-gw,g0+sweep,0),p(0,g0,0),true));EDGES.add(new Edge(p(0,g0,0),p(gw,g0+sweep,0),true));
        EDGES.add(new Edge(p(-gw,g1+sweep,0),p(0,g1,0),true));EDGES.add(new Edge(p(0,g1,0),p(gw,g1+sweep,0),true));
        EDGES.add(new Edge(p(-gw,g0+sweep,0),p(-gw,g1+sweep,0),true));EDGES.add(new Edge(p(gw,g0+sweep,0),p(gw,g1+sweep,0),true));
        // the grip and the pommel: turned, eight sides
        double[][] lathe={{0,.034},{.028,.058},{.066,.052},{S_POMMEL,S_GRIP_R},{S_GRIP,S_GRIP_R}};
        int sides=8;
        for(int i=0;i+1<lathe.length;i++){
            Part part=i==lathe.length-2?Part.GRIP:Part.POMMEL;
            for(int s=0;s<sides;s++){
                double u0=Math.PI*2*s/sides,u1=Math.PI*2*(s+1)/sides,um=(u0+u1)*.5;
                Vec3d p0=p(Math.cos(u0)*lathe[i][1],lathe[i][0],Math.sin(u0)*lathe[i][1]),p1=p(Math.cos(u1)*lathe[i][1],lathe[i][0],Math.sin(u1)*lathe[i][1]);
                Vec3d p2=p(Math.cos(u1)*lathe[i+1][1],lathe[i+1][0],Math.sin(u1)*lathe[i+1][1]),p3=p(Math.cos(u0)*lathe[i+1][1],lathe[i+1][0],Math.sin(u0)*lathe[i+1][1]);
                face(part,p(Math.cos(um),0,Math.sin(um)),p0,p1,p2,p3);
            }
        }
        for(int s=0;s<sides;s++){
            double u0=Math.PI*2*s/sides,u1=Math.PI*2*(s+1)/sides;
            face(Part.POMMEL,p(0,-1,0),p(0,0,0),p(Math.cos(u0)*lathe[0][1],0,Math.sin(u0)*lathe[0][1]),p(Math.cos(u1)*lathe[0][1],0,Math.sin(u1)*lathe[0][1]));
        }
        EDGES.add(new Edge(p(0,0,0),p(0,S_GRIP,0),false));
    }
    /** A sword's frame in the world, pointing along {@code dir} from its pommel, its guard turned {@code spin} degrees: across, along, through. */
    static Vec3d[] frame(Vec3d dir,double spin){
        Vec3d y=dir.normalize(),x0=y.crossProduct(UP);
        if(x0.lengthSquared()<1e-6)x0=new Vec3d(1,0,0);
        x0=x0.normalize();Vec3d x1=y.crossProduct(x0).normalize();double s=Math.toRadians(spin);
        Vec3d x=x0.multiply(Math.cos(s)).add(x1.multiply(Math.sin(s))).normalize(),z=x.crossProduct(y).normalize();
        return new Vec3d[]{x,y,z};
    }
    static Vec3d at(Vec3d pommel,Vec3d[] f,Vec3d p){return pommel.add(f[0].multiply(p.x)).add(f[1].multiply(p.y)).add(f[2].multiply(p.z));}
    private static int colour(Part part){return switch(part){case BLADE->0xd2dbee;case GUARD->0xaab5cd;case GRIP->0x3b4459;case POMMEL->0xbcc6dc;};}
    /** The light the swords are lit by: from above, a little from the side. */
    static final Vec3d LIGHT=new Vec3d(.35,1,.25).normalize();
    /**
     * A sword's body (the blended pass): every face toward the camera, lit from above and brighter at its rim, darkened
     * toward ink ({@code dark}: as it strikes), flared sky blue ({@code flare}) and inked ({@code ink}) as the formation ends.
     */
    static void sword(EffectMesh m,Vec3d camera,Vec3d pommel,Vec3d[] f,float alpha,float dark,float flare,float ink){
        if(alpha<=.005f)return;
        for(var face:FACES){
            Vec3d n=f[0].multiply(face.n().x).add(f[1].multiply(face.n().y)).add(f[2].multiply(face.n().z));
            Vec3d[] w=new Vec3d[face.v().length];Vec3d c=Vec3d.ZERO;
            for(int i=0;i<w.length;i++){w[i]=at(pommel,f,face.v()[i]);c=c.add(w[i]);}
            c=c.multiply(1.0/w.length);
            Vec3d view=camera.subtract(c);double facing=n.dotProduct(view);if(facing<=0)continue;
            double lit=.6+.4*Math.max(0,n.dotProduct(LIGHT)),rim=1-Math.min(1,facing/Math.max(1e-6,view.length()));
            int col=SpiritSword.scale(colour(face.part()),lit);
            col=SpiritSword.mix(col,0xffffff,.28*rim*rim);
            col=SpiritSword.mix(col,0x070b1c,.9*dark);
            col=SpiritSword.mix(col,0x86ecff,.85*flare);
            col=SpiritSword.mix(col,INK,.95*ink);
            float a=alpha*(face.part()==Part.BLADE?.95f:.97f);
            if(w.length==3)m.triangle(w[0],w[1],w[2],col,a);else m.quad(w[0],w[1],w[2],w[3],col,a);
        }
    }
    /**
     * A sword's light (the light pass): white along its outline with a pale glow round it (the video: the swords stood in
     * the formation glow white at their edges), blue at its edges while it is dark, brighter in the sky, and flaring sky
     * blue as the formation ends; none once it is ink.
     */
    static void swordGlow(EffectMesh m,Vec3d pommel,Vec3d[] f,float alpha,float dark,float sky,float flare,float ink){
        float a=alpha*(1-ink);if(a<=.005f)return;
        int edge=dark>.5f?AZURE:WHITE;float strong=.42f+.18f*sky+.5f*flare;
        for(var e:EDGES){
            Vec3d p=at(pommel,f,e.a()),q=at(pommel,f,e.b());
            if(e.outline()){m.line(p,q,.014+.01*flare,edge,Math.min(1,strong)*a);m.softLine(p,q,.1+.25*flare,dark>.5f?BLUE:PALE,(.13f+.04f*sky+.3f*flare)*a);}
            else m.line(p,q,.009,PALE,(.2f+.08f*sky)*a*(1-dark));
        }
        Vec3d base=at(pommel,f,p(0,S_GUARD,0)),tip=at(pommel,f,p(0,S_LEN,0)),top=at(pommel,f,p(0,0,0));
        m.softLine(top,tip,.36,dark>.5f?BLUE:AZURE,(.04f+.03f*sky)*a);
        if(flare>.01f){m.softLine(top,tip,.9,CYAN,.5f*flare*a);m.line(base,tip,.05,WHITE,.85f*flare*a);m.softDisc(top,.45,CYAN,.5f*flare*a);}
    }

    // ───────────────────────────── the ink mountains, the mist ─────────────────────────────

    /**
     * An ink mountain round the formation (the video: dark peaks drawn as with a brush, rimmed in light blue, round it):
     * which way from its middle (radians), how far, how wide, its ridge (heights at RIDGE points across it), its peaks
     * (where along it and how high), its seed.
     */
    record Mountain(double az,double radius,double width,double[] ridge,double[] peakX,double[] peakH,int seed){
        /** The ridge's height {@code x} along it (blocks from its middle). */
        double at(double x){
            double u=(x/width+.5)*(RIDGE-1);if(u<=0)return ridge[0];if(u>=RIDGE-1)return ridge[RIDGE-1];
            int i=(int)u;double f=u-i;return ridge[i]*(1-f)+ridge[i+1]*f;
        }
        double x(int i){return width*(i/(double)(RIDGE-1)-.5);}
    }
    static final int RIDGE=44;
    private static final Map<UUID,Mountain[]> MOUNTAINS=new LinkedHashMap<>();
    /**
     * The mountains of the formation of swing {@code id}: nine round it, each two to four tall peaks with round tops and
     * steep sides running into one another (as ink mountains are painted), a little ragged along the ridge.
     */
    static Mountain[] mountains(UUID id){
        var l=MOUNTAINS.get(id);if(l!=null)return l;
        int s0=(int)(id.getLeastSignificantBits()*31+id.getMostSignificantBits());
        int n=9;l=new Mountain[n];double off=Math.PI*2*Ease.hash(s0+5);
        for(int i=0;i<n;i++){
            int seed=s0+211*(i+1);
            double az=off+Math.PI*2*(i+.35*(Ease.hash(seed+1)-.5))/n,radius=11.8+2.0*Ease.hash(seed+2),width=4.4+2.8*Ease.hash(seed+3);
            int peaks=2+(int)Math.floor(Ease.hash(seed+4)*2.99);
            double[] px=new double[peaks],ph=new double[peaks],pw=new double[peaks];double top=0;
            for(int j=0;j<peaks;j++){
                px[j]=width*((j+.5+.35*(Ease.hash(seed+10+j)-.5))/peaks-.5);
                ph[j]=(3.0+3.3*Ease.hash(seed+20+j))*(j==peaks/2?1.18:1);
                pw[j]=width/peaks*(.75+.5*Ease.hash(seed+30+j));top=Math.max(top,ph[j]);
            }
            double[] ridge=new double[RIDGE];
            for(int k=0;k<RIDGE;k++){
                double x=width*(k/(double)(RIDGE-1)-.5),y=0;
                for(int j=0;j<peaks;j++){double u=Math.min(1,Math.abs(x-px[j])/pw[j]);y=Math.max(y,ph[j]*Math.pow(Math.cos(u*Math.PI/2),1.6));}
                double edge=Math.min(1,(x+width/2)/.7)*Math.min(1,(width/2-x)/.7);
                y+=top*(.035*Math.sin(9.3*x+seed)+.025*Math.sin(21.7*x+seed*1.7))*Math.min(1,y/top*3);
                ridge[k]=Math.max(0,y)*Math.max(0,edge);
            }
            l[i]=new Mountain(az,radius,width,ridge,px,ph,seed);
        }
        MOUNTAINS.put(id,l);while(MOUNTAINS.size()>16)MOUNTAINS.remove(MOUNTAINS.keySet().iterator().next());
        return l;
    }
    /** How tall a mountain stands at {@code t} (0 to 1): brushed up out of the ground, each a little after the last, and sinking as the formation ends. */
    static float risen(StandClient.Cast c,Mountain mt,float t){
        float d=.14f*(float)Ease.hash(mt.seed()+90);
        return Ease.out(t,RISE0+d,RISE1+d)*(1-Ease.smooth(t,end(c)+GLOW,end(c)+GONE+.2f));
    }
    /** A point of a mountain's face (fixed in the world, looking at the formation's middle): {@code x} along it, {@code y} up. */
    static Vec3d onMountain(Vec3d centre,Mountain mt,double x,double y){
        Vec3d out=new Vec3d(Math.cos(mt.az()),0,Math.sin(mt.az())),along=new Vec3d(-out.z,0,out.x);
        return centre.add(out.multiply(mt.radius())).add(along.multiply(x)).add(0,y,0);
    }
    /**
     * The mountains' ink (the blended pass): their faces darkest along the ridge, fading out toward their feet as ink
     * washed into mist does.
     */
    static void mountainInk(Fx c,EffectMesh m,float t){
        Vec3d centre=centre(c.cast);
        for(var mt:mountains(c.cast.id())){
            float h=risen(c.cast,mt,t);if(h<=.01f)continue;float a=Math.min(1,h*1.6f);
            for(int k=0;k+1<RIDGE;k++){
                double x0=mt.x(k),x1=mt.x(k+1),y0=mt.ridge()[k]*h,y1=mt.ridge()[k+1]*h;
                if(y0<.01&&y1<.01)continue;
                Vec3d b0=onMountain(centre,mt,x0,-.05),b1=onMountain(centre,mt,x1,-.05),p0=onMountain(centre,mt,x0,y0),p1=onMountain(centre,mt,x1,y1);
                Vec3d m0=onMountain(centre,mt,x0,y0*.55),m1=onMountain(centre,mt,x1,y1*.55);
                m.vertex(b0,0x16224a,.14f*a);m.vertex(b1,0x16224a,.14f*a);m.vertex(m1,0x0a1028,.7f*a);m.vertex(m0,0x0a1028,.7f*a);
                m.vertex(m0,0x0a1028,.7f*a);m.vertex(m1,0x0a1028,.7f*a);m.vertex(p1,0x05070f,.95f*a);m.vertex(p0,0x05070f,.95f*a);
            }
        }
    }
    /**
     * The mountains' light (the light pass): their ridges rimmed in light blue (brightest down their lit sides) with a soft
     * glow, and strokes of light down the lit side of each peak, as the brush leaves them.
     */
    static void mountainLight(Fx c,EffectMesh m,float t){
        Vec3d centre=centre(c.cast);
        for(var mt:mountains(c.cast.id())){
            float h=risen(c.cast,mt,t);if(h<=.01f)continue;float a=Math.min(1,h*1.4f);
            for(int k=0;k+1<RIDGE;k++){
                double y0=mt.ridge()[k]*h,y1=mt.ridge()[k+1]*h;if(y0<.05&&y1<.05)continue;
                Vec3d p0=onMountain(centre,mt,mt.x(k),y0),p1=onMountain(centre,mt,mt.x(k+1),y1);
                boolean lit=y1<y0;float fade=(float)Math.min(1,Math.min(y0,y1)/1.2);
                m.line(p0,p1,lit?.06:.035,lit?PALE:AZURE,(lit?.72f:.36f)*a*fade);
                m.softLine(p0,p1,lit?.42:.26,BLUE,(lit?.18f:.09f)*a*fade);
            }
            for(int j=0;j<mt.peakX().length;j++){
                for(int s=0;s<4;s++){
                    int seed=mt.seed()+j*17+s*5;
                    double x0=mt.peakX()[j]+.12+.22*s+.1*Ease.hash(seed),y0=mt.at(x0)*h-.18-.1*s;if(y0<.6)continue;
                    double x1=x0+.25+.35*Ease.hash(seed+1),y1=y0*(.42+.16*Ease.hash(seed+2));
                    m.line(onMountain(centre,mt,x0,y0),onMountain(centre,mt,x1,y1),.032,AZURE,.3f*a*(1-.18f*s));
                }
            }
        }
    }
    /** A wisp of the white mist drifting round the formation: which way, how far, how high, its size, how far and how slowly it drifts, its seed. */
    record Mist(double az,double radius,double up,double width,double height,double drift,double period,int seed){}
    private static final Map<UUID,Mist[]> MISTS=new LinkedHashMap<>();
    static Mist[] mists(UUID id){
        var l=MISTS.get(id);if(l!=null)return l;
        int s0=(int)(id.getMostSignificantBits()*17+id.getLeastSignificantBits()*7);
        l=new Mist[13];
        for(int i=0;i<l.length;i++){
            int seed=s0+131*(i+1);
            // (most of it before the mountains, seen against them from inside; some out over them)
            double radius=i%4==3?12+3*Ease.hash(seed+2):7.5+3.5*Ease.hash(seed+2),up=i%4==3?3.5+2*Ease.hash(seed+3):.7+1.8*Ease.hash(seed+3);
            l[i]=new Mist(Math.PI*2*(i+.6*Ease.hash(seed+1))/l.length,radius,up,2.6+2.2*Ease.hash(seed+4),.8+.6*Ease.hash(seed+5),1.4+1.8*Ease.hash(seed+6),24+18*Ease.hash(seed+7),seed);
        }
        MISTS.put(id,l);while(MISTS.size()>16)MISTS.remove(MISTS.keySet().iterator().next());
        return l;
    }
    /**
     * The mist (the blended pass): soft white wisps, each three sheets fixed in the world 60° apart (never turned to the
     * camera, as every smoke and mist here), drifting slowly to and fro round the formation and swelling a little.
     */
    static void mist(Fx c,EffectMesh m,float t){
        float a=Ease.smooth(t,MIST0,MIST1)*(1-Ease.smooth(t,end(c.cast)+GLOW,end(c.cast)+GONE+.3f));if(a<=.01f)return;
        Vec3d centre=centre(c.cast);
        for(var w:mists(c.cast.id())){
            double drift=w.drift()*Math.sin(Math.PI*2*t/w.period()+w.seed()),swell=1+.12*Math.sin(t*.7+w.seed()*.3);
            Vec3d out=new Vec3d(Math.cos(w.az()),0,Math.sin(w.az())),along=new Vec3d(-out.z,0,out.x);
            Vec3d mid=centre.add(out.multiply(w.radius())).add(along.multiply(drift)).add(0,w.up(),0);
            for(int s=0;s<3;s++){
                double turn=Math.toRadians(60*s+20*Ease.hash(w.seed()+8));
                Vec3d u=along.multiply(Math.cos(turn)).add(out.multiply(Math.sin(turn))).multiply(w.width()*swell),vv=new Vec3d(0,w.height()*swell,0);
                puff(m,mid,u,vv,0xe9eef6,(s==0?.2f:.12f)*a);
            }
        }
    }
    /** A soft sheet: opaque at its middle, fading to nothing at all four edges ({@code u}, {@code v} its half sizes). */
    static void puff(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,int rgb,float alpha){
        Vec3d[][] g=new Vec3d[3][3];float[][] al=new float[3][3];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++){g[i][j]=c.add(u.multiply(i-1)).add(v.multiply(j-1));al[i][j]=i==1&&j==1?alpha:0;}
        for(int i=0;i<2;i++)for(int j=0;j<2;j++){
            m.vertex(g[i][j],rgb,al[i][j]);m.vertex(g[i+1][j],rgb,al[i+1][j]);m.vertex(g[i+1][j+1],rgb,al[i+1][j+1]);m.vertex(g[i][j+1],rgb,al[i][j+1]);
        }
    }

    // ───────────────────────────── the ground ─────────────────────────────

    /** How far the pool of ink has spread from the middle at {@code t} (blocks): under her as she leaps, then swirled out over the formation. */
    static double pool(float t){
        if(t<POOL0)return 0;
        if(t<SWIRL0)return POOL_R*Ease.out(t,POOL0,POOL1);
        return POOL_R+(SWIRL_R-POOL_R)*Ease.out(t,SWIRL0,SWIRL1+.1f);
    }
    /** The blue of the formation's ground (0 to about .6): deep as the pool swirls out and the swords strike, then a faint blue while it stands. */
    static float tint(StandClient.Cast c,float t){
        float deep=Ease.smooth(t,SWIRL0,SWIRL1)*(1-Ease.smooth(t,DRY0+.15f,DRY1+.1f));
        float faint=Ease.smooth(t,SWIRL1,DRY0)*(1-Ease.smooth(t,end(c)+GLOW,end(c)+GONE+.2f));
        return .58f*deep+.3f*faint*(1-deep);
    }
    /** A ring of light lying on the ground (radius {@code r}, {@code w} wide, brightest along its middle), wavering a little. */
    static void groundBand(EffectMesh m,Vec3d c,double r,double w,int rgb,float alpha,int seed,float t){
        if(alpha<=.005f||r<=.01)return;int n=Math.max(24,(int)(r*10));
        for(int i=0;i<n;i++){
            double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n;
            double k0=1+.035*Math.sin(a0*5+seed+t*3)+.02*Math.sin(a0*11-seed),k1=1+.035*Math.sin(a1*5+seed+t*3)+.02*Math.sin(a1*11-seed);
            Vec3d d0=new Vec3d(Math.cos(a0),0,Math.sin(a0)),d1=new Vec3d(Math.cos(a1),0,Math.sin(a1));
            Vec3d i0=c.add(d0.multiply(Math.max(0,r*k0-w))),i1=c.add(d1.multiply(Math.max(0,r*k1-w))),m0=c.add(d0.multiply(r*k0)),m1=c.add(d1.multiply(r*k1)),o0=c.add(d0.multiply(r*k0+w)),o1=c.add(d1.multiply(r*k1+w));
            m.vertex(i0,rgb,0);m.vertex(i1,rgb,0);m.vertex(m1,rgb,alpha);m.vertex(m0,rgb,alpha);
            m.vertex(m0,rgb,alpha);m.vertex(m1,rgb,alpha);m.vertex(o1,rgb,0);m.vertex(o0,rgb,0);
        }
    }
    /** A disc lying on the ground: {@code rgb} all over it to {@code soft} of its radius, fading to nothing at its rim. */
    static void groundDisc(EffectMesh m,Vec3d c,double r,double soft,int rgb,float alpha){
        if(alpha<=.005f)return;int n=56;
        for(int i=0;i<n;i++){
            double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n;
            Vec3d d0=new Vec3d(Math.cos(a0),0,Math.sin(a0)),d1=new Vec3d(Math.cos(a1),0,Math.sin(a1));
            m.vertex(c,rgb,alpha);m.vertex(c.add(d0.multiply(r*soft)),rgb,alpha);m.vertex(c.add(d1.multiply(r*soft)),rgb,alpha);m.vertex(c,rgb,alpha);
            m.vertex(c.add(d0.multiply(r*soft)),rgb,alpha);m.vertex(c.add(d0.multiply(r)),rgb,0);m.vertex(c.add(d1.multiply(r)),rgb,0);m.vertex(c.add(d1.multiply(r*soft)),rgb,alpha);
        }
    }
    /** The swirl: three arms of blue light lined with ink, spiralling out over the formation's ground as the pool swirls out. */
    static final int ARMS=3;
    static void swirl(Vec3d centre,float t,int seed){
        if(t<SWIRL0-.02f||t>=DRY1)return;
        double head=POOL_R*.8+(SWIRL_R-POOL_R*.8)*Ease.out(t,SWIRL0,SWIRL1),turn=.9*(t-SWIRL0)+Math.PI*2*Ease.hash(seed);
        float ink=Ease.smooth(t,1.08f,1.42f),erase=Ease.smooth(t,1.25f,DRY1-.05f),alpha=Ease.smooth(t,SWIRL0-.02f,SWIRL0+.06f);
        int n=40;
        for(int arm=0;arm<ARMS;arm++){
            Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
            for(int i=0;i<n;i++){
                double s=i/(double)(n-1),r=1.0+(head-1.0)*s,phi=turn+Math.PI*2*arm/ARMS+2.3*s,w=.28+.72*s;
                Vec3d d=new Vec3d(Math.cos(phi),0,Math.sin(phi));
                in[i]=centre.add(d.multiply(Math.max(.2,r-w*.5))).add(0,.05,0);out[i]=centre.add(d.multiply(r+w*.5)).add(0,.05,0);along[i]=(float)s;
            }
            KagenoFire.inkRibbon(in,out,along,n,0,(seed+arm*5)&15,erase,ink,.8f*alpha);
        }
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /**
     * The ink: the pool under her and over the formation, its swirl, the ink splashed at every sword's foot as it strikes,
     * and as it ends the ink each sword breaks into, where it falls.
     */
    static void ink(Fx c){
        float t=c.sec;Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);double floor=centre.y+.02;int seed=c.cast.id().hashCode();
        // the pool: spreading under her, swirled out over the formation, drying away as the formation stands
        double r=pool(t);
        if(r>.05&&t<DRY1+.1f){
            float dry=Ease.smooth(t,DRY0,DRY1);
            stain(new Vec3d(centre.x,floor-.004,centre.z),2.1*r,Math.toRadians(-yaw),1,seed&15,dry,.94f*(1-Ease.smooth(dry,.55f,1)));
        }
        swirl(centre,t,seed);
        // a splash of ink at every sword's foot as it strikes (the video: black splashes round each one's flower)
        for(var b:layout(c.cast.id())){
            if(t<b.plant())continue;Vec3d f=foot(centre,yaw,b);
            GiantSweep.splatFor(new Vec3d(f.x,floor+.003+.001*(b.seed()&7),f.z),b.middle()?3.2:1.3+.8*b.flower(),Math.PI*2*Ease.hash(b.seed()+40),1+.3*Ease.hash(b.seed()+41),b.seed()&15,t-b.plant()-.02f,.9f);
        }
        // as it ends: ink where the swords stood and where the ink they break into falls
        float e=end(c.cast)+INKED;
        if(t>=e)for(var b:layout(c.cast.id())){
            Vec3d f=foot(centre,yaw,b);
            GiantSweep.splatFor(new Vec3d(f.x,floor+.004+.001*(b.seed()&7),f.z),1.5+.9*Ease.hash(b.seed()+43),Math.PI*2*Ease.hash(b.seed()+44),1.1,(b.seed()+3)&15,t-e-.04f,.85f);
        }
        for(var d:drops(c,t)){
            if(d.size()<STAIN_MIN)continue;
            double land=lands(d,floor+.01);if(land<0)continue;
            float age=t-d.born()-(float)land;if(age<0||age>=STAIN_LIFE)continue;
            Vec3d p=flight(d,land),mv=moving(d,land);
            stainAt(new Vec3d(p.x,floor+.001*(d.seed()%7),p.z),d.size()*4.6,Math.atan2(mv.z,mv.x),1+Math.min(1.4,Math.hypot(mv.x,mv.z)*.3),d.seed(),age,.78f);
        }
    }
    /** The ink each sword breaks into as the formation ends: flakes and drops flung off along it, falling. */
    static List<Drop> drops(Fx c,float t){
        List<Drop> out=new ArrayList<>();float e=end(c.cast)+INKED;if(t<e)return out;
        Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        for(var b:layout(c.cast.id())){
            Vec3d top=planted(centre,yaw,b),f=foot(centre,yaw,b),out0=f.subtract(centre).multiply(1,0,1);out0=out0.lengthSquared()<1e-4?Vec3d.fromPolar(0,yaw):out0.normalize();
            for(int k=0;k<5;k++){
                int seed=b.seed()*7+k*31+5003;double u=.15+.8*Ease.hash(seed);
                Vec3d at=top.lerp(f,u);double az=Math.PI*2*Ease.hash(seed+1),sp=.8+1.8*Ease.hash(seed+2);
                Vec3d vel=new Vec3d(Math.cos(az),0,Math.sin(az)).multiply(sp).add(out0.multiply(.6)).add(0,.6+1.4*Ease.hash(seed+3),0);
                int kind=k%3==1?1:k%4==3?2:0;
                out.add(new Drop(at,vel,3,3,e+.06f*(float)Ease.hash(seed+4),.6f+.35f*(float)Ease.hash(seed+5),kind==0?.04+.05*Ease.hash(seed+6):.06+.07*Ease.hash(seed+6),seed,kind));
            }
        }
        return out;
    }
    /**
     * The blended pass: the blue of the formation's ground, the ink mountains, the mist, the black spike out of the pool,
     * the swords (farthest first), the crystal flowers at their feet, and as it ends the ink the swords break into; the
     * ink round whom it strikes.
     */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec;Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        groundDisc(m,centre.add(0,.06,0),IsosangRules.FORMATION_RADIUS+.6,.88,NAVY,tint(c.cast,t));
        mountainInk(c,m,t);
        mist(c,m,t);
        spike(m,centre,t);
        drawSwords(c,m,t,centre,yaw);
        flowers(c,m,t,centre,yaw,false);
        double floor=centre.y+.03;
        for(var d:drops(c,t)){
            float age=t-d.born();if(age<0||age>=d.life())continue;
            double land=lands(d,floor);if(land>=0&&age>=land)continue;
            Vec3d q=flight(d,age),mv=moving(d,age);float u=age/d.life();
            float by=near(q,c.camera);if(by<=0)continue;
            double size=d.size()*(1-.45*u)*Ease.out(age,0,.06f)*(.35+.65*by);float a=.92f*(1-Ease.smooth(u,.6f,1))*by;
            switch(d.kind()){
                case 1->flake(m,q,size,age,1.2+2.0*Ease.hash(d.seed()+9),d.seed(),INK,a);
                case 2->blot(m,q,mv,size,d.seed(),INK,a);
                default->drop(m,q,mv,size,d.seed(),INK,a);
            }
        }
        struck(c,m,t,true);
    }
    /** The swords, drawn from the farthest to the nearest (the blended pass has no depth of its own). */
    static void drawSwords(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        var all=layout(c.cast.id());Integer[] order=new Integer[all.length];Vec3d[] tops=new Vec3d[all.length];
        for(int i=0;i<all.length;i++){order[i]=i;tops[i]=planted(centre,yaw,all[i]).add(0,above(all[i],t),0);}
        Arrays.sort(order,Comparator.comparingDouble((Integer i)->-tops[i].squaredDistanceTo(c.camera)));
        float e=end(c.cast),flare=Ease.smooth(t,e,e+.04f)*(1-Ease.smooth(t,e+GLOW,e+INKED)),ink=Ease.smooth(t,e+GLOW,e+INKED);
        for(int i:order){
            var b=all[i];float a=shown(c.cast,b,t);if(a<=.005f)continue;
            sword(m,c.camera,tops[i],frame(down(b),b.spin()),a,dark(b,t),flare,ink);
        }
    }
    /** How dark a sword is at {@code t} (0 to 1): dark as ink as it strikes (the video: black swords over the blue ground), silver again after. */
    static float dark(Blade b,float t){return Ease.smooth(t,b.plant()-.01f,b.plant()+.05f)*(1-Ease.smooth(t,SILVER0,SILVER1));}
    /** How bright a sword is in the sky (0 to 1): white as it hangs and falls, gone dark as it strikes. */
    static float sky(Blade b,float t){return t<b.plant()?1:1-Ease.smooth(t,b.plant(),b.plant()+.06f);}
    /**
     * The black spike shooting up out of the pool as the middle sword strikes (the video: a black point up out of the
     * middle of the pool, then a burst of blue light): two crossed blades of ink fixed in the world.
     */
    static void spike(EffectMesh m,Vec3d centre,float t){
        float at=IsosangRules.H_PLANT;if(t<at-.06f||t>=at+.12f)return;
        double h=2.6*Ease.out(t,at-.06f,at-.01f)*(1-Ease.smooth(t,at+.02f,at+.11f)),w=.38*(1-.5*Ease.smooth(t,at,at+.1f));if(h<=.02)return;
        Vec3d top=centre.add(0,h,0);
        for(int s=0;s<2;s++){
            Vec3d side=s==0?new Vec3d(w,0,0):new Vec3d(0,0,w);
            m.triangle(centre.subtract(side).add(0,.02,0),centre.add(side).add(0,.02,0),top,INK,.96f);
        }
    }
    /**
     * The crystal flowers at the swords' feet (the video: a flower of blue crystal petals round each sword as it strikes,
     * pale at their tips, gone as the colour comes back): petals of crystal fixed in the world, opening out of the ground as
     * the sword strikes; their bodies blended ({@code light} false) or their light added.
     */
    static void flowers(Fx c,EffectMesh m,float t,Vec3d centre,float yaw,boolean light){
        if(t>=WILT1)return;
        for(var b:layout(c.cast.id())){
            if(b.middle()||t<b.plant())continue;
            float open=(float)backOut(Ease.unit(t,b.plant(),b.plant()+.12f)),wilt=Ease.smooth(t,WILT0+.06f*(float)Ease.hash(b.seed()+70),WILT1);
            double size=b.flower()*open*(1-.4*wilt);float a=(1-wilt);if(a<=.01f||size<=.01)continue;
            Vec3d f=foot(centre,yaw,b).add(0,.03,0);
            int petals=7;
            for(int j=0;j<petals;j++){
                int seed=b.seed()*13+j*7;
                double az=Math.toRadians(b.spin())+Math.PI*2*(j+.3*(Ease.hash(seed)-.5))/petals,el=Math.toRadians(28+38*Ease.hash(seed+1));
                double len=(.42+.32*Ease.hash(seed+2))*size,wid=(.15+.1*Ease.hash(seed+3))*size;
                Vec3d dir=new Vec3d(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el)),side=new Vec3d(-Math.sin(az),0,Math.cos(az));
                Vec3d tip=f.add(dir.multiply(len)),mid=f.add(dir.multiply(len*.38)),l=mid.add(side.multiply(wid)),r=mid.subtract(side.multiply(wid));
                if(!light){
                    m.vertex(f,DEEP,.9f*a);m.vertex(l,0x4a6cf0,.88f*a);m.vertex(tip,LILAC,.92f*a);m.vertex(tip,LILAC,.92f*a);
                    m.vertex(f,DEEP,.9f*a);m.vertex(tip,LILAC,.92f*a);m.vertex(r,0x3a58d8,.88f*a);m.vertex(r,0x3a58d8,.88f*a);
                }else{
                    m.line(f,tip,.022*size,WHITE,.7f*a);m.line(l,tip,.012*size,PALE,.45f*a);m.line(r,tip,.012*size,PALE,.45f*a);
                }
            }
            if(light){m.softDisc(f.add(0,.15,0),.55*size,BLUE,.35f*a);glint8(m,f.add(0,.3*size,0),.18*size*(.6+.4*twinkle(t,b.seed())),WHITE,.6f*a);}
        }
    }
    /** An ease that overshoots a little before it settles (a flower opening). */
    static double backOut(float u){double s=1.4;double x=u-1;return 1+(s+1)*x*x*x+s*x*x;}
    /**
     * The light: her sword's aura; the thread of lightning from its point to the ground; the pool's blue rim; the swords
     * showing in the sky, their glow, the lightning they trail as they fall and the flash, the shaft of light and the
     * crystal's light as each strikes; the middle sword's burst; the mountains' rims; and the bursts on whom it strikes.
     */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        aura(c,m,t);
        thread(c,m,t,centre);
        // the pool's rim of blue light, spreading with it, and the swirl's glow
        double r=pool(t);float rim=Ease.smooth(t,POOL0,POOL0+.08f)*(1-Ease.smooth(t,SWIRL1,DRY0+.1f));
        if(rim>.01f){groundBand(m,centre.add(0,.06,0),r,.5,BLUE,.55f*rim,c.cast.id().hashCode(),t);groundBand(m,centre.add(0,.07,0),r,.14,PALE,.75f*rim,c.cast.id().hashCode()+3,t);}
        burst(m,centre,t);
        swordLight(c,m,t,centre,yaw);
        flowers(c,m,t,centre,yaw,true);
        mountainLight(c,m,t);
        struck(c,m,t,false);
    }
    /** Over the ink: the starlight now and then on a sword as the formation stands. */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec;if(t<SILVER1||t>=end(c.cast))return;
        Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        for(var b:layout(c.cast.id())){
            double period=4+3*Ease.hash(b.seed()+80),phase=period*Ease.hash(b.seed()+81),u=((t+phase)%period)/period;
            if(u>.07)continue;float a=(float)Math.sin(Math.PI*u/.07);
            Vec3d top=planted(centre,yaw,b),q=at(top,frame(down(b),b.spin()),p(0,S_GUARD+.4*Ease.hash(b.seed()+82),0));
            glint8(m,q,.2,WHITE,.8f*a);
        }
    }
    /** Her own sword's blue aura as she leaps and calls the swords. */
    static void aura(Fx c,EffectMesh m,float t){
        float g=Ease.window(t,.1f,.9f,.06f,.15f);if(g<=.01f)return;
        var hb=blade(c,t);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));
        m.softLine(guard,tip,.3,AURA[1],.12f*g);m.softLine(guard,tip,.14,AURA[1],.45f*g);
        m.line(guard,tip,.06,AURA[2],.85f*g);m.line(guard,tip,.027,AURA[3],.8f*g);
        m.softDisc(tip,.2,AURA[3],.5f*g);
    }
    /** The thread of lightning from her sword's point down to the ground as she leaps (the video: a white-blue bolt, flickering). */
    static void thread(Fx c,EffectMesh m,float t,Vec3d centre){
        float a=Ease.smooth(t,THREAD0-.02f,THREAD0+.02f)*(1-Ease.smooth(t,THREAD1-.08f,THREAD1));if(a<=.01f)return;
        var hb=blade(c,t);Vec3d tip=hb[0].add(hb[1].multiply(TIP)),ground=new Vec3d(tip.x,centre.y+.05,tip.z);
        if(tip.y-ground.y<.2)return;
        bolt(m,tip,ground,c.cast.id().hashCode()+7,t,.35,.05,WHITE,AZURE,a);
        m.softDisc(ground.add(0,.1,0),.7,BLUE,.45f*a);m.softDisc(tip,.25,PALE,.6f*a);
    }
    /**
     * A bolt of lightning from {@code a} to {@code b}: jagged, its kinks drawn anew some thirty times a second (a function of
     * the clock), a white core in a blue glow; light, so turned to the camera.
     */
    static void bolt(EffectMesh m,Vec3d a,Vec3d b,int seed,float t,double jag,double width,int core,int glow,float alpha){
        Vec3d d=b.subtract(a);double len=d.length();if(len<1e-3||alpha<=.005f)return;
        Vec3d s1=d.crossProduct(UP);if(s1.lengthSquared()<1e-6)s1=new Vec3d(1,0,0);s1=s1.normalize();Vec3d s2=d.crossProduct(s1).normalize();
        int n=Math.max(4,Math.min(16,(int)(len*1.6))),q=(int)Math.floor(t*30);
        Vec3d prev=a;
        for(int i=1;i<=n;i++){
            double u=i/(double)n,env=Math.sin(Math.PI*u);
            Vec3d next=i==n?b:a.add(d.multiply(u)).add(s1.multiply((Ease.hash(seed+q*131+i*7)-.5)*jag*2*env)).add(s2.multiply((Ease.hash(seed+q*131+i*7+3)-.5)*jag*2*env));
            m.line(prev,next,width,core,alpha);m.softLine(prev,next,width*7,glow,.35f*alpha);
            prev=next;
        }
    }
    /** The middle sword's burst as it strikes (the video: the black point bursting into a crown of blue light, a ring racing out). */
    static void burst(EffectMesh m,Vec3d centre,float t){
        float at=IsosangRules.H_PLANT,k=FxLib.kick(t,at+.02f,.1f);if(k<=.01f&&t>at+.4f)return;
        if(k>.01f){
            double h=2.2*Ease.out(t,at,at+.06f);Vec3d top=centre.add(0,h,0);
            for(int s=0;s<2;s++){Vec3d side=s==0?new Vec3d(.75,0,0):new Vec3d(0,0,.75);m.triangle(centre.subtract(side).add(0,.03,0),centre.add(side).add(0,.03,0),top,AZURE,.55f*k);m.triangle(centre.subtract(side.multiply(.4)).add(0,.03,0),centre.add(side.multiply(.4)).add(0,.03,0),top,WHITE,.6f*k);}
            for(int j=0;j<12;j++){
                double az=Math.PI*2*(j+.4*Ease.hash(j*7+1))/12,el=Math.toRadians(48+24*Ease.hash(j*7+2)),len=(1.1+.9*Ease.hash(j*7+3))*Ease.out(t,at,at+.08f);
                Vec3d dir=new Vec3d(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el)),side=new Vec3d(-Math.sin(az),0,Math.cos(az));
                m.needle(centre.add(0,.03,0),centre.add(dir.multiply(len)),side,.22,.35,PALE,.8f*k);
            }
            m.softDisc(centre.add(0,.6,0),2.4,BLUE,.42f*k);m.softDisc(centre.add(0,.4,0),.8,WHITE,.6f*k);
        }
        float u=Ease.unit(t,at,at+.3f);
        if(u>0&&u<1)groundBand(m,centre.add(0,.07,0),.4+3.2*Ease.out(t,at,at+.3f),.22,PALE,.8f*(1-u),17,t);
    }
    /**
     * The swords' light: a flash as each shows in the sky; their glow; the lightning each trails as it falls; the flash,
     * the shaft of light (every fifth one) and the ring at its foot as it strikes.
     */
    static void swordLight(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        float e=end(c.cast),flare=Ease.smooth(t,e,e+.04f)*(1-Ease.smooth(t,e+GLOW,e+INKED)),ink=Ease.smooth(t,e+GLOW,e+INKED);
        for(var b:layout(c.cast.id())){
            float a=shown(c.cast,b,t);if(a<=.005f)continue;
            Vec3d top=planted(centre,yaw,b).add(0,above(b,t),0),foot=foot(centre,yaw,b);var f=frame(down(b),b.spin());
            // shows in the sky
            float show=FxLib.kick(t,b.shows()+.02f,.1f);
            if(show>.01f){Vec3d mid=at(top,f,p(0,S_LEN*.45,0));m.star(mid,.5,WHITE,.7f*show);m.softDisc(mid,.6,AZURE,.3f*show);}
            swordGlow(m,top,f,a,dark(b,t),sky(b,t),flare,ink);
            // the lightning it trails as it falls, from the sky down to its point, gone a moment after it strikes
            float from=b.plant()-FALL,trail=Ease.smooth(t,from,from+.03f)*(1-Ease.smooth(t,b.plant()+.06f,b.plant()+.2f));
            if(trail>.01f){
                Vec3d point=at(top,f,p(0,S_LEN,0)),high=foot.add(0,b.sky()+3,0);
                if(t>=b.plant())point=foot;
                bolt(m,high,point,b.seed(),t,.3,.034,PALE,BLUE,.8f*trail);
            }
            // as it strikes: a flash and a ring at its foot, a shaft of light from every fifth one
            float k=FxLib.kick(t,b.plant(),.1f);
            if(k>.01f){
                m.softDisc(foot.add(0,.3,0),b.middle()?2:1.1,BLUE,.45f*k);m.star(foot.add(0,.35,0),.8,WHITE,.7f*k);
                float u=Ease.unit(t,b.plant(),b.plant()+.18f);if(u<1)groundBand(m,foot.add(0,.06,0),.15+.7*Ease.out(t,b.plant(),b.plant()+.18f),.08,PALE,.45f*(1-u),b.seed(),t);
            }
            if(Math.floorMod(b.seed(),5)==0||b.middle()){
                float s=Ease.smooth(t,b.plant(),b.plant()+.02f)*(1-Ease.smooth(t,b.plant()+.1f,b.plant()+.4f));
                if(s>.01f){Vec3d up=foot.add(0,14,0);m.line(foot,up,.06,WHITE,.55f*s);m.softLine(foot,up,.45,CYAN,.25f*s);}
            }
            // as the formation ends: the flare spreads over the ground at its foot (a cross of light)
            if(flare>.01f){
                Vec3d g=foot.add(0,.06,0),x=Vec3d.fromPolar(0,(float)b.spin()).multiply(1.3),z=new Vec3d(-x.z,0,x.x);
                groundSoftLine(m,g.subtract(x),g.add(x),.5,CYAN,.55f*flare);groundSoftLine(m,g.subtract(z),g.add(z),.5,CYAN,.55f*flare);
                m.softDisc(g.add(0,.2,0),.8,CYAN,.45f*flare);
            }
        }
    }
    /** A soft line lying flat on the ground (its width across it on the ground, not turned to the camera). */
    static void groundSoftLine(EffectMesh m,Vec3d a,Vec3d b,double width,int rgb,float alpha){
        Vec3d d=b.subtract(a);if(d.lengthSquared()<1e-8||alpha<=0)return;
        Vec3d side=new Vec3d(-d.z,0,d.x).normalize().multiply(width*.5);
        m.vertex(a,rgb,alpha);m.vertex(b,rgb,alpha);m.vertex(b.add(side),rgb,0);m.vertex(a.add(side),rgb,0);
        m.vertex(a.subtract(side),rgb,0);m.vertex(b.subtract(side),rgb,0);m.vertex(b,rgb,alpha);m.vertex(a,rgb,alpha);
    }
    /**
     * Bursts on whom the formation struck, as the server told (IsosangClient.formationStruck): blue spikes round a pale heart
     * with a crescent moon, as the sword's hits' ({@code ink} for their ink, in the blended pass).
     */
    static void struck(Fx c,EffectMesh m,float t,boolean ink){
        var world=c.mc.world;if(world==null)return;Vec3d centre=centre(c.cast);
        for(var e:IsosangClient.formationStruck(c.cast.id()).entrySet()){
            float at=e.getKey()/20f;if(t<at-.01f||t>=at+.35f)continue;
            int k=0;
            for(int id:e.getValue()){
                if(!(world.getEntityById(id) instanceof LivingEntity le))continue;
                Vec3d p=chest(c,le),way=p.subtract(centre).multiply(1,0,1);way=way.lengthSquared()<1e-4?c.f:way.normalize();
                double size=e.getKey()==IsosangRules.FORMATION_IMPACT_AT?.95:.7;
                if(ink)IsosangEmpower.burstInk(c,m,p,at,size,id*13+k);else IsosangEmpower.burst(c,m,p,way,at,size,id*13+k);
                k++;
            }
        }
    }

    // ───────────────────────────── the effect layer, the camera ─────────────────────────────

    static void frame(Fx c,FxFrame f){
        float t=c.sec;Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        float th=Ease.smooth(t,THREAD0-.02f,THREAD0+.02f)*(1-Ease.smooth(t,THREAD1-.08f,THREAD1));
        if(th>.01f)f.light(centre.add(0,1.5,0),0x9fc8ff,8,.9f*th);
        float pool=Ease.smooth(t,POOL0,POOL0+.1f)*(1-Ease.smooth(t,SWIRL1,DRY1));
        if(pool>.01f)f.light(centre.add(0,.6,0),0x5a8cff,11,.8f*pool);
        float mid=FxLib.kick(t,IsosangRules.H_PLANT+.02f,.16f);if(mid>.01f)f.light(centre.add(0,1,0),0xb8d8ff,12,1.4f*mid);
        for(var b:layout(c.cast.id())){float k=FxLib.kick(t,b.plant(),.12f);if(k>.05f&&!b.middle())f.light(foot(centre,yaw,b).add(0,.6,0),0x9fc8ff,6,.7f*k);}
        float e=end(c.cast),flare=Ease.smooth(t,e,e+.04f)*(1-Ease.smooth(t,e+GLOW,e+INKED+.05f));
        if(flare>.01f)f.light(centre.add(0,1,0),0x7fe6ff,14,1.3f*flare);
        float stand=Ease.smooth(t,SILVER0,SILVER1)*(1-Ease.smooth(t,e,e+GONE));
        if(stand>.01f)f.light(centre.add(0,2.2,0),0x8fb4ff,12,.25f*stand);
        // fast as she leaps and the swords rain: the afterglow cut short, else they leave copies of themselves on the way
        f.afterglow(t>=.14f&&t<1.6f||t>=e-.05f&&t<e+GONE+.1f||t>=IsosangRules.H_DROP&&t<IsosangRules.H_DOWN+.1f?.08f:.3f);
    }
    /** Shake: the middle sword striking, the rain of swords crashing in, and a little as the formation ends (each over two frames). */
    static float shake(StandClient.Cast cast,float sec){
        float e=end(cast);
        return Math.max(Math.max(.7f*bump(sec,IsosangRules.H_PLANT,.1f),.95f*bump(sec,(float)IsosangRules.FORMATION_IMPACT_AT/20,.16f)),.45f*bump(sec,e+.02f,.1f));
    }
    /** How far her own camera draws back for it (blocks): out as she leaps, further as the swords rain to show them all, held a little back while it stands, in again as she drops. */
    static final double PULL_LEAP=2.2,PULL_RAIN=5,PULL_STAND=3.2;
    static double pull(float sec){
        double out=PULL_LEAP*Ease.smooth(sec,.1f,.45f)+(PULL_RAIN-PULL_LEAP)*Ease.smooth(sec,.9f,1.35f)-(PULL_RAIN-PULL_STAND)*Ease.smooth(sec,1.6f,2.6f);
        return out*(1-Ease.smooth(sec,IsosangRules.H_DROP,IsosangRules.H_DOWN+.3f));
    }
}
