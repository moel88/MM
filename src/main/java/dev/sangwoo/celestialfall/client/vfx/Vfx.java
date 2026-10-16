package dev.sangwoo.celestialfall.client.vfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * Botand VFX rendering layer. One shader program ({@code vfx_energy}) whose materials scroll a
 * tileable noise texture in several layers (no per-pixel procedural noise), static unit meshes kept
 * on the GPU and placed with a model matrix (no per-frame vertex rebuild for the big structures), a
 * copy of the scene colour for refraction, and a small sprite batch for secondary sparks, debris,
 * smoke and flares. Every pass blends premultiplied (ONE, ONE_MINUS_SRC_ALPHA): alpha 0 adds light,
 * alpha 1 covers. Depth is tested against the world and never written.
 *
 * <p>The program is bound here for every draw and the blend function is set again right after the
 * bind: vanilla only re-applies a program's JSON blend when it differs from a cached state, and that
 * cache is not always up to date, so a later bind could otherwise switch the blending back.
 */
public final class Vfx {
    /** Material modes of vfx_energy.fsh. */
    public static final int BODY=1,SKIN=2,CAP=3,GROUND=4,RING=5,RING_REFRACT=6,HEAT_COLUMN=7,SPARK=8,FLARE=9,SMOKE=10,HAZE=11,SOLID=12,
        GLOW=13,CLOUD=14,SHOCK=15,POOL=16;
    private static final int SIDES=160,DISC=128,DOME_AZ=96,DOME_EL=24;
    private static ShaderProgram shader;
    private static NativeImageBackedTexture noise;
    private static VertexBuffer cylinderOut,cylinderIn,disc,dome;
    private static SimpleFramebuffer scene;
    private static int sceneTexture;
    private static boolean failed;
    private Vfx(){}

    public static void initialize(){
        CoreShaderRegistrationCallback.EVENT.register(c->c.register(CelestialFall.id("vfx_energy"),VertexFormats.POSITION_TEXTURE_COLOR,s->shader=s));
    }
    /** True once the shader is loaded and the GPU resources exist; builds them on the first call (render thread). */
    public static boolean ready(){
        if(shader==null||failed)return false;
        if(noise==null){
            try{build();}
            catch(RuntimeException|LinkageError e){
                failed=true;
                CelestialFall.LOGGER.error("Botand VFX could not be set up; the previous effect renderer is used instead",e);
                return false;
            }
        }
        return true;
    }
    /** Refraction samples a copy of the main framebuffer; shader-pack pipelines draw elsewhere, so it stays off there. */
    public static boolean refractionSupported(){
        var loader=FabricLoader.getInstance();
        return !loader.isModLoaded("iris")&&!loader.isModLoaded("oculus");
    }

