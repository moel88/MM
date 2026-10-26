package dev.sangwoo.celestialfall.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.gacha.GachaArt;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;
import dev.sangwoo.celestialfall.client.MapClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.GameMode;
import java.util.Comparator;
import java.util.List;

/**
 * The player list (2.45.0, user request after a Roblox screenshot: Minecraft's Tab list is gone; at the top right a
 * header "플레이어" with a faint emblem over a framed row per player, in sky blue). It shows on joining; Tab slides it
 * off to the right and Tab again slides it back ({@link PlayerPanelMotion}). Your own row comes first and is lit; each
 * row has the player's face in its left cell. While it shows, the status effect icons sit just left of it. It is part
 * of the HUD, so it stays (dimmed) behind the pause menu and other screens like the hotbar does, and goes with F1 and
 * F3. Only on the Botand map; elsewhere Minecraft's list is left alone. 2.73.0 (user request: "플레이창 UI는 사이즈도
 * 줄이고 UI를 더 하늘색에 가깝게 바꿔주고 디자인도 전체적으로 갈아엎어서 리메이크 해줘 모래시계와 관련 있게", then "모래시계가
 * 너무 대놓고 들어나있어 은연중에 들어나있게", "이거 처럼 색깔을 2개 쓰는 것도 좋을듯 메인은 화이트로 하되 테두리나 장식 같은걸
 * 하늘색으로", "가로폭은 너무 작고 세로 폭이 너무 커", "너무 하야니까 안 어울린다 어두은 파랑에 하늘색 조합으로 만들자", "최대
 * 5명만 보이고 나머지는 스크롤 내려야 보이게"): two colours, dark-blue panes with sky-blue rims, lines and names, the hourglass
 * only a faint outline in the header (HourglassUi); a little wider and much lower than it was (rows about eleven times as
 * wide as they are high); a thin sky-blue line between each face and name as in the reference. At most five rows show
 * (PlayerPanelScroll): with more players a thin bar at the right shows where the rows shown are among them all, and the
 * mouse wheel over the list (the cursor is free on the map, as in Roblox; with the chat open too) scrolls it a row a
 * notch instead of zooming the camera. Its slide is the same.
 */
