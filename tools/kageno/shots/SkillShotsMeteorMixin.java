package dev.sangwoo.botandshots.mixin;

import dev.sangwoo.botandshots.SkillShots;
import dev.sangwoo.celestialfall.meteor.MeteorManager;
import dev.sangwoo.celestialfall.meteor.MeteorRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Visual QA (2.49.1): a meteor's shatter rolls follow the entry's "holds=" script, so the slowed film run and the
 * real-time sound run break the same way; past the script (or without one) the real roll.
 */
@Mixin(value=MeteorManager.class,remap=false)
public abstract class SkillShotsMeteorMixin {
    @Redirect(method="mine",at=@At(value="INVOKE",target="Ldev/sangwoo/celestialfall/meteor/MeteorRules;holds(Ldev/sangwoo/celestialfall/meteor/MeteorRules$Rock;I)Z"))
    private static boolean shots$scripted(MeteorRules.Rock after,int roll){
        Boolean scripted=SkillShots.nextHold();
        return scripted==null?MeteorRules.holds(after,roll):scripted&&!after.depleted();
    }
}
