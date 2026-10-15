package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * E "신속한 맹공": a 0.47 s cross-body draw, a one-frame snap into a lunge with a glowing blade,
 * a white-violet energy ball spitting ≈26 pulses of needle streaks and lightning for ≈1.95 s, a
 * rising cut and a vertical crescent with a lightning pillar. Seconds after the cast.
 */
final class BarrageSkill implements Fx.Skill {
    static final double H=1.8;
    static final float SNAP=.467f,BALL=.517f,COLLAPSE=2.45f,BALL_END=2.536f,CUT=2.703f,UP=2.753f,CRESCENT=2.903f,PILLAR=2.98f;
    // Aim = the cast facing. The windup twists ≈18° to the left and snaps square to the aim in one frame.
    static final R6.Pose DRAW=pose().body(18,10,0).head(0,12,0).rArm(15,-60).lArm(-70,-20).rLeg(-70,160).lLeg(-75,-20).blade(0,95);
    static final R6.Pose LUNGE=pose().body(0,8,0).at(0,-.25,0).rArm(0,0).lArm(-40,-150).lLeg(-60,0).rLeg(-60,180).blade(0,0);
    static final R6.Pose PUMP=LUNGE.rArm(-28,6).blade(-22,4);
    static final Anim CLIP;
    static {
        List<Key> keys=new ArrayList<>(List.of(
            k(0,KagenoClips.IDLE),
            k(.016,pose().body(8,0,0).rArm(-60,10).lLeg(-80,-25).blade(-45,60),Anim.Ease.LINEAR),
            k(.066,pose().body(12,0,0).rArm(-30,-10).lLeg(-80,-25).blade(-45,50),Anim.Ease.LINEAR),
            k(.116,pose().body(15,5,0).rArm(0,-20).lLeg(-78,-25).blade(-10,85),Anim.Ease.LINEAR),
            k(.166,DRAW,Anim.Ease.OUT),
            k(.266,DRAW,Anim.Ease.HOLD),
            k(.416,DRAW.at(0,-.18,0).lArm(-60,-150).blade(-25,90)),
            k(SNAP,LUNGE,Anim.Ease.SNAP)));
        // The sword arm pumps a thrust every ≈4.5 frames while the ball hides the body.
        boolean out=false;for(float t=BALL;t<COLLAPSE;t+=.0375f){keys.add(k(t,out?LUNGE:PUMP,Anim.Ease.LINEAR));out=!out;}
        keys.addAll(List.of(
            k(BALL_END,LUNGE.rArm(-45,0).blade(-70,0)),
            k(CUT-.01,LUNGE.rArm(-45,0).blade(-70,0),Anim.Ease.HOLD),
            k(CUT+.033,LUNGE.at(0,-.1,0).rArm(30,20).blade(45,30),Anim.Ease.LINEAR),
            k(UP,pose().rArm(88,0).lArm(0,-90).blade(88,0),Anim.Ease.LINEAR),
            k(2.886,pose().rArm(88,0).lArm(0,-90).blade(72,180)),
            k(3.036,pose().rArm(85,0).lArm(0,-90).blade(72,180),Anim.Ease.HOLD),
            k(3.186,pose().rArm(-45,60).lArm(-60,-40).blade(-40,60)),
            k(3.336,KagenoClips.IDLE)));
        CLIP=Anim.of(keys.toArray(Key[]::new));
    }
    @Override public Anim clip(int combo){return CLIP;}
    @Override public float lifetime(){return 3.9f;}

