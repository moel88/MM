package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;

/**
 * Lee So-sang's G made grand (2.88.0, user request: "이제 컷씬 이펙트나 검을 웅장하게 대폭 강화해줘"), over what 2.87.0 drew:
 * <ul>
 *   <li>the sword's carved inscriptions burn with molten gold (isosang_blade.fsh's Rune, {@link #rune}): lit from its point
 *   up as it comes out of the storm, a light running up through them, brighter as it falls, flaring white as it strikes,
 *   and burning hotter and whiter the longer it stands in the ground, until the blast;</li>
 *   <li>her face: a golden light behind her head, rimming it, with two rings and a halo of thin golden rays round it;</li>
 *   <li>the sky: the storm's eye burning gold behind the sword, its light thrown out through the banks in slowly turning
 *   rays, and lightning forking through the storm, lighting its banks from inside (isosang_cloud.fsh's Grand and
 *   Lightning, {@link #storm}) and the sword; golden motes drawn spinning into the eye. The banks are drawn over the
 *   effect layer, so what of it is in the sky is drawn in front of them (some 7.5 blocks out);</li>
 *   <li>the fall: collars of vapour round the falling blade, the air streaking past the camera, and rings of light on the
 *   ground drawn in to where it will strike;</li>
 *   <li>the strike: a shock wave racing out along the ground with an ink rim and a ring of dust behind it, a pillar of light
 *   along the sword, a burst of light, an impact frame in white gold on deep amber ({@link #GOLDEN}), and the ground
 *   cracking open in glowing fissures that beat like a heart, faster and brighter, while lightning crawls over the
 *   sword and light is drawn in to its foot;</li>
 *   <li>the blast: a dome of light swelling over its ground, rings racing out along it, and a pillar of light into the sky.</li>
 * </ul>
 * Every part is a function of the cast clock alone (smooth at any frame rate). The sky and the fall are the caster's
 * cutscene's (and the arena's watchers'); the strike and the blast are in the world for everyone.
 */
final class GGrand {
   static final int GOLD = FallingSword.GOLD, GOLD_WHITE = FallingSword.GOLD_WHITE, EMBER = 0xFF8A2E, AMBER = 0xFFB04A;
   static final int BOLT = 0xA6BCFF, BOLT_CORE = 0xF4F7FF, MIST = 0xE6EEFF, INK = 0x170D07, DUST = 0x7A6650;
   static final Vec3d X = new Vec3d(1.0, 0.0, 0.0), Z = new Vec3d(0.0, 0.0, 1.0), UP = new Vec3d(0.0, 1.0, 0.0);
   /** Where the storm's eye is in the sky shot's frame (across, down; the clouds' own eyeCenter). */
   static final double EYE_X = .546, EYE_Y = .40;
   /** The lightning in the sky shot: when (seconds), from and to (frame across and down), over before the sword falls. */
   static final double[][] SKY_BOLTS = {
      {1.70, .20, .02, .30, .40},
      {1.86, .82, .00, .73, .36},
      {2.02, .05, .26, .15, .56},
      {2.17, .74, -.04, .79, .22},
      {2.31, .93, .22, .84, .60},
      {2.44, .36, -.04, .44, .18},
      {2.56, .12, .06, .32, .28}
   };
   /** The heart the fissures beat with while the sword stands in the ground (seconds), faster toward the blast. */
   static final float[] BEATS = {3.5F, 3.85F, 4.12F, 4.34F, 4.5F, 4.62F, 4.72F};
   static final int FISSURES = 13;
   /**
    * How deep the sky shot's motes start and its bolts are drawn: in front of the storm's banks, whose outer parts begin
    * some four blocks out (and none nearer than 1.2), behind the caster and the sword's point (blocks).
    */
   static final double NEAR = 2.2, BOLT_DEPTH = 2.0;
   /** The strike's impact frame: white gold on deep amber (the effect layer's frame, in place of Kageno's violet). */
   static final dev.sangwoo.celestialfall.client.vfx.FxFrame.ImpactLook GOLDEN =
      new dev.sangwoo.celestialfall.client.vfx.FxFrame.ImpactLook(0x2E1704, 0xFFF1CC, 0xFFF6E2, 0x1C0E02, 0x8C4E0A);

   private GGrand() {
   }

   // ───────────────────────────── the sword ─────────────────────────────

