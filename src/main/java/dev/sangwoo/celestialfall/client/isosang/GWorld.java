package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

final class GWorld {
   static final float GATHER = 0.35F;
   static final float FALL = 1.51F;
   static final float CLOUD_CLEAR = 4.8F;
   static final float FADE = 4.6F;
   static final float END = 4.78F;
   static final double FALL_FROM = 40.0;
   static final double CLOUD_HEIGHT = 40.0;
   static final float CONTACT = 3.1F;

   private GWorld() {
   }

   static boolean active(StandClient.Cast var0, float var1) {
      return GScene.active(var0, var1);
   }

   static Vec3d axis(StandClient.Cast var0) {
      return GScene.axis(var0);
   }

   static Vec3d cloudCenter(StandClient.Cast var0) {
      return GScene.cloudCenter(var0);
   }

   static double height(float var0) {
      return GScene.height(var0);
   }

   static boolean observer(StandClient.Cast var0) {
      if (GArenaClient.viewing(var0)) {
         return false;
      } else {
         ClientPlayerEntity var1 = MinecraftClient.getInstance().player;
         return var1 != null && (!var1.getUuid().equals(var0.caster()) || !CelestialFall.CONFIG.cinematicCamera);
      }
   }

   static float distantWeight(StandClient.Cast var0, Vec3d var1) {
      return observer(var0) ? Ease.smooth((float)var1.distanceTo(FallingSword.strike(var0)), 18.0F, 39.0F) : 0.0F;
   }

   static FallingSword.Blade blade(StandClient.Cast var0, float var1) {
      return GScene.blade(var0, var1);
   }

   static void wind(Fx var0, EffectMesh var1, float var2, FallingSword.Blade var3) {
      if (observer(var0.cast)) {
         observerWake(var1, var2, var3);
      } else {
         float var5 = var3.alpha() * Ease.smooth(var2, 1.51F, 1.59F) * (1.0F - Ease.smooth(var2, 3.1F, 3.5F));
         if (!(var5 < 0.005F)) {
            boolean var6 = var2 < 2.7833333F;

            for (int var4 = 0; var4 < 8; var4++) {
               Vec3d[] var8 = new Vec3d[97];
               Vec3d[] var9 = new Vec3d[97];
               double[] var10 = new double[97];
               float[] var11 = new float[97];
               double var12 = var6 ? 20.0 : 24.0;

               for (int var7 = 0; var7 <= 96; var7++) {
                  double var14 = (double)var7 / 96.0;
                  double var16 = 0.35 + var14 * var12;
                  double var18 = (double)var4 * Math.PI * 2.0 / 8.0 + var14 * 4.0 - (double)var2 * 2.7 + 0.23 * Math.sin((double)var4 * 3.8);
                  double var20 = 1.0 + 0.7 * Math.sin(var14 * Math.PI) + 0.22 * Math.sin((double)var4 * 2.4 + var14 * 7.0);
                  var8[var7] = casterAirPoint(var3, var16, var18, var20, var6);
                  Vec3d var22 = var3.across().multiply(Math.cos(var18)).add(var3.normal().multiply(Math.sin(var18)));
                  Vec3d var23 = casterAirPoint(var3, var16 + 0.02, var18 + 0.08 / var12, var20, var6).subtract(var8[var7]);
                  var9[var7] = var22.crossProduct(var23).normalize();
                  var10[var7] = 0.45 + 1.35 * Math.sin(var14 * Math.PI);
                  var11[var7] = (float)Math.pow(Math.sin(var14 * Math.PI), 0.8) * var5 * var3.shown(var16) * 0.28F;
               }

               for (int var27 = 0; var27 < 96; var27++) {
                  for (int var40 = 0; var40 < 8; var40++) {
                     double var15 = (double)var40 / 4.0 - 1.0;
                     double var17 = (double)(var40 + 1) / 4.0 - 1.0;
                     float var19 = (float)Math.pow(1.0 - Math.abs(var15), 1.7);
                     float var46 = (float)Math.pow(1.0 - Math.abs(var17), 1.7);
                     var1.vertex(var8[var27].add(var9[var27].multiply(var15 * var10[var27])), 15135743, var11[var27] * var19);
                     var1.vertex(var8[var27 + 1].add(var9[var27 + 1].multiply(var15 * var10[var27 + 1])), 15135743, var11[var27 + 1] * var19);
                     var1.vertex(var8[var27 + 1].add(var9[var27 + 1].multiply(var17 * var10[var27 + 1])), 15135743, var11[var27 + 1] * var46);
                     var1.vertex(var8[var27].add(var9[var27].multiply(var17 * var10[var27])), 15135743, var11[var27] * var46);
                  }
               }
            }

            for (int var24 = 0; var24 < 16; var24++) {
               double var28 = ((double)var2 * 1.85 + (double)var24 * 0.61803398875) % 1.0;
               double var31 = 1.0 + var28 * 24.0;
               double var34 = (double)var24 * 2.399 - (double)var2 * 0.45;
               float var13 = (float)(Math.pow(Math.sin(Math.PI * var28), 1.5) * (double)var5) * var3.shown(var31);
               Vec3d var41 = casterAirPoint(var3, var31, var34, 1.25 + 0.7 * Math.sin((double)var24 * 1.8), var6);
               Vec3d var43 = casterAirPoint(var3, var31 + 2.0, var34 + 0.09, 1.5 + 0.7 * Math.sin((double)var24 * 1.8), var6);
               var1.softTaper(var41, var43, 0.035, 0.55, 14544895, 0.38F * var13);
               var1.softTaper(var41, var43, 0.012, 0.075, 16776170, 0.36F * var13);
            }

            for (int var25 = 0; var25 < 2; var25++) {
               double var29 = ((double)var2 * 1.2 + (double)var25 * 0.5) % 1.0;
               double var32 = 1.2 + var29 * 20.0;
               float var35 = (float)(Math.sin(Math.PI * var29) * (double)var5) * var3.shown(var32);
               Vec3d var37 = null;

               for (int var39 = 0; var39 <= 44; var39++) {
                  double var42 = (double)var39 / 44.0;
                  double var44 = (double)var25 * Math.PI + 0.2 + (double)var2 * 0.18 + var42 * 3.4;
                  Vec3d var45 = casterAirPoint(var3, var32 + 0.35 * Math.sin(var42 * Math.PI), var44, 2.0, var6);
                  if (var37 != null) {
                     var1.softLine(var37, var45, 0.52, 15267583, 0.29F * var35 * (float)Math.sin(var42 * Math.PI));
                  }

                  var37 = var45;
               }
            }

            for (int var26 = 0; var26 < 7; var26++) {
               double var30 = (double)var26 * 2.399;
               double var33 = 6.0 + (double)var26 * 0.9;
               Vec3d var36 = GScene.cloudCenter(var0.cast)
                  .add(var3.across().multiply(Math.cos(var30) * var33))
                  .add(var3.normal().multiply(Math.sin(var30) * var33));
               Vec3d var38 = var36.subtract(GScene.sun(var0.cast).multiply(24.0 + (double)var26 * 2.0));
               var1.softTaper(var38, var36, 3.8, 0.65, 16773846, 0.062F * var5);
               var1.softTaper(var38, var36, 0.75, 0.13, 16775646, 0.025F * var5);
            }
         }
      }
   }

