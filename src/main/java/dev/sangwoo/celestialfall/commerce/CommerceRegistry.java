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
    public static EntityType<MerchantEntity> SELLER,BUYER;
    private static EntityType<MerchantEntity> type(String id,boolean seller){return Registry.register(Registries.ENTITY_TYPE,CelestialFall.id(id),FabricEntityTypeBuilder.<MerchantEntity>create(SpawnGroup.MISC,(t,w)->new MerchantEntity(t,w,seller)).dimensions(EntityDimensions.fixed(.6f,1.95f)).trackRangeBlocks(64).trackedUpdateRate(3).fireImmune().build());}
    public static UUID id(boolean seller){return UUID.nameUUIDFromBytes(("botand:merchant:"+(seller?"seller":"buyer")).getBytes(StandardCharsets.UTF_8));}
    private static final Map<net.minecraft.server.world.ServerWorld,int[]> LOADED=new IdentityHashMap<>();
    public static void initialize(){
        SELLER=type("item_seller",true);BUYER=type("item_buyer",false);
        FabricDefaultAttributeRegistry.register(SELLER,VillagerEntity.createVillagerAttributes());
        FabricDefaultAttributeRegistry.register(BUYER,VillagerEntity.createVillagerAttributes());
        Registry.register(Registries.ITEM,CelestialFall.id("rokakaka"),ROKAKAKA);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(e->e.add(ROKAKAKA));
        ServerTickEvents.END_WORLD_TICK.register(w->{
            if(!w.getRegistryKey().equals(World.OVERWORLD)||!MapGate.isBotandMap(w.getServer())||!MapGate.enabled(w))return;
            int[] loaded=LOADED.computeIfAbsent(w,k->new int[2]);
            for(int i=0;i<2;i++){
                boolean seller=i==0;var pos=BlockPos.ofFloored(MerchantEntity.homeOf(w.getServer(),seller));
                if(!w.isChunkLoaded(pos)){loaded[i]=0;continue;}
                if(++loaded[i]<40||loaded[i]%40!=0)continue;
                if(w.getEntity(id(seller))!=null)continue;
                // Stable UUID + a loaded-chunk grace period prevents duplicates across saves/reloads.
                var mob=(seller?SELLER:BUYER).create(w);if(mob==null)continue;
                float yaw=MerchantEntity.yawOf(w.getServer(),seller);
                mob.setUuid(id(seller));mob.refreshPositionAndAngles(mob.home().x,mob.home().y,mob.home().z,yaw,0);mob.setHeadYaw(yaw);mob.setBodyYaw(yaw);
                w.spawnEntity(mob);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s->LOADED.clear());
        CommerceServer.initialize();
    }
    private CommerceRegistry(){}
}
