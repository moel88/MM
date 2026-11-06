package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

final class GScene {
   static final float GATHER = 0.35F;
   static final float REVEAL = 1.51F;
   static final float CLOUD_CLEAR = 4.8F;
   static final float FADE = 4.6F;
   static final float END = 4.78F;
   static final double CLOUD_HEIGHT = 40.0;
   static final double SKY_HEIGHT = 40.0;
   static final double RELIEF = 0.1;
   static final double SKY_FOV = 59.82126740260396;
   static final Vec3d CAMERA_ACROSS = new Vec3d(0.8110692926216011, 0.5848031774182791, 0.01310901398962347);
   static final Vec3d CAMERA_AXIS = new Vec3d(-0.3487402431232067, 0.4654362269567147, 0.8134797855279896);
   static final Vec3d CAMERA_NORMAL = new Vec3d(-0.4696241533318556, 0.6643601149360052, -0.5814454336303241);
   static final Vec3d CAMERA_POINT = new Vec3d(1.9535245920695856, -0.9325270448285823, 5.286404154369239);

   private GScene() {
   }

   static boolean active(StandClient.Cast var0, float var1) {
      return var0.combo() == 9 && var1 >= 0.35F && var1 < 7.0F && Float.isNaN(FallingSword.off(var0));
   }

   static Vec3d axis(StandClient.Cast var0) {
      double var1 = Math.toRadians(16.96);
      return FallingSword.UP.multiply(Math.cos(var1)).subtract(FallingSword.right(FallingSword.forward(var0)).multiply(Math.sin(var1)));
   }

   static Vec3d normal(StandClient.Cast var0) {
      return FallingSword.forward(var0).negate();
   }

   static Vec3d across(StandClient.Cast var0) {
      return axis(var0).crossProduct(normal(var0));
   }

   static Vec3d cloudCenter(StandClient.Cast var0) {
      return FallingSword.strike(var0).add(axis(var0).multiply(40.0));
   }

   static Vec3d sun(StandClient.Cast var0) {
      return across(var0).multiply(-0.45).add(axis(var0).multiply(0.8)).add(normal(var0).multiply(0.65)).normalize();
   }

   static double height(float var0) {
      if (var0 >= 2.7833333F) {
         return FallingSword.fallHeight(var0);
      } else {
         double var1 = (double)Ease.smooth(var0, 1.51F, 2.7833333F);
         return 40.0 + -8.0 * var1;
      }
   }

   static FallingSword.Blade blade(StandClient.Cast var0, float var1) {
      float var2 = active(var0, var1) ? Ease.smooth(var1, 1.51F, 1.61F) * (1.0F - Ease.smooth(var1, 4.6F, 4.78F)) : 0.0F;
      float var3 = 1.0F - 0.15F * Ease.smooth(var1, 3.3999999F, 5.55F);
      double var4 = height(var1);
      double var6 = 40.0 - var4 + 12.0;
      return new FallingSword.Blade(
         FallingSword.strike(var0).add(axis(var0).multiply(var4)), axis(var0), across(var0), normal(var0), var2, var3, 0.0F, var6, var6 + 20.0
      );
   }

   private static Vec3d component(StandClient.Cast var0, double var1, double var3, double var5) {
      return across(var0).multiply(var1).add(axis(var0).multiply(var3)).add(normal(var0).multiply(var5));
   }

   static FallingSword.Frame skyFrame(StandClient.Cast var0, float var1) {
      Vec3d var2 = component(var0, CAMERA_ACROSS.x, CAMERA_AXIS.x, CAMERA_NORMAL.x);
      Vec3d var3 = component(var0, CAMERA_ACROSS.y, CAMERA_AXIS.y, CAMERA_NORMAL.y);
      Vec3d var4 = component(var0, CAMERA_ACROSS.z, CAMERA_AXIS.z, CAMERA_NORMAL.z);
      double var5 = FallingSword.nearing(1.95F) / FallingSword.nearing(var1);
      double var7 = CAMERA_POINT.z * var5;
      double var9 = poly(FallingSword.SKY_TIP_X, var1) - poly(FallingSword.SKY_TIP_X, 1.95F);
      double var11 = poly(FallingSword.SKY_TIP_Y, var1) - poly(FallingSword.SKY_TIP_Y, 1.95F);
      double var13 = Math.tan(Math.toRadians(29.91063370130198));
      Vec3d var15 = CAMERA_POINT.multiply(var5).add(4.334085778781039 * var13 * var7 * var9, -2.0 * var13 * var7 * var11, 0.0);
      Vec3d var16 = blade(var0, var1).point().subtract(var2.multiply(var15.x)).subtract(var3.multiply(var15.y)).subtract(var4.multiply(var15.z));
      return new FallingSword.Frame(var16, var4, var2, var3);
   }

   private static double poly(double[] var0, float var1) {
      return var0[0] + (double)var1 * (var0[1] + (double)var1 * var0[2]);
   }

   static FallingSword.Frame clouds(StandClient.Cast var0, float var1) {
      FallingSword.Frame var2 = skyFrame(var0, Math.max(1.51F, Math.min(var1, 2.7833333F)));
      Vec3d var3 = var2.at(0.552, 0.449, 1.0, 59.82126740260396).subtract(var2.eye()).normalize();
      Vec3d var4 = var2.r().subtract(var3.multiply(var3.dotProduct(var2.r()))).normalize();
      return new FallingSword.Frame(var2.eye(), var3, var4, var4.crossProduct(var3));
   }

