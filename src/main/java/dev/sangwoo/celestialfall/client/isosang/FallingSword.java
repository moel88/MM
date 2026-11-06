package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;

import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

final class FallingSword {
   static final float LIFE = 7.4F;
   static final float GONE = 0.6F;
   static final Vec3d UP = new Vec3d(0.0, 1.0, 0.0);
   static final int GOLD = 16761962;
   static final int GOLD_WHITE = 16771512;
   static final int WHITE = 16777215;
   static final int SHARD = 4034047;
   static final int EYE = 16768160;
   static final R6.Pose G_RAISE = IsosangSlash.w(
      0.0,
      0.0,
      -6.0F,
      2.0F,
      0.0F,
      IsosangSlash.d(-74.0, 10.0),
      IsosangSlash.d(18.0, 16.0),
      IsosangSlash.d(4.0, -55.0),
      IsosangSlash.d(-90.0, 3.0),
      IsosangSlash.d(-90.0, -3.0)
   );
   static final R6.Pose G_SWEEP = IsosangSlash.w(
         0.0,
         0.0,
         -10.0F,
         -2.0F,
         0.0F,
         IsosangSlash.d(-74.0, 10.0),
         IsosangSlash.d(16.0, 18.0),
         IsosangSlash.d(48.0, -40.0),
         IsosangSlash.d(-90.0, 3.0),
         IsosangSlash.d(-90.0, -3.0)
      )
      .head(-8.0F, -14.0F, 0.0F);
   static final R6.Pose G_POINT = IsosangSlash.w(
         0.0,
         0.0,
         -12.0F,
         -4.0F,
         0.0F,
         IsosangSlash.d(-74.0, 10.0),
         IsosangSlash.d(16.0, 18.0),
         IsosangSlash.d(54.0, -44.0),
         IsosangSlash.d(-90.0, 3.0),
         IsosangSlash.d(-90.0, -3.0)
      )
      .head(-10.0F, -20.0F, 0.0F);
   static final R6.Pose G_POINT2 = IsosangSlash.w(
         0.0,
         0.0,
         -12.0F,
         -4.0F,
         0.0F,
         IsosangSlash.d(-74.0, 10.0),
         IsosangSlash.d(16.0, 18.0),
         IsosangSlash.d(56.0, -40.0),
         IsosangSlash.d(-90.0, 3.0),
         IsosangSlash.d(-90.0, -3.0)
      )
      .head(-10.0F, -22.0F, 0.0F);
   static final R6.Pose G_STROKE = IsosangSlash.w(
         0.0,
         0.0,
         -4.0F,
         -2.0F,
         0.0F,
         IsosangSlash.d(-74.0, 10.0),
         IsosangSlash.d(16.0, 18.0),
         IsosangSlash.d(50.0, 22.0),
         IsosangSlash.d(-90.0, 3.0),
         IsosangSlash.d(-90.0, -3.0)
      )
      .head(-2.0F, -12.0F, 0.0F);
   static final R6.Pose G_SHH = IsosangSlash.w(
         0.0,
         0.0,
         0.0F,
         0.0F,
         0.0F,
         IsosangSlash.d(-12.0, -53.0),
         IsosangSlash.d(-55.0, -35.0),
         IsosangSlash.d(30.0, 46.0),
         IsosangSlash.d(-90.0, 4.0),
         IsosangSlash.d(-90.0, -4.0)
      )
      .head(0.0F, 5.0F, 0.0F);
   static final R6.Pose G_SHH2 = IsosangSlash.w(
         0.0,
         0.0,
         0.0F,
         1.0F,
         0.0F,
         IsosangSlash.d(-13.0, -52.0),
         IsosangSlash.d(-56.0, -34.0),
         IsosangSlash.d(31.0, 45.0),
         IsosangSlash.d(-90.0, 4.0),
         IsosangSlash.d(-90.0, -4.0)
      )
      .head(1.0F, 6.0F, 1.0F);
   static final R6.Pose G_CALL = IsosangSlash.w(
         0.0,
         0.0,
         0.0F,
         6.0F,
         0.0F,
         IsosangSlash.d(-60.0, 30.0),
         IsosangSlash.d(-10.0, 40.0),
         IsosangSlash.d(-6.0, 2.0),
         IsosangSlash.d(-90.0, 3.0),
         IsosangSlash.d(-90.0, -3.0)
      )
      .head(0.0F, 10.0F, 0.0F);
   static final R6.Pose G_CALL2 = IsosangSlash.w(
         0.0,
         0.0,
         0.0F,
         4.0F,
         0.0F,
         IsosangSlash.d(-62.0, 28.0),
         IsosangSlash.d(-6.0, 36.0),
         IsosangSlash.d(-10.0, 4.0),
         IsosangSlash.d(-90.0, 3.0),
         IsosangSlash.d(-90.0, -3.0)
      )
      .head(0.0F, 8.0F, 0.0F);
   static final R6.Pose G_WATCH = IsosangSlash.w(
         0.0,
         0.0,
         0.0F,
         2.0F,
         0.0F,
         IsosangSlash.d(-68.0, 16.0),
         IsosangSlash.d(20.0, 10.0),
         IsosangSlash.d(-44.0, -12.0),
         IsosangSlash.d(-90.0, 3.0),
         IsosangSlash.d(-90.0, -3.0)
      )
      .head(0.0F, 4.0F, 0.0F);
   static final Anim CLIP = Anim.joints(
         Anim.k(0.0, IsosangSlash.STANCE),
         Anim.k(0.16, G_RAISE, Anim.Ease.OUT),
         Anim.k(0.3, G_RAISE),
         Anim.k(0.44, G_SWEEP, Anim.Ease.OUT),
         Anim.k(0.62, G_POINT),
         Anim.k(0.83, G_POINT2),
         Anim.k(0.96, G_STROKE, Anim.Ease.OUT),
         Anim.k(1.06, G_SHH, Anim.Ease.OUT),
         Anim.k(2.6, G_SHH2),
         Anim.k(2.8, G_CALL, Anim.Ease.OUT),
         Anim.k(3.3, G_CALL2),
         Anim.k(3.9, G_WATCH),
         Anim.k(4.85F, IsosangSlash.STANCE)
      )
      .after(IsosangSlash::grounded);
   static final double POINT = 5.534;
   static final double ROUND = 1.503;
   static final double THICK = 0.75;
   static final double FALL_FROM = 32.0;
   static final double FALL_LEAN = 16.96;
   static final float DARK0 = 4.3F;
   static final float DARK1 = 4.83F;
   static final float PENETRATION_TIME = 2.4F;
   static final float RAISED = 0.16F;
   static final float S_WHIP = 0.3F;
   static final float S_ARM = 0.45F;
   static final float S_STROKE = 0.83F;
   static final float S_FACE = 1.008F;
   static final float S_SIDE = 1.42F;
   static final float S_SKY = 1.51F;
   static final float S_FALL = 2.7833333F;
   static final float S_GREY = 1.058F;
   public static final double ASPECT = 2.1670428893905194;
   static final double SKY_EYE_FW = 1.114;
   static final double SKY_EYE_SIDE = -0.023;
   static final double SKY_EYE_UP = -0.326;
   static final double SKY_PITCH = 60.0;
   static final double SKY_PAN = 41.92;
   static final double SKY_ROLL = 27.61;
   static final double SKY_FOV = 84.42;
   static final double SKY_TIP_DEPTH = 3.23023249;
   static final Vec3d SKY_ACROSS = new Vec3d(0.82441557, 0.55122074, -0.12843154);
   static final Vec3d SKY_AXIS = new Vec3d(-0.39547105, 0.48696541, 0.5620446);
   static final Vec3d SKY_NORMAL = new Vec3d(-0.44742353, 0.49574641, -0.74434379);
   static final double[] SKY_TIP_X = new double[]{0.5989945, 0.01809719, 0.00359858};
   static final double[] SKY_TIP_Y = new double[]{0.66175, -0.06177856, 0.0290817};
   static final double[] NEARING = new double[]{0.70791, 0.19574, -0.02285};
   static final double RING_X = 0.5519;
   static final double RING_Y = 0.4486;
   static final double HOLE = 29.5;
   static final double FALL_BACK = 7.19;
   static final double FALL_SIDE = -0.82;
   static final double FALL_UP = 3.667;
   static final double FALL_LOOK = 4.155;
   static final double FALL_PUSH = 0.18;
   static final double FALL_FOV = 58.0;
   static final double[][] LANDING_HEIGHT = new double[][]{{3.0, 3.667}, {3.1, 2.9225}, {3.15, 2.7}, {3.2, 2.57}, {3.24, 2.57}};
   static final float S_PULL = 1.6F;
   static final float S_PULLED = 1.65F;
   static final float S_ALONE = 2.75F;
   static final double FACE_FOV = 48.0;
   static final double FACE_EYES = 0.135;
   static final double EYE_SPACING = 0.25;
   static final double EYE_DROP = 0.031;
   static final double[][] FACE_AT = new double[][]{
      {1.008, 0.47, 0.3},
      {1.067, 0.483, 0.3065},
      {1.25, 0.4935, 0.3255},
      {1.45, 0.5015, 0.3345},
      {1.483, 0.5055, 0.289},
      {1.5, 0.5085, 0.2425},
      {1.517, 0.508, 0.227},
      {1.533, 0.508, 0.247},
      {1.55, 0.5075, 0.257},
      {1.567, 0.5135, 0.2335}
   };

