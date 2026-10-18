package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.atomic.AtomicAmbience;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** G seen from outside its damage column: the sky and clouds darken toward violet around the pillar. */
@Mixin(ClientWorld.class)
public abstract class AtomicSkyMixin {
    @Inject(method="getSkyColor",at=@At("RETURN"),cancellable=true)
    private void botand$atomicSky(Vec3d cameraPos,float tickDelta,CallbackInfoReturnable<Vec3d> cir){
        cir.setReturnValue(AtomicAmbience.sky(cir.getReturnValue(),cameraPos,tickDelta));
    }
    @Inject(method="getCloudsColor",at=@At("RETURN"),cancellable=true)
    private void botand$atomicClouds(float tickDelta,CallbackInfoReturnable<Vec3d> cir){
        cir.setReturnValue(AtomicAmbience.clouds(cir.getReturnValue(),tickDelta));
    }
}
