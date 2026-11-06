package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;

/**
 * Lee So-sang's G seen from afar (2.89.0, user request: "이거 컷씬 검이랑 멀리서 보는 검이랑 떨어지는 검 모델링이 다를거거든
 * 내가 어느정도는 멀리서 봐도 멋있게 만들어놨는데 너가 멀리서 봤을때 더 웅장하게 만들어줘 이펙트 5배정도 상향해줘"): effects on the
 * scale of the storm and of the sword itself, for anyone watching from outside the caster's cutscene. They come in with
 * the camera's distance from where the sword strikes ({@link #weight}: none within {@link #NEAR} blocks, whole from
 * {@link #FULL}), and their strokes widen with that distance ({@link #scale}) so they keep their weight on the screen:
 * <ul>
 *   <li>the storm: lightning striking the ground round it from its base ({@link #GROUND_BOLTS}), each bolt lighting its
 *   banks from inside, and its eye burning gold (isosang_cloud.fsh's Grand and Lightning in the observers' Mode 3,
 *   {@link #storm});</li>
 *   <li>a column of golden light from the storm down to where the sword will strike, motes drawn down it, and rings of
 *   light marking the spot;</li>
 *   <li>the fall: a long golden trail behind the sword and rings of vapour it bursts through on its way down;</li>
 *   <li>the strike: a dome of light, a wall of light racing out along the ground, a pillar of light far up along the
 *   sword, and the land lit gold;</li>
 *   <li>while it stands: a ring of light racing out along the ground on each beat of its heart, golden spirals climbing
 *   it and lightning running up it;</li>
 *   <li>the blast: a great dome of light rings, rings racing out past the edge of its ground, a pillar of light into the
 *   sky, and a ring at the storm's height blowing it apart.</li>
 * </ul>
 * Every part is a function of the cast clock alone.
 */
final class GFar {
   static final int GOLD = FallingSword.GOLD, GOLD_WHITE = FallingSword.GOLD_WHITE, MIST = 0xE6EEFF, INK = 0x170D07;
   static final int BOLT = GGrand.BOLT, BOLT_CORE = GGrand.BOLT_CORE;
   /** From how far (blocks from where the sword strikes) the far view's effects come in, and from how far they are whole. */
   static final double NEAR = 14.0, FULL = 40.0;
   /** The distance at which strokes keep their own widths; further away they widen with it. */
   static final double UNIT = 24.0;
   /** How high the storm's base is above where the sword strikes (the clouds' centre is 40 blocks up its axis). */
   static final double BASE = 36.0;
   /** The lightning from the storm to the ground: when (seconds). Where is chosen from the cast. */
   static final float[] GROUND_BOLTS = {.92F, 1.08F, 1.27F, 1.41F, 1.58F, 1.73F, 1.86F, 2.02F, 2.16F, 2.31F, 2.44F, 2.57F, 2.69F};
   static final double[] FALL_RINGS = {29.0, 21.0, 13.0, 6.0};

   private GFar() {
   }

   // ───────────────────────────── who sees it ─────────────────────────────

   /** How much of the far view the camera gets: 0 in the caster's cutscene, within {@link #NEAR} blocks or hidden. */
   static float weight(Fx c) {
      if (FallingSword.shown(c) || GStage.hidden() || !Float.isNaN(FallingSword.off(c.cast))) {
         return 0.0F;
      }
      return Ease.smooth((float)c.camera.distanceTo(FallingSword.strike(c.cast)), (float)NEAR, (float)FULL);
   }

   /** How much wider strokes are drawn: 1 up to {@link #UNIT} blocks away, then in step with the distance. */
   static double scale(Fx c) {
      return Math.max(1.0, c.camera.distanceTo(FallingSword.strike(c.cast)) / UNIT);
   }

   // ───────────────────────────── the storm ─────────────────────────────