   /**
    * The burning inscriptions at {@code t} (isosang_blade.fsh's Rune): how strongly, how far up from the point they are lit
    * (blocks), the clock of the light running up through them (seconds, faster while it stands in the ground), and how
    * white they flare (a flash as it strikes, then whiter until the blast).
    */
   static float[] rune(float t) {
      float beat = heart(t);
      float strength = .85F * Ease.smooth(t, 1.62F, 1.82F) + .15F * Ease.smooth(t, 2.6F, 2.9F) + .55F * Ease.smooth(t, 3.25F, 4.55F) + .3F * beat;
      float front = 2.0F + 34.0F * Ease.smooth(t, 1.66F, 2.45F) + 60.0F * Ease.smooth(t, 2.6F, 2.9F);
      // the light running up them speeds up while it stands in the ground, to nearly three times as fast
      double standing = Math.max(0.0, t - IsosangRules.G_STRIKE), most = 1.7;
      float clock = (float)(t + (standing < most ? .5 * standing * standing : .5 * most * most + most * (standing - most)));
      float flare = .9F * FxLib.kick(t, IsosangRules.G_STRIKE, .2F) + .7F * Ease.smooth(t, 4.15F, 4.62F) + .35F * beat;
      return new float[]{strength, front, clock, Math.min(1.0F, flare)};
   }

   /**
    * The heart the sword and the ground beat with while it stands in the ground (0 between beats, 1 on one): each beat
    * swells over some five frames at 60 fps and dies away.
    */
   static float heart(float t) {
      float heart = 0.0F;
      for (float b : BEATS) {
         if (t > b - .04F) {
            heart = Math.max(heart, Ease.smooth(t, b - .04F, b + .04F) * (float)Math.exp(-Math.max(0.0F, t - b - .04F) / .07F));
         }
      }
      return heart;
   }

   /**
    * The storm's own light at {@code t} (isosang_cloud.fsh's Grand and Lightning, the cutscene's canopy): how strongly its
    * eye burns gold behind the sword and the clock its rays turn by; where the lightning flashing now forks in the frame
    * (across, down), how strongly it lights the banks round it and how wide.
    */
   static float[] storm(float t) {
      float eye = .95F * Ease.smooth(t, 1.62F, 1.95F) * (1.0F - Ease.smooth(t, 2.68F, 2.84F));
      float best = 0.0F;
      double[] at = SKY_BOLTS[0];
      for (double[] b : SKY_BOLTS) {
         float fl = flash(t - (float)b[0]);
         if (fl > best) {
            best = fl;
            at = b;
         }
      }
      best *= 1.0F - Ease.smooth(t, IsosangRules.G_FALL - .05F, IsosangRules.G_FALL);
      eye *= 1.0F + .35F * best;
      return new float[]{eye, t, (float)((at[1] + at[3]) * .5), (float)((at[2] + at[4]) * .5), .62F * best, .13F};
   }

   // ───────────────────────────── passes ─────────────────────────────

   static void additive(Fx c, EffectMesh m) {
      if (!Float.isNaN(FallingSword.off(c.cast))) {
         return;
      }
      float t = c.sec;
      if (FallingSword.shown(c)) {
         face(c, m, t);
         sky(c, m, t);
         fall(c, m, t);
      }
      if (FallingSword.shown(c) || !GStage.hidden()) {
         strike(c, m, t, false);
         blast(c, m, t, false);
      }
   }

   /** The ink: the dark rims of the shock waves and the fissures, and the dust behind the waves. */
   static void alpha(Fx c, EffectMesh m) {
      if (!Float.isNaN(FallingSword.off(c.cast))) {
         return;
      }
      if (FallingSword.shown(c) || !GStage.hidden()) {
         strike(c, m, c.sec, true);
         blast(c, m, c.sec, true);
      }
   }

   static void frame(Fx c, FxFrame f) {
      if (!Float.isNaN(FallingSword.off(c.cast))) {
         return;
      }
      float t = c.sec;
      Vec3d s = FallingSword.strike(c.cast);
      if (FallingSword.shown(c) && t >= 1.6F && t < IsosangRules.G_FALL) {
         FallingSword.Frame k = GScene.skyFrame(c.cast, t);
         float on = Ease.window(t, 1.6F, IsosangRules.G_FALL, .1F, .04F);
         // the motes' light drawn out into rays from the eye
         f.shaft(k.at(EYE_X, EYE_Y, NEAR + 1.3, GScene.SKY_FOV), GOLD, .22F * on);
         for (double[] b : SKY_BOLTS) {
            float fl = flash(t - (float)b[0]);
            if (fl > .02F) {
               // the bolt lights the sword: its light pulled in from the storm to near the camera
               f.light(k.at((b[1] + b[3]) * .5, (b[2] + b[4]) * .5, 9.0, GScene.SKY_FOV), BOLT, 26.0, 1.1F * fl);
            }
         }
      }
      float k = FxLib.kick(t, IsosangRules.G_STRIKE, .3F);
      if (k > .01F) {
         f.light(s.add(0.0, 6.0, 0.0), GOLD_WHITE, 34.0, 1.2F * k);
      }
      float impact = impact(t, IsosangRules.G_STRIKE);
      if (impact > 0.0F) {
         float near = FallingSword.shown(c) ? 1.0F : (float)Math.max(0.0, Math.min(1.0, (40.0 - c.camera.distanceTo(s)) / 20.0));
         if (near > 0.0F) {
            f.impact(.85F * near * impact, 0.0F, GOLDEN);
         }
      }
      float glow = ground(t);
      if (glow > .02F && t < 4.85F) {
         f.light(s.add(0.0, .6, 0.0), EMBER, 18.0, .9F * glow);
      }
      float b = FxLib.kick(t, 4.86F, .5F);
      if (b > .01F) {
         f.light(s.add(0.0, 8.0, 0.0), GOLD, 40.0, .9F * b);
         f.shaft(s.add(0.0, 10.0, 0.0), GOLD, .3F * b);
      }
   }

