package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.AtomicClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import dev.sangwoo.celestialfall.client.MotionArmorModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import java.util.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    protected PlayerEntityRendererMixin(EntityRendererFactory.Context context,PlayerEntityModel<AbstractClientPlayerEntity> model,float shadow) {super(context,model,shadow);}
    @Unique private PlayerEntityModel<AbstractClientPlayerEntity> botand$vanilla,botand$animated;
    @Unique private List<FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>>> botand$normalFeatures,botand$mapFeatures;
    @Unique private boolean botand$usingMap;
    @Unique private boolean botand$posed;
    @Inject(method="<init>",at=@At("TAIL"))
    private void botand$articulatedSkin(EntityRendererFactory.Context context,boolean slim,CallbackInfo ci) {
        botand$vanilla=this.model;botand$normalFeatures=new ArrayList<>(this.features);
        botand$animated=new MotionPlayerModel(slim);botand$mapFeatures=new ArrayList<>(this.features);
        botand$mapFeatures.removeIf(feature->feature instanceof ArmorFeatureRenderer);
        botand$mapFeatures.add(new ArmorFeatureRenderer<>(this,new MotionArmorModel(.5f),new MotionArmorModel(1),context.getModelManager()));
    }
    @Unique private void botand$selectModel() {
        boolean enabled=dev.sangwoo.celestialfall.client.MapClient.enabled();
        if(enabled==botand$usingMap)return;botand$usingMap=enabled;
        this.model=enabled?botand$animated:botand$vanilla;
        this.features.clear();this.features.addAll(enabled?botand$mapFeatures:botand$normalFeatures);
    }
    @Inject(method={"renderRightArm","renderLeftArm"},at=@At("HEAD"))
    private void botand$selectArm(MatrixStack matrices,VertexConsumerProvider vertices,int light,AbstractClientPlayerEntity player,CallbackInfo ci){botand$selectModel();}
    @Inject(method="render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"),cancellable=true)
    private void celestialfall$atomicActor(AbstractClientPlayerEntity player,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci) {
        botand$selectModel();
        botand$posed=false;
        var atomic=dev.sangwoo.celestialfall.client.AtomicClient.cinematic();
        if(atomic!=null&&!atomic.caster.equals(player.getUuid())&&player.squaredDistanceTo(atomic.origin)<32*32){ci.cancel();return;}
        var highest=dev.sangwoo.celestialfall.client.StandClient.cinematic();
        if(highest!=null&&!highest.caster().equals(player.getUuid())&&highest.includes(player.getUuid())){ci.cancel();return;}
        if(player==net.minecraft.client.MinecraftClient.getInstance().player&&dev.sangwoo.celestialfall.client.BotandCamera.enabled()
            &&!dev.sangwoo.celestialfall.client.CinematicDirector.isActive()&&dev.sangwoo.celestialfall.client.BotandCamera.STATE.collisionDistance<.7){ci.cancel();return;}
        if(player.isAlive() && (AtomicClient.actor(player.getUuid())!=null || dev.sangwoo.celestialfall.client.HeavenClient.actor(player.getUuid())!=null)){ci.cancel();return;}
        matrices.push();botand$posed=true;
        var stand=dev.sangwoo.celestialfall.client.StandClient.action(player.getUuid());
        if(stand!=null&&stand.skill()==dev.sangwoo.celestialfall.stand.KagenoRules.H&&stand.age(delta)>=dev.sangwoo.celestialfall.client.KagenoCameraPath.SKY_CUT&&stand.age(delta)<56){matrices.pop();botand$posed=false;ci.cancel();return;}
        if(stand!=null){
            float age=stand.age(delta);float spin=dev.sangwoo.celestialfall.client.KagenoMotion.spin(stand.skill(),age,stand.combo());
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(spin));
            if(stand.skill()==dev.sangwoo.celestialfall.stand.KagenoRules.Y)matrices.translate(0,dev.sangwoo.celestialfall.client.KagenoMotion.yHop(age),0);
            if(stand.skill()==dev.sangwoo.celestialfall.stand.KagenoRules.E){
                var f=net.minecraft.util.math.Vec3d.fromPolar(0,stand.yaw());double lunge=dev.sangwoo.celestialfall.client.KagenoMotion.thrust(age)*.24;
                matrices.translate(f.x*lunge,0,f.z*lunge);
            }
            if(stand.skill()==dev.sangwoo.celestialfall.stand.KagenoRules.H){
                var direction=net.minecraft.util.math.Vec3d.fromPolar(0,stand.yaw());
                double travel=dev.sangwoo.celestialfall.client.KagenoMotion.hTravel(age);
                matrices.translate(direction.x*travel,dev.sangwoo.celestialfall.client.KagenoMotion.lift(age),direction.z*travel);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(dev.sangwoo.celestialfall.client.KagenoMotion.hTurn(age)));
                float flip=dev.sangwoo.celestialfall.client.KagenoMotion.flip(age);
                matrices.translate(0,.9,0);matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-stand.yaw()));matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(dev.sangwoo.celestialfall.client.KagenoMotion.hBank(age)));matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(flip));matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(stand.yaw()));matrices.translate(0,-.9,0);
            }
        }
    }
    @Inject(method="render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void botand$restoreLift(AbstractClientPlayerEntity player,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){if(botand$posed){matrices.pop();botand$posed=false;}}
}
