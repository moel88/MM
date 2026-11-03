package dev.sangwoo.celestialfall.client.isosang;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Lee So-sang's G's own space (2.83.0, user request: "컷씬은 그냥 다른 공간 보여줘서 검 안보이게 하고 박히는 순간 원래 월드에는
 * 박히는 효과만 남게"): while its user's camera is in its cutscene, the world is not drawn (no blocks, nobody else, no
 * particles, weather, sky or clouds: the mixins ask {@link #on}) and its shots are taken in a space of their own, as the
 * videos' are: black round her hand, a pale sky over far mountains behind her face, a storm whirling round the giant
 * sword over her, and a dusky sky over dark hills, on dark ground, where it comes down. Only she is in it (only her free
 * arm in the shots of her hand, and not in the shots of the sword coming down), the sword, and the effects. With a shader
 * pack's mod installed (Iris, Oculus) the world stays and the sword is shown in it (to her alone, as always).
 */
public final class GStage {
    private GStage(){}
    /** The spaces: black (her hand), pale sky and mountains (her face), the storm (the sword over her), dusk (it coming down). */
    public static final int NONE=-1,BLACK=0,GREY=1,STORM=2,DUSK=3;
    static final int FRONT=4,GROUND=5;
    /** How far off its sky is drawn (blocks: well within any view's far plane) and how far its ground reaches. */
    static final double SKY_R=60,GROUND_R=64;
    static final Identifier BLADE=CelestialFall.id("textures/effect/isosang_g_blade.png"),BLADE_N=CelestialFall.id("textures/effect/isosang_g_blade_n.png");
    private static ShaderProgram shader,blade;
    private static Boolean supported;

    static void initialize(){
        CoreShaderRegistrationCallback.EVENT.register(c->{
            c.register(CelestialFall.id("isosang_stage"),VertexFormats.POSITION_TEXTURE_COLOR,s->shader=s);
            c.register(CelestialFall.id("isosang_blade"),VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,s->blade=s);
        });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(GStage::frameStart);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(GStage::frameEnd);
    }
    /** Whether the world can be hidden for it (no shader pack's mod installed: those draw the world their own way). */
    public static boolean supported(){
        if(supported==null){var loader=FabricLoader.getInstance();supported=!loader.isModLoaded("iris")&&!loader.isModLoaded("oculus");}
        return supported;
    }
    /** The space the local player's camera is in (NONE: the world), at {@code delta} into the tick. */
    public static int space(float delta){
        if(!supported())return NONE;
        var c=FallingSword.cinematic();if(c==null)return NONE;
        return FallingSword.space(c,c.age(delta)/20f);
    }
    /** The space of the frame being drawn (set as the world's drawing starts, NONE again once it is over: the GUI's player is not in it). */
    private static int now=NONE;
    /** Whether the frame being drawn is the sky shot's last, the sword alone in the storm (its user not in it). */
    private static boolean alone;
    /** Whether the frame being drawn shows her whole body (her face in the dark before the pale sky; only her free arm before that). */
    private static boolean whole;
    static void frameStart(WorldRenderContext ctx){
        now=space(ctx.tickDelta());
        var c=now==NONE?null:FallingSword.cinematic();float t=c==null?0:c.age(ctx.tickDelta())/20f;
        alone=c!=null&&now==STORM&&FallingSword.alone(t);
        whole=c!=null&&FallingSword.wholeBody(t);
    }
    static void frameEnd(WorldRenderContext ctx){now=NONE;alone=false;whole=false;}
    /** Whether the world is hidden from the frame being drawn (its user's G's cutscene in its own space). */
    public static boolean hidden(){return now!=NONE;}
    /** Whether {@code e} is left out of the frame being drawn: everyone but its user, and its user in the shots of the sword coming down (and in the sky shot's last frame). */
    public static boolean hides(Entity e){
        if(now==NONE)return false;
        return e!=MinecraftClient.getInstance().player||now==DUSK||alone;
    }
    /** Whether {@code e} shows only their free arm in the frame being drawn (its user in the shots of her hand). */
    public static boolean handOnly(Entity e){return now==BLACK&&!whole&&e==MinecraftClient.getInstance().player;}
    /** Whether {@code e} holds nothing in the frame being drawn (its user all through her cutscene, as the videos': her hands are empty). */
    public static boolean emptyHanded(Entity e){return now!=NONE&&e==MinecraftClient.getInstance().player;}

    // ───────────────────────────── drawing ─────────────────────────────

    /** Draws {@code fill} with the stage's program in {@code mode}, the world turned to the camera by {@code view}. */
    private static void draw(Matrix4f view,int mode,float age,java.util.function.Consumer<BufferBuilder> fill){
        if(shader==null)return;
        var mv=RenderSystem.getModelViewStack();mv.push();mv.multiplyPositionMatrix(view);RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(()->shader);
        shader.getUniform("Mode").set(mode);shader.getUniform("Age").set(age);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        fill.accept(b);
        BufferRenderer.drawWithGlobalProgram(b.end());
        mv.pop();RenderSystem.applyModelViewMatrix();
    }
    private static void v(BufferBuilder b,double x,double y,double z,float u,float w,float shade,float a){
        b.vertex((float)x,(float)y,(float)z).texture(u,w).color(shade,shade,shade,a).next();
    }
    /**
     * Its sky, in place of the world's (from the sky's mixin, with the sky's matrices: the camera's turn only). Returns
     * whether it drew one (the world's is then not drawn).
     */
    public static boolean sky(MatrixStack matrices,float delta){
        if(now==NONE||shader==null)return false;
        var c=FallingSword.cinematic();if(c==null)return false;
        float t=c.age(delta)/20f;int s=now;
        if(s==STORM){Vec3d e=FallingSword.stormEye(c,t);shader.getUniform("Eye").set((float)e.x,(float)e.y,(float)e.z,(float)Math.toRadians(FallingSword.HOLE));}
        RenderSystem.depthMask(false);RenderSystem.disableCull();
        draw(matrices.peek().getPositionMatrix(),s,t,b->{
            int lat=24,lon=48;
            for(int i=0;i<lat;i++){
                double a0=-Math.PI/2+Math.PI*i/lat,a1=-Math.PI/2+Math.PI*(i+1)/lat;
                for(int j=0;j<lon;j++){
                    double b0=2*Math.PI*j/lon,b1=2*Math.PI*(j+1)/lon;
                    sphere(b,a0,b0);sphere(b,a0,b1);sphere(b,a1,b1);sphere(b,a1,b0);
                }
            }
        });
        RenderSystem.depthMask(true);RenderSystem.enableCull();
        return true;
    }
    private static void sphere(BufferBuilder b,double lat,double lon){
        double cl=Math.cos(lat);v(b,Math.cos(lon)*cl*SKY_R,Math.sin(lat)*SKY_R,Math.sin(lon)*cl*SKY_R,0,0,1,1);
    }
    /** Its dark ground under the sword coming down (BEFORE_ENTITIES: with depth, so the sword's point goes into it). */
    static void ground(WorldRenderContext ctx){
        if(now!=DUSK||shader==null)return;
        var c=FallingSword.cinematic();if(c==null)return;
        float t=c.age(ctx.tickDelta())/20f;
        Vec3d cam=ctx.camera().getPos(),s=FallingSword.strike(c);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableCull();
        draw(ctx.matrixStack().peek().getPositionMatrix(),GROUND,t,b->{
            int rings=18,seg=56;
            for(int i=0;i<rings;i++){
                double r0=GROUND_R*Math.pow(i/(double)rings,1.6),r1=GROUND_R*Math.pow((i+1)/(double)rings,1.6);
                for(int j=0;j<seg;j++){
                    double a0=2*Math.PI*j/seg,a1=2*Math.PI*(j+1)/seg;
                    disc(b,s,cam,r0,a0);disc(b,s,cam,r0,a1);disc(b,s,cam,r1,a1);disc(b,s,cam,r1,a0);
                }
            }
        });
        RenderSystem.enableCull();
    }
    private static void disc(BufferBuilder b,Vec3d s,Vec3d cam,double r,double a){
        double x=Math.cos(a)*r,z=Math.sin(a)*r;
        v(b,s.x+x-cam.x,s.y+.02-cam.y,s.z+z-cam.z,(float)x,(float)z,1,1);
    }

    /**
     * The sword, in its user's own cutscene only (AFTER_ENTITIES: solid, so her body and the dust meet it), lit as it is
     * drawn (isosang_blade): its blade, its edges, its guard, grip and pommel (2.83.1, user request: "저거 아직 그림이지?
     * 실제 모델 아니지?": a real model). In the storm the clouds round its eye take it past the hole in their ring on its far
     * side (its point and its near edges clear of them: "검 끝이 구름에 묻히면 안되지"), and the ring's clouds are drawn over
     * it there (FRONT: behind her, in front of it).
     */
    static void sword(WorldRenderContext ctx){
        var c=FallingSword.cinematic();if(c==null||blade==null)return;
        float t=c.age(ctx.tickDelta())/20f;
        var bl=FallingSword.blade(c,t);if(bl==null||bl.alpha()<=.004f)return;
        var camera=ctx.camera();Vec3d cam=camera.getPos();Matrix4f view=ctx.matrixStack().peek().getPositionMatrix();
        // a warm key light from beside the camera, over its left shoulder
        Vec3d fw=Vec3d.fromPolar(camera.getPitch(),camera.getYaw()),right=fw.crossProduct(new Vec3d(0,1,0));
        right=right.lengthSquared()<1e-6?new Vec3d(1,0,0):right.normalize();Vec3d up=right.crossProduct(fw).normalize();
        Vec3d light=right.multiply(-.45).add(up.multiply(.65)).subtract(fw.multiply(.6)).normalize();
        Vec3d across=bl.across(),axis=bl.axis();
        boolean storm=now==STORM;
        Vec3d eye=storm?FallingSword.stormEye(c,t):new Vec3d(0,1,0),far=storm?FallingSword.farSide(c):new Vec3d(1,0,0);
        float hole=storm?(float)Math.toRadians(FallingSword.HOLE):0;
        blade.getUniform("Across").set((float)across.x,(float)across.y,(float)across.z);
        blade.getUniform("Axis").set((float)axis.x,(float)axis.y,(float)axis.z);
        blade.getUniform("Light").set((float)light.x,(float)light.y,(float)light.z);
        blade.getUniform("Heat").set(bl.heat());blade.getUniform("Dark").set(bl.dark());
        blade.getUniform("Hole").set((float)eye.x,(float)eye.y,(float)eye.z,hole);
        blade.getUniform("Far").set((float)far.x,(float)far.y,(float)far.z);
        blade.getUniform("Age").set(t);
        RenderSystem.setShaderTexture(0,BLADE);RenderSystem.setShaderTexture(1,BLADE_N);
        RenderSystem.enableDepthTest();RenderSystem.disableCull();
        // in the storm it leaves the depth alone (the clouds over it are drawn after it, behind her)
        RenderSystem.depthMask(!storm);
        var mv=RenderSystem.getModelViewStack();mv.push();mv.multiplyPositionMatrix(view);RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(()->blade);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        blade.getUniform("Edge").set(0);
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
        faces(b,bl,cam);fittings(b,bl,cam);BufferRenderer.drawWithGlobalProgram(b.end());
        blade.getUniform("Edge").set(1);
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
        edges(b,bl,cam);BufferRenderer.drawWithGlobalProgram(b.end());
        mv.pop();RenderSystem.applyModelViewMatrix();
        if(storm&&shader!=null){
            // the ring's clouds over its far side (behind her: the depth test keeps her in front)
            RenderSystem.depthMask(false);
            shader.getUniform("Eye").set((float)eye.x,(float)eye.y,(float)eye.z,hole);
            shader.getUniform("Far").set((float)far.x,(float)far.y,(float)far.z);
            draw(view,FRONT,t,bb->{
                int lat=24,lon=48;
                for(int i=0;i<lat;i++){
                    double a0=-Math.PI/2+Math.PI*i/lat,a1=-Math.PI/2+Math.PI*(i+1)/lat;
                    for(int j=0;j<lon;j++){
                        double b0=2*Math.PI*j/lon,b1=2*Math.PI*(j+1)/lon;
                        sphere(bb,a0,b0);sphere(bb,a0,b1);sphere(bb,a1,b1);sphere(bb,a1,b0);
                    }
                }
            });
        }
        RenderSystem.depthMask(true);RenderSystem.enableCull();
    }
    /**
     * Its guard, grip and pommel past the top of its blade (blocks along it): a broad guard, a long round grip, a heavy
     * pommel; bronze, lit as the blade is (tint.g 1: no carving). Far up in the storm's clouds and out of every shot, as the
     * videos', but there: it is a whole sword.
     */
    static final double GUARD=1.4,GUARD_WIDE=1.45,GUARD_THICK=2.1,GRIP=6.5,GRIP_R=.8,POMMEL=1.8,POMMEL_R=1.35;
    private static void fittings(BufferBuilder b,FallingSword.Blade bl,Vec3d cam){
        double l=IsosangRules.BLADE_LENGTH,w=IsosangRules.BLADE_WIDTH;
        box(b,bl,cam,w*GUARD_WIDE/2,l,l+GUARD,GUARD_THICK/2);
        prism(b,bl,cam,l+GUARD,l+GUARD+GRIP,GRIP_R,GRIP_R);
        prism(b,bl,cam,l+GUARD+GRIP,l+GUARD+GRIP+POMMEL*.5,GRIP_R*1.1,POMMEL_R);
        prism(b,bl,cam,l+GUARD+GRIP+POMMEL*.5,l+GUARD+GRIP+POMMEL,POMMEL_R,POMMEL_R*.55);
    }
    private static void box(BufferBuilder b,FallingSword.Blade bl,Vec3d cam,double hx,double y0,double y1,double hz){
        double[][] c={{-hx,-hz},{hx,-hz},{hx,hz},{-hx,hz}};
        for(int i=0;i<4;i++){
            double[] p=c[i],q=c[(i+1)%4];
            Vec3d n=bl.across().multiply((p[0]+q[0])/2).add(bl.normal().multiply((p[1]+q[1])/2)).normalize();
            fit(b,bl,cam,p[0],y0,p[1],n);fit(b,bl,cam,q[0],y0,q[1],n);fit(b,bl,cam,q[0],y1,q[1],n);fit(b,bl,cam,p[0],y1,p[1],n);
        }
        for(int e=0;e<2;e++){
            double y=e==0?y0:y1;Vec3d n=bl.axis().multiply(e==0?-1:1);
            fit(b,bl,cam,-hx,y,-hz,n);fit(b,bl,cam,hx,y,-hz,n);fit(b,bl,cam,hx,y,hz,n);fit(b,bl,cam,-hx,y,hz,n);
        }
    }
    private static void prism(BufferBuilder b,FallingSword.Blade bl,Vec3d cam,double y0,double y1,double r0,double r1){
        int n=10;
        for(int i=0;i<n;i++){
            double a0=2*Math.PI*i/n,a1=2*Math.PI*(i+1)/n,am=(a0+a1)/2;
            Vec3d nn=bl.across().multiply(Math.cos(am)).add(bl.normal().multiply(Math.sin(am)));
            fit(b,bl,cam,Math.cos(a0)*r0,y0,Math.sin(a0)*r0,nn);fit(b,bl,cam,Math.cos(a1)*r0,y0,Math.sin(a1)*r0,nn);
            fit(b,bl,cam,Math.cos(a1)*r1,y1,Math.sin(a1)*r1,nn);fit(b,bl,cam,Math.cos(a0)*r1,y1,Math.sin(a0)*r1,nn);
        }
    }
    private static void fit(BufferBuilder b,FallingSword.Blade bl,Vec3d cam,double x,double y,double z,Vec3d n){
        Vec3d at=bl.at(x,y,z).subtract(cam);
        b.vertex((float)at.x,(float)at.y,(float)at.z).texture(.5f,0f).color(1f,1f,0f,bl.shown(y)).normal((float)n.x,(float)n.y,(float)n.z).next();
    }
    /** Its length cut into slices (blocks up from its point): close together round the point, where its outline turns. */
    static final double[] SLICES=slices();
    private static double[] slices(){
        int near=22,far=16;double p=FallingSword.POINT,l=IsosangRules.BLADE_LENGTH;
        double[] y=new double[near+far+1];
        for(int i=0;i<=near;i++)y[i]=p*Math.pow(i/(double)near,1.3);
        for(int i=1;i<=far;i++)y[near+i]=p+(l-p)*i/far;
        return y;
    }
    /** How far in from the edge its bevel reaches (blocks), and how thick its edge is (of its whole thickness). */
    static final double BEVEL=.45,EDGE=.2;
    /** Where across its face the mesh's points are: by how far in from its edge (fractions of the bevel), then inside it. */
    private static final double[] IN={0,.5,1};
    /**
     * A point of its face, x across and y up from its point: {x, z, nx, ny, nz} (its height off its middle plane and its
     * normal, the front's). Its bevel is a flat chamfer down to its edge, a crisp crease where it meets the flat face;
     * {@code inward} which side of a crease the normal is taken on (+1 the face's, -1 the bevel's).
     */
    static double[] surface(double x,double y,int inward){
        double h=FallingSword.half(y),b=Math.min(BEVEL,h*.45);
        double t=b<1e-6?1:Math.max(0,Math.min(1,(h-Math.abs(x))/b));
        double z=FallingSword.THICK*.5*(EDGE+(1-EDGE)*t);
        boolean bevel=t<1||(t>=1&&inward<0&&Math.abs(h-Math.abs(x)-b)<1e-9);
        double g=FallingSword.THICK*.5*(1-EDGE);
        double dzdx=b<1e-6||!bevel?0:-Math.signum(x)*g/b;
        double dzdy=b<1e-6||!bevel?0:g*FallingSword.widening(y)/b;
        double n=Math.sqrt(dzdx*dzdx+dzdy*dzdy+1);
        return new double[]{x,z,-dzdx/n,-dzdy/n,1/n};
    }
    /** The points across at a slice (x, from its left edge to its right). */
    static double[] across(double y){
        double h=FallingSword.half(y),b=Math.min(BEVEL,h*.45);
        double[] xs=new double[IN.length*2+1];int n=0;
        for(double f:IN)xs[n++]=-(h-f*b);
        xs[n++]=0;
        for(int i=IN.length-1;i>=0;i--)xs[n++]=h-IN[i]*b;
        return xs;
    }
    private static void faces(BufferBuilder b,FallingSword.Blade bl,Vec3d cam){
        double l=IsosangRules.BLADE_LENGTH;
        for(int s=-1;s<=1;s+=2){
            for(int i=0;i+1<SLICES.length;i++){
                double y0=SLICES[i],y1=SLICES[i+1];
                float a0=bl.shown(y0),a1=bl.shown(y1);if(a0<=.004f&&a1<=.004f)continue;
                double[] x0=across(y0),x1=across(y1);
                for(int k=0;k+1<x0.length;k++){
                    // (a quad on the bevel takes its crease's normal from the bevel's side, one on the face from the face's)
                    int in=k<IN.length-1||k>=x0.length-IN.length?-1:1;
                    blade(b,bl,cam,x0[k],y0,s,a0,l,in);blade(b,bl,cam,x0[k+1],y0,s,a0,l,in);
                    blade(b,bl,cam,x1[k+1],y1,s,a1,l,in);blade(b,bl,cam,x1[k],y1,s,a1,l,in);
                }
            }
        }
    }
    private static void blade(BufferBuilder b,FallingSword.Blade bl,Vec3d cam,double x,double y,int side,float a,double l,int inward){
        double[] p=surface(x,y,inward);
        Vec3d at=bl.at(x,y,side*p[1]).subtract(cam);
        Vec3d n=bl.across().multiply(p[2]).add(bl.axis().multiply(p[3])).add(bl.normal().multiply(side*p[4]));
        float u=.5f+(float)(x/IsosangRules.BLADE_WIDTH);if(side<0)u=1-u;
        b.vertex((float)at.x,(float)at.y,(float)at.z).texture(u,1-(float)(y/l)).color(side>0?1f:0f,0f,0f,a).normal((float)n.x,(float)n.y,(float)n.z).next();
    }
    /** Its thin edges, front to back along its outline. */
    private static void edges(BufferBuilder b,FallingSword.Blade bl,Vec3d cam){
        for(int s=-1;s<=1;s+=2){
            for(int i=0;i+1<SLICES.length;i++){
                double y0=SLICES[i],y1=SLICES[i+1];
                float a0=bl.shown(y0),a1=bl.shown(y1);if(a0<=.004f&&a1<=.004f)continue;
                edge(b,bl,cam,s,y0,-1,a0);edge(b,bl,cam,s,y0,1,a0);edge(b,bl,cam,s,y1,1,a1);edge(b,bl,cam,s,y1,-1,a1);
            }
        }
    }
    private static void edge(BufferBuilder b,FallingSword.Blade bl,Vec3d cam,int s,double y,int face,float a){
        double h=FallingSword.half(y),z=FallingSword.THICK*.5*EDGE;
        Vec3d n=bl.across().multiply(s).add(bl.axis().multiply(-FallingSword.widening(y))).normalize();
        Vec3d at=bl.at(s*h,y,face*z).subtract(cam);
        b.vertex((float)at.x,(float)at.y,(float)at.z).texture(.5f,1-(float)(y/IsosangRules.BLADE_LENGTH)).color(1f,0f,0f,a).normal((float)n.x,(float)n.y,(float)n.z).next();
    }
}
