package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/** Shared effect primitives for the Kageno skills: rings, flashes, converging motes, embers, columns, sword trails, bolts, glows. */
public final class FxLib {
    private FxLib(){}
    static final Vec3d UP=new Vec3d(0,1,0);

    /** Flat horizontal ring: a bright thin rim at {@code r} with a soft falloff {@code width} deep inside it. */
    public static void ring(EffectMesh m,Vec3d c,double r,double width,int rgb,float alpha){ringPlane(m,c,new Vec3d(1,0,0),new Vec3d(0,0,1),r,width,rgb,alpha);}
    /** The same ring in the plane spanned by {@code u} and {@code v}. */
    public static void ringPlane(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,double r,double width,int rgb,float alpha){
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
    public static void flash(EffectMesh m,Vec3d p,double r,int rgb,float alpha){
        if(alpha<=0||r<=0)return;
        m.softDisc(p,r,rgb,alpha*.45f);m.softDisc(p,r*.55,rgb,alpha*.7f);m.softDisc(p,r*.3,0xffffff,alpha);m.star(p,r*.9,0xffffff,alpha*.8f);
    }
    /** Motes spiralling into {@code target} from {@code r0} blocks out; each is born somewhere in [t0,t1] and takes 0.25 s. */
    public static void converge(EffectMesh m,Vec3d target,double r0,int n,float t,float t0,float t1,int rgb,int seed){
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
    public static void sparks(EffectMesh m,Vec3d p,int n,double len,double width,int rgb,float alpha,int seed,float grow){
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
    static void zig(EffectMesh m,Vec3d a,Vec3d b,int n,double width,int rgb,float alpha,int seed){
        Vec3d prev=a,d=b.subtract(a);double len=d.length();if(len<1e-4)return;
        Vec3d side=d.crossProduct(new Vec3d(0,1,0));if(side.lengthSquared()<1e-6)side=new Vec3d(1,0,0);side=side.normalize();Vec3d up=side.crossProduct(d).normalize();
        for(int i=1;i<=n;i++){
            double t=i/(double)n;Vec3d p=a.add(d.multiply(t));
            if(i<n)p=p.add(side.multiply((Ease.hash(seed*13+i)-.5)*len*.22)).add(up.multiply((Ease.hash(seed*7+i)-.5)*len*.12));
            m.glowLine(prev,p,width,rgb,alpha);prev=p;
        }
    }
    /**
     * Four-point star flare: a soft glow, four long rays tapering from the centre ({@code turn} degrees on screen, 45
     * for an X), four short ones between them and a white core. {@code r} is the ray length.
     */
    static void flare(EffectMesh m,Vec3d p,double r,int rgb,float alpha,double turn){
        if(alpha<=0||r<=0)return;
        Vec3d n=m.camera().subtract(p).normalize(),u=n.crossProduct(UP);if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(n).normalize();
        double a=Math.toRadians(turn);Vec3d x=u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a))),y=v.multiply(Math.cos(a)).subtract(u.multiply(Math.sin(a)));
        m.softDisc(p,r*.45,rgb,alpha*.5f);
        for(Vec3d d:new Vec3d[]{x,y,x.negate(),y.negate()}){m.needle(p,p.add(d.multiply(r)),r*.14,0,rgb,alpha*.95f);m.needle(p,p.add(d.multiply(r*.5)),r*.05,0,0xffffff,alpha);}
        for(Vec3d d:new Vec3d[]{x.add(y),x.subtract(y),y.subtract(x),x.add(y).negate()})m.needle(p,p.add(d.normalize().multiply(r*.3)),r*.06,0,rgb,alpha*.7f);
        m.softDisc(p,r*.18,0xffffff,alpha);
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
    // ───────────────────────────── effect layer helpers ─────────────────────────────

    /** A pulse that comes in over a frame at {@code at} and dies away with time constant {@code tau}. */
    public static float kick(float t,float at,float tau){return t<at-.01f?0:Ease.smooth(t,at-.01f,at+.015f)*(float)Math.exp(-Math.max(0,t-at-.015f)/tau);}
    /**
     * A big finisher's impact frame: in over two frames as the blow lands at {@code at}, held about three frames (white
     * shapes on dark, then over a frame and a half dark shapes on white, {@link #impactMode}), out over two frames.
     */
    static float impact(float t,float at){
        if(t<at-.01f||t>=at+.125f)return 0;
        return Ease.smooth(t,at-.01f,at+.02f)*(1-Ease.smooth(t,at+.085f,at+.125f));
    }
    static float impactMode(float t,float at){return Ease.smooth(t,at+.04f,at+.075f);}
    /** How much the screen-wide touches (impact frame, colour split, light shafts) reach a camera this far from {@code p}: all within 14 blocks, none past 26. */
    static float near(Fx c,Vec3d p){return (float)Math.max(0,Math.min(1,(26-c.camera.distanceTo(p))/12));}
    /**
     * Chunks of the floor block under {@code o}, {@code since} seconds after they were torn up: thrown out round it
     * ({@code out} blocks a second sideways, {@code up} upward), spinning, bouncing and settling, shrinking away from
     * {@code shrink} seconds. Only a picture: the world's blocks do not change.
     */
    public static void chunks(dev.sangwoo.celestialfall.client.vfx.FxSolids s,Vec3d o,float since,int n,int seed,double out,double up,double size,float shrink){
        double floor=s.groundY(o.add(0,.3,0));Vec3d at=new Vec3d(o.x,floor,o.z);
        var block=s.ground(at.add(0,-.5,0));if(block==null)return;
        for(int k=0;k<n;k++){
            float age=since-.012f*(float)Ease.hash(k*3+seed);if(age<0)continue;
            double sz=size*(.75+.6*Ease.hash(k*19+seed))*(1-Ease.smooth(age,shrink,shrink+.3f));
            double az=Ease.hash(k*7+seed)*Math.PI*2,r=.25+.5*Ease.hash(k*5+seed),speed=out*(.5+.5*Ease.hash(k*11+seed));
            Vec3d v=new Vec3d(Math.cos(az)*speed,up*(.7+.5*Ease.hash(k*13+seed)),Math.sin(az)*speed);
            Vec3d p=dev.sangwoo.celestialfall.client.vfx.FxSolids.flight(at.add(Math.cos(az)*r,.12,Math.sin(az)*r),v,floor+sz*.5,age);
            float spin=(float)((300+500*Ease.hash(k*23+seed))*.5*(1-Math.exp(-age/.5)));
            s.chunk(block,p,sz,spin+k*41,spin*.8f+k*13,spin*.5f);
        }
    }
    /**
     * The floor round {@code o} heaving in a ring that runs out from it at {@code speed} blocks a second ({@code amp}
     * high, out to {@code radius}), still within {@code hole} blocks of the middle; each block tips away from the middle.
     */
    static void heave(dev.sangwoo.celestialfall.client.vfx.FxSolids s,Vec3d o,float since,double radius,double amp,double speed,double width,double hole){
        double floor=s.groundY(o.add(0,.3,0));BlockPos base=BlockPos.ofFloored(o.x,floor-.5,o.z);int r=(int)Math.ceil(radius);
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            BlockPos pos=base.add(dx,0,dz);double ox=pos.getX()+.5-o.x,oz=pos.getZ()+.5-o.z,dist=Math.hypot(ox,oz);if(dist>radius)continue;
            double lift=dev.sangwoo.celestialfall.client.vfx.FxSolids.wave(dist,since,amp,speed,width)*Ease.smooth((float)dist,(float)hole,(float)(hole+.8));
            if(lift>.02){Vec3d away=dist<1e-3?new Vec3d(1,0,0):new Vec3d(ox/dist,0,oz/dist);s.lifted(pos,s.surface(pos),lift,(float)(lift*30),new Vec3d(-away.z,0,away.x));}
        }
    }
    /** A ring of anime dust puffs thrown out over the floor from {@code o} at {@code at}, from radius r0 out to r1 (cells for {@code KagenoFire.dust}). */
    public static void dustRing(Vec3d o,float t,float at,double r0,double r1,int n,double size,List<Vec3d> dust,List<float[]> puffs,int seed){
        for(int i=0;i<n;i++){
            float s=t-at-.03f*(float)Ease.hash(seed+i),life=.6f+.35f*(float)Ease.hash(seed+i*3);if(s<0||s>=life)continue;float u=s/life;
            double az=(i+Ease.hash(seed+i*5)*.6)*Math.PI*2/n,rad=r0+(r1-r0)*Ease.out(u,0,.7f);
            dust.add(o.add(Math.cos(az)*rad,.22+.45*u+.2*Ease.hash(seed+i*7),Math.sin(az)*rad));
            puffs.add(new float[]{(float)(size*(.3+.35*Ease.out(u,0,.5f))*(.8+.4*Ease.hash(seed+i*11))),.9f*Ease.smooth(s,0,.04f)*(1-Ease.smooth(u,.55f,1)),.6f*(1-u),u,seed+i*37});
        }
    }
}