   /** Where the {@code i}th bolt leaves the storm's base and where it strikes the ground. */
   static Vec3d[] groundBolt(StandClient.Cast cast, int i) {
      Vec3d s = FallingSword.strike(cast);
      Vec3d centre = GScene.cloudCenter(cast);
      int seed = cast.id().hashCode() * 31 + i * 97;
      double a = Ease.hash(seed + 1) * Math.PI * 2.0;
      double top = 8.0 + 20.0 * Ease.hash(seed + 3);
      double turn = (Ease.hash(seed + 5) - .5) * .9;
      double reach = 12.0 + 26.0 * Ease.hash(seed + 7);
      Vec3d from = new Vec3d(centre.x + Math.cos(a) * top, s.y + BASE - 2.0 * Ease.hash(seed + 9), centre.z + Math.sin(a) * top);
      Vec3d to = new Vec3d(s.x + Math.cos(a + turn) * reach, s.y + .05, s.z + Math.sin(a + turn) * reach);
      return new Vec3d[]{from, to};
   }

   /** The bolt flashing now and how brightly (its index, or -1 for none, and its flash). */
   static float[] flashing(float t) {
      float best = 0.0F;
      int which = -1;
      for (int i = 0; i < GROUND_BOLTS.length; i++) {
         float fl = GGrand.flash(t - GROUND_BOLTS[i]);
         if (fl > best) {
            best = fl;
            which = i;
         }
      }
      return new float[]{which, best};
   }

   /**
    * The storm's own light for the observers' view of it (isosang_cloud.fsh's Mode 3): how strongly its eye burns gold;
    * where the bolt flashing now leaves its base, in the storm's own frame (right, up, forward of the caster, from its
    * centre), and how strongly it lights the banks round it.
    */
   static float[] storm(StandClient.Cast cast, float t, Vec3d camera) {
      float w = Ease.smooth((float)camera.distanceTo(FallingSword.strike(cast)), (float)NEAR, (float)FULL);
      float eye = w * .9F * Ease.smooth(t, 1.2F, 1.8F) * (1.0F - Ease.smooth(t, 3.0F, 3.3F));
      float[] now = flashing(t);
      float[] out = new float[]{eye, t, 0.0F, 0.0F, 0.0F, 0.0F};
      if (now[0] >= 0.0F && now[1] > .005F) {
         Vec3d top = groundBolt(cast, (int)now[0])[0].subtract(GScene.cloudCenter(cast));
         Vec3d f = FallingSword.forward(cast), r = FallingSword.right(f);
         out[2] = (float)top.dotProduct(r);
         out[3] = (float)top.y;
         out[4] = (float)top.dotProduct(f);
         out[5] = w * 1.1F * now[1];
      }
      return out;
   }

   // ───────────────────────────── passes ─────────────────────────────

   static void additive(Fx c, EffectMesh m) {
      float w = weight(c);
      if (w <= .01F) {
         return;
      }
      float t = c.sec;
      double k = scale(c);
      bolts(c, m, t, w, k);
      column(c, m, t, w, k);
      fall(c, m, t, w, k);
      strike(c, m, t, w, k, false);
      standing(c, m, t, w, k);
      blast(c, m, t, w, k, false);
   }

   /** The ink: the dark rims of the walls of light racing out along the ground. */
   static void alpha(Fx c, EffectMesh m) {
      float w = weight(c);
      if (w <= .01F) {
         return;
      }
      strike(c, m, c.sec, w, scale(c), true);
      blast(c, m, c.sec, w, scale(c), true);
   }

