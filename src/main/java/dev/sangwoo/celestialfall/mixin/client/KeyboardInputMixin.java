package dev.sangwoo.celestialfall.mixin.client;
import dev.sangwoo.celestialfall.client.BotandCamera;
import net.minecraft.client.input.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void botand$input(boolean slow,float multiplier,CallbackInfo ci){
        Input input=(Input)(Object)this;
        dev.sangwoo.celestialfall.client.StandClient.cancelPoseOnMovement(input);
        BotandCamera.input(input);
    }
}
