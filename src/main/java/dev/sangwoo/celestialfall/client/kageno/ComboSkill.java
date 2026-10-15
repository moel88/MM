package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * M1 "검술의 대가": four hits, each a separate cast (combo 1–4). Seconds after the click; the
 * blade connects 0.30 s in (6 ticks). Sizes from the review: 1 stud = 0.36 blocks.
 */
final class ComboSkill implements Fx.Skill {
    static final double STUD=.36;
    static final float HIT=.30f;
    // H1 descending forehand diagonal (right-high → left-low).
    static final R6.Pose H1_END=pose().body(15,10,0).head(0,8,0).rArm(-70,-25).lArm(-50,-7).blade(-50,-40);
    static final Anim H1=Anim.of(
        k(0,KagenoClips.IDLE),
        k(.064,pose().body(5,0,0).rArm(-30,-21).lArm(-45,-5).blade(-50,-40)),
        k(.134,pose().body(15,0,0).rArm(3,-49).lArm(0,0).blade(15,-118),Anim.Ease.OUT),
        k(.251,pose().body(15,0,0).rArm(3,-49).lArm(0,0).blade(15,-118),Anim.Ease.HOLD),
        k(.267,pose().rArm(49,58).lArm(0,0).blade(59,-101),Anim.Ease.LINEAR),
        k(.284,pose().body(0,5,0).rArm(-34,17).lArm(-35,0).blade(59,61),Anim.Ease.LINEAR),
        k(.30,pose().body(15,10,0).head(0,8,0).rArm(-37,-30).lArm(-37,-7).blade(-38,-43),Anim.Ease.LINEAR),
        k(.514,pose().body(15,10,0).head(0,8,0).rArm(-37,-30).lArm(-37,-7).blade(-38,-43),Anim.Ease.HOLD),
        k(.57,H1_END),
        k(.95,KagenoClips.IDLE));
    // H2 descending backhand diagonal (left-high → right) with a right-leaning lunge.
    static final R6.Pose H2_END=pose().body(-15,0,20).rArm(3,72).lArm(0,0).lLeg(-58,-101).blade(2,75);
    static final Anim H2=Anim.of(
        k(0,H1_END),
        k(.07,pose().rArm(-18,-32).lArm(-30,0).blade(80,-90)),
        k(.08,pose().rArm(6,-49).lArm(18,-13).blade(44,-155),Anim.Ease.OUT),
        k(.22,pose().rArm(6,-49).lArm(18,-13).blade(44,-155),Anim.Ease.HOLD),
        k(.234,pose().body(0,0,5).rArm(6,37).lArm(0,0).blade(35,-45),Anim.Ease.LINEAR),
        k(.26,H2_END,Anim.Ease.LINEAR),
        k(.48,H2_END,Anim.Ease.HOLD),
        k(.95,KagenoClips.IDLE));
    // H3: a full 360° turn to the right (clockwise from above) ending in the horizontal ring.
    static final R6.Pose H3_END=pose().body(-360,0,25).at(0,-.08,0).rArm(-30,50).lArm(0,0).rLeg(-80,25).lLeg(-80,-25).blade(-40,40);
    static final Anim H3=Anim.of(
        k(0,H2_END),
        k(.02,pose().rArm(-60,20).lArm(-80,-10).blade(-55,30)),
        k(.08,pose().body(-75,0,0).at(0,-.05,0).rArm(0,162).lArm(-11,0).blade(0,170),Anim.Ease.LINEAR),
        k(.15,pose().body(-160,0,0).rArm(0,90).lArm(0,-80).blade(0,90),Anim.Ease.LINEAR),
        k(.235,pose().body(-250,0,0).rArm(0,120).lArm(0,-80).blade(0,130),Anim.Ease.LINEAR),
        k(.30,pose().body(-360,0,0).rArm(0,60).lArm(0,-90).rLeg(-75,30).lLeg(-75,-30).blade(30,60),Anim.Ease.LINEAR),
        k(.36,H3_END),
        k(.58,H3_END,Anim.Ease.HOLD),
        k(.95,pose().body(-360,0,0)));
    // H4: lean and draw back, then a forward thrust.
    static final R6.Pose THRUST=pose().body(-60,5,0).rArm(0,0).lArm(0,10).rLeg(-75,20).lLeg(-60,175).blade(0,0);
    static final Anim H4=Anim.of(
        k(0,H3_END.body(0,0,25)),
        k(.25,pose().body(-60,27,0).head(0,5,0).rArm(6,162).lArm(-45,0).rLeg(-75,20).lLeg(-55,180).blade(0,180),Anim.Ease.OUT),
        k(.275,THRUST,Anim.Ease.LINEAR),
        k(.30,THRUST.lArm(0,-108),Anim.Ease.OUT),
        k(.70,THRUST.lArm(0,-108),Anim.Ease.HOLD),
        k(1.17,KagenoClips.IDLE));

