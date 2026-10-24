package dev.sangwoo.celestialfall.client.title;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.lwjgl.opengl.GL33C.*;

/** Isolated PC title renderer. Owns its programs, VAOs and framebuffers; never replaces
 * Minecraft's framebuffer or global shader. All touched GL state is restored per call. */
final class CinemaGpu implements AutoCloseable {
    private record Mesh(int vao,int buffer,int count) implements AutoCloseable {
        void draw(){glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,count);}
        public void close(){glDeleteVertexArrays(vao);glDeleteBuffers(buffer);}
    }
    private record Target(int fbo,int color,int depth,int width,int height) implements AutoCloseable {
        void bind(){glBindFramebuffer(GL_FRAMEBUFFER,fbo);glViewport(0,0,width,height);}
        public void close(){glDeleteFramebuffers(fbo);if(color!=0)glDeleteTextures(color);if(depth!=0)glDeleteTextures(depth);}
    }
    private final Map<String,Mesh> meshes=new LinkedHashMap<>();
    private final List<Integer> programs=new ArrayList<>();
    private int lit,shadowProgram,blur,composite,screenVao,stageTexture,bomiTexture;
    private Target shadow,hdr,ping,pong;
    private final float[] matrices=new float[32*16];
    private final Matrix4f vp=new Matrix4f(),light=new Matrix4f();
    CinemaGpu() throws IOException {
        CinemaSupport.requireDesktop();
        try {
            lit=program("scene.vert","scene.frag");shadowProgram=program("scene.vert","shadow.frag");
            blur=program("screen.vert","blur.frag");composite=program("screen.vert","composite.frag");screenVao=glGenVertexArrays();
            for(String name:List.of("stage","bomi","prop","glass","glow"))meshes.put(name,upload(CinemaAssets.mesh(name)));
            meshes.put("petals",upload(petals()));
            shadow=target(2048,2048,true,true);
            var tm=MinecraftClient.getInstance().getTextureManager();
            var a=tm.getTexture(new Identifier("celestialfall","textures/cinema/stage.png"));
            var b=tm.getTexture(new Identifier("celestialfall","textures/cinema/bomi.png"));
            stageTexture=a.getGlId();bomiTexture=b.getGlId();
            // Set texture filtering directly inside our state scope; RenderSystem's cache is untouched.
            skinFiltering();
        } catch(Exception e){close();throw e;}
    }
    private void skinFiltering(){
        for(int id:new int[]{stageTexture,bomiTexture}){
            texture(0,id);int filter=id==bomiTexture?GL_NEAREST:GL_LINEAR;
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,filter);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,filter);
        }
    }
    private static String source(String file)throws IOException {
        try(var in=CinemaGpu.class.getResourceAsStream("/assets/celestialfall/cinema/shaders/"+file)){
            if(in==null)throw new IOException("Missing cinema shader "+file);return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private int program(String vert,String frag)throws IOException {
        int v=0,f=0,p=0;
        try {
            v=compile(GL_VERTEX_SHADER,source(vert));f=compile(GL_FRAGMENT_SHADER,source(frag));p=glCreateProgram();
            glAttachShader(p,v);glAttachShader(p,f);
            String[] names={"Position","Normal","UV","Color","Bone","Material"};
            for(int i=0;i<names.length;i++)glBindAttribLocation(p,i,names[i]);
            glBindFragDataLocation(p,0,"fragColor");glLinkProgram(p);
            if(glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE)throw new IOException("Cinema shader link: "+glGetProgramInfoLog(p));
            programs.add(p);return p;
        }catch(IOException|RuntimeException e){if(p!=0)glDeleteProgram(p);throw e;}
        finally{if(v!=0)glDeleteShader(v);if(f!=0)glDeleteShader(f);}
    }
    private static int compile(int type,String source)throws IOException {
        int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);
        if(glGetShaderi(s,GL_COMPILE_STATUS)==GL_FALSE){String error=glGetShaderInfoLog(s);glDeleteShader(s);throw new IOException("Cinema shader compile: "+error);}return s;
    }
    private static Mesh upload(CinemaAssets.Mesh data) {
        int vao=glGenVertexArrays(),vbo=glGenBuffers();FloatBuffer f=MemoryUtil.memAllocFloat(data.vertices().length);
        try {
            f.put(data.vertices()).flip();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,f,GL_STATIC_DRAW);
            int[] sizes={3,3,2,4,1,1};int offset=0;
            for(int i=0;i<sizes.length;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,sizes[i],GL_FLOAT,false,56,offset*4L);offset+=sizes[i];}
            return new Mesh(vao,vbo,data.count());
        }catch(RuntimeException e){glDeleteVertexArrays(vao);glDeleteBuffers(vbo);throw e;}finally{MemoryUtil.memFree(f);}
    }
    private static Target target(int w,int h,boolean depth,boolean depthOnly) throws IOException {
        int fb=glGenFramebuffers(),color=0,d=0;
        try {
            glBindFramebuffer(GL_FRAMEBUFFER,fb);
            if(!depthOnly){color=attachment(w,h,false);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);}
            if(depth){d=attachment(w,h,true);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,d,0);}
            if(depthOnly){glDrawBuffer(GL_NONE);glReadBuffer(GL_NONE);texture(0,d);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_COMPARE_MODE,GL_COMPARE_REF_TO_TEXTURE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_COMPARE_FUNC,GL_LEQUAL);}
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IOException("Cinema framebuffer is incomplete");
            return new Target(fb,color,d,w,h);
        }catch(IOException|RuntimeException e){glDeleteFramebuffers(fb);if(color!=0)glDeleteTextures(color);if(d!=0)glDeleteTextures(d);throw e;}
    }
    private static int attachment(int w,int h,boolean depth) {
        int t=glGenTextures();texture(0,t);glTexImage2D(GL_TEXTURE_2D,0,depth?GL_DEPTH_COMPONENT24:GL_RGBA16F,w,h,0,depth?GL_DEPTH_COMPONENT:GL_RGBA,GL_FLOAT,0L);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return t;
    }
    private void resize(int w,int h)throws IOException {
        if(hdr!=null&&hdr.width==w&&hdr.height==h)return;
        releaseTargets();hdr=target(w,h,true,false);ping=target(Math.max(1,w/4),Math.max(1,h/4),false,false);pong=target(ping.width,ping.height,false,false);
    }
    private static void texture(int slot,int id){glActiveTexture(GL_TEXTURE0+slot);glBindTexture(GL_TEXTURE_2D,id);glBindSampler(slot,0);}
    private static int uniform(int p,String s){return glGetUniformLocation(p,s);}
    private static void matrix(int p,String n,Matrix4f m){try(var s=MemoryStack.stackPush()){glUniformMatrix4fv(uniform(p,n),false,m.get(s.mallocFloat(16)));}}
    private void sceneUniforms(int p,Matrix4f camera,float time) {
        glUseProgram(p);matrix(p,"ViewProjection",camera);matrix(p,"LightMatrix",light);glUniform1f(uniform(p,"Time"),time);
        glUniformMatrix4fv(uniform(p,"Bones"),false,matrices);
    }
    void render(float[] pose,int boneCount,double time,int width,int height,Scope state)throws IOException {
        // Render at native framebuffer resolution, capped at 2560px for predictable VRAM.
        float scale=Math.min(1f,2560f/Math.max(width,height));int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));resize(w,h);
        if(boneCount>32)throw new IOException("PC cinema rig exceeds 32 bones");
        var tm=MinecraftClient.getInstance().getTextureManager();
        stageTexture=tm.getTexture(new Identifier("celestialfall","textures/cinema/stage.png")).getGlId();
        bomiTexture=tm.getTexture(new Identifier("celestialfall","textures/cinema/bomi.png")).getGlId();
        skinFiltering();
        for(int i=0;i<32;i++){
            int a=i*16;Arrays.fill(matrices,a,a+16,0);matrices[a]=matrices[a+5]=matrices[a+10]=matrices[a+15]=1;
            if(i<boneCount)for(int row=0;row<3;row++)for(int col=0;col<4;col++)matrices[a+col*4+row]=pose[i*12+row*4+col];
        }
        int c=boneCount*12;float ex=pose[c],ey=pose[c+1],ez=pose[c+2],tx=pose[c+3],ty=pose[c+4],tz=pose[c+5],fov=pose[c+6];
        if((float)w/h<1.25f){ex=.15f;tx=-.08f;ez+=1.15f;ty=1.42f;fov=49;}
        vp.identity().perspective((float)Math.toRadians(fov),(float)w/h,.05f,90).lookAt(ex,ey,ez,tx,ty,tz,0,1,0);
        float z=pose[11]-2;light.identity().ortho(-7,7,-7,7,.1f,40).lookAt(-6.4f,14.28f,z+6.24f,0,1,z,0,1,0);
        glDisable(GL_FRAMEBUFFER_SRGB);glDisable(GL_SCISSOR_TEST);glDisable(GL_CULL_FACE);glDisable(GL_BLEND);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glDepthMask(true);glColorMask(true,true,true,true);glClearDepth(1);
        shadow.bind();glClear(GL_DEPTH_BUFFER_BIT);glEnable(GL_POLYGON_OFFSET_FILL);glPolygonOffset(1.2f,2);
        sceneUniforms(shadowProgram,light,(float)time);glUniform1i(uniform(shadowProgram,"Atlas"),0);
        for(String n:List.of("stage","bomi","prop")){texture(0,n.equals("bomi")?bomiTexture:stageTexture);meshes.get(n).draw();}glDisable(GL_POLYGON_OFFSET_FILL);
        hdr.bind();glClearColor(.54f,.71f,1.02f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        sceneUniforms(lit,vp,(float)time);glUniform3f(uniform(lit,"Camera"),ex,ey,ez);glUniform1i(uniform(lit,"Atlas"),0);glUniform1i(uniform(lit,"ShadowMap"),1);texture(1,shadow.depth);
        texture(0,stageTexture);meshes.get("stage").draw();texture(0,bomiTexture);meshes.get("bomi").draw();texture(0,stageTexture);meshes.get("prop").draw();
        glEnable(GL_BLEND);glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
        for(String n:List.of("glass","glow","petals"))meshes.get(n).draw();glDepthMask(true);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);
        // Bloom is half a dozen tiny Gaussian passes, independent of the sharp HUD.
        glUseProgram(blur);glUniform1i(uniform(blur,"Source"),0);glBindVertexArray(screenVao);
        for(int i=0;i<4;i++){
            Target dest=i%2==0?ping:pong;dest.bind();texture(0,i==0?hdr.color:i%2==0?pong.color:ping.color);
            glUniform1i(uniform(blur,"Extract"),i==0?1:0);glUniform2f(uniform(blur,"Direction"),i%2==0?1.4f/ping.width:0,i%2==1?1.4f/ping.height:0);glDrawArrays(GL_TRIANGLES,0,3);
        }
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,state.drawFramebuffer);glViewport(state.viewport[0],state.viewport[1],state.viewport[2],state.viewport[3]);
        glUseProgram(composite);texture(0,hdr.color);texture(1,pong.color);texture(2,hdr.depth);
        glUniform1i(uniform(composite,"Source"),0);glUniform1i(uniform(composite,"Bloom"),1);glUniform1i(uniform(composite,"Depth"),2);glUniform2f(uniform(composite,"Resolution"),w,h);
        glUniform1f(uniform(composite,"Focus"),(float)Math.sqrt((ex-pose[3])*(ex-pose[3])+(ez-pose[11])*(ez-pose[11])));
        glDrawArrays(GL_TRIANGLES,0,3);
    }
    static CinemaAssets.Mesh petals(){
        float[] out=new float[80*6*14];int k=0;
        for(int i=0;i<80;i++){
            float x=(float)Math.sin(i*7.4)*4,y=.3f+(i*.731f%3.8f),z=2-(i*1.373f%25),r=.017f+(i%3)*.007f;
            float[][] p={{x-r,y-r,z},{x+r,y-r*.2f,z+.01f},{x+r*.7f,y+r,z},{x-r*.7f,y+r*.4f,z}};
            for(int j:new int[]{0,1,2,0,2,3})for(float v:new float[]{p[j][0],p[j][1],p[j][2],0,0,1,.02f,.02f,.91f,.97f,1,.72f,-1,6})out[k++]=v;
        }return new CinemaAssets.Mesh(out);
    }
    private void releaseTargets(){if(hdr!=null){hdr.close();hdr=null;}if(ping!=null){ping.close();ping=null;}if(pong!=null){pong.close();pong=null;}}
    public void close(){releaseTargets();if(shadow!=null){shadow.close();shadow=null;}meshes.values().forEach(Mesh::close);meshes.clear();programs.forEach(CinemaGpu::deleteProgram);programs.clear();if(screenVao!=0){glDeleteVertexArrays(screenVao);screenVao=0;}}
    private static void deleteProgram(int p){glDeleteProgram(p);}

    /** Raw GL state is restored to match Minecraft's cached state, including error paths. */
    static final class Scope implements AutoCloseable {
        final int drawFramebuffer,readFramebuffer,program,vao,array,active,depthFunc;
        final int[] viewport=new int[4],textures=new int[3],samplers=new int[3];
        final int[] capabilities={GL_DEPTH_TEST,GL_CULL_FACE,GL_BLEND,GL_SCISSOR_TEST,GL_FRAMEBUFFER_SRGB,GL_POLYGON_OFFSET_FILL};
        final boolean[] enabled=new boolean[capabilities.length];
        final boolean depthMask;final byte[] colorMask=new byte[4];final float[] clearColor=new float[4];
        final double clearDepth;final float factor,units;
        final int src,dst,srcA,dstA,eq,eqA;
        Scope(){
            // MUST precede ALL native calls, including field initializers. try/catch cannot recover a JNI abort.
            CinemaSupport.requireDesktop();
            drawFramebuffer=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);readFramebuffer=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            program=glGetInteger(GL_CURRENT_PROGRAM);vao=glGetInteger(GL_VERTEX_ARRAY_BINDING);array=glGetInteger(GL_ARRAY_BUFFER_BINDING);
            active=glGetInteger(GL_ACTIVE_TEXTURE);depthFunc=glGetInteger(GL_DEPTH_FUNC);depthMask=glGetBoolean(GL_DEPTH_WRITEMASK);
            clearDepth=glGetDouble(GL_DEPTH_CLEAR_VALUE);factor=glGetFloat(GL_POLYGON_OFFSET_FACTOR);units=glGetFloat(GL_POLYGON_OFFSET_UNITS);
            src=glGetInteger(GL_BLEND_SRC_RGB);dst=glGetInteger(GL_BLEND_DST_RGB);srcA=glGetInteger(GL_BLEND_SRC_ALPHA);dstA=glGetInteger(GL_BLEND_DST_ALPHA);
            eq=glGetInteger(GL_BLEND_EQUATION_RGB);eqA=glGetInteger(GL_BLEND_EQUATION_ALPHA);
            glGetIntegerv(GL_VIEWPORT,viewport);glGetFloatv(GL_COLOR_CLEAR_VALUE,clearColor);
            try(var stack=MemoryStack.stackPush()){var b=stack.malloc(4);glGetBooleanv(GL_COLOR_WRITEMASK,b);b.get(colorMask);}
            for(int i=0;i<capabilities.length;i++)enabled[i]=glIsEnabled(capabilities[i]);
            for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE0+i);textures[i]=glGetInteger(GL_TEXTURE_BINDING_2D);samplers[i]=glGetIntegeri(GL_SAMPLER_BINDING,i);}glActiveTexture(active);
        }
        public void close(){
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,drawFramebuffer);glBindFramebuffer(GL_READ_FRAMEBUFFER,readFramebuffer);glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,array);
            for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,textures[i]);glBindSampler(i,samplers[i]);}glActiveTexture(active);
            glDepthFunc(depthFunc);glDepthMask(depthMask);glColorMask(colorMask[0]!=0,colorMask[1]!=0,colorMask[2]!=0,colorMask[3]!=0);
            glClearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);glClearDepth(clearDepth);glPolygonOffset(factor,units);
            glBlendFuncSeparate(src,dst,srcA,dstA);glBlendEquationSeparate(eq,eqA);
            for(int i=0;i<capabilities.length;i++){if(enabled[i])glEnable(capabilities[i]);else glDisable(capabilities[i]);}
        }
    }
}
