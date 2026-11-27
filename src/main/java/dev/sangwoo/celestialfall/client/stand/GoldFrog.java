package dev.sangwoo.celestialfall.client.stand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.FrogEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The frog of Gold Experience's T (2.151.0: GoldLife has its moments), drawn on its user's body as it is drawn this
 * frame: the game's own frog (its model and its three skins, one chosen by the T), a little over half its size.
 * <ul>
 * <li>Held up in the raised hand the item shrinks away in a flash and the frog grows out of it, a little too big for a
 * moment; it leaps onto the shoulder of the other arm and sits there, riding it as the arm moves.</li>
 * <li>Each blow it turns back it croaks (its throat swells) and flicks its tongue, turned toward the striker, glowing
 * gold.</li>
 * <li>Its time nearly up it leaps back into the hand held up again and shrinks into the item, which grows back out of it;
 * ended early, it bursts into light where it is.</li>
 * </ul>
 * Its animations are the game frog's, run on the T's clock by a body of its own that is never in the world.
 */
public final class GoldFrog extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    /** Its size on the shoulder (of the game's frog). */
    static final float SIZE=.55f;
    /** How long it stays turned to a striker after a blow it turned back (seconds), and how long its croak and its tongue last. */
    static final float TURNED=.7f,CROAK=2.6f,TONGUE=.5f;
    static final Identifier[] SKINS={new Identifier("textures/entity/frog/temperate_frog.png"),new Identifier("textures/entity/frog/warm_frog.png"),new Identifier("textures/entity/frog/cold_frog.png")};
    private final FrogEntityModel<FrogEntity> model;
    /** Each user's frog's own body (never in the world): its animations' states. */
    private static final Map<UUID,Puppet> PUPPETS=new HashMap<>();
    /** For the checks: where each user's frog was last drawn (the body's model space), its hop and its size. */
    private static final Map<UUID,String> SEEN=new HashMap<>();
    public static String seen(UUID user){return SEEN.get(user);}
    private static final class Puppet{
        FrogEntity frog;float croakAt=-99,leapAt=-99;boolean leaping;
    }
    public GoldFrog(FeatureRendererContext<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> context,ModelPart frog){
        super(context);model=new FrogEntityModel<>(frog);
    }

    /** A new frog for {@code user} (GoldLife, as the T is heard). */
    static void born(UUID user){PUPPETS.remove(user);}
    /** {@code user}'s frog turned a blow back {@code at} seconds into it: it croaks and flicks its tongue. */
    static void croak(UUID user,float at){
        var p=PUPPETS.get(user);if(p==null||p.frog==null)return;
        int tick=Math.round(at*20);
        p.frog.croakingAnimationState.start(tick);p.frog.usingTongueAnimationState.start(tick);p.croakAt=at;
    }
    private static Puppet puppet(AbstractClientPlayerEntity player){
        var p=PUPPETS.get(player.getUuid());
        if(p==null||p.frog==null||p.frog.getWorld()!=player.getWorld()){
            p=new Puppet();p.frog=new FrogEntity(EntityType.FROG,player.getWorld());PUPPETS.put(player.getUuid(),p);
        }
        return p;
    }

    @Override public void render(MatrixStack matrices,VertexConsumerProvider buffers,int light,AbstractClientPlayerEntity player,float limbAngle,float limbDistance,float tickDelta,float animationProgress,float headYaw,float headPitch){
        var l=GoldLife.of(player.getUuid());
        if(l==null||!l.frog){PUPPETS.remove(player.getUuid());return;}
        float t=l.age(tickDelta);
        // (on a little past its end: the hand it went back into is still kept for its light)
        if(t<0||t>GoldLife.over(l)+.6f){PUPPETS.remove(player.getUuid());return;}
        var body=getContextModel();
        boolean right=player.getMainArm()!=Arm.LEFT,slim="slim".equals(player.getModel());
        Vector3f hand=hand(body,right,slim),seat=seat(body,!right,slim);
        // the item in the raised hand: there until the touch, shrinking away into the frog (and at the end growing back out of it)
        float item=GoldLife.itemShown(l,t);
        if(item>.01f&&!l.item.isEmpty()){
            matrices.push();
            Arm arm=right?Arm.RIGHT:Arm.LEFT;
            body.setArmAngle(arm,matrices);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.translate((right?1:-1)/16f,.125f,-.625f);
            matrices.scale(item,item,item);
            MinecraftClient.getInstance().getEntityRenderDispatcher().getHeldItemRenderer().renderItem(player,l.item,right?ModelTransformationMode.THIRD_PERSON_RIGHT_HAND:ModelTransformationMode.THIRD_PERSON_LEFT_HAND,!right,matrices,buffers,light);
            matrices.pop();
        }
        // on the hand, on the shoulder, or leaping between them in an arc
        float[] where=GoldLife.seat(l,t);
        Vector3f at=new Vector3f(hand).lerp(seat,where[0]);
        at.y-=(float)(.34*Math.sin(Math.PI*where[1]));
        // where they are in the world this frame, for their light (GoldLifeFx)
        GoldLife.drawn(player,matrices,hand,at);
        SEEN.put(player.getUuid(),String.format(java.util.Locale.ROOT,"hand %.2f %.2f %.2f, seat %.2f %.2f %.2f, at %.2f %.2f %.2f, where %.2f, size %.2f",hand.x,hand.y,hand.z,seat.x,seat.y,seat.z,at.x,at.y,at.z,where[0],GoldLife.frogShown(l,t)));
        float size=GoldLife.frogShown(l,t);if(size<=.005f)return;
        var p=puppet(player);var frog=p.frog;
        float clock=t*20;
        // its states run on the T's clock: the leap while it hops, the croak and the tongue after a blow it turned back
        boolean leaping=where[1]>0;
        if(leaping&&!p.leaping){frog.longJumpingAnimationState.start((int)clock);}
        if(!leaping&&p.leaping)frog.longJumpingAnimationState.stop();
        p.leaping=leaping;
        if(t-p.croakAt>CROAK)frog.croakingAnimationState.stop();
        if(t-p.croakAt>TONGUE)frog.usingTongueAnimationState.stop();
        // turned to whoever struck the blow it turned back, a moment (the way they are from the body, as an angle to its right)
        float turn=0;
        if(!l.reflected.isEmpty()){
            var last=l.reflected.get(l.reflected.size()-1);float since=t-last.at();
            if(since>=0&&since<TURNED&&player.getWorld().getEntityById(last.striker()) instanceof net.minecraft.entity.LivingEntity striker){
                float bodyYaw=MathHelper.lerpAngleDegrees(tickDelta,player.prevBodyYaw,player.bodyYaw);
                double dx=striker.getX()-player.getX(),dz=striker.getZ()-player.getZ();
                float to=MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx,dz))-bodyYaw);
                to=MathHelper.clamp(to,-100,100);
                float k=GoldLife.smooth(since/.08f)*(1-GoldLife.smooth((since-TURNED+.2f)/.2f));
                turn=to*k;
            }
        }
        float breathe=1+.025f*(float)Math.sin(t*Math.PI*2*.9);
        model.setAngles(frog,0,0,clock,0,0);
        matrices.push();
        matrices.translate(at.x,at.y,at.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(turn));
        float s=SIZE*size;matrices.scale(s,s*breathe,s);
        matrices.translate(0,-1.501f,0);
        var skin=SKINS[l.variant];
        model.render(matrices,buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(skin)),light,OverlayTexture.DEFAULT_UV,1,1,1,1);
        // golden as it comes out of the item, as it turns a blow back, and as it goes back into it
        float glow=glow(l,t);
        if(glow>.01f)model.render(matrices,buffers.getBuffer(RenderLayer.getEntityTranslucentEmissive(skin)),light,OverlayTexture.DEFAULT_UV,1,.82f,.32f,Math.min(1,glow));
        matrices.pop();
    }
    /** How brightly it glows gold {@code t} seconds into the T: as it is born, as it turns a blow back, as it goes back into the item or bursts. */
    static float glow(GoldLife.Life l,float t){
        float g=1-GoldLife.smooth((t-GoldLife.TOUCH)/.3f);
        if(t<GoldLife.TOUCH)g=0;
        for(var r:l.reflected){float since=t-r.at();if(since>=0&&since<.4f)g=Math.max(g,(1-since/.4f)*.9f);}
        if(Float.isNaN(l.early))g=Math.max(g,GoldLife.smooth((t-GoldLife.UNMORPH+.1f)/.2f));
        else g=Math.max(g,GoldLife.smooth((t-l.early)/.06f));
        return g;
    }
    /** A point given in sixteenths of a block on the stack as it stands, in the body's model space (blocks). */
    static Vector3f point(MatrixStack m,float x,float y,float z){var v=new Vector4f(x/16f,y/16f,z/16f,1).mul(m.peek().getPositionMatrix());return new Vector3f(v.x,v.y,v.z);}
    /** The middle of an arm across it (sixteenths): its box's middle off its joint. */
    static float across(boolean right,boolean slim){return right?(slim?-.5f:-1):(slim?.5f:1);}
    /** Where the frog sits on the raised hand (its fist's top, the arm held up before them), in the body's model space. */
    static Vector3f hand(PlayerEntityModel<?> body,boolean right,boolean slim){
        var m=new MatrixStack();
        body.setArmAngle(right?Arm.RIGHT:Arm.LEFT,m);
        return point(m,across(right,slim),8.6f,-2.3f);
    }
    /** Where it sits on the shoulder of the arm {@code right} (the top of the arm), in the body's model space. */
    static Vector3f seat(PlayerEntityModel<?> body,boolean right,boolean slim){
        var m=new MatrixStack();
        (right?body.rightArm:body.leftArm).rotate(m);
        return point(m,across(right,slim),-2.05f,0);
    }
}
