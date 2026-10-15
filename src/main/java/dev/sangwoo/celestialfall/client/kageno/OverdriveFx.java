package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Q "오버드라이브" aura, from the review: no cast animation; the body flashes white-hot for ≈0.25 s
 * while the scene brightens, then within ≈0.6 s a glowing right eye, a camera-facing field of thin
 * violet circuit traces behind the upper body, violet flame wisps licking off the hands, shoulders,
 * hips and feet, white sparkles on the clothes and violet motes at the legs. 1 stud = 0.36 blocks.
 */
final class OverdriveFx {
    private OverdriveFx(){}
    /** Seconds since the activation, or a large value when it was not seen (late join). */
    static float since(MinecraftClient mc,PlayerEntity p,float delta){
        Long at=StandClient.OVERDRIVE.get(p.getUuid());return at==null?99:(mc.world.getTime()-at+delta)/20f;
    }
    /** Body frame of a player: feet, facing and right (horizontal unit vectors). */
    record Body(Vec3d feet,Vec3d f,Vec3d right){
        Vec3d at(double fw,double up,double rt){return feet.add(f.multiply(fw)).add(0,up,0).add(right.multiply(rt));}
    }
    static Body body(PlayerEntity p,float delta){
        float yaw=MathHelper.lerpAngleDegrees(delta,p.prevBodyYaw,p.bodyYaw);Vec3d f=Vec3d.fromPolar(0,yaw);
        return new Body(p.getLerpedPos(delta),f,new Vec3d(-f.z,0,f.x));
    }
    /** Additive part: activation glow, right eye, flame wisps, sparkles and leg motes. */
    static void aura(EffectMesh m,MinecraftClient mc,PlayerEntity p,float delta,float now){
        float s=since(mc,p,delta);Body b=body(p,delta);float time=now/20f;
        // Activation: the whole body reads as a white silhouette with bloom, peaking at ≈0.1 s.
        if(s<.3f){
            float a=Ease.smooth(s,0,.06f)*(1-Ease.smooth(s,.12f,.3f));
            Vec3d mid=b.at(0,.95,0);
            BarrageSkill.ellipse(m,mid,1.2,1.6,0xf4ecff,a*.6f);
            for(int k=0;k<4;k++)BarrageSkill.ellipse(m,mid,.55-.04*k,1.1-.04*k,0xffffff,a);
        }
        // Right eye: white core, lavender falloff, a slight flare outward; brighter for the first 0.3 s.
        float eye=Ease.smooth(s,.08f,.2f);
        if(eye>0){
            float head=MathHelper.lerpAngleDegrees(delta,p.prevHeadYaw,p.headYaw);Vec3d hf=Vec3d.fromPolar(MathHelper.lerp(delta,p.prevPitch,p.getPitch()),head);
            Vec3d hr=new Vec3d(-hf.z,0,hf.x);if(hr.lengthSquared()<1e-4)hr=b.right();hr=hr.normalize();
            Vec3d o=p.getLerpedPos(delta).add(0,p.getStandingEyeHeight()+.1,0),e=o.add(hf.multiply(.27)).add(hr.multiply(.12));
            float boost=1+.6f*(1-Ease.smooth(s,.2f,.5f));
            m.softDisc(e,.18*boost,0xb7aff1,.55f*eye);m.softDisc(e,.13*boost,0xccbff6,.7f*eye);m.softDisc(e,.09,0xe8dcfb,.9f*eye);m.softDisc(e,.05,0xffffff,eye);
            m.needle(e.subtract(hr.multiply(.05)),e.add(hr.multiply(.3)),.06,.25,0xe8dcfb,.6f*eye);
        }
        // Tiny white glints on the shirt and arms, and violet motes around the legs.
        float rest=Ease.smooth(s,.3f,.6f);
        if(rest>0){
            int seed=(int)p.getUuid().getMostSignificantBits(),last=(int)Math.floor(time*20);
            for(int k=last-4;k<=last;k++){
                float born=k/20f,life=.1f+.1f*(float)Ease.hash(seed+k*13),u=(time-born)/life;if(u<0||u>=1)continue;
                double side=Ease.hash(seed+k*17)-.5;Vec3d pt=Math.abs(side)>.3?b.at(.08,.85+.55*Ease.hash(seed+k*19),side>0?.38:-.38):b.at(.14,.85+.6*Ease.hash(seed+k*19),side*.5);
                m.star(pt,.06,0xffffff,rest*(float)Math.sin(Math.PI*u));
            }
            for(int k=0;k<5;k++){
                double ang=time*1.3+k*1.26,r=.28+.08*Math.sin(time*2+k);
                Vec3d pt=b.feet().add(Math.cos(ang)*r,.1+.5*((time*.4+k*.21)%1),Math.sin(ang)*r);
                m.softDisc(pt,.05,0xa15cf0,rest*.8f);
            }
        }
    }
    /**
     * Violet flame wisps, ≈27 per second, each 0.25-0.4 s, born on a limb and drifting out and up.
     * Alpha-blended so they stay violet over a bright daytime sky.
     */
    static void flames(EffectMesh m,MinecraftClient mc,PlayerEntity p,float delta,float now){
        float s=since(mc,p,delta);Body b=body(p,delta);float time=now/20f;
        float flames=Ease.smooth(s,.3f,.6f);
        if(flames>0){
            float pulse=.75f+.25f*(float)Math.sin(time*Math.PI*2/.7f);
            int last=(int)Math.floor(time*27),seed=(int)p.getUuid().getLeastSignificantBits();
            for(int k=last-12;k<=last;k++){
                float born=k/27f,life=.25f+.15f*(float)Ease.hash(seed+k*3),u=(time-born)/life;if(u<0||u>=1)continue;
                Vec3d root=limb(b,(int)(Ease.hash(seed+k*5+1)*11));
                Vec3d out=root.subtract(b.at(0,root.y-b.feet().y,0));if(out.lengthSquared()<1e-4)out=b.right();out=out.normalize();
                double drift=(.4+.6*Ease.hash(seed+k*7+2))*2.2*(time-born);
                Vec3d base=root.add(out.multiply(drift*.6)).add(0,drift*.8,0);
                double size=(.26+.12*Ease.hash(seed+k*11+3))*(1-.45*u),flick=.8+.2*Math.sin(time*40+k);
                Vec3d tip=base.add(out.multiply(size*.4)).add(0,size*1.6*flick,0);
                float a=flames*pulse*(1-Ease.smooth(u,.55f,1))*Ease.smooth(u,0,.15f);
                m.needle(base,tip,size*1.4,.3,0x9d5ee8,a*.55f);m.needle(base,tip,size*.9,.3,0xc78af5,a*.8f);m.needle(base.add(0,size*.1,0),tip.add(0,-size*.3,0),size*.42,.3,0xf3c4ff,a*.9f);
            }
        }
    }
    /** Flame roots: hands (most often), shoulders, outer arms, hips and feet. */
    private static Vec3d limb(Body b,int i){
        return switch(i){
            case 0,1,2 -> b.at(.02,.76,.42);
            case 3,4 -> b.at(.02,.76,-.42);
            case 5 -> b.at(0,1.42,.36);
            case 6 -> b.at(0,1.42,-.36);
            case 7 -> b.at(0,1.08,i%2==0?.45:-.45);
            case 8 -> b.at(0,.82,.2);
            case 9 -> b.at(0,.06,.13);
            default -> b.at(0,.06,-.13);
        };
    }
    /**
     * Alpha-blended circuit-trace field: thin violet PCB traces with 90° turns on a camera-facing
     * plane behind the upper body (≈2.5 × 2 blocks), popping in and growing, pulsing at 1.3-2.6 Hz.
     */
    static void circuits(EffectMesh m,MinecraftClient mc,PlayerEntity p,float delta,float now){
        float s=since(mc,p,delta),in=Ease.smooth(s,.2f,.5f);if(in<=0)return;
        Body b=body(p,delta);float time=now/20f;
        Vec3d cam=m.camera(),centre=b.at(0,1.35,0),n=cam.subtract(centre).normalize();
        Vec3d u=n.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-4)u=b.right();u=u.normalize();Vec3d v=u.crossProduct(n).normalize();
        // Behind the body as seen from the camera, so the head and torso occlude it.
        Vec3d o=centre.subtract(n.multiply(.32));
        double cell=.12;int seed=(int)p.getUuid().getLeastSignificantBits()^0x3c3c;
        float global=.55f+.3f*(float)Math.sin(time*Math.PI*2*1.9)+.15f*(float)Math.sin(time*Math.PI*2*2.6+1);
        for(int i=0;i<34;i++){
            float period=.6f+.8f*(float)Ease.hash(seed+i*3),phase=(float)Ease.hash(seed+i*5)*period,cyc=((time+phase)%period)/period;
            float hold=.35f+.35f*(float)Ease.hash(seed+i*7);if(cyc>hold+.15f)continue;
            float grow=Ease.out(cyc,0,.15f),a=in*global*(1-Ease.smooth(cyc,hold,hold+.15f))*.75f;if(a<=.02f)continue;
            // Cycle-dependent layout so traces re-randomise each time they pop in again.
            int g=(int)Math.floor((time+phase)/period),h=seed+i*97+g*131;
            double x=Math.round((Ease.hash(h)-.5)*2*.95/cell)*cell,y=Math.round((Ease.hash(h+1)-.5)*2*.72/cell)*cell;
            if((x*x)/(1.0*1.0)+(y*y)/(.78*.78)>1)continue;
            int segs=2+(int)(Ease.hash(h+2)*3);boolean horizontal=Ease.hash(h+3)>.5;double total=0,shown=0;
            double[] lens=new double[segs];for(int k=0;k<segs;k++){lens[k]=cell*(1.5+3.5*Ease.hash(h+4+k));total+=lens[k];}
            for(int k=0;k<segs&&shown<total*grow;k++){
                double len=Math.min(lens[k],total*grow-shown),sign=Ease.hash(h+9+k)>.5?1:-1;
                double nx=x+(horizontal?sign*len:0),ny=y+(horizontal?0:sign*len);
                Vec3d a0=o.add(u.multiply(x)).add(v.multiply(y)),a1=o.add(u.multiply(nx)).add(v.multiply(ny));
                m.line(a0,a1,.075,0x6a3cff,a*.25f);m.line(a0,a1,.028,0x6a3cff,a);
                x=nx;y=ny;shown+=len;horizontal=!horizontal;
            }
            // Pad at the trace end.
            Vec3d pad=o.add(u.multiply(x)).add(v.multiply(y));double r=.03;
            m.quad(pad.add(u.multiply(r)).add(v.multiply(r)),pad.subtract(u.multiply(r)).add(v.multiply(r)),pad.subtract(u.multiply(r)).subtract(v.multiply(r)),pad.add(u.multiply(r)).subtract(v.multiply(r)),0x7a50ff,a);
        }
    }
    /** Screen brightening during the activation (≈1.75× for ≈0.25 s) for nearby viewers. */
    static float flash(MinecraftClient mc,PlayerEntity p,float delta,Vec3d camera){
        float s=since(mc,p,delta);if(s>=.3f||p.getLerpedPos(delta).squaredDistanceTo(camera)>28*28)return 0;
        return .38f*Ease.smooth(s,0,.05f)*(1-Ease.smooth(s,.18f,.3f));
    }
}
