package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.xchara.XCharaKnifeItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** X Chara's knife in 3D (2.111.0): its GeckoLib model (tools/xchara/make_knife.py) wherever it is drawn. */
public final class XCharaKnifeRenderer extends GeoItemRenderer<XCharaKnifeItem> {
    public XCharaKnifeRenderer(){super(new Model());}
    static final class Model extends GeoModel<XCharaKnifeItem> {
        @Override public Identifier getModelResource(XCharaKnifeItem item){return CelestialFall.id("geo/item/x_chara_knife.geo.json");}
        @Override public Identifier getTextureResource(XCharaKnifeItem item){return CelestialFall.id("textures/item/x_chara_knife_model.png");}
        @Override public Identifier getAnimationResource(XCharaKnifeItem item){return CelestialFall.id("animations/item/x_chara_knife.animation.json");}
    }
}
