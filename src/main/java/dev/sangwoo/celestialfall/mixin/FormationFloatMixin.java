package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.isosang.IsosangAttack;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lee So-sang standing on H's middle sword (2.82.0) is up over the ground with nothing under her for some 39 s: the server
 * must not take her for flying and kick her ("multiplayer.disconnect.flying" after 80 ticks of floating).
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class FormationFloatMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow private boolean floating;
    @Shadow private int floatingTicks;
    @Inject(method="tick",at=@At("HEAD"))
    private void botand$standOnSword(CallbackInfo ci){
        if(IsosangAttack.raised(player)){floating=false;floatingTicks=0;}
    }
}
