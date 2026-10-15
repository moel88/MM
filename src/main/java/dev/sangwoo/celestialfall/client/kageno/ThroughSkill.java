package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;
import static dev.sangwoo.celestialfall.client.kageno.SparkSkill.B;
import static dev.sangwoo.celestialfall.client.kageno.SparkSkill.H;

/**
 * T held "어둠을 가로지르다": the same windup and blast as T but the fire runs as a long strip
 * forward; 1.09 s after the blast a flat-out "comet" dash along the facing, a cage of vertical
 * crescents, then a horizontal ring slash. Seconds after the cast.
 */
final class ThroughSkill implements Fx.Skill {
    /** Dash start (D), end of the glide, dome window and the final cut. */
    static final float D=B+1.09f,GLIDE=D+.25f,DOME=D+.35f,CUT=D+.8f;
    static final R6.Pose GUARD_LOW=pose().body(0,18,0).at(0,-.18,0).rArm(-10,30).lArm(-80,-15).rLeg(-65,90).lLeg(-65,-90).blade(30,-20);
    static final R6.Pose LUNGE=pose().body(0,45,0).at(0,-.54,0).rArm(10,0).lArm(0,180).rLeg(-60,0).lLeg(-30,180).blade(20,0);
    static final R6.Pose COMET=pose().body(0,80,0).at(0,-.68,0).rArm(80,0).lArm(60,-20).blade(80,0);
    static final R6.Pose CROUCH=pose().body(0,30,0).at(0,-.35,0).rArm(0,90).lArm(-60,-40).rLeg(-65,60).lLeg(-65,-60).blade(0,100);
    static final R6.Pose FINISH=pose().rArm(-20,20).lArm(-75,-20).rLeg(-60,90).lLeg(-60,-90).blade(88,0);
    static final Anim CLIP=Anim.of(
        k(0,KagenoClips.IDLE),
        k(.04,pose().body(-15,0,0).lArm(-45,30).rArm(-80,20).blade(-55,40)),
        k(.11,pose().body(-55,0,0).lArm(0,-55).rArm(-85,150).lLeg(-75,0).rLeg(-75,180).blade(-60,0),Anim.Ease.LINEAR),
        k(.20,SparkSkill.PLANT,Anim.Ease.OUT),
        k(B,SparkSkill.PLANT.body(-55,35,0),Anim.Ease.HOLD),
        k(B+.07,pose().rArm(-60,20).blade(-40,40),Anim.Ease.LINEAR),
        k(B+.2,pose().rArm(60,60).blade(60,170)),
        k(B+.32,pose().rArm(0,0).blade(0,0),Anim.Ease.OUT),
        k(B+.5,GUARD_LOW),
        k(D-.06,GUARD_LOW,Anim.Ease.HOLD),
        k(D,LUNGE,Anim.Ease.OUT),
        k(D+.03,COMET,Anim.Ease.LINEAR),
        k(GLIDE,COMET,Anim.Ease.HOLD),
        k(DOME,CROUCH),
        k(CUT-.05,CROUCH.body(-150,30,0)),
        k(CUT+.05,CROUCH.body(-360,20,0),Anim.Ease.LINEAR),
        k(D+1.0,FINISH.body(-360,0,0)),
        k(D+1.4,FINISH.body(-360,0,0),Anim.Ease.HOLD),
        k(B+2.85f,KagenoClips.IDLE.body(-360,0,0)));
    @Override public Anim clip(int combo){return CLIP;}
    @Override public float lifetime(){return D+1.6f;}

