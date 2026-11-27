package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.TrainingDummyModel;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import dev.sangwoo.celestialfall.stand.GoldFlyEntity;
import dev.sangwoo.celestialfall.training.TrainingDummyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;

/**
 * Gold Experience's fly as every client draws it (2.151.0, GoldFlyEntity): where {@link GoldLife#flyAt} has it about whom
 * it belongs to as this client draws them, not where the server last put its body.
 * <ul>
 * <li>Flying off ({@link GoldExperienceRules#FLY_FORMS} ticks): the part the punch struck off -- their own head, arm or
 * leg as their body is drawn, their own skin -- tumbles out in an arc and, in a flash of gold (GoldLifeFx), shrinks into
 * the fly, which grows out of it.</li>
 * <li>Flying: a housefly (GoldFlyModel), facing their face, its body bobbing and turning a little, its wings a blur of
 * beats (a few see-through copies of each wing at once).</li>
 * <li>Its time up it flies back into the part's place and shrinks away there.</li>
 * </ul>
 */
public final class GoldFlyRenderer extends EntityRenderer<GoldFlyEntity> {
    static final Identifier TEXTURE=CelestialFall.id("textures/entity/gold_fly.png");
    static final Identifier DUMMY_SKIN=CelestialFall.id("textures/entity/training_dummy.png");
    /** Its size (the model's parts are in sixteenths, as the game's models are: ModelPart makes them blocks): some 0.35 blocks long. */
    static final float SCALE=.63f;
    /** Its wings' beats a second, how far they beat (radians up and down from level) and how far back they sweep. */
    static final float BEATS=26,BEAT=.95f,SWEEP=.38f;
    private final GoldFlyModel model=new GoldFlyModel();
    private static TrainingDummyModel dummy;
    public GoldFlyRenderer(EntityRendererFactory.Context context){super(context);shadowRadius=0;}
    @Override public Identifier getTexture(GoldFlyEntity fly){return TEXTURE;}
    @Override protected boolean hasLabel(GoldFlyEntity fly){return false;}
    @Override public boolean shouldRender(GoldFlyEntity fly,Frustum frustum,double x,double y,double z){
        var at=GoldLife.flyAt(fly,MinecraftClient.getInstance().getTickDelta());if(at==null)return false;
        return at.squaredDistanceTo(x,y,z)<72*72&&(frustum==null||frustum.isVisible(new Box(at,at).expand(1.2)));
    }


    @Override public void render(GoldFlyEntity fly,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light){
        var at=GoldLife.flyAt(fly,delta);if(at==null)return;
        var base=fly.getLerpedPos(delta);double age=fly.clientAge(delta);
        int lit=WorldRenderer.getLightmapCoordinates(fly.getWorld(),BlockPos.ofFloored(at));
        var owner=fly.getWorld().getEntityById(fly.ownerId()) instanceof LivingEntity l?l:null;
        matrices.push();
        matrices.translate(at.x-base.x,at.y-base.y,at.z-base.z);
        float part=age<FLY_FORMS?GoldLife.partShown(age/FLY_FORMS):0;
        if(part>.01f&&owner!=null)part(matrices,buffers,lit,owner,fly.part(),(float)(age/FLY_FORMS),fly.seed(),part,delta);
        float size=GoldLife.flyShown(age);
        if(size>.01f)fly(matrices,buffers,lit,fly,owner,at,age,size,delta);
        matrices.pop();
    }