   static void frame(Fx c, FxFrame f) {
      float w = weight(c);
      if (w <= .01F) {
         return;
      }
      float t = c.sec;
      Vec3d s = FallingSword.strike(c.cast);
      float[] now = flashing(t);
      if (now[0] >= 0.0F && now[1] > .02F) {
         Vec3d[] b = groundBolt(c.cast, (int)now[0]);
         f.light(b[1].add(0.0, 2.0, 0.0), BOLT, 34.0, 1.8F * now[1] * w);
         f.light(b[0], BOLT, 30.0, 1.2F * now[1] * w);
      }
      float col = columnOn(t);
      if (col > .02F) {
         f.light(s.add(0.0, 4.0, 0.0), GOLD, 30.0, .8F * col * w);
      }
      float k = FxLib.kick(t, IsosangRules.G_STRIKE, .35F);
      if (k > .01F) {
         f.light(s.add(0.0, 10.0, 0.0), GOLD_WHITE, 90.0, 2.2F * k * w);
         f.shaft(s.add(0.0, 14.0, 0.0), GOLD, .4F * k * w);
      }
      float beat = GGrand.heart(t) * Ease.smooth(t, 3.4F, 3.5F) * (1.0F - Ease.smooth(t, 4.75F, 4.82F));
      if (beat > .02F) {
         f.light(s.add(0.0, 3.0, 0.0), GOLD, 46.0, 1.1F * beat * w);
      }
      float b = FxLib.kick(t, 4.86F, .55F);
      if (b > .01F) {
         f.light(s.add(0.0, 18.0, 0.0), GOLD, 120.0, 1.8F * b * w);
         f.shaft(s.add(0.0, 24.0, 0.0), GOLD, .4F * b * w);
      }
   }

   // ───────────────────────────── the storm's lightning ─────────────────────────────

   /** Lightning striking the ground from the storm's base: jagged, forking, flashing twice; the ground lit where it lands. */
   static void bolts(Fx c, EffectMesh m, float t, float w, double k) {
      for (int i = 0; i < GROUND_BOLTS.length; i++) {
         float fl = GGrand.flash(t - GROUND_BOLTS[i]);
         if (fl <= .01F) {
            continue;
         }
         Vec3d[] b = groundBolt(c.cast, i);
         Vec3d d = b[1].subtract(b[0]);
         Vec3d side = d.crossProduct(new Vec3d(0.0, 1.0, 0.0));
         side = side.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : side.normalize();
         float a = fl * w;
         GGrand.bolt(m, b[0], b[1], side, c.cast.id().hashCode() + i * 13, .7 * k, .16 * k, a);
         m.softDisc(b[1].add(0.0, 1.0, 0.0), 6.0 * k, BOLT, .22F * a);
         m.softDisc(b[1].add(0.0, .4, 0.0), 2.2 * k, BOLT_CORE, .45F * a);
         float since = t - GROUND_BOLTS[i];
         double r = 1.5 + 9.0 * (1.0 - Math.exp(-Math.max(0.0, since) / .12));
         GGrand.band(m, b[1].add(0.0, .08, 0.0), GGrand.X, GGrand.Z, r, (.6 + 1.2 * since) * k, BOLT, .5F * a, 48);
      }
   }

   // ───────────────────────────── the column of light ─────────────────────────────

   static float columnOn(float t) {
      return Ease.smooth(t, 1.15F, 1.65F) * (1.0F - Ease.smooth(t, 2.9F, 3.14F));
   }

