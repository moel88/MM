package dev.sangwoo.celestialfall.client.commerce;

import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.menu.*;
import dev.sangwoo.celestialfall.commerce.*;
import dev.sangwoo.celestialfall.progression.ProgressProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;
import java.util.Locale;

/** Native, keyboard-accessible non-pausing screen. Transactions remain server-authoritative. */
public final class CommerceScreen extends Screen {
    public final boolean confirmation;
    private long token;
    private int entity,count,age,pendingAge;
    private boolean seller,pending;
    private String notice;
    private MenuLayout.View view;
    private TradeButton primary;
    private enum ButtonStyle { NORMAL, ACCENT, PRODUCT, CLOSE }

    public CommerceScreen(boolean confirmation,long token,int entity,boolean seller,int count,String notice){
        super(Text.literal(confirmation?"정말 사용하시겠습니까?":seller?"아이템 구매":"아이템 판매"));
        this.confirmation=confirmation;updated(token,entity,seller,count,notice);
    }
    public void updated(long nonce,int target,boolean seller,int count,String notice){
        this.token=nonce;this.entity=target;this.seller=seller;this.count=count;this.notice=notice;
        pending=false;pendingAge=0;if(client!=null)rebuild();
    }
    @Override protected void init(){
        double w=confirmation?640:740,h=confirmation?410:500;
        double scale=Math.max(.1,Math.min((width-24)/w,(height-24)/h));
        view=new MenuLayout.View((width-w*scale)/2,(height-h*scale)/2,scale);rebuild();
    }
    private void rebuild(){
        clearChildren();
        if(confirmation){
            addDrawableChild(new TradeButton(new MenuLayout.Rect(598,18,24,24),"×",ButtonStyle.NORMAL,this::close));
            addDrawableChild(new TradeButton(new MenuLayout.Rect(54,289,250,45),"취소",ButtonStyle.NORMAL,this::close));
            primary=new TradeButton(new MenuLayout.Rect(324,289,262,45),"사용하고 초기화",ButtonStyle.ACCENT,()->request(CommerceServer.CONFIRM));
        }else{
            primary=new TradeButton(ShopArt.PRODUCT,"로카카카 "+money(seller?TradeRules.BUY_PRICE:TradeRules.SELL_PRICE)+(seller?"에 구매":"에 판매"),ButtonStyle.PRODUCT,()->request(CommerceServer.TRADE));
            addDrawableChild(new TradeButton(ShopArt.CLOSE,"닫기",ButtonStyle.CLOSE,this::close));
        }
        addDrawableChild(primary);updateActive();
    }
    private void updateActive(){
        if(primary!=null)primary.active=!pending&&(confirmation||(seller?ProgressClient.profile.money()>=TradeRules.BUY_PRICE:count>0));
    }
    private void request(int action){
        if(pending)return;pending=true;pendingAge=0;CommerceClient.send(action,entity,token);updateActive();
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){CommerceClient.send(CommerceServer.CLOSE,entity,token);client.setScreen(null);}
    @Override public void tick(){
        age++;if(!CommerceClient.available()){close();return;}
        if(!confirmation){var npc=client.world.getEntityById(entity);if(npc==null||client.player.squaredDistanceTo(npc)>TradeRules.REACH*TradeRules.REACH||!client.player.canSee(npc)){close();return;}}
        if(age%60==0)CommerceClient.send(CommerceServer.PING,entity,token);
        if(pending&&++pendingAge>100){close();client.player.sendMessage(Text.literal("응답이 지연됐습니다. 다시 대화해 주세요."),true);}
        updateActive();
    }
    private static String money(long n){return String.format(Locale.ROOT,"%,d",n)+"원";}
    @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
        draw.fill(0,0,width,height,0x64213b55);
        var c=new UiCanvas();
        if(confirmation)drawConfirmation(c);else drawShop(c);
        MenuPainter.draw(draw,c,view);
        super.render(draw,mouseX,mouseY,delta);
        if(confirmation&&(pending||!notice.isEmpty())){
            var n=new UiCanvas();n.panel(11,375,618,27,6,0xee173e67,0xee173e67);
            n.center(pending?"서버에서 확인하는 중...":notice,320,383,11,0xffeffbff,592);MenuPainter.draw(draw,n,view);
        }
    }
    private void drawShop(UiCanvas c){
        ShopArt.frame(c,seller?"SHOP":"SELL ITEM");
        var p=ProgressClient.profile;
        c.text(seller?"클릭하여 1개 구매":"클릭하여 1개 판매",24,236,12,0xff6b8da4,210);
        c.text("스탠드 초기화 아이템",24,256,11,0xff90a6b8,210);
        // Keep the reference's spacious single-item grid; balances and results live in a quiet footer.
        String message=pending?"서버에서 확인하는 중...":notice;
        boolean unavailable=seller?p.money()<TradeRules.BUY_PRICE:count==0;
        if(message.isEmpty()&&unavailable)message=seller?"구매하려면 "+money(TradeRules.BUY_PRICE)+"이 필요해요.":"판매할 로카카카가 없습니다.";
        if(!message.isEmpty()){
            ShopArt.rounded(c,22,345,676,30,5,0xffeef8ff,0xffeef8ff);
            c.text(message,34,354,12,unavailable?0xff667f9a:0xff2875a4,650);
        }
        c.line(24,390,696,390,1,0xffd8eaf4);
        c.text("보유 금액",26,405,11,0xff8399aa,100);
        c.text(money(p.money()),26,424,22,0xff357aa7,240);
        c.text("보유 로카카카  "+count+"개",276,406,13,0xff587e99,240);
        c.text(seller?"구매 1회 · 경험치 +"+TradeRules.BUY_XP:"판매 1회 · 경험치 +"+TradeRules.SELL_XP,276,432,11,0xff819bad,240);
        c.text("Lv. "+p.level()+(p.level()==50?" [MAX]":""),548,406,13,0xff357aa7,140);
        c.text(p.level()==50?"최고 레벨":p.xp()+" / "+ProgressProfile.requiredXp(p.level())+" XP",548,432,11,0xff819bad,140);
    }
    private void drawConfirmation(UiCanvas c){
        c.framed(0,0,640,370,0xfff7fcff,0xffe2f0fb);
        c.panel(7,7,626,62,7,0xff173e67,0xff245e8f);
        c.text("ROKAKAKA  /  STAND RESET",25,19,10,0xff9bddff,545);
        c.text("로카카카 사용",25,37,22,0xffffffff,460);c.flower(579,37,14);
        var p=ProgressClient.profile;
        c.circle(320,113,25,0xffe0d7f5);c.star(320,113,17,0xffa376c1);
        c.center("정말 사용하시겠습니까?",320,156,25,0xff254c73,580);
        c.center("현재 스탠드 · "+(p.stand().equals("kageno")?"카게노":"없음"),320,201,16,0xff704798,565);
        c.center("로카카카 1개를 사용하여 스탠드를 초기화합니다.",320,234,13,0xff446784,565);
        c.center("레벨 · 환생 · 초월 · 보유 금액은 유지됩니다.",320,257,12,0xff668398,565);
    }
    private final class TradeButton extends PressableWidget {
        private final MenuLayout.Rect rect;
        private final ButtonStyle style;
        private final Runnable action;
        TradeButton(MenuLayout.Rect r,String label,ButtonStyle style,Runnable action){
            super((int)view.px(r.x()),(int)view.py(r.y()),Math.max(1,(int)(r.w()*view.scale())),Math.max(1,(int)(r.h()*view.scale())),Text.literal(label));
            this.rect=r;this.style=style;this.action=action;
        }
        @Override public void onPress(){MenuClient.click();action.run();}
        @Override public void playDownSound(SoundManager sounds){}
        @Override public void renderButton(DrawContext draw,int x,int y,float delta){
            var c=new UiCanvas();boolean hover=isHovered()||isFocused();
            if(style==ButtonStyle.PRODUCT)ShopArt.product(c,rect,seller?TradeRules.BUY_PRICE:TradeRules.SELL_PRICE,active,hover,pending);
            else if(style==ButtonStyle.CLOSE)ShopArt.close(c,rect,hover);
            else MenuArt.button(c,rect,getMessage().getString(),active,hover,style==ButtonStyle.ACCENT,false);
            MenuPainter.draw(draw,c,view);
        }
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
    }
}
