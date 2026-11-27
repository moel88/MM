package dev.sangwoo.celestialfall.mixin.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gold Experience's T (2.151.0): a part of a body that is a fly now wears nothing either (no helmet where the head has
 * flown off, no sleeve of armour on an arm that is gone). The armour's own pieces are shown for their slot, then those
 * of the parts that are flies hidden again.
 */
@Mixin(ArmorFeatureRenderer.class)
abstract class GoldPartArmorMixin {
    @Inject(method="renderArmor",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/feature/ArmorFeatureRenderer;setVisible(Lnet/minecraft/client/render/entity/model/BipedEntityModel;Lnet/minecraft/entity/EquipmentSlot;)V",shift=At.Shift.AFTER))
    private void botand$noArmourOnAFly(MatrixStack matrices,VertexConsumerProvider buffers,LivingEntity entity,EquipmentSlot slot,int light,BipedEntityModel<?> model,CallbackInfo ci){
        if(dev.sangwoo.celestialfall.client.MapClient.enabled())dev.sangwoo.celestialfall.client.stand.GoldLife.hide(model,entity);
    }
}
