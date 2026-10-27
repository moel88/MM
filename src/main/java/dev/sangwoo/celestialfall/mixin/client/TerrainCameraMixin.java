package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.HeavenClient;
import dev.sangwoo.celestialfall.client.HeavenTerrainCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla terrain setup for the wide cameras (H's cinematic, the title's map camera). Sodium replaces this method
 * with its own renderer, which builds its visibility from the camera itself, so this mixin is left out when Sodium is
 * installed ({@link ClientMixinPlugin}).
 */
@Mixin(WorldRenderer.class)
public abstract class TerrainCameraMixin {
    @ModifyVariable(method="setupTerrain",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Camera celestialfall$loadedTerrain(Camera view) {
        var mc=MinecraftClient.getInstance();
        // Vanilla keeps chunk storage around the player. Seed traversal there as well,
        // while the actual view/projection and supplied frustum remain at the wide camera.
        return (HeavenClient.wideCinematic()||dev.sangwoo.celestialfall.client.title.TitleMapCamera.active()) && mc.player!=null?new HeavenTerrainCamera(view,mc.player.getEyePos()):view;
    }
    @Redirect(method="setupTerrain",at=@At(value="FIELD",target="Lnet/minecraft/client/MinecraftClient;chunkCullingEnabled:Z"))
    private boolean celestialfall$wideOcclusion(MinecraftClient mc) {
        // Occlusion from the player's position is not valid for a detached camera.
        return !HeavenClient.wideCinematic() && !dev.sangwoo.celestialfall.client.title.TitleMapCamera.active() && mc.chunkCullingEnabled;
    }
}
