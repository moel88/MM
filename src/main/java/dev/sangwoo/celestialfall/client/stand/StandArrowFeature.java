package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import dev.sangwoo.celestialfall.client.MotionRig;
import dev.sangwoo.celestialfall.meteor.MeteorRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The stand arrow in the hands and the chest of one driving it into themselves (2.70.0, ArrowStabPose): the item drawn
 * as two sprites crossed along its shaft (a flat sprite seen edge on is a line: so it shows from every side), on the
 * body as it is drawn this frame. Until it goes in it rides in the fists as they are drawn (the body may still be
 * fading in from what it did before); then it stands out of the chest where it went in, moving with it, and is drawn
 * into it. The arrow in the hand the game would draw is not drawn meanwhile (StandHeldItemFeatureMixin). From one's
 * own eyes the forearms and the arrow have their own path ({@link #renderFirstPerson}).
 */
public final class StandArrowFeature extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final float RAD=(float)Math.PI/180;
    private static ItemStack arrow;
    public StandArrowFeature(FeatureRendererContext<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> context){super(context);}
    static ItemStack arrow(){if(arrow==null)arrow=new ItemStack(MeteorRegistry.STAND_ARROW);return arrow;}

    @Override public void render(MatrixStack matrices,VertexConsumerProvider buffers,int light,AbstractClientPlayerEntity player,float limbAngle,float limbDistance,float tickDelta,float animationProgress,float headYaw,float headPitch){
        var f=StandArrowClient.frame(player,tickDelta);if(f==null)return;
        var a=f.arrow();if(a==null||a.sink()>.97f)return;
        var model=getContextModel();
        if(!a.in()){
            // in the fists as drawn: moved by where the drawn grip is from the pose's
            Vector3f right=drawnFist(model,model.rightArm,-1),left=drawnFist(model,model.leftArm,1);
            Vector3f drawn=new Vector3f(right).lerp(new Vector3f(right).add(left).mul(.5f),f.join());
            Vector3f posed=new Vector3f(f.rightFist()).lerp(new Vector3f(f.rightFist()).add(f.leftFist()).mul(.5f),f.join());
            a=new ArrowStabPose.Arrow(new Vector3f(a.tip()).add(drawn).sub(posed),a.way(),a.face(),a.sink(),false);
        }
        matrices.push();
        model.body.rotate(matrices);
        draw(player,a,matrices,buffers,light);
        matrices.pop();
    }
    /** The arrow (torso blocks on the stack) as two sprites crossed along its shaft. */
    static void draw(AbstractClientPlayerEntity player,ArrowStabPose.Arrow a,MatrixStack matrices,VertexConsumerProvider buffers,int light){
        var renderer=MinecraftClient.getInstance().getItemRenderer();
        for(int turn=0;turn<2;turn++){
            matrices.push();
            transform(matrices,a.matrix(turn));
            matrices.translate(.5f,.5f,.5f);
            renderer.renderItem(player,arrow(),ModelTransformationMode.NONE,false,matrices,buffers,player.getWorld(),light,OverlayTexture.DEFAULT_UV,player.getId()*2+turn);
            matrices.pop();
        }
    }
    /** Where an arm of the drawn model closes its fist (torso px): from its part's pivot, turn and elbow, then into the torso's frame. */
    static Vector3f drawnFist(PlayerEntityModel<?> model,ModelPart arm,float side){
        Vector3f lower=new Vector3f(side,4.5f,0).rotateX(arm.getChild("elbow").pitch).add(0,4,0);
        Vector3f m=new Quaternionf().rotationZYX(arm.roll,arm.yaw,arm.pitch).transform(lower).add(arm.pivotX,arm.pivotY,arm.pivotZ);
        var body=model.body;
        return new Quaternionf().rotationZYX(body.roll,body.yaw,body.pitch).conjugate().transform(m.sub(body.pivotX,body.pivotY,body.pivotZ));
    }
    /** Multiplies the stack by a move, a turn and an even scale (so the normals only turn). */
    static void transform(MatrixStack m,Matrix4f a){
        Vector3f move=a.getTranslation(new Vector3f());float scale=a.getScale(new Vector3f()).x;
        m.translate(move.x,move.y,move.z);m.multiply(a.getUnnormalizedRotation(new Quaternionf()).normalize());m.scale(scale,scale,scale);
    }

    private static MotionPlayerModel normal,slim;
    /**
     * From one's own eyes: the forearms coming up to the fists from below the picture (the upper arms, close to the
     * eye, would fill it) and the arrow in them until it goes down out of sight (ArrowStabPose.view).
     */
    public static void renderFirstPerson(AbstractClientPlayerEntity p,float delta,MatrixStack m,VertexConsumerProvider vertices,int light){
        var s=StandArrowClient.of(p.getUuid());if(s==null)return;
        var v=ArrowStabPose.view(s.age(delta),s.fate,s.shows());
        m.push();
        // the neck's model pixels to the view: 2.16 px under the eye and 1 px behind it, turned to face the screen
        m.translate(0,-.135,.06);m.scale(-1,-1,1);
        if(!p.isInvisible()){
            boolean thin="slim".equals(p.getModel());
            if(thin&&slim==null)slim=new MotionPlayerModel(true);
            if(!thin&&normal==null)normal=new MotionPlayerModel(false);
            var model=thin?slim:normal;
            MotionRig.reset(model);
            pose(model.rightArm,v.rightShoulder(),v.rightArm());pose(model.leftArm,v.leftShoulder(),v.leftArm());
            model.overlays();
            var skin=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(p.getSkinTexture()));
            forearm(model.rightArm,m,skin,light);forearm(model.leftArm,m,skin,light);
            if(p.isPartVisible(PlayerModelPart.RIGHT_SLEEVE))forearm(model.rightSleeve,m,skin,light);
            if(p.isPartVisible(PlayerModelPart.LEFT_SLEEVE))forearm(model.leftSleeve,m,skin,light);
        }
        if(v.arrow()!=null)draw(p,v.arrow(),m,vertices,light);
        m.pop();
    }
    private static void forearm(ModelPart arm,MatrixStack m,net.minecraft.client.render.VertexConsumer skin,int light){
        m.push();arm.rotate(m);arm.getChild("elbow").render(m,skin,light,OverlayTexture.DEFAULT_UV);m.pop();
    }
    private static void pose(ModelPart arm,Vector3f shoulder,float[] a){
        arm.pivotX=shoulder.x;arm.pivotY=shoulder.y;arm.pivotZ=shoulder.z;
        arm.pitch=a[0]*RAD;arm.yaw=a[1]*RAD;arm.roll=a[2]*RAD;arm.getChild("elbow").pitch=a[3]*RAD;
    }
}
