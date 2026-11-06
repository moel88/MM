package dev.sangwoo.celestialfall.isosang;

import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.commerce.MerchantEntity;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class GExplosionDamage {
   private GExplosionDamage() {
   }

   public static List<LivingEntity> apply(ServerPlayerEntity var0, Vec3d var1, float var2, UUID var3) {
      ServerWorld var4 = var0.getServerWorld();
      // (2.87.1) the arena, or out of it 62 × 47 blocks round where the sword struck (GExplosionRules.area)
      GArenaBounds var5 = GArenaServer.arena(var3);
      if (var5 == null) {
         var5 = GExplosionRules.area(var1);
      }

      DamageSource var6 = new DamageSource(var4.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(IsosangAttack.ULTIMATE_DAMAGE), var0);
      ArrayList var7 = new ArrayList();

      for (LivingEntity var9 : var4.getEntitiesByClass(
         LivingEntity.class,
         var5.box(),
         var1x -> var1x != var0 && var1x.isAlive() && !var1x.isSpectator() && var1x.canHit() && !(var1x instanceof MerchantEntity)
      )) {
         if (var9 instanceof PlayerEntity var10 && !var0.shouldDamagePlayer(var10)) {
            continue;
         }

         float var12 = var5.damage(var9.getPos(), var1);
         if (!(var12 <= 0.0F)) {
            CombatManager.interrupt(var9);
            var9.timeUntilRegen = 0;
            if (CombatManager.damageWithoutKnockback(var9, var6, var12)) {
               var7.add(var9);
               if (!StarPlatinumTimeStop.deferFeedback(var0, var9, var12, var2) && ServerPlayNetworking.canSend(var0, KagenoManager.DAMAGE)) {
                  PacketByteBuf var11 = PacketByteBufs.create();
                  var11.writeVarInt(var9.getId());
                  var11.writeFloat(var12);
                  var11.writeFloat(var2);
                  ServerPlayNetworking.send(var0, KagenoManager.DAMAGE, var11);
               }
            }
         }
      }

      return var7;
   }
}