   // ───────────────────────────── her face ─────────────────────────────

   /** A golden light behind her head, two rings and a halo of thin golden rays, turning slowly (her face shot, 1.06 to 1.58 s). */
   static void face(Fx c, EffectMesh m, float t) {
      float on = Ease.window(t, 1.06F, 1.58F, .08F, .1F);
      if (on <= .01F) {
         return;
      }
      Vec3d head = FallingSword.head(c.cast.origin(), c.cast.yaw(), t);
      Vec3d view = head.subtract(c.camera);
      view = view.lengthSquared() < 1.0E-6 ? c.f : view.normalize();
      Vec3d across = view.crossProduct(UP);
      across = across.lengthSquared() < 1.0E-6 ? c.right : across.normalize();
      Vec3d up = across.crossProduct(view).normalize();
      Vec3d back = head.add(view.multiply(1.1));
      // a golden light behind her head (her head hides its middle: it rims her)
      m.softDisc(back, 1.25, GOLD, .2F * on);
      m.softDisc(back, .62, GOLD_WHITE, .16F * on);
      for (int i = 0; i < 14; i++) {
         double a = i * Math.PI * 2.0 / 14.0 + .35 * t + .12 * Ease.hash(i * 7 + 1);
         Vec3d d = across.multiply(Math.cos(a)).add(up.multiply(Math.sin(a)));
         double len = 1.0 + .9 * Ease.hash(i * 7 + 3);
         float k = on * (.6F + .4F * (float)Math.sin(t * 7.0F + i * 1.7F));
         m.softTaper(back.add(d.multiply(.45)), back.add(d.multiply(.45 + len)), .1, .004, GOLD, .36F * k);
      }
      m.ring(back, across, up, .42, 48, 1.0F, .014, GOLD_WHITE, .5F * on, .4 * t);
      m.ring(back, across, up, .78, 64, 1.0F, .01, GOLD, .28F * on, -.25 * t);
   }

   // ───────────────────────────── the sky ─────────────────────────────

   /**
    * The sky shot (1.6 to 2.78 s): the storm's eye burning gold behind the sword with rays thrown out round it, motes drawn
    * spinning into it, and lightning forking through the storm.
    */
   static void sky(Fx c, EffectMesh m, float t) {
      if (t < 1.6F || t >= IsosangRules.G_FALL) {
         return;
      }
      float on = Ease.window(t, 1.6F, IsosangRules.G_FALL, .1F, .04F);
      FallingSword.Frame k = GScene.skyFrame(c.cast, t);
      double fov = GScene.SKY_FOV, tan = Math.tan(Math.toRadians(fov / 2.0));
      // golden motes drawn spinning into the eye, in front of the banks
      for (int i = 0; i < 48; i++) {
         double ph = ((double)t * (.35 + .25 * Ease.hash(i * 13 + 1)) + Ease.hash(i * 13 + 3)) % 1.0;
         double depth = NEAR + .9 * Ease.hash(i * 13 + 5);
         double r = (.16 + .5 * Ease.hash(i * 13 + 7)) * Math.pow(1.0 - ph, 1.4);
         double a = Ease.hash(i * 13 + 9) * Math.PI * 2.0 + 2.4 * ph;
         double height = 2.0 * tan * depth;
         Vec3d p = k.at(EYE_X, EYE_Y, depth, fov).add(k.r().multiply(Math.cos(a) * r * height)).add(k.u().multiply(Math.sin(a) * r * height));
         float al = on * (float)Math.sin(Math.PI * ph) * (.55F + .45F * (float)Math.sin(t * 13.0F + i));
         m.softDisc(p, .006 * depth, GOLD, .5F * al);
         m.star(p, .008 * depth, GOLD_WHITE, .7F * al);
      }
      // the lightning: its bolts in front of the banks, which it lights from inside (storm)
      for (int i = 0; i < SKY_BOLTS.length; i++) {
         double[] b = SKY_BOLTS[i];
         float fl = flash(t - (float)b[0]);
         if (fl <= .01F) {
            continue;
         }
         double depth = BOLT_DEPTH;
         Vec3d from = k.at(b[1], b[2], depth, fov), to = k.at(b[3], b[4], depth, fov);
         Vec3d side = to.subtract(from).crossProduct(k.f()).normalize();
         bolt(m, from, to, side, i * 7 + 3, .011 * depth, .0032 * depth, on * fl);
         m.softDisc(from.add(to).multiply(.5), .22 * depth, BOLT, .12F * on * fl);
      }
   }

