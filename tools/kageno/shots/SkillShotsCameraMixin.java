package dev.sangwoo.botandshots.mixin;

import dev.sangwoo.botandshots.SkillShots;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Visual QA: a fixed far camera ("far=ex,ey,ez,tx,ty,tz,fov" relative to the cast spot) overrides every skill camera;
 * with "shake=1" it gets the Atomic camera shake a player standing there would (the game's renderer adds the roll
 * itself whenever no cinematic is running, so it comes from here only while one is).
 */
@Mixin(CinematicDirector.class)
public abstract class SkillShotsCameraMixin {
    @Inject(method="pose",at=@At("HEAD"),cancellable=true)
    private static void shots$pose(Vec3d vanilla,float yaw,float pitch,float delta,CallbackInfoReturnable<CinematicDirector.Pose> ci){
        var pose=SkillShots.farPose();if(pose==null)return;
        if(SkillShots.shake()){var s=dev.sangwoo.celestialfall.client.AtomicImmersion.at(pose.position(),delta);pose=new CinematicDirector.Pose(pose.position(),pose.yaw()+s.yaw(),pose.pitch()+s.pitch(),0);}
        ci.setReturnValue(pose);
    }
    @Inject(method="fov",at=@At("HEAD"),cancellable=true)
    private static void shots$fov(double vanilla,float delta,CallbackInfoReturnable<Double> ci){
        double fov=SkillShots.farFov();if(fov>0)ci.setReturnValue(fov);
    }
    @Inject(method="roll",at=@At("HEAD"),cancellable=true)
    private static void shots$roll(float delta,CallbackInfoReturnable<Float> ci){
        var pose=SkillShots.farPose();if(pose==null)return;
        ci.setReturnValue(SkillShots.shake()&&CinematicDirector.isActive()?dev.sangwoo.celestialfall.client.AtomicImmersion.at(pose.position(),delta).roll():0f);
    }
}
