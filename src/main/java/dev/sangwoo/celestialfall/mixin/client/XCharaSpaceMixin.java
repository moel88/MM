package dev.sangwoo.celestialfall.mixin.client;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * X Chara's G's strange space (2.112.0, XCharaClient): its two are drawn there in full light (it has no sun nor shade;
 * where they really stand is no matter, nor where the one marked is shown hanging, which may be in a hill).
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class XCharaSpaceMixin {
    @Inject(method="getLight",at=@At("RETURN"),cancellable=true)
    private <E extends Entity> void botand$spaceLight(E entity,float tickDelta,CallbackInfoReturnable<Integer> ci){
        int light=dev.sangwoo.celestialfall.client.xchara.XCharaClient.light(entity);
        if(light>=0)ci.setReturnValue(light);
    }
}
