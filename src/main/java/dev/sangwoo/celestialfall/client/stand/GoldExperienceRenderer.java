package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.GoldExperience;
import dev.sangwoo.celestialfall.stand.GoldExperienceEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws Gold Experience's figure (2.145.0) in its skin (2.156.0, {@link GoldSkin}): its own look, the box-built figure,
 * as GeckoLib draws its cubes; the skin "고퀄리티", the model the user sent (2.155.0, {@link GoldExperienceMesh}), skinned
 * by the bones' places as GeckoLib goes down its skeleton. Each body is drawn with its skin's model, size and shadow (a
 * stand by its user's skin, the figure by its own).
 */
public final class GoldExperienceRenderer extends GeoEntityRenderer<GoldExperienceEntity> {
    private final java.util.EnumMap<GoldSkin,GoldExperienceModel> models=new java.util.EnumMap<>(GoldSkin.class);
    /** The model of the body being drawn (GeckoLib asks for it as it draws). */
    private GoldExperienceModel current;
    private GoldExperienceMesh mesh;
    private Matrix4f[] poses;
    private Matrix3f[] normals;
    private boolean[] seen;
    /** For the capture tools: how many bodies have been drawn in each skin (2.156.0, by its ordinal). */
    public static final long[] DRAWN=new long[GoldSkin.values().length];
    public GoldExperienceRenderer(EntityRendererFactory.Context context){
        super(context,new GoldExperienceModel(GoldSkin.ORIGINAL));
        models.put(GoldSkin.ORIGINAL,(GoldExperienceModel)super.getGeoModel());models.put(GoldSkin.HQ,new GoldExperienceModel(GoldSkin.HQ));
        current=models.get(GoldSkin.ORIGINAL);withScale(GoldSkin.ORIGINAL.scale);shadowRadius=GoldSkin.ORIGINAL.shadow;
    }
    public static void initialize(){EntityRendererRegistry.register(GoldExperience.TYPE,GoldExperienceRenderer::new);}
    @Override public GeoModel<GoldExperienceEntity> getGeoModel(){return current!=null?current:super.getGeoModel();}
    /** The skin a body is drawn in: a stand's its user's (GoldPuppet), the figure's its own. */
    static GoldSkin skin(GoldExperienceEntity e){return GoldSkin.of(e.skin());}
    /**
     * (2.146.0) A stand beside its user is drawn at the size, solidity and colour of its look (see-through while it
     * fades in or out, and for its afterimages), leaning about its waist as it follows them; its shadow as dark as it
     * is solid. The figure ("/botand gold") has no look and is drawn as it is.
     */
    @Override public void render(GoldExperienceEntity e,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light){
        var skin=skin(e);current=models.get(skin);withScale(skin.scale);shadowRadius=skin.shadow;DRAWN[skin.ordinal()]++;
        var look=e.look(delta);
        if(look==null){shadowOpacity=1;super.render(e,yaw,delta,matrices,buffers,light);return;}
        shadowOpacity=look.alpha*look.shadow;
        matrices.push();
        if(look.lean!=0||look.roll!=0){
            // forward is (-sin yaw, cos yaw): a turn about (up x forward) tips its head forward, one about forward tips it to its right
            double r=Math.toRadians(yaw);float fx=(float)-Math.sin(r),fz=(float)Math.cos(r);
            matrices.translate(0,skin.waist*look.scale,0);
            matrices.multiply(new org.joml.Quaternionf().rotateAxis((float)Math.toRadians(look.lean),fz,0,-fx).rotateAxis((float)Math.toRadians(-look.roll),fx,0,fz));
            matrices.translate(0,-skin.waist*look.scale,0);
        }
        matrices.scale(look.scale,look.scale,look.scale);
        super.render(e,yaw,delta,matrices,buffers,light);
        matrices.pop();
    }
    /**
     * The box-built figure: an afterimage of the rush's arms (2.147.0) draws each arm, from its shoulder down, as strongly
     * as its look shows it: the arm striking out, not the one drawn back (as Star Platinum's do).
     */
    @Override public void renderRecursively(MatrixStack matrices,GoldExperienceEntity e,GeoBone bone,RenderLayer layer,VertexConsumerProvider buffers,VertexConsumer buffer,boolean reRender,float delta,int light,int overlay,float red,float green,float blue,float alpha){
        String name=bone.getName();
        if(current.skin.mesh==null&&(name.equals("left_arm")||name.equals("right_arm"))){var look=e.look(delta);if(look!=null&&look.armsOnly)alpha*=name.equals("left_arm")?look.showL:look.showR;}
        super.renderRecursively(matrices,e,bone,layer,buffers,buffer,reRender,delta,light,overlay,red,green,blue,alpha);
    }
    /**
     * The box-built figure's cubes are drawn as GeckoLib draws them. (2.155.0) The body the user sent: where GeckoLib would
     * draw a bone's cubes it is placed as the body needs it, the matrix that takes the model's rest pose to the screen for
     * that bone kept for {@link #actuallyRender}.
     */
    @Override public void renderCubesOfBone(MatrixStack matrices,GeoBone bone,VertexConsumer buffer,int light,int overlay,float red,float green,float blue,float alpha){
        if(current.skin.mesh==null){super.renderCubesOfBone(matrices,bone,buffer,light,overlay,red,green,blue,alpha);return;}
        var m=mesh();int i=m.bone(bone.getName());
        if(i<0)return;
        poses[i].set(matrices.peek().getPositionMatrix());normals[i].set(matrices.peek().getNormalMatrix());seen[i]=true;
    }
    /**
     * The body the user sent: goes down the skeleton (placing every bone), then draws the body skinned by those places. An
     * afterimage of the rush's arms (2.147.0) draws each arm, from its shoulder down, as strongly as its look shows it.
     */
    @Override public void actuallyRender(MatrixStack matrices,GoldExperienceEntity e,BakedGeoModel model,RenderLayer layer,VertexConsumerProvider buffers,VertexConsumer buffer,boolean reRender,float delta,int light,int overlay,float red,float green,float blue,float alpha){
        if(current.skin.mesh==null){super.actuallyRender(matrices,e,model,layer,buffers,buffer,reRender,delta,light,overlay,red,green,blue,alpha);return;}
        var m=mesh();java.util.Arrays.fill(seen,false);
        super.actuallyRender(matrices,e,model,layer,buffers,buffer,reRender,delta,light,overlay,red,green,blue,alpha);
        var look=e.look(delta);boolean armsOnly=look!=null&&look.armsOnly;
        m.skin(poses,normals,seen);
        m.draw(buffer,light,overlay,red,green,blue,alpha,armsOnly,armsOnly?look.showL:1,armsOnly?look.showR:1);
    }
    private GoldExperienceMesh mesh(){
        if(mesh==null){
            mesh=GoldExperienceMesh.get();int n=mesh.bones.length;
            poses=new Matrix4f[n];normals=new Matrix3f[n];seen=new boolean[n];
            for(int i=0;i<n;i++){poses[i]=new Matrix4f();normals[i]=new Matrix3f();}
        }
        return mesh;
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
