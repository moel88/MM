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
 *   <li>the storm: its eye burning gold (isosang_cloud.fsh's Grand in the observers' Mode 3, {@link #storm}). The
 *   lightning that struck the ground round it (2.89.0) is gone (2.90.0, user request: "번개 치는 건 없애고 구름을 더 많이
 *   추가하고 구름을 갈라서 떨어진다는 걸 아주 강하게 표현했으면 좋겠어", "멀리서 봐도 엄청 웅장하게"): instead there is far more
 *   cloud, and the sword cleaves it ({@link #cleftSpan}, {@link #cleftWide}: the clouds pushed aside along the blade's
 *   plane and piled up along the cleft's lips, a shock racing out through the banks as it comes through, cloud dragged
 *   down behind it as it falls, the storm blown apart by the blast, all in the shader), light pouring down through the
 *   cleft from its lit lips ({@link #cleft});</li>
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
   /** From how far (blocks from where the sword strikes) the far view's effects come in, and from how far they are whole. */
   static final double NEAR = 14.0, FULL = 40.0;
   /** The distance at which strokes keep their own widths; further away they widen with it. */
   static final double UNIT = 24.0;
   /** How high the storm's base is above where the sword strikes (the clouds' centre is 40 blocks up its axis). */
   static final double BASE = 36.0;
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

   /**
    * The storm's own light and its cleft for the observers' view of it (isosang_cloud.fsh's Grand in Mode 3): how strongly
    * its eye burns gold, the clock, and how long and how wide the cleft the sword cuts through it is ({@link #cleftSpan},
    * {@link #cleftWide}).
    */
   static float[] storm(StandClient.Cast cast, float t, Vec3d camera) {
      float w = Ease.smooth((float)camera.distanceTo(FallingSword.strike(cast)), (float)NEAR, (float)FULL);
      float eye = w * .9F * Ease.smooth(t, 1.2F, 1.8F) * (1.0F - Ease.smooth(t, 3.0F, 3.3F));
      return new float[]{eye, t, cleftSpan(t), cleftWide(t)};
   }

   /**
    * How long the cleft through the storm is (blocks along the blade's breadth, from its plane's centre line: whole to half
    * of it, closing to nothing at its end): it runs out as the sword comes through the storm's base and again as it falls.
    */
   static float cleftSpan(float t) {
      return 18.0F + 58.0F * Ease.smooth(t, 1.5F, 2.2F) + 30.0F * Ease.smooth(t, 2.78F, 3.3F);
   }

   /**
    * How wide the cleft is (blocks either side of the blade's plane): opening as the sword comes through the storm's base,
    * torn wider as it falls.
    */
   static float cleftWide(float t) {
      return (2.0F + 8.0F * Ease.smooth(t, 1.5F, 2.3F) + 7.0F * Ease.smooth(t, 2.78F, 3.3F)) * Ease.smooth(t, 1.48F, 1.72F);
   }

   // ───────────────────────────── passes ─────────────────────────────

   static void additive(Fx c, EffectMesh m) {
      float w = weight(c);
      if (w <= .01F) {
         return;
      }
      float t = c.sec;
      double k = scale(c);
      cleft(c, m, t, w, k);
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
      float through = cleftOn(t);
      if (through > .02F) {
         // the light pouring down through the cleft lights the land under it
         f.light(s.add(0.0, 12.0, 0.0), GOLD, 64.0, .7F * through * w);
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

   // ───────────────────────────── the cleft ─────────────────────────────

   /** How much light pours down through the cleft: from when it opens until the blast blows the storm apart. */
   static float cleftOn(float t) {
      return Ease.smooth(t, 1.55F, 2.0F) * (1.0F - Ease.smooth(t, 4.84F, 5.25F));
   }

   /**
    * Light pouring down through the cleft the sword cuts through the storm: its two lips lit gold and white along the
    * storm's base, a burst of light along them as it is torn wider by the falling sword, and shafts of light fanning down
    * from it to the land round where the sword falls.
    */
   static void cleft(Fx c, EffectMesh m, float t, float w, double k) {
      float on = cleftOn(t) * w;
      if (on <= .01F) {
         return;
      }
      Vec3d s = FallingSword.strike(c.cast);
      Vec3d centre = GScene.cloudCenter(c.cast);
      Vec3d f = FallingSword.forward(c.cast), r = FallingSword.right(f);
      Vec3d across = new Vec3d(-.957 * r.x, -.292, -.957 * r.z).normalize();
      double half = cleftSpan(t) * .5, wide = cleftWide(t);
      // the base of the storm, along the cleft: the clouds' centre is 40 blocks up the sword's axis
      Vec3d base = new Vec3d(centre.x, s.y + BASE - 1.0, centre.z);
      float torn = FxLib.kick(t, 2.8F, .45F);
      float glow = on * (.75F + .25F * (float)Math.sin(t * 2.3F));
      for (int side = -1; side <= 1; side += 2) {
         Vec3d lip = base.add(f.multiply(side * wide * .92));
         Vec3d prev = null;
         for (int j = 0; j <= 48; j++) {
            double u = -1.0 + 2.0 * j / 48.0;
            double ragged = .9 * Math.sin(u * 9.0 + side * 1.3 + t * .7) + .5 * Math.sin(u * 23.0 + side * 2.1);
            Vec3d p = lip.add(across.multiply(u * half)).add(f.multiply(side * ragged)).add(0.0, .6 * Math.sin(u * 5.0 + side), 0.0);
            if (prev != null) {
               float al = glow * (float)Math.pow(Math.cos(u * Math.PI * .5), .6);
               m.softLine(prev, p, (2.6 + 5.0 * torn) * k, GOLD, (.32F + .4F * torn) * al);
               m.line(prev, p, (.45 + .6 * torn) * k, GOLD_WHITE, (.7F + .3F * torn) * al);
            }
            prev = p;
         }
      }
      // shafts of light fanning down from the cleft to the land round where the sword falls
      for (int i = 0; i < 11; i++) {
         double u = -.85 + 1.7 * i / 10.0 + .06 * Math.sin(i * 3.7);
         Vec3d top = base.add(across.multiply(u * half * .9));
         double drift = Math.sin(t * .8 + i * 2.1);
         Vec3d foot = s.add(across.multiply(u * half * .55)).add(f.multiply((Ease.hash(i * 7 + 3) - .5) * 10.0 + 3.0 * drift)).add(0.0, .1, 0.0);
         float al = on * (.55F + .45F * (float)Math.sin(t * 1.7 + i * 1.9)) * (float)Math.cos(u * Math.PI * .5);
         m.softTaper(top, foot, wide * 1.1 + 2.0, wide * 1.7 + 5.0, GOLD, (.06F + .05F * torn) * al);
         m.softTaper(top, foot, wide * .3 + .8, wide * .5 + 1.6, GOLD_WHITE, (.07F + .06F * torn) * al);
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
      dome(m, c.camera, s, tau, 30.0, .1, .6F, w, k);
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
            m.vertex(b0, GOLD, .4F * a);
            m.vertex(b1, GOLD, .4F * a);
            m.vertex(t1, GOLD, 0.0F);
            m.vertex(t0, GOLD, 0.0F);
            Vec3d m0 = o.add(d0.multiply(r + lean * .55)).add(0.0, h * .55, 0.0), m1 = o.add(d1.multiply(r + lean * .55)).add(0.0, h * .55, 0.0);
            m.vertex(b0, GOLD_WHITE, .45F * a);
            m.vertex(b1, GOLD_WHITE, .45F * a);
            m.vertex(m1, GOLD_WHITE, 0.0F);
            m.vertex(m0, GOLD_WHITE, 0.0F);
         }
      }
      if (!ink) {
         GGrand.band(m, o.add(0.0, .03, 0.0), GGrand.X, GGrand.Z, r, (1.2 + 3.0 * tau) * k, GOLD_WHITE, .6F * a, 120);
      }
   }

   /**
    * A dome of light swelling over the ground to {@code reach} blocks, gone after {@code life} seconds: its outline as the
    * camera sees it (a half circle standing over the ground across the view), its rim along the ground, one latitude and
    * a faint fill.
    */
   static void dome(EffectMesh m, Vec3d camera, Vec3d o, float tau, double reach, double pace, float life, float w, double k) {
      if (tau <= 0.0F) {
         return;
      }
      double r = reach * (1.0 - Math.exp(-tau / pace));
      float fade = Ease.smooth(tau, 0.0F, .02F) * (1.0F - Ease.smooth(tau, life * .3F, life)) * w;
      if (fade <= .005F) {
         return;
      }
      Vec3d view = o.subtract(camera);
      Vec3d across = new Vec3d(-view.z, 0.0, view.x);
      across = across.lengthSquared() < 1.0E-6 ? GGrand.X : across.normalize();
      Vec3d prev = null;
      for (int i = 0; i <= 64; i++) {
         double a = Math.PI * i / 64.0;
         Vec3d p = o.add(across.multiply(Math.cos(a) * r)).add(0.0, Math.sin(a) * r, 0.0);
         if (prev != null) {
            m.softLine(prev, p, (1.6 + .05 * r) * k, GOLD, .4F * fade);
            m.line(prev, p, .3 * k, GOLD_WHITE, .75F * fade);
         }
         prev = p;
      }
      GGrand.band(m, o.add(0.0, .1, 0.0), GGrand.X, GGrand.Z, r, (1.0 + .04 * r) * k, GOLD_WHITE, .45F * fade, 96);
      double lat = Math.toRadians(32.0);
      GGrand.band(m, o.add(0.0, r * Math.sin(lat), 0.0), GGrand.X, GGrand.Z, r * Math.cos(lat), (.7 + .03 * r) * k, GOLD, .22F * fade, 96);
      m.softDisc(o.add(0.0, r * .45, 0.0), r * .95, GOLD, .08F * fade);
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
      FallingSword.Blade bl = GWorld.blade(c.cast, t);
      if (bl == null || bl.alpha() < .02F) {
         return;
      }
      // the far view's sword is the observers' own, wider and thicker (isosang_blade_observer.vsh): its surface is
      // GWorld.bladeSurface, so the spirals and the lightning go just outside it
      double buried = Math.max(0.0, -GScene.height(t));
      for (int h = 0; h < 3; h++) {
         Vec3d prev = null;
         for (int j = 0; j <= 90; j++) {
            double y = buried + .5 + 29.0 * j / 90.0;
            double a = h * Math.PI * 2.0 / 3.0 + y * .35 - t * 5.5;
            Vec3d p = GWorld.bladeSurface(bl, Math.cos(a) * 5.6, y, Math.sin(a) * 1.7);
            if (prev != null) {
               float al = charge * bl.alpha() * (float)Math.sin(Math.PI * j / 90.0) * (.6F + .4F * GGrand.heart(t));
               m.softLine(prev, p, 1.3 * k, GOLD, .35F * al);
               m.line(prev, p, .24 * k, GOLD_WHITE, .7F * al);
            }
            prev = p;
         }
      }
      int side = bl.normal().dotProduct(c.camera.subtract(bl.point())) >= 0.0 ? 1 : -1;
      for (int i = 0; i < 6; i++) {
         double slot = .07 + .03 * Ease.hash(i * 5 + 11);
         double clock = t + i * .021;
         long n = (long)Math.floor(clock / slot);
         double u = clock / slot - n;
         int seed = (int)(n * 131L + i * 17L);
         double y0 = buried + .5 + 12.0 * Ease.hash(seed + 1), y1 = y0 + 6.0 + 12.0 * Ease.hash(seed + 2);
         int edge = Ease.hash(seed + 3) > .5 ? 1 : -1;
         Vec3d from = GWorld.bladeSurface(bl, edge * 4.3, y0, 1.3 * side), to = GWorld.bladeSurface(bl, edge * 4.6, y1, 1.3 * side);
         float al = (float)Math.sin(Math.PI * u) * charge * bl.alpha();
         GGrand.bolt(m, from, to, bl.across().normalize(), seed, .5 * k, .12 * k, .9F * al);
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
      dome(m, c.camera, s, tau, 50.0, .22, 1.3F, w, k);
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
