package dev.sangwoo.celestialfall.client.isosang;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

final class GImpact {
   private static final double PHI = (1.0 + Math.sqrt(5.0)) / 2.0;
   private static final Vec3d[] ROCK = new Vec3d[]{
      new Vec3d(-1.0, PHI, 0.0),
      new Vec3d(1.0, PHI, 0.0),
      new Vec3d(-1.0, -PHI, 0.0),
      new Vec3d(1.0, -PHI, 0.0),
      new Vec3d(0.0, -1.0, PHI),
      new Vec3d(0.0, 1.0, PHI),
      new Vec3d(0.0, -1.0, -PHI),
      new Vec3d(0.0, 1.0, -PHI),
      new Vec3d(PHI, 0.0, -1.0),
      new Vec3d(PHI, 0.0, 1.0),
      new Vec3d(-PHI, 0.0, -1.0),
      new Vec3d(-PHI, 0.0, 1.0)
   };
   private static final int[][] FACES = new int[][]{
      {0, 11, 5},
      {0, 5, 1},
      {0, 1, 7},
      {0, 7, 10},
      {0, 10, 11},
      {1, 5, 9},
      {5, 11, 4},
      {11, 10, 2},
      {10, 7, 6},
      {7, 1, 8},
      {3, 9, 4},
      {3, 4, 2},
      {3, 2, 6},
      {3, 6, 8},
      {3, 8, 9},
      {4, 9, 5},
      {2, 4, 11},
      {6, 2, 10},
      {8, 6, 7},
      {9, 8, 1}
   };

   private GImpact() {
   }

   private static double random(int var0, int var1) {
      return Ease.hash(var0 * 73 + var1);
   }

