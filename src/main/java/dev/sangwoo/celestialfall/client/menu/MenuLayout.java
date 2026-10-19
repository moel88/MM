package dev.sangwoo.celestialfall.client.menu;
import java.util.*;
public final class MenuLayout {
    public static final double WIDTH=900,HEIGHT=500;
    public record Rect(double x,double y,double w,double h){public boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+h;}}
    public record View(double x,double y,double scale){public double px(double a){return x+a*scale;}public double py(double a){return y+a*scale;}}
    public record Dock(String id,Rect rect,boolean enabled){}
    public static View view(int width,int height){double s=Math.min((width-16)/WIDTH,(height-16)/HEIGHT);return new View((width-WIDTH*s)/2,(height-HEIGHT*s)/2,s);}
    public static List<Dock> dock(int width,int height,boolean expanded){
        boolean compact=height<260;double cell=compact?Math.min(30,(width-32)/7.0-4):Math.max(24,Math.min(40,(height-150)/5.0)),gap=5,x=12,y=height-(compact?72:78);
        var out=new ArrayList<Dock>();out.add(new Dock("menu",new Rect(x,y,cell,cell),true));if(!expanded)return out;
        if(compact){String[] ids={"rebirth","bag","achievements","quests","draw","settings"};for(int i=0;i<6;i++)out.add(new Dock(ids[i],new Rect(x+(i+1)*(cell+4),y,cell,cell),!ids[i].equals("bag")));out.set(0,new Dock("menu",new Rect(x,y,cell,cell),true));}
        else {out.add(new Dock("draw",new Rect(x+cell+gap,y,cell,cell),true));out.add(new Dock("settings",new Rect(x+2*(cell+gap),y,cell,cell),true));String[] ids={"quests","achievements","bag","rebirth"};for(int i=0;i<4;i++)out.add(new Dock(ids[i],new Rect(x,y-(i+1)*(cell+gap),cell,cell),!ids[i].equals("bag")));}
        return out;
    }
    public static String name(String id){return switch(id){case "settings"->"설정";case "draw"->"뽑기";case "quests"->"퀘스트";case "achievements"->"업적";case "bag"->"가방 · 준비 중";case "rebirth"->"환생";default->"메뉴";};}
}
