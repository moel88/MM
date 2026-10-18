package dev.sangwoo.celestialfall.client.integration;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.TrainingDummyModel;
import dev.sangwoo.celestialfall.training.TrainingDummyEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.util.RenderUtils;

/** GeckoLib geometry with the original combat pose sampler; no duplicate player renderer. */
public final class GeckoDummyModel extends GeoModel<TrainingDummyEntity> {
    private final TrainingDummyModel reference=new TrainingDummyModel();
    private static long frames;
    public static long renderedFrames(){return frames;}
    @Override public Identifier getModelResource(TrainingDummyEntity d){return CelestialFall.id("geo/training_dummy.geo.json");}
    @Override public Identifier getTextureResource(TrainingDummyEntity d){return CelestialFall.id("textures/entity/training_dummy.png");}
    @Override public Identifier getAnimationResource(TrainingDummyEntity d){return CelestialFall.id("animations/training_dummy.animation.json");}
    @Override public void setCustomAnimations(TrainingDummyEntity d,long id,AnimationState<TrainingDummyEntity> state){
        float delta=state.getPartialTick();
        float body=MathHelper.lerpAngleDegrees(delta,d.prevBodyYaw,d.bodyYaw);
        float head=MathHelper.lerpAngleDegrees(delta,d.prevHeadYaw,d.headYaw);
        reference.setAngles(d,d.limbAnimator.getPos(delta),d.limbAnimator.getSpeed(delta),d.age+delta,MathHelper.wrapDegrees(head-body),d.getPitch(delta));
        copy("head",reference.head,0,0,0);copy("body",reference.body,0,0,0);
        copy("right_arm",reference.rightArm,-5,2,0);copy("left_arm",reference.leftArm,5,2,0);
        copy("right_leg",reference.rightLeg,-1.9f,12,0);copy("left_leg",reference.leftLeg,1.9f,12,0);
        copy("right_elbow",reference.rightArm.getChild("elbow"),0,4,0);
        copy("left_elbow",reference.leftArm.getChild("elbow"),0,4,0);
        copy("right_knee",reference.rightLeg.getChild("knee"),0,6,0);
        copy("left_knee",reference.leftLeg.getChild("knee"),0,6,0);
        frames++;
    }
    private void copy(String name,ModelPart part,float x,float y,float z){
        getBone(name).ifPresent(b->{RenderUtils.matchModelPartRot(part,b);
            b.setPosX(part.pivotX-x);b.setPosY(y-part.pivotY);b.setPosZ(part.pivotZ-z);});
    }
}
