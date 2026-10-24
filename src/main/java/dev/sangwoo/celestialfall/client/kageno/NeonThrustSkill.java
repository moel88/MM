package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
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
 * E "신속한 맹공", after the user's reference recordings. The blade flares pink neon as the body steps side-on
 * into a lunge with the sword levelled at the target; from 0.48 s fifteen thrusts (the server's seven hits,
 * {@link KagenoRules#eTicks}, one between each pair and two openers) pour a storm out ahead of the hand: a
 * magenta core, streaks of light shooting ahead, hooked dark lightning, a thin beam standing through the core,
 * afterimage stabs, purple haze and motes. The sword is drawn back behind the hip, swept low in front and swung up over
 * the head and down behind; as it comes down the target bursts (the server's finisher, {@link KagenoRules#E_FINISH})
 * into a white-pink flash and a pillar of light, while the dark-cored magenta crescent over the caster spreads into
 * rings that cool to blue-violet. Only the sword is pink; the skill's light is magenta and purple. Times are seconds
 * after the cast and every effect is a pure function of the cast clock, so it moves at the render frame rate.
 * Through the thrusts the caster walks and turns with his aim, and the storm turns with him (user request, 2.38.2:
 * {@link KagenoAim#thrust}); the finisher is planted the way he faced as the thrusts ended.
 */
final class NeonThrustSkill implements Fx.Skill {
    /** The sword's neon is pink: white-hot core, light pink, hot pink, magenta. */
    static final int CORE=0xfff2fb,LIGHT=0xffa6e2,HOT=0xff48c4,MAGENTA=0xff1c98;
    /** The skill's light, picked off the reference: white-pink glare, light fuchsia, fuchsia, purple, violet, the blue-violet the rings cool to, the dark of the lightning. */
    static final int GLARE=0xffe6ff,PINKISH=0xfe8bfe,FUCHSIA=0xfd17fc,PURPLE=0x9d45d3,VIOLET=0x6832b9,BLUE=0x6f5cff,SHADOW=0x1c0634;
    /** Tube palettes (haze, glow, body, core): the sword's pink neon, the skill's magenta. */
    static final int[] PINK={MAGENTA,HOT,LIGHT,CORE},ARC={VIOLET,FUCHSIA,PINKISH,GLARE};
    /** The neon has run up the blade by FLARE; the lunge is set at LUNGE; the storm ahead of the hand blows from BARRAGE until CALM. */
    static final float FLARE=.12f,LUNGE=.11f,BARRAGE=.48f,CALM=2.42f;
    /** The sword drawn back behind the hip, swept low in front, straight up, the burst on the target (the server's finisher), level behind, the rest. */
    static final float BACK=2.47f,LOW=2.63f,UPRIGHT=2.72f,SLASH=KagenoRules.E_FINISH/20f,LEVEL=2.96f,REST=3.35f;
    /** Two thrusts open the barrage before the server's first hit. */
    static final float[] OPENERS={.55f,.675f};
    /** Thrust times (seconds) and whether the server strikes on each. */
    static final float[] THRUSTS;
    static final boolean[] STRIKES;
    /** Where each thrust goes: elevation and azimuth (degrees, toward the right) from the cast facing. */
    static final double[][] AIM={{3,2},{-4,-6},{2,0},{9,-7},{-5,6},{12,4},{-8,-5},{5,9},{0,-2},{10,-9},{-6,4},{7,8},{-3,-6},{11,2},{0,0}};
    /** The guard and the tip, in blocks along the blade from the hand. */
    static final double GUARD=.24,TIP=1.35;
    static final Anim CLIP;
    static {
        int[] ticks=KagenoRules.eTicks();
        THRUSTS=new float[OPENERS.length+ticks.length*2-1];STRIKES=new boolean[THRUSTS.length];
        System.arraycopy(OPENERS,0,THRUSTS,0,OPENERS.length);
        for(int i=0;i<ticks.length;i++){int k=OPENERS.length+2*i;THRUSTS[k]=ticks[i]/20f;STRIKES[k]=true;if(i+1<ticks.length)THRUSTS[k+1]=(ticks[i]+ticks[i+1])/40f;}
        List<Key> keys=new ArrayList<>(List.of(
            k(0,KagenoClips.IDLE),
            // A step into the lunge: side-on, the sword arm straight at the target, the free arm back.
            k(.06,world(.05,-.1,30,6,0,d(-25,22),d(-10,8),d(-40,-150),d(-68,18),d(-72,-175)),Anim.Ease.IN),
            k(LUNGE,lunge(.14,0),Anim.Ease.OUT),
            k(.3,lunge(.2,1)),
            k(BARRAGE,cocked(0),Anim.Ease.SMOOTH)));
        for(int i=0;i<THRUSTS.length;i++){
            keys.add(k(THRUSTS[i],thrust(i),Anim.Ease.OUT));
            if(i+1<THRUSTS.length)keys.add(k(THRUSTS[i]+.06f,cocked(i+1)));
        }
        keys.addAll(List.of(
            // Drawn back: the blade swings round to lie level behind the hip.
            k(2.41,world(.16,-.2,20,4,0,d(-30,95),d(-20,130),d(-30,-60),d(-50,16),d(-50,188))),
            k(BACK,back(0),Anim.Ease.OUT),
            k(2.56,back(1)),
            // Swept down and forward under the arm, crouched low with the blade low in front.
            k(LOW,world(.16,-.34,8,24,0,d(-45,25),d(-22,10),d(-40,-110),d(-45,20),d(-48,-172)),Anim.Ease.IN),
            // Swung up through the front, straight up, over the head and down behind.
            k(2.665,world(.2,-.2,2,10,0,d(28,8),d(38,2),d(-30,-120),d(-55,18),d(-58,-172)),Anim.Ease.LINEAR),
            k(UPRIGHT,world(.22,-.08,-4,-4,0,d(80,6),d(88,0),d(-25,-125),d(-62,16),d(-64,-174)),Anim.Ease.LINEAR),
            k(2.78,world(.22,-.06,-6,-8,0,d(80,172),d(52,178),d(-22,-110),d(-64,16),d(-66,-174))),
            k(2.86,world(.2,-.08,-8,-8,0,d(52,172),d(30,178),d(-20,-100),d(-62,16),d(-64,-174))),
            k(LEVEL,world(.18,-.1,-10,-4,0,d(18,168),d(3,178),d(-20,-90),d(-60,16),d(-62,-174))),
            k(3.12,world(.16,-.08,-10,-2,0,d(14,168),d(0,178),d(-25,-90),d(-62,16),d(-64,-174))),
            // Brought round the right side, the tip kept off the ground, and at rest.
            k(3.24,world(.1,-.05,0,4,0,d(-50,50),d(-30,70),d(-70,-20),d(-75,16),d(-78,-172))),
            k(REST,KagenoClips.IDLE)));
        CLIP=Anim.joints(keys.toArray(Key[]::new)).stops(Anim.stop(SLASH,.06,.1));
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
    /** The lunge: low and side-on (the sword shoulder leading), the sword arm and blade level at the target, the free arm back. */
    static R6.Pose lunge(double fw,int k){
        return world(fw,-.2-.02*k,60,6,0,d(0,4),d(1,2),d(-16,182),d(-50,16),d(-50,188));
    }
    /** Drawn in between thrusts, the shoulder turned back and the hand pulled in, the blade levelled at thrust i. */
    static R6.Pose cocked(int i){
        return world(.12,-.22,38,6,0,d(-18,62),d(AIM[i][0],AIM[i][1]),d(-24,178),d(-50,16),d(-50,188));
    }
    /** Thrust i fully out, the sword shoulder driven forward; the last one lunges deepest. */
    static R6.Pose thrust(int i){
        boolean last=i==THRUSTS.length-1;double e=AIM[i][0],a=AIM[i][1];
        return world(last?.34:.22+.04*(i%3),-.24,last?68:62+3*(i%2),6,0,d(e-3,a+4),d(e,a),d(-14,184),d(-50,16),d(-50,188));
    }
    /** The sword drawn back, the blade level behind the hip, the chest turned to the right. */
    static R6.Pose back(int k){
        return world(.1-.02*k,-.16-.02*k,-25,4,0,d(-50,160),d(-6,176),d(-40,-40),d(-50,16),d(-52,188));
    }

    /**
     * The thrusts walked (user request, 2.38.2): at {@code w} 1 the legs walk (the model's walk cycle) and the body
     * stays over them, without the lunge or the crouch and with a lighter lean, while the side-on twist and the arms
     * stay as they are so the blade still points the way the caster aims.
     */
    static R6.Frame walking(R6.Frame f,float w){
        if(w<=0)return f;
        return new R6.Frame(f.joints(),f.grip(),f.fw()*(1-w),f.up()*(1-.8f*w),f.rt()*(1-w),f.yaw(),f.pitch()*(1-.5f*w),f.roll()*(1-.5f*w));
    }

    // ───────────────────────────── timing ─────────────────────────────

    /** Index of the latest thrust at or before sec (-1 before the first). */
    static int thrustAt(float sec){int i=-1;for(int k=0;k<THRUSTS.length;k++)if(THRUSTS[k]<=sec)i=k;return i;}
    /** Fraction of the blade lit, from the guard: the neon runs up it as the lunge is set. */
    static float lit(float t){return Ease.smooth(t,0,FLARE);}
    /** Brightness of the blade's neon: a flicker as it catches, a kick on every thrust, the charge through the swing, out after the rest. */
    static float glow(float t){
        if(t<=0)return 0;
        float g=1;
        if(t>=FLARE&&t<FLARE+.08f){float u=t-FLARE;g=u<.02f?1.4f:u<.035f?.4f:u<.05f?1.2f:u<.065f?.6f:1;}
        int i=thrustAt(t);if(i>=0&&t<BACK)g+=.6f*(float)Math.exp(-(t-THRUSTS[i])/.05);
        g+=.9f*Ease.smooth(t,BACK,LOW)*(1-Ease.smooth(t,SLASH,SLASH+.2f));
        return g*(1-Ease.smooth(t,3.12f,3.36f));
    }
    /** Strength of the storm ahead of the hand. */
    static float storm(float t){return Ease.smooth(t,BARRAGE-.03f,BARRAGE+.08f)*(1-Ease.smooth(t,2.36f,CALM));}

    // ───────────────────────────── effects ─────────────────────────────

    /** Sword hand and blade direction of the rendered body at sec (walking or not, facing the caster's aim). */
    static Vec3d[] sword(Fx c,float sec){return KagenoClips.blade(c.pos,c.yaw,R6.poseOf(walking(CLIP.frame(sec),KagenoClips.walkWeight(c.cast,c.delta))));}
    static Vec3d aim(Fx c,int i){return c.dir(AIM[i][0],AIM[i][1]);}
    /** A unit vector across d. */
    static Vec3d across(Vec3d d){Vec3d x=d.crossProduct(new Vec3d(0,1,0));if(x.lengthSquared()<1e-4)x=d.crossProduct(new Vec3d(1,0,0));return x.normalize();}
    /** The heart of the storm: just ahead of the sword hand at full stretch. */
    static Vec3d core(Fx c){var hb=sword(c,THRUSTS[2]);return hb[0].add(hb[1].multiply(.7));}
    /** Unit vector at angle rad in the plane (u,v). */
    static Vec3d in(Vec3d u,Vec3d v,double rad){return u.multiply(Math.cos(rad)).add(v.multiply(Math.sin(rad)));}
    /** Colour k of the way from a to b. */
    static int mix(int a,int b,float k){
        int r=Math.round(((a>>16)&255)*(1-k)+((b>>16)&255)*k),g=Math.round(((a>>8)&255)*(1-k)+((b>>8)&255)*k),bl=Math.round((a&255)*(1-k)+(b&255)*k);
        return r<<16|g<<8|bl;
    }

    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;
        var hb=sword(c,t);Vec3d hand=hb[0],dir=hb[1],guard=hand.add(dir.multiply(GUARD)),tip=hand.add(dir.multiply(TIP));
        blade(m,t,guard,tip);
        flare(c,m,t,guard,tip,dir);
        storm(c,m,t);
        for(int i=0;i<THRUSTS.length;i++)thrust(c,m,t,i);
        afterimages(c,m,t);
        // The real blade smears through each thrust.
        if(t>=THRUSTS[0]-.05f&&t<BACK)trail(c,m,t,.05f,5,GUARD,TIP,HOT,.45f);
        swing(c,m,t);
        burst(c,m,t);
        // The neon flakes off the blade and drifts up as it goes out.
        if(t>=3.1f&&t<3.6f){
            float u=Ease.unit(t,3.1f,3.6f);
            for(int k=0;k<14;k++){
                Vec3d p=guard.add(tip.subtract(guard).multiply(Ease.hash(k*13+1))).add((Ease.hash(k*3)-.5)*.3*u,u*(.4+.8*Ease.hash(k*5)),(Ease.hash(k*7)-.5)*.3*u);
                m.softDisc(p,.05+.04*Ease.hash(k*11),k%3==0?CORE:HOT,(1-u)*(float)Math.sin(Math.PI*Math.min(1,u*2+.2)));
            }
        }
    }
    /**
     * The colour and the dark, blended over the scene so the magenta and purple stay saturated against a bright sky:
     * the storm's magenta heart, purple haze and streak bodies, the lightning's dark cores and the ring the storm turns in;
     * the swing's crescent with its dark inside, the whirl when the target bursts and the rings it spreads into.
     */
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec,s=storm(t);
        if(s>0){
            Vec3d core=core(c);int i=thrustAt(t);float kick=i>=0?(float)Math.exp(-(t-THRUSTS[i])/.06):0;
            m.softDisc(core,1.15+.4*kick,FUCHSIA,.42f*s);
            streaks(c,m,t,core,s,true);
            bolts(c,m,t,core,s,true);
            band(m,core,c.right,FxLib.UP,1.05,1.4,1.75,0,360,SHADOW,.14f*s);
        }
        vortex(c,m,t);
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
    /** The blade's neon, lit from the guard as far as it has run. */
    static void blade(EffectMesh m,float t,Vec3d guard,Vec3d tip){
        float g=glow(t),f=lit(t);if(g<=0||f<=0)return;
        Vec3d end=guard.add(tip.subtract(guard).multiply(f));float on=Math.min(1,g);
        neon(m,guard,end,.065*(1+.35*Math.max(0,Math.min(1,g-1))),Math.min(1.6f,g),PINK);
        // A soft bloom along the lit blade.
        for(int k=0;k<=4;k++)m.softDisc(guard.add(end.subtract(guard).multiply(k/4.0)),.22,HOT,.14f*on);
        m.softDisc(end,.18+.12*on,HOT,.55f*on);
        if(f>=1)m.softDisc(tip,.08,CORE,.8f*Math.min(1,g));
    }
    /** The neon runs up the blade with the step and rings out when it reaches the tip; through the lunge glints run along it and motes gather. */
    static void flare(Fx c,EffectMesh m,float t,Vec3d guard,Vec3d tip,Vec3d dir){
        if(t>0&&t<FLARE+.01f){
            Vec3d front=guard.add(tip.subtract(guard).multiply(lit(t)));
            m.star(front,.4,GLARE,1);m.softDisc(front,.35,FUCHSIA,.8f);
            FxLib.sparks(m,front,7,.5,.025,PINKISH,.9f,(int)(t*120),1);
        }
        if(t>=FLARE&&t<FLARE+.22f){
            float u=Ease.unit(t,FLARE,FLARE+.22f);Vec3d mid=guard.add(tip).multiply(.5),a=across(dir),b=dir.crossProduct(a).normalize();
            FxLib.ringPlane(m,mid,a,b,.2+1.6*Ease.out(t,FLARE,FLARE+.22f),.16,FUCHSIA,.9f*(1-u));
            FxLib.flash(m,tip,.35*(1-u)+.1,FUCHSIA,.9f*(1-u));
        }
        if(t>=LUNGE&&t<BARRAGE+.05f){
            float a=Ease.smooth(t,LUNGE,LUNGE+.08f)*(1-Ease.smooth(t,BARRAGE-.03f,BARRAGE+.05f));
            for(int k=0;k<9;k++){
                float ph=(float)(((t-LUNGE)*2.6+Ease.hash(k*5))%1);
                Vec3d p=guard.add(tip.subtract(guard).multiply(Ease.hash(k*7+1))).add(c.vec(-.3*ph,.25*ph+(Ease.hash(k*9)-.5)*.15,(Ease.hash(k*11)-.5)*.2));
                m.star(p,.1+.06*Ease.hash(k*13),k%2==0?GLARE:PINKISH,a*(float)Math.sin(Math.PI*ph));
            }
            FxLib.converge(m,guard.add(tip).multiply(.5),1.6,18,t,LUNGE,BARRAGE-.05f,PURPLE,7);
            FxLib.ring(m,c.pos.add(0,.04,0),1.8-.8*Ease.unit(t,LUNGE,BARRAGE),.14,FUCHSIA,.5f*a);
        }
    }
    /**
     * The storm's light while the thrusts land: the white-pink heart swelling with every thrust, a thin beam standing
     * through it, the lit ground, pink motes, broken arcs whirling round the dark ring it turns in, the streaks' bright
     * cores and the glow round the dark lightning (the colour and the dark are in the alpha pass).
     */
    static void storm(Fx c,EffectMesh m,float t){
        float s=storm(t);if(s<=0)return;
        Vec3d core=core(c),foot=new Vec3d(core.x,c.pos.y,core.z);
        int i=thrustAt(t);float kick=i>=0?(float)Math.exp(-(t-THRUSTS[i])/.06):0;
        float flick=.8f+.2f*(float)(Math.sin(t*53)*Math.sin(t*31+2));
        m.softDisc(core,1.5+.5*kick,FUCHSIA,.18f*s*flick);
        m.softDisc(core,.8+.3*kick,PINKISH,.45f*s);
        m.softDisc(core,.38+.2*kick,GLARE,.9f*s);
        m.star(core,.6+.4*kick,GLARE,.6f*s*flick);
        Vec3d top=foot.add(0,7,0);
        m.softLine(foot,top,.4,FUCHSIA,.22f*s*flick);m.line(foot,top,.04,GLARE,.65f*s*flick);
        FxLib.glow(m,foot.add(0,.04,0),2.4,FUCHSIA,.25f*s);
        FxLib.embers(m,c.pos.add(c.f.multiply(.9)),2.0,2.8,20,t-BARRAGE,1.7f,.07,PINKISH,s,41);
        // Broken arcs whirl round the rim of the dark ring.
        for(int k=0;k<3;k++){double rot=k*120+t*260;band(m,core,c.right,FxLib.UP,1.32,1.45,1.56,rot,rot+70,VIOLET,.55f*s);}
        streaks(c,m,t,core,s,false);
        bolts(c,m,t,core,s,false);
    }
    /** A streak leaves the core every STREAK seconds of the storm: most shoot ahead in a cone, the rest every way. */
    static final float STREAK=1/150f;
    /** The streaks alive at t: body draws their magenta bodies (alpha pass), else their white-pink cores. */
    static void streaks(Fx c,EffectMesh m,float t,Vec3d core,float s,boolean body){
        int newest=(int)Math.floor((Math.min(t,2.36f)-BARRAGE)/STREAK);
        for(int k=Math.max(0,newest-25);k<=newest;k++){
            float born=BARRAGE+k*STREAK,life=.09f+.06f*(float)Ease.hash(k*7+1),u=(t-born)/life;if(u<0||u>=1)continue;
            boolean ahead=Ease.hash(k*11+2)<.75;
            Vec3d d=ahead?c.dir((Ease.hash(k*13+3)-.5)*40,(Ease.hash(k*17+4)-.5)*70):c.dir((Ease.hash(k*13+3)-.5)*120,Ease.hash(k*17+4)*360);
            double speed=12+10*Ease.hash(k*19+5),len=(1.4+2.0*Ease.hash(k*23+6))*Math.min(1,u*4);
            Vec3d head=core.add(d.multiply(.3+speed*life*u)),tail=head.subtract(d.multiply(len));
            float a=s*(1-u*u);
            if(body)m.needle(tail,head,.24,.7,FUCHSIA,a*.6f);
            else{m.needle(tail,head,.09,.75,PINKISH,a*.9f);m.line(tail.add(d.multiply(len*.4)),head,.03,GLARE,a);}
        }
    }
    /** A hooked dark bolt cracks out of the storm every BOLT seconds and lives .12–.2 s; dark draws its core, else its purple glow. */
    static final float BOLT=.04f;
    static void bolts(Fx c,EffectMesh m,float t,Vec3d core,float s,boolean dark){
        if(s<=0)return;
        int newest=(int)Math.floor((Math.min(t,2.38f)-BARRAGE)/BOLT);
        for(int k=Math.max(0,newest-5);k<=newest;k++){
            float born=BARRAGE+k*BOLT,life=.12f+.08f*(float)Ease.hash(k*5+2),u=(t-born)/life;if(u<0||u>=1)continue;
            Vec3d[] p=boltPath(c,core,k);float grow=Math.min(1,u/.3f)*(p.length-1),a=s*(1-Ease.smooth(u,.45f,1));
            for(int j=1;j<p.length&&j-1<grow;j++){
                Vec3d a0=p[j-1],a1=j<=grow?p[j]:p[j-1].add(p[j].subtract(p[j-1]).multiply(grow-(j-1)));
                double w=.18*(1-.5*(j-1)/(p.length-1));
                if(dark){m.line(a0,a1,w,SHADOW,.9f*a);continue;}
                // The glow runs along both sides of the dark core, leaving it dark.
                Vec3d side=a1.subtract(a0).crossProduct(m.camera().subtract(a0));if(side.lengthSquared()<1e-10)continue;side=side.normalize().multiply(w*1.3);
                int rim=mix(PURPLE,FUCHSIA,.4f);
                m.softLine(a0.add(side),a1.add(side),w*1.6,rim,.9f*a);m.softLine(a0.subtract(side),a1.subtract(side),w*1.6,rim,.9f*a);
                m.softLine(a0.add(side.multiply(1.6)),a1.add(side.multiply(1.6)),w*5,VIOLET,.16f*a);m.softLine(a0.subtract(side.multiply(1.6)),a1.subtract(side.multiply(1.6)),w*5,VIOLET,.16f*a);
            }
        }
    }
    /** Path of bolt k: a few sharp zigzags out of the storm, mostly upward, ending in a hook bent back. */
    static Vec3d[] boltPath(Fx c,Vec3d core,int k){
        int n=4+(int)(Ease.hash(k*3+1)*3);Vec3d[] p=new Vec3d[n+2];
        p[0]=core.add(c.vec((Ease.hash(k*7+2)-.5)*.6,(Ease.hash(k*9+3)-.3)*.7,(Ease.hash(k*11+4)-.5)*.9));
        Vec3d d=c.dir(25+55*Ease.hash(k*13+5),(Ease.hash(k*17+6)-.5)*260);
        for(int i=1;i<=n;i++){
            double phi=Ease.hash(k*19+i)*Math.PI*2,turn=Math.toRadians((i%2==0?1:-1)*(30+50*Ease.hash(k*23+i)));
            Vec3d x=across(d),y=d.crossProduct(x).normalize(),axis=x.multiply(Math.cos(phi)).add(y.multiply(Math.sin(phi)));
            d=d.multiply(Math.cos(turn)).add(axis.crossProduct(d).multiply(Math.sin(turn))).normalize();
            p[i]=p[i-1].add(d.multiply(.4+.4*Ease.hash(k*29+i)));
        }
        double turn=Math.toRadians(125);Vec3d x=across(d);
        p[n+1]=p[n].add(d.multiply(Math.cos(turn)).add(x.crossProduct(d).multiply(Math.sin(turn))).normalize().multiply(.3));
        return p;
    }
    /**
     * Thrust i: a lance of light shot ahead of the tip and sliding on as it fades (a faint line of it lingers),
     * a flash and a ring of pressure at the tip, and on the server's hits a burst where the blade bites.
     */
    static void thrust(Fx c,EffectMesh m,float t,int i){
        float u=t-THRUSTS[i];if(u<0||u>=.45f)return;
        var hb=sword(c,THRUSTS[i]);Vec3d hand=hb[0],dir=hb[1],tip=hand.add(dir.multiply(TIP)),aim=aim(c,i);
        float big=i==THRUSTS.length-1?1.3f:STRIKES[i]?1:.7f;
        if(u<.17f){
            double len=(2.4+.8*big)*Ease.out(u,0,.035f),start=.1+1.5*u;float a=1-Ease.smooth(u,.06f,.17f);
            Vec3d a0=tip.add(aim.multiply(start)),a1=a0.add(aim.multiply(len));
            m.needle(a0,a1,.6*big,.3,VIOLET,a*.35f);m.needle(a0,a1,.3*big,.32,FUCHSIA,a*.9f);m.needle(a0,a1,.1*big,.35,GLARE,a);
            m.star(a1,.22*big,GLARE,a);
            // A spray of thinner needles around it: many stabs at once.
            if(u<.11f){
                float s=1-u/.11f;int n=STRIKES[i]?5:3;
                for(int k=0;k<n;k++){
                    Vec3d d=c.dir(AIM[i][0]+(Ease.hash(i*17+k*3)-.5)*16,AIM[i][1]+(Ease.hash(i*23+k*5)-.5)*24);
                    Vec3d b0=tip.add(d.multiply(.2+2.2*u+.4*Ease.hash(i*7+k))),b1=b0.add(d.multiply((.8+.8*Ease.hash(i*13+k))*Ease.out(u,0,.03f)));
                    m.needle(b0,b1,.16,.35,FUCHSIA,s*.8f);m.needle(b0,b1,.05,.4,GLARE,s);
                }
            }
        }else{
            Vec3d a0=tip.add(aim.multiply(.1+1.5*.17)),a1=a0.add(aim.multiply(2.2+.3*big));
            m.softLine(a0,a1,.05,FUCHSIA,.2f*(1-Ease.unit(u,.17f,.45f)));
        }
        if(u<.14f){
            float a=1-u/.14f;Vec3d p=tip.add(aim.multiply(.25+.8*u)),x=across(aim),y=aim.crossProduct(x).normalize();
            FxLib.ringPlane(m,p,x,y,.15+1.1*big*Ease.out(u,0,.14f),.12,PINKISH,.85f*a);
            if(u<.07f){float f=1-u/.07f;m.star(tip,(.4+.3*big)*f+.1,GLARE,1);m.softDisc(tip,.45*big,FUCHSIA,.7f*f);}
        }
        if(STRIKES[i]&&u<.24f){
            Vec3d p=bite(c,tip,aim);float a=1-Ease.smooth(u,.04f,.24f);
            FxLib.flash(m,p,(.8+.4*big)*(1-.5*u/.24f),FUCHSIA,a);
            FxLib.sparks(m,p,18,1.4*big,.05,PINKISH,a,i*31+5,Ease.out(u,0,.1f));
            Vec3d x=across(aim),y=aim.crossProduct(x).normalize(),d1=x.add(y).normalize(),d2=x.subtract(y).normalize();double r=(.5+.6*u/.24f)*big;
            m.glowLine(p.subtract(d1.multiply(r)),p.add(d1.multiply(r)),.06,FUCHSIA,a);m.glowLine(p.subtract(d2.multiply(r)),p.add(d2.multiply(r)),.06,FUCHSIA,a);
        }
    }
    /** Afterimage stabs are born every GHOST seconds from the first thrust to the last, and five more with every thrust. */
    static final float GHOST=.018f;
    /** An afterimage stab: when it was born, its seed and how far it reaches (1 = the real arm and blade). */
    record Ghost(float born,int seed,double reach){
        float life(){return .14f+.08f*(float)Ease.hash(seed+5);}
        boolean alive(float t){return t>=born&&t<born+life();}
    }
    /** The afterimage stabs alive at t: the steady stream and the bursts that come with each thrust. */
    static List<Ghost> ghosts(float t){
        List<Ghost> out=new ArrayList<>();
        float first=THRUSTS[0]-.02f,last=THRUSTS[THRUSTS.length-1]+.05f;
        if(t<first||t>=last+.25f)return out;
        int newest=(int)Math.floor((Math.min(t,last)-first)/GHOST);
        for(int g=Math.max(0,newest-14);g<=newest;g++){var x=new Ghost(first+g*GHOST,g*7+1,1.05);if(x.alive(t))out.add(x);}
        for(int i=0;i<THRUSTS.length;i++)for(int k=0;k<5;k++){var x=new Ghost(THRUSTS[i]+k*.008f,i*53+k*11+500,1.2);if(x.alive(t))out.add(x);}
        return out;
    }
    /** The air ahead fills with afterimage stabs while the thrusts land, all coming from the sword shoulder. */
    static void afterimages(Fx c,EffectMesh m,float t){
        var live=ghosts(t);if(live.isEmpty())return;
        Vec3d shoulder=KagenoClips.shoulder(c.pos,c.yaw,R6.poseOf(walking(CLIP.frame(t),KagenoClips.walkWeight(c.cast,c.delta))));
        for(var g:live)ghost(c,m,t,shoulder,g);
    }
    /**
     * One afterimage stab: a purple ghost of the arm and a pink ghost of the blade snapping out along a direction in
     * the fan ahead, a magenta smear along it, a glint at its tip, then fading.
     */
    static void ghost(Fx c,EffectMesh m,float t,Vec3d shoulder,Ghost g){
        int seed=g.seed();double reach=g.reach();float life=g.life(),u=t-g.born();
        // A wide fan around the body, so the stabs show past it even from right behind.
        Vec3d d=c.dir(4+(Ease.hash(seed)-.5)*32,(Ease.hash(seed+1)-.5)*44);
        Vec3d s=shoulder.add(c.right.multiply((Ease.hash(seed+2)-.5)*1.1)).add(0,(Ease.hash(seed+3)-.3)*.6,0);
        Vec3d hand=s.add(d.multiply(.62*reach-.35*(1-Ease.out(u,0,.035f)))),tip=hand.add(d.multiply(TIP*reach));
        float a=Ease.smooth(u,0,.015f)*(1-Ease.smooth(u,life*.4f,life));
        m.softLine(s,hand,.2,PURPLE,.1f*a);
        m.softLine(hand.subtract(d.multiply(.3)),tip,.16,FUCHSIA,.12f*a);
        neon(m,hand.add(d.multiply(GUARD)),tip,.03,.6f*a,PINK);
        m.star(tip,.16,GLARE,.75f*a);
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
    /** Where the finisher bursts: the nearest body in the thrust lane, else ahead. */
    static Vec3d target(Fx c){return bite(c,c.pos.add(c.f.multiply(1.9)).add(0,1.1,0),c.f);}

    /** The swing's plane: through the sword shoulder, along the facing and up, rolled toward the right so it reads from behind too. */
    static final double TILT=18;
    static Vec3d swingCentre(Fx c){return c.pos.add(c.f.multiply(.2)).add(0,1.35,0).add(c.right.multiply(.3));}
    static Vec3d swingUp(Fx c,double tilt){double k=Math.toRadians(tilt);return new Vec3d(0,Math.cos(k),0).add(c.right.multiply(Math.sin(k)));}
    /** Angle of the blade in the swing's plane at sec: 0 ahead, 90 straight up, 180 behind. */
    static double swingAngle(Fx c,float sec){
        Vec3d d=sword(c,sec)[1];double a=Math.toDegrees(Math.atan2(d.dotProduct(swingUp(c,TILT)),d.dotProduct(c.f)));
        return a<-90?a+360:a;
    }
    /** The crescent's head runs a little ahead of the blade, from low in front over the head to low behind. */
    static final double TAIL=-30;
    static double head(Fx c,float t){return Math.max(TAIL,Math.min(205,swingAngle(c,Math.min(t,LEVEL))+18));}
    /** Blades of the crescent that spreads into rings: their tilts and births. */
    static final double[] RING_TILT={TILT,-6,36,8};
    static final float[] RING_BORN={SLASH,SLASH+.05f,SLASH+.11f,SLASH+.18f};
    static final float RING_LIFE=.62f;
    /** Radius of ring k a fraction x through its life. */
    static double ringRadius(int k,float x){return (k==0?2.5:2.1)+3.0*Ease.out(x,0,1);}
    /** Colour of a ring a fraction x through its life: magenta, purple, then the blue-violet it cools to. */
    static int ringColour(float x){return x<.35f?mix(FUCHSIA,PURPLE,x/.35f):mix(PURPLE,BLUE,Ease.smooth(x,.35f,.85f));}
    /**
     * The swing's light: the blade's hot trail and the bright edge of the crescent growing with the blade from low in front
     * over the head and down behind (its magenta body and dark inside are in the alpha pass); when the target bursts, the
     * bright rims of the rings it spreads into and magenta streaks whirling round the dark heart.
     */
    static void swing(Fx c,EffectMesh m,float t){
        if(t<LOW-.04f||t>=3.75f)return;
        Vec3d o=swingCentre(c),u=c.f,v=swingUp(c,TILT);
        if(t<LEVEL+.05f){
            float a=Ease.smooth(t,LOW-.04f,LOW+.02f)*(1-Ease.smooth(t,LEVEL-.02f,LEVEL+.05f));
            trail(c,m,t,.07f,8,.3,TIP,FUCHSIA,.85f*a);trail(c,m,t,.05f,6,.8,TIP,GLARE,.6f*a);
        }
        if(t>=LOW&&t<SLASH+.1f){
            double head=head(c,t);float a=1-Ease.smooth(t,SLASH,SLASH+.1f);
            crescent(m,o,u,v,2.5,.35,TAIL,head,PINKISH,.45f*a);
            edge(m,o,u,v,2.5,TAIL,head,.08,a);
        }
        for(int k=0;k<RING_TILT.length;k++){
            float age=t-RING_BORN[k];if(age<0||age>=RING_LIFE)continue;float x=age/RING_LIFE;
            double r=ringRadius(k,x);float a=(float)Math.pow(1-x,1.3)*(k==0?.9f:.7f);
            band(m,o,u,swingUp(c,RING_TILT[k]),r-.12,r-.03,r+.03,-12,196,x<.4f?PINKISH:mix(PINKISH,BLUE,Ease.smooth(x,.4f,.9f)),.4f*a*(1-x));
        }
        if(t>=SLASH&&t<3.2f){
            float a=Ease.smooth(t,SLASH,SLASH+.04f)*(1-Ease.smooth(t,3.05f,3.2f));double r=1.6+1.8*Ease.out(t,SLASH-.05f,3.15f);
            for(int k=0;k<5;k++){double rk=r*(.3+.15*k),rot=k*72+(t-SLASH)*420;band(m,o,u,v,rk-.05,rk,rk+.05,rot,rot+100,k%2==0?FUCHSIA:PURPLE,.5f*a);}
        }
    }
    /** The swing's colour and dark: the crescent's magenta body and dark inside, the rings it spreads into, and a dark whirl round the caster as the target bursts. */
    static void vortex(Fx c,EffectMesh m,float t){
        if(t<LOW||t>=3.75f)return;
        Vec3d o=swingCentre(c),u=c.f,v=swingUp(c,TILT);
        if(t<3.0f){
            double head=head(c,t);float a=1-Ease.smooth(t,SLASH,3.0f);
            crescent(m,o,u,v,2.05,1.5,TAIL,head,SHADOW,.75f*a);
            crescent(m,o,u,v,2.5,.9,TAIL,head,FUCHSIA,.6f*a);
        }
        for(int k=0;k<RING_TILT.length;k++){
            float age=t-RING_BORN[k];if(age<0||age>=RING_LIFE)continue;float x=age/RING_LIFE;
            double r=ringRadius(k,x);float a=(float)Math.pow(1-x,1.3)*(k==0?.75f:.6f);
            band(m,o,u,swingUp(c,RING_TILT[k]),r-.75*(1-.5*x),r-.05,r+.06,-12,196,ringColour(x),a);
        }
        if(t>=3.25f)return;
        if(t>=SLASH-.05f){
            float a=Ease.smooth(t,SLASH-.05f,SLASH+.02f)*(1-Ease.smooth(t,3.0f,3.25f));double r=1.6+1.8*Ease.out(t,SLASH-.05f,3.15f);
            band(m,o,u,v,0,.05,r,0,360,SHADOW,.5f*a);
            for(int k=0;k<5;k++){double rk=r*(.3+.15*k),rot=k*72+36+(t-SLASH)*420;band(m,o,u,v,rk-.18,rk,rk+.18,rot,rot+140,SHADOW,.45f*a);}
        }
    }
    /**
     * The finisher on the target: a white-pink flash with a level flare, a pillar of light up past the sky with sparkles
     * rising round it, rings swirling out over the ground at its foot, a pink haze rolling up and embers.
     */
    static void burst(Fx c,EffectMesh m,float t){
        if(t<SLASH-.02f||t>=3.9f)return;
        Vec3d p=target(c),g=new Vec3d(p.x,c.pos.y,p.z);
        if(t<SLASH+.3f){
            float u=Ease.unit(t,SLASH-.02f,SLASH+.3f),a=1-Ease.smooth(t,SLASH+.04f,SLASH+.3f);
            FxLib.flash(m,p,2.2*(1-.4*u)+.3,FUCHSIA,.85f*a);
            m.softDisc(p,1.2*(1-.5*u),GLARE,a);
            // The flare lies level across the screen: along the camera's own right.
            var cam=c.mc.gameRenderer.getCamera();var look=cam.getHorizontalPlane();var up=cam.getVerticalPlane();
            Vec3d x=new Vec3d(look.x(),look.y(),look.z()).crossProduct(new Vec3d(up.x(),up.y(),up.z()));
            if(x.lengthSquared()>1e-6){
                x=x.normalize();
                m.needle(p.subtract(x.multiply(5)),p.add(x.multiply(5)),.9*(1-.5*u),.5,PINKISH,.5f*a);
                m.needle(p.subtract(x.multiply(3.5)),p.add(x.multiply(3.5)),.22*(1-.5*u),.5,GLARE,.8f*a);
            }
        }
        if(t>=SLASH&&t<3.66f){
            float a=1-Ease.smooth(t,3.25f,3.66f),thin=1-.55f*Ease.smooth(t,3.0f,3.5f);Vec3d top=g.add(0,16*Ease.out(t,SLASH,SLASH+.07f),0);
            m.softLine(g,top,2.0*thin,PURPLE,.1f*a);
            m.softLine(g,top,.8*thin,FUCHSIA,.6f*a);
            m.line(g,top,.12*thin,GLARE,a);
            m.softDisc(g.add(0,.3,0),1.6*thin,FUCHSIA,.55f*a);m.softDisc(g.add(0,.3,0),.7,GLARE,.7f*a);
            FxLib.glow(m,g.add(0,.04,0),2.4,FUCHSIA,.45f*a);
        }
        for(int k=0;k<30;k++){
            float born=SLASH+.02f+.5f*(float)Ease.hash(k*7+3),life=.5f+.35f*(float)Ease.hash(k*11+5),u=(t-born)/life;if(u<0||u>=1)continue;
            double ang=Ease.hash(k*13)*Math.PI*2+u*1.5,r=.15+.8*Ease.hash(k*17);
            Vec3d s=g.add(Math.cos(ang)*r,.3+(1.5+5.5*Ease.hash(k*19))*u,Math.sin(ang)*r);
            float tw=.6f+.4f*(float)Math.sin(t*(30+20*Ease.hash(k*23))+k);
            m.star(s,.08+.08*Ease.hash(k*29),k%4==0?GLARE:PINKISH,(1-u)*tw);
        }
        for(int k=0;k<3;k++){
            float born=SLASH+k*.07f,u=(t-born)/.55f;if(u<0||u>=1)continue;
            FxLib.ring(m,g.add(0,.05+.1*k,0),.3+2.0*Ease.out(u,0,1),.3,k==1?VIOLET:FUCHSIA,.55f*(1-u));
        }
        FxLib.embers(m,g,1.2,4.5,22,t-SLASH,1.5f,.06,PINKISH,Ease.smooth(t,SLASH,SLASH+.1f)*(1-Ease.smooth(t,3.5f,3.85f)),23);
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
    /** Soft band in the plane (u,v) between radii r0 and r1, brightest at rPeak, over the angles a0..a1 (degrees); an open arc fades at both ends. */
    static void band(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double r0,double rPeak,double r1,double a0,double a1,int rgb,float alpha){
        if(alpha<=0||a1-a0<.5)return;int n=Math.max(6,(int)((a1-a0)/4));boolean open=a1-a0<359;
        for(int i=0;i<n;i++){
            double x0=(double)i/n,x1=(double)(i+1)/n;
            float k0=alpha*(open?(float)Math.min(1,Math.min(x0,1-x0)/.12):1),k1=alpha*(open?(float)Math.min(1,Math.min(x1,1-x1)/.12):1);
            Vec3d d0=in(u,v,Math.toRadians(a0+(a1-a0)*x0)),d1=in(u,v,Math.toRadians(a0+(a1-a0)*x1));
            m.vertex(o.add(d0.multiply(r0)),rgb,0);m.vertex(o.add(d1.multiply(r0)),rgb,0);m.vertex(o.add(d1.multiply(rPeak)),rgb,k1);m.vertex(o.add(d0.multiply(rPeak)),rgb,k0);
            m.vertex(o.add(d0.multiply(rPeak)),rgb,k0);m.vertex(o.add(d1.multiply(rPeak)),rgb,k1);m.vertex(o.add(d1.multiply(r1)),rgb,0);m.vertex(o.add(d0.multiply(r1)),rgb,0);
        }
    }
    /** Crescent in the plane (u,v) from tail to head (degrees): bright at its outer edge r and fading inward, widest at the head. */
    static void crescent(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double r,double width,double tail,double head,int rgb,float alpha){
        if(alpha<=0||head-tail<1)return;int n=Math.max(6,(int)((head-tail)/3));
        for(int i=0;i<n;i++){
            double x0=(double)i/n,x1=(double)(i+1)/n,w0=width*Math.sqrt(x0),w1=width*Math.sqrt(x1);
            float k0=alpha*(float)Math.min(1,x0*4),k1=alpha*(float)Math.min(1,x1*4);
            Vec3d d0=in(u,v,Math.toRadians(tail+(head-tail)*x0)),d1=in(u,v,Math.toRadians(tail+(head-tail)*x1));
            m.vertex(o.add(d0.multiply(r-w0)),rgb,0);m.vertex(o.add(d1.multiply(r-w1)),rgb,0);m.vertex(o.add(d1.multiply(r)),rgb,k1);m.vertex(o.add(d0.multiply(r)),rgb,k0);
            m.vertex(o.add(d0.multiply(r)),rgb,k0);m.vertex(o.add(d1.multiply(r)),rgb,k1);m.vertex(o.add(d1.multiply(r+.12)),rgb,0);m.vertex(o.add(d0.multiply(r+.12)),rgb,0);
        }
    }
    /** Neon tube along the crescent's outer edge, thickening toward its head, which glows. */
    static void edge(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double r,double tail,double head,double width,float alpha){
        if(alpha<=0||head-tail<1)return;int n=Math.max(4,(int)((head-tail)/5));Vec3d prev=null;
        for(int i=0;i<=n;i++){
            Vec3d p=o.add(in(u,v,Math.toRadians(tail+(head-tail)*i/n)).multiply(r));
            if(prev!=null){float k=(float)i/n;neon(m,prev,p,width*(.3+.7*k),alpha*(.25f+.75f*k),ARC);}
            prev=p;
        }
        m.softDisc(prev,.4,FUCHSIA,.6f*alpha);
    }

    // ───────────────────────────── screen ─────────────────────────────

    /** A pink-white flash as the target bursts; on the effect layer the impact frame takes the first frames and the flash follows it, softer. */
    @Override public float flash(Fx c){
        float t=c.sec;
        if(FxLayer.available())return t>=SLASH+.09f&&t<SLASH+.24f?.15f*Ease.smooth(t,SLASH+.09f,SLASH+.115f)*(1-Ease.unit(t,SLASH+.115f,SLASH+.24f)):0;
        return t>=SLASH-.01f&&t<SLASH+.12f?.2f*(1-Ease.unit(t,SLASH-.01f,SLASH+.12f)):0;
    }
    @Override public int flashColor(Fx c){return 0xffd8ff;}
    /** Post-process shock: a hum through the storm, small on the server's thrust hits, strong on the burst. */
    @Override public float pulse(float sec){
        float p=.05f*storm(sec);
        for(int i=0;i<THRUSTS.length;i++)if(STRIKES[i]){float u=sec-THRUSTS[i];if(u>=0&&u<.12f)p=Math.max(p,.18f*(1-u/.12f));}
        if(sec>=SLASH&&sec<SLASH+.35f)p=Math.max(p,.8f*(1-Ease.smooth(sec,SLASH,SLASH+.35f)));
        return p;
    }
    @Override public float shake(float sec){
        float s=.12f*storm(sec);
        if(sec>=SLASH&&sec<SLASH+.4f)s=Math.max(s,1.2f*(1-Ease.unit(sec,SLASH,SLASH+.4f)));
        return s;
    }
    /** A small nod on every thrust, the view thrown up with the burst; both ease in over a frame or two. */
    @Override public float[] kick(float sec){
        if(sec>=SLASH-.01f&&sec<SLASH+.4f){float k=Ease.smooth(sec,SLASH-.01f,SLASH+.03f)*(float)Math.exp(-Math.max(0,sec-SLASH-.03f)/.1);return new float[]{0,-2.4f*k,.9f*k};}
        int i=thrustAt(sec);
        if(i>=0&&sec<BACK){float u=sec-THRUSTS[i];if(u<.1f){float k=Ease.smooth(u,0,.02f)*(1-Ease.smooth(u,.02f,.1f));return new float[]{(i%2==0?.3f:-.3f)*k,.5f*k*(STRIKES[i]?1:.6f),0};}}
        return null;
    }
    // ───────────────────────────── smoke ─────────────────────────────

    /**
     * Fire-shader cells on fixed planes (they stay put as the camera moves): the purple haze the storm turns in and
     * the pink haze rolling up from the burst; on the effect layer, dust thrown out over the floor round the target.
     */
    @Override public void shaders(Fx c){
        float t=c.sec;List<Vec3d> pts=new ArrayList<>();List<float[]> cells=new ArrayList<>();
        float s=storm(t);
        if(s>0){
            Vec3d core=core(c);
            for(int k=0;k<7;k++){
                float cyc=(float)(t*.9+Ease.hash(k*7+1)),ph=cyc-(float)Math.floor(cyc);int gen=(int)Math.floor(cyc);double ang=Ease.hash(k*5+2+gen*13)*Math.PI*2;
                Vec3d p=core.add(c.right.multiply(Math.cos(ang)*(.5+1.1*ph))).add(0,Math.sin(ang)*(.4+.8*ph)+.5*ph,0).add(c.f.multiply(-.3+.6*Ease.hash(k*3+gen)));
                pts.add(p);cells.add(new float[]{(float)(.45+.4*ph),.22f*s*(float)Math.sin(Math.PI*ph),k%2==0?.48f:.6f,.4f,k*31+gen*7+3});
            }
        }
        if(t>=SLASH+.05f&&t<3.88f){
            Vec3d p=target(c),g=new Vec3d(p.x,c.pos.y,p.z);float a=Ease.smooth(t,SLASH+.05f,SLASH+.25f)*(1-Ease.smooth(t,3.45f,3.88f));
            for(int k=0;k<10;k++){
                double ang=Ease.hash(k*31+1)*Math.PI*2,r=.3+.9*Ease.hash(k*37+2),rise=(t-SLASH)*(.6+.8*Ease.hash(k*41+3));
                Vec3d q=g.add(Math.cos(ang)*r+.2*Math.sin(t*1.7+k),.6+2.2*Ease.hash(k*43+4)+rise,Math.sin(ang)*r+.2*Math.cos(t*1.3+k));
                pts.add(q);cells.add(new float[]{(float)(.45+.3*Ease.hash(k*47)+.25*(t-SLASH)),.2f*a*(1-Ease.smooth(t,SLASH+.4f,3.6f)),k%3==0?.46f:.64f,.5f,k*29+101});
            }
        }
        if(!pts.isEmpty())KagenoFire.cells(c.matrix,c.camera,pts,cells,c.age);
        if(t>=SLASH&&t<SLASH+1.2f&&FxLayer.available()){
            List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();Vec3d p=target(c);
            FxLib.dustRing(new Vec3d(p.x,c.pos.y,p.z),t,SLASH,.6,3.2,14,1.1,dust,puffs,61);
            if(!dust.isEmpty())KagenoFire.dust(c.matrix,c.camera,dust,puffs,c.age);
        }
    }

    // ───────────────────────────── effect layer ─────────────────────────────

    @Override public boolean layered(){return true;}
    /**
     * Lights: the blade's pink neon, the storm's magenta heart (brighter with every thrust), a white-pink flash where
     * a striking thrust bites, the swing crescent's head, then the finisher: a blinding burst on the target and its
     * pillar of light. For viewers close by the finisher has the impact frame, a colour split and a light shaft; a
     * burn is torn across the floor under the target, glowing and cooling.
     */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;
        float g=Math.min(1.5f,glow(t))*lit(t);
        if(g>.02f){var hb=sword(c,t);f.light(hb[0].add(hb[1].multiply(.8)),HOT,4,.8f*g);}
        float s=storm(t);
        if(s>0){int i=thrustAt(t);float kick=i>=0?(float)Math.exp(-(t-THRUSTS[i])/.06):0;f.light(core(c),FUCHSIA,6.5,(1.1f+.9f*kick)*s);}
        for(int i=0;i<THRUSTS.length;i++){
            if(!STRIKES[i])continue;float u=t-THRUSTS[i];if(u<-.01f||u>=.24f)continue;
            var hb=sword(c,THRUSTS[i]);f.light(bite(c,hb[0].add(hb[1].multiply(TIP)),aim(c,i)),GLARE,5,1.6f*FxLib.kick(t,THRUSTS[i],.06f));
        }
        if(t>=LOW&&t<SLASH+.1f){
            double h=Math.toRadians(head(c,t));
            f.light(swingCentre(c).add(c.f.multiply(Math.cos(h)*2.3)).add(swingUp(c,TILT).multiply(Math.sin(h)*2.3)),FUCHSIA,6,1.2f*(1-Ease.smooth(t,SLASH,SLASH+.1f)));
        }
        if(t<SLASH-.01f)return;
        Vec3d p=target(c),g0=new Vec3d(p.x,c.pos.y,p.z);
        f.light(p,0xffd6ff,13,3.4f*FxLib.kick(t,SLASH,.18f));
        float pillar=Ease.smooth(t,SLASH,SLASH+.05f)*(1-Ease.smooth(t,3.25f,3.66f));
        if(pillar>0){f.light(g0.add(0,2.5,0),FUCHSIA,8,1.2f*pillar);f.light(g0.add(0,.4,0),PINKISH,5,.9f*pillar);}
        float near=FxLib.near(c,p);
        if(near>0){
            f.impact(near*FxLib.impact(t,SLASH),FxLib.impactMode(t,SLASH));
            f.chroma(near*.008f*FxLib.kick(t,SLASH,.12f));
            f.shaft(p,0xffc8f4,near*(.5f*FxLib.kick(t,SLASH,.22f)+.15f*pillar));
        }
        float heat=1-Ease.smooth(t,SLASH+.15f,SLASH+1.1f),a=Ease.smooth(t,SLASH,SLASH+.05f)*(1-Ease.smooth(t,3.5f,3.85f));double open=Ease.out(t,SLASH,SLASH+.1f);
        f.decal(g0,c.right,2.2*open+.2,.6,heat,a,4.7f);
        f.decal(g0,c.f,1.4*open+.2,.4,heat*.8f,.85f*a,8.3f);
    }
    /** The air bends round each thrust's point and along its lance, along the swing's crescent, and in the burst's shock over the floor and round the target. */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;
        for(int i=0;i<THRUSTS.length;i++){
            float u=t-THRUSTS[i];if(u<0||u>=.16f)continue;
            var hb=sword(c,THRUSTS[i]);Vec3d tip=hb[0].add(hb[1].multiply(TIP)),aim=aim(c,i),x=across(aim),y=aim.crossProduct(x).normalize();
            float big=i==THRUSTS.length-1?1.3f:STRIKES[i]?1:.7f;
            d.ring(tip.add(aim.multiply(.25+.8*u)),x,y,.2+1.3*big*Ease.out(u,0,.16f),.45,.55f*big*(1-u/.16f));
            if(u<.12f){Vec3d a0=tip.add(aim.multiply(.1+1.5*u));d.band(a0,a0.add(aim.multiply(2.6)),y,.5*big,.4f*(1-u/.12f));}
        }
        if(t>=LOW&&t<SLASH+.12f){
            double head=head(c,t);
            if(head>TAIL+1)d.arc(swingCentre(c),c.f,swingUp(c,TILT),2.6,1.1,TAIL,head-TAIL,.6f*(1-Ease.smooth(t,SLASH,SLASH+.12f)));
        }
        if(t>=SLASH-.01f&&t<SLASH+.5f){
            Vec3d p=target(c),g0=new Vec3d(p.x,c.pos.y+.05,p.z);float in=Ease.smooth(t,SLASH-.01f,SLASH+.015f);
            d.ring(g0,c.right,c.f,.5+8*Ease.out(t,SLASH,SLASH+.5f),1.6,.9f*in*(1-Ease.unit(t,SLASH,SLASH+.5f)));
            d.ring(p,c.right,FxLib.UP,.5+4*Ease.out(t,SLASH,SLASH+.3f),1,.8f*in*(1-Ease.unit(t,SLASH,SLASH+.3f)));
        }
    }
    /** The burst tears up the floor under the target: chunks thrown up round it, the floor heaving in a ring. */
    @Override public void solids(Fx c,FxSolids s){
        float t=c.sec;if(t<SLASH||t>=3.9f)return;
        Vec3d p=target(c),g0=new Vec3d(p.x,c.pos.y,p.z);
        FxLib.chunks(s,g0,t-SLASH,10,1201,2.2,7.5,.17,.75f);
        FxLib.heave(s,g0,t-SLASH,4.5,.3,12,.9,.5);
    }
}
