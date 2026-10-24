package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.CinematicPath;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

/** Changes only the rendered camera. Player position, perspective setting and controls are untouched. */
public final class CinematicDirector {
    private static UUID activeId;
    private static double startAzimuth;
    public static KeyBinding skip;
    private CinematicDirector() {}
    public record Pose(Vec3d position, float yaw, float pitch, float roll) {}
    public static void initialize() {
        skip=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.celestialfall.skip",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.celestialfall"));
    }
    public static void start(ClientSpell spell, int age) {
        MinecraftClient mc=MinecraftClient.getInstance();
        if(!CelestialFall.CONFIG.cinematicCamera || mc.player==null || age>30
            || !spell.caster.equals(mc.player.getUuid()) || activeId!=null)return;
        Vec3d offset=mc.player.getPos().subtract(spell.target);
        startAzimuth=offset.horizontalLengthSquared()>1 ? Math.atan2(offset.z,offset.x)
            : Math.toRadians(spell.yaw+90);
        activeId=spell.id;
    }
    public static void clear() { activeId=null; }
    public static ClientSpell active() {
        MinecraftClient mc=MinecraftClient.getInstance();
        if(!MapClient.enabled() || activeId==null || mc.world==null || mc.player==null || !mc.player.isAlive()
            || !CelestialFall.CONFIG.cinematicCamera || mc.getCameraEntity()!=mc.player)return null;
        ClientSpell s=CelestialFallClient.SPELLS.get(activeId);
        if(s==null || !s.dimension.equals(mc.world.getRegistryKey().getValue())
            || s.age(mc.world.getTime(),0)>=CinematicPath.END)return null;
        return s;
    }
    public static void tick() {
        while(skip.wasPressed()) { clear();AtomicClient.skip();HeavenClient.skip(); }
        if(active()==null)clear();
    }
    /** Never changes options.hudHidden: the user's F1 setting survives every exit path. */
    public static boolean isActive() {
        var mc=MinecraftClient.getInstance();
        return MapClient.enabled() && mc.world!=null && mc.player!=null && (HeavenClient.cinematic()!=null
            || StandClient.cinematic()!=null || AtomicClient.cinematic()!=null || active()!=null);
    }
    public static void closeInventory() {
        var mc=MinecraftClient.getInstance();
        if(isActive() && mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>)
            mc.player.closeHandledScreen();
    }
    public static float blend(float delta) {
        if(StandClient.cinematic()!=null)return StandClient.cameraBlend(delta);
        if(HeavenClient.cinematic()!=null)return HeavenClient.blend(delta);
        if(AtomicClient.cinematic()!=null)return AtomicClient.blend(delta);
        ClientSpell s=active();
        return s==null?0:CinematicPath.blend(s.age(MinecraftClient.getInstance().world.getTime(),delta));
    }
    public static double fov(double vanilla,float delta) {
        if(StandClient.cinematic()!=null)return vanilla+(StandClient.cameraFov(delta)-vanilla)*StandClient.cameraBlend(delta);
        if(HeavenClient.cinematic()!=null)return HeavenClient.fov(vanilla,delta);
        if(AtomicClient.cinematic()!=null)return AtomicClient.fov(vanilla,delta);
        ClientSpell s=active();
        // G seen from outside: a slow push-in through the ignition and a punch when the air shock arrives.
        if(s==null)return vanilla+dev.sangwoo.celestialfall.client.atomic.AtomicAmbience.fov(delta);
        float age=s.age(MinecraftClient.getInstance().world.getTime(),delta);
        return vanilla+(CinematicPath.sample(age).fov()-vanilla)*CinematicPath.blend(age);
    }
    public static float roll(float delta) {
        if(StandClient.cinematic()!=null)return StandClient.camera(delta).roll()*StandClient.cameraBlend(delta);
        if(HeavenClient.cinematic()!=null)return HeavenClient.roll(delta);
        if(AtomicClient.cinematic()!=null)return AtomicClient.roll(delta);
        ClientSpell s=active();
        if(s==null)return 0;
        float age=s.age(MinecraftClient.getInstance().world.getTime(),delta);
        return (float)CinematicPath.sample(age).roll()*CinematicPath.blend(age);
    }
    public static Pose pose(Vec3d vanilla,float yaw,float pitch,float delta) {
        if(StandClient.cinematic()!=null){var target=StandClient.camera(delta);float blend=StandClient.cameraBlend(delta);return new Pose(vanilla.lerp(target.position(),blend),yaw+net.minecraft.util.math.MathHelper.wrapDegrees(target.yaw()-yaw)*blend,pitch+(target.pitch()-pitch)*blend,target.roll()*blend);}
        if(HeavenClient.cinematic()!=null)return HeavenClient.camera(vanilla,yaw,pitch,delta);
        if(AtomicClient.cinematic()!=null)return AtomicClient.camera(vanilla,yaw,pitch,delta);
        MinecraftClient mc=MinecraftClient.getInstance();
        ClientSpell s=active();
        if(s==null)return null;
        float age=s.age(mc.world.getTime(),delta), blend=CinematicPath.blend(age);
        if(blend<=0)return null;
        CinematicPath.Shot shot=CinematicPath.sample(age);
        double angle=startAzimuth+shot.orbit();
        Vec3d anchorBase=shot.playerAnchor()?mc.player.getLerpedPos(delta):s.target;
        Vec3d desired=anchorBase.add(Math.cos(angle)*shot.radius(),shot.height(),Math.sin(angle)*shot.radius());
        // A wide shot can leave the loaded terrain on a short render distance. Pull its
        // horizontal position toward the player while retaining the aerial height.
        for(int attempt=0;attempt<8 && !mc.world.isChunkLoaded(BlockPos.ofFloored(desired));attempt++)
            desired=desired.lerp(new Vec3d(mc.player.getX(),desired.y,mc.player.getZ()),.25);
        if(!mc.world.isChunkLoaded(BlockPos.ofFloored(desired))) { clear(); return null; }
        // Raycast the centre and four corners to keep the camera away from slopes/walls.
        Vec3d anchor=shot.playerAnchor()?mc.player.getEyePos():s.target.add(0,shot.height(),0);
        double fraction=1;
        for(Vec3d off:new Vec3d[]{Vec3d.ZERO,new Vec3d(.35,.35,0),new Vec3d(-.35,-.35,0),
            new Vec3d(0,.35,.35),new Vec3d(0,-.35,-.35)}) {
            HitResult hit=mc.world.raycast(new RaycastContext(anchor.add(off),desired.add(off),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS)
                fraction=Math.min(fraction,Math.max(0,(anchor.add(off).distanceTo(hit.getPos())-.6)/anchor.distanceTo(desired)));
        }
        desired=anchor.lerp(desired,fraction);
        Vec3d position=vanilla.lerp(desired,blend);
        Vec3d focus=s.target.add(0,shot.focusHeight(),0);
        // Interpolate directions too, so entering and returning do not snap the view.
        Vec3d direction=Vec3d.fromPolar(pitch,yaw).lerp(focus.subtract(position).normalize(),blend).normalize();
        double shake=CinematicPath.shake(age)*CelestialFall.CONFIG.screenShakeStrength*blend;
        float outYaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
        float outPitch=(float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,direction.y))));
        outYaw+=(float)(Math.sin(age*2.21)*shake*1.9);
        outPitch+=(float)(Math.sin(age*2.83+.5)*shake*1.4);
        return new Pose(position,outYaw,outPitch,(float)shot.roll()*blend);
    }
}
