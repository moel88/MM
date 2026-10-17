package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * T "어둠 속의 불꽃": a 0.65 s windup (body turned 55° right, left hand raised in a burst of
 * sparkles, sword planted point-down), then a white blast cloud that turns into pink/violet fire
 * over a 6×6 character-height field, a dark orb on each hit target and an ink-black scorch.
 * Layered on top: motes charging the raised hand and violet cracks spreading from the planted
 * sword, a flash, ground shockwave, light column and spark burst at the blast, embers, crackling
 * bolts, smoke and heat rings over the burning field, a glow halo on the trapped orbs and a last
 * pulse as the fire dies. Seconds after the cast; B = 0.65 s is the blast. 1 character height (H) = 1.8 blocks.
 */
final class SparkSkill implements Fx.Skill {
    static final float B=.65f;
    static final double H=1.8;
    /** Windup shared with T+ (through the dark). */
    static final R6.Pose PLANT=pose().body(-55,33,0).at(0,-.09,0).head(0,15,0).lArm(40,-50).rArm(-82,160).lLeg(-75,0).rLeg(-75,180).blade(-75,25);
    static final Anim WINDUP=Anim.joints(
        k(0,KagenoClips.IDLE),
        k(.04,pose().body(-15,0,0).lArm(-45,30).rArm(-80,20).blade(-55,40)),
        k(.11,pose().body(-55,0,0).lArm(0,-55).rArm(-85,150).lLeg(-75,0).rLeg(-75,180).blade(-60,0),Anim.Ease.LINEAR),
        k(.20,PLANT,Anim.Ease.OUT),
        k(B,PLANT.body(-55,35,0),Anim.Ease.HOLD));
    static final Anim CLIP=Anim.joints(
        k(0,KagenoClips.IDLE),
        k(.04,pose().body(-15,0,0).lArm(-45,30).rArm(-80,20).blade(-55,40)),
        k(.11,pose().body(-55,0,0).lArm(0,-55).rArm(-85,150).lLeg(-75,0).rLeg(-75,180).blade(-60,0),Anim.Ease.LINEAR),
        k(.20,PLANT,Anim.Ease.OUT),
        k(B,PLANT.body(-55,35,0)),
        k(B+.035,PLANT.body(-27,18,0).lArm(10,-35).rArm(-75,100),Anim.Ease.LINEAR),
        k(B+.09,KagenoClips.IDLE,Anim.Ease.OUT),
        k(B+.5,KagenoClips.IDLE)).stops(Anim.stop(B,.05,.1));
    @Override public Anim clip(int combo){return CLIP;}
    @Override public float lifetime(){return B+5.6f;}

    /** The raised left hand, in front of the face of the turned body. */
    static Vec3d hand(Fx c){return c.origin.add(c.f.multiply(.5)).add(0,1.8,0).add(c.right.multiply(-.15));}
    /** Four-point stars bursting from the left hand during the windup (densest right away, then decaying). */
    static void sparkles(Fx c,EffectMesh m){
        if(c.sec<.11f||c.sec>=B)return;Vec3d hand=hand(c);
        for(int i=0;i<40;i++){
            float born=.11f+.5f*(i/40f)*(i/40f),life=.1f+.15f*(float)Ease.hash(i*13+1),u=(c.sec-born)/life;
            if(u<0||u>=1)continue;
            double az=Ease.hash(i*7+2)*Math.PI*2,el=(Ease.hash(i*5+3)-.35)*1.6,reach=(.2+2.0*Ease.hash(i*3+4))*(.6+.4*u);
            Vec3d dir=c.f.multiply(Math.cos(el)*Math.cos(az)*.6+.4).add(c.right.multiply(Math.cos(el)*Math.sin(az))).add(0,Math.sin(el),0);
            Vec3d p=hand.add(dir.normalize().multiply(reach)).add(0,-.6*Ease.hash(i*17),0);
            double size=(.18+.18*Ease.hash(i*11+5))*(1+.25*Math.sin(u*Math.PI*3));
            m.star(p,size,0xd0c4ef,(float)Math.sin(Math.PI*Math.min(1,u*1.4)));
        }
    }
    @Override public void additive(Fx c,EffectMesh m){sparkles(c,m);charge(c,m,CLIP);blastFx(c,m,c.origin.add(c.f.multiply(1.5*H)),3.5*H);emberFx(c,m,c.origin.add(c.f.multiply(1.7*H)).add(c.right.multiply(.4*H)),2.2*H,3.6f,4.3f);orbArcs(c,m,B,B+3.55f,B+3.8f);}
    @Override public void alpha(Fx c,EffectMesh m){heat(c,m,c.origin.add(c.f.multiply(1.6*H)).add(c.right.multiply(.3*H)),3.6f,4.2f);}

