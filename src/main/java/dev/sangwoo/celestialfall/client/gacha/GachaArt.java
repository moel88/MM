package dev.sangwoo.celestialfall.client.gacha;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Drawing for the gacha screen (2.39.0): the backdrop and logo lifted from the user's draft (tools/gacha/build_textures.py),
 * and code-drawn panels, neon outlines and glows in its style. Coordinates are GUI units.
 */
final class GachaArt {
    private GachaArt(){}
    static final Identifier BACKGROUND=new Identifier("celestialfall","textures/gui/gacha/background.png"),
        LOGO=new Identifier("celestialfall","textures/gui/gacha/logo.png"),
        KAGENO=new Identifier("celestialfall","textures/gui/gacha/kageno.png");
    static final int BG_W=1536,BG_H=1024,LOGO_W=286,LOGO_H=85,KAGENO_W=1280,KAGENO_H=548;

    /** A texture region drawn over {@code (x, y, w, h)}; additive adds its light to what is under it (the logo). */
    static void texture(DrawContext d,Identifier id,float x,float y,float w,float h,float u,float v,float uw,float vh,int tw,int th,float alpha,boolean additive){
        d.draw();
        RenderSystem.enableBlend();
        if(additive)RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);else RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1,1,1,alpha);RenderSystem.setShaderTexture(0,id);RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        Matrix4f m=d.getMatrices().peek().getPositionMatrix();var b=Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE);
        float u0=u/tw,u1=(u+uw)/tw,v0=v/th,v1=(v+vh)/th;
        b.vertex(m,x,y,0).texture(u0,v0).next();b.vertex(m,x,y+h,0).texture(u0,v1).next();
        b.vertex(m,x+w,y+h,0).texture(u1,v1).next();b.vertex(m,x+w,y,0).texture(u1,v0).next();
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();
    }

    /** Starts a batch of coloured triangles; {@code additive} for light. */
    static BufferBuilder begin(DrawContext d,boolean additive){
        d.draw();RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();
        if(additive)RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);else RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR);return b;
    }
    static void end(BufferBuilder b){BufferRenderer.drawWithGlobalProgram(b.end());RenderSystem.defaultBlendFunc();RenderSystem.enableCull();}
    static void vertex(BufferBuilder b,Matrix4f m,float x,float y,int argb){b.vertex(m,x,y,0).color((argb>>16)&255,(argb>>8)&255,argb&255,argb>>>24).next();}
    /** A convex polygon shaded from {@code top} to {@code bottom}. */
    static void fill(BufferBuilder b,Matrix4f m,float[] xy,int top,int bottom){
        float lo=Float.MAX_VALUE,hi=-Float.MAX_VALUE;for(int i=1;i<xy.length;i+=2){lo=Math.min(lo,xy[i]);hi=Math.max(hi,xy[i]);}
        for(int i=1;i<xy.length/2-1;i++)for(int j:new int[]{0,i,i+1}){float t=(xy[j*2+1]-lo)/Math.max(1e-3f,hi-lo);vertex(b,m,xy[j*2],xy[j*2+1],mix(top,bottom,t));}
    }
    static void fill(BufferBuilder b,Matrix4f m,float[] xy,int color){fill(b,m,xy,color,color);}
    /**
     * A band along a convex outline (clockwise on screen) from {@code in} at the outline to {@code out} {@code width}
     * outward (negative: inward). Neon edges and soft glows are both this.
     */
    static void band(BufferBuilder b,Matrix4f m,float[] xy,float width,int in,int out){
        int n=xy.length/2;float[] o=offset(xy,width);
        for(int i=0;i<n;i++){int j=(i+1)%n;
            float ax=xy[i*2],ay=xy[i*2+1],bx=xy[j*2],by=xy[j*2+1],cx=o[j*2],cy=o[j*2+1],dx=o[i*2],dy=o[i*2+1];
            vertex(b,m,ax,ay,in);vertex(b,m,bx,by,in);vertex(b,m,cx,cy,out);
            vertex(b,m,ax,ay,in);vertex(b,m,cx,cy,out);vertex(b,m,dx,dy,out);
        }
    }
    /** The outline moved {@code d} outward (clockwise on screen, y down), corners mitred. */
    static float[] offset(float[] xy,float d){
        int n=xy.length/2;float[] o=new float[xy.length];
        for(int i=0;i<n;i++){
            int p=(i+n-1)%n,q=(i+1)%n;
            float ex=xy[i*2]-xy[p*2],ey=xy[i*2+1]-xy[p*2+1],fx=xy[q*2]-xy[i*2],fy=xy[q*2+1]-xy[i*2+1];
            float el=(float)Math.hypot(ex,ey),fl=(float)Math.hypot(fx,fy);
            // outward normals of a clockwise (on screen) outline
            float n1x=ey/el,n1y=-ex/el,n2x=fy/fl,n2y=-fx/fl;
            float mx=n1x+n2x,my=n1y+n2y,ml=(float)Math.hypot(mx,my);mx/=ml;my/=ml;
            float k=d/Math.max(.35f,mx*n1x+my*n1y);
            o[i*2]=xy[i*2]+mx*k;o[i*2+1]=xy[i*2+1]+my*k;
        }
        return o;
    }
    /** A soft glow around an outline, added as light: strongest at the outline, gone {@code reach} out. */
    static void glow(BufferBuilder b,Matrix4f m,float[] xy,float reach,int color,float strength){
        // reach fractions and the share of the colour's alpha there: a quick falloff with a long faint tail
        float[] at={0,.1f,.32f,1},share={.6f,.3f,.1f,0};float a=(color>>>24)*strength;
        for(int i=0;i<3;i++)band(b,m,offset(xy,at[i]*reach),(at[i+1]-at[i])*reach,alpha(color,a*share[i]),alpha(color,a*share[i+1]));
    }
    static int alpha(int rgb,float a){return (Math.max(0,Math.min(255,Math.round(a)))<<24)|(rgb&0xffffff);}
    static int mix(int a,int b,float t){t=Math.max(0,Math.min(1,t));int o=0;for(int s=0;s<32;s+=8){int x=(a>>>s)&255,y=(b>>>s)&255;o|=Math.round(x+(y-x)*t)<<s;}return o;}

    /** Text {@code size} GUI units tall (the font's cap height is 7 of its 9 rows), left-aligned or centred on {@code x}. */
    static void text(DrawContext d,String s,float x,float y,float size,int color,boolean centered,boolean bold,boolean shadow){
        var font=MinecraftClient.getInstance().textRenderer;Text t=bold?Text.literal(s).formatted(Formatting.BOLD):Text.literal(s);
        float k=size/9f;var ms=d.getMatrices();ms.push();ms.translate(x,y,0);ms.scale(k,k,1);
        d.drawText(font,t,centered?-font.getWidth(t)/2:0,0,color,shadow);ms.pop();
    }
    static float width(String s,float size,boolean bold){var font=MinecraftClient.getInstance().textRenderer;return font.getWidth(bold?Text.literal(s).formatted(Formatting.BOLD):Text.literal(s))*size/9f;}
}
