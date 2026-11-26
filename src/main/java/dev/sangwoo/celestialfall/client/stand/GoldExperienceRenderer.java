package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.GoldExperience;
import dev.sangwoo.celestialfall.stand.GoldExperienceEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.object.Color;
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
    /** The height (blocks above its feet) it leans about as it follows its user: its waist. */
    static final float WAIST=23f/16*SCALE;
    /**
     * (2.146.0) A stand beside its user is drawn at the size, solidity and colour of its look (see-through while it
     * fades in or out, and for its afterimages), leaning about its waist as it follows them; its shadow as dark as it
     * is solid. The figure ("/botand gold") has no look and is drawn as it is.
     */
    @Override public void render(GoldExperienceEntity e,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light){
        var look=e.look(delta);
        if(look==null){shadowOpacity=1;super.render(e,yaw,delta,matrices,buffers,light);return;}
        shadowOpacity=look.alpha*look.shadow;
        matrices.push();
        if(look.lean!=0||look.roll!=0){
            // forward is (-sin yaw, cos yaw): a turn about (up x forward) tips its head forward, one about forward tips it to its right
            double r=Math.toRadians(yaw);float fx=(float)-Math.sin(r),fz=(float)Math.cos(r);
            matrices.translate(0,WAIST*look.scale,0);
            matrices.multiply(new org.joml.Quaternionf().rotateAxis((float)Math.toRadians(look.lean),fz,0,-fx).rotateAxis((float)Math.toRadians(-look.roll),fx,0,fz));
            matrices.translate(0,-WAIST*look.scale,0);
        }
        matrices.scale(look.scale,look.scale,look.scale);
        super.render(e,yaw,delta,matrices,buffers,light);
        matrices.pop();
    }
    @Override public RenderLayer getRenderType(GoldExperienceEntity e,Identifier texture,VertexConsumerProvider buffers,float delta){
        var look=e.look(delta);
        return look!=null&&look.alpha<.999f?RenderLayer.getEntityTranslucent(texture):super.getRenderType(e,texture,buffers,delta);
    }
    @Override public Color getRenderColor(GoldExperienceEntity e,float delta,int light){
        var look=e.look(delta);
        return look==null?super.getRenderColor(e,delta,light):Color.ofRGBA(look.red,look.green,look.blue,look.alpha);
    }
}
