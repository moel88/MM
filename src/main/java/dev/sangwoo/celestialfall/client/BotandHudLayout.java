package dev.sangwoo.celestialfall.client;

import java.util.*;
import dev.sangwoo.celestialfall.progression.ProgressProfile;

/** Code-native overlay on the unmodified supplied PNG. Shared by the in-game renderer and review. */
public final class BotandHudLayout {
    public static final int WIDTH=2048,HEIGHT=683;
    public record Quad(double[] xy,int top,int bottom) {}
    public record Label(String text,double x,double y,double scale,double angle,double maxWidth,int color,int outline) {}
    public record Frame(List<Quad> quads,List<Label> labels) {}
    private BotandHudLayout() {}
    /**
     * The art's width on a screen this wide (GUI units). 2.73.0 (user request: "HP창 UI 사이즈를 줄이고"): seven tenths of
     * what it was (until 2.72.0: 44% of the width, 240 to 360).
     */
    public static double width(int screenWidth){return Math.max(1,Math.min(screenWidth-16,Math.min(SIZE*360,Math.max(SIZE*240,screenWidth*.44*SIZE))));}
    /** How large the art is against its size until 2.72.0. */
    public static final double SIZE=.7;
    /** Where the art sits (GUI units; BotandHud draws it at 8, 8, {@link #width} wide), for HUD pieces placed around it. */
    public static double right(int screenWidth){return 8+width(screenWidth);}
    /** The bottom of the money and XP line under the art. */
    public static double bottom(int screenWidth){return 12+(int)(width(screenWidth)*HEIGHT/WIDTH)+11;}
    /**
     * Above this line the art holds only its portrait, at the left up to {@link #portraitRight}: the bars, the bow and the
     * lantern begin here (row 253 of the 724 of botand_hud.png, drawn 683 high).
     */
    public static double clearAbove(int screenWidth){return 8+width(screenWidth)*238.7/WIDTH;}
    /** The portrait's right edge (column 914 of the art's 2172, drawn 2048 wide). */
    public static double portraitRight(int screenWidth){return 8+width(screenWidth)*861.8/WIDTH;}
    public static float guard(float pressure,boolean broken){return broken?0:Math.max(0,Math.min(100,100-pressure));}
    private static int mix(int a,int b,double t) {
        int result=0;for(int shift:new int[]{0,8,16,24})result|=((int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;return result;
    }
    private static void capsule(List<Quad> list,double x,double y,double w,double h,double radius,double portion,int top,int bottom,double angle) {
        double cx=x+w/2,cy=y+h/2,c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));
        for(int row=0;row<Math.ceil(h);row++) {
            double yy=Math.min(h,row+.5),dy=yy<radius?radius-yy:yy>h-radius?yy-(h-radius):0;
            double inset=dy==0?0:radius-Math.sqrt(Math.max(0,radius*radius-dy*dy));
            double left=x+inset,right=Math.min(x+w-inset,x+w*portion);if(right<=left)continue;
            double[] xy={left,y+row,right,y+row,right,y+Math.min(h,row+1),left,y+Math.min(h,row+1)};
            for(int i=0;i<8;i+=2){double px=xy[i]-cx,py=xy[i+1]-cy;xy[i]=cx+px*c-py*s;xy[i+1]=cy+px*s+py*c;}
            list.add(new Quad(xy,mix(top,bottom,row/h),mix(top,bottom,Math.min(1,(row+1)/h))));
        }
    }
    public static Frame frame(float health,float maxHealth,float guard,ProgressProfile profile,boolean broken) {return frame(health,maxHealth,guard,profile,broken,false);}
    /** The frame, with {@code disabled} (2.60.0) the incapacitation's word over the guard bar, as BREAK over a broken guard. */
    public static Frame frame(float health,float maxHealth,float guard,ProgressProfile profile,boolean broken,boolean disabled) {
        var q=new ArrayList<Quad>();var text=new ArrayList<Label>();
        double hp=Float.isFinite(health)&&maxHealth>0?Math.max(0,Math.min(1,health/maxHealth)):0;
        double shield=Float.isFinite(guard)?Math.max(0,Math.min(1,guard/100)):0;
        capsule(q,398,331,1426,66,33,1,0xff34415e,0xff1c233a,0);
        capsule(q,398,331,1426,66,33,hp,0xfffa97ad,0xffd34661,0);
        capsule(q,452,428,1282,65,32,1,0xff263e59,0xff182a42,0);
        capsule(q,452,428,1282,65,32,shield,0xffb6f5ff,0xff3299dc,0);
        // Keep the original badge rims, hearts, character, ribbons and mini-map untouched.
        capsule(q,136,348,170,110,14,1,0xff1c3253,0xff233e65,-29);
        capsule(q,184,485,78,82,10,1,0xff42536a,0xff26374f,-29);
        capsule(q,286,432,78,82,10,1,0xff435774,0xff253850,-29);
        text.add(new Label(Integer.toString(Math.max(0,(int)Math.ceil(health))),1716,335,7.4,0,170,0xffffffff,0xff650d1b));
        text.add(new Label(Integer.toString(Math.max(0,(int)Math.ceil(guard))),1651,430,7.1,0,150,0xffeffcff,0xff073371));
        text.add(new Label(Integer.toString(profile.level()),205,351,6.3,-29,100,0xfff4faff,0xff172e55));
        text.add(new Label(profile.level()==50?"[MAX]":"[LV]",232,403,5.1,-29,173,0xfff4faff,0xff172e55));
        text.add(new Label(Integer.toString(profile.rebirths()),223,493,8.2,-29,75,0xffffd273,0xff855213));
        text.add(new Label(Integer.toString(profile.transcendence()),325,440,8.2,-29,75,0xff8edbff,0xff126fbb));
        if(broken)text.add(new Label("BREAK",1190,441,3.5,0,400,0xffffb2c2,0xff3c1833));
        else if(disabled)text.add(new Label("무력화",1190,441,3.5,0,400,0xffd7c8ff,0xff2a1846));
        return new Frame(q,text);
    }
}
