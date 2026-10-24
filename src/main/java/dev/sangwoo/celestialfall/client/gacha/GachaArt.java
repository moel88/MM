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
import java.util.ArrayList;
import java.util.List;

/**
 * Drawing for the gacha screens: the banner's layers lifted from the user's second mockup (2.40.0,
 * tools/gacha/build_banner.py), particle sprites, and code-drawn panels, neon outlines and glows in its style.
 * Coordinates are GUI units; the banner's own are its pixels (1671 x 941). The pause menu and the player list draw with
 * its shapes too (2.45.0, client/ui/SkyUi).
 */
public final class GachaArt {
    private GachaArt(){}
    private static Identifier id(String name){return new Identifier("celestialfall","textures/gui/gacha/"+name+".png");}
    static final Identifier BANNER=id("banner"),TITLE=id("title"),TITLE_CORE=id("title_core"),TITLE_SUB=id("title_sub"),LOGO=id("logo"),EYES=id("eyes"),
        SPARK=id("spark"),GLINT=id("glint");
    static final int BANNER_W=1671,BANNER_H=941;
    /** Each lifted layer's place in the banner (x, y) and size (w, h), in banner pixels. */
    static final int[] TITLE_AT={0,288,694,350},SUB_AT={142,540,488,62},LOGO_AT={22,6,312,97},EYES_AT={682,195,911,208};
    /** His face in the banner, kept on screen when it is narrower than the art; the SSR splash's glory shines from behind his hood. */
    static final float FACE_X=1180,FACE_Y=330,GLORY_X=1290,GLORY_Y=150;