   static FallingSword.Shot skyShot(StandClient.Cast var0, float var1) {
      FallingSword.Frame var2 = skyFrame(var0, var1);
      Vec3d var3 = var2.f().crossProduct(FallingSword.UP).normalize();
      Vec3d var4 = var3.crossProduct(var2.f());
      float var5 = (float)(-Math.toDegrees(Math.atan2(var2.r().dotProduct(var4), var2.r().dotProduct(var3))));
      return new FallingSword.Shot(var2.eye(), var2.eye().add(var2.f()), 59.82126740260396, var5);
   }

   /**
    * Her figure in the sky shot, as the videos frame her (2.87.2, user request: "컷씬 나올때 원본 영상은 캐릭터가 다리부터
    * 보이는데 너가 만든건 캐릭터가 아예 공중에 떠있음 똑같이 다리부터 보이게 만들어줘"): her face at (0.383, 0.522) of the 21:9
    * frame and her legs out through its bottom edge at about 0.30 across, cut at the knee, her feet below it (the shot
    * looks up at her, so her legs come big towards it). How much bigger she is drawn than the camera she was fitted to
    * shows her, how far she leans round the shot's line of sight (degrees, counterclockwise as seen; the look up from
    * below already sends her feet to the frame's lower left), and where her head is moved in the frame (right and down,
    * in frame heights). 2.87.0's 0.68 times and -24 degrees, her head sized as the videos', left her whole and floating
    * in the sky.
    */
   static final float ACTOR_SCALE = 1.18F;
   static final double ACTOR_LEAN = -2.0;
   static final double ACTOR_RIGHT = -0.07, ACTOR_DOWN = 0.015;
   /** How far above her feet her figure is pivoted for it (her neck). */
   static final double ACTOR_PIVOT = 1.45;

   /**
    * Her figure in the sky shot (GStage.actor): drawn as the camera she was fitted to (FallingSword.skyView) would show her,
    * through the shot's own camera, then made slighter and leaning away round her head (ACTOR_*). The identity at the
    * face shot, eased in over its cut (1.6 to 1.65 s).
    */
   static Matrix4f actor(StandClient.Cast c, float t, Vec3d pos) {
      return actor(c, t, pos, ACTOR_SCALE, ACTOR_LEAN, ACTOR_RIGHT, ACTOR_DOWN);
   }

   /** The same with its size, lean and where her head is moved to in the frame (right and down, in frame heights) given. */
   static Matrix4f actor(StandClient.Cast c, float t, Vec3d pos, float scale, double leanDegrees, double right, double down) {
      FallingSword.Frame view = FallingSword.skyView(c);
      FallingSword.Frame frame = skyFrame(c, t);
      double k = Math.tan(Math.toRadians(29.91063370130198)) / Math.tan(Math.toRadians(42.21));
      Vec3d[] axes = new Vec3d[3];
      for (int i = 0; i < 3; i++) {
         Vec3d e = i == 0 ? new Vec3d(1.0, 0.0, 0.0) : (i == 1 ? new Vec3d(0.0, 1.0, 0.0) : new Vec3d(0.0, 0.0, 1.0));
         axes[i] = frame.r().multiply(k * e.dotProduct(view.r())).add(frame.u().multiply(k * e.dotProduct(view.u()))).add(frame.f().multiply(e.dotProduct(view.f())));
      }
      // her head where the fitted camera puts it; the rest of her slighter and turned about it in the frame's plane
      Vec3d head = pos.add(0.0, ACTOR_PIVOT, 0.0);
      Vec3d rel = head.subtract(view.eye());
      Vec3d headAt = frame.eye().add(axes[0].multiply(rel.x)).add(axes[1].multiply(rel.y)).add(axes[2].multiply(rel.z));
      // moved in the frame's plane, at its own depth: a frame height there is 2 tan(fov / 2) times that depth
      double depth = headAt.subtract(frame.eye()).dotProduct(frame.f()), across = 2.0 * Math.tan(Math.toRadians(59.82126740260396 / 2.0)) * depth;
      headAt = headAt.add(frame.r().multiply(right * across)).add(frame.u().multiply(-down * across));
      double lean = Math.toRadians(leanDegrees), cos = Math.cos(lean), sin = Math.sin(lean);
      for (int i = 0; i < 3; i++) {
         Vec3d a = axes[i];
         double x = a.dotProduct(frame.r()), y = a.dotProduct(frame.u()), z = a.dotProduct(frame.f());
         axes[i] = frame.r().multiply(x * cos - y * sin).add(frame.u().multiply(x * sin + y * cos)).add(frame.f().multiply(z)).multiply(scale);
      }
      Vec3d back = pos.subtract(head);
      Vec3d at = headAt.add(axes[0].multiply(back.x)).add(axes[1].multiply(back.y)).add(axes[2].multiply(back.z)).subtract(pos);
      Matrix4f m = new Matrix4f();
      m.setColumn(0, new Vector4f((float)axes[0].x, (float)axes[0].y, (float)axes[0].z, 0.0F));
      m.setColumn(1, new Vector4f((float)axes[1].x, (float)axes[1].y, (float)axes[1].z, 0.0F));
      m.setColumn(2, new Vector4f((float)axes[2].x, (float)axes[2].y, (float)axes[2].z, 0.0F));
      m.setTranslation((float)at.x, (float)at.y, (float)at.z);
      return new Matrix4f().lerp(m, Ease.smooth(t, 1.6F, 1.65F));
   }
}