   static void solid(WorldRenderContext var0, StandClient.Cast var1, float var2) {
      if (var2 >= 4.85F) {
         GExplosionVisual.solid(var0, var1, var2);
      } else {
         double var3 = (double)(var2 - 3.1F);
         if (!(var3 < 0.025) && !(var2 >= 4.78F)) {
            Vec3d var5 = FallingSword.strike(var1);
            Vec3d var6 = var0.camera().getPos();
            Vec3d var7 = FallingSword.forward(var1).negate();
            Vec3d var8 = FallingSword.right(FallingSword.forward(var1));
            Vec3d var9 = var7.multiply(0.5).add(var8.multiply(-0.4)).add(0.0, 0.8, 0.0).normalize();
            MatrixStack var10 = RenderSystem.getModelViewStack();
            var10.push();
            var10.multiplyPositionMatrix(var0.matrixStack().peek().getPositionMatrix());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            BufferBuilder var11 = Tessellator.getInstance().getBuffer();
            var11.begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            boolean var12 = GWorld.observer(var1);

            // (2.87.0) fewer, the videos' few dark chunks (640 in the preview: a hail of black stone over the strike)
            for (int var13 = 0; var13 < 170; var13++) {
               double var14 = var13 < 130 ? 0.025 + 2.3 * random(var13, 11) : 2.0500002F + 0.22 * random(var13, 11);
               boolean var16 = var12 && var13 % 7 == 0;
               if (var16 && var13 % 3 != 0) {
                  var14 = 0.025 + 0.23 * random(var13, 93);
               }

               boolean var17 = !var12 && var13 % 8 == 0;
               if (!var12 && var13 < 144) {
                  var14 = 0.025 + 0.17 * random(var13, 93);
               }

               double var18 = var3 - var14;
               if (!(var18 < 0.0) && !(var18 > (var16 ? 2.05 : 1.65))) {
                  double var20 = (Math.PI * 2) * random(var13, 19);
                  double var22 = 1.7 + 4.1 * random(var13, 23);
                  double var24 = 3.5 + 6.4 * random(var13, 29);
                  if (!var12) {
                     var22 *= 1.35;
                     var24 *= var17 ? 1.2 : 1.06;
                  }

                  Vec3d var26 = var8.multiply(Math.cos(var20)).add(var7.multiply(Math.sin(var20)));
                  Vec3d var27 = var5.add(var26.multiply(0.35 + 0.95 * random(var13, 31) + 0.45 * var14)).add(var7.multiply(0.35));
                  double var28 = var22 * 0.35 * (1.0 - Math.exp(-var18 / 0.35));
                  if (var16) {
                     var28 = (9.0 + 15.0 * random(var13, 23)) * (1.0 - Math.exp(-1.55 * var18));
                     var24 += 5.0 + 3.9 * random(var13, 29);
                  }

                  Vec3d var30 = var27.add(var26.multiply(var28)).add(0.0, 0.06 + var24 * var18 - 5.8 * var18 * var18, 0.0);
                  if (!(var30.y < var5.y - 0.2)) {
                     double var31 = (0.025 + 0.075 * random(var13, 37)) * (var13 % 13 == 0 ? 3.0 : 1.0);
                     if (var16) {
                        var31 = 0.28 + 0.58 * random(var13, 37);
                     }

                     if (var17) {
                        var31 = 0.11 + 0.15 * random(var13, 37);
                     }

                     double var33 = (double)var13 + var18 * (7.0 + 9.0 * random(var13, 41));
                     Vec3d var35 = new Vec3d(Math.cos(var33), 0.35 * Math.sin(var33 * 0.7), Math.sin(var33)).normalize();
                     Vec3d var36 = var35.crossProduct(new Vec3d(0.23, 1.0, 0.15)).normalize();
                     Vec3d var37 = var35.crossProduct(var36).normalize();
                     double var38 = (var13 % 9 == 0 ? 2.0 : 1.1) * var31;
                     Vec3d var40 = var30.subtract(var6);
                     Vec3d[] var41 = new Vec3d[ROCK.length];

                     for (int var42 = 0; var42 < ROCK.length; var42++) {
                        Vec3d var43 = ROCK[var42].normalize();
                        double var44 = 0.8 + 0.4 * random(var13 * 19 + var42, 83);
                        var41[var42] = var40.add(var35.multiply(var43.x * var31 * var44))
                           .add(var36.multiply(var43.y * var31 * 0.72 * var44))
                           .add(var37.multiply(var43.z * var38 * var44));
                     }

                     float var60 = (1.0F - Ease.smooth((float)var18, 1.05F, 1.45F)) * (1.0F - Ease.smooth(var2, 6.45F, 7.0F));
                     if (var16) {
                        var60 = (1.0F - Ease.smooth((float)var18, 1.55F, 2.05F)) * (1.0F - Ease.smooth(var2, 6.15F, 6.65F));
                     }

                     var60 *= 1.0F - Ease.smooth(var2, 4.6F, 4.78F);
                     double var62 = Math.exp(-var18 * 6.0) * (0.2 + 0.3 * random(var13, 43));

                     for (int[] var48 : FACES) {
                        Vec3d var49 = var41[var48[1]].subtract(var41[var48[0]]).crossProduct(var41[var48[2]].subtract(var41[var48[0]])).normalize();
                        double var50 = 0.45 + 0.55 * Math.max(0.0, var49.dotProduct(var9));
                        float var52 = (float)(0.14 * var50 + var62 * 0.4);
                        float var53 = (float)(0.09 * var50 + var62 * 0.16);
                        float var54 = (float)(0.05 * var50 + var62 * 0.025);
                        if (var16) {
                           var52 += 0.41F * (float)var50;
                           var53 += 0.39F * (float)var50;
                           var54 += 0.34F * (float)var50;
                        }

                        if (!var12) {
                           var52 += 0.18F * (float)var50;
                           var53 += 0.2F * (float)var50;
                           var54 += 0.23F * (float)var50;
                           if (var17) {
                              var52 += (float)(var62 * 0.4);
                              var53 += (float)(var62 * 0.22);
                           }
                        }

                        for (int var58 : var48) {
                           Vec3d var59 = var41[var58];
                           var11.vertex((double)((float)var59.x), (double)((float)var59.y), (double)((float)var59.z)).color(var52, var53, var54, var60).next();
                        }
                     }
                  }
               }
            }

            BufferRenderer.drawWithGlobalProgram(var11.end());
            var10.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
         }
      }
   }