   // ───────────────────────────── the fall ─────────────────────────────

   /**
    * The fall (2.78 to 3.1 s): collars of vapour round the falling blade, the air streaking past the camera, and rings of
    * light on the ground drawn in to where it will strike.
    */
   static void fall(Fx c, EffectMesh m, float t) {
      if (t < IsosangRules.G_FALL - .02F || t > IsosangRules.G_STRIKE + .03F) {
         return;
      }
      FallingSword.Blade bl = FallingSword.blade(c.cast, t);
      if (bl == null) {
         return;
      }
      float on = Ease.window(t, IsosangRules.G_FALL, IsosangRules.G_STRIKE + .03F, .03F, .03F);
      Vec3d across = bl.across().normalize(), normal = bl.normal().normalize(), axis = bl.axis().normalize();
      for (int i = 0; i < 4; i++) {
         double y = 3.0 + 2.6 * i;
         double ph = ((double)t * 5.0 + i * .25) % 1.0;
         double radius = FallingSword.half(y) * (1.18 + .1 * i) + .5 + .45 * ph;
         float al = on * (.24F - .04F * i) * (float)Math.sin(Math.PI * ph);
         band(m, bl.at(0.0, y, 0.0), across, normal, radius, .5 + .3 * i, MIST, al, 64);
      }
      Vec3d s = FallingSword.strike(c.cast);
      for (int i = 0; i < 52; i++) {
         double a = Ease.hash(i * 17 + 1) * Math.PI * 2.0, r = 3.0 + 11.0 * Ease.hash(i * 17 + 3);
         double h = 2.0 + 36.0 * Ease.hash(i * 17 + 5), len = 2.5 + 4.0 * Ease.hash(i * 17 + 7);
         Vec3d radial = across.multiply(Math.cos(a)).add(normal.multiply(Math.sin(a)));
         Vec3d p = s.add(axis.multiply(h)).add(radial.multiply(r));
         m.softLine(p, p.add(axis.multiply(len)), .07, 0xDDE8FF, .24F * on);
      }
      float near = Ease.smooth(t, 2.86F, 3.08F);
      for (int i = 0; i < 3; i++) {
         double ph = Math.max(0.0, Math.min(1.0, ((double)t - 2.86 - i * .05) / .2));
         if (ph > 0.0 && ph < 1.0) {
            band(m, s.add(0.0, .07, 0.0), X, Z, 12.0 * (1.0 - ph) + .8, .4, GOLD, .55F * (float)Math.sin(Math.PI * ph), 72);
         }
      }
      pool(m, s.add(0.0, .05, 0.0), X, Z, 2.0 + 5.0 * near, GOLD, .32F * near * on, 40);
   }

   // ───────────────────────────── the strike ─────────────────────────────

   /** How bright the fissures are at {@code t}: lit as the sword strikes, beating faster and brighter until the blast. */
   static float ground(float t) {
      float present = Ease.smooth(t, IsosangRules.G_STRIKE, IsosangRules.G_STRIKE + .03F) * (1.0F - Ease.smooth(t, 5.3F, 5.8F));
      if (present <= 0.0F) {
         return 0.0F;
      }
      float charge = Ease.smooth(t, 3.3F, 4.7F);
      return present * (.45F + .35F * charge + .5F * heart(t) + .8F * FxLib.kick(t, 4.86F, .25F));
   }

