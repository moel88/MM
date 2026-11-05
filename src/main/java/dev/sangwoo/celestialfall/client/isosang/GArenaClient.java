package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.isosang.GArenaBounds;
import dev.sangwoo.celestialfall.isosang.GArenaServer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
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

public final class GArenaClient {
   private static final Map<UUID, GArenaClient.Scene> scenes = new LinkedHashMap<>();
   private static Object world;
   private static UUID active;

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
      });
   }

   private static void clear() {
      scenes.clear();
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

   public static double radius(StandClient.Cast var0) {
      GArenaBounds var1 = arena(var0);
      return var1 == null ? 20.0 : var1.reach(FallingSword.strike(var0));
   }

   public static boolean contains(StandClient.Cast var0, Vec3d var1) {
      GArenaBounds var2 = arena(var0);
      return var2 == null ? var1.squaredDistanceTo(FallingSword.strike(var0)) <= 400.0 : var2.contains(var1);
   }

   private static boolean live(StandClient.Cast var0) {
      MinecraftClient var1 = MinecraftClient.getInstance();
      PlayerEntity var2 = var0 != null && var1.world != null ? var1.world.getPlayerByUuid(var0.caster()) : null;
      return var2 != null && var2.isAlive() && var0.age(0.0F) >= 0.0F && var0.age(0.0F) / 20.0F < 4.83F && Float.isNaN(IsosangClient.ultimateEnd(var0.id()));
   }

   public static StandClient.Cast cinematic() {
      MinecraftClient var0 = MinecraftClient.getInstance();
      refresh(var0);
      if (var0.world != null && var0.player != null && var0.player.isAlive() && MapClient.enabled()) {
         StandClient.Cast var1 = active == null ? null : IsosangClient.SWINGS.get(active);
         if (live(var1)) {
            return var1;
         } else {
            active = null;

            for (Entry var3 : scenes.entrySet()) {
               GArenaClient.Scene var4 = (GArenaClient.Scene)var3.getValue();
               if (var4.selected) {
                  StandClient.Cast var5 = IsosangClient.SWINGS.get(var3.getKey());
                  if (live(var5) && var4.dimension.equals(var0.world.getRegistryKey().getValue())) {
                     active = var5.id();
                     return var5;
                  }
               }
            }

            StandClient.Cast var6 = IsosangClient.ownUltimate();
            return CelestialFall.CONFIG.cinematicCamera && live(var6) ? var6 : null;
         }
      } else {
         return null;
      }
   }

   /**
    * The G this client's camera showed (its own with the cutscene camera on, or one shared with it in the arena), a little
    * past its cutscene too (until {@code until} seconds into it): the dark after the cut is drawn over its view (2.87.0).
    */
   static StandClient.Cast watched(float delta, float until) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null || mc.player == null) {
         return null;
      }
      for (Entry<UUID, GArenaClient.Scene> e : scenes.entrySet()) {
         if (e.getValue().selected) {
            StandClient.Cast c = IsosangClient.SWINGS.get(e.getKey());
            if (c != null && c.age(delta) >= 0.0F && c.age(delta) / 20.0F < until && Float.isNaN(IsosangClient.ultimateEnd(c.id()))) {
               return c;
            }
         }
      }
      StandClient.Cast own = IsosangClient.ownUltimate();
      return own != null && CelestialFall.CONFIG.cinematicCamera && own.age(delta) / 20.0F < until ? own : null;
   }

   public static boolean shared() {
      StandClient.Cast var0 = cinematic();
      return var0 != null && scenes.containsKey(var0.id()) && scenes.get(var0.id()).selected;
   }

   static boolean actor(Entity var0) {
      StandClient.Cast var1 = cinematic();
      return var0 != null && var1 != null && var0.getUuid().equals(var1.caster());
   }

   static boolean viewing(StandClient.Cast var0) {
      StandClient.Cast var1 = cinematic();
      return var1 != null && var1.id().equals(var0.id());
   }

   private static record Scene(UUID caster, Identifier dimension, long start, boolean selected, GArenaBounds arena) {
   }
}
