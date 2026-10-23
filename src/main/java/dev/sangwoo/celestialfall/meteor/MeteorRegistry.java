package dev.sangwoo.celestialfall.meteor;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.*;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.*;
import net.minecraft.sound.SoundEvent;
/** The meteors' items (stand arrow, fragment, pickaxe), the rock and their sounds (2.49.0). */
public final class MeteorRegistry {
    public static final StandArrowItem STAND_ARROW=new StandArrowItem();
    public static final MeteorFragmentItem FRAGMENT=new MeteorFragmentItem();
    public static final MeteorPickaxeItem PICKAXE=new MeteorPickaxeItem();
    /** The fall (the rush of it coming in), the landing, a pickaxe hit, a fragment breaking off, the rock crumbling, an arrow made. */
    public static final SoundEvent IMPACT_SOUND=sound("meteor_impact"),HIT_SOUND=sound("meteor_hit"),
        BREAK_SOUND=sound("meteor_break"),CRUMBLE_SOUND=sound("meteor_crumble"),CRAFT_SOUND=sound("meteor_craft");
    public static EntityType<MeteorEntity> METEOR;
    private static SoundEvent sound(String name){return SoundEvent.of(CelestialFall.id(name));}
    public static void initialize(){
        Registry.register(Registries.ITEM,CelestialFall.id("stand_arrow"),STAND_ARROW);
        Registry.register(Registries.ITEM,CelestialFall.id("meteor_fragment"),FRAGMENT);
        Registry.register(Registries.ITEM,CelestialFall.id("meteor_pickaxe"),PICKAXE);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(e->{e.add(STAND_ARROW);e.add(FRAGMENT);});
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e->e.add(PICKAXE));
        for(var s:new SoundEvent[]{IMPACT_SOUND,HIT_SOUND,BREAK_SOUND,CRUMBLE_SOUND,CRAFT_SOUND})Registry.register(Registries.SOUND_EVENT,s.getId(),s);
        // a rock on a wide field is seen from far off: tracked as far as the server sends entities at all
        METEOR=Registry.register(Registries.ENTITY_TYPE,CelestialFall.id("meteor"),FabricEntityTypeBuilder.<MeteorEntity>create(SpawnGroup.MISC,MeteorEntity::new)
            .dimensions(EntityDimensions.fixed(2.3f,1.5f)).trackRangeBlocks(256).trackedUpdateRate(20).fireImmune().build());
        MeteorManager.initialize();
    }
    private MeteorRegistry(){}
}
