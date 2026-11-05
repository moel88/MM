package dev.sangwoo.celestialfall.isosang;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public record GArenaBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
   public static final GArenaBounds GARDEN = new GArenaBounds(3.0, 80.0, -71.0, 65.0, 320.0, -24.0);

   public boolean contains(Vec3d var1) {
      return var1.x >= this.minX && var1.x < this.maxX && var1.y >= this.minY && var1.y < this.maxY && var1.z >= this.minZ && var1.z < this.maxZ;
   }

   public Box box() {
      return new Box(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
   }

   public double reach(Vec3d var1) {
      return Math.hypot(
         Math.max(Math.abs(this.minX - var1.x), Math.abs(this.maxX - var1.x)), Math.max(Math.abs(this.minZ - var1.z), Math.abs(this.maxZ - var1.z))
      );
   }

   public float damage(Vec3d var1, Vec3d var2) {
      if (!this.contains(var1)) {
         return 0.0F;
      } else {
         double var3 = var1.x - var2.x;
         double var5 = var1.z - var2.z;
         double var7 = var3 * var3 + var5 * var5;
         return var7 <= 25.0 ? 200.0F : (var7 <= 225.0 ? 140.0F : (var7 <= 900.0 ? 90.0F : 50.0F));
      }
   }
}
