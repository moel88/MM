package dev.sangwoo.celestialfall.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.gacha.GachaArt;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

/**
 * The sky-blue interface of the pause menu and the player list (2.45.0, user request: the player list of the user's
 * Roblox screenshot, a header with an emblem over rows of names in framed boxes, recoloured from pink to sky blue, and
 * a pause menu to match). Deep navy glass with a sky-blue frame and a fainter line just inside it, a soft light when
 * lit, labels in Botand Gacha Sans. Shapes are {@link GachaArt}'s; sizes are GUI units.
 */
public final class SkyUi {
    private SkyUi(){}
    /** Deep navy glass, and lighter when lit. */
    public static final int FILL_TOP=0xea173252,FILL_BOTTOM=0xea0c1c32,LIT_TOP=0xf22a5a8c,LIT_BOTTOM=0xf2173a60;
    /** The frame, the frame lit, the faint line inside it. */
    public static final int EDGE=0xff8fdcff,EDGE_LIT=0xffe6f8ff,EDGE_INNER=0x5c8fdcff;
    /** Labels, labels lit, quiet text, the accent (the cooldown bars' sky blue) and the light added round lit things. */
    public static final int INK=0xffcfefff,INK_LIT=0xffffffff,INK_DIM=0xff89b8d6,ACCENT=0xff74d8ff,GLOW=0xff3fb8ff;

    /** A rounded rectangle's outline, clockwise on screen (the way {@link GachaArt}'s outline tools take it). */
    public static float[] rounded(float x,float y,float w,float h,float r){
        r=Math.max(0,Math.min(r,Math.min(w,h)/2));
        if(r<.05f)return GachaArt.rect(x,y,w,h);
        int seg=Math.max(2,Math.min(6,Math.round(r*1.4f)));
        float[] p=new float[(seg+1)*8];int k=0;
        // the corners in turn: top right, bottom right, bottom left, top left
        float[][] corners={{x+w-r,y+r,-90},{x+w-r,y+h-r,0},{x+r,y+h-r,90},{x+r,y+r,180}};
        for(float[] c:corners)for(int i=0;i<=seg;i++){
            double a=Math.toRadians(c[2]+90.0*i/seg);p[k++]=(float)(c[0]+Math.cos(a)*r);p[k++]=(float)(c[1]+Math.sin(a)*r);
        }
        return p;
    }
    /** One screen pixel in GUI units. */
    public static float px(){return 1/(float)MinecraftClient.getInstance().getWindow().getScaleFactor();}

