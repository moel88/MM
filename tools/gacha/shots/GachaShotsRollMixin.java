package dev.sangwoo.gachashots.mixin;

import dev.sangwoo.celestialfall.gacha.GachaRules;
import dev.sangwoo.celestialfall.gacha.GachaServer;
import dev.sangwoo.gachashots.QaRolls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.function.IntSupplier;

/**
 * Visual QA (2.40.0): the server rolls from {@link QaRolls}, reseeded for every entry, so the slowed film run and the
 * real-time sound run pull the same grades.
 */
@Mixin(value=GachaServer.class,remap=false)
public abstract class GachaShotsRollMixin {
    @Redirect(method="perform",at=@At(value="INVOKE",target="Ldev/sangwoo/celestialfall/gacha/GachaRules;pulls(Ldev/sangwoo/celestialfall/gacha/GachaRules$Count;ILjava/util/function/IntSupplier;)Ldev/sangwoo/celestialfall/gacha/GachaRules$Result;"))
    private static GachaRules.Result shots$seeded(GachaRules.Count count,int n,IntSupplier ignored){return GachaRules.pulls(count,n,()->QaRolls.next(GachaRules.ODDS));}
}
