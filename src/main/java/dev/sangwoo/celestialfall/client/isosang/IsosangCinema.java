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
    public static boolean active(){return FallingSword.cinematic()!=null||SwordFinale.cinematic()!=null;}
    /** (2.101.0) H again's cutscene (hers alone) comes after G's: the two never run together. */
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){return FallingSword.cinematic()==null&&SwordFinale.cinematic()!=null?SwordFinale.camera(vanilla,yaw,pitch,delta):FallingSword.camera(vanilla,yaw,pitch,delta);}
    public static double fov(double vanilla,float delta){return FallingSword.cinematic()==null&&SwordFinale.cinematic()!=null?SwordFinale.fov(vanilla,delta):FallingSword.fov(vanilla,delta);}
    public static float roll(float delta){return FallingSword.cinematic()==null&&SwordFinale.cinematic()!=null?SwordFinale.roll(delta):FallingSword.roll(delta);}
    /** (2.101.0) How deep H again's dusk is for the local player's camera (0 to 1): its cutscene's red dusk, the world darker into the distance. */
    public static float dusk(float delta){return SwordFinale.duskNow(delta);}
    /** How dark the world is for the local player's camera (0 to 1): black round their hand in their G's first shots. */
    public static float dark(float delta){return FallingSword.dark(delta);}
    /** How far the frame of the local player's G's cutscene has dimmed as it ends (0 to 1). */
    public static float dim(float delta){return FallingSword.dim(delta);}
    /** How thick G's dust is round the camera (0 to 1): a beige haze over everything near as the fire leaps up. */
    public static float dust(float delta){return FallingSword.dust(delta);}
    /** The black bar at the top and the bottom of the cutscene's frame (a share of the screen's height each). */
    public static float bars(){return FallingSword.cinematic()==null&&SwordFinale.cinematic()!=null?0:(float)(1-FallingSword.frameHeight())/2f;}
    /** Turns the finished world back left for right in the shots of her hand (GMirror; before the GUI draws). */
    public static void mirror(float delta){GMirror.apply(delta);}
    /** (2.101.0) Whether {@code e} holds nothing now: her sword gone into the light in her H again (SwordFinale.emptyHanded). */
    public static boolean emptyHanded(net.minecraft.entity.Entity e){return SwordFinale.emptyHanded(e);}
    /** The black over the screen just after its cutscene cuts back to the game (0 to 1, 2.87.0, FallingSword.afterBlack). */
    public static float afterBlack(float delta){return FallingSword.afterBlack(delta);}
}
