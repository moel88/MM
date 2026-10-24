package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinum;
import dev.sangwoo.celestialfall.stand.StarPlatinumEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws Star Platinum with its GeckoLib model (2.50.0): the figure, and a stand beside its user (2.51.0) at the size,
 * solidity and colour of its look; while it fades in or out, and for its afterimages, see-through. A stand's aura is
 * drawn round it on the screen (StandAuraLayer, 2.53.0). Where that cannot be drawn (a shader pack, or post-processing
 * off) the stand glows as in 2.52.0 (user request: "스타플래티나를 조금 빛나게하고"): the same body drawn twice more over it,
 * a little larger each time, in light that adds to what is behind it and is not shaded by the world, a faint pale
 * violet blue sheen on the body and a soft rim round its outline, breathing with it and flaring as it bursts out.
 */
public final class StarPlatinumRenderer extends GeoEntityRenderer<StarPlatinumEntity> {
    /**
     * The model stands 41 units (2.6 blocks) to the top of its head; at this size it stands about half a head over a
     * player (2.52.0, user request: "사이즈도 조금만 줄이고": 0.8 until 2.51.0).
     */
    public static final float SCALE=.72f;
    /** The glow's colour: a pale violet blue, like the body's own. */
    static final float AURA_R=.55f,AURA_G=.64f,AURA_B=1f;
    /** The glow's shells: how much larger than the body each is drawn, and how bright. */
    static final float[][] SHELLS={{1.02f,.1f},{1.05f,.08f}};
    /** The height (blocks above its feet) of the middle the shells grow from: its waist. */
    static final float WAIST=24f/16*SCALE;
    /** The brightness of the shell being drawn, or -1 while the body itself is. */
    private float shell=-1;
    public StarPlatinumRenderer(EntityRendererFactory.Context context){super(context,new StarPlatinumModel());withScale(SCALE);shadowRadius=.45f;}
    public static void initialize(){EntityRendererRegistry.register(StarPlatinum.TYPE,StarPlatinumRenderer::new);}
    @Override public void render(StarPlatinumEntity e,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light){
        var look=e.look(delta);
        // drawn after this: the shadow, as dark as the body is solid
        shadowOpacity=look.alpha*look.shadow;
        matrices.push();matrices.scale(look.scale,look.scale,look.scale);
        super.render(e,yaw,delta,matrices,buffers,light);
        if(look.glow>0&&!StandAuraLayer.available()){
            // the shells keep the body's pose: GeckoLib does not pose the same body twice in a frame
            float breath=1+.12f*(float)Math.sin(2*Math.PI*(e.age+delta)/20/StarPlatinumMotion.BREATH);
            for(float[] s:SHELLS){
                shell=look.glow*s[1]*breath;
                matrices.push();matrices.translate(0,WAIST,0);matrices.scale(s[0],s[0],s[0]);matrices.translate(0,-WAIST,0);
                super.render(e,yaw,delta,matrices,buffers,light);
                matrices.pop();
            }
            shell=-1;
        }
        matrices.pop();
    }
    /**
     * An afterimage of the rush's arms (2.58.0) draws each arm, from its shoulder down, as strongly as its look shows it:
     * the arm striking out, not the one drawn back, so the afterimages are a storm of fists rather than a wall of arms.
     */
    @Override public void renderRecursively(MatrixStack matrices,StarPlatinumEntity e,GeoBone bone,RenderLayer layer,VertexConsumerProvider buffers,net.minecraft.client.render.VertexConsumer buffer,boolean reRender,float delta,int light,int overlay,float red,float green,float blue,float alpha){
        String name=bone.getName();
        if(name.equals("left_arm")||name.equals("right_arm")){var look=e.look(delta);if(look.armsOnly)alpha*=name.equals("left_arm")?look.showL:look.showR;}
        super.renderRecursively(matrices,e,bone,layer,buffers,buffer,reRender,delta,light,overlay,red,green,blue,alpha);
    }
    @Override public RenderLayer getRenderType(StarPlatinumEntity e,Identifier texture,VertexConsumerProvider buffers,float delta){
        if(shell>=0)return RenderLayer.getEyes(texture);
        return e.look(delta).alpha<.999f?RenderLayer.getEntityTranslucent(texture):super.getRenderType(e,texture,buffers,delta);
    }
    @Override public Color getRenderColor(StarPlatinumEntity e,float delta,int light){
        if(shell>=0)return Color.ofRGBA(AURA_R*shell,AURA_G*shell,AURA_B*shell,1);
        var look=e.look(delta);return Color.ofRGBA(look.red,look.green,look.blue,look.alpha);
    }
}
