package dev.sangwoo.celestialfall.client.title;
import dev.sangwoo.celestialfall.client.menu.*;

/** Transparent logo and three text controls over the real world camera. */
public final class TitleArt {
    public static final String VERSION="2.15.0";
    public record State(String player,boolean multiplayer,boolean motion,String modal,String notice,TitleMotion animation){}
    public static TitleScene scene(TitleLayout l,int width,int height,State s){
        var out=new TitleScene();var a=s.animation();
        double vx=-l.ox()/l.scale(),vy=-l.oy()/l.scale(),vw=width/l.scale(),vh=height/l.scale();
        // A faint left-edge shade keeps white text readable while the map remains visible.
        var c=new UiCanvas();
        for(int i=0;i<32;i++){
            double fade=1-i/31.0;int alpha=(int)(100*fade*fade);
            c.rect(vx+vw*i/64,vy,vw/64+.05,vh,(alpha<<24)|0x07172a);
        }
        out.art(c);
        var logo=l.logo();out.picture("logo",logo.x(),logo.y(),logo.w(),logo.h(),1);
        String selected="play";double highest=0;
        for(var action:l.actions())if(a.hover(action.id())>highest){highest=a.hover(action.id());selected=action.id();}
        for(var action:l.actions())button(out,action,s,selected.equals(action.id()),l.portrait(),false);
        if(!s.multiplayer()){
            c=new UiCanvas();var r=l.actions().get(1).rect();
            c.text("현재 계정에서 사용할 수 없어요",r.x()+28,r.y()+r.h()-10,l.portrait()?14:9,0xffb2c5d9,r.w()-28);out.art(c);
        }
        if(!s.modal().isEmpty())modal(out,l,s,vx,vy,vw,vh);
        if(a.exitAlpha(s.motion())>0){c=new UiCanvas();c.rect(vx,vy,vw,vh,((int)(a.exitAlpha(s.motion())*200)<<24)|0x071d35);out.art(c);}
        return out;
    }
    private static void button(TitleScene out,TitleLayout.Action a,State s,boolean selected,boolean portrait,boolean modal){
        var c=new UiCanvas();var r=a.rect();boolean enabled=!a.id().equals("servers")||s.multiplayer();
        double emphasis=enabled?s.animation().hover(a.id()):0;
        if(selected&&enabled)emphasis=Math.max(.65,emphasis);
        double size=modal?(portrait?25:17):a.id().equals("play")?(portrait?48:34):(portrait?35:24);
        double x=r.x()+(modal?20:28),y=r.y()+(r.h()-size)/2;
        int ink=enabled?blend(0xffc0d2e7,0xfff5fcff,emphasis):0xff8097b0;
        if(emphasis>.01){
            int alpha=(int)(emphasis*130);
            c.line(r.x()+9,y+2,r.x()+9,y+size-1,9,(alpha<<24)|0x4eafff);
            c.line(r.x()+9,y+2,r.x()+9,y+size-1,3,((int)(emphasis*255)<<24)|0xe5faff);
        }
        c.text(a.label(),x+1,y+2,size,0x99214370,r.w()-(x-r.x())-8);
        c.text(a.label(),x,y,size,ink,r.w()-(x-r.x())-8);out.art(c);
    }
    private static void modal(TitleScene out,TitleLayout l,State s,double vx,double vy,double vw,double vh){
        var d=l.dialog();var c=new UiCanvas();c.rect(vx,vy,vw,vh,0xbb041127);
        c.rect(d.x(),d.y(),d.w(),d.h(),0xed09223e);
        String heading=switch(s.modal()){
            case "leave"->"현재 맵에서 나갈까요?";
            case "servers"->"서버 목록으로 이동할까요?";
            default->"크레딧";
        };
        c.text(heading,d.x()+24,d.y()+23,l.portrait()?30:23,0xfff0faff,d.w()-48);
        if(s.modal().equals("credits")){
            String[] lines={"기획 · 제작   SO DI · Codex","시작 화면   보미와 하늘빛 정원","배경음악   Satellites · Tsukasa","Minecraft 1.20.1 · Fabric / Noto Sans CJK KR (OFL)"};
            for(int i=0;i<lines.length;i++)c.text(lines[i],d.x()+24,d.y()+83+i*(l.portrait()?61:30),l.portrait()?21:14,0xffbdd7eb,d.w()-48);
        }else{
            c.text("현재 맵에서 나간 뒤 이동해요.",d.x()+24,d.y()+97,l.portrait()?24:17,0xffd0e5f5,d.w()-48);
            c.text(s.modal().equals("servers")?"저장된 서버 목록과 직접 연결을 열어요.":"마인크래프트 기본 화면으로 돌아가요.",d.x()+24,d.y()+140,l.portrait()?19:13,0xffa5c7e0,d.w()-48);
        }
        out.art(c);
        String selected=l.modal(s.modal()).get(0).id();double high=0;
        for(var a:l.modal(s.modal()))if(s.animation().hover(a.id())>high){high=s.animation().hover(a.id());selected=a.id();}
        for(var a:l.modal(s.modal()))button(out,a,s,a.id().equals(selected),l.portrait(),true);
    }
    public static int blend(int a,int b,double t){int out=0;for(int shift:new int[]{24,16,8,0})out|=((int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;return out;}
    private TitleArt(){}
}
