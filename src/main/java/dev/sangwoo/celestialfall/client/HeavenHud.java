package dev.sangwoo.celestialfall.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Cinema bars only. The complete scene is rendered as world-space 3D geometry. */
public final class HeavenHud {
    private HeavenHud() {}
    public static void render(DrawContext c,float delta) {
        var mc=MinecraftClient.getInstance();float blend=HeavenClient.blend(delta);
        if(blend<=0 || mc.options.hudHidden)return;
        int w=mc.getWindow().getScaledWidth(),h=mc.getWindow().getScaledHeight();
        int bar=(int)(h*.065*blend);
        c.fill(0,0,w,bar,0xFF020407);c.fill(0,h-bar,w,h,0xFF020407);
    }
}
