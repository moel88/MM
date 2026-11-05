package dev.sangwoo.celestialfall.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.boss.BossThreats;
import dev.sangwoo.celestialfall.map.MapGate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class GArenaServer {
   public static final Identifier EVENT = CelestialFall.id("isosang_g_arena_v1");
   private static final Map<UUID, GArenaServer.Scene> scenes = new HashMap<>();
   private static boolean initialized;

   public static void initialize() {
      if (!initialized) {
         initialized = true;
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)var0 -> scenes.clear());
         ServerTickEvents.END_SERVER_TICK
            .register(
               (EndTick)var0 -> scenes.entrySet()
                     .removeIf(
                        var1 -> {
                           GArenaServer.Scene var2 = var1.getValue();
                           ServerPlayerEntity var3 = var0.getPlayerManager().getPlayer(var2.caster);
                           ServerWorld var4 = var0.getWorld(RegistryKey.of(RegistryKeys.WORLD, var2.dimension));
                           boolean var5 = var3 == null
                              || !var3.isAlive()
                              || !var3.getWorld().getRegistryKey().getValue().equals(var2.dimension)
                              || var4 == null
                              || var4.getTime() - var2.start > 160L;
                           if (var5 && var4 != null) {
                              for (ServerPlayerEntity var7 : var4.getPlayers()) {
                                 send(var1.getKey(), var2, var7, true);
                              }
                           }

                           return var5;
                        }
                     )
            );
      }
   }

   public static BossThreats.Zone threat(Vec3d var0, double var1, double var3, double var5, ServerPlayerEntity var7) {
      IsosangAttack.UltimateNow var8 = IsosangAttack.ultimateNow(var7);

      for (GArenaServer.Scene var10 : scenes.values()) {
         if (var8 != null && var10.start == var8.start() && var10.caster.equals(var7.getUuid())) {
            GArenaBounds var11 = var10.arena;
            return new BossThreats.Zone(
               var11.minX(),
               var11.minY(),
               var11.minZ(),
               0.0F,
               0.0,
               var11.maxZ() - var11.minZ(),
               var11.minX() - var11.maxX(),
               0.0,
               0.0,
               var11.maxY() - var11.minY(),
               0.0
            );
         }
      }

      return BossThreats.Zone.ring(var0, var1, var3, var5);
   }

   public static GArenaBounds arena(UUID var0) {
      GArenaServer.Scene var1 = scenes.get(var0);
      return var1 == null ? null : var1.arena;
   }

   public static boolean selected(UUID var0, UUID var1) {
      GArenaServer.Scene var2 = scenes.get(var0);
      return var2 != null && var2.audience.contains(var1);
   }

   public static void event(ServerPlayerEntity var0, UUID var1, int var2, long var3, int var5) {
      if (var2 == 9) {
         ServerWorld var6 = var0.getServerWorld();
         if (var5 == 0) {
            GArenaBounds var7 = MapGate.isCity(var0.getServer()) && GArenaBounds.GARDEN.contains(var0.getPos()) ? GArenaBounds.GARDEN : null;
            if (var7 == null) {
               return;
            }

            HashSet var8 = new HashSet();

            for (ServerPlayerEntity var10 : var6.getPlayers()) {
               if (var10.isAlive() && var7.contains(var10.getPos())) {
                  var8.add(var10.getUuid());
               }
            }

            scenes.put(var1, new GArenaServer.Scene(var0.getUuid(), var6.getRegistryKey().getValue(), var3, var7, Set.copyOf(var8)));
         }

         GArenaServer.Scene var11 = scenes.get(var1);
         if (var11 != null) {
            if (var5 == 0 || var5 == 3) {
               for (ServerPlayerEntity var13 : var6.getPlayers()) {
                  if (!(var13.squaredDistanceTo(var0) > 16384.0) || var11.audience.contains(var13.getUuid())) {
                     send(var1, var11, var13, var5 == 3);
                  }
               }

               if (var5 == 3) {
                  scenes.remove(var1);
               }
            }
         }
      }
   }

   private static void send(UUID var0, GArenaServer.Scene var1, ServerPlayerEntity var2, boolean var3) {
      if (ServerPlayNetworking.canSend(var2, EVENT)) {
         PacketByteBuf var4 = PacketByteBufs.create();
         var4.writeUuid(var0);
         var4.writeUuid(var1.caster);
         var4.writeIdentifier(var1.dimension);
         var4.writeLong(var1.start);
         var4.writeBoolean(var3);
         var4.writeBoolean(var1.audience.contains(var2.getUuid()));
         GArenaBounds var5 = var1.arena;
         var4.writeDouble(var5.minX());
         var4.writeDouble(var5.minY());
         var4.writeDouble(var5.minZ());
         var4.writeDouble(var5.maxX());
         var4.writeDouble(var5.maxY());
         var4.writeDouble(var5.maxZ());
         ServerPlayNetworking.send(var2, EVENT, var4);
      }
   }

   private static record Scene(UUID caster, Identifier dimension, long start, GArenaBounds arena, Set<UUID> audience) {
   }
}
