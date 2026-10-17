package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * R "시간을 걷는 자": a raised back-hand stance, the world goes gray at 0.5 s (the caster keeps a
 * cyan outline), four fast sweeps leave white cut lines frozen in the air, colour returns at
 * 1.77 s and at 1.90 s the lines vanish into a white burst, a release line along the facing and
 * drifting petal shards. Seconds after the cast.
 */
final class TimeWalkerSkill implements Fx.Skill {
    static final double H=1.8;
    static final float GRAY=.5f,COLOR=1.769f,RELEASE=1.902f;
    static final float[] CUTS={1.018f,1.218f,1.384f,1.535f};
    static final R6.Pose STANCE=pose().body(0,10,0).head(0,20,0).rArm(55,110).lArm(-75,10).blade(-45,-150);
    static final R6.Pose SWEEP_A=pose().body(0,8,0).at(0,-.25,0).rArm(-30,90).lArm(0,-90).rLeg(-65,60).lLeg(-65,-60).blade(-30,90);
    static final Anim CLIP=Anim.of(
        k(0,KagenoClips.IDLE),
        k(.08,pose().body(-20,0,0).rArm(-45,10).blade(-30,30),Anim.Ease.LINEAR),
        k(.17,pose().body(-10,0,0).rArm(20,60).lArm(-60,-40).blade(30,170)),
        k(.40,STANCE,Anim.Ease.OUT),
        k(.92,STANCE.at(0,-.23,0),Anim.Ease.LINEAR),
        k(CUTS[0]-.05f,SWEEP_A.rArm(-10,-40).blade(-10,-60),Anim.Ease.LINEAR),
        k(CUTS[0],SWEEP_A,Anim.Ease.SNAP),
        k(1.10,SWEEP_A.rArm(-20,-60).blade(-40,-80),Anim.Ease.LINEAR),
        k(1.15,SWEEP_A,Anim.Ease.LINEAR),
        k(CUTS[1],SWEEP_A.body(90,8,0).rArm(20,0).blade(30,20),Anim.Ease.SNAP),
        k(CUTS[1]+.1f,SWEEP_A.body(90,8,0).rArm(20,0).blade(30,20),Anim.Ease.HOLD),
        k(CUTS[2],SWEEP_A.body(90,8,0).rArm(0,0).blade(0,0),Anim.Ease.SNAP),
        k(CUTS[2]+.08f,SWEEP_A.body(90,8,0).rArm(0,0).blade(0,0),Anim.Ease.HOLD),
        k(CUTS[3],SWEEP_A.body(90,8,0).rArm(-20,150).blade(-25,170),Anim.Ease.SNAP),
        k(1.70,SWEEP_A.body(90,8,0).rArm(0,140).blade(15,160)),
        k(1.75,SWEEP_A.body(90,8,0).rArm(-10,0).blade(-20,-20),Anim.Ease.LINEAR),
        k(RELEASE,SWEEP_A.at(0,-.45,0).body(0,25,0).rArm(-35,0).blade(-60,0)),
        k(2.07,SWEEP_A.at(0,-.45,0).body(0,25,0).rArm(-35,0).blade(-60,0),Anim.Ease.HOLD),
        k(2.47,KagenoClips.IDLE));
    @Override public Anim clip(int combo){return CLIP;}
    @Override public boolean walkLegs(float sec){return sec>=GRAY&&sec<COLOR;}
    @Override public float lifetime(){return 4.0f;}

