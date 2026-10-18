package dev.sangwoo.celestialfall.client.menu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
public final class MenuPainter {
    private static final Identifier DOCK=new Identifier("celestialfall","textures/gui/menu_dock.png");
    public static void draw(DrawContext c,UiCanvas scene,MenuLayout.View v){
        c.draw();var matrices=c.getMatrices();matrices.push();matrices.translate(v.x(),v.y(),10);matrices.scale((float)v.scale(),(float)v.scale(),1);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.disableDepthTest();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        for(var sprite:scene.sprites){
            matrices.push();matrices.translate(sprite.x(),sprite.y(),0);matrices.scale((float)(sprite.w()/sprite.sw()),(float)(sprite.h()/sprite.sh()),1);
            c.drawTexture(DOCK,0,0,(float)sprite.u(),(float)sprite.v(),sprite.sw(),sprite.sh(),DockSprites.WIDTH,DockSprites.HEIGHT);matrices.pop();
        }
        c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.disableDepthTest();RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        if(!scene.shapes.isEmpty()){
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR);var m=matrices.peek().getPositionMatrix();
        for(var shape:scene.shapes){double[] p=shape.xy();double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(int i=1;i<p.length;i+=2){min=Math.min(min,p[i]);max=Math.max(max,p[i]);}
            for(int i=1;i<p.length/2-1;i++)for(int j:new int[]{0,i,i+1}){double t=(p[j*2+1]-min)/Math.max(.001,max-min);int a=shape.top(),z=shape.bottom();b.vertex(m,(float)p[j*2],(float)p[j*2+1],0).color(mix(a,z,16,t),mix(a,z,8,t),mix(a,z,0,t),mix(a,z,24,t)).next();}}
        BufferRenderer.drawWithGlobalProgram(b.end());}
        RenderSystem.enableCull();RenderSystem.enableDepthTest();
        var font=MinecraftClient.getInstance().textRenderer;
        for(var t:scene.labels){float scale=(float)Math.min(t.size()/9,t.maxWidth()/Math.max(1,font.getWidth(t.text())));matrices.push();matrices.translate(t.x(),t.y(),1);matrices.scale(scale,scale,1);c.drawText(font,t.text(),t.centered()?-font.getWidth(t.text())/2:0,0,t.color(),false);matrices.pop();}
        c.draw();matrices.pop();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
    }
    private static int mix(int a,int b,int shift,double t){return (int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t);}
}
