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
    @Override public boolean hasLabel(TrainingDummyEntity d){return dispatcher.getSquaredDistanceToCamera(d)<32*32;}
    @Override protected void renderLabelIfPresent(TrainingDummyEntity d,Text text,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        var label=d.getDisplayName().copy().append(String.format(java.util.Locale.ROOT,"  %.0f / %.0f",d.getHealth(),d.getMaxHealth()));
        if(d.kind()==DummyKind.GUARD)label.append("  ").append(d.brokenUntil()>d.getWorld().getTime()
            ?Text.translatable("hud.botand.dummy_broken"):Text.translatable("hud.botand.dummy_guard",Math.round(d.guardPressure())));
        super.renderLabelIfPresent(d,label,matrices,vertices,light);
    }
}
