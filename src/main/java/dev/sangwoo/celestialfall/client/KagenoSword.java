package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/** Extruded, faceted mesh traced from the supplied sword silhouette; no flat sprite. */
public final class KagenoSword {
    static final Identifier TEXTURE=new Identifier("textures/block/white_concrete.png");
    private KagenoSword(){}
    public static void initialize(){BuiltinItemRendererRegistry.INSTANCE.register(CelestialFall.ATOMIC_SWORD,(stack,mode,m,b,light,overlay)->render(m,b,light,overlay));}
    public static void render(MatrixStack m,VertexConsumerProvider buffers,int light,int overlay){
        renderMesh(m,buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),light,overlay);
    }
    static void renderMesh(MatrixStack m,VertexConsumer v,int light,int overlay){
        m.push();m.translate(.5,0,.5);m.scale(1/16f,1/16f,1/16f);
        // Blade shoulders, taper and needle point; bevel catches directional light.
        prism(m,v,new float[][]{{-1.02f,8},{-.86f,26.5f},{0,31.5f},{.86f,26.5f},{1.02f,8}},.17f,0x25272b,light,overlay);
        face(m,v,new float[][]{{-1.02f,8,.18f},{-.86f,26.5f,.18f},{0,31.5f,.18f},{-.55f,26,.3f},{-.7f,8,.3f}},0x555b60,light,overlay);
        face(m,v,new float[][]{{1.02f,8,.18f},{.86f,26.5f,.18f},{0,31.5f,.18f},{.55f,26,.3f},{.7f,8,.3f}},0x373c40,light,overlay);
        prism(m,v,new float[][]{{-1.4f,6.9f},{-1.45f,8.4f},{0,9.25f},{1.45f,8.4f},{1.4f,6.9f},{0,6.2f}},.42f,0x4b5258,light,overlay);
        prism(m,v,new float[][]{{-1,7.3f},{-4.4f,5.6f},{-5.4f,5.35f},{-4.8f,6.35f},{-1.1f,8.3f}},.22f,0x272b30,light,overlay);
        prism(m,v,new float[][]{{1,7.3f},{4.4f,9.45f},{5.4f,9.6f},{4.8f,8.65f},{1.1f,6.8f}},.22f,0x272b30,light,overlay);
        prism(m,v,new float[][]{{-.43f,-.2f},{-.43f,6.7f},{.43f,6.7f},{.43f,-.2f}},.34f,0x171519,light,overlay);
        for(int i=0;i<13;i++)prism(m,v,new float[][]{{-.46f,i*.48f},{-.46f,.14f+i*.48f},{.46f,.38f+i*.48f},{.46f,.24f+i*.48f}},.36f,0x363035,light,overlay);
        prism(m,v,new float[][]{{-.7f,-.6f},{-.65f,.1f},{.65f,.1f},{.7f,-.6f},{0,-2}},.3f,0x181b20,light,overlay);
        prism(m,v,new float[][]{{-1.5f,-.7f},{-1.5f,-.35f},{1.5f,-.35f},{1.5f,-.7f}},.2f,0x202429,light,overlay);m.pop();
    }
    private static void prism(MatrixStack m,VertexConsumer v,float[][] poly,float z,int color,int light,int overlay){
        float[][] front=new float[poly.length][3],back=new float[poly.length][3];
        for(int i=0;i<poly.length;i++){front[i]=new float[]{poly[i][0],poly[i][1],z};back[poly.length-i-1]=new float[]{poly[i][0],poly[i][1],-z};}
        face(m,v,front,color,light,overlay);face(m,v,back,color,light,overlay);
        for(int i=0;i<poly.length;i++){int j=(i+1)%poly.length;face(m,v,new float[][]{{poly[i][0],poly[i][1],z},{poly[i][0],poly[i][1],-z},{poly[j][0],poly[j][1],-z},{poly[j][0],poly[j][1],z}},color,light,overlay);}
    }
    private static void face(MatrixStack m,VertexConsumer v,float[][] p,int rgb,int light,int overlay){
        // Fan is split into degenerate quads, matching Minecraft's item vertex format.
        var normal=new Vector3f(p[1][0]-p[0][0],p[1][1]-p[0][1],p[1][2]-p[0][2]).cross(new Vector3f(p[2][0]-p[0][0],p[2][1]-p[0][1],p[2][2]-p[0][2])).normalize();
        for(int i=1;i<p.length-1;i++)for(int index:new int[]{0,i,i+1,i+1}){float[] a=p[index];v.vertex(m.peek().getPositionMatrix(),a[0],a[1],a[2]).color(rgb>>16&255,rgb>>8&255,rgb&255,255).texture(.5f,.5f).overlay(overlay).light(light).normal(m.peek().getNormalMatrix(),normal.x,normal.y,normal.z).next();}
    }
}
