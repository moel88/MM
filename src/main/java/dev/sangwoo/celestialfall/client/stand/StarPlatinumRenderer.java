package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinum;
import dev.sangwoo.celestialfall.stand.StarPlatinumEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws Star Platinum with its GeckoLib model (2.50.0): the figure, and a stand beside its user (2.51.0) at the size,
 * solidity and colour of its look; while it fades in or out, and for its afterimages, see-through.
 */
public final class StarPlatinumRenderer extends GeoEntityRenderer<StarPlatinumEntity> {
    /** The model stands 41 units (2.6 blocks) to the top of its head; at this size it stands a head over a player. */
    public static final float SCALE=.8f;
    public StarPlatinumRenderer(EntityRendererFactory.Context context){super(context,new StarPlatinumModel());withScale(SCALE);shadowRadius=.5f;}
    public static void initialize(){EntityRendererRegistry.register(StarPlatinum.TYPE,StarPlatinumRenderer::new);}
    @Override public void render(StarPlatinumEntity e,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light){
        var look=e.look(delta);
        // drawn after this: the shadow, as dark as the body is solid
        shadowOpacity=look.alpha*look.shadow;
        matrices.push();matrices.scale(look.scale,look.scale,look.scale);
        super.render(e,yaw,delta,matrices,buffers,light);
        matrices.pop();
    }
    @Override public RenderLayer getRenderType(StarPlatinumEntity e,Identifier texture,VertexConsumerProvider buffers,float delta){
        return e.look(delta).alpha<.999f?RenderLayer.getEntityTranslucent(texture):super.getRenderType(e,texture,buffers,delta);
    }
    @Override public Color getRenderColor(StarPlatinumEntity e,float delta,int light){
        var look=e.look(delta);return Color.ofRGBA(look.red,look.green,look.blue,look.alpha);
    }
}
