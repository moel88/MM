package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the projected shadow under the animated root (slides, lunges, dashes). */
@Mixin(EntityRenderDispatcher.class)
public abstract class KagenoShadowMixin {
    @Inject(method="renderShadow",at=@At("HEAD"),cancellable=true)
    private static void botand$slideShadow(MatrixStack m,VertexConsumerProvider v,Entity entity,float opacity,float delta,WorldView world,float radius,CallbackInfo ci){
        // H targets kept out of the cutscene leave no shadow behind either, nor does whoever Star Platinum's beatdown
        // shows away from where they are (2.64.0).
        if(StandClient.cutsceneHidden(entity)||dev.sangwoo.celestialfall.client.stand.StandBeatdown.offset(entity,delta)!=null){ci.cancel();return;}
        // (2.83.0) nor anyone in Lee So-sang's G's own space, whose ground the world's is not (GStage): cancelled here,
        // before the push below (its pop at RETURN would not come; a second mixin cancelling first left the stack pushed)
        if(dev.sangwoo.celestialfall.client.isosang.GStage.hidden()||dev.sangwoo.celestialfall.client.xchara.XCharaClient.spaceHidden()){ci.cancel();return;}
        var cast=dev.sangwoo.celestialfall.client.kageno.KagenoClips.animated(entity.getUuid());
        if(cast==null)return;
        m.push();var offset=dev.sangwoo.celestialfall.client.kageno.KagenoClips.groundOffset(cast,delta);
        m.translate(offset.x,0,offset.z);
    }
    @Inject(method="renderShadow",at=@At("RETURN"))
    private static void botand$restoreShadow(MatrixStack m,VertexConsumerProvider v,Entity entity,float opacity,float delta,WorldView world,float radius,CallbackInfo ci){
        var cast=dev.sangwoo.celestialfall.client.kageno.KagenoClips.animated(entity.getUuid());if(cast!=null)m.pop();
    }
}
