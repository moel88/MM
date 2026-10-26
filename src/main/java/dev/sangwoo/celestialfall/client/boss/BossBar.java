package dev.sangwoo.celestialfall.client.boss;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.gacha.GachaArt;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;
import dev.sangwoo.celestialfall.client.ui.SkyUi;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

/**
 * The boss bar (2.69.0, user request: "보스 체력바는 가운데 아래 뜨게 체력 숫자는 안 보여줘도 되고 막대와 이름만"): the
 * nearest boss within its range has its name and a bar of its health at the bottom of the screen, in the middle,
 * just above the hotbar; no numbers. The bar follows the health quickly, and what a blow took lingers a moment as a
 * pale part that then drains away, so every blow reads. It comes in and goes out softly as one comes within the boss's
 * range or leaves it (and a moment after the boss falls). Every movement is a function of the bar's own clock (client
 * ticks and the frame between), so it is smooth at any frame rate.
 */
public final class BossBar {
    /** Seconds the pale part a blow took stays before it drains, and how fast it drains (share of the bar a second). */
    static final double TRAIL_HOLD=.45,TRAIL_DRAIN=.55;
    /** Seconds to come in or go out. */
    static final double FADE=.3;
    private static long ticks;
    private static UUID shown;
    private static double last=-1,lastDrop=-1e9;
    private static float alpha,bar=1,trail=1,actual=1,angry;
    private BossBar(){}
    static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(mc->ticks++);}

    /** The boss whose bar shows: the nearest within its range, alive or just fallen. */
    private static PlayerEntity pick(MinecraftClient mc){
        PlayerEntity best=null;double near=Double.MAX_VALUE;
        for(var info:BossClient.all()){
            var e=mc.world.getPlayerByUuid(info.id());if(e==null)continue;
            double d=e.squaredDistanceTo(mc.player);
            if(d>info.range()*info.range()||d>=near||!e.isAlive()&&e.deathTime>30)continue;
            near=d;best=e;
        }
        return best;
    }
    public static void render(DrawContext d,float delta){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null||!MapClient.enabled()){shown=null;alpha=0;return;}
        double now=ticks+delta,dt=last<0?0:Math.max(0,Math.min(.25,(now-last)/20));last=now;
        var boss=mc.options.hudHidden?null:pick(mc);
        if(boss!=null&&!boss.getUuid().equals(shown)){
            // another boss (or the first): its own health at once
            shown=boss.getUuid();actual=bar=trail=health(boss);if(alpha>.5f)alpha=.5f;
        }
        alpha=(float)Math.max(0,Math.min(1,alpha+(boss!=null?1:-1)*dt/FADE));
        if(alpha<=.001f){if(boss==null)shown=null;return;}
        var info=shown==null?null:BossClient.info(shown);
        if(boss!=null){
            float h=health(boss);
            if(h<actual-1e-4f)lastDrop=now;
            actual=h;
            if(info!=null)angry+=(float)((info.angry()?1:0)-angry)*(float)Math.min(1,dt*6);
        }
        bar+=(actual-bar)*(float)Math.min(1,dt*16);
        if(trail<bar)trail=bar;
        else if((now-lastDrop)/20>TRAIL_HOLD)trail=(float)Math.max(bar,trail-TRAIL_DRAIN*dt);
        draw(d,info==null?"":info.title(),now);
    }
    private static float health(PlayerEntity e){return e.isAlive()?Math.max(0,Math.min(1,e.getHealth()/Math.max(1,e.getMaxHealth()))):0;}

    private static void draw(DrawContext d,String title,double now){
        int w=d.getScaledWindowWidth(),h=d.getScaledWindowHeight();
        float bw=(float)Math.max(150,Math.min(300,w*.42)),bh=5.5f,x=(w-bw)/2f,y=h-34f;
        float a=(float)(alpha*alpha*(3-2*alpha));
        var m=d.getMatrices().peek().getPositionMatrix();
        float r=bh/2;
        var b=GachaArt.begin(d,false);
        // the backing, a little larger than the bar
        float[] back=SkyUi.rounded(x-1.6f,y-1.6f,bw+3.2f,bh+3.2f,r+1.6f);
        GachaArt.smooth(b,m,back,GachaArt.fade(0xd80b0d14,a),GachaArt.fade(0xd8191b26,a));
        // what a blow took, pale, draining; then the health, deep red, lighter along its top
        if(trail>bar+1e-3f)fillTo(b,m,x,y,bw,bh,r,bar,trail,GachaArt.fade(0xfff6d2c8,a*.95f),GachaArt.fade(0xffe8a99c,a*.95f));
        int top=GachaArt.mix(0xffd04a4a,0xffee5b4e,angry),bottom=GachaArt.mix(0xff8a1f26,0xffa8222b,angry);
        if(bar>1e-3f)fillTo(b,m,x,y,bw,bh,r,0,bar,GachaArt.fade(top,a),GachaArt.fade(bottom,a));
        if(bar>1e-3f)fillTo(b,m,x,y+.6f,bw,Math.max(.6f,bh*.28f),Math.max(.3f,r*.28f),.004f,Math.max(.004f,bar-.004f),GachaArt.fade(0x55ffffff,a),GachaArt.fade(0x10ffffff,a));
        GachaArt.stroke(b,m,back,Math.max(SkyUi.px(),.7f),GachaArt.fade(0xb0c9a59a,a));
        GachaArt.end(b);
        // a soft red light round it while the boss is angry
        if(angry>.01f){var g=GachaArt.begin(d,true);GachaArt.glow(g,m,back,5,GachaArt.fade(0xffe0443c,a),.32f*angry);GachaArt.end(g);}
        if(!title.isEmpty())GachaFont.text(title,w/2f,y-7.6f,9.5f,GachaFont.Weight.BOLD).align(.5f).color(0xfffaf6f2).alpha(a)
            .shadow(0xc8000000,1.1f,0,.6f).spacing(.04f).draw(d);
        RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
    }
    /** The part of a bar ({@code x}, {@code y}, {@code w}, {@code h}, rounded {@code r}) from {@code from} to {@code to} of its length. */
    private static void fillTo(net.minecraft.client.render.BufferBuilder b,org.joml.Matrix4f m,float x,float y,float w,float h,float r,float from,float to,int top,int bottom){
        float x0=x+w*from,x1=x+w*to;if(x1-x0<.05f)return;
        // rounded at the bar's own ends only: inside it the cut is straight
        float[] shape=SkyUi.rounded(x,y,w,h,r),cut=GachaArt.rect(x0,y-1,x1-x0,h+2);
        float[] part=GachaArt.clipTo(shape,cut);
        if(part.length>=6)GachaArt.smooth(b,m,part,top,bottom);
    }
}
