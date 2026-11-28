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
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws Gold Experience's figure (2.145.0): its GeckoLib model is its skeleton, which the stances turn; its body is the
 * model the user sent (2.155.0, {@link GoldExperienceMesh}), skinned by the bones' places as GeckoLib goes down the
 * skeleton.
 */
public final class GoldExperienceRenderer extends GeoEntityRenderer<GoldExperienceEntity> {
    /**
     * The model stands 40.49 units to the top of its helmet; at this size it stands 2.0 blocks, as tall as Star
     * Platinum to the top of its head (44.35 units at {@link StarPlatinumRenderer#SCALE}, its hair above that).
     * (2.146.1; .72 and a player's 1.8 blocks before, which read as too small beside Star Platinum. 2.146.2: .789 from
     * .8, its head a little larger and so a little taller. 2.155.0: the body the user sent, set to the same height.)
     */
    public static final float SCALE=.789f;
    /** The model's height to the top of its helmet (units, tools/stand/import_gold_experience.py). */
    public static final float HEIGHT=40.49f;
    public GoldExperienceRenderer(EntityRendererFactory.Context context){super(context,new GoldExperienceModel());withScale(SCALE);shadowRadius=.45f;}
    public static void initialize(){EntityRendererRegistry.register(GoldExperience.TYPE,GoldExperienceRenderer::new);}
    /** The height (blocks above its feet) it leans about as it follows its user: its waist (2.155.0: this body's hips). */
    static final float WAIST=26f/16*SCALE;
    private GoldExperienceMesh mesh;
    private Matrix4f[] poses;
    private Matrix3f[] normals;
    private boolean[] seen;
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
    /**
     * (2.155.0) Where GeckoLib would draw a bone's cubes it is placed as the body needs it: the matrix that takes the
     * model's rest pose to the screen for that bone, kept for {@link #actuallyRender}.
     */
    @Override public void renderCubesOfBone(MatrixStack matrices,GeoBone bone,VertexConsumer buffer,int light,int overlay,float red,float green,float blue,float alpha){
        var m=mesh();int i=m.bone(bone.getName());
        if(i<0)return;
        poses[i].set(matrices.peek().getPositionMatrix());normals[i].set(matrices.peek().getNormalMatrix());seen[i]=true;
    }
    /**
     * Goes down the skeleton (placing every bone), then draws the body skinned by those places. An afterimage of the rush's
     * arms (2.147.0) draws each arm, from its shoulder down, as strongly as its look shows it: the arm striking out, not
     * the one drawn back (as Star Platinum's do).
     */
    @Override public void actuallyRender(MatrixStack matrices,GoldExperienceEntity e,BakedGeoModel model,RenderLayer layer,VertexConsumerProvider buffers,VertexConsumer buffer,boolean reRender,float delta,int light,int overlay,float red,float green,float blue,float alpha){
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
