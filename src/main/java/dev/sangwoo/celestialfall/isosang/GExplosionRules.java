package dev.sangwoo.celestialfall.isosang;

import net.minecraft.util.math.Vec3d;

/**
 * Lee So-sang's G's last blast, as its sword has struck (the user's 2.87.0 preview: at the 97th tick, as the cutscene
 * ends). 2.87.1 (user request: "마지막 검 박히고 폭발 반경을 62 × 47로 넓혀주고 데미지는 5블록 이내 200, 5~15블록 100,
 * 15~30블록 50, 나머지 20"): it covers 62 × 47 blocks, the garden arena's own size (east-west by north-south, the way the
 * arena lies), its middle where the sword struck ({@link #area}; in the arena, the arena itself, GArenaServer), and
 * everyone in it is struck by how far they stand from where the sword struck, across the ground ({@link #damage}).
 */
public final class GExplosionRules {
   public static final int HIT_TICK = 97;
   public static final float START = 4.85F;
   /** The blast's ground (blocks): east-west, north-south, as GArenaBounds.GARDEN's (2.87.1). */
   public static final double WIDTH = 62.0, DEPTH = 47.0;
   /** How far under and over where the sword struck the blast reaches out of the arena (as the 20-block blast before 2.87.1). */
   public static final double BELOW = 20.0, ABOVE = 20.0;

   private GExplosionRules() {
   }

   /**
    * What the blast takes from one {@code squared} blocks from where the sword struck, across the ground (2.87.1): 200
    * within 5 blocks, 100 within 15, 50 within 30, 20 further on (in its ground, {@link GArenaBounds#damage}).
    */
   public static float damage(double squared) {
      if (!Double.isFinite(squared) || squared < 0.0) {
         return 0.0F;
      } else if (squared <= 25.0) {
         return 200.0F;
      } else if (squared <= 225.0) {
         return 100.0F;
      } else {
         return squared <= 900.0 ? 50.0F : 20.0F;
      }
   }

   /** The blast's ground out of the arena (2.87.1): 62 × 47 blocks, its middle where the sword struck, lying as the arena lies. */
   public static GArenaBounds area(Vec3d strike) {
      return new GArenaBounds(
         strike.x - WIDTH / 2.0, strike.y - BELOW, strike.z - DEPTH / 2.0, strike.x + WIDTH / 2.0, strike.y + ABOVE, strike.z + DEPTH / 2.0
      );
   }
}
