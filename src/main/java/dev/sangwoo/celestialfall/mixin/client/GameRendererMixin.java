package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.title.TitleMapCamera;
import dev.sangwoo.celestialfall.client.title.TitleCameraTour;
import dev.sangwoo.celestialfall.client.AnimePostProcessor;
import dev.sangwoo.celestialfall.client.CelestialFallClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method="updateTargetedEntity",at=@At("HEAD"))
    private void botand$aim(float delta,CallbackInfo ci) {
        var mc=net.minecraft.client.MinecraftClient.getInstance();
        if(dev.sangwoo.celestialfall.client.BotandCamera.controls()&&(dev.sangwoo.celestialfall.client.BotandCamera.locked()||mc.options.attackKey.isPressed()||mc.options.useKey.isPressed()))
            dev.sangwoo.celestialfall.client.BotandCamera.aim();
    }
    // Vanilla builds the view matrix from yaw/pitch, not Camera.rotation. Apply the
    // same roll here before its X/Y rotations; CameraMixin also rotates billboard axes.
    @Inject(method="renderWorld",at=@At(value="INVOKE",
        target="Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
        shift=At.Shift.AFTER))
    private void celestialfall$roll(float delta,long limitTime,MatrixStack matrices,CallbackInfo ci) {
        if(TitleMapCamera.active()||dev.sangwoo.celestialfall.client.integration.CinematicsBridge.ownsCamera())return;
        float roll=CinematicDirector.roll(delta);
        if(!CinematicDirector.isActive())roll+=dev.sangwoo.celestialfall.client.AtomicImmersion.at(net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos(),delta).roll();
        if(roll!=0)matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(roll));
    }
    @Inject(method="renderWorld",at=@At("TAIL"))
    private void celestialfall$post(float delta,long limitTime,MatrixStack matrices,CallbackInfo ci) {
        if(TitleMapCamera.active())return;
        dev.sangwoo.celestialfall.client.atomic.AtomicAmbience.letterbox(delta);
        AnimePostProcessor.render(delta);
    }
    @Inject(method="close",at=@At("HEAD"))
    private void celestialfall$release(CallbackInfo ci) { AnimePostProcessor.release(); }
    @Inject(method="getViewDistance",at=@At("RETURN"),cancellable=true)
    private void celestialfall$wideFog(CallbackInfoReturnable<Float> cir) {
        if(dev.sangwoo.celestialfall.client.HeavenClient.wideCinematic())
            cir.setReturnValue(Math.max(cir.getReturnValue(),1024f));
    }
    @Inject(method="getFarPlaneDistance",at=@At("RETURN"),cancellable=true)
    private void celestialfall$farPlane(CallbackInfoReturnable<Float> cir) {
        if(!dev.sangwoo.celestialfall.client.HeavenClient.CASTS.isEmpty()) { cir.setReturnValue(Math.max(2048f,cir.getReturnValue()));return; }
        if(!CelestialFallClient.SPELLS.isEmpty() || !dev.sangwoo.celestialfall.client.AtomicClient.CASTS.isEmpty())cir.setReturnValue(Math.max(dev.sangwoo.celestialfall.client.AtomicClient.CASTS.isEmpty()?1536f:4096f,cir.getReturnValue()));
    }
    @Inject(method="getFov",at=@At("RETURN"),cancellable=true)
    private void celestialfall$fov(Camera camera,float delta,boolean changing,CallbackInfoReturnable<Double> cir) {
        if(dev.sangwoo.celestialfall.client.integration.CinematicsBridge.ownsCamera())return;
        if(TitleMapCamera.active()){cir.setReturnValue(TitleCameraTour.FOV);return;}
        if(changing)cir.setReturnValue(CinematicDirector.fov(cir.getReturnValue(),delta));
    }
    @Inject(method="renderHand",at=@At("HEAD"),cancellable=true)
    private void celestialfall$hand(MatrixStack matrices,Camera camera,float delta,CallbackInfo ci) {
        if(TitleMapCamera.active()||CinematicDirector.isActive())ci.cancel();
    }
    @Inject(method="render",at=@At("HEAD"))
    private void celestialfall$closeInventory(float delta,long limitTime,boolean tick,CallbackInfo ci) {
        dev.sangwoo.celestialfall.client.BotandCamera.frame();
        CinematicDirector.closeInventory();
    }
    @Inject(method="bobView",at=@At("HEAD"),cancellable=true)
    private void botand$steadyCamera(MatrixStack matrices,float delta,CallbackInfo ci) {
        if(TitleMapCamera.active()||CinematicDirector.isActive()||(dev.sangwoo.celestialfall.client.BotandCamera.enabled()&&!dev.sangwoo.celestialfall.client.BotandCamera.STATE.firstPerson()))ci.cancel();
    }
    @Inject(method="tiltViewWhenHurt",at=@At("HEAD"),cancellable=true)
    private void botand$steadyMenu(MatrixStack matrices,float delta,CallbackInfo ci){if(TitleMapCamera.active()||CinematicDirector.isActive())ci.cancel();}
    // GUI projection is already installed here; this also runs when F1 skips InGameHud.
    // World effects are finished; pause/chat/resource-reload screens remain usable.
    @Inject(method="render",at=@At(value="INVOKE",ordinal=0,
        target="Lnet/minecraft/client/MinecraftClient;getOverlay()Lnet/minecraft/client/gui/screen/Overlay;"))
    private void celestialfall$cinematic(float delta,long limitTime,boolean tick,CallbackInfo ci) {
        if(!TitleMapCamera.active()){
            AnimePostProcessor.renderMonochrome(delta);
            dev.sangwoo.celestialfall.client.CinematicHud.render(delta);
        }
    }
    @Inject(method="renderFloatingItem",at=@At("HEAD"),cancellable=true)
    private void celestialfall$hideFloatingItem(int width,int height,float delta,CallbackInfo ci) {
        if(TitleMapCamera.active()||CinematicDirector.isActive())ci.cancel();
    }
}