   /**
    * The strike (3.1 s) and the sword standing in the ground until the blast: the shock wave along the ground (its ink rim
    * and dust in the alpha pass), the pillar of light along the sword, the burst, the fissures, lightning on the sword
    * and light drawn in to its foot.
    */
   static void strike(Fx c, EffectMesh m, float t, boolean ink) {
      float tau = t - IsosangRules.G_STRIKE;
      if (tau < -.01F || t > 5.8F) {
         return;
      }
      Vec3d s = FallingSword.strike(c.cast);
      Vec3d floor = s.add(0.0, .06, 0.0);
      if (tau < 1.2F) {
         wave(m, floor, tau, 46.0, .28, ink);
         wave(m, floor.add(0.0, .02, 0.0), tau - .09F, 30.0, .34, ink);
      }
      if (!ink) {
         float k = FxLib.kick(t, IsosangRules.G_STRIKE, .22F);
         if (k > .01F) {
            Vec3d axis = GScene.axis(c.cast);
            m.softTaper(s.subtract(axis), s.add(axis.multiply(80.0)), 10.0, 3.0, GOLD, .26F * k);
            m.softTaper(s.subtract(axis), s.add(axis.multiply(80.0)), 2.0, .5, GOLD_WHITE, .55F * k);
            double r = 3.0 + 8.0 * Ease.out(tau, 0.0F, .2F);
            m.softDisc(s.add(0.0, 2.0, 0.0), r, GOLD, .26F * (1.0F - Ease.smooth(tau, .03F, .25F)));
         }
      }
      fissures(c, m, t, floor, ink);
      if (!ink && t < 4.8F) {
         arcs(c, m, t);
         gather(c, m, t, s);
         embers(c, m, t);
      }
   }

   /**
    * Embers rising off the sword's face on both sides while it stands in the ground, more of them and faster as the blast
    * nears, swelling on each beat of its heart.
    */
   static void embers(Fx c, EffectMesh m, float t) {
      float on = Ease.smooth(t, IsosangRules.G_STRIKE + .05F, 3.5F) * (1.0F - Ease.smooth(t, 4.66F, 4.8F));
      if (on <= .01F) {
         return;
      }
      FallingSword.Blade bl = FallingSword.shown(c) ? FallingSword.blade(c.cast, t) : GWorld.blade(c.cast, t);
      if (bl == null || bl.alpha() < .02F) {
         return;
      }
      float charge = Ease.smooth(t, 3.3F, 4.7F), beat = heart(t);
      double buried = Math.max(0.0, -GScene.height(t)) + .2;
      int side = bl.normal().dotProduct(c.camera.subtract(bl.point())) >= 0.0 ? 1 : -1;
      for (int i = 0; i < 90; i++) {
         if (i >= 40 + 50 * charge) {
            break;
         }
         double speed = .3 + .25 * Ease.hash(i * 23 + 1) + .3 * charge;
         double ph = ((double)t * speed + Ease.hash(i * 23 + 3)) % 1.0;
         double y = buried + 13.0 * ph;
         double x = (Ease.hash(i * 23 + 5) * 2.0 - 1.0) * Math.max(.3, FallingSword.half(y) - .2) + .25 * Math.sin(t * 2.3 + i);
         double z = (.45 + .5 * Ease.hash(i * 23 + 7)) * side;
         Vec3d p = bl.at(x, y, z);
         float al = on * bl.alpha() * (float)Math.sin(Math.PI * ph) * (.55F + .45F * (float)Math.sin(t * 17.0F + i * 2.1F)) * (.75F + .5F * beat);
         m.softDisc(p, .1 + .05 * charge, AMBER, .45F * al);
         m.star(p, .07 + .05 * Ease.hash(i * 23 + 9), GOLD_WHITE, .8F * al);
      }
   }

   /** A shock wave {@code tau} seconds old racing out along the ground from {@code o}: bright, its ink rim, the dust behind. */
   static void wave(EffectMesh m, Vec3d o, float tau, double reach, double pace, boolean ink) {
      if (tau <= 0.0F) {
         return;
      }
      double r = reach * (1.0 - Math.exp(-tau / pace));
      double w = .6 + 2.4 * tau;
      float a = Ease.smooth(tau, 0.0F, .02F) * (1.0F - Ease.smooth(tau, .15F, .95F));
      if (ink) {
         band(m, o, X, Z, r + w * .55, w * .35, INK, .5F * a, 96);
         band(m, o.add(0.0, -.01, 0.0), X, Z, Math.max(0.0, r - w * 1.2), w * 1.6, DUST, .28F * a, 96);
      } else {
         band(m, o, X, Z, r, w, GOLD, .65F * a, 96);
         band(m, o.add(0.0, .01, 0.0), X, Z, r, w * .3, GOLD_WHITE, .9F * a, 96);
      }
   }