    @Override public Anim clip(int combo){return switch(combo){case 2->H2;case 3->H3;case 4->H4;default->H1;};}
    @Override public float lifetime(){return 1.25f;}

    /** Plane through the torso centre with body-space normal (R,U,F); returns {front-in-plane, right-in-plane}. */
    static Vec3d[] plane(Fx c,double nr,double nu,double nf){
        Vec3d n=c.vec(nf,nu,nr).normalize();
        Vec3d u=c.f.subtract(n.multiply(c.f.dotProduct(n))).normalize();
        Vec3d v=c.right.subtract(n.multiply(c.right.dotProduct(n))).subtract(u.multiply(c.right.dotProduct(u))).normalize();
        return new Vec3d[]{u,v};
    }
    static Vec3d torso(Fx c){return c.origin.add(0,3*STUD,0);}
    /** H4 thrust axis: 60° to the caster's right of the cast facing (the body turns into the thrust). */
    static Vec3d thrustAxis(Fx c){return c.f.multiply(Math.cos(Math.toRadians(60))).add(c.right.multiply(Math.sin(Math.toRadians(60))));}

    @Override public void crescents(Fx c){
        float t=c.sec-HIT;if(t<0)return;int combo=c.cast.combo();
        if(combo==1&&t<.27f){
            // Tilted 24°, highest at the right-rear; 160° (behind the right shoulder) → right → front → left.
            var b=plane(c,-.35,.92,.19);
            float erase=t<.1f?0:(float)Math.pow((t-.1f)/.17f,1.6);
            KagenoFire.crescent(torso(c),b[0],b[1],7.5*STUD,4.1*STUD,160,-250,0,3,erase,0,1,.35);
        }
        if(combo==2&&t<.22f){
            // Tilted 17°, highest at the left-front; left → front → right → right-rear.
            var b=plane(c,.22,.96,-.19);
            float erase=(float)Math.pow(t/.216f,.65);
            KagenoFire.crescent(torso(c),b[0],b[1],6.3*STUD,3.2*STUD,-80,240,1,7,erase,1,1,.55);
        }
        if(combo==3&&t<.15f){
            // Horizontal ring at the end of the spin: forms in ~2 frames, holds, then thins out.
            float sweep=Math.min(1,.45f+t/.033f*.55f),fade=1-Ease.smooth(t,.07f,.14f);
            KagenoFire.crescent(c.origin.add(0,3*STUD,0),c.f,c.right,13*STUD,6.2*STUD,0,360*sweep,2,11,Ease.smooth(t,.08f,.15f)*.6f,.5f,fade);
        }
        if(combo==4&&t<.38f){
            // White-lavender brush arcs circling the thrust axis, behind and around the caster.
            Vec3d axis=c.origin.add(0,1.05,0),fw=thrustAxis(c),side=new Vec3d(-fw.z,0,fw.x);
            for(int i=0;i<5;i++){
                float born=i==0?0:.017f+.011f*i;if(t<born)continue;float u=t-born;
                double r=(.5+.22*i)*1.8*(1+.4*Ease.out(u,0,.1f)),start=Ease.hash(i*7+3)*360,len=90+60*Ease.hash(i*11+5);
                Vec3d centre=axis.add(fw.multiply(-.3+.2*i));
                KagenoFire.crescent(centre,new Vec3d(0,1,0),side,r,.28+.08*i,start,len,4,i,0,.5f,1-Ease.smooth(u,.12f,.38f-born));
            }
        }
    }
    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec-HIT;if(t<0)return;int combo=c.cast.combo();
        if((combo==1||combo==2)&&t>=.014f&&t<.35f){
            // Thin white wind arcs in the slash plane, expanding 30 % while fading.
            var b=combo==1?plane(c,-.35,.92,.19):plane(c,.22,.96,-.19);double outer=(combo==1?7.5:6.3)*STUD;
            float grow=Ease.out(t,.014f,.35f),alpha=.65f*(1-Ease.smooth(t,.12f,.35f));
            for(int i=0;i<3;i++){
                double r=outer*(1.0+.12*i)*(1+.3*grow),a0=(combo==1?120:-60)+i*25,len=(combo==1?-140:140);
                arc(m,torso(c),b[0],b[1],r,a0,len,.018,0xffffff,alpha*(1-.25f*i));
            }
        }
        if(combo==3){
            if(t<.14f)arc(m,c.origin.add(0,3*STUD,0),c.f,c.right,6.6*STUD,0,360,.03,0xddd8f4,.85f*(1-Ease.smooth(t,.07f,.14f)));
            if(t>=.08f&&t<.22f){
                float u=Ease.out(t,.08f,.22f),a=.7f*(1-Ease.smooth(t,.14f,.22f));
                for(int i=0;i<3;i++)arc(m,c.origin.add(0,.5+.6*i,0),c.f,c.right,(1.5+.6*i)*1.8*(1+.5*u),i*70,250,.025,i%2==0?0xffffff:0xc8b8ff,a);
                if(t<.15f){Vec3d n=c.vec(0,.9,.42).normalize();var b=new Vec3d[]{c.f.subtract(n.multiply(c.f.dotProduct(n))).normalize(),c.right};arc(m,c.origin.add(0,1.2,0),b[0],b[1],4.6,-40,110,.03,0xffffff,.8f*(1-Ease.smooth(t,.1f,.15f)));}
            }
        }
        if(combo==4)thrust(c,m,t);
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec-HIT;if(c.cast.combo()!=3||t<.1f||t>=.62f)return;
        // Wide translucent lavender wind bands sweeping around (and through) the camera, drifting up and out.
        int[] colors={0xd4c2fb,0xaea2f8,0x8b84e8,0x6c6edc,0x454aaf,0xaea2f8,0x8b84e8};
        float life=Ease.unit(t,.1f,.62f),a=.6f*Ease.smooth(t,.1f,.16f)*(1-Ease.smooth(t,.4f,.62f));
        for(int i=0;i<7;i++){
            double r=3+1.6*i+4*life,y=.3+.55*i+1.8*life,start=i*67+60*life,len=150+30*i;
            band(m,c.origin.add(0,y,0),r,.6+.25*i,start,len,colors[i],a*(1-.08f*i));
        }
    }
    @Override public void shaders(Fx c){
        float t=c.sec-HIT;if(t<0)return;int combo=c.cast.combo();
        List<Vec3d> pts=new ArrayList<>();List<Double> sizes=new ArrayList<>();float alpha=0;
        if(combo==3&&t>=.08f&&t<.3f){
            alpha=Ease.smooth(t,.08f,.18f)*(1-Ease.smooth(t,.2f,.3f));
            for(int s=-1;s<=1;s+=2){pts.add(c.origin.add(c.right.multiply(s*13*STUD)).add(0,.35,0));sizes.add(.4);}
        }
        if(combo==4&&t>=.1f&&t<.78f){
            // Ground-hugging dark indigo smoke at the impact point, peaking 0.48 s after the thrust.
            alpha=Ease.smooth(t,.1f,.3f)*(1-Ease.smooth(t,.55f,.78f));float grow=Ease.out(t,.1f,.48f);
            Vec3d fw=thrustAxis(c),side=new Vec3d(-fw.z,0,fw.x),at=c.origin.add(fw.multiply(2.0));
            for(int i=0;i<7;i++){double a=i*2.4,r=.25+.55*grow*Ease.hash(i*3+1);pts.add(at.add(side.multiply(Math.cos(a)*r)).add(fw.multiply(Math.sin(a)*r*.6)).add(0,.3+.45*Ease.hash(i*5+2),0));sizes.add(.45+.75*grow*(.6+.4*Ease.hash(i*7)));}
        }
        if(!pts.isEmpty())KagenoFire.smoke(c.matrix,c.camera,pts,sizes,c.age,alpha);
    }
    /** H4 needle cone converging 2 character heights ahead, apex flash, and the smoke's violet lightning. */
    static void thrust(Fx c,EffectMesh m,float t){
        Vec3d fw=thrustAxis(c),side=new Vec3d(-fw.z,0,fw.x);
        Vec3d apex=c.origin.add(fw.multiply(3.6)).add(0,.62,0);
        if(t<.36f){
            float a=Ease.smooth(t,0,.017f)*(1-Ease.smooth(t,.23f,.36f));
            for(int i=0;i<13;i++){
                Vec3d base=c.origin.add(fw.multiply(.4)).add(side.multiply((Ease.hash(i*3)-.5)*1.3)).add(0,.7+Ease.hash(i*5)*1.0,0);
                Vec3d dir=apex.subtract(base);double len=(.4+1.2*Ease.hash(i*7+1))*1.8,from=Math.max(0,dir.length()-len-.2*Ease.hash(i*9));
                Vec3d p0=base.add(dir.normalize().multiply(from)),p1=base.add(dir.normalize().multiply(Math.min(dir.length(),from+len)));
                m.needle(p0,p1,.16,.55,0x4646b2,a*.55f);
                m.needle(p0,p1,.06,.6,i%4==0?0x9369e6:0xf9effb,a);
            }
            m.softDisc(apex,.55,0xffffff,a*(1-Ease.smooth(t,.05f,.16f)));
        }
        if(t>=.1f&&t<.72f){
            // Zig-zag lightning inside the smoke, re-randomised every frame or two.
            long step=(long)Math.floor(c.sec*40);float a=Ease.smooth(t,.1f,.18f)*(1-Ease.smooth(t,.55f,.72f));
            Vec3d at=c.origin.add(fw.multiply(2.0));
            for(int k=0;k<4;k++){
                Vec3d p=at.add(side.multiply((Ease.hash((int)step*13+k)-.5)*1.6)).add(0,.1,0);
                for(int i=0;i<5;i++){
                    Vec3d q=p.add(side.multiply((Ease.hash((int)step*7+k*31+i)-.5)*.5)).add(fw.multiply((Ease.hash((int)step*11+k*17+i)-.5)*.4)).add(0,.18+.12*Ease.hash(i+k*5+(int)step),0);
                    m.glowLine(p,q,.03,0x8040e8,a);p=q;
                }
            }
            for(int k=0;k<8;k++){double life=(t*1.6+k*.137)%1;Vec3d d=at.add(side.multiply((Ease.hash(k*3)-.5)*2*(.4+life))).add(0,.2+life*1.2,0);m.star(d,.05,0xc080ff,a*(float)(1-life));}
        }
    }
    static void arc(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double r,double startDeg,double lenDeg,double width,int rgb,float alpha){
        if(alpha<=0)return;int n=Math.max(8,(int)(Math.abs(lenDeg)/6));Vec3d prev=null;
        for(int i=0;i<=n;i++){double a=Math.toRadians(startDeg+lenDeg*i/n);Vec3d p=o.add(u.multiply(Math.cos(a)*r)).add(v.multiply(Math.sin(a)*r));
            if(prev!=null)m.line(prev,p,width*(1-.6*Math.abs(2.0*i/n-1)),rgb,alpha);prev=p;}
    }
    /** Horizontal ribbon of height {@code h} around {@code centre}, soft at its top and bottom edges. */
    static void band(EffectMesh m,Vec3d centre,double r,double h,double startDeg,double lenDeg,int rgb,float alpha){
        if(alpha<=0)return;int n=Math.max(12,(int)(lenDeg/5));
        for(int i=0;i<n;i++){
            double a0=Math.toRadians(startDeg+lenDeg*i/n),a1=Math.toRadians(startDeg+lenDeg*(i+1)/n);
            float e0=(float)Math.sin(Math.PI*i/n),e1=(float)Math.sin(Math.PI*(i+1)/n);
            Vec3d p0=centre.add(Math.cos(a0)*r,0,Math.sin(a0)*r),p1=centre.add(Math.cos(a1)*r,0,Math.sin(a1)*r);
            m.vertex(p0.add(0,-h*.5,0),rgb,0);m.vertex(p1.add(0,-h*.5,0),rgb,0);m.vertex(p1,rgb,alpha*e1);m.vertex(p0,rgb,alpha*e0);
            m.vertex(p0,rgb,alpha*e0);m.vertex(p1,rgb,alpha*e1);m.vertex(p1.add(0,h*.5,0),rgb,0);m.vertex(p0.add(0,h*.5,0),rgb,0);
        }
    }
}