   /** A column of golden light from the storm down to where the sword will strike, motes drawn down it, the spot ringed. */
   static void column(Fx c, EffectMesh m, float t, float w, double k) {
      float on = columnOn(t) * w;
      if (on <= .01F) {
         return;
      }
      Vec3d s = FallingSword.strike(c.cast);
      Vec3d axis = GScene.axis(c.cast);
      Vec3d top = s.add(axis.multiply(BASE + 2.0));
      float pulse = .85F + .15F * (float)Math.sin(t * 7.0F);
      m.softTaper(s, top, 13.0, 18.0, GOLD, .14F * on * pulse);
      m.softTaper(s, top, 4.0, 6.5, GOLD_WHITE, .3F * on * pulse);
      m.softTaper(s, top, .7 * k, 1.1 * k, 0xFFFDF2, .55F * on);
      Vec3d across = GScene.across(c.cast).normalize(), normal = GScene.normal(c.cast).normalize();
      for (int i = 0; i < 40; i++) {
         double ph = ((double)t * (.45 + .3 * Ease.hash(i * 11 + 1)) + Ease.hash(i * 11 + 3)) % 1.0;
         double h = (BASE + 1.0) * (1.0 - ph);
         double a = Ease.hash(i * 11 + 5) * Math.PI * 2.0 + t * (1.2 + Ease.hash(i * 11 + 7));
         double r = 1.5 + 4.5 * Ease.hash(i * 11 + 9);
         Vec3d p = s.add(axis.multiply(h)).add(across.multiply(Math.cos(a) * r)).add(normal.multiply(Math.sin(a) * r));
         float al = on * (float)Math.sin(Math.PI * ph);
         m.softDisc(p, .5 * k, GOLD, .4F * al);
         m.star(p, .45 * k, GOLD_WHITE, .7F * al);
      }
      Vec3d floor = s.add(0.0, .1, 0.0);
      GGrand.band(m, floor, GGrand.X, GGrand.Z, 7.5, .45 * k, GOLD_WHITE, .75F * on, 96);
      GGrand.band(m, floor, GGrand.X, GGrand.Z, 10.5 + .6 * Math.sin(t * 3.0), .3 * k, GOLD, .6F * on, 96);
      for (int i = 0; i < 12; i++) {
         double a = i * Math.PI / 6.0 + t * .6;
         Vec3d u = new Vec3d(Math.cos(a), 0.0, Math.sin(a));
         GGrand.strip(m, floor.add(u.multiply(7.8)), floor.add(u.multiply(10.2)), GGrand.UP, .35 * k, GOLD_WHITE, .7F * on);
      }
      GGrand.pool(m, floor, GGrand.X, GGrand.Z, 9.0, GOLD, .22F * on, 48);
   }

   // ───────────────────────────── the fall ─────────────────────────────

   /** A long golden trail behind the falling sword and rings of vapour left where it bursts through the air. */
   static void fall(Fx c, EffectMesh m, float t, float w, double k) {
      if (t < IsosangRules.G_FALL - .05F || t > IsosangRules.G_STRIKE + .7F) {
         return;
      }
      Vec3d s = FallingSword.strike(c.cast);
      Vec3d axis = GScene.axis(c.cast);
      Vec3d across = GScene.across(c.cast).normalize(), normal = GScene.normal(c.cast).normalize();
      float falling = Ease.smooth(t, IsosangRules.G_FALL, IsosangRules.G_FALL + .04F) * (1.0F - Ease.smooth(t, IsosangRules.G_STRIKE, IsosangRules.G_STRIKE + .25F)) * w;
      if (falling > .01F) {
         double tip = Math.max(0.0, GScene.height(t));
         Vec3d from = s.add(axis.multiply(tip));
         Vec3d to = s.add(axis.multiply(Math.min(BASE + 6.0, tip + 30.0)));
         m.softTaper(from, to, 16.0, 26.0, GOLD, .16F * falling);
         m.softTaper(from, to, 5.0, 9.0, GOLD_WHITE, .32F * falling);
      }
      for (double h : FALL_RINGS) {
         float at = IsosangRules.G_FALL + .3166666F * (float)Math.sqrt(Math.max(0.0, 1.0 - h / 32.0));
         float since = t - at;
         if (since < 0.0F || since > .7F) {
            continue;
         }
         double r = 7.0 + 22.0 * (1.0 - Math.exp(-since / .16));
         float a = Ease.smooth(since, 0.0F, .02F) * (1.0F - Ease.smooth(since, .1F, .7F)) * w;
         Vec3d o = s.add(axis.multiply(h));
         GGrand.band(m, o, across, normal, r, (1.6 + 4.0 * since) * k, MIST, .5F * a, 72);
         GGrand.band(m, o, across, normal, r, (.5 + 1.2 * since) * k, GOLD_WHITE, .55F * a, 72);
      }
   }