   private FallingSword() {
   }

   static Vec3d hand(Vec3d feet, float yaw, float t) {
      return KagenoClips.freeHand(feet, yaw, R6.poseOf(CLIP.frame(t)));
   }

   static Vec3d tip(Vec3d feet, float yaw, float t) {
      R6.Pose p = R6.poseOf(CLIP.frame(t));
      Vec3d h = KagenoClips.freeHand(feet, yaw, p);
      Vec3d s = KagenoClips.freeShoulder(feet, yaw, p);
      Vec3d d = h.subtract(s);
      return d.lengthSquared() < 1.0E-6 ? h : h.add(d.normalize().multiply(0.13));
   }

   static Vec3d head(Vec3d feet, float yaw, float t) {
      return KagenoClips.head(feet, yaw, R6.poseOf(CLIP.frame(t)));
   }

   static Vec3d forward(StandClient.Cast c) {
      return Vec3d.fromPolar(0.0F, c.yaw());
   }

   static Vec3d right(Vec3d f) {
      return new Vec3d(-f.z, 0.0, f.x);
   }

   static Vec3d strike(StandClient.Cast c) {
      IsosangClient.Ultimate u = IsosangClient.ultimate(c.id());
      return u != null ? u.strike() : c.origin().add(forward(c).multiply(9.0));
   }

   static int variant(StandClient.Cast c) {
      IsosangClient.Ultimate u = IsosangClient.ultimate(c.id());
      return u == null ? 0 : u.variant();
   }

   static float off(StandClient.Cast c) {
      return IsosangClient.ultimateEnd(c.id());
   }

   static int space(StandClient.Cast c, float t) {
      if (!(t < 0.0F) && !(t >= 4.83F) && Float.isNaN(off(c))) {
         return t < 1.058F ? 0 : (t < 1.51F ? 1 : (t < 2.7833333F ? 2 : 3));
      } else {
         return -1;
      }
   }

   static double half(double y) {
      double r = 3.8;
      if (y < 0.0) {
         return 0.0;
      } else if (y < 5.534) {
         double u = y / 5.534;
         return r * (1.0 - Math.pow(1.0 - u, 1.503));
      } else {
         return r;
      }
   }

   static double edgeDepth(double y) {
      return 0.07500000000000001 * Math.min(1.0, Math.max(0.0, y / 0.45));
   }

   static double widening(double y) {
      return !(y < 0.0) && !(y >= 5.534) ? 5.711399999999999 * Math.pow(1.0 - y / 5.534, 0.5029999999999999) / 5.534 : 0.0;
   }

   static Vec3d square(Vec3d a, Vec3d axis) {
      Vec3d n = a.subtract(axis.multiply(a.dotProduct(axis)));
      return n.lengthSquared() < 1.0E-8 ? right(axis).normalize() : n.normalize();
   }

   static FallingSword.Blade blade(StandClient.Cast c, float t) {
      return !(t < 1.51F) && !(t >= 4.83F) && Float.isNaN(off(c)) ? GScene.blade(c, t) : null;
   }

   static double fallHeight(float t) {
      if (t < 3.1F) {
         double u = (double)Math.max(0.0F, Math.min(1.0F, (t - 2.7833333F) / 0.3166666F));
         return 32.0 * (1.0 - u * u);
      } else {
         double a = (double)Math.max(0.0F, t - 3.1F);
         double bite = 0.2 * (1.0 - Math.exp(-a / 0.18));
         double drive = (double)Ease.smooth((float)a, 0.0F, 2.4F);
         return -bite - 2.4 * drive;
      }
   }

   static Vec3d stormEye(StandClient.Cast c, float t) {
      return ring(c);
   }

   static Vec3d feet(Fx c) {
      return c.origin;
   }

   static boolean shown(Fx c) {
      StandClient.Cast own = cinematic();
      return own != null && own.id().equals(c.cast.id()) && c.sec < 4.83F;
   }

   static void additive(Fx c, EffectMesh m) {
      float t = c.sec;
      Vec3d feet = feet(c);
      float yaw = c.cast.yaw();
      boolean gone = !Float.isNaN(off(c.cast));
      if (!gone) {
         fingertip(c, m, t, feet, yaw);
      }

      if (shown(c)) {
         faceLight(c, m, t);
         sky(c, m, t);
         FallingSword.Blade bl = blade(c.cast, t);
         if (bl != null) {
            edges(c, m, t, bl);
            GWorld.wind(c, m, t, bl);
         }
      } else if (!GStage.hidden() && GWorld.active(c.cast, t)) {
         FallingSword.Blade bl = GWorld.blade(c.cast, t);
         if (bl.alpha() > 0.004F) {
            edges(c, m, t, bl);
            GWorld.wind(c, m, t, bl);
         }
      }

      float ks = FxLib.kick(t, 3.1F, 0.18F);
      if (ks > 0.01F && !gone && (!shown(c) || !GStage.supported())) {
         Vec3d s = strike(c.cast).add(0.0, 1.0, 0.0);
         m.softDisc(s, 6.0, 16761962, 0.42F * ks);
         m.softDisc(s, 2.4, 16771512, 0.5F * ks);
         m.star(s, 3.4, 16777215, 0.6F * ks);
      }

      if (!gone && (shown(c) || !GStage.hidden())) {
         GImpact.sparks(c, m, t);
      }

      GGrand.additive(c, m);
   }

