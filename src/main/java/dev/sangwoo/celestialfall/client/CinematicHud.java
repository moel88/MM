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
        if(StandClient.cinematic()!=null)KagenoRenderer.hud(c,delta);
        else if(HeavenClient.cinematic()!=null)HeavenHud.render(c,delta);
        else if(AtomicClient.cinematic()!=null)AtomicHud.render(c,delta);
        else CelestialHud.render(c,delta);
        c.draw();
    }
}
