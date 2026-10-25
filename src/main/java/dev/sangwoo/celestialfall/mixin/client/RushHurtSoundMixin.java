package dev.sangwoo.celestialfall.mixin.client;

import dev.sangwoo.celestialfall.client.stand.StandRush;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A player in Star Platinum's rush (2.57.0) cries out at its first blow only: the rest come with the rush's own sound.
 * The server holds back the cry others hear (CombatManager.damageQuietly); a player's own cry is played by their own
 * client as the damage comes, so it is held back here (StandRush.quietHurt). So too the quiet blows of its beatdown's
 * cutscene (2.64.0, StandBeatdown.quietHurt: the multiplayer check heard the one struck cry out at all 45 of its blows).
 */
@Mixin(LivingEntity.class)
public abstract class RushHurtSoundMixin {
    @Redirect(method="onDamaged",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;playSound(Lnet/minecraft/sound/SoundEvent;FF)V"))
    private void botand$quietRushBlow(LivingEntity self,SoundEvent sound,float volume,float pitch){
        // (the beatdown's first, so that it counts its first blow even if a rush holds that one back)
        if(!dev.sangwoo.celestialfall.client.stand.StandBeatdown.quietHurt(self)&!StandRush.quietHurt(self))self.playSound(sound,volume,pitch);
    }
}