   static void foot(Fx c, EffectMesh m, float t) {
      float a = Ease.window(t, 3.1F, 4.83F, 0.05F, 0.6F);
      if (!(a <= 0.01F)) {
         Vec3d s = strike(c.cast).add(0.0, 0.5, 0.0);
         float flick = 0.85F + 0.15F * (float)Math.sin((double)(t * 37.0F)) * (float)Math.sin((double)(t * 23.0F + 1.0F));
         float hot = 1.0F - Ease.smooth(t, 3.1F, 4.3F);
         m.softDisc(s, 4.5, 16747056, 0.4F * a * flick);
         m.softDisc(s.add(0.0, 0.3, 0.0), 1.8, 16765056, (0.3F + 0.4F * hot) * a * flick);
      }
   }

   static void rays(Fx c, EffectMesh m, float t) {
      float after = t - 3.1F;
      if (!(after < 0.0F) && !(after > 0.6F)) {
         Vec3d s = strike(c.cast);

         for (int i = 0; i < 18; i++) {
            double ang = Ease.hash(i * 29 + 3) * Math.PI * 2.0;
            double up = 0.35 + 0.6 * Ease.hash(i * 31 + 5);
            double sp = 10.0 + 14.0 * Ease.hash(i * 37 + 7);
            Vec3d dir = new Vec3d(Math.cos(ang) * (1.0 - up * 0.5), up, Math.sin(ang) * (1.0 - up * 0.5)).normalize();
            double life = 0.3 + 0.3 * Ease.hash(i * 41 + 9);
            if (!((double)after > life)) {
               double a = (double)after;
               double head = sp * a * (1.0 - a * 0.6);
               double tail = Math.max(0.0, head - 2.2 - 3.0 * Ease.hash(i * 43 + 1));
               Vec3d o = s.add(0.0, 0.4, 0.0);
               Vec3d p = o.add(dir.multiply(head)).add(0.0, -2.0 * a * a, 0.0);
               Vec3d q = o.add(dir.multiply(tail)).add(0.0, -2.0 * Math.max(0.0, a - 0.06) * Math.max(0.0, a - 0.06), 0.0);
               float k = (float)(1.0 - a / life);
               m.softLine(q, p, 0.09, 16751168, 0.25F * k);
               m.line(q, p, 0.025, 16773312, 0.55F * k);
            }
         }
      }
   }

