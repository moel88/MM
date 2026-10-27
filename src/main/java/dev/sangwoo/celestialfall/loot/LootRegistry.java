package dev.sangwoo.celestialfall.loot;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.registry.*;
import net.minecraft.entity.*;
import net.minecraft.item.ItemGroups;
public final class LootRegistry {
    public static final ChestKeyItem CHEST_KEY=new ChestKeyItem();
    public static EntityType<FieldLootEntity> FIELD_LOOT;
    public static void initialize(){
        Registry.register(Registries.ITEM,CelestialFall.id("chest_key"),CHEST_KEY);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(e->e.add(CHEST_KEY));
        FIELD_LOOT=Registry.register(Registries.ENTITY_TYPE,CelestialFall.id("field_loot"),FabricEntityTypeBuilder.<FieldLootEntity>create(SpawnGroup.MISC,FieldLootEntity::new).dimensions(EntityDimensions.fixed(1.32f,1.05f)).trackRangeBlocks(64).trackedUpdateRate(10).fireImmune().build());
        LootWorld.initialize();LootServer.initialize();
    }
    private LootRegistry(){}
}
