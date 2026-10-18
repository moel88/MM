package dev.sangwoo.celestialfall.client;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
public final class MotionArmorModel extends BipedEntityModel<AbstractClientPlayerEntity> {
    public MotionArmorModel(float dilation) {super(MotionRig.armor(new Dilation(dilation)));}
}
