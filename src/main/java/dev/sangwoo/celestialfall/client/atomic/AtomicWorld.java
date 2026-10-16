package dev.sangwoo.celestialfall.client.atomic;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.AtomicClient;
import dev.sangwoo.celestialfall.client.EffectMesh;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.*;

/**
 * G ("I am Atomic") world effects, timed in seconds after the first skill frame of the review
 * footage: cast flash, circuit floor and sky, light pillars, needle flares, wisps, orbs, the
 * ribbon vortex and X glint, the violet void frame, the blast interior, the explosion seen from
 * outside (the tower of light, its smoke ring, spikes, debris, shockwaves and the dissipation) and the crater.
 * Nothing here edits a block.
 */
public final class AtomicWorld {
    private static final Map<UUID,AtomicScene> SCENES=new HashMap<>();
    private static final float[] PULSES={.367f,1.437f,2.437f,3.457f,4.387f,5.437f};
    private static final float PULSE_LEN=.32f;
    private static final double DECAL=14.5,SKY_RADIUS=30,SKY_TOP=46,STUD=AtomicScene.STUD;
    /** The dust and embers are gone a little before the cast ends (27.6 s). */
    static final float BLAST_END=27.4f;
    private AtomicWorld() {}

    static AtomicScene scene(AtomicClient.Cast c) { return SCENES.computeIfAbsent(c.id,k->new AtomicScene(c.id,c.origin,c.yaw,c.radius)); }
    static double h(double n) { double x=Math.sin(n*12.9898+78.233)*43758.5453;return x-Math.floor(x); }
    static float smooth(float a,float b,float c) { return AtomicLook.smooth(a,b,c); }
    static float out(float a,float b,float c) { float t=1-AtomicLook.unit(a,b,c);return 1-t*t*t; }
    /** Fades effects that come within a few blocks of the camera so they never fill the view. */
    static float near(Vec3d cam,Vec3d p,double inner,double outer) {
        double d=Math.hypot(cam.x-p.x,cam.z-p.z);return (float)Math.max(0,Math.min(1,(d-inner)/(outer-inner)));
    }

    public static void render(WorldRenderContext ctx) {
        var mc=MinecraftClient.getInstance();
        if(mc.world==null||AtomicClient.CASTS.isEmpty()){SCENES.clear();return;}
        SCENES.keySet().retainAll(AtomicClient.CASTS.keySet());
        if(!AtomicShader.ready())return;
        float delta=ctx.tickDelta();Vec3d cam=ctx.camera().getPos();
        Matrix4f rot=ctx.matrixStack().peek().getPositionMatrix();
        Matrix4f world=new Matrix4f(rot).translate((float)-cam.x,(float)-cam.y,(float)-cam.z);
        var dim=mc.world.getRegistryKey().getValue();
        List<AtomicClient.Cast> casts=new ArrayList<>();
        for(var c:AtomicClient.CASTS.values())if(c.dimension.equals(dim)&&c.origin.squaredDistanceTo(cam)<640*640)casts.add(c);
        if(casts.isEmpty())return;
        try{
            // The opaque crater slabs go first so the translucent blast layers can blend over them.
            for(var c:casts){
                var s=scene(c);float sec=c.age(delta)/20f;s.ground.refresh(mc.world);
                if(sec>=7.0f)crater(ctx,s,sec,mc);
            }
            RenderSystem.enableDepthTest();AtomicShader.premultiplied();
            for(var c:casts){
                var s=scene(c);float sec=c.age(delta)/20f;
                if(sec<AtomicLook.PINK+.06f)chant(s,sec,rot,world,cam,mc.world);
                if(sec>=AtomicLook.PINK&&sec<AtomicLook.BEAM_END)blast(s,sec,rot,world,cam,mc.world);
                if(sec>=AtomicLook.IMPACT&&sec<BLAST_END)explosion(s,sec,rot,world,cam,mc.world);
            }
            // The violet void replaces the whole view for three frames, so it is drawn last over everything.
            for(var c:casts){
                float sec=c.age(delta)/20f;
                if(AtomicLook.frame(sec)==1&&c.origin.squaredDistanceTo(cam)<120*120){voidFrame(ctx,scene(c),sec,rot,world,cam,mc.world);break;}
            }
        }finally{
            RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
        }
    }

    // ───────────────────────────── chant (0 → 5.59 s) ─────────────────────────────

    private static void chant(AtomicScene s,float sec,Matrix4f rot,Matrix4f world,Vec3d cam,ClientWorld w) {
        if(sec>=AtomicLook.REVEAL_START&&sec<AtomicLook.IMPACT)decal(s,sec,rot,cam,0,1);
        // The last white impact frame and the first pink frames show the floor faintly in pink.
        if(sec>=AtomicLook.WHITE_LAST&&sec<AtomicLook.PINK+.06f)decal(s,sec,rot,cam,2,.6f*(1-AtomicLook.unit(sec,AtomicLook.PINK,AtomicLook.PINK+.06f)));
        if(sec>=AtomicLook.IMPACT)return;
        if(sec<.034f)castFlash(s,sec,rot,cam,w);
        if(sec>=AtomicLook.REVEAL_START)sky(s,sec,rot,cam);
        pillars(s,sec,rot,cam,w);
        flares(s,sec,rot,cam,w);
        orbs(s,sec,rot,cam);
        var b=Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        var m=new EffectMesh(b,world,cam);
        wisps(m,s,sec,w);ribbons(m,s,sec);glint(m,s,sec,cam);sparkles(m,s,sec);
        BufferRenderer.drawWithGlobalProgram(b.end());
        AtomicShader.premultiplied();
    }

