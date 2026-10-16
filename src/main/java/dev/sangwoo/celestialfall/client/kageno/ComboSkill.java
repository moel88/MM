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
 * M1 "검술의 대가": four hits, each a separate cast (combo 1–4); the blade connects 0.30 s in
 * (6 ticks). An original four-cut combo: a horizontal sweep from right to left, a rising backhand
 * from low left to high right, an overhead chop and a full spin into a lunging thrust. Every cut
 * draws a sword trail and a brush crescent in its own swing plane, with wind arcs, sparks on the
 * target, dust at the chop and a shockwave on the thrust. Seconds after the click.
 */
final class ComboSkill implements Fx.Skill {
    static final double STUD=.36;
    static final float HIT=.30f;
    static final Vec3d UP=new Vec3d(0,1,0);
    // H1 — horizontal sweep: twist right with the sword drawn back, whip it across the front to the left.
    static final R6.Pose H1_END=pose().body(34,10,0).head(-15,4,0).rArm(-12,-55).lArm(-50,-55).blade(-10,-130).rLeg(-78,22).lLeg(-70,-12);
    static final Anim H1=Anim.joints(
        k(0,KagenoClips.IDLE),
        k(.08,pose().body(-30,4,0).head(20,0,0).rArm(-20,75).lArm(-55,-25).blade(-30,140).rLeg(-82,15).lLeg(-78,-18),Anim.Ease.OUT),
        k(.20,pose().body(-38,6,0).head(25,0,0).rArm(0,95).lArm(-50,-30).blade(0,165).rLeg(-82,15).lLeg(-78,-18),Anim.Ease.HOLD),
        k(.26,pose().body(-5,8,0).head(5,0,0).rArm(5,30).lArm(-50,-45).blade(10,40).rLeg(-80,18).lLeg(-74,-15),Anim.Ease.LINEAR),
        k(.30,pose().body(28,10,0).head(-12,4,0).rArm(0,-40).lArm(-48,-55).blade(0,-120).rLeg(-78,22).lLeg(-70,-12),Anim.Ease.LINEAR),
        k(.42,H1_END),
        k(.60,H1_END,Anim.Ease.HOLD),
        k(.95,KagenoClips.IDLE));
    // H2 — rising backhand: the sword dips low on the left, then climbs through the front to high right.
    static final R6.Pose H2_END=pose().body(-30,-6,0).head(14,-10,0).rArm(66,60).lArm(-55,-25).blade(80,80).rLeg(-82,15).lLeg(-76,-24);
    static final Anim H2=Anim.joints(
        k(0,H1_END),
        k(.10,pose().body(36,16,0).head(-10,10,0).rArm(-55,-60).lArm(-35,-70).blade(-70,-110).rLeg(-78,22).lLeg(-70,-12),Anim.Ease.OUT),
        k(.20,pose().body(36,16,0).head(-10,10,0).rArm(-55,-60).lArm(-35,-70).blade(-70,-110).rLeg(-78,22).lLeg(-70,-12),Anim.Ease.HOLD),
        k(.26,pose().body(8,8,0).head(0,0,0).rArm(5,-5).lArm(-45,-50).blade(25,-20).rLeg(-80,18).lLeg(-74,-18),Anim.Ease.LINEAR),
        k(.30,pose().body(-26,-4,0).head(12,-8,0).rArm(60,55).lArm(-50,-25).blade(75,70).rLeg(-82,15).lLeg(-76,-24),Anim.Ease.LINEAR),
        k(.44,H2_END),
        k(.60,H2_END,Anim.Ease.HOLD),
        k(.95,KagenoClips.IDLE));
    // H3 — overhead chop: both hands up, the blade tilted back, then straight down in front with a forward lean.
    static final R6.Pose H3_END=pose().body(0,28,0).head(0,14,0).at(.1,-.08,0).rArm(-28,0).lArm(-35,-20).blade(-48,0).rLeg(-66,18).lLeg(-82,-14);
    static final Anim H3=Anim.joints(
        k(0,H2_END),
        k(.10,pose().body(-8,-10,0).head(0,-12,0).rArm(88,15).lArm(60,-20).blade(75,170).rLeg(-84,10).lLeg(-80,-12),Anim.Ease.OUT),
        k(.20,pose().body(-8,-10,0).head(0,-12,0).rArm(88,15).lArm(60,-20).blade(75,170).rLeg(-84,10).lLeg(-80,-12),Anim.Ease.HOLD),
        k(.26,pose().body(0,12,0).head(0,0,0).rArm(45,0).lArm(30,-15).blade(55,0).rLeg(-80,14).lLeg(-82,-14),Anim.Ease.LINEAR),
        k(.30,pose().body(0,26,0).head(0,12,0).at(.1,-.06,0).rArm(-20,0).lArm(-30,-20).blade(-40,0).rLeg(-66,18).lLeg(-82,-14),Anim.Ease.LINEAR),
        k(.42,H3_END),
        k(.60,H3_END,Anim.Ease.HOLD),
        k(.95,KagenoClips.IDLE));
    // H4 — spin thrust: the sword swings out to the right, a full turn with it held level, then a lunge straight ahead.
    static final R6.Pose THRUST=pose().body(-360,10,0).head(0,0,0).at(.30,-.12,0).rArm(0,0).lArm(-5,165).blade(0,0).rLeg(-62,10).lLeg(-58,185);
    static final Anim H4=Anim.joints(
        k(0,H3_END),
        k(.08,pose().body(-45,8,0).head(10,0,0).rArm(-10,70).lArm(-55,-30).blade(-5,95).rLeg(-80,20).lLeg(-78,-20),Anim.Ease.OUT),
        k(.15,pose().body(-170,6,0).rArm(0,90).lArm(-30,-90).blade(0,95).rLeg(-80,20).lLeg(-78,-20),Anim.Ease.LINEAR),
        k(.23,pose().body(-310,10,0).rArm(0,85).lArm(-35,-70).blade(0,85).rLeg(-80,20).lLeg(-78,-20),Anim.Ease.LINEAR),
        k(.27,pose().body(-360,14,0).head(0,4,0).rArm(-5,140).lArm(-20,-40).blade(0,150).rLeg(-80,20).lLeg(-78,-20),Anim.Ease.LINEAR),
        k(.30,THRUST,Anim.Ease.LINEAR),
        k(.70,THRUST,Anim.Ease.HOLD),
        k(.85,pose().body(-360,4,0).at(.15,-.05,0).rArm(-30,10).lArm(-60,-20).blade(-20,20).rLeg(-80,12).lLeg(-80,-12)),
        k(1.15,KagenoClips.IDLE.body(-360,0,0)));

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
    /** H4 thrust axis: the cast facing (the player's aim). */
    static Vec3d thrustAxis(Fx c){return c.f;}
    /** Frontal swing plane of the rising backhand: right across, up tilted a little forward. */
    static Vec3d[] frontal(Fx c){return new Vec3d[]{c.right,c.f.multiply(.3).add(UP).normalize()};}
    /** Vertical swing plane of the chop along the facing. */
    static Vec3d[] sagittal(Fx c){return new Vec3d[]{c.f,UP};}

