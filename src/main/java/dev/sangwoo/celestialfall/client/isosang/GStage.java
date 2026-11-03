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
    static final int GROUND=5,FACE=6,SIDE=7;
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
    static void frameStart(WorldRenderContext ctx){now=space(ctx.tickDelta());}
    static void frameEnd(WorldRenderContext ctx){now=NONE;}
    /** Whether the world is hidden from the frame being drawn (its user's G's cutscene in its own space). */
    public static boolean hidden(){return now!=NONE;}
    /** Whether {@code e} is left out of the frame being drawn: everyone but its user, and its user in the shots of the sword coming down. */
    public static boolean hides(Entity e){
        if(now==NONE)return false;
        return e!=MinecraftClient.getInstance().player||now==DUSK;
    }
    /** Whether {@code e} shows only their free arm in the frame being drawn (its user in the shots of her hand). */
    public static boolean handOnly(Entity e){return now==BLACK&&e==MinecraftClient.getInstance().player;}

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
        if(s==STORM){Vec3d e=FallingSword.stormEye(c,t);shader.getUniform("Eye").set((float)e.x,(float)e.y,(float)e.z);}
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

    /** The sword, in its user's own cutscene only (AFTER_ENTITIES: solid, so her body and the dust meet it), lit as it is drawn (isosang_blade). */
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
        blade.getUniform("Across").set((float)across.x,(float)across.y,(float)across.z);
        blade.getUniform("Axis").set((float)axis.x,(float)axis.y,(float)axis.z);
        blade.getUniform("Light").set((float)light.x,(float)light.y,(float)light.z);
        blade.getUniform("Heat").set(bl.heat());blade.getUniform("Dark").set(bl.dark());
        RenderSystem.setShaderTexture(0,BLADE);RenderSystem.setShaderTexture(1,BLADE_N);
        RenderSystem.enableDepthTest();RenderSystem.disableCull();
        // where any of it is faded (in the storm, its top lost in the eye) it leaves the depth alone: the light over it must show
        RenderSystem.depthMask(bl.alpha()>=.999f&&bl.fadeFrom()>=IsosangRules.BLADE_LENGTH);
        var mv=RenderSystem.getModelViewStack();mv.push();mv.multiplyPositionMatrix(view);RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(()->blade);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        blade.getUniform("Edge").set(0);
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
        faces(b,bl,cam);BufferRenderer.drawWithGlobalProgram(b.end());
        blade.getUniform("Edge").set(1);
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
        edges(b,bl,cam);BufferRenderer.drawWithGlobalProgram(b.end());
        mv.pop();RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);RenderSystem.enableCull();
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
    private static final double[] IN={0,.12,.3,.55,.8,1};
    /** A point of its face, x across and y up from its point: {x, z, nx, ny, nz} (its height off its middle plane and its normal, the front's). */
    static double[] surface(double x,double y){
        double h=FallingSword.half(y),b=Math.min(BEVEL,h*.45);
        double t=b<1e-6?1:Math.max(0,Math.min(1,(h-Math.abs(x))/b));
        double z=FallingSword.THICK*.5*(EDGE+(1-EDGE)*(1-(1-t)*(1-t)));
        // its slopes: down toward the edge, and (round the point) up the blade as the outline narrows
        double dg=2*(1-t)*FallingSword.THICK*.5*(1-EDGE);
        double dzdx=b<1e-6||t>=1?0:-Math.signum(x)*dg/b;
        double dh=y<FallingSword.POINT?IsosangRules.BLADE_WIDTH/2*1.8*Math.pow(1-y/FallingSword.POINT,.8)/FallingSword.POINT:0;
        double dzdy=b<1e-6||t>=1?0:dg*dh/b;
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
                    blade(b,bl,cam,x0[k],y0,s,a0,l);blade(b,bl,cam,x0[k+1],y0,s,a0,l);
                    blade(b,bl,cam,x1[k+1],y1,s,a1,l);blade(b,bl,cam,x1[k],y1,s,a1,l);
                }
            }
        }
    }
    private static void blade(BufferBuilder b,FallingSword.Blade bl,Vec3d cam,double x,double y,int side,float a,double l){
        double[] p=surface(x,y);
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
        double dh=y<FallingSword.POINT?IsosangRules.BLADE_WIDTH/2*1.8*Math.pow(1-y/FallingSword.POINT,.8)/FallingSword.POINT:0;
        Vec3d n=bl.across().multiply(s).add(bl.axis().multiply(-dh)).normalize();
        Vec3d at=bl.at(s*h,y,face*z).subtract(cam);
        b.vertex((float)at.x,(float)at.y,(float)at.z).texture(.5f,1-(float)(y/IsosangRules.BLADE_LENGTH)).color(1f,0f,0f,a).normal((float)n.x,(float)n.y,(float)n.z).next();
    }
}
