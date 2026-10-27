package dev.sangwoo.celestialfall.client.menu;
import java.util.*;
public final class MenuLayout {
    public static final double WIDTH=900,HEIGHT=500;
    public record Rect(double x,double y,double w,double h){public boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+h;}}
    public record View(double x,double y,double scale){public double px(double a){return x+a*scale;}public double py(double a){return y+a*scale;}}
    public record Dock(String id,Rect rect,boolean enabled){}
    public static View view(int width,int height){double s=Math.min((width-16)/WIDTH,(height-16)/HEIGHT);return new View((width-WIDTH*s)/2,(height-HEIGHT*s)/2,s);}
    /** Half the hotbar's width (vanilla: 182 wide, 22 high, at the bottom's middle), and the off-hand slot's left of it. */
    static final double HOTBAR_HALF=91,OFFHAND=29;
    /**
     * The dock: the menu button (≡) at the bottom left on the hotbar's row (2.74.0, user request: "왼쪽에 있는 선 3개 메뉴
     * 조금만 더 아래로 내려줘", then "더 내려줘 인벤토리랑 y좌표가 같게하자": its bottom on the screen's bottom, as the hotbar's; it
     * began 78 above the bottom, 72 on compact screens), and, open, the other buttons above it and beside it, never over
     * the hotbar: the 뽑기 and 설정 buttons beside it where they clear the hotbar (and its off-hand slot), else in a column beside the column above
     * it; on compact screens their row one step above it. On a narrow screen the button is no wider than leaves the
     * off-hand slot clear.
     */
    public static List<Dock> dock(int width,int height,boolean expanded){
        boolean compact=height<260;double gap=compact?4:5,x=12;
        double cell=Math.min(compact?Math.min(30,(width-32)/7.0-4):Math.max(24,Math.min(40,(height-150)/5.0)),width/2.0-HOTBAR_HALF-OFFHAND-gap-x),y=height-cell;
        var out=new ArrayList<Dock>();out.add(new Dock("menu",new Rect(x,y,cell,cell),true));if(!expanded)return out;
        if(compact){String[] ids={"rebirth","bag","achievements","quests","draw","settings"};for(int i=0;i<6;i++)out.add(new Dock(ids[i],new Rect(x+i*(cell+gap),y-(cell+gap),cell,cell),!ids[i].equals("bag")));}
        else {boolean beside=x+3*cell+3*gap<=width/2.0-HOTBAR_HALF-OFFHAND;String[] side={"draw","settings"};
            for(int i=0;i<2;i++)out.add(new Dock(side[i],beside?new Rect(x+(i+1)*(cell+gap),y,cell,cell):new Rect(x+cell+gap,y-(i+1)*(cell+gap),cell,cell),true));
            String[] ids={"quests","achievements","bag","rebirth"};for(int i=0;i<4;i++)out.add(new Dock(ids[i],new Rect(x,y-(i+1)*(cell+gap),cell,cell),!ids[i].equals("bag")));}
        return out;
    }
    public static String name(String id){return switch(id){case "settings"->"설정";case "draw"->"뽑기";case "quests"->"퀘스트";case "achievements"->"업적";case "bag"->"가방 · 준비 중";case "rebirth"->"환생";default->"메뉴";};}
}
