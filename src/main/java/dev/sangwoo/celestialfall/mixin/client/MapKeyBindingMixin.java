package dev.sangwoo.celestialfall.mixin.client;
import java.util.Map;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.sangwoo.celestialfall.client.MapControls;

@Mixin(KeyBinding.class)
public abstract class MapKeyBindingMixin {
    @Shadow @Final private static Map<String,KeyBinding> KEYS_BY_ID;
    @Shadow @Final private static Map<InputUtil.Key,KeyBinding> KEY_TO_BINDINGS;
    @Inject(method="updateKeysByCode",at=@At("TAIL"))
    private static void botand$routeKeys(CallbackInfo ci){MapControls.routeKeys(KEY_TO_BINDINGS,KEYS_BY_ID.values());}
}
