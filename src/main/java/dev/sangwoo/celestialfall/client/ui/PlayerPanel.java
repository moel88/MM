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
 * F3. Only on the Botand map; elsewhere Minecraft's list is left alone.
 */
public final class PlayerPanel {
    /** The panel's measures, as shares of the screen's height. */
    static final double MARGIN=.024,WIDTH=.4,HEADER=.078,ROW=.064,GAP=.008,TITLE=.04,NAME=.031,FACE=.04,TALL=.62;
    private static final PlayerPanelMotion MOTION=new PlayerPanelMotion();
    private static long ticks;
    /** Whether Tab was down at the last tick. */
    private static boolean held;
    /** The panel's width and margin on the last frame drawn, which the status effect icons keep clear of. */
    private static double span;
    private PlayerPanel(){}

    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(PlayerPanel::tick);
        HudRenderCallback.EVENT.register(PlayerPanel::render);
        ClientPlayConnectionEvents.JOIN.register((handler,sender,mc)->MOTION.reset());
    }
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

    /** The panel's place on a screen this size with this many rows (and the widest name): left, top, width and rows shown. */
    record Layout(double x,double y,double width,double header,double row,double gap,int rows,boolean more){
        double rowTop(int i){return y+header+gap+i*(row+gap);}
    }
    static Layout layout(int w,int h,int players,double widest){
        double header=HEADER*h,row=ROW*h,gap=GAP*h,cell=row;
        double width=Math.min(Math.max(WIDTH*h,cell+widest+row*.9),w*.45);
        // as many boxes as fit in its share of the height; when some are left over, the last box counts them
        int fit=Math.max(2,(int)Math.floor((TALL*h-header)/(row+gap)));
        boolean more=players>fit;int rows=more?fit-1:players;
        return new Layout(w-MARGIN*h-width,MARGIN*h,width,header,row,gap,rows,more);
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
            .sorted(Comparator.<PlayerListEntry>comparingInt(e->e.getProfile().getId().equals(me)?0:1)
                .thenComparingInt(e->e.getGameMode()==GameMode.SPECTATOR?1:0)
                .thenComparing(e->e.getProfile().getName(),String::compareToIgnoreCase))
            .limit(80).toList();
    }

    private static void render(DrawContext d,float delta){
        var mc=MinecraftClient.getInstance();
        if(!visible(mc))return;
        double now=clock(),progress=MOTION.progress(now);
        if(progress>=1)return;
        int w=d.getScaledWindowWidth(),h=d.getScaledWindowHeight();
        var players=entries(mc);
        float nameSize=(float)(NAME*h);double widest=0;
        for(var e:players)widest=Math.max(widest,GachaFont.width(e.getProfile().getName(),nameSize,GachaFont.Weight.BOLD));
        var l=layout(w,h,players.size(),widest);
        span=l.width()+MARGIN*h;
        int boxes=1+l.rows()+(l.more()?1:0);
        // each box slides out past the screen's right edge
        double away=w-l.x()+4;
        float r=(float)Math.max(2,l.row()*.14);
        for(int i=0;i<boxes;i++){
            double out=PlayerPanelMotion.box(progress,i,boxes);
            if(out>=1)continue;
            float a=(float)(1-Math.max(0,(out-.7)/.3));
            float x=(float)(l.x()+away*out);
            if(i==0)SkyUi.header(d,x,(float)l.y(),(float)l.width(),(float)l.header(),r,a,now,"플레이어",(float)(TITLE*h));
            else if(i<=l.rows())row(d,players.get(i-1),i==1,x,(float)l.rowTop(i-1),(float)l.width(),(float)l.row(),r,a,h);
            else more(d,players.size()-l.rows(),x,(float)l.rowTop(i-1),(float)l.width(),(float)l.row(),r,a,h);
        }
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();
    }
    private static void row(DrawContext d,PlayerListEntry e,boolean me,float x,float y,float w,float rh,float r,float a,int h){
        var m=d.getMatrices().peek().getPositionMatrix();
        boolean spectator=e.getGameMode()==GameMode.SPECTATOR;
        float lit=me?.55f:0,cell=rh;
        var b=GachaArt.begin(d,false);
        SkyUi.panel(b,m,x,y,w,rh,r,lit,a);
        // the divider after the face cell
        GachaArt.line(b,m,x+cell,y+rh*.2f,x+cell,y+rh*.8f,Math.max(SkyUi.px(),.9f),GachaArt.fade(SkyUi.ACCENT,a*.85f));
        GachaArt.end(b);
        if(me){b=GachaArt.begin(d,true);SkyUi.halo(b,m,x,y,w,rh,r,rh*.35f,.28f*a);GachaArt.end(b);}
        // the face (and hat layer), placed at the row's own fraction of a pixel so it slides with it
        int face=Math.max(4,(int)Math.round(FACE*h));
        d.getMatrices().push();d.getMatrices().translate(x+(cell-face)/2,y+(rh-face)/2,0);
        RenderSystem.enableBlend();d.setShaderColor(1,1,1,a*(spectator?.6f:1));
        PlayerSkinDrawer.draw(d,e.getSkinTexture(),0,0,face);
        d.setShaderColor(1,1,1,1);d.getMatrices().pop();
        float size=(float)(NAME*h);
        var t=GachaFont.text(e.getProfile().getName(),x+cell+rh*.38f,y+rh/2,size,GachaFont.Weight.BOLD)
            .color(me?SkyUi.INK_LIT:spectator?SkyUi.INK_DIM:SkyUi.INK).alpha(a).shadow(0x80000000,.5f,0,size*.06f);
        if(me)t.glow(0xff3fb8ff,size*.4f,.45f);
        t.draw(d);
    }
    private static void more(DrawContext d,int count,float x,float y,float w,float rh,float r,float a,int h){
        var m=d.getMatrices().peek().getPositionMatrix();
        var b=GachaArt.begin(d,false);SkyUi.panel(b,m,x,y,w,rh,r,0,a*.85f);GachaArt.end(b);
        GachaFont.text("외 "+count+"명",x+w/2,y+rh/2,(float)(NAME*h),GachaFont.Weight.BOLD).align(.5f).color(SkyUi.INK_DIM).alpha(a).draw(d);
    }
}
