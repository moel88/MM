package dev.sangwoo.gachashots.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Visual QA: slows the integrated server by the same factor as the client tick counter. */
@Mixin(MinecraftServer.class)
public abstract class GachaShotsServerMixin {
    @ModifyConstant(method="runServer",constant=@Constant(longValue=50L))
    private long shots$slowTick(long value){return value*Integer.getInteger("botand.shots.slow",1);}
}
