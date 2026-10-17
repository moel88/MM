package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;
import static dev.sangwoo.celestialfall.client.kageno.SparkSkill.B;
import static dev.sangwoo.celestialfall.client.kageno.SparkSkill.H;

/**
 * T held "어둠을 가로지르다", rebuilt from the user's recording: T's windup and sparkles, then back to a ready
 * stance; black smoke spreads out on both sides like wings while the sword cuts left, right and left again,
 * throwing three deep-blue crescent beams up the dash line (each just before the server's hit at 1.0, 1.3 and
 * 1.55 s); the sword comes up in front of the face, and at 1.74 s a pink burst launches a flat-out dash that ends
 * in a white-violet spike. A cage of vertical rings and pink orbs forms around the body, then the spin cut sweeps a
 * huge deep-blue blade band round the body at waist height that spreads far out and breaks into thick blue arcs
 * flying away, leaving pink flame wisps burning on the ground. Seconds after the cast.
 */
final class ThroughSkill implements Fx.Skill {
    /** Ready stance, dash start (D), end of the glide, ring cage and the final cut (the server's {@code RUSH_FINISH}). */
    static final float READY=B+.13f,D=B+1.09f,GLIDE=D+.14f,DOME=D+.2f,CUT=D+.85f;
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

    /** Where beam {@code i} is {@code age} seconds after it left: chest high, flying up the dash line and rising a little. */
    static Vec3d beam(Fx c,int i,float age){double d=.8+BEAM_SPEED*age;return c.origin.add(c.f.multiply(d)).add(0,1.25+.05*d,0);}

