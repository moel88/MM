package dev.sangwoo.celestialfall.mixin.client;
import dev.sangwoo.celestialfall.client.MapControls;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameOptions.class)
public abstract class MapOptionsMixin {
    @Inject(method="write",at=@At("HEAD"))private void botand$saveNormalControls(CallbackInfo ci){MapControls.beginSave();}
    @Inject(method="write",at=@At("RETURN"))private void botand$restoreMapControls(CallbackInfo ci){MapControls.endSave();}
}
