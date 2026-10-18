package dev.sangwoo.celestialfall.client.title;
import java.util.*;
import dev.sangwoo.celestialfall.client.menu.MenuLayout.Rect;

/** Stable text hit areas, shared by the live screen and its review renders. */
public record TitleLayout(double width,double height,double scale,double ox,double oy,boolean portrait,
                          Rect logo,List<Action> actions) {
    public static final int IMAGE_WIDTH=1846,IMAGE_HEIGHT=852;
    public record Action(String id,Rect rect,String label,String subtitle){}
    public static TitleLayout of(int screenWidth,int screenHeight){
        boolean portrait=screenWidth/(double)Math.max(1,screenHeight)<1.18;
        double w=portrait?720:960,h=portrait?1100:960.0*IMAGE_HEIGHT/IMAGE_WIDTH;
        double s=Math.min(screenWidth/w,screenHeight/h);
        // UI fits on screen; unlike a background image, the logo must never be cover-cropped.
        double ox=portrait?(screenWidth-w*s)/2:0,oy=(screenHeight-h*s)/2;
        double logoWidth=portrait?520:285;
        Rect logo=new Rect(portrait?46:25,portrait?92:32,logoWidth,logoWidth*IMAGE_HEIGHT/IMAGE_WIDTH);
        double x=portrait?54:42,y=portrait?472:184,bw=portrait?612:250;
        double bh=Math.max(portrait?94:43,20/s),gap=portrait?17:2/s;
        List<Action> actions=List.of(
            new Action("play",new Rect(x,y,bw,bh),"플레이",""),
            new Action("servers",new Rect(x,y+bh+gap,bw,bh),"서버 목록",""),
            new Action("credits",new Rect(x,y+2*(bh+gap),bw,bh),"크레딧",""));
        return new TitleLayout(w,h,s,ox,oy,portrait,logo,actions);
    }
    public double x(double x){return ox+x*scale;}
    public double y(double y){return oy+y*scale;}
    public Rect screen(Rect b){return new Rect(x(b.x()),y(b.y()),b.w()*scale,b.h()*scale);}
    public Rect dialog(){
        return portrait?new Rect(42,350,636,530):new Rect(36,88,456,290);
    }
    public List<Action> modal(String modal){
        Rect d=dialog();double y=d.y()+d.h()-65;
        if(List.of("leave","servers").contains(modal))return List.of(
            new Action("cancel",new Rect(d.x()+24,y,(d.w()-64)/2,48),"돌아가기",""),
            new Action("confirm_"+modal,new Rect(d.x()+40+(d.w()-64)/2,y,(d.w()-64)/2,48),modal.equals("leave")?"맵 나가기":"서버 목록으로",""));
        return List.of(new Action("back",new Rect(d.x()+24,y,d.w()-48,48),"돌아가기",""));
    }
}
