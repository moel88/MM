package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.minecraft.util.math.Vec3d;

/**
 * Lee So-sang's G's cutscene as the camera and the fog see it (2.83.0): its user's own camera's shots (JadeTablet.shot)
 * and the dark round their hand. Public, for CinematicDirector and the fog's mixin.
 */
public final class IsosangCinema {
    private IsosangCinema(){}
    /** Whether the local player's camera is in their own G's cutscene now. */
    public static boolean active(){return JadeTablet.cinematic()!=null;}
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){return JadeTablet.camera(vanilla,yaw,pitch,delta);}
    public static double fov(double vanilla,float delta){return JadeTablet.fov(vanilla,delta);}
    public static float roll(float delta){return JadeTablet.roll(delta);}
    /** How dark the world is for the local player's camera (0 to 1): black round their hand in their G's first shots. */
    public static float dark(float delta){return JadeTablet.dark(delta);}
}
