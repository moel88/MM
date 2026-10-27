package dev.sangwoo.celestialfall.client.integration;

import com.immersivecinematics.immersive_cinematics.camera.CameraManager;
import com.immersivecinematics.immersive_cinematics.script.ScriptParser;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.nio.charset.StandardCharsets;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Local authoring previews; gameplay casts still use the server's existing synchronized packets. */
public final class IntegrationCommands {
    private IntegrationCommands(){}
    public static void initialize(){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("botandtools")
            .then(literal("camera").executes(c->{cameraDemo();return 1;}))
            .then(literal("animation").executes(c->{animationDemo();return 1;}))
            .then(literal("veil").executes(c->{boolean ok=VeilBridge.preview();c.getSource().sendFeedback(Text.literal(ok?"Veil 후처리 예제: 8초":"Veil 예제를 시작할 수 없습니다. 월드·컷씬·리소스 상태를 확인하세요."));return ok?1:0;}))
            .then(literal("stop").executes(c->{stop();return 1;}))
            .then(literal("status").executes(c->{c.getSource().sendFeedback(Text.literal(
                "Botand: Player Animator frames="+PlayerAnimatorBridge.sampledFrames()+", Veil ready="+VeilBridge.ready()+", Veil frames="+VeilBridge.renderedFrames()
                +", Gecko frames="+GeckoDummyModel.renderedFrames()+", cinematic="+CinematicsBridge.ownsCamera()));return 1;}))));
    }
    public static void cameraDemo(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||CinematicDirector.isActive())return;
        try(var resource=mc.getResourceManager().getResourceOrThrow(CelestialFall.id("cinematics/camera_demo.json")).getInputStream()){
            var script=ScriptParser.parse(new String(resource.readAllBytes(),StandardCharsets.UTF_8));
            CameraManager.INSTANCE.playScript(script);
        }catch(Exception ex){CelestialFall.LOGGER.error("Botand camera preview failed",ex);mc.player.sendMessage(Text.literal("카메라 예제를 읽지 못했습니다. 로그를 확인해 주세요."),false);}
    }
    public static void animationDemo(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||CinematicDirector.isActive())return;
        var clip=PlayerAnimationRegistry.getAnimation(CelestialFall.id("botand_preview"));
        var layer=PlayerAnimatorBridge.clips(mc.player);
        if(clip!=null&&layer!=null)layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5,Ease.INOUTSINE),new KeyframeAnimationPlayer(clip));
    }
    public static void stop(){
        VeilBridge.stop();
        CameraManager.INSTANCE.forceDeactivate();var mc=MinecraftClient.getInstance();
        if(mc.player!=null&&PlayerAnimatorBridge.clips(mc.player)!=null)PlayerAnimatorBridge.clips(mc.player).setAnimation(null);
    }
}
