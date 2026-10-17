package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * E "신속한 맹공". The sword is presented level across the chest and the left palm runs pink neon up the
 * blade from the guard to the tip; the body drops into a low stance with the sword drawn back beside the
 * head and levelled at the target; thirteen thrusts land in 1.55 s (the server's seven hits,
 * {@link KagenoRules#eTicks}, and one between each pair), each shooting a lance of light with two
 * afterimage blades; the sword coils low behind the body while energy gathers; the rising slash crosses
 * the target on the finisher tick ({@link KagenoRules#E_FINISH}), throwing a neon crescent and a geyser
 * of violet light; the sword is held high, flicked down and the neon flakes away. Only the sword is pink;
 * everything the skill throws is violet. Times are seconds after the cast and every effect is a pure
 * function of the cast clock.
 */
final class NeonThrustSkill implements Fx.Skill {
    /** The sword's neon is pink: white-hot core, light pink, hot pink, magenta. */
    static final int CORE=0xfff2fb,LIGHT=0xffa6e2,HOT=0xff48c4,MAGENTA=0xff1c98;
    /** The skill's own light is violet: white-violet core, lavender, violet, indigo. */
    static final int VCORE=0xece0ff,LAVENDER=0xb388ff,VIOLET=0x8448ff,INDIGO=0x5418d0;
    /** Tube palettes (haze, glow, body, core): the pink neon of the sword, the violet of the skill. */
    static final int[] PINK={MAGENTA,HOT,LIGHT,CORE},PURPLE={INDIGO,VIOLET,LAVENDER,VCORE};
    /** The palm lights the blade from the guard (LIT0) to the tip (LIT1); the stance is set at STANCE. */
    static final float LIT0=.16f,LIT1=.42f,STANCE=.62f;
    /** The coil after the last thrust, the start of the rising slash, the slash through the target, the follow-through, the rest. */
    static final float COIL=2.47f,RISE=2.84f,SLASH=KagenoRules.E_FINISH/20f,OVER=3.04f,REST=3.35f;
    /** Thrust times (seconds) and whether the server strikes on each. */
    static final float[] THRUSTS;
    static final boolean[] STRIKES;
    /** Where each thrust goes: elevation and azimuth (degrees, toward the right) from the cast facing. */
    static final double[][] AIM={{2,0},{9,-7},{-5,6},{12,4},{-8,-5},{5,9},{0,-2},{10,-9},{-6,4},{7,8},{-3,-6},{11,2},{0,0}};
    /** The guard and the tip, in blocks along the blade from the hand. */
    static final double GUARD=.24,TIP=1.35;
    static final Anim CLIP;
    static {
        int[] ticks=KagenoRules.eTicks();
        THRUSTS=new float[ticks.length*2-1];STRIKES=new boolean[THRUSTS.length];
        for(int i=0;i<ticks.length;i++){THRUSTS[2*i]=ticks[i]/20f;STRIKES[2*i]=true;if(i+1<ticks.length)THRUSTS[2*i+1]=(ticks[i]+ticks[i+1])/40f;}
        List<Key> keys=new ArrayList<>(List.of(
            k(0,KagenoClips.IDLE),
            k(.1,present(40),Anim.Ease.OUT),
            k(LIT0,present(40),Anim.Ease.HOLD),
            k(.29,present(-8)),
            k(LIT1,present(-48),Anim.Ease.LINEAR),
            // The palm leaves the tip with a flick.
            k(.49,world(0,0,8,0,0,d(14,14),d(6,-88),d(40,-100),d(-84,100),d(-84,-100)).head(-30,0,0),Anim.Ease.OUT),
            k(STANCE,stance(0),Anim.Ease.OUT),
            k(.73,stance(1))));
        for(int i=0;i<THRUSTS.length;i++){
            boolean last=i==THRUSTS.length-1;
            keys.add(k(THRUSTS[i],thrust(i),Anim.Ease.OUT));
            if(!last)keys.add(k(THRUSTS[i]+.06f,cocked(i+1)));
        }
        float end=THRUSTS[THRUSTS.length-1];
        keys.addAll(List.of(
            k(end+.06f,thrust(THRUSTS.length-1),Anim.Ease.HOLD),
            k(COIL,coil(0)),
            k(RISE,coil(1)),
            // The rising slash: from low behind, through the target at chest height, up over the head and behind.
            k(2.87,world(.24,-.26,-30,14,0,d(-34,58),d(-22,36),d(-18,-70),d(-50,40),d(-52,-150)),Anim.Ease.IN),
            k(SLASH,world(.3,-.08,-6,4,0,d(6,14),d(14,4),d(-45,-115),d(-55,25),d(-58,-165)),Anim.Ease.LINEAR),
            k(2.935,world(.32,.16,8,-8,4,d(52,8),d(62,0),d(-50,-120),d(-62,20),d(-66,-170)),Anim.Ease.LINEAR),
            k(2.975,world(.32,.3,14,-14,5,d(80,-6),d(76,178),d(-50,-120),d(-70,15),d(-72,-175)),Anim.Ease.OUT),
            k(OVER,world(.3,.22,16,-12,4,d(75,175),d(30,178),d(-45,-110),d(-68,15),d(-70,-170))),
            // Held high, then flicked down to the right, then at rest.
            k(3.13,world(.24,-.04,10,-4,0,d(68,14),d(78,4),d(-50,-100),d(-62,25),d(-64,-165))),
            k(3.24,world(.12,-.05,0,6,0,d(-45,42),d(-38,72),d(-80,-12),d(-75,20),d(-78,-160)),Anim.Ease.OUT),
            k(REST,KagenoClips.IDLE)));
        CLIP=Anim.joints(keys.toArray(Key[]::new));
    }
    @Override public Anim clip(int combo){return CLIP;}
    @Override public float lifetime(){return 3.9f;}

    // ───────────────────────────── poses ─────────────────────────────

    static double[] d(double elevation,double azimuth){return new double[]{elevation,azimuth};}
    /** Body-space direction of the world direction (elevation, azimuth toward the right of the facing) for a torso turned as in p. */
    static R6.Dir local(R6.Pose p,double elevation,double azimuth){
        float k=(float)Math.PI/180;
        // The frame of KagenoClips.blade with the facing along +z: the character's left is +x.
        var q=new Quaternionf().rotationAxis(p.yaw*k,0,1,0).rotateAxis(p.pitch*k,1,0,0).rotateAxis(p.roll*k,0,0,1);
        double e=Math.toRadians(elevation),a=Math.toRadians(azimuth);
        var w=new Vector3f((float)(-Math.cos(e)*Math.sin(a)),(float)Math.sin(e),(float)(Math.cos(e)*Math.cos(a)));
        q.conjugate().transform(w);
        return new R6.Dir(-w.x,w.y,w.z).norm();
    }
    /**
     * A pose written in the world: the root at (fw, up), the torso turned yaw degrees to the left, leaning pitch forward
     * and rolled; the limbs and the blade along world directions; the head facing the target, level.
     */
    static R6.Pose world(double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double[] rLeg,double[] lLeg){
        R6.Pose p=pose().at(fw,up,0).body(yaw,pitch,roll).head(yaw,-pitch*.8f,0);
        p.rArm=local(p,rArm[0],rArm[1]);p.blade=local(p,blade[0],blade[1]);p.lArm=local(p,lArm[0],lArm[1]);
        p.rLeg=local(p,rLeg[0],rLeg[1]);p.lLeg=local(p,lLeg[0],lLeg[1]);
        return p;
    }
    /** The sword held level across the chest pointing left; the left palm on the blade at azimuth lAz, the eyes on it. */
    static R6.Pose present(double lAz){
        return world(0,0,8,0,0,d(14,14),d(6,-88),d(12,lAz),d(-84,100),d(-84,-100)).head((float)(8+.6*lAz),12,0);
    }
    /** The stance: low, the torso turned right, the sword drawn back beside the head and level at the target, the left hand guiding it. */
    static R6.Pose stance(int deeper){
        return world(-.04-.04*deeper,-.2-.03*deeper,-40,8,0,d(36+4*deeper,165),d(-1,0),d(16,-4),d(-55,160),d(-58,-14));
    }
    /** Drawn back between thrusts, levelled at thrust i, in the lunge (right foot forward). */
    static R6.Pose cocked(int i){
        return world(.1,-.2,-18,8,0,d(22,122),d(AIM[i][0],AIM[i][1]),d(-38,-150),d(-52,22),d(-55,-168));
    }
    /** Thrust i fully out; the last one lunges deepest. */
    static R6.Pose thrust(int i){
        boolean last=i==THRUSTS.length-1;double e=AIM[i][0],a=AIM[i][1];
        return world(last?.4:.22+.04*(i%3),-.22,last?16:i%2==0?10:6,last?16:12,0,d(e-5,a+6),d(e,a),d(-40,-150),d(-50,22),d(-52,-168));
    }
    /** Coiled for the rising slash: crouched, turned right, the sword low behind. */
    static R6.Pose coil(int deeper){
        return world(.2,-.3-.04*deeper,-55-5*deeper,20,-6,d(-25,150),d(-20,160),d(4,-28),d(-48,128),d(-52,-30));
    }

    // ───────────────────────────── timing ─────────────────────────────

    /** Index of the latest thrust at or before sec (-1 before the first). */
    static int thrustAt(float sec){int i=-1;for(int k=0;k<THRUSTS.length;k++)if(THRUSTS[k]<=sec)i=k;return i;}
    /** Fraction of the blade lit, from the guard. */
    static float lit(float t){return Ease.smooth(t,LIT0,LIT1);}
    /** Brightness of the blade's neon: the ignition flicker, a kick on every thrust, the charge before the slash, out after the rest. */
    static float glow(float t){
        if(t<LIT0)return 0;
        float g=1;
        if(t>=LIT1&&t<.53f){float u=t-LIT1;g=u<.025f?1:u<.045f?.25f:u<.06f?1:u<.08f?.5f:1;}
        int i=thrustAt(t);if(i>=0&&t<COIL)g+=.6f*(float)Math.exp(-(t-THRUSTS[i])/.05);
        g+=.9f*Ease.smooth(t,COIL,RISE)*(1-Ease.smooth(t,SLASH,SLASH+.2f));
        return g*(1-Ease.smooth(t,3.12f,3.36f));
    }

    // ───────────────────────────── effects ─────────────────────────────

    /** Sword hand and blade direction of the rendered body at sec. */
    static Vec3d[] sword(Fx c,float sec){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(CLIP.frame(sec)));}
    static Vec3d aim(Fx c,int i){return c.dir(AIM[i][0],AIM[i][1]);}
    /** A unit vector across d. */
    static Vec3d across(Vec3d d){Vec3d x=d.crossProduct(new Vec3d(0,1,0));if(x.lengthSquared()<1e-4)x=d.crossProduct(new Vec3d(1,0,0));return x.normalize();}

    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;
        var hb=sword(c,t);Vec3d hand=hb[0],dir=hb[1],guard=hand.add(dir.multiply(GUARD)),tip=hand.add(dir.multiply(TIP));
        blade(m,t,guard,tip);
        ignition(m,t,guard,tip,dir);
        if(t>=.5f&&t<THRUSTS[0]){
            float u=Ease.unit(t,.5f,THRUSTS[0]);
            FxLib.converge(m,tip,1.8,22,t,.5f,THRUSTS[0]-.04f,VIOLET,7);
            FxLib.ring(m,c.pos.add(0,.04,0),2.3-1.4*u,.16,VIOLET,.6f*Ease.smooth(t,.5f,.58f));
            m.softDisc(tip,.12+.18*u,LAVENDER,.6f*u);
        }
        // The ground in front glows while the thrusts land.
        if(t>=THRUSTS[0]-.05f&&t<COIL+.1f)FxLib.glow(m,c.pos.add(c.f.multiply(1.8)).add(0,.04,0),2.0,INDIGO,.28f*Ease.smooth(t,THRUSTS[0]-.05f,THRUSTS[0]+.1f)*(1-Ease.smooth(t,COIL-.05f,COIL+.1f)));
        for(int i=0;i<THRUSTS.length;i++)thrust(c,m,t,i);
        coil(c,m,t,guard,tip);
        slash(c,m,t);
        // The neon flakes off the blade and drifts up as it goes out.
        if(t>=3.1f&&t<3.6f){
            float u=Ease.unit(t,3.1f,3.6f);
            for(int k=0;k<14;k++){
                Vec3d p=guard.add(tip.subtract(guard).multiply(Ease.hash(k*13+1))).add((Ease.hash(k*3)-.5)*.3*u,u*(.4+.8*Ease.hash(k*5)),(Ease.hash(k*7)-.5)*.3*u);
                m.softDisc(p,.05+.04*Ease.hash(k*11),k%3==0?CORE:HOT,(1-u)*(float)Math.sin(Math.PI*Math.min(1,u*2+.2)));
            }
        }
    }
    /** Glowing tube a→b in palette c (haze, glow, body, core). */
    static void neon(EffectMesh m,Vec3d a,Vec3d b,double width,float alpha,int[] c){
        if(alpha<=0)return;
        m.softLine(a,b,width*12,c[0],alpha*.12f);
        m.softLine(a,b,width*5.5,c[1],alpha*.3f);
        m.softLine(a,b,width*2.4,c[1],alpha*.75f);
        m.line(a,b,width,c[2],alpha);
        m.line(a,b,width*.45,c[3],alpha);
    }
    /** The blade's neon, lit from the guard as far as the palm has run. */
    static void blade(EffectMesh m,float t,Vec3d guard,Vec3d tip){
        float g=glow(t),f=lit(t);if(g<=0||f<=0)return;
        Vec3d end=guard.add(tip.subtract(guard).multiply(f));float on=Math.min(1,g);
        neon(m,guard,end,.065*(1+.35*Math.max(0,Math.min(1,g-1))),Math.min(1.6f,g),PINK);
        // A soft bloom along the lit blade.
        for(int k=0;k<=4;k++)m.softDisc(guard.add(end.subtract(guard).multiply(k/4.0)),.22,HOT,.14f*on);
        m.softDisc(end,.18+.12*on,HOT,.55f*on);
        if(f>=1)m.softDisc(tip,.08,CORE,.8f*Math.min(1,g));
    }
    /** The front of the neon runs up the blade under the palm; when it reaches the tip a ring pulses out and sparks fly off. */
    static void ignition(EffectMesh m,float t,Vec3d guard,Vec3d tip,Vec3d dir){
        if(t>=LIT0&&t<LIT1+.02f){
            Vec3d front=guard.add(tip.subtract(guard).multiply(lit(t)));
            m.star(front,.34,CORE,1);m.softDisc(front,.3,HOT,.8f);
            FxLib.sparks(m,front,7,.45,.025,LIGHT,.9f,(int)(t*60),1);
        }
        if(t>=LIT1&&t<LIT1+.22f){
            float u=Ease.unit(t,LIT1,LIT1+.22f);Vec3d mid=guard.add(tip).multiply(.5),a=across(dir),b=dir.crossProduct(a).normalize();
            FxLib.ringPlane(m,mid,a,b,.2+1.6*Ease.out(t,LIT1,LIT1+.22f),.16,VIOLET,.9f*(1-u));
            FxLib.flash(m,tip,.35*(1-u)+.1,VIOLET,.9f*(1-u));
            for(int k=0;k<8;k++){
                Vec3d p=guard.add(tip.subtract(guard).multiply(Ease.hash(k*7+3)));
                Vec3d out=a.multiply(Ease.hash(k*5)-.5).add(b.multiply(Ease.hash(k*9)-.5)).normalize();
                m.needle(p.add(out.multiply(.05+.6*u)),p.add(out.multiply(.25+.9*u)),.04,.3,LAVENDER,1-u);
            }
        }
    }
    /**
     * Thrust i: a lance of light shot ahead of the tip and sliding on as it fades (a faint line of it lingers),
     * two afterimage blades fanned around the real one, a flash and a ring of pressure at the tip, and on the
     * server's hits a burst where the blade bites.
     */
    static void thrust(Fx c,EffectMesh m,float t,int i){
        float u=t-THRUSTS[i];if(u<0||u>=.45f)return;
        var hb=sword(c,THRUSTS[i]);Vec3d hand=hb[0],dir=hb[1],tip=hand.add(dir.multiply(TIP)),aim=aim(c,i);
        float big=i==THRUSTS.length-1?1.3f:STRIKES[i]?1:.7f;
        if(u<.17f){
            double len=(2.2+.7*big)*Ease.out(u,0,.035f),start=.1+1.4*u;float a=1-Ease.smooth(u,.06f,.17f);
            Vec3d a0=tip.add(aim.multiply(start)),a1=a0.add(aim.multiply(len));
            m.needle(a0,a1,.6*big,.3,INDIGO,a*.35f);m.needle(a0,a1,.3*big,.32,VIOLET,a*.9f);m.needle(a0,a1,.1*big,.35,VCORE,a);
            m.star(a1,.22*big,VCORE,a);
            // A spray of thinner needles around it: many stabs at once.
            if(u<.11f){
                float s=1-u/.11f;int n=STRIKES[i]?5:3;
                for(int k=0;k<n;k++){
                    Vec3d d=c.dir(AIM[i][0]+(Ease.hash(i*17+k*3)-.5)*16,AIM[i][1]+(Ease.hash(i*23+k*5)-.5)*24);
                    Vec3d b0=tip.add(d.multiply(.2+2.2*u+.4*Ease.hash(i*7+k))),b1=b0.add(d.multiply((.8+.8*Ease.hash(i*13+k))*Ease.out(u,0,.03f)));
                    m.needle(b0,b1,.16,.35,VIOLET,s*.8f);m.needle(b0,b1,.05,.4,VCORE,s);
                }
            }
        }else{
            Vec3d a0=tip.add(aim.multiply(.1+1.4*.17)),a1=a0.add(aim.multiply(2.0+.3*big));
            m.softLine(a0,a1,.05,VIOLET,.22f*(1-Ease.unit(u,.17f,.45f)));
        }
        if(u<.09f){
            float a=.55f*(1-u/.09f);
            for(int g=0;g<2;g++){
                double side=g==0?-1:1;
                Vec3d d=c.dir(AIM[i][0]+(Ease.hash(i*11+g)-.5)*10,AIM[i][1]+side*(5+4*Ease.hash(i*7+g)));
                Vec3d h=hand.add(c.right.multiply(side*.1)).add(0,(Ease.hash(i*5+g)-.5)*.15,0);
                neon(m,h.add(d.multiply(GUARD)),h.add(d.multiply(TIP+.15)),.03,a,PINK);
            }
        }
        if(u<.14f){
            float a=1-u/.14f;Vec3d p=tip.add(aim.multiply(.25+.8*u)),x=across(aim),y=aim.crossProduct(x).normalize();
            FxLib.ringPlane(m,p,x,y,.15+1.1*big*Ease.out(u,0,.14f),.12,LAVENDER,.85f*a);
            if(u<.07f){float f=1-u/.07f;m.star(tip,(.4+.3*big)*f+.1,VCORE,1);m.softDisc(tip,.45*big,VIOLET,.7f*f);}
        }
        if(STRIKES[i]&&u<.24f){
            Vec3d p=bite(c,tip,aim);float a=1-Ease.smooth(u,.04f,.24f);
            FxLib.flash(m,p,(.8+.4*big)*(1-.5*u/.24f),VIOLET,a);
            FxLib.sparks(m,p,18,1.4*big,.05,LAVENDER,a,i*31+5,Ease.out(u,0,.1f));
            Vec3d x=across(aim),y=aim.crossProduct(x).normalize(),d1=x.add(y).normalize(),d2=x.subtract(y).normalize();double r=(.5+.6*u/.24f)*big;
            m.glowLine(p.subtract(d1.multiply(r)),p.add(d1.multiply(r)),.06,VIOLET,a);m.glowLine(p.subtract(d2.multiply(r)),p.add(d2.multiply(r)),.06,VIOLET,a);
        }
    }
    /** Where a thrust bites: the nearest body in the server's thrust lane, on its near side at the lance's height; else just past the tip. */
    static Vec3d bite(Fx c,Vec3d tip,Vec3d aim){
        if(c.mc.world!=null){
            LivingEntity best=null;double near=1e9;
            for(var e:c.mc.world.getEntitiesByClass(LivingEntity.class,new Box(c.pos,c.pos).expand(5),x->x.isAlive()&&!x.getUuid().equals(c.cast.caster()))){
                Vec3d off=e.getLerpedPos(c.delta).subtract(c.pos);double fw=off.dotProduct(c.f);
                if(fw<near&&KagenoRules.thrustLane(fw,off.dotProduct(c.right),off.y,e.getWidth()*.5)){near=fw;best=e;}
            }
            if(best!=null){
                Vec3d b=best.getLerpedPos(c.delta);double y=Math.max(b.y+.3,Math.min(b.y+best.getHeight()-.2,tip.y));
                return new Vec3d(b.x,y,b.z).subtract(c.f.multiply(best.getWidth()*.5));
            }
        }
        return tip.add(aim.multiply(.9));
    }
    /** The coil: energy spirals into the blade, a ring tightens on the ground and sparks drip off the tip. */
    static void coil(Fx c,EffectMesh m,float t,Vec3d guard,Vec3d tip){
        if(t<COIL-.05f||t>=RISE+.03f)return;
        float u=Ease.unit(t,COIL-.05f,RISE);Vec3d mid=guard.add(tip).multiply(.5);
        FxLib.converge(m,mid,2.4,30,t,COIL-.05f,RISE-.05f,LAVENDER,19);
        FxLib.ring(m,c.pos.add(0,.04,0),2.8-1.7*u,.2,VIOLET,.7f*Ease.smooth(t,COIL-.05f,COIL+.08f));
        FxLib.glow(m,c.pos.add(0,.03,0),1.6,INDIGO,.35f*u);
        for(int k=0;k<6;k++){float life=((t-COIL)*2.2f+k/6f)%1;if(life<0)life+=1;m.star(tip.add((Ease.hash(k*3)-.5)*.2,-life*.9,(Ease.hash(k*5)-.5)*.2),.06,LAVENDER,(1-life)*u);}
    }
    /** Plane of the rising slash: through the sword shoulder at the cut, along the facing and up, tilted 12° toward the left. */
    static Vec3d slashCentre(Fx c){return c.pos.add(c.f.multiply(.42)).add(0,1.5,0).add(c.right.multiply(.3));}
    static Vec3d slashUp(Fx c){double k=Math.toRadians(12);return new Vec3d(0,Math.cos(k),0).add(c.left.multiply(Math.sin(k)));}
    /** Angle of the blade in the slash plane at sec (0 straight ahead, 90 straight up). */
    static double swingAngle(Fx c,float sec){Vec3d d=sword(c,sec)[1];return Math.toDegrees(Math.atan2(d.dotProduct(slashUp(c)),d.dotProduct(c.f)));}
    /**
     * The rising slash: the blade's hot trail, a crescent growing with it from below the target to over the head
     * that holds and burns away from its tail; at the finisher a flash, sparks fountaining up, a ground shock,
     * a geyser of violet light with rings racing up it, and embers rising after.
     */
    static void slash(Fx c,EffectMesh m,float t){
        if(t<RISE||t>=3.7f)return;
        Vec3d o=slashCentre(c),u=c.f,v=slashUp(c);
        if(t<OVER+.05f){
            float a=1-Ease.smooth(t,OVER-.04f,OVER+.05f);
            trail(c,m,t,.07f,8,.3,TIP,VIOLET,.85f*a);trail(c,m,t,.05f,6,.85,TIP,VCORE,.7f*a);
        }
        if(t>=2.86f&&t<3.3f){
            double from=-48,head=Math.max(from,Math.min(118,swingAngle(c,Math.min(t,OVER))));
            float burn=Ease.unit(t,3.02f,3.3f);double tail=from+(head-from)*burn;
            band(m,o,u,v,1.25,2.3,tail,head,1-.6f*burn);
            neonArc(m,o,u,v,2.25,tail,head,.09,1-burn*.5f);
            neonArc(m,o,u,v,1.65,tail+(head-tail)*.25,head,.04,.7f*(1-burn));
        }
        Vec3d bite=bite(c,c.pos.add(c.f.multiply(1.9)).add(0,1.1,0),c.f),ground=new Vec3d(bite.x,c.pos.y,bite.z);
        if(t>=SLASH&&t<SLASH+.32f){
            float a=1-Ease.smooth(t,SLASH+.05f,SLASH+.32f),grow=Ease.out(t,SLASH,SLASH+.16f);
            FxLib.flash(m,bite,1.1*(1-.4*Ease.unit(t,SLASH,SLASH+.32f)),VIOLET,a);
            for(int k=0;k<16;k++){
                double az=Ease.hash(k*3+1)*Math.PI*2,el=Math.toRadians(35+50*Ease.hash(k*5+2)),l=(1.2+1.8*Ease.hash(k*7))*grow;
                Vec3d d=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
                m.needle(bite.add(d.multiply(l*.3)),bite.add(d.multiply(l)),.06,.3,k%3==0?VCORE:LAVENDER,a);
            }
            FxLib.ring(m,ground.add(0,.05,0),.3+3.6*Ease.out(t,SLASH,SLASH+.32f),.35,VIOLET,.8f*a);
        }
        if(t>=SLASH&&t<3.55f){
            float a=1-Ease.smooth(t,3.08f,3.55f);double h=8*Ease.out(t,SLASH,SLASH+.12f);
            FxLib.column(m,ground,h,.35,VIOLET,a);
            m.needle(ground,ground.add(0,h*.85,0),.9,.15,INDIGO,a*.25f);
            for(int k=0;k<6;k++){float born=SLASH+k*.05f,r=(t-born)/.45f;if(r<0||r>=1)continue;
                FxLib.ring(m,ground.add(0,.3+r*h*.9,0),.55+.5*r,.12,k%2==0?LAVENDER:VIOLET,.75f*(1-r)*a);}
            FxLib.glow(m,ground.add(0,.04,0),1.7,INDIGO,.5f*a);
        }
        if(t>=SLASH+.05f)FxLib.embers(m,ground,1.3,4.5,26,t-SLASH-.05f,1.4f,.07,LAVENDER,Ease.smooth(t,SLASH+.05f,SLASH+.15f)*(1-Ease.smooth(t,3.4f,3.7f)),23);
    }
    /** Ribbon swept by the blade (from..to blocks along it) over the last {@code span} seconds, on the moving body. */
    static void trail(Fx c,EffectMesh m,float t,float span,int samples,double from,double to,int rgb,float alpha){
        if(alpha<=0)return;Vec3d[] a=new Vec3d[samples+1],b=new Vec3d[samples+1];
        for(int i=0;i<=samples;i++){var hb=sword(c,Math.max(0,t-span*i/samples));a[i]=hb[0].add(hb[1].multiply(from));b[i]=hb[0].add(hb[1].multiply(to));}
        for(int i=0;i<samples;i++){
            float a0=alpha*(1-(float)i/samples),a1=alpha*(1-(float)(i+1)/samples);
            m.vertex(a[i],rgb,a0*.35f);m.vertex(b[i],rgb,a0);m.vertex(b[i+1],rgb,a1);m.vertex(a[i+1],rgb,a1*.35f);
        }
    }
    /** Band of the crescent in the plane (u,v) from tail to head (degrees): bright at the outer edge, fading inward, thin at the tail. */
    static void band(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double inner,double outer,double tail,double head,float alpha){
        if(alpha<=0||head-tail<1)return;int n=Math.max(4,(int)((head-tail)/4));
        for(int i=0;i<n;i++){
            double a0=Math.toRadians(tail+(head-tail)*i/n),a1=Math.toRadians(tail+(head-tail)*(i+1)/n);
            float w0=(float)Math.pow((double)i/n,.6),w1=(float)Math.pow((double)(i+1)/n,.6);
            Vec3d d0=u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))),d1=u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
            m.vertex(o.add(d0.multiply(outer-(outer-inner)*w0)),INDIGO,0);m.vertex(o.add(d1.multiply(outer-(outer-inner)*w1)),INDIGO,0);
            m.vertex(o.add(d1.multiply(outer)),VIOLET,alpha*w1);m.vertex(o.add(d0.multiply(outer)),VIOLET,alpha*w0);
        }
    }
    /** Neon tube along the crescent's outer edge, thickening toward its head, which glows. */
    static void neonArc(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double r,double tail,double head,double width,float alpha){
        if(alpha<=0||head-tail<1)return;int n=Math.max(4,(int)((head-tail)/5));Vec3d prev=null;
        for(int i=0;i<=n;i++){
            double a=Math.toRadians(tail+(head-tail)*i/n);Vec3d p=o.add(u.multiply(Math.cos(a)*r)).add(v.multiply(Math.sin(a)*r));
            if(prev!=null){float k=(float)i/n;neon(m,prev,p,width*(.3+.7*k),alpha*(.25f+.75f*k),PURPLE);}
            prev=p;
        }
        m.softDisc(prev,.35,VIOLET,.6f*alpha);
    }

    // ───────────────────────────── screen ─────────────────────────────

    @Override public float flash(Fx c){float t=c.sec;return t>=SLASH&&t<SLASH+.1f?.14f*(1-Ease.unit(t,SLASH,SLASH+.1f)):0;}
    @Override public int flashColor(Fx c){return 0xd8c4ff;}
    /** Post-process shock: small on the server's thrust hits, strong on the slash. */
    @Override public float pulse(float sec){
        float p=0;
        for(int i=0;i<THRUSTS.length;i++)if(STRIKES[i]){float u=sec-THRUSTS[i];if(u>=0&&u<.12f)p=Math.max(p,.18f*(1-u/.12f));}
        if(sec>=SLASH&&sec<SLASH+.3f)p=Math.max(p,.7f*(1-Ease.smooth(sec,SLASH,SLASH+.3f)));
        return p;
    }
    @Override public float shake(float sec){return sec>=SLASH&&sec<SLASH+.35f?1.1f*(1-Ease.unit(sec,SLASH,SLASH+.35f)):0;}
    /** A small nod on every thrust, the view thrown up with the slash. */
    @Override public float[] kick(float sec){
        if(sec>=SLASH&&sec<SLASH+.4f){float k=(float)Math.exp(-(sec-SLASH)/.1);return new float[]{0,-2.4f*k,.9f*k};}
        int i=thrustAt(sec);
        if(i>=0&&sec<COIL){float u=sec-THRUSTS[i];if(u<.08f){float k=1-u/.08f;return new float[]{(i%2==0?.3f:-.3f)*k,.5f*k*(STRIKES[i]?1:.6f),0};}}
        return null;
    }
}
