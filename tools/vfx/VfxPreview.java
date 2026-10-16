package dev.sangwoo.botandtools;

import dev.sangwoo.celestialfall.client.atomic.AtomicPillarShape;
import dev.sangwoo.celestialfall.client.atomic.AtomicStage;
import dev.sangwoo.celestialfall.client.vfx.VfxNoise;
import dev.sangwoo.celestialfall.client.vfx.VfxStreaks;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Offscreen preview of the vfx_energy materials with the real GLSL (Mesa on Xvfb): G's exterior
 * pillar over a flat ground and sky, at given cameras and cast times, written as PNGs.
 * usage: VfxPreview SHADER_DIR OUT_DIR WIDTHxHEIGHT R shot...   shot = name@sec@ex,ey,ez@tx,ty,tz@fov
 */
public final class VfxPreview {
    private static int program,bgProgram,noise,vao,vbo;
    private static int width,height;

    public static void main(String[] args) throws Exception{
        Path shaders=Path.of(args[0]),out=Path.of(args[1]);String[] size=args[2].split("x");
        width=Integer.parseInt(size[0]);height=Integer.parseInt(size[1]);double R=Double.parseDouble(args[3]);
        Files.createDirectories(out);
        if(!GLFW.glfwInit())throw new IllegalStateException("glfw");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,GLFW.GLFW_TRUE);
        long win=GLFW.glfwCreateWindow(64,64,"vfx",0,0);if(win==0)throw new IllegalStateException("window");
        GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();
        program=link(Files.readString(shaders.resolve("vfx_energy.vsh")),Files.readString(shaders.resolve("vfx_energy.fsh")));
        bgProgram=link("""
            #version 150
            in vec3 Position;in vec2 UV0;in vec4 Color;uniform mat4 ModelViewMat;uniform mat4 ProjMat;out vec4 c;out vec3 w;
            void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);c=Color;w=Position;}
            ""","""
            #version 150
            in vec4 c;in vec3 w;out vec4 fragColor;
            void main(){float g=0.92+0.08*fract(sin(dot(floor(w.xz),vec2(12.9898,78.233)))*43758.5453);fragColor=vec4(c.rgb*g,1.0);}
            """);
        noise=noiseTexture();
        vao=GL33.glGenVertexArrays();vbo=GL33.glGenBuffers();
        int fbo=GL33.glGenFramebuffers(),color=GL33.glGenTextures(),depth=GL33.glGenRenderbuffers();
        GL33.glBindTexture(GL33.GL_TEXTURE_2D,color);
        GL33.glTexImage2D(GL33.GL_TEXTURE_2D,0,GL33.GL_RGBA8,width,height,0,GL33.GL_RGBA,GL33.GL_UNSIGNED_BYTE,(ByteBuffer)null);
        GL33.glBindRenderbuffer(GL33.GL_RENDERBUFFER,depth);GL33.glRenderbufferStorage(GL33.GL_RENDERBUFFER,GL33.GL_DEPTH_COMPONENT24,width,height);
        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER,fbo);
        GL33.glFramebufferTexture2D(GL33.GL_FRAMEBUFFER,GL33.GL_COLOR_ATTACHMENT0,GL33.GL_TEXTURE_2D,color,0);
        GL33.glFramebufferRenderbuffer(GL33.GL_FRAMEBUFFER,GL33.GL_DEPTH_ATTACHMENT,GL33.GL_RENDERBUFFER,depth);
        GL33.glViewport(0,0,width,height);
        for(int i=4;i<args.length;i++){
            String[] p=args[i].split("@");
            float sec=Float.parseFloat(p[1]);Vector3f eye=vec(p[2]),target=vec(p[3]);float fov=Float.parseFloat(p[4]);
            render(sec,R,eye,target,fov);
            write(out.resolve(p[0]+".png").toFile());
        }
        GLFW.glfwTerminate();
    }
    private static Vector3f vec(String s){String[] v=s.split(",");return new Vector3f(Float.parseFloat(v[0]),Float.parseFloat(v[1]),Float.parseFloat(v[2]));}

    private static void render(float sec,double R,Vector3f eye,Vector3f target,float fov){
        Matrix4f proj=new Matrix4f().perspective((float)Math.toRadians(fov),width/(float)height,.05f,4000f);
        Matrix4f view=new Matrix4f().lookAt(new Vector3f(),new Vector3f(target).sub(eye),new Vector3f(0,1,0));
        // Sky and terrain dim toward deep violet like the game does for viewers outside the blast.
        float dim=AtomicStage.sky(sec)*.92f;
        GL33.glDepthMask(true);GL33.glClearColor(.62f+(.035f-.62f)*dim,.74f+(.02f-.74f)*dim,1f+(.11f-1f)*dim,1f);GL33.glClearDepth(1);GL33.glClear(GL33.GL_COLOR_BUFFER_BIT|GL33.GL_DEPTH_BUFFER_BIT);
        float dark=1-.38f*dim,lr=dark*(1+(.82f-1)*dim),lg=dark*(1+(.6f-1)*dim),lb=dark*(1+(1.22f-1)*dim);
        // Ground: a grey plaza of radius 1.6 R in a green field, opaque and depth-writing like terrain.
        GL33.glEnable(GL33.GL_DEPTH_TEST);GL33.glDepthMask(true);GL33.glDisable(GL33.GL_BLEND);GL33.glDisable(GL33.GL_CULL_FACE);
        GL33.glUseProgram(bgProgram);
        mats(bgProgram,new Matrix4f(view).translate(new Vector3f(eye).negate()),proj);
        draw(quad(-2000,2000,0,.36f*lr,.5f*lg,.25f*lb));
        draw(quad((float)-R*1.6f,(float)R*1.6f,.01f,.55f*lr,.55f*lg,.56f*lb));
        // The pillar, exactly as AtomicPillar draws it.
        GL33.glDepthMask(false);GL33.glEnable(GL33.GL_BLEND);GL33.glBlendFuncSeparate(GL33.GL_ONE,GL33.GL_ONE_MINUS_SRC_ALPHA,GL33.GL_ONE,GL33.GL_ONE_MINUS_SRC_ALPHA);
        GL33.glUseProgram(program);
        GL33.glActiveTexture(GL33.GL_TEXTURE0);GL33.glBindTexture(GL33.GL_TEXTURE_2D,noise);
        GL33.glActiveTexture(GL33.GL_TEXTURE1);GL33.glBindTexture(GL33.GL_TEXTURE_2D,noise);
        GL33.glUniform1i(loc("Sampler0"),0);GL33.glUniform1i(loc("Sampler1"),1);
        GL33.glUniform4f(loc("ColorModulator"),1,1,1,1);GL33.glUniform2f(loc("ScreenSize"),width,height);
        double r=AtomicPillarShape.radius(sec,R),y0=AtomicPillarShape.base(0),y1=AtomicPillarShape.top(sec,0,R);
        float flow=AtomicPillarShape.flowTime(sec);
        float g=AtomicPillarShape.groundGlow(sec);
        if(g>.005f){
            material(4,sec,0,g,0,0,0,0,0,0);
            mats(program,new Matrix4f(view).translate(new Vector3f(eye).negate()).translate(0,.06f,0).scale((float)R*1.1f,1,(float)R*1.1f),proj);
            draw(disc());
        }
        if(r>.05){
            float white=AtomicPillarShape.whiteOut(sec),heat=AtomicPillarShape.heat(sec),a=AtomicPillarShape.opacity(sec),h=(float)(y1-y0);
            Matrix4f mv=new Matrix4f(view).translate(new Vector3f(eye).negate()).translate(0,(float)y0,0).scale((float)r,h,(float)r);
            GL33.glEnable(GL33.GL_CULL_FACE);
            float front=(float)AtomicStage.surgeFront(sec,R),glow=AtomicStage.surgeGlow(sec);
            material(1,flow,(float)r,h,(float)R,white,1,heat,a,.3f);surge(front,glow);mats(program,mv,proj);draw(cylinder());
            material(2,flow,(float)r,h,(float)R,white,1,heat,a,.3f);surge(front,glow);mats(program,mv,proj);draw(cylinder());
            GL33.glDisable(GL33.GL_CULL_FACE);
            float level=AtomicPillarShape.streaks(sec);
            if(level>.01f&&!noStreaks){
                material(17,flow,(float)r,h,(float)R,level,1,1,0,0);surge(front,glow);
                mats(program,new Matrix4f(view).translate(new Vector3f(eye).negate()).translate(0,(float)y0,0),proj);
                draw(streakField());
            }
        }
    }
    private static int loc(String n){return GL33.glGetUniformLocation(program,n);}
    private static final boolean noStreaks=Boolean.getBoolean("vfx.noStreaks");
    private static void material(int mode,float time,float a,float b,float c,float d,float e,float f,float g,float h){
        GL33.glUniform1i(loc("Mode"),mode);GL33.glUniform1f(loc("Time"),time);
        GL33.glUniform4f(loc("Params"),a,b,c,d);GL33.glUniform4f(loc("Params2"),e,f,g,h);
        surge(-1,0);
    }
    private static void surge(float front,float strength){GL33.glUniform4f(loc("Params3"),front,strength,0,0);}
    private static float[] streaks;
    /** The same streak field as Vfx: seeds in Position and Color (0..1), the corner in UV0, as triangles. */
    private static float[] streakField(){
        if(streaks!=null)return streaks;
        int n=VfxStreaks.COUNT;float[] p=VfxStreaks.positions(n);int[] c=VfxStreaks.colors(n);
        float[] v=new float[n*6*9];int k=0;int[][] corners={{0,0},{1,0},{1,1},{0,0},{1,1},{0,1}};
        for(int i=0;i<n;i++)for(int[] q:corners){
            v[k++]=p[i*3];v[k++]=p[i*3+1];v[k++]=p[i*3+2];v[k++]=q[0];v[k++]=q[1];
            v[k++]=c[i*4]/255f;v[k++]=c[i*4+1]/255f;v[k++]=c[i*4+2]/255f;v[k++]=c[i*4+3]/255f;
        }
        return streaks=v;
    }
    private static void mats(int prog,Matrix4f mv,Matrix4f proj){
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            FloatBuffer fb=stack.mallocFloat(16);
            GL33.glUniformMatrix4fv(GL33.glGetUniformLocation(prog,"ModelViewMat"),false,mv.get(fb));
            GL33.glUniformMatrix4fv(GL33.glGetUniformLocation(prog,"ProjMat"),false,proj.get(fb));
        }
    }
    /** Interleaved Position(3) UV0(2) Color(4) triangles. */
    private static void draw(float[] v){
        GL33.glBindVertexArray(vao);GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER,vbo);
        FloatBuffer fb=MemoryUtil.memAllocFloat(v.length);fb.put(v).flip();
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER,fb,GL33.GL_STREAM_DRAW);MemoryUtil.memFree(fb);
        int stride=9*4;
        GL33.glEnableVertexAttribArray(0);GL33.glVertexAttribPointer(0,3,GL33.GL_FLOAT,false,stride,0);
        GL33.glEnableVertexAttribArray(1);GL33.glVertexAttribPointer(1,2,GL33.GL_FLOAT,false,stride,12);
        GL33.glEnableVertexAttribArray(2);GL33.glVertexAttribPointer(2,4,GL33.GL_FLOAT,false,stride,20);
        GL33.glDrawArrays(GL33.GL_TRIANGLES,0,v.length/9);
    }
    private static void put(List<Float> l,float x,float y,float z,float u,float w,float r,float g,float b){
        l.add(x);l.add(y);l.add(z);l.add(u);l.add(w);l.add(r);l.add(g);l.add(b);l.add(1f);
    }
    private static float[] arr(List<Float> l){float[] a=new float[l.size()];for(int i=0;i<a.length;i++)a[i]=l.get(i);return a;}
    private static float[] quad(float lo,float hi,float y,float r,float g,float b){
        List<Float> l=new ArrayList<>();
        put(l,lo,y,lo,0,0,r,g,b);put(l,hi,y,lo,1,0,r,g,b);put(l,hi,y,hi,1,1,r,g,b);
        put(l,lo,y,lo,0,0,r,g,b);put(l,hi,y,hi,1,1,r,g,b);put(l,lo,y,hi,0,1,r,g,b);
        return arr(l);
    }
    /** The same unit cylinder as Vfx (outward, counter-clockwise from outside), as triangles. */
    private static float[] cylinder(){
        List<Float> l=new ArrayList<>();int n=160;
        for(int i=0;i<n;i++){
            float u0=(float)i/n,u1=(float)(i+1)/n;double a0=u0*Math.PI*2,a1=u1*Math.PI*2;
            float x0=(float)Math.cos(a0),z0=(float)Math.sin(a0),x1=(float)Math.cos(a1),z1=(float)Math.sin(a1);
            put(l,x1,0,z1,u1,0,1,1,1);put(l,x0,0,z0,u0,0,1,1,1);put(l,x0,1,z0,u0,1,1,1,1);
            put(l,x1,0,z1,u1,0,1,1,1);put(l,x0,1,z0,u0,1,1,1,1);put(l,x1,1,z1,u1,1,1,1,1);
        }
        return arr(l);
    }
    private static float[] disc(){
        List<Float> l=new ArrayList<>();int n=128;
        for(int i=0;i<n;i++){
            double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
            float x0=(float)Math.cos(a0),z0=(float)Math.sin(a0),x1=(float)Math.cos(a1),z1=(float)Math.sin(a1);
            put(l,0,0,0,.5f,.5f,1,1,1);put(l,x0,0,z0,.5f+x0*.5f,.5f+z0*.5f,1,1,1);put(l,x1,0,z1,.5f+x1*.5f,.5f+z1*.5f,1,1,1);
        }
        return arr(l);
    }
    private static int noiseTexture(){
        int[] px=VfxNoise.abgr();
        ByteBuffer b=MemoryUtil.memAlloc(px.length*4);for(int p:px)b.putInt(p);b.flip();
        b.order(java.nio.ByteOrder.nativeOrder());
        int id=GL33.glGenTextures();GL33.glBindTexture(GL33.GL_TEXTURE_2D,id);
        // ABGR ints in native (little-endian) order are R,G,B,A bytes.
        ByteBuffer le=MemoryUtil.memAlloc(px.length*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(int p:px)le.putInt(p);le.flip();
        GL33.glTexImage2D(GL33.GL_TEXTURE_2D,0,GL33.GL_RGBA8,VfxNoise.SIZE,VfxNoise.SIZE,0,GL33.GL_RGBA,GL33.GL_UNSIGNED_BYTE,le);
        MemoryUtil.memFree(le);MemoryUtil.memFree(b);
        GL33.glGenerateMipmap(GL33.GL_TEXTURE_2D);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_MIN_FILTER,GL33.GL_LINEAR_MIPMAP_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_MAG_FILTER,GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_WRAP_S,GL33.GL_REPEAT);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_WRAP_T,GL33.GL_REPEAT);
        return id;
    }
    private static int link(String vs,String fs){
        int v=compile(GL33.GL_VERTEX_SHADER,vs),f=compile(GL33.GL_FRAGMENT_SHADER,fs),p=GL33.glCreateProgram();
        GL33.glAttachShader(p,v);GL33.glAttachShader(p,f);
        GL33.glBindAttribLocation(p,0,"Position");GL33.glBindAttribLocation(p,1,"UV0");GL33.glBindAttribLocation(p,2,"Color");
        GL33.glLinkProgram(p);
        if(GL33.glGetProgrami(p,GL33.GL_LINK_STATUS)==0)throw new IllegalStateException(GL33.glGetProgramInfoLog(p));
        return p;
    }
    private static int compile(int type,String src){
        int s=GL33.glCreateShader(type);GL33.glShaderSource(s,src);GL33.glCompileShader(s);
        if(GL33.glGetShaderi(s,GL33.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL33.glGetShaderInfoLog(s));
        return s;
    }
    private static void write(File f) throws Exception{
        ByteBuffer b=MemoryUtil.memAlloc(width*height*4);
        GL33.glReadPixels(0,0,width,height,GL33.GL_RGBA,GL33.GL_UNSIGNED_BYTE,b);
        BufferedImage img=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int i=((height-1-y)*width+x)*4;
            img.setRGB(x,y,((b.get(i)&255)<<16)|((b.get(i+1)&255)<<8)|(b.get(i+2)&255));
        }
        MemoryUtil.memFree(b);ImageIO.write(img,"png",f);
    }
}
