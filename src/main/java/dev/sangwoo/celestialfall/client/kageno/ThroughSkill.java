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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;
import static dev.sangwoo.celestialfall.client.kageno.SparkSkill.B;
import static dev.sangwoo.celestialfall.client.kageno.SparkSkill.H;

/**
 * T held "어둠을 가로지르다", rebuilt from the user's recording: T's windup and sparkles, then back to a ready
 * stance; black smoke spreads out on both sides like wings while the sword cuts left, right and left again,
 * throwing three deep-blue crescent beams up the dash line (each just before the server's hit at 1.0, 1.3 and
 * 1.55 s); the sword comes up in front of the face, and at 1.74 s a pink burst launches a flat-out dash that ends
 * in a white-violet spike. A cage of vertical rings and pink orbs forms around the body, then the spin cut: huge
 * deep-blue blade crescents whirl round the body at waist height, each a slash whose tail wears away behind its
 * edge, spreading far out and breaking into thick blue arcs flying away; pink flame wisps are left burning on the
 * ground. Seconds after the cast.
 */
final class ThroughSkill implements Fx.Skill {
    /** Ready stance, dash start (D), end of the glide, ring cage and the final cut (the server's {@code RUSH_FINISH}). */
    static final float READY=B+.13f,D=B+1.09f,GLIDE=D+.14f,CAGE=D+.2f,CUT=D+.85f;
    /** Sword-beam launches, each 0.05 s before one of the server's pre-dash hits. */
    static final float[] BEAMS={.95f,1.25f,1.5f};
    /** Beam speed (blocks per second) and how far it flies: the server's strip reaches 28 blocks ahead. */
    static final double BEAM_SPEED=150,BEAM_RANGE=30;
    static final R6.Pose STANCE=pose().body(-8,10,0).at(0,-.12,0).head(4,-4,0).rArm(-38,42).lArm(-74,-24).rLeg(-80,28).lLeg(-80,-26).blade(-6,58);
    static final R6.Pose WIND=pose().body(-34,12,0).at(0,-.16,0).head(18,0,0).rArm(-8,112).lArm(-62,-38).rLeg(-78,40).lLeg(-78,-30).blade(12,128);
    static final R6.Pose CROSS_L=pose().body(32,10,0).at(.05,-.18,0).head(-20,2,0).rArm(-22,-58).lArm(-58,-70).rLeg(-78,42).lLeg(-78,-30).blade(-14,-102);
    static final R6.Pose CROSS_R=pose().body(-30,14,0).at(.05,-.2,0).head(16,4,0).rArm(-34,98).lArm(-60,-36).rLeg(-76,44).lLeg(-76,-32).blade(-36,118);
    static final R6.Pose CROSS_L2=pose().body(26,8,0).at(.08,-.2,0).head(-16,2,0).rArm(-30,-66).lArm(-60,-60).rLeg(-76,44).lLeg(-76,-32).blade(-34,-112);
    /** The blade passing in front of the body at each beam. */
    static final R6.Pose PASS1=pose().body(0,11,0).at(.03,-.17,0).rArm(-4,12).lArm(-60,-50).rLeg(-78,41).lLeg(-78,-30).blade(4,-18);
    static final R6.Pose PASS2=pose().body(2,12,0).at(.05,-.19,0).rArm(-2,18).lArm(-60,-50).rLeg(-77,43).lLeg(-77,-31).blade(10,42);
    static final R6.Pose PASS3=pose().body(-2,8,0).at(.07,-.2,0).rArm(6,4).lArm(-58,-55).rLeg(-76,44).lLeg(-76,-32).blade(22,-26);
    /** Sword up in front of the face, crouched for the dash. */
    static final R6.Pose RAISED=pose().body(0,14,0).at(0,-.3,0).head(0,-6,0).rArm(-24,6).lArm(-30,-14).rLeg(-68,48).lLeg(-68,-46).blade(84,0);
    static final R6.Pose LUNGE=pose().body(0,45,0).at(0,-.54,0).rArm(10,0).lArm(0,180).rLeg(-60,0).lLeg(-30,180).blade(20,0);
    static final R6.Pose COMET=pose().body(0,80,0).at(0,-.68,0).rArm(80,0).lArm(60,-20).blade(80,0);
    /** Standing in the ring cage, the sword held up. */
    static final R6.Pose UPRIGHT=pose().body(0,4,0).at(0,-.06,0).rArm(-12,8).lArm(-70,-22).rLeg(-84,22).lLeg(-84,-20).blade(86,0);
    static final R6.Pose CROUCH=pose().body(0,30,0).at(0,-.35,0).rArm(0,90).lArm(-60,-40).rLeg(-65,60).lLeg(-65,-60).blade(0,100);
    static final R6.Pose FINISH=pose().rArm(-20,20).lArm(-75,-20).rLeg(-60,90).lLeg(-60,-90).blade(88,0);
    static final Anim CLIP=Anim.joints(
        k(0,KagenoClips.IDLE),
        k(.045,SparkSkill.LIFT),
        k(.085,SparkSkill.SWEEP,Anim.Ease.LINEAR),
        k(.14,SparkSkill.PLANT.lArm(28,-40).body(-52,26,0)),
        k(.24,SparkSkill.PLANT),
        k(B-.04,SparkSkill.PLANT.body(-57,27,0).at(0,-.11,0).lArm(46,-30)),
        k(READY,STANCE),
        // Three cuts: each beam leaves as the blade passes in front, the follow-through carries on round.
        k(.86,WIND),
        k(BEAMS[0],PASS1,Anim.Ease.LINEAR),
        k(1.04,CROSS_L),
        k(1.15,CROSS_L.body(36,10,0).blade(-8,-112)),
        k(BEAMS[1],PASS2,Anim.Ease.LINEAR),
        k(1.33,CROSS_R),
        k(1.42,CROSS_R.body(-36,14,0).blade(-24,128)),
        k(BEAMS[2],PASS3,Anim.Ease.LINEAR),
        k(1.57,CROSS_L2,Anim.Ease.OUT),
        k(1.66,RAISED),
        k(D-.05,RAISED.at(0,-.34,0).body(0,16,0)),
        k(D,LUNGE,Anim.Ease.OUT),
        k(D+.03,COMET,Anim.Ease.LINEAR),
        k(GLIDE,COMET,Anim.Ease.HOLD),
        // Skidding to a stop and straightening up inside the cage.
        k(D+.32,UPRIGHT),
        k(CUT-.16,UPRIGHT.at(0,-.12,0).body(0,8,0)),
        k(CUT-.05,CROUCH.body(-150,30,0)),
        k(CUT+.05,CROUCH.body(-360,20,0),Anim.Ease.LINEAR),
        k(D+1.0,FINISH.body(-360,0,0)),
        k(3.3f,FINISH.body(-360,2,0).at(0,-.04,0)),
        k(3.7f,KagenoClips.IDLE.body(-360,0,0))).stops(Anim.stop(dev.sangwoo.celestialfall.stand.KagenoRules.RUSH_FINISH/20f,.06,.12));
    @Override public Anim clip(int combo){return CLIP;}
    /** The client drops the cast at the server's duration (3.7 s), so everything is over by then. */
    @Override public float lifetime(){return dev.sangwoo.celestialfall.stand.KagenoRules.DURATION[dev.sangwoo.celestialfall.stand.KagenoRules.RUSH]/20f;}

