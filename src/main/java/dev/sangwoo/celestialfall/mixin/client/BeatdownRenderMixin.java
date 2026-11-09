package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.stand.StandBeatdown;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Star Platinum's beatdown (2.64.0): the one struck is drawn where its cutscene shows them (StandBeatdown.offset), held,
 * thrown up, rushed in the air and sent flying, while they are held where they were: their model, name, fire and aura
 * go there together (the dispatcher moves an entity's drawing by its renderer's offset), and they are drawn wherever
 * that is, even where they are is out of sight; not at all while the cutscene's shot is from their own eyes (2.66.0).
 * While a client's camera is a cutscene's, nobody's name is drawn over their head there (2.66.0).
 */
@Mixin(EntityRenderer.class)
public abstract class BeatdownRenderMixin {
    @Inject(method="getPositionOffset",at=@At("RETURN"),cancellable=true)
    private void botand$beatdownPlace(Entity e,float delta,CallbackInfoReturnable<Vec3d> ci){
        var offset=StandBeatdown.offset(e,delta);if(offset!=null)ci.setReturnValue(ci.getReturnValue().add(offset));
        // (2.112.0) X Chara's G's strange space: the one marked hangs across from its user
        var space=dev.sangwoo.celestialfall.client.xchara.XCharaClient.offset(e,delta);if(space!=null)ci.setReturnValue(ci.getReturnValue().add(space));
    }
    /** No names over anyone's head while this client's camera is a beatdown's cutscene's (2.66.0: its close shots had the one struck's name fill the frame), as its HUD is hidden. */
    @Inject(method="render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"),cancellable=true)
    private void botand$beatdownNames(Entity e,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        if(StandBeatdown.cinematic()!=null||dev.sangwoo.celestialfall.client.xchara.XCharaClient.cinematic()!=null)ci.cancel();
    }
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void botand$beatdownSeen(Entity e,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> ci){
        // (2.66.0: not drawn while its shot is from their own eyes)
        if(StandBeatdown.hidden(e)){ci.setReturnValue(false);return;}
        // (2.83.0) Lee So-sang's G's own space: nobody else is in it (GStage)
        if(dev.sangwoo.celestialfall.client.isosang.GStage.hides(e)){ci.setReturnValue(false);return;}
        // (2.112.0) X Chara's G's strange space: only its two in it, drawn where it shows them
        if(dev.sangwoo.celestialfall.client.xchara.XCharaClient.hides(e)){ci.setReturnValue(false);return;}
        if(dev.sangwoo.celestialfall.client.xchara.XCharaClient.staged(e)){ci.setReturnValue(true);return;}
        // (2.93.0) Lee So-sang's Z: she is the orb of light as she flies from one of her formation's swords to another (SwordStep)
        if(dev.sangwoo.celestialfall.client.isosang.SwordStep.hides(e)){ci.setReturnValue(false);return;}
        // (the user's 2.87.0 preview) she is drawn wherever the cutscene's camera looks, posed in its shot (GStage.staged)
        if(dev.sangwoo.celestialfall.client.isosang.GStage.staged(e)){ci.setReturnValue(true);return;}
        var offset=StandBeatdown.offset(e,1);
        if(offset!=null&&frustum.isVisible(e.getBoundingBox().offset(offset).expand(1)))ci.setReturnValue(true);
    }
}