    @Override public void additive(Fx c,EffectMesh m){
        SparkSkill.sparkles(c,m);SparkSkill.charge(c,m);
        float t=c.sec;Vec3d pos=c.pos;
        // Two white comets streak up the dash line as the stance is taken.
        for(int k=0;k<2;k++){
            float born=.79f+k*.035f,u=(t-born)/.16f;if(u<0||u>=1)continue;
            Vec3d head=c.origin.add(c.f.multiply(2+26*Ease.out(u,0,1))).add(c.right.multiply(k==0?-.9:1.1)).add(0,1.5+.6*k,0);
            m.needle(head.subtract(c.f.multiply(2.4)),head,.3,.85,0xf0dcff,.9f*(1-u));m.softDisc(head,.35,0xffffff,1-u);
        }
        // The cuts leave violet trails; the beams' tips glow pink.
        if(t>=.86f&&t<1.62f)FxLib.trail(m,c,CLIP,t,.07f,8,.25,1.35,0x9a3cff,.75f*Ease.smooth(t,.86f,.9f)*(1-Ease.smooth(t,1.56f,1.62f)));
        for(int i=0;i<BEAMS.length;i++){
            float age=t-BEAMS[i];if(age<0||age>=.26f)continue;float a=1-Ease.smooth(age,.12f,.26f);
            Vec3d o=beam(c,i,age);double r=1.5;
            for(int s=-1;s<=1;s+=2){Vec3d tip=o.add(c.right.multiply(s*r*Math.cos(Math.toRadians(25)))).add(0,r*Math.sin(Math.toRadians(25))-r*.55,0);m.softDisc(tip,.42,0xff7ad8,.8f*a);m.star(tip,.3,0xffffff,a);}
            if(age<.05f)FxLib.flash(m,c.origin.add(c.f.multiply(1)).add(0,1.25,0),.9,0x8a7cff,1-age/.05f);
        }
        // The raised blade gathers a white-pink glow before the dash.
        if(t>=1.6f&&t<D+.03f){float a=Ease.smooth(t,1.6f,1.72f);Vec3d tip=c.origin.add(c.f.multiply(.45)).add(0,2.2,0);m.softDisc(tip,.3+.35*a,0xff9ce8,.6f*a);m.star(tip,.5*a,0xffffff,a);}
        // Launch: a pink burst left at the start, and the dash line itself fading.
        if(t>=D&&t<D+.22f){
            float u=Ease.unit(t,D,D+.22f),a=1-Ease.smooth(t,D+.04f,D+.22f);Vec3d o=c.origin.add(0,1,0);
            m.softDisc(o,2.5+4*Ease.out(t,D,D+.1f),0xff2fb4,.65f*a);m.softDisc(o,1.2+1.6*u,0xff7ad0,.85f*a);m.softDisc(o,.5+.5*u,0xffd8f4,.7f*a);
            for(int k=0;k<18;k++){double az=k*Math.PI/9+Ease.hash(k*3)*.3,el=(Ease.hash(k*5)-.4)*1.3,len=(2.5+5*Ease.hash(k*7))*(.35+.65*Ease.out(t,D,D+.08f));
                Vec3d d=c.f.multiply(Math.cos(el)*Math.cos(az)).add(c.right.multiply(Math.cos(el)*Math.sin(az))).add(0,Math.sin(el),0);m.needle(o.add(d.multiply(.5)),o.add(d.multiply(.5+len)),.45,.25,k%3==0?0xffe0f8:0xff3cc8,a);}
        }
        if(t>=D&&t<D+.42f){float a=1-Ease.smooth(t,D+.18f,D+.42f);Vec3d from=c.origin.add(0,1,0),to=pos.add(0,1,0);m.glowLine(from,to,.1,0xa855f8,a);m.softLine(from,to,.6,0x7c3cf0,.35f*a);}
        // Arrival: a jagged white-violet spike shooting up from the body.
        if(t>=D+.05f&&t<D+.24f){
            float a=1-Ease.smooth(t,D+.14f,D+.24f);long step=(long)Math.floor(t*40);
            for(int k=0;k<5;k++)FxLib.zig(m,pos.add((k-2)*.16,.1,(Ease.hash(k*9)-.5)*.3),pos.add((Ease.hash((int)step*3+k)-.5)*1.2,3.4+.5*k,(Ease.hash((int)step*5+k)-.5)*1.2),9,.15-.02*k,k%2==0?0xeadcff:0x9c6cff,a,(int)step*7+k);
            m.softDisc(pos.add(0,1.6,0),1.6,0xa070ff,.5f*a);m.softDisc(pos.add(0,1.2,0),.7,0xf4ecff,.7f*a);
        }
        // Inside the cage: pink orbs drifting round the body.
        if(t>=DOME&&t<CUT+.35f){
            float a=Ease.smooth(t,DOME,DOME+.12f)*(1-Ease.smooth(t,CUT+.1f,CUT+.35f));
            for(int k=0;k<16;k++){double ang=t*(1.2+.8*Ease.hash(k*3))+k*Math.PI*2/16,r=1.8+2*Ease.hash(k*5)+(t>CUT?6*Ease.out(t,CUT,CUT+.35f):0);
                Vec3d p=pos.add(Math.cos(ang)*r,.4+2.6*Ease.hash(k*7)+.25*Math.sin(t*3+k),Math.sin(ang)*r);m.softDisc(p,.28+.22*Ease.hash(k*11),0xff4fd0,a);m.softDisc(p,.1+.06*Ease.hash(k*13),0xffd8f4,a);}
        }
        // The cut: a flash at the body and the blade band's bright outer edge running outward.
        if(t>=CUT&&t<CUT+.1f)FxLib.flash(m,pos.add(0,1,0),2,0x9a7cff,1-Ease.unit(t,CUT,CUT+.1f));
        if(t>=SWELL+SWEEP&&t<SWELL+.4f){float u=Ease.unit(t,SWELL+SWEEP,SWELL+.4f);FxLib.ring(m,pos.add(0,.7,0),reach(t),1.4,0x5c3cff,.7f*(1-u));}
        // Embers rising from the wisps.
        if(t>=CUT+.2f&&t<CUT+1f){float a=Ease.smooth(t,CUT+.2f,CUT+.3f)*(1-Ease.smooth(t,CUT+.7f,CUT+1f));FxLib.embers(m,pos,7,2.5,22,t-CUT,.8f,.05,0xffa8f0,a,13);}
    }
    /** The spin cut's blade band starts sweeping just before the cut lands so it is round the body with the server's hit; one turn takes SWEEP. */
    static final float SWELL=CUT-.13f,SWEEP=.09f;
    /** Outer edge of the blade band (blocks): 3 as it starts, past 16 half a second later. */
    static double reach(float t){return 3+13*Ease.out(t,SWELL,SWELL+.5f);}
    /** Where the band starts (degrees, clockwise from above like the spin): beside the sword at the left. */
    static double sweepStart(Fx c){return Math.toDegrees(Math.atan2(c.f.z,c.f.x))-90;}
    @Override public void crescents(Fx c){
        float t=c.sec;Vec3d pos=c.pos;
        // The beams: deep-blue crescents bowed upward, flying up the dash line.
        for(int i=0;i<BEAMS.length;i++){
            float age=t-BEAMS[i];if(age<0||age>=.26f)continue;
            Vec3d o=beam(c,i,age).add(0,-1.5*.55,0);
            KagenoFire.crescent(o,c.right,new Vec3d(0,1,0),1.5,.42,25,130,7,i*5+1,Ease.smooth(age,.14f,.26f),.5f,1);
        }
        // Cage of vertical rings flickering round the body: white-pink and magenta with a few dark violet ones.
        if(t>=DOME-.05f&&t<CUT){
            for(int k=0;k<60;k++){
                float born=DOME-.05f+(float)Ease.hash(k*11+1)*(CUT-DOME-.1f),life=.14f+.1f*(float)Ease.hash(k*13+2),u=(t-born)/life;
                if(u<0||u>=1)continue;
                double az=Ease.hash(k*17+3)*Math.PI*2+(t-DOME)*2.5,len=100+60*Ease.hash(k*19+4);
                Vec3d r=new Vec3d(Math.cos(az),0,Math.sin(az));
                boolean dark=k%6==2;int style=dark?2:k%5==1?5:k%3==0?7:6;
                KagenoFire.crescent(pos.add(0,.2,0),r,new Vec3d(0,1,0),1.7*H*(.85+.3*Ease.hash(k*31)),(.08+.14*Ease.hash(k*23))*H,Ease.hash(k*29)*20,len,style,k,Ease.smooth(u,.55f,1),.5f,dark?.7f:.9f*(float)Math.sin(Math.PI*Math.min(1,u*1.6)));
            }
        }
        // A dark-blue ring winds up round the waist just before the cut, then is thrown out with the blade band.
        if(t>=SWELL-.14f&&t<SWELL+.4f){
            float a=t<SWELL?Ease.smooth(t,SWELL-.14f,SWELL-.05f):1-Ease.smooth(t,SWELL+.2f,SWELL+.4f);
            double r=t<SWELL?2.4:reach(t);
            for(int s=0;s<2;s++)KagenoFire.crescent(pos.add(0,.9-.3*s,0),new Vec3d(1,0,0),new Vec3d(0,0,1),r,.5+.3*s+(t<SWELL?0:r*.08),t*900+s*170,300,7,s*3+2,0,.5f,a);
        }
        // The band breaks into thick blue arcs flying away.
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
        // Black smoke spreading out from both sides of the body like wings while the cuts begin.
        if(t>=.86f&&t<1.4f){
            float a=Ease.smooth(t,.86f,.92f)*(1-Ease.smooth(t,1.08f,1.4f)),spread=Ease.out(t,.86f,1.05f);
            for(int i=0;i<16;i++){
                double side=(i%2==0?1:-1)*(.6+3.2*Ease.hash(i*3+1)*spread),back=(Ease.hash(i*5+2)-.6)*1.4;
                Vec3d p=c.origin.add(c.right.multiply(side)).add(c.f.multiply(back)).add(0,.4+1.2*Ease.hash(i*7+3)+.3*Math.abs(side)*.2,0);
                pts.add(p);cells.add(new float[]{(float)((1.0+.8*Ease.hash(i*11+4))*(.6+.4*spread)),a*.62f,(float)(.05*Ease.hash(i*13+5)),0,i*23+9});
            }
        }
        // Light pink flame wisps left burning all round after the cut.
        if(t>=CUT+.05f&&t<CUT+1f)for(int i=0;i<40;i++){
            float born=CUT+.05f+.35f*(float)Ease.hash(i*3+1),life=.4f+.2f*(float)Ease.hash(i*5+2),u=(t-born)/life;if(u<0||u>=1)continue;
            double az=Ease.hash(i*7+3)*Math.PI*2,r=1.8+5.2*Math.sqrt(Ease.hash(i*11+4));
            Vec3d p=c.pos.add(Math.cos(az)*r,.45+.4*Ease.hash(i*13+5)+.45*u,Math.sin(az)*r);
            pts.add(p);cells.add(new float[]{(float)((.6+.45*Ease.hash(i*17+6))*(1-.25*u)),Ease.smooth(u,0,.18f)*(1-Ease.smooth(u,.55f,1)),(float)(1.0+.12*Ease.hash(i*19+7)),1,i*41+77});
        }
        KagenoFire.cells(c.matrix,c.camera,pts,cells,c.age);
        // The spin cut: a deep-blue blade band sweeping once round the body at waist height, two layers thick,
        // spreading far out, then thinning out from its tail as it breaks into the arcs.
        if(t>=SWELL&&t<SWELL+.42f){
            float a=1-Ease.smooth(t,SWELL+.2f,SWELL+.42f);double r=reach(t),turn=360*Ease.out(t,SWELL,SWELL+SWEEP),start=sweepStart(c)+150*(t-SWELL);
            for(int layer=0;layer<2;layer++)KagenoFire.sweep(c.matrix,c.pos.add(0,.55+.55*layer,0),.6,r*(1-.08*layer),start+layer*14,turn,a*(1-.25f*layer),c.age);
        }
        // Targets hit by the beams are held in dark orbs until the dash takes them.
        if(t>=BEAMS[0]+.05f&&t<D+.4f&&!c.targets.isEmpty()){
            float form=Ease.out(t,BEAMS[0]+.05f,BEAMS[0]+.2f),a=form*(1-Ease.smooth(t,D+.1f,D+.4f));
            List<Vec3d> orbs=new ArrayList<>();List<Double> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
            for(LivingEntity e:c.targets){orbs.add(e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0));sizes.add(.6*H*(.4+.6*form));alphas.add(a);}
            KagenoFire.billboards(c.matrix,c.camera,10,orbs,sizes,alphas,c.age);
        }
    }
    @Override public float flash(Fx c){
        // The blade band's edge passing under a nearby camera tints the view deep blue for a moment.
        float t=c.sec;if(t<SWELL||t>=SWELL+.4f)return 0;
        double d=Math.hypot(c.camera.x-c.pos.x,c.camera.z-c.pos.z),r=reach(t);
        return d>18?0:.38f*(float)Math.max(0,1-Math.abs(r-d)/3)*(1-Ease.smooth(t,SWELL+.2f,SWELL+.4f));
    }
    @Override public int flashColor(Fx c){return 0x2c10c8;}
}
