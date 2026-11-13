package dev.sangwoo.celestialfall.client.gacha;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import org.joml.Matrix4f;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * The gacha screens' type (user request, 2.40.0: premium, not pixel text): Botand Gacha Sans, cut from Pretendard (SIL
 * Open Font License 1.1, tools/gacha/build_font.py). Each line is drawn by stb_truetype at the screen's own pixel size
 * into a texture of its own, filtered smoothly, with a blurred copy for glow and shadow; lines are kept until they have
 * gone unused for a while. Sizes are the font's em in GUI units; {@code y} is the middle of the capitals.
 *
 * <p>The HUD draws with it too (2.42.0): the cooldown list in Botand Gacha Sans and the danger banner in Botand Hud
 * Serif ({@link Weight#SERIF}, cut from Nanum Myeongjo ExtraBold, SIL Open Font License 1.1, tools/hud/build_font.py).
 */
public final class GachaFont {
    private GachaFont(){}
    public enum Weight {
        MEDIUM("gacha/font/gacha_medium.ttf"),BOLD("gacha/font/gacha_bold.ttf"),HEAVY("gacha/font/gacha_heavy.ttf"),
        /** The danger banner's serif: only printable ASCII and the banner's own Hangul (tools/hud/build_font.py). */
        SERIF("hud/font/hud_serif.ttf");
        final String file;Weight(String f){file=f;}
    }

    private static final class Face {
        final ByteBuffer data;final STBTTFontinfo info;final int ascent,descent;final float cap;
        Face(ByteBuffer data,STBTTFontinfo info,int ascent,int descent,float cap){this.data=data;this.info=info;this.ascent=ascent;this.descent=descent;this.cap=cap;}
    }
    private static final Face[] FACES=new Face[Weight.values().length];
    /** Faces that failed to load (each falls back to Minecraft's font on its own). */
    private static final boolean[] BROKEN=new boolean[Weight.values().length];

    private record Key(String text,Weight weight,int pixels,int blur){}
    /** A line's texture: its size in pixels, where its pen starts and its baseline lie in it, and how far the pen moved. */
    private static final class Line {
        int id;final int width,height;final float originX,baseline,advance;long used;
        Line(int id,int width,int height,float originX,float baseline,float advance){this.id=id;this.width=width;this.height=height;this.originX=originX;this.baseline=baseline;this.advance=advance;}
        void close(){if(id>0){TextureUtil.releaseTextureId(id);id=0;}}
    }
    private static final Map<Key,Line> LINES=new LinkedHashMap<>(64,.75f,true);
    private static int draws;

    private static Face face(Weight w){
        Face f=FACES[w.ordinal()];if(f!=null||BROKEN[w.ordinal()])return f;
        try(var in=GachaFont.class.getResourceAsStream("/assets/celestialfall/"+w.file)){
            if(in==null)throw new IOException("missing gacha font "+w.file);
            byte[] bytes=in.readAllBytes();ByteBuffer data=MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            STBTTFontinfo info=STBTTFontinfo.malloc();
            if(!stbtt_InitFont(info,data)){info.free();MemoryUtil.memFree(data);throw new IOException("bad gacha font "+w.file);}
            try(var s=MemoryStack.stackPush()){
                var a=s.mallocInt(1);var d=s.mallocInt(1);var g=s.mallocInt(1);stbtt_GetFontVMetrics(info,a,d,g);
                // the capitals' height: the top of an H
                var x0=s.mallocInt(1);var y0=s.mallocInt(1);var x1=s.mallocInt(1);var y1=s.mallocInt(1);
                float cap=.7f;
                if(stbtt_GetCodepointBox(info,'H',x0,y0,x1,y1))cap=y1.get(0)/(float)unitsPerEm(info);
                f=new Face(data,info,a.get(0),d.get(0),cap);
            }
            FACES[w.ordinal()]=f;return f;
        }catch(IOException|RuntimeException e){
            BROKEN[w.ordinal()]=true;org.slf4j.LoggerFactory.getLogger("Botand/GachaFont").warn("Font "+w.file+" unavailable; using Minecraft's",e);return null;
        }
    }
    private static int unitsPerEm(STBTTFontinfo info){
        // the 'head' table's unitsPerEm (offset 18), read through stb's own table lookup
        return Math.round(1/stbtt_ScaleForMappingEmToPixels(info,1));
    }
    private static float guiScale(){return (float)MinecraftClient.getInstance().getWindow().getScaleFactor();}

    /** Draws a line of text; the builder sets how. */
    public static Text text(String s,float x,float y,float size,Weight w){return new Text(s,x,y,size,w);}

    public static final class Text {
        final String s;final float x,y,size;final Weight w;
        int top=0xffffffff,bottom=0xffffffff,glow,shadow;float align,alpha=1,glowRadius,glowStrength,shadowBlur,shadowX,shadowY,spacing;
        Text(String s,float x,float y,float size,Weight w){this.s=s;this.x=x;this.y=y;this.size=size;this.w=w;}
        public Text color(int c){top=bottom=c;return this;}
        public Text gradient(int t,int b){top=t;bottom=b;return this;}
        /** 0 left, .5 centred, 1 right. */
        public Text align(float a){align=a;return this;}
        public Text alpha(float a){alpha=a;return this;}
        /** A soft glow {@code radius} GUI units wide, added as light. */
        public Text glow(int color,float radius,float strength){glow=color;glowRadius=radius;glowStrength=strength;return this;}
        public Text shadow(int color,float blur,float dx,float dy){shadow=color;shadowBlur=blur;shadowX=dx;shadowY=dy;return this;}
        /** Extra space between letters, in ems. */
        public Text spacing(float em){spacing=em;return this;}
        public float width(){return GachaFont.width(s,size,w,spacing);}
        public void draw(DrawContext d){GachaFont.draw(d,this);}
    }

    /** The width of {@code s} in GUI units. */
    public static float width(String s,float size,Weight w){return width(s,size,w,0);}
    public static float width(String s,float size,Weight w,float spacing){
        Face f=face(w);
        if(f==null)return GachaArt.width(s,size*.72f,w!=Weight.MEDIUM);
        float k=guiScale();Line l=line(s,w,Math.max(1,Math.round(size*k)),0,spacing);
        return l==null?0:l.advance/k;
    }

    private static void draw(DrawContext d,Text t){
        if(t.s.isEmpty()||t.alpha<=0.002f)return;
        Face f=face(t.w);
        if(f==null){
            // Minecraft's own font when stb_truetype cannot load the face
            float sz=t.size*.72f;float wd=GachaArt.width(t.s,sz,t.w!=Weight.MEDIUM);
            GachaArt.text(d,t.s,t.x-wd*t.align,t.y-sz*.4f,sz,GachaArt.alpha(t.top,(t.top>>>24)*t.alpha),false,t.w!=Weight.MEDIUM,t.shadow!=0);
            return;
        }
        float k=guiScale();int px=Math.max(1,Math.round(t.size*k));
        Line main=line(t.s,t.w,px,0,t.spacing);if(main==null)return;
        // the pen's start, so the capitals' middle sits on y; snapped to whole pixels so the letters stay crisp
        float capPx=f.cap*px;
        float penX=t.x*k-main.advance*t.align,baseY=t.y*k+capPx/2;
        float left=Math.round(penX-main.originX),top=Math.round(baseY-main.baseline);
        d.draw();
        Matrix4f m=d.getMatrices().peek().getPositionMatrix();
        if(t.shadow!=0){
            int blur=Math.max(1,Math.round(t.shadowBlur*k));Line s=line(t.s,t.w,px,blur,t.spacing);
            if(s!=null){float l=Math.round(penX-s.originX+t.shadowX*k),tp=Math.round(baseY-s.baseline+t.shadowY*k);int c=GachaArt.alpha(t.shadow,(t.shadow>>>24)*t.alpha);quad(m,s,l/k,tp/k,k,c,c,false);}
        }
        if(t.glow!=0&&t.glowStrength>0){
            int blur=Math.max(1,Math.round(t.glowRadius*k));Line g=line(t.s,t.w,px,blur,t.spacing);
            if(g!=null){float l=Math.round(penX-g.originX),tp=Math.round(baseY-g.baseline);int c=GachaArt.alpha(t.glow,Math.min(255,(t.glow>>>24)*t.alpha*t.glowStrength));quad(m,g,l/k,tp/k,k,c,c,true);
                if(t.glowStrength>1){c=GachaArt.alpha(t.glow,Math.min(255,(t.glow>>>24)*t.alpha*(t.glowStrength-1)));quad(m,g,l/k,tp/k,k,c,c,true);}}
        }
        // the letters, the colour shaded from the capitals' top to the baseline
        int c0=GachaArt.alpha(t.top,(t.top>>>24)*t.alpha),c1=GachaArt.alpha(t.bottom,(t.bottom>>>24)*t.alpha);
        if(t.top==t.bottom)quad(m,main,left/k,top/k,k,c0,c1,false);
        else{
            float capTop=main.baseline-capPx,base=main.baseline;
            strip(m,main,left/k,top/k,k,0,capTop,c0,c0);
            strip(m,main,left/k,top/k,k,capTop,base,c0,c1);
            strip(m,main,left/k,top/k,k,base,main.height,c1,c1);
        }
        if((++draws&255)==0)sweep();
    }
    private static void quad(Matrix4f m,Line l,float x,float y,float k,int top,int bottom,boolean additive){
        begin(l,additive);var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        float w=l.width/k,h=l.height/k;
        vertex(b,m,x,y,0,0,top);vertex(b,m,x,y+h,0,1,bottom);vertex(b,m,x+w,y+h,1,1,bottom);vertex(b,m,x+w,y,1,0,top);
        BufferRenderer.drawWithGlobalProgram(b.end());end();
    }
    /** Rows {@code from}..{@code to} (pixels) of a line, shaded from {@code a} to {@code b}. */
    private static void strip(Matrix4f m,Line l,float x,float y,float k,float from,float to,int a,int b){
        if(to<=from)return;
        begin(l,false);var buf=Tessellator.getInstance().getBuffer();buf.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        float w=l.width/k,v0=from/l.height,v1=to/l.height,y0=y+from/k,y1=y+to/k;
        vertex(buf,m,x,y0,0,v0,a);vertex(buf,m,x,y1,0,v1,b);vertex(buf,m,x+w,y1,1,v1,b);vertex(buf,m,x+w,y0,1,v0,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());end();
    }
    private static void begin(Line l,boolean additive){
        RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();
        if(additive)RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);else RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(()->GachaShaders.tex(additive));RenderSystem.setShaderTexture(0,l.id);RenderSystem.setShaderColor(1,1,1,1);
    }
    private static void end(){RenderSystem.defaultBlendFunc();RenderSystem.enableCull();}
    private static void vertex(BufferBuilder b,Matrix4f m,float x,float y,float u,float v,int argb){b.vertex(m,x,y,0).texture(u,v).color((argb>>16)&255,(argb>>8)&255,argb&255,argb>>>24).next();}

    /** The line's texture, made when first asked for. */
    private static Line line(String s,Weight w,int pixels,int blur,float spacing){
        var key=new Key(spacing==0?s:s+"\u0000"+spacing,w,pixels,blur);
        Line l=LINES.get(key);
        if(l==null){l=raster(s,face(w),pixels,blur,spacing);if(l==null)return null;LINES.put(key,l);}
        l.used=System.nanoTime();return l;
    }
    /** Frees lines unused for half a minute, and keeps the cache small (least recently used first). */
    private static void sweep(){
        long now=System.nanoTime();var it=LINES.values().iterator();
        while(it.hasNext()){Line l=it.next();if(now-l.used>30_000_000_000L||LINES.size()>320){l.close();it.remove();}}
    }
    static void clear(){for(Line l:LINES.values())l.close();LINES.clear();}

    private static Line raster(String s,Face f,int pixels,int blur,float spacing){
        if(f==null)return null;
        float scale=stbtt_ScaleForMappingEmToPixels(f.info,pixels);
        int[] cps=s.codePoints().toArray();int n=cps.length;
        int[] glyph=new int[n];float[] pen=new float[n];float x=0;int prev=0;
        try(var st=MemoryStack.stackPush()){
            var adv=st.mallocInt(1);var lsb=st.mallocInt(1);
            for(int i=0;i<n;i++){
                int g=stbtt_FindGlyphIndex(f.info,cps[i]);
                if(i>0)x+=stbtt_GetGlyphKernAdvance(f.info,prev,g)*scale+spacing*pixels;
                glyph[i]=g;pen[i]=x;stbtt_GetGlyphHMetrics(f.info,g,adv,lsb);x+=adv.get(0)*scale;prev=g;
            }
        }
        int pad=2+blur*3,baseline=pad+(int)Math.ceil(f.ascent*scale);
        // glyph boxes may reach left of the pen's start or past its end
        int minX=0,maxX=(int)Math.ceil(x);int[][] box=new int[n][4];
        try(var st=MemoryStack.stackPush()){
            var x0=st.mallocInt(1);var y0=st.mallocInt(1);var x1=st.mallocInt(1);var y1=st.mallocInt(1);
            for(int i=0;i<n;i++){
                float fx=pen[i]-(float)Math.floor(pen[i]);
                stbtt_GetGlyphBitmapBoxSubpixel(f.info,glyph[i],scale,scale,fx,0,x0,y0,x1,y1);
                box[i]=new int[]{x0.get(0),y0.get(0),x1.get(0),y1.get(0)};
                minX=Math.min(minX,(int)Math.floor(pen[i])+box[i][0]);maxX=Math.max(maxX,(int)Math.floor(pen[i])+box[i][2]);
            }
        }
        int width=maxX-minX+2*pad,height=baseline+(int)Math.ceil(-f.descent*scale)+pad;
        if(width<=0||height<=0||width>8192||height>2048)return null;
        float[] cover=new float[width*height];
        for(int i=0;i<n;i++){
            int gw=box[i][2]-box[i][0],gh=box[i][3]-box[i][1];if(gw<=0||gh<=0)continue;
            ByteBuffer tmp=MemoryUtil.memAlloc(gw*gh);
            try{
                float fx=pen[i]-(float)Math.floor(pen[i]);
                stbtt_MakeGlyphBitmapSubpixel(f.info,tmp,gw,gh,gw,scale,scale,fx,0,glyph[i]);
                int ox=pad-minX+(int)Math.floor(pen[i])+box[i][0],oy=baseline+box[i][1];
                for(int yy=0;yy<gh;yy++){int ty=oy+yy;if(ty<0||ty>=height)continue;
                    for(int xx=0;xx<gw;xx++){int tx=ox+xx;if(tx<0||tx>=width)continue;
                        float v=(tmp.get(yy*gw+xx)&255)/255f;int at=ty*width+tx;if(v>cover[at])cover[at]=v;}}
            }finally{MemoryUtil.memFree(tmp);}
        }
        if(blur>0)cover=blur(cover,width,height,blur);
        NativeImage image=new NativeImage(NativeImage.Format.RGBA,width,height,false);
        try{
            for(int yy=0;yy<height;yy++)for(int xx=0;xx<width;xx++){
                int a=Math.min(255,Math.round(cover[yy*width+xx]*255));
                image.setColor(xx,yy,(a<<24)|0xffffff);
            }
            int id=TextureUtil.generateTextureId();TextureUtil.prepareImage(id,width,height);
            image.upload(0,0,0,0,0,width,height,true,true,false,false);
            return new Line(id,width,height,pad-minX,baseline,x);
        }finally{image.close();}
    }
    /** Three box blurs, near enough a gaussian {@code r} pixels wide; brightened so the glow keeps its body. */
    private static float[] blur(float[] a,int w,int h,int r){
        float[] b=new float[a.length];
        for(int pass=0;pass<3;pass++){box(a,b,w,h,r,true);box(b,a,w,h,r,false);}
        for(int i=0;i<a.length;i++)a[i]=Math.min(1,a[i]*1.6f);
        return a;
    }
    private static void box(float[] src,float[] dst,int w,int h,int r,boolean horizontal){
        int n=horizontal?w:h,lines=horizontal?h:w;float inv=1f/(2*r+1);
        for(int l=0;l<lines;l++){
            float sum=0;
            for(int i=-r;i<=r;i++){int j=Math.max(0,Math.min(n-1,i));sum+=src[horizontal?l*w+j:j*w+l];}
            for(int i=0;i<n;i++){
                dst[horizontal?l*w+i:i*w+l]=sum*inv;
                int out=Math.max(0,Math.min(n-1,i-r)),in=Math.max(0,Math.min(n-1,i+r+1));
                sum+=src[horizontal?l*w+in:in*w+l]-src[horizontal?l*w+out:out*w+l];
            }
        }
    }
}
