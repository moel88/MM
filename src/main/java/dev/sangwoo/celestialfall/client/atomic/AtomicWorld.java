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
 * ribbon vortex and X glint, the violet void frame, the blast interior and the crater. Nothing
 * here edits a block.
 */
public final class AtomicWorld {
    private static final Map<UUID,AtomicScene> SCENES=new HashMap<>();
    private static final float[] PULSES={.367f,1.437f,2.437f,3.457f,4.387f,5.437f};
    private static final float PULSE_LEN=.32f;
    private static final double DECAL=14.5,SKY_RADIUS=30,SKY_TOP=46,STUD=AtomicScene.STUD;
    private AtomicWorld() {}

    static AtomicScene scene(AtomicClient.Cast c) { return SCENES.computeIfAbsent(c.id,k->new AtomicScene(c.id,c.origin,c.yaw)); }
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
            RenderSystem.enableDepthTest();AtomicShader.premultiplied();
            for(var c:casts){
                var s=scene(c);float sec=c.age(delta)/20f;s.ground.refresh(mc.world);
                if(sec<AtomicLook.PINK+.06f)chant(s,sec,rot,world,cam,mc.world);
                if(sec>=AtomicLook.PINK&&sec<AtomicLook.BEAM_END)blast(s,sec,rot,world,cam,mc.world);
            }
            for(var c:casts){
                float sec=c.age(delta)/20f;
                if(sec>=7.0f)crater(ctx,scene(c),sec,mc);
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
        for(int k=0;k<s.curtains.length;k++){
            var c=s.curtains[k];
            float a=c.alpha()*in*(1-gone)*(c.extra()?columns:1);
            if(a<=.003f)continue;
            double th=c.angle()+c.spin()*t,tilt=c.tilt()*(1+columns*.6);
            double rb=c.radius()*(1-.92*vee),rt=Math.max(.6,c.radius()-c.height()*Math.tan(tilt))*(1-vee)+(c.radius()+6)*vee;
            Vec3d base=new Vec3d(s.origin.x+Math.cos(th)*rb,y0,s.origin.z+Math.sin(th)*rb);
            Vec3d top=new Vec3d(s.origin.x+Math.cos(th)*rt,y0+c.height(),s.origin.z+Math.sin(th)*rt);
            b.strip(base,top,c.width(),c.color(),a);
        }
        AtomicShader.end(b);
        if(sec>=21.87f&&sec<AtomicLook.BEAM_END){
            float a=smooth(sec,21.87f,21.9f)*(1-smooth(sec,22.0f,AtomicLook.BEAM_END));
            b=AtomicShader.begin(rot,cam,AtomicShader.STRIATED,sec,s.seed,0,0,0,0);
            int n=48;double r=20*STUD,top=150;float[] u=new float[4],v=new float[4];
            for(int i=0;i<n;i++){
                double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
                Vec3d p0=new Vec3d(s.origin.x+Math.cos(a0)*r,y0,s.origin.z+Math.sin(a0)*r),p1=new Vec3d(s.origin.x+Math.cos(a1)*r,y0,s.origin.z+Math.sin(a1)*r);
                u[0]=u[3]=i/(float)n;u[1]=u[2]=(i+1)/(float)n;v[0]=v[1]=0;v[2]=v[3]=1;
                b.quad(p0,p1,p1.add(0,top,0),p0.add(0,top,0),u,v,0xcbb4ff,a);
            }
            AtomicShader.end(b);
        }
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
