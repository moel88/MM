package dev.sangwoo.celestialfall.client.integration;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.fabric.event.FabricVeilPostProcessingEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Code-authored Veil post pipeline. Only an explicit authoring preview activates it;
 * existing skill shaders, camera paths and impact timing remain their current owners. */
public final class VeilBridge {
    public static final Identifier PREVIEW = CelestialFall.id("veil_preview");
    private static int ticksLeft;
    private static long renderedFrames;
    private VeilBridge() {}

    private static PostProcessingManager manager() {
        return VeilRenderSystem.renderer().getPostProcessingManager();
    }
    public static boolean ready() {
        var shader = VeilRenderSystem.renderer().getShaderManager().getShader(PREVIEW);
        return manager().getPipeline(PREVIEW) != null && shader != null
            && shader.getShaders().containsKey(org.lwjgl.opengl.GL20.GL_VERTEX_SHADER)
            && shader.getShaders().containsKey(org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER)
            && org.lwjgl.opengl.GL20.glGetProgrami(shader.getProgram(), org.lwjgl.opengl.GL20.GL_LINK_STATUS) != 0;
    }
    public static boolean active() { return manager().isActive(PREVIEW); }
    public static long renderedFrames() { return renderedFrames; }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (ticksLeft > 0 && (--ticksLeft == 0 || mc.world == null || CinematicDirector.isActive())) stop();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(VeilBridge::stop));
        FabricVeilPostProcessingEvent.POST.register((id, pipeline, context) -> {
            if (PREVIEW.equals(id)) renderedFrames++;
        });
    }
    /** Eight-second local preview, usable alongside an Immersive Cinematics shot. */
    public static boolean preview() {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null || CinematicDirector.isActive() || !ready()) return false;
        ticksLeft = 160;
        manager().add(PREVIEW);
        return true;
    }
    public static void stop() { ticksLeft = 0; manager().remove(PREVIEW); }
}
