package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gl.VertexBuffer.Usage;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

final class GBladeMesh {
   private static float[] mesh;
   private static final VertexBuffer[] observerFaces = new VertexBuffer[2];
   private static final int STRIDE = 8;

   private GBladeMesh() {
   }

   static void invalidate() {
      mesh = null;

      for (int var0 = 0; var0 < observerFaces.length; var0++) {
         if (observerFaces[var0] != null) {
            observerFaces[var0].close();
         }

         observerFaces[var0] = null;
      }
   }

   static void drawObserver(FallingSword.Blade var0, Vec3d var1, Matrix4f var2, Matrix4f var3, ShaderProgram var4) {
      if (observerFaces[0] == null || observerFaces[1] == null) {
         float[] var5 = vertices();

         for (int var6 = 0; var6 < 2; var6++) {
            int var7 = var6 == 0 ? -1 : 1;
            BufferBuilder var8 = Tessellator.getInstance().getBuffer();
            var8.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);

            for (int var9 = 0; var9 < var5.length; var9 += 8) {
               var8.vertex((double)var5[var9], (double)var5[var9 + 1], (double)((float)var7 * var5[var9 + 2]))
                  .texture(var7 > 0 ? var5[var9 + 3] : 1.0F - var5[var9 + 3], var5[var9 + 4])
                  .color(var7 > 0 ? 1.0F : 0.0F, 0.0F, 0.0F, 1.0F)
                  .normal(var5[var9 + 5], var5[var9 + 6], (float)var7 * var5[var9 + 7])
                  .next();
            }

            VertexBuffer var17 = new VertexBuffer(Usage.STATIC);
            var17.bind();
            var17.upload(var8.end());
            observerFaces[var6] = var17;
         }

         VertexBuffer.unbind();
      }

      int var13 = var0.normal().dotProduct(var1.subtract(var0.point())) >= 0.0 ? 1 : 0;
      var4.getUniform("ObjectSpace").set(1);

      try {
         for (int var18 : new int[]{1 - var13, var13}) {
            observerFaces[var18].bind();
            observerFaces[var18].draw(var2, var3, var4);
         }
      } finally {
         VertexBuffer.unbind();
         var4.getUniform("ObjectSpace").set(0);
      }
   }

   private static float[] vertices() {
      if (mesh != null) {
         return mesh;
      } else {
         Identifier var0 = CelestialFall.id("textures/effect/isosang_g_blade.mesh");

         try {
            float[] var5;
            try (InputStream var1 = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(var0).getInputStream()) {
               ByteBuffer var2 = ByteBuffer.wrap(var1.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
               int var3 = var2.getInt();
               if (var3 < 4 || var3 % 4 != 0 || var3 > 1000000 || var2.remaining() != var3 * 8 * 4) {
                  throw new IOException("Invalid G sword mesh");
               }

               float[] var4 = new float[var3 * 8];
               var2.asFloatBuffer().get(var4);
               mesh = var4;
               var5 = var4;
            }

            return var5;
         } catch (IOException var8) {
            throw new IllegalStateException("Cannot read the G sword mesh", var8);
         }
      }
   }

   static void draw(BufferBuilder var0, FallingSword.Blade var1, Vec3d var2) {
      draw(var0, var1, var2, false);
   }

   static void draw(BufferBuilder var0, FallingSword.Blade var1, Vec3d var2, boolean var3) {
      float[] var4 = vertices();
      Vec3d var5 = var1.point().subtract(var2);
      Vec3d var6 = var1.across();
      Vec3d var7 = var1.axis();
      Vec3d var8 = var1.normal();
      Vec3d var9 = var1.acrossNormal();
      Vec3d var10 = var1.axisNormal();
      int var11 = var8.dotProduct(var2.subtract(var1.point())) >= 0.0 ? 1 : -1;

      for (int var12 = 0; var12 < (var3 ? 2 : 1); var12++) {
         int var13 = var3 && var12 == 0 ? -var11 : var11;

         for (int var14 = 0; var14 < var4.length; var14 += 8) {
            double var15 = (double)var4[var14];
            double var17 = (double)var4[var14 + 1];
            double var19 = (double)(var4[var14 + 2] * (float)var13);
            if (var14 % 32 == 0 && var1.shown(Math.min(var17, (double)Math.min(var4[var14 + 8 + 1], var4[var14 + 16 + 1]))) < 0.001F) {
               var14 += 24;
            } else {
               double var21 = (double)var4[var14 + 5];
               double var23 = (double)var4[var14 + 6];
               double var25 = (double)(var4[var14 + 7] * (float)var13);
               var0.vertex(
                     (double)((float)(var5.x + var6.x * var15 + var7.x * var17 + var8.x * var19)),
                     (double)((float)(var5.y + var6.y * var15 + var7.y * var17 + var8.y * var19)),
                     (double)((float)(var5.z + var6.z * var15 + var7.z * var17 + var8.z * var19))
                  )
                  .texture(var13 > 0 ? var4[var14 + 3] : 1.0F - var4[var14 + 3], var4[var14 + 4])
                  .color(var13 > 0 ? 1.0F : 0.0F, 0.0F, 0.0F, var1.shown(var17))
                  .normal(
                     (float)(var9.x * var21 + var10.x * var23 + var8.x * var25),
                     (float)(var9.y * var21 + var10.y * var23 + var8.y * var25),
                     (float)(var9.z * var21 + var10.z * var23 + var8.z * var25)
                  )
                  .next();
            }
         }
      }
   }
}
