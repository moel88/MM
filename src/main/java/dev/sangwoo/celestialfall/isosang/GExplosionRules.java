package dev.sangwoo.celestialfall.isosang;

public final class GExplosionRules {
   public static final int HIT_TICK = 97;
   public static final float START = 4.85F;
   public static final double RADIUS = 20.0;

   private GExplosionRules() {
   }

   public static float damage(double var0) {
      if (!Double.isFinite(var0) || var0 < 0.0) {
         return 0.0F;
      } else if (var0 <= 25.0) {
         return 200.0F;
      } else if (var0 <= 100.0) {
         return 100.0F;
      } else {
         return var0 <= 400.0 ? 50.0F : 0.0F;
      }
   }
}
