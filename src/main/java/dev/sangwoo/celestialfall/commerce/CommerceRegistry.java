package dev.sangwoo.celestialfall.commerce;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.map.MapGate;
import net.fabricmc.fabric.api.object.builder.v1.entity.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.*;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class CommerceRegistry {
    public static final RokakakaItem ROKAKAKA=new RokakakaItem();
    public static EntityType<MerchantEntity> SELLER,BUYER,CRAFTER;
    private static final String[] KEYS={"seller","buyer","crafter"};
    private static EntityType<MerchantEntity> type(String id,int role){return Registry.register(Registries.ENTITY_TYPE,CelestialFall.id(id),FabricEntityTypeBuilder.<MerchantEntity>create(SpawnGroup.MISC,(t,w)->new MerchantEntity(t,w,role)).dimensions(EntityDimensions.fixed(.6f,1.95f)).trackRangeBlocks(64).trackedUpdateRate(3).fireImmune().build());}
    /** Each merchant's fixed id (the seller's and buyer's as before 2.49.0, so worlds keep exactly one of each). */
    public static UUID id(int role){return UUID.nameUUIDFromBytes(("botand:merchant:"+KEYS[role]).getBytes(StandardCharsets.UTF_8));}
    public static UUID id(boolean seller){return id(seller?MerchantEntity.SELLER:MerchantEntity.BUYER);}
    public static EntityType<MerchantEntity> type(int role){return role==MerchantEntity.SELLER?SELLER:role==MerchantEntity.BUYER?BUYER:CRAFTER;}
    private static final Map<net.minecraft.server.world.ServerWorld,int[]> LOADED=new IdentityHashMap<>();
    public static void initialize(){
        SELLER=type("item_seller",MerchantEntity.SELLER);BUYER=type("item_buyer",MerchantEntity.BUYER);CRAFTER=type("item_crafter",MerchantEntity.CRAFTER);
        for(var t:List.of(SELLER,BUYER,CRAFTER))FabricDefaultAttributeRegistry.register(t,VillagerEntity.createVillagerAttributes());
        Registry.register(Registries.ITEM,CelestialFall.id("rokakaka"),ROKAKAKA);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(e->e.add(ROKAKAKA));
        ServerTickEvents.END_WORLD_TICK.register(w->{
            if(!w.getRegistryKey().equals(World.OVERWORLD)||!MapGate.isBotandMap(w.getServer())||!MapGate.enabled(w))return;
            int[] loaded=LOADED.computeIfAbsent(w,k->new int[3]);
            for(int role=0;role<3;role++){
                var pos=BlockPos.ofFloored(MerchantEntity.homeOf(w.getServer(),role));
                if(!w.isChunkLoaded(pos)){loaded[role]=0;continue;}
                if(++loaded[role]<40||loaded[role]%40!=0)continue;
                if(w.getEntity(id(role))!=null)continue;
                // Stable UUID + a loaded-chunk grace period prevents duplicates across saves/reloads.
                var mob=type(role).create(w);if(mob==null)continue;
                float yaw=MerchantEntity.yawOf(w.getServer(),role);
                mob.setUuid(id(role));mob.refreshPositionAndAngles(mob.home().x,mob.home().y,mob.home().z,yaw,0);mob.setHeadYaw(yaw);mob.setBodyYaw(yaw);
                w.spawnEntity(mob);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s->LOADED.clear());
        CommerceServer.initialize();
    }
    private CommerceRegistry(){}
}
