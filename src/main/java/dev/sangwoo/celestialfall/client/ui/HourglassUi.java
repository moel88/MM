package dev.sangwoo.celestialfall.client.ui;

import dev.sangwoo.celestialfall.client.gacha.GachaArt;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

/**
 * The player list's look since 2.73.0 (user request: "플레이창 UI는 사이즈도 줄이고 UI를 더 하늘색에 가깝게 바꿔주고 디자인도
 * 전체적으로 갈아엎어서 리메이크 해줘 모래시계와 관련 있게"): light sky-blue glass with a white rim and a thin dark outline
 * round it (so it stands out on a bright sky too), an hourglass in the header whose sand runs down and which turns over
 * once it has run out, grains of sand drifting down behind the title, and a small hourglass between each player's face
 * and name. The pause menu keeps its own navy look (SkyUi). Everything moving is a function of the HUD's clock (ticks).
 * Sizes are GUI units.
 */
public final class HourglassUi {
    private HourglassUi(){}
    /** The glass, top to bottom, and lit (the player's own row). */
    public static final int TOP=0xe68fd9fb,BOTTOM=0xe63b9de0,LIT_TOP=0xf0c4f0ff,LIT_BOTTOM=0xf05cb9f1;
    /** The white rim, the dark line round it, the faint line inside it. */
    public static final int EDGE=0xfff4fcff,OUTLINE=0x8a0f4a7a,INNER=0x4dffffff;
    /** Names, quiet names, the shadow under them, and the light round lit things. */
    public static final int INK=0xffffffff,INK_DIM=0xffd7ecf8,SHADOW=0xa80b3a66,GLOW=0xff9fe4ff;
    /** The sand (pale gold), the hourglass's glass and its plates. */
    public static final int SAND=0xffffdc8f,SAND_DEEP=0xfff4b752,GLASS=0x38ffffff,GLASS_EDGE=0xebffffff,PLATE=0xfffbfdff,PLATE_DEEP=0xffb9dcf3;

    /** Ticks the sand takes to run down, and to turn the hourglass over once it has. */
    public static final double FLOW=140,FLIP=14;
    /** The header's hourglass at a moment: the sand still above (0..1), the sand below (0..1), the stream between (0..1), its turn (radians). */
    public record Sand(float top,float bottom,float stream,float angle){}
    /**
     * The header's hourglass at {@code ticks}: the sand runs from the upper bulb to the lower over FLOW ticks, then it
     * turns over (half a turn, easing in and out) over FLIP. Turned over, a full lower bulb is a full upper one, so the
     * next run begins as the last one ended.
     */
    public static Sand sand(double ticks){
        double u=ticks%(FLOW+FLIP);if(u<0)u+=FLOW+FLIP;
        if(u<FLOW){
            float f=(float)(u/FLOW);
            float stream=(float)Math.min(1,Math.min(u/3,(FLOW-u)/3));
            return new Sand(1-f,f,Math.max(0,stream),0);
        }
        float t=(float)((u-FLOW)/FLIP);t=t*t*(3-2*t);
        return new Sand(0,1,0,(float)Math.PI*t);
    }

    /** The upper bulb of an hourglass {@code s} tall centred on the origin: convex, clockwise on screen; its neck at y 0. */
    static float[] bulb(float s){
        float[] u={-.25f,-.40f, .25f,-.40f, .235f,-.30f, .19f,-.18f, .10f,-.07f, .035f,-.015f, .015f,-.004f,
            -.015f,-.004f, -.035f,-.015f, -.10f,-.07f, -.19f,-.18f, -.235f,-.30f};
        for(int i=0;i<u.length;i++)u[i]*=s;
        return u;
    }
    /** The lower bulb: the upper one turned half round (still clockwise). */
    static float[] lower(float[] bulb){float[] p=new float[bulb.length];for(int i=0;i<bulb.length;i++)p[i]=-bulb[i];return p;}
    /** Moves and turns an outline drawn about the origin to ({@code cx},{@code cy}) turned by {@code angle}. */
    private static float[] place(float[] p,float cx,float cy,float angle){
        float c=(float)Math.cos(angle),sn=(float)Math.sin(angle);float[] o=new float[p.length];
        for(int i=0;i<p.length;i+=2){o[i]=cx+p[i]*c-p[i+1]*sn;o[i+1]=cy+p[i]*sn+p[i+1]*c;}
        return o;
    }

