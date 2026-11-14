package dev.sangwoo.celestialfall.mixin.client;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** H's ascent and landing slide move the model beyond its frozen server anchor (and Lee So-sang's H again her ride round the sky). */
@Mixin(EntityRenderer.class)
public abstract class KagenoCullingMixin {
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void botand$airborneActor(Entity e,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> ci){
        var c=dev.sangwoo.celestialfall.client.kageno.KagenoClips.animated(e.getUuid());if(c!=null&&c.skill()==KagenoRules.H&&c.age(0)<KagenoRules.DURATION[KagenoRules.H])ci.setReturnValue(true);
        // (2.101.1) Lee So-sang's H again: her body rides round the sky away from where she really is (IsosangClient.shift)
        else if(dev.sangwoo.celestialfall.client.isosang.IsosangClient.finaleOf(e.getUuid())!=null)ci.setReturnValue(true);
        // (2.121.0) X Chara's cleave: the body is drawn high over where its user stands through its leap
        else if(dev.sangwoo.celestialfall.client.xchara.KnifeClient.cleaveOf(e.getUuid())!=null)ci.setReturnValue(true);
        // (2.128.0) X Chara's slam: the body is drawn up in the air over where its user stands, and down its dive
        else if(dev.sangwoo.celestialfall.client.xchara.KnifeClient.slamOf(e.getUuid())!=null)ci.setReturnValue(true);
        // (2.129.0) X Chara's three cuts: the body is drawn along its way forward from where its user stands
        else if(dev.sangwoo.celestialfall.client.xchara.KnifeClient.cutterOf(e.getUuid())!=null)ci.setReturnValue(true);
        // (2.132.0) X Chara's rain of knives: the body is drawn along its run from where its user stands
        else if(dev.sangwoo.celestialfall.client.xchara.KnifeClient.rainOf(e.getUuid())!=null)ci.setReturnValue(true);
    }
}