    /** Tip of the planted sword, from the windup clip. */
    static Vec3d swordTip(Fx c,Anim clip){var hb=KagenoClips.blade(c.origin,c.cast.yaw(),R6.poseOf(clip.frame(Math.min(c.sec,B))));return hb[0].add(hb[1].multiply(1.35));}
    /** Windup: motes spiral into the raised hand while violet cracks spread over the ground from the sword tip. */
    static void charge(Fx c,EffectMesh m,Anim clip){
        float t=c.sec;if(t<.11f||t>=B+.05f)return;Vec3d hand=hand(c);
        float u=Ease.unit(t,.11f,B),pulse=.85f+.15f*(float)Math.sin(t*40);
        FxLib.converge(m,hand,1.8,30,t,.11f,B,0xc8a8ff,7);
        m.softDisc(hand,.14+.3*u,0xcdb0ff,.45f*u*pulse);m.softDisc(hand,.07+.12*u,0xffffff,.9f*u);
        Vec3d tip=swordTip(c,clip);Vec3d g=new Vec3d(tip.x,c.origin.y+.03,tip.z);int seed=(int)c.cast.id().getLeastSignificantBits();
        float grow=Ease.out(t,.2f,B);
        for(int k=0;k<5;k++){
            double ang=k*1.2566+Ease.hash(seed+k)*.6;Vec3d p=g;int segs=(int)(6*grow);
            for(int i=0;i<segs;i++){double a2=ang+(Ease.hash(seed+k*13+i)-.5)*1.2;Vec3d q=p.add(Math.cos(a2)*.35,0,Math.sin(a2)*.35);m.glowLine(p,q,.025,0x9a60f0,.8f*grow);p=q;}
        }
        FxLib.glow(m,g,.5+.6*grow,0x5a2cc0,.5f*grow);
    }
    /** The blast: a flash, a ground shockwave, a light column shooting up and sparks thrown into the air. */
    static void blastFx(Fx c,EffectMesh m,Vec3d centre,double ringRadius){
        float t=c.sec-B;if(t<0||t>=.5f)return;float u=Ease.unit(t,0,.5f);
        Vec3d ground=new Vec3d(centre.x,c.origin.y,centre.z);
        FxLib.flash(m,centre.add(0,1.4,0),2.2*(1-Ease.smooth(t,0,.25f))+.2,0xf8e8ff,1-Ease.smooth(t,.05f,.25f));
        FxLib.ring(m,ground.add(0,.06,0),.5+ringRadius*Ease.out(t,0,.3f),.5,0xe8c8ff,.7f*(1-Ease.smooth(t,.1f,.4f)));
        FxLib.column(m,ground,6*H*Ease.out(t,0,.08f),.9*(1-u),0xffffff,.9f*(1-Ease.smooth(t,.08f,.3f)));
        for(int k=0;k<36;k++){
            double az=Ease.hash(k*3)*Math.PI*2,el=.4+1.1*Ease.hash(k*5),sp=(2+4*Ease.hash(k*7))*H;float life=(float)(.25+.25*Ease.hash(k*11)),v=t/life;if(v>=1)continue;
            Vec3d d=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
            m.star(centre.add(d.multiply(sp*v)).add(0,.4-3*v*v,0),.08+.06*Ease.hash(k*13),0xffe8ff,(1-v)*(1-v));
        }
    }
    /** Over the burning field: the ground lit violet, embers rising, bolts crackling between the flames, a last pulse. */
    static void emberFx(Fx c,EffectMesh m,Vec3d centre,double radius,float dieStart,float dieEnd){
        float t=c.sec-B;if(t<.3f||t>=dieEnd)return;float a=Ease.smooth(t,.3f,.6f)*(1-Ease.smooth(t,dieStart,dieEnd));
        Vec3d ground=new Vec3d(centre.x,c.origin.y+.04,centre.z);
        FxLib.glow(m,ground,radius*1.1,0x7c3cf0,.35f*a*(float)(.8+.2*Math.sin(t*6)));
        FxLib.embers(m,ground,radius,3.5*H,dev.sangwoo.celestialfall.CelestialFall.CONFIG.visualQuality==0?14:32,t,.9f,.07,0xffb0ff,a,5);
        long step=(long)Math.floor(c.sec*30);
        for(int k=0;k<3;k++){
            if(Ease.hash((int)step*5+k)<.45)continue;
            Vec3d p=ground.add((Ease.hash((int)step*7+k)-.5)*radius*1.6,.8*H+1.2*H*Ease.hash((int)step*11+k),(Ease.hash((int)step*13+k)-.5)*radius*1.6);
            Vec3d q=p.add((Ease.hash((int)step*17+k)-.5)*1.8,(Ease.hash((int)step*19+k)-.3)*1.2,(Ease.hash((int)step*23+k)-.5)*1.8);
            FxLib.zig(m,p,q,4,.03,0xd0a8ff,.85f*a,(int)step*29+k);
        }
        if(t>=dieStart-.1f&&t<dieStart+.4f){float u=Ease.unit(t,dieStart-.1f,dieStart+.4f);FxLib.ring(m,ground,radius*.7+radius*Ease.out(t,dieStart-.1f,dieStart+.4f),.4,0xc8a0ff,.5f*(1-u));}
    }
    /** Heat rings: translucent lavender bands drifting out and up over the field (alpha pass). */
    static void heat(Fx c,EffectMesh m,Vec3d centre,float dieStart,float dieEnd){
        float t=c.sec-B;if(t<.3f||t>=dieEnd)return;
        for(int k=0;k<3;k++){
            float cyc=((t-.3f)/.9f+k/3f)%1;double r=(1.2+2.6*cyc)*H;
            float a=.16f*(1-cyc)*Ease.smooth(cyc,0,.15f)*(1-Ease.smooth(t,dieStart,dieEnd));
            ComboSkill.band(m,new Vec3d(centre.x,c.origin.y+.3+.5*cyc,centre.z),r,.25+.3*cyc,k*120+t*20,360,0xbfa6f6,a);
        }
    }
    /** Dark smoke rolling up above the flames. */
    static void smoke(Fx c,float t,Vec3d centre,double radius,float end){
        if(t<.6f||t>=end)return;float a=.38f*Ease.smooth(t,.6f,1.2f)*(1-Ease.smooth(t,end-.8f,end));
        List<Vec3d> pts=new ArrayList<>();List<Double> sizes=new ArrayList<>();
        for(int i=0;i<7;i++){
            double ang=Ease.hash(i*7+5)*Math.PI*2,r=radius*Math.sqrt(Ease.hash(i*13+6)),rise=(.8+.6*Ease.hash(i*3))*(t-.6)*.35*H;
            pts.add(centre.add(Math.cos(ang)*r,1.7*H+rise+.4*Math.sin(t*1.3+i),Math.sin(ang)*r));sizes.add((.65+.45*Ease.hash(i*11+7))*(1+.12*(t-.6)));
        }
        KagenoFire.smoke(c.matrix,c.camera,pts,sizes,c.age,a);
    }
    /** Violet orbit arcs caging each trapped target's dark orb, a glow halo around it and ink drips. */
    static void orbArcs(Fx c,EffectMesh m,float from,float hold,float end){
        float t=c.sec;if(t<from+.15f||t>=end)return;
        float a=Ease.smooth(t,from+.15f,from+.31f)*(1-Ease.smooth(t,hold,end));
        for(LivingEntity e:c.targets){
            Vec3d o=e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);
            m.softDisc(o,1.25*H,0x7a3cf0,.25f*a*(float)(.8+.2*Math.sin(t*9)));
            for(int k=0;k<6;k++){float cyc=((t-from)*1.3f+(float)Ease.hash(k*7))%1;Vec3d p=o.add((Ease.hash(k*3)-.5)*.9,.3-cyc*1.6,(Ease.hash(k*5)-.5)*.9);m.needle(p,p.add(0,-.25,0),.06,.2,0x4a2a9a,a*(1-cyc));}
            for(int k=0;k<2;k++){
                double tilt=.5+k*1.1,spin=t*(2.2+k)+k;Vec3d u=new Vec3d(Math.cos(spin),Math.sin(tilt)*.4,Math.sin(spin)).normalize(),v=new Vec3d(0,1,0).crossProduct(u).normalize().add(0,Math.cos(tilt)*.3,0).normalize();
                Vec3d prev=null;
                for(int i=0;i<=20;i++){double ang=i*Math.PI*1.4/20;Vec3d p=o.add(u.multiply(Math.cos(ang)*1.15)).add(v.multiply(Math.sin(ang)*1.15));if(prev!=null)m.glowLine(prev,p,.025,0x9758ed,a*.8f);prev=p;}
            }
        }
    }
    @Override public void shaders(Fx c){
        float t=c.sec-B;if(t<0)return;
        scorch(c,t);
        cloud(c,t,c.origin.add(c.f.multiply(1.5*H)),2.9*H,1);
        fire(c,t);
        orbs(c,t,3.55f,3.8f);
        smoke(c,t,c.origin.add(c.f.multiply(1.7*H)).add(c.right.multiply(.4*H)),2.2*H,4.8f);
    }
    /** Billowy white cloud that grows in 3 frames, stays white to +0.27 s and gives way to the fire by +0.55 s. */
    static void cloud(Fx c,float t,Vec3d centre,double width,double stretch){
        if(t>=.6f)return;
        float grow=Ease.out(t,0,.05f),a=1-Ease.smooth(t,.3f,.55f);
        List<Vec3d> pts=new ArrayList<>();List<Double> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
        for(int i=0;i<10;i++){
            double fw=(Ease.hash(i*3+1)-.35)*width*1.2*stretch,rt=(Ease.hash(i*5+2)-.45)*width*.85,up=.3+Ease.hash(i*7+3)*1.7*H*.55;
            pts.add(centre.add(c.f.multiply(fw*grow)).add(c.right.multiply(rt*grow)).add(0,up*grow+.3,0));
            sizes.add((.65+.65*Ease.hash(i*11+4))*grow+.05);alphas.add(a);
        }
        KagenoFire.billboards(c.matrix,c.camera,9,pts,sizes,alphas,c.age);
    }
    private static final float[] HT={.3f,1.65f,1.75f,2.15f,2.25f,2.45f,2.55f,2.85f,2.95f,3.35f,3.45f,3.55f,3.65f,3.95f,4.05f};
    private static final float[] HH={3.6f,3.6f,2.1f,2.1f,2.55f,2.55f,3.2f,3.2f,2.8f,2.8f,2.0f,2.0f,1.75f,1.75f,0};
    /** Plume height (character heights) over time after the blast, from the dummy-view measurement. */
    static double plume(float t){
        if(t<HT[0])return HH[0]*Ease.out(t,0,HT[0]);
        for(int i=1;i<HT.length;i++)if(t<HT[i])return HH[i-1]+(HH[i]-HH[i-1])*Ease.smooth(t,HT[i-1],HT[i]);
        return 0;
    }
    /** World-anchored pink/violet fire over ≈6×6 H, biased forward and to the sword side. */
    static void fire(Fx c,float t){
        if(t<.25f||t>=4.05f)return;
        double top=plume(t)*H*.44;float in=Ease.smooth(t,.25f,.5f);
        List<Vec3d> roots=new ArrayList<>();List<double[]> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
        int n=dev.sangwoo.celestialfall.CelestialFall.CONFIG.visualQuality==0?10:18;
        for(int i=0;i<n;i++){
            double fw=(-.35+3.8*Ease.hash(i*7+1))*H,rt=(-1.6+3.2*Ease.hash(i*13+2))*H;
            double cycle=(t*(.8+.5*Ease.hash(i*3))+Ease.hash(i*5))%1,flare=.55+.45*Math.sin(Math.PI*cycle);
            roots.add(c.origin.add(c.f.multiply(fw)).add(c.right.multiply(rt)).add(0,.02,0));
            sizes.add(new double[]{ .50+.40*Ease.hash(i*17),Math.max(.2,top*flare*(.6+.4*Ease.hash(i*19)))});
            alphas.add(in*(float)(.65+.15*flare));
        }
        KagenoFire.tongues(c.matrix,c.camera,11,roots,sizes,alphas,c.age);
    }
    /** Dark ink orb on each target (≈1.2 H across), forming over +0.15..0.31 s, breaking up at the end. */
    static void orbs(Fx c,float t,float hold,float end){
        if(t<.15f||t>=end||c.targets.isEmpty())return;
        float form=Ease.out(t,.15f,.31f),a=form*(1-Ease.smooth(t,hold,end));double grow=1+.35*Ease.smooth(t,hold,end);
        List<Vec3d> pts=new ArrayList<>();List<Double> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
        for(LivingEntity e:c.targets){pts.add(e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0));sizes.add(.6*H*(.4+.6*form)*grow);alphas.add(a);}
        KagenoFire.billboards(c.matrix,c.camera,10,pts,sizes,alphas,c.age);
    }
    /** Opaque black ink splash under the fire, eroding away over +4.2..5.5 s. */
    static void scorch(Fx c,float t){
        if(t>=5.5f)return;float a=1-Ease.smooth(t,4.3f,5.5f);
        Vec3d centre=c.origin.add(c.f.multiply(1.7*H)).add(c.right.multiply(.4*H)).add(0,.015,0);
        KagenoFire.pool(c.matrix,centre,c.f.multiply(2.1*H),c.right.multiply(1.9*H),c.age,a);
    }
}
