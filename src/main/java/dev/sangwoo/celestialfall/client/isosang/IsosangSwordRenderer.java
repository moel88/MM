package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.isosang.IsosangSwordItem;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Lee So-sang's sword in 3D (2.76.0): its GeckoLib model (tools/isosang/make_sword.py) wherever it is drawn, the lightning
 * on its blade and their glints drawn once more over it at full brightness from the glow texture (both its faces: a
 * GeckoLib glow mask cut those texels out of the model and drew them with back faces culled, which hid them in a hand).
 */
public final class IsosangSwordRenderer extends GeoItemRenderer<IsosangSwordItem> {
    static final Identifier GLOW=CelestialFall.id("textures/item/isosang_sword_model_glow.png");
    public IsosangSwordRenderer(){
        super(new Model());
        addRenderLayer(new GeoRenderLayer<>(this){
            @Override public void render(MatrixStack matrices,IsosangSwordItem item,BakedGeoModel model,RenderLayer type,VertexConsumerProvider buffers,VertexConsumer buffer,float delta,int light,int overlay){
                RenderLayer glow=RenderLayer.getEntityTranslucentEmissive(GLOW);
                getRenderer().reRender(model,matrices,buffers,item,glow,buffers.getBuffer(glow),delta,LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay,1,1,1,1);
            }
        });
    }
    static final class Model extends GeoModel<IsosangSwordItem> {
        @Override public Identifier getModelResource(IsosangSwordItem item){return CelestialFall.id("geo/item/isosang_sword.geo.json");}
        @Override public Identifier getTextureResource(IsosangSwordItem item){return CelestialFall.id("textures/item/isosang_sword_model.png");}
        @Override public Identifier getAnimationResource(IsosangSwordItem item){return CelestialFall.id("animations/item/isosang_sword.animation.json");}
    }
}
