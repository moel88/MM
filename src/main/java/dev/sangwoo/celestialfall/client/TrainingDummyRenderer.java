package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.training.*;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class TrainingDummyRenderer extends software.bernie.geckolib.renderer.GeoEntityRenderer<TrainingDummyEntity> {
    private static final Identifier SKIN=CelestialFall.id("textures/entity/training_dummy.png");
    public TrainingDummyRenderer(EntityRendererFactory.Context context){super(context,new dev.sangwoo.celestialfall.client.integration.GeckoDummyModel());shadowRadius=.4f;}
    public static void initialize(){for(var type:TrainingDummies.TYPES.values())EntityRendererRegistry.register(type,TrainingDummyRenderer::new);}
    @Override public Identifier getTexture(TrainingDummyEntity d){return SKIN;}
    /** The Atomic dummy holds the stand's sword through its Atomic, as the caster's held-item layer would draw it. */
    @Override public void render(TrainingDummyEntity d,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        super.render(d,yaw,delta,matrices,vertices,light);
        var atomic=AtomicClient.poseCast(d.getUuid());if(atomic==null)return;
        matrices.push();
        // The body's model frame, as the living renderer sets it up for a player (the dummy is drawn at full size).
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180-net.minecraft.util.math.MathHelper.lerpAngleDegrees(delta,d.prevBodyYaw,d.bodyYaw)));
        matrices.scale(-1,-1,1);matrices.translate(0,-1.501f,0);
        dev.sangwoo.celestialfall.client.kageno.AtomicPose.applyHeld(matrices,atomic.age(delta)/20f);
        net.minecraft.client.MinecraftClient.getInstance().getEntityRenderDispatcher().getHeldItemRenderer().renderItem(d,StandClient.sword(),
            net.minecraft.client.render.model.json.ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,false,matrices,vertices,light);
        matrices.pop();
    }
    @Override public boolean hasLabel(TrainingDummyEntity d){return dispatcher.getSquaredDistanceToCamera(d)<32*32;}
    @Override protected void renderLabelIfPresent(TrainingDummyEntity d,Text text,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        var label=d.getDisplayName().copy().append(String.format(java.util.Locale.ROOT,"  %.0f / %.0f",d.getHealth(),d.getMaxHealth()));
        if(d.kind()==DummyKind.GUARD)label.append("  ").append(d.brokenUntil()>d.getWorld().getTime()
            ?Text.translatable("hud.botand.dummy_broken"):Text.translatable("hud.botand.dummy_guard",Math.round(d.guardPressure())));
        super.renderLabelIfPresent(d,label,matrices,vertices,light);
    }
}