   // ───────────────────────────── the strike ─────────────────────────────

   /** The strike seen from afar: a dome of light, a wall of light racing out with its ink rim, a pillar far up the sword. */
   static void strike(Fx c, EffectMesh m, float t, float w, double k, boolean ink) {
      float tau = t - IsosangRules.G_STRIKE;
      if (tau < -.01F || tau > 1.6F) {
         return;
      }
      Vec3d s = FallingSword.strike(c.cast);
      wall(m, s.add(0.0, .05, 0.0), tau, 62.0, .34, 7.0, w, k, ink);
      if (ink) {
         return;
      }
      dome(m, s, tau, 30.0, .1, .6F, w, k);
      float kick = FxLib.kick(t, IsosangRules.G_STRIKE, .3F);
      if (kick > .01F) {
         Vec3d axis = GScene.axis(c.cast);
         m.softTaper(s.subtract(axis), s.add(axis.multiply(170.0)), 26.0, 12.0, GOLD, .2F * kick * w);
         m.softTaper(s.subtract(axis), s.add(axis.multiply(170.0)), 7.0, 3.0, GOLD_WHITE, .5F * kick * w);
         m.softDisc(s.add(0.0, 6.0, 0.0), 22.0 * (1.0 - .5 * Math.exp(-tau / .08)), GOLD, .22F * kick * w);
      }
   }

   /**
    * A wall of light {@code tau} seconds old racing out along the ground to {@code reach} blocks, {@code high} blocks
    * tall, its top lit gold and white (or in the ink pass, its dark rim along the ground and a veil of dust behind it).
    */
   static void wall(EffectMesh m, Vec3d o, float tau, double reach, double pace, double high, float w, double k, boolean ink) {
      if (tau <= 0.0F) {
         return;
      }
      double r = reach * (1.0 - Math.exp(-tau / pace));
      double h = high * (.35 + .65 * (1.0 - Math.exp(-tau / .12))) * (1.0 - .5 * Ease.smooth(tau, .3F, 1.2F));
      float a = Ease.smooth(tau, 0.0F, .03F) * (1.0F - Ease.smooth(tau, .25F, 1.25F)) * w;
      if (a <= .005F) {
         return;
      }
      int n = 120;
      for (int i = 0; i < n; i++) {
         double a0 = Math.PI * 2.0 * i / n, a1 = Math.PI * 2.0 * (i + 1) / n;
         Vec3d d0 = new Vec3d(Math.cos(a0), 0.0, Math.sin(a0)), d1 = new Vec3d(Math.cos(a1), 0.0, Math.sin(a1));
         double lean = .25 * h;
         Vec3d b0 = o.add(d0.multiply(r)), b1 = o.add(d1.multiply(r));
         Vec3d t0 = o.add(d0.multiply(r + lean)).add(0.0, h, 0.0), t1 = o.add(d1.multiply(r + lean)).add(0.0, h, 0.0);
         if (ink) {
            // a veil of dust thrown up behind the wall, darkest at its foot
            Vec3d i0 = o.add(d0.multiply(Math.max(0.0, r - 3.5 * k))), i1 = o.add(d1.multiply(Math.max(0.0, r - 3.5 * k)));
            m.vertex(i0, GGrand.DUST, 0.0F);
            m.vertex(i1, GGrand.DUST, 0.0F);
            m.vertex(b1, GGrand.DUST, .3F * a);
            m.vertex(b0, GGrand.DUST, .3F * a);
            m.vertex(b0, INK, .45F * a);
            m.vertex(b1, INK, .45F * a);
            m.vertex(b1.add(0.0, .3 * h, 0.0), INK, 0.0F);
            m.vertex(b0.add(0.0, .3 * h, 0.0), INK, 0.0F);
         } else {
            m.vertex(b0, GOLD, .5F * a);
            m.vertex(b1, GOLD, .5F * a);
            m.vertex(t1, GOLD, 0.0F);
            m.vertex(t0, GOLD, 0.0F);
            Vec3d m0 = o.add(d0.multiply(r + lean * .55)).add(0.0, h * .55, 0.0), m1 = o.add(d1.multiply(r + lean * .55)).add(0.0, h * .55, 0.0);
            m.vertex(b0, GOLD_WHITE, .55F * a);
            m.vertex(b1, GOLD_WHITE, .55F * a);
            m.vertex(m1, GOLD_WHITE, 0.0F);
            m.vertex(m0, GOLD_WHITE, 0.0F);
         }
      }
      if (!ink) {
         GGrand.band(m, o.add(0.0, .03, 0.0), GGrand.X, GGrand.Z, r, (1.2 + 3.0 * tau) * k, GOLD_WHITE, .6F * a, 120);
      }
   }

