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
    @Inject(method="renderShadow",at=@At("HEAD"))
    private static void botand$slideShadow(MatrixStack m,VertexConsumerProvider v,Entity entity,float opacity,float delta,WorldView world,float radius,CallbackInfo ci){
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
