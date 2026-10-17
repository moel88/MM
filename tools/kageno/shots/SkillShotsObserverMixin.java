package dev.sangwoo.botandshots.mixin;

import dev.sangwoo.botandshots.SkillShots;
import dev.sangwoo.celestialfall.client.AtomicClient;
import dev.sangwoo.celestialfall.client.atomic.AtomicPillar;
import dev.sangwoo.celestialfall.client.atomic.AtomicPillarShape;
import dev.sangwoo.celestialfall.client.atomic.AtomicStage;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Visual QA: a fixed or following camera ("far", "track") is an onlooker, not the casting player's own camera, so G's
 * hit view goes by where that camera is (inside the column as far as it has opened) and never by the caster's spot.
 */
@Mixin(AtomicPillar.class)
public abstract class SkillShotsObserverMixin {
    @Inject(method="interior",at=@At("HEAD"),cancellable=true)
    private static void shots$onlooker(AtomicClient.Cast c,Vec3d cam,float sec,CallbackInfoReturnable<Boolean> ci){
        if(SkillShots.farPose()==null)return;
        ci.setReturnValue(AtomicPillarShape.inside(AtomicStage.visual(sec),cam.x-c.origin.x,cam.y-c.origin.y,cam.z-c.origin.z,c.radius));
    }
}
