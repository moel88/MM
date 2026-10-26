package dev.sangwoo.celestialfall.client.ui;

import dev.sangwoo.celestialfall.client.gacha.GachaArt;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

/**
 * The player list's look since 2.73.0 (user request: "플레이창 UI는 사이즈도 줄이고 UI를 더 하늘색에 가깝게 바꿔주고 디자인도
 * 전체적으로 갈아엎어서 리메이크 해줘 모래시계와 관련 있게"; then, of a first look with an hourglass on a seal in the header and
 * a small one in every row: "모래시계가 너무 대놓고 들어나있어 은연중에 들어나있게 바꾸고", and with the Roblox list the panel came
 * from: "이거 처럼 색깔을 2개 쓰는 것도 좋을듯 메인은 화이트로 하되 테두리나 장식 같은걸 하늘색으로"): two colours, white panes
 * with sky-blue rims, lines and letters, and the hourglass only hinted at: a faint outline in the header's left part on a
 * faint dial, cut by the header's edges as the reference's emblem is, its sand running down slowly and the glass turning
 * over once it has run out, and a few grains of sand drifting down behind the title. The pause menu keeps its own navy
 * look (SkyUi). Everything moving is a function of the HUD's clock (ticks). Sizes are GUI units.
 */
public final class HourglassUi {
    private HourglassUi(){}
    /** The panes, top to bottom: white, the player's own row a breath of sky blue. */
    public static final int PANE_TOP=0xf7ffffff,PANE_BOTTOM=0xf2f3faff,LIT_TOP=0xf7f0faff,LIT_BOTTOM=0xf2dcf1ff;
    /**
     * The sky blue of the rims, lines and ornaments; the letters' (a little deeper, to read on white); quieter letters
     * (spectators, the count of the rest); the light round the player's own row.
     */
    public static final int SKY=0xff5cc6f8,INK=0xff1e8ad6,INK_DIM=0xff86bde2,GLOW=0xff8fdcff;

    /** Ticks the header's faint hourglass's sand takes to run down (half a minute, it is hardly seen moving), and to turn it over once it has. */
    public static final double FLOW=600,FLIP=30;
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