    /** A texture region drawn over {@code (x, y, w, h)}, tinted; additive adds its light to what is under it. */
    static void texture(DrawContext d,Identifier id,float x,float y,float w,float h,float u0,float v0,float u1,float v1,int tint,boolean additive){
        d.draw();
        RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();
        if(additive)RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);else RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShaderTexture(0,id);RenderSystem.setShader(()->GachaShaders.tex(additive));
        Matrix4f m=d.getMatrices().peek().getPositionMatrix();var b=Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        tex(b,m,x,y,u0,v0,tint);tex(b,m,x,y+h,u0,v1,tint);tex(b,m,x+w,y+h,u1,v1,tint);tex(b,m,x+w,y,u1,v0,tint);
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.defaultBlendFunc();RenderSystem.enableCull();
    }
    static void texture(DrawContext d,Identifier id,float x,float y,float w,float h,float alpha,boolean additive){texture(d,id,x,y,w,h,0,0,1,1,alpha(0xffffff,255*alpha),additive);}
    static void tex(BufferBuilder b,Matrix4f m,float x,float y,float u,float v,int argb){b.vertex(m,x,y,0).texture(u,v).color((argb>>16)&255,(argb>>8)&255,argb&255,argb>>>24).next();}

    /** Starts a batch of textured, coloured quads ({@link #sprite}) from one texture; {@code additive} for light. */
    static BufferBuilder sprites(DrawContext d,Identifier id,boolean additive){
        d.draw();RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();
        if(additive)RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);else RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShaderTexture(0,id);RenderSystem.setShader(()->GachaShaders.tex(additive));
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);return b;
    }
    /** A sprite centred on {@code (x, y)}, {@code w} by {@code h}, turned by {@code angle} radians. */
    static void sprite(BufferBuilder b,Matrix4f m,float x,float y,float w,float h,float angle,int argb){
        float c=(float)Math.cos(angle),s=(float)Math.sin(angle),hw=w/2,hh=h/2;
        float ax=c*hw,ay=s*hw,bx=-s*hh,by=c*hh;
        tex(b,m,x-ax-bx,y-ay-by,0,0,argb);tex(b,m,x-ax+bx,y-ay+by,0,1,argb);tex(b,m,x+ax+bx,y+ay+by,1,1,argb);tex(b,m,x+ax-bx,y+ay-by,1,0,argb);
    }
    static void endSprites(BufferBuilder b){BufferRenderer.drawWithGlobalProgram(b.end());RenderSystem.defaultBlendFunc();RenderSystem.enableCull();}

    /** Starts a batch of coloured triangles; {@code additive} for light. */
    public static BufferBuilder begin(DrawContext d,boolean additive){
        d.draw();RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();
        if(additive)RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);else RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShader(()->GachaShaders.color(additive));
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR);return b;
    }
    public static void end(BufferBuilder b){BufferRenderer.drawWithGlobalProgram(b.end());RenderSystem.defaultBlendFunc();RenderSystem.enableCull();}
    public static void vertex(BufferBuilder b,Matrix4f m,float x,float y,int argb){b.vertex(m,x,y,0).color((argb>>16)&255,(argb>>8)&255,argb&255,argb>>>24).next();}
    /** A convex polygon shaded from {@code top} to {@code bottom}. */
    public static void fill(BufferBuilder b,Matrix4f m,float[] xy,int top,int bottom){
        float lo=Float.MAX_VALUE,hi=-Float.MAX_VALUE;for(int i=1;i<xy.length;i+=2){lo=Math.min(lo,xy[i]);hi=Math.max(hi,xy[i]);}
        for(int i=1;i<xy.length/2-1;i++)for(int j:new int[]{0,i,i+1}){float t=(xy[j*2+1]-lo)/Math.max(1e-3f,hi-lo);vertex(b,m,xy[j*2],xy[j*2+1],mix(top,bottom,t));}
    }
    public static void fill(BufferBuilder b,Matrix4f m,float[] xy,int color){fill(b,m,xy,color,color);}
    /** A convex polygon shaded across, from {@code left} to {@code right}. */
    public static void fillAcross(BufferBuilder b,Matrix4f m,float[] xy,int left,int right){
        float lo=Float.MAX_VALUE,hi=-Float.MAX_VALUE;for(int i=0;i<xy.length;i+=2){lo=Math.min(lo,xy[i]);hi=Math.max(hi,xy[i]);}
        for(int i=1;i<xy.length/2-1;i++)for(int j:new int[]{0,i,i+1}){float t=(xy[j*2]-lo)/Math.max(1e-3f,hi-lo);vertex(b,m,xy[j*2],xy[j*2+1],mix(left,right,t));}
    }
    /** A filled polygon with its edge feathered over one screen pixel, so slanted edges are not stair-stepped. */
    public static void smooth(BufferBuilder b,Matrix4f m,float[] xy,int top,int bottom){
        fill(b,m,xy,top,bottom);
        float px=1/(float)MinecraftClient.getInstance().getWindow().getScaleFactor();
        float lo=Float.MAX_VALUE,hi=-Float.MAX_VALUE;for(int i=1;i<xy.length;i+=2){lo=Math.min(lo,xy[i]);hi=Math.max(hi,xy[i]);}
        int n=xy.length/2;float[] o=offset(xy,px);
        for(int i=0;i<n;i++){int j=(i+1)%n;
            int ci=mix(top,bottom,(xy[i*2+1]-lo)/Math.max(1e-3f,hi-lo)),cj=mix(top,bottom,(xy[j*2+1]-lo)/Math.max(1e-3f,hi-lo));
            vertex(b,m,xy[i*2],xy[i*2+1],ci);vertex(b,m,xy[j*2],xy[j*2+1],cj);vertex(b,m,o[j*2],o[j*2+1],alpha(cj,0));
            vertex(b,m,xy[i*2],xy[i*2+1],ci);vertex(b,m,o[j*2],o[j*2+1],alpha(cj,0));vertex(b,m,o[i*2],o[i*2+1],alpha(ci,0));
        }
    }
    /**
     * A band along a convex outline (clockwise on screen) from {@code in} at the outline to {@code out} {@code width}
     * outward (negative: inward). Neon edges and soft glows are both this.
     */
    public static void band(BufferBuilder b,Matrix4f m,float[] xy,float width,int in,int out){
        int n=xy.length/2;float[] o=offset(xy,width);
        for(int i=0;i<n;i++){int j=(i+1)%n;
            float ax=xy[i*2],ay=xy[i*2+1],bx=xy[j*2],by=xy[j*2+1],cx=o[j*2],cy=o[j*2+1],dx=o[i*2],dy=o[i*2+1];
            vertex(b,m,ax,ay,in);vertex(b,m,bx,by,in);vertex(b,m,cx,cy,out);
            vertex(b,m,ax,ay,in);vertex(b,m,cx,cy,out);vertex(b,m,dx,dy,out);
        }
    }
    /** A crisp line along an outline, {@code width} wide and centred on it, its edges feathered a pixel each side. */
    public static void stroke(BufferBuilder b,Matrix4f m,float[] xy,float width,int color){
        float px=1/(float)MinecraftClient.getInstance().getWindow().getScaleFactor();float h=Math.max(px*.5f,width/2);
        float[] in=offset(xy,-h),out=offset(xy,h);
        band(b,m,in,2*h,color,color);band(b,m,out,px,color,alpha(color,0));band(b,m,in,-px,color,alpha(color,0));
    }
    /** The outline moved {@code d} outward (clockwise on screen, y down), corners mitred. */
    public static float[] offset(float[] xy,float d){
        int n=xy.length/2;float[] o=new float[xy.length];
        for(int i=0;i<n;i++){
            int p=(i+n-1)%n,q=(i+1)%n;
            float ex=xy[i*2]-xy[p*2],ey=xy[i*2+1]-xy[p*2+1],fx=xy[q*2]-xy[i*2],fy=xy[q*2+1]-xy[i*2+1];
            float el=(float)Math.hypot(ex,ey),fl=(float)Math.hypot(fx,fy);
            if(el<1e-5f||fl<1e-5f){o[i*2]=xy[i*2];o[i*2+1]=xy[i*2+1];continue;}
            // outward normals of a clockwise (on screen) outline
            float n1x=ey/el,n1y=-ex/el,n2x=fy/fl,n2y=-fx/fl;
            float mx=n1x+n2x,my=n1y+n2y,ml=(float)Math.hypot(mx,my);if(ml<1e-5f){mx=n1x;my=n1y;ml=1;}mx/=ml;my/=ml;
            float k=d/Math.max(.35f,mx*n1x+my*n1y);
            o[i*2]=xy[i*2]+mx*k;o[i*2+1]=xy[i*2+1]+my*k;
        }
        return o;
    }
    /** A soft glow around an outline, added as light: strongest at the outline, gone {@code reach} out. */
    public static void glow(BufferBuilder b,Matrix4f m,float[] xy,float reach,int color,float strength){
        // reach fractions and the share of the colour's alpha there: a quick falloff with a long faint tail
        float[] at={0,.1f,.32f,1},share={.6f,.3f,.1f,0};float a=(color>>>24)*strength;
        for(int i=0;i<3;i++)band(b,m,offset(xy,at[i]*reach),(at[i+1]-at[i])*reach,alpha(color,a*share[i]),alpha(color,a*share[i+1]));
    }
    /** {@code poly} cut to the inside of the convex, clockwise {@code clip} (Sutherland-Hodgman); empty when nothing is left. */
    static float[] clip(float[] poly,float[] clip){
        List<float[]> out=new ArrayList<>();for(int i=0;i<poly.length;i+=2)out.add(new float[]{poly[i],poly[i+1]});
        int n=clip.length/2;
        for(int e=0;e<n&&!out.isEmpty();e++){
            float ax=clip[e*2],ay=clip[e*2+1],bx=clip[(e+1)%n*2],by=clip[(e+1)%n*2+1];
            List<float[]> in=out;out=new ArrayList<>();
            for(int i=0;i<in.size();i++){
                float[] p=in.get(i),q=in.get((i+1)%in.size());
                float sp=side(ax,ay,bx,by,p),sq=side(ax,ay,bx,by,q);
                if(sp>=0)out.add(p);
                if((sp>=0)!=(sq>=0)){float t=sp/(sp-sq);out.add(new float[]{p[0]+(q[0]-p[0])*t,p[1]+(q[1]-p[1])*t});}
            }
        }
        float[] r=new float[out.size()*2];for(int i=0;i<out.size();i++){r[i*2]=out.get(i)[0];r[i*2+1]=out.get(i)[1];}return r;
    }
    /** Positive inside a clockwise (on screen, y down) outline's edge a-b. */
    private static float side(float ax,float ay,float bx,float by,float[] p){return (bx-ax)*(p[1]-ay)-(by-ay)*(p[0]-ax);}
    /** A regular polygon (a circle when {@code n} is large), clockwise on screen. */
    public static float[] circle(float x,float y,float r,int n){float[] p=new float[n*2];for(int i=0;i<n;i++){double a=i*Math.PI*2/n;p[i*2]=x+(float)Math.cos(a)*r;p[i*2+1]=y+(float)Math.sin(a)*r;}return p;}
    /** A star of {@code points} points, outer radius {@code r}, inner {@code inner}, one point up. */
    static float[] star(float x,float y,float r,float inner,int points){
        float[] p=new float[points*4];
        for(int i=0;i<points*2;i++){double a=-Math.PI/2+i*Math.PI/points;float rr=i%2==0?r:inner;p[i*2]=x+(float)Math.cos(a)*rr;p[i*2+1]=y+(float)Math.sin(a)*rr;}
        return p;
    }
    /** A filled star (not convex: drawn as a fan from its centre). */
    static void fillStar(BufferBuilder b,Matrix4f m,float x,float y,float r,float inner,int points,int center,int tip){
        float[] p=star(x,y,r,inner,points);int n=p.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;vertex(b,m,x,y,center);vertex(b,m,p[i*2],p[i*2+1],i%2==0?tip:mix(center,tip,.5f));vertex(b,m,p[j*2],p[j*2+1],j%2==0?tip:mix(center,tip,.5f));}
    }
    /** A slanted panel (a parallelogram with clipped corners): x, y its top-left, w, h its size. Clockwise on screen. */
    static float[] slab(float x,float y,float w,float h,float slant,float clip){
        return new float[]{x+slant,y, x+w-clip,y, x+w,y+clip, x+w-slant,y+h, x+clip,y+h, x,y+h-clip};
    }
    public static float[] rect(float x,float y,float w,float h){return new float[]{x,y,x+w,y,x+w,y+h,x,y+h};}
    public static boolean inside(float[] p,double x,double y){
        boolean in=false;for(int i=0,j=p.length/2-1;i<p.length/2;j=i++){
            double xi=p[i*2],yi=p[i*2+1],xj=p[j*2],yj=p[j*2+1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)in=!in;
        }return in;
    }
    /** A line segment {@code w} wide. */
    public static void line(BufferBuilder b,Matrix4f m,float x1,float y1,float x2,float y2,float w,int color){line(b,m,x1,y1,x2,y2,w,color,color);}
    public static void line(BufferBuilder b,Matrix4f m,float x1,float y1,float x2,float y2,float w,int c1,int c2){
        float len=(float)Math.hypot(x2-x1,y2-y1);if(len==0)return;float dx=-(y2-y1)*w/len/2,dy=(x2-x1)*w/len/2;
        vertex(b,m,x1+dx,y1+dy,c1);vertex(b,m,x2+dx,y2+dy,c2);vertex(b,m,x2-dx,y2-dy,c2);
        vertex(b,m,x1+dx,y1+dy,c1);vertex(b,m,x2-dx,y2-dy,c2);vertex(b,m,x1-dx,y1-dy,c1);
    }
    public static int alpha(int rgb,float a){return (Math.max(0,Math.min(255,Math.round(a)))<<24)|(rgb&0xffffff);}
    public static int mix(int a,int b,float t){t=Math.max(0,Math.min(1,t));int o=0;for(int s=0;s<32;s+=8){int x=(a>>>s)&255,y=(b>>>s)&255;o|=Math.round(x+(y-x)*t)<<s;}return o;}
    /** The colour with its alpha scaled by {@code k}. */
    public static int fade(int argb,float k){return alpha(argb,(argb>>>24)*Math.max(0,Math.min(1,k)));}

    /** Minecraft's own font (the fallback when the gacha's cannot load): {@code size} GUI units tall. */
    static void text(DrawContext d,String s,float x,float y,float size,int color,boolean centered,boolean bold,boolean shadow){
        var font=MinecraftClient.getInstance().textRenderer;Text t=bold?Text.literal(s).formatted(Formatting.BOLD):Text.literal(s);
        float k=size/9f;var ms=d.getMatrices();ms.push();ms.translate(x,y,0);ms.scale(k,k,1);
        d.drawText(font,t,centered?-font.getWidth(t)/2:0,0,color,shadow);ms.pop();
    }
    static float width(String s,float size,boolean bold){var font=MinecraftClient.getInstance().textRenderer;return font.getWidth(bold?Text.literal(s).formatted(Formatting.BOLD):Text.literal(s))*size/9f;}

    public static float ease(float t){t=Math.max(0,Math.min(1,t));return 1-(1-t)*(1-t)*(1-t);}
    /** Overshoots a little and settles: for things that pop in. */
    static float back(float t){t=Math.max(0,Math.min(1,t));float c=1.9f;return 1+(c+1)*(float)Math.pow(t-1,3)+c*(float)Math.pow(t-1,2);}
    public static float clamp01(float t){return Math.max(0,Math.min(1,t));}
    /** A smooth 0..1 ramp from {@code a} to {@code b}. */
    public static float ramp(float a,float b,float x){float t=clamp01((x-a)/(b-a));return t*t*(3-2*t);}
    /** A repeatable random number in [0, 1) for {@code i}. */
    public static float hash(int i){int h=i*0x27d4eb2d;h^=h>>>15;h*=0x85ebca6b;h^=h>>>13;return (h>>>8)/(float)(1<<24);}
}
