package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.GoldTreeEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws Gold Experience's tree (2.152.0) with its GeckoLib model at its own size, its front the way its maker faced as
 * it burst up (the tree's own record of it, not a living thing's turning body), darkening as it withers. No shadow (it is
 * branches). Where it stands between the camera and the body the camera follows (its maker, flung on past it, with the
 * camera behind them), it is drawn see-through (GoldTreeGrowth.opacity): that body is never hidden behind it.
 */
public final class GoldTreeRenderer extends GeoEntityRenderer<GoldTreeEntity> {
    public GoldTreeRenderer(EntityRendererFactory.Context context){super(context,new GoldTreeModel());shadowRadius=0;}
    @Override protected void applyRotations(GoldTreeEntity e,MatrixStack matrices,float age,float yaw,float delta){
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f-e.facing()));
    }
    /** No name over it (GeckoLib would show a living thing's name when the camera is near it). */
    @Override public boolean hasLabel(GoldTreeEntity e){return false;}
    @Override public Color getRenderColor(GoldTreeEntity e,float delta,int light){
        double age=e.clientAge(delta);float k=GoldTreeGrowth.dimmed(age,e.witherAt()-e.born());
        return Color.ofRGBA(1-.5f*k,1-.55f*k,1-.6f*k,opacity(e,delta));
    }
    /** Seen through, its own see-through layer (its front faces only, writing no depth: whatever is behind it still shows). */
    @Override public RenderLayer getRenderType(GoldTreeEntity e,Identifier texture,VertexConsumerProvider buffers,float delta){
        return opacity(e,delta)<.995f?SeeThrough.layer(texture):super.getRenderType(e,texture,buffers,delta);
    }
    /** What shows of each tree, eased (GoldTreeGrowth.eased): its value and the client's time it was worked out (ticks). */
    private static final Map<Integer,double[]> SHOWN=new HashMap<>();
    /** How much of it shows this frame from this client's camera (all of it in the first person: no body to hide), easing toward what should. */
    static float opacity(GoldTreeEntity e,float delta){
        var mc=MinecraftClient.getInstance();
        if(mc.world==null)return 1;
        double now=mc.world.getTime()+delta;
        var was=SHOWN.get(e.getId());
        if(was!=null&&was[1]==now)return (float)was[0];
        float should=1;
        var camera=mc.gameRenderer.getCamera();
        if(camera!=null&&camera.isThirdPerson()&&camera.getFocusedEntity()!=null){
            var who=camera.getFocusedEntity();
            Vec3d c=camera.getPos(),b=who.getLerpedPos(delta).add(0,who.getHeight()*.55,0),t=e.getLerpedPos(delta);
            should=GoldTreeGrowth.opacity(t.x,t.y,t.z,c.x,c.y,c.z,b.x,b.y,b.z,e.clientAge(delta));
        }
        float shown=was==null?should:GoldTreeGrowth.eased((float)was[0],should,now-was[1]);
        if(SHOWN.size()>32)SHOWN.keySet().removeIf(id->mc.world.getEntityById(id)==null);
        SHOWN.put(e.getId(),new double[]{shown,now});
        return shown;
    }
    /**
     * The tree's see-through layer: the game's translucent entity shading, its back faces culled, writing colour only (no
     * depth), so that nothing drawn after it is hidden behind it. (A subclass only to reach the game's layer parts.)
     */
    private static final class SeeThrough extends RenderLayer {
        private SeeThrough(){super("celestialfall_unused",VertexFormats.POSITION,VertexFormat.DrawMode.QUADS,0,false,false,()->{},()->{});}
        private static final Function<Identifier,RenderLayer> LAYERS=Util.memoize(texture->RenderLayer.of("celestialfall_gold_tree_see_through",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,VertexFormat.DrawMode.QUADS,256,true,true,
            MultiPhaseParameters.builder().program(ENTITY_TRANSLUCENT_CULL_PROGRAM).texture(new Texture(texture,false,false)).transparency(TRANSLUCENT_TRANSPARENCY)
                .lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).writeMaskState(COLOR_MASK).build(true)));
        static RenderLayer layer(Identifier texture){return LAYERS.apply(texture);}
    }
}
