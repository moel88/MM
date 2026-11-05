package dev.sangwoo.celestialfall.client.isosang;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.StandClient;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class GStage {
   public static final int NONE = -1;
   public static final int BLACK = 0;
   public static final int GREY = 1;
   public static final int STORM = 2;
   public static final int DUSK = 3;
   static final int GROUND = 5;
   static final double SKY_R = 90.0;
   static final double GROUND_R = 16.0;
   static final Identifier BLADE = CelestialFall.id("textures/effect/isosang_g_blade.png");
   static final Identifier BLADE_N = CelestialFall.id("textures/effect/isosang_g_blade_n.png");
   private static ShaderProgram blade;
   private static ShaderProgram observerBlade;
   /** The stage's own program (isosang_stage: the pale sky with ink mountains behind her face, FACE_SPACE). */
   private static ShaderProgram stage;
   private static boolean materialMipmapsReady;
   static final double[] SLICES = slices();
   static final double BEVEL = 0.22;
   static final double EDGE = 0.2;
   private static final double[] IN = new double[]{0.0, 0.5, 1.0};
   private static int currentShot = -1;
   private static boolean bladeOnly;
   private static boolean wholeCaster;

   private GStage() {
   }

   static void initialize() {
      GClouds.initialize();
      CoreShaderRegistrationCallback.EVENT
         .register((CoreShaderRegistrationCallback)c -> c.register(CelestialFall.id("isosang_stage"), VertexFormats.POSITION_TEXTURE_COLOR, program -> stage = program));
      CoreShaderRegistrationCallback.EVENT
         .register(
            (CoreShaderRegistrationCallback)var0 -> var0.register(CelestialFall.id("isosang_blade"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, var0x -> {
                  blade = var0x;
                  GBladeMesh.invalidate();
                  materialMipmapsReady = false;
               })
         );
      CoreShaderRegistrationCallback.EVENT
         .register(
            (CoreShaderRegistrationCallback)var0 -> var0.register(
                  CelestialFall.id("isosang_blade_observer"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, var0x -> observerBlade = var0x
               )
         );
      WorldRenderEvents.START.register(GStage::frameStart);
      WorldRenderEvents.END.register(GStage::frameEnd);
   }

   public static boolean supported() {
      FabricLoader var0 = FabricLoader.getInstance();
      return !var0.isModLoaded("iris") && !var0.isModLoaded("oculus");
   }

   public static int space(float var0) {
      if (!supported()) {
         return -1;
      } else {
         StandClient.Cast var1 = FallingSword.cinematic();
         return var1 == null ? -1 : FallingSword.space(var1, var1.age(var0) / 20.0F);
      }
   }

   static void frameStart(WorldRenderContext var0) {
      float var1 = var0.tickDelta();
      currentShot = space(var1);
      StandClient.Cast var2 = currentShot == -1 ? null : FallingSword.cinematic();
      float var3 = var2 == null ? 0.0F : var2.age(var1) / 20.0F;
      bladeOnly = currentShot == 2 && FallingSword.alone(var3);
      wholeCaster = var2 != null && FallingSword.wholeBody(var3);
   }

   static void frameEnd(WorldRenderContext var0) {
      currentShot = -1;
      bladeOnly = false;
      wholeCaster = false;
   }

   /**
    * Whether the world is hidden from the frame being drawn: the black round her hand (GBlackout), and her face's own space
    * if it has one (FACE_SPACE).
    */
   public static boolean hidden() {
      return GBlackout.hidden() || FACE_SPACE && currentShot == GREY;
   }

   /**
    * Whether her face is shot in its own space (the videos' pale sky and ink mountains) rather than in the world. The sky
    * and the fall stay in the real world: the user made the sword come down there on purpose.
    */
   static final boolean FACE_SPACE = false;

   /** Draws the cutscene's own sky in place of the world's (from the sky's mixin); whether the world's is left out. */
   public static boolean sky(MatrixStack matrices, float delta) {
      if (!(FACE_SPACE && currentShot == GREY)) {
         return GBlackout.hidden();
      }
      StandClient.Cast c = FallingSword.cinematic();
      if (c == null || stage == null) {
         return true;
      }
      float t = c.age(delta) / 20.0F;
      int mode = 1;
      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      drawStage(matrices.peek().getPositionMatrix(), mode, t, b -> {
         int lat = 24, lon = 48;
         for (int i = 0; i < lat; i++) {
            double a0 = -Math.PI / 2 + Math.PI * i / lat, a1 = -Math.PI / 2 + Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
               double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
               sphere(b, a0, b0);
               sphere(b, a0, b1);
               sphere(b, a1, b1);
               sphere(b, a1, b0);
            }
         }
      });
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      return true;
   }

   private static void drawStage(Matrix4f view, int mode, float age, java.util.function.Consumer<BufferBuilder> fill) {
      MatrixStack mv = RenderSystem.getModelViewStack();
      mv.push();
      mv.multiplyPositionMatrix(view);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.setShader(() -> stage);
      stage.getUniform("Mode").set(mode);
      stage.getUniform("Age").set(age);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      BufferBuilder b = Tessellator.getInstance().getBuffer();
      b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      fill.accept(b);
      BufferRenderer.drawWithGlobalProgram(b.end());
      mv.pop();
      RenderSystem.applyModelViewMatrix();
   }

   private static void vertex(BufferBuilder b, double x, double y, double z, float u, float v) {
      b.vertex((float)x, (float)y, (float)z).texture(u, v).color(1.0F, 1.0F, 1.0F, 1.0F).next();
   }

   private static void sphere(BufferBuilder b, double lat, double lon) {
      double cl = Math.cos(lat);
      vertex(b, Math.cos(lon) * cl * SKY_R, Math.sin(lat) * SKY_R, Math.sin(lon) * cl * SKY_R, 0.0F, 0.0F);
   }

   /** The black round her hand (the world's colour cleared). */
   static void ground(WorldRenderContext ctx) {
      if (GBlackout.hidden()) {
         RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
         RenderSystem.clear(16384, MinecraftClient.IS_SYSTEM_MAC);
      }
   }

   /** Whether {@code e} is left out of the frame: everyone but her where the world is hidden, and her as the sword comes down. */
   public static boolean hides(Entity var0) {
      return hidden() && !GArenaClient.actor(var0) ? true : (currentShot == 3 || bladeOnly) && GArenaClient.actor(var0);
   }

   public static boolean handOnly(Entity var0) {
      return currentShot == 0 && !wholeCaster && GArenaClient.actor(var0);
   }

   public static boolean emptyHanded(Entity var0) {
      return (currentShot != -1 && GArenaClient.actor(var0)) | GGesture.signing(var0);
   }

   public static boolean staged(Entity var0) {
      return currentShot == 2 && !bladeOnly && GArenaClient.actor(var0);
   }

   public static void actor(MatrixStack var0, Entity var1, float var2) {
      if (staged(var1)) {
         StandClient.Cast var3 = FallingSword.cinematic();
         if (var3 != null) {
            float var4 = var3.age(var2) / 20.0F;
            if (!(var4 < 1.6F)) {
               Matrix4f var5 = GScene.actor(var3, var4, var1.getLerpedPos(var2));
               var0.multiplyPositionMatrix(var5);
               var0.peek().getNormalMatrix().mul(new Matrix3f(var5).invert().transpose());
            }
         }
      }
   }

   static void sword(WorldRenderContext var0) {
      if (blade != null) {
         world(var0);
      }
   }

   private static void world(WorldRenderContext var0) {
      for (StandClient.Cast var2 : worldCasts(var0)) {
         float var3 = var2.age(var0.tickDelta()) / 20.0F;
         FallingSword.Blade var4 = GWorld.blade(var2, var3);
         if (var4.alpha() > 0.004F) {
            boolean var5 = GWorld.observer(var2);
            ShaderProgram var6 = var5 ? observerBlade : blade;
            if (var6 != null) {
               solid(var0, var2, var4, var3, var6, var5);
            }
         }

         GImpact.solid(var0, var2, var3);
      }
   }

   static void worldClouds(WorldRenderContext var0) {
      if (!GBlackout.hidden()) {
         for (StandClient.Cast var2 : worldCasts(var0)) {
            float var3 = var2.age(var0.tickDelta()) / 20.0F;
            FallingSword.Blade var4 = GWorld.blade(var2, var3);
            GClouds.world(var0, var2, var4);
            if (var3 >= 3.1F) {
               GClouds.draw(var0, var2, var4, false);
            }
         }
      }
   }

   private static List<StandClient.Cast> worldCasts(WorldRenderContext var0) {
      MinecraftClient var1 = MinecraftClient.getInstance();
      return var1.world == null
         ? List.of()
         : IsosangClient.SWINGS
            .values()
            .stream()
            .filter(var2 -> var2.dimension().equals(var1.world.getRegistryKey().getValue()) && GWorld.active(var2, var2.age(var0.tickDelta()) / 20.0F))
            .sorted(Comparator.<StandClient.Cast>comparingDouble(var1x -> FallingSword.strike(var1x).squaredDistanceTo(var0.camera().getPos())).reversed())
            .toList();
   }

   private static void prepareBladeTextures() {
      if (!materialMipmapsReady) {
         int var0 = GL11.glGetInteger(32873);

         try {
            TextureManager var1 = MinecraftClient.getInstance().getTextureManager();

            for (Identifier var3 : List.of(BLADE, BLADE_N)) {
               RenderSystem.bindTexture(var1.getTexture(var3).getGlId());
               int var4 = 3553;
               GL11.glTexParameteri(var4, 33085, 13);
               GL11.glTexParameterf(var4, 33083, 13.0F);
               GL30.glGenerateMipmap(var4);
               GL11.glTexParameteri(var4, 10241, 9987);
            }

            materialMipmapsReady = true;
         } finally {
            RenderSystem.bindTexture(var0);
         }
      }
   }

   private static void solid(WorldRenderContext var0, StandClient.Cast var1, FallingSword.Blade var2, float var3, ShaderProgram var4, boolean var5) {
      Camera var6 = var0.camera();
      Vec3d var7 = var6.getPos();
      Matrix4f var8 = var0.matrixStack().peek().getPositionMatrix();
      Vec3d var9 = GScene.sun(var1);
      Vec3d var10 = var2.point().subtract(var7);
      var4.getUniform("Tip").set((float)var10.x, (float)var10.y, (float)var10.z);
      var4.getUniform("Exposure").set(var2.alpha(), (float)var2.fadeFrom(), (float)var2.fadeTo());
      Vec3d var11 = var2.across();
      Vec3d var12 = var2.axis();
      var4.getUniform("Across").set((float)var11.x, (float)var11.y, (float)var11.z);
      var4.getUniform("Axis").set((float)var12.x, (float)var12.y, (float)var12.z);
      var4.getUniform("Light").set((float)var9.x, (float)var9.y, (float)var9.z);
      var4.getUniform("Heat").set(var2.heat());
      var4.getUniform("Dark").set(var2.dark());
      var4.getUniform("Buried").set((float)Math.max(0.0, -GScene.height(var3)));
      var4.getUniform("Storm").set(var3 < 2.7833333F ? 1.0F : 0.0F);
      prepareBladeTextures();
      RenderSystem.setShaderTexture(0, BLADE);
      RenderSystem.setShaderTexture(1, BLADE_N);
      RenderSystem.enableDepthTest();
      RenderSystem.disableCull();
      RenderSystem.depthMask(true);
      MatrixStack var13 = RenderSystem.getModelViewStack();
      var13.push();
      var13.multiplyPositionMatrix(var8);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.setShader(() -> var4);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      var4.getUniform("Edge").set(0);
      BufferBuilder var14 = Tessellator.getInstance().getBuffer();
      if (var5) {
         GBladeMesh.drawObserver(var2, var7, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), var4);
      } else {
         var14.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
         GBladeMesh.draw(var14, var2, var7, false);
         BufferRenderer.drawWithGlobalProgram(var14.end());
      }

      var4.getUniform("Edge").set(1);
      var14.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
      edges(var14, var2, var7);
      BufferRenderer.drawWithGlobalProgram(var14.end());
      var13.pop();
      RenderSystem.applyModelViewMatrix();
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
   }

   private static double[] slices() {
      int var1 = 22;
      int var2 = 128;
      double var3 = 5.534;
      double var5 = 96.0;
      double[] var7 = new double[var1 + var2 + 1];

      for (int var0 = 0; var0 <= var1; var0++) {
         var7[var0] = var3 * Math.pow((double)var0 / (double)var1, 1.3);
      }

      for (int var8 = 1; var8 <= var2; var8++) {
         var7[var1 + var8] = var3 + (var5 - var3) * (double)var8 / (double)var2;
      }

      return var7;
   }

   static double[] surface(double var0, double var2, int var4) {
      double var5 = FallingSword.half(var2);
      double var7 = Math.min(0.22, var5 * 0.45);
      double var9 = var7 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, (var5 - Math.abs(var0)) / var7));
      double var11 = 0.375 * (0.2 + 0.8 * var9);
      boolean var13 = var9 < 1.0 || var9 >= 1.0 && var4 < 0 && Math.abs(var5 - Math.abs(var0) - var7) < 1.0E-9;
      double var14 = 0.30000000000000004;
      double var16 = !(var7 < 1.0E-6) && var13 ? -Math.signum(var0) * var14 / var7 : 0.0;
      double var18 = !(var7 < 1.0E-6) && var13 ? var14 * FallingSword.widening(var2) / var7 : 0.0;
      double var20 = Math.min(1.0, Math.max(0.0, var2 / 0.45));
      var18 = var18 * var20 + (var2 < 0.45 ? var11 / 0.45 : 0.0);
      double var25;
      double var22 = Math.sqrt((var25 = var16 * var20) * var25 + var18 * var18 + 1.0);
      double var24;
      return new double[]{var0, var24 = var11 * var20, -var25 / var22, -var18 / var22, 1.0 / var22};
   }

   static double[] across(double var0) {
      double var2 = FallingSword.half(var0);
      double var4 = Math.min(0.22, var2 * 0.45);
      double[] var6 = new double[IN.length * 2 + 1];
      int var7 = 0;

      for (double var11 : IN) {
         var6[var7++] = -(var2 - var11 * var4);
      }

      var6[var7++] = 0.0;

      for (int var14 = IN.length - 1; var14 >= 0; var14--) {
         var6[var7++] = var2 - IN[var14] * var4;
      }

      return var6;
   }

   private static void faces(BufferBuilder var0, FallingSword.Blade var1, Vec3d var2) {
      double var3 = 96.0;

      for (int var5 = -1; var5 <= 1; var5 += 2) {
         for (int var6 = 0; var6 + 1 < SLICES.length; var6++) {
            double var7 = SLICES[var6];
            double var9 = SLICES[var6 + 1];
            float var11 = var1.shown(var7);
            float var12 = var1.shown(var9);
            if (!(var11 <= 0.004F) || !(var12 <= 0.004F)) {
               double[] var13 = across(var7);
               double[] var14 = across(var9);

               for (int var15 = 0; var15 + 1 < var13.length; var15++) {
                  int var16 = var15 >= IN.length - 1 && var15 < var13.length - IN.length ? 1 : -1;
                  blade(var0, var1, var2, var13[var15], var7, var5, var11, var3, var16);
                  blade(var0, var1, var2, var13[var15 + 1], var7, var5, var11, var3, var16);
                  blade(var0, var1, var2, var14[var15 + 1], var9, var5, var12, var3, var16);
                  blade(var0, var1, var2, var14[var15], var9, var5, var12, var3, var16);
               }
            }
         }
      }
   }

   private static void blade(BufferBuilder var0, FallingSword.Blade var1, Vec3d var2, double var3, double var5, int var7, float var8, double var9, int var11) {
      double[] var12 = surface(var3, var5, var11);
      Vec3d var13 = var1.at(var3, var5, (double)var7 * var12[1]).subtract(var2);
      Vec3d var14 = var1.normalAt(var12[2], var12[3], (double)var7 * var12[4]);
      float var15 = 0.5F + (float)(var3 / 7.6);
      if (var7 < 0) {
         var15 = 1.0F - var15;
      }

      var0.vertex((double)((float)var13.x), (double)((float)var13.y), (double)((float)var13.z))
         .texture(var15, 1.0F - (float)(var5 / var9))
         .color(var7 > 0 ? 1.0F : 0.0F, 0.0F, 0.0F, var8)
         .normal((float)var14.x, (float)var14.y, (float)var14.z)
         .next();
   }

   private static void edges(BufferBuilder var0, FallingSword.Blade var1, Vec3d var2) {
      for (int var3 = -1; var3 <= 1; var3 += 2) {
         for (int var4 = 0; var4 + 1 < SLICES.length; var4++) {
            double var5 = SLICES[var4];
            double var7 = SLICES[var4 + 1];
            float var9 = var1.shown(var5);
            float var10 = var1.shown(var7);
            if (!(var9 <= 0.004F) || !(var10 <= 0.004F)) {
               edge(var0, var1, var2, var3, var5, -1, var9);
               edge(var0, var1, var2, var3, var5, 1, var9);
               edge(var0, var1, var2, var3, var7, 1, var10);
               edge(var0, var1, var2, var3, var7, -1, var10);
            }
         }
      }
   }

   private static void edge(BufferBuilder var0, FallingSword.Blade var1, Vec3d var2, int var3, double var4, int var6, float var7) {
      double var8 = FallingSword.half(var4);
      double var10 = FallingSword.edgeDepth(var4);
      Vec3d var12 = var1.normalAt((double)var3, -FallingSword.widening(var4), 0.0);
      Vec3d var13 = var1.at((double)var3 * var8, var4, (double)var6 * var10).subtract(var2);
      var0.vertex((double)((float)var13.x), (double)((float)var13.y), (double)((float)var13.z))
         .texture(0.5F, 1.0F - (float)(var4 / 96.0))
         .color(1.0F, 0.0F, 0.0F, var7)
         .normal((float)var12.x, (float)var12.y, (float)var12.z)
         .next();
   }
}
