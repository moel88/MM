package dev.sangwoo.celestialfall.client.menu;
import dev.sangwoo.celestialfall.menu.MenuProgress;
public final class MenuContent {
    public static void settings(UiCanvas c,double[] volume){
        c.text("소리 설정",43,146,18,MenuArt.INK,180);
        String[] names={"배경음악","스킬 효과음","인터페이스 음"},details={"게임 배경음악","스킬과 궁극기 소리","메뉴 버튼과 알림"};
        for(int i=0;i<3;i++){int y=180+i*78;c.panel(38,y,573,62,8,0xfff8fdff,0xffe5f5fd);c.text(names[i],57,y+12,16,MenuArt.INK,180);c.text(details[i],58,y+36,11,MenuArt.SOFT,170);c.panel(536,y+12,63,34,6,0xff23649e,0xff134a7b);c.center(Math.round(volume[i]*100)+"%",567,y+21,15,MenuArt.WHITE,57);}
    }
    public static void achievements(UiCanvas c,java.util.List<Integer> ids,int scroll,boolean ready,dev.sangwoo.celestialfall.menu.MenuProgress earnedState,dev.sangwoo.celestialfall.progression.ProgressProfile profile){
        c.text("업적",42,143,18,MenuArt.INK,170);c.panel(277,139,288,33,7,0xfff8fdff,0xfff8fdff);
        if(ids.isEmpty()){MenuArt.empty(c,"검색 결과가 없어요.","다른 검색어를 입력해 보세요.");return;}
        for(int row=0;row<3&&scroll+row<ids.size();row++){
            int id=ids.get(scroll+row),y=181+row*77;boolean earned=ready&&earnedState.earned(id);
            c.panel(37,y,578,70,8,earned?0xfff9fdff:0xffe4f0f7,earned?0xffe6f7ff:0xffd9e9f3);
            c.panel(46,y+10,47,47,7,earned?0xff4fc5f6:0xffa1becf,earned?0xff087ac5:0xff7ca4ba);MenuArt.icon(c,earned?"achievements":"lock",69,y+34,31,0xfff3fcff);
            c.text(MenuProgress.NAMES[id],108,y+9,16,MenuArt.INK,380);c.text(MenuProgress.DETAILS[id],108,y+31,11,MenuArt.SOFT,355);
            int val=earned?new int[]{50,1,15,1}[id]:new int[]{profile.level(),profile.rebirths(),profile.rebirths(),profile.transcendence()}[id],goal=new int[]{50,1,15,1}[id];
            MenuArt.meter(c,108,y+53,285,val/(double)goal,0xff33baf3);c.text(Math.min(val,goal)+" / "+goal,406,y+49,11,MenuArt.SOFT,75);
        }
        c.text((Math.min(ids.size(),scroll+1))+"–"+Math.min(ids.size(),scroll+3)+" / "+ids.size(),414,450,12,MenuArt.SOFT,70);
        c.text("칭호는 능력치에 영향을 주지 않아요.",167,450,11,MenuArt.SOFT,240);
    }
    public static void growth(UiCanvas c,dev.sangwoo.celestialfall.progression.ProgressProfile p,int maxHealth,String title){
        c.text("지금까지의 성장",44,100,20,MenuArt.INK,420);
        String[] left={"레벨","최대 체력","최대 가드","현재 스탠드","환생 횟수","초월 횟수","적용 중인 칭호"};
        String[] right={p.level()+" / 50",maxHealth+" HP","100",dev.sangwoo.celestialfall.progression.StandStats.get(p.stand()).korean(),p.rebirths()+" / 15",""+p.transcendence(),title};
        for(int i=0;i<7;i++){int y=137+i*39;c.panel(38,y,575,33,6,i%2==0?0xfffbfeff:0xffe3f3fc,i%2==0?0xfff0faff:0xffdcedfa);c.flower(55,y+16,7);c.text(left[i],76,y+9,13,MenuArt.INK,250);c.text(right[i],379,y+8,14,i==4?0xffc48514:MenuArt.INK,220);}
    }
}