    @Override public void additive(Fx c,EffectMesh m){
        SparkSkill.sparkles(c,m);
        SparkSkill.orbArcs(c,m,B,D+.6f,D+.75f);
        float t=c.sec;Vec3d pos=c.pos;
        // Comets shooting ahead along the dash line just before the dash.
        if(t>=B+.8f&&t<D){
            long burst=(long)Math.floor((t-B-.8f)/.07f);float u=((t-B-.8f)/.07f)%1;
            for(int k=0;k<3;k++){
                double s=burst*7.3+k*2.1,len=(1+2*Ease.hash((int)(s*10)))*H,y=.9+.9*Ease.hash((int)(s*10)+1),side=(Ease.hash((int)(s*10)+2)-.5)*1.4;
                Vec3d head=pos.add(c.f.multiply(1+u*6)).add(c.right.multiply(side)).add(0,y,0);
                m.needle(head.subtract(c.f.multiply(len)),head,.18+.18*Ease.hash((int)(s*10)+3),.85,0xebe1f8,(1-u)*.9f);
                m.softDisc(head,.18,0xffffff,(1-u));
            }
        }
        // Launch flash and the needle pointing down the dash line.
        if(t>=D&&t<D+.06f){float a=1-Ease.unit(t,D,D+.06f);m.softDisc(c.origin.add(0,1,0),1.25*H,0xffffff,a);m.needle(c.origin.add(0,1,0),c.origin.add(c.f.multiply(6)).add(0,1,0),.25,.15,0xffffff,a);}
        // Thin dash beam at chest height along the whole dash line, crackling purple at the end.
        if(t>=D&&t<D+.25f){
            float a=1-Ease.smooth(t,D+.18f,D+.25f);Vec3d from=c.origin.add(0,1,0),to=c.origin.add(c.f.multiply(32)).add(0,1,0);
            m.glowLine(from,to,.08,t<D+.13f?0xf8eefa:0xa855f8,a);
            if(t>=D+.13f){long step=(long)Math.floor(t*40);
                for(int k=0;k<3;k++){Vec3d p=pos.add(0,.6,0);for(int i=0;i<5;i++){Vec3d q=p.add((Ease.hash((int)step*7+k*31+i)-.5)*.7,(Ease.hash((int)step*11+k*17+i)-.2)*.5,(Ease.hash((int)step*5+k*13+i)-.5)*.7);m.glowLine(p,q,.025,0x8a3ce8,a);p=q;}}}
        }
        // White tail just behind the gliding body.
        if(t>=D&&t<D+.45f){float a=1-Ease.smooth(t,D+.25f,D+.45f);Vec3d tail=pos.add(0,.5,0);m.needle(tail.subtract(c.f.multiply(1.8*H)),tail,1*H,.85,0xffffff,a*.85f);m.needle(tail.subtract(c.f.multiply(1.4*H)),tail,.5*H,.8,0xe58cf5,a*.6f);}
        // Purple sparks around the body from the end of the dash through the dome.
        if(t>=D+.3f&&t<CUT+.2f)for(int k=0;k<16;k++){double life=((t-D)*2.6+k*.137)%1;Vec3d p=pos.add((Ease.hash(k*3)-.5)*3,.3+Ease.hash(k*5)*2.2+life*.4,(Ease.hash(k*7)-.5)*3);m.star(p,.06+.08*Ease.hash(k*9),0xd651f6,(float)Math.sin(Math.PI*life));}
        // Final cut: white shatter spikes outward and the waist-high slash line through the body.
        if(t>=CUT&&t<CUT+.18f){
            float a=1-Ease.smooth(t,CUT+.08f,CUT+.18f);
            for(int k=0;k<14;k++){double az=k*Math.PI*2/14+Ease.hash(k)*.3,r0=.6+.4*Ease.hash(k*3),len=1+1.6*Ease.hash(k*5);
                Vec3d d=new Vec3d(Math.cos(az),0,Math.sin(az));m.needle(pos.add(d.multiply(r0)).add(0,.8,0),pos.add(d.multiply(r0+len)).add(0,.65+.3*Ease.hash(k*7),0),.12,.3,0xffffff,a);}
            if(t<CUT+.12f)m.glowLine(pos.add(c.right.multiply(-3.5)).add(0,1,0),pos.add(c.right.multiply(3.5)).add(0,1,0),.07,0xebe1f8,a);
        }
    }
    @Override public void crescents(Fx c){
        float t=c.sec;Vec3d pos=c.pos;
        // Cage of vertical crescents (meridians) flickering all around the body.
        if(t>=DOME&&t<CUT){
            for(int k=0;k<15;k++){
                float born=DOME+(float)Ease.hash(k*11+1)*(CUT-DOME-.1f),life=.1f+.1f*(float)Ease.hash(k*13+2),u=(t-born)/life;
                if(u<0||u>=1)continue;
                double az=Ease.hash(k*17+3)*Math.PI*2+(t-DOME)*2.5,len=100+60*Ease.hash(k*19+4);
                Vec3d r=new Vec3d(Math.cos(az),0,Math.sin(az));
                boolean dark=k%5==2;
                KagenoFire.crescent(pos,r,new Vec3d(0,1,0),2.2*H,(.1+.15*Ease.hash(k*23))*H,Ease.hash(k*29)*20,len,dark?2:5,k,Ease.smooth(u,.5f,1),.5f,dark?.7f:.55f*(float)Math.sin(Math.PI*u));
            }
        }
        // Horizontal ring slash expanding past 5 H in 0.05 s, lilac-pink top and an indigo rim.
        if(t>=CUT&&t<CUT+.28f){
            float u=t-CUT,grow=Ease.out(u,0,.05f);double r=(1.5+4*grow)*H;
            // Slightly tilted: the far side a little higher than the near side.
            Vec3d front=c.f.add(0,Math.sin(Math.toRadians(5)),0).normalize();
            KagenoFire.crescent(pos.add(0,.7,0),front,c.right,r,r*.55,0,360,6,3,Ease.smooth(u,.12f,.28f)*.7f,.5f,1-Ease.smooth(u,.16f,.28f));
        }
    }
    @Override public void shaders(Fx c){
        float t=c.sec-B;if(t<0)return;
        // The blast cloud reaches down the whole line in 0.1 s; the fire runs as a long forward strip.
        if(t<.45f){
            float grow=Ease.out(t,0,.1f),a=1-Ease.smooth(t,.25f,.42f);
            List<Vec3d> pts=new ArrayList<>();List<Double> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
            for(int i=0;i<20;i++){double along=(.5+14.5*i/19.0)*H;if(along>grow*15*H)continue;
                pts.add(c.origin.add(c.f.multiply(along)).add(c.right.multiply((Ease.hash(i*3)-.2)*2.5*H)).add(0,.8+Ease.hash(i*5)*2.4,0));sizes.add(1.4+1.4*Ease.hash(i*7));alphas.add(a);}
            KagenoFire.billboards(c.matrix,c.camera,9,pts,sizes,alphas,c.age);
        }
        if(t>=.2f&&t<D-B+.3f){
            float in=Ease.smooth(t,.2f,.4f),out=1-Ease.smooth(t,D-B-.1f,D-B+.3f);
            List<Vec3d> roots=new ArrayList<>();List<double[]> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
            for(int i=0;i<18;i++){
                double along=(2+13*Ease.hash(i*7+1))*H,side=(1+3*Ease.hash(i*13+2))*H*(Ease.hash(i*3)<.25?-.4:1);
                double cycle=(t*(.9+.5*Ease.hash(i*3))+Ease.hash(i*5))%1,flare=.55+.45*Math.sin(Math.PI*cycle);
                roots.add(c.origin.add(c.f.multiply(along)).add(c.right.multiply(side)).add(0,.02,0));
                sizes.add(new double[]{1.0+.7*Ease.hash(i*17),3.6*H*flare*(.6+.4*Ease.hash(i*19))});alphas.add(in*out*(float)(.75+.25*flare));
            }
            KagenoFire.tongues(c.matrix,c.camera,5,roots,sizes,alphas,c.age);
        }
        SparkSkill.orbs(c,t,D-B+.6f,D-B+.75f);
        // Dense white cloud left at the dash start.
        if(c.sec>=D&&c.sec<D+.45f){
            float a=1-Ease.smooth(c.sec,D+.05f,D+.45f);
            List<Vec3d> pts=new ArrayList<>();List<Double> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
            for(int i=0;i<6;i++){pts.add(c.origin.add(c.right.multiply((Ease.hash(i*3)-.5)*1.6)).add(0,.6+Ease.hash(i*5)*2.4,0));sizes.add(1.0+.8*Ease.hash(i*7));alphas.add(a);}
            KagenoFire.billboards(c.matrix,c.camera,9,pts,sizes,alphas,c.age);
        }
        // Small white-pink flame wisps left around the finish.
        if(c.sec>=CUT+.1f&&c.sec<CUT+1.0f){
            float a=Ease.smooth(c.sec,CUT+.1f,CUT+.2f)*(1-Ease.smooth(c.sec,CUT+.6f,CUT+1.0f));
            List<Vec3d> roots=new ArrayList<>();List<double[]> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
            for(int i=0;i<12;i++){double az=Ease.hash(i*3)*Math.PI*2,r=(1.5+1.5*Ease.hash(i*5))*H;roots.add(c.pos.add(Math.cos(az)*r,.02,Math.sin(az)*r));sizes.add(new double[]{.35,(.3+.5*Ease.hash(i*7))*H});alphas.add(a);}
            KagenoFire.tongues(c.matrix,c.camera,0,roots,sizes,alphas,c.age);
        }
    }
    @Override public float flash(Fx c){
        // The cut ring's edge sweeping over the camera tints the view violet-magenta for ≈0.15 s.
        float t=c.sec;if(t<D+1.16f||t>=D+1.32f)return 0;
        double d=c.camera.distanceTo(c.pos);if(d>12)return 0;
        return .5f*Ease.smooth(t,D+1.16f,D+1.2f)*(1-Ease.smooth(t,D+1.24f,D+1.32f));
    }
    @Override public int flashColor(Fx c){return 0x9a58f4;}
}