public final class PlayerPanel {
    /**
     * The panel's measures, as shares of the screen's height: its margin, its least width, the header's and each row's
     * height, the space between, the title's, a name's and a face's size, and how far down it may reach (2.73.0: wider
     * and lower; until 2.72.0 .024, .4, .078, .064, .008, .04, .031, .04).
     */
    static final double MARGIN=.022,WIDTH=.42,HEADER=.046,ROW=.036,GAP=.006,TITLE=.025,NAME=.0205,FACE=.025,TALL=.62;
    /** The scrollbar's width and the space before it, as shares of the screen's height. */
    static final double BAR=.0065,BAR_GAP=.006;
    private static final PlayerPanelMotion MOTION=new PlayerPanelMotion();
    private static final PlayerPanelScroll SCROLL=new PlayerPanelScroll();
    /** The list as drawn on the last frame (GUI units), for the wheel: its edges, how many are listed and shown; none while away. */
    private static double hitX0,hitY0,hitX1,hitY1;private static int listed,shownRows;private static boolean hit;
    private static long ticks;
    /** Whether Tab was down at the last tick. */
    private static boolean held;
    /** The panel's width and margin on the last frame drawn, which the status effect icons keep clear of. */
    private static double span;
    private PlayerPanel(){}

    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(PlayerPanel::tick);
        HudRenderCallback.EVENT.register(PlayerPanel::render);
        ClientPlayConnectionEvents.JOIN.register((handler,sender,mc)->{MOTION.reset();SCROLL.reset();});
    }
    /**
     * The mouse wheel turned by {@code notches} (2.73.0): whether the player list takes it. It does with the cursor free
     * over the list as drawn on the last frame, mostly shown, while more players are listed than show.
     */
    public static boolean wheel(double notches){
        var mc=MinecraftClient.getInstance();
        if(!hit||!visible(mc)||mc.mouse.isCursorLocked())return false;
        var window=mc.getWindow();if(window.getWidth()<=0||window.getHeight()<=0)return false;
        double x=mc.mouse.getX()*window.getScaledWidth()/window.getWidth(),y=mc.mouse.getY()*window.getScaledHeight()/window.getHeight();
        if(x<hitX0||x>hitX1||y<hitY0||y>hitY1)return false;
        return SCROLL.wheel(notches,listed,shownRows,clock());
    }
    /** The list's edges on the last frame (GUI units: left, top, right, bottom), or null while it is not there. */
    public static double[] area(){return hit?new double[]{hitX0,hitY0,hitX1,hitY1}:null;}
    /** The first row shown now (a fraction while it scrolls) and the row it is going to. */
    public static double[] scroll(){return new double[]{SCROLL.first(clock()),SCROLL.target()};}
    /** Whether Tab has sent the panel away. */
    public static boolean hidden(){return MOTION.hidden();}
    /** Whether the panel stands in for Minecraft's Tab list (on the Botand map). */
    public static boolean replaces(){return MapClient.enabled();}
    /** The HUD's clock on this frame: its own ticks and the share of the next (which runs on in a paused game). */
    static double clock(){return ticks+MinecraftClient.getInstance().getTickDelta();}

    private static void tick(MinecraftClient mc){
        ticks++;
        // Tab turns the panel round once a press: a key held down repeats its presses, which do not (Minecraft's list
        // was held open, so a player may hold it). A tap within one tick still counts. Presses off the map are dropped,
        // so none waits to turn it on arrival.
        int presses=0;while(mc.options.playerListKey.wasPressed())presses++;
        boolean down=mc.options.playerListKey.isPressed();
        if(replaces()&&turns(held,down,presses))MOTION.toggle(ticks);
        held=down;
    }
    /** Whether Tab turns the panel this tick: it was up at the last tick and is down now, or was tapped since. */
    static boolean turns(boolean wasDown,boolean down,int presses){return !wasDown&&(down||presses>0);}

    /**
     * The panel's place on a screen this size with this many players (and the widest name): left, top, width, the
     * header's and a row's height, the space between, the rows shown and whether it scrolls (more players than rows).
     */
    record Layout(double x,double y,double width,double header,double row,double gap,int rows,boolean scrolls){
        double rowTop(int i){return y+header+gap+i*(row+gap);}
        /** The bottom of the last row shown. */
        double bottom(){return rowTop(rows-1)+row;}
    }
    static Layout layout(int w,int h,int players,double widest){
        double header=HEADER*h,row=ROW*h,gap=GAP*h,cell=row;
        // five rows at most, fewer if its share of the height holds fewer; the rest are scrolled to
        int fit=Math.max(1,(int)Math.floor((TALL*h-header)/(row+gap)));
        int shown=Math.min(PlayerPanelScroll.SHOWN,fit),rows=Math.max(1,Math.min(players,shown));
        boolean scrolls=players>shown;
        double width=Math.min(Math.max(WIDTH*h,cell+widest+row*1.1+(scrolls?(BAR+BAR_GAP)*h:0)),w*.45);
        return new Layout(w-MARGIN*h-width,MARGIN*h,width,header,row,gap,rows,scrolls);
    }
    /**
     * How far the status effect icons move left to clear the panel, easing back as the header slides away (none once it
     * has gone). The icons are drawn before the panel, so this is the last frame's width: one frame late only when a
     * name that changes the width comes or goes.
     */
    public static double effectShift(){
        var mc=MinecraftClient.getInstance();
        if(!visible(mc))return 0;
        return span*(1-PlayerPanelMotion.box(MOTION.progress(clock()),0,1));
    }
    private static boolean visible(MinecraftClient mc){return replaces()&&mc.player!=null&&mc.world!=null&&!mc.options.hudHidden&&!mc.options.debugEnabled;}

    /** Everyone listed, you first, then players before spectators, by name. */
    static List<PlayerListEntry> entries(MinecraftClient mc){
        var me=mc.player.getUuid();
        return mc.player.networkHandler.getListedPlayerListEntries().stream()
            // (no boss: nobody plays it, 2.69.0)
            .filter(e->!dev.sangwoo.celestialfall.client.boss.BossClient.isBoss(e.getProfile().getId()))
            .sorted(Comparator.<PlayerListEntry>comparingInt(e->e.getProfile().getId().equals(me)?0:1)
                .thenComparingInt(e->e.getGameMode()==GameMode.SPECTATOR?1:0)
                .thenComparing(e->e.getProfile().getName(),String::compareToIgnoreCase))
            .limit(80).toList();
    }

    private static void render(DrawContext d,float delta){
        var mc=MinecraftClient.getInstance();hit=false;
        if(!visible(mc))return;
        double now=clock(),progress=MOTION.progress(now);
        if(progress>=1)return;
        int w=d.getScaledWindowWidth(),h=d.getScaledWindowHeight();
        var players=entries(mc);
        float nameSize=(float)(NAME*h);double widest=0;
        for(var e:players)widest=Math.max(widest,GachaFont.width(e.getProfile().getName(),nameSize,GachaFont.Weight.BOLD));
        var l=layout(w,h,players.size(),widest);
        span=l.width()+MARGIN*h;
        SCROLL.fit(players.size(),l.rows(),now);
        double first=l.scrolls()?SCROLL.first(now):0;
        int boxes=1+l.rows();
        // each box slides out past the screen's right edge
        double away=w-l.x()+4;
        float r=(float)Math.max(1.5,l.row()*.22),rowWidth=(float)(l.width()-(l.scrolls()?(BAR+BAR_GAP)*h:0));
        double out=PlayerPanelMotion.box(progress,0,boxes);
        if(out<1)HourglassUi.header(d,(float)(l.x()+away*out),(float)l.y(),(float)l.width(),(float)l.header(),(float)l.header()*.26f,fade(out),now,"플레이어",(float)(TITLE*h));
        // the rows shown, cut under the header and below the last row's place while they scroll (with room for the
        // light round your own row)
        float top=(float)(l.y()+l.header()),bottom=(float)l.bottom();
        SkyUi.clip(0,top,w,bottom+(float)l.gap()-top);
        for(int j=Math.max(0,(int)Math.floor(first));j<players.size()&&j<first+l.rows();j++){
            double slot=j-first;
            out=slide(progress,1+Math.max(0,Math.min(l.rows()-1,slot)),boxes);
            if(out>=1)continue;
            float y=(float)(l.rowTop(0)+slot*(l.row()+l.gap()));
            row(d,players.get(j),j==0,(float)(l.x()+away*out),y,rowWidth,(float)l.row(),r,fade(out),h);
        }
        SkyUi.unclip();
        if(l.scrolls())bar(d,l,first,players.size(),progress,boxes,away,h);
        // the wheel scrolls it while it is mostly shown
        hit=l.scrolls()&&progress<.5;hitX0=l.x();hitY0=l.y();hitX1=l.x()+l.width();hitY1=bottom;listed=players.size();shownRows=l.rows();
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();
    }
    /** How far box {@code index} (fractional between two boxes, while the rows scroll) of {@code boxes} has gone out. */
    private static double slide(double progress,double index,int boxes){
        int a=(int)Math.floor(index),b=Math.min(boxes-1,a+1);double f=index-a;
        return PlayerPanelMotion.box(progress,a,boxes)*(1-f)+PlayerPanelMotion.box(progress,b,boxes)*f;
    }
    /** A box fading as it leaves the screen. */
    private static float fade(double out){return (float)(1-Math.max(0,(out-.7)/.3));}
    /** The scrollbar: a faint track along the rows shown and, on it, a thumb as long as their share of everyone, where they are. */
    private static void bar(DrawContext d,Layout l,double first,int players,double progress,int boxes,double away,int h){
        double out=slide(progress,1+(l.rows()-1)/2.0,boxes);if(out>=1)return;
        float a=fade(out),bw=(float)(BAR*h),x=(float)(l.x()+l.width()-bw+away*out),top=(float)l.rowTop(0),track=(float)(l.bottom()-l.rowTop(0));
        float thumb=Math.max(bw*2,track*l.rows()/players),y=top+(track-thumb)*(float)(first/Math.max(1,PlayerPanelScroll.most(players,l.rows())));
        var m=d.getMatrices().peek().getPositionMatrix();
        var b=GachaArt.begin(d,false);
        GachaArt.smooth(b,m,SkyUi.rounded(x,top,bw,track,bw/2),GachaArt.fade(HourglassUi.PANE_TOP,a),GachaArt.fade(HourglassUi.PANE_BOTTOM,a));
        GachaArt.stroke(b,m,SkyUi.rounded(x,top,bw,track,bw/2),Math.max(SkyUi.px(),.5f),GachaArt.fade(HourglassUi.SKY,a*.45f));
        GachaArt.smooth(b,m,SkyUi.rounded(x,y,bw,thumb,bw/2),GachaArt.fade(HourglassUi.SKY,a),GachaArt.fade(HourglassUi.SKY,a*.85f));
        GachaArt.end(b);
    }
    private static void row(DrawContext d,PlayerListEntry e,boolean me,float x,float y,float w,float rh,float r,float a,int h){
        var m=d.getMatrices().peek().getPositionMatrix();
        boolean spectator=e.getGameMode()==GameMode.SPECTATOR;
        float cell=rh,dim=spectator?.7f:1;
        int face=Math.max(4,(int)Math.round(FACE*h));
        float fx=x+(cell-face)/2,fy=y+(rh-face)/2,pad=Math.max(SkyUi.px(),face*.09f),line=HourglassUi.rim();
        var b=GachaArt.begin(d,false);
        HourglassUi.pane(b,m,x,y,w,rh,r,me?1:0,a*dim);
        // the face's thin frame, and the line between the face and the name (the reference's divider)
        GachaArt.stroke(b,m,SkyUi.rounded(fx-pad,fy-pad,face+2*pad,face+2*pad,pad),Math.max(SkyUi.px(),line*.75f),GachaArt.fade(HourglassUi.SKY,a*.9f*dim));
        GachaArt.line(b,m,x+cell+line,y+rh*.2f,x+cell+line,y+rh*.8f,line,GachaArt.fade(HourglassUi.SKY,a*dim));
        GachaArt.end(b);
        // your own row: a soft sky-blue light round it
        if(me){b=GachaArt.begin(d,true);GachaArt.glow(b,m,SkyUi.rounded(x,y,w,rh,r),rh*.35f,HourglassUi.GLOW,.3f*a);GachaArt.end(b);}
        // the face (and hat layer), placed at the row's own fraction of a pixel so it slides with it
        d.getMatrices().push();d.getMatrices().translate(fx,fy,0);
        RenderSystem.enableBlend();d.setShaderColor(1,1,1,a*(spectator?.6f:1));
        PlayerSkinDrawer.draw(d,e.getSkinTexture(),0,0,face);
        d.setShaderColor(1,1,1,1);d.getMatrices().pop();
        float size=(float)(NAME*h);
        GachaFont.text(e.getProfile().getName(),x+cell+rh*.45f,y+rh/2,size,GachaFont.Weight.BOLD)
            .color(spectator?HourglassUi.INK_DIM:HourglassUi.INK).alpha(a*dim).draw(d);
    }
}
