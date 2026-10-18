package dev.sangwoo.celestialfall.client.menu;
import dev.sangwoo.celestialfall.menu.MenuProgress;
public final class MenuArt {
    public static final int INK=0xff164b7c,SOFT=0xff4987b2,WHITE=0xfff5fdff;
    public record Player(String name,String stand,String title,int hp,int maxHp,int guard,int level,int rebirths,int transcendence){}
    public static void icon(UiCanvas c,String type,double x,double y,double s,int color){
        double r=s*.32,w=Math.max(1.5,s*.065);
        switch(type){
            case "menu"->{for(int i=-1;i<=1;i++)c.line(x-r,y+i*r*.72,x+r,y+i*r*.72,w,color);}
            case "settings"->{for(int i=0;i<8;i++){double a=i*Math.PI/4;c.line(x+Math.cos(a)*r*.68,y+Math.sin(a)*r*.68,x+Math.cos(a)*r*1.14,y+Math.sin(a)*r*1.14,w*1.7,color);}c.circle(x,y,r*.82,color);c.circle(x,y,r*.36,0xff348ed2);}
            case "achievements"->{c.polygon(color,x-r*.7,y-r,x+r*.7,y-r,x+r*.5,y,x,y+r*.4,x-r*.5,y);c.line(x,y,x,y+r*.95,w,color);c.line(x-r*.55,y+r,x+r*.55,y+r,w,color);c.line(x-r*.7,y-r*.6,x-r*1.03,y-r*.6,w*.7,color);c.line(x-r*1.03,y-r*.6,x-r*.72,y+r*.1,w*.7,color);c.line(x+r*.7,y-r*.6,x+r*1.03,y-r*.6,w*.7,color);c.line(x+r*1.03,y-r*.6,x+r*.72,y+r*.1,w*.7,color);}
            case "rebirth"->{c.star(x,y,r*1.12,color);c.star(x,y,r*.53,0xff348ed2);}
            case "transcend"->{c.star(x,y,r*1.2,color);}
            case "quests","news"->{c.panel(x-r*.8,y-r,r*1.6,r*2,2,color,color);for(int i=-1;i<=1;i++)c.line(x-r*.45,y+i*r*.5,x+r*.4,y+i*r*.5,w*.65,0xff348ed2);}
            case "bag"->{c.panel(x-r*.8,y-r*.6,r*1.6,r*1.7,3,color,color);c.line(x-r*.4,y-r*.6,x-r*.4,y-r,w,color);c.line(x-r*.4,y-r,x+r*.4,y-r,w,color);c.line(x+r*.4,y-r,x+r*.4,y-r*.6,w,color);c.line(x-r*.5,y+r*.3,x+r*.5,y+r*.3,w,0xff348ed2);}
            case "draw"->{c.panel(x-r,y-r,r*1.35,r*1.8,2,color,color);c.panel(x-r*.15,y-r*.55,r*1.2,r*1.7,2,0xff348ed2,0xff348ed2);c.star(x+r*.45,y+r*.27,r*.45,color);}
            case "close"->{c.line(x-r*.65,y-r*.65,x+r*.65,y+r*.65,w,color);c.line(x-r*.65,y+r*.65,x+r*.65,y-r*.65,w,color);}
            case "search"->{c.circle(x-r*.25,y-r*.25,r*.62,color);c.circle(x-r*.25,y-r*.25,r*.42,0xfff0fbff);c.line(x+r*.2,y+r*.2,x+r*.9,y+r*.9,w,color);}
            case "lock"->{c.panel(x-r*.7,y-r*.1,r*1.4,r*1.05,2,color,color);c.line(x-r*.45,y,x-r*.45,y-r*.65,w,color);c.line(x-r*.45,y-r*.65,x+r*.45,y-r*.65,w,color);c.line(x+r*.45,y-r*.65,x+r*.45,y,w,color);}
        }
    }
    public static void button(UiCanvas c,MenuLayout.Rect b,String label,boolean enabled,boolean hover,boolean selected,boolean danger){
        int top=enabled?(danger?0xffff8ea7:hover||selected?0xff59d8ff:0xffe3f8ff):0xffc4d5e0;
        int bottom=enabled?(danger?0xffce365c:hover||selected?0xff0089e2:0xffb5e8ff):0xffa9bfce;
        c.framed(b.x(),b.y(),b.w(),b.h(),top,bottom);
        if(!label.isEmpty())c.center(label,b.x()+b.w()/2,b.y()+(b.h()-15)/2,15,enabled?(selected||danger?WHITE:INK):0xff657f93,b.w()-18);
    }
    public static void dockButton(UiCanvas c,String id,MenuLayout.Rect b,boolean enabled,boolean hover){
        DockSprites.add(c,id,b);
        if(hover&&enabled){double x=b.x(),y=b.y(),w=b.w(),h=b.h();int color=0xcce7faff;
            c.line(x,y,x+w,y,1,color);c.line(x+w,y,x+w,y+h,1,color);
            c.line(x+w,y+h,x,y+h,1,color);c.line(x,y+h,x,y,1,color);
        }
        if(!enabled){double x=b.x()+b.w()-5,y=b.y()+b.h()-5;c.circle(x,y,6,0xee174778);icon(c,"lock",x,y,10,0xfff5fbff);}
    }
    public static UiCanvas dock(int width,int height,boolean expanded,double mx,double my){
        var c=new UiCanvas();for(var d:MenuLayout.dock(width,height,expanded)){var b=d.rect();boolean hover=b.contains(mx,my);
            dockButton(c,d.id(),b,d.enabled(),hover);
            if(hover)c.text(MenuLayout.name(d.id()),b.x()+b.w()+7,b.y()+7,10,WHITE,140);
        }return c;
    }
    public static UiCanvas frame(String page,Player p){
        var c=new UiCanvas();c.panel(-3,-3,906,506,22,0x5013335c,0x9013335c);c.framed(0,0,900,500,0xfff3fcff,0xffccedfc);
        c.panel(8,8,884,54,12,0xff205b9b,0xff0b355f);c.text("BOTAND",32,18,12,0xff8cdfff,160);c.text(page.equals("rebirth")?"환생 · 초월":"플레이어 메뉴",32,34,20,WHITE,350);
        c.ribbon(646,20,26);c.star(590,28,9,0xffa0e4ff);c.star(565,47,4,0xff64c9fa);
        c.panel(650,74,226,362,14,0xff2066a5,0xff0d3966);c.panel(657,81,212,76,10,0xff3ca5df,0xff1969a9);
        c.bunny(686,124,16,0xffe7faff);c.text(p.name(),714,96,16,WHITE,146);c.text(p.stand(),714,120,12,0xffb7eaff,145);
        c.text(p.title(),670,174,15,0xffa6e9ff,190);
        c.text("HP",670,205,12,0xffb8eaff,35);c.text(p.hp()+" / "+p.maxHp(),713,202,17,WHITE,143);
        c.panel(670,229,186,9,4,0xff123b61,0xff123b61);double hp=Math.max(0,Math.min(1,p.hp()/(double)Math.max(1,p.maxHp())));if(hp>0)c.rect(672,231,182*hp,5,0xffff879d);
        c.text("가드",670,252,12,0xffb8eaff,55);c.text(p.guard()+" / 100",725,249,16,WHITE,130);
        c.panel(670,275,186,8,3,0xff123b61,0xff123b61);c.rect(672,277,182*Math.max(0,Math.min(1,p.guard()/100.0)),4,0xff57d3ff);
        String[] labels={"레벨","환생","초월"};int[] values={p.level(),p.rebirths(),p.transcendence()};
        for(int i=0;i<3;i++){int y=307+i*36;c.text(labels[i],670,y,14,0xffa4d6f1,60);c.text(""+values[i]+(i==0&&values[i]==50?"  MAX":""),753,y-1,16,i==1?0xffffd477:WHITE,103);}
        c.flower(862,427,16);c.ribbon(17,484,24);c.star(617,467,8,0xff76caf0);c.line(43,473,610,473,1,0xffa9dbee);
        if(!page.equals("rebirth"))c.panel(24,135,602,292,12,0xffeaf8ff,0xffd5effc);
        else c.panel(24,84,602,343,12,0xffeaf8ff,0xffd5effc);
        return c;
    }
    public static void empty(UiCanvas c,String title,String subtitle){c.bunny(324,272,35,0xffb4e4fa);c.center(title,324,334,20,INK,530);c.center(subtitle,324,366,12,SOFT,540);c.star(213,287,7,0xff84d2f5);c.star(433,253,9,0xff84d2f5);}
    public static void meter(UiCanvas c,double x,double y,double width,double value,int color){c.panel(x,y,width,8,3,0xffbbddeb,0xffbbddeb);if(value>0)c.rect(x+2,y+2,(width-4)*Math.max(0,Math.min(1,value)),4,color);}
}
