package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.KagenoMotion;
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

/** Keep the projected shadow attached to H's visual root during its ground slide. */
@Mixin(EntityRenderDispatcher.class)
public abstract class KagenoShadowMixin {
    @Inject(method="renderShadow",at=@At("HEAD"))
    private static void botand$slideShadow(MatrixStack m,VertexConsumerProvider v,Entity entity,float opacity,float delta,WorldView world,float radius,CallbackInfo ci){
        var cast=StandClient.action(entity.getUuid());
        if(cast==null||cast.skill()!=KagenoRules.H)return;
        m.push();var offset=Vec3d.fromPolar(0,cast.yaw()).multiply(KagenoMotion.hTravel(cast.age(delta)));
        m.translate(offset.x,0,offset.z);
    }
    @Inject(method="renderShadow",at=@At("RETURN"))
    private static void botand$restoreShadow(MatrixStack m,VertexConsumerProvider v,Entity entity,float opacity,float delta,WorldView world,float radius,CallbackInfo ci){
        var cast=StandClient.action(entity.getUuid());if(cast!=null&&cast.skill()==KagenoRules.H)m.pop();
    }
}
