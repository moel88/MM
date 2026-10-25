package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.stand.StandBeatdown;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Star Platinum's beatdown (2.64.0): the one struck is drawn where its cutscene shows them (StandBeatdown.offset), held,
 * thrown up, rushed in the air and sent flying, while they are held where they were: their model, name, fire and aura
 * go there together (the dispatcher moves an entity's drawing by its renderer's offset), and they are drawn wherever
 * that is, even where they are is out of sight.
 */
@Mixin(EntityRenderer.class)
public abstract class BeatdownRenderMixin {
    @Inject(method="getPositionOffset",at=@At("RETURN"),cancellable=true)
    private void botand$beatdownPlace(Entity e,float delta,CallbackInfoReturnable<Vec3d> ci){
        var offset=StandBeatdown.offset(e,delta);if(offset!=null)ci.setReturnValue(ci.getReturnValue().add(offset));
    }
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void botand$beatdownSeen(Entity e,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> ci){
        var offset=StandBeatdown.offset(e,1);
        if(offset!=null&&frustum.isVisible(e.getBoundingBox().offset(offset).expand(1)))ci.setReturnValue(true);
    }
}
