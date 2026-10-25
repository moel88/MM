package dev.sangwoo.celestialfall.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Runs in GUI projection even with F1 enabled, before pause/chat screens. */
public final class CinematicHud {
    private CinematicHud() {}
    public static void render(float delta) {
        var mc=MinecraftClient.getInstance();
        if(!CinematicDirector.isActive())return;
        var c=new DrawContext(mc,mc.getBufferBuilders().getEntityVertexConsumers());
        // Star Platinum's beatdown (2.64.0): black bars sliding in from the top and the bottom as its cutscene begins
        if(dev.sangwoo.celestialfall.client.stand.StandBeatdown.cinematic()!=null)letterbox(mc,c,dev.sangwoo.celestialfall.client.stand.StandBeatdown.bars(delta));
        else if(StandClient.cinematic()!=null)KagenoRenderer.hud(c,delta);
        else if(HeavenClient.cinematic()!=null)HeavenHud.render(c,delta);
        else if(AtomicClient.cinematic()!=null)AtomicHud.render(c,delta);
        else CelestialHud.render(c,delta);
        c.draw();
    }
    /** Black bars {@code bar} of the screen's height each at the top and the bottom, to the window's own pixel (not the GUI's coarser one) as they slide. */
    static void letterbox(MinecraftClient mc,DrawContext c,float bar){
        if(bar<=0)return;
        var window=mc.getWindow();int w=window.getFramebufferWidth(),h=window.getFramebufferHeight();
        int px=Math.round(bar*h);if(px<=0)return;
        float k=(float)(1/window.getScaleFactor());
        var m=c.getMatrices();m.push();m.scale(k,k,1);
        c.fill(0,0,w,px,0xff000000);c.fill(0,h-px,w,h,0xff000000);
        m.pop();
    }
}
