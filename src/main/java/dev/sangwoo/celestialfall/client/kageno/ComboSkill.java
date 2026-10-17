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
 * M1 "검술의 대가", rebuilt from the user's four-angle recordings: four hits, each a separate cast (combo 1–4); the
 * blade connects 0.30 s after the click (the server's hit tick 6). Hit 1 raises the sword over the left shoulder and
 * cuts down to the right, leaving a tilted ring round the body (higher behind) that forms black-violet, turns
 * magenta and spreads out as blue-violet streaks; hit 2 draws the sword back behind the left side and sweeps it
 * flat round to the front right in a lunge (a flatter ring at the waist); hit 3 crouches and sweeps low (a wide ring
 * on the ground with a dark inner edge, a magenta mist and pink motes rising inside it); hit 4 draws the sword back
 * to the hip and thrusts, throwing a fan of white-pink spikes forward along the ground and leaving dark violet
 * crackle round the body. Seconds after the click.
 */
final class ComboSkill implements Fx.Skill {
    static final double STUD=.36;
    static final float HIT=.30f;
    static final Vec3d UP=new Vec3d(0,1,0);
    // H1 — diagonal cut: the sword goes up over the left shoulder, then down across the front past the right hip.
    static final R6.Pose H1_RAISE=pose().body(24,-6,0).head(-10,-6,0).at(-.04,.02,0).rArm(72,-30).lArm(-60,-20).blade(58,-150).rLeg(-84,12).lLeg(-80,-14);
    static final R6.Pose H1_END=pose().body(-34,16,0).head(14,8,0).at(.08,-.08,0).rArm(-58,108).lArm(-50,-40).blade(-46,132).rLeg(-76,24).lLeg(-72,-16);
    static final Anim H1=Anim.joints(
        k(0,KagenoClips.IDLE),
        k(.12,H1_RAISE),
        // The coil keeps winding (a moving hold), then the hips lead the cut and the weight drops forward.
        k(.21,H1_RAISE.body(28,-8,0).rArm(76,-38).blade(62,-158)),
        k(.26,pose().body(2,6,0).at(.03,-.03,0).rArm(28,10).lArm(-55,-35).blade(18,30).rLeg(-80,18).lLeg(-76,-15),Anim.Ease.LINEAR),
        k(HIT,pose().body(-26,13,0).head(10,6,0).at(.07,-.07,0).rArm(-36,72).lArm(-52,-40).blade(-30,96).rLeg(-77,22).lLeg(-73,-16),Anim.Ease.LINEAR),
        k(.42,H1_END),
        k(.62,H1_END.body(-31,13,0).at(.05,-.05,0)),
        k(.95,KagenoClips.IDLE)).stops(Anim.stop(HIT,.035,.08));
    // H2 — flat sweep: the sword drawn back behind the left side at the chest, swept round the front to the right in a lunge.
    static final R6.Pose H2_COIL=pose().body(48,8,0).head(-24,0,0).at(-.04,-.08,0).rArm(4,-96).lArm(-58,-30).blade(6,-168).rLeg(-80,16).lLeg(-76,-22);
    static final R6.Pose H2_END=pose().body(-40,14,0).head(18,4,0).at(.18,-.15,0).rArm(-8,86).lArm(-30,-140).blade(-8,106).rLeg(-64,14).lLeg(-60,182);
    static final Anim H2=Anim.joints(
        k(0,H1_END),
        k(.12,H2_COIL),
        k(.21,H2_COIL.body(52,9,0).rArm(4,-102).blade(6,-174)),
        k(.26,pose().body(12,10,0).head(-6,0,0).at(.06,-.1,0).rArm(0,-12).lArm(-50,-60).blade(2,-24).rLeg(-74,14).lLeg(-70,-40),Anim.Ease.LINEAR),
        k(HIT,pose().body(-22,12,0).head(10,2,0).at(.13,-.13,0).rArm(-2,60).lArm(-38,-110).blade(-2,80).rLeg(-68,14).lLeg(-64,120),Anim.Ease.LINEAR),
        k(.44,H2_END),
        k(.62,H2_END.body(-37,12,0).at(.15,-.12,0)),
        k(.95,KagenoClips.IDLE)).stops(Anim.stop(HIT,.04,.09));
    // H3 — low sweep: down into a wide crouch with the sword low behind the left, swept along the ground to the right.
    static final R6.Pose H3_COIL=pose().body(40,24,0).head(-20,10,0).at(-.02,-.36,0).rArm(-40,-110).lArm(-50,-20).blade(-35,-160).rLeg(-58,40).lLeg(-58,-40);
    static final R6.Pose H3_END=pose().body(-44,26,0).head(20,12,0).at(.08,-.4,0).rArm(-48,104).lArm(-50,-60).blade(-38,120).rLeg(-56,46).lLeg(-56,-42);
    static final Anim H3=Anim.joints(
        k(0,H2_END),
        k(.13,H3_COIL),
        k(.21,H3_COIL.body(44,25,0).at(-.03,-.38,0).blade(-36,-166)),
        k(.26,pose().body(6,26,0).head(-4,10,0).at(.03,-.38,0).rArm(-42,0).lArm(-50,-40).blade(-34,10).rLeg(-57,43).lLeg(-57,-41),Anim.Ease.LINEAR),
        k(HIT,pose().body(-28,26,0).head(12,12,0).at(.06,-.39,0).rArm(-46,70).lArm(-50,-52).blade(-36,90).rLeg(-56,45).lLeg(-56,-42),Anim.Ease.LINEAR),
        k(.44,H3_END),
        k(.62,H3_END.body(-40,22,0).at(.06,-.32,0)),
        k(.95,KagenoClips.IDLE)).stops(Anim.stop(HIT,.045,.1));
    // H4 — thrust: the sword drawn back to the right hip with its point forward, the left hand out, then a long lunge.
    static final R6.Pose H4_DRAW=pose().body(-34,4,0).head(26,2,0).at(-.1,-.1,0).rArm(-48,160).lArm(-10,-40).blade(-4,6).rLeg(-80,20).lLeg(-76,-20);
    static final R6.Pose THRUST=pose().body(12,10,0).head(-8,0,0).at(.42,-.16,0).rArm(-2,4).lArm(-10,-150).blade(0,0).rLeg(-62,10).lLeg(-58,185);
    static final Anim H4=Anim.joints(
        k(0,H3_END),
        k(.14,H4_DRAW),
        k(.24,H4_DRAW.at(-.14,-.12,0).rArm(-50,166).body(-38,5,0)),
        k(HIT,THRUST,Anim.Ease.LINEAR),
        k(.66,THRUST.at(.38,-.14,0).body(10,8,0)),
        k(.84,pose().body(4,4,0).at(.2,-.06,0).rArm(-30,10).lArm(-60,-20).blade(-20,20).rLeg(-80,12).lLeg(-80,-12)),
        k(1.15,KagenoClips.IDLE)).stops(Anim.stop(HIT,.06,.12));

    @Override public Anim clip(int combo){return switch(combo){case 2->H2;case 3->H3;case 4->H4;default->H1;};}
    @Override public float lifetime(){return 1.25f;}

    /** Plane through the body with body-space normal (R,U,F); returns {front-in-plane, right-in-plane}. */
    static Vec3d[] plane(Fx c,double nr,double nu,double nf){
        Vec3d n=c.vec(nf,nu,nr).normalize();
        Vec3d u=c.f.subtract(n.multiply(c.f.dotProduct(n))).normalize();
        Vec3d v=c.right.subtract(n.multiply(c.right.dotProduct(n))).subtract(u.multiply(c.right.dotProduct(u))).normalize();
        return new Vec3d[]{u,v};
    }
    /** The ring each of the first three hits leaves: height of its centre, tilt (higher behind), first radius, growth, band width, where the blade enters (degrees from the front toward the right). */
    record Ring(double y,double tilt,double radius,double grow,double width,double start){}
    static Ring ring(int combo){
        return switch(combo){
            case 2->new Ring(.85,9,2.9,1.9,.95,-150);
            case 3->new Ring(.3,3,3.4,2.8,1.15,-160);
            default->new Ring(1.05,25,2.8,1.9,1.0,-135);
        };
    }
    static Vec3d[] ringPlane(Fx c,Ring r){double a=Math.toRadians(r.tilt());return plane(c,0,Math.cos(a),Math.sin(a));}

    @Override public void crescents(Fx c){
        float t=c.sec-HIT;int combo=c.cast.combo();
        if(combo<=3){
            Ring r=ring(combo);var b=ringPlane(c,r);Vec3d o=c.origin.add(0,r.y(),0);
            // Forming: a black-violet crescent sweeps once round the body in three frames.
            if(t>=-.03f&&t<.08f){
                double len=360*Ease.out(t,-.03f,.03f);float a=1-Ease.smooth(t,.03f,.08f);
                KagenoFire.crescent(o,b[0],b[1],r.radius(),r.width()*1.1,r.start(),len,9,combo*3,0,.5f,a);
            }
            // The magenta ring: holds and swells, then wears away from where it began. Hit 3 rings twice.
            if(t>=0&&t<.36f){
                double rad=r.radius()+r.grow()*.5*Ease.out(t,0,.36f);
                KagenoFire.crescent(o,b[0],b[1],rad,r.width(),r.start(),360,8,combo*3+1,Ease.smooth(t,.16f,.36f),.5f,Ease.smooth(t,0,.04f));
                if(combo==3)KagenoFire.crescent(o.add(0,.05,0),b[0],b[1],rad*.62,r.width()*.55,r.start()+30,360,8,12,Ease.smooth(t,.2f,.36f),.5f,Ease.smooth(t,.02f,.06f));
            }
            // Blue-violet streaks spreading out as it fades.
            if(t>=.1f&&t<.6f){
                float u=Ease.unit(t,.1f,.6f);
                for(int i=0;i<3;i++){
                    double rad=r.radius()+r.grow()*(.5+.5*u)+.35*i;
                    KagenoFire.crescent(o.add(0,(i-1)*.12,0),b[0],b[1],rad,.16+.06*(2-i),r.start()+40*i+120*u,200+60*i,10,combo*3+2+i,Ease.smooth(u,.4f,1),.5f,.8f*Ease.smooth(u,0,.15f));
                }
            }
        }
        if(combo==4&&t>=.02f&&t<.55f){
            // Blue-violet streaks whipping round the body after the thrust.
            float u=Ease.unit(t,.02f,.55f);var b=plane(c,0,1,0);
            for(int i=0;i<4;i++)KagenoFire.crescent(c.origin.add(0,.5+.35*i,0),b[0],b[1],1.8+2.6*Ease.out(u,0,1)+.3*i,.14+.05*(i%2),i*90+200*u,150,10,40+i,Ease.smooth(u,.45f,1),.5f,.85f*Ease.smooth(u,0,.1f));
        }
    }
    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec-HIT;int combo=c.cast.combo();
        // Sword trail: a magenta ribbon swept by the outer blade through the strike.
        float tr=Ease.smooth(c.sec,.21f,.25f)*(1-Ease.smooth(c.sec,.32f,.40f));
        if(tr>0)FxLib.trail(m,c,clip(combo),c.sec,.08f,8,.7,1.35,0xe00ff4,.8f*tr);
        if(t<0)return;
        if(combo<=3)motes(c,m,t,combo);
        if(combo==4)spikes(c,m,t);
        targetSparks(c,m,t,combo);
    }
    /** Pink motes flung out inside the ring; hit 3 throws many more, rising through its mist. */
    static void motes(Fx c,EffectMesh m,float t,int combo){
        Ring r=ring(combo);int n=combo==3?38:12;float end=combo==3?.7f:.5f;
        if(t>=end)return;
        for(int k=0;k<n;k++){
            float born=.02f+.08f*(float)Ease.hash(k*3+combo),u=(t-born)/(end-born);if(u<0||u>=1)continue;
            double az=Ease.hash(k*5+combo*7)*Math.PI*2,rad=r.radius()*(.3+.9*Ease.hash(k*7+combo))*(1+.4*u);
            Vec3d p=c.origin.add(Math.cos(az)*rad,r.y()+(combo==3?.3+2.2*Ease.hash(k*11)*(.5+.5*u):(Ease.hash(k*11)-.5)*.8),Math.sin(az)*rad);
            float a=(float)Math.sin(Math.PI*u)*(.7f+.3f*(float)Math.sin(t*30+k));
            m.softDisc(p,.1+.08*Ease.hash(k*13),0xff4fe6,a);m.softDisc(p,.04,0xffe8ff,a);
        }
    }
    /** H4: a fan of white-pink spikes thrown forward along the ground from the sword point, and a flash at the point. */
    static void spikes(Fx c,EffectMesh m,float t){
        if(t>=.34f)return;
        Vec3d fw=c.f,side=c.right,base=c.origin.add(fw.multiply(1.1)).add(0,.75,0);
        float grow=Ease.out(t,0,.05f),a=1-Ease.smooth(t,.14f,.34f);
        for(int i=0;i<16;i++){
            double ang=(Ease.hash(i*3+1)-.5)*Math.toRadians(56),len=(2.2+4.5*Ease.hash(i*5+2))*grow,drop=.1+.5*Ease.hash(i*7+3);
            Vec3d dir=fw.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(0,-drop*.25+(i%5==0?.25:0),0).normalize();
            Vec3d p0=base.add(side.multiply((Ease.hash(i*11)-.5)*.4)).add(0,(Ease.hash(i*13)-.5)*.5,0),p1=p0.add(dir.multiply(len));
            m.needle(p0,p1,.38+.25*Ease.hash(i*17),.12,0xd440df,a*.85f);
            m.needle(p0,p1.subtract(dir.multiply(len*.15)),.14+.1*Ease.hash(i*19),.15,0xfbf0fc,a);
        }
        FxLib.flash(m,base,1.2,0xe8b0ff,1-Ease.smooth(t,0,.12f));
        // Lavender crackle round the body as the dark smoke rises.
        if(t>=.08f){
            long step=(long)Math.floor(c.sec*40);float b=Ease.smooth(t,.08f,.14f)*(1-Ease.smooth(t,.24f,.34f));
            for(int k=0;k<3;k++){Vec3d p=c.origin.add((Ease.hash((int)step*5+k)-.5)*1.4,.3+1.6*Ease.hash((int)step*7+k),(Ease.hash((int)step*11+k)-.5)*1.4);FxLib.zig(m,p,p.add((Ease.hash((int)step*13+k)-.5)*1.2,(Ease.hash((int)step*17+k)-.3)*1.2,(Ease.hash((int)step*19+k)-.5)*1.2),4,.02,0xd0a5f9,b,(int)step*3+k);}
        }
    }
    /** A spark at every struck target's chest; the fourth hit adds a flash and a ring facing the thrust. */
    static void targetSparks(Fx c,EffectMesh m,float t,int combo){
        if(t<0||t>=.4f||c.targets.isEmpty())return;
        for(LivingEntity e:c.targets){
            Vec3d chest=e.getLerpedPos(c.delta).add(0,e.getHeight()*.6,0);
            float u=Ease.unit(t,0,.16f);
            m.star(chest,.35*(1+.4*u),0xffffff,1-Ease.smooth(u,.5f,1));
            FxLib.sparks(m,chest,combo==4?12:7,combo==4?1.6:1.0,.05,0xffd8fa,1-Ease.smooth(t,.1f,.3f),combo*31+5,Ease.out(t,0,.12f));
            if(combo==4){
                float v=Ease.unit(t,0,.4f);
                FxLib.flash(m,chest,.9*(1-v)+.2,0xf0a8ff,.9f*(1-Ease.smooth(t,.05f,.3f)));
                FxLib.ringPlane(m,chest,c.right,UP,.4+2.2*Ease.out(t,0,.4f),.25,0xe8b8ff,.8f*(1-v));
            }
        }
    }
    @Override public void alpha(Fx c,EffectMesh m){
        int combo=c.cast.combo();
        // Inside each ring the ground goes dark, like the eye of the swirl.
        float tt=c.sec-HIT;
        if(combo<=3&&tt>=-.02f&&tt<.5f){
            Ring r=ring(combo);float a=Ease.smooth(tt,-.02f,.04f)*(1-Ease.smooth(tt,.25f,.5f));double rad=(r.radius()+r.grow()*.5*Ease.out(tt,0,.36f))*.92;
            Vec3d o=c.origin.add(0,.04,0);int n=32;
            for(int i=0;i<n;i++){double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
                m.vertex(o,0x12061e,.5f*a);m.vertex(o.add(Math.cos(a0)*rad,0,Math.sin(a0)*rad),0x2a0a44,.12f*a);m.vertex(o.add(Math.cos(a1)*rad,0,Math.sin(a1)*rad),0x2a0a44,.12f*a);m.vertex(o,0x12061e,.5f*a);}
        }
        // Violet body of the sword trail under the magenta ribbon.
        float tr=Ease.smooth(c.sec,.21f,.25f)*(1-Ease.smooth(c.sec,.32f,.40f));
        if(tr>0)FxLib.trail(m,c,clip(combo),c.sec,.08f,8,.4,1.3,0x8e01cd,.45f*tr);
        float t=c.sec-HIT;if(combo!=4||t<.08f||t>=.5f)return;
        // H4: dark crackle wrapped round the body after the thrust.
        long step=(long)Math.floor(c.sec*30);float a=Ease.smooth(t,.08f,.14f)*(1-Ease.smooth(t,.3f,.5f));
        for(int k=0;k<4;k++){
            Vec3d p=c.origin.add((Ease.hash((int)step*3+k)-.5)*1.2,.2+1.8*Ease.hash((int)step*5+k),(Ease.hash((int)step*7+k)-.5)*1.2);
            for(int i=0;i<4;i++){Vec3d q=p.add((Ease.hash((int)step*11+k*7+i)-.5)*.6,(Ease.hash((int)step*13+k*5+i)-.4)*.5,(Ease.hash((int)step*17+k*3+i)-.5)*.6);m.line(p,q,.09,0x3b1b7e,.5f*a);m.line(p,q,.04,0x1a0d35,.9f*a);p=q;}
        }
    }
    @Override public void shaders(Fx c){
        float t=c.sec-HIT;if(t<0)return;int combo=c.cast.combo();
        List<Vec3d> pts=new ArrayList<>();List<float[]> cells=new ArrayList<>();
        if(combo==3&&t>=.02f&&t<.62f){
            // A magenta mist welling up inside the low ring.
            float a=Ease.smooth(t,.02f,.1f)*(1-Ease.smooth(t,.35f,.62f)),grow=Ease.out(t,.02f,.4f);
            for(int i=0;i<20;i++){
                double az=Ease.hash(i*3+1)*Math.PI*2,rad=(.4+2.8*Math.sqrt(Ease.hash(i*5+2)))*(.7+.3*grow);
                Vec3d p=c.origin.add(Math.cos(az)*rad,.6+1.6*Ease.hash(i*7+3)*(.5+.5*grow),Math.sin(az)*rad);
                pts.add(p);cells.add(new float[]{(float)((.6+.6*Ease.hash(i*11+4))*(.6+.4*grow)),a*.45f,(float)(.78+.14*Ease.hash(i*13+5)),0,i*29+7});
            }
        }
        if(combo==4&&t>=.08f&&t<.6f){
            // Dark violet smoke round the body after the thrust.
            float a=Ease.smooth(t,.08f,.16f)*(1-Ease.smooth(t,.35f,.6f)),grow=Ease.out(t,.08f,.4f);
            for(int i=0;i<7;i++){
                double az=Ease.hash(i*3+9)*Math.PI*2,rad=.3+.9*Ease.hash(i*5+8)*(.6+.4*grow);
                Vec3d p=c.origin.add(Math.cos(az)*rad,.4+1.6*Ease.hash(i*7+7),Math.sin(az)*rad);
                pts.add(p);cells.add(new float[]{(float)((.5+.4*Ease.hash(i*11+6))*(.7+.3*grow)),a*.75f,(float)(.04+.1*Ease.hash(i*13+5)),.3f,i*31+3});
            }
        }
        if(!pts.isEmpty())KagenoFire.cells(c.matrix,c.camera,pts,cells,c.age);
    }
}