   /**
    * The fissures: {@link #FISSURES} cracks torn out along the ground from where the sword struck (in a third of a second),
    * glowing with {@link #ground} (their dark rims in the ink pass).
    */
   static void fissures(Fx c, EffectMesh m, float t, Vec3d o, boolean ink) {
      float g = ground(t);
      if (g <= .01F) {
         return;
      }
      float tau = t - IsosangRules.G_STRIKE;
      double grown = Ease.out(tau, 0.0F, .32F);
      int seed = c.cast.id().hashCode();
      for (int i = 0; i < FISSURES; i++) {
         double angle = (i + .35 * Ease.hash(seed + i * 11)) * Math.PI * 2.0 / FISSURES;
         double length = 9.0 + 9.0 * Ease.hash(seed + i * 11 + 3);
         Vec3d[] points = new Vec3d[10];
         for (int j = 0; j < points.length; j++) {
            double u = (double)j / (points.length - 1);
            double r = 1.6 + (length - 1.6) * u;
            double bend = (Ease.hash(seed + i * 11 + j * 3 + 7) - .5) * (.18 + .1 * u);
            points[j] = o.add(Math.cos(angle + bend) * r, 0.0, Math.sin(angle + bend) * r);
         }
         crack(m, points, grown, g, ink, 1.0);
         Vec3d[] fork = new Vec3d[5];
         double turn = (Ease.hash(seed + i * 11 + 5) > .5 ? 1 : -1) * (.45 + .25 * Ease.hash(seed + i * 11 + 9));
         fork[0] = points[5];
         for (int j = 1; j < fork.length; j++) {
            double r = length * .22 * j / (fork.length - 1);
            fork[j] = points[5].add(Math.cos(angle + turn) * r, 0.0, Math.sin(angle + turn) * r);
         }
         crack(m, fork, Math.max(0.0, (grown - .55) / .45), g * .8F, ink, .7);
      }
   }

   /** One crack along {@code points}, drawn out to {@code grown} (0..1) of its length. */
   static void crack(EffectMesh m, Vec3d[] points, double grown, float g, boolean ink, double width) {
      if (grown <= 0.0) {
         return;
      }
      double reach = grown * (points.length - 1);
      for (int j = 0; j + 1 < points.length && j < reach; j++) {
         Vec3d a = points[j], b = points[j + 1];
         if (j + 1 > reach) {
            b = a.add(b.subtract(a).multiply(reach - j));
         }
         double taper = width * (1.0 - .55 * j / (points.length - 1));
         if (ink) {
            strip(m, a, b, UP, .7 * taper, INK, .55F * Math.min(1.0F, g * 1.6F));
         } else {
            strip(m, a.add(0.0, .01, 0.0), b.add(0.0, .01, 0.0), UP, .38 * taper, EMBER, .55F * g);
            strip(m, a.add(0.0, .02, 0.0), b.add(0.0, .02, 0.0), UP, .11 * taper, GOLD_WHITE, .95F * Math.min(1.0F, g));
         }
      }
   }

   /** Lightning crawling over the sword's foot while it stands in the ground: more of it as the blast nears. */
   static void arcs(Fx c, EffectMesh m, float t) {
      if (t < IsosangRules.G_STRIKE + .05F) {
         return;
      }
      FallingSword.Blade bl = FallingSword.shown(c) ? FallingSword.blade(c.cast, t) : GWorld.blade(c.cast, t);
      if (bl == null || bl.alpha() < .02F) {
         return;
      }
      float charge = Ease.smooth(t, 3.2F, 4.65F);
      double buried = Math.max(0.0, -GScene.height(t)) + .3;
      int side = bl.normal().dotProduct(c.camera.subtract(bl.point())) >= 0.0 ? 1 : -1;
      for (int a = 0; a < 8; a++) {
         float showing = Ease.smooth(charge, a / 8.0F - .1F, a / 8.0F + .05F) * bl.alpha();
         if (showing <= .01F) {
            continue;
         }
         double slot = .055 + .02 * Ease.hash(a * 5 + 1);
         double clock = t + a * .013;
         long n = (long)Math.floor(clock / slot);
         double u = clock / slot - n;
         int seed = (int)(n * 97L + a * 13L);
         double y0 = buried + 9.0 * Ease.hash(seed + 1), y1 = Math.max(buried, y0 + (Ease.hash(seed + 2) - .4) * 5.0);
         double x0 = (Ease.hash(seed + 3) * 2.0 - 1.0) * Math.max(.2, FallingSword.half(y0) - .4);
         double x1 = (Ease.hash(seed + 4) * 2.0 - 1.0) * Math.max(.2, FallingSword.half(y1) - .4);
         Vec3d from = bl.at(x0, y0, .45 * side), to = bl.at(x1, y1, .45 * side);
         float al = (float)Math.sin(Math.PI * u) * showing;
         bolt(m, from, to, bl.across().normalize(), seed, .2, .05, .9F * al);
      }
   }

