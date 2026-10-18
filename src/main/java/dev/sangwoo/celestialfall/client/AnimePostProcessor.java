package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.SpellTimeline;
import dev.sangwoo.celestialfall.HeavenTimeline;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/** Own, bounded post chain. Does not replace Minecraft's or another mod's post processor. */
public final class AnimePostProcessor {
    private static int program,vao,width,height;
    private static final int[] fbos=new int[3],textures=new int[3];
    private static boolean failed;
    private static int monoProgram,monoVao,monoTexture,monoFbo,monoWidth,monoHeight;
    private static boolean monoFailed;
    private static float focusX=.5f,focusY=.5f,worldM11=Float.NaN;
    private AnimePostProcessor() {}
    /** Remembers the world projection at the end of the world pass; the chain itself runs later (after Veil), when other passes may have replaced it. */
    public static void captureWorldProjection(){worldM11=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix().m11();}
    public static boolean available(){return !failed;}
    public static void focus(float x,float y) { focusX=Math.max(.08f,Math.min(.92f,x));focusY=Math.max(.08f,Math.min(.92f,y)); }
    public static void render(float delta) {
        MinecraftClient mc=MinecraftClient.getInstance();
        // R has a separate final pass, after hands and other post processors.
        float mono=0;boolean timeStop=false;
        // When the effect layer drew this frame it brought its own bloom (effects only): the scene-wide one stays low.
        boolean layered=dev.sangwoo.celestialfall.client.vfx.FxLayer.consumeDrawn();
        if(failed || mc.world==null || mc.player==null || !CelestialFall.CONFIG.postProcessing&&!timeStop)return;
        ClientSpell spell=CinematicDirector.active();
        if(spell==null)spell=CelestialFallClient.nearest();
        var atomic=AtomicClient.nearest();
        var heaven=HeavenClient.nearest();
        var kageno=StandClient.visualCast();
        boolean isKageno=spell==null&&atomic==null&&heaven==null&&kageno!=null;
        if(spell==null && atomic==null && heaven==null&&!timeStop&&!isKageno)return;
        boolean isHeaven=!timeStop&&spell==null && heaven!=null,isAtomic=!timeStop&&spell==null && !isHeaven&&atomic!=null;
        var immersion=isAtomic?AtomicImmersion.at(mc.gameRenderer.getCamera().getPos(),delta):AtomicImmersion.NONE;
        float age=timeStop?mc.world.getTime()%1000+delta:isKageno?kageno.age(delta):isHeaven?heaven.age(delta):isAtomic?atomic.age(delta):spell.age(mc.world.getTime(),delta);
        float strength=timeStop?1:isKageno?.88f:isHeaven?HeavenTimeline.visibility(age)*(float)Math.max(0,Math.min(1,(1100-mc.player.getPos().distanceTo(heaven.target))/400))
            :isAtomic?(immersion.active()||dev.sangwoo.celestialfall.client.atomic.AtomicAmbience.dim(delta)>0?1:0)
            :SpellTimeline.visibility(age)*(float)Math.max(0,Math.min(1,(640-mc.player.getPos().distanceTo(spell.target))/220));
        if(strength<.005f)return;
        var main=mc.getFramebuffer();
        if(main.textureWidth<=0 || main.textureHeight<=0)return;
        try(State state=new State()) {
            initialize(mc,main.textureWidth,main.textureHeight);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);GL11.glColorMask(true,true,true,true);
            // Snapshot before composition: never sample the framebuffer being written.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbos[0]);
            GL30.glBlitFramebuffer(0,0,width,height,0,0,width,height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            GL20.glUseProgram(program);GL30.glBindVertexArray(vao);
            i("SceneTex",0);i("BloomTex",1);
            f("KagenoTone",isKageno&&!timeStop?1:0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[0]);
            int bw=Math.max(1,width/4),bh=Math.max(1,height/4);
            draw(0,fbos[1],textures[0],bw,bh,1f/width,1f/height);
            draw(1,fbos[2],textures[1],bw,bh,1f/bw,1f/bh);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[2]);
            f("Strength",strength);f("Time",age/20f);
            f("Bloom",(float)CelestialFall.CONFIG.bloomStrength*(layered?.25f:1f));
            float wave=timeStop||isAtomic?0:isKageno?dev.sangwoo.celestialfall.client.kageno.KagenoCamera.pulse(kageno.skill(),age):isHeaven?HeavenTimeline.aftershock(age):SpellTimeline.wavePulse(age),clash=timeStop||isKageno||isAtomic || isHeaven?0:SpellTimeline.clashPulse(age);
            f("HeavenTone",isHeaven?1:0);
            f("TimeStopMono",mono);
            var grade=immersion.grade();
            f("AtomicMode",isAtomic?1:0);
            f("AtomicTint",grade.tint());
            f("AtomicFrame",CelestialFall.CONFIG.screenFlash?grade.frame():0);
            f("AtomicMorph",grade.morph());
            f("AtomicGrade",grade.mix());
            rgb("AtomicLo",grade.lo());rgb("AtomicHi",grade.hi());
            f("AtomicHorizon",horizon(mc,delta));
            f("Power",Math.max(wave,clash*.65f));
            f("Distortion",CelestialFall.CONFIG.screenDistortion?1:0);
            f("Speed",!timeStop&&CelestialFall.CONFIG.speedLines?(isKageno?0:isHeaven?HeavenTimeline.rush(age):isAtomic?0:SpellTimeline.descentPower(age)):0);
            f("Mono",!timeStop&&!isKageno&&!isAtomic && !isHeaven && CelestialFall.CONFIG.screenFlash && CelestialFall.CONFIG.animeImpactFrames?SpellTimeline.monoImpact(age):0);
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"Focus"),focusX,focusY);
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"Resolution"),width,height);
            draw(2,main.fbo,textures[0],width,height,1f/width,1f/height);
        } catch(Exception | LinkageError e) {
            failed=true;
            CelestialFall.LOGGER.error("Anime post-processing disabled after a graphics error; world effects remain available",e);
        }
    }
    private static void draw(int mode,int fbo,int texture,int w,int h,float dx,float dy) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glViewport(0,0,w,h);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        i("Mode",mode);GL20.glUniform2f(GL20.glGetUniformLocation(program,"Texel"),dx,dy);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    }
    private static void f(String name,float value) { GL20.glUniform1f(GL20.glGetUniformLocation(program,name),value); }
    /** Whether this frame's world already went gray (before the Kageno effects were drawn). */
    private static boolean monoDrawn;
    /** R's gray world, drawn at the end of the world pass before the Kageno effects: the cuts, the sword and the caster's outline stay in colour. */
    public static void renderMonochromeWorld(float delta){monoDrawn=true;renderMonochrome(delta);}
    /** After the world: grays the frame only if the world pass did not (no world render event this frame). */
    public static void renderMonochromeFallback(float delta){if(!monoDrawn)renderMonochrome(delta);monoDrawn=false;}
    /** Independent of optional bloom/distortion and its failure flag. */
    public static void renderMonochrome(float delta){
        var mc=MinecraftClient.getInstance();float amount=StandClient.monoAmount(delta);
        if(amount<=0||monoFailed||mc.world==null||mc.player==null)return;
        var main=mc.getFramebuffer();int w=main.textureWidth,h=main.textureHeight;
        if(w<=0||h<=0)return;
        try(State state=new State()){
            if(monoProgram==0){
                int vs=0,fs=0,p=0;
                try{
                    vs=compile(mc,GL20.GL_VERTEX_SHADER,"fullscreen.vert");
                    fs=compile(mc,GL20.GL_FRAGMENT_SHADER,"monochrome.frag");
                    p=GL20.glCreateProgram();GL20.glAttachShader(p,vs);GL20.glAttachShader(p,fs);GL20.glLinkProgram(p);
                    if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));
                    monoProgram=p;p=0;monoVao=GL30.glGenVertexArrays();
                }finally{if(vs!=0)GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs);if(p!=0)GL20.glDeleteProgram(p);}
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if(monoTexture==0){monoTexture=GL11.glGenTextures();monoFbo=GL30.glGenFramebuffers();}
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,monoTexture);
            if(w!=monoWidth||h!=monoHeight){
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,monoFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,monoTexture,0);
                if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete R framebuffer");
                monoWidth=w;monoHeight=h;
            }
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);GL11.glColorMask(true,true,true,true);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.fbo);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,monoFbo);
            GL30.glBlitFramebuffer(0,0,w,h,0,0,w,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,main.fbo);GL11.glViewport(0,0,w,h);
            GL20.glUseProgram(monoProgram);GL30.glBindVertexArray(monoVao);
            GL20.glUniform1i(GL20.glGetUniformLocation(monoProgram,"SceneTex"),0);
            GL20.glUniform1f(GL20.glGetUniformLocation(monoProgram,"Amount"),amount);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        }catch(Exception|LinkageError e){monoFailed=true;CelestialFall.LOGGER.error("R monochrome pass failed",e);}
    }
    private static void rgb(String name,int c) { GL20.glUniform3f(GL20.glGetUniformLocation(program,name),((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f); }
    /** Screen-space height (0 bottom, 1 top) of the horizon for the current camera pitch. */
    private static float horizon(MinecraftClient mc,float delta) {
        float m11=Float.isNaN(worldM11)?com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix().m11():worldM11;
        double pitch=Math.toRadians(Math.max(-89,Math.min(89,mc.gameRenderer.getCamera().getPitch())));
        return (float)Math.max(-1,Math.min(2,.5+.5*m11*Math.tan(pitch)));
    }
    private static void i(String name,int value) { GL20.glUniform1i(GL20.glGetUniformLocation(program,name),value); }
    private static int compile(MinecraftClient mc,int type,String file) throws Exception {
        String source;
        try(var stream=mc.getResourceManager().getResource(CelestialFall.id("shaders/anime/"+file)).orElseThrow().getInputStream()) {
            source=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
        int shader=GL20.glCreateShader(type);
        GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE) {
            String error=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IllegalStateException(error);
        }
        return shader;
    }
    private static void initialize(MinecraftClient mc,int w,int h) throws Exception {
        if(program==0) {
            int vs=0,fs=0;
            try {
                vs=compile(mc,GL20.GL_VERTEX_SHADER,"fullscreen.vert");fs=compile(mc,GL20.GL_FRAGMENT_SHADER,"anime.frag");
                program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);GL20.glLinkProgram(program);
                if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==GL11.GL_FALSE)throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
                vao=GL30.glGenVertexArrays();
            } finally { if(vs!=0)GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs); }
        }
        if(w==width && h==height && fbos[0]!=0)return;
        freeBuffers();width=w;height=h;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        for(int n=0;n<3;n++) {
            fbos[n]=GL30.glGenFramebuffers();textures[n]=GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[n]);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,n==0?w:Math.max(1,w/4),n==0?h:Math.max(1,h/4),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbos[n]);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,textures[n],0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete anime framebuffer");
        }
    }
    private static void freeBuffers() {
        for(int n=0;n<3;n++) {
            if(fbos[n]!=0)GL30.glDeleteFramebuffers(fbos[n]);if(textures[n]!=0)GL11.glDeleteTextures(textures[n]);
            fbos[n]=textures[n]=0;
        }
    }
    public static void release() {
        if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()){com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(AnimePostProcessor::release);return;}
        freeBuffers();width=height=0;
        if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);
        program=vao=0;failed=false;focusX=focusY=.5f;
        if(monoProgram!=0)GL20.glDeleteProgram(monoProgram);if(monoVao!=0)GL30.glDeleteVertexArrays(monoVao);
        if(monoTexture!=0)GL11.glDeleteTextures(monoTexture);if(monoFbo!=0)GL30.glDeleteFramebuffers(monoFbo);
        monoProgram=monoVao=monoTexture=monoFbo=monoWidth=monoHeight=0;monoFailed=false;
    }
    /** Raw GL calls are restored exactly so RenderSystem's cached state stays valid. */
    private static final class State implements AutoCloseable {
        final int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),tex0,tex1;
        final boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int[] viewport=new int[4];final boolean[] mask=new boolean[4];
        final int[] caps={GL11.GL_BLEND,GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST,GL11.GL_STENCIL_TEST,GL30.GL_FRAMEBUFFER_SRGB};
        final boolean[] enabled=new boolean[caps.length];
        State() {
            try(MemoryStack stack=MemoryStack.stackPush()) {
                IntBuffer v=stack.mallocInt(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);v.get(viewport);
                ByteBuffer m=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,m);
                for(int n=0;n<4;n++)mask[n]=m.get(n)!=0;
            }
            for(int n=0;n<caps.length;n++)enabled[n]=GL11.glIsEnabled(caps[n]);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);tex0=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);tex1=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(active);
        }
        public void close() {
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
