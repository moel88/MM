package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/**
 * Star Platinum's time stop drawn over the frame (2.67.0): the wave and the stopped world (StandTimeStop.wave) through
 * shaders/timestop/timestop.frag. For whom it did not stop it is drawn at the end of the world pass, before the effects
 * (KagenoRenderer: the stand's punches keep their colour in the stopped world, as R's cuts do in its gray one), where
 * the scene's depth is copied to tell how far from the middle of the stopped time each pixel is. For whom it stopped the
 * frame of the moment it stopped is kept (its colour, and its depth from that frame's world pass) and drawn in place of
 * every frame after, the wave spreading over it, until time moves again (holdStill, letGo): the world they see stands
 * still, its user too ("당한 사람은 시전자도 멈춰보이고 전부 멈춰보이고 풀려야 다시 보여"). When the frame is a shader
 * pack's (Iris, Oculus: the world pass does not draw into the game's own framebuffer), there is no depth to read: the
 * wave is drawn over the finished frame as a circle on the screen round where its middle shows.
 */
public final class TimeStopView {
    private static int program,vao;private static boolean failed;
    /** The frame copied to be drawn back through the shader, the scene's depth copied, and the still (its colour and depth). */
    private static SimpleFramebuffer scene,depth,still;
    /** The world pass's camera this frame (its view and projection, inverted) and where it was; the still's. */
    private static final Matrix4f projView=new Matrix4f(),inverse=new Matrix4f(),stillProjView=new Matrix4f(),stillInverse=new Matrix4f();
    private static Vec3d eye=Vec3d.ZERO,stillEye=Vec3d.ZERO;
    /** This frame: whether the world pass saw the camera, copied the depth, drew the wave. */
    private static boolean seen,depthNow,drawn;
    /** The still: wanted (this client's player stopped), taken, and whether it has its depth. */
    private static boolean wanted,taken,stillDepth;
    private TimeStopView(){}

    /** This client's player is stopped: the frame now drawn is kept, and shown until time moves again. */
    static void holdStill(){wanted=true;taken=false;}
    /** Time moves again: the frames go on. */
    static void letGo(){wanted=false;taken=false;}
    /** Whether the still is being shown in place of the frames (for the capture tools' logs). */
    public static boolean showingStill(){return wanted&&taken;}