   /** Light drawn in from all round to the sword's foot, faster and brighter as the blast nears (3.5 s on). */
   static void gather(Fx c, EffectMesh m, float t, Vec3d s) {
      float charge = Ease.smooth(t, 3.5F, 4.6F) * (1.0F - Ease.smooth(t, 4.72F, 4.8F));
      if (charge <= .01F) {
         return;
      }
      Vec3d foot = s.add(0.0, 1.2, 0.0);
      for (int i = 0; i < 72; i++) {
         double ph = ((double)t * (.9 + .8 * Ease.hash(i * 19 + 1)) + Ease.hash(i * 19 + 3)) % 1.0;
         double az = Ease.hash(i * 19 + 5) * Math.PI * 2.0, el = .1 + 1.1 * Ease.hash(i * 19 + 7);
         double r0 = 7.0 + 14.0 * Ease.hash(i * 19 + 9);
         Vec3d start = foot.add(Math.cos(az) * Math.cos(el) * r0, Math.sin(el) * r0, Math.sin(az) * Math.cos(el) * r0);
         double in = ph * ph;
         Vec3d p = start.add(foot.subtract(start).multiply(in)), q = start.add(foot.subtract(start).multiply(Math.max(0.0, in - .08)));
         float al = charge * (float)Math.sin(Math.PI * ph);
         m.softTaper(q, p, .02, .1, AMBER, .35F * al);
         m.star(p, .14, GOLD_WHITE, .6F * al);
      }
      m.softDisc(foot, 1.5 + 2.5 * charge, GOLD_WHITE, .35F * charge);
   }

   // ───────────────────────────── the blast ─────────────────────────────

   /** The blast (4.85 s): a dome of light rings swelling over its ground, rings racing out along it, a pillar into the sky. */
   static void blast(Fx c, EffectMesh m, float t, boolean ink) {
      float tau = t - 4.85F;
      if (tau < 0.0F || tau > 1.7F) {
         return;
      }
      Vec3d s = FallingSword.strike(c.cast);
      double reach = Math.max(31.0, GArenaClient.radius(c.cast));
      wave(m, s.add(0.0, .08, 0.0), tau, reach + 6.0, .35, ink);
      wave(m, s.add(0.0, .1, 0.0), tau - .12F, reach, .45, ink);
      if (ink) {
         return;
      }
      double r = 36.0 * (1.0 - Math.exp(-tau / .3));
      float fade = Ease.smooth(tau, 0.0F, .03F) * (1.0F - Ease.smooth(tau, .2F, 1.1F));
      for (int i = 0; i < 5; i++) {
         double lat = Math.toRadians(i * 15.0);
         band(m, s.add(0.0, r * Math.sin(lat), 0.0), X, Z, r * Math.cos(lat), .7 + .035 * r, i == 0 ? GOLD_WHITE : GOLD, .2F * fade * (1.0F - i / 6.0F), 96);
      }
      float k = FxLib.kick(t, 4.86F, .4F);
      if (k > .01F) {
         m.softTaper(s, s.add(0.0, 110.0, 0.0), 12.0, 4.0, GOLD, .2F * k);
         m.softTaper(s, s.add(0.0, 110.0, 0.0), 2.6, .8, GOLD_WHITE, .6F * k);
      }
   }

   // ───────────────────────────── shapes ─────────────────────────────

   /** A bolt from a to b: a jagged line (its kinks across {@code side}) forking twice; glow and core widths in blocks. */
   static void bolt(EffectMesh m, Vec3d a, Vec3d b, Vec3d side, int seed, double glow, double core, float alpha) {
      if (alpha <= .005F) {
         return;
      }
      int n = 12;
      double length = b.subtract(a).length();
      Vec3d[] points = new Vec3d[n + 1];
      for (int j = 0; j <= n; j++) {
         double u = (double)j / n;
         double off = j == 0 || j == n ? 0.0 : (Ease.hash(seed * 31 + j) - .5) * .22 * length * Math.sin(Math.PI * u) + (Ease.hash(seed * 37 + j * 3) - .5) * .05 * length;
         points[j] = a.add(b.subtract(a).multiply(u)).add(side.multiply(off));
      }
      for (int j = 0; j < n; j++) {
         m.softLine(points[j], points[j + 1], glow, BOLT, .45F * alpha);
         m.line(points[j], points[j + 1], core, BOLT_CORE, alpha);
      }
      Vec3d along = b.subtract(a).normalize();
      for (int f = 0; f < 2; f++) {
         Vec3d o = points[3 + f * 4 + (int)(Ease.hash(seed * 41 + f) * 2.0)];
         Vec3d dir = along.multiply(.6).add(side.multiply(f == 0 ? .8 : -.8)).normalize();
         double fl = length * (.18 + .12 * Ease.hash(seed * 43 + f));
         Vec3d q = o;
         for (int j = 1; j <= 5; j++) {
            double u = j / 5.0;
            Vec3d p = o.add(dir.multiply(fl * u)).add(side.multiply((Ease.hash(seed * 47 + f * 7 + j) - .5) * .12 * fl));
            float k = alpha * (float)(1.0 - u * .6);
            m.softLine(q, p, glow * .6, BOLT, .35F * k);
            m.line(q, p, core * .6, BOLT_CORE, .8F * k);
            q = p;
         }
      }
   }

