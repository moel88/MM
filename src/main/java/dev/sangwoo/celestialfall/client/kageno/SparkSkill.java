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
 * T "어둠 속의 불꽃", rebuilt from the user's recording: the sword lifts, sweeps down to the back right in a blink and
 * stays low while the left hand goes up beside the face in a burst of four-point sparkles; 0.65 s after the cast
 * black smoke erupts over the field, blue and violet billows break out of it within a tenth of a second and it
 * turns into a dense magenta fire full of big four-point star flares and small black bolts. After ≈3.5 s the fire
 * breaks up into separate pink wisps that die out. Targets in the field are held in dark orbs. Seconds after the
 * cast; B = 0.65 s is the blast (the server's {@code T_STRIKE}). 1 character height (H) = 1.8 blocks.
 */
final class SparkSkill implements Fx.Skill {
    static final float B=.65f;
    static final double H=1.8;
    /** Dense fire until FIRE_END after the blast, wisps until WISPS_END (the server holds targets for 3.55 s). */
    static final float FIRE_END=3.55f,WISPS_END=4.15f;
    /** The sword up and back for the sweep. */
    static final R6.Pose LIFT=pose().body(12,-5,0).at(0,.03,0).head(-6,-4,0).lArm(-72,-28).rArm(-15,62).rLeg(-88,10).lLeg(-89,-6).blade(58,78);
    /** Mid-sweep: the blade cutting down past the right hip. */
    static final R6.Pose SWEEP=pose().body(-32,12,0).at(0,-.05,0).head(10,6,0).lArm(-38,-42).rArm(-62,104).rLeg(-80,128).lLeg(-82,-12).blade(-36,118);
    /** Held through the sparkles: turned right, the sword low behind the right side, the left hand up beside the face. */
    static final R6.Pose PLANT=pose().body(-55,24,0).at(0,-.09,0).head(0,14,0).lArm(42,-34).rArm(-74,96).lLeg(-76,-4).rLeg(-76,172).blade(-32,108);
    static final Anim CLIP=Anim.joints(
        k(0,KagenoClips.IDLE),
        // Anticipation: the sword comes up and the weight goes onto the left foot.
        k(.045,LIFT),
        k(.085,SWEEP,Anim.Ease.LINEAR),
        // The sweep runs out into the plant; the left hand is still rising.
        k(.14,PLANT.lArm(28,-40).body(-52,26,0),Anim.Ease.OUT),
        k(.24,PLANT),
        // Moving hold: the raised hand keeps reaching while the body sinks a little.
        k(B-.04,PLANT.body(-57,27,0).at(0,-.11,0).lArm(46,-30)),
        // The blast: the hand thrusts out and the body is thrown back for a moment.
        k(B,PLANT.body(-50,18,0).at(0,-.1,0).lArm(30,-12),Anim.Ease.LINEAR),
        k(B+.06,PLANT.body(-38,4,0).at(0,-.04,0).head(0,4,0).lArm(10,-10).blade(-40,95)),
        k(B+.32,KagenoClips.IDLE)).stops(Anim.stop(B,.05,.1));
    @Override public Anim clip(int combo){return CLIP;}
    @Override public float lifetime(){return B+WISPS_END+.1f;}

    /** The raised left hand, beside the face of the turned body. */
    static Vec3d hand(Fx c){return c.origin.add(c.f.multiply(.5)).add(0,1.8,0).add(c.right.multiply(-.15));}
    /** Middle of the field the server burns (forward 3, right 0.9 of the cast spot). */
    static Vec3d centre(Fx c){return c.origin.add(c.f.multiply(3)).add(c.right.multiply(.9));}
    /** Four-point stars bursting from the left hand during the windup: a bright cluster first, then rising and fading. */
    static void sparkles(Fx c,EffectMesh m){
        if(c.sec<.13f||c.sec>=B)return;Vec3d hand=hand(c);
        for(int i=0;i<40;i++){
            float born=.13f+.45f*(i/40f)*(i/40f),life=.12f+.2f*(float)Ease.hash(i*13+1),u=(c.sec-born)/life;
            if(u<0||u>=1)continue;
            double az=Ease.hash(i*7+2)*Math.PI*2,el=(Ease.hash(i*5+3)-.3)*1.6,reach=(.15+1.6*Ease.hash(i*3+4))*(.55+.45*u);
            Vec3d dir=c.f.multiply(Math.cos(el)*Math.cos(az)*.6+.3).add(c.right.multiply(Math.cos(el)*Math.sin(az)-.25)).add(0,Math.sin(el)+.35,0);
            Vec3d p=hand.add(dir.normalize().multiply(reach)).add(0,.5*u*Ease.hash(i*17),0);
            double size=(.16+.2*Ease.hash(i*11+5))*(1+.3*Math.sin(u*Math.PI*3))*(i<12?1.35:1);
            m.star(p,size,0xe2d4ff,(float)Math.sin(Math.PI*Math.min(1,u*1.4)));
        }
    }
    /** A faint glow gathering in the raised hand while the sparkles fly. */
    static void charge(Fx c,EffectMesh m){
        float t=c.sec;if(t<.13f||t>=B+.05f)return;Vec3d hand=hand(c);
        float u=Ease.unit(t,.13f,B),pulse=.85f+.15f*(float)Math.sin(t*40);
        FxLib.converge(m,hand,1.4,18,t,.13f,B,0xc8a8ff,7);
        m.softDisc(hand,.12+.25*u,0xcdb0ff,.35f*u*pulse);m.softDisc(hand,.06+.08*u,0xffffff,.7f*u);
    }

    @Override public void additive(Fx c,EffectMesh m){
        sparkles(c,m);charge(c,m);
        float t=c.sec-B;if(t<0)return;
        float alive=1-Ease.smooth(t,FIRE_END-.25f,FIRE_END+.1f);
        Vec3d mid=centre(c),ground=new Vec3d(mid.x,c.origin.y+.04,mid.z);
        // The road under the fire lit violet, embers drifting up out of it.
        if(t<FIRE_END+.3f){
            float a=Ease.smooth(t,.08f,.3f)*(1-Ease.smooth(t,FIRE_END-.3f,FIRE_END+.3f));
            FxLib.glow(m,ground,6,0x7c2cf0,.32f*a*(float)(.85+.15*Math.sin(t*5)));
            FxLib.embers(m,ground,4.5,4.6,dev.sangwoo.celestialfall.CelestialFall.CONFIG.visualQuality==0?14:30,t,.9f,.06,0xff6cf5,a,5);
        }
        flares(c,m,t,mid,alive);
        orbArcs(c,m,B,B+FIRE_END,B+FIRE_END+.25f);
    }
    /** Big four-point star flares flashing all over the fire (the first, the biggest, right at the blast), small stars twinkling between them. */
    static void flares(Fx c,EffectMesh m,float t,Vec3d mid,float alive){
        if(t>=FIRE_END+.1f)return;
        for(int k=0;k<44;k++){
            float born=.1f+k*.078f+.05f*(float)Ease.hash(k*3+1),life=.2f+.16f*(float)Ease.hash(k*5+2),u=(t-born)/life;
            if(u<0||u>=1||born>FIRE_END-.2f)continue;
            double fw=(Ease.hash(k*7+3)-.5)*8,rt=(Ease.hash(k*11+4)-.5)*8,up=.5+3*Ease.hash(k*13+5);
            double size=(1.8+2.6*Ease.hash(k*17+6))*(k<2?1.5:1);
            Vec3d p=k<2?mid.add(c.f.multiply(-1.2+k*1.5)).add(0,1.4+k*.9,0):mid.add(c.f.multiply(fw)).add(c.right.multiply(rt)).add(0,up,0);
            float a=Ease.smooth(u,0,.12f)*(1-Ease.smooth(u,.35f,1))*alive;
            FxLib.flare(m,p,size*(.75+.25*u),0xff48dc,a,Ease.hash(k*19+7)<.6?45:0);
        }
        for(int k=0;k<26;k++){
            float cyc=(t*(1.6f+(float)Ease.hash(k*3))+(float)Ease.hash(k*5))%1;
            Vec3d p=mid.add(c.f.multiply((Ease.hash(k*7)-.5)*9)).add(c.right.multiply((Ease.hash(k*11)-.5)*9)).add(0,.4+3.6*Ease.hash(k*13),0);
            m.star(p,.1+.16*Ease.hash(k*17),0xffe4ff,alive*(float)Math.sin(Math.PI*cyc)*Ease.smooth(t,.1f,.3f));
        }
    }
    @Override public void alpha(Fx c,EffectMesh m){bolts(c,m,c.sec-B,centre(c),1-Ease.smooth(c.sec-B,FIRE_END-.3f,FIRE_END));}
    /** Small black zigzag bolts flickering inside the fire, one or two every fifth of a second. */
    static void bolts(Fx c,EffectMesh m,float t,Vec3d mid,float alive){
        if(t<.25f||alive<=0)return;
        int window=(int)Math.floor(t/.2f);
        for(int k=0;k<2;k++){
            int s=window*7+k*3;if(Ease.hash(s)<.35)continue;
            float born=window*.2f+.1f*(float)Ease.hash(s+1);if(t<born||t>=born+.09f)continue;
            Vec3d a=mid.add(c.f.multiply((Ease.hash(s+2)-.5)*7)).add(c.right.multiply((Ease.hash(s+3)-.5)*7)).add(0,.8+2.6*Ease.hash(s+4),0);
            double len=.7+.7*Ease.hash(s+5),az=Ease.hash(s+6)*Math.PI*2;
            Vec3d b=a.add(Math.cos(az)*len*.6,(Ease.hash(s+7)-.5)*len,Math.sin(az)*len*.6);
            Vec3d prev=a;
            for(int i=1;i<=5;i++){
                Vec3d p=a.lerp(b,i/5.0);if(i<5)p=p.add((Ease.hash(s*11+i)-.5)*.3,(Ease.hash(s*13+i)-.5)*.3,(Ease.hash(s*17+i)-.5)*.3);
                m.line(prev,p,.11,0x1a0828,.35f*alive);m.line(prev,p,.045,0x07020c,.95f*alive);prev=p;
            }
        }
    }

    @Override public void shaders(Fx c){
        float t=c.sec-B;if(t<0)return;
        pool(c,t);
        List<Vec3d> pts=new ArrayList<>();List<float[]> cells=new ArrayList<>();
        blast(c,t,centre(c),pts,cells);
        fire(c,t,centre(c),pts,cells);
        wisps(c,t,centre(c),5.5,FIRE_END-.2f,WISPS_END,pts,cells,0);
        KagenoFire.cells(c.matrix,c.camera,pts,cells,c.age);
        orbs(c,t,FIRE_END,FIRE_END+.25f);
    }
    /** Black smoke thrown out over the field at the blast; blue billows break out of it, then violet and magenta, as the fire takes over. */
    static void blast(Fx c,float t,Vec3d mid,List<Vec3d> pts,List<float[]> cells){
        if(t>=.6f)return;
        float spread=Ease.out(t,0,.06f),a=1-Ease.smooth(t,.35f,.6f);
        for(int i=0;i<22;i++){
            double az=Ease.hash(i*3+1)*Math.PI*2,r=(.6+5*Math.sqrt(Ease.hash(i*5+2)))*(.55+.45*spread);
            Vec3d p=mid.add(c.f.multiply(Math.cos(az)*r)).add(c.right.multiply(Math.sin(az)*r)).add(0,.3+2.6*Ease.hash(i*7+3)*(.4+.6*spread),0);
            // Black at first; most cells break out blue, then violet; a few stay black smoke a little longer.
            boolean smoke=i%4==0;
            float heat=smoke?.1f*Ease.smooth(t,.2f,.45f):.3f*Ease.smooth(t,.03f,.12f)+.15f*Ease.smooth(t,.12f,.3f)+.15f*Ease.smooth(t,.3f,.5f);
            pts.add(p);cells.add(new float[]{(float)((1.0+1.0*Ease.hash(i*11+4))*(.55+.45*spread)),a*(smoke?.95f:1),heat,0,i*29+3});
        }
    }
    /** The fire: billows born all over the field, each rising, swelling and cooling from pink-white to violet and blue. */
    static void fire(Fx c,float t,Vec3d mid,List<Vec3d> pts,List<float[]> cells){
        if(t<.08f||t>=FIRE_END+.15f)return;
        float field=Ease.smooth(t,.12f,.45f)*(1-Ease.smooth(t,FIRE_END-.35f,FIRE_END+.15f));
        // The fire catches blue and violet first and burns up to magenta over the first half second.
        float ignite=.4f+.6f*Ease.smooth(t,.3f,.9f);
        // It also starts compact round the body and spreads over the whole burning field in the first second.
        double spread=.65+.35*Ease.smooth(t,.1f,1f);
        double lift=1.25+.45*(1-Ease.smooth(t,.4f,1.4f));
        int n=dev.sangwoo.celestialfall.CelestialFall.CONFIG.visualQuality==0?32:58;
        for(int i=0;i<n;i++){
            float period=.8f+.65f*(float)Ease.hash(i*7+1),cyc=t/period+(float)Ease.hash(i*11+2);int gen=(int)Math.floor(cyc);float u=cyc-gen;
            double az=Ease.hash(i*13+gen*31)*Math.PI*2,edge=Math.sqrt(Ease.hash(i*17+gen*29));
            double fw=Math.cos(az)*edge*4.2*spread,rt=Math.sin(az)*edge*4*spread,rise=(1.2+1.7*Ease.hash(i*19+gen*7))*(1-.45*edge)*lift*(.75+.25*spread);
            Vec3d p=mid.add(c.f.multiply(fw)).add(c.right.multiply(rt)).add(0,.3+u*rise+(1-edge)*.5,0);
            double size=(.8+.85*Ease.hash(i*23+gen*3))*(1.15-.35*edge)*(.55+.45*Math.sin(Math.PI*Math.min(1,u*1.3)))*(.8+.2*spread);
            double kind=Ease.hash(i*43+gen*13);
            // Most billows run violet to magenta; some are dark smoke inside the fire, some white-hot cores, the rim cools to blue.
            float heat=kind<.22?(float)(.08+.16*Ease.hash(i*5+gen)):kind>.88?ignite*(float)(1.02+.12*Ease.hash(i*5+gen)-.3*u):ignite*(float)(.9-.6*u-.3*edge+.22*Ease.hash(i*5+gen));
            float a=(float)Math.pow(Math.sin(Math.PI*u),.5)*field;
            pts.add(p);cells.add(new float[]{(float)size,a,heat,0,i*37+11});
        }
        // Flame tongues licking up out of the top of the fire.
        int tongues=dev.sangwoo.celestialfall.CelestialFall.CONFIG.visualQuality==0?8:16;
        for(int i=0;i<tongues;i++){
            float period=.55f+.4f*(float)Ease.hash(i*7+5),cyc=t/period+(float)Ease.hash(i*11+6);int gen=(int)Math.floor(cyc);float u=cyc-gen;
            double az=Ease.hash(i*13+gen*17+3)*Math.PI*2,edge=Math.sqrt(Ease.hash(i*17+gen*19+4))*.8;
            Vec3d p=mid.add(c.f.multiply(Math.cos(az)*edge*4.2)).add(c.right.multiply(Math.sin(az)*edge*4)).add(0,(1.6+1.4*Ease.hash(i*19+gen*5))*lift*.85+u*1.6,0);
            float a=(float)Math.sin(Math.PI*u)*field;
            pts.add(p);cells.add(new float[]{(float)((.7+.5*Ease.hash(i*23+gen))*(1-.35*u)),a,(float)(.78+.18*Ease.hash(i*29+gen)-.3*u),.75f,i*53+19});
        }
    }
    /** The fire breaking up: separate pink wisps curling up over the field between {@code from} and {@code to}. */
    static void wisps(Fx c,float t,Vec3d mid,double radius,float from,float to,List<Vec3d> pts,List<float[]> cells,int seed){
        if(t<from||t>=to)return;
        for(int i=0;i<24;i++){
            float born=from+(to-from-.5f)*(float)Ease.hash(seed+i*3+1),life=.4f+.25f*(float)Ease.hash(seed+i*5+2),u=(t-born)/life;
            if(u<0||u>=1)continue;
            double az=Ease.hash(seed+i*7+3)*Math.PI*2,r=radius*Math.sqrt(Ease.hash(seed+i*11+4))*(1+.15*u);
            Vec3d p=mid.add(Math.cos(az)*r,.3+.5*Ease.hash(seed+i*13+5)+.5*u,Math.sin(az)*r);
            float a=Ease.smooth(u,0,.2f)*(1-Ease.smooth(u,.55f,1));
            pts.add(p);cells.add(new float[]{(float)((.45+.35*Ease.hash(seed+i*17+6))*(1-.3*u)),a,(float)(.84+.12*Ease.hash(seed+i*19+7)),1,seed+i*41+5});
        }
    }
    /** Dark ground under the fire, gone when the fire dies. */
    static void pool(Fx c,float t){
        if(t>=FIRE_END+.3f)return;float a=Ease.smooth(t,.03f,.25f)*(1-Ease.smooth(t,FIRE_END-.4f,FIRE_END+.3f));
        KagenoFire.pool(c.matrix,centre(c).add(0,.015,0),c.f.multiply(5.2),c.right.multiply(5),c.age,a*.85f);
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
    /** Dark ink orb on each target (≈1.2 H across), forming over +0.15..0.31 s, breaking up at the end. */
    static void orbs(Fx c,float t,float hold,float end){
        if(t<.15f||t>=end||c.targets.isEmpty())return;
        float form=Ease.out(t,.15f,.31f),a=form*(1-Ease.smooth(t,hold,end));double grow=1+.35*Ease.smooth(t,hold,end);
        List<Vec3d> pts=new ArrayList<>();List<Double> sizes=new ArrayList<>();List<Float> alphas=new ArrayList<>();
        for(LivingEntity e:c.targets){pts.add(e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0));sizes.add(.6*H*(.4+.6*form)*grow);alphas.add(a);}
        KagenoFire.billboards(c.matrix,c.camera,10,pts,sizes,alphas,c.age);
    }
}
