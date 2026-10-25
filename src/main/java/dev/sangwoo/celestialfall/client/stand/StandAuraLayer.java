package dev.sangwoo.celestialfall.client.stand;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.vfx.VfxNoise;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * The aura round Star Platinum and its user (2.53.0, user request: "아우라 전체적으로 리메이크 해줘 몸 전체를 두르고 있는
 * 아우라를 만들어줘 플레이어도 아우라가 몸 전체에 둘러지게", and "걸을때 뒤에 이펙트 안 남기게"). Every frame the stands that are
 * out and their users are drawn once more, just as they are, into two masks of their own that share the scene's depth
 * (so what hides a body hides its aura); the masks are shrunk to half size and blurred into a glow; and the glow is laid
 * on the screen as a violet aura wrapping each whole body: its edge licks upwards in tongues that flow up out of the body
 * and flicker, a brighter violet near the body, deep violet out in the tongues. It is drawn only round the bodies, never
 * on them, and never white ("몸에 흰색 테두리는 없애고": a white-hot rim just inside the outline was tried first). It
 * belongs to the bodies on screen, so it goes where they go and leaves nothing behind. Its pattern is measured in blocks
 * round the nearest stand (it keeps its size as the stand comes nearer, and moves with it on screen) and flows with the
 * clock, so it moves at the frame rate. With a shader pack, or post-processing off, it is not drawn; the stand then
 * glows with its shells instead (StarPlatinumRenderer). Raw GL is used only for the full-screen passes, and the program,
 * vertex array and framebuffer they change are put back.
 * <p>
 * It also draws the sky-blue glow of a body using a skill with a cutscene (2.65.0, CutsceneFlash: "몸 전체가 하늘색으로
 * 빛이 나야하는 쪽에 가까움 그러니까 몸 색깔을 바꾸는건 X"): the body into a third mask of its own, blurred further out
 * (GLOW blocks), laid on the screen as sky-blue light spreading out round its whole outline, palest and brightest right
 * at it, never on the body.
 */
public final class StandAuraLayer {
    private StandAuraLayer(){}
    /** Something wrapped in the aura this frame (a stand's body or its user's) or glowing sky blue, and how strongly (0 to 1). */
    record Bearer(Entity body,int kind,float strength){
        boolean stand(){return kind==STAND;}
    }
    /** The kinds of bearer, each its own mask: a stand, its user, a body glowing sky blue (CutsceneFlash). */
    static final int STAND=0,USER=1,GLOW=2;
    /** The users' aura beside the stands' (a little fainter). */
    static final float USER_GAIN=.8f;
    /** How far the glow reaches beyond a body (blocks), and how high its tongues of flame can lick above it. */
    static final float REACH=.17f,LICK=.45f;
    /** How far the sky-blue glow reaches beyond a body (blocks). */
    static final float GLOW_REACH=.42f;
    /** How fast the three layers of flame flow upwards, in texture units a second: their patterns are about 3, 1.4 and
     * 0.6 blocks a unit (aura.frag), so each rises at about a block a second, the finer ones flickering faster. */
    static final double[] FLOW={.32,.85,1.9};
    private static boolean failed;
    private static int packProg,blurProg,auraProg,vao,noiseTex,width,height;
    private static final int[] maskTex=new int[3],maskFbo=new int[3],haloTex=new int[2],haloFbo=new int[2];
    private static int drawnLast,glowedLast;
    private static Masks masks;

    /** Whether the aura can be drawn: post-processing on and no shader pack (which draws the world its own way). */
    public static boolean available(){
        if(failed||!CelestialFall.CONFIG.postProcessing)return false;
        var loader=FabricLoader.getInstance();
        return !loader.isModLoaded("iris")&&!loader.isModLoaded("oculus");
    }
    /** How many bodies the last frame wrapped in the aura, and made glow sky blue (for the capture tools' logs). */
    public static int drawnLast(){return drawnLast;}
    public static int glowedLast(){return glowedLast;}

    /** The three layers' flow at {@code seconds}: each wraps from 1 back to 0, which the tiling noise does not show. */
    static float[] flow(double seconds){
        float[] out=new float[FLOW.length];
        for(int k=0;k<FLOW.length;k++){double v=seconds*FLOW[k];out[k]=(float)(v-Math.floor(v));}
        return out;
    }
    /** Screen pixels a block {@code distance} blocks away, for a screen {@code height} tall and a projection's y scale. */
    static float scale(int height,float projY,double distance){return (float)(height*.5*projY/Math.max(.3,distance));}

    /**
     * Wraps the bearers in the aura, on the screen as it is after the entities. {@code seconds} is the client clock,
     * {@code flare} how much brighter it burns (0 to 1, as a stand bursts into its pose).
     */
    static void draw(WorldRenderContext c,List<Bearer> bearers,double seconds,float flare){
        drawnLast=glowedLast=0;
        if(bearers.isEmpty()||!available())return;
        var mc=MinecraftClient.getInstance();var main=mc.getFramebuffer();
        int w=main.textureWidth,h=main.textureHeight;if(w<=0||h<=0)return;
        float delta=c.tickDelta();var cam=c.camera().getPos();
        Matrix4f viewProj=new Matrix4f(c.projectionMatrix()).mul(c.matrixStack().peek().getPositionMatrix());
        // where they are on screen: a box round them all with room for the glow and the flames, and the nearest stand
        float x0=Float.MAX_VALUE,y0=Float.MAX_VALUE,x1=-Float.MAX_VALUE,y1=-Float.MAX_VALUE;boolean whole=false;
        Bearer nearest=null;double best=Double.MAX_VALUE,stands=0,users=0;int standCount=0,userCount=0;boolean glows=false;
        for(var b:bearers){
            var e=b.body();var box=e.getBoundingBox().offset(e.getLerpedPos(delta).subtract(e.getPos()));
            box=b.kind()==GLOW?box.expand(GLOW_REACH+.2):box.expand(REACH+.2).stretch(0,LICK,0);
            glows|=b.kind()==GLOW;
            for(int k=0;k<8;k++){
                var v=new Vector4f((float)(((k&1)==0?box.minX:box.maxX)-cam.x),(float)(((k&2)==0?box.minY:box.maxY)-cam.y),(float)(((k&4)==0?box.minZ:box.maxZ)-cam.z),1).mul(viewProj);
                if(v.w<.05f){whole=true;continue;}
                float px=(v.x/v.w*.5f+.5f)*w,py=(v.y/v.w*.5f+.5f)*h;
                x0=Math.min(x0,px);y0=Math.min(y0,py);x1=Math.max(x1,px);y1=Math.max(y1,py);
            }
            double away=e.getLerpedPos(delta).add(0,e.getHeight()*.5,0).distanceTo(cam);
            if(b.kind()==STAND){stands+=away;standCount++;}else if(b.kind()==USER){users+=away;userCount++;}
            double d=away-(b.stand()?1000:0);
            if(d<best){best=d;nearest=b;}
        }
        if(whole){x0=0;y0=0;x1=w;y1=h;}
        int sx=Math.max(0,(int)Math.floor(x0)),sy=Math.max(0,(int)Math.floor(y0)),ex=Math.min(w,(int)Math.ceil(x1)),ey=Math.min(h,(int)Math.ceil(y1));
        if(ex<=sx||ey<=sy)return;
        var centre=nearest.body().getLerpedPos(delta).add(0,nearest.body().getHeight()*.55,0);
        float px=w*.5f,py=h*.5f;
        var a=new Vector4f((float)(centre.x-cam.x),(float)(centre.y-cam.y),(float)(centre.z-cam.z),1).mul(viewProj);
        if(a.w>.05f){px=(a.x/a.w*.5f+.5f)*w;py=(a.y/a.w*.5f+.5f)*h;}
        float pixels=scale(h,c.projectionMatrix().m11(),centre.distanceTo(cam));
        // the frame's bodies first, so the aura is laid round them on the finished picture
        if(c.consumers() instanceof VertexConsumerProvider.Immediate im)im.drawCurrentLayer();
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),array=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        float[] clear=new float[4];GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);
        try{
            setup(mc,w,h);
            if(masks==null)masks=new Masks(VertexConsumerProvider.immediate(new BufferBuilder(4096)));
            int depth=main.getDepthAttachment();
            // the masks: each body drawn again as it is, tested against the scene's depth
            for(int k=0;k<3;k++){
                if(!attachDepth(maskFbo[k],depth))throw new IllegalStateException("The screen's depth buffer cannot be shared");
                GlStateManager._viewport(0,0,w,h);
                RenderSystem.clearColor(0,0,0,0);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,MinecraftClient.IS_SYSTEM_MAC);
                for(var b:bearers)if(b.kind()==k){body(mc,c,b,cam,delta);if(k==GLOW)glowedLast++;else drawnLast++;}
                masks.inner.draw();
            }
            RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3]);
            // the glow: half size, blurred across and up
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);
            int hw=Math.max(1,w/2),hh=Math.max(1,h/2);
            // both glow targets empty outside this frame's box: the flames and the blur read beyond it, and must not
            // find an earlier frame's glow there
            for(int k=0;k<2;k++){
                target(haloFbo[k],hw,hh);
                RenderSystem.clearColor(0,0,0,0);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,MinecraftClient.IS_SYSTEM_MAC);
            }
            RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3]);
            RenderSystem.enableScissor(sx/2,sy/2,Math.max(1,(ex-sx+3)/2),Math.max(1,(ey-sy+3)/2));
            use(packProg);target(haloFbo[0],hw,hh);
            bind(0,maskTex[0]);bind(1,maskTex[1]);bind(2,maskTex[2]);i(packProg,"MaskA",0);i(packProg,"MaskB",1);i(packProg,"MaskC",2);f2(packProg,"Texel",1f/w,1f/h);
            triangle();
            // the blur's 13 taps reach 7.5 steps each way: the glow's reach at the stand's distance, in half-size pixels
            // (the sky-blue glow's further: GLOW_REACH)
            float step=MathHelper.clamp(REACH*pixels*.5f/7.5f,.6f,4f),far=MathHelper.clamp(GLOW_REACH*pixels*.5f/7.5f,.6f,9f);
            use(blurProg);i(blurProg,"Src",0);
            target(haloFbo[1],hw,hh);bind(0,haloTex[0]);f2(blurProg,"Step",step/hw,0);f2(blurProg,"StepB",far/hw,0);triangle();
            target(haloFbo[0],hw,hh);bind(0,haloTex[1]);f2(blurProg,"Step",0,step/hh);f2(blurProg,"StepB",0,far/hh);triangle();
            RenderSystem.disableScissor();
            // the aura, on the screen
            RenderSystem.enableScissor(sx,sy,ex-sx,ey-sy);
            use(auraProg);target(main.fbo,w,h);
            bind(0,maskTex[0]);bind(1,maskTex[1]);bind(2,haloTex[0]);bind(3,noiseTex);bind(4,maskTex[2]);
            i(auraProg,"MaskA",0);i(auraProg,"MaskB",1);i(auraProg,"Halo",2);i(auraProg,"Noise",3);i(auraProg,"MaskC",4);f(auraProg,"Glow",glows?1f:0f);
            f2(auraProg,"Texel",1f/w,1f/h);f(auraProg,"Scale",pixels);f2(auraProg,"Anchor",px,py);
            float[] phase=flow(seconds);f3(auraProg,"Phase",phase[0],phase[1],phase[2]);
            f2(auraProg,"Gain",1f,USER_GAIN);f(auraProg,"Flare",MathHelper.clamp(flare,0,1));
            // which kind is nearer the camera (on average): the one behind keeps its glow off the other
            f(auraProg,"Front",userCount>0&&standCount>0&&users/userCount<stands/standCount?1f:0f);
            RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE,GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            triangle();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        }catch(Exception|LinkageError e){
            failed=true;drawnLast=glowedLast=0;
            CelestialFall.LOGGER.error("The stand aura was switched off after a graphics error; stands glow without it",e);
        }finally{
            RenderSystem.disableScissor();
            GL20.glUseProgram(program);GL30.glBindVertexArray(array);
            main.beginWrite(true);
            RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3]);
            RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.enableCull();
        }
    }
    /** One body into the mask bound now: drawn as the game draws it (no shadow, no name tag), as solid as its strength. */
    private static void body(MinecraftClient mc,WorldRenderContext c,Bearer b,Vec3d cam,float delta){
        Entity e=b.body();var dispatcher=mc.getEntityRenderDispatcher();var renderer=dispatcher.getRenderer(e);
        double x=MathHelper.lerp(delta,e.prevX,e.getX()),y=MathHelper.lerp(delta,e.prevY,e.getY()),z=MathHelper.lerp(delta,e.prevZ,e.getZ());
        float yaw=MathHelper.lerp(delta,e.prevYaw,e.getYaw());
        var off=renderer.getPositionOffset(e,delta);
        var m=c.matrixStack();m.push();
        m.translate(x-cam.x+off.x,y-cam.y+off.y,z-cam.z+off.z);
        masks.alpha=MathHelper.clamp(b.strength(),0,1);
        renderer.render(e,yaw,delta,m,masks,dispatcher.getLight(e,delta));
        m.pop();
    }

    /** The masks' buffers: every layer as the game draws it, but faded by the bearer's strength, and no name tags. */
    private static final class Masks implements VertexConsumerProvider {
        final VertexConsumerProvider.Immediate inner;float alpha=1;
        Masks(VertexConsumerProvider.Immediate inner){this.inner=inner;}
        @Override public VertexConsumer getBuffer(RenderLayer layer){
            if(layer.toString().startsWith("RenderType[text"))return Nothing.INSTANCE;
            var vc=inner.getBuffer(layer);
            return alpha>=.999f?vc:new Faded(vc,alpha);
        }
    }
    /** A body drawn fainter: its alpha scaled (how solid it is in the mask). */
    private record Faded(VertexConsumer inner,float alpha) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x,double y,double z){inner.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){inner.color(r,g,b,Math.round(a*alpha));return this;}
        @Override public VertexConsumer texture(float u,float v){inner.texture(u,v);return this;}
        @Override public VertexConsumer overlay(int u,int v){inner.overlay(u,v);return this;}
        @Override public VertexConsumer light(int u,int v){inner.light(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){inner.normal(x,y,z);return this;}
        @Override public void next(){inner.next();}
        @Override public void fixedColor(int r,int g,int b,int a){inner.fixedColor(r,g,b,Math.round(a*alpha));}
        @Override public void unfixColor(){inner.unfixColor();}
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){
            inner.vertex(x,y,z,r,g,b,a*alpha,u,v,overlay,light,nx,ny,nz);
        }
    }
    /** Where a name tag goes: nowhere. */
    private static final class Nothing implements VertexConsumer {
        static final Nothing INSTANCE=new Nothing();
        @Override public VertexConsumer vertex(double x,double y,double z){return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer normal(float x,float y,float z){return this;}
        @Override public void next(){}
        @Override public void fixedColor(int r,int g,int b,int a){}
        @Override public void unfixColor(){}
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){}
    }

    // ───────────────────────────── resources ─────────────────────────────

    private static void setup(MinecraftClient mc,int w,int h) throws Exception {
        if(vao==0){
            packProg=program(mc,"pack.frag");blurProg=program(mc,"blur.frag");auraProg=program(mc,"aura.frag");
            vao=GL30.glGenVertexArrays();noiseTex=noise();
        }
        if(w==width&&h==height&&maskFbo[0]!=0)return;
        freeTargets();width=w;height=h;
        int hw=Math.max(1,w/2),hh=Math.max(1,h/2);
        for(int k=0;k<3;k++){maskTex[k]=texture(w,h);maskFbo[k]=framebuffer(maskTex[k],false);}
        for(int k=0;k<2;k++){haloTex[k]=texture(hw,hh);haloFbo[k]=framebuffer(haloTex[k],true);}
    }
    private static int noise(){
        int[] px=VfxNoise.abgr();int n=VfxNoise.SIZE;
        int t=TextureUtil.generateTextureId();GlStateManager._bindTexture(t);
        ByteBuffer data=org.lwjgl.system.MemoryUtil.memAlloc(n*n*4);
        try{
            for(int v:px)data.put((byte)(v&255)).put((byte)((v>>8)&255)).put((byte)((v>>16)&255)).put((byte)((v>>>24)&255));
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
    private static int texture(int w,int h){
        int t=TextureUtil.generateTextureId();GlStateManager._bindTexture(t);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL13.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL13.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(IntBuffer)null);
        return t;
    }
    /** A framebuffer drawing into one texture; {@code complete} checks it now (a mask's gets the screen's depth later). */
    private static int framebuffer(int color,boolean complete){
        int f=GlStateManager.glGenFramebuffers();GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        if(complete){
            int status=GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if(status!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete aura framebuffer: "+status);
        }
        return f;
    }
    /** Binds a mask's framebuffer with the screen's depth texture in it. */
    private static boolean attachDepth(int fbo,int depth){
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
        return GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE;
    }
    private static void freeTargets(){
        for(int k=0;k<3;k++){
            if(maskFbo[k]!=0)GlStateManager._glDeleteFramebuffers(maskFbo[k]);if(maskTex[k]!=0)TextureUtil.releaseTextureId(maskTex[k]);
            maskFbo[k]=maskTex[k]=0;
        }
        for(int k=0;k<2;k++){
            if(haloFbo[k]!=0)GlStateManager._glDeleteFramebuffers(haloFbo[k]);if(haloTex[k]!=0)TextureUtil.releaseTextureId(haloTex[k]);
            haloFbo[k]=haloTex[k]=0;
        }
        width=height=0;
    }

    // ───────────────────────────── GL helpers ─────────────────────────────

    private static int program(MinecraftClient mc,String fragment) throws Exception {
        int vs=compile(mc,GL20.GL_VERTEX_SHADER,"fullscreen.vert"),fs=0,p=0;
        try{
            fs=compile(mc,GL20.GL_FRAGMENT_SHADER,fragment);
            p=GL20.glCreateProgram();GL20.glAttachShader(p,vs);GL20.glAttachShader(p,fs);
            GL20.glLinkProgram(p);
            if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==GL11.GL_FALSE)throw new IllegalStateException(fragment+": "+GL20.glGetProgramInfoLog(p));
            int done=p;p=0;return done;
        }finally{GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs);if(p!=0)GL20.glDeleteProgram(p);}
    }
    private static int compile(MinecraftClient mc,int type,String file) throws Exception {
        String source;
        try(var stream=mc.getResourceManager().getResource(CelestialFall.id("shaders/aura/"+file)).orElseThrow().getInputStream()){
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
}
