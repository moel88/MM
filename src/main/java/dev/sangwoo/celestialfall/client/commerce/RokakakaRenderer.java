package dev.sangwoo.celestialfall.client.commerce;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.commerce.RokakakaItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * The Rokakaka in 3D (2.41.0, user request): its GeckoLib model (tools/commerce/make_fruit_model.py) wherever the item is
 * drawn: in a hand, in the inventory, on the ground, and on the gacha's R cards. The leaves stir a little.
 */
public final class RokakakaRenderer extends GeoItemRenderer<RokakakaItem> {
    public RokakakaRenderer(){super(new Model());}
    static final class Model extends GeoModel<RokakakaItem> {
        @Override public Identifier getModelResource(RokakakaItem item){return CelestialFall.id("geo/item/rokakaka.geo.json");}
        @Override public Identifier getTextureResource(RokakakaItem item){return CelestialFall.id("textures/item/rokakaka_model.png");}
        @Override public Identifier getAnimationResource(RokakakaItem item){return CelestialFall.id("animations/item/rokakaka.animation.json");}
    }
}
