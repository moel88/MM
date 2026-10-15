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
 * Seconds after the cast; B = 0.65 s is the blast. 1 character height (H) = 1.8 blocks.
 */
final class SparkSkill implements Fx.Skill {
    static final float B=.65f;
    static final double H=1.8;
    /** Windup shared with T+ (through the dark). */
    static final R6.Pose PLANT=pose().body(-55,33,0).at(0,-.09,0).head(0,15,0).lArm(40,-50).rArm(-82,160).lLeg(-75,0).rLeg(-75,180).blade(-75,25);
    static final Anim WINDUP=Anim.of(
        k(0,KagenoClips.IDLE),
        k(.04,pose().body(-15,0,0).lArm(-45,30).rArm(-80,20).blade(-55,40)),
        k(.11,pose().body(-55,0,0).lArm(0,-55).rArm(-85,150).lLeg(-75,0).rLeg(-75,180).blade(-60,0),Anim.Ease.LINEAR),
        k(.20,PLANT,Anim.Ease.OUT),
        k(B,PLANT.body(-55,35,0),Anim.Ease.HOLD));
    static final Anim CLIP=Anim.of(
        k(0,KagenoClips.IDLE),
        k(.04,pose().body(-15,0,0).lArm(-45,30).rArm(-80,20).blade(-55,40)),
        k(.11,pose().body(-55,0,0).lArm(0,-55).rArm(-85,150).lLeg(-75,0).rLeg(-75,180).blade(-60,0),Anim.Ease.LINEAR),
        k(.20,PLANT,Anim.Ease.OUT),
        k(B,PLANT.body(-55,35,0),Anim.Ease.HOLD),
        k(B+.07,KagenoClips.IDLE,Anim.Ease.SNAP),
        k(B+.5,KagenoClips.IDLE));
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
    @Override public void additive(Fx c,EffectMesh m){sparkles(c,m);orbArcs(c,m,B,B+3.55f,B+3.8f);}
    /** Violet orbit arcs caging each trapped target's dark orb. */
    static void orbArcs(Fx c,EffectMesh m,float from,float hold,float end){
        float t=c.sec;if(t<from+.15f||t>=end)return;
        float a=Ease.smooth(t,from+.15f,from+.31f)*(1-Ease.smooth(t,hold,end));
        for(LivingEntity e:c.targets){
            Vec3d o=e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);
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
        cloud(c,t,c.origin.add(c.f.multiply(1.5*H)),5*H,1);
        fire(c,t);
        orbs(c,t,3.55f,3.8f);
    }
    /** Billowy white cloud that grows in 3 frames, stays white to +0.27 s and gives way to the fire by +0.55 s. */
    static void cloud(Fx c,float t,Vec3d centre,double width,double stretch){
        if(t>=.6f)return;
        float grow=Ease.out(t,0,.05f),a=1-Ease.smooth(t,.3f,.55f);
        List<Vec3d> pts=new ArrayList<>();List<Double> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
        for(int i=0;i<22;i++){
            double fw=(Ease.hash(i*3+1)-.35)*width*1.2*stretch,rt=(Ease.hash(i*5+2)-.45)*width*.85,up=.4+Ease.hash(i*7+3)*3.4*H*.55;
            pts.add(centre.add(c.f.multiply(fw*grow)).add(c.right.multiply(rt*grow)).add(0,up*grow+.3,0));
            sizes.add((1.6+1.4*Ease.hash(i*11+4))*grow+.05);alphas.add(a);
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
        double top=plume(t)*H;float in=Ease.smooth(t,.25f,.5f);
        List<Vec3d> roots=new ArrayList<>();List<double[]> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
        int n=dev.sangwoo.celestialfall.CelestialFall.CONFIG.visualQuality==0?20:34;
        for(int i=0;i<n;i++){
            double fw=(-1.4+6.1*Ease.hash(i*7+1))*H,rt=(-2.5+6*Ease.hash(i*13+2))*H;
            double cycle=(t*(.8+.5*Ease.hash(i*3))+Ease.hash(i*5))%1,flare=.55+.45*Math.sin(Math.PI*cycle);
            roots.add(c.origin.add(c.f.multiply(fw)).add(c.right.multiply(rt)).add(0,.02,0));
            sizes.add(new double[]{1.3+.9*Ease.hash(i*17),Math.max(.2,top*flare*(.6+.4*Ease.hash(i*19)))});
            alphas.add(in*(float)(.85+.15*flare));
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
        KagenoFire.pool(c.matrix,centre,c.f.multiply(2.9*H),c.right.multiply(3.1*H),c.age,a);
    }
}
