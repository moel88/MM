package dev.sangwoo.celestialfall.client.isosang;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.isosang.GArenaBounds;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

final class GExplosionVisual {
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

   private static double rand(int var0, int var1) {
      return Ease.hash(var0 * 173 + var1);
   }

   private static Vec3d radial(int var0) {
      double var1 = rand(var0, 17) * Math.PI * 2.0;
      return new Vec3d(Math.cos(var1), 0.0, Math.sin(var1));
   }

   private static double travel(int var0, double var1) {
      return (8.0 + 10.0 * rand(var0, 19)) * (1.0 - Math.exp(-1.8 * var1));
   }

   static void solid(WorldRenderContext var0, StandClient.Cast var1, float var2) {
      double var3 = (double)(var2 - 4.85F);
      if (!(var3 < 0.0) && !(var3 > 2.1)) {
         Vec3d var5 = FallingSword.strike(var1);
         Vec3d var6 = var0.camera().getPos();
         double var7 = GArenaClient.radius(var1) / 20.0;
         Vec3d var9 = new Vec3d(-0.4, 0.8, 0.5).normalize();
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

         // (2.87.0) a few dark chunks thrown off, as the videos' (480 in the preview: a swarm of rock over everything)
         for (int var12 = 0; var12 < 120; var12++) {
            double var13 = var3 - (var12 < 90 ? 0.085 : 0.1 + 0.18 * rand(var12, 11));
            if (!(var13 <= 0.0)) {
               boolean var15 = var12 % 19 == 0;
               double var16 = (double)(var15 ? 10 : 6) + 8.0 * rand(var12, 23);
               double var18 = 0.08 + var16 * var13 - 7.8 * var13 * var13;
               if (!(var18 < 0.02)) {
                  Vec3d var20 = radial(var12);
                  double var21 = (0.45 + travel(var12, var13)) * var7;
                  double var23 = var15 ? 0.2 + 0.38 * rand(var12, 29) : 0.025 + 0.1 * rand(var12, 29);
                  if (GArenaClient.contains(var1, var5.add(var20.multiply(var21)))) {
                     Vec3d var25 = var5.add(var20.multiply(var21)).add(0.0, var18, 0.0).subtract(var6);
                     double var26 = (double)var12 + var13 * (5.0 + 11.0 * rand(var12, 31));
                     Vec3d var28 = new Vec3d(Math.cos(var26), 0.4 * Math.sin(var26 * 0.7), Math.sin(var26)).normalize();
                     Vec3d var29 = var28.crossProduct(new Vec3d(0.21, 1.0, 0.13)).normalize();
                     Vec3d var30 = var28.crossProduct(var29).normalize();
                     Vec3d[] var31 = new Vec3d[12];

                     for (int var32 = 0; var32 < 12; var32++) {
                        Vec3d var33 = ROCK[var32].normalize();
                        double var34 = 0.75 + 0.5 * rand(var12 * 19 + var32, 37);
                        var31[var32] = var25.add(var28.multiply(var33.x * var23 * var34))
                           .add(var29.multiply(var33.y * var23 * 0.66 * var34))
                           .add(var30.multiply(var33.z * var23 * 1.35 * var34));
                     }

                     float var50 = (1.0F - Ease.smooth((float)var3, 1.4F, 2.08F)) * Math.min(1.0F, (float)var13 / 0.025F);
                     double var51 = Math.exp(-var13 * 5.2) * (0.25 + 0.3 * rand(var12, 41));

                     for (int[] var38 : FACES) {
                        Vec3d var39 = var31[var38[1]].subtract(var31[var38[0]]).crossProduct(var31[var38[2]].subtract(var31[var38[0]])).normalize();
                        double var40 = 0.42 + 0.58 * Math.max(0.0, var39.dotProduct(var9));
                        float var42 = (float)(0.42 * var40 + var51 * 0.68);
                        float var43 = (float)(0.35 * var40 + var51 * 0.35);
                        float var44 = (float)(0.26 * var40 + var51 * 0.07);

                        for (int var48 : var38) {
                           Vec3d var49 = var31[var48];
                           var11.vertex(var49.x, var49.y, var49.z).color(var42, var43, var44, var50).next();
                        }
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

   static void light(Fx var0, EffectMesh var1, float var2) {
      double var3 = (double)(var2 - 4.85F);
      if (!(var3 < 0.0) && !(var3 > 2.1)) {
         Vec3d var5 = FallingSword.strike(var0.cast);
         GArenaBounds var6 = GArenaClient.arena(var0.cast);
         double var7 = GArenaClient.radius(var0.cast) / 20.0;
         float var9 = (float)(Math.min(1.0, var3 / 0.018) * Math.exp(-var3 * 15.0));
         if (var9 > 0.002F) {
            var1.softDisc(var5.add(0.0, 0.6, 0.0), 8.0, 16765820, 0.62F * var9);
            var1.softDisc(var5.add(0.0, 0.7, 0.0), 3.5, 16777194, 0.9F * var9);
            var1.star(var5.add(0.0, 0.8, 0.0), 7.0, 16775120, 0.65F * var9);
         }

         for (int var10 = 0; var10 < (var6 == null ? 12 : 32); var10++) {
            double var11 = var3 - 0.012 - 0.032 * rand(var10, 47);
            if (!(var11 <= 0.0) && !(var11 > 0.58)) {
               double var13 = (double)var10 * 2.399963 + 0.17 * rand(var10, 51);
               Vec3d var15 = new Vec3d(Math.cos(var13), 0.0, Math.sin(var13));
               boolean var16 = var10 >= 4;
               double var17 = var16 ? 7.0 + 5.5 * rand(var10, 59) : 1.0 + 2.0 * rand(var10, 59);
               double var19 = 1.0 - Math.exp(-35.0 * var11);
               double var21 = (var16 ? 9.0 + 6.0 * rand(var10, 53) : 6.0 + 7.0 * rand(var10, 53)) * var19;
               Vec3d var23 = var5.add(var15.multiply(var17)).add(0.0, 0.12, 0.0);
               if (var6 != null && var16) {
                  var23 = new Vec3d(
                     var6.minX() + 1.5 + (var6.maxX() - var6.minX() - 3.0) * rand(var10, 97),
                     var5.y + 0.12,
                     var6.minZ() + 1.5 + (var6.maxZ() - var6.minZ() - 3.0) * rand(var10, 101)
                  );
               }

               Vec3d var24 = var23.add(var15.multiply(var21 * (0.1 + 0.13 * rand(var10, 61)))).add(0.0, var21, 0.0);
               float var25 = (1.0F - Ease.smooth((float)var11, 0.19F, 0.57F)) * Math.min(1.0F, (float)var11 / 0.025F);
               var1.softTaper(var23, var24, 2.8, 0.018, 16757319, 0.48F * var25);
               var1.softTaper(var23, var24, 0.68, 0.004, 16775120, 0.92F * var25);
            }
         }

         for (int var35 = 0; var35 < 320; var35++) {
            double var36 = 0.015 + 0.19 * rand(var35, 67);
            double var37 = var3 - var36;
            double var38 = 0.55 + 0.85 * rand(var35, 71);
            if (!(var37 <= 0.0) && !(var37 > var38)) {
               Vec3d var39 = radial(var35 + 601);
               double var18 = 4.0 + 13.0 * rand(var35, 73);
               double var20 = 7.0 + 10.0 * rand(var35, 79);
               double var22 = Math.max(0.0, var37 - (0.035 + 0.075 * rand(var35, 83)));
               double var40 = var7 * var20 * 0.75 * (1.0 - Math.exp(-var37 / 0.75));
               double var26 = var7 * var20 * 0.75 * (1.0 - Math.exp(-var22 / 0.75));
               double var28 = var18 * var37 - 6.4 * var37 * var37;
               double var30 = var18 * var22 - 6.4 * var22 * var22;
               if (!(var28 < 0.0)) {
                  Vec3d var32 = var5.add(var39.multiply(0.4 + var40)).add(0.0, 0.12 + var28, 0.0);
                  Vec3d var33 = var5.add(var39.multiply(0.4 + var26)).add(0.0, 0.12 + Math.max(0.0, var30), 0.0);
                  if (GArenaClient.contains(var0.cast, var32)) {
                     float var34 = (float)(Math.min(1.0, var37 / 0.025) * Math.pow(1.0 - var37 / var38, 0.8));
                     var1.softTaper(var33, var32, 0.014, 0.11, 16764023, 0.55F * var34);
                     var1.line(var33, var32, 0.012, 16774603, 0.83F * var34);
                  }
               }
            }
         }
      }
   }
}
