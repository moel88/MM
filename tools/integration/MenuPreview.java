package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.client.menu.*;
import dev.sangwoo.celestialfall.menu.MenuProgress;
import dev.sangwoo.celestialfall.progression.ProgressProfile;
import java.util.*;
import java.nio.file.*;
public final class MenuPreview {
    static void button(UiCanvas c,int x,int y,int w,int h,String name,boolean enabled,boolean selected){MenuArt.button(c,new MenuLayout.Rect(x,y,w,h),name,enabled,false,selected,false);}
    public static void main(String[] args)throws Exception {
        var shots=new LinkedHashMap<String,Object>();
        shots.put("dock_closed",Map.of("width",900,"height",500,"scene",MenuArt.dock(900,500,false,-1,-1)));
        shots.put("dock_open",Map.of("width",900,"height",500,"scene",MenuArt.dock(900,500,true,-1,-1)));
        for(String page:new String[]{"settings","news","quests","achievements","rebirth"}){
            var p=new MenuArt.Player("SO DI","스탠드 · 카게노","한계에 닿은 자",200,200,100,50,5,page.equals("achievements")?0:2);var c=MenuArt.frame(page,p);
            if(!page.equals("rebirth")){String[] ids={"news","quests","achievements","settings"},names={"소식","퀘스트","업적","설정"};for(int i=0;i<4;i++)button(c,25+i*152,78,145,43,names[i],true,page.equals(ids[i]));}
            MenuArt.button(c,new MenuLayout.Rect(841,14,36,36),"",true,false,false,true);MenuArt.icon(c,"close",859,32,24,0xffffffff);
            if(page.equals("settings")){
                double[] values={.8,.6,1};MenuContent.settings(c,values);
                for(int i=0;i<3;i++){double x=251,y=203+i*78,w=259;MenuArt.meter(c,x,y-4,w,values[i],0xff23b3f0);c.circle(x+w*values[i],y,13,0xff1173c3);c.circle(x+w*values[i],y,10,0xfff3fdff);}
                button(c,28,441,116,32,"기본값",true,false);button(c,155,441,132,32,"버튼 소리 듣기",true,false);button(c,467,438,156,38,"설정 저장",true,true);
            }else if(page.equals("news")){MenuArt.empty(c,"아직 소식이 없습니다.","새로운 소식이 등록되면 이곳에서 확인할 수 있어요.");button(c,25,441,124,32,"새로고침",true,false);}
            else if(page.equals("quests")){c.text("퀘스트",42,143,18,MenuArt.INK,170);c.panel(277,139,288,33,7,0xfff8fdff,0xfff8fdff);c.text("검색...",288,146,14,MenuArt.SOFT,230);button(c,574,139,43,32,"×",false,false);MenuArt.empty(c,"받은 퀘스트가 없어요.","퀘스트를 받으면 여기에 표시돼요.");button(c,25,441,124,32,"새로고침",true,false);}
            else if(page.equals("achievements")){MenuContent.achievements(c,List.of(0,1,2,3),0,true,new MenuProgress(3,0),new ProgressProfile(50,5,0,"kageno"));c.text("검색...",288,146,14,MenuArt.SOFT,230);button(c,574,139,43,32,"×",false,false);for(int i=0;i<3;i++)button(c,502,191+i*77,105,32,i==0?"적용 중":i==1?"적용":"미달성",i<2,i==0);button(c,25,441,124,32,"새로고침",true,false);button(c,494,441,54,32,"↑",false,false);button(c,559,441,54,32,"↓",true,false);}
            else {MenuContent.growth(c,new ProgressProfile(50,5,2,"kageno"),200,"한계에 닿은 자");button(c,28,435,286,43,"환생  ·  레벨 50 필요",true,true);button(c,330,435,290,43,"초월  ·  환생 15회 필요",false,true);}
            shots.put(page,Map.of("width",900,"height",500,"scene",c));
        }
        Path out=Path.of("build/botand-menu");Files.createDirectories(out);Files.writeString(out.resolve("scenes.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(shots));
    }
}
