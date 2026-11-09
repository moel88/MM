package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.isosang.GStage;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lee So-sang's G's own space (2.83.0, GStage): no chests, signs or other block entities while its user's camera is in its cutscene there. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class IsosangStageBlockEntityMixin {
    @Inject(method="render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",at=@At("HEAD"),cancellable=true,require=0)
    private <E extends BlockEntity> void botand$stageBlockEntities(E entity,float tickDelta,MatrixStack matrices,VertexConsumerProvider vertices,CallbackInfo ci){
        if(GStage.hidden()||dev.sangwoo.celestialfall.client.xchara.XCharaClient.spaceHidden())ci.cancel();
    }
}
