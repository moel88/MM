package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Shadow public abstract Vec3d getPos();
    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();
    @Shadow private boolean thirdPerson;
    @Shadow public abstract org.joml.Quaternionf getRotation();
    @Shadow public abstract org.joml.Vector3f getHorizontalPlane();
    @Shadow public abstract org.joml.Vector3f getVerticalPlane();
    @Shadow public abstract org.joml.Vector3f getDiagonalPlane();
    @Inject(method="update",at=@At("TAIL"))
    private void celestialfall$camera(BlockView area,Entity entity,boolean third,boolean inverse,float delta,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.integration.CinematicsBridge.ownsCamera())return;
        var menu=dev.sangwoo.celestialfall.client.title.TitleMapCamera.pose();
        if(menu!=null){setPos(menu.position());setRotation(menu.yaw(),menu.pitch());thirdPerson=true;return;}
        var base=dev.sangwoo.celestialfall.client.BotandCamera.pose(delta);
        if(base!=null) {setPos(base.position());setRotation(base.yaw(),base.pitch());thirdPerson=!dev.sangwoo.celestialfall.client.BotandCamera.STATE.firstPerson();}
        var pose=CinematicDirector.pose(getPos(),getYaw(),getPitch(),delta);
        if(pose!=null) {
            setPos(pose.position());setRotation(pose.yaw(),pose.pitch());thirdPerson=true;
            var q=getRotation().rotateZ((float)Math.toRadians(pose.roll()));
            getHorizontalPlane().set(0,0,1).rotate(q);
            getVerticalPlane().set(0,1,0).rotate(q);
            getDiagonalPlane().set(1,0,0).rotate(q);
        } else {
            // Offset the view reconstructed from mouse input each frame. Never
            // accumulate shake into BotandCamera.STATE or seize cinematic ownership.
            var kick=dev.sangwoo.celestialfall.client.kageno.KagenoCamera.shake(delta);
            if(kick!=null){
                setRotation(getYaw()+kick[0],getPitch()+kick[1]);
                var q=getRotation().rotateZ((float)Math.toRadians(kick[2]));
                getHorizontalPlane().set(0,0,1).rotate(q);getVerticalPlane().set(0,1,0).rotate(q);getDiagonalPlane().set(1,0,0).rotate(q);
            }
            // Star Platinum's punches landing (2.54.0)
            var punch=dev.sangwoo.celestialfall.client.stand.StandPunchFx.shake(getPos(),delta);
            if(punch!=null){
                setRotation(getYaw()+punch[0],getPitch()+punch[1]);
                var q=getRotation().rotateZ((float)Math.toRadians(punch[2]));
                getHorizontalPlane().set(0,0,1).rotate(q);getVerticalPlane().set(0,1,0).rotate(q);getDiagonalPlane().set(1,0,0).rotate(q);
            }
            // a meteor landing near (2.49.0)
            var meteor=dev.sangwoo.celestialfall.client.meteor.MeteorFx.shake(getPos(),delta);
            if(meteor!=null){
                setRotation(getYaw()+meteor[0],getPitch()+meteor[1]);
                var q=getRotation().rotateZ((float)Math.toRadians(meteor[2]));
                getHorizontalPlane().set(0,0,1).rotate(q);getVerticalPlane().set(0,1,0).rotate(q);getDiagonalPlane().set(1,0,0).rotate(q);
            }
            var blast=dev.sangwoo.celestialfall.client.AtomicImmersion.at(getPos(),delta);
            if(blast.yaw()!=0||blast.pitch()!=0||blast.roll()!=0){
                setRotation(getYaw()+blast.yaw(),getPitch()+blast.pitch());
                var q=getRotation().rotateZ((float)Math.toRadians(blast.roll()));
                getHorizontalPlane().set(0,0,1).rotate(q);
                getVerticalPlane().set(0,1,0).rotate(q);
                getDiagonalPlane().set(1,0,0).rotate(q);
            }
        }
    }
}