    private static void build(){
        int[] px=VfxNoise.abgr();
        var image=new NativeImage(VfxNoise.SIZE,VfxNoise.SIZE,false);
        for(int y=0;y<VfxNoise.SIZE;y++)for(int x=0;x<VfxNoise.SIZE;x++)image.setColor(x,y,px[y*VfxNoise.SIZE+x]);
        noise=new NativeImageBackedTexture(image);
        // Mipmapped and repeating, so streaks squeezed toward a silhouette or seen from far do not shimmer.
        // The vanilla upload limits the texture to level 0 (MAX_LEVEL and MAX_LOD 0); both are opened up here.
        GlStateManager._bindTexture(noise.getGlId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL,8);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LOD,8);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);
        sceneTexture=noise.getGlId();
        cylinderOut=upload(cylinderMesh(true));
        cylinderIn=upload(cylinderMesh(false));
        disc=upload(discMesh());
        dome=upload(domeMesh());
    }
    private static VertexBuffer upload(BufferBuilder.BuiltBuffer built){
        var vb=new VertexBuffer(VertexBuffer.Usage.STATIC);vb.bind();vb.upload(built);VertexBuffer.unbind();return vb;
    }
    /** Unit cylinder: radius 1 around the y axis, y 0..1, uv.x around (0..1), uv.y up. Outward or inward front faces. */
    private static BufferBuilder.BuiltBuffer cylinderMesh(boolean outward){
        var b=new BufferBuilder(SIDES*4*VertexFormats.POSITION_TEXTURE_COLOR.getVertexSizeByte());
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        for(int i=0;i<SIDES;i++){
            float u0=(float)i/SIDES,u1=(float)(i+1)/SIDES;double a0=u0*Math.PI*2,a1=u1*Math.PI*2;
            float x0=(float)Math.cos(a0),z0=(float)Math.sin(a0),x1=(float)Math.cos(a1),z1=(float)Math.sin(a1);
            if(outward){v(b,x1,0,z1,u1,0);v(b,x0,0,z0,u0,0);v(b,x0,1,z0,u0,1);v(b,x1,1,z1,u1,1);}
            else{v(b,x0,0,z0,u0,0);v(b,x1,0,z1,u1,0);v(b,x1,1,z1,u1,1);v(b,x0,1,z0,u0,1);}
        }
        return b.end();
    }
    /** Unit disc in the xz plane (radius 1), uv = (x, z) mapped to 0..1. */
    private static BufferBuilder.BuiltBuffer discMesh(){
        var b=new BufferBuilder(DISC*4*VertexFormats.POSITION_TEXTURE_COLOR.getVertexSizeByte());
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        for(int i=0;i<DISC;i++){
            double a0=i*Math.PI*2/DISC,a1=(i+1)*Math.PI*2/DISC;
            float x0=(float)Math.cos(a0),z0=(float)Math.sin(a0),x1=(float)Math.cos(a1),z1=(float)Math.sin(a1);
            v(b,0,0,0,.5f,.5f);v(b,x0,0,z0,.5f+x0*.5f,.5f+z0*.5f);v(b,x1,0,z1,.5f+x1*.5f,.5f+z1*.5f);v(b,0,0,0,.5f,.5f);
        }
        return b.end();
    }
    /** Unit hemisphere above the xz plane (radius 1), uv.x around, uv.y from the rim (0) to the top (1); outward faces. */
    private static BufferBuilder.BuiltBuffer domeMesh(){
        var b=new BufferBuilder(DOME_AZ*DOME_EL*4*VertexFormats.POSITION_TEXTURE_COLOR.getVertexSizeByte());
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        for(int j=0;j<DOME_EL;j++){
            float v0=(float)j/DOME_EL,v1=(float)(j+1)/DOME_EL;double e0=v0*Math.PI/2,e1=v1*Math.PI/2;
            for(int i=0;i<DOME_AZ;i++){
                float u0=(float)i/DOME_AZ,u1=(float)(i+1)/DOME_AZ;double a0=u0*Math.PI*2,a1=u1*Math.PI*2;
                v(b,(float)(Math.cos(a1)*Math.cos(e0)),(float)Math.sin(e0),(float)(Math.sin(a1)*Math.cos(e0)),u1,v0);
                v(b,(float)(Math.cos(a0)*Math.cos(e0)),(float)Math.sin(e0),(float)(Math.sin(a0)*Math.cos(e0)),u0,v0);
                v(b,(float)(Math.cos(a0)*Math.cos(e1)),(float)Math.sin(e1),(float)(Math.sin(a0)*Math.cos(e1)),u0,v1);
                v(b,(float)(Math.cos(a1)*Math.cos(e1)),(float)Math.sin(e1),(float)(Math.sin(a1)*Math.cos(e1)),u1,v1);
            }
        }
        return b.end();
    }
    private static void v(BufferBuilder b,float x,float y,float z,float u,float w){b.vertex(x,y,z).texture(u,w).color(255,255,255,255).next();}

    /** Starts a group of VFX draws: depth tested, no depth writes, premultiplied blending. */
    public static void begin(){
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();blend();
    }
    private static void blend(){
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL30.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
    /** Ends a group: drops the scene copy binding and leaves culling off like the other effect renderers. */
    public static void end(){sceneTexture=noise==null?0:noise.getGlId();RenderSystem.disableCull();}

    /** Sets the material for the next draws. */
    public static void material(int mode,float time,float a,float b,float c,float d,float e,float f,float g,float h){
        shader.getUniform("Mode").set(mode);shader.getUniform("Time").set(time);
        shader.getUniform("Params").set(a,b,c,d);shader.getUniform("Params2").set(e,f,g,h);
    }
    /** Straight cylinder of radius r from y0 up {@code height} around (x,z): outward faces from outside, inward from inside. */
    public static void cylinder(Matrix4f rot,Vec3d cam,double x,double y0,double z,double r,double height,boolean cameraInside){
        mesh(cameraInside?cylinderIn:cylinderOut,rot,cam,x,y0,z,(float)r,(float)height,(float)r,true);
    }
    /** Hemisphere of radius r standing on (x,y,z); both sides drawn. */
    public static void dome(Matrix4f rot,Vec3d cam,double x,double y,double z,double r){
        mesh(dome,rot,cam,x,y,z,(float)r,(float)r,(float)r,false);
    }
    /** Horizontal disc of radius r centred at (x,y,z). */
    public static void disc(Matrix4f rot,Vec3d cam,double x,double y,double z,double r){
        mesh(disc,rot,cam,x,y,z,(float)r,1,(float)r,false);
    }
    private static void mesh(VertexBuffer vb,Matrix4f rot,Vec3d cam,double x,double y,double z,float sx,float sy,float sz,boolean cull){
        // Translate in doubles relative to the camera before going to floats: no jitter far from the origin.
        Matrix4f mv=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(rot)
            .translate((float)(x-cam.x),(float)(y-cam.y),(float)(z-cam.z)).scale(sx,sy,sz);
        if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        vb.bind();
        submit(mv,vb::draw);
        VertexBuffer.unbind();
        RenderSystem.disableCull();
    }
    /** Uploads the per-draw uniforms, binds the program, sets the blending again and issues the draw. */
    private static void submit(Matrix4f modelView,Runnable draw){
        shader.addSampler("Sampler0",noise.getGlId());shader.addSampler("Sampler1",sceneTexture);
        if(shader.modelViewMat!=null)shader.modelViewMat.set(modelView);
        if(shader.projectionMat!=null)shader.projectionMat.set(RenderSystem.getProjectionMatrix());
        if(shader.colorModulator!=null)shader.colorModulator.set(RenderSystem.getShaderColor());
        var window=MinecraftClient.getInstance().getWindow();
        if(shader.screenSize!=null)shader.screenSize.set((float)window.getFramebufferWidth(),(float)window.getFramebufferHeight());
        shader.bind();
        blend();
        draw.run();
        shader.unbind();
    }

    /** Copies the scene colour into Sampler1 for the refraction modes and rebinds the main framebuffer. */
    public static void captureScene(){
        var main=MinecraftClient.getInstance().getFramebuffer();int w=main.textureWidth,h=main.textureHeight;
        if(scene==null){scene=new SimpleFramebuffer(w,h,false,MinecraftClient.IS_SYSTEM_MAC);scene.setTexFilter(GL11.GL_LINEAR);}
        else if(scene.textureWidth!=w||scene.textureHeight!=h){scene.resize(w,h,MinecraftClient.IS_SYSTEM_MAC);scene.setTexFilter(GL11.GL_LINEAR);}
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.fbo);
        GlStateManager._glBlitFrameBuffer(0,0,w,h,0,0,w,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        main.beginWrite(true);
        sceneTexture=scene.getColorAttachment();
    }

    /** Camera-facing sprites and small solids in one material, built on the CPU each frame (only for secondary effects). */
    public static final class Sprites {
        private final BufferBuilder buffer;private final Matrix4f matrix;private final Vec3d cam;
        private int count;
        private Sprites(BufferBuilder buffer,Matrix4f matrix,Vec3d cam){this.buffer=buffer;this.matrix=matrix;this.cam=cam;}
        private void point(Vec3d p,float u,float v,int rgb,float alpha){
            buffer.vertex(matrix,(float)(p.x-cam.x),(float)(p.y-cam.y),(float)(p.z-cam.z)).texture(u,v)
                .color((rgb>>16)&255,(rgb>>8)&255,rgb&255,Math.max(0,Math.min(255,(int)(alpha*255)))).next();
        }
        /** Quad a-b-c-d with uv (u0,0) (u0+1,0) (u0+1,1) (u0,1); counter-clockwise is its front. */
        public void quad(Vec3d a,Vec3d b,Vec3d c,Vec3d d,float u0,int rgb,float alpha){
            if(alpha<=0)return;
            point(a,u0,0,rgb,alpha);point(b,u0+1,0,rgb,alpha);point(c,u0+1,1,rgb,alpha);point(d,u0,1,rgb,alpha);count++;
        }
        /** Quad from a to b turned toward the camera, {@code width} wide; uv.x across, uv.y along (a motion-stretched spark). */
        public void streak(Vec3d a,Vec3d b,double width,int rgb,float alpha){
            if(alpha<=0)return;Vec3d axis=b.subtract(a);
            Vec3d side=axis.crossProduct(cam.subtract(a.add(b).multiply(.5)));
            if(side.lengthSquared()<1e-10)return;side=side.normalize().multiply(width*.5);
            point(a.subtract(side),0,0,rgb,alpha);point(a.add(side),1,0,rgb,alpha);point(b.add(side),1,1,rgb,alpha);point(b.subtract(side),0,1,rgb,alpha);count++;
        }
        /** Camera-facing square of half-size r. */
        public void billboard(Vec3d p,double r,int rgb,float alpha){
            if(alpha<=0||r<=0)return;Vec3d n=cam.subtract(p);if(n.lengthSquared()<1e-8)return;n=n.normalize();
            Vec3d u=n.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();
            Vec3d v=u.crossProduct(n).normalize();u=u.multiply(r);v=v.multiply(r);
            point(p.subtract(u).subtract(v),0,0,rgb,alpha);point(p.add(u).subtract(v),1,0,rgb,alpha);
            point(p.add(u).add(v),1,1,rgb,alpha);point(p.subtract(u).add(v),0,1,rgb,alpha);count++;
        }
    }
    /** Starts a sprite batch in one material; draw it with {@link #draw(Sprites,boolean)}. */
    public static Sprites sprites(Matrix4f rot,Vec3d cam,int mode,float time,float a,float b,float c,float d){
        material(mode,time,a,b,c,d,0,0,0,0);
        var buffer=Tessellator.getInstance().getBuffer();buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        return new Sprites(buffer,rot,cam);
    }
    /** Draws a sprite batch; {@code cull} for closed solids whose faces wind counter-clockwise from outside. */
    public static void draw(Sprites s,boolean cull){
        var built=s.buffer.end();
        if(s.count==0){built.release();return;}
        if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        submit(RenderSystem.getModelViewMatrix(),()->BufferRenderer.draw(built));
        RenderSystem.disableCull();
    }
}