    /**
     * A panel: the glass shaded down its height, the frame, and the fainter line inside it (the reference's double
     * line). {@code lit} (0..1) brightens it all (hover, focus, the player's own row).
     */
    public static void panel(BufferBuilder b,Matrix4f m,float x,float y,float w,float h,float r,float lit,float alpha){
        float[] o=rounded(x,y,w,h,r);
        GachaArt.smooth(b,m,o,GachaArt.fade(GachaArt.mix(FILL_TOP,LIT_TOP,lit),alpha),GachaArt.fade(GachaArt.mix(FILL_BOTTOM,LIT_BOTTOM,lit),alpha));
        GachaArt.stroke(b,m,o,Math.max(px(),.95f),GachaArt.fade(GachaArt.mix(EDGE,EDGE_LIT,lit),alpha));
        float in=Math.min(2.2f,Math.min(w,h)*.2f);
        GachaArt.stroke(b,m,rounded(x+in,y+in,w-2*in,h-2*in,Math.max(0,r-in*.6f)),Math.max(px()*.8f,.45f),GachaArt.fade(EDGE_INNER,alpha*(1-.4f*lit)));
    }
    /** The light round a lit panel, for an additive batch. */
    public static void halo(BufferBuilder b,Matrix4f m,float x,float y,float w,float h,float r,float reach,float strength){
        if(strength<=.002f)return;
        GachaArt.glow(b,m,rounded(x,y,w,h,r),reach,GLOW,strength);
    }
    /**
     * A header box (the reference's): the panel, the emblem faint and larger than the box, cut to it and turning slowly,
     * now and then a light running across it, and the title in the middle. {@code now} is in ticks. The cut is in screen
     * coordinates, so the matrix must not be moved or scaled here.
     */
    public static void header(DrawContext d,float x,float y,float w,float hh,float r,float a,double now,String title,float size){
        var m=d.getMatrices().peek().getPositionMatrix();
        var b=GachaArt.begin(d,false);panel(b,m,x,y,w,hh,r,0,a);GachaArt.end(b);
        clip(x+1,y+1,w-2,hh-2);
        b=GachaArt.begin(d,false);emblem(b,m,x+hh*.95f,y+hh*.55f,hh*.92f,GachaArt.fade(0x6f8fdcff,a),(float)(now*.006));GachaArt.end(b);
        double cycle=(now%140)/140;
        if(cycle<.22){
            float s=(float)(cycle/.22),bx=x-hh+(w+2*hh)*s;
            b=GachaArt.begin(d,true);
            GachaArt.fill(b,m,new float[]{bx,y,bx+hh*.45f,y,bx+hh*.05f,y+hh,bx-hh*.4f,y+hh},GachaArt.fade(0x2fbfefff,a*(float)Math.sin(Math.PI*s)));
            GachaArt.end(b);
        }
        unclip();
        GachaFont.text(title,x+w/2,y+hh/2,size,GachaFont.Weight.BOLD).align(.5f)
            .gradient(0xffeaf9ff,0xffa8e4ff).alpha(a).glow(GLOW,size*.45f,.55f).shadow(0x80000000,.6f,0,size*.06f).draw(d);
    }
    /** A small key cap ("Esc", "R", "Enter") right-aligned at {@code right}, centred on {@code cy}; returns its left edge. */
    public static float keyCap(DrawContext d,String key,float right,float cy,float size,float alpha){
        float tw=GachaFont.width(key,size,GachaFont.Weight.BOLD),w=tw+size*1.1f,h=size*1.55f,x=right-w,y=cy-h/2;
        var b=GachaArt.begin(d,false);var m=d.getMatrices().peek().getPositionMatrix();
        float[] o=rounded(x,y,w,h,h*.28f);
        GachaArt.smooth(b,m,o,GachaArt.fade(0x5a0b1b30,alpha),GachaArt.fade(0x5a0b1b30,alpha));
        GachaArt.stroke(b,m,o,Math.max(px(),.7f),GachaArt.fade(0xa88fdcff,alpha));
        GachaArt.end(b);
        GachaFont.text(key,x+w/2,cy,size,GachaFont.Weight.BOLD).align(.5f).color(INK).alpha(alpha).draw(d);
        return x;
    }

    /**
     * The emblem behind a header's title (the reference's faint crest, here a sky-blue sun): a ring, eight spokes with
     * knobs turning slowly with {@code spin} (radians), and two long strokes reaching out across the header.
     */
    public static void emblem(BufferBuilder b,Matrix4f m,float cx,float cy,float r,int color,float spin){
        float t=r*.085f;
        GachaArt.stroke(b,m,GachaArt.circle(cx,cy,r*.3f,28),t*1.1f,color);
        GachaArt.smooth(b,m,GachaArt.circle(cx,cy,r*.13f,18),color,color);
        for(int i=0;i<8;i++){
            double a=spin+i*Math.PI/4;float c=(float)Math.cos(a),s=(float)Math.sin(a);
            GachaArt.line(b,m,cx+c*r*.42f,cy+s*r*.42f,cx+c*r*.74f,cy+s*r*.74f,t,color);
            GachaArt.smooth(b,m,GachaArt.circle(cx+c*r*.8f,cy+s*r*.8f,r*.075f,12),color,color);
        }
        for(int k=-1;k<=1;k+=2){
            float a=(float)Math.toRadians(-14*k),c=(float)Math.cos(a),s=(float)Math.sin(a);
            GachaArt.line(b,m,cx+c*r*.95f,cy+s*r*.95f,cx+c*r*2.6f,cy+s*r*2.6f+k*r*.35f,t*.8f,color,GachaArt.fade(color,0));
        }
    }

