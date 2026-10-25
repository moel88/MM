package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.CombatClient;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Receive the remappable key even when another binding owns KeyBinding's key map. */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method="onKey",at=@At("HEAD"),cancellable=true)
    private void botand$dash(long window,int key,int scanCode,int action,int modifiers,CallbackInfo ci) {
        if(dev.sangwoo.celestialfall.client.loot.FieldLootClient.key(window,key,action)){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.commerce.CommerceClient.key(window,key,action)){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.stand.StandPresence.key(window,key,action,modifiers)){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.stand.StandRush.key(window,key,action,modifiers)){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.stand.StandAttack.heavyKey(window,key,action,modifiers)){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.stand.StandAttack.fingerKey(window,key,action,modifiers)){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.stand.StandAttack.beatdownKey(window,key,action,modifiers)){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.stand.StandInhale.key(window,key,action,modifiers)){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.stand.StandTimeStop.key(window,key,action,modifiers)){ci.cancel();return;}
        if(dev.sangwoo.celestialfall.client.StandClient.key(window,key,action,modifiers)){ci.cancel();return;}
        CombatClient.dashKey(window,key,scanCode,action);
    }
}
