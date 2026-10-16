package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;

/** Shared effect primitives for the Kageno skills: rings, flashes, converging motes, embers, columns, sword trails. */
final class FxLib {
    private FxLib(){}
    static final Vec3d UP=new Vec3d(0,1,0);

    /** Flat horizontal ring: a bright thin rim at {@code r} with a soft falloff {@code width} deep inside it. */
    static void ring(EffectMesh m,Vec3d c,double r,double width,int rgb,float alpha){ringPlane(m,c,new Vec3d(1,0,0),new Vec3d(0,0,1),r,width,rgb,alpha);}
    /** The same ring in the plane spanned by {@code u} and {@code v}. */
    static void ringPlane(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double r,double width,int rgb,float alpha){
        if(alpha<=0||r<=0)return;int n=Math.max(24,Math.min(96,(int)(r*10)));double ri=Math.max(0,r-width),ro=r+width*.18;
        for(int i=0;i<n;i++){
            double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
            Vec3d d0=u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))),d1=u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
            Vec3d o0=c.add(d0.multiply(r)),o1=c.add(d1.multiply(r));
            m.vertex(c.add(d0.multiply(ri)),rgb,0);m.vertex(c.add(d1.multiply(ri)),rgb,0);m.vertex(o1,rgb,alpha);m.vertex(o0,rgb,alpha);
            m.vertex(o0,0xffffff,alpha);m.vertex(o1,0xffffff,alpha);m.vertex(c.add(d1.multiply(ro)),rgb,0);m.vertex(c.add(d0.multiply(ro)),rgb,0);
        }
    }
    /** Soft glow lying flat on the ground. */
    static void glow(EffectMesh m,Vec3d c,double r,int rgb,float alpha){
        if(alpha<=0||r<=0)return;int n=24;
        for(int i=0;i<n;i++){
            double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
            m.vertex(c,rgb,alpha);m.vertex(c.add(Math.cos(a0)*r,0,Math.sin(a0)*r),rgb,0);m.vertex(c.add(Math.cos(a1)*r,0,Math.sin(a1)*r),rgb,0);m.vertex(c,rgb,alpha);
        }
    }
    /** Camera-facing flash: white core, coloured halo and a four-point glint. */
    static void flash(EffectMesh m,Vec3d p,double r,int rgb,float alpha){
        if(alpha<=0||r<=0)return;
        m.softDisc(p,r,rgb,alpha*.45f);m.softDisc(p,r*.55,rgb,alpha*.7f);m.softDisc(p,r*.3,0xffffff,alpha);m.star(p,r*.9,0xffffff,alpha*.8f);
    }
    /** Motes spiralling into {@code target} from {@code r0} blocks out; each is born somewhere in [t0,t1] and takes 0.25 s. */
    static void converge(EffectMesh m,Vec3d target,double r0,int n,float t,float t0,float t1,int rgb,int seed){
        if(t<t0||t>=t1+.05f)return;
        for(int k=0;k<n;k++){
            float born=t0+Math.max(0,t1-t0-.25f)*(float)Ease.hash(seed+k*3),u=(t-born)/.25f;if(u<0||u>=1)continue;
            double az=Ease.hash(seed+k*5)*Math.PI*2+u*2.4,el=(Ease.hash(seed+k*7)-.5)*2.4,r=r0*(1-u)*(1-u)*(.5+.5*Ease.hash(seed+k*11));
            Vec3d p=target.add(Math.cos(el)*Math.cos(az)*r,Math.sin(el)*r,Math.cos(el)*Math.sin(az)*r);
            float a=(float)Math.sin(Math.PI*Math.min(1,u*1.2+.2));
            m.softDisc(p,.05+.05*(1-u),rgb,a);
            if(u>.35)m.line(p,target.add(p.subtract(target).multiply(.7)),.015,rgb,.5f*u);
        }
    }
    /** Embers: {@code n} glowing dots rising from a disc of radius {@code r} to height {@code h}, cycling and twinkling. */
    static void embers(EffectMesh m,Vec3d base,double r,double h,int n,float t,float speed,double size,int rgb,float alpha,int seed){
        if(alpha<=0||t<0)return;
        for(int k=0;k<n;k++){
            float life=(float)(1.2+1.4*Ease.hash(seed+k*3))/speed,cyc=t/life+(float)Ease.hash(seed+k*5);int c=(int)Math.floor(cyc);float u=cyc-c;
            double az=Ease.hash(seed+k*7+c*13)*Math.PI*2,rr=r*Math.sqrt(Ease.hash(seed+k*11+c*17));
            Vec3d p=base.add(Math.cos(az)*rr+Math.sin(t*2.3+k)*.25,u*h*(.6+.4*Ease.hash(seed+k*19)),Math.sin(az)*rr+Math.cos(t*1.7+k)*.25);
            float tw=.55f+.45f*(float)Math.sin(t*(7+5*Ease.hash(seed+k*23))+k);
            m.softDisc(p,size*(1-.5*u),rgb,alpha*tw*(float)Math.sin(Math.PI*u));
        }
    }
    /** Radial spark needles out of {@code p}. */
    static void sparks(EffectMesh m,Vec3d p,int n,double len,double width,int rgb,float alpha,int seed,float grow){
        if(alpha<=0||grow<=0)return;
        for(int k=0;k<n;k++){
            double az=Ease.hash(seed+k*3)*Math.PI*2,el=(Ease.hash(seed+k*5)-.5)*2.6,l=len*(.5+.5*Ease.hash(seed+k*7))*grow;
            Vec3d d=new Vec3d(Math.cos(el)*Math.cos(az),Math.sin(el),Math.cos(el)*Math.sin(az));
            m.needle(p.add(d.multiply(l*.25)),p.add(d.multiply(l)),width,.3,rgb,alpha);
        }
    }
    /** Vertical light column: layered needles narrowing toward the top. */
    static void column(EffectMesh m,Vec3d base,double height,double width,int rgb,float alpha){
        if(alpha<=0||height<=0)return;Vec3d top=base.add(0,height,0);
        m.needle(base,top,width*2.2,.25,rgb,alpha*.3f);m.needle(base,top,width*1.2,.25,rgb,alpha*.6f);m.needle(base,top,width*.45,.2,0xffffff,alpha);
    }
    /** Sword trail: a ribbon swept by the blade (from..to blocks along it) over the last {@code span} seconds of {@code clip}. */
    static void trail(EffectMesh m,Fx c,Anim clip,float t,float span,int samples,double from,double to,int rgb,float alpha){
        if(alpha<=0)return;Vec3d[] a=new Vec3d[samples+1],b=new Vec3d[samples+1];
        for(int i=0;i<=samples;i++){
            float ts=Math.max(0,t-span*i/samples);
            var hb=KagenoClips.blade(c.origin,c.cast.yaw(),R6.poseOf(clip.frame(ts)));
            a[i]=hb[0].add(hb[1].multiply(from));b[i]=hb[0].add(hb[1].multiply(to));
        }
        for(int i=0;i<samples;i++){
            float a0=alpha*(1-(float)i/samples),a1=alpha*(1-(float)(i+1)/samples);
            m.vertex(a[i],rgb,a0*.35f);m.vertex(b[i],rgb,a0);m.vertex(b[i+1],rgb,a1);m.vertex(a[i+1],rgb,a1*.35f);
        }
    }
    /** Jagged bolt from a to b with {@code n} kinks. */
    static void zig(EffectMesh m,Vec3d a,Vec3d b,int n,double width,int rgb,float alpha,int seed){BarrageSkill.zig(m,a,b,n,width,rgb,alpha,seed);}
}
