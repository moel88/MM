package dev.sangwoo.celestialfall.client.integration;

import com.immersivecinematics.immersive_cinematics.camera.CameraManager;
import dev.sangwoo.celestialfall.client.BotandCamera;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** The external cinematic owns position/FOV/roll/input until it finishes, then the orbit
 * controller resumes at the final orientation. Native H retains its authored camera track.
 */
public final class CinematicsBridge {
    private static boolean owned;
    private static float yaw,pitch;
    private CinematicsBridge(){}
    public static boolean active(){return CameraManager.INSTANCE.isActive();}
    public static boolean ownsCamera(){return active()&&CameraManager.INSTANCE.hasActiveCameraClip();}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            boolean current=ownsCamera();
            if(current){yaw=CameraManager.INSTANCE.getProperties().getYaw();pitch=CameraManager.INSTANCE.getProperties().getPitch();}
            if(owned&&!current&&mc.world!=null){BotandCamera.STATE.yaw=yaw;BotandCamera.STATE.pitch=Math.max(-80,Math.min(80,pitch));}
            owned=current;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->mc.execute(()->{CameraManager.INSTANCE.forceDeactivate();owned=false;}));
    }
    /** Skill cutscenes have priority over optional editor/demo playback. */
    public static void nativeCutscene(){if(active())CameraManager.INSTANCE.forceDeactivate();owned=false;}
}