    /** Radius of a sword beam's crescent (blocks). */
    static final double R_BEAM=2.1;
    /** Where beam {@code i} is {@code age} seconds after it left: chest high, flying up the dash line and rising a little. */
    static Vec3d beam(Fx c,int i,float age){double d=.8+BEAM_SPEED*age;return c.origin.add(c.f.multiply(d)).add(0,1.25+.05*d,0);}
    /** Where the beams leave the blade. */
    static Vec3d muzzle(Fx c){return c.origin.add(c.f.multiply(1)).add(0,1.25,0);}
    /** Who the beams pass through: the server's caught targets once they are known, else whoever stands in its strip ahead. */
    static List<LivingEntity> strip(Fx c){
        if(!c.targets.isEmpty())return c.targets;
        var world=c.mc.world;if(world==null)return List.of();UUID caster=c.cast.caster();Vec3d mid=c.origin.add(c.f.multiply(15));
        return world.getEntitiesByClass(LivingEntity.class,new Box(mid,mid).expand(16),e->e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!e.getUuid().equals(caster)&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)&&inStrip(c,e.getPos()));
    }
    /** The server's strip for the fire ticks before the dash: 2.5-28 blocks ahead, 3.8 left to 7.6 right, 2 below to 7 above. */
    static boolean inStrip(Fx c,Vec3d p){Vec3d d=p.subtract(c.origin);double x=d.dotProduct(c.f),y=d.dotProduct(c.right);return x>=2.5&&x<=28&&y>=-3.8&&y<=7.6&&d.y>=-2&&d.y<=7;}
    /** When the caster passes {@code d} blocks up the dash line: the server's steps, a tick behind as the client sees them. */
    static float passes(double d){
        double done=0;
        for(int i=0;;i++){double step=KagenoRules.rushSpeed(KagenoRules.RUSH_START+i);if(step<=0)return (KagenoRules.RUSH_START+1+i)/20f;
            if(d<=done+step)return (float)((KagenoRules.RUSH_START+1+i+(d-done)/step)/20);done+=step;}
    }
    /** Rings popping round the dash line as the caster passes, every {@code RING_STEP} blocks (no further than he got). */
    static final double RING_STEP=4;
    static int rings(Fx c){return (int)Math.floor((c.pos.subtract(c.origin).dotProduct(c.f)+.5)/RING_STEP);}

    @Override public void additive(Fx c,EffectMesh m){
        SparkSkill.sparkles(c,m);SparkSkill.charge(c,m);
        float t=c.sec;Vec3d pos=c.pos;
        sigil(c,m,t);
        // Two white comets streak up the dash line as the stance is taken.
        for(int k=0;k<2;k++){
            float born=.79f+k*.035f,u=(t-born)/.16f;if(u<0||u>=1)continue;
            Vec3d head=c.origin.add(c.f.multiply(2+26*Ease.out(u,0,1))).add(c.right.multiply(k==0?-.9:1.1)).add(0,1.5+.6*k,0);
            m.needle(head.subtract(c.f.multiply(2.4)),head,.3,.85,0xf0dcff,.9f*(1-u));m.softDisc(head,.35,0xffffff,1-u);
        }
        // The cuts leave violet trails.
        if(t>=.86f&&t<1.62f)FxLib.trail(m,c,CLIP,t,.07f,8,.25,1.35,0x9a3cff,.85f*Ease.smooth(t,.86f,.9f)*(1-Ease.smooth(t,1.56f,1.62f)));
        beams(c,m,t);
        gather(c,m,t);
        launch(c,m,t);
        dash(c,m,t);
        cage(c,m,t);
        cut(c,m,t);
        // Embers rising from the wisps.
        if(t>=CUT+.2f&&t<CUT+1f){float a=Ease.smooth(t,CUT+.2f,CUT+.3f)*(1-Ease.smooth(t,CUT+.7f,CUT+1f));FxLib.embers(m,pos,8,3,44,t-CUT,.8f,.06,0xffa8f0,a,13);}
    }
    /** A circle of light lying on the floor: two rings and diamond runes turning between them. */
    static void circle(EffectMesh m,Vec3d o,double r,double turn,int runes,int rim,int inner,int rune,float bright){
        if(bright<=0||r<=.05)return;
        FxLib.ring(m,o,r,.5,rim,.8f*bright);FxLib.ring(m,o,r*.72,.22,inner,.7f*bright);
        for(int i=0;i<runes;i++){
            double a=turn+i*Math.PI*2/runes;Vec3d out=new Vec3d(Math.cos(a),0,Math.sin(a)),along=new Vec3d(-Math.sin(a),0,Math.cos(a));
            Vec3d q=o.add(out.multiply(r*.86));double l=.06*r,w=.028*r;
            m.quad(q.add(out.multiply(l)),q.add(along.multiply(w)),q.subtract(out.multiply(l)),q.subtract(along.multiply(w)),rune,.9f*bright);
        }
    }
    /**
     * A deep-blue circle opening on the floor under the stance as the cuts begin, flaring with each beam and as the
     * dash leaves it, then gone.
     */
    static void sigil(Fx c,EffectMesh m,float t){
        if(t<.6f||t>=D+.3f)return;
        float a=Ease.smooth(t,.6f,.7f)*(1-Ease.smooth(t,D+.04f,D+.3f)),pulse=0;
        for(float b:BEAMS)pulse=Math.max(pulse,FxLib.kick(t,b,.12f));
        Vec3d o=c.origin.add(0,.04,0);double r=2.8*Ease.out(t,.6f,.86f);
        circle(m,o,r,(t-.6f)*.9,10,0x4a3cff,0x9c8cff,0xd8d0ff,a*(.7f+.6f*pulse+1.2f*FxLib.kick(t,D,.1f)));
        FxLib.glow(m,o,r*1.15,0x2a1a9c,.35f*a);
    }
    /**
     * The beams: pink-white tips, a deep-blue wake glowing along the line each flew, a ring of air popping where it
     * leaves the blade and a burst on whoever it passes through.
     */
    static void beams(Fx c,EffectMesh m,float t){
        Vec3d from=muzzle(c);
        for(int i=0;i<BEAMS.length;i++){
            float age=t-BEAMS[i];if(age<0||age>=.4f)continue;
            if(age<.26f){
                float a=1-Ease.smooth(age,.12f,.26f);Vec3d o=beam(c,i,age);double r=R_BEAM;
                for(int s=-1;s<=1;s+=2){Vec3d tip=o.add(c.right.multiply(s*r*Math.cos(Math.toRadians(25)))).add(0,r*Math.sin(Math.toRadians(25))-r*.55,0);m.softDisc(tip,.6,0xff7ad8,.8f*a);m.star(tip,.45,0xffffff,a);}
                m.softLine(from,o.add(0,-.2,0),1.4,0x3c2cff,.3f*a);
            }
            if(age<.05f)FxLib.flash(m,from,1.3,0x8a7cff,1-age/.05f);
            if(age<.2f)FxLib.ringPlane(m,from,c.right,FxLib.UP,.3+2.0*Ease.out(age,0,.18f),.22,0xb8b0ff,.8f*(1-Ease.unit(age,0,.2f)));
            float k=FxLib.kick(t,BEAMS[i]+.03f,.1f);
            if(k>.01f)for(var e:strip(c)){Vec3d p=e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);FxLib.flash(m,p,1.4*k+.3,0x6a5cff,k);FxLib.ringPlane(m,p,c.right,FxLib.UP,.4+1.6*(1-k),.2,0xd8d0ff,.8f*k);}
        }
    }
    /** The raised blade gathering white-pink light, motes drawn into it and a pink ring closing in over the floor to the feet. */
    static void gather(Fx c,EffectMesh m,float t){
        if(t<1.52f||t>=D+.03f)return;
        float a=Ease.smooth(t,1.6f,1.72f);Vec3d tip=c.origin.add(c.f.multiply(.45)).add(0,2.2,0);
        m.softDisc(tip,.4+.5*a,0xff9ce8,.65f*a);m.star(tip,.7*a,0xffffff,a);
        FxLib.converge(m,tip,2.4,20,t,1.52f,D-.02f,0xffb8f0,77);
        FxLib.ring(m,c.origin.add(0,.05,0),.6+4.4*(1-Ease.in(t,1.56f,D)),.4,0xff6ad8,.7f*Ease.smooth(t,1.56f,1.62f)*(1-Ease.smooth(t,D-.02f,D+.03f)));
    }
    /** The dash's start: a pink burst, shock rings racing out over the floor and a ring of air round the line it takes. */
    static void launch(Fx c,EffectMesh m,float t){
        if(t<D-.01f||t>=D+.45f)return;
        Vec3d o=c.origin.add(0,1,0);
        if(t>=D&&t<D+.22f){
            // On the effect layer the light adds up past white, so the burst is drawn a little softer there.
            float u=Ease.unit(t,D,D+.22f),a=(FxLayer.available()?.65f:1)*(1-Ease.smooth(t,D+.04f,D+.22f));
            m.softDisc(o,2.5+4*Ease.out(t,D,D+.1f),0xff2fb4,.65f*a);m.softDisc(o,1.2+1.6*u,0xff7ad0,.85f*a);m.softDisc(o,.5+.5*u,0xffd8f4,.7f*a);
            for(int k=0;k<18;k++){double az=k*Math.PI/9+Ease.hash(k*3)*.3,el=(Ease.hash(k*5)-.4)*1.3,len=(2.5+5*Ease.hash(k*7))*(.35+.65*Ease.out(t,D,D+.08f));
                Vec3d d=c.f.multiply(Math.cos(el)*Math.cos(az)).add(c.right.multiply(Math.cos(el)*Math.sin(az))).add(0,Math.sin(el),0);m.needle(o.add(d.multiply(.5)),o.add(d.multiply(.5+len)),.45,.25,k%3==0?0xffe0f8:0xff3cc8,a);}
        }
        float a=Ease.smooth(t,D-.01f,D+.01f);Vec3d floor=c.origin.add(0,.06,0);
        FxLib.ring(m,floor,.5+9*Ease.out(t,D,D+.4f),.8,0xff5ad0,.75f*a*(1-Ease.unit(t,D,D+.4f)));
        FxLib.ring(m,floor,.3+5.5*Ease.out(t,D,D+.25f),.35,0xffffff,.7f*a*(1-Ease.unit(t,D,D+.25f)));
        FxLib.ringPlane(m,o,c.right,FxLib.UP,.5+3.4*Ease.out(t,D,D+.22f),.4,0xffb0ec,.8f*a*(1-Ease.unit(t,D,D+.22f)));
    }
    /**
     * The dash: the line it took burning pink with a white core, rings of light popping round it as the caster passes,
     * sparks streaming off behind him, and the jagged white-violet spike shooting up from the body as he arrives.
     */
    static void dash(Fx c,EffectMesh m,float t){
        Vec3d pos=c.pos;
        if(t>=D&&t<D+.6f){
            float a=1-Ease.smooth(t,D+.22f,D+.55f);Vec3d from=c.origin.add(0,1,0),to=pos.add(0,1,0);
            m.softLine(from,to,1.6,0xff3cc8,.4f*a);m.softLine(from,to,.6,0x7c3cf0,.35f*a);m.glowLine(from,to,.16,0xffc8f0,a);
            for(int k=1;k<=rings(c);k++){
                double d=k*RING_STEP;float at=passes(d),u=Ease.unit(t,at,at+.3f);if(t<at||u>=1)continue;
                FxLib.ringPlane(m,c.origin.add(c.f.multiply(d)).add(0,1,0),c.right,FxLib.UP,.4+(1.8+.1*k)*Ease.out(t,at,at+.22f),.3,k%2==0?0xffffff:0xff7ad8,.85f*(1-u)*Ease.smooth(t,at,at+.02f));
            }
        }
        if(t>=D&&t<GLIDE+.15f){
            float a=Ease.smooth(t,D,D+.02f)*(1-Ease.smooth(t,GLIDE,GLIDE+.15f));
            for(int k=0;k<16;k++){
                double back=(.4+5*Ease.hash(k*7+181))*(.6+.4*Math.sin(t*30+k)),off=(Ease.hash(k*11+182)-.5)*1.2,lift=.3+1.6*Ease.hash(k*13+183);
                Vec3d q=pos.subtract(c.f.multiply(back)).add(c.right.multiply(off)).add(0,lift,0);
                m.needle(q.subtract(c.f.multiply(.5+.4*Ease.hash(k*17+184))),q,.07,.3,k%3==0?0xffffff:0xff8ae8,a*(float)(1-back/6));
            }
        }
        if(t>=D+.05f&&t<D+.24f){
            float a=1-Ease.smooth(t,D+.14f,D+.24f);long step=(long)Math.floor(t*40);
            for(int k=0;k<5;k++)FxLib.zig(m,pos.add((k-2)*.16,.1,(Ease.hash(k*9)-.5)*.3),pos.add((Ease.hash((int)step*3+k)-.5)*1.2,3.4+.5*k,(Ease.hash((int)step*5+k)-.5)*1.2),9,.15-.02*k,k%2==0?0xeadcff:0x9c6cff,a,(int)step*7+k);
            m.softDisc(pos.add(0,1.6,0),1.6,0xa070ff,.5f*a);m.softDisc(pos.add(0,1.2,0),.7,0xf4ecff,.7f*a);
            FxLib.ring(m,pos.add(0,.06,0),.4+4*Ease.out(t,D+.05f,D+.24f),.5,0xd8c8ff,.7f*a);
        }
    }
    /**
     * Inside the cage: pink orbs drifting round the body with short tails, a pink circle on the floor brightening
     * toward the cut, and motes rising inside it.
     */
    static void cage(Fx c,EffectMesh m,float t){
        if(t<CAGE||t>=CUT+.35f)return;
        Vec3d pos=c.pos;float a=Ease.smooth(t,CAGE,CAGE+.12f)*(1-Ease.smooth(t,CUT+.1f,CUT+.35f));
        for(int k=0;k<24;k++){
            double ang=t*(1.2+.8*Ease.hash(k*3))+k*Math.PI*2/24,r=1.8+2*Ease.hash(k*5)+(t>CUT?6*Ease.out(t,CUT,CUT+.35f):0),h=.4+2.6*Ease.hash(k*7)+.25*Math.sin(t*3+k);
            Vec3d p=pos.add(Math.cos(ang)*r,h,Math.sin(ang)*r),q=pos.add(Math.cos(ang-.35)*r,h-.05,Math.sin(ang-.35)*r);
            m.softTaper(p,q,.24,.02,0xff7ad8,.5f*a);m.softDisc(p,.28+.22*Ease.hash(k*11),0xff4fd0,a);m.softDisc(p,.1+.06*Ease.hash(k*13),0xffd8f4,a);
        }
        float rise=Ease.smooth(t,CAGE,SWELL);
        circle(m,pos.add(0,.05,0),3.2,-(t-CAGE)*1.4,12,0xff4fd0,0xffb0ec,0xffe0f8,a*(.45f+.75f*rise)*(1-Ease.smooth(t,CUT,CUT+.12f)));
        FxLib.embers(m,pos,2.2,3.2,18,t-CAGE,1.4f,.06,0xffb0f0,.8f*a,19);
    }
    /**
     * The spin cut: a blinding flash and a column of light at the body, shock rings racing out over the floor (white,
     * deep blue, pink) and sparks flung out flat round the waist.
     */
    static void cut(Fx c,EffectMesh m,float t){
        if(t<CUT-.01f||t>=CUT+.6f)return;
        Vec3d pos=c.pos,o=pos.add(0,.06,0);float a=Ease.smooth(t,CUT-.01f,CUT+.01f);
        if(t<CUT+.12f)FxLib.flash(m,pos.add(0,1,0),3.2,0x9a7cff,1-Ease.unit(t,CUT,CUT+.12f));
        FxLib.column(m,pos,9*Ease.out(t,CUT,CUT+.06f),.55*(1-Ease.unit(t,CUT,CUT+.4f))+.1,0xb8a8ff,a*(1-Ease.smooth(t,CUT+.05f,CUT+.4f)));
        FxLib.ring(m,o,.5+14*Ease.out(t,CUT,CUT+.5f),.9,0xffffff,.6f*a*(1-Ease.unit(t,CUT,CUT+.5f)));
        FxLib.ring(m,o,.4+10*Ease.out(t,CUT,CUT+.4f),1.2,0x3c2cff,.8f*a*(1-Ease.unit(t,CUT,CUT+.4f)));
        FxLib.ring(m,o,.3+6.5*Ease.out(t,CUT,CUT+.3f),.6,0xff6ad8,.7f*a*(1-Ease.unit(t,CUT,CUT+.3f)));
        float s=a*(1-Ease.smooth(t,CUT+.15f,CUT+.5f));
        for(int k=0;k<40;k++){
            double az=k*Math.PI*2/40+Ease.hash(k*3+171)*.15,go=(1.5+7*Ease.hash(k*5+172))*Ease.out(t,CUT,CUT+.35f),h=.5+Ease.hash(k*7+173);
            Vec3d d=new Vec3d(Math.cos(az),0,Math.sin(az)),q=pos.add(d.multiply(1+go)).add(0,h-.6*(t-CUT)*(t-CUT),0);
            m.needle(q.subtract(d.multiply(.3+.3*Ease.hash(k*11+174))),q,.07,.3,k%3==0?0xffffff:k%3==1?0xff8ae8:0x8a7cff,s);
        }
    }
    /** The spin cut's slashes leave just before the cut lands, so they are round the body with the server's hit. */
    static final float SWELL=CUT-.13f;
    /**
     * The spin cut's blade crescents, one per row: delay after SWELL and life (s), outer radius from and to, width
     * (blocks, at most 60% of the radius so each stays a blade with the body clear inside it), length (degrees),
     * height of its middle, tilt (degrees, higher behind), where its head starts (degrees past the left side) and
     * how many blades of light it is (1 the wide deep-blue blade, 0 a thin bright streak).
     */
    static final double[][] SLASHES={
        {0,.36,2.8,9.5,4.2,230,.7,0,0,1},
        {.04,.36,2.2,8,2.6,200,1.15,8,140,1},
        {.07,.34,3.5,11,1.6,170,.35,-6,260,1},
        {.02,.32,4,13,.5,150,.9,3,60,0},
        {.09,.3,5,15,.35,120,.55,-3,200,0}};
    /** Degrees a slash's head turns per second, clockwise seen from above like the body's spin. */
    static final double SPIN=1000;
    /** Outer radius of the widest slash (blocks) at {@code t}, or 0 before and after it. */
    static double reach(float t){
        double[] s=SLASHES[0];float age=(float)(t-SWELL-s[0]);if(age<0||age>=s[1])return 0;
        return s[2]+(s[3]-s[2])*Ease.out(age,0,(float)s[1]);
    }
    /** Where the slashes start (degrees, clockwise from above like the spin): beside the sword at the left. */
    static double sweepStart(Fx c){return Math.toDegrees(Math.atan2(c.f.z,c.f.x))-90;}
    /** Basis {u, v} of a plane through the body tilted up behind by {@code tilt} degrees; angles in it run clockwise seen from above. */
    static Vec3d[] slashPlane(Fx c,double tilt){
        double a=Math.toRadians(tilt);Vec3d n=new Vec3d(0,Math.cos(a),0).add(c.f.multiply(Math.sin(a))).normalize();
        Vec3d u=new Vec3d(1,0,0).subtract(n.multiply(n.x)).normalize();
        return new Vec3d[]{u,u.crossProduct(n)};
    }
    @Override public void crescents(Fx c){
        float t=c.sec;Vec3d pos=c.pos;
        // The beams: deep-blue crescents bowed upward, flying up the dash line.
        for(int i=0;i<BEAMS.length;i++){
            float age=t-BEAMS[i];if(age<0||age>=.26f)continue;
            Vec3d o=beam(c,i,age).add(0,-R_BEAM*.55,0);
            KagenoFire.crescent(o,c.right,new Vec3d(0,1,0),R_BEAM,.6,25,130,7,i*5+1,Ease.smooth(age,.14f,.26f),.5f,1);
        }
        // Cage of vertical rings flickering round the body: white-pink and magenta with a few dark violet ones.
        if(t>=CAGE-.05f&&t<CUT){
            for(int k=0;k<60;k++){
                float born=CAGE-.05f+(float)Ease.hash(k*11+1)*(CUT-CAGE-.1f),life=.14f+.1f*(float)Ease.hash(k*13+2),u=(t-born)/life;
                if(u<0||u>=1)continue;
                double az=Ease.hash(k*17+3)*Math.PI*2+(t-CAGE)*2.5,len=100+60*Ease.hash(k*19+4);
                Vec3d r=new Vec3d(Math.cos(az),0,Math.sin(az));
                boolean dark=k%6==2;int style=dark?2:k%5==1?5:k%3==0?7:6;
                KagenoFire.crescent(pos.add(0,.2,0),r,new Vec3d(0,1,0),1.7*H*(.85+.3*Ease.hash(k*31)),(.08+.14*Ease.hash(k*23))*H,Ease.hash(k*29)*20,len,style,k,Ease.smooth(u,.55f,1),.5f,dark?.7f:.9f*(float)Math.sin(Math.PI*Math.min(1,u*1.6)));
            }
        }
        // A dark-blue ring winds up round the waist just before the cut and goes into the slashes.
        if(t>=SWELL-.14f&&t<SWELL+.08f){
            float a=t<SWELL?Ease.smooth(t,SWELL-.14f,SWELL-.05f):1-Ease.smooth(t,SWELL,SWELL+.08f);
            for(int s=0;s<2;s++)KagenoFire.crescent(pos.add(0,.9-.3*s,0),new Vec3d(1,0,0),new Vec3d(0,0,1),2.4,.5+.3*s,t*900+s*170,300,7,s*3+2,0,.5f,a);
        }
        // The spin cut: blade crescents whirling round the body and spreading out, each wearing away from its tail
        // toward its edge so it reads as a slash passing, not a disc.
        for(int i=0;i<SLASHES.length;i++){
            double[] b=SLASHES[i];float age=(float)(t-SWELL-b[0]),life=(float)b[1];if(age<0||age>=life)continue;
            double grow=Ease.out(age,0,life),outer=b[2]+(b[3]-b[2])*grow,width=Math.min(b[4]*(.85+.3*grow),outer*.6);
            double head=sweepStart(c)+b[8]+SPIN*age;
            var plane=slashPlane(c,b[7]);
            KagenoFire.crescent(pos.add(0,b[6],0),plane[0],plane[1],outer,width,head-b[5],b[5],b[9]>.5?7:4,i*3+5,
                .97f*Ease.smooth(age,life*.2f,life),.85f,1-Ease.smooth(age,life*.55f,life),.2);
        }
        // The slashes break into thick blue arcs flying away.
        if(t>=SWELL+.18f&&t<SWELL+.68f){
            float u=Ease.unit(t,SWELL+.18f,SWELL+.68f);
            // Thick ribbons, some tilted, sweeping out past a camera standing a few blocks off.
            for(int k=0;k<7;k++){double start=k*51+Ease.hash(k*3)*30,tilt=(k%3-1)*.3;
                KagenoFire.crescent(pos.add(0,.3+.8*Ease.hash(k*5),0),new Vec3d(1,0,0),new Vec3d(0,Math.sin(tilt),Math.cos(tilt)),4.5+10*Ease.out(u,0,1),2.2,start,55,7,k+6,Ease.smooth(u,.45f,1),.5f,.95f);}
        }
    }
    @Override public void shaders(Fx c){
        float t=c.sec;
        List<Vec3d> pts=new ArrayList<>();List<float[]> cells=new ArrayList<>();
        // Black smoke spreading out from both sides of the body like wings while the cuts begin, thick and wide.
        if(t>=.86f&&t<1.5f){
            float a=Ease.smooth(t,.86f,.92f)*(1-Ease.smooth(t,1.12f,1.5f)),spread=Ease.out(t,.86f,1.08f);
            for(int i=0;i<26;i++){
                double side=(i%2==0?1:-1)*(.6+4.4*Ease.hash(i*3+1)*spread),back=(Ease.hash(i*5+2)-.6)*1.8;
                Vec3d p=c.origin.add(c.right.multiply(side)).add(c.f.multiply(back)).add(0,.4+1.6*Ease.hash(i*7+3)+.3*Math.abs(side)*.2+.4*Ease.unit(t,.9f,1.5f)*Ease.hash(i*9+4),0);
                pts.add(p);cells.add(new float[]{(float)((1.3+1.0*Ease.hash(i*11+4))*(.6+.4*spread)),a*.7f,(float)(.05*Ease.hash(i*13+5)),0,i*23+9});
            }
        }
        // Light pink flame wisps left burning all round after the cut.
        if(t>=CUT+.05f&&t<CUT+1.05f)for(int i=0;i<56;i++){
            float born=CUT+.05f+.38f*(float)Ease.hash(i*3+1),life=.4f+.22f*(float)Ease.hash(i*5+2),u=(t-born)/life;if(u<0||u>=1)continue;
            double az=Ease.hash(i*7+3)*Math.PI*2,r=1.8+6.4*Math.sqrt(Ease.hash(i*11+4));
            Vec3d p=c.pos.add(Math.cos(az)*r,.45+.4*Ease.hash(i*13+5)+.45*u,Math.sin(az)*r);
            pts.add(p);cells.add(new float[]{(float)((.6+.5*Ease.hash(i*17+6))*(1-.25*u)),Ease.smooth(u,0,.18f)*(1-Ease.smooth(u,.55f,1)),(float)(1.0+.12*Ease.hash(i*19+7)),1,i*41+77});
        }
        KagenoFire.cells(c.matrix,c.camera,pts,cells,c.age);
        // On the effect layer: dust kicked up where the dash leaves and along its line, and rings of it thrown out by the cut.
        if(FxLayer.available()&&t>=D-.02f&&t<CUT+1.3f){
            List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
            FxLib.dustRing(c.origin,t,D,.4,3.6,16,1.15,dust,puffs,121);
            Vec3d end=new Vec3d(c.pos.x,c.origin.y,c.pos.z);double len=c.origin.distanceTo(end);
            for(int i=0;i<24&&len>2;i++){
                double along=(i+.5)/24*len;float born=passes(along),s=t-born-.02f*(float)Ease.hash(i*3+131),life=.7f+.3f*(float)Ease.hash(i*5+132);if(s<0||s>=life)continue;float u=s/life;
                Vec3d q=c.origin.add(c.f.multiply(along)).add(c.right.multiply((Ease.hash(i*7+133)-.5)*1.8*(1+u))).add(0,.25+.6*u,0);
                dust.add(q);puffs.add(new float[]{(float)((.4+.5*Ease.out(u,0,.5f))*(.8+.4*Ease.hash(i*11+134))),.8f*Ease.smooth(s,0,.05f)*(1-Ease.smooth(u,.5f,1)),.55f*(1-u),u,i*37+135});
            }
            FxLib.dustRing(end,t,CUT,1.2,9.5,28,1.6,dust,puffs,141);
            FxLib.dustRing(end,t,CUT+.06f,.8,4.5,14,1.25,dust,puffs,151);
            if(!dust.isEmpty())KagenoFire.dust(c.matrix,c.camera,dust,puffs,c.age);
        }
        // Targets hit by the beams are held in dark orbs until the dash takes them.
        if(t>=BEAMS[0]+.05f&&t<D+.4f&&!c.targets.isEmpty()){
            float form=Ease.out(t,BEAMS[0]+.05f,BEAMS[0]+.2f),a=form*(1-Ease.smooth(t,D+.1f,D+.4f));
            List<Vec3d> orbs=new ArrayList<>();List<Double> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
            for(LivingEntity e:c.targets){orbs.add(e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0));sizes.add(.6*H*(.4+.6*form));alphas.add(a);}
            KagenoFire.billboards(c.matrix,c.camera,10,orbs,sizes,alphas,c.age);
        }
    }
    // ───────────────────────────── effect layer ─────────────────────────────

    @Override public boolean layered(){return true;}
    /**
     * Lights: the hand's glow in the windup, the circle under the stance, each deep-blue beam lighting the dash line
     * as it flies (the flash it leaves at the blade and on whoever it passes through), the raised blade's glow, the
     * pink burst at the dash's start, the rings popping along the line and the caster's light along it, the cage's
     * pink glow, then the spin cut's blinding flash with lights round the floor where the slashes pass and the pink
     * wisps it leaves. For viewers close by the cut has the impact frame, a colour split and a light shaft. Decals:
     * the beams scorch the floor under their line, the dash burns its line into the floor, the spin cut gashes it
     * eight ways round the caster.
     */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;Vec3d pos=c.pos;
        if(t>=.13f&&t<B+.05f)f.light(SparkSkill.hand(c),0xcdb0ff,3,.9f*Ease.unit(t,.13f,B));
        if(t>=.6f&&t<D+.3f){float pulse=0;for(float b:BEAMS)pulse=Math.max(pulse,FxLib.kick(t,b,.12f));
            f.light(c.origin.add(0,.4,0),0x5c4cff,6,(.6f+1.2f*pulse)*Ease.smooth(t,.6f,.75f)*(1-Ease.smooth(t,D+.04f,D+.3f)));}
        for(int i=0;i<BEAMS.length;i++){
            float age=t-BEAMS[i];if(age<-.01f||age>=.26f)continue;
            if(age>=0)f.light(beam(c,i,age),0x5c4cff,9,2.4f*(1-Ease.smooth(age,.12f,.26f)));
            f.light(muzzle(c),0x8a7cff,6,2.0f*FxLib.kick(t,BEAMS[i],.06f));
            float k=FxLib.kick(t,BEAMS[i]+.03f,.1f);if(k>.01f)for(var e:strip(c))f.light(e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0),0x8a7cff,6,2.2f*k);
        }
        if(t>=BEAMS[0]){
            float heat=1-Ease.smooth(t,BEAMS[2],D+.6f),a=Ease.smooth(t,BEAMS[0],BEAMS[0]+.05f)*(1-Ease.smooth(t,3.35f,3.68f));
            f.decal(c.origin.add(c.f.multiply(15.5)),c.f,13,.32,heat*.8f,.8f*a,23.9f);
        }
        if(t>=1.52f&&t<D+.03f)f.light(c.origin.add(c.f.multiply(.45)).add(0,2.2,0),0xff9ce8,5,1.6f*Ease.smooth(t,1.6f,1.72f));
        if(t>=D-.01f&&t<D+.6f)f.light(c.origin.add(0,1,0),0xff3cc8,13,3.6f*FxLib.kick(t,D,.15f));
        if(t>=D&&t<D+.45f)f.light(pos.add(0,1.2,0),0xa070ff,8,2.0f*(1-Ease.smooth(t,D+.24f,D+.45f)));
        for(int k=1;k<=Math.min(8,rings(c));k++){float at=passes(k*RING_STEP);if(t>=at-.01f&&t<at+.3f)f.light(c.origin.add(c.f.multiply(k*RING_STEP)).add(0,1,0),0xff7ad8,6,1.6f*FxLib.kick(t,at,.1f));}
        float cage=Ease.smooth(t,CAGE,CAGE+.12f)*(1-Ease.smooth(t,CUT+.05f,CUT+.2f));
        if(cage>0)f.light(pos.add(0,1.2,0),0xff4fd0,7,(.9f+.8f*Ease.smooth(t,CAGE,SWELL))*cage);
        Vec3d end=new Vec3d(pos.x,c.origin.y,pos.z);
        if(t>=CUT-.01f){
            f.light(pos.add(0,1,0),0xc8b8ff,16,5.0f*FxLib.kick(t,CUT,.2f));
            for(int k=0;k<6;k++){double az=k*Math.PI/3+.3;f.light(pos.add(Math.cos(az)*5,.8,Math.sin(az)*5),0x5c4cff,7,2.0f*FxLib.kick(t,CUT+.04f+.01f*k,.15f));}
            float wisps=Ease.smooth(t,CUT+.1f,CUT+.3f)*(1-Ease.smooth(t,CUT+.7f,CUT+1.05f));
            if(wisps>0)f.light(pos.add(0,.8,0),0xffa8f0,10,1.0f*wisps);
            float near=FxLib.near(c,pos);
            if(near>0){f.impact(near*FxLib.impact(t,CUT),FxLib.impactMode(t,CUT));f.chroma(near*.012f*FxLib.kick(t,CUT,.12f));f.shaft(pos.add(0,1,0),0xd0c4ff,.7f*near*FxLib.kick(t,CUT,.22f));}
            float heat=1-Ease.smooth(t,CUT+.15f,CUT+1.1f),a=Ease.smooth(t,CUT,CUT+.05f)*(1-Ease.smooth(t,3.35f,3.68f));double open=Ease.out(t,CUT,CUT+.12f);
            for(int k=0;k<8;k++){double ang=Math.toRadians(25+45*k+10*Ease.hash(k*7+151)),reach=k%2==0?1:.65;Vec3d dir=c.f.multiply(Math.cos(ang)).add(c.right.multiply(Math.sin(ang)));
                f.decal(end.add(dir.multiply((2.8*open+.6)*reach)),dir,(2.4*open+.2)*reach,k%2==0?.45:.32,heat,a,21.1f+k*3.7f);}
        }
        if(t>=D){
            double len=c.origin.distanceTo(end);
            if(len>1.5){float heat=1-Ease.smooth(t,D+.2f,D+1.4f),a=Ease.smooth(t,D,D+.08f)*(1-Ease.smooth(t,3.35f,3.68f));
                f.decal(c.origin.add(end).multiply(.5),c.f,len*.5,.6,heat,a,17.3f);}
        }
    }
    /**
     * The air bends along each beam and in a ring where it leaves the blade, in rings over the floor and round the
     * line as the dash leaves, along the line and in each ring popping on it, and in the spin cut's slashes and its
     * shock over the floor.
     */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;Vec3d pos=c.pos;
        for(int i=0;i<BEAMS.length;i++){
            float age=t-BEAMS[i];if(age<0||age>=.22f)continue;
            Vec3d o=beam(c,i,age).add(0,-R_BEAM*.55,0);
            d.arc(o,c.right,FxLib.UP,R_BEAM+.1,.9,25,130,.8f*(1-Ease.smooth(age,.1f,.22f)));
            if(age<.18f)d.ring(muzzle(c),c.right,FxLib.UP,.3+2.0*Ease.out(age,0,.18f),.6,.5f*(1-Ease.unit(age,0,.18f)));
        }
        if(t>=D-.01f&&t<D+.4f){
            float in=Ease.smooth(t,D-.01f,D+.015f);
            d.ring(c.origin.add(0,.05,0),c.right,c.f,.5+9*Ease.out(t,D,D+.4f),1.6,.8f*in*(1-Ease.unit(t,D,D+.4f)));
            d.ring(c.origin.add(0,1,0),c.right,FxLib.UP,.5+3.4*Ease.out(t,D,D+.22f),.9,.6f*in*(1-Ease.unit(t,D,D+.22f)));
        }
        if(t>=D&&t<D+.35f){Vec3d from=c.origin.add(0,1,0),to=pos.add(0,1,0);if(from.distanceTo(to)>1)d.band(from,to,FxLib.UP,1.8,.9f*(1-Ease.smooth(t,D+.12f,D+.35f)));}
        for(int k=1;k<=rings(c);k++){float at=passes(k*RING_STEP);if(t>=at&&t<at+.25f)d.ring(c.origin.add(c.f.multiply(k*RING_STEP)).add(0,1,0),c.right,FxLib.UP,.4+1.9*Ease.out(t,at,at+.22f),.6,.5f*(1-Ease.unit(t,at,at+.25f)));}
        for(int i=0;i<SLASHES.length;i++){
            double[] b=SLASHES[i];float age=(float)(t-SWELL-b[0]),life=(float)b[1];if(age<0||age>=life)continue;
            double grow=Ease.out(age,0,life),outer=b[2]+(b[3]-b[2])*grow,width=Math.min(b[4]*(.85+.3*grow),outer*.6);
            var plane=slashPlane(c,b[7]);double head=sweepStart(c)+b[8]+SPIN*age;
            d.arc(pos.add(0,b[6],0),plane[0],plane[1],outer,width,head-b[5],b[5],(b[9]>.5?.7f:.4f)*(1-Ease.smooth(age,life*.5f,life)));
        }
        if(t>=CUT-.01f&&t<CUT+.55f){
            float in=Ease.smooth(t,CUT-.01f,CUT+.015f);Vec3d floor=new Vec3d(pos.x,c.origin.y+.05,pos.z);
            d.ring(floor,c.right,c.f,.5+13*Ease.out(t,CUT,CUT+.55f),2.0,.95f*in*(1-Ease.unit(t,CUT,CUT+.55f)));
            d.ring(floor,c.right,c.f,.4+7*Ease.out(t,CUT,CUT+.35f),1.4,.7f*in*(1-Ease.unit(t,CUT,CUT+.35f)));
        }
    }
    /**
     * Chunks torn from the floor where the dash leaves and along its line as the caster passes, and the spin cut
     * tearing it up round him: chunks thrown out and the floor heaving in a wide ring.
     */
    @Override public void solids(Fx c,FxSolids s){
        float t=c.sec;
        if(t>=D&&t<D+1.3f)FxLib.chunks(s,c.origin,t-D,10,1301,3.5,6,.18,.8f);
        for(int k=1;k<=Math.min(6,rings(c));k+=2){float at=passes(k*RING_STEP);if(t>=at&&t<at+1.2f)FxLib.chunks(s,c.origin.add(c.f.multiply(k*RING_STEP)),t-at,5,1351+k*17,2.5,5,.15,.7f);}
        if(t<CUT||t>=CUT+1.4f)return;Vec3d end=new Vec3d(c.pos.x,c.origin.y,c.pos.z);
        FxLib.chunks(s,end,t-CUT,30,1401,6.5,7.5,.22,.9f);
        FxLib.heave(s,end,t-CUT,11,.42,17,1.3,1.4);
    }
    @Override public float flash(Fx c){
        // The widest slash passing under a nearby camera tints the view deep blue for a moment.
        float t=c.sec;double r=reach(t);if(r<=0)return 0;
        // On the effect layer the cut's impact frame owns those frames; the tint follows it.
        if(FxLayer.available()&&t>=CUT-.02f&&t<CUT+.13f)return 0;
        double d=Math.hypot(c.camera.x-c.pos.x,c.camera.z-c.pos.z);
        return d>18?0:.38f*(float)Math.max(0,1-Math.abs(r-d)/3)*(1-Ease.smooth(t,SWELL+.2f,SWELL+.36f));
    }
    @Override public int flashColor(Fx c){return 0x2c10c8;}
}