    /** The barrage, crescent and pillar follow the cast facing (the player's aim). */
    static Vec3d aim(Fx c){return c.f;}
    static Vec3d ballCentre(Fx c){return c.origin.add(aim(c).multiply(.45*H+.35)).add(0,.58*H,0);}
    static float ballSize(float t){return t<BALL?0:Ease.out(t,BALL,BALL+.15f)*(1-Ease.smooth(t,COLLAPSE,BALL_END));}

    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;
        var hb=KagenoClips.blade(c.origin,c.cast.yaw(),CLIP.pose(t));Vec3d hand=hb[0],tip=hand.add(hb[1].multiply(1.35));
        // Neon blade from the snap to the end of the overhead hold; a pink X glint at ignition.
        if(t>=SNAP&&t<2.89f){
            float a=1-.25f*Ease.smooth(t,2.7f,2.89f);
            m.glowLine(hand,tip,.05,0xcca3ef,a);
            if(t<SNAP+.05f){Vec3d mid=hand.add(tip).multiply(.5),cross=hb[1].crossProduct(new Vec3d(0,1,0)).normalize().multiply(.45).add(0,.2,0);m.glowLine(mid.subtract(cross),mid.add(cross),.05,0xf0a0f8,1);}
        }
        if(t>=CUT&&t<CUT+.05f)m.star(hand,.27,0xffffff,1);
        // Swirl ring at the ball's onset, expanding into the ball.
        if(t>=BALL&&t<BALL+.08f){
            float u=Ease.unit(t,BALL,BALL+.08f);Vec3d o=ballCentre(c);
            ComboSkill.arc(m,o,aim(c),c.right,1.17*(1+.4*u),20,250,.08,0x9a77c8,.5f*(1-u));
        }
        float size=ballSize(t);
        if(size>0){
            Vec3d o=ballCentre(c);
            // Squashed additive ball ≈2.0 × 1.45 character heights: haze, violet rim, lilac-pink body,
            // white core; the bright area pulses irregularly (peaks every 0.15-0.3 s).
            float flick=.78f+.22f*(float)Math.sin(t*23)*(float)Math.sin(t*9.3+1);
            ellipse(m,o,2.15*size,1.55*size,0x6577b6,.3f);
            ellipse(m,o,1.85*size,1.32*size,0x9a77c8,.45f*flick);
            ellipse(m,o,1.45*size,1.05*size,0xe0a0ff,.7f*flick);
            ellipse(m,o,1.0*size,.75*size,0xf3cdfa,.85f);
            ellipse(m,o,.55*size,.45*size,0xffffff,1);
            // Needle streaks: ≈26 pulses, 3–5 streaks each, shooting forward in a ±25° cone that
            // leans down, far ends reaching ≈3 character heights from the ball.
            if(t>=.584f){
                int pulse=(int)Math.floor((t-.584f)/.075f);
                for(int p=Math.max(0,pulse-2);p<=pulse;p++){
                    float born=.584f+p*.075f,u=(t-born)/.1f;if(u<0||u>=1)continue;
                    int count=3+(int)(Ease.hash(p*41+7)*3);
                    for(int s=0;s<count;s++){
                        double h1=Ease.hash(p*17+s*5+3),h2=Ease.hash(p*29+s*7+1),h3=Ease.hash(p*11+s*13+2);
                        double yaw=(h1-.5)*50,pitch=-(h2*.8-.2)*25;Vec3d dir=rotateAim(c,yaw,pitch);
                        double len=(.3+1.0*h3)*H,start=.5+u*3.2;
                        Vec3d a0=o.add(dir.multiply(start)),a1=o.add(dir.multiply(start+len*Math.min(1,u*3)));
                        float a=1-Ease.smooth(u,.5f,1);
                        m.needle(a0,a1,.3,.5,0x7a78d6,a*.5f);m.needle(a0,a1,.16,.52,0xdca8ed,a*.8f);m.needle(a0,a1,.06,.55,0xfdcbfb,a);
                    }
                }
            }
            // Jagged branching lightning forward and down to the ground, re-randomised every frame or two;
            // densest at the onset.
            if(t>=.534f){
                long step=(long)Math.floor(t*40);int bolts=t<.69f?8:Ease.hash((int)step)>.3?5:2;
                for(int k=0;k<bolts;k++){
                    Vec3d p=o.add(rotateAim(c,(Ease.hash((int)step*7+k)-.5)*50,(Ease.hash((int)step*13+k)-.6)*40).multiply(1.1*size));double len=(.5+1.9*Ease.hash((int)step*3+k))*H;
                    Vec3d end=p.add(rotateAim(c,(Ease.hash((int)step*5+k)-.5)*60,-35-30*Ease.hash((int)step*9+k)).multiply(len));
                    zig(m,p,end,7,.035,0xa28bee,.95f,(int)step*31+k);
                    // A short side branch from the middle of the bolt.
                    Vec3d mid=p.lerp(end,.45),branch=mid.add(rotateAim(c,(Ease.hash((int)step*19+k)-.5)*120,-20-40*Ease.hash((int)step*23+k)).multiply(len*.35));
                    zig(m,mid,branch,3,.025,0x7e7ad8,.8f,(int)step*37+k);
                }
            }
        }
        // Faint violet haze for 0.3 s after the collapse.
        if(t>=BALL_END&&t<BALL_END+.3f)ellipse(m,ballCentre(c),1.9,1.4,0x9a77c8,.3f*(1-Ease.unit(t,BALL_END,BALL_END+.3f)));
        // Wind arcs concentric with the finisher crescent.
        if(t>=CRESCENT&&t<CRESCENT+.48f){
            float u=Ease.unit(t,CRESCENT,CRESCENT+.48f);Vec3d o=crescentCentre(c),fw=aim(c);
            for(int i=0;i<4;i++)ComboSkill.arc(m,o,fw,new Vec3d(0,1,0),(1.0+.25*i)*H+.36*u,-30+15*i,140,.025,0xffffff,(.9f-.15f*i)*(1-u));
        }
        pillar(c,m);
    }
    /** Camera-facing elliptical glow, {@code rx} across and {@code ry} up (blocks). */
    static void ellipse(EffectMesh m,Vec3d p,double rx,double ry,int rgb,float alpha){
        if(alpha<=0||rx<=0)return;
        Vec3d n=m.camera().subtract(p).normalize(),u=n.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(n).normalize();
        for(int i=0;i<24;i++){
            double a=i*Math.PI/12,b=(i+1)*Math.PI/12;
            Vec3d pa=p.add(u.multiply(Math.cos(a)*rx)).add(v.multiply(Math.sin(a)*ry)),pb=p.add(u.multiply(Math.cos(b)*rx)).add(v.multiply(Math.sin(b)*ry));
            m.vertex(p,rgb,alpha);m.vertex(pa,rgb,0);m.vertex(pb,rgb,0);m.vertex(p,rgb,alpha);
        }
    }
    static Vec3d rotateAim(Fx c,double yawDeg,double pitchDeg){
        Vec3d fw=aim(c),side=new Vec3d(-fw.z,0,fw.x);double y=Math.toRadians(yawDeg),p=Math.toRadians(pitchDeg);
        return fw.multiply(Math.cos(y)*Math.cos(p)).add(side.multiply(Math.sin(y)*Math.cos(p))).add(0,Math.sin(p),0);
    }
    static Vec3d crescentCentre(Fx c){return c.origin.add(aim(c).multiply(.15*H)).add(0,.9*H,0);}
    /** Jagged bolt from a to b with {@code n} kinks. */
    static void zig(EffectMesh m,Vec3d a,Vec3d b,int n,double width,int rgb,float alpha,int seed){
        Vec3d prev=a,d=b.subtract(a);double len=d.length();if(len<1e-4)return;
        Vec3d side=d.crossProduct(new Vec3d(0,1,0));if(side.lengthSquared()<1e-6)side=new Vec3d(1,0,0);side=side.normalize();Vec3d up=side.crossProduct(d).normalize();
        for(int i=1;i<=n;i++){
            double t=i/(double)n;Vec3d p=a.add(d.multiply(t));
            if(i<n)p=p.add(side.multiply((Ease.hash(seed*13+i)-.5)*len*.22)).add(up.multiply((Ease.hash(seed*7+i)-.5)*len*.12));
            m.glowLine(prev,p,width,rgb,alpha);prev=p;
        }
    }
    /** Lightning pillar at the crescent's ground point: thin bolt, full at +0.08 s, fading to +0.62 s. */
    static void pillar(Fx c,EffectMesh m){
        float t=c.sec;if(t<PILLAR||t>=PILLAR+.85f)return;
        Vec3d base=c.targets.isEmpty()?c.origin.add(aim(c).multiply(.8*H)):c.targets.get(0).getLerpedPos(c.delta);
        float grow=Ease.out(t,PILLAR,PILLAR+.08f),a=1-Ease.smooth(t,PILLAR+.33f,PILLAR+.62f);
        long step=(long)Math.floor(t*40);
        if(a>0){
            Vec3d lean=c.right.multiply(.17).add(0,1,0).normalize();
            Vec3d prev=base;int n=14;double h=9*H*grow;
            for(int i=1;i<=n;i++){
                Vec3d p=base.add(lean.multiply(h*i/n)).add(c.right.multiply((Ease.hash((int)step*11+i)-.5)*.5)).add(c.f.multiply((Ease.hash((int)step*5+i)-.5)*.5));
                // Wide violet glow (0.15-0.3 character heights) around a thin white-lilac core.
                m.line(prev,p,.5,0x5858c4,a*.28f);m.line(prev,p,.26,0x937aee,a*.5f);m.line(prev,p,.13,0xdfbbff,a*.8f);m.line(prev,p,.05,0xfefcfd,a);
                if(i%3==0){m.softDisc(p,.3,0xc19fff,a*.5f);m.star(p,.3,0xffffff,a);}
                prev=p;
            }
            m.softDisc(base.add(0,.2,0),.4*H,0x391ea7,.6f*a);
        }
        for(int k=0;k<10;k++){double life=((t-PILLAR)*1.5+k*.1)%1;m.star(base.add((Ease.hash(k*3)-.5)*.8,life*5,(Ease.hash(k*5)-.5)*.8),.08,0xe0d0ff,(float)(1-life)*(1-Ease.smooth(t,PILLAR+.6f,PILLAR+.85f)));}
    }
    @Override public void crescents(Fx c){
        float t=c.sec-CRESCENT;if(t<0||t>=.29f)return;
        // Vertical crescent along the facing: from the ground in front, up over the front, curling back behind the head.
        float erase=t<.13f?0:(float)Math.pow((t-.13f)/.16f,1.2);
        KagenoFire.crescent(crescentCentre(c),aim(c),new Vec3d(0,1,0),1.0*H,.42*H,-45,175,3,5,erase,.5f,1);
    }
    @Override public void alpha(Fx c,EffectMesh m){
        // Dark violet haze on the ground under and in front of the ball.
        float size=ballSize(c.sec);if(size<=0)return;
        Vec3d o=c.origin.add(aim(c).multiply(1.1)).add(0,.03,0);
        for(int i=0;i<24;i++){double a0=i*Math.PI/12,a1=(i+1)*Math.PI/12,r=2.3*size;
            m.vertex(o,0x332664,.45f);m.vertex(o.add(Math.cos(a0)*r,0,Math.sin(a0)*r),0x5e47a1,0);m.vertex(o.add(Math.cos(a1)*r,0,Math.sin(a1)*r),0x5e47a1,0);m.vertex(o,0x332664,.4f);}
    }
    @Override public void strokes(Fx c){
        // Vortex of spinning crescent wisps around the thrust axis, filling the squashed ball; after the
        // collapse a faint wider ring of them lingers for 0.3 s.
        float t=c.sec,size=ballSize(t);Vec3d o=ballCentre(c),fw=aim(c),side=new Vec3d(-fw.z,0,fw.x);
        float linger=t>=COLLAPSE&&t<BALL_END+.3f?1-Ease.unit(t,COLLAPSE,BALL_END+.3f):0;
        if(size<=0&&linger<=0)return;
        int n=size>0?14:8;
        for(int i=0;i<n;i++){
            double tilt=(Ease.hash(i*7)-.5)*1.1,spin=t*(9+4*Ease.hash(i*3))+i*1.7;
            // Plane roughly across the thrust axis, tilted per wisp; squashed vertically like the ball.
            Vec3d a=side.multiply(Math.cos(tilt)).add(fw.multiply(Math.sin(tilt))),b=new Vec3d(0,.74,0).add(fw.multiply(.25*Math.sin(tilt*1.7)));
            Vec3d u=a.multiply(Math.cos(spin)).add(b.multiply(Math.sin(spin))),v=b.multiply(Math.cos(spin)).subtract(a.multiply(Math.sin(spin)));
            double r=size>0?(.7+1.0*Ease.hash(i*11))*size:(1.6+.8*Ease.hash(i*11))*(1+.2*(1-linger));
            KagenoFire.stroke(o,u,v,r,0,1.9+.8*Ease.hash(i*13),(.25+.2*Ease.hash(i*17))*(size>0?size:.6),i*9+3,size>0?.8f:.35f*linger);
        }
    }
}
