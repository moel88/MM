package dev.sangwoo.celestialfall.client.vfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The HDR effect layer. Effects that opt in are drawn on a floating-point copy of the frame instead of the
 * screen, so overlapping light keeps adding up past white. The world under them is first refracted by the
 * distortion they write, lit by their coloured lights and marked by their ground decals (all from the depth
 * buffer). Afterwards the light the effects added (layer minus world) feeds a six-level bloom, a game-time
 * afterglow and light shafts, and everything goes back to the screen with light above white turned white-hot,
 * plus the finisher's impact frame and chromatic split.
 *
 * <p>Order in a frame: {@link #begin} (copies the scene colour and depth, opens the distortion pass),
 * {@link #scene} (world pass, then the layer is bound for the effects), the caller's effect passes,
 * {@link #finish} (bloom, afterglow, shafts, composite to the screen). Raw GL is used only for the full-screen
 * passes; the program and vertex array they replace are put back, and every texture, blend and depth change
 * goes through the state cache.
 */
public final class FxLayer {
    private FxLayer(){}
    private static final int LEVELS=6;
    /** Game seconds over which the afterglow falls to 1/e. */
    static final float AFTERGLOW_TAU=.055f;
    private static ShaderProgram colorShader,distortShader;
    private static int depthProg,sceneProg,downProg,upProg,histProg,shaftProg,compProg,vao;
    private static boolean failed;
    private static int width,height,noiseTex,blackTex;
    private static int sceneCopyTex,sceneCopyFbo,depthTex,depthFbo,distortTex,distortFbo,baseTex,hdrTex,sceneFbo,hdrFbo,shaftTex,shaftFbo;
    private static final int[] downTex=new int[LEVELS],downFbo=new int[LEVELS],upTex=new int[LEVELS],upFbo=new int[LEVELS],levelW=new int[LEVELS],levelH=new int[LEVELS];
    private static final int[] histTex=new int[2],histFbo=new int[2];
    private static int histCurrent;
    private static boolean historyValid;
    private static double lastGameSeconds=Double.NaN;
    private static boolean drawn;
    // The frame in flight.
    private static Matrix4f viewProj;
    private static Vec3d camera;
    private static FxDistort distort;
    private static float[] clearColor=new float[4];

    public static void initialize(){
        CoreShaderRegistrationCallback.EVENT.register(c->{
            c.register(CelestialFall.id("fx_color"),VertexFormats.POSITION_COLOR,s->colorShader=s);
            c.register(CelestialFall.id("fx_distort"),VertexFormats.POSITION_TEXTURE_COLOR,s->distortShader=s);
        });
    }
    /** Whether the layer can run: shaders loaded, post-processing on, no shader-pack pipeline (it draws elsewhere). */
    public static boolean available(){
        if(failed||colorShader==null||distortShader==null||!CelestialFall.CONFIG.postProcessing)return false;
        var loader=FabricLoader.getInstance();
        return !loader.isModLoaded("iris")&&!loader.isModLoaded("oculus");
    }
    /** True once after a frame the layer drew in (the anime post chain then keeps its own bloom low). */
    public static boolean consumeDrawn(){boolean d=drawn;drawn=false;return d;}

    // ───────────────────────────── frame ─────────────────────────────

    /**
     * Copies the scene colour and depth and opens the distortion pass. {@code view} is the camera rotation of the
     * world pass, {@code matrix} the effect geometry matrix (rotation and camera translation). Returns the
     * distortion writer, or null when the layer cannot run this frame (nothing is changed then).
     */
    public static FxDistort begin(Matrix4f view,Matrix4f matrix,Vec3d cameraPos){
        if(!available())return null;
        var mc=MinecraftClient.getInstance();var main=mc.getFramebuffer();
        int w=main.textureWidth,h=main.textureHeight;if(w<=0||h<=0)return null;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),array=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try{
            setup(mc,w,h);
            int depth=main.getDepthAttachment();
            if(!attachDepth(distortFbo,depth)||!attachDepth(hdrFbo,depth))throw new IllegalStateException("The screen's depth buffer cannot be shared");
            try(MemoryStack stack=MemoryStack.stackPush()){FloatBuffer f=stack.mallocFloat(4);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,f);f.get(clearColor);}
            viewProj=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(view);camera=cameraPos;
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);
            // The scene as it is now (colour), and its depth as a plain float texture.
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.fbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,sceneCopyFbo);
            GlStateManager._glBlitFrameBuffer(0,0,w,h,0,0,w,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            target(depthFbo,w,h);use(depthProg);
            bind(0,depth);i(depthProg,"DepthTex",0);
            triangle();
            // The distortion pass: tested against the scene depth, written additively from zero.
            target(distortFbo,w,h);
            RenderSystem.clearColor(0,0,0,0);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,MinecraftClient.IS_SYSTEM_MAC);
            RenderSystem.clearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);
        }catch(Exception|LinkageError e){
            fail(e);GL20.glUseProgram(program);GL30.glBindVertexArray(array);main.beginWrite(true);return null;
        }
        GL20.glUseProgram(program);GL30.glBindVertexArray(array);
        RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
        var buffer=Tessellator.getInstance().getBuffer();buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        distort=new FxDistort(buffer,matrix);
        return distort;
    }
    /**
     * Draws the distortion, then the world pass (refraction, lights, decals) into the layer, and binds the layer
     * for the effects: depth tested against the scene, no depth writes, ordinary alpha blending.
     */
    public static void scene(FxFrame frame){
        var mc=MinecraftClient.getInstance();var main=mc.getFramebuffer();
        var built=distort.buffer().end();
        if(distort.quads()>0){
            var s=distortShader;
            if(s.modelViewMat!=null)s.modelViewMat.set(RenderSystem.getModelViewMatrix());
            if(s.projectionMat!=null)s.projectionMat.set(RenderSystem.getProjectionMatrix());
            if(s.screenSize!=null)s.screenSize.set((float)width,(float)height);
            var amount=s.getUniform("Amount");if(amount!=null)amount.set(height*.032f);
            s.bind();
            RenderSystem.enableBlend();RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ONE);
            RenderSystem.disableCull();
            BufferRenderer.draw(built);
            s.unbind();
        }else built.release();
        distort=null;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),array=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try{
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.defaultBlendFunc();
            target(sceneFbo,width,height);use(sceneProg);
            bind(0,sceneCopyTex);bind(1,distortTex);bind(2,depthTex);bind(3,noiseTex);
            i(sceneProg,"SceneTex",0);i(sceneProg,"DistortTex",1);i(sceneProg,"DepthTex",2);i(sceneProg,"NoiseTex",3);
            f2(sceneProg,"Texel",1f/width,1f/height);
            matrix(sceneProg,"InvViewProj",new Matrix4f(viewProj).invert());
            f3(sceneProg,"Anchor",(float)(camera.x%512),(float)(camera.y%512),(float)(camera.z%512));
            f(sceneProg,"LightGain",1f);
            List<FxFrame.Light> lights=frame.strongest(camera);
            List<FxFrame.Decal> decals=frame.nearest(camera);
            try(MemoryStack stack=MemoryStack.stackPush()){
                FloatBuffer pos=stack.mallocFloat(4*FxFrame.MAX_LIGHTS),col=stack.mallocFloat(4*FxFrame.MAX_LIGHTS);
                for(var l:lights){pos.put((float)(l.pos().x-camera.x)).put((float)(l.pos().y-camera.y)).put((float)(l.pos().z-camera.z)).put((float)l.radius());col.put(l.r()).put(l.g()).put(l.b()).put(1);}
                while(pos.position()<pos.capacity()){pos.put(0);col.put(0);}
                pos.flip();col.flip();
                GL20.glUniform4fv(GL20.glGetUniformLocation(sceneProg,"LightPos"),pos);GL20.glUniform4fv(GL20.glGetUniformLocation(sceneProg,"LightColor"),col);
                FloatBuffer a=stack.mallocFloat(4*FxFrame.MAX_DECALS),b=stack.mallocFloat(4*FxFrame.MAX_DECALS),c=stack.mallocFloat(4*FxFrame.MAX_DECALS);
                for(var d:decals){
                    a.put((float)(d.center().x-camera.x)).put((float)(d.center().y-camera.y)).put((float)(d.center().z-camera.z)).put((float)d.halfLength());
                    b.put((float)d.along().x).put((float)d.along().y).put((float)d.along().z).put((float)d.halfWidth());
                    c.put(d.heat()).put(d.alpha()).put(d.seed()).put(0);
                }
                while(a.position()<a.capacity()){a.put(0);b.put(0);c.put(0);}
                a.flip();b.flip();c.flip();
                GL20.glUniform4fv(GL20.glGetUniformLocation(sceneProg,"DecalA"),a);GL20.glUniform4fv(GL20.glGetUniformLocation(sceneProg,"DecalB"),b);GL20.glUniform4fv(GL20.glGetUniformLocation(sceneProg,"DecalC"),c);
            }
            GL20.glUniform1i(GL20.glGetUniformLocation(sceneProg,"LightCount"),lights.size());
            GL20.glUniform1i(GL20.glGetUniformLocation(sceneProg,"DecalCount"),decals.size());
            triangle();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        }catch(Exception|LinkageError e){fail(e);}
        GL20.glUseProgram(program);GL30.glBindVertexArray(array);
        target(hdrFbo,width,height);
        RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
    }
    /** Draws a batch of effect geometry (POSITION_COLOR) on the layer with soft edges against the world and a light gain (1 = as authored). */
    public static void draw(BufferBuilder.BuiltBuffer built,boolean additive,float gain,float soft){
        var s=colorShader;
        s.addSampler("Sampler1",depthTex);
        if(s.modelViewMat!=null)s.modelViewMat.set(RenderSystem.getModelViewMatrix());
        if(s.projectionMat!=null)s.projectionMat.set(RenderSystem.getProjectionMatrix());
        if(s.colorModulator!=null)s.colorModulator.set(gain,gain,gain,1f);
        if(s.screenSize!=null)s.screenSize.set((float)width,(float)height);
        var u=s.getUniform("Soft");if(u!=null)u.set(soft);
        s.bind();
        RenderSystem.enableBlend();RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        if(additive)RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);else RenderSystem.defaultBlendFunc();
        BufferRenderer.draw(built);
        s.unbind();
        RenderSystem.defaultBlendFunc();
    }
    /**
     * Bloom, afterglow and shafts from the light the effects added, then the composite back onto the screen.
     * {@code gameSeconds} drives the afterglow's fade, so it looks the same at any frame rate or game speed.
     */
    public static void finish(FxFrame frame,double gameSeconds){
        var mc=MinecraftClient.getInstance();var main=mc.getFramebuffer();
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),array=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try{
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            // Bloom: the first level keeps only the light the effects added.
            use(downProg);
            for(int k=0;k<LEVELS;k++){
                target(downFbo[k],levelW[k],levelH[k]);
                if(k==0){bind(0,hdrTex);bind(1,baseTex);f2(downProg,"Texel",1f/width,1f/height);}
                else{bind(0,downTex[k-1]);bind(1,blackTex);f2(downProg,"Texel",1f/levelW[k-1],1f/levelH[k-1]);}
                i(downProg,"SrcA",0);i(downProg,"SrcB",1);i(downProg,"First",k==0?1:0);
                triangle();
            }
            use(upProg);
            for(int k=LEVELS-2;k>=0;k--){
                int small=k==LEVELS-2?downTex[LEVELS-1]:upTex[k+1],sw=levelW[k+1],sh=levelH[k+1];
                target(upFbo[k],levelW[k],levelH[k]);
                bind(0,small);bind(1,downTex[k]);i(upProg,"Small",0);i(upProg,"Current",1);
                f2(upProg,"Texel",1f/sw,1f/sh);f(upProg,"Spread",1f);
                triangle();
            }
            // Afterglow: fades with game time; a first frame or a long gap starts it fresh.
            double dt=Double.isNaN(lastGameSeconds)?1:gameSeconds-lastGameSeconds;
            if(!historyValid||dt<0||dt>.5){
                for(int k=0;k<2;k++){target(histFbo[k],levelW[0],levelH[0]);RenderSystem.clearColor(0,0,0,0);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,MinecraftClient.IS_SYSTEM_MAC);}
                RenderSystem.clearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);
                historyValid=true;dt=0;
            }
            lastGameSeconds=gameSeconds;
            use(histProg);
            int next=1-histCurrent;target(histFbo[next],levelW[0],levelH[0]);
            bind(0,histTex[histCurrent]);bind(1,downTex[0]);i(histProg,"Prev",0);i(histProg,"Cur",1);
            f(histProg,"Decay",(float)Math.exp(-Math.max(0,dt)/AFTERGLOW_TAU));
            triangle();histCurrent=next;
            // Light shafts toward the strongest source on screen.
            int shafts=blackTex;float shaftGain=0;float[] focus={.5f,.5f};
            if(frame.shaftStrength()>0&&frame.shaft()!=null){
                Vec3d p=frame.shaft().subtract(camera);Vector4f clip=new Vector4f((float)p.x,(float)p.y,(float)p.z,1).mul(viewProj);
                if(clip.w>.05f){
                    float u=clip.x/clip.w*.5f+.5f,v=clip.y/clip.w*.5f+.5f;
                    focus[0]=Math.max(-.2f,Math.min(1.2f,u));focus[1]=Math.max(-.2f,Math.min(1.2f,v));
                    use(shaftProg);target(shaftFbo,levelW[0],levelH[0]);
                    bind(0,downTex[0]);i(shaftProg,"Src",0);
                    f2(shaftProg,"Center",focus[0],focus[1]);f(shaftProg,"Length",.85f);f(shaftProg,"Decay",.96f);
                    f2(shaftProg,"Aspect",(float)width/height,1f);
                    triangle();shafts=shaftTex;shaftGain=frame.shaftStrength();
                }
            }
            // Composite onto the screen.
            use(compProg);target(main.fbo,width,height);
            bind(0,hdrTex);bind(1,upTex[0]);bind(2,histTex[histCurrent]);bind(3,downTex[0]);bind(4,shafts);
            i(compProg,"HdrTex",0);i(compProg,"BloomTex",1);i(compProg,"HistTex",2);i(compProg,"LightTex",3);i(compProg,"ShaftTex",4);
            f(compProg,"BloomGain",(float)(.62*CelestialFall.CONFIG.bloomStrength/1.35));
            f(compProg,"Afterglow",.75f);
            f(compProg,"ShaftGain",shaftGain);
            int rgb=frame.shaftRgb();f3(compProg,"ShaftTint",((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);
            boolean flash=CelestialFall.CONFIG.screenFlash&&CelestialFall.CONFIG.animeImpactFrames;
            f(compProg,"Impact",flash?frame.impact():0);f(compProg,"ImpactMode",frame.impactMode());
            f(compProg,"Chroma",CelestialFall.CONFIG.screenDistortion?frame.chroma():0);
            f2(compProg,"Focus",focus[0],focus[1]);
            triangle();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            drawn=true;
        }catch(Exception|LinkageError e){fail(e);}
        GL20.glUseProgram(program);GL30.glBindVertexArray(array);
        main.beginWrite(true);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
    }
    /** Drops the afterglow when the layer was not used for a while (no stale light when the next cast starts). */
    public static void idle(){historyValid=false;lastGameSeconds=Double.NaN;}

    // ───────────────────────────── resources ─────────────────────────────

    private static void setup(MinecraftClient mc,int w,int h) throws Exception {
        if(vao==0){
            depthProg=program(mc,"depth.frag",null);sceneProg=program(mc,"scene.frag",new String[]{"baseOut","hdrOut"});
            downProg=program(mc,"down.frag",null);upProg=program(mc,"up.frag",null);histProg=program(mc,"history.frag",null);
            shaftProg=program(mc,"shafts.frag",null);compProg=program(mc,"composite.frag",null);
            vao=GL30.glGenVertexArrays();
            noiseTex=noise();
            blackTex=texture(GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,1,1,false);
        }
        if(w==width&&h==height&&hdrFbo!=0)return;
        freeTargets();width=w;height=h;
        sceneCopyTex=texture(GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,w,h,true);sceneCopyFbo=framebuffer(sceneCopyTex);
        depthTex=texture(GL30.GL_R32F,GL11.GL_RED,GL11.GL_FLOAT,w,h,false);depthFbo=framebuffer(depthTex);
        distortTex=texture(GL30.GL_RG16F,GL30.GL_RG,GL11.GL_FLOAT,w,h,true);distortFbo=framebuffer(distortTex);
        baseTex=texture(GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT,w,h,true);hdrTex=texture(GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT,w,h,true);
        sceneFbo=framebuffer(baseTex,hdrTex);hdrFbo=framebuffer(hdrTex);
        int lw=w,lh=h;
        for(int k=0;k<LEVELS;k++){
            lw=Math.max(1,lw/2);lh=Math.max(1,lh/2);levelW[k]=lw;levelH[k]=lh;
            downTex[k]=texture(GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT,lw,lh,true);downFbo[k]=framebuffer(downTex[k]);
            upTex[k]=texture(GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT,lw,lh,true);upFbo[k]=framebuffer(upTex[k]);
        }
        for(int k=0;k<2;k++){histTex[k]=texture(GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT,levelW[0],levelH[0],true);histFbo[k]=framebuffer(histTex[k]);}
        shaftTex=texture(GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT,levelW[0],levelH[0],true);shaftFbo=framebuffer(shaftTex);
        historyValid=false;
    }
    private static int noise(){
        int[] px=VfxNoise.abgr();int n=VfxNoise.SIZE;
        int t=TextureUtil.generateTextureId();GlStateManager._bindTexture(t);
        // 256 KB: too big for the thread's small native stack, so it goes on the native heap and is freed right after.
        ByteBuffer data=org.lwjgl.system.MemoryUtil.memAlloc(n*n*4);
        try{
            for(int v:px){data.put((byte)(v&255)).put((byte)((v>>8)&255)).put((byte)((v>>16)&255)).put((byte)((v>>>24)&255));}
            data.flip();
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,4);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,n,n,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        }finally{org.lwjgl.system.MemoryUtil.memFree(data);}
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);
        return t;
    }
    private static int texture(int internal,int format,int type,int w,int h,boolean linear){
        int t=TextureUtil.generateTextureId();GlStateManager._bindTexture(t);
        int filter=linear?GL11.GL_LINEAR:GL11.GL_NEAREST;
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,filter);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,filter);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL13.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL13.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,type,(IntBuffer)null);
        return t;
    }
    private static int framebuffer(int... colors){
        int f=GlStateManager.glGenFramebuffers();GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);
        for(int k=0;k<colors.length;k++)GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+k,GL11.GL_TEXTURE_2D,colors[k],0);
        if(colors.length>1){
            try(MemoryStack stack=MemoryStack.stackPush()){IntBuffer b=stack.mallocInt(colors.length);for(int k=0;k<colors.length;k++)b.put(GL30.GL_COLOR_ATTACHMENT0+k);b.flip();GL20.glDrawBuffers(b);}
        }
        int status=GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if(status!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete effect layer framebuffer: "+status);
        return f;
    }
    /** Shares the screen's depth texture with one of the layer's framebuffers. */
    private static boolean attachDepth(int fbo,int depth){
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
        return GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE;
    }
    private static void freeTargets(){
        int[] fbos={sceneCopyFbo,depthFbo,distortFbo,sceneFbo,hdrFbo,shaftFbo,histFbo[0],histFbo[1]};
        int[] texs={sceneCopyTex,depthTex,distortTex,baseTex,hdrTex,shaftTex,histTex[0],histTex[1]};
        for(int f:fbos)if(f!=0)GlStateManager._glDeleteFramebuffers(f);
        for(int t:texs)if(t!=0)TextureUtil.releaseTextureId(t);
        for(int k=0;k<LEVELS;k++){
            if(downFbo[k]!=0)GlStateManager._glDeleteFramebuffers(downFbo[k]);if(upFbo[k]!=0)GlStateManager._glDeleteFramebuffers(upFbo[k]);
            if(downTex[k]!=0)TextureUtil.releaseTextureId(downTex[k]);if(upTex[k]!=0)TextureUtil.releaseTextureId(upTex[k]);
            downFbo[k]=upFbo[k]=downTex[k]=upTex[k]=0;
        }
        sceneCopyFbo=depthFbo=distortFbo=sceneFbo=hdrFbo=shaftFbo=histFbo[0]=histFbo[1]=0;
        sceneCopyTex=depthTex=distortTex=baseTex=hdrTex=shaftTex=histTex[0]=histTex[1]=0;
        width=height=0;
    }
    public static void release(){
        if(!RenderSystem.isOnRenderThread()){RenderSystem.recordRenderCall(FxLayer::release);return;}
        freeTargets();
        for(int p:new int[]{depthProg,sceneProg,downProg,upProg,histProg,shaftProg,compProg})if(p!=0)GL20.glDeleteProgram(p);
        if(vao!=0)GL30.glDeleteVertexArrays(vao);
        if(noiseTex!=0)TextureUtil.releaseTextureId(noiseTex);if(blackTex!=0)TextureUtil.releaseTextureId(blackTex);
        depthProg=sceneProg=downProg=upProg=histProg=shaftProg=compProg=vao=noiseTex=blackTex=0;
        failed=false;historyValid=false;lastGameSeconds=Double.NaN;
    }
    private static void fail(Throwable e){
        failed=true;
        CelestialFall.LOGGER.error("The effect layer was switched off after a graphics error; effects are drawn without it",e);
    }

    // ───────────────────────────── GL helpers ─────────────────────────────

    private static int program(MinecraftClient mc,String fragment,String[] outputs) throws Exception {
        int vs=compile(mc,GL20.GL_VERTEX_SHADER,"fullscreen.vert"),fs=0,p=0;
        try{
            fs=compile(mc,GL20.GL_FRAGMENT_SHADER,fragment);
            p=GL20.glCreateProgram();GL20.glAttachShader(p,vs);GL20.glAttachShader(p,fs);
            if(outputs!=null)for(int k=0;k<outputs.length;k++)GL30.glBindFragDataLocation(p,k,outputs[k]);
            GL20.glLinkProgram(p);
            if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==GL11.GL_FALSE)throw new IllegalStateException(fragment+": "+GL20.glGetProgramInfoLog(p));
            int done=p;p=0;return done;
        }finally{GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs);if(p!=0)GL20.glDeleteProgram(p);}
    }
    private static int compile(MinecraftClient mc,int type,String file) throws Exception {
        String source;
        try(var stream=mc.getResourceManager().getResource(CelestialFall.id("shaders/fx/"+file)).orElseThrow().getInputStream()){
            source=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
        int shader=GL20.glCreateShader(type);
        GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE){String error=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IllegalStateException(file+": "+error);}
        return shader;
    }
    private static void target(int fbo,int w,int h){GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GlStateManager._viewport(0,0,w,h);}
    private static void use(int p){GL20.glUseProgram(p);GL30.glBindVertexArray(vao);}
    private static void bind(int unit,int texture){GlStateManager._activeTexture(GL13.GL_TEXTURE0+unit);GlStateManager._bindTexture(texture);}
    private static void triangle(){GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);}
    private static void i(int p,String name,int v){GL20.glUniform1i(GL20.glGetUniformLocation(p,name),v);}
    private static void f(int p,String name,float v){GL20.glUniform1f(GL20.glGetUniformLocation(p,name),v);}
    private static void f2(int p,String name,float a,float b){GL20.glUniform2f(GL20.glGetUniformLocation(p,name),a,b);}
    private static void f3(int p,String name,float a,float b,float c){GL20.glUniform3f(GL20.glGetUniformLocation(p,name),a,b,c);}
    private static void matrix(int p,String name,Matrix4f m){
        try(MemoryStack stack=MemoryStack.stackPush()){FloatBuffer b=stack.mallocFloat(16);m.get(b);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(p,name),false,b);}
    }
    /** Solids are drawn on the screen before the layer: they hide the effects behind them and are lit by them. */
    public static FxSolids solids(MatrixStack matrices,Vec3d cameraPos){return new FxSolids(MinecraftClient.getInstance(),matrices,cameraPos);}
    public static void finishSolids(FxSolids s){s.finish();}
}