    /**
     * An hourglass {@code s} tall at ({@code cx},{@code cy}): its frame (a plate at either end and two slim posts), its
     * two glass bulbs and the sand in them, {@code sand} saying how much is where and how it is turned.
     */
    public static void hourglass(BufferBuilder b,Matrix4f m,float cx,float cy,float s,Sand sand,float alpha){
        float[] up=bulb(s),down=lower(up);
        // the glass
        GachaArt.smooth(b,m,place(up,cx,cy,sand.angle()),GachaArt.fade(GLASS,alpha),GachaArt.fade(GLASS,alpha*.6f));
        GachaArt.smooth(b,m,place(down,cx,cy,sand.angle()),GachaArt.fade(GLASS,alpha*.6f),GachaArt.fade(GLASS,alpha));
        // the sand above: what is left sits in the bulb's lower part, down to the neck
        if(sand.top()>.002f){
            float level=-.40f*s*(float)Math.pow(sand.top(),.7);
            float[] cut=GachaArt.clipTo(up,GachaArt.rect(-s,level,2*s,-level+.001f*s));
            if(cut.length>=6)GachaArt.smooth(b,m,place(cut,cx,cy,sand.angle()),GachaArt.fade(SAND,alpha),GachaArt.fade(SAND_DEEP,alpha));
        }
        // the sand below: a mound rising from the bottom, its peak under the stream
        if(sand.bottom()>.002f){
            float base=.40f*s-.42f*s*sand.bottom(),peak=.035f*s*Math.min(1,sand.bottom()*8);
            float[] mound={-.3f*s,base+peak, 0,base-peak, .3f*s,base+peak, .3f*s,.45f*s, -.3f*s,.45f*s};
            float[] cut=GachaArt.clipTo(down,mound);
            if(cut.length>=6)GachaArt.smooth(b,m,place(cut,cx,cy,sand.angle()),GachaArt.fade(SAND,alpha),GachaArt.fade(SAND_DEEP,alpha));
            // the stream from the neck down to it
            if(sand.stream()>.01f){
                float[] line=place(new float[]{0,0,0,base-peak},cx,cy,sand.angle());
                GachaArt.line(b,m,line[0],line[1],line[2],line[3],Math.max(SkyUi.px()*.8f,.022f*s),GachaArt.fade(SAND,alpha*sand.stream()));
            }
        }
        float edge=Math.max(SkyUi.px()*.8f,.03f*s);
        GachaArt.stroke(b,m,place(up,cx,cy,sand.angle()),edge,GachaArt.fade(GLASS_EDGE,alpha));
        GachaArt.stroke(b,m,place(down,cx,cy,sand.angle()),edge,GachaArt.fade(GLASS_EDGE,alpha));
        // the frame: two slim posts and a plate at either end
        for(int k=-1;k<=1;k+=2){
            float[] post=GachaArt.rect(k*.31f*s-.024f*s,-.42f*s,.048f*s,.84f*s);
            GachaArt.smooth(b,m,place(post,cx,cy,sand.angle()),GachaArt.fade(PLATE,alpha),GachaArt.fade(PLATE_DEEP,alpha));
            float[] plate=SkyUi.rounded(-.38f*s,k<0?-.5f*s:.41f*s,.76f*s,.09f*s,.03f*s);
            GachaArt.smooth(b,m,place(plate,cx,cy,sand.angle()),GachaArt.fade(PLATE,alpha),GachaArt.fade(PLATE_DEEP,alpha));
        }
    }
    /** A small still hourglass {@code s} tall (a row's divider): the upper bulb empty glass, the lower one full of sand. */
    public static void glyph(BufferBuilder b,Matrix4f m,float cx,float cy,float s,float alpha){
        float[] up=place(bulb(s),cx,cy,0),down=place(lower(bulb(s)),cx,cy,0);
        GachaArt.smooth(b,m,up,GachaArt.fade(GLASS,alpha),GachaArt.fade(GLASS,alpha));
        GachaArt.smooth(b,m,down,GachaArt.fade(SAND,alpha*.95f),GachaArt.fade(SAND_DEEP,alpha*.95f));
        float edge=Math.max(SkyUi.px()*.7f,.05f*s);
        GachaArt.stroke(b,m,up,edge,GachaArt.fade(GLASS_EDGE,alpha*.85f));
        GachaArt.stroke(b,m,down,edge,GachaArt.fade(GLASS_EDGE,alpha*.85f));
        for(int k=-1;k<=1;k+=2)GachaArt.line(b,m,cx-.32f*s,cy+k*.45f*s,cx+.32f*s,cy+k*.45f*s,Math.max(SkyUi.px(),.07f*s),GachaArt.fade(PLATE,alpha));
    }
    /**
     * A pane of the glass: shaded down its height ({@code lit} 0..1 lighter), a white rim, a thin dark line round the rim
     * (it holds against a bright sky) and a faint line inside.
     */
    public static void glass(BufferBuilder b,Matrix4f m,float x,float y,float w,float h,float r,float lit,float alpha){
        float px=SkyUi.px(),line=Math.max(px,.85f);
        GachaArt.stroke(b,m,SkyUi.rounded(x-line,y-line,w+2*line,h+2*line,r+line),Math.max(px,line*.9f),GachaArt.fade(OUTLINE,alpha));
        float[] o=SkyUi.rounded(x,y,w,h,r);
        GachaArt.smooth(b,m,o,GachaArt.fade(GachaArt.mix(TOP,LIT_TOP,lit),alpha),GachaArt.fade(GachaArt.mix(BOTTOM,LIT_BOTTOM,lit),alpha));
        // a sheen over the upper half
        GachaArt.smooth(b,m,SkyUi.rounded(x+1.2f*line,y+1.2f*line,w-2.4f*line,h*.42f,Math.max(0,r-line)),GachaArt.fade(0x40ffffff,alpha),GachaArt.fade(0x00ffffff,alpha));
        GachaArt.stroke(b,m,o,line,GachaArt.fade(EDGE,alpha*(.85f+.15f*lit)));
        float in=Math.min(2f,Math.min(w,h)*.18f);
        GachaArt.stroke(b,m,SkyUi.rounded(x+in,y+in,w-2*in,h-2*in,Math.max(0,r-in*.6f)),Math.max(px*.8f,.4f),GachaArt.fade(INNER,alpha));
    }
    /**
     * The header: the glass, the hourglass on a round seal at its left, grains of sand drifting down behind the title,
     * now and then a light running across it, and the title. {@code now} is in ticks. The cut is in screen coordinates,
     * so the matrix must not be moved or scaled here.
     */
    public static void header(DrawContext d,float x,float y,float w,float hh,float r,float a,double now,String title,float size){
        var m=d.getMatrices().peek().getPositionMatrix();
        var b=GachaArt.begin(d,false);glass(b,m,x,y,w,hh,r,.25f,a);GachaArt.end(b);
        SkyUi.clip(x+1,y+1,w-2,hh-2);
        // grains of sand drifting down, each on its own time
        b=GachaArt.begin(d,true);
        float sx=x+hh*1.15f,sw=w-hh*1.15f-3;
        for(int i=0;i<9;i++){
            float period=70+50*GachaArt.hash(i*7+1),phase=GachaArt.hash(i*7+2);
            double f=(now/period+phase)%1;float gx=sx+sw*GachaArt.hash(i*7+3)+(float)Math.sin(now*.05+i)*.6f,gy=y+1+(hh-2)*(float)f;
            float g=(float)Math.sin(Math.PI*f)*.55f,rad=Math.max(SkyUi.px()*.9f,hh*.035f);
            GachaArt.fill(b,m,GachaArt.circle(gx,gy,rad,8),GachaArt.fade(SAND,a*g));
        }
        double cycle=(now%160)/160;
        if(cycle<.2){
            float s=(float)(cycle/.2),bx=x-hh+(w+2*hh)*s;
            GachaArt.fill(b,m,new float[]{bx,y,bx+hh*.45f,y,bx+hh*.05f,y+hh,bx-hh*.4f,y+hh},GachaArt.fade(0x36ffffff,a*(float)Math.sin(Math.PI*s)));
        }
        GachaArt.end(b);
        SkyUi.unclip();
        // the seal and its hourglass
        float cx=x+hh*.62f,cy=y+hh/2,seal=hh*.42f;
        b=GachaArt.begin(d,true);GachaArt.glow(b,m,GachaArt.circle(cx,cy,seal,28),hh*.3f,GLOW,.32f*a);GachaArt.end(b);
        b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,GachaArt.circle(cx,cy,seal,28),GachaArt.fade(0xff3f9fe0,a),GachaArt.fade(0xff1f6fb6,a));
        GachaArt.stroke(b,m,GachaArt.circle(cx,cy,seal,28),Math.max(SkyUi.px(),.8f),GachaArt.fade(EDGE,a));
        hourglass(b,m,cx,cy,seal*1.55f,sand(now),a);
        GachaArt.end(b);
        float tx=x+hh*1.1f+(w-hh*1.1f)/2;
        GachaFont.text(title,tx,y+hh/2,size,GachaFont.Weight.BOLD).align(.5f)
            .color(INK).alpha(a).glow(GLOW,size*.4f,.4f).shadow(SHADOW,.7f,0,size*.08f).draw(d);
    }
}
