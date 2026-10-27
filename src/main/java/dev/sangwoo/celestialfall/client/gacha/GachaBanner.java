package dev.sangwoo.celestialfall.client.gacha;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * The Kageno banner (user request, 2.40.0, the user's second mockup): the art covering the screen, his face always on it,
 * and the layers lifted off it brought to life: the "카게노" title cut in by a stroke of light, a shine passing over its
 * letters now and then, its lightning flickering, his red eyes flaring, and motes of violet light drifting up. Every
 * motion is a smooth function of the screen's clock (ticks, smooth between ticks).
 */
final class GachaBanner {
    private GachaBanner(){}
    /** Where the art lies: banner pixel {@code (ax, ay)} is at screen {@code (x + ax * scale, y + ay * scale)}. */
    record Place(float scale,float x,float y){
        float sx(float ax){return x+ax*scale;}
        float sy(float ay){return y+ay*scale;}
    }
    /**
     * The art covering a {@code w} by {@code h} screen from its top, cut from the left only as far as keeps his face on
     * screen (the title is part of the art, so it always sits where the art has it), zoomed by {@code zoom} about
     * {@code (px, py)}.
     */
    static Place place(int w,int h,float zoom,float px,float py){
        float s=Math.max(w/(float)GachaArt.BANNER_W,h/(float)GachaArt.BANNER_H),visible=w/s;
        float cut=Math.max(0,Math.min(GachaArt.BANNER_W-visible,GachaArt.FACE_X+170-visible));
        float x=-cut*s,y=0;
        return new Place(s*zoom,px-(px-x)*zoom,py-(py-y)*zoom);
    }
    static Place place(int w,int h){return place(w,h,1,w/2f,h/2f);}

    static void art(DrawContext d,Place p,float alpha){
        GachaArt.texture(d,GachaArt.BANNER,p.x,p.y,GachaArt.BANNER_W*p.scale,GachaArt.BANNER_H*p.scale,alpha,false);
    }
    /** A lifted layer at its place in the art. */
    static void layer(DrawContext d,Place p,Identifier id,int[] at,int tint,boolean additive){
        GachaArt.texture(d,id,p.sx(at[0]),p.sy(at[1]),at[2]*p.scale,at[3]*p.scale,0,0,1,1,tint,additive);
    }

    /** How a layer's light is spread over its box: a value per cell corner from the corner's banner coordinates. */
    interface Field {float at(float ax,float ay);}
    /** A layer drawn through a grid of cells, each corner's light given by {@code field} (0..1), tinted {@code color}. */
    static void field(DrawContext d,Place p,Identifier id,int[] at,int color,boolean additive,Field field){
        int cols=28,rows=8;
        var b=GachaArt.sprites(d,id,additive);Matrix4f m=d.getMatrices().peek().getPositionMatrix();
        float[] a=new float[(cols+1)*(rows+1)];
        for(int j=0;j<=rows;j++)for(int i=0;i<=cols;i++)a[j*(cols+1)+i]=GachaArt.clamp01(field.at(at[0]+at[2]*i/(float)cols,at[1]+at[3]*j/(float)rows));
        for(int j=0;j<rows;j++)for(int i=0;i<cols;i++){
            float u0=i/(float)cols,u1=(i+1)/(float)cols,v0=j/(float)rows,v1=(j+1)/(float)rows;
            float x0=p.sx(at[0]+at[2]*u0),x1=p.sx(at[0]+at[2]*u1),y0=p.sy(at[1]+at[3]*v0),y1=p.sy(at[1]+at[3]*v1);
            float c00=a[j*(cols+1)+i],c10=a[j*(cols+1)+i+1],c01=a[(j+1)*(cols+1)+i],c11=a[(j+1)*(cols+1)+i+1];
            if(c00+c10+c01+c11<=0)continue;
            GachaArt.tex(b,m,x0,y0,u0,v0,GachaArt.fade(color,c00));GachaArt.tex(b,m,x0,y1,u0,v1,GachaArt.fade(color,c01));
            GachaArt.tex(b,m,x1,y1,u1,v1,GachaArt.fade(color,c11));GachaArt.tex(b,m,x1,y0,u1,v0,GachaArt.fade(color,c10));
        }
        GachaArt.endSprites(b);
    }

    // ---- the title ----
    /** The title's letters lean: a line of light crossing them runs along this slant (x shifts this much per unit up). */
    static final float LEAN=.42f;
    /**
     * The title: {@code cut} (0..1) is how far the stroke of light revealing it has crossed, left to right; {@code glow}
     * adds its light again (an overshoot as it lands, a slow breath, the lightning's flicker); {@code shine} (0..1) is a
     * pass of white light over the letters, and {@code alpha} fades it all.
     */
    static void title(DrawContext d,Place p,float cut,float glow,float shine,float alpha){
        int[] at=GachaArt.TITLE_AT;float mid=at[1]+at[3]*.55f,span=at[2]+at[3]*LEAN,front=-at[3]*LEAN*.5f+cut*(span+160),soft=120;
        if(cut<=0||alpha<=0)return;
        Field shown=(x,y)->(front-(x+(mid-y)*LEAN))/soft;
        if(cut>=1)GachaBanner.layer(d,p,GachaArt.TITLE,at,GachaArt.alpha(0xffffff,255*alpha),true);
        else field(d,p,GachaArt.TITLE,at,GachaArt.alpha(0xffffff,255*alpha),true,shown);
        if(glow>0)GachaBanner.layer(d,p,GachaArt.TITLE,at,GachaArt.alpha(0xffffff,255*Math.min(1,glow)*alpha),true);
        // the stroke's bright edge, lighting the letters' cores white as it passes
        if(cut<1){
            Field edge=(x,y)->{float k=(front-(x+(mid-y)*LEAN))/70f;return k<0?Math.max(0,1+k*2.2f):Math.max(0,1-k);};
            field(d,p,GachaArt.TITLE_CORE,at,GachaArt.alpha(0xfff4f0ff,255*alpha),true,edge);
            field(d,p,GachaArt.TITLE,at,GachaArt.alpha(0xffd9c9ff,200*alpha),true,edge);
        }
        if(shine>0&&shine<1){
            float f=-at[3]*LEAN*.5f+shine*(span+200)-100;
            Field band=(x,y)->{float k=Math.abs(f-(x+(mid-y)*LEAN))/46f;return Math.max(0,1-k*k);};
            field(d,p,GachaArt.TITLE_CORE,at,GachaArt.alpha(0xffffffff,235*alpha),true,band);
        }
    }
    /** "— KAGENO —": fading in as its letters close up from {@code spread} times as wide. */
    static void sub(DrawContext d,Place p,float alpha,float spread){
        if(alpha<=0)return;int[] at=GachaArt.SUB_AT;float cx=p.sx(at[0]+at[2]/2f),w=at[2]*p.scale*spread,h=at[3]*p.scale;
        GachaArt.texture(d,GachaArt.TITLE_SUB,cx-w/2,p.sy(at[1]),w,h,0,0,1,1,GachaArt.alpha(0xffffff,255*alpha),true);
    }
    static void eyes(DrawContext d,Place p,float strength){
        if(strength>0)layer(d,p,GachaArt.EYES,GachaArt.EYES_AT,GachaArt.alpha(0xffffff,255*Math.min(1,strength)),true);
    }

    /** The lightning's flicker at {@code t} ticks: brief soft flashes at irregular times, never a hard cut. */
    static float flicker(float t,int seed){
        float sum=0;int k=(int)Math.floor(t/7f);
        for(int i=k-1;i<=k+1;i++){
            float h=GachaArt.hash(i*7919+seed);if(h<.62f)continue;
            float at=i*7f+GachaArt.hash(i*31+seed)*7f,x=(t-at)/.9f;
            sum+=(float)Math.exp(-x*x)*(h-.62f)/.38f;
        }
        return Math.min(1,sum);
    }

    /**
     * Motes of light drifting up across the screen (screen units), {@code count} of them, in {@code color}; some flare as
     * four-pointed glints. {@code t} is in ticks.
     */
    static void motes(DrawContext d,int w,int h,float t,float alpha,int count,int color,int glintColor,int seed){
        if(alpha<=0)return;
        Matrix4f m=d.getMatrices().peek().getPositionMatrix();float unit=Math.min(w,h)/700f;
        BufferBuilder b=GachaArt.sprites(d,GachaArt.SPARK,true);
        for(int i=0;i<count;i++){
            float hx=GachaArt.hash(i*3+seed),hy=GachaArt.hash(i*3+1+seed),hs=GachaArt.hash(i*3+2+seed);
            float speed=.35f+hs*.9f,life=(h+60)/speed,ph=(t+hy*life)%life;
            float y=h+30-ph*speed,x=hx*w+(float)Math.sin((t+i*37)*.031f*(.6f+hs))*18*unit;
            float size=(2.2f+hs*5.5f)*unit,a=alpha*(.35f+.65f*(float)Math.sin(Math.PI*ph/life))*(.55f+.45f*(float)Math.sin(t*.12f+i));
            GachaArt.sprite(b,m,x,y,size*3,size*3,0,GachaArt.fade(color,a));
            GachaArt.sprite(b,m,x,y,size,size,0,GachaArt.fade(0xffffffff,a*.8f));
        }
        GachaArt.endSprites(b);
        b=GachaArt.sprites(d,GachaArt.GLINT,true);
        for(int i=0;i<count/4;i++){
            float hx=GachaArt.hash(i*5+seed+911),hy=GachaArt.hash(i*5+seed+912),period=60+GachaArt.hash(i*5+seed+913)*90;
            float ph=((t+GachaArt.hash(i*5+seed+914)*period)%period)/period,a=(float)Math.pow(Math.sin(Math.PI*ph),4)*alpha;
            if(a<.01f)continue;
            float size=(16+GachaArt.hash(i*5+seed+915)*26)*unit*(.7f+.3f*a);
            GachaArt.sprite(b,m,hx*w,hy*h,size,size,ph*.6f,GachaArt.fade(glintColor,a));
        }
        GachaArt.endSprites(b);
    }
}