    /** The world stops: a white pulse at the chest, rings racing out, then glints hanging frozen in the air until colour returns. */
    static void freeze(Fx c,EffectMesh m){
        float t=c.sec;Vec3d chest=c.pos.add(0,1.1,0);
        if(t>=GRAY&&t<GRAY+.5f){
            float u=Ease.unit(t,GRAY,GRAY+.5f);
            FxLib.flash(m,chest,1.2*(1-u)+.2,0xe8f4ff,1-Ease.smooth(t,GRAY+.03f,GRAY+.2f));
            FxLib.ring(m,new Vec3d(c.pos.x,c.origin.y+.05,c.pos.z),.4+6*H*Ease.out(t,GRAY,GRAY+.5f),.5,0xf4f8ff,.8f*(1-u));
            FxLib.ringPlane(m,chest,c.f,new Vec3d(0,1,0),.3+3*H*Ease.out(t,GRAY,GRAY+.4f),.3,0xf4f8ff,.7f*(1-Ease.unit(t,GRAY,GRAY+.4f)));
        }
        if(t>=GRAY+.1f&&t<COLOR+.15f){
            float a=Ease.smooth(t,GRAY+.1f,GRAY+.4f)*(1-Ease.smooth(t,COLOR,COLOR+.15f));
            for(int k=0;k<24;k++){
                double ang=Ease.hash(k*7+41)*Math.PI*2,r=(1+3*Ease.hash(k*5+42))*H,y=.3+2.2*H*Ease.hash(k*3+43),drift=(t-GRAY)*.08;
                Vec3d p=c.origin.add(Math.cos(ang)*r,y+drift,Math.sin(ang)*r);
                float tw=.6f+.4f*(float)Math.sin(t*(4+3*Ease.hash(k*11))+k);
                m.star(p,.05+.05*Ease.hash(k*13),k%3==0?0xffffff:0xd8f0ff,a*tw);
            }
        }
    }
    /** Every cut flashes where it crosses and throws a few glass-like shards. */
    static void cutFlash(Fx c,EffectMesh m){
        float t=c.sec;if(t<CUTS[0]||t>=RELEASE)return;var list=cuts(c);
        for(int i=0;i<4;i++){
            float u=(t-CUTS[i])/.28f;if(u<0||u>=1)continue;var cut=list.get(i);
            FxLib.flash(m,cut.centre(),.6*(1-u)+.1,0xf0f8ff,(1-Ease.smooth(u,.3f,1)));
            for(int k=0;k<6;k++){
                double az=Ease.hash(i*17+k*3)*Math.PI*2,el=(Ease.hash(i*19+k*5)-.4)*1.6,len=(.3+.5*Ease.hash(i*23+k*7))*H;
                Vec3d d=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
                Vec3d p=cut.centre().add(d.multiply(len*u*2));
                m.needle(p,p.add(d.multiply(.25)),.05,.5,0xffffff,(1-u)*(1-u));
            }
        }
    }
    /** One frozen cut: centre, direction, length, primary thickness. */
    record Cut(Vec3d centre,Vec3d dir,double length,double width){}
    static List<Cut> cuts(Fx c){
        List<Cut> out=new ArrayList<>();
        // Without a target the lines cross ≈0.75 character heights to the caster's right side.
        Vec3d at=c.targets.isEmpty()?c.origin.add(c.right.multiply(.75*H*.6)):c.targets.get(0).getLerpedPos(c.delta);
        double[][] spec={{-.6,.6,90,8,7.0,.55},{-.3,1.0,75,30,6.5,.45},{-.8,1.35,100,14,7.5,.6},{.6,.8,80,-25,6.0,.4}};
        for(int i=0;i<4;i++){
            double[] s=spec[i];double jitter=c.targets.isEmpty()?0:(Ease.hash(i*31+(int)c.cast.start())-.5)*40;
            double yaw=Math.toRadians(s[2]+jitter),el=Math.toRadians(s[3]);
            Vec3d dir=c.f.multiply(Math.cos(yaw)*Math.cos(el)).add(c.right.multiply(Math.sin(yaw)*Math.cos(el))).add(0,Math.sin(el),0);
            out.add(new Cut(at.add(c.right.multiply(s[0])).add(0,s[1],0),dir,s[4],s[5]));
        }
        return out;
    }
    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;
        freeze(c,m);cutFlash(c,m);
        if(t>=CUTS[0]&&t<RELEASE){
            var list=cuts(c);
            // White light during the gray phase; once colour returns the lavender-periwinkle glow shows.
            float tint=Ease.smooth(t,COLOR,COLOR+.1f);
            for(int i=0;i<4;i++){
                if(t<CUTS[i])continue;var cut=list.get(i);
                float grow=Ease.out(t,CUTS[i],CUTS[i]+.08f);double len=cut.length()*(.75+.35*grow);
                Vec3d a=cut.centre().subtract(cut.dir().multiply(len*.5)),b=cut.centre().add(cut.dir().multiply(len*.5));
                m.needle(a,b,cut.width()*1.9,.5,0xf0eeec,.45f*(1-tint));
                if(tint>0){m.needle(a,b,cut.width()*2.8,.5,0x8896d8,.4f*tint);m.needle(a,b,cut.width()*1.9,.5,0xcebbec,.6f*tint);}
                m.needle(a,b,cut.width(),.5,0xfffaff,1);
                // 1–3 thinner parallel strands, offset sideways, extending past the ends.
                Vec3d side=cut.dir().crossProduct(new Vec3d(0,1,0));if(side.lengthSquared()<1e-4)side=c.right;side=side.normalize();
                for(int k=1;k<=3;k++){
                    double off=(k-2)*.22+.1,ext=len*(.15+.15*k)*grow;Vec3d o=side.multiply(off).add(0,(k-2)*.12,0);
                    m.needle(a.add(o).subtract(cut.dir().multiply(ext*.5)),b.add(o).add(cut.dir().multiply(ext)),.06+.03*(3-k),.5,0xfffaff,.85f);
                }
            }
        }
        // Release: a white starburst at the crossing point, then the release line along the facing.
        Vec3d burst=c.targets.isEmpty()?c.origin.add(c.right.multiply(.45*H)).add(0,1.0,0):c.targets.get(0).getLerpedPos(c.delta).add(0,1.0,0);
        // Very opaque white flash cloud ≈2.5 × 1.5 character heights with a lavender-blue edge and a
        // ragged outline, 5 frames, then it collapses into the release line.
        if(t>=RELEASE&&t<RELEASE+.13f){
            float a=1-Ease.smooth(t,RELEASE+.05f,RELEASE+.13f);double s=1-.6*Ease.smooth(t,RELEASE+.05f,RELEASE+.13f);
            FxLib.ellipse(m,burst,1.6*H*s,1.0*H*s,0x596bd0,a*.35f);
            FxLib.ellipse(m,burst,1.4*H*s,.88*H*s,0xc7b7fb,a*.6f);
            FxLib.ellipse(m,burst,1.2*H*s,.72*H*s,0xfbfdf8,a);FxLib.ellipse(m,burst,.9*H*s,.55*H*s,0xffffff,a);
            for(int k=0;k<14;k++){double az=k*Math.PI/7+Ease.hash(k)*.3,len=(.7+.8*Ease.hash(k*3))*H*s;Vec3d d=c.right.multiply(Math.cos(az)).add(0,Math.sin(az)*.6,0);m.needle(burst,burst.add(d.multiply(len)),.3,.2,0xffffff,a);}
        }
        // Release shockwave: a ground ring racing out to 8 H, a vertical ring on the facing and a violet ground glow.
        if(t>=RELEASE&&t<RELEASE+.6f){
            float u=Ease.unit(t,RELEASE,RELEASE+.6f);Vec3d g=new Vec3d(burst.x,c.origin.y+.05,burst.z);
            FxLib.ring(m,g,.5+8*H*Ease.out(t,RELEASE,RELEASE+.6f),.7,0xe8e0ff,.85f*(1-u));
            FxLib.ringPlane(m,burst,c.right,new Vec3d(0,1,0),.4+1.8*H*Ease.out(t,RELEASE,RELEASE+.4f),.25,0xd8ccff,.7f*(1-Ease.unit(t,RELEASE,RELEASE+.4f)));
            FxLib.glow(m,g,2.5*H,0x7a50e8,.45f*(1-u));
        }
        if(t>=RELEASE+.05f&&t<RELEASE+.67f){
            float a=1-Ease.smooth(t,RELEASE+.3f,RELEASE+.67f);
            Vec3d from=c.origin.subtract(c.f.multiply(3*H)).add(0,.43*H,0),to=c.targets.isEmpty()?c.origin.add(c.f.multiply(3*H)).add(0,.43*H,0):c.targets.get(0).getLerpedPos(c.delta).add(c.f.multiply(2.5*H)).add(0,.43*H,0);
            m.glowLine(from,to,.09,0xbfb2f0,a);
            for(int k=0;k<4;k++)m.softDisc(from.lerp(to,.2+.2*k),.35,0xefe2f5,a*.5f);
        }
        // Petal shards burst out sideways and up, slow down, drift and slowly fall while fading.
        if(t>=RELEASE+.07f&&t<RELEASE+2.1f){
            for(int k=0;k<60;k++){
                float born=RELEASE+.07f+.3f*(float)Ease.hash(k*3),u=(t-born)/(1.0f+1.0f*(float)Ease.hash(k*5));if(u<0||u>=1)continue;
                double az=Ease.hash(k*7)*Math.PI*2,out=(2+1.2*Ease.hash(k*11))*H*(1-Math.pow(1-Math.min(1,u*2.2),2)),rise=(.6+1.2*Ease.hash(k*13))*Math.sin(Math.min(1,u*2)*Math.PI*.5)-u*u*.8;
                Vec3d p=burst.add(Math.cos(az)*out*.5,rise,Math.sin(az)*out*.5);
                double size=(.05+.15*Ease.hash(k*17))*H*(1-.6*u),spin=t*(4+5*Ease.hash(k*19))+k;
                Vec3d d=new Vec3d(Math.cos(spin),Math.sin(spin*.7),Math.sin(spin)).normalize().multiply(size);
                m.needle(p.subtract(d),p.add(d),size*.6*(.4+.6*Math.abs(Math.sin(spin*1.3))),.5,u<.6?0xfff5ff:0xe8cbf8,(1-u));
            }
        }
    }
}