   /** A ring lying in the plane of u and v (unit vectors) round o: a band {@code width} wide, brightest at its middle. */
   static void band(EffectMesh m, Vec3d o, Vec3d u, Vec3d v, double radius, double width, int rgb, float alpha, int segments) {
      if (alpha <= .003F || radius <= 0.0 || width <= 0.0) {
         return;
      }
      double in = Math.max(0.0, radius - width * .5), out = radius + width * .5;
      for (int i = 0; i < segments; i++) {
         double a = Math.PI * 2.0 * i / segments, b = Math.PI * 2.0 * (i + 1) / segments;
         Vec3d da = u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a))), db = u.multiply(Math.cos(b)).add(v.multiply(Math.sin(b)));
         m.vertex(o.add(da.multiply(in)), rgb, 0.0F);
         m.vertex(o.add(db.multiply(in)), rgb, 0.0F);
         m.vertex(o.add(db.multiply(radius)), rgb, alpha);
         m.vertex(o.add(da.multiply(radius)), rgb, alpha);
         m.vertex(o.add(da.multiply(radius)), rgb, alpha);
         m.vertex(o.add(db.multiply(radius)), rgb, alpha);
         m.vertex(o.add(db.multiply(out)), rgb, 0.0F);
         m.vertex(o.add(da.multiply(out)), rgb, 0.0F);
      }
   }

   /** A flat glow lying in the plane of u and v round o: brightest at o, nothing at its rim. */
   static void pool(EffectMesh m, Vec3d o, Vec3d u, Vec3d v, double radius, int rgb, float alpha, int segments) {
      if (alpha <= .003F || radius <= 0.0) {
         return;
      }
      for (int i = 0; i < segments; i++) {
         double a = Math.PI * 2.0 * i / segments, b = Math.PI * 2.0 * (i + 1) / segments;
         Vec3d pa = o.add(u.multiply(Math.cos(a) * radius)).add(v.multiply(Math.sin(a) * radius));
         Vec3d pb = o.add(u.multiply(Math.cos(b) * radius)).add(v.multiply(Math.sin(b) * radius));
         m.vertex(o, rgb, alpha);
         m.vertex(pa, rgb, 0.0F);
         m.vertex(pb, rgb, 0.0F);
         m.vertex(o, rgb, alpha);
      }
   }

   /** A ribbon from a to b lying on the plane with normal {@code n}: solid along its middle, soft at both edges. */
   static void strip(EffectMesh m, Vec3d a, Vec3d b, Vec3d n, double width, int rgb, float alpha) {
      if (alpha <= .003F) {
         return;
      }
      Vec3d d = b.subtract(a);
      if (d.lengthSquared() < 1.0E-8) {
         return;
      }
      Vec3d side = d.crossProduct(n).normalize().multiply(width * .5);
      m.vertex(a, rgb, alpha);
      m.vertex(b, rgb, alpha);
      m.vertex(b.add(side), rgb, 0.0F);
      m.vertex(a.add(side), rgb, 0.0F);
      m.vertex(a.subtract(side), rgb, 0.0F);
      m.vertex(b.subtract(side), rgb, 0.0F);
      m.vertex(b, rgb, alpha);
      m.vertex(a, rgb, alpha);
   }

   // ───────────────────────────── timing ─────────────────────────────

   /** A lightning flash {@code since} seconds after it began: a strike and a weaker second stroke. */
   static float flash(float since) {
      if (since < -.01F || since > .45F) {
         return 0.0F;
      }
      return Math.min(1.0F, FxLib.kick(since, 0.0F, .03F) + .75F * FxLib.kick(since, .075F, .05F)) * (1.0F - Ease.smooth(since, .35F, .45F));
   }

   /** The impact frame round {@code at} (as FxLib's: a few frames). */
   static float impact(float t, float at) {
      if (t < at - .01F || t >= at + .125F) {
         return 0.0F;
      }
      return Ease.smooth(t, at - .01F, at + .02F) * (1.0F - Ease.smooth(t, at + .085F, at + .125F));
   }
}
