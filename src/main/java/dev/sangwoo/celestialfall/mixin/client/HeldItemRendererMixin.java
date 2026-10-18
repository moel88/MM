package dev.sangwoo.celestialfall.mixin.client;
import dev.sangwoo.celestialfall.client.CombatHands;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @org.spongepowered.asm.mixin.Shadow
    private void renderFirstPersonItem(net.minecraft.client.network.AbstractClientPlayerEntity player,float delta,float pitch,net.minecraft.util.Hand hand,float swing,net.minecraft.item.ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider vertices,int light){throw new AssertionError();}

    @Inject(method="renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",at=@At("HEAD"),cancellable=true)
    private void botand$hands(float delta,MatrixStack matrices,VertexConsumerProvider.Immediate vertices,ClientPlayerEntity player,int light,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.StandClient.visible(player)){
            float swing=player.getHandSwingProgress(delta);
            var cast=dev.sangwoo.celestialfall.client.kageno.KagenoClips.animated(player.getUuid());
            matrices.push();
            var atomic=dev.sangwoo.celestialfall.client.AtomicClient.poseCast(player.getUuid());
            if(atomic!=null){
                // First person: the sword rises overhead with the A-frame and stays raised through the blast.
                float sec=atomic.age(delta)/20f;
                float raise=dev.sangwoo.celestialfall.client.kageno.Ease.smooth(sec,.067f,.267f)*(1-dev.sangwoo.celestialfall.client.kageno.Ease.smooth(sec,21.9f,22.6f));
                matrices.translate(-raise*.12,raise*.42,-raise*.12);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(raise*25));swing=0;
            }
            renderFirstPersonItem(player,delta,player.getPitch(delta),net.minecraft.util.Hand.MAIN_HAND,player.preferredHand==net.minecraft.util.Hand.OFF_HAND?0:swing,dev.sangwoo.celestialfall.client.StandClient.sword(),0,matrices,vertices,light);
            matrices.pop();
            if(!player.getOffHandStack().isEmpty())renderFirstPersonItem(player,delta,player.getPitch(delta),net.minecraft.util.Hand.OFF_HAND,player.preferredHand==net.minecraft.util.Hand.OFF_HAND?swing:0,player.getOffHandStack(),0,matrices,vertices,light);
            // Vanilla flushes this buffer at the end of the cancelled outer method.
            vertices.draw();
            ci.cancel();return;
        }
        if(CombatHands.enabled(player)){CombatHands.render(player,delta,matrices,vertices,light);ci.cancel();}
    }
}
