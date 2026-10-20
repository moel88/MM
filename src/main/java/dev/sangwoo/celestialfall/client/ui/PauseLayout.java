package dev.sangwoo.celestialfall.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the pause menu's parts sit on a screen of a given size (GUI units), kept free of Minecraft for tests. Every
 * measure is a share of the screen's height, so the menu is the same share of the window at any GUI scale; it narrows
 * on a tall, thin window rather than running off it.
 */
public final class PauseLayout {
    /** The column's width, the header, a button, the gap between buttons and the space under the header. */
    static final double WIDTH=.7,HEADER=.11,BUTTON=.088,GAP=.022,SPACE=.04;
    /** The confirmation box: width, height, its buttons' height and its inner margin. */
    static final double DIALOG_W=.86,DIALOG_H=.42,DIALOG_BUTTON=.085,PAD=.035;
    private PauseLayout(){}

    public record Box(double x,double y,double w,double h){
        public boolean contains(double px,double py){return px>=x&&px<x+w&&py>=y&&py<y+h;}
        public double bottom(){return y+h;}
    }
    /** The header above the buttons, and the buttons from the top. */
    public record Menu(Box header,List<Box> buttons){
        /** The button under a point, or -1. */
        public int at(double x,double y){for(int i=0;i<buttons.size();i++)if(buttons.get(i).contains(x,y))return i;return -1;}
    }
    public static Menu menu(int w,int h,int count){
        double width=Math.min(WIDTH*h,w-32),header=HEADER*h,button=BUTTON*h,gap=GAP*h,space=SPACE*h;
        double total=header+space+count*button+Math.max(0,count-1)*gap;
        // a little above the middle, the way the eye expects a menu
        double x=(w-width)/2,y=Math.max(8,(h-total)*.46);
        var buttons=new ArrayList<Box>(count);
        for(int i=0;i<count;i++)buttons.add(new Box(x,y+header+space+i*(button+gap),width,button));
        return new Menu(new Box(x,y,width,header),List.copyOf(buttons));
    }

    /** The confirmation box in the middle, its confirm button (left) and its cancel button (right). */
    public record Dialog(Box box,Box confirm,Box cancel){
        /** 0 the confirm button, 1 cancel, -1 neither. */
        public int at(double x,double y){return confirm.contains(x,y)?0:cancel.contains(x,y)?1:-1;}
    }
    public static Dialog dialog(int w,int h){
        double width=Math.min(DIALOG_W*h,w-24),height=DIALOG_H*h,pad=PAD*h,button=DIALOG_BUTTON*h;
        double x=(w-width)/2,y=(h-height)/2;
        double bw=(width-3*pad)/2,by=y+height-pad-button;
        return new Dialog(new Box(x,y,width,height),new Box(x+pad,by,bw,button),new Box(x+2*pad+bw,by,bw,button));
    }
}