   static void edges(Fx c, EffectMesh m, float t, FallingSword.Blade bl) {
      float glow = bl.heat() * (1.0F - bl.dark());
      if (glow > 0.02F) {
         double z = 0.375;
         double[] ys = GStage.SLICES;

         for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i + 1 < ys.length; i++) {
               float a = Math.min(bl.shown(ys[i]), bl.shown(ys[i + 1])) * glow;
               if (!(a <= 0.01F)) {
                  Vec3d p = bl.at((double)s * half(ys[i]), ys[i], z);
                  Vec3d q = bl.at((double)s * half(ys[i + 1]), ys[i + 1], z);
                  m.softLine(p, q, 0.35, 16761962, 0.1F * a);
                  m.line(p, q, 0.03, 16771512, 0.12F * a);
               }
            }
         }
      }

      if (t >= 2.7833333F && t < 3.1999998F) {
         float w = Ease.smooth(t, 2.7833333F, 2.8433332F) * (1.0F - Ease.smooth(t, 3.1F, 3.1999998F));

         for (int ix = 0; ix < 10; ix++) {
            int side = ix % 2 == 0 ? 1 : -1;
            double y0 = 0.6 + (double)(ix / 2) * 0.9;
            double off = 0.3 + 0.5 * Ease.hash(ix * 7 + 3);
            double len = 5.0 + 4.0 * Ease.hash(ix * 7 + 5);
            Vec3d prev = null;

            for (int k = 0; k <= 8; k++) {
               double y = y0 + len * (double)k / 8.0;
               double bend = off * (1.0 + 0.6 * Math.sin((double)k / 8.0 * Math.PI));
               Vec3d p = bl.at((double)side * (half(y) + bend), y, (double)(ix % 3 - 1) * 0.75 * 0.8);
               if (prev != null) {
                  float a = (float)Math.sin((double)k / 8.0 * Math.PI);
                  m.softLine(prev, p, 0.12, 15398143, 0.12F * w * a);
                  m.line(prev, p, 0.015, 16777215, 0.1F * w * a);
               }

               prev = p;
            }
         }
      }
   }

   /** The golds and the blue of the light at her fingertips (the videos'). */
   static final int HAZE = 16757836, AMBER = 16750144;

   /**
    * The light at her fingertips (2.87.0, as the videos' and much stronger than the preview's): a star and a wide warm
    * haze that fills the frame beside her hand as it gathers (0.2 to 0.45 s); the gold trail her fingertips leave as the
    * hand whips round under the arm (0.33 to 0.55 s, in place of the preview's beam straight up); a long thin light run
    * out along the arm under it (0.45 to 0.7 s); the stroke her two fingers draw (0.83 s on) running on past them as a
    * blade of light; blue shards breaking past close to the camera on the frame's left (her hair's crystals, the videos'
    * 0.5 and 1 s). Every part is a continuous function of the cast's clock.
    */
   static void fingertip(Fx c, EffectMesh m, float t, Vec3d feet, float yaw) {
      if (t < 0.15F || t > 1.5F) {
         return;
      }
      Vec3d tip = GGesture.tip(c.cast, t);
      float on = Ease.window(t, 0.17F, 1.42F, 0.06F, 0.12F);
      float flick = 0.85F + 0.15F * (float)Math.sin((double)(t * 31.0F));
      float grow = 0.45F * (0.15F + 0.85F * Ease.smooth(t, 0.33F, 0.6F)) * (1.0F - 0.65F * Ease.smooth(t, 0.978F, 1.038F));
      m.softDisc(tip, 0.3 * (double)grow, GOLD, 0.45F * on);
      m.softDisc(tip, 0.12 * (double)grow, GOLD_WHITE, 0.6F * on);
      m.softDisc(tip, 0.045 * (double)grow, WHITE, 0.9F * on);
      m.star(tip, (0.13 + 0.04 * (double)flick) * (double)grow * (double)on, GOLD_WHITE, 0.95F * on);
      Vec3d view = tip.subtract(c.camera);
      view = view.lengthSquared() < 1.0E-6 ? c.f : view.normalize();
      Vec3d across = view.crossProduct(UP);
      across = across.lengthSquared() < 1.0E-6 ? c.right : across.normalize();
      Vec3d upward = across.crossProduct(view).normalize();
      m.softLine(tip.subtract(across.multiply(0.9 * (double)grow)), tip.add(across.multiply(0.9 * (double)grow)), 0.025, GOLD, 0.3F * on * grow);
      // the warm haze filling the frame beside her hand as the light gathers (the videos' 0.27 to 0.42 s)
      float haze = Ease.window(t, 0.2F, 0.5F, 0.08F, 0.1F);
      if (haze > 0.01F) {
         m.softDisc(tip, 0.42, HAZE, 0.34F * haze);
         m.softDisc(tip, 0.2, GOLD_WHITE, 0.3F * haze);
         m.softLine(tip.subtract(across.multiply(0.55)), tip.add(across.multiply(0.55)), 0.06, GOLD_WHITE, 0.5F * haze);
      }
      // the trail of gold her fingertips leave as the hand whips round (the preview drew a beam straight up instead)
      float whip = Ease.window(t, 0.33F, 0.56F, 0.03F, 0.08F);
      if (whip > 0.01F) {
         Vec3d prev = null;
         int n = 16;
         for (int i = 0; i <= n; i++) {
            float back = (float)i / (float)n;
            float at = Math.max(0.3F, t - 0.13F * back);
            Vec3d p = GGesture.tip(c.cast, at);
            if (prev != null) {
               float k = (1.0F - back) * whip;
               m.softLine(prev, p, 0.16 * (double)(1.0F - back * 0.7F), GOLD, 0.55F * k);
               m.softLine(prev, p, 0.05 * (double)(1.0F - back * 0.7F), GOLD_WHITE, 0.75F * k);
               m.line(prev, p, 0.012 * (double)(1.0F - back * 0.6F), WHITE, 0.9F * k);
            }
            prev = p;
         }
      }
      // a long thin light run out along her arm under it, from beside the hand back past the elbow (the videos' 0.5 s)
      float streak = Ease.window(t, 0.45F, 0.72F, 0.04F, 0.12F);
      if (streak > 0.01F) {
         Vec3d hand = GGesture.wrist(c.cast, t);
         Vec3d along = tip.subtract(hand);
         along = along.lengthSquared() < 1.0E-6 ? c.f : along.normalize();
         Vec3d side = along.crossProduct(view);
         side = side.lengthSquared() < 1.0E-6 ? upward : side.normalize();
         Vec3d o = tip.add(side.multiply(0.035));
         float run = Ease.smooth(t, 0.45F, 0.56F);
         Vec3d far = o.subtract(along.multiply(0.2 + 1.6 * (double)run));
         m.softTaper(o.add(along.multiply(0.25)), far, 0.05, 0.002, GOLD, 0.6F * streak);
         m.softTaper(o.add(along.multiply(0.25)), far, 0.014, 0.001, WHITE, 0.95F * streak);
      }
      // the stroke her two fingers draw, and the blade of light it runs on into past them (the videos' 0.95 to 1.05 s)
      float stroke = 1.0F - Ease.smooth(t, 1.02F, 1.3F);
      if (t > 0.83F && stroke > 0.01F) {
         int steps = 14;
         Vec3d prev = null;
         float upTo = Math.min(t, 0.97F);
         for (int i = 0; i <= steps; i++) {
            float at = 0.83F + (upTo - 0.83F) * (float)i / (float)steps;
            Vec3d p = GGesture.tip(c.cast, at);
            if (prev != null) {
               float k = (float)i / (float)steps;
               m.softLine(prev, p, 0.14 + 0.1 * (double)k, GOLD, 0.5F * stroke);
               m.softLine(prev, p, 0.05 + 0.03 * (double)k, GOLD_WHITE, 0.6F * stroke);
               m.line(prev, p, 0.014 + 0.008 * (double)k, WHITE, 0.85F * stroke);
            }
            prev = p;
         }
         float blade = Ease.window(t, 0.9F, 1.12F, 0.04F, 0.1F);
         if (blade > 0.01F) {
            Vec3d from = GGesture.tip(c.cast, Math.min(t, 0.97F) - 0.06F);
            Vec3d to = GGesture.tip(c.cast, Math.min(t, 0.97F));
            Vec3d way = to.subtract(from);
            if (way.lengthSquared() > 1.0E-6) {
               way = way.normalize();
               float reach = Ease.smooth(t, 0.9F, 1.0F);
               Vec3d end = to.add(way.multiply(0.3 + 1.5 * (double)reach)).add(upward.multiply(0.12 * (double)reach));
               m.softTaper(to, end, 0.22, 0.01, GOLD, 0.5F * blade);
               m.softTaper(to, end, 0.07, 0.003, GOLD_WHITE, 0.8F * blade);
               m.softTaper(to, end, 0.02, 0.001, WHITE, 0.95F * blade);
            }
         }
      }
      // the flash of the seal as the two fingers come up before her face: a long level line across, a short upright one
      float seal = FxLib.kick(t, 1.02F, 0.12F);
      if (seal > 0.01F) {
         Vec3d cross = across.crossProduct(view).normalize();
         m.softLine(tip.subtract(across.multiply(3.6)), tip.add(across.multiply(3.6)), 0.03, GOLD, 0.55F * seal);
         m.line(tip.subtract(across.multiply(3.0)), tip.add(across.multiply(3.0)), 0.006, WHITE, 0.9F * seal);
         m.softLine(tip.subtract(cross.multiply(1.0)), tip.add(cross.multiply(1.0)), 0.024, GOLD, 0.45F * seal);
         m.line(tip.subtract(cross.multiply(0.75)), tip.add(cross.multiply(0.75)), 0.005, WHITE, 0.8F * seal);
         m.star(tip, 0.09 * (double)seal, WHITE, 0.8F * seal);
         m.softDisc(tip, 0.09, GOLD_WHITE, 0.4F * seal);
         m.softDisc(tip, 0.26, GOLD, 0.16F * seal);
      }
      // blue shards round her hand, and big ones breaking past close to the camera on the frame's left
      float shards = Ease.window(t, 0.38F, 1.06F, 0.08F, 0.1F);
      if (shards > 0.01F) {
         Vec3d hand = GGesture.wrist(c.cast, t);
         for (int i = 0; i < 6; i++) {
            double turn = (double)t * (2.2 + (double)i * 0.3) + (double)i * 1.05;
            double r = 0.42 + 0.16 * Ease.hash(i * 13 + 1);
            double lift = -0.1 + 0.36 * Ease.hash(i * 17 + 2);
            Vec3d p = hand.add(Math.cos(turn) * r, lift + 0.06 * Math.sin((double)(t * 5.0F + (float)i)), Math.sin(turn) * r);
            Vec3d a = new Vec3d(Math.cos(turn + 1.3), 0.4, Math.sin(turn + 1.3)).normalize().multiply(0.13);
            Vec3d b = new Vec3d(-Math.sin(turn), 0.2, Math.cos(turn)).normalize().multiply(0.05);
            m.triangle(p.add(a), p.subtract(a).add(b), p.subtract(a).subtract(b), SHARD, 0.85F * shards);
         }
         shardsByCamera(c, m, t, view, across, upward);
      }
   }

   /**
    * Big blue shards breaking past between the camera and her hand on the frame's left (drawn on its right: the picture
    * is turned back across, GMirror), as the videos' at 0.5 s and at 1 s. Each a long sliver with a lighter edge.
    */
   private static void shardsByCamera(Fx c, EffectMesh m, float t, Vec3d view, Vec3d across, Vec3d upward) {
      if (!shown(c)) {
         return;
      }
      for (int i = 0; i < 5; i++) {
         float born = i < 3 ? 0.44F + 0.03F * (float)i : 0.9F + 0.035F * (float)(i - 3);
         float life = 0.2F + 0.05F * (float)(i % 2);
         float a = (t - born) / life;
         if (a <= 0.0F || a >= 1.0F) {
            continue;
         }
         float fade = Ease.smooth(a, 0.0F, 0.15F) * (1.0F - Ease.smooth(a, 0.6F, 1.0F));
         double depth = 0.34 + 0.12 * Ease.hash(i * 7 + 3);
         double side = (mirrored(t) ? 1.0 : -1.0) * (0.19 + 0.07 * Ease.hash(i * 7 + 5));
         Vec3d o = c.camera.add(view.multiply(depth)).add(across.multiply(side)).add(upward.multiply(0.12 * (Ease.hash(i * 7 + 9) - 0.3) + 0.12 * (double)a));
         Vec3d drift = across.multiply((mirrored(t) ? 1.0 : -1.0) * 0.06 * (double)a).add(upward.multiply(0.04 * (double)a));
         double turn = 0.6 + 1.8 * Ease.hash(i * 7 + 11) + 1.2 * (double)a;
         Vec3d len = across.multiply(Math.cos(turn)).add(upward.multiply(Math.sin(turn))).multiply(0.07 + 0.04 * Ease.hash(i * 7 + 13));
         Vec3d wid = across.multiply(-Math.sin(turn)).add(upward.multiply(Math.cos(turn))).multiply(0.018);
         Vec3d p = o.add(drift);
         m.quad(p.add(len), p.add(wid), p.subtract(len), p.subtract(wid).multiply(1.0), SHARD, 0.9F * fade);
         m.line(p.add(len), p.add(wid), 0.003, 9425919, 0.8F * fade);
      }
   }

   /**
    * Behind and round her face (2.87.0, the videos' 1.1 to 1.5 s): thin upright lines of gold falling either side of her
    * head, a soft glow behind it, and motes of light drifting up past her, all fading as the shot pulls away.
    */
   static void faceLight(Fx c, EffectMesh m, float t) {
      float on = Ease.window(t, 1.06F, 1.58F, 0.06F, 0.1F);
      if (on <= 0.01F) {
         return;
      }
      Vec3d head = head(c.cast.origin(), c.cast.yaw(), t);
      Vec3d view = head.subtract(c.camera);
      view = view.lengthSquared() < 1.0E-6 ? c.f : view.normalize();
      Vec3d across = view.crossProduct(UP);
      across = across.lengthSquared() < 1.0E-6 ? c.right : across.normalize();
      Vec3d back = head.add(view.multiply(0.6));
      m.softDisc(back.add(0.0, 0.05, 0.0), 0.9, HAZE, 0.12F * on);
      for (int i = 0; i < 7; i++) {
         double side = (i % 2 == 0 ? -1.0 : 1.0) * (0.55 + 0.38 * Ease.hash(i * 11 + 1));
         double fall = ((double)t * (0.7 + 0.5 * Ease.hash(i * 11 + 3)) + Ease.hash(i * 11 + 5)) % 1.0;
         double len = 0.5 + 0.6 * Ease.hash(i * 11 + 7);
         Vec3d top = back.add(across.multiply(side)).add(0.0, 0.9 - 1.6 * fall, 0.0);
         Vec3d bottom = top.add(0.0, -len, 0.0);
         float k = on * (float)Math.sin(Math.PI * fall) * (0.6F + 0.4F * (float)Math.sin((double)(t * 19.0F + (float)i)));
         m.softLine(top, bottom, 0.02, GOLD, 0.45F * k);
         m.line(top, bottom, 0.004, GOLD_WHITE, 0.85F * k);
      }
      for (int i = 0; i < 16; i++) {
         double u = ((double)t * (0.25 + 0.2 * Ease.hash(i * 19 + 1)) + Ease.hash(i * 19 + 3)) % 1.0;
         Vec3d p = head.add(across.multiply(-0.7 + 1.4 * Ease.hash(i * 19 + 5))).add(view.multiply(-0.15 + 0.5 * Ease.hash(i * 19 + 7))).add(0.0, -0.45 + 0.9 * u, 0.0);
         float k = on * (float)Math.sin(Math.PI * u);
         m.softDisc(p, 0.016 + 0.012 * Ease.hash(i * 19 + 9), GOLD_WHITE, 0.6F * k);
      }
   }

   static void sky(Fx c, EffectMesh m, float t) {
      if (!(t < 1.51F) && !(t >= 2.7833333F)) {
         FallingSword.Frame k = GScene.skyFrame(c.cast, t);

         for (int i = 0; i < 34; i++) {
            double phase = ((double)t * 1.1 + Ease.hash(i * 17 + 3)) % 1.0;
            double angle = Ease.hash(i * 29 + 5) * Math.PI * 2.0;
            double depth = 3.4 + 4.0 * Ease.hash(i * 31 + 7);
            double radius = 2.4 + phase * 7.0;
            double length = 3.0 + 6.0 * Ease.hash(i * 37 + 9);
            Vec3d center = k.at(0.53, 0.34, depth, 59.82126740260396);
            Vec3d direction = k.r().multiply(Math.cos(angle)).add(k.u().multiply(Math.sin(angle)));
            Vec3d a = center.add(direction.multiply(radius));
            Vec3d b = center.add(direction.multiply(radius + length));
            float alpha = (float)(Math.pow(Math.sin(phase * Math.PI), 2.0) * (0.02 + 0.06 * Math.abs(Math.cos(angle))));
            m.softTaper(a, b, 0.002, 0.045, 14018559, alpha);
         }
      }
   }

   static void alpha(Fx c, EffectMesh m) {
      GGrand.alpha(c, m);
   }

   static void overlay(Fx c, EffectMesh m) {
   }

   static void frame(Fx var0, FxFrame var1) {
      float var4 = var0.sec;
      Vec3d var5 = feet(var0);
      float var6 = var0.cast.yaw();
      boolean var7 = !Float.isNaN(off(var0.cast));
      float var8 = Ease.window(var4, 0.17F, 1.42F, 0.06F, 0.12F);
      if (var8 > 0.01F && !var7) {
         var1.light(GGesture.tip(var0.cast, var4), 16765066, 3.0, 0.6F * var8);
      }

      if (!var7 && (shown(var0) || GWorld.active(var0.cast, var4))) {
         FallingSword.Blade var11 = shown(var0) ? blade(var0.cast, var4) : GWorld.blade(var0.cast, var4);
         float var10;
         if (var11 != null && (var10 = var11.heat() * var11.alpha() * (1.0F - var11.dark())) > 0.02F && var4 >= 2.7833333F) {
            var1.light(var11.at(0.0, 2.0, 0.0), 16756832, 16.0, 0.8F * var10);
         }

         float var9;
         if ((var9 = Ease.window(var4, 3.1F, 4.78F, 0.06F, 0.18F)) > 0.01F) {
            Vec3d var13 = strike(var0.cast).add(0.0, 0.65, 0.0);
            var1.light(var13, 16760688, 12.0, 1.1F * var9);
            var1.shaft(var13, 16766106, 0.22F * var9);
            var1.afterglow(0.12F);
         }
      }

      float var3;
      if ((var3 = FxLib.kick(var4, 3.1F, 0.5F)) > 0.01F && !var7) {
         var1.light(strike(var0.cast).add(0.0, 2.0, 0.0), 16752720, 20.0, 1.3F * var3);
      }

      float var2;
      if ((var2 = FxLib.kick(var4, 5.15F, 0.6F)) > 0.01F && !var7) {
         var1.light(var5.add(0.0, 1.5, 0.0), 16751168, 16.0, 1.0F * var2);
      }

      if (var4 > 0.28F && var4 < 1.1F || var4 > 2.7833333F && var4 < 3.3999999F) {
         var1.afterglow(0.08F);
      }

      GGrand.frame(var0, var1);
   }

   static float shake(StandClient.Cast cast, float sec) {
      return !Float.isNaN(off(cast)) && off(cast) < 3.1F
         ? 0.0F
         : Math.max(2.6F * IsosangSlash.bump(sec, 3.1F, 0.25F), 1.2F * IsosangSlash.bump(sec, 5.15F, 0.2F));
   }

   static StandClient.Cast cinematic() {
      return GArenaClient.cinematic();
   }

   static boolean wholeBody(float t) {
      return t >= 1.008F;
   }

   public static double frameHeight() {
      Window w = MinecraftClient.getInstance().getWindow();
      return w == null ? 1.0 : Math.min(1.0, (double)w.getFramebufferWidth() / (double)Math.max(1, w.getFramebufferHeight()) / 2.1670428893905194);
   }

   static double windowFov(double fov) {
      return Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(fov / 2.0)) / frameHeight()));
   }

   static FallingSword.Frame skyFrame(StandClient.Cast c) {
      Vec3d feet = c.origin();
      Vec3d f = forward(c);
      Vec3d r = right(f);
      Vec3d eye = feet.add(f.multiply(1.114)).subtract(r.multiply(-0.023)).add(0.0, -0.326, 0.0);
      double pan = Math.toRadians(41.92);
      double pitch = Math.toRadians(60.0);
      Vec3d level = f.negate().multiply(Math.cos(pan)).add(r.negate().multiply(Math.sin(pan))).normalize();
      Vec3d look = level.multiply(Math.cos(pitch)).add(UP.multiply(Math.sin(pitch))).normalize();
      Vec3d cr = look.crossProduct(UP).normalize();
      Vec3d cu = cr.crossProduct(look).normalize();
      return new FallingSword.Frame(eye, look, cr, cu);
   }

   static FallingSword.Frame skyView(StandClient.Cast c) {
      return skyFrame(c).rolled(27.61);
   }

   static double nearing(float t) {
      return NEARING[0] + (double)t * (NEARING[1] + (double)t * NEARING[2]);
   }

   static Vec3d ring(StandClient.Cast c) {
      FallingSword.Frame k = skyView(c);
      return k.at(0.5519, 0.4486, 1.0, 84.42).subtract(k.eye()).normalize();
   }

   static Vec3d farSide(StandClient.Cast c) {
      FallingSword.Frame k = skyView(c);
      Vec3d e = ring(c);
      Vec3d p = k.at(0.5218999999999999, 0.3836, 1.0, 84.42).subtract(k.eye()).normalize();
      return p.subtract(e.multiply(p.dotProduct(e))).normalize();
   }

   static double landingHeight(float t) {
      if ((double)t <= LANDING_HEIGHT[0][0]) {
         return LANDING_HEIGHT[0][1];
      } else {
         for (int i = 0; i + 1 < LANDING_HEIGHT.length; i++) {
            if ((double)t < LANDING_HEIGHT[i + 1][0]) {
               double[] a = LANDING_HEIGHT[i];
               double[] b = LANDING_HEIGHT[i + 1];
               double u = (double)Ease.smooth((float)(((double)t - a[0]) / (b[0] - a[0])), 0.0F, 1.0F);
               return a[1] + (b[1] - a[1]) * u;
            }
         }

         return LANDING_HEIGHT[LANDING_HEIGHT.length - 1][1];
      }
   }

   static FallingSword.Shot shot(StandClient.Cast c, float t) {
      Vec3d feet = c.origin();
      float yaw = c.yaw();
      Vec3d f = forward(c);
      Vec3d r = right(f);
      if (t < 1.008F) {
         Vec3d h = hand(feet, yaw, t);
         Vec3d tip = tip(feet, yaw, t);
         Vec3d d = tip.subtract(h);
         d = d.lengthSquared() < 1.0E-6 ? f : d.normalize();
         Vec3d across = d.crossProduct(UP);
         across = across.lengthSquared() < 1.0E-6 ? r : across.normalize();
         Vec3d side = across.multiply(-0.72).add(0.0, 0.14, 0.0);
         Vec3d under = under(f, r);
         if (t < 0.3F) {
            Vec3d h0 = hand(feet, yaw, 0.16F);
            Vec3d d0 = tip(feet, yaw, 0.16F).subtract(h0).normalize();
            Vec3d a0 = d0.crossProduct(UP).normalize();
            return GGesture.handShot(
               new FallingSword.Shot(
                  h0.add(a0.multiply(-0.7)).add(0.0, 0.16, 0.0).subtract(d0.multiply(0.18 + 0.04 * (double)t / 0.3F)),
                  h0.subtract(d0.multiply(0.16)).add(0.0, HAND_DROP, 0.0),
                  HAND_FOV,
                  0.0F
               ),
               c,
               t
            );
         } else if (t < 0.45F) {
            float u = Ease.smooth(t, 0.3F, 0.45F);
            Vec3d h0 = hand(feet, yaw, 0.16F);
            Vec3d d0 = tip(feet, yaw, 0.16F).subtract(h0).normalize();
            Vec3d a0 = d0.crossProduct(UP).normalize();
            Vec3d h1 = hand(feet, yaw, 0.45F);
            Vec3d d1 = tip(feet, yaw, 0.45F).subtract(h1).normalize();
            Vec3d a1 = d1.crossProduct(UP).normalize();
            Vec3d from = a0.multiply(-0.7).add(0.0, 0.16, 0.0).subtract(d0.multiply(0.22));
            Vec3d to = under(f, r);
            return GGesture.handShot(
               new FallingSword.Shot(
                  h.add(slerp(from, to, (double)u)),
                  h.subtract(d0.multiply(0.16)).add(0.0, HAND_DROP * (1.0F - u), 0.0).lerp(h.subtract(d1.multiply(UNDER_LOOK)), (double)u),
                  HAND_FOV + (UNDER_FOV - HAND_FOV) * u,
                  -30.0F * u
               ),
               c,
               t
            );
         } else if (t < 0.83F) {
            float u = (t - 0.45F) / 0.38F;
            return GGesture.handShot(
               new FallingSword.Shot(h.add(under.multiply(1.0 - 0.12 * (double)u)), h.subtract(d.multiply(UNDER_LOOK)), UNDER_FOV, -30.0F + 8.0F * u), c, t
            );
         } else {
            float u = Ease.smooth(t, 0.83F, 1.008F);
            Vec3d front = f.multiply(0.72).subtract(r.multiply(0.2)).add(0.0, -0.24, 0.0);
            return GGesture.handShot(
               new FallingSword.Shot(
                  h.add(slerp(under.multiply(0.88), front, (double)u)), h.subtract(d.multiply(UNDER_LOOK)).lerp(tip, (double)u), UNDER_FOV + (FACE_FOV - UNDER_FOV) * u, -22.0F * (1.0F - u)
               ),
               c,
               t
            );
         }
      } else if (t < 2.7833333F) {
         FallingSword.Shot sky = GScene.skyShot(c, t);
         if (t >= 1.65F) {
            return GGesture.handShot(sky, c, t);
         } else {
            FallingSword.Shot face = face(c, Math.min(t, 1.6F));
            if (t < 1.6F) {
               return GGesture.handShot(face, c, t);
            } else {
               float u = Ease.smooth(t, 1.6F, 1.65F);
               return GGesture.handShot(
                  new FallingSword.Shot(
                     face.eye().lerp(sky.eye(), (double)u),
                     face.look().lerp(sky.look(), (double)u),
                     face.fov() + (sky.fov() - face.fov()) * (double)u,
                     face.roll() + (sky.roll() - face.roll()) * u
                  ),
                  c,
                  t
               );
            }
         }
      } else {
         Vec3d s = strike(c);
         float u = Ease.smooth(t, 3.1F, 4.83F);
         double follow = Math.max(0.0, fallHeight(t));
         double lean = Math.toRadians(16.96);
         Vec3d shift = UP.multiply(Math.cos(lean) * follow).subtract(r.multiply(Math.sin(lean) * follow));
         Vec3d eye = s.subtract(f.multiply(7.19 - 0.18 * (double)u)).add(r.multiply(-0.82)).add(0.0, landingHeight(t), 0.0).add(shift);
         double j = jolt(t);
         eye = eye.add(r.multiply(j * 0.6 * Math.cos((double)(t * 53.0F)))).add(0.0, j * Math.sin((double)(t * 61.0F)), 0.0);
         double kick = (double)IsosangSlash.bump(t, 3.1F, 0.2F);
         return GGesture.handShot(
            new FallingSword.Shot(eye, eye.add(f).add(0.0, 0.06787204450625875, 0.0), 58.0 - 1.2 * kick, (float)(0.45 * kick * Math.sin((double)(t * 47.0F)))),
            c,
            t
         );
      }
   }

   /**
    * A G begun (IsosangClient): the sword's bite into the ground as it strikes (3.1 s: the dust and smoke round its foot,
    * its embers and its glow, MeteorFx's landing the sword's way, over G's own strike, GImpact), and the fire leaping up
    * round her and the dust over them at FIRE (5.05 s, tall by G_ERUPT), as the videos' after the cutscene (tongues of
    * fire on fixed planes, dust, stones, embers). Everyone sees both.
    */
   static void begin(StandClient.Cast c) {
      dev.sangwoo.celestialfall.client.meteor.MeteorFx.strike(c.id(), strike(c), c.origin(), c.id().hashCode(), c.age(0.0F) - IsosangRules.G_IMPACT, (FIRE - IsosangRules.G_STRIKE) * 20.0F);
   }

   /** G called off before it strikes: no fire after it. */
   static void calledOff(java.util.UUID id) {
      dev.sangwoo.celestialfall.client.meteor.MeteorFx.unblast(id);
   }

   /**
    * The shots of her hand framed as tight as the videos' (2.87.0, measured against them: the hand about twice as large
    * across as the preview's): beside the hand (HAND_FOV, the view dropped HAND_DROP so the hand sits at the frame's
    * middle), and under the arm (UNDER_FOV, looking UNDER_LOOK back down the arm so the hand rides up to the upper
    * right and the fingertips near the corner). Vertical fields of view (degrees) of the cutscene's frame.
    */
   static final double HAND_FOV = 28.0, UNDER_FOV = 31.0, UNDER_LOOK = 0.27, HAND_DROP = -0.03;

   /** Whether the shot at {@code t} is one of her hand's, flipped back left for right (GMirror). */
   static boolean mirrored(float t) {
      return t >= 0.0F && t < 1.008F;
   }

   static boolean alone(float t) {
      return t >= 2.75F && t < 2.7833333F;
   }

   static double[] faceAt(float t) {
      double[][] k = FACE_AT;
      if ((double)t <= k[0][0]) {
         return new double[]{k[0][1], k[0][2]};
      } else {
         for (int i = 0; i + 1 < k.length; i++) {
            if ((double)t < k[i + 1][0]) {
               double u = (double)Ease.smooth((float)(((double)t - k[i][0]) / (k[i + 1][0] - k[i][0])), 0.0F, 1.0F);
               return new double[]{k[i][1] + (k[i + 1][1] - k[i][1]) * u, k[i][2] + (k[i + 1][2] - k[i][2]) * u};
            }
         }

         double[] e = k[k.length - 1];
         return new double[]{e[1], e[2]};
      }
   }

   static FallingSword.Shot face(StandClient.Cast c, float t) {
      Vec3d feet = c.origin();
      float yaw = c.yaw();
      Vec3d f = forward(c);
      Vec3d r = right(f);
      Vec3d head = head(feet, yaw, t);
      double wide = 1.8518518518518516;
      double high = wide / 2.1670428893905194;
      double back = high / 2.0 / Math.tan(Math.toRadians(24.0));
      Vec3d eyes = head.add(f.multiply(0.25)).add(0.0, -0.031, 0.0);
      Vec3d look = f.negate();
      Vec3d cr = look.crossProduct(UP).normalize();
      double[] at = faceAt(t);
      Vec3d eye = eyes.add(f.multiply(back)).subtract(cr.multiply((at[0] - 0.5) * wide)).add(0.0, (at[1] - 0.5) * high, 0.0);
      return new FallingSword.Shot(eye, eye.add(look), 48.0, 0.0F);
   }

   static Vec3d under(Vec3d f, Vec3d r) {
      return f.multiply(0.78).add(r.multiply(0.12)).add(0.0, -0.42, 0.0);
   }

   static Vec3d slerp(Vec3d a, Vec3d b, double u) {
      double la = a.length();
      double lb = b.length();
      Vec3d na = a.multiply(1.0 / la);
      Vec3d nb = b.multiply(1.0 / lb);
      double dot = MathHelper.clamp(na.dotProduct(nb), -1.0, 1.0);
      double ang = Math.acos(dot);
      Vec3d d = ang < 1.0E-4 ? na.lerp(nb, u) : na.multiply(Math.sin((1.0 - u) * ang) / Math.sin(ang)).add(nb.multiply(Math.sin(u * ang) / Math.sin(ang)));
      return d.normalize().multiply(la + (lb - la) * u);
   }

   static double jolt(float t) {
      return 0.055 * (double)IsosangSlash.bump(t, 3.1F, 0.22F) + 0.008 * (double)Ease.window(t, 3.1999998F, 5.5F, 0.15F, 0.3F);
   }

   public static CinematicDirector.Pose camera(Vec3d vanilla, float yaw, float pitch, float delta) {
      StandClient.Cast c = cinematic();
      if (c == null) {
         return null;
      } else {
         float t = c.age(delta) / 20.0F;
         FallingSword.Shot s = shot(c, t);
         MinecraftClient mc = MinecraftClient.getInstance();
         Vec3d eye = GStage.supported() ? s.eye() : clear(mc, s.look(), s.eye());
         Vec3d d = s.look().subtract(eye);
         if (d.lengthSquared() < 1.0E-6) {
            d = forward(c);
         }

         d = d.normalize();
         float sy = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
         float sp = (float)(-Math.toDegrees(Math.asin(MathHelper.clamp(d.y, -1.0, 1.0))));
         return new CinematicDirector.Pose(eye, sy, sp, s.roll());
      }
   }

   public static double fov(double vanilla, float delta) {
      StandClient.Cast c = cinematic();
      if (c == null) {
         return vanilla;
      } else {
         float t = c.age(delta) / 20.0F;
         return windowFov(shot(c, t).fov());
      }
   }

   public static float roll(float delta) {
      StandClient.Cast c = cinematic();
      if (c == null) {
         return 0.0F;
      } else {
         float t = c.age(delta) / 20.0F;
         return shot(c, t).roll();
      }
   }

   static Vec3d clear(MinecraftClient mc, Vec3d look, Vec3d eye) {
      if (mc.world != null && mc.player != null) {
         BlockHitResult hit = mc.world.raycast(new RaycastContext(look, eye, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
         if (hit.getType() != Type.MISS && !hit.isInsideBlock() && !(hit.getPos().squaredDistanceTo(look) < 0.04)) {
            Vec3d d = eye.subtract(look);
            double len = d.length();
            return len < 1.0E-4 ? eye : look.add(d.multiply(Math.max(0.3, look.distanceTo(hit.getPos()) - 0.3) / len));
         } else {
            return eye;
         }
      } else {
         return eye;
      }
   }

   public static float dust(float delta) {
      return 0.0F;
   }

   public static float dim(float delta) {
      StandClient.Cast c = cinematic();
      if (c == null) {
         return 0.0F;
      } else {
         float t = c.age(delta) / 20.0F;
         return 0.62F * Ease.smooth(t, 4.3F, 4.83F);
      }
   }

   /** When the screen is black after the cut (seconds): held from it, then lifting as the fire leaps up (the videos'). */
   static final float AFTER_BLACK = 4.97F, AFTER_LIT = 5.1F;
   /** When the fire leaps up round her (seconds): a moment before G_ERUPT so it stands tall by then (the videos' 5.1 s). */
   static final float FIRE = IsosangRules.G_ERUPT - 0.1F;

   /**
    * How dark the world is for the local camera (0 to 1): the black round her hand (GBlackout), and, as the videos', the
    * moment of black after the cutscene cuts back to the game (4.83 s) before the fire leaps up, for those who watched it.
    */
   public static float dark(float delta) {
      return Math.max(GBlackout.alpha(delta), afterBlack(delta));
   }

   /**
    * The black over everything after the cut (0 to 1), for those who watched the cutscene: the GUI's black (drawn before
    * the HUD, IsosangCinema.afterBlack, so the HUD shows over it as the videos') and the world's fog (dark). The burst's
    * light would otherwise flash through at once.
    */
   public static float afterBlack(float delta) {
      StandClient.Cast c = GArenaClient.watched(delta, AFTER_LIT + 0.05F);
      if (c == null) {
         return 0.0F;
      }
      float t = c.age(delta) / 20.0F;
      return t >= 4.83F ? 0.94F * (1.0F - Ease.smooth(t, AFTER_BLACK, AFTER_LIT)) : 0.0F;
   }

   static record Blade(Vec3d point, Vec3d axis, Vec3d across, Vec3d normal, float alpha, float heat, float dark, double fadeFrom, double fadeTo) {
      Vec3d acrossNormal() {
         return this.axis.crossProduct(this.normal).multiply(1.0 / this.across.dotProduct(this.axis.crossProduct(this.normal)));
      }

      Vec3d axisNormal() {
         return this.normal.crossProduct(this.across).multiply(1.0 / this.axis.dotProduct(this.normal.crossProduct(this.across)));
      }

      Vec3d normalAt(double x, double y, double z) {
         return this.acrossNormal().multiply(x).add(this.axisNormal().multiply(y)).add(this.normal.multiply(z)).normalize();
      }

      Vec3d at(double x, double y, double z) {
         return this.point.add(this.across().multiply(x)).add(this.axis.multiply(y)).add(this.normal.multiply(z));
      }

      float shown(double y) {
         return this.alpha * (1.0F - Ease.smooth((float)y, (float)this.fadeFrom, (float)this.fadeTo));
      }
   }

   static record Frame(Vec3d eye, Vec3d f, Vec3d r, Vec3d u) {
      FallingSword.Frame rolled(double roll) {
         double a = Math.toRadians(-roll);
         return new FallingSword.Frame(
            this.eye, this.f, this.r.multiply(Math.cos(a)).add(this.u.multiply(Math.sin(a))), this.r.multiply(-Math.sin(a)).add(this.u.multiply(Math.cos(a)))
         );
      }

      Vec3d at(double sx, double sy, double depth, double fov) {
         double t = Math.tan(Math.toRadians(fov / 2.0));
         return this.eye.add(this.f.add(this.r.multiply((2.0 * sx - 1.0) * t * 2.1670428893905194)).add(this.u.multiply((1.0 - 2.0 * sy) * t)).multiply(depth));
      }

      Vec3d vector(Vec3d v) {
         return this.r.multiply(v.x).add(this.u.multiply(v.y)).add(this.f.multiply(v.z));
      }

      Vec3d way(double elevation, double azimuth) {
         double e = Math.toRadians(elevation);
         double z = Math.toRadians(azimuth);
         return this.f.multiply(Math.cos(e) * Math.cos(z)).add(this.r.multiply(Math.cos(e) * Math.sin(z))).add(this.u.multiply(Math.sin(e))).normalize();
      }
   }

   static record Shot(Vec3d eye, Vec3d look, double fov, float roll) {
   }
}