   /** A dome of light rings swelling over the ground to {@code reach} blocks, gone after {@code life} seconds. */
   static void dome(EffectMesh m, Vec3d o, float tau, double reach, double pace, float life, float w, double k) {
      if (tau <= 0.0F) {
         return;
      }
      double r = reach * (1.0 - Math.exp(-tau / pace));
      float fade = Ease.smooth(tau, 0.0F, .02F) * (1.0F - Ease.smooth(tau, life * .3F, life)) * w;
      if (fade <= .005F) {
         return;
      }
      for (int i = 0; i < 7; i++) {
         double lat = Math.toRadians(i * 12.0);
         double width = (.8 + .045 * r) * k;
         GGrand.band(m, o.add(0.0, r * Math.sin(lat), 0.0), GGrand.X, GGrand.Z, r * Math.cos(lat), width, i == 0 ? GOLD_WHITE : GOLD, .3F * fade * (1.0F - i / 8.0F), 96);
      }
      m.softDisc(o.add(0.0, r * .5, 0.0), r * .95, GOLD, .1F * fade);
   }

   // ───────────────────────────── while it stands ─────────────────────────────

   /** A ring of light racing out on each beat of the sword's heart, golden spirals climbing it, lightning running up it. */
   static void standing(Fx c, EffectMesh m, float t, float w, double k) {
      if (t < IsosangRules.G_STRIKE || t > 4.85F) {
         return;
      }
      Vec3d s = FallingSword.strike(c.cast);
      Vec3d floor = s.add(0.0, .1, 0.0);
      for (float b : GGrand.BEATS) {
         float tau = t - b;
         if (tau < -.04F || tau > .8F) {
            continue;
         }
         double u = Ease.out(tau, -.04F, .76F);
         double r = 3.0 + 44.0 * u;
         float a = Ease.smooth(tau, -.04F, .04F) * (1.0F - Ease.smooth(tau, .15F, .8F)) * w;
         GGrand.band(m, floor, GGrand.X, GGrand.Z, r, (1.4 + 2.5 * tau) * k, GOLD, .55F * a, 120);
         GGrand.band(m, floor.add(0.0, .02, 0.0), GGrand.X, GGrand.Z, r, (.45 + .8 * tau) * k, GOLD_WHITE, .85F * a, 120);
      }
      float charge = Ease.smooth(t, 3.2F, 4.55F) * (1.0F - Ease.smooth(t, 4.66F, 4.8F)) * w;
      if (charge <= .01F) {
         return;
      }
      Vec3d axis = GScene.axis(c.cast);
      Vec3d across = GScene.across(c.cast).normalize(), normal = GScene.normal(c.cast).normalize();
      double buried = Math.max(0.0, -GScene.height(t));
      for (int h = 0; h < 3; h++) {
         Vec3d prev = null;
         for (int j = 0; j <= 90; j++) {
            double y = buried + 36.0 * j / 90.0;
            double a = h * Math.PI * 2.0 / 3.0 + y * .38 - t * 5.5;
            double r = FallingSword.half(Math.min(y, 6.0)) + 3.0 + .12 * y;
            Vec3d p = s.add(axis.multiply(y - buried)).add(across.multiply(Math.cos(a) * r * 1.25)).add(normal.multiply(Math.sin(a) * r));
            if (prev != null) {
               float al = charge * (float)Math.sin(Math.PI * j / 90.0) * (.6F + .4F * GGrand.heart(t));
               m.softLine(prev, p, 1.2 * k, GOLD, .35F * al);
               m.line(prev, p, .22 * k, GOLD_WHITE, .7F * al);
            }
            prev = p;
         }
      }
      FallingSword.Blade bl = GWorld.blade(c.cast, t);
      if (bl == null || bl.alpha() < .02F) {
         return;
      }
      int side = bl.normal().dotProduct(c.camera.subtract(bl.point())) >= 0.0 ? 1 : -1;
      for (int i = 0; i < 6; i++) {
         double slot = .07 + .03 * Ease.hash(i * 5 + 11);
         double clock = t + i * .021;
         long n = (long)Math.floor(clock / slot);
         double u = clock / slot - n;
         int seed = (int)(n * 131L + i * 17L);
         double y0 = buried + 14.0 * Ease.hash(seed + 1), y1 = y0 + 8.0 + 16.0 * Ease.hash(seed + 2);
         int edge = Ease.hash(seed + 3) > .5 ? 1 : -1;
         double x0 = edge * (FallingSword.half(y0) * 1.6 + 1.0), x1 = edge * (FallingSword.half(y1) * 1.6 + 1.5);
         Vec3d from = bl.at(x0, y0, 1.6 * side), to = bl.at(x1, y1, 1.6 * side);
         float al = (float)Math.sin(Math.PI * u) * charge * bl.alpha();
         GGrand.bolt(m, from, to, bl.across().normalize(), seed, .45 * k, .1 * k, .9F * al);
      }
   }