   static void sparks(Fx var0, EffectMesh var1, float var2) {
      if (var2 >= 4.85F) {
         GExplosionVisual.light(var0, var1, var2);
      } else {
         double var3 = (double)(var2 - 3.1F);
         if (!(var3 < 0.015) && !(var2 >= 4.78F)) {
            Vec3d var5 = FallingSword.strike(var0.cast);
            Vec3d var6 = FallingSword.forward(var0.cast).negate();
            Vec3d var7 = FallingSword.right(FallingSword.forward(var0.cast));
            boolean var8 = GWorld.observer(var0.cast);

            for (int var9 = 0; var9 < 780; var9++) {
               double var10 = var9 < 600 ? 0.015 + 2.35 * random(var9, 47) : 2.0500002F + 0.2 * random(var9, 47);
               boolean var12 = var8 && var9 % 9 == 0;
               if (var12) {
                  var10 = 0.035 + 0.42 * random(var9, 47);
               }

               boolean var13 = !var8 && var9 % 5 == 0;
               if (!var8 && var9 < 180) {
                  var10 = 0.018 + 0.15 * random(var9, 47);
               }

               double var14 = var3 - var10;
               double var16 = 0.24 + 0.55 * random(var9, 53);
               if (var12) {
                  var16 = 0.75 + 0.55 * random(var9, 53);
               }

               if (var13) {
                  var16 = 0.48 + 0.4 * random(var9, 53);
               }

               if (!(var14 < 0.0) && !(var14 > var16)) {
                  double var18 = random(var9, 59) * Math.PI * 2.0;
                  double var20 = 2.0 + 6.0 * random(var9, 61);
                  double var22 = 3.0 + 10.0 * random(var9, 67);
                  if (var12) {
                     var20 = 11.0 + 17.0 * random(var9, 61);
                     var22 = 9.0 + 11.0 * random(var9, 67);
                  }

                  if (!var8) {
                     var20 *= var13 ? 1.5 : 1.2;
                     var22 *= var13 ? 1.35 : 1.08;
                  }

                  Vec3d var24 = var7.multiply(Math.cos(var18)).add(var6.multiply(Math.sin(var18)));
                  Vec3d var25 = var5.add(var24.multiply(0.45 + 0.7 * random(var9, 71) + 0.3 * var10)).add(var6.multiply(0.45)).add(0.0, 0.12, 0.0);
                  if (!var8) {
                     double var26 = FallingSword.half(Math.max(0.0, -GScene.height((float)(3.1 + var10))) / 0.86);
                     var25 = var5.add(var7.multiply((double)(var9 % 2 == 0 ? 1 : -1) * var26))
                        .add(var24.multiply(0.18 + 0.45 * random(var9, 71)))
                        .add(var6.multiply(0.55))
                        .add(0.0, 0.14, 0.0);
                  }

                  double var31 = Math.max(0.0, var14 - (0.04 + 0.1 * random(var9, 79)));
                  Vec3d var28 = var25.add(var24.multiply(var20 * var14)).add(0.0, var22 * var14 - 5.0 * var14 * var14, 0.0);
                  Vec3d var29 = var25.add(var24.multiply(var20 * var31)).add(0.0, var22 * var31 - 5.0 * var31 * var31, 0.0);
                  float var30 = (float)(Math.min(1.0, var14 / 0.035) * (1.0 - var14 / var16));
                  var30 *= 1.0F - Ease.smooth(var2, 4.6F, 4.78F);
                  if (var12) {
                     var1.softTaper(var29, var28, 0.04, 0.27, 16763248, 0.34F * var30);
                     var1.line(var29, var28, 0.035, 16773302, 0.72F * var30);
                  }

                  if (!var8) {
                     var1.softTaper(var29, var28, 0.025, var13 ? 0.21 : 0.11, 16758620, (var13 ? 0.38F : 0.25F) * var30);
                     var1.softTaper(var29, var28, 0.006, var13 ? 0.036 : 0.018, 16775902, 0.78F * var30);
                  }

                  var1.softTaper(var29, var28, 0.006, 0.055, 16757844, 0.45F * var30);
                  var1.line(var29, var28, 0.008, 16773073, 0.65F * var30);
               }
            }
         }
      }
   }
}
