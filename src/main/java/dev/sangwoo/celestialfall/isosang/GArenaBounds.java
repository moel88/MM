package dev.sangwoo.celestialfall.isosang;

import dev.sangwoo.celestialfall.boss.BossThreats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public record GArenaBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
   public static final GArenaBounds GARDEN = new GArenaBounds(3.0, 80.0, -71.0, 65.0, 320.0, -24.0);

   public boolean contains(Vec3d var1) {
      return var1.x >= this.minX && var1.x < this.maxX && var1.y >= this.minY && var1.y < this.maxY && var1.z >= this.minZ && var1.z < this.maxZ;
   }

   /**
    * How far {@code at} stands out of this box (blocks, the straight way to its nearest face, edge or corner; 0 in it):
    * the client hears G's sound as loud as its user does in its blast's ground and fainter beyond it by this (2.92.0).
    */
   public double outside(Vec3d at) {
      double x = Math.max(Math.max(this.minX - at.x, at.x - this.maxX), 0.0);
      double y = Math.max(Math.max(this.minY - at.y, at.y - this.maxY), 0.0);
      double z = Math.max(Math.max(this.minZ - at.z, at.z - this.maxZ), 0.0);
      return Math.sqrt(x * x + y * y + z * z);
   }

   public Box box() {
      return new Box(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
   }

   /**
    * This box as a boss reads it (BossThreats, GArenaServer.threat): from its north-west foot, facing south (yaw 0, ahead
    * +z), its depth ahead and its width to its left (+x: side 0 down to minus its width).
    */
   public BossThreats.Zone zone() {
      return new BossThreats.Zone(
         this.minX, this.minY, this.minZ, 0.0F, 0.0, this.maxZ - this.minZ, this.minX - this.maxX, 0.0, 0.0, this.maxY - this.minY, 0.0
      );
   }

   public double reach(Vec3d var1) {
      return Math.hypot(
         Math.max(Math.abs(this.minX - var1.x), Math.abs(this.maxX - var1.x)), Math.max(Math.abs(this.minZ - var1.z), Math.abs(this.maxZ - var1.z))
      );
   }

   /**
    * What G's last blast takes from one standing at {@code var1} in this ground, where its sword struck at {@code var2}
    * (by how far across the ground, GExplosionRules.damage: 2.87.1, 200 · 100 · 50 · 20 within 5 · 15 · 30 blocks and
    * further on; the user's preview gave 200 · 140 · 90 · 50 in the arena); none out of it.
    */
   public float damage(Vec3d var1, Vec3d var2) {
      if (!this.contains(var1)) {
         return 0.0F;
      } else {
         double var3 = var1.x - var2.x;
         double var5 = var1.z - var2.z;
         return GExplosionRules.damage(var3 * var3 + var5 * var5);
      }
   }
}
