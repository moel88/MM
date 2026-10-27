package dev.sangwoo.botandtools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Offscreen run of a Veil post pipeline (Mesa on Xvfb), close to what Veil does: the pipeline JSON's
 * stages (veil:blit, veil:copy) and its own framebuffers (sizes from q.screen_width / q.screen_height
 * expressions), each program compiled with GLSL 410 core prepended, the stage input bound as
 * DiffuseSampler0 with InSize/OutSize, and a program's framebuffer textures bound by name. The PNG is
 * veil:post; the result is veil:post after the last stage. Uniforms given as name=value (one or two
 * comma-separated numbers) go to every program, like PostPipeline.setFloat/setVector.
 * usage: PostPreview PINWHEEL_DIR PIPELINE IN.png OUT.png name=value...
 */
public final class PostPreview {
    private record Target(int fbo,int texture,int width,int height){}
    private static final Map<String,Target> targets=new HashMap<>();
    private static final Map<String,Integer> programs=new HashMap<>();
    private static Path pinwheel;
    private static int screenW,screenH,vao;
    private static String[] uniforms;

    public static void main(String[] args) throws Exception{
        pinwheel=Path.of(args[0]);String pipelineName=args[1];
        BufferedImage in=ImageIO.read(new File(args[2]));screenW=in.getWidth();screenH=in.getHeight();
        uniforms=java.util.Arrays.copyOfRange(args,4,args.length);
        if(!GLFW.glfwInit())throw new IllegalStateException("glfw");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,1);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,GLFW.GLFW_TRUE);
        long win=GLFW.glfwCreateWindow(64,64,"post",0,0);if(win==0)throw new IllegalStateException("window");
        GLFW.glfwMakeContextCurrent(win);GL.createCapabilities();
        vao=GL41.glGenVertexArrays();int vbo=GL41.glGenBuffers();GL41.glBindVertexArray(vao);GL41.glBindBuffer(GL41.GL_ARRAY_BUFFER,vbo);
        FloatBuffer q=MemoryUtil.memAllocFloat(18);q.put(new float[]{-1,-1,0,1,-1,0,1,1,0,-1,-1,0,1,1,0,-1,1,0}).flip();
        GL41.glBufferData(GL41.GL_ARRAY_BUFFER,q,GL41.GL_STATIC_DRAW);MemoryUtil.memFree(q);
        GL41.glEnableVertexAttribArray(0);GL41.glVertexAttribPointer(0,3,GL41.GL_FLOAT,false,12,0);

        Target post=target(screenW,screenH,true);targets.put("veil:post",post);
        ByteBuffer px=MemoryUtil.memAlloc(screenW*screenH*4);
        for(int y=0;y<screenH;y++)for(int x=0;x<screenW;x++){int c=in.getRGB(x,screenH-1-y);px.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)255);}
        px.flip();GL41.glBindTexture(GL41.GL_TEXTURE_2D,post.texture());
        GL41.glTexSubImage2D(GL41.GL_TEXTURE_2D,0,0,0,screenW,screenH,GL41.GL_RGBA,GL41.GL_UNSIGNED_BYTE,px);MemoryUtil.memFree(px);

        JsonObject pipeline=JsonParser.parseString(Files.readString(pinwheel.resolve("post/"+pipelineName+".json"))).getAsJsonObject();
        if(pipeline.has("framebuffers"))for(var e:pipeline.getAsJsonObject("framebuffers").entrySet()){
            JsonObject d=e.getValue().getAsJsonObject();
            int w=(int)eval(d.has("width")?d.get("width").getAsString():"q.screen_width"),h=(int)eval(d.has("height")?d.get("height").getAsString():"q.screen_height");
            targets.put(local(e.getKey()),target(Math.max(1,w),Math.max(1,h),d.has("linear")&&d.get("linear").getAsBoolean()));
        }
        for(JsonElement el:pipeline.getAsJsonArray("stages")){
            JsonObject s=el.getAsJsonObject();String type=s.get("type").getAsString();
            Target from=targets.get(local(s.get("in").getAsString())),to=targets.get(local(s.get("out").getAsString()));
            if(type.equals("veil:copy")){
                GL41.glBindFramebuffer(GL41.GL_READ_FRAMEBUFFER,from.fbo());GL41.glBindFramebuffer(GL41.GL_DRAW_FRAMEBUFFER,to.fbo());
                GL41.glBlitFramebuffer(0,0,from.width(),from.height(),0,0,to.width(),to.height(),GL41.GL_COLOR_BUFFER_BIT,GL41.GL_LINEAR);
            }else if(type.equals("veil:blit"))blit(s.get("shader").getAsString(),from,to);
            else throw new IllegalStateException("stage type "+type);
        }
        write(post,new File(args[3]));
        GLFW.glfwTerminate();
    }

    private static String local(String name){return name.contains(":")?name:"temp:"+name;}
    private static Target target(int w,int h,boolean linear){
        int tex=GL41.glGenTextures();GL41.glBindTexture(GL41.GL_TEXTURE_2D,tex);
        GL41.glTexImage2D(GL41.GL_TEXTURE_2D,0,GL41.GL_RGBA16F,w,h,0,GL41.GL_RGBA,GL41.GL_FLOAT,(ByteBuffer)null);
        int f=linear?GL41.GL_LINEAR:GL41.GL_NEAREST;
        GL41.glTexParameteri(GL41.GL_TEXTURE_2D,GL41.GL_TEXTURE_MIN_FILTER,f);GL41.glTexParameteri(GL41.GL_TEXTURE_2D,GL41.GL_TEXTURE_MAG_FILTER,f);
        GL41.glTexParameteri(GL41.GL_TEXTURE_2D,GL41.GL_TEXTURE_WRAP_S,GL41.GL_CLAMP_TO_EDGE);GL41.glTexParameteri(GL41.GL_TEXTURE_2D,GL41.GL_TEXTURE_WRAP_T,GL41.GL_CLAMP_TO_EDGE);
        int fbo=GL41.glGenFramebuffers();GL41.glBindFramebuffer(GL41.GL_FRAMEBUFFER,fbo);
        GL41.glFramebufferTexture2D(GL41.GL_FRAMEBUFFER,GL41.GL_COLOR_ATTACHMENT0,GL41.GL_TEXTURE_2D,tex,0);
        return new Target(fbo,tex,w,h);
    }
    private static void blit(String shaderId,Target from,Target to) throws Exception{
        String path=shaderId.substring(shaderId.indexOf(':')+1);
        JsonObject def=JsonParser.parseString(Files.readString(pinwheel.resolve("shaders/program/"+path+".json"))).getAsJsonObject();
        int prog=programs.computeIfAbsent(shaderId,k->{
            try{return link(source(def.get("vertex").getAsString(),"vsh"),source(def.get("fragment").getAsString(),"fsh"));}
            catch(Exception e){throw new RuntimeException(shaderId+": "+e.getMessage(),e);}
        });
        GL41.glBindFramebuffer(GL41.GL_FRAMEBUFFER,to.fbo());GL41.glViewport(0,0,to.width(),to.height());
        GL41.glClearColor(0,0,0,0);GL41.glClear(GL41.GL_COLOR_BUFFER_BIT);
        GL41.glDisable(GL41.GL_BLEND);GL41.glDisable(GL41.GL_DEPTH_TEST);
        GL41.glUseProgram(prog);
        int unit=0;
        GL41.glActiveTexture(GL41.GL_TEXTURE0+unit);GL41.glBindTexture(GL41.GL_TEXTURE_2D,from.texture());
        GL41.glUniform1i(GL41.glGetUniformLocation(prog,"DiffuseSampler0"),unit++);
        if(def.has("textures"))for(var e:def.getAsJsonObject("textures").entrySet()){
            JsonElement v=e.getValue();String name=v.isJsonObject()?v.getAsJsonObject().get("name").getAsString():v.getAsString();
            Target t=targets.get(local(name));if(t==null)throw new IllegalStateException("no framebuffer "+name);
            GL41.glActiveTexture(GL41.GL_TEXTURE0+unit);GL41.glBindTexture(GL41.GL_TEXTURE_2D,t.texture());
            GL41.glUniform1i(GL41.glGetUniformLocation(prog,e.getKey()),unit++);
        }
        GL41.glUniform2f(GL41.glGetUniformLocation(prog,"InSize"),from.width(),from.height());
        GL41.glUniform2f(GL41.glGetUniformLocation(prog,"OutSize"),to.width(),to.height());
        for(String u:uniforms){
            String[] kv=u.split("=");String[] v=kv[1].split(",");int loc=GL41.glGetUniformLocation(prog,kv[0]);
            if(loc<0)continue;
            if(v.length==1)GL41.glUniform1f(loc,Float.parseFloat(v[0]));else GL41.glUniform2f(loc,Float.parseFloat(v[0]),Float.parseFloat(v[1]));
        }
        GL41.glBindVertexArray(vao);GL41.glDrawArrays(GL41.GL_TRIANGLES,0,6);
    }
    private static String source(String id,String ext) throws Exception{
        return "#version 410 core\n"+Files.readString(pinwheel.resolve("shaders/program/"+id.substring(id.indexOf(':')+1)+"."+ext));
    }
    private static int link(String vs,String fs){
        int v=compile(GL41.GL_VERTEX_SHADER,vs),f=compile(GL41.GL_FRAGMENT_SHADER,fs),p=GL41.glCreateProgram();
        GL41.glAttachShader(p,v);GL41.glAttachShader(p,f);GL41.glLinkProgram(p);
        if(GL41.glGetProgrami(p,GL41.GL_LINK_STATUS)==0)throw new IllegalStateException(GL41.glGetProgramInfoLog(p));
        return p;
    }
    private static int compile(int type,String src){
        int s=GL41.glCreateShader(type);GL41.glShaderSource(s,src);GL41.glCompileShader(s);
        if(GL41.glGetShaderi(s,GL41.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL41.glGetShaderInfoLog(s));
        return s;
    }
    private static void write(Target t,File f) throws Exception{
        GL41.glBindFramebuffer(GL41.GL_READ_FRAMEBUFFER,t.fbo());
        ByteBuffer b=MemoryUtil.memAlloc(t.width()*t.height()*4);
        GL41.glReadPixels(0,0,t.width(),t.height(),GL41.GL_RGBA,GL41.GL_UNSIGNED_BYTE,b);
        BufferedImage img=new BufferedImage(t.width(),t.height(),BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<t.height();y++)for(int x=0;x<t.width();x++){int i=((t.height()-1-y)*t.width()+x)*4;img.setRGB(x,y,((b.get(i)&255)<<16)|((b.get(i+1)&255)<<8)|(b.get(i+2)&255));}
        MemoryUtil.memFree(b);ImageIO.write(img,"png",f);
    }

    /** The framebuffer size expressions: numbers, q.screen_width, q.screen_height, + - * / and parentheses. */
    static double eval(String expr){
        String s=expr.replace("q.screen_width",Integer.toString(screenW)).replace("q.screen_height",Integer.toString(screenH)).replace(" ","");
        int[] pos={0};double v=sum(s,pos);
        if(pos[0]!=s.length())throw new IllegalArgumentException("cannot read "+expr);
        return v;
    }
    private static double sum(String s,int[] p){double v=product(s,p);while(p[0]<s.length()&&(s.charAt(p[0])=='+'||s.charAt(p[0])=='-')){char o=s.charAt(p[0]++);double r=product(s,p);v=o=='+'?v+r:v-r;}return v;}
    private static double product(String s,int[] p){double v=atom(s,p);while(p[0]<s.length()&&(s.charAt(p[0])=='*'||s.charAt(p[0])=='/')){char o=s.charAt(p[0]++);double r=atom(s,p);v=o=='*'?v*r:v/r;}return v;}
    private static double atom(String s,int[] p){
        if(s.charAt(p[0])=='('){p[0]++;double v=sum(s,p);p[0]++;return v;}
        int start=p[0];while(p[0]<s.length()&&(Character.isDigit(s.charAt(p[0]))||s.charAt(p[0])=='.'))p[0]++;
        return Double.parseDouble(s.substring(start,p[0]));
    }
}
