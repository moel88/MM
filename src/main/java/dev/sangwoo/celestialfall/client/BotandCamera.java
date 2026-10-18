package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.combat.OrbitCameraState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

/** Botand's classic third-person controller. Cutscenes retain final camera ownership. */
public final class BotandCamera {
    public static final OrbitCameraState STATE=new OrbitCameraState();
    private static KeyBinding LOCK,RUN,DISMOUNT;
    private static Object player,world;
    private static boolean rightDrag,wasCinematic;
    private static long lastFrame;
    private static long frameId,lastClipFrame=-1;
    private static double dt=1.0/60;
    private static Perspective previousPerspective;
    private static Vec3d lastPlayerPos;
    private BotandCamera() {}
    public static void initialize() {
        LOCK=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.botand.shift_lock",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_SHIFT,"category.botand"));
        RUN=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.botand.run",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_CONTROL,"category.botand"));
        DISMOUNT=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.botand.dismount",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_X,"category.botand"));
        ClientTickEvents.START_CLIENT_TICK.register(BotandCamera::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->reset(mc));
        HudRenderCallback.EVENT.register(BotandCamera::hud);
    }
    public static boolean enabled() {
        var mc=MinecraftClient.getInstance();return mc.player!=null&&mc.world!=null&&CombatClient.connected()
            &&mc.getCameraEntity()==mc.player&&!mc.player.isSpectator();
    }
    public static boolean gameplay(){var mc=MinecraftClient.getInstance();return !dev.sangwoo.celestialfall.client.integration.CinematicsBridge.active()&&enabled()&&mc.currentScreen==null&&mc.getOverlay()==null&&mc.isWindowFocused()&&mc.player.isAlive();}
    public static boolean controls(){return gameplay()&&!CinematicDirector.isActive();}
    public static boolean locked(){return STATE.shiftLock||STATE.firstPerson();}
    public static boolean sprinting() {
        var mc=MinecraftClient.getInstance();if(!controls()||CombatClient.actionLocked()||!STATE.run)return false;
        var p=mc.player;var s=CombatClient.snapshot(p.getUuid());
        return !s.guard()&&s.attack()==0&&s.dashAge(mc.world.getTime())>=7&&!p.hasVehicle()&&!p.isUsingItem()
            &&!p.isFallFlying()&&!p.getAbilities().flying&&(p.getHungerManager().getFoodLevel()>6||p.getAbilities().allowFlying)
            &&Math.hypot(p.input.movementForward,p.input.movementSideways)>.1&&!p.horizontalCollision;
    }
    public static void reset(MinecraftClient mc) {
        if(previousPerspective!=null)mc.options.setPerspective(previousPerspective);
        boolean hadControl=previousPerspective!=null;
        previousPerspective=null;player=world=null;rightDrag=false;lastFrame=0;frameId=0;lastClipFrame=-1;lastPlayerPos=null;wasCinematic=false;STATE.reset(0);
        if(hadControl&&mc.world!=null&&mc.currentScreen==null)((MouseCapture)mc.mouse).botand$capture(true);
    }
    private static void tick(MinecraftClient mc) {
        if(!enabled()){if(player!=null)reset(mc);return;}
        if(player!=mc.player||world!=mc.world) {
            if(previousPerspective==null)previousPerspective=mc.options.getPerspective();
            player=mc.player;world=mc.world;STATE.reset(mc.player.getYaw());lastFrame=0;lastPlayerPos=mc.player.getPos();rightDrag=false;
        }
        while(LOCK.wasPressed())if(controls())STATE.shiftLock=!STATE.shiftLock;
        while(RUN.wasPressed())if(controls()&&!CombatClient.actionLocked())STATE.run=!STATE.run;
        // F5 uses the same zoom state, so it cannot leave the camera in front-view mode.
        while(mc.options.togglePerspectiveKey.wasPressed())if(controls())STATE.targetDistance=STATE.targetDistance==0?OrbitCameraState.DEFAULT_DISTANCE:0;
        if(!gameplay())rightDrag=false;
        if(CombatClient.actionLocked()) {
            mc.player.clearActiveItem();mc.player.setSprinting(false);
            if(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>)mc.player.closeHandledScreen();
        }
        refreshCapture();
    }
    public static void frame() {
        if(!enabled()||player!=MinecraftClient.getInstance().player||dev.sangwoo.celestialfall.client.integration.CinematicsBridge.active())return;
        long now=System.nanoTime();dt=lastFrame==0?1.0/60:Math.min(.1,(now-lastFrame)*1e-9);lastFrame=now;frameId++;
        var mc=MinecraftClient.getInstance();boolean cinematic=CinematicDirector.isActive();
        if(!cinematic&&!mc.isPaused())STATE.step(dt);
        if(wasCinematic&&!cinematic){rightDrag=false;lastFrame=now;}
        wasCinematic=cinematic;
        var p=mc.player.getPos();if(lastPlayerPos!=null&&lastPlayerPos.squaredDistanceTo(p)>64){STATE.yaw=mc.player.getYaw();STATE.collisionDistance=STATE.distance;}
        lastPlayerPos=p;
        Perspective wanted=STATE.firstPerson()?Perspective.FIRST_PERSON:Perspective.THIRD_PERSON_BACK;
        if(mc.options.getPerspective()!=wanted){mc.options.setPerspective(wanted);mc.worldRenderer.scheduleTerrainUpdate();}
        refreshCapture();
    }
    public static void refreshCapture() {
        if(!enabled()||dev.sangwoo.celestialfall.client.integration.CinematicsBridge.active())return;
        boolean capture=gameplay()&&(CinematicDirector.isActive()||STATE.captured(rightDrag));
        ((MouseCapture)MinecraftClient.getInstance().mouse).botand$capture(capture);
    }
    public static void rightButton(boolean down){rightDrag=down&&controls();refreshCapture();}
    public static void look(double x,double y) {
        if(!controls()||!STATE.captured(rightDrag))return;
        STATE.rotate(x*.15,y*.15);
        if(locked())faceCamera();
    }
    public static void scroll(double vertical) {if(controls())STATE.scroll(vertical);}
    public static void faceCamera() {
        if(!controls())return;var p=MinecraftClient.getInstance().player;
        p.setYaw(STATE.yaw);p.setPitch(STATE.pitch);p.setHeadYaw(STATE.yaw);
    }
    /** Resolve shoulder-camera parallax back to an eye-origin aim ray; never grants extra reach. */
    public static void aim() {
        if(!controls())return;var mc=MinecraftClient.getInstance();var p=mc.player;
        if(HitStunClient.aimCapturedTarget())return;
        var pose=pose(1);Vec3d start=pose.position(),end=start.add(Vec3d.fromPolar(STATE.pitch,STATE.yaw).multiply(64));
        var block=mc.world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));
        if(block.getType()!=HitResult.Type.MISS)end=block.getPos();
        var entity=net.minecraft.entity.projectile.ProjectileUtil.raycast(p,start,end,new Box(start,end).expand(1),e->e!=p&&e.canHit()&&!e.isSpectator(),start.squaredDistanceTo(end));
        if(entity!=null)end=entity.getPos();
        Vec3d direction=end.subtract(p.getEyePos());
        // A camera pressed against a wall must not aim back through the player.
        if(direction.dotProduct(Vec3d.fromPolar(STATE.pitch,STATE.yaw))<=.05){faceCamera();return;}
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalLength()));
        p.setYaw(yaw);p.setPitch(pitch);p.setHeadYaw(yaw);
    }
    public static void prepareInteraction() {
        if(!controls())return;
        aim();var mc=MinecraftClient.getInstance();mc.gameRenderer.updateTargetedEntity(1);
        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
    }
    public static void input(Input input) {
        if(!enabled())return;
        var mc=MinecraftClient.getInstance();var p=mc.player;
        if(StandClient.movementLocked()||HitStunClient.localStunned()) {
            input.movementForward=input.movementSideways=0;input.jumping=input.sneaking=false;
            input.pressingForward=input.pressingBack=input.pressingLeft=input.pressingRight=false;
            // T held is aimed while its slashes are thrown (user request): the caster turns to where the camera looks.
            if(controls()&&!HitStunClient.localStunned()&&StandClient.aimingRush())aim();
            return;
        }
        // On-foot crouch is removed. X is only dismount / creative-flight descent.
        input.sneaking=!CombatClient.actionLocked()&&(p.hasVehicle()||p.getAbilities().flying)&&DISMOUNT.isPressed();
        if(CombatClient.actionLocked())input.jumping=false;
        if(!controls()||p.hasVehicle())return;
        float forward=input.movementForward,side=input.movementSideways;
        var s=CombatClient.snapshot(p.getUuid());
        boolean facing=locked()||s.guard()||s.attack()>0||mc.options.attackKey.isPressed()||mc.options.useKey.isPressed();
        if(facing)aim();
        else if(Math.hypot(forward,side)>.01) {
            p.setYaw(OrbitCameraState.turn(p.getYaw(),OrbitCameraState.movementYaw(STATE.yaw,forward,side),36));
            p.setHeadYaw(p.getYaw());p.setPitch(0);
        }
        float[] movement=OrbitCameraState.movement(forward,side,STATE.yaw,p.getYaw());
        input.movementForward=movement[0];input.movementSideways=movement[1];
    }
    public static CinematicDirector.Pose pose(float delta) {
        if(!enabled()||dev.sangwoo.celestialfall.client.integration.CinematicsBridge.ownsCamera())return null;var mc=MinecraftClient.getInstance();
        Vec3d eye=mc.player.getLerpedPos(delta).add(0,mc.player.getStandingEyeHeight(),0);
        if(STATE.firstPerson())return new CinematicDirector.Pose(eye,STATE.yaw,STATE.pitch,0);
        var offset=STATE.offset(STATE.distance,STATE.shoulder);
        Vec3d desired=eye.add(offset.x(),offset.y(),offset.z());
        double fraction=clearFraction(eye,desired);
        double distance=STATE.clip(STATE.distance*fraction,lastClipFrame==frameId?0:dt),ratio=STATE.distance<.001?0:distance/STATE.distance;
        lastClipFrame=frameId;
        return new CinematicDirector.Pose(eye.add(desired.subtract(eye).multiply(ratio)),STATE.yaw,STATE.pitch,0);
    }
    /** Swept camera volume rather than one centre ray: corners stay clear of walls. */
    private static double clearFraction(Vec3d eye,Vec3d end) {
        var mc=MinecraftClient.getInstance();double fraction=1,length=eye.distanceTo(end);
        if(length<.001)return 1;
        for(int i=0;i<8;i++) {
            Vec3d pad=new Vec3d((i&1)==0?-.16:.16,(i&2)==0?-.16:.16,(i&4)==0?-.16:.16);
            var hit=mc.world.raycast(new RaycastContext(eye.add(pad),end.add(pad),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS)fraction=Math.min(fraction,Math.max(0,(eye.add(pad).distanceTo(hit.getPos())-.12)/length));
        }
        return fraction;
    }
    private static void hud(DrawContext draw,float delta) {
        var mc=MinecraftClient.getInstance();if(!enabled()||mc.options.hudHidden||CinematicDirector.isActive()||mc.currentScreen!=null)return;
        int y=draw.getScaledWindowHeight()-35;
        if(STATE.shiftLock&&!STATE.firstPerson()) {
            int x=draw.getScaledWindowWidth()/2,cy=draw.getScaledWindowHeight()/2;
            draw.fill(x-2,cy-6,x+2,cy+6,0xaa111827);draw.fill(x-6,cy-2,x+6,cy+2,0xaa111827);
            draw.fill(x-1,cy-5,x+1,cy+5,0xffeee9ff);draw.fill(x-5,cy-1,x+5,cy+1,0xffeee9ff);
        }
        draw.fill(8,y-3,156,y+25,0xa0121925);
        draw.drawTextWithShadow(mc.textRenderer,Text.translatable(STATE.shiftLock?"hud.botand.lock_on":"hud.botand.lock_off"),14,y,STATE.shiftLock?0xffb5a2ff:0xffaebaca);
        draw.drawTextWithShadow(mc.textRenderer,Text.translatable(STATE.run?"hud.botand.run_on":"hud.botand.run_off"),14,y+12,STATE.run?0xff91e5cd:0xffaebaca);
    }
}
