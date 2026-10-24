package dev.sangwoo.celestialfall.client.title;
import java.util.*;
import dev.sangwoo.celestialfall.client.menu.UiCanvas;

/** Ordered draw commands shared by native OpenGL rendering and offline previews. */
public final class TitleScene {
    public record Command(String kind,double[] points,int top,int bottom,String text,String asset,
                          double size,double maxWidth,boolean centered){}
    public final List<Command> commands=new ArrayList<>();
    public void art(UiCanvas c){
        for(var p:c.shapes)commands.add(new Command("shape",p.xy(),p.top(),p.bottom(),"","",0,0,false));
        for(var t:c.labels)commands.add(new Command("text",new double[]{t.x(),t.y()},t.color(),0,t.text(),"",t.size(),t.maxWidth(),t.centered()));
    }
    public void picture(String asset,double x,double y,double w,double h,double alpha){commands.add(new Command("image",new double[]{x,y,w,h,alpha},0,0,"",asset,0,0,false));}
}