    /** The fly: facing whom it belongs to, bobbing and turning a little, its wings beating in a blur. */
    private void fly(MatrixStack m,VertexConsumerProvider buffers,int light,GoldFlyEntity fly,LivingEntity owner,Vec3d at,double age,float size,float delta){
        float t=(float)(age/20);
        float face=owner==null?MathHelper.lerpAngleDegrees(delta,fly.prevClientHeading,fly.clientHeading)+180:
            (float)Math.toDegrees(Math.atan2(-(owner.getX()-at.x),owner.getZ()-at.z));
        int seed=fly.seed();
        face+=22*(float)Math.sin(t*Math.PI*2*.73+seed)+9*(float)Math.sin(t*Math.PI*2*2.3+seed*.7);
        float pitch=-8+10*(float)Math.sin(t*Math.PI*2*1.1+seed*.3);
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-face));
        m.scale(-SCALE*size,-SCALE*size,SCALE*size);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        // the body (its wings apart: see-through, drawn after)
        model.rightWing.visible=model.leftWing.visible=false;
        model.root.render(m,buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),light,OverlayTexture.DEFAULT_UV);
        model.rightWing.visible=model.leftWing.visible=true;
        // its wings: where they are this frame, and two faint copies at either end of their beat, a blur
        var wings=buffers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        float now=BEAT*(float)Math.sin(t*Math.PI*2*BEATS);
        m.push();model.thorax.rotate(m);
        for(int k=0;k<3;k++){
            float beat=k==0?now:k==1?BEAT*.8f:-BEAT*.45f,alpha=k==0?.8f:.28f;
            model.wings(beat,SWEEP);
            model.rightWing.render(m,wings,light,OverlayTexture.DEFAULT_UV,1,1,1,alpha);
            model.leftWing.render(m,wings,light,OverlayTexture.DEFAULT_UV,1,1,1,alpha);
        }
        m.pop();
        m.pop();
    }

    /**
     * The part flying off: whom it belongs to's own head, arm or leg as their body is drawn (a player's skin, or the
     * training dummy's), turned as their body faced and tumbling over as it goes. Nothing for a body of another shape.
     */
    private void part(MatrixStack m,VertexConsumerProvider buffers,int light,LivingEntity owner,int which,float u,int seed,float size,float delta){
        BipedEntityModel<?> model;Identifier skin;
        if(owner instanceof AbstractClientPlayerEntity player&&MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerEntityRenderer renderer){
            model=renderer.getModel();skin=player.getSkinTexture();
        }else if(owner instanceof TrainingDummyEntity){
            if(dummy==null)dummy=new TrainingDummyModel();
            model=dummy;skin=DUMMY_SKIN;
        }else return;
        ModelPart[] parts=parts(model,which);
        // the middle of the part as it hangs from its joint (sixteenths)
        Vector3f middle=switch(which){case HEAD->new Vector3f(0,-4,0);case RIGHT_ARM->new Vector3f(-1,4,0);case LEFT_ARM->new Vector3f(1,4,0);default->new Vector3f(0,6,0);};
        float body=MathHelper.lerpAngleDegrees(delta,owner.prevBodyYaw,owner.bodyYaw);
        double a=dev.sangwoo.celestialfall.client.kageno.Ease.hash(seed)*Math.PI*2;
        var axis=new Vector3f((float)Math.cos(a),.35f,(float)Math.sin(a)).normalize();
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-body));
        m.scale(-size,-size,size);
        m.multiply(new org.joml.Quaternionf().rotationAxis((float)(Math.PI*2*1.6*u),axis));
        m.translate(-middle.x/16f,-middle.y/16f,-middle.z/16f);
        var buffer=buffers.getBuffer(RenderLayer.getEntityTranslucent(skin));
        for(var p:parts){
            if(p==null)continue;
            var kept=p.getTransform();boolean shown=p.visible;
            ModelPart lower=p.hasChild("elbow")?p.getChild("elbow"):p.hasChild("knee")?p.getChild("knee"):null;
            var keptLower=lower==null?null:lower.getTransform();
            p.setTransform(ModelTransform.NONE);p.visible=true;
            if(lower!=null){lower.pitch=0;lower.yaw=0;lower.roll=0;}
            p.render(m,buffer,light,OverlayTexture.DEFAULT_UV);
            p.setTransform(kept);p.visible=shown;
            if(lower!=null)lower.setTransform(keptLower);
        }
        m.pop();
    }
    /** The model's parts that are part {@code which} (with a player's outer layer). */
    static ModelPart[] parts(BipedEntityModel<?> model,int which){
        boolean player=model instanceof PlayerEntityModel<?>;var p=player?(PlayerEntityModel<?>)model:null;
        return switch(which){
            case HEAD->new ModelPart[]{model.head,player?model.hat:null};
            case RIGHT_ARM->new ModelPart[]{model.rightArm,p==null?null:p.rightSleeve};
            case LEFT_ARM->new ModelPart[]{model.leftArm,p==null?null:p.leftSleeve};
            case RIGHT_LEG->new ModelPart[]{model.rightLeg,p==null?null:p.rightPants};
            default->new ModelPart[]{model.leftLeg,p==null?null:p.leftPants};
        };
    }
}
