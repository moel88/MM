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
    /**
     * The sword in hand while its holder's next combo is empowered (2.80.0, E): set by whoever draws it in a hand
     * (StandHeldItemFeatureMixin, HeldItemRendererMixin) for that one draw, 0 otherwise; above 0 the sword glows, its
     * lightning drawn once more over it, a blue sheen over the whole of it, pulsing.
     */
    public static float charge;
    /** The world's clock for the pulse (ticks, with the frame's share of one). */
    public static float clock;
    {
        addRenderLayer(new GeoRenderLayer<>(this){
            @Override public void render(MatrixStack matrices,IsosangSwordItem item,BakedGeoModel model,RenderLayer type,VertexConsumerProvider buffers,VertexConsumer buffer,float delta,int light,int overlay){
                if(charge<=0)return;
                float pulse=.75f+.25f*(float)Math.sin(clock*.35f);
                RenderLayer glow=RenderLayer.getEntityTranslucentEmissive(GLOW);
                getRenderer().reRender(model,matrices,buffers,item,glow,buffers.getBuffer(glow),delta,LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay,.7f,.85f,1,charge*pulse);
                RenderLayer sheen=RenderLayer.getEntityTranslucentEmissive(getTextureLocation(item));
                getRenderer().reRender(model,matrices,buffers,item,sheen,buffers.getBuffer(sheen),delta,LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay,.35f,.6f,1,.32f*charge*pulse);
            }
        });
    }
    /**
     * The sword as a thing of light (2.80.0, E): the empowered first's thrown swords and the empowered fourth's giant
     * sword, its model drawn see-through and lit through ({@link #alpha}, {@link #tint}, set for each draw), its
     * lightning over it.
     */
    public static final class Spectral extends GeoItemRenderer<IsosangSwordItem> {
        public static float alpha=1;public static int tint=0xffffff;
        public Spectral(){
            super(new Model());
            addRenderLayer(new GeoRenderLayer<>(this){
                @Override public void render(MatrixStack matrices,IsosangSwordItem item,BakedGeoModel model,RenderLayer type,VertexConsumerProvider buffers,VertexConsumer buffer,float delta,int light,int overlay){
                    RenderLayer glow=RenderLayer.getEntityTranslucentEmissive(GLOW);
                    getRenderer().reRender(model,matrices,buffers,item,glow,buffers.getBuffer(glow),delta,LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay,1,1,1,alpha);
                }
            });
        }
        @Override public RenderLayer getRenderType(IsosangSwordItem item,Identifier texture,VertexConsumerProvider buffers,float delta){return RenderLayer.getEntityTranslucentEmissive(texture);}
        @Override public software.bernie.geckolib.core.object.Color getRenderColor(IsosangSwordItem item,float delta,int light){
            return software.bernie.geckolib.core.object.Color.ofRGBA(((tint>>16)&255)/255f,((tint>>8)&255)/255f,(tint&255)/255f,alpha);
        }
    }
    static final class Model extends GeoModel<IsosangSwordItem> {
        @Override public Identifier getModelResource(IsosangSwordItem item){return CelestialFall.id("geo/item/isosang_sword.geo.json");}
        @Override public Identifier getTextureResource(IsosangSwordItem item){return CelestialFall.id("textures/item/isosang_sword_model.png");}
        @Override public Identifier getAnimationResource(IsosangSwordItem item){return CelestialFall.id("animations/item/isosang_sword.animation.json");}
    }
}
