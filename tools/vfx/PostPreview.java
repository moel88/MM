package dev.sangwoo.botandtools;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL41;
import org.lwjgl.system.MemoryUtil;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offscreen check of a Veil post shader (Mesa on Xvfb): compiles the program the way Veil does
 * (GLSL 410 core prepended) and applies it to a PNG with the given uniforms.
 * usage: PostPreview PROGRAM_DIR NAME IN.png OUT.png name=value...   (value: one or two numbers, comma separated)
 */
public final class PostPreview {
    public static void main(String[] args) throws Exception{
        Path dir=Path.of(args[0]);String name=args[1];
        BufferedImage in=ImageIO.read(new File(args[2]));int w=in.getWidth(),h=in.getHeight();
        if(!GLFW.glfwInit())throw new IllegalStateException("glfw");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,1);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,GLFW.GLFW_TRUE);
        long win=GLFW.glfwCreateWindow(64,64,"post",0,0);if(win==0)throw new IllegalStateException("window");
        GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();
        int vs=compile(GL41.GL_VERTEX_SHADER,"#version 410 core\n"+Files.readString(dir.resolve(name+".vsh")));
        int fs=compile(GL41.GL_FRAGMENT_SHADER,"#version 410 core\n"+Files.readString(dir.resolve(name+".fsh")));
        int prog=GL41.glCreateProgram();GL41.glAttachShader(prog,vs);GL41.glAttachShader(prog,fs);GL41.glLinkProgram(prog);
        if(GL41.glGetProgrami(prog,GL41.GL_LINK_STATUS)==0)throw new IllegalStateException(GL41.glGetProgramInfoLog(prog));
        ByteBuffer px=MemoryUtil.memAlloc(w*h*4);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=in.getRGB(x,h-1-y);px.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)255);}
        px.flip();
        int tex=GL41.glGenTextures();GL41.glBindTexture(GL41.GL_TEXTURE_2D,tex);
        GL41.glTexImage2D(GL41.GL_TEXTURE_2D,0,GL41.GL_RGBA8,w,h,0,GL41.GL_RGBA,GL41.GL_UNSIGNED_BYTE,px);MemoryUtil.memFree(px);
        GL41.glTexParameteri(GL41.GL_TEXTURE_2D,GL41.GL_TEXTURE_MIN_FILTER,GL41.GL_LINEAR);GL41.glTexParameteri(GL41.GL_TEXTURE_2D,GL41.GL_TEXTURE_MAG_FILTER,GL41.GL_LINEAR);
        GL41.glTexParameteri(GL41.GL_TEXTURE_2D,GL41.GL_TEXTURE_WRAP_S,GL41.GL_CLAMP_TO_EDGE);GL41.glTexParameteri(GL41.GL_TEXTURE_2D,GL41.GL_TEXTURE_WRAP_T,GL41.GL_CLAMP_TO_EDGE);
        int fbo=GL41.glGenFramebuffers(),color=GL41.glGenTextures();
        GL41.glBindTexture(GL41.GL_TEXTURE_2D,color);GL41.glTexImage2D(GL41.GL_TEXTURE_2D,0,GL41.GL_RGBA8,w,h,0,GL41.GL_RGBA,GL41.GL_UNSIGNED_BYTE,(ByteBuffer)null);
        GL41.glBindFramebuffer(GL41.GL_FRAMEBUFFER,fbo);GL41.glFramebufferTexture2D(GL41.GL_FRAMEBUFFER,GL41.GL_COLOR_ATTACHMENT0,GL41.GL_TEXTURE_2D,color,0);
        GL41.glViewport(0,0,w,h);
        GL41.glUseProgram(prog);
        GL41.glActiveTexture(GL41.GL_TEXTURE0);GL41.glBindTexture(GL41.GL_TEXTURE_2D,tex);
        GL41.glUniform1i(GL41.glGetUniformLocation(prog,"DiffuseSampler0"),0);
        GL41.glUniform2f(GL41.glGetUniformLocation(prog,"OutSize"),w,h);
        for(int i=4;i<args.length;i++){
            String[] kv=args[i].split("=");String[] v=kv[1].split(",");int loc=GL41.glGetUniformLocation(prog,kv[0]);
            if(loc<0){System.out.println("unused uniform "+kv[0]);continue;}
            if(v.length==1)GL41.glUniform1f(loc,Float.parseFloat(v[0]));else GL41.glUniform2f(loc,Float.parseFloat(v[0]),Float.parseFloat(v[1]));
        }
        int vao=GL41.glGenVertexArrays(),vbo=GL41.glGenBuffers();GL41.glBindVertexArray(vao);GL41.glBindBuffer(GL41.GL_ARRAY_BUFFER,vbo);
        FloatBuffer q=MemoryUtil.memAllocFloat(18);q.put(new float[]{-1,-1,0,1,-1,0,1,1,0,-1,-1,0,1,1,0,-1,1,0}).flip();
        GL41.glBufferData(GL41.GL_ARRAY_BUFFER,q,GL41.GL_STATIC_DRAW);MemoryUtil.memFree(q);
        GL41.glEnableVertexAttribArray(0);GL41.glVertexAttribPointer(0,3,GL41.GL_FLOAT,false,12,0);
        GL41.glDrawArrays(GL41.GL_TRIANGLES,0,6);
        ByteBuffer outPx=MemoryUtil.memAlloc(w*h*4);GL41.glReadPixels(0,0,w,h,GL41.GL_RGBA,GL41.GL_UNSIGNED_BYTE,outPx);
        BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-1-y)*w+x)*4;out.setRGB(x,y,((outPx.get(i)&255)<<16)|((outPx.get(i+1)&255)<<8)|(outPx.get(i+2)&255));}
        MemoryUtil.memFree(outPx);ImageIO.write(out,"png",new File(args[3]));
        GLFW.glfwTerminate();
    }
    private static int compile(int type,String src){
        int s=GL41.glCreateShader(type);GL41.glShaderSource(s,src);GL41.glCompileShader(s);
        if(GL41.glGetShaderi(s,GL41.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL41.glGetShaderInfoLog(s));
        return s;
    }
}
