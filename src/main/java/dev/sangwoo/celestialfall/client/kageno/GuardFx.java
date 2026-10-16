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
    private static Vec3d sph(double t,double a){return new Vec3d(Math.cos(t)*Math.cos(a),Math.sin(t),Math.cos(t)*Math.sin(a));}
}
