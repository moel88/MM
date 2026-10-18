package dev.sangwoo.celestialfall.client.title;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;

/** One ordinary GUI texture and text. Compatible with the vanilla GUI render path. */
public final class TitlePainter {
    public static final Identifier FONT=new Identifier("celestialfall","title");
    private static final Identifier LOGO=new Identifier("celestialfall","textures/title/logo.png");
    public static void draw(DrawContext c,TitleScene scene,TitleLayout l){
        MinecraftClient.getInstance().getTextureManager().getTexture(LOGO).setFilter(true,false);
        c.draw();var stack=c.getMatrices();stack.push();stack.translate(l.ox(),l.oy(),20);stack.scale((float)l.scale(),(float)l.scale(),1);
        try{
            var list=scene.commands;
            for(int index=0;index<list.size();){
                var cmd=list.get(index);c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.disableDepthTest();RenderSystem.setShaderColor(1,1,1,1);
                if(cmd.kind().equals("shape")){
                    RenderSystem.setShader(GameRenderer::getPositionColorProgram);var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR);var m=stack.peek().getPositionMatrix();
                    while(index<list.size()&&list.get(index).kind().equals("shape")){
                        var q=list.get(index++);double[] xy=q.points();double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
                        for(int k=1;k<xy.length;k+=2){min=Math.min(min,xy[k]);max=Math.max(max,xy[k]);}
                        for(int i=1;i<xy.length/2-1;i++)for(int j:new int[]{0,i,i+1}){
                            double t=(xy[2*j+1]-min)/Math.max(.001,max-min);int col=TitleArt.blend(q.top(),q.bottom(),t);
                            b.vertex(m,(float)xy[2*j],(float)xy[2*j+1],0).color(col>>16&255,col>>8&255,col&255,col>>>24).next();
                        }
                    }
                    BufferRenderer.drawWithGlobalProgram(b.end());
                }else if(cmd.kind().equals("image")){
                    double[] r=cmd.points();int tw=TitleLayout.IMAGE_WIDTH,th=TitleLayout.IMAGE_HEIGHT;
                    stack.push();stack.translate(r[0],r[1],0);stack.scale((float)(r[2]/tw),(float)(r[3]/th),1);
                    RenderSystem.setShaderColor(1,1,1,(float)r[4]);c.drawTexture(LOGO,0,0,0,0,tw,th,tw,th);stack.pop();RenderSystem.setShaderColor(1,1,1,1);index++;
                }else{
                    var font=MinecraftClient.getInstance().textRenderer;
                    while(index<list.size()&&list.get(index).kind().equals("text")){
                        var t=list.get(index++);var text=Text.literal(t.text()).styled(style->style.withFont(FONT));
                        float scale=(float)Math.min(t.size()/12,t.maxWidth()/Math.max(1,font.getWidth(text)));
                        stack.push();stack.translate(t.points()[0],t.points()[1]+3*scale,1);stack.scale(scale,scale,1);
                        c.drawText(font,text,t.centered()?-font.getWidth(text)/2:0,0,t.top(),false);stack.pop();
                    }
                }
            }
            c.draw();
        }finally{stack.pop();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.disableBlend();}
    }
    private TitlePainter(){}
}