   private static Vec3d casterAirPoint(FallingSword.Blade var0, double var1, double var3, double var5, boolean var7) {
      double var10 = FallingSword.half(var1);
      double var12 = 0.7;
      double var8;
      if (var7) {
         double var14 = Math.max(0.0, Math.min(1.0, (var1 - 3.5) / 13.0));
         double var16 = var14 * var14 * (3.0 - 2.0 * var14);
         var8 = var1 + 0.93 * (13.0 * (var14 * var14 * var14 - 0.5 * var14 * var14 * var14 * var14) + Math.max(var1 - 16.5, 0.0));
         var10 *= 1.0 + Math.max(0.0, Math.min(2.52, (var8 - 5.534) * 0.43 / 3.8));
         var12 = 0.7 + 2.9 * var16;
      } else {
         double var18 = Math.max(0.0, Math.min(1.0, var1 - 5.534));
         var8 = 0.86 * var1 + 0.07 * var18 * var18 + 0.14 * Math.max(var1 - 6.534, 0.0);
      }

      return var0.at(Math.cos(var3) * (var10 + var5), var8, Math.sin(var3) * (var12 + var5 * 0.8));
   }

   static Vec3d bladeSurface(FallingSword.Blade var0, double var1, double var3, double var5) {
      double var7 = Math.max(0.0, Math.min(1.0, (var3 - 3.5) / 13.0));
      double var9 = var7 * var7 * (3.0 - 2.0 * var7);
      double var11 = 13.0 * (var7 * var7 * var7 - 0.5 * var7 * var7 * var7 * var7) + Math.max(0.0, var3 - 16.5);
      double var13 = 0.7 * (1.0 - Math.exp(-Math.max(var3, 0.0) / 1.4)) * Math.exp(-Math.max(var3, 0.0) / 6.0);
      double var15 = var3 + 0.93 * var11 - var13;
      double var17 = Math.max(0.0, Math.min(1.0, var3 / 6.0));
      double var19 = 0.5 * (var9 + var17 * var17 * (3.0 - 2.0 * var17));
      double var21 = 1.45 * Math.max(0.0, 1.0 - var1 * var1 / 14.44) * Math.signum(var5) * var19;
      double var23 = 0.5 * (1.0 + Math.max(0.0, Math.min(2.52, (var15 - 5.534) * 0.43 / 3.8)) + 2.8 + 0.72 * var9);
      return var0.at(var1 * var23, var15, var5 * (1.0 + 2.65 * var19) + var21);
   }

