package dev.sangwoo.celestialfall.mixin;

import com.immersivecinematics.immersive_cinematics.fabric.FabricNetwork;
import com.immersivecinematics.immersive_cinematics.trigger.network.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Function;

/** IC 0.3.5 registers client lambdas in its common entrypoint. Keep the six original
 * server channels, but do not link ClientPlayNetworking on a dedicated server.
 * Client and integrated-server initialization still use IC's unmodified implementation.
 */
@Mixin(value=FabricNetwork.class,remap=false)
public abstract class ImmersiveNetworkServerMixin {
    @Inject(method="init",at=@At("HEAD"),cancellable=true,remap=false)
    private static void botand$serverOnly(CallbackInfo ci){
        if(FabricLoader.getInstance().getEnvironmentType()!=EnvType.SERVER)return;
        NetworkHandler.setBridge(new FabricNetwork());
        botand$channel("script_finished",C2SScriptFinishedPacket::new);
        botand$channel("playback_started",C2SPlaybackStartedPacket::new);
        botand$channel("script_pause",C2SScriptPausePacket::new);
        botand$channel("script_saved",C2SScriptSavedPacket::new);
        botand$channel("preload_req",C2SPreloadRequestPacket::new);
        botand$channel("preload_pos",C2SPreloadPositionPacket::new);
        ci.cancel();
    }
    @Unique private static void botand$channel(String name,Function<PacketByteBuf,CinematicC2SPacket> decoder){
        ServerPlayNetworking.registerGlobalReceiver(new Identifier("immersive_cinematics",name),(server,player,handler,buf,sender)->{
            var packet=decoder.apply(buf);server.execute(()->packet.handle(player));
        });
    }
}
