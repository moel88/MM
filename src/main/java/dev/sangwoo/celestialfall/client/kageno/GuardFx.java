package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;

/**
 * F guard bubble: a white fresnel sphere, radius 3.5 studs, centred on the torso (3 studs up).
 * It appears at full size and opacity on the first guard frame; the alpha follows the measured
 * screen profile (clear centre, bright rim) with a soft halo just outside the silhouette.
 */
final class GuardFx {
    private GuardFx(){}
    static final double RADIUS=3.5*.36,CENTER=3.0*.36;
    /** Measured rim alpha against the view cosine N·V (1 = facing the camera, 0 = silhouette). */
    private static final double[] COS={1,.8,.74,.6,.49,.37,.1,0},ALPHA={0,.04,.08,.36,.55,.77,.91,.91};
    static float rim(double cos){
        cos=Math.max(0,Math.min(1,cos));
        for(int i=1;i<COS.length;i++)if(cos>=COS[i]){double t=(cos-COS[i])/(COS[i-1]-COS[i]);return (float)(ALPHA[i]+(ALPHA[i-1]-ALPHA[i])*t);}
        return (float)ALPHA[ALPHA.length-1];
    }
    static void bubble(EffectMesh m,Vec3d feet,Vec3d camera,float alpha,float time){
        if(alpha<=0)return;
        Vec3d o=feet.add(0,CENTER,0);
        // Irregular 3–4 Hz shimmer of the rim.
        float shimmer=.9f+.05f*(float)(Math.sin(time*.95)+Math.sin(time*1.53+1.7))*.5f;
        int rows=18,cols=36;
        for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
            double t0=-Math.PI/2+y*Math.PI/rows,t1=t0+Math.PI/rows,a0=x*2*Math.PI/cols,a1=a0+2*Math.PI/cols;
            Vec3d[] n={sph(t0,a0),sph(t0,a1),sph(t1,a1),sph(t1,a0)};
            Vec3d mid=n[0].add(n[2]).normalize();
            if(camera.subtract(o.add(mid.multiply(RADIUS))).dotProduct(mid)<0)continue;
            for(Vec3d k:n){
                Vec3d p=o.add(k.multiply(RADIUS));double cos=camera.subtract(p).normalize().dotProduct(k);
                m.vertex(p,0xffffff,rim(cos)*alpha*shimmer);
            }
        }
        // Soft halo just outside the silhouette (0.28 at 1.065 R, 0.24 at 1.13 R, gone at 1.19 R).
        Vec3d view=camera.subtract(o);double d=view.length();if(d<=RADIUS*1.05)return;view=view.multiply(1/d);
        Vec3d u=view.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(view).normalize();
        double sil=RADIUS*d/Math.sqrt(d*d-RADIUS*RADIUS);Vec3d c=o.add(view.multiply(RADIUS*RADIUS/d));
        double[] rs={1.0,1.065,1.13,1.19};float[] as={.5f,.28f,.24f,0};
        for(int i=0;i<48;i++){
            double a0=i*Math.PI/24,a1=(i+1)*Math.PI/24;
            for(int j=0;j<3;j++){
                Vec3d p00=c.add(u.multiply(Math.cos(a0)*sil*rs[j])).add(v.multiply(Math.sin(a0)*sil*rs[j])),p01=c.add(u.multiply(Math.cos(a1)*sil*rs[j])).add(v.multiply(Math.sin(a1)*sil*rs[j]));
                Vec3d p10=c.add(u.multiply(Math.cos(a1)*sil*rs[j+1])).add(v.multiply(Math.sin(a1)*sil*rs[j+1])),p11=c.add(u.multiply(Math.cos(a0)*sil*rs[j+1])).add(v.multiply(Math.sin(a0)*sil*rs[j+1]));
                m.vertex(p00,0xffffff,as[j]*alpha);m.vertex(p01,0xffffff,as[j]*alpha);m.vertex(p10,0xffffff,as[j+1]*alpha);m.vertex(p11,0xffffff,as[j+1]*alpha);
            }
        }
    }
    /** Additive part: a ring sweeping down the sphere as it forms, orbiting glints, a ground ring, a burst on release. */
    static void glints(EffectMesh m,Vec3d feet,float in,float out,float time){
        Vec3d o=feet.add(0,CENTER,0);float t=time/20f;
        if(!Float.isNaN(in)){
            if(in<.3f){
                double th=Math.PI*Ease.unit(in,0,.3f);
                FxLib.ring(m,o.add(0,RADIUS*Math.cos(th),0),RADIUS*Math.sin(th)+.05,.12,0xd8d0ff,.9f*(1-Ease.smooth(in,.2f,.3f)));
                FxLib.sparks(m,o,12,1.4,.04,0xf0ecff,1-Ease.smooth(in,.08f,.3f),3,Ease.out(in,0,.12f));
            }
            float a=Ease.smooth(in,.1f,.3f);
            for(int k=0;k<6;k++){
                double tilt=.4+.5*(k%3),ang=t*(2+.4*k)+k*1.05;
                Vec3d p=o.add(Math.cos(ang)*RADIUS*1.03,Math.sin(ang)*Math.sin(tilt)*RADIUS*1.03,Math.sin(ang)*Math.cos(tilt)*RADIUS*1.03);
                m.star(p,.05,0xffffff,a*(.6f+.4f*(float)Math.sin(t*9+k)));
            }
            FxLib.ring(m,feet.add(0,.04,0),RADIUS*1.05,.15,0xc8c0ff,.3f*a);
        }else if(!Float.isNaN(out)&&out<.2f){
            float u=Ease.unit(out,0,.2f);
            FxLib.ring(m,o,RADIUS*(1+.8*u),.2,0xe0d8ff,.8f*(1-u));
        }
    }
    /**
     * On the effect layer, as light: the bubble's rim glowing lavender (so it blooms), a bright line climbing the
     * sphere every 1.6 s, motes drifting up inside it, a flash and a shock ring over the floor as it goes up, and the
     * bubble bursting into glass shards as it drops.
     */
    static void light(EffectMesh m,Vec3d feet,Vec3d camera,float in,float out,float time){
        Vec3d o=feet.add(0,CENTER,0);float t=time/20f;
        float a=!Float.isNaN(in)?1:!Float.isNaN(out)?1-Ease.smooth(out,0,.15f):0;
        if(a>0){
            int rows=18,cols=36;
            for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
                double t0=-Math.PI/2+y*Math.PI/rows,t1=t0+Math.PI/rows,a0=x*2*Math.PI/cols,a1=a0+2*Math.PI/cols;
                Vec3d[] n={sph(t0,a0),sph(t0,a1),sph(t1,a1),sph(t1,a0)};
                Vec3d mid=n[0].add(n[2]).normalize();
                if(camera.subtract(o.add(mid.multiply(RADIUS))).dotProduct(mid)<0)continue;
                for(Vec3d k:n){Vec3d p=o.add(k.multiply(RADIUS));m.vertex(p,0xa898ff,rim(camera.subtract(p).normalize().dotProduct(k))*.4f*a);}
            }
            float cyc=(t%1.6f)/1.6f;
            if(cyc<.8f){double th=Math.PI*.46*(2*cyc/.8-1);FxLib.ring(m,o.add(0,RADIUS*Math.sin(th),0),RADIUS*Math.cos(th)*1.01,.08,0xe8e0ff,.5f*a*(float)Math.sin(Math.PI*cyc/.8));}
            for(int k=0;k<10;k++){
                float ph=(float)((t/1.4+k*.37)%1);double ang=k*2.4+t*.3,r=RADIUS*(.2+.45*Ease.hash(k*13+5));
                m.softDisc(o.add(Math.cos(ang)*r,RADIUS*(-.7+1.3*ph),Math.sin(ang)*r),.035,0xd8ccff,.7f*a*(float)Math.sin(Math.PI*ph));
            }
        }
        if(!Float.isNaN(in)&&in<.4f){
            m.softDisc(o,RADIUS*1.4,0xf0eaff,.5f*FxLib.kick(in,.01f,.12f));
            FxLib.ring(m,feet.add(0,.05,0),RADIUS*(.8+2.2*Ease.out(in,0,.35f)),.35,0xd8d0ff,.7f*(1-Ease.smooth(in,.1f,.4f)));
        }
        if(!Float.isNaN(out)&&out<.4f){
            float u=Ease.unit(out,0,.4f),fly=Ease.out(out,0,.3f);
            m.softDisc(o,RADIUS*1.2,0xe8e0ff,.45f*FxLib.kick(out,.01f,.08f));
            for(int i=0;i<18;i++){
                double az=Ease.hash(i*7+11)*Math.PI*2,el=Math.asin(2*Ease.hash(i*11+13)-1);
                Vec3d d=sph(el,az),side=d.crossProduct(new Vec3d(0,1,0));if(side.lengthSquared()<1e-4)side=new Vec3d(1,0,0);side=side.normalize();
                Vec3d up=side.crossProduct(d).normalize(),p=o.add(d.multiply(RADIUS*(1+1.6*fly))).add(0,-.8*out*out,0);
                double s=.15+.12*Ease.hash(i*17+19),spin=out*(8+6*Ease.hash(i*19+23))+i;
                Vec3d e1=side.multiply(Math.cos(spin)).add(up.multiply(Math.sin(spin))).multiply(s),e2=side.multiply(Math.cos(spin+2.1)).add(up.multiply(Math.sin(spin+2.1))).multiply(s*.8),e3=e1.add(e2).multiply(-.6);
                m.triangle(p.add(e1),p.add(e2),p.add(e3),0xb8a8ff,.7f*(1-u));
                m.triangle(p.add(e1.multiply(.5)),p.add(e2.multiply(.5)),p.add(e3.multiply(.5)),0xffffff,.9f*(1-u));
            }
        }
    }
    private static Vec3d sph(double t,double a){return new Vec3d(Math.cos(t)*Math.cos(a),Math.sin(t),Math.cos(t)*Math.sin(a));}
}
