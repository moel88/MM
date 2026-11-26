package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.GoldExperience;
import dev.sangwoo.celestialfall.stand.GoldExperienceEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Draws Gold Experience's figure with its GeckoLib model (2.145.0). */
public final class GoldExperienceRenderer extends GeoEntityRenderer<GoldExperienceEntity> {
    /**
     * The model stands 40 units (2.5 blocks) to the top of its helmet; at this size it stands 1.8 blocks, a player's
     * height (Star Platinum is drawn at the same size, {@link StarPlatinumRenderer#SCALE}, and stands a little taller).
     */
    public static final float SCALE=.72f;
    /** The model's height to the top of its helmet (units, tools/stand/make_gold_experience.py). */
    public static final float HEIGHT=40f;
    public GoldExperienceRenderer(EntityRendererFactory.Context context){super(context,new GoldExperienceModel());withScale(SCALE);shadowRadius=.4f;}
    public static void initialize(){EntityRendererRegistry.register(GoldExperience.TYPE,GoldExperienceRenderer::new);}
}