    /** Pattern index: the trace layout is re-randomised at the end of every ground pulse. */
    static int pattern(float sec) { int n=0;for(float p:PULSES)if(sec>=p+PULSE_LEN)n++;return n; }
    /** Pulse wash radius in blocks (≈60 studs/s), or -1 between pulses. */
    static float wash(float sec) {
        for(float p:PULSES)if(sec>=p&&sec<p+PULSE_LEN)return (float)Math.max(.01,60*STUD*(sec-p-.04));
        return -1;
    }
    /** Circuit decal on the block tops around the cast; tone 0 white, 1 magenta (void), 2 pink. */
    static void decal(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,int tone,float alpha) {
        if(alpha<=0)return;
        float grow=sec<AtomicLook.REVEAL_END?.25f+.75f*out(sec,AtomicLook.REVEAL_START,AtomicLook.REVEAL_END):1;
        float opacity=alpha*(sec<AtomicLook.REVEAL_END?.3f+.7f*out(sec,AtomicLook.REVEAL_START,AtomicLook.REVEAL_END):1);
        var b=AtomicShader.begin(rot,cam,AtomicShader.CIRCUIT,sec,s.seed+pattern(Math.min(sec,AtomicLook.LAST_CHANT))*7.13f,grow,(float)DECAL,tone==0?wash(sec):-1,tone);
        int ox=(int)Math.floor(s.origin.x),oz=(int)Math.floor(s.origin.z),r=s.ground.radius();
        float[] u=new float[4],v=new float[4];
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(dx*dx+dz*dz>(DECAL+1.5)*(DECAL+1.5))continue;
            float y=s.ground.at(ox+dx,oz+dz);if(Float.isNaN(y))continue;
            double x0=ox+dx,z0=oz+dz,yy=y+.012;
            u[0]=u[3]=(float)(x0-s.origin.x);u[1]=u[2]=(float)(x0+1-s.origin.x);
            v[0]=v[1]=(float)(z0-s.origin.z);v[2]=v[3]=(float)(z0+1-s.origin.z);
            b.quad(new Vec3d(x0,yy,z0),new Vec3d(x0+1,yy,z0),new Vec3d(x0+1,yy,z0+1),new Vec3d(x0,yy,z0+1),u,v,0xffffff,opacity);
        }
        AtomicShader.end(b);
    }
    /** First skill frame only: white ground disc (12 studs) and three sky beams. */
    static void castFlash(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,ClientWorld w) {
        double y=s.groundY(w,s.origin.x,s.origin.z)+.02;
        var b=AtomicShader.begin(rot,cam,AtomicShader.DISC,sec,0,0,0,0,0);
        b.flat(new Vec3d(s.origin.x,y,s.origin.z),12*STUD,0xffffff,1);
        AtomicShader.end(b);
        b=AtomicShader.begin(rot,cam,AtomicShader.BEAM,sec,0,0,0,0,0);
        for(int side=-1;side<=1;side++){
            Vec3d base=s.origin.add(s.right.multiply(side*9.8*STUD));
            double from=side==0?1.95:.35;
            b.upright(new Vec3d(base.x,y+from,base.z),70,side==0?.95:1.15,0xffffff,1);
        }
        AtomicShader.end(b);
    }
    /** Giant circuit traces on a translucent wall and ceiling around the area. */
    static void sky(AtomicScene s,float sec,Matrix4f rot,Vec3d cam) {
        float reveal=out(sec,AtomicLook.REVEAL_START,AtomicLook.REVEAL_END+.01f);
        var b=AtomicShader.begin(rot,cam,AtomicShader.SKY,sec,s.seed,reveal,3,0,0);
        int n=72;double y0=s.origin.y+1,y1=s.origin.y+SKY_TOP;
        float[] u=new float[4],v=new float[4];
        for(int i=0;i<n;i++){
            double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
            Vec3d p0=s.origin.add(Math.cos(a0)*SKY_RADIUS,0,Math.sin(a0)*SKY_RADIUS),p1=s.origin.add(Math.cos(a1)*SKY_RADIUS,0,Math.sin(a1)*SKY_RADIUS);
            u[0]=u[3]=(float)(a0*SKY_RADIUS);u[1]=u[2]=(float)(a1*SKY_RADIUS);v[0]=v[1]=0;v[2]=v[3]=(float)(y1-y0);
            b.quad(new Vec3d(p0.x,y0,p0.z),new Vec3d(p1.x,y0,p1.z),new Vec3d(p1.x,y1,p1.z),new Vec3d(p0.x,y1,p0.z),u,v,0xffffff,1);
        }
        // Ceiling: the same traces in plan view, without the wall's height fade.
        AtomicShader.end(b);
        b=AtomicShader.begin(rot,cam,AtomicShader.SKY,sec,s.seed+5,reveal,-1000,0,0);
        for(int i=0;i<n;i++){
            double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
            Vec3d c=new Vec3d(s.origin.x,y1,s.origin.z);
            Vec3d p0=c.add(Math.cos(a0)*SKY_RADIUS,0,Math.sin(a0)*SKY_RADIUS),p1=c.add(Math.cos(a1)*SKY_RADIUS,0,Math.sin(a1)*SKY_RADIUS);
            u[0]=u[1]=0;v[0]=v[1]=0;
            u[2]=(float)(p1.x-c.x);v[2]=(float)(p1.z-c.z);u[3]=(float)(p0.x-c.x);v[3]=(float)(p0.z-c.z);
            b.quad(c,c,p1,p0,u,v,0xffffff,1);
        }
        AtomicShader.end(b);
    }
    static int spawned(AtomicScene s,float sec) { int n=0;for(var p:s.pillars)if(p.spawn()<=sec)n++;return n; }
    static Vec3d pillarBase(AtomicScene s,AtomicScene.Pillar p,ClientWorld w) {
        double x=s.origin.x+p.x(),z=s.origin.z+p.z();return new Vec3d(x,s.groundY(w,x,z),z);
    }
    /** Thin infinite light shafts rising around the plaza edge; half drop out before the flash. */
    static void pillars(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,ClientWorld w) {
        if(sec<.317f)return;
        var b=AtomicShader.begin(rot,cam,AtomicShader.PILLAR,sec,0,0,0,0,0);
        float late=1-.5f*smooth(sec,5.33f,AtomicLook.LAST_CHANT);
        for(var p:s.pillars){
            if(p.spawn()>sec||sec>=p.drop())continue;
            float rise=out(sec,p.spawn(),p.spawn()+.15f);
            Vec3d base=pillarBase(s,p,w);
            float a=(.86f+.14f*(float)Math.sin(sec*7.3+p.phase()))*late*near(cam,base,1.5,4);
            b.upright(base,90*rise,1.0,0xffffff,a);
        }
        AtomicShader.end(b);
    }
    private static final float[] FLARE_T={.217f,.317f,.417f,.517f,1.27f,1.97f,2.07f,5.27f,5.53f,5.59f};
    private static final float[] FLARE_N={0,5,20,52,60,70,75,85,40,0};
    static int flareCount(float sec) {
        if(sec<FLARE_T[0]||sec>=FLARE_T[FLARE_T.length-1])return 0;
        for(int i=1;i<FLARE_T.length;i++)if(sec<FLARE_T[i])return Math.round(FLARE_N[i-1]+(FLARE_N[i]-FLARE_N[i-1])*(sec-FLARE_T[i-1])/(FLARE_T[i]-FLARE_T[i-1]));
        return 0;
    }
    /** Spindle flares standing at the pillar bases, re-randomised every couple of frames. */
    static void flares(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,ClientWorld w) {
        int count=flareCount(sec),avail=spawned(s,sec);if(count<=0||avail<=0)return;
        long step=(long)Math.floor(sec*30);
        var b=AtomicShader.begin(rot,cam,AtomicShader.FLARE,sec,0,0,0,0,0);
        for(int k=0;k<count;k++){
            double seed=step*131.7+k*7.31+s.seed;
            var p=s.pillars[(int)(h(seed)*avail)%avail];
            Vec3d base=pillarBase(s,p,w).add((h(seed+1)-.5)*.5,0,(h(seed+2)-.5)*.5);
            double height=(2.5+6.5*h(seed+3))*STUD,width=(.5+.5*h(seed+4))*STUD;
            b.upright(base,height,width*2.4,0xffffff,(float)(.6+.4*h(seed+5))*near(cam,base,1.5,4));
        }
        AtomicShader.end(b);
    }
    /** Soft white light balls drifting up and sideways. */
    static void orbs(AtomicScene s,float sec,Matrix4f rot,Vec3d cam) {
        if(sec<.467f)return;
        float in=smooth(sec,.467f,.8f),t=sec-.467f;
        var b=AtomicShader.begin(rot,cam,AtomicShader.ORB,sec,0,0,0,0,0);
        for(int k=0;k<s.orbs.length;k++){
            var o=s.orbs[k];
            float cyc=t/o.life()+o.phase();int n=(int)Math.floor(cyc);float u=cyc-n;
            double a=o.angle()+h(n*3.1+k)*2.4,r=o.radius()*(.7+.6*h(n*5.7+k)),y0=o.height();
            Vec3d p=s.origin.add(Math.cos(a)*r+o.vx()*u*o.life(),y0+o.vy()*u*o.life(),Math.sin(a)*r+o.vz()*u*o.life());
            b.facing(p,o.size()*1.6,0xd8ccff,in*(float)Math.sqrt(Math.max(0,Math.sin(Math.PI*u)))*near(cam,p,1.5,3.5));
        }
        AtomicShader.end(b);
    }
    static void sparkles(EffectMesh m,AtomicScene s,float sec) {
        if(sec<.467f)return;float in=smooth(sec,.467f,.8f);
        for(int k=0;k<90;k++){
            double a=h(k*1.7+s.seed)*Math.PI*2,r=1+13*Math.sqrt(h(k*2.9+s.seed)),y=(h(k*4.3)*5+sec*(.4+h(k*5.1)))%6.5;
            float tw=(float)Math.pow(Math.max(0,Math.sin(sec*(6+h(k*6.1)*6)+k)),4);
            m.star(s.origin.add(Math.cos(a)*r,y,Math.sin(a)*r),.07+.05*h(k*7.7),0xf2eaff,in*tw);
        }
    }
    /** Short violet lightning zigzags at the pillar bases, in the air and around the caster, redrawn every frame. */
    static void wisps(EffectMesh m,AtomicScene s,float sec,ClientWorld w) {
        if(sec<.10f)return;long step=(long)Math.floor(sec*40);int avail=Math.max(1,spawned(s,sec));
        for(int k=0;k<36;k++){
            double seed=step*97.13+k*13.7+s.seed;int kind=k%4;Vec3d p;
            if(kind<2&&sec>=.317f){var pl=s.pillars[(int)(h(seed)*avail)%avail];p=pillarBase(s,pl,w).add((h(seed+1)-.5)*1.2,h(seed+2)*1.5,(h(seed+3)-.5)*1.2);}
            else if(kind==2){double a=h(seed+4)*Math.PI*2,r=(25+15*h(seed+5))*STUD;p=s.origin.add(Math.cos(a)*r,2+4*h(seed+6),Math.sin(a)*r);}
            else{double a=h(seed+7)*Math.PI*2,r=.4+1.2*h(seed+8);p=s.origin.add(Math.cos(a)*r,.2+2*h(seed+9),Math.sin(a)*r);}
            double len=(1+2*h(seed+10))*STUD/4;
            for(int i=0;i<4;i++){
                double el=Math.toRadians(-40+120*h(seed+20+i)),az=h(seed+30+i)*Math.PI*2;
                Vec3d q=p.add(Math.cos(el)*Math.cos(az)*len,Math.sin(el)*len,Math.cos(el)*Math.sin(az)*len);
                m.glowLine(p,q,.018,0xb090f0,.85f);p=q;
            }
        }
    }
    /** Crackling energy ribbons above the raised hands; they converge into the X glint before the flash. */
    static void ribbons(EffectMesh m,AtomicScene s,float sec) {
        if(sec<.10f||sec>=5.457f)return;
        long step=(long)Math.floor(sec*40);float in=smooth(sec,.10f,.3f),conv=smooth(sec,5.327f,5.457f);
        for(int k=0;k<11;k++){
            double seed=step*61.7+k*17.3+s.seed;boolean wide=k>=9;
            double h0=wide?3+4*h(seed):2+3.6*h(seed),rho=wide?3.5+3.5*h(seed+1):.3+1.15*h(seed+1);
            double th0=h(seed+2)*Math.PI*2,sweep=wide?.8+h(seed+3):1.5+3*h(seed+3),rise=wide?2.5*(h(seed+4)-.5):-.6+2.2*h(seed+4);
            h0=h0+(3.85-h0)*conv;rho*=1-.75*conv;
            Vec3d prev=null;
            for(int i=0;i<=16;i++){
                double t=i/16.,th=th0+sweep*t,r=rho*(wide?1:.55+.45*Math.sin(Math.PI*t));
                Vec3d p=s.origin.add(Math.cos(th)*r,h0+rise*t,Math.sin(th)*r);
                if(prev!=null)m.glowLine(prev,p,.032,0x929fe6,in*(1-conv)*.9f);
                prev=p;
            }
        }
    }
    /** Two crossing curved strokes ≈5.5 studs above the head (150.06 → 150.31 s). */
    static void glint(EffectMesh m,AtomicScene s,float sec,Vec3d cam) {
        if(sec<5.327f||sec>=AtomicLook.IMPACT)return;
        float a=smooth(sec,5.327f,5.457f);
        Vec3d c=s.origin.add(0,3.85,0),view=cam.subtract(c).normalize();
        Vec3d u=view.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-4)u=s.right;u=u.normalize();
        Vec3d v=u.crossProduct(view).normalize();
        for(int k=-1;k<=1;k+=2){
            double ang=Math.toRadians(90+k*38);
            Vec3d d=u.multiply(Math.cos(ang)).add(v.multiply(Math.sin(ang))),n=u.multiply(-Math.sin(ang)).add(v.multiply(Math.cos(ang)));
            Vec3d prev=null;
            for(int i=0;i<=10;i++){
                double t=i/10.-.5;
                Vec3d p=c.add(d.multiply(t*1.3*a)).add(n.multiply(k*.12*(1-4*t*t)));
                if(prev!=null)m.glowLine(prev,p,.045*(1-Math.abs(t)),0xe8e0ff,a);
                prev=p;
            }
        }
    }

    // ───────────────────────────── impact: violet void (5.590 → 5.640 s) ─────────────────────────────

    static void voidFrame(WorldRenderContext ctx,AtomicScene s,float sec,Matrix4f rot,Matrix4f world,Vec3d cam,ClientWorld w) {
        RenderSystem.disableDepthTest();
        var camera=ctx.camera();var look=camera.getHorizontalPlane();var up=camera.getVerticalPlane();
        Vec3d fwd=new Vec3d(look.x(),look.y(),look.z()),vu=new Vec3d(up.x(),up.y(),up.z()),side=fwd.crossProduct(vu).normalize();
        Matrix4f proj=ctx.projectionMatrix();double d=.2,hh=d/proj.m11(),hw=d/proj.m00();
        Vec3d centre=cam.add(fwd.multiply(d));
        java.util.function.BiFunction<Double,Double,Vec3d> at=(x,y)->centre.add(side.multiply(x*hw*1.05)).add(vu.multiply(y*hh*1.05));
        var b=Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var m=new EffectMesh(b,world,cam);
        // Dense dark-purple fog: lighter at the top, darker at the horizon, the floor a little pinker.
        double[] ys={1,.25,-.12,-1};int[] cs={0x7a5fae,0x5c3790,0x4f2680,0x6a2aa0};
        for(int i=0;i<3;i++){
            Vec3d a0=at.apply(-1.0,ys[i]),a1=at.apply(1.0,ys[i]),b1=at.apply(1.0,ys[i+1]),b0=at.apply(-1.0,ys[i+1]);
            m.vertex(a0,cs[i],1);m.vertex(a1,cs[i],1);m.vertex(b1,cs[i+1],1);m.vertex(b0,cs[i+1],1);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
        // Light shafts falling from the top centre toward the lower right.
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(b,world,cam);
        Vec3d src=at.apply(-.05,1.3);
        for(int k=0;k<5;k++){
            double ang=Math.toRadians(-92+k*9+h(k*3.3)*5),wdt=Math.toRadians(1.2+1.8*h(k*5.1)),len=2.2;
            Vec3d e0=at.apply(-.05+Math.cos(ang-wdt)*len,1.3+Math.sin(ang-wdt)*len),e1=at.apply(-.05+Math.cos(ang+wdt)*len,1.3+Math.sin(ang+wdt)*len);
            m.vertex(src,0xa088d8,.08f);m.vertex(src,0xa088d8,.08f);m.vertex(e1,0x8a70c8,0);m.vertex(e0,0x8a70c8,0);
        }
        // Bright magenta wedge on the ground from the caster's spot toward the viewer.
        Vec3d toCam=new Vec3d(cam.x-s.origin.x,0,cam.z-s.origin.z);double dist=toCam.length();
        if(dist>1.5){
            Vec3d dir=toCam.multiply(1/dist),perp=new Vec3d(-dir.z,0,dir.x);
            double y=s.groundY(w,s.origin.x,s.origin.z)+.03;Vec3d apex=new Vec3d(s.origin.x,y,s.origin.z);
            Vec3d base=apex.add(dir.multiply(dist*1.4));
            m.vertex(apex,0xf266f5,.95f);m.vertex(apex,0xf266f5,.95f);
            m.vertex(base.add(perp.multiply(dist*.7)),0xe945f8,.8f);m.vertex(base.subtract(perp.multiply(dist*.7)),0xe945f8,.8f);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
        // The radial circuit traces glow magenta on the fogged floor.
        AtomicShader.premultiplied();
        decal(s,sec,rot,cam,1,1);
        RenderSystem.enableDepthTest();
    }

    // ───────────────────────────── blast interior (5.89 → 22.07 s) ─────────────────────────────

    static void blast(AtomicScene s,float sec,Matrix4f rot,Matrix4f world,Vec3d cam,ClientWorld w) {
        float t=sec-AtomicLook.PINK;
        float columns=smooth(sec,21.24f,21.30f)*(1-smooth(sec,21.62f,21.72f));
        float vee=smooth(sec,21.62f,21.80f),gone=smooth(sec,21.80f,21.87f);
        double y0=s.origin.y-2;
        var b=AtomicShader.begin(rot,cam,AtomicShader.CURTAIN,sec,s.seed,0,0,0,0);
        float in=smooth(sec,AtomicLook.PINK,AtomicLook.PINK+.1f);
        float outside=smooth((float)Math.hypot(cam.x-s.origin.x,cam.z-s.origin.z),(float)(56*s.scale),(float)(84*s.scale));
        for(int k=0;k<s.curtains.length;k++){
            var c=s.curtains[k];
            float a=c.alpha()*in*(1-gone)*(c.extra()?columns:1)*(c.color()==0x0c0530?1-outside:1);
            if(a<=.003f)continue;
            double th=c.angle()+c.spin()*t,tilt=c.tilt()*(1+columns*.6);
            double rb=c.radius()*(1-.92*vee),rt=Math.max(.6,c.radius()-c.height()*Math.tan(tilt))*(1-vee)+(c.radius()+6)*vee;
            Vec3d base=new Vec3d(s.origin.x+Math.cos(th)*rb,y0,s.origin.z+Math.sin(th)*rb);
            Vec3d top=new Vec3d(s.origin.x+Math.cos(th)*rt,y0+c.height(),s.origin.z+Math.sin(th)*rt);
            b.strip(base,top,c.width(),c.color(),a);
        }
        AtomicShader.end(b);
        // Swirl rings, lightning tendrils and spiral arcs over the crater, random every frame.
        if(sec<7.0f||sec>=AtomicLook.COLLAPSE)return;
        var buffer=Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
        buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var m=new EffectMesh(buffer,world,cam);
        long step=(long)Math.floor(sec*45);float fade=smooth(sec,7.0f,7.6f);
        for(int k=0;k<4;k++){
            double seed=step*71.3+k*11.1+s.seed,r=(13+13*h(seed))*STUD,y=s.origin.y+.3+2.2*h(seed+1);
            double tilt=Math.toRadians((h(seed+2)-.5)*16),start=h(seed+3)*Math.PI*2,sweep=1.5+3.5*h(seed+4);
            Vec3d prev=null;
            for(int i=0;i<=24;i++){
                double a=start+sweep*i/24.;
                Vec3d p=new Vec3d(s.origin.x+Math.cos(a)*r,y+Math.sin(a)*Math.sin(tilt)*r*.3,s.origin.z+Math.sin(a)*r*.92);
                if(prev!=null)m.glowLine(prev,p,.18+.12*h(seed+5),0xd8c8ff,.8f*fade);
                prev=p;
            }
        }
        for(int k=0;k<6;k++){
            double seed=step*37.9+k*19.7+s.seed,a=h(seed)*Math.PI*2,r=(8+48*h(seed+1))*STUD;
            Vec3d p=new Vec3d(s.origin.x+Math.cos(a)*r,s.origin.y,s.origin.z+Math.sin(a)*r);double height=4+10*h(seed+2);
            for(int i=0;i<8;i++){
                Vec3d q=p.add((h(seed+10+i)-.5)*1.2,height/8,(h(seed+20+i)-.5)*1.2);
                m.glowLine(p,q,.1,0xcbb8ff,.75f*fade);
                if(i==3||i==5){Vec3d br=q.add((h(seed+30+i)-.5)*2,1+h(seed+40+i)*1.5,(h(seed+50+i)-.5)*2);m.glowLine(q,br,.06,0xcbb8ff,.6f*fade);}
                p=q;
            }
        }
        for(int k=0;k<2;k++){
            double seed=step*23.1+k*41.3+s.seed,r=(22+33*h(seed))*STUD,y=s.origin.y+3+12*h(seed+1);
            double start=h(seed+2)*Math.PI*2,sweep=2+2*h(seed+3);Vec3d prev=null;
            for(int i=0;i<=30;i++){
                double a=start+sweep*i/30.,rr=r*(1-.25*i/30.);
                Vec3d p=new Vec3d(s.origin.x+Math.cos(a)*rr,y+i/30.*3,s.origin.z+Math.sin(a)*rr);
                if(prev!=null)m.glowLine(prev,p,.12,0xb8a0ff,.7f*fade);
                prev=p;
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        AtomicShader.premultiplied();
    }

    // ───────────────────────────── explosion seen from outside (5.59 → 27.4 s) ─────────────────────────────
    // After the "I am Atomic" scene: a dense tower of light standing on a ring of dark smoke, streaks
    // racing up its face, white spikes and debris thrown upward, violet shockwaves over a ground bathed
    // in magenta. R is the server's damage radius (the configured atomicVisualRadius), so the tower's
    // width, its base and the shockwaves end exactly where the damage does; heights stay at 9 R.

    static void explosion(AtomicScene s,float sec,Matrix4f rot,Matrix4f world,Vec3d cam,ClientWorld w) {
        double R=28*s.scale;
        Vec3d o=new Vec3d(s.origin.x,s.groundY(w,s.origin.x,s.origin.z),s.origin.z);
        int quality=CelestialFall.CONFIG.visualQuality;
        if(sec>=AtomicLook.PINK){
            ground(s,sec,rot,cam,o,R);
            if(quality>0)debris(s,sec,world,cam,o,R,quality);
            smokeBand(s,sec,rot,cam,o,R);
            dustColumn(s,sec,rot,cam,o,R);
            shock(s,sec,rot,cam,o,R);
            embers(s,sec,rot,cam,o,R,quality);
        }
        tower(s,sec,rot,cam,o,R);
        if(sec>=AtomicLook.PINK)crown(s,sec,rot,cam,o,R);
        flash(s,sec,rot,cam,o,R);
    }
    static float unit(float a,float b,float c) { return AtomicLook.unit(a,b,c); }
    /** Tower radius: a hairline at the impact, the damage radius by 6.4 s, held, then drawn back into the caster from 21.2 s to the end of the damage. */
    static double towerRadius(float sec,double R) {
        if(sec<AtomicLook.IMPACT)return 0;
        double r=R*(.04+.26*out(sec,AtomicLook.IMPACT,AtomicLook.PINK)+.70*out(sec,AtomicLook.PINK,6.4f));
        if(sec>=AtomicLook.COLLAPSE){float u=unit(sec,AtomicLook.COLLAPSE,AtomicLook.BEAM_END);r*=Math.pow(1-u,1.6);}
        return r;
    }
    /** Point of light at the impact that swells into the blast, and the pulse when the tower starts to close. */
    static void flash(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R) {
        float a=0;double size=0;
        if(sec>=AtomicLook.IMPACT&&sec<6.4f){
            float grow=out(sec,AtomicLook.IMPACT,AtomicLook.PINK+.15f);
            size=R*(.15+1.0*grow);a=1-smooth(sec,AtomicLook.PINK+.1f,6.4f);
        }else if(sec>=AtomicLook.COLLAPSE_FLAT&&sec<21.8f){
            float u=unit(sec,AtomicLook.COLLAPSE_FLAT,21.8f);size=R*(.6+1.4*u);a=(1-u)*(1-u);
        }
        if(a<=0||size<=0)return;
        var b=AtomicShader.begin(rot,cam,AtomicShader.FLASH,sec,s.seed,1.2f,.6f,0,0);
        Vec3d p=o.add(0,R*.35,0);
        b.facing(p,size,0xffffff,a*near(cam,p,2,6));
        AtomicShader.end(b);
    }
    /** The tower of light itself, a translucent violet sleeve around it and the glow it throws into the air. */
    static void tower(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R) {
        if(sec<AtomicLook.IMPACT||sec>=AtomicLook.BEAM_END)return;
        double r=towerRadius(sec,R);if(r<.05)return;
        double H=9*R,y0=o.y-2,y1=o.y+H*out(sec,AtomicLook.IMPACT,AtomicLook.IMPACT+.12f);
        float white=.6f*smooth(sec,AtomicLook.COLLAPSE,AtomicLook.BEAM_END-.1f);
        float a=1-smooth(sec,AtomicLook.BEAM_END-.08f,AtomicLook.BEAM_END);
        float flow=1f+.6f*smooth(sec,AtomicLook.COLLAPSE,AtomicLook.BEAM_END);
        double flare=.3;
        var b=AtomicShader.beginAt(o,rot,cam,AtomicShader.TOWER,sec,s.seed,white,.62f,flow,1f);
        b.cylinder(o,y0,y1,v->r*(1+flare*Math.pow(1-v,6)),64,24,0xffffff,a);
        AtomicShader.end(b);
        b=AtomicShader.beginAt(o,rot,cam,AtomicShader.TOWER,sec,s.seed+5,white*.5f,.5f,flow*.6f,.22f);
        b.cylinder(o,y0,y1*.95+o.y*.05,v->r*(1.18+flare*Math.pow(1-v,6)),48,12,0xd8b8ff,a*.8f);
        AtomicShader.end(b);
        float g=smooth(sec,AtomicLook.PINK,AtomicLook.PINK+.4f)*a;
        if(g>0){
            b=AtomicShader.begin(rot,cam,AtomicShader.ORB,sec,0,0,0,0,0);
            b.facing(o.add(0,R*1.2,0),r*1.6+R*.3,0xa070ff,g*.35f);
            b.facing(o.add(0,R*.25,0),r*1.4+R*.2,0xd090ff,g*.5f*near(cam,o,3,8));
            AtomicShader.end(b);
        }
    }
    /** White spikes of light fanning outward and upward from the foot of the tower, strongest in the first seconds. */
    static void crown(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R) {
        if(sec>=AtomicLook.BEAM_END)return;
        double r=towerRadius(sec,R);
        float level=(.35f+.65f*(1-smooth(sec,7f,9f)))*smooth(sec,AtomicLook.PINK,AtomicLook.PINK+.15f)*(1-smooth(sec,AtomicLook.COLLAPSE+.3f,AtomicLook.BEAM_END-.1f));
        if(level<=.01f)return;
        long step=(long)Math.floor(sec*20);
        var b=AtomicShader.begin(rot,cam,AtomicShader.COLUMN,sec,s.seed,10f,.45f,9f,.3f);
        int n=28;
        for(int k=0;k<n;k++){
            double seed=k*7.31+s.seed+(k%3==0?step*13.7:0);
            double ang=(k+h(seed))*Math.PI*2/n,lean=Math.toRadians(18+22*h(seed+1)),hgt=R*(.6+1.1*h(seed+2))*(.6+.4*level);
            Vec3d base=new Vec3d(o.x+Math.cos(ang)*r*1.02,o.y,o.z+Math.sin(ang)*r*1.02);
            Vec3d top=base.add(Math.cos(ang)*Math.sin(lean)*hgt,Math.cos(lean)*hgt,Math.sin(ang)*Math.sin(lean)*hgt);
            b.strip(base,top,R*(.05+.07*h(seed+3)),0xffffff,level*(float)(.6+.4*h(seed+4))*near(cam,base,2,6));
        }
        AtomicShader.end(b);
    }
    /** Ring of dark, lumpy smoke hugging the foot of the tower, lit pink from below; it follows the tower's width. */
    static void smokeBand(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R) {
        if(sec<AtomicLook.PINK+.1f||sec>=AtomicLook.BEAM_END)return;
        double r=towerRadius(sec,R);
        float in=smooth(sec,AtomicLook.PINK+.1f,AtomicLook.PINK+.6f),outF=1-smooth(sec,AtomicLook.BEAM_END-.25f,AtomicLook.BEAM_END),t=sec-AtomicLook.PINK;
        var b=AtomicShader.begin(rot,cam,AtomicShader.PUFF,sec,s.seed+7,.95f,.55f,0,0);
        int k=0;
        for(var p:s.wall){
            double ang=p.angle()+.03*t,rad=r*(1.0+.08*p.size()),size=R*(.09+.09*p.size())*(1+.2*Math.sin(t*1.7+p.phase()));
            Vec3d q=new Vec3d(o.x+Math.cos(ang)*rad,o.y+size*.55,o.z+Math.sin(ang)*rad);
            b.facingSeeded(q,size,0x6a4a9a,in*outF*p.alpha()*near(cam,q,2,6),k++);
        }
        AtomicShader.end(b);
    }
    /** The floor inside the radius bathed in magenta light with radial rays and waves racing outward. */
    static void ground(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R) {
        if(sec>=AtomicLook.BEAM_END+1.5f)return;
        double rr=Math.max(towerRadius(sec,R),R*.3);
        float a=smooth(sec,AtomicLook.PINK,AtomicLook.PINK+.3f)*(1-smooth(sec,AtomicLook.BEAM_END,AtomicLook.BEAM_END+1.5f));
        var b=AtomicShader.beginAt(o,rot,cam,AtomicShader.GROUND,sec,s.seed,(float)rr,1f,0,0);
        double e=rr*1.35;Vec3d p=o.add(0,.08,0);
        b.quad(p.add(-e,0,-e),p.add(e,0,-e),p.add(e,0,e),p.add(-e,0,e),0xffffff,a);
        AtomicShader.end(b);
    }
    /** Ground shockwaves, none past the damage radius: one with the ignition, one with the slabs, then a pulse every 1.6 s. */
    static void shock(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R) {
        ring(s,sec,rot,cam,new Vec3d(o.x,o.y+.12,o.z),AtomicLook.PINK,.45f,R,.3*R,.9f,AtomicLook.PINK+.9f,0xe0b8ff,.95f,.3f);
        ring(s,sec,rot,cam,new Vec3d(o.x,o.y+.17,o.z),7.07f,.5f,R,.25*R,.6f,8.1f,0xd0a0ff,.8f,.15f);
        for(int i=0;i<12;i++){float t0=8.6f+1.6f*i;if(t0>=AtomicLook.COLLAPSE)break;ring(s,sec,rot,cam,new Vec3d(o.x,o.y+.14,o.z),t0,.7f,R,.2*R,.5f,t0+1.0f,0xc088ff,.6f,.1f);}
    }
    /** One expanding flat ring born at t0, reaching rmax after grow seconds and gone at tEnd. */
    static void ring(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d c,float t0,float grow,double rmax,double band,float whiteMix,float tEnd,int rgb,float alpha,float cover) {
        if(sec<t0||sec>=tEnd)return;
        float u=unit(sec,t0,t0+grow);double r=rmax*(1-Math.pow(1-u,3)),w=band*(.6+.9*u);
        float a=alpha*smooth(sec,t0,t0+.05f)*(1-smooth(sec,t0+grow*.7f,tEnd));
        if(a<=.005||r<.3)return;
        var b=AtomicShader.begin(rot,cam,AtomicShader.SHOCK,sec,s.seed,cover,whiteMix,.86f,.55f);
        b.annulus(c,r-w,r,96,rgb,a);
        AtomicShader.end(b);
    }
    /** Dust column rising out of the crater once the tower is gone. */
    static void dustColumn(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R) {
        if(sec<21.8f||sec>=BLAST_END)return;
        float t=sec-21.8f,in=smooth(sec,21.8f,22.6f),outF=1-smooth(sec,24.5f,BLAST_END);
        var b=AtomicShader.begin(rot,cam,AtomicShader.PUFF,sec,s.seed+11,.7f,.35f*(1-smooth(sec,22.5f,25f)),0,0);
        int k=0;
        for(var p:s.dust){
            double y=o.y+R*(.1+p.size()*.5)+R*.22*t*(.6+.7*p.size()),r=R*(.1+.45*p.size())+R*.04*t,ang=p.angle()+.12*t;
            Vec3d q=new Vec3d(o.x+Math.cos(ang)*r,y,o.z+Math.sin(ang)*r);
            b.facingSeeded(q,R*(.28+.3*p.size())*(1+.06*t),0xffffff,in*outF*p.alpha()*near(cam,q,2,7),k++);
        }
        AtomicShader.end(b);
    }
    static Vec3d turn(Vec3d v,Vec3d axis,double ang) {
        double c=Math.cos(ang),sn=Math.sin(ang);
        return v.multiply(c).add(axis.crossProduct(v).multiply(sn)).add(axis.multiply(axis.dotProduct(v)*(1-c)));
    }
    /** Ground chunks lifted straight up the tower, tumbling, lit pink on the side facing the light; never past the radius. */
    static void debris(AtomicScene s,float sec,Matrix4f world,Vec3d cam,Vec3d o,double R,int quality) {
        float t0=AtomicLook.PINK;
        if(sec<t0||sec>=t0+6.5f)return;
        var buffer=Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.defaultBlendFunc();
        buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var m=new EffectMesh(buffer,world,cam);
        int n=quality>1?s.debris.length:s.debris.length/2;
        for(int k=0;k<n;k++){
            var d=s.debris[k];float t=sec-t0-d.delay()*3;if(t<0||t>=4.6f)continue;
            double rr=Math.min(R*.95,d.r0()*R*.85+d.speed()*R*.12*t),y=o.y+d.rise()*R*1.6*t-R*.06*t*t;
            Vec3d p=new Vec3d(o.x+Math.cos(d.angle())*rr,y,o.z+Math.sin(d.angle())*rr);
            float a=.95f*(1-smooth(t,3.4f,4.6f))*near(cam,p,1.5,4);
            if(a<=.01)continue;
            double ang=d.spin()*t;
            Vec3d u=turn(new Vec3d(1,0,0),d.axis(),ang).multiply(d.sx()*R),v=turn(new Vec3d(0,1,0),d.axis(),ang).multiply(d.sy()*R),w=turn(new Vec3d(0,0,1),d.axis(),ang).multiply(d.sz()*R);
            Vec3d light=new Vec3d(o.x,p.y,o.z).subtract(p).normalize();
            face(m,p,u,v,w,light,a);face(m,p,u.multiply(-1),w,v,light,a);
            face(m,p,v,w,u,light,a);face(m,p,v.multiply(-1),u,w,light,a);
            face(m,p,w,u,v,light,a);face(m,p,w.multiply(-1),v,u,light,a);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        AtomicShader.premultiplied();
    }
    /** One box face: centre p + n, spanned by a and b, shaded by how much it faces the tower. */
    static void face(EffectMesh m,Vec3d p,Vec3d n,Vec3d a,Vec3d b,Vec3d light,float alpha) {
        double lit=Math.max(0,n.normalize().dotProduct(light));
        int rgb=AtomicLook.lerpColor(0x150a2b,0xb070e8,(float)(.08+.92*lit*lit));
        Vec3d c=p.add(n);
        m.quad(c.subtract(a).subtract(b),c.add(a).subtract(b),c.add(a).add(b),c.subtract(a).add(b),rgb,alpha);
    }
    /** Embers drifting out of the crater after the tower, over the violet afterglow pooled in it. */
    static void embers(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,int quality) {
        float start=AtomicLook.BEAM_END-.2f;
        if(sec<start||sec>=BLAST_END)return;
        float t=sec-start,in=smooth(sec,start,start+.8f),outF=1-smooth(sec,25.5f,BLAST_END);
        var b=AtomicShader.begin(rot,cam,AtomicShader.ORB,sec,0,0,0,0,0);
        float glow=in*(1-smooth(sec,AtomicLook.BEAM_END,26.5f));
        if(glow>0)b.flat(new Vec3d(o.x,o.y+.2,o.z),R,0xb070ff,glow*.6f);
        int n=quality>1?s.embers.length:quality>0?s.embers.length/2:s.embers.length/4;
        for(int k=0;k<n;k++){
            var e=s.embers[k];float cyc=t/e.life()+e.phase();int c=(int)Math.floor(cyc);float u=cyc-c;
            double a=e.angle()+h(c*3.1+k)*1.2,r=e.radius()*R*.7*(.6+.6*h(c*5.7+k));
            Vec3d p=o.add(Math.cos(a)*r+e.vx()*R*u*e.life(),R*.05+e.vy()*R*u*e.life(),Math.sin(a)*r+e.vz()*R*u*e.life());
            float tw=(float)Math.pow(Math.max(0,Math.sin(sec*(5+h(k*6.1)*7)+k)),2);
            b.facing(p,e.size()*R,0xffb0ff,in*outF*(float)Math.sqrt(Math.max(0,Math.sin(Math.PI*u)))*(.5f+.5f*tw)*near(cam,p,1,3));
        }
        AtomicShader.end(b);
    }

    // ───────────────────────────── crater (7.07 → 27.6 s) ─────────────────────────────

    static void crater(WorldRenderContext ctx,AtomicScene s,float sec,MinecraftClient mc) {
        s.buildSlabs(mc.world);
        var matrices=ctx.matrixStack();var cam=ctx.camera().getPos();
        var vertices=mc.getBufferBuilders().getEntityVertexConsumers();boolean drew=false;
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        int quality=CelestialFall.CONFIG.visualQuality;
        for(var slab:s.slabs){
            if(quality==0&&slab.ring()>=6)continue;
            if(sec<slab.rise()||sec>=slab.vanish()+.05f)continue;
            double depth=slab.thick()+slab.width()*Math.sin(slab.tilt())+1;
            float up=out(sec,slab.rise(),slab.rise()+.45f);
            double drop=depth*(1-up)+depth*smooth(sec,slab.vanish(),slab.vanish()+.05f);
            double x=s.origin.x+Math.cos(slab.angle())*slab.radius(),z=s.origin.z+Math.sin(slab.angle())*slab.radius();
            matrices.push();
            matrices.translate(x-cam.x,slab.y()+slab.lift()-.35-drop-cam.y,z-cam.z);
            // Local frame: x along the ring, z outward; the outer edge is raised by the tilt.
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)(-slab.angle()+Math.PI/2+slab.twist())));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation((float)-slab.tilt()));
            int nl=Math.max(1,(int)Math.round(slab.length()/1.6)),nw=Math.max(1,(int)Math.round(slab.width()/1.6));
            double pl=slab.length()/nl,pw=slab.width()/nw;
            int light=WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(x,slab.y()+1.5,z));
            for(int i=0;i<nl;i++)for(int j=0;j<nw;j++){
                matrices.push();
                matrices.translate(-slab.length()*.5+i*pl,-slab.thick(),j*pw);
                matrices.scale((float)pl,(float)slab.thick(),(float)pw);
                mc.getBlockRenderManager().renderBlockAsEntity(slab.state(),matrices,vertices,light,OverlayTexture.DEFAULT_UV);
                matrices.pop();drew=true;
            }
            matrices.pop();
        }
        if(drew)vertices.draw();
        AtomicShader.premultiplied();
    }
}
