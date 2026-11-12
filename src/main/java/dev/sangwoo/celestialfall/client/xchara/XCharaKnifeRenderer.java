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
    /**
     * (2.122.0) How many times its size the blade is drawn for this one draw, the rod as it is (their cleave: "손잡이는
     * 상관없고 칼날 부분을 엄청 키워줘"): about its heel, so it still sits on the rod's end. Set as {@link #hack} is; 1
     * otherwise.
     */
    public static float blade=1;
    @Override public void renderRecursively(net.minecraft.client.util.math.MatrixStack matrices,XCharaKnifeItem item,software.bernie.geckolib.cache.object.GeoBone bone,net.minecraft.client.render.RenderLayer layer,
        net.minecraft.client.render.VertexConsumerProvider buffers,net.minecraft.client.render.VertexConsumer buffer,boolean reRender,float delta,int light,int overlay,float red,float green,float blue,float alpha){
        float k=blade;
        if(k!=1&&"blade".equals(bone.getName())){
            matrices.push();
            matrices.translate(0,HackKnife.u(HackKnife.Y_HEEL)*(1-k),0);matrices.scale(k,k,k);
            super.renderRecursively(matrices,item,bone,layer,buffers,buffer,reRender,delta,light,overlay,red,green,blue,alpha);
            matrices.pop();
            return;
        }
        super.renderRecursively(matrices,item,bone,layer,buffers,buffer,reRender,delta,light,overlay,red,green,blue,alpha);
    }
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