    /** The width of the rims and lines. */
    public static float rim(){return Math.max(SkyUi.px(),.7f);}
    /** A pane: white shaded down its height ({@code lit} 0..1 toward a breath of sky blue), with its sky-blue rim. */
    public static void pane(BufferBuilder b,Matrix4f m,float x,float y,float w,float h,float r,float lit,float alpha){
        float[] o=SkyUi.rounded(x,y,w,h,r);
        GachaArt.smooth(b,m,o,GachaArt.fade(GachaArt.mix(PANE_TOP,LIT_TOP,lit),alpha),GachaArt.fade(GachaArt.mix(PANE_BOTTOM,LIT_BOTTOM,lit),alpha));
        GachaArt.stroke(b,m,o,rim(),GachaArt.fade(SKY,alpha));
    }
    /**
     * The header's faint hourglass, {@code s} across its dial, at ({@code cx},{@code cy}) leaning by {@code tilt}: a
     * dial with its twelve marks, and in it the outline of the hourglass's bulbs and frame with the sand in it as
     * {@code sand} says, all in lines of the one sky blue at {@code alpha} (the sand fainter still).
     */
    static void emblem(BufferBuilder b,Matrix4f m,float cx,float cy,float s,float tilt,Sand sand,float alpha){
        float line=Math.max(SkyUi.px(),.026f*s),dial=.5f*s;int faint=GachaArt.fade(SKY,alpha*.7f),full=GachaArt.fade(SKY,alpha);
        GachaArt.stroke(b,m,GachaArt.circle(cx,cy,dial,56),line*.8f,faint);
        for(int i=0;i<12;i++){
            double t=tilt+i*Math.PI/6;float c=(float)Math.cos(t),sn=(float)Math.sin(t),in=dial*(i%3==0?.84f:.9f);
            GachaArt.line(b,m,cx+c*in,cy+sn*in,cx+c*dial,cy+sn*dial,line*(i%3==0?1:.7f),faint);
        }
        float g=.74f*s,turn=tilt+sand.angle();
        float[] up=bulb(g),down=lower(up);
        // the sand: what is left above sits in the upper bulb's lower part, a mound rises below, a thread between
        int grain=GachaArt.fade(SKY,alpha*.45f);
        if(sand.top()>.002f){
            float level=-.40f*g*(float)Math.pow(sand.top(),.7);
            float[] cut=GachaArt.clipTo(up,GachaArt.rect(-g,level,2*g,-level+.001f*g));
            if(cut.length>=6)GachaArt.fill(b,m,place(cut,cx,cy,turn),grain);
        }
        if(sand.bottom()>.002f){
            float base=.40f*g-.42f*g*sand.bottom(),peak=.035f*g*Math.min(1,sand.bottom()*8);
            float[] cut=GachaArt.clipTo(down,new float[]{-.3f*g,base+peak, 0,base-peak, .3f*g,base+peak, .3f*g,.45f*g, -.3f*g,.45f*g});
            if(cut.length>=6)GachaArt.fill(b,m,place(cut,cx,cy,turn),grain);
            if(sand.stream()>.01f){float[] l=place(new float[]{0,0,0,base-peak},cx,cy,turn);GachaArt.line(b,m,l[0],l[1],l[2],l[3],line*.6f,GachaArt.fade(SKY,alpha*.45f*sand.stream()));}
        }
        GachaArt.stroke(b,m,place(up,cx,cy,turn),line,full);
        GachaArt.stroke(b,m,place(down,cx,cy,turn),line,full);
        // the frame: two slim posts and a plate at either end
        for(int k=-1;k<=1;k+=2){
            float[] post=place(new float[]{k*.31f*g,-.42f*g,k*.31f*g,.42f*g},cx,cy,turn);
            GachaArt.line(b,m,post[0],post[1],post[2],post[3],line,full);
            GachaArt.stroke(b,m,place(SkyUi.rounded(-.38f*g,k<0?-.5f*g:.41f*g,.76f*g,.09f*g,.03f*g),cx,cy,turn),line,full);
        }
    }
    /**
     * The header: a white pane with its sky-blue rim, the faint hourglass on its dial in its left part (cut by its
     * edges), a few grains of sand drifting down behind the title, and the title in the middle. {@code now} is in ticks.
     * The cut is in screen coordinates, so the matrix must not be moved or scaled here.
     */
    public static void header(DrawContext d,float x,float y,float w,float hh,float r,float a,double now,String title,float size){
        var m=d.getMatrices().peek().getPositionMatrix();
        float[] o=SkyUi.rounded(x,y,w,hh,r);float rim=rim();
        var b=GachaArt.begin(d,false);GachaArt.smooth(b,m,o,GachaArt.fade(PANE_TOP,a),GachaArt.fade(PANE_BOTTOM,a));GachaArt.end(b);
        SkyUi.clip(x+rim,y+rim,w-2*rim,hh-2*rim);
        b=GachaArt.begin(d,false);
        emblem(b,m,x+hh*1.75f,y+hh*.62f,hh*2.5f,-.35f,sand(now),a*.3f);
        // grains of sand drifting down, each on its own time
        for(int i=0;i<6;i++){
            float period=90+70*GachaArt.hash(i*7+1),phase=GachaArt.hash(i*7+2);
            double f=(now/period+phase)%1;float gx=x+hh*.6f+(w-hh*1.2f)*GachaArt.hash(i*7+3)+(float)Math.sin(now*.04+i)*.6f,gy=y+(float)(hh*f);
            GachaArt.fill(b,m,GachaArt.circle(gx,gy,Math.max(SkyUi.px()*.9f,hh*.04f),8),GachaArt.fade(SKY,a*.4f*(float)Math.sin(Math.PI*f)));
        }
        GachaArt.end(b);
        SkyUi.unclip();
        b=GachaArt.begin(d,false);GachaArt.stroke(b,m,o,rim,GachaArt.fade(SKY,a));GachaArt.end(b);
        GachaFont.text(title,x+w/2,y+hh/2,size,GachaFont.Weight.BOLD).align(.5f).color(INK).alpha(a).draw(d);
    }
}
