package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.GoldTreeEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws Gold Experience's tree (2.152.0) with its GeckoLib model at its own size, its front the way its maker faced as
 * it burst up (the tree's own record of it, not a living thing's turning body), darkening as it withers. No shadow (it is
 * branches).
 */
public final class GoldTreeRenderer extends GeoEntityRenderer<GoldTreeEntity> {
    public GoldTreeRenderer(EntityRendererFactory.Context context){super(context,new GoldTreeModel());shadowRadius=0;}
    @Override protected void applyRotations(GoldTreeEntity e,MatrixStack matrices,float age,float yaw,float delta){
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f-e.facing()));
    }
    /** No name over it (GeckoLib would show a living thing's name when the camera is near it). */
    @Override public boolean hasLabel(GoldTreeEntity e){return false;}
    @Override public Color getRenderColor(GoldTreeEntity e,float delta,int light){
        double age=e.clientAge(delta);float k=GoldTreeGrowth.dimmed(age,e.witherAt()-e.born());
        return Color.ofRGBA(1-.5f*k,1-.55f*k,1-.6f*k,1f);
    }
}
