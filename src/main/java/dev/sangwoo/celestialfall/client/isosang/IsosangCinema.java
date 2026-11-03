package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.minecraft.util.math.Vec3d;

/**
 * Lee So-sang's G's cutscene as the camera and the fog see it (2.83.0): its user's own camera's shots (FallingSword.shot)
 * and the dark round their hand. Public, for CinematicDirector and the fog's mixin.
 */
public final class IsosangCinema {
    private IsosangCinema(){}
    /** Whether the local player's camera is in their own G's cutscene now. */
    public static boolean active(){return FallingSword.cinematic()!=null;}
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){return FallingSword.camera(vanilla,yaw,pitch,delta);}
    public static double fov(double vanilla,float delta){return FallingSword.fov(vanilla,delta);}
    public static float roll(float delta){return FallingSword.roll(delta);}
    /** How dark the world is for the local player's camera (0 to 1): black round their hand in their G's first shots. */
    public static float dark(float delta){return FallingSword.dark(delta);}
    /** How far the frame of the local player's G's cutscene has dimmed as it ends (0 to 1). */
    public static float dim(float delta){return FallingSword.dim(delta);}
    /** How thick G's dust is round the camera (0 to 1): a beige haze over everything near as the fire leaps up. */
    public static float dust(float delta){return FallingSword.dust(delta);}
}
