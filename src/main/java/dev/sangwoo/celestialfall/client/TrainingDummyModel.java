package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.combat.*;
import dev.sangwoo.celestialfall.training.*;
import net.minecraft.client.render.entity.model.BipedEntityModel;

/** The same segmented shoulders, elbows, hips and knees as the player motion engine. */
public final class TrainingDummyModel extends BipedEntityModel<TrainingDummyEntity> {
    public TrainingDummyModel(){super(MotionRig.player(false));hat.visible=false;}
    @Override public void setAngles(TrainingDummyEntity d,float phase,float amount,float animation,float yaw,float pitch) {
        MotionRig.reset(this);float delta=animation-d.age,time=d.getWorld().getTime()+delta;
        // The Atomic dummy's body plays the caster's Atomic as a player's would (user request, 2.38.2), fading in and out.
        var atomic=AtomicClient.poseCast(d.getUuid());
        if(atomic!=null){
            dev.sangwoo.celestialfall.client.kageno.AtomicPose.apply(this,atomic.age(delta)/20f);hat.visible=false;
            if(delta>=0&&delta<=1.001f)PoseBlend.apply(d.getUuid(),this,"atomic",3,animation);
            return;
        }
        MotionPose pose;
        var reaction=HitStunClient.reaction(d);
        if(reaction!=null)pose=MotionClips.hitReaction(time-reaction.hitAt(),reaction.strike());
        else if(time<d.brokenUntil())pose=MotionClips.broken(time-(d.brokenUntil()-CombatState.BREAK_TICKS));
        else if(d.strike()>0)pose=MotionClips.attack(d.strike(),time-d.strikeStart());
        else if(d.guarding())pose=MotionClips.guard(animation,d.guardPressure());
        else pose=MotionClips.locomotion(phase,Math.min(1,amount*3),1,0,0,d.kind()==DummyKind.ATTACK?1:0,animation);
        MotionRig.apply(this,pose,yaw,pitch);hat.visible=false;
        if(delta>=0&&delta<=1.001f)PoseBlend.apply(d.getUuid(),this,"dummy",3,animation);
    }
}
