package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.isosang.GArenaBounds;
import dev.sangwoo.celestialfall.isosang.GArenaServer;
import dev.sangwoo.celestialfall.isosang.GExplosionRules;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Who sees Lee So-sang's G's cutscene, and the ground its last blast covers. Its user sees it all, from her hand on (with
 * the cutscene camera on). 2.92.0 (user request: "62 × 47 범위에 있는 사람은 모두 시전자의 컷씬이 보이는 대신 검이 하늘에서
 * 떨어지는 순간부터 보이게 해줘 그대신 시전하는 소리는 처음부터 끝까지 들리게 하고 시전자는 원래랑 똑같이 손으로 인을 맺고
 * 검이 떨어지는거 똑같이 처음부터 재생되게 그래야 다른 플레이어들이 소리 듣고 조금이라도 도망 갈 수 있을 거 같아", then
 * with a frame of the sky's shot, the sword coming out of the storm over her: "다른 사람들도 이 시점부터 보이게"): everyone
 * else standing in its blast's ground ({@link #area}: the garden arena it was cast in, or 62 × 47 blocks round where its
 * sword strikes) as its sky's shot begins ({@link #JOIN}) watches it from there to its cut, and the dark after it; until
 * then they keep their own camera and may run (its sound they hear from its start, IsosangClient). Who stood in it is
 * settled once, at that moment. Before 2.92.0 those in the arena as it was cast watched it all (the user's 2.87.0
 * preview; the server still says who they were, {@link Scene#selected}, but it no longer counts).
 */
public final class GArenaClient {
   private static final Map<UUID, GArenaClient.Scene> scenes = new LinkedHashMap<>();
   /** Whether this client's player stood in each other's G's blast ground as its sword fell (settled then, 2.92.0). */
   private static final Map<UUID, Boolean> joined = new HashMap<>();
   private static Object world;
   private static UUID active;
   /**
    * When those in another's G's blast ground join its cutscene (seconds into it, 2.92.0): the sky's shot's first frame
    * (FallingSword.S_PULLED, 1.65 s), the sword just out of the storm. The user's frame of it was 1.83 s into G (matched
    * against the 2.88.0 film): the shot from its start, so they see the sword come out too. First taken as its fall (2.78 s).
    */
   static final float JOIN = FallingSword.S_PULLED;

   public static void initialize() {
      ClientPlayNetworking.registerGlobalReceiver(
         GArenaServer.EVENT,
         (var0, var1, var2, var3) -> {
            UUID var4 = var2.readUuid();
            UUID var5 = var2.readUuid();
            Identifier var6 = var2.readIdentifier();
            long var7 = var2.readLong();
            boolean var9 = var2.readBoolean();
            boolean var10 = var2.readBoolean();
            GArenaBounds var11 = new GArenaBounds(
               var2.readDouble(), var2.readDouble(), var2.readDouble(), var2.readDouble(), var2.readDouble(), var2.readDouble()
            );
            var0.execute(() -> {
               refresh(var0);
               if (var0.world != null && var0.world.getRegistryKey().getValue().equals(var6)) {
                  if (var9) {
                     scenes.remove(var4);
                     if (var4.equals(active)) {
                        active = null;
                     }
                  } else {
                     scenes.putIfAbsent(var4, new GArenaClient.Scene(var5, var6, var7, var10, var11));
                  }
               }
            });
         }
      );
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(var0, var1) -> clear());
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)var0 -> {
         refresh(var0);
         if (var0.world != null) {
            scenes.entrySet().removeIf(var1 -> var0.world.getTime() - var1.getValue().start > 180L);
         }
         joined.keySet().removeIf(id -> !IsosangClient.SWINGS.containsKey(id));
      });
   }

   private static void clear() {
      scenes.clear();
      joined.clear();
      active = null;
      world = null;
   }

   private static void refresh(MinecraftClient var0) {
      if (world != var0.world) {
         clear();
         world = var0.world;
      }
   }

   public static GArenaBounds arena(StandClient.Cast var0) {
      GArenaClient.Scene var1 = scenes.get(var0.id());
      return var1 == null ? null : var1.arena;
   }

   /**
    * The ground G's last blast covers (2.87.1, as the server's GExplosionDamage): the arena it was cast in, or 62 × 47
    * blocks round where its sword strikes (GExplosionRules.area; before, 20 blocks round it).
    */
   public static GArenaBounds area(StandClient.Cast var0) {
      GArenaBounds var1 = arena(var0);
      return var1 != null ? var1 : GExplosionRules.area(FallingSword.strike(var0));
   }

   /** How far from where the sword strikes the blast's ground goes, to its furthest corner. */
   public static double radius(StandClient.Cast var0) {
      return area(var0).reach(FallingSword.strike(var0));
   }

   public static boolean contains(StandClient.Cast var0, Vec3d var1) {
      return area(var0).contains(var1);
   }

   private static boolean live(StandClient.Cast var0) {
      MinecraftClient var1 = MinecraftClient.getInstance();
      PlayerEntity var2 = var0 != null && var1.world != null ? var1.world.getPlayerByUuid(var0.caster()) : null;
      return var2 != null && var2.isAlive() && var0.age(0.0F) >= 0.0F && var0.age(0.0F) / 20.0F < 4.83F && Float.isNaN(IsosangClient.ultimateEnd(var0.id()));
   }

   /**
    * The G whose cutscene this client's camera shows now, or null: its player's own (with the cutscene camera on), from
    * her hand to its cut; or (2.92.0) another's, from its sky's shot to its cut, if they stood in its blast's ground as
    * that began ({@link #watches}).
    */
   public static StandClient.Cast cinematic() {
      MinecraftClient mc = MinecraftClient.getInstance();
      refresh(mc);
      if (mc.world == null || mc.player == null || !mc.player.isAlive() || !MapClient.enabled()) {
         return null;
      }
      StandClient.Cast own = IsosangClient.ownUltimate();
      if (CelestialFall.CONFIG.cinematicCamera && live(own)) {
         return own;
      }
      StandClient.Cast kept = active == null ? null : IsosangClient.SWINGS.get(active);
      if (watches(mc, kept)) {
         return kept;
      }
      active = null;
      for (StandClient.Cast c : IsosangClient.SWINGS.values()) {
         if (watches(mc, c)) {
            active = c.id();
            return c;
         }
      }
      return null;
   }

   /**
    * Whether this client watches another's G {@code c} now (2.92.0): from its sky's shot ({@link #JOIN}) to its cut, while
    * its user lives and it was not called off, if this client's player stood in its blast's ground as that began (looked
    * at once, the first frame from then; one who ran out of it before then keeps their own camera).
    */
   private static boolean watches(MinecraftClient mc, StandClient.Cast c) {
      if (c == null || c.combo() != IsosangRules.ULTIMATE || c.caster().equals(mc.player.getUuid()) || !live(c)
         || !c.dimension().equals(mc.world.getRegistryKey().getValue()) || !joins(c.age(mc.getTickDelta()) / 20.0F)) {
         return false;
      }
      return joined.computeIfAbsent(c.id(), id -> contains(c, mc.player.getPos()));
   }

   /** Whether one in another's G's blast ground watches its cutscene {@code sec} seconds into it (2.92.0): from its sky's shot to its cut. */
   static boolean joins(float sec) {
      return sec >= JOIN && sec < IsosangRules.G_BACK;
   }

   /**
    * The G this client's camera showed (its own with the cutscene camera on, from its start; 2.92.0: another's it joined
    * as its sky's shot began, from then), a little past its cutscene too (until {@code until} seconds into it): the dark
    * after the cut is drawn over its view (2.87.0).
    */
   static StandClient.Cast watched(float delta, float until) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null || mc.player == null) {
         return null;
      }
      for (StandClient.Cast c : IsosangClient.SWINGS.values()) {
         if (c.combo() == IsosangRules.ULTIMATE && Boolean.TRUE.equals(joined.get(c.id())) && !c.caster().equals(mc.player.getUuid())
            && c.age(delta) / 20.0F >= JOIN && c.age(delta) / 20.0F < until && Float.isNaN(IsosangClient.ultimateEnd(c.id()))) {
            return c;
         }
      }
      StandClient.Cast own = IsosangClient.ownUltimate();
      return own != null && CelestialFall.CONFIG.cinematicCamera && own.age(delta) / 20.0F < until ? own : null;
   }

   /** Whether this client's camera shows another's G's cutscene now (2.92.0: one it joined as its sky's shot began). */
   public static boolean shared() {
      StandClient.Cast c = cinematic();
      MinecraftClient mc = MinecraftClient.getInstance();
      return c != null && mc.player != null && !c.caster().equals(mc.player.getUuid());
   }

   /**
    * The ground G's sound is as loud in as for its user (2.92.0): its blast's ({@link #area}) for the G {@code id}
    * whose sword strikes at {@code strike}, as the cast or (once it is gone) its strike alone tells it.
    */
   static GArenaBounds ground(UUID id, Vec3d strike) {
      GArenaClient.Scene s = scenes.get(id);
      return s != null ? s.arena : GExplosionRules.area(strike);
   }

   static boolean actor(Entity var0) {
      StandClient.Cast var1 = cinematic();
      return var0 != null && var1 != null && var0.getUuid().equals(var1.caster());
   }

   static boolean viewing(StandClient.Cast var0) {
      StandClient.Cast var1 = cinematic();
      return var1 != null && var1.id().equals(var0.id());
   }

   /**
    * A G cast in the garden's arena, as its server tells (the user's 2.87.0 preview): its user, its world, its start, the
    * arena, and whether this client's player stood in it then ({@code selected}: until 2.91.0 they watched all of its
    * cutscene; 2.92.0, as everyone else in its ground, from its fall).
    */
   private static record Scene(UUID caster, Identifier dimension, long start, boolean selected, GArenaBounds arena) {
   }
}
