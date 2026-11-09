package dev.sangwoo.celestialfall.mixin.client;
import dev.sangwoo.celestialfall.client.*;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerMixin {
    @Shadow private int ticksToNextAutojump;
    @Inject(method="tickMovement",at=@At("HEAD"))
    private void botand$noAutoJumpDuringBreak(CallbackInfo ci){if(CombatClient.actionLocked())ticksToNextAutojump=0;}
    @Inject(method="canStartSprinting",at=@At("HEAD"),cancellable=true)
    private void botand$noDoubleTap(CallbackInfoReturnable<Boolean> cir){if(BotandCamera.enabled())cir.setReturnValue(false);}
    @Redirect(method="tickMovement",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayerEntity;setSprinting(Z)V"))
    private void botand$run(ClientPlayerEntity player,boolean sprint){player.setSprinting(BotandCamera.enabled()?BotandCamera.sprinting():sprint);}
    @Inject(method="tickMovement",at=@At(value="INVOKE",target="Lnet/minecraft/client/input/Input;tick(ZF)V",shift=At.Shift.AFTER))
    private void botand$runAfterInput(CallbackInfo ci){if(BotandCamera.enabled())((ClientPlayerEntity)(Object)this).setSprinting(BotandCamera.sprinting());}
}
