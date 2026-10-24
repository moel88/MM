package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinum;
import dev.sangwoo.celestialfall.stand.StarPlatinumEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Draws Star Platinum's figure with its GeckoLib model (2.50.0). */
public final class StarPlatinumRenderer extends GeoEntityRenderer<StarPlatinumEntity> {
    /** The model stands 41 units (2.6 blocks) to the top of its head; at this size it stands a head over a player. */
    public static final float SCALE=.8f;
    public StarPlatinumRenderer(EntityRendererFactory.Context context){super(context,new StarPlatinumModel());withScale(SCALE);shadowRadius=.5f;}
    public static void initialize(){EntityRendererRegistry.register(StarPlatinum.TYPE,StarPlatinumRenderer::new);}
}
