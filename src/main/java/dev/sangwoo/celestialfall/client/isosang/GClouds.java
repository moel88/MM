package dev.sangwoo.celestialfall.client.isosang;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.isosang.GArenaBounds;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class GClouds {
   private static final Identifier NOISE = CelestialFall.id("textures/effect/isosang_g_density.png");
   private static final Identifier MATERIAL = CelestialFall.id("textures/effect/isosang_g_cloud_material.png");
   private static final Identifier PRESSURE = CelestialFall.id("textures/effect/isosang_g_cloud_pressure.png");
   private static final Identifier CONDENSATION = CelestialFall.id("textures/effect/isosang_g_condensation.png");
   private static ShaderProgram shader;
   private static SimpleFramebuffer depth;

   private GClouds() {
   }

   static void initialize() {
      CoreShaderRegistrationCallback.EVENT
         .register(
            (CoreShaderRegistrationCallback)var0 -> var0.register(
                  CelestialFall.id("isosang_cloud"), VertexFormats.POSITION_TEXTURE_COLOR, var0x -> shader = var0x
               )
         );
   }

   private static void vec(String var0, Vec3d var1) {
      shader.getUniform(var0).set((float)var1.x, (float)var1.y, (float)var1.z);
   }

   private static Vec3d local(Vec3d var0, Vec3d var1, Vec3d var2, Vec3d var3) {
      return new Vec3d(var0.dotProduct(var1), var0.dotProduct(var2), var0.dotProduct(var3));
   }

   static void draw(WorldRenderContext var0, StandClient.Cast var1, FallingSword.Blade var2, boolean var3) {
      draw(var0, var1, var2, var3 ? 0 : 1);
   }

   static void world(WorldRenderContext var0, StandClient.Cast var1, FallingSword.Blade var2) {
      draw(var0, var1, var2, GWorld.observer(var1) ? 3 : 2);
   }

   private static void draw(WorldRenderContext var0, StandClient.Cast var1, FallingSword.Blade var2, int var3) {
      if (shader != null) {
         float var4 = var1.age(var0.tickDelta()) / 20.0F;
         if (var3 == 1 || !(var4 >= 4.82F)) {
            MinecraftClient var5 = MinecraftClient.getInstance();
            Framebuffer var6 = var5.getFramebuffer();
            if (GL11.glGetInteger(36006) == var6.fbo) {
               if (depth == null) {
                  depth = new SimpleFramebuffer(var6.textureWidth, var6.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
               } else if (depth.textureWidth != var6.textureWidth || depth.textureHeight != var6.textureHeight) {
                  depth.resize(var6.textureWidth, var6.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
               }

               depth.copyDepthFrom(var6);
               var6.beginWrite(true);
               boolean var7 = var3 == 3;
               boolean var8 = var3 != 1;
               if (!var8 && GWorld.observer(var1)) {
                  var3 = 4;
               }

               FallingSword.Frame var9 = GScene.skyFrame(var1, Math.max(1.51F, Math.min(var4, 2.7833333F)));
               Vec3d var10 = var8 ? var9.r() : new Vec3d(1.0, 0.0, 0.0);
               Vec3d var11 = var8 ? var9.u() : new Vec3d(0.0, 1.0, 0.0);
               Vec3d var12 = var8 ? var9.f() : new Vec3d(0.0, 0.0, 1.0);
               Vec3d var13 = var8 ? var9.eye() : FallingSword.strike(var1);
               if (var7) {
                  var12 = FallingSword.forward(var1);
                  var10 = FallingSword.right(var12);
                  var11 = FallingSword.UP;
                  var13 = GScene.cloudCenter(var1);
               }

               Vec3d var14 = var0.camera().getPos();
               RenderSystem.setShader(() -> shader);
               RenderSystem.setShaderTexture(0, NOISE);
               RenderSystem.setShaderTexture(1, depth.getDepthAttachment());
               RenderSystem.setShaderTexture(2, MATERIAL);
               RenderSystem.setShaderTexture(3, PRESSURE);
               RenderSystem.setShaderTexture(4, CONDENSATION);
               shader.getUniform("InverseViewProjection")
                  .set(new Matrix4f(RenderSystem.getProjectionMatrix()).mul(var0.matrixStack().peek().getPositionMatrix()).invert());
               vec("Sun", local(GScene.sun(var1), var10, var11, var12));
               vec("Origin", var13.subtract(var14));
               vec("CloudCenter", local(GScene.cloudCenter(var1).subtract(var13), var10, var11, var12));
               vec("BasisX", var10);
               vec("BasisY", var11);
               vec("BasisZ", var12);
               vec("BladePoint", local(var2.point().subtract(var13), var10, var11, var12));
               vec("BladeAcross", local(var3 != 4 ? var2.across() : var2.across().normalize(), var10, var11, var12));
               vec("BladeAxis", local(var2.axis(), var10, var11, var12));
               vec("BladeNormal", local(var3 != 4 ? var2.normal() : var2.normal().normalize(), var10, var11, var12));
               shader.getUniform("DistantView").set(!var7 && var3 != 4 ? 0.0F : GWorld.distantWeight(var1, var14));
               // (2.87.1) the blast fills its ground: the arena, or 62 × 47 blocks round where the sword struck
               GArenaBounds var15 = GArenaClient.area(var1);
               Vec3d var16 = FallingSword.strike(var1);
               float var17 = (float)GArenaClient.radius(var1);
               shader.getUniform("BurstRadius").set(var17);
               shader.getUniform("ArenaRect")
                  .set((float)(var15.minX() - var16.x), (float)(var15.minZ() - var16.z), (float)(var15.maxX() - var16.x), (float)(var15.maxZ() - var16.z));
               shader.getUniform("Age").set(var4);
               shader.getUniform("Mode").set(var3);
               RenderSystem.disableDepthTest();
               RenderSystem.depthMask(false);
               RenderSystem.disableCull();
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               BufferBuilder var18 = Tessellator.getInstance().getBuffer();
               var18.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
               var18.vertex(-1.0, -1.0, 0.0).texture(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).next();
               var18.vertex(1.0, -1.0, 0.0).texture(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).next();
               var18.vertex(1.0, 1.0, 0.0).texture(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, 1.0F).next();
               var18.vertex(-1.0, 1.0, 0.0).texture(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, 1.0F).next();
               BufferRenderer.drawWithGlobalProgram(var18.end());
               RenderSystem.depthMask(true);
               RenderSystem.enableDepthTest();
               RenderSystem.enableCull();
               RenderSystem.disableBlend();
            }
         }
      }
   }
}