    /** The pause menu's icons. */
    public enum Icon{RESUME,RESET,SETTINGS,LEAVE}
    /** An icon {@code s} across, centred on ({@code cx},{@code cy}). */
    public static void icon(BufferBuilder b,Matrix4f m,Icon icon,float cx,float cy,float s,int color){
        float t=Math.max(px(),s*.1f);
        switch(icon){
            case RESUME->GachaArt.smooth(b,m,new float[]{cx-s*.26f,cy-s*.36f,cx+s*.38f,cy,cx-s*.26f,cy+s*.36f},color,color);
            case RESET->{
                // an arc most of the way round and its arrow head, turning clockwise
                int n=18;float r=s*.33f,from=(float)Math.toRadians(-60),to=(float)Math.toRadians(230);
                for(int i=0;i<n;i++){
                    double a0=from+(to-from)*i/n,a1=from+(to-from)*(i+1)/n;
                    GachaArt.line(b,m,cx+(float)Math.cos(a0)*r,cy+(float)Math.sin(a0)*r,cx+(float)Math.cos(a1)*r,cy+(float)Math.sin(a1)*r,t,color);
                }
                float hx=cx+(float)Math.cos(from)*r,hy=cy+(float)Math.sin(from)*r,ta=from-(float)Math.PI/2;
                float dx=(float)Math.cos(ta),dy=(float)Math.sin(ta),nx=-dy,ny=dx,hs=s*.2f;
                GachaArt.smooth(b,m,new float[]{hx+dx*hs*1.1f,hy+dy*hs*1.1f,hx+nx*hs*.9f-dx*hs*.2f,hy+ny*hs*.9f-dy*hs*.2f,hx-nx*hs*.9f-dx*hs*.2f,hy-ny*hs*.9f-dy*hs*.2f},color,color);
            }
            case SETTINGS->{
                // a cog: eight teeth round a ring
                for(int i=0;i<8;i++){
                    double a=i*Math.PI/4;float c=(float)Math.cos(a),sn=(float)Math.sin(a);
                    GachaArt.line(b,m,cx+c*s*.24f,cy+sn*s*.24f,cx+c*s*.42f,cy+sn*s*.42f,s*.15f,color);
                }
                GachaArt.stroke(b,m,GachaArt.circle(cx,cy,s*.24f,24),s*.13f,color);
                GachaArt.smooth(b,m,GachaArt.circle(cx,cy,s*.07f,12),color,color);
            }
            case LEAVE->{
                // a door frame open on its right, and an arrow going out through it
                float l=cx-s*.36f,r=cx+s*.06f,top=cy-s*.38f,bot=cy+s*.38f;
                GachaArt.line(b,m,r,top,l,top,t,color);GachaArt.line(b,m,l,top-t/2,l,bot+t/2,t,color);GachaArt.line(b,m,l,bot,r,bot,t,color);
                GachaArt.line(b,m,cx-s*.14f,cy,cx+s*.3f,cy,t,color);
                GachaArt.smooth(b,m,new float[]{cx+s*.44f,cy,cx+s*.24f,cy+s*.16f,cx+s*.24f,cy-s*.16f},color,color);
            }
        }
    }

    /** An ellipse's outline, clockwise on screen. */
    public static float[] ellipse(float cx,float cy,float rx,float ry,int n){
        float[] p=new float[n*2];for(int i=0;i<n;i++){double a=i*Math.PI*2/n;p[i*2]=cx+(float)Math.cos(a)*rx;p[i*2+1]=cy+(float)Math.sin(a)*ry;}return p;
    }
    /** Clips what follows to a GUI rectangle (whole screen pixels, the way the scissor wants them). */
    public static void clip(float x,float y,float w,float h){
        var window=MinecraftClient.getInstance().getWindow();double k=window.getScaleFactor();
        int x0=(int)Math.floor(x*k),y0=(int)Math.floor(y*k),x1=(int)Math.ceil((x+w)*k),y1=(int)Math.ceil((y+h)*k);
        RenderSystem.enableScissor(x0,window.getFramebufferHeight()-y1,Math.max(0,x1-x0),Math.max(0,y1-y0));
    }
    public static void unclip(){RenderSystem.disableScissor();}
    /** Eases in and out. */
    public static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
}
