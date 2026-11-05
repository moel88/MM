package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import net.minecraft.client.MinecraftClient;

final class GBlackout {
   static float alpha(float var0) {
      StandClient.Cast var1 = FallingSword.cinematic();
      if (var1 == null) {
         return 0.0F;
      } else {
         float var2 = var1.age(var0) / 20.0F;
         return Ease.smooth(var2, 0.04F, 0.12F) * (1.0F - Ease.smooth(var2, 1.075F, 1.16F));
      }
   }

   static boolean hidden() {
      StandClient.Cast var0 = FallingSword.cinematic();
      if (var0 == null) {
         return false;
      } else {
         float var1 = var0.age(MinecraftClient.getInstance().getTickDelta()) / 20.0F;
         return var1 >= 0.12F && var1 < 1.12F;
      }
   }
}
