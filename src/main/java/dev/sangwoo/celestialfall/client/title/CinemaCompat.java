package dev.sangwoo.celestialfall.client.title;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;

/** Uses only Minecraft's own shader/buffer path, with CPU skinning and vertex lighting.
 * No custom GLSL, indexed sampler queries, sampler objects, HDR or custom framebuffer. */
final class CinemaCompat implements AutoCloseable {
    private static final Identifier STAGE=new Identifier("celestialfall","textures/cinema/stage.png");
    private static final Identifier BOMI=new Identifier("celestialfall","textures/cinema/bomi.png");
    private final Map<String,CinemaAssets.Mesh> meshes=new LinkedHashMap<>();
    private final BufferBuilder buffer=new BufferBuilder(2_000_000);
    private VertexBuffer stage;
    CinemaCompat()throws IOException{
        for(String name:List.of("stage","bomi","prop","glass","glow"))meshes.put(name,CinemaAssets.mesh(name));
        meshes.put("petals",CinemaGpu.petals()); // pure mesh generation, no GPU object constructed
    }
    void render(DrawContext draw,int width,int height,float[] pose,int bones,double time){
        // The GUI sky and the scene both use Minecraft's supported drawing path.
        draw.fill(0,0,width,height,0xffb4d9f9);draw.draw();
        var oldProjection=new Matrix4f(RenderSystem.getProjectionMatrix());var oldSort=RenderSystem.getVertexSorting();
        var oldShader=RenderSystem.getShader();int oldTexture=RenderSystem.getShaderTexture(0);
        float[] oldColor=RenderSystem.getShaderColor().clone();
        boolean depth=glIsEnabled(GL_DEPTH_TEST),blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE);
        boolean mask=glGetBoolean(GL_DEPTH_WRITEMASK);int depthFunc=glGetInteger(GL_DEPTH_FUNC);
        int src=glGetInteger(GL_BLEND_SRC_RGB),dst=glGetInteger(GL_BLEND_DST_RGB),srcA=glGetInteger(GL_BLEND_SRC_ALPHA),dstA=glGetInteger(GL_BLEND_DST_ALPHA);
        var stack=RenderSystem.getModelViewStack();stack.push();
        try{
            int c=bones*12;float ex=pose[c],ey=pose[c+1],ez=pose[c+2],tx=pose[c+3],ty=pose[c+4],tz=pose[c+5],fov=pose[c+6];
            float aspect=(float)Math.max(1,width)/Math.max(1,height);
            if(aspect<1.25f){ex=.15f;tx=-.08f;ez+=1.15f;ty=1.42f;fov=49;}
            var projection=new Matrix4f().perspective((float)Math.toRadians(fov),aspect,.05f,90);
            var view=new Matrix4f().lookAt(ex,ey,ez,tx,ty,tz,0,1,0);
            RenderSystem.setProjectionMatrix(projection,VertexSorter.BY_DISTANCE);
            stack.loadIdentity();stack.multiplyPositionMatrix(view);RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL_LEQUAL);RenderSystem.depthMask(true);
            RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.clear(GL_DEPTH_BUFFER_BIT,false);
            if(stage==null){
                stage=new VertexBuffer(VertexBuffer.Usage.STATIC);stage.bind();
                stage.upload(vertices(meshes.get("stage"),pose,0,false));VertexBuffer.unbind();BufferRenderer.reset();
                meshes.remove("stage"); // don't retain both CPU and GPU copies of the static garden
            }
            RenderSystem.setShaderTexture(0,STAGE);stage.bind();stage.draw(view,projection,GameRenderer.getPositionTexColorProgram());
            VertexBuffer.unbind();BufferRenderer.reset();
            RenderSystem.setShaderTexture(0,BOMI);drawMesh("bomi",pose,time);
            RenderSystem.setShaderTexture(0,STAGE);drawMesh("prop",pose,time);
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);
            drawMesh("glass",pose,time);drawMesh("glow",pose,time);drawMesh("petals",pose,time);
        }finally{
            if(buffer.isBuilding())buffer.reset();
            VertexBuffer.unbind();BufferRenderer.reset();
            stack.pop();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(oldProjection,oldSort);
            RenderSystem.setShader(()->oldShader);RenderSystem.setShaderTexture(0,oldTexture);
            RenderSystem.setShaderColor(oldColor[0],oldColor[1],oldColor[2],oldColor[3]);
            RenderSystem.depthMask(mask);RenderSystem.depthFunc(depthFunc);RenderSystem.blendFuncSeparate(src,dst,srcA,dstA);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        }
    }
    private void drawMesh(String name,float[] pose,double time){BufferRenderer.drawWithGlobalProgram(vertices(meshes.get(name),pose,time,true));}
    private BufferBuilder.BuiltBuffer vertices(CinemaAssets.Mesh mesh,float[] pose,double time,boolean animate){
        buffer.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_TEXTURE_COLOR);float[] a=mesh.vertices();
        for(int i=0;i<a.length;i+=14){
            float x=a[i],y=a[i+1],z=a[i+2],nx=a[i+3],ny=a[i+4],nz=a[i+5];int bone=(int)a[i+12],material=(int)a[i+13];
            if(bone>=0){
                int k=bone*12;
                float px=pose[k]*x+pose[k+1]*y+pose[k+2]*z+pose[k+3],py=pose[k+4]*x+pose[k+5]*y+pose[k+6]*z+pose[k+7];
                z=pose[k+8]*x+pose[k+9]*y+pose[k+10]*z+pose[k+11];x=px;y=py;
                float qx=pose[k]*nx+pose[k+1]*ny+pose[k+2]*nz,qy=pose[k+4]*nx+pose[k+5]*ny+pose[k+6]*nz;
                nz=pose[k+8]*nx+pose[k+9]*ny+pose[k+10]*nz;nx=qx;ny=qy;
            }
            if(animate&&material==6){x+=.12f*(float)Math.sin(time*.5+a[i+2]);y=.3f+(float)(((a[i+1]-time*.09)%3.8+3.8)%3.8);}
            float sun=Math.max(0,-.4f*nx+.83f*ny+.39f*nz);
            float light=.70f+.30f*sun;
            if(material==1||material==4||material==6)light=1;
            float alpha=a[i+11];if(material==3)alpha=Math.min(alpha,.19f);
            buffer.vertex(x,y,z).texture(a[i+6],a[i+7]).color(a[i+8]*light,a[i+9]*light,a[i+10]*light,alpha).next();
        }
        return buffer.end();
    }
    @Override public void close(){if(stage!=null){stage.close();stage=null;}buffer.clear();meshes.clear();}
}
