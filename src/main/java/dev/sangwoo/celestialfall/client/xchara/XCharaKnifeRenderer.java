package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.xchara.XCharaKnifeItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * X Chara's knife in 3D (2.111.0): its GeckoLib model (tools/xchara/make_knife.py) wherever it is drawn; and (2.115.0)
 * over it, while its holder's hack is on (their Q), its white and its glowing purple rim (HackKnife).
 */
public final class XCharaKnifeRenderer extends GeoItemRenderer<XCharaKnifeItem> {
    /**
     * The hack of the one whose hand the knife is drawn in: set by whoever draws it in a hand (StandHeldItemFeatureMixin,
     * HeldItemRendererMixin) for that one draw, null otherwise.
     */
    public static HackLook.Frame hack;
    public XCharaKnifeRenderer(){
        super(new Model());
        addRenderLayer(new software.bernie.geckolib.renderer.layer.GeoRenderLayer<>(this){
            @Override public void render(net.minecraft.client.util.math.MatrixStack matrices,XCharaKnifeItem item,software.bernie.geckolib.cache.object.BakedGeoModel model,net.minecraft.client.render.RenderLayer type,net.minecraft.client.render.VertexConsumerProvider buffers,net.minecraft.client.render.VertexConsumer buffer,float delta,int light,int overlay){
                // where it is in the world, if it is drawn in its holder's hand there (the effects about it: HackFx)
                HackMode.recordKnife(matrices);
                var h=hack;if(h==null||!h.shows())return;
                HackKnife.draw(XCharaKnifeRenderer.this,model,matrices,buffers,item,delta,overlay,h);
            }
        });
    }
    static final class Model extends GeoModel<XCharaKnifeItem> {
        @Override public Identifier getModelResource(XCharaKnifeItem item){return CelestialFall.id("geo/item/x_chara_knife.geo.json");}
        @Override public Identifier getTextureResource(XCharaKnifeItem item){return CelestialFall.id("textures/item/x_chara_knife_model.png");}
        @Override public Identifier getAnimationResource(XCharaKnifeItem item){return CelestialFall.id("animations/item/x_chara_knife.animation.json");}
    }
}