    /**
     * The end of the world pass, before the effects: the camera is noted (and, wanted for the still, the depth kept), and
     * the wave drawn over the frame for whom it did not stop.
     */
    public static void world(WorldRenderContext c){
        seen=depthNow=drawn=false;
        var mc=MinecraftClient.getInstance();
        if(failed||mc.world==null||StandTimeStop.STOPS.isEmpty())return;
        projView.set(c.projectionMatrix()).mul(c.matrixStack().peek().getPositionMatrix());
        projView.invert(inverse);eye=c.camera().getPos();seen=true;
        boolean keep=wanted&&!taken;
        var wave=wanted?null:StandTimeStop.wave(c.tickDelta(),eye);
        if(!keep&&wave==null)return;
        var main=mc.getFramebuffer();
        // a shader pack's frame: no depth to read here (the wave goes over the finished frame instead, post)
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.fbo)return;
        int w=main.textureWidth,h=main.textureHeight;if(w<=0||h<=0)return;
        try(State state=new State()){
            buffers(mc,w,h);
            depth.copyDepthFrom(main);depthNow=true;
            if(wave!=null){draw(main,true,scene,depth.getDepthAttachment(),true,wave,eye,inverse,projView);drawn=true;}
        }catch(Exception|LinkageError e){fail(e);}
    }
    /**
     * After the world and its post-processing, before the HUD: for whom it stopped, the still (taken now if it is not
     * yet: this frame, the moment it stopped) with the wave over it; for the rest, the wave over the finished frame if the
     * world pass could not draw it (a shader pack's frame).
     */
    public static void post(float delta){
        var mc=MinecraftClient.getInstance();
        if(failed||mc.world==null)return;
        if(!wanted&&(drawn||StandTimeStop.STOPS.isEmpty()))return;
        var main=mc.getFramebuffer();int w=main.textureWidth,h=main.textureHeight;if(w<=0||h<=0)return;
        try(State state=new State()){
            buffers(mc,w,h);
            if(wanted){
                if(!taken){
                    blit(main,still,GL11.GL_COLOR_BUFFER_BIT);
                    stillDepth=depthNow;
                    if(depthNow)blit(depth,still,GL11.GL_DEPTH_BUFFER_BIT);
                    stillProjView.set(projView);stillInverse.set(inverse);stillEye=seen?eye:mc.gameRenderer.getCamera().getPos();
                    taken=true;
                }
                var wave=StandTimeStop.wave(delta,stillEye);
                draw(main,false,still,still.getDepthAttachment(),stillDepth,wave,stillEye,stillInverse,stillProjView);
                return;
            }
            var wave=StandTimeStop.wave(delta,seen?eye:mc.gameRenderer.getCamera().getPos());
            if(wave==null||!seen)return;
            draw(main,true,scene,0,false,wave,eye,inverse,projView);
        }catch(Exception|LinkageError e){fail(e);}
    }

    /**
     * Draws {@code from} (the frame copied first if {@code copy}, else the still) through the shader into the game's
     * framebuffer, with the wave's uniforms ({@code wave} null: drawn as it is).
     */
    private static void draw(Framebuffer main,boolean copy,Framebuffer from,int depthTex,boolean hasDepth,StandTimeStop.Wave wave,Vec3d cam,Matrix4f inv,Matrix4f pv){
        int w=main.textureWidth,h=main.textureHeight;
        if(copy)blit(main,from,GL11.GL_COLOR_BUFFER_BIT);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,main.fbo);GL11.glViewport(0,0,w,h);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);GL11.glColorMask(true,true,true,true);
        GL20.glUseProgram(program);GL30.glBindVertexArray(vao);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,from.getColorAttachment());
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,hasDepth?depthTex:from.getColorAttachment());
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        i("SceneTex",0);i("DepthTex",1);
        try(MemoryStack stack=MemoryStack.stackPush()){
            FloatBuffer m=stack.mallocFloat(16);inv.get(m);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"InvProjView"),false,m);
        }
        if(wave==null){
            f("Radius",0);f("Band",1);f("BandSide",1);f("Negative",0);f("Rim",0);f("Grade",0);f("Outer",0);f("Flash",0);f("HasDepth",0);
            GL20.glUniform3f(GL20.glGetUniformLocation(program,"Center"),0,0,0);GL20.glUniform2f(GL20.glGetUniformLocation(program,"CenterScreen"),.5f,.5f);
            f("ScreenRadius",0);f("Aspect",w/(float)h);
        }else{
            var c=wave.center().subtract(cam);
            GL20.glUniform3f(GL20.glGetUniformLocation(program,"Center"),(float)c.x,(float)c.y,(float)c.z);
            f("Radius",wave.radius());f("Band",wave.band());f("BandSide",wave.side());f("Negative",wave.negative());f("Rim",wave.rim());
            f("Grade",wave.grade());f("Outer",wave.outer());f("Flash",wave.flash());f("HasDepth",hasDepth?1:0);
            // without the depth: the wave as a circle on the screen round where its middle shows (off to one side, or
            // the middle of the screen if it is behind)
            var p=new Vector4f((float)c.x,(float)c.y,(float)c.z,1).mul(pv);
            float sx=.5f,sy=.5f;
            if(p.w>.05f){sx=p.x/p.w*.5f+.5f;sy=p.y/p.w*.5f+.5f;}
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"CenterScreen"),sx,sy);
            f("ScreenRadius",wave.radius()/StandTimeStop.REACH*1.8f);f("Aspect",w/(float)h);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    }
    private static void blit(Framebuffer a,Framebuffer b,int what){
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,a.fbo);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,b.fbo);
        GL30.glBlitFramebuffer(0,0,a.textureWidth,a.textureHeight,0,0,b.textureWidth,b.textureHeight,what,GL11.GL_NEAREST);
    }
    private static void f(String name,float v){GL20.glUniform1f(GL20.glGetUniformLocation(program,name),v);}
    private static void i(String name,int v){GL20.glUniform1i(GL20.glGetUniformLocation(program,name),v);}
    /** Makes the shader and the framebuffers (the still keeps its size while it is shown: it is drawn stretched to the screen). */
    private static void buffers(MinecraftClient mc,int w,int h) throws Exception {
        if(program==0){
            int vs=0,fs=0,p=0;
            try{
                vs=compile(mc,GL20.GL_VERTEX_SHADER,"fullscreen.vert");fs=compile(mc,GL20.GL_FRAGMENT_SHADER,"timestop.frag");
                p=GL20.glCreateProgram();GL20.glAttachShader(p,vs);GL20.glAttachShader(p,fs);GL20.glLinkProgram(p);
                if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==GL11.GL_FALSE)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));
                program=p;p=0;vao=GL30.glGenVertexArrays();
            }finally{if(vs!=0)GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs);if(p!=0)GL20.glDeleteProgram(p);}
        }
        scene=size(scene,w,h,false);depth=size(depth,w,h,true);
        if(!(wanted&&taken))still=size(still,w,h,true);
    }
    private static SimpleFramebuffer size(SimpleFramebuffer fb,int w,int h,boolean withDepth){
        if(fb==null)return new SimpleFramebuffer(w,h,withDepth,MinecraftClient.IS_SYSTEM_MAC);
        if(fb.textureWidth!=w||fb.textureHeight!=h)fb.resize(w,h,MinecraftClient.IS_SYSTEM_MAC);
        return fb;
    }
    private static int compile(MinecraftClient mc,int type,String file) throws Exception {
        String source;
        try(var stream=mc.getResourceManager().getResource(CelestialFall.id("shaders/timestop/"+file)).orElseThrow().getInputStream()){
            source=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
        int shader=GL20.glCreateShader(type);
        GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE){String error=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IllegalStateException(error);}
        return shader;
    }
    /** A graphics error: the time stop draws nothing over the frame from now on (its rules and sounds go on). */
    private static void fail(Throwable e){failed=true;CelestialFall.LOGGER.error("Time stop view failed; it is off from now on",e);}
    public static void release(){
        if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()){com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(TimeStopView::release);return;}
        if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);program=vao=0;
        for(var fb:new SimpleFramebuffer[]{scene,depth,still})if(fb!=null)fb.delete();
        scene=depth=still=null;taken=false;failed=false;
    }

    /** The raw GL state touched here, put back as it was so that the game's cached state stays true. */
    private static final class State implements AutoCloseable {
        final int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),tex0,tex1;
        final boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int[] viewport=new int[4];final boolean[] mask=new boolean[4];
        final int[] caps={GL11.GL_BLEND,GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST,GL11.GL_STENCIL_TEST,GL30.GL_FRAMEBUFFER_SRGB};
        final boolean[] enabled=new boolean[caps.length];
        State(){
            try(MemoryStack stack=MemoryStack.stackPush()){
                IntBuffer v=stack.mallocInt(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);v.get(viewport);
                ByteBuffer m=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,m);
                for(int n=0;n<4;n++)mask[n]=m.get(n)!=0;
            }
            for(int n=0;n<caps.length;n++)enabled[n]=GL11.glIsEnabled(caps[n]);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);tex0=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);tex1=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(active);
        }
        public void close(){
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            GL20.glUseProgram(oldProgram);GL30.glBindVertexArray(oldVao);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex1);GL13.glActiveTexture(active);
            GL11.glDepthMask(depthMask);GL11.glColorMask(mask[0],mask[1],mask[2],mask[3]);
            for(int n=0;n<caps.length;n++)if(enabled[n])GL11.glEnable(caps[n]);else GL11.glDisable(caps[n]);
        }
    }
}
