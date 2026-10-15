package dev.sangwoo.celestialfall.mixin.client;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** H's ascent and landing slide move the model beyond its frozen server anchor. */
@Mixin(EntityRenderer.class)
public abstract class KagenoCullingMixin {
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void botand$airborneActor(Entity e,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> ci){
        var c=StandClient.action(e.getUuid());if(c!=null&&c.skill()==KagenoRules.H&&c.age(0)<KagenoRules.DURATION[KagenoRules.H])ci.setReturnValue(true);
    }
}
