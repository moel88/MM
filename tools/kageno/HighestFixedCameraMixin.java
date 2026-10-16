package dev.sangwoo.botandverify.mixin;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.AtomicClient;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Verification-only fixed world camera; never included in the playable mod. */
@Mixin(CinematicDirector.class)
public abstract class HighestFixedCameraMixin {
 private static boolean fixed(){return Boolean.getBoolean("botand.review.fixed")&&StandClient.cinematic()!=null;}
 @Inject(method="pose",at=@At("HEAD"),cancellable=true)
 private static void pose(Vec3d vanilla,float yaw,float pitch,float delta,CallbackInfoReturnable<CinematicDirector.Pose> ci){
  if(Boolean.getBoolean("botand.review.atomicFar")&&!AtomicClient.CASTS.isEmpty()){
   var c=AtomicClient.CASTS.values().iterator().next();
   Vec3d eye=c.origin.add(125,90,-156.25),target=c.origin.add(0,65,0),d=target.subtract(eye).normalize();
   ci.setReturnValue(new CinematicDirector.Pose(eye,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.asin(d.y)),0));return;
  }
  if(!fixed())return;var c=StandClient.cinematic();Vec3d f=Vec3d.fromPolar(0,c.yaw()),right=new Vec3d(-f.z,0,f.x);
  Vec3d eye=c.origin().add(f.multiply(-3.35)).add(right.multiply(-6.5)).add(0,2.2,0);
  Vec3d target=c.origin().add(f.multiply(-3.35)).add(0,.7,0),d=target.subtract(eye).normalize();
  ci.setReturnValue(new CinematicDirector.Pose(eye,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.asin(d.y)),0));
 }
 @Inject(method="fov",at=@At("HEAD"),cancellable=true)
 private static void fov(double vanilla,float delta,CallbackInfoReturnable<Double> ci){if(Boolean.getBoolean("botand.review.atomicFar")&&!AtomicClient.CASTS.isEmpty())ci.setReturnValue(60d);else if(fixed())ci.setReturnValue(40d);}
 @Inject(method="roll",at=@At("HEAD"),cancellable=true)
 private static void roll(float delta,CallbackInfoReturnable<Float> ci){if(fixed())ci.setReturnValue(0f);}
}
