package dev.sangwoo.celestialfall.client;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Immutable-per-frame culling seed for already-loaded chunks; never used as the view camera. */
public final class HeavenTerrainCamera extends Camera {
    public HeavenTerrainCamera(Camera view,Vec3d loadedOrigin) {
        setPos(loadedOrigin);setRotation(view.getYaw(),view.getPitch());
    }
}
