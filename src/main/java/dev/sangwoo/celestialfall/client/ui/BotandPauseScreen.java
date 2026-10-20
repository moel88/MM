package dev.sangwoo.celestialfall.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.gacha.GachaArt;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;
import dev.sangwoo.celestialfall.client.menu.MenuClient;
import dev.sangwoo.celestialfall.client.title.TitlePortal;
import dev.sangwoo.celestialfall.menu.CharacterReset;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The pause menu on the Botand map (2.45.0, user request: sky blue, only "게임 계속하기", "설정" and "나가기" kept of
 * Minecraft's menu, and a new "캐릭터 재설정하기" that kills the character at once). Clicking reset resets at once; R
 * asks "정말 재설정 하시겠습니까?" first, Enter resets and Esc goes back. The server does the dying
 * ({@link CharacterReset}). Settings is Minecraft's options screen, which comes back here; leaving is Minecraft's own
 * save and disconnect ({@link TitlePortal#leaveMap}). Everything moves on the screen's own clock: its ticks and the
 * share of the next, which run on while the game is paused.
 */
public final class BotandPauseScreen extends Screen {
    enum Entry{
        RESUME("게임 계속하기",SkyUi.Icon.RESUME,"Esc"),
        SETTINGS("설정",SkyUi.Icon.SETTINGS,null),
        RESET("캐릭터 재설정하기",SkyUi.Icon.RESET,"R"),
        LEAVE("나가기",SkyUi.Icon.LEAVE,null);
        final String label;final SkyUi.Icon icon;final String key;
        Entry(String label,SkyUi.Icon icon,String key){this.label=label;this.icon=icon;this.key=key;}
    }
    private static final Entry[] ENTRIES=Entry.values();
    /** Ticks for a part to come in, for each button to follow the one above, for the question to open or close, and for a hover to settle. */
    static final double ENTER=6,STAGGER=1.1,DIALOG=4,SETTLE=1.4;
    /** Sizes of the title, the labels and the key caps, as shares of the screen's height. */
    static final double TITLE=.05,LABEL=.037,KEY=.026,QUESTION=.046,NOTE=.03;
    private static final String QUESTION_TEXT="정말 재설정 하시겠습니까?",NOTE_TEXT="캐릭터가 바로 사망합니다.";

    private int ticks;
    private final float start;
    /** The button under the mouse or picked with the arrow keys (-1 none), and the question's button under the mouse. */
    private int selected=-1,choice=-1;
    private final float[] lit=new float[ENTRIES.length],answer=new float[2];
    private boolean asking,leaving;
    private double askFrom,askAt=-1e9,lastFrame=Double.NaN;

    public BotandPauseScreen(){
        super(Text.literal("메뉴"));
        start=MinecraftClient.getInstance().getTickDelta();
    }
    /** Whether this menu stands in for Minecraft's (on the Botand map). */
    public static boolean replaces(){return MapClient.enabled();}
    /** Whether a reset can be asked for: alive, playing, and a server that knows it. */
    static boolean resettable(MinecraftClient mc){
        return mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()&&ClientPlayNetworking.canSend(CharacterReset.REQUEST);
    }
    private static String unavailable(MinecraftClient mc){
        if(mc.player!=null&&mc.player.isSpectator())return "관전 중";
        return ClientPlayNetworking.canSend(CharacterReset.REQUEST)?"":"서버 업데이트 필요";
    }

    /** Whether "정말 재설정 하시겠습니까?" is up. */
    public boolean asking(){return asking;}
    /** The screen's clock in ticks from its opening. */
    private double clock(){return ticks+client.getTickDelta()-start;}
    /** How far the question is open, 0 to 1, steady in time from wherever it was when it turned. */
    private double asked(double now){return Math.max(0,Math.min(1,askFrom+(asking?1:-1)*(now-askAt)/DIALOG));}
    private void ask(boolean open){double now=clock();askFrom=asked(now);askAt=now;asking=open;choice=-1;}

    @Override protected void init(){leaving=false;}
    @Override public void tick(){ticks++;}
    @Override public boolean shouldPause(){return true;}

    private void activate(Entry e){
        switch(e){
            case RESUME->{MenuClient.click();close();}
            case SETTINGS->{MenuClient.click();client.setScreen(new OptionsScreen(this,client.options));}
            case RESET->reset();
            case LEAVE->leave();
        }
    }
    private void reset(){
        if(!resettable(client))return;
        MenuClient.click();
        client.setScreen(null);
        ClientPlayNetworking.send(CharacterReset.REQUEST,PacketByteBufs.create());
    }
    private void leave(){
        if(leaving)return;
        leaving=true;MenuClient.click();
        boolean servers=!client.isInSingleplayer();
        // Minecraft's own way out: a pending player report is offered first, and coming back from it returns here
        client.getAbuseReportContext().tryShowDraftScreen(client,this,()->TitlePortal.leaveMap(client,servers),true);
    }

    @Override public boolean keyPressed(int key,int scanCode,int modifiers){
        if(asking){
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)reset();
            else if(key==GLFW.GLFW_KEY_ESCAPE){MenuClient.click();ask(false);}
            return true;
        }
        switch(key){
            case GLFW.GLFW_KEY_ESCAPE->{MenuClient.click();close();return true;}
            case GLFW.GLFW_KEY_R->{if(resettable(client)){MenuClient.click();ask(true);}return true;}
            case GLFW.GLFW_KEY_UP,GLFW.GLFW_KEY_DOWN,GLFW.GLFW_KEY_TAB->{
                boolean up=key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_TAB&&(modifiers&GLFW.GLFW_MOD_SHIFT)!=0;
                selected=selected<0?(up?ENTRIES.length-1:0):Math.floorMod(selected+(up?-1:1),ENTRIES.length);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER,GLFW.GLFW_KEY_SPACE->{if(selected>=0)activate(ENTRIES[selected]);return true;}
            default->{return super.keyPressed(key,scanCode,modifiers);}
        }
    }
    @Override public void mouseMoved(double x,double y){
        if(asking)choice=PauseLayout.dialog(width,height).at(x,y);
        else selected=PauseLayout.menu(width,height,ENTRIES.length).at(x,y);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return false;
        if(asking){
            int i=PauseLayout.dialog(width,height).at(x,y);
            if(i==0)reset();else if(i==1){MenuClient.click();ask(false);}
            return true;
        }
        int i=PauseLayout.menu(width,height,ENTRIES.length).at(x,y);
        if(i<0)return false;
        selected=i;activate(ENTRIES[i]);
        return true;
    }

    @Override public void render(DrawContext d,int mouseX,int mouseY,float delta){
        double now=clock(),dt=Double.isNaN(lastFrame)?0:Math.max(0,now-lastFrame);lastFrame=now;
        int w=width,h=height;
        float shown=GachaArt.ease((float)(now/ENTER)),ask=GachaArt.ease((float)asked(now));
        boolean canReset=resettable(client);
        // hovers settle towards the pick in a frame-rate-free way
        float k=(float)(1-Math.exp(-dt/SETTLE));
        for(int i=0;i<lit.length;i++){
            boolean on=i==selected&&!asking&&(ENTRIES[i]!=Entry.RESET||canReset);
            lit[i]+=((on?1:0)-lit[i])*k;
        }
        for(int i=0;i<2;i++)answer[i]+=((asking&&(i==choice||choice<0&&i==0)?1:0)-answer[i])*k;

        backdrop(d,w,h,now,shown);
        var menu=PauseLayout.menu(w,h,ENTRIES.length);
        float r=(float)Math.max(2,menu.buttons().get(0).h()*.16);
        float dim=1-.6f*ask;
        // the header drops in, then each button slides in from the left after the one above it
        float head=GachaArt.ease((float)(now/ENTER));
        var hb=menu.header();
        SkyUi.header(d,(float)hb.x(),(float)(hb.y()-(1-head)*h*.03),(float)hb.w(),(float)hb.h(),r,head*dim,now,"메뉴",(float)(TITLE*h));
        for(int i=0;i<ENTRIES.length;i++){
            float in=GachaArt.ease((float)((now-1-STAGGER*(i+1))/ENTER));
            if(in<=0)continue;
            var e=ENTRIES[i];var bx=menu.buttons().get(i);
            boolean off=e==Entry.RESET&&!canReset;
            button(d,e,(float)(bx.x()-(1-in)*h*.06+lit[i]*h*.008),(float)bx.y(),(float)bx.w(),(float)bx.h(),r,in*dim*(off?.5f:1),lit[i],off?unavailable(client):null,now,h);
        }
        if(ask>0)question(d,w,h,r,ask,now);
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();
    }

    /** The world behind, darkened into deep navy, a soft light behind the menu and specks of light drifting up. */
    private void backdrop(DrawContext d,int w,int h,double now,float shown){
        var m=d.getMatrices().peek().getPositionMatrix();
        var b=GachaArt.begin(d,false);
        GachaArt.fill(b,m,GachaArt.rect(0,0,w,h),GachaArt.fade(0x9a081a30,shown),GachaArt.fade(0xc4030a16,shown));
        GachaArt.end(b);
        var menu=PauseLayout.menu(w,h,ENTRIES.length);
        var top=menu.header();var last=menu.buttons().get(ENTRIES.length-1);
        b=GachaArt.begin(d,true);
        // a soft round light behind the menu, fading out far past it
        float cx=w/2f,cy=(float)(top.y()+last.bottom())/2,cw=(float)top.w(),ch=(float)(last.bottom()-top.y());
        float[] core=SkyUi.ellipse(cx,cy,cw*.3f,ch*.3f,48);
        int light=GachaArt.alpha(SkyUi.GLOW,30*shown);
        GachaArt.fill(b,m,core,light);
        GachaArt.band(b,m,core,Math.max(cw,ch)*.75f,light,GachaArt.alpha(SkyUi.GLOW,0));
        for(int i=0;i<26;i++){
            float sx=GachaArt.hash(i*7+1)*w,speed=.12f+.3f*GachaArt.hash(i*7+2),phase=GachaArt.hash(i*7+3)*(h+30);
            float y=(float)(h+15-((now*speed+phase)%(h+30)));
            float drift=(float)Math.sin(now*.03+i)*h*.01f;
            float twinkle=.5f+.5f*(float)Math.sin(now*(.05+.04*GachaArt.hash(i*7+4))+i*1.7);
            float size=(.5f+1.1f*GachaArt.hash(i*7+5))*h/365f;
            GachaArt.smooth(b,m,GachaArt.circle(sx+drift,y,size,10),GachaArt.fade(0xff9fe4ff,.55f*twinkle*shown),GachaArt.fade(0xff9fe4ff,.55f*twinkle*shown));
            GachaArt.glow(b,m,GachaArt.circle(sx+drift,y,size,10),size*3,SkyUi.GLOW,.25f*twinkle*shown);
        }
        GachaArt.end(b);
    }

    /** One button: the panel, the icon in its cell, the label, and its key (or why it cannot be used). */
    private void button(DrawContext d,Entry e,float x,float y,float w,float bh,float r,float a,float lit,String note,double now,int h){
        if(a<=.003f)return;
        var m=d.getMatrices().peek().getPositionMatrix();
        float cell=bh;
        var b=GachaArt.begin(d,false);
        SkyUi.panel(b,m,x,y,w,bh,r,lit,a);
        GachaArt.line(b,m,x+cell,y+bh*.2f,x+cell,y+bh*.8f,Math.max(SkyUi.px(),.9f),GachaArt.fade(SkyUi.ACCENT,a*.85f));
        SkyUi.icon(b,m,e.icon,x+cell/2,y+bh/2,bh*.48f,GachaArt.fade(GachaArt.mix(SkyUi.ACCENT,0xffffffff,lit),a));
        GachaArt.end(b);
        if(lit>.002f){b=GachaArt.begin(d,true);SkyUi.halo(b,m,x,y,w,bh,r,bh*.4f,.3f*lit*a);GachaArt.end(b);}
        float size=(float)(LABEL*h);
        var t=GachaFont.text(e.label,x+cell+bh*.4f,y+bh/2,size,GachaFont.Weight.BOLD)
            .color(GachaArt.mix(SkyUi.INK,SkyUi.INK_LIT,lit)).alpha(a).shadow(0x80000000,.5f,0,size*.06f);
        if(lit>.002f)t.glow(SkyUi.GLOW,size*.4f,.45f*lit);
        t.draw(d);
        float right=x+w-bh*.32f,ky=y+bh/2;
        if(note!=null&&!note.isEmpty())GachaFont.text(note,right,ky,(float)(KEY*h),GachaFont.Weight.MEDIUM).align(1).color(SkyUi.INK_DIM).alpha(a).draw(d);
        else if(e.key!=null&&note==null)SkyUi.keyCap(d,e.key,right,ky,(float)(KEY*h),a);
    }

    /** "정말 재설정 하시겠습니까?": the menu sinks back and the question rises in the middle. */
    private void question(DrawContext d,int w,int h,float r,float ask,double now){
        var m=d.getMatrices().peek().getPositionMatrix();
        var b=GachaArt.begin(d,false);GachaArt.fill(b,m,GachaArt.rect(0,0,w,h),GachaArt.fade(0xa8020812,ask));GachaArt.end(b);
        var dl=PauseLayout.dialog(w,h);var box=dl.box();
        float cx=(float)(box.x()+box.w()/2),cy=(float)(box.y()+box.h()/2),s=.94f+.06f*ask;
        var ms=d.getMatrices();ms.push();ms.translate(cx,cy,0);ms.scale(s,s,1);ms.translate(-cx,-cy,0);
        m=ms.peek().getPositionMatrix();
        float x=(float)box.x(),y=(float)box.y(),bw=(float)box.w(),bh=(float)box.h(),pad=(float)(PauseLayout.PAD*h);
        b=GachaArt.begin(d,true);SkyUi.halo(b,m,x,y,bw,bh,r*1.4f,h*.05f,.3f*ask);GachaArt.end(b);
        b=GachaArt.begin(d,false);
        // solid under the glass, so the menu behind does not show through the question
        float[] solid=SkyUi.rounded(x,y,bw,bh,r*1.4f);
        GachaArt.smooth(b,m,solid,GachaArt.fade(0xff0f2440,ask),GachaArt.fade(0xff081626,ask));
        SkyUi.panel(b,m,x,y,bw,bh,r*1.4f,.15f,ask);
        float iconY=y+pad+(float)(QUESTION*h)*.5f;
        SkyUi.icon(b,m,SkyUi.Icon.RESET,x+pad+(float)(QUESTION*h)*.55f,iconY,(float)(QUESTION*h)*1.1f,GachaArt.fade(SkyUi.ACCENT,ask));
        float lineY=iconY+(float)(QUESTION*h)*1.05f;
        GachaArt.line(b,m,x+pad,lineY,x+bw-pad,lineY,Math.max(SkyUi.px(),.8f),GachaArt.fade(0x668fdcff,ask),GachaArt.fade(0x088fdcff,ask));
        GachaArt.end(b);
        GachaFont.text("캐릭터 재설정",x+pad+(float)(QUESTION*h)*1.35f,iconY,(float)(LABEL*h),GachaFont.Weight.BOLD).color(SkyUi.INK).alpha(ask).draw(d);
        float qy=lineY+(float)(dl.confirm().y()-pad*.6f-lineY)*.42f;
        GachaFont.text(QUESTION_TEXT,cx,qy,(float)(QUESTION*h),GachaFont.Weight.BOLD).align(.5f)
            .gradient(0xffffffff,0xffbfeaff).alpha(ask).glow(SkyUi.GLOW,(float)(QUESTION*h)*.4f,.4f).shadow(0x80000000,.6f,0,(float)(QUESTION*h)*.06f).draw(d);
        GachaFont.text(NOTE_TEXT,cx,qy+(float)(QUESTION*h)*1.15f,(float)(NOTE*h),GachaFont.Weight.MEDIUM).align(.5f).color(SkyUi.INK_DIM).alpha(ask).draw(d);
        answer(d,dl.confirm(),"재설정","Enter",answer[0],ask,r,h);
        answer(d,dl.cancel(),"취소","Esc",answer[1],ask,r,h);
        ms.pop();
    }
    private void answer(DrawContext d,PauseLayout.Box box,String label,String key,float lit,float a,float r,int h){
        var m=d.getMatrices().peek().getPositionMatrix();
        float x=(float)box.x(),y=(float)box.y(),w=(float)box.w(),bh=(float)box.h();
        var b=GachaArt.begin(d,false);SkyUi.panel(b,m,x,y,w,bh,r,lit,a);GachaArt.end(b);
        if(lit>.002f){b=GachaArt.begin(d,true);SkyUi.halo(b,m,x,y,w,bh,r,bh*.4f,.3f*lit*a);GachaArt.end(b);}
        float size=(float)(LABEL*h),ks=(float)(KEY*h),kw=GachaFont.width(key,ks,GachaFont.Weight.BOLD)+ks*1.1f,gap=ks*.7f;
        float lw=GachaFont.width(label,size,GachaFont.Weight.BOLD),left=x+(w-(lw+gap+kw))/2;
        GachaFont.text(label,left,y+bh/2,size,GachaFont.Weight.BOLD).color(GachaArt.mix(SkyUi.INK,SkyUi.INK_LIT,lit)).alpha(a).shadow(0x80000000,.5f,0,size*.06f).draw(d);
        SkyUi.keyCap(d,key,left+lw+gap+kw,y+bh/2,ks,a);
    }
}