   private static void observerWake(EffectMesh var0, float var1, FallingSword.Blade var2) {
      float var3 = var2.alpha();
      if (!(var3 < 0.005F)) {
         float var4 = Ease.smooth(var1, 1.5F, 2.0F) * (1.0F - Ease.smooth(var1, 3.1F, 3.82F));

         for (int var8 : new int[]{-1, 1}) {
            Vec3d var9 = null;

            for (int var10 = 0; var10 <= 80; var10++) {
               double var11 = (double)var10 * 0.48;
               Vec3d var13 = bladeSurface(var2, (double)var8 * FallingSword.half(var11), var11, 0.075);
               double var14 = var13.subtract(var2.point()).dotProduct(var2.axis());
               float var16 = var2.shown(var14) * 0.74F;
               if (var9 != null) {
                  var0.softLine(var9, var13, 2.3, 16765323, 0.14F * var16);
                  var0.softLine(var9, var13, 0.72, 16770478, 0.55F * var16);
                  var0.line(var9, var13, 0.095, 16776159, 0.8F * var16);
               }

               var9 = var13;
            }
         }

         if (!(var4 < 0.005F)) {
            double var27 = Math.min(41.0, Math.max(8.0, 51.0 - GScene.height(var1)));

            for (int var28 = 0; var28 < 6; var28++) {
               Vec3d var31 = null;

               for (int var34 = 0; var34 <= 64; var34++) {
                  double var35 = (double)var34 / 64.0;
                  double var12 = 1.0 + var35 * (var27 - 1.0);
                  double var40 = (double)var28 * 2.399 + var35 * (2.6 + 0.18 * (double)var28) - (double)var1 * 4.2;
                  double var43 = bladeSurface(var2, FallingSword.half(var12), var12, 0.0).subtract(var2.point()).dotProduct(var2.across());
                  double var18 = var43 + 1.7 + var35 * 5.8;
                  Vec3d var20 = var2.at(Math.cos(var40) * var18, var12, Math.sin(var40) * (3.8 + 0.25 * var12));
                  float var21 = (float)(Math.pow(Math.sin(Math.PI * var35), 0.85) * (double)var4 * (double)var3 * (double)var2.shown(var12));
                  if (var31 != null) {
                     var0.softLine(var31, var20, 4.2 + 2.2 * Math.sin(Math.PI * var35), 13363967, 0.32F * var21);
                     var0.softLine(var31, var20, 1.05, 15333119, 0.68F * var21);
                     var0.softLine(var31, var20, 0.22, 16775656, 0.82F * var21);
                  }

                  var31 = var20;
               }
            }

            for (int var29 = 0; var29 < 3; var29++) {
               double var32 = ((double)var1 * 1.35 + (double)var29 / 3.0) % 1.0;
               double var36 = 3.0 + var32 * (var27 - 3.0);
               double var38 = bladeSurface(var2, FallingSword.half(var36), var36, 0.0).subtract(var2.point()).dotProduct(var2.across());
               double var41 = var38 + 2.8 + var32 * 5.0;
               double var44 = 4.6 + var32 * 7.0;
               float var46 = (float)(Math.sin(Math.PI * var32) * (double)var4 * (double)var3);
               Vec3d var19 = null;

               for (int var48 = 0; var48 <= 56; var48++) {
                  double var50 = (double)var48 / 56.0;
                  double var23 = (double)var29 * 2.15 - (double)var1 * 0.8 + var50 * 4.2;
                  Vec3d var25 = var2.at(Math.cos(var23) * var41, var36 + 1.9 * Math.sin(var23), Math.sin(var23) * var44);
                  float var26 = (float)Math.pow(Math.sin(Math.PI * var50), 0.7) * var46;
                  if (var19 != null) {
                     var0.softLine(var19, var25, 3.2, 14610175, 0.27F * var26);
                     var0.softLine(var19, var25, 0.48, 16055039, 0.65F * var26);
                  }

                  var19 = var25;
               }
            }

            for (int var30 = 0; var30 < 24; var30++) {
               double var33 = ((double)var1 * (1.15 + (double)(var30 % 4) * 0.18) + (double)var30 * 0.61803398875) % 1.0;
               double var37 = 2.0 + var33 * var27;
               double var39 = (double)var30 * 2.399 + (double)var1 * 0.25;
               double var42 = bladeSurface(var2, FallingSword.half(var37), var37, 0.0).subtract(var2.point()).dotProduct(var2.across());
               double var45 = var42 + 3.2 + (double)(var30 % 5) * 1.1;
               double var47 = 6.5 + (double)(var30 % 4) * 1.2;
               Vec3d var49 = var2.at(Math.cos(var39) * var45, var37, Math.sin(var39) * var47);
               Vec3d var51 = var2.at(Math.cos(var39 + 0.08) * (var45 + 1.3), var37 + 3.5 + (double)(var30 % 4), Math.sin(var39 + 0.08) * (var47 + 1.1));
               float var22 = (float)(Math.pow(Math.sin(Math.PI * var33), 1.6) * (double)var4 * (double)var3);
               var0.softTaper(var49, var51, 0.9, 1.8, 14281983, 0.36F * var22);
               var0.softTaper(var49, var51, 0.06, 0.18, 16580095, 0.72F * var22);
            }
         }
      }
   }
}
