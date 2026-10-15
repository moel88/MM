package dev.sangwoo.celestialfall.client.menu;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.menu.MenuProgress;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** A real non-pausing Minecraft Screen: native focus, keyboard, pointer, drag and scroll dispatch. */
public final class BotandMenuScreen extends Screen {
    private String page,query="",localNotice="";
    private int scroll,confirmation,age;
    private MenuLayout.View view;
    private TextFieldWidget search;
    private final double[] draft=MenuSettings.current();
    private boolean dirty;
    public BotandMenuScreen(){super(Text.literal("보탠드 메뉴"));}
    @Override protected void init(){view=MenuLayout.view(width,height);rebuild(false);}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(confirmation!=0){confirmation=0;rebuild(false);return;}if(page!=null){page=null;rebuild(false);return;}client.setScreen(null);}
    private void exit(){confirmation=0;page=null;client.setScreen(null);}
    @Override public void tick(){age++;if(!MenuClient.available()){exit();return;}if(search!=null)search.tick();if(page!=null&&age%60==0&&!MenuClient.pending)MenuClient.request(0,-1);}
    public void updated(){if(client!=null)rebuild(search!=null&&search.isFocused());}
    private void select(String next){page=next;query="";scroll=confirmation=0;localNotice="";MenuClient.notice="";rebuild(false);}
    private List<Integer> matches(){var out=new ArrayList<Integer>();String q=query.toLowerCase(Locale.ROOT).strip();for(int i=0;i<4;i++)if((MenuProgress.NAMES[i]+MenuProgress.DETAILS[i]+MenuProgress.TITLES[i]).toLowerCase(Locale.ROOT).contains(q))out.add(i);return out;}
    private MenuLayout.Rect box(double x,double y,double w,double h){return new MenuLayout.Rect(x,y,w,h);}
    private void button(MenuLayout.Rect rect,String label,boolean active,boolean selected,boolean danger,Runnable action){var b=new ArtButton(rect,label,active,selected,danger,action);if(rect.x()==841)b.setMessage(Text.literal("닫기"));addDrawableChild(b);}
    private void rebuild(boolean focusSearch){
        clearChildren();search=null;
        if(page==null){for(var d:MenuLayout.dock(width,height,true)){
            var v=new MenuLayout.View(0,0,1);var b=new ArtButton(d.rect(),"",d.enabled(),false,false,()->{if(d.id().equals("menu"))exit();else select(d.id());},v,d.id());
            b.setMessage(Text.literal(MenuLayout.name(d.id())));addDrawableChild(b);
        }return;}
        if(confirmation!=0){
            button(box(182,310,166,40),"취소",true,false,false,()->{confirmation=0;rebuild(false);});
            button(box(364,310,170,40),confirmation==1?"환생하기":"초월하기",!MenuClient.pending,true,false,()->{int action=confirmation;confirmation=0;localNotice="";MenuClient.notice="";MenuClient.request(action,-1);rebuild(false);});return;
        }
        button(box(841,14,36,36),"",true,false,true,()->close());
        if(!page.equals("rebirth")){
            String[] tabs={"news","quests","achievements","settings"};String[] names={"소식","퀘스트","업적","설정"};
            for(int i=0;i<4;i++){String tab=tabs[i];button(box(25+i*152,78,145,43),names[i],true,tab.equals(page),false,()->select(tab));}
        }
        if(page.equals("settings")){
            for(int i=0;i<3;i++)addDrawableChild(new SoundSlider(i,box(243,186+i*78,275,34)));
            button(box(28,441,116,32),"기본값",true,false,false,()->{draft[0]=1;draft[1]=.8;draft[2]=1;dirty=true;localNotice="기본값으로 바꿨어요. 저장하면 적용돼요.";rebuild(false);});
            button(box(155,441,132,32),"버튼 소리 듣기",true,false,false,()->MenuClient.click(draft[2]));
            button(box(467,438,156,38),"설정 저장",true,true,false,()->{boolean ok=MenuSettings.save(draft);localNotice=ok?"설정을 저장했어요.":"저장하지 못했어요. 설정 파일을 확인해 주세요.";if(ok)dirty=false;rebuild(false);});
        }else if(page.equals("achievements")||page.equals("quests")){
            var b=box(288,142,275,27);search=new TextFieldWidget(textRenderer,(int)view.px(b.x()),(int)view.py(b.y()),(int)(b.w()*view.scale()),Math.max(14,(int)(b.h()*view.scale())),Text.literal("검색"));
            search.setMaxLength(48);search.setText(query);search.setSuggestion(query.isEmpty()?"검색...":null);search.setEditableColor(0xff154f82);search.setUneditableColor(0xff6d91ac);search.setDrawsBackground(false);
            search.setChangedListener(value->{query=value;scroll=0;rebuild(true);});addDrawableChild(search);if(focusSearch)setFocused(search);
            button(box(574,139,43,32),"×",!query.isEmpty(),false,false,()->{query="";scroll=0;rebuild(true);});
            button(box(25,441,124,32),"새로고침",!MenuClient.pending,false,false,()->{localNotice="최신 상태를 확인했어요.";MenuClient.request(0,-1);});
            if(page.equals("achievements")){
                var ids=matches();scroll=Math.max(0,Math.min(scroll,Math.max(0,ids.size()-3)));
                for(int row=0;row<3&&scroll+row<ids.size();row++){
                    int id=ids.get(scroll+row);boolean earned=MenuClient.ready&&MenuClient.progress.earned(id),selected=MenuClient.progress.equipped()==id;
                    button(box(502,191+row*77,105,32),selected?"적용 중":earned?"적용":"미달성",earned&&!MenuClient.pending,selected,false,()->{localNotice="";MenuClient.notice="";MenuClient.request(3,selected?-1:id);});
                }
                button(box(494,441,54,32),"↑",scroll>0,false,false,()->{scroll--;rebuild(false);});button(box(559,441,54,32),"↓",scroll+3<ids.size(),false,false,()->{scroll++;rebuild(false);});
            }
        }else if(page.equals("rebirth")){
            var p=ProgressClient.profile;
            button(box(28,435,286,43),"환생  ·  레벨 50 필요",MenuClient.ready&&p.canRebirth()&&!MenuClient.pending,true,false,()->{confirmation=1;rebuild(false);});
            button(box(330,435,290,43),"초월  ·  환생 15회 필요",MenuClient.ready&&p.canTranscend()&&!MenuClient.pending,true,false,()->{confirmation=2;rebuild(false);});
        }else button(box(25,441,124,32),"새로고침",!MenuClient.pending,false,false,()->{localNotice="새로운 소식이 아직 없어요.";MenuClient.request(0,-1);});
    }
    private MenuArt.Player player(){var p=client.player;var s=ProgressClient.profile;var combat=CombatClient.snapshot(p.getUuid());int guard=combat.broken(client.world.getTime())?0:Math.round(100-combat.pressure());return new MenuArt.Player(p.getName().getString(),s.stand().equals("kageno")?"스탠드 · 카게노":"스탠드 없음",MenuClient.ready?MenuClient.progress.title():"정보 불러오는 중...",(int)Math.ceil(p.getHealth()),(int)Math.ceil(p.getMaxHealth()),guard,s.level(),s.rebirths(),s.transcendence());}
    @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
        if(page==null){super.render(draw,mouseX,mouseY,delta);for(var d:MenuLayout.dock(width,height,true))if(d.rect().contains(mouseX,mouseY))draw.drawTooltip(textRenderer,Text.literal(MenuLayout.name(d.id())),mouseX,mouseY);return;}
        draw.fill(0,0,width,height,0x88213a58);var c=MenuArt.frame(page,player());
        MenuArt.icon(c,"close",859,32,24,0xffffffff);
        if(page.equals("settings"))MenuContent.settings(c,draft);
        else if(page.equals("news"))MenuArt.empty(c,"아직 소식이 없습니다.","새로운 소식이 등록되면 이곳에서 확인할 수 있어요.");
        else if(page.equals("quests")){c.text("퀘스트",42,143,18,MenuArt.INK,170);c.panel(277,139,288,33,7,0xfff8fdff,0xfff8fdff);MenuArt.empty(c,query.isEmpty()?"받은 퀘스트가 없어요.":"검색 결과가 없어요.",query.isEmpty()?"퀘스트를 받으면 여기에 표시돼요.":"검색어를 지우고 다시 확인해 보세요.");}
        else if(page.equals("achievements"))MenuContent.achievements(c,matches(),scroll,MenuClient.ready,MenuClient.progress,ProgressClient.profile);
        else if(page.equals("rebirth"))MenuContent.growth(c,ProgressClient.profile,Math.round(client.player.getMaxHealth()),MenuClient.progress.title());
        String message=!localNotice.isEmpty()?localNotice:MenuClient.notice;
        if(MenuClient.pending)message="서버에서 확인하는 중...";
        if(page.equals("settings")&&dirty&&localNotice.isEmpty())message="변경한 설정은 저장해야 적용돼요.";
        c.text(message,28,484,10,MenuArt.SOFT,825);
        MenuPainter.draw(draw,c,view);
        if(confirmation!=0){draw.fill(0,0,width,height,0x990a223d);var dialog=new UiCanvas();dialog.framed(149,148,417,224,0xfff2fcff,0xffd5effb);dialog.center(confirmation==1?"환생할까요?":"초월할까요?",357,179,24,MenuArt.INK,360);dialog.center(confirmation==1?"레벨 50 → 1  ·  환생 횟수 +1":"환생 15 → 0  ·  초월 횟수 +1",357,227,17,MenuArt.INK,370);dialog.center(confirmation==1?"스탠드와 초월 횟수는 유지돼요.":"현재 레벨과 스탠드는 유지돼요.",357,264,13,MenuArt.SOFT,375);MenuPainter.draw(draw,dialog,view);}
        super.render(draw,mouseX,mouseY,delta);
        if(confirmation==0){var top=new UiCanvas();MenuArt.icon(top,"close",859,32,24,0xffffffff);MenuPainter.draw(draw,top,view);}
    }
    @Override public boolean mouseScrolled(double x,double y,double amount){if(confirmation==0&&"achievements".equals(page)&&box(24,176,602,250).contains((x-view.x())/view.scale(),(y-view.y())/view.scale())){int next=Math.max(0,Math.min(Math.max(0,matches().size()-3),scroll-(int)Math.signum(amount)));if(next!=scroll){scroll=next;rebuild(false);}return true;}return super.mouseScrolled(x,y,amount);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_M&&(search==null||!search.isFocused())){close();return true;}return super.keyPressed(key,scan,modifiers);}
    private final class ArtButton extends PressableWidget {
        final MenuLayout.Rect logical;final Runnable action;final boolean selected,danger;final MenuLayout.View viewport;final String icon,label;
        ArtButton(MenuLayout.Rect r,String label,boolean enabled,boolean selected,boolean danger,Runnable action){this(r,label,enabled,selected,danger,action,view,"");}
        ArtButton(MenuLayout.Rect r,String label,boolean enabled,boolean selected,boolean danger,Runnable action,MenuLayout.View viewport,String icon){super((int)viewport.px(r.x()),(int)viewport.py(r.y()),Math.max(1,(int)Math.ceil(r.w()*viewport.scale())),Math.max(1,(int)Math.ceil(r.h()*viewport.scale())),Text.literal(label));this.logical=r;this.action=action;this.active=enabled;this.selected=selected;this.danger=danger;this.viewport=viewport;this.icon=icon;this.label=label;}
        @Override public void onPress(){MenuClient.click();action.run();}
        @Override public void playDownSound(SoundManager manager){}
        @Override public void renderButton(DrawContext context,int x,int y,float delta){var c=new UiCanvas();if(icon.isEmpty())MenuArt.button(c,logical,label,active,isHovered()||isFocused(),selected,danger);else MenuArt.dockButton(c,icon,logical,active,isHovered()||isFocused());MenuPainter.draw(context,c,viewport);}
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
    }
    private final class SoundSlider extends SliderWidget {
        final int index;final MenuLayout.Rect logical;
        SoundSlider(int index,MenuLayout.Rect b){super((int)view.px(b.x()),(int)view.py(b.y()),(int)(b.w()*view.scale()),Math.max(14,(int)(b.h()*view.scale())),Text.empty(),draft[index]);this.index=index;logical=b;updateMessage();}
        protected void updateMessage(){setMessage(Text.literal(new String[]{"배경음악","스킬 효과음","인터페이스 음"}[index]+" "+Math.round(value*100)+"%"));}
        protected void applyValue(){draft[index]=value;dirty=true;localNotice="";}
        @Override public void renderButton(DrawContext context,int x,int y,float delta){var c=new UiCanvas();double start=(getX()+4-view.x())/view.scale(),w=(getWidth()-8)/view.scale(),yc=logical.y()+17;MenuArt.meter(c,start,yc-4,w,value,0xff23b3f0);c.circle(start+w*value,yc,13,0xff1173c3);c.circle(start+w*value,yc,10,isHovered()||isFocused()?0xffa8eeff:0xfff3fdff);MenuPainter.draw(context,c,view);}
        @Override public void playDownSound(SoundManager manager){MenuClient.click(draft[2]);}
    }
}
