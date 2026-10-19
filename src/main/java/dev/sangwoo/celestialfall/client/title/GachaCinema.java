package dev.sangwoo.celestialfall.client.title;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * The gacha's pull scenes (2.39.0) on the title cinema's pipeline: Bomi (the intro's own rig, skin and hourglass), copies
 * of her hourglass, and furniture built here, in a dark place. A dim cool key light casts soft shadows; every hourglass is
 * a point light in its grade's colour; HDR, bloom and depth of field as in the intro. It shares the title's assets and its
 * GL state {@link CinemaGpu.Scope} but none of its programs or targets, and calls only the GL entry points
 * {@link CinemaSupport} checks, so the title renders exactly as before.
 */
public final class GachaCinema implements AutoCloseable {
    private static final Logger LOG=LoggerFactory.getLogger("Botand/GachaCinema");
    /** Where the hourglass sits in Bomi's model space (scene.json hourglass_center); its mesh is built around it. */
    public static final float[] HOURGLASS_CENTER={0,1.1f,.565f};
    public static final int MAX_GLASSES=10,MAX_LIGHTS=12;

    /** Everything one frame shows; the gacha's timeline fills it. Matrices are row-major 3x4, as performance.anim stores them. */
    public static final class Frame {
        public final float[] eye=new float[3],target=new float[3];public float fov=34,focus=2,time;
        public final float[] bones=new float[7*12];
        public boolean chair,cupboard,wall,held;
        public int glasses;
        public final float[][] place=new float[MAX_GLASSES][12],accent=new float[MAX_GLASSES][3];
        public final float[] glow=new float[MAX_GLASSES],hold=new float[MAX_GLASSES];
        public int lights;
        public final float[][] lightPos=new float[MAX_LIGHTS][3],lightColor=new float[MAX_LIGHTS][3];
        public final float[] keyDir={-.35f,.85f,-.4f},key={.10f,.12f,.2f},ambient={.006f,.007f,.012f},clear={.0015f,.0018f,.004f};
        public float bloom=.42f,exposure=1;
        public int motes;
        /** The spotlight (2.40.0): where it hangs, where it points, its cone (cosines of the outer and inner angle), its light. */
        public final float[] spotPos=new float[3],spotDir={0,-1,0},spotCone={.94f,.97f},spotColor=new float[3];
        /** Its visible beam: strength, the floor radius it reaches; the floor's magic circle: strength, colour, centre, radius. */
        public float beam,beamRadius=1;
        public float circle,circleRadius=1.2f;
        public final float[] circleColor=new float[3],circleAt=new float[2];
        /** Sparks circling a point: strength, colour, centre, radius and the height they rise through. */
        public float sparks,sparkRadius=.3f,sparkHeight=.5f;
        public final float[] sparkColor=new float[3],sparkCenter=new float[3];
        /** Half the width of the key light's shadow map, about the point it looks at. */
        public float shadowSpan=3.4f;
        public final float[] shadowAt={0,.8f,.3f};
    }

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
    private int lit,shadowProgram,blur,composite,screenVao,atlas,skin;
    private Target shadow,hdr,ping,pong;
    private final float[] palette=new float[32*16];
    private final Matrix4f vp=new Matrix4f(),light=new Matrix4f();
    private int viewWidth=1,viewHeight=1,viewX,viewY;
    private Boolean desktop;
    private boolean failed;

    /** Draws {@code f} over the whole framebuffer; false when this machine cannot (the caller then draws its 2D stand-in). */
    public boolean render(Frame f){
        if(!supported())return false;
        try{
            // Never enter Scope on an unsupported context: JNI aborts bypass Java catch.
            try(var state=new CinemaGpu.Scope()){
                while(step<STEPS)create();
                var w=MinecraftClient.getInstance().getWindow();
                draw(f,w.getFramebufferWidth(),w.getFramebufferHeight(),state);
            }
            return true;
        }catch(Exception e){
            LOG.warn("Gacha scenes unavailable; showing them in 2D",e);failed=true;close();return false;
        }
    }
    /**
     * Readies one more piece (a shader program, a mesh, the shadow map) while the gacha screen waits, so the first pull's
     * scene starts without a stall; true once everything is ready.
     */
    public boolean prepare(){
        if(!supported())return false;
        if(step>=STEPS)return true;
        try(var state=new CinemaGpu.Scope()){create();}
        catch(Exception e){LOG.warn("Gacha scenes unavailable; showing them in 2D",e);failed=true;close();return false;}
        return step>=STEPS;
    }
    private boolean supported(){
        if(failed)return false;
        if(desktop==null){String why=CinemaSupport.incompatibility();desktop=why==null;if(why!=null)LOG.info("Gacha scenes in 2D ({})",why);}
        return desktop;
    }
    /** Where a world point lands on screen, in GUI units (NaN behind the eye), as of the last frame drawn. */
    public float[] project(float x,float y,float z,int guiWidth,int guiHeight){
        var v=new Vector4f(x,y,z,1).mul(vp);
        if(v.w<=1e-4f)return new float[]{Float.NaN,Float.NaN};
        return new float[]{(v.x/v.w*.5f+.5f)*guiWidth,(1-(v.y/v.w*.5f+.5f))*guiHeight};
    }

    /** How far {@link #create} has got, of {@link #STEPS} pieces. */
    private int step;
    private static final int STEPS=9;
    /** Makes the next piece of the GPU resources. */
    private void create()throws IOException {
        CinemaSupport.requireDesktop();
        try{
            switch(step){
                case 0->lit=program("gacha.vert","gacha.frag");
                case 1->shadowProgram=program("gacha.vert","shadow.frag");
                case 2->blur=program("screen.vert","blur.frag");
                case 3->{composite=program("screen.vert","gacha_composite.frag");screenVao=glGenVertexArrays();}
                case 4->meshes.put("bomi",upload(CinemaAssets.mesh("bomi")));
                case 5->meshes.put("prop",upload(CinemaAssets.mesh("prop")));
                case 6->meshes.put("glass",upload(CinemaAssets.mesh("glass")));
                case 7->{meshes.put("floor",upload(floor()));meshes.put("chair",upload(chair()));meshes.put("cupboard",upload(cupboard()));meshes.put("wall",upload(wall()));meshes.put("motes",upload(motes(160)));
                    meshes.put("beam",upload(beam()));meshes.put("circle",upload(circle()));meshes.put("sparks",upload(sparks(90)));}
                case 8->shadow=target(2048,2048,true,true);
            }
            step++;
        }catch(IOException|RuntimeException e){close();throw e;}
    }
    private static String source(String file)throws IOException {
        try(var in=GachaCinema.class.getResourceAsStream("/assets/celestialfall/cinema/shaders/"+file)){
            if(in==null)throw new IOException("Missing cinema shader "+file);return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private int program(String vert,String frag)throws IOException {
        int v=0,f=0,p=0;
        try{
            v=compile(GL_VERTEX_SHADER,source(vert));f=compile(GL_FRAGMENT_SHADER,source(frag));p=glCreateProgram();
            glAttachShader(p,v);glAttachShader(p,f);
            String[] names={"Position","Normal","UV","Color","Bone","Material"};
            for(int i=0;i<names.length;i++)glBindAttribLocation(p,i,names[i]);
            glBindFragDataLocation(p,0,"fragColor");glLinkProgram(p);
            if(glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE)throw new IOException("Gacha shader link: "+glGetProgramInfoLog(p));
            programs.add(p);return p;
        }catch(IOException|RuntimeException e){if(p!=0)glDeleteProgram(p);throw e;}
        finally{if(v!=0)glDeleteShader(v);if(f!=0)glDeleteShader(f);}
    }
    private static int compile(int type,String source)throws IOException {
        int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);
        if(glGetShaderi(s,GL_COMPILE_STATUS)==GL_FALSE){String error=glGetShaderInfoLog(s);glDeleteShader(s);throw new IOException("Gacha shader compile: "+error);}return s;
    }
    private static Mesh upload(CinemaAssets.Mesh data){
        int vao=glGenVertexArrays(),vbo=glGenBuffers();FloatBuffer f=MemoryUtil.memAllocFloat(data.vertices().length);
        try{
            f.put(data.vertices()).flip();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,f,GL_STATIC_DRAW);
            int[] sizes={3,3,2,4,1,1};int offset=0;
            for(int i=0;i<sizes.length;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,sizes[i],GL_FLOAT,false,56,offset*4L);offset+=sizes[i];}
            return new Mesh(vao,vbo,data.count());
        }catch(RuntimeException e){glDeleteVertexArrays(vao);glDeleteBuffers(vbo);throw e;}finally{MemoryUtil.memFree(f);}
    }
    private static Target target(int w,int h,boolean depth,boolean depthOnly)throws IOException {
        int fb=glGenFramebuffers(),color=0,d=0;
        try{
            glBindFramebuffer(GL_FRAMEBUFFER,fb);
            if(!depthOnly){color=attachment(w,h,false);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);}
            if(depth){d=attachment(w,h,true);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,d,0);}
            if(depthOnly){glDrawBuffer(GL_NONE);glReadBuffer(GL_NONE);texture(0,d);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_COMPARE_MODE,GL_COMPARE_REF_TO_TEXTURE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_COMPARE_FUNC,GL_LEQUAL);}
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IOException("Gacha framebuffer is incomplete");
            return new Target(fb,color,d,w,h);
        }catch(IOException|RuntimeException e){glDeleteFramebuffers(fb);if(color!=0)glDeleteTextures(color);if(d!=0)glDeleteTextures(d);throw e;}
    }
    private static int attachment(int w,int h,boolean depth){
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
    private static void vec3(int p,String n,float[] v){glUniform3f(uniform(p,n),v[0],v[1],v[2]);}
    /** Loads the bone palette: Bomi's bones, or only slot 1 (the bone the hourglass mesh follows) set to {@code glass}. */
    private void bones(int p,float[] bomi,float[] glass){
        for(int i=0;i<32;i++){
            int a=i*16;Arrays.fill(palette,a,a+16,0);palette[a]=palette[a+5]=palette[a+10]=palette[a+15]=1;
            float[] src=glass!=null?(i==1?glass:null):(i<7?bomi:null);int at=glass!=null?0:i*12;
            if(src!=null)for(int row=0;row<3;row++)for(int col=0;col<4;col++)palette[a+col*4+row]=src[at+row*4+col];
        }
        glUniformMatrix4fv(uniform(p,"Bones"),false,palette);
    }
    private void look(int p,float accent[],float glow,float hold){glUniform3f(uniform(p,"Accent"),accent[0],accent[1],accent[2]);glUniform1f(uniform(p,"Glow"),glow);glUniform1f(uniform(p,"Hold"),hold);}
    private static final float[] NONE={0,0,0};

    private void draw(Frame f,int width,int height,CinemaGpu.Scope state)throws IOException {
        float scale=Math.min(1f,2560f/Math.max(width,height));int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));resize(w,h);
        var tm=MinecraftClient.getInstance().getTextureManager();
        atlas=tm.getTexture(new Identifier("celestialfall","textures/cinema/stage.png")).getGlId();
        skin=tm.getTexture(new Identifier("celestialfall","textures/cinema/bomi.png")).getGlId();
        for(int id:new int[]{atlas,skin}){texture(0,id);int filter=id==skin?GL_NEAREST:GL_LINEAR;glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,filter);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,filter);}
        vp.identity().perspective((float)Math.toRadians(f.fov),(float)w/h,.05f,90).lookAt(f.eye[0],f.eye[1],f.eye[2],f.target[0],f.target[1],f.target[2],0,1,0);
        var kd=new Vector3f(f.keyDir[0],f.keyDir[1],f.keyDir[2]).normalize();
        float span=f.shadowSpan,ax=f.shadowAt[0],ay=f.shadowAt[1],az=f.shadowAt[2];
        boolean overhead=kd.x*kd.x+kd.z*kd.z<1e-4f;
        light.identity().ortho(-span,span,-span,span,.1f,40).lookAt(ax+kd.x*14,ay+kd.y*14,az+kd.z*14,ax,ay,az,0,overhead?0:1,overhead?1:0);
        glDisable(GL_FRAMEBUFFER_SRGB);glDisable(GL_SCISSOR_TEST);glDisable(GL_CULL_FACE);glDisable(GL_BLEND);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glDepthMask(true);glColorMask(true,true,true,true);glClearDepth(1);
        // shadows of the solid things in the key light
        shadow.bind();glClear(GL_DEPTH_BUFFER_BIT);glEnable(GL_POLYGON_OFFSET_FILL);glPolygonOffset(1.2f,2);
        glUseProgram(shadowProgram);matrix(shadowProgram,"ViewProjection",light);matrix(shadowProgram,"LightMatrix",light);glUniform1f(uniform(shadowProgram,"Time"),f.time);
        glUniform3f(uniform(shadowProgram,"Camera"),f.eye[0],f.eye[1],f.eye[2]);glUniform1i(uniform(shadowProgram,"Atlas"),0);
        solids(shadowProgram,f,true);
        glDisable(GL_POLYGON_OFFSET_FILL);
        // the lit scene in HDR
        hdr.bind();glClearColor(f.clear[0],f.clear[1],f.clear[2],1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        glUseProgram(lit);matrix(lit,"ViewProjection",vp);matrix(lit,"LightMatrix",light);glUniform1f(uniform(lit,"Time"),f.time);
        glUniform3f(uniform(lit,"Camera"),f.eye[0],f.eye[1],f.eye[2]);glUniform1i(uniform(lit,"Atlas"),0);glUniform1i(uniform(lit,"ShadowMap"),1);texture(1,shadow.depth);
        glUniform3f(uniform(lit,"KeyDir"),kd.x,kd.y,kd.z);vec3(lit,"Key",f.key);vec3(lit,"Ambient",f.ambient);
        vec3(lit,"SpotPos",f.spotPos);var sd=new Vector3f(f.spotDir[0],f.spotDir[1],f.spotDir[2]).normalize();glUniform3f(uniform(lit,"SpotDir"),sd.x,sd.y,sd.z);
        glUniform2f(uniform(lit,"SpotCone"),f.spotCone[0],f.spotCone[1]);vec3(lit,"SpotColor",f.spotColor);
        glUniform3f(uniform(lit,"SparkCenter"),f.sparkCenter[0],f.sparkCenter[1],f.sparkCenter[2]);glUniform1f(uniform(lit,"SparkRadius"),f.sparkRadius);glUniform1f(uniform(lit,"SparkHeight"),f.sparkHeight);
        int lights=Math.min(MAX_LIGHTS,f.lights);glUniform1i(uniform(lit,"Lights"),lights);
        for(int i=0;i<lights;i++){vec3(lit,"LightPos["+i+"]",f.lightPos[i]);vec3(lit,"LightColor["+i+"]",f.lightColor[i]);}
        solids(lit,f,false);
        // glass, back to front, then the motes as added light
        glEnable(GL_BLEND);glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);
        texture(0,atlas);
        Integer[] order=new Integer[Math.min(MAX_GLASSES,f.glasses)];for(int i=0;i<order.length;i++)order[i]=i;
        Arrays.sort(order,Comparator.comparingDouble(i->-distance(f,i)));
        for(int i:order){bones(lit,f.bones,f.held&&i==0?null:f.place[i]);look(lit,f.accent[i],f.glow[i],f.hold[i]);meshes.get("glass").draw();}
        if(f.motes>0){glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);bones(lit,f.bones,null);look(lit,NONE,0,0);meshes.get("motes").draw();}
        // light drawn as light (2.40.0): the floor's magic circle, the spotlight's beam, sparks about the hourglass
        glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
        if(f.circle>0){float r=f.circleRadius;bones(lit,null,new float[]{r,0,0,f.circleAt[0],0,1,0,.004f,0,0,r,f.circleAt[1]});look(lit,f.circleColor,f.circle,0);meshes.get("circle").draw();}
        if(f.beam>0){
            // the cone from the lamp to the floor: scaled to the floor radius, its height the lamp's
            float r=f.beamRadius,hgt=f.spotPos[1];
            bones(lit,null,new float[]{r,0,0,f.spotPos[0],0,hgt,0,0,0,0,r,f.spotPos[2]});look(lit,f.spotColor,f.beam,0);meshes.get("beam").draw();
        }
        if(f.sparks>0){bones(lit,f.bones,null);look(lit,f.sparkColor,f.sparks,0);meshes.get("sparks").draw();}
        glDepthMask(true);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);
        // bloom, then into Minecraft's framebuffer
        glUseProgram(blur);glUniform1i(uniform(blur,"Source"),0);glBindVertexArray(screenVao);
        for(int i=0;i<4;i++){
            Target dest=i%2==0?ping:pong;dest.bind();texture(0,i==0?hdr.color:i%2==0?pong.color:ping.color);
            glUniform1i(uniform(blur,"Extract"),i==0?1:0);glUniform2f(uniform(blur,"Direction"),i%2==0?1.4f/ping.width:0,i%2==1?1.4f/ping.height:0);glDrawArrays(GL_TRIANGLES,0,3);
        }
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,state.drawFramebuffer);glViewport(state.viewport[0],state.viewport[1],state.viewport[2],state.viewport[3]);
        glUseProgram(composite);texture(0,hdr.color);texture(1,pong.color);texture(2,hdr.depth);
        glUniform1i(uniform(composite,"Source"),0);glUniform1i(uniform(composite,"Bloom"),1);glUniform1i(uniform(composite,"Depth"),2);glUniform2f(uniform(composite,"Resolution"),w,h);
        glUniform1f(uniform(composite,"Focus"),f.focus);glUniform1f(uniform(composite,"BloomStrength"),f.bloom);glUniform1f(uniform(composite,"Exposure"),f.exposure);
        glDrawArrays(GL_TRIANGLES,0,3);
    }
    private static double distance(Frame f,int i){
        if(f.held&&i==0)return 0;
        float[] m=f.place[i],c=HOURGLASS_CENTER;double[] p=new double[3];
        for(int r=0;r<3;r++)p[r]=m[r*4]*c[0]+m[r*4+1]*c[1]+m[r*4+2]*c[2]+m[r*4+3]-f.eye[r];
        return p[0]*p[0]+p[1]*p[1]+p[2]*p[2];
    }
    /** The opaque things: floor, furniture, Bomi, and every hourglass's frame, sand and jewels (not its glass). */
    private void solids(int p,Frame f,boolean shadowPass){
        bones(p,f.bones,null);if(!shadowPass)look(p,NONE,0,0);
        texture(0,atlas);
        if(!shadowPass)meshes.get("floor").draw();
        if(f.wall&&!shadowPass)meshes.get("wall").draw();
        if(f.chair)meshes.get("chair").draw();
        if(f.cupboard)meshes.get("cupboard").draw();
        texture(0,skin);meshes.get("bomi").draw();
        texture(0,atlas);
        for(int i=0;i<Math.min(MAX_GLASSES,f.glasses);i++){
            bones(p,f.bones,f.held&&i==0?null:f.place[i]);if(!shadowPass)look(p,f.accent[i],f.glow[i],f.hold[i]);meshes.get("prop").draw();
        }
    }

    // ---- the set, built here: 14 floats a vertex (position, normal, the atlas's white tile, colour, no bone, material) ----
    private static final class Builder {
        final List<float[]> v=new ArrayList<>();
        void tri(float[] a,float[] b,float[] c,float[] n,float[] rgba,float material){for(float[] p:new float[][]{a,b,c})v.add(new float[]{p[0],p[1],p[2],n[0],n[1],n[2],.02f,.02f,rgba[0],rgba[1],rgba[2],rgba[3],-1,material});}
        void quad(float[] a,float[] b,float[] c,float[] d,float[] rgba,float material){
            var v=new Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2]).cross(c[0]-a[0],c[1]-a[1],c[2]-a[2]).normalize();float[] n={v.x,v.y,v.z};
            tri(a,b,c,n,rgba,material);tri(a,c,d,n,rgba,material);
        }
        /** An axis-aligned box. */
        void box(float x0,float y0,float z0,float x1,float y1,float z1,float[] rgba,float material){
            float[][] p={{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};
            int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
            for(int[] q:faces)quad(p[q[0]],p[q[1]],p[q[2]],p[q[3]],rgba,material);
        }
        CinemaAssets.Mesh mesh(){float[] out=new float[v.size()*14];for(int i=0;i<v.size();i++)System.arraycopy(v.get(i),0,out,i*14,14);return new CinemaAssets.Mesh(out);}
    }
    static float[] rgb(int hex){return new float[]{(hex>>16&255)/255f,(hex>>8&255)/255f,(hex&255)/255f,1};}
    /** Polished dark stone under everything, fading into the dark (the shader fades it). */
    static CinemaAssets.Mesh floor(){var b=new Builder();float s=9;b.quad(new float[]{-s,0,-s},new float[]{-s,0,s},new float[]{s,0,s},new float[]{s,0,-s},rgb(0x2a2d3c),8);return b.mesh();}
    /** The wall the cupboard stands against (the 10-pull's room). */
    static CinemaAssets.Mesh wall(){
        var b=new Builder();float z=CUPBOARD_Z+CUPBOARD_DEPTH+.06f;
        b.quad(new float[]{6,0,z},new float[]{-6,0,z},new float[]{-6,5.2f,z},new float[]{6,5.2f,z},rgb(0x272a44),0);
        b.box(-6,0,z-.04f,6,.22f,z,rgb(0x2b1d17),0);b.box(-6,1.5f,z-.03f,6,1.56f,z,rgb(0x3a281e),0);
        for(int i=-8;i<=8;i++)b.box(i*.8f-.008f,0,z-.005f,i*.8f+.008f,1.5f,z,rgb(0x1f2236),0);
        return b.mesh();
    }
    /** A wooden chair with a pale blue cushion, its seat at the origin's height {@link #SEAT}, facing +Z. */
    public static final float SEAT=.5f;
    static CinemaAssets.Mesh chair(){
        var b=new Builder();float[] wood=rgb(0x4a2c1d),dark=rgb(0x2e1b12),cushion=rgb(0x9fb6e8),gold=rgb(0xc9a25a);
        float s=.25f,t=.045f,top=SEAT-.05f;
        for(float x:new float[]{-s,s-t})for(float z:new float[]{-s,s-t})b.box(x,0,z,x+t,top,z+t,wood,0);
        b.box(-s-.01f,top,-s-.01f,s+.01f,top+.04f,s+.01f,wood,0);
        b.box(-s+.02f,top+.04f,-s+.02f,s-.02f,SEAT+.03f,s-.02f,cushion,0);
        for(float x:new float[]{-s,s-t})b.box(x,top,-s,x+t,top+.62f,-s+t,dark,0);
        b.box(-s,top+.48f,-s,s,top+.62f,-s+t,dark,0);b.box(-s+.03f,top+.62f,-s+.005f,s-.03f,top+.645f,-s+t-.005f,gold,2);
        for(int i=0;i<3;i++){float x=-.12f+i*.12f;b.box(x-.013f,top+.06f,-s+.008f,x+.013f,top+.48f,-s+t-.008f,wood,0);}
        b.box(-s,.12f,-s+.005f,s,.15f,-s+t-.005f,dark,0);b.box(-s,.12f,s-t+.005f,s,.15f,s-.005f,dark,0);
        return b.mesh();
    }
    /**
     * The cupboard (찬장), built large (user request, 2.40.0: a bigger cupboard for bigger hourglasses): a closed cabinet
     * below, two open shelves above for the ten hourglasses, facing -Z toward Bomi; every measure the 2.39.0 one's times
     * {@link #CUPBOARD_SCALE}.
     */
    public static final float CUPBOARD_SCALE=1.55f,CUPBOARD_Z=3.0f,CUPBOARD_DEPTH=.46f*CUPBOARD_SCALE,
        SHELF_LOW=.9f*CUPBOARD_SCALE,SHELF_HIGH=1.45f*CUPBOARD_SCALE,SHELF_X=.34f*CUPBOARD_SCALE,CUPBOARD_TOP=2.0f*CUPBOARD_SCALE;
    static CinemaAssets.Mesh cupboard(){
        var b=new Builder();float[] wood=rgb(0x4b2f21),dark=rgb(0x2a1a12),back=rgb(0x1e1416),gold=rgb(0xd7b26a),panel=rgb(0x3d2619);
        float k=CUPBOARD_SCALE,x0=-.98f*k,x1=.98f*k,z0=CUPBOARD_Z,z1=CUPBOARD_Z+CUPBOARD_DEPTH,low=SHELF_LOW,high=SHELF_HIGH,top=CUPBOARD_TOP;
        // lower cabinet with two panelled doors and gold knobs
        b.box(x0,0,z0+.02f*k,x1,.08f*k,z1,dark,0);
        b.box(x0,.08f*k,z0,x1,low-.04f*k,z1,wood,0);
        for(int d=0;d<2;d++){float a=d==0?x0+.06f*k:.02f*k,c=d==0?-.02f*k:x1-.06f*k;
            b.box(a,.14f*k,z0-.012f*k,c,low-.1f*k,z0,panel,0);b.box(a+.06f*k,.2f*k,z0-.022f*k,c-.06f*k,low-.16f*k,z0-.012f*k,wood,0);
            float kx=d==0?c-.07f*k:a+.07f*k;b.box(kx-.018f*k,.5f*k,z0-.05f*k,kx+.018f*k,.536f*k,z0-.022f*k,gold,2);}
        // counter top (the lower shelf)
        b.box(x0-.03f*k,low-.04f*k,z0-.04f*k,x1+.03f*k,low,z1+.01f*k,wood,0);
        // hutch: sides, back, middle shelf, top and crown
        b.box(x0,low,z0+.06f*k,x0+.05f*k,top,z1,wood,0);b.box(x1-.05f*k,low,z0+.06f*k,x1,top,z1,wood,0);
        b.box(x0+.05f*k,low,z1-.03f*k,x1-.05f*k,top,z1,back,0);
        b.box(x0+.05f*k,high-.035f*k,z0+.08f*k,x1-.05f*k,high,z1-.03f*k,wood,0);
        b.box(x0+.05f*k,high-.05f*k,z0+.075f*k,x1-.05f*k,high-.035f*k,z0+.09f*k,gold,2);
        b.box(x0-.02f*k,top,z0+.04f*k,x1+.02f*k,top+.05f*k,z1+.01f*k,wood,0);
        b.box(x0-.06f*k,top+.05f*k,z0,x1+.06f*k,top+.09f*k,z1+.03f*k,dark,0);
        b.box(x0-.04f*k,top+.09f*k,z0+.02f*k,x1+.04f*k,top+.16f*k,z1+.02f*k,wood,0);
        b.box(x0-.06f*k,top+.16f*k,z0,x1+.06f*k,top+.19f*k,z1+.03f*k,dark,0);
        // a gold trim along the crown and a round medallion at its middle
        b.box(x0-.065f*k,top+.12f*k,z0-.004f*k,x1+.065f*k,top+.135f*k,z0+.004f*k,gold,2);
        b.box(-.09f*k,top+.19f*k,z0+.03f*k,.09f*k,top+.33f*k,z0+.06f*k,dark,0);b.box(-.06f*k,top+.215f*k,z0+.02f*k,.06f*k,top+.305f*k,z0+.03f*k,gold,2);
        // pilasters with gold capitals at the hutch's front edges
        for(float x:new float[]{x0,x1-.07f*k}){b.box(x,low,z0+.03f*k,x+.07f*k,top,z0+.07f*k,dark,0);b.box(x-.005f*k,top-.06f*k,z0+.025f*k,x+.075f*k,top-.035f*k,z0+.075f*k,gold,2);}
        return b.mesh();
    }
    /** The spotlight's beam: an open cone, its apex at (0, 1, 0) over its unit circle on the floor (bone 1 sets its size). */
    static CinemaAssets.Mesh beam(){
        int n=48;var v=new float[n*6*14];int k=0;
        for(int i=0;i<n;i++){
            double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
            float[][] p={{(float)Math.cos(a0),0,(float)Math.sin(a0),0},{(float)Math.cos(a1),0,(float)Math.sin(a1),0},{0,1,0,1}};
            // a thin sliver of a sleeve near the apex rather than a point, so the top end has some width to fade over
            float[][] q={p[0],p[1],{(float)Math.cos(a1)*.06f,1,(float)Math.sin(a1)*.06f,1},p[0],{(float)Math.cos(a1)*.06f,1,(float)Math.sin(a1)*.06f,1},{(float)Math.cos(a0)*.06f,1,(float)Math.sin(a0)*.06f,1}};
            for(float[] c:q){float nx=c[0],nz=c[2],l=(float)Math.max(1e-4,Math.hypot(nx,nz));
                for(float val:new float[]{c[0],c[1],c[2],nx/l,.5f,nz/l,(float)(i/(double)n),c[3],1,1,1,1,1,9})v[k++]=val;}
        }
        return new CinemaAssets.Mesh(v);
    }
    /** The floor's magic circle: a unit square in x and z (bone 1 places and sizes it), its pattern drawn by the shader. */
    static CinemaAssets.Mesh circle(){
        float[][] c={{-1,0,-1,0,0},{1,0,-1,1,0},{1,0,1,1,1},{-1,0,-1,0,0},{1,0,1,1,1},{-1,0,1,0,1}};
        var v=new float[6*14];int k=0;
        for(float[] p:c)for(float val:new float[]{p[0],p[1],p[2],0,1,0,p[3],p[4],1,1,1,1,1,10})v[k++]=val;
        return new CinemaAssets.Mesh(v);
    }
    /** Sparks to circle the held hourglass (material 11; the vertex shader moves them). */
    static CinemaAssets.Mesh sparks(int n){
        var v=new float[n*6*14];int k=0;var r=new Random(23);
        for(int i=0;i<n;i++){
            float angle=r.nextFloat()*6.2832f,radius=.55f+r.nextFloat()*.6f,phase=r.nextFloat(),speed=(.5f+r.nextFloat()*.9f)*(r.nextBoolean()?1:-1),rise=.08f+r.nextFloat()*.22f,size=.004f+r.nextFloat()*.008f;
            float[][] corner={{0,0},{1,0},{1,1},{0,0},{1,1},{0,1}};
            for(float[] c:corner)for(float val:new float[]{angle,radius,phase,speed,rise,size,c[0],c[1],1,1,1,.4f+.6f*r.nextFloat(),-1,11})v[k++]=val;
        }
        return new CinemaAssets.Mesh(v);
    }
    /** Motes of dust or light drifting up through the scene (material 6; the vertex shader moves and turns them). */
    static CinemaAssets.Mesh motes(int n){
        var v=new float[n*6*14];int k=0;var r=new Random(11);
        for(int i=0;i<n;i++){
            float x=(r.nextFloat()*2-1)*2.2f,y=r.nextFloat()*2.6f,z=-1.2f+r.nextFloat()*3.6f,speed=.04f+r.nextFloat()*.07f,a=.35f+r.nextFloat()*.65f;
            float[][] corner={{0,0},{1,0},{1,1},{0,0},{1,1},{0,1}};
            for(float[] c:corner)for(float val:new float[]{x,y,z,speed,2.6f,0,c[0],c[1],.85f,.92f,1,a,-1,6})v[k++]=val;
        }
        return new CinemaAssets.Mesh(v);
    }

    private void releaseTargets(){if(hdr!=null){hdr.close();hdr=null;}if(ping!=null){ping.close();ping=null;}if(pong!=null){pong.close();pong=null;}}
    @Override public void close(){
        // GL objects belong to the render thread's context; elsewhere a GL call aborts the game
        if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()){com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(this::close);return;}
        if(Boolean.TRUE.equals(desktop)&&(hdr!=null||shadow!=null||!meshes.isEmpty()||!programs.isEmpty()||screenVao!=0)){
            releaseTargets();if(shadow!=null){shadow.close();shadow=null;}meshes.values().forEach(Mesh::close);meshes.clear();
            programs.forEach(GachaCinema::deleteProgram);programs.clear();if(screenVao!=0){glDeleteVertexArrays(screenVao);screenVao=0;}
        }
        step=0;
    }
    private static void deleteProgram(int p){glDeleteProgram(p);}
}