    @Override public void crescents(Fx c){
        float t=c.sec-HIT;if(t<0)return;int combo=c.cast.combo();
        if(combo==1&&t<.28f){
            // Horizontal sweep, the plane tilted so the right-rear end is highest: right-rear → front → left.
            var b=plane(c,-.3,.94,.15);
            float erase=t<.09f?0:(float)Math.pow((t-.09f)/.19f,1.5);
            KagenoFire.crescent(torso(c),b[0],b[1],2.2,.42,110,-230,0,3,erase,0,1,.3);
        }
        if(combo==2&&t<.26f){
            // Rising backhand in the frontal plane: low left, under the body, up to high right.
            var b=frontal(c);
            float erase=t<.07f?0:(float)Math.pow((t-.07f)/.19f,1.4);
            KagenoFire.crescent(torso(c).add(0,.1,0),b[0],b[1],2.1,.4,215,170,1,7,erase,.8f,1,.6);
        }
        if(combo==3&&t<.26f){
            // Overhead chop: a crisp white-lavender vertical crescent from above and behind down to the ground ahead.
            var b=sagittal(c);
            float erase=t<.06f?0:(float)Math.pow((t-.06f)/.2f,1.3);
            KagenoFire.crescent(torso(c),b[0],b[1],2.25,.38,100,-125,4,11,erase,.85f,1,.5);
        }
        if(combo==4){
            // Spin ring at the end of the turn: forms in two frames, holds through the thrust, then thins out.
            float s=t+.03f;
            if(s>=0&&s<.17f){
                float sweep=Math.min(1,.45f+s/.033f*.55f),fade=1-Ease.smooth(s,.09f,.16f);
                KagenoFire.crescent(torso(c),c.f,c.right,2.6,.45,0,360*sweep,2,13,Ease.smooth(s,.1f,.17f)*.6f,.5f,fade);
            }
            // White-lavender arcs circling the thrust axis behind the caster.
            if(t>=0&&t<.38f){
                Vec3d axis=c.origin.add(0,1.05,0),fw=thrustAxis(c),side=new Vec3d(-fw.z,0,fw.x);
                for(int i=0;i<3;i++){
                    float born=i==0?0:.017f+.011f*i;if(t<born)continue;float u=t-born;
                    double r=(.5+.2*i)*1.3*(1+.25*Ease.out(u,0,.1f)),start=Ease.hash(i*7+3)*360,len=90+60*Ease.hash(i*11+5);
                    KagenoFire.crescent(axis.add(fw.multiply(-.3+.2*i)),UP,side,r,.12+.035*i,start,len,4,i,0,.5f,1-Ease.smooth(u,.12f,.38f-born));
                }
            }
        }
    }
    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec-HIT;int combo=c.cast.combo();
        // Sword trail: a white-lavender ribbon swept by the outer blade through the strike.
        float tr=Ease.smooth(c.sec,.18f,.23f)*(1-Ease.smooth(c.sec,.31f,.40f));
        if(tr>0)FxLib.trail(m,c,clip(combo),c.sec,.1f,8,.7,1.35,0xece0ff,tr);
        if(t<0)return;
        if(combo==1||combo==2||combo==3){
            // Thin white wind arcs in the swing plane, expanding 30 % while fading.
            Vec3d[] b=combo==1?plane(c,-.3,.94,.15):combo==2?frontal(c):sagittal(c);
            double start=combo==1?110:combo==2?215:100,len=combo==1?-180:combo==2?140:-110,outer=2.2;
            if(t>=.014f&&t<.35f){
                float grow=Ease.out(t,.014f,.35f),alpha=.4f*(1-Ease.smooth(t,.12f,.35f));
                for(int i=0;i<3;i++)arc(m,torso(c),b[0],b[1],outer*(1.0+.12*i)*(1+.3*grow),start+i*12*Math.signum(len),len*.8,.018,0xffffff,alpha*(1-.25f*i));
            }
            // Sparks flung off the blade near the end of the cut.
            if(t<.3f){
                double end=start+len;
                for(int k=0;k<8;k++){
                    float born=.02f*k,u=(t-born)/.22f;if(u<0||u>=1)continue;
                    double ang=Math.toRadians(end-len*.35*Ease.hash(k*3+1));
                    Vec3d d=b[0].multiply(Math.cos(ang)).add(b[1].multiply(Math.sin(ang)));
                    Vec3d p=torso(c).add(d.multiply(outer*(1+.8*u))).add(0,-.6*u*u+.2*Ease.hash(k*5),0);
                    m.star(p,.05+.06*Ease.hash(k*7),0xf4ecff,(1-u)*(1-u));
                }
            }
        }
        if(combo==3){
            // The chop meets the ground: a crack of light ahead and a small shockwave.
            Vec3d at=c.origin.add(c.f.multiply(1.9)).add(0,.04,0);
            if(t<.3f){
                float u=Ease.unit(t,0,.3f);
                FxLib.ring(m,at,.3+1.6*Ease.out(t,0,.3f),.25,0xd8c4ff,.8f*(1-u));
                m.glowLine(at,at.add(c.f.multiply(1.6*Ease.out(t,0,.08f))),.035,0xd0b8ff,1-Ease.smooth(t,.15f,.3f));
                FxLib.flash(m,at.add(0,.15,0),.45,0xe8d8ff,.9f*(1-Ease.smooth(t,.02f,.14f)));
            }
        }
        if(combo==4)thrust(c,m,t);
        targetSparks(c,m,t,combo);
    }
    /** A spark at every struck target's chest; the fourth hit adds a flash and a ring facing the thrust. */
    static void targetSparks(Fx c,EffectMesh m,float t,int combo){
        if(t<0||t>=.4f||c.targets.isEmpty())return;
        for(LivingEntity e:c.targets){
            Vec3d chest=e.getLerpedPos(c.delta).add(0,e.getHeight()*.6,0);
            float u=Ease.unit(t,0,.16f);
            m.star(chest,.35*(1+.4*u),0xffffff,1-Ease.smooth(u,.5f,1));
            FxLib.sparks(m,chest,combo==4?12:7,combo==4?1.6:1.0,.05,0xf6e8ff,1-Ease.smooth(t,.1f,.3f),combo*31+5,Ease.out(t,0,.12f));
            if(combo==4){
                float v=Ease.unit(t,0,.4f);
                FxLib.flash(m,chest,.9*(1-v)+.2,0xd8b8ff,.9f*(1-Ease.smooth(t,.05f,.3f)));
                FxLib.ringPlane(m,chest,c.right,UP,.4+2.2*Ease.out(t,0,.4f),.25,0xe4d4ff,.8f*(1-v));
            }
        }
    }
    @Override public void alpha(Fx c,EffectMesh m){
        int combo=c.cast.combo();
        // Violet body of the sword trail under the white ribbon.
        float tr=Ease.smooth(c.sec,.18f,.23f)*(1-Ease.smooth(c.sec,.31f,.40f));
        if(tr>0)FxLib.trail(m,c,clip(combo),c.sec,.1f,8,.3,1.3,0x7a40e0,.55f*tr);
        float t=c.sec-HIT;if(combo!=4||t<-.03f||t>=.5f)return;
        // Wide translucent lavender wind bands thrown out by the spin, drifting up and out.
        int[] colors={0xd4c2fb,0xaea2f8};
        float life=Ease.unit(t,-.03f,.5f),a=.2f*Ease.smooth(t,-.03f,.03f)*(1-Ease.smooth(t,.3f,.5f));
        for(int i=0;i<2;i++){
            double r=2.6+.6*i+1.4*life,y=.5+.35*i+.6*life,start=i*67+90*life,len=150+30*i;
            band(m,c.origin.add(0,y,0),r,.16+.06*i,start,len,colors[i],a*(1-.08f*i));
        }
    }
    @Override public void shaders(Fx c){
        float t=c.sec-HIT;if(t<0)return;int combo=c.cast.combo();
        List<Vec3d> pts=new ArrayList<>();List<Double> sizes=new ArrayList<>();float alpha=0;
        if(combo==3&&t>=.02f&&t<.5f){
            // Dust kicked up where the chop lands.
            alpha=Ease.smooth(t,.02f,.1f)*(1-Ease.smooth(t,.3f,.5f));float grow=Ease.out(t,.02f,.3f);
            Vec3d at=c.origin.add(c.f.multiply(1.9));
            for(int i=0;i<4;i++){double a=i*1.57+.4,r=.2+.7*grow;pts.add(at.add(Math.cos(a)*r,.2+.3*grow*Ease.hash(i*5+2),Math.sin(a)*r));sizes.add(.25+.35*grow);}
        }
        if(combo==4&&t>=.1f&&t<.78f){
            // Ground-hugging dark indigo smoke at the thrust's impact point, peaking 0.48 s after it.
            alpha=Ease.smooth(t,.1f,.3f)*(1-Ease.smooth(t,.55f,.78f));float grow=Ease.out(t,.1f,.48f);
            Vec3d fw=thrustAxis(c),side=new Vec3d(-fw.z,0,fw.x),at=c.origin.add(fw.multiply(2.0));
            for(int i=0;i<4;i++){double a=i*2.4,r=.25+.55*grow*Ease.hash(i*3+1);pts.add(at.add(side.multiply(Math.cos(a)*r)).add(fw.multiply(Math.sin(a)*r*.6)).add(0,.3+.45*Ease.hash(i*5+2),0));sizes.add(.3+.40*grow*(.6+.4*Ease.hash(i*7)));}
        }
        if(!pts.isEmpty())KagenoFire.smoke(c.matrix,c.camera,pts,sizes,c.age,alpha);
    }
    /** H4: needle cone converging two character heights ahead, apex flash, a shockwave facing the thrust and lightning in the smoke. */
    static void thrust(Fx c,EffectMesh m,float t){
        Vec3d fw=thrustAxis(c),side=new Vec3d(-fw.z,0,fw.x);
        Vec3d apex=c.origin.add(fw.multiply(3.6)).add(0,.62,0);
        if(t<.36f){
            float a=Ease.smooth(t,0,.017f)*(1-Ease.smooth(t,.23f,.36f));
            for(int i=0;i<7;i++){
                Vec3d base=c.origin.add(fw.multiply(.4)).add(side.multiply((Ease.hash(i*3)-.5)*1.3)).add(0,.7+Ease.hash(i*5)*1.0,0);
                Vec3d dir=apex.subtract(base);double len=(.4+1.2*Ease.hash(i*7+1))*1.8,from=Math.max(0,dir.length()-len-.2*Ease.hash(i*9));
                Vec3d p0=base.add(dir.normalize().multiply(from)),p1=base.add(dir.normalize().multiply(Math.min(dir.length(),from+len)));
                m.needle(p0,p1,.08,.55,0x4646b2,a*.55f);
                m.needle(p0,p1,.035,.6,i%4==0?0x9369e6:0xf9effb,a);
            }
            m.softDisc(apex,.30,0xffffff,a*(1-Ease.smooth(t,.05f,.16f)));
            // Shockwave ring facing the thrust, and a flash at the sword tip.
            float u=Ease.unit(t,0,.36f);
            FxLib.ringPlane(m,c.origin.add(fw.multiply(2.2)).add(0,1.0,0),side,UP,.3+2.6*Ease.out(t,0,.36f),.3,0xdcc8ff,.85f*(1-u));
            FxLib.flash(m,c.origin.add(fw.multiply(1.9)).add(0,1.0,0),.7*(1-u)+.1,0xe8d8ff,.9f*(1-Ease.smooth(t,.03f,.18f)));
        }
        if(t>=.1f&&t<.72f){
            // Zig-zag lightning inside the smoke, re-randomised every frame or two.
            long step=(long)Math.floor(c.sec*40);float a=Ease.smooth(t,.1f,.18f)*(1-Ease.smooth(t,.55f,.72f));
            Vec3d at=c.origin.add(fw.multiply(2.0));
            for(int k=0;k<2;k++){
                Vec3d p=at.add(side.multiply((Ease.hash((int)step*13+k)-.5)*1.6)).add(0,.1,0);
                for(int i=0;i<3;i++){
                    Vec3d q=p.add(side.multiply((Ease.hash((int)step*7+k*31+i)-.5)*.5)).add(fw.multiply((Ease.hash((int)step*11+k*17+i)-.5)*.4)).add(0,.18+.12*Ease.hash(i+k*5+(int)step),0);
                    m.glowLine(p,q,.018,0x8040e8,a);p=q;
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