   // ───────────────────────────── the blast ─────────────────────────────

   /** The blast seen from afar: a great dome, walls of light past the edge of its ground, a pillar, the storm blown apart. */
   static void blast(Fx c, EffectMesh m, float t, float w, double k, boolean ink) {
      float tau = t - 4.85F;
      if (tau < 0.0F || tau > 2.0F) {
         return;
      }
      Vec3d s = FallingSword.strike(c.cast);
      double reach = Math.max(40.0, GArenaClient.radius(c.cast) + 12.0);
      wall(m, s.add(0.0, .06, 0.0), tau, reach + 22.0, .42, 10.0, w, k, ink);
      wall(m, s.add(0.0, .08, 0.0), tau - .15F, reach, .5, 6.0, w * .8F, k, ink);
      if (ink) {
         return;
      }
      dome(m, s, tau, 50.0, .22, 1.3F, w, k);
      float kick = FxLib.kick(t, 4.86F, .5F);
      if (kick > .01F) {
         m.softTaper(s, s.add(0.0, 260.0, 0.0), 34.0, 16.0, GOLD, .18F * kick * w);
         m.softTaper(s, s.add(0.0, 260.0, 0.0), 9.0, 4.0, GOLD_WHITE, .45F * kick * w);
      }
      Vec3d high = s.add(0.0, BASE, 0.0);
      double r = 14.0 + 90.0 * (1.0 - Math.exp(-tau / .45));
      float a = Ease.smooth(tau, 0.0F, .05F) * (1.0F - Ease.smooth(tau, .3F, 1.5F)) * w;
      GGrand.band(m, high, GGrand.X, GGrand.Z, r, (3.0 + 9.0 * tau) * k, MIST, .45F * a, 120);
      GGrand.band(m, high.add(0.0, .1, 0.0), GGrand.X, GGrand.Z, r, (1.0 + 2.5 * tau) * k, GOLD_WHITE, .6F * a, 120);
   }
}
